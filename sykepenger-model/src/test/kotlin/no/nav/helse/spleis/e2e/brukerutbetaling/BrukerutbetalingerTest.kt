package no.nav.helse.spleis.e2e.brukerutbetaling

import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class BrukerutbetalingerTest : AbstractEndToEndTest() {

    @Test
    fun `utbetaling med 0 refusjon til arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(0.månedlig, null),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = ORGNUMMER)
    }

    @Test
    fun `utbetaling med delvis refusjon til arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(20000.månedlig, null),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser()

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = ORGNUMMER)
    }

    @Test
    fun `utbetaling med full refusjon til arbeidsgiver`() {
        nyttVedtak(1.januar til 31.januar)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = ORGNUMMER)
    }


    @Test
    fun `utbetaling med delvis refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(INNTEKT /2, null),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser()
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = ORGNUMMER)
    }
}
