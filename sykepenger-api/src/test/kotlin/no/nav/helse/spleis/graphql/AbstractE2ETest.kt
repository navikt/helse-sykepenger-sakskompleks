package no.nav.helse.spleis.graphql

import java.time.LocalDate
import java.time.LocalDate.EPOCH
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.februar
import no.nav.helse.gjenopprettFraJSON
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.InntekterForOpptjeningsvurdering
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.spleis.IdInnhenter
import no.nav.helse.spleis.speil.serializePersonForSpeil
import no.nav.helse.spleis.testhelpers.ArbeidsgiverHendelsefabrikk
import no.nav.helse.spleis.testhelpers.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.testhelpers.PersonHendelsefabrikk
import no.nav.helse.spleis.testhelpers.TestObservatør
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.BeforeEach

internal abstract class AbstractE2ETest {
    protected companion object {
        private const val UNG_PERSON_FNR = "12029240045"
        private const val AKTØRID = "42"
        private val UNG_PERSON_FØDSELSDATO = 12.februar(1992)
        const val a1 = "a1"
        const val a2 = "a2"
        const val a3 = "a3"
        val INNTEKT = 48000.månedlig

        private val personfabrikk = PersonHendelsefabrikk(AKTØRID, Personidentifikator(UNG_PERSON_FNR))
        private val a1fabrikk = ArbeidsgiverHendelsefabrikk(AKTØRID, a1)
        private val a2fabrikk = ArbeidsgiverHendelsefabrikk(AKTØRID, a2)
        private val a3fabrikk = ArbeidsgiverHendelsefabrikk(AKTØRID, a3)
        private val fabrikker = mapOf(
            a1 to a1fabrikk,
            a2 to a2fabrikk,
            a3 to a3fabrikk
        )
    }

    private lateinit var person: Person
    private lateinit var observatør: TestObservatør
    private lateinit var spekemat: Spekemat
    private lateinit var hendelselogg: Aktivitetslogg
    private val ubesvarteBehov = ConcurrentLinkedDeque<Aktivitet.Behov>()

    private fun createTestPerson(creator: (Subsumsjonslogg) -> Person) {
        observatør = TestObservatør()
        spekemat = Spekemat()
        person = creator(EmptyLog)
        person.addObserver(observatør)
        person.addObserver(spekemat)
    }
    protected fun createOvergangFraInfotrygdPerson() = createTestPerson { jurist ->
        gjenopprettFraJSON("/personer/infotrygdforlengelse.json", jurist)
    }

    @BeforeEach
    fun setup() {
        createTestPerson {
            Person(AKTØRID, Personidentifikator(UNG_PERSON_FNR), Alder(UNG_PERSON_FØDSELSDATO, null), it)
        }
        hendelselogg = Aktivitetslogg()
    }

    protected val Int.vedtaksperiode: IdInnhenter get() = IdInnhenter { orgnummer -> this.vedtaksperiode(orgnummer) }
    protected fun Int.vedtaksperiode(orgnummer: String) = observatør.vedtaksperiode(orgnummer, this - 1)

    protected val UUID.vedtaksperiode get() = IdInnhenter { _ -> this }

    protected fun dto() = person.dto()
    protected fun speilApi() = serializePersonForSpeil(person, spekemat.resultat())

    protected fun <T : Hendelse> T.håndter(håndter: Person.(T, IAktivitetslogg) -> Unit) = apply {
        hendelselogg = Aktivitetslogg()
        person.håndter(this, hendelselogg)
        ubesvarteBehov.addAll(hendelselogg.behov)

        observatør.ventendeReplays().forEach { (orgnr, vedtaksperiodeId) ->
            hendelselogg = Aktivitetslogg()
            person.håndter(fabrikker.getValue(orgnr).lagInntektsmeldingReplayUtført(vedtaksperiodeId), hendelselogg)
        }
    }

