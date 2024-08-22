package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Periode
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand
import no.nav.helse.person.Behandlinger
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VedtaksperiodeVisitor
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal val Vedtaksperiode.inspektør get() = VedtaksperiodeInspektør(this)

internal class VedtaksperiodeInspektør(vedtaksperiode: Vedtaksperiode) : VedtaksperiodeVisitor {

    internal lateinit var id: UUID
        private set

    internal lateinit var periode: Periode
        private set

    internal lateinit var tilstand: Vedtaksperiode.Vedtaksperiodetilstand
        private set

    internal lateinit var oppdatert: LocalDateTime
        private set
    internal lateinit var skjæringstidspunkt: LocalDate
        private set

    internal lateinit var utbetalingIdTilVilkårsgrunnlagId: Pair<UUID, UUID>
        private set
    internal lateinit var utbetalingstidslinje: Utbetalingstidslinje
        private set
    internal val behandlinger = mutableListOf<Behandling>()
    internal var egenmeldingsperioder = listOf<Periode>()
    init {
        vedtaksperiode.accept(this)
    }

    data class Behandling(
        val id: UUID,
        val endringer: List<Behandlingendring>,
        val periode: Periode,
        val tilstand: Behandlingtilstand,
        val vedtakFattet: LocalDateTime?,
        val avsluttet: LocalDateTime?,
        val kilde: Behandlingkilde
    ) {
        val skjæringstidspunkt get() = endringer.last().skjæringstidspunkt

        internal enum class Behandlingtilstand {
            UBEREGNET, UBEREGNET_OMGJØRING, UBEREGNET_REVURDERING, BEREGNET, BEREGNET_OMGJØRING, BEREGNET_REVURDERING,
            VEDTAK_FATTET, REVURDERT_VEDTAK_AVVIST, VEDTAK_IVERKSATT, AVSLUTTET_UTEN_VEDTAK, ANNULLERT_PERIODE, TIL_INFOTRYGD
        }
        data class Behandlingendring(
            val grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
            val utbetaling: Utbetaling?,
            val periode: Periode,
            val dokumentsporing: Dokumentsporing,
            val skjæringstidspunkt: LocalDate,
            val sykdomstidslinje: Sykdomstidslinje,
            val arbeidsgiverperiode: List<Periode>
        )

        data class Behandlingkilde(
           val meldingsreferanseId: UUID,
           val innsendt: LocalDateTime,
           val registert: LocalDateTime,
           val avsender: Avsender
        )
    }

    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: LocalDate,
        hendelseIder: Set<Dokumentsporing>,
        egenmeldingsperioder: List<Periode>
    ) {
        this.id = id
        this.periode = periode
        this.oppdatert = oppdatert
        this.skjæringstidspunkt = skjæringstidspunkt
        this.tilstand = tilstand
        this.egenmeldingsperioder = egenmeldingsperioder
    }

    override fun preVisitBehandling(
        id: UUID,
        tidsstempel: LocalDateTime,
        tilstand: Behandlinger.Behandling.Tilstand,
        periode: Periode,
        vedtakFattet: LocalDateTime?,
        avsluttet: LocalDateTime?,
        kilde: Behandlinger.Behandlingkilde
    ) {
        this.behandlinger.add(Behandling(
            id = id,
            endringer = emptyList(),
            periode = periode,
            tilstand = when (tilstand) {
                is Behandlinger.Behandling.Tilstand.Uberegnet -> Behandlingtilstand.UBEREGNET
                is Behandlinger.Behandling.Tilstand.UberegnetOmgjøring -> Behandlingtilstand.UBEREGNET_OMGJØRING
                is Behandlinger.Behandling.Tilstand.UberegnetRevurdering -> Behandlingtilstand.UBEREGNET_REVURDERING
                is Behandlinger.Behandling.Tilstand.Beregnet -> Behandlingtilstand.BEREGNET
                is Behandlinger.Behandling.Tilstand.BeregnetOmgjøring -> Behandlingtilstand.BEREGNET_OMGJØRING
                is Behandlinger.Behandling.Tilstand.BeregnetRevurdering -> Behandlingtilstand.BEREGNET_REVURDERING
                is Behandlinger.Behandling.Tilstand.VedtakFattet -> Behandlingtilstand.VEDTAK_FATTET
                is Behandlinger.Behandling.Tilstand.VedtakIverksatt -> Behandlingtilstand.VEDTAK_IVERKSATT
                is Behandlinger.Behandling.Tilstand.RevurdertVedtakAvvist -> Behandlingtilstand.REVURDERT_VEDTAK_AVVIST
                is Behandlinger.Behandling.Tilstand.AvsluttetUtenVedtak -> Behandlingtilstand.AVSLUTTET_UTEN_VEDTAK
                is Behandlinger.Behandling.Tilstand.TilInfotrygd -> Behandlingtilstand.TIL_INFOTRYGD
                is Behandlinger.Behandling.Tilstand.AnnullertPeriode -> Behandlingtilstand.ANNULLERT_PERIODE
            },
            vedtakFattet = vedtakFattet,
            avsluttet = avsluttet,
            kilde = Behandling.Behandlingkilde(
                meldingsreferanseId = kilde.meldingsreferanseId,
                innsendt = kilde.innsendt,
                registert = kilde.registert,
                avsender = kilde.avsender
            )
        ))
    }

    override fun preVisitBehandlingendring(
        id: UUID,
        tidsstempel: LocalDateTime,
        sykmeldingsperiode: Periode,
        periode: Periode,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
        utbetaling: Utbetaling?,
        dokumentsporing: Dokumentsporing,
        sykdomstidslinje: Sykdomstidslinje,
        skjæringstidspunkt: LocalDate,
        arbeidsgiverperiode: List<Periode>
    ) {
        val sisteBehandling = this.behandlinger.last()
        this.behandlinger[this.behandlinger.lastIndex] = sisteBehandling.copy(
            endringer = sisteBehandling.endringer.plus(Behandling.Behandlingendring(grunnlagsdata, utbetaling, sykdomstidslinje.periode()!!, dokumentsporing, skjæringstidspunkt, sykdomstidslinje, arbeidsgiverperiode))
        )
        val vilkårsgrunnlagId = grunnlagsdata?.inspektør?.vilkårsgrunnlagId ?: return
        val utbetalingId = utbetaling!!.inspektør.utbetalingId
        utbetalingIdTilVilkårsgrunnlagId = utbetalingId to vilkårsgrunnlagId
    }

    override fun visitBehandlingkilde(
        meldingsreferanseId: UUID,
        innsendt: LocalDateTime,
        registrert: LocalDateTime,
        avsender: Avsender
    ) {
        val sisteBehandling = this.behandlinger.last()
        this.behandlinger[this.behandlinger.lastIndex] = sisteBehandling.copy(
            kilde = Behandling.Behandlingkilde(meldingsreferanseId, innsendt, registrert, avsender)
        )
    }

    override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {
        this.utbetalingstidslinje = tidslinje
    }
}
