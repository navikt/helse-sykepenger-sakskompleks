package no.nav.helse.sykdomstidslinje

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Objects
import java.util.SortedMap
import java.util.stream.Collectors.toMap
import no.nav.helse.dto.SykdomstidslinjeDto
import no.nav.helse.erHelg
import no.nav.helse.erRettFør
import no.nav.helse.etterlevelse.SykdomstidslinjeBuilder
import no.nav.helse.etterlevelse.Tidslinjedag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.contains
import no.nav.helse.hendelser.til
import no.nav.helse.nesteDag
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse
import no.nav.helse.sykdomstidslinje.Dag.ArbeidIkkeGjenopptattDag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.ArbeidsgiverHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsgiverdag
import no.nav.helse.sykdomstidslinje.Dag.Companion.default
import no.nav.helse.sykdomstidslinje.Dag.Companion.funksjoneltLik
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Dag.Companion.sammenhengendeSykdom
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.ForeldetSykedag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.ProblemDag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.sykdomstidslinje.Dag.SykedagNav
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi

class Sykdomstidslinje private constructor(
    private val dager: SortedMap<LocalDate, Dag>,
    periode: Periode? = null,
    private val _låstePerioder: MutableList<Periode> = mutableListOf()
) : Iterable<Dag> {
    val låstePerioder get() = _låstePerioder.toList()

    // Støtte for at perioden er lengre enn vi har dager for (Map-et er sparse)
    private val periode: Periode? = periode ?: if (dager.size > 0) Periode(dager.firstKey(), dager.lastKey()) else null

    internal constructor(dager: Map<LocalDate, Dag> = emptyMap()) : this(dager.toSortedMap())

    internal constructor(original: Sykdomstidslinje, spanningPeriode: Periode) :
        this(original.dager, original.periode?.plus(spanningPeriode), original.låstePerioder.toMutableList())

    internal fun periode() = periode
    internal fun førsteDag() = periode!!.start
    internal fun sisteDag() = periode!!.endInclusive

    internal fun overlapperMed(other: Sykdomstidslinje) =
        when {
            this.periode == null && other.periode == null -> true
            this.periode == null || other.periode == null -> false
            else -> this.periode.overlapperMed(other.periode)
        }

    internal fun merge(other: Sykdomstidslinje, beste: BesteStrategy = default): Sykdomstidslinje {
        val nyeDager = dager.toMutableMap()
        other.dager.filter { it.key !in låstePerioder }.forEach { (dato, dag) -> nyeDager.merge(dato, dag, beste) }
        return Sykdomstidslinje(
            nyeDager.toSortedMap(),
            this.periode?.plus(other.periode) ?: other.periode,
            this.låstePerioder.toMutableList()
        )
    }

    internal operator fun plus(other: Sykdomstidslinje) = this.merge(other)
    internal operator fun minus(other: Sykdomstidslinje) = Sykdomstidslinje(dager.filterNot { it.key in other.dager.keys }.toSortedMap())
    internal operator fun get(dato: LocalDate): Dag = dager[dato] ?: UkjentDag(dato, INGEN)
    internal fun subset(periode: Periode) =
        if (this.periode == null || !periode.overlapperMed(this.periode)) Sykdomstidslinje()
        else Sykdomstidslinje(dager.subMap(periode.start, periode.endInclusive.nesteDag), this.periode.subset(periode))

    /**
     * Uten å utvide tidslinjen
     */
    internal fun fremTilOgMed(dato: LocalDate) =
        if (periode == null || dato < førsteDag()) Sykdomstidslinje() else subset(førsteDag() til dato)


    private fun kuttEtterSisteSykedag(): Sykdomstidslinje = periode
        ?.findLast { erEnSykedag(this[it]) }
        ?.let { this.subset(Periode(dager.firstKey(), it)) } ?: Sykdomstidslinje()

    internal fun fraOgMed(dato: LocalDate) =
        Sykdomstidslinje(dager.tailMap(dato).toMap())

    internal fun trim(perioder: List<Periode>): Sykdomstidslinje {
        val forkast = perioder.flatten()
        val låstePerioder = låstePerioder.filterNot { it in perioder }
        return Sykdomstidslinje(dager.filterKeys { it !in forkast }.toSortedMap(), null, låstePerioder.toMutableList())
    }

    internal fun erLåst(periode: Periode) = låstePerioder.contains(periode)

    internal fun lås(periode: Periode) = this.also {
        requireNotNull(this.periode)
        require(periode in this.periode) { "$periode er ikke i ${this.periode}" }
        _låstePerioder.add(periode)
    }

    internal fun låsOpp(periode: Periode) = this.also {
        _låstePerioder.removeIf { it == periode } || throw IllegalArgumentException("Kan ikke låse opp periode $periode")
    }

    internal fun bekreftErLåst(periode: Periode) {
        check(låstePerioder.any { it == periode }) { "$periode er ikke låst" }
    }
    internal fun bekreftErÅpen(periode: Periode) {
        check(låstePerioder.none { it.overlapperMed(periode) }) { "hele eller deler av $periode er låst" }
    }

    override operator fun iterator(): Iterator<Dag> {
        if (periode == null) return emptyList<Nothing>().iterator()
        return object : Iterator<Dag> {
            private val periodeIterator = periode.iterator()
            override fun hasNext() = periodeIterator.hasNext()
            override fun next() = this@Sykdomstidslinje[periodeIterator.next()]
        }
    }

    internal fun sisteSkjæringstidspunkt(periode: Periode? = null): LocalDate? {
        val søkeperiode = periode ?: this.periode ?: return null
        return Skjæringstidspunkt(this).beregnSkjæringstidspunktOrNull(søkeperiode, null)
    }

    internal fun erRettFør(other: Sykdomstidslinje): Boolean {
        if (!this.sisteDag().erRettFør(other.førsteDag())) return false
        val sammenslått = this + other
        return sammenslått.sisteSkjæringstidspunkt(other.periode) == sammenslått.sisteSkjæringstidspunkt(this.periode)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Sykdomstidslinje) return false
        return dager == other.dager && periode == other.periode && låstePerioder == other.låstePerioder
    }

    internal fun funksjoneltLik(other: Sykdomstidslinje) = dager.funksjoneltLik(other.dager)

    override fun hashCode() = Objects.hash(dager, periode, låstePerioder)

    override fun toString() = toShortString()

    internal fun toUnikDagtypeShortString(): String {
        if (periode == null) return "Tom tidslinje"
        return String(toShortString().replace(" ", "").toSortedSet().toCharArray())
    }

    internal fun toShortString(): String {
        return periode?.joinToString(separator = "") {
            (if (it.dayOfWeek == DayOfWeek.MONDAY) " " else "") +
                when (this[it]) {
                    is Sykedag -> "S"
                    is Arbeidsdag -> "A"
                    is UkjentDag -> "?"
                    is ProblemDag -> "X"
                    is SykHelgedag -> "H"
                    is Arbeidsgiverdag -> "U"
                    is ArbeidsgiverHelgedag -> "G"
                    is Feriedag -> "F"
                    is ArbeidIkkeGjenopptattDag -> "J"
                    is Permisjonsdag -> "P"
                    is FriskHelgedag -> "R"
                    is ForeldetSykedag -> "K"
                    is SykedagNav -> "N"
                    is AndreYtelser -> "Y"
                }
        }?.trim() ?: "Tom tidslinje"
    }

    internal fun sykdomsperiode() = kuttEtterSisteSykedag().periode
    internal fun subsumsjonsformat(): List<Tidslinjedag> = SykdomstidslinjeBuilder(this).dager()

    internal fun egenmeldingerFraSøknad() = dager
        .filter { it.value.kommerFra(Søknad::class) && ( it.value is Arbeidsgiverdag || it.value is ArbeidsgiverHelgedag) }
        .map { it.key }
        .grupperSammenhengendePerioderMedHensynTilHelg()

    internal companion object {
        internal fun List<Sykdomstidslinje>.slåSammenForkastedeSykdomstidslinjer() =
            fold(Sykdomstidslinje()) { acc, sykdomstidslinje ->
                val utenProblemdager = Sykdomstidslinje(sykdomstidslinje.dager.filter { (_, dag) -> dag !is ProblemDag }.toSortedMap(), sykdomstidslinje.periode)
                acc.merge(utenProblemdager, replace)
            }
        private fun erEnSykedag(it: Dag) =
            it is Sykedag || it is SykHelgedag || it is Arbeidsgiverdag || it is ArbeidsgiverHelgedag || it is ForeldetSykedag || it is SykedagNav

        internal fun beregnSkjæringstidspunkt(tidslinjer: List<Sykdomstidslinje>) =
            Skjæringstidspunkt(samletTidslinje(tidslinjer))

        private fun samletTidslinje(tidslinjer: List<Sykdomstidslinje>) = tidslinjer
            .map { Sykdomstidslinje(it.dager, it.periode) } // fjerner evt. låser først
            .merge(sammenhengendeSykdom)

        internal fun arbeidsdager(periode: Periode, kilde: Hendelseskilde) =
            Sykdomstidslinje(periode.associateWith { if (it.erHelg()) FriskHelgedag(it, kilde) else Arbeidsdag(it, kilde) })
        internal fun ghostdager(periode: Periode) =
            Sykdomstidslinje(periode.associateWith { if (it.erHelg()) UkjentDag(it, INGEN) else UkjentDag(it, INGEN) })

        internal fun arbeidsdager(førsteDato: LocalDate, sisteDato: LocalDate, kilde: Hendelseskilde) =
            arbeidsdager(Periode(førsteDato, sisteDato), kilde)

        internal fun sykedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            grad: Prosentdel,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap(
                            { it },
                            {
                                Økonomi.sykdomsgrad(grad).let { økonomi ->
                                    if (it.erHelg()) SykHelgedag(it, økonomi, kilde) else Sykedag(it, økonomi, kilde)
                                }

                            }
                        )
                    ))

        internal fun sykedagerNav(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            grad: Prosentdel,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap(
                            { it },
                            {
                                Økonomi.sykdomsgrad(grad).let { økonomi ->
                                    if (it.erHelg()) SykHelgedag(it, økonomi, kilde) else SykedagNav(it, økonomi, kilde)
                                }
                            }
                        )
                    ))

        internal fun ukjent(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap<LocalDate, LocalDate, Dag>(
                            { it },
                            { UkjentDag(it, kilde) }
                        )
                    ))

        internal fun sykedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            avskjæringsdato: LocalDate,
            grad: Prosentdel,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap(
                            { it },
                            {
                                Økonomi.sykdomsgrad(grad).let { økonomi ->
                                    if (it.erHelg()) SykHelgedag(it, økonomi, kilde) else sykedag(
                                        it,
                                        avskjæringsdato,
                                        økonomi,
                                        kilde
                                    )
                                }
                            })
                    )
            )

        private fun sykedag(
            dato: LocalDate,
            avskjæringsdato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = if (dato < avskjæringsdato) ForeldetSykedag(dato, økonomi, kilde) else Sykedag(dato, økonomi, kilde)

        internal fun arbeidsgiverdager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            grad: Prosentdel,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(
                        toMap(
                            { it },
                            {
                                Økonomi.sykdomsgrad(grad).let { økonomi ->
                                    if (it.erHelg()) ArbeidsgiverHelgedag(it, økonomi, kilde)
                                    else Arbeidsgiverdag(it, økonomi, kilde)
                                }
                            })
                    )
            )

        internal fun feriedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, Dag>({ it }, { Feriedag(it, kilde) }))
            )

        internal fun arbeidIkkeGjenopptatt(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, Dag>({ it }, { ArbeidIkkeGjenopptattDag(it, kilde) }))
            )

        internal fun permisjonsdager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, Dag>({ it }, { Permisjonsdag(it, kilde) }))
            )

        internal fun andreYtelsedager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde,
            ytelse: AnnenYtelse
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, Dag>({ it }, { AndreYtelser(it, kilde, ytelse) }))
            )

        internal fun problemdager(
            førsteDato: LocalDate,
            sisteDato: LocalDate,
            kilde: Hendelseskilde,
            melding: String
        ) =
            Sykdomstidslinje(
                førsteDato.datesUntil(sisteDato.plusDays(1))
                    .collect(toMap<LocalDate, LocalDate, Dag>({ it }, { ProblemDag(it, kilde, melding) }))
            )

        internal fun gjenopprett(dto: SykdomstidslinjeDto): Sykdomstidslinje {
            return Sykdomstidslinje(
                dager = dto.dager.associate { it.dato to Dag.gjenopprett(it) }.toSortedMap(),
                periode = dto.periode?.let { Periode.gjenopprett(it) },
                _låstePerioder = dto.låstePerioder.map { Periode.gjenopprett(it) }.toMutableList()
            )
        }
    }

    internal fun dto() = SykdomstidslinjeDto(
        dager = dager.map { (_, dag) -> dag.dto() },
        periode = periode?.dto(),
        låstePerioder = låstePerioder.map { it.dto() }
    )
}

internal fun List<Sykdomstidslinje>.merge(beste: BesteStrategy = default): Sykdomstidslinje =
    if (this.isEmpty()) Sykdomstidslinje()
    else reduce { result, tidslinje -> result.merge(tidslinje, beste) }
