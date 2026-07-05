package com.lifeos.feature.finance.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.CrossModuleRule
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeEvent
import javax.inject.Inject

/** R8 (§3): ReceiptScanned into transaction + warranty + filed doc. */
class ReceiptRule @Inject constructor() : CrossModuleRule {
    override val id = "receipt-to-finance"

    override fun matches(event: LifeEvent) = event is LifeEvent.ReceiptScanned

    override suspend fun produce(event: LifeEvent): List<LifeAction> {
        val receipt = event as LifeEvent.ReceiptScanned
        return listOf(
            LifeAction.RecordReceipt(
                docId = receipt.docId,
                merchant = receipt.merchant,
                totalCents = receipt.totalCents,
                warrantyMonths = receipt.warrantyMonths,
                source = SourceRef(LifeModule.IMAGE_REASONING, receipt.docId.toString()),
            ),
        )
    }
}

internal class FinanceActionHandler @Inject constructor(
    private val financeRepository: FinanceRepository,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction) = action is LifeAction.RecordReceipt

    override suspend fun execute(action: LifeAction): LifeResult<Long?> =
        financeRepository.recordReceipt(action as LifeAction.RecordReceipt)
}
