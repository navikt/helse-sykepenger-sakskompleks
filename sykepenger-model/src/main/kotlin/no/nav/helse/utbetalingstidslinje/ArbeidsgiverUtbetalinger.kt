package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.etterlevelse.SubsumsjonObserver

internal class ArbeidsgiverUtbetalinger(
    regler: ArbeidsgiverRegler,
    alder: Alder,
    private val arbeidsgivere: Map<Arbeidsgiver, (Periode) -> Utbetalingstidslinje>,
    infotrygdUtbetalingstidslinje: Utbetalingstidslinje,
    dødsdato: LocalDate?,
    vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
) {
    private val maksimumSykepengedagerfilter = MaksimumSykepengedagerfilter(alder, regler, infotrygdUtbetalingstidslinje)
    private val filtere = listOf(
        Sykdomsgradfilter,
        AvvisDagerEtterDødsdatofilter(dødsdato),
        AvvisInngangsvilkårfilter(vilkårsgrunnlagHistorikk),
        maksimumSykepengedagerfilter,
        MaksimumUtbetalingFilter(),
    )
    internal val maksimumSykepenger by lazy { maksimumSykepengedagerfilter.maksimumSykepenger() }

    internal fun beregn(
        beregningsperiode: Periode,
        perioder: List<Triple<Periode, IAktivitetslogg, SubsumsjonObserver>>
    ): Pair<Alder.MaksimumSykepenger, Map<Arbeidsgiver, Utbetalingstidslinje>> {
        val tidslinjerPerArbeidsgiver = filtere.fold(tidslinjer(beregningsperiode)) { tidslinjer, filter ->
            val input = tidslinjer.entries.map { (key, value) -> key to value }
            val result = filter.filter(input.map { (_, tidslinje) -> tidslinje }, perioder)
            input.zip(result) { (arbeidsgiver, _), utbetalingstidslinje ->
                arbeidsgiver to utbetalingstidslinje
            }.toMap()
        }
        return maksimumSykepenger to tidslinjerPerArbeidsgiver
    }

    private fun tidslinjer(periode: Periode) = arbeidsgivere
        .mapValues { (_, builder) -> builder(periode) }

}
