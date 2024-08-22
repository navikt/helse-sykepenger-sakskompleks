package no.nav.helse.serde

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.dto.AlderDto
import no.nav.helse.dto.ArbeidsforholdDto
import no.nav.helse.dto.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagDto
import no.nav.helse.dto.ArbeidsgiverOpptjeningsgrunnlagDto
import no.nav.helse.dto.AvsenderDto
import no.nav.helse.dto.BegrunnelseDto
import no.nav.helse.dto.BehandlingkildeDto
import no.nav.helse.dto.BehandlingtilstandDto
import no.nav.helse.dto.DokumentsporingDto
import no.nav.helse.dto.DokumenttypeDto
import no.nav.helse.dto.EndringIRefusjonDto
import no.nav.helse.dto.EndringskodeDto
import no.nav.helse.dto.FagområdeDto
import no.nav.helse.dto.FeriepengeberegnerDto
import no.nav.helse.dto.HendelseskildeDto
import no.nav.helse.dto.InfotrygdFerieperiodeDto
import no.nav.helse.dto.InntektbeløpDto
import no.nav.helse.dto.InntekttypeDto
import no.nav.helse.dto.KlassekodeDto
import no.nav.helse.dto.OppdragstatusDto
import no.nav.helse.dto.PeriodeDto
import no.nav.helse.dto.ProsentdelDto
import no.nav.helse.dto.SatstypeDto
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.dto.SkatteopplysningDto
import no.nav.helse.dto.SubsumsjonDto
import no.nav.helse.dto.SykdomshistorikkDto
import no.nav.helse.dto.SykdomshistorikkElementDto
import no.nav.helse.dto.SykdomstidslinjeDagDto
import no.nav.helse.dto.SykdomstidslinjeDto
import no.nav.helse.dto.SykmeldingsperioderDto
import no.nav.helse.dto.UtbetalingTilstandDto
import no.nav.helse.dto.UtbetalingVurderingDto
import no.nav.helse.dto.UtbetalingtypeDto
import no.nav.helse.dto.UtbetaltDagDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.deserialisering.ArbeidsgiverInnDto
import no.nav.helse.dto.deserialisering.ArbeidsgiverInntektsopplysningInnDto
import no.nav.helse.dto.deserialisering.BehandlingInnDto
import no.nav.helse.dto.deserialisering.BehandlingendringInnDto
import no.nav.helse.dto.deserialisering.BehandlingerInnDto
import no.nav.helse.dto.deserialisering.FeriepengeInnDto
import no.nav.helse.dto.deserialisering.ForkastetVedtaksperiodeInnDto
import no.nav.helse.dto.deserialisering.InfotrygdArbeidsgiverutbetalingsperiodeInnDto
import no.nav.helse.dto.deserialisering.InfotrygdInntektsopplysningInnDto
import no.nav.helse.dto.deserialisering.InfotrygdPersonutbetalingsperiodeInnDto
import no.nav.helse.dto.deserialisering.InfotrygdhistorikkInnDto
import no.nav.helse.dto.deserialisering.InfotrygdhistorikkelementInnDto
import no.nav.helse.dto.deserialisering.InntektshistorikkInnDto
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.deserialisering.MinimumSykdomsgradVurderingInnDto
import no.nav.helse.dto.deserialisering.OppdragInnDto
import no.nav.helse.dto.deserialisering.OpptjeningInnDto
import no.nav.helse.dto.deserialisering.PersonInnDto
import no.nav.helse.dto.deserialisering.RefusjonInnDto
import no.nav.helse.dto.deserialisering.RefusjonshistorikkInnDto
import no.nav.helse.dto.deserialisering.RefusjonsopplysningInnDto
import no.nav.helse.dto.deserialisering.RefusjonsopplysningerInnDto
import no.nav.helse.dto.deserialisering.SammenligningsgrunnlagInnDto
import no.nav.helse.dto.deserialisering.SykepengegrunnlagInnDto
import no.nav.helse.dto.deserialisering.UtbetalingInnDto
import no.nav.helse.dto.deserialisering.UtbetalingsdagInnDto
import no.nav.helse.dto.deserialisering.UtbetalingslinjeInnDto
import no.nav.helse.dto.deserialisering.UtbetalingstidslinjeInnDto
import no.nav.helse.dto.deserialisering.VedtaksperiodeInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlagInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlagInnslagInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlaghistorikkInnDto
import no.nav.helse.dto.deserialisering.ØkonomiInnDto
import no.nav.helse.serde.mapping.JsonMedlemskapstatus
import kotlin.streams.asSequence

