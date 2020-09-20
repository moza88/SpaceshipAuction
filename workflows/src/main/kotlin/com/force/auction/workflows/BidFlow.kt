package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.contracts.AuctionContract
import com.force.auction.states.AuctionState
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

@StartableByRPC
@InitiatingFlow
class BidFlow(
        private val auctionId : UUID,
        private val bidAmount : Amount<Currency>
        ): FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        //Getting a List of Auction States
        val auctionStateAndRefs = serviceHub.vaultService.queryBy<AuctionState>().states

        //Filtering the list of Auction States for the auction ID reference
        val inputStateAndRef = auctionStateAndRefs.filter {
            val auctionState = it.state.data
            auctionState.auctionId == this.auctionId
        }[0]

        //Use the reference above to pull the Input State
        val inputState = inputStateAndRef.state.data

        val output = inputState.copy(highestBid = bidAmount, highestBidder = ourIdentity)

        //Get Notary
        val notary = inputStateAndRef.state.notary

        //Pull Command from the Auction Contract interface
        val command = AuctionContract.Commands.Bid()

        //Build the transaction with the notary, input, output, and command
        val txBuilder = TransactionBuilder(notary)
                .addInputState(inputStateAndRef)
                .addOutputState(output)
                .addCommand(command, ourIdentity.owningKey)

        //Verify Transaction
        txBuilder.verify(serviceHub)

        //Create the bidder session, the bidder session excludes initiator of this flow and adds in the auctioneer
        val bidderSession = inputState.bidders
                .minus(ourIdentity)
                .plus(inputState.auctioneer)
                .map {
                    initiateFlow(it)
                }

        //Sign Transaction
        val stx = serviceHub.signInitialTransaction(txBuilder)

        //Notarize and "FINALIZE" the transaction
        return subFlow(FinalityFlow(stx, bidderSession))

    }
}

//The other side responds back to the BidFlow
@InitiatedBy(BidFlow::class)
class BidFlowResponder(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(counterPartySession))
    }
}
