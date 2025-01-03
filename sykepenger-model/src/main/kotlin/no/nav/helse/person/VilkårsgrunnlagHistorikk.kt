package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.dto.MedlemskapsvurderingDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlagInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlagInnslagInnDto
import no.nav.helse.dto.deserialisering.VilkårsgrunnlaghistorikkInnDto
import no.nav.helse.dto.serialisering.InntektsgrunnlagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagInnslagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlaghistorikkUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.KorrigertInntektOgRefusjon
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.til
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement.Companion.skjæringstidspunktperioder
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.builders.UtkastTilVedtakBuilder
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Companion.harUlikeGrunnbeløp
import no.nav.helse.person.inntekt.InntektsgrunnlagView
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.person.inntekt.NyInntektUnderveis
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal class VilkårsgrunnlagHistorikk private constructor(private val historikk: MutableList<Innslag>) {

    internal constructor() : this(mutableListOf())

    private fun sisteInnlag() = historikk.firstOrNull()

    private fun forrigeInnslag() = historikk.elementAtOrNull(1)

    internal fun view() = VilkårsgrunnlagHistorikkView(innslag = historikk.map { it.view() })

    internal fun lagre(vararg vilkårsgrunnlag: VilkårsgrunnlagElement) {
        if (vilkårsgrunnlag.isEmpty()) return
        val siste = sisteInnlag()
        val nytt = Innslag(siste, vilkårsgrunnlag.toList())
        if (nytt == siste) return
        historikk.add(0, nytt)
    }

    internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, sykefraværstilfeller: Set<LocalDate>) {
        val nyttInnslag = sisteInnlag()?.oppdaterHistorikk(aktivitetslogg, sykefraværstilfeller) ?: return
        if (nyttInnslag == sisteInnlag()) return
        historikk.add(0, nyttInnslag)
    }

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
        sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun avvisInngangsvilkår(tidslinjer: List<Utbetalingstidslinje>, periode: Periode, subsumsjonslogg: Subsumsjonslogg) =
        sisteInnlag()?.avvis(tidslinjer, periode, subsumsjonslogg) ?: tidslinjer

    internal fun blitt6GBegrensetSidenSist(skjæringstidspunkt: LocalDate): Boolean {
        if (sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)?.er6GBegrenset() == false) return false
        return forrigeInnslag()?.vilkårsgrunnlagFor(skjæringstidspunkt)?.er6GBegrenset() == false
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

        internal constructor(other: Innslag?, elementer: List<VilkårsgrunnlagElement>) : this(other?.vilkårsgrunnlag?.toMap() ?: emptyMap()) {
            elementer.forEach { it.add(this) }
        }

        internal fun view() = VilkårsgrunnlagInnslagView(vilkårsgrunnlag = vilkårsgrunnlag.map { it.value.view() })

        internal fun add(skjæringstidspunkt: LocalDate, vilkårsgrunnlagElement: VilkårsgrunnlagElement) {
            vilkårsgrunnlag[skjæringstidspunkt] = vilkårsgrunnlagElement
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

    internal sealed class VilkårsgrunnlagElement(
        val vilkårsgrunnlagId: UUID,
        val skjæringstidspunkt: LocalDate,
        val inntektsgrunnlag: Inntektsgrunnlag,
        val opptjening: Opptjening?
    ) : Aktivitetskontekst {
        internal fun add(innslag: Innslag) {
            innslag.add(skjæringstidspunkt, this)
        }

        internal open fun valider(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String) = true

        internal fun view() = VilkårsgrunnlagView(
            vilkårsgrunnlagId = vilkårsgrunnlagId,
            skjæringstidspunkt = skjæringstidspunkt,
            vurdertOk = when (this) {
                is Grunnlagsdata -> vurdertOk
                is InfotrygdVilkårsgrunnlag -> true
            },
            type = when (this) {
                is Grunnlagsdata -> VilkårsgrunnlagView.VilkårsgrunnlagTypeView.SPLEIS
                is InfotrygdVilkårsgrunnlag -> VilkårsgrunnlagView.VilkårsgrunnlagTypeView.INFOTRYGD
            },
            meldingsreferanseId = when (this) {
                is Grunnlagsdata -> this.meldingsreferanseId
                is InfotrygdVilkårsgrunnlag -> null
            },
            inntektsgrunnlag = inntektsgrunnlag.view(),
            opptjening = opptjening?.view()
        )

        internal fun erArbeidsgiverRelevant(organisasjonsnummer: String) = inntektsgrunnlag.erArbeidsgiverRelevant(organisasjonsnummer)

        internal fun inntektskilde() = inntektsgrunnlag.inntektskilde()

        internal fun fastsattInntekt(organisasjonsnummer: String) =
            inntektsgrunnlag.fastsattInntekt(organisasjonsnummer)

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

        internal fun overstyrArbeidsgiveropplysninger(person: Person, hendelse: OverstyrArbeidsgiveropplysninger, aktivitetslogg: IAktivitetslogg, subsumsjonslogg: Subsumsjonslogg): Pair<VilkårsgrunnlagElement, Revurderingseventyr>? {
            val sykepengegrunnlag = inntektsgrunnlag.overstyrArbeidsgiveropplysninger(person, hendelse, opptjening, subsumsjonslogg)
            val endringsdato = sykepengegrunnlag.finnEndringsdato(this.inntektsgrunnlag) ?: return null
            val eventyr = Revurderingseventyr.arbeidsgiveropplysninger(hendelse, skjæringstidspunkt, endringsdato)
            return kopierMed(aktivitetslogg, sykepengegrunnlag, opptjening, subsumsjonslogg) to eventyr
        }

        internal fun skjønnsmessigFastsettelse(hendelse: SkjønnsmessigFastsettelse, aktivitetslogg: IAktivitetslogg, subsumsjonslogg: Subsumsjonslogg): Pair<VilkårsgrunnlagElement, Revurderingseventyr>? {
            val sykepengegrunnlag = inntektsgrunnlag.skjønnsmessigFastsettelse(hendelse, opptjening, subsumsjonslogg)
            val endringsdato = sykepengegrunnlag.finnEndringsdato(this.inntektsgrunnlag) ?: return null
            val eventyr = Revurderingseventyr.skjønnsmessigFastsettelse(hendelse, skjæringstidspunkt, endringsdato)
            return kopierMed(aktivitetslogg, sykepengegrunnlag, opptjening, subsumsjonslogg) to eventyr
        }

        protected abstract fun kopierMed(
            aktivitetslogg: IAktivitetslogg,
            inntektsgrunnlag: Inntektsgrunnlag,
            opptjening: Opptjening?,
            subsumsjonslogg: Subsumsjonslogg,
            nyttSkjæringstidspunkt: LocalDate? = null
        ): VilkårsgrunnlagElement

        abstract fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            aktivitetslogg: IAktivitetslogg,
            subsumsjonslogg: Subsumsjonslogg
        ): VilkårsgrunnlagElement

        internal fun grunnbeløpsregulering(
            hendelse: Grunnbeløpsregulering,
            aktivitetslogg: IAktivitetslogg,
            subsumsjonslogg: Subsumsjonslogg
        ): VilkårsgrunnlagElement? {
            val nyttSykepengegrunnlag = inntektsgrunnlag.grunnbeløpsregulering()
            if (nyttSykepengegrunnlag == inntektsgrunnlag) {
                aktivitetslogg.info("Grunnbeløpet i sykepengegrunnlaget $skjæringstidspunkt er allerede korrekt.")
                return null
            }
            aktivitetslogg.info("Grunnbeløpet i sykepengegrunnlaget $skjæringstidspunkt korrigeres til rett beløp.")
            return kopierMed(aktivitetslogg, nyttSykepengegrunnlag, opptjening, subsumsjonslogg)
        }

        internal fun tilkomneInntekterFraSøknaden(søknad: IAktivitetslogg, periode: Periode, nyeInntekter: List<NyInntektUnderveis>, subsumsjonslogg: Subsumsjonslogg): VilkårsgrunnlagElement? {
            val sykepengegrunnlag = inntektsgrunnlag.tilkomneInntekterFraSøknaden(søknad, periode, nyeInntekter, subsumsjonslogg) ?: return null
            return kopierMed(søknad, sykepengegrunnlag, opptjening, EmptyLog)
        }

        internal fun nyeArbeidsgiverInntektsopplysninger(
            person: Person,
            korrigertInntektsmelding: KorrigertInntektOgRefusjon,
            aktivitetslogg: IAktivitetslogg,
            subsumsjonslogg: Subsumsjonslogg
        ): Pair<VilkårsgrunnlagElement, Revurderingseventyr>? {
            val sykepengegrunnlag = inntektsgrunnlag.nyeArbeidsgiverInntektsopplysninger(
                person,
                korrigertInntektsmelding,
                subsumsjonslogg
            )
            val endringsdato = sykepengegrunnlag.finnEndringsdato(this.inntektsgrunnlag) ?: return null
            val eventyr = Revurderingseventyr.korrigertInntektsmeldingInntektsopplysninger(korrigertInntektsmelding.hendelse, skjæringstidspunkt, endringsdato)
            return kopierMed(aktivitetslogg, sykepengegrunnlag, opptjening, EmptyLog) to eventyr
        }

        protected abstract fun vilkårsgrunnlagtype(): String

        internal fun er6GBegrenset() = inntektsgrunnlag.er6GBegrenset()

        internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
            inntektsgrunnlag.harNødvendigInntektForVilkårsprøving(organisasjonsnummer)

        internal fun harTilkommendeInntekter() = inntektsgrunnlag.harTilkommendeInntekter()

        internal fun harGjenbrukbarInntekt(organisasjonsnummer: String) =
            inntektsgrunnlag.harGjenbrukbarInntekt(organisasjonsnummer)

        internal fun lagreTidsnæreInntekter(
            skjæringstidspunkt: LocalDate,
            arbeidsgiver: Arbeidsgiver,
            aktivitetslogg: IAktivitetslogg,
            nyArbeidsgiverperiode: Boolean
        ) {
            inntektsgrunnlag.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, aktivitetslogg, nyArbeidsgiverperiode)
        }

        internal fun berik(builder: UtkastTilVedtakBuilder) {
            builder.vilkårsgrunnlagId(vilkårsgrunnlagId)
            inntektsgrunnlag.berik(builder)
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

            internal fun List<VilkårsgrunnlagElement>.harUlikeGrunnbeløp(): Boolean {
                return map { it.inntektsgrunnlag }.harUlikeGrunnbeløp()
            }

            internal fun gjenopprett(alder: Alder, dto: VilkårsgrunnlagInnDto, inntekter: MutableMap<UUID, Inntektsopplysning>): VilkårsgrunnlagElement {
                return when (dto) {
                    is VilkårsgrunnlagInnDto.Infotrygd -> InfotrygdVilkårsgrunnlag.gjenopprett(alder, dto, inntekter)
                    is VilkårsgrunnlagInnDto.Spleis -> Grunnlagsdata.gjenopprett(alder, dto, inntekter)
                }
            }
        }

        internal fun dto(): VilkårsgrunnlagUtDto =
            dto(vilkårsgrunnlagId, skjæringstidspunkt, inntektsgrunnlag.dto())

        protected abstract fun dto(
            vilkårsgrunnlagId: UUID,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: InntektsgrunnlagUtDto
        ): VilkårsgrunnlagUtDto

        fun faktaavklarteInntekter() = inntektsgrunnlag.faktaavklarteInntekter()
    }

    internal class Grunnlagsdata(
        skjæringstidspunkt: LocalDate,
        inntektsgrunnlag: Inntektsgrunnlag,
        opptjening: Opptjening,
        val medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
        val vurdertOk: Boolean,
        val meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, inntektsgrunnlag, opptjening) {
        internal fun validerFørstegangsvurdering(aktivitetslogg: IAktivitetslogg) {
            inntektsgrunnlag.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjening)
            inntektsgrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
        }

        override fun valider(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: String): Boolean {
            inntektsgrunnlag.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, organisasjonsnummer)
            return !aktivitetslogg.harFunksjonelleFeilEllerVerre()
        }

        override fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode, periode: Periode, subsumsjonslogg: Subsumsjonslogg): List<Utbetalingstidslinje> {
            val foreløpigAvvist = inntektsgrunnlag.avvis(tidslinjer, skjæringstidspunktperiode, periode, subsumsjonslogg)
            val begrunnelser = mutableListOf<Begrunnelse>()
            if (medlemskapstatus == Medlemskapsvurdering.Medlemskapstatus.Nei) begrunnelser.add(Begrunnelse.ManglerMedlemskap)
            if (!opptjening!!.harTilstrekkeligAntallOpptjeningsdager()) begrunnelser.add(Begrunnelse.ManglerOpptjening)
            return Utbetalingstidslinje.avvis(foreløpigAvvist, listOf(skjæringstidspunktperiode), begrunnelser)
        }

        override fun vilkårsgrunnlagtype() = "Spleis"

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            aktivitetslogg: IAktivitetslogg,
            subsumsjonslogg: Subsumsjonslogg
        ) = kopierMed(
            aktivitetslogg = aktivitetslogg,
            inntektsgrunnlag = inntektsgrunnlag.overstyrArbeidsforhold(hendelse, subsumsjonslogg),
            opptjening = opptjening!!.overstyrArbeidsforhold(hendelse).also {
                subsumsjonslogg.logg(it.subsumsjon)
            },
            subsumsjonslogg = subsumsjonslogg,
        )

        override fun kopierMed(
            aktivitetslogg: IAktivitetslogg,
            inntektsgrunnlag: Inntektsgrunnlag,
            opptjening: Opptjening?,
            subsumsjonslogg: Subsumsjonslogg,
            nyttSkjæringstidspunkt: LocalDate?
        ): VilkårsgrunnlagElement {
            val sykepengegrunnlagOk = inntektsgrunnlag.valider(aktivitetslogg)
            val opptjeningOk = opptjening?.validerOpptjeningsdager(aktivitetslogg)
            return Grunnlagsdata(
                skjæringstidspunkt = nyttSkjæringstidspunkt ?: skjæringstidspunkt,
                inntektsgrunnlag = inntektsgrunnlag,
                opptjening = opptjening ?: this.opptjening!!,
                medlemskapstatus = medlemskapstatus,
                vurdertOk = vurdertOk && sykepengegrunnlagOk && (opptjeningOk ?: true),
                meldingsreferanseId = meldingsreferanseId,
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }

        override fun dto(
            vilkårsgrunnlagId: UUID,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: InntektsgrunnlagUtDto
        ) = VilkårsgrunnlagUtDto.Spleis(
            vilkårsgrunnlagId = vilkårsgrunnlagId,
            skjæringstidspunkt = skjæringstidspunkt,
            inntektsgrunnlag = sykepengegrunnlag,
            opptjening = this.opptjening!!.dto(),
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
                    inntektsgrunnlag = Inntektsgrunnlag.gjenopprett(alder, dto.skjæringstidspunkt, dto.inntektsgrunnlag, inntekter),
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
        inntektsgrunnlag: Inntektsgrunnlag,
        vilkårsgrunnlagId: UUID = UUID.randomUUID()
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, inntektsgrunnlag, null) {

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            aktivitetslogg: IAktivitetslogg,
            subsumsjonslogg: Subsumsjonslogg
        ) = kopierMed(
            aktivitetslogg = aktivitetslogg,
            inntektsgrunnlag = inntektsgrunnlag.overstyrArbeidsforhold(hendelse, subsumsjonslogg),
            opptjening = null,
            subsumsjonslogg = subsumsjonslogg
        )

        override fun kopierMed(
            aktivitetslogg: IAktivitetslogg,
            inntektsgrunnlag: Inntektsgrunnlag,
            opptjening: Opptjening?,
            subsumsjonslogg: Subsumsjonslogg,
            nyttSkjæringstidspunkt: LocalDate?
        ): InfotrygdVilkårsgrunnlag {
            return InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = nyttSkjæringstidspunkt ?: skjæringstidspunkt,
                inntektsgrunnlag = inntektsgrunnlag,
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }

        override fun vilkårsgrunnlagtype() = "Infotrygd"

        override fun equals(other: Any?): Boolean {
            if (other !is InfotrygdVilkårsgrunnlag) return false
            return skjæringstidspunkt == other.skjæringstidspunkt && inntektsgrunnlag == other.inntektsgrunnlag
        }

        override fun hashCode(): Int {
            var result = skjæringstidspunkt.hashCode()
            result = 31 * result + inntektsgrunnlag.hashCode()
            return result
        }

        override fun dto(
            vilkårsgrunnlagId: UUID,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: InntektsgrunnlagUtDto
        ) = VilkårsgrunnlagUtDto.Infotrygd(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag)

        internal companion object {
            fun gjenopprett(alder: Alder, dto: VilkårsgrunnlagInnDto.Infotrygd, inntekter: MutableMap<UUID, Inntektsopplysning>): InfotrygdVilkårsgrunnlag {
                return InfotrygdVilkårsgrunnlag(
                    skjæringstidspunkt = dto.skjæringstidspunkt,
                    inntektsgrunnlag = Inntektsgrunnlag.gjenopprett(alder, dto.skjæringstidspunkt, dto.inntektsgrunnlag, inntekter),
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

internal data class VilkårsgrunnlagHistorikkView(val innslag: List<VilkårsgrunnlagInnslagView>)
internal data class VilkårsgrunnlagInnslagView(val vilkårsgrunnlag: List<VilkårsgrunnlagView>)
internal data class VilkårsgrunnlagView(
    val vilkårsgrunnlagId: UUID,
    val skjæringstidspunkt: LocalDate,
    val vurdertOk: Boolean,
    val type: VilkårsgrunnlagTypeView,
    val meldingsreferanseId: UUID?,
    val inntektsgrunnlag: InntektsgrunnlagView,
    val opptjening: OpptjeningView?
) {
    enum class VilkårsgrunnlagTypeView { INFOTRYGD, SPLEIS }
}
