package no.nav.helse.spleis.graphql

import no.nav.helse.spleis.graphql.dto.Utbetalingtype as GraphQLUtbetalingtype
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.spleis.dto.HendelseDTO
import no.nav.helse.spleis.dto.HendelsetypeDto
import no.nav.helse.spleis.graphql.dto.GraphQLArbeidsgiverinntekt
import no.nav.helse.spleis.graphql.dto.GraphQLArbeidsgiverrefusjon
import no.nav.helse.spleis.graphql.dto.GraphQLBegrunnelse
import no.nav.helse.spleis.graphql.dto.GraphQLBeregnetPeriode
import no.nav.helse.spleis.graphql.dto.GraphQLDag
import no.nav.helse.spleis.graphql.dto.GraphQLHendelse
import no.nav.helse.spleis.graphql.dto.GraphQLInfotrygdVilkarsgrunnlag
import no.nav.helse.spleis.graphql.dto.GraphQLInntektFraAOrdningen
import no.nav.helse.spleis.graphql.dto.GraphQLInntektskilde
import no.nav.helse.spleis.graphql.dto.GraphQLInntektsmelding
import no.nav.helse.spleis.graphql.dto.GraphQLInntektstype
import no.nav.helse.spleis.graphql.dto.GraphQLOmregnetArsinntekt
import no.nav.helse.spleis.graphql.dto.GraphQLOppdrag
import no.nav.helse.spleis.graphql.dto.GraphQLPeriodetilstand
import no.nav.helse.spleis.graphql.dto.GraphQLPeriodetype
import no.nav.helse.spleis.graphql.dto.GraphQLPeriodevilkar
import no.nav.helse.spleis.graphql.dto.GraphQLRefusjonselement
import no.nav.helse.spleis.graphql.dto.GraphQLSimulering
import no.nav.helse.spleis.graphql.dto.GraphQLSimuleringsdetaljer
import no.nav.helse.spleis.graphql.dto.GraphQLSimuleringsperiode
import no.nav.helse.spleis.graphql.dto.GraphQLSimuleringsutbetaling
import no.nav.helse.spleis.graphql.dto.GraphQLSkjonnsmessigFastsatt
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadArbeidsgiver
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadArbeidsledig
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadFrilans
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadNav
import no.nav.helse.spleis.graphql.dto.GraphQLSoknadSelvstendig
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
import no.nav.helse.spleis.graphql.dto.GraphQLVurdering
import no.nav.helse.spleis.speil.builders.SykepengegrunnlagsgrenseDTO
import no.nav.helse.spleis.speil.dto.AnnullertPeriode
import no.nav.helse.spleis.speil.dto.Arbeidsgiverinntekt
import no.nav.helse.spleis.speil.dto.Arbeidsgiverrefusjon
import no.nav.helse.spleis.speil.dto.BegrunnelseDTO
import no.nav.helse.spleis.speil.dto.BeregnetPeriode
import no.nav.helse.spleis.speil.dto.InfotrygdVilkårsgrunnlag
import no.nav.helse.spleis.speil.dto.Inntekt
import no.nav.helse.spleis.speil.dto.Inntektkilde
import no.nav.helse.spleis.speil.dto.Periodetilstand
import no.nav.helse.spleis.speil.dto.SammenslåttDag
import no.nav.helse.spleis.speil.dto.SpeilOppdrag
import no.nav.helse.spleis.speil.dto.SpeilTidslinjeperiode
import no.nav.helse.spleis.speil.dto.SpleisVilkårsgrunnlag
import no.nav.helse.spleis.speil.dto.SykdomstidslinjedagKildetype
import no.nav.helse.spleis.speil.dto.SykdomstidslinjedagType
import no.nav.helse.spleis.speil.dto.Tidslinjeperiodetype
import no.nav.helse.spleis.speil.dto.UberegnetPeriode
import no.nav.helse.spleis.speil.dto.Utbetaling
import no.nav.helse.spleis.speil.dto.Utbetalingstatus
import no.nav.helse.spleis.speil.dto.UtbetalingstidslinjedagType
import no.nav.helse.spleis.speil.dto.Utbetalingtype
import no.nav.helse.spleis.speil.dto.Vilkårsgrunnlag

