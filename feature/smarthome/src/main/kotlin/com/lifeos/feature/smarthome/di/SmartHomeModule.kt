package com.lifeos.feature.smarthome.di

import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.smarthome.data.HaActionHandler
import com.lifeos.feature.smarthome.data.HomeSceneRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SmartHomeModule {

    @Binds
    @IntoSet
    abstract fun bindHomeSceneRule(impl: HomeSceneRule): CrossModuleRule

    @Binds
    @IntoSet
    abstract fun bindHaActionHandler(impl: HaActionHandler): LifeActionHandler
}
