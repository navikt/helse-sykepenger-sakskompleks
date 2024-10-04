package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelser.AnnullerUtbetaling
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.spleis.IHendelseMediator

internal class AnnulleringMessage(packet: JsonMessage) : HendelseMessage(packet) {

    private val aktørId = packet["aktørId"].asText()
    override val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val utbetalingId = packet["utbetalingId"].asText().trim().toUUID()
    private val saksbehandler = Saksbehandler.fraJson(packet["saksbehandler"])
    private val annullerUtbetaling
        get() = AnnullerUtbetaling(
            id,
            aktørId,
            fødselsnummer,
            organisasjonsnummer,
            utbetalingId,
            saksbehandler.ident,
            saksbehandler.epostadresse,
            opprettet
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, annullerUtbetaling, context)
    }

    private class Saksbehandler(
        val epostadresse: String,
        val ident: String,
    ) {
        companion object {
            fun fraJson(jsonNode: JsonNode) = Saksbehandler(
                epostadresse = jsonNode["epostaddresse"].asText(),
                ident = jsonNode["ident"].asText()
            )
        }
    }
}
