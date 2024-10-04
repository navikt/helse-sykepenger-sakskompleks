package no.nav.helse.spleis.meldinger.model

import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.utbetalingslinjer.Oppdragstatus

internal class UtbetalingMessage(packet: JsonMessage) : BehovMessage(packet) {
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val fagsystemId = packet["${Utbetaling.name}.fagsystemId"].asText().trim()
    private val utbetalingId = packet["utbetalingId"].asText().toUUID()

    private val status: Oppdragstatus = enumValueOf(packet["@løsning.${Utbetaling.name}.status"].asText())
    private val beskrivelse = packet["@løsning.${Utbetaling.name}.beskrivelse"].asText()
    private val avstemmingsnøkkel = packet["@løsning.${Utbetaling.name}.avstemmingsnøkkel"].asLong()
    private val overføringstidspunkt = packet["@løsning.${Utbetaling.name}.overføringstidspunkt"].asLocalDateTime()
    override val skalDuplikatsjekkes = false

    private val utbetaling
        get() = UtbetalingHendelse(
            meldingsreferanseId = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            orgnummer = organisasjonsnummer,
            fagsystemId = fagsystemId,
            utbetalingId = utbetalingId,
            status = status,
            melding = beskrivelse,
            avstemmingsnøkkel = avstemmingsnøkkel,
            overføringstidspunkt = overføringstidspunkt
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        if (status == Oppdragstatus.OVERFØRT) return // sender bare inn kvitteringer til modellen
        mediator.behandle(this, utbetaling, context)
    }
}
