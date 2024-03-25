package no.nav.helse.hendelser

import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

class Omsorgspenger(
    private val perioder: List<Periode>
) {
    internal fun overlapper(aktivitetslogg: IAktivitetslogg, sykdomsperiode: Periode, erForlengelse: Boolean): Boolean {
        if (perioder.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen omsorgspengeytelser")
            return false
        }
        val overlappsperiode = if (erForlengelse) sykdomsperiode else sykdomsperiode.familieYtelserPeriode
        return perioder.any { ytelse -> ytelse.overlapperMed(overlappsperiode) }.also { overlapper ->
            if (!overlapper) aktivitetslogg.info("Bruker har omsorgspengeytelser, men det slår ikke ut på overlappssjekken")
        }
    }
}
