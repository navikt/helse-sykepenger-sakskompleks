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
import no.nav.helse.hendelser.Inntektsmelding.Avsendersystem.Companion.erPortalinntektsmelding
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.nesteDag
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Behandlinger
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.erForkastet
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.overlapperMed
import no.nav.helse.person.Person
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.Vedtaksperiode.Companion.SAMMENHENGENDE_PERIODER_HOS_ARBEIDSGIVER
import no.nav.helse.person.Vedtaksperiode.Companion.finn
import no.nav.helse.person.Vedtaksperiode.Companion.finnSkjæringstidspunktFor
import no.nav.helse.person.Vedtaksperiode.Companion.inneholder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsgrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Inntektsmelding as InntektsmeldingInntekt
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.refusjonsopplysninger
import no.nav.helse.person.refusjon.Refusjonsservitør
import no.nav.helse.økonomi.Inntekt
import org.slf4j.LoggerFactory

class Inntektsmelding(
    meldingsreferanseId: UUID,
    private val refusjon: Refusjon,
    orgnummer: String,
    private val aktørId: String,
    private val førsteFraværsdag: LocalDate?,
    private val inntektsdato: LocalDate?,
    private val beregnetInntekt: Inntekt,
    arbeidsgiverperioder: List<Periode>,
    private val arbeidsforholdId: String?,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String?,
    harOpphørAvNaturalytelser: Boolean = false,
    harFlereInntektsmeldinger: Boolean,
    private val avsendersystem: Avsendersystem?,
    private val vedtaksperiodeId: UUID?,
    mottatt: LocalDateTime
) : Hendelse {
    companion object {
        private fun inntektdato(førsteFraværsdag: LocalDate?, arbeidsgiverperioder: List<Periode>): LocalDate {
            if (førsteFraværsdag != null && (arbeidsgiverperioder.isEmpty() || førsteFraværsdag > arbeidsgiverperioder.last().endInclusive.nesteDag)) return førsteFraværsdag
            return arbeidsgiverperioder.maxOf { it.start }
        }

        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logger = LoggerFactory.getLogger(Inntektsmelding::class.java)

    }

    init {
        if (arbeidsgiverperioder.isEmpty() && førsteFraværsdag == null) error("Arbeidsgiverperiode er tom og førsteFraværsdag er null")
    }

    override val behandlingsporing = Behandlingsporing.Arbeidsgiver(
        organisasjonsnummer = orgnummer
    )
    override val metadata = HendelseMetadata(
        meldingsreferanseId = meldingsreferanseId,
        avsender = Avsender.ARBEIDSGIVER,
        innsendt = mottatt,
        registrert = mottatt,
        automatiskBehandling = false
    )

    private val arbeidsgiverperioder = arbeidsgiverperioder.grupperSammenhengendePerioder()
    private val dager = DagerFraInntektsmelding(
        this.arbeidsgiverperioder,
        førsteFraværsdag,
        mottatt,
        begrunnelseForReduksjonEllerIkkeUtbetalt,
        avsendersystem,
        harFlereInntektsmeldinger,
        harOpphørAvNaturalytelser,
        this
    )
    private var håndtertInntekt = false
    private val beregnetInntektsdato = inntektdato(førsteFraværsdag, this.arbeidsgiverperioder)
    private val dokumentsporing = Dokumentsporing.inntektsmeldingInntekt(meldingsreferanseId)

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, aktivitetslogg: IAktivitetslogg, alternativInntektsdato: LocalDate) {
        if (alternativInntektsdato == this.beregnetInntektsdato) return
        if (!inntektshistorikk.leggTil(InntektsmeldingInntekt(alternativInntektsdato, metadata.meldingsreferanseId, beregnetInntekt))) return
        aktivitetslogg.info("Lagrer inntekt på alternativ inntektsdato $alternativInntektsdato")
    }

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, subsumsjonslogg: Subsumsjonslogg, vedtaksperioder: List<Vedtaksperiode>): LocalDate {
        subsumsjonslogg.logg(`§ 8-10 ledd 3`(beregnetInntekt.årlig, beregnetInntekt.daglig))
        if (erPortalinntektsmelding()) {
            requireNotNull(vedtaksperiodeId) { "En portalinntektsmelding må oppgi vedtaksperiodeId-en den skal gjelde for" }
            val skjæringstidspunkt = vedtaksperioder.finnSkjæringstidspunktFor(vedtaksperiodeId) ?: return beregnetInntektsdato // TODO tenk litt mer på dette
            if (skjæringstidspunkt != inntektsdato) {
                "Inntekt lagres på en annen dato enn oppgitt i portalinntektsmelding for inntektsmeldingId ${metadata.meldingsreferanseId}. Inntektsmelding oppga inntektsdato $inntektsdato, men inntekten ble lagret på skjæringstidspunkt $skjæringstidspunkt"
                    .let {
                        logger.info(it)
                        sikkerlogg.info("$it. For aktørId $aktørId.")
                    }
            }
            inntektshistorikk.leggTil(InntektsmeldingInntekt(skjæringstidspunkt, metadata.meldingsreferanseId, beregnetInntekt))
            return skjæringstidspunkt
        }

        inntektshistorikk.leggTil(InntektsmeldingInntekt(beregnetInntektsdato, metadata.meldingsreferanseId, beregnetInntekt))
        return beregnetInntektsdato
    }

    private val refusjonsElement = Refusjonshistorikk.Refusjon(
        meldingsreferanseId = metadata.meldingsreferanseId,
        førsteFraværsdag = førsteFraværsdag,
        arbeidsgiverperioder = arbeidsgiverperioder,
        beløp = refusjon.beløp,
        sisteRefusjonsdag = refusjon.opphørsdato,
        endringerIRefusjon = refusjon.endringerIRefusjon.map { it.tilEndring() },
        tidsstempel = metadata.registrert
    )

    private fun refusjonsElement(vedtaksperioder: List<Vedtaksperiode>, arbeidsgiver: Arbeidsgiver): Refusjonshistorikk.Refusjon {
        val utledetFørsteFraværsdag = utledFørsteFraværsdag(vedtaksperioder, arbeidsgiver)
        return Refusjonshistorikk.Refusjon(
            meldingsreferanseId = metadata.meldingsreferanseId,
            førsteFraværsdag = utledetFørsteFraværsdag,
            arbeidsgiverperioder = arbeidsgiverperioder,
            beløp = refusjon.beløp,
            sisteRefusjonsdag = refusjon.opphørsdato,
            endringerIRefusjon = refusjon.endringerIRefusjon.map { it.tilEndring() },
            tidsstempel = metadata.registrert
        )
    }

    private fun utledFørsteFraværsdag(
        vedtaksperioder: List<Vedtaksperiode>,
        arbeidsgiver: Arbeidsgiver
    ): LocalDate? {
        return if (erPortalinntektsmelding()) {
            val skjæringstidspunkt = vedtaksperioder.finnSkjæringstidspunktFor(requireNotNull(vedtaksperiodeId)) ?: return førsteFraværsdag
            val vedtaksperiode = vedtaksperioder.finn(vedtaksperiodeId) ?: return førsteFraværsdag
            arbeidsgiver.finnFørsteFraværsdag(skjæringstidspunkt, SAMMENHENGENDE_PERIODER_HOS_ARBEIDSGIVER(vedtaksperiode)) ?: beregnetInntektsdato
        } else {
            førsteFraværsdag
        }
    }

    internal val refusjonsservitør = Refusjonsservitør.fra(refusjon.refusjonstidslinje(førsteFraværsdag, arbeidsgiverperioder, meldingsreferanseId, mottatt))

    internal fun leggTilRefusjon(refusjonshistorikk: Refusjonshistorikk, vedtaksperioder: List<Vedtaksperiode>, arbeidsgiver: Arbeidsgiver) {
        val refusjonsElement = refusjonsElement(vedtaksperioder, arbeidsgiver)
        refusjonshistorikk.leggTilRefusjon(refusjonsElement)
    }

    internal fun leggTil(behandlinger: Behandlinger): Boolean {
        håndtertInntekt = true
        return behandlinger.oppdaterDokumentsporing(dokumentsporing)
    }


    internal fun nyeArbeidsgiverInntektsopplysninger(builder: ArbeidsgiverInntektsopplysningerOverstyringer, skjæringstidspunkt: LocalDate) {
        val refusjonshistorikk = Refusjonshistorikk()
        refusjonshistorikk.leggTilRefusjon(refusjonsElement)
        // startskuddet dikterer hvorvidt refusjonsopplysningene skal strekkes tilbake til å fylle gråsonen (perioden mellom skjæringstidspunkt og første refusjonsopplysning)
        // inntektsdato er den dagen refusjonsopplysningen i IM gjelder fom slik at det blir ingen strekking da, bare dersom skjæringstidspunkt brukes
        val startskudd = if (builder.ingenRefusjonsopplysninger(behandlingsporing.organisasjonsnummer)) skjæringstidspunkt else beregnetInntektsdato
        val inntektGjelder = skjæringstidspunkt til LocalDate.MAX
        builder.leggTilInntekt(
            ArbeidsgiverInntektsopplysning(
                behandlingsporing.organisasjonsnummer,
                inntektGjelder,
                InntektsmeldingInntekt(beregnetInntektsdato, metadata.meldingsreferanseId, beregnetInntekt),
                refusjonshistorikk.refusjonsopplysninger(startskudd)
            )
        )

    }

    enum class Avsendersystem {
        NAV_NO,
        NAV_NO_SELVBESTEMT,
        ALTINN,
        LPS;
        companion object {
            fun Avsendersystem?.erPortalinntektsmelding() = when (this) {
                NAV_NO,
                NAV_NO_SELVBESTEMT -> true
                ALTINN,
                LPS,
                null -> false
            }
        }
    }

    class Refusjon(
        val beløp: Inntekt?,
        val opphørsdato: LocalDate?,
        val endringerIRefusjon: List<EndringIRefusjon> = emptyList()
    ) {

        internal fun refusjonstidslinje(førsteFraværsdag: LocalDate?, arbeidsgiverperioder: List<Periode>, meldingsreferanseId: UUID, tidsstempel: LocalDateTime): Beløpstidslinje {
            val kilde = Kilde(meldingsreferanseId, ARBEIDSGIVER, tidsstempel)
            val startskuddet = startskuddet(førsteFraværsdag, arbeidsgiverperioder)
            val opphørIRefusjon = opphørsdato?.let {
                val sisteRefusjonsdag = maxOf(it, startskuddet.forrigeDag)
                EndringIRefusjon(Inntekt.INGEN, sisteRefusjonsdag.nesteDag)
            }

            val hovedopplysning = EndringIRefusjon(beløp ?: Inntekt.INGEN, startskuddet).takeUnless { it.endringsdato == opphørIRefusjon?.endringsdato }
            val alleRefusjonsopplysninger = listOfNotNull(opphørIRefusjon, hovedopplysning, *endringerIRefusjon.toTypedArray())
                .sortedBy { it.endringsdato }
                .filter { it.endringsdato >= startskuddet }
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

        private fun startskuddet(førsteFraværsdag: LocalDate?, arbeidsgiverperioder: List<Periode>) =
            if (førsteFraværsdag == null) arbeidsgiverperioder.maxOf { it.start }
            else arbeidsgiverperioder.map { it.start }.plus(førsteFraværsdag).max()

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
        return dager
    }

    internal fun ikkeHåndert(
        aktivitetslogg: IAktivitetslogg,
        person: Person,
        vedtaksperioder: List<Vedtaksperiode>,
        forkastede: List<ForkastetVedtaksperiode>,
        sykmeldingsperioder: Sykmeldingsperioder,
        dager: DagerFraInntektsmelding
    ) {
        if (håndtertNå()) return
        aktivitetslogg.info("Inntektsmelding ikke håndtert")
        val relevanteSykmeldingsperioder = sykmeldingsperioder.overlappendePerioder(dager) + sykmeldingsperioder.perioderInnenfor16Dager(dager)
        val overlapperMedForkastet = forkastede.overlapperMed(dager)
        if (relevanteSykmeldingsperioder.isNotEmpty() && !overlapperMedForkastet) {
            person.emitInntektsmeldingFørSøknadEvent(metadata.meldingsreferanseId, relevanteSykmeldingsperioder, behandlingsporing.organisasjonsnummer)
            return aktivitetslogg.info("Inntektsmelding er relevant for sykmeldingsperioder $relevanteSykmeldingsperioder")
        }
        person.emitInntektsmeldingIkkeHåndtert(this, behandlingsporing.organisasjonsnummer, dager.harPeriodeInnenfor16Dager(vedtaksperioder))
    }
    private fun håndtertNå() = håndtertInntekt
    internal fun subsumsjonskontekst() = Subsumsjonskontekst(
        type = KontekstType.Inntektsmelding,
        verdi = metadata.meldingsreferanseId.toString()
    )

    internal fun skalOppdatereVilkårsgrunnlag(
        aktivitetslogg: IAktivitetslogg,
        sykdomstidslinjeperiode: Periode?,
        forkastede: List<ForkastetVedtaksperiode>
    ): Boolean {
        if (vedtaksperiodeId != null && forkastede.erForkastet(vedtaksperiodeId)) return false.also {
            aktivitetslogg.info("Vi har bedt om arbeidsgiveropplysninger, men perioden er forkastet")
        }
        if (erPortalinntektsmelding()) return true
        if (sykdomstidslinjeperiode == null) return false // har ikke noe sykdom for arbeidsgiveren
        return beregnetInntektsdato in sykdomstidslinjeperiode
    }

    private fun erPortalinntektsmelding() = avsendersystem.erPortalinntektsmelding()
    internal fun validerPortalinntektsmelding(vedtaksperioder: List<Vedtaksperiode>, aktivitetslogg: IAktivitetslogg) {
        if (!erPortalinntektsmelding()) return

        if (!vedtaksperioder.inneholder(checkNotNull(vedtaksperiodeId) { "Fikk en portalinntektsmelding uten vedtaksperiodeId!" })) {
            aktivitetslogg.funksjonellFeil(Varselkode.RV_IM_26)
        }
    }
    data class PortalinntektsmledingBuilder(
        private val meldingsreferanseId: UUID,
        private val refusjon: Refusjon,
        private val orgnummer: String,
        private val fødselsnummer: String,
        private val aktørId: String,
        private val førsteFraværsdag: LocalDate?,
        private val inntektsdato: LocalDate?,
        private val beregnetInntekt: Inntekt,
        private val arbeidsgiverperioder: List<Periode>,
        private val arbeidsforholdId: String?,
        private val begrunnelseForReduksjonEllerIkkeUtbetalt: String?,
        private val harOpphørAvNaturalytelser: Boolean = false,
        private val harFlereInntektsmeldinger: Boolean,
        private val avsendersystem: Avsendersystem?,
        private val vedtaksperiodeId: UUID?,
        private val mottatt: LocalDateTime
    ) {
        internal fun build(vedtaksperioder: List<Vedtaksperiode>, aktivitetslogg: IAktivitetslogg): Inntektsmelding? {
            check(avsendersystem.erPortalinntektsmelding()) { "Din tøysefant, denne er jo ikke en portalinntektsmelding!" }
            val vedtaksperioden = vedtaksperioder.finn(checkNotNull(vedtaksperiodeId) { "Fikk en portalinntektsmelding uten vedtaksperiodeId!" })
            if (vedtaksperioden == null) {
                aktivitetslogg.funksjonellFeil(Varselkode.RV_IM_26)
                return null
            }

            return Inntektsmelding(
                meldingsreferanseId = meldingsreferanseId,
                refusjon = refusjon,
                orgnummer = orgnummer,
                aktørId = aktørId,
                førsteFraværsdag = vedtaksperioden.periode().start,
                inntektsdato = inntektsdato,
                beregnetInntekt = beregnetInntekt,
                arbeidsgiverperioder = arbeidsgiverperioder,
                arbeidsforholdId = arbeidsforholdId,
                begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
                harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
                harFlereInntektsmeldinger = harFlereInntektsmeldinger,
                avsendersystem = avsendersystem,
                vedtaksperiodeId = vedtaksperiodeId,
                mottatt = mottatt
            )

        }
        internal val behandlingsporing = Behandlingsporing.Arbeidsgiver(orgnummer)
    }
}
