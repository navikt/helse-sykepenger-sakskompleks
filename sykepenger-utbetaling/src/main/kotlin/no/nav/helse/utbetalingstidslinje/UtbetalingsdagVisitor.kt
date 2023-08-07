package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.økonomi.Økonomi

interface UtbetalingsdagVisitor {
    fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.NavDag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.NavHelgDag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.Arbeidsdag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.Fridag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.AvvistDag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.ForeldetDag, dato: LocalDate, økonomi: Økonomi) {}
    fun visit(dag: Utbetalingsdag.UkjentDag, dato: LocalDate, økonomi: Økonomi) {}
}