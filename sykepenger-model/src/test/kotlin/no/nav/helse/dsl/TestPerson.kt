package no.nav.helse.dsl

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.dto.serialisering.PersonUtDto
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.februar
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.InntekterForOpptjeningsvurdering
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold.ArbeidsforholdOverstyrt
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonVisitor
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Arbeidsavklaringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.ArbeidsforholdV2
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Dagpenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Foreldrepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.InntekterForSykepengegrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Institusjonsopphold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Medlemskap
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Omsorgspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Opplæringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Pleiepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Simulering
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Sykepengehistorikk
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spill_av_im.Forespørsel
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.fail

internal class TestPerson(
    private val observatør: PersonObserver,
    private val aktørId: String = AKTØRID,
    private val personidentifikator: Personidentifikator = UNG_PERSON_FNR_2018,
    private val fødselsdato: LocalDate = UNG_PERSON_FDATO_2018,
    deferredLog: DeferredLog = DeferredLog(),
    jurist: MaskinellJurist = MaskinellJurist()
) {
    internal companion object {
        private val fnrformatter = DateTimeFormatter.ofPattern("ddMMyy")
        internal val UNG_PERSON_FDATO_2018 = 12.februar(1992)
        internal val UNG_PERSON_FNR_2018: Personidentifikator = "${UNG_PERSON_FDATO_2018.format(fnrformatter)}40045".somPersonidentifikator()
        internal const val AKTØRID = "42"

        internal val INNTEKT = 31000.00.månedlig

        internal operator fun <R> String.invoke(testPerson: TestPerson, testblokk: TestArbeidsgiver.() -> R) =
            testPerson.arbeidsgiver(this, testblokk)
    }

    private lateinit var forrigeHendelse: IAktivitetslogg

    private val behovsamler = Behovsamler(deferredLog)
    private val vedtaksperiodesamler = Vedtaksperiodesamler()
    private val personHendelsefabrikk = PersonHendelsefabrikk(aktørId, personidentifikator)
    internal val person = Person(aktørId, personidentifikator, fødselsdato.alder, jurist).also {
        it.addObserver(vedtaksperiodesamler)
        it.addObserver(behovsamler)
        it.addObserver(observatør)
    }

    private val ugyldigeSituasjonerObservatør = UgyldigeSituasjonerObservatør(person)
    private val arbeidsgivere = mutableMapOf<String, TestArbeidsgiver>()

    internal fun <INSPEKTØR : PersonVisitor> inspiser(inspektør: (Person) -> INSPEKTØR) = inspektør(person)

    internal fun arbeidsgiver(orgnummer: String) =
        arbeidsgivere.getOrPut(orgnummer) { TestArbeidsgiver(orgnummer) }

    internal fun <R> arbeidsgiver(orgnummer: String, block: TestArbeidsgiver.() -> R) =
        arbeidsgiver(orgnummer)(block)

    internal operator fun <R> String.invoke(testblokk: TestArbeidsgiver.() -> R) =
        arbeidsgiver(this, testblokk)

    private fun <T : PersonHendelse> T.håndter(håndter: Person.(T) -> Unit): T {
        forrigeHendelse = this
        this.register(ugyldigeSituasjonerObservatør)
        person.håndter(this)
        behovsamler.registrerBehov(forrigeHendelse)
        return this
    }

    internal fun bekreftBehovOppfylt() {
        behovsamler.bekreftBehovOppfylt()
    }

    internal fun bekreftIngenUgyldigeSituasjoner() {
        ugyldigeSituasjonerObservatør.bekreftIngenUgyldigeSituasjoner()
    }

    internal fun håndterOverstyrArbeidsforhold(skjæringstidspunkt: LocalDate, vararg overstyrteArbeidsforhold: ArbeidsforholdOverstyrt) {
        personHendelsefabrikk.lagOverstyrArbeidsforhold(skjæringstidspunkt, *overstyrteArbeidsforhold)
            .håndter(Person::håndter)
    }

    internal fun håndterSkjønnsmessigFastsettelse(skjæringstidspunkt: LocalDate, arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>, meldingsreferanseId: UUID) {
        personHendelsefabrikk.lagSkjønnsmessigFastsettelse(skjæringstidspunkt, arbeidsgiveropplysninger, meldingsreferanseId)
            .håndter(Person::håndter)
    }

    internal fun håndterOverstyrArbeidsgiveropplysninger(skjæringstidspunkt: LocalDate, arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>, meldingsreferanseId: UUID) {
        personHendelsefabrikk.lagOverstyrArbeidsgiveropplysninger(skjæringstidspunkt, arbeidsgiveropplysninger, meldingsreferanseId)
            .håndter(Person::håndter)
    }

    internal fun håndterDødsmelding(dødsdato: LocalDate) {
        personHendelsefabrikk.lagDødsmelding(dødsdato).håndter(Person::håndter)
    }

    operator fun <R> invoke(testblokk: TestPerson.() -> R): R {
        return testblokk(this)
    }

    fun dto(): PersonUtDto {
        return person.dto()
    }

    inner class TestArbeidsgiver(internal val orgnummer: String) {
        private val arbeidsgiverHendelsefabrikk = ArbeidsgiverHendelsefabrikk(aktørId, personidentifikator, orgnummer)

        internal val inspektør get() = TestArbeidsgiverInspektør(person, orgnummer)

        internal val Int.vedtaksperiode get() = vedtaksperiodesamler.vedtaksperiodeId(orgnummer, this - 1)

        internal fun håndterSykmelding(periode: Periode) = håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive))

        internal fun håndterSykmelding(
            vararg sykmeldingsperiode: Sykmeldingsperiode,
            sykmeldingSkrevet: LocalDateTime? = null,
            mottatt: LocalDateTime? = null
        ) = arbeidsgiverHendelsefabrikk.lagSykmelding(*sykmeldingsperiode).håndter(Person::håndter)

        internal fun håndterAvbruttSøknad(sykmeldingsperiode: Periode) = arbeidsgiverHendelsefabrikk.lagAvbruttSøknad(sykmeldingsperiode).håndter(Person::håndter)

        internal fun håndterSøknad(periode: Periode) = håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent))

        internal fun håndterSøknad(
            vararg perioder: Søknad.Søknadsperiode,
            egenmeldinger: List<Periode> = emptyList(),
            andreInntektskilder: Boolean = false,
            arbeidUtenforNorge: Boolean = false,
            yrkesskade: Boolean = false,
            sendtTilNAVEllerArbeidsgiver: Temporal? = null,
            sykmeldingSkrevet: LocalDateTime? = null,
            orgnummer: String = "",
            søknadId: UUID = UUID.randomUUID(),
            utenlandskSykmelding: Boolean = false,
            søknadstype: Søknad.Søknadstype = Søknad.Søknadstype.Arbeidstaker,
            sendTilGosys: Boolean = false,
            registrert: LocalDateTime = LocalDateTime.now()
        ) =
            behovsamler.fangInntektsmeldingReplay({
                vedtaksperiodesamler.fangVedtaksperiode {
                    arbeidsgiverHendelsefabrikk.lagSøknad(
                        *perioder,
                        egenmeldinger = egenmeldinger,
                        andreInntektskilder = andreInntektskilder,
                        arbeidUtenforNorge = arbeidUtenforNorge,
                        sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver,
                        sykmeldingSkrevet = sykmeldingSkrevet,
                        id = søknadId,
                        yrkesskade = yrkesskade,
                        utenlandskSykmelding = utenlandskSykmelding,
                        søknadstype = søknadstype,
                        sendTilGosys = sendTilGosys,
                        registrert = registrert
                    ).håndter(Person::håndter)
                }?.also {
                    if (behovsamler.harBehov(it, Sykepengehistorikk)){
                        arbeidsgiverHendelsefabrikk.lagUtbetalingshistorikk(it).håndter(Person::håndter)
                    }
                }
            }) { vedtaksperioderSomHarBedtOmReplay ->
                vedtaksperioderSomHarBedtOmReplay.forEach { forespørsel ->
                    håndterInntektsmeldingReplay(forespørsel)
                }
            }

        internal fun håndterInntektsmelding(
            arbeidsgiverperioder: List<Periode>,
            beregnetInntekt: Inntekt = INNTEKT,
            førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOf { it.start },
            refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
            harOpphørAvNaturalytelser: Boolean = false,
            arbeidsforholdId: String? = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
            id: UUID = UUID.randomUUID(),
            orgnummer: String = "",
            mottatt: LocalDateTime = LocalDateTime.now()
        ): UUID {
            arbeidsgiverHendelsefabrikk.lagInntektsmelding(
                arbeidsgiverperioder,
                beregnetInntekt,
                førsteFraværsdag,
                refusjon,
                harOpphørAvNaturalytelser,
                arbeidsforholdId,
                begrunnelseForReduksjonEllerIkkeUtbetalt,
                id,
                mottatt = mottatt
            ).håndter(Person::håndter)
            return id
        }

        internal fun håndterInntektsmeldingPortal(
            arbeidsgiverperioder: List<Periode>,
            beregnetInntekt: Inntekt = INNTEKT,
            førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOf { it.start },
            inntektsdato: LocalDate,
            vedtaksperiodeId: UUID = inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.id,
            refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
            harOpphørAvNaturalytelser: Boolean = false,
            arbeidsforholdId: String? = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
            id: UUID = UUID.randomUUID(),
            orgnummer: String = "",
            mottatt: LocalDateTime = LocalDateTime.now()
        ): UUID {
            arbeidsgiverHendelsefabrikk.lagPortalinntektsmelding(
                arbeidsgiverperioder,
                beregnetInntekt,
                førsteFraværsdag,
                inntektsdato,
                vedtaksperiodeId,
                refusjon,
                harOpphørAvNaturalytelser,
                arbeidsforholdId,
                begrunnelseForReduksjonEllerIkkeUtbetalt,
                id,
                mottatt = mottatt
            ).håndter(Person::håndter)
            return id
        }

        internal fun håndterForkastSykmeldingsperioder(periode: Periode) =
            arbeidsgiverHendelsefabrikk.lagHåndterForkastSykmeldingsperioder(periode).håndter(Person::håndter)

        internal fun håndterAnmodningOmForkasting(vedtaksperiodeId: UUID) =
            arbeidsgiverHendelsefabrikk.lagAnmodningOmForkasting(vedtaksperiodeId).håndter(Person::håndter)

        private fun håndterInntektsmeldingReplay(forespørsel: Forespørsel) {
            val håndterteInntektsmeldinger = behovsamler.håndterteInntektsmeldinger()
            behovsamler.bekreftOgKvitterReplay(forespørsel.vedtaksperiodeId)
            arbeidsgiverHendelsefabrikk.lagInntektsmeldingReplay(forespørsel, håndterteInntektsmeldinger)
                .håndter(Person::håndter)
        }

        internal fun håndterVilkårsgrunnlag(
            vedtaksperiodeId: UUID = 1.vedtaksperiode,
            inntekt: Inntekt = INNTEKT,
            medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag? = null,
            inntekterForOpptjeningsvurdering: InntekterForOpptjeningsvurdering? = null,
            arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>? = null,
            orgnummer: String = "aa"
        ) {
            behovsamler.bekreftBehov(vedtaksperiodeId, InntekterForSykepengegrunnlag, ArbeidsforholdV2, Medlemskap)
            arbeidsgiverHendelsefabrikk.lagVilkårsgrunnlag(
                vedtaksperiodeId,
                inspektør.skjæringstidspunkt(vedtaksperiodeId),
                medlemskapstatus,
                arbeidsforhold ?: arbeidsgivere.map { (orgnr, _) -> Vilkårsgrunnlag.Arbeidsforhold(orgnr, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT) },
                inntektsvurderingForSykepengegrunnlag ?: lagStandardSykepengegrunnlag(this.orgnummer, inntekt, inspektør.skjæringstidspunkt(vedtaksperiodeId)),
                inntekterForOpptjeningsvurdering ?: lagStandardInntekterForOpptjeningsvurdering(this.orgnummer, inntekt, inspektør.skjæringstidspunkt(vedtaksperiodeId))
            ).håndter(Person::håndter)
        }

        internal fun håndterYtelser(
            vedtaksperiodeId: UUID,
            foreldrepenger: List<GradertPeriode> = emptyList(),
            svangerskapspenger: List<GradertPeriode> = emptyList(),
            pleiepenger: List<GradertPeriode> = emptyList(),
            omsorgspenger: List<GradertPeriode> = emptyList(),
            opplæringspenger: List<GradertPeriode> = emptyList(),
            institusjonsoppholdsperioder: List<no.nav.helse.hendelser.Institusjonsopphold.Institusjonsoppholdsperiode> = emptyList(),
            arbeidsavklaringspenger: List<Periode> = emptyList(),
            dagpenger: List<Periode> = emptyList(),
            orgnummer: String = "aa"
        ) {
            behovsamler.bekreftBehov(vedtaksperiodeId, Dagpenger, Arbeidsavklaringspenger, Institusjonsopphold, Opplæringspenger, Pleiepenger, Omsorgspenger, Foreldrepenger)
            arbeidsgiverHendelsefabrikk.lagYtelser(vedtaksperiodeId, foreldrepenger, svangerskapspenger, pleiepenger, omsorgspenger, opplæringspenger, institusjonsoppholdsperioder, arbeidsavklaringspenger, dagpenger)
                .håndter(Person::håndter)
        }

        internal fun håndterSimulering(vedtaksperiodeId: UUID, simuleringOK: Boolean = true) {
            behovsamler.bekreftBehov(vedtaksperiodeId, Simulering)
            behovsamler.detaljerFor(vedtaksperiodeId, Simulering).forEach { (detaljer, kontekst) ->
                val fagsystemId = detaljer.getValue("fagsystemId") as String
                val fagområde = detaljer.getValue("fagområde") as String
                val utbetalingId = UUID.fromString(kontekst.getValue("utbetalingId"))

                arbeidsgiverHendelsefabrikk.lagSimulering(vedtaksperiodeId, utbetalingId, fagsystemId, fagområde, simuleringOK, standardSimuleringsresultat(orgnummer)).håndter(Person::håndter)
            }
        }

        internal fun håndterUtbetalingsgodkjenning(vedtaksperiodeId: UUID, godkjent: Boolean = true, automatiskBehandling: Boolean = true, godkjenttidspunkt: LocalDateTime = LocalDateTime.now()) {
            behovsamler.bekreftBehov(vedtaksperiodeId, Godkjenning)
            val (_, kontekst) = behovsamler.detaljerFor(vedtaksperiodeId, Godkjenning).single()
            val utbetalingId = UUID.fromString(kontekst.getValue("utbetalingId"))
            arbeidsgiverHendelsefabrikk.lagUtbetalingsgodkjenning(vedtaksperiodeId, godkjent, automatiskBehandling, utbetalingId, godkjenttidspunkt)
                .håndter(Person::håndter)
        }

        internal fun håndterVedtakFattet(vedtaksperiodeId: UUID, utbetalingId: UUID = inspektør.utbetalingId { vedtaksperiodeId }, automatisert: Boolean = true, vedtakFattetTidspunkt: LocalDateTime = LocalDateTime.now()) {
            arbeidsgiverHendelsefabrikk.lagVedtakFattet(vedtaksperiodeId, utbetalingId, automatisert, vedtakFattetTidspunkt)
                .håndter(Person::håndter)
        }
        internal fun håndterKanIkkeBehandlesHer(vedtaksperiodeId: UUID, utbetalingId: UUID = inspektør.utbetalingId { vedtaksperiodeId }, automatisert: Boolean = true) {
            arbeidsgiverHendelsefabrikk.lagKanIkkeBehandlesHer(vedtaksperiodeId, utbetalingId, automatisert)
                .håndter(Person::håndter)
        }

        internal fun håndterUtbetalt(status: Oppdragstatus = Oppdragstatus.AKSEPTERT) {
            behovsamler.bekreftBehov(orgnummer, Utbetaling)
            behovsamler.detaljerFor(orgnummer, Utbetaling).forEach { (detaljer, kontekst) ->
                val utbetalingId = UUID.fromString(kontekst.getValue("utbetalingId"))
                val fagsystemId = detaljer.getValue("fagsystemId") as String
                arbeidsgiverHendelsefabrikk.lagUtbetalinghendelse(utbetalingId, fagsystemId, status)
                    .håndter(Person::håndter)
            }
        }

        internal fun håndterAnnullering(utbetalingId: UUID) {
            arbeidsgiverHendelsefabrikk.lagAnnullering(utbetalingId).håndter(Person::håndter)
        }

        internal fun håndterIdentOpphørt(nyttFnr: Personidentifikator, nyAktørId: String) {
            arbeidsgiverHendelsefabrikk.lagIdentOpphørt().håndter {
                håndter(it, nyttFnr, nyAktørId)
            }
        }

        internal fun håndterPåminnelse(vedtaksperiodeId: UUID, tilstand: TilstandType, tilstandsendringstidspunkt: LocalDateTime = LocalDateTime.now(), reberegning: Boolean = false) {
            arbeidsgiverHendelsefabrikk.lagPåminnelse(vedtaksperiodeId, tilstand, tilstandsendringstidspunkt, reberegning = reberegning)
                .håndter(Person::håndter)
        }

        internal fun håndterGrunnbeløpsregulering(skjæringstidspunkt: LocalDate) {
            arbeidsgiverHendelsefabrikk.lagGrunnbeløpsregulering(skjæringstidspunkt)
                .håndter(Person::håndter)
        }

        internal fun håndterGjenopplivVilkårsgrunnlag(skjæringstidspunkt: LocalDate?, vilkårsgrunnlagId: UUID) {
            arbeidsgiverHendelsefabrikk.lagGjenopplivVilkårsgrunnlag(skjæringstidspunkt, vilkårsgrunnlagId)
                .håndter(Person::håndter)
        }

        internal fun håndterPersonPåminnelse() {
            personHendelsefabrikk.lagPåminnelse()
                .håndter(Person::håndter)
        }

        internal fun håndterOverstyrArbeidsforhold(skjæringstidspunkt: LocalDate, vararg overstyrteArbeidsforhold: ArbeidsforholdOverstyrt) {
            personHendelsefabrikk.lagOverstyrArbeidsforhold(skjæringstidspunkt, *overstyrteArbeidsforhold)
                .håndter(Person::håndter)
        }

        internal fun håndterOverstyrTidslinje(overstyringsdager: List<ManuellOverskrivingDag>) =
            arbeidsgiverHendelsefabrikk.lagHåndterOverstyrTidslinje(overstyringsdager)
                .håndter(Person::håndter)

        internal fun håndterOverstyrInntekt(
            skjæringstidspunkt: LocalDate,
            inntekt: Inntekt,
            hendelseId: UUID = UUID.randomUUID(),
            organisasjonsnummer: String = orgnummer,
        ) =
            arbeidsgiverHendelsefabrikk.lagOverstyrInntekt(hendelseId, skjæringstidspunkt, inntekt, organisasjonsnummer)
                .håndter(Person::håndter)

        internal fun håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt: LocalDate,
            overstyringer: List<OverstyrtArbeidsgiveropplysning>,
            hendelseId: UUID = UUID.randomUUID(),
        ) =
            personHendelsefabrikk.lagOverstyrArbeidsgiveropplysninger(skjæringstidspunkt, overstyringer, hendelseId)
                .håndter(Person::håndter)

        internal fun håndterUtbetalingshistorikkEtterInfotrygdendring(
            utbetalinger: List<Infotrygdperiode> = listOf(),
            inntektshistorikk: List<Inntektsopplysning> = emptyList(),
            besvart: LocalDateTime = LocalDateTime.now(),
            id: UUID = UUID.randomUUID()
        ) =
            arbeidsgiverHendelsefabrikk.lagUtbetalingshistorikkEtterInfotrygdendring(utbetalinger, inntektshistorikk, besvart, id)
                .håndter(Person::håndter)

        internal fun håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår: Year
        ) =
            personHendelsefabrikk.lagUtbetalingshistorikkForFeriepenger(opptjeningsår)
                .håndter(Person::håndter)

        internal fun antallFeriepengeutbetalingerTilArbeidsgiver() = behovsamler.detaljerFor(orgnummer, Utbetaling)
            .count { (behov, _) ->
                behov["linjer"].toString().contains("SPREFAGFER-IOP")
            }

        operator fun <R> invoke(testblokk: TestArbeidsgiver.() -> R): R {
            return testblokk(this)
        }
    }
}

