package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.utbetalingslinjer.Oppdragstatus

class UtbetalingHendelse(
    meldingsreferanseId: UUID,
    aktørId: String,
    fødselsnummer: String,
    orgnummer: String,
    override val fagsystemId: String,
    override val utbetalingId: UUID,
    override val status: Oppdragstatus,
    override val melding: String,
    override val avstemmingsnøkkel: Long,
    override val overføringstidspunkt: LocalDateTime
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, orgnummer), UtbetalingHendelseHendelse