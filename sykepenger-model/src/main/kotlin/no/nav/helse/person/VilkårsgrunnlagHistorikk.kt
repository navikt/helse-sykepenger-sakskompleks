package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
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
import no.nav.helse.person.Sykefraværstilfelleeventyr.Companion.erAktivtSkjæringstidspunkt
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement.Companion.skjæringstidspunktperioder
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Støtter ikke søknadstypen for forlengelser vilkårsprøvd i Infotrygd`
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_11
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
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

    internal fun build(skjæringstidspunkt: LocalDate, builder: VedtakFattetBuilder) {
        vilkårsgrunnlagFor(skjæringstidspunkt)?.build(builder)
    }

    internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, sykefraværstilfeller: List<Sykefraværstilfelleeventyr>) {
        val nyttInnslag = sisteInnlag()?.oppdaterHistorikk(aktivitetslogg, sykefraværstilfeller) ?: return
        if (nyttInnslag == sisteInnlag()) return
        historikk.add(0, nyttInnslag)
    }

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
        sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun avvisInngangsvilkår(tidslinjer: List<Utbetalingstidslinje>, subsumsjonObserver: SubsumsjonObserver) =
        sisteInnlag()?.avvis(tidslinjer, subsumsjonObserver) ?: tidslinjer

    internal fun medInntekt(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver) =
        sisteInnlag()!!.medInntekt(organisasjonsnummer, dato, økonomi, regler, subsumsjonObserver)

    internal fun medUtbetalingsopplysninger(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver) =
        sisteInnlag()!!.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, regler, subsumsjonObserver)

    internal fun utenInntekt(dato: LocalDate, økonomi: Økonomi) =
        sisteInnlag()!!.utenInntekt(dato, økonomi)

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

        internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
            vilkårsgrunnlag[skjæringstidspunkt]

        internal fun avvis(tidslinjer: List<Utbetalingstidslinje>, subsumsjonObserver: SubsumsjonObserver): List<Utbetalingstidslinje> {
            val skjæringstidspunktperioder = skjæringstidspunktperioder(vilkårsgrunnlag.values)
            return vilkårsgrunnlag.entries.fold(tidslinjer) { resultat, (skjæringstidspunkt, element) ->
                val periode = checkNotNull(skjæringstidspunktperioder.singleOrNull { it.start == skjæringstidspunkt })
                element.avvis(resultat, periode, subsumsjonObserver)
            }
        }

        internal fun medInntekt(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver
        ) = VilkårsgrunnlagElement.medInntekt(
            vilkårsgrunnlag.values,
            organisasjonsnummer,
            dato,
            økonomi,
            regler,
            subsumsjonObserver
        )

        internal fun medUtbetalingsopplysninger(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver,
        ) = VilkårsgrunnlagElement.medUtbetalingsopplysninger(
            vilkårsgrunnlag.values,
            organisasjonsnummer,
            dato,
            økonomi,
            regler,
            subsumsjonObserver
        )

        internal fun utenInntekt(dato: LocalDate, økonomi: Økonomi): Økonomi {
            return VilkårsgrunnlagElement.utenInntekt(vilkårsgrunnlag.values, dato, økonomi)
        }

        override fun hashCode(): Int {
            return this.vilkårsgrunnlag.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Innslag) return false
            return this.vilkårsgrunnlag == other.vilkårsgrunnlag
        }

        internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, sykefraværstilfeller: List<Sykefraværstilfelleeventyr>): Innslag {
            val gyldigeVilkårsgrunnlag = beholdAktiveSkjæringstidspunkter(sykefraværstilfeller)
            val diff = this.vilkårsgrunnlag.size - gyldigeVilkårsgrunnlag.size
            if (diff > 0) aktivitetslogg.info("Fjernet $diff vilkårsgrunnlagselementer")
            return Innslag(gyldigeVilkårsgrunnlag)
        }

        private fun beholdAktiveSkjæringstidspunkter(sykefraværstilfeller: List<Sykefraværstilfelleeventyr>): Map<LocalDate, VilkårsgrunnlagElement> {
            return vilkårsgrunnlag.filter { (dato, _) -> sykefraværstilfeller.erAktivtSkjæringstidspunkt(dato) }
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
        }
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
        abstract fun validerAnnenSøknadstype(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String): Boolean

        internal abstract fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor)
        internal fun erArbeidsgiverRelevant(organisasjonsnummer: String) = sykepengegrunnlag.erArbeidsgiverRelevant(organisasjonsnummer)

        internal fun build(builder: VedtakFattetBuilder) {
            sykepengegrunnlag.build(builder)
        }
        internal fun inntektskilde() = sykepengegrunnlag.inntektskilde()

        internal fun inntektsdata(skjæringstidspunkt: LocalDate, organisasjonsnummer: String) =
            sykepengegrunnlag.inntektsdata(skjæringstidspunkt, organisasjonsnummer)

        internal open fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode, subsumsjonObserver: SubsumsjonObserver): List<Utbetalingstidslinje> {
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

        internal fun overstyrArbeidsgiveropplysninger(person: Person, hendelse: OverstyrArbeidsgiveropplysninger, subsumsjonObserver: SubsumsjonObserver): Pair<VilkårsgrunnlagElement?, Revurderingseventyr> {
            val sykepengegrunnlag = sykepengegrunnlag.overstyrArbeidsgiveropplysninger(person, hendelse, opptjening, subsumsjonObserver)
            if (sykepengegrunnlag == null) hendelse.info("Endringene hensyntas ikke fordi de er funksjonelt lik sykepengegrunnlaget.")
            val endringsdato = sykepengegrunnlag?.finnEndringsdato(this.sykepengegrunnlag)
            val eventyr = Revurderingseventyr.arbeidsgiveropplysninger(hendelse, skjæringstidspunkt, endringsdato ?: skjæringstidspunkt)
            return sykepengegrunnlag?.let {
                kopierMed(hendelse, sykepengegrunnlag, opptjening, subsumsjonObserver)
            } to eventyr
        }
        internal fun skjønnsmessigFastsettelse(hendelse: SkjønnsmessigFastsettelse, subsumsjonObserver: SubsumsjonObserver): Pair<VilkårsgrunnlagElement?, Revurderingseventyr> {
            val sykepengegrunnlag = sykepengegrunnlag.skjønnsmessigFastsettelse(hendelse, opptjening, subsumsjonObserver)
            if (sykepengegrunnlag == null) hendelse.info("Endringene hensyntas ikke fordi de er funksjonelt lik sykepengegrunnlaget.")
            val endringsdato = sykepengegrunnlag?.finnEndringsdato(this.sykepengegrunnlag)
            val eventyr = Revurderingseventyr.skjønnsmessigFastsettelse(hendelse, skjæringstidspunkt, endringsdato ?: skjæringstidspunkt)
            return sykepengegrunnlag?.let {
                kopierMed(hendelse, sykepengegrunnlag, opptjening, subsumsjonObserver)
            } to eventyr
        }
        protected abstract fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonObserver: SubsumsjonObserver,
            nyttSkjæringstidspunkt: LocalDate? = null
        ): VilkårsgrunnlagElement

        abstract fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonObserver: SubsumsjonObserver
        ): VilkårsgrunnlagElement?

        abstract fun grunnbeløpsregulering(
            hendelse: Grunnbeløpsregulering,
            subsumsjonObserver: SubsumsjonObserver
        ): VilkårsgrunnlagElement?

        internal fun nyeArbeidsgiverInntektsopplysninger(
            person: Person,
            inntektsmelding: Inntektsmelding,
            subsumsjonObserver: SubsumsjonObserver
        ): Pair<VilkårsgrunnlagElement, Revurderingseventyr>?  {
            val sykepengegrunnlag = sykepengegrunnlag.nyeArbeidsgiverInntektsopplysninger(
                person,
                inntektsmelding,
                subsumsjonObserver
            ) ?: return null
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
            subsumsjonObserver: SubsumsjonObserver
        ) = sykepengegrunnlag.medInntekt(organisasjonsnummer, dato, økonomi, regler, subsumsjonObserver)

        private fun medUtbetalingsopplysninger(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver
        ) = sykepengegrunnlag.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, regler, subsumsjonObserver)

        private fun utenInntekt(økonomi: Økonomi): Økonomi {
            return sykepengegrunnlag.utenInntekt(økonomi)
        }

        internal fun er6GBegrenset() = sykepengegrunnlag.er6GBegrenset()

        internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
            sykepengegrunnlag.harNødvendigInntektForVilkårsprøving(organisasjonsnummer)

        internal fun refusjonsopplysninger(organisasjonsnummer: String) =
            sykepengegrunnlag.refusjonsopplysninger(organisasjonsnummer)

        internal fun inntekt(organisasjonsnummer: String): Inntekt? =
            sykepengegrunnlag.inntekt(organisasjonsnummer)

        internal fun overlappendeEllerSenereRefusjonsopplysninger(organisasjonsnummer: String, periode: Periode): List<Refusjonsopplysning> =
            refusjonsopplysninger(organisasjonsnummer).overlappendeEllerSenereRefusjonsopplysninger(periode)

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
            sykepengegrunnlag.byggGodkjenningsbehov(builder)
        }

        internal fun ghosttidslinje(organisasjonsnummer: String, sisteDag: LocalDate) =
            sykepengegrunnlag.ghosttidslinje(organisasjonsnummer, sisteDag)

        internal fun gjenoppliv(hendelse: GjenopplivVilkårsgrunnlag, vilkårsgrunnlagId: UUID, nyttSkjæringstidspunkt: LocalDate?): VilkårsgrunnlagElement? {
            if (this.vilkårsgrunnlagId != vilkårsgrunnlagId) return null
            val gjenopplivetSykepengegrunnlag = this.sykepengegrunnlag.gjenoppliv(hendelse, nyttSkjæringstidspunkt) ?: return null
            return kopierMed(hendelse, gjenopplivetSykepengegrunnlag, this.opptjening, NullObserver, nyttSkjæringstidspunkt)
        }
        internal fun trengerFastsettelseEtterSkjønn() = sykepengegrunnlag.avventerFastsettelseEtterSkjønn()
        internal fun loggInntektsvurdering(hendelse: IAktivitetslogg) = sykepengegrunnlag.loggInntektsvurdering(hendelse)

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
                subsumsjonObserver: SubsumsjonObserver
            ): Økonomi {
                return finnVilkårsgrunnlag(elementer, dato)?.medInntekt(
                    organisasjonsnummer,
                    dato,
                    økonomi,
                    regler,
                    subsumsjonObserver
                ) ?: utenInntekt(elementer, dato, økonomi)
            }

            internal fun medUtbetalingsopplysninger(
                elementer: Collection<VilkårsgrunnlagElement>,
                organisasjonsnummer: String,
                dato: LocalDate,
                økonomi: Økonomi,
                regler: ArbeidsgiverRegler,
                subsumsjonObserver: SubsumsjonObserver
            ): Økonomi {
                val vilkårsgrunnlag = checkNotNull(finnVilkårsgrunnlag(elementer, dato)) {
                    "Fant ikke vilkårsgrunnlag for $dato. Må ha et vilkårsgrunnlag for å legge til utbetalingsopplysninger. Har vilkårsgrunnlag på skjæringstidspunktene ${elementer.map { it.skjæringstidspunkt }}"
                }
                return vilkårsgrunnlag.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, regler, subsumsjonObserver)
            }

            internal fun utenInntekt(
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
        }
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

        override fun validerAnnenSøknadstype(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String) =
            sykepengegrunnlag.validerAnnenSøknadstype(aktivitetslogg, organisasjonsnummer)

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

        override fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode, subsumsjonObserver: SubsumsjonObserver): List<Utbetalingstidslinje> {
            val foreløpigAvvist = sykepengegrunnlag.avvis(tidslinjer, skjæringstidspunktperiode, subsumsjonObserver)
            val begrunnelser = mutableListOf<Begrunnelse>()
            if (medlemskapstatus == Medlemskapsvurdering.Medlemskapstatus.Nei) begrunnelser.add(Begrunnelse.ManglerMedlemskap)
            if (!opptjening.erOppfylt()) begrunnelser.add(Begrunnelse.ManglerOpptjening)
            return Utbetalingstidslinje.avvis(foreløpigAvvist, listOf(skjæringstidspunktperiode), begrunnelser)
        }

        override fun vilkårsgrunnlagtype() = "Spleis"

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonObserver: SubsumsjonObserver
        ) = kopierMed(
            hendelse,
            sykepengegrunnlag.overstyrArbeidsforhold(hendelse, subsumsjonObserver),
            opptjening.overstyrArbeidsforhold(hendelse, subsumsjonObserver),
            subsumsjonObserver,
        )

        override fun grunnbeløpsregulering(
            hendelse: Grunnbeløpsregulering,
            subsumsjonObserver: SubsumsjonObserver
        ): VilkårsgrunnlagElement? {
            val nyttSykepengegrunnlag = sykepengegrunnlag.grunnbeløpsregulering()
            if (nyttSykepengegrunnlag == sykepengegrunnlag) {
                hendelse.info("Grunnbeløpet i sykepengegrunnlaget $skjæringstidspunkt er allerede korrekt.")
                return null
            }
            hendelse.info("Grunnbeløpet i sykepengegrunnlaget $skjæringstidspunkt korrigeres til rett beløp.")
            return kopierMed(hendelse, nyttSykepengegrunnlag, opptjening, subsumsjonObserver)
        }

        override fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonObserver: SubsumsjonObserver,
            nyttSkjæringstidspunkt: LocalDate?
        ): VilkårsgrunnlagElement {
            val sykepengegrunnlagOk = sykepengegrunnlag.valider(hendelse)
            val opptjeningOk = opptjening?.valider(hendelse)
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
    }

    internal class InfotrygdVilkårsgrunnlag(
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID = UUID.randomUUID()
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag, null) {

        override fun validerAnnenSøknadstype(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String) : Boolean {
            aktivitetslogg.funksjonellFeil(`Støtter ikke søknadstypen for forlengelser vilkårsprøvd i Infotrygd`)
            return false
        }

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonObserver: SubsumsjonObserver
        ): Grunnlagsdata? {
            hendelse.funksjonellFeil(RV_VV_11)
            return null
        }

        override fun grunnbeløpsregulering(
            hendelse: Grunnbeløpsregulering,
            subsumsjonObserver: SubsumsjonObserver
        ): Grunnlagsdata? {
            hendelse.funksjonellFeil(RV_VV_11)
            return null
        }

        override fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonObserver: SubsumsjonObserver,
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
    }

    internal companion object {
        internal fun ferdigVilkårsgrunnlagHistorikk(innslag: List<Innslag>) =
            VilkårsgrunnlagHistorikk(innslag.toMutableList())
    }
}
