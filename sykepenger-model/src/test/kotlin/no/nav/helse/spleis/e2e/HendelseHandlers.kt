package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.PersonHendelsefabrikk
import no.nav.helse.dsl.lagStandardInntekterForOpptjeningsvurdering
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.etterspurteBehov
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.InntekterForOpptjeningsvurdering
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.MinimumSykdomsgradsvurderingMelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.inntektsmelding.Avsenderutleder
import no.nav.helse.hendelser.inntektsmelding.LPS
import no.nav.helse.hendelser.inntektsmelding.NAV_NO
import no.nav.helse.hendelser.inntektsmelding.erNavPortal
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.person.AbstractPersonTest
import no.nav.helse.person.AbstractPersonTest.Companion.ORGNUMMER
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.person.Arbeidsledig
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.Person
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsopplysning.*
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.sisteBehov
import no.nav.helse.spleis.e2e.AbstractEndToEndTest.Companion.INNTEKT
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning.Companion.refusjonstidslinjer
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning.Companion.tilOverstyrt
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning.Companion.tilSkjønnsmessigFastsatt
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertTrue

internal fun AbstractEndToEndTest.håndterSykmelding(
    periode: Periode,
    orgnummer: String = ORGNUMMER
) = håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), orgnummer = orgnummer)

