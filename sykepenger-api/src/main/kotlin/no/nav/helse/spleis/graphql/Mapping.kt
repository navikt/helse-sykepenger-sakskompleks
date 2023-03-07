package no.nav.helse.spleis.graphql

import java.util.UUID
import no.nav.helse.person.Inntektskilde
import no.nav.helse.person.Periodetype
import no.nav.helse.serde.api.dto.Arbeidsgiverinntekt
import no.nav.helse.serde.api.dto.Arbeidsgiverrefusjon
import no.nav.helse.serde.api.dto.BegrunnelseDTO
import no.nav.helse.serde.api.dto.BeregnetPeriode
import no.nav.helse.serde.api.dto.HendelseDTO
import no.nav.helse.serde.api.dto.InfotrygdVilkårsgrunnlag
import no.nav.helse.serde.api.dto.Inntektkilde
import no.nav.helse.serde.api.dto.InntektsmeldingDTO
import no.nav.helse.serde.api.dto.Periodetilstand
import no.nav.helse.serde.api.dto.SammenslåttDag
import no.nav.helse.serde.api.dto.SpeilOppdrag
import no.nav.helse.serde.api.dto.SpleisVilkårsgrunnlag
import no.nav.helse.serde.api.dto.SykdomstidslinjedagKildetype
import no.nav.helse.serde.api.dto.SykdomstidslinjedagType
import no.nav.helse.serde.api.dto.SykmeldingDTO
import no.nav.helse.serde.api.dto.SøknadArbeidsgiverDTO
import no.nav.helse.serde.api.dto.SøknadNavDTO
import no.nav.helse.serde.api.dto.Tidslinjeperiode
import no.nav.helse.serde.api.dto.Utbetaling
import no.nav.helse.serde.api.dto.Utbetalingstatus
import no.nav.helse.serde.api.dto.UtbetalingstidslinjedagType
import no.nav.helse.serde.api.dto.Vilkårsgrunnlag
import no.nav.helse.serde.api.speil.builders.SykepengegrunnlagsgrenseDTO
import no.nav.helse.spleis.graphql.dto.GraphQLAktivitet
import no.nav.helse.spleis.graphql.dto.GraphQLArbeidsgiverinntekt
import no.nav.helse.spleis.graphql.dto.GraphQLArbeidsgiverrefusjon
import no.nav.helse.spleis.graphql.dto.GraphQLBegrunnelse
import no.nav.helse.spleis.graphql.dto.GraphQLBeregnetPeriode
import no.nav.helse.spleis.graphql.dto.GraphQLDag
import no.nav.helse.spleis.graphql.dto.GraphQLHendelse
import no.nav.helse.spleis.graphql.dto.GraphQLHendelsetype
import no.nav.helse.spleis.graphql.dto.GraphQLInfotrygdVilkarsgrunnlag
import no.nav.helse.spleis.graphql.dto.GraphQLInntekterFraAOrdningen
import no.nav.helse.spleis.graphql.dto.GraphQLInntektskilde
import no.nav.helse.spleis.graphql.dto.GraphQLInntektsmelding
import no.nav.helse.spleis.graphql.dto.GraphQLInntektstype
import no.nav.helse.spleis.graphql.dto.GraphQLOmregnetArsinntekt
import no.nav.helse.spleis.graphql.dto.GraphQLOppdrag
import no.nav.helse.spleis.graphql.dto.GraphQLPeriodetilstand
import no.nav.helse.spleis.graphql.dto.GraphQLPeriodetype
import no.nav.helse.spleis.graphql.dto.GraphQLPeriodevilkar
import no.nav.helse.spleis.graphql.dto.GraphQLRefusjonselement
import no.nav.helse.spleis.graphql.dto.GraphQLSammenligningsgrunnlag
import no.nav.helse.spleis.graphql.dto.GraphQLSimulering
import no.nav.helse.spleis.graphql.dto.GraphQLSimuleringsdetaljer
import no.nav.helse.spleis.graphql.dto.GraphQLSimuleringsperiode
import no.nav.helse.spleis.graphql.dto.GraphQLSimuleringsutbetaling
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadArbeidsgiver
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadNav
import no.nav.helse.spleis.graphql.dto.GraphQLSpleisVilkarsgrunnlag
import no.nav.helse.spleis.graphql.dto.GraphQLSykdomsdagkilde
import no.nav.helse.spleis.graphql.dto.GraphQLSykdomsdagkildetype
import no.nav.helse.spleis.graphql.dto.GraphQLSykdomsdagtype
import no.nav.helse.spleis.graphql.dto.GraphQLSykepengegrunnlagsgrense
import no.nav.helse.spleis.graphql.dto.GraphQLSykmelding
import no.nav.helse.spleis.graphql.dto.GraphQLUberegnetPeriode
import no.nav.helse.spleis.graphql.dto.GraphQLUtbetaling
import no.nav.helse.spleis.graphql.dto.GraphQLUtbetalingsdagType
import no.nav.helse.spleis.graphql.dto.GraphQLUtbetalingsinfo
import no.nav.helse.spleis.graphql.dto.GraphQLUtbetalingstatus
import no.nav.helse.spleis.graphql.dto.GraphQLVilkarsgrunnlag
import no.nav.helse.spleis.graphql.dto.GraphQLVilkarsgrunnlaghistorikk
import no.nav.helse.spleis.graphql.dto.GraphQLVilkarsgrunnlagtype
import no.nav.helse.spleis.graphql.dto.GraphQLVurdering
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.NaN
import kotlin.Double.Companion.POSITIVE_INFINITY

