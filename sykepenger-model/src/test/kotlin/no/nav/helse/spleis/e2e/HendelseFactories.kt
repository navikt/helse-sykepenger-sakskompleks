package no.nav.helse.spleis.e2e

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.etterspurteBehov
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Foreldrepenger
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.InntekterForOpptjeningsvurdering
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Svangerskapspenger
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingpåminnelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.person.AbstractPersonTest
import no.nav.helse.person.AbstractPersonTest.Companion.AKTØRID
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FØDSELSDATO
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.sisteBehov
import no.nav.helse.testhelpers.Inntektperioder
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.økonomi.Inntekt
import no.nav.inntektsmeldingkontrakt.Arbeidsgivertype
import no.nav.inntektsmeldingkontrakt.AvsenderSystem
import no.nav.inntektsmeldingkontrakt.Refusjon
import no.nav.inntektsmeldingkontrakt.Status

internal fun AbstractEndToEndTest.utbetaling(
    fagsystemId: String,
    status: Oppdragstatus,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    meldingsreferanseId: UUID = UUID.randomUUID(),
    utbetalingId: UUID? = null
) =
    UtbetalingHendelse(
        meldingsreferanseId = meldingsreferanseId,
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        orgnummer = orgnummer,
        fagsystemId = fagsystemId,
        utbetalingId = utbetalingId?.toString() ?: person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Utbetaling).kontekst().getValue("utbetalingId"),
        status = status,
        melding = "hei",
        avstemmingsnøkkel = 123456L,
        overføringstidspunkt = LocalDateTime.now()
    ).apply {
        hendelselogg = this
    }

internal fun AbstractEndToEndTest.feriepengeutbetaling(
    fagsystemId: String,
    status: Oppdragstatus,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    meldingsreferanseId: UUID = UUID.randomUUID()
) =
    UtbetalingHendelse(
        meldingsreferanseId = meldingsreferanseId,
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        orgnummer = orgnummer,
        fagsystemId = fagsystemId,
        utbetalingId = person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Utbetaling).kontekst().getValue("utbetalingId"),
        status = status,
        melding = "hey",
        avstemmingsnøkkel = 654321L,
        overføringstidspunkt = LocalDateTime.now()
    ).apply {
        hendelselogg = this
    }

internal fun AbstractEndToEndTest.sykmelding(
    id: UUID,
    vararg sykeperioder: Sykmeldingsperiode,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    sykmeldingSkrevet: LocalDateTime? = null,
    mottatt: LocalDateTime? = null,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    fødselsdato: LocalDate = UNG_PERSON_FØDSELSDATO
) = ArbeidsgiverHendelsefabrikk(AKTØRID, fnr, orgnummer).lagSykmelding(
    sykeperioder = sykeperioder,
    id = id
).apply {
        hendelselogg = this
    }

internal fun AbstractEndToEndTest.søknad(
    id: UUID,
    vararg perioder: Søknadsperiode,
    andreInntektskilder: Boolean = false,
    sendtTilNAVEllerArbeidsgiver: LocalDate = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    sykmeldingSkrevet: LocalDateTime? = null,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    fødselsdato: LocalDate = UNG_PERSON_FØDSELSDATO,
    utenlandskSykmelding: Boolean = false,
    sendTilGosys: Boolean = false,
    korrigerer: UUID? = null,
    opprinneligSendt: LocalDate? = null,
    merknaderFraSykmelding: List<Søknad.Merknad> = emptyList(),
    permittert: Boolean = false,
    egenmeldinger: List<Periode> = emptyList()
) = ArbeidsgiverHendelsefabrikk(AKTØRID, fnr, orgnummer).lagSøknad(
    id = id,
    perioder = perioder,
    andreInntektskilder = andreInntektskilder,
    sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver,
    sykmeldingSkrevet = sykmeldingSkrevet ?: Søknadsperiode.søknadsperiode(perioder.toList())!!.start.atStartOfDay(),
    korrigerer = korrigerer,
    utenlandskSykmelding = utenlandskSykmelding,
    sendTilGosys = sendTilGosys,
    opprinneligSendt = opprinneligSendt,
    merknaderFraSykmelding = merknaderFraSykmelding,
    permittert = permittert,
    egenmeldinger = egenmeldinger
).apply {
    hendelselogg = this
}

