package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.februar
import no.nav.helse.flex.sykepengesoknad.kafka.InntektFraNyttArbeidsforholdDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_5
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class InntektFraNyttArbeidsforholdTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `inntekt fra nytt arbeidsforhold med harJobbet = false`() {
        nyttVedtak(1.januar, 31.januar)

        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100)),
            inntektFraNyttArbeidsforhold = listOf(
                InntektFraNyttArbeidsforholdDTO(
                    fom = 1.februar,
                    tom = 28.februar,
                    belop = null,
                    arbeidsstedOrgnummer = "4",
                    opplysningspliktigOrgnummer = "5",
                    harJobbet = false
                )
            )

        )

        assertTilstander(1, "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE", "AVVENTER_HISTORIKK")
        assertIngenVarsler(1)
    }

    @Test
    fun `inntekt fra nytt arbeidsforhold med harJobbet = true & beløp`() {
        nyttVedtak(1.januar, 31.januar)

        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100)),
            inntektFraNyttArbeidsforhold = listOf(
                InntektFraNyttArbeidsforholdDTO(
                    fom = 1.februar,
                    tom = 28.februar,
                    belop = 1000,
                    arbeidsstedOrgnummer = "4",
                    opplysningspliktigOrgnummer = "5",
                    harJobbet = true
                )
            )

        )

        assertTilstander(1, "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE", "AVVENTER_HISTORIKK")
        assertVarsel(1, RV_SV_5)
    }

    @Test
    fun `inntekt fra nytt arbeidsforhold med harJobbet = true & inget beløp`() {
        nyttVedtak(1.januar, 31.januar)

        assertEquals(1, testRapid.inspektør.vedtaksperiodeteller)

        assertThrows<IllegalStateException> {
            sendSøknad(
                perioder = listOf(SoknadsperiodeDTO(fom = 1.februar, tom = 28.februar, sykmeldingsgrad = 100)),
                inntektFraNyttArbeidsforhold = listOf(
                    InntektFraNyttArbeidsforholdDTO(
                        fom = 1.februar,
                        tom = 28.februar,
                        belop = null,
                        arbeidsstedOrgnummer = "4",
                        opplysningspliktigOrgnummer = "5",
                        harJobbet = true
                    )
                )
            )
        }

        // En slik type søknad skal angivelig ikke skje & den tryner på en exception
        assertEquals(1, testRapid.inspektør.vedtaksperiodeteller)
    }
}
