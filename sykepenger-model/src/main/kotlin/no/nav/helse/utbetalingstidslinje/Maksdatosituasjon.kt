package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.plus
import no.nav.helse.ukedager

internal class Maksdatosituasjon private constructor(
    private val regler: ArbeidsgiverRegler,
    private val dato: LocalDate,
    private val alder: Alder,
    startdatoSykepengerettighet: LocalDate,
    private val startdatoTreårsvindu: LocalDate,
    private val betalteDager: Set<LocalDate>,
    private val maksdatovurdering: Maksdatovurdering,
    private val tilstand: State,
    private val oppholdsdager: Int = 0
) {
    private constructor(
        regler: ArbeidsgiverRegler,
        dato: LocalDate,
        alder: Alder,
        startdatoSykepengerettighet: LocalDate,
        startdatoTreårsvindu: LocalDate,
        betalteDager: Set<LocalDate>,
        maksdatovurdering: Maksdatovurdering
    ) : this(regler, dato, alder, startdatoSykepengerettighet, startdatoTreårsvindu, betalteDager, maksdatovurdering, State.Initiell)

    internal constructor(regler: ArbeidsgiverRegler, dato: LocalDate, alder: Alder, maksdatovurdering: Maksdatovurdering) :
            this(regler, dato, alder, LocalDate.MIN, LocalDate.MIN, emptySet(), maksdatovurdering)

    private val redusertYtelseAlder = alder.redusertYtelseAlder
    private val syttiårsdagen = alder.syttiårsdagen
    private val sisteVirkedagFørFylte70år = syttiårsdagen.forrigeVirkedagFør()

    private val forbrukteDager = betalteDager.size
    private val forbrukteDagerOver67 = betalteDager.count { it > redusertYtelseAlder }

    private val gjenståendeSykepengedagerUnder67 = regler.maksSykepengedager() - forbrukteDager

    private val gjenståendeSykepengedagerOver67 = regler.maksSykepengedagerOver67() - forbrukteDagerOver67
    private val startdatoSykepengerettighet = startdatoSykepengerettighet.takeUnless { it == LocalDate.MIN }

    private val harNåddMaks = minOf(gjenståendeSykepengedagerOver67, gjenståendeSykepengedagerUnder67) == 0
    private val forrigeMaksdato = if (harNåddMaks) betalteDager.last() else null
    private val forrigeVirkedag = forrigeMaksdato ?: dato.sisteVirkedagInklusiv()

    private val maksdatoOrdinærRett = forrigeVirkedag + gjenståendeSykepengedagerUnder67.ukedager
    private val maksdatoBegrensetRett = maxOf(forrigeVirkedag, redusertYtelseAlder) + gjenståendeSykepengedagerOver67.ukedager

    internal val rettighetsvurdering: Rettighetsvurdering

    init {
        // maksdato er den dagen som først inntreffer blant ordinær kvote, 67-års-kvoten og 70-årsdagen,
        // med mindre man allerede har brukt opp alt tidligere
        when {
            maksdatoOrdinærRett <= maksdatoBegrensetRett -> {
                rettighetsvurdering = Rettighetsvurdering(
                    maksdato = maksdatoOrdinærRett,
                    forbrukteDager = forbrukteDager,
                    gjenståendeDager = gjenståendeSykepengedagerUnder67,
                    syttiårsdagen = syttiårsdagen,
                    bestemmelse = Rettighetsvurdering.Maksdatobestemmelse.OrdinærRett(startdatoSykepengerettighet)
                )
            }
            maksdatoBegrensetRett <= sisteVirkedagFørFylte70år -> {
                rettighetsvurdering = Rettighetsvurdering(
                    maksdato = maksdatoBegrensetRett,
                    forbrukteDager = forbrukteDager,
                    gjenståendeDager = ukedager(forrigeVirkedag, maksdatoBegrensetRett),
                    syttiårsdagen = syttiårsdagen,
                    bestemmelse = Rettighetsvurdering.Maksdatobestemmelse.BegrensetRett(startdatoSykepengerettighet)
                )
            }
            else -> {
                rettighetsvurdering = Rettighetsvurdering(
                    maksdato = sisteVirkedagFørFylte70år,
                    forbrukteDager = forbrukteDager,
                    gjenståendeDager = ukedager(forrigeVirkedag, sisteVirkedagFørFylte70år),
                    syttiårsdagen = syttiårsdagen,
                    bestemmelse = Rettighetsvurdering.Maksdatobestemmelse.Over70()
                )
            }
        }
    }

    private fun inkrementer(dato: LocalDate): Maksdatosituasjon {
        if (dato >= alder.syttiårsdagen) return avvistOver70(dato)
        return forbruktDag(dato)
    }

    private fun forbruktDag(dato: LocalDate): Maksdatosituasjon {
        check(gjenståendeSykepengedagerOver67 > 0 && gjenståendeSykepengedagerUnder67 > 0) { "gjenstående dager må være større enn 0" }
        val nyTilstand = when {
            gjenståendeSykepengedagerUnder67 == 1 -> State.Karantene(Begrunnelse.SykepengedagerOppbrukt)
            gjenståendeSykepengedagerOver67 == 1 -> State.Karantene(Begrunnelse.SykepengedagerOppbruktOver67)
            else -> State.Syk
        }
        return Maksdatosituasjon(regler, dato, alder, startdatoSykepengerettighet!!, startdatoTreårsvindu, betalteDager + setOf(dato), maksdatovurdering, nyTilstand, 0).also {
            it.maksdatovurdering.forbruktDag(dato, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, oppholdsdager)
        }
    }

    private fun avvistOver70(dato: LocalDate): Maksdatosituasjon {
        return medTilstand(dato, State.ForGammel).also {
            it.maksdatovurdering.avvistDag(dato, Begrunnelse.Over70, it.sisteVirkedagFørFylte70år)
        }
    }

    private fun avvistDag(dato: LocalDate, begrunnelse: Begrunnelse): Maksdatosituasjon {
        maksdatovurdering.avvistDag(dato, begrunnelse, rettighetsvurdering.maksdato)
        return this
    }

    private fun medTilstand(dato: LocalDate, nyTilstand: State, oppholdsdager: Int = this.oppholdsdager): Maksdatosituasjon {
        return Maksdatosituasjon(regler, dato, alder, startdatoSykepengerettighet!!, startdatoTreårsvindu, betalteDager, maksdatovurdering, nyTilstand, oppholdsdager)
    }

    // tilgir forbrukte dager som følge av at treårsvinduet forskyves
    private fun dekrementer(dagen: LocalDate): Maksdatosituasjon {
        val nyStartdatoTreårsvindu = dagen.minusYears(HISTORISK_PERIODE_I_ÅR)
        val nyBetalteDager = betalteDager.filter { it >= nyStartdatoTreårsvindu }.toSet()
        //println("Tilgir ${betalteDager.size - nyBetalteDager.size} dager som følge av at treårsvindu flyttes fra $startdatoTreårsvindu til $nyStartdatoTreårsvindu")
        return Maksdatosituasjon(regler, dato, alder, startdatoSykepengerettighet!!, nyStartdatoTreårsvindu, nyBetalteDager, maksdatovurdering, this.tilstand)
    }

    internal fun maksdatoFor(dato: LocalDate): Rettighetsvurdering {
        return Maksdatosituasjon(regler, dato, alder, startdatoSykepengerettighet!!, startdatoTreårsvindu, betalteDager, maksdatovurdering).rettighetsvurdering
    }

    fun betalbarDag(dagen: LocalDate) = tilstand.betalbarDag(this, dagen)
    fun oppholdsdag(dagen: LocalDate) = tilstand.oppholdsdag(this, dagen)
    fun sykdomshelg(dagen: LocalDate) = tilstand.sykdomshelg(this, dagen)
    fun fridag(dagen: LocalDate) = tilstand.fridag(this, dagen)
    fun avvistDag(dagen: LocalDate) = tilstand.avvistDag(this, dagen)

    private fun håndterOpphold(dagen: LocalDate, nyTilstand: State): Maksdatosituasjon? {
        val oppholdsdager = oppholdsdager + 1
        if (oppholdsdager >= TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) {
            maksdatovurdering.oppholdsdag(dagen, rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, oppholdsdager)
            return null
        }
        return medTilstand(dagen, nyTilstand, oppholdsdager).also {
            maksdatovurdering.oppholdsdag(dagen, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, oppholdsdager)
        }
    }

    private interface State {
        fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
            throw NotImplementedError()
        }
        fun avvistDag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser
        fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? = avgrenser // return null indikerer tilbakestilling/nok opphold nådd
        fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? = avgrenser // return null indikerer tilbakestilling/nok opphold nådd
        fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? = avgrenser // return null indikerer tilbakestilling/nok opphold nådd

        object Initiell : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return Maksdatosituasjon(avgrenser.regler, dagen, avgrenser.alder, dagen, dagen.minusYears(HISTORISK_PERIODE_I_ÅR), emptySet(), avgrenser.maksdatovurdering)
                    .inkrementer(dagen)
            }

            override fun avvistDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                throw IllegalStateException("Maksdatosituasjon må begynne med en betalbardag")
            }

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? {
                throw IllegalStateException("Maksdatosituasjon må begynne med en betalbardag")
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? {
                throw IllegalStateException("Maksdatosituasjon må begynne med en betalbardag")
            }

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? {
                throw IllegalStateException("Maksdatosituasjon må begynne med en betalbardag")
            }
        }

        object Syk : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.inkrementer(dagen)
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? {
                if (dagen < avgrenser.syttiårsdagen) return avgrenser
                return avgrenser.avvistOver70(dagen)
            }

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, Opphold)

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, OppholdFri, 1)
            }
        }

        object Opphold : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.dekrementer(dagen).inkrementer(dagen)
            }

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = oppholdsdag(avgrenser, dagen)

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = oppholdsdag(avgrenser, dagen)
            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, Opphold)
        }

        object OppholdFri : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.inkrementer(dagen)
            }

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, OppholdFri)
            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, Opphold)
        }

        class Karantene(private val begrunnelse: Begrunnelse) : State {
            /* betalbarDag skal ikke medføre ny rettighet */
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                avvistDag(avgrenser, dagen)
                return håndterOppholdsdagUtenNyRettighet(avgrenser, dagen)
            }
            /* helg skal ikke medføre ny rettighet */
            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = håndterOppholdsdagUtenNyRettighet(avgrenser, dagen)
            /* fridag skal ikke medføre ny rettighet */
            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = håndterOppholdsdagUtenNyRettighet(avgrenser, dagen)
            override fun avvistDag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.avvistDag(dagen, begrunnelse)
            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, this)

            private fun håndterOppholdsdagUtenNyRettighet(avgrenser: Maksdatosituasjon, dato: LocalDate) =
                avgrenser.håndterOpphold(dato, this) ?: avgrenser.medTilstand(dato, KaranteneTilstrekkeligOppholdNådd)
        }

        object KaranteneTilstrekkeligOppholdNådd : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, KaranteneTilstrekkeligOppholdNådd, avgrenser.oppholdsdager).also {
                    it.tilstand.avvistDag(it, dagen)
                }
            }

            override fun avvistDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.avvistDag(dagen, Begrunnelse.NyVilkårsprøvingNødvendig)
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = håndterOppholdUtenNyRettighet(avgrenser, dagen)

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? {
                avgrenser.maksdatovurdering.oppholdsdag(dagen, avgrenser.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, avgrenser.oppholdsdager)
                return null
            }

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = håndterOppholdUtenNyRettighet(avgrenser, dagen)

            private fun håndterOppholdUtenNyRettighet(avgrenser: Maksdatosituasjon, dato: LocalDate) =
                avgrenser.medTilstand(dato, this, avgrenser.oppholdsdager).also {
                    it.maksdatovurdering.oppholdsdag(dato, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, it.oppholdsdager)
                }

        }

        object ForGammel : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.avvistOver70(dagen)
            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.avvistOver70(dagen)
        }
    }

    private companion object {
        const val TILSTREKKELIG_OPPHOLD_I_SYKEDAGER = 26 * 7
        private const val HISTORISK_PERIODE_I_ÅR: Long = 3

        fun LocalDate.forrigeVirkedagFør() = minusDays(when (dayOfWeek) {
            SUNDAY -> 2
            MONDAY -> 3
            else -> 1
        })
        fun LocalDate.sisteVirkedagInklusiv() = when (dayOfWeek) {
            SATURDAY -> minusDays(1)
            SUNDAY -> minusDays(2)
            else -> this
        }
    }
}

internal interface Maksdatovurdering {
    fun forbruktDag(dato: LocalDate, rettighetsvurdering: Rettighetsvurdering, tilstrekkeligOpphold: Int, oppholdsdager: Int)
    fun oppholdsdag(dato: LocalDate, rettighetsvurdering: Rettighetsvurdering, tilstrekkeligOpphold: Int, oppholdsdager: Int)
    fun avvistDag(dato: LocalDate, begrunnelse: Begrunnelse, maksdato: LocalDate) {}

    companion object {
        val Nullvurdering = object : Maksdatovurdering {
            override fun forbruktDag(dato: LocalDate, rettighetsvurdering: Rettighetsvurdering, tilstrekkeligOpphold: Int, oppholdsdager: Int) {}
            override fun oppholdsdag(dato: LocalDate, rettighetsvurdering: Rettighetsvurdering, tilstrekkeligOpphold: Int, oppholdsdager: Int) {}
        }
    }
}