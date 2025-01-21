package no.nav.helse.spleis.mediator.e2e

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.test_support.TestDataSource
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import kotliquery.queryOf
import kotliquery.sessionOf
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.februar
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
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
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.InntekterForSykepengegrunnlag
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Institusjonsopphold
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Medlemskap
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Omsorgspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Opplæringspenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Pleiepenger
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Simulering
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Sykepengehistorikk
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.spill_av_im.Forespørsel
import no.nav.helse.spill_av_im.FørsteFraværsdag
import no.nav.helse.spleis.HendelseMediator
import no.nav.helse.spleis.MessageMediator
import no.nav.helse.spleis.Subsumsjonproducer
import no.nav.helse.spleis.db.HendelseRepository
import no.nav.helse.spleis.db.PersonDao
import no.nav.helse.spleis.mediator.TestMessageFactory
import no.nav.helse.spleis.mediator.TestMessageFactory.ArbeidsavklaringspengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.Arbeidsforhold
import no.nav.helse.spleis.mediator.TestMessageFactory.ArbeidsforholdOverstyrt
import no.nav.helse.spleis.mediator.TestMessageFactory.Arbeidsgiveropplysning
import no.nav.helse.spleis.mediator.TestMessageFactory.DagpengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.InntekterForSykepengegrunnlagFraLøsning
import no.nav.helse.spleis.mediator.TestMessageFactory.InstitusjonsoppholdTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.OmsorgspengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.OpplæringspengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.PleiepengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.SkjønnsmessigFastsatt
import no.nav.helse.spleis.mediator.TestMessageFactory.UtbetalingshistorikkForFeriepengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.UtbetalingshistorikkTestdata
import no.nav.helse.spleis.mediator.VarseloppsamlerTest.Companion.Varsel
import no.nav.helse.spleis.mediator.databaseContainer
import no.nav.helse.spleis.mediator.meldinger.TestRapid
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.inntektsmeldingkontrakt.OpphoerAvNaturalytelse
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory

internal abstract class AbstractEndToEndMediatorTest {
    internal companion object {
        internal const val UNG_PERSON_FNR_2018 = "12029240045"
        internal val UNG_PERSON_FØDSELSDATO = 12.februar(1992)
        internal const val ORGNUMMER = "987654321"
        internal const val INNTEKT = 31000.00
        private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    }

    protected val meldingsfabrikk = TestMessageFactory(UNG_PERSON_FNR_2018, ORGNUMMER, INNTEKT, UNG_PERSON_FØDSELSDATO)
    protected lateinit var testRapid: TestRapid
    protected lateinit var hendelseRepository: HendelseRepository
    private lateinit var hendelseMediator: HendelseMediator
    private lateinit var messageMediator: MessageMediator
    private lateinit var dataSource: TestDataSource
    protected lateinit var subsumsjoner: MutableList<JsonNode>

    @BeforeEach
    fun setupDatabase() {
        dataSource = databaseContainer.nyTilkobling()

        testRapid = TestRapid()
        subsumsjoner = mutableListOf()
        hendelseRepository = HendelseRepository(dataSource.ds)
        hendelseMediator = HendelseMediator(
            hendelseRepository = hendelseRepository,
            personDao = PersonDao(dataSource.ds, STØTTER_IDENTBYTTE = true),
            versjonAvKode = "test-versjon",
            støtterIdentbytte = true,
            subsumsjonsproducer = object : Subsumsjonproducer {
                override fun send(fnr: String, melding: String) {
                    subsumsjoner.add(objectMapper.readTree(melding))
                }
            }
        )

        messageMediator = MessageMediator(
            rapidsConnection = testRapid,
            hendelseMediator = hendelseMediator,
            hendelseRepository = hendelseRepository
        )

        testRapid.observer(InntektsmeldingerReplayObserver(testRapid, dataSource.ds))
    }

    @AfterEach
    fun tearDown() {
        databaseContainer.droppTilkobling(dataSource)
    }

