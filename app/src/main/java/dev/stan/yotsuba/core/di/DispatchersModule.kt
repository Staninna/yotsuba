package dev.stan.yotsuba.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The one injectable dispatcher: CPU work off the main thread. Tests hand ViewModels their own. */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides fun computeDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
