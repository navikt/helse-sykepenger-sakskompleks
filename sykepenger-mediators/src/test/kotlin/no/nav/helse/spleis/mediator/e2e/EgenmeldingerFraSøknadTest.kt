package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import org.junit.jupiter.api.Test


internal class EgenmeldingerFraSøknadTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `Egenmeldingsdager på begge sider av helg - skal telle helgedager som arbeidsgiverperiode`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 9.januar, tom = 22.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 9.januar, tom = 22.januar, sykmeldingsgrad = 100)),
            egenmeldingerFraSykmelding = listOf(5.januar, 8.januar),
        )

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING"
        )
    }
}