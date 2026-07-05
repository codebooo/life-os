package com.lifeos.feature.dhl.di

import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.dhl.data.DefaultPackagesRepository
import com.lifeos.feature.dhl.data.DhlActionHandler
import com.lifeos.feature.dhl.data.PackagesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DhlModule {

    @Binds
    @Singleton
    abstract fun bindPackagesRepository(impl: DefaultPackagesRepository): PackagesRepository

    @Binds
    @IntoSet
    abstract fun bindDhlActionHandler(impl: DhlActionHandler): LifeActionHandler
}
