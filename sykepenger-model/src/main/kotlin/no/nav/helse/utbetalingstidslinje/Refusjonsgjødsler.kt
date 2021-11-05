package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.Refusjonshistorikk
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import org.slf4j.LoggerFactory

internal class Refusjonsgjødsler(
    private val tidslinje: Utbetalingstidslinje,
    private val refusjonshistorikk: Refusjonshistorikk,
    private val infotrygdhistorikk: Infotrygdhistorikk
) {
    internal fun gjødsle(aktivitetslogg: IAktivitetslogg, periode: Periode) {
        sammenhengendeUtbetalingsperioder(tidslinje).forEach { utbetalingsperiode ->
            val refusjon = refusjonshistorikk.finnRefusjon(utbetalingsperiode.periode(), aktivitetslogg)
            if (utbetalingsperiode.periode().overlapperMed(periode)) {
                if (refusjon == null) {
                    if (infotrygdhistorikk.harBrukerutbetalingerFor(utbetalingsperiode.periode())) {
                        aktivitetslogg.error("Finner ikke informasjon om refusjon i inntektsmelding og personen har brukerutbetaling")
                    } else {
                        aktivitetslogg.warn("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.")
                    }
                }

                if (refusjon?.erFørFørsteDagIArbeidsgiverperioden(utbetalingsperiode.periode().start) == true) {
                    aktivitetslogg.info("Refusjon gjelder ikke for hele utbetalingsperioden")
                    sikkerLogg.info("Refusjon gjelder ikke for hele utbetalingsperioden. Meldingsreferanse:${refusjon.meldingsreferanseId}, Utbetalingsperiode:${utbetalingsperiode.periode()}")
                }
            }

            utbetalingsperiode.forEach { utbetalingsdag ->
                when (refusjon) {
                    null -> utbetalingsdag.økonomi.settFullArbeidsgiverRefusjon()
                    else -> utbetalingsdag.økonomi.arbeidsgiverRefusjon(refusjon.beløp(utbetalingsdag.dato))
                }
            }
        }
    }

    private fun sammenhengendeUtbetalingsperioder(utbetalingstidslinje: Utbetalingstidslinje) = utbetalingstidslinje
        .filter { it !is Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag && it !is Utbetalingstidslinje.Utbetalingsdag.UkjentDag }
        .map(Utbetalingstidslinje.Utbetalingsdag::dato)
        .grupperSammenhengendePerioder()
        .map(utbetalingstidslinje::subset)
        .map(Utbetalingstidslinje::trimLedendeFridager)
        .filter(Utbetalingstidslinje::harUtbetalinger)

    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }
}
