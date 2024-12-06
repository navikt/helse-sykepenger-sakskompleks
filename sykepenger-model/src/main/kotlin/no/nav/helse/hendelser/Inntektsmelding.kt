package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 8-10 ledd 3`
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Avsender.ARBEIDSGIVER
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.nesteDag
import no.nav.helse.person.Behandlinger
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.overlapperMed
import no.nav.helse.person.Person
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsgrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.refusjonsopplysninger
import no.nav.helse.person.refusjon.Refusjonsservitør
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.person.inntekt.Inntektsmelding as InntektsmeldingInntekt

class Inntektsmelding(
    meldingsreferanseId: UUID,
    private val refusjon: Refusjon,
    private val orgnummer: String,
    private val beregnetInntekt: Inntekt,
    arbeidsgiverperioder: List<Periode>,
    private val begrunnelseForReduksjonEllerIkkeUtbetalt: String?,
    private val harOpphørAvNaturalytelser: Boolean,
    private val harFlereInntektsmeldinger: Boolean,
    val avsendersystem: Avsendersystem,
    mottatt: LocalDateTime
) : Hendelse {

    override val behandlingsporing = Behandlingsporing.Arbeidsgiver(
        organisasjonsnummer = orgnummer
    )
    override val metadata = HendelseMetadata(
        meldingsreferanseId = meldingsreferanseId,
        avsender = ARBEIDSGIVER,
        innsendt = mottatt,
        registrert = mottatt,
        automatiskBehandling = false
    )

    private val arbeidsgiverperioder = arbeidsgiverperioder.grupperSammenhengendePerioder()
    private var håndtertInntekt = false
    private val dokumentsporing = Dokumentsporing.inntektsmeldingInntekt(meldingsreferanseId)

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, aktivitetslogg: IAktivitetslogg, alternativInntektsdato: LocalDate) {
        check(avsendersystem !is Avsendersystem.NavPortal)
        val inntektsdato = avsendersystem.alternativInntektsdatoForInntekthistorikk(this, alternativInntektsdato) ?: return
        if (!inntektshistorikk.leggTil(InntektsmeldingInntekt(inntektsdato, metadata.meldingsreferanseId, beregnetInntekt))) return
        aktivitetslogg.info("Lagrer inntekt på alternativ inntektsdato $inntektsdato")
    }

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, subsumsjonslogg: Subsumsjonslogg): LocalDate {
        check(avsendersystem !is Avsendersystem.NavPortal)
        subsumsjonslogg.logg(`§ 8-10 ledd 3`(beregnetInntekt.årlig, beregnetInntekt.daglig))
        val inntektsdato = avsendersystem.inntektsdato(this)
        inntektshistorikk.leggTil(InntektsmeldingInntekt(inntektsdato, metadata.meldingsreferanseId, beregnetInntekt))
        return inntektsdato
    }

    internal fun addPortalInntekt(inntektsdato: LocalDate, inntektshistorikk: Inntektshistorikk, subsumsjonslogg: Subsumsjonslogg) {
        check(avsendersystem is Avsendersystem.NavPortal)
        subsumsjonslogg.logg(`§ 8-10 ledd 3`(beregnetInntekt.årlig, beregnetInntekt.daglig))
        inntektshistorikk.leggTil(InntektsmeldingInntekt(inntektsdato, metadata.meldingsreferanseId, beregnetInntekt))
    }

    internal fun skjæringstidspunkt(person: Person) = avsendersystem.skjæringstidspunkt(this, person)

    private fun refusjonsElement(refusjonsdato: LocalDate? = avsendersystem.refusjonsdato(this)) = Refusjonshistorikk.Refusjon(
        meldingsreferanseId = metadata.meldingsreferanseId,
        førsteFraværsdag = refusjonsdato,
        arbeidsgiverperioder = arbeidsgiverperioder,
        beløp = refusjon.beløp,
        sisteRefusjonsdag = refusjon.opphørsdato,
        endringerIRefusjon = refusjon.endringerIRefusjon.map { it.tilEndring() },
        tidsstempel = metadata.registrert
    )

    internal fun refusjonsservitør(dag: LocalDate? = null): Refusjonsservitør {
        val refusjonsdato = when (avsendersystem) {
            is Avsendersystem.Altinn,
            is Avsendersystem.LPS -> avsendersystem.refusjonsdato(this)
            is Avsendersystem.NavPortal -> checkNotNull(dag)
        }
        return Refusjonsservitør.fra(refusjon.refusjonstidslinje(refusjonsdato, metadata.meldingsreferanseId, metadata.innsendt))
    }

    internal fun leggTilPortalRefusjon(refusjonsdato: LocalDate, refusjonshistorikk: Refusjonshistorikk) {
        check(avsendersystem is Avsendersystem.NavPortal)
        refusjonshistorikk.leggTilRefusjon(refusjonsElement(refusjonsdato))
    }

    internal fun leggTilRefusjon(refusjonshistorikk: Refusjonshistorikk) {
        check(avsendersystem !is Avsendersystem.NavPortal)
        refusjonshistorikk.leggTilRefusjon(refusjonsElement())
    }

    internal fun leggTil(behandlinger: Behandlinger): Boolean {
        håndtertInntekt = true
        return behandlinger.oppdaterDokumentsporing(dokumentsporing)
    }

    internal fun nyeArbeidsgiverInntektsopplysninger(builder: ArbeidsgiverInntektsopplysningerOverstyringer, skjæringstidspunkt: LocalDate) {
        check(avsendersystem !is Avsendersystem.NavPortal)

        val refusjonshistorikk = Refusjonshistorikk()
        refusjonshistorikk.leggTilRefusjon(refusjonsElement())
        // startskuddet dikterer hvorvidt refusjonsopplysningene skal strekkes tilbake til å fylle gråsonen (perioden mellom skjæringstidspunkt og første refusjonsopplysning)
        // inntektsdato er den dagen refusjonsopplysningen i IM gjelder fom slik at det blir ingen strekking da, bare dersom skjæringstidspunkt brukes
        val startskudd = if (builder.ingenRefusjonsopplysninger(behandlingsporing.organisasjonsnummer)) skjæringstidspunkt else avsendersystem.refusjonsdato(this)
        val inntektGjelder = skjæringstidspunkt til LocalDate.MAX
        builder.leggTilInntekt(
            ArbeidsgiverInntektsopplysning(
                behandlingsporing.organisasjonsnummer,
                inntektGjelder,
                InntektsmeldingInntekt(avsendersystem.inntektsdato(this), metadata.meldingsreferanseId, beregnetInntekt),
                refusjonshistorikk.refusjonsopplysninger(startskudd)
            )
        )

    }

    sealed interface Avsendersystem {
        data class Altinn(internal val førsteFraværsdag: LocalDate?): Avsendersystem
        data class LPS(internal val førsteFraværsdag: LocalDate?): Avsendersystem
        data class NavPortal(internal val vedtaksperiodeId: UUID, internal val inntektsdato: LocalDate, internal val forespurt: Boolean): Avsendersystem
    }

    private fun Avsendersystem.valider(inntektsmelding: Inntektsmelding): Boolean {
        val førsteFraværsdag = this.førsteFraværsdag
        if (inntektsmelding.arbeidsgiverperioder.isEmpty() && førsteFraværsdag == null) error("Arbeidsgiverperiode er tom og førsteFraværsdag er null")
        return true
    }

    private fun Avsendersystem.skalOppdatereVilkårsgrunnlag(inntektsmelding: Inntektsmelding, sykdomstidslinjeperiode: Periode?): Boolean {
        when (this) {
            is Avsendersystem.Altinn,
            is Avsendersystem.LPS -> {
                if (sykdomstidslinjeperiode == null) return false // har ikke noe sykdom for arbeidsgiveren
                return inntektsdato(inntektsmelding) in sykdomstidslinjeperiode
            }
            is Avsendersystem.NavPortal -> error("skal ikke invokere denne som NavPortal")
        }
    }
    private val Avsendersystem.førsteFraværsdag get() = when (this) {
        is Avsendersystem.Altinn -> førsteFraværsdag
        is Avsendersystem.LPS -> førsteFraværsdag
        is Avsendersystem.NavPortal -> error("skal ikke invokere denne som NavPortal")
    }
    private fun Avsendersystem.inntektsdato(inntektsmelding: Inntektsmelding): LocalDate {
        val førsteFraværsdag = this.førsteFraværsdag
        if (førsteFraværsdag != null && (inntektsmelding.arbeidsgiverperioder.isEmpty() || førsteFraværsdag > inntektsmelding.arbeidsgiverperioder.last().endInclusive.nesteDag)) return førsteFraværsdag
        return inntektsmelding.arbeidsgiverperioder.maxOf { it.start }
    }
    private fun Avsendersystem.alternativInntektsdatoForInntekthistorikk(inntektsmelding: Inntektsmelding, alternativInntektsdato: LocalDate): LocalDate? {
        check(this !is Avsendersystem.NavPortal) { "skal ikke invokere denne som NavPortal" }
        return alternativInntektsdato.takeUnless { it == inntektsdato(inntektsmelding) }
    }
    private fun Avsendersystem.refusjonsdato(inntektsmelding: Inntektsmelding): LocalDate {
        check(this !is Avsendersystem.NavPortal) { "skal ikke invokere denne som NavPortal" }
        val førsteFraværsdag = this.førsteFraværsdag
        return if (førsteFraværsdag == null) inntektsmelding.arbeidsgiverperioder.maxOf { it.start }
        else inntektsmelding.arbeidsgiverperioder.map { it.start }.plus(førsteFraværsdag).max()
    }
    private fun Avsendersystem.førsteFraværsdagForHåndteringAvDager(): LocalDate? {
        check(this !is Avsendersystem.NavPortal) { "skal ikke invokere denne som NavPortal" }
        return førsteFraværsdag
    }
    private fun Avsendersystem.skjæringstidspunkt(inntektsmelding: Inntektsmelding, person: Person): LocalDate {
        check(this !is Avsendersystem.NavPortal) { "skal ikke invokere denne som NavPortal" }
        return person.beregnSkjæringstidspunkt()().beregnSkjæringstidspunkt(inntektsdato(inntektsmelding).somPeriode())
    }

    class Refusjon(
        val beløp: Inntekt?,
        val opphørsdato: LocalDate?,
        val endringerIRefusjon: List<EndringIRefusjon> = emptyList()
    ) {

        internal fun refusjonstidslinje(refusjonsdato: LocalDate, meldingsreferanseId: UUID, tidsstempel: LocalDateTime): Beløpstidslinje {
            val kilde = Kilde(meldingsreferanseId, ARBEIDSGIVER, tidsstempel)

            val opphørIRefusjon = opphørsdato?.let {
                val sisteRefusjonsdag = maxOf(it, refusjonsdato.forrigeDag)
                EndringIRefusjon(Inntekt.INGEN, sisteRefusjonsdag.nesteDag)
            }

            val hovedopplysning = EndringIRefusjon(beløp ?: Inntekt.INGEN, refusjonsdato).takeUnless { it.endringsdato == opphørIRefusjon?.endringsdato }
            val alleRefusjonsopplysninger = listOfNotNull(opphørIRefusjon, hovedopplysning, *endringerIRefusjon.toTypedArray())
                .sortedBy { it.endringsdato }
                .filter { it.endringsdato >= refusjonsdato }
                .filter { it.endringsdato <= (opphørIRefusjon?.endringsdato ?: LocalDate.MAX) }

            check(alleRefusjonsopplysninger.isNotEmpty()) {"Inntektsmeldingen inneholder ingen refusjonsopplysninger. Hvordan er dette mulig?"}

            val sisteBit = Beløpstidslinje.fra(alleRefusjonsopplysninger.last().endringsdato.somPeriode(), alleRefusjonsopplysninger.last().beløp, kilde)
            val refusjonstidslinje = alleRefusjonsopplysninger
                .zipWithNext { nåværende, neste ->
                    Beløpstidslinje.fra(nåværende.endringsdato.somPeriode().oppdaterTom(neste.endringsdato.forrigeDag), nåværende.beløp, kilde)
                }
                .fold(sisteBit) { acc, beløpstidslinje -> acc + beløpstidslinje }

            return refusjonstidslinje
        }

        class EndringIRefusjon(
            internal val beløp: Inntekt,
            internal val endringsdato: LocalDate
        ) {

            internal fun tilEndring() = Refusjonshistorikk.Refusjon.EndringIRefusjon(beløp, endringsdato)

            internal companion object {
                internal fun List<EndringIRefusjon>.minOf(opphørsdato: LocalDate?) =
                    (map { it.endringsdato } + opphørsdato).filterNotNull().minOrNull()
            }
        }
    }

    internal fun dager(): DagerFraInntektsmelding {
        return when (avsendersystem) {
            is Avsendersystem.Altinn,
            is Avsendersystem.LPS -> DagerFraInntektsmelding(
                arbeidsgiverperioder = this.arbeidsgiverperioder,
                førsteFraværsdag = avsendersystem.førsteFraværsdagForHåndteringAvDager(),
                mottatt = metadata.registrert,
                begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
                avsendersystem = avsendersystem,
                harFlereInntektsmeldinger = harFlereInntektsmeldinger,
                harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
                hendelse = this
            )
            is Avsendersystem.NavPortal -> DagerFraInntektsmelding(
                arbeidsgiverperioder = this.arbeidsgiverperioder,
                førsteFraværsdag = null,
                mottatt = metadata.registrert,
                begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
                avsendersystem = avsendersystem,
                harFlereInntektsmeldinger = harFlereInntektsmeldinger,
                harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
                hendelse = this
            )
        }
    }

    internal fun ferdigstillKlassiskIM(
        aktivitetslogg: IAktivitetslogg,
        person: Person,
        vedtaksperioder: List<Vedtaksperiode>,
        forkastede: List<ForkastetVedtaksperiode>,
        sykmeldingsperioder: Sykmeldingsperioder,
        dager: DagerFraInntektsmelding
    ) {
        check(avsendersystem !is Avsendersystem.NavPortal)
        if (håndtertInntekt) return // Definisjonen av om en inntektsmelding er håndtert eller ikke er at vi har håndtert inntekten i den... 🤡
        val relevanteSykmeldingsperioder = sykmeldingsperioder.overlappendePerioder(dager) + sykmeldingsperioder.perioderInnenfor16Dager(dager)
        val overlapperMedForkastet = forkastede.overlapperMed(dager)
        val harPeriodeInnenfor16Dager = dager.harPeriodeInnenfor16Dager(vedtaksperioder)

        if (relevanteSykmeldingsperioder.isNotEmpty() && !overlapperMedForkastet) {
            person.emitInntektsmeldingFørSøknadEvent(metadata.meldingsreferanseId, relevanteSykmeldingsperioder, behandlingsporing.organisasjonsnummer)
            return aktivitetslogg.info("Inntektsmelding før søknad - er relevant for sykmeldingsperioder $relevanteSykmeldingsperioder")
        }
        aktivitetslogg.info("Inntektsmelding ikke håndtert - ved ferdigstilling. Type ${this::class.simpleName}. Avsendersystem $avsendersystem")
        person.emitInntektsmeldingIkkeHåndtert(this, behandlingsporing.organisasjonsnummer, harPeriodeInnenfor16Dager)
    }

    internal fun ferdigstillPortalIM() {
        check(avsendersystem is Avsendersystem.NavPortal)
        // todo: denne slår ut i test fordi testriggen sender inn portal-inntektsmeldinger på avsluttede perioder, mm.
        /*check(håndtertInntekt) {
            "hæ, har vi ikke håndtert inntekt på portalinntektsmeldingen?"
        }*/
    }

    internal fun subsumsjonskontekst() = Subsumsjonskontekst(
        type = KontekstType.Inntektsmelding,
        verdi = metadata.meldingsreferanseId.toString()
    )

    internal fun skalOppdatereVilkårsgrunnlag(sykdomstidslinjeperiode: Periode?) = avsendersystem.skalOppdatereVilkårsgrunnlag(this, sykdomstidslinjeperiode)

    internal fun valider(): Boolean {
        check(avsendersystem !is Avsendersystem.NavPortal) {
            "skal ikke validere Navportal via denne"
        }
        val valideringOk = avsendersystem.valider(this)
        return valideringOk
    }
}
