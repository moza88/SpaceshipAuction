package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.contracts.AuctionContract
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

@StartableByRPC
class AuctionSettlementFlow(private val auctionId: UUID,
                            private val amount: Amount<Currency>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(AuctionDvPFlow(auctionId, amount))
        subFlow(AuctionExitFlow(auctionId))
    }

}