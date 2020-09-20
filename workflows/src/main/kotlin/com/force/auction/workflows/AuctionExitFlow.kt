package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.contracts.AuctionContract
import com.force.auction.states.AuctionState
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.util.*

@InitiatingFlow
@StartableByRPC
class AuctionExitFlow(private val auctionId: UUID) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        //Query the vault to get all the Auction States
        val auctionStateAndRefs = serviceHub.vaultService.queryBy<AuctionState>().states

        //Use the AuctionState to fetch all results based on auction Id
        val inputStateAndRefs = auctionStateAndRefs.filter {
            val auctionState = it.state.data
            auctionState.auctionId == this.auctionId
        }[0]

        val auctionState = inputStateAndRefs.state.data

        //Figure out who is the signers of the transaction based on whether the auction has received bids.
        //Highest bidder must sign to avoid consuming an auction that's not settled yet
        val signers = listOf(auctionState.auctioneer.owningKey)

        if(auctionState.winner != null) {
            signers.plus(auctionState.winner!!.owningKey)
        }

        //Build the transaction with the notary, input state reference, and the command (along with the signers)
        val txBuilder = TransactionBuilder(inputStateAndRefs.state.notary)
                .addInputState(inputStateAndRefs)
                .addCommand(AuctionContract.Commands.Exit(), signers)

        //Verify the transaction
        txBuilder.verify(serviceHub)

        //Sign the transaction
        var stx = serviceHub.signInitialTransaction(txBuilder)

        if(auctionState.winner != null) {

            if(auctionState.auctioneer == ourIdentity) {

                val winnerSession = initiateFlow(auctionState.winner!!)

                winnerSession.send(true)

                stx = subFlow(CollectSignaturesFlow(stx, listOf(winnerSession)))

            } else {

                val auctioneerSession = initiateFlow(auctionState.auctioneer)

                auctioneerSession.send(true)

                stx = subFlow(CollectSignaturesFlow(stx, listOf(auctioneerSession)))
            }
        }

        val allSession: MutableList<FlowSession> = mutableListOf<FlowSession>().toMutableList()

        for (party in auctionState.participants.filter { it != ourIdentity }) {

            val session = initiateFlow(party)
            session.send(false)
            allSession += session
        }

        return subFlow(FinalityFlow(stx, allSession))
    }
}

@InitiatedBy(AuctionExitFlow::class)
class AuctionExitFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call():SignedTransaction {

        val flag = counterpartySession.receive<Boolean>().unwrap{ it -> it}

        if(flag) {

            subFlow(object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {

                }
            })
        }
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}
