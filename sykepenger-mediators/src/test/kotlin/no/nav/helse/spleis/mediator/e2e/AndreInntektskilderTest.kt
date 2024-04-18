package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import org.junit.jupiter.api.Test


internal class AndreInntektskilderTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `andre arbeidsforhold - har jobbet siste 14 dager`() {
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 22.januar, sykmeldingsgrad = 100)),
            andreInntektskilder = listOf(
                InntektskildeDTO(
                    type = InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD,
                    sykmeldt = null
                )
            ),
            ikkeJobbetIDetSisteFraAnnetArbeidsforhold = false,
        )

        assertTilstander(0, "TIL_INFOTRYGD")
    }

    @Test
    fun `andre arbeidsforhold - har ikke jobbet siste 14 dager`() {
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 22.januar, sykmeldingsgrad = 100)),
            andreInntektskilder = listOf(
                InntektskildeDTO(
                    type = InntektskildetypeDTO.ANDRE_ARBEIDSFORHOLD,
                    sykmeldt = null
                )
            ),
            ikkeJobbetIDetSisteFraAnnetArbeidsforhold = true,
        )

        assertTilstander(0, "AVVENTER_INFOTRYGDHISTORIKK", "AVVENTER_INNTEKTSMELDING_REPLAY", "AVVENTER_INNTEKTSMELDING")
    }
}