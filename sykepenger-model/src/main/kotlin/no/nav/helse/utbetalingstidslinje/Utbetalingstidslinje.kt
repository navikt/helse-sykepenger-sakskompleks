package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.contains
import no.nav.helse.hendelser.til
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Arbeidsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodedagNav
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.AvvistDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ForeldetDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.økonomi.Økonomi

/**
 * Forstår utbetalingsforpliktelser for en bestemt arbeidsgiver
 */

class Utbetalingstidslinje(utbetalingsdager: Collection<Utbetalingsdag>) : Collection<Utbetalingsdag> by utbetalingsdager {
    private val utbetalingsdager = utbetalingsdager.toList()
    private val førsteDato get() = utbetalingsdager.first().dato
    private val sisteDato get() = utbetalingsdager.last().dato

    constructor() : this(mutableListOf())

    init {
        check(utbetalingsdager.distinctBy { it.dato }.size == utbetalingsdager.size) {
            "Utbetalingstidslinjen består av minst én dato som pekes på av mer enn én Utbetalingsdag"
        }
    }

    companion object {
        fun periode(tidslinjer: List<Utbetalingstidslinje>) = tidslinjer
            .filter { it.utbetalingsdager.isNotEmpty() }
            .map { it.periode() }
            .reduce(Periode::plus)

        fun avvis(
            tidslinjer: List<Utbetalingstidslinje>,
            avvistePerioder: List<Periode>,
            begrunnelser: List<Begrunnelse>
        ) = tidslinjer.map { it.avvis(avvistePerioder, begrunnelser) }

        fun avvisteDager(
            tidslinjer: List<Utbetalingstidslinje>,
            periode: Periode,
            begrunnelse: Begrunnelse
        ) = tidslinjer.flatMap { it.subset(periode) }.mapNotNull { it.erAvvistMed(begrunnelse) }

        fun betale(tidslinjer: List<Utbetalingstidslinje>): List<Utbetalingstidslinje> {
            return Utbetalingsdag.betale(tidslinjer).map { Utbetalingstidslinje(it) }
        }

        fun ferdigUtbetalingstidslinje(utbetalingsdager: List<Utbetalingsdag>) = Utbetalingstidslinje(utbetalingsdager.toMutableList())
    }

    fun er6GBegrenset(): Boolean {
        return utbetalingsdager.any {
            it.økonomi.er6GBegrenset()
        }
    }

    fun accept(visitor: UtbetalingstidslinjeVisitor) {
        visitor.preVisitUtbetalingstidslinje(this, when(this.isEmpty()) {
            true -> null
            else -> this.periode()
        })
        utbetalingsdager.forEach { it.accept(visitor) }
        visitor.postVisitUtbetalingstidslinje()
    }

    private fun avvis(avvistePerioder: List<Periode>, begrunnelser: List<Begrunnelse>): Utbetalingstidslinje {
        if (begrunnelser.isEmpty()) return this
        return Utbetalingstidslinje(utbetalingsdager.map { utbetalingsdag ->
            val avvistDag = if (utbetalingsdag.dato in avvistePerioder) utbetalingsdag.avvis(begrunnelser) else null
            avvistDag ?: utbetalingsdag
        })
    }

    operator fun plus(other: Utbetalingstidslinje): Utbetalingstidslinje {
        if (other.isEmpty()) return this
        if (this.isEmpty()) return other
        val tidligsteDato = this.tidligsteDato(other)
        val sisteDato = this.sisteDato(other)
        return this.utvide(tidligsteDato, sisteDato).binde(other.utvide(tidligsteDato, sisteDato))
    }

    fun harUtbetalingsdager() = sykepengeperiode() != null

    override fun iterator() = this.utbetalingsdager.iterator()

    private fun binde(
        other: Utbetalingstidslinje
    ) = Utbetalingstidslinje(
        this.utbetalingsdager.zip(other.utbetalingsdager) { venstre, høyre -> maxOf(venstre, høyre) }.toMutableList()
    )

