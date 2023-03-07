package no.nav.helse.spleis.graphql.dto

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.serde.api.dto.Utbetalingtype

internal fun SchemaBuilder.tidslinjeperiodeTypes() {
    enum<GraphQLInntektstype>()
    enum<GraphQLPeriodetype>()
    enum<GraphQLSykdomsdagtype>()
    enum<GraphQLUtbetalingsdagType>()
    enum<GraphQLSykdomsdagkildetype>()
    enum<GraphQLBegrunnelse>()
    enum<GraphQLPeriodetilstand>()
    enum<Utbetalingtype>()
    enum<GraphQLUtbetalingstatus>()

    type<GraphQLSykdomsdagkilde>()
    type<GraphQLUtbetalingsinfo>()
    type<GraphQLVurdering>()
    type<GraphQLUtbetaling>() {
        property(GraphQLUtbetaling::type) {
            deprecate("Burde bruke enum \"typeEnum\"")
        }
        property(GraphQLUtbetaling::status) {
            deprecate("Burde bruke enum \"statusEnum\"")
        }
    }
    type<GraphQLDag>()
    type<GraphQLTidslinjeperiode>()
    type<GraphQLUberegnetPeriode>()
    type<GraphQLAktivitet>()
    type<GraphQLPeriodevilkar>()
    type<GraphQLPeriodevilkar.Sykepengedager>()
    type<GraphQLPeriodevilkar.Alder>()
    type<GraphQLPeriodevilkar.Soknadsfrist>()
    type<GraphQLBeregnetPeriode>() {
        property<List<GraphQLInntektsmelding>>("inntektsmeldinger") {
            resolver { periode ->
                periode.hendelser.filterIsInstance<GraphQLInntektsmelding>()
            }
        }
        property<List<GraphQLSoknadNav>>("soknaderNav") {
            resolver { periode ->
                periode.hendelser.filterIsInstance<GraphQLSoknadNav>()
            }
        }
        property<List<GraphQLSoknadArbeidsgiver>>("soknaderArbeidsgiver") {
            resolver { periode ->
                periode.hendelser.filterIsInstance<GraphQLSoknadArbeidsgiver>()
            }
        }
        property<List<GraphQLSykmelding>>("sykmeldinger") {
            resolver { periode ->
                periode.hendelser.filterIsInstance<GraphQLSykmelding>()
            }
        }
    }
}

enum class GraphQLInntektstype {
    EnArbeidsgiver,
    FlereArbeidsgivere
}

enum class GraphQLPeriodetype {
    Forstegangsbehandling,
    Forlengelse,
    OvergangFraIt,
    Infotrygdforlengelse;
}

enum class GraphQLSykdomsdagtype {
    Arbeidsdag,
    Arbeidsgiverdag,
    Feriedag,
    ForeldetSykedag,
    FriskHelgedag,
    Permisjonsdag,
    Sykedag,
    SykHelgedag,
    Ubestemtdag,
    Avslatt
}

enum class GraphQLUtbetalingsdagType {
    ArbeidsgiverperiodeDag,
    NavDag,
    NavHelgDag,
    Helgedag,
    Arbeidsdag,
    Feriedag,
    AvvistDag,
    UkjentDag,
    ForeldetDag
}

enum class GraphQLSykdomsdagkildetype {
    Inntektsmelding,
    Soknad,
    Sykmelding,
    Saksbehandler,
    Ukjent
}

enum class GraphQLBegrunnelse {
    SykepengedagerOppbrukt,
    SykepengedagerOppbruktOver67,
    MinimumInntekt,
    MinimumInntektOver67,
    EgenmeldingUtenforArbeidsgiverperiode,
    MinimumSykdomsgrad,
    EtterDodsdato,
    ManglerMedlemskap,
    ManglerOpptjening,
    Over70
}

enum class GraphQLPeriodetilstand {
    TilUtbetaling,
    TilAnnullering,
    Utbetalt,
    Annullert,
    AnnulleringFeilet,
    IngenUtbetaling,
    RevurderingFeilet,
    TilInfotrygd,
    UtbetalingFeilet,
    ForberederGodkjenning,
    ManglerInformasjon,
    VenterPaAnnenPeriode,
    UtbetaltVenterPaAnnenPeriode,
    TilGodkjenning;
}

enum class GraphQLUtbetalingstatus {
    Annullert,
    Forkastet,
    Godkjent,
    GodkjentUtenUtbetaling,
    IkkeGodkjent,
    Overfort,
    Ubetalt,
    Utbetalt
}

data class GraphQLSykdomsdagkilde(
    val id: UUID,
    val type: GraphQLSykdomsdagkildetype
)

