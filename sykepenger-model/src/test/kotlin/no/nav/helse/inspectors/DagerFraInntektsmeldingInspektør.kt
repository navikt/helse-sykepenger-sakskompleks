package no.nav.helse.inspectors

import java.time.LocalDate
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.omsluttendePeriode

internal val DagerFraInntektsmelding.inspektør get() = DagerFraInntektsmeldingInspektør(this)

internal class DagerFraInntektsmeldingInspektør(dager: DagerFraInntektsmelding) {
    val gjenståendeDager: Set<LocalDate> = dager.gjenståendeDager
    val periode: Periode? = gjenståendeDager.omsluttendePeriode
}