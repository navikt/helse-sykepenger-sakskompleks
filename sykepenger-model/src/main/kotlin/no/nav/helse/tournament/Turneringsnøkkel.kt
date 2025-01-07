package no.nav.helse.tournament

import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Søknad
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

// disse verdiene må være 1-til-1 mellom navnene i dagturnering-csv-filene
internal enum class Turneringsnøkkel {
    Arbeidsdag_SØ,
    Arbeidsdag_IM,
    Sykedag_SØ,
    ForeldetSykedag,
    Feriedag_SØ,
    Feriedag_IM,
    ArbeidIkkeGjenopptattDag,
    Permisjonsdag_SØ,
    SykHelgedag_SØ,
    Arbeidsgiverdag_IM,
    ArbeidsgiverHelgedag_IM,
    Arbeidsgiverdag_SØ,
    ArbeidsgiverHelgedag_SØ,
    Saksbehandlerdag,
    Arbeidsgiverdag_SB,
    Arbeidsdag_SB,
    SykedagNav_IM,
    AndreYtelserDag,
    UkjentDag,
    UbestemtDag;

    companion object {
        fun fraDag(dag: Dag) = when {
            dag is Arbeidsgiverdag && dag.kommerFra(OverstyrTidslinje::class) -> Arbeidsgiverdag_SB
            dag is Arbeidsdag && dag.kommerFra(OverstyrTidslinje::class) -> Arbeidsdag_SB
            dag is Dag.ArbeidIkkeGjenopptattDag -> ArbeidIkkeGjenopptattDag
            dag.kommerFra(OverstyrTidslinje::class) -> Saksbehandlerdag
            dag is Arbeidsdag && dag.kommerFra("Inntektsmelding") -> Arbeidsdag_IM
            dag is Arbeidsdag && dag.kommerFra(Søknad::class) -> Arbeidsdag_SØ
            dag is Arbeidsgiverdag && dag.kommerFra("Inntektsmelding") -> Arbeidsgiverdag_IM
            dag is Dag.SykedagNav && dag.kommerFra("Inntektsmelding") -> SykedagNav_IM
            dag is Arbeidsgiverdag && dag.kommerFra(Søknad::class) -> Arbeidsgiverdag_SØ
            dag is ArbeidsgiverHelgedag && dag.kommerFra("Inntektsmelding") -> ArbeidsgiverHelgedag_IM
            dag is SykHelgedag && dag.kommerFra("Inntektsmelding") -> ArbeidsgiverHelgedag_IM
            dag is ArbeidsgiverHelgedag && dag.kommerFra(Søknad::class) -> ArbeidsgiverHelgedag_SØ
            dag is Feriedag && dag.kommerFra("Inntektsmelding") -> Feriedag_IM
            dag is Feriedag && dag.kommerFra(Søknad::class) -> Feriedag_SØ
            dag is FriskHelgedag && dag.kommerFra("Inntektsmelding") -> Feriedag_IM
            dag is FriskHelgedag && dag.kommerFra(Søknad::class) -> Feriedag_SØ
            dag is Dag.ForeldetSykedag -> ForeldetSykedag
            dag is Permisjonsdag -> Permisjonsdag_SØ
            dag is ProblemDag -> UbestemtDag
            dag is Sykedag && dag.kommerFra(Søknad::class) -> Sykedag_SØ
            dag is SykHelgedag && dag.kommerFra(Søknad::class) -> SykHelgedag_SØ
            dag is Dag.AndreYtelser -> AndreYtelserDag
            dag is Dag.UkjentDag -> UkjentDag
            else -> throw IllegalArgumentException("Ingen turneringsnøkkel definert for ${dag::class.simpleName} med kilde ${dag.kilde}")
        }
    }
}
