package com.lifeos.core.service.di

import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeActionHandler
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the multibound sets so they exist even before any feature contributes. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ServiceModule {

    @Multibinds
    abstract fun actionHandlers(): Set<LifeActionHandler>

    @Multibinds
    abstract fun rules(): Set<CrossModuleRule>
}
