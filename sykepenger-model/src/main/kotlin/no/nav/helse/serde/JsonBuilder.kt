package no.nav.helse.serde

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.*
import no.nav.helse.serde.UtbetalingstidslinjeData.TypeData
import no.nav.helse.serde.mapping.NyJsonDagType.*
import no.nav.helse.serde.reflection.*
import no.nav.helse.sykdomstidslinje.NyDag
import no.nav.helse.sykdomstidslinje.NyDag.*
import no.nav.helse.sykdomstidslinje.NySykdomstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Grad
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun Person.serialize(): SerialisertPerson {
    val jsonBuilder = JsonBuilder()
    this.accept(jsonBuilder)
    return SerialisertPerson(jsonBuilder.toString())
}

internal class JsonBuilder : PersonVisitor {

    private val stack: Stack<JsonState> = Stack()

    init {
        stack.push(Root())
    }

    internal fun toJson() = SerialisertPerson.medSkjemaversjon(currentState.toJson())

    private val currentState: JsonState
        get() = stack.peek()

    override fun toString() = toJson().toString()

    private fun pushState(state: JsonState) {
        currentState.leaving()
        stack.push(state)
        currentState.entering()
    }

    private fun popState() {
        currentState.leaving()
        stack.pop()
        currentState.entering()
    }

    override fun preVisitPerson(
        person: Person,
        aktørId: String,
        fødselsnummer: String
    ) = currentState.preVisitPerson(person, aktørId, fødselsnummer)

    override fun postVisitPerson(
        person: Person,
        aktørId: String,
        fødselsnummer: String
    ) = currentState.postVisitPerson(person, aktørId, fødselsnummer)

    override fun visitPersonAktivitetslogg(aktivitetslogg: Aktivitetslogg) =
        currentState.visitPersonAktivitetslogg(aktivitetslogg)

