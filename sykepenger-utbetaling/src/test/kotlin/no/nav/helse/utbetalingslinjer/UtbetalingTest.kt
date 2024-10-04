package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.hendelser.AnnullerUtbetalingHendelse
import no.nav.helse.hendelser.Saksbehandler
import no.nav.helse.hendelser.SimuleringHendelse
import no.nav.helse.hendelser.UtbetalingHendelseHendelse
import no.nav.helse.hendelser.UtbetalingsavgjørelseHendelse
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.NAP
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.UKJ
import no.nav.helse.testhelpers.UTELATE
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AKSEPTERT
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AVVIST
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.tillaterOpprettelseAvUtbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.ANNULLERT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.FORKASTET
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.GODKJENT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.GODKJENT_UTEN_UTBETALING
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.IKKE_UTBETALT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.OVERFØRT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.UTBETALT
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class UtbetalingTest {

    private lateinit var aktivitetslogg: Aktivitetslogg

    private companion object {
        private const val UNG_PERSON_FNR_2018 = "12029240045"
        private const val ORGNUMMER = "987654321"
    }

    @BeforeEach
    internal fun initEach() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `forkaster annulleringer som utbetalingen peker på`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        utbetalingen.forkast(Aktivitetslogg())

        assertEquals(FORKASTET, annullering1.inspektør.tilstand)
        assertEquals(FORKASTET, annullering2.inspektør.tilstand)
        assertEquals(FORKASTET, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling erstatter flere uten utbetalinger`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        val hendelse = godkjenn(utbetalingen)
        assertFalse(hendelse.aktivitetslogg.harBehov(Behovtype.Utbetaling))
        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT_UTEN_UTBETALING, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling erstatter flere med utbetalinger`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon, linjer = listOf(
                Utbetalingslinje(1.februar, 15.februar, beløp = 500, grad = 10, delytelseId = 1, endringskode = ENDR, datoStatusFom = 1.februar)
            )),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        val hendelse = godkjenn(utbetalingen)
        assertEquals(1, hendelse.aktivitetslogg.behov.size)
        hendelse.aktivitetslogg.sisteBehov(Behovtype.Utbetaling).also { behov ->
            val detaljer = behov.detaljer()
            val kontekster = behov.kontekst()

            val oppdragInspektør = annullering1.inspektør.arbeidsgiverOppdrag.inspektør
            assertEquals(oppdragInspektør.mottaker, detaljer.getValue("mottaker"))
            assertEquals(oppdragInspektør.fagsystemId(), detaljer.getValue("fagsystemId"))

            assertEquals(annullering1.inspektør.utbetalingId.toString(), kontekster.getValue("utbetalingId"))
            assertEquals(oppdragInspektør.fagsystemId(), kontekster.getValue("fagsystemId"))
        }

        assertEquals(OVERFØRT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        kvittèr(annullering1)

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        kvittèr(annullering1, utbetalingmottaker = utbetalingen)

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT_UTEN_UTBETALING, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling erstatter flere med utbetalinger - med egne utbetalinger`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon, linjer = listOf(
                Utbetalingslinje(1.februar, 15.februar, beløp = 500, grad = 10, delytelseId = 1, endringskode = ENDR, datoStatusFom = 1.februar)
            )),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon, linjer = listOf(
                Utbetalingslinje(1.januar, 31.januar, beløp = 500, grad = 10, delytelseId = 1, endringskode = NY)
            )),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        val hendelse = godkjenn(utbetalingen)
        assertEquals(1, hendelse.aktivitetslogg.behov.size)
        hendelse.aktivitetslogg.sisteBehov(Behovtype.Utbetaling).also { behov ->
            val detaljer = behov.detaljer()
            val kontekster = behov.kontekst()

            val oppdragInspektør = annullering1.inspektør.arbeidsgiverOppdrag.inspektør
            assertEquals(oppdragInspektør.mottaker, detaljer.getValue("mottaker"))
            assertEquals(oppdragInspektør.fagsystemId(), detaljer.getValue("fagsystemId"))

            assertEquals(annullering1.inspektør.utbetalingId.toString(), kontekster.getValue("utbetalingId"))
            assertEquals(oppdragInspektør.fagsystemId(), kontekster.getValue("fagsystemId"))
        }

        assertEquals(OVERFØRT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        kvittèr(annullering1)

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        val kvitteringen = kvittèr(annullering1, utbetalingmottaker = utbetalingen)
        assertEquals(1, kvitteringen.aktivitetslogg.behov.size)
        kvitteringen.aktivitetslogg.sisteBehov(Behovtype.Utbetaling).also { behov ->
            val detaljer = behov.detaljer()
            val kontekster = behov.kontekst()

            val oppdragInspektør = utbetalingen.inspektør.arbeidsgiverOppdrag.inspektør
            assertEquals(oppdragInspektør.mottaker, detaljer.getValue("mottaker"))
            assertEquals(oppdragInspektør.fagsystemId(), detaljer.getValue("fagsystemId"))

            assertEquals(utbetalingen.inspektør.utbetalingId.toString(), kontekster.getValue("utbetalingId"))
            assertEquals(oppdragInspektør.fagsystemId(), kontekster.getValue("fagsystemId"))
        }

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(OVERFØRT, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling splitter opp tidligere utbetaling før`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV, 1.ARB, 27.NAV, 2.ARB, 29.NAV, startDato = 1.januar).betale()
        val utbetaling1 = opprettUtbetaling(tidslinje)

        val tidslinjeNy = tidslinjeOf(16.AP, 15.NAV, 18.ARB, 16.AP, 25.NAV, startDato = 1.januar).betale()
        val utbetaling2 = opprettUtbetaling(tidslinjeNy, utbetaling1, periode = 31.januar.somPeriode())

        val utbetaling1Inspektør = utbetaling1.inspektør
        val utbetaling2Inspektør = utbetaling2.inspektør

        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNNANHH NNNNNHH NNNNNHH NNNNNHH NNNAAHH NNNNNHH NNNNNHH NNNNNHH NNNNNH", utbetaling1Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 31.mars, utbetaling1Inspektør.utbetalingstidslinje.periode())
        assertEquals(3, utbetaling1Inspektør.arbeidsgiverOppdrag.size)
        utbetaling1Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
        utbetaling1Inspektør.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(2.februar til 28.februar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(2, linje.delytelseId)
            assertEquals(1, linje.refDelytelseId)
        }
        utbetaling1Inspektør.arbeidsgiverOppdrag[2].inspektør.also { linje ->
            assertEquals(3.mars til 30.mars, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(3, linje.delytelseId)
            assertEquals(2, linje.refDelytelseId)
        }

        assertEquals(utbetaling1Inspektør.korrelasjonsId, utbetaling2Inspektør.korrelasjonsId)
        assertEquals(utbetaling1Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), utbetaling2Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", utbetaling2Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 31.januar, utbetaling2Inspektør.utbetalingstidslinje.periode())
        assertEquals(1, utbetaling2Inspektør.arbeidsgiverOppdrag.size)
        utbetaling2Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(4, linje.delytelseId)
            assertEquals(3, linje.refDelytelseId)
        }
    }

    @Test
    fun `utbetalinger med ulik korrelasjonsId kan ikke overlappe`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val utbetaling2 = opprettUtbetaling(tidslinje)
        assertFalse(listOf(utbetaling).tillaterOpprettelseAvUtbetaling(utbetaling2))
    }

    @Test
    fun `utbetalinger med ulik korrelasjonsId kan overlappe hvis det er annullering`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val utbetaling2 = annuller(opprettUtbetaling(tidslinje))
        assertTrue(listOf(utbetaling).tillaterOpprettelseAvUtbetaling(utbetaling2!!))
    }

    @Test
    fun `utbetalinger med samme korrelasjonsId kan overlappe`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val tidslinje2 = tidslinjeOf(16.AP, 30.NAV).betale()
        val utbetaling2 = opprettUtbetaling(tidslinje2, tidligere = utbetaling)
        assertTrue(listOf(utbetaling).tillaterOpprettelseAvUtbetaling(utbetaling2))
    }

    @Test
    fun `tom utbetaling slutter før tidligere`() {
        // periode med 1 SykNav + 1 AGP dag
        val tidslinje1 = tidslinjeOf(31.UTELATE, 1.NAP, 1.AP).betale()
        val utbetaling1 = opprettUtbetaling(tidslinje1, periode = 1.februar til 2.februar)
        // perioden strekkes tilbake med 31 Foreldrepengedager, og 2 AGP dager etter
        val tidslinje2 = tidslinjeOf(31.FRI, 2.AP).betale()
        val utbetaling2 = opprettUtbetaling(tidslinje2, tidligere = utbetaling1, periode = 1.januar til 2.februar)

        val utbetaling1Inspektør = utbetaling1.inspektør
        val utbetaling2Inspektør = utbetaling2.inspektør

        assertEquals("?P", utbetaling1Inspektør.utbetalingstidslinje.toString())
        assertEquals("FFFFFFF FFFFFFF FFFFFFF FFFFFFF FFFPP", utbetaling2Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.februar til 2.februar, utbetaling1Inspektør.utbetalingstidslinje.periode())
        assertEquals(1.januar til 2.februar, utbetaling2Inspektør.utbetalingstidslinje.periode())
        assertEquals(utbetaling2Inspektør.korrelasjonsId, utbetaling1Inspektør.korrelasjonsId)

        assertEquals(1, utbetaling1Inspektør.arbeidsgiverOppdrag.size)
        utbetaling1Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(1.februar til 1.februar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
        assertEquals(1, utbetaling2Inspektør.arbeidsgiverOppdrag.size)
        utbetaling2Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(1.februar til 1.februar, linje.fom til linje.tom)
            assertEquals(ENDR, linje.endringskode)
            assertEquals("OPPH", linje.statuskode)
            assertEquals(1.februar, linje.datoStatusFom)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
    }

    @Test
    fun `utbetaling starter i Ny`() {
        val tidslinje = tidslinjeOf(16.AP, 17.NAV).betale()

        val sisteDato = 21.januar
        val utbetaling = Utbetaling.lagUtbetaling(
            emptyList(),
            UNG_PERSON_FNR_2018,
            ORGNUMMER,
            tidslinje,
            sisteDato.somPeriode(),
            listOf(Utbetalingsak(1.januar, listOf(sisteDato.somPeriode()))),
            aktivitetslogg,
            LocalDate.MAX,
            100,
            148
        ).first
        assertEquals(Utbetalingstatus.NY, utbetaling.inspektør.tilstand)
        utbetaling.opprett(aktivitetslogg)
        assertEquals(IKKE_UTBETALT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `utbetalinger inkluderer ikke dager etter siste dato`() {
        val tidslinje = tidslinjeOf(16.AP, 17.NAV).betale()

        val sisteDato = 21.januar
        val utbetaling = Utbetaling.lagUtbetaling(
            emptyList(),
            UNG_PERSON_FNR_2018,
            ORGNUMMER,
            tidslinje,
            sisteDato.somPeriode(),
            listOf(Utbetalingsak(1.januar, listOf(sisteDato.somPeriode()))),
            aktivitetslogg,
            LocalDate.MAX,
            100,
            148
        ).first.also { it.opprett(aktivitetslogg) }
        assertEquals(1.januar til sisteDato, utbetaling.inspektør.utbetalingstidslinje.periode())
        assertEquals(1.januar til sisteDato, utbetaling.inspektør.periode)
    }

    @Test
    fun `forlenger seg ikke på en annullering`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV).betale()
        val første = opprettUtbetaling(tidslinje)
        val annullering = annuller(første) ?: fail { "forventet utbetaling" }
        val andre = opprettUtbetaling(tidslinje, annullering)
        assertNotEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertNotEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
    }

    @Test
    fun `omgjøre en revurdert periode med opphør`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV)
        val ferietidslinje = tidslinjeOf(16.AP, 10.FRI)
        val første = opprettUtbetaling(tidslinje.betale())
        val andre = opprettUtbetaling(ferietidslinje.betale(), første)
        val tredje = opprettUtbetaling(tidslinje.betale(), andre)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertTrue(andre.inspektør.arbeidsgiverOppdrag[0].erOpphør())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
        assertEquals(17.januar, tredje.inspektør.arbeidsgiverOppdrag.first().inspektør.fom)
        assertEquals(26.januar, tredje.inspektør.arbeidsgiverOppdrag.last().inspektør.tom)
        assertEquals(17.januar, tredje.inspektør.arbeidsgiverOppdrag.inspektør.periode?.start)
    }

    @Test
    fun `forlenge en utbetaling uten utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV).betale()
        val første = opprettUtbetaling(tidslinje.fremTilOgMed(16.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertTrue(første.inspektør.arbeidsgiverOppdrag.isEmpty())
        assertTrue(andre.inspektør.arbeidsgiverOppdrag.isNotEmpty())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertEquals(NY, andre.inspektør.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(NY, andre.inspektør.arbeidsgiverOppdrag[0].inspektør.endringskode)
        assertEquals(17.januar, andre.inspektør.arbeidsgiverOppdrag[0].inspektør.fom)
        assertEquals(26.januar, andre.inspektør.arbeidsgiverOppdrag[0].inspektør.tom)
    }

    @Test
    fun `forlenger seg på en revurdert periode med opphør`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV).betale()
        val ferietidslinje = tidslinjeOf(16.AP, 10.FRI)
        val første = opprettUtbetaling(tidslinje)
        val andre = opprettUtbetaling(ferietidslinje.betale(), første)
        val tredje = opprettUtbetaling(beregnUtbetalinger(ferietidslinje + tidslinjeOf(10.NAV, startDato = 27.januar)), andre)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertTrue(andre.inspektør.arbeidsgiverOppdrag[0].erOpphør())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
        assertEquals(27.januar, tredje.inspektør.arbeidsgiverOppdrag.first().inspektør.fom)
        assertNull(tredje.inspektør.arbeidsgiverOppdrag.first().inspektør.datoStatusFom)
        assertEquals(5.februar, tredje.inspektør.arbeidsgiverOppdrag.last().inspektør.tom)
        assertEquals(27.januar, tredje.inspektør.arbeidsgiverOppdrag.inspektør.periode?.start)
    }

    @Test
    fun `setter refFagsystemId og refDelytelseId når en linje peker på en annen`() {
        val tidslinje = tidslinjeOf(
            16.AP, 3.NAV, 5.NAV(1200, 50.0),
            startDato = 1.januar(2020)
        ).betale()

        val tidligere = opprettUtbetaling(tidslinje.fremTilOgMed(19.januar(2020)))
        val utbetaling = opprettUtbetaling(tidslinje.fremTilOgMed(24.januar(2020)), tidligere = tidligere)

        assertEquals(tidligere.inspektør.korrelasjonsId, utbetaling.inspektør.korrelasjonsId)
        utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.also { inspektør ->
            assertEquals(2, inspektør.antallLinjer())
            assertNull(inspektør.refDelytelseId(0))
            assertNull(inspektør.refFagsystemId(0))
            assertNotNull(inspektør.refDelytelseId(1))
            assertNotNull(inspektør.refFagsystemId(1))
        }
    }

    @Test
    fun `kan forkaste uten utbetalinger`() {
        assertTrue(Utbetaling.kanForkastes(emptyList(), emptyList()))
    }

    @Test
    fun `kan forkaste ubetalt utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan forkaste underkjent utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        godkjenn(utbetaling, false)
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan forkaste forkastet utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        utbetaling.forkast(Aktivitetslogg())
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan ikke forkaste utbetaling i spill`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan ikke forkaste feilet utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), status = AVVIST)
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan forkaste annullert utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val annullering = annuller(utbetaling) ?: fail { "Kunne ikke annullere" }
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(annullering)))
    }

    @Test
    fun `kan forkaste utbetalt utbetaling dersom den er annullert`() {
        val tidslinje = tidslinjeOf(16.AP, 32.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje.fremTilOgMed(31.januar))
        val annullert = opprettUtbetaling(tidslinje.fremTilOgMed(17.februar), tidligere = utbetaling).let {
            annuller(it)
        } ?: fail { "Kunne ikke annullere" }
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(annullert)))
    }

    @Test
    fun `går ikke videre når ett av to oppdrag er akseptert`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling)
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `går videre når begge oppdragene er akseptert`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling)
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId)
        assertEquals(UTBETALT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `utbetalingen er feilet dersom én av oppdragene er avvist 1`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), AVVIST)
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `tar imot kvittering på det andre oppdraget selv om utbetalingen har feilet`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), AVVIST)
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId, AKSEPTERT)
        assertEquals(AVVIST, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.status())
        assertEquals(AKSEPTERT, utbetaling.inspektør.personOppdrag.inspektør.status())
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `utbetalingen er feilet dersom én av oppdragene er avvist 2`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), AKSEPTERT)
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId, AVVIST)
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        assertTrue(utbetaling.harDelvisRefusjon())
    }

    @Test
    fun `annullere delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val annullering = annuller(utbetaling) ?: fail { "forventet utbetaling" }
        assertTrue(annullering.inspektør.arbeidsgiverOppdrag.last().erOpphør())
        assertTrue(annullering.inspektør.personOppdrag.last().erOpphør())
    }

    @Test
    fun `annullere utbetaling med full refusjon, så null refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 5.NAV, 10.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val første = opprettUtbetaling(tidslinje.fremTilOgMed(21.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        val annullering = annuller(andre) ?: fail { "forventet utbetaling" }
        assertTrue(annullering.inspektør.arbeidsgiverOppdrag.last().erOpphør())
        assertTrue(annullering.inspektør.personOppdrag.last().erOpphør())
    }

    @Test
    fun `null refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 30.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)).betale()
        val første = opprettUtbetaling(tidslinje.fremTilOgMed(31.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertFalse(første.harDelvisRefusjon())
        assertTrue(første.harUtbetalinger())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
    }

    @Test
    fun `korrelasjonsId er lik på brukerutbetalinger direkte fra Infotrygd`() {
        val tidslinje =
            beregnUtbetalinger(tidslinjeOf(5.UKJ, 26.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)))
        val første = opprettUtbetaling(tidslinje.fremTilOgMed(21.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertEquals(første.inspektør.personOppdrag.fagsystemId, andre.inspektør.personOppdrag.fagsystemId)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
    }

    @Test
    fun `simulerer ingen refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        val simulering = opprettSimulering(
            utbetaling.inspektør.personOppdrag.fagsystemId, Fagområde.Sykepenger, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 1000,
                perioder = emptyList()
            )
        )
        utbetaling.håndter(simulering)
        assertNotNull(utbetaling.inspektør.personOppdrag.inspektør.simuleringsResultat())
        assertNull(utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.simuleringsResultat())
    }

    @Test
    fun `simulerer full refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 1000)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        val simulering = opprettSimulering(
            utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), Fagområde.SykepengerRefusjon, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 1000,
                perioder = emptyList()
            )
        )
        utbetaling.håndter(simulering)
        assertNotNull(utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.simuleringsResultat())
        assertNull(utbetaling.inspektør.personOppdrag.inspektør.simuleringsResultat())
    }

    @Test
    fun `simulerer delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 500)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)

        val simuleringArbeidsgiver = opprettSimulering(
            utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), Fagområde.SykepengerRefusjon, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 500,
                perioder = emptyList()
            )
        )
        val simuleringPerson = opprettSimulering(
            utbetaling.inspektør.personOppdrag.fagsystemId, Fagområde.Sykepenger, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 500,
                perioder = emptyList()
            )
        )
        utbetaling.håndter(simuleringArbeidsgiver)
        utbetaling.håndter(simuleringPerson)

        assertNotNull(utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.simuleringsResultat())
        assertNotNull(utbetaling.inspektør.personOppdrag.inspektør.simuleringsResultat())
    }

    private fun opprettSimulering(fagsystemId: String, fagområde: Fagområde, utbetalingId: UUID, simuleringResultat: SimuleringResultatDto? = null) =
        Simulering(
            utbetalingId = utbetalingId,
            fagsystemId = fagsystemId,
            fagområde = fagområde,
            simuleringsResultat = simuleringResultat
        )

    private fun Utbetalingstidslinje.betale() = beregnUtbetalinger(this)
    private fun beregnUtbetalinger(tidslinje: Utbetalingstidslinje) = Utbetalingstidslinje.betale(listOf(tidslinje)).single()

    private fun opprettGodkjentUtbetaling(
        tidslinje: Utbetalingstidslinje,
        periode: Periode = tidslinje.periode(),
        fødselsnummer: String = UNG_PERSON_FNR_2018,
        orgnummer: String = ORGNUMMER,
        utbetalingsaker: List<Utbetalingsak> = listOf(Utbetalingsak(tidslinje.periode().start, listOf(periode))),
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg
    ) = opprettUbetaltUtbetaling(tidslinje, null, periode, fødselsnummer, orgnummer, utbetalingsaker, aktivitetslogg)
        .also { godkjenn(it) }
    private fun opprettGodkjentUtbetaling() =
        opprettGodkjentUtbetaling(beregnUtbetalinger(tidslinjeOf(16.AP, 5.NAV(3000))))

    private fun opprettUbetaltUtbetaling(
        tidslinje: Utbetalingstidslinje,
        tidligere: Utbetaling? = null,
        periode: Periode = tidslinje.periode(),
        fødselsnummer: String = UNG_PERSON_FNR_2018,
        orgnummer: String = ORGNUMMER,
        utbetalingsaker: List<Utbetalingsak> = listOf(Utbetalingsak(tidslinje.periode().start, listOf(periode))),
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg
    ) = Utbetaling.lagUtbetaling(
        tidligere?.let { listOf(tidligere) } ?: emptyList(),
        fødselsnummer,
        orgnummer,
        tidslinje,
        periode,
        utbetalingsaker,
        aktivitetslogg,
        LocalDate.MAX,
        100,
        148
    ).first.also { it.opprett(aktivitetslogg) }

    private fun opprettUtbetaling(
        tidslinje: Utbetalingstidslinje,
        tidligere: Utbetaling? = null,
        periode: Periode = tidslinje.periode(),
        fødselsnummer: String = UNG_PERSON_FNR_2018,
        orgnummer: String = ORGNUMMER,
        utbetalingsaker: List<Utbetalingsak> = listOf(Utbetalingsak(tidslinje.periode().start, listOf(periode))),
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg
    ) = opprettUbetaltUtbetaling(tidslinje, tidligere, periode, fødselsnummer, orgnummer, utbetalingsaker, aktivitetslogg).also { utbetaling ->
        godkjenn(utbetaling)
        listOf(utbetaling.inspektør.arbeidsgiverOppdrag, utbetaling.inspektør.personOppdrag)
            .filter { it.harUtbetalinger() }
            .map { it.fagsystemId }
            .onEach { kvittèr(utbetaling, it) }
    }

    private fun kvittèr(
        utbetaling: Utbetaling,
        fagsystemId: String = utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(),
        status: Oppdragstatus = AKSEPTERT,
        utbetalingmottaker: Utbetaling = utbetaling
    ): Kvittering {
        val hendelsen = Kvittering(
            fagsystemId = fagsystemId,
            utbetalingId = utbetaling.inspektør.utbetalingId,
            status = status
        )
        utbetalingmottaker.håndter(hendelsen)
        return hendelsen
    }

    private fun godkjenn(utbetaling: Utbetaling, utbetalingGodkjent: Boolean = true) =
        Utbetalingsgodkjenning(
            utbetalingId = utbetaling.inspektør.utbetalingId,
            godkjent = utbetalingGodkjent
        ).also {
            utbetaling.håndter(it)
        }

    private fun annuller(utbetaling: Utbetaling, utbetalingId: UUID = utbetaling.inspektør.utbetalingId) =
        utbetaling.annuller(
            hendelse = AnnullerUtbetaling(utbetalingId),
            alleUtbetalinger = listOf(utbetaling)
        )?.also {
            it.opprett(aktivitetslogg)
        }

    private fun Aktivitetslogg.sisteBehov(type: Behovtype) =
        behov.last { it.type == type }

    private fun Aktivitetslogg.harBehov(behov: Behovtype) =
        this.behov.any { it.type == behov }

    private class Utbetalingsgodkjenning(
        override val utbetalingId: UUID,
        override val godkjent: Boolean,
        val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
    ) : UtbetalingsavgjørelseHendelse, IAktivitetslogg by (aktivitetslogg) {
        override fun saksbehandler(): Saksbehandler = Saksbehandler("Z999999", "mille.mellomleder@nav.no")

        override val avgjørelsestidspunkt: LocalDateTime = LocalDateTime.now()
        override val automatisert: Boolean = false
    }

    private class Kvittering(
        override val fagsystemId: String,
        override val utbetalingId: UUID,
        override val status: Oppdragstatus,
        val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
    ) : UtbetalingHendelseHendelse, IAktivitetslogg by aktivitetslogg {
        override val avstemmingsnøkkel: Long = 123456789L
        override val overføringstidspunkt: LocalDateTime = LocalDateTime.now()
        override val melding: String = ""
    }

    private class AnnullerUtbetaling(
        override val utbetalingId: UUID,
        val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
    ) : AnnullerUtbetalingHendelse, IAktivitetslogg by aktivitetslogg {
        override val vurdering: Utbetaling.Vurdering = Utbetaling.Vurdering(true, "Z999999", "utbetaling@nav.no", LocalDateTime.now(), false)
    }

    private class Simulering(
        override val utbetalingId: UUID,
        override val fagsystemId: String,
        override val fagområde: Fagområde,
        override val simuleringsResultat: SimuleringResultatDto?,
        val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
    ) : SimuleringHendelse, IAktivitetslogg by aktivitetslogg {
        override val simuleringOK: Boolean = true
        override val melding: String = "OK"
    }
}

private val frø = LocalDate.of(2018, 1, 1)
val Int.januar get() = frø.withMonth(1).withDayOfMonth(this)
val Int.februar get() = frø.withMonth(2).withDayOfMonth(this)
val Int.mars get() = frø.withMonth(3).withDayOfMonth(this)
val Int.desember get() = frø.withMonth(12).withDayOfMonth(this)