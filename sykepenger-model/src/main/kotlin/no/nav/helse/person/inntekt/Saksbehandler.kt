package no.nav.helse.person.inntekt

import java.util.*
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto

internal class Saksbehandler internal constructor(
    id: UUID,
    inntektsdata: Inntektsdata,
    val omregnetÅrsinntekt: Inntektsopplysning,
    val overstyrtInntekt: Inntektsopplysning
) : Inntektsopplysning(id, inntektsdata) {

    init {
        check(omregnetÅrsinntekt !is Saksbehandler) {
            "kan ikke være saksbehandler"
        }
    }

    override fun dto() =
        InntektsopplysningUtDto.SaksbehandlerDto(
            id = id,
            inntektsdata = inntektsdata.dto(),
            overstyrtInntekt = overstyrtInntekt.dto().id
        )

    internal companion object {
        fun gjenopprett(dto: InntektsopplysningInnDto.SaksbehandlerDto, inntekter: Map<UUID, Inntektsopplysning>): Saksbehandler {
            val overstyrtInntekt = inntekter.getValue(dto.overstyrtInntekt)
            return Saksbehandler(
                id = dto.id,
                inntektsdata = Inntektsdata.gjenopprett(dto.inntektsdata),
                omregnetÅrsinntekt = when (overstyrtInntekt) {
                    is Arbeidsgiverinntekt,
                    is Infotrygd,
                    is SkattSykepengegrunnlag -> overstyrtInntekt

                    is Saksbehandler -> overstyrtInntekt.omregnetÅrsinntekt
                },
                overstyrtInntekt = overstyrtInntekt
            )
        }
    }
}
