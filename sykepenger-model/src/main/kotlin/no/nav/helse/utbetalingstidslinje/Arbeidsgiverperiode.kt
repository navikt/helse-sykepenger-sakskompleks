package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.erRettFør
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_3
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.ukedager
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker

internal class Arbeidsgiverperiode private constructor(private val perioder: List<Periode>, førsteUtbetalingsdag: LocalDate?) : Iterable<LocalDate>, Comparable<LocalDate> {
    constructor(perioder: List<Periode>) : this(perioder, null)

    private val kjenteDager = mutableListOf<Periode>()
    private val utbetalingsdager = mutableListOf<Periode>()
    private val oppholdsdager = mutableListOf<Periode>()

    init {
        check(perioder.isNotEmpty() || førsteUtbetalingsdag != null) {
            "Enten må arbeidsgiverperioden være oppgitt eller så må første utbetalingsdag være oppgitt"
        }
        førsteUtbetalingsdag?.also { utbetalingsdag(it) }
    }

    private val arbeidsgiverperioden get() = perioder.first().start til perioder.last().endInclusive
    private val førsteKjente get() = listOfNotNull(perioder.firstOrNull()?.start, utbetalingsdager.firstOrNull()?.start, kjenteDager.firstOrNull()?.start).minOf { it }
    private val sisteKjente get() = listOfNotNull(perioder.lastOrNull()?.endInclusive, utbetalingsdager.lastOrNull()?.endInclusive, kjenteDager.lastOrNull()?.endInclusive).maxOf { it }
    private val innflytelseperioden get() = førsteKjente til sisteKjente

    internal fun fiktiv() = perioder.isEmpty() // Arbeidsperioden er gjennomført i Infotrygd

    internal fun villeBlittFiktiv(perioderUtbetaltInfotrygd: List<Periode>): Boolean {
        if (perioder.isEmpty()) return true
        if (perioderUtbetaltInfotrygd.any { it.overlapperMed(arbeidsgiverperioden) }) return true
        if (utbetalingsdager.isEmpty()) return false
        return perioderUtbetaltInfotrygd.any { utbetalingsdager.first() in it }
    }

    internal fun periode(sisteDag: LocalDate) = førsteKjente til sisteDag

    internal fun kjentDag(dagen: LocalDate) {
        kjenteDager.add(dagen)
    }

    override fun compareTo(other: LocalDate) =
        førsteKjente.compareTo(other)

    operator fun contains(dato: LocalDate) =
        dato in innflytelseperioden

    operator fun contains(periode: Periode) =
        innflytelseperioden.overlapperMed(periode)

    internal fun forventerInntekt(
        periode: Periode,
        sykdomstidslinje: Sykdomstidslinje,
        subsumsjonslogg: Subsumsjonslogg?
    ): Boolean {
        if (!dekkesAvArbeidsgiver(periode)) return erFørsteUtbetalingsdagFørEllerLik(periode)
        subsumsjonslogg?.`§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`(this, sykdomstidslinje.subsumsjonsformat())
        return false
    }

    /* forventer opplysninger fra arbeidsgiver om første utbetalingsdag etter agp eller opphold er i perioden */
    internal fun forventerOpplysninger(periode: Periode): Boolean {
        if (dekkesAvArbeidsgiver(periode)) return false // trenger ikke opplysninger om perioden er innenfor agp
        val utbetalingsperiode = utbetalingsperiodeForPeriode(periode) ?: return false
        val utbetalingsperiodeFør = forrigeUtbetalingsperiode(utbetalingsperiode) ?: return true
        return erOppholdMellom(utbetalingsperiodeFør, utbetalingsperiode)
    }

    fun forventerArbeidsgiverperiodeopplysning(periode: Periode): Boolean {
        if (dekkesAvArbeidsgiver(periode)) return false // trenger ikke opplysninger om perioden er innenfor agp
        val utbetalingsperiode = utbetalingsdager.firstOrNull() ?: return false
        return utbetalingsperiode.start in periode
    }

    private fun utbetalingsperiodeForPeriode(periode: Periode) =
        utbetalingsdager
            .firstOrNull { utbetalingsperiode -> utbetalingsperiode.start in periode }

