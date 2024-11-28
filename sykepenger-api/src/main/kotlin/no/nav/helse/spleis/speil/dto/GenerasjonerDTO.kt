package no.nav.helse.spleis.speil.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.helse.dto.BeløpstidslinjeDto
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.spleis.speil.builders.ISpleisGrunnlag
import no.nav.helse.spleis.speil.builders.IVilkårsgrunnlagHistorikk
import no.nav.helse.spleis.speil.dto.Periodetilstand.IngenUtbetaling

data class SpeilGenerasjonDTO(
    val id: UUID, // Runtime
    val perioder: List<SpeilTidslinjeperiode>,
    val kildeTilGenerasjon: UUID
) {
    val size = perioder.size
}

enum class Periodetilstand {
    TilUtbetaling,
    TilAnnullering,
    Utbetalt,
    Annullert,
    AnnulleringFeilet,
    RevurderingFeilet,
    ForberederGodkjenning,
    ManglerInformasjon,
    UtbetaltVenterPåAnnenPeriode,
    VenterPåAnnenPeriode,
    AvventerInntektsopplysninger,
    TilGodkjenning,
    IngenUtbetaling,
    TilInfotrygd;
}

data class Utbetalingsinfo(
    val personbeløp: Int? = null,
    val arbeidsgiverbeløp: Int? = null,
    val totalGrad: Int // Speil vises grad i heltall
) {
    fun harUtbetaling() = personbeløp != null || arbeidsgiverbeløp != null
}

enum class Tidslinjeperiodetype {
    FØRSTEGANGSBEHANDLING,
    FORLENGELSE,
    OVERGANG_FRA_IT,
    INFOTRYGDFORLENGELSE;
}

sealed class SpeilTidslinjeperiode : Comparable<SpeilTidslinjeperiode> {
    abstract val vedtaksperiodeId: UUID
    abstract val behandlingId: UUID
    abstract val kilde: UUID
    abstract val fom: LocalDate
    abstract val tom: LocalDate
    abstract val sammenslåttTidslinje: List<SammenslåttDag>
    abstract val periodetype: Tidslinjeperiodetype
    abstract val inntektskilde: UtbetalingInntektskilde
    abstract val erForkastet: Boolean
    abstract val opprettet: LocalDateTime
    abstract val oppdatert: LocalDateTime
    abstract val periodetilstand: Periodetilstand
    abstract val skjæringstidspunkt: LocalDate
    abstract val hendelser: Set<UUID>

    internal open fun registrerBruk(vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk, organisasjonsnummer: String): SpeilTidslinjeperiode {
        return this
    }

    internal abstract fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode

    override fun compareTo(other: SpeilTidslinjeperiode) = tom.compareTo(other.tom)
    internal open fun medOpplysningerFra(other: UberegnetPeriode): UberegnetPeriode? = null

    internal companion object {
        fun List<SpeilTidslinjeperiode>.utledPeriodetyper(): List<SpeilTidslinjeperiode> {
            val out = mutableListOf<SpeilTidslinjeperiode>()
            val sykefraværstilfeller = this.sortedBy { it.fom }.groupBy { it.skjæringstidspunkt }
            sykefraværstilfeller.forEach { (_, perioder) ->
                out.add(perioder.first().medPeriodetype(Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING))
                perioder.zipWithNext { forrige, nåværende ->
                    if (forrige is BeregnetPeriode) out.add(nåværende.medPeriodetype(Tidslinjeperiodetype.FORLENGELSE))
                    else out.add(nåværende.medPeriodetype(Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING))
                }
            }
            return out.sortedByDescending { it.fom }
        }
    }
}

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private fun LocalDate.format() = format(formatter)

