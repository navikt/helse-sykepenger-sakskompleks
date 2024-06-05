package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.dto.MedlemskapsvurderingDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlagInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlagInnslagInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlaghistorikkInnDto
import no.nav.helse.dto.serialisering.SykepengegrunnlagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagInnslagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlaghistorikkUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.NullObserver
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.til
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement.Companion.skjæringstidspunktperioder
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_8
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Økonomi

internal class VilkårsgrunnlagHistorikk private constructor(private val historikk: MutableList<Innslag>) {

    internal constructor() : this(mutableListOf())

    private fun sisteInnlag() = historikk.firstOrNull()

    private fun forrigeInnslag() = historikk.elementAtOrNull(1)

    internal fun accept(visitor: VilkårsgrunnlagHistorikkVisitor) {
        visitor.preVisitVilkårsgrunnlagHistorikk()
        historikk.forEach { it.accept(visitor) }
        visitor.postVisitVilkårsgrunnlagHistorikk()
    }

    internal fun lagre(vararg vilkårsgrunnlag: VilkårsgrunnlagElement) {
        if (vilkårsgrunnlag.isEmpty()) return
        val siste = sisteInnlag()
        val nytt = Innslag(siste, vilkårsgrunnlag.toList())
        if (nytt == siste) return
        historikk.add(0, nytt)
    }

    internal fun gjenoppliv(hendelse: GjenopplivVilkårsgrunnlag, vilkårsgrunnlagId: UUID, nyttSkjæringstidspunkt: LocalDate?) {
        if (!kanGjenopplive(hendelse, vilkårsgrunnlagId, nyttSkjæringstidspunkt)) return hendelse.info("Kan ikke gjenopplive. Vilkårsgrunnlaget lever!")
        val gjenopplivet = historikk.firstNotNullOfOrNull { it.gjennoppliv(hendelse, vilkårsgrunnlagId, nyttSkjæringstidspunkt) } ?: return hendelse.info("Fant ikke vilkårsgrunnlag å gjenopplive")
        lagre(gjenopplivet)
    }

    private fun kanGjenopplive(hendelse: GjenopplivVilkårsgrunnlag, vilkårsgrunnlagId: UUID, nyttSkjæringstidspunkt: LocalDate?): Boolean {
        return when (nyttSkjæringstidspunkt) {
            null -> sisteInnlag()?.gjennoppliv(hendelse, vilkårsgrunnlagId, null) == null
            else -> sisteInnlag()?.vilkårsgrunnlagFor(nyttSkjæringstidspunkt) == null
        }
    }

    internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, sykefraværstilfeller: Set<LocalDate>) {
        val nyttInnslag = sisteInnlag()?.oppdaterHistorikk(aktivitetslogg, sykefraværstilfeller) ?: return
        if (nyttInnslag == sisteInnlag()) return
        historikk.add(0, nyttInnslag)
    }

    internal fun forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer: String, skjæringstidspunkt: LocalDate, periode: Periode): List<PersonObserver.ForespurtOpplysning> {
        return sisteInnlag()?.forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer, skjæringstidspunkt, periode) ?: listOf(
            PersonObserver.Inntekt(forslag = null),
            PersonObserver.Refusjon(forslag = emptyList())
        )
    }

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
        sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun avvisInngangsvilkår(tidslinjer: List<Utbetalingstidslinje>, periode: Periode, subsumsjonslogg: Subsumsjonslogg) =
        sisteInnlag()?.avvis(tidslinjer, periode, subsumsjonslogg) ?: tidslinjer

    internal fun medInntekt(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, regler: ArbeidsgiverRegler, subsumsjonslogg: Subsumsjonslogg) =
        sisteInnlag()!!.medInntekt(organisasjonsnummer, dato, økonomi, regler, subsumsjonslogg)

    internal fun medUtbetalingsopplysninger(hendelse: IAktivitetslogg, organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, regler: ArbeidsgiverRegler, subsumsjonslogg: Subsumsjonslogg) =
        sisteInnlag()!!.medUtbetalingsopplysninger(hendelse, organisasjonsnummer, dato, økonomi, regler, subsumsjonslogg)

    internal fun blitt6GBegrensetSidenSist(skjæringstidspunkt: LocalDate): Boolean {
        if (sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)?.er6GBegrenset() == false) return false
        return forrigeInnslag()?.vilkårsgrunnlagFor(skjæringstidspunkt)?.er6GBegrenset() == false
    }

    fun ghosttidslinje(organisasjonsnummer: String, sisteDag: LocalDate): Sykdomstidslinje {
        return sisteInnlag()?.ghosttidslinje(organisasjonsnummer, sisteDag) ?: Sykdomstidslinje()
    }

    internal class Innslag private constructor(
        internal val id: UUID,
        private val opprettet: LocalDateTime,
        private val vilkårsgrunnlag: MutableMap<LocalDate, VilkårsgrunnlagElement>
    ) {
        internal constructor(vilkårsgrunnlag: Map<LocalDate, VilkårsgrunnlagElement>) : this(
            UUID.randomUUID(),
            LocalDateTime.now(),
            vilkårsgrunnlag.toMutableMap()
        )

        internal constructor(other: Innslag?, elementer: List<VilkårsgrunnlagElement>) : this(other?.vilkårsgrunnlag?.toMap()?: emptyMap()) {
            elementer.forEach { it.add(this) }
        }

        internal fun accept(visitor: VilkårsgrunnlagHistorikkVisitor) {
            visitor.preVisitInnslag(this, id, opprettet)
            vilkårsgrunnlag.forEach { (_, element) ->
                element.accept(visitor)
            }
            visitor.postVisitInnslag(this, id, opprettet)
        }

        internal fun add(skjæringstidspunkt: LocalDate, vilkårsgrunnlagElement: VilkårsgrunnlagElement) {
            vilkårsgrunnlag[skjæringstidspunkt] = vilkårsgrunnlagElement
        }

        internal fun forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer: String, skjæringstidspunkt: LocalDate, periode: Periode): List<PersonObserver.ForespurtOpplysning>? {
            val fastsatteOpplysninger = vilkårsgrunnlagFor(skjæringstidspunkt)
                ?.forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer, periode)

            if (fastsatteOpplysninger != null) {
                val (fastsattInntekt, fastsattRefusjon) = fastsatteOpplysninger
                return listOf(fastsattInntekt, fastsattRefusjon)
            }

            // om arbeidsgiveren ikke har noen fastsatte opplysninger på skjæringstidspunktet så foreslår
            // vi inntekt og refusjon fra forrige skjæringstidspunkt arbeidsgiveren er representert
            val forrigeFastsatteOpplysning = vilkårsgrunnlag
                .keys
                .filter { it < skjæringstidspunkt }
                .sortedDescending()
                .firstNotNullOfOrNull {
                    vilkårsgrunnlagFor(it)?.forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer, periode)
                } ?: return null

            val (_, refusjonForslag, inntektForslag) = forrigeFastsatteOpplysning
            return listOf(
                PersonObserver.Inntekt(forslag = inntektForslag),
                PersonObserver.Refusjon(forslag = refusjonForslag.forslag)
            )
        }

        internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
            vilkårsgrunnlag[skjæringstidspunkt]

        internal fun avvis(tidslinjer: List<Utbetalingstidslinje>, periode: Periode, subsumsjonslogg: Subsumsjonslogg): List<Utbetalingstidslinje> {
            val skjæringstidspunktperioder = skjæringstidspunktperioder(vilkårsgrunnlag.values)
            return vilkårsgrunnlag.entries.fold(tidslinjer) { resultat, (skjæringstidspunkt, element) ->
                val skjæringstidspunktperiode = checkNotNull(skjæringstidspunktperioder.singleOrNull { it.start == skjæringstidspunkt })
                element.avvis(resultat, skjæringstidspunktperiode, periode, subsumsjonslogg)
            }
        }

        internal fun medInntekt(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonslogg: Subsumsjonslogg
        ) = VilkårsgrunnlagElement.medInntekt(
            vilkårsgrunnlag.values,
            organisasjonsnummer,
            dato,
            økonomi,
            regler,
            subsumsjonslogg
        )

        internal fun medUtbetalingsopplysninger(
            hendelse: IAktivitetslogg,
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonslogg: Subsumsjonslogg,
        ) = VilkårsgrunnlagElement.medUtbetalingsopplysninger(
            vilkårsgrunnlag.values,
            hendelse,
            organisasjonsnummer,
            dato,
            økonomi,
            regler,
            subsumsjonslogg
        )

        override fun hashCode(): Int {
            return this.vilkårsgrunnlag.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Innslag) return false
            return this.vilkårsgrunnlag == other.vilkårsgrunnlag
        }

        internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, sykefraværstilfeller: Set<LocalDate>): Innslag {
            val gyldigeVilkårsgrunnlag = beholdAktiveSkjæringstidspunkter(sykefraværstilfeller)
            val diff = this.vilkårsgrunnlag.size - gyldigeVilkårsgrunnlag.size
            if (diff > 0) aktivitetslogg.info("Fjernet $diff vilkårsgrunnlagselementer")
            return Innslag(gyldigeVilkårsgrunnlag)
        }

        private fun beholdAktiveSkjæringstidspunkter(sykefraværstilfeller: Set<LocalDate>): Map<LocalDate, VilkårsgrunnlagElement> {
            return vilkårsgrunnlag.filter { (dato, _) -> dato in sykefraværstilfeller }
        }

        internal fun gjennoppliv(hendelse: GjenopplivVilkårsgrunnlag, vilkårsgrunnlagId: UUID, nyttSkjæringstidspunkt: LocalDate?) = vilkårsgrunnlag.values.firstNotNullOfOrNull { it.gjenoppliv(hendelse, vilkårsgrunnlagId, nyttSkjæringstidspunkt) }
        fun ghosttidslinje(organisasjonsnummer: String, sisteDag: LocalDate) =
            vilkårsgrunnlag.mapNotNull { it.value.ghosttidslinje(organisasjonsnummer, sisteDag) }.merge()

        internal companion object {
            fun gjenopprett(
                id: UUID,
                opprettet: LocalDateTime,
                elementer: Map<LocalDate, VilkårsgrunnlagElement>
            ): Innslag = Innslag(id, opprettet, elementer.toMutableMap())

            fun gjenopprett(alder: Alder, dto: VilkårsgrunnlagInnslagInnDto, inntekter: MutableMap<UUID, Inntektsopplysning>, grunnlagsdata: MutableMap<UUID, VilkårsgrunnlagElement>): Innslag {
                return Innslag(
                    id = dto.id,
                    opprettet = dto.opprettet,
                    vilkårsgrunnlag = dto.vilkårsgrunnlag.associate {
                        it.skjæringstidspunkt to grunnlagsdata.getOrPut(it.vilkårsgrunnlagId) {
                            VilkårsgrunnlagElement.gjenopprett(alder, it, inntekter)
                        }
                    }.toMutableMap()
                )
            }
        }

        internal fun dto() = VilkårsgrunnlagInnslagUtDto(
            id = this.id,
            opprettet = this.opprettet,
            vilkårsgrunnlag = this.vilkårsgrunnlag.map { it.value.dto() }
        )
    }

    internal abstract class VilkårsgrunnlagElement(
        protected val vilkårsgrunnlagId: UUID,
        protected val skjæringstidspunkt: LocalDate,
        protected val sykepengegrunnlag: Sykepengegrunnlag,
        private val opptjening: Opptjening?
    ) : Aktivitetskontekst {
        internal fun add(innslag: Innslag) {
            innslag.add(skjæringstidspunkt, this)
        }

        internal open fun valider(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String, organisasjonsnummerRelevanteArbeidsgivere: List<String>) = true

        internal abstract fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor)
        internal fun erArbeidsgiverRelevant(organisasjonsnummer: String) = sykepengegrunnlag.erArbeidsgiverRelevant(organisasjonsnummer)

        internal fun build(builder: VedtakFattetBuilder) {
            sykepengegrunnlag.build(builder)
        }

        internal fun build(builder: GodkjenningsbehovBuilder) {
            sykepengegrunnlag.build(builder)
        }

        internal fun inntektskilde() = sykepengegrunnlag.inntektskilde()

        internal fun forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer: String, periode: Periode) =
            sykepengegrunnlag.forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer, periode)

        internal open fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode, periode: Periode, subsumsjonslogg: Subsumsjonslogg): List<Utbetalingstidslinje> {
            return tidslinjer
        }

        final override fun toSpesifikkKontekst() = SpesifikkKontekst(
            kontekstType = "vilkårsgrunnlag",
            kontekstMap = mapOf(
                "vilkårsgrunnlagId" to vilkårsgrunnlagId.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                "vilkårsgrunnlagtype" to vilkårsgrunnlagtype()
            )
        )

        internal fun overstyrArbeidsgiveropplysninger(person: Person, hendelse: OverstyrArbeidsgiveropplysninger, subsumsjonslogg: Subsumsjonslogg): Pair<VilkårsgrunnlagElement, Revurderingseventyr> {
            val sykepengegrunnlag = sykepengegrunnlag.overstyrArbeidsgiveropplysninger(person, hendelse, opptjening, subsumsjonslogg)
            val endringsdato = sykepengegrunnlag.finnEndringsdato(this.sykepengegrunnlag)
            val eventyr = Revurderingseventyr.arbeidsgiveropplysninger(hendelse, skjæringstidspunkt, endringsdato)
            return kopierMed(hendelse, sykepengegrunnlag, opptjening, subsumsjonslogg) to eventyr
        }
        internal fun skjønnsmessigFastsettelse(hendelse: SkjønnsmessigFastsettelse, subsumsjonslogg: Subsumsjonslogg): Pair<VilkårsgrunnlagElement, Revurderingseventyr> {
            val sykepengegrunnlag = sykepengegrunnlag.skjønnsmessigFastsettelse(hendelse, opptjening, subsumsjonslogg)
            val endringsdato = sykepengegrunnlag.finnEndringsdato(this.sykepengegrunnlag)
            val eventyr = Revurderingseventyr.skjønnsmessigFastsettelse(hendelse, skjæringstidspunkt, endringsdato)
            return kopierMed(hendelse, sykepengegrunnlag, opptjening, subsumsjonslogg) to eventyr
        }
        protected abstract fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonslogg: Subsumsjonslogg,
            nyttSkjæringstidspunkt: LocalDate? = null
        ): VilkårsgrunnlagElement

        abstract fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonslogg: Subsumsjonslogg
        ): VilkårsgrunnlagElement

        internal fun grunnbeløpsregulering(
            hendelse: Grunnbeløpsregulering,
            subsumsjonslogg: Subsumsjonslogg
        ): VilkårsgrunnlagElement? {
            val nyttSykepengegrunnlag = sykepengegrunnlag.grunnbeløpsregulering()
            if (nyttSykepengegrunnlag == sykepengegrunnlag) {
                hendelse.info("Grunnbeløpet i sykepengegrunnlaget $skjæringstidspunkt er allerede korrekt.")
                return null
            }
            hendelse.info("Grunnbeløpet i sykepengegrunnlaget $skjæringstidspunkt korrigeres til rett beløp.")
            return kopierMed(hendelse, nyttSykepengegrunnlag, opptjening, subsumsjonslogg)
        }

        internal fun nyeArbeidsgiverInntektsopplysninger(
            person: Person,
            inntektsmelding: Inntektsmelding,
            subsumsjonslogg: Subsumsjonslogg
        ): Pair<VilkårsgrunnlagElement, Revurderingseventyr>  {
            val sykepengegrunnlag = sykepengegrunnlag.nyeArbeidsgiverInntektsopplysninger(
                person,
                inntektsmelding,
                subsumsjonslogg
            )
            val endringsdato = sykepengegrunnlag.finnEndringsdato(this.sykepengegrunnlag)
            val eventyr = Revurderingseventyr.korrigertInntektsmeldingInntektsopplysninger(inntektsmelding, skjæringstidspunkt, endringsdato)
            return kopierMed(inntektsmelding, sykepengegrunnlag, opptjening, NullObserver) to eventyr
        }

        protected abstract fun vilkårsgrunnlagtype(): String
        private fun medInntekt(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonslogg: Subsumsjonslogg
        ) = sykepengegrunnlag.medInntekt(organisasjonsnummer, dato, økonomi, regler, subsumsjonslogg)

        private fun medUtbetalingsopplysninger(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonslogg: Subsumsjonslogg
        ) = sykepengegrunnlag.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, regler, subsumsjonslogg)

        private fun utenInntekt(økonomi: Økonomi): Økonomi {
            return sykepengegrunnlag.utenInntekt(økonomi)
        }

        internal fun er6GBegrenset() = sykepengegrunnlag.er6GBegrenset()

        internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
            sykepengegrunnlag.harNødvendigInntektForVilkårsprøving(organisasjonsnummer)

        internal fun refusjonsopplysninger(organisasjonsnummer: String) =
            sykepengegrunnlag.refusjonsopplysninger(organisasjonsnummer)

        internal fun lagreTidsnæreInntekter(
            skjæringstidspunkt: LocalDate,
            arbeidsgiver: Arbeidsgiver,
            hendelse: IAktivitetslogg,
            oppholdsperiodeMellom: Periode?
        ) {
            sykepengegrunnlag.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, hendelse, oppholdsperiodeMellom)
        }

        internal fun byggGodkjenningsbehov(builder: GodkjenningsbehovBuilder) {
            builder.erInfotrygd(this is InfotrygdVilkårsgrunnlag)
            builder.skjæringstidspunkt(skjæringstidspunkt)
            builder.vilkårsgrunnlagId(vilkårsgrunnlagId)
            this.build(builder)
            sykepengegrunnlag.byggGodkjenningsbehov(builder)
        }

        internal fun ghosttidslinje(organisasjonsnummer: String, sisteDag: LocalDate) =
            sykepengegrunnlag.ghosttidslinje(organisasjonsnummer, sisteDag)

        internal fun gjenoppliv(hendelse: GjenopplivVilkårsgrunnlag, vilkårsgrunnlagId: UUID, nyttSkjæringstidspunkt: LocalDate?): VilkårsgrunnlagElement? {
            if (this.vilkårsgrunnlagId != vilkårsgrunnlagId) return null
            val gjenopplivetSykepengegrunnlag = this.sykepengegrunnlag.gjenoppliv(hendelse, nyttSkjæringstidspunkt) ?: return null
            return kopierMed(hendelse, gjenopplivetSykepengegrunnlag, this.opptjening, NullObserver, nyttSkjæringstidspunkt)
        }

        internal companion object {
            internal fun skjæringstidspunktperioder(elementer: Collection<VilkårsgrunnlagElement>): List<Periode> {
                val skjæringstidspunkter = elementer
                    .map { it.skjæringstidspunkt }
                    .sorted()
                return skjæringstidspunkter
                    .mapIndexed { index, skjæringstidspunkt ->
                        val sisteDag = skjæringstidspunkter.elementAtOrNull(index + 1)?.forrigeDag ?: LocalDate.MAX
                        skjæringstidspunkt til sisteDag
                    }
            }

            internal fun medInntekt(
                elementer: Collection<VilkårsgrunnlagElement>,
                organisasjonsnummer: String,
                dato: LocalDate,
                økonomi: Økonomi,
                regler: ArbeidsgiverRegler,
                subsumsjonslogg: Subsumsjonslogg
            ): Økonomi {
                return finnVilkårsgrunnlag(elementer, dato)?.medInntekt(
                    organisasjonsnummer,
                    dato,
                    økonomi,
                    regler,
                    subsumsjonslogg
                ) ?: utenInntekt(elementer, dato, økonomi)
            }

            internal fun medUtbetalingsopplysninger(
                elementer: Collection<VilkårsgrunnlagElement>,
                hendelse: IAktivitetslogg,
                organisasjonsnummer: String,
                dato: LocalDate,
                økonomi: Økonomi,
                regler: ArbeidsgiverRegler,
                subsumsjonslogg: Subsumsjonslogg
            ): Økonomi {
                val vilkårsgrunnlag = finnVilkårsgrunnlag(elementer, dato)

                if (vilkårsgrunnlag == null) {
                    hendelse.info("Fant ikke vilkårsgrunnlag for $dato. Må ha et vilkårsgrunnlag for å legge til utbetalingsopplysninger. Har vilkårsgrunnlag på skjæringstidspunktene ${elementer.map { it.skjæringstidspunkt }}")
                    hendelse.varsel(RV_IV_8)
                    return utenInntekt(elementer, dato, økonomi)
                }
                return vilkårsgrunnlag.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, regler, subsumsjonslogg)
            }

            private fun utenInntekt(
                elementer: Collection<VilkårsgrunnlagElement>,
                dato: LocalDate,
                økonomi: Økonomi
            ): Økonomi {
                return finnVilkårsgrunnlag(elementer, dato)?.utenInntekt(økonomi) ?: økonomi.inntekt(
                    aktuellDagsinntekt = INGEN,
                    dekningsgrunnlag = INGEN,
                    `6G` = INGEN,
                    refusjonsbeløp = INGEN
                )
            }

            private fun finnVilkårsgrunnlag(
                elementer: Collection<VilkårsgrunnlagElement>,
                dato: LocalDate
            ): VilkårsgrunnlagElement? {
                return elementer
                    .filter { it.skjæringstidspunkt <= dato }
                    .maxByOrNull { it.skjæringstidspunkt }
            }

            internal fun gjenopprett(alder: Alder, dto: VilkårsgrunnlagInnDto, inntekter: MutableMap<UUID, Inntektsopplysning>): VilkårsgrunnlagElement {
                return when (dto) {
                    is VilkårsgrunnlagInnDto.Infotrygd -> InfotrygdVilkårsgrunnlag.gjenopprett(alder, dto, inntekter)
                    is VilkårsgrunnlagInnDto.Spleis -> Grunnlagsdata.gjenopprett(alder, dto, inntekter)
                }
            }
        }

        internal fun dto(): VilkårsgrunnlagUtDto =
            dto(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag.dto())
        protected abstract fun dto(
            vilkårsgrunnlagId: UUID,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: SykepengegrunnlagUtDto
        ): VilkårsgrunnlagUtDto
    }

    internal class Grunnlagsdata(
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        internal val opptjening: Opptjening,
        private val medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
        private val vurdertOk: Boolean,
        private val meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag, opptjening) {
        internal fun validerFørstegangsvurdering(aktivitetslogg: IAktivitetslogg) {
            sykepengegrunnlag.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjening)
            sykepengegrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
        }

        override fun valider(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String, organisasjonsnummerRelevanteArbeidsgivere: List<String>): Boolean {
            sykepengegrunnlag.sjekkForNyeArbeidsgivere(aktivitetslogg, organisasjonsnummerRelevanteArbeidsgivere)
            sykepengegrunnlag.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, organisasjonsnummer)
            return !aktivitetslogg.harFunksjonelleFeilEllerVerre()
        }

        override fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor) {
            vilkårsgrunnlagHistorikkVisitor.preVisitGrunnlagsdata(
                skjæringstidspunkt,
                this,
                sykepengegrunnlag,
                opptjening,
                vurdertOk,
                meldingsreferanseId,
                vilkårsgrunnlagId,
                medlemskapstatus
            )
            sykepengegrunnlag.accept(vilkårsgrunnlagHistorikkVisitor)
            opptjening.accept(vilkårsgrunnlagHistorikkVisitor)
            vilkårsgrunnlagHistorikkVisitor.postVisitGrunnlagsdata(
                skjæringstidspunkt,
                this,
                sykepengegrunnlag,
                medlemskapstatus,
                vurdertOk,
                meldingsreferanseId,
                vilkårsgrunnlagId
            )
        }

        override fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode, periode: Periode, subsumsjonslogg: Subsumsjonslogg): List<Utbetalingstidslinje> {
            val foreløpigAvvist = sykepengegrunnlag.avvis(tidslinjer, skjæringstidspunktperiode, periode, subsumsjonslogg)
            val begrunnelser = mutableListOf<Begrunnelse>()
            if (medlemskapstatus == Medlemskapsvurdering.Medlemskapstatus.Nei) begrunnelser.add(Begrunnelse.ManglerMedlemskap)
            if (!opptjening.harTilstrekkeligAntallOpptjeningsdager()) begrunnelser.add(Begrunnelse.ManglerOpptjening)
            return Utbetalingstidslinje.avvis(foreløpigAvvist, listOf(skjæringstidspunktperiode), begrunnelser)
        }

        override fun vilkårsgrunnlagtype() = "Spleis"

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonslogg: Subsumsjonslogg
        ) = kopierMed(
            hendelse = hendelse,
            sykepengegrunnlag = sykepengegrunnlag.overstyrArbeidsforhold(hendelse, subsumsjonslogg),
            opptjening = opptjening.overstyrArbeidsforhold(hendelse, subsumsjonslogg),
            subsumsjonslogg = subsumsjonslogg,
        )


        override fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonslogg: Subsumsjonslogg,
            nyttSkjæringstidspunkt: LocalDate?
        ): VilkårsgrunnlagElement {
            val sykepengegrunnlagOk = sykepengegrunnlag.valider(hendelse)
            val opptjeningOk = opptjening?.validerOpptjeningsdager(hendelse)
            return Grunnlagsdata(
                skjæringstidspunkt = nyttSkjæringstidspunkt ?: skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag,
                opptjening = opptjening ?: this.opptjening,
                medlemskapstatus = medlemskapstatus,
                vurdertOk = vurdertOk && sykepengegrunnlagOk && (opptjeningOk ?: true),
                meldingsreferanseId = meldingsreferanseId,
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }

        override fun dto(
            vilkårsgrunnlagId: UUID,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: SykepengegrunnlagUtDto
        ) = VilkårsgrunnlagUtDto.Spleis(
            vilkårsgrunnlagId = vilkårsgrunnlagId,
            skjæringstidspunkt = skjæringstidspunkt,
            sykepengegrunnlag = sykepengegrunnlag,
            opptjening = this.opptjening.dto(),
            medlemskapstatus = when (medlemskapstatus) {
                Medlemskapsvurdering.Medlemskapstatus.Ja -> MedlemskapsvurderingDto.Ja
                Medlemskapsvurdering.Medlemskapstatus.Nei -> MedlemskapsvurderingDto.Nei
                Medlemskapsvurdering.Medlemskapstatus.VetIkke -> MedlemskapsvurderingDto.VetIkke
                Medlemskapsvurdering.Medlemskapstatus.UavklartMedBrukerspørsmål -> MedlemskapsvurderingDto.UavklartMedBrukerspørsmål
            },
            vurdertOk = vurdertOk,
            meldingsreferanseId = meldingsreferanseId
        )

        internal companion object {
            fun gjenopprett(alder: Alder, dto: VilkårsgrunnlagInnDto.Spleis, inntekter: MutableMap<UUID, Inntektsopplysning>): Grunnlagsdata {
                return Grunnlagsdata(
                    skjæringstidspunkt = dto.skjæringstidspunkt,
                    sykepengegrunnlag = Sykepengegrunnlag.gjenopprett(alder, dto.skjæringstidspunkt, dto.sykepengegrunnlag, inntekter),
                    opptjening = Opptjening.gjenopprett(dto.skjæringstidspunkt, dto.opptjening),
                    vilkårsgrunnlagId = dto.vilkårsgrunnlagId,
                    medlemskapstatus = when (dto.medlemskapstatus) {
                        MedlemskapsvurderingDto.Ja -> Medlemskapsvurdering.Medlemskapstatus.Ja
                        MedlemskapsvurderingDto.Nei -> Medlemskapsvurdering.Medlemskapstatus.Nei
                        MedlemskapsvurderingDto.UavklartMedBrukerspørsmål -> Medlemskapsvurdering.Medlemskapstatus.UavklartMedBrukerspørsmål
                        MedlemskapsvurderingDto.VetIkke -> Medlemskapsvurdering.Medlemskapstatus.VetIkke
                    },
                    vurdertOk = dto.vurdertOk,
                    meldingsreferanseId = dto.meldingsreferanseId
                )
            }
        }
    }

    internal class InfotrygdVilkårsgrunnlag(
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID = UUID.randomUUID()
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag, null) {

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonslogg: Subsumsjonslogg
        ) = kopierMed(
            hendelse = hendelse,
            sykepengegrunnlag = sykepengegrunnlag.overstyrArbeidsforhold(hendelse, subsumsjonslogg),
            opptjening = null,
            subsumsjonslogg = subsumsjonslogg
        )

        override fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonslogg: Subsumsjonslogg,
            nyttSkjæringstidspunkt: LocalDate?
        ): InfotrygdVilkårsgrunnlag {
            return InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = nyttSkjæringstidspunkt ?: skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag,
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }

        override fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor) {
            vilkårsgrunnlagHistorikkVisitor.preVisitInfotrygdVilkårsgrunnlag(
                this,
                skjæringstidspunkt,
                sykepengegrunnlag,
                vilkårsgrunnlagId
            )
            sykepengegrunnlag.accept(vilkårsgrunnlagHistorikkVisitor)
            vilkårsgrunnlagHistorikkVisitor.postVisitInfotrygdVilkårsgrunnlag(
                this,
                skjæringstidspunkt,
                sykepengegrunnlag,
                vilkårsgrunnlagId
            )
        }

        override fun vilkårsgrunnlagtype() = "Infotrygd"

        override fun equals(other: Any?): Boolean {
            if (other !is InfotrygdVilkårsgrunnlag) return false
            return skjæringstidspunkt == other.skjæringstidspunkt && sykepengegrunnlag == other.sykepengegrunnlag
        }

        override fun hashCode(): Int {
            var result = skjæringstidspunkt.hashCode()
            result = 31 * result + sykepengegrunnlag.hashCode()
            return result
        }

        override fun dto(
            vilkårsgrunnlagId: UUID,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: SykepengegrunnlagUtDto
        ) = VilkårsgrunnlagUtDto.Infotrygd(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag)

        internal companion object {
            fun gjenopprett(alder: Alder, dto: VilkårsgrunnlagInnDto.Infotrygd, inntekter: MutableMap<UUID, Inntektsopplysning>): InfotrygdVilkårsgrunnlag {
                return InfotrygdVilkårsgrunnlag(
                    skjæringstidspunkt = dto.skjæringstidspunkt,
                    sykepengegrunnlag = Sykepengegrunnlag.gjenopprett(alder, dto.skjæringstidspunkt, dto.sykepengegrunnlag, inntekter),
                    vilkårsgrunnlagId = dto.vilkårsgrunnlagId
                )
            }
        }
    }

    internal companion object {

        internal fun gjenopprett(alder: Alder, dto: VilkårsgrunnlaghistorikkInnDto, grunnlagsdata: MutableMap<UUID, VilkårsgrunnlagElement>): VilkårsgrunnlagHistorikk {
            val inntekter = mutableMapOf<UUID, Inntektsopplysning>()
            return VilkårsgrunnlagHistorikk(
                historikk = dto.historikk.asReversed().map { Innslag.gjenopprett(alder, it, inntekter, grunnlagsdata) }.asReversed().toMutableList()
            )
        }
    }

    fun dto() = VilkårsgrunnlaghistorikkUtDto(
        historikk = this.historikk.map { it.dto() }
    )
}
