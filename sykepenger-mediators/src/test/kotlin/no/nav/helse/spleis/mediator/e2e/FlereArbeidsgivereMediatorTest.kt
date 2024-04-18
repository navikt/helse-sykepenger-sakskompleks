package no.nav.helse.spleis.mediator.e2e

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.person.TilstandType
import no.nav.helse.spleis.mediator.TestMessageFactory
import no.nav.helse.spleis.mediator.TestMessageFactory.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FlereArbeidsgivereMediatorTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `sender riktig orgnummer i trenger_ikke_inntektsmelding for arbeidsgiveren som venter på inntektsmelding`() {
        val a1 = "arbeidsgiver 1"
        val a2 = "arbeidsgiver 2"
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100), orgnummer = a1)
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            orgnummer = a1
        )
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100), orgnummer = a2)
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            andreInntektskilder = listOf(InntektskildeDTO(InntektskildetypeDTO.FOSTERHJEMGODTGJORELSE)),
            sendtNav = LocalDateTime.MAX,
            orgnummer = a2
        )

        val melding = testRapid.inspektør.meldinger("trenger_ikke_inntektsmelding")
        val orgnumre = melding.map { it["organisasjonsnummer"].asText() }.sorted()
        val vedtaksperiodeIder = melding.map { UUID.fromString(it["vedtaksperiodeId"].asText()) }.sorted()

        assertEquals(listOf("arbeidsgiver 1"), orgnumre)
        assertEquals(listOf(testRapid.inspektør.vedtaksperiodeId(0)), vedtaksperiodeIder)
    }

    @Test
    fun `sender riktig orgnummer i trenger_ikke_inntektsmelding for alle arbeidsgivere som venter på inntektsmelding`() {
        val a1 = "arbeidsgiver 1"
        val a2 = "arbeidsgiver 2"
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100), orgnummer = a1)
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            orgnummer = a1
        )
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100), orgnummer = a2)
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            orgnummer = a2
        )
        sendNyPåminnelse(
            vedtaksperiodeIndeks = 0,
            orgnummer = a1,
            tilstandsendringstidspunkt = LocalDateTime.MIN,
            tilstandType = TilstandType.AVVENTER_INNTEKTSMELDING
        )

        val melding = testRapid.inspektør.meldinger("trenger_ikke_inntektsmelding")
        val orgnumre = melding.map { it["organisasjonsnummer"].asText() }.sorted()
        val vedtaksperiodeIder = melding.map { UUID.fromString(it["vedtaksperiodeId"].asText()) }.sorted()

        assertEquals(listOf(testRapid.inspektør.vedtaksperiodeId(0), testRapid.inspektør.vedtaksperiodeId(1)).sorted(), vedtaksperiodeIder)
        assertEquals(listOf("arbeidsgiver 1", "arbeidsgiver 2").sorted(), orgnumre)
    }

    @Test
    fun `overstyring av arbeidsforhold fører til tilstandsendring`() {
        val a1 = "ag1"
        val a2 = "ag2"
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100), orgnummer = a1)
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)),
            orgnummer = a1
        )
        sendInntektsmelding(listOf(Periode(1.januar, 16.januar)), 1.januar, orgnummer = a1)
        sendVilkårsgrunnlag(
            vedtaksperiodeIndeks = 0,
            skjæringstidspunkt = 1.januar,
            orgnummer = a1,
            arbeidsforhold = listOf(
                TestMessageFactory.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                TestMessageFactory.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
            ),
            inntekterForSykepengegrunnlag = sykepengegrunnlag(
                1.januar, listOf(
                    TestMessageFactory.InntekterForSykepengegrunnlagFraLøsning.Inntekt(INNTEKT, a1),
                    TestMessageFactory.InntekterForSykepengegrunnlagFraLøsning.Inntekt(1000.0, a2),
                )
            )
        )
        sendYtelser(0, orgnummer = a1)
        sendSimulering(0, orgnummer = a1, status = SimuleringMessage.Simuleringstatus.OK)
        sendOverstyringArbeidsforhold(1.januar, listOf(
            TestMessageFactory.ArbeidsforholdOverstyrt(
            a2,
            true,
            "forklaring"
        )))
        sendYtelser(0, orgnummer = a1)
        sendSimulering(0, orgnummer = a1, status = SimuleringMessage.Simuleringstatus.OK)
        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING",
            "AVVENTER_GODKJENNING"
        )
    }
}
