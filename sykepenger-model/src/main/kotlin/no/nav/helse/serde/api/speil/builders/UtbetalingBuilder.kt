package no.nav.helse.serde.api.speil.builders

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.person.Opptjening
import no.nav.helse.utbetalingslinjer.UtbetalingVisitor
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VedtaksperiodeUtbetalingVisitor
import no.nav.helse.person.VedtaksperiodeVisitor
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.serde.api.dto.EndringskodeDTO.Companion.dto
import no.nav.helse.serde.api.dto.SpeilOppdrag
import no.nav.helse.serde.api.dto.Utbetaling
import no.nav.helse.serde.api.speil.IUtbetaling
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.utbetalingslinjer.Klassekode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Satstype
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.FORKASTET
import no.nav.helse.økonomi.Prosent
import org.slf4j.LoggerFactory
import no.nav.helse.utbetalingslinjer.Utbetaling as InternUtbetaling

// Besøker hele vedtaksperiode-treet
internal class UtbetalingerBuilder(
    vedtaksperiode: Vedtaksperiode,
    private val vedtaksperiodetilstand: Vedtaksperiode.Vedtaksperiodetilstand
) : VedtaksperiodeVisitor {
    val utbetalinger = mutableMapOf<UUID, IUtbetaling>()
    val vilkårsgrunnlag = mutableMapOf<UUID, IVilkårsgrunnlag>()

    private companion object {
        private val sikkerlog = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        vedtaksperiode.accept(this)
    }

    internal fun build(vedtaksperiodeId: UUID): List<Pair<IVilkårsgrunnlag?, IUtbetaling>>{
        val vilkårsgrunnlagTilUtbetaling = mutableListOf<Pair<IVilkårsgrunnlag?, IUtbetaling>>()
        utbetalinger.forEach { (utbetalingId, utbetaling) ->
            if (utbetalingId in vilkårsgrunnlag.keys) {
                vilkårsgrunnlagTilUtbetaling.add(vilkårsgrunnlag[utbetalingId] to utbetaling)
            } else {
                sikkerlog.error("Fant ikke vilkårsgrunnlag for utbetaling med utbetalingId=$utbetalingId for vedtaksperiodeId=$vedtaksperiodeId")
                vilkårsgrunnlagTilUtbetaling.add(null to utbetaling)
            }
        }
        return vilkårsgrunnlagTilUtbetaling.toList()
    }

    override fun preVisitVedtaksperiodeUtbetaling(
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
        utbetaling: no.nav.helse.utbetalingslinjer.Utbetaling
    ) {
        val utbetalingIDTilVilkårsgrunnlag = VedtaksperiodeUtbetalingVilkårsgrunnlagBuilder(grunnlagsdata, utbetaling).build()
        vilkårsgrunnlag[utbetalingIDTilVilkårsgrunnlag.first] = utbetalingIDTilVilkårsgrunnlag.second
    }

    internal class VedtaksperiodeUtbetalingVilkårsgrunnlagBuilder(grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement, utbetaling: InternUtbetaling): VedtaksperiodeUtbetalingVisitor {
        private lateinit var utbetalingId: UUID
        private lateinit var vilkårsgrunnlag: IVilkårsgrunnlag
        init {
            grunnlagsdata.accept(this)
            utbetaling.accept(this)
        }
        internal fun build() = utbetalingId to vilkårsgrunnlag

        override fun preVisitUtbetaling(
            utbetaling: no.nav.helse.utbetalingslinjer.Utbetaling,
            id: UUID,
            korrelasjonsId: UUID,
            type: Utbetalingtype,
            utbetalingstatus: Utbetalingstatus,
            periode: Periode,
            tidsstempel: LocalDateTime,
            oppdatert: LocalDateTime,
            arbeidsgiverNettoBeløp: Int,
            personNettoBeløp: Int,
            maksdato: LocalDate,
            forbrukteSykedager: Int?,
            gjenståendeSykedager: Int?,
            stønadsdager: Int,
            beregningId: UUID,
            overføringstidspunkt: LocalDateTime?,
            avsluttet: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            annulleringer: Set<UUID>
        ) {
            utbetalingId = id

        }

        override fun preVisitGrunnlagsdata(
            skjæringstidspunkt: LocalDate,
            grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
            sykepengegrunnlag: Sykepengegrunnlag,
            sammenligningsgrunnlag: Sammenligningsgrunnlag,
            avviksprosent: Prosent?,
            opptjening: Opptjening,
            vurdertOk: Boolean,
            meldingsreferanseId: UUID?,
            vilkårsgrunnlagId: UUID,
            medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus
        ) {
            val sammenligningsgrunnlagBuilder =
                VilkårsgrunnlagBuilder.InnslagBuilder.SammenligningsgrunnlagBuilder(sammenligningsgrunnlag)

            val compositeSykepengegrunnlag = VilkårsgrunnlagBuilder.InnslagBuilder.SykepengegrunnlagBuilder(
                sykepengegrunnlag,
                sammenligningsgrunnlagBuilder
            ).build()
            val oppfyllerKravOmMedlemskap = when (medlemskapstatus) {
                Medlemskapsvurdering.Medlemskapstatus.Ja -> true
                Medlemskapsvurdering.Medlemskapstatus.Nei -> false
                else -> null
            }

            vilkårsgrunnlag = ISpleisGrunnlag(
                skjæringstidspunkt = skjæringstidspunkt,
                omregnetÅrsinntekt = compositeSykepengegrunnlag.omregnetÅrsinntekt,
                sammenligningsgrunnlag = sammenligningsgrunnlagBuilder.total(),
                inntekter = compositeSykepengegrunnlag.inntekterPerArbeidsgiver,
                refusjonsopplysningerPerArbeidsgiver = compositeSykepengegrunnlag.refusjonsopplysningerPerArbeidsgiver,
                sykepengegrunnlag = compositeSykepengegrunnlag.sykepengegrunnlag,
                avviksprosent = avviksprosent?.prosent(),
                grunnbeløp = compositeSykepengegrunnlag.begrensning.grunnbeløp,
                sykepengegrunnlagsgrense = compositeSykepengegrunnlag.begrensning,
                meldingsreferanseId = meldingsreferanseId,
                antallOpptjeningsdagerErMinst = opptjening.opptjeningsdager(),
                oppfyllerKravOmMinstelønn = compositeSykepengegrunnlag.oppfyllerMinsteinntektskrav,
                oppfyllerKravOmOpptjening = opptjening.erOppfylt(),
                oppfyllerKravOmMedlemskap = oppfyllerKravOmMedlemskap,
                id = vilkårsgrunnlagId
            )
        }

        override fun preVisitInfotrygdVilkårsgrunnlag(
            infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: Sykepengegrunnlag,
            vilkårsgrunnlagId: UUID
        ) {
            val byggetSykepengegrunnlag = VilkårsgrunnlagBuilder.InnslagBuilder.SykepengegrunnlagBuilder(
                sykepengegrunnlag,
                null /* vi har ikke noe sammenligningsgrunnlag for Infotrygd-saker */
            )
                .build()
            vilkårsgrunnlag = IInfotrygdGrunnlag(
                skjæringstidspunkt = skjæringstidspunkt,
                omregnetÅrsinntekt = byggetSykepengegrunnlag.omregnetÅrsinntekt,
                sammenligningsgrunnlag = null,
                inntekter = byggetSykepengegrunnlag.inntekterPerArbeidsgiver,
                refusjonsopplysningerPerArbeidsgiver = byggetSykepengegrunnlag.refusjonsopplysningerPerArbeidsgiver,
                sykepengegrunnlag = byggetSykepengegrunnlag.sykepengegrunnlag,
                id = vilkårsgrunnlagId
            )
        }
    }

    override fun preVisitUtbetaling(
        utbetaling: no.nav.helse.utbetalingslinjer.Utbetaling,
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        utbetalingstatus: Utbetalingstatus,
        periode: Periode,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        stønadsdager: Int,
        beregningId: UUID,
        overføringstidspunkt: LocalDateTime?,
        avsluttet: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        annulleringer: Set<UUID>
    ) {
        if (utbetalingstatus == FORKASTET && vedtaksperiodetilstand != Vedtaksperiode.RevurderingFeilet) return
        utbetalinger.entries.find { it.value.forkastet() }?.let { utbetalinger.remove(it.key) }
        utbetalinger.putIfAbsent(id, UtbetalingBuilder(utbetaling).build())
    }
}

