package no.nav.helse.spleis.e2e.inntektsmelding

import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TrengerInntektsmeldingTest : AbstractEndToEndTest() {

    @Test
    fun `Kort periode ber om inntektsmelding når den går tilbake, og sier fra om at inntektsmelding ikke trengs etter at den er mottatt`() {
        nyPeriode(5.januar til 17.januar)
        nyPeriode(20.januar til 22.januar)

        assertTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(2, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertEquals(2, observatør.manglendeInntektsmeldingVedtaksperioder.size)

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 20.januar)
        assertEquals(4, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)
    }


    @Test
    fun `Sender ut trenger_ikke_inntektsmelding i revurdering dersom vi har fått inntektsmeldingen vi trenger`() {
        nyPeriode(1.januar til 31.januar)
        håndterInntektsmelding(listOf(5.januar til 20.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(2, observatør.manglendeInntektsmeldingVedtaksperioder.size)
        assertEquals(2, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)
    }
}