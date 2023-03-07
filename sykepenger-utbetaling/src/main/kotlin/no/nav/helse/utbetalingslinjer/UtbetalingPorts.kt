package no.nav.helse.utbetalingslinjer

import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

interface UtbetalingHendelsePort : IAktivitetslogg {
    val avstemmingsnøkkel: Long
    val overføringstidspunkt: LocalDateTime
    val status: Oppdragstatus
    fun erRelevant(fagsystemId: String): Boolean
    fun erRelevant(arbeidsgiverFagsystemId: String, personFagsystemId: String, utbetaling: UUID): Boolean
    fun valider()
    fun skalForsøkesIgjen(): Boolean
}

interface GrunnbeløpsreguleringPort: IAktivitetslogg {
    fun erRelevant(fagsystemId: String): Boolean
    fun fødselsnummer(): String
    fun organisasjonsnummer(): String
}

interface SimuleringPort: IAktivitetslogg {
    val simuleringResultat: SimuleringResultat?
    fun valider(oppdrag: Oppdrag): SimuleringPort
    fun erSimulert(fagområde: Fagområde, fagsystemId: String): Boolean
    fun erRelevantForUtbetaling(id: UUID): Boolean
}

interface UtbetalingpåminnelsePort: IAktivitetslogg {
    fun erRelevant(id: UUID): Boolean
    fun harOversteget(makstid: Duration): Boolean
    fun gjelderStatus(tilstand: Utbetalingstatus): Boolean
}

interface AnnullerUtbetalingPort: IAktivitetslogg {
    fun vurdering(): Utbetaling.Vurdering
    fun erRelevant(fagsystemId: String): Boolean
}

interface UtbetalingsgodkjenningPort: IAktivitetslogg {
    fun erRelevant(id: UUID): Boolean
    fun valider()
    fun vurdering(): Utbetaling.Vurdering
}

interface Beløpkilde {
    fun arbeidsgiverbeløp(): Int
    fun personbeløp(): Int
}

interface UtbetalingVedtakFattetBuilder {
    fun utbetalingVurdert(tidspunkt: LocalDateTime): UtbetalingVedtakFattetBuilder
    fun utbetalingId(id: UUID): UtbetalingVedtakFattetBuilder
}