internal fun AbstractEndToEndTest.inntektsmelding(
    id: UUID = UUID.randomUUID(),
    arbeidsgiverperioder: List<Periode>,
    beregnetInntekt: Inntekt = AbstractEndToEndTest.INNTEKT,
    førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOfOrNull { it.start } ?: 1.januar,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    harOpphørAvNaturalytelser: Boolean = false,
    arbeidsforholdId: String? = null,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
    fødselsdato: LocalDate = UNG_PERSON_FØDSELSDATO,
    harFlereInntektsmeldinger: Boolean = false
): Inntektsmelding {
    val inntektsmeldinggenerator = { aktivitetslogg: Aktivitetslogg ->
        ArbeidsgiverHendelsefabrikk(AKTØRID, fnr, orgnummer).lagInntektsmelding(
            id = id,
            refusjon = refusjon,
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = beregnetInntekt,
            arbeidsgiverperioder = arbeidsgiverperioder,
            arbeidsforholdId = arbeidsforholdId,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger,
            aktivitetslogg = aktivitetslogg
        )
    }
    val kontrakten = no.nav.inntektsmeldingkontrakt.Inntektsmelding(
        inntektsmeldingId = UUID.randomUUID().toString(),
        arbeidstakerFnr = fnr.toString(),
        arbeidstakerAktorId = AKTØRID,
        virksomhetsnummer = orgnummer,
        arbeidsgiverFnr = null,
        arbeidsgiverAktorId = null,
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsforholdId = null,
        beregnetInntekt = BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }),
        refusjon = Refusjon(BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }), null),
        endringIRefusjoner = emptyList(),
        opphoerAvNaturalytelser = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        arbeidsgiverperioder = arbeidsgiverperioder.map {
            no.nav.inntektsmeldingkontrakt.Periode(it.start, it.endInclusive)
        },
        status = Status.GYLDIG,
        arkivreferanse = "",
        ferieperioder = emptyList(),
        foersteFravaersdag = førsteFraværsdag,
        mottattDato = LocalDateTime.now(),
        begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
        naerRelasjon = null,
        avsenderSystem = AvsenderSystem("SpleisModell"),
        innsenderTelefon = "tlfnr",
        innsenderFulltNavn = "SPLEIS Modell"
    )
    inntektsmeldinger[id] = AbstractEndToEndTest.InnsendtInntektsmelding(LocalDateTime.now(), inntektsmeldinggenerator, kontrakten)
    inntekter[id] = beregnetInntekt
    EtterspurtBehov.fjern(ikkeBesvarteBehov, orgnummer, Aktivitet.Behov.Behovtype.Sykepengehistorikk)
    return inntektsmeldinggenerator(Aktivitetslogg()).apply { hendelselogg = this }
}

