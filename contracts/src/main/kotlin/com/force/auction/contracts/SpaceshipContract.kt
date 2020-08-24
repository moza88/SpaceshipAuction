package com.force.auction.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class SpaceshipContract : Contract {
    companion object {
        const val ID = "com.force.auction.contracts.SpaceshipContract"
    }

    override fun verify(tx: LedgerTransaction) {
        TODO("Not yet implemented")
    }

    interface Commands : CommandData {
        class TransferSpaceship : Commands
        class CreateSpaceship : Commands
    }
}