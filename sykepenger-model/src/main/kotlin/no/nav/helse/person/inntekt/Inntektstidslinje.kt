package no.nav.helse.person.inntekt

import java.time.LocalDate
import no.nav.helse.person.beløp.Beløpsdag
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Dag
import no.nav.helse.person.beløp.UkjentDag

data class Inntektstidslinje(
    private val beløpstidslinje: Beløpstidslinje,
    private val skjæringstidspunkt: LocalDate,
    private val gjelderTilOgMed: LocalDate
) {
    internal operator fun get(dato: LocalDate): Dag {
        if (dato > gjelderTilOgMed) return UkjentDag // Arbeidsgiveren har opphørt/deaktivert
        val dag = beløpstidslinje[dato]
        if (dag is Beløpsdag) return dag // Direktetreff
        val beløpsdager = beløpstidslinje.filterIsInstance<Beløpsdag>()
        val førsteBeløpsdag = beløpsdager.minOfOrNull { it.dato } ?: return UkjentDag
        // Om beløpstidslinjen starter _etter_ skjæringstidspunktet (tilkommen) er det kun direktetreff som gjelder
        if (førsteBeløpsdag > skjæringstidspunkt) return UkjentDag
        return beløpsdager.last().copy(dato = dato)
    }
}
