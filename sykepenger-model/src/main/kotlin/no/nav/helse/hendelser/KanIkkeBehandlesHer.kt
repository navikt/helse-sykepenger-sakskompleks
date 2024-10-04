package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Avsender.SYSTEM
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg

class KanIkkeBehandlesHer(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    organisasjonsnummer: String,
    private val vedtaksperiodeId: UUID,
    override val utbetalingId: UUID,
    private val saksbehandlerIdent: String,
    private val saksbehandlerEpost: String,
    private val opprettet: LocalDateTime,
    override val automatisert: Boolean
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, Aktivitetslogg()),
    Behandlingsavgjørelse {
    override val avgjørelsestidspunkt = opprettet
    override val godkjent = false
    override fun saksbehandler() = Saksbehandler(saksbehandlerIdent, saksbehandlerEpost)
    override fun relevantVedtaksperiode(id: UUID) = vedtaksperiodeId == id
    override fun innsendt() = opprettet
    override fun avsender() = if (automatisert) SYSTEM else SAKSBEHANDLER
}