internal fun lagStandardSykepengegrunnlag(orgnummer: String, inntekt: Inntekt, skjæringstidspunkt: LocalDate) =
    lagStandardSykepengegrunnlag(listOf(orgnummer to inntekt), skjæringstidspunkt)
internal fun lagStandardSykepengegrunnlag(arbeidsgivere: List<Pair<String, Inntekt>>, skjæringstidspunkt: LocalDate, arbeidsforhold: List<InntektForSykepengegrunnlag.Arbeidsforhold> = emptyList()) =
    InntektForSykepengegrunnlag(
        inntekter = inntektperioderForSykepengegrunnlag {
            val måned = YearMonth.from(skjæringstidspunkt)
            val periode = måned.minusMonths(3L).atDay(1) til måned.minusMonths(1).atDay(1)
            periode inntekter {
                arbeidsgivere.forEach { (orgnummer, inntekt) -> orgnummer inntekt inntekt }
            }
        },
        arbeidsforhold = arbeidsforhold
    )

internal fun List<String>.lagStandardSykepengegrunnlag(inntekt: Inntekt, skjæringstidspunkt: LocalDate) =
    lagStandardSykepengegrunnlag(map { it to inntekt }, skjæringstidspunkt)

internal fun lagStandardInntekterForOpptjeningsvurdering(orgnummer: String, inntekt: Inntekt, skjæringstidspunkt: LocalDate) =
    lagStandardInntekterForOpptjeningsvurdering(listOf(orgnummer to inntekt), skjæringstidspunkt)

