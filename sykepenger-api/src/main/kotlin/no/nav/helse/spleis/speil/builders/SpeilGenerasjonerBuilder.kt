package no.nav.helse.spleis.speil.builders

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.dto.BehandlingtilstandDto
import no.nav.helse.dto.EndringskodeDto
import no.nav.helse.dto.UtbetalingTilstandDto
import no.nav.helse.dto.UtbetalingtypeDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.serialisering.ArbeidsgiverUtDto
import no.nav.helse.dto.serialisering.BehandlingUtDto
import no.nav.helse.dto.serialisering.OppdragUtDto
import no.nav.helse.dto.serialisering.VedtaksperiodeUtDto
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.spleis.speil.SpekematDTO
import no.nav.helse.spleis.speil.builders.ArbeidsgiverBuilder.Companion.fjernUnødvendigeRader
import no.nav.helse.spleis.speil.dto.AlderDTO
import no.nav.helse.spleis.speil.dto.AnnullertPeriode
import no.nav.helse.spleis.speil.dto.AnnullertUtbetaling
import no.nav.helse.spleis.speil.dto.BeregnetPeriode
import no.nav.helse.spleis.speil.dto.EndringskodeDTO
import no.nav.helse.spleis.speil.dto.Periodetilstand
import no.nav.helse.spleis.speil.dto.SpeilGenerasjonDTO
import no.nav.helse.spleis.speil.dto.SpeilOppdrag
import no.nav.helse.spleis.speil.dto.SpeilTidslinjeperiode
import no.nav.helse.spleis.speil.dto.SpeilTidslinjeperiode.Companion.utledPeriodetyper
import no.nav.helse.spleis.speil.dto.Tidslinjeperiodetype
import no.nav.helse.spleis.speil.dto.UberegnetPeriode
import no.nav.helse.spleis.speil.dto.Utbetaling
import no.nav.helse.spleis.speil.dto.Utbetalingstatus
import no.nav.helse.spleis.speil.dto.Utbetalingstidslinjedag
import no.nav.helse.spleis.speil.dto.UtbetalingstidslinjedagType
import no.nav.helse.spleis.speil.dto.Utbetalingtype
import no.nav.helse.spleis.speil.merge

