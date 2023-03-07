package no.nav.helse.spleis.e2e

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.flex.sykepengesoknad.kafka.FravarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helse.spleis.MessageMediator
import no.nav.helse.spleis.TestHendelseMediator
import no.nav.helse.spleis.db.HendelseRepository
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.inntektsmeldingkontrakt.Naturalytelse
import no.nav.inntektsmeldingkontrakt.OpphoerAvNaturalytelse
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KunEnArbeidsgiverMediatorTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `kort periode`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 10.januar, sykmeldingsgrad = 100))
        sendSøknad(perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 10.januar, sykmeldingsgrad = 100)))
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVSLUTTET_UTEN_UTBETALING"
        )
    }

    @Test
    fun `ingen historie med Søknad først`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "AVVENTER_ARBEIDSGIVERKVITTERING", "UTBETALT")
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_UTBETALING",
            "AVSLUTTET"
        )
    }

    @Test
    fun `bare ferie`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)),
            fravær = listOf(FravarDTO(19.januar, 26.januar, FravarstypeDTO.FERIE))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVSLUTTET_UTEN_UTBETALING",
            "AVSLUTTET_UTEN_UTBETALING"
        )
    }

    @Test
    fun `bare permisjon`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)),
            fravær = listOf(FravarDTO(19.januar, 26.januar, FravarstypeDTO.PERMISJON))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVSLUTTET_UTEN_UTBETALING",
            "AVSLUTTET_UTEN_UTBETALING"
        )
    }

    @Test
    fun `ikke godkjent utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0, false)
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "IKKE_GODKJENT")
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_INFOTRYGD"
        )
    }

    @Test
    fun `Korrigert søknad medfører foreldede dager og ingen utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        val søknadId = sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            sendtNav = 1.mai.atStartOfDay()
        )
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            sendtNav = 2.mai.atStartOfDay(),
            korrigerer = søknadId,
            opprinneligSendt = 1.mai.atStartOfDay()
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(0)

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK"
        )
    }

    @Test
    fun `perioder påvirket av annullering-event blir forkastet men forblir i Avsluttet`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        val fagsystemId = testRapid.inspektør.alleEtterspurteBehov(Utbetaling).last { it.path(Utbetaling.name).path("fagområde").asText() == "SPREF"}.path(Utbetaling.name).path("fagsystemId").asText()
        sendAnnullering(fagsystemId)
        sendUtbetaling()
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "AVVENTER_ARBEIDSGIVERKVITTERING", "UTBETALT")
        assertUtbetalingTilstander(1, "NY", "IKKE_UTBETALT", "AVVENTER_ARBEIDSGIVERKVITTERING", "ANNULLERT")
        val annulleringsmelding = testRapid.inspektør.siste("utbetaling_annullert")

        assertEquals(UNG_PERSON_FNR_2018, annulleringsmelding.path("fødselsnummer").asText())
        assertEquals(AKTØRID, annulleringsmelding.path("aktørId").asText())
        assertEquals(ORGNUMMER, annulleringsmelding.path("organisasjonsnummer").asText())
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_UTBETALING",
            "AVSLUTTET"
        )
    }

    @Test
    fun `kan ikke utbetale på overstyrt utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendOverstyringTidslinje(listOf(ManuellOverskrivingDag(26.januar, Dagtype.Feriedag)))
        sendUtbetalingsgodkjenning(0, true)
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "FORKASTET")
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_HISTORIKK"
        )
    }

    @Test
    fun `overstyring av tidslinje fra saksbehandler fører til tilstandsendring`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendOverstyringTidslinje(listOf(ManuellOverskrivingDag(25.januar, Dagtype.Permisjonsdag), ManuellOverskrivingDag(26.januar, Dagtype.Feriedag)))
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0, true)
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "FORKASTET")
        assertUtbetalingTilstander(1, "NY", "IKKE_UTBETALT", "AVVENTER_ARBEIDSGIVERKVITTERING")
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_UTBETALING"
        )
    }

    @Test
    fun `overstyring av inntekt fra saksbehandler fører til tilstandsendring`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendInntektsmelding(
            listOf(Periode(fom = 1.januar, tom = 16.januar)),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 33000.0
        )
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendOverstyringInntekt(33000.0, 1.januar, null)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0, true)
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "FORKASTET")
        assertUtbetalingTilstander(1, "NY", "IKKE_UTBETALT", "FORKASTET")
        assertUtbetalingTilstander(2, "NY", "IKKE_UTBETALT", "AVVENTER_ARBEIDSGIVERKVITTERING")
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_UTBETALING"
        )
    }

    @Test
    fun `Inntektsmelding med opphør av naturalytelser blir kastet til infotrygd`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(
            arbeidsgiverperiode = listOf(Periode(fom = 1.januar, tom = 16.januar)),
            førsteFraværsdag = 1.januar,
            opphørAvNaturalytelser = listOf(
                OpphoerAvNaturalytelse(
                    Naturalytelse.ELEKTRONISKKOMMUNIKASJON,
                    2.januar,
                    BigDecimal(600.0)
                )
            )
        )

        assertForkastedeTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "TIL_INFOTRYGD"
        )
    }

    @Test
    fun `ignorerer teknisk feil ved simuleringer`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.TEKNISK_FEIL)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }

    @Test
    fun `replayer inntektsmeldinger hvis er i gap og venter på inntektsmelding`() {
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 20.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 20.januar, sykmeldingsgrad = 100))
        )
        sendVilkårsgrunnlag(0)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK")
    }

    @Test
    fun `Sender med vedtakFattetTidspunkt i vedtak_fattet`() {
        val vedtakFattetTidspunkt = LocalDateTime.now().plusMinutes(1)
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0, true, godkjenttidspunkt = vedtakFattetTidspunkt)
        sendUtbetaling()
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_UTBETALING",
            "AVSLUTTET"
        )
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "AVVENTER_ARBEIDSGIVERKVITTERING", "UTBETALT")

        assertEquals(1, testRapid.inspektør.meldinger("vedtak_fattet").size)
        assertEquals(vedtakFattetTidspunkt, testRapid.inspektør.siste("vedtak_fattet")["vedtakFattetTidspunkt"].asLocalDateTime())
    }

    @Test
    fun `Sender med vedtakFattetTidspunkt i vedtak_fattet for perioder som avsluttes uten utbetaling`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 16.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 16.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVSLUTTET_UTEN_UTBETALING",
            "AVSLUTTET_UTEN_UTBETALING"
        )

        assertEquals(0, testRapid.inspektør.meldinger("utbetaling_endret").size)
        assertEquals(1, testRapid.inspektør.meldinger("vedtak_fattet").size)
        // Sjekker på localdate fordi modellen slenger på LocalDateTime.now() ved automatisk godkjenning, og det er vanskelig å teste på.
        assertEquals(LocalDate.now(), testRapid.inspektør.siste("vedtak_fattet")["vedtakFattetTidspunkt"].asLocalDateTime().toLocalDate())
    }

    @Test
    fun `Behandler ikke melding hvis den allerede er behandlet`() {
        val hendelseRepository: HendelseRepository = mockk(relaxed = true)
        every { hendelseRepository.erBehandlet(any()) } returnsMany(listOf(false, true))

        MessageMediator(
            rapidsConnection = testRapid,
            hendelseRepository = hendelseRepository,
            hendelseMediator = TestHendelseMediator()
        )

        val (meldingId, message) = meldingsfabrikk.lagNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 25.januar, sykmeldingsgrad = 100))
        testRapid.sendTestMessage(message)
        testRapid.sendTestMessage(message)
        verify(exactly = 1) { hendelseRepository.markerSomBehandlet(eq(meldingId.toUUID())) }
        verify(exactly = 2) { hendelseRepository.erBehandlet(any()) }
    }

    @Test
    fun `Behandler melding hvis den tidligere har prøvd å behandle melding, men kræsjet`() {
        val hendelseRepository: HendelseRepository = mockk(relaxed = true)
        every { hendelseRepository.erBehandlet(any()) } returnsMany(listOf(false, false, true))

        MessageMediator(
            rapidsConnection = testRapid,
            hendelseRepository = hendelseRepository,
            hendelseMediator = TestHendelseMediator()
        )

        val meldingId = sendNySøknad(SoknadsperiodeDTO(fom = 25.januar, tom = 1.januar, sykmeldingsgrad = 100))
        verify(exactly = 0) { hendelseRepository.markerSomBehandlet(meldingId) }
        val (_, message) = meldingsfabrikk.lagNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 25.januar, sykmeldingsgrad = 100))
        val medSammeId = (jacksonObjectMapper().readTree(message) as ObjectNode).also {
            it.put("@id", meldingId.toString())
        }.toString()
        testRapid.sendTestMessage(medSammeId)
        testRapid.sendTestMessage(medSammeId)
        verify(exactly = 1) { hendelseRepository.markerSomBehandlet(meldingId) }
        verify(exactly = 1) { hendelseRepository.markerSomBehandlet(meldingId) }
        verify(exactly = 3) { hendelseRepository.erBehandlet(any()) }
    }

    @Test
    fun `InntektsmeldingReplay blir ikke stoppet av duplikatsjekk`() {
        val hendelseRepository: HendelseRepository = mockk(relaxed = true)
        MessageMediator(
            rapidsConnection = testRapid,
            hendelseRepository = hendelseRepository,
            hendelseMediator = TestHendelseMediator()
        )

        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )

        val (meldingId, inntektsmelding) = sendInntektsmelding(
            listOf(Periode(fom = 1.januar, tom = 16.januar)),
            førsteFraværsdag = 1.januar
        )
        verify(exactly = 1) { hendelseRepository.markerSomBehandlet(meldingId) }
        sendInntektsmeldingReplay(
            vedtaksperiodeIndeks = 0,
            inntektsmelding = inntektsmelding
        )
        verify(exactly = 2) { hendelseRepository.markerSomBehandlet(meldingId) }
    }

    @Test
    fun `delvis refusjon`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(
            listOf(Periode(fom = 3.januar, tom = 18.januar)),
            førsteFraværsdag = 3.januar,
            opphørsdatoForRefusjon = 20.januar
        )
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK, forventedeFagområder = setOf("SPREF", "SP"))
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        assertUtbetalingTilstander(0, "NY", "IKKE_UTBETALT", "AVVENTER_KVITTERINGER", "AVVENTER_ARBEIDSGIVERKVITTERING", "UTBETALT")
        assertEquals(3, testRapid.inspektør.alleEtterspurteBehov(Utbetaling).size)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "TIL_UTBETALING",
            "AVSLUTTET"
        )
    }

    @Test
    fun `vedtaksperiode endret`() {
        sendNySøknad(SoknadsperiodeDTO(1.januar, 31.januar, 100))
        sendSøknad(perioder = listOf(SoknadsperiodeDTO(1.januar, 31.januar, 100)))
        val vedtaksperiodeEndret = testRapid.inspektør.siste("vedtaksperiode_endret")
        assertEquals(1.januar, LocalDate.parse(vedtaksperiodeEndret.path("fom").asText()))
        assertEquals(31.januar, LocalDate.parse(vedtaksperiodeEndret.path("tom").asText()))
    }
}
