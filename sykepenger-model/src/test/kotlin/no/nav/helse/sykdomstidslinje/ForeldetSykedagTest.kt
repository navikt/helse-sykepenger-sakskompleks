package no.nav.helse.sykdomstidslinje

import java.time.LocalDate
import no.nav.helse.april
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.februar
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.Dag.ForeldetSykedag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ForeldetSykedagTest {

    companion object {
        private const val UNG_PERSON_FNR_2018 = "12029240045"
        private const val AKTØRID = "42"
        private const val ORGNUMMER = "987654321"
        private val hendelefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = AKTØRID,
            personidentifikator = Personidentifikator(UNG_PERSON_FNR_2018),
            organisasjonsnummer = ORGNUMMER
        )
    }

    @Test fun `omgående innsending`() {
        undersøke(søknad(1.mars)).also {
            assertEquals(28, it.antallDager)
            assertEquals(20, it.dagteller[Sykedag::class])
            assertEquals(8, it.dagteller[SykHelgedag::class])
            assertNull(it.dagteller[ForeldetSykedag::class])
        }
    }

    @Test fun `siste dag innlevering`() {
        undersøke(søknad(30.april)).also {
            assertEquals(28, it.antallDager)
            assertEquals(20, it.dagteller[Sykedag::class])
            assertEquals(8, it.dagteller[SykHelgedag::class])
            assertNull(it.dagteller[ForeldetSykedag::class])
        }
    }

    @Test fun `Noen dager er ugyldige`() {
        undersøke(søknad(1.mai)).also {
            assertEquals(28, it.antallDager)
            assertEquals(10, it.dagteller[Sykedag::class])
            assertEquals(10, it.dagteller[ForeldetSykedag::class])
            assertEquals(8, it.dagteller[SykHelgedag::class])
        }
    }

    @Test fun `Alle dager er ugyldige`() {
        undersøke(søknad(1.juni)).also {
            assertEquals(28, it.antallDager)
            assertNull(it.dagteller[Sykedag::class])
            assertEquals(20, it.dagteller[ForeldetSykedag::class])
            assertEquals(8, it.dagteller[SykHelgedag::class])
        }
    }

    private fun søknad(sendtTilNAV: LocalDate) = hendelefabrikk.lagSøknad(
        perioder = arrayOf(Sykdom(18.januar, 14.februar, 100.prosent)), // 10 sykedag januar & februar
        sendtTilNAVEllerArbeidsgiver = sendtTilNAV
    )

    private fun undersøke(søknad: SykdomstidslinjeHendelse) = søknad.sykdomstidslinje().inspektør
}
