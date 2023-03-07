package no.nav.helse.spleis.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.zaxxer.hikari.HikariConfig
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource
import no.nav.helse.februar
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.januar
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Arbeidsavklaringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.ArbeidsforholdV2
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Dagpenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Foreldrepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.InntekterForSammenligningsgrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.InntekterForSykepengegrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Institusjonsopphold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Medlemskap
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Omsorgspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Opplæringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Pleiepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Simulering
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Sykepengehistorikk
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helse.spleis.HendelseMediator
import no.nav.helse.spleis.MessageMediator
import no.nav.helse.spleis.TestMessageFactory
import no.nav.helse.spleis.TestMessageFactory.ArbeidsavklaringspengerTestdata
import no.nav.helse.spleis.TestMessageFactory.Arbeidsforhold
import no.nav.helse.spleis.TestMessageFactory.ArbeidsforholdOverstyrt
import no.nav.helse.spleis.TestMessageFactory.Arbeidsgiveropplysning
import no.nav.helse.spleis.TestMessageFactory.DagpengerTestdata
import no.nav.helse.spleis.TestMessageFactory.InntekterForSammenligningsgrunnlagFraLøsning
import no.nav.helse.spleis.TestMessageFactory.InntekterForSykepengegrunnlagFraLøsning
import no.nav.helse.spleis.TestMessageFactory.InstitusjonsoppholdTestdata
import no.nav.helse.spleis.TestMessageFactory.OmsorgspengerTestdata
import no.nav.helse.spleis.TestMessageFactory.OpplæringspengerTestdata
import no.nav.helse.spleis.TestMessageFactory.PleiepengerTestdata
import no.nav.helse.spleis.TestMessageFactory.Subsumsjon
import no.nav.helse.spleis.TestMessageFactory.UtbetalingshistorikkForFeriepengerTestdata
import no.nav.helse.spleis.TestMessageFactory.UtbetalingshistorikkTestdata
import no.nav.helse.spleis.db.HendelseRepository
import no.nav.helse.spleis.db.PersonDao
import no.nav.helse.spleis.e2e.SpleisDataSource.migratedDb
import no.nav.helse.spleis.meldinger.TestRapid
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.inntektsmeldingkontrakt.OpphoerAvNaturalytelse
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractEndToEndMediatorTest {
    internal companion object {
        internal const val UNG_PERSON_FNR_2018 = "12029240045"
        internal val UNG_PERSON_FØDSELSDATO = 12.februar(1992)
        internal const val AKTØRID = "42"
        internal const val ORGNUMMER = "987654321"
        internal const val INNTEKT = 31000.00
    }

    protected val meldingsfabrikk = TestMessageFactory(UNG_PERSON_FNR_2018, AKTØRID, ORGNUMMER, INNTEKT, UNG_PERSON_FØDSELSDATO)
    protected val testRapid = TestRapid()
    private lateinit var dataSource: DataSource
    private lateinit var hendelseMediator: HendelseMediator
    private lateinit var messageMediator: MessageMediator

    @BeforeAll
    internal fun setupAll() {
        dataSource = migratedDb

        hendelseMediator = HendelseMediator(
            rapidsConnection = testRapid,
            hendelseRepository = HendelseRepository(dataSource),
            personDao = PersonDao(dataSource),
            versjonAvKode = "test-versjon"
        )

        messageMediator = MessageMediator(
            rapidsConnection = testRapid,
            hendelseMediator = hendelseMediator,
            hendelseRepository = HendelseRepository(dataSource)
        )
    }

    @BeforeEach
    internal fun setupEach() {
        resetDatabase()
        testRapid.reset()
    }

    protected fun sendNySøknad(
        vararg perioder: SoknadsperiodeDTO,
        meldingOpprettet: LocalDateTime = perioder.minOfOrNull { it.fom!! }!!.atStartOfDay(),
        orgnummer: String = ORGNUMMER,
        fnr: String = UNG_PERSON_FNR_2018
    ): UUID {
        val (id, message) = meldingsfabrikk.lagNySøknad(*perioder, opprettet = meldingOpprettet, orgnummer = orgnummer, fnr = fnr)
        testRapid.sendTestMessage(message)
        return id.toUUID()
    }

    protected fun sendSøknad(
        fnr: String = UNG_PERSON_FNR_2018,
        perioder: List<SoknadsperiodeDTO>,
        fravær: List<FravarDTO> = emptyList(),
        egenmeldinger: List<PeriodeDTO> = emptyList(),
        andreInntektskilder: List<InntektskildeDTO>? = null,
        sendtNav: LocalDateTime? = perioder.maxOfOrNull { it.tom!! }?.atStartOfDay(),
        orgnummer: String = ORGNUMMER,
        korrigerer: UUID? = null,
        opprinneligSendt: LocalDateTime? = null,
        historiskeFolkeregisteridenter: List<String> = emptyList()
    ): UUID {
        val (id, message) = meldingsfabrikk.lagSøknadNav(
            fnr = fnr,
            perioder = perioder,
            fravær = fravær,
            egenmeldinger = egenmeldinger,
            andreInntektskilder = andreInntektskilder,
            sendtNav = sendtNav,
            orgnummer = orgnummer,
            korrigerer = korrigerer,
            opprinneligSendt = opprinneligSendt,
            historiskeFolkeregisteridenter = historiskeFolkeregisteridenter,
        )

        val perioderFør = testRapid.inspektør.vedtaksperiodeteller
        testRapid.sendTestMessage(message)
        val perioderEtter = testRapid.inspektør.vedtaksperiodeteller
        if (perioderFør < perioderEtter) {
            val vedtaksperiodeIndeks = perioderEtter - perioderFør - 1
            if (testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Sykepengehistorikk)) {
                sendUtbetalingshistorikk(vedtaksperiodeIndeks, orgnummer = orgnummer)
            }
        }
        return id.toUUID()
    }

    protected fun sendKorrigerendeSøknad(
        perioder: List<SoknadsperiodeDTO>,
        fravær: List<FravarDTO> = emptyList(),
        egenmeldinger: List<PeriodeDTO> = emptyList()
    ) {
        val (_, message) = meldingsfabrikk.lagSøknadNav(
            fnr = UNG_PERSON_FNR_2018,
            perioder = perioder,
            fravær = fravær,
            egenmeldinger = egenmeldinger,
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendSøknadArbeidsgiver(
        vedtaksperiodeIndeks: Int,
        perioder: List<SoknadsperiodeDTO>,
        egenmeldinger: List<PeriodeDTO> = emptyList()
    ) {
        assertFalse(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Foreldrepenger))
        val (_, message) = meldingsfabrikk.lagSøknadArbeidsgiver(perioder, egenmeldinger)
        testRapid.sendTestMessage(message)
    }

    protected fun sendInntektsmelding(
        arbeidsgiverperiode: List<Periode>,
        førsteFraværsdag: LocalDate,
        opphørAvNaturalytelser: List<OpphoerAvNaturalytelse> = emptyList(),
        beregnetInntekt: Double = INNTEKT,
        opphørsdatoForRefusjon: LocalDate? = null,
        orgnummer: String = ORGNUMMER
    ): Pair<UUID, String> {
        return meldingsfabrikk.lagInnteksmelding(
            arbeidsgiverperiode,
            førsteFraværsdag,
            opphørAvNaturalytelser,
            beregnetInntekt,
            opphørsdatoForRefusjon,
            orgnummer
        ).let { (id, message) ->
            testRapid.sendTestMessage(message)
            id.toUUID() to message
        }
    }

    protected fun sendInntektsmeldingReplay(
        vedtaksperiodeIndeks: Int = 0,
        inntektsmelding: String
    ) {
        val (_, message) = meldingsfabrikk.lagInnteksmeldingReplay(
            vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
            inntektsmelding = inntektsmelding
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendNyUtbetalingpåminnelse(utbetalingIndeks: Int, status: Utbetalingstatus = Utbetalingstatus.IKKE_UTBETALT) {
        val utbetalingId = testRapid.inspektør.utbetalingId(utbetalingIndeks)
        val (_, message) = meldingsfabrikk.lagUtbetalingpåminnelse(utbetalingId, status)
        testRapid.sendTestMessage(message)
    }

    protected fun sendNyPåminnelse(
        vedtaksperiodeIndeks: Int = -1,
        tilstandType: TilstandType = TilstandType.START,
        orgnummer: String = ORGNUMMER,
        tilstandsendringstidspunkt: LocalDateTime = LocalDateTime.now()
    ): UUID {
        val vedtaksperiodeId = if (vedtaksperiodeIndeks == -1) UUID.randomUUID() else testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks)
        val (_, message) = meldingsfabrikk.lagPåminnelse(vedtaksperiodeId, tilstandType, orgnummer, tilstandsendringstidspunkt)
        testRapid.sendTestMessage(message)
        return vedtaksperiodeId
    }

    protected fun sendUtbetalingsgodkjenning(
        vedtaksperiodeIndeks: Int,
        godkjent: Boolean = true,
        saksbehandlerIdent: String = "O123456",
        saksbehandlerEpost: String = "jan@banan.no",
        automatiskBehandling: Boolean = false,
        makstidOppnådd: Boolean = false,
        godkjenttidspunkt: LocalDateTime = LocalDateTime.now(),
        orgnummer: String = ORGNUMMER
    ) {
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Godkjenning))
        val (_, message) = meldingsfabrikk.lagUtbetalingsgodkjenning(
            vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
            orgnummer = orgnummer,
            utbetalingId = UUID.fromString(testRapid.inspektør.etterspurteBehov(Godkjenning).path("utbetalingId").asText()),
            tilstand = testRapid.inspektør.tilstandForEtterspurteBehov(vedtaksperiodeIndeks, Godkjenning),
            utbetalingGodkjent = godkjent,
            saksbehandlerIdent = saksbehandlerIdent,
            saksbehandlerEpost = saksbehandlerEpost,
            automatiskBehandling = automatiskBehandling,
            makstidOppnådd = makstidOppnådd,
            godkjenttidspunkt = godkjenttidspunkt
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendYtelser(
        vedtaksperiodeIndeks: Int,
        pleiepenger: List<PleiepengerTestdata> = emptyList(),
        omsorgspenger: List<OmsorgspengerTestdata> = emptyList(),
        opplæringspenger: List<OpplæringspengerTestdata> = emptyList(),
        institusjonsoppholdsperioder: List<InstitusjonsoppholdTestdata> = emptyList(),
        arbeidsavklaringspenger: List<ArbeidsavklaringspengerTestdata> = emptyList(),
        dagpenger: List<DagpengerTestdata> = emptyList(),
        orgnummer: String = ORGNUMMER
    ) {
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Foreldrepenger))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Pleiepenger))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Omsorgspenger))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Opplæringspenger))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Arbeidsavklaringspenger))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Dagpenger))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Institusjonsopphold))
        val (_, message) = meldingsfabrikk.lagYtelser(
            vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
            tilstand = testRapid.inspektør.tilstandForEtterspurteBehov(vedtaksperiodeIndeks, Foreldrepenger),
            pleiepenger = pleiepenger,
            omsorgspenger = omsorgspenger,
            opplæringspenger = opplæringspenger,
            institusjonsoppholdsperioder = institusjonsoppholdsperioder,
            arbeidsavklaringspenger = arbeidsavklaringspenger,
            dagpenger = dagpenger,
            orgnummer = orgnummer
        )
        testRapid.sendTestMessage(message)
    }

    private fun sendUtbetalingshistorikk(
        vedtaksperiodeIndeks: Int,
        sykepengehistorikk: List<UtbetalingshistorikkTestdata> = emptyList(),
        orgnummer: String? = null
    ) {
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Sykepengehistorikk))
        val (_, message) = meldingsfabrikk.lagUtbetalingshistorikk(
            testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
            TilstandType.AVVENTER_INNTEKTSMELDING,
            sykepengehistorikk,
            orgnummer = orgnummer
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendUtbetalingshistorikkEtterInfotrygdendring(sykepengehistorikk: List<UtbetalingshistorikkTestdata> = emptyList()) {
        val (_, message) = meldingsfabrikk.lagUtbetalingshistorikkEtterInfotrygdendring(sykepengehistorikk)
        testRapid.sendTestMessage(message)
    }

    protected fun sendUtbetalingshistorikkForFeriepenger(testdata: UtbetalingshistorikkForFeriepengerTestdata) {
        val (_, message) = meldingsfabrikk.lagUtbetalingshistorikkForFeriepenger(testdata)
        testRapid.sendTestMessage(message)
    }

    protected fun sendVilkårsgrunnlag(
        vedtaksperiodeIndeks: Int,
        skjæringstidspunkt: LocalDate = 1.januar,
        orgnummer: String = ORGNUMMER,
        inntekter: List<InntekterForSammenligningsgrunnlagFraLøsning> = sammenligningsgrunnlag(
            skjæringstidspunkt = skjæringstidspunkt,
            inntekter = listOf(InntekterForSammenligningsgrunnlagFraLøsning.Inntekt(INNTEKT, orgnummer))
        ),
        arbeidsforhold: List<Arbeidsforhold> = listOf(
            Arbeidsforhold(
                orgnummer,
                1.januar(2010),
                null
            )
        ),
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
        inntekterForSykepengegrunnlag: List<InntekterForSykepengegrunnlagFraLøsning> = sykepengegrunnlag(
            skjæringstidspunkt = skjæringstidspunkt,
            inntekter = listOf(InntekterForSykepengegrunnlagFraLøsning.Inntekt(INNTEKT, orgnummer))
        ),
    ) {
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, InntekterForSammenligningsgrunnlag))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Medlemskap))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, ArbeidsforholdV2))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, InntekterForSykepengegrunnlag))
        val (_, message) = meldingsfabrikk.lagVilkårsgrunnlag(
            vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
            skjæringstidspunkt = testRapid.inspektør.etterspurteBehov(Medlemskap).path("Medlemskap").path("skjæringstidspunkt").asLocalDate(),
            tilstand = testRapid.inspektør.tilstandForEtterspurteBehov(
                vedtaksperiodeIndeks,
                InntekterForSammenligningsgrunnlag
            ),
            inntekter = inntekter,
            arbeidsforhold = (arbeidsforhold),
            medlemskapstatus = medlemskapstatus,
            inntekterForSykepengegrunnlag = inntekterForSykepengegrunnlag,
            orgnummer = orgnummer
        )
        testRapid.sendTestMessage(message)
    }

    fun sykepengegrunnlag(
        skjæringstidspunkt: LocalDate,
        inntekter: List<InntekterForSykepengegrunnlagFraLøsning.Inntekt>,
        arbeidsforhold: List<InntekterForSykepengegrunnlagFraLøsning.Arbeidsforhold> = emptyList()
    ): List<InntekterForSykepengegrunnlagFraLøsning> {
        return (3L downTo 1L).map {
            val mnd = YearMonth.from(skjæringstidspunkt).minusMonths(it)
            InntekterForSykepengegrunnlagFraLøsning(mnd, inntekter, arbeidsforhold)
        }
    }

    fun sammenligningsgrunnlag(
        skjæringstidspunkt: LocalDate,
        inntekter: List<InntekterForSammenligningsgrunnlagFraLøsning.Inntekt>
    ): List<InntekterForSammenligningsgrunnlagFraLøsning> {
        return (12L downTo 1L).map {
            val mnd = YearMonth.from(skjæringstidspunkt).minusMonths(it)
            InntekterForSammenligningsgrunnlagFraLøsning(mnd, inntekter)
        }
    }

    protected fun sendSimulering(
        vedtaksperiodeIndeks: Int,
        status: SimuleringMessage.Simuleringstatus,
        forventedeFagområder: Set<String> = setOf("SPREF"),
        orgnummer: String = ORGNUMMER
    ) {
        val fagområder = mutableSetOf<String>()
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Simulering))
        testRapid.inspektør.alleEtterspurteBehov(Simulering).forEach { behov ->
            val (_, message) = meldingsfabrikk.lagSimulering(
                vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
                orgnummer = orgnummer,
                tilstand = testRapid.inspektør.tilstandForEtterspurteBehov(vedtaksperiodeIndeks, Simulering),
                status = status,
                utbetalingId = UUID.fromString(behov.path("utbetalingId").asText()),
                fagsystemId = behov.path("Simulering").path("fagsystemId").asText(),
                fagområde = behov.path("Simulering").path("fagområde").asText().also {
                    fagområder.add(it)
                }
            )
            testRapid.sendTestMessage(message)
        }
        assertEquals(forventedeFagområder, fagområder)
    }

    protected fun sendEtterbetaling(
        fagsystemId: String = testRapid.inspektør.etterspurteBehov(Utbetaling).path(Utbetaling.name).path("fagsystemId").asText(),
        gyldighetsdato: LocalDate
    ) {
        val (_, message) = meldingsfabrikk.lagEtterbetaling(
            fagsystemId = fagsystemId,
            gyldighetsdato = gyldighetsdato
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendEtterbetalingMedHistorikk(
        fagsystemId: String = testRapid.inspektør.etterspurteBehov(Utbetaling).path(Utbetaling.name).path("fagsystemId").asText(),
        gyldighetsdato: LocalDate
    ) {
        val (_, message) = meldingsfabrikk.lagEtterbetalingMedHistorikk(
            fagsystemId = fagsystemId,
            gyldighetsdato = gyldighetsdato
        )

        testRapid.sendTestMessage(message)
    }

    protected fun sendUtbetaling(utbetalingOK: Boolean = true) {
        val etterspurtBehov = testRapid.inspektør.etterspurteBehov(Utbetaling)
        sendUtbetaling(utbetalingOK, etterspurtBehov)
        val personbehov = testRapid.inspektør.etterspurteBehov(Utbetaling).takeUnless { etterspurtBehov === it } ?: return
        sendUtbetaling(utbetalingOK, personbehov)
    }

    private fun sendUtbetaling(utfall: Boolean, behov: JsonNode) {
        val (_, kvitteringmelding) = meldingsfabrikk.lagUtbetaling(
            fagsystemId = behov.path("fagsystemId").asText(),
            utbetalingId = behov.path("utbetalingId").asText(),
            utbetalingOK = utfall
        )
        testRapid.sendTestMessage(kvitteringmelding)
    }

    protected fun sendAvstemming() {
        val (_, message) = meldingsfabrikk.lagAvstemming()
        testRapid.sendTestMessage(message)
    }

    protected fun sendAnnullering(fagsystemId: String) {
        val (_, message) = meldingsfabrikk.lagAnnullering(fagsystemId)
        testRapid.sendTestMessage(message)
    }

    protected fun sendOverstyringTidslinje(dager: List<ManuellOverskrivingDag>) {
        val (_, message) = meldingsfabrikk.lagOverstyringTidslinje(dager)
        testRapid.sendTestMessage(message)
    }

    protected fun sendOverstyringInntekt(inntekt: Double, skjæringstidspunkt: LocalDate, subsumsjon: Subsumsjon?, orgnummer: String = ORGNUMMER, forklaring: String = "forklaring") {
        val (_, message) = meldingsfabrikk.lagOverstyringInntekt(inntekt, skjæringstidspunkt, subsumsjon, orgnummer, forklaring)
        testRapid.sendTestMessage(message)
    }

    protected fun sendOverstyringArbeidsforhold(
        skjæringstidspunkt: LocalDate,
        overstyrteArbeidsforhold: List<ArbeidsforholdOverstyrt>
    ) {
        val (_, message) = meldingsfabrikk.lagOverstyrArbeidsforhold(skjæringstidspunkt, overstyrteArbeidsforhold)
        testRapid.sendTestMessage(message)
    }

    protected fun sendOverstyrArbeidsgiveropplysninger(
        skjæringstidspunkt: LocalDate,
        arbeidsgiveropplysninger: List<Arbeidsgiveropplysning>
    ) {
        val (_, message) = meldingsfabrikk.lagOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiveropplysninger = arbeidsgiveropplysninger
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendInfotrygdendring() {
        val (_, message) = meldingsfabrikk.lagInfotrygdendringer()
        testRapid.sendTestMessage(message)
    }

    protected fun assertUtbetalingtype(utbetalingIndeks: Int, type: String) {
        assertEquals(
            type,
            testRapid.inspektør.utbetalingtype(utbetalingIndeks)
        )
    }

    protected fun assertTilstander(vedtaksperiodeIndeks: Int, vararg tilstand: String) {
        assertEquals(
            tilstand.toList(),
            testRapid.inspektør.tilstander(testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks))
        )
    }

    protected fun assertTilstand(vedtaksperiodeIndeks: Int, tilstand: String) {
        assertEquals(tilstand, testRapid.inspektør.tilstander(testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks)).lastOrNull())
    }

    protected fun assertUtbetalingTilstander(utbetalingIndeks: Int, vararg tilstand: String) {
        assertEquals(
            tilstand.toList(),
            testRapid.inspektør.utbetalingtilstander(utbetalingIndeks)
        )
    }

    protected fun assertIkkeForkastedeTilstander(vedtaksperiodeIndeks: Int, vararg tilstand: String) {
        assertEquals(
            tilstand.toList(),
            testRapid.inspektør.tilstanderUtenForkastede(testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks))
        )
    }

    protected fun assertForkastedeTilstander(vedtaksperiodeIndeks: Int, vararg tilstand: String) {
        assertEquals(
            tilstand.toList(),
            testRapid.inspektør.forkastedeTilstander(testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks))
        )
    }
}

internal fun createHikariConfig(jdbcUrl: String) =
    HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
    }
