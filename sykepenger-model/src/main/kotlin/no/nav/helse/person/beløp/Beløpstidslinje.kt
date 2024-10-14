package no.nav.helse.person.beløp

import java.time.LocalDate
import java.util.SortedMap
import no.nav.helse.dto.BeløpstidslinjeDto
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.nesteDag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig

data class Beløpstidslinje(private val dager: SortedMap<LocalDate, Beløpsdag>) : Collection<Dag> by dager.values {
    private constructor(dager: Map<LocalDate, Beløpsdag>): this(dager.toSortedMap())

    constructor(dager: List<Beløpsdag> = emptyList()): this(dager.associateBy { it.dato }.toSortedMap().also {
        require(dager.size == it.size) { "Forsøkte å opprette en beløpstidslinje med duplikate datoer. Det blir for rart for meg." }
    })

    internal constructor(vararg dager: Beløpsdag):this(dager.toList())

    private val periode = if (dager.isEmpty()) null else dager.firstKey() til dager.lastKey()

    internal operator fun get(dato: LocalDate): Dag = dager[dato] ?: UkjentDag

    override operator fun iterator(): Iterator<Dag> {
        if (periode == null) return emptyList<Dag>().iterator()
        return object : Iterator<Dag> {
            private val periodeIterator = periode.iterator()
            override fun hasNext() = periodeIterator.hasNext()
            override fun next() = this@Beløpstidslinje[periodeIterator.next()]
        }
    }

    operator fun plus(other: Beløpstidslinje) = merge(other, BevareEksisterendeOpplysningHvisLikeBeløp)

    private fun merge(other: Beløpstidslinje, strategi: BesteRefusjonsopplysningstrategi): Beløpstidslinje {
        val results = this.dager.toMutableMap()
        other.dager.forEach { (key, dag) ->
            results.merge(key, dag, strategi)
        }
        return Beløpstidslinje(results)
    }

    internal operator fun minus(datoer: Iterable<LocalDate>) = Beløpstidslinje(this.dager.filterKeys { it !in datoer })
    internal operator fun minus(other: Beløpstidslinje): Beløpstidslinje {
        if (other.periode == null) return this
        return this - other.periode
    }
    internal operator fun minus(dato: LocalDate) = Beløpstidslinje(this.dager.filterKeys { it != dato })

    internal fun subset(periode: Periode): Beløpstidslinje {
        if (this.periode == null || !this.periode.overlapperMed(periode)) return Beløpstidslinje()
        return Beløpstidslinje(dager.subMap(periode.start, periode.endInclusive.nesteDag).toSortedMap())
    }

    internal fun fraOgMed(dato: LocalDate) = Beløpstidslinje(dager.tailMap(dato).toSortedMap())

    private fun snute(snute: LocalDate): Beløpstidslinje {
        val førsteBeløpsdag = periode?.start?.let { dager.getValue(it) } ?: return Beløpstidslinje()
        return førsteBeløpsdag.strekkTilbake(snute)
    }

    private fun hale(hale: LocalDate): Beløpstidslinje {
        val sisteBeløpsdag = periode?.endInclusive?.let { dager.getValue(it) } ?: return Beløpstidslinje()
        return sisteBeløpsdag.strekkFrem(hale)
    }

    internal fun strekk(periode: Periode) = snute(periode.start) + this + hale(periode.endInclusive)
    internal fun strekkFrem(til: LocalDate) = this + hale(til)

    internal fun førsteEndring(other: Beløpstidslinje): LocalDate? {
        val fom = setOfNotNull(periode?.start, other.periode?.start).minOrNull() ?: return null
        val tom = setOfNotNull(periode?.endInclusive, other.periode?.endInclusive).max()
        return (fom til tom).firstOrNull { dato ->
            val dennes = get(dato)
            val andres = other[dato]
            if (dennes is UkjentDag && andres is UkjentDag) false
            else if (dennes is UkjentDag || andres is UkjentDag) true
            else dennes.beløp != andres.beløp
        }
    }

    internal fun dto() = BeløpstidslinjeDto(
        perioder = dager
            .map { (_, dag) ->
                BeløpstidslinjeDto.BeløpstidslinjeperiodeDto(
                    fom = dag.dato,
                    tom = dag.dato,
                    dagligBeløp = dag.beløp.daglig,
                    kilde = BeløpstidslinjeDto.BeløpstidslinjedagKildeDto(
                        meldingsreferanseId = dag.kilde.meldingsreferanseId,
                        avsender = dag.kilde.avsender.dto(),
                        tidsstempel = dag.kilde.tidsstempel
                    )
                )
            }
            .fold(emptyList()) { result, dag ->
                when {
                    result.isEmpty() -> listOf(dag)
                    result.last().kanUtvidesAv(dag) -> result.dropLast(1) + result.last().copy(tom = dag.tom)
                    else -> result.plusElement(dag)
                }
            }
    )

    companion object {
        private val BevareEksisterendeOpplysningHvisLikeBeløp: BesteRefusjonsopplysningstrategi = { venstre: Beløpsdag, høyre: Beløpsdag ->
            when {
                venstre.beløp == høyre.beløp -> venstre
                venstre.kilde.tidsstempel > høyre.kilde.tidsstempel -> venstre
                else -> høyre
            }
        }
        fun fra(periode: Periode, beløp: Inntekt, kilde: Kilde) = Beløpstidslinje(periode.map { Beløpsdag(it, beløp, kilde) })
        internal fun gjenopprett(dto: BeløpstidslinjeDto) = Beløpstidslinje(
            dager = dto.perioder
                .flatMap {
                    (it.fom til it.tom).map { dato ->
                        Beløpsdag(
                            dato = dato,
                            beløp = it.dagligBeløp.daglig,
                            kilde = Kilde(
                                meldingsreferanseId = it.kilde.meldingsreferanseId,
                                avsender = Avsender.gjenopprett(it.kilde.avsender),
                                tidsstempel = it.kilde.tidsstempel
                            )
                        )
                    }
                }
        )
    }
}

internal typealias BesteRefusjonsopplysningstrategi = (Beløpsdag, Beløpsdag) -> Beløpsdag

sealed interface Dag {
    val dato: LocalDate
    val beløp: Inntekt
    val kilde: Kilde
}

data class Beløpsdag(
    override val dato: LocalDate,
    override val beløp: Inntekt,
    override val kilde: Kilde
): Dag {
    fun strekk(periode: Periode) = Beløpstidslinje(periode.map { copy(dato = it) })
    fun strekkTilbake(datoFør: LocalDate) = if (datoFør > dato) Beløpstidslinje() else strekk(datoFør til dato)
    fun strekkFrem(datoEtter: LocalDate) = if (datoEtter < dato) Beløpstidslinje() else strekk(dato til datoEtter)
}

data object UkjentDag : Dag {
    override val dato get() = error("En ukjent dag har ikke en dato")
    override val beløp get() = error("En ukjent dag har ikke et beløp")
    override val kilde get() = error("En ukjent dag har ikke et kilde")
}
