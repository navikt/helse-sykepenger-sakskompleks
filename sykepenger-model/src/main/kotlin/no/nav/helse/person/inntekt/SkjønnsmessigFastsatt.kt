package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto
import no.nav.helse.økonomi.Inntekt

class SkjønnsmessigFastsatt internal constructor(
    id: UUID,
    dato: LocalDate,
    hendelseId: UUID,
    beløp: Inntekt,
    val overstyrtInntekt: Inntektsopplysning?,
    tidsstempel: LocalDateTime
) : Inntektsopplysning(id, hendelseId, dato, beløp, tidsstempel, overstyrtInntekt?.kilde) {
    constructor(dato: LocalDate, hendelseId: UUID, beløp: Inntekt, tidsstempel: LocalDateTime) : this(UUID.randomUUID(), dato, hendelseId, beløp, null, tidsstempel)

    override fun omregnetÅrsinntekt(): Inntektsopplysning {
        return checkNotNull(overstyrtInntekt) { "overstyrt inntekt kan ikke være null" }.omregnetÅrsinntekt()
    }

    override fun gjenbrukbarInntekt(beløp: Inntekt?) = checkNotNull(overstyrtInntekt) { "overstyrt inntekt kan ikke være null" }.gjenbrukbarInntekt(beløp)

    override fun blirOverstyrtAv(ny: Inntektsopplysning): Inntektsopplysning {
        return ny.overstyrer(this)
    }

    override fun overstyrer(gammel: Saksbehandler) = kopierMed(gammel)
    override fun overstyrer(gammel: SkjønnsmessigFastsatt) = kopierMed(gammel)
    override fun overstyrer(gammel: IkkeRapportert) = kopierMed(gammel)
    override fun overstyrer(gammel: SkattSykepengegrunnlag) = kopierMed(gammel)
    override fun overstyrer(gammel: Inntektsmelding) = kopierMed(gammel)

    private fun kopierMed(overstyrtInntekt: Inntektsopplysning) =
        SkjønnsmessigFastsatt(id, dato, hendelseId, beløp, overstyrtInntekt, tidsstempel)

    override fun erSamme(other: Inntektsopplysning) =
        other is SkjønnsmessigFastsatt && this.dato == other.dato && this.beløp == other.beløp

    override fun dto() =
        InntektsopplysningUtDto.SkjønnsmessigFastsattDto(
            id = id,
            hendelseId = hendelseId,
            dato = dato,
            beløp = beløp.dto(),
            tidsstempel = tidsstempel,
            overstyrtInntekt = overstyrtInntekt!!.dto().id,
            kilde = overstyrtInntekt.dto().kilde
        )

    internal companion object {
        fun gjenopprett(dto: InntektsopplysningInnDto.SkjønnsmessigFastsattDto, inntekter: Map<UUID, Inntektsopplysning>): SkjønnsmessigFastsatt {
            val overstyrtInntekt = inntekter.getValue(dto.overstyrtInntekt)
            return SkjønnsmessigFastsatt(
                id = dto.id,
                hendelseId = dto.hendelseId,
                dato = dto.dato,
                beløp = Inntekt.gjenopprett(dto.beløp),
                tidsstempel = dto.tidsstempel,
                overstyrtInntekt = overstyrtInntekt
            )
        }
    }
}
