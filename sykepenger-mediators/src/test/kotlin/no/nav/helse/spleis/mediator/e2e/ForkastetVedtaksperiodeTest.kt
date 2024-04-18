package no.nav.helse.spleis.mediator.e2e

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.spleis.mediator.e2e.KontraktAssertions.assertAntallUtgåendeMeldinger
import no.nav.helse.spleis.mediator.e2e.KontraktAssertions.assertUtgåendeMelding
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class ForkastetVedtaksperiodeTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `vedtaksperiode_forkastet`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        val søknadId = sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        val søknadId2 = sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 27.januar, sykmeldingsgrad = 100))
        )

        assertTilstander(0, "AVVENTER_INFOTRYGDHISTORIKK", "AVVENTER_INNTEKTSMELDING_REPLAY", "AVVENTER_INNTEKTSMELDING", "TIL_INFOTRYGD")
        assertTilstander(1, "TIL_INFOTRYGD")

        testRapid.assertAntallUtgåendeMeldinger("vedtaksperiode_forkastet", 2)

        @Language("JSON")
        val forventet1 = """
            {
              "@event_name": "vedtaksperiode_forkastet",
              "aktørId": "$AKTØRID",
              "fødselsnummer": "$UNG_PERSON_FNR_2018",
              "organisasjonsnummer": "$ORGNUMMER",
              "tilstand" : "AVVENTER_INNTEKTSMELDING",
              "fom" : "2018-01-03",
              "tom" : "2018-01-26",
              "behandletIInfotrygd": false,
              "forlengerPeriode" : false,
              "harPeriodeInnenfor16Dager" : false,
              "trengerArbeidsgiveropplysninger": true,
              "hendelser": ["$søknadId"],
              "sykmeldingsperioder": [{"fom": "2018-01-03", "tom": "2018-01-26"}],
              "vedtaksperiodeId": "<uuid>"
            }
        """
        assertVedtaksperiodeForkastet(forventet1, 0)
        @Language("JSON")
        val forventet2 = """
            {
              "@event_name": "vedtaksperiode_forkastet",
              "aktørId": "$AKTØRID",
              "fødselsnummer": "$UNG_PERSON_FNR_2018",
              "organisasjonsnummer": "$ORGNUMMER",
              "tilstand" : "START",
              "fom" : "2018-01-03",
              "tom" : "2018-01-27",
              "behandletIInfotrygd": false,
              "forlengerPeriode" : false,
              "harPeriodeInnenfor16Dager" : false,
              "trengerArbeidsgiveropplysninger": true,
              "hendelser": ["$søknadId2"],
              "sykmeldingsperioder": [{"fom": "2018-01-03", "tom": "2018-01-26"}, {"fom": "2018-01-03", "tom": "2018-01-27"}],
              "vedtaksperiodeId": "<uuid>"
            }
        """
        assertVedtaksperiodeForkastet(forventet2, 1)
    }

    @Test
    fun `historiskeFolkeregisteridenter test`() {
        val historiskFnr = "123"
        val nyttFnr = "111"
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100), fnr = historiskFnr)
        sendSøknad(
            historiskFnr,
            listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            historiskeFolkeregisteridenter = emptyList()
        )

        sendNySøknad(SoknadsperiodeDTO(fom = 1.mars, tom = 31.mars, sykmeldingsgrad = 100), fnr = nyttFnr)
        val søknadId2 = sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.mars, tom = 31.mars, sykmeldingsgrad = 100)),
            historiskeFolkeregisteridenter = listOf(historiskFnr),
            fnr = nyttFnr
        )

        testRapid.assertAntallUtgåendeMeldinger("vedtaksperiode_forkastet", 1)

        @Language("JSON")
        val forventet = """
            {
              "@event_name": "vedtaksperiode_forkastet",
              "aktørId": "$AKTØRID",
              "fødselsnummer": "111",
              "organisasjonsnummer": "$ORGNUMMER",
              "tilstand" : "START",
              "fom" : "2018-03-01",
              "tom" : "2018-03-31",
              "behandletIInfotrygd": false,
              "forlengerPeriode" : false,
              "harPeriodeInnenfor16Dager" : false,
              "trengerArbeidsgiveropplysninger": true,  
              "hendelser": ["$søknadId2"],
              "sykmeldingsperioder": [{"fom": "2018-03-01", "tom": "2018-03-31"}],
              "vedtaksperiodeId": "<uuid>"
            }
        """
        assertVedtaksperiodeForkastet(forventet, 0)
    }

    @Test
    fun `vedtaksperide_forkastet sender med flagg om den forkastede vedtaksperiode skal be om arbeidsgiveropplysninger`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        val søknadId1 = sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            sendTilGosys = true
        )

        sendNySøknad(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100))
        val søknadId2 = sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100)),
            sendTilGosys = true
        )

        testRapid.assertAntallUtgåendeMeldinger("vedtaksperiode_forkastet", 2)

        assertVedtaksperiodeForkastet(forventet(søknadId1, 1.januar, 31.januar, true), 0)
        assertVedtaksperiodeForkastet(
            forventet(
                søknadId2,
                1.februar,
                28.februar,
                false,
                listOf(1.januar til 31.januar, 1.februar til 28.februar)
            ), 1
        )
    }

    @Language("JSON")
    private fun forventet(
        søknadId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        trengerArbeidsgiveropplysninger: Boolean,
        sykmeldingsperioder: List<Periode> = listOf(fom til tom)
    ) = """
            {
              "@event_name": "vedtaksperiode_forkastet",
              "aktørId": "$AKTØRID",
              "fødselsnummer": "$UNG_PERSON_FNR_2018",
              "organisasjonsnummer": "$ORGNUMMER",
              "tilstand" : "START",
              "fom" : "$fom",
              "tom" : "$tom",
              "behandletIInfotrygd": false,
              "forlengerPeriode" : false,
              "harPeriodeInnenfor16Dager" : false,
              "trengerArbeidsgiveropplysninger": $trengerArbeidsgiveropplysninger, 
              "hendelser": ["$søknadId"], 
              "sykmeldingsperioder": [${sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
              "vedtaksperiodeId": "<uuid>"
            }
        """

    private fun assertVedtaksperiodeForkastet(forventetMelding: String, index: Int) =
        testRapid.assertUtgåendeMelding(forventetMelding, faktiskMelding = { it[index] })
}

private fun Periode.hardcodedJson(): String =
    """
    {
        "fom": "$start",
        "tom": "$endInclusive"
    }
    """
