package dev.stan.yotsuba.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** CPU work off the main thread. Qualified so an unqualified dispatcher injection fails at build time. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class ComputeDispatcher

/** Blocking file and ContentResolver work. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class IoDispatcher

/** A scope that lives as long as the process, for work no screen owns. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/** The coroutine bindings: compute and io dispatchers and one process scope. Tests hand ViewModels their own. */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @ComputeDispatcher
    fun computeDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
