package com.force.auction.states

import com.force.auction.contracts.SpaceshipContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(SpaceshipContract::class)
data class Spaceship(
        override val linearId: UniqueIdentifier,
        val name: String,
        val description: String,
        val imageUrl: String,
        val batteryLifeHours: String,
        val speedMPH: Double,
        val size: String,
        val luxury: String,
        override val owner: AbstractParty,
        override val participants: List<AbstractParty> = listOf(owner)) : OwnableState, LinearState {

        override fun withNewOwner(newOwner: net.corda.core.identity.AbstractParty): net.corda.core.contracts.CommandAndState {
                return CommandAndState(SpaceshipContract.Commands.TransferSpaceship(), Spaceship(this.linearId, this.name, this.description, this.imageUrl, this.batteryLifeHours, this.speedMPH, this.size, this.luxury, newOwner))

        }
}