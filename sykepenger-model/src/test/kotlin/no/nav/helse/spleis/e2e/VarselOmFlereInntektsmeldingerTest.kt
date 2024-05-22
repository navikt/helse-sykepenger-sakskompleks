package no.nav.helse.spleis.e2e

import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.personLogg
import no.nav.helse.mars
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VarselOmFlereInntektsmeldingerTest : AbstractEndToEndTest() {

    @Test
    fun `Prodbug - Feilaktig varsel om flere inntektsmeldinger`() {
        håndterSykmelding(Sykmeldingsperiode(22.mars(2021), 28.mars(2021)))
        håndterSøknad(Sykdom(22.mars(2021), 28.mars(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(29.mars(2021), 5.april(2021)))
        håndterSøknad(Sykdom(29.mars(2021), 5.april(2021), 100.prosent))
        håndterSøknad(Sykdom(29.mars(2021), 5.april(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(6.april(2021), 16.april(2021)))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(22.mars(2021) til 6.april(2021)),
            førsteFraværsdag = 22.mars(2021),
        )
        håndterSøknad(Sykdom(6.april(2021), 16.april(2021), 50.prosent))

        håndterVilkårsgrunnlag(vedtaksperiodeIdInnhenter = 3.vedtaksperiode, inntekt = INNTEKT)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(17.april(2021), 30.april(2021)))
        assertTrue(person.personLogg.varsel().none { w ->
            w.toString().contains("Mottatt flere inntektsmeldinger")
        })
    }

    @Test
    fun `Varsel om flere inntektsmeldinger hvis vi forlenger en avsluttet periode med inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.februar til 16.februar), førsteFraværsdag = 1.februar)
        håndterVilkårsgrunnlag(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, inntekt = INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(1.mars, 20.mars))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.mars til 16.mars), førsteFraværsdag = 1.mars)
        håndterSøknad(Sykdom(1.mars, 20.mars, 50.prosent))

        assertForventetFeil(
            forklaring = "Burde legge på warning om vi får en inntektsmelding ville ha truffet vedtaksperioden til søknaden",
            nå = {
                assertIngenVarsel(RV_IM_4, 2.vedtaksperiode.filter())
                assertIngenVarsel(RV_IM_3, 2.vedtaksperiode.filter())
                assertIngenVarsel(RV_IM_2, 2.vedtaksperiode.filter())
            },
            ønsket = {
                assertVarsel(RV_IM_4, 2.vedtaksperiode.filter())
                assertVarsel(RV_IM_3, 2.vedtaksperiode.filter())
                assertVarsel(RV_IM_2, 2.vedtaksperiode.filter())
            }
        )
    }

}
