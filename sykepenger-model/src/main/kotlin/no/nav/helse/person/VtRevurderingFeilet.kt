package no.nav.helse.person

import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RV_2
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object RevurderingFeilet : Vedtaksperiodetilstand {
    override val type: TilstandType = REVURDERING_FEILET
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak? {
        if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, Aktivitetslogg())) return null
        return HJELP.utenBegrunnelse
    }

    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        throw IllegalStateException("Kan ikke håndtere søknad mens perioden er i RevurderingFeilet")
    }

    override fun gjenopptaBehandling(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (!vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, Aktivitetslogg())) return aktivitetslogg.info(
            "Gjenopptar ikke revurdering feilet fordi perioden har tidligere avsluttede utbetalinger. Må behandles manuelt vha annullering."
        )
        aktivitetslogg.funksjonellFeil(RV_RV_2)
        vedtaksperiode.forkast(hendelse, aktivitetslogg)
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
    }
}
