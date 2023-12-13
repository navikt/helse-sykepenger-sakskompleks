package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Alder

internal class Maksdatosituasjon private constructor(
    internal val rettighetsvurdering: Rettighetsvurdering,
    private val maksdatovurdering: Maksdatovurdering,
    private val tilstand: State,
    private val oppholdsdager: Int = 0
) {
    private constructor(rettighetsvurdering: Rettighetsvurdering, maksdatovurdering: Maksdatovurdering) : this(rettighetsvurdering, maksdatovurdering, State.Initiell)

    internal constructor(regler: ArbeidsgiverRegler, dato: LocalDate, alder: Alder, maksdatovurdering: Maksdatovurdering) :
            this(Rettighetsvurdering.maksdatoFor(regler, alder, dato, emptySet(), LocalDate.MIN, LocalDate.MIN), maksdatovurdering)

    private fun inkrementer(dato: LocalDate): Maksdatosituasjon {
        //check(rettighetsvurdering.harGjenståendeDager()) { "gjenstående dager må være større enn 0" }
        val nyVurdering = rettighetsvurdering.medForbruktDag(dato)
        return forbruktDag(nyVurdering, dato)
    }

    // tilgir forbrukte dager som følge av at treårsvinduet forskyves
    private fun forskyvOgForbruk(dato: LocalDate): Maksdatosituasjon {
        val nyVurdering = rettighetsvurdering.forskyvTreårsvindu(dato, dato.minusYears(HISTORISK_PERIODE_I_ÅR))
        return forbruktDag(nyVurdering, dato)
    }

    private fun forbruktDag(nyVurdering: Rettighetsvurdering, dato: LocalDate): Maksdatosituasjon {
        val nyTilstand = when {
            nyVurdering.forGammel() -> State.ForGammel
            nyVurdering.bruktOppOrdinærKvote() -> State.Karantene(Begrunnelse.SykepengedagerOppbrukt)
            nyVurdering.bruktOppBegrensetKvote() -> State.Karantene(Begrunnelse.SykepengedagerOppbruktOver67)
            else -> State.Syk
        }
        return Maksdatosituasjon(nyVurdering, maksdatovurdering, nyTilstand, 0).also {
            if (nyTilstand == State.ForGammel)
                it.maksdatovurdering.avvistDag(dato, Begrunnelse.Over70, it.rettighetsvurdering.maksdato)
            else
                it.maksdatovurdering.forbruktDag(dato, it.rettighetsvurdering, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, oppholdsdager)
        }
    }

    private fun avvistOver70(dato: LocalDate): Maksdatosituasjon {
        return medTilstand(dato, State.ForGammel).also {
            it.maksdatovurdering.avvistDag(dato, Begrunnelse.Over70, it.rettighetsvurdering.maksdato)
        }
    }

    private fun avvistDag(dato: LocalDate, begrunnelse: Begrunnelse): Maksdatosituasjon {
        maksdatovurdering.avvistDag(dato, begrunnelse, rettighetsvurdering.maksdato)
        return medTilstand(dato, tilstand)
    }

    private fun medTilstand(dato: LocalDate, nyTilstand: State, oppholdsdager: Int = this.oppholdsdager): Maksdatosituasjon {
        return Maksdatosituasjon(rettighetsvurdering.forskyvMaksdato(dato), maksdatovurdering, nyTilstand, oppholdsdager)
    }

    fun betalbarDag(dagen: LocalDate) = tilstand.betalbarDag(this, dagen)
    fun oppholdsdag(dagen: LocalDate) = tilstand.oppholdsdag(this, dagen)
    fun sykdomshelg(dagen: LocalDate) = tilstand.sykdomshelg(this, dagen)
    fun fridag(dagen: LocalDate) = tilstand.fridag(this, dagen)
    fun avvistDag(dagen: LocalDate) = tilstand.avvistDag(this, dagen)
    fun foreldetDag(dagen: LocalDate) = medTilstand(dagen, tilstand)

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
        fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon? // return null indikerer tilbakestilling/nok opphold nådd
        fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon?
        fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon?

        object Initiell : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return Maksdatosituasjon(avgrenser.rettighetsvurdering.resett(dagen, dagen.minusYears(HISTORISK_PERIODE_I_ÅR)), avgrenser.maksdatovurdering).inkrementer(dagen)
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
                if (dagen < avgrenser.rettighetsvurdering.syttiårsdagen) return avgrenser.medTilstand(dagen, this)
                return avgrenser.avvistOver70(dagen)
            }

            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, Opphold)

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.medTilstand(dagen, OppholdFri, 1)
            }
        }

        object Opphold : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.forskyvOgForbruk(dagen).inkrementer(dagen)
            }

            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = oppholdsdag(avgrenser, dagen)

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = oppholdsdag(avgrenser, dagen)
            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.håndterOpphold(dagen, Opphold)
        }

        object OppholdFri : State {
            override fun betalbarDag(avgrenser: Maksdatosituasjon, dagen: LocalDate): Maksdatosituasjon {
                return avgrenser.inkrementer(dagen)
            }

            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.medTilstand(dagen, this)

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
            override fun oppholdsdag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.medTilstand(dagen, this)
            override fun fridag(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.medTilstand(dagen, this)
            override fun sykdomshelg(avgrenser: Maksdatosituasjon, dagen: LocalDate) = avgrenser.avvistOver70(dagen)
        }
    }

    private companion object {
        const val TILSTREKKELIG_OPPHOLD_I_SYKEDAGER = 26 * 7
        private const val HISTORISK_PERIODE_I_ÅR: Long = 3
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