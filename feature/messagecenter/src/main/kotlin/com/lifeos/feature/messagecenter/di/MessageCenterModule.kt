package com.lifeos.feature.messagecenter.di

import com.lifeos.core.service.CrossModuleRule
import com.lifeos.feature.messagecenter.rules.TrackingNumberRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MessageCenterModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTrackingNumberRule(impl: TrackingNumberRule): CrossModuleRule
}