internal class UtbetalingBuilder(utbetaling: InternUtbetaling) : UtbetalingVisitor {
    private lateinit var utbetaling: IUtbetaling

    init {
        utbetaling.accept(this)
    }

    internal fun build() = utbetaling

    override fun preVisitUtbetaling(
        utbetaling: no.nav.helse.utbetalingslinjer.Utbetaling,
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        utbetalingstatus: Utbetalingstatus,
        periode: Periode,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        stønadsdager: Int,
        beregningId: UUID,
        overføringstidspunkt: LocalDateTime?,
        avsluttet: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        annulleringer: Set<UUID>
    ) {
        val tidslinje = UtbetalingstidslinjeBuilder(utbetaling).build()
        val vurdering = VurderingBuilder(utbetaling).build()
        val oppdragBuilder = OppdragBuilder(utbetaling)
        this.utbetaling = IUtbetaling(
            id = id,
            korrelasjonsId = korrelasjonsId,
            beregningId = beregningId,
            opprettet = tidsstempel,
            utbetalingstidslinje = tidslinje,
            maksdato = maksdato,
            gjenståendeSykedager = gjenståendeSykedager,
            forbrukteSykedager = forbrukteSykedager,
            type = type.toString(),
            tilstand = utbetalingstatus,
            arbeidsgiverNettoBeløp = arbeidsgiverNettoBeløp,
            personNettoBeløp = personNettoBeløp,
            arbeidsgiverFagsystemId = oppdragBuilder.arbeidsgiverFagsystemId(),
            personFagsystemId = oppdragBuilder.personFagsystemId(),
            vurdering = vurdering,
            oppdrag = oppdragBuilder.oppdrag()
        )
    }
}

