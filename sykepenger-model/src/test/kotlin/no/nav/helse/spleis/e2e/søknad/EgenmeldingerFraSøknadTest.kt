package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EgenmeldingerFraSøknadTest : AbstractEndToEndTest() {

    @Test
    fun `legger egenmeldingsdager fra søknad på sykdomstidslinjen`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 31.januar))
        håndterSøknad(Sykdom(2.januar, 31.januar, 100.prosent), egenmeldinger = listOf(1.januar til 1.januar))

        assertTrue(inspektør.sykdomstidslinje[1.januar] is Dag.Arbeidsgiverdag)
        assertEquals(januar, inspektør(ORGNUMMER).periode(1.vedtaksperiode))
    }

    @Test
    fun `fjerner egenmeldingsdager fra søknad som er før tidligere vedtaksperioder`() {
        nyttVedtak(3.januar til 21.januar)

        håndterSykmelding(Sykmeldingsperiode(25.januar, 31.januar))
        håndterSøknad(Sykdom(25.januar, 31.januar, 100.prosent), egenmeldinger = listOf(
            1.januar til 2.januar,
            24.januar til 24.januar
        ))

        assertEquals(24.januar til 31.januar, inspektør(ORGNUMMER).periode(2.vedtaksperiode))
        assertTrue(inspektør.sykdomstidslinje[24.januar] is Dag.Arbeidsgiverdag)
    }

    @Test
    fun `fjerner egenmeldingsdager fra søknad som overlapper med tidligere vedtaksperioder`() {
        nyttVedtak(3.januar til 21.januar)

        håndterSykmelding(Sykmeldingsperiode(25.januar, 31.januar))
        håndterSøknad(Sykdom(25.januar, 31.januar, 100.prosent), egenmeldinger = listOf(
            1.januar til 4.januar,
            20.januar til 24.januar
        ))

        assertEquals(22.januar til 31.januar, inspektør(ORGNUMMER).periode(2.vedtaksperiode) )
        assertTrue(inspektør.sykdomstidslinje[21.januar] is Dag.SykHelgedag)
        assertTrue(inspektør.sykdomstidslinje[22.januar] is Dag.Arbeidsgiverdag)
        assertTrue(inspektør.sykdomstidslinje[23.januar] is Dag.Arbeidsgiverdag)
        assertTrue(inspektør.sykdomstidslinje[24.januar] is Dag.Arbeidsgiverdag)
    }

    @Test
    fun `fjerner egenmeldingsdager fra søknad som er etter starten av sykmeldingsperioden`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), egenmeldinger = listOf(
            31.desember(2017) til 1.februar
        ))

        assertEquals(31.desember(2017) til 31.januar, inspektør(ORGNUMMER).periode(1.vedtaksperiode))
        assertTrue(inspektør.sykdomstidslinje[31.desember(2017)] is Dag.ArbeidsgiverHelgedag)
        assertTrue(inspektør.sykdomstidslinje[1.januar] is Dag.Sykedag)
        assertTrue(inspektør.sykdomstidslinje[1.februar] is Dag.UkjentDag)
    }

    @Test
    fun `egenmeldingsdager påvirker ikke hvilken vedtaksperiode som håndterer en søknad`() {
        nyttVedtak(3.januar til 21.januar)

        håndterSykmelding(Sykmeldingsperiode(25.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(25.januar, 31.januar, 100.prosent), egenmeldinger = listOf(
            1.januar til 4.januar
        ))

        assertFalse(inspektør(ORGNUMMER).hendelseIder(1.vedtaksperiode).contains(søknadId))
        assertTrue(inspektør(ORGNUMMER).hendelseIder(2.vedtaksperiode).contains(søknadId))
    }

    @Test
    fun `kort periode blir lang pga egenmeldingsdager fra søknad, men blir kort igjen pga inntektsmelding - skal gå til Avsluttet Uten Utbetaling`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 17.januar))
        håndterSøknad(Sykdom(2.januar, 17.januar, 100.prosent), egenmeldinger = listOf(
            1.januar til 1.januar
        ))
        håndterInntektsmelding(listOf(2.januar til 17.januar))

        assertTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `egenmeldingsdager fra inntektsmelding vinner over søknad`() {
        håndterSykmelding(Sykmeldingsperiode(7.januar, 31.januar))
        håndterSøknad(Sykdom(7.januar, 31.januar, 100.prosent), egenmeldinger = listOf(
            3.januar til 5.januar
        ))
        håndterInntektsmelding(listOf(1.januar til 1.januar, 5.januar til 19.januar))

        assertTrue(inspektør.sykdomstidslinje[1.januar] is Dag.Arbeidsgiverdag)
        assertTrue(inspektør.sykdomstidslinje[2.januar] is Dag.Arbeidsdag)
        assertTrue(inspektør.sykdomstidslinje[3.januar] is Dag.Arbeidsdag)
        assertTrue(inspektør.sykdomstidslinje[4.januar] is Dag.Arbeidsdag)
        assertTrue(inspektør.sykdomstidslinje[5.januar] is Dag.Arbeidsgiverdag)
        assertTrue(inspektør.sykdomstidslinje[6.januar] is Dag.ArbeidsgiverHelgedag)
        assertTrue(inspektør.sykdomstidslinje[7.januar] is Dag.SykHelgedag)

        (1..6).forEach {
            assertTrue(inspektør.sykdomstidslinje[it.januar].kommerFra("Inntektsmelding") )
        }
        (7..31).forEach {
            assertTrue(inspektør.sykdomstidslinje[it.januar].kommerFra(Søknad::class) )
        }
    }
    @Test
    fun `arbeidsdager fra inntektsmelding vinner over egenmeldingsdager fra søknad`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 31.januar))
        håndterSøknad(Sykdom(2.januar, 31.januar, 100.prosent), egenmeldinger = listOf(
            1.januar til 1.januar
        ))
        håndterInntektsmelding(listOf(2.januar til 17.januar))

        assertTrue(inspektør.sykdomstidslinje[1.januar] is Dag.Arbeidsdag)
        assertTrue(inspektør.sykdomstidslinje[1.januar].kommerFra("Inntektsmelding") )

        (2..31).forEach {
            assertTrue(inspektør.sykdomstidslinje[it.januar].kommerFra(Søknad::class) )
        }
    }

    @Test
    fun `skal kutte bort egenmeldingsdager som ikke påvirker arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), egenmeldinger = listOf(
            26.desember(2017) til 26.desember(2017),
            15.januar til 15.januar,
            1.februar til 1.februar,
            15.februar til 15.februar)
        )

        assertForventetFeil(
            forklaring = "Kan vurdere å innføre dette for å unngå unødvendig lange pølser",
            nå = {
                assertTrue(inspektør.sykdomstidslinje[26.desember(2017)] is Dag.Arbeidsgiverdag)
                assertTrue(inspektør.sykdomstidslinje[15.januar] is Dag.Arbeidsgiverdag)
                assertTrue(inspektør.sykdomstidslinje[1.februar] is Dag.Arbeidsgiverdag)
                assertTrue(inspektør.sykdomstidslinje[15.februar] is Dag.Arbeidsgiverdag)
            },
            ønsket = {
                assertTrue(inspektør.sykdomstidslinje[26.desember(2017)] is Dag.UkjentDag)
                assertTrue(inspektør.sykdomstidslinje[15.januar] is Dag.Arbeidsgiverdag)
                assertTrue(inspektør.sykdomstidslinje[1.februar] is Dag.Arbeidsgiverdag)
                assertTrue(inspektør.sykdomstidslinje[15.februar] is Dag.Arbeidsgiverdag)
            }
        )
    }
}