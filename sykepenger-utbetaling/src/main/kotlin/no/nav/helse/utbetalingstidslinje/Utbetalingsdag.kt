package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.utbetalingslinjer.Beløpkilde
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.utbetalingstidslinje.InternUtbetalingstidslinje.Companion.tidslinjer
import no.nav.helse.økonomi.betal
import no.nav.helse.økonomi.Økonomi
import no.nav.helse.økonomi.Økonomi.Companion.erUnderGrensen
import no.nav.helse.økonomi.ØkonomiVisitor

sealed class Utbetalingsdag(
    val dato: LocalDate,
    val økonomi: Økonomi
) : Comparable<Utbetalingsdag> {

    internal abstract val prioritet: Int
    fun beløpkilde(): Beløpkilde = BeløpkildeAdapter(økonomi)
    override fun compareTo(other: Utbetalingsdag): Int {
        return this.prioritet.compareTo(other.prioritet)
    }

    override fun toString() = "${this.javaClass.simpleName} ($dato) ${økonomi.brukGrad { grad-> grad }} %"

    fun avvis(begrunnelser: List<Begrunnelse>) = begrunnelser
        .filter { it.skalAvvises(this) }
        .takeIf(List<*>::isNotEmpty)
        ?.let(::avvisDag)

    protected open fun avvisDag(begrunnelser: List<Begrunnelse>) = AvvistDag(dato, økonomi, begrunnelser)
    protected abstract fun kopierMed(økonomi: Økonomi): Utbetalingsdag

    abstract fun accept(visitor: UtbetalingsdagVisitor)

    open fun erAvvistMed(begrunnelse: Begrunnelse): AvvistDag? = null

    class ArbeidsgiverperiodeDag(dato: LocalDate, økonomi: Økonomi) : Utbetalingsdag(dato, økonomi) {
        override val prioritet = 30
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = ArbeidsgiverperiodeDag(dato, økonomi)
    }

    class ArbeidsgiverperiodedagNav(dato: LocalDate, økonomi: Økonomi) : Utbetalingsdag(dato, økonomi) {
        override val prioritet = 45
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = ArbeidsgiverperiodedagNav(dato, økonomi)
    }

    class NavDag(
        dato: LocalDate,
        økonomi: Økonomi
    ) : Utbetalingsdag(dato, økonomi) {
        override val prioritet = 50
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = NavDag(dato, økonomi)
    }

    class NavHelgDag(dato: LocalDate, økonomi: Økonomi) :
        Utbetalingsdag(dato, økonomi) {
        override val prioritet = 40
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = NavHelgDag(dato, økonomi)
    }

    class Fridag(dato: LocalDate, økonomi: Økonomi) : Utbetalingsdag(dato, økonomi) {
        override val prioritet = 20
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = Fridag(dato, økonomi)
    }

    class Arbeidsdag(dato: LocalDate, økonomi: Økonomi) : Utbetalingsdag(dato, økonomi) {
        override val prioritet = 10
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = Arbeidsdag(dato, økonomi)
    }

    class AvvistDag(
        dato: LocalDate,
        økonomi: Økonomi,
        val begrunnelser: List<Begrunnelse>
    ) : Utbetalingsdag(dato, økonomi.lås()) {
        override val prioritet = 60
        override fun avvisDag(begrunnelser: List<Begrunnelse>) =
            AvvistDag(dato, økonomi, this.begrunnelser + begrunnelser)

        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)

        override fun erAvvistMed(begrunnelse: Begrunnelse) = takeIf { begrunnelse in begrunnelser }
        override fun kopierMed(økonomi: Økonomi) = AvvistDag(dato, økonomi, begrunnelser)
    }

    class ForeldetDag(dato: LocalDate, økonomi: Økonomi) :
        Utbetalingsdag(dato, økonomi) {
        override val prioritet = 40 // Mellom ArbeidsgiverperiodeDag og NavDag
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = ForeldetDag(dato, økonomi)
    }

    class UkjentDag(dato: LocalDate, økonomi: Økonomi) : Utbetalingsdag(dato, økonomi) {
        override val prioritet = 0
        override fun accept(visitor: UtbetalingsdagVisitor) = visitor.visit(this, dato, økonomi)
        override fun kopierMed(økonomi: Økonomi) = UkjentDag(dato, økonomi)
    }

    companion object {
        fun dagerUnderGrensen(tidslinjer: Collection<Collection<Utbetalingsdag>>): Set<LocalDate> {
            return tidslinjer
                .flatten()
                .groupBy({ it.dato }) { it.økonomi }
                .filterValues { it.erUnderGrensen() }
                .keys
        }

        fun betale(tidslinjer: Collection<Collection<Utbetalingsdag>>): Collection<Collection<Utbetalingsdag>> {
            return periode(tidslinjer).fold(tidslinjer.tidslinjer) { resultat, dato ->
                try {
                    resultat
                        .map { it[dato].økonomi }
                        .betal()
                        .mapIndexed { index, økonomi ->
                            InternUtbetalingstidslinje(resultat[index].map {
                                if (it.dato == dato) it.kopierMed(økonomi) else it
                            })
                        }
                } catch (err: Exception) {
                    throw IllegalArgumentException("Klarte ikke å utbetale for dag=$dato, fordi: ${err.message}", err)
                }
            }
        }

        fun totalSykdomsgrad(tidslinjer: Collection<Collection<Utbetalingsdag>>): Collection<Collection<Utbetalingsdag>> {
            return periode(tidslinjer).fold(tidslinjer.tidslinjer) { tidslinjer1, dagen ->
                // regner ut totalgrad for alle økonomi på samme dag
                val dager = Økonomi.totalSykdomsgrad(tidslinjer1.map { it[dagen].økonomi })
                // oppdaterer tidslinjen til hver ag med nytt økonomiobjekt
                tidslinjer1.zip(dager) { tidslinjen, økonomi ->
                    InternUtbetalingstidslinje(tidslinjen.map { if (it.dato == dagen) it.kopierMed(økonomi) else it })
                }
            }
        }
    }
}

