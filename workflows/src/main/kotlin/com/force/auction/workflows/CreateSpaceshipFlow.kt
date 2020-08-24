package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.contracts.SpaceshipContract
import com.force.auction.states.Spaceship
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class CreateSpaceshipFlow(
        private val name: String,
        private val description: String,
        private val imageUrl: String,
        private val batteryLifeHours: String,
        private val speedMPH: Double,
        private val size: String,
        private val luxury: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        //Get the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,P=Venus")) // METHOD 2

        //Create desired output state
        val output = Spaceship(UniqueIdentifier(), name, description, imageUrl, batteryLifeHours, speedMPH, size, luxury, ourIdentity)

        val command = SpaceshipContract.Commands.CreateSpaceship()

        //Build the transaction with notary, output, and command
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(output)
                .addCommand(command, listOf(ourIdentity.owningKey))

        //Verify transaction
        txBuilder.verify(serviceHub)

        //Sign the transaction
        val stx = serviceHub.signInitialTransaction(txBuilder)

        //Notarize the transaction and record the state in the ledger
        return subFlow(FinalityFlow(stx, listOf()))
    }


}