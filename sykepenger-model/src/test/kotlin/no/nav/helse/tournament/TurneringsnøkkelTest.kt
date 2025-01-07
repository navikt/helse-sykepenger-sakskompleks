package no.nav.helse.tournament

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.ArbeidsgiverHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsgiverdag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.ProblemDag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.tournament.Turneringsnøkkel.Arbeidsdag_IM
import no.nav.helse.tournament.Turneringsnøkkel.Arbeidsdag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.ArbeidsgiverHelgedag_IM
import no.nav.helse.tournament.Turneringsnøkkel.ArbeidsgiverHelgedag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.Arbeidsgiverdag_IM
import no.nav.helse.tournament.Turneringsnøkkel.Arbeidsgiverdag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.Companion.fraDag
import no.nav.helse.tournament.Turneringsnøkkel.Feriedag_IM
import no.nav.helse.tournament.Turneringsnøkkel.Feriedag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.Permisjonsdag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.SykHelgedag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.Sykedag_SØ
import no.nav.helse.tournament.Turneringsnøkkel.UbestemtDag
import no.nav.helse.tournament.TurneringsnøkkelTest.TestHendelse.Companion.inntektsmelding
import no.nav.helse.tournament.TurneringsnøkkelTest.TestHendelse.Companion.søknad
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TurneringsnøkkelTest {
    private val enDag = LocalDate.now()
    private val økonomi = Økonomi.sykdomsgrad(100.prosent)

    @Test
    fun turneringsnøkler() {
        assertEquals(Arbeidsdag_IM, fraDag(Arbeidsdag(enDag, inntektsmelding)))
        assertEquals(Arbeidsdag_SØ, fraDag(Arbeidsdag(enDag, søknad)))
        assertEquals(Arbeidsgiverdag_IM, fraDag(Arbeidsgiverdag(enDag, økonomi, inntektsmelding)))
        assertEquals(Arbeidsgiverdag_SØ, fraDag(Arbeidsgiverdag(enDag, økonomi, søknad)))
        assertEquals(ArbeidsgiverHelgedag_IM, fraDag(ArbeidsgiverHelgedag(enDag, økonomi, inntektsmelding)))
        assertEquals(ArbeidsgiverHelgedag_SØ, fraDag(ArbeidsgiverHelgedag(enDag, økonomi, søknad)))
        assertEquals(Turneringsnøkkel.ForeldetSykedag, fraDag(Dag.ForeldetSykedag(enDag, økonomi, inntektsmelding)))
        assertEquals(Turneringsnøkkel.ForeldetSykedag, fraDag(Dag.ForeldetSykedag(enDag, økonomi, søknad)))
        assertEquals(Feriedag_IM, fraDag(Feriedag(enDag, inntektsmelding)))
        assertEquals(Feriedag_SØ, fraDag(Feriedag(enDag, søknad)))
        assertEquals(Feriedag_IM, fraDag(FriskHelgedag(enDag, inntektsmelding)))
        assertEquals(Feriedag_SØ, fraDag(FriskHelgedag(enDag, søknad)))
        assertEquals(Turneringsnøkkel.UkjentDag, fraDag(Dag.UkjentDag(enDag, INGEN)))
        assertEquals(UbestemtDag, fraDag(ProblemDag(enDag, INGEN, "")))
        assertEquals(Permisjonsdag_SØ, fraDag(Permisjonsdag(enDag, søknad)))
        assertEquals(Sykedag_SØ, fraDag(Sykedag(enDag, økonomi, søknad)))
        assertEquals(SykHelgedag_SØ, fraDag(SykHelgedag(enDag, økonomi, søknad)))
    }

    private sealed class TestHendelse : SykdomshistorikkHendelse {
        companion object {
            val søknad = Søknad.kilde
            val inntektsmelding = Inntektsmelding.kilde
        }

        val kilde: Hendelseskilde = Hendelseskilde(this::class, UUID.randomUUID(), LocalDateTime.now())

        // Objects impersonating real-life sources of sickness timeline days
        object Inntektsmelding : TestHendelse()
        object Søknad : TestHendelse()

        override fun sykdomstidslinje(): Sykdomstidslinje {
            error("ikke i bruk")
        }
    }
}
