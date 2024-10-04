package no.nav.helse.sykdomstidslinje

import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.testhelpers.TestEvent
import no.nav.helse.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class HendelsekildeTest {
    @Test
    fun `første dato for melding`() {
        val kilder = listOf(
            TestEvent.Sykmelding(2.januar.atStartOfDay()),
            TestEvent.Sykmelding(1.januar.atStartOfDay()),
            TestEvent.Sykmelding(3.januar.atStartOfDay())
        ).map { it.kilde }
        assertEquals(2.januar.atStartOfDay(), SykdomshistorikkHendelse.Hendelseskilde.tidligsteTidspunktFor(kilder, TestEvent.Sykmelding::class))
    }

    @Test
    fun `ulike kilder`() {
        val kilder = listOf(
            TestEvent.Sykmelding(2.januar.atStartOfDay()),
            TestEvent.Søknad(1.januar.atStartOfDay()),
            TestEvent.Sykmelding(3.januar.atStartOfDay())
        ).map { it.kilde }
        assertThrows<IllegalStateException> { SykdomshistorikkHendelse.Hendelseskilde.tidligsteTidspunktFor(kilder, TestEvent.Sykmelding::class) }
    }
}