// Besøker hele utbetaling-treet
internal class VurderingBuilder(utbetaling: InternUtbetaling) : UtbetalingVisitor {
    init {
        utbetaling.accept(this)
    }

    private var vurdering: Utbetaling.Vurdering? = null
    internal fun build() = vurdering

    override fun visitVurdering(
        vurdering: InternUtbetaling.Vurdering,
        ident: String,
        epost: String,
        tidspunkt: LocalDateTime,
        automatiskBehandling: Boolean,
        godkjent: Boolean
    ) {
        this.vurdering = Utbetaling.Vurdering(
            godkjent = godkjent,
            tidsstempel = tidspunkt,
            automatisk = automatiskBehandling,
            ident = ident
        )
    }
}

// Besøker hele utbetaling-treet
internal class OppdragBuilder(utbetaling: InternUtbetaling) : UtbetalingVisitor {
    private val speilOppdrag: MutableMap<String, SpeilOppdrag> = mutableMapOf()

    init {
        utbetaling.accept(this)
    }

    private lateinit var arbeidsgiverFagsystemId: String
    private lateinit var personFagsystemId: String


    private var linjer = mutableListOf<SpeilOppdrag.Utbetalingslinje>()

    internal fun arbeidsgiverFagsystemId() = arbeidsgiverFagsystemId

    internal fun personFagsystemId() = personFagsystemId

    internal fun oppdrag() = speilOppdrag

