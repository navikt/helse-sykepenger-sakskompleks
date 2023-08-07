package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import no.nav.helse.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class UtbetalingslinjeTest {

    @Test
    fun `kutte helg`() {
        val linje = Utbetalingslinje(
            fom = 1.januar(2018),
            tom = 7.januar(2018),
            aktuellDagsinntekt = null,
            grad = null,
            beløp = null
        )

        val nyLinje = linje.kuttHelg() ?: fail { "forventet linje" }
        assertEquals(1.januar(2018), nyLinje.inspektør.fom)
        assertEquals(5.januar(2018), nyLinje.inspektør.tom)
    }

    @Test
    fun `kutte helg - linje forsvinner`() {
        val linje = Utbetalingslinje(
            fom = 6.januar(2018),
            tom = 7.januar(2018),
            aktuellDagsinntekt = null,
            grad = null,
            beløp = null
        )

        assertNull(linje.kuttHelg())
    }
}

internal val Utbetalingslinje.inspektør get() = UtbetalingslinjeInspektør(this)

internal class UtbetalingslinjeInspektør(linje: Utbetalingslinje) : UtbetalingslinjeVisitor {
    internal lateinit var fom: LocalDate
        private set
    internal lateinit var tom: LocalDate
        private set

    init {
        linje.accept(this)
    }

    override fun visitUtbetalingslinje(
        linje: Utbetalingslinje,
        fom: LocalDate,
        tom: LocalDate,
        stønadsdager: Int,
        totalbeløp: Int,
        satstype: Satstype,
        beløp: Int?,
        aktuellDagsinntekt: Int?,
        grad: Int?,
        delytelseId: Int,
        refDelytelseId: Int?,
        refFagsystemId: String?,
        endringskode: Endringskode,
        datoStatusFom: LocalDate?,
        statuskode: String?,
        klassekode: Klassekode
    ) {
        this.fom = fom
        this.tom = tom
    }
}