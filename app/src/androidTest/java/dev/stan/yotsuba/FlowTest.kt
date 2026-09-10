package dev.stan.yotsuba

import android.content.Intent
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dev.stan.yotsuba.di.Fakes
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Shared rules for every instrumented flow test.
 *
 * Hilt is set up first (order 0), then the Compose rule (order 1), which is empty so the
 * activity launches only in [setUp], after [seed] has put the fakes in the state the test
 * needs. Subclasses still carry their own @HiltAndroidTest annotation.
 *
 * The full nav graph is real; only the repositories are fakes (see di/). Override [seed]
 * to set knobs before launch, or [launchOnSetUp] to launch by hand with an [Intent].
 */
abstract class FlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule: ComposeTestRule = createEmptyComposeRule()

    @Inject lateinit var fakes: Fakes

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Runs after injection and before the activity launches. */
    protected open fun seed() = Unit

    /** False when the test launches with its own intent through [launch]. */
    protected open val launchOnSetUp: Boolean get() = true

    @Before
    fun setUp() {
        grantNotificationPermission()
        hiltRule.inject()
        seed()
        if (launchOnSetUp) launch()
    }

    /**
     * The first bookmark asks for POST_NOTIFICATIONS through a system dialog, which pauses
     * the activity and leaves the test staring at "no compose hierarchies". Granted up front.
     */
    private fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    protected fun launch(intent: Intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)) {
        check(scenario == null) { "The activity is already running" }
        scenario = ActivityScenario.launch(intent)
        // Barrier, not a no-op: it blocks here until the main thread has drained, so the
        // first composition happens there. The Compose test framework composes on an
        // unconfined dispatcher, which runs on whichever thread resumes it, and this one is
        // the instrumentation thread, which has no Looper for a lazy list's prefetch
        // scheduler to take a Choreographer from.
        onActivity { }
    }

    /** Destroys and recreates the activity, as a configuration change would. */
    protected fun recreate() {
        checkNotNull(scenario).recreate()
    }

    /** Delivers a new intent to the running activity, as a tapped link would. */
    protected fun deliver(intent: Intent) {
        onActivity { it.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    }

    protected fun onActivity(block: (MainActivity) -> Unit) {
        checkNotNull(scenario).onActivity(block)
    }

    /** Presses the system back button on the activity. */
    protected fun pressBack() {
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }
}
