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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * In-app update: ask GitHub for the newest release, and when it beats the
 * running build, fetch that APK and install it over ourselves.
 *
 * Driven entirely from Settings — nothing here runs on its own.
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

    suspend fun check(token: String) {
        _state.value = State.Checking
        _state.value = try {
            val release = releases.latest(token)
            if (Version.isNewer(release.tag, currentVersion)) State.Available(release)
            else State.UpToDate(currentVersion)
        } catch (e: ReleaseException) {
            State.Failed(e.message ?: "Couldn't reach GitHub.")
        } catch (e: Exception) {
            State.Failed("Couldn't reach GitHub: ${e.message ?: "no connection"}")
        }
    }

    suspend fun downloadAndInstall(release: Release, token: String) {
        val apk = try {
            download(release, token)
        } catch (e: Exception) {
            _state.value = State.Failed("Download failed: ${e.message ?: "unknown error"}")
            return
        }
        _state.value = State.Installing
        try {
            install(apk)
        } catch (e: Exception) {
            _state.value = State.Failed("Install failed: ${e.message ?: "unknown error"}")
        }
    }

    private suspend fun download(release: Release, token: String): File = withContext(Dispatchers.IO) {
        // One APK at a time: a half-written file from a failed run must never
        // be the thing we install.
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "yotsuba-${release.tag}.apk")
        _state.value = State.Downloading(0, release.sizeBytes)

        releases.openApk(release, token).use { resp ->
            if (!resp.isSuccessful) throw ReleaseException("GitHub said ${resp.code}")
            val body = resp.body ?: throw ReleaseException("empty response")
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

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            // Android 12+ lets an app update *itself* with no confirmation
            // dialog. When the OS declines, the session reports
            // PENDING_USER_ACTION and we show the installer screen instead.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("yotsuba", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusReceiver(sessionId).intentSender)
        }
    }

    /**
     * Registered for this one session, so it can write back into the state
     * flow — a manifest receiver in a fresh process could not.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun statusReceiver(sessionId: Int): PendingIntent {
        val action = "${context.packageName}.INSTALL_STATUS.$sessionId"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = IntentCompat.getParcelableExtra(
                            intent, Intent.EXTRA_INTENT, Intent::class.java,
                        )
                        if (confirm != null) {
                            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
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
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        _state.value = State.Failed(msg ?: "Android refused the install.")
                        unregister(this)
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
