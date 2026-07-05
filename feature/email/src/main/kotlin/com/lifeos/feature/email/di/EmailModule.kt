package com.lifeos.feature.email.di

import com.lifeos.core.service.CrossModuleRule
import com.lifeos.feature.email.data.DefaultEmailRepository
import com.lifeos.feature.email.data.EmailRepository
import com.lifeos.feature.email.data.InviteEmailRule
import com.lifeos.feature.email.data.InvoiceEmailRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EmailModule {

    @Binds
    @Singleton
    abstract fun bindEmailRepository(impl: DefaultEmailRepository): EmailRepository

    @Binds
    @IntoSet
    abstract fun bindInvoiceRule(impl: InvoiceEmailRule): CrossModuleRule

    @Binds
    @IntoSet
    abstract fun bindInviteRule(impl: InviteEmailRule): CrossModuleRule
}
