package dev.stan.yotsuba.core.lock

import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.fake.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockTest {

    private class Env(initial: Settings) {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val settings = FakeSettings(initial)
        var now = 1_000L
        val lock = AppLock(settings, scope, now = { now })

        fun settle() = dispatcher.scheduler.advanceUntilIdle()
    }

    @Test fun `not ready until the settings have been read once`() = runTest {
        val env = Env(Settings(appLock = true))
        assertEquals(false, env.lock.ready.value)
        assertEquals(false, env.lock.locked.value)
        env.settle()
        assertEquals(true, env.lock.ready.value)
    }

    @Test fun `a fresh process starts locked exactly when the setting is on`() = runTest {
        val on = Env(Settings(appLock = true)).apply { settle() }
        assertEquals(true, on.lock.locked.value)
        val off = Env(Settings(appLock = false)).apply { settle() }
        assertEquals(false, off.lock.locked.value)
    }

    @Test fun `unlock clears the lock`() = runTest {
        val env = Env(Settings(appLock = true)).apply { settle() }
        env.lock.unlock()
        assertEquals(false, env.lock.locked.value)
    }

    @Test fun `with no delay, leaving and coming back locks right away`() = runTest {
        val env = Env(Settings(appLock = true, appLockDelaySeconds = 0)).apply { settle() }
        env.lock.unlock()
        env.lock.onAppStop()
        env.lock.onAppStart()
        assertEquals(true, env.lock.locked.value)
    }

    @Test fun `with a delay, a short trip away does not lock but a long one does`() = runTest {
        val env = Env(Settings(appLock = true, appLockDelaySeconds = 30)).apply { settle() }
        env.lock.unlock()
        env.lock.onAppStop()
        env.now += 29_999
        env.lock.onAppStart()
        assertEquals(false, env.lock.locked.value)

        env.lock.onAppStop()
        env.now += 30_000
        env.lock.onAppStart()
        assertEquals(true, env.lock.locked.value)
    }

    @Test fun `the first start of a process never locks on its own`() = runTest {
        val env = Env(Settings(appLock = false)).apply { settle() }
        env.lock.onAppStart()
        assertEquals(false, env.lock.locked.value)
    }

    @Test fun `with the lock off, leaving and coming back never locks`() = runTest {
        val env = Env(Settings(appLock = false)).apply { settle() }
        env.lock.onAppStop()
        env.now += 1_000_000
        env.lock.onAppStart()
        assertEquals(false, env.lock.locked.value)
    }

    @Test fun `turning the setting on later takes effect on the next return`() = runTest {
        val env = Env(Settings(appLock = false)).apply { settle() }
        env.settings.state.value = Settings(appLock = true)
        env.settle()
        assertEquals(false, env.lock.locked.value)
        env.lock.onAppStop()
        env.lock.onAppStart()
        assertEquals(true, env.lock.locked.value)
    }
}
