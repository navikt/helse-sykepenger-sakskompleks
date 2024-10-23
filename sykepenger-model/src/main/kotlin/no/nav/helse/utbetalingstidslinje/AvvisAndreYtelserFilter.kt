package no.nav.helse.utbetalingstidslinje

import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

internal class AvvisAndreYtelserFilter(
    private val andreYtelser: AndreYtelserPerioder
): UtbetalingstidslinjerFilter {

    override fun filter(
        tidslinjer: List<Utbetalingstidslinje>,
        periode: Periode,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonslogg: Subsumsjonslogg
    ): List<Utbetalingstidslinje> {
        return tidslinjer
            .avvis(andreYtelser.foreldrepenger, listOf(Begrunnelse.AndreYtelserForeldrepenger))
            .avvis(andreYtelser.svangerskapspenger, listOf(Begrunnelse.AndreYtelserSvangerskapspenger))
            .avvis(andreYtelser.pleiepenger, listOf(Begrunnelse.AndreYtelserPleiepenger))
            .avvis(andreYtelser.dagpenger, listOf(Begrunnelse.AndreYtelserDagpenger))
            .avvis(andreYtelser.arbeidsavklaringspenger, listOf(Begrunnelse.AndreYtelserAap))
            .avvis(andreYtelser.opplæringspenger, listOf(Begrunnelse.AndreYtelserOpplaringspenger))
            .avvis(andreYtelser.omsorgspenger, listOf(Begrunnelse.AndreYtelserOmsorgspenger))
    }
}

data class AndreYtelserPerioder(
    val foreldrepenger: List<Periode>,
    val svangerskapspenger: List<Periode>,
    val pleiepenger: List<Periode>,
    val dagpenger: List<Periode>,
    val arbeidsavklaringspenger: List<Periode>,
    val opplæringspenger: List<Periode>,
    val omsorgspenger: List<Periode>
)