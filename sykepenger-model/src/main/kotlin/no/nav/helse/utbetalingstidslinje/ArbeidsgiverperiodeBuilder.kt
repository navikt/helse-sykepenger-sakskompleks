package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.SykdomstidslinjeVisitor
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.økonomi.Økonomi

internal class ArbeidsgiverperiodeBuilder(
    private val arbeidsgiverperiodeteller: Arbeidsgiverperiodeteller,
    mediator: ArbeidsgiverperiodeMediator,
    subsumsjonObserver: SubsumsjonObserver?
) : SykdomstidslinjeVisitor,
    Arbeidsgiverperiodeteller.Observatør {

    private val mediator = Arbeidsgiverperiodesubsumsjon(mediator, subsumsjonObserver)

    init {
        arbeidsgiverperiodeteller.observer(this)
    }

    private var tilstand: Tilstand = Initiell

    override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {
        mediator.tidslinje(tidslinje)
    }

    override fun postVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: MutableList<Periode>) {
        fridager.somFerieOppholdsdager()
    }

    private fun tilstand(tilstand: Tilstand) {
        if (this.tilstand == tilstand) return
        this.tilstand.leaving(this)
        this.tilstand = tilstand
        this.tilstand.entering(this)
    }

    override fun arbeidsgiverperiodeFerdig() {
        tilstand(ArbeidsgiverperiodeSisteDag)
    }

    override fun arbeidsgiverperiodedag() {
        tilstand(Arbeidsgiverperiode)
    }

    override fun sykedag() {
        tilstand(Utbetaling)
    }

    override fun oppholdsdag() {
        mediator.oppholdsdag()
    }

    override fun arbeidsgiverperiodeAvbrutt() {
        mediator.arbeidsgiverperiodeAvbrutt()
    }

    private fun MutableList<LocalDate>.somSykedager(kilde: Hendelseskilde) {
        onEach {
            arbeidsgiverperiodeteller.inc()
            tilstand.feriedagSomSyk(this@ArbeidsgiverperiodeBuilder, it, kilde)
        }.clear()
    }

    private fun MutableList<LocalDate>.somFerieOppholdsdager() {
        onEach {
            arbeidsgiverperiodeteller.dec()
            mediator.fridagOppholdsdag(it)
        }.clear()
    }

    override fun visitDag(dag: Dag.Sykedag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        arbeidsgiverperiodeteller.inc()
        tilstand.sykdomsdag(this, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.SykedagNav, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        arbeidsgiverperiodeteller.inc()
        tilstand.sykdomsdagNav(this, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.SykHelgedag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        arbeidsgiverperiodeteller.inc()
        tilstand.sykdomshelg(this, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.Arbeidsgiverdag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        arbeidsgiverperiodeteller.inc()
        tilstand.egenmeldingsdag(this, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.ArbeidsgiverHelgedag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        arbeidsgiverperiodeteller.inc()
        tilstand.sykdomsdag(this, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.Feriedag, dato: LocalDate, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        tilstand.feriedagMedSykmelding(this, dato, kilde)
    }

    override fun visitDag(dag: Dag.ArbeidIkkeGjenopptattDag, dato: LocalDate, kilde: Hendelseskilde) {
        tilstand.feriedag(this, dato)
    }

    override fun visitDag(dag: Dag.Permisjonsdag, dato: LocalDate, kilde: Hendelseskilde) {
        tilstand.feriedag(this, dato)
    }

    override fun visitDag(
        dag: Dag.ProblemDag,
        dato: LocalDate,
        kilde: Hendelseskilde,
        other: Hendelseskilde?,
        melding: String
    ) {
        throw UtbetalingstidslinjeBuilderException.UforventetDagException(dag, melding)
    }

    override fun visitDag(
        dag: Dag.AndreYtelser,
        dato: LocalDate,
        kilde: Hendelseskilde,
        ytelse: Dag.AndreYtelser.AnnenYtelse
    ) {
        val begrunnelse = when(ytelse) {
            Dag.AndreYtelser.AnnenYtelse.AAP -> Begrunnelse.AndreYtelserAap
            Dag.AndreYtelser.AnnenYtelse.Dagpenger -> Begrunnelse.AndreYtelserDagpenger
            Dag.AndreYtelser.AnnenYtelse.Foreldrepenger -> Begrunnelse.AndreYtelserForeldrepenger
            Dag.AndreYtelser.AnnenYtelse.Omsorgspenger -> Begrunnelse.AndreYtelserOmsorgspenger
            Dag.AndreYtelser.AnnenYtelse.Opplæringspenger -> Begrunnelse.AndreYtelserOpplaringspenger
            Dag.AndreYtelser.AnnenYtelse.Pleiepenger -> Begrunnelse.AndreYtelserPleiepenger
            Dag.AndreYtelser.AnnenYtelse.Svangerskapspenger -> Begrunnelse.AndreYtelserSvangerskapspenger

        }
        tilstand.andreYtelser(this, dato, begrunnelse)
    }

    override fun visitDag(dag: Dag.Arbeidsdag, dato: LocalDate, kilde: Hendelseskilde) {
        tilstand(Initiell)
        fridager.somFerieOppholdsdager()
        arbeidsgiverperiodeteller.dec()
        mediator.arbeidsdag(dato)
    }

    override fun visitDag(dag: Dag.FriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) {
        tilstand(Initiell)
        fridager.somFerieOppholdsdager()
        arbeidsgiverperiodeteller.dec()
        mediator.arbeidsdag(dato)
    }

    override fun visitDag(dag: Dag.UkjentDag, dato: LocalDate, kilde: Hendelseskilde) {
        if (dato.erHelg()) return tilstand.feriedag(this, dato)
        tilstand(Initiell)
        fridager.somFerieOppholdsdager()
        arbeidsgiverperiodeteller.dec()
        mediator.arbeidsdag(dato)
    }

    override fun visitDag(dag: Dag.ForeldetSykedag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        fridager.somSykedager(kilde)
        arbeidsgiverperiodeteller.inc()
        tilstand.foreldetDag(this, dato, økonomi, kilde)
    }

    private val fridager = mutableListOf<LocalDate>()

    private interface Tilstand {
        fun entering(builder: ArbeidsgiverperiodeBuilder) {}
        fun sykdomsdag(
            builder: ArbeidsgiverperiodeBuilder,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) {
            builder.mediator.utbetalingsdag(dato, økonomi, kilde)
        }
        fun sykdomsdagNav(
            builder: ArbeidsgiverperiodeBuilder,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) {
            builder.mediator.utbetalingsdag(dato, økonomi, kilde)
        }
        fun egenmeldingsdag(
            builder: ArbeidsgiverperiodeBuilder,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = sykdomsdag(builder, dato, økonomi, kilde)
        fun sykdomshelg(
            builder: ArbeidsgiverperiodeBuilder,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) {
            builder.mediator.utbetalingsdag(dato, økonomi, kilde)
        }
        fun foreldetDag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.foreldetDag(dato, økonomi)
        }
        fun feriedagSomSyk(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde)
        fun feriedag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate)
        fun feriedagMedSykmelding(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde)
        fun leaving(builder: ArbeidsgiverperiodeBuilder) {}
        fun andreYtelser(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, begrunnelse: Begrunnelse)
    }
    private object Initiell : Tilstand {
        override fun feriedag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.arbeidsgiverperiodeteller.dec()
            builder.mediator.fridagOppholdsdag(dato)
        }

        override fun feriedagMedSykmelding(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            feriedag(builder, dato)
        }

        override fun andreYtelser(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, begrunnelse: Begrunnelse) {
            builder.mediator.avvistDag(dato, begrunnelse, Økonomi.ikkeBetalt())
        }

        override fun feriedagSomSyk(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            throw IllegalStateException()
        }
    }
    private object Arbeidsgiverperiode : Tilstand {
        override fun sykdomsdagNav(
            builder: ArbeidsgiverperiodeBuilder,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) {
            builder.mediator.arbeidsgiverperiodedagNav(dato, økonomi, kilde)
        }

        override fun sykdomsdag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, økonomi, kilde)
        }
        override fun sykdomshelg(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, økonomi, kilde)
        }
        override fun feriedagSomSyk(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, Økonomi.ikkeBetalt(), kilde)
        }

        override fun feriedagMedSykmelding(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            builder.arbeidsgiverperiodeteller.inc()
            builder.tilstand.feriedagSomSyk(builder, dato, kilde)
        }

        override fun feriedag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.fridager.add(dato)
        }
        override fun andreYtelser(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, begrunnelse: Begrunnelse) {
            builder.fridager.add(dato)
        }

        override fun foreldetDag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, økonomi, kilde)
        }
    }
    private object ArbeidsgiverperiodeSisteDag : Tilstand {
        override fun entering(builder: ArbeidsgiverperiodeBuilder) {
            builder.mediator.arbeidsgiverperiodeSistedag()
        }

        override fun sykdomsdagNav(
            builder: ArbeidsgiverperiodeBuilder,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) {
            builder.mediator.arbeidsgiverperiodedagNav(dato, økonomi, kilde)
            builder.tilstand(Utbetaling)
        }

        override fun sykdomsdag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, økonomi, kilde)
            builder.tilstand(Utbetaling)
        }
        override fun sykdomshelg(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, økonomi, kilde)
            builder.tilstand(Utbetaling)
        }
        override fun feriedagSomSyk(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, Økonomi.ikkeBetalt(), kilde)
            builder.tilstand(Utbetaling)
        }

        override fun foreldetDag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.arbeidsgiverperiodedag(dato, økonomi, kilde)
            builder.tilstand(Utbetaling)
        }

        override fun feriedagMedSykmelding(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            throw IllegalStateException("kan ikke ha fridag som siste dag i arbeidsgiverperioden")
        }

        override fun feriedag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            throw IllegalStateException("kan ikke ha fridag som siste dag i arbeidsgiverperioden")
        }

        override fun andreYtelser(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, begrunnelse: Begrunnelse) {
            throw IllegalStateException("kan ikke ha andre ytelser som siste dag i arbeidsgiverperioden")
        }
    }
    private object Utbetaling : Tilstand {
        override fun entering(builder: ArbeidsgiverperiodeBuilder) {
            // signaliserer at arbeidsgiverperioden er ferdig;
            // enten som følge av at vi nettopp har fullført å telle arbeidsgiverperioden,
            // eller fordi vi skal gå rett på utbetaling (arbeidsgiverperioden er unnagjort i Infotrygd, eller antall arbeidsgiverperiodedager skal være 0)
            builder.mediator.arbeidsgiverperiodeFerdig()
        }

        override fun egenmeldingsdag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            builder.mediator.avvistDag(dato, Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode, økonomi)
        }

        override fun feriedag(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate) {
            builder.mediator.fridag(dato)
        }

        override fun feriedagMedSykmelding(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            builder.mediator.fridag(dato)
        }

        override fun andreYtelser(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, begrunnelse: Begrunnelse) {
            builder.mediator.avvistDag(dato, begrunnelse, Økonomi.ikkeBetalt())
        }

        override fun feriedagSomSyk(builder: ArbeidsgiverperiodeBuilder, dato: LocalDate, kilde: Hendelseskilde) {
            builder.mediator.fridag(dato)
        }
    }
}
