package no.nav.helse.person.inntekt

import java.util.*
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto

class SkjønnsmessigFastsatt internal constructor(
    id: UUID,
    inntektsdata: Inntektsdata,
    val overstyrtInntekt: Inntektsopplysning,
    val omregnetÅrsinntekt: Inntektsopplysning?
) : Inntektsopplysning(id, inntektsdata) {

    fun endreOmregnetÅrsinntekt(overstyrtInntekt: Arbeidsgiverinntekt) =
        SkjønnsmessigFastsatt(id, inntektsdata, overstyrtInntekt, overstyrtInntekt.omregnetÅrsinntekt())

    override fun dto() =
        InntektsopplysningUtDto.SkjønnsmessigFastsattDto(
            id = id,
            inntektsdata = inntektsdata.dto(),
            overstyrtInntekt = overstyrtInntekt.dto().id
        )

    internal companion object {
        fun gjenopprett(dto: InntektsopplysningInnDto.SkjønnsmessigFastsattDto, inntekter: Map<UUID, Inntektsopplysning>): SkjønnsmessigFastsatt {
            val overstyrtInntekt = inntekter.getValue(dto.overstyrtInntekt)
            return SkjønnsmessigFastsatt(
                id = dto.id,
                inntektsdata = Inntektsdata.gjenopprett(dto.inntektsdata),
                overstyrtInntekt = overstyrtInntekt,
                omregnetÅrsinntekt = overstyrtInntekt.omregnetÅrsinntekt()
            )
        }
    }
}
