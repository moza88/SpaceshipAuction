package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.contracts.AuctionContract
import com.force.auction.states.AuctionState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@InitiatingFlow
@StartableByRPC
class CreateAuctionFlow(
        private val basePrice: Amount<Currency>,
        private val auctionItem: UUID,
        private val bidDeadline: LocalDateTime) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        //Get notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,P=Venus")) // METHOD 2

        //Stating that the party that creates the auction, is the auction
        //So the auctioneer is the identity of the party initiating the create auction flow
        val auctioneer = ourIdentity

        //All nodes except the auctioneer and notary can be bidders in the auction
        val bidders = serviceHub.networkMapCache.allNodes.map {
            it.legalIdentities.get(0)
        } - auctioneer - notary

        //Design output state
        //The nulls are the values that are yet to be determined by the auction's results
        //We don't know who is going to be our winning bidder, what's the highest bid, etc.
        val output = AuctionState(
                LinearPointer(UniqueIdentifier(null, auctionItem), LinearState::class.java),
                UUID.randomUUID(),
                basePrice,
        null,
        null,
                bidDeadline.atZone(ZoneId.systemDefault()).toInstant(),
        null,
        true,
                auctioneer,
                bidders,
        null)

        //Build transaction
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(output)
                .addCommand(AuctionContract.Commands.CreateAuction(), listOf(auctioneer.owningKey))

        //Verify Transaction
        txBuilder.verify(serviceHub)

        //Sign the transaction
        val stx = serviceHub.signInitialTransaction(txBuilder)

        //Create a bidder session, which is the bidder initiation the flow
        val bidderSessions = bidders.map{initiateFlow(it)}

        //Notarize and record transaction
        return subFlow(FinalityFlow(stx, bidderSessions))
    }

}

@InitiatedBy(CreateAuctionFlow::class)
class CreateAuctionFlowResponder(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        return subFlow(ReceiveFinalityFlow(counterPartySession))
    }
}