    private fun forrigeUtbetalingsperiode(periode: Periode) =
        utbetalingsdager.lastOrNull { utbetalingsperiode -> utbetalingsperiode.endInclusive < periode.start }

    // krever at det foreligger opphold mellom utbetalingsperiodene for at vi skal forvente nye opplysninger
    private fun erOppholdMellom(a: Periode, b: Periode): Boolean {
        val mellomliggendePeriode = checkNotNull(a.periodeMellom(b.start)) {
            "forventer at det skal være dager mellom to utbetalingsperioder, enten pga. helg eller andre oppholdsdager"
        }
        return oppholdsdager.any { oppholdsperiode -> oppholdsperiode.overlapperMed(mellomliggendePeriode) }
    }

    internal fun dekkesAvArbeidsgiver(periode: Periode): Boolean {
        if (fiktiv()) return false
        val arbeidsgiversAnsvar = dagerSomarbeidsgiverUtbetaler() ?: return false
        return (periode.overlapperMed(arbeidsgiversAnsvar) && arbeidsgiversAnsvar.slutterEtter(periode.endInclusive))
    }

    private fun dagerSomarbeidsgiverUtbetaler(): Periode? {
        val heleInklHelg = arbeidsgiverperioden.justerForHelg()
        val utbetalingsperiode = utbetalingsdager.periode() ?: return heleInklHelg
        return heleInklHelg.trimDagerFør(utbetalingsperiode)
    }

    internal fun hørerTil(periode: Periode, sisteKjente: LocalDate = this.sisteKjente) =
        periode.overlapperMed(førsteKjente til sisteKjente)

    internal fun sammenlign(other: List<Periode>): Boolean {
        if (fiktiv()) return true
        val thisSiste = this.perioder.last().endInclusive
        val otherSiste = other.lastOrNull()?.endInclusive?.coerceAtMost(this.sisteKjente) ?: return false
        return otherSiste == thisSiste || (thisSiste.erHelg() && otherSiste.erRettFør(thisSiste)) || (otherSiste.erHelg() && thisSiste.erRettFør(otherSiste))
    }

    internal fun erFørsteUtbetalingsdagFørEllerLik(periode: Periode): Boolean {
        // forventer inntekt dersom vi overlapper med en utbetalingsperiode…
        if (utbetalingsdager.any { periode.overlapperMed(it) }) return true
        // …eller dersom det ikke har vært gap siden forrige utbetaling
        val forrigeUtbetaling = utbetalingsdager.lastOrNull { other -> other.endInclusive < periode.endInclusive } ?: return false
        return oppholdsdager.none { it.overlapperMed(forrigeUtbetaling.endInclusive til periode.endInclusive) }
    }

    private val gjennomført get() = NormalArbeidstaker.arbeidsgiverperiodenGjennomført(perioder.flatten().count())

    internal fun tags(periode: Periode, godkjenningsbehovBuilder: GodkjenningsbehovBuilder, harPeriodeRettFør: Boolean) {
        if (fiktiv() || harPeriodeRettFør) return
        if (periode.start < arbeidsgiverperioden.endInclusive) return
        if (!gjennomført) return
        godkjenningsbehovBuilder.tagIngenNyArbeidsgiverperiode()
    }

    override fun equals(other: Any?) = other is Arbeidsgiverperiode && other.førsteKjente == this.førsteKjente
    override fun hashCode() = førsteKjente.hashCode()

    override fun iterator(): Iterator<LocalDate> {
        return object : Iterator<LocalDate> {
            private val periodeIterators = perioder.map { it.iterator() }.iterator()
            private var current: Iterator<LocalDate>? = null

            override fun hasNext(): Boolean {
                val iterator = current
                if (iterator != null && iterator.hasNext()) return true
                if (!periodeIterators.hasNext()) return false
                current = periodeIterators.next()
                return true
            }

            override fun next(): LocalDate {
                return current?.next() ?: throw NoSuchElementException()
            }
        }
    }

    internal fun utbetalingsdag(dato: LocalDate) = apply {
        if (!dato.erHelg()) this.utbetalingsdager.add(dato)
        kjentDag(dato)
    }

    internal fun oppholdsdag(dato: LocalDate) = apply {
        this.oppholdsdager.add(dato)
        kjentDag(dato)
    }

