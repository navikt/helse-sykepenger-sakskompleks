package no.nav.helse.økonomi

import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO
import java.math.MathContext
import no.nav.helse.etterlevelse.SubsumsjonObserver
import kotlin.math.pow

class Prosentdel private constructor(private val teller: Long, private val nevner: Long): Comparable<Prosentdel> {
    init {
        require(teller in 0..nevner) { "Må være prosent mellom 0 og 100: $teller / $nevner"}
        require(nevner > 0L) { "Kan ikke dele på 0 $teller / $nevner"}
    }

    companion object {
        private val mc = MathContext.DECIMAL128
        private val HUNDRE_PROSENT = Prosentdel(1, 1)
        private val GRENSE = 20.prosent
        private const val DESIMALNØYAKTIGHET = 8
        private val TIERPOTENS = 10.0.pow(DESIMALNØYAKTIGHET).toLong()

        internal fun ratio(a: Double, b: Double) =
            if (a < b) Prosentdel((a * TIERPOTENS).toLong(), (b * TIERPOTENS).toLong()) else HUNDRE_PROSENT

        fun subsumsjon(subsumsjonObserver: SubsumsjonObserver, block: SubsumsjonObserver.(Double) -> Unit) {
            subsumsjonObserver.block(GRENSE.toDouble())
        }

        internal fun Collection<Pair<Prosentdel, Double>>.average(): Prosentdel {
            return map { it.first to it.second.toBigDecimal(mc) }.average()
        }

        private fun Collection<Pair<Prosentdel, BigDecimal>>.average(): Prosentdel {
            // her adderes doubles; må derfor trengs det BigDecimal ellers løper man
            // risiko for at summeringen gir feil flyttal (pga. doubles + aritmetikk = dårlig idé)
            val total = this.sumOf { it.second }
            if (total <= ZERO) return map { it.first to ONE }.average()
            val nevnerne = productOf { (brøk, _) -> brøk.nevner }
            val teller = sumOf { (brøken, inntekt) ->
                // ganger telleren med de andre nevnerne (deler på egen nevner fordi vi skal gange med de -andre- nevnerne)
                val a = brøken.teller * (nevnerne / brøken.nevner)
                // ganger med inntekt (en double) så da går vi via bigdecimal
                a.toBigDecimal().multiply(inntekt)
            }
            val fellesnevner = (total * nevnerne.toBigDecimal()).toLong()
            return Prosentdel(teller.toLong(), fellesnevner)
        }

        private fun <T> Iterable<T>.productOf(multiplication: (T) -> Long): Long {
            var result = 1L
            for (product in this) {
                result *= multiplication(product)
            }
            return result
        }

        val Number.prosent get() =
            when (this) {
                is Double -> {
                    require(this in 0.0..100.0) { "ugyldig brøkdel: $this - må være mellom 0.0 og 100.0" }
                    // siden prosentdel ikke bruker doubles må vi konvertere dem til Long, men vi ganger
                    // den opp med et passe stort tall for å bevare mest mulige desimaler
                    Prosentdel((this * TIERPOTENS).toLong() / 100, TIERPOTENS)
                }
                else -> Prosentdel(this.toLong(), 100)
            }
    }

    override fun equals(other: Any?) = other is Prosentdel && this.equals(other)

    private fun equals(other: Prosentdel) = this.compareTo(other) == 0

    override fun hashCode() = (teller / nevner.toDouble()).hashCode()

    operator fun not() = Prosentdel(this.nevner - this.teller, this.nevner)

    internal operator fun div(other: Prosentdel) = Prosentdel(this.teller * other.nevner, this.nevner * other.teller)

    override fun compareTo(other: Prosentdel) = (this.teller * other.nevner).compareTo(other.teller * this.nevner)

    override fun toString(): String {
        return "${(toDouble())} % ($teller / $nevner)"
    }

    internal fun gradér(beløp: Double) = beløp * this.nevner / this.teller

    internal fun times(other: Double) = (other * this.teller) / this.nevner

    fun toDouble() = (this.teller * 100) / this.nevner.toDouble()

    internal fun erUnderGrensen() = this < GRENSE
}