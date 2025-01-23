package no.nav.helse.dto.deserialisering

import java.util.*

sealed class InntektsopplysningInnDto {
    abstract val id: UUID
    abstract val inntektsdata: InntektsdataInnDto

    data class InfotrygdDto(
        override val id: UUID,
        override val inntektsdata: InntektsdataInnDto
    ) : InntektsopplysningInnDto()

    data class ArbeidsgiverinntektDto(
        override val id: UUID,
        override val inntektsdata: InntektsdataInnDto,
        val kilde: KildeDto
    ) : InntektsopplysningInnDto() {
        enum class KildeDto {
            Arbeidsgiver,
            AOrdningen
        }
    }

    data class SkattSykepengegrunnlagDto(
        override val id: UUID,
        override val inntektsdata: InntektsdataInnDto
    ) : InntektsopplysningInnDto()
}