internal fun AbstractEndToEndTest.inntektsmeldingPortal(
    id: UUID = UUID.randomUUID(),
    arbeidsgiverperioder: List<Periode>,
    beregnetInntekt: Inntekt = AbstractEndToEndTest.INNTEKT,
    inntektsdato: LocalDate,
    vedtaksperiodeId: UUID,
    førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOfOrNull { it.start } ?: 1.januar,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    harOpphørAvNaturalytelser: Boolean = false,
    arbeidsforholdId: String? = null,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
    fødselsdato: LocalDate = UNG_PERSON_FØDSELSDATO,
    harFlereInntektsmeldinger: Boolean = false
): Inntektsmelding {
    val inntektsmeldinggenerator = { aktivitetslogg: Aktivitetslogg ->
        ArbeidsgiverHendelsefabrikk(AKTØRID, fnr, orgnummer).lagPortalinntektsmelding(
            id = id,
            refusjon = refusjon,
            inntektsdato = inntektsdato,
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = beregnetInntekt,
            vedtaksperiodeId = vedtaksperiodeId,
            arbeidsgiverperioder = arbeidsgiverperioder,
            arbeidsforholdId = arbeidsforholdId,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger,
            aktivitetslogg = aktivitetslogg
        )
    }
    val kontrakten = no.nav.inntektsmeldingkontrakt.Inntektsmelding(
        inntektsmeldingId = UUID.randomUUID().toString(),
        arbeidstakerFnr = fnr.toString(),
        arbeidstakerAktorId = AKTØRID,
        virksomhetsnummer = orgnummer,
        arbeidsgiverFnr = null,
        arbeidsgiverAktorId = null,
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsforholdId = null,
        beregnetInntekt = BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }),
        refusjon = Refusjon(BigDecimal.valueOf(beregnetInntekt.reflection { _, månedlig, _, _ -> månedlig }), null),
        endringIRefusjoner = emptyList(),
        opphoerAvNaturalytelser = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        arbeidsgiverperioder = arbeidsgiverperioder.map {
            no.nav.inntektsmeldingkontrakt.Periode(it.start, it.endInclusive)
        },
        status = Status.GYLDIG,
        arkivreferanse = "",
        ferieperioder = emptyList(),
        foersteFravaersdag = førsteFraværsdag,
        mottattDato = LocalDateTime.now(),
        begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
        naerRelasjon = null,
        avsenderSystem = AvsenderSystem("NAV_NO"),
        innsenderTelefon = "tlfnr",
        innsenderFulltNavn = "SPLEIS Modell"
    )
    inntektsmeldinger[id] = AbstractEndToEndTest.InnsendtInntektsmelding(LocalDateTime.now(), inntektsmeldinggenerator, kontrakten)
    inntekter[id] = beregnetInntekt
    EtterspurtBehov.fjern(ikkeBesvarteBehov, orgnummer, Aktivitet.Behov.Behovtype.Sykepengehistorikk)
    return inntektsmeldinggenerator(Aktivitetslogg()).apply { hendelselogg = this }
}

internal fun AbstractEndToEndTest.vilkårsgrunnlag(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    skjæringstidspunkt: LocalDate,
    medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold> = listOf(Vilkårsgrunnlag.Arbeidsforhold(orgnummer, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT)),
    inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag,
    inntekterForOpptjeningsvurdering: InntekterForOpptjeningsvurdering,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018
): Vilkårsgrunnlag {
    return Vilkårsgrunnlag(
        meldingsreferanseId = UUID.randomUUID(),
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        skjæringstidspunkt = skjæringstidspunkt,
        aktørId = AKTØRID,
        personidentifikator = fnr,
        orgnummer = orgnummer,
        medlemskapsvurdering = Medlemskapsvurdering(medlemskapstatus),
        inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag,
        inntekterForOpptjeningsvurdering = inntekterForOpptjeningsvurdering,
        arbeidsforhold = arbeidsforhold
    ).apply {
        hendelselogg = this
    }
}

internal fun utbetalingpåminnelse(
    utbetalingId: UUID,
    status: Utbetalingstatus,
    tilstandsendringstidspunkt: LocalDateTime,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER
): Utbetalingpåminnelse {
    return Utbetalingpåminnelse(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        organisasjonsnummer = orgnummer,
        utbetalingId = utbetalingId,
        antallGangerPåminnet = 0,
        status = status,
        endringstidspunkt = tilstandsendringstidspunkt,
        påminnelsestidspunkt = LocalDateTime.now()
    )
}

internal fun påminnelse(
    vedtaksperiodeId: UUID,
    påminnetTilstand: TilstandType,
    tilstandsendringstidspunkt: LocalDateTime,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    antallGangerPåminnet: Int = 1,
    skalReberegnes: Boolean = false
): Påminnelse {
    return Påminnelse(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        organisasjonsnummer = orgnummer,
        vedtaksperiodeId = vedtaksperiodeId.toString(),
        antallGangerPåminnet = antallGangerPåminnet,
        tilstand = påminnetTilstand,
        tilstandsendringstidspunkt = tilstandsendringstidspunkt,
        påminnelsestidspunkt = LocalDateTime.now(),
        nestePåminnelsestidspunkt = LocalDateTime.now(),
        ønskerReberegning = skalReberegnes,
        opprettet = LocalDateTime.now()
    )
}

