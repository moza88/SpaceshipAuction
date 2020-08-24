package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.states.AuctionState
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class AuctionDvPFlow(
        private val auctionId : UUID,
        private val payment : Amount<Currency>): FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        //Get Input
        val auctionStateAndRefs = serviceHub.vaultService.queryBy<AuctionState>().states

        val inputStateAndRefs = auctionStateAndRefs.filter {
            val auctionState = it.state.data
            auctionState.auctionId == this.auctionId
        }[0]

        val inputState = inputStateAndRefs.state.data

        //Get Output

        //Get Notary

        //Get Command

        //Build Transaction

        //Verify Transaction

        //Sign Transaction

        //Initiate Flow Session

        //Notarize

    }


}
