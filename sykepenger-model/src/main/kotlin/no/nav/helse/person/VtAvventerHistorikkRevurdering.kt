package no.nav.helse.person

import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Hva.BEREGNING
import no.nav.helse.person.Venteårsak.Hvorfor.OVERSTYRING_IGANGSATT
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_9
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerHistorikkRevurdering : Vedtaksperiodetilstand {
    override val type = AVVENTER_HISTORIKK_REVURDERING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        checkNotNull(vedtaksperiode.vilkårsgrunnlag) { "Forventer vilkårsgrunnlag for å beregne revurdering" }
        aktivitetslogg.info("Forespør sykdoms- og inntektshistorikk")
        vedtaksperiode.trengerYtelser(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) =
        BEREGNING fordi OVERSTYRING_IGANGSATT

    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering, aktivitetslogg)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        } ?: vedtaksperiode.trengerYtelser(aktivitetslogg)
    }

    override fun skalHåndtereDager(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) =
        vedtaksperiode.skalHåndtereDagerRevurdering(dager, aktivitetslogg)

    override fun håndter(
        person: Person,
        arbeidsgiver: Arbeidsgiver,
        vedtaksperiode: Vedtaksperiode,
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        val wrapper = aktivitetsloggForRevurdering(aktivitetslogg)
        vedtaksperiode.oppdaterHistorikk(ytelser, wrapper, infotrygdhistorikk)
        beregnUtbetalinger(vedtaksperiode, ytelser, wrapper)
    }

    override fun beregnUtbetalinger(
        vedtaksperiode: Vedtaksperiode,
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg
    ) {
        val maksdatoresultat = vedtaksperiode.beregnUtbetalinger(aktivitetslogg)
        if (vedtaksperiode.harTilkomneInntekter() && !ytelser.andreYtelserPerioder().erTom()) {
            aktivitetslogg.varsel(RV_IV_9)
        }
        ytelser.valider(
            aktivitetslogg,
            vedtaksperiode.periode,
            vedtaksperiode.skjæringstidspunkt,
            maksdatoresultat.maksdato,
            vedtaksperiode.erForlengelse()
        )
        vedtaksperiode.høstingsresultater(
            aktivitetslogg,
            AvventerSimuleringRevurdering,
            AvventerGodkjenningRevurdering
        )
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }
}
