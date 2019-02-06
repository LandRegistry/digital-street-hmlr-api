package com.hmlr.api.controllers

import com.hmlr.api.common.VaultQueryHelperConsumer
import com.hmlr.api.common.models.*
import com.hmlr.api.rpcClient.NodeRPCConnection
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.loggerFor
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.hmlr.model.ChargeRestriction
import com.hmlr.states.LandAgreementState
import com.hmlr.states.PaymentConfirmationState
import com.hmlr.states.ProposedChargesAndRestrictionsState


@Suppress("unused")
@RestController
@RequestMapping("/api")
class ApiController(@Suppress("CanBeParameter") private val rpc: NodeRPCConnection) : VaultQueryHelperConsumer() {

    override val rpcOps = rpc.proxy
    override val myIdentity = rpcOps.nodeInfo().legalIdentities.first()

    companion object {
        private val logger = loggerFor<ApiController>()
    }

    /**
     * Return the node's name
     */
    @GetMapping(value = "/me", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun me() = mapOf("me" to myIdentity.toDTOWithName())

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GetMapping(value = "/peers", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun peers() = mapOf("peers" to rpcOps.networkMapSnapshot()
            .asSequence()
            .filter { nodeInfo -> nodeInfo.legalIdentities.first() != myIdentity }
            .map { it.legalIdentities.first().toDTOWithName() }
            .toList())

    /**
     * Gets a title's sales agreement
     */
    @GetMapping(value = "/titles/{title-number}/sales-agreement",
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getSalesAgreement(@PathVariable("title-number") titleNumber: String): ResponseEntity<Any?> {
        logger.info("GET /titles/$titleNumber/sales-agreement")

        vaultQueryHelper {
            val agreementStateAndInstant: StateAndInstant<LandAgreementState>? = getStateBy { it.state.data.titleID == titleNumber }

            //Return 404 if null
            agreementStateAndInstant ?: return ResponseEntity.notFound().build()

            //Get payment settler
            val paymentStateAndInstants: List<StateAndInstant<PaymentConfirmationState>> = getStatesBy { it.state.data.titleID == titleNumber }
            val referencedPaymentState = paymentStateAndInstants.first { it.state.landAgreementStateLinearId == agreementStateAndInstant.state.linearId.toString() }
            val paymentSettler = referencedPaymentState.state.settlingParty

            //Build the DTO
            val salesAgreementDTO = agreementStateAndInstant.state.toDTO(paymentSettler, agreementStateAndInstant.instant?.toLocalDateTime())

            //Return the DTO
            return ResponseEntity.ok().body(salesAgreementDTO)
        }
    }

    /**
     * Gets the restrictions on a title
     */
    @GetMapping(value = "/titles/{title-number}/restrictions",
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getRestrictionsOnTitle(@PathVariable("title-number") titleNumber: String,
                               @RequestParam("type", required = false) restrictionType: String?): ResponseEntity<Any?> {
        logger.info("GET /titles/$titleNumber/restrictions")

        vaultQueryHelper {
            //Get State and Instant
            val chargesAndRestrictionsStateAndInstant: StateAndInstant<ProposedChargesAndRestrictionsState>? = getStateBy { it.state.data.titleID == titleNumber }

            val restrictions = chargesAndRestrictionsStateAndInstant.let { it ->
                // Either return restrictions or empty array
                it?.state?.restrictions ?: return ResponseEntity.ok().body(listOf<Unit>())
            }.let { restrictions ->
                //Filter by restriction type if applicable
                if (restrictionType == null) restrictions else {
                    restrictions.filter { restriction ->
                        when (restriction) {
                            is ChargeRestriction -> restrictionType == ChargeRestrictionDTO.RESTRICTION_TYPE
                            else /*is Restriction*/ -> restrictionType == RestrictionDTO.RESTRICTION_TYPE
                        }
                    }
                }
            }

            //Return Restrictions DTO
            return ResponseEntity.ok().body(restrictions.map { it.toDTO() })
        }
    }

    /**
     * Gets the charge on a title
     */
    @GetMapping(value = "/titles/{title-number}/charges",
            produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getChargesOnTitle(@PathVariable("title-number") titleNumber: String): ResponseEntity<Any?> {
        logger.info("GET /titles/$titleNumber/charges")

        vaultQueryHelper {
            //Get State and Instant
            val chargesAndRestrictionsStateAndInstant: StateAndInstant<ProposedChargesAndRestrictionsState>? = getStateBy { it.state.data.titleID == titleNumber }

            //Return empty array if null
            chargesAndRestrictionsStateAndInstant ?: return ResponseEntity.ok().body(listOf<Unit>())

            //Build the DTOs
            val chargesDTO = chargesAndRestrictionsStateAndInstant.state.charges.map { it.toDTO() }

            //Return the DTOs
            return ResponseEntity.ok().body(chargesDTO)
        }
    }

    /**
     * Returns all titles
     */
    @GetMapping(value = "/titles", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getTitles(): ResponseEntity<Any?> {
        logger.info("GET /titles")

        vaultQueryHelper {
            //Build Title Transfer DTOs
            val titleTransferDTO = buildTitleTransferDTOs()

            //Return Title Transfer DTOs
            return ResponseEntity.ok().body(titleTransferDTO)
        }
    }

    /**
     * Returns a title
     */
    @GetMapping(value = "/titles/{title-number}", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getTitle(@PathVariable("title-number") titleNumber: String): ResponseEntity<Any?> {
        logger.info("GET /titles/$titleNumber")

        vaultQueryHelper {
            //Build Title Transfer DTO
            val titleTransferDTO = buildTitleTransferDTO(titleNumber)

            //Return 404 if null
            titleTransferDTO ?: return ResponseEntity.notFound().build()

            //Return Title Transfer DTO
            return ResponseEntity.ok().body(titleTransferDTO)
        }
    }

}