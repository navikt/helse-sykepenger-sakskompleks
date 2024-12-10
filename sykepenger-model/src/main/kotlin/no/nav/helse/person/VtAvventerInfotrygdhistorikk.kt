package no.nav.helse.person

import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Validation.Companion.validation
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.Vedtaksperiode.ArbeidsgiveropplysningerStrategi
import no.nav.helse.person.Vedtaksperiode.FørInntektsmelding
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerInfotrygdHistorikk : Vedtaksperiodetilstand {
    override val type = AVVENTER_INFOTRYGDHISTORIKK
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.person.trengerHistorikkFraInfotrygd(aktivitetslogg)
    }

    override val arbeidsgiveropplysningerStrategi get(): ArbeidsgiveropplysningerStrategi = FørInntektsmelding
    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = null
    override fun gjenopptaBehandling(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.person.trengerHistorikkFraInfotrygd(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        validation(aktivitetslogg) {
            onValidationFailed { vedtaksperiode.forkast(hendelse, aktivitetslogg) }
            valider {
                infotrygdhistorikk.valider(
                    this,
                    vedtaksperiode.periode,
                    vedtaksperiode.skjæringstidspunkt,
                    vedtaksperiode.arbeidsgiver.organisasjonsnummer
                )
            }
            onSuccess {
                endreTilstand(vedtaksperiode, aktivitetslogg, AvventerInntektsmelding)
            }
        }
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.person.trengerHistorikkFraInfotrygd(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknad(søknad, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
    }
}
