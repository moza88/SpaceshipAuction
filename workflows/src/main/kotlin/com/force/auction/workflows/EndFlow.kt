package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.contracts.AuctionContract
import com.force.auction.states.AuctionState
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@InitiatingFlow
@StartableByRPC
@SchedulableFlow
class EndFlow(private val auctionId: UUID) : FlowLogic<SignedTransaction?>() {

    @Suspendable
    override fun call(): SignedTransaction? {

        //Get Command
        val command = AuctionContract.Commands.EndAuction()

        //Get List of Auction State references
        val auctionStateAndRefs = serviceHub.vaultService.queryBy<AuctionState>().states

        //Get the Input State for the Auction Id passed in
        val inputStateAndRef = auctionStateAndRefs.filter {
            val auctionState = it.state.data
            auctionState.auctionId == this.auctionId
        }[0]

        val inputState = inputStateAndRef.state.data

        //Get Notary for the item with the auction id passed in
        val notary = inputStateAndRef.state.notary

        //Get Output, only the auctioneer can find the output
        if(ourIdentity.owningKey == inputState.auctioneer.owningKey) {
            val output = inputState.copy(
                    active = false, //Auction is no longer happening
                    highestBidder = inputState.highestBidder,
                    winner = inputState.winner,
                    winningBid = inputState.highestBid)


            //Build Transaction
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(inputStateAndRef)
                    .addOutputState(output)
                    .addCommand(command, ourIdentity.owningKey)

            //Verify Transaction
            txBuilder.verify(serviceHub)

            //Sign Transaction
            val stx = serviceHub.signInitialTransaction(txBuilder)

            //Create the bidder session
            val bidderSesssion = inputState.bidders.map {
                initiateFlow(it)
            }

            //Notarize Transaction
            return subFlow(FinalityFlow(stx, bidderSesssion))
        }
        return null
    }
}

@InitiatedBy(EndFlow::class)
class EndFlowResponder(val counterPartySession:FlowSession) : FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {

        return subFlow(ReceiveFinalityFlow(counterPartySession))
    }

}