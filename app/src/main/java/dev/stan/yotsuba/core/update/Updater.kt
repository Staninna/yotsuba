package dev.stan.yotsuba.core.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * In-app update: ask GitHub for the newest release, and when it beats the
 * running build, fetch that APK and install it over ourselves.
 *
 * Driven entirely from Settings. Nothing here runs on its own.
 */
@Singleton
class Updater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val releases: GithubReleases,
) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val release: Release) : State
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State
        /** Handed to the system; the process may be killed at any moment now. */
        data object Installing : State
        data class Failed(val message: String) : State
    }

    sealed interface History {
        data object Idle : History
        data object Loading : History
        data class Loaded(val entries: List<ReleaseEntry>) : History
        data class Failed(val message: String) : History
    }

    private val _history = MutableStateFlow<History>(History.Idle)
    /** Every release's notes, newest first. Loaded once per process, when Updates opens. */
    val history: StateFlow<History> = _history.asStateFlow()

    suspend fun loadHistory(force: Boolean = false) {
        if (!force && _history.value is History.Loaded) return
        _history.value = History.Loading
        _history.value = try {
            History.Loaded(releases.all())
        } catch (e: ReleaseException) {
            History.Failed(e.message ?: "Couldn't reach GitHub.")
        } catch (e: Exception) {
            History.Failed("Couldn't reach GitHub: ${e.message ?: "no connection"}")
        }
    }

    // Survives the composable: the retry is kicked off from a broadcast callback.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen where "install unknown apps" is granted. */
    fun unknownSourcesIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun dismiss() {
        _state.value = State.Idle
    }

    suspend fun check() {
        _state.value = State.Checking
        _state.value = try {
            val release = releases.latest()
            if (Version.isNewer(release.tag, currentVersion)) State.Available(release)
            else State.UpToDate(currentVersion)
        } catch (e: ReleaseException) {
            State.Failed(e.message ?: "Couldn't reach GitHub.")
        } catch (e: Exception) {
            State.Failed("Couldn't reach GitHub: ${e.message ?: "no connection"}")
        }
    }

    suspend fun downloadAndInstall(release: Release) {
        val apk = try {
            download(release)
        } catch (e: Exception) {
            _state.value = State.Failed("Download failed: ${e.message ?: "unknown error"}")
            return
        }
        runInstall(apk, allowSilent = true)
    }

    /** The one place the install transition is authored: Installing, then Failed on a throw. */
    private suspend fun runInstall(apk: File, allowSilent: Boolean) {
        _state.value = State.Installing
        try {
            install(apk, allowSilent)
        } catch (e: Exception) {
            _state.value = State.Failed("Install failed: ${e.message ?: "unknown error"}")
        }
    }

    /**
     * Some OEM builds, HyperOS among them, refuse a silent self-update by
     * killing the session outright ("INSTALL_FAILED_ABORTED: Permission
     * denied") rather than reporting PENDING_USER_ACTION and letting us show
     * the installer. So a silent attempt that fails is retried once the
     * ordinary way, with the system's confirmation dialog.
     */
    private fun retryWithConfirmation(apk: File) {
        scope.launch { runInstall(apk, allowSilent = false) }
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        // One APK at a time: a half-written file from a failed run must never
        // be the thing we install.
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "yotsuba-${release.tag}.apk")
        _state.value = State.Downloading(0, release.sizeBytes)

        releases.openApk(release).use { resp ->
            if (!resp.isSuccessful) throw ReleaseException("GitHub said ${resp.code}")
            val body = resp.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var reported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        // Per ~256KB: a state change per 64KB chunk is
                        // recomposition noise, not information.
                        if (done - reported >= 256 * 1024) {
                            reported = done
                            _state.value = State.Downloading(done, total)
                        }
                    }
                    _state.value = State.Downloading(done, total)
                }
            }
        }
        target
    }

    private suspend fun install(apk: File, allowSilent: Boolean) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            // Android 12+ lets an app update *itself* with no confirmation
            // dialog. When the OS declines, the session reports
            // PENDING_USER_ACTION and we show the installer screen instead.
            if (allowSilent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("yotsuba", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusReceiver(sessionId, apk, allowSilent).intentSender)
        }
    }

    /**
     * Registered for this one session, so it can write back into the state
     * flow. A manifest receiver in a fresh process could not.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun statusReceiver(sessionId: Int, apk: File, allowSilent: Boolean): PendingIntent {
        val action = "${context.packageName}.INSTALL_STATUS.$sessionId"
        val receiver = object : BroadcastReceiver() {
            // Once the installer screen has been shown, a failure is the user's answer, not an
            // OEM refusing silent install: retrying would put the same dialog up twice.
            var userActionShown = false

            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = IntentCompat.getParcelableExtra(
                            intent, Intent.EXTRA_INTENT, Intent::class.java,
                        )
                        if (confirm != null) {
                            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            userActionShown = true
                        } else {
                            unregister(this)
                            _state.value = State.Failed("Android wants confirmation but gave no screen.")
                        }
                    }
                    // On a silent update the process dies before this arrives;
                    // seeing it means the installer screen ran.
                    PackageInstaller.STATUS_SUCCESS -> {
                        _state.value = State.Idle
                        unregister(this)
                    }
                    else -> {
                        unregister(this)
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        if (allowSilent && !userActionShown) {
                            retryWithConfirmation(apk)
                        } else {
                            _state.value = State.Failed(msg ?: "Android refused the install.")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun unregister(receiver: BroadcastReceiver) {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
