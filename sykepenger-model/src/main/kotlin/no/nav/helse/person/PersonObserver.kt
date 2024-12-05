package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.person.PersonObserver.ForespurtOpplysning.Companion.toJsonMap
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.OppdragDetaljer
import no.nav.helse.utbetalingstidslinje.Begrunnelse

interface PersonObserver {
    data class SykefraværstilfelleIkkeFunnet(
        val skjæringstidspunkt: LocalDate
    )

    data class VedtaksperiodeIkkeFunnetEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID
    )

    data class VedtaksperiodeEndretEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val gjeldendeTilstand: TilstandType,
        val forrigeTilstand: TilstandType,
        val hendelser: Set<UUID>,
        val makstid: LocalDateTime,
        val fom: LocalDate,
        val tom: LocalDate,
        val skjæringstidspunkt: LocalDate
    )

    data class VedtaksperiodeVenterEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val skjæringstidspunkt: LocalDate,
        val hendelser: Set<UUID>,
        val ventetSiden: LocalDateTime,
        val venterTil: LocalDateTime,
        val venterPå: VenterPå
    ) {
        data class VenterPå(
            val vedtaksperiodeId: UUID,
            val skjæringstidspunkt: LocalDate,
            val organisasjonsnummer: String,
            val venteårsak: Venteårsak
        )

        data class Venteårsak(
            val hva : String,
            val hvorfor: String?
        )
    }

    data class VedtaksperiodeForkastetEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val gjeldendeTilstand: TilstandType,
        val hendelser: Set<UUID>,
        val fom: LocalDate,
        val tom: LocalDate,
        val behandletIInfotrygd: Boolean,
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
                "behandletIInfotrygd" to behandletIInfotrygd,
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
        val relevanteSykmeldingsperioder: List<Periode>,
        val organisasjonsnummer: String
    )

    data class SkatteinntekterLagtTilGrunnEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val skjæringstidspunkt: LocalDate,
        val skatteinntekter: List<Skatteinntekt>,
        val omregnetÅrsinntekt: Double
    ) {
        data class Skatteinntekt(
            val måned: YearMonth,
            val beløp: Double
        )

        fun toJsonMap(): Map<String, Any> =
            mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "behandlingId" to behandlingId,
                "skjæringstidspunkt" to skjæringstidspunkt,
                "omregnetÅrsinntekt" to omregnetÅrsinntekt,
                "skatteinntekter" to skatteinntekter.map {
                    mapOf(
                        "måned" to it.måned,
                        "beløp" to it.beløp
                    )
                }
            )
    }

    data class TrengerArbeidsgiveropplysningerEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val skjæringstidspunkt: LocalDate,
        val sykmeldingsperioder: List<Periode>,
        val egenmeldingsperioder: List<Periode>,
        val førsteFraværsdager: List<FørsteFraværsdag>,
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
                "førsteFraværsdager" to førsteFraværsdager.map {
                    mapOf(
                        "organisasjonsnummer" to it.organisasjonsnummer,
                        "førsteFraværsdag" to it.førsteFraværsdag
                    )
                },
                "forespurteOpplysninger" to forespurteOpplysninger.toJsonMap()
            )
    }

    class TrengerIkkeArbeidsgiveropplysningerEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID
    ) {
        fun toJsonMap(): Map<String, Any> =
            mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId
            )
    }

    data class ArbeidsgiveropplysningerKorrigertEvent(
        val korrigertInntektsmeldingId: UUID,
        val korrigerendeInntektsopplysningId: UUID,
        val korrigerendeInntektektsopplysningstype: Inntektsopplysningstype
    ) {
        fun toJsonMap(): Map<String, Any> =
            mapOf(
                "korrigertInntektsmeldingId" to korrigertInntektsmeldingId,
                "korrigerendeInntektsopplysningId" to korrigerendeInntektsopplysningId,
                "korrigerendeInntektektsopplysningstype" to korrigerendeInntektektsopplysningstype
            )
    }

    data class FørsteFraværsdag(
        val organisasjonsnummer: String,
        val førsteFraværsdag: LocalDate
    )
    sealed class ForespurtOpplysning {

        companion object {
            fun List<ForespurtOpplysning>.toJsonMap() = map { forespurtOpplysning ->
                when (forespurtOpplysning) {
                    is Arbeidsgiverperiode -> mapOf(
                        "opplysningstype" to "Arbeidsgiverperiode"
                    )

                    is Inntekt -> mapOf(
                        "opplysningstype" to "Inntekt",
                        "forslag" to mapOf(
                            "forrigeInntekt" to forespurtOpplysning.forslag?.let {
                                mapOf(
                                    "skjæringstidspunkt" to it.skjæringstidspunkt,
                                    "kilde" to it.kilde.name,
                                    "beløp" to it.beløp
                                )
                            }
                        )
                    )

                    is FastsattInntekt -> mapOf(
                        "opplysningstype" to "FastsattInntekt",
                        "fastsattInntekt" to forespurtOpplysning.fastsattInntekt.månedlig
                    )

                    is Refusjon -> mapOf(
                        "opplysningstype" to "Refusjon",
                        "forslag" to forespurtOpplysning.forslag.map { forslag ->
                            mapOf(
                                "fom" to forslag.fom,
                                "tom" to forslag.tom,
                                "beløp" to forslag.månedligBeløp
                            )
                        }
                    )
                }
            }
        }
    }

    data class Inntektsdata(val skjæringstidspunkt: LocalDate, val kilde: Inntektsopplysningstype, val beløp: Double)
    enum class Inntektsopplysningstype{
        INNTEKTSMELDING,
        SAKSBEHANDLER
    }
    data class Inntekt(val forslag: Inntektsdata?) : ForespurtOpplysning()
    data class FastsattInntekt(val fastsattInntekt: no.nav.helse.økonomi.Inntekt) : ForespurtOpplysning()
    object Arbeidsgiverperiode : ForespurtOpplysning()
    data class Refusjon(val forslag: List<Refusjonsforslag>) : ForespurtOpplysning() {
        data class Refusjonsforslag(val fom: LocalDate, val tom: LocalDate?, val månedligBeløp: Double)
    }

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
        data class OppdragEventDetaljer(
            val fagsystemId: String,
            val mottaker: String,
            val nettoBeløp: Int,
            val linjer: List<OppdragEventLinjeDetaljer>
        ) {
            data class OppdragEventLinjeDetaljer(
                val fom: LocalDate,
                val tom: LocalDate,
                val totalbeløp: Int
            )

            companion object {
                fun mapOppdrag(oppdrag: Oppdrag) = OppdragEventDetaljer(
                    fagsystemId = oppdrag.fagsystemId,
                    mottaker = oppdrag.mottaker,
                    nettoBeløp = oppdrag.nettoBeløp(),
                    linjer = oppdrag.map { linje ->
                        OppdragEventLinjeDetaljer(
                            fom = linje.fom,
                            tom = linje.tom,
                            totalbeløp = linje.totalbeløp()
                        )
                    })
            }
        }
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
        val arbeidsgiverOppdrag: OppdragEventDetaljer,
        val personOppdrag: OppdragEventDetaljer,
        val utbetalingsdager: List<Utbetalingsdag>,
        val ident: String
    ) {
        data class OppdragEventDetaljer(
            val fagsystemId: String,
            val fagområde: String,
            val mottaker: String,
            val nettoBeløp: Int,
            val stønadsdager: Int,
            val fom: LocalDate,
            val tom: LocalDate,
            val linjer: List<OppdragEventLinjeDetaljer>
        ) {
            data class OppdragEventLinjeDetaljer(
                val fom: LocalDate,
                val tom: LocalDate,
                val sats: Int,
                val grad: Double,
                val stønadsdager: Int,
                val totalbeløp: Int,
                val statuskode: String?
            )

            companion object {
                fun mapOppdrag(oppdrag: Oppdrag) = mapOppdragdetaljer(oppdrag.detaljer())
                private fun mapOppdragdetaljer(detaljer: OppdragDetaljer) =
                    OppdragEventDetaljer(
                        fagsystemId = detaljer.fagsystemId,
                        fagområde = detaljer.fagområde,
                        mottaker = detaljer.mottaker,
                        nettoBeløp = detaljer.nettoBeløp,
                        stønadsdager = detaljer.stønadsdager,
                        fom = detaljer.fom,
                        tom = detaljer.tom,
                        linjer = detaljer.linjer.map {
                            OppdragEventLinjeDetaljer(
                                fom = it.fom,
                                tom = it.tom,
                                sats = it.sats,
                                grad = it.grad ?: error("mangler grad for linje"),
                                stønadsdager = it.stønadsdager,
                                totalbeløp = it.totalbeløp,
                                statuskode = it.statuskode
                            )
                        }
                    )
            }
        }
    }

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
            Feriedag,
            ArbeidIkkeGjenopptattDag,
            AndreYtelser
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
        val arbeidsgiverOppdrag: OppdragEventDetaljer,
        val personOppdrag: OppdragEventDetaljer
    ) {
        data class OppdragEventDetaljer(
            val fagsystemId: String,
            val mottaker: String,
            val fom: LocalDate,
            val tom: LocalDate,
            val linjer: List<OppdragEventLinjeDetaljer>
        ) {
            data class OppdragEventLinjeDetaljer(
                val fom: LocalDate,
                val tom: LocalDate,
                val totalbeløp: Int
            )

            companion object {
                fun mapOppdrag(oppdrag: Oppdrag) = mapDetaljer(oppdrag.detaljer())
                private fun mapDetaljer(detaljer: OppdragDetaljer) =
                    OppdragEventDetaljer(
                        fagsystemId = detaljer.fagsystemId,
                        mottaker = detaljer.mottaker,
                        fom = detaljer.fom,
                        tom = detaljer.tom,
                        linjer = detaljer.linjer.map {
                            OppdragEventLinjeDetaljer(
                                fom = it.fom,
                                tom = it.tom,
                                totalbeløp = it.totalbeløp
                            )
                        }
                    )
            }
        }
    }

    data class OverlappendeInfotrygdperioder(
        val overlappendeInfotrygdperioder: List<OverlappendeInfotrygdperiodeEtterInfotrygdendring>,
        val infotrygdhistorikkHendelseId: String
    )

    data class OverlappendeInfotrygdperiodeEtterInfotrygdendring(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val kanForkastes: Boolean,
        val vedtaksperiodeFom: LocalDate,
        val vedtaksperiodeTom: LocalDate,
        val vedtaksperiodetilstand: String,
        val infotrygdperioder: List<Infotrygdperiode>
    ) {
        data class Infotrygdperiode(
            val fom: LocalDate,
            val tom: LocalDate,
            val type: String,
            val orgnummer: String?
        )
    }

    data class AvsluttetUtenVedtakEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val periode: Periode,
        val hendelseIder: Set<UUID>,
        val skjæringstidspunkt: LocalDate,
        val avsluttetTidspunkt: LocalDateTime
    )

    data class BehandlingLukketEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID
    )

    data class BehandlingForkastetEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val automatiskBehandling: Boolean
    )

    data class BehandlingOpprettetEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val søknadIder: Set<UUID>,
        val fom: LocalDate,
        val tom: LocalDate,
        val type: Type,
        val kilde: Kilde
    ) {
        enum class Type {
            Søknad,
            Omgjøring,
            Revurdering
        }
        data class Kilde(
            val meldingsreferanseId: UUID,
            val innsendt: LocalDateTime,
            val registert: LocalDateTime,
            val avsender: Avsender
        )
    }

    data class UtkastTilVedtakEvent(
        val skjæringstidspunkt: LocalDate,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val tags: Set<String>,
        val `6G`: Double?
    ) {
        sealed interface Sykepengegrunnlagsfakta {
            val fastsatt: String
            val omregnetÅrsinntekt: Double
        }
        data class FastsattIInfotrygd(override val omregnetÅrsinntekt: Double) : Sykepengegrunnlagsfakta {
            override val fastsatt = "IInfotrygd"
        }
        data class FastsattEtterHovedregel(override val omregnetÅrsinntekt: Double, val sykepengegrunnlag: Double, val `6G`: Double, val arbeidsgivere: List<Arbeidsgiver>) : Sykepengegrunnlagsfakta {
            override val fastsatt = "EtterHovedregel"
            data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val inntektskilde: Inntektsopplysning.Inntektskilde?)
        }
        data class FastsattEtterSkjønn(override val omregnetÅrsinntekt: Double, val sykepengegrunnlag: Double, val `6G`: Double, val arbeidsgivere: List<Arbeidsgiver>) : Sykepengegrunnlagsfakta {
            override val fastsatt = "EtterSkjønn"
            val skjønnsfastsatt = arbeidsgivere.sumOf { it.skjønnsfastsatt }
            data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val skjønnsfastsatt: Double, val inntektskilde: Inntektsopplysning.Inntektskilde?)
        }
    }

    data class AvsluttetMedVedtakEvent(
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val periode: Periode,
        val hendelseIder: Set<UUID>,
        val skjæringstidspunkt: LocalDate,
        val sykepengegrunnlag: Double,
        val beregningsgrunnlag: Double,
        val omregnetÅrsinntektPerArbeidsgiver: Map<String, Double>,
        val inntekt: Double,
        val utbetalingId: UUID,
        val sykepengegrunnlagsbegrensning: String,
        val vedtakFattetTidspunkt: LocalDateTime,
        val sykepengegrunnlagsfakta: UtkastTilVedtakEvent.Sykepengegrunnlagsfakta
    )

    data class OverstyringIgangsatt(
        val årsak: String,
        val skjæringstidspunkt: LocalDate,
        val periodeForEndring: Periode,
        val berørtePerioder: List<VedtaksperiodeData>,
        val meldingsreferanseId: UUID
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

    data class VedtaksperiodeAnnullertEvent(
        val fom: LocalDate,
        val tom: LocalDate,
        val vedtaksperiodeId: UUID,
        val organisasjonsnummer: String,
        val behandlingId: UUID
    )

    fun inntektsmeldingReplay(
        personidentifikator: Personidentifikator,
        organisasjonsnummer: String,
        vedtaksperiodeId: UUID,
        skjæringstidspunkt: LocalDate,
        sykmeldingsperioder: List<Periode>,
        egenmeldingsperioder: List<Periode>,
        førsteFraværsdager: List<FørsteFraværsdag>,
        trengerArbeidsgiverperiode: Boolean,
        erPotensiellForespørsel: Boolean
    ) {}
    fun vedtaksperiodeOpprettet(event: VedtaksperiodeOpprettet) {}
    fun vedtaksperiodePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, påminnelse: Påminnelse) {}
    fun vedtaksperiodeIkkePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, nåværendeTilstand: TilstandType) {}
    fun vedtaksperiodeEndret(event: VedtaksperiodeEndretEvent) {}
    fun vedtaksperioderVenter(eventer: List<VedtaksperiodeVenterEvent>) {}
    fun vedtaksperiodeForkastet(event: VedtaksperiodeForkastetEvent) {}
    fun vedtaksperiodeIkkeFunnet(event: VedtaksperiodeIkkeFunnetEvent) {}
    fun sykefraværstilfelleIkkeFunnet(event: SykefraværstilfelleIkkeFunnet) {}
    fun trengerArbeidsgiveropplysninger(event: TrengerArbeidsgiveropplysningerEvent) {}
    fun trengerIkkeArbeidsgiveropplysninger(event: TrengerIkkeArbeidsgiveropplysningerEvent) {}
    fun arbeidsgiveropplysningerKorrigert(event: ArbeidsgiveropplysningerKorrigertEvent) {}
    fun utbetalingEndret(event: UtbetalingEndretEvent) {}
    fun utbetalingUtbetalt(event: UtbetalingUtbetaltEvent) {}
    fun utbetalingUtenUtbetaling(event: UtbetalingUtbetaltEvent) {}
    fun feriepengerUtbetalt(event: FeriepengerUtbetaltEvent) {}
    fun annullering(event: UtbetalingAnnullertEvent) {}
    fun avsluttetMedVedtak(event: AvsluttetMedVedtakEvent) {}

    fun behandlingLukket(event: BehandlingLukketEvent) {}
    fun behandlingForkastet(event: BehandlingForkastetEvent) {}
    fun nyBehandling(event: BehandlingOpprettetEvent) {}
    fun avsluttetUtenVedtak(event: AvsluttetUtenVedtakEvent) {}
    fun nyVedtaksperiodeUtbetaling(
        organisasjonsnummer: String,
        utbetalingId: UUID,
        vedtaksperiodeId: UUID
    ) {}
    fun overstyringIgangsatt(event: OverstyringIgangsatt) {}
    fun overlappendeInfotrygdperioder(event: OverlappendeInfotrygdperioder) {}
    fun inntektsmeldingFørSøknad(event: InntektsmeldingFørSøknadEvent) {}
    fun inntektsmeldingIkkeHåndtert(inntektsmeldingId: UUID, organisasjonsnummer: String, harPeriodeInnenfor16Dager: Boolean) {}
    fun inntektsmeldingHåndtert(inntektsmeldingId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {}
    fun skatteinntekterLagtTilGrunn(event: SkatteinntekterLagtTilGrunnEvent) {}
    fun søknadHåndtert(søknadId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {}
    fun behandlingUtført() {}
    fun vedtaksperiodeAnnullert(vedtaksperiodeAnnullertEvent: VedtaksperiodeAnnullertEvent) {}
    fun utkastTilVedtak(event: UtkastTilVedtakEvent) {}
}