    override fun preVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) =
        currentState.preVisitArbeidsgiver(arbeidsgiver, id, organisasjonsnummer)

    override fun postVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) =
        currentState.postVisitArbeidsgiver(arbeidsgiver, id, organisasjonsnummer)

    override fun preVisitArbeidsgivere() = currentState.preVisitArbeidsgivere()
    override fun postVisitArbeidsgivere() = currentState.postVisitArbeidsgivere()
    override fun preVisitInntekthistorikk(inntekthistorikk: Inntekthistorikk) =
        currentState.preVisitInntekthistorikk(inntekthistorikk)

    override fun postVisitInntekthistorikk(inntekthistorikk: Inntekthistorikk) {
        currentState.postVisitInntekthistorikk(inntekthistorikk)
    }

    override fun visitInntekt(inntekt: Inntekthistorikk.Inntekt, id: UUID) = currentState.visitInntekt(inntekt, id)
    override fun preVisitTidslinjer(tidslinjer: MutableList<Utbetalingstidslinje>) = currentState.preVisitTidslinjer(tidslinjer)
    override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) =
        currentState.preVisitUtbetalingstidslinje(tidslinje)

    override fun visitArbeidsdag(dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag) =
        currentState.visitArbeidsdag(dag)

    override fun visitArbeidsgiverperiodeDag(dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag) =
        currentState.visitArbeidsgiverperiodeDag(dag)

    override fun visitNavDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) =
        currentState.visitNavDag(dag)

    override fun visitNavHelgDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) =
        currentState.visitNavHelgDag(dag)

    override fun visitFridag(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag) =
        currentState.visitFridag(dag)

    override fun visitAvvistDag(dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) =
        currentState.visitAvvistDag(dag)

    override fun visitUkjentDag(dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag) =
        currentState.visitUkjentDag(dag)

    override fun visitForeldetDag(dag: Utbetalingstidslinje.Utbetalingsdag.ForeldetDag) =
        currentState.visitForeldetDag(dag)

    override fun postVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) =
        currentState.postVisitUtbetalingstidslinje(tidslinje)

    override fun preVisitPerioder() = currentState.preVisitPerioder()
    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        gruppeId: UUID
    ) =
        currentState.preVisitVedtaksperiode(vedtaksperiode, id, gruppeId)

    override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) =
        currentState.preVisitSykdomshistorikk(sykdomshistorikk)

    override fun preVisitSykdomshistorikkElement(
        element: Sykdomshistorikk.Element,
        id: UUID,
        tidsstempel: LocalDateTime
    ) =
        currentState.preVisitSykdomshistorikkElement(element, id, tidsstempel)

    override fun preVisitHendelseSykdomstidslinje(tidslinje: NySykdomstidslinje) =
        currentState.preVisitHendelseSykdomstidslinje(tidslinje)

    override fun postVisitHendelseSykdomstidslinje(tidslinje: NySykdomstidslinje) =
        currentState.postVisitHendelseSykdomstidslinje(tidslinje)

    override fun preVisitBeregnetSykdomstidslinje(tidslinje: NySykdomstidslinje) =
        currentState.preVisitBeregnetSykdomstidslinje(tidslinje)

    override fun postVisitBeregnetSykdomstidslinje(tidslinje: NySykdomstidslinje) =
        currentState.postVisitBeregnetSykdomstidslinje(tidslinje)

    override fun preVisitNySykdomstidslinje(
        tidslinje: NySykdomstidslinje,
        låstePerioder: List<Periode>,
        id: UUID,
        tidsstempel: LocalDateTime
    ) =
        currentState.preVisitNySykdomstidslinje(tidslinje, låstePerioder, id, tidsstempel)

    override fun postVisitNySykdomstidslinje(tidslinje: NySykdomstidslinje, id: UUID, tidsstempel: LocalDateTime) =
        currentState.postVisitNySykdomstidslinje(tidslinje, id, tidsstempel)

    override fun postVisitSykdomshistorikkElement(
        element: Sykdomshistorikk.Element,
        id: UUID,
        tidsstempel: LocalDateTime
    ) =
        currentState.postVisitSykdomshistorikkElement(element, id, tidsstempel)

    override fun postVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        gruppeId: UUID
    ) =
        currentState.postVisitVedtaksperiode(vedtaksperiode, id, gruppeId)

    override fun visitDag(dag: NyUkjentDag, dato: LocalDate, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, kilde)
    override fun visitDag(dag: NyArbeidsdag, dato: LocalDate, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, kilde)
    override fun visitDag(dag: NyArbeidsgiverdag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, grad, kilde)
    override fun visitDag(dag: NyFeriedag, dato: LocalDate, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, kilde)
    override fun visitDag(dag: NyFriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, kilde)
    override fun visitDag(dag: NyArbeidsgiverHelgedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, grad, kilde)
    override fun visitDag(dag: NySykedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, grad, kilde)
    override fun visitDag(dag: NyForeldetSykedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, grad, kilde)
    override fun visitDag(dag: NySykHelgedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
        currentState.visitDag(dag, dato, grad, kilde)
    override fun visitDag(dag: ProblemDag, dato: LocalDate, kilde: Hendelseskilde, melding: String) =
        currentState.visitDag(dag, dato, kilde, melding)

    override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) =
        currentState.visitTilstand(tilstand)

    override fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) =
        currentState.preVisitUtbetalinger(utbetalinger)

    override fun postVisitUtbetalinger(utbetalinger: List<Utbetaling>) =
        currentState.postVisitUtbetalinger(utbetalinger)

    override fun preVisitUtbetaling(utbetaling: Utbetaling, tidsstempel: LocalDateTime) =
        currentState.preVisitUtbetaling(utbetaling, tidsstempel)

    private interface JsonState : PersonVisitor {
        fun entering() {}
        fun leaving() {}
        fun toJson(): JsonNode =
            throw RuntimeException("toJson() kan bare kalles på rotnode. Ble kalt på ${toString()}")
    }

    private inner class Root : JsonState {
        private val personMap = mutableMapOf<String, Any?>()

        override fun preVisitPerson(
            person: Person,
            aktørId: String,
            fødselsnummer: String
        ) {
            pushState(PersonState(person, personMap))
        }

        override fun toString() = personMap.toString()

        override fun toJson(): JsonNode = serdeObjectMapper.valueToTree<JsonNode>(personMap)
    }

    private inner class PersonState(person: Person, private val personMap: MutableMap<String, Any?>) : JsonState {
        init {
            personMap.putAll(PersonReflect(person).toMap())
        }

        override fun visitPersonAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
            personMap["aktivitetslogg"] = AktivitetsloggReflect(aktivitetslogg).toMap()
        }

        private val arbeidsgivere = mutableListOf<MutableMap<String, Any?>>()

        override fun preVisitArbeidsgivere() {
            personMap["arbeidsgivere"] = arbeidsgivere
        }

        override fun preVisitArbeidsgiver(
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            organisasjonsnummer: String
        ) {
            val arbeidsgiverMap = mutableMapOf<String, Any?>()
            arbeidsgivere.add(arbeidsgiverMap)
            pushState(ArbeidsgiverState(arbeidsgiver, arbeidsgiverMap))
        }

        override fun postVisitPerson(
            person: Person,
            aktørId: String,
            fødselsnummer: String
        ) {
            popState()
        }
    }

    private inner class ArbeidsgiverState(
        arbeidsgiver: Arbeidsgiver,
        private val arbeidsgiverMap: MutableMap<String, Any?>
    ) :
        JsonState {
        init {
            arbeidsgiverMap.putAll(ArbeidsgiverReflect(arbeidsgiver).toMap())
        }

        override fun preVisitInntekthistorikk(inntekthistorikk: Inntekthistorikk) {
            val inntekter = mutableListOf<MutableMap<String, Any?>>()
            arbeidsgiverMap["inntekter"] = inntekter
            pushState(InntektHistorieState(inntekter))
        }

        private val utbetalingstidslinjer = mutableListOf<MutableMap<String, Any?>>()

        override fun preVisitTidslinjer(tidslinjer: MutableList<Utbetalingstidslinje>) {
            arbeidsgiverMap["utbetalingstidslinjer"] = utbetalingstidslinjer
        }

        override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) {
            val utbetalingstidslinjeMap = mutableMapOf<String, Any?>()
            utbetalingstidslinjer.add(utbetalingstidslinjeMap)
            pushState(UtbetalingstidslinjeState(utbetalingstidslinjeMap))
        }

        override fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
            arbeidsgiverMap["utbetalinger"] = mutableListOf<MutableMap<String, Any?>>().also {
                pushState(UtbetalingerState(it))
            }
        }

        private val vedtaksperioder = mutableListOf<MutableMap<String, Any?>>()

        override fun preVisitPerioder() {
            arbeidsgiverMap["vedtaksperioder"] = vedtaksperioder
        }

        override fun preVisitVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            id: UUID,
            gruppeId: UUID
        ) {
            val vedtaksperiodeMap = mutableMapOf<String, Any?>()
            vedtaksperioder.add(vedtaksperiodeMap)
            pushState(VedtaksperiodeState(vedtaksperiode, vedtaksperiodeMap))

        }

        override fun postVisitArbeidsgiver(
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            organisasjonsnummer: String
        ) {
            popState()
        }
    }

    private inner class UtbetalingerState(private val utbetalinger: MutableList<MutableMap<String, Any?>>) : JsonState {

        override fun preVisitUtbetaling(utbetaling: Utbetaling, tidsstempel: LocalDateTime) {
            utbetalinger.add(UtbetalingReflect(utbetaling).toMap())
        }

        override fun postVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
            popState()
        }
    }

    private inner class InntektHistorieState(private val inntekter: MutableList<MutableMap<String, Any?>>) : JsonState {
        override fun visitInntekt(inntekt: Inntekthistorikk.Inntekt, id: UUID) {
            val inntektMap = mutableMapOf<String, Any?>()
            inntekter.add(inntektMap)

            inntektMap.putAll(InntektReflect(inntekt).toMap())
        }

        override fun postVisitInntekthistorikk(inntekthistorikk: Inntekthistorikk) {
            popState()
        }
    }

    private inner class UtbetalingstidslinjeState(utbetalingstidslinjeMap: MutableMap<String, Any?>) :
        JsonState {

        private val dager = mutableListOf<MutableMap<String, Any?>>()

        init {
            utbetalingstidslinjeMap["dager"] = dager
        }

        override fun visitArbeidsdag(dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag) {
            dager.add(UtbetalingsdagReflect(dag, TypeData.Arbeidsdag).toMap())
        }

        override fun visitArbeidsgiverperiodeDag(dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag) {
            dager.add(UtbetalingsdagReflect(dag, TypeData.ArbeidsgiverperiodeDag).toMap())
        }

        override fun visitNavDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) {
            dager.add(NavDagReflect(dag, TypeData.NavDag).toMap())
        }

        override fun visitNavHelgDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) {
            dager.add(UtbetalingsdagMedGradReflect(dag, TypeData.NavHelgDag).toMap())
        }

        override fun visitFridag(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag) {
            dager.add(UtbetalingsdagReflect(dag, TypeData.Fridag).toMap())
        }

        override fun visitUkjentDag(dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag) {
            dager.add(UtbetalingsdagReflect(dag, TypeData.UkjentDag).toMap())
        }

        override fun visitAvvistDag(dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) {
            val avvistDagMap = mutableMapOf<String, Any?>()
            dager.add(avvistDagMap)

            avvistDagMap.putAll(AvvistdagReflect(dag).toMap())
        }

        override fun visitForeldetDag(dag: Utbetalingstidslinje.Utbetalingsdag.ForeldetDag) {
            val foreldetDagMap = mutableMapOf<String, Any?>()
            dager.add(foreldetDagMap)

            foreldetDagMap.putAll(UtbetalingsdagReflect(dag, TypeData.ForeldetDag).toMap())
        }

        override fun postVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) = popState()
    }

    private inner class VedtaksperiodeState(
        vedtaksperiode: Vedtaksperiode,
        private val vedtaksperiodeMap: MutableMap<String, Any?>
    ) : JsonState {
        init {
            vedtaksperiodeMap.putAll(VedtaksperiodeReflect(vedtaksperiode).toMap())
        }

        private val sykdomshistorikkElementer = mutableListOf<MutableMap<String, Any?>>()

        override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
            vedtaksperiodeMap["sykdomshistorikk"] = sykdomshistorikkElementer
        }

        override fun preVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            tidsstempel: LocalDateTime
        ) {
            val elementMap = mutableMapOf<String, Any?>()
            sykdomshistorikkElementer.add(elementMap)

            pushState(SykdomshistorikkElementState(id, tidsstempel, elementMap))
        }

        override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) {
            vedtaksperiodeMap["tilstand"] = tilstand.type.name
        }

        override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) {
            val utbetalingstidslinje = mutableMapOf<String, Any?>()
            vedtaksperiodeMap["utbetalingstidslinje"] = utbetalingstidslinje
            pushState(UtbetalingstidslinjeState(utbetalingstidslinje))
        }

        override fun postVisitVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            id: UUID,
            gruppeId: UUID
        ) {
            popState()
        }
    }

    private inner class SykdomshistorikkElementState(
        id: UUID,
        tidsstempel: LocalDateTime,
        private val elementMap: MutableMap<String, Any?>
    ) : JsonState {
        init {
            elementMap["hendelseId"] = id
            elementMap["tidsstempel"] = tidsstempel
        }

        override fun preVisitHendelseSykdomstidslinje(tidslinje: NySykdomstidslinje) {
            val sykdomstidslinje = mutableMapOf<String, Any>()
            elementMap["nyHendelseSykdomstidslinje"] = sykdomstidslinje
            pushState(NySykdomstidslinjeState(sykdomstidslinje))
        }

        override fun preVisitBeregnetSykdomstidslinje(tidslinje: NySykdomstidslinje) {
            val sykdomstidslinje = mutableMapOf<String, Any>()
            elementMap["nyBeregnetSykdomstidslinje"] = sykdomstidslinje
            pushState(NySykdomstidslinjeState(sykdomstidslinje))
        }

        override fun postVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            tidsstempel: LocalDateTime
        ) {
            popState()
        }
    }

    private inner class NySykdomstidslinjeState(private val sykdomstidslinje: MutableMap<String, Any>) : JsonState {

        private val dager: MutableList<MutableMap<String, Any>> = mutableListOf()

        override fun preVisitNySykdomstidslinje(
            tidslinje: NySykdomstidslinje,
            låstePerioder: List<Periode>,
            id: UUID,
            tidsstempel: LocalDateTime
        ) {
            sykdomstidslinje["id"] = id
            sykdomstidslinje["tidsstempel"] = tidsstempel
            sykdomstidslinje["låstePerioder"] = låstePerioder.map {
                mapOf("fom" to it.start, "tom" to it.endInclusive)
            }
        }

        override fun postVisitNySykdomstidslinje(tidslinje: NySykdomstidslinje, id: UUID, tidsstempel: LocalDateTime) {
            sykdomstidslinje["dager"] = dager
        }

        override fun visitDag(dag: NyUkjentDag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde)

        override fun visitDag(dag: NyArbeidsdag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde)

        override fun visitDag(dag: NyArbeidsgiverdag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde, grad)

        override fun visitDag(dag: NyFeriedag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde)

        override fun visitDag(dag: NyFriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde)

        override fun visitDag(dag: NyArbeidsgiverHelgedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde, grad)

        override fun visitDag(dag: NySykedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde, grad)

        override fun visitDag(dag: NyForeldetSykedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde, grad)

        override fun visitDag(dag: NySykHelgedag, dato: LocalDate, grad: Grad, kilde: Hendelseskilde) =
            leggTilDag(dag, dato, kilde, grad)

        override fun visitDag(dag: ProblemDag, dato: LocalDate, kilde: Hendelseskilde, melding: String) =
            leggTilDag(dag, dato, kilde, melding = melding)

        private fun leggTilDag(
            dag: NyDag,
            dato: LocalDate,
            kilde: Hendelseskilde,
            grad: Grad? = null,
            melding: String? = null
        ) {
            dager.add(
                mutableMapOf(
                    "dato" to dato,
                    "type" to dag.toJsonType(),
                    "kilde" to kilde.toJson()
                ).also { data ->
                    grad?.let { data["grad"] = it.toPercentage() }
                    melding?.let { data["melding"] = it }
                }
            )
        }

        override fun postVisitHendelseSykdomstidslinje(tidslinje: NySykdomstidslinje) {
            popState()
        }

        override fun postVisitBeregnetSykdomstidslinje(tidslinje: NySykdomstidslinje) {
            popState()
        }
    }
}

private fun Hendelseskilde.toJson() = mapOf(
    "type" to toString(), "id" to meldingsreferanseId()
)

private fun NyDag.toJsonType() = when (this) {
    is NySykedag -> SYKEDAG
    is NyUkjentDag -> UKJENT_DAG
    is NyArbeidsdag -> ARBEIDSDAG
    is NyArbeidsgiverdag -> ARBEIDSGIVERDAG
    is NyFeriedag -> FERIEDAG
    is NyFriskHelgedag -> FRISK_HELGEDAG
    is NyArbeidsgiverHelgedag -> ARBEIDSGIVER_HELGEDAG
    is NyForeldetSykedag -> FORELDET_SYKEDAG
    is NySykHelgedag -> SYK_HELGEDAG
    is NyPermisjonsdag -> PERMISJONSDAG
    is NyStudiedag -> STUDIEDAG
    is NyUtenlandsdag -> UTENLANDSDAG
    is ProblemDag -> PROBLEMDAG
}
