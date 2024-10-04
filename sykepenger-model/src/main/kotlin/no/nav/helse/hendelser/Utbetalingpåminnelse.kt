package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.*
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.utbetalingslinjer.Utbetalingstatus

class Utbetalingpåminnelse(
    meldingsreferanseId: UUID,
    aktørId: String,
    fødselsnummer: String,
    organisasjonsnummer: String,
    override val utbetalingId: UUID,
    private val antallGangerPåminnet: Int,
    override val status: Utbetalingstatus,
    private val endringstidspunkt: LocalDateTime,
    private val påminnelsestidspunkt: LocalDateTime
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, Aktivitetslogg()), UtbetalingpåminnelseHendelse