private class InternUtbetalingstidslinje(private val dager: Collection<Utbetalingsdag>) : Collection<Utbetalingsdag> by dager {
    private val periode = dager.firstOrNull()?.let { it.dato.rangeTo(dager.last().dato) }

    operator fun contains(dato: LocalDate) = periode?.let { dato in it } ?: false

    operator fun get(dato: LocalDate) =
        if (dato !in this) UkjentDag(dato, Økonomi.ikkeBetalt())
        else dager.first { it.dato == dato }


    companion object {
        val Collection<Utbetalingsdag>.tidslinje get() = InternUtbetalingstidslinje(this)
        val Collection<Collection<Utbetalingsdag>>.tidslinjer get() = map { it.tidslinje }
    }
}

private fun periode(tidslinjer: Collection<Collection<Utbetalingsdag>>): Iterable<LocalDate> {
    val range = tidslinjer
        .mapNotNull { dager -> dager.lastOrNull()?.let { dager.firstOrNull()?.dato?.rangeTo(it.dato) } }
        .reduce { a, b, -> minOf(a.start, b.start).rangeTo(maxOf(a.endInclusive, b.endInclusive)) }

    return object : Iterable<LocalDate> {
        override fun iterator() = RangeIterator(range)
    }
}

/**
 * Tilpasser Økonomi så det passer til Beløpkilde-porten til utbetalingslinjer
 */
internal class BeløpkildeAdapter(økonomi: Økonomi): Beløpkilde, ØkonomiVisitor {
    private var arbeidsgiverbeløp: Int? = null
    private var personbeløp: Int? = null
    init {
        økonomi.accept(this)
    }
    override fun arbeidsgiverbeløp(): Int = arbeidsgiverbeløp!!
    override fun personbeløp(): Int = personbeløp!!

    override fun visitAvrundetØkonomi(grad: Int, arbeidsgiverRefusjonsbeløp: Int, dekningsgrunnlag: Int, totalGrad: Int, aktuellDagsinntekt: Int, arbeidsgiverbeløp: Int?, personbeløp: Int?, er6GBegrenset: Boolean?) {
        this.arbeidsgiverbeløp = arbeidsgiverbeløp
        this.personbeløp = personbeløp
    }
}

private class RangeIterator(start: LocalDate, private val end: LocalDate): Iterator<LocalDate> {
    private var currentDate = start
    constructor(range: ClosedRange<LocalDate>) : this(range.start, range.endInclusive)
    override fun hasNext() = end >= currentDate
    override fun next(): LocalDate {
        check(hasNext())
        return currentDate.also {
            currentDate = it.plusDays(1)
        }
    }
}