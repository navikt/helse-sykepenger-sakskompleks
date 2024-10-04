package no.nav.helse.inspectors

import java.time.LocalDate
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.ArbeidsgiverHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsgiverdag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.ForeldetSykedag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.ProblemDag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.økonomi.Økonomi
import kotlin.reflect.KClass

internal val Sykdomstidslinje.inspektør get() = SykdomstidslinjeInspektør(this)

internal class SykdomstidslinjeInspektør(tidslinje: Sykdomstidslinje) {
    internal val dager = mutableMapOf<LocalDate, Dag>()
    internal val kilder = mutableMapOf<LocalDate, Hendelseskilde>()
    internal val grader = mutableMapOf<LocalDate, Int>()
    internal val problemdagmeldinger = mutableMapOf<LocalDate, String>()
    internal val låstePerioder = tidslinje.låstePerioder
    internal val dagteller = mutableMapOf<KClass<out Dag>, Int>()
    internal val førsteIkkeUkjenteDag get() = dager.filterNot { (_, b) -> b is UkjentDag }.keys.minOrNull()
    internal val antallDager get() = dager.size

    init {
        tidslinje.forEach { dag ->
            when (dag) {
                is ArbeidsgiverHelgedag -> set(dag, dag.dato, dag.økonomi, dag.kilde)
                is Arbeidsgiverdag -> set(dag, dag.dato, dag.økonomi, dag.kilde)
                is Sykedag -> set(dag, dag.dato, dag.økonomi, dag.kilde)
                is SykHelgedag -> set(dag, dag.dato, dag.økonomi, dag.kilde)
                is ForeldetSykedag -> set(dag, dag.dato, dag.økonomi, dag.kilde)
                is ProblemDag -> set(dag, dag.dato, dag.kilde, dag.melding)
                is Dag.AndreYtelser,
                is Dag.ArbeidIkkeGjenopptattDag,
                is Arbeidsdag,
                is Feriedag,
                is FriskHelgedag,
                is Permisjonsdag,
                is Dag.SykedagNav,
                is UkjentDag -> set(dag, dag.dato, dag.kilde)
            }
        }
    }

    internal operator fun get(dato: LocalDate) = dager[dato]
        ?: throw IllegalArgumentException("No dag for $dato")

    internal val size get() = dager.size

    private fun set(dag: Dag, dato: LocalDate, kilde: Hendelseskilde) {
        dager[dato] = dag
        kilder[dato] = kilde
        dagteller.compute(dag::class) { _, value -> 1 + (value ?: 0) }
    }

    private fun set(dag: Dag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        this.grader[dato] = økonomi.inspektør.gradProsent.toInt()
        set(dag, dato, kilde)
    }

    private fun set(dag: Dag, dato: LocalDate, kilde: Hendelseskilde, melding: String) {
        problemdagmeldinger[dato] = melding
        set(dag, dato, kilde)
    }
}
