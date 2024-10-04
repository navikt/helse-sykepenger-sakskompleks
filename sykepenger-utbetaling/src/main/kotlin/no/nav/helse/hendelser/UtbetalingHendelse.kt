package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.utbetalingslinjer.Oppdragstatus

interface UtbetalingHendelseHendelse : IAktivitetslogg {
    val utbetalingId: UUID
    val fagsystemId: String
    val avstemmingsnøkkel: Long
    val overføringstidspunkt: LocalDateTime
    val melding: String
    val status: Oppdragstatus
}