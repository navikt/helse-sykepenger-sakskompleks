package no.nav.helse.sykdomstidslinje

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.SykdomshistorikkDto
import no.nav.helse.dto.SykdomshistorikkElementDto
import no.nav.helse.hendelser.Periode
import no.nav.helse.tournament.Dagturnering

internal class Sykdomshistorikk private constructor(
    private val elementer: MutableList<Element>
) {
    internal constructor() : this(mutableListOf())

    internal val size get() = elementer.size

    internal fun isEmpty() = elementer.isEmpty()

    internal fun harSykdom() = !isEmpty() && !elementer.first().isEmpty()

    internal fun sykdomstidslinje() = Element.sykdomstidslinje(elementer)

    fun view() = SykdomshistorikkView(elementer = elementer.map { it.view() })

    internal fun håndter(meldingsreferanseId: UUID, sykdomstidslinje: Sykdomstidslinje): Sykdomstidslinje {
        elementer.add(0, Element.opprett(this, meldingsreferanseId, sykdomstidslinje))
        return sykdomstidslinje()
    }

    internal fun fjernDager(perioder: List<Periode>) {
        if (perioder.isEmpty()) return
        if (isEmpty()) return
        val periode = sykdomstidslinje().periode() ?: return
        if (perioder.none { periode.overlapperMed(it) }) return
        elementer.add(0, Element.opprettReset(this, perioder))
    }

    class Element private constructor(
        val id: UUID = UUID.randomUUID(),
        val hendelseId: UUID? = null,
        val tidsstempel: LocalDateTime = LocalDateTime.now(),
        val hendelseSykdomstidslinje: Sykdomstidslinje,
        val beregnetSykdomstidslinje: Sykdomstidslinje
    ) : Comparable<Element> {

        override fun compareTo(other: Element) = this.tidsstempel.compareTo(other.tidsstempel)

        override fun toString() = beregnetSykdomstidslinje.toString()

        internal fun isEmpty(): Boolean = !beregnetSykdomstidslinje.iterator().hasNext()

        companion object {
            internal fun sykdomstidslinje(elementer: List<Element>) = elementer.first().beregnetSykdomstidslinje

            internal fun opprett(
                sykdomshistorikk: Sykdomshistorikk,
                meldingsreferanseId: UUID,
                hendelseSykdomstidslinje: Sykdomstidslinje
            ): Element {
                val beregnetSykdomstidslinje = if (!sykdomshistorikk.isEmpty())
                    sykdomshistorikk.sykdomstidslinje().merge(hendelseSykdomstidslinje, Dagturnering.TURNERING::beste)
                else hendelseSykdomstidslinje
                return Element(
                    hendelseId = meldingsreferanseId,
                    hendelseSykdomstidslinje = hendelseSykdomstidslinje,
                    beregnetSykdomstidslinje = beregnetSykdomstidslinje
                )
            }

            internal fun opprettReset(
                historikk: Sykdomshistorikk,
                perioder: List<Periode>
            ): Element {
                return Element(
                    hendelseSykdomstidslinje = Sykdomstidslinje(),
                    beregnetSykdomstidslinje = historikk.sykdomstidslinje().trim(perioder)
                )
            }

            internal fun gjenopprett(dto: SykdomshistorikkElementDto): Element {
                return Element(
                    id = dto.id,
                    hendelseId = dto.hendelseId,
                    tidsstempel = dto.tidsstempel,
                    hendelseSykdomstidslinje = Sykdomstidslinje.gjenopprett(dto.hendelseSykdomstidslinje),
                    beregnetSykdomstidslinje = Sykdomstidslinje.gjenopprett(dto.beregnetSykdomstidslinje)
                )
            }
        }

        internal fun view() = SykdomshistorikkElementView(
            id = id,
            hendelseId = hendelseId,
            tidsstempel = tidsstempel,
            hendelseSykdomstidslinje = hendelseSykdomstidslinje,
            beregnetSykdomstidslinje = beregnetSykdomstidslinje
        )

        internal fun dto() = SykdomshistorikkElementDto(
            id = id,
            hendelseId = hendelseId,
            tidsstempel = tidsstempel,
            hendelseSykdomstidslinje = hendelseSykdomstidslinje.dto(),
            beregnetSykdomstidslinje = beregnetSykdomstidslinje.dto(),
        )
    }

    internal fun dto() = SykdomshistorikkDto(
        elementer = elementer.map { it.dto() }
    )

    internal companion object {

        internal fun gjenopprett(dto: SykdomshistorikkDto): Sykdomshistorikk {
            return Sykdomshistorikk(
                elementer = dto.elementer.map { Element.gjenopprett(it) }.toMutableList()
            )
        }
    }
}

internal data class SykdomshistorikkView(val elementer: List<SykdomshistorikkElementView>)
internal data class SykdomshistorikkElementView(
    val id: UUID,
    val hendelseId: UUID?,
    val tidsstempel: LocalDateTime,
    val hendelseSykdomstidslinje: Sykdomstidslinje,
    val beregnetSykdomstidslinje: Sykdomstidslinje
)
