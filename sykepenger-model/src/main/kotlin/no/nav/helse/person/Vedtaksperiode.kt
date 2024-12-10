package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*
import no.nav.helse.Grunnbeløp
import no.nav.helse.Toggle
import no.nav.helse.dto.LazyVedtaksperiodeVenterDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.deserialisering.VedtaksperiodeInnDto
import no.nav.helse.dto.serialisering.VedtaksperiodeUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.etterlevelse.`§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Behandlingsavgjørelse
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.FunksjonelleFeilTilVarsler
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrInntektsgrunnlag
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.hendelser.Periode.Companion.lik
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.hendelser.SykdomstidslinjeHendelse
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.nesteDag
import no.nav.helse.person.Behandlinger.Companion.berik
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.arbeidsavklaringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.arbeidsforhold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.dagpenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.foreldrepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.inntekterForOpptjeningsvurdering
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.inntekterForSykepengegrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.inntekterForSykepengegrunnlagForArbeidsgiver
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.institusjonsopphold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.medlemskap
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.omsorgspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.opplæringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.pleiepenger
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Mottatt søknad som delvis overlapper`
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.varsel
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_24
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_28
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_29
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_30
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_31
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_32
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_33
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_34
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_35
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_36
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_37
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_38
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_5
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VT_1
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.builders.UtkastTilVedtakBuilder
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.PersonUtbetalingsperiode
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.refusjon.Refusjonsservitør
import no.nav.helse.sykdomstidslinje.Skjæringstidspunkt
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje.Companion.slåSammenForkastedeSykdomstidslinjer
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverFaktaavklartInntekt
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.AvvisDagerEtterDødsdatofilter
import no.nav.helse.utbetalingstidslinje.AvvisInngangsvilkårfilter
import no.nav.helse.utbetalingstidslinje.Maksdatoresultat
import no.nav.helse.utbetalingstidslinje.MaksimumSykepengedagerfilter
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetalingFilter
import no.nav.helse.utbetalingstidslinje.Sykdomsgradfilter
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjerFilter
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinjesubsumsjon
import no.nav.helse.økonomi.Inntekt
import org.slf4j.LoggerFactory

