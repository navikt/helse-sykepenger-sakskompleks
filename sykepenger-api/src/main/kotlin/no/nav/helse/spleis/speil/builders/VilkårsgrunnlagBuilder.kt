package no.nav.helse.spleis.speil.builders

import java.time.LocalDate
import java.util.*
import no.nav.helse.Grunnbeløp
import no.nav.helse.dto.InntektDto
import no.nav.helse.dto.MedlemskapsvurderingDto
import no.nav.helse.dto.serialisering.ArbeidsgiverInntektsopplysningUtDto
import no.nav.helse.dto.serialisering.InntektsgrunnlagUtDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto
import no.nav.helse.dto.serialisering.SaksbehandlerUtDto
import no.nav.helse.dto.serialisering.SkjønnsmessigFastsattUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlaghistorikkUtDto
import no.nav.helse.spleis.speil.dto.GhostPeriodeDTO
import no.nav.helse.spleis.speil.dto.InfotrygdVilkårsgrunnlag
import no.nav.helse.spleis.speil.dto.NyttInntektsforholdPeriodeDTO
import no.nav.helse.spleis.speil.dto.SkjønnsmessigFastsattDTO
import no.nav.helse.spleis.speil.dto.SpleisVilkårsgrunnlag
import no.nav.helse.spleis.speil.dto.Vilkårsgrunnlag
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.årlig

internal abstract class IVilkårsgrunnlag(
    val skjæringstidspunkt: LocalDate,
    val beregningsgrunnlag: Double,
    val sykepengegrunnlag: Double,
    val inntekter: List<IArbeidsgiverinntekt>,
    val nyeInntekterUnderveis: List<INyInntektUnderveis>,
    val id: UUID
) {
    abstract fun toDTO(refusjonsopplysningerFraBehandlinger: List<IArbeidsgiverrefusjon>): Vilkårsgrunnlag
    fun inngårIkkeISammenligningsgrunnlag(organisasjonsnummer: String) = inntekter.none { it.arbeidsgiver == organisasjonsnummer }
    open fun potensiellGhostperiode(
        organisasjonsnummer: String,
        sykefraværstilfeller: Map<LocalDate, List<ClosedRange<LocalDate>>>
    ): GhostPeriodeDTO? {
        if (inntekter.size < 2 || this.skjæringstidspunkt !in sykefraværstilfeller) return null
        val inntekten = inntekter.firstOrNull { it.arbeidsgiver == organisasjonsnummer }
        if (inntekten == null) return null
        val sisteDag = minOf(inntekten.tom, sykefraværstilfeller.getValue(skjæringstidspunkt).maxOf { it.endInclusive })
        return GhostPeriodeDTO(
            id = UUID.randomUUID(),
            fom = inntekten.fom,
            tom = sisteDag,
            skjæringstidspunkt = skjæringstidspunkt,
            vilkårsgrunnlagId = this.id,
            deaktivert = inntekten.deaktivert
        )
    }
}

internal class ISpleisGrunnlag(
    skjæringstidspunkt: LocalDate,
    beregningsgrunnlag: Double,
    inntekter: List<IArbeidsgiverinntekt>,
    sykepengegrunnlag: Double,
    id: UUID,
    nyeInntekterUnderveis: List<INyInntektUnderveis>,
    val overstyringer: Set<UUID>,
    val omregnetÅrsinntekt: Double,
    val grunnbeløp: Int,
    val sykepengegrunnlagsgrense: SykepengegrunnlagsgrenseDTO,
    val meldingsreferanseId: UUID?,
    val antallOpptjeningsdagerErMinst: Int,
    val oppfyllerKravOmMinstelønn: Boolean,
    val oppfyllerKravOmOpptjening: Boolean,
    val oppfyllerKravOmMedlemskap: Boolean?
) : IVilkårsgrunnlag(skjæringstidspunkt, beregningsgrunnlag, sykepengegrunnlag, inntekter, nyeInntekterUnderveis, id) {

    override fun toDTO(refusjonsopplysningerFraBehandlinger: List<IArbeidsgiverrefusjon>): Vilkårsgrunnlag {
        return SpleisVilkårsgrunnlag(
            skjæringstidspunkt = skjæringstidspunkt,
            beregningsgrunnlag = beregningsgrunnlag,
            omregnetÅrsinntekt = omregnetÅrsinntekt,
            sykepengegrunnlag = sykepengegrunnlag,
            inntekter = inntekter.map { it.toDTO() },
            arbeidsgiverrefusjoner = refusjonsopplysningerFraBehandlinger.map { it.toDTO() },
            grunnbeløp = grunnbeløp,
            sykepengegrunnlagsgrense = sykepengegrunnlagsgrense,
            antallOpptjeningsdagerErMinst = antallOpptjeningsdagerErMinst,
            opptjeningFra = skjæringstidspunkt.minusDays(antallOpptjeningsdagerErMinst.toLong()),
            oppfyllerKravOmMinstelønn = oppfyllerKravOmMinstelønn,
            oppfyllerKravOmOpptjening = oppfyllerKravOmOpptjening,
            oppfyllerKravOmMedlemskap = oppfyllerKravOmMedlemskap
        )
    }
}