private fun mapDag(dag: SammenslåttDag) = GraphQLDag(
    dato = dag.dagen,
    sykdomsdagtype = when (dag.sykdomstidslinjedagtype) {
        SykdomstidslinjedagType.ARBEIDSDAG -> GraphQLSykdomsdagtype.Arbeidsdag
        SykdomstidslinjedagType.ARBEIDSGIVERDAG -> GraphQLSykdomsdagtype.Arbeidsgiverdag
        SykdomstidslinjedagType.FERIEDAG -> GraphQLSykdomsdagtype.Feriedag
        SykdomstidslinjedagType.FORELDET_SYKEDAG -> GraphQLSykdomsdagtype.ForeldetSykedag
        SykdomstidslinjedagType.FRISK_HELGEDAG -> GraphQLSykdomsdagtype.FriskHelgedag
        SykdomstidslinjedagType.PERMISJONSDAG -> GraphQLSykdomsdagtype.Permisjonsdag
        SykdomstidslinjedagType.SYKEDAG -> GraphQLSykdomsdagtype.Sykedag
        SykdomstidslinjedagType.SYK_HELGEDAG -> GraphQLSykdomsdagtype.SykHelgedag
        SykdomstidslinjedagType.UBESTEMTDAG -> GraphQLSykdomsdagtype.Ubestemtdag
    },
    utbetalingsdagtype = when (dag.utbetalingstidslinjedagtype) {
        UtbetalingstidslinjedagType.ArbeidsgiverperiodeDag -> GraphQLUtbetalingsdagType.ArbeidsgiverperiodeDag
        UtbetalingstidslinjedagType.NavDag -> GraphQLUtbetalingsdagType.NavDag
        UtbetalingstidslinjedagType.NavHelgDag -> GraphQLUtbetalingsdagType.NavHelgDag
        UtbetalingstidslinjedagType.Helgedag -> GraphQLUtbetalingsdagType.Helgedag
        UtbetalingstidslinjedagType.Arbeidsdag -> GraphQLUtbetalingsdagType.Arbeidsdag
        UtbetalingstidslinjedagType.Feriedag -> GraphQLUtbetalingsdagType.Feriedag
        UtbetalingstidslinjedagType.AvvistDag -> GraphQLUtbetalingsdagType.AvvistDag
        UtbetalingstidslinjedagType.UkjentDag -> GraphQLUtbetalingsdagType.UkjentDag
        UtbetalingstidslinjedagType.ForeldetDag -> GraphQLUtbetalingsdagType.ForeldetDag
    },
    kilde = GraphQLSykdomsdagkilde(
        id = dag.kilde.id,
        type = when (dag.kilde.type) {
            SykdomstidslinjedagKildetype.Inntektsmelding -> GraphQLSykdomsdagkildetype.Inntektsmelding
            SykdomstidslinjedagKildetype.Søknad -> GraphQLSykdomsdagkildetype.Soknad
            SykdomstidslinjedagKildetype.Sykmelding -> GraphQLSykdomsdagkildetype.Sykmelding
            SykdomstidslinjedagKildetype.Saksbehandler -> GraphQLSykdomsdagkildetype.Saksbehandler
            SykdomstidslinjedagKildetype.Ukjent -> GraphQLSykdomsdagkildetype.Ukjent
        }
    ),
    grad = dag.grad,
    utbetalingsinfo = dag.utbetalingsinfo?.let {
        GraphQLUtbetalingsinfo(
            inntekt = it.inntekt,
            utbetaling = it.utbetaling,
            personbelop = it.personbeløp,
            arbeidsgiverbelop = it.arbeidsgiverbeløp,
            refusjonsbelop = it.refusjonsbeløp,
            totalGrad = it.totalGrad
        )
    },
    begrunnelser = dag.begrunnelser?.map {
        when (it) {
            BegrunnelseDTO.SykepengedagerOppbrukt -> GraphQLBegrunnelse.SykepengedagerOppbrukt
            BegrunnelseDTO.SykepengedagerOppbruktOver67 -> GraphQLBegrunnelse.SykepengedagerOppbruktOver67
            BegrunnelseDTO.MinimumInntekt -> GraphQLBegrunnelse.MinimumInntekt
            BegrunnelseDTO.MinimumInntektOver67 -> GraphQLBegrunnelse.MinimumInntektOver67
            BegrunnelseDTO.EgenmeldingUtenforArbeidsgiverperiode -> GraphQLBegrunnelse.EgenmeldingUtenforArbeidsgiverperiode
            BegrunnelseDTO.MinimumSykdomsgrad -> GraphQLBegrunnelse.MinimumSykdomsgrad
            BegrunnelseDTO.EtterDødsdato -> GraphQLBegrunnelse.EtterDodsdato
            BegrunnelseDTO.ManglerMedlemskap -> GraphQLBegrunnelse.ManglerMedlemskap
            BegrunnelseDTO.ManglerOpptjening -> GraphQLBegrunnelse.ManglerOpptjening
            BegrunnelseDTO.Over70 -> GraphQLBegrunnelse.Over70
        }
    }
)

