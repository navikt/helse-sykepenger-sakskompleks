package no.nav.helse.etterlevelse

import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.SortedSet
import java.util.UUID
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_A
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_B
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_C
import no.nav.helse.etterlevelse.Inntektsubsumsjon.Companion.subsumsjonsformat
import no.nav.helse.etterlevelse.Ledd.Companion.ledd
import no.nav.helse.etterlevelse.Ledd.LEDD_1
import no.nav.helse.etterlevelse.Ledd.LEDD_2
import no.nav.helse.etterlevelse.Ledd.LEDD_3
import no.nav.helse.etterlevelse.Ledd.LEDD_5
import no.nav.helse.etterlevelse.Paragraf.KJENNELSE_2006_4023
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_10
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_11
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_12
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_13
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_15
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_16
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_17
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_19
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_2
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_28
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_29
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_3
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_30
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_48
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_51
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_9
import no.nav.helse.etterlevelse.Punktum.Companion.punktum
import no.nav.helse.etterlevelse.RangeIterator.Companion.forEach
import no.nav.helse.etterlevelse.RangeIterator.Companion.iterator
import no.nav.helse.etterlevelse.RangeIterator.Companion.trim
import no.nav.helse.etterlevelse.Subsumsjon.Utfall
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_BEREGNET
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_IKKE_OPPFYLT
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_OPPFYLT
import no.nav.helse.etterlevelse.Tidslinjedag.Companion.dager