    private fun Builder.addUkjentDagHåndterHelg(dato: LocalDate) {
        if (dato.erHelg()) return addFridag(dato, Økonomi.ikkeBetalt())
        addUkjentDag(dato)
    }
    private fun utvide(tidligsteDato: LocalDate, sisteDato: LocalDate): Utbetalingstidslinje {
        val original = this
        return Builder().apply {
            tidligsteDato.datesUntil(original.førsteDato)
                .forEach { addUkjentDagHåndterHelg(it) }
            original.utbetalingsdager.forEach { add(it) }
            original.sisteDato.plusDays(1)
                .datesUntil(sisteDato.plusDays(1))
                .forEach { addUkjentDagHåndterHelg(it) }
        }.build()
    }

    private fun tidligsteDato(other: Utbetalingstidslinje) =
        minOf(this.førsteDato, other.førsteDato)

    private fun sisteDato(other: Utbetalingstidslinje) =
        maxOf(this.sisteDato, other.sisteDato)

    fun periode() = Periode(førsteDato, sisteDato)

    fun sykepengeperiode(): Periode? {
        val første = utbetalingsdager.firstOrNull { it is NavDag }?.dato ?: return null
        val siste = utbetalingsdager.last { it is NavDag }.dato
        return første til siste
    }

    fun subset(periode: Periode): Utbetalingstidslinje {
        if (isEmpty()) return Utbetalingstidslinje()
        if (periode == periode()) return this
        return Utbetalingstidslinje(utbetalingsdager.filter { it.dato in periode }.toMutableList())
    }

    fun kutt(sisteDato: LocalDate) = subset(LocalDate.MIN til sisteDato)

    operator fun get(dato: LocalDate) =
        if (isEmpty() || dato !in periode()) UkjentDag(dato, Økonomi.ikkeBetalt())
        else utbetalingsdager.first { it.dato == dato }

    override fun toString(): String {
        return utbetalingsdager.joinToString(separator = "") {
            (if (it.dato.dayOfWeek == DayOfWeek.MONDAY) " " else "") +
                when (it::class) {
                    NavDag::class -> "N"
                    NavHelgDag::class -> "H"
                    Arbeidsdag::class -> "A"
                    ArbeidsgiverperiodeDag::class -> "P"
                    Fridag::class -> "F"
                    AvvistDag::class -> "X"
                    UkjentDag::class -> "U"
                    ForeldetDag::class -> "O"
                    else -> "?"
                }
        }.trim()
    }

    class Builder {
        private val utbetalingsdager = mutableListOf<Utbetalingsdag>()

        fun build() = Utbetalingstidslinje(utbetalingsdager)

        fun addArbeidsgiverperiodedag(dato: LocalDate, økonomi: Økonomi) {
            add(ArbeidsgiverperiodeDag(dato, økonomi))
        }

        fun addArbeidsgiverperiodedagNav(dato: LocalDate, økonomi: Økonomi) {
            add(ArbeidsgiverperiodedagNav(dato, økonomi))
        }

        fun addNAVdag(dato: LocalDate, økonomi: Økonomi) {
            add(NavDag(dato, økonomi))
        }

        fun addArbeidsdag(dato: LocalDate, økonomi: Økonomi) {
            add(Arbeidsdag(dato, økonomi))
        }

        fun addFridag(dato: LocalDate, økonomi: Økonomi) {
            add(Fridag(dato, økonomi))
        }

        fun addHelg(dato: LocalDate, økonomi: Økonomi) {
            add(NavHelgDag(dato, økonomi))
        }

        fun addUkjentDag(dato: LocalDate) =
            add(UkjentDag(dato, Økonomi.ikkeBetalt()))

        fun addAvvistDag(dato: LocalDate, økonomi: Økonomi, begrunnelser: List<Begrunnelse>) {
            add(AvvistDag(dato, økonomi, begrunnelser))
        }

        fun addForeldetDag(dato: LocalDate, økonomi: Økonomi) {
            add(ForeldetDag(dato, økonomi))
        }

        internal fun add(dag: Utbetalingsdag) {
            utbetalingsdager.add(dag)
        }
    }
}