data class UberegnetPeriode(
    override val vedtaksperiodeId: UUID,
    override val behandlingId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val periodetype: Tidslinjeperiodetype, // feltet gir ikke mening for uberegnede perioder
    override val inntektskilde: UtbetalingInntektskilde, // feltet gir ikke mening for uberegnede perioder
    override val erForkastet: Boolean,
    override val opprettet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate,
    override val hendelser: Set<UUID>
) : SpeilTidslinjeperiode() {
    override fun toString(): String {
        return "${fom.format()}-${tom.format()} - $periodetilstand"
    }

    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this.copy(periodetype = periodetype)
    }

    override fun medOpplysningerFra(other: UberegnetPeriode): UberegnetPeriode? {
        // kopierer bare -like- generasjoner; om en periode er strukket tilbake så bevarer vi generasjonen
        if (this.fom != other.fom) return null
        if (this.periodetilstand == IngenUtbetaling && other.periodetilstand != IngenUtbetaling) return null
        return this.copy(
            hendelser = this.hendelser + other.hendelser,
            sammenslåttTidslinje = other.sammenslåttTidslinje
        )
    }
}

// Dekker datagrunnlaget vi trenger for å populere både pølsen og _hele_ saksbildet
data class BeregnetPeriode(
    override val vedtaksperiodeId: UUID,
    override val behandlingId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val erForkastet: Boolean, // feltet trengs ikke i speil
    override val periodetype: Tidslinjeperiodetype,
    override val inntektskilde: UtbetalingInntektskilde, // verdien av dette feltet brukes bare for å sjekke !=null i speil
    override val opprettet: LocalDateTime,
    val behandlingOpprettet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate,
    override val hendelser: Set<UUID>,
    // todo: feltet brukes så og si ikke i speil, kan fjernes fra graphql
    // verdien av ID-en brukes ifm. å lage en unik ID for notatet om utbetalingene.
    val beregningId: UUID,
    val utbetaling: Utbetaling,
    val periodevilkår: Vilkår,
    val vilkårsgrunnlagId: UUID,
    val refusjonstidslinje: BeløpstidslinjeDto
) : SpeilTidslinjeperiode() {
    override fun registrerBruk(vilkårsgrunnlaghistorikk: IVilkårsgrunnlagHistorikk, organisasjonsnummer: String): BeregnetPeriode {
        val vilkårsgrunnlag = vilkårsgrunnlagId.let { vilkårsgrunnlaghistorikk.leggIBøtta(it) }
        vilkårsgrunnlaghistorikk.medRefusjonstidslinje(vilkårsgrunnlagId, refusjonstidslinje, organisasjonsnummer)
        if (vilkårsgrunnlag !is ISpleisGrunnlag) return this
        return this.copy(hendelser = this.hendelser + vilkårsgrunnlag.overstyringer)
    }

    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this.copy(periodetype = periodetype)
    }

    override fun toString(): String {
        return "${fom.format()}-${tom.format()} - $periodetilstand - ${utbetaling.type}"
    }

    data class Vilkår(
        val sykepengedager: Sykepengedager,
        val alder: Alder
    )

    data class Sykepengedager(
        val skjæringstidspunkt: LocalDate,
        val maksdato: LocalDate,
        val forbrukteSykedager: Int?,
        val gjenståendeDager: Int?,
        val oppfylt: Boolean
    )

    data class Alder(
        val alderSisteSykedag: Int,
        val oppfylt: Boolean
    )
}

data class AnnullertPeriode(
    override val vedtaksperiodeId: UUID,
    override val behandlingId: UUID,
    override val kilde: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val opprettet: LocalDateTime,
    val vilkår: BeregnetPeriode.Vilkår, // feltet gir ikke mening for annullert periode
    val beregnet: LocalDateTime,
    override val oppdatert: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val hendelser: Set<UUID>,

    // todo: feltet brukes så og si ikke i speil, kan fjernes fra graphql
    // verdien av ID-en brukes ifm. å lage en unik ID for notatet om utbetalingene.
    val beregningId: UUID,
    val utbetaling: Utbetaling
) : SpeilTidslinjeperiode() {
    override val sammenslåttTidslinje: List<SammenslåttDag> = emptyList() // feltet gir ikke mening for annullert periode
    override val erForkastet = true
    override val skjæringstidspunkt = fom // feltet gir ikke mening for annullert periode
    override val periodetype =
        Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING // feltet gir ikke mening for annullert periode
    override val inntektskilde = UtbetalingInntektskilde.EN_ARBEIDSGIVER // feltet gir ikke mening for annullert periode
    override fun medPeriodetype(periodetype: Tidslinjeperiodetype): SpeilTidslinjeperiode {
        return this
    }
}