internal fun AbstractEndToEndTest.utbetalingshistorikk(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    utbetalinger: List<Infotrygdperiode> = listOf(),
    inntektshistorikk: List<Inntektsopplysning> = emptyList(),
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    besvart: LocalDateTime = LocalDateTime.now(),
): Utbetalingshistorikk {
    return Utbetalingshistorikk(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        organisasjonsnummer = orgnummer,
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        besvart = LocalDateTime.now(),
        element = InfotrygdhistorikkElement.opprett(
            oppdatert = besvart,
            hendelseId = UUID.randomUUID(),
            perioder = utbetalinger,
            inntekter = inntektshistorikk,
            arbeidskategorikoder = emptyMap()
        )
    ).apply {
        hendelselogg = this
    }
}

internal fun AbstractEndToEndTest.utbetalingshistorikkEtterInfotrygdEndring(
    meldingsreferanseId : UUID = UUID.randomUUID(),
    utbetalinger: List<Infotrygdperiode> = listOf(),
    inntektshistorikk: List<Inntektsopplysning> = emptyList(),
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    arbeidskategorikoder: Map<String, LocalDate> = emptyMap(),
    besvart: LocalDateTime = LocalDateTime.now(),
): UtbetalingshistorikkEtterInfotrygdendring {
    return UtbetalingshistorikkEtterInfotrygdendring(
        meldingsreferanseId = meldingsreferanseId,
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        element = InfotrygdhistorikkElement.opprett(
            oppdatert = besvart,
            hendelseId = meldingsreferanseId,
            perioder = utbetalinger,
            inntekter = inntektshistorikk,
            arbeidskategorikoder = arbeidskategorikoder
        ),
        besvart = LocalDateTime.now()
    ).apply {
        hendelselogg = this
    }
}

internal fun AbstractEndToEndTest.utbetalingshistorikkForFeriepenger(
    utbetalinger: List<UtbetalingshistorikkForFeriepenger.Utbetalingsperiode> = listOf(),
    feriepengehistorikk: List<UtbetalingshistorikkForFeriepenger.Feriepenger> = listOf(),
    arbeidskategorikoder: UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder =
        UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(
            listOf(
                UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.KodePeriode(
                    LocalDate.MIN til LocalDate.MAX,
                    UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.Arbeidskategorikode.Arbeidstaker
                )
            )
        ),
    opptjeningsår: Year = Year.of(2017),
    skalBeregnesManuelt: Boolean
): UtbetalingshistorikkForFeriepenger {
    return UtbetalingshistorikkForFeriepenger(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AKTØRID,
        fødselsnummer = AbstractPersonTest.UNG_PERSON_FNR_2018.toString(),
        utbetalinger = utbetalinger,
        feriepengehistorikk = feriepengehistorikk,
        arbeidskategorikoder = arbeidskategorikoder,
        opptjeningsår = opptjeningsår,
        skalBeregnesManuelt = skalBeregnesManuelt
    ).apply {
        hendelselogg = this
    }
}

internal fun AbstractEndToEndTest.ytelser(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    foreldrepenger: List<GradertPeriode> = emptyList(),
    svangerskapspenger: List<GradertPeriode> = emptyList(),
    pleiepenger: List<GradertPeriode> = emptyList(),
    omsorgspenger: List<GradertPeriode> = emptyList(),
    opplæringspenger: List<GradertPeriode> = emptyList(),
    institusjonsoppholdsperioder: List<Institusjonsopphold.Institusjonsoppholdsperiode> = emptyList(),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    arbeidsavklaringspenger: List<Periode> = emptyList(),
    dagpenger: List<Periode> = emptyList(),
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018
): Ytelser {
    val aktivitetslogg = Aktivitetslogg()
    val meldingsreferanseId = UUID.randomUUID()
    return Ytelser(
        meldingsreferanseId = meldingsreferanseId,
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        organisasjonsnummer = orgnummer,
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        foreldrepenger = Foreldrepenger(
            foreldrepengeytelse = foreldrepenger
        ),
        svangerskapspenger = Svangerskapspenger(
            svangerskapsytelse = svangerskapspenger
        ),
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
    ).apply {
        hendelselogg = this
    }
}

internal fun manuellPermisjonsdag(dato: LocalDate) = ManuellOverskrivingDag(dato, Dagtype.Permisjonsdag)
internal fun manuellFeriedag(dato: LocalDate) = ManuellOverskrivingDag(dato, Dagtype.Feriedag)
internal fun manuellForeldrepengedag(dato: LocalDate) = ManuellOverskrivingDag(dato, Dagtype.Foreldrepengerdag)
internal fun manuellSykedag(dato: LocalDate, grad: Int = 100) = ManuellOverskrivingDag(dato, Dagtype.Sykedag, grad)
internal fun manuellArbeidsgiverdag(dato: LocalDate) = ManuellOverskrivingDag(dato, Dagtype.Egenmeldingsdag)


