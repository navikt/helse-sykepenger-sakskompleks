package no.nav.helse.spleis

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.februar
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Foreldrepenger
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.InntekterForOpptjeningsvurdering
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Svangerskapspenger
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.testhelpers.TestObservatør
import no.nav.helse.spleis.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AKSEPTERT
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent

internal abstract class AbstractObservableTest {
    protected companion object {
        const val UNG_PERSON_FNR = "12029240045"
        val UNG_PERSON_FØDSELSDATO = 12.februar(1992)
        const val ORGNUMMER = "987654321"
        const val AKTØRID = "42"
        val INNTEKTSMELDING_ID: UUID = UUID.randomUUID()
        val SØKNAD_ID: UUID = UUID.randomUUID()
        val SYKMELDING_ID: UUID = UUID.randomUUID()
        val INNTEKT: Inntekt = 31000.00.månedlig
        val FOM: LocalDate = LocalDate.of(2018, 1, 1)
        val TOM: LocalDate = LocalDate.of(2018, 1, 30)
    }

    protected lateinit var person: Person
    internal lateinit var observatør: TestObservatør

    private val Int.vedtaksperiode: IdInnhenter get() = IdInnhenter { orgnummer -> this.vedtaksperiode(orgnummer) }
    private fun Int.vedtaksperiode(orgnummer: String) = observatør.vedtaksperiode(orgnummer, this - 1)

    protected fun sykmelding(
        id: UUID = SYKMELDING_ID,
        sykeperioder: List<Sykmeldingsperiode> = listOf(Sykmeldingsperiode(FOM, TOM)),
        orgnummer: String = ORGNUMMER,
        fnr: String = UNG_PERSON_FNR
    ): Sykmelding = Sykmelding(
        meldingsreferanseId = id,
        fnr = fnr,
        aktørId = AKTØRID,
        orgnummer = orgnummer,
        sykeperioder = sykeperioder
    )

    protected fun søknad(
        id: UUID = SØKNAD_ID,
        vararg perioder: Søknadsperiode = arrayOf(Sykdom(FOM, TOM, 100.prosent)),
        andreInntektskilder: Boolean = false,
        sendtTilNAVEllerArbeidsgiver: LocalDate = TOM.plusDays(1),
        orgnummer: String = ORGNUMMER,
        sykmeldingSkrevet: LocalDateTime = FOM.atStartOfDay(),
        fnr: String = UNG_PERSON_FNR,
        egenmeldinger: List<Periode> = emptyList()
    ): Søknad = Søknad(
        meldingsreferanseId = id,
        fnr = fnr,
        aktørId = AKTØRID,
        orgnummer = orgnummer,
        perioder = listOf(*perioder),
        andreInntektskilder = andreInntektskilder,
        ikkeJobbetIDetSisteFraAnnetArbeidsforhold = false,
        sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver.atStartOfDay(),
        permittert = false,
        merknaderFraSykmelding = emptyList(),
        sykmeldingSkrevet = sykmeldingSkrevet,
        opprinneligSendt = null,
        utenlandskSykmelding = false,
        arbeidUtenforNorge = false,
        sendTilGosys = false,
        yrkesskade = false,
        egenmeldinger = egenmeldinger,
        registrert = LocalDateTime.now(),
        søknadstype = Søknad.Søknadstype.Arbeidstaker
    )

    protected fun utbetalinghistorikk() = UtbetalingshistorikkEtterInfotrygdendring(
        UUID.randomUUID(),
        "",
        "",
        InfotrygdhistorikkElement.opprett(
            oppdatert = LocalDateTime.now(),
            hendelseId = UUID.randomUUID(),
            perioder = emptyList(),
            inntekter = emptyList(),
            arbeidskategorikoder = emptyMap()
        ),
        besvart = LocalDateTime.now()
    )

    protected fun inntektsmelding(
        id: UUID = INNTEKTSMELDING_ID,
        arbeidsgiverperioder: List<Periode> = listOf(Periode(FOM, TOM)),
        beregnetInntekt: Inntekt = INNTEKT,
        førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOfOrNull { it.start } ?: LocalDate.of(2018, 1, 1),
        inntektsdato: LocalDate? = null,
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
        orgnummer: String = ORGNUMMER,
        harOpphørAvNaturalytelser: Boolean = false,
        arbeidsforholdId: String? = null,
        fnr: String = UNG_PERSON_FNR,
        harFlereInntektsmeldinger: Boolean = false,
        avsendersystem: Inntektsmelding.Avsendersystem? = null
    ): Inntektsmelding = Inntektsmelding(
        meldingsreferanseId = id,
        refusjon = refusjon,
        orgnummer = orgnummer,
        fødselsnummer = fnr,
        aktørId = AKTØRID,
        førsteFraværsdag = førsteFraværsdag,
        inntektsdato = inntektsdato,
        beregnetInntekt = beregnetInntekt,
        arbeidsgiverperioder = arbeidsgiverperioder,
        arbeidsforholdId = arbeidsforholdId,
        begrunnelseForReduksjonEllerIkkeUtbetalt = null,
        harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
        harFlereInntektsmeldinger = harFlereInntektsmeldinger,
        avsendersystem = avsendersystem,
        vedtaksperiodeId = vedtaksperiodeId,
        mottatt = LocalDateTime.now()
    )

