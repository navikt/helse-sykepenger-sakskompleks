package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.Tidslinjedag
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.plus
import no.nav.helse.ukedager

internal class Rettighetsvurdering private constructor(
    private val regler: ArbeidsgiverRegler,
    private val alder: Alder,
    private val dato: LocalDate,
    private val forrigeVirkedag: LocalDate,
    internal val maksdato: LocalDate,
    private val startdatoSykepengerettighet: LocalDate,
    private val startdatoTreårsvindu: LocalDate,
    private val betalteDager: Set<LocalDate>,
    internal val gjenståendeSykepengedagerUnder67: Int,
    internal val gjenståendeSykepengedagerOver67: Int,
    internal val gjenståendeDager: Int,
    internal val syttiårsdagen: LocalDate,
    private val bestemmelse: Maksdatobestemmelse
) {
    internal val forbrukteDager: Int = betalteDager.size

    internal fun vurderMaksdatobestemmelse(
        subsumsjonObserver: SubsumsjonObserver,
        periode: Periode,
        tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
        beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
        avvisteDager: Set<LocalDate>
    ) {
        bestemmelse.sporHjemmel(
            subsumsjonObserver,
            periode,
            tidslinjegrunnlagsubsumsjon,
            beregnetTidslinjesubsumsjon,
            avvisteDager,
            this
        )
    }

    private fun førFylte70(subsumsjonObserver: SubsumsjonObserver, periode: Periode, utfallTom: LocalDate = periode.endInclusive) {
        subsumsjonObserver.`§ 8-3 ledd 1 punktum 2`(
            oppfylt = true,
            syttiårsdagen = syttiårsdagen,
            utfallFom = periode.start,
            utfallTom = utfallTom,
            tidslinjeFom = periode.start,
            tidslinjeTom = periode.endInclusive,
            avvistePerioder = emptyList()
        )
    }

    fun harGjenståendeDager() = gjenståendeDager > 0
    fun bruktOppOrdinærKvote() = gjenståendeSykepengedagerUnder67 == 0
    fun bruktOppBegrensetKvote() = gjenståendeSykepengedagerOver67 == 0
    fun forGammel() = dato >= alder.syttiårsdagen
    fun forskyvMaksdato(dato: LocalDate): Rettighetsvurdering {
        return maksdatoFor(regler, alder, dato, betalteDager, startdatoTreårsvindu, startdatoSykepengerettighet)
    }
    fun medForbruktDag(dato: LocalDate): Rettighetsvurdering {
        return maksdatoFor(regler, alder, dato, betalteDager + setOf(dato), startdatoTreårsvindu, startdatoSykepengerettighet)
    }
    fun forskyvTreårsvindu(dato: LocalDate, nyStartdatoTreårsvindu: LocalDate): Rettighetsvurdering {
        val nyBetalteDager = betalteDager.filter { it >= nyStartdatoTreårsvindu }.toSet()
        //println("Tilgir ${betalteDager.size - nyBetalteDager.size} dager som følge av at treårsvindu flyttes fra $startdatoTreårsvindu til $nyStartdatoTreårsvindu")
        return maksdatoFor(regler, alder, dato, nyBetalteDager + setOf(dato), nyStartdatoTreårsvindu, startdatoSykepengerettighet)
    }
    fun resett(dato: LocalDate, startdatoTreårsvindu: LocalDate): Rettighetsvurdering {
        return maksdatoFor(regler, alder, dato, emptySet(), startdatoTreårsvindu, dato)
    }

    internal sealed interface Maksdatobestemmelse {
        fun sporHjemmel(
            subsumsjonObserver: SubsumsjonObserver,
            periode: Periode,
            tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
            beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
            avvisteDager: Set<LocalDate>,
            maksdatosituasjon: Rettighetsvurdering
        )

        data object OrdinærRett : Maksdatobestemmelse {
            override fun sporHjemmel(
                subsumsjonObserver: SubsumsjonObserver,
                periode: Periode,
                tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
                beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
                avvisteDager: Set<LocalDate>,
                maksdatosituasjon: Rettighetsvurdering
            ) {
                subsumsjonObserver.`§ 8-12 ledd 1 punktum 1`(periode, tidslinjegrunnlagsubsumsjon, beregnetTidslinjesubsumsjon, maksdatosituasjon.gjenståendeDager, maksdatosituasjon.forbrukteDager, maksdatosituasjon.maksdato, maksdatosituasjon.startdatoSykepengerettighet)
                maksdatosituasjon.førFylte70(subsumsjonObserver, periode)
            }
        }

        data object BegrensetRett : Maksdatobestemmelse {
            override fun sporHjemmel(
                subsumsjonObserver: SubsumsjonObserver,
                periode: Periode,
                tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
                beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
                avvisteDager: Set<LocalDate>,
                maksdatosituasjon: Rettighetsvurdering
            ) {
                subsumsjonObserver.`§ 8-51 ledd 3`(periode, tidslinjegrunnlagsubsumsjon, beregnetTidslinjesubsumsjon, maksdatosituasjon.gjenståendeDager, maksdatosituasjon.forbrukteDager, maksdatosituasjon.maksdato, maksdatosituasjon.startdatoSykepengerettighet)
                maksdatosituasjon.førFylte70(subsumsjonObserver, periode)
            }
        }

        data object Over70 : Maksdatobestemmelse {
            override fun sporHjemmel(
                subsumsjonObserver: SubsumsjonObserver,
                periode: Periode,
                tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
                beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
                avvisteDager: Set<LocalDate>,
                maksdatosituasjon: Rettighetsvurdering
            ) {
                if (periode.start < maksdatosituasjon.syttiårsdagen) {
                    maksdatosituasjon.førFylte70(subsumsjonObserver, periode, maksdatosituasjon.syttiårsdagen.forrigeDag)
                }
                val avvisteDagerFraOgMedSøtti = avvisteDager.filter { it >= maksdatosituasjon.syttiårsdagen }
                if (avvisteDagerFraOgMedSøtti.isEmpty()) return
                subsumsjonObserver.`§ 8-3 ledd 1 punktum 2`(
                    oppfylt = false,
                    syttiårsdagen = maksdatosituasjon.syttiårsdagen,
                    utfallFom = maxOf(maksdatosituasjon.syttiårsdagen, periode.start),
                    utfallTom = periode.endInclusive,
                    tidslinjeFom = periode.start,
                    tidslinjeTom = periode.endInclusive,
                    avvistePerioder = avvisteDagerFraOgMedSøtti.grupperSammenhengendePerioder()
                )
            }
        }
    }

    companion object {
        fun maksdatoFor(regler: ArbeidsgiverRegler, alder: Alder, dato: LocalDate, betalteDager: Set<LocalDate>, startdatoTreårsvindu: LocalDate, startdatoSykepengerettighet: LocalDate): Rettighetsvurdering {
            val syttiårsdagen = alder.syttiårsdagen
            val sisteVirkedagFørFylte70år = syttiårsdagen.forrigeVirkedagFør()
            val redusertYtelseAlder = alder.redusertYtelseAlder

            val uten = betalteDager.filter { it <= sisteVirkedagFørFylte70år }.toSet()
            val forbrukteDager = uten.size
            val forbrukteDagerOver67 = uten.count { it > redusertYtelseAlder }
            val gjenståendeSykepengedagerUnder67 = regler.maksSykepengedager() - forbrukteDager
            val gjenståendeSykepengedagerOver67 = regler.maksSykepengedagerOver67() - forbrukteDagerOver67

            val harNåddMaks = minOf(gjenståendeSykepengedagerOver67, gjenståendeSykepengedagerUnder67) == 0
            val forrigeMaksdato = if (harNåddMaks) uten.last() else null

            val forrigeVirkedag = dato.sisteVirkedagInklusiv()
            val utgangspunktForBeregning = forrigeMaksdato ?: forrigeVirkedag
            val maksdatoOrdinærRett = utgangspunktForBeregning + gjenståendeSykepengedagerUnder67.ukedager
            val maksdatoBegrensetRett = maxOf(utgangspunktForBeregning, redusertYtelseAlder) + gjenståendeSykepengedagerOver67.ukedager

            // maksdato er den dagen som først inntreffer blant ordinær kvote, 67-års-kvoten og 70-årsdagen,
            // med mindre man allerede har brukt opp alt tidligere
            val (maksdato, gjenstående, bestemmelse) = when {
                maksdatoOrdinærRett <= maksdatoBegrensetRett -> {
                    //check(startdatoSykepengerettighet != LocalDate.MIN)
                    //check(startdatoTreårsvindu != LocalDate.MIN)
                    Triple(maksdatoOrdinærRett, gjenståendeSykepengedagerUnder67, Maksdatobestemmelse.OrdinærRett)
                }
                maksdatoBegrensetRett <= sisteVirkedagFørFylte70år -> {
                    //check(startdatoSykepengerettighet != LocalDate.MIN)
                    //check(startdatoTreårsvindu != LocalDate.MIN)
                    Triple(maksdatoBegrensetRett, ukedager(utgangspunktForBeregning, maksdatoBegrensetRett), Maksdatobestemmelse.BegrensetRett)

                }
                else -> Triple(sisteVirkedagFørFylte70år, ukedager(utgangspunktForBeregning, sisteVirkedagFørFylte70år), Maksdatobestemmelse.Over70)
            }
            return Rettighetsvurdering(
                regler = regler,
                alder = alder,
                dato = dato,
                forrigeVirkedag = forrigeVirkedag,
                maksdato = maksdato,
                startdatoSykepengerettighet = startdatoSykepengerettighet,
                startdatoTreårsvindu = startdatoTreårsvindu,
                betalteDager = uten,
                gjenståendeDager = gjenstående,
                gjenståendeSykepengedagerUnder67 = gjenståendeSykepengedagerUnder67,
                gjenståendeSykepengedagerOver67 = gjenståendeSykepengedagerOver67,
                syttiårsdagen = syttiårsdagen,
                bestemmelse = bestemmelse
            )
        }

        private fun LocalDate.forrigeVirkedagFør() = minusDays(when (dayOfWeek) {
            DayOfWeek.SUNDAY -> 2
            DayOfWeek.MONDAY -> 3
            else -> 1
        })

        private fun LocalDate.sisteVirkedagInklusiv() = when (dayOfWeek) {
            DayOfWeek.SATURDAY -> minusDays(1)
            DayOfWeek.SUNDAY -> minusDays(2)
            else -> this
        }
    }
}