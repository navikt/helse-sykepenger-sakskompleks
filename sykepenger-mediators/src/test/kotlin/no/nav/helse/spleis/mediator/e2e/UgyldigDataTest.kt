package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UgyldigDataTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `ny søknad - tom er før fom`() {
        assertThrows<IllegalArgumentException> {
            sendNySøknad(SoknadsperiodeDTO(fom = 2.januar, tom = 1.januar, sykmeldingsgrad = 100)).let {  }
        }
    }

    @Test
    fun `sendt søknad - tom er før fom`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 2.januar, sykmeldingsgrad = 100))
        assertThrows<IllegalArgumentException> {
            sendSøknad(
                perioder = listOf(SoknadsperiodeDTO(fom = 2.januar, tom = 1.januar, sykmeldingsgrad = 100))
            )
        }
    }
}
