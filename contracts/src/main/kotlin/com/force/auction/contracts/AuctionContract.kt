package com.force.auction.contracts

import com.force.auction.states.AuctionState
import com.force.auction.states.Spaceship
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

class AuctionContract : Contract {

    //Used to identify our contract with building a transaction
    companion object{
        const val ID = "com.force.auction.contracts"
    }

    override fun verify(tx: LedgerTransaction) {

        //Transaction is only valid if it's not empty
        if(tx.commands.isEmpty()) {
            throw IllegalArgumentException("One command Expected")
        }

        val command = tx.commands[0]

        when (command.value) {

            is Commands.Bid -> requireThat {
                "One Input Expected" using (tx.inputStates.size == 1)
                "One Output Expected" using (tx.outputStates.size == 1)

                //If the input is active then the auction has ended
                val input = tx.inputsOfType<AuctionState>()[0]
                "Auction has Ended" using (input.active)

                //If the output's highest bid is greater than the base price we began with
                val output = tx.outputsOfType<AuctionState>()[0]
                "Bid Price should be greater than base price" using (output.highestBid!!.quantity >= input.basePrice.quantity)

                //The auction has started and the bid should be greater than the last bid
                if(input.highestBid != null) {
                    "Bid Price should be greater than previous highest bid" using (output.highestBid.quantity >= input.highestBid.quantity)
                }
            }

            is Commands.EndAuction -> requireThat {
                //We need at least one buyer for the auction of the item to end
                "One Output Expected" using (tx.outputStates.size == 1)

                //Requiring that the auctioneer signs the transaction
                val output = tx.outputsOfType<AuctionState>()[0]
                val commandEnd = tx.commandsOfType<Commands.EndAuction>()[0]

                "Auctioneer Signature Required" using (commandEnd.signers.contains(output.auctioneer.owningKey))
            }

            is Commands.Settlement -> requireThat {
                val input = tx.inputsOfType<AuctionState>()[0]
                val commandSett = tx.commandsOfType<Commands.Settlement>()[0]

                "Auction is Active" using (!input.active)

                //Checking if we have the auctioneer's signature and winner's signatures, the winner is unknown so there is a ? after winner
                "Auctioneer and Winner must Sign" using (commandSett.signers.contains(input.auctioneer.owningKey) && commandSett.signers.contains(input.winner?.owningKey))
            }

            is Commands.Exit -> requireThat {
                val input = tx. inputsOfType<AuctionState>()[0]

                val commandExit = tx.commandsOfType<Commands.Exit>()[0]

                val spaceship = tx.referenceInputRefsOfType<Spaceship>()[0].state.data

                "Auction is Active" using (!input.active)

                if(input.winner != null) {
                    "Auctioneer and Winner must Sign" using (commandExit.signers.contains(input.auctioneer.owningKey) && commandExit.signers.contains(input.winner.owningKey))
                }

                //The auction isn't settled if the original owner is not the winner
                "Auction not settled yet" using (spaceship.owner.owningKey == input.winner!!.owningKey)

                }
            }
        }



    interface Commands : CommandData {
        class CreateAuction : Commands
        class Bid : Commands
        class EndAuction : Commands
        class Settlement : Commands
        class Exit : Commands
    }

}