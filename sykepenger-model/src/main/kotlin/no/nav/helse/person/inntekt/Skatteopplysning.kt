package no.nav.helse.person.inntekt

import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*
import no.nav.helse.etterlevelse.Inntektsubsumsjon
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.summer

data class Skatteopplysning(
    val hendelseId: UUID,
    val beløp: Inntekt,
    val måned: YearMonth,
    val type: Inntekttype,
    val fordel: String,
    val beskrivelse: String,
    val tidsstempel: LocalDateTime = LocalDateTime.now()
) {
    enum class Inntekttype {
        LØNNSINNTEKT,
        NÆRINGSINNTEKT,
        PENSJON_ELLER_TRYGD,
        YTELSE_FRA_OFFENTLIGE;
    }

    companion object {
        fun omregnetÅrsinntekt(liste: List<Skatteopplysning>) = liste
            .map { it.beløp }
            .summer()
            .coerceAtLeast(Inntekt.INGEN)
            .div(3)

        fun List<Skatteopplysning>.subsumsjonsformat() = this.map {
            Inntektsubsumsjon(
                beløp = it.beløp.månedlig,
                årMåned = it.måned,
                type = it.type.toString(),
                fordel = it.fordel,
                beskrivelse = it.beskrivelse
            )
        }
    }
}
