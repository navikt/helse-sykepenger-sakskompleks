package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.spleis.mediator.TestMessageFactory.SkjønnsmessigFastsatt
import no.nav.helse.spleis.meldinger.model.SimuleringMessage.Simuleringstatus.OK
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Test

internal class SkjønnsmessigFastsettelseMediatorTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `skjønnsmessig fastsettelse uten endring i beløp`() {
        vedtaOgSkjønnsmessigFastsett(INNTEKT * 12)
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
            "TIL_UTBETALING",
            "AVSLUTTET",
            "AVVENTER_REVURDERING",
            "AVVENTER_HISTORIKK_REVURDERING",
            "AVVENTER_GODKJENNING_REVURDERING" // Ingenting å simulere
        )
    }

    @Test
    fun `skjønnsmessig fastsettelse med endring i beløp`() {
        vedtaOgSkjønnsmessigFastsett((INNTEKT * 12) * 1.25)
        sendSimulering(0, forventedeFagområder = setOf("SPREF", "SP"), status = OK)
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
            "TIL_UTBETALING",
            "AVSLUTTET",
            "AVVENTER_REVURDERING",
            "AVVENTER_HISTORIKK_REVURDERING",
            "AVVENTER_SIMULERING_REVURDERING", // Her må det simuleres
            "AVVENTER_GODKJENNING_REVURDERING"
        )
    }

    private fun vedtaOgSkjønnsmessigFastsett(årlig: Double) {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100)))
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        sendSkjønnsmessigFastsettelse(
            skjæringstidspunkt = 1.januar,
            skjønnsmessigFastsatt = listOf(
                SkjønnsmessigFastsatt(
                    organisasjonsnummer = ORGNUMMER,
                    årlig = årlig
                )
            )
        )
        sendYtelser(0)
    }
}