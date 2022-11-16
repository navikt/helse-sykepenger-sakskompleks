package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.Refusjonsopplysning.Refusjonsopplysninger.Companion.gjennopprett
import no.nav.helse.person.Refusjonsopplysning.Refusjonsopplysninger.Companion.refusjonsopplysninger
import no.nav.helse.person.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode.Companion.harNødvendigeRefusjonsopplysninger
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RefusjonsopplysningerTest {

    @Test
    fun `ny refusjonsopplysning i midten av eksisterende`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(
            Refusjonsopplysning(
                meldingsreferanseId1,
                1.januar,
                31.januar,
                2000.daglig
            )
        ).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(
            Refusjonsopplysning(
                meldingsreferanseId2,
                15.januar,
                20.januar,
                1000.daglig
            )
        ).refusjonsopplysninger()

        assertEquals(
            listOf(
                Refusjonsopplysning(meldingsreferanseId1, 1.januar, 14.januar, 2000.daglig),
                Refusjonsopplysning(meldingsreferanseId2, 15.januar, 20.januar, 1000.daglig),
                Refusjonsopplysning(meldingsreferanseId1, 21.januar, 31.januar, 2000.daglig)
            ),
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `ny refusjonsopplysning uten tom`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId1, 1.januar, 31.januar, 2000.daglig)).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId2, 15.januar, null, 1000.daglig)).refusjonsopplysninger()

        assertEquals(
            listOf(
                Refusjonsopplysning(meldingsreferanseId1, 1.januar, 14.januar, 2000.daglig),
                Refusjonsopplysning(meldingsreferanseId2, 15.januar, null, 1000.daglig)
            ),
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `ny refusjonsopplysning erstatter gamle`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId1, 2.januar, 30.januar, 2000.daglig)).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId2, 1.januar, 31.januar, 1000.daglig)).refusjonsopplysninger()

        assertEquals(
            nyeRefusjonsopplysninger.inspektør.refusjonsopplysninger,
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `ny refusjonsopplysning uten tom erstatter gammel uten tom`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId1, 1.januar, null, 2000.daglig)).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId2, 1.januar, null, 1000.daglig)).refusjonsopplysninger()

        assertEquals(
            nyeRefusjonsopplysninger.inspektør.refusjonsopplysninger,
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `ny refusjonsopplysning uten tom legges på eksisterende uten tom`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId1, 1.januar, null, 2000.daglig)).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId2, 1.mars, null, 1000.daglig)).refusjonsopplysninger()

        assertEquals(
            listOf(Refusjonsopplysning(meldingsreferanseId1, 1.januar, 28.februar, 2000.daglig), Refusjonsopplysning(meldingsreferanseId2, 1.mars, null, 1000.daglig)),
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `ny refusjonsopplysning uten tom som starter tidligere enn forrige`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId1, 1.mars, null, 2000.daglig)).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId2, 1.januar, null, 1000.daglig)).refusjonsopplysninger()

        assertEquals(
            nyeRefusjonsopplysninger.inspektør.refusjonsopplysninger,
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `perfekt overlapp - bruker nye opplysninger`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId1, 1.januar, 1.mars, 2000.daglig)).refusjonsopplysninger()
        val meldingsreferanseId2 = UUID.randomUUID()
        val nyeRefusjonsopplysninger = listOf(Refusjonsopplysning(meldingsreferanseId2, 1.januar, 1.mars, 1000.daglig)).refusjonsopplysninger()

        assertEquals(
            nyeRefusjonsopplysninger.inspektør.refusjonsopplysninger,
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `nye opplysninger erstatter deler av eksisterende opplysninger`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val meldingsreferanseId2 = UUID.randomUUID()
        val meldingsreferanseId3 = UUID.randomUUID()
        val meldingsreferanseId4 = UUID.randomUUID()
        val meldingsreferanseId5 = UUID.randomUUID()
        val meldingsreferanseId6 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(
            Refusjonsopplysning(meldingsreferanseId1, 1.januar, 5.januar, 2000.daglig),
            Refusjonsopplysning(meldingsreferanseId2, 6.januar, 11.januar, 3000.daglig),
            Refusjonsopplysning(meldingsreferanseId3, 12.januar, 17.januar, 4000.daglig),
        ).refusjonsopplysninger()
        val nyeRefusjonsopplysninger = listOf(
            Refusjonsopplysning(meldingsreferanseId4, 2.januar, 4.januar, 5000.daglig),
            Refusjonsopplysning(meldingsreferanseId5, 7.januar, 10.januar, 6000.daglig),
            Refusjonsopplysning(meldingsreferanseId6, 13.januar, 16.januar, 7000.daglig),
        ).refusjonsopplysninger()

        assertEquals(
            listOf(
                Refusjonsopplysning(meldingsreferanseId1, 1.januar, 1.januar, 2000.daglig),
                Refusjonsopplysning(meldingsreferanseId4, 2.januar, 4.januar, 5000.daglig),
                Refusjonsopplysning(meldingsreferanseId1, 5.januar, 5.januar, 2000.daglig),
                Refusjonsopplysning(meldingsreferanseId2, 6.januar, 6.januar, 3000.daglig),
                Refusjonsopplysning(meldingsreferanseId5, 7.januar, 10.januar, 6000.daglig),
                Refusjonsopplysning(meldingsreferanseId2, 11.januar, 11.januar, 3000.daglig),
                Refusjonsopplysning(meldingsreferanseId3, 12.januar, 12.januar, 4000.daglig),
                Refusjonsopplysning(meldingsreferanseId6, 13.januar, 16.januar, 7000.daglig),
                Refusjonsopplysning(meldingsreferanseId3, 17.januar, 17.januar, 4000.daglig)
            ),
            refusjonsopplysninger.merge(nyeRefusjonsopplysninger).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `merging av to tomme refusjonsopplysninger blir en tom refusjonsopplysning`() {
        assertEquals(
            Refusjonsopplysninger().inspektør.refusjonsopplysninger,
            Refusjonsopplysninger().merge(Refusjonsopplysninger()).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `perioder kant i kant hvor siste periode har tom null`() {
        val meldingsreferanseId = UUID.randomUUID()
        val originaleRefusjonsopplysninger = listOf(
            Refusjonsopplysning(meldingsreferanseId, 1.januar, 19.januar, 1000.månedlig),
            Refusjonsopplysning(meldingsreferanseId, 20.januar, 24.januar, 500.månedlig),
            Refusjonsopplysning(meldingsreferanseId, 25.januar, 28.februar, 2000.månedlig),
            Refusjonsopplysning(meldingsreferanseId, 1.mars, 19.mars, 999.månedlig),
            Refusjonsopplysning(meldingsreferanseId, 20.mars, 24.mars, 99.månedlig),
            Refusjonsopplysning(meldingsreferanseId, 25.mars, null, 9.månedlig)
        )

        assertEquals(
            originaleRefusjonsopplysninger,
            Refusjonsopplysninger(originaleRefusjonsopplysninger, LocalDateTime.now()).inspektør.refusjonsopplysninger
        )
    }

    @Test
    fun `senere periode uten tom`() {
        val meldingsreferanseId1 = UUID.randomUUID()
        val meldingsreferanseId2 = UUID.randomUUID()
        val refusjonsopplysninger = listOf(
            Refusjonsopplysning(meldingsreferanseId1, 1.januar, 31.januar, 2000.daglig),
            Refusjonsopplysning(meldingsreferanseId2, 1.mars, null, 2000.daglig)
        )

        assertEquals(refusjonsopplysninger, Refusjonsopplysninger(refusjonsopplysninger, LocalDateTime.now()).inspektør.refusjonsopplysninger)
    }

    @Test
    fun `ny opplysning før oss`() {
        val eksisterende = Refusjonsopplysning(UUID.randomUUID(), 1.mars, 31.mars, 2000.daglig)
        val ny = Refusjonsopplysning(UUID.randomUUID(), 1.januar, 15.februar, 2000.daglig)
        assertEquals(listOf(ny, eksisterende), Refusjonsopplysninger(listOf(eksisterende, ny), LocalDateTime.now()).inspektør.refusjonsopplysninger)
    }

    @Test
    fun `ny opplysning før med overlapp`() {
        val eksisterendeId = UUID.randomUUID()
        val eksisterendeTidspunkt = LocalDateTime.now()
        val nyttTidspunkt = eksisterendeTidspunkt.plusSeconds(1)
        val eksisterende = Refusjonsopplysning(eksisterendeId, 1.mars, 31.mars, 2000.daglig)
        val ny = Refusjonsopplysning(UUID.randomUUID(), 1.januar, 1.mars, 1000.daglig)
        val refusjonsopplysning = RefusjonsopplysningerBuilder().leggTil(eksisterende, eksisterendeTidspunkt).leggTil(ny, nyttTidspunkt).build()
        assertEquals(listOf(ny, Refusjonsopplysning(eksisterendeId, 2.mars, 31.mars, 2000.daglig)), refusjonsopplysning.inspektør.refusjonsopplysninger)

    }

    @Test
    fun `håndterer å ta inn refusjonsopplysninger hulter til bulter`() {
        val eksisterendeTidspunkt = LocalDateTime.now()
        val nyttTidspunkt = eksisterendeTidspunkt.plusSeconds(1)
        val ny = Refusjonsopplysning(UUID.randomUUID(), 1.januar, 1.mars, 2000.daglig)
        val eksisterende = Refusjonsopplysning(UUID.randomUUID(), 1.mars, 31.mars, 2000.daglig)
        val eksisterendeFørst = RefusjonsopplysningerBuilder().leggTil(eksisterende, eksisterendeTidspunkt).leggTil(ny, nyttTidspunkt).build()
        val nyFørst = RefusjonsopplysningerBuilder().leggTil(ny, nyttTidspunkt).leggTil(eksisterende, eksisterendeTidspunkt).build()
        assertEquals(eksisterendeFørst, nyFørst)
    }

    @Test
    fun `refusjonsopplysninger med samme tidspunkt sorteres på fom`() {
        val tidspunkt = LocalDateTime.now()
        val januar = Refusjonsopplysning(UUID.randomUUID(), 1.januar, 1.mars, 2000.daglig)
        val mars = Refusjonsopplysning(UUID.randomUUID(), 1.mars, 31.mars, 2000.daglig)
        val marsFørst = RefusjonsopplysningerBuilder().leggTil(mars, tidspunkt).leggTil(januar,tidspunkt).build()
        val januarFørst = RefusjonsopplysningerBuilder().leggTil(januar, tidspunkt).leggTil(mars, tidspunkt).build()
        assertEquals(marsFørst, januarFørst)
    }

    @Test
    fun `har refusjonsopplysninger for forventede dager`() {
        val januar = Refusjonsopplysning(UUID.randomUUID(), 2.januar, 31.januar, 2000.daglig)
        val refusjonsopplysninger = januar.refusjonsopplysninger
        assertFalse(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(listOf(1.januar)))
        assertTrue(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(listOf(2.januar)))
        assertTrue(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(listOf(31.januar)))
        assertFalse(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(listOf(1.februar)))
        assertTrue(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(2.januar til 31.januar))
        assertTrue(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(3.januar til 30.januar))
        assertFalse(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(1.januar til 31.januar))
        assertFalse(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(2.januar til 1.februar))
        assertFalse(refusjonsopplysninger.harNødvendigRefusjonsopplysninger(31.januar til 28.februar))
    }

    @Test
    fun `kan gjenopprette refusjonsopplysninger som ikke overlapper`() {
        assertGjenopprettetRefusjonsopplysninger(emptyList())
        assertGjenopprettetRefusjonsopplysninger(listOf(Refusjonsopplysning(UUID.randomUUID(), 1.januar, 31.januar, 1000.daglig)))
        assertGjenopprettetRefusjonsopplysninger(listOf(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 1000.daglig)))
        assertGjenopprettetRefusjonsopplysninger(listOf(
            Refusjonsopplysning(UUID.randomUUID(), 1.januar, 31.januar, 1000.daglig),
            Refusjonsopplysning(UUID.randomUUID(), 1.mars, 31.mars, 2000.daglig),
            Refusjonsopplysning(UUID.randomUUID(), 1.april, null, 3000.daglig),
        ))
    }

    @Test
    fun `kan ikke gjenopprette refusjonsopplysningr som overlapper`() {
        assertThrows<IllegalStateException> { listOf(
            Refusjonsopplysning(UUID.randomUUID(), 1.januar, 31.januar, 1000.daglig),
            Refusjonsopplysning(UUID.randomUUID(), 1.mars, null, 2000.daglig),
            Refusjonsopplysning(UUID.randomUUID(), 1.april, 30.april, 3000.daglig),
        ).gjennopprett() }

        assertThrows<IllegalStateException> { listOf(
            Refusjonsopplysning(UUID.randomUUID(), 1.januar, 31.januar, 1000.daglig),
            Refusjonsopplysning(UUID.randomUUID(), 1.januar, 28.februar, 2000.daglig)
        ).gjennopprett() }
    }

    @Test
    fun `Har ikke nødvendig refusjonsopplysninger etter oppholdsdager`() {
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(1.januar til 16.januar)).apply {
            (17.januar til 25.januar).forEach { utbetalingsdag(it) }
            (26.januar til 31.januar).forEach { oppholdsdag(it) }
            (1.februar til 28.februar).forEach { utbetalingsdag(it) }
        }
        val refusjonsopplysninger = Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 20000.månedlig).refusjonsopplysninger
        assertTrue(harNødvendigeRefusjonsopplysninger(1.januar til 31.januar, refusjonsopplysninger, arbeidsgiverperiode))
        assertFalse(harNødvendigeRefusjonsopplysninger(1.februar til 28.februar, refusjonsopplysninger, arbeidsgiverperiode))
    }

    @Test
    fun `Har nødvendige refusjonsopplysninger når vi får nye refusjonsopplysninger for perioden etter oppholdet`() {
        val arbeidsgiverperiode = Arbeidsgiverperiode(listOf(1.januar til 16.januar)).apply {
            (17.januar til 25.januar).forEach { utbetalingsdag(it) }
            (26.januar til 31.januar).forEach { oppholdsdag(it) }
            (1.februar til 28.februar).forEach { utbetalingsdag(it) }
        }
        // IM: FF = 1.januar, AGP = 1.januar - 16.januar
        val refusjonsopplysningerJanuar = Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 20000.månedlig).refusjonsopplysninger
        assertTrue(harNødvendigeRefusjonsopplysninger(1.januar til 31.januar, refusjonsopplysningerJanuar, arbeidsgiverperiode))
        assertFalse(harNødvendigeRefusjonsopplysninger(1.februar til 28.februar, refusjonsopplysningerJanuar, arbeidsgiverperiode))

        // IM: FF = 1.februar, AGP = 1.januar - 16.januar
        val refusjonsopplysningerFebruar = Refusjonsopplysning(UUID.randomUUID(), 1.februar, null, 25000.månedlig).refusjonsopplysninger

        val oppdaterteRefusjonsopplysninger = refusjonsopplysningerJanuar.merge(refusjonsopplysningerFebruar)
        assertTrue(harNødvendigeRefusjonsopplysninger(1.januar til 31.januar, oppdaterteRefusjonsopplysninger, arbeidsgiverperiode))
        assertTrue(harNødvendigeRefusjonsopplysninger(1.februar til 28.februar, oppdaterteRefusjonsopplysninger, arbeidsgiverperiode))
    }

    internal companion object {
        private fun harNødvendigeRefusjonsopplysninger(periode: Periode, refusjonsopplysninger: Refusjonsopplysninger, arbeidsgiverperiode: Arbeidsgiverperiode) =
            harNødvendigeRefusjonsopplysninger(periode, refusjonsopplysninger, arbeidsgiverperiode, Aktivitetslogg(), "")
        private fun assertGjenopprettetRefusjonsopplysninger(refusjonsopplysninger: List<Refusjonsopplysning>) {
            assertEquals(refusjonsopplysninger, refusjonsopplysninger.gjennopprett().inspektør.refusjonsopplysninger)
        }
        private fun Refusjonsopplysninger.harNødvendigRefusjonsopplysninger(dager: List<LocalDate>) = harNødvendigRefusjonsopplysninger(dager, null, Aktivitetslogg(), "")
        private fun Refusjonsopplysninger.harNødvendigRefusjonsopplysninger(periode: Periode) = harNødvendigRefusjonsopplysninger(periode.toList(), null, Aktivitetslogg(), "")
        private fun List<Refusjonsopplysning>.refusjonsopplysninger() = Refusjonsopplysninger(this, LocalDateTime.now())

        private fun Refusjonsopplysninger(refusjonsopplysninger: List<Refusjonsopplysning>, tidsstempel: LocalDateTime): Refusjonsopplysninger{
            val refusjonsopplysningerBuilder = RefusjonsopplysningerBuilder()
            refusjonsopplysninger.forEach { refusjonsopplysningerBuilder.leggTil(it, tidsstempel) }
            return refusjonsopplysningerBuilder.build()
        }
    }
}