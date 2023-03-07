package no.nav.helse.serde.api.speil

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.person.Inntektskilde
import no.nav.helse.person.Periodetype
import no.nav.helse.person.Vedtaksperiode.AvsluttetUtenUtbetaling
import no.nav.helse.person.Vedtaksperiode.AvventerBlokkerendePeriode
import no.nav.helse.person.Vedtaksperiode.AvventerGjennomførtRevurdering
import no.nav.helse.person.Vedtaksperiode.AvventerGodkjenning
import no.nav.helse.person.Vedtaksperiode.AvventerGodkjenningRevurdering
import no.nav.helse.person.Vedtaksperiode.AvventerHistorikk
import no.nav.helse.person.Vedtaksperiode.AvventerRevurdering
import no.nav.helse.person.Vedtaksperiode.AvventerVilkårsprøving
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.serde.api.dto.BeregnetPeriode
import no.nav.helse.serde.api.dto.Generasjon
import no.nav.helse.serde.api.dto.HendelseDTO
import no.nav.helse.serde.api.dto.HendelseDTO.Companion.finn
import no.nav.helse.serde.api.dto.Periodetilstand
import no.nav.helse.serde.api.dto.Periodetilstand.Annullert
import no.nav.helse.serde.api.dto.Periodetilstand.ForberederGodkjenning
import no.nav.helse.serde.api.dto.Periodetilstand.IngenUtbetaling
import no.nav.helse.serde.api.dto.Periodetilstand.ManglerInformasjon
import no.nav.helse.serde.api.dto.Periodetilstand.TilAnnullering
import no.nav.helse.serde.api.dto.Periodetilstand.TilGodkjenning
import no.nav.helse.serde.api.dto.Periodetilstand.Utbetalt
import no.nav.helse.serde.api.dto.Periodetilstand.UtbetaltVenterPåAnnenPeriode
import no.nav.helse.serde.api.dto.Periodetilstand.VenterPåAnnenPeriode
import no.nav.helse.serde.api.dto.SammenslåttDag
import no.nav.helse.serde.api.dto.SpeilOppdrag
import no.nav.helse.serde.api.dto.Sykdomstidslinjedag
import no.nav.helse.serde.api.dto.SøknadNavDTO
import no.nav.helse.serde.api.dto.Tidslinjeperiode
import no.nav.helse.serde.api.dto.UberegnetPeriode
import no.nav.helse.serde.api.dto.Utbetaling
import no.nav.helse.serde.api.dto.Utbetalingstatus
import no.nav.helse.serde.api.dto.Utbetalingstidslinjedag
import no.nav.helse.serde.api.dto.UtbetalingstidslinjedagType
import no.nav.helse.serde.api.dto.Utbetalingtype
import no.nav.helse.serde.api.speil.Generasjoner.Generasjon.Companion.fjernErstattede
import no.nav.helse.serde.api.speil.Generasjoner.Generasjon.Companion.flyttPerioder
import no.nav.helse.serde.api.speil.Generasjoner.Generasjon.Companion.sorterGenerasjoner
import no.nav.helse.serde.api.speil.Generasjoner.Generasjon.Companion.toDTO
import no.nav.helse.serde.api.speil.IVedtaksperiode.Companion.tilGodkjenning
import no.nav.helse.serde.api.speil.Tidslinjeberegninger.ITidslinjeberegning
import no.nav.helse.serde.api.speil.builders.BeregningId
import no.nav.helse.serde.api.speil.builders.IVilkårsgrunnlag
import no.nav.helse.serde.api.speil.builders.IVilkårsgrunnlagHistorikk
import no.nav.helse.serde.api.speil.builders.PeriodeVarslerBuilder
import no.nav.helse.Alder
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.*

internal class Generasjoner(perioder: Tidslinjeperioder) {
    private val generasjoner: List<Generasjon> = perioder.toGenerasjoner()

    internal fun build(): List<no.nav.helse.serde.api.dto.Generasjon> {
        return generasjoner
            .flyttPerioder()
            .fjernErstattede()
            .sorterGenerasjoner()
            .toDTO()
            .reversed()
    }

    internal class Generasjon(perioder: List<Tidslinjeperiode>) {
        private val perioder = perioder.toMutableList()
        private var erstattet: Boolean = false