private fun mapOppdrag(oppdrag: SpeilOppdrag): GraphQLOppdrag =
    GraphQLOppdrag(
        fagsystemId = oppdrag.fagsystemId,
        tidsstempel = oppdrag.tidsstempel,
        simulering = oppdrag.simulering?.let { simulering ->
            GraphQLSimulering(
                totalbelop = simulering.totalbeløp,
                perioder = simulering.perioder.map { periode ->
                    GraphQLSimuleringsperiode(
                        fom = periode.fom,
                        tom = periode.tom,
                        utbetalinger = periode.utbetalinger.map { utbetaling ->
                            GraphQLSimuleringsutbetaling(
                                utbetalesTilId = utbetaling.mottakerId,
                                utbetalesTilNavn = utbetaling.mottakerNavn,
                                forfall = utbetaling.forfall,
                                feilkonto = utbetaling.feilkonto,
                                detaljer = utbetaling.detaljer.map {
                                    GraphQLSimuleringsdetaljer(
                                        faktiskFom = it.faktiskFom,
                                        faktiskTom = it.faktiskTom,
                                        konto = it.konto,
                                        belop = it.beløp,
                                        tilbakeforing = it.tilbakeføring,
                                        sats = it.sats,
                                        typeSats = it.typeSats,
                                        antallSats = it.antallSats,
                                        uforegrad = it.uføregrad,
                                        klassekode = it.klassekode,
                                        klassekodeBeskrivelse = it.klassekodeBeskrivelse,
                                        utbetalingstype = it.utbetalingstype,
                                        refunderesOrgNr = it.refunderesOrgNr
                                    )
                                }
                            )
                        }
                    )
                }
            )
        },
        utbetalingslinjer = emptyList()
    )

