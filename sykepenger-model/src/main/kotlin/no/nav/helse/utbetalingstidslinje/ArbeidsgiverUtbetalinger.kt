package no.nav.helse.utbetalingstidslinje

import no.nav.helse.Alder
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.etterlevelse.SubsumsjonObserver

internal class ArbeidsgiverUtbetalinger(
    regler: ArbeidsgiverRegler,
    alder: Alder,
    private val arbeidsgivere: (Periode, SubsumsjonObserver, IAktivitetslogg) -> Map<Arbeidsgiver, Utbetalingstidslinje>,
    infotrygdUtbetalingstidslinje: Utbetalingstidslinje,
    vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
) {
    private val maksimumSykepengedagerfilter = MaksimumSykepengedagerfilter(alder, regler, infotrygdUtbetalingstidslinje)
    private val filtere = listOf(
        Sykdomsgradfilter,
        AvvisDagerEtterDødsdatofilter(alder),
        AvvisInngangsvilkårfilter(vilkårsgrunnlagHistorikk),
        maksimumSykepengedagerfilter,
        MaksimumUtbetalingFilter(),
    )

    internal fun beregn(
        beregningsperiode: Periode,
        vedtaksperiode: Periode,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonObserver: SubsumsjonObserver
    ): Pair<Alder.MaksimumSykepenger, Map<Arbeidsgiver, Utbetalingstidslinje>> {
        val arbeidsgivertidslinjer = arbeidsgivere(beregningsperiode, subsumsjonObserver, aktivitetslogg)
        val tidslinjerPerArbeidsgiver = filtere.fold(arbeidsgivertidslinjer) { tidslinjer, filter ->
            val input = tidslinjer.entries.map { (key, value) -> key to value }
            val result = filter.filter(input.map { (_, tidslinje) -> tidslinje }, vedtaksperiode, aktivitetslogg, subsumsjonObserver)
            input.zip(result) { (arbeidsgiver, _), utbetalingstidslinje ->
                arbeidsgiver to utbetalingstidslinje
            }.toMap()
        }
        return maksimumSykepengedagerfilter.maksimumSykepenger(vedtaksperiode, subsumsjonObserver) to tidslinjerPerArbeidsgiver
    }
}