        private val tidslinjeperioder = perioder.filterIsInstance<BeregnetPeriode>()

        private fun finnPeriode(periode: Tidslinjeperiode) = perioder.find { it.erSammeVedtaksperiode(periode) }
        private fun inneholderPeriode(periode: Tidslinjeperiode) = finnPeriode(periode) != null
        private fun harAnnullert(periode: BeregnetPeriode): Boolean =
            tidslinjeperioder.any { it.hørerSammen(periode) && it.erAnnullering() }

        private fun senereUtbetalingAnnullert(periode: BeregnetPeriode): Boolean {
            return perioder.any { it is BeregnetPeriode && it > periode && it.erAnnullering() && !it.hørerSammen(periode) }
        }

        private fun harMinstÉnRevurdertPeriodeTidligereEnn(tidslinjeperiode: Tidslinjeperiode): Boolean =
            tidslinjeperioder.any { it.erRevurdering() && it.fom < tidslinjeperiode.fom && it.hørerSammen(tidslinjeperiode) }

        private fun kandidaterSomSkalFlyttesTilNesteGenerasjon(nesteGenerasjon: Generasjon, alle: List<Generasjon>): Pair<List<Tidslinjeperiode>, List<Tidslinjeperiode>> {
            return perioder.partition { periode ->
                if (periode !is BeregnetPeriode) return@partition FLYTTES
                if (!periode.erAnnullering()) {
                    if (nesteGenerasjon.harAnnullert(periode)) return@partition FLYTTES_IKKE

                    // Dersom det har forekommet to annulleringer på rad rett etter denne versjonen av perioden,
                    // der perioden inngår, ønsker vi ikke å flytte denne versjonen til neste generasjon fordi vi ønsker
                    // å sammenstille de to annulleringene
                    val nesteEtterNeste = alle.getOrNull(alle.indexOf(nesteGenerasjon) + 1)
                    if (nesteGenerasjon.senereUtbetalingAnnullert(periode) && nesteEtterNeste?.harAnnullert(periode) == true) return@partition FLYTTES_IKKE
                    if (nesteGenerasjon.harMinstÉnRevurdertPeriodeTidligereEnn(periode)) return@partition FLYTTES_IKKE
                }
                if (nesteGenerasjon.harMinstÉnRevurdertPeriodeTidligereEnn(periode)) return@partition FLYTTES
                !nesteGenerasjon.inneholderPeriode(periode)
            }
        }

        private fun sorterFallende(): Generasjon {
            perioder.sortByDescending { it.fom }
            return this
        }

        private fun fjernPerioderSomVenter(perioder: List<Tidslinjeperiode>) {
            this.perioder.removeAll(perioder.filter { it.venter() })
        }

        private fun fjernOutOfOrder(skalFlyttes: List<Tidslinjeperiode>) {
            val sortertePerioder = perioder.sortedByDescending { it.fom }
            val skalFjernes = skalFlyttes.filter { sortertePerioder.indexOf(it) != 0 && it.opprettet > sortertePerioder[0].opprettet }
            this.perioder.removeAll(skalFjernes)
        }

        private fun utvidMed(perioder: List<Tidslinjeperiode>) {
            this.perioder.addAll(perioder)
        }

        internal companion object {
            private const val FLYTTES_IKKE: Boolean = false
            private const val FLYTTES: Boolean = true

            internal fun List<Generasjon>.flyttPerioder(): List<Generasjon> {
                forEachIndexed { index, generasjon ->
                    if (index == size - 1) return@forEachIndexed // siste generasjon skal ikke gjøres noe med
                    val nesteGenerasjon = this[index + 1]
                    val (skalFlyttes, skalIkkeFlyttes) =
                        generasjon.kandidaterSomSkalFlyttesTilNesteGenerasjon(nesteGenerasjon, this)
                    nesteGenerasjon.utvidMed(skalFlyttes)
                    generasjon.fjernOutOfOrder(skalFlyttes)
                    generasjon.fjernPerioderSomVenter(skalFlyttes)
                    generasjon.erstattet = skalIkkeFlyttes.isEmpty()
                }
                return this
            }

            internal fun List<Generasjon>.fjernErstattede() = filterNot { it.erstattet }

            internal fun List<Generasjon>.sorterGenerasjoner() = map { it.sorterFallende() }

            internal fun List<Generasjon>.toDTO(): List<no.nav.helse.serde.api.dto.Generasjon> {
                return map {
                    Generasjon(UUID.randomUUID(), it.perioder)
                }
            }
        }
    }
}