    @BeforeEach
    internal fun setupEach() {
        testRapid.reset()
    }

    protected fun antallPersoner() = sessionOf(dataSource.ds).use {
        it.run(queryOf("SELECT COUNT(1) FROM person").map { it.long(1) }.asSingle) ?: 0
    }

    protected fun antallPersonalias(fnr: String? = null) = sessionOf(dataSource.ds).use {
        it.run(queryOf("SELECT COUNT(1) FROM person_alias ${fnr?.let { "WHERE fnr=${fnr.toLong()}" } ?: ""}").map { it.long(1) }.asSingle) ?: 0
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

    protected fun sendNySøknadFrilanser(
        vararg perioder: SoknadsperiodeDTO,
        meldingOpprettet: LocalDateTime = perioder.minOfOrNull { it.fom!! }!!.atStartOfDay(),
        fnr: String = UNG_PERSON_FNR_2018
    ): UUID {
        val (id, message) = meldingsfabrikk.lagNySøknadFrilanser(*perioder, opprettet = meldingOpprettet, fnr = fnr)
        testRapid.sendTestMessage(message)
        return id.toUUID()
    }

    protected fun sendNySøknadSelvstendig(
        vararg perioder: SoknadsperiodeDTO,
        meldingOpprettet: LocalDateTime = perioder.minOfOrNull { it.fom!! }!!.atStartOfDay(),
        fnr: String = UNG_PERSON_FNR_2018
    ): UUID {
        val (id, message) = meldingsfabrikk.lagNySøknadSelvstendig(*perioder, opprettet = meldingOpprettet, fnr = fnr)
        testRapid.sendTestMessage(message)
        return id.toUUID()
    }

    protected fun sendNySøknadArbeidsledig(
        vararg perioder: SoknadsperiodeDTO,
        meldingOpprettet: LocalDateTime = perioder.minOfOrNull { it.fom!! }!!.atStartOfDay(),
        fnr: String = UNG_PERSON_FNR_2018,
        tidligereArbeidsgiverOrgnummer: String? = null
    ): UUID {
        val (id, message) = meldingsfabrikk.lagNySøknadArbeidsledig(*perioder, opprettet = meldingOpprettet, fnr = fnr, tidligereArbeidsgiverOrgnummer = tidligereArbeidsgiverOrgnummer)
        testRapid.sendTestMessage(message)
        return id.toUUID()
    }

    protected fun sendSøknad(
        fnr: String = UNG_PERSON_FNR_2018,
        perioder: List<SoknadsperiodeDTO>,
        fravær: List<FravarDTO> = emptyList(),
        andreInntektskilder: List<InntektskildeDTO>? = null,
        ikkeJobbetIDetSisteFraAnnetArbeidsforhold: Boolean = false,
        sendtNav: LocalDateTime? = perioder.maxOfOrNull { it.tom!! }?.atStartOfDay(),
        orgnummer: String = ORGNUMMER,
        korrigerer: UUID? = null,
        opprinneligSendt: LocalDateTime? = null,
        historiskeFolkeregisteridenter: List<String> = emptyList(),
        sendTilGosys: Boolean? = false,
        egenmeldingerFraSykmelding: List<LocalDate> = emptyList(),
        inntektFraNyttArbeidsforhold: List<InntektFraNyttArbeidsforholdDTO> = emptyList()
    ): UUID {
        val (id, message) = meldingsfabrikk.lagSøknadNav(
            fnr = fnr,
            perioder = perioder,
            fravær = fravær,
            andreInntektskilder = andreInntektskilder,
            ikkeJobbetIDetSisteFraAnnetArbeidsforhold = ikkeJobbetIDetSisteFraAnnetArbeidsforhold,
            sendtNav = sendtNav,
            orgnummer = orgnummer,
            korrigerer = korrigerer,
            opprinneligSendt = opprinneligSendt,
            historiskeFolkeregisteridenter = historiskeFolkeregisteridenter,
            sendTilGosys = sendTilGosys,
            egenmeldingerFraSykmelding = egenmeldingerFraSykmelding,
            inntektFraNyttArbeidsforhold = inntektFraNyttArbeidsforhold
        )

        val antallVedtaksperioderFørSøknad = testRapid.inspektør.vedtaksperiodeteller
        testRapid.sendTestMessage(message)
        val antallVedtaksperioderEtterSøknad = testRapid.inspektør.vedtaksperiodeteller
        if (antallVedtaksperioderFørSøknad < antallVedtaksperioderEtterSøknad) {
            val vedtaksperiodeIndeks = antallVedtaksperioderEtterSøknad - 1
            if (testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Sykepengehistorikk)) {
                sendUtbetalingshistorikk(vedtaksperiodeIndeks, orgnummer = orgnummer)
            }
        }
        return id.toUUID()
    }

