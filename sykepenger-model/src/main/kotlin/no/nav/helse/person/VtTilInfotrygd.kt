package no.nav.helse.person

import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object TilInfotrygd : Vedtaksperiodetilstand {
    override val type = TIL_INFOTRYGD
    override val erFerdigBehandlet = true
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.info("Vedtaksperioden kan ikke behandles i Spleis.")
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = HJELP.utenBegrunnelse
    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        throw IllegalStateException("Kan ikke håndtere søknad mens perioden er i TilInfotrygd")
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        aktivitetslogg.info("Overstyrer ikke en vedtaksperiode som er sendt til Infotrygd")
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        throw IllegalStateException("Revurdering håndteres av en periode i til_infotrygd")
    }
}
