package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.Grunnbeløp
import no.nav.helse.Toggle
import no.nav.helse.dto.LazyVedtaksperiodeVenterDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.deserialisering.VedtaksperiodeInnDto
import no.nav.helse.dto.serialisering.VedtaksperiodeUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.etterlevelse.`fvl § 35 ledd 1`
import no.nav.helse.etterlevelse.`§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrInntektsgrunnlag
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.hendelser.Periode.Companion.lik
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Validation.Companion.validation
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.hendelser.inntektsmelding.DagerFraInntektsmelding
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.Behandlingsavgjørelse
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.person.Behandlinger.Companion.berik
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.BEREGNING
import no.nav.helse.person.Venteårsak.Hva.GODKJENNING
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.Venteårsak.Hva.INNTEKTSMELDING
import no.nav.helse.person.Venteårsak.Hva.SØKNAD
import no.nav.helse.person.Venteårsak.Hva.UTBETALING
import no.nav.helse.person.Venteårsak.Hvorfor.FLERE_SKJÆRINGSTIDSPUNKT
import no.nav.helse.person.Venteårsak.Hvorfor.OVERSTYRING_IGANGSATT
import no.nav.helse.person.Venteårsak.Hvorfor.SKJÆRINGSTIDSPUNKT_FLYTTET_REVURDERING
import no.nav.helse.person.Venteårsak.Hvorfor.VIL_OMGJØRES
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
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Mottatt søknad som delvis overlapper`
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.varsel
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_24
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_38
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_11
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OO_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RV_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_2
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
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_24
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
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje.Companion.slåSammenForkastedeSykdomstidslinjer
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.AndreYtelserPerioder
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverFaktaavklartInntekt
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.AvvisAndreYtelserFilter
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
import kotlin.collections.component1
import kotlin.collections.component2

