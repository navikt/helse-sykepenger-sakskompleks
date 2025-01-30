package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.BehandlingView
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal val BehandlingView.inspektør get() = BehandlingInspektør(this)

internal class BehandlingInspektør(view: BehandlingView) {

    internal val behandling: Behandling = Behandling(
        id = view.id,
        endringer = view.endringer.map {
            Behandling.Behandlingendring(
                grunnlagsdata = it.grunnlagsdata,
                utbetaling = it.utbetaling,
                periode = it.sykdomstidslinje.periode()!!,
                dokumentsporing = it.dokumentsporing,
                utbetalingstidslinje = it.utbetalingstidslinje,
                refusjonstidslinje = it.refusjonstidslinje,
                skjæringstidspunkt = it.skjæringstidspunkt,
                sykdomstidslinje = it.sykdomstidslinje,
                arbeidsgiverperiode = it.arbeidsgiverperiode.arbeidsgiverperioder.map { it.periode },
                sykmeldingsperiode = it.sykmeldingsperiode,
            )
        },
        periode = view.periode,
        tilstand = view.tilstand,
        vedtakFattet = view.vedtakFattet,
        avsluttet = view.avsluttet,
        kilde = Behandling.Behandlingkilde(view.kilde.meldingsreferanseId, view.kilde.innsendt, view.kilde.registert, view.kilde.avsender)
    )

    internal val arbeidsgiverperiode get() = behandling.endringer.last().arbeidsgiverperiode
    internal val utbetalingstidslinje get() = behandling.endringer.last().utbetalingstidslinje

    data class Behandling(
        val id: UUID,
        val endringer: List<Behandlingendring>,
        val periode: Periode,
        val tilstand: BehandlingView.TilstandView,
        val vedtakFattet: LocalDateTime?,
        val avsluttet: LocalDateTime?,
        val kilde: Behandlingkilde
    ) {
        val skjæringstidspunkt get() = endringer.last().skjæringstidspunkt

        data class Behandlingendring(
            val grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
            val utbetaling: Utbetaling?,
            val periode: Periode,
            val dokumentsporing: Dokumentsporing,
            val utbetalingstidslinje: Utbetalingstidslinje,
            val refusjonstidslinje: Beløpstidslinje,
            val skjæringstidspunkt: LocalDate,
            val sykdomstidslinje: Sykdomstidslinje,
            val arbeidsgiverperiode: List<Periode>,
            val sykmeldingsperiode: Periode
        )

        data class Behandlingkilde(
            val meldingsreferanseId: UUID,
            val innsendt: LocalDateTime,
            val registert: LocalDateTime,
            val avsender: Avsender
        )
    }
}
