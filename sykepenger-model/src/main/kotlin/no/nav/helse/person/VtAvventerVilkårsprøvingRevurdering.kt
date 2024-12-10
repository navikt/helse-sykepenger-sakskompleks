package no.nav.helse.person

import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerVilkårsprøvingRevurdering : Vedtaksperiode.Vedtaksperiodetilstand {
    override val type = TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.trengerVilkårsgrunnlag(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = null
    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering, aktivitetslogg)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.trengerVilkårsgrunnlag(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        vilkårsgrunnlag: Vilkårsgrunnlag,
        aktivitetslogg: IAktivitetslogg
    ) {
        val wrapper = aktivitetsloggForRevurdering(aktivitetslogg)
        vedtaksperiode.håndterVilkårsgrunnlag(vilkårsgrunnlag, wrapper, AvventerHistorikkRevurdering)
    }

    override fun skalHåndtereDager(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) =
        vedtaksperiode.skalHåndtereDagerRevurdering(dager, aktivitetslogg)

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad, aktivitetslogg)
    }
}
