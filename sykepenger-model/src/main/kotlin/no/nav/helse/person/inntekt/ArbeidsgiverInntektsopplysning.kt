package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Toggle
import no.nav.helse.dto.deserialisering.ArbeidsgiverInntektsopplysningInnDto
import no.nav.helse.dto.serialisering.ArbeidsgiverInntektsopplysningUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.inntekt.Inntektsopplysning.Companion.markerFlereArbeidsgivere
import no.nav.helse.person.inntekt.Inntektsopplysning.Companion.validerSkjønnsmessigAltEllerIntet
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Økonomi

class ArbeidsgiverInntektsopplysning(
    private val orgnummer: String,
    private val gjelder: Periode,
    private val inntektsopplysning: Inntektsopplysning,
    private val refusjonsopplysninger: Refusjonsopplysninger
) {
    private fun gjelderPåSkjæringstidspunktet(skjæringstidspunkt: LocalDate) =
        skjæringstidspunkt == gjelder.start

    private fun fastsattÅrsinntekt(acc: Inntekt, skjæringstidspunkt: LocalDate): Inntekt {
        return acc + beregningsgrunnlag(skjæringstidspunkt)
    }
    private fun omregnetÅrsinntekt(acc: Inntekt, skjæringstidspunkt: LocalDate): Inntekt {
        return acc + omregnetÅrsinntekt(skjæringstidspunkt)
    }

    private fun fastsattÅrsinntekt(dagen: LocalDate): Inntekt {
        if (dagen > gjelder.endInclusive) return INGEN
        return inntektsopplysning.fastsattÅrsinntekt()
    }

    private fun beregningsgrunnlag(skjæringstidspunkt: LocalDate): Inntekt {
        if (!gjelderPåSkjæringstidspunktet(skjæringstidspunkt)) return INGEN
        return inntektsopplysning.fastsattÅrsinntekt()
    }

    private fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate): Inntekt {
        if (!gjelderPåSkjæringstidspunktet(skjæringstidspunkt)) return INGEN
        return inntektsopplysning.omregnetÅrsinntekt().fastsattÅrsinntekt()
    }

    private fun omregnetÅrsinntekt(skjæringstidspunkt: LocalDate, builder: GodkjenningsbehovBuilder) {
        if (!gjelderPåSkjæringstidspunktet(skjæringstidspunkt)) return
        builder.omregnedeÅrsinntekter(orgnummer, inntektsopplysning.omregnetÅrsinntekt().fastsattÅrsinntekt().reflection { årlig, _, _, _ -> årlig })
    }

    internal fun gjelder(organisasjonsnummer: String) = organisasjonsnummer == orgnummer

    internal fun accept(visitor: ArbeidsgiverInntektsopplysningVisitor) {
        visitor.preVisitArbeidsgiverInntektsopplysning(this, orgnummer, gjelder)
        inntektsopplysning.accept(visitor)
        refusjonsopplysninger.accept(visitor)
        visitor.postVisitArbeidsgiverInntektsopplysning(this, orgnummer, gjelder)
    }

    private fun overstyr(overstyringer: List<ArbeidsgiverInntektsopplysning>): ArbeidsgiverInntektsopplysning {
        val overstyring = overstyringer.singleOrNull { it.orgnummer == this.orgnummer } ?: return this
        return overstyring.overstyrer(this)
    }

    private fun overstyrer(gammel: ArbeidsgiverInntektsopplysning): ArbeidsgiverInntektsopplysning {
        val nyGjelder = when (this.inntektsopplysning) {
            is Saksbehandler -> this.gjelder
            else -> gammel.gjelder
        }
        return ArbeidsgiverInntektsopplysning(orgnummer = this.orgnummer, gjelder = nyGjelder, inntektsopplysning = gammel.inntektsopplysning.overstyresAv(this.inntektsopplysning), refusjonsopplysninger = gammel.refusjonsopplysninger.merge(this.refusjonsopplysninger))
    }

    private fun rullTilbake() = ArbeidsgiverInntektsopplysning(this.orgnummer, gjelder = this.gjelder, this.inntektsopplysning.omregnetÅrsinntekt(), refusjonsopplysninger)

    private fun subsummer(subsumsjonslogg: Subsumsjonslogg, opptjening: Opptjening?) {
        inntektsopplysning.subsumerSykepengegrunnlag(subsumsjonslogg, orgnummer, opptjening?.startdatoFor(orgnummer))
    }

    private fun deaktiver(forklaring: String, oppfylt: Boolean, subsumsjonslogg: Subsumsjonslogg): ArbeidsgiverInntektsopplysning {
        inntektsopplysning.subsumerArbeidsforhold(subsumsjonslogg, orgnummer, forklaring, oppfylt)
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArbeidsgiverInntektsopplysning) return false
        if (orgnummer != other.orgnummer) return false
        if (inntektsopplysning != other.inntektsopplysning) return false
        if (refusjonsopplysninger != other.refusjonsopplysninger) return false
        if (this.gjelder != other.gjelder) return false
        return true
    }

    override fun hashCode(): Int {
        var result = orgnummer.hashCode()
        result = 31 * result + inntektsopplysning.hashCode()
        result = 31 * result + refusjonsopplysninger.hashCode()
        result = 31 * result + gjelder.hashCode()
        return result
    }

    internal fun arbeidsgiveropplysningerKorrigert(
        person: Person,
        inntektsmelding: Inntektsmelding
    ) {
        inntektsopplysning.arbeidsgiveropplysningerKorrigert(person, inntektsmelding)
    }

    internal fun arbeidsgiveropplysningerKorrigert(
        person: Person,
        saksbehandleroverstyring: OverstyrArbeidsgiveropplysninger
    ) {
        inntektsopplysning.arbeidsgiveropplysningerKorrigert(person, orgnummer, saksbehandleroverstyring)
    }

    internal fun ghosttidslinje(organisasjonsnummer: String, sisteDag: LocalDate): Sykdomstidslinje? {
        if (organisasjonsnummer != this.orgnummer) return null
        if (sisteDag < gjelder.start) return Sykdomstidslinje()
        return Sykdomstidslinje.ghostdager(gjelder.start til sisteDag)
    }

    internal fun gjenoppliv(forrigeSkjæringstidspunkt: LocalDate, nyttSkjæringstidspunkt: LocalDate): ArbeidsgiverInntektsopplysning {
        if (forrigeSkjæringstidspunkt == nyttSkjæringstidspunkt) return this // Intet nytt fra vestfronten
        if (nyttSkjæringstidspunkt > gjelder.endInclusive) return this // Unngår å havne i problemer med ugyldige perioder
        return ArbeidsgiverInntektsopplysning(orgnummer, nyttSkjæringstidspunkt til gjelder.endInclusive, inntektsopplysning, refusjonsopplysninger.gjenoppliv(nyttSkjæringstidspunkt))
    }

    internal companion object {
        internal fun List<ArbeidsgiverInntektsopplysning>.validerSkjønnsmessigAltEllerIntet(skjæringstidspunkt: LocalDate) =
            omregnetÅrsinntekter(skjæringstidspunkt, this).validerSkjønnsmessigAltEllerIntet()

        internal fun List<ArbeidsgiverInntektsopplysning>.finn(orgnummer: String) = firstOrNull { it.gjelder(orgnummer) }

        internal fun List<ArbeidsgiverInntektsopplysning>.deaktiver(deaktiverte: List<ArbeidsgiverInntektsopplysning>, orgnummer: String, forklaring: String, subsumsjonslogg: Subsumsjonslogg) =
            this.fjernInntekt(deaktiverte, orgnummer, forklaring, true, subsumsjonslogg)

        internal fun List<ArbeidsgiverInntektsopplysning>.aktiver(aktiveres: List<ArbeidsgiverInntektsopplysning>, orgnummer: String, forklaring: String, subsumsjonslogg: Subsumsjonslogg): Pair<List<ArbeidsgiverInntektsopplysning>, List<ArbeidsgiverInntektsopplysning>> {
            val (deaktiverte, aktiverte) = this.fjernInntekt(aktiveres, orgnummer, forklaring, false, subsumsjonslogg)
            // Om inntektene i sykepengegrunnlaget var skjønnsmessig fastsatt før aktivering sikrer vi at alle "rulles tilbake" slik at vi ikke lager et sykepengegrunnlag med mix av SkjønnsmessigFastsatt & andre inntektstyper.
            return deaktiverte to aktiverte.map { ArbeidsgiverInntektsopplysning(it.orgnummer, it.gjelder, it.inntektsopplysning.omregnetÅrsinntekt(), it.refusjonsopplysninger) }
        }

        // flytter inntekt for *orgnummer* fra *this* til *deaktiverte*
        // aktive.deaktiver(deaktiverte, orgnummer) er direkte motsetning til deaktiverte.deaktiver(aktive, orgnummer)
        private fun List<ArbeidsgiverInntektsopplysning>.fjernInntekt(deaktiverte: List<ArbeidsgiverInntektsopplysning>, orgnummer: String, forklaring: String, oppfylt: Boolean, subsumsjonslogg: Subsumsjonslogg): Pair<List<ArbeidsgiverInntektsopplysning>, List<ArbeidsgiverInntektsopplysning>> {
            val inntektsopplysning = checkNotNull(this.singleOrNull { it.orgnummer == orgnummer }) {
                "Kan ikke overstyre arbeidsforhold for en arbeidsgiver vi ikke kjenner til"
            }.deaktiver(forklaring, oppfylt, subsumsjonslogg)
            val aktive = this.filterNot { it === inntektsopplysning }
            return aktive to (deaktiverte + listOfNotNull(inntektsopplysning))
        }

        // overskriver eksisterende verdier i *this* med verdier fra *other*,
        // og legger til ting i *other* som ikke finnes i *this* som tilkommet inntekter
        internal fun List<ArbeidsgiverInntektsopplysning>.overstyrInntekter(skjæringstidspunkt: LocalDate, opptjening: Opptjening?, other: List<ArbeidsgiverInntektsopplysning>, subsumsjonslogg: Subsumsjonslogg): List<ArbeidsgiverInntektsopplysning> {
            val tilkommetInntekter = other
                .filter { inntekt -> none { it.gjelder(inntekt.orgnummer) } }
                .takeIf { Toggle.TilkommenInntekt.enabled } ?: emptyList()
            val endringen = this
                .map { inntekt -> inntekt.overstyr(other) }
                .also { it.subsummer(subsumsjonslogg, opptjening, this) }
                .plus(tilkommetInntekter)
            if (erOmregnetÅrsinntektEndret(skjæringstidspunkt, this, endringen)) return endringen.map { it.rullTilbake() }
            return endringen
        }

        private fun erOmregnetÅrsinntektEndret(skjæringstidspunkt: LocalDate, før: List<ArbeidsgiverInntektsopplysning>, etter: List<ArbeidsgiverInntektsopplysning>): Boolean {
            return Inntektsopplysning.erOmregnetÅrsinntektEndret(omregnetÅrsinntekter(skjæringstidspunkt, før), omregnetÅrsinntekter(skjæringstidspunkt, etter))
        }

        private fun omregnetÅrsinntekter(skjæringstidspunkt: LocalDate, opplysninger: List<ArbeidsgiverInntektsopplysning>): List<Inntektsopplysning> {
            return opplysninger.filter { it.gjelderPåSkjæringstidspunktet(skjæringstidspunkt) }.map { it.inntektsopplysning }
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.sjekkForNyArbeidsgiver(aktivitetslogg: IAktivitetslogg, opptjening: Opptjening, orgnummer: String) {
            val arbeidsforholdAktivePåSkjæringstidspunktet = singleOrNull { opptjening.ansattVedSkjæringstidspunkt(it.orgnummer) } ?: return
            if (arbeidsforholdAktivePåSkjæringstidspunktet.orgnummer == orgnummer) return
            aktivitetslogg.varsel(Varselkode.RV_VV_8)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg: IAktivitetslogg, opptjening: Opptjening) {
            if (none { opptjening.startdatoFor(it.orgnummer) == null }) return
            aktivitetslogg.varsel(Varselkode.RV_VV_1)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.markerFlereArbeidsgivere(aktivitetslogg: IAktivitetslogg) {
            return map { it.inntektsopplysning }.markerFlereArbeidsgivere(aktivitetslogg)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.refusjonsopplysninger(organisasjonsnummer: String) =
            singleOrNull{it.gjelder(organisasjonsnummer)}?.refusjonsopplysninger ?: Refusjonsopplysninger()

        internal fun List<ArbeidsgiverInntektsopplysning>.forespurtInntektOgRefusjonsopplysninger(skjæringstidspunkt: LocalDate, organisasjonsnummer: String, periode: Periode): Triple<PersonObserver.FastsattInntekt, PersonObserver.Refusjon, PersonObserver.Inntektsdata?>? {
            val fastsattOpplysning = singleOrNull { it.gjelder(organisasjonsnummer) } ?: return null
            val inntekt = PersonObserver.FastsattInntekt(fastsattOpplysning.fastsattÅrsinntekt(skjæringstidspunkt))
            val forslag = inntektforslag(skjæringstidspunkt, fastsattOpplysning)
            val refusjon = PersonObserver.Refusjon(forslag = fastsattOpplysning.refusjonsopplysninger.overlappendeEllerSenereRefusjonsopplysninger(periode))
            return Triple(inntekt, refusjon, forslag)
        }

        private fun inntektforslag(skjæringstidspunkt: LocalDate, arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning): PersonObserver.Inntektsdata? {
            val originalInntektsopplysning = arbeidsgiverInntektsopplysning.inntektsopplysning.omregnetÅrsinntekt()
            val type = when (originalInntektsopplysning) {
                is no.nav.helse.person.inntekt.Inntektsmelding -> PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING
                is Saksbehandler -> PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
                else -> return null
            }
            return PersonObserver.Inntektsdata(
                skjæringstidspunkt = skjæringstidspunkt,
                kilde = type,
                beløp = originalInntektsopplysning.fastsattÅrsinntekt().reflection { _, månedlig, _, _ -> månedlig })
        }

        private fun List<ArbeidsgiverInntektsopplysning>.finnEndredeInntektsopplysninger(forrige: List<ArbeidsgiverInntektsopplysning>): List<ArbeidsgiverInntektsopplysning> {
            val forrigeInntektsopplysninger = forrige.map { it.inntektsopplysning }
            return filterNot { it.inntektsopplysning in forrigeInntektsopplysninger }
        }
        internal fun List<ArbeidsgiverInntektsopplysning>.subsummer(subsumsjonslogg: Subsumsjonslogg, opptjening: Opptjening? = null, forrige: List<ArbeidsgiverInntektsopplysning>) {
            val endredeInntektsopplysninger = finnEndredeInntektsopplysninger(forrige)
            if (endredeInntektsopplysninger.isEmpty()) return
            endredeInntektsopplysninger.forEach { it.subsummer(subsumsjonslogg, opptjening) }
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.build(builder: VedtakFattetBuilder.FastsattISpleisBuilder) {
            forEach { arbeidsgiver ->
                builder.arbeidsgiver(
                    arbeidsgiver = arbeidsgiver.orgnummer,
                    omregnetÅrsinntekt = arbeidsgiver.inntektsopplysning.omregnetÅrsinntekt().fastsattÅrsinntekt(),
                    skjønnsfastsatt = if (arbeidsgiver.inntektsopplysning is SkjønnsmessigFastsatt) arbeidsgiver.inntektsopplysning.fastsattÅrsinntekt() else null
                )
            }
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.harInntekt(organisasjonsnummer: String) =
            singleOrNull { it.orgnummer == organisasjonsnummer } != null

        internal fun List<ArbeidsgiverInntektsopplysning>.ingenRefusjonsopplysninger(organisasjonsnummer: String): Boolean {
            val refusjonsopplysninger = singleOrNull { it.orgnummer == organisasjonsnummer }?.refusjonsopplysninger ?: Refusjonsopplysninger()
            return refusjonsopplysninger.erTom
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.fastsattÅrsinntekt(skjæringstidspunkt: LocalDate) =
            fold(INGEN) { acc, item -> item.fastsattÅrsinntekt(acc, skjæringstidspunkt)}

        internal fun List<ArbeidsgiverInntektsopplysning>.totalOmregnetÅrsinntekt(skjæringstidspunkt: LocalDate) =
            fold(INGEN) { acc, item -> item.omregnetÅrsinntekt(acc, skjæringstidspunkt) }

        internal fun List<ArbeidsgiverInntektsopplysning>.medInntekt(organisasjonsnummer: String, `6G`: Inntekt, skjæringstidspunkt: LocalDate, dato: LocalDate, økonomi: Økonomi, regler: ArbeidsgiverRegler, subsumsjonslogg: Subsumsjonslogg): Økonomi? {
            val arbeidsgiverInntektsopplysning = singleOrNull { it.orgnummer == organisasjonsnummer } ?: return null
            val inntekt = arbeidsgiverInntektsopplysning.fastsattÅrsinntekt(dato)
            val beregningsgrunnlag = arbeidsgiverInntektsopplysning.beregningsgrunnlag(skjæringstidspunkt)
            val refusjonsbeløp = arbeidsgiverInntektsopplysning.refusjonsopplysninger.refusjonsbeløpOrNull(dato) ?: inntekt
            return økonomi.inntekt(
                aktuellDagsinntekt = inntekt,
                beregningsgrunnlag = beregningsgrunnlag,
                dekningsgrunnlag = inntekt.dekningsgrunnlag(dato, regler, subsumsjonslogg),
                `6G` = `6G`,
                refusjonsbeløp = refusjonsbeløp
            )
        }

        private fun List<ArbeidsgiverInntektsopplysning>.arbeidsgiverInntektsopplysning(organisasjonsnummer: String, dato: LocalDate) = checkNotNull(singleOrNull { it.orgnummer == organisasjonsnummer }) {
            """Arbeidsgiver $organisasjonsnummer mangler i sykepengegrunnlaget ved utbetaling av $dato. 
                Arbeidsgiveren må være i sykepengegrunnlaget for å legge til utbetalingsopplysninger. 
                Arbeidsgiverne i sykepengegrunlaget er ${map { it.orgnummer }}"""
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.medUtbetalingsopplysninger(organisasjonsnummer: String, `6G`: Inntekt, skjæringstidspunkt: LocalDate, dato: LocalDate, økonomi: Økonomi, regler: ArbeidsgiverRegler, subsumsjonslogg: Subsumsjonslogg): Økonomi {
            val arbeidsgiverInntektsopplysning = arbeidsgiverInntektsopplysning(organisasjonsnummer, dato)
            val inntekt = arbeidsgiverInntektsopplysning.fastsattÅrsinntekt(dato)
            val beregningsgrunnlag = arbeidsgiverInntektsopplysning.beregningsgrunnlag(skjæringstidspunkt)
            val refusjonsbeløp = checkNotNull(arbeidsgiverInntektsopplysning.refusjonsopplysninger.refusjonsbeløpOrNull(dato)) {
                "Har ingen refusjonsopplysninger på vilkårsgrunnlag med skjæringstidspunkt $skjæringstidspunkt for utbetalingsdag $dato"
            }
            return økonomi.inntekt(
                aktuellDagsinntekt = inntekt,
                beregningsgrunnlag = beregningsgrunnlag,
                dekningsgrunnlag = inntekt.dekningsgrunnlag(dato, regler, subsumsjonslogg),
                `6G` = `6G`,
                refusjonsbeløp = refusjonsbeløp
            )
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.finnEndringsdato(
            skjæringstidspunkt: LocalDate,
            other: List<ArbeidsgiverInntektsopplysning>
        ): LocalDate {
            val endringsDatoer = this.mapNotNull { ny ->
                val gammel = other.singleOrNull { it.orgnummer == ny.orgnummer }
                when {
                    (gammel == null || ny.inntektsopplysning != gammel.inntektsopplysning) -> skjæringstidspunkt
                    else -> ny.refusjonsopplysninger.finnFørsteDatoForEndring(gammel.refusjonsopplysninger)
                }
            }
            return endringsDatoer.minOrNull() ?: skjæringstidspunkt
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.lagreTidsnæreInntekter(
            skjæringstidspunkt: LocalDate,
            arbeidsgiver: Arbeidsgiver,
            hendelse: IAktivitetslogg,
            oppholdsperiodeMellom: Periode?
        ) {
            this.forEach {
                it.inntektsopplysning.lagreTidsnærInntekt(
                    skjæringstidspunkt,
                    arbeidsgiver,
                    hendelse,
                    oppholdsperiodeMellom,
                    it.refusjonsopplysninger,
                    it.orgnummer
                )
            }
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.omregnedeÅrsinntekter(skjæringstidspunkt: LocalDate, builder: GodkjenningsbehovBuilder) {
            builder.orgnummereMedRelevanteArbeidsforhold(this.map { it.orgnummer }.toSet())
            this.forEach { it.omregnetÅrsinntekt(skjæringstidspunkt, builder) }
        }

        internal fun gjenopprett(dto: ArbeidsgiverInntektsopplysningInnDto, inntekter: MutableMap<UUID, Inntektsopplysning>): ArbeidsgiverInntektsopplysning {
            return ArbeidsgiverInntektsopplysning(
                orgnummer = dto.orgnummer,
                gjelder = Periode.gjenopprett(dto.gjelder),
                inntektsopplysning = Inntektsopplysning.gjenopprett(dto.inntektsopplysning, inntekter),
                refusjonsopplysninger = Refusjonsopplysninger.gjenopprett(dto.refusjonsopplysninger)
            )
        }
    }

    internal fun dto() = ArbeidsgiverInntektsopplysningUtDto(
        orgnummer = this.orgnummer,
        gjelder = this.gjelder.dto(),
        inntektsopplysning = this.inntektsopplysning.dto(),
        refusjonsopplysninger = this.refusjonsopplysninger.dto()
    )
}
