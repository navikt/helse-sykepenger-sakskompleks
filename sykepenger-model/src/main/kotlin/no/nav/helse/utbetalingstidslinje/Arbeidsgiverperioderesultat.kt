package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 8-17 ledd 1 bokstav a`
import no.nav.helse.etterlevelse.`§ 8-19 fjerde ledd`
import no.nav.helse.etterlevelse.`§ 8-19 første ledd`
import no.nav.helse.etterlevelse.`§ 8-19 tredje ledd`
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.nesteDag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje

data class Arbeidsgiverperioderesultat(
    // perioden som dekkes av arbeidsgiverperioden, fra første kjente dag til siste kjente dag
    val omsluttendePeriode: Periode,
    // dager som tolkes som del av arbeidsgiverperioden
    val arbeidsgiverperiode: List<Periode>,
    // perioder hvor det er registrert utbetaling
    val utbetalingsperioder: List<Periode>,
    // perioder hvor det er registrert oppholdsdager
    val oppholdsperioder: List<Periode>,
    // hvorvidt arbeidsgiverperiodetellingen er komplett, aka. 16 dager.
    // hvis tellingen er fullstendig så er arbeidsgiverperiode.last() dag nr. 16.
    val fullstendig: Boolean,
    // oppholdsdag nr 16. som gjør at arbeidsgiverperioden er ferdig, og ny telling påstartes
    val sisteDag: LocalDate?
) {

    fun utvideMed(
        dato: LocalDate,
        arbeidsgiverperiode: LocalDate? = null,
        utbetalingsperiode: LocalDate? = null,
        oppholdsperiode: LocalDate? = null,
        fullstendig: Boolean? = null,
        sisteDag: LocalDate? = null
    ): Arbeidsgiverperioderesultat {
        return this.copy(
            omsluttendePeriode = this.omsluttendePeriode.oppdaterTom(dato),
            arbeidsgiverperiode = this.arbeidsgiverperiode.leggTil(arbeidsgiverperiode),
            utbetalingsperioder = this.utbetalingsperioder.leggTil(utbetalingsperiode),
            oppholdsperioder = this.oppholdsperioder.leggTil(oppholdsperiode),
            fullstendig = fullstendig ?: this.fullstendig,
            sisteDag = sisteDag ?: this.sisteDag
        )
    }

    /**
     * subsummerer arbeidsgiverperioden, men bare dersom dagene overlapper med vedtaksperioden
     */
    internal fun subsummering(subsumsjonslogg: Subsumsjonslogg, sykdomstidslinje: Sykdomstidslinje) {
        if (arbeidsgiverperiode.isEmpty()) return // subsummerer ikke Infotrygd-ting

        val vedtaksperiode = sykdomstidslinje.periode() ?: return
        val sykdomstidslinjesubsumsjon = sykdomstidslinje.subsumsjonsformat()

        // første agp-dag i hver brudd-periode skal subsummeres med § 8-19 tredje ledd
        arbeidsgiverperiode.drop(1)
            .filter { it.start in vedtaksperiode }
            .map { it.start }
            .also {
                subsumsjonslogg.logg(`§ 8-19 tredje ledd`(it, sykdomstidslinjesubsumsjon))
            }

        // siste 16. agp-dag skal subsummeres med § 8-19 første ledd
        if (fullstendig && arbeidsgiverperiode.last().endInclusive in vedtaksperiode)
            subsumsjonslogg.logg(`§ 8-19 første ledd`(arbeidsgiverperiode.last().endInclusive, sykdomstidslinjesubsumsjon))

        // første utbetalingsdag skal subsummeres med § 8-17 ledd 1 bokstav a (oppfylt = true)
        utbetalingsperioder.firstOrNull()?.firstOrNull { !it.erHelg() }?.takeIf { it in vedtaksperiode }?.also {
            subsumsjonslogg.logg(`§ 8-17 ledd 1 bokstav a`(oppfylt = true, dagen = listOf(it.rangeTo(it)), sykdomstidslinjesubsumsjon))
        }

        // siste oppholdsdag som medfører at agp avbrytes subsummeres med § 8-19 fjerde ledd
        if (fullstendig && sisteDag != null && sisteDag in vedtaksperiode)
            subsumsjonslogg.logg(`§ 8-19 fjerde ledd`(sisteDag, sykdomstidslinjesubsumsjon))
    }

    internal fun somArbeidsgiverperiode(): Arbeidsgiverperiode {
        val agp =
            if (arbeidsgiverperiode.isEmpty() && utbetalingsperioder.isNotEmpty())
                Arbeidsgiverperiode.fiktiv(utbetalingsperioder.first().start)
            else
                Arbeidsgiverperiode(arbeidsgiverperiode)
        utbetalingsperioder.flatten().forEach { agp.utbetalingsdag(it) }
        oppholdsperioder.flatten().forEach { agp.oppholdsdag(it) }
        omsluttendePeriode.forEach { agp.kjentDag(it) }
        return agp
    }

    companion object {
        // utvider liste av perioder med ny dato. antar at listen er sortert i stigende rekkefølge,
        // og at <dato> må være nyere enn forrige periode. strekker altså -ikke- periodene eventuelt tilbake i tid, kun frem
        private fun List<Periode>.leggTil(dato: LocalDate?): List<Periode> = when {
            dato == null -> this
            // tom liste
            isEmpty() -> listOf(dato.somPeriode())
            // dagen er dekket av en tidligere periode
            dato <= last().endInclusive -> this
            // dagen utvider ikke siste datoperiode
            dato > last().endInclusive.nesteDag -> this + listOf(dato.somPeriode())
            // dagen utvider siste periode
            else -> dropLast(1) + listOf(last().oppdaterTom(dato))
        }

        internal fun Iterable<Arbeidsgiverperioderesultat>.finn(periode: Periode) = lastOrNull { arbeidsgiverperiode ->
            periode.overlapperMed(arbeidsgiverperiode.omsluttendePeriode)
        }
    }
}
