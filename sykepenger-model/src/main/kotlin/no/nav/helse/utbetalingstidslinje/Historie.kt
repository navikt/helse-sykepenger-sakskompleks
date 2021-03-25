package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.person.Inntektshistorikk
import no.nav.helse.person.Periodetype
import no.nav.helse.person.Periodetype.*
import no.nav.helse.person.Person
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.sykdomstidslinje.erHelg
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal class Historie() {

    internal constructor(person: Person) : this() {
        person.append(spleisbøtte)
    }

    internal constructor(person: Person, infotrygdhistorikk: Infotrygdhistorikk) : this(person) {
        infotrygdhistorikk.append(infotrygdbøtte)
    }

    private val infotrygdbøtte = Historikkbøtte()
    private val spleisbøtte = Historikkbøtte(konverterUtbetalingstidslinje = true)
    private val sykdomstidslinjer get() = infotrygdbøtte.sykdomstidslinjer() + spleisbøtte.sykdomstidslinjer()

    internal fun periodetype(orgnr: String, periode: Periode): Periodetype {
        return beregnPeriodetype(orgnr, periode).somPeriodetype()
    }

    private fun beregnPeriodetype(orgnr: String, periode: Periode): InternPeriodetype {
        val skjæringstidspunkt = skjæringstidspunkt(orgnr, periode)
        return when {
            skjæringstidspunkt in periode -> InternPeriodetype.PERIODE_MED_SKJÆRINGSTIDSPUNKT
            infotrygdbøtte.erUtbetaltDag(orgnr, skjæringstidspunkt) -> {
                val sammenhengendePeriode = Periode(skjæringstidspunkt, periode.start.minusDays(1))
                when {
                    spleisbøtte.harOverlappendeHistorikk(orgnr, sammenhengendePeriode) -> InternPeriodetype.FORLENGELSE_MED_OPPHAV_I_INFOTRYGD
                    else -> InternPeriodetype.FØRSTE_PERIODE_SOM_FORLENGER_INFOTRYGD
                }
            }
            !spleisbøtte.erUtbetaltDag(orgnr, skjæringstidspunkt) -> InternPeriodetype.PERIODE_MED_FØRSTE_UTBETALING
            else -> InternPeriodetype.FORLENGELSE_MED_OPPHAV_I_SPLEIS
        }
    }

    internal fun beregnUtbetalingstidslinje(
        organisasjonsnummer: String,
        periode: Periode,
        inntektshistorikk: Inntektshistorikk,
        arbeidsgiverRegler: ArbeidsgiverRegler
    ): Utbetalingstidslinje {
        val builder = UtbetalingstidslinjeBuilder(
            skjæringstidspunkter = skjæringstidspunkter(periode),
            inntektshistorikk = inntektshistorikk,
            forlengelseStrategy = { dagen -> erArbeidsgiverperiodenGjennomførtFør(organisasjonsnummer, dagen) },
            arbeidsgiverRegler = arbeidsgiverRegler
        )
        val utbetalingstidlinje = builder.result(sykdomstidslinje(organisasjonsnummer).fremTilOgMed(periode.endInclusive))
        return fjernInfotrygd(utbetalingstidlinje, organisasjonsnummer)
    }

    private fun fjernInfotrygd(utbetalingstidlinje: Utbetalingstidslinje, organisasjonsnummer: String): Utbetalingstidslinje {
        val tidligsteDato = spleisbøtte.tidligsteDato(organisasjonsnummer)
        return utbetalingstidlinje.plus(infotrygdbøtte.utbetalingstidslinje(organisasjonsnummer)) { spleisDag: Utbetalingsdag, infotrygdDag: Utbetalingsdag ->
            when {
                // fjerner ledende dager
                spleisDag.dato < tidligsteDato -> UkjentDag(spleisDag.dato, spleisDag.økonomi)
                // fjerner utbetalinger i ukedager (bevarer fridager)
                !infotrygdDag.dato.erHelg() && infotrygdDag is NavDag -> UkjentDag(spleisDag.dato, spleisDag.økonomi)
                // fjerner utbetalinger i helger (bevarer fridager)
                infotrygdDag.dato.erHelg() && infotrygdDag !is Fridag -> UkjentDag(spleisDag.dato, spleisDag.økonomi)
                else -> spleisDag
            }
        }
    }

    internal fun erForlengelse(orgnr: String, periode: Periode) =
        beregnPeriodetype(orgnr, periode).erForlengelse()

    internal fun forlengerInfotrygd(orgnr: String, periode: Periode) =
        beregnPeriodetype(orgnr, periode).opphavInfotrygd()

    internal fun avgrensetPeriode(orgnr: String, periode: Periode) =
        Periode(maxOf(periode.start, skjæringstidspunkt(orgnr, periode)), periode.endInclusive)

    internal fun skjæringstidspunkt(periode: Periode) =
        Sykdomstidslinje.skjæringstidspunkt(periode.endInclusive, sykdomstidslinjer)

    private fun skjæringstidspunkt(orgnr: String, periode: Periode) =
        sykdomstidslinje(orgnr).skjæringstidspunkt(periode.endInclusive) ?: periode.start

    internal fun skjæringstidspunkter(periode: Periode) =
        skjæringstidspunkter(periode.endInclusive)

    private fun skjæringstidspunkter(kuttdato: LocalDate) =
        Sykdomstidslinje.skjæringstidspunkter(kuttdato, sykdomstidslinjer)

    internal fun utbetalingstidslinjeFraInfotrygd(periode: Periode) =
        infotrygdbøtte.utbetalingstidslinje().kutt(periode.endInclusive)

    internal fun add(orgnummer: String, tidslinje: Utbetalingstidslinje) {
        spleisbøtte.add(orgnummer, tidslinje)
    }

    internal fun add(orgnummer: String, tidslinje: Sykdomstidslinje) {
        spleisbøtte.add(orgnummer, tidslinje)
    }

    internal fun forrigeSkjæringstidspunktInnenforArbeidsgiverperioden(regler: ArbeidsgiverRegler, orgnummer: String, nyFørsteSykedag: LocalDate): LocalDate? {
        val sykdomstidslinje = sykdomstidslinje(orgnummer)
        if (sykdomstidslinje.harNyArbeidsgiverperiodeFør(regler, nyFørsteSykedag)) return null
        return sykdomstidslinje.skjæringstidspunkt(nyFørsteSykedag.minusDays(1))
    }

    private fun erArbeidsgiverperiodenGjennomførtFør(organisasjonsnummer: String, dagen: LocalDate): Boolean {
        if (infotrygdbøtte.erUtbetaltDag(organisasjonsnummer, dagen)) return true
        val skjæringstidspunkt = skjæringstidspunkt(organisasjonsnummer, dagen til dagen)
        if (skjæringstidspunkt == dagen) return false
        if (infotrygdbøtte.erUtbetaltDag(organisasjonsnummer, skjæringstidspunkt)) return true
        return spleisbøtte.erUtbetaltDag(organisasjonsnummer, skjæringstidspunkt)
    }

    private fun sykdomstidslinje(orgnummer: String) =
        infotrygdbøtte.sykdomstidslinje(orgnummer).merge(spleisbøtte.sykdomstidslinje(orgnummer), replace)

    internal fun harForlengelseForAlleArbeidsgivereIInfotrygdhistorikken(
        orgnummerForOverlappendeVedtaksperioder: List<String>,
        skjæringstidspunkt: LocalDate
    ) = infotrygdbøtte.harForlengelseForAlleArbeidsgivereIInfotrygdhistorikken(orgnummerForOverlappendeVedtaksperioder, skjæringstidspunkt)

    internal companion object {
        private const val ALLE_ARBEIDSGIVERE = "UKJENT"
        private fun Utbetalingsdag.erSykedag() =
            this is NavDag || this is NavHelgDag || this is ArbeidsgiverperiodeDag || this is AvvistDag
    }

    internal class Historikkbøtte(private val konverterUtbetalingstidslinje: Boolean = false) {
        private val utbetalingstidslinjer = mutableMapOf<String, Utbetalingstidslinje>()
        private val sykdomstidslinjer = mutableMapOf<String, Sykdomstidslinje>()

        internal fun tidligsteDato(orgnummer: String): LocalDate {
            val førsteUtbetalingsdag = utbetalingstidslinje(orgnummer).sykepengeperiode()?.start
            val førsteSykdomstidslinjedag = sykdomstidslinje(orgnummer).periode()?.start
            return listOfNotNull(førsteUtbetalingsdag, førsteSykdomstidslinjedag).minOrNull()
                ?: throw IllegalArgumentException("Finner ingen første dag! Både sykdomstidslinjen og historiske utbetalinger er helt tom")
        }

        internal fun sykdomstidslinjer() = sykdomstidslinjer.values.toList()
        internal fun sykdomstidslinje(orgnummer: String) =
            sykdomstidslinjer.getOrElse(ALLE_ARBEIDSGIVERE) { Sykdomstidslinje() }.merge(
                sykdomstidslinjer.getOrElse(orgnummer) { Sykdomstidslinje() }, replace
            )

        internal fun utbetalingstidslinje() =
            utbetalingstidslinjer.values.fold(Utbetalingstidslinje(), Utbetalingstidslinje::plus)

        internal fun utbetalingstidslinje(orgnummer: String) =
            utbetalingstidslinjer.getOrElse(ALLE_ARBEIDSGIVERE) { Utbetalingstidslinje() } +
                utbetalingstidslinjer.getOrElse(orgnummer) { Utbetalingstidslinje() }

        internal fun add(orgnummer: String = ALLE_ARBEIDSGIVERE, tidslinje: Utbetalingstidslinje) {
            utbetalingstidslinjer.merge(orgnummer, tidslinje, Utbetalingstidslinje::plus)
            if (konverterUtbetalingstidslinje) {
                // for å ta høyde for forkastet historikk, men ved overlapp vinner den eksisterende historikken
                val eksisterende = sykdomstidslinjer[orgnummer]
                add(orgnummer, konverter(tidslinje))
                if (eksisterende != null) add(orgnummer, eksisterende)
            }
        }

        internal fun add(orgnummer: String = ALLE_ARBEIDSGIVERE, tidslinje: Sykdomstidslinje) {
            sykdomstidslinjer.merge(orgnummer, tidslinje) { venstre, høyre -> venstre.merge(høyre, replace) }
        }

        internal fun harOverlappendeHistorikk(orgnr: String, periode: Periode) =
            sykdomstidslinje(orgnr).subset(periode).any { it !is UkjentDag }

        internal fun erUtbetaltDag(orgnr: String, dato: LocalDate) =
            utbetalingstidslinje(orgnr)[dato].erSykedag()

        private fun orgnummerMedOverlappendeSykdomstidlinje(skjæringstidspunkt: LocalDate) =
            sykdomstidslinjer.filter { (_, sykdomstidlinje) -> sykdomstidlinje.førsteSykedagEtter(skjæringstidspunkt) != null }.keys

        internal fun harForlengelseForAlleArbeidsgivereIInfotrygdhistorikken(
            orgnummerForOverlappendeVedtaksperioder: List<String>,
            skjæringstidspunkt: LocalDate
        ) = orgnummerForOverlappendeVedtaksperioder.containsAll(orgnummerMedOverlappendeSykdomstidlinje(skjæringstidspunkt))


        internal companion object {
            internal fun konverter(utbetalingstidslinje: Utbetalingstidslinje) =
                utbetalingstidslinje
                    .mapNotNull {
                        when {
                            !it.dato.erHelg() && it.erSykedag() -> Dag.Sykedag(it.dato, it.økonomi.medGrad(), INGEN)
                            !it.dato.erHelg() && it is Fridag -> Dag.Feriedag(it.dato, INGEN)
                            it.dato.erHelg() && it.erSykedag() -> Dag.SykHelgedag(it.dato, it.økonomi.medGrad(), INGEN)
                            it is Arbeidsdag -> Dag.Arbeidsdag(it.dato, INGEN)
                            it is ForeldetDag -> Dag.ForeldetSykedag(it.dato, it.økonomi.medGrad(), INGEN)
                            else -> null
                        }?.let { sykedag -> it.dato to sykedag }
                    }
                    .toMap()
                    .let(::Sykdomstidslinje)

            private fun Økonomi.medGrad() = Økonomi.sykdomsgrad(reflection { grad, _, _, _, _, _, _, _, _ -> grad }.prosent)
        }
    }

    private enum class InternPeriodetype(private val periodetype: Periodetype) {
        PERIODE_MED_SKJÆRINGSTIDSPUNKT(FØRSTEGANGSBEHANDLING),
        PERIODE_MED_FØRSTE_UTBETALING(FØRSTEGANGSBEHANDLING),
        FORLENGELSE_MED_OPPHAV_I_SPLEIS(FORLENGELSE),
        FØRSTE_PERIODE_SOM_FORLENGER_INFOTRYGD(OVERGANG_FRA_IT),
        FORLENGELSE_MED_OPPHAV_I_INFOTRYGD(INFOTRYGDFORLENGELSE);

        fun opphavInfotrygd() = this in listOf(FORLENGELSE_MED_OPPHAV_I_INFOTRYGD, FØRSTE_PERIODE_SOM_FORLENGER_INFOTRYGD)
        fun erForlengelse() = this != PERIODE_MED_SKJÆRINGSTIDSPUNKT
        fun somPeriodetype() = periodetype
    }
}
