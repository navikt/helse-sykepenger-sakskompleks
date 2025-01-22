package no.nav.helse.inspectors

import java.time.LocalDateTime
import java.util.*
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.økonomi.Inntekt

internal val Inntektsopplysning.inspektør get() = InntektsopplysningInspektør(this)

internal class InntektsopplysningInspektør(inntektsopplysning: Inntektsopplysning) {

    val beløp: Inntekt = inntektsopplysning.inntektsdata.beløp
    val hendelseId: UUID = inntektsopplysning.inntektsdata.hendelseId
    val tidsstempel: LocalDateTime = inntektsopplysning.inntektsdata.tidsstempel
}