    protected fun håndterSøknad(periode: Periode, orgnummer: String = a1): UUID {
        return håndterSøknad(Søknad.Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent), sykmeldingSkrevet = periode.start.atStartOfDay(), sendtTilNAV = periode.endInclusive.atStartOfDay(), orgnummer = orgnummer)
    }
    protected fun håndterSøknad(
        vararg perioder: Søknad.Søknadsperiode,
        sykmeldingSkrevet: LocalDateTime = 1.januar.atStartOfDay(),
        sendtTilNAV: LocalDateTime = 1.januar.atStartOfDay(),
        orgnummer: String = a1,
        tilkomneInntekter: List<Søknad.TilkommenInntekt> = emptyList()
    ): UUID {
        val søknadId = UUID.randomUUID()
        val søknad = fabrikker.getValue(orgnummer).lagSøknad(
            *perioder,
            sykmeldingSkrevet = sykmeldingSkrevet,
            sendtTilNAVEllerArbeidsgiver = sendtTilNAV,
            id = søknadId,
            tilkomneInntekter = tilkomneInntekter
        )
        søknad.håndter(Person::håndter)

        val behov = hendelselogg.infotrygdhistorikkbehov()
        if (behov != null) håndterUtbetalingshistorikk(behov.vedtaksperiodeId, orgnummer = behov.orgnummer)

        return søknadId
    }

    protected fun håndterUtbetalingshistorikk(vedtaksperiodeId: UUID, orgnummer: String) {
        (fabrikker.getValue(orgnummer).lagUtbetalingshistorikk(
            vedtaksperiodeId = vedtaksperiodeId
        )).håndter(Person::håndter)
    }

    protected fun håndterInntektsmelding(
        fom: LocalDate,
        orgnummer: String = a1,
        vedtaksperiode: Int = 1,
        beregnetInntekt: Inntekt = INNTEKT,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
        meldingsreferanseId: UUID = UUID.randomUUID()
    ): UUID {
        return håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            orgnummer = orgnummer,
            vedtaksperiode = vedtaksperiode,
            beregnetInntekt = beregnetInntekt,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null),
            meldingsreferanseId = meldingsreferanseId
        )
    }

    protected fun håndterInntektsmeldingUtenRefusjon(
        fom: LocalDate,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
        meldingsreferanseId: UUID = UUID.randomUUID(),
        orgnummer: String = a1,
        vedtaksperiode: Int,

    ): UUID {
        return håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            orgnummer = orgnummer,
            beregnetInntekt = INNTEKT,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            refusjon = Inntektsmelding.Refusjon(INGEN, null),
            meldingsreferanseId = meldingsreferanseId,
            vedtaksperiode = vedtaksperiode
        )
    }

    protected fun håndterInntektsmeldingUtenRefusjon(
        arbeidsgiverperioder: List<Periode>,
        vedtaksperiode: Int,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
        meldingsreferanseId: UUID = UUID.randomUUID(),
        orgnummer: String = a1
    ): UUID {
        return håndterInntektsmelding(
            arbeidsgiverperioder = arbeidsgiverperioder,
            orgnummer = orgnummer,
            beregnetInntekt = INNTEKT,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            refusjon = Inntektsmelding.Refusjon(INGEN, null),
            meldingsreferanseId = meldingsreferanseId,
            vedtaksperiode = vedtaksperiode
        )
    }

    protected fun håndterInntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        orgnummer: String = a1,
        vedtaksperiode: Int = 1,
        beregnetInntekt: Inntekt = INNTEKT,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null,
        refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null),
        meldingsreferanseId: UUID = UUID.randomUUID(),
    ): UUID {
        fabrikker.getValue(orgnummer).lagPortalinntektsmelding(
            arbeidsgiverperioder = arbeidsgiverperioder,
            beregnetInntekt = beregnetInntekt,
            vedtaksperiodeId = vedtaksperiode.vedtaksperiode(orgnummer),
            refusjon = refusjon,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            id = meldingsreferanseId,
        ).håndter(Person::håndter)
        return meldingsreferanseId
    }

    protected fun håndterVilkårsgrunnlag(arbeidsgivere: List<Pair<String, Inntekt>> = listOf(a1 to INNTEKT)) {
        håndterVilkårsgrunnlag(inntekter = arbeidsgivere, arbeidsforhold = arbeidsgivere.map { (orgnr, _) -> orgnr to EPOCH })
    }
    protected fun håndterVilkårsgrunnlag(inntekter: List<Pair<String, Inntekt>> = listOf(a1 to INNTEKT), arbeidsforhold: List<Pair<String, LocalDate>> = listOf(a1 to EPOCH)) {
        val behov = hendelselogg.vilkårsgrunnlagbehov() ?: error("Fant ikke vilkårsgrunnlagbehov")
        håndterVilkårsgrunnlag(
            vedtaksperiodeId = behov.vedtaksperiodeId.vedtaksperiode,
            skjæringstidspunkt = behov.skjæringstidspunkt,
            inntekterForOpptjeningsvurdering = InntekterForOpptjeningsvurdering(inntekter = inntekter.map { arbeidsgiverInntekt ->
                val orgnummer = arbeidsgiverInntekt.first
                val inntekt = arbeidsgiverInntekt.second
                val måned = behov.skjæringstidspunkt.minusMonths(1L)
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
            }),
            arbeidsforhold = arbeidsforhold.map { (orgnr, oppstart) ->
                Vilkårsgrunnlag.Arbeidsforhold(orgnr, oppstart, type = Arbeidsforholdtype.ORDINÆRT)
            },
            inntekter = InntektForSykepengegrunnlag(
                inntekter = inntekter.map { (orgnr, inntekt) -> grunnlag(orgnr, behov.skjæringstidspunkt, (1..3).map { inntekt }) },
                arbeidsforhold = emptyList()
            ),
            orgnummer = behov.orgnummer
        )
    }

    protected fun grunnlag(orgnr: String, skjæringstidspunkt: LocalDate, inntekter: List<Inntekt>) =
        ArbeidsgiverInntekt(
            arbeidsgiver = orgnr,
            inntekter = inntekter.mapIndexed { i, inntekt ->
                ArbeidsgiverInntekt.MånedligInntekt(
                    yearMonth = skjæringstidspunkt.minusMonths(i.toLong() + 1).yearMonth,
                    inntekt = inntekt,
                    type = ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
                    fordel = "",
                    beskrivelse = ""
                )
            }
        )

    protected fun håndterVilkårsgrunnlag(
        vedtaksperiodeId: IdInnhenter = 1.vedtaksperiode,
        skjæringstidspunkt: LocalDate,
        inntekter: InntektForSykepengegrunnlag,
        inntekterForOpptjeningsvurdering: InntekterForOpptjeningsvurdering,
        arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>,
        orgnummer: String = a1
    ) {
        (fabrikker.getValue(orgnummer).lagVilkårsgrunnlag(
            vedtaksperiodeId = vedtaksperiodeId.id(orgnummer),
            skjæringstidspunkt = skjæringstidspunkt,
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            arbeidsforhold = arbeidsforhold,
            inntektsvurderingForSykepengegrunnlag = inntekter,
            inntekterForOpptjeningsvurdering = inntekterForOpptjeningsvurdering
        )).håndter(Person::håndter)
    }

    protected fun håndterVilkårsgrunnlagTilGodkjenning() {
        håndterVilkårsgrunnlag()
        håndterYtelserTilGodkjenning()
    }

    protected fun håndterYtelser() {
        val ytelsebehov = hendelselogg.ytelserbehov() ?: error("Fant ikke ytelserbehov")
        fabrikker.getValue(ytelsebehov.orgnummer).lagYtelser(vedtaksperiodeId = ytelsebehov.vedtaksperiodeId).håndter(Person::håndter)
    }

    protected fun håndterSimulering() {
        val behov = hendelselogg.simuleringbehov() ?: error("Fant ikke simuleringsbehov")
        behov.oppdrag.forEach {
            håndterSimulering(behov.vedtaksperiodeId.vedtaksperiode, behov.utbetalingId, it.fagsystemId, it.fagområde, behov.orgnummer)
        }
    }

    protected fun håndterSimulering(vedtaksperiodeId: IdInnhenter, utbetalingId: UUID, fagsystemId: String, fagområde: String, orgnummer: String) {
        (fabrikker.getValue(orgnummer).lagSimulering(
            vedtaksperiodeId = vedtaksperiodeId.id(orgnummer),
            utbetalingId = utbetalingId,
            fagsystemId = fagsystemId,
            fagområde = fagområde,
            simuleringOK = true,
            simuleringsresultat = null
        )).håndter(Person::håndter)
    }

    protected fun håndterDødsmelding(dødsdato: LocalDate) {
        personfabrikk.lagDødsmelding(dødsdato).håndter(Person::håndter)
    }

    protected fun håndterMinimumSykdomsgradsvurderingMelding(perioderMedMinimumSykdomsgradVurdertOK: Set<Periode> = emptySet(), perioderMedMinimumSykdomsgradVurdertIkkeOK: Set<Periode> = emptySet()) {
        personfabrikk.lagMinimumSykdomsgradsvurderingMelding(perioderMedMinimumSykdomsgradVurdertOK, perioderMedMinimumSykdomsgradVurdertIkkeOK).håndter(Person::håndter)
    }

    protected fun håndterYtelserTilGodkjenning() {
        håndterYtelser()
        try {
            håndterSimulering()
        } catch (err: IllegalStateException) {
            // ok at simulering ikke er forespurt
        }
    }

    protected fun håndterOverstyrTidslinje(dager: List<ManuellOverskrivingDag>, meldingsreferanseId: UUID = UUID.randomUUID(), orgnummer: String = a1) {
        (fabrikker.getValue(orgnummer).lagHåndterOverstyrTidslinje(
            overstyringsdager = dager,
            meldingsreferanseId = meldingsreferanseId
        )).håndter(Person::håndter)
    }
    protected fun tilGodkjenning(fom: LocalDate, tom: LocalDate, orgnummer: String = a1, vedtaksperiode: Int = 1) {
        håndterSøknad(fom til tom, orgnummer)
        håndterInntektsmelding(fom, orgnummer = orgnummer, vedtaksperiode = vedtaksperiode)
        håndterVilkårsgrunnlag()
        håndterYtelserTilGodkjenning()
    }
    protected fun forlengTilGodkjenning(fom: LocalDate, tom: LocalDate, orgnummer: String = a1) {
        håndterSøknad(fom til tom, orgnummer)
        håndterYtelserTilGodkjenning()
    }
    protected fun tilYtelser(fom: LocalDate, tom: LocalDate, vararg orgnummerOgVedtaksperioder: Pair<String, Int>) {
        orgnummerOgVedtaksperioder.forEach { håndterSøknad(fom til tom, it.first) }
        orgnummerOgVedtaksperioder.forEach { håndterInntektsmelding(fom, orgnummer = it.first, vedtaksperiode = it.second) }
        håndterVilkårsgrunnlag()
    }
    protected fun tilGodkjenning(fom: LocalDate, tom: LocalDate, vararg orgnummerOgVedtaksperioder: Pair<String, Int>) {
        tilYtelser(fom, tom, *orgnummerOgVedtaksperioder)
        håndterYtelserTilGodkjenning()
    }

    protected fun nyeVedtak(fom: LocalDate, tom: LocalDate, vararg orgnummerOgVedtaksperioder: Pair<String, Int>) {
        tilYtelser(fom, tom, *orgnummerOgVedtaksperioder)
        orgnummerOgVedtaksperioder.forEach {
            håndterYtelserTilGodkjenning()
            håndterUtbetalingsgodkjenning()
            håndterUtbetalt()
        }
    }
    protected fun forlengVedtak(fom: LocalDate, tom: LocalDate, vararg orgnumre: String) {
        orgnumre.forEach { håndterSøknad(fom til tom, it) }
        orgnumre.forEach {
            håndterYtelserTilGodkjenning()
            håndterUtbetalingsgodkjenning()
            håndterUtbetalt()
        }
    }
    protected fun nyttVedtak(fom: LocalDate, tom: LocalDate, orgnummer: String = a1, vedtaksperiode: Int = 1): Utbetalingbehov {
        tilGodkjenning(fom, tom, orgnummer to vedtaksperiode)
        håndterUtbetalingsgodkjenning()
        return håndterUtbetalt()
    }
    protected fun forlengVedtak(fom: LocalDate, tom: LocalDate, orgnummer: String = a1): Utbetalingbehov {
        forlengTilGodkjenning(fom, tom, orgnummer)
        håndterUtbetalingsgodkjenning()
        return håndterUtbetalt()
    }

    protected fun håndterOverstyrArbeidsforhold(
        skjæringstidspunkt: LocalDate,
        opplysninger: List<OverstyrArbeidsforhold.ArbeidsforholdOverstyrt>
    ) {
        personfabrikk.lagOverstyrArbeidsforhold(skjæringstidspunkt, *opplysninger.toTypedArray()).håndter(Person::håndter)
    }

    protected fun håndterOverstyrArbeidsgiveropplysninger(
        skjæringstidspunkt: LocalDate,
        opplysninger: List<OverstyrtArbeidsgiveropplysning>,
        meldingsreferanseId: UUID = UUID.randomUUID()
    ) {
        personfabrikk.lagOverstyrArbeidsgiveropplysninger(skjæringstidspunkt, opplysninger, meldingsreferanseId).håndter(Person::håndter)
    }
    protected fun håndterSkjønnsmessigFastsettelse(
        skjæringstidspunkt: LocalDate,
        opplysninger: List<OverstyrtArbeidsgiveropplysning>,
        meldingsreferanseId: UUID = UUID.randomUUID()
    ) {
        personfabrikk.lagSkjønnsmessigFastsettelse(skjæringstidspunkt, opplysninger, meldingsreferanseId).håndter(Person::håndter)
    }
    protected fun håndterUtbetalingsgodkjenning(utbetalingGodkjent: Boolean = true) {
        val behov = hendelselogg.godkjenningbehov() ?: error("Fant ikke godkjenningsbehov")
        fabrikker.getValue(behov.orgnummer).lagUtbetalingsgodkjenning(
            vedtaksperiodeId = behov.vedtaksperiodeId,
            utbetalingGodkjent = utbetalingGodkjent,
            automatiskBehandling = true,
            utbetalingId = behov.utbetalingId
        ).håndter(Person::håndter)
    }
    protected fun håndterUtbetalt(status: Oppdragstatus = Oppdragstatus.AKSEPTERT): Utbetalingbehov {
        val behov = hendelselogg.utbetalingbehov() ?: error("Fant ikke utbetalingbehov")
        behov.oppdrag.forEach {
            fabrikker.getValue(behov.orgnummer).lagUtbetalinghendelse(
                utbetalingId = behov.utbetalingId,
                fagsystemId = it.fagsystemId,
                status = status
            ).håndter(Person::håndter)
        }
        return behov
    }
    protected fun håndterVilkårsgrunnlagTilUtbetalt(status: Oppdragstatus = Oppdragstatus.AKSEPTERT) {
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt(status)
    }
    protected fun håndterYtelserTilGodkjent() {
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
    }
    protected fun håndterYtelserTilUtbetalt(status: Oppdragstatus = Oppdragstatus.AKSEPTERT): Utbetalingbehov {
        håndterYtelserTilGodkjent()
        return håndterUtbetalt(status)
    }
    protected fun håndterAnnullerUtbetaling(behov: Utbetalingbehov) {
        fabrikker.getValue(behov.orgnummer).lagAnnullering(behov.utbetalingId)
            .håndter(Person::håndter)
    }

    private fun ønsketBehov(ønsket: Set<Aktivitet.Behov.Behovtype>): List<Aktivitet.Behov>? {
        return ubesvarteBehov
            .filter { it.type in ønsket }
            .takeIf { it.size >= ønsket.size }
            ?.also {
                ubesvarteBehov.clear()
            }

    }
    private fun IAktivitetslogg.infotrygdhistorikkbehov() =
        ønsketBehov(setOf(Aktivitet.Behov.Behovtype.Sykepengehistorikk))?.single()?.let {
            ubesvarteBehov.remove(it)
            Infotrygdhistorikkbehov(
                vedtaksperiodeId = UUID.fromString(it.kontekst().getValue("vedtaksperiodeId")),
                orgnummer = it.kontekst().getValue("organisasjonsnummer")
            )
        }
    private fun IAktivitetslogg.vilkårsgrunnlagbehov() =
        ønsketBehov(setOf(Aktivitet.Behov.Behovtype.InntekterForSykepengegrunnlag, Aktivitet.Behov.Behovtype.ArbeidsforholdV2, Aktivitet.Behov.Behovtype.Medlemskap))?.let {
            ubesvarteBehov.removeAll(it)
            val (vedtaksperiodeId, behovene) = it.groupBy { UUID.fromString(it.kontekst().getValue("vedtaksperiodeId")) }.entries.single()
            Vilkårsgrunnlagbehov(
                vedtaksperiodeId = vedtaksperiodeId,
                orgnummer = behovene.first().kontekst().getValue("organisasjonsnummer"),
                skjæringstidspunkt = LocalDate.parse(behovene.first().detaljer().getValue("skjæringstidspunkt") as String)
            )
        }
    private fun IAktivitetslogg.ytelserbehov() = ønsketBehov(setOf(
            Aktivitet.Behov.Behovtype.Foreldrepenger,
            Aktivitet.Behov.Behovtype.Pleiepenger,
            Aktivitet.Behov.Behovtype.Omsorgspenger,
            Aktivitet.Behov.Behovtype.Opplæringspenger,
            Aktivitet.Behov.Behovtype.Institusjonsopphold,
            Aktivitet.Behov.Behovtype.Arbeidsavklaringspenger,
            Aktivitet.Behov.Behovtype.Dagpenger
        ))
        ?.let {
            ubesvarteBehov.removeAll(it)
            val (vedtaksperiodeId, behovene) = it.groupBy { UUID.fromString(it.kontekst().getValue("vedtaksperiodeId")) }.entries.single()
            Ytelserbehov(
                vedtaksperiodeId = vedtaksperiodeId,
                orgnummer = it.first().kontekst().getValue("organisasjonsnummer")
            )
        }
    private fun IAktivitetslogg.simuleringbehov() =
        ønsketBehov(setOf(Aktivitet.Behov.Behovtype.Simulering))
            ?.let {
                ubesvarteBehov.removeAll(it)
                val (utbetalingId, oppdrag) = it.groupBy { UUID.fromString(it.kontekst().getValue("utbetalingId")) }.entries.single()
                val vedtaksperiodeId = UUID.fromString(oppdrag.first().kontekst().getValue("vedtaksperiodeId"))
                val orgnummer = oppdrag.first().kontekst().getValue("organisasjonsnummer")
                Simuleringbehov(
                    vedtaksperiodeId = vedtaksperiodeId,
                    orgnummer = orgnummer,
                    utbetalingId = utbetalingId,
                    oppdrag = oppdrag.map {
                        Oppdragbehov(
                            fagområde = it.detaljer().getValue("fagområde") as String,
                            fagsystemId = it.kontekst().getValue("fagsystemId"),
                        )
                    }
                )
            }
    private fun IAktivitetslogg.godkjenningbehov() = ønsketBehov(setOf(Aktivitet.Behov.Behovtype.Godkjenning))?.single()?.let {
        ubesvarteBehov.remove(it)
        Godkjenningbehov(
            vedtaksperiodeId = UUID.fromString(it.kontekst().getValue("vedtaksperiodeId")),
            orgnummer = it.kontekst().getValue("organisasjonsnummer"),
            utbetalingId = UUID.fromString(it.kontekst().getValue("utbetalingId"))
        )
    }
    private fun IAktivitetslogg.utbetalingbehov() =
        ønsketBehov(setOf(Aktivitet.Behov.Behovtype.Utbetaling))
            ?.let {
                ubesvarteBehov.removeAll(it)
                val (utbetalingId, oppdrag) = it.groupBy { UUID.fromString(it.kontekst().getValue("utbetalingId")) }.entries.single()
                val orgnummer = oppdrag.first().kontekst().getValue("organisasjonsnummer")
                Utbetalingbehov(
                    orgnummer = orgnummer,
                    utbetalingId = utbetalingId,
                    oppdrag = oppdrag.map {
                        Oppdragbehov(
                            fagområde = it.detaljer().getValue("fagområde") as String,
                            fagsystemId = it.kontekst().getValue("fagsystemId"),
                        )
                    }
                )
            }

    data class Infotrygdhistorikkbehov(
        val vedtaksperiodeId: UUID,
        val orgnummer: String
    )
    data class Vilkårsgrunnlagbehov(
        val vedtaksperiodeId: UUID,
        val orgnummer: String,
        val skjæringstidspunkt: LocalDate
    )
    data class Ytelserbehov(
        val vedtaksperiodeId: UUID,
        val orgnummer: String
    )
    data class Simuleringbehov(
        val vedtaksperiodeId: UUID,
        val orgnummer: String,
        val utbetalingId: UUID,
        val oppdrag: List<Oppdragbehov>
    )
    data class Godkjenningbehov(
        val vedtaksperiodeId: UUID,
        val orgnummer: String,
        val utbetalingId: UUID
    )
    data class Utbetalingbehov(
        val orgnummer: String,
        val utbetalingId: UUID,
        val oppdrag: List<Oppdragbehov>
    )
    data class Oppdragbehov(
        val fagområde: String,
        val fagsystemId: String
    )
}