package no.nav.helse.person

import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.BEREGNING
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_33
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_9
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerHistorikk : Vedtaksperiodetilstand {
    override val type = AVVENTER_HISTORIKK
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        checkNotNull(vedtaksperiode.vilkårsgrunnlag) { "Forventer vilkårsgrunnlag for å beregne utbetaling" }
        vedtaksperiode.trengerYtelser(aktivitetslogg)
        aktivitetslogg.info("Forespør sykdoms- og inntektshistorikk")
        val infotrygda = vedtaksperiode.vilkårsgrunnlag is VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag
        if (vedtaksperiode.arbeidsgiver.harIngenSporingTilInntektsmeldingISykefraværet() && !infotrygda) {
            aktivitetslogg.info("Inntektsmeldingen kunne ikke tolkes. Vi har ingen dokumentsporing til inntektsmeldingen i sykefraværet.")
        }
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = BEREGNING.utenBegrunnelse
    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknad(søknad, aktivitetslogg, AvventerBlokkerendePeriode)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        } ?: vedtaksperiode.trengerYtelser(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        infotrygdhistorikk.valider(
            aktivitetslogg,
            vedtaksperiode.periode,
            vedtaksperiode.skjæringstidspunkt,
            vedtaksperiode.arbeidsgiver.organisasjonsnummer
        )
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(hendelse, aktivitetslogg)
        if (vedtaksperiode.vilkårsgrunnlag != null) return
        aktivitetslogg.funksjonellFeil(RV_IT_33)
        vedtaksperiode.forkast(hendelse, aktivitetslogg)
    }

    override fun håndter(
        person: Person,
        arbeidsgiver: Arbeidsgiver,
        vedtaksperiode: Vedtaksperiode,
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        val aktivAktivitetslogg = håndterFørstegangsbehandling(aktivitetslogg, vedtaksperiode)
        vedtaksperiode.oppdaterHistorikk(ytelser, aktivAktivitetslogg, infotrygdhistorikk)
        if (aktivAktivitetslogg.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(
            ytelser,
            aktivAktivitetslogg
        )
        beregnUtbetalinger(vedtaksperiode, ytelser, aktivAktivitetslogg)
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
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(ytelser, aktivitetslogg)
        vedtaksperiode.høstingsresultater(aktivitetslogg, AvventerSimulering, AvventerGodkjenning)
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering, aktivitetslogg)
    }
}
