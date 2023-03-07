package no.nav.helse.serde.api.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.helse.person.Inntektskilde
import no.nav.helse.person.Periodetype
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.serde.api.dto.Periodetilstand.ForberederGodkjenning
import no.nav.helse.serde.api.dto.Periodetilstand.ManglerInformasjon
import no.nav.helse.serde.api.dto.Periodetilstand.Utbetalt
import no.nav.helse.serde.api.dto.Periodetilstand.VenterPåAnnenPeriode
import no.nav.helse.serde.api.speil.builders.BeregningId
import no.nav.helse.serde.api.speil.builders.KorrelasjonsId

data class Generasjon(
    val id: UUID, // Runtime
    val perioder: List<Tidslinjeperiode>
)

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
    TilGodkjenning,
    IngenUtbetaling,
    TilInfotrygd;
}

data class Utbetalingsinfo(
    val inntekt: Int? = null,
    val utbetaling: Int? = null,
    val personbeløp: Int? = null,
    val arbeidsgiverbeløp: Int? = null,
    val refusjonsbeløp: Int? = null,
    val totalGrad: Double? = null
)

interface Tidslinjeperiode {
    // Brukes i Speil for å kunne korrelere tidslinje-komponenten og saksbildet. Trenger ikke være persistent på tvers av snapshots.
    val tidslinjeperiodeId: UUID
    val vedtaksperiodeId: UUID
    val fom: LocalDate
    val tom: LocalDate
    val sammenslåttTidslinje: List<SammenslåttDag>
    val periodetype: Periodetype
    val inntektskilde: Inntektskilde
    val erForkastet: Boolean
    val opprettet: LocalDateTime
    val periodetilstand: Periodetilstand
    val skjæringstidspunkt: LocalDate

    fun erSammeVedtaksperiode(other: Tidslinjeperiode) = vedtaksperiodeId == other.vedtaksperiodeId
    fun venter() = periodetilstand in setOf(VenterPåAnnenPeriode, ForberederGodkjenning, ManglerInformasjon)
}

data class UberegnetPeriode(
    override val vedtaksperiodeId: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val periodetype: Periodetype,
    override val inntektskilde: Inntektskilde,
    override val erForkastet: Boolean,
    override val opprettet: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate
) : Tidslinjeperiode {
    override val tidslinjeperiodeId: UUID = UUID.randomUUID()
    override fun toString(): String {
        return "${fom.format()}-${tom.format()} - $periodetilstand"
    }
    private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

// Dekker datagrunnlaget vi trenger for å populere både pølsen og _hele_ saksbildet
data class BeregnetPeriode(
    override val vedtaksperiodeId: UUID,
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val sammenslåttTidslinje: List<SammenslåttDag>,
    override val erForkastet: Boolean,
    override val periodetype: Periodetype,
    override val inntektskilde: Inntektskilde,
    override val opprettet: LocalDateTime,
    override val periodetilstand: Periodetilstand,
    override val skjæringstidspunkt: LocalDate,
    val beregningId: BeregningId,
    val gjenståendeSykedager: Int?,
    val forbrukteSykedager: Int?,
    val maksdato: LocalDate,
    val utbetaling: Utbetaling,
    val hendelser: List<HendelseDTO>,
    val periodevilkår: Vilkår,
    val aktivitetslogg: List<AktivitetDTO>,
    val vilkårsgrunnlagId: UUID?
) : Tidslinjeperiode, Comparable<BeregnetPeriode> {
    override val tidslinjeperiodeId: UUID = UUID.randomUUID()

    override fun venter(): Boolean = super.venter() && periodetilstand != Utbetalt

    internal fun erAnnullering() = utbetaling.type == Utbetalingtype.ANNULLERING
    internal fun erRevurdering() = utbetaling.type == Utbetalingtype.REVURDERING
    internal fun hørerSammen(other: Tidslinjeperiode) = other is BeregnetPeriode && utbetaling.hørerSammen(other.utbetaling)

    override fun compareTo(other: BeregnetPeriode) = tom.compareTo(other.tom)
    override fun toString(): String {
        return "${fom.format()}-${tom.format()} - $periodetilstand - ${utbetaling.type}"
    }
    private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    data class Vilkår(
        val sykepengedager: Sykepengedager,
        val alder: Alder,
        val søknadsfrist: Søknadsfrist?
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

    data class Søknadsfrist(
        val sendtNav: LocalDateTime,
        val søknadFom: LocalDate,
        val søknadTom: LocalDate,
        val oppfylt: Boolean
    )
}

data class SpeilOppdrag(
    val fagsystemId: String,
    val tidsstempel: LocalDateTime,
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
    Forkastet,
    Godkjent,
    GodkjentUtenUtbetaling,
    IkkeGodkjent,
    Overført,
    IkkeUtbetalt,
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
    val type: Utbetalingtype,
    val status: Utbetalingstatus,
    val arbeidsgiverNettoBeløp: Int,
    val personNettoBeløp: Int,
    val arbeidsgiverFagsystemId: String,
    val personFagsystemId: String,
    val oppdrag: Map<String, SpeilOppdrag>,
    val vurdering: Vurdering?,
    val id: UUID,
    val tilGodkjenning: Boolean,
    private val korrelasjonsId: KorrelasjonsId
) {
    fun erAnnullering() = type == Utbetalingtype.ANNULLERING
    private fun erForkastetRevurdering() = status == Utbetalingstatus.Forkastet && type == Utbetalingtype.REVURDERING
    fun utbetales() = status in listOf(Utbetalingstatus.Overført)
    fun ikkeBetalt() = status == Utbetalingstatus.IkkeUtbetalt
    fun utbetalt() = status in listOf(Utbetalingstatus.Utbetalt, Utbetalingstatus.GodkjentUtenUtbetaling)
    fun kanUtbetales() = !erForkastetRevurdering()
    fun tilGodkjenning() = tilGodkjenning

    fun hørerSammen(other: Utbetaling) = korrelasjonsId == other.korrelasjonsId

    internal fun revurderingFeilet(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) = (erForkastetRevurdering() || status in setOf(Utbetalingstatus.IkkeGodkjent, Utbetalingstatus.IkkeUtbetalt)) && tilstand == Vedtaksperiode.RevurderingFeilet
    internal fun venterPåRevurdering(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) = tilstand == Vedtaksperiode.AvventerRevurdering

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