internal fun lagStandardInntekterForOpptjeningsvurdering(arbeidsgivere: List<Pair<String, Inntekt>>, skjæringstidspunkt: LocalDate) =
    InntekterForOpptjeningsvurdering(inntekter = arbeidsgivere.map { arbeidsgiver ->
        val orgnummer = arbeidsgiver.first
        val inntekt = arbeidsgiver.second
        val måned = skjæringstidspunkt.minusMonths(1L)
        ArbeidsgiverInntekt(
            arbeidsgiver = orgnummer,
            inntekter = listOf(
                ArbeidsgiverInntekt.MånedligInntekt(
                    YearMonth.from(måned),
                    inntekt,
                    ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
                    "kontantytelse",
                    "fastloenn"
                )
            )
        )
    })

internal fun standardSimuleringsresultat(orgnummer: String) = SimuleringResultatDto(
    totalbeløp = 2000,
    perioder = listOf(
        SimuleringResultatDto.SimulertPeriode(
            fom = 17.januar,
            tom = 20.januar,
            utbetalinger = listOf(
                SimuleringResultatDto.SimulertUtbetaling(
                    forfallsdato = 21.januar,
                    utbetalesTil = SimuleringResultatDto.Mottaker(
                        id = orgnummer,
                        navn = "Org Orgesen AS"
                    ),
                    feilkonto = false,
                    detaljer = listOf(
                        SimuleringResultatDto.Detaljer(
                            fom = 17.januar,
                            tom = 20.januar,
                            konto = "81549300",
                            beløp = 2000,
                            klassekode = SimuleringResultatDto.Klassekode(
                                kode = "SPREFAG-IOP",
                                beskrivelse = "Sykepenger, Refusjon arbeidsgiver"
                            ),
                            uføregrad = 100,
                            utbetalingstype = "YTEL",
                            tilbakeføring = false,
                            sats = SimuleringResultatDto.Sats(
                                sats = 1000.toDouble(),
                                antall = 2,
                                type = "DAG"
                            ),
                            refunderesOrgnummer = orgnummer
                        )
                    )
                )
            )
        )
    )
)

