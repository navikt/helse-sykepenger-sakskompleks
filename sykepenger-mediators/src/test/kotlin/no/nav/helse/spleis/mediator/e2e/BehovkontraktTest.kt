package no.nav.helse.spleis.mediator.e2e

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.*
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder.Fastsatt.EtterHovedregel
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder.Fastsatt.EtterSkjønn
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder.Fastsatt.IInfotrygd
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class BehovkontraktTest : AbstractEndToEndMediatorTest() {

    @Test
    fun vilkårsgrunnlag() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        val behov = testRapid.inspektør.melding(testRapid.inspektør.antall() - 1)
        assertVedtaksperiodeBehov(
            behov,
            Dagpenger,
            InntekterForSykepengegrunnlag,
            Medlemskap,
            ArbeidsforholdV2
        )
        assertMedlemskapdetaljer(behov)
        assertInntekterForSykepengegrunnlagdetaljer(behov)
        assertArbeidsforholdV2detaljer(behov)
    }

    @Test
    fun ytelser() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        val utbetalinghistorikkbehov = testRapid.inspektør.meldinger("behov").last()
        assertVedtaksperiodeBehov(utbetalinghistorikkbehov, Sykepengehistorikk)
        assertSykepengehistorikkdetaljer(utbetalinghistorikkbehov)
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        val behov = testRapid.inspektør.melding(testRapid.inspektør.antall() - 1)
        assertVedtaksperiodeBehov(
            behov,
            Arbeidsavklaringspenger,
            Dagpenger,
            Foreldrepenger,
            Institusjonsopphold,
            Omsorgspenger,
            Opplæringspenger,
            Pleiepenger
        )
        assertArbeidsavklaringspengerdetaljer(behov)
        assertDagpengerdetaljer(behov)
        assertForeldrepengerdetaljer(behov)
        assertInstitusjonsoppholddetaljer(behov)
        assertOmsorgspengerdetaljer(behov)
        assertOpplæringspengerdetaljer(behov)
        assertPleiepengerdetaljer(behov)
    }

    @Test
    fun simulering() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        val behov = testRapid.inspektør.melding(testRapid.inspektør.antall() - 1)
        assertVedtaksperiodeBehov(behov, Simulering)
        assertSimuleringdetaljer(behov)
    }

    @Test
    fun godkjenning() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        val behov = testRapid.inspektør.melding(testRapid.inspektør.antall() - 1)
        assertVedtaksperiodeBehov(behov, Godkjenning)
        assertGodkjenningdetaljer(behov)
    }

    @Test
    fun utbetaling() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, SimuleringMessage.Simuleringstatus.OK)
        sendUtbetalingsgodkjenning(0)
        val behov = testRapid.inspektør.melding(testRapid.inspektør.antall() - 1)
        assertUtbetalingBehov(behov, Utbetaling)
        assertUtbetalingdetaljer(behov)
    }

    @Test
    fun annullering() {
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
        sendAnnullering(testRapid.inspektør.etterspurteBehov(Utbetaling).path(Utbetaling.name).path("fagsystemId").asText())
        val behov = testRapid.inspektør.melding(testRapid.inspektør.antall() - 1)
        assertUtbetalingBehov(behov, Utbetaling)
        assertUtbetalingdetaljer(behov, true)
    }

    private fun assertVedtaksperiodeBehov(behov: JsonNode, vararg typer: Aktivitet.Behov.Behovtype) {
        assertBehov(behov, *typer)
        assertTrue(behov.path("vedtaksperiodeId").asText().isNotEmpty())
    }

    private fun assertUtbetalingBehov(behov: JsonNode, vararg typer: Aktivitet.Behov.Behovtype) {
        assertBehov(behov, *typer)
        assertTrue(behov.path("utbetalingId").asText().isNotEmpty())
    }

    private fun assertBehov(behov: JsonNode, vararg typer: Aktivitet.Behov.Behovtype) {
        val id = behov.path("@id").asText()
        assertEquals("behov", behov.path("@event_name").asText())
        assertTrue(behov.path("fødselsnummer").asText().isNotEmpty())
        assertTrue(behov.path("aktørId").asText().isNotEmpty())
        assertTrue(behov.path("organisasjonsnummer").asText().isNotEmpty())
        assertTrue(behov.path("@behov").isArray)
        assertDatotid(behov.path("@opprettet").asText())
        assertTrue(id.isNotEmpty())
        assertDoesNotThrow { UUID.fromString(id) }
        assertTrue(typer.map(Enum<*>::name).containsAll(behov.path("@behov").map(JsonNode::asText)))
    }

    private fun assertArbeidsavklaringspengerdetaljer(behov: JsonNode) {
        assertDato(behov.path(Arbeidsavklaringspenger.name).path("periodeFom").asText())
        assertDato(behov.path(Arbeidsavklaringspenger.name).path("periodeTom").asText())
    }

    private fun assertDagpengerdetaljer(behov: JsonNode) {
        assertDato(behov.path(Dagpenger.name).path("periodeFom").asText())
        assertDato(behov.path(Dagpenger.name).path("periodeTom").asText())
    }

    private fun assertInntekterForSykepengegrunnlagdetaljer(behov: JsonNode) {
        assertDato(behov.path(InntekterForSykepengegrunnlag.name).path("skjæringstidspunkt").asText())
        assertÅrMåned(behov.path(InntekterForSykepengegrunnlag.name).path("beregningStart").asText())
        assertÅrMåned(behov.path(InntekterForSykepengegrunnlag.name).path("beregningSlutt").asText())
    }


    private fun assertMedlemskapdetaljer(behov: JsonNode) {
        assertDato(behov.path(Medlemskap.name).path("skjæringstidspunkt").asText())
        assertDato(behov.path(Medlemskap.name).path("medlemskapPeriodeFom").asText())
        assertDato(behov.path(Medlemskap.name).path("medlemskapPeriodeTom").asText())
    }

    private fun assertArbeidsforholdV2detaljer(behov: JsonNode) {
        assertDato(behov.path(ArbeidsforholdV2.name).path("skjæringstidspunkt").asText())
    }

    private fun assertForeldrepengerdetaljer(behov: JsonNode) {
        assertDato(behov.path(Foreldrepenger.name).path("foreldrepengerFom").asText())
        assertDato(behov.path(Foreldrepenger.name).path("foreldrepengerTom").asText())
    }

    private fun assertInstitusjonsoppholddetaljer(behov: JsonNode) {
        assertDato(behov.path(Institusjonsopphold.name).path("institusjonsoppholdFom").asText())
        assertDato(behov.path(Institusjonsopphold.name).path("institusjonsoppholdTom").asText())
    }

    private fun assertOmsorgspengerdetaljer(behov: JsonNode) {
        assertDato(behov.path(Omsorgspenger.name).path("omsorgspengerFom").asText())
        assertDato(behov.path(Omsorgspenger.name).path("omsorgspengerTom").asText())
    }

    private fun assertOpplæringspengerdetaljer(behov: JsonNode) {
        assertDato(behov.path(Opplæringspenger.name).path("opplæringspengerFom").asText())
        assertDato(behov.path(Opplæringspenger.name).path("opplæringspengerTom").asText())
    }

    private fun assertPleiepengerdetaljer(behov: JsonNode) {
        assertDato(behov.path(Pleiepenger.name).path("pleiepengerFom").asText())
        assertDato(behov.path(Pleiepenger.name).path("pleiepengerTom").asText())
    }

    private fun assertSykepengehistorikkdetaljer(behov: JsonNode) {
        assertDato(behov.path(Sykepengehistorikk.name).path("historikkFom").asText())
        assertDato(behov.path(Sykepengehistorikk.name).path("historikkTom").asText())
    }

    private fun assertSimuleringdetaljer(behov: JsonNode) {
        val simulering = behov.path(Simulering.name)
        assertTrue(behov.path("utbetalingId").asText().isNotEmpty())
        assertDato(simulering.path("maksdato").asText())
        assertTrue(simulering.path("saksbehandler").asText().isNotEmpty())
        assertOppdragdetaljer(simulering, false)
    }

    private fun assertGodkjenningdetaljer(behov: JsonNode) {
        val godkjenning = behov.path(Godkjenning.name)
        assertTrue(behov.path("utbetalingId").asText().isNotEmpty())
        assertDato(godkjenning.path("periodeFom").asText())
        assertDato(godkjenning.path("periodeTom").asText())
        assertDato(godkjenning.path("skjæringstidspunkt").asText())
        assertTrue(godkjenning.path("periodetype").asText().isNotEmpty())
        assertTrue(godkjenning.path("vilkårsgrunnlagId").asText().isNotEmpty())
        assertTrue(godkjenning.path("førstegangsbehandling").isBoolean)
        assertTrue(godkjenning.path("utbetalingtype").asText().isNotEmpty())
        assertTrue(godkjenning.path("inntektskilde").asText().isNotEmpty())
        assertTrue(godkjenning.path("orgnummereMedRelevanteArbeidsforhold").isArray)
        assertFalse(godkjenning.path("orgnummereMedRelevanteArbeidsforhold").isEmpty)
        assertTrue(godkjenning.path("tags").isArray)
        assertTrue(godkjenning.path("kanAvvises").isBoolean)
        assertTrue(godkjenning.path("omregnedeÅrsinntekter").isArray)
        assertFalse(godkjenning.path("omregnedeÅrsinntekter").isEmpty)
        assertTrue(godkjenning.path("omregnedeÅrsinntekter").path(0).path("organisasjonsnummer").asText().isNotEmpty())
        assertTrue(godkjenning.path("omregnedeÅrsinntekter").path(0).path("beløp").isDouble)
        assertTrue(godkjenning.path("behandlingId").asText().isNotEmpty())
        assertTrue(godkjenning.path("perioderMedSammeSkjæringstidspunkt").isArray)
        assertFalse(godkjenning.path("perioderMedSammeSkjæringstidspunkt").isEmpty)
        godkjenning.path("perioderMedSammeSkjæringstidspunkt").path(0).also {
            assertTrue(it.path("vedtaksperiodeId").asText().isNotEmpty())
            assertTrue(it.path("behandlingId").asText().isNotEmpty())
            assertDato(it.path("fom").asText())
            assertDato(it.path("tom").asText())
        }
        godkjenning.path("sykepengegrunnlagsfakta").also {
            it.path("fastsatt").asText() in listOf(EtterSkjønn.name, EtterHovedregel.name, IInfotrygd.name)
            assertTrue(it.path("omregnetÅrsinntektTotalt").isDouble)
            assertTrue(it.path("6G").isDouble)
            assertTrue(it.path("skjønnsfastsatt").isMissingNode)
            assertTrue(it.path("arbeidsgivere").isArray)
            it.path("arbeidsgivere").path(0)?.also { arbeidsgiver ->
                assertTrue(arbeidsgiver.path("arbeidsgiver").isTextual)
                assertTrue(arbeidsgiver.path("omregnetÅrsinntekt").isDouble)
                assertTrue(arbeidsgiver.path("skjønnsfastsatt").isMissingNode)
            }
        }
    }

    private fun assertUtbetalingdetaljer(behov: JsonNode, erAnnullering: Boolean = false) {
        val utbetaling = behov.path(Utbetaling.name)
        if (!erAnnullering) assertDato(utbetaling.path("maksdato").asText())
        assertTrue(utbetaling.path("saksbehandler").asText().isNotEmpty())
        assertOppdragdetaljer(utbetaling, erAnnullering)
    }

    private fun assertOppdragdetaljer(oppdrag: JsonNode, erAnnullering: Boolean) {
        assertTrue(oppdrag.path("mottaker").asText().isNotEmpty())
        assertTrue(oppdrag.path("fagsystemId").asText().isNotEmpty())
        assertTrue(oppdrag.path("fagområde").asText().isNotEmpty())
        assertTrue(oppdrag.path("endringskode").asText().isNotEmpty())
        val linjer = oppdrag.path("linjer")
        assertTrue(linjer.isArray)
        linjer.forEach { linje ->
            assertDato(linje.path("fom").asText())
            assertDato(linje.path("tom").asText())
            assertTrue(linje.path("sats").isInt)
            assertTrue(linje.path("grad").isDouble)
            assertTrue(linje.path("delytelseId").isInt)
            assertTrue(linje.has("refFagsystemId"))
            assertTrue(linje.has("refDelytelseId"))
            if (!erAnnullering) {
                assertTrue(linje.has("datoStatusFom"))
                assertTrue(linje.has("statuskode"))
            } else {
                assertDato(linje.path("datoStatusFom").asText())
                assertTrue(linje.path("statuskode").asText().isNotEmpty())
            }
            assertTrue(linje.path("endringskode").asText().isNotEmpty())
            assertTrue(linje.path("klassekode").asText().isNotEmpty())
        }
    }

    private fun assertÅrMåned(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { YearMonth.parse(tekst) }
    }

    private fun assertDato(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { LocalDate.parse(tekst) }
    }

    private fun assertDatotid(tekst: String) {
        assertTrue(tekst.isNotEmpty())
        assertDoesNotThrow { LocalDateTime.parse(tekst) }
    }
}


