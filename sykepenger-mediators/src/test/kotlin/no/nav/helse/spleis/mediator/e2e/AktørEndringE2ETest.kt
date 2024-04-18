package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.februar
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.spleis.mediator.TestMessageFactory
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class AktørEndringE2ETest : AbstractEndToEndMediatorTest() {
    private companion object {
        private const val FNR1 = "12029240045"
        private const val FNR2 = "12029277777"
    }

    @Test
    fun `person får nytt fnr - behandling fortsetter på samme personjson`() {
        val meldingsfabrikkFNR2 = TestMessageFactory(FNR2, AKTØRID, ORGNUMMER, INNTEKT, UNG_PERSON_FØDSELSDATO)
        sendSøknad(fnr = FNR1, perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendSøknad(fnr = FNR2, perioder = listOf(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100)), historiskeFolkeregisteridenter = listOf(FNR1))
        sendSøknad(fnr = FNR2, perioder = listOf(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100)), historiskeFolkeregisteridenter = listOf(FNR1))

        meldingsfabrikkFNR2.lagInnteksmelding(listOf(Periode(3.januar, 18.januar)), 3.januar).also { (_, melding) ->
            testRapid.sendTestMessage(melding)
        }

        assertEquals(1, antallPersoner())
        assertEquals(2, antallPersonalias())
        val meldinger = testRapid.inspektør.meldinger("vedtaksperiode_endret")
        assertEquals(11, meldinger.size)
        assertEquals(FNR1, meldinger[0].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[1].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[2].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[3].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[4].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[5].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[6].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[7].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[8].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[9].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[10].path("fødselsnummer").asText())

        assertTilstander(0, "AVVENTER_INFOTRYGDHISTORIKK", "AVVENTER_INNTEKTSMELDING_REPLAY", "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE", "AVVENTER_VILKÅRSPRØVING")
        assertTilstander(1, "AVVENTER_INNTEKTSMELDING_REPLAY", "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE")
    }

    @Test
    fun `to ulike personer - finner sammenheng etterpå`() {
        sendSøknad(fnr = FNR1, perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendSøknad(fnr = FNR2, perioder = listOf(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100)))

        sendSøknad(fnr = FNR2, perioder = listOf(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100)), historiskeFolkeregisteridenter = listOf(FNR1))

        assertEquals(2, antallPersoner())
        assertEquals(2, antallPersonalias())
        assertEquals(1, antallPersonalias(FNR1))
        assertEquals(1, antallPersonalias(FNR2))
        val meldinger = testRapid.inspektør.meldinger("vedtaksperiode_endret")
        assertEquals(5, meldinger.size)
        assertEquals(FNR1, meldinger[0].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[1].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[2].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[3].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[4].path("fødselsnummer").asText())

        testRapid.inspektør.siste("vedtaksperiode_forkastet").also { melding ->
            assertEquals(FNR2, melding.path("fødselsnummer").asText())
        }

        testRapid.inspektør.siste("aktivitetslogg_ny_aktivitet").also { melding ->
            val error = melding.path("aktiviteter").first { it.path("melding").asText() == "Personen har blitt behandlet på en tidligere ident" }
            assertEquals("FUNKSJONELL_FEIL", error.path("nivå").asText())
        }
    }

    @Test
    fun `endrer fødselsnummer ved opphørt ident`() {
        sendSøknad(fnr = FNR1, perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendIdentOpphørt(FNR1, FNR2)
        assertEquals(1, antallPersoner())
        assertEquals(2, antallPersonalias())
        sendSøknad(fnr = FNR2, perioder = listOf(SoknadsperiodeDTO(fom = 27.januar, tom = 31.januar, sykmeldingsgrad = 100)))
        val meldinger = testRapid.inspektør.meldinger("vedtaksperiode_endret")
        assertEquals(5, meldinger.size)
        assertEquals(FNR1, meldinger[0].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[1].path("fødselsnummer").asText())
        assertEquals(FNR1, meldinger[2].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[3].path("fødselsnummer").asText())
        assertEquals(FNR2, meldinger[4].path("fødselsnummer").asText())
    }
}
