package no.nav.helse.etterlevelse

import java.time.LocalDate
import no.nav.helse.etterlevelse.Tidslinjedag.Companion.tidslinjeperiodedager
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjeVisitor
import no.nav.helse.økonomi.Økonomi

internal class UtbetalingstidslinjeBuilder(utbetalingstidslinje: Utbetalingstidslinje) : UtbetalingstidslinjeVisitor {
    private val navdager = mutableListOf<Tidslinjedag>()

    init {
        utbetalingstidslinje.accept(this)
    }

    fun dager() = navdager.tidslinjeperiodedager()

    override fun visit(dag: Utbetalingsdag.NavDag, dato: LocalDate, økonomi: Økonomi) {
        visit(dato, "NAVDAG", økonomi)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {
        visit(dato, "AGPDAG", økonomi)
    }

    override fun visit(dag: Utbetalingsdag.NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
        if (navdager.isNotEmpty() && navdager.last().erAvvistDag()) visit(dato, "AVVISTDAG", økonomi) else visit(dato, "NAVDAG", økonomi)
    }

    override fun visit(dag: Utbetalingsdag.Fridag, dato: LocalDate, økonomi: Økonomi) {
        // Dersom vi er inne i en oppholdsperiode ønsker vi ikke å ta med vanlige helger
        if (navdager.isNotEmpty() && navdager.last().erRettFør(dato)) visit(dato, "FRIDAG", økonomi)
    }

    override fun visit(dag: Utbetalingsdag.AvvistDag, dato: LocalDate, økonomi: Økonomi) {
        visit(dato, "AVVISTDAG", økonomi)
    }

    private fun visit(dato: LocalDate, dagtype: String, økonomi: Økonomi?) {
        val grad = økonomi?.brukAvrundetGrad { grad -> grad }
        navdager.add(Tidslinjedag(dato, dagtype, grad))
    }

    companion object {
        internal fun Utbetalingstidslinje.subsumsjonsformat(): List<Tidslinjedag.Tidslinjeperiode> = UtbetalingstidslinjeBuilder(this).dager()
        internal fun List<Utbetalingstidslinje>.subsumsjonsformat(): List<List<Tidslinjedag.Tidslinjeperiode>> = map { it.subsumsjonsformat() }
            .filter { it.isNotEmpty() }
    }
}