private fun mapDag(dag: SammenslåttDag) = GraphQLDag(
    dato = dag.dagen,
    sykdomsdagtype = when (dag.sykdomstidslinjedagtype) {
        SykdomstidslinjedagType.ARBEIDSDAG -> GraphQLSykdomsdagtype.Arbeidsdag
        SykdomstidslinjedagType.ARBEIDSGIVERDAG -> GraphQLSykdomsdagtype.Arbeidsgiverdag
        SykdomstidslinjedagType.FERIEDAG -> GraphQLSykdomsdagtype.Feriedag
        SykdomstidslinjedagType.ARBEID_IKKE_GJENOPPTATT_DAG -> GraphQLSykdomsdagtype.ArbeidIkkeGjenopptattDag
        SykdomstidslinjedagType.FORELDET_SYKEDAG -> GraphQLSykdomsdagtype.ForeldetSykedag
        SykdomstidslinjedagType.FRISK_HELGEDAG -> GraphQLSykdomsdagtype.FriskHelgedag
        SykdomstidslinjedagType.PERMISJONSDAG -> GraphQLSykdomsdagtype.Permisjonsdag
        SykdomstidslinjedagType.SYKEDAG -> GraphQLSykdomsdagtype.Sykedag
        SykdomstidslinjedagType.SYKEDAG_NAV -> GraphQLSykdomsdagtype.SykedagNav
        SykdomstidslinjedagType.SYK_HELGEDAG -> GraphQLSykdomsdagtype.SykHelgedag
        SykdomstidslinjedagType.ANDRE_YTELSER_FORELDREPENGER -> GraphQLSykdomsdagtype.AndreYtelserForeldrepenger
        SykdomstidslinjedagType.ANDRE_YTELSER_AAP -> GraphQLSykdomsdagtype.AndreYtelserAap
        SykdomstidslinjedagType.ANDRE_YTELSER_OMSORGSPENGER -> GraphQLSykdomsdagtype.AndreYtelserOmsorgspenger
        SykdomstidslinjedagType.ANDRE_YTELSER_PLEIEPENGER -> GraphQLSykdomsdagtype.AndreYtelserPleiepenger
        SykdomstidslinjedagType.ANDRE_YTELSER_SVANGERSKAPSPENGER -> GraphQLSykdomsdagtype.AndreYtelserSvangerskapspenger
        SykdomstidslinjedagType.ANDRE_YTELSER_OPPLÆRINGSPENGER -> GraphQLSykdomsdagtype.AndreYtelserOpplaringspenger
        SykdomstidslinjedagType.ANDRE_YTELSER_DAGPENGER -> GraphQLSykdomsdagtype.AndreYtelserDagpenger
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
    grad = dag.grad?.toDouble(),
    utbetalingsinfo = dag.utbetalingsinfo?.let {
        GraphQLUtbetalingsinfo(
            inntekt = null, // deprecated: speil bruker ikke denne verdien
            utbetaling = it.arbeidsgiverbeløp, // deprecated: verdien settes til det samme som arbeidsgiverbeløp
            personbelop = it.personbeløp,
            arbeidsgiverbelop = it.arbeidsgiverbeløp,
            refusjonsbelop = null, // deprecated: speil bruker ikke denne verdien
            totalGrad = it.totalGrad.toDouble() // double er deprecated: speil forventer float, med runder ned før visning
        )
    },
    begrunnelser = dag.begrunnelser?.map {
        when (it) {
            BegrunnelseDTO.SykepengedagerOppbrukt -> GraphQLBegrunnelse.SykepengedagerOppbrukt
            BegrunnelseDTO.SykepengedagerOppbruktOver67 -> GraphQLBegrunnelse.SykepengedagerOppbruktOver67
            BegrunnelseDTO.MinimumInntekt -> GraphQLBegrunnelse.MinimumInntekt
            BegrunnelseDTO.MinimumInntektOver67 -> GraphQLBegrunnelse.MinimumInntektOver67
            BegrunnelseDTO.EgenmeldingUtenforArbeidsgiverperiode -> GraphQLBegrunnelse.EgenmeldingUtenforArbeidsgiverperiode
            BegrunnelseDTO.AndreYtelserAap -> GraphQLBegrunnelse.AndreYtelser
            BegrunnelseDTO.AndreYtelserDagpenger -> GraphQLBegrunnelse.AndreYtelser
            BegrunnelseDTO.AndreYtelserForeldrepenger -> GraphQLBegrunnelse.AndreYtelser
            BegrunnelseDTO.AndreYtelserOmsorgspenger -> GraphQLBegrunnelse.AndreYtelser
            BegrunnelseDTO.AndreYtelserOpplaringspenger -> GraphQLBegrunnelse.AndreYtelser
            BegrunnelseDTO.AndreYtelserPleiepenger -> GraphQLBegrunnelse.AndreYtelser
            BegrunnelseDTO.AndreYtelserSvangerskapspenger -> GraphQLBegrunnelse.AndreYtelser
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
    typeEnum = when (utbetaling.type) {
        Utbetalingtype.UTBETALING -> GraphQLUtbetalingtype.UTBETALING
        Utbetalingtype.ETTERUTBETALING -> GraphQLUtbetalingtype.ETTERUTBETALING
        Utbetalingtype.ANNULLERING -> GraphQLUtbetalingtype.ANNULLERING
        Utbetalingtype.REVURDERING -> GraphQLUtbetalingtype.REVURDERING
        Utbetalingtype.FERIEPENGER -> GraphQLUtbetalingtype.FERIEPENGER
    },
    status = utbetaling.status.toString(),
    statusEnum = when (utbetaling.status) {
        Utbetalingstatus.Annullert -> GraphQLUtbetalingstatus.Annullert
        Utbetalingstatus.Godkjent -> GraphQLUtbetalingstatus.Godkjent
        Utbetalingstatus.GodkjentUtenUtbetaling -> GraphQLUtbetalingstatus.GodkjentUtenUtbetaling
        Utbetalingstatus.IkkeGodkjent -> GraphQLUtbetalingstatus.IkkeGodkjent
        Utbetalingstatus.Overført -> GraphQLUtbetalingstatus.Overfort
        Utbetalingstatus.Ubetalt -> GraphQLUtbetalingstatus.Ubetalt
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

private fun mapHendelse(hendelse: HendelseDTO) = when (hendelse.type) {
    HendelsetypeDto.NY_SØKNAD -> GraphQLSykmelding(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        fom = hendelse.fom!!,
        tom = hendelse.tom!!,
        rapportertDato = hendelse.rapportertdato!!
    )

    HendelsetypeDto.SENDT_SØKNAD_NAV -> GraphQLSoknadNav(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        fom = hendelse.fom!!,
        tom = hendelse.tom!!,
        rapportertDato = hendelse.rapportertdato!!,
        sendtNav = hendelse.sendtNav!!
    )

    HendelsetypeDto.SENDT_SØKNAD_FRILANS -> GraphQLSoknadFrilans(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        fom = hendelse.fom!!,
        tom = hendelse.tom!!,
        rapportertDato = hendelse.rapportertdato!!,
        sendtNav = hendelse.sendtNav!!
    )

    HendelsetypeDto.SENDT_SØKNAD_SELVSTENDIG -> GraphQLSoknadSelvstendig(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        fom = hendelse.fom!!,
        tom = hendelse.tom!!,
        rapportertDato = hendelse.rapportertdato!!,
        sendtNav = hendelse.sendtNav!!
    )

    HendelsetypeDto.SENDT_SØKNAD_ARBEIDSLEDIG -> GraphQLSoknadArbeidsledig(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        fom = hendelse.fom!!,
        tom = hendelse.tom!!,
        rapportertDato = hendelse.rapportertdato!!,
        sendtNav = hendelse.sendtNav!!
    )

    HendelsetypeDto.SENDT_SØKNAD_ARBEIDSGIVER -> GraphQLSoknadArbeidsgiver(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        fom = hendelse.fom!!,
        tom = hendelse.tom!!,
        rapportertDato = hendelse.rapportertdato!!,
        sendtArbeidsgiver = hendelse.sendtArbeidsgiver!!
    )

    HendelsetypeDto.INNTEKTSMELDING -> GraphQLInntektsmelding(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        mottattDato = hendelse.mottattDato!!,
        beregnetInntekt = hendelse.beregnetInntekt!!
    )

    HendelsetypeDto.INNTEKT_FRA_AORDNINGEN -> GraphQLInntektFraAOrdningen(
        id = hendelse.id,
        eksternDokumentId = hendelse.eksternDokumentId,
        mottattDato = hendelse.mottattDato!!
    )

    else -> null
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
    }
)

private fun mapPeriodetype(type: Tidslinjeperiodetype) = when (type) {
    Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING -> GraphQLPeriodetype.Forstegangsbehandling
    Tidslinjeperiodetype.FORLENGELSE -> GraphQLPeriodetype.Forlengelse
    Tidslinjeperiodetype.OVERGANG_FRA_IT -> GraphQLPeriodetype.OvergangFraIt
    Tidslinjeperiodetype.INFOTRYGDFORLENGELSE -> GraphQLPeriodetype.Infotrygdforlengelse
}

private fun mapInntektstype(kilde: UtbetalingInntektskilde) = when (kilde) {
    UtbetalingInntektskilde.EN_ARBEIDSGIVER -> GraphQLInntektstype.EnArbeidsgiver
    UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE -> GraphQLInntektstype.FlereArbeidsgivere
}

internal fun mapTidslinjeperiode(periode: SpeilTidslinjeperiode, hendelser: List<HendelseDTO>) =
    when (periode) {
        is AnnullertPeriode -> mapAnnullertPeriode(periode, hendelser)
        is BeregnetPeriode -> mapBeregnetPeriode(periode, hendelser)
        is UberegnetPeriode -> GraphQLUberegnetPeriode(
            behandlingId = periode.behandlingId,
            kilde = periode.kilde,
            fom = periode.fom,
            tom = periode.tom,
            tidslinje = periode.sammenslåttTidslinje.map { mapDag(it) },
            periodetype = mapPeriodetype(periode.periodetype),
            inntektstype = mapInntektstype(periode.inntektskilde),
            erForkastet = periode.erForkastet,
            opprettet = periode.oppdatert,
            vedtaksperiodeId = periode.vedtaksperiodeId,
            periodetilstand = mapTilstand(periode.periodetilstand),
            skjaeringstidspunkt = periode.skjæringstidspunkt,
            hendelser = periode.hendelser.tilHendelseDTO(hendelser)
        )
    }

private fun mapBeregnetPeriode(periode: BeregnetPeriode, hendelser: List<HendelseDTO>) =
    GraphQLBeregnetPeriode(
        behandlingId = periode.behandlingId,
        kilde = periode.kilde,
        fom = periode.fom,
        tom = periode.tom,
        tidslinje = periode.sammenslåttTidslinje.map { mapDag(it) },
        periodetype = mapPeriodetype(periode.periodetype),
        inntektstype = mapInntektstype(periode.inntektskilde),
        erForkastet = periode.erForkastet,
        opprettet = periode.behandlingOpprettet,
        vedtaksperiodeId = periode.vedtaksperiodeId,
        beregningId = periode.beregningId,
        gjenstaendeSykedager = periode.utbetaling.gjenståendeDager,
        forbrukteSykedager = periode.utbetaling.forbrukteSykedager,
        skjaeringstidspunkt = periode.skjæringstidspunkt,
        maksdato = periode.utbetaling.maksdato,
        utbetaling = mapUtbetaling(periode.utbetaling),
        hendelser = periode.hendelser.tilHendelseDTO(hendelser),
        periodevilkar = mapPeriodevilkår(periode.periodevilkår),
        periodetilstand = mapTilstand(periode.periodetilstand),
        vilkarsgrunnlagId = periode.vilkårsgrunnlagId
    )

private fun mapAnnullertPeriode(periode: AnnullertPeriode, hendelser: List<HendelseDTO>) =
    GraphQLBeregnetPeriode(
        behandlingId = periode.behandlingId,
        kilde = periode.kilde,
        fom = periode.fom,
        tom = periode.tom,
        tidslinje = periode.sammenslåttTidslinje.map { mapDag(it) },
        periodetype = mapPeriodetype(periode.periodetype),
        inntektstype = mapInntektstype(periode.inntektskilde),
        erForkastet = periode.erForkastet,
        opprettet = periode.opprettet,
        vedtaksperiodeId = periode.vedtaksperiodeId,
        beregningId = periode.beregningId,
        gjenstaendeSykedager = periode.utbetaling.gjenståendeDager,
        forbrukteSykedager = periode.utbetaling.forbrukteSykedager,
        skjaeringstidspunkt = periode.skjæringstidspunkt,
        maksdato = periode.utbetaling.maksdato,
        utbetaling = mapUtbetaling(periode.utbetaling),
        hendelser = periode.hendelser.tilHendelseDTO(hendelser),
        periodevilkar = GraphQLPeriodevilkar(
            sykepengedager = GraphQLPeriodevilkar.Sykepengedager(
                skjaeringstidspunkt = LocalDate.MIN,
                maksdato = LocalDate.MAX,
                forbrukteSykedager = null,
                gjenstaendeSykedager = null,
                oppfylt = false
            ),
            alder = GraphQLPeriodevilkar.Alder(
                alderSisteSykedag = 0,
                oppfylt = false
            )
        ),
        periodetilstand = mapTilstand(periode.periodetilstand),
        vilkarsgrunnlagId = null
    )

private fun Set<UUID>.tilHendelseDTO(hendelser: List<HendelseDTO>): List<GraphQLHendelse> {
    return this
        .mapNotNull { dokumentId -> hendelser.firstOrNull { hendelseDTO -> hendelseDTO.id == dokumentId.toString() } }
        .mapNotNull { mapHendelse(it) }
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
    Periodetilstand.AvventerInntektsopplysninger -> GraphQLPeriodetilstand.AvventerInntektsopplysninger
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
    omregnetArsinntekt = inntekt.omregnetÅrsinntekt.tilGraphQLOmregnetArsinntekt(),
    skjonnsmessigFastsatt = inntekt.skjønnsmessigFastsatt?.let {
        GraphQLSkjonnsmessigFastsatt(
            belop = it.årlig,
            manedsbelop = it.månedlig
        )
    },
    skjonnsmessigFastsattAarlig = inntekt.skjønnsmessigFastsatt?.årlig,
    fom = inntekt.fom,
    tom = inntekt.tom,
    deaktivert = inntekt.deaktivert
)

private fun Inntekt.tilGraphQLOmregnetArsinntekt() = GraphQLOmregnetArsinntekt(
    kilde = when (this.kilde) {
        Inntektkilde.Saksbehandler -> GraphQLInntektskilde.Saksbehandler
        Inntektkilde.Inntektsmelding -> GraphQLInntektskilde.Inntektsmelding
        Inntektkilde.Infotrygd -> GraphQLInntektskilde.Infotrygd
        Inntektkilde.AOrdningen -> GraphQLInntektskilde.AOrdningen
        Inntektkilde.IkkeRapportert -> GraphQLInntektskilde.IkkeRapportert
    },
    belop = this.beløp,
    manedsbelop = this.månedsbeløp,
    inntekterFraAOrdningen = null
)

internal fun mapVilkårsgrunnlag(id: UUID, vilkårsgrunnlag: Vilkårsgrunnlag) =
    when (vilkårsgrunnlag) {
        is SpleisVilkårsgrunnlag -> GraphQLSpleisVilkarsgrunnlag(
            id = id,
            skjaeringstidspunkt = vilkårsgrunnlag.skjæringstidspunkt,
            omregnetArsinntekt = vilkårsgrunnlag.omregnetÅrsinntekt,
            sykepengegrunnlag = vilkårsgrunnlag.sykepengegrunnlag,
            inntekter = vilkårsgrunnlag.inntekter.map { inntekt -> mapInntekt(inntekt) },
            grunnbelop = vilkårsgrunnlag.grunnbeløp,
            sykepengegrunnlagsgrense = mapSykepengergrunnlagsgrense(vilkårsgrunnlag.sykepengegrunnlagsgrense),
            antallOpptjeningsdagerErMinst = vilkårsgrunnlag.antallOpptjeningsdagerErMinst,
            opptjeningFra = vilkårsgrunnlag.opptjeningFra,
            oppfyllerKravOmMinstelonn = vilkårsgrunnlag.oppfyllerKravOmMinstelønn,
            oppfyllerKravOmOpptjening = vilkårsgrunnlag.oppfyllerKravOmOpptjening,
            oppfyllerKravOmMedlemskap = vilkårsgrunnlag.oppfyllerKravOmMedlemskap,
            arbeidsgiverrefusjoner = vilkårsgrunnlag.arbeidsgiverrefusjoner.map { refusjon -> mapArbeidsgiverRefusjon(refusjon) }
        )

        is InfotrygdVilkårsgrunnlag -> GraphQLInfotrygdVilkarsgrunnlag(
            id = id,
            skjaeringstidspunkt = vilkårsgrunnlag.skjæringstidspunkt,
            omregnetArsinntekt = vilkårsgrunnlag.beregningsgrunnlag, // For infotrygd har vi ikke noe konsept for hvorvidt en inntekt er skjønnsfastsatt
            sykepengegrunnlag = vilkårsgrunnlag.sykepengegrunnlag,
            inntekter = vilkårsgrunnlag.inntekter.map { inntekt -> mapInntekt(inntekt) },
            arbeidsgiverrefusjoner = vilkårsgrunnlag.arbeidsgiverrefusjoner.map { refusjon -> mapArbeidsgiverRefusjon(refusjon) }
        )

        else -> throw IllegalStateException("har ikke mapping for vilkårsgrunnlag ${vilkårsgrunnlag::class.simpleName ?: "[ukjent klassenavn]"}")
    }


private fun mapSykepengergrunnlagsgrense(sykepengegrunnlagsgrenseDTO: SykepengegrunnlagsgrenseDTO) =
    GraphQLSykepengegrunnlagsgrense(sykepengegrunnlagsgrenseDTO.grunnbeløp, sykepengegrunnlagsgrenseDTO.grense, sykepengegrunnlagsgrenseDTO.virkningstidspunkt)