internal class AnnullertUtbetaling(
    internal val id: UUID,
    internal val korrelasjonsId: UUID,
    internal val annulleringstidspunkt: LocalDateTime,
    internal val arbeidsgiverFagsystemId: String,
    internal val personFagsystemId: String,
    internal val utbetalingstatus: Utbetalingstatus
) {
    val periodetilstand = when (utbetalingstatus) {
        Utbetalingstatus.Annullert -> Periodetilstand.Annullert
        else -> Periodetilstand.TilAnnullering
    }

    fun annullerer(korrelasjonsId: UUID) = this.korrelasjonsId == korrelasjonsId

}

data class SpeilOppdrag(
    val fagsystemId: String,
    val tidsstempel: LocalDateTime,
    val nettobeløp: Int,
    val simulering: Simulering?,
    val utbetalingslinjer: List<Utbetalingslinje>
) {
    data class Simulering(
        val totalbeløp: Int,
        val perioder: List<Simuleringsperiode>
    )

    data class Simuleringsperiode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetalinger: List<Simuleringsutbetaling>
    )

    data class Simuleringsutbetaling(
        val mottakerId: String,
        val mottakerNavn: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Simuleringsdetaljer>
    )

    data class Simuleringsdetaljer(
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val konto: String,
        val beløp: Int,
        val tilbakeføring: Boolean,
        val sats: Double,
        val typeSats: String,
        val antallSats: Int,
        val uføregrad: Int,
        val klassekode: String,
        val klassekodeBeskrivelse: String,
        val utbetalingstype: String,
        val refunderesOrgNr: String
    )

    data class Utbetalingslinje(
        val fom: LocalDate,
        val tom: LocalDate,
        val dagsats: Int,
        val grad: Int,
        val endringskode: EndringskodeDTO
    )
}

enum class Utbetalingstatus {
    Annullert,
    Godkjent,
    GodkjentUtenUtbetaling,
    IkkeGodkjent,
    Overført,
    Ubetalt,
    Utbetalt
}

enum class Utbetalingtype {
    UTBETALING,
    ETTERUTBETALING,
    ANNULLERING,
    REVURDERING,
    FERIEPENGER
}

class Utbetaling(
    val id: UUID,
    val type: Utbetalingtype,
    val korrelasjonsId: UUID,
    val maksdato: LocalDate,
    val forbrukteSykedager: Int,
    val gjenståendeDager: Int,
    val status: Utbetalingstatus,
    val arbeidsgiverNettoBeløp: Int,
    val personNettoBeløp: Int,
    val arbeidsgiverFagsystemId: String,
    val personFagsystemId: String,
    val oppdrag: Map<String, SpeilOppdrag>,
    val vurdering: Vurdering?
) {
    data class Vurdering(
        val godkjent: Boolean,
        val tidsstempel: LocalDateTime,
        val automatisk: Boolean,
        val ident: String
    )
}

data class Refusjon(
    val arbeidsgiverperioder: List<Periode>,
    val endringer: List<Endring>,
    val førsteFraværsdag: LocalDate?,
    val sisteRefusjonsdag: LocalDate?,
    val beløp: Double?,
) {
    data class Periode(
        val fom: LocalDate,
        val tom: LocalDate
    )

    data class Endring(
        val beløp: Double,
        val dato: LocalDate
    )
}
