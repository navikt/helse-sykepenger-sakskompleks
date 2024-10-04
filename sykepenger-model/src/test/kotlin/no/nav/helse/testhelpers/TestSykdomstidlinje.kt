package no.nav.helse.testhelpers

import no.nav.helse.hendelser.Periode
import no.nav.helse.sykdomstidslinje.BesteStrategy
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import java.time.LocalDate

internal class TestSykdomstidslinje(
    private val førsteDato: LocalDate,
    private val sisteDato: LocalDate,
    private val daggenerator: (LocalDate, LocalDate, Prosentdel, Hendelseskilde) -> Sykdomstidslinje
) {
    private var grad: Prosentdel = 100.prosent

    internal infix fun grad(grad: Number) = this.also { it.grad = grad.prosent }

    internal fun asSykdomstidslinje(kilde: Hendelseskilde = TestEvent.testkilde) = daggenerator(førsteDato, sisteDato, grad, kilde)
    internal infix fun merge(annen: TestSykdomstidslinje) = this.asSykdomstidslinje().merge(annen)
    internal fun merge(annen: Sykdomstidslinje) = this.asSykdomstidslinje().merge(annen)
    internal fun lås(periode: Periode) = this.asSykdomstidslinje().also { it.lås(periode) }
    internal fun låsOpp(periode: Periode) = this.asSykdomstidslinje().also { it.låsOpp(periode) }
}

internal infix fun LocalDate.jobbTil(sisteDato: LocalDate) =
    TestSykdomstidslinje(this, sisteDato) { første: LocalDate, siste: LocalDate, _, kilde: Hendelseskilde ->
        Sykdomstidslinje.arbeidsdager(første, siste, kilde)
    }

internal infix fun LocalDate.ferieTil(sisteDato: LocalDate) =
    TestSykdomstidslinje(this, sisteDato) { første: LocalDate, siste: LocalDate, _, kilde: Hendelseskilde ->
        Sykdomstidslinje.feriedager(første, siste, kilde)
    }

internal infix fun LocalDate.sykTil(sisteDato: LocalDate) = TestSykdomstidslinje(this, sisteDato, Sykdomstidslinje.Companion::sykedager)

internal infix fun LocalDate.permisjonTil(sisteDato: LocalDate) = TestSykdomstidslinje(this, sisteDato) { førstedato, sistedato, _, kilde ->
    Sykdomstidslinje.permisjonsdager(førstedato, sistedato, kilde)
}

internal infix fun LocalDate.betalingTil(sisteDato: LocalDate) = TestSykdomstidslinje(this, sisteDato, Sykdomstidslinje.Companion::arbeidsgiverdager )

internal fun Sykdomstidslinje.merge(testTidslinje: TestSykdomstidslinje): Sykdomstidslinje = this.merge(testTidslinje.asSykdomstidslinje())

internal fun List<TestSykdomstidslinje>.merge(beste: BesteStrategy) = this.map { it.asSykdomstidslinje() }.merge(beste)
