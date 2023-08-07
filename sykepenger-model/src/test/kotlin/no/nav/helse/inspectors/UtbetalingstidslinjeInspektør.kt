package no.nav.helse.inspectors

import java.time.LocalDate
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Arbeidsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodedagNav
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.AvvistDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ForeldetDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjeVisitor
import no.nav.helse.økonomi.Økonomi
import no.nav.helse.økonomi.ØkonomiVisitor
import kotlin.reflect.KClass

val Utbetalingstidslinje.inspektør get() = UtbetalingstidslinjeInspektør(this)

// Collects assertable statistics for a Utbetalingstidslinje
class UtbetalingstidslinjeInspektør(private val utbetalingstidslinje: Utbetalingstidslinje):
    UtbetalingstidslinjeVisitor, ØkonomiVisitor {
    var førstedato = LocalDate.MIN
    var sistedato = LocalDate.MAX
    lateinit var førstedag: Utbetalingsdag
    lateinit var sistedag: Utbetalingsdag

    var arbeidsdagTeller = 0
    var arbeidsgiverperiodeDagTeller = 0
    var arbeidsgiverperiodedagNavTeller = 0
    var avvistDagTeller = 0
    var fridagTeller = 0
    var navDagTeller = 0
    var navHelgDagTeller = 0
    var foreldetDagTeller = 0
    var ukjentDagTeller = 0
    var totalUtbetaling = 0.0
    var totalInntekt = 0.0

    val navdager = mutableListOf<NavDag>()
    val navHelgdager = mutableListOf<NavHelgDag>()
    val arbeidsdager = mutableListOf<Arbeidsdag>()
    val arbeidsgiverdager = mutableListOf<ArbeidsgiverperiodeDag>()
    val arbeidsgiverperiodedagerNavAnsvar = mutableListOf<ArbeidsgiverperiodedagNav>()
    val fridager = mutableListOf<Fridag>()
    val avvistedatoer = mutableListOf<LocalDate>()
    val avvistedager = mutableListOf<AvvistDag>()
    private val begrunnelser = mutableMapOf<LocalDate, List<Begrunnelse>>()

    private val økonomi = mutableMapOf<LocalDate, Økonomi>()
    val unikedager = mutableSetOf<KClass<out Utbetalingsdag>>()

    val size get() =
        arbeidsdagTeller +
            arbeidsgiverperiodeDagTeller +
            avvistDagTeller +
            fridagTeller +
            navDagTeller +
            navHelgDagTeller +
            foreldetDagTeller +
            ukjentDagTeller +
                arbeidsgiverperiodedagNavTeller

    init {
        arbeidsdagTeller = 0
        arbeidsgiverperiodeDagTeller = 0
        avvistDagTeller = 0
        fridagTeller = 0
        navDagTeller = 0
        navHelgDagTeller = 0
        ukjentDagTeller = 0
        totalUtbetaling = 0.0
        utbetalingstidslinje.accept(this)
    }

    fun grad(dag: LocalDate) = økonomi.getValue(dag).brukAvrundetGrad { grad -> grad }
    fun <R> økonomi(lambda: (Økonomi) -> R): List<R> = økonomi.values.map(lambda)

    fun totalUtbetaling() = totalUtbetaling
    fun totalInntekt() = totalInntekt

    fun begrunnelse(dato: LocalDate) =
        begrunnelser[dato] ?: emptyList()

    fun erNavdag(dato: LocalDate) = utbetalingstidslinje[dato] is NavDag

    private fun collect(dag: Utbetalingsdag, dato: LocalDate, økonomi: Økonomi) {
        this.økonomi[dato] = økonomi
        unikedager.add(dag::class)
        første(dag, dato)
        sistedag = dag
        sistedato = dato
    }

    private fun første(dag: Utbetalingsdag, dato: LocalDate) {
        if (this::førstedag.isInitialized) return
        førstedag = dag
        førstedato = dato
    }

    override fun visit(
        dag: Arbeidsdag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        arbeidsdagTeller += 1
        arbeidsdager.add(dag)
        collect(dag, dato, økonomi)
    }

    override fun visit(dag: ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        arbeidsgiverperiodedagNavTeller += 1
        arbeidsgiverperiodedagerNavAnsvar.add(dag)
        collect(dag, dato, økonomi)
    }

    override fun visit(
        dag: ArbeidsgiverperiodeDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        arbeidsgiverperiodeDagTeller += 1
        arbeidsgiverdager.add(dag)
        collect(dag, dato, økonomi)
    }

    override fun visit(
        dag: AvvistDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        avvistDagTeller += 1
        avvistedatoer.add(dato)
        avvistedager.add(dag)
        begrunnelser[dato] = dag.begrunnelser
        collect(dag, dato, økonomi)
    }

    override fun visit(
        dag: Fridag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        fridagTeller += 1
        fridager.add(dag)
        collect(dag, dato, økonomi)
    }

    override fun visit(
        dag: NavDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        økonomi.accept(this)
        navDagTeller += 1
        navdager.add(dag)
        collect(dag, dato, økonomi)
    }

    override fun visitØkonomi(
        grad: Double,
        arbeidsgiverRefusjonsbeløp: Double,
        dekningsgrunnlag: Double,
        totalGrad: Double,
        aktuellDagsinntekt: Double,
        arbeidsgiverbeløp: Double?,
        personbeløp: Double?,
        er6GBegrenset: Boolean?
    ) {
        totalUtbetaling += arbeidsgiverbeløp ?: 0.0
        totalUtbetaling += personbeløp ?: 0.0
        totalInntekt += aktuellDagsinntekt
    }

    override fun visit(
        dag: NavHelgDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        navHelgDagTeller += 1
        navHelgdager.add(dag)
        collect(dag, dato, økonomi)
    }

    override fun visit(
        dag: ForeldetDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        foreldetDagTeller += 1
        collect(dag, dato, økonomi)
    }

    override fun visit(
        dag: UkjentDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        ukjentDagTeller += 1
        collect(dag, dato, økonomi)
    }
}