internal class Tidslinjeperioder(
    private val alder: Alder,
    private val forkastetVedtaksperiodeIder: List<UUID>,
    vedtaksperioder: List<IVedtaksperiode>,
    tidslinjeberegninger: Tidslinjeberegninger,
    vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk
) {
    private var perioder: List<Tidslinjeperiode>

    private fun erForkastet(vedtaksperiodeId: UUID) = vedtaksperiodeId in forkastetVedtaksperiodeIder

    init {
        perioder = vedtaksperioder.toMutableList().flatMap { periode ->
            when {
                periode.utbetalinger.isEmpty() -> listOf(
                    uberegnetPeriode(
                        periode,
                        erForkastet(periode.vedtaksperiodeId)
                    )
                )
                else -> periode.utbetalinger.map { (vilkårsgrunnlag, utbetaling) ->
                    val tidslinjeberegning = tidslinjeberegninger.finn(utbetaling.beregningId)
                    utbetaling.settTilGodkjenning(vedtaksperioder)
                    beregnetPeriode(
                        periode = periode,
                        vilkårsgrunnlagTilutbetaling = vilkårsgrunnlag to utbetaling,
                        utbetalinger = periode.utbetalinger.map { it.second },
                        tidslinjeberegning = tidslinjeberegning,
                        erForkastet = erForkastet(periode.vedtaksperiodeId),
                        vilkårsgrunnlaghistorikk = vilkårsgrunnlaghistorikk
                    )
                }
            }
        }.sortedBy { it.opprettet }
    }

    internal fun toGenerasjoner() = perioder.map {
        Generasjoner.Generasjon(listOf(it))
    }

    private fun uberegnetPeriode(periode: IVedtaksperiode, erForkastet: Boolean): UberegnetPeriode {

        val tidslinje = periode.sykdomstidslinje.merge(emptyList())
        return UberegnetPeriode(
            vedtaksperiodeId = periode.vedtaksperiodeId,
            fom = periode.fom,
            tom = periode.tom,
            sammenslåttTidslinje = tidslinje,
            periodetype = periode.periodetype,
            inntektskilde = periode.inntektskilde,
            erForkastet = erForkastet,
            opprettet = periode.oppdatert,
            skjæringstidspunkt = periode.skjæringstidspunkt,
            periodetilstand = when (periode.tilstand) {
                is AvsluttetUtenUtbetaling -> IngenUtbetaling
                is AvventerRevurdering,
                is AvventerBlokkerendePeriode -> VenterPåAnnenPeriode
                is AvventerHistorikk,
                is AvventerVilkårsprøving -> ForberederGodkjenning
                else -> ManglerInformasjon
            }
        )
    }

    private fun beregnetPeriode(
        periode: IVedtaksperiode,
        vilkårsgrunnlagTilutbetaling: Pair<IVilkårsgrunnlag?, IUtbetaling>,
        utbetalinger: List<IUtbetaling>,
        tidslinjeberegning: ITidslinjeberegning,
        erForkastet: Boolean,
        vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk
    ): BeregnetPeriode {
        val utbetaling = vilkårsgrunnlagTilutbetaling.second
        val sammenslåttTidslinje =
            tidslinjeberegning.sammenslåttTidslinje(utbetaling.utbetalingstidslinje, periode.fom, periode.tom)
        val varsler = PeriodeVarslerBuilder(
            periode.aktivitetsloggForPeriode
        ).build()
        val utbetalingDTO = utbetaling.toDTO()

        val vilkårsgrunnlag = vilkårsgrunnlagTilutbetaling.first
        vilkårsgrunnlaghistorikk.leggIBøtta(vilkårsgrunnlag)
        val vilkårsgrunnlagId = vilkårsgrunnlag?.id
        return BeregnetPeriode(
            vedtaksperiodeId = periode.vedtaksperiodeId,
            beregningId = utbetaling.beregningId,
            fom = periode.fom,
            tom = periode.tom,
            erForkastet = erForkastet,
            periodetype = periode.periodetype,
            inntektskilde = periode.inntektskilde,
            skjæringstidspunkt = periode.skjæringstidspunkt,
            hendelser = periode.hendelser,
            maksdato = utbetaling.maksdato,
            opprettet = utbetaling.opprettet,
            periodevilkår = periodevilkår(periode, utbetaling, sammenslåttTidslinje, periode.hendelser),
            sammenslåttTidslinje = sammenslåttTidslinje,
            gjenståendeSykedager = utbetaling.gjenståendeSykedager,
            forbrukteSykedager = utbetaling.forbrukteSykedager,
            utbetaling = utbetalingDTO,
            vilkårsgrunnlagId = vilkårsgrunnlagId,
            aktivitetslogg = varsler,
            periodetilstand = when {
                utbetalingDTO.erAnnullering() -> if (utbetalingDTO.status != Utbetalingstatus.Annullert) TilAnnullering else Annullert
                utbetalingDTO.revurderingFeilet(periode.tilstand) -> when {
                    utbetalinger.all { it.toDTO().revurderingFeilet(periode.tilstand) } && erForkastet -> Annullert
                    else -> Periodetilstand.RevurderingFeilet
                }
                utbetalingDTO.kanUtbetales() -> when {
                    utbetalingDTO.venterPåRevurdering(periode.tilstand) && utbetalingDTO.ikkeBetalt() -> VenterPåAnnenPeriode
                    utbetalingDTO.venterPåRevurdering(periode.tilstand) && !utbetalingDTO.utbetales() -> UtbetaltVenterPåAnnenPeriode
                    utbetalingDTO.utbetalt() -> if (sammenslåttTidslinje.inneholderSykepengedager()) Utbetalt else IngenUtbetaling
                    utbetalingDTO.utbetales() -> Periodetilstand.TilUtbetaling
                    utbetalingDTO.tilGodkjenning() -> TilGodkjenning
                    !iVentetilstand(periode.tilstand) -> ForberederGodkjenning
                    else -> VenterPåAnnenPeriode
                }
                else -> ManglerInformasjon
            },
        )
    }

    private fun iVentetilstand(tilstand: Vedtaksperiodetilstand) = tilstand in listOf(
        AvventerBlokkerendePeriode,
        AvventerGjennomførtRevurdering,
        AvventerRevurdering
    )

    private fun List<SammenslåttDag>.sisteNavDag() =
        lastOrNull { it.utbetalingstidslinjedagtype == UtbetalingstidslinjedagType.NavDag }

    private fun periodevilkår(
        periode: IVedtaksperiode,
        utbetaling: IUtbetaling,
        sammenslåttTidslinje: List<SammenslåttDag>,
        hendelser: List<HendelseDTO>
    ): BeregnetPeriode.Vilkår {
        val sisteSykepengedag = sammenslåttTidslinje.sisteNavDag()?.dagen ?: periode.tom
        val sykepengedager = BeregnetPeriode.Sykepengedager(
            periode.skjæringstidspunkt,
            utbetaling.maksdato,
            utbetaling.forbrukteSykedager,
            utbetaling.gjenståendeSykedager,
            utbetaling.maksdato > sisteSykepengedag
        )
        val alderSisteSykepengedag = alder.let {
            BeregnetPeriode.Alder(it.alderPåDato(sisteSykepengedag), it.innenfor70årsgrense(sisteSykepengedag))
        }
        val søknadsfrist = hendelser.finn<SøknadNavDTO>()?.let {
            BeregnetPeriode.Søknadsfrist(
                sendtNav = it.sendtNav,
                søknadFom = it.fom,
                søknadTom = it.tom,
                oppfylt = it.søknadsfristOppfylt()
            )
        }

        return BeregnetPeriode.Vilkår(sykepengedager, alderSisteSykepengedag, søknadsfrist)
    }
}