internal class SpeilGenerasjonerBuilder(
    private val organisasjonsnummer: String,
    private val alder: AlderDTO,
    private val arbeidsgiverUtDto: ArbeidsgiverUtDto,
    private val vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk,
    private val pølsepakke: SpekematDTO.PølsepakkeDTO
) {
    private val utbetalinger = mapUtbetalinger()
    private val annulleringer = mapAnnulleringer()
    private val utbetalingstidslinjer = mapUtbetalingstidslinjer()

    private val allePerioder = mapPerioder()

    internal fun build(): List<SpeilGenerasjonDTO> {
        return buildSpekemat()
    }

    private fun mapPerioder(): List<SpeilTidslinjeperiode> {
        val aktive = arbeidsgiverUtDto.vedtaksperioder.flatMap { mapVedtaksperiode(it) }
        val forkastede = arbeidsgiverUtDto.forkastede.flatMap { mapVedtaksperiode(it.vedtaksperiode) }
        return aktive + forkastede
    }
    private fun mapVedtaksperiode(vedtaksperiode: VedtaksperiodeUtDto): List<SpeilTidslinjeperiode> {
        var forrigeGenerasjon: SpeilTidslinjeperiode? = null
        return vedtaksperiode.behandlinger.behandlinger.mapNotNull { generasjon ->
            when (generasjon.tilstand) {
                BehandlingtilstandDto.BEREGNET,
                BehandlingtilstandDto.BEREGNET_OMGJØRING,
                BehandlingtilstandDto.BEREGNET_REVURDERING,
                BehandlingtilstandDto.REVURDERT_VEDTAK_AVVIST,
                BehandlingtilstandDto.VEDTAK_FATTET,
                BehandlingtilstandDto.VEDTAK_IVERKSATT -> mapBeregnetPeriode(vedtaksperiode, generasjon)
                BehandlingtilstandDto.UBEREGNET,
                BehandlingtilstandDto.UBEREGNET_OMGJØRING,
                BehandlingtilstandDto.UBEREGNET_REVURDERING,
                BehandlingtilstandDto.AVSLUTTET_UTEN_VEDTAK -> mapUberegnetPeriode(vedtaksperiode, generasjon)
                BehandlingtilstandDto.TIL_INFOTRYGD -> mapTilInfotrygdperiode(vedtaksperiode, forrigeGenerasjon, generasjon)
                BehandlingtilstandDto.ANNULLERT_PERIODE -> mapAnnullertPeriode(vedtaksperiode, generasjon)
            }.also {
                forrigeGenerasjon = it
            }
        }
    }

    private fun mapTilInfotrygdperiode(vedtaksperiode: VedtaksperiodeUtDto, forrigePeriode: SpeilTidslinjeperiode?, generasjon: BehandlingUtDto): UberegnetPeriode? {
        if (forrigePeriode == null) return null // todo: her kan vi mappe perioder som tas ut av Speil
        return mapUberegnetPeriode(vedtaksperiode, generasjon, Periodetilstand.Annullert)
    }

    private fun mapUberegnetPeriode(vedtaksperiode: VedtaksperiodeUtDto, generasjon: BehandlingUtDto, periodetilstand: Periodetilstand? = null): UberegnetPeriode {
        val sisteEndring = generasjon.endringer.last()
        val sykdomstidslinje = SykdomstidslinjeBuilder(sisteEndring.sykdomstidslinje, sisteEndring.periode).build()
        val utbetalingstidslinje = UtbetalingstidslinjeBuilder(sisteEndring.utbetalingstidslinje).build()
        return UberegnetPeriode(
            vedtaksperiodeId = vedtaksperiode.id,
            behandlingId = generasjon.id,
            kilde = generasjon.kilde.meldingsreferanseId,
            fom = sisteEndring.periode.fom,
            tom = sisteEndring.periode.tom,
            sammenslåttTidslinje = sykdomstidslinje.merge(utbetalingstidslinje),
            periodetype = Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING, // feltet gir ikke mening for uberegnede perioder
            inntektskilde = UtbetalingInntektskilde.EN_ARBEIDSGIVER, // feltet gir ikke mening for uberegnede perioder
            erForkastet = false,
            opprettet = generasjon.endringer.first().tidsstempel,
            oppdatert = sisteEndring.tidsstempel,
            skjæringstidspunkt = vedtaksperiode.skjæringstidspunkt,
            hendelser = dokumenterTilOgMedDenneGenerasjonen(vedtaksperiode, generasjon),
            periodetilstand = periodetilstand ?: generasjon.avsluttet?.let { Periodetilstand.IngenUtbetaling } ?: when (vedtaksperiode.tilstand) {
                is VedtaksperiodetilstandDto.AVVENTER_REVURDERING -> Periodetilstand.UtbetaltVenterPåAnnenPeriode
                is VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE -> Periodetilstand.VenterPåAnnenPeriode

                is VedtaksperiodetilstandDto.AVVENTER_HISTORIKK,
                is VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING,
                is VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING,
                is VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING_REVURDERING -> Periodetilstand.ForberederGodkjenning

                is VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING -> Periodetilstand.AvventerInntektsopplysninger
                is VedtaksperiodetilstandDto.AVVENTER_INFOTRYGDHISTORIKK -> Periodetilstand.ManglerInformasjon
                else -> error("Forventer ikke mappingregel for ${vedtaksperiode.tilstand}")
            }

        )
    }
    private fun mapBeregnetPeriode(vedtaksperiode: VedtaksperiodeUtDto, generasjon: BehandlingUtDto): BeregnetPeriode {
        val sisteEndring = generasjon.endringer.last()
        val utbetaling = utbetalinger.singleOrNull { it.id == sisteEndring.utbetalingId } ?: error("Fant ikke tilhørende utbetaling for vedtaksperiodeId=${vedtaksperiode.id}")
        val utbetalingstidslinje = UtbetalingstidslinjeBuilder(sisteEndring.utbetalingstidslinje).build()
        val skjæringstidspunkt = sisteEndring.skjæringstidspunkt
        val sisteSykepengedag = utbetalingstidslinje.sisteNavDag()?.dato ?: sisteEndring.periode.tom
        val sykdomstidslinje = SykdomstidslinjeBuilder(sisteEndring.sykdomstidslinje, sisteEndring.periode).build()
        return BeregnetPeriode(
            vedtaksperiodeId = vedtaksperiode.id,
            behandlingId = generasjon.id,
            kilde = generasjon.kilde.meldingsreferanseId,
            fom = sisteEndring.periode.fom,
            tom = sisteEndring.periode.tom,
            sammenslåttTidslinje = sykdomstidslinje.merge(utbetalingstidslinje),
            erForkastet = false,
            periodetype = Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING, // TODO: fikse
            inntektskilde = UtbetalingInntektskilde.EN_ARBEIDSGIVER, // verdien av feltet brukes ikke i speil
            opprettet = vedtaksperiode.opprettet,
            behandlingOpprettet = generasjon.endringer.first().tidsstempel,
            oppdatert = vedtaksperiode.oppdatert,
            periodetilstand = utledePeriodetilstand(vedtaksperiode.tilstand, utbetaling, utbetalingstidslinje),
            skjæringstidspunkt = skjæringstidspunkt,
            hendelser = dokumenterTilOgMedDenneGenerasjonen(vedtaksperiode, generasjon),
            beregningId = utbetaling.id,
            utbetaling = utbetaling,
            periodevilkår = periodevilkår(sisteSykepengedag, utbetaling, alder, skjæringstidspunkt),
            vilkårsgrunnlagId = sisteEndring.vilkårsgrunnlagId!!,
            refusjonstidslinje = sisteEndring.refusjonstidslinje
        )
    }

    private fun mapAnnullertPeriode(vedtaksperiode: VedtaksperiodeUtDto, generasjon: BehandlingUtDto): AnnullertPeriode {
        val sisteEndring = generasjon.endringer.last()
        val annulleringen = annulleringer.single { it.id == sisteEndring.utbetalingId }
        return AnnullertPeriode(
            vedtaksperiodeId = vedtaksperiode.id,
            behandlingId = generasjon.id,
            kilde = generasjon.kilde.meldingsreferanseId,
            fom = sisteEndring.periode.fom,
            tom = sisteEndring.periode.tom,
            opprettet = generasjon.endringer.first().tidsstempel,
            // feltet gir ikke mening for annullert periode:
            vilkår = BeregnetPeriode.Vilkår(
                sykepengedager = BeregnetPeriode.Sykepengedager(sisteEndring.periode.fom, LocalDate.MAX, null, null, false),
                alder = alder.let {
                    val alderSisteSykedag = it.alderPåDato(sisteEndring.periode.tom)
                    BeregnetPeriode.Alder(alderSisteSykedag, alderSisteSykedag < 70)
                }
            ),
            beregnet = annulleringen.annulleringstidspunkt,
            oppdatert = vedtaksperiode.oppdatert,
            periodetilstand = annulleringen.periodetilstand,
            hendelser = dokumenterTilOgMedDenneGenerasjonen(vedtaksperiode, generasjon),
            beregningId = annulleringen.id,
            utbetaling = Utbetaling(
                annulleringen.id,
                Utbetalingtype.ANNULLERING,
                annulleringen.korrelasjonsId,
                LocalDate.MAX,
                0,
                0,
                annulleringen.utbetalingstatus,
                0,
                0,
                annulleringen.arbeidsgiverFagsystemId,
                annulleringen.personFagsystemId,
                emptyMap(),
                null
            )
        )
    }

    private fun dokumenterTilOgMedDenneGenerasjonen(vedtaksperiode: VedtaksperiodeUtDto, generasjon: BehandlingUtDto): Set<UUID> {
        return vedtaksperiode.behandlinger.behandlinger
            .asSequence()
            .takeWhile { it.id != generasjon.id }
            .plus(generasjon)
            .flatMap { it.endringer }
            .map { it.dokumentsporing.id }
            .toSet()
    }

    private fun List<Utbetalingstidslinjedag>.sisteNavDag() =
        lastOrNull { it.type == UtbetalingstidslinjedagType.NavDag }

    private fun utledePeriodetilstand(periodetilstand: VedtaksperiodetilstandDto, utbetalingDTO: Utbetaling, avgrensetUtbetalingstidslinje: List<Utbetalingstidslinjedag>) =
        when (utbetalingDTO.status) {
            Utbetalingstatus.IkkeGodkjent -> Periodetilstand.RevurderingFeilet
            Utbetalingstatus.Utbetalt, Utbetalingstatus.GodkjentUtenUtbetaling -> when {
                avgrensetUtbetalingstidslinje.none { it.utbetalingsinfo()?.harUtbetaling() == true } -> Periodetilstand.IngenUtbetaling
                else -> Periodetilstand.Utbetalt
            }
            Utbetalingstatus.Ubetalt -> when {
                periodetilstand in setOf(VedtaksperiodetilstandDto.AVVENTER_GODKJENNING_REVURDERING, VedtaksperiodetilstandDto.AVVENTER_GODKJENNING) -> Periodetilstand.TilGodkjenning
                periodetilstand in setOf(VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING, VedtaksperiodetilstandDto.AVVENTER_SIMULERING, VedtaksperiodetilstandDto.AVVENTER_SIMULERING_REVURDERING) -> Periodetilstand.ForberederGodkjenning
                periodetilstand in setOf(VedtaksperiodetilstandDto.AVVENTER_HISTORIKK) -> Periodetilstand.ForberederGodkjenning
                periodetilstand == VedtaksperiodetilstandDto.AVVENTER_REVURDERING -> Periodetilstand.UtbetaltVenterPåAnnenPeriode // flere AG; en annen AG har laget utbetaling på vegne av *denne* (revurdering)
                periodetilstand in setOf(VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE, VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING) -> Periodetilstand.VenterPåAnnenPeriode // flere AG; en annen AG har laget utbetaling på vegne av *denne* (førstegangsvurdering)
                else -> error("har ikke mappingregel for utbetalingstatus ${utbetalingDTO.status} og periodetilstand=$periodetilstand")
            }
            Utbetalingstatus.Godkjent,
            Utbetalingstatus.Overført -> Periodetilstand.TilUtbetaling
            else -> error("har ikke mappingregel for ${utbetalingDTO.status}")
        }

    private fun periodevilkår(
        sisteSykepengedag: LocalDate,
        utbetaling: Utbetaling,
        alder: AlderDTO,
        skjæringstidspunkt: LocalDate
    ): BeregnetPeriode.Vilkår {
        val sykepengedager = BeregnetPeriode.Sykepengedager(
            skjæringstidspunkt,
            utbetaling.maksdato,
            utbetaling.forbrukteSykedager,
            utbetaling.gjenståendeDager,
            utbetaling.maksdato > sisteSykepengedag
        )
        val alderSisteSykepengedag = alder.alderPåDato(sisteSykepengedag).let {
            BeregnetPeriode.Alder(it, it < 70)
        }
        return BeregnetPeriode.Vilkår(sykepengedager, alderSisteSykepengedag)
    }

    private fun mapUtbetalingstidslinjer(): List<Pair<UUID, UtbetalingstidslinjeBuilder>> {
        return arbeidsgiverUtDto.utbetalinger.map {
            it.id to UtbetalingstidslinjeBuilder(it.utbetalingstidslinje)
        }
    }

    private fun mapUtbetalinger(): List<Utbetaling> {
        return arbeidsgiverUtDto.utbetalinger
            .filter { it.type in setOf(UtbetalingtypeDto.REVURDERING, UtbetalingtypeDto.UTBETALING) }
            .mapNotNull {
                Utbetaling(
                    id = it.id,
                    type = when (it.type) {
                        UtbetalingtypeDto.REVURDERING -> Utbetalingtype.REVURDERING
                        UtbetalingtypeDto.UTBETALING -> Utbetalingtype.UTBETALING
                        else -> error("Forventer ikke mapping for utbetalingtype=${it.type}")
                    },
                    korrelasjonsId = it.korrelasjonsId,
                    status = when (it.tilstand) {
                        UtbetalingTilstandDto.ANNULLERT -> Utbetalingstatus.Annullert
                        UtbetalingTilstandDto.GODKJENT -> Utbetalingstatus.Godkjent
                        UtbetalingTilstandDto.GODKJENT_UTEN_UTBETALING -> Utbetalingstatus.GodkjentUtenUtbetaling
                        UtbetalingTilstandDto.IKKE_GODKJENT -> Utbetalingstatus.IkkeGodkjent
                        UtbetalingTilstandDto.IKKE_UTBETALT -> Utbetalingstatus.Ubetalt
                        UtbetalingTilstandDto.OVERFØRT -> Utbetalingstatus.Overført
                        UtbetalingTilstandDto.UTBETALT -> Utbetalingstatus.Utbetalt
                        else -> return@mapNotNull null
                    },
                    maksdato = it.maksdato,
                    forbrukteSykedager = it.forbrukteSykedager!!,
                    gjenståendeDager = it.gjenståendeSykedager!!,
                    arbeidsgiverNettoBeløp = it.arbeidsgiverOppdrag.nettoBeløp,
                    arbeidsgiverFagsystemId = it.arbeidsgiverOppdrag.fagsystemId,
                    personNettoBeløp = it.personOppdrag.nettoBeløp,
                    personFagsystemId = it.personOppdrag.fagsystemId,
                    oppdrag = mapOf(
                        it.arbeidsgiverOppdrag.fagsystemId to mapOppdrag(it.arbeidsgiverOppdrag),
                        it.personOppdrag.fagsystemId to mapOppdrag(it.personOppdrag),
                    ),
                    vurdering = it.vurdering?.let { vurdering ->
                        Utbetaling.Vurdering(
                            godkjent = vurdering.godkjent,
                            tidsstempel = vurdering.tidspunkt,
                            automatisk = vurdering.automatiskBehandling,
                            ident = vurdering.ident
                        )
                    }
                )
            }
    }

    private fun mapAnnulleringer(): List<AnnullertUtbetaling> {
        return arbeidsgiverUtDto.utbetalinger
            .filter { it.type == UtbetalingtypeDto.ANNULLERING }
            .mapNotNull {
                AnnullertUtbetaling(
                    id = it.id,
                    korrelasjonsId = it.korrelasjonsId,
                    annulleringstidspunkt = it.tidsstempel,
                    arbeidsgiverFagsystemId = it.personOppdrag.fagsystemId,
                    personFagsystemId = it.personOppdrag.fagsystemId,
                    utbetalingstatus = when (it.tilstand) {
                        UtbetalingTilstandDto.ANNULLERT -> Utbetalingstatus.Annullert
                        UtbetalingTilstandDto.GODKJENT -> Utbetalingstatus.Godkjent
                        UtbetalingTilstandDto.GODKJENT_UTEN_UTBETALING -> Utbetalingstatus.GodkjentUtenUtbetaling
                        UtbetalingTilstandDto.IKKE_GODKJENT -> Utbetalingstatus.IkkeGodkjent
                        UtbetalingTilstandDto.IKKE_UTBETALT -> Utbetalingstatus.Ubetalt
                        UtbetalingTilstandDto.OVERFØRT -> Utbetalingstatus.Overført
                        UtbetalingTilstandDto.UTBETALT -> Utbetalingstatus.Utbetalt
                        else -> return@mapNotNull null
                    }
                )
            }
    }

    private fun mapOppdrag(dto: OppdragUtDto): SpeilOppdrag {
        return SpeilOppdrag(
            fagsystemId = dto.fagsystemId,
            tidsstempel = dto.tidsstempel,
            nettobeløp = dto.nettoBeløp,
            simulering = dto.simuleringsResultat?.let {
                SpeilOppdrag.Simulering(
                    totalbeløp = it.totalbeløp,
                    perioder = it.perioder.map {
                        SpeilOppdrag.Simuleringsperiode(
                            fom = it.fom,
                            tom = it.tom,
                            utbetalinger = it.utbetalinger.map {
                                SpeilOppdrag.Simuleringsutbetaling(
                                    mottakerNavn = it.utbetalesTil.navn,
                                    mottakerId = it.utbetalesTil.id,
                                    forfall = it.forfallsdato,
                                    feilkonto = it.feilkonto,
                                    detaljer = it.detaljer.map {
                                        SpeilOppdrag.Simuleringsdetaljer(
                                            faktiskFom = it.fom,
                                            faktiskTom = it.tom,
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
            utbetalingslinjer = dto.linjer.map { linje ->
                SpeilOppdrag.Utbetalingslinje(
                    fom = linje.fom,
                    tom = linje.tom,
                    dagsats = linje.beløp,
                    grad = linje.grad!!,
                    endringskode = when (linje.endringskode) {
                        EndringskodeDto.ENDR -> EndringskodeDTO.ENDR
                        EndringskodeDto.NY -> EndringskodeDTO.NY
                        EndringskodeDto.UEND -> EndringskodeDTO.UEND
                    }
                )
            }
        )
    }

    private fun buildSpekemat(): List<SpeilGenerasjonDTO> {
        val generasjoner = buildTidslinjeperioder()
        return pølsepakke.rader
            .fjernUnødvendigeRader()
            .map { rad -> mapRadTilSpeilGenerasjon(rad, generasjoner) }
            .filterNot { rad -> rad.size == 0 } // fjerner tomme rader
    }

    private fun buildTidslinjeperioder(): List<SpeilTidslinjeperiode> {
        return allePerioder
    }

    private fun mapRadTilSpeilGenerasjon(rad: SpekematDTO.PølsepakkeDTO.PølseradDTO, generasjoner: List<SpeilTidslinjeperiode>): SpeilGenerasjonDTO {
        val perioder = rad.pølser.mapNotNull { pølse -> generasjoner.firstOrNull { it.behandlingId == pølse.behandlingId } }
        return SpeilGenerasjonDTO(
            id = UUID.randomUUID(),
            kildeTilGenerasjon = rad.kildeTilRad,
            perioder = perioder
                .map { it.registrerBruk(vilkårsgrunnlaghistorikk, organisasjonsnummer) }
                .utledPeriodetyper()
        )
    }
}
