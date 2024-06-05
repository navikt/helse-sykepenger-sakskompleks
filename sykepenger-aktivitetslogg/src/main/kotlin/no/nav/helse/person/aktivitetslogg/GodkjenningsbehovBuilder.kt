package no.nav.helse.person.aktivitetslogg

import java.time.LocalDate
import java.util.UUID

class PeriodeMedSammeSkjæringstidspunkt(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val periode: ClosedRange<LocalDate>
)
class GodkjenningsbehovBuilder(
    private val erForlengelse: Boolean,
    private val kanAvvises: Boolean,
    periode: ClosedRange<LocalDate>,
    private val behandlingId: UUID,
    private val perioderMedSammeSkjæringstidspunkt: List<PeriodeMedSammeSkjæringstidspunkt>,
    private val hendelser: Set<UUID>
) {
    private val tags: MutableSet<String> = mutableSetOf()
    private lateinit var skjæringstidspunkt: LocalDate
    private lateinit var vilkårsgrunnlagId: UUID
    private val periodeFom: LocalDate = periode.start
    private val periodeTom: LocalDate = periode.endInclusive
    private lateinit var periodetype: UtbetalingPeriodetype
    private val førstegangsbehandling = !erForlengelse
    private lateinit var utbetalingtype: String
    private lateinit var inntektskilde: UtbetalingInntektskilde
    private lateinit var orgnummereMedRelevanteArbeidsforhold: Set<String>
    private val omregnedeÅrsinntekter: MutableList<Map<String, Any>> = mutableListOf()

    private lateinit var sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta
    fun sykepengegrunnlagsfakta(sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta) = apply { this.sykepengegrunnlagsfakta = sykepengegrunnlagsfakta }

    init {
        if (førstegangsbehandling) tags.add("Førstegangsbehandling")
        else tags.add("Forlengelse")
    }

    fun tagFlereArbeidsgivere(antall: Int) {
        if (antall > 1) tags.add("FlereArbeidsgivere")
        else tags.add("EnArbeidsgiver")
    }
    fun skjæringstidspunkt(skjæringstidspunkt: LocalDate) = apply {
        this.skjæringstidspunkt = skjæringstidspunkt
    }

    fun vilkårsgrunnlagId(vilkårsgrunnlagId: UUID) = apply {
        this.vilkårsgrunnlagId = vilkårsgrunnlagId
    }

    fun erInfotrygd(erInfotrygd: Boolean) = apply {
        this.periodetype = when (erForlengelse) {
            true -> when (erInfotrygd) {
                true -> UtbetalingPeriodetype.INFOTRYGDFORLENGELSE
                else -> UtbetalingPeriodetype.FORLENGELSE
            }
            false -> when (erInfotrygd) {
                true -> UtbetalingPeriodetype.OVERGANG_FRA_IT
                else -> UtbetalingPeriodetype.FØRSTEGANGSBEHANDLING
            }
        }
        if (erInfotrygd) {
            tags.add("InngangsvilkårFraInfotrygd")
        }
    }
    fun utbetalingtype(utbetalingtype: String) = apply {
        this.utbetalingtype = utbetalingtype
    }
    fun inntektskilde(inntektskilde: UtbetalingInntektskilde) = apply {
        this.inntektskilde = inntektskilde
    }
    fun orgnummereMedRelevanteArbeidsforhold(orgnummereMedRelevanteArbeidsforhold: Set<String>) = apply {
        this.orgnummereMedRelevanteArbeidsforhold = orgnummereMedRelevanteArbeidsforhold
    }
    fun tagUtbetaling(arbeidsgiverNettoBeløp: Int, personNettoBeløp: Int) {
        val antallTagsFør = tags.size

        if (arbeidsgiverNettoBeløp > 0) tags.add("Arbeidsgiverutbetaling")
        else if (arbeidsgiverNettoBeløp < 0) tags.add("NegativArbeidsgiverutbetaling")

        if (personNettoBeløp > 0) tags.add("Personutbetaling")
        else if (personNettoBeløp < 0) tags.add("NegativPersonutbetaling")

        if (arbeidsgiverNettoBeløp == 0 && personNettoBeløp == 0) tags.add("IngenUtbetaling")

        check(tags.size > antallTagsFør) { "arbeidsgiverNettoBeløp=$arbeidsgiverNettoBeløp, personNettoBeløp=$personNettoBeløp burde bli minst én ny tag." }
    }

    fun tagBehandlingsresultat(behandlingsresultat: String) {
        tags.add(behandlingsresultat)
    }
    fun tagSykepengegrunnlagErUnder2G() {
        tags.add("SykepengegrunnlagUnder2G")
    }
    fun tagSykepengegrunnlagEr6GBegrenset() {
        tags.add("6GBegrenset")
    }
    fun tagIngenNyArbeidsgiverperiode() {
        tags.add("IngenNyArbeidsgiverperiode")
    }

    fun omregnedeÅrsinntekter(orgnummer: String, omregnetÅrsinntekt: Double) = apply {
        omregnedeÅrsinntekter.add(mapOf("organisasjonsnummer" to orgnummer, "beløp" to omregnetÅrsinntekt))
    }

    fun build() = mapOf(
        "periodeFom" to periodeFom.toString(),
        "periodeTom" to periodeTom.toString(),
        "skjæringstidspunkt" to skjæringstidspunkt.toString(),
        "vilkårsgrunnlagId" to vilkårsgrunnlagId.toString(),
        "periodetype" to periodetype.name,
        "førstegangsbehandling" to førstegangsbehandling,
        "utbetalingtype" to utbetalingtype,
        "inntektskilde" to inntektskilde.name,
        "orgnummereMedRelevanteArbeidsforhold" to orgnummereMedRelevanteArbeidsforhold,
        "tags" to tags,
        "kanAvvises" to kanAvvises,
        "omregnedeÅrsinntekter" to omregnedeÅrsinntekter,
        "behandlingId" to behandlingId.toString(),
        "hendelser" to hendelser,
        "perioderMedSammeSkjæringstidspunkt" to perioderMedSammeSkjæringstidspunkt.map {
            mapOf(
                "vedtaksperiodeId" to it.vedtaksperiodeId.toString(),
                "behandlingId" to it.behandlingId.toString(),
                "fom" to it.periode.start.toString(),
                "tom" to it.periode.endInclusive.toString()
            )
        },
        "sykepengegrunnlagsfakta" to when (sykepengegrunnlagsfakta) {
            is FastsattIInfotrygd -> mapOf(
                "omregnetÅrsinntektTotalt" to sykepengegrunnlagsfakta.omregnetÅrsinntektTotalt,
                "fastsatt" to sykepengegrunnlagsfakta.fastsatt.name
            )
            is FastsattISpeil -> mutableMapOf(
                "omregnetÅrsinntektTotalt" to sykepengegrunnlagsfakta.omregnetÅrsinntektTotalt,
                "6G" to (sykepengegrunnlagsfakta as FastsattISpeil).`6G`,
                "fastsatt" to sykepengegrunnlagsfakta.fastsatt.name,
                "arbeidsgivere" to (sykepengegrunnlagsfakta as FastsattISpeil).arbeidsgivere.map {
                    mutableMapOf(
                        "arbeidsgiver" to it.arbeidsgiver,
                        "omregnetÅrsinntekt" to it.omregnetÅrsinntekt,
                    ).apply {
                        compute("skjønnsfastsatt") { _, _ -> it.skjønnsfastsatt }
                    }
                },
            ).apply {
                compute("skjønnsfastsatt") { _, _ -> (sykepengegrunnlagsfakta as FastsattISpeil).skjønnsfastsatt}
            }
        }
    )

    fun tags() = tags.toSet()

    sealed class SykepengegrunnlagsfaktaBuilder {
        abstract fun build(): Sykepengegrunnlagsfakta
    }

    class FastsattIInfotrygdBuilder(private val omregnetÅrsinntektTotalt: Double) : SykepengegrunnlagsfaktaBuilder() {
        override fun build() = FastsattIInfotrygd(omregnetÅrsinntektTotalt)
    }
    class FastsattISpleisBuilder(
        private val omregnetÅrsinntektTotalt: Double,
        private val `6G`: Double
    ) : SykepengegrunnlagsfaktaBuilder() {

        private val arbeidsgivere = mutableListOf<FastsattISpeil.Arbeidsgiver>()
        fun arbeidsgiver(arbeidsgiver: String, omregnetÅrsinntekt: Double, skjønnsfastsatt: Double?) = apply {
            arbeidsgivere.add(
                FastsattISpeil.Arbeidsgiver(
                    arbeidsgiver,
                    omregnetÅrsinntekt,
                    skjønnsfastsatt
                )
            )
        }

        override fun build() = FastsattISpeil(
            omregnetÅrsinntektTotalt = omregnetÅrsinntektTotalt,
            `6G`= `6G`,
            arbeidsgivere = arbeidsgivere.toList()
        )
    }

    enum class Fastsatt {
        EtterHovedregel,
        EtterSkjønn,
        IInfotrygd
    }

    sealed class Sykepengegrunnlagsfakta {
        abstract val fastsatt: Fastsatt
        abstract val omregnetÅrsinntektTotalt: Double
    }
    data class FastsattIInfotrygd(override val omregnetÅrsinntektTotalt: Double) : Sykepengegrunnlagsfakta() {
        override val fastsatt = Fastsatt.IInfotrygd
    }
    data class FastsattISpeil(
        override val omregnetÅrsinntektTotalt: Double,
        val `6G`: Double,
        val arbeidsgivere: List<Arbeidsgiver>
    ) : Sykepengegrunnlagsfakta() {
        val skjønnsfastsatt: Double? = arbeidsgivere.mapNotNull { it.skjønnsfastsatt }.takeIf(List<*>::isNotEmpty)?.sum()
        override val fastsatt = if (skjønnsfastsatt == null) Fastsatt.EtterHovedregel else Fastsatt.EtterSkjønn
        data class Arbeidsgiver(val arbeidsgiver: String, val omregnetÅrsinntekt: Double, val skjønnsfastsatt: Double?)
    }

}