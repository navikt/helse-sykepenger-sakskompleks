package no.nav.helse.hendelser

import no.nav.helse.februar
import no.nav.helse.januar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ForeldrepengerTest {


    @Test
    fun `foreldrepenger fullstendig i perioden`() {
        val vedtaksperiode = 1.januar til 31.januar
        val foreldrepenger = Foreldrepenger(listOf(1.januar til 31.januar))
        assertTrue(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode))
    }

    @Test
    fun `foreldrepenger utenfor perioden`() {
        val vedtaksperiode = 1.februar til 28.februar
        val foreldrepenger = Foreldrepenger(listOf(1.januar til 31.januar))
        assertFalse(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode))
    }

    @Test
    fun `foreldrepenger i halen av perioden`() {
        val vedtaksperiode = 1.januar til 31.januar
        val foreldrepenger = Foreldrepenger(listOf(20.januar til 31.januar))
        assertTrue(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode))
    }
    @Test
    fun `foreldrepenger i snuten av perioden`() {
        val vedtaksperiode = 1.januar til 31.januar
        val foreldrepenger = Foreldrepenger(listOf(1.januar til 20.januar))
        assertFalse(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode))
    }

    @Test
    fun `foreldrepenger i snuten og før perioden`() {
        val vedtaksperiode = 1.februar til 20.februar
        val foreldrepenger = Foreldrepenger(listOf(1.januar til 5.februar))
        assertFalse(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode))
    }

    @Test
    fun `foreldrepenger i halen og etter perioden`() {
    }

    @Test
    fun `foreldrepenger oppstykket i perioden`() {
        val vedtaksperiode = 1.januar til 31.januar
        val foreldrepenger = Foreldrepenger(listOf(1.januar til 10.januar, 20.januar til 31.januar))
        assertFalse(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode))
    }

    @Test
    fun `har vedtaksperiode rett etter`() {
        val vedtaksperiode= 1.januar til 31.januar
        val forlengelse= 1.februar til 28.februar
        val foreldrepenger = Foreldrepenger(listOf(1.januar til 31.januar))
        assertFalse(foreldrepenger.skalOppdatereHistorikk(vedtaksperiode, forlengelse))
    }
}