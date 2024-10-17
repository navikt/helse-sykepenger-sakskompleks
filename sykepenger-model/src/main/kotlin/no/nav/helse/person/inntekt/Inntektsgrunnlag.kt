package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Grunnbeløp
import no.nav.helse.Grunnbeløp.Companion.`2G`
import no.nav.helse.Grunnbeløp.Companion.halvG
import no.nav.helse.Toggle
import no.nav.helse.dto.deserialisering.InntektsgrunnlagInnDto
import no.nav.helse.dto.serialisering.InntektsgrunnlagUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 8-10 ledd 2 punktum 1`
import no.nav.helse.etterlevelse.`§ 8-3 ledd 2 punktum 1`
import no.nav.helse.etterlevelse.`§ 8-51 ledd 2`
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_1
import no.nav.helse.person.builders.UtkastTilVedtakBuilder
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.aktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.berik
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.deaktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.faktaavklarteInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.fastsattÅrsinntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.finn
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.finnEndringsdato
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.forespurtInntektOgRefusjonsopplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.harGjenbrukbareOpplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.harInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.ingenRefusjonsopplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.lagreTidsnæreInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.markerFlereArbeidsgivere
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.måHaRegistrertOpptjeningForArbeidsgivere
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.overstyrInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.refusjonsopplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.sjekkForNyArbeidsgiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.subsummer
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.totalOmregnetÅrsinntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.validerSkjønnsmessigAltEllerIntet
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Begrensning.ER_6G_BEGRENSET
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Begrensning.ER_IKKE_6G_BEGRENSET
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Begrensning.VURDERT_I_INFOTRYGD
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.VilkårsprøvdSkjæringstidspunkt
import no.nav.helse.økonomi.Avviksprosent
import no.nav.helse.økonomi.Inntekt

internal class Inntektsgrunnlag private constructor(
    private val alder: Alder,
    private val skjæringstidspunkt: LocalDate,
    private val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    private val deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
    private val vurdertInfotrygd: Boolean,
    private val sammenligningsgrunnlag: Sammenligningsgrunnlag,
    `6G`: Inntekt? = null
) : Comparable<Inntekt> {

    init {
        arbeidsgiverInntektsopplysninger.validerSkjønnsmessigAltEllerIntet(skjæringstidspunkt)
    }

    private val `6G`: Inntekt = `6G` ?: Grunnbeløp.`6G`.beløp(skjæringstidspunkt, LocalDate.now())
    // sum av alle inntekter foruten skjønnsmessig fastsatt beløp; da brukes inntekten den fastsatte
    private val omregnetÅrsinntekt = arbeidsgiverInntektsopplysninger.totalOmregnetÅrsinntekt(skjæringstidspunkt)
    // summen av alle inntekter
    private val beregningsgrunnlag = arbeidsgiverInntektsopplysninger.fastsattÅrsinntekt(skjæringstidspunkt)
    private val sykepengegrunnlag = beregningsgrunnlag.coerceAtMost(this.`6G`)
    private val begrensning = if (vurdertInfotrygd) VURDERT_I_INFOTRYGD else if (beregningsgrunnlag > this.`6G`) ER_6G_BEGRENSET else ER_IKKE_6G_BEGRENSET

    private val forhøyetInntektskrav = alder.forhøyetInntektskrav(skjæringstidspunkt)
    private val minsteinntekt = (if (forhøyetInntektskrav) `2G` else halvG).minsteinntekt(skjæringstidspunkt)
    private val oppfyllerMinsteinntektskrav = beregningsgrunnlag >= minsteinntekt
    private val avviksprosent = sammenligningsgrunnlag.avviksprosent(omregnetÅrsinntekt)

    internal constructor(
        alder: Alder,
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        skjæringstidspunkt: LocalDate,
        sammenligningsgrunnlag: Sammenligningsgrunnlag,
        subsumsjonslogg: Subsumsjonslogg,
        vurdertInfotrygd: Boolean = false
    ) : this(alder, skjæringstidspunkt, arbeidsgiverInntektsopplysninger, emptyList(), vurdertInfotrygd, sammenligningsgrunnlag) {
        subsumsjonslogg.apply {
            arbeidsgiverInntektsopplysninger.subsummer(this, forrige = emptyList())
            logg(`§ 8-10 ledd 2 punktum 1`(
                erBegrenset = begrensning == ER_6G_BEGRENSET,
                maksimaltSykepengegrunnlagÅrlig = `6G`.årlig,
                skjæringstidspunkt = skjæringstidspunkt,
                beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig
            ))
            subsummerMinsteSykepengegrunnlag(alder, skjæringstidspunkt, this)
        }
    }

    private fun subsummerMinsteSykepengegrunnlag(
        alder: Alder,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        if (alder.forhøyetInntektskrav(skjæringstidspunkt))
            subsumsjonslogg.logg(`§ 8-51 ledd 2`(
                oppfylt = oppfyllerMinsteinntektskrav,
                skjæringstidspunkt = skjæringstidspunkt,
                alderPåSkjæringstidspunkt = alder.alderPåDato(skjæringstidspunkt),
                beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig,
                minimumInntektÅrlig = minsteinntekt.årlig
            ))
        else
            subsumsjonslogg.logg(`§ 8-3 ledd 2 punktum 1`(
                oppfylt = oppfyllerMinsteinntektskrav,
                skjæringstidspunkt = skjæringstidspunkt,
                beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig,
                minimumInntektÅrlig = minsteinntekt.årlig
            ))
    }

    internal companion object {

        internal fun List<Inntektsgrunnlag>.harUlikeGrunnbeløp(): Boolean {
            return map { it.`6G` }.distinct().size > 1
        }

        fun opprett(
            alder: Alder,
            arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
            skjæringstidspunkt: LocalDate,
            sammenligningsgrunnlag: Sammenligningsgrunnlag,
            subsumsjonslogg: Subsumsjonslogg
        ): Inntektsgrunnlag {
            return Inntektsgrunnlag(
                alder,
                arbeidsgiverInntektsopplysninger,
                skjæringstidspunkt,
                sammenligningsgrunnlag,
                subsumsjonslogg
            )
        }

        fun ferdigSykepengegrunnlag(
            alder: Alder,
            skjæringstidspunkt: LocalDate,
            arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
            deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
            vurdertInfotrygd: Boolean,
            sammenligningsgrunnlag: Sammenligningsgrunnlag,
            `6G`: Inntekt? = null
        ): Inntektsgrunnlag {
            return Inntektsgrunnlag(alder, skjæringstidspunkt, arbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold, vurdertInfotrygd, sammenligningsgrunnlag, `6G`)
        }

        fun gjenopprett(alder: Alder, skjæringstidspunkt: LocalDate, dto: InntektsgrunnlagInnDto, inntekter: MutableMap<UUID, Inntektsopplysning>): Inntektsgrunnlag {
            return Inntektsgrunnlag(
                alder = alder,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiverInntektsopplysninger = dto.arbeidsgiverInntektsopplysninger.map { ArbeidsgiverInntektsopplysning.gjenopprett(it, inntekter) },
                deaktiverteArbeidsforhold = dto.deaktiverteArbeidsforhold.map { ArbeidsgiverInntektsopplysning.gjenopprett(it, inntekter) },
                vurdertInfotrygd = dto.vurdertInfotrygd,
                sammenligningsgrunnlag = Sammenligningsgrunnlag.gjenopprett(dto.sammenligningsgrunnlag),
                `6G` = Inntekt.gjenopprett(dto.`6G`)
            )
        }
    }

    internal fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode, periode: Periode, subsumsjonslogg: Subsumsjonslogg): List<Utbetalingstidslinje> {
        val tidslinjeperiode = Utbetalingstidslinje.periode(tidslinjer) ?: return tidslinjer
        if (tidslinjeperiode.starterEtter(skjæringstidspunktperiode) || tidslinjeperiode.endInclusive < skjæringstidspunkt) return tidslinjer

        val avvisningsperiode = skjæringstidspunktperiode.start til minOf(tidslinjeperiode.endInclusive, skjæringstidspunktperiode.endInclusive)
        val avvisteDager = avvisningsperiode.filter { dato ->
            val faktor = if (alder.forhøyetInntektskrav(dato)) `2G` else halvG
            beregningsgrunnlag < faktor.minsteinntekt(skjæringstidspunkt)
        }
        if (avvisteDager.isEmpty()) return tidslinjer
        val (avvisteDagerOver67, avvisteDagerTil67) = avvisteDager.partition { alder.forhøyetInntektskrav(it) }

        if (avvisteDagerOver67.isNotEmpty()) {
            alder.fraOgMedFylte67(
                oppfylt = false,
                utfallFom = avvisteDagerOver67.min(),
                utfallTom = avvisteDagerOver67.max(),
                periodeFom = periode.start,
                periodeTom = periode.endInclusive,
                beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig,
                minimumInntektÅrlig = `2G`.minsteinntekt(avvisteDagerOver67.min()).årlig,
                jurist = subsumsjonslogg
            )
        }
        val dager = listOf(
            Begrunnelse.MinimumInntektOver67 to avvisteDagerOver67.grupperSammenhengendePerioder(),
            Begrunnelse.MinimumInntekt to avvisteDagerTil67.grupperSammenhengendePerioder()
        )
        return dager.fold(tidslinjer) { result, (begrunnelse, perioder) ->
            Utbetalingstidslinje.avvis(result, perioder, listOf(begrunnelse))
        }
    }

    internal fun view() = InntektsgrunnlagView(
        sykepengegrunnlag = sykepengegrunnlag,
        avviksprosent = avviksprosent,
        omregnetÅrsinntekt = omregnetÅrsinntekt,
        beregningsgrunnlag = beregningsgrunnlag,
        `6G` = `6G`,
        begrensning = begrensning,
        vurdertInfotrygd = vurdertInfotrygd,
        minsteinntekt = minsteinntekt,
        oppfyllerMinsteinntektskrav = oppfyllerMinsteinntektskrav,
        arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger,
        deaktiverteArbeidsforhold = deaktiverteArbeidsforhold.map { it.orgnummer }
    )

    internal fun valider(aktivitetslogg: IAktivitetslogg): Boolean {
        if (oppfyllerMinsteinntektskrav) aktivitetslogg.info("Krav til minste sykepengegrunnlag er oppfylt")
        else aktivitetslogg.varsel(RV_SV_1)
        return oppfyllerMinsteinntektskrav && !aktivitetslogg.harFunksjonelleFeilEllerVerre()
    }


    internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.harInntekt(organisasjonsnummer) || deaktiverteArbeidsforhold.harInntekt(organisasjonsnummer)

    internal fun sjekkForNyArbeidsgiver(aktivitetslogg: IAktivitetslogg, opptjening: Opptjening?, orgnummer: String) {
        if (opptjening == null) return
        arbeidsgiverInntektsopplysninger.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, orgnummer)
    }

    internal fun måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg: IAktivitetslogg, opptjening: Opptjening?) {
        if (opptjening == null) return
        arbeidsgiverInntektsopplysninger.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjening)
    }

    internal fun markerFlereArbeidsgivere(aktivitetslogg: IAktivitetslogg) {
        arbeidsgiverInntektsopplysninger.markerFlereArbeidsgivere(aktivitetslogg)
    }

    internal fun aktiver(orgnummer: String, forklaring: String, subsumsjonslogg: Subsumsjonslogg) =
        deaktiverteArbeidsforhold.aktiver(arbeidsgiverInntektsopplysninger, orgnummer, forklaring, subsumsjonslogg)
            .let { (deaktiverte, aktiverte) ->
                kopierSykepengegrunnlag(
                    arbeidsgiverInntektsopplysninger = aktiverte,
                    deaktiverteArbeidsforhold = deaktiverte
                )
            }

    internal fun deaktiver(orgnummer: String, forklaring: String, subsumsjonslogg: Subsumsjonslogg) =
        arbeidsgiverInntektsopplysninger.deaktiver(deaktiverteArbeidsforhold, orgnummer, forklaring, subsumsjonslogg)
            .let { (aktiverte, deaktiverte) ->
                kopierSykepengegrunnlag(
                    arbeidsgiverInntektsopplysninger = aktiverte,
                    deaktiverteArbeidsforhold = deaktiverte
                )
            }

    internal fun overstyrArbeidsforhold(hendelse: OverstyrArbeidsforhold, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag {
        return hendelse.overstyr(this, subsumsjonslogg)
    }

    internal fun overstyrArbeidsgiveropplysninger(person: Person, hendelse: OverstyrArbeidsgiveropplysninger, opptjening: Opptjening?, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag {
        val builder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, arbeidsgiverInntektsopplysninger, opptjening, subsumsjonslogg)
        hendelse.overstyr(builder)
        val (resultat, harTilkommetInntekter) = builder.resultat(Toggle.TilkommenArbeidsgiver.enabled)
        arbeidsgiverInntektsopplysninger.forEach { it.arbeidsgiveropplysningerKorrigert(person, hendelse) }
        return kopierSykepengegrunnlagOgValiderMinsteinntekt(resultat, deaktiverteArbeidsforhold, subsumsjonslogg)
    }

    internal fun gjenoppliv(hendelse: GjenopplivVilkårsgrunnlag, nyttSkjæringstidspunkt: LocalDate?): Inntektsgrunnlag? {
        val skjæringstidspunkt = nyttSkjæringstidspunkt ?: this.skjæringstidspunkt
        val nyeArbeidsgiverInntektsopplysninger = hendelse.arbeidsgiverinntektsopplysninger(skjæringstidspunkt)
        if (arbeidsgiverInntektsopplysninger.isNotEmpty() && nyeArbeidsgiverInntektsopplysninger.isNotEmpty()) {
            hendelse.info("Kan ikke gjenopplive sykepengegrunnlag med nye inntektsopplysninger hvor det allerede foreligger inntektsopplysninger.")
            return null
        }

        val gjenopplivetArbeidsgiverInntektsopplysninger = nyeArbeidsgiverInntektsopplysninger.takeUnless { it.isEmpty() } ?: arbeidsgiverInntektsopplysninger.map { it.gjenoppliv(this.skjæringstidspunkt, skjæringstidspunkt) }

        if (gjenopplivetArbeidsgiverInntektsopplysninger.isEmpty()) {
            hendelse.info("Kan ikke gjenopplive sykepengegrunnlag uten inntektsopplysninger.")
            return null
        }
        return kopierSykepengegrunnlag(gjenopplivetArbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold, skjæringstidspunkt)
    }

    internal fun skjønnsmessigFastsettelse(hendelse: SkjønnsmessigFastsettelse, opptjening: Opptjening?, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag {
        val builder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, arbeidsgiverInntektsopplysninger, opptjening, subsumsjonslogg)
        hendelse.overstyr(builder)
        val (resultat, harTilkommetInntekter) = builder.resultat(kandidatForTilkommenInntekt = false)
        return kopierSykepengegrunnlagOgValiderMinsteinntekt(resultat, deaktiverteArbeidsforhold, subsumsjonslogg)
    }

    internal fun refusjonsopplysninger(organisasjonsnummer: String): Refusjonsopplysninger{
        val aktive = arbeidsgiverInntektsopplysninger.refusjonsopplysninger(organisasjonsnummer)
        val deaktiverte = deaktiverteArbeidsforhold.refusjonsopplysninger(organisasjonsnummer)
        return if (!aktive.erTom) aktive else deaktiverte
    }

    fun tilkomneInntekterFraSøknaden(søknad: Søknad, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag {
        val builder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, arbeidsgiverInntektsopplysninger, null, subsumsjonslogg)
        søknad.nyeInntekter(builder, skjæringstidspunkt)
        val (resultat, harTilkommetInntekter) = builder.resultat(kandidatForTilkommenInntekt = true)
        if (harTilkommetInntekter) {
            søknad.info("Legger til inntekter fra søknaden i sykepengegrunnlaget")
        }
        return kopierSykepengegrunnlagOgValiderMinsteinntekt(resultat, deaktiverteArbeidsforhold, subsumsjonslogg)
    }


    internal fun nyeArbeidsgiverInntektsopplysninger(
        person: Person,
        inntektsmelding: Inntektsmelding,
        subsumsjonslogg: Subsumsjonslogg
    ): Inntektsgrunnlag {
        val aktiveBuilder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, arbeidsgiverInntektsopplysninger, null, subsumsjonslogg)
        inntektsmelding.nyeArbeidsgiverInntektsopplysninger(aktiveBuilder, skjæringstidspunkt)
        val (aktiveResultat, harTilkommetInntekter) = aktiveBuilder.resultat(Toggle.TilkommenArbeidsgiver.enabled)
        if (harTilkommetInntekter) {
            inntektsmelding.varsel(Varselkode.RV_SV_5) // TODO: Burde ha eget varsel så det ikke forvekles med tilkommen inntekt fra søknaden
        }

        val deaktiverteBuilder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, deaktiverteArbeidsforhold, null, subsumsjonslogg)
        inntektsmelding.nyeArbeidsgiverInntektsopplysninger(deaktiverteBuilder, skjæringstidspunkt)
        val (deaktiverteResultat, _) = deaktiverteBuilder.resultat(Toggle.TilkommenArbeidsgiver.enabled)

        (arbeidsgiverInntektsopplysninger + deaktiverteArbeidsforhold)
            .finn(inntektsmelding.organisasjonsnummer())
            ?.arbeidsgiveropplysningerKorrigert(person, inntektsmelding)

        return kopierSykepengegrunnlagOgValiderMinsteinntekt(aktiveResultat, deaktiverteResultat, subsumsjonslogg)
    }

    private fun kopierSykepengegrunnlagOgValiderMinsteinntekt(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
        subsumsjonslogg: Subsumsjonslogg
    ): Inntektsgrunnlag {
        return kopierSykepengegrunnlag(arbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold).apply {
           subsummerMinsteSykepengegrunnlag(alder, skjæringstidspunkt, subsumsjonslogg)
        }
    }

    private fun kopierSykepengegrunnlag(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
        nyttSkjæringstidspunkt: LocalDate = skjæringstidspunkt
    ) = Inntektsgrunnlag(
            alder = alder,
            skjæringstidspunkt = nyttSkjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger,
            deaktiverteArbeidsforhold = deaktiverteArbeidsforhold,
            vurdertInfotrygd = vurdertInfotrygd,
            sammenligningsgrunnlag = sammenligningsgrunnlag
        )

    internal fun grunnbeløpsregulering() = kopierSykepengegrunnlag(
        arbeidsgiverInntektsopplysninger,
        deaktiverteArbeidsforhold
    )

    internal fun inntektskilde() = when {
        arbeidsgiverInntektsopplysninger.size > 1 -> UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE
        else -> UtbetalingInntektskilde.EN_ARBEIDSGIVER
    }

    internal fun erArbeidsgiverRelevant(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.any { it.gjelder(organisasjonsnummer) } || sammenligningsgrunnlag.erRelevant(organisasjonsnummer)

    internal fun berik(builder: UtkastTilVedtakBuilder) {
        builder.sykepengegrunnlag(
            sykepengegrunnlag = sykepengegrunnlag,
            beregningsgrunnlag = beregningsgrunnlag,
            totalOmregnetÅrsinntekt = omregnetÅrsinntekt,
            seksG = `6G`,
            inngangsvilkårFraInfotrygd = vurdertInfotrygd
        )
        arbeidsgiverInntektsopplysninger.berik(builder)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Inntektsgrunnlag) return false
        return sykepengegrunnlag == other.sykepengegrunnlag
                 && arbeidsgiverInntektsopplysninger == other.arbeidsgiverInntektsopplysninger
                 && beregningsgrunnlag == other.beregningsgrunnlag
                 && begrensning == other.begrensning
                 && `6G` == other.`6G`
                 && deaktiverteArbeidsforhold == other.deaktiverteArbeidsforhold
    }

    override fun hashCode(): Int {
        var result = sykepengegrunnlag.hashCode()
        result = 31 * result + arbeidsgiverInntektsopplysninger.hashCode()
        result = 31 * result + beregningsgrunnlag.hashCode()
        result = 31 * result + begrensning.hashCode()
        result = 31 * result + deaktiverteArbeidsforhold.hashCode()
        return result
    }

    override fun compareTo(other: Inntekt) = this.sykepengegrunnlag.compareTo(other)

    internal fun er6GBegrenset() = begrensning == ER_6G_BEGRENSET

    internal fun finnEndringsdato(other: Inntektsgrunnlag): LocalDate {
        check(this.skjæringstidspunkt == other.skjæringstidspunkt) {
            "Skal bare sammenlikne med samme skjæringstidspunkt"
        }
        return arbeidsgiverInntektsopplysninger.finnEndringsdato(this.skjæringstidspunkt, other.arbeidsgiverInntektsopplysninger)
    }

    fun harGjenbrukbareOpplysninger(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.harGjenbrukbareOpplysninger(organisasjonsnummer)

    fun lagreTidsnæreInntekter(skjæringstidspunkt: LocalDate, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg, nyArbeidsgiverperiode: Boolean) {
        arbeidsgiverInntektsopplysninger.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, hendelse, nyArbeidsgiverperiode)
    }

    enum class Begrensning {
        ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD
    }

    internal class ArbeidsgiverInntektsopplysningerOverstyringer(
        private val skjæringstidspunkt: LocalDate,
        private val opprinneligArbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        private val opptjening: Opptjening?,
        private val subsumsjonslogg: Subsumsjonslogg
    ) {
        private val nyeInntektsopplysninger = mutableListOf<ArbeidsgiverInntektsopplysning>()

        internal fun leggTilInntekt(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning) {
            nyeInntektsopplysninger.add(arbeidsgiverInntektsopplysning)
        }

        internal fun ingenRefusjonsopplysninger(organisasjonsnummer: String) = opprinneligArbeidsgiverInntektsopplysninger.ingenRefusjonsopplysninger(organisasjonsnummer)

        internal fun resultat(kandidatForTilkommenInntekt: Boolean = false): Pair<List<ArbeidsgiverInntektsopplysning>, Boolean> {
            return opprinneligArbeidsgiverInntektsopplysninger.overstyrInntekter(skjæringstidspunkt, opptjening, nyeInntektsopplysninger, subsumsjonslogg, kandidatForTilkommenInntekt)
        }
    }

    internal fun forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer: String, periode: Periode) =
        arbeidsgiverInntektsopplysninger.forespurtInntektOgRefusjonsopplysninger(skjæringstidspunkt, organisasjonsnummer, periode)

    internal fun dto() = InntektsgrunnlagUtDto(
        arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.dto() },
        deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.dto() },
        vurdertInfotrygd = this.vurdertInfotrygd,
        sammenligningsgrunnlag = this.sammenligningsgrunnlag.dto(),
        `6G` = this.`6G`.dto(),
        sykepengegrunnlag = this.sykepengegrunnlag.dto(),
        totalOmregnetÅrsinntekt = this.omregnetÅrsinntekt.dto(),
        beregningsgrunnlag = this.beregningsgrunnlag.dto(),
        er6GBegrenset = beregningsgrunnlag > this.`6G`,
        forhøyetInntektskrav = this.forhøyetInntektskrav,
        minsteinntekt = this.minsteinntekt.dto(),
        oppfyllerMinsteinntektskrav = this.oppfyllerMinsteinntektskrav
    )

    internal fun faktaavklarteInntekter() = VilkårsprøvdSkjæringstidspunkt(
        skjæringstidspunkt = skjæringstidspunkt,
        `6G` = `6G`,
        inntekter = arbeidsgiverInntektsopplysninger.faktaavklarteInntekter(),
        deaktiverteInntekter = deaktiverteArbeidsforhold.faktaavklarteInntekter()
    )
}

internal data class InntektsgrunnlagView(
    val sykepengegrunnlag: Inntekt,
    val avviksprosent: Avviksprosent,
    val omregnetÅrsinntekt: Inntekt,
    val beregningsgrunnlag: Inntekt,
    val `6G`: Inntekt,
    val begrensning: Inntektsgrunnlag.Begrensning,
    val vurdertInfotrygd: Boolean,
    val minsteinntekt: Inntekt,
    val oppfyllerMinsteinntektskrav: Boolean,
    val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    val deaktiverteArbeidsforhold: List<String>
)
