package no.nav.helse.spleis.speil.dto

import java.time.LocalDate
import java.util.*
import no.nav.helse.spleis.speil.builders.SykepengegrunnlagsgrenseDTO

interface Vilkårsgrunnlag {
    val skjæringstidspunkt: LocalDate
    val beregningsgrunnlag: Double
    val sykepengegrunnlag: Double
    val inntekter: List<Arbeidsgiverinntekt>
    val arbeidsgiverrefusjoner: List<Arbeidsgiverrefusjon>
}

data class SpleisVilkårsgrunnlag(
    override val skjæringstidspunkt: LocalDate,
    override val beregningsgrunnlag: Double,
    override val sykepengegrunnlag: Double,
    override val inntekter: List<Arbeidsgiverinntekt>,
    override val arbeidsgiverrefusjoner: List<Arbeidsgiverrefusjon>,
    val omregnetÅrsinntekt: Double,
    val grunnbeløp: Int,
    val sykepengegrunnlagsgrense: SykepengegrunnlagsgrenseDTO,
    val antallOpptjeningsdagerErMinst: Int,
    val opptjeningFra: LocalDate,
    val oppfyllerKravOmMinstelønn: Boolean,
    val oppfyllerKravOmOpptjening: Boolean,
    val oppfyllerKravOmMedlemskap: Boolean?
) : Vilkårsgrunnlag

data class InfotrygdVilkårsgrunnlag(
    override val skjæringstidspunkt: LocalDate,
    override val beregningsgrunnlag: Double,
    override val sykepengegrunnlag: Double,
    override val inntekter: List<Arbeidsgiverinntekt>,
    override val arbeidsgiverrefusjoner: List<Arbeidsgiverrefusjon>
) : Vilkårsgrunnlag

data class Arbeidsgiverinntekt(
    val organisasjonsnummer: String,
    val omregnetÅrsinntekt: Inntekt,
    val skjønnsmessigFastsatt: SkjønnsmessigFastsattDTO? = null,
    val fom: LocalDate,
    val tom: LocalDate?,
    val deaktivert: Boolean
)

data class Arbeidsgiverrefusjon(
    val arbeidsgiver: String,
    val refusjonsopplysninger: List<Refusjonselement>
)

data class Refusjonselement(
    val fom: LocalDate,
    val tom: LocalDate?,
    val beløp: Double,
    val meldingsreferanseId: UUID
)

enum class Inntektkilde {
    Saksbehandler, Inntektsmelding, Infotrygd, AOrdningen, IkkeRapportert
}

data class SkjønnsmessigFastsattDTO(
    val årlig: Double,
    val månedlig: Double
)

data class Inntekt(
    val kilde: Inntektkilde,
    val beløp: Double,
    val månedsbeløp: Double
)
