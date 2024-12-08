package no.nav.helse.hendelser

import no.nav.helse.person.inntekt.Inntektsmelding as InntektsmeldingInntekt
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
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.nesteDag
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.overlapperMed
import no.nav.helse.person.Person
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.Vedtaksperiode.Companion.finn
import no.nav.helse.person.Vedtaksperiode.Companion.påvirkerArbeidsgiverperiode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.refusjonsopplysninger
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.Companion.refusjonsopplysninger
import no.nav.helse.person.refusjon.Refusjonsservitør
import no.nav.helse.økonomi.Inntekt
import org.slf4j.LoggerFactory

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
    val dokumentsporing = Dokumentsporing.inntektsmeldingInntekt(meldingsreferanseId)

    fun korrigertInntekt(refusjonsdato: LocalDate = type.refusjonsdato(this)) = KorrigertInntektOgRefusjon(
        hendelse = this,
        organisasjonsnummer = orgnummer,
        inntekt = InntektsmeldingInntekt(type.inntektsdato(this), metadata.meldingsreferanseId, beregnetInntekt),
        refusjonsopplysninger = Refusjonshistorikk().apply {
            leggTilRefusjon(refusjonsElement(refusjonsdato))
        }.refusjonsopplysninger(refusjonsdato)
    )

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, aktivitetslogg: IAktivitetslogg, alternativInntektsdato: LocalDate) {
        check(avsendersystem !is Avsendersystem.NavPortal)
        val inntektsdato = type.alternativInntektsdatoForInntekthistorikk(this, alternativInntektsdato) ?: return
        if (!inntektshistorikk.leggTil(InntektsmeldingInntekt(inntektsdato, metadata.meldingsreferanseId, beregnetInntekt))) return
        aktivitetslogg.info("Lagrer inntekt på alternativ inntektsdato $inntektsdato")
    }

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, subsumsjonslogg: Subsumsjonslogg): LocalDate {
        check(avsendersystem !is Avsendersystem.NavPortal)
        subsumsjonslogg.logg(`§ 8-10 ledd 3`(beregnetInntekt.årlig, beregnetInntekt.daglig))
        val inntektsdato = type.inntektsdato(this)
        inntektshistorikk.leggTil(InntektsmeldingInntekt(inntektsdato, metadata.meldingsreferanseId, beregnetInntekt))
        return inntektsdato
    }
    internal fun addPortalInntekt(inntektsdato: LocalDate, inntektshistorikk: Inntektshistorikk, subsumsjonslogg: Subsumsjonslogg): LocalDate {
        check(avsendersystem is Avsendersystem.NavPortal)
        subsumsjonslogg.logg(`§ 8-10 ledd 3`(beregnetInntekt.årlig, beregnetInntekt.daglig))
        inntektshistorikk.leggTil(InntektsmeldingInntekt(inntektsdato, metadata.meldingsreferanseId, beregnetInntekt))
        return inntektsdato
    }

    internal fun skjæringstidspunkt(person: Person) = type.skjæringstidspunkt(this, person)

    private fun refusjonsElement(refusjonsdato: LocalDate? = null) = Refusjonshistorikk.Refusjon(
                meldingsreferanseId = metadata.meldingsreferanseId,
                førsteFraværsdag = when (avsendersystem) {
            is Avsendersystem.Altinn,
            is Avsendersystem.LPS -> type.refusjonsdato(this)
            is Avsendersystem.NavPortal -> checkNotNull(refusjonsdato)
        },
                arbeidsgiverperioder = arbeidsgiverperioder,
                beløp = refusjon.beløp,
                sisteRefusjonsdag = refusjon.opphørsdato,
                endringerIRefusjon = refusjon.endringerIRefusjon.map { it.tilEndring() },
                tidsstempel = metadata.registrert
        )

    internal fun refusjonsservitør(refusjonsdato: LocalDate? = null): Refusjonsservitør {
        return Refusjonsservitør.fra(refusjon.refusjonstidslinje(
            refusjonsdato = when (avsendersystem) {
                is Avsendersystem.Altinn,
                is Avsendersystem.LPS -> type.refusjonsdato(this)
                is Avsendersystem.NavPortal -> checkNotNull(refusjonsdato)
            },
            meldingsreferanseId = metadata.meldingsreferanseId,
            tidsstempel = metadata.innsendt
        ))
    }

    internal fun leggTilRefusjon(refusjonshistorikk: Refusjonshistorikk) {
        refusjonshistorikk.leggTilRefusjon(refusjonsElement())
    }

    internal fun leggTilPortalRefusjon(refusjonsdato: LocalDate, refusjonshistorikk: Refusjonshistorikk) {
        refusjonshistorikk.leggTilRefusjon(refusjonsElement(refusjonsdato))
    }

    internal fun inntektHåndtert() {
        håndtertInntekt = true
    }

    sealed interface Avsendersystem {
        data class Altinn(internal val førsteFraværsdag: LocalDate?) : Avsendersystem
        data class LPS(internal val førsteFraværsdag: LocalDate?) : Avsendersystem
        data class NavPortal(internal val vedtaksperiodeId: UUID, internal val inntektsdato: LocalDate, internal val forespurt: Boolean) : Avsendersystem
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

            check(alleRefusjonsopplysninger.isNotEmpty()) { "Inntektsmeldingen inneholder ingen refusjonsopplysninger. Hvordan er dette mulig?" }

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
                førsteFraværsdag = type.førsteFraværsdagForHåndteringAvDager(this),
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
        if (håndtertInntekt) return // Definisjonen av om en inntektsmelding er håndtert eller ikke er at vi har håndtert inntekten i den... 🤡
        type.ikkeHåndtert(
            inntektsmelding = this,
            aktivitetslogg = aktivitetslogg,
            person = person,
            relevanteSykmeldingsperioder = sykmeldingsperioder.overlappendePerioder(dager) + sykmeldingsperioder.perioderInnenfor16Dager(dager),
            overlapperMedForkastet = forkastede.overlapperMed(dager),
            harPeriodeInnenfor16Dager = dager.harPeriodeInnenfor16Dager(vedtaksperioder)
        )
    }

    internal fun ferdigstillPortalIM() {
        check(håndtertInntekt) {
            "portalinntektsmelding er ikke håndtert, selv om vedtaksperioden er aktiv. Er den sendt inn i en annen tilstand enn AvIM?"
        }
    }

    internal fun subsumsjonskontekst() = Subsumsjonskontekst(
        type = KontekstType.Inntektsmelding,
        verdi = metadata.meldingsreferanseId.toString()
    )

    internal fun skalOppdatereVilkårsgrunnlag(sykdomstidslinjeperiode: Periode?) = type.skalOppdatereVilkårsgrunnlag(this, sykdomstidslinjeperiode)

    private lateinit var type: Type

    internal fun valider(vedtaksperioder: List<Vedtaksperiode>, aktivitetslogg: IAktivitetslogg, inntektsmeldingIkkeHåndtert: (inntektsmelding: Inntektsmelding, orgnr: String, harPeriodeInnenfor16Dager: Boolean) -> Unit): Boolean {
        val førsteValidering = !::type.isInitialized
        this.type = when (avsendersystem) {
            is Avsendersystem.Altinn -> KlassiskInntektsmelding(avsendersystem.førsteFraværsdag)
            is Avsendersystem.LPS -> KlassiskInntektsmelding(avsendersystem.førsteFraværsdag)
            is Avsendersystem.NavPortal -> {
                val vedtaksperiode = vedtaksperioder.finn(avsendersystem.vedtaksperiodeId)
                if (vedtaksperiode == null) PortalinntektsmeldingForForkastetPeriode
                else if (!avsendersystem.forespurt && vedtaksperiode.erForlengelse()) SelvbestemtPortalinntektsmeldingForForlengelse
                else if (avsendersystem.forespurt) ForespurtPortalinnteksmelding(vedtaksperiode, avsendersystem.inntektsdato, avsendersystem.vedtaksperiodeId)
                else SelvbestemtPortalinnteksmelding(vedtaksperiode, avsendersystem.inntektsdato)
            }
        }
        if (førsteValidering || type is ForkastetPortalinntektsmelding) aktivitetslogg.info("Håndterer inntektsmelding som ${type::class.simpleName}. Avsendersystem $avsendersystem")
        if (this.type.valider(this, aktivitetslogg)) return true
        aktivitetslogg.info("Inntektsmelding ikke håndtert - ved validering. Type ${type::class.simpleName}. Avsendersystem $avsendersystem")
        if (arbeidsgiverperioder.isEmpty()) inntektsmeldingIkkeHåndtert(this, orgnummer, true)
        else inntektsmeldingIkkeHåndtert(this, orgnummer, vedtaksperioder.påvirkerArbeidsgiverperiode(arbeidsgiverperioder.periode()!!))
        return false
    }

    private sealed interface Type {
        fun valider(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg): Boolean
        fun skalOppdatereVilkårsgrunnlag(inntektsmelding: Inntektsmelding, sykdomstidslinjeperiode: Periode?): Boolean
        fun inntektsdato(inntektsmelding: Inntektsmelding): LocalDate
        fun alternativInntektsdatoForInntekthistorikk(inntektsmelding: Inntektsmelding, alternativInntektsdato: LocalDate): LocalDate?
        fun refusjonsdato(inntektsmelding: Inntektsmelding): LocalDate
        fun førsteFraværsdagForHåndteringAvDager(inntektsmelding: Inntektsmelding): LocalDate?
        fun skjæringstidspunkt(inntektsmelding: Inntektsmelding, person: Person): LocalDate
        fun ikkeHåndtert(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, person: Person, relevanteSykmeldingsperioder: List<Periode>, overlapperMedForkastet: Boolean, harPeriodeInnenfor16Dager: Boolean)
    }

    private data class KlassiskInntektsmelding(private val førsteFraværsdag: LocalDate?) : Type {
        override fun valider(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg): Boolean {
            if (inntektsmelding.arbeidsgiverperioder.isEmpty() && førsteFraværsdag == null) error("Arbeidsgiverperiode er tom og førsteFraværsdag er null")
            return true
        }

        override fun skalOppdatereVilkårsgrunnlag(inntektsmelding: Inntektsmelding, sykdomstidslinjeperiode: Periode?): Boolean {
            if (sykdomstidslinjeperiode == null) return false // har ikke noe sykdom for arbeidsgiveren
            return inntektsdato(inntektsmelding) in sykdomstidslinjeperiode
        }

        override fun inntektsdato(inntektsmelding: Inntektsmelding): LocalDate {
            if (førsteFraværsdag != null && (inntektsmelding.arbeidsgiverperioder.isEmpty() || førsteFraværsdag > inntektsmelding.arbeidsgiverperioder.last().endInclusive.nesteDag)) return førsteFraværsdag
            return inntektsmelding.arbeidsgiverperioder.maxOf { it.start }
        }

        override fun alternativInntektsdatoForInntekthistorikk(inntektsmelding: Inntektsmelding, alternativInntektsdato: LocalDate) = alternativInntektsdato.takeUnless { it == inntektsdato(inntektsmelding) }
        override fun refusjonsdato(inntektsmelding: Inntektsmelding): LocalDate {
            return if (førsteFraværsdag == null) inntektsmelding.arbeidsgiverperioder.maxOf { it.start }
            else inntektsmelding.arbeidsgiverperioder.map { it.start }.plus(førsteFraværsdag).max()
        }

        override fun førsteFraværsdagForHåndteringAvDager(inntektsmelding: Inntektsmelding) = førsteFraværsdag
        override fun skjæringstidspunkt(inntektsmelding: Inntektsmelding, person: Person) = person.beregnSkjæringstidspunkt()().beregnSkjæringstidspunkt(inntektsdato(inntektsmelding).somPeriode())
        override fun ikkeHåndtert(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, person: Person, relevanteSykmeldingsperioder: List<Periode>, overlapperMedForkastet: Boolean, harPeriodeInnenfor16Dager: Boolean) {
            if (relevanteSykmeldingsperioder.isNotEmpty() && !overlapperMedForkastet) {
                person.emitInntektsmeldingFørSøknadEvent(inntektsmelding.metadata.meldingsreferanseId, relevanteSykmeldingsperioder, inntektsmelding.behandlingsporing.organisasjonsnummer)
                return aktivitetslogg.info("Inntektsmelding før søknad - er relevant for sykmeldingsperioder $relevanteSykmeldingsperioder")
            }
            aktivitetslogg.info("Inntektsmelding ikke håndtert - ved ferdigstilling. Type ${this::class.simpleName}. Avsendersystem ${inntektsmelding.avsendersystem}")
            person.emitInntektsmeldingIkkeHåndtert(inntektsmelding, inntektsmelding.behandlingsporing.organisasjonsnummer, harPeriodeInnenfor16Dager)
        }
    }

    private abstract class Portalinntektsmelding(private val vedtaksperiode: Vedtaksperiode, private val inntektsdato: LocalDate) : Type {
        override fun valider(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg) = true
        override fun skalOppdatereVilkårsgrunnlag(inntektsmelding: Inntektsmelding, sykdomstidslinjeperiode: Periode?) = true
        override fun inntektsdato(inntektsmelding: Inntektsmelding): LocalDate {
            val skjæringstidspunkt = vedtaksperiode.skjæringstidspunkt
            if (skjæringstidspunkt != inntektsdato) {
                "Inntekt lagres på en annen dato enn oppgitt i portalinntektsmelding for inntektsmeldingId ${inntektsmelding.metadata.meldingsreferanseId}. Inntektsmelding oppga inntektsdato $inntektsdato, men inntekten ble lagret på skjæringstidspunkt $skjæringstidspunkt"
                    .let {
                        logger.info(it)
                        sikkerlogg.info(it)
                    }
            }
            return skjæringstidspunkt
        }

        override fun alternativInntektsdatoForInntekthistorikk(inntektsmelding: Inntektsmelding, alternativInntektsdato: LocalDate) = null
        override fun refusjonsdato(inntektsmelding: Inntektsmelding) = vedtaksperiode.startdatoPåSammenhengendeVedtaksperioder
        override fun førsteFraværsdagForHåndteringAvDager(inntektsmelding: Inntektsmelding) = vedtaksperiode.periode().start
        override fun skjæringstidspunkt(inntektsmelding: Inntektsmelding, person: Person) = vedtaksperiode.skjæringstidspunkt

        private companion object {
            private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
            private val logger = LoggerFactory.getLogger(Portalinntektsmelding::class.java)
        }
    }

    private data class ForespurtPortalinnteksmelding(private val vedtaksperiode: Vedtaksperiode, private val inntektsdato: LocalDate, private val vedtaksperiodeId: UUID) : Portalinntektsmelding(vedtaksperiode, inntektsdato) {
        override fun ikkeHåndtert(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, person: Person, relevanteSykmeldingsperioder: List<Periode>, overlapperMedForkastet: Boolean, harPeriodeInnenfor16Dager: Boolean) {
            person.emitInntektsmeldingHåndtert(inntektsmelding.metadata.meldingsreferanseId, vedtaksperiodeId, inntektsmelding.orgnummer)
        }
    }

    private data class SelvbestemtPortalinnteksmelding(private val vedtaksperiode: Vedtaksperiode, private val inntektsdato: LocalDate) : Portalinntektsmelding(vedtaksperiode, inntektsdato) {
        override fun ikkeHåndtert(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, person: Person, relevanteSykmeldingsperioder: List<Periode>, overlapperMedForkastet: Boolean, harPeriodeInnenfor16Dager: Boolean) {
            aktivitetslogg.info("Inntektsmelding ikke håndtert - ved ferdigstilling. Type ${this::class.simpleName}. Avsendersystem ${inntektsmelding.avsendersystem}")
            person.emitInntektsmeldingIkkeHåndtert(inntektsmelding, inntektsmelding.behandlingsporing.organisasjonsnummer, harPeriodeInnenfor16Dager)
        }
    }

    private abstract class ForkastetPortalinntektsmelding : Type {
        override fun skalOppdatereVilkårsgrunnlag(inntektsmelding: Inntektsmelding, sykdomstidslinjeperiode: Periode?) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet")
        override fun inntektsdato(inntektsmelding: Inntektsmelding) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet")
        override fun alternativInntektsdatoForInntekthistorikk(inntektsmelding: Inntektsmelding, alternativInntektsdato: LocalDate) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet")
        override fun refusjonsdato(inntektsmelding: Inntektsmelding) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet")
        override fun førsteFraværsdagForHåndteringAvDager(inntektsmelding: Inntektsmelding) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet")
        override fun skjæringstidspunkt(inntektsmelding: Inntektsmelding, person: Person) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet")
        override fun ikkeHåndtert(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, person: Person, relevanteSykmeldingsperioder: List<Periode>, overlapperMedForkastet: Boolean, harPeriodeInnenfor16Dager: Boolean) = error("Forventer ikke videre behandling av portalinntektsmelding som er forkastet.")
    }

    private data object PortalinntektsmeldingForForkastetPeriode : ForkastetPortalinntektsmelding() {
        override fun valider(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg): Boolean {
            aktivitetslogg.funksjonellFeil(Varselkode.RV_IM_26)
            return false
        }
    }

    private data object SelvbestemtPortalinntektsmeldingForForlengelse : ForkastetPortalinntektsmelding() {
        override fun valider(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg) = false
    }
}

