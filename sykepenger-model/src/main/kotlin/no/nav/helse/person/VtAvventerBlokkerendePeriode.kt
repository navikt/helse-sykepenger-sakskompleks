package no.nav.helse.person

import java.time.LocalDateTime
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverFaktaavklartInntekt
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal data object AvventerBlokkerendePeriode : Vedtaksperiode.Vedtaksperiodetilstand {
    override val type: TilstandType = TilstandType.AVVENTER_BLOKKERENDE_PERIODE
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        check(!vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) {
            "Periode i avventer blokkerende har ikke tilstrekkelig informasjon til utbetaling! VedtaksperiodeId = ${vedtaksperiode.id}"
        }
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun lagUtbetalingstidslinje(
        vedtaksperiode: Vedtaksperiode,
        inntekt: ArbeidsgiverFaktaavklartInntekt?
    ): Utbetalingstidslinje {
        val benyttetInntekt =
            inntekt ?: vedtaksperiode.defaultinntektForAUU().takeUnless { vedtaksperiode.skalFatteVedtak() }
            ?: error(
                "Det er en vedtaksperiode som ikke inngår i SP: ${vedtaksperiode.arbeidsgiver.organisasjonsnummer} - $vedtaksperiode.id - $vedtaksperiode.periode." +
                    "Burde ikke arbeidsgiveren være kjent i sykepengegrunnlaget, enten i form av en skatteinntekt eller en tilkommet?"
            )
        return vedtaksperiode.behandlinger.lagUtbetalingstidslinje(
            benyttetInntekt,
            vedtaksperiode.jurist,
            vedtaksperiode.refusjonstidslinje
        )
    }

    override fun makstid(vedtaksperiode: Vedtaksperiode, tilstandsendringstidspunkt: LocalDateTime): LocalDateTime =
        when {
            vedtaksperiode.person.avventerSøknad(vedtaksperiode.periode) -> tilstandsendringstidspunkt.plusDays(90)
            else -> LocalDateTime.MAX
        }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak? {
        return tilstand(Aktivitetslogg(), vedtaksperiode).venteårsak()
    }

    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode): VedtaksperiodeVenter? {
        val venterPå = tilstand(Aktivitetslogg(), vedtaksperiode).venterPå() ?: nestemann
        return vedtaksperiode.vedtaksperiodeVenter(venterPå)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
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
        if (vedtaksperiode.skalFatteVedtak()) return vedtaksperiode.håndterKorrigerendeInntektsmelding(
            dager,
            aktivitetslogg
        )
        vedtaksperiode.håndterDager(dager, aktivitetslogg)
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(
            dager.hendelse,
            aktivitetslogg
        )
    }

    override fun håndtertInntektPåSkjæringstidspunktet(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Inntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndtertInntektPåSkjæringstidspunktetOgVurderVarsel(hendelse, aktivitetslogg)
    }

    override fun gjenopptaBehandling(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) =
        tilstand(aktivitetslogg, vedtaksperiode).gjenopptaBehandling(vedtaksperiode, hendelse, aktivitetslogg)

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        if (påminnelse.skalReberegnes()) return endreTilstand(vedtaksperiode, aktivitetslogg, AvventerInntektsmelding)
        if (vedtaksperiode.harFlereSkjæringstidspunkt()) {
            aktivitetslogg.funksjonellFeil(Varselkode.RV_IV_11)
            return vedtaksperiode.forkast(påminnelse, aktivitetslogg)
        }
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        anmodningOmForkasting: AnmodningOmForkasting,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        // todo: infotrygdendringer burde nok kommet inn som revurderingseventyr istedenfor.. ?
        if (!vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) return
        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerInntektsmelding)
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.behandlinger.forkastUtbetaling(aktivitetslogg)
        if (vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) endreTilstand(
            vedtaksperiode,
            aktivitetslogg,
            AvventerInntektsmelding
        )
        revurdering.inngåVedSaksbehandlerendring(vedtaksperiode, aktivitetslogg, vedtaksperiode.periode)
    }

    override fun beregnUtbetalinger(
        vedtaksperiode: Vedtaksperiode,
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg
    ) {
        super.beregnUtbetalinger(vedtaksperiode, ytelser, aktivitetslogg)
        if (!vedtaksperiode.skalFatteVedtak()) {
            // LOL vi skal til AUU så bare slenger på noen varsler her
            ytelser.valider(
                aktivitetslogg,
                vedtaksperiode.periode,
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.periode.endInclusive,
                vedtaksperiode.erForlengelse()
            )
        }
    }

    private fun tilstand(
        aktivitetslogg: IAktivitetslogg,
        vedtaksperiode: Vedtaksperiode,
    ): Tilstand {
        check(!vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) {
            "Periode i avventer blokkerende har ikke tilstrekkelig informasjon til utbetaling! VedtaksperiodeId = ${vedtaksperiode.id}"
        }
        if (!vedtaksperiode.skalFatteVedtak()) return ForventerIkkeInntekt
        if (vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) return ManglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag
        if (vedtaksperiode.harFlereSkjæringstidspunkt()) return HarFlereSkjæringstidspunkt(vedtaksperiode)
        if (vedtaksperiode.person.avventerSøknad(vedtaksperiode.periode)) return AvventerTidligereEllerOverlappendeSøknad

        val førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver =
            vedtaksperiode.førstePeriodeAnnenArbeidsgiverSomTrengerInntektsmelding()
        if (førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver != null) return TrengerInntektsmeldingAnnenArbeidsgiver(
            førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver
        )
        if (vedtaksperiode.vilkårsgrunnlag == null) return KlarForVilkårsprøving
        return KlarForBeregning
    }

    private sealed interface Tilstand {
        fun venteårsak(): Venteårsak? = null
        fun venterPå(): Vedtaksperiode? = null
        fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse, aktivitetslogg: IAktivitetslogg)
    }

    private data class HarFlereSkjæringstidspunkt(private val vedtaksperiode: Vedtaksperiode) : Tilstand {
        override fun venterPå() = vedtaksperiode
        override fun venteårsak() = Venteårsak.Hva.HJELP fordi Venteårsak.Hvorfor.FLERE_SKJÆRINGSTIDSPUNKT
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Denne perioden har flere skjæringstidspunkt slik den står nå.")
            aktivitetslogg.funksjonellFeil(Varselkode.RV_IV_11)
            return vedtaksperiode.forkast(hendelse, aktivitetslogg)
        }
    }

    private data object AvventerTidligereEllerOverlappendeSøknad : Tilstand {
        override fun venteårsak() = Venteårsak.Hva.SØKNAD.utenBegrunnelse
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Gjenopptar ikke behandling fordi minst én arbeidsgiver venter på søknad for sykmelding som er før eller overlapper med vedtaksperioden")
        }
    }

    private data object ForventerIkkeInntekt : Tilstand {
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            endreTilstand(vedtaksperiode, aktivitetslogg, AvsluttetUtenUtbetaling)
        }
    }

    private data object ManglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag : Tilstand {
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.funksjonellFeil(Varselkode.RV_SV_2)
            vedtaksperiode.forkast(hendelse, aktivitetslogg)
        }
    }

    private data class TrengerInntektsmeldingAnnenArbeidsgiver(private val trengerInntektsmelding: Vedtaksperiode) :
        Tilstand {
        override fun venteårsak() = trengerInntektsmelding.venteårsak()
        override fun venterPå() = trengerInntektsmelding
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Gjenopptar ikke behandling fordi minst én overlappende periode venter på nødvendig opplysninger fra arbeidsgiver")
        }
    }

    private data object KlarForVilkårsprøving : Tilstand {
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            endreTilstand(vedtaksperiode, aktivitetslogg, AvventerVilkårsprøving)
        }
    }

    private data object KlarForBeregning : Tilstand {
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            endreTilstand(vedtaksperiode, aktivitetslogg, AvventerHistorikk)
        }
    }
}
