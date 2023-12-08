package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.etterlevelse.UtbetalingstidslinjeBuilder.Companion.subsumsjonsformat
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_9
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.økonomi.Økonomi

internal class MaksimumSykepengedagerfilter(
    private val alder: Alder,
    private val arbeidsgiverRegler: ArbeidsgiverRegler,
    private val infotrygdtidslinje: Utbetalingstidslinje
): UtbetalingstidslinjerFilter, UtbetalingstidslinjeVisitor {

    private companion object {
        const val TILSTREKKELIG_OPPHOLD_I_SYKEDAGER = 26 * 7
    }

    private lateinit var state: State
    private lateinit var teller: UtbetalingTeller
    private var opphold = 0
    private val begrunnelserForAvvisteDager = mutableMapOf<Begrunnelse, MutableSet<LocalDate>>()
    private val avvisteDager get() = begrunnelserForAvvisteDager.values.flatten().toSet()
    private lateinit var beregnetTidslinje: Utbetalingstidslinje
    private lateinit var tidslinjegrunnlag: List<Utbetalingstidslinje>
    private var subsumsjonObserver: SubsumsjonObserver = NullObserver

    private val tidslinjegrunnlagsubsumsjon by lazy { tidslinjegrunnlag.subsumsjonsformat() }
    private val beregnetTidslinjesubsumsjon by lazy { beregnetTidslinje.subsumsjonsformat() }

    private val maksdatoer = mutableMapOf<LocalDate, UtbetalingTeller>()

    internal fun maksimumSykepenger(periode: Periode, subsumsjonObserver: SubsumsjonObserver): Alder.MaksimumSykepenger {
        val telleren = maksdatoer.getValue(periode.endInclusive)
        val maksimumSykepenger = telleren.maksimumSykepenger(periode.endInclusive)
        maksimumSykepenger.sisteDag(karantenesporing(telleren, periode, subsumsjonObserver))
        return maksimumSykepenger
    }

    private fun avvisDag(dag: LocalDate, begrunnelse: Begrunnelse) {
        begrunnelserForAvvisteDager.getOrPut(begrunnelse) {
            mutableSetOf()
        }.add(dag)
    }

    private fun karantenesporing(teller: UtbetalingTeller, periode: Periode, subsumsjonObserver: SubsumsjonObserver) = object : Alder.MaksimumSykepenger.Begrunnelse {
        override fun `§ 8-12 ledd 1 punktum 1`(sisteDag: LocalDate, forbrukteDager: Int, gjenståendeDager: Int) {
            val sakensStartdato = teller.startdatoSykepengerettighet() ?: return
            subsumsjonObserver.`§ 8-12 ledd 1 punktum 1`(
                periode,
                tidslinjegrunnlagsubsumsjon,
                beregnetTidslinjesubsumsjon,
                gjenståendeDager,
                forbrukteDager,
                sisteDag,
                sakensStartdato
            )
        }

        override fun `§ 8-51 ledd 3`(sisteDag: LocalDate, forbrukteDager: Int, gjenståendeDager: Int) {
            val sakensStartdato = teller.startdatoSykepengerettighet() ?: return
            subsumsjonObserver.`§ 8-51 ledd 3`(
                periode,
                tidslinjegrunnlagsubsumsjon,
                beregnetTidslinjesubsumsjon,
                gjenståendeDager,
                forbrukteDager,
                sisteDag,
                sakensStartdato
            )
        }

        override fun `§ 8-3 ledd 1 punktum 2`(sisteDag: LocalDate, forbrukteDager: Int, gjenståendeDager: Int) {}
    }

    override fun filter(
        tidslinjer: List<Utbetalingstidslinje>,
        periode: Periode,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonObserver: SubsumsjonObserver
    ): List<Utbetalingstidslinje> {
        this.subsumsjonObserver = subsumsjonObserver
        tidslinjegrunnlag = tidslinjer + listOf(infotrygdtidslinje)
        beregnetTidslinje = tidslinjegrunnlag.reduce(Utbetalingstidslinje::plus)
        teller = UtbetalingTeller(alder, arbeidsgiverRegler)
        state = State.Initiell
        beregnetTidslinje.accept(this)

        val avvisteTidslinjer = begrunnelserForAvvisteDager.entries.fold(tidslinjer) { result, (begrunnelse, dager) ->
            Utbetalingstidslinje.avvis(result, dager.grupperSammenhengendePerioder(), listOf(begrunnelse))
        }

        // TODO: logge juridisk vurdering for 70 år i "karantenesporing"
        alder.etterlevelse70år(aktivitetslogg, beregnetTidslinje.periode(), avvisteDager, subsumsjonObserver)

        if (begrunnelserForAvvisteDager[Begrunnelse.NyVilkårsprøvingNødvendig]?.any { it in periode } == true) {
            aktivitetslogg.funksjonellFeil(RV_VV_9)
        }
        if (avvisteDager in periode)
            aktivitetslogg.info("Maks antall sykepengedager er nådd i perioden")
        else
            aktivitetslogg.info("Maksimalt antall sykedager overskrides ikke i perioden")

        return avvisteTidslinjer
    }

    private fun state(nyState: State) {
        if (this.state == nyState) return
        state.leaving(this)
        state = nyState
        state.entering(this)
    }

    override fun visit(dag: Utbetalingsdag.ForeldetDag, dato: LocalDate, økonomi: Økonomi) {
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: NavDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        if (alder.mistetSykepengerett(dato)) state(State.ForGammel)
        state.betalbarDag(this, dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: Utbetalingsdag.NavHelgDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        if (alder.mistetSykepengerett(dato)) state(State.ForGammel)
        state.sykdomshelg(this, dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: Utbetalingsdag.Arbeidsdag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: Utbetalingsdag.ArbeidsgiverperiodeDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        oppholdsdag(dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: Utbetalingsdag.Fridag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        fridag(dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: Utbetalingsdag.AvvistDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.avvistDag(this, dato)
        oppholdsdag(dato)
        maksdatoer[dato] = teller.clone()
    }

    override fun visit(
        dag: UkjentDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
        maksdatoer[dato] = teller.clone()
    }

    private fun oppholdsdag(dagen: LocalDate) {
        opphold += 1
        state.oppholdsdag(this, dagen)
    }

    private fun fridag(dagen: LocalDate) {
        opphold += 1
        state.fridag(this, dagen)
    }

    private fun håndterBetalbarDag(dagen: LocalDate, justerTreårsvindu: Boolean = false) {
        teller.inkrementer(dagen)
        if (justerTreårsvindu) teller.dekrementer(dagen)
        subsumsjonObserver.`§ 8-12 ledd 2`(
            oppfylt = false,
            dato = dagen,
            gjenståendeSykepengedager = teller.gjenståendeDager(dagen),
            beregnetAntallOppholdsdager = opphold,
            tilstrekkeligOppholdISykedager = TILSTREKKELIG_OPPHOLD_I_SYKEDAGER,
            tidslinjegrunnlag = tidslinjegrunnlagsubsumsjon,
            beregnetTidslinje = beregnetTidslinjesubsumsjon,
        )
        if (!teller.erAlleDagerForbrukte()) return state(State.Syk)
        state(if (teller.erDagerOver67ÅrForbrukte()) State.Karantene(Begrunnelse.SykepengedagerOppbruktOver67) else State.Karantene(Begrunnelse.SykepengedagerOppbrukt))
    }
    private fun tilstrekkeligOppholdNådd(dagen: LocalDate): Boolean {
        val harTilstrekkeligOpphold = opphold >= TILSTREKKELIG_OPPHOLD_I_SYKEDAGER
        subsumsjonObserver.`§ 8-12 ledd 2`(
            oppfylt = harTilstrekkeligOpphold,
            dato = dagen,
            gjenståendeSykepengedager = teller.gjenståendeDager(dagen),
            beregnetAntallOppholdsdager = opphold,
            tilstrekkeligOppholdISykedager = TILSTREKKELIG_OPPHOLD_I_SYKEDAGER,
            tidslinjegrunnlag = tidslinjegrunnlagsubsumsjon,
            beregnetTidslinje = beregnetTidslinjesubsumsjon,
        )
        return harTilstrekkeligOpphold
    }

    private interface State {
        fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun avvistDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) = oppholdsdag(avgrenser, dagen)
        fun entering(avgrenser: MaksimumSykepengedagerfilter) {}
        fun leaving(avgrenser: MaksimumSykepengedagerfilter) {}

        object Initiell : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                avgrenser.teller.resett()
                avgrenser.opphold = 0
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.teller.inkrementer(dagen)
                avgrenser.teller.dekrementer(dagen)
                avgrenser.state(Syk)
            }
        }

        object Syk : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                avgrenser.opphold = 0
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.håndterBetalbarDag(dagen)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.state(Opphold)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.state(OppholdFri)
            }
        }

        object Opphold : State {

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.håndterBetalbarDag(dagen, true)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
                oppholdsdag(avgrenser, dagen)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                if (!avgrenser.tilstrekkeligOppholdNådd(dagen)) return
                avgrenser.state(Initiell)
            }
        }

        object OppholdFri : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.håndterBetalbarDag(dagen)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                if (!avgrenser.tilstrekkeligOppholdNådd(dagen)) return
                avgrenser.state(Initiell)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                if (!avgrenser.tilstrekkeligOppholdNådd(dagen)) return avgrenser.state(Opphold)
                avgrenser.state(Initiell)
            }
        }

        class Karantene(private val begrunnelse: Begrunnelse) : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
                vurderTilstrekkeligOppholdNådd(avgrenser)
                avgrenser.state.avvistDag(avgrenser, dagen)
            }

            override fun avvistDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, begrunnelse)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
                /* helg skal ikke medføre ny rettighet */
                vurderTilstrekkeligOppholdNådd(avgrenser)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                if (!avgrenser.tilstrekkeligOppholdNådd(dagen)) return
                avgrenser.state(Initiell)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                vurderTilstrekkeligOppholdNådd(avgrenser)
            }

            private fun vurderTilstrekkeligOppholdNådd(avgrenser: MaksimumSykepengedagerfilter) {
                if (avgrenser.opphold <= TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) return
                avgrenser.state(KaranteneTilstrekkeligOppholdNådd)
            }
        }

        object KaranteneTilstrekkeligOppholdNådd : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avvistDag(avgrenser, dagen)
            }

            override fun avvistDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, Begrunnelse.NyVilkårsprøvingNødvendig)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.state(Initiell)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        }

        object ForGammel : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                over70(avgrenser, dagen)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                over70(avgrenser, dagen)
            }

            override fun leaving(avgrenser: MaksimumSykepengedagerfilter) = throw IllegalStateException("Kan ikke gå ut fra state ForGammel")

            private fun over70(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, Begrunnelse.Over70)
            }
        }
    }
}
