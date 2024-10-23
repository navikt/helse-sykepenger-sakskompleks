package no.nav.helse.hendelser

import java.time.LocalDate
import no.nav.helse.hendelser.Periode.Companion.slutterEtter
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_4

class Dagpenger(val perioder: List<Periode>) {
    internal fun valider(aktivitetslogg: IAktivitetslogg, skjæringstidspunkt: LocalDate, periode: Periode): IAktivitetslogg {
        if (perioder.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen dagpenger")
            return aktivitetslogg
        }
        val starterFørEllerOverlapper = perioder.filterNot { it.start > periode.endInclusive }
        if (starterFørEllerOverlapper.slutterEtter(skjæringstidspunkt.minusWeeks(4))) {
            aktivitetslogg.varsel(RV_AY_4)
        } else {
            aktivitetslogg.info("Bruker har dagpenger, men det slår ikke ut på overlappsjekken")
        }
        return aktivitetslogg
    }
}
