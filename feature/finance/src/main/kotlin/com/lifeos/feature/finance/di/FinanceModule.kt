package com.lifeos.feature.finance.di

import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.feature.finance.data.DefaultFinanceRepository
import com.lifeos.feature.finance.data.FinanceActionHandler
import com.lifeos.feature.finance.data.FinanceRepository
import com.lifeos.feature.finance.data.ReceiptRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FinanceModule {

    @Binds
    @Singleton
    abstract fun bindFinanceRepository(impl: DefaultFinanceRepository): FinanceRepository

    @Binds
    @IntoSet
    abstract fun bindFinanceActionHandler(impl: FinanceActionHandler): LifeActionHandler

    @Binds
    @IntoSet
    abstract fun bindReceiptRule(impl: ReceiptRule): CrossModuleRule
}