internal fun TestPerson.TestArbeidsgiver.tilGodkjenning(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    førsteFraværsdag: LocalDate = periode.start,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode> = emptyList(),
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    sykepengegrunnlagSkatt: InntektForSykepengegrunnlag = lagStandardSykepengegrunnlag(orgnummer, beregnetInntekt, førsteFraværsdag),
    inntekterForOpptjeningsvurdering: InntekterForOpptjeningsvurdering? = lagStandardInntekterForOpptjeningsvurdering(orgnummer, beregnetInntekt, førsteFraværsdag),
    arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>? = null,
): UUID {
    val vedtaksperiode = nyPeriode(periode, grad)
    håndterInntektsmelding(arbeidsgiverperiode, beregnetInntekt, førsteFraværsdag, refusjon)
    håndterVilkårsgrunnlag(vedtaksperiode, beregnetInntekt, Medlemskapsvurdering.Medlemskapstatus.Ja, sykepengegrunnlagSkatt, inntekterForOpptjeningsvurdering, arbeidsforhold)
    håndterYtelser(vedtaksperiode)
    håndterSimulering(vedtaksperiode)
    return vedtaksperiode
}
internal fun TestPerson.TestArbeidsgiver.nyttVedtak(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    førsteFraværsdag: LocalDate = periode.start,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode> = emptyList(),
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    sykepengegrunnlagSkatt: InntektForSykepengegrunnlag = lagStandardSykepengegrunnlag(orgnummer, beregnetInntekt, førsteFraværsdag)
) {
    val vedtaksperiode = tilGodkjenning(periode, grad, førsteFraværsdag, beregnetInntekt, refusjon, arbeidsgiverperiode, status, sykepengegrunnlagSkatt)
    håndterUtbetalingsgodkjenning(vedtaksperiode)
    håndterUtbetalt(status)
}

internal fun TestPerson.TestArbeidsgiver.forlengVedtak(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT
): UUID {
    val vedtaksperiode = nyPeriode(periode, grad)
    håndterYtelser(vedtaksperiode)
    håndterSimulering(vedtaksperiode)
    håndterUtbetalingsgodkjenning(vedtaksperiode)
    håndterUtbetalt(status)
    return vedtaksperiode
}

internal fun TestPerson.TestArbeidsgiver.nyPeriode(periode: Periode, grad: Prosentdel = 100.prosent, søknadId : UUID = UUID.randomUUID()): UUID {
    håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive))
    return håndterSøknad(Sykdom(periode.start, periode.endInclusive, grad), søknadId = søknadId) ?: fail { "Det ble ikke opprettet noen vedtaksperiode." }
}

internal fun TestPerson.nyPeriode(periode: Periode, vararg orgnummer: String, grad: Prosentdel = 100.prosent) {
    orgnummer.forEach { it { håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive)) } }
    orgnummer.forEach { it { håndterSøknad(Sykdom(periode.start, periode.endInclusive, grad)) } }
}