private fun mapUtbetaling(utbetaling: Utbetaling) = GraphQLUtbetaling(
    id = utbetaling.id,
    type = utbetaling.type.toString(),
    typeEnum = utbetaling.type,
    status = utbetaling.status.toString(),
    statusEnum = when (utbetaling.status) {
        Utbetalingstatus.Annullert -> GraphQLUtbetalingstatus.Annullert
        Utbetalingstatus.Forkastet -> GraphQLUtbetalingstatus.Forkastet
        Utbetalingstatus.Godkjent -> GraphQLUtbetalingstatus.Godkjent
        Utbetalingstatus.GodkjentUtenUtbetaling -> GraphQLUtbetalingstatus.GodkjentUtenUtbetaling
        Utbetalingstatus.IkkeGodkjent -> GraphQLUtbetalingstatus.IkkeGodkjent
        Utbetalingstatus.Overført -> GraphQLUtbetalingstatus.Overfort
        Utbetalingstatus.IkkeUtbetalt -> GraphQLUtbetalingstatus.Ubetalt
        Utbetalingstatus.Utbetalt -> GraphQLUtbetalingstatus.Utbetalt
    },
    arbeidsgiverNettoBelop = utbetaling.arbeidsgiverNettoBeløp,
    personNettoBelop = utbetaling.personNettoBeløp,
    arbeidsgiverFagsystemId = utbetaling.arbeidsgiverFagsystemId,
    personFagsystemId = utbetaling.personFagsystemId,
    vurdering = utbetaling.vurdering?.let {
        GraphQLVurdering(
            godkjent = it.godkjent,
            tidsstempel = it.tidsstempel,
            automatisk = it.automatisk,
            ident = it.ident
        )
    },
    arbeidsgiveroppdrag = utbetaling.oppdrag[utbetaling.arbeidsgiverFagsystemId]?.let {
        mapOppdrag(it)
    },
    personoppdrag = utbetaling.oppdrag[utbetaling.personFagsystemId]?.let {
        mapOppdrag(it)
    }
)

private fun mapHendelse(hendelse: HendelseDTO) = when (hendelse) {
    is InntektsmeldingDTO -> GraphQLInntektsmelding(
        id = hendelse.id,
        mottattDato = hendelse.mottattDato,
        beregnetInntekt = hendelse.beregnetInntekt
    )
    is SøknadNavDTO -> GraphQLSoknadNav(
        id = hendelse.id,
        fom = hendelse.fom,
        tom = hendelse.tom,
        rapportertDato = hendelse.rapportertdato,
        sendtNav = hendelse.sendtNav
    )
    is SøknadArbeidsgiverDTO -> GraphQLSoknadArbeidsgiver(
        id = hendelse.id,
        fom = hendelse.fom,
        tom = hendelse.tom,
        rapportertDato = hendelse.rapportertdato,
        sendtArbeidsgiver = hendelse.sendtArbeidsgiver
    )
    is SykmeldingDTO -> GraphQLSykmelding(
        id = hendelse.id,
        fom = hendelse.fom,
        tom = hendelse.tom,
        rapportertDato = hendelse.rapportertdato
    )
    else -> object : GraphQLHendelse {
        override val id = hendelse.id
        override val type = GraphQLHendelsetype.Ukjent
    }
}

