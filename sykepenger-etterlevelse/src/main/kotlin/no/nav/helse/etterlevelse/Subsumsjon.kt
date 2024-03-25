package no.nav.helse.etterlevelse

import java.time.LocalDate
import no.nav.helse.etterlevelse.RangeIterator.Companion.merge

abstract class Subsumsjon {

    enum class Utfall {
        VILKAR_OPPFYLT, VILKAR_IKKE_OPPFYLT, VILKAR_UAVKLART, VILKAR_BEREGNET
    }

    abstract val lovverk: String
    abstract val utfall: Utfall
    abstract val versjon: LocalDate
    abstract val paragraf: Paragraf
    abstract val ledd: Ledd?
    open val punktum: Punktum? = null
    open val bokstav: Bokstav? = null

    abstract val input: Map<String, Any>
    protected abstract val kontekster: Map<String, KontekstType>

    protected abstract fun output(): Map<String, Any>

    fun accept(visitor: SubsumsjonVisitor) {
        visitor.preVisitSubsumsjon(utfall, lovverk, versjon, paragraf, ledd, punktum, bokstav, input, output(), kontekster)
        acceptSpesifikk(visitor)
        visitor.postVisitSubsumsjon(utfall, lovverk, versjon, paragraf, ledd, punktum, bokstav, input, output(), kontekster)
    }

    protected abstract fun acceptSpesifikk(visitor: SubsumsjonVisitor)

    abstract fun sammenstill(subsumsjoner: List<Subsumsjon>): Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Subsumsjon) return false
        if (other::class != this::class) return false
        return ekstraEquals(other) && utfall == other.utfall &&
            paragraf == other.paragraf &&
            ledd == other.ledd &&
            punktum == other.punktum &&
            bokstav == other.bokstav &&
            input == other.input &&
            kontekster == other.kontekster
    }

    protected open fun ekstraEquals(other: Subsumsjon): Boolean {
        return other.versjon == this.versjon && other.output() == this.output()
    }

    override fun hashCode(): Int {
        var result = utfall.hashCode()
        result = 31 * result + versjon.hashCode()
        result = 31 * result + paragraf.hashCode()
        result = 31 * result + ledd.hashCode()
        result = 31 * result + punktum.hashCode()
        result = 31 * result + bokstav.hashCode()
        result = 31 * result + input.hashCode()
        result = 31 * result + output().hashCode()
        result = 31 * result + kontekster.hashCode()
        return result
    }

    override fun toString(): String {
        return "$paragraf $ledd ${if (punktum == null) "" else punktum} ${if (bokstav == null) "" else bokstav} [$utfall]"
    }
}

class EnkelSubsumsjon(
    override val utfall: Utfall,
    override val lovverk: String,
    override val versjon: LocalDate,
    override val paragraf: Paragraf,
    override val ledd: Ledd?,
    override val punktum: Punktum? = null,
    override val bokstav: Bokstav? = null,
    override val input: Map<String, Any>,
    private val output: Map<String, Any>,
    override val kontekster: Map<String, KontekstType>
) : Subsumsjon() {
    override fun sammenstill(subsumsjoner: List<Subsumsjon>) = false

    override fun output() = output
    override fun acceptSpesifikk(visitor: SubsumsjonVisitor) {}
}

class GrupperbarSubsumsjon private constructor(
    private var perioder: List<ClosedRange<LocalDate>>,
    override val lovverk: String,
    override val utfall: Utfall,
    override val versjon: LocalDate,
    override val paragraf: Paragraf,
    override val ledd: Ledd?,
    override val punktum: Punktum? = null,
    override val bokstav: Bokstav? = null,
    private val originalOutput: Map<String, Any>,
    override val input: Map<String, Any>,
    override val kontekster: Map<String, KontekstType>
) : Subsumsjon() {
    constructor(
        dato: LocalDate,
        lovverk: String,
        input: Map<String, Any>,
        output: Map<String, Any>,
        utfall: Utfall,
        versjon: LocalDate,
        paragraf: Paragraf,
        ledd: Ledd?,
        punktum: Punktum? = null,
        bokstav: Bokstav? = null,
        kontekster: Map<String, KontekstType>
    ) : this(mutableListOf(dato.rangeTo(dato)), lovverk, utfall, versjon, paragraf, ledd, punktum, bokstav, output, input, kontekster)

    override fun output(): Map<String, Any> {
        return originalOutput.toMutableMap().apply {
            this["perioder"] = perioder.map {
                mapOf(
                    "fom" to it.start,
                    "tom" to it.endInclusive
                )
            }
        }
    }

    override fun acceptSpesifikk(visitor: SubsumsjonVisitor) {
        visitor.visitGrupperbarSubsumsjon(perioder)
    }

    override fun ekstraEquals(other: Subsumsjon): Boolean {
        if (other !is GrupperbarSubsumsjon) return false
        return originalOutput == other.originalOutput
    }

    override fun sammenstill(subsumsjoner: List<Subsumsjon>): Boolean {
        val medSammeDatagrunnlag = subsumsjoner
            .asSequence()
            .filterIsInstance<GrupperbarSubsumsjon>()
            .firstOrNull { it == this } ?: return false
        medSammeDatagrunnlag.sammenstillMed(this)
        return true
    }

    private fun sammenstillMed(ny: GrupperbarSubsumsjon) {
        this.perioder = this.perioder.plusElement(ny.perioder.single()).merge()
    }
}

class BetingetSubsumsjon(
    private val funnetRelevant: Boolean,
    override val lovverk: String,
    override val utfall: Utfall,
    override val versjon: LocalDate,
    override val paragraf: Paragraf,
    override val ledd: Ledd?,
    override val punktum: Punktum? = null,
    override val bokstav: Bokstav? = null,
    override val input: Map<String, Any>,
    private val output: Map<String, Any>,
    override val kontekster: Map<String, KontekstType>
) : Subsumsjon() {
    override fun output() = output
    override fun sammenstill(subsumsjoner: List<Subsumsjon>) = !funnetRelevant

    override fun acceptSpesifikk(visitor: SubsumsjonVisitor) {
        visitor.visitBetingetSubsumsjon(funnetRelevant)
    }
}
