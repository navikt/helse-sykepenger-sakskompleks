package no.nav.helse.person

import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.Vedtaksperiode.ArbeidsgiveropplysningerStrategi
import no.nav.helse.person.Vedtaksperiode.Companion.NYERE_SKJÆRINGSTIDSPUNKT_MED_UTBETALING
import no.nav.helse.person.Vedtaksperiode.Companion.NYERE_SKJÆRINGSTIDSPUNKT_UTEN_UTBETALING
import no.nav.helse.person.Vedtaksperiode.FørInntektsmelding
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OO_1
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object Start : Vedtaksperiodetilstand {
    override val type = START
    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = HJELP.utenBegrunnelse
    override val arbeidsgiveropplysningerStrategi get(): ArbeidsgiveropplysningerStrategi = FørInntektsmelding
    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        val harSenereUtbetalinger =
            vedtaksperiode.person.vedtaksperioder(NYERE_SKJÆRINGSTIDSPUNKT_MED_UTBETALING(vedtaksperiode))
                .isNotEmpty()
        val harSenereAUU =
            vedtaksperiode.person.vedtaksperioder(NYERE_SKJÆRINGSTIDSPUNKT_UTEN_UTBETALING(vedtaksperiode))
                .isNotEmpty()
        if (harSenereUtbetalinger || harSenereAUU) {
            aktivitetslogg.varsel(RV_OO_1)
        }
        vedtaksperiode.arbeidsgiver.vurderOmSøknadIkkeKanHåndteres(aktivitetslogg, vedtaksperiode, arbeidsgivere)
        infotrygdhistorikk.valider(
            aktivitetslogg,
            vedtaksperiode.periode,
            vedtaksperiode.skjæringstidspunkt,
            vedtaksperiode.arbeidsgiver.organisasjonsnummer
        )
        vedtaksperiode.håndterSøknad(søknad, aktivitetslogg)
        vedtaksperiode.videreførEksisterendeRefusjonsopplysninger(søknad, aktivitetslogg)
        aktivitetslogg.info("Fullført behandling av søknad")
        vedtaksperiode.person.igangsettOverstyring(
            Revurderingseventyr.nyPeriode(
                søknad,
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.periode
            ), aktivitetslogg
        )
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return
        endreTilstand(
            vedtaksperiode = vedtaksperiode,
            event = aktivitetslogg,
            nyTilstand = when {
                !infotrygdhistorikk.harHistorikk() -> AvventerInfotrygdHistorikk
                vedtaksperiode.periodeRettFørHarFåttInntektsmelding() -> AvventerBlokkerendePeriode
                periodeRettEtterHarFåttInntektsmelding(vedtaksperiode, aktivitetslogg) -> AvventerBlokkerendePeriode
                else -> AvventerInntektsmelding
            }
        )
    }

    private fun periodeRettEtterHarFåttInntektsmelding(
        vedtaksperiode: Vedtaksperiode,
        aktivitetslogg: IAktivitetslogg
    ): Boolean {
        val rettEtter = vedtaksperiode.arbeidsgiver.finnVedtaksperiodeRettEtter(vedtaksperiode) ?: return false
        // antagelse at om vi har en periode rett etter oss, og vi har tilstrekkelig informasjon til utbetaling, så har vi endt
        // opp med å gjenbruke tidsnære opplysninger og trenger derfor ikke egen IM
        return !rettEtter.måInnhenteInntektEllerRefusjon(aktivitetslogg)
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
    }
}