private fun mapPeriodevilkår(vilkår: BeregnetPeriode.Vilkår) = GraphQLPeriodevilkar(
    sykepengedager = vilkår.sykepengedager.let {
        GraphQLPeriodevilkar.Sykepengedager(
            skjaeringstidspunkt = it.skjæringstidspunkt,
            maksdato = it.maksdato,
            forbrukteSykedager = it.forbrukteSykedager,
            gjenstaendeSykedager = it.gjenståendeDager,
            oppfylt = it.oppfylt
        )
    },
    alder = vilkår.alder.let {
        GraphQLPeriodevilkar.Alder(
            alderSisteSykedag = it.alderSisteSykedag,
            oppfylt = it.oppfylt
        )
    },
    soknadsfrist = vilkår.søknadsfrist?.let {
        GraphQLPeriodevilkar.Soknadsfrist(
            sendtNav = it.sendtNav,
            soknadFom = it.søknadFom,
            soknadTom = it.søknadTom,
            oppfylt = it.oppfylt
        )
    }
)

private fun mapPeriodetype(type: Periodetype) = when (type) {
    Periodetype.FØRSTEGANGSBEHANDLING -> GraphQLPeriodetype.Forstegangsbehandling
    Periodetype.FORLENGELSE -> GraphQLPeriodetype.Forlengelse
    Periodetype.OVERGANG_FRA_IT -> GraphQLPeriodetype.OvergangFraIt
    Periodetype.INFOTRYGDFORLENGELSE -> GraphQLPeriodetype.Infotrygdforlengelse
}

private fun mapInntektstype(kilde: Inntektskilde) = when (kilde) {
    Inntektskilde.EN_ARBEIDSGIVER -> GraphQLInntektstype.EnArbeidsgiver
    Inntektskilde.FLERE_ARBEIDSGIVERE -> GraphQLInntektstype.FlereArbeidsgivere
}

internal fun mapTidslinjeperiode(periode: Tidslinjeperiode) =
    when (periode) {
        is BeregnetPeriode -> GraphQLBeregnetPeriode(
            fom = periode.fom,
            tom = periode.tom,
            tidslinje = periode.sammenslåttTidslinje.map { mapDag(it) },
            periodetype = mapPeriodetype(periode.periodetype),
            inntektstype = mapInntektstype(periode.inntektskilde),
            erForkastet = periode.erForkastet,
            opprettet = periode.opprettet,
            vedtaksperiodeId = periode.vedtaksperiodeId,
            beregningId = periode.beregningId,
            gjenstaendeSykedager = periode.gjenståendeSykedager,
            forbrukteSykedager = periode.forbrukteSykedager,
            skjaeringstidspunkt = periode.skjæringstidspunkt,
            maksdato = periode.maksdato,
            utbetaling = mapUtbetaling(periode.utbetaling),
            hendelser = periode.hendelser.map { mapHendelse(it) },
            periodevilkar = mapPeriodevilkår(periode.periodevilkår),
            aktivitetslogg = periode.aktivitetslogg.map {
                GraphQLAktivitet(
                    vedtaksperiodeId = it.vedtaksperiodeId,
                    alvorlighetsgrad = it.alvorlighetsgrad,
                    melding = it.melding,
                    tidsstempel = it.tidsstempel
                )
            },
            periodetilstand = mapTilstand(periode.periodetilstand),
            vilkarsgrunnlagId = periode.vilkårsgrunnlagId
        )
        else -> GraphQLUberegnetPeriode(
            fom = periode.fom,
            tom = periode.tom,
            tidslinje = periode.sammenslåttTidslinje.map { mapDag(it) },
            periodetype = mapPeriodetype(periode.periodetype),
            inntektstype = mapInntektstype(periode.inntektskilde),
            erForkastet = periode.erForkastet,
            opprettet = periode.opprettet,
            vedtaksperiodeId = periode.vedtaksperiodeId,
            periodetilstand = mapTilstand(periode.periodetilstand),
            skjaeringstidspunkt = periode.skjæringstidspunkt
        )
    }

