package com.lifeos.feature.route.di

import com.lifeos.core.service.CrossModuleRule
import com.lifeos.feature.route.data.LeaveByRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RouteModule {

    @Binds
    @IntoSet
    abstract fun bindLeaveByRule(impl: LeaveByRule): CrossModuleRule
}