    internal fun kopierMed(other: Arbeidsgiverperiode): Arbeidsgiverperiode {
        this.kjenteDager.addAll(other.kjenteDager)
        this.utbetalingsdager.addAll(other.utbetalingsdager)
        this.oppholdsdager.addAll(other.oppholdsdager)
        return this
    }

    internal fun klinLik(other: Arbeidsgiverperiode?): Boolean {
        if (other == null) return false
        if (this.toSet().containsAll(other.toSet())) return true
        return false
    }

    internal fun validerFeilaktigNyArbeidsgiverperiode(vedtaksperiode: Periode, aktivitetslogg: IAktivitetslogg): IAktivitetslogg {
        val sisteDagAgp = perioder.lastOrNull()?.endInclusive ?: return aktivitetslogg
        // Om det er én eller fler ukedager mellom beregnet AGP og vedtaksperioden som overlapper med dager fra inntektsmeldingen
        // tyder det på at arbeidsgiver tror det er ny arbeidsgiverperiode, men vi har beregnet at det _ikke_ er ny arbeidsgiverperiode.
        if (ukedager(sisteDagAgp, vedtaksperiode.start) > 0) aktivitetslogg.varsel(RV_IM_3)
        return aktivitetslogg
    }

    internal fun sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(sykdomstidslinje: Sykdomstidslinje): Sykdomstidslinje {
        val periode = sykdomstidslinje.periode() ?: return Sykdomstidslinje()
        val sisteDagIArbeidsgiverperioden = perioder.lastOrNull()?.endInclusive ?: return Sykdomstidslinje()
        val periodeUtoverArbeidsgiverperioden = periode.beholdDagerEtter(sisteDagIArbeidsgiverperioden)?.utenHelgehale() ?: return Sykdomstidslinje()
        return sykdomstidslinje.subset(periodeUtoverArbeidsgiverperioden)
    }

    internal companion object {
        internal fun fiktiv(førsteUtbetalingsdag: LocalDate) = Arbeidsgiverperiode(emptyList(), førsteUtbetalingsdag)

        internal fun forventerInntekt(
            arbeidsgiverperiode: Arbeidsgiverperiode?,
            periode: Periode,
            sykdomstidslinje: Sykdomstidslinje,
            subsumsjonslogg: Subsumsjonslogg?
        ) =
            arbeidsgiverperiode?.forventerInntekt(periode, sykdomstidslinje, subsumsjonslogg) ?: false

        internal fun List<Arbeidsgiverperiode>.finn(periode: Periode) = lastOrNull { arbeidsgiverperiode ->
            periode in arbeidsgiverperiode
        }

        private fun Periode.justerForHelg() = when (endInclusive.dayOfWeek) {
            DayOfWeek.SATURDAY -> start til endInclusive.plusDays(1)
            DayOfWeek.FRIDAY -> start til endInclusive.plusDays(2)
            else -> this
        }

        private fun MutableList<Periode>.add(dagen: LocalDate) {
            if (isNotEmpty() && last().endInclusive.plusDays(1) == dagen) {
                this[size - 1] = last().oppdaterTom(dagen)
            } else {
                add(dagen.somPeriode())
            }
        }

        internal fun harNødvendigeRefusjonsopplysninger(skjæringstidspunkt: LocalDate, periode: Periode, refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger, arbeidsgiverperiode: Arbeidsgiverperiode, hendelse: IAktivitetslogg, organisasjonsnummer: String): Boolean {
            val utbetalingsdager = periode.filter { dag -> arbeidsgiverperiode.utbetalingsdager.any { utbetalingsperiode -> dag in utbetalingsperiode }}
            val førsteUtbetalingsdag = utbetalingsdager.firstOrNull() ?: return true
            val sisteOppholdsdagFørUtbetalingsdager = arbeidsgiverperiode.oppholdsdager.lastOrNull { it.endInclusive < førsteUtbetalingsdag }?.endInclusive ?: arbeidsgiverperiode.firstOrNull()?.forrigeDag
            return refusjonsopplysninger.harNødvendigRefusjonsopplysninger(skjæringstidspunkt, utbetalingsdager, sisteOppholdsdagFørUtbetalingsdager, hendelse, organisasjonsnummer)
        }
    }
}
