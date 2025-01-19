package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.util.*
import no.nav.helse.dto.deserialisering.ArbeidsgiverInntektsopplysningInnDto
import no.nav.helse.dto.serialisering.ArbeidsgiverInntektsopplysningUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger.KorrigertArbeidsgiverInntektsopplysning
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Opptjening
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.UtkastTilVedtakEvent.Inntektskilde
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.builders.UtkastTilVedtakBuilder
import no.nav.helse.person.inntekt.Inntektsopplysning.Companion.markerFlereArbeidsgivere
import no.nav.helse.person.inntekt.Inntektsopplysning.Companion.validerSkjønnsmessigAltEllerIntet
import no.nav.helse.utbetalingstidslinje.VilkårsprøvdSkjæringstidspunkt
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN

internal data class ArbeidsgiverInntektsopplysning(
    val orgnummer: String,
    val gjelder: Periode,
    val inntektsopplysning: Inntektsopplysning
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

    internal fun gjelder(organisasjonsnummer: String) = organisasjonsnummer == orgnummer

    private fun overstyrMedInntektsmelding(organisasjonsnummer: String, nyInntekt: Inntektsmeldinginntekt): ArbeidsgiverInntektsopplysning {
        if (this.orgnummer != organisasjonsnummer) return this

        val endring = when (this.inntektsopplysning) {
            // erstatter skjønnsmessig inntekt hvis inntektsmeldingen har annet beløp enn den inntekten
            // som ligger bak den skjønnsmessige. i praksis medfører det at det skjønnsmessige sykepengegrunnlaget "rulles tilbake"
            is SkjønnsmessigFastsatt -> when (Inntektsopplysning.erOmregnetÅrsinntektEndret(nyInntekt, this.inntektsopplysning)) {
                true -> nyInntekt
                // oppdater bakenforliggende inntekt til skjønnsmessig fastsettelsen
                else -> this.inntektsopplysning.endreOmregnetÅrsinntekt(nyInntekt)
            }
            // inntektsmelding tillates bare hvis inntekten er i samme måned.
            // hvis det er flere AG med ulik fom, så kan f.eks. skjæringstidspunktet være i en måned og inntektmåneden være i en annen mnd
            else -> when (nyInntekt.inntektsdata.dato.yearMonth == this.inntektsopplysning.inntektsdata.dato.yearMonth) {
                true -> nyInntekt
                else -> this.inntektsopplysning
            }
        }

        return ArbeidsgiverInntektsopplysning(
            orgnummer = this.orgnummer,
            gjelder = this.gjelder,
            inntektsopplysning = endring
        )
    }

    private fun overstyrMedSaksbehandler(overstyringer: List<KorrigertArbeidsgiverInntektsopplysning>): ArbeidsgiverInntektsopplysning {
        val korrigering = overstyringer.singleOrNull { it.organisasjonsnummer == this.orgnummer } ?: return this
        val saksbehandler = Saksbehandler(
            id = UUID.randomUUID(),
            inntektsdata = korrigering.inntektsdata,
            overstyrtInntekt = this.inntektsopplysning
        )
        // bare sett inn ny inntekt hvis beløp er ulikt (speil sender inntekt- og refusjonoverstyring i samme melding)
        val nyInntektsopplysning = when (saksbehandler.fastsattÅrsinntekt() != this.inntektsopplysning.omregnetÅrsinntekt().fastsattÅrsinntekt()) {
            true -> saksbehandler
            false -> this.inntektsopplysning
        }
        return ArbeidsgiverInntektsopplysning(
            orgnummer = this.orgnummer,
            gjelder = korrigering.gjelder,
            inntektsopplysning = nyInntektsopplysning
        )
    }

    private fun skjønnsfastsett(fastsettelser: List<SkjønnsmessigFastsettelse.SkjønnsfastsattInntekt>): ArbeidsgiverInntektsopplysning {
        val fastsettelse = fastsettelser.single { it.orgnummer == this.orgnummer }
        val nyInntektsopplysning = SkjønnsmessigFastsatt(
            id = UUID.randomUUID(),
            inntektsdata = fastsettelse.inntektsdata,
            overstyrtInntekt = this.inntektsopplysning,
            omregnetÅrsinntekt = this.inntektsopplysning.omregnetÅrsinntekt()
        )
        return ArbeidsgiverInntektsopplysning(
            orgnummer = this.orgnummer,
            gjelder = this.gjelder,
            inntektsopplysning = nyInntektsopplysning
        )
    }

    private fun rullTilbake() = ArbeidsgiverInntektsopplysning(
        orgnummer = this.orgnummer,
        gjelder = this.gjelder,
        inntektsopplysning = this.inntektsopplysning.omregnetÅrsinntekt()
    )

    private fun deaktiver(
        forklaring: String,
        oppfylt: Boolean,
        subsumsjonslogg: Subsumsjonslogg
    ): ArbeidsgiverInntektsopplysning {
        inntektsopplysning.subsumerArbeidsforhold(subsumsjonslogg, orgnummer, forklaring, oppfylt)
        return this
    }

    internal companion object {
        internal fun List<ArbeidsgiverInntektsopplysning>.faktaavklarteInntekter() = this
            .map {
                VilkårsprøvdSkjæringstidspunkt.FaktaavklartInntekt(
                    organisasjonsnummer = it.orgnummer,
                    fastsattÅrsinntekt = it.inntektsopplysning.fastsattÅrsinntekt(),
                    gjelder = it.gjelder
                )
            }

        internal fun List<ArbeidsgiverInntektsopplysning>.validerSkjønnsmessigAltEllerIntet(skjæringstidspunkt: LocalDate) =
            omregnetÅrsinntekter(skjæringstidspunkt, this).validerSkjønnsmessigAltEllerIntet()

        internal fun List<ArbeidsgiverInntektsopplysning>.finn(orgnummer: String) =
            firstOrNull { it.gjelder(orgnummer) }

        internal fun List<ArbeidsgiverInntektsopplysning>.deaktiver(
            deaktiverte: List<ArbeidsgiverInntektsopplysning>,
            orgnummer: String,
            forklaring: String,
            subsumsjonslogg: Subsumsjonslogg
        ) =
            this.fjernInntekt(deaktiverte, orgnummer, forklaring, true, subsumsjonslogg)

        internal fun List<ArbeidsgiverInntektsopplysning>.aktiver(
            aktiveres: List<ArbeidsgiverInntektsopplysning>,
            orgnummer: String,
            forklaring: String,
            subsumsjonslogg: Subsumsjonslogg
        ): Pair<List<ArbeidsgiverInntektsopplysning>, List<ArbeidsgiverInntektsopplysning>> {
            val (deaktiverte, aktiverte) = this.fjernInntekt(aktiveres, orgnummer, forklaring, false, subsumsjonslogg)
            // Om inntektene i sykepengegrunnlaget var skjønnsmessig fastsatt før aktivering sikrer vi at alle "rulles tilbake" slik at vi ikke lager et sykepengegrunnlag med mix av SkjønnsmessigFastsatt & andre inntektstyper.
            return deaktiverte to aktiverte.map {
                ArbeidsgiverInntektsopplysning(
                    it.orgnummer,
                    it.gjelder,
                    it.inntektsopplysning.omregnetÅrsinntekt()
                )
            }
        }

        // flytter inntekt for *orgnummer* fra *this* til *deaktiverte*
        // aktive.deaktiver(deaktiverte, orgnummer) er direkte motsetning til deaktiverte.deaktiver(aktive, orgnummer)
        private fun List<ArbeidsgiverInntektsopplysning>.fjernInntekt(
            deaktiverte: List<ArbeidsgiverInntektsopplysning>,
            orgnummer: String,
            forklaring: String,
            oppfylt: Boolean,
            subsumsjonslogg: Subsumsjonslogg
        ): Pair<List<ArbeidsgiverInntektsopplysning>, List<ArbeidsgiverInntektsopplysning>> {
            val inntektsopplysning = checkNotNull(this.singleOrNull { it.orgnummer == orgnummer }) {
                "Kan ikke overstyre arbeidsforhold for en arbeidsgiver vi ikke kjenner til"
            }.deaktiver(forklaring, oppfylt, subsumsjonslogg)
            val aktive = this.filterNot { it === inntektsopplysning }
            return aktive to (deaktiverte + listOfNotNull(inntektsopplysning))
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.overstyrMedInntektsmelding(
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String,
            nyInntekt: Inntektsmeldinginntekt
        ): List<ArbeidsgiverInntektsopplysning> {
            val endringen = this.map { inntekt -> inntekt.overstyrMedInntektsmelding(organisasjonsnummer, nyInntekt) }
            if (erOmregnetÅrsinntektEndret(skjæringstidspunkt, this, endringen)) {
                return endringen.map { it.rullTilbake() }
            }
            return endringen
        }

        // overskriver eksisterende verdier i *this* med verdier fra *other*,
        // og legger til ting i *other* som ikke finnes i *this* som tilkommet inntekter
        internal fun List<ArbeidsgiverInntektsopplysning>.overstyrMedSaksbehandler(
            skjæringstidspunkt: LocalDate,
            other: List<KorrigertArbeidsgiverInntektsopplysning>
        ): List<ArbeidsgiverInntektsopplysning> {
            val endringen = this.map { inntekt -> inntekt.overstyrMedSaksbehandler(other) }
            if (erOmregnetÅrsinntektEndret(skjæringstidspunkt, this, endringen)) {
                return endringen.map { it.rullTilbake() }
            }
            return endringen
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.skjønnsfastsett(other: List<SkjønnsmessigFastsettelse.SkjønnsfastsattInntekt>): List<ArbeidsgiverInntektsopplysning> {
            check (this.size == other.size) { "alle inntektene må skjønnsfastsettes" }
            return this.map { inntekt -> inntekt.skjønnsfastsett(other) }
        }

        private fun erOmregnetÅrsinntektEndret(
            skjæringstidspunkt: LocalDate,
            før: List<ArbeidsgiverInntektsopplysning>,
            etter: List<ArbeidsgiverInntektsopplysning>
        ): Boolean {
            return Inntektsopplysning.erOmregnetÅrsinntektEndret(
                omregnetÅrsinntekter(skjæringstidspunkt, før),
                omregnetÅrsinntekter(skjæringstidspunkt, etter)
            )
        }

        private fun omregnetÅrsinntekter(
            skjæringstidspunkt: LocalDate,
            opplysninger: List<ArbeidsgiverInntektsopplysning>
        ): List<Inntektsopplysning> {
            return opplysninger.filter { it.gjelderPåSkjæringstidspunktet(skjæringstidspunkt) }
                .map { it.inntektsopplysning }
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.sjekkForNyArbeidsgiver(
            aktivitetslogg: IAktivitetslogg,
            opptjening: Opptjening,
            orgnummer: String
        ) {
            val arbeidsforholdAktivePåSkjæringstidspunktet =
                singleOrNull { opptjening.ansattVedSkjæringstidspunkt(it.orgnummer) } ?: return
            if (arbeidsforholdAktivePåSkjæringstidspunktet.orgnummer == orgnummer) return
            aktivitetslogg.varsel(Varselkode.RV_VV_8)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.måHaRegistrertOpptjeningForArbeidsgivere(
            aktivitetslogg: IAktivitetslogg,
            opptjening: Opptjening
        ) {
            if (none { opptjening.startdatoFor(it.orgnummer) == null }) return
            aktivitetslogg.varsel(Varselkode.RV_VV_1)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.markerFlereArbeidsgivere(aktivitetslogg: IAktivitetslogg) {
            return map { it.inntektsopplysning }.markerFlereArbeidsgivere(aktivitetslogg)
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.fastsattInntekt(
            skjæringstidspunkt: LocalDate,
            organisasjonsnummer: String
        ): PersonObserver.FastsattInntekt? {
            val fastsattOpplysning = singleOrNull { it.gjelder(organisasjonsnummer) } ?: return null
            return PersonObserver.FastsattInntekt(fastsattOpplysning.fastsattÅrsinntekt(skjæringstidspunkt))
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.berik(builder: UtkastTilVedtakBuilder) = this
            .forEach { arbeidsgiver ->
                builder.arbeidsgiverinntekt(
                    arbeidsgiver = arbeidsgiver.orgnummer,
                    omregnedeÅrsinntekt = arbeidsgiver.inntektsopplysning.omregnetÅrsinntekt().fastsattÅrsinntekt(),
                    skjønnsfastsatt = if (arbeidsgiver.inntektsopplysning is SkjønnsmessigFastsatt) arbeidsgiver.inntektsopplysning.fastsattÅrsinntekt() else null,
                    gjelder = arbeidsgiver.gjelder,
                    inntektskilde = when (arbeidsgiver.inntektsopplysning) {
                        is IkkeRapportert,
                        is SkattSykepengegrunnlag -> Inntektskilde.AOrdningen

                        is Inntektsmeldinginntekt -> arbeidsgiver.inntektsopplysning.inntektskilde()

                        is Infotrygd -> Inntektskilde.Arbeidsgiver

                        is Saksbehandler,
                        is SkjønnsmessigFastsatt -> Inntektskilde.Saksbehandler
                    }
                )
            }

        internal fun List<ArbeidsgiverInntektsopplysning>.harInntekt(organisasjonsnummer: String) =
            singleOrNull { it.orgnummer == organisasjonsnummer } != null

        internal fun List<ArbeidsgiverInntektsopplysning>.fastsattÅrsinntekt(skjæringstidspunkt: LocalDate) =
            fold(INGEN) { acc, item -> item.fastsattÅrsinntekt(acc, skjæringstidspunkt) }

        internal fun List<ArbeidsgiverInntektsopplysning>.totalOmregnetÅrsinntekt(skjæringstidspunkt: LocalDate) =
            fold(INGEN) { acc, item -> item.omregnetÅrsinntekt(acc, skjæringstidspunkt) }

        internal fun List<ArbeidsgiverInntektsopplysning>.finnEndringsdato(
            other: List<ArbeidsgiverInntektsopplysning>
        ): LocalDate? {
            val endringsDatoer = this.mapNotNull { ny ->
                val gammel = other.singleOrNull { it.orgnummer == ny.orgnummer }
                when {
                    gammel == null -> ny.gjelder.start
                    !ny.inntektsopplysning.funksjoneltLik(gammel.inntektsopplysning) || ny.gjelder != gammel.gjelder -> minOf(ny.gjelder.start, gammel.gjelder.start)
                    else -> null
                }
            }
            return endringsDatoer.minOrNull()
        }

        internal fun List<ArbeidsgiverInntektsopplysning>.harGjenbrukbarInntekt(organisasjonsnummer: String) =
            singleOrNull { it.orgnummer == organisasjonsnummer }?.inntektsopplysning?.gjenbrukbarInntekt() != null

        internal fun List<ArbeidsgiverInntektsopplysning>.lagreTidsnæreInntekter(
            skjæringstidspunkt: LocalDate,
            arbeidsgiver: Arbeidsgiver,
            aktivitetslogg: IAktivitetslogg,
            nyArbeidsgiverperiode: Boolean
        ) {
            this.forEach {
                it.inntektsopplysning.lagreTidsnærInntekt(
                    skjæringstidspunkt,
                    arbeidsgiver,
                    aktivitetslogg,
                    nyArbeidsgiverperiode,
                    it.orgnummer
                )
            }
        }

        internal fun gjenopprett(
            dto: ArbeidsgiverInntektsopplysningInnDto,
            inntekter: MutableMap<UUID, Inntektsopplysning>
        ): ArbeidsgiverInntektsopplysning {
            return ArbeidsgiverInntektsopplysning(
                orgnummer = dto.orgnummer,
                gjelder = Periode.gjenopprett(dto.gjelder),
                inntektsopplysning = Inntektsopplysning.gjenopprett(dto.inntektsopplysning, inntekter)
            )
        }
    }

    internal fun dto() = ArbeidsgiverInntektsopplysningUtDto(
        orgnummer = this.orgnummer,
        gjelder = this.gjelder.dto(),
        inntektsopplysning = this.inntektsopplysning.dto()
    )
}
