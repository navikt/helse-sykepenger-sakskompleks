package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.person.BeregnetArbeidsgiverperiode
import no.nav.helse.person.beløp.Beløpsdag
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Dag as Beløpstidslinjedag
import no.nav.helse.person.beløp.UkjentDag
import no.nav.helse.person.inntekt.Inntektstidslinje
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Økonomi

internal class VilkårsprøvdSkjæringstidspunkt(
    private val skjæringstidspunkt: LocalDate,
    private val `6G`: Inntekt,
    inntekter: List<FaktaavklartInntekt>,
    val tilkommendeInntekter: List<NyInntektUnderveis>,
    val deaktiverteArbeidsforhold: List<String>
) {
    private val inntekter = inntekter.associate { inntekt ->
        inntekt.organisasjonsnummer to ArbeidsgiverFaktaavklartInntekt(
            skjæringstidspunkt = skjæringstidspunkt,
            `6G` = `6G`,
            inntektstidslinje = inntekt.inntektstidslinje
        )
    }

    internal fun forArbeidsgiver(organisasjonsnummer: String): ArbeidsgiverFaktaavklartInntekt? {
        return inntekter[organisasjonsnummer]
    }

    internal fun medGhostOgNyeInntekterUnderveis(utbetalingstidslinjer: Map<String, List<Utbetalingstidslinje>>): Map<String, Utbetalingstidslinje> {
        return nyeInntekterUnderveis(ghosttidslinjer(utbetalingstidslinjer))
    }

    private fun ghosttidslinjer(utbetalingstidslinjer: Map<String, List<Utbetalingstidslinje>>): Map<String, Utbetalingstidslinje> {
        val beregningsperiode = utbetalingstidslinjer.values.flatten().map { it.periode() }.periode()!!
        return inntekter
            .mapValues { (orgnr, v) -> v.ghosttidslinje(beregningsperiode, skjæringstidspunkt, `6G`, utbetalingstidslinjer[orgnr] ?: emptyList()) }
            .filterValues { it.isNotEmpty() }
    }

    private fun nyeInntekterUnderveis(utbetalingstidslinjer: Map<String, Utbetalingstidslinje>): Map<String, Utbetalingstidslinje> {
        val beregningsperiode = utbetalingstidslinjer.values.map { it.periode() }.periode()!!
        val tilkommendeInntekterTidslinje = tilkommendeInntekter.associate { nyInntekt ->
            val tilkommenInntektTidslinje = Utbetalingstidslinje.Builder().apply {
                beregningsperiode.forEach { dato ->
                    when (val beløpsdag = nyInntekt.beløpstidslinje[dato]) {
                        is Beløpsdag -> {
                            addArbeidsdag(
                                dato, Økonomi.ikkeBetalt().inntekt(
                                aktuellDagsinntekt = beløpsdag.beløp,
                                beregningsgrunnlag = INGEN,
                                `6G` = `6G`,
                                refusjonsbeløp = INGEN
                            )
                            )
                        }

                        is UkjentDag -> {
                            addArbeidsdag(dato, Økonomi.ikkeBetalt())
                        }
                    }
                }
            }.build()
            nyInntekt.orgnummer to tilkommenInntektTidslinje
        }
        // hvis vi skal kunne ha søknad og tilkommen inntekt for en og samme arbeidsgiver så må vi
        // gjøre en litt bedre merging enn Map.plus() her :)
        return utbetalingstidslinjer + tilkommendeInntekterTidslinje
    }

    data class FaktaavklartInntekt(
        val organisasjonsnummer: String,
        val inntektstidslinje: Inntektstidslinje
    )

    data class NyInntektUnderveis(
        val orgnummer: String,
        val beløpstidslinje: Beløpstidslinje
    )
}

