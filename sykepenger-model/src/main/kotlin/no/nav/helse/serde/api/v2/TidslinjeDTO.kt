package no.nav.helse.serde.api.v2

import no.nav.helse.serde.api.BegrunnelseDTO
import java.time.LocalDate
import java.util.*

data class SammenslåttDag(
    val dagen: LocalDate,
    val sykdomstidslinjedagtype: SykdomstidslinjedagType,
    val utbetalingsdagtype: UtbetalingstidslinjeDagtype,
    val kilde: Sykdomstidslinjedag.SykdomstidslinjedagKilde,
    val grad: Double? = null,
    val utbetalingsinfo: Utbetalingsinfo? = null,
    val begrunnelser: List<BegrunnelseDTO>? = null,
)

enum class SykdomstidslinjedagType {
    ARBEIDSDAG,
    ARBEIDSGIVERDAG,
    FERIEDAG,
    FORELDET_SYKEDAG,
    FRISK_HELGEDAG,
    PERMISJONSDAG,
    SYKEDAG,
    SYK_HELGEDAG,
    UBESTEMTDAG,
    AVSLÅTT
}

enum class SykdomstidslinjedagKildetype {
    Inntektsmelding,
    Søknad,
    Sykmelding,
    Saksbehandler,
    Ukjent
}

data class Sykdomstidslinjedag(
    val dagen: LocalDate,
    val type: SykdomstidslinjedagType,
    val kilde: SykdomstidslinjedagKilde,
    val grad: Double? = null
) {
    data class SykdomstidslinjedagKilde(
        val type: SykdomstidslinjedagKildetype,
        val kildeId: UUID
    )
}

enum class UtbetalingstidslinjeDagtype {
    ArbeidsgiverperiodeDag,
    NavDag,
    NavHelgDag,
    Helgedag,   // SpeilBuilder only code breakout of Fridag
    Arbeidsdag,
    Feriedag,   // SpeilBuilder only code breakout of Fridag
    AvvistDag,
    UkjentDag,
    ForeldetDag
}

interface Utbetalingstidslinjedag {
    val type: UtbetalingstidslinjeDagtype
    val inntekt: Int
    val dato: LocalDate
}

data class NavDag(
    override val type: UtbetalingstidslinjeDagtype = UtbetalingstidslinjeDagtype.NavDag,
    override val inntekt: Int,
    override val dato: LocalDate,
    val utbetaling: Int,
    val grad: Double,
    val totalGrad: Double?
) : Utbetalingstidslinjedag

data class AvvistDag(
    override val type: UtbetalingstidslinjeDagtype = UtbetalingstidslinjeDagtype.AvvistDag,
    override val inntekt: Int,
    override val dato: LocalDate,
    val begrunnelser: List<BegrunnelseDTO>,
    val grad: Double
) : Utbetalingstidslinjedag

data class Utbetalingsdag(
    override val type: UtbetalingstidslinjeDagtype,
    override val inntekt: Int,
    override val dato: LocalDate
) : Utbetalingstidslinjedag

data class UtbetalingsdagMedGrad(
    override val type: UtbetalingstidslinjeDagtype,
    override val inntekt: Int,
    override val dato: LocalDate,
    val grad: Double
) : Utbetalingstidslinjedag
