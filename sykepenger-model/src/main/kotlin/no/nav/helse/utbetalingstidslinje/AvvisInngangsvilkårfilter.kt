package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.etterlevelse.SubsumsjonObserver

internal class AvvisInngangsvilkårfilter(
    private val vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
): UtbetalingstidslinjerFilter {

    override fun filter(
        tidslinjer: List<Utbetalingstidslinje>,
        periode: Periode,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonObserver: SubsumsjonObserver
    ): List<Utbetalingstidslinje> {
        return vilkårsgrunnlagHistorikk.avvisInngangsvilkår(tidslinjer, subsumsjonObserver)
    }
}