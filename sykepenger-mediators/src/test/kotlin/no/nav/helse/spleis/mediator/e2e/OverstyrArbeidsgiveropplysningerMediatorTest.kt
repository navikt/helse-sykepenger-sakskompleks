package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.spleis.mediator.TestMessageFactory.Arbeidsgiveropplysning
import no.nav.helse.spleis.mediator.TestMessageFactory.Refusjonsopplysning
import no.nav.helse.spleis.meldinger.model.SimuleringMessage.Simuleringstatus.OK
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Test

internal class OverstyrArbeidsgiveropplysningerMediatorTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `overstyrer både inntekt og refusjon`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 1.januar, tom = 31.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)
        sendSimulering(0, OK)
        sendUtbetalingsgodkjenning(0)
        sendUtbetaling()
        sendOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(Arbeidsgiveropplysning(
                organisasjonsnummer = ORGNUMMER,
                månedligInntekt = INNTEKT *1.25,
                forklaring = "forklaring",
                subsumsjon = null,
                refusjonsopplysninger = listOf(Refusjonsopplysning(
                    fom = 1.januar,
                    tom = null,
                    beløp = INNTEKT *1.25
                ))
            ))
        )
        sendYtelser(0)
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
            "AVVENTER_SIMULERING_REVURDERING"
        )
    }
}