internal class Vedtaksperiode private constructor(
    private val person: Person,
    private val arbeidsgiver: Arbeidsgiver,
    private val id: UUID,
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    private var tilstand: Vedtaksperiodetilstand,
    private val behandlinger: Behandlinger,
    private var egenmeldingsperioder: List<Periode>,
    private val opprettet: LocalDateTime,
    private var oppdatert: LocalDateTime = opprettet,
    private val subsumsjonslogg: Subsumsjonslogg
) : Aktivitetskontekst, Comparable<Vedtaksperiode>, BehandlingObserver {

    internal constructor(
        søknad: Søknad,
        person: Person,
        arbeidsgiver: Arbeidsgiver,
        aktørId: String,
        fødselsnummer: String,
        organisasjonsnummer: String,
        sykdomstidslinje: Sykdomstidslinje,
        dokumentsporing: Dokumentsporing,
        sykmeldingsperiode: Periode,
        subsumsjonslogg: Subsumsjonslogg
    ) : this(
        person = person,
        arbeidsgiver = arbeidsgiver,
        id = UUID.randomUUID(),
        aktørId = aktørId,
        fødselsnummer = fødselsnummer,
        organisasjonsnummer = organisasjonsnummer,
        tilstand = Start,
        behandlinger = Behandlinger(),
        egenmeldingsperioder = søknad.egenmeldingsperioder(),
        opprettet = LocalDateTime.now(),
        subsumsjonslogg = subsumsjonslogg
    ) {
        kontekst(søknad)
        val periode = checkNotNull(sykdomstidslinje.periode()) { "sykdomstidslinjen er tom" }
        person.vedtaksperiodeOpprettet(id, organisasjonsnummer, periode, periode.start, opprettet)
        behandlinger.initiellBehandling(sykmeldingsperiode, sykdomstidslinje, dokumentsporing, søknad)
    }

    private val sykmeldingsperiode get() = behandlinger.sykmeldingsperiode()
    private val periode get() = behandlinger.periode()
    internal val sykdomstidslinje get() = behandlinger.sykdomstidslinje()
    private val jurist get() = behandlinger.subsumsjonslogg(subsumsjonslogg, id, fødselsnummer, organisasjonsnummer)
    private val skjæringstidspunkt get() = behandlinger.skjæringstidspunkt()
    private val vilkårsgrunnlag get() = person.vilkårsgrunnlagFor(skjæringstidspunkt)
    private val hendelseIder get() = behandlinger.dokumentsporing()
    private val refusjonstidslinje get() = behandlinger.refusjonstidslinje()

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
        håndtering: (Hendelse) -> Unit
    ) {
        if (!hendelse.erRelevant(this.periode)) return hendelse.vurdertTilOgMed(periode.endInclusive)
        kontekst(hendelse)
        hendelse.leggTil(id, behandlinger)
        håndtering(hendelse)
        hendelse.vurdertTilOgMed(periode.endInclusive)
    }

    private fun validerTilstand(hendelse: Hendelse) {
        if (!tilstand.erFerdigBehandlet) return
        behandlinger.validerFerdigBehandlet(hendelse)
    }

    internal fun håndter(søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
        håndterSykdomstidslinjeHendelse(søknad) {
            søknadHåndtert(søknad)
            tilstand.håndter(this, søknad, arbeidsgivere, infotrygdhistorikk)
        }
    }

    internal fun håndter(hendelse: OverstyrTidslinje) {
        håndterSykdomstidslinjeHendelse(hendelse) {
            val arbeidsgiverperiodeFørOverstyring = arbeidsgiver.arbeidsgiverperiode(periode())
            tilstand.håndter(this, hendelse)
            val arbeidsgiverperiodeEtterOverstyring = arbeidsgiver.arbeidsgiverperiode(periode())
            if (arbeidsgiverperiodeFørOverstyring != arbeidsgiverperiodeEtterOverstyring) {
                behandlinger.sisteInntektsmeldingDagerId()?.let {
                    person.arbeidsgiveropplysningerKorrigert(
                        PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                            korrigerendeInntektsopplysningId = hendelse.meldingsreferanseId(),
                            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER,
                            korrigertInntektsmeldingId = it
                        )
                    )
                }
            }
        }
    }

    private fun inntektsmeldingHåndtert(inntektsmelding: Inntektsmelding): Boolean {
        if (!inntektsmelding.leggTil(behandlinger)) return true
        person.emitInntektsmeldingHåndtert(inntektsmelding.meldingsreferanseId(), id, organisasjonsnummer)
        return false
    }

    private fun søknadHåndtert(søknad: Søknad) {
        person.emitSøknadHåndtert(søknad.meldingsreferanseId(), id, organisasjonsnummer)
    }

    internal fun håndter(anmodningOmForkasting: AnmodningOmForkasting) {
        if (!anmodningOmForkasting.erRelevant(id)) return
        kontekst(anmodningOmForkasting)
        if (anmodningOmForkasting.force) return forkast(anmodningOmForkasting)
        tilstand.håndter(this, anmodningOmForkasting)
    }

    private fun etterkomAnmodningOmForkasting(anmodningOmForkasting: AnmodningOmForkasting) {
        if (!arbeidsgiver.kanForkastes(this, anmodningOmForkasting)) return anmodningOmForkasting.info("Kan ikke etterkomme anmodning om forkasting")
        anmodningOmForkasting.info("Etterkommer anmodning om forkasting")
        forkast(anmodningOmForkasting)
    }

    internal fun håndter(replays: InntektsmeldingerReplay) {
        if (!replays.erRelevant(this.id)) return
        kontekst(replays)
        tilstand.replayUtført(this, replays)
    }

    internal fun inntektsmeldingFerdigbehandlet(hendelse: Hendelse) {
        kontekst(hendelse)
        tilstand.inntektsmeldingFerdigbehandlet(this, hendelse)
    }

    internal fun håndter(dager: DagerFraInntektsmelding) {
        if (!tilstand.skalHåndtereDager(this, dager) || dager.alleredeHåndtert(behandlinger))
            return dager.vurdertTilOgMed(periode.endInclusive)
        kontekst(dager)
        tilstand.håndter(this, dager)
        dager.vurdertTilOgMed(periode.endInclusive)
    }

    private fun skalHåndtereDagerRevurdering(dager: DagerFraInntektsmelding): Boolean {
        return skalHåndtereDager(dager) { sammenhengende ->
            dager.skalHåndteresAvRevurdering(periode, sammenhengende, finnArbeidsgiverperiode())
        }
    }

    private fun skalHåndtereDagerAvventerInntektsmelding(dager: DagerFraInntektsmelding): Boolean {
        return skalHåndtereDager(dager) { sammenhengende ->
            dager.skalHåndteresAv(sammenhengende)
        }
    }

    private fun skalHåndtereDager(
        dager: DagerFraInntektsmelding,
        strategi: DagerFraInntektsmelding.(Periode) -> Boolean
    ): Boolean {
        val sammenhengende = arbeidsgiver.finnSammenhengendeVedtaksperioder(this)
            .map { it.periode }
            .periode() ?: return false
        if (!strategi(dager, sammenhengende)) return false
        dager.info("Vedtaksperioden $periode håndterer dager fordi den sammenhengende perioden $sammenhengende overlapper med inntektsmelding")
        return true
    }

    private fun håndterDager(dager: DagerFraInntektsmelding) {
        val hendelse = dager.bitAvInntektsmelding(periode) ?: dager.tomBitAvInntektsmelding(periode)
        håndterDager(hendelse) {
            dager.valider(periode)
            dager.validerArbeidsgiverperiode(periode, finnArbeidsgiverperiode())
        }
    }

    private fun håndterDagerUtenEndring(dager: DagerFraInntektsmelding) {
        val hendelse = dager.tomBitAvInntektsmelding(periode)
        håndterDager(hendelse) {
            dager.valider(periode, finnArbeidsgiverperiode())
        }
    }

    private fun håndterDager(hendelse: DagerFraInntektsmelding.BitAvInntektsmelding, validering: () -> Unit) {
        oppdaterHistorikk(hendelse, validering)
    }

    internal fun håndterHistorikkFraInfotrygd(hendelse: Hendelse, infotrygdhistorikk: Infotrygdhistorikk) {
        kontekst(hendelse)
        tilstand.håndter(this, hendelse, infotrygdhistorikk)
    }

    internal fun håndter(
        ytelser: Ytelser,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        if (!ytelser.erRelevant(id)) return
        kontekst(ytelser)
        tilstand.håndter(person, arbeidsgiver, this, ytelser, infotrygdhistorikk)
    }

    internal fun håndter(utbetalingsavgjørelse: Behandlingsavgjørelse) {
        if (!utbetalingsavgjørelse.relevantVedtaksperiode(id)) return
        if (behandlinger.gjelderIkkeFor(utbetalingsavgjørelse)) return utbetalingsavgjørelse.info("Ignorerer løsning på utbetalingsavgjørelse, utbetalingid på løsningen matcher ikke vedtaksperiodens nåværende utbetaling")
        kontekst(utbetalingsavgjørelse)
        tilstand.håndter(person, arbeidsgiver, this, utbetalingsavgjørelse)
    }

    internal fun håndter(sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver) {
        if (!sykepengegrunnlagForArbeidsgiver.erRelevant(id, skjæringstidspunkt)) return
        kontekst(sykepengegrunnlagForArbeidsgiver)
        tilstand.håndter(this, sykepengegrunnlagForArbeidsgiver)
    }
    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        if (!vilkårsgrunnlag.erRelevant(id, skjæringstidspunkt)) return
        kontekst(vilkårsgrunnlag)
        tilstand.håndter(this, vilkårsgrunnlag)
    }

    internal fun håndter(simulering: Simulering) {
        if (simulering.vedtaksperiodeId != this.id.toString()) return
        kontekst(simulering)
        tilstand.håndter(this, simulering)
    }

    internal fun håndter(hendelse: UtbetalingHendelse) {
        if (!behandlinger.håndterUtbetalinghendelse(hendelse)) return
        kontekst(hendelse)
        tilstand.håndter(this, hendelse)
    }

    internal fun håndter(hendelse: AnnullerUtbetaling, vedtaksperioder: List<Vedtaksperiode>) {
        kontekst(hendelse)
        val annullering = behandlinger.håndterAnnullering(arbeidsgiver, hendelse, vedtaksperioder.map { it.behandlinger }) ?: return
        hendelse.info("Forkaster denne, og senere perioder, som følge av annullering.")
        forkast(hendelse)
        person.igangsettOverstyring(Revurderingseventyr.annullering(hendelse, annullering.periode()))
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        if (!påminnelse.erRelevant(id)) return false
        kontekst(påminnelse)
        tilstand.påminnelse(this, påminnelse)
        return true
    }

    internal fun nyAnnullering(hendelse: IAktivitetslogg, annullering: Utbetaling) {
        kontekst(hendelse)
        tilstand.nyAnnullering(this, hendelse)
    }

    internal fun håndter(overstyrInntektsgrunnlag: OverstyrInntektsgrunnlag): Boolean {
        if (!overstyrInntektsgrunnlag.erRelevant(skjæringstidspunkt)) return false
        if (vilkårsgrunnlag?.erArbeidsgiverRelevant(organisasjonsnummer) != true) return false
        kontekst(overstyrInntektsgrunnlag)
        overstyrInntektsgrunnlag.vilkårsprøvEtterNyInformasjonFraSaksbehandler(person, jurist)
        return true
    }

    // 💡Må ikke forveksles med `førsteFraværsdag` 💡
    // F.eks. januar med agp 1-10 & 16-21 så er `førsteFraværsdag` 16.januar, mens `startdatoPåSammenhengendeVedtaksperioder` er 1.januar
    private val startdatoPåSammenhengendeVedtaksperioder get() = arbeidsgiver.finnSammenhengendeVedtaksperioder(this).periode().start

    internal fun håndter(hendelse: Hendelse, servitør: Refusjonsservitør) {
        val refusjonstidslinje = servitør.servér(startdatoPåSammenhengendeVedtaksperioder, periode)
        if (refusjonstidslinje.isEmpty()) return
        behandlinger.håndterRefusjonstidslinje(arbeidsgiver, hendelse, person.beregnSkjæringstidspunkt(), arbeidsgiver.beregnArbeidsgiverperiode(jurist), refusjonstidslinje)
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

    private fun erForlengelse(): Boolean = arbeidsgiver
        .finnVedtaksperiodeRettFør(this)
        ?.takeIf { it.forventerInntekt() } != null

    private fun manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag(): Boolean {
        return vilkårsgrunnlag?.harNødvendigInntektForVilkårsprøving(organisasjonsnummer) == false
    }

    private fun måInnhenteInntektEllerRefusjon(hendelse: IAktivitetslogg): Boolean {
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        if (!arbeidsgiverperiode.forventerInntekt(periode)) return false
        if (tilstand.arbeidsgiveropplysningerStrategi.harInntektOgRefusjon(this, arbeidsgiverperiode, hendelse)) return false
        return true
    }

    private fun harFlereSkjæringstidspunkt(): Boolean {
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        if (!arbeidsgiverperiode.forventerInntekt(periode)) return false
        val utbetalingsdagerFørSkjæringstidspunkt = Arbeidsgiverperiode.utbetalingsdagerFørSkjæringstidspunkt(skjæringstidspunkt, periode, arbeidsgiverperiode)
        if (utbetalingsdagerFørSkjæringstidspunkt.isEmpty()) return false
        sikkerlogg.warn("Har flere skjæringstidspunkt:\n\nAktørId: $aktørId (${id.toString().take(5).uppercase()}) $periode\nSkjæringstidspunkt: ${skjæringstidspunkt.format(datoformat)}\nArbeidsgiver: ${organisasjonsnummer}\nUtbetalingsdager før skjæringstidspunkt: ${utbetalingsdagerFørSkjæringstidspunkt.joinToString { it.format(datoformat)} }\nSykdomstidslinje: ${sykdomstidslinje.toShortString()}")
        return true
    }

    internal fun kanForkastes(arbeidsgiverUtbetalinger: List<Utbetaling>, hendelse: IAktivitetslogg): Boolean {
        if (!behandlinger.kanForkastes(hendelse, arbeidsgiverUtbetalinger)) {
            hendelse.info("[kanForkastes] Kan ikke forkastes fordi behandlinger nekter det")
            return false
        }
        hendelse.info("[kanForkastes] Kan forkastes fordi evt. overlappende utbetalinger er annullerte/forkastet")
        return true
    }

    internal fun forkast(hendelse: Hendelse, utbetalinger: List<Utbetaling>): VedtaksperiodeForkastetEventBuilder? {
        if (!kanForkastes(utbetalinger, hendelse)) return null
        kontekst(hendelse)
        hendelse.info("Forkaster vedtaksperiode: %s", this.id.toString())
        this.behandlinger.forkast(arbeidsgiver, hendelse)
        val arbeidsgiverperiodeHensyntarForkastede = finnArbeidsgiverperiodeHensyntarForkastede()
        val trengerArbeidsgiveropplysninger =
            arbeidsgiverperiodeHensyntarForkastede?.forventerOpplysninger(periode) ?: false
        val sykmeldingsperioder =
            sykmeldingsperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiodeHensyntarForkastede)
        val vedtaksperiodeForkastetEventBuilder =
            VedtaksperiodeForkastetEventBuilder(tilstand.type, trengerArbeidsgiveropplysninger, sykmeldingsperioder)
        tilstand(hendelse, TilInfotrygd)
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
                    fødselsnummer = fødselsnummer,
                    aktørId = aktørId,
                    organisasjonsnummer = organisasjonsnummer,
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

    private fun forkast(hendelse: Hendelse) {
        if (!arbeidsgiver.kanForkastes(this, hendelse)) return hendelse.info("Kan ikke etterkomme forkasting")
        person.søppelbøtte(hendelse, TIDLIGERE_OG_ETTERGØLGENDE(this))
    }

    private fun revurderTidslinje(hendelse: OverstyrTidslinje) {
        oppdaterHistorikk(hendelse) {
            // ingen validering å gjøre :(
        }
        igangsettOverstyringAvTidslinje(hendelse)
    }

    private fun igangsettOverstyringAvTidslinje(hendelse: OverstyrTidslinje) {
        hendelse.info("Igangsetter overstyring av tidslinje")
        val vedtaksperiodeTilRevurdering = arbeidsgiver.finnVedtaksperiodeFør(this)
            ?.takeIf { nyArbeidsgiverperiodeEtterEndring(it) } ?: this
        person.igangsettOverstyring(
            Revurderingseventyr.sykdomstidslinje(
                hendelse,
                vedtaksperiodeTilRevurdering.skjæringstidspunkt,
                vedtaksperiodeTilRevurdering.periode
            )
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

    private fun kontekst(hendelse: IAktivitetslogg) {
        hendelse.kontekst(arbeidsgiver)
        hendelse.kontekst(this)
        hendelse.kontekst(this.tilstand)
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

    private fun oppdaterHistorikk(hendelse: SykdomshistorikkHendelse, validering: () -> Unit) {
        behandlinger.håndterEndring(person, arbeidsgiver, hendelse, person.beregnSkjæringstidspunkt(), arbeidsgiver.beregnArbeidsgiverperiode(jurist), validering)
    }

    private fun håndterEgenmeldingsperioderFraOverlappendeSøknad(søknad: Søknad) {
        val nyeEgenmeldingsperioder = søknad.egenmeldingsperioder()
        if (egenmeldingsperioder.lik(nyeEgenmeldingsperioder)) return
        if (nyeEgenmeldingsperioder.isEmpty()) return søknad.info("Hadde egenmeldingsperioder $egenmeldingsperioder, men den overlappende søknaden har ingen.")

        val sammenslåtteEgenmeldingsperioder = (egenmeldingsperioder + nyeEgenmeldingsperioder).grupperSammenhengendePerioderMedHensynTilHelg()
        søknad.info("Oppdaterer egenmeldingsperioder fra $egenmeldingsperioder til $sammenslåtteEgenmeldingsperioder")
        egenmeldingsperioder = sammenslåtteEgenmeldingsperioder
    }

    private fun håndterSøknad(søknad: Søknad, nesteTilstand: () -> Vedtaksperiodetilstand? = { null }) {
        oppdaterHistorikk(søknad) {
            søknad.valider(vilkårsgrunnlag, jurist)
        }
        tilstand.videreførRefusjonsopplysningerFraNabo(this, søknad)
        if (søknad.harFunksjonelleFeilEllerVerre()) return forkast(søknad)
        person.oppdaterVilkårsgrunnlagMedInntektene(skjæringstidspunkt, søknad, periode, søknad.nyeInntekterUnderveis(), jurist)
        nesteTilstand()?.also { tilstand(søknad, it) }
    }

    private fun håndterOverlappendeSøknad(søknad: Søknad, nesteTilstand: Vedtaksperiodetilstand? = null) {
        if (søknad.delvisOverlappende(periode)) {
            søknad.funksjonellFeil(`Mottatt søknad som delvis overlapper`)
            return forkast(søknad)
        }
        søknad.info("Håndterer overlappende søknad")
        håndterEgenmeldingsperioderFraOverlappendeSøknad(søknad)
        håndterSøknad(søknad) { nesteTilstand }
        person.igangsettOverstyring(Revurderingseventyr.korrigertSøknad(søknad, skjæringstidspunkt, periode))
    }

    private fun håndterOverlappendeSøknadRevurdering(søknad: Søknad) {
        if (søknad.delvisOverlappende(periode)) return søknad.funksjonellFeil(`Mottatt søknad som delvis overlapper`)
        if (søknad.sendtTilGosys()) return søknad.funksjonellFeil(RV_SØ_30)
        if (søknad.utenlandskSykmelding()) return søknad.funksjonellFeil(RV_SØ_29)
        else {
            søknad.info("Søknad har trigget en revurdering")
            håndterEgenmeldingsperioderFraOverlappendeSøknad(søknad)
            person.oppdaterVilkårsgrunnlagMedInntektene(skjæringstidspunkt, søknad, periode, søknad.nyeInntekterUnderveis(), jurist)
            oppdaterHistorikk(søknad) {
                søknad.valider(vilkårsgrunnlag, jurist)
            }
        }

        person.igangsettOverstyring(Revurderingseventyr.korrigertSøknad(søknad, skjæringstidspunkt, periode))
    }

    private fun håndtertInntektPåSkjæringstidspunktetOgVurderVarsel(hendelse: Inntektsmelding) {
        val harHåndtertInntektTidligere = behandlinger.harHåndtertInntektTidligere()
        if (inntektsmeldingHåndtert(hendelse)) return
        if (!harHåndtertInntektTidligere) return
        hendelse.varsel(RV_IM_4)
    }

    private fun håndterKorrigerendeInntektsmelding(dager: DagerFraInntektsmelding) {
        val korrigertInntektsmeldingId = behandlinger.sisteInntektsmeldingDagerId()
        val opprinneligAgp = finnArbeidsgiverperiode()
        if (dager.erKorrigeringForGammel(opprinneligAgp)) {
            håndterDagerUtenEndring(dager)
        } else {
            håndterDager(dager)
        }

        if (dager.harFunksjonelleFeilEllerVerre()) return

        val nyAgp = finnArbeidsgiverperiode()
        if (opprinneligAgp != null && !opprinneligAgp.klinLik(nyAgp)) {
            dager.varsel(RV_IM_24, "Ny agp er utregnet til å være ulik tidligere utregnet agp i ${tilstand.type.name}")
            korrigertInntektsmeldingId?.let {
                person.arbeidsgiveropplysningerKorrigert(
                    PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                        korrigerendeInntektsopplysningId = dager.meldingsreferanseId(),
                        korrigerendeInntektektsopplysningstype = Inntektsopplysningstype.INNTEKTSMELDING,
                        korrigertInntektsmeldingId = it
                    )
                )
            }
        }
    }

    private fun håndterVilkårsgrunnlag(vilkårsgrunnlag: Vilkårsgrunnlag, nesteTilstand: Vedtaksperiodetilstand) {
        val sykepengegrunnlag = vilkårsgrunnlag.avklarSykepengegrunnlag(person, jurist)
        vilkårsgrunnlag.valider(sykepengegrunnlag, jurist)
        val grunnlagsdata = vilkårsgrunnlag.grunnlagsdata()
        grunnlagsdata.validerFørstegangsvurdering(vilkårsgrunnlag)
        person.lagreVilkårsgrunnlag(grunnlagsdata)
        vilkårsgrunnlag.info("Vilkårsgrunnlag vurdert")
        if (vilkårsgrunnlag.harFunksjonelleFeilEllerVerre()) return forkast(vilkårsgrunnlag)
        arbeidsgiver.sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode(this, vilkårsgrunnlag)
        tilstand(vilkårsgrunnlag, nesteTilstand)
    }

    private fun håndterUtbetalingHendelse(hendelse: UtbetalingHendelse) {
        if (!hendelse.harFunksjonelleFeilEllerVerre()) return
        hendelse.funksjonellFeil(RV_UT_5)
    }

    private fun trengerYtelser(hendelse: IAktivitetslogg) {
        val søkevinduFamilieytelser = periode.familieYtelserPeriode
        foreldrepenger(hendelse, søkevinduFamilieytelser)
        pleiepenger(hendelse, søkevinduFamilieytelser)
        omsorgspenger(hendelse, søkevinduFamilieytelser)
        opplæringspenger(hendelse, søkevinduFamilieytelser)
        institusjonsopphold(hendelse, periode)
        arbeidsavklaringspenger(hendelse, periode.start.minusMonths(6), periode.endInclusive)
        dagpenger(hendelse, periode.start.minusMonths(2), periode.endInclusive)
    }

    private fun trengerVilkårsgrunnlag(hendelse: IAktivitetslogg) {
        val beregningSlutt = YearMonth.from(skjæringstidspunkt).minusMonths(1)
        inntekterForSykepengegrunnlag(hendelse, skjæringstidspunkt, beregningSlutt.minusMonths(2), beregningSlutt)
        inntekterForOpptjeningsvurdering(hendelse, skjæringstidspunkt, beregningSlutt, beregningSlutt)
        arbeidsforhold(hendelse, skjæringstidspunkt)
        medlemskap(hendelse, skjæringstidspunkt, periode.start, periode.endInclusive)
    }

    private fun trengerInntektFraSkatt(hendelse: IAktivitetslogg) {
        if (Toggle.InntektsmeldingSomIkkeKommer.enabled) {
            val beregningSlutt = YearMonth.from(skjæringstidspunkt).minusMonths(1)
            inntekterForSykepengegrunnlagForArbeidsgiver(hendelse, skjæringstidspunkt, organisasjonsnummer, beregningSlutt.minusMonths(2), beregningSlutt)
        }
    }

    private fun sjekkTrengerArbeidsgiveropplysninger(hendelse:IAktivitetslogg): Boolean {
        if (!måInnhenteInntektEllerRefusjon(hendelse)) return false
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        return arbeidsgiverperiode.forventerOpplysninger(periode)
    }

    private fun sendTrengerArbeidsgiveropplysninger(arbeidsgiverperiode: Arbeidsgiverperiode? = finnArbeidsgiverperiode()) {
        checkNotNull (arbeidsgiverperiode) { "Må ha arbeidsgiverperiode før vi sier dette." }
        val forespurtInntektOgRefusjon = person.forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer, skjæringstidspunkt, periode)
        val forespurteOpplysninger = forespurtInntektOgRefusjon + listOfNotNull(forespurtArbeidsgiverperiode(arbeidsgiverperiode))

        val vedtaksperioder = when {
            // For å beregne riktig arbeidsgiverperiode/første fraværsdag
            PersonObserver.Arbeidsgiverperiode in forespurteOpplysninger -> vedtaksperioderIArbeidsgiverperiodeTilOgMedDenne(arbeidsgiverperiode)
            // Dersom vi ikke trenger å beregne arbeidsgiverperiode/første fravarsdag trenger vi bare denne sykemeldingsperioden
            else -> listOf(this)
        }
        val førsteFraværsdager = person.førsteFraværsdager(skjæringstidspunkt)

        person.trengerArbeidsgiveropplysninger(
            PersonObserver.TrengerArbeidsgiveropplysningerEvent(
                organisasjonsnummer = organisasjonsnummer,
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
        return arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode).filter { it <= this  }
    }

    private fun trengerIkkeArbeidsgiveropplysninger() {
        person.trengerIkkeArbeidsgiveropplysninger(
            PersonObserver.TrengerIkkeArbeidsgiveropplysningerEvent(
                organisasjonsnummer = organisasjonsnummer,
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

    private fun trengerInntektsmeldingReplay() {
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
            organisasjonsnummer = organisasjonsnummer,
            sykmeldingsperioder = sykmeldingsperioder(vedtaksperioder),
            egenmeldingsperioder = egenmeldingsperioder(vedtaksperioder),
            førsteFraværsdager = førsteFraværsdager,
            trengerArbeidsgiverperiode = trengerArbeidsgiverperiode,
            erPotensiellForespørsel = !forventerInntekt()
        )
    }

    private fun emitVedtaksperiodeVenter(vedtaksperiodeVenter: VedtaksperiodeVenter) {
        person.vedtaksperiodeVenter(vedtaksperiodeVenter.event(aktørId, fødselsnummer))
    }

    private fun emitVedtaksperiodeEndret(previousState: Vedtaksperiodetilstand) {
        val event = PersonObserver.VedtaksperiodeEndretEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
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
        hendelse: IAktivitetslogg,
        behandlingId: UUID,
        tidsstempel: LocalDateTime,
        periode: Periode,
        dokumentsporing: Set<UUID>
    ) {
        if (finnArbeidsgiverperiode()?.dekkesAvArbeidsgiver(periode) != false) {
            jurist.logg(`§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`(periode, sykdomstidslinje.subsumsjonsformat()))
        }
        person.avsluttetUtenVedtak(PersonObserver.AvsluttetUtenVedtakEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = id,
            behandlingId = behandlingId,
            periode = periode,
            hendelseIder = hendelseIder,
            skjæringstidspunkt = skjæringstidspunkt,
            avsluttetTidspunkt = tidsstempel
        ))
        person.gjenopptaBehandling(hendelse)
    }

    override fun vedtakIverksatt(
        hendelse: IAktivitetslogg,
        vedtakFattetTidspunkt: LocalDateTime,
        behandling: Behandlinger.Behandling
    ) {
        val utkastTilVedtakBuilder = utkastTilVedtakBuilder()
        // Til ettertanke: Her er vi aldri innom "behandlinger"-nivå, så får ikke "Grunnbeløpsregulering"-tag, men AvsluttetMedVedtak har jo ikke tags nå uansett.
        behandling.berik(utkastTilVedtakBuilder)
        person.avsluttetMedVedtak(utkastTilVedtakBuilder.buildAvsluttedMedVedtak(vedtakFattetTidspunkt, hendelseIder))
        person.gjenopptaBehandling(hendelse)
    }

    override fun vedtakAnnullert(hendelse: IAktivitetslogg, behandlingId: UUID) {
        person.vedtaksperiodeAnnullert(PersonObserver.VedtaksperiodeAnnullertEvent(periode.start, periode.endInclusive, id, organisasjonsnummer,
            behandlingId
        ))
    }

    override fun behandlingLukket(behandlingId: UUID) {
        person.behandlingLukket(PersonObserver.BehandlingLukketEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = id,
            behandlingId = behandlingId
        ))
    }

    override fun behandlingForkastet(behandlingId: UUID, hendelse: Hendelse) {
        val automatiskBehandling = when(hendelse) {
            is AnnullerUtbetaling -> hendelse.erAutomatisk()
            is Utbetalingsgodkjenning -> hendelse.automatisert
            else -> true
        }

        person.behandlingForkastet(PersonObserver.BehandlingForkastetEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = id,
            behandlingId = behandlingId,
            automatiskBehandling = automatiskBehandling
        ))
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
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
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

    private fun høstingsresultater(hendelse: ArbeidstakerHendelse, simuleringtilstand: Vedtaksperiodetilstand, godkjenningtilstand: Vedtaksperiodetilstand) = when {
        behandlinger.harUtbetalinger() -> tilstand(hendelse, simuleringtilstand) {
            hendelse.info("""Saken oppfyller krav for behandling, settes til "Avventer simulering"""")
        }
        else -> tilstand(hendelse, godkjenningtilstand) {
            hendelse.info("""Saken oppfyller krav for behandling, settes til "Avventer godkjenning" fordi ingenting skal utbetales""")
        }
    }

    private fun Vedtaksperiodetilstand.påminnelse(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
        if (!påminnelse.gjelderTilstand(type)) return vedtaksperiode.person.vedtaksperiodeIkkePåminnet(id, organisasjonsnummer, type)
        vedtaksperiode.person.vedtaksperiodePåminnet(id, organisasjonsnummer, påminnelse)
        val beregnetMakstid = { tilstandsendringstidspunkt: LocalDateTime -> makstid(tilstandsendringstidspunkt) }
        if (påminnelse.nåddMakstid(beregnetMakstid)) return håndterMakstid(vedtaksperiode, påminnelse)
        håndter(vedtaksperiode, påminnelse)
    }

    override fun toString() =
        "${this.periode.start} - ${this.periode.endInclusive} (${this.tilstand::class.simpleName})"

    private fun finnArbeidsgiverperiode() = arbeidsgiver.arbeidsgiverperiode(periode)

    private fun finnArbeidsgiverperiodeHensyntarForkastede() = arbeidsgiver.arbeidsgiverperiodeInkludertForkastet(periode, sykdomstidslinje)

    private fun forventerInntekt(): Boolean {
        return Arbeidsgiverperiode.forventerInntekt(finnArbeidsgiverperiode(), periode)
    }

    private fun trengerGodkjenning(hendelse: IAktivitetslogg) {
        behandlinger.godkjenning(hendelse, utkastTilVedtakBuilder())
    }

    private fun utkastTilVedtakBuilder(): UtkastTilVedtakBuilder {
        val builder = UtkastTilVedtakBuilder(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            arbeidsgiver = organisasjonsnummer,
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

    internal fun gjenopptaBehandling(hendelse: Hendelse) {
        hendelse.kontekst(arbeidsgiver)
        kontekst(hendelse)
        hendelse.info("Forsøker å gjenoppta $this")
        tilstand.gjenopptaBehandling(this, hendelse)
    }


    internal fun igangsettOverstyring(revurdering: Revurderingseventyr) {
        if (revurdering.ikkeRelevant(periode)) return
        kontekst(revurdering)
        tilstand.igangsettOverstyring(this, revurdering)
        tilstand.arbeidsgiveropplysningerStrategi.lagreGjenbrukbareOpplysninger(this, revurdering)
    }

    internal fun inngåIRevurderingseventyret(
        vedtaksperioder: MutableList<PersonObserver.OverstyringIgangsatt.VedtaksperiodeData>,
        typeEndring: String
    ) {
        vedtaksperioder.add(
            PersonObserver.OverstyringIgangsatt.VedtaksperiodeData(
                orgnummer = organisasjonsnummer,
                vedtaksperiodeId = id,
                skjæringstidspunkt = skjæringstidspunkt,
                periode = periode,
                typeEndring = typeEndring
            )
        )
    }

    internal fun håndtertInntektPåSkjæringstidspunktet(skjæringstidspunkt: LocalDate, inntektsmelding: Inntektsmelding) {
        if (skjæringstidspunkt != this.skjæringstidspunkt) return
        if (!forventerInntekt()) return
        kontekst(inntektsmelding)
        tilstand.håndtertInntektPåSkjæringstidspunktet(this, inntektsmelding)
    }

    private fun vedtaksperiodeVenter(venterPå: Vedtaksperiode): VedtaksperiodeVenter? {
        val venteårsak = venterPå.venteårsak() ?: return null
        val builder = VedtaksperiodeVenter.Builder()
        builder.venterPå(venterPå.id, venterPå.skjæringstidspunkt, venterPå.organisasjonsnummer, venteårsak)
        builder.venter(
            vedtaksperiodeId = id,
            skjæringstidspunkt = skjæringstidspunkt,
            orgnummer = organisasjonsnummer,
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

    private fun venteårsak() = tilstand.venteårsak(this)

    private fun makstid(tilstandsendringstidspunkt: LocalDateTime = oppdatert) =
        tilstand.makstid(this, tilstandsendringstidspunkt)

    fun slutterEtter(dato: LocalDate) = periode.slutterEtter(dato)

    private fun aktivitetsloggkopi(hendelse: IAktivitetslogg) =
        hendelse.barn().also { kopi ->
            this.kontekst(kopi)
        }

    private fun oppdaterHistorikk(ytelser: Ytelser, infotrygdhistorikk: Infotrygdhistorikk) {
        val vilkårsgrunnlag = requireNotNull(vilkårsgrunnlag)
        ytelser.kontekst(vilkårsgrunnlag)
        vilkårsgrunnlag.valider(ytelser, organisasjonsnummer)
        infotrygdhistorikk.valider(ytelser, periode, skjæringstidspunkt, organisasjonsnummer)
        ytelser.oppdaterHistorikk(periode, skjæringstidspunkt, person.nåværendeVedtaksperioder(OVERLAPPENDE_ELLER_SENERE_MED_SAMME_SKJÆRINGSTIDSPUNKT(this)).firstOrNull()?.periode) {
            oppdaterHistorikk(
                ytelser.avgrensTil(periode),
                validering = {}
            )
        }
    }

    private fun lagNyUtbetaling(arbeidsgiverSomBeregner: Arbeidsgiver, hendelse: IAktivitetslogg, maksdatoresultat: Maksdatoresultat, utbetalingstidslinje: Utbetalingstidslinje, grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement) {
        behandlinger.nyUtbetaling(this.id, this.fødselsnummer, this.arbeidsgiver, grunnlagsdata, hendelse, maksdatoresultat, utbetalingstidslinje)
        val subsumsjonen = Utbetalingstidslinjesubsumsjon(this.jurist, this.sykdomstidslinje, utbetalingstidslinje)
        subsumsjonen.subsummer(periode, person.regler)
        loggDersomViTrekkerTilbakePengerPåAnnenArbeidsgiver(arbeidsgiverSomBeregner, hendelse)
    }

    private fun loggDersomViTrekkerTilbakePengerPåAnnenArbeidsgiver(arbeidsgiverSomBeregner: Arbeidsgiver, aktivitetslogg: IAktivitetslogg) {
        if (!behandlinger.trekkerTilbakePenger()) return
        if (this.arbeidsgiver === arbeidsgiverSomBeregner && !person.blitt6GBegrensetSidenSist(skjæringstidspunkt)) return
        aktivitetslogg.info("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere")
    }

    private fun perioderDetSkalBeregnesUtbetalingFor(): List<Vedtaksperiode> {
        // lag utbetaling for seg selv + andre overlappende perioder hos andre arbeidsgivere (som ikke er utbetalt/avsluttet allerede)
        return person.nåværendeVedtaksperioder { it.erKandidatForUtbetaling(this, this.skjæringstidspunkt) }.filter { it.behandlinger.klarForUtbetaling() }
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
        if (!forventerInntekt()) return false
        return this.periode.overlapperMed(periodeSomBeregner.periode) && skjæringstidspunktet == this.skjæringstidspunkt && !this.tilstand.erFerdigBehandlet
    }

    private fun førstePeriodeAnnenArbeidsgiverSomTrengerInntekt(): Vedtaksperiode? {
        // trenger ikke inntekt for vilkårsprøving om vi har vilkårsprøvd før
        if (vilkårsgrunnlag != null) return null
        return person.vedtaksperioder {
            it.organisasjonsnummer != organisasjonsnummer &&
            it.skjæringstidspunkt == skjæringstidspunkt &&
            it.forventerInntekt() &&
            !it.arbeidsgiver.kanBeregneSykepengegrunnlag(skjæringstidspunkt)
        }.minOrNull()
    }

    private fun førstePeriodeAnnenArbeidsgiverSomTrengerRefusjonsopplysninger(): Vedtaksperiode? {
        val bereningsperiode = perioderSomMåHensyntasVedBeregning().periode()
        return person.vedtaksperioder {
            it.organisasjonsnummer != organisasjonsnummer &&
            it.skjæringstidspunkt == skjæringstidspunkt &&
            it.periode.overlapperMed(bereningsperiode) &&
            it.måInnhenteInntektEllerRefusjon(Aktivitetslogg())
        }.minOrNull()
    }

    private fun førstePeriodeAnnenArbeidsgiverSomTrengerInntektsmelding() =
        førstePeriodeAnnenArbeidsgiverSomTrengerInntekt() ?: førstePeriodeAnnenArbeidsgiverSomTrengerRefusjonsopplysninger()

    private fun utbetalingstidslinje() = behandlinger.utbetalingstidslinje()

    private fun lagUtbetalingstidslinje(inntekt: ArbeidsgiverFaktaavklartInntekt?): Utbetalingstidslinje {
        /** krever inntekt for vedtaksperioder med samme skjæringstidspunkt som det som beregnes, tillater manglende for AUU'er */
        val inntekt = inntekt
            ?: defaultinntektForAUU() // todo: spleis må legge inn en IkkeRapportert-inntekt for alle auuer som finnes på skjæringstidspunktet når vi vilkårsprøver
            ?: error("Det er en vedtaksperiode som ikke inngår i SP: $organisasjonsnummer - $id - $periode." +
                    "Burde ikke arbeidsgiveren være kjent i sykepengegrunnlaget, enten i form av en skatteinntekt eller en tilkommet?")

        return behandlinger.lagUtbetalingstidslinje(inntekt, jurist)
    }

    private fun defaultinntektForAUU(): ArbeidsgiverFaktaavklartInntekt? {
        if (forventerInntekt()) return null
        return ArbeidsgiverFaktaavklartInntekt(
            skjæringstidspunkt = skjæringstidspunkt,
            `6G` = Grunnbeløp.`6G`.beløp(skjæringstidspunkt),
            fastsattÅrsinntekt = Inntekt.INGEN,
            gjelder = skjæringstidspunkt til LocalDate.MAX,
            refusjonsopplysninger = Refusjonsopplysninger()
        )
    }

    private fun beregnUtbetalinger(hendelse: IAktivitetslogg, andreYtelserPerioder: AndreYtelserPerioder): Maksdatoresultat {
        val perioderDetSkalBeregnesUtbetalingFor = perioderDetSkalBeregnesUtbetalingFor()

        check(perioderDetSkalBeregnesUtbetalingFor.all { it.skjæringstidspunkt == this.skjæringstidspunkt }) {
            "ugyldig situasjon: skal beregne utbetaling for vedtaksperioder med ulike skjæringstidspunkter"
        }
        val grunnlagsdata = checkNotNull(vilkårsgrunnlag) {
            "krever vilkårsgrunnlag for ${skjæringstidspunkt}, men har ikke. Lages det utbetaling for en periode som ikke skal lage utbetaling?"
        }

        val (maksdatofilter, beregnetTidslinjePerArbeidsgiver) = beregnUtbetalingstidslinjeForOverlappendeVedtaksperioder(hendelse, grunnlagsdata, andreYtelserPerioder)
        perioderDetSkalBeregnesUtbetalingFor.forEach { other ->
            val utbetalingstidslinje = beregnetTidslinjePerArbeidsgiver.getValue(other.organisasjonsnummer)
            val maksdatoresultat = maksdatofilter.maksdatoresultatForVedtaksperiode(other.periode, other.jurist)
            other.lagNyUtbetaling(this.arbeidsgiver, other.aktivitetsloggkopi(hendelse), maksdatoresultat, utbetalingstidslinje, grunnlagsdata)
        }
        return maksdatofilter.maksdatoresultatForVedtaksperiode(periode, EmptyLog)
    }

    private fun beregnUtbetalingstidslinjeForOverlappendeVedtaksperioder(
        hendelse: IAktivitetslogg,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
        andreYtelserPerioder: AndreYtelserPerioder
    ): Pair<MaksimumSykepengedagerfilter, Map<String, Utbetalingstidslinje>> {
        val uberegnetTidslinjePerArbeidsgiver = utbetalingstidslinjePerArbeidsgiver(grunnlagsdata)
        return filtrerUtbetalingstidslinjer(hendelse, uberegnetTidslinjePerArbeidsgiver, grunnlagsdata, andreYtelserPerioder)
    }

    private fun utbetalingstidslinjePerArbeidsgiver(grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement): Map<String, Utbetalingstidslinje> {
        val perioderSomMåHensyntasVedBeregning = perioderSomMåHensyntasVedBeregning().groupBy { it.organisasjonsnummer }

        val faktaavklarteInntekter = grunnlagsdata.faktaavklarteInntekter()
        val utbetalingstidslinjer = perioderSomMåHensyntasVedBeregning.mapValues { (arbeidsgiver, vedtaksperioder) ->
            val inntektForArbeidsgiver = faktaavklarteInntekter.forArbeidsgiver(arbeidsgiver)
            vedtaksperioder.map { it.lagUtbetalingstidslinje(inntektForArbeidsgiver) }
        }
        // nå vi må lage en ghost-tidslinje per arbeidsgiver for de som eksisterer i sykepengegrunnlaget.
        // resultatet er én utbetalingstidslinje per arbeidsgiver som garantert dekker perioden ${vedtaksperiode.periode}, dog kan
        // andre arbeidsgivere dekke litt før/litt etter, avhengig av perioden til vedtaksperiodene som overlapper
        return faktaavklarteInntekter.medGhostOgNyeInntekterUnderveis(utbetalingstidslinjer)
    }

    private fun filtrerUtbetalingstidslinjer(
        hendelse: IAktivitetslogg,
        uberegnetTidslinjePerArbeidsgiver: Map<String, Utbetalingstidslinje>,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
        andreYtelserPerioder: AndreYtelserPerioder
    ): Pair<MaksimumSykepengedagerfilter, Map<String, Utbetalingstidslinje>> {
        // grunnlaget for maksdatoberegning er alt som har skjedd før, frem til og med vedtaksperioden som
        // beregnes
        val historisktidslinjePerArbeidsgiver = person.vedtaksperioder { it.periode.endInclusive < periode.start }
            .groupBy { it.organisasjonsnummer }
            .mapValues { it.value.map { it.utbetalingstidslinje() }.reduce(Utbetalingstidslinje::plus) }

        val historisktidslinje = historisktidslinjePerArbeidsgiver.values
            .fold(person.infotrygdhistorikk.utbetalingstidslinje(), Utbetalingstidslinje::plus)

        val maksdatofilter = MaksimumSykepengedagerfilter(person.alder, person.regler, historisktidslinje)
        val filtere = listOf(
            AvvisAndreYtelserFilter(andreYtelserPerioder),
            Sykdomsgradfilter(person.minimumSykdomsgradsvurdering),
            AvvisDagerEtterDødsdatofilter(person.alder),
            AvvisInngangsvilkårfilter(grunnlagsdata),
            maksdatofilter,
            MaksimumUtbetalingFilter()
        )

        val kjørFilter = fun(tidslinjer: Map<String, Utbetalingstidslinje>, filter: UtbetalingstidslinjerFilter): Map<String, Utbetalingstidslinje> {
            val input = tidslinjer.entries.map { (key, value) -> key to value }
            val result = filter.filter(input.map { (_, tidslinje) -> tidslinje }, periode, hendelse, jurist)
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

    private fun håndterOverstyringIgangsattRevurdering(revurdering: Revurderingseventyr) {
        revurdering.inngåSomRevurdering(this, periode)
        behandlinger.sikreNyBehandling(arbeidsgiver, revurdering, person.beregnSkjæringstidspunkt(), arbeidsgiver.beregnArbeidsgiverperiode(jurist))
        tilstand(revurdering, AvventerRevurdering)
    }

    private fun håndterOverstyringIgangsattFørstegangsvurdering(revurdering: Revurderingseventyr) {
        revurdering.inngåSomEndring(this, periode)
        behandlinger.forkastUtbetaling(revurdering)
        if (måInnhenteInntektEllerRefusjon(revurdering)) return tilstand(revurdering, AvventerInntektsmelding)
        tilstand(revurdering, AvventerBlokkerendePeriode)
    }

    private fun periodeRettFørHarFåttInntektsmelding(): Boolean {
        val rettFør = arbeidsgiver.finnVedtaksperiodeRettFør(this) ?: return false
        if (rettFør.tilstand in setOf(AvsluttetUtenUtbetaling, AvventerInfotrygdHistorikk, AvventerInntektsmelding)) return false
        // auu-er vil kunne ligge i Avventer blokkerende periode
        if (rettFør.tilstand == AvventerBlokkerendePeriode && !rettFør.forventerInntekt()) return false
        if (rettFør.skjæringstidspunkt != this.skjæringstidspunkt) return false
        return true
    }

    private fun prioritertNabolag(): List<Vedtaksperiode> {
        val (nabolagFør, nabolagEtter) = this.arbeidsgiver.finnSammenhengendeVedtaksperioder(this).partition { it.periode.endInclusive < this.periode.start }
        // Vi prioriterer refusjonsopplysninger fra perioder før oss før vi sjekker forlengelsene
        // Når vi ser på periodene før oss starter vi med den nærmeste
        return (nabolagFør.asReversed() + nabolagEtter)
    }

    private fun videreførRefusjonsopplysningerFraNabo(hendelse: Hendelse? = null) {
        if (refusjonstidslinje.isNotEmpty()) return
        val refusjonstidslinjeFraNabolaget = prioritertNabolag().firstNotNullOfOrNull { it.refusjonstidslinje.takeUnless { refusjonstidslinje -> refusjonstidslinje.isEmpty() } } ?: return
        val nedarvetRefusjonstidslinje = refusjonstidslinjeFraNabolaget.strekk(this.periode).subset(this.periode)
        val refusjonstidslinjeFraRefusjonshistorikk = arbeidsgiver.refusjonstidslinje(this)
        this.behandlinger.håndterRefusjonstidslinje(arbeidsgiver, hendelse, person.beregnSkjæringstidspunkt(), arbeidsgiver.beregnArbeidsgiverperiode(jurist), nedarvetRefusjonstidslinje + refusjonstidslinjeFraRefusjonshistorikk)
    }

    internal sealed class ArbeidsgiveropplysningerStrategi {
        abstract fun harInntektOgRefusjon(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, hendelse: IAktivitetslogg): Boolean
        abstract fun harRefusjonsopplysninger(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, refusjonsopplysninger: Refusjonsopplysninger, hendelse: IAktivitetslogg): Boolean
        abstract fun lagreGjenbrukbareOpplysninger(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg)

        protected fun harEksisterendeInntektOgRefusjon(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, hendelse: IAktivitetslogg) =
            harEksisterendeInntekt(vedtaksperiode) && harRefusjonsopplysninger(vedtaksperiode, arbeidsgiverperiode, eksisterendeRefusjonsopplysninger(vedtaksperiode), hendelse)
        // Inntekt vi allerede har i vilkårsgrunnlag/inntektshistorikken på arbeidsgiver
        private fun harEksisterendeInntekt(vedtaksperiode: Vedtaksperiode): Boolean {
            // inntekt kreves så lenge det ikke finnes et vilkårsgrunnlag.
            // hvis det finnes et vilkårsgrunnlag så antas det at inntekten er representert der (vil vi slå ut på tilkommen inntekt-error senere hvis ikke)
            val vilkårsgrunnlag = vedtaksperiode.vilkårsgrunnlag
            return vilkårsgrunnlag != null || vedtaksperiode.arbeidsgiver.kanBeregneSykepengegrunnlag(vedtaksperiode.skjæringstidspunkt)
        }
        // Refusjonsopplysningene vi allerede har i vilkårsgrunnlag/ i refusjonshistorikken på arbeidsgiver
        private fun eksisterendeRefusjonsopplysninger(vedtaksperiode: Vedtaksperiode) = when (val vilkårsgrunnlag = vedtaksperiode.vilkårsgrunnlag) {
            null -> vedtaksperiode.arbeidsgiver.refusjonsopplysninger(vedtaksperiode.skjæringstidspunkt)
            else -> vilkårsgrunnlag.refusjonsopplysninger(vedtaksperiode.organisasjonsnummer)
        }
    }

    private data object FørInntektsmelding: ArbeidsgiveropplysningerStrategi() {
        override fun harInntektOgRefusjon(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, hendelse: IAktivitetslogg) =
            harEksisterendeInntektOgRefusjon(vedtaksperiode, arbeidsgiverperiode, hendelse)
        override fun harRefusjonsopplysninger(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, refusjonsopplysninger: Refusjonsopplysninger, hendelse: IAktivitetslogg) =
            Arbeidsgiverperiode.harNødvendigeRefusjonsopplysninger(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode, refusjonsopplysninger, arbeidsgiverperiode, hendelse, vedtaksperiode.organisasjonsnummer)
        override fun lagreGjenbrukbareOpplysninger(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) { /* Før vi har fått inntektmelding kan vi ikke lagre gjenbrukbare opplysninger 🙅‍ */}
    }

    private data object EtterInntektsmelding: ArbeidsgiveropplysningerStrategi() {
        override fun harInntektOgRefusjon(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, hendelse: IAktivitetslogg) =
            harEksisterendeInntektOgRefusjon(vedtaksperiode, arbeidsgiverperiode, hendelse) || vedtaksperiode.behandlinger.harGjenbrukbareOpplysninger(vedtaksperiode.organisasjonsnummer)
        override fun harRefusjonsopplysninger(vedtaksperiode: Vedtaksperiode, arbeidsgiverperiode: Arbeidsgiverperiode, refusjonsopplysninger: Refusjonsopplysninger, hendelse: IAktivitetslogg) =
            Arbeidsgiverperiode.harNødvendigeRefusjonsopplysningerEtterInntektsmelding(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode, refusjonsopplysninger, arbeidsgiverperiode, hendelse, vedtaksperiode.organisasjonsnummer)
        override fun lagreGjenbrukbareOpplysninger(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            val arbeidsgiverperiode = vedtaksperiode.finnArbeidsgiverperiode() ?: return
            if (vedtaksperiode.tilstand == AvventerBlokkerendePeriode && !arbeidsgiverperiode.forventerInntekt(vedtaksperiode.periode)) return // En periode i AvventerBlokkerendePeriode som skal tilbake AvsluttetUtenUtbetaling trenger uansett ikke inntekt og/eller refusjon
            if (harEksisterendeInntektOgRefusjon(vedtaksperiode, arbeidsgiverperiode, hendelse)) return // Trenger ikke lagre gjenbrukbare inntekter om vi har det vi trenger allerede
            vedtaksperiode.behandlinger.lagreGjenbrukbareOpplysninger(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer, vedtaksperiode.arbeidsgiver, hendelse) // Ikke 100% at dette lagrer noe. F.eks. revurderinger med Infotryfd-vilkårsgrunnlag har ikke noe å gjenbruke
        }
    }

    // Gang of four State pattern
    internal sealed interface Vedtaksperiodetilstand : Aktivitetskontekst {
        val type: TilstandType
        val erFerdigBehandlet: Boolean get() = false

        val arbeidsgiveropplysningerStrategi: ArbeidsgiveropplysningerStrategi get() = EtterInntektsmelding

        fun håndterRevurdering(hendelse: Hendelse, block: () -> Unit) {
            if (hendelse !is PersonHendelse) return block()
            PersonHendelse.wrap(hendelse, block)
        }
        fun håndterFørstegangsbehandling(hendelse: Hendelse, vedtaksperiode: Vedtaksperiode, block: () -> Unit) {
            if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, Aktivitetslogg())) return block()
            // Om førstegangsbehandling ikke kan forkastes (typisk Out of Order/ omgjøring av AUU) så håndteres det som om det er en revurdering
            håndterRevurdering(hendelse, block)
        }

        fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {}

        fun makstid(vedtaksperiode: Vedtaksperiode, tilstandsendringstidspunkt: LocalDateTime): LocalDateTime = LocalDateTime.MAX

        fun håndterMakstid(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.funksjonellFeil(RV_VT_1)
            vedtaksperiode.forkast(påminnelse)
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

        fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk)

        fun replayUtført(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {}
        fun inntektsmeldingFerdigbehandlet(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {}
        fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            dager.skalHåndteresAv(vedtaksperiode.periode)
        fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            vedtaksperiode.håndterKorrigerendeInntektsmelding(dager)
        }

        fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {}

        fun håndter(vedtaksperiode: Vedtaksperiode, sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver) {
            sykepengegrunnlagForArbeidsgiver.info("Forventet ikke sykepengegrunnlag for arbeidsgiver i %s".format(type.name))
        }
        fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
            vilkårsgrunnlag.info("Forventet ikke vilkårsgrunnlag i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            val kanForkastes = vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, anmodningOmForkasting)
            if (kanForkastes) return anmodningOmForkasting.info("Avslår anmodning om forkasting i ${type.name} (kan forkastes)")
            anmodningOmForkasting.info("Avslår anmodning om forkasting i ${type.name} (kan ikke forkastes)")
        }

        fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {}

        fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            ytelser: Ytelser,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            ytelser.info("Forventet ikke ytelsehistorikk i %s".format(type.name))
        }

        fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            utbetalingsavgjørelse: Behandlingsavgjørelse
        ) {
            utbetalingsavgjørelse.info("Forventet ikke utbetalingsavgjørelse i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {}

        fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering) {
            simulering.info("Forventet ikke simulering i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: UtbetalingHendelse) {
            hendelse.info("Forventet ikke utbetaling i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            hendelse.info("Forventet ikke overstyring fra saksbehandler i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrArbeidsgiveropplysninger) {}

        fun nyAnnullering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {}

        fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse
        ) {
            hendelse.info("Tidligere periode ferdigbehandlet, men gjør ingen tilstandsendring.")
        }

        fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr)

        fun beregnUtbetalinger(vedtaksperiode: Vedtaksperiode, ytelser: Ytelser) {
            ytelser.info("Etter å ha oppdatert sykdomshistorikken fra ytelser står vi nå i ${type.name}. Avventer beregning av utbetalinger.")
        }

        fun videreførRefusjonsopplysningerFraNabo(vedtaksperiode: Vedtaksperiode, søknad: Søknad) {}

        fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {}
    }

    internal data object Start : Vedtaksperiodetilstand {
        override val type = START
        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = HJELP.utenBegrunnelse

        override val arbeidsgiveropplysningerStrategi get(): ArbeidsgiveropplysningerStrategi = FørInntektsmelding

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            val harSenereUtbetalinger = vedtaksperiode.person.vedtaksperioder(NYERE_SKJÆRINGSTIDSPUNKT_MED_UTBETALING(vedtaksperiode)).isNotEmpty()
            val harSenereAUU = vedtaksperiode.person.vedtaksperioder(NYERE_SKJÆRINGSTIDSPUNKT_UTEN_UTBETALING(vedtaksperiode)).isNotEmpty()
            if (harSenereUtbetalinger || harSenereAUU) {
                søknad.varsel(RV_OO_1)
            }
            vedtaksperiode.arbeidsgiver.vurderOmSøknadIkkeKanHåndteres(søknad, vedtaksperiode, arbeidsgivere)
            infotrygdhistorikk.valider(søknad, vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer)
            vedtaksperiode.håndterSøknad(søknad)
            søknad.info("Fullført behandling av søknad")
            vedtaksperiode.person.igangsettOverstyring(Revurderingseventyr.nyPeriode(søknad, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode))
            if (søknad.harFunksjonelleFeilEllerVerre()) return
            vedtaksperiode.tilstand(søknad, when {
                !infotrygdhistorikk.harHistorikk() -> AvventerInfotrygdHistorikk
                vedtaksperiode.periodeRettFørHarFåttInntektsmelding() -> AvventerBlokkerendePeriode
                periodeRettEtterHarFåttInntektsmelding(vedtaksperiode, søknad) -> AvventerBlokkerendePeriode
                else -> AvventerInntektsmelding
            })
        }

        private fun periodeRettEtterHarFåttInntektsmelding(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg): Boolean {
            val rettEtter = vedtaksperiode.arbeidsgiver.finnVedtaksperiodeRettEtter(vedtaksperiode) ?: return false
            // antagelse at om vi har en periode rett etter oss, og vi har tilstrekkelig informasjon til utbetaling, så har vi endt
            // opp med å gjenbruke tidsnære opplysninger og trenger derfor ikke egen IM
            return !rettEtter.måInnhenteInntektEllerRefusjon(hendelse)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {}

        override fun videreførRefusjonsopplysningerFraNabo(vedtaksperiode: Vedtaksperiode, søknad: Søknad) {
            vedtaksperiode.videreførRefusjonsopplysningerFraNabo(søknad)
        }
    }

    internal data object AvventerInfotrygdHistorikk : Vedtaksperiodetilstand {
        override val type = AVVENTER_INFOTRYGDHISTORIKK
        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.person.trengerHistorikkFraInfotrygd(hendelse)
        }

        override val arbeidsgiveropplysningerStrategi get(): ArbeidsgiveropplysningerStrategi = FørInntektsmelding

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = null
        override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.person.trengerHistorikkFraInfotrygd(hendelse)
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            validation(hendelse) {
                onValidationFailed { vedtaksperiode.forkast(hendelse) }
                valider {
                    infotrygdhistorikk.valider(
                        this,
                        vedtaksperiode.periode,
                        vedtaksperiode.skjæringstidspunkt,
                        vedtaksperiode.organisasjonsnummer
                    )
                }
                onSuccess {
                    vedtaksperiode.tilstand(hendelse, AvventerInntektsmelding)
                }
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            vedtaksperiode.person.trengerHistorikkFraInfotrygd(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad)
        }
        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {}

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {}
    }

    internal data object AvventerRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.behandlinger.forkastUtbetaling(hendelse)
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak? {
            return tilstand(vedtaksperiode, Aktivitetslogg()).venteårsak()
        }

        override fun igangsettOverstyring(
            vedtaksperiode: Vedtaksperiode,
            revurdering: Revurderingseventyr
        ) {
            vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering)
            vedtaksperiode.behandlinger.forkastUtbetaling(revurdering)
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode): VedtaksperiodeVenter? {
            val venterPå = tilstand(vedtaksperiode, Aktivitetslogg()).venterPå() ?: nestemann
            return vedtaksperiode.vedtaksperiodeVenter(venterPå)
        }

        override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            tilstand(vedtaksperiode, hendelse).gjenopptaBehandling(vedtaksperiode, hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: UtbetalingHendelse) {
            vedtaksperiode.håndterUtbetalingHendelse(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (påminnelse.skalReberegnes()) {
                vedtaksperiode.behandlinger.lagreGjenbrukbareOpplysninger(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer, vedtaksperiode.arbeidsgiver, påminnelse)
            }
            vedtaksperiode.person.gjenopptaBehandling(påminnelse)
        }

        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            vedtaksperiode.inntektsmeldingHåndtert(hendelse)
        }

        private fun tilstand(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg): Tilstand {
            if (vedtaksperiode.harFlereSkjæringstidspunkt()) return HarFlereSkjæringstidspunkt(vedtaksperiode)
            if (vedtaksperiode.måInnhenteInntektEllerRefusjon(hendelse)) return TrengerInntektsmelding(vedtaksperiode)
            val førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver = vedtaksperiode.førstePeriodeAnnenArbeidsgiverSomTrengerInntektsmelding()
            if (førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver != null) return TrengerInntektsmeldingAnnenArbeidsgiver(førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver)
            if (vedtaksperiode.vilkårsgrunnlag == null) return KlarForVilkårsprøving
            return KlarForBeregning
        }

        private sealed interface Tilstand {
            fun venteårsak(): Venteårsak?
            fun venterPå(): Vedtaksperiode? = null
            fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse)
        }

        private data class TrengerInntektsmelding(private val vedtaksperiode: Vedtaksperiode): Tilstand {
            override fun venterPå() = vedtaksperiode
            override fun venteårsak() = INNTEKTSMELDING fordi SKJÆRINGSTIDSPUNKT_FLYTTET_REVURDERING
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Trenger inntektsmelding for perioden etter igangsatt revurdering")
            }
        }

        private data class HarFlereSkjæringstidspunkt(private val vedtaksperiode: Vedtaksperiode): Tilstand {
            override fun venterPå() = vedtaksperiode
            override fun venteårsak() = HJELP fordi FLERE_SKJÆRINGSTIDSPUNKT
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Denne perioden har flere skjæringstidspunkt slik den står nå. Saksbehandler må inn å vurdere om det kan overstyres dager på en slik måte at det kun er ett skjæringstidspunkt. Om ikke må den kastes ut av Speil.")
            }
        }

        private data class TrengerInntektsmeldingAnnenArbeidsgiver(private val trengerInntektsmelding: Vedtaksperiode): Tilstand {
            override fun venteårsak() = trengerInntektsmelding.venteårsak()
            override fun venterPå() = trengerInntektsmelding
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Trenger inntektsmelding på annen arbeidsgiver etter igangsatt revurdering")
            }
        }

        private data object KlarForVilkårsprøving: Tilstand {
            override fun venteårsak() = null
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                vedtaksperiode.tilstand(hendelse, AvventerVilkårsprøvingRevurdering) {
                    hendelse.info("Trenger å utføre vilkårsprøving før vi kan beregne utbetaling for revurderingen.")
                }
            }
        }

        private data object KlarForBeregning: Tilstand {
            override fun venteårsak() = null
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                vedtaksperiode.tilstand(hendelse, AvventerHistorikkRevurdering)
            }
        }
    }

    internal data object AvventerHistorikkRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_HISTORIKK_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            checkNotNull(vedtaksperiode.vilkårsgrunnlag) { "Forventer vilkårsgrunnlag for å beregne revurdering" }
            hendelse.info("Forespør sykdoms- og inntektshistorikk")
            vedtaksperiode.trengerYtelser(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) =
            BEREGNING fordi OVERSTYRING_IGANGSATT

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: vedtaksperiode.trengerYtelser(påminnelse)
        }
        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)
        override fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            ytelser: Ytelser,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            håndterRevurdering(ytelser) {
                vedtaksperiode.oppdaterHistorikk(ytelser, infotrygdhistorikk)
                vedtaksperiode.tilstand.beregnUtbetalinger(vedtaksperiode, ytelser)
            }
        }

        override fun beregnUtbetalinger(vedtaksperiode: Vedtaksperiode, ytelser: Ytelser) {
            val maksdatoresultat = vedtaksperiode.beregnUtbetalinger(ytelser, ytelser.andreYtelserPerioder())
            ytelser.valider(vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, maksdatoresultat.maksdato, vedtaksperiode.erForlengelse())
            vedtaksperiode.høstingsresultater(ytelser, AvventerSimuleringRevurdering, AvventerGodkjenningRevurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }
    }

    internal data object AvventerVilkårsprøvingRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_VILKÅRSPRØVING_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.trengerVilkårsgrunnlag(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = null

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            vedtaksperiode.trengerVilkårsgrunnlag(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
            håndterRevurdering(vilkårsgrunnlag) {
                vedtaksperiode.håndterVilkårsgrunnlag(vilkårsgrunnlag, AvventerHistorikkRevurdering)
            }
        }
        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)
        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }
    }

    internal data object AvventerInntektsmelding : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_INNTEKTSMELDING
        override fun makstid(vedtaksperiode: Vedtaksperiode, tilstandsendringstidspunkt: LocalDateTime): LocalDateTime =
            tilstandsendringstidspunkt.plusDays(180)

        override val arbeidsgiveropplysningerStrategi get(): ArbeidsgiveropplysningerStrategi = FørInntektsmelding

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.trengerInntektsmeldingReplay()
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) =
            if (vedtaksperiode.harFlereSkjæringstidspunkt()) HJELP fordi FLERE_SKJÆRINGSTIDSPUNKT else INNTEKTSMELDING.utenBegrunnelse

        override fun venter(
            vedtaksperiode: Vedtaksperiode,
            nestemann: Vedtaksperiode
        ) = vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            check(vedtaksperiode.behandlinger.harIkkeUtbetaling()) {
                "hæ?! vedtaksperiodens behandling er ikke uberegnet!"
            }
        }

        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding): Boolean {
            return vedtaksperiode.skalHåndtereDagerAvventerInntektsmelding(dager)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            vedtaksperiode.håndterDager(dager)
            if (dager.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(dager)
        }

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            vedtaksperiode.inntektsmeldingHåndtert(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.videreførRefusjonsopplysningerFraNabo()
            vurderOmKanGåVidere(vedtaksperiode, revurdering)
            if (vedtaksperiode.tilstand !in setOf(AvventerInntektsmelding, AvventerBlokkerendePeriode)) return
            if (vedtaksperiode.tilstand == AvventerInntektsmelding && vedtaksperiode.sjekkTrengerArbeidsgiveropplysninger(revurdering)) {
                vedtaksperiode.sendTrengerArbeidsgiveropplysninger()
            }
            revurdering.inngåVedSaksbehandlerendring(vedtaksperiode, vedtaksperiode.periode)
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            validation(hendelse) {
                onValidationFailed { vedtaksperiode.forkast(hendelse) }
                valider {
                    infotrygdhistorikk.valider(this, vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer)
                }
            }
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver
        ) {
            sykepengegrunnlagForArbeidsgiver.info("Håndterer sykepengegrunnlag for arbeidsgiver")
            sykepengegrunnlagForArbeidsgiver.varsel(Varselkode.RV_IV_10)
            vedtaksperiode.arbeidsgiver.lagreInntekt(sykepengegrunnlagForArbeidsgiver)
            vedtaksperiode.behandlinger.sendSkatteinntekterLagtTilGrunn(sykepengegrunnlagForArbeidsgiver, vedtaksperiode.person)
            vedtaksperiode.tilstand(sykepengegrunnlagForArbeidsgiver, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (vedtaksperiode.periodeRettFørHarFåttInntektsmelding()) {
                påminnelse.info("Periode ser ut til å feilaktig vente på inntektsmelding. ")
                return vedtaksperiode.tilstand(påminnelse, AvventerBlokkerendePeriode)
            }
            if (vedtaksperiode.harFlereSkjæringstidspunkt()) {
                påminnelse.funksjonellFeil(RV_IV_11)
                return vedtaksperiode.forkast(påminnelse)
            }
            if (påminnelse.skalReberegnes()) {
                vedtaksperiode.behandlinger.forkastUtbetaling(påminnelse)
                return vurderOmKanGåVidere(vedtaksperiode, påminnelse)
            }
            if (påminnelse.harVentet3MånederEllerMer()) {
                påminnelse.info("Her ønsker vi å hente inntekt fra skatt")
                return vedtaksperiode.trengerInntektFraSkatt(påminnelse)
            }
            if (vedtaksperiode.sjekkTrengerArbeidsgiveropplysninger(påminnelse)) {
                vedtaksperiode.sendTrengerArbeidsgiveropplysninger()
            }
        }

        override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.videreførRefusjonsopplysningerFraNabo()
            vurderOmKanGåVidere(vedtaksperiode, hendelse)
        }

        override fun replayUtført(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            if (vedtaksperiode.sjekkTrengerArbeidsgiveropplysninger(hendelse)) {
                vedtaksperiode.sendTrengerArbeidsgiveropplysninger()
                // ved out-of-order gir vi beskjed om at vi ikke trenger arbeidsgiveropplysninger for den seneste perioden lenger
                vedtaksperiode.arbeidsgiver.finnVedtaksperiodeRettEtter(vedtaksperiode)?.also {
                    it.trengerIkkeArbeidsgiveropplysninger()
                }
            }
            vurderOmKanGåVidere(vedtaksperiode, hendelse)
        }

        override fun inntektsmeldingFerdigbehandlet(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vurderOmKanGåVidere(vedtaksperiode, hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting)
        }

        private fun vurderOmKanGåVidere(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            if (!vedtaksperiode.forventerInntekt()) return vedtaksperiode.tilstand(hendelse, AvsluttetUtenUtbetaling)
            if (vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) {
                hendelse.funksjonellFeil(RV_SV_2)
                return vedtaksperiode.forkast(hendelse)
            }
            if (vedtaksperiode.harFlereSkjæringstidspunkt()) {
                hendelse.funksjonellFeil(RV_IV_11)
                return vedtaksperiode.forkast(hendelse)
            }
            if (vedtaksperiode.måInnhenteInntektEllerRefusjon(hendelse)) return
            vedtaksperiode.tilstand(hendelse, AvventerBlokkerendePeriode)
        }
    }

    internal data object AvventerBlokkerendePeriode : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_BLOKKERENDE_PERIODE
        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            check(!vedtaksperiode.måInnhenteInntektEllerRefusjon(hendelse)) {
                "Periode i avventer blokkerende har ikke tilstrekkelig informasjon til utbetaling! VedtaksperiodeId = ${vedtaksperiode.id}"
            }
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun makstid(vedtaksperiode: Vedtaksperiode, tilstandsendringstidspunkt: LocalDateTime) = when {
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

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            if (vedtaksperiode.forventerInntekt()) return vedtaksperiode.håndterKorrigerendeInntektsmelding(dager)
            vedtaksperiode.håndterDager(dager)
            if (dager.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(dager)
        }

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            vedtaksperiode.håndtertInntektPåSkjæringstidspunktetOgVurderVarsel(hendelse)
        }

        override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) =
            tilstand(hendelse, vedtaksperiode).gjenopptaBehandling(vedtaksperiode, hendelse)

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (påminnelse.skalReberegnes()) return vedtaksperiode.tilstand(påminnelse, AvventerInntektsmelding)
            if (vedtaksperiode.harFlereSkjæringstidspunkt()) {
                påminnelse.funksjonellFeil(RV_IV_11)
                return vedtaksperiode.forkast(påminnelse)
            }
            vedtaksperiode.person.gjenopptaBehandling(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse, infotrygdhistorikk: Infotrygdhistorikk) {
            // todo: infotrygdendringer burde nok kommet inn som revurderingseventyr istedenfor.. ?
            if (!vedtaksperiode.måInnhenteInntektEllerRefusjon(hendelse)) return
            vedtaksperiode.tilstand(hendelse, AvventerInntektsmelding)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.behandlinger.forkastUtbetaling(revurdering)
            if (vedtaksperiode.måInnhenteInntektEllerRefusjon(revurdering)) vedtaksperiode.tilstand(revurdering, AvventerInntektsmelding)
            revurdering.inngåVedSaksbehandlerendring(vedtaksperiode, vedtaksperiode.periode)
        }

        override fun beregnUtbetalinger(vedtaksperiode: Vedtaksperiode, ytelser: Ytelser) {
            super.beregnUtbetalinger(vedtaksperiode, ytelser)
            if (!vedtaksperiode.forventerInntekt()) {
                // LOL vi skal til AUU så bare slenger på noen varsler her
                ytelser.valider(vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode.endInclusive, vedtaksperiode.erForlengelse())
            }
        }

        private fun tilstand(
            hendelse: IAktivitetslogg,
            vedtaksperiode: Vedtaksperiode,
        ): Tilstand {
            check(!vedtaksperiode.måInnhenteInntektEllerRefusjon(hendelse)) {
                "Periode i avventer blokkerende har ikke tilstrekkelig informasjon til utbetaling! VedtaksperiodeId = ${vedtaksperiode.id}"
            }
            if (!vedtaksperiode.forventerInntekt()) return ForventerIkkeInntekt
            if (vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) return ManglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag
            if (vedtaksperiode.harFlereSkjæringstidspunkt()) return HarFlereSkjæringstidspunkt(vedtaksperiode)
            if (vedtaksperiode.person.avventerSøknad(vedtaksperiode.periode)) return AvventerTidligereEllerOverlappendeSøknad

            val førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver = vedtaksperiode.førstePeriodeAnnenArbeidsgiverSomTrengerInntektsmelding()
            if (førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver != null) return TrengerInntektsmeldingAnnenArbeidsgiver(førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver)
            if (vedtaksperiode.vilkårsgrunnlag == null) return KlarForVilkårsprøving
            return KlarForBeregning
        }

        private sealed interface Tilstand {
            fun venteårsak(): Venteårsak? = null
            fun venterPå(): Vedtaksperiode? = null
            fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse)
        }

        private data class HarFlereSkjæringstidspunkt(private val vedtaksperiode: Vedtaksperiode): Tilstand {
            override fun venterPå() = vedtaksperiode
            override fun venteårsak() = HJELP fordi FLERE_SKJÆRINGSTIDSPUNKT
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Denne perioden har flere skjæringstidspunkt slik den står nå.")
                hendelse.funksjonellFeil(RV_IV_11)
                return vedtaksperiode.forkast(hendelse)
            }
        }
        private data object AvventerTidligereEllerOverlappendeSøknad: Tilstand {
            override fun venteårsak() = SØKNAD.utenBegrunnelse
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Gjenopptar ikke behandling fordi minst én arbeidsgiver venter på søknad for sykmelding som er før eller overlapper med vedtaksperioden")
            }
        }
        private data object ForventerIkkeInntekt: Tilstand {
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                vedtaksperiode.tilstand(hendelse, AvsluttetUtenUtbetaling)
            }
        }
        private data object ManglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag: Tilstand {
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.funksjonellFeil(RV_SV_2)
                vedtaksperiode.forkast(hendelse)
            }
        }

        private data class TrengerInntektsmeldingAnnenArbeidsgiver(private val trengerInntektsmelding: Vedtaksperiode): Tilstand {
            override fun venteårsak() = trengerInntektsmelding.venteårsak()
            override fun venterPå() = trengerInntektsmelding
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Gjenopptar ikke behandling fordi minst én overlappende periode venter på nødvendig opplysninger fra arbeidsgiver")
            }
        }

        private data object KlarForVilkårsprøving: Tilstand {
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                vedtaksperiode.tilstand(hendelse, AvventerVilkårsprøving)
            }
        }

        private data object KlarForBeregning: Tilstand {
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                vedtaksperiode.tilstand(hendelse, AvventerHistorikk)
            }
        }
    }

    internal data object AvventerVilkårsprøving : Vedtaksperiodetilstand {
        override val type = AVVENTER_VILKÅRSPRØVING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.trengerVilkårsgrunnlag(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = null

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            vedtaksperiode.trengerVilkårsgrunnlag(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad, AvventerBlokkerendePeriode)
        }

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            vedtaksperiode.håndtertInntektPåSkjæringstidspunktetOgVurderVarsel(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
            håndterFørstegangsbehandling(vilkårsgrunnlag, vedtaksperiode) {
                vedtaksperiode.håndterVilkårsgrunnlag(vilkårsgrunnlag, AvventerHistorikk)
            }
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering)
        }

    }

    internal data object AvventerHistorikk : Vedtaksperiodetilstand {
        override val type = AVVENTER_HISTORIKK

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            checkNotNull(vedtaksperiode.vilkårsgrunnlag) { "Forventer vilkårsgrunnlag for å beregne utbetaling" }
            vedtaksperiode.trengerYtelser(hendelse)
            hendelse.info("Forespør sykdoms- og inntektshistorikk")
            val infotrygda = vedtaksperiode.vilkårsgrunnlag is VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag
            if (vedtaksperiode.arbeidsgiver.harIngenSporingTilInntektsmeldingISykefraværet() && !infotrygda) {
                hendelse.info("Inntektsmeldingen kunne ikke tolkes. Vi har ingen dokumentsporing til inntektsmeldingen i sykefraværet.")
            }
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = BEREGNING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: vedtaksperiode.trengerYtelser(påminnelse)
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            infotrygdhistorikk.valider(hendelse, vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer)
            if (hendelse.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(hendelse)
            if (vedtaksperiode.vilkårsgrunnlag != null) return
            hendelse.funksjonellFeil(Varselkode.RV_IT_33)
            vedtaksperiode.forkast(hendelse)
        }

        override fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            ytelser: Ytelser,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            håndterFørstegangsbehandling(ytelser, vedtaksperiode) {
                vedtaksperiode.oppdaterHistorikk(ytelser, infotrygdhistorikk)
                if (ytelser.harFunksjonelleFeilEllerVerre()) return@håndterFørstegangsbehandling vedtaksperiode.forkast(ytelser)
                vedtaksperiode.tilstand.beregnUtbetalinger(vedtaksperiode, ytelser)
            }
        }

        override fun beregnUtbetalinger(vedtaksperiode: Vedtaksperiode, ytelser: Ytelser) {
            val maksdatoresultat = vedtaksperiode.beregnUtbetalinger(ytelser, ytelser.andreYtelserPerioder())
            ytelser.valider(vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, maksdatoresultat.maksdato, vedtaksperiode.erForlengelse())
            if (ytelser.harFunksjonelleFeilEllerVerre()) return vedtaksperiode.forkast(ytelser)
            vedtaksperiode.høstingsresultater(ytelser, AvventerSimulering, AvventerGodkjenning)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering)
        }
    }

    internal data object AvventerSimulering : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_SIMULERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            trengerSimulering(vedtaksperiode, hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = UTBETALING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: trengerSimulering(vedtaksperiode, påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering) {
            håndterFørstegangsbehandling(simulering, vedtaksperiode) {
                vedtaksperiode.behandlinger.valider(simulering)
            }
            if (!vedtaksperiode.behandlinger.erKlarForGodkjenning()) return simulering.info("Kan ikke gå videre da begge oppdragene ikke er simulert.")
            vedtaksperiode.tilstand(simulering, AvventerGodkjenning)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        private fun trengerSimulering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.behandlinger.simuler(hendelse)
        }
    }

    internal data object AvventerSimuleringRevurdering : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_SIMULERING_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.behandlinger.simuler(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = UTBETALING fordi OVERSTYRING_IGANGSATT

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering)
        }

        override fun venter(
            vedtaksperiode: Vedtaksperiode,
            nestemann: Vedtaksperiode
        ) = vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: vedtaksperiode.behandlinger.simuler(påminnelse)
        }
        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)
        override fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering) {
            håndterRevurdering(simulering) {
                vedtaksperiode.behandlinger.valider(simulering)
            }
            if (!vedtaksperiode.behandlinger.erKlarForGodkjenning()) return simulering.info("Kan ikke gå videre da begge oppdragene ikke er simulert.")
            vedtaksperiode.tilstand(simulering, AvventerGodkjenningRevurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

    }

    internal data object AvventerGodkjenning : Vedtaksperiodetilstand {
        override val type = AVVENTER_GODKJENNING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.trengerGodkjenning(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = GODKJENNING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad, AvventerBlokkerendePeriode)
            if (søknad.harFunksjonelleFeilEllerVerre()) return
        }

        override fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            utbetalingsavgjørelse: Behandlingsavgjørelse
        ) {
            vedtaksperiode.behandlinger.vedtakFattet(arbeidsgiver, utbetalingsavgjørelse)
            if (vedtaksperiode.behandlinger.erAvvist()) {
                return if (arbeidsgiver.kanForkastes(vedtaksperiode, utbetalingsavgjørelse)) vedtaksperiode.forkast(utbetalingsavgjørelse)
                else utbetalingsavgjørelse.varsel(RV_UT_24)
            }
            vedtaksperiode.tilstand(
                utbetalingsavgjørelse,
                when {
                    vedtaksperiode.behandlinger.harUtbetalinger() -> TilUtbetaling
                    else -> Avsluttet
                }
            )
        }

        override fun nyAnnullering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.tilstand(hendelse, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: vedtaksperiode.trengerGodkjenning(påminnelse)
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            if (!vedtaksperiode.behandlinger.erHistorikkEndretSidenBeregning(infotrygdhistorikk)) {
                hendelse.info("Infotrygdhistorikken er uendret, reberegner ikke periode")
                return
            }
            vedtaksperiode.behandlinger.forkastUtbetaling(hendelse)
            vedtaksperiode.tilstand(hendelse, AvventerBlokkerendePeriode) {
                hendelse.info("Infotrygdhistorikken har endret seg, reberegner periode")
            }
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering)
        }

    }

    internal data object AvventerGodkjenningRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_GODKJENNING_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.trengerGodkjenning(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = GODKJENNING fordi OVERSTYRING_IGANGSATT

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering)
        }

        override fun nyAnnullering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            if (vedtaksperiode.behandlinger.erAvvist()) return
            vedtaksperiode.tilstand(hendelse, AvventerRevurdering)
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: vedtaksperiode.trengerGodkjenning(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            utbetalingsavgjørelse: Behandlingsavgjørelse
        ) {
            vedtaksperiode.behandlinger.vedtakFattet(arbeidsgiver, utbetalingsavgjørelse)
            if (vedtaksperiode.behandlinger.erAvvist()) {
                if (utbetalingsavgjørelse.automatisert) {
                    return utbetalingsavgjørelse.info("Revurderingen ble avvist automatisk - hindrer tilstandsendring for å unngå saker som blir stuck")
                }
            }
            vedtaksperiode.tilstand(utbetalingsavgjørelse, when {
                vedtaksperiode.behandlinger.erAvvist() -> RevurderingFeilet
                vedtaksperiode.behandlinger.harUtbetalinger() -> TilUtbetaling
                else -> Avsluttet
            })
        }
        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            if (vedtaksperiode.behandlinger.erHistorikkEndretSidenBeregning(infotrygdhistorikk)) return vedtaksperiode.tilstand(
                hendelse,
                AvventerRevurdering
            ) {
                hendelse.info("Infotrygdhistorikken har endret seg, reberegner periode")
            }
            else hendelse.info("Infotrygdhistorikken er uendret, reberegner ikke periode")
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }
    }

    internal data object TilUtbetaling : Vedtaksperiodetilstand {
        override val type = TIL_UTBETALING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {}

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = UTBETALING.utenBegrunnelse

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering)
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: UtbetalingHendelse) {
            vedtaksperiode.håndterUtbetalingHendelse(hendelse)
            if (!vedtaksperiode.behandlinger.erAvsluttet()) return
            vedtaksperiode.tilstand(hendelse, Avsluttet) {
                hendelse.info("OK fra Oppdragssystemet")
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            when {
                vedtaksperiode.behandlinger.erUbetalt() -> vedtaksperiode.tilstand(påminnelse, AvventerBlokkerendePeriode)
                vedtaksperiode.behandlinger.erAvsluttet() -> vedtaksperiode.tilstand(påminnelse, Avsluttet)
            }
        }
    }

    internal data object AvsluttetUtenUtbetaling : Vedtaksperiodetilstand {
        override val type = AVSLUTTET_UTEN_UTBETALING
        override val erFerdigBehandlet = true

        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            val arbeidsgiverperiode = vedtaksperiode.arbeidsgiver.arbeidsgiverperiodeHensyntattEgenmeldinger(vedtaksperiode.periode)
            if (arbeidsgiverperiode?.forventerInntekt(vedtaksperiode.periode) == true) {
                // Dersom egenmeldingene hinter til at perioden er utenfor AGP, da ønsker vi å sende en ekte forespørsel til arbeidsgiver om opplysninger
                vedtaksperiode.sendTrengerArbeidsgiveropplysninger(arbeidsgiverperiode)
            }
            val utbetalingstidslinje = forsøkÅLageUtbetalingstidslinje(vedtaksperiode, hendelse)
            vedtaksperiode.behandlinger.avsluttUtenVedtak(vedtaksperiode.arbeidsgiver, hendelse, utbetalingstidslinje)
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        private fun forsøkÅLageUtbetalingstidslinje(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg): Utbetalingstidslinje {
            val faktaavklarteInntekter = vedtaksperiode.vilkårsgrunnlag?.faktaavklarteInntekter()?.forArbeidsgiver(vedtaksperiode.organisasjonsnummer)
            return try {
                vedtaksperiode.lagUtbetalingstidslinje(faktaavklarteInntekter)
            } catch (err: Exception) {
                sikkerLogg.warn("klarte ikke lage utbetalingstidslinje for auu: ${err.message}, {}", kv("vedtaksperiodeId", vedtaksperiode.id), kv("aktørId", vedtaksperiode.aktørId), err)
                Utbetalingstidslinje()
            }
        }

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.behandlinger.bekreftÅpenBehandling(vedtaksperiode.arbeidsgiver)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak {
            if (!vedtaksperiode.forventerInntekt()) return HJELP.utenBegrunnelse
            return HJELP fordi VIL_OMGJØRES
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            if (!vedtaksperiode.forventerInntekt()) null
            else vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.behandlinger.sikreNyBehandling(vedtaksperiode.arbeidsgiver, revurdering, vedtaksperiode.person.beregnSkjæringstidspunkt(), vedtaksperiode.arbeidsgiver.beregnArbeidsgiverperiode(vedtaksperiode.jurist))
            if (vedtaksperiode.forventerInntekt()) {
                revurdering.inngåSomEndring(vedtaksperiode, vedtaksperiode.periode)
                revurdering.loggDersomKorrigerendeSøknad(revurdering, "Startet omgjøring grunnet korrigerende søknad")
                revurdering.info(RV_RV_1.varseltekst)
                if (vedtaksperiode.måInnhenteInntektEllerRefusjon(revurdering)) {
                    revurdering.info("mangler nødvendige opplysninger fra arbeidsgiver")
                    return vedtaksperiode.tilstand(revurdering, AvventerInntektsmelding)
                }
            }
            vedtaksperiode.tilstand(revurdering, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            søknad.info("Prøver å igangsette revurdering grunnet korrigerende søknad")
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            vedtaksperiode.håndterDager(dager)
            if (dager.harFunksjonelleFeilEllerVerre()) {
                if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, dager)) return vedtaksperiode.forkast(dager)
                return vedtaksperiode.behandlinger.avsluttUtenVedtak(vedtaksperiode.arbeidsgiver, dager, forsøkÅLageUtbetalingstidslinje(vedtaksperiode, dager))
            }
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            if (!vedtaksperiode.forventerInntekt()) return
            vedtaksperiode.behandlinger.sikreNyBehandling(vedtaksperiode.arbeidsgiver, hendelse, vedtaksperiode.person.beregnSkjæringstidspunkt(), vedtaksperiode.arbeidsgiver.beregnArbeidsgiverperiode(vedtaksperiode.jurist))
            håndterFørstegangsbehandling(hendelse, vedtaksperiode) {
                infotrygdhistorikk.valider(hendelse, vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer)
            }
            if (hendelse.harFunksjonelleFeilEllerVerre()) {
                hendelse.info("Forkaster perioden fordi Infotrygdhistorikken ikke validerer")
                return vedtaksperiode.forkast(hendelse)
            }
            if (vedtaksperiode.måInnhenteInntektEllerRefusjon(hendelse)) {
                hendelse.info("Forkaster perioden fordi perioden har ikke tilstrekkelig informasjon til utbetaling")
                return vedtaksperiode.forkast(hendelse)
            }
            hendelse.varsel(RV_IT_38)
            vedtaksperiode.person.igangsettOverstyring(
                Revurderingseventyr.infotrygdendring(hendelse, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)
            )
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            forsøkÅLageUtbetalingstidslinje(vedtaksperiode, påminnelse)

            if (!vedtaksperiode.forventerInntekt() && vedtaksperiode.behandlinger.erAvsluttet()) return påminnelse.info("Forventer ikke inntekt. Vil forbli i AvsluttetUtenUtbetaling")
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }
    }

    internal data object Avsluttet : Vedtaksperiodetilstand {
        override val type = AVSLUTTET

        override val erFerdigBehandlet = true
        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.behandlinger.bekreftAvsluttetBehandlingMedVedtak(vedtaksperiode.arbeidsgiver)
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) =
            HJELP.utenBegrunnelse

        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            vedtaksperiode.håndterKorrigerendeInntektsmelding(dager)
        }

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.behandlinger.bekreftÅpenBehandling(vedtaksperiode.arbeidsgiver)
        }

        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.behandlinger.sikreNyBehandling(vedtaksperiode.arbeidsgiver, revurdering, vedtaksperiode.person.beregnSkjæringstidspunkt(), vedtaksperiode.arbeidsgiver.beregnArbeidsgiverperiode(vedtaksperiode.jurist))
            vedtaksperiode.jurist.logg(`fvl § 35 ledd 1`())
            revurdering.inngåSomRevurdering(vedtaksperiode, vedtaksperiode.periode)
            vedtaksperiode.tilstand(revurdering, AvventerRevurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            }
        }
    }

    internal data object RevurderingFeilet : Vedtaksperiodetilstand {
        override val type: TilstandType = REVURDERING_FEILET

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }
        override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak? {
            if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, Aktivitetslogg())) return null
            return HJELP.utenBegrunnelse
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            throw IllegalStateException("Kan ikke håndtere søknad mens perioden er i RevurderingFeilet")
        }

        override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            if (!vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode, Aktivitetslogg())) return hendelse.info("Gjenopptar ikke revurdering feilet fordi perioden har tidligere avsluttede utbetalinger. Må behandles manuelt vha annullering.")
            hendelse.funksjonellFeil(RV_RV_2)
            vedtaksperiode.forkast(hendelse)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {}
    }

    internal data object TilInfotrygd : Vedtaksperiodetilstand {
        override val type = TIL_INFOTRYGD
        override val erFerdigBehandlet = true

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            hendelse.info("Vedtaksperioden kan ikke behandles i Spleis.")
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode) = HJELP.utenBegrunnelse

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
            throw IllegalStateException("Kan ikke håndtere søknad mens perioden er i TilInfotrygd")
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            hendelse.info("Overstyrer ikke en vedtaksperiode som er sendt til Infotrygd")
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            throw IllegalStateException("Revurdering håndteres av en periode i til_infotrygd")
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
        internal fun List<Vedtaksperiode>.refusjonstidslinje() = fold(Beløpstidslinje()) { beløpstidslinje, vedtaksperiode ->
            beløpstidslinje + vedtaksperiode.refusjonstidslinje
        }
        internal fun List<Vedtaksperiode>.startdatoerPåSammenhengendeVedtaksperioder(): Set<LocalDate> {
            val startdatoer = mutableMapOf<UUID, LocalDate>()

            this.forEach { vedtaksperiode ->
                if (vedtaksperiode.id in startdatoer) return@forEach
                val sammenhendeVedtaksperioder = vedtaksperiode.arbeidsgiver.finnSammenhengendeVedtaksperioder(vedtaksperiode)
                val startdatoPåSammenhengendeVedtaksperioder = sammenhendeVedtaksperioder.periode().start
                startdatoer.putAll(sammenhendeVedtaksperioder.associate { it.id to startdatoPåSammenhengendeVedtaksperioder })
            }

            return startdatoer.values.toSet()
        }
        internal fun List<Vedtaksperiode>.refusjonseventyr(hendelse: Hendelse) = firstOrNull {
            it.behandlinger.håndterer(Dokumentsporing.inntektsmeldingRefusjon(hendelse.meldingsreferanseId()))
        }?.let { Revurderingseventyr.refusjonsopplysninger(hendelse, it.skjæringstidspunkt, it.periode) }

        // Fredet funksjonsnavn
        internal val TIDLIGERE_OG_ETTERGØLGENDE = fun(segSelv: Vedtaksperiode): VedtaksperiodeFilter {
            val medSammeAGP = MED_SAMME_AGP_OG_SKJÆRINGSTIDSPUNKT(segSelv)
            return fun (other: Vedtaksperiode): Boolean {
                if (other.periode.start >= segSelv.periode.start) return true // Forkaster nyere perioder på tvers av arbeidsgivere
                return medSammeAGP(other)
            }
        }
        internal val MED_SAMME_AGP_OG_SKJÆRINGSTIDSPUNKT = fun(segSelv: Vedtaksperiode): VedtaksperiodeFilter {
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            val arbeidsgiverperiode = segSelv.finnArbeidsgiverperiode()
            return fun (other: Vedtaksperiode): Boolean {
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
                MED_SKJÆRINGSTIDSPUNKT(skjæringstidspunkt)(vedtaksperiode) && vedtaksperiode.forventerInntekt()
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
            it.tilstand == AvsluttetUtenUtbetaling && it.forventerInntekt()
        }

        internal val OVERLAPPENDE_ELLER_SENERE_MED_SAMME_SKJÆRINGSTIDSPUNKT = { segSelv: Vedtaksperiode ->
            { vedtaksperiode: Vedtaksperiode ->
                vedtaksperiode !== segSelv && vedtaksperiode.skjæringstidspunkt == segSelv.skjæringstidspunkt && vedtaksperiode.periode.start >= segSelv.periode.start
            }
        }

        private fun sykmeldingsperioder(vedtaksperioder: List<Vedtaksperiode>): List<Periode> {
            return vedtaksperioder.map { it.sykmeldingsperiode }
        }
        private fun egenmeldingsperioder(vedtaksperioder: List<Vedtaksperiode>) = vedtaksperioder.flatMap { it.egenmeldingsperioder }

        internal fun List<Vedtaksperiode>.beregnSkjæringstidspunkter(beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) {
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

        private fun Vedtaksperiode.erTidligereEnn(other: Vedtaksperiode): Boolean = this <= other || this.skjæringstidspunkt < other.skjæringstidspunkt

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


        internal fun List<Vedtaksperiode>.sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            val nestePeriode = this
                .firstOrNull { it.skjæringstidspunkt > vedtaksperiode.skjæringstidspunkt && it.forventerInntekt() }
                ?.takeIf { it.tilstand == AvventerInntektsmelding }
                ?: return
            if (nestePeriode.sjekkTrengerArbeidsgiveropplysninger(hendelse)) {
                nestePeriode.sendTrengerArbeidsgiveropplysninger()
            }
        }

        internal fun Iterable<Vedtaksperiode>.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas: Vedtaksperiode) {
            check(this.filterNot { it == periodeSomSkalGjenopptas }.none(HAR_AVVENTENDE_GODKJENNING)) {
                "Ugyldig situasjon! Flere perioder til godkjenning samtidig"
            }
        }

        internal fun List<Vedtaksperiode>.venter(nestemann: Vedtaksperiode) {
            forEach { vedtaksperiode ->
                vedtaksperiode.tilstand.venter(vedtaksperiode, nestemann)?.also {
                    vedtaksperiode.emitVedtaksperiodeVenter(it)
                }
            }
        }

        internal fun List<Vedtaksperiode>.validerTilstand(hendelse: Hendelse) = forEach { it.validerTilstand(hendelse) }

        internal fun harNyereForkastetPeriode(forkastede: Iterable<Vedtaksperiode>, vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) =
            forkastede
                .filter { it.periode.start > vedtaksperiode.periode.endInclusive }
                .onEach {
                    val sammeArbeidsgiver = it.organisasjonsnummer == vedtaksperiode.organisasjonsnummer
                    hendelse.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_31 else RV_SØ_32)
                    hendelse.info("Søknaden ${vedtaksperiode.periode} er før en forkastet vedtaksperiode ${it.id} (${it.periode})")
                }
                .isNotEmpty()

        internal fun harOverlappendeForkastetPeriode(forkastede: Iterable<Vedtaksperiode>, vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) =
            forkastede
                .filter { it.periode.overlapperMed(vedtaksperiode.periode()) }
                .onEach {
                    val delvisOverlappende = !it.periode.inneholder(vedtaksperiode.periode) // hvorvidt vedtaksperioden strekker seg utenfor den forkastede
                    val sammeArbeidsgiver = it.organisasjonsnummer == vedtaksperiode.organisasjonsnummer
                    hendelse.funksjonellFeil(when {
                        delvisOverlappende && sammeArbeidsgiver -> RV_SØ_35
                        delvisOverlappende && !sammeArbeidsgiver -> RV_SØ_36
                        !delvisOverlappende && sammeArbeidsgiver -> RV_SØ_33
                        !delvisOverlappende && !sammeArbeidsgiver -> RV_SØ_34
                        else -> throw IllegalStateException("dette er ikke mulig med mindre noen har tullet til noe")
                    })
                    hendelse.info("Søknad ${vedtaksperiode.periode} overlapper med en forkastet vedtaksperiode ${it.id} (${it.periode})")
                }
                .isNotEmpty()

        internal fun harKortGapTilForkastet(forkastede: Iterable<Vedtaksperiode>, hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode) =
            forkastede
                .filter { other -> vedtaksperiode.påvirkerArbeidsgiverperioden(other) }
                .onEach {
                    hendelse.funksjonellFeil(RV_SØ_28)
                    hendelse.info("Søknad har et gap som er kortere enn 20 dager til en forkastet vedtaksperiode ${it.id}, vedtaksperiode periode: ${it.periode}")
                }
                .isNotEmpty()

        internal fun forlengerForkastet(forkastede: List<Vedtaksperiode>, hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode) =
            forkastede
                .filter { it.periode.erRettFør(vedtaksperiode.periode) }
                .onEach {
                    val sammeArbeidsgiver = it.organisasjonsnummer == vedtaksperiode.organisasjonsnummer
                    hendelse.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_37 else RV_SØ_38)
                    hendelse.info("Søknad forlenger forkastet vedtaksperiode ${it.id}, vedtaksperiode periode: ${it.periode}")
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
            aktørId: String,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            organisasjonsnummer: String,
            dto: VedtaksperiodeInnDto,
            subsumsjonslogg: Subsumsjonslogg,
            grunnlagsdata: Map<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>,
            utbetalinger: Map<UUID, Utbetaling>
        ): Vedtaksperiode {
            return Vedtaksperiode(
                person = person,
                arbeidsgiver = arbeidsgiver,
                id = dto.id,
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                organisasjonsnummer = organisasjonsnummer,
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
        return result.copy(overlappendeInfotrygdperioder = result.overlappendeInfotrygdperioder.plusElement(
            PersonObserver.OverlappendeInfotrygdperiodeEtterInfotrygdendring(
                organisasjonsnummer = this.organisasjonsnummer,
                vedtaksperiodeId = this.id,
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