private fun mapTilstand(tilstand: Periodetilstand) = when (tilstand) {
    Periodetilstand.TilUtbetaling -> GraphQLPeriodetilstand.TilUtbetaling
    Periodetilstand.TilAnnullering -> GraphQLPeriodetilstand.TilAnnullering
    Periodetilstand.Utbetalt -> GraphQLPeriodetilstand.Utbetalt
    Periodetilstand.Annullert -> GraphQLPeriodetilstand.Annullert
    Periodetilstand.AnnulleringFeilet -> GraphQLPeriodetilstand.AnnulleringFeilet
    Periodetilstand.IngenUtbetaling -> GraphQLPeriodetilstand.IngenUtbetaling
    Periodetilstand.RevurderingFeilet -> GraphQLPeriodetilstand.RevurderingFeilet
    Periodetilstand.TilInfotrygd -> GraphQLPeriodetilstand.TilInfotrygd
    Periodetilstand.ForberederGodkjenning -> GraphQLPeriodetilstand.ForberederGodkjenning
    Periodetilstand.ManglerInformasjon -> GraphQLPeriodetilstand.ManglerInformasjon
    Periodetilstand.VenterPåAnnenPeriode -> GraphQLPeriodetilstand.VenterPaAnnenPeriode
    Periodetilstand.TilGodkjenning -> GraphQLPeriodetilstand.TilGodkjenning
    Periodetilstand.UtbetaltVenterPåAnnenPeriode -> GraphQLPeriodetilstand.UtbetaltVenterPaAnnenPeriode
}

private fun mapArbeidsgiverRefusjon(arbeidsgiverrefusjon: Arbeidsgiverrefusjon) = GraphQLArbeidsgiverrefusjon(
    arbeidsgiver = arbeidsgiverrefusjon.arbeidsgiver,
    refusjonsopplysninger = arbeidsgiverrefusjon.refusjonsopplysninger.map {
        GraphQLRefusjonselement(
            fom = it.fom,
            tom = it.tom,
            belop = it.beløp,
            meldingsreferanseId = it.meldingsreferanseId
        )
    }
)

private fun mapInntekt(inntekt: Arbeidsgiverinntekt) = GraphQLArbeidsgiverinntekt(
    arbeidsgiver = inntekt.organisasjonsnummer,
    omregnetArsinntekt = inntekt.omregnetÅrsinntekt?.let { omregnetÅrsinntekt ->
        GraphQLOmregnetArsinntekt(
            kilde = when (omregnetÅrsinntekt.kilde) {
                Inntektkilde.Saksbehandler -> GraphQLInntektskilde.Saksbehandler
                Inntektkilde.Inntektsmelding -> GraphQLInntektskilde.Inntektsmelding
                Inntektkilde.Infotrygd -> GraphQLInntektskilde.Infotrygd
                Inntektkilde.AOrdningen -> GraphQLInntektskilde.AOrdningen
                Inntektkilde.IkkeRapportert -> GraphQLInntektskilde.IkkeRapportert
            },
            belop = omregnetÅrsinntekt.beløp,
            manedsbelop = omregnetÅrsinntekt.månedsbeløp,
            inntekterFraAOrdningen = omregnetÅrsinntekt.inntekterFraAOrdningen?.map {
                GraphQLInntekterFraAOrdningen(
                    maned = it.måned,
                    sum = it.sum
                )
            }
        )
    },
    sammenligningsgrunnlag = inntekt.sammenligningsgrunnlag?.let {
        GraphQLSammenligningsgrunnlag(
            belop = it,
            inntekterFraAOrdningen = emptyList()
        )
    },
    deaktivert = inntekt.deaktivert
)

private fun Double?.mapAvviksprosent() =
    if (this in setOf(POSITIVE_INFINITY, NEGATIVE_INFINITY, NaN)) 100.0 else this

internal fun mapVilkårsgrunnlagHistorikk(id: UUID, vilkårsgrunnlag: List<Vilkårsgrunnlag>) =
    GraphQLVilkarsgrunnlaghistorikk(
        id = id,
        grunnlag = vilkårsgrunnlag.map { grunnlag ->
            mapVilkårsgrunnlag(id, grunnlag)
        }
    )

