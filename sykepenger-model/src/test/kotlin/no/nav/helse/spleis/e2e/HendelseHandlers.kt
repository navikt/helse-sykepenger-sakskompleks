package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.etterspurteBehov
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingReplayUtført
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.person.AbstractPersonTest
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.Person
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.sisteBehov
import no.nav.helse.spleis.e2e.AbstractEndToEndTest.Companion.INNTEKT
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning.Companion.tilOverstyrt
import no.nav.helse.testhelpers.Inntektperioder
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.*
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail


internal fun AbstractEndToEndTest.håndterSykmelding(
    vararg sykeperioder: Sykmeldingsperiode,
    sykmeldingSkrevet: LocalDateTime? = null,
    mottatt: LocalDateTime? = null,
    id: UUID = UUID.randomUUID(),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
): UUID {
    sykmelding(
        id,
        *sykeperioder,
        sykmeldingSkrevet = sykmeldingSkrevet,
        mottatt = mottatt,
        orgnummer = orgnummer,
        fnr = fnr
    ).håndter(Person::håndter)
    sykmeldinger[id] = sykeperioder
    return id
}

internal fun AbstractEndToEndTest.tilGodkjenning(fom: LocalDate, tom: LocalDate, vararg organisasjonsnummere: String, beregnetInntekt: Inntekt = 20000.månedlig, inntekterBlock: Inntektperioder.() -> Unit = {
    fom.minusYears(1) til fom.minusMonths(1) inntekter {
        organisasjonsnummere.forEach {
            it inntekt beregnetInntekt
        }
    }
}) {
    require(organisasjonsnummere.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnummere.forEach { nyPeriode(fom til tom, it) }
    organisasjonsnummere.forEach {
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(fom, fom.plusDays(15))),
            beregnetInntekt = beregnetInntekt,
            orgnummer = it
        )
    }

    organisasjonsnummere.first().let { organisasjonsnummer ->
        val vedtaksperiode = observatør.sisteVedtaksperiode()
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = vedtaksperiode,
            inntekt = beregnetInntekt,
            orgnummer = organisasjonsnummer,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                inntekterBlock()
            })
        )
        håndterYtelser(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterSimulering(vedtaksperiode, orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.nyeVedtak(
    fom: LocalDate,
    tom: LocalDate,
    vararg organisasjonsnummere: String,
    inntekt: Inntekt = 20000.månedlig,
    inntekterBlock: Inntektperioder.() -> Unit = {
        organisasjonsnummere.forEach {
            lagInntektperioder(it, fom, inntekt)
        }
    }
) {
    require(organisasjonsnummere.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    tilGodkjenning(fom, tom, *organisasjonsnummere, inntekterBlock = inntekterBlock, beregnetInntekt = inntekt)
    val (første, resten) = organisasjonsnummere.first() to organisasjonsnummere.drop(1)

    første.let { organisasjonsnummer ->
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterUtbetalt(orgnummer = organisasjonsnummer)
    }

    resten.forEach { organisasjonsnummer ->
        håndterYtelser(1.vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterSimulering(1.vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterUtbetalt(orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.førstegangTilGodkjenning(
    fom: LocalDate,
    tom: LocalDate,
    vararg organisasjonsnummere: String,
    inntekterBlock: Inntektperioder.() -> Unit = {
        organisasjonsnummere.forEach {
            lagInntektperioder(it, fom, 20000.månedlig)
        }
    }
) {
    require(organisasjonsnummere.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnummere.forEach {
        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = it)
    }
    organisasjonsnummere.forEach {
        håndterSøknad(Søknadsperiode.Sykdom(fom, tom, 100.prosent), orgnummer = it)

    }
    organisasjonsnummere.forEach {
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(fom, fom.plusDays(15))),
            beregnetInntekt = 20000.månedlig,
            orgnummer = it
        )
    }

    val vedtaksperiode = observatør.sisteVedtaksperiode()

    organisasjonsnummere.first().let { organisasjonsnummer ->
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = vedtaksperiode,
            orgnummer = organisasjonsnummer,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                inntekterBlock()
            })
        )
        håndterYtelser(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterSimulering(vedtaksperiode, orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.forlengelseTilGodkjenning(fom: LocalDate, tom: LocalDate, vararg organisasjonsnumre: String) {
    require(organisasjonsnumre.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnumre.forEach { nyPeriode(fom til tom, it) }
    håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnumre.first())
    håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnumre.first())
}

internal fun AbstractEndToEndTest.forlengVedtak(fom: LocalDate, tom: LocalDate, vararg organisasjonsnumre: String) {
    require(organisasjonsnumre.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnumre.forEach { håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = it) }
    organisasjonsnumre.forEach { håndterSøknad(Søknadsperiode.Sykdom(fom, tom, 100.prosent), orgnummer = it) }
    organisasjonsnumre.forEach { organisasjonsnummer ->
        håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnummer)
        håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnummer)
        håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnummer)
        håndterUtbetalt(orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.nyttVedtak(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel = 100.prosent,
    førsteFraværsdag: LocalDate = fom,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode>? = null,
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    inntekterBlock: Inntektperioder.() -> Unit = { lagInntektperioder(orgnummer, fom, beregnetInntekt) }
) {
    tilGodkjent(fom, tom, grad, førsteFraværsdag, fnr = fnr, orgnummer = orgnummer, refusjon = refusjon, inntekterBlock = inntekterBlock, arbeidsgiverperiode = arbeidsgiverperiode, beregnetInntekt = beregnetInntekt)
    håndterUtbetalt(status = status, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.tilGodkjent(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel,
    førsteFraværsdag: LocalDate,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    inntekterBlock: Inntektperioder.() -> Unit = { lagInntektperioder(orgnummer, fom, beregnetInntekt) },
    arbeidsgiverperiode: List<Periode>? = null
): IdInnhenter {
    val id = tilGodkjenning(fom, tom, grad, førsteFraværsdag, fnr = fnr, orgnummer = orgnummer, refusjon = refusjon, inntekterBlock = inntekterBlock, arbeidsgiverperiode = arbeidsgiverperiode, beregnetInntekt = beregnetInntekt)
    håndterUtbetalingsgodkjenning(id, true, fnr = fnr, orgnummer = orgnummer)
    return id
}

internal fun AbstractEndToEndTest.tilGodkjenning(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel,
    førsteFraværsdag: LocalDate,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    inntekterBlock: Inntektperioder.() -> Unit = { lagInntektperioder(orgnummer, fom, beregnetInntekt) },
    arbeidsgiverperiode: List<Periode>? = null,
): IdInnhenter {
    val id = tilSimulering(fom, tom, grad, førsteFraværsdag, fnr = fnr, orgnummer = orgnummer, refusjon = refusjon, inntekterBlock = inntekterBlock, arbeidsgiverperiode = arbeidsgiverperiode, beregnetInntekt = beregnetInntekt)
    håndterSimulering(id, fnr = fnr, orgnummer = orgnummer)
    return id
}

internal fun AbstractEndToEndTest.tilSimulering(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel,
    førsteFraværsdag: LocalDate,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    inntekterBlock: Inntektperioder.() -> Unit = { lagInntektperioder(orgnummer, fom, beregnetInntekt) },
    arbeidsgiverperiode: List<Periode>? = null
): IdInnhenter {
    return tilYtelser(fom, tom, grad, førsteFraværsdag, fnr = fnr, orgnummer = orgnummer, refusjon = refusjon, inntekterBlock = inntekterBlock, arbeidsgiverperiode = arbeidsgiverperiode, beregnetInntekt = beregnetInntekt)
}

internal fun AbstractEndToEndTest.tilYtelser(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel,
    førsteFraværsdag: LocalDate,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    inntekterBlock: Inntektperioder.() -> Unit = { lagInntektperioder(orgnummer, fom, beregnetInntekt) },
    inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag = InntektForSykepengegrunnlag(
        inntekter = inntektperioderForSykepengegrunnlag {
            fom.minusMonths(3) til fom.minusMonths(1) inntekter {
                orgnummer inntekt beregnetInntekt
            }
        }, arbeidsforhold = emptyList()
    ),
    arbeidsgiverperiode: List<Periode>? = null
): IdInnhenter {
    håndterSykmelding(Sykmeldingsperiode(fom, tom), fnr = fnr, orgnummer = orgnummer)
    håndterSøknad(Søknadsperiode.Sykdom(fom, tom, grad), fnr = fnr, orgnummer = orgnummer)
    håndterInntektsmelding(
        arbeidsgiverperiode ?: listOf(Periode(fom, fom.plusDays(15))),
        førsteFraværsdag = førsteFraværsdag,
        fnr = fnr,
        orgnummer = orgnummer,
        refusjon = refusjon,
        beregnetInntekt = beregnetInntekt
    )
    val id = observatør.sisteVedtaksperiode()
    håndterVilkårsgrunnlag(
        vedtaksperiodeIdInnhenter = id,
        inntekt = beregnetInntekt,
        fnr = fnr,
        orgnummer = orgnummer,
        inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag(inntekterBlock)
        ),
        inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag
    )
    håndterYtelser(id, fnr = fnr, orgnummer = orgnummer, besvart = LocalDate.EPOCH.atStartOfDay())
    return id
}

internal fun AbstractEndToEndTest.forlengTilGodkjentVedtak(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel = 100.prosent,
    besvart: LocalDateTime = LocalDateTime.now(),
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER
) {
    forlengTilGodkjenning(fom, tom, grad, besvart, fnr, orgnummer)
    håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), true, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forlengTilSimulering(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER
) {
    nyPeriode(fom til tom, orgnummer, grad = grad, fnr = fnr)
    val id: IdInnhenter = observatør.sisteVedtaksperiode()
    håndterYtelser(id, fnr = fnr, orgnummer = orgnummer)
    assertTrue(person.personLogg.etterspurteBehov(id, Behovtype.Simulering, orgnummer)) { "Forventet at simulering er etterspurt" }
}

internal fun AbstractEndToEndTest.forlengTilGodkjenning(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel = 100.prosent,
    besvart: LocalDateTime = LocalDateTime.now(),
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER
) {
    nyPeriode(fom til tom, orgnummer, grad = grad, fnr = fnr)
    val id: IdInnhenter = observatør.sisteVedtaksperiode()
    håndterYtelser(id, fnr = fnr, orgnummer = orgnummer, besvart = besvart)
    if (person.personLogg.etterspurteBehov(id, Behovtype.Simulering, orgnummer)) håndterSimulering(id, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forlengVedtak(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel = 100.prosent,
    besvart: LocalDateTime = LocalDateTime.now(),
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER
) {
    forlengTilGodkjentVedtak(fom, tom, grad, besvart, fnr, orgnummer)
    håndterUtbetalt(status = Oppdragstatus.AKSEPTERT, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forlengPeriode(
    fom: LocalDate,
    tom: LocalDate,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER
) {
    nyPeriode(fom til tom, orgnummer, grad = grad, fnr = fnr)
}

internal fun AbstractEndToEndTest.håndterSøknadMedValidering(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    vararg perioder: Søknadsperiode,
    andreInntektskilder: Boolean = false,
    sendtTilNAVEllerArbeidsgiver: LocalDate = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
) {
    assertIkkeEtterspurt(
        Søknad::class, Behovtype.InntekterForSammenligningsgrunnlag, vedtaksperiodeIdInnhenter,
        AbstractPersonTest.ORGNUMMER
    )
    håndterSøknad(
        *perioder,
        andreInntektskilder = andreInntektskilder,
        sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver,
        orgnummer = orgnummer,
        fnr = fnr
    )
}

internal fun AbstractEndToEndTest.håndterSøknad(
    vararg perioder: Søknadsperiode,
    andreInntektskilder: Boolean = false,
    sendtTilNAVEllerArbeidsgiver: LocalDate = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive,
    id: UUID = UUID.randomUUID(),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    sykmeldingSkrevet: LocalDateTime? = null,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    korrigerer: UUID? = null,
    utenlandskSykmelding: Boolean = false,
    sendTilGosys: Boolean = false,
    opprinneligSendt: LocalDate? = null,
    merknaderFraSykmelding: List<Søknad.Merknad> = emptyList(),
    permittert: Boolean = false
): UUID {
    håndterOgReplayInntektsmeldinger(orgnummer) {
        søknad(
            id,
            *perioder,
            andreInntektskilder = andreInntektskilder,
            sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver,
            orgnummer = orgnummer,
            sykmeldingSkrevet = sykmeldingSkrevet,
            fnr = fnr,
            korrigerer = korrigerer,
            utenlandskSykmelding = utenlandskSykmelding,
            sendTilGosys = sendTilGosys,
            opprinneligSendt = opprinneligSendt,
            merknaderFraSykmelding = merknaderFraSykmelding,
            permittert = permittert
        ).håndter(Person::håndter)
        søknader[id] = Triple(sendtTilNAVEllerArbeidsgiver, andreInntektskilder, perioder)
        val vedtaksperiodeId: IdInnhenter = observatør.sisteVedtaksperiode()
        if (hendelselogg.etterspurteBehov(vedtaksperiodeId, Behovtype.Sykepengehistorikk, orgnummer = orgnummer)) {
            håndterUtbetalingshistorikk(vedtaksperiodeId, orgnummer = orgnummer)
        }
    }
    return id
}

private fun AbstractEndToEndTest.håndterOgReplayInntektsmeldinger(orgnummer: String, block: () -> Unit) {
    observatør.replayInntektsmeldinger { block() }.forEach { vedtaksperiodeId ->
        inntektsmeldinger
            .mapValues { (_, gen) -> gen.first to gen.second() }
            .filterValues { (_, im) -> im.organisasjonsnummer() == orgnummer }
            .entries
            .sortedBy { (_, value) -> value.first }
            .forEach { (id, _) ->
                håndterInntektsmeldingReplay(id, vedtaksperiodeId)
            }
        håndterInntektsmeldingReplayUtført(vedtaksperiodeId, orgnummer)
        observatør.kvitterInntektsmeldingReplay(vedtaksperiodeId)
    }
}

internal fun AbstractEndToEndTest.håndterInntektsmeldingMedValidering(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    arbeidsgiverperioder: List<Periode>,
    førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOfOrNull { it.start } ?: 1.januar,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
): UUID {
    assertIkkeEtterspurt(Inntektsmelding::class, Behovtype.InntekterForSammenligningsgrunnlag, vedtaksperiodeIdInnhenter, orgnummer)
    return håndterInntektsmelding(arbeidsgiverperioder, førsteFraværsdag, beregnetInntekt = beregnetInntekt, refusjon, orgnummer = orgnummer, fnr = fnr)
}

internal fun AbstractEndToEndTest.håndterInntektsmelding(
    arbeidsgiverperioder: List<Periode>,
    førsteFraværsdag: LocalDate = arbeidsgiverperioder.maxOfOrNull { it.start } ?: 1.januar,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    id: UUID = UUID.randomUUID(),
    harOpphørAvNaturalytelser: Boolean = false,
    arbeidsforholdId: String? = null,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
    harFlereInntektsmeldinger: Boolean = false,
    førReplay: () -> Unit = {}
): UUID {
    håndterOgReplayInntektsmeldinger(orgnummer) {
        inntektsmelding(
            id,
            arbeidsgiverperioder,
            beregnetInntekt = beregnetInntekt,
            førsteFraværsdag = førsteFraværsdag,
            refusjon = refusjon,
            orgnummer = orgnummer,
            harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
            arbeidsforholdId = arbeidsforholdId,
            fnr = fnr,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger
        ).håndter(Person::håndter)
        førReplay()
    }
    return id
}

private fun AbstractEndToEndTest.håndterInntektsmeldingReplay(
    inntektsmeldingId: UUID,
    vedtaksperiodeId: UUID
) {
    val inntektsmeldinggenerator = inntektsmeldinger[inntektsmeldingId]?.second ?: fail { "Fant ikke inntektsmelding med id $inntektsmeldingId" }
    inntektsmeldingReplay(inntektsmeldinggenerator(), vedtaksperiodeId)
        .håndter(Person::håndter)
}

private fun AbstractEndToEndTest.håndterInntektsmeldingReplayUtført(vedtaksperiodeId: UUID, orgnummer: String) {
    person.håndter(InntektsmeldingReplayUtført(UUID.randomUUID(), UNG_PERSON_FNR_2018.toString(), "aktør", orgnummer, vedtaksperiodeId))
}

internal fun YearMonth.lønnsinntekt(inntekt: Inntekt = INNTEKT) =
    ArbeidsgiverInntekt.MånedligInntekt(
        yearMonth = this,
        type = ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
        inntekt = inntekt,
        fordel = "fordel",
        beskrivelse = "beskrivelse"
    )

internal fun AbstractEndToEndTest.håndterVilkårsgrunnlag(
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    inntekt: Inntekt = INNTEKT,
    medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    inntektsvurdering: Inntektsvurdering = Inntektsvurdering(
        inntekter = inntektperioderForSammenligningsgrunnlag {
            val skjæringstidspunkt = inspektør(orgnummer).skjæringstidspunkt(vedtaksperiodeIdInnhenter)
            skjæringstidspunkt.minusMonths(12L).withDayOfMonth(1) til skjæringstidspunkt.minusMonths(1L).withDayOfMonth(1) inntekter {
                orgnummer inntekt inntekt
            }
        }
    ),
    inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag = InntektForSykepengegrunnlag(
        inntekter = listOf(
            ArbeidsgiverInntekt(orgnummer, (0..2).map {
                val yearMonth = YearMonth.from(inspektør(orgnummer).skjæringstidspunkt(vedtaksperiodeIdInnhenter)).minusMonths(3L - it)
                ArbeidsgiverInntekt.MånedligInntekt(
                    yearMonth = yearMonth,
                    type = ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
                    inntekt = inntekt,
                    fordel = "fordel",
                    beskrivelse = "beskrivelse"
                )
            })
        ), arbeidsforhold = emptyList()
    ),
    arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold> = finnArbeidsgivere().map { Vilkårsgrunnlag.Arbeidsforhold(it, LocalDate.EPOCH, null) },
    fnr: Personidentifikator = UNG_PERSON_FNR_2018
): Vilkårsgrunnlag {
    fun assertEtterspurt(behovtype: Behovtype) =
        assertEtterspurt(Vilkårsgrunnlag::class, behovtype, vedtaksperiodeIdInnhenter, orgnummer)

    assertEtterspurt(Behovtype.InntekterForSammenligningsgrunnlag)
    assertEtterspurt(Behovtype.InntekterForSykepengegrunnlag)
    assertEtterspurt(Behovtype.ArbeidsforholdV2)
    assertEtterspurt(Behovtype.Medlemskap)
    return vilkårsgrunnlag(
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter,
        skjæringstidspunkt = finnSkjæringstidspunkt(orgnummer, vedtaksperiodeIdInnhenter),
        medlemskapstatus = medlemskapstatus,
        orgnummer = orgnummer,
        arbeidsforhold = arbeidsforhold,
        inntektsvurdering = inntektsvurdering,
        inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag,
        fnr = fnr
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterSimulering(
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    simuleringOK: Boolean = true,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    simuleringsresultat: SimuleringResultat? = standardSimuleringsresultat(orgnummer)
) {
    assertEtterspurt(Simulering::class, Behovtype.Simulering, vedtaksperiodeIdInnhenter, orgnummer)
    simulering(vedtaksperiodeIdInnhenter, simuleringOK, fnr, orgnummer, simuleringsresultat).forEach { simulering -> simulering.håndter(Person::håndter) }
}

internal fun AbstractEndToEndTest.håndterSimulering(
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    utbetalingId: UUID,
    fagsystemId: String,
    fagområde: Fagområde,
    simuleringOK: Boolean = true,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    simuleringsresultat: SimuleringResultat? = standardSimuleringsresultat(orgnummer)
) {
    assertEtterspurt(Simulering::class, Behovtype.Simulering, vedtaksperiodeIdInnhenter, orgnummer)
    Simulering(
        meldingsreferanseId = UUID.randomUUID(),
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        aktørId = AbstractPersonTest.AKTØRID,
        fødselsnummer = fnr.toString(),
        orgnummer = orgnummer,
        fagsystemId = fagsystemId,
        fagområde = fagområde.toString(),
        simuleringOK = simuleringOK,
        melding = "",
        utbetalingId = utbetalingId,
        simuleringResultat = simuleringsresultat
    ).apply {
        hendelselogg = this
    }.håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterInfotrygdendring() {
    Infotrygdendring(UUID.randomUUID(), UNG_PERSON_FNR_2018.toString(), AbstractPersonTest.AKTØRID)
        .håndter(Person::håndter)
}

private fun AbstractEndToEndTest.håndterUtbetalingshistorikk(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    vararg utbetalinger: Infotrygdperiode,
    inntektshistorikk: List<Inntektsopplysning> = emptyList(),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    statslønn: Boolean = false,
    besvart: LocalDateTime = LocalDateTime.now()
) {
    val bedtOmSykepengehistorikk = person.personLogg.etterspurteBehov(vedtaksperiodeIdInnhenter, Behovtype.Sykepengehistorikk, orgnummer)
    if (bedtOmSykepengehistorikk) assertEtterspurt(Utbetalingshistorikk::class, Behovtype.Sykepengehistorikk, vedtaksperiodeIdInnhenter, orgnummer)
    utbetalingshistorikk(
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter,
        utbetalinger = utbetalinger.toList(),
        inntektshistorikk = inntektshistorikk,
        orgnummer = orgnummer,
        besvart = besvart
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterUtbetalingshistorikkEtterInfotrygdendring(
    vararg utbetalinger: Infotrygdperiode,
    inntektshistorikk: List<Inntektsopplysning> = emptyList(),
    arbeidskategorikoder: Map<String, LocalDate> = emptyMap(),
    besvart: LocalDateTime = LocalDateTime.now(),
    meldingsreferanseId : UUID = UUID.randomUUID()
): UUID {
    utbetalingshistorikkEtterInfotrygdEndring(
        meldingsreferanseId = meldingsreferanseId,
        utbetalinger = utbetalinger.toList(),
        inntektshistorikk = inntektshistorikk,
        arbeidskategorikoder = arbeidskategorikoder,
        besvart = besvart
    ).håndter(Person::håndter)
    return meldingsreferanseId
}

internal fun Inntekt.repeat(antall: Int) = (0.until(antall)).map { this }

private fun AbstractPersonTest.finnArbeidsgivere() = person.inspektør.arbeidsgivere()

internal fun AbstractEndToEndTest.håndterYtelser(
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    vararg utbetalinger: Infotrygdperiode,
    inntektshistorikk: List<Inntektsopplysning> = emptyList(),
    foreldrepenger: Periode? = null,
    pleiepenger: List<Periode> = emptyList(),
    omsorgspenger: List<Periode> = emptyList(),
    opplæringspenger: List<Periode> = emptyList(),
    institusjonsoppholdsperioder: List<Institusjonsopphold.Institusjonsoppholdsperiode> = emptyList(),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    dødsdato: LocalDate? = null,
    statslønn: Boolean = false,
    arbeidskategorikoder: Map<String, LocalDate> = emptyMap(),
    arbeidsavklaringspenger: List<Periode> = emptyList(),
    dagpenger: List<Periode> = emptyList(),
    besvart: LocalDateTime = LocalDateTime.now(),
    fnr: Personidentifikator = UNG_PERSON_FNR_2018
) {
    fun assertEtterspurt(behovtype: Behovtype) =
        assertEtterspurt(Ytelser::class, behovtype, vedtaksperiodeIdInnhenter, orgnummer)

    assertEtterspurt(Behovtype.Foreldrepenger)
    assertEtterspurt(Behovtype.Pleiepenger)
    assertEtterspurt(Behovtype.Omsorgspenger)
    assertEtterspurt(Behovtype.Opplæringspenger)
    assertEtterspurt(Behovtype.Arbeidsavklaringspenger)
    assertEtterspurt(Behovtype.Dagpenger)
    assertEtterspurt(Behovtype.Institusjonsopphold)
    assertEtterspurt(Behovtype.Dødsinfo)

    ytelser(
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter,
        foreldrepenger = foreldrepenger,
        pleiepenger = pleiepenger,
        omsorgspenger = omsorgspenger,
        opplæringspenger = opplæringspenger,
        institusjonsoppholdsperioder = institusjonsoppholdsperioder,
        orgnummer = orgnummer,
        dødsdato = dødsdato,
        arbeidsavklaringspenger = arbeidsavklaringspenger,
        dagpenger = dagpenger,
        fnr = fnr
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterUtbetalingpåminnelse(
    utbetalingIndeks: Int,
    status: Utbetalingstatus,
    tilstandsendringstidspunkt: LocalDateTime = LocalDateTime.now()
) {
    utbetalingpåminnelse(inspektør.utbetalingId(utbetalingIndeks), status, tilstandsendringstidspunkt).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterPåminnelse(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    påminnetTilstand: TilstandType,
    tilstandsendringstidspunkt: LocalDateTime = LocalDateTime.now(),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    antallGangerPåminnet: Int = 1,
    skalReberegnes: Boolean = false
) {
    påminnelse(
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer),
        påminnetTilstand = påminnetTilstand,
        tilstandsendringstidspunkt = tilstandsendringstidspunkt,
        orgnummer = orgnummer,
        antallGangerPåminnet = antallGangerPåminnet,
        skalReberegnes = skalReberegnes
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterUtbetalingsgodkjenning(
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    utbetalingGodkjent: Boolean = true,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    automatiskBehandling: Boolean = false,
    utbetalingId: UUID = UUID.fromString(
        person.personLogg.sisteBehov(Behovtype.Godkjenning).kontekst()["utbetalingId"]
            ?: throw IllegalStateException("Finner ikke utbetalingId i: ${person.personLogg.sisteBehov(Behovtype.Godkjenning).kontekst()}")
    ),
) {
    assertEtterspurt(Utbetalingsgodkjenning::class, Behovtype.Godkjenning, vedtaksperiodeIdInnhenter, orgnummer)
    utbetalingsgodkjenning(vedtaksperiodeIdInnhenter, utbetalingGodkjent, fnr, orgnummer, automatiskBehandling, utbetalingId).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterUtbetalt(
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fagsystemId: String,
    utbetalingId: UUID? = null,
    meldingsreferanseId: UUID = UUID.randomUUID()
) {
    val faktiskUtbetalingId = utbetalingId?.toString() ?: person.personLogg.sisteBehov(Behovtype.Utbetaling).kontekst().getValue("utbetalingId")
    utbetaling(
        fagsystemId = fagsystemId,
        status = status,
        fnr = fnr,
        orgnummer = orgnummer,
        meldingsreferanseId = meldingsreferanseId,
        utbetalingId = UUID.fromString(faktiskUtbetalingId)
    ).håndter(Person::håndter)
}

private fun Oppdrag.fagsytemIdOrNull() = if (harUtbetalinger()) inspektør.fagsystemId() else null

private fun AbstractEndToEndTest.førsteUhåndterteUtbetalingsbehov(orgnummer: String): Pair<UUID, List<String>>? {
    val utbetalingsbehovUtbetalingIder = person.personLogg.behov()
        .filter { it.type == Behovtype.Utbetaling }
        .map { UUID.fromString(it.kontekst().getValue("utbetalingId")) }

    return inspektør(orgnummer).utbetalinger
        .filter { it.inspektør.tilstand in setOf(
            AVVENTER_KVITTERINGER,
            AVVENTER_ARBEIDSGIVERKVITTERING,
            AVVENTER_PERSONKVITTERING
        ) }
        .also { require(it.size < 2) { "For mange utbetalinger i spill! Er sendt ut godkjenningsbehov for periodene ${it.map { utbetaling -> utbetaling.inspektør.periode }}" } }
        .firstOrNull { it.inspektør.utbetalingId in utbetalingsbehovUtbetalingIder }
        ?.let {
            it.inspektør.utbetalingId to listOfNotNull(
                it.inspektør.arbeidsgiverOppdrag.fagsytemIdOrNull(),
                it.inspektør.personOppdrag.fagsytemIdOrNull()
            )
        }
}

internal fun AbstractEndToEndTest.håndterUtbetalt(
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    sendOverførtKvittering: Boolean = true,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    meldingsreferanseId: UUID = UUID.randomUUID()
) {
    førsteUhåndterteUtbetalingsbehov(orgnummer)?.also { (utbetalingId, fagsystemIder) ->
        fagsystemIder.forEach { fagsystemId ->
            håndterUtbetalt(status, fnr, orgnummer, fagsystemId, utbetalingId, meldingsreferanseId)
        }
    }
}

internal fun AbstractEndToEndTest.håndterAnnullerUtbetaling(
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fagsystemId: String = inspektør.arbeidsgiverOppdrag.last().fagsystemId(),
    opprettet: LocalDateTime = LocalDateTime.now()
) {
    AnnullerUtbetaling(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AbstractPersonTest.AKTØRID,
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        organisasjonsnummer = orgnummer,
        fagsystemId = fagsystemId,
        saksbehandlerIdent = "Ola Nordmann",
        saksbehandlerEpost = "tbd@nav.no",
        opprettet = opprettet
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterOverstyrInntekt(
    inntekt: Inntekt = 31000.månedlig,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    skjæringstidspunkt: LocalDate,
    meldingsreferanseId: UUID = UUID.randomUUID(),
    forklaring: String = "forklaring",
    subsumsjon: Subsumsjon? = null
) {
    håndterOverstyrArbeidsgiveropplysninger(
        skjæringstidspunkt,
        listOf(OverstyrtArbeidsgiveropplysning(orgnummer, inntekt, forklaring, subsumsjon, emptyList())),
        meldingsreferanseId
    )
}

internal fun AbstractEndToEndTest.håndterOverstyrArbeidsgiveropplysninger(
    skjæringstidspunkt: LocalDate,
    arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>,
    meldingsreferanseId: UUID = UUID.randomUUID()
) {
    OverstyrArbeidsgiveropplysninger(
        meldingsreferanseId = meldingsreferanseId,
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        aktørId = AbstractPersonTest.AKTØRID,
        skjæringstidspunkt = skjæringstidspunkt,
        arbeidsgiveropplysninger = arbeidsgiveropplysninger.tilOverstyrt(meldingsreferanseId, skjæringstidspunkt)
    ).håndter(Person::håndter)
}

internal class OverstyrtArbeidsgiveropplysning(
    private val orgnummer: String,
    private val inntekt: Inntekt,
    private val forklaring: String,
    private val subsumsjon: Subsumsjon?,
    private val refusjonsopplysninger: List<Triple<LocalDate, LocalDate?, Inntekt>>
) {
    internal companion object {
        internal fun List<OverstyrtArbeidsgiveropplysning>.tilOverstyrt(meldingsreferanseId: UUID, skjæringstidspunkt: LocalDate) =
            map {
                ArbeidsgiverInntektsopplysning(it.orgnummer, Saksbehandler(skjæringstidspunkt, meldingsreferanseId, it.inntekt, it.forklaring, it.subsumsjon, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                    it.refusjonsopplysninger.forEach { (fom, tom, refusjonsbeløp) -> leggTil(Refusjonsopplysning(meldingsreferanseId, fom, tom, refusjonsbeløp), LocalDateTime.now())}
                }.build())
            }
    }
}

internal fun AbstractEndToEndTest.håndterOverstyrTidslinje(
    overstyringsdager: List<ManuellOverskrivingDag> = listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag, 100)),
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    meldingsreferanseId: UUID = UUID.randomUUID()
): ArbeidstakerHendelse {
    return OverstyrTidslinje(
        meldingsreferanseId = meldingsreferanseId,
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        aktørId = AbstractPersonTest.AKTØRID,
        organisasjonsnummer = orgnummer,
        dager = overstyringsdager,
        opprettet = LocalDateTime.now()
    ).also { it.håndter(Person::håndter) }
}

internal fun AbstractEndToEndTest.håndterOverstyrArbeidsforhold(
    skjæringstidspunkt: LocalDate,
    overstyrteArbeidsforhold: List<OverstyrArbeidsforhold.ArbeidsforholdOverstyrt>
) {
    OverstyrArbeidsforhold(
        meldingsreferanseId = UUID.randomUUID(),
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        aktørId = AbstractPersonTest.AKTØRID,
        skjæringstidspunkt = skjæringstidspunkt,
        overstyrteArbeidsforhold = overstyrteArbeidsforhold
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterUtbetalingshistorikkForFeriepenger(
    opptjeningsår: Year,
    utbetalinger: List<UtbetalingshistorikkForFeriepenger.Utbetalingsperiode> = listOf(),
    feriepengehistorikk: List<UtbetalingshistorikkForFeriepenger.Feriepenger> = listOf(),
    skalBeregnesManuelt: Boolean = false
) {
    utbetalingshistorikkForFeriepenger(
        opptjeningsår = opptjeningsår,
        utbetalinger = utbetalinger,
        feriepengehistorikk = feriepengehistorikk,
        skalBeregnesManuelt = skalBeregnesManuelt
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterFeriepengerUtbetalt(
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    orgnummer: String = AbstractPersonTest.ORGNUMMER,
    fagsystemId: String,
    meldingsreferanseId: UUID = UUID.randomUUID()
): UtbetalingHendelse {
    return feriepengeutbetaling(
        fagsystemId = fagsystemId,
        status = status,
        orgnummer = orgnummer,
        meldingsreferanseId = meldingsreferanseId
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterOverstyringSykedag(periode: Periode) = håndterOverstyrTidslinje(periode.map { manuellSykedag(it) })

internal fun AbstractEndToEndTest.nyPeriode(periode: Periode, orgnummer: String = AbstractPersonTest.ORGNUMMER, grad: Prosentdel = 100.prosent, fnr: Personidentifikator = UNG_PERSON_FNR_2018) {
    håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), fnr = fnr, orgnummer = orgnummer)
    håndterSøknad(Søknadsperiode.Sykdom(periode.start, periode.endInclusive, grad), fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forkastAlle(hendelse: IAktivitetslogg) = person.søppelbøtte(hendelse) { true }