internal class ArbeidsgiverFaktaavklartInntekt(
    private val skjæringstidspunkt: LocalDate,
    private val `6G`: Inntekt,
    private val inntektstidslinje: Inntektstidslinje
) {
    private val lagDefaultRefusjonsbeløpHvisMangler = { _: LocalDate, aktuellDagsinntekt: Inntekt -> aktuellDagsinntekt }
    private val krevRefusjonsbeløpHvisMangler = { dato: LocalDate, _: Inntekt ->
        error("Har ingen refusjonsopplysninger på vilkårsgrunnlag for utbetalingsdag $dato")
    }

    private fun fastsattÅrsinntekt(dagen: LocalDate) = inntektstidslinje[dagen] ?: INGEN
    private fun beregningsgrunnlag(skjæringstidspunkt: LocalDate) = inntektstidslinje[skjæringstidspunkt] ?: INGEN

    internal fun medInntektHvisFinnes(
        dato: LocalDate,
        økonomi: Økonomi,
        regler: ArbeidsgiverRegler,
        refusjon: Beløpstidslinjedag
    ): Økonomi {
        return medInntekt(dato, økonomi, regler, refusjon, lagDefaultRefusjonsbeløpHvisMangler)
    }

    internal fun medInntektOrThrow(
        dato: LocalDate,
        økonomi: Økonomi,
        regler: ArbeidsgiverRegler,
        refusjon: Beløpstidslinjedag,
    ): Økonomi {
        return medInntekt(dato, økonomi, regler, refusjon, krevRefusjonsbeløpHvisMangler)
    }

    private fun medInntekt(
        dato: LocalDate,
        økonomi: Økonomi,
        regler: ArbeidsgiverRegler,
        refusjon: Beløpstidslinjedag,
        refusjonsopplysningFinnesIkkeStrategi: (LocalDate, Inntekt) -> Inntekt
    ): Økonomi {
        val aktuellDagsinntekt = fastsattÅrsinntekt(dato)
        return økonomi.inntekt(
            aktuellDagsinntekt = aktuellDagsinntekt,
            beregningsgrunnlag = beregningsgrunnlag(skjæringstidspunkt),
            dekningsgrunnlag = aktuellDagsinntekt * regler.dekningsgrad(),
            `6G` = if (dato < skjæringstidspunkt) INGEN else `6G`,
            refusjonsbeløp = refusjonsbeløp(dato, refusjon, aktuellDagsinntekt, refusjonsopplysningFinnesIkkeStrategi)
        )
    }

    private fun refusjonsbeløp(dato: LocalDate, refusjon: Beløpstidslinjedag, aktuellDagsinntekt: Inntekt, refusjonsopplysningFinnesIkkeStrategi: (LocalDate, Inntekt) -> Inntekt): Inntekt {
        val refusjonFraBehandling = refusjon.takeIf { it is Beløpsdag }?.beløp
        return refusjonFraBehandling ?: refusjonsopplysningFinnesIkkeStrategi(dato, aktuellDagsinntekt)
    }

    internal fun ghosttidslinje(beregningsperiode: Periode, skjæringstidspunkt: LocalDate, `6G`: Inntekt, arbeidsgiverlinjer: List<Utbetalingstidslinje>): Utbetalingstidslinje {
        val vedtaksperioder = arbeidsgiverlinjer.map { it.periode() }
        // Dette er dager man er ghost/ tilkommen
        val beløpsdager = beregningsperiode.associateWith { dato ->
            if (vedtaksperioder.any { vedtaksperiode -> dato in vedtaksperiode }) null
            else inntektstidslinje[dato]
        }.filterValues { it != null }.mapValues { (_, inntekt) -> inntekt!! }

        // lager faktiske ghost/tilkommen-tidslinjer fra brudd-periodene
        val utbetalingstidslinje = with(Utbetalingstidslinje.Builder()) {
            val beregningsgrunnlag = beregningsgrunnlag(skjæringstidspunkt)
            beløpsdager.forEach { (dato, inntekt) ->
                if (dato.erHelg()) addFridag(dato, Økonomi.ikkeBetalt())
                else addArbeidsdag(
                    dato = dato,
                    økonomi = Økonomi.ikkeBetalt().inntekt(
                        aktuellDagsinntekt = inntekt,
                        beregningsgrunnlag = beregningsgrunnlag,
                        dekningsgrunnlag = INGEN,
                        `6G` = `6G`,
                        refusjonsbeløp = INGEN
                    )
                )
            }
            build()
        }
        return arbeidsgiverlinjer.fold(utbetalingstidslinje, Utbetalingstidslinje::plus)
    }
}

