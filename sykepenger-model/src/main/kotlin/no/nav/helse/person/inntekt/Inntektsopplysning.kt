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
import no.nav.helse.person.Arbeidsgiver
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

sealed class Inntektsopplysning(
    val id: UUID,
    val inntektsdata: Inntektsdata
) {
    internal fun fastsattÅrsinntekt() = inntektsdata.beløp
    internal fun omregnetÅrsinntekt() = when (this) {
        is Infotrygd,
        is Arbeidsgiverinntekt,
        is Saksbehandler,
        is SkattSykepengegrunnlag -> this
        is SkjønnsmessigFastsatt -> this.omregnetÅrsinntekt!!
    }

    fun erUlik(other: Inntektsopplysning): Boolean {
        if (this.id != other.id) return true
        return when (this) {
            is Saksbehandler -> overstyrtInntekt.id != (other as Saksbehandler).overstyrtInntekt.id
            is SkjønnsmessigFastsatt -> overstyrtInntekt.id != (other as SkjønnsmessigFastsatt).overstyrtInntekt.id
            is SkattSykepengegrunnlag,
            is Infotrygd,
            is Arbeidsgiverinntekt -> false
        }
    }

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
                    is Saksbehandler,
                    is SkjønnsmessigFastsatt -> emptyList()
                },
                forklaring = forklaring,
                oppfylt = oppfylt
            )
        )
    }

    internal fun gjenbrukbarInntekt(beløp: Inntekt? = null): Arbeidsgiverinntekt? = when (this) {
        is Arbeidsgiverinntekt -> beløp?.let { Arbeidsgiverinntekt(UUID.randomUUID(), inntektsdata.copy(beløp = it), kilde) } ?: this
        is Saksbehandler -> overstyrtInntekt.gjenbrukbarInntekt(beløp ?: this.inntektsdata.beløp)
        is SkjønnsmessigFastsatt -> checkNotNull(overstyrtInntekt) { "overstyrt inntekt kan ikke være null" }.gjenbrukbarInntekt(beløp)

        is Infotrygd,
        is SkattSykepengegrunnlag -> null
    }

    internal fun lagreTidsnærInntekt(
        skjæringstidspunkt: LocalDate,
        arbeidsgiver: Arbeidsgiver,
        aktivitetslogg: IAktivitetslogg,
        nyArbeidsgiverperiode: Boolean,
        orgnummer: String,
        beløp: Inntekt? = null
    ) {
        val gjenbrukbarInntekt = gjenbrukbarInntekt(beløp) ?: return
        arbeidsgiver.lagreTidsnærInntektsmelding(
            skjæringstidspunkt = skjæringstidspunkt,
            orgnummer = orgnummer,
            arbeidsgiverinntekt = gjenbrukbarInntekt,
            aktivitetslogg = aktivitetslogg,
            nyArbeidsgiverperiode = nyArbeidsgiverperiode
        )
    }

    internal companion object {
        internal fun erOmregnetÅrsinntektEndret(før: Inntektsopplysning, etter: Inntektsopplysning) =
            erOmregnetÅrsinntektEndret(listOf(før), listOf(etter))

        internal fun erOmregnetÅrsinntektEndret(før: List<Inntektsopplysning>, etter: List<Inntektsopplysning>) =
            omregnetÅrsinntekt(før) != omregnetÅrsinntekt(etter)

        private fun omregnetÅrsinntekt(liste: List<Inntektsopplysning>) = liste
            .map { it.omregnetÅrsinntekt().inntektsdata.beløp }
            .map { it.årlig.toInt() }

        internal fun List<Inntektsopplysning>.markerFlereArbeidsgivere(aktivitetslogg: IAktivitetslogg) {
            if (distinctBy { it.inntektsdata.dato }.size <= 1 && none { it is SkattSykepengegrunnlag }) return
            aktivitetslogg.varsel(Varselkode.RV_VV_2)
        }

        internal fun List<Inntektsopplysning>.validerSkjønnsmessigAltEllerIntet() {
            check(all { it is SkjønnsmessigFastsatt } || none { it is SkjønnsmessigFastsatt }) { "Enten så må alle inntektsopplysninger var skjønnsmessig fastsatt, eller så må ingen være det" }
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
                    is InntektsopplysningInnDto.SkjønnsmessigFastsattDto -> SkjønnsmessigFastsatt.gjenopprett(
                        dto,
                        inntekter
                    )
                }
            }
            return inntektsopplysning
        }
    }

    internal abstract fun dto(): InntektsopplysningUtDto
}
