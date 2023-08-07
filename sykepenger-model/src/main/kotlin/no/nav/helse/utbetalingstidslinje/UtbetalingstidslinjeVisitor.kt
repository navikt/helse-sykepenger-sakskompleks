package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode

interface UtbetalingstidslinjeVisitor : UtbetalingsdagVisitor {
    /**
     * gjeldendePeriode vil være null om det ikke er noen utbetalingsdager her
     */
    fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {}
    fun postVisitUtbetalingstidslinje() {}
}