class MaskinellJurist private constructor(
    private val parent: MaskinellJurist?,
    private val kontekster: Map<String, KontekstType>,
    vedtaksperiode: ClosedRange<LocalDate>? = null
) : SubsumsjonObserver {

    private val periode: () -> ClosedRange<LocalDate>

    init {
        // Når periode blir kalt av en subsumsjon skal vi være i kontekst av en vedtaksperiode.
        periode =  { checkNotNull(vedtaksperiode){"MaksinellJurist må være i kontekst av en vedtaksperiode for å registrere subsumsjonen"} }
    }

    private var subsumsjoner = listOf<Subsumsjon>()

    constructor() : this(null, emptyMap())

    private fun leggTil(subsumsjon: Subsumsjon) {
        subsumsjoner = subsumsjon.sammenstill(subsumsjoner)
        parent?.leggTil(subsumsjon)
    }

    private fun kontekster(): Map<String, KontekstType> = this.kontekster.toMap()

    fun medFødselsnummer(personidentifikator: String) =
        kopierMedKontekst(mapOf(personidentifikator to KontekstType.Fødselsnummer) + kontekster.filterNot { it.value == KontekstType.Fødselsnummer })

    fun medOrganisasjonsnummer(organisasjonsnummer: String) =
        kopierMedKontekst(mapOf(organisasjonsnummer to KontekstType.Organisasjonsnummer) + kontekster.filterNot { it.value == KontekstType.Organisasjonsnummer })

    fun medVedtaksperiode(vedtaksperiodeId: UUID, hendelseIder: Map<UUID, KontekstType>, periode: ClosedRange<LocalDate>) =
        kopierMedKontekst(
            mapOf(vedtaksperiodeId.toString() to KontekstType.Vedtaksperiode) + hendelseIder
                .map { it.key.toString() to it.value } + kontekster,
            periode
        )

    fun medInntektsmelding(inntektsmeldingId: UUID) = kopierMedKontekst(mapOf(
        inntektsmeldingId.toString() to KontekstType.Inntektsmelding
    ) + kontekster)

    private fun kopierMedKontekst(kontekster: Map<String, KontekstType>, periode: ClosedRange<LocalDate>? = null) = MaskinellJurist(this, kontekster, periode)

    override fun `§ 8-2 ledd 1`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        tilstrekkeligAntallOpptjeningsdager: Int,
        arbeidsforhold: List<Map<String, Any?>>,
        antallOpptjeningsdager: Int
    ) {
        leggTil(
            EnkelSubsumsjon(
                lovverk = "folketrygdloven",
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2020, 6, 12),
                paragraf = PARAGRAF_8_2,
                ledd = 1.ledd,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "tilstrekkeligAntallOpptjeningsdager" to tilstrekkeligAntallOpptjeningsdager,
                    "arbeidsforhold" to arbeidsforhold
                ),
                output = mapOf("antallOpptjeningsdager" to antallOpptjeningsdager),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-3 ledd 1 punktum 2`(
        oppfylt: Boolean,
        syttiårsdagen: LocalDate,
        utfallFom: LocalDate,
        utfallTom: LocalDate,
        tidslinjeFom: LocalDate,
        tidslinjeTom: LocalDate,
        avvistePerioder: List<ClosedRange<LocalDate>>
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_3,
                ledd = 1.ledd,
                punktum = 2.punktum,
                input = mapOf(
                    "syttiårsdagen" to syttiårsdagen,
                    "utfallFom" to utfallFom,
                    "utfallTom" to utfallTom,
                    "tidslinjeFom" to tidslinjeFom,
                    "tidslinjeTom" to tidslinjeTom
                ),
                output = mapOf("avvisteDager" to avvistePerioder),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-3 ledd 2 punktum 1`(oppfylt: Boolean, skjæringstidspunkt: LocalDate, beregningsgrunnlagÅrlig: Double, minimumInntektÅrlig: Double) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_3,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlagÅrlig,
                    "minimumInntekt" to minimumInntektÅrlig
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-9 ledd 1`(oppfylt: Boolean, utlandsperiode: ClosedRange<LocalDate>, søknadsperioder: List<Map<String, Serializable>>) {
        utlandsperiode.forEach {
            leggTil(
                GrupperbarSubsumsjon(
                    dato = it,
                    lovverk = "folketrygdloven",
                    utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                    versjon = LocalDate.of(2021, 6, 1),
                    paragraf = PARAGRAF_8_9,
                    ledd = LEDD_1,
                    input = mapOf( "soknadsPerioder" to søknadsperioder),
                    output = emptyMap(),
                    kontekster = kontekster()
                )
            )
        }
    }

    override fun `§ 8-10 ledd 2 punktum 1`(
        erBegrenset: Boolean,
        maksimaltSykepengegrunnlagÅrlig: Double,
        skjæringstidspunkt: LocalDate,
        beregningsgrunnlagÅrlig: Double
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2020, 1, 1),
                paragraf = PARAGRAF_8_10,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "maksimaltSykepengegrunnlag" to maksimaltSykepengegrunnlagÅrlig,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlagÅrlig
                ),
                output = mapOf(
                    "erBegrenset" to erBegrenset
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-10 ledd 3`(årsinntekt: Double, inntektOmregnetTilDaglig: Double) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2020, 1, 1),
                paragraf = PARAGRAF_8_10,
                ledd = 3.ledd,
                input = mapOf("årligInntekt" to årsinntekt),
                output = mapOf("dagligInntekt" to inntektOmregnetTilDaglig),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-11 ledd 1`(dato: LocalDate) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                paragraf = PARAGRAF_8_11,
                ledd = 1.ledd,
                utfall = VILKAR_IKKE_OPPFYLT,
                versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                input = mapOf("periode" to mapOf( "fom" to periode().start, "tom" to periode().endInclusive)),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-12 ledd 1 punktum 1`(
        periode: ClosedRange<LocalDate>,
        tidslinjegrunnlag: List<List<Tidslinjedag>>,
        beregnetTidslinje: List<Tidslinjedag>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate
    ) {
        val iterator = RangeIterator(periode).subsetFom(startdatoSykepengerettighet)
        val (dagerOppfylt, dagerIkkeOppfylt) = iterator
            .asSequence()
            .partition { it <= maksdato }

        fun logg(utfall: Utfall, utfallFom: LocalDate, utfallTom: LocalDate) {
            leggTil(
                EnkelSubsumsjon(
                    utfall = utfall,
                    lovverk = "folketrygdloven",
                    versjon = LocalDate.of(2021, 5, 21),
                    paragraf = PARAGRAF_8_12,
                    ledd = 1.ledd,
                    punktum = 1.punktum,
                    input = mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive,
                        "utfallFom" to utfallFom,
                        "utfallTom" to utfallTom,
                        "tidslinjegrunnlag" to tidslinjegrunnlag.map { it.dager(periode) },
                        "beregnetTidslinje" to beregnetTidslinje.dager(periode)
                    ),
                    output = mapOf(
                        "gjenståendeSykedager" to gjenståendeSykedager,
                        "forbrukteSykedager" to forbrukteSykedager,
                        "maksdato" to maksdato,
                    ),
                    kontekster = kontekster()
                )
            )
        }
        if (dagerOppfylt.isNotEmpty()) logg(VILKAR_OPPFYLT, dagerOppfylt.first(), dagerOppfylt.last())
        if (dagerIkkeOppfylt.isNotEmpty()) logg(VILKAR_IKKE_OPPFYLT, dagerIkkeOppfylt.first(), dagerIkkeOppfylt.last())
    }

    override fun `§ 8-12 ledd 2`(
        oppfylt: Boolean,
        dato: LocalDate,
        gjenståendeSykepengedager: Int,
        beregnetAntallOppholdsdager: Int,
        tilstrekkeligOppholdISykedager: Int,
        tidslinjegrunnlag: List<List<Tidslinjedag>>,
        beregnetTidslinje: List<Tidslinjedag>
    ) {
        leggTil(
            BetingetSubsumsjon(
                funnetRelevant = oppfylt || gjenståendeSykepengedager == 0, // Bare relevant om det er ny rett på sykepenger eller om vilkåret ikke er oppfylt
                lovverk = "folketrygdloven",
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2021, 5, 21),
                paragraf = PARAGRAF_8_12,
                ledd = 2.ledd,
                punktum = null,
                bokstav = null,
                input = mapOf(
                    "dato" to dato,
                    "tilstrekkeligOppholdISykedager" to tilstrekkeligOppholdISykedager,
                    "tidslinjegrunnlag" to tidslinjegrunnlag.map { it.dager() },
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-13 ledd 1`(periode: ClosedRange<LocalDate>, avvisteDager: SortedSet<LocalDate>, tidslinjer: List<List<Tidslinjedag>>) {
        fun logg(utfall: Utfall, dager: Iterable<LocalDate>) {
            dager.forEach { dagen ->
                leggTil(
                    GrupperbarSubsumsjon(
                        dato = dagen,
                        lovverk = "folketrygdloven",
                        utfall = utfall,
                        paragraf = PARAGRAF_8_13,
                        ledd = LEDD_1,
                        versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                        input = mapOf(
                            "tidslinjegrunnlag" to tidslinjer.map { it.dager(periode) }
                        ),
                        output = emptyMap(),
                        kontekster = kontekster()
                    )
                )
            }
        }

        val oppfylteDager = periode.trim(avvisteDager).flatMap { it.iterator().asSequence().toList() }
        if (oppfylteDager.isNotEmpty()) logg(VILKAR_OPPFYLT, oppfylteDager)
        if (avvisteDager.isNotEmpty()) logg(VILKAR_IKKE_OPPFYLT, avvisteDager)
    }

    override fun `§ 8-13 ledd 2`(
        periode: ClosedRange<LocalDate>,
        tidslinjer: List<List<Tidslinjedag>>,
        grense: Double,
        dagerUnderGrensen: List<ClosedRange<LocalDate>>
    ) {
        val tidslinjegrunnlag = tidslinjer.map { it.dager(periode) }
        val dagerUnderGrensenMap = dagerUnderGrensen.map {
            mapOf(
                "fom" to it.start,
                "tom" to it.endInclusive
            )
        }
        periode.forEach { dagen ->
            leggTil(
                GrupperbarSubsumsjon(
                    dato = dagen,
                    lovverk = "folketrygdloven",
                    utfall = VILKAR_BEREGNET,
                    paragraf = PARAGRAF_8_13,
                    ledd = LEDD_2,
                    versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                    input = mapOf(
                        "tidslinjegrunnlag" to tidslinjegrunnlag,
                        "grense" to grense
                    ),
                    output = mapOf(
                        "dagerUnderGrensen" to dagerUnderGrensenMap
                    ),
                    kontekster = kontekster()
                )
            )
        }
    }

    override fun `§ 8-15`(
        skjæringstidspunkt: LocalDate,
        organisasjonsnummer: String,
        inntekterSisteTreMåneder: List<Inntektsubsumsjon>,
        forklaring: String,
        oppfylt: Boolean
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(1998, 12, 18),
                paragraf = PARAGRAF_8_15,
                ledd = null,
                punktum = null,
                bokstav = null,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "inntekterSisteTreMåneder" to inntekterSisteTreMåneder.subsumsjonsformat(),
                    "forklaring" to forklaring
                ),
                kontekster = kontekster(),
                output = if (oppfylt) {
                    mapOf("arbeidsforholdAvbrutt" to organisasjonsnummer)
                } else {
                    mapOf("aktivtArbeidsforhold" to organisasjonsnummer)
                }
            )
        )
    }

    override fun `§ 8-16 ledd 1`(dato: LocalDate, dekningsgrad: Double, inntekt: Double, dekningsgrunnlag: Double) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                input = mapOf("dekningsgrad" to dekningsgrad, "inntekt" to inntekt),
                output = mapOf("dekningsgrunnlag" to dekningsgrunnlag),
                utfall = VILKAR_BEREGNET,
                paragraf = PARAGRAF_8_16,
                ledd = 1.ledd,
                versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1 bokstav a`(
        oppfylt: Boolean,
        dagen: LocalDate,
        sykdomstidslinje: List<Tidslinjedag>
    ) {
        leggTil(
            GrupperbarSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2018, 1, 1),
                dato = dagen,
                paragraf = PARAGRAF_8_17,
                ledd = 1.ledd,
                bokstav = BOKSTAV_A,
                input = mapOf("sykdomstidslinje" to sykdomstidslinje.dager(periode())),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1`(dato: LocalDate) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2018, 1, 1),
                utfall = VILKAR_OPPFYLT,
                paragraf = PARAGRAF_8_17,
                ledd = LEDD_1,
                input = emptyMap(),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`(
        periode: Iterable<LocalDate>,
        sykdomstidslinje: List<Tidslinjedag>
    ) {
        periode.forEach {
            `§ 8-17 ledd 1 bokstav a`(false, it, sykdomstidslinje)
        }
    }

    override fun `§ 8-17 ledd 2`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2018, 1, 1),
                utfall = VILKAR_IKKE_OPPFYLT,
                paragraf = PARAGRAF_8_17,
                ledd = LEDD_2,
                input = mapOf(
                    "beregnetTidslinje" to sykdomstidslinje.dager(periode())
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 første ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2001, 1, 1),
                paragraf = PARAGRAF_8_19,
                ledd = 1.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = mapOf(
                    "sisteDagIArbeidsgiverperioden" to dato
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 andre ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                utfall = VILKAR_BEREGNET,
                versjon = LocalDate.of(2001, 1, 1),
                paragraf = PARAGRAF_8_19,
                ledd = 2.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 tredje ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                utfall = VILKAR_BEREGNET,
                versjon = LocalDate.of(2001, 1, 1),
                paragraf = PARAGRAF_8_19,
                ledd = 3.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 fjerde ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                utfall = VILKAR_BEREGNET,
                versjon = LocalDate.of(2001, 1, 1),
                paragraf = PARAGRAF_8_19,
                ledd = 4.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 3 bokstav a`(
        organisasjonsnummer: String,
        inntekterSisteTreMåneder: List<Inntektsubsumsjon>,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double,
        skjæringstidspunkt: LocalDate
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_3,
                bokstav = BOKSTAV_A,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "inntekterSisteTreMåneder" to inntekterSisteTreMåneder.subsumsjonsformat(),
                    "skjæringstidspunkt" to skjæringstidspunkt
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlagÅrlig,
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlagMånedlig
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 3 bokstav b`(
        organisasjonsnummer: String,
        startdatoArbeidsforhold: LocalDate,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_3,
                bokstav = BOKSTAV_B,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "startdatoArbeidsforhold" to startdatoArbeidsforhold,
                    "overstyrtInntektFraSaksbehandler" to overstyrtInntektFraSaksbehandler,
                    "forklaring" to forklaring
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlagÅrlig,
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlagMånedlig
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 3 bokstav c`(
        organisasjonsnummer: String,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_3,
                bokstav = BOKSTAV_C,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "overstyrtInntektFraSaksbehandler" to overstyrtInntektFraSaksbehandler,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "forklaring" to forklaring
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlagÅrlig,
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlagMånedlig
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 5`(
        organisasjonsnummer: String,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        grunnlagForSykepengegrunnlagMånedlig: Double,
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_5,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "overstyrtInntektFraSaksbehandler" to overstyrtInntektFraSaksbehandler,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "forklaring" to forklaring
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlagÅrlig,
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlagMånedlig
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-29`(
        skjæringstidspunkt: LocalDate,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        inntektsopplysninger: List<Inntektsubsumsjon>,
        organisasjonsnummer: String
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_29,
                ledd = null,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "organisasjonsnummer" to organisasjonsnummer,
                    "inntektsopplysninger" to inntektsopplysninger.subsumsjonsformat()
                ),
                output = mapOf(
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlagÅrlig
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-30 ledd 1`(grunnlagForSykepengegrunnlagPerArbeidsgiverMånedlig: Map<String, Double>, grunnlagForSykepengegrunnlagÅrlig: Double) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_30,
                ledd = LEDD_1,
                input = mapOf(
                    "beregnetMånedsinntektPerArbeidsgiver" to grunnlagForSykepengegrunnlagPerArbeidsgiverMånedlig
                ),
                output = mapOf(
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlagÅrlig
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-30 ledd 2 punktum 1`(
        maksimaltTillattAvvikPåÅrsinntekt: Int,
        grunnlagForSykepengegrunnlagÅrlig: Double,
        sammenligningsgrunnlag: Double,
        avvik: Double
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2017, 4, 5),
                paragraf = PARAGRAF_8_30,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "maksimaltTillattAvvikPåÅrsinntekt" to maksimaltTillattAvvikPåÅrsinntekt.toDouble(),
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlagÅrlig,
                    "sammenligningsgrunnlag" to sammenligningsgrunnlag
                ),
                output = mapOf("avviksprosent" to avvik),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-30 ledd 2`(skjæringstidspunkt: LocalDate, sammenligningsgrunnlag: SubsumsjonObserver.SammenligningsgrunnlagDTO) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_30,
                ledd = LEDD_2,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "inntekterFraAOrdningen" to sammenligningsgrunnlag.inntekterFraAOrdningen
                ),
                output = mapOf(
                    "sammenligningsgrunnlag" to sammenligningsgrunnlag.sammenligningsgrunnlag
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-33 ledd 1`() {
        super.`§ 8-33 ledd 1`()
    }

    override fun `§ 8-33 ledd 3`(grunnlagForFeriepenger: Int, opptjeningsår: Year, prosentsats: Double, alder: Int, feriepenger: Double) {
        super.`§ 8-33 ledd 3`(grunnlagForFeriepenger, opptjeningsår, prosentsats, alder, feriepenger)
    }

    override fun `§ 8-51 ledd 2`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        alderPåSkjæringstidspunkt: Int,
        beregningsgrunnlagÅrlig: Double,
        minimumInntektÅrlig: Double
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_51,
                ledd = LEDD_2,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "alderPåSkjæringstidspunkt" to alderPåSkjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlagÅrlig,
                    "minimumInntekt" to minimumInntektÅrlig
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-51 ledd 2`(
        oppfylt: Boolean,
        periode: ClosedRange<LocalDate>,
        sekstisyvårsdagen: LocalDate,
        beregningsgrunnlagÅrlig: Double,
        minimumInntektÅrlig: Double
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_51,
                ledd = 2.ledd,
                input = mapOf(
                    "periode" to periode,
                    "sekstisyvårsdagen" to sekstisyvårsdagen,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlagÅrlig,
                    "minimumInntekt" to minimumInntektÅrlig
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-51 ledd 3`(
        periode: ClosedRange<LocalDate>,
        tidslinjegrunnlag: List<List<Tidslinjedag>>,
        beregnetTidslinje: List<Tidslinjedag>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate
    ) {
        val iterator = RangeIterator(periode).subsetFom(startdatoSykepengerettighet)
        val (dagerOppfylt, dagerIkkeOppfylt) = iterator.asSequence().partition { it <= maksdato }

        fun logg(utfall: Utfall, utfallFom: LocalDate, utfallTom: LocalDate) {
            leggTil(
                EnkelSubsumsjon(
                    utfall = utfall,
                    versjon = LocalDate.of(2011, 12, 16),
                    lovverk = "folketrygdloven",
                    paragraf = PARAGRAF_8_51,
                    ledd = LEDD_3,
                    input = mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive,
                        "utfallFom" to utfallFom,
                        "utfallTom" to utfallTom,
                        "tidslinjegrunnlag" to tidslinjegrunnlag.map { it.dager() },
                        "beregnetTidslinje" to beregnetTidslinje.dager()
                    ),
                    output = mapOf(
                        "gjenståendeSykedager" to gjenståendeSykedager,
                        "forbrukteSykedager" to forbrukteSykedager,
                        "maksdato" to maksdato,
                    ),
                    kontekster = kontekster()
                )
            )
        }
        if (dagerOppfylt.isNotEmpty()) logg(VILKAR_OPPFYLT, dagerOppfylt.first(), dagerOppfylt.last())
        if (dagerIkkeOppfylt.isNotEmpty()) logg(VILKAR_IKKE_OPPFYLT, dagerIkkeOppfylt.first(), dagerIkkeOppfylt.last())
    }

    override fun `§ 22-13 ledd 3`(avskjæringsdato: LocalDate, perioder: List<ClosedRange<LocalDate>>) {
        leggTil(EnkelSubsumsjon(
            utfall = VILKAR_IKKE_OPPFYLT,
            lovverk = "folketrygdloven",
            versjon = LocalDate.of(2011, 12, 16),
            paragraf = Paragraf.PARAGRAF_22_13,
            ledd = LEDD_3,
            input = mapOf(
                "avskjæringsdato" to avskjæringsdato
            ),
            output = mapOf(
                "perioder" to perioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                }
            ),
            kontekster = kontekster()
        ))
    }

    override fun `fvl § 35 ledd 1`() {
        leggTil(EnkelSubsumsjon(
            utfall = VILKAR_OPPFYLT,
            lovverk = "forvaltningsloven",
            versjon = LocalDate.of(2021, 6, 1),
            paragraf = Paragraf.PARAGRAF_35,
            ledd = LEDD_1,
            input = mapOf(
                "stadfesting" to true
            ),
            output = emptyMap(),
            kontekster = kontekster()
        ))
    }

    override fun `§ 8-48 ledd 2 punktum 2`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {
        leggTil(GrupperbarSubsumsjon(
            dato = dato,
            utfall = VILKAR_IKKE_OPPFYLT,
            lovverk = "folketrygdloven",
            versjon = LocalDate.parse("2021-05-21"),
            paragraf = PARAGRAF_8_48,
            ledd = LEDD_2,
            punktum = Punktum.PUNKTUM_2,
            input = mapOf("sykdomstidslinje" to sykdomstidslinje.dager(periode())),
            output = emptyMap(),
            kontekster = kontekster()
        ))
    }

    override fun `Trygderettens kjennelse 2006-4023`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {
        leggTil(GrupperbarSubsumsjon(
            dato = dato,
            utfall = VILKAR_IKKE_OPPFYLT,
            lovverk = "trygderetten",
            versjon = LocalDate.parse("2007-03-02"),
            paragraf = KJENNELSE_2006_4023,
            ledd = null,
            input = mapOf("sykdomstidslinje" to sykdomstidslinje.dager(periode())),
            output = emptyMap(),
            kontekster = kontekster()
        ))
    }

    fun subsumsjoner() = subsumsjoner.toList()

    fun events() = subsumsjoner.map(SubsumsjonEvent.Companion::fraSubsumsjon)

    data class SubsumsjonEvent(
        val id: UUID = UUID.randomUUID(),
        val sporing: Map<KontekstType, List<String>>,
        val lovverk: String,
        val ikrafttredelse: String,
        val paragraf: String,
        val ledd: Int?,
        val punktum: Int?,
        val bokstav: Char?,
        val input: Map<String, Any>,
        val output: Map<String, Any>,
        val utfall: String,
    ) {

        companion object {

            private val paragrafVersjonFormaterer = DateTimeFormatter.ISO_DATE

            internal fun fraSubsumsjon(subsumsjon: Subsumsjon): SubsumsjonEvent {
                return object : SubsumsjonVisitor {
                    lateinit var event: SubsumsjonEvent

                    init {
                        subsumsjon.accept(this)
                    }

                    override fun preVisitSubsumsjon(
                        utfall: Utfall,
                        lovverk: String,
                        versjon: LocalDate,
                        paragraf: Paragraf,
                        ledd: Ledd?,
                        punktum: Punktum?,
                        bokstav: Bokstav?,
                        input: Map<String, Any>,
                        output: Map<String, Any>,
                        kontekster: Map<String, KontekstType>
                    ) {
                        event = SubsumsjonEvent(
                            sporing = kontekster.toMutableMap()
                                .filterNot { it.value == KontekstType.Fødselsnummer }
                                .toList()
                                .fold(mutableMapOf()) { acc, kontekst ->
                                    acc.compute(kontekst.second) { _, value ->
                                        value?.plus(
                                            kontekst.first
                                        ) ?: mutableListOf(kontekst.first)
                                    }
                                    acc
                                },
                            lovverk = lovverk,
                            ikrafttredelse = paragrafVersjonFormaterer.format(versjon),
                            paragraf = paragraf.ref,
                            ledd = ledd?.nummer,
                            punktum = punktum?.toJson(),
                            bokstav = bokstav?.toJson(),
                            input = input,
                            output = output,
                            utfall = utfall.name
                        )
                    }
                }.event
            }
        }
    }
}

internal class RangeIterator(start: LocalDate, private val end: LocalDate): Iterator<LocalDate> {
    private var currentDate = start
    constructor(range: ClosedRange<LocalDate>) : this(range.start, range.endInclusive)
    fun subsetFom(fom: LocalDate) = apply {
        currentDate = maxOf(currentDate, fom)
    }
    override fun hasNext() = end >= currentDate
    override fun next(): LocalDate {
        check(hasNext())
        return currentDate.also {
            currentDate = it.plusDays(1)
        }
    }

    internal companion object {
        // forutsetter at <other> er sortert
        fun ClosedRange<LocalDate>.trim(other: SortedSet<LocalDate>): List<ClosedRange<LocalDate>> {
            return other.fold(listOf(this)) { result, date ->
                val siste = result.last()
                // ingen trim
                if (date !in siste) result
                // trimmer hele
                else {
                    val matcherSiste = date == siste.endInclusive
                    val matcherFørste = date == siste.start
                    if (matcherFørste && matcherSiste) result.dropLast(1)
                    else {
                        val nye = mutableListOf<ClosedRange<LocalDate>>()
                        // trimmer slutten eller inni
                        if (matcherSiste || !matcherFørste) nye.add(siste.start.rangeTo(date.minusDays(1)))
                        // trimmer starten eller inni
                        if (matcherFørste || !matcherSiste) nye.add(date.plusDays(1).rangeTo(siste.endInclusive))
                        result.dropLast(1) + nye
                    }
                }
            }
        }
        // utvider perioden hvis <dato> ligger inntil, foran eller bak, (inkl. helg)
        fun ClosedRange<LocalDate>.utvide(dato: LocalDate): ClosedRange<LocalDate>? {
            if (dato in this) return this
            val dagen = dato.dayOfWeek
            return when {
                (endInclusive == dato.minusDays(1)) ||
                (endInclusive in dato.minusDays(2)..dato.minusDays(1) && dagen == DayOfWeek.SUNDAY) ||
                (endInclusive in dato.minusDays(3)..dato.minusDays(1) && dagen == DayOfWeek.MONDAY) ->
                    start.rangeTo(dato)
                (start == dato.plusDays(1)) ||
                (start in dato.plusDays(1)..dato.plusDays(2) && dagen == DayOfWeek.SATURDAY) ||
                (start in dato.plusDays(1)..dato.plusDays(3) && dagen == DayOfWeek.FRIDAY) ->
                    dato.rangeTo(endInclusive)
                else -> null
            }
        }
        fun List<ClosedRange<LocalDate>>.merge(): List<ClosedRange<LocalDate>> {
            if (this.size <= 1) return this
            return this
                .sortedBy { it.start }
                .fold(emptyList()) { result, range ->
                    val ny = result.lastOrNull()?.utvide(range.start)
                    if (result.isEmpty()) listOf(range)
                    else if (ny != null) result.dropLast(1).plusElement(ny)
                    else result.plusElement(range)
                }
        }
        fun ClosedRange<LocalDate>.iterator(): Iterator<LocalDate> = RangeIterator(this)
        fun ClosedRange<LocalDate>.forEach(block: (LocalDate) -> Unit) {
            iterator().forEach(block)
        }
    }
}