data class GraphQLUtbetalingsinfo(
    val inntekt: Int?,
    val utbetaling: Int?,
    val personbelop: Int?,
    val arbeidsgiverbelop: Int?,
    val refusjonsbelop: Int?,
    val totalGrad: Double?
)

data class GraphQLVurdering(
    val godkjent: Boolean,
    val tidsstempel: LocalDateTime,
    val automatisk: Boolean,
    val ident: String
)

data class GraphQLUtbetalingslinje(
    val fom: LocalDate,
    val tom: LocalDate,
    val dagsats: Int,
    val grad: Int
)

data class GraphQLOppdrag(
    val fagsystemId: String,
    val tidsstempel: LocalDateTime,
    val simulering: GraphQLSimulering?,
    val utbetalingslinjer: List<GraphQLUtbetalingslinje>
)

data class GraphQLUtbetaling(
    val id: UUID,
    val type: String,
    val typeEnum: Utbetalingtype,
    val status: String,
    val statusEnum: GraphQLUtbetalingstatus,
    val arbeidsgiverNettoBelop: Int,
    val personNettoBelop: Int,
    val arbeidsgiverFagsystemId: String,
    val personFagsystemId: String,
    val arbeidsgiveroppdrag: GraphQLOppdrag?,
    val personoppdrag: GraphQLOppdrag?,
    val vurdering: GraphQLVurdering?
)

data class GraphQLDag(
    val dato: LocalDate,
    val sykdomsdagtype: GraphQLSykdomsdagtype,
    val utbetalingsdagtype: GraphQLUtbetalingsdagType,
    val kilde: GraphQLSykdomsdagkilde,
    val grad: Double?,
    val utbetalingsinfo: GraphQLUtbetalingsinfo?,
    val begrunnelser: List<GraphQLBegrunnelse>?
)

interface GraphQLTidslinjeperiode {
    val id: UUID
    val fom: LocalDate
    val tom: LocalDate
    val tidslinje: List<GraphQLDag>
    val periodetype: GraphQLPeriodetype
    val inntektstype: GraphQLInntektstype
    val erForkastet: Boolean
    val opprettet: LocalDateTime
    val vedtaksperiodeId: UUID
    val periodetilstand: GraphQLPeriodetilstand
    val skjaeringstidspunkt: LocalDate
}

data class GraphQLUberegnetPeriode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val tidslinje: List<GraphQLDag>,
    override val periodetype: GraphQLPeriodetype,
    override val inntektstype: GraphQLInntektstype,
    override val erForkastet: Boolean,
    override val opprettet: LocalDateTime,
    override val vedtaksperiodeId: UUID,
    override val periodetilstand: GraphQLPeriodetilstand,
    override val skjaeringstidspunkt: LocalDate
) : GraphQLTidslinjeperiode {
    override val id: UUID = UUID.randomUUID()
}

data class GraphQLAktivitet(
    val vedtaksperiodeId: UUID,
    val alvorlighetsgrad: String,
    val melding: String,
    val tidsstempel: String
)

data class GraphQLPeriodevilkar(
    val sykepengedager: Sykepengedager,
    val alder: Alder,
    val soknadsfrist: Soknadsfrist?
) {
    data class Sykepengedager(
        val skjaeringstidspunkt: LocalDate,
        val maksdato: LocalDate,
        val forbrukteSykedager: Int?,
        val gjenstaendeSykedager: Int?,
        val oppfylt: Boolean
    )

    data class Alder(
        val alderSisteSykedag: Int,
        val oppfylt: Boolean
    )

    data class Soknadsfrist(
        val sendtNav: LocalDateTime,
        val soknadFom: LocalDate,
        val soknadTom: LocalDate,
        val oppfylt: Boolean
    )
}

data class GraphQLBeregnetPeriode(
    override val fom: LocalDate,
    override val tom: LocalDate,
    override val tidslinje: List<GraphQLDag>,
    override val periodetype: GraphQLPeriodetype,
    override val inntektstype: GraphQLInntektstype,
    override val erForkastet: Boolean,
    override val opprettet: LocalDateTime,
    override val vedtaksperiodeId: UUID,
    override val periodetilstand: GraphQLPeriodetilstand,
    override val skjaeringstidspunkt: LocalDate,
    val beregningId: UUID,
    val gjenstaendeSykedager: Int?,
    val forbrukteSykedager: Int?,
    val maksdato: LocalDate,
    val utbetaling: GraphQLUtbetaling,
    val hendelser: List<GraphQLHendelse>,
    val periodevilkar: GraphQLPeriodevilkar,
    val aktivitetslogg: List<GraphQLAktivitet>,
    val vilkarsgrunnlagId: UUID?
) : GraphQLTidslinjeperiode {
    override val id: UUID = UUID.randomUUID()
}