internal fun mapVilkårsgrunnlag(id: UUID, vilkårsgrunnlag: Vilkårsgrunnlag) =
        when (vilkårsgrunnlag) {
            is SpleisVilkårsgrunnlag -> GraphQLSpleisVilkarsgrunnlag(
                id = id,
                skjaeringstidspunkt = vilkårsgrunnlag.skjæringstidspunkt,
                omregnetArsinntekt = vilkårsgrunnlag.omregnetÅrsinntekt,
                sammenligningsgrunnlag = vilkårsgrunnlag.sammenligningsgrunnlag,
                sykepengegrunnlag = vilkårsgrunnlag.sykepengegrunnlag,
                inntekter = vilkårsgrunnlag.inntekter.map { inntekt -> mapInntekt(inntekt) },
                avviksprosent = vilkårsgrunnlag.avviksprosent.mapAvviksprosent(),
                grunnbelop = vilkårsgrunnlag.grunnbeløp,
                sykepengegrunnlagsgrense = mapSykepengergrunnlagsgrense(vilkårsgrunnlag.sykepengegrunnlagsgrense),
                antallOpptjeningsdagerErMinst = vilkårsgrunnlag.antallOpptjeningsdagerErMinst,
                opptjeningFra = vilkårsgrunnlag.opptjeningFra,
                oppfyllerKravOmMinstelonn = vilkårsgrunnlag.oppfyllerKravOmMinstelønn,
                oppfyllerKravOmOpptjening = vilkårsgrunnlag.oppfyllerKravOmOpptjening,
                oppfyllerKravOmMedlemskap = vilkårsgrunnlag.oppfyllerKravOmMedlemskap,
                arbeidsgiverrefusjoner = vilkårsgrunnlag.arbeidsgiverrefusjoner.map{ refusjon -> mapArbeidsgiverRefusjon(refusjon)}
            )
            is InfotrygdVilkårsgrunnlag -> GraphQLInfotrygdVilkarsgrunnlag(
                id = id,
                skjaeringstidspunkt = vilkårsgrunnlag.skjæringstidspunkt,
                omregnetArsinntekt = vilkårsgrunnlag.omregnetÅrsinntekt,
                sammenligningsgrunnlag = vilkårsgrunnlag.sammenligningsgrunnlag,
                sykepengegrunnlag = vilkårsgrunnlag.sykepengegrunnlag,
                inntekter = vilkårsgrunnlag.inntekter.map { inntekt -> mapInntekt(inntekt) },
                arbeidsgiverrefusjoner = vilkårsgrunnlag.arbeidsgiverrefusjoner.map{ refusjon -> mapArbeidsgiverRefusjon(refusjon)}
            )
            else -> object : GraphQLVilkarsgrunnlag {
                override val id = id
                override val skjaeringstidspunkt = vilkårsgrunnlag.skjæringstidspunkt
                override val omregnetArsinntekt = vilkårsgrunnlag.omregnetÅrsinntekt
                override val sammenligningsgrunnlag = vilkårsgrunnlag.sammenligningsgrunnlag
                override val sykepengegrunnlag = vilkårsgrunnlag.sykepengegrunnlag
                override val inntekter = vilkårsgrunnlag.inntekter.map { inntekt -> mapInntekt(inntekt) }
                override val arbeidsgiverrefusjoner = vilkårsgrunnlag.arbeidsgiverrefusjoner.map{ refusjon -> mapArbeidsgiverRefusjon(refusjon)}
                override val vilkarsgrunnlagtype = GraphQLVilkarsgrunnlagtype.Ukjent
            }
        }


private fun mapSykepengergrunnlagsgrense(sykepengegrunnlagsgrenseDTO: SykepengegrunnlagsgrenseDTO) =
    GraphQLSykepengegrunnlagsgrense(sykepengegrunnlagsgrenseDTO.grunnbeløp, sykepengegrunnlagsgrenseDTO.grense, sykepengegrunnlagsgrenseDTO.virkningstidspunkt)