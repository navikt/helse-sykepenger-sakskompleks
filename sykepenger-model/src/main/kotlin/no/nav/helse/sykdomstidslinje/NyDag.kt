package no.nav.helse.sykdomstidslinje

import java.time.LocalDate

internal sealed class NyDag(private val dato: LocalDate) {
    internal class NyUkjentDag(dato: LocalDate) : NyDag(dato)
    internal class NyArbeidsdag(dato: LocalDate) : NyDag(dato)
}

