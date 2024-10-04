package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID

class Utbetalingsgodkjenning(
    meldingsreferanseId: UUID,
    aktørId: String,
    fødselsnummer: String,
    organisasjonsnummer: String,
    override val utbetalingId: UUID,
    private val vedtaksperiodeId: String,
    private val saksbehandler: String,
    private val saksbehandlerEpost: String,
    utbetalingGodkjent: Boolean,
    private val godkjenttidspunkt: LocalDateTime,
    automatiskBehandling: Boolean
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer), Behandlingsavgjørelse {
    override fun relevantVedtaksperiode(id: UUID) = id.toString() == this.vedtaksperiodeId
    override fun saksbehandler() = Saksbehandler(saksbehandler, saksbehandlerEpost)
    override val godkjent = utbetalingGodkjent
    override val avgjørelsestidspunkt = godkjenttidspunkt
    override val automatisert = automatiskBehandling
    override fun innsendt() = godkjenttidspunkt
    override fun avsender() = if (automatisert) Avsender.SYSTEM else Avsender.SAKSBEHANDLER

}
