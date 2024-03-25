package no.nav.helse.etterlevelse

import java.io.Serializable
import java.time.LocalDate
import java.time.Year
import java.util.SortedSet

interface SubsumsjonObserver {

    /**
     * Vurdering av opptjeningstid
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-2)
     *
     * @param oppfylt hvorvidt sykmeldte har oppfylt krav om opptjeningstid
     * @param skjæringstidspunkt dato som antall opptjeningsdager regnes mot
     * @param tilstrekkeligAntallOpptjeningsdager antall opptjeningsdager som kreves for at vilkåret skal være [oppfylt]
     * @param arbeidsforhold hvilke arbeidsforhold det er tatt utgangspunkt i ved beregning av opptjeningstid
     * @param antallOpptjeningsdager antall opptjeningsdager sykmeldte faktisk har på [skjæringstidspunkt]
     */
    fun `§ 8-2 ledd 1`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        tilstrekkeligAntallOpptjeningsdager: Int,
        arbeidsforhold: List<Map<String, Any?>>,
        antallOpptjeningsdager: Int
    ) {}

    /**
     * Vurdering av rett til sykepenger ved fylte 70 år
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-3)
     *
     * @param oppfylt hvorvidt sykmeldte har fylt 70 år. Oppfylt så lenge sykmeldte ikke er 70 år eller eldre
     * @param syttiårsdagen dato sykmeldte fyller 70 år
     * @param utfallFom fra-og-med-dato [oppfylt]-vurderingen gjelder for
     * @param utfallTom til-og-med-dato [oppfylt]-vurderingen gjelder for
     * @param tidslinjeFom fra-og-med-dato vurderingen gjøres for
     * @param tidslinjeTom til-og-med-dato vurderingen gjøres for
     * @param avvistePerioder alle dager vurderingen ikke er [oppfylt] for. Tom dersom sykmeldte ikke fyller 70 år mellom [tidslinjeFom] og [tidslinjeTom]
     */
    fun `§ 8-3 ledd 1 punktum 2`(
        oppfylt: Boolean,
        syttiårsdagen: LocalDate,
        utfallFom: LocalDate,
        utfallTom: LocalDate,
        tidslinjeFom: LocalDate,
        tidslinjeTom: LocalDate,
        avvistePerioder: List<ClosedRange<LocalDate>>
    ) {}

    /**
     * Vurdering av krav til minimum inntekt
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-3)
     *
     * @param oppfylt hvorvidt sykmeldte har inntekt lik eller større enn minimum inntekt
     * @param skjæringstidspunkt dato det tas utgangspunkt i ved vurdering av minimum inntekt
     * @param beregningsgrunnlagÅrlig total inntekt på tvers av alle relevante arbeidsgivere
     * @param minimumInntektÅrlig minimum beløp [beregningsgrunnlagÅrlig] må være lik eller større enn for at vilkåret skal være [oppfylt]
     */
    fun `§ 8-3 ledd 2 punktum 1`(oppfylt: Boolean, skjæringstidspunkt: LocalDate, beregningsgrunnlagÅrlig: Double, minimumInntektÅrlig: Double) {}


    /**
     * Vilkår for rett til sykepenger at medlemmet oppholder seg i Norge.
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/§8-9)
     *
     * @param oppfylt hvorvidt sykmeldte har oppholdt seg i Norge i søknadsperioden
     * @param utlandsperiode perioden burker har oppgitt å ha vært i utlandet
     * @param søknadsperioder perioder i søknaden som ligger til grunn
     */
    fun `§ 8-9 ledd 1`(oppfylt: Boolean, utlandsperiode: ClosedRange<LocalDate>, søknadsperioder: List<Map<String, Serializable>>) {}

    /**
     * Vurdering av maksimalt sykepengegrunnlag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-10)
     *
     * @param erBegrenset dersom hjemlen slår inn ved at [beregningsgrunnlagÅrlig] blir begrenset til [maksimaltSykepengegrunnlagÅrlig]
     * @param maksimaltSykepengegrunnlagÅrlig maksimalt årlig beløp utbetaling skal beregnes ut fra
     * @param skjæringstidspunkt dato [maksimaltSykepengegrunnlagÅrlig] settes ut fra
     * @param beregningsgrunnlagÅrlig total inntekt på tvers av alle relevante arbeidsgivere
     */
    fun `§ 8-10 ledd 2 punktum 1`(
        erBegrenset: Boolean,
        maksimaltSykepengegrunnlagÅrlig: Double,
        skjæringstidspunkt: LocalDate,
        beregningsgrunnlagÅrlig: Double
    ) {}

    /**
     * Beregning av inntekt pr. dag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-10)
     *
     * @param årsinntekt inntekt oppgitt fra inntektsmelding omregnet til årlig
     * @param inntektOmregnetTilDaglig årsinntekt omregnet til daglig inntekt
     */
    fun `§ 8-10 ledd 3`(årsinntekt: Double, inntektOmregnetTilDaglig: Double) {}

    /**
     * Trygden yter ikke sykepenger i lørdag og søndag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-11)
     *
     * @param dato dagen vilkåret ikke er oppfylt for
     */
    fun `§ 8-11 ledd 1`(dato: LocalDate) {}

    /**
     * Vurdering av maksimalt antall sykepengedager
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-12)
     *
     * @param periode aktuell periode som vilkårsprøves
     * @param tidslinjegrunnlag alle tidslinjer det tas utgangspunkt i inklusiv potensielt utbetalte dager fra Infotrygd
     * @param beregnetTidslinje sammenslått tidslinje det tas utgangspunkt i når man beregner [gjenståendeSykedager], [forbrukteSykedager] og [maksdato]
     * @param gjenståendeSykedager antall gjenstående sykepengedager ved siste utbetalte dag i [periode].
     * @param forbrukteSykedager antall forbrukte sykepengedager ved siste utbetalte dag i [periode].
     * @param maksdato dato for opphør av rett til sykepenger
     * @param startdatoSykepengerettighet første NAV-dag i siste 248-dagers sykeforløp
     */
    fun `§ 8-12 ledd 1 punktum 1`(
        periode: ClosedRange<LocalDate>,
        tidslinjegrunnlag: List<List<Tidslinjedag.Tidslinjeperiode>>,
        beregnetTidslinje: List<Tidslinjedag.Tidslinjeperiode>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate?
    ) {}

    /**
     * Vurdering av ny rett til sykepenger
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-12)
     *
     * @param oppfylt **true** dersom det har vært tilstrekelig opphold
     * @param dato dato vurdering av hjemmel gjøres
     * @param tilstrekkeligOppholdISykedager antall dager med opphold i ytelsen som nødvendig for å oppnå ny rett til sykepenger
     * @param tidslinjegrunnlag alle tidslinjer det tas utgangspunkt i ved bygging av [beregnetTidslinje]
     * @param beregnetTidslinje tidslinje det tas utgangspunkt i ved utbetaling for aktuell vedtaksperiode
     */
    fun `§ 8-12 ledd 2`(
        oppfylt: Boolean,
        dato: LocalDate,
        gjenståendeSykepengedager: Int,
        beregnetAntallOppholdsdager: Int,
        tilstrekkeligOppholdISykedager: Int,
        tidslinjegrunnlag: List<List<Tidslinjedag.Tidslinjeperiode>>,
        beregnetTidslinje: List<Tidslinjedag.Tidslinjeperiode>
    ) {}

    /**
     * Vurdering av graderte sykepenger
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-13)
     *
     * @param periode perioden vilkåret vurderes for
     * @param avvisteDager dager som vilkåret ikke er oppfylt for, hvis noen
     * @param tidslinjer alle tidslinjer på tvers av arbeidsgivere
     */
    fun `§ 8-13 ledd 1`(periode: ClosedRange<LocalDate>, avvisteDager: SortedSet<LocalDate>, tidslinjer: List<List<Tidslinjedag.Tidslinjeperiode>>) {}

    /**
     * Vurdering av sykepengenes størrelse
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-13)
     *
     * @param periode perioden vilkåret vurderes for
     * @param tidslinjer alle tidslinjer på tvers av arbeidsgivere
     * @param grense grense brukt til å vurdere [dagerUnderGrensen]
     * @param dagerUnderGrensen dager som befinner seg under tilstrekkelig uføregrad, gitt av [grense]
     */
    fun `§ 8-13 ledd 2`(periode: ClosedRange<LocalDate>, tidslinjer: List<List<Tidslinjedag.Tidslinjeperiode>>, grense: Double, dagerUnderGrensen: List<ClosedRange<LocalDate>>) {}

    /**
     * Retten til sykepenger etter dette kapitlet faller bort når arbeidsforholdet midlertidig avbrytes i mer enn 14 dager
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/§8-15)
     *
     * @param skjæringstidspunkt dato som aktive arbeidsforhold beregnes for
     * @param organisasjonsnummer arbeidsgiveren som vurderes
     * @param inntekterSisteTreMåneder månedlig inntekt for de tre siste måneder før skjæringstidspunktet
     * @param forklaring saksbehandler sin forklaring for overstyring av arbeidsforhold
     * @param oppfylt **true** dersom [organisasjonsnummer] har avbrudd mer enn 14 dager
     */
    fun `§ 8-15`(skjæringstidspunkt: LocalDate, organisasjonsnummer: String, inntekterSisteTreMåneder: List<Inntektsubsumsjon>, forklaring: String, oppfylt: Boolean){}

    /**
     * Fastsettelse av dekningsgrunnlag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-16)
     *
     * @param dato dagen [dekningsgrunnlag] beregnes for
     * @param dekningsgrad hvor stor andel av inntekten det ytes sykepenger av
     * @param inntekt inntekt for aktuell arbeidsgiver
     * @param dekningsgrunnlag maks dagsats før reduksjon til 6G og reduksjon for sykmeldingsgrad
     */
    fun `§ 8-16 ledd 1`(dato: LocalDate, dekningsgrad: Double, inntekt: Double, dekningsgrunnlag: Double) {}

    /**
     * Vurdering av når utbetaling av sykepenger tidligst skal starte
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-17)
     *
     * @param oppfylt **true** dersom [dagen] er etter arbeidsgiverperioden
     * @param dagen aktuelle dagen for vurdering
     */
    fun `§ 8-17 ledd 1 bokstav a`(oppfylt: Boolean, dagen: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {}

    /**
     * Vurdering av når utbetaling av sykepenger tidligst skal starte
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-17)
     *
     * @param periode arbeidsgiversøknad-perioden
     */
    fun `§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`(
        periode: Iterable<LocalDate>,
        sykdomstidslinje: List<Tidslinjedag>
    ) {}

    /**
     * Vurdering av når utbetaling av sykepenger tidligst skal starte
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-17)
     *
     * @param dato Nav utbetaler første 16 dager
     */
    fun `§ 8-17 ledd 1`(
        dato: LocalDate
    ) {}

    /**
     * Trygden yter ikke sykepenger for lovpålagt ferie og permisjon
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-17)
     *
     * @param dato dagen vilkåret blir vurdert for
     */
    fun `§ 8-17 ledd 2`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {}

    /**
     * Arbeidsgiverperioden teller 16 sykedager
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-19)
     *
     * @param dato dagen vilkåret blir vurdert for
     * @param beregnetTidslinje tidslinje som ligger til grunn for beregning av agp
     */
    fun `§ 8-19 første ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {}

    /**
     * Arbeidsgiverperioden regnes fra og med første hele fraværsdag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-19)
     *
     * @param dato for en dag som anses som en agp-dag
     * @param beregnetTidslinje tidslinje som ligger til grunn for beregning av agp
     */
    fun `§ 8-19 andre ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {}

    /**
     * Når det er gått mindre enn 16 kalenderdager siden forrige sykefravær,
     * skal et nytt sykefravær regnes med i samme arbeidsgiverperiode.
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-19)
     *
     * @param dato for en dag som anses som en agp-dag
     * @param beregnetTidslinje tidslinje som ligger til grunn for beregning av agp
     */
    fun `§ 8-19 tredje ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {}

    /**
     * Når arbeidsgiveren har utbetalt sykepenger i en full arbeidsgiverperiode,
     * skal det inntre ny arbeidsgiverperiode ved sykdom som inntreffer 16 dager
     * etter at vedkommende arbeidstaker har gjenopptatt arbeidet.
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-19)
     *
     * @param dato for den 16. oppholdsdag
     * @param beregnetTidslinje tidslinje som ligger til grunn for beregning av agp
     */
    fun `§ 8-19 fjerde ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {}

    /**
     * Inntekt som legges til grunn dersom sykdom ved en arbeidsgiver starter senere enn skjæringstidspunktet tilsvarer
     * innrapportert inntekt til a-ordningen for de tre siste månedene før skjæringstidspunktet
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-28)
     *
     * @param organisasjonsnummer arbeidsgiveren [grunnlagForSykepengegrunnlagÅrlig] er beregnet for
     * @param inntekterSisteTreMåneder månedlig inntekt for de tre siste måneder før skjæringstidspunktet
     * @param skjæringstidspunkt dato som [grunnlagForSykepengegrunnlagÅrlig] beregnes relativt til
     * @param grunnlagForSykepengegrunnlagÅrlig beregnet grunnlag basert på [inntekterSisteTreMåneder]
     * @param grunnlagForSykepengegrunnlagMånedlig beregnet grunnlag basert på [inntekterSisteTreMåneder]
     */
    fun `§ 8-28 ledd 3 bokstav a`(
        organisasjonsnummer: String,
        inntekterSisteTreMåneder: List<Inntektsubsumsjon>,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double,
        skjæringstidspunkt: LocalDate
    ) {}

    /**
     * I arbeidsforhold som har vart så kort tid at det ikke er rapportert inntekt til a-ordningen
     * for tre hele kalendermåneder, skal dette kortere tidsrommet legges til grunn.
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-28)
     *
     * @param organisasjonsnummer arbeidsgiveren [grunnlagForSykepengegrunnlagÅrlig] er beregnet for
     * @param startdatoArbeidsforhold startdato hos arbeidsgiver [organisasjonsnummer]
     * @param overstyrtInntektFraSaksbehandler inntekt saksbehandler har vurdert korrekt iht. § 8-28 (3) b
     * @param skjæringstidspunkt dato som [grunnlagForSykepengegrunnlagÅrlig] beregnes relativt til
     * @param forklaring saksbehandler sin forklaring for overstyring av inntekt
     * @param grunnlagForSykepengegrunnlagÅrlig beregnet grunnlag basert på [overstyrtInntektFraSaksbehandler]
     * @param grunnlagForSykepengegrunnlagMånedlig beregnet grunnlag basert på [overstyrtInntektFraSaksbehandler]
     */
    fun `§ 8-28 ledd 3 bokstav b`(
        organisasjonsnummer: String,
        startdatoArbeidsforhold: LocalDate,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double
    ) {}

    /**
     * I et arbeidsforhold der arbeidstakeren har fått varig lønnsendring i løpet av eller etter beregningsperioden,
     * men før arbeidsuførhetstidspunktet, skal tidsrommet etter lønnsendringen legges til grunn.
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-28)
     *
     * @param organisasjonsnummer arbeidsgiveren [grunnlagForSykepengegrunnlagÅrlig] er beregnet for
     * @param overstyrtInntektFraSaksbehandler inntekt saksbehandler har vurdert korrekt iht. § 8-28 (3) c
     * @param skjæringstidspunkt dato som [grunnlagForSykepengegrunnlagÅrlig] beregnes relativt til
     * @param forklaring saksbehandler sin forklaring for overstyring av inntekt
     * @param grunnlagForSykepengegrunnlagÅrlig beregnet grunnlag basert på [overstyrtInntektFraSaksbehandler]
     * @param grunnlagForSykepengegrunnlagMånedlig beregnet grunnlag basert på [overstyrtInntektFraSaksbehandler]
     */
    fun `§ 8-28 ledd 3 bokstav c`(
        organisasjonsnummer: String,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double
    ) {}

    /**
     * Dersom rapporteringen til a-ordningen er mangelfull eller uriktig,
     * fastsettes sykepengegrunnlaget ut fra den inntekten arbeidsgiver skulle ha rapportert til a-ordningen i beregningsperioden.
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-28)
     *
     * @param organisasjonsnummer arbeidsgiveren [grunnlagForSykepengegrunnlagÅrlig] er beregnet for
     * @param overstyrtInntektFraSaksbehandler inntekt saksbehandler har vurdert korrekt iht. § 8-28 (3) c
     * @param skjæringstidspunkt dato som [grunnlagForSykepengegrunnlagÅrlig] beregnes relativt til
     * @param forklaring saksbehandler sin forklaring for overstyring av inntekt
     * @param grunnlagForSykepengegrunnlagÅrlig beregnet grunnlag basert på [overstyrtInntektFraSaksbehandler]
     * @param grunnlagForSykepengegrunnlagMånedlig beregnet grunnlag basert på [overstyrtInntektFraSaksbehandler]
     */
    fun `§ 8-28 ledd 5`(
        organisasjonsnummer: String,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double
    ) {}

    /**
     * Inntekter som legges til grunn for beregning av sykepengegrunnlag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-29)
     *
     * @param organisasjonsnummer arbeidsgiveren [grunnlagForSykepengegrunnlagÅrlig] er beregnet for
     * @param inntektsopplysninger inntekter som ligger til grunn for beregning av [grunnlagForSykepengegrunnlagÅrlig]
     * @param skjæringstidspunkt dato som [grunnlagForSykepengegrunnlagÅrlig] beregnes relativt til
     * @param grunnlagForSykepengegrunnlagÅrlig beregnet grunnlag basert på [inntektsopplysninger]
     */
    fun `§ 8-29`(
        skjæringstidspunkt: LocalDate,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        inntektsopplysninger: List<Inntektsubsumsjon>,
        organisasjonsnummer: String
    ) {}

    fun `§ 8-33 ledd 1`() {}

    @Suppress("UNUSED_PARAMETER")
    fun `§ 8-33 ledd 3`(grunnlagForFeriepenger: Int, opptjeningsår: Year, prosentsats: Double, alder: Int, feriepenger: Double) {}

    /**
     * Vurdering av krav til minimum inntekt ved alder mellom 67 og 70 år
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-51)
     *
     * @param oppfylt dersom vedkommende har inntekt større enn eller lik to ganger grunnbeløpet. Det er en forutsetning at vedkommende er mellom 67 og 70 år
     * @param skjæringstidspunkt dato det tas utgangspunkt i ved vurdering av minimum inntekt
     * @param alderPåSkjæringstidspunkt alder på skjæringstidspunktet
     * @param beregningsgrunnlagÅrlig total inntekt på tvers av alle relevante arbeidsgivere
     * @param minimumInntektÅrlig minimum beløp [beregningsgrunnlagÅrlig] må være lik eller større enn for at vilkåret skal være [oppfylt]
     */
    fun `§ 8-51 ledd 2`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        alderPåSkjæringstidspunkt: Int,
        beregningsgrunnlagÅrlig: Double,
        minimumInntektÅrlig: Double
    ) {}

    /**
     * Løpende vurdering av krav til minimum inntekt ved alder mellom 67 og 70 år, trer i kraft når vedkommende fyller 67 år i løpet av sykefraværstilfellet0
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-51)
     *
     * @param oppfylt dersom vedkommende fyller 67 år i løpet av sykefraværstilfellet og har inntekt større enn eller lik to ganger grunnbeløpet.
     * @param utfallFom fra-og-med-dato [oppfylt]-vurderingen gjelder for
     * @param utfallTom til-og-med-dato [oppfylt]-vurderingen gjelder for
     * @param sekstisyvårsdag dato vedkommene fyller 67 år
     * @param periodeFom fra-og-med-dato for perioden til behandling som vurderer kravet om minimum inntekt
     * @param periodeTom til-og-med-dato for perioden til behandling som vurderer kravet om minimum inntekt
     * @param beregningsgrunnlagÅrlig total inntekt på tvers av alle relevante arbeidsgivere
     * @param minimumInntektÅrlig minimum beløp [beregningsgrunnlagÅrlig] må være lik eller større enn for at vilkåret skal være [oppfylt]
     */
    fun `§ 8-51 ledd 2`(
        oppfylt: Boolean,
        utfallFom: LocalDate,
        utfallTom: LocalDate,
        periodeFom: LocalDate,
        periodeTom: LocalDate,
        sekstisyvårsdag: LocalDate,
        beregningsgrunnlagÅrlig: Double,
        minimumInntektÅrlig: Double
    ) {}

    /**
     * Vurdering av maksimalt antall sykepengedager ved fytle 67 år
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-51)
     *
     * @param periode aktuell periode som vilkårsprøves
     * @param tidslinjegrunnlag alle tidslinjer det tas utgangspunkt i inklusiv potensielt utbetalte dager fra Infotrygd
     * @param beregnetTidslinje sammenslått tidslinje det tas utgangspunkt i når man beregner [gjenståendeSykedager], [forbrukteSykedager] og [maksdato]
     * @param gjenståendeSykedager antall gjenstående sykepengedager ved siste utbetalte dag i [periode].
     * @param forbrukteSykedager antall forbrukte sykepengedager ved siste utbetalte dag i [periode].
     * @param maksdato dato for opphør av rett til sykepenger
     * @param startdatoSykepengerettighet første NAV-dag i siste 248-dagers sykeforløp
     */
    fun `§ 8-51 ledd 3`(
        periode: ClosedRange<LocalDate>,
        tidslinjegrunnlag: List<List<Tidslinjedag.Tidslinjeperiode>>,
        beregnetTidslinje: List<Tidslinjedag.Tidslinjeperiode>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate?
    ) {}

    /**
     * Vurdering av foreldelse
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/§22-13)
     */
    fun `§ 22-13 ledd 3`(avskjæringsdato: LocalDate, perioder: List<ClosedRange<LocalDate>>) {}

    /**
     * Omgjøring av vedtak uten klage
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/§22-13)
     */
    fun `fvl § 35 ledd 1`() {}

    /**
     * Arbeidsavklaringspenger istedenfor sykepenger
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-48)
     */
    fun `§ 8-48 ledd 2 punktum 2`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {}

    /**
     * Annen livsoppsoppholdsytelse istedenfor sykepenger
     *
     *  Lovdata: [lenke](https://lovdata.no/dokument/TRR/avgjorelse/trr-2006-4023)
     */
    fun `Trygderettens kjennelse 2006-4023`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {}

    class SammenligningsgrunnlagDTO(
        val sammenligningsgrunnlag: Double,
        val inntekterFraAOrdningen: Map<String, List<Map<String, Any>>>
    )

    companion object {
        val NullObserver = object : SubsumsjonObserver {}
    }
}
