package no.nav.helse.spleis.e2e

import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VedtaksperiodeForkastetE2ETest : AbstractEndToEndTest() {

    @Test
    fun `vedtaksperioder forkastes`() {
        nyttVedtak(1.januar til 31.januar)
        forlengVedtak(1.februar til 28.februar)
        forlengVedtak(1.mars til 31.mars)
        forlengPeriode(1.april, 30.april)
        håndterYtelser(4.vedtaksperiode)
        håndterSimulering(4.vedtaksperiode)
        håndterUtbetalingsgodkjenning(4.vedtaksperiode, false) // <- TIL_INFOTRYGD
        assertEquals(1, observatør.forkastedePerioder())
        assertEquals(AVVENTER_GODKJENNING, observatør.forkastet(4.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(4.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster kort periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 5.januar))
        håndterSøknad(Sykdom(1.januar, 5.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(6.januar, 15.januar))
        håndterSøknad(Sykdom(6.januar, 15.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(16.januar, 31.januar))
        håndterSøknad(Sykdom(16.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()
        håndterAnnullerUtbetaling(ORGNUMMER)
        assertEquals(3, observatør.forkastedePerioder())
        assertEquals(AVSLUTTET, observatør.forkastet(3.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger for søknad som forkastes pga sendTilGosys = true`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendTilGosys = true)

        assertTrue(observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 31.januar), observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forlengelse av spleis`() {
        nyttVedtak(1.januar til 31.januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), sendTilGosys = true)

        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 31.januar, 1.februar til 28.februar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forlengelse av spleis over helg`() {
        nyttVedtak(1.januar til 26.januar)
        håndterSykmelding(Sykmeldingsperiode(29.januar, 28.februar))
        håndterSøknad(Sykdom(29.januar, 28.februar, 100.prosent), sendTilGosys = true)

        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 26.januar, 29.januar til 28.februar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forlengelse forkastet periode`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), sendTilGosys = true)

        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 31.januar, 1.februar til 28.februar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forlengelse forkastet periode over helg`() {
        tilGodkjenning(1.januar, 26.januar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 28.februar))
        håndterSøknad(Sykdom(29.januar, 28.februar, 100.prosent), sendTilGosys = true)

        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 26.januar, 29.januar til 28.februar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger ved kort gap til spleis`() {
        nyttVedtak(1.januar til 31.januar)
        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent), sendTilGosys = true)

        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 31.januar, 2.februar til 28.februar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger ved kort gap til forkastet periode`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))

        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 31.januar, 2.februar til 28.februar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forkasting av AUU`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        val event = observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER))
        assertFalse(event.trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar), observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forlengelse av kort spleisperiode, ny periode er fortsatt innenfor AGP`() {
        nyPeriode(1.januar til 10.januar)

        håndterSykmelding(Sykmeldingsperiode(11.januar, 15.januar))
        håndterSøknad(Sykdom(11.januar, 15.januar, 100.prosent), sendTilGosys = true)

        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar, 11.januar til 15.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger ved forlengelse av kort spleisperiode, ny periode går utover AGP`() {
        nyPeriode(1.januar til 10.januar)

        håndterSykmelding(Sykmeldingsperiode(11.januar, 17.januar))
        håndterSøknad(Sykdom(11.januar, 17.januar, 100.prosent), sendTilGosys = true)

        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar, 11.januar til 17.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger ved forlengelse av kort forkastet periode, ny periode er fortsatt innenfor AGP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(11.januar, 15.januar))
        håndterSøknad(Sykdom(11.januar, 15.januar, 100.prosent))

        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar, 11.januar til 15.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger ved forlengelse av kort forkastet periode, ny periode går utover AGP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(11.januar, 31.januar))
        håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent))

        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar, 11.januar til 31.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger ved forlengelse av kort forkastet periode, ny periode går utover AGP, men er kortere enn 16 dager`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(11.januar, 17.januar))
        håndterSøknad(Sykdom(11.januar, 17.januar, 100.prosent))

        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar, 11.januar til 17.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeisgiveropplysninger ved kort periode med gap til kort forkastet periode, ny periode går ikke utover AGP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 5.januar))
        håndterSøknad(Sykdom(1.januar, 5.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(10.januar, 15.januar))
        håndterSøknad(Sykdom(10.januar, 15.januar, 100.prosent))
        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 5.januar, 10.januar til 15.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeisgiveropplysninger ved kort periode med gap til kort forkastet periode, ny periode går utover AGP`() {
        håndterSykmelding(Sykmeldingsperiode(1.desember(2017), 10.desember(2017)))
        håndterSøknad(Sykdom(1.desember(2017), 10.desember(2017), 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(15.januar, 25.januar))
        håndterSøknad(Sykdom(15.januar, 25.januar, 100.prosent))
        assertTrue(observatør.forkastet(3.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 10.januar, 15.januar til 25.januar), observatør.forkastet(3.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger fra kort periode når den går utover AGP pga kort periode behandlet i spleis og kort forkastet periode`() {
        nyPeriode(1.januar til 5.januar)

        håndterSykmelding(Sykmeldingsperiode(10.januar, 15.januar))
        håndterSøknad(Sykdom(10.januar, 15.januar, 100.prosent), sendTilGosys = true)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)

        håndterSykmelding(Sykmeldingsperiode(20.januar, 31.januar))
        håndterSøknad(Sykdom(20.januar, 31.januar, 100.prosent))
        assertTrue(observatør.forkastet(3.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 5.januar, 10.januar til 15.januar, 20.januar til 31.januar), observatør.forkastet(3.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger fra periode der arbeidsgiver har sendt inntektsmelding før vi mottar søknad`() {
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        nyPeriode(1.januar til 31.januar)
        person.søppelbøtte(hendelselogg) { true }
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertForventetFeil(
            forklaring = "Falsk positiv",
            nå = {
                assertTrue(observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
            },
            ønsket = {
                assertFalse(observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
            }
        )
        assertEquals(listOf(1.januar til 31.januar), observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer ikke arbeidsgiveropplysninger fra periode med utbetaling som mottar overlappende søknad`() {
        nyPeriode(1.januar til 31.januar)
        person.søppelbøtte(hendelselogg) { true }
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        nyPeriode(31.januar til 31.januar)
        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 31.januar, 31.januar til 31.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger fra kort periode som mottar overlappende søknad som gjør at perioden går utover AGP`() {
        nyPeriode(1.januar til 1.januar)
        person.søppelbøtte(hendelselogg) { true }
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)

        nyPeriode(1.januar til 31.januar)
        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 1.januar, 1.januar til 31.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Sender forventede sykmeldingsperioder når søknad blir kastet ut pga delvis overlapp` () {
        nyPeriode(1.januar til 25.januar)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTrue(observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertEquals(listOf(1.januar til 25.januar, 1.januar til 31.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
    }

    @Test
    fun `Forventer arbeidsgiveropplysninger når søknad delvis overlapper med en kort periode` () {
        nyPeriode(1.januar til 15.januar)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertEquals(listOf(1.januar til 15.januar, 1.januar til 31.januar), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
        assertTrue(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
    }

    @Test
    fun `Sender ikke med senere sykmeldingsperioder enn vedtaksperioden som forkastes` () {
        nyPeriode(1.januar til 31.januar)
        nyPeriode(1.februar til 28.februar)
        person.søppelbøtte(hendelselogg) { true }
        assertEquals(listOf(1.januar til 31.januar), observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).sykmeldingsperioder)
        assertTrue(observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
        assertFalse(observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).trengerArbeidsgiveropplysninger)
    }
}
