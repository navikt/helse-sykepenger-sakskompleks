package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.nesteDag
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.utbetalingslinjer.Oppdrag.Companion.periode
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.aktive
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag

class Utbetalingkladd(
    private val periode: Periode,
    private val arbeidsgiveroppdrag: Oppdrag,
    private val personoppdrag: Oppdrag
) {
    fun overlapperMed(other: Periode) = other.overlapperMed(this.periode)
    fun lagUtbetaling(
        type: Utbetalingtype,
        korrelerendeUtbetaling: Utbetaling?,
        beregningId: UUID,
        utbetalingstidslinje: Collection<Utbetalingsdag>,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        annulleringer: List<Utbetaling> = emptyList()
    ): Utbetaling {
        return Utbetaling(
            beregningId = beregningId,
            korrelerendeUtbetaling = korrelerendeUtbetaling,
            periode = periode,
            utbetalingstidslinje = utbetalingstidslinje.filter { it.dato <= periode.endInclusive },
            arbeidsgiverOppdrag = arbeidsgiveroppdrag,
            personOppdrag = personoppdrag,
            type = type,
            maksdato = maksdato,
            forbrukteSykedager = forbrukteSykedager,
            gjenståendeSykedager = gjenståendeSykedager,
            annulleringer = annulleringer
        )
    }

    fun forrigeUtbetalte(utbetalinger: List<Utbetaling>): List<Utbetaling> {
        // fordi arbeidsgiverperioder kan slås sammen eller splittes opp så kan det hende vi finner
        // mer enn én utbetaling som dekker perioden vår
        return utbetalinger.aktive(periode)
    }

    fun opphørerHale(other: Periode) = this.periode.endInclusive < other.endInclusive
    fun arbeidsgiveroppdrag(other: Oppdrag, aktivitetslogg: IAktivitetslogg) =
        this.arbeidsgiveroppdrag.minus(other, aktivitetslogg)

    fun personoppdrag(other: Oppdrag, aktivitetslogg: IAktivitetslogg) =
        this.personoppdrag.minus(other, aktivitetslogg)

    fun begrensTil(other: Periode): Utbetalingkladd {
        return Utbetalingkladd(
            periode = periode.oppdaterFom(other).start til other.endInclusive,
            arbeidsgiveroppdrag = this.arbeidsgiveroppdrag.begrensTil(other.endInclusive),
            personoppdrag = this.personoppdrag.begrensTil(other.endInclusive)
        )
    }

    // hensyntar bare endringer frem til og med angitt dato
    fun begrensTil(aktivitetslogg: IAktivitetslogg, other: Periode, tidligereArbeidsgiveroppdrag: Oppdrag, tidligerePersonoppdrag: Oppdrag): Utbetalingkladd {
        return begrensTil(other)
            .diffMotTidligere(aktivitetslogg, tidligereArbeidsgiveroppdrag, tidligerePersonoppdrag)
    }

    // hensyntar bare endringer frem til og med angitt dato,
    // og kopierer evt. senere linjer fra tidligere oppdrag
    fun begrensTilOgKopier(
        aktivitetslogg: IAktivitetslogg,
        other: Periode,
        tidligereArbeidsgiveroppdrag: Oppdrag,
        tidligerePersonoppdrag: Oppdrag
    ): Utbetalingkladd {
        return begrensTil(other)
            .kopierArbeidsgiveroppdrag(tidligereArbeidsgiveroppdrag)
            .kopierPersonoppdrag(tidligerePersonoppdrag)
            .diffMotTidligere(aktivitetslogg, tidligereArbeidsgiveroppdrag, tidligerePersonoppdrag)
    }

    private fun diffMotTidligere(aktivitetslogg: IAktivitetslogg, tidligereArbeidsgiveroppdrag: Oppdrag, tidligerePersonoppdrag: Oppdrag) =
        Utbetalingkladd(
            periode = this.periode,
            arbeidsgiveroppdrag = this.arbeidsgiveroppdrag.minus(tidligereArbeidsgiveroppdrag, aktivitetslogg),
            personoppdrag = this.personoppdrag.minus(tidligerePersonoppdrag, aktivitetslogg)
        )

    private fun kopierArbeidsgiveroppdrag(tidligereArbeidsgiveroppdrag: Oppdrag) =
        Utbetalingkladd(
            periode = this.periode,
            arbeidsgiveroppdrag = this.arbeidsgiveroppdrag + tidligereArbeidsgiveroppdrag.begrensFra(this.periode.endInclusive.nesteDag),
            personoppdrag = this.personoppdrag
        )

    private fun kopierPersonoppdrag(tidligerePersonoppdrag: Oppdrag) =
        Utbetalingkladd(
            periode = this.periode,
            arbeidsgiveroppdrag = this.arbeidsgiveroppdrag,
            personoppdrag = this.personoppdrag + tidligerePersonoppdrag.begrensFra(this.periode.endInclusive.nesteDag)
        )

    companion object {
        fun List<Utbetalingkladd>.finnKladd(periode: Periode): List<Utbetalingkladd> {
            val kladdene = filter { kladd -> kladd.overlapperMed(periode) }
            val medUtbetaling = kladdene
                .filter { kladd -> kladd.arbeidsgiveroppdrag.isNotEmpty() || kladd.personoppdrag.isNotEmpty() }
                .filterNot { kladd ->
                    val oppdragsperiode = periode(kladd.arbeidsgiveroppdrag, kladd.personoppdrag)
                    oppdragsperiode != null && oppdragsperiode.endInclusive < periode.start
                }
            if (kladdene.size <= 1 || medUtbetaling.isEmpty()) return kladdene.take(1)
            return medUtbetaling
        }
    }
}