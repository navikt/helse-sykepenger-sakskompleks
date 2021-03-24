package no.nav.helse.person.infotrygdhistorikk

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.person.*
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Companion.utbetalingshistorikk
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingstidslinje.Historie
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class Infotrygdhistorikk private constructor(
    private val elementer: MutableList<Element>
) {
    private val siste get() = elementer.first()

    constructor() : this(mutableListOf())

    private companion object {
        private val gammel get() = LocalDateTime.now()
            .minusHours(2)

        private fun oppfriskningsperiode(tidligsteDato: LocalDate) =
            tidligsteDato.minusYears(4) til LocalDate.now()
    }

    internal fun valider(aktivitetslogg: IAktivitetslogg, historie: Historie, organisasjonsnummer: String, periode: Periode, skjæringstidspunkt: LocalDate?): Boolean {
        val avgrensetPeriode = historie.avgrensetPeriode(organisasjonsnummer, periode)
        return valider(aktivitetslogg, historie.periodetype(organisasjonsnummer, periode), avgrensetPeriode, skjæringstidspunkt)
    }

    internal fun valider(aktivitetslogg: IAktivitetslogg, periodetype: Periodetype, periode: Periode, skjæringstidspunkt: LocalDate?): Boolean {
        if (!harHistorikk()) return true
        return siste.valider(aktivitetslogg, periodetype, periode, skjæringstidspunkt)
    }

    internal fun validerOverlappende(aktivitetslogg: IAktivitetslogg, historie: Historie, organisasjonsnummer: String, periode: Periode): Boolean {
        val avgrensetPeriode = historie.avgrensetPeriode(organisasjonsnummer, periode)
        return validerOverlappende(aktivitetslogg, avgrensetPeriode, historie.skjæringstidspunkt(periode))
    }

    internal fun validerOverlappende(aktivitetslogg: IAktivitetslogg, periode: Periode, skjæringstidspunkt: LocalDate?): Boolean {
        if (!harHistorikk()) return true
        return siste.validerOverlappende(aktivitetslogg, periode, skjæringstidspunkt)
    }

    internal fun oppfriskNødvendig(aktivitetslogg: IAktivitetslogg, tidligsteDato: LocalDate, cutoff: LocalDateTime? = null): Boolean {
        if (oppfrisket(cutoff ?: gammel)) return false
        oppfrisk(aktivitetslogg, tidligsteDato)
        return true
    }

    internal fun addInntekter(person: Person, aktivitetslogg: IAktivitetslogg) {
        if (!harHistorikk()) return
        siste.addInntekter(person, aktivitetslogg)
    }

    internal fun append(bøtte: Historie.Historikkbøtte) {
        if (!harHistorikk()) return
        siste.append(bøtte)
    }

    internal fun sisteSykepengedag(orgnummer: String): LocalDate? {
        if (!harHistorikk()) return null
        return siste.sisteSykepengedag(orgnummer)
    }

    internal fun lagreVilkårsgrunnlag(skjæringstidspunkt: LocalDate, periodetype: Periodetype, vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk) {
        if (!harHistorikk()) return
        if (periodetype !in listOf(Periodetype.OVERGANG_FRA_IT, Periodetype.INFOTRYGDFORLENGELSE)) return
        if (vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) != null) return
        siste.lagreVilkårsgrunnlag(skjæringstidspunkt, vilkårsgrunnlagHistorikk)
    }

    internal fun oppdaterHistorikk(element: Element) {
        element.erstatt(elementer)
    }

    internal fun tøm() {
        if (!harHistorikk()) return
        oppdaterHistorikk(Element.opprettTom())
    }

    private fun oppfrisket(cutoff: LocalDateTime) =
        elementer.firstOrNull()?.oppfrisket(cutoff) ?: false

    private fun oppfrisk(aktivitetslogg: IAktivitetslogg, tidligsteDato: LocalDate) {
        utbetalingshistorikk(aktivitetslogg, oppfriskningsperiode(tidligsteDato))
    }

    internal fun accept(visitor: InfotrygdhistorikkVisitor) {
        visitor.preVisitInfotrygdhistorikk()
        elementer.forEach { it.accept(visitor) }
        visitor.postVisitInfotrygdhistorikk()
    }
    private fun harHistorikk() = elementer.isNotEmpty()

    internal class Element private constructor(
        private val id: UUID,
        private val tidsstempel: LocalDateTime,
        private val hendelseId: UUID? = null,
        perioder: List<Infotrygdperiode>,
        private val inntekter: List<Inntektsopplysning>,
        private val arbeidskategorikoder: Map<String, LocalDate>,
        private val ugyldigePerioder: List<Pair<LocalDate?, LocalDate?>>,
        private val harStatslønn: Boolean,
        private var oppdatert: LocalDateTime = tidsstempel
    ) {
        private val perioder = perioder.sortedBy { it.start }
        private val kilde = SykdomstidslinjeHendelse.Hendelseskilde("Infotrygdhistorikk", id)

        init {
            if (!erTom()) requireNotNull(hendelseId) { "HendelseID må være satt når elementet inneholder data" }
        }

        internal companion object {
            fun opprett(
                oppdatert: LocalDateTime,
                hendelseId: UUID,
                perioder: List<Infotrygdperiode>,
                inntekter: List<Inntektsopplysning>,
                arbeidskategorikoder: Map<String, LocalDate>,
                ugyldigePerioder: List<Pair<LocalDate?, LocalDate?>>,
                harStatslønn: Boolean
            ) =
                Element(
                    id = UUID.randomUUID(),
                    tidsstempel = LocalDateTime.now(),
                    hendelseId = hendelseId,
                    perioder = perioder,
                    inntekter = inntekter,
                    arbeidskategorikoder = arbeidskategorikoder,
                    ugyldigePerioder = ugyldigePerioder,
                    harStatslønn = harStatslønn,
                    oppdatert = oppdatert
                )

            fun opprettTom() =
                Element(
                    id = UUID.randomUUID(),
                    tidsstempel = LocalDateTime.now(),
                    hendelseId = null,
                    perioder = emptyList(),
                    inntekter = emptyList(),
                    arbeidskategorikoder = emptyMap(),
                    ugyldigePerioder = emptyList(),
                    harStatslønn = false,
                    oppdatert = LocalDateTime.MIN
                )
        }

        private fun erTom() =
            perioder.isEmpty() && inntekter.isEmpty() && arbeidskategorikoder.isEmpty()

        internal fun addInntekter(person: Person, aktivitetslogg: IAktivitetslogg) {
            Inntektsopplysning.addInntekter(inntekter, person, aktivitetslogg, id)
        }

        fun lagreVilkårsgrunnlag(skjæringstidspunkt: LocalDate, vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk) {
            vilkårsgrunnlagHistorikk.lagre(skjæringstidspunkt, VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag())
        }

        internal fun append(bøtte: Historie.Historikkbøtte) {
            perioder.forEach { it.append(bøtte, kilde) }
        }

        internal fun valider(aktivitetslogg: IAktivitetslogg, periodetype: Periodetype, periode: Periode, skjæringstidspunkt: LocalDate?): Boolean {
            validerUgyldigePerioder(aktivitetslogg)
            validerStatslønn(aktivitetslogg, periodetype)
            return valider(aktivitetslogg, perioder, periode, skjæringstidspunkt)
        }

        internal fun validerOverlappende(aktivitetslogg: IAktivitetslogg, periode: Periode, skjæringstidspunkt: LocalDate?): Boolean {
            aktivitetslogg.info("Sjekker utbetalte perioder for overlapp mot %s", periode)
            return valider(aktivitetslogg, perioder.filterIsInstance<Utbetalingsperiode>(), periode, skjæringstidspunkt)
        }

        private fun validerUgyldigePerioder(aktivitetslogg: IAktivitetslogg) {
            ugyldigePerioder.forEach { (fom,  tom) ->
                val tekst = when {
                    fom == null || tom == null -> "mangler fom- eller tomdato"
                    fom > tom -> "fom er nyere enn tom"
                    else -> null
                }
                aktivitetslogg.error("Det er en ugyldig utbetalingsperiode i Infotrygd%s", tekst?.let { " ($it)" } ?: "")
            }
        }

        private fun validerStatslønn(aktivitetslogg: IAktivitetslogg, periodetype: Periodetype) {
            if (periodetype != Periodetype.OVERGANG_FRA_IT) return
            aktivitetslogg.info("Perioden er en direkte overgang fra periode med opphav i Infotrygd")
            if (!harStatslønn) return
            aktivitetslogg.warn("Det er lagt inn statslønn i Infotrygd, undersøk at utbetalingen blir riktig.")
        }

        private fun valider(aktivitetslogg: IAktivitetslogg, perioder: List<Infotrygdperiode>, periode: Periode, skjæringstidspunkt: LocalDate?): Boolean {
            aktivitetslogg.info("Sjekker utbetalte perioder")
            perioder.forEach { it.valider(aktivitetslogg, periode) }

            aktivitetslogg.info("Sjekker inntektsopplysninger")
            Inntektsopplysning.valider(inntekter, aktivitetslogg, periode, skjæringstidspunkt)

            aktivitetslogg.info("Sjekker arbeidskategorikoder")
            if (!erNormalArbeidstaker(skjæringstidspunkt)) aktivitetslogg.error("Personen er ikke registrert som normal arbeidstaker i Infotrygd")

            return !aktivitetslogg.hasErrorsOrWorse()
        }

        internal fun sisteSykepengedag(orgnummer: String): LocalDate? {
            return perioder.filterIsInstance<Utbetalingsperiode>()
                .filter { it.gjelder(orgnummer) }
                .maxOfOrNull { it.endInclusive }
        }

        internal fun oppfrisket(cutoff: LocalDateTime) =
            oppdatert > cutoff

        internal fun accept(visitor: InfotrygdhistorikkVisitor) {
            visitor.preVisitInfotrygdhistorikkElement(id, tidsstempel, oppdatert, hendelseId, harStatslønn)
            visitor.preVisitInfotrygdhistorikkPerioder()
            perioder.forEach { it.accept(visitor) }
            visitor.postVisitInfotrygdhistorikkPerioder()
            visitor.preVisitInfotrygdhistorikkInntektsopplysninger()
            inntekter.forEach { it.accept(visitor) }
            visitor.postVisitInfotrygdhistorikkInntektsopplysninger()
            visitor.visitUgyldigePerioder(ugyldigePerioder)
            visitor.visitInfotrygdhistorikkArbeidskategorikoder(arbeidskategorikoder)
            visitor.postVisitInfotrygdhistorikkElement(id, tidsstempel, oppdatert, hendelseId, harStatslønn)
        }

        private fun erNormalArbeidstaker(skjæringstidspunkt: LocalDate?): Boolean {
            if (arbeidskategorikoder.isEmpty() || skjæringstidspunkt == null) return true
            return arbeidskategorikoder
                .filter { (_, dato) -> dato >= skjæringstidspunkt }
                .all { (arbeidskategorikode, _) -> arbeidskategorikode == "01" }
        }

        override fun hashCode(): Int {
            return Objects.hash(perioder, inntekter, arbeidskategorikoder, ugyldigePerioder, harStatslønn)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Element) return false
            return this.id == other.id
        }

        fun erstatt(elementer: MutableList<Element>) {
            if (elementer.isNotEmpty() && elementer.first().hashCode() == this.hashCode()) return oppdater(elementer.first())
            return elementer.add(0, this)
        }

        private fun oppdater(other: Element) {
            other.oppdatert = this.oppdatert
        }
    }
}
