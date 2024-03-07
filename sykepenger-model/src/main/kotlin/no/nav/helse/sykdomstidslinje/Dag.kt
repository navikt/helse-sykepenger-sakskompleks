package no.nav.helse.sykdomstidslinje

import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.dto.SykdomstidslinjeDagDto
import no.nav.helse.dto.SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto
import no.nav.helse.dto.HendelseskildeDto
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.økonomi.Økonomi

internal typealias BesteStrategy = (Dag, Dag) -> Dag

internal sealed class Dag(
    protected val dato: LocalDate,
    protected val kilde: Hendelseskilde
) {
    private fun name() = javaClass.canonicalName.split('.').last()

    companion object {
        internal val default: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            if (venstre == høyre) venstre else høyre.problem(venstre)
        }

        internal val override: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            høyre
        }

        internal val sammenhengendeSykdom: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            when (venstre) {
                is Sykedag,
                is SykedagNav,
                is SykHelgedag,
                is Arbeidsgiverdag,
                is ArbeidsgiverHelgedag -> venstre
                is Feriedag,
                is Permisjonsdag -> when (høyre) {
                    is Sykedag,
                    is SykedagNav,
                    is SykHelgedag,
                    is Arbeidsgiverdag,
                    is ArbeidsgiverHelgedag -> høyre
                    else -> venstre
                }
                else -> høyre
            }
        }

        internal val noOverlap: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            høyre.problem(venstre, "Støtter ikke overlappende perioder (${venstre.kilde} og ${høyre.kilde})")
        }

        internal val replace: BesteStrategy = { venstre: Dag, høyre: Dag ->
            if (høyre is UkjentDag) venstre
            else høyre
        }
    }

    internal fun kommerFra(hendelse: Melding) = kilde.erAvType(hendelse)
    internal fun kommerFra(hendelse: String) = kilde.erAvType(hendelse)

    internal fun erHelg() = dato.erHelg()

    internal fun problem(other: Dag, melding: String = "Kan ikke velge mellom ${name()} fra $kilde og ${other.name()} fra ${other.kilde}."): Dag =
        ProblemDag(dato, kilde, other.kilde, melding)

    override fun equals(other: Any?) =
        other != null && this::class == other::class && this.equals(other as Dag)

    protected open fun equals(other: Dag) = this.dato == other.dato && this.kilde == other.kilde

    override fun hashCode() = dato.hashCode() * 37 + kilde.hashCode() * 41 + this::class.hashCode()

    override fun toString() = "${this::class.java.simpleName} ($dato) $kilde"

    internal open fun accept(visitor: SykdomstidslinjeDagVisitor) {}

    internal class UkjentDag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde)

        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.UkjentDagDto(dato, kilde)
    }

    internal class Arbeidsdag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidsdagDto(dato, kilde)
    }

    internal class Arbeidsgiverdag(
        dato: LocalDate,
        private val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, økonomi, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidsgiverdagDto(dato, kilde, økonomi.dto())
    }

    internal class Feriedag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.FeriedagDto(dato, kilde)
    }

    internal class ArbeidIkkeGjenopptattDag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto(dato, kilde)
    }

    internal class FriskHelgedag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde)

        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.FriskHelgedagDto(dato, kilde)
    }

    internal class ArbeidsgiverHelgedag(
        dato: LocalDate,
        private val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, økonomi, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto(dato, kilde, økonomi.dto())
    }

    internal class Sykedag(
        dato: LocalDate,
        private val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, økonomi, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.SykedagDto(dato, kilde, økonomi.dto())
    }

    internal class ForeldetSykedag(
        dato: LocalDate,
        private val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, økonomi, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ForeldetSykedagDto(dato, kilde, økonomi.dto())
    }

    internal class SykHelgedag(
        dato: LocalDate,
        private val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, økonomi, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.SykHelgedagDto(dato, kilde, økonomi.dto())
    }

    internal class SykedagNav(
        dato: LocalDate,
        private val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun accept(visitor: SykdomstidslinjeDagVisitor) = visitor.visitDag(this, dato, økonomi, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.SykedagNavDto(dato, kilde, økonomi.dto())
    }

    internal class Permisjonsdag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.PermisjonsdagDto(dato, kilde)
    }

    internal class ProblemDag(
        dato: LocalDate,
        kilde: Hendelseskilde,
        private val other: Hendelseskilde,
        private val melding: String
    ) : Dag(dato, kilde) {

        internal constructor(dato: LocalDate, kilde: Hendelseskilde, melding: String) : this(dato, kilde, kilde, melding)

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde, other, melding)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ProblemDagDto(dato, kilde, other.dto(), melding)
    }

    internal class AndreYtelser(
        dato: LocalDate,
        kilde: Hendelseskilde,
        private val ytelse: AnnenYtelse,
    ) : Dag(dato, kilde) {
        enum class AnnenYtelse {
            Foreldrepenger, AAP, Omsorgspenger, Pleiepenger, Svangerskapspenger, Opplæringspenger, Dagpenger;

            fun dto() = when (this) {
                Foreldrepenger -> YtelseDto.Foreldrepenger
                AAP -> YtelseDto.AAP
                Omsorgspenger -> YtelseDto.Omsorgspenger
                Pleiepenger -> YtelseDto.Pleiepenger
                Svangerskapspenger -> YtelseDto.Svangerskapspenger
                Opplæringspenger -> YtelseDto.Opplæringspenger
                Dagpenger -> YtelseDto.Dagpenger
            }
        }

        override fun accept(visitor: SykdomstidslinjeDagVisitor) =
            visitor.visitDag(this, dato, kilde, ytelse)

        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) =
            SykdomstidslinjeDagDto.AndreYtelserDto(dato, kilde, ytelse.dto())
    }

    internal fun dto() = dto(dato, kilde.dto())
    protected abstract fun dto(dato: LocalDate, kilde: HendelseskildeDto): SykdomstidslinjeDagDto
}

internal interface SykdomstidslinjeDagVisitor {
    fun visitDag(dag: Dag.UkjentDag, dato: LocalDate, kilde: Hendelseskilde) {}
    fun visitDag(dag: Dag.Arbeidsdag, dato: LocalDate, kilde: Hendelseskilde) {}
    fun visitDag(
        dag: Dag.Arbeidsgiverdag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) {
    }

    fun visitDag(dag: Dag.Feriedag, dato: LocalDate, kilde: Hendelseskilde) {}

    fun visitDag(dag: Dag.ArbeidIkkeGjenopptattDag, dato: LocalDate, kilde: Hendelseskilde) {}

    fun visitDag(dag: Dag.FriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) {}
    fun visitDag(
        dag: Dag.ArbeidsgiverHelgedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) {
    }

    fun visitDag(
        dag: Dag.Sykedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) {
    }

    fun visitDag(
        dag: Dag.ForeldetSykedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) {
    }

    fun visitDag(
        dag: Dag.SykHelgedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) {
    }

    fun visitDag(
        dag: Dag.SykedagNav,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) {}

    fun visitDag(dag: Dag.Permisjonsdag, dato: LocalDate, kilde: Hendelseskilde) {}
    fun visitDag(
        dag: Dag.ProblemDag,
        dato: LocalDate,
        kilde: Hendelseskilde,
        other: Hendelseskilde?,
        melding: String
    ) {
    }
    fun visitDag(
        dag: Dag.AndreYtelser,
        dato: LocalDate,
        kilde: Hendelseskilde,
        ytelse: Dag.AndreYtelser.AnnenYtelse
    ) {
    }
}