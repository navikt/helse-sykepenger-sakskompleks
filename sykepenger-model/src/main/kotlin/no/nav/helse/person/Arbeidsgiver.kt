package no.nav.helse.person

import no.nav.helse.hendelser.*
import no.nav.helse.person.ArbeidstakerHendelse.Hendelsestype.GjennopptaBehandling
import no.nav.helse.sykdomstidslinje.ConcreteSykdomstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class Arbeidsgiver private constructor(
    private val person: Person,
    private val organisasjonsnummer: String,
    private val id: UUID,
    private val inntekthistorikk: Inntekthistorikk,
    private val tidslinjer: MutableList<Utbetalingstidslinje>,
    private val perioder: MutableList<Vedtaksperiode>,
    internal var utbetalingsreferanse: Long,
    private val aktivitetslogger: Aktivitetslogger
) {

    internal fun inntektshistorikk() = inntekthistorikk.clone()

    internal constructor(person: Person, organisasjonsnummer: String) : this(
        person = person,
        organisasjonsnummer = organisasjonsnummer,
        id = UUID.randomUUID(),
        inntekthistorikk = Inntekthistorikk(),
        tidslinjer = mutableListOf(),
        perioder = mutableListOf(),
        utbetalingsreferanse = System.currentTimeMillis(),
        aktivitetslogger = Aktivitetslogger()
    )

    internal fun accept(visitor: ArbeidsgiverVisitor) {
        visitor.preVisitArbeidsgiver(this, id, organisasjonsnummer)
        visitor.visitArbeidsgiverAktivitetslogger(aktivitetslogger)
        inntekthistorikk.accept(visitor)
        visitor.preVisitTidslinjer()
        tidslinjer.forEach { it.accept(visitor) }
        visitor.postVisitTidslinjer()
        visitor.preVisitPerioder()
        perioder.forEach { it.accept(visitor) }
        visitor.postVisitPerioder()
        visitor.postVisitArbeidsgiver(this, id, organisasjonsnummer)
    }

    internal fun organisasjonsnummer() = organisasjonsnummer

    internal fun peekTidslinje() = tidslinjer.last()

    internal fun push(tidslinje: Utbetalingstidslinje) = tidslinjer.add(tidslinje)

    internal fun håndter(nySøknad: NySøknad) {
        if (!perioder.fold(false) { håndtert, periode -> håndtert || periode.håndter(nySøknad) }) {
            aktivitetslogger.info("Lager ny vedtaksperiode")
            nyVedtaksperiode(nySøknad).håndter(nySøknad)
        }
        nySøknad.kopierAktiviteterTil(aktivitetslogger)
    }

    internal fun håndter(sendtSøknad: SendtSøknad) {
        if (perioder.none { it.håndter(sendtSøknad) }) {
            sendtSøknad.error("Uventet sendt søknad, mangler ny søknad")
        }
        sendtSøknad.kopierAktiviteterTil(aktivitetslogger)
    }

    internal fun håndter(inntektsmelding: Inntektsmelding) {
        inntekthistorikk.add(
            inntektsmelding.førsteFraværsdag.minusDays(1),  // Assuming salary is the day before the first sykedag
            inntektsmelding.hendelseId(),
            inntektsmelding.beregnetInntekt.toBigDecimal()
        )
        if (perioder.none { it.håndter(inntektsmelding) }) {
            inntektsmelding.error("Uventet inntektsmelding, mangler ny søknad")
        }
        inntektsmelding.kopierAktiviteterTil(aktivitetslogger)
    }

    internal fun håndter(person: Person, ytelser: Ytelser) {
        ytelser.addInntekter(inntekthistorikk)
        perioder.forEach { it.håndter(ytelser) }
        ytelser.kopierAktiviteterTil(aktivitetslogger)
    }

    internal fun håndter(manuellSaksbehandling: ManuellSaksbehandling, person: Person) {
        perioder.forEach { it.håndter(manuellSaksbehandling, this, person) }
        manuellSaksbehandling.kopierAktiviteterTil(aktivitetslogger)
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        perioder.forEach { it.håndter(vilkårsgrunnlag) }
        vilkårsgrunnlag.kopierAktiviteterTil(aktivitetslogger)
    }

    internal fun håndter(påminnelse: Påminnelse) =
        perioder.any { it.håndter(påminnelse) }.also {
            påminnelse.kopierAktiviteterTil(aktivitetslogger)
        }

    internal fun sykdomstidslinje(): ConcreteSykdomstidslinje? =
        Vedtaksperiode.sykdomstidslinje(perioder)

    internal fun inntekt(dato: LocalDate): BigDecimal? =
        inntekthistorikk.inntekt(dato)

    internal fun invaliderPerioder(hendelse: ArbeidstakerHendelse) {
        perioder.forEach { it.invaliderPeriode(hendelse) }
    }

    private fun nyVedtaksperiode(nySøknad: NySøknad): Vedtaksperiode {
        return Vedtaksperiode(
            person = person,
            arbeidsgiver = this,
            id = UUID.randomUUID(),
            aktørId = nySøknad.aktørId(),
            fødselsnummer = nySøknad.fødselsnummer(),
            organisasjonsnummer = nySøknad.organisasjonsnummer()
        ).also {
            perioder.add(it)
        }
    }

    internal fun harTilstøtendePeriode(vedtaksperiode: Vedtaksperiode): Boolean {
        return perioder.any { it.harTilstøtende(vedtaksperiode) }
    }

    internal fun tidligerePerioderFerdigBehandlet(vedtaksperiode: Vedtaksperiode) =
        perioder.all { it.erFerdigBehandlet(vedtaksperiode) }

    internal fun gjennoptaBehandling(vedtaksperiode: Vedtaksperiode) {
        perioder.forEach { it.håndter(this, vedtaksperiode, GjennoptaBehandling()) }
    }

    internal class GjennoptaBehandling :
        ArbeidstakerHendelse(UUID.randomUUID(), GjennopptaBehandling, Aktivitetslogger()) {
        override fun rapportertdato(): LocalDateTime {
            kotlin.error("Uventet kall")
        }

        override fun aktørId(): String {
            kotlin.error("Uventet kall")
        }

        override fun fødselsnummer(): String {
            kotlin.error("Uventet kall")
        }

        override fun organisasjonsnummer(): String {
            kotlin.error("Uventet kall")
        }
    }

}