class SykepengegrunnlagsgrenseDTO(
    val grunnbeløp: Int,
    val grense: Int,
    val virkningstidspunkt: LocalDate,
) {
    companion object {
        fun fra6GBegrensning(`6G`: InntektDto): SykepengegrunnlagsgrenseDTO {
            val `1G` = `6G`.årlig.beløp / 6
            return SykepengegrunnlagsgrenseDTO(
                grunnbeløp = `1G`.toInt(),
                grense = `6G`.årlig.beløp.toInt(),
                virkningstidspunkt = Grunnbeløp.virkningstidspunktFor(`1G`.årlig)
            )
        }
    }
}

internal class IInfotrygdGrunnlag(
    skjæringstidspunkt: LocalDate,
    beregningsgrunnlag: Double,
    inntekter: List<IArbeidsgiverinntekt>,
    sykepengegrunnlag: Double,
    id: UUID
) : IVilkårsgrunnlag(skjæringstidspunkt, beregningsgrunnlag, sykepengegrunnlag, inntekter, emptyList(), id) {

    override fun toDTO(refusjonsopplysningerFraBehandlinger: List<IArbeidsgiverrefusjon>): Vilkårsgrunnlag {
        return InfotrygdVilkårsgrunnlag(
            skjæringstidspunkt = skjæringstidspunkt,
            beregningsgrunnlag = beregningsgrunnlag,
            sykepengegrunnlag = sykepengegrunnlag,
            inntekter = inntekter.map { it.toDTO() },
            arbeidsgiverrefusjoner = refusjonsopplysningerFraBehandlinger.map { it.toDTO() }
        )
    }

    override fun potensiellGhostperiode(organisasjonsnummer: String, sykefraværstilfeller: Map<LocalDate, List<ClosedRange<LocalDate>>>) = null
}

internal class IVilkårsgrunnlagHistorikk(private val tilgjengeligeVilkårsgrunnlag: List<Map<UUID, IVilkårsgrunnlag>>) {
    private val vilkårsgrunnlagIBruk = mutableMapOf<UUID, IVilkårsgrunnlag>()
    private val refusjonsopplysningerPerArbeidsgiver = mutableMapOf<Pair<UUID, String>, IArbeidsgiverrefusjon>()
    internal fun inngårIkkeISammenligningsgrunnlag(organisasjonsnummer: String) =
        vilkårsgrunnlagIBruk.all { (_, a) -> a.inngårIkkeISammenligningsgrunnlag(organisasjonsnummer) }

    internal fun nyeInntekterUnderveis(orgnummer: String) =
        tilgjengeligeVilkårsgrunnlag.firstOrNull()?.flatMap { (_, vilkårsgrunnlag) ->
            vilkårsgrunnlag.nyeInntekterUnderveis
                .filter { it.arbeidsgiver == orgnummer }
                .map {
                    NyttInntektsforholdPeriodeDTO(
                        id = UUID.randomUUID(),
                        fom = it.fom,
                        tom = it.tom,
                        dagligBeløp = it.dagligbeløp,
                        månedligBeløp = it.månedligBeløp,
                        skjæringstidspunkt = vilkårsgrunnlag.skjæringstidspunkt
                    )
                }
        } ?: emptyList()

    internal fun arbeidsgivere() = vilkårsgrunnlagIBruk
        .flatMap { (_, grunnlag) ->
            val arbeidsgivereFraInntektsgrunnlag = grunnlag.inntekter.map { it.arbeidsgiver }
            val tilkommetArbeidsgivere = grunnlag.nyeInntekterUnderveis.map { it.arbeidsgiver }
            (arbeidsgivereFraInntektsgrunnlag + tilkommetArbeidsgivere)
        }
        .toSet()

    internal fun potensielleGhostsperioder(
        organisasjonsnummer: String,
        sykefraværstilfeller: Map<LocalDate, List<ClosedRange<LocalDate>>>
    ) =
        tilgjengeligeVilkårsgrunnlag.firstOrNull()?.mapNotNull { (_, vilkårsgrunnlag) ->
            vilkårsgrunnlag.potensiellGhostperiode(organisasjonsnummer, sykefraværstilfeller)
        } ?: emptyList()

