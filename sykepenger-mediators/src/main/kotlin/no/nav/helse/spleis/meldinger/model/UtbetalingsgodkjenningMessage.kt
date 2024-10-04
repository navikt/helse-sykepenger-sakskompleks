package no.nav.helse.spleis.meldinger.model

import java.util.UUID
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import no.nav.helse.spleis.IHendelseMediator

// Understands a JSON message representing a Godkjenning-behov
internal class UtbetalingsgodkjenningMessage(packet: JsonMessage) : BehovMessage(packet) {
    private val utbetalingId = packet["utbetalingId"].asText()
    private val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val saksbehandler = packet["@løsning.${Godkjenning.name}.saksbehandlerIdent"].asText()
    private val saksbehandlerEpost = packet["@løsning.${Godkjenning.name}.saksbehandlerEpost"].asText()
    private val godkjenttidspunkt = packet["@løsning.${Godkjenning.name}.godkjenttidspunkt"].asLocalDateTime()
    private val utbetalingGodkjent = packet["@løsning.${Godkjenning.name}.godkjent"].asBoolean()
    private val automatiskBehandling = packet["@løsning.${Godkjenning.name}.automatiskBehandling"].asBoolean()

    private val utbetalingsgodkjenning
        get() = Utbetalingsgodkjenning(
            meldingsreferanseId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            utbetalingId = UUID.fromString(utbetalingId),
            vedtaksperiodeId = vedtaksperiodeId,
            saksbehandler = saksbehandler,
            saksbehandlerEpost = saksbehandlerEpost,
            utbetalingGodkjent = utbetalingGodkjent,
            godkjenttidspunkt = godkjenttidspunkt,
            automatiskBehandling = automatiskBehandling
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, utbetalingsgodkjenning, context)
    }
}