    protected fun sendFrilanssøknad(
        fnr: String = UNG_PERSON_FNR_2018,
        perioder: List<SoknadsperiodeDTO>,
        andreInntektskilder: List<InntektskildeDTO>? = null,
        sendtNav: LocalDateTime? = perioder.maxOfOrNull { it.tom!! }?.atStartOfDay(),
        korrigerer: UUID? = null,
        opprinneligSendt: LocalDateTime? = null,
        historiskeFolkeregisteridenter: List<String> = emptyList(),
        sendTilGosys: Boolean? = false,
        egenmeldingerFraSykmelding: List<LocalDate> = emptyList()
    ): UUID {
        val (id, message) = meldingsfabrikk.lagSøknadFrilanser(
            fnr = fnr,
            perioder = perioder,
            andreInntektskilder = andreInntektskilder,
            sendtNav = sendtNav,
            korrigerer = korrigerer,
            opprinneligSendt = opprinneligSendt,
            historiskeFolkeregisteridenter = historiskeFolkeregisteridenter,
            sendTilGosys = sendTilGosys,
            egenmeldingerFraSykmelding = egenmeldingerFraSykmelding
        )

        val antallVedtaksperioderFørSøknad = testRapid.inspektør.vedtaksperiodeteller
        testRapid.sendTestMessage(message)
        val antallVedtaksperioderEtterSøknad = testRapid.inspektør.vedtaksperiodeteller
        if (antallVedtaksperioderFørSøknad < antallVedtaksperioderEtterSøknad) {
            val vedtaksperiodeIndeks = antallVedtaksperioderEtterSøknad - 1
            if (testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Sykepengehistorikk)) {
                sendUtbetalingshistorikk(vedtaksperiodeIndeks)
            }
        }
        return id.toUUID()
    }

    protected fun sendArbeidsledigsøknad(
        fnr: String = UNG_PERSON_FNR_2018,
        perioder: List<SoknadsperiodeDTO>,
        tidligereArbeidsgiverOrgnummer: String? = null,
        andreInntektskilder: List<InntektskildeDTO>? = null,
        sendtNav: LocalDateTime? = perioder.maxOfOrNull { it.tom!! }?.atStartOfDay(),
        korrigerer: UUID? = null,
        opprinneligSendt: LocalDateTime? = null,
        historiskeFolkeregisteridenter: List<String> = emptyList(),
        sendTilGosys: Boolean? = false,
        egenmeldingerFraSykmelding: List<LocalDate> = emptyList()
    ): UUID {
        val (id, message) = meldingsfabrikk.lagSøknadArbeidsledig(
            fnr = fnr,
            tidligereArbeidsgiverOrgnummer = tidligereArbeidsgiverOrgnummer,
            perioder = perioder,
            andreInntektskilder = andreInntektskilder,
            sendtNav = sendtNav,
            korrigerer = korrigerer,
            opprinneligSendt = opprinneligSendt,
            historiskeFolkeregisteridenter = historiskeFolkeregisteridenter,
            sendTilGosys = sendTilGosys,
            egenmeldingerFraSykmelding = egenmeldingerFraSykmelding
        )

        val antallVedtaksperioderFørSøknad = testRapid.inspektør.vedtaksperiodeteller
        testRapid.sendTestMessage(message)
        val antallVedtaksperioderEtterSøknad = testRapid.inspektør.vedtaksperiodeteller
        if (antallVedtaksperioderFørSøknad < antallVedtaksperioderEtterSøknad) {
            val vedtaksperiodeIndeks = antallVedtaksperioderEtterSøknad - 1
            if (testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Sykepengehistorikk)) {
                sendUtbetalingshistorikk(vedtaksperiodeIndeks)
            }
        }
        return id.toUUID()
    }

    protected fun sendSelvstendigsøknad(
        fnr: String = UNG_PERSON_FNR_2018,
        perioder: List<SoknadsperiodeDTO>,
        andreInntektskilder: List<InntektskildeDTO>? = null,
        sendtNav: LocalDateTime? = perioder.maxOfOrNull { it.tom!! }?.atStartOfDay(),
        korrigerer: UUID? = null,
        opprinneligSendt: LocalDateTime? = null,
        historiskeFolkeregisteridenter: List<String> = emptyList(),
        sendTilGosys: Boolean? = false,
        egenmeldingerFraSykmelding: List<LocalDate> = emptyList()
    ): UUID {
        val (id, message) = meldingsfabrikk.lagSøknadSelvstendig(
            fnr = fnr,
            perioder = perioder,
            andreInntektskilder = andreInntektskilder,
            sendtNav = sendtNav,
            korrigerer = korrigerer,
            opprinneligSendt = opprinneligSendt,
            historiskeFolkeregisteridenter = historiskeFolkeregisteridenter,
            sendTilGosys = sendTilGosys,
            egenmeldingerFraSykmelding = egenmeldingerFraSykmelding
        )

        val antallVedtaksperioderFørSøknad = testRapid.inspektør.vedtaksperiodeteller
        testRapid.sendTestMessage(message)
        val antallVedtaksperioderEtterSøknad = testRapid.inspektør.vedtaksperiodeteller
        if (antallVedtaksperioderFørSøknad < antallVedtaksperioderEtterSøknad) {
            val vedtaksperiodeIndeks = antallVedtaksperioderEtterSøknad - 1
            if (testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Sykepengehistorikk)) {
                sendUtbetalingshistorikk(vedtaksperiodeIndeks)
            }
        }
        return id.toUUID()
    }

    protected fun sendIdentOpphørt(
        fnr: String = UNG_PERSON_FNR_2018,
        nyttFnr: String
    ) {
        val (_, message) = meldingsfabrikk.lagIdentOpphørt(fnr, nyttFnr)
        testRapid.sendTestMessage(message)
    }

    protected fun sendKorrigerendeSøknad(
        perioder: List<SoknadsperiodeDTO>,
        fravær: List<FravarDTO> = emptyList(),
    ) {
        val (_, message) = meldingsfabrikk.lagSøknadNav(
            fnr = UNG_PERSON_FNR_2018,
            perioder = perioder,
            fravær = fravær
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendSøknadArbeidsgiver(
        vedtaksperiodeIndeks: Int,
        perioder: List<SoknadsperiodeDTO>
    ) {
        assertFalse(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Foreldrepenger))
        val (_, message) = meldingsfabrikk.lagSøknadArbeidsgiver(perioder)
        testRapid.sendTestMessage(message)
    }

    protected fun sendInntektsmelding(
        arbeidsgiverperiode: List<Periode>,
        førsteFraværsdag: LocalDate,
        opphørAvNaturalytelser: List<OpphoerAvNaturalytelse> = emptyList(),
        beregnetInntekt: Double = INNTEKT,
        opphørsdatoForRefusjon: LocalDate? = null,
        orgnummer: String = ORGNUMMER,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null
    ): Pair<UUID, String> {
        return meldingsfabrikk.lagLpsInntektsmelding(
            arbeidsgiverperiode,
            førsteFraværsdag,
            opphørAvNaturalytelser,
            beregnetInntekt,
            opphørsdatoForRefusjon,
            orgnummer,
            begrunnelseForReduksjonEllerIkkeUtbetalt,
        ).let { (id, message) ->
            testRapid.sendTestMessage(message)
            id.toUUID() to message
        }
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

    protected fun sendSykepengegrunnlagForArbeidsgiver(
        vedtaksperiodeIndeks: Int = -1,
        skjæringstidspunkt: LocalDate = 1.januar,
        orgnummer: String = ORGNUMMER,
        inntekterForSykepengegrunnlag: List<InntekterForSykepengegrunnlagFraLøsning> = sykepengegrunnlag(
            skjæringstidspunkt = skjæringstidspunkt,
            inntekter = listOf(InntekterForSykepengegrunnlagFraLøsning.Inntekt(INNTEKT, orgnummer))
        ),
    ) {
        val vedtaksperiodeId = if (vedtaksperiodeIndeks == -1) UUID.randomUUID() else testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks)
        val (_, message) = meldingsfabrikk.lagSykepengegrunnlagForArbeidsgiver(
            vedtaksperiodeId = vedtaksperiodeId,
            orgnummer = orgnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            tilstand = TilstandType.AVVENTER_INNTEKTSMELDING,
            inntekterForSykepengegrunnlag = inntekterForSykepengegrunnlag
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendVilkårsgrunnlag(
        vedtaksperiodeIndeks: Int,
        skjæringstidspunkt: LocalDate = 1.januar,
        orgnummer: String = ORGNUMMER,
        arbeidsforhold: List<Arbeidsforhold> = listOf(
            Arbeidsforhold(orgnummer, 1.januar(2010), null, Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT)
        ),
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
        inntekterForSykepengegrunnlag: List<InntekterForSykepengegrunnlagFraLøsning> = sykepengegrunnlag(
            skjæringstidspunkt = skjæringstidspunkt,
            inntekter = listOf(InntekterForSykepengegrunnlagFraLøsning.Inntekt(INNTEKT, orgnummer))
        ),
    ) {
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, Medlemskap))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, ArbeidsforholdV2))
        assertTrue(testRapid.inspektør.harEtterspurteBehov(vedtaksperiodeIndeks, InntekterForSykepengegrunnlag))
        val skjæringstidspunktFraBehov =
            testRapid.inspektør.etterspurteBehov(Medlemskap).path("Medlemskap").path("skjæringstidspunkt").asLocalDate()
        val (_, message) = meldingsfabrikk.lagVilkårsgrunnlag(
            vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks),
            skjæringstidspunkt = skjæringstidspunktFraBehov,
            tilstand = testRapid.inspektør.tilstandForEtterspurteBehov(vedtaksperiodeIndeks, InntekterForSykepengegrunnlag),
            inntekterForSykepengegrunnlag = inntekterForSykepengegrunnlag,
            inntekterForOpptjeningsvurdering = listOf(
                TestMessageFactory.InntekterForOpptjeningsvurderingFraLøsning(
                    måned = YearMonth.from(skjæringstidspunktFraBehov.minusMonths(1)),
                    inntekter = listOf(
                        TestMessageFactory.InntekterForOpptjeningsvurderingFraLøsning.Inntekt(
                            32000.0,
                            ORGNUMMER
                        )
                    ),
                )
            ),
            arbeidsforhold = (arbeidsforhold),
            medlemskapstatus = medlemskapstatus,
            orgnummer = orgnummer
        )
        testRapid.sendTestMessage(message)
    }

    fun sykepengegrunnlag(
        skjæringstidspunkt: LocalDate,
        inntekter: List<InntekterForSykepengegrunnlagFraLøsning.Inntekt>
    ): List<InntekterForSykepengegrunnlagFraLøsning> {
        return (3L downTo 1L).map {
            val mnd = YearMonth.from(skjæringstidspunkt).minusMonths(it)
            InntekterForSykepengegrunnlagFraLøsning(mnd, inntekter)
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
        val etterspurteBehov = testRapid.inspektør.alleEtterspurteBehov(Utbetaling)
        etterspurteBehov.forEach { behov ->
            val (_, message) = meldingsfabrikk.lagUtbetaling(
                fagsystemId = behov.path("fagsystemId").asText(),
                utbetalingId = behov.path("utbetalingId").asText(),
                utbetalingOK = utbetalingOK
            )
            testRapid.sendTestMessage(message)
        }
    }

    protected fun sendAvstemming() {
        val (_, message) = meldingsfabrikk.lagAvstemming()
        testRapid.sendTestMessage(message)
    }

    protected fun sendAnnullering(utbetalingId: String) {
        val (_, message) = meldingsfabrikk.lagAnnullering(utbetalingId)
        testRapid.sendTestMessage(message)
    }

    protected fun sendOverstyringTidslinje(dager: List<ManuellOverskrivingDag>) {
        val (_, message) = meldingsfabrikk.lagOverstyringTidslinje(dager)
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

    protected fun sendMinimumSykdomdsgradVurdert(
        perioderMedMinimumSykdomsgradVurdertOK: List<Pair<LocalDate, LocalDate>>,
        perioderMedMinimumSykdomsgradVurdertIkkeOK: List<Pair<LocalDate, LocalDate>>
    ) {
        val (_, message) = meldingsfabrikk.lagMinimumSykdomsgradVurdert(
            perioderMedMinimumSykdomsgradVurdertOK,
            perioderMedMinimumSykdomsgradVurdertIkkeOK
        )
        testRapid.sendTestMessage(message)
    }

    protected fun sendSkjønnsmessigFastsettelse(
        skjæringstidspunkt: LocalDate,
        skjønnsmessigFastsatt: List<SkjønnsmessigFastsatt>
    ): Pair<UUID, String> {
        val (id, message) = meldingsfabrikk.lagSkjønnsmessigFastsettelse(
            skjæringstidspunkt = skjæringstidspunkt,
            skjønnsmessigFastsatt = skjønnsmessigFastsatt
        )
        testRapid.sendTestMessage(message)
        return id.toUUID() to message
    }

    protected fun sendInfotrygdendring() {
        val (_, message) = meldingsfabrikk.lagInfotrygdendringer()
        testRapid.sendTestMessage(message)
    }

    protected fun nyttVedtak(fom: LocalDate = LocalDate.of(2018, 1, 1), tom: LocalDate = LocalDate.of(2018, 1, 31)) {
        val soknadperiode = SoknadsperiodeDTO(fom, tom, sykmeldingsgrad = 100)
        sendNySøknad(soknadperiode)
        sendSøknad(perioder = listOf(soknadperiode))
        sendInntektsmelding(arbeidsgiverperiode = listOf(Periode(fom, fom.plusDays(15))), fom)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
    }

    protected fun assertUtbetalingtype(utbetalingIndeks: Int, type: String) {
        assertEquals(
            type,
            testRapid.inspektør.utbetalingtype(utbetalingIndeks)
        )
    }

    protected fun assertTilstander(vedtaksperiodeIndeks: Int, vararg tilstand: String) {
        assertEquals(tilstand.toList(), testRapid.inspektør.tilstander(testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks)))
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

    protected fun assertVarsel(vedtaksperiodeIndeks: Int, varselkode: Varselkode) {
        val vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks)
        assertNotNull(testRapid.inspektør.varsel(vedtaksperiodeId, varselkode))
    }

    protected fun assertIngenVarsler(vedtaksperiodeIndeks: Int) {
        val vedtaksperiodeId = testRapid.inspektør.vedtaksperiodeId(vedtaksperiodeIndeks)
        assertNotNull(testRapid.inspektør.varsler(vedtaksperiodeId))
    }

    protected fun assertIngenVarsler() {
        assertEquals(emptyList<Varsel>(), testRapid.inspektør.varsler())
    }

    private class InntektsmeldingerReplayObserver(private val testRapid: TestRapid, private val dataSource: HikariDataSource) : TestRapid.TestRapidObserver {
        private companion object {
            private val log = LoggerFactory.getLogger(InntektsmeldingerReplayObserver::class.java)
            private val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        private val håndterteInntektsmeldinger = mutableListOf<UUID>()

        override fun onMessagePublish(message: String) {
            val node = objectMapper.readTree(message)
            when (node.path("@event_name").asText()) {
                "trenger_inntektsmelding_replay" -> håndterInntektsmeldingReplay(node)
                "inntektsmelding_håndtert" -> håndterInntektsmeldingHåndtert(node)
            }
        }

        private fun håndterInntektsmeldingHåndtert(node: JsonNode) {
            val inntektsmeldingId = node.path("inntektsmeldingId").textValue().toUUID()
            håndterteInntektsmeldinger.add(inntektsmeldingId)
        }

        private fun håndterInntektsmeldingReplay(node: JsonNode) {
            val fnr = node.path("fødselsnummer").textValue()
            val orgnr = node.path("organisasjonsnummer").textValue()
            val vedtaksperiodeId = node.path("vedtaksperiodeId").textValue().toUUID()
            val forespørsel = Forespørsel(
                fnr = fnr,
                orgnr = orgnr,
                vedtaksperiodeId = vedtaksperiodeId,
                skjæringstidspunkt = node.path("skjæringstidspunkt").asLocalDate(),
                førsteFraværsdager = node.path("førsteFraværsdager").map { FørsteFraværsdag(it.path("organisasjonsnummer").textValue(), it.path("førsteFraværsdag").asLocalDate()) },
                sykmeldingsperioder = node.path("sykmeldingsperioder").map { no.nav.helse.spill_av_im.Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()) },
                egenmeldinger = node.path("egenmeldinger").map { no.nav.helse.spill_av_im.Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()) },
                harForespurtArbeidsgiverperiode = node.path("trengerArbeidsgiverperiode").booleanValue(),
                erPotensiellForespørsel = node.path("potensiellForespørsel").booleanValue()
            )

            val replayMessage = lagInntektsmeldingerReplayMessage(forespørsel)


            log.info("lager inntektsmeldinger_replay-melding for {}", kv("vedtaksperiodeId", vedtaksperiodeId))
            testRapid.sendTestMessage(replayMessage.toJson())
        }

        private fun finnInntektsmeldinger(fnr: String): List<JsonNode> =
            sessionOf(dataSource).use { session ->
                session.run(queryOf("SELECT data FROM melding WHERE fnr = ? AND melding_type = 'INNTEKTSMELDING' ORDER BY lest_dato ASC", fnr.toLong()).map {
                    objectMapper.readTree(it.string("data"))
                }.asList)
            }

        private fun lagInntektsmeldingerReplayMessage(forespørsel: Forespørsel): JsonMessage {
            // replayer alle inntektsmeldinger som ikke er hånderte og som kan være relevante
            val replays = finnInntektsmeldinger(forespørsel.fnr)
                .filter { it.path("virksomhetsnummer").asText() == forespørsel.orgnr }
                .filterNot { inntektsmelding ->
                    inntektsmelding.path("@id").textValue().toUUID() in håndterteInntektsmeldinger
                }
                .filter { node ->
                    val im = objectMapper.treeToValue<Inntektsmelding>(node)
                    forespørsel.erInntektsmeldingRelevant(im)
                }
                .map { inntektsmelding -> inntektsmelding.path("@id").asText().toUUID() to inntektsmelding }

            return JsonMessage.newMessage("inntektsmeldinger_replay", mapOf(
                "fødselsnummer" to forespørsel.fnr,
                "organisasjonsnummer" to forespørsel.orgnr,
                "vedtaksperiodeId" to "${forespørsel.vedtaksperiodeId}",
                "inntektsmeldinger" to replays.map { (internDokumentId, jsonNode) ->
                    mapOf(
                        "internDokumentId" to internDokumentId,
                        "inntektsmelding" to objectMapper.convertValue<Map<String, Any?>>(jsonNode)
                    )
                }
            ))
        }
    }
}