internal class Vedtaksperiode private constructor(
    val person: Person,
    val arbeidsgiver: Arbeidsgiver,
    val id: UUID,
    tilstand: Vedtaksperiodetilstand,
    val behandlinger: Behandlinger,
    private var egenmeldingsperioder: List<Periode>,
    private val opprettet: LocalDateTime,
    private var oppdatert: LocalDateTime = opprettet,
    private val subsumsjonslogg: Subsumsjonslogg
) : Aktivitetskontekst, Comparable<Vedtaksperiode>, BehandlingObserver {

    var tilstand: Vedtaksperiodetilstand = tilstand
        private set

    internal constructor(
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        person: Person,
        arbeidsgiver: Arbeidsgiver,
        sykdomstidslinje: Sykdomstidslinje,
        dokumentsporing: Dokumentsporing,
        sykmeldingsperiode: Periode,
        subsumsjonslogg: Subsumsjonslogg
    ) : this(
        person = person,
        arbeidsgiver = arbeidsgiver,
        id = UUID.randomUUID(),
        tilstand = Start,
        behandlinger = Behandlinger(),
        egenmeldingsperioder = søknad.egenmeldingsperioder(),
        opprettet = LocalDateTime.now(),
        subsumsjonslogg = subsumsjonslogg
    ) {
        registrerKontekst(aktivitetslogg)
        val periode = checkNotNull(sykdomstidslinje.periode()) { "sykdomstidslinjen er tom" }
        person.vedtaksperiodeOpprettet(id, arbeidsgiver.organisasjonsnummer, periode, periode.start, opprettet)
        behandlinger.initiellBehandling(sykmeldingsperiode, sykdomstidslinje, dokumentsporing, søknad)
    }

    private val sykmeldingsperiode get() = behandlinger.sykmeldingsperiode()
    val periode get() = behandlinger.periode()
    internal val sykdomstidslinje get() = behandlinger.sykdomstidslinje()
    val jurist
        get() = behandlinger.subsumsjonslogg(
            subsumsjonslogg,
            id,
            person.fødselsnummer,
            arbeidsgiver.organisasjonsnummer
        )
    internal val skjæringstidspunkt get() = behandlinger.skjæringstidspunkt()

    // 💡Må ikke forveksles med `førsteFraværsdag` 💡
    // F.eks. januar med agp 1-10 & 16-21 så er `førsteFraværsdag` 16.januar, mens `startdatoPåSammenhengendeVedtaksperioder` er 1.januar
    internal val startdatoPåSammenhengendeVedtaksperioder
        get() = arbeidsgiver.startdatoPåSammenhengendeVedtaksperioder(
            this
        )
    val vilkårsgrunnlag get() = person.vilkårsgrunnlagFor(skjæringstidspunkt)
    private val hendelseIder get() = behandlinger.dokumentsporing()
    val refusjonstidslinje get() = behandlinger.refusjonstidslinje()

    init {
        behandlinger.addObserver(this)
    }

    internal fun view() = VedtaksperiodeView(
        id = id,
        periode = periode,
        tilstand = tilstand.type,
        oppdatert = oppdatert,
        skjæringstidspunkt = skjæringstidspunkt,
        egenmeldingsperioder = egenmeldingsperioder,
        behandlinger = behandlinger.view()
    )

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Vedtaksperiode", mapOf("vedtaksperiodeId" to id.toString()))
    }

    internal fun håndter(sykmelding: Sykmelding) {
        sykmelding.trimLeft(periode.endInclusive)
    }

    private fun <Hendelse : SykdomstidslinjeHendelse> håndterSykdomstidslinjeHendelse(
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        håndtering: (Hendelse) -> Unit
    ) {
        if (!hendelse.erRelevant(this.periode)) return hendelse.vurdertTilOgMed(periode.endInclusive)
        registrerKontekst(aktivitetslogg)
        hendelse.leggTil(id, behandlinger)
        håndtering(hendelse)
        hendelse.vurdertTilOgMed(periode.endInclusive)
    }

    private fun validerTilstand(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        if (!tilstand.erFerdigBehandlet) return
        behandlinger.validerFerdigBehandlet(hendelse, aktivitetslogg)
    }

    internal fun håndter(
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        håndterSykdomstidslinjeHendelse(søknad, aktivitetslogg) {
            søknadHåndtert(søknad)
            tilstand.håndter(this, søknad, aktivitetslogg, arbeidsgivere, infotrygdhistorikk)
        }
    }

    internal fun håndter(hendelse: OverstyrTidslinje, aktivitetslogg: IAktivitetslogg) {
        håndterSykdomstidslinjeHendelse(hendelse, aktivitetslogg) {
            val arbeidsgiverperiodeFørOverstyring = arbeidsgiver.arbeidsgiverperiode(periode())
            tilstand.håndter(this, hendelse, aktivitetslogg)
            val arbeidsgiverperiodeEtterOverstyring = arbeidsgiver.arbeidsgiverperiode(periode())
            if (arbeidsgiverperiodeFørOverstyring != arbeidsgiverperiodeEtterOverstyring) {
                behandlinger.sisteInntektsmeldingDagerId()?.let {
                    person.arbeidsgiveropplysningerKorrigert(
                        PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                            korrigerendeInntektsopplysningId = hendelse.metadata.meldingsreferanseId,
                            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER,
                            korrigertInntektsmeldingId = it
                        )
                    )
                }
            }
        }
    }

    fun inntektsmeldingHåndtert(inntektsmelding: Inntektsmelding): Boolean {
        inntektsmelding.inntektHåndtert()
        if (!behandlinger.oppdaterDokumentsporing(inntektsmelding.dokumentsporing)) return true
        person.emitInntektsmeldingHåndtert(
            inntektsmelding.metadata.meldingsreferanseId,
            id,
            arbeidsgiver.organisasjonsnummer
        )
        return false
    }

    private fun søknadHåndtert(søknad: Søknad) {
        person.emitSøknadHåndtert(søknad.metadata.meldingsreferanseId, id, arbeidsgiver.organisasjonsnummer)
    }

    internal fun håndter(anmodningOmForkasting: AnmodningOmForkasting, aktivitetslogg: IAktivitetslogg) {
        if (!anmodningOmForkasting.erRelevant(id)) return
        registrerKontekst(aktivitetslogg)
        if (anmodningOmForkasting.force) return forkast(anmodningOmForkasting, aktivitetslogg)
        tilstand.håndter(this, anmodningOmForkasting, aktivitetslogg)
    }

    fun etterkomAnmodningOmForkasting(
        anmodningOmForkasting: AnmodningOmForkasting,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (!arbeidsgiver.kanForkastes(
                this,
                aktivitetslogg
            )
        ) return aktivitetslogg.info("Kan ikke etterkomme anmodning om forkasting")
        aktivitetslogg.info("Etterkommer anmodning om forkasting")
        forkast(anmodningOmForkasting, aktivitetslogg)
    }

    internal fun håndter(replays: InntektsmeldingerReplay, aktivitetslogg: IAktivitetslogg) {
        if (!replays.erRelevant(this.id)) return
        registrerKontekst(aktivitetslogg)
        tilstand.replayUtført(this, replays, aktivitetslogg)
    }

    internal fun inntektsmeldingFerdigbehandlet(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        registrerKontekst(aktivitetslogg)
        tilstand.inntektsmeldingFerdigbehandlet(this, hendelse, aktivitetslogg)
    }

    internal fun håndter(dager: DagerFraInntektsmelding, aktivitetslogg: IAktivitetslogg) {
        if (!tilstand.skalHåndtereDager(this, dager, aktivitetslogg) || dager.alleredeHåndtert(behandlinger))
            return dager.vurdertTilOgMed(periode.endInclusive)
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(this, dager, aktivitetslogg)
        dager.vurdertTilOgMed(periode.endInclusive)
    }

    fun skalHåndtereDagerRevurdering(dager: DagerFraInntektsmelding, aktivitetslogg: IAktivitetslogg): Boolean {
        return skalHåndtereDager(dager, aktivitetslogg) { sammenhengende ->
            dager.skalHåndteresAvRevurdering(periode, sammenhengende, finnArbeidsgiverperiode())
        }
    }

    fun skalHåndtereDagerAvventerInntektsmelding(
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ): Boolean {
        return skalHåndtereDager(dager, aktivitetslogg) { sammenhengende ->
            dager.skalHåndteresAv(sammenhengende)
        }
    }

    private fun skalHåndtereDager(
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg,
        strategi: DagerFraInntektsmelding.(Periode) -> Boolean
    ): Boolean {
        val sammenhengende = arbeidsgiver.finnSammenhengendeVedtaksperioder(this)
            .map { it.periode }
            .periode() ?: return false
        if (!strategi(dager, sammenhengende)) return false
        aktivitetslogg.info("Vedtaksperioden $periode håndterer dager fordi den sammenhengende perioden $sammenhengende overlapper med inntektsmelding")
        return true
    }

    fun håndterDager(dager: DagerFraInntektsmelding, aktivitetslogg: IAktivitetslogg) {
        val hendelse = dager.bitAvInntektsmelding(aktivitetslogg, periode) ?: dager.tomBitAvInntektsmelding(
            aktivitetslogg,
            periode
        )
        håndterDager(hendelse, aktivitetslogg) {
            dager.valider(aktivitetslogg, periode)
            dager.validerArbeidsgiverperiode(aktivitetslogg, periode, finnArbeidsgiverperiode())
        }
    }

    private fun håndterDagerUtenEndring(dager: DagerFraInntektsmelding, aktivitetslogg: IAktivitetslogg) {
        val hendelse = dager.tomBitAvInntektsmelding(aktivitetslogg, periode)
        håndterDager(hendelse, aktivitetslogg) {
            dager.valider(aktivitetslogg, periode, finnArbeidsgiverperiode())
        }
    }

    private fun håndterDager(
        hendelse: DagerFraInntektsmelding.BitAvInntektsmelding,
        aktivitetslogg: IAktivitetslogg,
        validering: () -> Unit
    ) {
        if (egenmeldingsperioder.isNotEmpty()) {
            aktivitetslogg.info("Forkaster egenmeldinger oppgitt i sykmelding etter at arbeidsgiverperiode fra inntektsmeldingen er håndtert: $egenmeldingsperioder")
            egenmeldingsperioder = emptyList()
        }
        oppdaterHistorikk(hendelse, aktivitetslogg, validering)
    }

    internal fun håndterHistorikkFraInfotrygd(
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(this, hendelse, aktivitetslogg, infotrygdhistorikk)
    }

    internal fun håndter(
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        if (!ytelser.erRelevant(id)) return
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(person, arbeidsgiver, this, ytelser, aktivitetslogg, infotrygdhistorikk)
    }

    internal fun håndter(utbetalingsavgjørelse: Behandlingsavgjørelse, aktivitetslogg: IAktivitetslogg) {
        if (!utbetalingsavgjørelse.relevantVedtaksperiode(id)) return
        if (behandlinger.gjelderIkkeFor(utbetalingsavgjørelse)) return aktivitetslogg.info("Ignorerer løsning på utbetalingsavgjørelse, utbetalingid på løsningen matcher ikke vedtaksperiodens nåværende utbetaling")
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(person, arbeidsgiver, this, utbetalingsavgjørelse, aktivitetslogg)
    }

    internal fun håndter(
        sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (!sykepengegrunnlagForArbeidsgiver.erRelevant(aktivitetslogg, id, skjæringstidspunkt)) return
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(this, sykepengegrunnlagForArbeidsgiver, aktivitetslogg)
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag, aktivitetslogg: IAktivitetslogg) {
        if (!vilkårsgrunnlag.erRelevant(aktivitetslogg, id, skjæringstidspunkt)) return
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(this, vilkårsgrunnlag, aktivitetslogg)
    }

    internal fun håndter(simulering: Simulering, aktivitetslogg: IAktivitetslogg) {
        if (simulering.vedtaksperiodeId != this.id.toString()) return
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(this, simulering, aktivitetslogg)
    }

    internal fun håndter(hendelse: UtbetalingHendelse, aktivitetslogg: IAktivitetslogg) {
        if (!behandlinger.håndterUtbetalinghendelse(hendelse, aktivitetslogg)) return
        registrerKontekst(aktivitetslogg)
        tilstand.håndter(this, hendelse, aktivitetslogg)
    }

    internal fun håndter(
        hendelse: AnnullerUtbetaling,
        aktivitetslogg: IAktivitetslogg,
        vedtaksperioder: List<Vedtaksperiode>
    ) {
        registrerKontekst(aktivitetslogg)
        val annullering = behandlinger.håndterAnnullering(
            arbeidsgiver,
            hendelse,
            aktivitetslogg,
            vedtaksperioder.map { it.behandlinger }) ?: return
        aktivitetslogg.info("Forkaster denne, og senere perioder, som følge av annullering.")
        forkast(hendelse, aktivitetslogg)
        person.igangsettOverstyring(Revurderingseventyr.annullering(hendelse, annullering.periode()), aktivitetslogg)
    }

    internal fun håndter(påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg): Boolean {
        if (!påminnelse.erRelevant(id)) return false
        registrerKontekst(aktivitetslogg)
        tilstand.påminnelse(this, påminnelse, aktivitetslogg)
        return true
    }

    internal fun nyAnnullering(aktivitetslogg: IAktivitetslogg) {
        registrerKontekst(aktivitetslogg)
        tilstand.nyAnnullering(this, aktivitetslogg)
    }

    internal fun håndter(overstyrInntektsgrunnlag: OverstyrInntektsgrunnlag, aktivitetslogg: IAktivitetslogg): Boolean {
        if (!overstyrInntektsgrunnlag.erRelevant(skjæringstidspunkt)) return false
        if (vilkårsgrunnlag?.erArbeidsgiverRelevant(arbeidsgiver.organisasjonsnummer) != true) return false
        registrerKontekst(aktivitetslogg)

        // i praksis double-dispatch, kotlin-style
        when (overstyrInntektsgrunnlag) {
            is Grunnbeløpsregulering -> person.vilkårsprøvEtterNyInformasjonFraSaksbehandler(
                overstyrInntektsgrunnlag,
                aktivitetslogg,
                skjæringstidspunkt,
                jurist
            )

            is OverstyrArbeidsforhold -> person.vilkårsprøvEtterNyInformasjonFraSaksbehandler(
                overstyrInntektsgrunnlag,
                aktivitetslogg,
                skjæringstidspunkt,
                jurist
            )

            is OverstyrArbeidsgiveropplysninger -> person.vilkårsprøvEtterNyInformasjonFraSaksbehandler(
                overstyrInntektsgrunnlag,
                aktivitetslogg,
                skjæringstidspunkt,
                jurist
            )

            is SkjønnsmessigFastsettelse -> person.vilkårsprøvEtterNyInformasjonFraSaksbehandler(
                overstyrInntektsgrunnlag,
                aktivitetslogg,
                skjæringstidspunkt,
                jurist
            )
        }
        return true
    }

    internal fun håndter(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, servitør: Refusjonsservitør) {
        val refusjonstidslinje = servitør.servér(startdatoPåSammenhengendeVedtaksperioder, periode)
        if (refusjonstidslinje.isEmpty()) return
        behandlinger.håndterRefusjonstidslinje(
            arbeidsgiver,
            hendelse,
            aktivitetslogg,
            person.beregnSkjæringstidspunkt(),
            arbeidsgiver.beregnArbeidsgiverperiode(jurist),
            refusjonstidslinje
        )
    }

    private fun påvirkerArbeidsgiverperioden(ny: Vedtaksperiode): Boolean {
        val dagerMellom = ny.periode.periodeMellom(this.periode.start)?.count() ?: return false
        return dagerMellom < MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD
    }

    override fun compareTo(other: Vedtaksperiode): Int {
        val delta = this.periode.start.compareTo(other.periode.start)
        if (delta != 0) return delta
        return this.periode.endInclusive.compareTo(other.periode.endInclusive)
    }

    internal infix fun før(other: Vedtaksperiode) = this < other
    internal infix fun etter(other: Vedtaksperiode) = this > other
    internal fun erVedtaksperiodeRettFør(other: Vedtaksperiode) =
        this.sykdomstidslinje.erRettFør(other.sykdomstidslinje)

    internal fun erForlengelse(): Boolean = arbeidsgiver
        .finnVedtaksperiodeRettFør(this)
        ?.takeIf { it.skalFatteVedtak() } != null

    fun manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag(): Boolean {
        return vilkårsgrunnlag?.harNødvendigInntektForVilkårsprøving(arbeidsgiver.organisasjonsnummer) == false
    }

    fun måInnhenteInntektEllerRefusjon(aktivitetslogg: IAktivitetslogg): Boolean {
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        if (!arbeidsgiverperiode.forventerInntekt(periode)) return false
        if (tilstand.arbeidsgiveropplysningerStrategi.harInntektOgRefusjon(
                this,
                arbeidsgiverperiode,
                aktivitetslogg
            )
        ) return false
        return true
    }

    fun harFlereSkjæringstidspunkt(): Boolean {
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        if (!arbeidsgiverperiode.forventerInntekt(periode)) return false
        val utbetalingsdagerFørSkjæringstidspunkt =
            Arbeidsgiverperiode.utbetalingsdagerFørSkjæringstidspunkt(skjæringstidspunkt, periode, arbeidsgiverperiode)
        if (utbetalingsdagerFørSkjæringstidspunkt.isEmpty()) return false
        sikkerlogg.warn(
            "Har flere skjæringstidspunkt:\n\n (${
                id.toString().take(5).uppercase()
            }) $periode\nSkjæringstidspunkt: ${skjæringstidspunkt.format(datoformat)}\nArbeidsgiver: ${arbeidsgiver.organisasjonsnummer}\nUtbetalingsdager før skjæringstidspunkt: ${
                utbetalingsdagerFørSkjæringstidspunkt.joinToString {
                    it.format(
                        datoformat
                    )
                }
            }\nSykdomstidslinje: ${sykdomstidslinje.toShortString()}"
        )
        return true
    }

    fun harTilkomneInntekter(): Boolean {
        return vilkårsgrunnlag?.harTilkommendeInntekter() ?: false
    }

    internal fun kanForkastes(arbeidsgiverUtbetalinger: List<Utbetaling>, aktivitetslogg: IAktivitetslogg): Boolean {
        if (!behandlinger.kanForkastes(aktivitetslogg, arbeidsgiverUtbetalinger)) {
            aktivitetslogg.info("[kanForkastes] Kan ikke forkastes fordi behandlinger nekter det")
            return false
        }
        aktivitetslogg.info("[kanForkastes] Kan forkastes fordi evt. overlappende utbetalinger er annullerte/forkastet")
        return true
    }

    internal fun forkast(
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        utbetalinger: List<Utbetaling>
    ): VedtaksperiodeForkastetEventBuilder? {
        registrerKontekst(aktivitetslogg)
        if (!kanForkastes(utbetalinger, aktivitetslogg)) return null
        aktivitetslogg.info("Forkaster vedtaksperiode: %s", this.id.toString())
        this.behandlinger.forkast(arbeidsgiver, hendelse, aktivitetslogg)
        val arbeidsgiverperiodeHensyntarForkastede = finnArbeidsgiverperiodeHensyntarForkastede()
        val trengerArbeidsgiveropplysninger =
            arbeidsgiverperiodeHensyntarForkastede?.forventerOpplysninger(periode) ?: false
        val sykmeldingsperioder =
            sykmeldingsperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiodeHensyntarForkastede)
        val vedtaksperiodeForkastetEventBuilder =
            VedtaksperiodeForkastetEventBuilder(tilstand.type, trengerArbeidsgiveropplysninger, sykmeldingsperioder)
        tilstand(aktivitetslogg, TilInfotrygd)
        return vedtaksperiodeForkastetEventBuilder
    }

    private fun sykmeldingsperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?): List<Periode> {
        val forkastedeVedtaksperioder =
            arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiodeInkludertForkastede(arbeidsgiverperiode)
        return (forkastedeVedtaksperioder.map { it.sykmeldingsperiode }
            .filter { it.start < sykmeldingsperiode.endInclusive } + listOf(sykmeldingsperiode)).distinct()
    }

    internal inner class VedtaksperiodeForkastetEventBuilder(
        private val gjeldendeTilstand: TilstandType,
        private val trengerArbeidsgiveropplysninger: Boolean,
        private val sykmeldingsperioder: List<Periode>
    ) {
        internal fun buildAndEmit() {
            person.vedtaksperiodeForkastet(
                PersonObserver.VedtaksperiodeForkastetEvent(
                    organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                    vedtaksperiodeId = id,
                    gjeldendeTilstand = gjeldendeTilstand,
                    hendelser = hendelseIder,
                    fom = periode.start,
                    tom = periode.endInclusive,
                    behandletIInfotrygd = person.erBehandletIInfotrygd(periode),
                    forlengerPeriode = person.nåværendeVedtaksperioder {
                        (it.periode.overlapperMed(periode) || it.periode.erRettFør(
                            periode
                        ))
                    }.isNotEmpty(),
                    harPeriodeInnenfor16Dager = person.nåværendeVedtaksperioder { påvirkerArbeidsgiverperioden(it) }
                        .isNotEmpty(),
                    trengerArbeidsgiveropplysninger = trengerArbeidsgiveropplysninger,
                    sykmeldingsperioder = sykmeldingsperioder
                )
            )
        }
    }

    fun forkast(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        if (!arbeidsgiver.kanForkastes(
                this,
                aktivitetslogg
            )
        ) return aktivitetslogg.info("Kan ikke etterkomme forkasting")
        person.søppelbøtte(hendelse, aktivitetslogg, TIDLIGERE_OG_ETTERGØLGENDE(this))
    }

    fun revurderTidslinje(hendelse: OverstyrTidslinje, aktivitetslogg: IAktivitetslogg) {
        oppdaterHistorikk(hendelse, aktivitetslogg) {
            // ingen validering å gjøre :(
        }
        igangsettOverstyringAvTidslinje(hendelse, aktivitetslogg)
    }

    private fun igangsettOverstyringAvTidslinje(hendelse: OverstyrTidslinje, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.info("Igangsetter overstyring av tidslinje")
        val vedtaksperiodeTilRevurdering = arbeidsgiver.finnVedtaksperiodeFør(this)
            ?.takeIf { nyArbeidsgiverperiodeEtterEndring(it) } ?: this
        person.igangsettOverstyring(
            Revurderingseventyr.sykdomstidslinje(
                hendelse,
                vedtaksperiodeTilRevurdering.skjæringstidspunkt,
                vedtaksperiodeTilRevurdering.periode
            ),
            aktivitetslogg
        )
    }

    private fun nyArbeidsgiverperiodeEtterEndring(other: Vedtaksperiode): Boolean {
        if (this.behandlinger.erUtbetaltPåForskjelligeUtbetalinger(other.behandlinger)) return false
        val arbeidsgiverperiodeOther = other.finnArbeidsgiverperiode()
        val arbeidsgiverperiodeThis = this.finnArbeidsgiverperiode()
        if (arbeidsgiverperiodeOther == null || arbeidsgiverperiodeThis == null) return false
        val periode = arbeidsgiverperiodeThis.periode(this.periode.endInclusive)
        // ingen overlapp i arbeidsgiverperiodene => ny arbeidsgiverperiode
        return periode !in arbeidsgiverperiodeOther
    }

    internal fun periode() = periode
    private fun registrerKontekst(aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(arbeidsgiver)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.kontekst(this.tilstand)
    }

    private fun tilstand(
        event: IAktivitetslogg,
        nyTilstand: Vedtaksperiodetilstand,
        block: () -> Unit = {}
    ) {
        if (tilstand == nyTilstand) return  // Already in this state => ignore
        tilstand.leaving(this, event)

        val previousState = tilstand

        tilstand = nyTilstand
        oppdatert = LocalDateTime.now()
        block()

        event.kontekst(tilstand)
        emitVedtaksperiodeEndret(previousState)
        tilstand.entering(this, event)
    }

    private fun oppdaterHistorikk(
        hendelse: SykdomshistorikkHendelse,
        aktivitetslogg: IAktivitetslogg,
        validering: () -> Unit
    ) {
        behandlinger.håndterEndring(
            person,
            arbeidsgiver,
            hendelse,
            aktivitetslogg,
            person.beregnSkjæringstidspunkt(),
            arbeidsgiver.beregnArbeidsgiverperiode(jurist),
            validering
        )
    }

    private fun håndterEgenmeldingsperioderFraOverlappendeSøknad(søknad: Søknad, aktivitetslogg: IAktivitetslogg) {
        val nyeEgenmeldingsperioder = søknad.egenmeldingsperioder()
        if (egenmeldingsperioder.lik(nyeEgenmeldingsperioder)) return
        if (nyeEgenmeldingsperioder.isEmpty()) return aktivitetslogg.info("Hadde egenmeldingsperioder $egenmeldingsperioder, men den overlappende søknaden har ingen.")

        val sammenslåtteEgenmeldingsperioder =
            (egenmeldingsperioder + nyeEgenmeldingsperioder).grupperSammenhengendePerioderMedHensynTilHelg()
        aktivitetslogg.info("Oppdaterer egenmeldingsperioder fra $egenmeldingsperioder til $sammenslåtteEgenmeldingsperioder")
        egenmeldingsperioder = sammenslåtteEgenmeldingsperioder
    }

    fun håndterSøknad(
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        nesteTilstand: () -> Vedtaksperiodetilstand? = { null }
    ) {
        oppdaterHistorikk(søknad, aktivitetslogg) {
            søknad.valider(aktivitetslogg, vilkårsgrunnlag, jurist)
        }
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return forkast(søknad, aktivitetslogg)
        person.oppdaterVilkårsgrunnlagMedInntektene(
            skjæringstidspunkt,
            aktivitetslogg,
            periode,
            søknad.nyeInntekterUnderveis(aktivitetslogg),
            jurist
        )
        nesteTilstand()?.also { tilstand(aktivitetslogg, it) }
    }

    fun håndterOverlappendeSøknad(
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        nesteTilstand: Vedtaksperiodetilstand? = null
    ) {
        if (søknad.delvisOverlappende(periode)) {
            aktivitetslogg.funksjonellFeil(`Mottatt søknad som delvis overlapper`)
            return forkast(søknad, aktivitetslogg)
        }
        aktivitetslogg.info("Håndterer overlappende søknad")
        håndterEgenmeldingsperioderFraOverlappendeSøknad(søknad, aktivitetslogg)
        håndterSøknad(søknad, aktivitetslogg) { nesteTilstand }
        person.igangsettOverstyring(
            Revurderingseventyr.korrigertSøknad(søknad, skjæringstidspunkt, periode),
            aktivitetslogg
        )
    }

    fun håndterOverlappendeSøknadRevurdering(søknad: Søknad, aktivitetslogg: IAktivitetslogg) {
        if (søknad.delvisOverlappende(periode)) return aktivitetslogg.funksjonellFeil(
            `Mottatt søknad som delvis overlapper`
        )
        if (søknad.sendtTilGosys()) return aktivitetslogg.funksjonellFeil(RV_SØ_30)
        if (søknad.utenlandskSykmelding()) return aktivitetslogg.funksjonellFeil(RV_SØ_29)
        else {
            aktivitetslogg.info("Søknad har trigget en revurdering")
            håndterEgenmeldingsperioderFraOverlappendeSøknad(søknad, aktivitetslogg)
            person.oppdaterVilkårsgrunnlagMedInntektene(
                skjæringstidspunkt,
                aktivitetslogg,
                periode,
                søknad.nyeInntekterUnderveis(aktivitetslogg),
                jurist
            )
            oppdaterHistorikk(søknad, aktivitetslogg) {
                søknad.valider(aktivitetslogg, vilkårsgrunnlag, jurist)
            }
        }

        person.igangsettOverstyring(
            Revurderingseventyr.korrigertSøknad(søknad, skjæringstidspunkt, periode),
            aktivitetslogg
        )
    }

    fun håndtertInntektPåSkjæringstidspunktetOgVurderVarsel(
        hendelse: Inntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
        val harHåndtertInntektTidligere = behandlinger.harHåndtertInntektTidligere()
        if (inntektsmeldingHåndtert(hendelse)) return
        if (!harHåndtertInntektTidligere) return
        aktivitetslogg.varsel(RV_IM_4)
    }

    fun håndterKorrigerendeInntektsmelding(dager: DagerFraInntektsmelding, aktivitetslogg: IAktivitetslogg) {
        val korrigertInntektsmeldingId = behandlinger.sisteInntektsmeldingDagerId()
        val opprinneligAgp = finnArbeidsgiverperiode()
        if (dager.erKorrigeringForGammel(aktivitetslogg, opprinneligAgp)) {
            håndterDagerUtenEndring(dager, aktivitetslogg)
        } else {
            håndterDager(dager, aktivitetslogg)
        }

        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return

        val nyAgp = finnArbeidsgiverperiode()
        if (opprinneligAgp != null && !opprinneligAgp.klinLik(nyAgp)) {
            aktivitetslogg.varsel(
                RV_IM_24,
                "Ny agp er utregnet til å være ulik tidligere utregnet agp i ${tilstand.type.name}"
            )
            korrigertInntektsmeldingId?.let {
                person.arbeidsgiveropplysningerKorrigert(
                    PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                        korrigerendeInntektsopplysningId = dager.hendelse.metadata.meldingsreferanseId,
                        korrigerendeInntektektsopplysningstype = Inntektsopplysningstype.INNTEKTSMELDING,
                        korrigertInntektsmeldingId = it
                    )
                )
            }
        }
    }

    fun håndterVilkårsgrunnlag(
        vilkårsgrunnlag: Vilkårsgrunnlag,
        aktivitetslogg: IAktivitetslogg,
        nesteTilstand: Vedtaksperiodetilstand
    ) {
        val skatteopplysninger = vilkårsgrunnlag.skatteopplysninger()
        val sykepengegrunnlag = person.avklarSykepengegrunnlag(
            aktivitetslogg,
            skjæringstidspunkt,
            skatteopplysninger,
            jurist
        )
        vilkårsgrunnlag.valider(aktivitetslogg, sykepengegrunnlag, jurist)
        val grunnlagsdata = vilkårsgrunnlag.grunnlagsdata()
        grunnlagsdata.validerFørstegangsvurdering(aktivitetslogg)
        person.lagreVilkårsgrunnlag(grunnlagsdata)
        aktivitetslogg.info("Vilkårsgrunnlag vurdert")
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return forkast(vilkårsgrunnlag, aktivitetslogg)
        arbeidsgiver.sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode(this, aktivitetslogg)
        tilstand(aktivitetslogg, nesteTilstand)
    }

    fun håndterUtbetalingHendelse(aktivitetslogg: IAktivitetslogg) {
        if (!aktivitetslogg.harFunksjonelleFeilEllerVerre()) return
        aktivitetslogg.funksjonellFeil(RV_UT_5)
    }

    fun trengerYtelser(aktivitetslogg: IAktivitetslogg) {
        val søkevinduFamilieytelser = periode.familieYtelserPeriode
        foreldrepenger(aktivitetslogg, søkevinduFamilieytelser)
        pleiepenger(aktivitetslogg, søkevinduFamilieytelser)
        omsorgspenger(aktivitetslogg, søkevinduFamilieytelser)
        opplæringspenger(aktivitetslogg, søkevinduFamilieytelser)
        institusjonsopphold(aktivitetslogg, periode)
        arbeidsavklaringspenger(aktivitetslogg, periode.start.minusMonths(6), periode.endInclusive)
        dagpenger(aktivitetslogg, periode.start.minusMonths(2), periode.endInclusive)
    }

    fun trengerVilkårsgrunnlag(aktivitetslogg: IAktivitetslogg) {
        val beregningSlutt = YearMonth.from(skjæringstidspunkt).minusMonths(1)
        inntekterForSykepengegrunnlag(aktivitetslogg, skjæringstidspunkt, beregningSlutt.minusMonths(2), beregningSlutt)
        inntekterForOpptjeningsvurdering(aktivitetslogg, skjæringstidspunkt, beregningSlutt, beregningSlutt)
        arbeidsforhold(aktivitetslogg, skjæringstidspunkt)
        medlemskap(aktivitetslogg, skjæringstidspunkt, periode.start, periode.endInclusive)
    }

    fun trengerInntektFraSkatt(aktivitetslogg: IAktivitetslogg) {
        if (Toggle.InntektsmeldingSomIkkeKommer.enabled) {
            val beregningSlutt = YearMonth.from(skjæringstidspunkt).minusMonths(1)
            inntekterForSykepengegrunnlagForArbeidsgiver(
                aktivitetslogg,
                skjæringstidspunkt,
                arbeidsgiver.organisasjonsnummer,
                beregningSlutt.minusMonths(2),
                beregningSlutt
            )
        }
    }

    fun sjekkTrengerArbeidsgiveropplysninger(aktivitetslogg: IAktivitetslogg): Boolean {
        if (!måInnhenteInntektEllerRefusjon(aktivitetslogg)) return false
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        return arbeidsgiverperiode.forventerOpplysninger(periode)
    }

    fun sendTrengerArbeidsgiveropplysninger(arbeidsgiverperiode: Arbeidsgiverperiode? = finnArbeidsgiverperiode()) {
        checkNotNull(arbeidsgiverperiode) { "Må ha arbeidsgiverperiode før vi sier dette." }
        val forespurtInntektOgRefusjon = person.forespurtInntektOgRefusjonsopplysninger(
            arbeidsgiver.organisasjonsnummer,
            skjæringstidspunkt,
            periode
        ) ?: listOf(
            PersonObserver.Inntekt(forslag = null),
            PersonObserver.Refusjon(forslag = emptyList())
        )
        val forespurteOpplysninger =
            forespurtInntektOgRefusjon + listOfNotNull(forespurtArbeidsgiverperiode(arbeidsgiverperiode))

        val vedtaksperioder = when {
            // For å beregne riktig arbeidsgiverperiode/første fraværsdag
            PersonObserver.Arbeidsgiverperiode in forespurteOpplysninger -> vedtaksperioderIArbeidsgiverperiodeTilOgMedDenne(
                arbeidsgiverperiode
            )
            // Dersom vi ikke trenger å beregne arbeidsgiverperiode/første fravarsdag trenger vi bare denne sykemeldingsperioden
            else -> listOf(this)
        }
        val førsteFraværsdager = person.førsteFraværsdager(skjæringstidspunkt)

        person.trengerArbeidsgiveropplysninger(
            PersonObserver.TrengerArbeidsgiveropplysningerEvent(
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                vedtaksperiodeId = id,
                skjæringstidspunkt = skjæringstidspunkt,
                sykmeldingsperioder = sykmeldingsperioder(vedtaksperioder),
                egenmeldingsperioder = egenmeldingsperioder(vedtaksperioder),
                førsteFraværsdager = førsteFraværsdager,
                forespurteOpplysninger = forespurteOpplysninger
            )
        )
    }

    private fun vedtaksperioderIArbeidsgiverperiodeTilOgMedDenne(arbeidsgiverperiode: Arbeidsgiverperiode?): List<Vedtaksperiode> {
        if (arbeidsgiverperiode == null) return listOf(this)
        return arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode).filter { it <= this }
    }

    fun trengerIkkeArbeidsgiveropplysninger() {
        person.trengerIkkeArbeidsgiveropplysninger(
            PersonObserver.TrengerIkkeArbeidsgiveropplysningerEvent(
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                vedtaksperiodeId = id
            )
        )
    }

    private fun forespurtArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?) =
        if (trengerArbeidsgiverperiode(arbeidsgiverperiode)) PersonObserver.Arbeidsgiverperiode else null

    private fun trengerArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?) =
        arbeidsgiverperiode != null && arbeidsgiverperiode.forventerArbeidsgiverperiodeopplysning(periode)
            && harIkkeFåttOpplysningerOmArbeidsgiverperiode(arbeidsgiverperiode)

    private fun harIkkeFåttOpplysningerOmArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode) =
        arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode)
            .none { it.behandlinger.harHåndtertDagerTidligere() }

    fun trengerInntektsmeldingReplay() {
        val arbeidsgiverperiode = finnArbeidsgiverperiode()
        val trengerArbeidsgiverperiode = trengerArbeidsgiverperiode(arbeidsgiverperiode)
        val vedtaksperioder = when {
            // For å beregne riktig arbeidsgiverperiode/første fraværsdag
            trengerArbeidsgiverperiode -> vedtaksperioderIArbeidsgiverperiodeTilOgMedDenne(arbeidsgiverperiode)
            // Dersom vi ikke trenger å beregne arbeidsgiverperiode/første fravarsdag trenger vi bare denne sykemeldingsperioden
            else -> listOf(this)
        }
        val førsteFraværsdager = person.førsteFraværsdager(skjæringstidspunkt)

        person.inntektsmeldingReplay(
            vedtaksperiodeId = id,
            skjæringstidspunkt = skjæringstidspunkt,
            organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
            sykmeldingsperioder = sykmeldingsperioder(vedtaksperioder),
            egenmeldingsperioder = egenmeldingsperioder(vedtaksperioder),
            førsteFraværsdager = førsteFraværsdager,
            trengerArbeidsgiverperiode = trengerArbeidsgiverperiode,
            erPotensiellForespørsel = !skalFatteVedtak()
        )
    }

    private fun emitVedtaksperiodeEndret(previousState: Vedtaksperiodetilstand) {
        val event = PersonObserver.VedtaksperiodeEndretEvent(
            organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
            vedtaksperiodeId = id,
            gjeldendeTilstand = tilstand.type,
            forrigeTilstand = previousState.type,
            hendelser = hendelseIder,
            makstid = makstid(),
            fom = periode.start,
            tom = periode.endInclusive,
            skjæringstidspunkt = skjæringstidspunkt
        )

        person.vedtaksperiodeEndret(event)
    }

    override fun avsluttetUtenVedtak(
        aktivitetslogg: IAktivitetslogg,
        behandlingId: UUID,
        tidsstempel: LocalDateTime,
        periode: Periode,
        dokumentsporing: Set<UUID>
    ) {
        if (finnArbeidsgiverperiode()?.dekkesAvArbeidsgiver(periode) != false) {
            jurist.logg(`§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`(periode, sykdomstidslinje.subsumsjonsformat()))
        }
        person.avsluttetUtenVedtak(
            PersonObserver.AvsluttetUtenVedtakEvent(
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                vedtaksperiodeId = id,
                behandlingId = behandlingId,
                periode = periode,
                hendelseIder = hendelseIder,
                skjæringstidspunkt = skjæringstidspunkt,
                avsluttetTidspunkt = tidsstempel
            )
        )
        person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun vedtakIverksatt(
        aktivitetslogg: IAktivitetslogg,
        vedtakFattetTidspunkt: LocalDateTime,
        behandling: Behandlinger.Behandling
    ) {
        val utkastTilVedtakBuilder = utkastTilVedtakBuilder()
        // Til ettertanke: Her er vi aldri innom "behandlinger"-nivå, så får ikke "Grunnbeløpsregulering"-tag, men AvsluttetMedVedtak har jo ikke tags nå uansett.
        behandling.berik(utkastTilVedtakBuilder)
        person.avsluttetMedVedtak(utkastTilVedtakBuilder.buildAvsluttedMedVedtak(vedtakFattetTidspunkt, hendelseIder))
        person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun vedtakAnnullert(aktivitetslogg: IAktivitetslogg, behandlingId: UUID) {
        person.vedtaksperiodeAnnullert(
            PersonObserver.VedtaksperiodeAnnullertEvent(
                periode.start, periode.endInclusive, id, arbeidsgiver.organisasjonsnummer,
                behandlingId
            )
        )
    }

    override fun behandlingLukket(behandlingId: UUID) {
        person.behandlingLukket(
            PersonObserver.BehandlingLukketEvent(
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                vedtaksperiodeId = id,
                behandlingId = behandlingId
            )
        )
    }

    override fun behandlingForkastet(behandlingId: UUID, hendelse: Hendelse) {
        person.behandlingForkastet(
            PersonObserver.BehandlingForkastetEvent(
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                vedtaksperiodeId = id,
                behandlingId = behandlingId,
                automatiskBehandling = hendelse.metadata.automatiskBehandling
            )
        )
    }

    override fun nyBehandling(
        id: UUID,
        periode: Periode,
        meldingsreferanseId: UUID,
        innsendt: LocalDateTime,
        registert: LocalDateTime,
        avsender: Avsender,
        type: PersonObserver.BehandlingOpprettetEvent.Type,
        søknadIder: Set<UUID>
    ) {
        val event = PersonObserver.BehandlingOpprettetEvent(
            organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
            vedtaksperiodeId = this.id,
            søknadIder = behandlinger.søknadIder() + søknadIder,
            behandlingId = id,
            fom = periode.start,
            tom = periode.endInclusive,
            type = type,
            kilde = PersonObserver.BehandlingOpprettetEvent.Kilde(meldingsreferanseId, innsendt, registert, avsender)
        )
        person.nyBehandling(event)
    }

    override fun utkastTilVedtak(utkastTilVedtak: PersonObserver.UtkastTilVedtakEvent) {
        person.utkastTilVedtak(utkastTilVedtak)
    }

    fun høstingsresultater(
        aktivitetslogg: IAktivitetslogg,
        simuleringtilstand: Vedtaksperiodetilstand,
        godkjenningtilstand: Vedtaksperiodetilstand
    ) = when {
        behandlinger.harUtbetalinger() -> tilstand(aktivitetslogg, simuleringtilstand) {
            aktivitetslogg.info("""Saken oppfyller krav for behandling, settes til "Avventer simulering"""")
        }

        else -> tilstand(aktivitetslogg, godkjenningtilstand) {
            aktivitetslogg.info("""Saken oppfyller krav for behandling, settes til "Avventer godkjenning" fordi ingenting skal utbetales""")
        }
    }

    private fun Vedtaksperiodetilstand.påminnelse(
        vedtaksperiode: Vedtaksperiode,
        påminnelse: Påminnelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (!påminnelse.gjelderTilstand(aktivitetslogg, type)) return vedtaksperiode.person.vedtaksperiodeIkkePåminnet(
            id,
            arbeidsgiver.organisasjonsnummer,
            type
        )
        vedtaksperiode.person.vedtaksperiodePåminnet(id, arbeidsgiver.organisasjonsnummer, påminnelse)
        val beregnetMakstid = { tilstandsendringstidspunkt: LocalDateTime -> makstid(tilstandsendringstidspunkt) }
        if (påminnelse.nåddMakstid(beregnetMakstid)) return håndterMakstid(vedtaksperiode, påminnelse, aktivitetslogg)
        håndter(vedtaksperiode, påminnelse, aktivitetslogg)
    }

    override fun toString() =
        "${this.periode.start} - ${this.periode.endInclusive} (${this.tilstand::class.simpleName})"

    private fun finnArbeidsgiverperiode() = arbeidsgiver.arbeidsgiverperiode(periode)
    private fun finnArbeidsgiverperiodeHensyntarForkastede() =
        arbeidsgiver.arbeidsgiverperiodeInkludertForkastet(periode, sykdomstidslinje)

    fun skalFatteVedtak(): Boolean {
        if (!Toggle.FatteVedtakPåTidligereBeregnetPerioder.enabled) return forventerInntekt()
        return behandlinger.harVærtBeregnet() || forventerInntekt()
    }

    fun forventerInntekt(): Boolean {
        return Arbeidsgiverperiode.forventerInntekt(finnArbeidsgiverperiode(), periode)
    }

    fun trengerGodkjenning(aktivitetslogg: IAktivitetslogg) {
        behandlinger.godkjenning(aktivitetslogg, utkastTilVedtakBuilder())
    }

    private fun utkastTilVedtakBuilder(): UtkastTilVedtakBuilder {
        val builder = UtkastTilVedtakBuilder(
            arbeidsgiver = arbeidsgiver.organisasjonsnummer,
            vedtaksperiodeId = id,
            kanForkastes = arbeidsgiver.kanForkastes(this, Aktivitetslogg()),
            erForlengelse = erForlengelse(),
            harPeriodeRettFør = arbeidsgiver.finnVedtaksperiodeRettFør(this) != null
        )
        person.vedtaksperioder(MED_SKJÆRINGSTIDSPUNKT(skjæringstidspunkt))
            .associate { it.id to it.behandlinger }
            .berik(builder)

        return builder
    }

    internal fun gjenopptaBehandling(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(arbeidsgiver)
        registrerKontekst(aktivitetslogg)
        aktivitetslogg.info("Forsøker å gjenoppta $this")
        tilstand.gjenopptaBehandling(this, hendelse, aktivitetslogg)
    }

    internal fun igangsettOverstyring(revurdering: Revurderingseventyr, aktivitetslogg: IAktivitetslogg) {
        if (revurdering.ikkeRelevant(periode)) return
        registrerKontekst(aktivitetslogg)
        tilstand.igangsettOverstyring(this, revurdering, aktivitetslogg)
        tilstand.arbeidsgiveropplysningerStrategi.lagreGjenbrukbareOpplysninger(this, aktivitetslogg)
    }

    internal fun inngåIRevurderingseventyret(
        vedtaksperioder: MutableList<PersonObserver.OverstyringIgangsatt.VedtaksperiodeData>,
        typeEndring: String
    ) {
        vedtaksperioder.add(
            PersonObserver.OverstyringIgangsatt.VedtaksperiodeData(
                orgnummer = arbeidsgiver.organisasjonsnummer,
                vedtaksperiodeId = id,
                skjæringstidspunkt = skjæringstidspunkt,
                periode = periode,
                typeEndring = typeEndring
            )
        )
    }

    internal fun håndtertInntektPåSkjæringstidspunktet(
        skjæringstidspunkt: LocalDate,
        inntektsmelding: Inntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (skjæringstidspunkt != this.skjæringstidspunkt) return
        if (!skalFatteVedtak()) return
        registrerKontekst(aktivitetslogg)
        tilstand.håndtertInntektPåSkjæringstidspunktet(this, inntektsmelding, aktivitetslogg)
    }

    fun vedtaksperiodeVenter(venterPå: Vedtaksperiode): VedtaksperiodeVenter? {
        val venteårsak = venterPå.venteårsak() ?: return null
        val builder = VedtaksperiodeVenter.Builder()
        builder.venterPå(
            venterPå.id,
            venterPå.skjæringstidspunkt,
            venterPå.arbeidsgiver.organisasjonsnummer,
            venteårsak
        )
        builder.venter(
            vedtaksperiodeId = id,
            skjæringstidspunkt = skjæringstidspunkt,
            orgnummer = arbeidsgiver.organisasjonsnummer,
            ventetSiden = oppdatert,
            venterTil = venterTil(venterPå)
        )
        behandlinger.behandlingVenter(builder)
        builder.hendelseIder(hendelseIder)
        return builder.build()
    }

    private fun venterTil(venterPå: Vedtaksperiode) =
        if (id == venterPå.id) makstid()
        else minOf(makstid(), venterPå.makstid())

    fun venteårsak() = tilstand.venteårsak(this)
    private fun makstid(tilstandsendringstidspunkt: LocalDateTime = oppdatert) =
        tilstand.makstid(this, tilstandsendringstidspunkt)

    fun slutterEtter(dato: LocalDate) = periode.slutterEtter(dato)
    private fun aktivitetsloggkopi(aktivitetslogg: IAktivitetslogg) =
        aktivitetslogg.barn().also { kopi ->
            this.registrerKontekst(kopi)
        }

    fun oppdaterHistorikk(
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        val vilkårsgrunnlag = requireNotNull(vilkårsgrunnlag)
        aktivitetslogg.kontekst(vilkårsgrunnlag)
        vilkårsgrunnlag.valider(aktivitetslogg, arbeidsgiver.organisasjonsnummer)
        infotrygdhistorikk.valider(aktivitetslogg, periode, skjæringstidspunkt, arbeidsgiver.organisasjonsnummer)
        ytelser.oppdaterHistorikk(
            aktivitetslogg,
            periode,
            skjæringstidspunkt,
            person.nåværendeVedtaksperioder(OVERLAPPENDE_ELLER_SENERE_MED_SAMME_SKJÆRINGSTIDSPUNKT(this))
                .firstOrNull()?.periode
        ) {
            oppdaterHistorikk(
                ytelser.avgrensTil(periode),
                aktivitetslogg,
                validering = {}
            )
        }
    }

    private fun lagNyUtbetaling(
        arbeidsgiverSomBeregner: Arbeidsgiver,
        aktivitetslogg: IAktivitetslogg,
        maksdatoresultat: Maksdatoresultat,
        utbetalingstidslinje: Utbetalingstidslinje,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
    ) {
        behandlinger.nyUtbetaling(
            this.id,
            this.arbeidsgiver,
            grunnlagsdata,
            aktivitetslogg,
            maksdatoresultat,
            utbetalingstidslinje
        )
        val subsumsjonen = Utbetalingstidslinjesubsumsjon(this.jurist, this.sykdomstidslinje, utbetalingstidslinje)
        subsumsjonen.subsummer(periode, person.regler)
        loggDersomViTrekkerTilbakePengerPåAnnenArbeidsgiver(arbeidsgiverSomBeregner, aktivitetslogg)
    }

    private fun loggDersomViTrekkerTilbakePengerPåAnnenArbeidsgiver(
        arbeidsgiverSomBeregner: Arbeidsgiver,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (!behandlinger.trekkerTilbakePenger()) return
        if (this.arbeidsgiver === arbeidsgiverSomBeregner && !person.blitt6GBegrensetSidenSist(skjæringstidspunkt)) return
        aktivitetslogg.info("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere")
    }

    private fun perioderDetSkalBeregnesUtbetalingFor(): List<Vedtaksperiode> {
        // lag utbetaling for seg selv + andre overlappende perioder hos andre arbeidsgivere (som ikke er utbetalt/avsluttet allerede)
        return person.nåværendeVedtaksperioder { it.erKandidatForUtbetaling(this, this.skjæringstidspunkt) }
            .filter { it.behandlinger.klarForUtbetaling() }
    }

    private fun perioderSomMåHensyntasVedBeregning(): List<Vedtaksperiode> {
        val skjæringstidspunkt = this.skjæringstidspunkt
        return person.vedtaksperioder(MED_SKJÆRINGSTIDSPUNKT(skjæringstidspunkt))
            .filter { it !== this }
            .fold(listOf(this)) { utbetalingsperioder, vedtaksperiode ->
                if (utbetalingsperioder.any { vedtaksperiode.periode.overlapperMed(it.periode) }) utbetalingsperioder + vedtaksperiode
                else utbetalingsperioder
            }
    }

    private fun erKandidatForUtbetaling(periodeSomBeregner: Vedtaksperiode, skjæringstidspunktet: LocalDate): Boolean {
        if (this === periodeSomBeregner) return true
        if (!skalFatteVedtak()) return false
        return this.periode.overlapperMed(periodeSomBeregner.periode) && skjæringstidspunktet == this.skjæringstidspunkt && !this.tilstand.erFerdigBehandlet
    }

    private fun førstePeriodeAnnenArbeidsgiverSomTrengerInntekt(): Vedtaksperiode? {
        // trenger ikke inntekt for vilkårsprøving om vi har vilkårsprøvd før
        if (vilkårsgrunnlag != null) return null
        return person.vedtaksperioder {
            it.arbeidsgiver.organisasjonsnummer != arbeidsgiver.organisasjonsnummer &&
                it.skjæringstidspunkt == skjæringstidspunkt &&
                it.skalFatteVedtak() &&
                !it.arbeidsgiver.kanBeregneSykepengegrunnlag(skjæringstidspunkt)
        }.minOrNull()
    }

    private fun førstePeriodeAnnenArbeidsgiverSomTrengerRefusjonsopplysninger(): Vedtaksperiode? {
        val bereningsperiode = perioderSomMåHensyntasVedBeregning().periode()
        return person.vedtaksperioder {
            it.arbeidsgiver.organisasjonsnummer != arbeidsgiver.organisasjonsnummer &&
                it.skjæringstidspunkt == skjæringstidspunkt &&
                it.periode.overlapperMed(bereningsperiode) &&
                it.måInnhenteInntektEllerRefusjon(Aktivitetslogg())
        }.minOrNull()
    }

    fun førstePeriodeAnnenArbeidsgiverSomTrengerInntektsmelding() =
        førstePeriodeAnnenArbeidsgiverSomTrengerInntekt()
            ?: førstePeriodeAnnenArbeidsgiverSomTrengerRefusjonsopplysninger()

    private fun utbetalingstidslinje() = behandlinger.utbetalingstidslinje()
    fun defaultinntektForAUU(): ArbeidsgiverFaktaavklartInntekt {
        return ArbeidsgiverFaktaavklartInntekt(
            skjæringstidspunkt = skjæringstidspunkt,
            `6G` = Grunnbeløp.`6G`.beløp(skjæringstidspunkt),
            fastsattÅrsinntekt = Inntekt.INGEN,
            gjelder = skjæringstidspunkt til LocalDate.MAX,
            refusjonsopplysninger = Refusjonsopplysninger()
        )
    }

    fun beregnUtbetalinger(aktivitetslogg: IAktivitetslogg): Maksdatoresultat {
        val perioderDetSkalBeregnesUtbetalingFor = perioderDetSkalBeregnesUtbetalingFor()

        check(perioderDetSkalBeregnesUtbetalingFor.all { it.skjæringstidspunkt == this.skjæringstidspunkt }) {
            "ugyldig situasjon: skal beregne utbetaling for vedtaksperioder med ulike skjæringstidspunkter"
        }
        val grunnlagsdata = checkNotNull(vilkårsgrunnlag) {
            "krever vilkårsgrunnlag for ${skjæringstidspunkt}, men har ikke. Lages det utbetaling for en periode som ikke skal lage utbetaling?"
        }

        val (maksdatofilter, beregnetTidslinjePerArbeidsgiver) = beregnUtbetalingstidslinjeForOverlappendeVedtaksperioder(
            aktivitetslogg,
            grunnlagsdata
        )
        perioderDetSkalBeregnesUtbetalingFor.forEach { other ->
            val utbetalingstidslinje = beregnetTidslinjePerArbeidsgiver.getValue(other.arbeidsgiver.organisasjonsnummer)
            val maksdatoresultat = maksdatofilter.maksdatoresultatForVedtaksperiode(other.periode, other.jurist)
            other.lagNyUtbetaling(
                this.arbeidsgiver,
                other.aktivitetsloggkopi(aktivitetslogg),
                maksdatoresultat,
                utbetalingstidslinje,
                grunnlagsdata
            )
        }
        return maksdatofilter.maksdatoresultatForVedtaksperiode(periode, EmptyLog)
    }

    private fun beregnUtbetalingstidslinjeForOverlappendeVedtaksperioder(
        aktivitetslogg: IAktivitetslogg,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
    ): Pair<MaksimumSykepengedagerfilter, Map<String, Utbetalingstidslinje>> {
        val uberegnetTidslinjePerArbeidsgiver = utbetalingstidslinjePerArbeidsgiver(grunnlagsdata)
        return filtrerUtbetalingstidslinjer(aktivitetslogg, uberegnetTidslinjePerArbeidsgiver, grunnlagsdata)
    }

    private fun utbetalingstidslinjePerArbeidsgiver(grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement): Map<String, Utbetalingstidslinje> {
        val perioderSomMåHensyntasVedBeregning =
            perioderSomMåHensyntasVedBeregning().groupBy { it.arbeidsgiver.organisasjonsnummer }

        val faktaavklarteInntekter = grunnlagsdata.faktaavklarteInntekter()
        val utbetalingstidslinjer = perioderSomMåHensyntasVedBeregning.mapValues { (arbeidsgiver, vedtaksperioder) ->
            val inntektForArbeidsgiver = faktaavklarteInntekter.forArbeidsgiver(arbeidsgiver)
            vedtaksperioder.map { it.tilstand.lagUtbetalingstidslinje(it, inntektForArbeidsgiver) }
        }
        // nå vi må lage en ghost-tidslinje per arbeidsgiver for de som eksisterer i sykepengegrunnlaget.
        // resultatet er én utbetalingstidslinje per arbeidsgiver som garantert dekker perioden ${vedtaksperiode.periode}, dog kan
        // andre arbeidsgivere dekke litt før/litt etter, avhengig av perioden til vedtaksperiodene som overlapper
        return faktaavklarteInntekter.medGhostOgNyeInntekterUnderveis(utbetalingstidslinjer)
    }

    private fun filtrerUtbetalingstidslinjer(
        aktivitetslogg: IAktivitetslogg,
        uberegnetTidslinjePerArbeidsgiver: Map<String, Utbetalingstidslinje>,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
    ): Pair<MaksimumSykepengedagerfilter, Map<String, Utbetalingstidslinje>> {
        // grunnlaget for maksdatoberegning er alt som har skjedd før, frem til og med vedtaksperioden som
        // beregnes
        val historisktidslinjePerArbeidsgiver = person.vedtaksperioder { it.periode.endInclusive < periode.start }
            .groupBy { it.arbeidsgiver.organisasjonsnummer }
            .mapValues {
                it.value.map { vedtaksperiode -> vedtaksperiode.utbetalingstidslinje() }
                    .reduce(Utbetalingstidslinje::plus)
            }

        val historisktidslinje = historisktidslinjePerArbeidsgiver.values
            .fold(person.infotrygdhistorikk.utbetalingstidslinje(), Utbetalingstidslinje::plus)

        val maksdatofilter = MaksimumSykepengedagerfilter(person.alder, person.regler, historisktidslinje)
        val filtere = listOf(
            Sykdomsgradfilter(person.minimumSykdomsgradsvurdering),
            AvvisDagerEtterDødsdatofilter(person.alder),
            AvvisInngangsvilkårfilter(grunnlagsdata),
            maksdatofilter,
            MaksimumUtbetalingFilter()
        )

        val kjørFilter = fun(
            tidslinjer: Map<String, Utbetalingstidslinje>,
            filter: UtbetalingstidslinjerFilter
        ): Map<String, Utbetalingstidslinje> {
            val input = tidslinjer.entries.map { (key, value) -> key to value }
            val result = filter.filter(input.map { (_, tidslinje) -> tidslinje }, periode, aktivitetslogg, jurist)
            return input.zip(result) { (arbeidsgiver, _), utbetalingstidslinje ->
                arbeidsgiver to utbetalingstidslinje
            }.toMap()
        }
        val beregnetTidslinjePerArbeidsgiver = filtere.fold(uberegnetTidslinjePerArbeidsgiver) { tidslinjer, filter ->
            kjørFilter(tidslinjer, filter)
        }

        return maksdatofilter to beregnetTidslinjePerArbeidsgiver.mapValues { (arbeidsgiver, resultat) ->
            listOfNotNull(historisktidslinjePerArbeidsgiver[arbeidsgiver], resultat).reduce(Utbetalingstidslinje::plus)
        }
    }

    fun håndterOverstyringIgangsattRevurdering(
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        revurdering.inngåSomRevurdering(this, aktivitetslogg, periode)
        behandlinger.sikreNyBehandling(
            arbeidsgiver,
            revurdering.hendelse,
            person.beregnSkjæringstidspunkt(),
            arbeidsgiver.beregnArbeidsgiverperiode(jurist)
        )
        tilstand(aktivitetslogg, AvventerRevurdering)
    }

    fun håndterOverstyringIgangsattFørstegangsvurdering(
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        revurdering.inngåSomEndring(this, aktivitetslogg, periode)
        behandlinger.forkastUtbetaling(aktivitetslogg)
        if (måInnhenteInntektEllerRefusjon(aktivitetslogg)) return tilstand(aktivitetslogg, AvventerInntektsmelding)
        tilstand(aktivitetslogg, AvventerBlokkerendePeriode)
    }

    fun periodeRettFørHarFåttInntektsmelding(): Boolean {
        val rettFør = arbeidsgiver.finnVedtaksperiodeRettFør(this) ?: return false
        if (rettFør.tilstand in setOf(
                AvsluttetUtenUtbetaling,
                AvventerInfotrygdHistorikk,
                AvventerInntektsmelding
            )
        ) return false
        // auu-er vil kunne ligge i Avventer blokkerende periode
        if (rettFør.tilstand == AvventerBlokkerendePeriode && !rettFør.skalFatteVedtak()) return false
        if (rettFør.skjæringstidspunkt != this.skjæringstidspunkt) return false
        return true
    }

    private fun prioritertNabolag(): List<Vedtaksperiode> {
        val (nabolagFør, nabolagEtter) = this.arbeidsgiver.finnSammenhengendeVedtaksperioder(this)
            .partition { it.periode.endInclusive < this.periode.start }
        // Vi prioriterer refusjonsopplysninger fra perioder før oss før vi sjekker forlengelsene
        // Når vi ser på periodene før oss starter vi med den nærmeste
        return (nabolagFør.asReversed() + nabolagEtter)
    }

    fun videreførEksisterendeRefusjonsopplysninger(
        hendelse: Hendelse? = null,
        aktivitetslogg: IAktivitetslogg
    ) {
        if (refusjonstidslinje.isNotEmpty()) return
        val refusjonstidslinjeFraNabolaget =
            prioritertNabolag().firstOrNull { it.refusjonstidslinje.isNotEmpty() }?.let { nabo ->
                aktivitetslogg.info("Fant refusjonsopplysninger for $periode hos nabo-vedtaksperiode ${nabo.periode} (${nabo.id})")
                nabo.refusjonstidslinje
            } ?: Beløpstidslinje()
        val refusjonstidslinjeFraArbeidsgiver =
            arbeidsgiver.refusjonstidslinje(this).takeUnless { it.isEmpty() }?.also { ubrukte ->
                aktivitetslogg.info("Fant ubrukte refusjonsopplysninger for $periode fra kildene ${ubrukte.unikeKilder.joinToString()}")
            } ?: Beløpstidslinje()
        val benyttetRefusjonstidslinje =
            (refusjonstidslinjeFraArbeidsgiver + refusjonstidslinjeFraNabolaget).fyll(periode)
        if (benyttetRefusjonstidslinje.isEmpty()) return
        this.behandlinger.håndterRefusjonstidslinje(
            arbeidsgiver,
            hendelse,
            aktivitetslogg,
            person.beregnSkjæringstidspunkt(),
            arbeidsgiver.beregnArbeidsgiverperiode(jurist),
            benyttetRefusjonstidslinje
        )
    }

    internal fun hensyntattUbrukteRefusjonsopplysninger(ubrukteRefusjonsopplysninger: Refusjonsservitør): Beløpstidslinje {
        val menyBakHalen = ubrukteRefusjonsopplysninger.dessertmeny(startdatoPåSammenhengendeVedtaksperioder, periode)
            .fraOgMed(periode.endInclusive.nesteDag)
        return refusjonstidslinje + menyBakHalen
    }

    internal sealed class ArbeidsgiveropplysningerStrategi {
        abstract fun harInntektOgRefusjon(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            aktivitetslogg: IAktivitetslogg
        ): Boolean

        abstract fun harRefusjonsopplysninger(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            refusjonsopplysninger: Refusjonsopplysninger,
            aktivitetslogg: IAktivitetslogg
        ): Boolean

        abstract fun lagreGjenbrukbareOpplysninger(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg)
        protected fun harEksisterendeInntektOgRefusjon(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            aktivitetslogg: IAktivitetslogg
        ) =
            harEksisterendeInntekt(vedtaksperiode) && harRefusjonsopplysninger(
                vedtaksperiode,
                arbeidsgiverperiode,
                eksisterendeRefusjonsopplysninger(vedtaksperiode),
                aktivitetslogg
            )

        // Inntekt vi allerede har i vilkårsgrunnlag/inntektshistorikken på arbeidsgiver
        private fun harEksisterendeInntekt(vedtaksperiode: Vedtaksperiode): Boolean {
            // inntekt kreves så lenge det ikke finnes et vilkårsgrunnlag.
            // hvis det finnes et vilkårsgrunnlag så antas det at inntekten er representert der (vil vi slå ut på tilkommen inntekt-error senere hvis ikke)
            val vilkårsgrunnlag = vedtaksperiode.vilkårsgrunnlag
            return vilkårsgrunnlag != null || vedtaksperiode.arbeidsgiver.kanBeregneSykepengegrunnlag(vedtaksperiode.skjæringstidspunkt)
        }

        // Refusjonsopplysningene vi allerede har i vilkårsgrunnlag/ i refusjonshistorikken på arbeidsgiver
        private fun eksisterendeRefusjonsopplysninger(vedtaksperiode: Vedtaksperiode) =
            when (val vilkårsgrunnlag = vedtaksperiode.vilkårsgrunnlag) {
                null -> vedtaksperiode.arbeidsgiver.refusjonsopplysninger(vedtaksperiode.skjæringstidspunkt)
                else -> vilkårsgrunnlag.refusjonsopplysninger(vedtaksperiode.arbeidsgiver.organisasjonsnummer)
            }
    }

    data object FørInntektsmelding : ArbeidsgiveropplysningerStrategi() {
        override fun harInntektOgRefusjon(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            aktivitetslogg: IAktivitetslogg
        ) =
            harEksisterendeInntektOgRefusjon(vedtaksperiode, arbeidsgiverperiode, aktivitetslogg)

        override fun harRefusjonsopplysninger(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            refusjonsopplysninger: Refusjonsopplysninger,
            aktivitetslogg: IAktivitetslogg
        ) =
            Arbeidsgiverperiode.harNødvendigeRefusjonsopplysninger(
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.periode,
                refusjonsopplysninger,
                arbeidsgiverperiode,
                aktivitetslogg,
                vedtaksperiode.arbeidsgiver.organisasjonsnummer
            )

        override fun lagreGjenbrukbareOpplysninger(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) { /* Før vi har fått inntektmelding kan vi ikke lagre gjenbrukbare opplysninger 🙅‍ */
        }
    }

    private data object EtterInntektsmelding : ArbeidsgiveropplysningerStrategi() {
        override fun harInntektOgRefusjon(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            aktivitetslogg: IAktivitetslogg
        ) =
            harEksisterendeInntektOgRefusjon(
                vedtaksperiode,
                arbeidsgiverperiode,
                aktivitetslogg
            ) || vedtaksperiode.behandlinger.harGjenbrukbareOpplysninger(vedtaksperiode.arbeidsgiver.organisasjonsnummer)

        override fun harRefusjonsopplysninger(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgiverperiode: Arbeidsgiverperiode,
            refusjonsopplysninger: Refusjonsopplysninger,
            aktivitetslogg: IAktivitetslogg
        ) =
            Arbeidsgiverperiode.harNødvendigeRefusjonsopplysningerEtterInntektsmelding(
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.periode,
                refusjonsopplysninger,
                arbeidsgiverperiode,
                aktivitetslogg,
                vedtaksperiode.arbeidsgiver.organisasjonsnummer
            )

        override fun lagreGjenbrukbareOpplysninger(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
            val arbeidsgiverperiode = vedtaksperiode.finnArbeidsgiverperiode() ?: return
            if (vedtaksperiode.tilstand == AvventerBlokkerendePeriode && !arbeidsgiverperiode.forventerInntekt(
                    vedtaksperiode.periode
                )
            ) return // En periode i AvventerBlokkerendePeriode som skal tilbake AvsluttetUtenUtbetaling trenger uansett ikke inntekt og/eller refusjon
            if (harEksisterendeInntektOgRefusjon(
                    vedtaksperiode,
                    arbeidsgiverperiode,
                    aktivitetslogg
                )
            ) return // Trenger ikke lagre gjenbrukbare inntekter om vi har det vi trenger allerede
            vedtaksperiode.behandlinger.lagreGjenbrukbareOpplysninger(
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.arbeidsgiver.organisasjonsnummer,
                vedtaksperiode.arbeidsgiver,
                aktivitetslogg
            ) // Ikke 100% at dette lagrer noe. F.eks. revurderinger med Infotryfd-vilkårsgrunnlag har ikke noe å gjenbruke
        }
    }

    // Gang of four State pattern
    internal sealed interface Vedtaksperiodetilstand : Aktivitetskontekst {
        val type: TilstandType
        val erFerdigBehandlet: Boolean get() = false

        val arbeidsgiveropplysningerStrategi: ArbeidsgiveropplysningerStrategi get() = EtterInntektsmelding
        fun aktivitetsloggForRevurdering(aktivitetslogg: IAktivitetslogg): IAktivitetslogg {
            return FunksjonelleFeilTilVarsler(aktivitetslogg)
        }

        fun håndterFørstegangsbehandling(
            aktivitetslogg: IAktivitetslogg,
            vedtaksperiode: Vedtaksperiode
        ): IAktivitetslogg {
            if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, Aktivitetslogg())) return aktivitetslogg
            // Om førstegangsbehandling ikke kan forkastes (typisk Out of Order/ omgjøring av AUU) så håndteres det som om det er en revurdering
            return aktivitetsloggForRevurdering(aktivitetslogg)
        }

        fun lagUtbetalingstidslinje(
            vedtaksperiode: Vedtaksperiode,
            inntekt: ArbeidsgiverFaktaavklartInntekt?
        ): Utbetalingstidslinje {
            inntekt ?: error(
                "Det er en vedtaksperiode som ikke inngår i SP: ${vedtaksperiode.arbeidsgiver.organisasjonsnummer} - $vedtaksperiode.id - $vedtaksperiode.periode." +
                    "Burde ikke arbeidsgiveren være kjent i sykepengegrunnlaget, enten i form av en skatteinntekt eller en tilkommet?"
            )
            return vedtaksperiode.behandlinger.lagUtbetalingstidslinje(
                inntekt,
                vedtaksperiode.jurist,
                vedtaksperiode.refusjonstidslinje
            )
        }

        fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {}
        fun makstid(vedtaksperiode: Vedtaksperiode, tilstandsendringstidspunkt: LocalDateTime): LocalDateTime =
            LocalDateTime.MAX

        fun håndterMakstid(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.funksjonellFeil(RV_VT_1)
            vedtaksperiode.forkast(påminnelse, aktivitetslogg)
        }

        override fun toSpesifikkKontekst(): SpesifikkKontekst {
            return SpesifikkKontekst(
                "Tilstand", mapOf(
                "tilstand" to type.name
            )
            )
        }

        // Gitt at du er nestemann som skal behandles - hva venter du på?
        fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak?

        // venter du på noe?
        fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode): VedtaksperiodeVenter? = null
        fun håndter(
            vedtaksperiode: Vedtaksperiode,
            søknad: Søknad,
            aktivitetslogg: IAktivitetslogg,
            arbeidsgivere: List<Arbeidsgiver>,
            infotrygdhistorikk: Infotrygdhistorikk
        )

        fun replayUtført(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {}
        fun inntektsmeldingFerdigbehandlet(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
        }

        fun skalHåndtereDager(
            vedtaksperiode: Vedtaksperiode,
            dager: DagerFraInntektsmelding,
            aktivitetslogg: IAktivitetslogg
        ) =
            dager.skalHåndteresAv(vedtaksperiode.periode)

        fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding, aktivitetslogg: IAktivitetslogg) {
            vedtaksperiode.håndterKorrigerendeInntektsmelding(dager, aktivitetslogg)
        }

        fun håndtertInntektPåSkjæringstidspunktet(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Inntektsmelding,
            aktivitetslogg: IAktivitetslogg
        ) {
        }

        fun håndter(
            vedtaksperiode: Vedtaksperiode,
            sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Forventet ikke sykepengegrunnlag for arbeidsgiver i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.info("Forventet ikke vilkårsgrunnlag i %s".format(type.name))
        }

        fun håndter(
            vedtaksperiode: Vedtaksperiode,
            anmodningOmForkasting: AnmodningOmForkasting,
            aktivitetslogg: IAktivitetslogg
        ) {
            val kanForkastes = vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, aktivitetslogg)
            if (kanForkastes) return aktivitetslogg.info("Avslår anmodning om forkasting i ${type.name} (kan forkastes)")
            aktivitetslogg.info("Avslår anmodning om forkasting i ${type.name} (kan ikke forkastes)")
        }

        fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
        }

        fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            ytelser: Ytelser,
            aktivitetslogg: IAktivitetslogg,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            aktivitetslogg.info("Forventet ikke ytelsehistorikk i %s".format(type.name))
        }

        fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            utbetalingsavgjørelse: Behandlingsavgjørelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Forventet ikke utbetalingsavgjørelse i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {}
        fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.info("Forventet ikke simulering i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: UtbetalingHendelse, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.info("Forventet ikke utbetaling i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.info("Forventet ikke overstyring fra saksbehandler i %s".format(type.name))
        }

        fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: OverstyrArbeidsgiveropplysninger,
            aktivitetslogg: IAktivitetslogg
        ) {
        }

        fun nyAnnullering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {}
        fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Tidligere periode ferdigbehandlet, men gjør ingen tilstandsendring.")
        }

        fun igangsettOverstyring(
            vedtaksperiode: Vedtaksperiode,
            revurdering: Revurderingseventyr,
            aktivitetslogg: IAktivitetslogg
        )

        fun beregnUtbetalinger(vedtaksperiode: Vedtaksperiode, ytelser: Ytelser, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.info("Etter å ha oppdatert sykdomshistorikken fra ytelser står vi nå i ${type.name}. Avventer beregning av utbetalinger.")
        }

        fun leaving(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {}
        fun Vedtaksperiodetilstand.endreTilstand(
            vedtaksperiode: Vedtaksperiode,
            event: IAktivitetslogg,
            nyTilstand: Vedtaksperiodetilstand,
            block: () -> Unit = {}
        ) {
            vedtaksperiode.tilstand(event, nyTilstand, block)
        }
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val datoformat = DateTimeFormatter.ofPattern("dd-MM-yyyy")

        // dersom "ny" slutter på en fredag, så starter ikke oppholdstelling før påfølgende mandag.
        // det kan derfor være mer enn 16 dager avstand mellom periodene, og arbeidsgiverperioden kan være den samme
        // Derfor bruker vi tallet 18 fremfor kanskje det forventende 16…
        internal const val MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD = 18L
        internal fun List<Vedtaksperiode>.egenmeldingsperioder(): List<Periode> = flatMap { it.egenmeldingsperioder }
        internal fun List<Vedtaksperiode>.arbeidsgiverperioder() = map { it.behandlinger.arbeidsgiverperiode() }
        internal fun List<Vedtaksperiode>.refusjonstidslinje() =
            fold(Beløpstidslinje()) { beløpstidslinje, vedtaksperiode ->
                beløpstidslinje + vedtaksperiode.refusjonstidslinje
            }

        internal fun List<Vedtaksperiode>.finn(vedtaksperiodeId: UUID): Vedtaksperiode? =
            firstOrNull { it.id == vedtaksperiodeId }

        internal fun List<Vedtaksperiode>.startdatoerPåSammenhengendeVedtaksperioder(): Set<LocalDate> {
            val startdatoer = mutableMapOf<UUID, LocalDate>()

            this.forEach { vedtaksperiode ->
                if (vedtaksperiode.id in startdatoer) return@forEach
                val sammenhendeVedtaksperioder =
                    vedtaksperiode.arbeidsgiver.finnSammenhengendeVedtaksperioder(vedtaksperiode)
                val startdatoPåSammenhengendeVedtaksperioder = sammenhendeVedtaksperioder.periode().start
                startdatoer.putAll(sammenhendeVedtaksperioder.associate { it.id to startdatoPåSammenhengendeVedtaksperioder })
            }

            return startdatoer.values.toSet()
        }

        internal fun List<Vedtaksperiode>.refusjonseventyr(hendelse: Hendelse) = firstOrNull {
            it.behandlinger.håndterer(Dokumentsporing.inntektsmeldingRefusjon(hendelse.metadata.meldingsreferanseId))
        }?.let { Revurderingseventyr.refusjonsopplysninger(hendelse, it.skjæringstidspunkt, it.periode) }

        internal fun List<Vedtaksperiode>.migrerRefusjonsopplysningerPåBehandlinger(
            aktivitetslogg: IAktivitetslogg,
            orgnummer: String
        ) {
            forEach { it.behandlinger.migrerRefusjonsopplysninger(aktivitetslogg, orgnummer) }
        }

        // Fredet funksjonsnavn
        internal val TIDLIGERE_OG_ETTERGØLGENDE = fun(segSelv: Vedtaksperiode): VedtaksperiodeFilter {
            val medSammeAGP = MED_SAMME_AGP_OG_SKJÆRINGSTIDSPUNKT(segSelv)
            return fun(other: Vedtaksperiode): Boolean {
                if (other.periode.start >= segSelv.periode.start) return true // Forkaster nyere perioder på tvers av arbeidsgivere
                return medSammeAGP(other)
            }
        }
        internal val MED_SAMME_AGP_OG_SKJÆRINGSTIDSPUNKT = fun(segSelv: Vedtaksperiode): VedtaksperiodeFilter {
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            val arbeidsgiverperiode = segSelv.finnArbeidsgiverperiode()
            return fun(other: Vedtaksperiode): Boolean {
                if (arbeidsgiverperiode != null && other.arbeidsgiver === segSelv.arbeidsgiver && other.periode in arbeidsgiverperiode) return true // Forkaster samme arbeidsgiverperiode (kun for samme arbeidsgiver)
                return other.skjæringstidspunkt == skjæringstidspunkt // Forkaster alt med samme skjæringstidspunkt på tvers av arbeidsgivere
            }
        }

        internal val HAR_PÅGÅENDE_UTBETALINGER: VedtaksperiodeFilter = { it.behandlinger.utbetales() }

        private val HAR_AVVENTENDE_GODKJENNING: VedtaksperiodeFilter = {
            it.tilstand == AvventerGodkjenning || it.tilstand == AvventerGodkjenningRevurdering
        }

        private val IKKE_FERDIG_BEHANDLET: VedtaksperiodeFilter = { !it.tilstand.erFerdigBehandlet }

        internal val MED_SKJÆRINGSTIDSPUNKT = { skjæringstidspunkt: LocalDate ->
            { vedtaksperiode: Vedtaksperiode -> vedtaksperiode.skjæringstidspunkt == skjæringstidspunkt }
        }

        internal val SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG = { skjæringstidspunkt: LocalDate ->
            { vedtaksperiode: Vedtaksperiode ->
                MED_SKJÆRINGSTIDSPUNKT(skjæringstidspunkt)(vedtaksperiode) && vedtaksperiode.skalFatteVedtak()
            }
        }

        internal val NYERE_SKJÆRINGSTIDSPUNKT_MED_UTBETALING = { segSelv: Vedtaksperiode ->
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            { vedtaksperiode: Vedtaksperiode ->
                vedtaksperiode.behandlinger.erAvsluttet() && vedtaksperiode.skjæringstidspunkt > skjæringstidspunkt && vedtaksperiode.skjæringstidspunkt > segSelv.periode.endInclusive
            }
        }

        internal val NYERE_SKJÆRINGSTIDSPUNKT_UTEN_UTBETALING = { segSelv: Vedtaksperiode ->
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            { vedtaksperiode: Vedtaksperiode ->
                vedtaksperiode.tilstand == AvsluttetUtenUtbetaling && vedtaksperiode.skjæringstidspunkt > skjæringstidspunkt && vedtaksperiode.skjæringstidspunkt > segSelv.periode.endInclusive
            }
        }

        internal val AUU_SOM_VIL_UTBETALES: VedtaksperiodeFilter = {
            it.tilstand == AvsluttetUtenUtbetaling && it.skalFatteVedtak()
        }

        internal val OVERLAPPENDE_ELLER_SENERE_MED_SAMME_SKJÆRINGSTIDSPUNKT = { segSelv: Vedtaksperiode ->
            { vedtaksperiode: Vedtaksperiode ->
                vedtaksperiode !== segSelv && vedtaksperiode.skjæringstidspunkt == segSelv.skjæringstidspunkt && vedtaksperiode.periode.start >= segSelv.periode.start
            }
        }

        private fun sykmeldingsperioder(vedtaksperioder: List<Vedtaksperiode>): List<Periode> {
            return vedtaksperioder.map { it.sykmeldingsperiode }
        }

        private fun egenmeldingsperioder(vedtaksperioder: List<Vedtaksperiode>) =
            vedtaksperioder.flatMap { it.egenmeldingsperioder }

        internal fun List<Vedtaksperiode>.beregnSkjæringstidspunkter(
            beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
            beregnArbeidsgiverperiode: (Periode) -> List<Periode>
        ) {
            forEach { it.behandlinger.beregnSkjæringstidspunkt(beregnSkjæringstidspunkt, beregnArbeidsgiverperiode) }
        }

        internal fun List<Vedtaksperiode>.harIngenSporingTilInntektsmeldingISykefraværet(): Boolean {
            return all { !it.behandlinger.harHåndtertInntektTidligere() && !it.behandlinger.harHåndtertDagerTidligere() }
        }

        internal fun List<Vedtaksperiode>.aktiveSkjæringstidspunkter(): Set<LocalDate> {
            return map { it.skjæringstidspunkt }.toSet()
        }

        internal fun Iterable<Vedtaksperiode>.nåværendeVedtaksperiode(filter: VedtaksperiodeFilter) =
            firstOrNull(filter)

        private fun Vedtaksperiode.erTidligereEnn(other: Vedtaksperiode): Boolean =
            this <= other || this.skjæringstidspunkt < other.skjæringstidspunkt

        private fun Iterable<Vedtaksperiode>.førstePeriode(): Vedtaksperiode? {
            var minste: Vedtaksperiode? = null
            this
                .forEach { vedtaksperiode ->
                    minste = minste?.takeIf { it.erTidligereEnn(vedtaksperiode) } ?: vedtaksperiode
                }
            return minste
        }

        internal fun Iterable<Vedtaksperiode>.nestePeriodeSomSkalGjenopptas() =
            filter(IKKE_FERDIG_BEHANDLET).førstePeriode()

        internal fun List<Vedtaksperiode>.sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) {
            val nestePeriode = this
                .firstOrNull { it.skjæringstidspunkt > vedtaksperiode.skjæringstidspunkt && it.skalFatteVedtak() }
                ?.takeIf { it.tilstand == AvventerInntektsmelding }
                ?: return
            if (nestePeriode.sjekkTrengerArbeidsgiveropplysninger(aktivitetslogg)) {
                nestePeriode.sendTrengerArbeidsgiveropplysninger()
            }
        }

        internal fun Iterable<Vedtaksperiode>.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas: Vedtaksperiode) {
            check(this.filterNot { it == periodeSomSkalGjenopptas }.none(HAR_AVVENTENDE_GODKJENNING)) {
                "Ugyldig situasjon! Flere perioder til godkjenning samtidig"
            }
        }

        internal fun List<Vedtaksperiode>.venter(nestemann: Vedtaksperiode) =
            mapNotNull { vedtaksperiode -> vedtaksperiode.tilstand.venter(vedtaksperiode, nestemann) }

        internal fun List<Vedtaksperiode>.validerTilstand(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) =
            forEach { it.validerTilstand(hendelse, aktivitetslogg) }

        internal fun harNyereForkastetPeriode(
            forkastede: Iterable<Vedtaksperiode>,
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) =
            forkastede
                .filter { it.periode.start > vedtaksperiode.periode.endInclusive }
                .onEach {
                    val sammeArbeidsgiver =
                        it.arbeidsgiver.organisasjonsnummer == vedtaksperiode.arbeidsgiver.organisasjonsnummer
                    aktivitetslogg.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_31 else RV_SØ_32)
                    aktivitetslogg.info("Søknaden ${vedtaksperiode.periode} er før en forkastet vedtaksperiode ${it.id} (${it.periode})")
                }
                .isNotEmpty()

        internal fun harOverlappendeForkastetPeriode(
            forkastede: Iterable<Vedtaksperiode>,
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) =
            forkastede
                .filter { it.periode.overlapperMed(vedtaksperiode.periode()) }
                .onEach {
                    val delvisOverlappende =
                        !it.periode.inneholder(vedtaksperiode.periode) // hvorvidt vedtaksperioden strekker seg utenfor den forkastede
                    val sammeArbeidsgiver =
                        it.arbeidsgiver.organisasjonsnummer == vedtaksperiode.arbeidsgiver.organisasjonsnummer
                    aktivitetslogg.funksjonellFeil(
                        when {
                            delvisOverlappende && sammeArbeidsgiver -> RV_SØ_35
                            delvisOverlappende && !sammeArbeidsgiver -> RV_SØ_36
                            !delvisOverlappende && sammeArbeidsgiver -> RV_SØ_33
                            !delvisOverlappende && !sammeArbeidsgiver -> RV_SØ_34
                            else -> throw IllegalStateException("dette er ikke mulig med mindre noen har tullet til noe")
                        }
                    )
                    aktivitetslogg.info("Søknad ${vedtaksperiode.periode} overlapper med en forkastet vedtaksperiode ${it.id} (${it.periode})")
                }
                .isNotEmpty()

        internal fun harKortGapTilForkastet(
            forkastede: Iterable<Vedtaksperiode>,
            aktivitetslogg: IAktivitetslogg,
            vedtaksperiode: Vedtaksperiode
        ) =
            forkastede
                .filter { other -> vedtaksperiode.påvirkerArbeidsgiverperioden(other) }
                .onEach {
                    aktivitetslogg.funksjonellFeil(RV_SØ_28)
                    aktivitetslogg.info("Søknad har et gap som er kortere enn 20 dager til en forkastet vedtaksperiode ${it.id}, vedtaksperiode periode: ${it.periode}")
                }
                .isNotEmpty()

        internal fun forlengerForkastet(
            forkastede: List<Vedtaksperiode>,
            aktivitetslogg: IAktivitetslogg,
            vedtaksperiode: Vedtaksperiode
        ) =
            forkastede
                .filter { it.periode.erRettFør(vedtaksperiode.periode) }
                .onEach {
                    val sammeArbeidsgiver =
                        it.arbeidsgiver.organisasjonsnummer == vedtaksperiode.arbeidsgiver.organisasjonsnummer
                    aktivitetslogg.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_37 else RV_SØ_38)
                    aktivitetslogg.info("Søknad forlenger forkastet vedtaksperiode ${it.id}, vedtaksperiode periode: ${it.periode}")
                }
                .isNotEmpty()

        internal fun List<Vedtaksperiode>.påvirkerArbeidsgiverperiode(periode: Periode): Boolean {
            return any { vedtaksperiode ->
                val dagerMellom = periode.periodeMellom(vedtaksperiode.periode.start)?.count() ?: return@any false
                return dagerMellom < MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD
            }
        }

        internal fun List<Vedtaksperiode>.slåSammenForkastedeSykdomstidslinjer(sykdomstidslinje: Sykdomstidslinje): Sykdomstidslinje =
            map { it.sykdomstidslinje }.plusElement(sykdomstidslinje).slåSammenForkastedeSykdomstidslinjer()

        internal fun List<Vedtaksperiode>.inneholder(id: UUID) = any { id == it.id }
        internal fun List<Vedtaksperiode>.periode(): Periode {
            val fom = minOf { it.periode.start }
            val tom = maxOf { it.periode.endInclusive }
            return Periode(fom, tom)
        }

        internal fun gjenopprett(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            dto: VedtaksperiodeInnDto,
            subsumsjonslogg: Subsumsjonslogg,
            grunnlagsdata: Map<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>,
            utbetalinger: Map<UUID, Utbetaling>
        ): Vedtaksperiode {
            return Vedtaksperiode(
                person = person,
                arbeidsgiver = arbeidsgiver,
                id = dto.id,
                tilstand = when (dto.tilstand) {
                    VedtaksperiodetilstandDto.AVSLUTTET -> Avsluttet
                    VedtaksperiodetilstandDto.AVSLUTTET_UTEN_UTBETALING -> AvsluttetUtenUtbetaling
                    VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE -> AvventerBlokkerendePeriode
                    VedtaksperiodetilstandDto.AVVENTER_GODKJENNING -> AvventerGodkjenning
                    VedtaksperiodetilstandDto.AVVENTER_GODKJENNING_REVURDERING -> AvventerGodkjenningRevurdering
                    VedtaksperiodetilstandDto.AVVENTER_HISTORIKK -> AvventerHistorikk
                    VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING -> AvventerHistorikkRevurdering
                    VedtaksperiodetilstandDto.AVVENTER_INFOTRYGDHISTORIKK -> AvventerInfotrygdHistorikk
                    VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING -> AvventerInntektsmelding
                    VedtaksperiodetilstandDto.AVVENTER_REVURDERING -> AvventerRevurdering
                    VedtaksperiodetilstandDto.AVVENTER_SIMULERING -> AvventerSimulering
                    VedtaksperiodetilstandDto.AVVENTER_SIMULERING_REVURDERING -> AvventerSimuleringRevurdering
                    VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING -> AvventerVilkårsprøving
                    VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING_REVURDERING -> AvventerVilkårsprøvingRevurdering
                    VedtaksperiodetilstandDto.REVURDERING_FEILET -> RevurderingFeilet
                    VedtaksperiodetilstandDto.START -> Start
                    VedtaksperiodetilstandDto.TIL_INFOTRYGD -> TilInfotrygd
                    VedtaksperiodetilstandDto.TIL_UTBETALING -> TilUtbetaling
                },
                behandlinger = Behandlinger.gjenopprett(dto.behandlinger, grunnlagsdata, utbetalinger),
                egenmeldingsperioder = dto.egenmeldingsperioder.map { egenmeldingsperiode -> egenmeldingsperiode.fom til egenmeldingsperiode.tom },
                opprettet = dto.opprettet,
                oppdatert = dto.oppdatert,
                subsumsjonslogg = subsumsjonslogg
            )
        }
    }

    fun overlappendeInfotrygdperioder(
        result: PersonObserver.OverlappendeInfotrygdperioder,
        perioder: List<Infotrygdperiode>
    ): PersonObserver.OverlappendeInfotrygdperioder {
        val overlappende = perioder.filter { it.overlapperMed(this.periode) }
        if (overlappende.isEmpty()) return result
        return result.copy(
            overlappendeInfotrygdperioder = result.overlappendeInfotrygdperioder.plusElement(
                PersonObserver.OverlappendeInfotrygdperiodeEtterInfotrygdendring(
                    organisasjonsnummer = arbeidsgiver.organisasjonsnummer,
                    vedtaksperiodeId = this.id,
                    kanForkastes = arbeidsgiver.kanForkastes(this, Aktivitetslogg()),
                    vedtaksperiodeFom = this.periode.start,
                    vedtaksperiodeTom = this.periode.endInclusive,
                    vedtaksperiodetilstand = tilstand.type.name,
                    infotrygdperioder = overlappende.map {
                        when (it) {
                            is Friperiode -> PersonObserver.OverlappendeInfotrygdperiodeEtterInfotrygdendring.Infotrygdperiode(
                                fom = it.periode.start,
                                tom = it.periode.endInclusive,
                                type = "FRIPERIODE",
                                orgnummer = null
                            )

                            is ArbeidsgiverUtbetalingsperiode -> PersonObserver.OverlappendeInfotrygdperiodeEtterInfotrygdendring.Infotrygdperiode(
                                fom = it.periode.start,
                                tom = it.periode.endInclusive,
                                type = "ARBEIDSGIVERUTBETALING",
                                orgnummer = it.orgnr
                            )

                            is PersonUtbetalingsperiode -> PersonObserver.OverlappendeInfotrygdperiodeEtterInfotrygdendring.Infotrygdperiode(
                                fom = it.periode.start,
                                tom = it.periode.endInclusive,
                                type = "PERSONUTBETALING",
                                orgnummer = it.orgnr
                            )
                        }
                    }
                )
            ))
    }

    internal fun dto(nestemann: Vedtaksperiode?) = VedtaksperiodeUtDto(
        id = id,
        tilstand = when (tilstand) {
            Avsluttet -> VedtaksperiodetilstandDto.AVSLUTTET
            AvsluttetUtenUtbetaling -> VedtaksperiodetilstandDto.AVSLUTTET_UTEN_UTBETALING
            AvventerBlokkerendePeriode -> VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE
            AvventerGodkjenning -> VedtaksperiodetilstandDto.AVVENTER_GODKJENNING
            AvventerGodkjenningRevurdering -> VedtaksperiodetilstandDto.AVVENTER_GODKJENNING_REVURDERING
            AvventerHistorikk -> VedtaksperiodetilstandDto.AVVENTER_HISTORIKK
            AvventerHistorikkRevurdering -> VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING
            AvventerInfotrygdHistorikk -> VedtaksperiodetilstandDto.AVVENTER_INFOTRYGDHISTORIKK
            AvventerInntektsmelding -> VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING
            AvventerRevurdering -> VedtaksperiodetilstandDto.AVVENTER_REVURDERING
            AvventerSimulering -> VedtaksperiodetilstandDto.AVVENTER_SIMULERING
            AvventerSimuleringRevurdering -> VedtaksperiodetilstandDto.AVVENTER_SIMULERING_REVURDERING
            AvventerVilkårsprøving -> VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING
            AvventerVilkårsprøvingRevurdering -> VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING_REVURDERING
            RevurderingFeilet -> VedtaksperiodetilstandDto.REVURDERING_FEILET
            Start -> VedtaksperiodetilstandDto.START
            TilInfotrygd -> VedtaksperiodetilstandDto.TIL_INFOTRYGD
            TilUtbetaling -> VedtaksperiodetilstandDto.TIL_UTBETALING
        },
        skjæringstidspunkt = this.skjæringstidspunkt,
        fom = this.periode.start,
        tom = this.periode.endInclusive,
        sykmeldingFom = this.sykmeldingsperiode.start,
        sykmeldingTom = this.sykmeldingsperiode.endInclusive,
        behandlinger = behandlinger.dto(),
        venteårsak = LazyVedtaksperiodeVenterDto { nestemann?.let { tilstand.venter(this, it)?.dto() } },
        egenmeldingsperioder = egenmeldingsperioder.map { it.dto() },
        opprettet = opprettet,
        oppdatert = oppdatert
    )
}

internal typealias VedtaksperiodeFilter = (Vedtaksperiode) -> Boolean

internal data class VedtaksperiodeView(
    val id: UUID,
    val periode: Periode,
    val tilstand: TilstandType,
    val oppdatert: LocalDateTime,
    val skjæringstidspunkt: LocalDate,
    val egenmeldingsperioder: List<Periode>,
    val behandlinger: BehandlingerView
) {
    val sykdomstidslinje = behandlinger.behandlinger.last().endringer.last().sykdomstidslinje
    val refusjonstidslinje = behandlinger.behandlinger.last().endringer.last().refusjonstidslinje
}
