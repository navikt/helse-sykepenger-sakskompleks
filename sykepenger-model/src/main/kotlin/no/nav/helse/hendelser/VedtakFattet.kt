package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Avsender.SYSTEM
import no.nav.helse.hendelser.utbetaling.Saksbehandler
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg

class VedtakFattet(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    organisasjonsnummer: String,
    private val vedtaksperiodeId: UUID,
    private val utbetalingId: UUID,
    private val saksbehandlerIdent: String,
    private val saksbehandlerEpost: String,
    private val vedtakFattetTidspunkt: LocalDateTime,
    override val automatisert: Boolean,
    override val godkjent: Boolean
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, Aktivitetslogg()), Utbetalingsavgjørelse {
    override val avgjørelsestidspunkt = vedtakFattetTidspunkt
    override fun saksbehandler() = Saksbehandler(saksbehandlerIdent, saksbehandlerEpost)
    override fun relevantVedtaksperiode(id: UUID) = vedtaksperiodeId == id
    override fun relevantUtbetaling(id: UUID) = utbetalingId == id
    override fun innsendt() = vedtakFattetTidspunkt
    override fun avsender() = if (automatisert) SYSTEM else SAKSBEHANDLER
}
