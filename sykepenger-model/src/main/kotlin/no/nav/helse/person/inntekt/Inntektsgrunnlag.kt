package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Grunnbeløp
import no.nav.helse.Grunnbeløp.Companion.`2G`
import no.nav.helse.Grunnbeløp.Companion.halvG
import no.nav.helse.dto.deserialisering.InntektsgrunnlagInnDto
import no.nav.helse.dto.serialisering.InntektsgrunnlagUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 8-10 ledd 2 punktum 1`
import no.nav.helse.etterlevelse.`§ 8-3 ledd 2 punktum 1`
import no.nav.helse.etterlevelse.`§ 8-51 ledd 2`
import no.nav.helse.hendelser.KorrigertInntektOgRefusjon
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.til
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_1
import no.nav.helse.person.builders.UtkastTilVedtakBuilder
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.aktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.berik
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.deaktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.faktaavklarteInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.fastsattÅrsinntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.finn
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.finnEndringsdato
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.fastsattInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.harGjenbrukbarInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.harInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.lagreTidsnæreInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.markerFlereArbeidsgivere
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.måHaRegistrertOpptjeningForArbeidsgivere
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.overstyrInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.sjekkForNyArbeidsgiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.subsummer
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.totalOmregnetÅrsinntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.validerSkjønnsmessigAltEllerIntet
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Begrensning.ER_6G_BEGRENSET
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Begrensning.ER_IKKE_6G_BEGRENSET
import no.nav.helse.person.inntekt.Inntektsgrunnlag.Begrensning.VURDERT_I_INFOTRYGD
import no.nav.helse.person.inntekt.NyInntektUnderveis.Companion.finnEndringsdato
import no.nav.helse.person.inntekt.NyInntektUnderveis.Companion.merge
import no.nav.helse.person.inntekt.NyInntektUnderveis.Companion.overstyr
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.VilkårsprøvdSkjæringstidspunkt
import no.nav.helse.økonomi.Inntekt

internal class Inntektsgrunnlag private constructor(
    private val alder: Alder,
    private val skjæringstidspunkt: LocalDate,
    private val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    private val deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
    private val tilkommendeInntekter: List<NyInntektUnderveis>,
    private val vurdertInfotrygd: Boolean,
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

    internal constructor(
        alder: Alder,
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg,
        vurdertInfotrygd: Boolean = false
    ) : this(alder, skjæringstidspunkt, arbeidsgiverInntektsopplysninger, emptyList(), emptyList(), vurdertInfotrygd) {
        subsumsjonslogg.apply {
            arbeidsgiverInntektsopplysninger.subsummer(this, forrige = emptyList())
            logg(
                `§ 8-10 ledd 2 punktum 1`(
                    erBegrenset = begrensning == ER_6G_BEGRENSET,
                    maksimaltSykepengegrunnlagÅrlig = `6G`.årlig,
                    skjæringstidspunkt = skjæringstidspunkt,
                    beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig
                )
            )
            subsummerMinsteSykepengegrunnlag(alder, skjæringstidspunkt, this)
        }
    }

    private fun subsummerMinsteSykepengegrunnlag(
        alder: Alder,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        if (alder.forhøyetInntektskrav(skjæringstidspunkt))
            subsumsjonslogg.logg(
                `§ 8-51 ledd 2`(
                    oppfylt = oppfyllerMinsteinntektskrav,
                    skjæringstidspunkt = skjæringstidspunkt,
                    alderPåSkjæringstidspunkt = alder.alderPåDato(skjæringstidspunkt),
                    beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig,
                    minimumInntektÅrlig = minsteinntekt.årlig
                )
            )
        else
            subsumsjonslogg.logg(
                `§ 8-3 ledd 2 punktum 1`(
                    oppfylt = oppfyllerMinsteinntektskrav,
                    skjæringstidspunkt = skjæringstidspunkt,
                    beregningsgrunnlagÅrlig = beregningsgrunnlag.årlig,
                    minimumInntektÅrlig = minsteinntekt.årlig
                )
            )
    }

    internal companion object {

        internal fun List<Inntektsgrunnlag>.harUlikeGrunnbeløp(): Boolean {
            return map { it.`6G` }.distinct().size > 1
        }

        fun opprett(
            alder: Alder,
            arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
            skjæringstidspunkt: LocalDate,
            subsumsjonslogg: Subsumsjonslogg
        ): Inntektsgrunnlag {
            check(arbeidsgiverInntektsopplysninger.distinctBy { it.orgnummer }.size == arbeidsgiverInntektsopplysninger.size) {
                "det er oppgitt duplikat orgnumre i inntektsgrunnlaget: ${arbeidsgiverInntektsopplysninger.joinToString { it.orgnummer }}"
            }
            return Inntektsgrunnlag(
                alder,
                arbeidsgiverInntektsopplysninger,
                skjæringstidspunkt,
                subsumsjonslogg
            )
        }

        fun ferdigSykepengegrunnlag(
            alder: Alder,
            skjæringstidspunkt: LocalDate,
            arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
            deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
            vurdertInfotrygd: Boolean,
            `6G`: Inntekt? = null
        ): Inntektsgrunnlag {
            return Inntektsgrunnlag(alder, skjæringstidspunkt, arbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold, emptyList(), vurdertInfotrygd, `6G`)
        }

        fun gjenopprett(alder: Alder, skjæringstidspunkt: LocalDate, dto: InntektsgrunnlagInnDto, inntekter: MutableMap<UUID, Inntektsopplysning>): Inntektsgrunnlag {
            return Inntektsgrunnlag(
                alder = alder,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiverInntektsopplysninger = dto.arbeidsgiverInntektsopplysninger.map { ArbeidsgiverInntektsopplysning.gjenopprett(it, inntekter) },
                deaktiverteArbeidsforhold = dto.deaktiverteArbeidsforhold.map { ArbeidsgiverInntektsopplysning.gjenopprett(it, inntekter) },
                tilkommendeInntekter = dto.tilkommendeInntekter.map { NyInntektUnderveis.gjenopprett(it) },
                vurdertInfotrygd = dto.vurdertInfotrygd,
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
        omregnetÅrsinntekt = omregnetÅrsinntekt,
        beregningsgrunnlag = beregningsgrunnlag,
        `6G` = `6G`,
        begrensning = begrensning,
        vurdertInfotrygd = vurdertInfotrygd,
        minsteinntekt = minsteinntekt,
        oppfyllerMinsteinntektskrav = oppfyllerMinsteinntektskrav,
        arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger,
        deaktiverteArbeidsforhold = deaktiverteArbeidsforhold.map { it.orgnummer },
        tilkommendeInntekter = tilkommendeInntekter
    )

    internal fun valider(aktivitetslogg: IAktivitetslogg): Boolean {
        if (oppfyllerMinsteinntektskrav) aktivitetslogg.info("Krav til minste sykepengegrunnlag er oppfylt")
        else aktivitetslogg.varsel(RV_SV_1)
        return oppfyllerMinsteinntektskrav && !aktivitetslogg.harFunksjonelleFeilEllerVerre()
    }

    internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.harInntekt(organisasjonsnummer)

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
        val resultat = builder.resultat()
        val overstyrtTilkommenInntekt = tilkommendeInntekter.overstyr(hendelse)
        arbeidsgiverInntektsopplysninger.forEach { it.arbeidsgiveropplysningerKorrigert(person, hendelse) }
        return kopierSykepengegrunnlagOgValiderMinsteinntekt(resultat, deaktiverteArbeidsforhold, overstyrtTilkommenInntekt, subsumsjonslogg)
    }

    internal fun skjønnsmessigFastsettelse(hendelse: SkjønnsmessigFastsettelse, opptjening: Opptjening?, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag {
        val builder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, arbeidsgiverInntektsopplysninger, opptjening, subsumsjonslogg)
        hendelse.overstyr(builder)
        val resultat = builder.resultat()
        return kopierSykepengegrunnlagOgValiderMinsteinntekt(
            resultat,
            deaktiverteArbeidsforhold,
            tilkommendeInntekter,
            subsumsjonslogg
        )
    }

    internal fun tilkomneInntekterFraSøknaden(søknad: IAktivitetslogg, periode: Periode, nyeInntekter: List<NyInntektUnderveis>, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag? {
        if (this.tilkommendeInntekter.isEmpty() && nyeInntekter.isEmpty()) return null
        return kopierSykepengegrunnlag(arbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold, tilkommendeInntekter = this.tilkommendeInntekter.merge(periode, nyeInntekter))
    }

    internal fun harTilkommendeInntekter() = tilkommendeInntekter.isNotEmpty()
    internal fun nyeArbeidsgiverInntektsopplysninger(
        person: Person,
        korrigertInntektsmelding: KorrigertInntektOgRefusjon,
        subsumsjonslogg: Subsumsjonslogg
    ): Inntektsgrunnlag {
        val builder = ArbeidsgiverInntektsopplysningerOverstyringer(skjæringstidspunkt, arbeidsgiverInntektsopplysninger, null, subsumsjonslogg)
        builder.leggTilInntekt(korrigertInntektsmelding.arbeidsgiverInntektsopplysning(skjæringstidspunkt))
        val resultat = builder.resultat()
        when (val inntektFraFør = arbeidsgiverInntektsopplysninger.finn(korrigertInntektsmelding.organisasjonsnummer)?.inntektsopplysning) {
            is Inntektsmeldinginntekt -> {
                person.arbeidsgiveropplysningerKorrigert(
                    PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                        korrigertInntektsmeldingId = inntektFraFør.hendelseId,
                        korrigerendeInntektektsopplysningstype = INNTEKTSMELDING,
                        korrigerendeInntektsopplysningId = korrigertInntektsmelding.hendelse.metadata.meldingsreferanseId
                    )
                )
            }

            is Infotrygd,
            is Saksbehandler,
            is IkkeRapportert,
            is SkattSykepengegrunnlag,
            is SkjønnsmessigFastsatt,
            null -> { /* gjør ingenting */
            }
        }
        return kopierSykepengegrunnlagOgValiderMinsteinntekt(
            resultat,
            deaktiverteArbeidsforhold,
            tilkommendeInntekter,
            subsumsjonslogg
        )
    }

    private fun kopierSykepengegrunnlagOgValiderMinsteinntekt(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
        tilkommendeInntekter: List<NyInntektUnderveis>,
        subsumsjonslogg: Subsumsjonslogg
    ): Inntektsgrunnlag {
        return kopierSykepengegrunnlag(arbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold, tilkommendeInntekter = tilkommendeInntekter).apply {
            subsummerMinsteSykepengegrunnlag(alder, skjæringstidspunkt, subsumsjonslogg)
        }
    }

    private fun kopierSykepengegrunnlag(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
        nyttSkjæringstidspunkt: LocalDate = skjæringstidspunkt,
        tilkommendeInntekter: List<NyInntektUnderveis> = this.tilkommendeInntekter
    ) = Inntektsgrunnlag(
        alder = alder,
        skjæringstidspunkt = nyttSkjæringstidspunkt,
        arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger,
        deaktiverteArbeidsforhold = deaktiverteArbeidsforhold,
        tilkommendeInntekter = tilkommendeInntekter,
        vurdertInfotrygd = vurdertInfotrygd
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
        arbeidsgiverInntektsopplysninger.any { it.gjelder(organisasjonsnummer) }

    internal fun berik(builder: UtkastTilVedtakBuilder) {
        builder.sykepengegrunnlag(
            sykepengegrunnlag = sykepengegrunnlag,
            beregningsgrunnlag = beregningsgrunnlag,
            totalOmregnetÅrsinntekt = omregnetÅrsinntekt,
            seksG = `6G`,
            inngangsvilkårFraInfotrygd = vurdertInfotrygd
        )
        tilkommendeInntekter.forEach { builder.tilkommetInntekt(it.orgnummer) }
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
    internal fun finnEndringsdato(other: Inntektsgrunnlag): LocalDate? {
        check(this.skjæringstidspunkt == other.skjæringstidspunkt) {
            "Skal bare sammenlikne med samme skjæringstidspunkt"
        }
        return arbeidsgiverInntektsopplysninger.finnEndringsdato(other.arbeidsgiverInntektsopplysninger)
            ?: tilkommendeInntekter.finnEndringsdato(other.tilkommendeInntekter)
    }

    fun harGjenbrukbarInntekt(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.harGjenbrukbarInntekt(organisasjonsnummer)

    fun lagreTidsnæreInntekter(skjæringstidspunkt: LocalDate, arbeidsgiver: Arbeidsgiver, aktivitetslogg: IAktivitetslogg, nyArbeidsgiverperiode: Boolean) {
        arbeidsgiverInntektsopplysninger.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, aktivitetslogg, nyArbeidsgiverperiode)
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

        internal fun resultat(): List<ArbeidsgiverInntektsopplysning> {
            return opprinneligArbeidsgiverInntektsopplysninger.overstyrInntekter(skjæringstidspunkt, opptjening, nyeInntektsopplysninger, subsumsjonslogg)
        }
    }

    internal fun fastsattInntekt(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.fastsattInntekt(skjæringstidspunkt, organisasjonsnummer)

    internal fun dto() = InntektsgrunnlagUtDto(
        arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.dto() },
        deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.dto() },
        tilkommendeInntekter = this.tilkommendeInntekter.map { it.dto() },
        vurdertInfotrygd = this.vurdertInfotrygd,
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
        tilkommendeInntekter = this.tilkommendeInntekter.map { VilkårsprøvdSkjæringstidspunkt.NyInntektUnderveis(it.orgnummer, it.beløpstidslinje) },
        deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.orgnummer }
    )
}

internal data class InntektsgrunnlagView(
    val sykepengegrunnlag: Inntekt,
    val omregnetÅrsinntekt: Inntekt,
    val beregningsgrunnlag: Inntekt,
    val `6G`: Inntekt,
    val begrensning: Inntektsgrunnlag.Begrensning,
    val vurdertInfotrygd: Boolean,
    val minsteinntekt: Inntekt,
    val oppfyllerMinsteinntektskrav: Boolean,
    val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    val deaktiverteArbeidsforhold: List<String>,
    val tilkommendeInntekter: List<NyInntektUnderveis>
)