    internal fun toDTO(): Map<UUID, Vilkårsgrunnlag> {
        return vilkårsgrunnlagIBruk.mapValues { (_, vilkårsgrunnlag) ->
            vilkårsgrunnlag.toDTO(refusjonsopplysningerPerArbeidsgiver.filterKeys { (vilkårsgrunnlagId, _) -> vilkårsgrunnlagId == vilkårsgrunnlag.id }.values.toList())
        }
    }

    internal fun leggIBøtta(vilkårsgrunnlagId: UUID): IVilkårsgrunnlag {
        return vilkårsgrunnlagIBruk.getOrPut(vilkårsgrunnlagId) {
            tilgjengeligeVilkårsgrunnlag.firstNotNullOf { elementer ->
                elementer[vilkårsgrunnlagId]
            }
        }
    }

    internal fun leggRefusjonsopplysningerIBøtta(vilkårsgrunnlagId: UUID, refusjonsopplysninger: IArbeidsgiverrefusjon) {
        refusjonsopplysningerPerArbeidsgiver[vilkårsgrunnlagId to refusjonsopplysninger.arbeidsgiver] = refusjonsopplysninger
    }
}

internal class VilkårsgrunnlagBuilder(vilkårsgrunnlagHistorikk: VilkårsgrunnlaghistorikkUtDto) {
    private val inntekter = mutableMapOf<UUID, IOmregnetÅrsinntekt>()
    private val historikk = LinkedList<Map<UUID, IVilkårsgrunnlag>>()

    init {
        vilkårsgrunnlagHistorikk.historikk.asReversed().forEach {
            historikk.addFirst(it.vilkårsgrunnlag.associate {
                it.vilkårsgrunnlagId to when (it) {
                    is VilkårsgrunnlagUtDto.Infotrygd -> mapInfotrygd(it)
                    is VilkårsgrunnlagUtDto.Spleis -> mapSpleis(it)
                }
            })
        }
    }

    internal fun build() = IVilkårsgrunnlagHistorikk(historikk)
    private fun mapSpleis(grunnlagsdata: VilkårsgrunnlagUtDto.Spleis): IVilkårsgrunnlag {
        val oppfyllerKravOmMedlemskap = when (grunnlagsdata.medlemskapstatus) {
            MedlemskapsvurderingDto.Ja -> true
            MedlemskapsvurderingDto.Nei -> false
            MedlemskapsvurderingDto.UavklartMedBrukerspørsmål -> null
            MedlemskapsvurderingDto.VetIkke -> null
        }

        val begrensning = SykepengegrunnlagsgrenseDTO.fra6GBegrensning(grunnlagsdata.inntektsgrunnlag.`6G`)
        val overstyringer = grunnlagsdata.inntektsgrunnlag.arbeidsgiverInntektsopplysninger.flatMap {
            listOfNotNull(when (it.inntektsopplysning) {
                is InntektsopplysningUtDto.InfotrygdDto -> null
                is InntektsopplysningUtDto.ArbeidsgiverinntektDto -> null
                is InntektsopplysningUtDto.SkattSykepengegrunnlagDto -> null
            }, it.korrigertInntekt?.inntektsdata?.hendelseId, it.skjønnsmessigFastsatt?.inntektsdata?.hendelseId)
        }.toSet()

        return ISpleisGrunnlag(
            skjæringstidspunkt = grunnlagsdata.skjæringstidspunkt,
            overstyringer = overstyringer,
            beregningsgrunnlag = grunnlagsdata.inntektsgrunnlag.beregningsgrunnlag.årlig.beløp,
            omregnetÅrsinntekt = grunnlagsdata.inntektsgrunnlag.totalOmregnetÅrsinntekt.årlig.beløp,
            inntekter = inntekter(grunnlagsdata.inntektsgrunnlag),
            nyeInntekterUnderveis = grunnlagsdata.inntektsgrunnlag.tilkommendeInntekter.flatMap { nyInntekt ->
                nyInntekt.beløpstidslinje.perioder.map { nyInntektperiode ->
                    INyInntektUnderveis(
                        arbeidsgiver = nyInntekt.orgnummer,
                        fom = nyInntektperiode.fom,
                        tom = nyInntektperiode.tom,
                        dagligbeløp = nyInntektperiode.dagligBeløp,
                        månedligBeløp = nyInntektperiode.dagligBeløp.daglig.månedlig,
                    )
                }
            },
            sykepengegrunnlag = grunnlagsdata.inntektsgrunnlag.sykepengegrunnlag.årlig.beløp,
            grunnbeløp = begrensning.grunnbeløp,
            sykepengegrunnlagsgrense = begrensning,
            meldingsreferanseId = grunnlagsdata.meldingsreferanseId,
            antallOpptjeningsdagerErMinst = grunnlagsdata.opptjening.opptjeningsdager,
            oppfyllerKravOmMinstelønn = grunnlagsdata.inntektsgrunnlag.oppfyllerMinsteinntektskrav,
            oppfyllerKravOmOpptjening = grunnlagsdata.opptjening.erOppfylt,
            oppfyllerKravOmMedlemskap = oppfyllerKravOmMedlemskap,
            id = grunnlagsdata.vilkårsgrunnlagId
        )
    }

