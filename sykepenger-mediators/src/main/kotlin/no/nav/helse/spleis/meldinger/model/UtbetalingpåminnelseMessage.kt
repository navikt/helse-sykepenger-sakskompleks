package no.nav.helse.spleis.meldinger.model

import java.util.UUID
import no.nav.helse.hendelser.Utbetalingpåminnelse
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.spleis.IHendelseMediator

// Understands a JSON message representing a UtbetalingpPåminnelse
internal class UtbetalingpåminnelseMessage(packet: JsonMessage) : HendelseMessage(packet) {

    private val utbetalingId = UUID.fromString(packet["utbetalingId"].asText())
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    override val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val antallGangerPåminnet = packet["antallGangerPåminnet"].asInt()
    private val status = packet["status"].asText()
    private val endringstidspunkt = packet["endringstidspunkt"].asLocalDateTime()
    private val påminnelsestidspunkt = packet["@opprettet"].asLocalDateTime()

    private val påminnelse
        get() = Utbetalingpåminnelse(
            meldingsreferanseId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            utbetalingId = utbetalingId,
            antallGangerPåminnet = antallGangerPåminnet,
            status = Utbetalingstatus.valueOf(status),
            endringstidspunkt = endringstidspunkt,
            påminnelsestidspunkt = påminnelsestidspunkt
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, påminnelse, context)
    }
}
