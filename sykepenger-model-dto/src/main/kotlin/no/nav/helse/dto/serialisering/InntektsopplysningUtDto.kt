package no.nav.helse.dto.serialisering

import java.util.*

sealed class InntektsopplysningUtDto {
    abstract val id: UUID
    abstract val inntektsdata: InntektsdataUtDto

    data class InfotrygdDto(
        override val id: UUID,
        override val inntektsdata: InntektsdataUtDto
    ) : InntektsopplysningUtDto()

    data class ArbeidsgiverinntektDto(
        override val id: UUID,
        override val inntektsdata: InntektsdataUtDto,
        val kilde: KildeDto
    ) : InntektsopplysningUtDto() {
        enum class KildeDto {
            Arbeidsgiver,
            AOrdningen
        }

    }

    data class SkattSykepengegrunnlagDto(
        override val id: UUID,
        override val inntektsdata: InntektsdataUtDto
    ) : InntektsopplysningUtDto()
}
