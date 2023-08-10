package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.person.PersonObserver.ForespurtOpplysning.Companion.toJsonMap
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Utbetalingsperiode
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.økonomi.Prosentdel

interface PersonObserver : SykefraværstilfelleeventyrObserver {
    data class VedtaksperiodeIkkeFunnetEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID
    )

    data class VedtaksperiodeEndretEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val gjeldendeTilstand: TilstandType,
        val forrigeTilstand: TilstandType,
        val hendelser: Set<UUID>,
        val makstid: LocalDateTime,
        val fom: LocalDate,
        val tom: LocalDate
    )

    data class VedtaksperiodeVenterEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val hendelser: Set<UUID>,
        val ventetSiden: LocalDateTime,
        val venterTil: LocalDateTime,
        val venterPå: VenterPå
    ) {
        data class VenterPå(
            val vedtaksperiodeId: UUID,
            val organisasjonsnummer: String,
            val venteårsak: Venteårsak
        )

        data class Venteårsak(
            val hva : String,
            val hvorfor: String?
        )
    }

    data class VedtaksperiodeForkastetEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val gjeldendeTilstand: TilstandType,
        val hendelser: Set<UUID>,
        val fom: LocalDate,
        val tom: LocalDate,
        val forlengerPeriode: Boolean,
        val harPeriodeInnenfor16Dager: Boolean,
        val trengerArbeidsgiveropplysninger: Boolean,
        val sykmeldingsperioder: List<Periode>
    ) {
        fun toJsonMap(): Map<String, Any> =
            mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "tilstand" to gjeldendeTilstand,
                "hendelser" to hendelser,
                "fom" to fom,
                "tom" to tom,
                "forlengerPeriode" to forlengerPeriode,
                "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager,
                "trengerArbeidsgiveropplysninger" to trengerArbeidsgiveropplysninger,
                "sykmeldingsperioder" to sykmeldingsperioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                }
            )
    }
    data class InntektsmeldingFørSøknadEvent(
        val inntektsmeldingId: UUID,
        val overlappendeSykmeldingsperioder: List<Periode>,
        val organisasjonsnummer: String
    )

    data class ManglendeInntektsmeldingEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val fom: LocalDate,
        val tom: LocalDate,
        val søknadIder: Set<UUID>
    )

    data class TrengerIkkeInntektsmeldingEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val fom: LocalDate,
        val tom: LocalDate,
        val søknadIder: Set<UUID>
    )

    class TrengerArbeidsgiveropplysningerEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val skjæringstidspunkt: LocalDate,
        val sykmeldingsperioder: List<Periode>,
        val egenmeldingsperioder: List<Periode>,
        val forespurteOpplysninger: List<ForespurtOpplysning>
    ) {
        fun toJsonMap(): Map<String, Any> =
            mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "skjæringstidspunkt" to skjæringstidspunkt,
                "sykmeldingsperioder" to sykmeldingsperioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                },
                "egenmeldingsperioder" to egenmeldingsperioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                },
                "forespurteOpplysninger" to forespurteOpplysninger.toJsonMap()
            )
    }

    data class ArbeidsgiveropplysningerKorrigertEvent(
        val korrigertInntektsmeldingId: UUID,
        val korrigerendeInntektsopplysningId: UUID,
        val korrigerendeInntektektsopplysningstype: KorrigerendeInntektektsopplysningstype
    ) {
        fun toJsonMap(): Map<String, Any> =
            mapOf(
                "korrigertInntektsmeldingId" to korrigertInntektsmeldingId,
                "korrigerendeInntektsopplysningId" to korrigerendeInntektsopplysningId,
                "korrigerendeInntektektsopplysningstype" to korrigerendeInntektektsopplysningstype
            )

        enum class KorrigerendeInntektektsopplysningstype {
            INNTEKTSMELDING,
            SAKSBEHANDLER
        }
    }
    sealed class ForespurtOpplysning {

        companion object {
            fun List<ForespurtOpplysning>.toJsonMap() = map { forespurtOpplysning ->
                when (forespurtOpplysning) {
                    is Arbeidsgiverperiode -> mapOf(
                        "opplysningstype" to "Arbeidsgiverperiode",
                        "forslag" to forespurtOpplysning.forslag.map { forslag ->
                            mapOf(
                                "fom" to forslag.start,
                                "tom" to forslag.endInclusive
                            )
                        }
                    )

                    is Inntekt -> mapOf(
                        "opplysningstype" to "Inntekt",
                        "forslag" to mapOf(
                            "beregningsmåneder" to forespurtOpplysning.forslag.beregningsmåneder
                        )
                    )

                    is FastsattInntekt -> mapOf(
                        "opplysningstype" to "FastsattInntekt",
                        "fastsattInntekt" to forespurtOpplysning.fastsattInntekt.reflection { _, månedlig, _, _ -> månedlig }
                    )

                    is Refusjon -> mapOf(
                        "opplysningstype" to "Refusjon",
                        "forslag" to forespurtOpplysning.forslag.map { forslag ->
                            mapOf(
                                "fom" to forslag.fom(),
                                "tom" to forslag.tom(),
                                "beløp" to forslag.beløp().reflection {_, månedlig, _, _ -> månedlig}
                            )
                        }
                    )
                }
            }
        }
    }

    data class Inntektsforslag(val beregningsmåneder: List<YearMonth>)
    data class Inntekt(val forslag: Inntektsforslag) : ForespurtOpplysning()
    data class FastsattInntekt(val fastsattInntekt: no.nav.helse.økonomi.Inntekt) : ForespurtOpplysning()
    data class Arbeidsgiverperiode(val forslag: List<Periode>) : ForespurtOpplysning()
    data class Refusjon(val forslag: List<Refusjonsopplysning>) : ForespurtOpplysning()

    data class UtbetalingAnnullertEvent(
        val organisasjonsnummer: String,
        val utbetalingId: UUID,
        val korrelasjonsId: UUID,
        val arbeidsgiverFagsystemId: String,
        val personFagsystemId: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val annullertAvSaksbehandler: LocalDateTime,
        val saksbehandlerEpost: String,
        val saksbehandlerIdent: String
    )
    data class UtbetalingEndretEvent(
        val organisasjonsnummer: String,
        val utbetalingId: UUID,
        val type: String,
        val forrigeStatus: String,
        val gjeldendeStatus: String,
        val arbeidsgiverOppdrag: OppdragEventDetaljer,
        val personOppdrag: OppdragEventDetaljer,
        val korrelasjonsId: UUID
    ) {
        data class OppdragEventDetaljer(val fagsystemId: String, val nettoBeløp: Int)
    }

    data class UtbetalingUtbetaltEvent(
        val organisasjonsnummer: String,
        val utbetalingId: UUID,
        val korrelasjonsId: UUID,
        val type: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val maksdato: LocalDate,
        val forbrukteSykedager: Int,
        val gjenståendeSykedager: Int,
        val stønadsdager: Int,
        val epost: String,
        val tidspunkt: LocalDateTime,
        val automatiskBehandling: Boolean,
        val arbeidsgiverOppdrag: Map<String, Any?>,
        val personOppdrag: Map<String, Any?>,
        val utbetalingsdager: List<Utbetalingsdag>,
        val vedtaksperiodeIder: List<UUID>,
        val ident: String
    )

    data class Utbetalingsdag(
        val dato: LocalDate,
        val type: Dagtype,
        val begrunnelser: List<EksternBegrunnelseDTO>? = null
    ) {
        enum class Dagtype {
            ArbeidsgiverperiodeDag,
            NavDag,
            NavHelgDag,
            Arbeidsdag,
            Fridag,
            AvvistDag,
            UkjentDag,
            ForeldetDag,
            Permisjonsdag,
            Feriedag
        }

        enum class EksternBegrunnelseDTO {
            SykepengedagerOppbrukt,
            SykepengedagerOppbruktOver67,
            MinimumInntekt,
            MinimumInntektOver67,
            EgenmeldingUtenforArbeidsgiverperiode,
            AndreYtelserAap,
            AndreYtelserDagpenger,
            AndreYtelserForeldrepenger,
            AndreYtelserOmsorgspenger,
            AndreYtelserOpplaringspenger,
            AndreYtelserPleiepenger,
            AndreYtelserSvangerskapspenger,
            MinimumSykdomsgrad,
            EtterDødsdato,
            ManglerMedlemskap,
            ManglerOpptjening,
            Over70;

            internal companion object {
                fun fraBegrunnelse(begrunnelse: Begrunnelse) = when (begrunnelse) {
                    is Begrunnelse.SykepengedagerOppbrukt -> SykepengedagerOppbrukt
                    is Begrunnelse.SykepengedagerOppbruktOver67 -> SykepengedagerOppbruktOver67
                    is Begrunnelse.MinimumSykdomsgrad -> MinimumSykdomsgrad
                    is Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode -> EgenmeldingUtenforArbeidsgiverperiode
                    is Begrunnelse.MinimumInntekt -> MinimumInntekt
                    is Begrunnelse.MinimumInntektOver67 -> MinimumInntektOver67
                    is Begrunnelse.EtterDødsdato -> EtterDødsdato
                    is Begrunnelse.ManglerMedlemskap -> ManglerMedlemskap
                    is Begrunnelse.ManglerOpptjening -> ManglerOpptjening
                    is Begrunnelse.Over70 -> Over70
                    is Begrunnelse.AndreYtelserAap -> AndreYtelserAap
                    is Begrunnelse.AndreYtelserDagpenger -> AndreYtelserDagpenger
                    is Begrunnelse.AndreYtelserForeldrepenger -> AndreYtelserForeldrepenger
                    is Begrunnelse.AndreYtelserOmsorgspenger -> AndreYtelserOmsorgspenger
                    is Begrunnelse.AndreYtelserOpplaringspenger -> AndreYtelserOpplaringspenger
                    is Begrunnelse.AndreYtelserPleiepenger -> AndreYtelserPleiepenger
                    is Begrunnelse.AndreYtelserSvangerskapspenger -> AndreYtelserSvangerskapspenger
                    is Begrunnelse.NyVilkårsprøvingNødvendig -> SykepengedagerOppbrukt // TODO: Map til NyVilkårsprøvingNødvendig
                }
            }
        }
    }

    data class FeriepengerUtbetaltEvent(
        val organisasjonsnummer: String,
        val arbeidsgiverOppdrag: Map<String, Any?>,
        val personOppdrag: Map<String, Any?> = mapOf("linjer" to emptyList<String>())
    )

    data class OverlappendeInfotrygdperiodeEtterInfotrygdendring(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val vedtaksperiodeFom: LocalDate,
        val vedtaksperiodeTom: LocalDate,
        val vedtaksperiodetilstand: String,
        val infotrygdhistorikkHendelseId: String?,
        val infotrygdperioder: List<Infotrygdperiode>
    ) {
        data class Infotrygdperiode(
            val fom: LocalDate,
            val tom: LocalDate,
            val type: String,
            val orgnummer: String?
        )
        internal class InfotrygdperiodeBuilder(infotrygdperiode: no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode): InfotrygdperiodeVisitor {
            var infotrygdperiode: Infotrygdperiode? = null

            init {
                infotrygdperiode.accept(this)
            }

            override fun visitInfotrygdhistorikkFerieperiode(periode: Friperiode, fom: LocalDate, tom: LocalDate) {
                infotrygdperiode = Infotrygdperiode(fom, tom, "FRIPERIODE", null)
            }

            override fun visitInfotrygdhistorikkPersonUtbetalingsperiode(
                periode: Utbetalingsperiode,
                orgnr: String,
                fom: LocalDate,
                tom: LocalDate,
                grad: Prosentdel,
                inntekt: no.nav.helse.økonomi.Inntekt
            ) {
                infotrygdperiode = Infotrygdperiode(fom, tom, "PERSONUTBETALING", orgnr)
            }

            override fun visitInfotrygdhistorikkArbeidsgiverUtbetalingsperiode(
                periode: Utbetalingsperiode,
                orgnr: String,
                fom: LocalDate,
                tom: LocalDate,
                grad: Prosentdel,
                inntekt: no.nav.helse.økonomi.Inntekt
            ) {
                infotrygdperiode = Infotrygdperiode(fom, tom, "ARBEIDSGIVERUTBETALING", orgnr)
            }
        }
    }

    data class VedtakFattetEvent(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val periode: Periode,
        val hendelseIder: Set<UUID>,
        val skjæringstidspunkt: LocalDate,
        val sykepengegrunnlag: Double,
        val beregningsgrunnlag: Double,
        val omregnetÅrsinntektPerArbeidsgiver: Map<String, Double>,
        val inntekt: Double,
        val utbetalingId: UUID?,
        val sykepengegrunnlagsbegrensning: String,
        val vedtakFattetTidspunkt: LocalDateTime,
        val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta?
    ) {
        enum class Fastsatt {
            EtterHovedregel,
            EtterSkjønn,
            IInfotrygd
        }

        enum class Tag {
            `6GBegrenset`
        }

        sealed class Sykepengegrunnlagsfakta {
            abstract val fastsatt: Fastsatt
            abstract val omregnetÅrsinntekt: Double
        }
        data class FastsattIInfotrygd(override val omregnetÅrsinntekt: Double) : Sykepengegrunnlagsfakta() {
            override val fastsatt = Fastsatt.IInfotrygd
        }
        data class FastsattEtterHovedregel(
            override val omregnetÅrsinntekt: Double,
            val innrapportertÅrsinntekt: Double,
            val avviksprosent: Double,
            val `6G`: Double,
            val tags: Set<Tag>,
            val arbeidsgivere: List<Arbeidsgiver>
        ) : Sykepengegrunnlagsfakta() {
            override val fastsatt = Fastsatt.EtterHovedregel
            data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double)
        }
        data class FastsattEtterSkjønn(
            override val omregnetÅrsinntekt: Double,
            val innrapportertÅrsinntekt: Double,
            val skjønnsfastsatt: Double,
            val avviksprosent: Double,
            val `6G`: Double,
            val tags: Set<Tag>,
            val arbeidsgivere: List<Arbeidsgiver>
        ) : Sykepengegrunnlagsfakta() {
            override val fastsatt = Fastsatt.EtterSkjønn
            data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val skjønnsfastsatt: Double)
        }
    }

    data class OverstyringIgangsatt(
        val årsak: String,
        val skjæringstidspunkt: LocalDate,
        val periodeForEndring: Periode,
        val berørtePerioder: List<VedtaksperiodeData>
    ) {
        val typeEndring get() = if (berørtePerioder.any { it.typeEndring == "REVURDERING" }) "REVURDERING" else "OVERSTYRING"

        data class VedtaksperiodeData(
            val orgnummer: String,
            val vedtaksperiodeId: UUID,
            val periode: Periode,
            val skjæringstidspunkt: LocalDate,
            val typeEndring: String
        )
    }

    data class VedtaksperiodeOpprettet(
        val vedtaksperiodeId: UUID,
        val organisasjonsnummer: String,
        val periode: Periode,
        val skjæringstidspunkt: LocalDate,
        val opprettet: LocalDateTime
    )

    fun inntektsmeldingReplay(personidentifikator: Personidentifikator, aktørId: String, organisasjonsnummer: String, vedtaksperiodeId: UUID, skjæringstidspunkt: LocalDate, sammenhengendePeriode: Periode) {}
    fun trengerIkkeInntektsmeldingReplay(vedtaksperiodeId: UUID) {}
    fun vedtaksperiodeOpprettet(event: VedtaksperiodeOpprettet) {}
    fun vedtaksperiodePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, påminnelse: Påminnelse) {}
    fun vedtaksperiodeIkkePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, nåværendeTilstand: TilstandType) {}
    fun vedtaksperiodeEndret(event: VedtaksperiodeEndretEvent) {}
    fun vedtaksperiodeVenter(event: VedtaksperiodeVenterEvent) {}
    fun vedtaksperiodeForkastet(event: VedtaksperiodeForkastetEvent) {}
    fun vedtaksperiodeIkkeFunnet(event: VedtaksperiodeIkkeFunnetEvent) {}
    fun manglerInntektsmelding(event: ManglendeInntektsmeldingEvent) {}
    fun trengerIkkeInntektsmelding(event: TrengerIkkeInntektsmeldingEvent) {}
    fun trengerArbeidsgiveropplysninger(event: TrengerArbeidsgiveropplysningerEvent) {}
    fun arbeidsgiveropplysningerKorrigert(event: ArbeidsgiveropplysningerKorrigertEvent) {}
    fun utbetalingEndret(event: UtbetalingEndretEvent) {}
    fun utbetalingUtbetalt(event: UtbetalingUtbetaltEvent) {}
    fun utbetalingUtenUtbetaling(event: UtbetalingUtbetaltEvent) {}
    fun feriepengerUtbetalt(event: FeriepengerUtbetaltEvent) {}
    fun annullering(event: UtbetalingAnnullertEvent) {}
    fun avstemt(result: Map<String, Any>) {}
    fun vedtakFattet(event: VedtakFattetEvent) {}

    fun nyVedtaksperiodeUtbetaling(
        personidentifikator: Personidentifikator,
        aktørId: String,
        organisasjonsnummer: String,
        utbetalingId: UUID,
        vedtaksperiodeId: UUID
    ) {}
    fun overstyringIgangsatt(event: OverstyringIgangsatt) {}
    fun overlappendeInfotrygdperiodeEtterInfotrygdendring(event: OverlappendeInfotrygdperiodeEtterInfotrygdendring) {}
    fun inntektsmeldingFørSøknad(event: InntektsmeldingFørSøknadEvent) {}
    fun inntektsmeldingIkkeHåndtert(inntektsmeldingId: UUID, organisasjonsnummer: String) {}
    fun inntektsmeldingHåndtert(inntektsmeldingId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {}
    fun søknadHåndtert(søknadId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {}
    fun behandlingUtført() {}
}
