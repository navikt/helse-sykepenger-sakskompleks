package no.nav.helse.person.infotrygdhistorikk

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.deserialisering.InfotrygdhistorikkelementInnDto
import no.nav.helse.dto.serialisering.InfotrygdhistorikkelementUtDto
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_14
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode.Companion.harBetaltRettFør
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode.Companion.utbetalingsperioder
import no.nav.helse.sykdomstidslinje.Dag.Companion.sammenhengendeSykdom
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

class InfotrygdhistorikkElement private constructor(
    val id: UUID,
    val tidsstempel: LocalDateTime,
    val hendelseId: UUID? = null,
    perioder: List<Infotrygdperiode>,
    inntekter: List<Inntektsopplysning>,
    private val arbeidskategorikoder: Map<String, LocalDate>,
    oppdatert: LocalDateTime,
    private var nyOpprettet: Boolean = false
) {
    var oppdatert = oppdatert
        private set
    private val inntekter = Inntektsopplysning.sorter(inntekter)
    val perioder = Infotrygdperiode.sorter(perioder)
    private val kilde = SykdomshistorikkHendelse.Hendelseskilde("Infotrygdhistorikk", id, tidsstempel)

    init {
        if (!erTom()) requireNotNull(hendelseId) { "HendelseID må være satt når elementet inneholder data" }
    }

    companion object {
        fun opprett(
            oppdatert: LocalDateTime,
            hendelseId: UUID,
            perioder: List<Infotrygdperiode>,
            inntekter: List<Inntektsopplysning>,
            arbeidskategorikoder: Map<String, LocalDate>
        ) =
            InfotrygdhistorikkElement(
                id = UUID.randomUUID(),
                tidsstempel = LocalDateTime.now(),
                hendelseId = hendelseId,
                perioder = perioder,
                inntekter = inntekter,
                arbeidskategorikoder = arbeidskategorikoder,
                oppdatert = oppdatert,
                nyOpprettet = true
            )

        internal fun gjenopprett(dto: InfotrygdhistorikkelementInnDto): InfotrygdhistorikkElement {
            return InfotrygdhistorikkElement(
                id = dto.id,
                tidsstempel = dto.tidsstempel,
                hendelseId = dto.hendelseId,
                perioder = dto.arbeidsgiverutbetalingsperioder.map { ArbeidsgiverUtbetalingsperiode.gjenopprett(it) } +
                    dto.personutbetalingsperioder.map { PersonUtbetalingsperiode.gjenopprett(it) } +
                    dto.ferieperioder.map { Friperiode.gjenopprett(it) },
                inntekter = dto.inntekter.map { Inntektsopplysning.gjenopprett(it) },
                arbeidskategorikoder = dto.arbeidskategorikoder,
                oppdatert = dto.oppdatert
            )
        }
    }

    internal fun betaltePerioder(orgnummer: String? = null): List<Periode> = perioder.utbetalingsperioder(orgnummer)

    internal fun sykdomstidslinje(orgnummer: String): Sykdomstidslinje {
        return perioder
            .filter { it.gjelder(orgnummer) }
            .fold(Sykdomstidslinje()) { result, periode ->
                result.merge(periode.sykdomstidslinje(kilde), sammenhengendeSykdom)
            }
    }

    internal fun sykdomstidslinje(): Sykdomstidslinje {
        return perioder.fold(Sykdomstidslinje()) { result, periode ->
            result.merge(periode.sykdomstidslinje(kilde), sammenhengendeSykdom)
        }
    }

    private fun erTom() =
        perioder.isEmpty() && inntekter.isEmpty() && arbeidskategorikoder.isEmpty()

    internal fun valider(aktivitetslogg: IAktivitetslogg, periode: Periode, organisasjonsnummer: String): Boolean {
        validerBetaltRettFør(periode, aktivitetslogg)
        aktivitetslogg.info("Sjekker utbetalte perioder")
        perioder.forEach { it.valider(aktivitetslogg, organisasjonsnummer, periode) }
        return !aktivitetslogg.harFunksjonelleFeilEllerVerre()
    }

    private fun validerBetaltRettFør(periode: Periode, aktivitetslogg: IAktivitetslogg){
        if (!harBetaltRettFør(periode)) return
        aktivitetslogg.funksjonellFeil(RV_IT_14)
    }

    internal fun utbetalingstidslinje() =
        perioder
            .map { it.utbetalingstidslinje() }
            .fold(Utbetalingstidslinje(), Utbetalingstidslinje::plus)

    private fun harBetaltRettFør(periode: Periode) = perioder.harBetaltRettFør(periode)

    internal fun funksjoneltLik(other: InfotrygdhistorikkElement): Boolean {
        if (!harLikePerioder(other)) return false
        if (!harLikeInntekter(other)) return false
        return this.arbeidskategorikoder == other.arbeidskategorikoder
    }

    private fun harLikePerioder(other: InfotrygdhistorikkElement) = likhet(this.perioder, other.perioder, Infotrygdperiode::funksjoneltLik)
    private fun harLikeInntekter(other: InfotrygdhistorikkElement) = likhet(this.inntekter, other.inntekter, Inntektsopplysning::funksjoneltLik)
    private fun <R> likhet(one: List<R>, two: List<R>, comparator: (R, R) -> Boolean): Boolean {
        if (one.size != two.size) return false
        return one.zip(two, comparator).all { it }
    }

    internal fun erstatter(other: InfotrygdhistorikkElement): Boolean {
        if (!this.funksjoneltLik(other)) return false
        oppdater(other)
        return true
    }

    private fun oppdater(other: InfotrygdhistorikkElement) {
        other.oppdatert = this.oppdatert
    }

    internal fun erEldreEnn(utbetaling: Utbetaling): Boolean {
        return utbetaling.erNyereEnn(this.tidsstempel)
    }

    internal fun erEndretUtbetaling(sisteElementSomFantesFørUtbetaling: InfotrygdhistorikkElement): Boolean {
        if (this === sisteElementSomFantesFørUtbetaling) return false
        return !harLikePerioder(sisteElementSomFantesFørUtbetaling)
    }

    internal fun erNyopprettet() = nyOpprettet

    internal fun harUtbetaltI(periode: Periode) = betaltePerioder().any { it.overlapperMed(periode) }

    internal fun harFerieI(periode: Periode) = perioder.filterIsInstance<Friperiode>().any { it.overlapperMed(periode) }

    internal fun dto() = InfotrygdhistorikkelementUtDto(
        id = this.id,
        tidsstempel = this.tidsstempel,
        hendelseId = this.hendelseId,
        ferieperioder = this.perioder.filterIsInstance<Friperiode>().map { it.dto() },
        arbeidsgiverutbetalingsperioder = this.perioder.filterIsInstance<ArbeidsgiverUtbetalingsperiode>().map { it.dto() },
        personutbetalingsperioder = this.perioder.filterIsInstance<PersonUtbetalingsperiode>().map { it.dto() },
        inntekter = this.inntekter.map { it.dto() },
        arbeidskategorikoder = this.arbeidskategorikoder,
        oppdatert = this.oppdatert
    )
}

