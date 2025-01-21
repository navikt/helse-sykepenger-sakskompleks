package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.dto.deserialisering.InntektsdataInnDto
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.serialisering.InntektsdataUtDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 8-15`
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.inntekt.Skatteopplysning.Companion.subsumsjonsformat
import no.nav.helse.økonomi.Inntekt

data class Inntektsdata(
    val hendelseId: UUID,
    val dato: LocalDate,
    val beløp: Inntekt,
    val tidsstempel: LocalDateTime
) {

    fun funksjoneltLik(other: Inntektsdata) =
        this.dato == other.dato && this.beløp == other.beløp

    fun dto() = InntektsdataUtDto(
        hendelseId = hendelseId,
        dato = dato,
        beløp = beløp.dto(),
        tidsstempel = tidsstempel
    )

    companion object {
        fun ingen(hendelseId: UUID, dato: LocalDate, tidsstempel: LocalDateTime = LocalDateTime.now()) = Inntektsdata(
            hendelseId = hendelseId,
            dato = dato,
            beløp = Inntekt.INGEN,
            tidsstempel = tidsstempel
        )

        fun gjenopprett(dto: InntektsdataInnDto) = Inntektsdata(
            hendelseId = dto.hendelseId,
            dato = dto.dato,
            beløp = Inntekt.gjenopprett(dto.beløp),
            tidsstempel = dto.tidsstempel
        )
    }
}

internal sealed class Inntektsopplysning(
    val id: UUID,
    val inntektsdata: Inntektsdata
) {
    fun funksjoneltLik(other: Inntektsopplysning) =
        this::class == other::class && this.inntektsdata.funksjoneltLik(other.inntektsdata)

    internal fun subsumerArbeidsforhold(
        subsumsjonslogg: Subsumsjonslogg,
        organisasjonsnummer: String,
        forklaring: String,
        oppfylt: Boolean
    ) {
        subsumsjonslogg.logg(
            `§ 8-15`(
                skjæringstidspunkt = inntektsdata.dato,
                organisasjonsnummer = organisasjonsnummer,
                inntekterSisteTreMåneder = when (this) {
                    is SkattSykepengegrunnlag -> inntektsopplysninger.subsumsjonsformat()
                    is Infotrygd,
                    is Arbeidsgiverinntekt,
                    is Saksbehandler -> emptyList()
                },
                forklaring = forklaring,
                oppfylt = oppfylt
            )
        )
    }

    internal fun gjenbrukbarInntekt(beløp: Inntekt? = null): Arbeidsgiverinntekt? = when (this) {
        is Arbeidsgiverinntekt -> beløp?.let { Arbeidsgiverinntekt(UUID.randomUUID(), inntektsdata.copy(beløp = it), kilde) } ?: this
        is Saksbehandler -> overstyrtInntekt.gjenbrukbarInntekt(beløp ?: this.inntektsdata.beløp)

        is Infotrygd,
        is SkattSykepengegrunnlag -> null
    }

    internal companion object {
        internal fun List<Inntektsopplysning>.markerFlereArbeidsgivere(aktivitetslogg: IAktivitetslogg) {
            if (distinctBy { it.inntektsdata.dato }.size <= 1 && none { it is SkattSykepengegrunnlag }) return
            aktivitetslogg.varsel(Varselkode.RV_VV_2)
        }

        internal fun gjenopprett(
            dto: InntektsopplysningInnDto,
            inntekter: MutableMap<UUID, Inntektsopplysning>
        ): Inntektsopplysning {
            val inntektsopplysning = inntekter.getOrPut(dto.id) {
                when (dto) {
                    is InntektsopplysningInnDto.InfotrygdDto -> Infotrygd.gjenopprett(dto)
                    is InntektsopplysningInnDto.ArbeidsgiverinntektDto -> Arbeidsgiverinntekt.gjenopprett(dto)
                    is InntektsopplysningInnDto.SaksbehandlerDto -> Saksbehandler.gjenopprett(dto, inntekter)
                    is InntektsopplysningInnDto.SkattSykepengegrunnlagDto -> SkattSykepengegrunnlag.gjenopprett(dto)
                }
            }
            return inntektsopplysning
        }
    }

    internal abstract fun dto(): InntektsopplysningUtDto
}
