package com.force.auction.workflows

import co.paralleluniverse.fibers.Suspendable
import com.force.auction.states.AuctionState
import com.force.auction.states.Spaceship
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandAndState
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.workflows.asset.CashUtils
import java.util.*
import javax.annotation.Signed

@StartableByRPC
@InitiatingFlow
class AuctionDvPFlow(
    private val auctionId: UUID,
    private val payment: Amount<Currency>
): FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call():SignedTransaction {

        //Query the vault to fetch a list of AuctionStates
        val auctionStateAndRefs = serviceHub.vaultService.queryBy<AuctionState>().states

        //Filter the list by the presented auction id
        val inputStateAndRef = auctionStateAndRefs.filter {
            val auctionState = it.state.data
            auctionState.auctionId == this.auctionId
        }[0]

        val auctionState = inputStateAndRef.state.data

        //Create Query Criteria to query the Spaceship, the Spaceship should correspond to the auction id or Unconsumed
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                null,
                listOf(inputStateAndRef.state.data.auctionItem.resolve(serviceHub).state.data.linearId.id),
                null,
                Vault.StateStatus.UNCONSUMED)

        //Query the vault to fetch the Spaceship that's Unconsumed and corresponds to the item in Auction (see queryCriteria)
        val spaceshipStateAndRef = serviceHub.vaultService.queryBy<Spaceship>(queryCriteria).states[0]

        //Get Notary
        val notary = serviceHub.networkMapCache.notaryIdentities.single()
        //val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary, L=London, C=GB"))

        val commandAndState = spaceshipStateAndRef.state.data.withNewOwner(auctionState.winner!!)

        val txBuilderPre = TransactionBuilder(notary)

        //Generating Spend for the Cash using CashUtils
        //CashUtil's generateSpend method is used to update the txBuilder with inputs and outputs that reflect the cash being spent.
        //A new keypair is generated to sign the transaction, so that the change returned to the send after the case is spent is untraceable
        //Flows the UTXO Model, similar to Bitcoin
        val txAndKeysPair = CashUtils.generateSpend(
                serviceHub,
                txBuilderPre,
                payment,
                ourIdentityAndCert,
                auctionState.auctioneer,
                emptySet())

        val txBuilder = txAndKeysPair.first

        //Building the transaction with the Spaceship input, output, the command
        txBuilder.addInputState(spaceshipStateAndRef)
                .addOutputState(commandAndState.ownableState)
                .addCommand(commandAndState.command, listOf(auctionState.auctioneer.owningKey))

        //Verifying Transaction
        txBuilder.verify(serviceHub)

        /*Signing the transaction with the new key pair generated
        - KeysToSign Generates the new keypair using our Identity
        - stx is when we sign the transaction with our new key pairs
         */
        val keysToSign = txAndKeysPair.second.plus(ourIdentity.owningKey)

        val stx = serviceHub.signInitialTransaction(txBuilder, keysToSign)

        //Collect the counterparty's signature
        val auctioneerFlow = initiateFlow(auctionState.auctioneer)

        val ftx = subFlow(CollectSignaturesFlow(stx, listOf(auctioneerFlow)))

        return subFlow(FinalityFlow(ftx, (auctioneerFlow)))

    }
}

@InitiatedBy(AuctionDvPFlow::class)
class AuctionDvPFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call():SignedTransaction {
        subFlow(object: SignTransactionFlow(counterpartySession) {
            @Throws(FlowException::class)
            override fun checkTransaction(stx: SignedTransaction) {
            }
        })
        return subFlow(ReceiveFinalityFlow(counterpartySession))
    }
}