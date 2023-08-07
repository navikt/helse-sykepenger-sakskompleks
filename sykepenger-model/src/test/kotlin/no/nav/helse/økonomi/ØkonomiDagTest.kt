package no.nav.helse.økonomi

import java.time.LocalDate
import no.nav.helse.april
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.inspectors.ØkonomiAsserter
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.AVV
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetalingFilter
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ØkonomiDagTest {

    @Test
    fun `Beløp er ikke 6G-begrenset`() {
        val a1 = tidslinjeOf(2.NAV(500))
        val b1 = tidslinjeOf(2.NAV(500))
        val c1 = tidslinjeOf(2.NAV(500))
        val (a, b, c) = listOf(a1, b1, c1).betal()
        assertØkonomi(a, 500.0)
        assertØkonomi(b, 500.0)
        assertØkonomi(c, 500.0)
    }

    @Test
    fun `dekningsgrunnlag rundes opp`() {
        val a1 = tidslinjeOf(2.NAV(1200.75, 50.0))
        val (a) = listOf(a1).betal()
        assertØkonomi(a, 600.0)
    }

    @Test
    fun `dekningsgrunnlag rundes ned`() {
        val a1 = tidslinjeOf(2.NAV(1200.49, 50.0))
        val (a) = listOf(a1).betal()
        assertØkonomi(a, 600.0)
    }

    @Test
    fun `Dekningsgrunnlag uten desimaler`() {
        val a1 = tidslinjeOf(2.NAV(1201, 50.0))
        val (a) = listOf(a1).betal()
        assertØkonomi(a, 601.0)
    }

    @Test
    fun `liten inntekt`() {
        val inntekt = 5000.månedlig
        val a1 = tidslinjeOf(1.NAV(inntekt))
        val (a) = listOf(a1).betal()
        assertØkonomi(a, 231.0, 0.0)
        val b1 = tidslinjeOf(1.NAV(inntekt, 50))
        val (b) = listOf(b1).betal()
        assertØkonomi(b, 115.0, 0.0)
        val c1 = tidslinjeOf(1.NAV(inntekt, refusjonsbeløp = inntekt/2))
        val (c) = listOf(c1).betal()
        assertØkonomi(c, 115.0, 116.0)
    }

    @Test
    fun `Beløp er 6G-begrenset`() {
        val a1 = tidslinjeOf(2.NAV(1200))
        val b1 = tidslinjeOf(2.NAV(1200))
        val c1 = tidslinjeOf(2.NAV(1200))
        val (a, b, c) = listOf(a1, b1, c1).betal()
        assertØkonomi(a, 721.0)
        assertØkonomi(b, 720.0)
        assertØkonomi(c, 720.0)
    }

    @Test
    fun `bruker riktig G-verdi ved 6G-begrensning`() {
        tidslinjeOf(2.NAV(3000)).let { tidslinje ->
            val (resultat) = listOf(tidslinje).betal(2.januar)
            assertØkonomi(resultat, 2161.0)
        }
        tidslinjeOf(2.NAV(3000), startDato = 30.april).let { tidslinje ->
            val (resultat) = listOf(tidslinje).betal(2.mai)
            assertØkonomi(resultat, 2161.0)
        }
    }

    @Test
    fun `Beløp med arbeidsdag`() {
        val a1 = tidslinjeOf(2.NAV(1200))
        val b1 = tidslinjeOf(2.NAV(1200))
        val c1 = tidslinjeOf(2.ARB(1200))
        val (a, b, c) = listOf(a1, b1, c1).betal()
        assertØkonomi(a, 721.0)
        assertØkonomi(b, 720.0)
        assertØkonomi(c, 0.0)
    }

    @Test
    fun `avvist dag endrer ikke på økonomi til navdag`() {
        val økonomi = Økonomi.sykdomsgrad(100.prosent).inntekt(500.daglig, 500.daglig, 600000.årlig, 500.daglig)
        val builderFør = Økonomitester()
        økonomi.builder(builderFør)

        val a = Utbetalingsdag.NavDag(1.januar, økonomi)
        val b = a.avvis(listOf(Begrunnelse.MinimumInntekt))

        assertNotNull(b)
        val builderEtter = Økonomitester()
        økonomi.builder(builderEtter)

        assertFalse(økonomi === b.økonomi)
        assertTrue(builderFør.erLike(builderEtter))
    }

    private class Økonomitester : ØkonomiBuilder() {
        fun erLike(other: Økonomitester): Boolean {
            if (this.grad != other.grad) return false
            if (this.arbeidsgiverRefusjonsbeløp != other.arbeidsgiverRefusjonsbeløp) return false
            if (this.dekningsgrunnlag != other.dekningsgrunnlag) return false
            if (this.totalGrad != other.totalGrad) return false
            if (this.aktuellDagsinntekt != other.aktuellDagsinntekt) return false
            if (this.arbeidsgiverbeløp != other.arbeidsgiverbeløp) return false
            if (this.personbeløp != other.personbeløp) return false
            if (this.er6GBegrenset != other.er6GBegrenset) return false
            if (this.grunnbeløpgrense != other.grunnbeløpgrense) return false
            return this.tilstand == other.tilstand
        }
    }

    @Test
    fun `Beløp medNavDag som har blitt avvist`() {
        val a = tidslinjeOf(2.NAV(1200))
        val b = tidslinjeOf(2.NAV(1200))
        val c = Utbetalingstidslinje.avvis(listOf(tidslinjeOf(2.NAV(1200))), listOf(1.januar til 31.januar), listOf(
            Begrunnelse.MinimumInntekt)).single()
        val (a1, b1, c1) = listOf(a, b, c).betal()
        assertØkonomi(a, null, null)
        assertØkonomi(a1, 721.0, 0.0)
        assertØkonomi(b, null, null)
        assertØkonomi(b1, 720.0, 0.0)
        assertØkonomi(c, null, null)
        assertØkonomi(c1, 0.0, 0.0)
    }

    @Test
    fun `Beløp med avvistdager`() {
        val a1 = tidslinjeOf(2.NAV(1200))
        val b1 = tidslinjeOf(2.NAV(1200))
        val c1 = tidslinjeOf(2.AVV(1200, 100))
        val (a, b, c) = listOf(a1, b1, c1).betal()
        assertØkonomi(a, 721.0)
        assertØkonomi(b, 720.0)
        assertØkonomi(c, 0.0)
    }

    private fun assertØkonomi(tidslinje: Utbetalingstidslinje, arbeidsgiverbeløp: Double?, personbeløp: Double? = 0.0) {
        tidslinje.forEach {
            it.økonomi.accept( ØkonomiAsserter {
                    _,
                    _,
                    _,
                    _,
                    _,
                    actualArbeidsgiverbeløp,
                    actualPersonbeløp,
                    _ ->
                assertEquals(arbeidsgiverbeløp, actualArbeidsgiverbeløp)
                assertEquals(personbeløp, actualPersonbeløp)
            })
        }
    }

    private fun List<Utbetalingstidslinje>.betal(virkningsdato: LocalDate = 1.januar): List<Utbetalingstidslinje> {
        val periode = virkningsdato til virkningsdato // Brukes ikke når vi eksplisitt setter virkningsdato
        return MaksimumUtbetalingFilter().betal(this, periode, Aktivitetslogg(), MaskinellJurist())
    }
}
