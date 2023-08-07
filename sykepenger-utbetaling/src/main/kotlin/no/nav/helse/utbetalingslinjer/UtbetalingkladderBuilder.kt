package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Arbeidsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodedagNav
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.AvvistDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ForeldetDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.utbetalingstidslinje.UtbetalingsdagVisitor
import no.nav.helse.økonomi.Økonomi

class UtbetalingkladderBuilder(
    tidslinje: Collection<Utbetalingsdag>,
    private val mottakerRefusjon: String,
    private val mottakerBruker: String
) : UtbetalingsdagVisitor {
    private companion object {
        private const val MaksimaltAntallOppholdsdagerFørNyArbeidsgiverperiode = 15
    }
    private val oppdrag = mutableListOf<Utbetalingkladd>()
    private var kladdBuilder: UtbetalingkladdBuilder? = null
    private var arbeidsgiverdager: Periode? = null

    init {
        tidslinje.forEach { it.accept(this) }
    }

    fun build() = ferdigstill()

    override fun visit(dag: UkjentDag, dato: LocalDate, økonomi: Økonomi) {
        ferdigstill(dato)
    }

    override fun visit(dag: ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {
        håndterArbeidsgiverperiode(dato)
        if (dato.erHelg()) return
        kladdBuilder?.ikkeBetalingsdag(dato)
    }

    private fun håndterArbeidsgiverperiode(dato: LocalDate) {
        val forrige = arbeidsgiverdager
        val gapFraForrige = forrige?.periodeMellom(dato)
        val dagerMellomForrige = forrige?.let { gapFraForrige?.count() ?: 0 }
        // forsøker å la utbetalingene starte på første agp-dag, men siden vi ikke vet 100 % når agp starter,
        // så forholder vi oss til at det kan maksimalt være 15 dager opphold fra forrige agp-dag. Det vil derfor ikke
        // gi et helt nøyaktig bilde, f.eks. i situasjoner hvor det er ferie inni arbeidsgiverperioden: da kan avstanden mellom
        // agp-dagene egentlig være mye høyere, og fortsatt samme arbeidsgiverperiode
        if (dagerMellomForrige != null && dagerMellomForrige <= MaksimaltAntallOppholdsdagerFørNyArbeidsgiverperiode) {
            arbeidsgiverdager = forrige.oppdaterTom(dato)
            return
        }
        ferdigstill(dato)
    }
    private fun builder(dato: LocalDate) = kladdBuilder ?: resettBuilder(dato)

    private fun resettBuilder(førsteDag: LocalDate) =
        UtbetalingkladdBuilder(arbeidsgiverdager ?: førsteDag.somPeriode(), mottakerRefusjon, mottakerBruker).also {
            kladdBuilder = it
        }

    private fun ferdigstill(dato: LocalDate? = null): List<Utbetalingkladd> {
        kladdBuilder?.build()?.also {
            oppdrag.add(it)
        }
        kladdBuilder = null
        arbeidsgiverdager = dato?.somPeriode()
        return oppdrag.toList()
    }

    override fun visit(dag: ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        håndterArbeidsgiverperiode(dato)
        builder(dato).betalingsdag(beløpkilde = dag.beløpkilde(), dato = dato, økonomi = økonomi)
    }

    override fun visit(dag: NavDag, dato: LocalDate, økonomi: Økonomi) {
        builder(dato).betalingsdag(beløpkilde = dag.beløpkilde(), dato = dato, økonomi = økonomi)
    }

    override fun visit(dag: NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
        builder(dato).betalingshelgedag(dato, økonomi)
    }

    override fun visit(dag: Arbeidsdag, dato: LocalDate, økonomi: Økonomi) {
        builder(dato).ikkeBetalingsdag(dato)
    }

    override fun visit(dag: AvvistDag, dato: LocalDate, økonomi: Økonomi) {
        builder(dato).ikkeBetalingsdag(dato)
    }

    override fun visit(dag: Fridag, dato: LocalDate, økonomi: Økonomi) {
        builder(dato).ikkeBetalingsdag(dato)
    }

    override fun visit(dag: ForeldetDag, dato: LocalDate, økonomi: Økonomi) {
        builder(dato).ikkeBetalingsdag(dato)
    }
}