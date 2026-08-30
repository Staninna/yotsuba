package dev.stan.yotsuba.core.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockPrompterTest {

    private class Env {
        var shown = 0
        var unlocked = 0
        private var pending: ((UnlockResult) -> Unit)? = null
        val prompter = LockPrompter(title = { "t" }, onUnlocked = { unlocked++ }, show = { _, cb ->
            shown++
            pending = cb
        })

        fun end(result: UnlockResult) {
            val cb = pending ?: error("no prompt open")
            pending = null
            cb(result)
        }
    }

    @Test fun `every resume prompts while locked`() {
        val env = Env()
        env.prompter.onResume()
        assertEquals(1, env.shown)
        env.end(UnlockResult.FAILED)
        env.prompter.onStop()
        env.prompter.onResume()
        assertEquals(2, env.shown)
    }

    @Test fun `a prompt the system dismissed while backgrounded comes back on resume`() {
        val env = Env()
        env.prompter.onResume()
        env.prompter.onStop()            // activity stops with the prompt still open
        env.end(UnlockResult.FAILED)     // then the system cancels it
        env.prompter.onResume()
        assertEquals(2, env.shown)
    }

    @Test fun `the user's own cancel skips exactly one resume`() {
        val env = Env()
        env.prompter.onResume()
        env.prompter.onStop()            // keyguard path: our activity stopped behind the PIN screen
        env.end(UnlockResult.CANCELLED)
        env.prompter.onResume()          // the resume that cancel caused
        assertEquals(1, env.shown)
        env.prompter.onStop()
        env.prompter.onResume()
        assertEquals(2, env.shown)
    }

    @Test fun `a cancel over a resumed activity does not eat the next return`() {
        val env = Env()
        env.prompter.onResume()
        env.end(UnlockResult.CANCELLED)  // biometric overlay: no lifecycle event follows
        env.prompter.onStop()            // user leaves later
        env.prompter.onResume()
        assertEquals(2, env.shown)
    }

    @Test fun `only one prompt at a time and passing unlocks`() {
        val env = Env()
        env.prompter.prompt()
        env.prompter.onResume()
        assertEquals(1, env.shown)
        env.end(UnlockResult.PASSED)
        assertEquals(1, env.unlocked)
    }
}
