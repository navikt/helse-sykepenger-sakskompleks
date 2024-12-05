package no.nav.helse.dto.deserialisering

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.AnsattPeriodeDto
import no.nav.helse.dto.InntektbeløpDto
import no.nav.helse.dto.InntektskildeDto
import no.nav.helse.dto.SkatteopplysningDto
import no.nav.helse.dto.SubsumsjonDto

sealed class InntektsopplysningInnDto {
    abstract val id: UUID
    abstract val hendelseId: UUID
    abstract val dato: LocalDate
    abstract val beløp: InntektbeløpDto.MånedligDouble?
    abstract val tidsstempel: LocalDateTime
    abstract val kilde: InntektskildeDto?

    data class IkkeRapportertDto(
        override val id: UUID,
        override val hendelseId: UUID,
        override val dato: LocalDate,
        override val tidsstempel: LocalDateTime
    ) : InntektsopplysningInnDto() {
        override val beløp: InntektbeløpDto.MånedligDouble? = null
        override val kilde: InntektskildeDto = InntektskildeDto.AOrdningen
    }

    data class InfotrygdDto(
        override val id: UUID,
        override val hendelseId: UUID,
        override val dato: LocalDate,
        override val beløp: InntektbeløpDto.MånedligDouble,
        override val tidsstempel: LocalDateTime
    ) : InntektsopplysningInnDto() {
        override val kilde: InntektskildeDto = InntektskildeDto.Arbeidsgiver
    }

    data class SaksbehandlerDto(
        override val id: UUID,
        override val hendelseId: UUID,
        override val dato: LocalDate,
        override val beløp: InntektbeløpDto.MånedligDouble,
        override val tidsstempel: LocalDateTime,
        val forklaring: String?,
        val subsumsjon: SubsumsjonDto?,
        val overstyrtInntekt: UUID,
        override val kilde: InntektskildeDto?
    ) : InntektsopplysningInnDto()

    data class SkjønnsmessigFastsattDto(
        override val id: UUID,
        override val hendelseId: UUID,
        override val dato: LocalDate,
        override val beløp: InntektbeløpDto.MånedligDouble,
        override val tidsstempel: LocalDateTime,
        val overstyrtInntekt: UUID,
        override val kilde: InntektskildeDto?
    ) : InntektsopplysningInnDto()

    data class InntektsmeldingDto(
        override val id: UUID,
        override val hendelseId: UUID,
        override val dato: LocalDate,
        override val beløp: InntektbeløpDto.MånedligDouble,
        override val tidsstempel: LocalDateTime,
        // I tidligere tilfeller der kilde ikke finnes kunne kilde bare komme fra vanlig inntektsmelding
        // Nå kan den også ha kilde AOrdning dersom inntektsmeldingen aldri kommer
        override val kilde: InntektskildeDto = InntektskildeDto.Arbeidsgiver
    ) : InntektsopplysningInnDto()

    data class SkattSykepengegrunnlagDto(
        override val id: UUID,
        override val hendelseId: UUID,
        override val dato: LocalDate,
        override val tidsstempel: LocalDateTime,
        val inntektsopplysninger: List<SkatteopplysningDto>,
        val ansattPerioder: List<AnsattPeriodeDto>
    ) : InntektsopplysningInnDto() {
        override val beløp: InntektbeløpDto.MånedligDouble? = null
        override val kilde: InntektskildeDto = InntektskildeDto.AOrdningen
    }
}