data class PersonData(
    val aktørId: String,
    val fødselsnummer: String,
    val fødselsdato: LocalDate,
    val arbeidsgivere: List<ArbeidsgiverData>,
    val opprettet: LocalDateTime,
    val infotrygdhistorikk: List<InfotrygdhistorikkElementData>,
    val vilkårsgrunnlagHistorikk: List<VilkårsgrunnlagInnslagData>,
    val minimumSykdomsgradVurdering: List<MinimumSykdomsgradVurderingPeriodeData>?,
    val dødsdato: LocalDate?,
    val skjemaVersjon: Int
) {
    fun tilPersonDto() = PersonInnDto(
        aktørId = this.aktørId,
        fødselsnummer = this.fødselsnummer,
        alder = AlderDto(fødselsdato = this.fødselsdato, dødsdato = this.dødsdato),
        opprettet = this.opprettet,
        arbeidsgivere = this.arbeidsgivere.map { it.tilDto() },
        infotrygdhistorikk = InfotrygdhistorikkInnDto(this.infotrygdhistorikk.map { it.tilDto() }),
        vilkårsgrunnlagHistorikk = VilkårsgrunnlaghistorikkInnDto(vilkårsgrunnlagHistorikk.map { it.tilDto() }),
        minimumSykdomsgradVurdering = MinimumSykdomsgradVurderingInnDto(
            perioder = minimumSykdomsgradVurdering?.map { PeriodeDto(it.fom, it.tom) } ?: emptyList()
        )
    )

    data class MinimumSykdomsgradVurderingPeriodeData(val fom: LocalDate, val tom: LocalDate)
    data class InfotrygdhistorikkElementData(
        val id: UUID,
        val tidsstempel: LocalDateTime,
        val hendelseId: UUID?,
        val ferieperioder: List<FerieperiodeData>,
        val arbeidsgiverutbetalingsperioder: List<ArbeidsgiverutbetalingsperiodeData>,
        val personutbetalingsperioder: List<PersonutbetalingsperiodeData>,
        val inntekter: List<InntektsopplysningData>,
        val arbeidskategorikoder: Map<String, LocalDate>,
        val oppdatert: LocalDateTime
    ) {
        fun tilDto() = InfotrygdhistorikkelementInnDto(
            id = this.id,
            tidsstempel = this.tidsstempel,
            hendelseId = this.hendelseId,
            ferieperioder = this.ferieperioder.map { it.tilDto() },
            arbeidsgiverutbetalingsperioder = this.arbeidsgiverutbetalingsperioder.map { it.tilDto() },
            personutbetalingsperioder = this.personutbetalingsperioder.map { it.tilDto() },
            inntekter = this.inntekter.map { it.tilDto() },
            arbeidskategorikoder = this.arbeidskategorikoder,
            oppdatert = this.oppdatert
        )

        data class FerieperiodeData(
            val fom: LocalDate,
            val tom: LocalDate
        ) {
            fun tilDto() = InfotrygdFerieperiodeDto(
                periode = PeriodeDto(
                    fom = this.fom,
                    tom = this.tom
                )
            )
        }

        data class PersonutbetalingsperiodeData(
            val orgnr: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Double,
            val inntekt: Int
        ) {
            fun tilDto() = InfotrygdPersonutbetalingsperiodeInnDto(
                orgnr = this.orgnr,
                periode = PeriodeDto(
                    fom = this.fom,
                    tom = this.tom
                ),
                grad = ProsentdelDto(prosent = this.grad),
                inntekt = InntektbeløpDto.DagligInt(this.inntekt)
            )
        }

        data class ArbeidsgiverutbetalingsperiodeData(
            val orgnr: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Double,
            val inntekt: Int
        ) {
            fun tilDto() = InfotrygdArbeidsgiverutbetalingsperiodeInnDto(
                orgnr = this.orgnr,
                periode = PeriodeDto(fom = this.fom, tom = this.tom),
                grad = ProsentdelDto(prosent = grad),
                inntekt = InntektbeløpDto.DagligInt(inntekt)
            )
        }

        data class InntektsopplysningData(
            val orgnr: String,
            val sykepengerFom: LocalDate,
            val inntekt: Double,
            val refusjonTilArbeidsgiver: Boolean,
            val refusjonTom: LocalDate?,
            val lagret: LocalDateTime?
        ) {
            fun tilDto() = InfotrygdInntektsopplysningInnDto(
                orgnummer = this.orgnr,
                sykepengerFom = this.sykepengerFom,
                inntekt = InntektbeløpDto.MånedligDouble(inntekt),
                refusjonTilArbeidsgiver = refusjonTilArbeidsgiver,
                refusjonTom = refusjonTom,
                lagret = lagret
            )
        }
    }

    data class VilkårsgrunnlagInnslagData(
        val id: UUID,
        val opprettet: LocalDateTime,
        val vilkårsgrunnlag: List<VilkårsgrunnlagElementData>
    ) {
        fun tilDto() = VilkårsgrunnlagInnslagInnDto(
            id = this.id,
            opprettet = this.opprettet,
            vilkårsgrunnlag = this.vilkårsgrunnlag.map { it.tilDto() }
        )
    }

    data class VilkårsgrunnlagElementData(
        val skjæringstidspunkt: LocalDate,
        val type: GrunnlagsdataType,
        val sykepengegrunnlag: SykepengegrunnlagData,
        val opptjening: OpptjeningData?,
        val medlemskapstatus: JsonMedlemskapstatus?,
        val vurdertOk: Boolean?,
        val meldingsreferanseId: UUID?,
        val vilkårsgrunnlagId: UUID
    ) {
        fun tilDto() = when (type) {
            GrunnlagsdataType.Infotrygd -> VilkårsgrunnlagInnDto.Infotrygd(
                vilkårsgrunnlagId = this.vilkårsgrunnlagId,
                skjæringstidspunkt = this.skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag.tilInfotrygdDto()
            )
            GrunnlagsdataType.Vilkårsprøving -> VilkårsgrunnlagInnDto.Spleis(
                vilkårsgrunnlagId = this.vilkårsgrunnlagId,
                skjæringstidspunkt = this.skjæringstidspunkt,
                sykepengegrunnlag = this.sykepengegrunnlag.tilSpleisDto(),
                opptjening = this.opptjening!!.tilDto(),
                medlemskapstatus = this.medlemskapstatus!!.tilDto(),
                vurdertOk = this.vurdertOk!!,
                meldingsreferanseId = this.meldingsreferanseId
            )
        }

        enum class GrunnlagsdataType {
            Infotrygd,
            Vilkårsprøving
        }

        data class SykepengegrunnlagData(
            val grunnbeløp: Double?,
            val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningData>,
            val sammenligningsgrunnlag: SammenligningsgrunnlagData?,
            val deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysningData>,
            val vurdertInfotrygd: Boolean
        ) {
            fun tilSpleisDto() = SykepengegrunnlagInnDto(
                arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilDto() },
                deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.tilDto() },
                vurdertInfotrygd = this.vurdertInfotrygd,
                sammenligningsgrunnlag = this.sammenligningsgrunnlag!!.tilDto(),
                `6G` = InntektbeløpDto.Årlig(grunnbeløp!!)
            )
            fun tilInfotrygdDto() = SykepengegrunnlagInnDto(
                arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilDto() },
                deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.tilDto() },
                vurdertInfotrygd = this.vurdertInfotrygd,
                sammenligningsgrunnlag = SammenligningsgrunnlagInnDto(InntektbeløpDto.Årlig(0.0), emptyList()),
                `6G` = InntektbeløpDto.Årlig(grunnbeløp!!)
            )
        }

        data class SammenligningsgrunnlagData(
            val sammenligningsgrunnlag: Double,
            val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData>,
        ) {
            fun tilDto() = SammenligningsgrunnlagInnDto(
                sammenligningsgrunnlag = InntektbeløpDto.Årlig(sammenligningsgrunnlag),
                arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilDto() }
            )
        }

        data class ArbeidsgiverInntektsopplysningData(
            val orgnummer: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val inntektsopplysning: InntektsopplysningData,
            val refusjonsopplysninger: List<ArbeidsgiverData.RefusjonsopplysningData>
        ) {
            fun tilDto() = ArbeidsgiverInntektsopplysningInnDto(
                orgnummer = this.orgnummer,
                gjelder = PeriodeDto(fom = this.fom, tom = this.tom),
                inntektsopplysning = this.inntektsopplysning.tilDto(),
                refusjonsopplysninger = RefusjonsopplysningerInnDto(opplysninger = this.refusjonsopplysninger.map { it.tilDto() })
            )

            data class SkatteopplysningData(
                val hendelseId: UUID,
                val beløp: Double,
                val måned: YearMonth,
                val type: InntekttypeData,
                val fordel: String,
                val beskrivelse: String,
                val tidsstempel: LocalDateTime
            ) {
                enum class InntekttypeData {
                    LØNNSINNTEKT,
                    NÆRINGSINNTEKT,
                    PENSJON_ELLER_TRYGD,
                    YTELSE_FRA_OFFENTLIGE
                }
                fun tilDto() = SkatteopplysningDto(
                    hendelseId = this.hendelseId,
                    beløp = InntektbeløpDto.MånedligDouble(beløp = beløp),
                    måned = this.måned,
                    type = when (type) {
                        InntekttypeData.LØNNSINNTEKT -> InntekttypeDto.LØNNSINNTEKT
                        InntekttypeData.NÆRINGSINNTEKT -> InntekttypeDto.NÆRINGSINNTEKT
                        InntekttypeData.PENSJON_ELLER_TRYGD -> InntekttypeDto.PENSJON_ELLER_TRYGD
                        InntekttypeData.YTELSE_FRA_OFFENTLIGE -> InntekttypeDto.YTELSE_FRA_OFFENTLIGE
                    },
                    fordel = fordel,
                    beskrivelse = beskrivelse,
                    tidsstempel = tidsstempel
                )
            }

            data class InntektsopplysningData(
                val id: UUID,
                val dato: LocalDate,
                val hendelseId: UUID,
                val beløp: Double?,
                val kilde: String,
                val forklaring: String?,
                val subsumsjon: SubsumsjonData?,
                val tidsstempel: LocalDateTime,
                val overstyrtInntektId: UUID?,
                val skatteopplysninger: List<SkatteopplysningData>?
            ) {
                fun tilDto() = when (kilde.let(Inntektsopplysningskilde::valueOf)) {
                    Inntektsopplysningskilde.INFOTRYGD -> InntektsopplysningInnDto.InfotrygdDto(
                        id = this.id,
                        hendelseId = this.hendelseId,
                        dato = this.dato,
                        beløp = InntektbeløpDto.MånedligDouble(beløp = beløp!!),
                        tidsstempel = this.tidsstempel
                    )
                    Inntektsopplysningskilde.INNTEKTSMELDING -> InntektsopplysningInnDto.InntektsmeldingDto(
                        id = this.id,
                        hendelseId = this.hendelseId,
                        dato = this.dato,
                        beløp = InntektbeløpDto.MånedligDouble(beløp = beløp!!),
                        tidsstempel = this.tidsstempel
                    )
                    Inntektsopplysningskilde.IKKE_RAPPORTERT -> InntektsopplysningInnDto.IkkeRapportertDto(
                        id = this.id,
                        hendelseId = this.hendelseId,
                        dato = this.dato,
                        tidsstempel = this.tidsstempel
                    )
                    Inntektsopplysningskilde.SAKSBEHANDLER -> InntektsopplysningInnDto.SaksbehandlerDto(
                        id = this.id,
                        hendelseId = this.hendelseId,
                        dato = this.dato,
                        beløp = InntektbeløpDto.MånedligDouble(beløp = beløp!!),
                        tidsstempel = this.tidsstempel,
                        overstyrtInntekt = this.overstyrtInntektId!!,
                        forklaring = this.forklaring,
                        subsumsjon = this.subsumsjon?.tilDto()
                    )
                    Inntektsopplysningskilde.SKJØNNSMESSIG_FASTSATT -> InntektsopplysningInnDto.SkjønnsmessigFastsattDto(
                        id = this.id,
                        hendelseId = this.hendelseId,
                        dato = this.dato,
                        beløp = InntektbeløpDto.MånedligDouble(beløp = beløp!!),
                        tidsstempel = this.tidsstempel,
                        overstyrtInntekt = this.overstyrtInntektId!!
                    )
                    Inntektsopplysningskilde.SKATT_SYKEPENGEGRUNNLAG -> InntektsopplysningInnDto.SkattSykepengegrunnlagDto(
                        id = this.id,
                        hendelseId = this.hendelseId,
                        dato = this.dato,
                        tidsstempel = this.tidsstempel,
                        inntektsopplysninger = this.skatteopplysninger!!.map { it.tilDto() },
                        ansattPerioder = emptyList()
                    )
                    else -> error("Fant ${kilde}. Det er ugyldig for sykepengegrunnlag")
                }

                data class SubsumsjonData(
                    val paragraf: String,
                    val ledd: Int?,
                    val bokstav: String?
                ) {
                    fun tilDto() = SubsumsjonDto(paragraf, ledd, bokstav)
                }
            }
        }

        data class ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData(
            val orgnummer: String,
            val skatteopplysninger: List<SammenligningsgrunnlagInntektsopplysningData>
        ) {
            fun tilDto() = ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagDto(
                orgnummer = this.orgnummer,
                inntektsopplysninger = this.skatteopplysninger.map { it.tilDto() }
            )
            data class SammenligningsgrunnlagInntektsopplysningData(
                val hendelseId: UUID,
                val beløp: Double,
                val måned: YearMonth,
                val type: InntekttypeData,
                val fordel: String,
                val beskrivelse: String,
                val tidsstempel: LocalDateTime,
            ) {
                fun tilDto() = SkatteopplysningDto(
                    hendelseId = this.hendelseId,
                    beløp = InntektbeløpDto.MånedligDouble(beløp = beløp),
                    måned = this.måned,
                    type = when (type) {
                        InntekttypeData.LØNNSINNTEKT -> InntekttypeDto.LØNNSINNTEKT
                        InntekttypeData.NÆRINGSINNTEKT -> InntekttypeDto.NÆRINGSINNTEKT
                        InntekttypeData.PENSJON_ELLER_TRYGD -> InntekttypeDto.PENSJON_ELLER_TRYGD
                        InntekttypeData.YTELSE_FRA_OFFENTLIGE -> InntekttypeDto.YTELSE_FRA_OFFENTLIGE
                    },
                    fordel = fordel,
                    beskrivelse = beskrivelse,
                    tidsstempel = tidsstempel
                )
                enum class InntekttypeData {
                    LØNNSINNTEKT,
                    NÆRINGSINNTEKT,
                    PENSJON_ELLER_TRYGD,
                    YTELSE_FRA_OFFENTLIGE
                }
            }
        }

        data class OpptjeningData(
            val opptjeningFom: LocalDate,
            val opptjeningTom: LocalDate,
            val arbeidsforhold: List<ArbeidsgiverOpptjeningsgrunnlagData>
        ) {
            fun tilDto() = OpptjeningInnDto(
                arbeidsforhold = this.arbeidsforhold.map { it.tilDto() },
                opptjeningsperiode = PeriodeDto(fom = this.opptjeningFom, tom = this.opptjeningTom)
            )
            data class ArbeidsgiverOpptjeningsgrunnlagData(
                val orgnummer: String,
                val ansattPerioder: List<ArbeidsforholdData>
            ) {
                fun tilDto() = ArbeidsgiverOpptjeningsgrunnlagDto(
                    orgnummer = this.orgnummer,
                    ansattPerioder = this.ansattPerioder.map { it.tilDto() }
                )
                data class ArbeidsforholdData(
                    val ansattFom: LocalDate,
                    val ansattTom: LocalDate?,
                    val deaktivert: Boolean
                ) {
                    fun tilDto() = ArbeidsforholdDto(
                        ansattFom = ansattFom, ansattTom = ansattTom, deaktivert = deaktivert
                    )
                }
            }
        }
    }

    data class ArbeidsgiverData(
        val organisasjonsnummer: String,
        val id: UUID,
        val inntektshistorikk: List<InntektsmeldingData>,
        val sykdomshistorikk: List<SykdomshistorikkData>,
        val sykmeldingsperioder: List<SykmeldingsperiodeData>,
        val vedtaksperioder: List<VedtaksperiodeData>,
        val forkastede: List<ForkastetVedtaksperiodeData>,
        val utbetalinger: List<UtbetalingData>,
        val feriepengeutbetalinger: List<FeriepengeutbetalingData>,
        val refusjonshistorikk: List<RefusjonData>
    ) {
        fun tilDto() = ArbeidsgiverInnDto(
            id = this.id,
            organisasjonsnummer = this.organisasjonsnummer,
            inntektshistorikk = InntektshistorikkInnDto(this.inntektshistorikk.map { it.tilDto() }),
            sykdomshistorikk = SykdomshistorikkDto(this.sykdomshistorikk.map { it.tilDto() }),
            sykmeldingsperioder = SykmeldingsperioderDto(this.sykmeldingsperioder.map { it.tilDto() }),
            vedtaksperioder = this.vedtaksperioder.map { it.tilDto() },
            forkastede = this.forkastede.map { it.tilDto() },
            utbetalinger = this.utbetalinger.map { it.tilDto() },
            feriepengeutbetalinger = this.feriepengeutbetalinger.map { it.tilDto() },
            refusjonshistorikk = RefusjonshistorikkInnDto(this.refusjonshistorikk.map { it.tilDto() })
        )

        data class InntektsmeldingData(
            val id: UUID,
            val dato: LocalDate,
            val hendelseId: UUID,
            val beløp: Double,
            val tidsstempel: LocalDateTime
        ) {
            fun tilDto() = InntektsopplysningInnDto.InntektsmeldingDto(
                id = this.id,
                hendelseId = this.hendelseId,
                dato = this.dato,
                beløp = InntektbeløpDto.MånedligDouble(beløp = this.beløp),
                tidsstempel = this.tidsstempel
            )
        }

        data class RefusjonsopplysningData(
            val meldingsreferanseId: UUID,
            val fom: LocalDate,
            val tom: LocalDate?,
            val beløp: Double
        ) {
            fun tilDto() = RefusjonsopplysningInnDto(
                meldingsreferanseId = this.meldingsreferanseId,
                fom = this.fom,
                tom = this.tom,
                beløp = InntektbeløpDto.MånedligDouble(beløp)
            )
        }

        data class PeriodeData(val fom: LocalDate, val tom: LocalDate) {
            fun tilDto() = PeriodeDto(fom = this.fom, tom = this.tom)
        }
        data class SykdomstidslinjeData(
            val dager: List<DagData>,
            val periode: PeriodeData?,
            val låstePerioder: List<PeriodeData>?
        ) {
            fun tilDto() = SykdomstidslinjeDto(
                dager = this.dager.flatMap { it.tilDto() },
                periode = this.periode?.tilDto(),
                låstePerioder = this.låstePerioder?.map { it.tilDto() } ?: emptyList()
            )

            data class DagData(
                val type: JsonDagType,
                val kilde: KildeData,
                val grad: Double,
                val other: KildeData?,
                val melding: String?,
                val fom: LocalDate?,
                val tom: LocalDate?,
                val dato: LocalDate?
            ) {
                init {
                    check (dato != null || (fom != null && tom != null)) {
                        "enten må dato være satt eller så må både fom og tom være satt"
                    }
                }

                private val datoer = datosekvens(dato, fom, tom)

                fun tilDto(): Sequence<SykdomstidslinjeDagDto> {
                    val kilde = this.kilde.tilDto()
                    return datoer.map { tilDto(it, kilde) }
                }
                private fun tilDto(dagen: LocalDate, kilde: HendelseskildeDto) = when (type) {
                    JsonDagType.ARBEIDSDAG -> SykdomstidslinjeDagDto.ArbeidsdagDto(dato = dagen, kilde = kilde)
                    JsonDagType.ARBEIDSGIVERDAG -> if (dagen.erHelg())
                        SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto(dato = dagen, kilde = kilde, grad = ProsentdelDto(grad))
                    else
                        SykdomstidslinjeDagDto.ArbeidsgiverdagDto(dato = dagen, kilde = kilde, grad = ProsentdelDto(grad))
                    JsonDagType.FERIEDAG -> SykdomstidslinjeDagDto.FeriedagDto(dato = dagen, kilde = kilde)
                    JsonDagType.ARBEID_IKKE_GJENOPPTATT_DAG -> SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto(dato = dagen, kilde = kilde)
                    JsonDagType.FRISK_HELGEDAG -> SykdomstidslinjeDagDto.FriskHelgedagDto(dato = dagen, kilde = kilde)
                    JsonDagType.FORELDET_SYKEDAG -> SykdomstidslinjeDagDto.ForeldetSykedagDto(dato = dagen, kilde = kilde, grad = ProsentdelDto(grad))
                    JsonDagType.PERMISJONSDAG -> SykdomstidslinjeDagDto.PermisjonsdagDto(dato = dagen, kilde = kilde)
                    JsonDagType.PROBLEMDAG -> SykdomstidslinjeDagDto.ProblemDagDto(dato = dagen, kilde = kilde, other = this.other!!.tilDto(), melding = this.melding!!)
                    JsonDagType.SYKEDAG -> if (dagen.erHelg())
                        SykdomstidslinjeDagDto.SykHelgedagDto(dato = dagen, kilde = kilde, grad = ProsentdelDto(grad))
                    else
                        SykdomstidslinjeDagDto.SykedagDto(dato = dagen, kilde = kilde, grad = ProsentdelDto(grad))
                    JsonDagType.SYKEDAG_NAV -> SykdomstidslinjeDagDto.SykedagNavDto(dato = dagen, kilde = kilde, grad = ProsentdelDto(grad))
                    JsonDagType.ANDRE_YTELSER_FORELDREPENGER -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Foreldrepenger)
                    JsonDagType.ANDRE_YTELSER_AAP -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, ytelse = SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.AAP)
                    JsonDagType.ANDRE_YTELSER_OMSORGSPENGER -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, ytelse = SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Omsorgspenger)
                    JsonDagType.ANDRE_YTELSER_PLEIEPENGER -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, ytelse = SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Pleiepenger)
                    JsonDagType.ANDRE_YTELSER_SVANGERSKAPSPENGER -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, ytelse = SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Svangerskapspenger)
                    JsonDagType.ANDRE_YTELSER_OPPLÆRINGSPENGER -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, ytelse = SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Opplæringspenger)
                    JsonDagType.ANDRE_YTELSER_DAGPENGER -> SykdomstidslinjeDagDto.AndreYtelserDto(dato = dagen, kilde = kilde, ytelse = SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Dagpenger)
                    JsonDagType.UKJENT_DAG -> SykdomstidslinjeDagDto.UkjentDagDto(dato = dagen, kilde = kilde)
                }
            }

            enum class JsonDagType {
                ARBEIDSDAG,
                ARBEIDSGIVERDAG,

                FERIEDAG,
                ARBEID_IKKE_GJENOPPTATT_DAG,
                FRISK_HELGEDAG,
                FORELDET_SYKEDAG,
                PERMISJONSDAG,
                PROBLEMDAG,
                SYKEDAG,
                SYKEDAG_NAV,
                ANDRE_YTELSER_FORELDREPENGER,
                ANDRE_YTELSER_AAP,
                ANDRE_YTELSER_OMSORGSPENGER,
                ANDRE_YTELSER_PLEIEPENGER,
                ANDRE_YTELSER_SVANGERSKAPSPENGER,
                ANDRE_YTELSER_OPPLÆRINGSPENGER,
                ANDRE_YTELSER_DAGPENGER,

                UKJENT_DAG
            }

            data class KildeData(
                val type: String,
                val id: UUID,
                val tidsstempel: LocalDateTime
            ) {
                fun tilDto() = HendelseskildeDto(
                    type = this.type,
                    meldingsreferanseId = this.id,
                    tidsstempel = this.tidsstempel
                )
            }
        }

        data class ForkastetVedtaksperiodeData(
            val vedtaksperiode: VedtaksperiodeData
        ) {
            fun tilDto() = ForkastetVedtaksperiodeInnDto(vedtaksperiode = this.vedtaksperiode.tilDto())
        }

        data class FeriepengeutbetalingData(
            val infotrygdFeriepengebeløpPerson: Double,
            val infotrygdFeriepengebeløpArbeidsgiver: Double,
            val spleisFeriepengebeløpArbeidsgiver: Double,
            val spleisFeriepengebeløpPerson: Double,
            val oppdrag: OppdragData,
            val personoppdrag: OppdragData,
            val opptjeningsår: Year,
            val utbetalteDager: List<UtbetaltDagData>,
            val feriepengedager: List<UtbetaltDagData>,
            val utbetalingId: UUID,
            val sendTilOppdrag: Boolean,
            val sendPersonoppdragTilOS: Boolean,
        ) {
            fun tilDto() = FeriepengeInnDto(
                feriepengeberegner = FeriepengeberegnerDto(
                    opptjeningsår = this.opptjeningsår,
                    utbetalteDager = this.utbetalteDager.map { it.tilDto() },
                    feriepengedager = this.feriepengedager.map { it.tilDto() }
                ),
                infotrygdFeriepengebeløpPerson = this.infotrygdFeriepengebeløpPerson,
                infotrygdFeriepengebeløpArbeidsgiver = this.infotrygdFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpPerson = this.spleisFeriepengebeløpPerson,
                spleisFeriepengebeløpArbeidsgiver = this.spleisFeriepengebeløpArbeidsgiver,
                oppdrag = this.oppdrag.tilDto(),
                personoppdrag = this.personoppdrag.tilDto(),
                utbetalingId = this.utbetalingId,
                sendTilOppdrag = this.sendTilOppdrag,
                sendPersonoppdragTilOS = this.sendPersonoppdragTilOS
            )

            data class UtbetaltDagData(
                val type: String,
                val orgnummer: String,
                val dato: LocalDate,
                val beløp: Int,
            ) {
                fun tilDto(): UtbetaltDagDto = when (type) {
                    "InfotrygdPersonDag" -> UtbetaltDagDto.InfotrygdPerson(
                        orgnummer = orgnummer,
                        dato = dato,
                        beløp = beløp
                    )
                    "InfotrygdArbeidsgiverDag" -> UtbetaltDagDto.InfotrygdArbeidsgiver(
                        orgnummer = orgnummer,
                        dato = dato,
                        beløp = beløp
                    )
                    "SpleisArbeidsgiverDag" -> UtbetaltDagDto.SpleisArbeidsgiver(
                        orgnummer = orgnummer,
                        dato = dato,
                        beløp = beløp
                    )
                    "SpleisPersonDag" -> UtbetaltDagDto.SpleisPerson(
                        orgnummer = orgnummer,
                        dato = dato,
                        beløp = beløp
                    )
                    else -> error("Ukjent utbetaltdag-type: $type")
                }
            }
        }

        data class SykmeldingsperiodeData(
            val fom: LocalDate,
            val tom: LocalDate
        ) {
            fun tilDto() = PeriodeDto(fom = fom, tom = tom)
        }

        data class VedtaksperiodeData(
            val id: UUID,
            val tilstand: TilstandTypeData,
            val skjæringstidspunkt: LocalDate,
            val behandlinger: List<BehandlingData>,
            val opprettet: LocalDateTime,
            val oppdatert: LocalDateTime,
            val egenmeldingsperioder: List<PeriodeData>
        ) {
            enum class TilstandTypeData {
                AVVENTER_HISTORIKK,
                AVVENTER_GODKJENNING,
                AVVENTER_SIMULERING,
                TIL_UTBETALING,
                TIL_INFOTRYGD,
                AVSLUTTET,
                AVSLUTTET_UTEN_UTBETALING,
                REVURDERING_FEILET,
                START,
                AVVENTER_INFOTRYGDHISTORIKK,
                AVVENTER_INNTEKTSMELDING,
                AVVENTER_BLOKKERENDE_PERIODE,
                AVVENTER_VILKÅRSPRØVING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_VILKÅRSPRØVING_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING
            }

            fun tilDto() = VedtaksperiodeInnDto(
                id = this.id,
                tilstand = when (tilstand) {
                    TilstandTypeData.AVVENTER_HISTORIKK -> VedtaksperiodetilstandDto.AVVENTER_HISTORIKK
                    TilstandTypeData.AVVENTER_GODKJENNING -> VedtaksperiodetilstandDto.AVVENTER_GODKJENNING
                    TilstandTypeData.AVVENTER_SIMULERING -> VedtaksperiodetilstandDto.AVVENTER_SIMULERING
                    TilstandTypeData.TIL_UTBETALING -> VedtaksperiodetilstandDto.TIL_UTBETALING
                    TilstandTypeData.TIL_INFOTRYGD -> VedtaksperiodetilstandDto.TIL_INFOTRYGD
                    TilstandTypeData.AVSLUTTET -> VedtaksperiodetilstandDto.AVSLUTTET
                    TilstandTypeData.AVSLUTTET_UTEN_UTBETALING -> VedtaksperiodetilstandDto.AVSLUTTET_UTEN_UTBETALING
                    TilstandTypeData.REVURDERING_FEILET -> VedtaksperiodetilstandDto.REVURDERING_FEILET
                    TilstandTypeData.START -> VedtaksperiodetilstandDto.START
                    TilstandTypeData.AVVENTER_INFOTRYGDHISTORIKK -> VedtaksperiodetilstandDto.AVVENTER_INFOTRYGDHISTORIKK
                    TilstandTypeData.AVVENTER_INNTEKTSMELDING -> VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING
                    TilstandTypeData.AVVENTER_BLOKKERENDE_PERIODE -> VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE
                    TilstandTypeData.AVVENTER_VILKÅRSPRØVING -> VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING
                    TilstandTypeData.AVVENTER_REVURDERING -> VedtaksperiodetilstandDto.AVVENTER_REVURDERING
                    TilstandTypeData.AVVENTER_HISTORIKK_REVURDERING -> VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING
                    TilstandTypeData.AVVENTER_VILKÅRSPRØVING_REVURDERING -> VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING_REVURDERING
                    TilstandTypeData.AVVENTER_SIMULERING_REVURDERING -> VedtaksperiodetilstandDto.AVVENTER_SIMULERING_REVURDERING
                    TilstandTypeData.AVVENTER_GODKJENNING_REVURDERING -> VedtaksperiodetilstandDto.AVVENTER_GODKJENNING_REVURDERING
                },
                behandlinger = BehandlingerInnDto(this.behandlinger.map { it.tilDto() }),
                egenmeldingsperioder = egenmeldingsperioder.map { it.tilDto() },
                opprettet = opprettet,
                oppdatert = oppdatert
            )
            data class DokumentsporingData(
                val dokumentId: UUID,
                val dokumenttype: DokumentTypeData
            ) {
                fun tilDto() = DokumentsporingDto(
                    id = this.dokumentId,
                    type = when (dokumenttype) {
                        DokumentTypeData.Sykmelding -> DokumenttypeDto.Sykmelding
                        DokumentTypeData.Søknad -> DokumenttypeDto.Søknad
                        DokumentTypeData.InntektsmeldingInntekt -> DokumenttypeDto.InntektsmeldingInntekt
                        DokumentTypeData.InntektsmeldingDager -> DokumenttypeDto.InntektsmeldingDager
                        DokumentTypeData.OverstyrTidslinje -> DokumenttypeDto.OverstyrTidslinje
                        DokumentTypeData.OverstyrInntekt -> DokumenttypeDto.OverstyrInntekt
                        DokumentTypeData.OverstyrRefusjon -> DokumenttypeDto.OverstyrRefusjon
                        DokumentTypeData.OverstyrArbeidsgiveropplysninger -> DokumenttypeDto.OverstyrArbeidsgiveropplysninger
                        DokumentTypeData.OverstyrArbeidsforhold -> DokumenttypeDto.OverstyrArbeidsforhold
                        DokumentTypeData.SkjønnsmessigFastsettelse -> DokumenttypeDto.SkjønnsmessigFastsettelse
                        DokumentTypeData.AndreYtelser -> DokumenttypeDto.AndreYtelser
                    }
                )
            }
            enum class DokumentTypeData {
                Sykmelding,
                Søknad,
                InntektsmeldingInntekt,
                InntektsmeldingDager,
                OverstyrTidslinje,
                OverstyrInntekt,
                OverstyrRefusjon,
                OverstyrArbeidsgiveropplysninger,
                OverstyrArbeidsforhold,
                SkjønnsmessigFastsettelse,
                AndreYtelser
            }

            data class BehandlingData(
                val id: UUID,
                val tilstand: TilstandData,
                val vedtakFattet: LocalDateTime?,
                val avsluttet: LocalDateTime?,
                val kilde: KildeData,
                val endringer: List<EndringData>
            ) {
                fun tilDto() = BehandlingInnDto(
                    id = this.id,
                    tilstand = when (tilstand) {
                        TilstandData.UBEREGNET -> BehandlingtilstandDto.UBEREGNET
                        TilstandData.UBEREGNET_OMGJØRING -> BehandlingtilstandDto.UBEREGNET_OMGJØRING
                        TilstandData.UBEREGNET_REVURDERING -> BehandlingtilstandDto.UBEREGNET_REVURDERING
                        TilstandData.BEREGNET -> BehandlingtilstandDto.BEREGNET
                        TilstandData.BEREGNET_OMGJØRING -> BehandlingtilstandDto.BEREGNET_OMGJØRING
                        TilstandData.BEREGNET_REVURDERING -> BehandlingtilstandDto.BEREGNET_REVURDERING
                        TilstandData.VEDTAK_FATTET -> BehandlingtilstandDto.VEDTAK_FATTET
                        TilstandData.REVURDERT_VEDTAK_AVVIST -> BehandlingtilstandDto.REVURDERT_VEDTAK_AVVIST
                        TilstandData.VEDTAK_IVERKSATT -> BehandlingtilstandDto.VEDTAK_IVERKSATT
                        TilstandData.AVSLUTTET_UTEN_VEDTAK -> BehandlingtilstandDto.AVSLUTTET_UTEN_VEDTAK
                        TilstandData.ANNULLERT_PERIODE -> BehandlingtilstandDto.ANNULLERT_PERIODE
                        TilstandData.TIL_INFOTRYGD -> BehandlingtilstandDto.TIL_INFOTRYGD
                    },
                    vedtakFattet = this.vedtakFattet,
                    avsluttet = this.avsluttet,
                    kilde = this.kilde.tilDto(),
                    endringer = this.endringer.map { it.tilDto() }
                )
                enum class TilstandData {
                    UBEREGNET, UBEREGNET_OMGJØRING, UBEREGNET_REVURDERING, BEREGNET, BEREGNET_OMGJØRING, BEREGNET_REVURDERING,
                    VEDTAK_FATTET, REVURDERT_VEDTAK_AVVIST, VEDTAK_IVERKSATT, AVSLUTTET_UTEN_VEDTAK, ANNULLERT_PERIODE, TIL_INFOTRYGD
                }
                enum class AvsenderData {
                    SYKMELDT, ARBEIDSGIVER, SAKSBEHANDLER, SYSTEM;
                    fun tilDto() = when (this) {
                        SYKMELDT -> AvsenderDto.SYKMELDT
                        ARBEIDSGIVER -> AvsenderDto.ARBEIDSGIVER
                        SAKSBEHANDLER -> AvsenderDto.SAKSBEHANDLER
                        SYSTEM -> AvsenderDto.SYSTEM
                    }
                }

                data class KildeData(
                    val meldingsreferanseId: UUID,
                    val innsendt: LocalDateTime,
                    val registrert: LocalDateTime,
                    val avsender: AvsenderData
                ) {
                    fun tilDto() = BehandlingkildeDto(
                        meldingsreferanseId = this.meldingsreferanseId,
                        innsendt = this.innsendt,
                        registert = this.registrert,
                        avsender = this.avsender.tilDto()
                    )
                }
                data class EndringData(
                    val id: UUID,
                    val tidsstempel: LocalDateTime,
                    val sykmeldingsperiodeFom: LocalDate,
                    val sykmeldingsperiodeTom: LocalDate,
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val skjæringstidspunkt: LocalDate,
                    val utbetalingId: UUID?,
                    val vilkårsgrunnlagId: UUID?,
                    val sykdomstidslinje: SykdomstidslinjeData,
                    val dokumentsporing: DokumentsporingData,
                    val arbeidsgiverperioder: List<PeriodeData>?
                ) {
                    fun tilDto() = BehandlingendringInnDto(
                        id = this.id,
                        tidsstempel = this.tidsstempel,
                        sykmeldingsperiode = PeriodeDto(fom = sykmeldingsperiodeFom, tom = sykmeldingsperiodeTom),
                        periode = PeriodeDto(fom = this.fom, tom = this.tom),
                        vilkårsgrunnlagId = this.vilkårsgrunnlagId,
                        utbetalingId = this.utbetalingId,
                        dokumentsporing = this.dokumentsporing.tilDto(),
                        sykdomstidslinje = this.sykdomstidslinje.tilDto(),
                        skjæringstidspunkt = skjæringstidspunkt,
                        arbeidsgiverperiode = arbeidsgiverperioder?.map { it.tilDto() }
                    )
                }
            }
            data class DataForSimuleringData(
                val totalbeløp: Int,
                val perioder: List<SimulertPeriode>
            ) {
                internal fun tilDto() = SimuleringResultatDto(
                    totalbeløp = totalbeløp,
                    perioder = perioder.map { it.tilDto() }
                )

                data class SimulertPeriode(
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val utbetalinger: List<SimulertUtbetaling>
                ) {

                    internal fun tilDto(): SimuleringResultatDto.SimulertPeriode {
                        return SimuleringResultatDto.SimulertPeriode(
                            fom = fom,
                            tom = tom,
                            utbetalinger = utbetalinger.map { it.tilDto() }
                        )
                    }
                }

                data class SimulertUtbetaling(
                    val forfallsdato: LocalDate,
                    val utbetalesTil: Mottaker,
                    val feilkonto: Boolean,
                    val detaljer: List<Detaljer>
                ) {
                    internal fun tilDto(): SimuleringResultatDto.SimulertUtbetaling {
                        return SimuleringResultatDto.SimulertUtbetaling(
                            forfallsdato = forfallsdato,
                            utbetalesTil = SimuleringResultatDto.Mottaker(
                                id = utbetalesTil.id,
                                navn = utbetalesTil.navn
                            ),
                            feilkonto = feilkonto,
                            detaljer = detaljer.map { it.tilDto() }
                        )
                    }
                }

                data class Detaljer(
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val konto: String,
                    val beløp: Int,
                    val klassekode: Klassekode,
                    val uføregrad: Int,
                    val utbetalingstype: String,
                    val tilbakeføring: Boolean,
                    val sats: Sats,
                    val refunderesOrgnummer: String
                ) {
                    internal fun tilDto(): SimuleringResultatDto.Detaljer {
                        return SimuleringResultatDto.Detaljer(
                            fom = fom,
                            tom = tom,
                            konto = konto,
                            beløp = beløp,
                            klassekode = SimuleringResultatDto.Klassekode(
                                kode = klassekode.kode,
                                beskrivelse = klassekode.beskrivelse
                            ),
                            uføregrad = uføregrad,
                            utbetalingstype = utbetalingstype,
                            tilbakeføring = tilbakeføring,
                            sats = SimuleringResultatDto.Sats(
                                sats = sats.sats,
                                antall = sats.antall,
                                type = sats.type
                            ),
                            refunderesOrgnummer = refunderesOrgnummer
                        )
                    }
                }

                data class Sats(
                    val sats: Double,
                    val antall: Int,
                    val type: String
                )

                data class Klassekode(
                    val kode: String,
                    val beskrivelse: String
                )

                data class Mottaker(
                    val id: String,
                    val navn: String
                )
            }
        }

        data class RefusjonData(
            val meldingsreferanseId: UUID,
            val førsteFraværsdag: LocalDate?,
            val arbeidsgiverperioder: List<PeriodeData>,
            val beløp: Double?,
            val sisteRefusjonsdag: LocalDate?,
            val endringerIRefusjon: List<EndringIRefusjonData>,
            val tidsstempel: LocalDateTime
        ) {
            fun tilDto() = RefusjonInnDto(
                meldingsreferanseId = this.meldingsreferanseId,
                førsteFraværsdag = this.førsteFraværsdag,
                arbeidsgiverperioder = this.arbeidsgiverperioder.map { it.tilDto() },
                beløp = this.beløp?.let { InntektbeløpDto.MånedligDouble(it) },
                sisteRefusjonsdag = this.sisteRefusjonsdag,
                endringerIRefusjon = this.endringerIRefusjon.map { it.tilDto() },
                tidsstempel = this.tidsstempel
            )

            data class EndringIRefusjonData(
                val beløp: Double,
                val endringsdato: LocalDate
            ) {
                fun tilDto() = EndringIRefusjonDto(beløp = InntektbeløpDto.MånedligDouble(this.beløp), endringsdato = this.endringsdato)
            }
        }
    }

    data class SykdomshistorikkData(
        val tidsstempel: LocalDateTime,
        val id: UUID,
        val hendelseId: UUID?,
        val hendelseSykdomstidslinje: ArbeidsgiverData.SykdomstidslinjeData,
        val beregnetSykdomstidslinje: ArbeidsgiverData.SykdomstidslinjeData
    ) {
        fun tilDto() = SykdomshistorikkElementDto(
            id = this.id,
            hendelseId = this.hendelseId,
            tidsstempel = this.tidsstempel,
            hendelseSykdomstidslinje = hendelseSykdomstidslinje.tilDto(),
            beregnetSykdomstidslinje = beregnetSykdomstidslinje.tilDto()
        )
    }

    data class UtbetalingData(
        val id: UUID,
        val korrelasjonsId: UUID,
        val fom: LocalDate,
        val tom: LocalDate,
        val annulleringer: List<UUID>?,
        val utbetalingstidslinje: UtbetalingstidslinjeData,
        val arbeidsgiverOppdrag: OppdragData,
        val personOppdrag: OppdragData,
        val tidsstempel: LocalDateTime,
        val type: UtbetalingtypeData,
        val status: UtbetalingstatusData,
        val maksdato: LocalDate,
        val forbrukteSykedager: Int?,
        val gjenståendeSykedager: Int?,
        val vurdering: VurderingData?,
        val overføringstidspunkt: LocalDateTime?,
        val avstemmingsnøkkel: Long?,
        val avsluttet: LocalDateTime?,
        val oppdatert: LocalDateTime
    ) {
        enum class UtbetalingtypeData { UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING, FERIEPENGER }
        enum class UtbetalingstatusData {
            NY,
            IKKE_UTBETALT,
            IKKE_GODKJENT,
            OVERFØRT,
            UTBETALT,
            GODKJENT,
            GODKJENT_UTEN_UTBETALING,
            ANNULLERT,
            FORKASTET
        }
        fun tilDto() = UtbetalingInnDto(
            id = this.id,
            korrelasjonsId = this.korrelasjonsId,
            periode = PeriodeDto(fom = this.fom, tom = this.tom),
            utbetalingstidslinje = this.utbetalingstidslinje.tilDto(),
            arbeidsgiverOppdrag = this.arbeidsgiverOppdrag.tilDto(),
            personOppdrag = this.personOppdrag.tilDto(),
            tidsstempel = this.tidsstempel,
            tilstand = when (status) {
                UtbetalingstatusData.NY -> UtbetalingTilstandDto.NY
                UtbetalingstatusData.IKKE_UTBETALT -> UtbetalingTilstandDto.IKKE_UTBETALT
                UtbetalingstatusData.IKKE_GODKJENT -> UtbetalingTilstandDto.IKKE_GODKJENT
                UtbetalingstatusData.OVERFØRT -> UtbetalingTilstandDto.OVERFØRT
                UtbetalingstatusData.UTBETALT -> UtbetalingTilstandDto.UTBETALT
                UtbetalingstatusData.GODKJENT -> UtbetalingTilstandDto.GODKJENT
                UtbetalingstatusData.GODKJENT_UTEN_UTBETALING -> UtbetalingTilstandDto.GODKJENT_UTEN_UTBETALING
                UtbetalingstatusData.ANNULLERT -> UtbetalingTilstandDto.ANNULLERT
                UtbetalingstatusData.FORKASTET -> UtbetalingTilstandDto.FORKASTET
            },
            type = when (type) {
                UtbetalingtypeData.UTBETALING -> UtbetalingtypeDto.UTBETALING
                UtbetalingtypeData.ETTERUTBETALING -> UtbetalingtypeDto.ETTERUTBETALING
                UtbetalingtypeData.ANNULLERING -> UtbetalingtypeDto.ANNULLERING
                UtbetalingtypeData.REVURDERING -> UtbetalingtypeDto.REVURDERING
                UtbetalingtypeData.FERIEPENGER -> UtbetalingtypeDto.FERIEPENGER
            },
            maksdato = this.maksdato,
            forbrukteSykedager = this.forbrukteSykedager,
            gjenståendeSykedager = this.gjenståendeSykedager,
            annulleringer = this.annulleringer ?: emptyList(),
            vurdering = this.vurdering?.tilDto(),
            overføringstidspunkt = overføringstidspunkt,
            avstemmingsnøkkel = avstemmingsnøkkel,
            avsluttet = avsluttet,
            oppdatert = oppdatert
        )

        data class VurderingData(
            val godkjent: Boolean,
            val ident: String,
            val epost: String,
            val tidspunkt: LocalDateTime,
            val automatiskBehandling: Boolean
        ) {
            fun tilDto() = UtbetalingVurderingDto(
                godkjent = godkjent,
                ident = ident,
                epost = epost,
                tidspunkt = tidspunkt,
                automatiskBehandling = automatiskBehandling
            )
        }
    }

    data class OppdragData(
        val mottaker: String,
        val fagområde: String,
        val linjer: List<UtbetalingslinjeData>,
        val fagsystemId: String,
        val endringskode: String,
        val tidsstempel: LocalDateTime,
        val nettoBeløp: Int,
        val avstemmingsnøkkel: Long?,
        val status: OppdragstatusData?,
        val overføringstidspunkt: LocalDateTime?,
        val erSimulert: Boolean,
        val simuleringsResultat: ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData?
    ) {
        enum class OppdragstatusData { OVERFØRT, AKSEPTERT, AKSEPTERT_MED_FEIL, AVVIST, FEIL }
        fun tilDto() = OppdragInnDto(
            mottaker = this.mottaker,
            fagområde = when (fagområde) {
                "SPREF" -> FagområdeDto.SPREF
                "SP" -> FagområdeDto.SP
                else -> error("Ukjent fagområde: $fagområde")
            },
            linjer = this.linjer.map { it.tilDto() },
            fagsystemId = this.fagsystemId,
            endringskode = when (endringskode) {
                "NY" -> EndringskodeDto.NY
                "ENDR" -> EndringskodeDto.ENDR
                "UEND" -> EndringskodeDto.UEND
                else -> error("Ukjent endringskode: $endringskode")
            },
            nettoBeløp = this.nettoBeløp,
            overføringstidspunkt = this.overføringstidspunkt,
            avstemmingsnøkkel = this.avstemmingsnøkkel,
            status = when (status) {
                OppdragstatusData.OVERFØRT -> OppdragstatusDto.OVERFØRT
                OppdragstatusData.AKSEPTERT -> OppdragstatusDto.AKSEPTERT
                OppdragstatusData.AKSEPTERT_MED_FEIL -> OppdragstatusDto.AKSEPTERT_MED_FEIL
                OppdragstatusData.AVVIST -> OppdragstatusDto.AVVIST
                OppdragstatusData.FEIL -> OppdragstatusDto.FEIL
                null -> null
            },
            tidsstempel = this.tidsstempel,
            erSimulert = this.erSimulert,
            simuleringsResultat = this.simuleringsResultat?.tilDto()
        )
    }

    data class UtbetalingslinjeData(
        val fom: LocalDate,
        val tom: LocalDate,
        val satstype: String,
        val sats: Int,
        val grad: Int?,
        val refFagsystemId: String?,
        val delytelseId: Int,
        val refDelytelseId: Int?,
        val endringskode: String,
        val klassekode: String,
        val datoStatusFom: LocalDate?
    ) {
        fun tilDto() = UtbetalingslinjeInnDto(
            fom = this.fom,
            tom = this.tom,
            satstype = when (this.satstype) {
                "ENG" -> SatstypeDto.Engang
                "DAG" -> SatstypeDto.Daglig
                else -> error("Ukjent satstype: $satstype")
            },
            beløp = this.sats,
            grad = this.grad,
            refFagsystemId = this.refFagsystemId,
            delytelseId = this.delytelseId,
            refDelytelseId = this.refDelytelseId,
            endringskode = when (this.endringskode) {
                "NY" -> EndringskodeDto.NY
                "ENDR" -> EndringskodeDto.ENDR
                "UEND" -> EndringskodeDto.UEND
                else -> error("Ukjent endringskode: $endringskode")
            },
            klassekode = when (this.klassekode) {
                "SPREFAG-IOP" -> KlassekodeDto.RefusjonIkkeOpplysningspliktig
                "SPREFAGFER-IOP" -> KlassekodeDto.RefusjonFeriepengerIkkeOpplysningspliktig
                "SPATORD" -> KlassekodeDto.SykepengerArbeidstakerOrdinær
                "SPATFER" -> KlassekodeDto.SykepengerArbeidstakerFeriepenger
                else -> error("Ukjent klassekode: ${this.klassekode}")
            },
            datoStatusFom = this.datoStatusFom
        )
    }

    data class UtbetalingstidslinjeData(
        val dager: List<UtbetalingsdagData>
    ) {
        fun tilDto() = UtbetalingstidslinjeInnDto(dager = this.dager.flatMap { it.tilDto() })

        enum class BegrunnelseData {
            SykepengedagerOppbrukt,
            SykepengedagerOppbruktOver67,
            MinimumInntekt,
            MinimumInntektOver67,
            EgenmeldingUtenforArbeidsgiverperiode,
            MinimumSykdomsgrad,
            AndreYtelserAap,
            AndreYtelserDagpenger,
            AndreYtelserForeldrepenger,
            AndreYtelserOmsorgspenger,
            AndreYtelserOpplaringspenger,
            AndreYtelserPleiepenger,
            AndreYtelserSvangerskapspenger,
            EtterDødsdato,
            ManglerMedlemskap,
            ManglerOpptjening,
            Over70,
            NyVilkårsprøvingNødvendig;

            fun tilDto() = when (this) {
                SykepengedagerOppbrukt -> BegrunnelseDto.SykepengedagerOppbrukt
                SykepengedagerOppbruktOver67 -> BegrunnelseDto.SykepengedagerOppbruktOver67
                MinimumInntekt -> BegrunnelseDto.MinimumInntekt
                MinimumInntektOver67 -> BegrunnelseDto.MinimumInntektOver67
                EgenmeldingUtenforArbeidsgiverperiode -> BegrunnelseDto.EgenmeldingUtenforArbeidsgiverperiode
                MinimumSykdomsgrad -> BegrunnelseDto.MinimumSykdomsgrad
                AndreYtelserAap -> BegrunnelseDto.AndreYtelserAap
                AndreYtelserDagpenger -> BegrunnelseDto.AndreYtelserDagpenger
                AndreYtelserForeldrepenger -> BegrunnelseDto.AndreYtelserForeldrepenger
                AndreYtelserOmsorgspenger -> BegrunnelseDto.AndreYtelserOmsorgspenger
                AndreYtelserOpplaringspenger -> BegrunnelseDto.AndreYtelserOpplaringspenger
                AndreYtelserPleiepenger -> BegrunnelseDto.AndreYtelserPleiepenger
                AndreYtelserSvangerskapspenger -> BegrunnelseDto.AndreYtelserSvangerskapspenger
                EtterDødsdato -> BegrunnelseDto.EtterDødsdato
                ManglerMedlemskap -> BegrunnelseDto.ManglerMedlemskap
                ManglerOpptjening -> BegrunnelseDto.ManglerOpptjening
                Over70 -> BegrunnelseDto.Over70
                NyVilkårsprøvingNødvendig -> BegrunnelseDto.NyVilkårsprøvingNødvendig
            }
        }

        enum class TypeData {
            ArbeidsgiverperiodeDag,
            NavDag,
            NavHelgDag,
            Arbeidsdag,
            Fridag,
            AvvistDag,
            UkjentDag,
            ForeldetDag,
            ArbeidsgiverperiodedagNav
        }

        data class UtbetalingsdagData(
            val type: TypeData,
            val aktuellDagsinntekt: Double,
            val beregningsgrunnlag: Double,
            val dekningsgrunnlag: Double,
            val grunnbeløpgrense: Double?,
            val begrunnelser: List<BegrunnelseData>?,
            val grad: Double,
            val totalGrad: Double,
            val arbeidsgiverRefusjonsbeløp: Double,
            val arbeidsgiverbeløp: Double?,
            val personbeløp: Double?,
            val er6GBegrenset: Boolean?,
            val dato: LocalDate?,
            val fom: LocalDate?,
            val tom: LocalDate?
        ) {
            private val datoer = datosekvens(dato, fom, tom)
            private val økonomiDto by lazy {
                ØkonomiInnDto(
                    grad = ProsentdelDto(grad),
                    totalGrad = ProsentdelDto(totalGrad),
                    arbeidsgiverRefusjonsbeløp = InntektbeløpDto.DagligDouble(this.arbeidsgiverRefusjonsbeløp),
                    aktuellDagsinntekt = InntektbeløpDto.DagligDouble(this.aktuellDagsinntekt),
                    beregningsgrunnlag = InntektbeløpDto.DagligDouble(this.beregningsgrunnlag),
                    dekningsgrunnlag = InntektbeløpDto.DagligDouble(this.dekningsgrunnlag),
                    grunnbeløpgrense = this.grunnbeløpgrense?.let { InntektbeløpDto.Årlig(it) },
                    arbeidsgiverbeløp = this.arbeidsgiverbeløp?.let { InntektbeløpDto.DagligDouble(it) },
                    personbeløp = this.personbeløp?.let { InntektbeløpDto.DagligDouble(it) },
                    er6GBegrenset = this.er6GBegrenset
                )
            }

            fun tilDto() = datoer.map { tilDto(it) }
            private fun tilDto(dato: LocalDate): UtbetalingsdagInnDto {
                return when (type) {
                    TypeData.ArbeidsgiverperiodeDag -> UtbetalingsdagInnDto.ArbeidsgiverperiodeDagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.NavDag -> UtbetalingsdagInnDto.NavDagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.NavHelgDag -> UtbetalingsdagInnDto.NavHelgDagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.Arbeidsdag -> UtbetalingsdagInnDto.ArbeidsdagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.Fridag -> UtbetalingsdagInnDto.FridagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.AvvistDag -> UtbetalingsdagInnDto.AvvistDagDto(dato = dato, økonomi = økonomiDto, begrunnelser = begrunnelser!!.map { it.tilDto() })
                    TypeData.UkjentDag -> UtbetalingsdagInnDto.UkjentDagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.ForeldetDag -> UtbetalingsdagInnDto.ForeldetDagDto(dato = dato, økonomi = økonomiDto)
                    TypeData.ArbeidsgiverperiodedagNav -> UtbetalingsdagInnDto.ArbeidsgiverperiodeDagNavDto(dato = dato, økonomi = økonomiDto)
                }
            }
        }
    }
}

private fun LocalDate.erHelg() = dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun datosekvens(dato: LocalDate?, fom: LocalDate?, tom: LocalDate?): Sequence<LocalDate> {
    check(dato != null || (fom != null && tom != null)) {
        "må ha <dato> eller både <fom> og <tom>. Fikk dato=$dato, fom = $fom, tom = $tom"
    }
    return when {
        dato != null -> sequenceOf(dato)
        else -> fom!!.datesUntil(tom!!.plusDays(1)).asSequence()
    }
}
