package no.nav.helse.testhelpers

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Collectors
import no.nav.helse.hendelser.Melding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.januar
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.ProblemDag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi

private var threadLocalDagensDato = ThreadLocal.withInitial { 1.januar }
private var dagensDato: LocalDate
    get() = threadLocalDagensDato.get()
    set(value) = threadLocalDagensDato.set(value)

internal fun resetSeed(frøDato: LocalDate = 1.januar) {
    dagensDato = frøDato
}

internal fun resetSeed(frøDato: LocalDate = 1.januar, tidslinjegenerator: () -> Sykdomstidslinje): Sykdomstidslinje {
    resetSeed(frøDato)
    return tidslinjegenerator()
}

internal val Int.S
    get() = Sykdomstidslinje.sykedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        100.prosent,
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.N
    get() = Sykdomstidslinje.sykedagerNav(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        100.prosent,
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal fun Int.S(melding: Melding) = this.S(Hendelseskilde(melding, UUID.randomUUID(), LocalDateTime.now()))

internal fun Int.S(hendelseskilde: Hendelseskilde, grad: Prosentdel = 100.prosent) = Sykdomstidslinje.sykedager(
    dagensDato,
    dagensDato.plusDays(this.toLong() - 1),
    grad,
    hendelseskilde
).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.U
    get() = Sykdomstidslinje.arbeidsgiverdager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        100.prosent,
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal fun Int.U(meldingsreferanseId: UUID = UUID.randomUUID()) = Sykdomstidslinje.arbeidsgiverdager(
    dagensDato,
    dagensDato.plusDays(this.toLong() - 1),
    100.prosent,
    Hendelseskilde(SykdomshistorikkHendelse::class, meldingsreferanseId, LocalDateTime.now())
).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.K
    get() = Sykdomstidslinje.sykedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        dagensDato.plusMonths(4),
        100.prosent,
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.A
    get() = Sykdomstidslinje.arbeidsdager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.opphold
    get() = Sykdomstidslinje(
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.F
    get() = Sykdomstidslinje.feriedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.AIG
    get() = Sykdomstidslinje.arbeidIkkeGjenopptatt(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.YF
    get() = YF(Dag.AndreYtelser.AnnenYtelse.Foreldrepenger)

internal fun Int.YF(ytelse: Dag.AndreYtelser.AnnenYtelse, kilde: Hendelseskilde = INGEN) = Sykdomstidslinje.andreYtelsedager(
    dagensDato,
    dagensDato.plusDays(this.toLong() - 1),
    kilde,
    ytelse
).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal fun unikKilde() = UUID.randomUUID().let { Hendelseskilde(it.toString(), it, LocalDateTime.now()) }

internal val Int.YD
    get() = Sykdomstidslinje.andreYtelsedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN,
        Dag.AndreYtelser.AnnenYtelse.Dagpenger
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.YA
    get() = Sykdomstidslinje.andreYtelsedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN,
        Dag.AndreYtelser.AnnenYtelse.AAP
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.YO
    get() = Sykdomstidslinje.andreYtelsedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN,
        Dag.AndreYtelser.AnnenYtelse.Omsorgspenger
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.YP
    get() = Sykdomstidslinje.andreYtelsedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN,
        Dag.AndreYtelser.AnnenYtelse.Pleiepenger
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.YS
    get() = Sykdomstidslinje.andreYtelsedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN,
        Dag.AndreYtelser.AnnenYtelse.Svangerskapspenger
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.YOL
    get() = Sykdomstidslinje.andreYtelsedager(
        dagensDato,
        dagensDato.plusDays(this.toLong() - 1),
        INGEN,
        Dag.AndreYtelser.AnnenYtelse.Opplæringspenger
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.H
    get() = Sykdomstidslinje(
        dagensDato.datesUntil(dagensDato.plusDays(this.toLong() - 1).plusDays(1))
            .collect(Collectors.toMap<LocalDate, LocalDate, Dag>({ it }, { SykHelgedag(it, Økonomi.sykdomsgrad(100.prosent), INGEN) }))
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.P
    get() = Sykdomstidslinje(
        dagensDato.datesUntil(dagensDato.plusDays(this.toLong() - 1).plusDays(1))
            .collect(Collectors.toMap<LocalDate, LocalDate, Dag>({ it }, { Permisjonsdag(it, INGEN) }))
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.R
    get() = Sykdomstidslinje(
        dagensDato.datesUntil(dagensDato.plusDays(this.toLong() - 1).plusDays(1))
            .collect(Collectors.toMap<LocalDate, LocalDate, Dag>({ it }, { FriskHelgedag(it, INGEN) }))
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.UK
    get() = Sykdomstidslinje(
        dagensDato.datesUntil(dagensDato.plusDays(this.toLong() - 1).plusDays(1))
            .collect(Collectors.toMap<LocalDate, LocalDate, Dag>({ it }, { UkjentDag(it, INGEN) }))
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.PROBLEM get() = PROBLEM("Problemdag", INGEN)
internal fun Int.PROBLEM(melding: String, kilde: Hendelseskilde) = Sykdomstidslinje(
    dagensDato.datesUntil(dagensDato.plusDays(this.toLong() - 1).plusDays(1))
        .collect(Collectors.toMap<LocalDate, LocalDate, Dag>({ it }, { ProblemDag(it, kilde, melding) }))
).also { dagensDato = dagensDato.plusDays(this.toLong()) }

internal val Int.FORELDET
    get() = Sykdomstidslinje(
        dagensDato.datesUntil(dagensDato.plusDays(this.toLong() - 1).plusDays(1))
            .collect(Collectors.toMap<LocalDate, LocalDate, Dag>({ it }, { Dag.ForeldetSykedag(it, Økonomi.sykdomsgrad(100.prosent), INGEN) }))
    ).also { dagensDato = dagensDato.plusDays(this.toLong()) }


internal class TestHendelse(private val tidslinje: Sykdomstidslinje = Sykdomstidslinje()) : SykdomshistorikkHendelse {
    override fun sykdomstidslinje() = tidslinje
}
