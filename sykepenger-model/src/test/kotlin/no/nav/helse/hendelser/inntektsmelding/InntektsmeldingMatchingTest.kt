package no.nav.helse.hendelser.inntektsmelding

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
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

        assertEquals(2.januar til 16.januar, dager.håndter(vedtaksperiode1))
        assertEquals(1.januar.somPeriode(), dager.håndterPeriodeRettFør(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperiode slutter på lørdag, forlengelse starter på mandag - første fraværsdag 5 januar`() {
        val vedtaksperiode1 = 5.januar til 20.januar
        val vedtaksperiode2 = 22.januar til 31.januar
        val dager = inntektsmelding(5.januar, 5.januar til 20.januar)

        assertEquals(5.januar til 20.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperiode slutter på lørdag, forlengelse starter på mandag - første fraværsdag 22 januar`() {
        val vedtaksperiode1 = 5.januar til 20.januar
        val vedtaksperiode2 = 22.januar til 31.januar
        val dager = inntektsmelding(22.januar, 5.januar til 20.januar)

        assertEquals(5.januar til 20.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
        // TODO: Hva første fraværsdag er satt til har innvirkning på hvor lang sykdomstisdlinjen til IM er
    }

    @Test
    fun `inntektsmelding treffer ingen vedtaksperiode`() {
        val vedtaksperiode1 = 1.januar til 31.januar
        val dager = inntektsmelding(1.mars, 1.mars til 16.mars)

        assertNull(dager.håndter(vedtaksperiode1))
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode1))
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
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode1))
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
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode1))
        assertEquals(8.januar til 10.januar, dager.håndter(vedtaksperiode2))
        assertEquals(5.januar til 7.januar, dager.håndterPeriodeRettFør(vedtaksperiode2))
        assertEquals(11.januar til 21.januar, dager.håndter(vedtaksperiode3))
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode2))
    }

    @Test
    fun `første fraværsdag i helg`() {
        val vedtaksperiode1 = 1.januar til 16.januar
        val vedtaksperiode2 = 22.januar til 31.januar

        val dager = inntektsmelding(20.januar, 1.januar til 16.januar)

        assertEquals(1.januar til 16.januar, dager.håndter(vedtaksperiode1))
        assertNull(dager.håndterPeriodeRettFør(vedtaksperiode1))
        assertNull(dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `Har blitt håndtert av`() {
        val vedtaksperiode1 =  2.januar til 15.januar
        val dager = inntektsmelding(1.januar, 1.januar til 16.januar)

        assertTrue(dager.skalHåndteresAv(vedtaksperiode1))
        assertFalse(dager.harBlittHåndtertAv(vedtaksperiode1))
        assertEquals(1.januar.somPeriode(), dager.håndterPeriodeRettFør(vedtaksperiode1))
        assertEquals(2.januar til 15.januar, dager.håndter(vedtaksperiode1))
        assertTrue(dager.skalHåndteresAv(vedtaksperiode1))
        assertTrue(dager.harBlittHåndtertAv(vedtaksperiode1))
    }

    @Test
    fun `Har ikke blitt håndtert av revurdering mer enn 10 dager`() {
        val vedtaksperiode1 =  1.januar til 31.januar
        val vedtaksperiode2 =  1.februar til 28.februar
        val sammenhengendePeriode = 1.januar til 28.februar
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(1.januar til 16.januar))
        val dager = inntektsmelding(1.februar, 1.februar til 16.februar)

        assertFalse(dager.skalHåndteresAvRevurdering(vedtaksperiode1, sammenhengendePeriode, arbeidsgiverperiode))
        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode2, sammenhengendePeriode, arbeidsgiverperiode))

        val håndtertDagerFraRevurdering = dager.håndterDagerFraRevurdering(arbeidsgiverperiode)
        assertFalse(håndtertDagerFraRevurdering)
    }

    @Test
    fun `Har blitt håndtert av revurdering mindre enn 10 dager`() {
        val vedtaksperiode1 =  10.januar til 31.januar
        val vedtaksperiode2 =  1.februar til 28.februar
        val sammenhengendePeriode = 10.januar til 28.februar
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(10.januar til 26.januar))
        val dager = inntektsmelding(1.februar, 1.februar til 16.februar)

        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode1, sammenhengendePeriode, arbeidsgiverperiode))
        assertTrue(dager.skalHåndteresAvRevurdering(vedtaksperiode2, sammenhengendePeriode, arbeidsgiverperiode))

        val håndtertDagerFraRevurdering = dager.håndterDagerFraRevurdering(arbeidsgiverperiode)
        assertTrue(håndtertDagerFraRevurdering)
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

        val håndtertDagerFraRevurdering = dager.håndterDagerFraRevurdering(arbeidsgiverperiode)
        assertTrue(håndtertDagerFraRevurdering)
    }

    @Test
    fun `arbeidsgiverperiode rett i forkant av vedtaksperiode`() {
        val vedtaksperiode1 = 17.januar til 31.januar
        val dager = inntektsmelding(1.januar, 1.januar til 16.januar)
        assertNull(dager.håndter(vedtaksperiode1))
        assertEquals(1.januar til 16.januar, dager.håndterPeriodeRettFør(vedtaksperiode1))
    }

    @Test
    fun `arbeidsgiverperiode rett i forkant av vedtaksperiode med helg mellom`() {
        val vedtaksperiode1 = 22.januar til 31.januar
        val dager = inntektsmelding(4.januar, 4.januar til 19.januar)
        assertNull(dager.håndter(vedtaksperiode1))
        assertEquals(4.januar til 19.januar, dager.håndterPeriodeRettFør(vedtaksperiode1))
    }

    @Test
    fun `arbeidsgiverperioden strekker seg utover vedtaksperioden`() {
        val vedtaksperiode1 = 1.januar til 20.januar
        val vedtaksperiode2 = 25.januar til 25.januar

        val dager = inntektsmelding(
            25.januar,
            25.januar til 25.januar.plusDays(15)
        )
        assertNull(dager.håndter(vedtaksperiode1))
        assertEquals(25.januar til 25.januar, dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperioden strekker seg utover vedtaksperioden, men første fraværsdag er etter perioden`() {
        val vedtaksperiode1 = 1.januar til 20.januar
        val vedtaksperiode2 = 25.januar til 25.januar

        val dager = inntektsmelding(
            26.januar,
            25.januar til 25.januar.plusDays(15)
        )
        assertNull(dager.håndter(vedtaksperiode1))
        assertEquals(25.januar til 25.januar, dager.håndter(vedtaksperiode2))
    }

    @Test
    fun `arbeidsgiverperiode starter før & slutter etter vedtaksperioder med gap mellom`() {
        val vedtaksperiode1 = 2.januar til 3.januar
        val vedtaksperiode2 = 8.januar til 9.januar
        val vedtaksperiode3 = 11.januar til 12.januar

        val dager = inntektsmelding(null, 1.januar til 16.januar)

        assertEquals(2.januar til 3.januar, dager.håndter(vedtaksperiode1))
        assertEquals(8.januar til 9.januar, dager.håndter(vedtaksperiode2))
        assertEquals(11.januar til 12.januar, dager.håndter(vedtaksperiode3))

        assertEquals(setOf(1.januar, 4.januar, 5.januar, 6.januar, 7.januar, 10.januar, 13.januar, 14.januar, 15.januar, 16.januar), dager.inspektør.gjenståendeDager)
        assertTrue(dager.noenDagerHåndtert())
    }

    @Test
    fun `Må hensynta arbeidsdager før i tillegg til de opprinnelig dagene for å avgjøre om en periode er håndtert`() {
        val vedtaksperiode = 1.januar til 31.januar
        val dager = inntektsmelding(1.januar, 15.januar til 30.januar)
        dager.leggTilArbeidsdagerFør(vedtaksperiode.start)
        assertEquals(1.januar til 30.januar, dager.håndter(vedtaksperiode))
        assertFalse(dager.harBlittHåndtertAv(31.desember(2017).somPeriode()))
        assertTrue(dager.harBlittHåndtertAv(1.januar til 14.januar))
        assertFalse(dager.harBlittHåndtertAv(31.januar.somPeriode()))
    }

    private fun DagerFraInntektsmelding.håndter(periode: Periode): Periode? {
        var håndtertPeriode: Periode? = null
        håndter(periode, { null }) {
            håndtertPeriode = it.sykdomstidslinje().periode()
            Sykdomstidslinje()
        }
        return håndtertPeriode
    }

    private fun DagerFraInntektsmelding.håndterPeriodeRettFør(periode: Periode): Periode? {
        return håndterPeriodeRettFør(periode) {
            it.sykdomstidslinje()
        }.periode()
    }

    private fun DagerFraInntektsmelding.håndterDagerFraRevurdering(arbeidsgiverperiode: Arbeidsgiverperiode): Boolean {
        var håndtert = false
        håndterKorrigering(arbeidsgiverperiode) { håndtert = true }
        return håndtert
    }

    private companion object {
        private fun inntektsmelding(
            førsteFraværsdag: LocalDate?,
            vararg arbeidsgiverperiode: Periode
        ) = Inntektsmelding(
            meldingsreferanseId = UUID.randomUUID(),
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null),
            orgnummer = "12345678",
            fødselsnummer = "12345678910",
            aktørId = "1",
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = 31000.månedlig,
            arbeidsgiverperioder = arbeidsgiverperiode.toList(),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null,
            harFlereInntektsmeldinger = false,
            mottatt = LocalDateTime.now()
        ).let { inntektsmelding ->
            DagerFraInntektsmelding(inntektsmelding)
        }

        private val DagerFraInntektsmelding.inspektør get() = DagerFraInntektsmeldingInspektør(this)
        private class DagerFraInntektsmeldingInspektør(dager: DagerFraInntektsmelding): DagerFraInntektsmeldingVisitor {
            lateinit var gjenståendeDager: Set<LocalDate>

            init {
                dager.accept(this)
            }

            override fun visitGjenståendeDager(dager: Set<LocalDate>) {
                gjenståendeDager = dager
            }
        }
    }
}