internal fun AbstractEndToEndTest.simulering(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    simuleringOK: Boolean = true,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    simuleringsresultat: SimuleringResultatDto? = standardSimuleringsresultat(orgnummer)
) = person.personLogg.etterspurteBehov(vedtaksperiodeIdInnhenter, orgnummer).filter { it.type == Aktivitet.Behov.Behovtype.Simulering }.map { simuleringsBehov ->
    Simulering(
        meldingsreferanseId = UUID.randomUUID(),
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        aktørId = AKTØRID,
        fødselsnummer = fnr.toString(),
        orgnummer = orgnummer,
        fagsystemId = simuleringsBehov.detaljer().getValue("fagsystemId") as String,
        fagområde = simuleringsBehov.detaljer().getValue("fagområde") as String,
        simuleringOK = simuleringOK,
        melding = "",
        utbetalingId = UUID.fromString(simuleringsBehov.kontekst().getValue("utbetalingId")),
        simuleringResultat = simuleringsresultat
    ).apply {
        hendelselogg = this
    }
}

internal fun standardSimuleringsresultat(orgnummer: String, totalbeløp: Int = 2000) = SimuleringResultatDto(
    totalbeløp = totalbeløp,
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

internal fun AbstractEndToEndTest.utbetalingsgodkjenning(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    utbetalingGodkjent: Boolean,
    fnr: Personidentifikator = AbstractPersonTest.UNG_PERSON_FNR_2018,
    orgnummer: String,
    automatiskBehandling: Boolean,
    utbetalingId: UUID = UUID.fromString(
        person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Godkjenning).kontekst()["utbetalingId"]
            ?: throw IllegalStateException(
                "Finner ikke utbetalingId i: ${
                    person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Godkjenning).kontekst()
                }"
            )
    ),
) = Utbetalingsgodkjenning(
    meldingsreferanseId = UUID.randomUUID(),
    aktørId = AKTØRID,
    fødselsnummer = fnr.toString(),
    organisasjonsnummer = orgnummer,
    utbetalingId = utbetalingId,
    vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
    saksbehandler = "Ola Nordmann",
    saksbehandlerEpost = "ola.nordmann@nav.no",
    utbetalingGodkjent = utbetalingGodkjent,
    godkjenttidspunkt = LocalDateTime.now(),
    automatiskBehandling = automatiskBehandling,
).apply {
    hendelselogg = this
}

internal fun inntektsvurderingForSykepengegrunnlag(inntekt: Inntekt, skjæringstidspunkt: LocalDate, vararg orgnummere: String) = InntektForSykepengegrunnlag(
    inntekter = orgnummere.map { orgnummer ->
        grunnlag(orgnummer, skjæringstidspunkt, inntekt.repeat(3))
    },
    arbeidsforhold = emptyList()
)

internal fun grunnlag(
    orgnummer: String,
    skjæringstidspunkt: LocalDate,
    inntekter: List<Inntekt>
) = lagMånedsinntekter(orgnummer, skjæringstidspunkt, inntekter)

private fun lagMånedsinntekter(
    orgnummer: String,
    skjæringstidspunkt: LocalDate,
    inntekter: List<Inntekt>
) = ArbeidsgiverInntekt(
    orgnummer, inntekter.mapIndexed { index, inntekt ->
        val sluttMnd = YearMonth.from(skjæringstidspunkt)
        ArbeidsgiverInntekt.MånedligInntekt(
            sluttMnd.minusMonths((inntekter.size - index).toLong()),
            inntekt,
            ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
            "Juidy inntekt",
            "Juidy fordel"
        )
    }
)

internal fun Inntektperioder.lagInntektperioder(
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fom: LocalDate,
    inntekt: Inntekt = AbstractEndToEndTest.INNTEKT
) =
    fom.minusYears(1) til fom.minusMonths(1) inntekter {
        orgnummer inntekt inntekt
    }
