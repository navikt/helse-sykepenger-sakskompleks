package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.utbetalingslinjer.Utbetaling

class AnnullerUtbetaling(
    meldingsreferanseId: UUID,
    aktørId: String,
    fødselsnummer: String,
    organisasjonsnummer: String,
    override val utbetalingId: UUID,
    private val saksbehandlerIdent: String,
    private val saksbehandlerEpost: String,
    internal val opprettet: LocalDateTime,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, aktivitetslogg), AnnullerUtbetalingHendelse {
    override val vurdering: Utbetaling.Vurdering = Utbetaling.Vurdering(true, saksbehandlerIdent, saksbehandlerEpost, opprettet, false)

    fun erAutomatisk() = this.saksbehandlerIdent == "Automatisk behandlet"

    override fun avsender() = SAKSBEHANDLER
}
