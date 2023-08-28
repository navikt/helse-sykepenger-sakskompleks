package no.nav.helse.økonomi

import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.average
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Prosentdel.Companion.ratio
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ProsentdelTest {

    @Test
    fun `total sykdomsgrad - flyttall`() {
        val inntekter = listOf(
            100.prosent to 502400.04,
            100.prosent to 70065.12
        )
        val result = inntekter.average()
        assertEquals(100.prosent, result)
    }

    @Test
    fun `total sykdomsgrad - flyttall 2`() {
        val inntekter = listOf(
            50.prosent to 1000.0,
            0.prosent to 2000.0
        )
        val result = inntekter.average()
        assertEquals(Prosentdel.ratio(1.0, 6.0), result)
    }

    @Test fun equality() {
        assertEquals(ratio(1.0, 4.0), 25.0.prosent )
        assertNotEquals(ratio(1.0, 4.0), 75.0.prosent )
        assertNotEquals(ratio(1.0, 4.0), Any() )
        assertNotEquals(ratio(1.0, 4.0), null )
    }

    @Test
    fun reciprok() {
        val gradertVerdi = 5.daglig
        val grad = 50.prosent
        assertEquals(10.daglig, Inntekt.fraGradert(gradertVerdi, grad))
        val inntekt = 100.daglig
        val enTredjedel = ratio(1.0, 3.0)
        val gradertInntekt = inntekt * enTredjedel
        assertEquals(inntekt, Inntekt.fraGradert(gradertInntekt, enTredjedel))
    }

    @Test
    fun `opprette med Int`() {
        1.rangeTo(99).forEach { n ->
            assertEquals(n, n.prosent)
            assertEquals(100 - n, n.prosent.not())
        }
        assertEquals(ratio(0.0, 1.0), 0.prosent)
        assertEquals(ratio(100.0, 100.0), 0.prosent.not())
    }

    private fun assertEquals(n: Int, prosentdel: Prosentdel) {
        val forventet = ratio(n.toDouble(), 100.0)
        assertEquals(forventet, prosentdel)
        assertEquals(n, prosentdel.toDouble().toInt())
    }

    @Test
    fun avrundingsfeil() {
        // Fredet variabelnavn
        val karakterMedAvrunding = (1 / 7.0).prosent
        val dobbelomvendt = !!karakterMedAvrunding
        assertEquals(karakterMedAvrunding, dobbelomvendt)
        assertEquals(karakterMedAvrunding, karakterMedAvrunding)
        assertEquals(karakterMedAvrunding.hashCode(), (dobbelomvendt).hashCode())
    }

    @Test
    fun `parameterskontroll av sykdomsgrad`() {
        assertThrows<IllegalArgumentException> { (-0.001).prosent }
        assertThrows<IllegalArgumentException> { (100.001).prosent }
    }

    @Test fun minimumssyke() {
        assertFalse(25.prosent.erUnderGrensen())
        assertFalse(20.prosent.erUnderGrensen())
        assertTrue(15.prosent.erUnderGrensen())
    }
}
