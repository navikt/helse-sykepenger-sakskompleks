package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Grunnbeløp
import no.nav.helse.februar
import no.nav.helse.hendelser.Avsender.ARBEIDSGIVER
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.UtbetalingstidslinjeInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.beløpstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.testhelpers.A
import no.nav.helse.testhelpers.AIG
import no.nav.helse.testhelpers.F
import no.nav.helse.testhelpers.K
import no.nav.helse.testhelpers.N
import no.nav.helse.testhelpers.P
import no.nav.helse.testhelpers.PROBLEM
import no.nav.helse.testhelpers.R
import no.nav.helse.testhelpers.S
import no.nav.helse.testhelpers.U
import no.nav.helse.testhelpers.YA
import no.nav.helse.testhelpers.YD
import no.nav.helse.testhelpers.YF
import no.nav.helse.testhelpers.YO
import no.nav.helse.testhelpers.YOL
import no.nav.helse.testhelpers.YP
import no.nav.helse.testhelpers.YS
import no.nav.helse.testhelpers.opphold
import no.nav.helse.testhelpers.resetSeed
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UtbetalingstidslinjeBuilderTest {
    @Test
    fun problemdag() {
        assertThrows<ProblemdagException> {
            undersøke(1.PROBLEM)
        }
    }

    @Test
    fun kort() {
        undersøke(15.S)
        assertEquals(15, inspektør.size)
        assertEquals(15, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 15.januar,
                arbeidsgiverperiode = listOf(1.januar til 15.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `kort - skal utbetales`() {
        undersøke(15.N)
        assertEquals(15, inspektør.size)
        assertEquals(4, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(11, inspektør.arbeidsgiverperiodedagNavTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 15.januar,
                arbeidsgiverperiode = listOf(1.januar til 15.januar),
                utbetalingsperioder = listOf(1.januar til 5.januar, 8.januar til 12.januar, 15.januar.somPeriode()),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
        assertTrue(perioder.first().somArbeidsgiverperiode().forventerInntekt(1.januar til 15.januar))
    }

    @Test
    fun enkel() {
        undersøke(31.S)
        assertEquals(31, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(11, inspektør.navDagTeller)
        assertEquals(4, inspektør.navHelgDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 31.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(17.januar til 31.januar),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `enkel - SykedagNav`() {
        undersøke(31.N)
        assertEquals(31, inspektør.size)
        assertEquals(12, inspektør.arbeidsgiverperiodedagNavTeller)
        assertEquals(4, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(11, inspektør.navDagTeller)
        assertEquals(4, inspektør.navHelgDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 31.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(1.januar til 5.januar, 8.januar til 12.januar, 15.januar til 31.januar),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
        utbetalingstidslinje[1.januar].økonomi.inspektør.also {
            assertEquals(31000.månedlig, it.aktuellDagsinntekt)
        }
    }

    @Test
    fun `arbeidsgiverperioden er ferdig tidligere`() {
        teller.fullfør()
        undersøke(15.S)
        assertEquals(15, inspektør.size)
        assertEquals(11, inspektør.navDagTeller)
        assertEquals(4, inspektør.navHelgDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 15.januar,
                arbeidsgiverperiode = emptyList(),
                utbetalingsperioder = listOf(1.januar til 15.januar),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
        assertTrue(perioder.first().somArbeidsgiverperiode().fiktiv())
    }

    @Test
    fun `arbeidsgiverperioden oppdages av noen andre`() {
        val betalteDager = listOf(10.januar til 16.januar)
        undersøke(15.S, infotrygdBetalteDager = betalteDager)
        assertEquals(15, inspektør.size)
        assertEquals(9, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(4, inspektør.navDagTeller)
        assertEquals(2, inspektør.navHelgDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 15.januar,
                arbeidsgiverperiode = listOf(1.januar til 9.januar),
                utbetalingsperioder = listOf(10.januar til 15.januar),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `arbeidsgiverperioden er ferdig`() {
        val betalteDager = listOf(1.januar til 1.januar)
        undersøke(15.S, infotrygdBetalteDager = betalteDager)
        assertEquals(15, inspektør.size)
        assertEquals(0, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(11, inspektør.navDagTeller)
        assertEquals(4, inspektør.navHelgDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 15.januar,
                arbeidsgiverperiode = emptyList(),
                utbetalingsperioder = listOf(1.januar til 15.januar),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
        assertTrue(perioder.first().somArbeidsgiverperiode().fiktiv())
    }

    @Test
    fun `ny arbeidsgiverperiode etter infotrygd`() {
        val betalteDager = listOf(1.januar til 1.januar)
        undersøke(1.S + 16.A + 17.S, infotrygdBetalteDager = betalteDager)
        assertEquals(34, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(1, inspektør.navDagTeller)
        assertEquals(1, inspektør.navHelgDagTeller)
        assertEquals(16, inspektør.arbeidsdagTeller)
        assertEquals(2, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 17.januar,
                arbeidsgiverperiode = emptyList(),
                utbetalingsperioder = listOf(1.januar.somPeriode()),
                oppholdsperioder = listOf(2.januar til 17.januar),
                fullstendig = false,
                sisteDag = 17.januar
            ), perioder.first()
        )
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 18.januar til 3.februar,
                arbeidsgiverperiode = listOf(18.januar til 2.februar),
                utbetalingsperioder = listOf(3.februar.somPeriode()),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.last()
        )
        assertTrue(perioder.first().somArbeidsgiverperiode().fiktiv())
    }

    @Test
    fun `opphold etter infotrygd`() {
        val betalteDager = listOf(1.januar til 1.januar)
        undersøke(1.S + 15.A + 18.S, infotrygdBetalteDager = betalteDager)
        assertEquals(34, inspektør.size)
        assertEquals(0, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(14, inspektør.navDagTeller)
        assertEquals(5, inspektør.navHelgDagTeller)
        assertEquals(15, inspektør.arbeidsdagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 3.februar,
                arbeidsgiverperiode = emptyList(),
                utbetalingsperioder = listOf(1.januar.somPeriode(), 17.januar til 3.februar),
                oppholdsperioder = listOf(2.januar til 16.januar),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
        assertTrue(perioder.first().somArbeidsgiverperiode().fiktiv())
    }

    @Test
    fun `infotrygd utbetaler etter vi har startet arbeidsgiverperiodetelling med opphold`() {
        val betalteDager = listOf(11.januar til 1.februar)
        undersøke(9.S + 1.A + 22.S, infotrygdBetalteDager = betalteDager)
        assertEquals(32, inspektør.size)
        assertEquals(9, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(16, inspektør.navDagTeller)
        assertEquals(6, inspektør.navHelgDagTeller)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 1.februar,
                arbeidsgiverperiode = listOf(1.januar til 9.januar),
                utbetalingsperioder = listOf(11.januar til 1.februar),
                oppholdsperioder = listOf(10.januar.somPeriode()),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `kort infotrygdperiode etter utbetalingopphold`() {
        val betalteDager = listOf(19.februar til 15.mars)
        undersøke(16.U + 1.S + 32.opphold + 5.S + 20.S, infotrygdBetalteDager = betalteDager)
        assertEquals(74, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(22, inspektør.arbeidsdagTeller)
        assertEquals(10, inspektør.fridagTeller)
        assertEquals(20, inspektør.navDagTeller)
        assertEquals(6, inspektør.navHelgDagTeller)
    }

    @Test
    fun `infotrygd midt i`() {
        val betalteDager = listOf(22.februar til 13.mars)
        undersøke(20.S + 32.A + 20.S, infotrygdBetalteDager = betalteDager)
        assertEquals(72, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(32, inspektør.arbeidsdagTeller)
        assertEquals(17, inspektør.navDagTeller)
        assertEquals(7, inspektør.navHelgDagTeller)
    }

    @Test
    fun `alt infotrygd`() {
        val betalteDager = listOf(2.januar til 20.januar, 22.februar til 13.mars)
        undersøke(20.S + 32.A + 20.S, infotrygdBetalteDager = betalteDager)
        assertEquals(72, inspektør.size)
        assertEquals(1, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(32, inspektør.arbeidsdagTeller)
        assertEquals(28, inspektør.navDagTeller)
        assertEquals(11, inspektør.navHelgDagTeller)
    }

    @Test
    fun `tar ikke med nyere historikk i beregning av utbetalingstidslinje`() {
        undersøke(31.S, infotrygdBetalteDager = listOf(1.februar til 28.februar))
        assertEquals(31, inspektør.size)
    }

    @Test
    fun `tar ikke med eldre historikk i beregning av utbetalingstidslinje`() {
        undersøke(31.opphold + 28.S, infotrygdBetalteDager = listOf(1.januar til 31.januar))
        assertEquals(28, inspektør.size)
    }

    @Test
    fun `ferie etter arbeid etter fullført agp`() {
        undersøke(31.S + 4.A + 24.F)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 16.februar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(17.januar til 31.januar),
                oppholdsperioder = listOf(1.februar til 16.februar),
                fullstendig = true,
                sisteDag = 16.februar
            ), perioder.single()
        )
    }

    @Test
    fun `ferie og permisjon med i arbeidsgiverperioden`() {
        undersøkeLike({ 6.S + 6.F + 6.S }, { 6.S + 6.P + 6.S }, { 6.S + 6.AIG + 6.S }, { 6.S + 6.YF + 6.S }) {
            assertEquals(18, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(2, inspektør.navDagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 18.januar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(17.januar til 18.januar),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `ferie og permisjon fullfører arbeidsgiverperioden`() {
        undersøkeLike({ 1.S + 15.F + 6.S }, { 1.S + 15.P + 6.S }, { 1.S + 15.AIG + 6.S }, { 1.S + 15.YF + 6.S }) {
            assertEquals(22, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(4, inspektør.navDagTeller)
            assertEquals(2, inspektør.navHelgDagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 22.januar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(17.januar til 22.januar),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `ferie med sykmelding etter permisjon fullfører agp`() {
        undersøke(1.S + 10.P + 6.F + 5.S)

        assertEquals(22, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(3, inspektør.navDagTeller)
        assertEquals(2, inspektør.navHelgDagTeller)
        assertEquals(1, inspektør.fridagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 22.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(18.januar til 22.januar),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `ferie og permisjon etter utbetaling`() {
        undersøkeLike({ 16.S + 15.F }, { 16.S + 15.P }, { 16.S + 15.AIG }) {
            assertEquals(31, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 31.januar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `andre ytelser etter utbetaling`() {
        undersøke(16.S + 15.YF)
        assertEquals(31, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(15, inspektør.avvistDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 31.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `andre ytelser umiddelbart etter utbetaling teller ikke som opphold`() {
        undersøke(16.S + 16.YF + 10.S)
        assertEquals(42, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(16, inspektør.avvistDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 11.februar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(2.februar til 11.februar),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `andre ytelser umiddelbart etter utbetaling teller ikke som opphold hvis etterfølgt av arbeidsdag`() {
        undersøke(16.S + 15.YF + 1.A + 10.S)
        assertEquals(42, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(6, inspektør.navDagTeller)
        assertEquals(4, inspektør.navHelgDagTeller)
        assertEquals(15, inspektør.avvistDagTeller)
        assertEquals(1, inspektør.arbeidsdagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 11.februar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(2.februar til 11.februar),
                oppholdsperioder = listOf(1.februar.somPeriode()),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `ferie og permisjon umiddelbart etter utbetaling teller ikke som opphold hvis etterfølgt av arbeidsdag`() {
        undersøkeLike({ 16.S + 15.F + 1.A + 10.S }, { 16.S + 15.P + 1.A + 10.S }, { 16.S + 15.AIG + 1.A + 10.S }) {
            assertEquals(42, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(6, inspektør.navDagTeller)
            assertEquals(4, inspektør.navHelgDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 11.februar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(2.februar til 11.februar),
                    oppholdsperioder = listOf(1.februar.somPeriode()),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `ferie og permisjon etter utbetaling teller ikke som opphold hvis etterfølgt av arbeidsdag`() {
        undersøkeLike({ 17.S + 15.F + 1.A + 9.S }, { 17.S + 15.P + 1.A + 9.S }, { 17.S + 15.AIG + 1.A + 9.S }) {
            assertEquals(42, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(6, inspektør.navDagTeller)
            assertEquals(4, inspektør.navHelgDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 11.februar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(17.januar.somPeriode(), 3.februar til 11.februar),
                    oppholdsperioder = listOf(2.februar.somPeriode()),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `ferie og permisjon mellom utbetaling`() {
        undersøkeLike({ 16.S + 10.F + 5.S }, { 16.S + 10.P + 5.S }, { 16.S + 10.AIG + 5.S }) {
            assertEquals(31, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(3, inspektør.navDagTeller)
            assertEquals(2, inspektør.navHelgDagTeller)
            assertEquals(10, inspektør.fridagTeller)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 31.januar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(27.januar til 31.januar),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `permisjon før arbeidsdag tilbakestiller arbeidsgiverperioden`() {
        undersøkeLike({ 1.S + 15.P + 1.A + 16.S }, { 1.S + 15.AIG + 1.A + 16.S }) {
            assertEquals(33, inspektør.size)
            assertEquals(17, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(2, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 17.januar,
                    arbeidsgiverperiode = listOf(1.januar.somPeriode()),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = listOf(2.januar til 17.januar),
                    fullstendig = false,
                    sisteDag = 17.januar
                ), perioder.first()
            )
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 18.januar til 2.februar,
                    arbeidsgiverperiode = listOf(18.januar til 2.februar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.last()
            )
        }
    }

    @Test
    fun `ferie før arbeidsdag tilbakestiller ikke arbeidsgiverperioden`() {
        undersøkeLike({ 1.S + 15.F + 1.A + 16.S }) {
            assertEquals(33, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(0, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(12, inspektør.navDagTeller)
            assertEquals(4, inspektør.navHelgDagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 2.februar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(18.januar til 2.februar),
                    oppholdsperioder = listOf(17.januar.somPeriode()),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `ferie og permisjon etter arbeidsdag tilbakestiller arbeidsgiverperioden`() {
        undersøkeLike({ 1.S + 1.A + 15.F + 16.S }, { 1.S + 1.A + 15.P + 16.S }, { 1.S + 1.A + 15.AIG + 16.S }) {
            assertEquals(33, inspektør.size)
            assertEquals(17, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(2, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 17.januar,
                    arbeidsgiverperiode = listOf(1.januar.somPeriode()),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = listOf(2.januar til 17.januar),
                    fullstendig = false,
                    sisteDag = 17.januar
                ), perioder.first()
            )
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 18.januar til 2.februar,
                    arbeidsgiverperiode = listOf(18.januar til 2.februar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.last()
            )
        }
    }

    @Test
    fun `ferie og permisjon etter frisk helg tilbakestiller arbeidsgiverperioden`() {
        undersøkeLike({ 6.S + 1.A + 15.F + 16.S }, { 6.S + 1.A + 15.P + 16.S }, { 6.S + 1.A + 15.AIG + 16.S }) {
            assertEquals(38, inspektør.size)
            assertEquals(22, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(2, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 22.januar,
                    arbeidsgiverperiode = listOf(1.januar til 6.januar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = listOf(7.januar til 22.januar),
                    fullstendig = false,
                    sisteDag = 22.januar
                ), perioder.first()
            )
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 23.januar til 7.februar,
                    arbeidsgiverperiode = listOf(23.januar til 7.februar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.last()
            )
        }
    }

    @Test
    fun `permisjon før frisk helg tilbakestiller arbeidsgiverperioden`() {
        undersøkeLike({ 5.S + 15.P + 1.A + 16.S }, { 5.S + 15.AIG + 1.A + 16.S }) {
            assertEquals(37, inspektør.size)
            assertEquals(21, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(2, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 21.januar,
                    arbeidsgiverperiode = listOf(1.januar til 5.januar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = listOf(6.januar til 21.januar),
                    fullstendig = false,
                    sisteDag = 21.januar
                ), perioder.first()
            )
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 22.januar til 6.februar,
                    arbeidsgiverperiode = listOf(22.januar til 6.februar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.last()
            )
        }
    }

    @Test
    fun `ferie før frisk helg tilbakestiller ikke arbeidsgiverperioden`() {
        undersøkeLike({ 5.S + 15.F + 1.A + 16.S }) {
            assertEquals(37, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(4, inspektør.fridagTeller)
            assertEquals(1, inspektør.arbeidsdagTeller)
            assertEquals(12, inspektør.navDagTeller)
            assertEquals(4, inspektør.navHelgDagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 6.februar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(22.januar til 6.februar),
                    oppholdsperioder = listOf(21.januar.somPeriode()),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `ferie og permisjon som opphold før arbeidsgiverperioden`() {
        undersøkeLike({ 15.F + 16.S }, { 15.P + 16.S }, { 15.AIG + 16.S }) {
            assertEquals(31, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(15, inspektør.fridagTeller)
            assertEquals(1, perioder.size)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 16.januar til 31.januar,
                    arbeidsgiverperiode = listOf(16.januar til 31.januar),
                    utbetalingsperioder = emptyList(),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `bare arbeidsdager`() {
        undersøke(31.A)
        assertEquals(31, inspektør.size)
        assertEquals(31, inspektør.arbeidsdagTeller)
        assertEquals(0, inspektør.fridagTeller)
        assertEquals(0, perioder.size)
    }

    @Test
    fun `bare ukjent dager`() {
        undersøke(14.opphold)
        assertEquals(0, inspektør.size)
    }

    @Test
    fun `fridager med ukjent dager i mellom`() {
        undersøke(1.F + 12.opphold + 1.F)
        assertEquals(14, inspektør.size)
        assertEquals(9, inspektør.arbeidsdagTeller)
        assertEquals(5, inspektør.fridagTeller)
        assertEquals(0, perioder.size)
    }

    @Test
    fun `fridager fullfører arbeidsgiverperioden dersom etterfulgt av ukjent dager`() {
        undersøke(15.S + 1.F + 12.opphold + 1.S)
        assertEquals(29, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(8, inspektør.arbeidsdagTeller)
        assertEquals(4, inspektør.fridagTeller)
        assertEquals(1, inspektør.navDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 29.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(29.januar.somPeriode()),
                oppholdsperioder = listOf(17.januar til 28.januar),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `foreldet dag regnes fortsatt som syk`() {
        undersøke(16.S + 1.K)
        assertEquals(17, inspektør.size)
        assertEquals(1, inspektør.foreldetDagTeller)
        assertEquals(100, inspektør.grad(17.januar))
    }

    @Test
    fun `foreldet dager telles som arbeidsgiverperiode`() {
        undersøke(10.K + 6.S)
        assertEquals(16, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 16.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `foreldet dager etter utbetaling forblir foreldet`() {
        undersøke(19.K)
        assertEquals(19, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(3, inspektør.foreldetDagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 19.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = listOf(17.januar til 19.januar),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `ferie mellom egenmeldingsdager`() {
        undersøkeLike({ 1.U + 14.F + 1.U + 10.S }, { 1.U + 14.AIG + 1.U + 10.S }) {
            assertEquals(26, inspektør.size)
            assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
            assertEquals(8, inspektør.navDagTeller)
            assertEquals(2, inspektør.navHelgDagTeller)
            assertEquals(
                Arbeidsgiverperioderesultat(
                    omsluttendePeriode = 1.januar til 26.januar,
                    arbeidsgiverperiode = listOf(1.januar til 16.januar),
                    utbetalingsperioder = listOf(17.januar til 26.januar),
                    oppholdsperioder = emptyList(),
                    fullstendig = true,
                    sisteDag = null
                ), perioder.single()
            )
        }
    }

    @Test
    fun `egenmeldingsdager med frisk helg gir opphold i arbeidsgiverperiode`() {
        undersøke(12.U + 2.R + 2.F + 2.U)
        assertEquals(18, inspektør.size)
        assertEquals(14, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(2, inspektør.arbeidsdagTeller)
        assertEquals(2, inspektør.fridagTeller)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 18.januar,
                arbeidsgiverperiode = listOf(1.januar til 12.januar, 17.januar til 18.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = listOf(13.januar til 16.januar),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `avviser egenmeldingsdager utenfor arbeidsgiverperioden`() {
        undersøke(15.U + 1.F + 1.U)
        assertEquals(17, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(1, inspektør.avvistDagTeller)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 17.januar,
                arbeidsgiverperiode = listOf(1.januar til 16.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `avviser ikke egenmeldingsdager utenfor arbeidsgiverperioden som faller på helg`() {
        undersøke(3.A + 15.U + 1.F + 1.U)
        assertEquals(20, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(0, inspektør.avvistDagTeller)
        assertEquals(1, inspektør.navHelgDagTeller)
        assertEquals(3, inspektør.arbeidsdagTeller)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 4.januar til 20.januar,
                arbeidsgiverperiode = listOf(4.januar til 19.januar),
                utbetalingsperioder = listOf(20.januar.somPeriode()),
                oppholdsperioder = emptyList(),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `frisk helg gir opphold i arbeidsgiverperiode`() {
        undersøke(4.U + 8.S + 2.R + 2.F + 2.S)
        assertEquals(18, inspektør.size)
        assertEquals(14, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(2, inspektør.arbeidsdagTeller)
        assertEquals(2, inspektør.fridagTeller)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 18.januar,
                arbeidsgiverperiode = listOf(1.januar til 12.januar, 17.januar til 18.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = listOf(13.januar til 16.januar),
                fullstendig = false,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `spredt arbeidsgiverperiode`() {
        undersøke(10.S + 15.A + 7.S)
        assertEquals(32, inspektør.size)
        assertEquals(16, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(1, inspektør.navDagTeller)
        assertEquals(15, inspektør.arbeidsdagTeller)
        assertEquals(0, inspektør.fridagTeller)
        assertEquals(1, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 1.februar,
                arbeidsgiverperiode = listOf(1.januar til 10.januar, 26.januar til 31.januar),
                utbetalingsperioder = listOf(1.februar.somPeriode()),
                oppholdsperioder = listOf(11.januar til 25.januar),
                fullstendig = true,
                sisteDag = null
            ), perioder.single()
        )
    }

    @Test
    fun `nok opphold til å tilbakestille arbeidsgiverperiode`() {
        undersøke(10.S + 16.A + 7.S)
        assertEquals(33, inspektør.size)
        assertEquals(17, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(0, inspektør.navDagTeller)
        assertEquals(16, inspektør.arbeidsdagTeller)
        assertEquals(0, inspektør.fridagTeller)
        assertEquals(2, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 26.januar,
                arbeidsgiverperiode = listOf(1.januar til 10.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = listOf(11.januar til 26.januar),
                fullstendig = false,
                sisteDag = 26.januar
            ), perioder.first()
        )
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 27.januar til 2.februar,
                arbeidsgiverperiode = listOf(27.januar til 2.februar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.last()
        )
    }

    @Test
    fun `masse opphold`() {
        undersøke(10.S + 31.A + 7.S)
        assertEquals(48, inspektør.size)
        assertEquals(17, inspektør.arbeidsgiverperiodeDagTeller)
        assertEquals(0, inspektør.navDagTeller)
        assertEquals(31, inspektør.arbeidsdagTeller)
        assertEquals(0, inspektør.fridagTeller)
        assertEquals(2, perioder.size)
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 1.januar til 26.januar,
                arbeidsgiverperiode = listOf(1.januar til 10.januar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = listOf(11.januar til 26.januar),
                fullstendig = false,
                sisteDag = 26.januar
            ), perioder.first()
        )
        assertEquals(
            Arbeidsgiverperioderesultat(
                omsluttendePeriode = 11.februar til 17.februar,
                arbeidsgiverperiode = listOf(11.februar til 17.februar),
                utbetalingsperioder = emptyList(),
                oppholdsperioder = emptyList(),
                fullstendig = false,
                sisteDag = null
            ), perioder.last()
        )
    }

    @Test
    fun `avviser andre ytelser med riktige begrunnelser`() {
        undersøke(16.S + 1.YF + 1.YD + 1.YA + 1.YO + 1.YP + 1.YS + 1.YOL)
        assertEquals(7, inspektør.avvistDagTeller)
        assertEquals(Begrunnelse.AndreYtelserForeldrepenger, inspektør.begrunnelse(17.januar).single())
        assertEquals(Begrunnelse.AndreYtelserDagpenger, inspektør.begrunnelse(18.januar).single())
        assertEquals(Begrunnelse.AndreYtelserAap, inspektør.begrunnelse(19.januar).single())
        assertEquals(Begrunnelse.AndreYtelserOmsorgspenger, inspektør.begrunnelse(20.januar).single())
        assertEquals(Begrunnelse.AndreYtelserPleiepenger, inspektør.begrunnelse(21.januar).single())
        assertEquals(Begrunnelse.AndreYtelserSvangerskapspenger, inspektør.begrunnelse(22.januar).single())
        assertEquals(Begrunnelse.AndreYtelserOpplaringspenger, inspektør.begrunnelse(23.januar).single())
    }

    private lateinit var teller: Arbeidsgiverperiodeteller

    @BeforeEach
    fun setup() {
        reset()
    }

    private lateinit var inspektør: UtbetalingstidslinjeInspektør
    private lateinit var utbetalingstidslinje: Utbetalingstidslinje
    private val perioder: MutableList<Arbeidsgiverperioderesultat> = mutableListOf()

    private fun undersøke(tidslinje: Sykdomstidslinje, infotrygdBetalteDager: List<Periode> = emptyList()) {
        val arbeidsgiverperiodeberegner = Arbeidsgiverperiodeberegner(teller)
        val arbeidsgiverperioder = arbeidsgiverperiodeberegner.resultat(tidslinje, infotrygdBetalteDager)
        perioder.addAll(arbeidsgiverperioder)

        val builder = UtbetalingstidslinjeBuilderVedtaksperiode(
            faktaavklarteInntekter = ArbeidsgiverFaktaavklartInntekt(
                skjæringstidspunkt = 1.januar,
                `6G` = Grunnbeløp.`6G`.beløp(1.januar),
                fastsattÅrsinntekt = 31000.månedlig,
                gjelder = 1.januar til LocalDate.MAX
            ),
            regler = ArbeidsgiverRegler.Companion.NormalArbeidstaker,
            arbeidsgiverperiode = arbeidsgiverperioder.flatMap { it.arbeidsgiverperiode }.grupperSammenhengendePerioder(),
            refusjonstidslinje = tidslinje.periode()?.let { ARBEIDSGIVER.beløpstidslinje(it, 31000.månedlig) } ?: Beløpstidslinje()
        )

        utbetalingstidslinje = builder.result(tidslinje)
        inspektør = utbetalingstidslinje.inspektør
    }

    // undersøker forskjellige tidslinjer som skal ha samme funksjonelle betydning
    private fun undersøkeLike(vararg tidslinje: () -> Sykdomstidslinje, assertBlock: () -> Unit) {
        tidslinje.forEach {
            undersøke(resetSeed(tidslinjegenerator = it))
            assertBlock()
            reset()
        }
    }

    private fun reset() {
        resetSeed()
        teller = Arbeidsgiverperiodeteller.NormalArbeidstaker
        perioder.clear()
    }
}
