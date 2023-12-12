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
): UtbetalingstidslinjerFilter, UtbetalingstidslinjeVisitor, Maksdatovurdering {
    private val begrunnelserForAvvisteDager = mutableMapOf<Begrunnelse, MutableSet<LocalDate>>()
    private val avvisteDager get() = begrunnelserForAvvisteDager.values.flatten().toSet()
    private lateinit var beregnetTidslinje: Utbetalingstidslinje
    private lateinit var tidslinjegrunnlag: List<Utbetalingstidslinje>
    private var subsumsjonObserver: SubsumsjonObserver = NullObserver

    private val tidslinjegrunnlagsubsumsjon by lazy { tidslinjegrunnlag.subsumsjonsformat() }
    private val beregnetTidslinjesubsumsjon by lazy { beregnetTidslinje.subsumsjonsformat() }

    private val maksdatoer = mutableMapOf<LocalDate, Rettighetsvurdering>()

    private var gjeldendeVurdering: Maksdatosituasjon? = null

    internal fun maksimumSykepenger(periode: Periode, subsumsjonObserver: SubsumsjonObserver): Rettighetsvurdering {
        val maksimumSykepenger = maksdatoer.getValue(periode.endInclusive)
        maksimumSykepenger.vurderMaksdatobestemmelse(subsumsjonObserver, periode, tidslinjegrunnlagsubsumsjon, beregnetTidslinjesubsumsjon, avvisteDager)
        return maksimumSykepenger
    }

    private fun avvisDag(dag: LocalDate, begrunnelse: Begrunnelse) {
        begrunnelserForAvvisteDager.getOrPut(begrunnelse) {
            mutableSetOf()
        }.add(dag)
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
        beregnetTidslinje.accept(this)

        val avvisteTidslinjer = begrunnelserForAvvisteDager.entries.fold(tidslinjer) { result, (begrunnelse, dager) ->
            Utbetalingstidslinje.avvis(result, dager.grupperSammenhengendePerioder(), listOf(begrunnelse))
        }

        if (begrunnelserForAvvisteDager[Begrunnelse.NyVilkårsprøvingNødvendig]?.any { it in periode } == true) {
            aktivitetslogg.funksjonellFeil(RV_VV_9)
        }
        if (avvisteDager in periode)
            aktivitetslogg.info("Maks antall sykepengedager er nådd i perioden")
        else
            aktivitetslogg.info("Maksimalt antall sykedager overskrides ikke i perioden")

        return avvisteTidslinjer
    }

    override fun forbruktDag(dato: LocalDate, rettighetsvurdering: Rettighetsvurdering, tilstrekkeligOpphold: Int, oppholdsdager: Int) {
        subsumsjonObserver.`§ 8-12 ledd 2`(
            oppfylt = false,
            dato = dato,
            gjenståendeSykepengedager = rettighetsvurdering.gjenståendeDager,
            beregnetAntallOppholdsdager = oppholdsdager,
            tilstrekkeligOppholdISykedager = tilstrekkeligOpphold,
            tidslinjegrunnlag = tidslinjegrunnlagsubsumsjon,
            beregnetTidslinje = beregnetTidslinjesubsumsjon,
        )
        // println("$dato telles som forbrukt. Status per $dato: $forbrukteDager / $gjenståendeDager – $maksdato")
    }

    override fun oppholdsdag(dato: LocalDate, rettighetsvurdering: Rettighetsvurdering, tilstrekkeligOpphold: Int, oppholdsdager: Int) {
        subsumsjonObserver.`§ 8-12 ledd 2`(
            oppfylt = oppholdsdager >= tilstrekkeligOpphold,
            dato = dato,
            gjenståendeSykepengedager = rettighetsvurdering.gjenståendeDager,
            beregnetAntallOppholdsdager = oppholdsdager,
            tilstrekkeligOppholdISykedager = tilstrekkeligOpphold,
            tidslinjegrunnlag = tidslinjegrunnlagsubsumsjon,
            beregnetTidslinje = beregnetTidslinjesubsumsjon,
        )

        // println("$dato telles som opphold ($oppholdsdager / $tilstrekkeligOpphold). Status per $dato: $forbrukteDager / $gjenståendeDager – $maksdato")
    }

    override fun avvistDag(dato: LocalDate, begrunnelse: Begrunnelse, maksdato: LocalDate) {
        //println("$dato avvises med $begrunnelse. Maksdato $maksdato")
        avvisDag(dato, begrunnelse)
    }

    private fun Maksdatosituasjon?.maksdatoFor(dato: LocalDate): Rettighetsvurdering {
        return this?.rettighetsvurdering ?: Maksdatosituasjon(arbeidsgiverRegler, dato, alder, this@MaksimumSykepengedagerfilter).rettighetsvurdering
    }

    override fun visit(dag: Utbetalingsdag.ForeldetDag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.foreldetDag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: NavDag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = (gjeldendeVurdering ?: Maksdatosituasjon(arbeidsgiverRegler, dato, alder, this)).betalbarDag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: Utbetalingsdag.NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.sykdomshelg(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: Utbetalingsdag.Arbeidsdag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.oppholdsdag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.oppholdsdag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.oppholdsdag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: Utbetalingsdag.Fridag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.fridag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: Utbetalingsdag.AvvistDag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.avvistDag(dato)
        gjeldendeVurdering = gjeldendeVurdering?.oppholdsdag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }

    override fun visit(dag: UkjentDag, dato: LocalDate, økonomi: Økonomi) {
        gjeldendeVurdering = gjeldendeVurdering?.oppholdsdag(dato)
        maksdatoer[dato] = gjeldendeVurdering.maksdatoFor(dato)
    }
}
