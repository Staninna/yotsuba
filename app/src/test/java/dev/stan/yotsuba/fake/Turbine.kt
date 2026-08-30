package dev.stan.yotsuba.fake

import app.cash.turbine.TurbineTestContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Runs every pending coroutine on [scheduler], then returns the newest emission. */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TurbineTestContext<T>.latest(scheduler: TestCoroutineScheduler): T {
    scheduler.advanceUntilIdle()
    return expectMostRecentItem()
}

/** Like [latest] but without running the clock forward: for states behind a timer. */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TurbineTestContext<T>.now(scheduler: TestCoroutineScheduler): T {
    scheduler.runCurrent()
    return expectMostRecentItem()
}