    private fun inntekter(dto: InntektsgrunnlagUtDto): List<IArbeidsgiverinntekt> {
        return dto.arbeidsgiverInntektsopplysninger.map { mapInntekt(it) } + dto.deaktiverteArbeidsforhold.map { mapInntekt(it, true) }
    }

    private fun mapInntekt(dto: ArbeidsgiverInntektsopplysningUtDto, deaktivert: Boolean = false): IArbeidsgiverinntekt {
        return mapInntekt(dto.orgnummer, dto.gjelder.fom, dto.gjelder.tom, dto.inntektsopplysning, dto.korrigertInntekt, dto.skjønnsmessigFastsatt, deaktivert)
    }

    private fun mapInntekt(orgnummer: String, fom: LocalDate, tom: LocalDate, io: InntektsopplysningUtDto, korrigertInntekt: SaksbehandlerUtDto?, skjønnsmessigFastsattDto: SkjønnsmessigFastsattUtDto?, deaktivert: Boolean): IArbeidsgiverinntekt {
        val omregnetÅrsinntekt = omregnetÅrsinntekt(korrigertInntekt, io).also {
            inntekter[io.id] = it
        }
        return IArbeidsgiverinntekt(
            arbeidsgiver = orgnummer,
            fom = fom,
            tom = tom,
            omregnetÅrsinntekt = omregnetÅrsinntekt,
            skjønnsmessigFastsatt = skjønnsmessigFastsattDto?.let {
                SkjønnsmessigFastsattDTO(
                    årlig = it.inntektsdata.beløp.årlig.beløp,
                    månedlig = it.inntektsdata.beløp.månedligDouble.beløp
                )
            },
            deaktivert = deaktivert
        )
    }

    private fun omregnetÅrsinntekt(korrigertInntekt: SaksbehandlerUtDto?, io: InntektsopplysningUtDto): IOmregnetÅrsinntekt {
        if (korrigertInntekt != null) return IOmregnetÅrsinntekt(
            kilde = IInntektkilde.Saksbehandler,
            beløp = korrigertInntekt.inntektsdata.beløp.årlig.beløp,
            månedsbeløp = korrigertInntekt.inntektsdata.beløp.månedligDouble.beløp
        )

        return when (io) {
            is InntektsopplysningUtDto.InfotrygdDto -> IOmregnetÅrsinntekt(
                kilde = IInntektkilde.Infotrygd,
                beløp = io.inntektsdata.beløp.årlig.beløp,
                månedsbeløp = io.inntektsdata.beløp.månedligDouble.beløp
            )
            is InntektsopplysningUtDto.ArbeidsgiverinntektDto -> {
                IOmregnetÅrsinntekt(
                    kilde = if (io.kilde == InntektsopplysningUtDto.ArbeidsgiverinntektDto.KildeDto.AOrdningen) IInntektkilde.AOrdningen else IInntektkilde.Inntektsmelding,
                    beløp = io.inntektsdata.beløp.årlig.beløp,
                    månedsbeløp = io.inntektsdata.beløp.månedligDouble.beløp
                )
            }

            is InntektsopplysningUtDto.SkattSykepengegrunnlagDto -> IOmregnetÅrsinntekt(
                kilde = if (io.inntektsdata.beløp.årlig.beløp == 0.0) IInntektkilde.IkkeRapportert else IInntektkilde.AOrdningen,
                beløp = io.inntektsdata.beløp.årlig.beløp,
                månedsbeløp = io.inntektsdata.beløp.månedligDouble.beløp
            )
        }
    }

    private fun mapInfotrygd(infotrygdVilkårsgrunnlag: VilkårsgrunnlagUtDto.Infotrygd): IVilkårsgrunnlag {
        return IInfotrygdGrunnlag(
            skjæringstidspunkt = infotrygdVilkårsgrunnlag.skjæringstidspunkt,
            beregningsgrunnlag = infotrygdVilkårsgrunnlag.inntektsgrunnlag.beregningsgrunnlag.årlig.beløp,
            inntekter = inntekter(infotrygdVilkårsgrunnlag.inntektsgrunnlag),
            sykepengegrunnlag = infotrygdVilkårsgrunnlag.inntektsgrunnlag.sykepengegrunnlag.årlig.beløp,
            id = infotrygdVilkårsgrunnlag.vilkårsgrunnlagId
        )
    }
}
