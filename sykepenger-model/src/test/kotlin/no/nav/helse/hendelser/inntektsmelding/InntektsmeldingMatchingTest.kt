package no.nav.helse.hendelser.inntektsmelding

import java.time.LocalDate
import no.nav.helse.Personidentifikator
import no.nav.helse.desember
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.februar
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class InntektsmeldingMatchingTest {

    @Test
    fun `1-16 - auu som eneste periode mottar inntektsmelding`() {
        val vedtaksperiode1 = 1.januar til 16.januar
        val dager = inntektsmelding(1.januar, 1.januar til 16.januar)
        assertEquals(1.januar til 16.januar, dager.håndter(vedtaksperiode1))
    }

    @Test
    fun `1-16, 17-31 - auu håndterer dager, forlengelse håndterer inntekt og refusjon`() {
        val vedtaksperiode1 = 1.januar til 16.januar
        val vedtaksperiode2 = 17.januar til 31.januar
        val dager = inntektsmelding(1.januar, 1.januar til 16.januar)

        assertEquals(1.januar til 16.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `2-17, 22-31 - opplyser om annen arbeidsgiverperiode`() {
        val vedtaksperiode1 = 2.januar til 17.januar
        val vedtaksperiode2 = 22.januar til 31.januar
        val dager = inntektsmelding(22.januar, 1.januar til 16.januar)

        assertEquals(1.januar til 16.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperiode slutter på lørdag, forlengelse starter på mandag - første fraværsdag 5 januar`() {
        val vedtaksperiode1 = 5.januar til 20.januar
        val vedtaksperiode2 = 22.januar til 31.januar
        val dager = inntektsmelding(5.januar, 5.januar til 20.januar)

        assertEquals(5.januar til 20.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperiode slutter på lørdag, forlengelse starter på mandag - første fraværsdag 22 januar`() {
        val vedtaksperiode1 = 5.januar til 20.januar
        val vedtaksperiode2 = 22.januar til 31.januar
        val dager = inntektsmelding(22.januar, 5.januar til 20.januar)

        assertEquals(5.januar til 20.januar, dager.håndter(vedtaksperiode1))
        assertEquals(21.januar.somPeriode(), dager.håndter(vedtaksperiode2))
        // TODO: Hva første fraværsdag er satt til har innvirkning på hvor lang sykdomstisdlinjen til IM er
    }

    @Test
    fun `inntektsmelding treffer ingen vedtaksperiode`() {
        val vedtaksperiode1 = januar
        val dager = inntektsmelding(1.mars, 1.mars til 16.mars)

        assertFalse(dager.skalHåndteresAv(vedtaksperiode1))
        assertEquals(januar, dager.håndter(vedtaksperiode1))
    }

    @Test
    fun `oppstykket arbeidsgiverperiode med gjenstående dager`() {
        val vedtaksperiode1 = 1.januar til 20.januar
        val dager = inntektsmelding(
            1.januar,
            1.januar til 5.januar, // mandag - fredag
            8.januar til 12.januar, // mandag - fredag,
            15.januar til 19.januar, // mandag - fredag,
            22.januar.somPeriode() // mandag
        )

        assertEquals(1.januar til 20.januar, dager.håndter(vedtaksperiode1))
    }

    @Test
    fun `vedtaksperiode som skal håndtere inntekt starter i helg`() {
        val vedtaksperiode1 = 3.januar til 4.januar
        val vedtaksperiode2 = 8.januar til 10.januar
        val vedtaksperiode3 = 11.januar til 22.januar

        val dager = inntektsmelding(
            8.januar,
            3.januar til 4.januar,
            8.januar til 21.januar
        )

        assertEquals(3.januar til 4.januar, dager.håndter(vedtaksperiode1))
        assertEquals(5.januar til 10.januar, dager.håndter(vedtaksperiode2))
        assertEquals(11.januar til 21.januar, dager.håndter(vedtaksperiode3))
    }

    @Test
    fun `første fraværsdag i helg`() {
        val vedtaksperiode1 = 1.januar til 16.januar
        val vedtaksperiode2 = 22.januar til 31.januar

        val dager = inntektsmelding(20.januar, 1.januar til 16.januar)

        assertEquals(1.januar til 16.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `Har blitt håndtert av`() {
        val vedtaksperiode1 =  2.januar til 15.januar
        val dager = inntektsmelding(1.januar, 1.januar til 16.januar)

        assertTrue(dager.skalHåndteresAv(vedtaksperiode1))
        assertFalse(dager.harBlittHåndtertAv(vedtaksperiode1))
        assertEquals(1.januar til 15.januar, dager.håndter(vedtaksperiode1))
        assertTrue(dager.skalHåndteresAv(vedtaksperiode1))
        assertTrue(dager.harBlittHåndtertAv(vedtaksperiode1))
    }

    @Test
    fun `Har ikke blitt håndtert av revurdering mer enn 10 dager`() {
        val vedtaksperiode1 = 1.januar til 31.januar
        val vedtaksperiode2 = februar
        val sammenhengendePeriode = 1.januar til 28.februar
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(1.januar til 16.januar))
        val dager = inntektsmelding(1.februar, 1.februar til 16.februar)

        assertFalse(dager.skalHåndteresAvRevurdering(vedtaksperiode1, sammenhengendePeriode, arbeidsgiverperiode))
        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode2, sammenhengendePeriode, arbeidsgiverperiode))

        val håndtertDagerFraRevurdering = dager.erKorrigeringForGammel(arbeidsgiverperiode)
        assertTrue(håndtertDagerFraRevurdering)
    }

    @Test
    fun `Har blitt håndtert av revurdering mindre enn 10 dager`() {
        val vedtaksperiode1 = 10.januar til 31.januar
        val vedtaksperiode2 = februar
        val sammenhengendePeriode = 10.januar til 28.februar
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(10.januar til 26.januar))
        val dager = inntektsmelding(1.februar, 1.februar til 16.februar)

        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode1, sammenhengendePeriode, arbeidsgiverperiode))
        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode2, sammenhengendePeriode, arbeidsgiverperiode))

        val håndtertDagerFraRevurdering = dager.erKorrigeringForGammel(arbeidsgiverperiode)
        assertFalse(håndtertDagerFraRevurdering)
    }

    @Test
    fun `Har ikke blitt håndtert av revurdering mindre enn 10 dager med gap`() {
        val vedtaksperiode1 =  10.januar til 31.januar
        val vedtaksperiode2 =  2.februar til 28.februar
        val sammenhengendePeriode1 = 10.januar til 31.januar
        val sammenhengendePeriode2 = 2.februar til 28.februar
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(10.januar til 26.januar))
        val dager = inntektsmelding(2.februar, 2.februar til 17.februar)

        assertFalse(dager.skalHåndteresAvRevurdering(vedtaksperiode1, sammenhengendePeriode1, arbeidsgiverperiode))
        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode2, sammenhengendePeriode2, arbeidsgiverperiode))

        val håndtertDagerFraRevurdering = dager.erKorrigeringForGammel(arbeidsgiverperiode)
        assertFalse(håndtertDagerFraRevurdering)
    }

    @Test
    fun `arbeidsgiverperiode rett i forkant av vedtaksperiode`() {
        val vedtaksperiode1 = 17.januar til 31.januar
        val dager = inntektsmelding(1.januar, 1.januar til 16.januar)
        assertEquals(1.januar til 16.januar, dager.håndter(vedtaksperiode1))
    }

    @Test
    fun `arbeidsgiverperiode rett i forkant av vedtaksperiode med helg mellom`() {
        val vedtaksperiode1 = 22.januar til 31.januar
        val dager = inntektsmelding(4.januar, 4.januar til 19.januar)
        assertEquals(4.januar til 19.januar, dager.håndter(vedtaksperiode1))
    }

    @Test
    fun `arbeidsgiverperioden strekker seg utover vedtaksperioden`() {
        val vedtaksperiode1 = 1.januar til 20.januar
        val vedtaksperiode2 = 25.januar til 25.januar

        val dager = inntektsmelding(
            25.januar,
            25.januar til 25.januar.plusDays(15)
        )
        assertEquals(1.januar til 20.januar, dager.håndter(vedtaksperiode1))
        assertEquals(21.januar til 25.januar, dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperioden strekker seg utover vedtaksperioden, men første fraværsdag er etter perioden`() {
        val vedtaksperiode1 = 1.januar til 20.januar
        val vedtaksperiode2 = 25.januar til 25.januar

        val dager = inntektsmelding(
            26.januar,
            25.januar til 25.januar.plusDays(15)
        )
        assertFalse(dager.skalHåndteresAv(vedtaksperiode1))
        assertEquals(1.januar til 20.januar, dager.håndter(vedtaksperiode1))
        assertEquals(21.januar til 25.januar, dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperiode starter før & slutter etter vedtaksperioder med gap mellom`() {
        val vedtaksperiode1 = 2.januar til 3.januar
        val vedtaksperiode2 = 8.januar til 9.januar
        val vedtaksperiode3 = 11.januar til 12.januar

        val dager = inntektsmelding(null, 1.januar til 16.januar)

        assertEquals(1.januar til 3.januar, dager.håndter(vedtaksperiode1))
        assertEquals(4.januar til 9.januar, dager.håndter(vedtaksperiode2))
        assertEquals(10.januar til 12.januar, dager.håndter(vedtaksperiode3))
        assertEquals(setOf(13.januar, 14.januar, 15.januar, 16.januar), dager.inspektør.gjenståendeDager)
    }

    @Test
    fun `Må hensynta arbeidsdager før i tillegg til de opprinnelig dagene for å avgjøre om en periode er håndtert`() {
        val vedtaksperiode = januar
        val dager = inntektsmelding(1.januar, 15.januar til 30.januar)
        dager.leggTilArbeidsdagerFør(vedtaksperiode.start)
        assertEquals(1.januar til 30.januar, dager.håndter(vedtaksperiode))
        assertFalse(dager.harBlittHåndtertAv(31.desember(2017).somPeriode()))
        assertTrue(dager.harBlittHåndtertAv(1.januar til 14.januar))
        assertTrue(dager.harBlittHåndtertAv(31.januar.somPeriode()))
    }

    private fun DagerFraInntektsmelding.håndter(periode: Periode): Periode? {
        return bitAvInntektsmelding(periode)?.sykdomstidslinje()?.periode()
    }

    private companion object {
        private val fabrikk = ArbeidsgiverHendelsefabrikk("42", Personidentifikator("11"), "a1")
        private fun inntektsmelding(
            førsteFraværsdag: LocalDate?,
            vararg arbeidsgiverperiode: Periode
        ) = fabrikk.lagInntektsmelding(arbeidsgiverperiode.toList(), førsteFraværsdag = førsteFraværsdag, beregnetInntekt = 400.månedlig).dager()
    }
}