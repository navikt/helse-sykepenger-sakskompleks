package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.Tidslinjedag
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder

internal class Rettighetsvurdering(
    internal val maksdato: LocalDate,
    internal val forbrukteDager: Int,
    internal val gjenståendeDager: Int,
    private val syttiårsdagen: LocalDate,
    private val bestemmelse: Maksdatobestemmelse
) {
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

    internal sealed interface Maksdatobestemmelse {
        fun sporHjemmel(
            subsumsjonObserver: SubsumsjonObserver,
            periode: Periode,
            tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
            beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
            avvisteDager: Set<LocalDate>,
            maksdatosituasjon: Rettighetsvurdering
        )

        class OrdinærRett(private val startdatoSykepengerettighet: LocalDate) : Maksdatobestemmelse {
            override fun sporHjemmel(
                subsumsjonObserver: SubsumsjonObserver,
                periode: Periode,
                tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
                beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
                avvisteDager: Set<LocalDate>,
                maksdatosituasjon: Rettighetsvurdering
            ) {
                subsumsjonObserver.`§ 8-12 ledd 1 punktum 1`(periode, tidslinjegrunnlagsubsumsjon, beregnetTidslinjesubsumsjon, maksdatosituasjon.gjenståendeDager, maksdatosituasjon.forbrukteDager, maksdatosituasjon.maksdato, startdatoSykepengerettighet)
                maksdatosituasjon.førFylte70(subsumsjonObserver, periode)
            }
        }

        class BegrensetRett(private val startdatoSykepengerettighet: LocalDate) : Maksdatobestemmelse {
            override fun sporHjemmel(
                subsumsjonObserver: SubsumsjonObserver,
                periode: Periode,
                tidslinjegrunnlagsubsumsjon: List<List<Tidslinjedag>>,
                beregnetTidslinjesubsumsjon: List<Tidslinjedag>,
                avvisteDager: Set<LocalDate>,
                maksdatosituasjon: Rettighetsvurdering
            ) {
                subsumsjonObserver.`§ 8-51 ledd 3`(periode, tidslinjegrunnlagsubsumsjon, beregnetTidslinjesubsumsjon, maksdatosituasjon.gjenståendeDager, maksdatosituasjon.forbrukteDager, maksdatosituasjon.maksdato, startdatoSykepengerettighet)
                maksdatosituasjon.førFylte70(subsumsjonObserver, periode)
            }
        }

        class Over70() : Maksdatobestemmelse {
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
}