internal fun AbstractEndToEndTest.håndterSykmelding(
    vararg sykeperioder: Sykmeldingsperiode,
    sykmeldingSkrevet: LocalDateTime? = null,
    mottatt: LocalDateTime? = null,
    id: UUID = UUID.randomUUID(),
    orgnummer: String = ORGNUMMER,
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

internal fun AbstractEndToEndTest.håndterAvbrytSøknad(
    periode: Periode,
    orgnummer: String,
    meldingsreferanseId: UUID = UUID.randomUUID()
) {
    AvbruttSøknad(
        periode,
        meldingsreferanseId,
        orgnummer
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterAvbrytArbeidsledigSøknad(
    periode: Periode,
    orgnummer: String,
    meldingsreferanseId: UUID = UUID.randomUUID()
) {
    AvbruttSøknad(
        periode,
        meldingsreferanseId,
        Arbeidsledig
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.tilGodkjenning(
    periode: Periode,
    vararg organisasjonsnummere: String,
    beregnetInntekt: Inntekt = 20000.månedlig,
    arbeidsgiverperiode: List<Periode> = listOf(Periode(periode.start, periode.start.plusDays(15))),
    inntektsmeldingId: UUID = UUID.randomUUID(),
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode
) {
    require(organisasjonsnummere.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnummere.forEach { nyPeriode(periode, it) }
    organisasjonsnummere.forEach {
        håndterInntektsmelding(
            arbeidsgiverperioder = arbeidsgiverperiode,
            beregnetInntekt = beregnetInntekt,
            orgnummer = it,
            id = inntektsmeldingId,
            vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter
        )
    }

    organisasjonsnummere.first().let { organisasjonsnummer ->
        val vedtaksperiode = observatør.sisteVedtaksperiode()
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = vedtaksperiode,
            inntekt = beregnetInntekt,
            orgnummer = organisasjonsnummer
        )
        håndterYtelser(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterSimulering(vedtaksperiode, orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.nyeVedtak(
    periode: Periode,
    vararg organisasjonsnummere: String,
    inntekt: Inntekt = 20000.månedlig
) {
    val vedtaksperiode = observatør.sisteVedtaksperiode()
    require(organisasjonsnummere.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    tilGodkjenning(periode, *organisasjonsnummere, beregnetInntekt = inntekt)
    val (første, resten) = organisasjonsnummere.first() to organisasjonsnummere.drop(1)

    første.let { organisasjonsnummer ->
        håndterUtbetalingsgodkjenning(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterUtbetalt(orgnummer = organisasjonsnummer)
    }

    resten.forEach { organisasjonsnummer ->
        håndterYtelser(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterSimulering(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterUtbetalingsgodkjenning(vedtaksperiode, orgnummer = organisasjonsnummer)
        håndterUtbetalt(orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.førstegangTilGodkjenning(
    periode: Periode,
    vararg arbeidsgivere: Pair<String, IdInnhenter?>,
) {
    require(arbeidsgivere.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    arbeidsgivere.forEach {
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), orgnummer = it.first)
    }
    arbeidsgivere.forEach {
        håndterSøknad(Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = it.first)

    }
    arbeidsgivere.forEach {
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(periode.start, periode.start.plusDays(15))),
            førsteFraværsdag = if (it.second == null) periode.start else null,
            beregnetInntekt = 20000.månedlig,
            orgnummer = it.first,
            vedtaksperiodeIdInnhenter = it.second
        )
    }

    val vedtaksperiode = observatør.sisteVedtaksperiode()

    arbeidsgivere.first().let { arbeidsgiver ->
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = vedtaksperiode,
            orgnummer = arbeidsgiver.first
        )
        håndterYtelser(vedtaksperiode, orgnummer = arbeidsgiver.first)
        håndterSimulering(vedtaksperiode, orgnummer = arbeidsgiver.first)
    }
}

internal fun AbstractEndToEndTest.forlengelseTilGodkjenning(periode: Periode, vararg organisasjonsnumre: String) {
    require(organisasjonsnumre.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnumre.forEach { nyPeriode(periode, it) }
    håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnumre.first())
    håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnumre.first())
}

internal fun AbstractEndToEndTest.forlengVedtak(periode: Periode, vararg organisasjonsnumre: String) {
    require(organisasjonsnumre.isNotEmpty()) { "Må inneholde minst ett organisasjonsnummer" }
    organisasjonsnumre.forEach { håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), orgnummer = it) }
    organisasjonsnumre.forEach { håndterSøknad(Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = it) }
    organisasjonsnumre.forEach { organisasjonsnummer ->
        håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnummer)
        håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnummer)
        håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), orgnummer = organisasjonsnummer)
        håndterUtbetalt(orgnummer = organisasjonsnummer)
    }
}

internal fun AbstractEndToEndTest.nyttVedtak(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode>? = null,
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    inntektsmeldingId: UUID = UUID.randomUUID(),
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode
) {
    tilGodkjent(periode, grad, fnr = fnr, orgnummer = orgnummer, beregnetInntekt = beregnetInntekt, refusjon = refusjon, arbeidsgiverperiode = arbeidsgiverperiode, inntektsmeldingId = inntektsmeldingId, vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter)
    håndterUtbetalt(status = status, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.tilGodkjent(
    periode: Periode,
    grad: Prosentdel,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode>? = null,
    inntektsmeldingId: UUID = UUID.randomUUID(),
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode
): IdInnhenter {
    val id = tilGodkjenning(
        periode = periode,
        grad = grad,
        fnr = fnr,
        orgnummer = orgnummer,
        refusjon = refusjon,
        arbeidsgiverperiode = arbeidsgiverperiode,
        beregnetInntekt = beregnetInntekt,
        inntektsmeldingId = inntektsmeldingId,
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter
    )
    håndterUtbetalingsgodkjenning(id, true, fnr = fnr, orgnummer = orgnummer)
    return id
}

internal fun AbstractEndToEndTest.tilGodkjenning(
    periode: Periode,
    grad: Prosentdel,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode>? = null,
    inntektsmeldingId: UUID = UUID.randomUUID(),
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode
): IdInnhenter {
    val id = tilSimulering(periode, grad, fnr = fnr, orgnummer = orgnummer, beregnetInntekt = beregnetInntekt, refusjon = refusjon, arbeidsgiverperiode = arbeidsgiverperiode, inntektsmeldingId = inntektsmeldingId, vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter)
    håndterSimulering(id, fnr = fnr, orgnummer = orgnummer)
    return id
}

internal fun AbstractEndToEndTest.tilSimulering(
    periode: Periode,
    grad: Prosentdel,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    arbeidsgiverperiode: List<Periode>? = null,
    inntektsmeldingId: UUID = UUID.randomUUID(),
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode
): IdInnhenter {
    return tilYtelser(periode, grad, fnr = fnr, orgnummer = orgnummer, beregnetInntekt = beregnetInntekt, refusjon = refusjon, arbeidsgiverperiode = arbeidsgiverperiode, inntektsmeldingId = inntektsmeldingId, vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter)
}

internal fun AbstractEndToEndTest.tilYtelser(
    periode: Periode,
    grad: Prosentdel,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    inntektsvurderingForSykepengegrunnlag: InntektForSykepengegrunnlag = InntektForSykepengegrunnlag(
        inntekter = inntektperioderForSykepengegrunnlag {
            periode.start.minusMonths(3) til periode.start.minusMonths(1) inntekter {
                orgnummer inntekt beregnetInntekt
            }
        }
    ),
    arbeidsgiverperiode: List<Periode>? = null,
    inntektsmeldingId: UUID = UUID.randomUUID(),
    vedtaksperiodeIdInnhenter: IdInnhenter
): IdInnhenter {
    håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), fnr = fnr, orgnummer = orgnummer)
    håndterSøknad(Søknadsperiode.Sykdom(periode.start, periode.endInclusive, grad), fnr = fnr, orgnummer = orgnummer)
    håndterInntektsmelding(
        arbeidsgiverperiode ?: listOf(Periode(periode.start, periode.start.plusDays(15))),
        førsteFraværsdag = null,
        beregnetInntekt = beregnetInntekt,
        refusjon = refusjon,
        orgnummer = orgnummer,
        id = inntektsmeldingId,
        fnr = fnr,
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter
    )
    val id = observatør.sisteVedtaksperiode()
    håndterVilkårsgrunnlag(
        vedtaksperiodeIdInnhenter = id,
        inntekt = beregnetInntekt,
        fnr = fnr,
        orgnummer = orgnummer,
        inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag
    )
    håndterYtelser(id, orgnummer = orgnummer, fnr = fnr)
    return id
}

internal fun AbstractEndToEndTest.forlengTilGodkjentVedtak(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER
) {
    forlengTilGodkjenning(periode, grad, fnr, orgnummer)
    håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), true, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forlengTilSimulering(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER
) {
    nyPeriode(periode, orgnummer, grad = grad, fnr = fnr)
    val id: IdInnhenter = observatør.sisteVedtaksperiode()
    håndterYtelser(id, orgnummer = orgnummer, fnr = fnr)
    assertTrue(person.personLogg.etterspurteBehov(id, Behovtype.Simulering, orgnummer)) { "Forventet at simulering er etterspurt" }
}

internal fun AbstractEndToEndTest.forlengTilGodkjenning(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER
) {
    nyPeriode(periode, orgnummer, grad = grad, fnr = fnr)
    val id: IdInnhenter = observatør.sisteVedtaksperiode()
    håndterYtelser(id, orgnummer = orgnummer, fnr = fnr)
    if (person.personLogg.etterspurteBehov(id, Behovtype.Simulering, orgnummer)) håndterSimulering(id, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forlengVedtak(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER
) {
    forlengTilGodkjentVedtak(periode, grad, fnr, orgnummer)
    håndterUtbetalt(status = Oppdragstatus.AKSEPTERT, fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forlengPeriode(
    periode: Periode,
    grad: Prosentdel = 100.prosent,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
) {
    nyPeriode(periode, orgnummer, grad = grad, fnr = fnr)
}

internal fun AbstractEndToEndTest.håndterSøknad(
    periode: Periode,
    orgnummer: String = ORGNUMMER,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    sendTilGosys: Boolean = false,
    sendtTilNAVEllerArbeidsgiver: LocalDate = periode.endInclusive
): UUID {
    return håndterSøknad(
        Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent),
        orgnummer = orgnummer,
        fnr = fnr,
        sykmeldingSkrevet = periode.start.atStartOfDay(),
        sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver,
        sendTilGosys = sendTilGosys
    )
}

internal fun AbstractEndToEndTest.håndterSøknad(
    vararg perioder: Søknadsperiode,
    andreInntektskilder: Boolean = false,
    sendtTilNAVEllerArbeidsgiver: LocalDate = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive,
    id: UUID = UUID.randomUUID(),
    orgnummer: String = ORGNUMMER,
    sykmeldingSkrevet: LocalDateTime? = null,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    korrigerer: UUID? = null,
    utenlandskSykmelding: Boolean = false,
    sendTilGosys: Boolean = false,
    opprinneligSendt: LocalDate? = null,
    merknaderFraSykmelding: List<Søknad.Merknad> = emptyList(),
    permittert: Boolean = false,
    egenmeldinger: List<Periode> = emptyList()
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
            permittert = permittert,
            egenmeldinger = egenmeldinger
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
    observatør.replayInntektsmeldinger { block() }.forEach { forespørsel ->
        val imReplays = inntektsmeldinger
            .entries
            .sortedBy { it.value.tidspunkt }
            .filter { forespørsel.erInntektsmeldingRelevant(it.value.inntektsmeldingkontrakt) }
            .map { it.value.generator() }
            .filter { im -> im.metadata.meldingsreferanseId !in observatør.inntektsmeldingHåndtert.map(Pair<*, *>::first) }
            .filter { im -> im.behandlingsporing.organisasjonsnummer == orgnummer }
        InntektsmeldingerReplay(
            meldingsreferanseId = UUID.randomUUID(),
            organisasjonsnummer = orgnummer,
            vedtaksperiodeId = forespørsel.vedtaksperiodeId,
            inntektsmeldinger = imReplays
        ).håndter(Person::håndter)
        observatør.kvitterInntektsmeldingReplay(forespørsel.vedtaksperiodeId)
    }
}

internal fun AbstractEndToEndTest.håndterInntektsmelding(
    arbeidsgiverperioder: List<Periode>,
    førsteFraværsdag: LocalDate? = null,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    orgnummer: String = ORGNUMMER,
    id: UUID = UUID.randomUUID(),
    harOpphørAvNaturalytelser: Boolean = false,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
    harFlereInntektsmeldinger: Boolean = false,
    avsendersystem: Avsenderutleder? = null,
    vedtaksperiodeIdInnhenter: IdInnhenter? = null,
    førReplay: () -> Unit = {}
): UUID {
    val utledetAvsendersystem = when {
        avsendersystem != null -> avsendersystem
        vedtaksperiodeIdInnhenter != null -> NAV_NO
        else -> LPS
    }

    if (erNavPortal(utledetAvsendersystem)) {
        check(førsteFraværsdag == null) {
            """
            Du har satt første fraværsdag $førsteFraværsdag på en portalinntektsmelding!
            Denne brukes ikke til noe i portalinntektsmeldinger, så du må sette avsendersystem
            til LPS/ALTINN om det er en viktig detalj i testen din at første fraværsdag er satt.
            """
        }

        return håndterInntektsmelding(
            portalInntektsmelding(
                id,
                arbeidsgiverperioder,
                beregnetInntekt = beregnetInntekt,
                vedtaksperiodeId = inspektør(orgnummer).vedtaksperiodeId(checkNotNull(vedtaksperiodeIdInnhenter) { "Du må sette vedtaksperiodeId for portalinntektsmelding!" }),
                refusjon = refusjon,
                orgnummer = orgnummer,
                harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
                begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
                harFlereInntektsmeldinger = harFlereInntektsmeldinger,
                avsendersystem = utledetAvsendersystem
            )
        )
    }
    check(vedtaksperiodeIdInnhenter == null) { "Du kan ikke sette vedtaksperiodeId for LPS/ALTINN. De vet ikke hva det er!" }
    return håndterInntektsmelding(
        klassiskInntektsmelding(
            id,
            arbeidsgiverperioder,
            beregnetInntekt = beregnetInntekt,
            førsteFraværsdag = førsteFraværsdag,
            refusjon = refusjon,
            orgnummer = orgnummer,
            harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
            fnr = fnr,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger,
            avsendersystem = utledetAvsendersystem
        ), førReplay
    )
}

internal fun AbstractEndToEndTest.håndterInntektsmeldingPortal(
    arbeidsgiverperioder: List<Periode>,
    beregnetInntekt: Inntekt = INNTEKT,
    refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
    orgnummer: String = ORGNUMMER,
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    id: UUID = UUID.randomUUID(),
    harOpphørAvNaturalytelser: Boolean = false,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
    harFlereInntektsmeldinger: Boolean = false,
    avsendersystem: Avsenderutleder = NAV_NO
): UUID {
    val portalinntektsmelding = portalInntektsmelding(
        id,
        arbeidsgiverperioder,
        beregnetInntekt = beregnetInntekt,
        vedtaksperiodeId = inspektør(orgnummer).vedtaksperiodeId(vedtaksperiodeIdInnhenter),
        refusjon = refusjon,
        orgnummer = orgnummer,
        harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
        begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
        harFlereInntektsmeldinger = harFlereInntektsmeldinger,
        avsendersystem = avsendersystem,
    )
    return håndterInntektsmelding(portalinntektsmelding)
}

internal fun AbstractEndToEndTest.håndterInntektsmelding(inntektsmelding: Inntektsmelding, førReplay: () -> Unit = {}): UUID {
    håndterOgReplayInntektsmeldinger(inntektsmelding.behandlingsporing.organisasjonsnummer) {
        inntektsmelding.håndter(Person::håndter)
        førReplay()
    }
    return inntektsmelding.metadata.meldingsreferanseId
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
    orgnummer: String = ORGNUMMER,
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
        )
    ),
    inntekterForOpptjeningsvurdering: InntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering(ORGNUMMER, INNTEKT, inspektør(orgnummer).skjæringstidspunkt(vedtaksperiodeIdInnhenter)),
    arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold> = finnArbeidsgivere().map { Vilkårsgrunnlag.Arbeidsforhold(it, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT) },
    fnr: Personidentifikator = UNG_PERSON_FNR_2018
): Vilkårsgrunnlag {
    fun assertEtterspurt(behovtype: Behovtype) =
        assertEtterspurt(Vilkårsgrunnlag::class, behovtype, vedtaksperiodeIdInnhenter, orgnummer)

    assertEtterspurt(Behovtype.InntekterForSykepengegrunnlag)
    assertEtterspurt(Behovtype.InntekterForOpptjeningsvurdering)
    assertEtterspurt(Behovtype.ArbeidsforholdV2)
    assertEtterspurt(Behovtype.Medlemskap)
    return vilkårsgrunnlag(
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter,
        skjæringstidspunkt = finnSkjæringstidspunkt(orgnummer, vedtaksperiodeIdInnhenter),
        medlemskapstatus = medlemskapstatus,
        orgnummer = orgnummer,
        arbeidsforhold = arbeidsforhold,
        inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag,
        inntekterForOpptjeningsvurdering = inntekterForOpptjeningsvurdering,
        fnr = fnr
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterSimulering(
    vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode,
    simuleringOK: Boolean = true,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    simuleringsresultat: SimuleringResultatDto? = standardSimuleringsresultat(orgnummer)
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
    orgnummer: String = ORGNUMMER,
    simuleringsresultat: SimuleringResultatDto? = standardSimuleringsresultat(orgnummer)
) {
    assertEtterspurt(Simulering::class, Behovtype.Simulering, vedtaksperiodeIdInnhenter, orgnummer)
    Simulering(
        meldingsreferanseId = UUID.randomUUID(),
        vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
        orgnummer = orgnummer,
        fagsystemId = fagsystemId,
        fagområde = fagområde.toString(),
        simuleringOK = simuleringOK,
        melding = "",
        utbetalingId = utbetalingId,
        simuleringsResultat = simuleringsresultat
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterInfotrygdendring() {
    Infotrygdendring(UUID.randomUUID())
        .håndter(Person::håndter)
}

private fun AbstractEndToEndTest.håndterUtbetalingshistorikk(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    vararg utbetalinger: Infotrygdperiode,
    inntektshistorikk: List<Inntektsopplysning> = emptyList(),
    orgnummer: String = ORGNUMMER,
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
    meldingsreferanseId: UUID = UUID.randomUUID()
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
    foreldrepenger: List<GradertPeriode> = emptyList(),
    svangerskapspenger: List<GradertPeriode> = emptyList(),
    pleiepenger: List<GradertPeriode> = emptyList(),
    omsorgspenger: List<GradertPeriode> = emptyList(),
    opplæringspenger: List<GradertPeriode> = emptyList(),
    institusjonsoppholdsperioder: List<Institusjonsopphold.Institusjonsoppholdsperiode> = emptyList(),
    orgnummer: String = ORGNUMMER,
    arbeidsavklaringspenger: List<Periode> = emptyList(),
    dagpenger: List<Periode> = emptyList(),
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

    ytelser(
        vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter,
        foreldrepenger = foreldrepenger,
        svangerskapspenger = svangerskapspenger,
        pleiepenger = pleiepenger,
        omsorgspenger = omsorgspenger,
        opplæringspenger = opplæringspenger,
        institusjonsoppholdsperioder = institusjonsoppholdsperioder,
        orgnummer = orgnummer,
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

internal fun AbstractEndToEndTest.håndterPersonPåminnelse() = PersonHendelsefabrikk().lagPåminnelse().håndter(Person::håndter)

internal fun AbstractEndToEndTest.håndterSykepengegrunnlagForArbeidsgiver(
    vedtaksperiodeId: IdInnhenter,
    skjæringstidspunkt: LocalDate = 1.januar,
    orgnummer: String = ORGNUMMER
): UUID {
    val inntektFraAOrdningen: SykepengegrunnlagForArbeidsgiver = sykepengegrunnlagForArbeidsgiver(vedtaksperiodeId.id(orgnummer), skjæringstidspunkt, orgnummer)
    inntektFraAOrdningen.håndter(Person::håndter)
    return inntektFraAOrdningen.metadata.meldingsreferanseId
}

internal fun AbstractEndToEndTest.håndterPåminnelse(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    påminnetTilstand: TilstandType,
    tilstandsendringstidspunkt: LocalDateTime = LocalDateTime.now(),
    orgnummer: String = ORGNUMMER,
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
    orgnummer: String = ORGNUMMER,
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
    orgnummer: String = ORGNUMMER,
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
    val utbetalingsbehovUtbetalingIder = person.personLogg.behov
        .filter { it.type == Behovtype.Utbetaling }
        .map { UUID.fromString(it.kontekst().getValue("utbetalingId")) }

    return inspektør(orgnummer).utbetalingerInFlight()
        .also { require(it.size < 2) { "For mange utbetalinger i spill! Er sendt ut godkjenningsbehov for periodene ${it.map { utbetaling -> utbetaling.periode }}" } }
        .firstOrNull { it.utbetalingId in utbetalingsbehovUtbetalingIder }
        ?.let {
            it.utbetalingId to listOfNotNull(
                it.arbeidsgiverOppdrag.fagsytemIdOrNull(),
                it.personOppdrag.fagsytemIdOrNull()
            )
        }
}

internal fun AbstractEndToEndTest.håndterUtbetalt(
    status: Oppdragstatus = Oppdragstatus.AKSEPTERT,
    sendOverførtKvittering: Boolean = true,
    fnr: Personidentifikator = UNG_PERSON_FNR_2018,
    orgnummer: String = ORGNUMMER,
    meldingsreferanseId: UUID = UUID.randomUUID()
) {
    førsteUhåndterteUtbetalingsbehov(orgnummer)?.also { (utbetalingId, fagsystemIder) ->
        fagsystemIder.forEach { fagsystemId ->
            håndterUtbetalt(status, fnr, orgnummer, fagsystemId, utbetalingId, meldingsreferanseId)
        }
    }
}

internal fun AbstractEndToEndTest.håndterAnnullerUtbetaling(
    orgnummer: String = ORGNUMMER,
    utbetalingId: UUID = inspektør.sisteUtbetaling().utbetalingId,
    opprettet: LocalDateTime = LocalDateTime.now()
) {
    AnnullerUtbetaling(
        meldingsreferanseId = UUID.randomUUID(),
        organisasjonsnummer = orgnummer,
        utbetalingId = utbetalingId,
        saksbehandlerIdent = "Ola Nordmann",
        saksbehandlerEpost = "tbd@nav.no",
        opprettet = opprettet
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterOverstyrInntekt(
    inntekt: Inntekt = 31000.månedlig,
    orgnummer: String = ORGNUMMER,
    skjæringstidspunkt: LocalDate,
    gjelder: Periode = skjæringstidspunkt til LocalDate.MAX,
    meldingsreferanseId: UUID = UUID.randomUUID(),
    forklaring: String = "forklaring",
    subsumsjon: Subsumsjon? = null
) {
    håndterOverstyrArbeidsgiveropplysninger(
        skjæringstidspunkt,
        listOf(OverstyrtArbeidsgiveropplysning(orgnummer, inntekt, forklaring, subsumsjon, emptyList(), gjelder)),
        meldingsreferanseId
    )
}

internal fun AbstractEndToEndTest.håndterMinimumSykdomsgradVurdert(
    perioderMedMinimumSykdomsgradVurdertOK: List<Periode>,
    perioderMedMinimumSykdomsgradVurdertIkkeOK: List<Periode> = emptyList()
) {
    MinimumSykdomsgradsvurderingMelding(
        perioderMedMinimumSykdomsgradVurdertOK.toSet(),
        perioderMedMinimumSykdomsgradVurdertIkkeOK.toSet(),
        UUID.randomUUID()
    ).håndter(Person::håndter)
}

internal fun AbstractEndToEndTest.håndterOverstyrArbeidsgiveropplysninger(
    skjæringstidspunkt: LocalDate,
    arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>,
    meldingsreferanseId: UUID = UUID.randomUUID()
): UUID {
    val opprettet = LocalDateTime.now()
    OverstyrArbeidsgiveropplysninger(
        meldingsreferanseId = meldingsreferanseId,
        skjæringstidspunkt = skjæringstidspunkt,
        arbeidsgiveropplysninger = arbeidsgiveropplysninger.tilOverstyrt(meldingsreferanseId, skjæringstidspunkt),
        refusjonstidslinjer = arbeidsgiveropplysninger.refusjonstidslinjer(meldingsreferanseId, opprettet),
        opprettet = opprettet
    ).håndter(Person::håndter)
    return meldingsreferanseId
}

internal fun AbstractEndToEndTest.håndterSkjønnsmessigFastsettelse(
    skjæringstidspunkt: LocalDate,
    arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>,
    meldingsreferanseId: UUID = UUID.randomUUID()
): UUID {
    SkjønnsmessigFastsettelse(
        meldingsreferanseId = meldingsreferanseId,
        skjæringstidspunkt = skjæringstidspunkt,
        arbeidsgiveropplysninger = arbeidsgiveropplysninger.tilSkjønnsmessigFastsatt(meldingsreferanseId, skjæringstidspunkt),
        opprettet = LocalDateTime.now()
    ).håndter(Person::håndter)
    return meldingsreferanseId
}

internal class OverstyrtArbeidsgiveropplysning(
    private val orgnummer: String,
    private val inntekt: Inntekt,
    private val forklaring: String,
    private val subsumsjon: Subsumsjon?,
    private val refusjonsopplysninger: List<Triple<LocalDate, LocalDate?, Inntekt>>,
    private val gjelder: Periode? = null
) {
    internal constructor(orgnummer: String, inntekt: Inntekt) : this(orgnummer, inntekt, "forklaring", null, emptyList(), null)
    internal constructor(orgnummer: String, inntekt: Inntekt, subsumsjon: Subsumsjon) : this(orgnummer, inntekt, "forklaring", subsumsjon, emptyList(), null)
    internal constructor(orgnummer: String, inntekt: Inntekt, gjelder: Periode) : this(orgnummer, inntekt, "forklaring", null, emptyList(), gjelder)

    internal companion object {
        internal fun List<OverstyrtArbeidsgiveropplysning>.tilOverstyrt(meldingsreferanseId: UUID, skjæringstidspunkt: LocalDate) =
            map {
                val gjelder = it.gjelder ?: (skjæringstidspunkt til LocalDate.MAX)
                ArbeidsgiverInntektsopplysning(it.orgnummer, gjelder, Saksbehandler(skjæringstidspunkt, meldingsreferanseId, it.inntekt, it.forklaring, it.subsumsjon, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                    it.refusjonsopplysninger.forEach { (fom, tom, refusjonsbeløp) -> leggTil(Refusjonsopplysning(meldingsreferanseId, fom, tom, refusjonsbeløp, SAKSBEHANDLER, LocalDateTime.now()), LocalDateTime.now()) }
                }.build())
            }

        internal fun List<OverstyrtArbeidsgiveropplysning>.tilSkjønnsmessigFastsatt(meldingsreferanseId: UUID, skjæringstidspunkt: LocalDate) =
            map {
                val gjelder = it.gjelder ?: (skjæringstidspunkt til LocalDate.MAX)
                ArbeidsgiverInntektsopplysning(it.orgnummer, gjelder, SkjønnsmessigFastsatt(skjæringstidspunkt, meldingsreferanseId, it.inntekt, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
                    it.refusjonsopplysninger.forEach { (fom, tom, refusjonsbeløp) -> leggTil(Refusjonsopplysning(meldingsreferanseId, fom, tom, refusjonsbeløp, SAKSBEHANDLER, LocalDateTime.now()), LocalDateTime.now()) }
                }.build())
            }

        internal fun List<OverstyrtArbeidsgiveropplysning>.refusjonstidslinjer(meldingsreferanseId: UUID, opprettet: LocalDateTime) = this.associateBy { it.orgnummer }.mapValues { (_, opplysning) ->
            val strekkbar = opplysning.refusjonsopplysninger.any { (_, tom) -> tom == null }
            opplysning.refusjonsopplysninger.fold(Beløpstidslinje()) { acc, (fom, tom, beløp) ->
                acc + Beløpstidslinje.fra(fom til (tom ?: fom), beløp, Kilde(meldingsreferanseId, SAKSBEHANDLER, opprettet))
            } to strekkbar
        }
    }
}

internal fun AbstractEndToEndTest.håndterOverstyrTidslinje(
    overstyringsdager: List<ManuellOverskrivingDag> = listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag, 100)),
    orgnummer: String = ORGNUMMER,
    meldingsreferanseId: UUID = UUID.randomUUID()
): OverstyrTidslinje {
    val hendelse = OverstyrTidslinje(
        meldingsreferanseId = meldingsreferanseId,
        organisasjonsnummer = orgnummer,
        dager = overstyringsdager,
        opprettet = LocalDateTime.now()
    )
    håndterOgReplayInntektsmeldinger(orgnummer) {
        hendelse.also { it.håndter(Person::håndter) }
    }
    return hendelse
}

internal fun AbstractEndToEndTest.håndterOverstyrArbeidsforhold(
    skjæringstidspunkt: LocalDate,
    overstyrteArbeidsforhold: List<OverstyrArbeidsforhold.ArbeidsforholdOverstyrt>
) {
    OverstyrArbeidsforhold(
        meldingsreferanseId = UUID.randomUUID(),
        skjæringstidspunkt = skjæringstidspunkt,
        overstyrteArbeidsforhold = overstyrteArbeidsforhold,
        opprettet = LocalDateTime.now()
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
    orgnummer: String = ORGNUMMER,
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

internal fun AbstractEndToEndTest.nyPeriode(periode: Periode, orgnummer: String = ORGNUMMER, grad: Prosentdel = 100.prosent, fnr: Personidentifikator = UNG_PERSON_FNR_2018) {
    håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), fnr = fnr, orgnummer = orgnummer)
    håndterSøknad(Søknadsperiode.Sykdom(periode.start, periode.endInclusive, grad), fnr = fnr, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.forkastAlle() = person.søppelbøtte(forrigeHendelse, Aktivitetslogg()) { true }
