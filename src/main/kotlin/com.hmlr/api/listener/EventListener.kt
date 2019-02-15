package com.hmlr.api.listener

import com.hmlr.api.common.models.toDTO
import com.hmlr.api.rpcClient.CORDA_VARS
import com.hmlr.model.*
import com.hmlr.states.LandTitleState
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.ContractState
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import khttp.put
import org.json.JSONObject

class EventListenerRPC {

    companion object {
        val logger: Logger = loggerFor<EventListenerRPC>()
    }


    private fun processState(state: ContractState) {
        when (state) {
            is LandTitleState -> {
                if (state.status == LandTitleStatus.TRANSFERRED) {
                    logger.info("Processing title ${state.titleID}.")

                    val titleDTO = JSONObject()

                    val ownerAddressDTO = JSONObject()
                    ownerAddressDTO.put("house_name_number", state.landTitleProperties.owner.address.houseNumber)
                    ownerAddressDTO.put("street", state.landTitleProperties.owner.address.streetName)
                    ownerAddressDTO.put("town_city", state.landTitleProperties.owner.address.city)
                    ownerAddressDTO.put("county", state.landTitleProperties.owner.address.county)
                    ownerAddressDTO.put("country", state.landTitleProperties.owner.address.country)
                    ownerAddressDTO.put("postcode", state.landTitleProperties.owner.address.postalCode)

                    val ownerDTO = JSONObject()
                    ownerDTO.put("identity", state.landTitleProperties.owner.userID.toInt())
                    ownerDTO.put("first_name", state.landTitleProperties.owner.forename)
                    ownerDTO.put("last_name", state.landTitleProperties.owner.surname)
                    ownerDTO.put("email_address", state.landTitleProperties.owner.email)
                    ownerDTO.put("phone_number", state.landTitleProperties.owner.phone)
                    ownerDTO.put("type", state.landTitleProperties.owner.userType.toString().toLowerCase())
                    ownerDTO.put("address", ownerAddressDTO)

                    titleDTO.put("owner", ownerDTO)

                    titleDTO.put("restrictions", state.restrictions.map { JSONObject(it.toDTO()) })
                    titleDTO.put("charges", state.charges.map { JSONObject(it.toDTO()) })

                    if (state.lastSoldValue != null) {
                        val priceDTO = JSONObject()
                        priceDTO.put("amount", state.lastSoldValue!!.quantity)
                        priceDTO.put("currency_code", state.lastSoldValue!!.token.currencyCode)
                        titleDTO.put("price_history", priceDTO)
                    }

                    val titleRequest = put("${System.getenv("TITLE_API_URL")}/titles/${state.titleID}", timeout = 15.0, data = titleDTO.toString(), headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json"))
                    if (titleRequest.statusCode == 200) {
                        logger.info("${state.titleID} transfer to " +
                                "${state.landTitleProperties.owner.forename} ${state.landTitleProperties.owner.surname} " +
                                "(${state.landTitleProperties.owner.userID}) " +
                                "has been registered with HMLR."
                        )
                    } else {
                        logger.info("${state.titleID} transfer to " +
                                "${state.landTitleProperties.owner.forename} ${state.landTitleProperties.owner.surname} " +
                                "(${state.landTitleProperties.owner.userID}) " +
                                "failed to be registered with HMLR.\n" +
                                "Response:\n${titleRequest.text}\n" +
                                "Request data:\n$titleDTO"
                        )
                    }
                }
            }
        }
    }

    fun run() {
        val nodeIpAndPort = "${System.getenv(CORDA_VARS.CORDA_NODE_HOST)}:${System.getenv(CORDA_VARS.CORDA_NODE_RPC_PORT)}"
        val nodeAddress = NetworkHostAndPort.parse(nodeIpAndPort)

        logger.info("Connecting to RPC: ${System.getenv(CORDA_VARS.CORDA_USER_NAME)}@${System.getenv(CORDA_VARS.CORDA_NODE_HOST)}:${System.getenv(CORDA_VARS.CORDA_NODE_RPC_PORT).toInt()}")
        val client = CordaRPCClient(nodeAddress)

        val nodeUsername = System.getenv(CORDA_VARS.CORDA_USER_NAME)
        val nodePassword = System.getenv(CORDA_VARS.CORDA_USER_PASSWORD)
        val proxy = client.start(nodeUsername, nodePassword).proxy

        val (snapshot, updates) = proxy.vaultTrack(LandTitleState::class.java)

        //snapshot.states.forEach {}
        updates.toBlocking().subscribe { update ->
            update.produced.forEach { processState(it.state.data) }
        }
    }
}
