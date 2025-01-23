package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.util.*
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto

internal class SkattSykepengegrunnlag(
    id: UUID,
    inntektsdata: Inntektsdata
) : Inntektsopplysning(id, inntektsdata) {
    internal companion object {
        internal fun ikkeRapportert(dato: LocalDate, meldingsreferanseId: UUID) =
            SkattSykepengegrunnlag(
                id = UUID.randomUUID(),
                inntektsdata = Inntektsdata.ingen(meldingsreferanseId, dato)
            )
        internal fun fraSkatt(inntektsdata: Inntektsdata) =
            SkattSykepengegrunnlag(
                id = UUID.randomUUID(),
                inntektsdata = inntektsdata
            )

        internal fun gjenopprett(dto: InntektsopplysningInnDto.SkattSykepengegrunnlagDto): SkattSykepengegrunnlag {
            return SkattSykepengegrunnlag(
                id = dto.id,
                inntektsdata = Inntektsdata.gjenopprett(dto.inntektsdata)
            )
        }
    }

    override fun dto() =
        InntektsopplysningUtDto.SkattSykepengegrunnlagDto(
            id = id,
            inntektsdata = inntektsdata.dto()
        )
}
