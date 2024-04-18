package no.nav.helse.spleis.mediator.e2e

import java.time.YearMonth
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.spleis.mediator.TestMessageFactory.InntekterForSykepengegrunnlagFraLøsning
import no.nav.helse.spleis.mediator.TestMessageFactory.InntekterForSykepengegrunnlagFraLøsning.Arbeidsforhold
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Test


internal class FrilanserTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `Person med frilanserinntekt i løpet av de siste 3 månedene sendes til infotrygd`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(
            vedtaksperiodeIndeks = 0,
            inntekterForSykepengegrunnlag = (10..12).map {
                InntekterForSykepengegrunnlagFraLøsning(
                    måned = YearMonth.of(2017, it),
                    inntekter = listOf(
                        InntekterForSykepengegrunnlagFraLøsning.Inntekt(INNTEKT, ORGNUMMER)
                    ),
                    arbeidsforhold = listOf(Arbeidsforhold(orgnummer = ORGNUMMER, type = "frilanserOppdragstakerHonorarPersonerMm"))
                )
            }
        )
        assertTilstander(0, "AVVENTER_INFOTRYGDHISTORIKK", "AVVENTER_INNTEKTSMELDING_REPLAY", "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE", "AVVENTER_VILKÅRSPRØVING", "TIL_INFOTRYGD")
    }

    @Test
    fun `frilansersøknad`() {
        sendNySøknadFrilanser(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendFrilanssøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        assertTilstander(0, "TIL_INFOTRYGD")
    }
}