internal data class ArbeidsgiverperiodeForVedtaksperiode(
    val vedtaksperiode: Periode,
    val arbeidsgiverperiode: BeregnetArbeidsgiverperiode
)

internal class UtbetalingstidslinjeBuilderVedtaksperiode(
    private val faktaavklarteInntekter: ArbeidsgiverFaktaavklartInntekt,
    private val regler: ArbeidsgiverRegler,
    private val arbeidsgiverperiode: BeregnetArbeidsgiverperiode,
    private val refusjonstidslinje: Beløpstidslinje
) {
    internal fun result(sykdomstidslinje: Sykdomstidslinje): Utbetalingstidslinje {
        val builder = Utbetalingstidslinje.Builder()
        sykdomstidslinje.forEach { dag ->
            when (dag) {
                /** <potensielt arbeidsgiverperiode-dager> **/
                is Dag.ArbeidsgiverHelgedag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, dag.økonomi)
                    else helg(builder, dag.dato, dag.økonomi)
                }

                is Dag.Arbeidsgiverdag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, dag.økonomi)
                    else avvistDag(builder, dag.dato, dag.økonomi.ikkeBetalt(), Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode)
                }

                is Dag.Sykedag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, dag.økonomi)
                    else navDag(builder, dag.dato, dag.økonomi)
                }

                is Dag.SykHelgedag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, dag.økonomi)
                    else helg(builder, dag.dato, dag.økonomi)
                }

                is Dag.SykedagNav -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, dag.økonomi)
                    else when (dag.dato.erHelg()) {
                        true -> helg(builder, dag.dato, dag.økonomi)
                        false -> navDag(builder, dag.dato, dag.økonomi)
                    }
                }

                is Dag.AndreYtelser -> {
                    // andreytelse-dagen er fridag hvis den overlapper med en agp-dag, eller om vedtaksperioden ikke har noen agp -- fordi andre ytelsen spiser opp alt
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, Økonomi.ikkeBetalt())
                    else if (arbeidsgiverperiode.omsluttendePeriode == null || dag.dato < arbeidsgiverperiode.omsluttendePeriode.start) fridag(builder, dag.dato)
                    else {
                        val begrunnelse = when (dag.ytelse) {
                            Dag.AndreYtelser.AnnenYtelse.AAP -> Begrunnelse.AndreYtelserAap
                            Dag.AndreYtelser.AnnenYtelse.Dagpenger -> Begrunnelse.AndreYtelserDagpenger
                            Dag.AndreYtelser.AnnenYtelse.Foreldrepenger -> Begrunnelse.AndreYtelserForeldrepenger
                            Dag.AndreYtelser.AnnenYtelse.Omsorgspenger -> Begrunnelse.AndreYtelserOmsorgspenger
                            Dag.AndreYtelser.AnnenYtelse.Opplæringspenger -> Begrunnelse.AndreYtelserOpplaringspenger
                            Dag.AndreYtelser.AnnenYtelse.Pleiepenger -> Begrunnelse.AndreYtelserPleiepenger
                            Dag.AndreYtelser.AnnenYtelse.Svangerskapspenger -> Begrunnelse.AndreYtelserSvangerskapspenger
                        }
                        avvistDag(builder, dag.dato, Økonomi.ikkeBetalt(), begrunnelse)
                    }
                }

                is Dag.Feriedag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, Økonomi.ikkeBetalt())
                    else fridag(builder, dag.dato)
                }

                is Dag.ForeldetSykedag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, dag.økonomi)
                    else builder.addForeldetDag(dag.dato, faktaavklarteInntekter.medInntektHvisFinnes(dag.dato, dag.økonomi, regler, refusjonstidslinje[dag.dato]))
                }

                is Dag.ArbeidIkkeGjenopptattDag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, Økonomi.ikkeBetalt())
                    else fridag(builder, dag.dato)
                }

                is Dag.Permisjonsdag -> {
                    if (erAGP(dag.dato)) arbeidsgiverperiodedag(builder, dag.dato, Økonomi.ikkeBetalt())
                    else fridag(builder, dag.dato)
                }
                /** </potensielt arbeidsgiverperiode-dager> **/

                is Dag.Arbeidsdag -> arbeidsdag(builder, dag.dato)
                is Dag.FriskHelgedag -> arbeidsdag(builder, dag.dato)
                is Dag.ProblemDag -> {
                    // den andre builderen kaster egentlig exception her, men trenger vi det –– sånn egentlig?
                    fridag(builder, dag.dato)
                }

                is Dag.UkjentDag -> {
                    // todo: pga strekking av egenmeldingsdager fra søknad så har vi vedtaksperioder med ukjentdager
                    // error("Forventer ikke å finne en ukjentdag i en vedtaksperiode")
                    when (dag.dato.erHelg()) {
                        true -> fridag(builder, dag.dato)
                        false -> arbeidsdag(builder, dag.dato)
                    }
                }
            }
        }
        return builder.build()
    }

    private fun erAGP(dato: LocalDate) = arbeidsgiverperiode.arbeidsgiverperioder.any { dato in it.periode }
    private fun erSykNav(dato: LocalDate) = arbeidsgiverperiode.arbeidsgiverperioder.any { it.navOvertarAnsvar && dato in it.periode }

    private fun arbeidsgiverperiodedag(builder: Utbetalingstidslinje.Builder, dato: LocalDate, økonomi: Økonomi) {
        if (erSykNav(dato)) {
            builder.addArbeidsgiverperiodedagNav(dato, faktaavklarteInntekter.medInntektOrThrow(dato, økonomi, regler, refusjonstidslinje[dato]))
        } else {
            builder.addArbeidsgiverperiodedag(dato, faktaavklarteInntekter.medInntektHvisFinnes(dato, økonomi.ikkeBetalt(), regler, refusjonstidslinje[dato]))
        }
    }

    private fun avvistDag(builder: Utbetalingstidslinje.Builder, dato: LocalDate, økonomi: Økonomi, begrunnelse: Begrunnelse) {
        builder.addAvvistDag(dato, faktaavklarteInntekter.medInntektHvisFinnes(dato, økonomi, regler, refusjonstidslinje[dato]), listOf(begrunnelse))
    }

    private fun helg(builder: Utbetalingstidslinje.Builder, dato: LocalDate, økonomi: Økonomi) {
        builder.addHelg(dato, faktaavklarteInntekter.medInntektHvisFinnes(dato, økonomi.ikkeBetalt(), regler, refusjonstidslinje[dato]))
    }

    private fun navDag(builder: Utbetalingstidslinje.Builder, dato: LocalDate, økonomi: Økonomi) {
        builder.addNAVdag(dato, faktaavklarteInntekter.medInntektOrThrow(dato, økonomi, regler, refusjonstidslinje[dato]))
    }

    private fun fridag(builder: Utbetalingstidslinje.Builder, dato: LocalDate) {
        builder.addFridag(dato, faktaavklarteInntekter.medInntektHvisFinnes(dato, Økonomi.ikkeBetalt(), regler, refusjonstidslinje[dato]))
    }

    private fun arbeidsdag(builder: Utbetalingstidslinje.Builder, dato: LocalDate) {
        builder.addArbeidsdag(dato, faktaavklarteInntekter.medInntektHvisFinnes(dato, Økonomi.ikkeBetalt(), regler, refusjonstidslinje[dato]))
    }
}
