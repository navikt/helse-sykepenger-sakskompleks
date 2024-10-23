package no.nav.helse.hendelser

import java.time.LocalDate
import no.nav.helse.hendelser.Periode.Companion.slutterEtter
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode

class Arbeidsavklaringspenger(val perioder: List<Periode>) {
    internal fun valider(aktivitetslogg: IAktivitetslogg, skjæringstidspunkt: LocalDate, periode: Periode): IAktivitetslogg {
        if (perioder.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen arbeidsavklaringspenger")
            return aktivitetslogg
        }
        val starterFørEllerOverlapper = perioder.filterNot { it.start > periode.endInclusive }
        if (starterFørEllerOverlapper.slutterEtter(skjæringstidspunkt.minusMonths(6))) {
            aktivitetslogg.varsel(Varselkode.RV_AY_3)
        } else {
            aktivitetslogg.info("Bruker har arbeidsavklaringspenger, men det slår ikke ut på overlappsjekken")
        }
        return aktivitetslogg
    }
}