data class KorrigertInntektOgRefusjon(
    val hendelse: Hendelse,
    val organisasjonsnummer: String,
    val inntekt: InntektsmeldingInntekt,
    val refusjonsopplysninger: Refusjonsopplysninger
) {
    internal fun arbeidsgiverInntektsopplysning(skjæringstidspunkt: LocalDate, strekkTilSkjæringstidspunkt: Boolean): ArbeidsgiverInntektsopplysning {
        return ArbeidsgiverInntektsopplysning(
            orgnummer = organisasjonsnummer,
            gjelder = skjæringstidspunkt til LocalDate.MAX,
            inntektsopplysning = inntekt,
            refusjonsopplysninger = if (strekkTilSkjæringstidspunkt) refusjonsopplysninger.sikreRefusjonFraOgMed(skjæringstidspunkt) else refusjonsopplysninger
        )
    }

    private fun Refusjonsopplysninger.sikreRefusjonFraOgMed(startskudd: LocalDate): Refusjonsopplysninger {
        if (refusjonsbeløpOrNull(startskudd) != null) return this
        val første = validerteRefusjonsopplysninger.firstOrNull() ?: return this
        val gråsone = første.copy(
            fom = startskudd,
            tom = første.fom.forrigeDag
        )
        return gråsone.refusjonsopplysninger.merge(this)
    }
}
