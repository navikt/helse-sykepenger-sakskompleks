package no.nav.helse.person

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Validation.Companion.validation
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.Vedtaksperiode.ArbeidsgiveropplysningerStrategi
import no.nav.helse.person.Vedtaksperiode.FørInntektsmelding
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.Venteårsak.Hva.INNTEKTSMELDING
import no.nav.helse.person.Venteårsak.Hvorfor.FLERE_SKJÆRINGSTIDSPUNKT
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_10
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_11
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_2
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.inntekt.Skatteopplysning
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverFaktaavklartInntekt
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt

internal data object AvventerInntektsmelding : Vedtaksperiodetilstand {
    override val type: TilstandType = AVVENTER_INNTEKTSMELDING
    override fun makstid(vedtaksperiode: Vedtaksperiode, tilstandsendringstidspunkt: LocalDateTime): LocalDateTime =
        tilstandsendringstidspunkt.plusDays(180)

    override val arbeidsgiveropplysningerStrategi get(): ArbeidsgiveropplysningerStrategi = FørInntektsmelding
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.trengerInntektsmeldingReplay()
    }

    override fun lagUtbetalingstidslinje(
        vedtaksperiode: Vedtaksperiode,
        inntekt: ArbeidsgiverFaktaavklartInntekt?
    ): Utbetalingstidslinje {
        inntekt ?: error(
            "Det er en vedtaksperiode som ikke inngår i SP: ${vedtaksperiode.arbeidsgiver.organisasjonsnummer} - $vedtaksperiode.id - $vedtaksperiode.periode." +
                "Burde ikke arbeidsgiveren være kjent i sykepengegrunnlaget, enten i form av en skatteinntekt eller en tilkommet?"
        )

        val refusjonstidslinje = Beløpstidslinje.fra(
            vedtaksperiode.periode,
            Inntekt.INGEN,
            Kilde(UUID.randomUUID(), Avsender.SYSTEM, LocalDateTime.now())
        )
        return vedtaksperiode.behandlinger.lagUtbetalingstidslinje(
            inntekt,
            vedtaksperiode.jurist,
            refusjonstidslinje
        )
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) =
        if (vedtaksperiode.harFlereSkjæringstidspunkt()) HJELP fordi FLERE_SKJÆRINGSTIDSPUNKT else INNTEKTSMELDING.utenBegrunnelse

    override fun venter(
        vedtaksperiode: Vedtaksperiode,
        nestemann: Vedtaksperiode
    ) = vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun leaving(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        check(vedtaksperiode.behandlinger.harIkkeUtbetaling()) {
            "hæ?! vedtaksperiodens behandling er ikke uberegnet!"
        }
    }

    override fun skalHåndtereDager(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ): Boolean {
        return vedtaksperiode.skalHåndtereDagerAvventerInntektsmelding(dager, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
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
        vedtaksperiode.inntektsmeldingHåndtert(hendelse)
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

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.videreførEksisterendeRefusjonsopplysninger(aktivitetslogg = aktivitetslogg)
        vurderOmKanGåVidere(vedtaksperiode, revurdering.hendelse, aktivitetslogg)
        if (vedtaksperiode.tilstand !in setOf(AvventerInntektsmelding, AvventerBlokkerendePeriode)) return
        if (vedtaksperiode.tilstand == AvventerInntektsmelding && vedtaksperiode.sjekkTrengerArbeidsgiveropplysninger(
                aktivitetslogg
            )
        ) {
            vedtaksperiode.sendTrengerArbeidsgiveropplysninger()
        }
        revurdering.inngåVedSaksbehandlerendring(vedtaksperiode, aktivitetslogg, vedtaksperiode.periode)
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
        }
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver,
        aktivitetslogg: IAktivitetslogg
    ) {
        aktivitetslogg.info("Håndterer sykepengegrunnlag for arbeidsgiver")
        aktivitetslogg.varsel(RV_IV_10)

        val skatteopplysninger = sykepengegrunnlagForArbeidsgiver.inntekter()
        val omregnetÅrsinntekt = Skatteopplysning.omregnetÅrsinntekt(skatteopplysninger)

        vedtaksperiode.arbeidsgiver.lagreInntektFraAOrdningen(
            meldingsreferanseId = sykepengegrunnlagForArbeidsgiver.metadata.meldingsreferanseId,
            skjæringstidspunkt = vedtaksperiode.skjæringstidspunkt,
            omregnetÅrsinntekt = omregnetÅrsinntekt
        )
        val ingenRefusjon = Beløpstidslinje.fra(
            periode = vedtaksperiode.periode,
            beløp = Inntekt.INGEN,
            kilde = Kilde(
                sykepengegrunnlagForArbeidsgiver.metadata.meldingsreferanseId,
                sykepengegrunnlagForArbeidsgiver.metadata.avsender,
                sykepengegrunnlagForArbeidsgiver.metadata.innsendt
            )
        )
        vedtaksperiode.behandlinger.håndterRefusjonstidslinje(
            arbeidsgiver = vedtaksperiode.arbeidsgiver,
            hendelse = sykepengegrunnlagForArbeidsgiver,
            aktivitetslogg = aktivitetslogg,
            beregnSkjæringstidspunkt = vedtaksperiode.person.beregnSkjæringstidspunkt(),
            beregnArbeidsgiverperiode = vedtaksperiode.arbeidsgiver.beregnArbeidsgiverperiode(vedtaksperiode.jurist),
            refusjonstidslinje = ingenRefusjon
        )

        val event = PersonObserver.SkatteinntekterLagtTilGrunnEvent(
            organisasjonsnummer = vedtaksperiode.arbeidsgiver.organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiode.id,
            behandlingId = vedtaksperiode.behandlinger.sisteBehandlingId,
            skjæringstidspunkt = vedtaksperiode.skjæringstidspunkt,
            skatteinntekter = skatteopplysninger.map {
                PersonObserver.SkatteinntekterLagtTilGrunnEvent.Skatteinntekt(it.måned, it.beløp.månedlig)
            },
            omregnetÅrsinntekt = Skatteopplysning.omregnetÅrsinntekt(skatteopplysninger).årlig
        )
        vedtaksperiode.person.sendSkatteinntekterLagtTilGrunn(event)

        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerBlokkerendePeriode)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        if (vedtaksperiode.periodeRettFørHarFåttInntektsmelding()) {
            aktivitetslogg.info("Periode ser ut til å feilaktig vente på inntektsmelding. ")
            return endreTilstand(vedtaksperiode, aktivitetslogg, AvventerBlokkerendePeriode)
        }
        if (vedtaksperiode.harFlereSkjæringstidspunkt()) {
            aktivitetslogg.funksjonellFeil(RV_IV_11)
            return vedtaksperiode.forkast(påminnelse, aktivitetslogg)
        }
        if (påminnelse.skalReberegnes()) {
            vedtaksperiode.behandlinger.forkastUtbetaling(aktivitetslogg)
            return vurderOmKanGåVidere(vedtaksperiode, påminnelse, aktivitetslogg)
        }
        if (påminnelse.harVentet3MånederEllerMer()) {
            aktivitetslogg.info("Her ønsker vi å hente inntekt fra skatt")
            return vedtaksperiode.trengerInntektFraSkatt(aktivitetslogg)
        }
        if (vedtaksperiode.sjekkTrengerArbeidsgiveropplysninger(aktivitetslogg)) {
            vedtaksperiode.sendTrengerArbeidsgiveropplysninger()
        }
    }

    override fun gjenopptaBehandling(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.videreførEksisterendeRefusjonsopplysninger(aktivitetslogg = aktivitetslogg)
        vurderOmKanGåVidere(vedtaksperiode, hendelse, aktivitetslogg)
    }

    override fun replayUtført(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        if (vedtaksperiode.sjekkTrengerArbeidsgiveropplysninger(aktivitetslogg)) {
            vedtaksperiode.sendTrengerArbeidsgiveropplysninger()
            // ved out-of-order gir vi beskjed om at vi ikke trenger arbeidsgiveropplysninger for den seneste perioden lenger
            vedtaksperiode.arbeidsgiver.finnVedtaksperiodeRettEtter(vedtaksperiode)?.also {
                it.trengerIkkeArbeidsgiveropplysninger()
            }
        }
        vurderOmKanGåVidere(vedtaksperiode, hendelse, aktivitetslogg)
    }

    override fun inntektsmeldingFerdigbehandlet(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        vurderOmKanGåVidere(vedtaksperiode, hendelse, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        anmodningOmForkasting: AnmodningOmForkasting,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting, aktivitetslogg)
    }

    private fun vurderOmKanGåVidere(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (!vedtaksperiode.skalFatteVedtak()) return endreTilstand(
            vedtaksperiode,
            aktivitetslogg,
            AvsluttetUtenUtbetaling
        )
        if (vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) {
            aktivitetslogg.funksjonellFeil(RV_SV_2)
            return vedtaksperiode.forkast(hendelse, aktivitetslogg)
        }
        if (vedtaksperiode.harFlereSkjæringstidspunkt()) {
            aktivitetslogg.funksjonellFeil(RV_IV_11)
            return vedtaksperiode.forkast(hendelse, aktivitetslogg)
        }
        if (vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) return
        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerBlokkerendePeriode)
    }
}
