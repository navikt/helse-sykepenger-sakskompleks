package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.Refusjonshistorikk

internal class Refusjonsgjødsler(
    private val tidslinje: Utbetalingstidslinje,
    private val refusjonshistorikk: Refusjonshistorikk
) {
    internal fun gjødsle(aktivitetslogg: IAktivitetslogg) {
        sammenhengendeUtbetalingsperioder(tidslinje).forEach { utbetalingsperiode ->
            val refusjon = refusjonshistorikk.finnRefusjon(utbetalingsperiode.periode())
            if (refusjon == null) aktivitetslogg.warn("Fant ikke refusjon for perioden. Defaulter til 100%% refusjon. placeholder") // TODO: Spør voksne om tekst

            utbetalingsperiode.forEach { utbetalingsdag ->
                when (refusjon) {
                    null -> utbetalingsdag.økonomi.settFullArbeidsgiverRefusjon()
                    else -> utbetalingsdag.økonomi.arbeidsgiverRefusjon(refusjon.beløp(utbetalingsdag.dato, aktivitetslogg))
                }
            }
        }
    }

    private fun sammenhengendeUtbetalingsperioder(utbetalingstidslinje: Utbetalingstidslinje) = utbetalingstidslinje
        .filter { it !is Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag && it !is Utbetalingstidslinje.Utbetalingsdag.UkjentDag }
        .map(Utbetalingstidslinje.Utbetalingsdag::dato)
        .grupperSammenhengendePerioder()
        .map(utbetalingstidslinje::subset)
        .filter(Utbetalingstidslinje::harUtbetalinger)
}