    protected fun vilkårsgrunnlag(
        vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
        orgnummer: String = ORGNUMMER,
        arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold> = listOf(Vilkårsgrunnlag.Arbeidsforhold(orgnummer, FOM.minusYears(1), type = Arbeidsforholdtype.ORDINÆRT)),
        inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = inntektperioderForSykepengegrunnlag {
                        Periode(FOM.minusMonths(3), FOM.minusDays(1)) inntekter {
                            ORGNUMMER inntekt INNTEKT
                        }
                    }, arbeidsforhold = emptyList()),
        fnr: String = UNG_PERSON_FNR,
        inntekterForOpptjeningsvurdering: InntekterForOpptjeningsvurdering = InntekterForOpptjeningsvurdering(listOf(
            ArbeidsgiverInntekt(ORGNUMMER, listOf(ArbeidsgiverInntekt.MånedligInntekt(YearMonth.from(FOM.minusMonths(1)),
                INNTEKT, ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT, "kontantytelse", "fastloenn")))
        ))
    ): Vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
            skjæringstidspunkt = FOM,
            aktørId = AKTØRID,
            personidentifikator = fnr.somPersonidentifikator(),
            orgnummer = orgnummer,
            medlemskapsvurdering = Medlemskapsvurdering(medlemskapstatus),
            inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag,
            inntekterForOpptjeningsvurdering = inntekterForOpptjeningsvurdering,
            arbeidsforhold = arbeidsforhold
    )

    protected fun ytelser(
        vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
        foreldrepenger: List<GradertPeriode> = emptyList(),
        svangerskapspenger: List<GradertPeriode> = emptyList(),
        pleiepenger: List<GradertPeriode> = emptyList(),
        omsorgspenger: List<GradertPeriode> = emptyList(),
        opplæringspenger: List<GradertPeriode> = emptyList(),
        institusjonsoppholdsperioder: List<Institusjonsopphold.Institusjonsoppholdsperiode> = emptyList(),
        orgnummer: String = ORGNUMMER,
        arbeidsavklaringspenger: List<Periode> = emptyList(),
        dagpenger: List<Periode> = emptyList(),
        fnr: String = UNG_PERSON_FNR
    ): Ytelser {
        val aktivitetslogg = Aktivitetslogg()
        val meldingsreferanseId = UUID.randomUUID()
        return Ytelser(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = AKTØRID,
            fødselsnummer = fnr,
            organisasjonsnummer = orgnummer,
            vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
            foreldrepenger = Foreldrepenger(foreldrepengeytelse = foreldrepenger),
            svangerskapspenger = Svangerskapspenger(svangerskapsytelse = svangerskapspenger),
            pleiepenger = Pleiepenger(
                perioder = pleiepenger
            ),
            omsorgspenger = Omsorgspenger(
                perioder = omsorgspenger
            ),
            opplæringspenger = Opplæringspenger(
                perioder = opplæringspenger
            ),
            institusjonsopphold = Institusjonsopphold(
                perioder = institusjonsoppholdsperioder
            ),
            arbeidsavklaringspenger = Arbeidsavklaringspenger(arbeidsavklaringspenger),
            dagpenger = Dagpenger(dagpenger),
            aktivitetslogg = aktivitetslogg
        )
    }

    protected fun simulering(
        vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
        simuleringOK: Boolean = true,
        orgnummer: String = ORGNUMMER,
        fom: LocalDate = FOM.plusDays(16),
        tom: LocalDate = TOM,
        fagsystemId: String,
        fagområde: String,
        utbetalingId: UUID
    ) =
        Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR,
            orgnummer = orgnummer,
            fagsystemId = fagsystemId,
            fagområde = fagområde,
            simuleringOK = simuleringOK,
            melding = "",
            utbetalingId = utbetalingId,
            simuleringResultat = SimuleringResultatDto(
                totalbeløp = 2000,
                perioder = listOf(
                    SimuleringResultatDto.SimulertPeriode(
                        fom = fom,
                        tom = tom,
                        utbetalinger = listOf(
                            SimuleringResultatDto.SimulertUtbetaling(
                                forfallsdato = tom.plusDays(1),
                                utbetalesTil = SimuleringResultatDto.Mottaker(
                                    id = orgnummer,
                                    navn = "Org Orgesen AS"
                                ),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimuleringResultatDto.Detaljer(
                                        fom = fom,
                                        tom = tom,
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
                                            sats = 1000.0,
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
        )

    protected fun utbetalingsgodkjenning(
        vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
        utbetalingGodkjent: Boolean = true,
        orgnummer: String = ORGNUMMER,
        automatiskBehandling: Boolean = false,
        utbetalingId: UUID
    ) = Utbetalingsgodkjenning(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AKTØRID,
        fødselsnummer = UNG_PERSON_FNR,
        organisasjonsnummer = orgnummer,
        utbetalingId = utbetalingId,
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        saksbehandler = "Ola Nordmann",
        saksbehandlerEpost = "ola.nordmann@nav.no",
        utbetalingGodkjent = utbetalingGodkjent,
        godkjenttidspunkt = LocalDateTime.now(),
        automatiskBehandling = automatiskBehandling,
    )

    protected fun utbetaling(
        fagsystemId: String,
        status: Oppdragstatus = AKSEPTERT,
        orgnummer: String = ORGNUMMER,
        meldingsreferanseId: UUID = UUID.randomUUID(),
        utbetalingId: UUID
    ) =
        UtbetalingHendelse(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR,
            orgnummer = orgnummer,
            fagsystemId = fagsystemId,
            utbetalingId = "$utbetalingId",
            status = status,
            melding = "hei",
            avstemmingsnøkkel = 123456L,
            overføringstidspunkt = LocalDateTime.now()
        )
}

internal fun interface IdInnhenter {
    fun id(orgnummer: String): UUID
}

