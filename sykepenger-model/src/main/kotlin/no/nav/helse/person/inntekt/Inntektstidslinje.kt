package no.nav.helse.person.inntekt

import java.time.LocalDate
import no.nav.helse.person.beløp.Beløpsdag
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.økonomi.Inntekt

class Inntektstidslinje(
    skjæringstidspunkt: LocalDate,
    private val fastsattÅrsinntekt: Inntekt?,
    beløpstidslinje: Beløpstidslinje,
    private val gjelderTilOgMed: LocalDate
) {
    private val beløpstidslinje = beløpstidslinje.fraOgMed(skjæringstidspunkt.plusDays(1))

    internal operator fun get(dato: LocalDate): Inntekt? {
        if (dato > gjelderTilOgMed) return null // Arbeidsgiveren har opphørt/deaktivert
        val dag = beløpstidslinje[dato]
        if (dag is Beløpsdag) return dag.beløp // Direktetreff i beløpstidslinjen
        return fastsattÅrsinntekt
    }
}
