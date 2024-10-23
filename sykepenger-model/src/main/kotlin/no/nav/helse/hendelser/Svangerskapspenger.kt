package no.nav.helse.hendelser

import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

class Svangerskapspenger(
    private val svangerskapsytelse: List<GradertPeriode>
) {

    internal fun overlapper(aktivitetslogg: IAktivitetslogg, sykdomsperiode: Periode, erForlengelse: Boolean): Boolean {
        if (svangerskapsytelse.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen svangerskapsytelser")
            return false
        }
        val overlappsperiode = if (erForlengelse) sykdomsperiode else sykdomsperiode.familieYtelserPeriode
        return svangerskapsytelse.any { ytelse -> ytelse.periode.overlapperMed(overlappsperiode) }.also { overlapper ->
            if (!overlapper) aktivitetslogg.info("Bruker har svangerskapsytelser, men det slår ikke ut på overlappssjekken")
        }
    }

    internal fun perioder(): List<Periode> {
        return svangerskapsytelse.map { it.periode }
    }
}