internal class IVedtaksperiode(
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val inntektskilde: Inntektskilde,
    val hendelser: List<HendelseDTO>,
    utbetalinger: List<Pair<IVilkårsgrunnlag?, IUtbetaling>>,
    val periodetype: Periodetype,
    val sykdomstidslinje: List<Sykdomstidslinjedag>,
    val oppdatert: LocalDateTime,
    val tilstand: Vedtaksperiodetilstand,
    val skjæringstidspunkt: LocalDate,
    val aktivitetsloggForPeriode: Aktivitetslogg
) {
    val utbetalinger = utbetalinger.toMutableList()

    fun håndterAnnullering(annulleringer: AnnulleringerAkkumulator) {
        utbetalinger.firstOrNull()?.also {
            annulleringer.finnAnnullering(it.second)?.let { annullering ->
                utbetalinger.add(null to annullering)
            }
        }
    }

    fun tilGodkjenning() = tilstand in listOf(AvventerGodkjenning, AvventerGodkjenningRevurdering)

    internal companion object {
        fun List<IVedtaksperiode>.tilGodkjenning(utbetaling: IUtbetaling) =
            any { periode -> periode.utbetalinger.any { it.second.erSammeSom(utbetaling) } && periode.tilGodkjenning() }
    }
}

internal class IUtbetaling(
    val id: UUID,
    private val korrelasjonsId: UUID,
    val beregningId: BeregningId,
    val opprettet: LocalDateTime,
    val utbetalingstidslinje: List<Utbetalingstidslinjedag>,
    val maksdato: LocalDate,
    val gjenståendeSykedager: Int?,
    val forbrukteSykedager: Int?,
    private val type: String,
    tilstand: no.nav.helse.utbetalingslinjer.Utbetalingstatus,
    private val arbeidsgiverNettoBeløp: Int,
    private val personNettoBeløp: Int,
    private val arbeidsgiverFagsystemId: String,
    private val personFagsystemId: String,
    private val vurdering: Utbetaling.Vurdering?,
    private val oppdrag: Map<String, SpeilOppdrag>
) {
    private val status = when (tilstand) {
        IKKE_UTBETALT -> Utbetalingstatus.IkkeUtbetalt
        IKKE_GODKJENT -> Utbetalingstatus.IkkeGodkjent
        GODKJENT -> Utbetalingstatus.Godkjent
        AVVENTER_ARBEIDSGIVERKVITTERING,
        AVVENTER_PERSONKVITTERING,
        AVVENTER_KVITTERINGER -> Utbetalingstatus.Overført
        UTBETALT -> Utbetalingstatus.Utbetalt
        GODKJENT_UTEN_UTBETALING -> Utbetalingstatus.GodkjentUtenUtbetaling
        ANNULLERT -> Utbetalingstatus.Annullert
        FORKASTET -> Utbetalingstatus.Forkastet
        else -> error("Har ikke mappingregel for $tilstand")
    }
    private var erTilGodkjenning = false
    fun erSammeSom(other: IUtbetaling) = id == other.id
    fun fagsystemId() = arbeidsgiverFagsystemId
    fun hørerSammen(other: IUtbetaling) = korrelasjonsId == other.korrelasjonsId
    fun forkastet() = status == Utbetalingstatus.Forkastet

    fun settTilGodkjenning(vedtaksperioder: List<IVedtaksperiode>) {
        erTilGodkjenning = vedtaksperioder.tilGodkjenning(this)
    }

    fun toDTO(): Utbetaling {
        return Utbetaling(
            type = Utbetalingtype.valueOf(type),
            status = status,
            arbeidsgiverNettoBeløp = arbeidsgiverNettoBeløp,
            personNettoBeløp = personNettoBeløp,
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            personFagsystemId = personFagsystemId,
            vurdering = vurdering,
            id = id,
            oppdrag = oppdrag,
            tilGodkjenning = erTilGodkjenning,
            korrelasjonsId = korrelasjonsId
        )
    }

    internal companion object {
        internal fun Map<UUID, IUtbetaling>.leggTil(utbetaling: IUtbetaling): Map<UUID, IUtbetaling> {
            return this.toMutableMap().also {
                it.putIfAbsent(utbetaling.korrelasjonsId, utbetaling)
            }
        }
    }
}

private fun List<SammenslåttDag>.inneholderSykepengedager(): Boolean =
    any { it.utbetalingstidslinjedagtype == UtbetalingstidslinjedagType.NavDag }