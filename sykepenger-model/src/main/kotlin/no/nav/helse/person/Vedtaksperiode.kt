package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.FunksjonelleFeilTilVarsler
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingReplayUtført
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrSykepengegrunnlag
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Validation.Companion.validation
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.hendelser.inntektsmelding.DagerFraInntektsmelding
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.memoized
import no.nav.helse.person.Arbeidsgiver.Companion.avventerSøknad
import no.nav.helse.person.Arbeidsgiver.Companion.harNødvendigInntektForVilkårsprøving
import no.nav.helse.person.Arbeidsgiver.Companion.trengerInntektsmelding
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.person.Sykefraværstilfelleeventyr.Companion.bliMed
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
import no.nav.helse.person.Venteårsak.Companion.venterPåInntektsmelding
import no.nav.helse.person.Venteårsak.Companion.venterPåSøknad
import no.nav.helse.person.Venteårsak.Hva.BEREGNING
import no.nav.helse.person.Venteårsak.Hva.GODKJENNING
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.Venteårsak.Hva.INNTEKTSMELDING
import no.nav.helse.person.Venteårsak.Hva.SØKNAD
import no.nav.helse.person.Venteårsak.Hva.UTBETALING
import no.nav.helse.person.Venteårsak.Hvorfor.HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE
import no.nav.helse.person.Venteårsak.Hvorfor.MANGLER_INNTEKT_FOR_VILKÅRSPRØVING_PÅ_ANDRE_ARBEIDSGIVERE
import no.nav.helse.person.Venteårsak.Hvorfor.MANGLER_REFUSJONSOPPLYSNINGER_PÅ_ANDRE_ARBEIDSGIVERE
import no.nav.helse.person.Venteårsak.Hvorfor.MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_ANDRE_ARBEIDSGIVERE
import no.nav.helse.person.Venteårsak.Hvorfor.MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_SAMME_ARBEIDSGIVER
import no.nav.helse.person.Venteårsak.Hvorfor.OVERSTYRING_IGANGSATT
import no.nav.helse.person.Venteårsak.Hvorfor.VIL_OMGJØRES
import no.nav.helse.person.VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.arbeidsavklaringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.arbeidsforhold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.dagpenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.foreldrepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.inntekterForSykepengegrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.institusjonsopphold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.medlemskap
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.omsorgspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.opplæringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.pleiepenger
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Mottatt søknad som delvis overlapper`
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.varsel
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_24
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_38
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
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_24
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_5
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VT_1
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje.Companion.slåSammenForkastedeSykdomstidslinjer
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverUtbetalinger
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Rettighetsvurdering
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjeBuilderException
import no.nav.helse.økonomi.Inntekt
import org.slf4j.LoggerFactory

internal class Vedtaksperiode private constructor(
    private val person: Person,
    private val arbeidsgiver: Arbeidsgiver,
    private val id: UUID,
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    private var tilstand: Vedtaksperiodetilstand,
    private val generasjoner: Generasjoner,
    private val opprettet: LocalDateTime,
    private var oppdatert: LocalDateTime = opprettet,
    private val arbeidsgiverjurist: MaskinellJurist
) : Aktivitetskontekst, Comparable<Vedtaksperiode>, GenerasjonObserver {

    private val sykmeldingsperiode = generasjoner.sykmeldingsperiode()
    private val periode get() = generasjoner.periode()
    private val sykdomstidslinje get() = generasjoner.sykdomstidslinje()
    private val jurist get() = generasjoner.jurist(arbeidsgiverjurist, id)
    private val skjæringstidspunkt get() = person.skjæringstidspunkt(sykdomstidslinje.sykdomsperiode() ?: periode)
    private val vilkårsgrunnlag get() = person.vilkårsgrunnlagFor(skjæringstidspunkt)

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
        jurist: MaskinellJurist
    ) : this(
        person = person,
        arbeidsgiver = arbeidsgiver,
        id = UUID.randomUUID(),
        aktørId = aktørId,
        fødselsnummer = fødselsnummer,
        organisasjonsnummer = organisasjonsnummer,
        tilstand = Start,
        generasjoner = Generasjoner(sykmeldingsperiode, sykdomstidslinje, dokumentsporing, søknad),
        opprettet = LocalDateTime.now(),
        arbeidsgiverjurist = jurist
    ) {
        kontekst(søknad)
        person.vedtaksperiodeOpprettet(id, organisasjonsnummer, periode, skjæringstidspunkt, opprettet)
        generasjoner.førsteGenerasjonOpprettet()
    }

    init {
        generasjoner.addObserver(this)
    }

    internal fun accept(visitor: VedtaksperiodeVisitor) {
        val skjæringstidspunktMemoized = this::skjæringstidspunkt.memoized()
        visitor.preVisitVedtaksperiode(
            this,
            id,
            tilstand,
            opprettet,
            oppdatert,
            periode,
            sykmeldingsperiode,
            skjæringstidspunktMemoized,
            generasjoner.hendelseIder()
        )
        sykdomstidslinje.accept(visitor)
        generasjoner.accept(visitor)
        visitor.postVisitVedtaksperiode(
            this,
            id,
            tilstand,
            opprettet,
            oppdatert,
            periode,
            sykmeldingsperiode,
            skjæringstidspunktMemoized,
            generasjoner.hendelseIder()
        )
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Vedtaksperiode", mapOf("vedtaksperiodeId" to id.toString()))
    }

    internal fun hendelseIder() = generasjoner.dokumentsporing()

    internal fun håndter(sykmelding: Sykmelding) {
        sykmelding.trimLeft(periode.endInclusive)
    }

    private fun <Hendelse: SykdomstidslinjeHendelse> håndterSykdomstidslinjeHendelse(hendelse: Hendelse, håndtering: (Hendelse) -> Unit) {
        if (!hendelse.erRelevant(this.periode)) return hendelse.vurdertTilOgMed(periode.endInclusive)
        kontekst(hendelse)
        hendelse.leggTil(id, generasjoner)
        håndtering(hendelse)
        hendelse.vurdertTilOgMed(periode.endInclusive)
    }

    internal fun håndter(søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
        håndterSykdomstidslinjeHendelse(søknad) {
            søknadHåndtert(søknad)
            tilstand.håndter(this, søknad, arbeidsgivere)
        }
    }

    internal fun håndter(hendelse: OverstyrTidslinje) {
        håndterSykdomstidslinjeHendelse(hendelse) {
            val arbeidsgiverperiodeFørOverstyring = arbeidsgiver.arbeidsgiverperiode(periode())
            tilstand.håndter(this, hendelse)
            val arbeidsgiverperiodeEtterOverstyring = arbeidsgiver.arbeidsgiverperiode(periode())
            if (arbeidsgiverperiodeFørOverstyring != arbeidsgiverperiodeEtterOverstyring) {
                generasjoner.sisteInntektsmeldingId()?.let {
                    person.arbeidsgiveropplysningerKorrigert(
                        PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                            korrigerendeInntektsopplysningId = hendelse.meldingsreferanseId(),
                            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER,
                            korrigertInntektsmeldingId = it
                        ))
                }
            }
        }
    }

    private fun inntektsmeldingHåndtert(inntektsmelding: Inntektsmelding): Boolean {
        if (!inntektsmelding.leggTil(generasjoner)) return true
        person.emitInntektsmeldingHåndtert(inntektsmelding.meldingsreferanseId(), id, organisasjonsnummer)
        return false
    }

    private fun inntektsmeldingHåndtert(dager: DagerFraInntektsmelding): Boolean {
        if (!dager.leggTil(generasjoner)) return true
        person.emitInntektsmeldingHåndtert(dager.meldingsreferanseId(), id, organisasjonsnummer)
        return false
    }

    private fun søknadHåndtert(søknad: Søknad) {
        person.emitSøknadHåndtert(søknad.meldingsreferanseId(), id, organisasjonsnummer)
    }

    internal fun håndter(anmodningOmForkasting: AnmodningOmForkasting) {
        if (!anmodningOmForkasting.erRelevant(id)) return
        kontekst(anmodningOmForkasting)
        tilstand.håndter(this, anmodningOmForkasting)
    }

    private fun etterkomAnmodningOmForkasting(anmodningOmForkasting: AnmodningOmForkasting) {
        if (!arbeidsgiver.kanForkastes(this)) return anmodningOmForkasting.info("Kan ikke etterkomme anmodning om forkasting")
        anmodningOmForkasting.info("Etterkommer anmodning om forkasting")
        forkast(anmodningOmForkasting)
    }

    internal fun håndter(inntektsmeldingReplayUtført: InntektsmeldingReplayUtført) {
        if (!inntektsmeldingReplayUtført.erRelevant(this.id)) return
        kontekst(inntektsmeldingReplayUtført)
        tilstand.håndter(this, inntektsmeldingReplayUtført)
    }

    internal fun håndter(dager: DagerFraInntektsmelding) {
        if (!tilstand.skalHåndtereDager(this, dager) || dager.alleredeHåndtert(generasjoner))
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

    private fun skalHåndtereDager(dager: DagerFraInntektsmelding, strategi: DagerFraInntektsmelding.(Periode) -> Boolean): Boolean {
        val sammenhengende = arbeidsgiver.finnSammenhengendeVedtaksperioder(this)
            .map { it.periode }
            .periode() ?: return false
        if (!strategi(dager, sammenhengende)) return false
        dager.info("Vedtaksperioden $periode håndterer dager fordi den sammenhengende perioden $sammenhengende overlapper med inntektsmelding")
        return true
    }

    private fun håndterDager(dager: DagerFraInntektsmelding) {
        val hendelse = dager.bitAvInntektsmelding(periode)
        if (hendelse != null) {
            oppdaterHistorikk(hendelse)
            person.emitInntektsmeldingHåndtert(dager.meldingsreferanseId(), id, organisasjonsnummer)
        }
        dager.validerArbeidsgiverperiode(periode, finnArbeidsgiverperiode())
    }

    internal fun håndterHistorikkFraInfotrygd(hendelse: Hendelse, infotrygdhistorikk: Infotrygdhistorikk) {
        kontekst(hendelse)
        infotrygdhistorikk.utbetalingshistorikkEtterInfotrygdendring(id, periode, tilstand.type.toString(), organisasjonsnummer, person)
        tilstand.håndter(this, hendelse, infotrygdhistorikk)
    }

    internal fun håndter(
        ytelser: Ytelser,
        infotrygdhistorikk: Infotrygdhistorikk,
        arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger
    ) {
        if (!ytelser.erRelevant(id)) return
        kontekst(ytelser)
        tilstand.håndter(person, arbeidsgiver, this, ytelser, infotrygdhistorikk, arbeidsgiverUtbetalinger)
    }

    internal fun håndter(utbetalingsavgjørelse: Utbetalingsavgjørelse) {
        if (!utbetalingsavgjørelse.relevantVedtaksperiode(id)) return
        if (generasjoner.gjelderIkkeFor(utbetalingsavgjørelse)) return utbetalingsavgjørelse.info("Ignorerer løsning på utbetalingsavgjørelse, utbetalingid på løsningen matcher ikke vedtaksperiodens nåværende utbetaling")
        kontekst(utbetalingsavgjørelse)
        tilstand.håndter(person, arbeidsgiver, this, utbetalingsavgjørelse)
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        if (!vilkårsgrunnlag.erRelevant(id, skjæringstidspunkt)) return
        kontekst(vilkårsgrunnlag)
        tilstand.håndter(this, vilkårsgrunnlag)
    }

    internal fun håndter(simulering: Simulering) {
        if (!simulering.erRelevant(id)) return
        kontekst(simulering)
        tilstand.håndter(this, simulering)
    }

    internal fun håndter(hendelse: UtbetalingHendelse) {
        if (!generasjoner.håndterUtbetalinghendelse(hendelse)) return
        kontekst(hendelse)
        tilstand.håndter(this, hendelse)
    }

    internal fun håndter(hendelse: AnnullerUtbetaling, annullering: Utbetaling) {
        if (generasjoner.hørerIkkeSammenMed(annullering)) return

        person
            .vedtaksperioder { !it.generasjoner.hørerIkkeSammenMed(annullering) }
            .onEach {
                if (it.generasjoner.harAvsluttede()) {
                    person.vedtaksperiodeAnnullert(PersonObserver.VedtaksperiodeAnnullertEvent(it.periode.start, it.periode.endInclusive, it.id, it.organisasjonsnummer))
                }
            }

        kontekst(hendelse)
        hendelse.info("Forkaster denne, og senere perioder, som følge av annullering.")
        forkast(hendelse)
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        if (!påminnelse.erRelevant(id)) return false
        kontekst(påminnelse)
        tilstand.påminnelse(this, påminnelse)
        return true
    }

    internal fun håndter(overstyrSykepengegrunnlag: OverstyrSykepengegrunnlag, alleVedtaksperioder: Iterable<Vedtaksperiode>): Boolean {
        if (!overstyrSykepengegrunnlag.erRelevant(skjæringstidspunkt)) return false
        if (vilkårsgrunnlag?.erArbeidsgiverRelevant(organisasjonsnummer) != true) return false
        kontekst(overstyrSykepengegrunnlag)
        alleVedtaksperioder.filter(VILKÅRSPRØVD_PÅ(skjæringstidspunkt)).forEach {
            it.generasjoner.oppdaterDokumentsporing(overstyrSykepengegrunnlag.dokumentsporing())
        }
        overstyrSykepengegrunnlag.vilkårsprøvEtterNyInformasjonFraSaksbehandler(person, jurist)
        return true
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

    private fun manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag() =
        person.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag(skjæringstidspunkt)

    private fun harTilstrekkeligInformasjonTilUtbetaling(hendelse: IAktivitetslogg) =
        arbeidsgiver.harTilstrekkeligInformasjonTilUtbetaling(skjæringstidspunkt, periode, hendelse)

    private fun låsOpp() = arbeidsgiver.låsOpp(periode)
    private fun lås() = arbeidsgiver.lås(periode)

    internal fun kanForkastes(arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
        if (tilstand == Start) return true // For vedtaksperioder som forkates på "direkten"
        if (!generasjoner.kanForkastes(arbeidsgiverUtbetalinger)) return false
        val overlappendeUtbetalinger = arbeidsgiverUtbetalinger.filter { it.overlapperMed(periode) }
        val kanForkastes = Utbetaling.kanForkastes(overlappendeUtbetalinger, arbeidsgiverUtbetalinger)
        if (kanForkastes) return true
        val overlappendeOppdrag = arbeidsgiverUtbetalinger.filter { it.overlapperMedUtbetaling(periode) }
        if (!Utbetaling.kanForkastes(overlappendeOppdrag, arbeidsgiverUtbetalinger)) return false // forkaster ikke om perioden har utbetalinger
        // om perioden kun er auu, og er utbetalt i infotrygd, så er det greit
        if (tilstand != AvsluttetUtenUtbetaling) return false
        // auuen overlapper ikke med et oppdrag, men overlapper med perioden til en aktiv utbetaling
        // I utgangspunktet må vi anta at auuen derfor påvirker utfallet av arbeidsgiverperiode-beregningen, og kan ikke forkastes
        // unntak er dersom perioden overlapper med en infotrygdutbetaling, eller dersom det foreligger en utbetaling i Infotrygd mellom
        // auuen og første oppdragslinje/vedtak
        val nesteVedtak = arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiode(finnArbeidsgiverperiode()).firstOrNull { it.tilstand != AvsluttetUtenUtbetaling }
        val periodeSomKanVæreUtbetaltIInfotrygd = if (nesteVedtak == null) this.periode else this.periode.oppdaterTom(nesteVedtak.periode.start.forrigeDag)
        return person.erBetaltInfotrygd(periodeSomKanVæreUtbetaltIInfotrygd)
    }

    internal fun forkast(hendelse: Hendelse, utbetalinger: List<Utbetaling>): VedtaksperiodeForkastetEventBuilder? {
        if (!kanForkastes(utbetalinger)) return null
        kontekst(hendelse)
        hendelse.info("Forkaster vedtaksperiode: %s", this.id.toString())
        this.generasjoner.forkast(hendelse)
        val arbeidsgiverperiodeHensyntarForkastede = finnArbeidsgiverperiodeHensyntarForkastede()
        val trengerArbeidsgiveropplysninger = arbeidsgiverperiodeHensyntarForkastede?.forventerOpplysninger(periode) ?: false
        val sykmeldingsperioder = sykmeldingsperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiodeHensyntarForkastede)
        val vedtaksperiodeForkastetEventBuilder = VedtaksperiodeForkastetEventBuilder(tilstand.type, trengerArbeidsgiveropplysninger, sykmeldingsperioder)
        tilstand(hendelse, TilInfotrygd)
        return vedtaksperiodeForkastetEventBuilder
    }

    private fun sykmeldingsperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?): List<Periode> {
        val forkastedeVedtaksperioder = arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiodeInkludertForkastede(arbeidsgiverperiode)
        return (forkastedeVedtaksperioder.map { it.sykmeldingsperiode }.filter { it.start < sykmeldingsperiode.endInclusive } + listOf(sykmeldingsperiode)).distinct()
    }

    internal inner class VedtaksperiodeForkastetEventBuilder(private val gjeldendeTilstand: TilstandType, private val trengerArbeidsgiveropplysninger: Boolean, private val sykmeldingsperioder: List<Periode>) {
        internal fun buildAndEmit() {
            person.vedtaksperiodeForkastet(
                PersonObserver.VedtaksperiodeForkastetEvent(
                    fødselsnummer = fødselsnummer,
                    aktørId = aktørId,
                    organisasjonsnummer = organisasjonsnummer,
                    vedtaksperiodeId = id,
                    gjeldendeTilstand = gjeldendeTilstand,
                    hendelser = hendelseIder(),
                    fom = periode.start,
                    tom = periode.endInclusive,
                    forlengerPeriode = person.nåværendeVedtaksperioder { (it.periode.overlapperMed(periode) || it.periode.erRettFør(periode)) }.isNotEmpty(),
                    harPeriodeInnenfor16Dager = person.nåværendeVedtaksperioder { påvirkerArbeidsgiverperioden(it) }.isNotEmpty(),
                    trengerArbeidsgiveropplysninger = trengerArbeidsgiveropplysninger,
                    sykmeldingsperioder = sykmeldingsperioder
                )
            )
        }
    }

    private fun forkast(hendelse: Hendelse) {
        if (!arbeidsgiver.kanForkastes(this)) return hendelse.info("Kan ikke etterkomme forkasting")
        person.søppelbøtte(hendelse, TIDLIGERE_OG_ETTERGØLGENDE(this))
    }

    private fun revurderTidslinje(hendelse: OverstyrTidslinje) {
        oppdaterHistorikk(hendelse)
        igangsettOverstyringAvTidslinje(hendelse)
    }
    private fun revurderLåstTidslinje(hendelse: OverstyrTidslinje) {
        låsOpp()
        oppdaterHistorikk(hendelse)
        lås()
        igangsettOverstyringAvTidslinje(hendelse)
    }

    private fun igangsettOverstyringAvTidslinje(hendelse: OverstyrTidslinje) {
        val vedtaksperiodeTilRevurdering = arbeidsgiver.finnVedtaksperiodeFør(this)
            ?.takeIf { nyArbeidsgiverperiodeEtterEndring(it) } ?: this
        person.igangsettOverstyring(Revurderingseventyr.sykdomstidslinje(hendelse, vedtaksperiodeTilRevurdering.skjæringstidspunkt, vedtaksperiodeTilRevurdering.periode))
    }

    private fun nyArbeidsgiverperiodeEtterEndring(other: Vedtaksperiode): Boolean {
        if (this.generasjoner.erUtbetaltPåForskjelligeUtbetalinger(other.generasjoner)) return false
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
        event: Hendelse,
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

    private fun oppdaterHistorikk(hendelse: SykdomstidslinjeHendelse) {
        val rettEtterFørEndring = arbeidsgiver.finnVedtaksperiodeRettEtter(this)

        oppdaterHistorikk(hendelse as SykdomshistorikkHendelse)

        lagreTidsnæreopplysninger(hendelse)
        val periodeEtter = rettEtterFørEndring ?: arbeidsgiver.finnVedtaksperiodeRettEtter(this)
        periodeEtter?.lagreTidsnæreopplysninger(hendelse)
    }

    private fun oppdaterHistorikk(hendelse: SykdomshistorikkHendelse) {
        generasjoner.håndterEndring(person, arbeidsgiver, hendelse)
    }

    private fun lagreTidsnæreopplysninger(hendelse: IAktivitetslogg) {
        val periodeFør = arbeidsgiver.finnVedtaksperiodeFør(this)?.takeUnless { it.erVedtaksperiodeRettFør(this) }
        val oppholdsperiodeMellom = periodeFør?.sykdomstidslinje?.oppholdsperiodeMellom(this.sykdomstidslinje)

        generasjoner.lagreTidsnæreInntekter(arbeidsgiver, skjæringstidspunkt, aktivitetsloggkopi(hendelse), oppholdsperiodeMellom)
    }

    private fun håndterSøknad(søknad: Søknad, nesteTilstand: () -> Vedtaksperiodetilstand? = { null }) {
        oppdaterHistorikk(søknad)
        søknad.valider(vilkårsgrunnlag, jurist)
        if (manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) søknad.funksjonellFeil(RV_SV_2)
        if (søknad.harFunksjonelleFeilEllerVerre()) return forkast(søknad)
        nesteTilstand()?.also { tilstand(søknad, it) }
    }

    private fun håndterOverlappendeSøknad(søknad: Søknad, nesteTilstand: Vedtaksperiodetilstand? = null) {
        if (søknad.delvisOverlappende(periode)) {
            generasjoner.oppdaterDokumentsporing(søknad.dokumentsporing())
            søknad.funksjonellFeil(`Mottatt søknad som delvis overlapper`)
            return forkast(søknad)
        }
        søknad.info("Håndterer overlappende søknad")
        håndterSøknad(søknad) { nesteTilstand }
    }

    private fun håndterLåstOverlappendeSøknadRevurdering(søknad: Søknad) {
        håndterOverlappendeSøknadRevurdering(søknad) {
            låsOpp()
            oppdaterHistorikk(søknad)
            lås()
        }
    }
    private fun håndterOverlappendeSøknadRevurdering(søknad: Søknad, oppdaterHistorikkBlock: (SykdomstidslinjeHendelse) -> Unit = ::oppdaterHistorikk) {
        if (søknad.delvisOverlappende(periode)) return søknad.funksjonellFeil(`Mottatt søknad som delvis overlapper`)
        if (søknad.sendtTilGosys()) return søknad.funksjonellFeil(RV_SØ_30)
        if (søknad.utenlandskSykmelding()) return søknad.funksjonellFeil(RV_SØ_29)
        else {
            søknad.valider(vilkårsgrunnlag, jurist)
            søknad.info("Søknad har trigget en revurdering")
            oppdaterHistorikkBlock(søknad)
        }

        person.igangsettOverstyring(Revurderingseventyr.korrigertSøknad(søknad, skjæringstidspunkt, periode))
    }

    private fun håndterKorrigerendeInntektsmelding(dager: DagerFraInntektsmelding, håndterLås: (() -> Unit) -> Unit = { it() }) {
        dager.valider(periode)
        if (dager.harFunksjonelleFeilEllerVerre()) return
        val korrigertInntektsmeldingId = generasjoner.sisteInntektsmeldingId()
        val opprinneligAgp = finnArbeidsgiverperiode()
        if (dager.erKorrigeringForGammel(opprinneligAgp)) {
            inntektsmeldingHåndtert(dager)
        }
        else {
            håndterLås { håndterDager(dager) }
        }
        val nyAgp = finnArbeidsgiverperiode()
        if (opprinneligAgp != null && !opprinneligAgp.klinLik(nyAgp)) {
            dager.varsel(RV_IM_24, "Ny agp er utregnet til å være ulik tidligere utregnet agp i ${tilstand.type.name}")
            korrigertInntektsmeldingId?.let {
                person.arbeidsgiveropplysningerKorrigert(
                    PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                        korrigerendeInntektsopplysningId = dager.meldingsreferanseId(),
                        korrigerendeInntektektsopplysningstype = Inntektsopplysningstype.INNTEKTSMELDING,
                        korrigertInntektsmeldingId = it
                    ))
            }
        }
        person.igangsettOverstyring(Revurderingseventyr.korrigertInntektsmeldingArbeidsgiverperiode(dager, skjæringstidspunkt = skjæringstidspunkt, periodeForEndring = periode))
        // setter kontekst tilbake siden igangsettelsen over kan endre på kontekstene
        kontekst(dager)
    }

    private fun håndterVilkårsgrunnlag(vilkårsgrunnlag: Vilkårsgrunnlag, nesteTilstand: Vedtaksperiodetilstand) {
        val sykepengegrunnlag = vilkårsgrunnlag.avklarSykepengegrunnlag(person, jurist)
        sykepengegrunnlag.leggTil(generasjoner, organisasjonsnummer) { inntektsmeldingId ->
            person.emitInntektsmeldingHåndtert(inntektsmeldingId, id, organisasjonsnummer)
        }
        vilkårsgrunnlag.valider(sykepengegrunnlag, jurist)
        val grunnlagsdata = vilkårsgrunnlag.grunnlagsdata()
        grunnlagsdata.validerFørstegangsvurdering(vilkårsgrunnlag)
        person.lagreVilkårsgrunnlag(grunnlagsdata)
        vilkårsgrunnlag.info("Vilkårsgrunnlag vurdert")
        if (vilkårsgrunnlag.harFunksjonelleFeilEllerVerre()) return forkast(vilkårsgrunnlag)
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
        arbeidsforhold(hendelse, skjæringstidspunkt)
        medlemskap(hendelse, skjæringstidspunkt, periode.start, periode.endInclusive)
    }

    private fun trengerArbeidsgiveropplysninger(hendelse: IAktivitetslogg): Boolean {
        val arbeidsgiverperiode = finnArbeidsgiverperiode() ?: return false
        if (harTilstrekkeligInformasjonTilUtbetaling(hendelse)) return false
        if (!arbeidsgiverperiode.forventerOpplysninger(periode)) return false

        val fastsattInntekt = person.vilkårsgrunnlagFor(skjæringstidspunkt)?.inntekt(arbeidsgiver.organisasjonsnummer())
        val vedtaksperioderKnyttetTilArbeidsgiverperiode = arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode)
        val sykdomstidslinjeKnyttetTilArbeidsgiverperiode = vedtaksperioderKnyttetTilArbeidsgiverperiode
            .map { it.sykdomstidslinje }
            .merge()

        val forespurteOpplysninger = listOfNotNull(
            forespurtInntekt(fastsattInntekt),
            forespurtFastsattInntekt(fastsattInntekt),
            forespurtRefusjon(fastsattInntekt),
            forespurtArbeidsgiverperiode(arbeidsgiverperiode)
        )

        val førsteFraværsdager = person.førsteFraværsdager(skjæringstidspunkt)

        person.trengerArbeidsgiveropplysninger(
            PersonObserver.TrengerArbeidsgiveropplysningerEvent(
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = id,
                skjæringstidspunkt = skjæringstidspunkt,
                sykmeldingsperioder = relevanteSykmeldingsperioder(arbeidsgiverperiode, vedtaksperioderKnyttetTilArbeidsgiverperiode),
                egenmeldingsperioder = sykdomstidslinjeKnyttetTilArbeidsgiverperiode.egenmeldingerFraSøknad(),
                førsteFraværsdager = førsteFraværsdager,
                forespurteOpplysninger = forespurteOpplysninger
            )
        )
        return true
    }

    private fun trengerIkkeArbeidsgiveropplysninger() {
        person.trengerIkkeArbeidsgiveropplysninger(
            PersonObserver.TrengerIkkeArbeidsgiveropplysningerEvent(
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = id
            )
        )
    }

    private fun forespurtInntekt(fastsattInntekt: Inntekt?): PersonObserver.Inntekt? {
        if (fastsattInntekt != null) return null
        val inntektForrigeSkjæringstidspunkt = person.skjæringstidspunkter()
            .filter { it != skjæringstidspunkt }
            .maxOrNull()
            ?.let { person.vilkårsgrunnlagFor(it)?.inntektsdata(it, organisasjonsnummer) }

        return PersonObserver.Inntekt(forslag = inntektForrigeSkjæringstidspunkt)
    }

    private fun forespurtFastsattInntekt(fastsattInntekt: Inntekt?): PersonObserver.FastsattInntekt? =
        fastsattInntekt?.let(PersonObserver::FastsattInntekt)

    private fun forespurtRefusjon(fastsattInntekt: Inntekt?): PersonObserver.Refusjon {
        if (fastsattInntekt != null) {
            val refusjonsopplysninger = person
                .vilkårsgrunnlagFor(skjæringstidspunkt)
                ?.overlappendeEllerSenereRefusjonsopplysninger(arbeidsgiver.organisasjonsnummer(), periode())
                .orEmpty()
            return PersonObserver.Refusjon(forslag = refusjonsopplysninger)

        } else {
            val forrigeSkjæringstidspunkt = person.skjæringstidspunkter()
                .filter { it != skjæringstidspunkt }
                .maxOrNull()

            val refusjonsopplysninger = forrigeSkjæringstidspunkt?.let { person
                .vilkårsgrunnlagFor(forrigeSkjæringstidspunkt)
                ?.overlappendeEllerSenereRefusjonsopplysninger(arbeidsgiver.organisasjonsnummer(), periode())
            } ?: emptyList()

            return PersonObserver.Refusjon(forslag = refusjonsopplysninger)
        }
    }

    private fun forespurtArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?): PersonObserver.Arbeidsgiverperiode? {
        if (trengerArbeidsgiverperiode(arbeidsgiverperiode)) return PersonObserver.Arbeidsgiverperiode
        return null
    }

    private fun trengerArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?) =
        arbeidsgiverperiode != null && arbeidsgiverperiode.forventerArbeidsgiverperiodeopplysning(periode)
            && harIkkeFåttOpplysningerOmArbeidsgiverperiode(arbeidsgiverperiode)

    private fun harIkkeFåttOpplysningerOmArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?) =
        arbeidsgiver.vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode)
            .all { it.generasjoner.trengerArbeidsgiverperiode() }

    private fun relevanteSykmeldingsperioder(arbeidsgiverperiode: Arbeidsgiverperiode?, vedtaksperioder: List<Vedtaksperiode>): List<Periode> {
        if (trengerArbeidsgiverperiode(arbeidsgiverperiode)) return vedtaksperioder
            .filter { it.sykmeldingsperiode.start < periode().endInclusive }
            .map { it.sykmeldingsperiode }
        return listOf(sykmeldingsperiode)
    }

    private fun trengerInntektsmelding() {
        if (!forventerInntekt()) return
        if (arbeidsgiver.finnVedtaksperiodeRettFør(this) != null) return
        this.person.trengerInntektsmelding(
            PersonObserver.ManglendeInntektsmeldingEvent(
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = id,
                fom = this.periode.start,
                tom = this.periode.endInclusive,
                søknadIder = generasjoner.søknadIder()
            )
        )
    }

    private fun trengerIkkeInntektsmelding() {
        this.person.trengerIkkeInntektsmelding(
            PersonObserver.TrengerIkkeInntektsmeldingEvent(
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = id,
                fom = this.periode.start,
                tom = this.periode.endInclusive,
                søknadIder = generasjoner.søknadIder()
            )
        )
    }

    internal val sammenhengendePeriode get() = checkNotNull(arbeidsgiver.finnSammenhengendeVedtaksperioder(this)
        .map { it.periode }
        .periode())

    private fun trengerInntektsmeldingReplay() {
        person.inntektsmeldingReplay(id, skjæringstidspunkt, organisasjonsnummer, sammenhengendePeriode)
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
            hendelser = hendelseIder(),
            makstid = person.makstid(this, LocalDateTime.now()),
            fom = periode.start,
            tom = periode.endInclusive
        )

        person.vedtaksperiodeEndret(event)
    }

    override fun avsluttetUtenVedtak(
        hendelse: IAktivitetslogg,
        generasjonId: UUID,
        tidsstempel: LocalDateTime,
        periode: Periode,
        dokumentsporing: Set<UUID>
    ) {
        person.avsluttetUtenVedtak(PersonObserver.AvsluttetUtenVedtakEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = id,
            generasjonId = generasjonId,
            periode = periode,
            hendelseIder = hendelseIder(),
            skjæringstidspunkt = skjæringstidspunkt,
            avsluttetTidspunkt = tidsstempel
        ))
        person.gjenopptaBehandling(hendelse)
    }

    override fun vedtakIverksatt(
        hendelse: IAktivitetslogg,
        generasjonId: UUID,
        tidsstempel: LocalDateTime,
        periode: Periode,
        dokumentsporing: Set<UUID>,
        utbetalingId: UUID,
        vedtakFattetTidspunkt: LocalDateTime
    ) {
        val vilkårsgrunnlag = checkNotNull(vilkårsgrunnlag) { "Særdeles besynderlig at vi ikke har vilkårsgrunnlag nå som vedtaket iverksettes!" }
        val builder = VedtakFattetBuilder(fødselsnummer, aktørId, organisasjonsnummer, id, generasjonId, periode, hendelseIder(), skjæringstidspunkt)
        val harPeriodeRettFør = arbeidsgiver.finnVedtaksperiodeRettFør(this) != null
        this.finnArbeidsgiverperiode()?.tags(this.periode, builder, harPeriodeRettFør)
        builder.utbetalingId(utbetalingId)
        builder.utbetalingVurdert(vedtakFattetTidspunkt)
        vilkårsgrunnlag.build(builder)
        person.avsluttetMedVedtak(builder.result())
        person.gjenopptaBehandling(hendelse)
    }

    override fun generasjonLukket(generasjonId: UUID) {
        person.generasjonLukket(PersonObserver.GenerasjonLukketEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = id,
            generasjonId = generasjonId
        ))
    }

    override fun generasjonForkastet(generasjonId: UUID) {
        person.generasjonForkastet(PersonObserver.GenerasjonForkastetEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = id,
            generasjonId = generasjonId
        ))
    }

    override fun nyGenerasjon(
        id: UUID,
        meldingsreferanseId: UUID,
        innsendt: LocalDateTime,
        registert: LocalDateTime,
        avsender: Avsender,
        type: PersonObserver.GenerasjonOpprettetEvent.Type
    ) {
        val nyGenerasjon = PersonObserver.GenerasjonOpprettetEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = this.id,
            generasjonId = id,
            type = type,
            kilde = PersonObserver.GenerasjonOpprettetEvent.Kilde(meldingsreferanseId, innsendt, registert, avsender)
        )
        person.nyGenerasjon(nyGenerasjon)
    }

    private fun høstingsresultater(hendelse: ArbeidstakerHendelse, simuleringtilstand: Vedtaksperiodetilstand, godkjenningtilstand: Vedtaksperiodetilstand) = when {
        generasjoner.harUtbetalinger() -> tilstand(hendelse, simuleringtilstand) {
            hendelse.info("""Saken oppfyller krav for behandling, settes til "Avventer simulering"""")
        }
        else -> tilstand(hendelse, godkjenningtilstand) {
            hendelse.info("""Saken oppfyller krav for behandling, settes til "Avventer godkjenning" fordi ingenting skal utbetales""")
        }
    }

    private fun Vedtaksperiodetilstand.påminnelse(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
        if (!påminnelse.gjelderTilstand(type)) return vedtaksperiode.person.vedtaksperiodeIkkePåminnet(id, organisasjonsnummer, type)
        vedtaksperiode.person.vedtaksperiodePåminnet(id, organisasjonsnummer, påminnelse)
        if (påminnelse.nåddMakstid(vedtaksperiode, person)) return håndterMakstid(vedtaksperiode, påminnelse)
        håndter(vedtaksperiode, påminnelse)
    }

    override fun toString() =
        "${this.periode.start} - ${this.periode.endInclusive} (${this.tilstand::class.simpleName})"

    private fun finnArbeidsgiverperiode() = arbeidsgiver.arbeidsgiverperiode(periode)

    private fun finnArbeidsgiverperiodeHensyntarForkastede() = arbeidsgiver.arbeidsgiverperiodeInkludertForkastet(periode, sykdomstidslinje)

    private fun forventerInntekt(subsumsjonObserver: SubsumsjonObserver = jurist): Boolean {
        return Arbeidsgiverperiode.forventerInntekt(finnArbeidsgiverperiode(), periode, sykdomstidslinje, subsumsjonObserver)
    }

    private fun loggInnenforArbeidsgiverperiode() {
        if (forventerInntekt()) return
        sikkerlogg.info(
            "Vedtaksperioden {} for {} er egentlig innenfor arbeidsgiverperioden ved {}",
            keyValue("vedtaksperiodeId", id), keyValue("fnr", fødselsnummer), keyValue("tilstand", tilstand.type)
        )
    }

    private fun trengerGodkjenning(hendelse: IAktivitetslogg) {
        generasjoner.godkjenning(hendelse, erForlengelse(), arbeidsgiver.kanForkastes(this))
    }

    internal fun gjenopptaBehandling(hendelse: Hendelse, arbeidsgivere: Iterable<Arbeidsgiver>) {
        hendelse.kontekst(arbeidsgiver)
        kontekst(hendelse)
        hendelse.info("Forsøker å gjenoppta $this")
        tilstand.gjenopptaBehandling(this, arbeidsgivere, hendelse)
    }


    internal fun igangsettOverstyring(revurdering: Revurderingseventyr) {
        if (revurdering.ikkeRelevant(skjæringstidspunkt, periode)) return
        kontekst(revurdering)
        tilstand.igangsettOverstyring(this, revurdering)
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
        kontekst(inntektsmelding)
        tilstand.håndtertInntektPåSkjæringstidspunktet(this, inntektsmelding)
    }

    private fun vedtaksperiodeVenter(venterPå: Vedtaksperiode) {
        val venteårsak = person.venteårsak(venterPå) ?: return
        val builder = VedtaksperiodeVenter.Builder()
        builder.venterPå(venterPå.id, venterPå.organisasjonsnummer, venteårsak)
        builder.venter(
            vedtaksperiodeId = id,
            orgnummer = organisasjonsnummer,
            ventetSiden = oppdatert,
            venterTil = venterTil(venterPå)
        )
        builder.hendelseIder(hendelseIder())
        emitVedtaksperiodeVenter(builder.build())
    }

    private fun venterTil(venterPå: Vedtaksperiode) =
        if (id == venterPå.id) person.makstid(this, oppdatert)
        else minOf(person.makstid(this, oppdatert), person.makstid(venterPå, venterPå.oppdatert))

    internal fun venteårsak(arbeidsgivere: List<Arbeidsgiver>) =
        tilstand.venteårsak(this, arbeidsgivere)

    internal fun makstid(tilstandsendringstidspunkt: LocalDateTime, arbeidsgivere: List<Arbeidsgiver>) =
        tilstand.makstid(tilstandsendringstidspunkt, this, arbeidsgivere)

    fun slutterEtter(dato: LocalDate) = periode.slutterEtter(dato)

    private fun aktivitetsloggkopi(hendelse: IAktivitetslogg) =
        hendelse.barn().also { kopi ->
            this.kontekst(kopi)
        }

    private fun kalkulerUtbetalinger(aktivitetslogg: IAktivitetslogg, ytelser: Ytelser, infotrygdhistorikk: Infotrygdhistorikk, arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger): Boolean {
        val vilkårsgrunnlag = requireNotNull(vilkårsgrunnlag)
        aktivitetslogg.kontekst(vilkårsgrunnlag)

        person.valider(aktivitetslogg, vilkårsgrunnlag, organisasjonsnummer, skjæringstidspunkt)
        infotrygdhistorikk.valider(aktivitetslogg, periode, skjæringstidspunkt, organisasjonsnummer)
        val maksimumSykepenger = beregnUtbetalinger(aktivitetslogg, arbeidsgiverUtbetalinger) ?: return false
        ytelser.valider(periode, skjæringstidspunkt, maksimumSykepenger.maksdato, erForlengelse())
        return !aktivitetslogg.harFunksjonelleFeilEllerVerre()
    }

    private fun lagNyUtbetaling(arbeidsgiverSomBeregner: Arbeidsgiver, hendelse: IAktivitetslogg, utbetalingstidslinje: Utbetalingstidslinje, maksimumSykepenger: Rettighetsvurdering) {
        val grunnlagsdata = checkNotNull(vilkårsgrunnlag) {
            "krever vilkårsgrunnlag for ${skjæringstidspunkt}, men har ikke. Lages det utbetaling for en periode som ikke skal lage utbetaling?"
        }
        generasjoner.nyUtbetaling(this.id, this.fødselsnummer, this.arbeidsgiver, grunnlagsdata, periode, hendelse, maksimumSykepenger, utbetalingstidslinje)
        loggDersomViTrekkerTilbakePengerPåAnnenArbeidsgiver(arbeidsgiverSomBeregner, hendelse)
    }

    private fun loggDersomViTrekkerTilbakePengerPåAnnenArbeidsgiver(arbeidsgiverSomBeregner: Arbeidsgiver, aktivitetslogg: IAktivitetslogg) {
        if (!generasjoner.trekkerTilbakePenger()) return
        if (this.arbeidsgiver === arbeidsgiverSomBeregner && !person.blitt6GBegrensetSidenSist(skjæringstidspunkt)) return
        aktivitetslogg.info("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere")
    }

    private fun utbetalingsperioder(): List<Vedtaksperiode> {
        val skjæringstidspunktet = this.skjæringstidspunkt
        // lag utbetaling for seg selv + andre overlappende perioder hos andre arbeidsgivere (som ikke er utbetalt/avsluttet allerede)
        return person
            .nåværendeVedtaksperioder { it.erKandidatForUtbetaling(this, skjæringstidspunktet)}
            .filter { it.generasjoner.klarForUtbetaling() }
    }

    private fun erKandidatForUtbetaling(periodeSomBeregner: Vedtaksperiode, skjæringstidspunktet: LocalDate): Boolean {
        if (this === periodeSomBeregner) return true
        if (!forventerInntekt(NullObserver)) return false
        return this.periode.overlapperMed(periodeSomBeregner.periode) && skjæringstidspunktet == this.skjæringstidspunkt && !this.tilstand.erFerdigBehandlet
    }

    private fun beregnUtbetalinger(hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger): Rettighetsvurdering? {
        val sisteTomKlarTilBehandling = beregningsperioderFørstegangsbehandling(person, this)
        val beregningsperiode = this.finnArbeidsgiverperiode()?.periode(sisteTomKlarTilBehandling) ?: this.periode

        val utbetalingsperioder = utbetalingsperioder()
        check(utbetalingsperioder.all { it.skjæringstidspunkt == this.skjæringstidspunkt }) {
            "ugyldig situasjon: skal beregne utbetaling for vedtaksperioder med ulike skjæringstidspunkter"
        }
        try {
            val (maksimumSykepenger, tidslinjerPerArbeidsgiver) = arbeidsgiverUtbetalinger.beregn(beregningsperiode, this.periode, hendelse, this.jurist)
            utbetalingsperioder.forEach { other ->
                val utbetalingstidslinje = tidslinjerPerArbeidsgiver.getValue(other.arbeidsgiver)
                other.lagNyUtbetaling(this.arbeidsgiver, other.aktivitetsloggkopi(hendelse), utbetalingstidslinje, maksimumSykepenger)
            }
            return maksimumSykepenger
        } catch (err: UtbetalingstidslinjeBuilderException) {
            err.logg(hendelse)
        }
        return null
    }

    internal fun sykefraværsfortelling(list: List<Sykefraværstilfelleeventyr>) =
        list.bliMed(this.id, this.organisasjonsnummer, this.periode, this.skjæringstidspunkt)

    // Gang of four State pattern
    internal sealed interface Vedtaksperiodetilstand : Aktivitetskontekst {
        val type: TilstandType
        val erFerdigBehandlet: Boolean get() = false

        fun håndterRevurdering(hendelse: Hendelse, block: () -> Unit) {
            if (hendelse !is PersonHendelse) return block()
            FunksjonelleFeilTilVarsler.wrap(hendelse, block)
        }
        fun håndterFørstegangsbehandling(hendelse: Hendelse, vedtaksperiode: Vedtaksperiode, block: () -> Unit) {
            if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode)) return block()
            // Om førstegangsbehandling ikke kan forkastes (typisk Out of Order/ omgjøring av AUU) så håndteres det som om det er en revurdering
            håndterRevurdering(hendelse, block)
        }

        fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {}

        fun makstid(
            tilstandsendringstidspunkt: LocalDateTime,
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: List<Arbeidsgiver>
        ): LocalDateTime = LocalDateTime.MAX

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
        fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Venteårsak?

        // venter du på noe?
        fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode)

        fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>)

        fun håndter(vedtaksperiode: Vedtaksperiode, inntektsmeldingReplayUtført: InntektsmeldingReplayUtført) {}
        fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            dager.skalHåndteresAv(vedtaksperiode.periode)
        fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            vedtaksperiode.håndterKorrigerendeInntektsmelding(dager)
        }

        fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {}

        fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
            vilkårsgrunnlag.info("Forventet ikke vilkårsgrunnlag i %s".format(type.name))
        }

        fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            val kanForkastes = vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode)
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
            infotrygdhistorikk: Infotrygdhistorikk,
            arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger
        ) {
            ytelser.info("Forventet ikke ytelsehistorikk i %s".format(type.name))
        }

        fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            utbetalingsavgjørelse: Utbetalingsavgjørelse
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

        fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: Iterable<Arbeidsgiver>,
            hendelse: Hendelse
        ) {
            hendelse.info("Tidligere periode ferdigbehandlet, men gjør ingen tilstandsendring.")
        }

        fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            revurdering.inngåSomRevurdering(vedtaksperiode, vedtaksperiode.periode)
            vedtaksperiode.generasjoner.sikreNyGenerasjon(revurdering)
            vedtaksperiode.tilstand(revurdering, AvventerRevurdering)
        }

        fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {}

    }

    internal data object Start : Vedtaksperiodetilstand {
        override val type = START
        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            HJELP.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) { }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            val harSenereUtbetalinger = vedtaksperiode.person.vedtaksperioder(NYERE_SKJÆRINGSTIDSPUNKT_MED_UTBETALING(vedtaksperiode)).isNotEmpty()
            val harSenereAUU = vedtaksperiode.person.vedtaksperioder(NYERE_SKJÆRINGSTIDSPUNKT_UTEN_UTBETALING(vedtaksperiode)).isNotEmpty()
            if (harSenereUtbetalinger || harSenereAUU) {
                søknad.varsel(RV_OO_1)
            }
            vedtaksperiode.arbeidsgiver.vurderOmSøknadKanHåndteres(søknad, vedtaksperiode, arbeidsgivere)
            vedtaksperiode.håndterSøknad(søknad) {
                vedtaksperiode.person.igangsettOverstyring(Revurderingseventyr.nyPeriode(søknad, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode))
                val rettFør = vedtaksperiode.arbeidsgiver.finnVedtaksperiodeRettFør(vedtaksperiode)
                when {
                    rettFør != null && rettFør.tilstand !in setOf(AvsluttetUtenUtbetaling, AvventerInfotrygdHistorikk, AvventerInntektsmelding) -> AvventerBlokkerendePeriode
                    else -> AvventerInfotrygdHistorikk
                }
            }

            søknad.info("Fullført behandling av søknad")
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {}
    }

    internal data object AvventerInfotrygdHistorikk : Vedtaksperiodetilstand {
        override val type = AVVENTER_INFOTRYGDHISTORIKK
        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.person.trengerHistorikkFraInfotrygd(hendelse, vedtaksperiode)
        }
        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {}

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = null
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: Iterable<Arbeidsgiver>,
            hendelse: Hendelse
        ) {
            vedtaksperiode.person.trengerHistorikkFraInfotrygd(hendelse, vedtaksperiode)
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
                    if (vedtaksperiode.vilkårsgrunnlag is InfotrygdVilkårsgrunnlag) {
                        info("Oppdaget at perioden startet i infotrygd")
                        vedtaksperiode.tilstand(hendelse, AvventerBlokkerendePeriode)
                    } else {
                        vedtaksperiode.tilstand(hendelse, AvventerInntektsmelding)
                    }
                }
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            vedtaksperiode.person.trengerHistorikkFraInfotrygd(påminnelse, vedtaksperiode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad)
        }
        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {}

        override fun igangsettOverstyring(
            vedtaksperiode: Vedtaksperiode,
            revurdering: Revurderingseventyr
        ) {
        }
    }

    internal data object AvventerRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.generasjoner.forkastUtbetaling(hendelse)
            if (!vedtaksperiode.arbeidsgiver.harNødvendigInntektForVilkårsprøving(vedtaksperiode.skjæringstidspunkt)) {
                hendelse.info("Revurdering førte til at sykefraværstilfellet trenger inntektsmelding")
                vedtaksperiode.trengerInntektsmelding()
            }
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Venteårsak? {
            if (!harNødvendigInntektForVilkårsprøving(vedtaksperiode))
                return INNTEKTSMELDING fordi MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_SAMME_ARBEIDSGIVER
            if (!harNødvendigInntektForVilkårsprøving(vedtaksperiode, arbeidsgivere)) {
                loggDersomStuckRevurdering(vedtaksperiode, arbeidsgivere)
                return INNTEKTSMELDING fordi MANGLER_TILSTREKKELIG_INFORMASJON_TIL_UTBETALING_ANDRE_ARBEIDSGIVERE
            }
            if (arbeidsgivere.trengerInntektsmelding(vedtaksperiode.periode))
                return INNTEKTSMELDING fordi MANGLER_REFUSJONSOPPLYSNINGER_PÅ_ANDRE_ARBEIDSGIVERE
            return null
        }

        override fun igangsettOverstyring(
            vedtaksperiode: Vedtaksperiode,
            revurdering: Revurderingseventyr
        ) {
            super.igangsettOverstyring(vedtaksperiode, revurdering)
            vedtaksperiode.generasjoner.forkastUtbetaling(revurdering)
        }

        private fun loggDersomStuckRevurdering(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) {
            val vilkårsgrunnlag = vedtaksperiode.person.vilkårsgrunnlagFor(vedtaksperiode.skjæringstidspunkt) ?: return
            val arbeidsgivereISykepengegrunnlag = arbeidsgivere.filter { vilkårsgrunnlag.erArbeidsgiverRelevant(it.organisasjonsnummer()) }
            if (harNødvendigInntektForVilkårsprøving(vedtaksperiode, arbeidsgivere = arbeidsgivereISykepengegrunnlag)) {
                sikkerlogg.info("Periode sitter fast i revurdering pga ny arbeidsgiver som ikke er i sykepengegrunnlaget. {}, {}", kv("vedtaksperiodeId", "${vedtaksperiode.id}"), kv("fødselsnummer", vedtaksperiode.fødselsnummer))
            }
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(nestemann)
        }

        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: Iterable<Arbeidsgiver>,
            hendelse: Hendelse
        ) {
            if (!harNødvendigInntektForVilkårsprøving(vedtaksperiode))
                return hendelse.info("Mangler nødvendig inntekt for vilkårsprøving og kan derfor ikke gjenoppta revurdering.")
            if (!harNødvendigInntektForVilkårsprøving(vedtaksperiode, arbeidsgivere))
                return hendelse.info("Mangler nødvendig inntekt for vilkårsprøving på annen arbeidsgiver og kan derfor ikke gjenoppta revurdering.")
            if (arbeidsgivere.trengerInntektsmelding(vedtaksperiode.periode))
                return hendelse.info("Trenger inntektsmelding for overlappende periode på annen arbeidsgiver og kan derfor ikke gjenoppta revurdering.")
            return vedtaksperiode.nesteTilstandForAktivRevurdering(hendelse)
        }

        private fun harNødvendigInntektForVilkårsprøving(vedtaksperiode: Vedtaksperiode, arbeidsgivere: Iterable<Arbeidsgiver> = listOf(vedtaksperiode.arbeidsgiver)): Boolean {
            val skjæringstidspunkt = vedtaksperiode.skjæringstidspunkt
            return arbeidsgivere.harNødvendigInntektForVilkårsprøving(skjæringstidspunkt)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: UtbetalingHendelse) {
            vedtaksperiode.håndterUtbetalingHendelse(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (!vedtaksperiode.arbeidsgiver.harNødvendigInntektForVilkårsprøving(vedtaksperiode.skjæringstidspunkt)) {
                påminnelse.info("Varsler arbeidsgiver at vi har behov for inntektsmelding.")
                vedtaksperiode.trengerInntektsmelding()
            }
            vedtaksperiode.person.gjenopptaBehandling(påminnelse)
        }

        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)
        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            vedtaksperiode.inntektsmeldingHåndtert(hendelse)
        }

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.trengerIkkeInntektsmelding()
        }
    }

    private fun nesteTilstandForAktivRevurdering(hendelse: Hendelse) {
        vilkårsgrunnlag ?: return tilstand(hendelse, AvventerVilkårsprøvingRevurdering) {
            hendelse.info("Trenger å utføre vilkårsprøving før vi kan beregne utbetaling for revurderingen.")
        }
        tilstand(hendelse, AvventerHistorikkRevurdering)
    }

    internal data object AvventerHistorikkRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_HISTORIKK_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            checkNotNull(vedtaksperiode.vilkårsgrunnlag) { "Forventer vilkårsgrunnlag for å beregne revurdering" }
            hendelse.info("Forespør sykdoms- og inntektshistorikk")
            vedtaksperiode.trengerYtelser(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            BEREGNING fordi OVERSTYRING_IGANGSATT

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
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
            infotrygdhistorikk: Infotrygdhistorikk,
            arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger
        ) {
            håndterRevurdering(ytelser) {
                validation(ytelser) {
                    valider { vedtaksperiode.kalkulerUtbetalinger(this, ytelser, infotrygdhistorikk, arbeidsgiverUtbetalinger) }
                    onSuccess { vedtaksperiode.høstingsresultater(ytelser, AvventerSimuleringRevurdering, AvventerGodkjenningRevurdering) }
                }
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }
    }

    internal data object AvventerVilkårsprøvingRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_VILKÅRSPRØVING_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.trengerVilkårsgrunnlag(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = null
        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {}
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
        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

    }

    internal data object AvventerInntektsmelding : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_INNTEKTSMELDING
        override fun makstid(
            tilstandsendringstidspunkt: LocalDateTime,
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: List<Arbeidsgiver>
        ): LocalDateTime =
            tilstandsendringstidspunkt.plusDays(180)

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.trengerInntektsmeldingReplay()
            vedtaksperiode.trengerInntektsmelding()
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            INNTEKTSMELDING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            check(vedtaksperiode.generasjoner.harIkkeUtbetaling()) {
                "hæ?! vedtaksperiodens generasjon er ikke uberegnet!"
            }
            vedtaksperiode.trengerIkkeInntektsmelding()
            vedtaksperiode.person.trengerIkkeInntektsmeldingReplay(vedtaksperiode.id)
        }

        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding): Boolean {
            return vedtaksperiode.skalHåndtereDagerAvventerInntektsmelding(dager)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            dager.valider(vedtaksperiode.periode)
            if (dager.harFunksjonelleFeilEllerVerre()) {
                vedtaksperiode.forkast(dager)
                return
            }
            vedtaksperiode.håndterDager(dager)
            if(vedtaksperiode.sykdomstidslinje.egenmeldingerFraSøknad().isNotEmpty()) {
                sikkerlogg.warn("Det er egenmeldingsdager fra søknaden på sykdomstidlinjen, selv etter at inntektsmeldingen har oppdatert historikken. Undersøk hvorfor inntektsmeldingen ikke har overskrevet disse. Da er kanskje denne aktørId-en til hjelp: ${vedtaksperiode.aktørId}.")
            }
            if (vedtaksperiode.forventerInntekt()) return
            vedtaksperiode.tilstand(dager, AvsluttetUtenUtbetaling)
        }

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            vedtaksperiode.inntektsmeldingHåndtert(hendelse)
            vurderOmKanGåVidere(vedtaksperiode, hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad)
            vedtaksperiode.trengerArbeidsgiveropplysninger(søknad)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            revurdering.hvisIkkeArbeidsgiverperiode { vedtaksperiode.trengerArbeidsgiveropplysninger(revurdering) }
            vurderOmKanGåVidere(vedtaksperiode, revurdering)
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

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) {
                påminnelse.info("Mangler nødvendig inntekt ved tidligere beregnet sykepengegrunnlag")
            }
            if (påminnelse.skalReberegnes()) {
                return vurderOmKanGåVidere(vedtaksperiode, påminnelse)
            }
            vedtaksperiode.trengerArbeidsgiveropplysninger(påminnelse)
        }

        override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, arbeidsgivere: Iterable<Arbeidsgiver>, hendelse: Hendelse) {
            vurderOmKanGåVidere(vedtaksperiode, hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, inntektsmeldingReplayUtført: InntektsmeldingReplayUtført) {
            if (vedtaksperiode.trengerArbeidsgiveropplysninger(inntektsmeldingReplayUtført)) {
                // ved out-of-order gir vi beskjed om at vi ikke trenger arbeidsgiveropplysninger for den seneste perioden lenger
                vedtaksperiode.arbeidsgiver.finnVedtaksperiodeRettEtter(vedtaksperiode)?.also {
                    it.trengerIkkeArbeidsgiveropplysninger()
                }
            }
            vurderOmKanGåVidere(vedtaksperiode, inntektsmeldingReplayUtført)
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
            if (!vedtaksperiode.harTilstrekkeligInformasjonTilUtbetaling(hendelse)) return
            vedtaksperiode.tilstand(hendelse, AvventerBlokkerendePeriode)
        }
    }

    internal data object AvventerBlokkerendePeriode : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_BLOKKERENDE_PERIODE
        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.generasjoner.forkastUtbetaling(hendelse)
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun makstid(
            tilstandsendringstidspunkt: LocalDateTime,
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: List<Arbeidsgiver>
        ) = tilstand(vedtaksperiode, arbeidsgivere).makstid(tilstandsendringstidspunkt)

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Venteårsak? {
            return tilstand(vedtaksperiode, arbeidsgivere).venteårsak()
        }
        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(nestemann)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad)
        }

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            if (vedtaksperiode.inntektsmeldingHåndtert(hendelse)) return
            hendelse.varsel(RV_IM_4)
        }

        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: Iterable<Arbeidsgiver>,
            hendelse: Hendelse
        ) = tilstand(vedtaksperiode, arbeidsgivere).gjenopptaBehandling(vedtaksperiode, hendelse)

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag()) {
                påminnelse.info("Mangler nødvendig inntekt ved tidligere beregnet sykepengegrunnlag")
            }
            vedtaksperiode.person.gjenopptaBehandling(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.generasjoner.forkastUtbetaling(revurdering)
        }

        private fun tilstand(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: Iterable<Arbeidsgiver>
        ) = when {
            arbeidsgivere.avventerSøknad(vedtaksperiode.periode) -> AvventerTidligereEllerOverlappendeSøknad
            !vedtaksperiode.forventerInntekt() -> ForventerIkkeInntekt
            vedtaksperiode.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag() -> ManglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag
            !vedtaksperiode.arbeidsgiver.harNødvendigInntektForVilkårsprøving(vedtaksperiode.skjæringstidspunkt) -> ManglerNødvendigInntektForVilkårsprøving
            !arbeidsgivere.harNødvendigInntektForVilkårsprøving(vedtaksperiode.skjæringstidspunkt) -> ManglerNødvendigInntektForVilkårsprøvingForAndreArbeidsgivere
            arbeidsgivere.trengerInntektsmelding(vedtaksperiode.periode) -> TrengerInntektsmelding
            vedtaksperiode.vilkårsgrunnlag == null -> KlarForVilkårsprøving
            else -> KlarForBeregning
        }

        private sealed interface Tilstand {
            fun venteårsak(): Venteårsak? = null
            fun makstid(tilstandsendringstidspunkt: LocalDateTime): LocalDateTime {
                val venteårsak = venteårsak()
                return if (venteårsak.venterPåInntektsmelding) tilstandsendringstidspunkt.plusDays(180)
                else if (venteårsak.venterPåSøknad) tilstandsendringstidspunkt.plusDays(90)
                else LocalDateTime.MAX
            }
            fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse)
        }
        private data object AvventerTidligereEllerOverlappendeSøknad: Tilstand {
            override fun venteårsak() = SØKNAD fordi HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE
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
        private data object ManglerNødvendigInntektForVilkårsprøving: Tilstand {
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Mangler inntekt for sykepengegrunnlag som følge av at skjæringstidspunktet har endret seg")
                vedtaksperiode.tilstand(hendelse, AvventerInntektsmelding)
            }
        }
        private data object ManglerNødvendigInntektForVilkårsprøvingForAndreArbeidsgivere: Tilstand {
            override fun venteårsak() = INNTEKTSMELDING fordi MANGLER_INNTEKT_FOR_VILKÅRSPRØVING_PÅ_ANDRE_ARBEIDSGIVERE
            override fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
                hendelse.info("Gjenopptar ikke behandling fordi minst én arbeidsgiver ikke har tilstrekkelig inntekt for skjæringstidspunktet")
            }
        }
        private data object TrengerInntektsmelding: Tilstand {
            override fun venteårsak() = INNTEKTSMELDING fordi MANGLER_REFUSJONSOPPLYSNINGER_PÅ_ANDRE_ARBEIDSGIVERE
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

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.trengerVilkårsgrunnlag(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = null

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {}

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            vedtaksperiode.trengerVilkårsgrunnlag(påminnelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad, AvventerBlokkerendePeriode)
        }

        override fun håndtertInntektPåSkjæringstidspunktet(vedtaksperiode: Vedtaksperiode, hendelse: Inntektsmelding) {
            if (vedtaksperiode.inntektsmeldingHåndtert(hendelse)) return
            hendelse.varsel(RV_IM_4)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, vilkårsgrunnlag: Vilkårsgrunnlag) {
            håndterFørstegangsbehandling(vilkårsgrunnlag, vedtaksperiode) {
                vedtaksperiode.håndterVilkårsgrunnlag(vilkårsgrunnlag, AvventerHistorikk)
            }
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            vedtaksperiode.tilstand(revurdering, AvventerBlokkerendePeriode)
        }

    }

    internal data object AvventerHistorikk : Vedtaksperiodetilstand {
        override val type = AVVENTER_HISTORIKK

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            checkNotNull(vedtaksperiode.vilkårsgrunnlag) { "Forventer vilkårsgrunnlag for å beregne utbetaling" }
            vedtaksperiode.loggInnenforArbeidsgiverperiode()
            vedtaksperiode.trengerYtelser(hendelse)
            hendelse.info("Forespør sykdoms- og inntektshistorikk")
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            BEREGNING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }
        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
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
            infotrygdhistorikk: Infotrygdhistorikk,
            arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger
        ) {
            håndterFørstegangsbehandling(ytelser, vedtaksperiode) {
                validation(ytelser) {
                    onValidationFailed { vedtaksperiode.forkast(ytelser) }
                    valider { vedtaksperiode.kalkulerUtbetalinger(this, ytelser, infotrygdhistorikk, arbeidsgiverUtbetalinger) }
                    onSuccess { vedtaksperiode.høstingsresultater(ytelser, AvventerSimulering, AvventerGodkjenning) }
                }
            }
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            revurdering.inngåSomEndring(vedtaksperiode, vedtaksperiode.periode)
            vedtaksperiode.tilstand(revurdering, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting)
        }
    }

    internal data object AvventerSimulering : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_SIMULERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            trengerSimulering(vedtaksperiode, hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            UTBETALING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }
        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            revurdering.inngåSomEndring(vedtaksperiode, vedtaksperiode.periode)
            vedtaksperiode.tilstand(revurdering, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
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
                vedtaksperiode.generasjoner.valider(simulering)
            }
            if (!vedtaksperiode.generasjoner.erKlarForGodkjenning()) return simulering.info("Kan ikke gå videre da begge oppdragene ikke er simulert.")
            vedtaksperiode.tilstand(simulering, AvventerGodkjenning)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        private fun trengerSimulering(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) {
            vedtaksperiode.generasjoner.simuler(hendelse)
        }
    }

    internal data object AvventerSimuleringRevurdering : Vedtaksperiodetilstand {
        override val type: TilstandType = AVVENTER_SIMULERING_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.generasjoner.simuler(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            UTBETALING fordi OVERSTYRING_IGANGSATT

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            } ?: vedtaksperiode.generasjoner.simuler(påminnelse)
        }
        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)
        override fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering) {
            håndterRevurdering(simulering) {
                vedtaksperiode.generasjoner.valider(simulering)
            }
            if (!vedtaksperiode.generasjoner.erKlarForGodkjenning()) return simulering.info("Kan ikke gå videre da begge oppdragene ikke er simulert.")
            vedtaksperiode.tilstand(simulering, AvventerGodkjenningRevurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }
    }

    internal data object AvventerGodkjenning : Vedtaksperiodetilstand {
        override val type = AVVENTER_GODKJENNING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.trengerGodkjenning(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = GODKJENNING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknad(søknad, AvventerBlokkerendePeriode)
            if (søknad.harFunksjonelleFeilEllerVerre()) return
        }

        override fun håndter(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            vedtaksperiode: Vedtaksperiode,
            utbetalingsavgjørelse: Utbetalingsavgjørelse
        ) {
            vedtaksperiode.generasjoner.vedtakFattet(utbetalingsavgjørelse)
            if (vedtaksperiode.generasjoner.erAvvist()) {
                return if (arbeidsgiver.kanForkastes(vedtaksperiode)) vedtaksperiode.forkast(utbetalingsavgjørelse)
                else utbetalingsavgjørelse.varsel(RV_UT_24)
            }
            vedtaksperiode.tilstand(
                utbetalingsavgjørelse,
                when {
                    vedtaksperiode.generasjoner.harUtbetalinger() -> TilUtbetaling
                    else -> Avsluttet
                }
            )
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
            if (vedtaksperiode.generasjoner.erHistorikkEndretSidenBeregning(infotrygdhistorikk)) return vedtaksperiode.tilstand(hendelse, AvventerBlokkerendePeriode) {
                hendelse.info("Infotrygdhistorikken har endret seg, reberegner periode")
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            revurdering.inngåSomEndring(vedtaksperiode, vedtaksperiode.periode)
            vedtaksperiode.tilstand(revurdering, AvventerBlokkerendePeriode)
        }

    }

    internal data object AvventerGodkjenningRevurdering : Vedtaksperiodetilstand {
        override val type = AVVENTER_GODKJENNING_REVURDERING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.trengerGodkjenning(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) =
            GODKJENNING fordi OVERSTYRING_IGANGSATT

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }
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
            utbetalingsavgjørelse: Utbetalingsavgjørelse
        ) {
            vedtaksperiode.generasjoner.vedtakFattet(utbetalingsavgjørelse)
            if (vedtaksperiode.generasjoner.erAvvist()) {
                if (utbetalingsavgjørelse.automatisert) {
                    utbetalingsavgjørelse.info("Revurderingen ble avvist automatisk - hindrer tilstandsendring for å unngå saker som blir stuck")
                    return sikkerlogg.error("Revurderingen ble avvist automatisk - hindrer tilstandsendring for å unngå saker som blir stuck")
                }
                utbetalingsavgjørelse.varsel(RV_UT_1)
            }
            vedtaksperiode.tilstand(utbetalingsavgjørelse, when {
                vedtaksperiode.generasjoner.erAvvist() -> RevurderingFeilet
                vedtaksperiode.generasjoner.harUtbetalinger() -> TilUtbetaling
                else -> Avsluttet
            })
        }
        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            if (vedtaksperiode.generasjoner.erHistorikkEndretSidenBeregning(infotrygdhistorikk)) return vedtaksperiode.tilstand(
                hendelse,
                AvventerRevurdering
            ) {
                hendelse.info("Infotrygdhistorikken har endret seg, reberegner periode")
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }
    }

    internal data object TilUtbetaling : Vedtaksperiodetilstand {
        override val type = TIL_UTBETALING

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {}

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = UTBETALING.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }
        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: UtbetalingHendelse) {
            vedtaksperiode.håndterUtbetalingHendelse(hendelse)
            if (!vedtaksperiode.generasjoner.erAvsluttet()) return
            vedtaksperiode.tilstand(hendelse, Avsluttet) {
                hendelse.info("OK fra Oppdragssystemet")
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            when {
                vedtaksperiode.generasjoner.erUbetalt() -> vedtaksperiode.tilstand(påminnelse, AvventerBlokkerendePeriode)
                vedtaksperiode.generasjoner.erAvsluttet() -> vedtaksperiode.tilstand(påminnelse, Avsluttet)
            }
        }
    }

    internal data object AvsluttetUtenUtbetaling : Vedtaksperiodetilstand {
        override val type = AVSLUTTET_UTEN_UTBETALING

        override val erFerdigBehandlet = true

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            loggPeriodeSomStrekkerSegUtoverArbeidsgiverperioden(vedtaksperiode)
            vedtaksperiode.lås()
            vedtaksperiode.generasjoner.avslutt(hendelse)
            check(!vedtaksperiode.generasjoner.harUtbetaling()) { "Forventet ikke at perioden har fått utbetaling: kun perioder innenfor arbeidsgiverperioden skal sendes hit. " }
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        private fun loggPeriodeSomStrekkerSegUtoverArbeidsgiverperioden(vedtaksperiode: Vedtaksperiode) {
            val sykdomstidslinjeUtoverAgp = vedtaksperiode.finnArbeidsgiverperiode()?.sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(vedtaksperiode.sykdomstidslinje)?.takeUnless { it.periode() == null } ?: return
            sikkerlogg.info("Periode som går til avsluttet uten utbetaling og strekker seg utover arbeidsgiverperioden ${sykdomstidslinjeUtoverAgp.toUnikDagtypeShortString()}",
                keyValue("aktørId", vedtaksperiode.aktørId),
                keyValue("organisasjonsnummer", vedtaksperiode.organisasjonsnummer),
                keyValue("fom", "${vedtaksperiode.periode.start}")
            )
        }

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            check(vedtaksperiode.generasjoner.harÅpenGenerasjon())
            vedtaksperiode.låsOpp()
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Venteårsak {
            if (!vedtaksperiode.forventerInntekt(NullObserver)) return HJELP.utenBegrunnelse
            return HJELP fordi VIL_OMGJØRES
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            if (!vedtaksperiode.forventerInntekt(NullObserver)) return
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            if (!vedtaksperiode.forventerInntekt()) {
                return vedtaksperiode.generasjoner.avslutt(revurdering)
            }
            vedtaksperiode.generasjoner.sikreNyGenerasjon(revurdering)
            revurdering.inngåSomEndring(vedtaksperiode, vedtaksperiode.periode)
            revurdering.loggDersomKorrigerendeSøknad(revurdering, "Startet omgjøring grunnet korrigerende søknad")
            revurdering.info(RV_RV_1.varseltekst)
            if (!vedtaksperiode.arbeidsgiver.harTilstrekkeligInformasjonTilUtbetaling(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode, revurdering)) {
                revurdering.info("mangler nødvendige opplysninger fra arbeidsgiver")
                return vedtaksperiode.tilstand(revurdering, AvventerInntektsmelding)
            }
            vedtaksperiode.tilstand(revurdering, AvventerBlokkerendePeriode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            søknad.info("Prøver å igangsette revurdering grunnet korrigerende søknad")
            vedtaksperiode.håndterLåstOverlappendeSøknadRevurdering(søknad)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            dager.valider(vedtaksperiode.periode)
            if (dager.harFunksjonelleFeilEllerVerre()) {
                vedtaksperiode.forkast(dager)
                return
            }

            vedtaksperiode.låsOpp()
            vedtaksperiode.håndterDager(dager)
            vedtaksperiode.lås()

            vedtaksperiode.person.igangsettOverstyring(
                Revurderingseventyr.arbeidsgiverperiode(dager, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)
            )
        }

        override fun håndter(
            vedtaksperiode: Vedtaksperiode,
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            if (!vedtaksperiode.forventerInntekt()) return
            håndterRevurdering(hendelse) {
                infotrygdhistorikk.valider(hendelse, vedtaksperiode.periode, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.organisasjonsnummer)
            }

            if (forkastPåGrunnAvInfotrygdendring(hendelse, vedtaksperiode, infotrygdhistorikk)) {
                hendelse.funksjonellFeil(RV_IT_3)
                vedtaksperiode.person.forkastAuu(hendelse, vedtaksperiode)
                return
            }

            hendelse.varsel(RV_IT_38)
            vedtaksperiode.person.igangsettOverstyring(
                Revurderingseventyr.infotrygdendring(hendelse, vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)
            )
        }

        private fun utbetaltIInfotrygd(vedtaksperiode: Vedtaksperiode, infotrygdhistorikk: Infotrygdhistorikk) =
            vedtaksperiode.finnArbeidsgiverperiode()?.utbetaltIInfotrygd(vedtaksperiode.periode, infotrygdhistorikk) == true

        private fun forkastPåGrunnAvInfotrygdendring(hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode, infotrygdhistorikk: Infotrygdhistorikk): Boolean {
            if (vedtaksperiode.harTilstrekkeligInformasjonTilUtbetaling(hendelse)) return false // Om vi har info kan vi sende den ut til Saksbehandler uansett
            if (!vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode)) return false // Perioden kan ikke forkastes
            return utbetaltIInfotrygd(vedtaksperiode, infotrygdhistorikk) // Kan forkaste om alt er utbetalt i Infotrygd i sin helhet
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse) {
            if (!vedtaksperiode.forventerInntekt(NullObserver)) return påminnelse.info("Forventer ikke inntekt. Vil forbli i AvsluttetUtenUtbetaling")
            påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
                påminnelse.info("Reberegner perioden ettersom det er ønsket")
                vedtaksperiode.person.igangsettOverstyring(it)
            }
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderLåstTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, anmodningOmForkasting: AnmodningOmForkasting) {
            vedtaksperiode.person.forkastAuu(anmodningOmForkasting, vedtaksperiode)
            if (vedtaksperiode.tilstand == AvsluttetUtenUtbetaling) return anmodningOmForkasting.info("Kan ikke etterkomme anmodning om forkasting")
            anmodningOmForkasting.info("Etterkommer anmodning om forkasting")
        }
    }

    internal data object Avsluttet : Vedtaksperiodetilstand {
        override val type = AVSLUTTET

        override val erFerdigBehandlet = true
        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.lås()
            check(vedtaksperiode.generasjoner.erAvsluttet()) {
                "forventer at utbetaling skal være avsluttet"
            }
            check(vedtaksperiode.generasjoner.erFattetVedtak()) {
                "forventer at generasjonen skal ha fattet vedtak"
            }
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = HJELP.utenBegrunnelse
        override fun håndter(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) {
            vedtaksperiode.håndterKorrigerendeInntektsmelding(dager) {
                vedtaksperiode.låsOpp()
                it()
                vedtaksperiode.lås()
            }
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {}

        override fun leaving(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.låsOpp()
            check(vedtaksperiode.generasjoner.harÅpenGenerasjon()) {
                "forventer at vedtaksperioden er uberegnet når den går ut av Avsluttet"
            }
        }
        override fun skalHåndtereDager(vedtaksperiode: Vedtaksperiode, dager: DagerFraInntektsmelding) =
            vedtaksperiode.skalHåndtereDagerRevurdering(dager)

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {
            revurdering.inngåSomRevurdering(vedtaksperiode, vedtaksperiode.periode)
            vedtaksperiode.jurist.`fvl § 35 ledd 1`()
            vedtaksperiode.generasjoner.sikreNyGenerasjon(revurdering)
            vedtaksperiode.tilstand(revurdering, AvventerRevurdering)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, hendelse: OverstyrTidslinje) {
            vedtaksperiode.revurderLåstTidslinje(hendelse)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            vedtaksperiode.håndterLåstOverlappendeSøknadRevurdering(søknad)
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

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            vedtaksperiode.person.gjenopptaBehandling(hendelse)
        }
        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Venteårsak? {
            if (vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode.generasjoner)) return null
            return HJELP.utenBegrunnelse
        }

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {
            vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)
        }

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
            throw IllegalStateException("Kan ikke håndtere søknad mens perioden er i RevurderingFeilet")
        }

        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            arbeidsgivere: Iterable<Arbeidsgiver>,
            hendelse: Hendelse
        ) {
            if (!vedtaksperiode.arbeidsgiver.kanForkastes(vedtaksperiode.generasjoner)) return hendelse.info("Gjenopptar ikke revurdering feilet fordi perioden har tidligere avsluttede utbetalinger. Må behandles manuelt vha annullering.")
            hendelse.funksjonellFeil(RV_RV_2)
            vedtaksperiode.forkast(hendelse)
        }

        override fun igangsettOverstyring(vedtaksperiode: Vedtaksperiode, revurdering: Revurderingseventyr) {}
    }

    internal data object TilInfotrygd : Vedtaksperiodetilstand {
        override val type = TIL_INFOTRYGD
        override val erFerdigBehandlet = true

        override fun entering(vedtaksperiode: Vedtaksperiode, hendelse: Hendelse) {
            hendelse.info("Vedtaksperioden kan ikke behandles i Spleis.")
        }

        override fun venteårsak(vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>) = HJELP.utenBegrunnelse

        override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) {}

        override fun håndter(vedtaksperiode: Vedtaksperiode, søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
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
        // dersom "ny" slutter på en fredag, så starter ikke oppholdstelling før påfølgende mandag.
        // det kan derfor være mer enn 16 dager avstand mellom periodene, og arbeidsgiverperioden kan være den samme
        // Derfor bruker vi tallet 18 fremfor kanskje det forventende 16…
        internal const val MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD = 18L
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        // Fredet funksjonsnavn
        internal val TIDLIGERE_OG_ETTERGØLGENDE = fun(segSelv: Vedtaksperiode): VedtaksperiodeFilter {
            // forkaster perioder som er før/overlapper med oppgitt periode, eller som er sammenhengende med
            // perioden som overlapper (per skjæringstidpunkt!).
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            val arbeidsgiverperiode = segSelv.finnArbeidsgiverperiode()
            return fun (other: Vedtaksperiode): Boolean {
                if (other.periode.start >= segSelv.periode.start) return true // Forkaster nyere perioder på tvers av arbeidsgivere
                if (arbeidsgiverperiode != null && other.arbeidsgiver === segSelv.arbeidsgiver && other.periode in arbeidsgiverperiode) return true // Forkaster samme arbeidsgiverperiode (kun for samme arbeidsgiver)
                return other.skjæringstidspunkt == skjæringstidspunkt // Forkaster alt med samme skjæringstidspunkt på tvers av arbeidsgivere
            }
        }

        internal val HAR_PÅGÅENDE_UTBETALINGER: VedtaksperiodeFilter = { it.generasjoner.utbetales() }

        internal val HAR_AVVENTENDE_GODKJENNING: VedtaksperiodeFilter = {
            it.tilstand == AvventerGodkjenning || it.tilstand == AvventerGodkjenningRevurdering
        }

        internal val KLAR_TIL_BEHANDLING: VedtaksperiodeFilter = {
            it.tilstand == AvventerBlokkerendePeriode || it.tilstand == AvventerGodkjenning
        }

        private val IKKE_FERDIG_BEHANDLET: VedtaksperiodeFilter = { !it.tilstand.erFerdigBehandlet }

        private val VILKÅRSPRØVD_PÅ = { skjæringstidspunkt: LocalDate ->
            { vedtaksperiode: Vedtaksperiode -> vedtaksperiode.skjæringstidspunkt == skjæringstidspunkt && vedtaksperiode.vilkårsgrunnlag?.erArbeidsgiverRelevant(vedtaksperiode.organisasjonsnummer) == true }
        }

        private val OVERLAPPER_MED = { other: Vedtaksperiode ->
            { vedtaksperiode: Vedtaksperiode -> vedtaksperiode.periode.overlapperMed(other.periode) }
        }

        internal val MED_SKJÆRINGSTIDSPUNKT = { skjæringstidspunkt: LocalDate ->
            { vedtaksperiode: Vedtaksperiode -> vedtaksperiode.skjæringstidspunkt == skjæringstidspunkt }
        }

        internal val SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG = { skjæringstidspunkt: LocalDate ->
            { vedtaksperiode: Vedtaksperiode ->
                MED_SKJÆRINGSTIDSPUNKT(skjæringstidspunkt)(vedtaksperiode) && vedtaksperiode.forventerInntekt()
            }
        }

        internal val TRENGER_REFUSJONSOPPLYSNINGER = { skjæringstidspunkt: LocalDate, periode: Periode ->
            { vedtaksperiode: Vedtaksperiode ->
                SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG(skjæringstidspunkt)(vedtaksperiode) && vedtaksperiode.periode.overlapperMed(periode)
            }
        }

        internal val NYERE_SKJÆRINGSTIDSPUNKT_MED_UTBETALING = { segSelv: Vedtaksperiode ->
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            { vedtaksperiode: Vedtaksperiode ->
                vedtaksperiode.generasjoner.erAvsluttet() && vedtaksperiode.skjæringstidspunkt > skjæringstidspunkt && vedtaksperiode.skjæringstidspunkt > segSelv.periode.endInclusive
            }
        }

        internal val NYERE_SKJÆRINGSTIDSPUNKT_UTEN_UTBETALING = { segSelv: Vedtaksperiode ->
            val skjæringstidspunkt = segSelv.skjæringstidspunkt
            { vedtaksperiode: Vedtaksperiode ->
                vedtaksperiode.tilstand == AvsluttetUtenUtbetaling && vedtaksperiode.skjæringstidspunkt > skjæringstidspunkt && vedtaksperiode.skjæringstidspunkt > segSelv.periode.endInclusive
            }
        }

        internal val AUU_SOM_VIL_UTBETALES: VedtaksperiodeFilter = {
            it.tilstand == AvsluttetUtenUtbetaling && it.forventerInntekt(NullObserver)
        }

        internal fun Iterable<Vedtaksperiode>.nåværendeVedtaksperiode(filter: VedtaksperiodeFilter) =
            firstOrNull(filter)

        internal fun Iterable<Vedtaksperiode>.nestePeriodeSomSkalGjenopptas(): Vedtaksperiode? {
            var minste: Vedtaksperiode? = null
            this
                .filter(IKKE_FERDIG_BEHANDLET)
                .forEach { vedtaksperiode ->
                    minste = minste?.takeIf { it.erTidligereEnn(vedtaksperiode) } ?: vedtaksperiode
                }
            return minste
        }

        private fun Vedtaksperiode.erTidligereEnn(other: Vedtaksperiode): Boolean = this <= other || this.skjæringstidspunkt < other.skjæringstidspunkt

        internal fun List<Vedtaksperiode>.trengerInntektsmelding() = filter {
            it.tilstand == AvventerInntektsmelding
        }.filter {
            it.forventerInntekt()
        }

        internal fun Iterable<Vedtaksperiode>.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas: Vedtaksperiode) {
            check(this.filterNot { it == periodeSomSkalGjenopptas }.none(HAR_AVVENTENDE_GODKJENNING)) {
                "Ugyldig situasjon! Flere perioder til godkjenning samtidig"
            }
        }

        internal abstract class AuuGruppering protected constructor(
            protected val organisasjonsnummer: String,
            auuer: List<Vedtaksperiode>
        ) {
            init {
                check(auuer.isNotEmpty()) { "Må inneholde minst en vedtaksperiode" }
                check(auuer.all { it.tilstand == AvsluttetUtenUtbetaling }) { "Alle vedtaksperioder må være AvsluttetUtenUtbetaling" }
                check(auuer.all { it.organisasjonsnummer == organisasjonsnummer }) { "Alle vedtaksperioder må høre til samme arbeidsgiver" }
            }

            private val auuer = auuer.filter { it.arbeidsgiver.kanForkastes(it) }
            protected val førsteAuu = auuer.min()
            protected val sisteAuu = auuer.max()
            protected val perioder = auuer.map { it.periode }
            protected val arbeidsgiver = sisteAuu.arbeidsgiver
            private val person = sisteAuu.person

            abstract fun påvirkerForkastingArbeidsgiverperioden(alleVedtaksperioder: List<Vedtaksperiode>): Boolean

            internal fun forkast(
                hendelse: Hendelse,
                alleVedtaksperioder: List<Vedtaksperiode>,
                årsak: String = "${hendelse::class.simpleName}"
            ) {
                hendelse.info("Forkaste AUU: Vurderer om periodene $perioder kan forkastes på grunn av $årsak")
                if (!kanForkastes(hendelse, alleVedtaksperioder)) return
                hendelse.info("Forkaste AUU: Vedtaksperiodene $perioder forkastes på grunn av $årsak")
                sikkerlogg.info("Forkaste AUU: Vedtaksperiodene $perioder forkastes på grunn av $årsak", keyValue("aktørId", sisteAuu.aktørId), keyValue("fom", "${førsteAuu.periode.start}"), keyValue("tom", "${sisteAuu.periode.endInclusive}"))
                val forkastes = auuer.map { it.id }
                person.søppelbøtte(hendelse) { it.id in forkastes }
            }

            private fun kanForkastes(
                hendelse: IAktivitetslogg?,
                alleVedtaksperioder: List<Vedtaksperiode>
            ): Boolean {
                if (auuer.any { !it.arbeidsgiver.kanForkastes(it) }) return false.also { hendelse?.info("Forkaste AUU: Kan ikke forkastes, har overlappende utbetalte utbetalinger på samme arbeidsgiver") }
                if (påvirkerForkastingArbeidsgiverperioden(alleVedtaksperioder)) return false.also { hendelse?.info("Forkaste AUU: Kan ikke forkastes, påvirker arbeidsgiverperiode på samme arbeidsgiver") }
                if (påvirkerForkastingSkjæringstidspunktPåPerson(hendelse, alleVedtaksperioder)) return false.also { hendelse?.info("Forkaste AUU: Kan ikke forkastes, påvirker skjæringstidspunkt på personen") }
                return true
            }

            private fun påvirkerForkastingSkjæringstidspunktPåPerson(hendelse: IAktivitetslogg?, alleVedtaksperioder: List<Vedtaksperiode>): Boolean {
                val auuenesVedtaksperiodeId = auuer.map { it.id }
                val vedtaksperioderSomMåBeholdeSkjæringstidspunkt = alleVedtaksperioder.filterNot { it.id in auuenesVedtaksperiodeId }.filterNot { it.tilstand == TilInfotrygd }
                val arbeidsgiversSykdomstidslinjeUtenAuuene = arbeidsgiver.sykdomstidslinjeUten(auuer.map { it.sykdomstidslinje })

                vedtaksperioderSomMåBeholdeSkjæringstidspunkt.forEach { vedtaksperiode ->
                    if (vedtaksperiode.vilkårsgrunnlag != null && vedtaksperiode.skjæringstidspunkt != person.skjæringstidspunkt(arbeidsgiver, arbeidsgiversSykdomstidslinjeUtenAuuene, vedtaksperiode.periode)) {
                        hendelse?.info("Forkaste AUU: Kan ikke forkaste, vedtaksperioden ${vedtaksperiode.periode} på ${vedtaksperiode.organisasjonsnummer} ville fått endret skjæringstidspunkt")
                        return true
                    }
                }
                return false
            }

            internal companion object {
                internal fun List<Vedtaksperiode>.auuGruppering(vedtaksperiode: Vedtaksperiode, infotrygdhistorikk: Infotrygdhistorikk): AuuGruppering? {
                    if (vedtaksperiode.tilstand != AvsluttetUtenUtbetaling) return null
                    val arbeidsgiverperiode = vedtaksperiode.finnArbeidsgiverperiode() ?: return AuuUtenAGP(vedtaksperiode.organisasjonsnummer, vedtaksperiode)
                    return this
                        .filter { it.organisasjonsnummer == vedtaksperiode.organisasjonsnummer }
                        .filter { it.tilstand == AvsluttetUtenUtbetaling }
                        .filter { it.finnArbeidsgiverperiode() == arbeidsgiverperiode }
                        .let { AuuerMedSammeAGP(infotrygdhistorikk, vedtaksperiode.organisasjonsnummer, it, arbeidsgiverperiode) }
                }
            }
        }

        internal class AuuerMedSammeAGP(
            private val infotrygdhistorikk: Infotrygdhistorikk,
            organisasjonsnummer: String,
            auuer: List<Vedtaksperiode>,
            private val arbeidsgiverperiode: Arbeidsgiverperiode
        ): AuuGruppering(organisasjonsnummer, auuer) {
            init {
                check(auuer.all { it.finnArbeidsgiverperiode() == arbeidsgiverperiode }) { "Alle vedtaksperidoer må ha samme arbeidsgiverperioder" }
            }
            override fun påvirkerForkastingArbeidsgiverperioden(alleVedtaksperioder: List<Vedtaksperiode>): Boolean {
                if (arbeidsgiverperiode.fiktiv()) return false // Om AGP er fiktiv er AGP gjennomført i Infotrygd, og periode i Spleis skal ikke påvirke AGP (🤞)
                // om arbeidsgiverperioden blir fiktiv, f.eks. at vi har registrert en auu, også har IT utbetalt overlappende/rett etterpå.
                // da kan auuen forkastes siden agp fortsatt vil bli riktig
                // Hvis infotrygd har utbetalt arbeidsgiverperioden, eller rett etterpå, så vil arbeidsgiverperioden
                // fremdeles kunne bli regnet ut riktig for evt. vedtak i spleis som kommer etter IT-periodene
                if (infotrygdhistorikk.villeBlittFiktiv(organisasjonsnummer, arbeidsgiverperiode)) return false
                return alleVedtaksperioder
                    .filter { it.organisasjonsnummer == organisasjonsnummer }
                    .filter { it.tilstand != AvsluttetUtenUtbetaling }
                    .filter { it.periode.starterEtter(sisteAuu.periode) }
                    .any { it.finnArbeidsgiverperiode() == arbeidsgiverperiode && infotrygdhistorikk.ingenUtbetalingerMellom(organisasjonsnummer, sisteAuu.periode.oppdaterTom(it.periode))}
            }
        }

        internal class AuuUtenAGP(
            organisasjonsnummer: String,
            auu: Vedtaksperiode
        ): AuuGruppering(organisasjonsnummer, listOf(auu)) {
            init {
                check(auu.finnArbeidsgiverperiode() == null) { "Vedtaksperiodens arbeidsgiverperiode må være null" }
            }
            override fun påvirkerForkastingArbeidsgiverperioden(alleVedtaksperioder: List<Vedtaksperiode>) = false
        }

        internal fun List<Vedtaksperiode>.venter(nestemann: Vedtaksperiode) {
            forEach { vedtaksperiode ->
                vedtaksperiode.tilstand.venter(vedtaksperiode, nestemann)
            }
        }

        internal fun harNyereForkastetPeriode(forkastede: Iterable<Vedtaksperiode>, vedtaksperiode: Vedtaksperiode, hendelse: SykdomstidslinjeHendelse) =
            forkastede
                .filter { it.periode.start > vedtaksperiode.periode.endInclusive }
                .onEach {
                    val sammeArbeidsgiver = it.organisasjonsnummer == vedtaksperiode.organisasjonsnummer
                    hendelse.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_31 else RV_SØ_32)
                    hendelse.info("Søknaden ${vedtaksperiode.periode} er før en forkastet vedtaksperiode ${it.id} (${it.periode})")
                }
                .isNotEmpty()

        internal fun harOverlappendeForkastetPeriode(forkastede: Iterable<Vedtaksperiode>, vedtaksperiode: Vedtaksperiode, hendelse: SykdomstidslinjeHendelse) =
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

        internal fun harKortGapTilForkastet(forkastede: Iterable<Vedtaksperiode>, hendelse: SykdomstidslinjeHendelse, vedtaksperiode: Vedtaksperiode) =
            forkastede
                .filter { other -> vedtaksperiode.påvirkerArbeidsgiverperioden(other) }
                .onEach {
                    hendelse.funksjonellFeil(RV_SØ_28)
                    hendelse.info("Søknad har et gap som er kortere enn 20 dager til en forkastet vedtaksperiode ${it.id}, hendelse periode: ${hendelse.periode()}, vedtaksperiode periode: ${it.periode}")
                }
                .isNotEmpty()

        internal fun forlengerForkastet(forkastede: List<Vedtaksperiode>, hendelse: SykdomstidslinjeHendelse, vedtaksperiode: Vedtaksperiode) =
            forkastede
                .filter { it.periode.erRettFør(vedtaksperiode.periode) }
                .onEach {
                    val sammeArbeidsgiver = it.organisasjonsnummer == vedtaksperiode.organisasjonsnummer
                    hendelse.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_37 else RV_SØ_38)
                    hendelse.info("Søknad forlenger forkastet vedtaksperiode ${it.id}, hendelse periode: ${hendelse.periode()}, vedtaksperiode periode: ${it.periode}")
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

        internal fun List<Vedtaksperiode>.iderMedUtbetaling(utbetalingId: UUID) =
            filter { it.generasjoner.harId(utbetalingId) }.map { it.id }

        internal fun List<Vedtaksperiode>.inneholder(id: UUID) = any { id == it.id }

        internal fun List<Vedtaksperiode>.periode(): Periode {
            val fom = minOf { it.periode.start }
            val tom = maxOf { it.periode.endInclusive }
            return Periode(fom, tom)
        }

        internal fun ferdigVedtaksperiode(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            aktørId: String,
            fødselsnummer: String,
            organisasjonsnummer: String,
            tilstand: Vedtaksperiodetilstand,
            generasjoner: Generasjoner,
            opprettet: LocalDateTime,
            oppdatert: LocalDateTime,
            medOrganisasjonsnummer: MaskinellJurist
        ): Vedtaksperiode = Vedtaksperiode(
            person = person,
            arbeidsgiver = arbeidsgiver,
            id = id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            tilstand = tilstand,
            generasjoner = generasjoner,
            opprettet = opprettet,
            oppdatert = oppdatert,
            arbeidsgiverjurist = medOrganisasjonsnummer
        )

        private fun List<Vedtaksperiode>.manglendeUtbetalingsopplysninger(dag: LocalDate, melding: String) {
            val vedtaksperiode = firstOrNull { dag in it.periode } ?: return

            sikkerlogg.warn("Manglende utbetalingsopplysninger: $melding for $dag med skjæringstidspunkt ${vedtaksperiode.skjæringstidspunkt}. {}, {}, {}, {}",
                keyValue("aktørId", vedtaksperiode.aktørId),
                keyValue("organisasjonsnummer", vedtaksperiode.organisasjonsnummer),
                keyValue("tilstand", vedtaksperiode.tilstand.type.name),
                keyValue("vedtaksperiodeId", "${vedtaksperiode.id}")
            )
        }

        internal fun List<Vedtaksperiode>.ugyldigUtbetalingstidslinje(dager: Set<LocalDate>) {
            val vedtaksperiode = firstOrNull() ?: return
            sikkerlogg.warn("Ugyldig utbetalingstidslinje: utbetalingsdager med kilde Sykmelding: ${dager.grupperSammenhengendePerioder()} {}, {}, {}",
                keyValue("aktørId", vedtaksperiode.aktørId),
                keyValue("organisasjonsnummer", vedtaksperiode.organisasjonsnummer),
                keyValue("antallDager", dager.size)
            )
        }

        internal fun List<Vedtaksperiode>.manglerVilkårsgrunnlag(dag: LocalDate) =
            manglendeUtbetalingsopplysninger(dag, "mangler vilkårsgrunnlag")
        internal fun List<Vedtaksperiode>.inngårIkkeISykepengegrunnlaget(dag: LocalDate) =
            manglendeUtbetalingsopplysninger(dag, "inngår ikke i sykepengegrunnlaget")
        internal fun List<Vedtaksperiode>.manglerRefusjonsopplysninger(dag: LocalDate) =
            manglendeUtbetalingsopplysninger(dag, "mangler refusjonsopplysninger")

        private fun beregningsperioderFørstegangsbehandling(person: Person, vedtaksperiode: Vedtaksperiode) = (
                listOf(vedtaksperiode) + person
                    .vedtaksperioder(OVERLAPPER_MED(vedtaksperiode))
                    .filter(MED_SKJÆRINGSTIDSPUNKT(vedtaksperiode.skjæringstidspunkt))
                ).maxOf { it.periode.endInclusive }

    }
}

internal typealias VedtaksperiodeFilter = (Vedtaksperiode) -> Boolean
