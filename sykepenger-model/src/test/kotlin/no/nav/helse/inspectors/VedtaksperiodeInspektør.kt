package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.inspectors.BehandlingInspektør.Behandling
import no.nav.helse.person.Behandlinger
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VedtaksperiodeVisitor
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Maksdatoresultat
import no.nav.helse.utbetalingstidslinje.Maksdatoresultat.Bestemmelse
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

    internal val utbetalingstidslinje: Utbetalingstidslinje get() = behandlinger.last().endringer.last().utbetalingstidslinje
    internal val behandlinger = mutableListOf<Behandling>()
    internal var egenmeldingsperioder = listOf<Periode>()

    internal val arbeidsgiverperiode get() = behandlinger.last().endringer.last().arbeidsgiverperiode

    internal val sykdomstidslinje get() = behandlinger.last().endringer.last().sykdomstidslinje

    internal val maksdatoer = mutableListOf<Maksdatoresultat>()

    internal val utbetalinger = mutableListOf<Utbetaling>()

    internal lateinit var hendelser: Set<Dokumentsporing>
        private set

    internal val hendelseIder get() = hendelser.map { it.id }.toSet()

    init {
        vedtaksperiode.accept(this)
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
        this.hendelser = hendelseIder
    }

    override fun preVisitBehandlinger(behandlinger: List<Behandlinger.Behandling>) {
        this.behandlinger.addAll(behandlinger.map { it.inspektør.behandling })
    }

    override fun visitBehandlingendring(
        id: UUID,
        tidsstempel: LocalDateTime,
        sykmeldingsperiode: Periode,
        periode: Periode,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
        utbetaling: Utbetaling?,
        dokumentsporing: Dokumentsporing,
        sykdomstidslinje: Sykdomstidslinje,
        skjæringstidspunkt: LocalDate,
        arbeidsgiverperiode: List<Periode>,
        utbetalingstidslinje: Utbetalingstidslinje,
        maksdatoresultat: Maksdatoresultat
    ) {
        if (utbetaling != null) utbetalinger.add(utbetaling)

        if (maksdatoresultat.bestemmelse == Bestemmelse.IKKE_VURDERT) return
        maksdatoer.add(maksdatoresultat)
    }
}
