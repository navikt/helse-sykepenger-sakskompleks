package no.nav.helse.person.beløp

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.BeløpstidslinjeDto
import no.nav.helse.februar
import no.nav.helse.hendelser.Avsender.ARBEIDSGIVER
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Avsender.SYKMELDT
import no.nav.helse.hendelser.Avsender.SYSTEM
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class BeløpstidslinjeTest {

    @Test
    fun `beløpstidlinje lager en tidslinje med beløp og kilde`() {
        val beløpstidslinje = (Arbeidsgiver oppgir 31000.månedlig fra 1.januar til 10.januar) og (Saksbehandler oppgir 15500.månedlig fra 11.januar til 31.januar)

        assertEquals(10, beløpstidslinje.count { it.kilde == Arbeidsgiver })
        assertEquals(21, beløpstidslinje.count { it.kilde == Saksbehandler })
    }

    @Test
    fun `Hvis man slår opp på en dag som ikke finnes, da skal man få en ukjent dag`() {
        val beløpstidslinje = Arbeidsgiver oppgir 31000.månedlig hele januar
        assertDoesNotThrow { beløpstidslinje[1.februar] }
        assertEquals(UkjentDag, beløpstidslinje[1.februar])
    }

    @Test
    fun `Hvis du absolutt vil ha en tom tidslinje, så skal du få det`() {
        val beløpstidslinje = Beløpstidslinje()
        assertEquals(0, beløpstidslinje.count())
    }

    @Test
    fun `Man skal ikke kunne opprette en ny tidslinje med overlappende dager`() {
        assertThrows<IllegalArgumentException> {
            Beløpstidslinje(Beløpsdag(1.januar, 1.daglig, Arbeidsgiver), Beløpsdag(1.januar, 2.daglig, Sykmeldt))
        }
    }

    @Test
    fun `Du haver to stykk beløpstidslinje, som du ønsker forent`()  {
        val gammelTidslinje = (Arbeidsgiver oppgir 31000.månedlig hele januar) og (Arbeidsgiver oppgir 0.daglig hele mars)

        val nyTidslinje = (Saksbehandler oppgir 31005.månedlig fra 20.januar til 10.mars)

        val forventetTidslinje =
            (Arbeidsgiver oppgir 31000.månedlig fra 1.januar til 19.januar) og
            (Saksbehandler oppgir 31005.månedlig fra 20.januar til 10.mars) og
            (Arbeidsgiver oppgir 0.daglig fra 11.mars til 31.mars)

        assertEquals(forventetTidslinje, gammelTidslinje og nyTidslinje)
    }

    @Test
    fun `Slår sammen to beløptidslinjer med ulike beløp og tidsstempler`() {
        val kilde1 = Kilde(UUID.randomUUID(), ARBEIDSGIVER, LocalDateTime.now().minusDays(1))
        val kilde2 = Kilde(UUID.randomUUID(), ARBEIDSGIVER, LocalDateTime.now())

        val gammelTidslinje = Beløpstidslinje.fra(januar, 500.daglig, kilde1)
        val nyTidslinje = Beløpstidslinje.fra(januar, 1000.daglig, kilde2)

        assertEquals(nyTidslinje, gammelTidslinje + nyTidslinje)
        assertEquals(nyTidslinje, nyTidslinje + gammelTidslinje)
    }

    @Test
    fun `Slår sammen to beløptidslinjer med like beløp og ulike tidsstempler`() {
        val kilde1 = Kilde(UUID.randomUUID(), ARBEIDSGIVER, LocalDateTime.now().minusDays(1))
        val kilde2 = Kilde(UUID.randomUUID(), ARBEIDSGIVER, LocalDateTime.now())

        val beløp = 1000.daglig
        val gammelTidslinje = Beløpstidslinje.fra(januar, beløp, kilde1)
        val nyTidslinje = Beløpstidslinje.fra(januar, beløp, kilde2)

        assertEquals(gammelTidslinje, gammelTidslinje + nyTidslinje)
        assertEquals(nyTidslinje, nyTidslinje + gammelTidslinje)
    }

    @Test
    fun `Slår sammen to beløptidslinjer med like beløp og like tidsstempler`() {
        val tidsstempel = LocalDateTime.now()
        val kilde1 = Kilde(UUID.randomUUID(), ARBEIDSGIVER, tidsstempel)
        val kilde2 = Kilde(UUID.randomUUID(), ARBEIDSGIVER, tidsstempel)

        val beløp = 1000.daglig
        val gammelTidslinje = Beløpstidslinje.fra(januar, beløp, kilde1)
        val nyTidslinje = Beløpstidslinje.fra(januar, beløp, kilde2)

        assertEquals(gammelTidslinje, gammelTidslinje + nyTidslinje)
        assertEquals(nyTidslinje, nyTidslinje + gammelTidslinje)
    }

    @Test
    fun `Trekke dager fra på en beløpstidslinje`() {
        val tidslinje = (1.daglig fra 1.januar til 2.januar) og (2.daglig fra 4.januar til 5.januar)
        assertEquals(1.daglig, tidslinje[1.januar].beløp)
        assertEquals(UkjentDag, tidslinje[3.januar])
        assertEquals(2.daglig, tidslinje[4.januar].beløp)

        val forventet = (1.daglig kun 2.januar) og (2.daglig kun 5.januar)

        val fratrukket = tidslinje - 4.januar - setOf(1.januar)

        assertEquals(forventet, fratrukket)

        assertEquals(UkjentDag, fratrukket[1.januar])
        assertEquals(UkjentDag, fratrukket[4.januar])
    }

    @Test
    fun `Strekker en beløpstidslinje i snuten`() {
        assertEquals(Beløpstidslinje(), Beløpstidslinje().strekk(januar))
        val tidslinje = Arbeidsgiver oppgir 1000.daglig hele februar
        assertEquals(tidslinje, tidslinje.strekk(februar))
        assertEquals(tidslinje, tidslinje.strekk(2.februar til 28.februar))
        assertEquals(Arbeidsgiver oppgir 1000.daglig fra 31.januar til 28.februar, tidslinje.strekk(31.januar til 28.februar))
    }

    @Test
    fun `Strekker en beløpstidslinje i halen`() {
        val tidslinje = Arbeidsgiver oppgir 1000.daglig hele februar
        assertEquals(tidslinje, tidslinje.strekk(februar))
        assertEquals(tidslinje, tidslinje.strekk(1.februar til 27.februar))
        assertEquals(Arbeidsgiver oppgir 1000.daglig fra 1.februar til 1.mars, tidslinje.strekk(1.februar til 1.mars))
    }

    @Test
    fun `Strekker en beløpstidslinje i snuten og halen`() {
        val tidslinje = (Arbeidsgiver oppgir 1000.daglig kun 1.februar) og (Saksbehandler oppgir 2000.daglig kun 28.februar)
        val forventet = (Arbeidsgiver oppgir 1000.daglig fra 31.januar til 1.februar) og (Saksbehandler oppgir 2000.daglig fra 28.februar til 1.mars)
        assertEquals(forventet, tidslinje.strekk(31.januar til 1.mars))
        assertEquals(UkjentDag, forventet[2.februar])
        assertEquals(UkjentDag, forventet[27.februar])

        assertEquals(Systemet oppgir 100.daglig fra 5.januar til 7.januar, (Systemet oppgir 100.daglig kun 6.januar).strekk(5.januar til 7.januar))
    }

    @Test
    fun `Finne første endring i beløp`() {
        assertNull(Beløpstidslinje().førsteEndring(Beløpstidslinje()))
        assertNull((Saksbehandler oppgir 100.daglig hele januar).førsteEndring(Arbeidsgiver oppgir 100.daglig hele januar))
        assertEquals(1.januar, (Saksbehandler oppgir 100.daglig hele januar).førsteEndring(Arbeidsgiver oppgir 101.daglig hele januar))
        assertEquals(15.januar, (Saksbehandler oppgir 100.daglig hele januar).førsteEndring((Arbeidsgiver oppgir 100.daglig fra 1.januar til 14.januar) og (Arbeidsgiver oppgir 101.daglig kun 15.januar)))

        assertEquals(1.januar, (Saksbehandler oppgir 100.daglig hele januar).førsteEndring(Arbeidsgiver oppgir 100.daglig fra 2.januar til 31.januar))
        assertEquals(31.januar, (Saksbehandler oppgir 100.daglig hele januar).førsteEndring(Arbeidsgiver oppgir 100.daglig fra 1.januar til 30.januar))
        val hulleteSaksbehandler = (Saksbehandler oppgir 100.daglig kun 1.januar) og (Saksbehandler oppgir 100.daglig kun 31.januar)
        val hulleteArbeidsgiver = (Saksbehandler oppgir 100.daglig kun 1.januar) og (Saksbehandler oppgir 100.daglig kun 30.januar)
        assertEquals(30.januar, hulleteSaksbehandler.førsteEndring(hulleteArbeidsgiver))
        assertEquals(30.januar, hulleteArbeidsgiver.førsteEndring(hulleteSaksbehandler))
    }

    @Test
    fun dto() {
        val tidslinje = (Arbeidsgiver oppgir 500.daglig kun 1.februar) og
                (Arbeidsgiver oppgir 250.daglig fra 2.februar til 10.februar) og
                (Arbeidsgiver oppgir 500.daglig fra 11.februar til 12.februar)

        val kilde = BeløpstidslinjeDto.BeløpstidslinjedagKildeDto(Arbeidsgiver.meldingsreferanseId, Arbeidsgiver.avsender.dto(), Arbeidsgiver.tidsstempel)
        assertEquals(BeløpstidslinjeDto(
            perioder = listOf(
                BeløpstidslinjeDto.BeløpstidslinjeperiodeDto(
                    fom = 1.februar,
                    tom = 1.februar,
                    dagligBeløp = 500.0,
                    kilde = kilde
                ),
                BeløpstidslinjeDto.BeløpstidslinjeperiodeDto(
                    fom = 2.februar,
                    tom = 10.februar,
                    dagligBeløp = 250.0,
                    kilde = kilde
                ),
                BeløpstidslinjeDto.BeløpstidslinjeperiodeDto(
                    fom = 11.februar,
                    tom = 12.februar,
                    dagligBeløp = 500.0,
                    kilde = kilde
                )
            )
        ), tidslinje.dto())
    }

    private companion object {
        private val ArbeidsgiverId = UUID.fromString("00000000-0000-0000-0000-000000000000")
        private val SaksbehandlerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val SykmeldtId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        private val SystemId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        private val Arbeidsgiver = Kilde(ArbeidsgiverId, ARBEIDSGIVER, LocalDateTime.now())
        private val Saksbehandler = Kilde(SaksbehandlerId, SAKSBEHANDLER, LocalDateTime.now())
        private val Sykmeldt = Kilde(SykmeldtId, SYKMELDT, LocalDateTime.now())
        private val Systemet = Kilde(SystemId, SYSTEM, LocalDateTime.now())

        infix fun Inntekt.fra(fra: LocalDate) = Triple(Systemet, this, fra)
        infix fun Inntekt.kun(kun: LocalDate) = fra(kun) til kun

        infix fun Kilde.oppgir(inntekt: Inntekt) = this to inntekt
        infix fun Pair<Kilde, Inntekt>.fra(fra: LocalDate) = Triple(first, second, fra)
        infix fun Pair<Kilde, Inntekt>.kun(kun: LocalDate) = fra(kun) til kun
        infix fun Pair<Kilde, Inntekt>.hele(periode: Periode) = fra(periode.start) til periode.endInclusive
        infix fun Triple<Kilde, Inntekt, LocalDate>.til(til: LocalDate) = Beløpstidslinje((third til til).map { Beløpsdag(it, second, first) })
        infix fun Beløpstidslinje.og(other: Beløpstidslinje) = this + other
    }
}