    override fun preVisitArbeidsgiverOppdrag(oppdrag: Oppdrag) {
        arbeidsgiverFagsystemId = oppdrag.fagsystemId()
    }

    override fun preVisitPersonOppdrag(oppdrag: Oppdrag) {
        personFagsystemId = oppdrag.fagsystemId()
    }

    override fun visitUtbetalingslinje(
        linje: Utbetalingslinje,
        fom: LocalDate,
        tom: LocalDate,
        stønadsdager: Int,
        totalbeløp: Int,
        satstype: Satstype,
        beløp: Int?,
        aktuellDagsinntekt: Int?,
        grad: Int?,
        delytelseId: Int,
        refDelytelseId: Int?,
        refFagsystemId: String?,
        endringskode: Endringskode,
        datoStatusFom: LocalDate?,
        statuskode: String?,
        klassekode: Klassekode
    ) {
        if (beløp == null || grad == null) return
        linjer.add(
            SpeilOppdrag.Utbetalingslinje(
                fom = fom,
                tom = tom,
                dagsats = beløp,
                grad = grad,
                endringskode = endringskode.dto()
            )
        )
    }

    override fun preVisitOppdrag(
        oppdrag: Oppdrag,
        fagområde: Fagområde,
        fagsystemId: String,
        mottaker: String,
        sisteArbeidsgiverdag: LocalDate?,
        stønadsdager: Int,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime,
        endringskode: Endringskode,
        avstemmingsnøkkel: Long?,
        status: Oppdragstatus?,
        overføringstidspunkt: LocalDateTime?,
        erSimulert: Boolean,
        simuleringsResultat: SimuleringResultat?
    ) {
        linjer = mutableListOf()
    }

    override fun postVisitOppdrag(
        oppdrag: Oppdrag,
        fagområde: Fagområde,
        fagsystemId: String,
        mottaker: String,
        sisteArbeidsgiverdag: LocalDate?,
        stønadsdager: Int,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime,
        endringskode: Endringskode,
        avstemmingsnøkkel: Long?,
        status: Oppdragstatus?,
        overføringstidspunkt: LocalDateTime?,
        erSimulert: Boolean,
        simuleringsResultat: SimuleringResultat?
    ) {
        speilOppdrag.putIfAbsent(
            fagsystemId,
            SpeilOppdrag(
                fagsystemId = fagsystemId,
                tidsstempel = tidsstempel,
                simulering = simuleringsResultat?.let { simulering ->
                    SpeilOppdrag.Simulering(
                        totalbeløp = simulering.totalbeløp,
                        perioder = simulering.perioder.map { periode ->
                            SpeilOppdrag.Simuleringsperiode(
                                fom = periode.periode.start,
                                tom = periode.periode.endInclusive,
                                utbetalinger = periode.utbetalinger.map { utbetaling ->
                                    SpeilOppdrag.Simuleringsutbetaling(
                                        mottakerId = utbetaling.utbetalesTil.id,
                                        mottakerNavn = utbetaling.utbetalesTil.navn,
                                        forfall = utbetaling.forfallsdato,
                                        feilkonto = utbetaling.feilkonto,
                                        detaljer = utbetaling.detaljer.map {
                                            SpeilOppdrag.Simuleringsdetaljer(
                                                faktiskFom = it.periode.start,
                                                faktiskTom = it.periode.endInclusive,
                                                konto = it.konto,
                                                beløp = it.beløp,
                                                tilbakeføring = it.tilbakeføring,
                                                sats = it.sats.sats,
                                                typeSats = it.sats.type,
                                                antallSats = it.sats.antall,
                                                uføregrad = it.uføregrad,
                                                klassekode = it.klassekode.kode,
                                                klassekodeBeskrivelse = it.klassekode.beskrivelse,
                                                utbetalingstype = it.utbetalingstype,
                                                refunderesOrgNr = it.refunderesOrgnummer
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                },
                utbetalingslinjer = linjer
            )
        )
    }
}
