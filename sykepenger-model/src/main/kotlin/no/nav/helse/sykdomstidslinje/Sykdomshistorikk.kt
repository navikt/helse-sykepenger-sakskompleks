package no.nav.helse.sykdomstidslinje

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.SykdomshistorikkDto
import no.nav.helse.dto.SykdomshistorikkElementDto
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk.Element.Companion.uhåndtertSykdomstidslinje
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

    internal fun håndter(hendelse: SykdomshistorikkHendelse): Sykdomstidslinje {
        val s = hendelse.sykdomstidslinje()
        val m = hendelse.meldingsreferanseId()

        val nyttElement = Element.opprett(m, s)
        val uhåndtertSykdomstidslinje = elementer.uhåndtertSykdomstidslinje(hendelse) ?: return sykdomstidslinje()
        elementer.add(0, nyttElement.merge(this, uhåndtertSykdomstidslinje))
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
        val hendelseSykdomstidslinje: Sykdomstidslinje = Sykdomstidslinje(),
        val beregnetSykdomstidslinje: Sykdomstidslinje = Sykdomstidslinje(),
    ) : Comparable<Element> {

        internal fun merge(historikk: Sykdomshistorikk, uhåndtertSykdomstidslinje: Sykdomstidslinje): Element {
            val beregnetSykdomstidslinje = mergeTidslinje(historikk.elementer.firstOrNull(), uhåndtertSykdomstidslinje)
            return Element(
                id = this.id,
                hendelseId = this.hendelseId,
                tidsstempel = this.tidsstempel,
                hendelseSykdomstidslinje = uhåndtertSykdomstidslinje,
                beregnetSykdomstidslinje = beregnetSykdomstidslinje
            )
        }

        private fun mergeTidslinje(forrige: Element?, uhåndtertSykdomstidslinje: Sykdomstidslinje) =
            forrige?.beregnetSykdomstidslinje?.merge(uhåndtertSykdomstidslinje, Dagturnering.TURNERING::beste) ?: uhåndtertSykdomstidslinje

        override fun compareTo(other: Element) = this.tidsstempel.compareTo(other.tidsstempel)

        override fun toString() = beregnetSykdomstidslinje.toString()

        internal fun harHåndtert(hendelse: SykdomshistorikkHendelse) = hendelseId == hendelse.meldingsreferanseId()

        internal fun isEmpty(): Boolean = !beregnetSykdomstidslinje.iterator().hasNext()

        companion object {
            internal fun List<Element>.uhåndtertSykdomstidslinje(hendelse: SykdomshistorikkHendelse) : Sykdomstidslinje? {
                if (hendelse.sykdomstidslinje().periode() == null) return null // tom sykdomstidslinje
                val tidligere = filter { it.harHåndtert(hendelse) }.takeUnless { it.isEmpty() } ?: return hendelse.sykdomstidslinje() // Første gang vi ser hendelsen
                val alleredeHåndtertSykdomstidslinje = tidligere.fold(Sykdomstidslinje()) { tidligereHåndtert, element ->
                    tidligereHåndtert + element.hendelseSykdomstidslinje
                }
                val uhåndtertSykdomstidslinje = hendelse.sykdomstidslinje() - alleredeHåndtertSykdomstidslinje
                if (uhåndtertSykdomstidslinje.periode() == null) return null // Tom sykdomstidslinje, ikke noe nytt
                return uhåndtertSykdomstidslinje.also {
                    hendelse.info("Legger til bit nummer ${tidligere.size +1 } for ${it.periode()} i sykdomshistorikken")
                }
            }

            internal fun sykdomstidslinje(elementer: List<Element>) = elementer.first().beregnetSykdomstidslinje

            internal fun opprett(
                meldingsreferanseId: UUID,
                hendelseSykdomstidslinje: Sykdomstidslinje
            ): Element {
                return Element(
                    hendelseId = meldingsreferanseId,
                    hendelseSykdomstidslinje = hendelseSykdomstidslinje,
                )
            }

            internal fun opprettReset(
                historikk: Sykdomshistorikk,
                perioder: List<Periode>
            ): Element {
                return Element(beregnetSykdomstidslinje = historikk.sykdomstidslinje().trim(perioder))
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
