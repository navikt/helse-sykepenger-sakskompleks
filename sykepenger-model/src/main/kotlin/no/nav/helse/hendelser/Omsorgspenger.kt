package no.nav.helse.hendelser

import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

class Omsorgspenger(
    private val perioder: List<GradertPeriode>
) {
    internal fun overlapper(aktivitetslogg: IAktivitetslogg, sykdomsperiode: Periode, erForlengelse: Boolean): Boolean {
        if (perioder.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen omsorgspengeytelser")
            return false
        }
        val overlappsperiode = if (erForlengelse) sykdomsperiode else sykdomsperiode.familieYtelserPeriode
        return perioder.any { ytelse -> ytelse.periode.overlapperMed(overlappsperiode) }.also { overlapper ->
            if (!overlapper) aktivitetslogg.info("Bruker har omsorgspengeytelser, men det slår ikke ut på overlappssjekken")
        }
    }

    internal fun perioder(): List<Periode> {
        return perioder.map { it.periode }
    }
}
