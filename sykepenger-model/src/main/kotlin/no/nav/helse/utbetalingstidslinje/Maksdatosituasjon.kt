package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.plus
import no.nav.helse.ukedager

// todo: avviser ikke første sykdomshelg etter 70 år hvis det ikke har blitt avvist dager tidligere
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
        if (dato >= alder.syttiårsdagen) {
            maksdatovurdering.avvistDag(dato, Begrunnelse.Over70, sisteVirkedagFørFylte70år)
            return medTilstand(dato, State.ForGammel)
        }
        check(rettighetsvurdering.gjenståendeDager > 0) { "gjenstående dager må være større enn 0" }
        val nyTilstand = when {
            gjenståendeSykepengedagerUnder67 == 1 -> State.Karantene(Begrunnelse.SykepengedagerOppbrukt)
            gjenståendeSykepengedagerOver67 == 1 -> State.Karantene(Begrunnelse.SykepengedagerOppbruktOver67)
            else -> State.Syk
        }
        return Maksdatosituasjon(regler, dato, alder, startdatoSykepengerettighet!!, startdatoTreårsvindu, betalteDager + setOf(dato), maksdatovurdering, nyTilstand, 0).also {
            it.maksdatovurdering.forbruktDag(dato, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, oppholdsdager)
        }
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

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, Opphold, 1).also {
                    avgrenser.maksdatovurdering.oppholdsdag(dagen, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, 1)
                }
            }

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
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                /* betalbarDag skal ikke medføre ny rettighet */
                return vurderTilstrekkeligOppholdNådd(avgrenser, dagen).also {
                    it.tilstand.avvistDag(it, dagen)
                }
            }

            override fun avvistDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                avgrenser.maksdatovurdering.avvistDag(dagen, begrunnelse, avgrenser.rettighetsvurdering.maksdato)
                return avgrenser
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                /* helg skal ikke medføre ny rettighet */
                return vurderTilstrekkeligOppholdNådd(avgrenser, dagen).also {
                    it.maksdatovurdering.oppholdsdag(dagen, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, it.oppholdsdager)
                }
            }

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, this)
            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return vurderTilstrekkeligOppholdNådd(avgrenser, dagen).also {
                    it.maksdatovurdering.oppholdsdag(dagen, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, it.oppholdsdager)
                }
            }

            private fun vurderTilstrekkeligOppholdNådd(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                val oppholdsdager = avgrenser.oppholdsdager + 1
                if (oppholdsdager > TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) return avgrenser.medTilstand(dagen, KaranteneTilstrekkeligOppholdNådd, oppholdsdager)
                return avgrenser.medTilstand(dagen, this, oppholdsdager)
            }
        }

        object KaranteneTilstrekkeligOppholdNådd : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, KaranteneTilstrekkeligOppholdNådd, avgrenser.oppholdsdager).also {
                    it.tilstand.avvistDag(it, dagen)
                }
            }

            override fun avvistDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                avgrenser.maksdatovurdering.avvistDag(dagen, Begrunnelse.NyVilkårsprøvingNødvendig, avgrenser.rettighetsvurdering.maksdato)
                return avgrenser
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, KaranteneTilstrekkeligOppholdNådd, avgrenser.oppholdsdager).also {
                    it.maksdatovurdering.oppholdsdag(dagen, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, it.oppholdsdager)
                }
            }

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? {
                avgrenser.maksdatovurdering.oppholdsdag(dagen, avgrenser.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, avgrenser.oppholdsdager)
                return null
            }

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, KaranteneTilstrekkeligOppholdNådd, avgrenser.oppholdsdager).also {
                    it.maksdatovurdering.oppholdsdag(dagen, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, it.oppholdsdager)
                }
            }
        }

        object ForGammel : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return over70(avgrenser, dagen)
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return over70(avgrenser, dagen)
            }

            private fun over70(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                avgrenser.maksdatovurdering.avvistDag(dagen, Begrunnelse.Over70, avgrenser.sisteVirkedagFørFylte70år)
                return avgrenser
            }
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