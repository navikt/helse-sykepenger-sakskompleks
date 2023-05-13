package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.Periode.Companion.omsluttendePeriode
import no.nav.helse.hendelser.Periode.Companion.overlapper
import no.nav.helse.hendelser.til
import no.nav.helse.person.RefusjonsopplysningerVisitor
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.økonomi.Inntekt

class Refusjonsopplysning(
    private val meldingsreferanseId: UUID,
    private val fom: LocalDate,
    private val tom: LocalDate?,
    private val beløp: Inntekt
) {
    init {
        check(tom == null || tom <= tom) { "fom ($fom) kan ikke være etter tom ($tom) "}
    }

    private val periode = fom til (tom ?: LocalDate.MAX)

    private fun merge(other: List<Refusjonsopplysning>): List<Refusjonsopplysning> {
        if (other.isEmpty()) return listOf(this)
        val førsteFom = other.minBy { it.fom }.fom
        // begrenser refusjonsopplysningen slik at den ikke kan strekke tilbake i tid
        if (fom < førsteFom) return begrensTil(førsteFom, other)
        // bevarer eksisterende opplysning hvis *this* finnes fra før (dvs. vi bevarer meldingsreferanseId på forrige)
        if (other.any(::funksjoneltLik)) return other
        return other
            .flatMap { it.merge(this) }
            .plusElement(this)
    }

    private fun begrensTil(førsteFom: LocalDate, other: List<Refusjonsopplysning>): List<Refusjonsopplysning> {
        if (periode.endInclusive < førsteFom) return other
        return Refusjonsopplysning(meldingsreferanseId, førsteFom, tom, beløp).merge(other)
    }

    private fun merge(ny: Refusjonsopplysning): List<Refusjonsopplysning> {
        return this.periode
            .trim(ny.periode)
            .map {
                Refusjonsopplysning(
                    fom = it.start,
                    tom = it.endInclusive.takeUnless { tom -> tom == LocalDate.MAX },
                    beløp = this.beløp,
                    meldingsreferanseId = this.meldingsreferanseId
                )
            }
    }

    internal fun fom() = fom
    internal fun tom() = tom
    internal fun beløp() = beløp

    private fun oppdatertTom(nyTom: LocalDate) = if (nyTom < fom) null else Refusjonsopplysning(meldingsreferanseId, fom, nyTom, beløp)

    private fun begrensTil(dato: LocalDate): Refusjonsopplysning? {
        return if (dekker(dato)) oppdatertTom(dato.forrigeDag)
        else this
    }

    private fun dekker(dag: LocalDate) = dag in periode

    private fun aksepterer(skjæringstidspunkt: LocalDate, dag: LocalDate) =
        dag >= skjæringstidspunkt && dag < fom

    internal fun accept(visitor: RefusjonsopplysningerVisitor) {
        visitor.visitRefusjonsopplysning(meldingsreferanseId, fom, tom, beløp)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Refusjonsopplysning) return false
        if (fom != other.fom) return false
        if (tom != other.tom) return false
        if (beløp != other.beløp) return false
        return meldingsreferanseId == other.meldingsreferanseId
    }

    private fun funksjoneltLik(other: Refusjonsopplysning) =
        this.periode == other.periode && this.beløp == other.beløp

    override fun toString() = "$periode, ${beløp.reflection { _, _, daglig, _ -> daglig }} ($meldingsreferanseId)"

    override fun hashCode(): Int {
        var result = periode.hashCode()
        result = 31 * result + beløp.hashCode()
        result = 31 * result + meldingsreferanseId.hashCode()
        return result
    }

    internal companion object {
        private fun List<Refusjonsopplysning>.merge(nyeOpplysninger: List<Refusjonsopplysning>): List<Refusjonsopplysning> {
            return nyeOpplysninger
                .fold(this) { result, opplysning -> opplysning.merge(result) }
                .sortedBy { it.fom }
        }
    }

    class Refusjonsopplysninger private constructor(
        refusjonsopplysninger: List<Refusjonsopplysning>
    ) {
        private val validerteRefusjonsopplysninger = refusjonsopplysninger.sortedBy { it.fom }

        internal val erTom = validerteRefusjonsopplysninger.isEmpty()
        internal constructor(): this(emptyList())

        init {
            check(!validerteRefusjonsopplysninger.overlapper()) { "Refusjonsopplysninger skal ikke kunne inneholde overlappende informasjon: $refusjonsopplysninger" }
        }

        internal fun lagreTidsnær(førsteFraværsdag: LocalDate, refusjonshistorikk: Refusjonshistorikk) {
            if (validerteRefusjonsopplysninger.isEmpty()) return
            val første = validerteRefusjonsopplysninger.first()
            val sisteRefusjonsdag = validerteRefusjonsopplysninger.last().tom
            val endringerIRefusjon = validerteRefusjonsopplysninger.map { refusjonsopplysning ->
                Refusjonshistorikk.Refusjon.EndringIRefusjon(
                    endringsdato = refusjonsopplysning.fom,
                    beløp = refusjonsopplysning.beløp
                )
            }

            val refusjon = Refusjonshistorikk.Refusjon(første.meldingsreferanseId, førsteFraværsdag, emptyList(), første.beløp, sisteRefusjonsdag, endringerIRefusjon)
            refusjonshistorikk.leggTilRefusjon(refusjon)
        }

        internal fun accept(visitor: RefusjonsopplysningerVisitor) {
            visitor.preVisitRefusjonsopplysninger(this)
            validerteRefusjonsopplysninger.forEach { it.accept(visitor) }
            visitor.postVisitRefusjonsopplysninger(this)
        }

        internal fun merge(other: Refusjonsopplysninger): Refusjonsopplysninger {
            return Refusjonsopplysninger(validerteRefusjonsopplysninger.merge(other.validerteRefusjonsopplysninger))
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Refusjonsopplysninger) return false
            return validerteRefusjonsopplysninger == other.validerteRefusjonsopplysninger
        }

        override fun hashCode() = validerteRefusjonsopplysninger.hashCode()

        override fun toString() = validerteRefusjonsopplysninger.toString()

        private fun hensyntattSisteOppholdagFørUtbetalingsdager(sisteOppholdsdagFørUtbetalingsdager: LocalDate?) = when (sisteOppholdsdagFørUtbetalingsdager) {
            null -> this
            else -> Refusjonsopplysninger(validerteRefusjonsopplysninger.mapNotNull { it.begrensTil(sisteOppholdsdagFørUtbetalingsdager )})
        }

        private fun harNødvendigRefusjonsopplysninger(
            skjæringstidspunkt: LocalDate,
            utbetalingsdager: List<LocalDate>,
            hendelse: IAktivitetslogg,
            organisasjonsnummer: String
        ): Boolean {
            val førsteRefusjonsopplysning = førsteRefusjonsopplysning() ?: return false.also {
                hendelse.info("Mangler refusjonsopplysninger på orgnummer $organisasjonsnummer for hele perioden (${utbetalingsdager.omsluttendePeriode})")
            }
            val dekkes = utbetalingsdager.filter { utbetalingsdag -> dekker(utbetalingsdag) }
            val aksepteres = utbetalingsdager.filter { utbetalingsdag -> førsteRefusjonsopplysning.aksepterer(skjæringstidspunkt, utbetalingsdag) }
            val mangler = (utbetalingsdager - dekkes - aksepteres).takeUnless { it.isEmpty() } ?: return true
            hendelse.info("Mangler refusjonsopplysninger på orgnummer $organisasjonsnummer for periodene ${mangler.grupperSammenhengendePerioder()}")
            return false
        }

        internal fun harNødvendigRefusjonsopplysninger(
            skjæringstidspunkt: LocalDate,
            utbetalingsdager: List<LocalDate>,
            sisteOppholdsdagFørUtbetalingsdager: LocalDate?,
            hendelse: IAktivitetslogg,
            organisasjonsnummer: String
        ) = hensyntattSisteOppholdagFørUtbetalingsdager(sisteOppholdsdagFørUtbetalingsdager).harNødvendigRefusjonsopplysninger(skjæringstidspunkt, utbetalingsdager, hendelse, organisasjonsnummer)
        internal fun refusjonsbeløpOrNull(dag: LocalDate) = validerteRefusjonsopplysninger.singleOrNull { it.dekker(dag) }?.beløp

        private fun førsteRefusjonsopplysning() = validerteRefusjonsopplysninger.minByOrNull { it.fom }

        private fun dekker(dag: LocalDate) = validerteRefusjonsopplysninger.any { it.dekker(dag) }

        // finner første dato hvor refusjonsbeløpet for dagen er ulikt beløpet i forrige versjon
        internal fun finnFørsteDatoForEndring(other: Refusjonsopplysninger): LocalDate? {
            // finner alle nye
            val nye = this.validerteRefusjonsopplysninger.filter { opplysning ->
                other.validerteRefusjonsopplysninger.none { it.meldingsreferanseId == opplysning.meldingsreferanseId }
            }
            // fjerner de hvor perioden og beløpet dekkes av forrige
            val nyeUlik = nye.filterNot { opplysning -> other.validerteRefusjonsopplysninger.any {
                opplysning.periode in it.periode && opplysning.beløp == it.beløp
            } }
            // første nye ulike opplysning eller bare første nye opplysning
            return nyeUlik.firstOrNull()?.fom ?: nye.firstOrNull()?.fom
        }

        internal fun overlappendeEllerSenereRefusjonsopplysninger(periode: Periode): List<Refusjonsopplysning> =
            validerteRefusjonsopplysninger.filter {
                val refusjonsperiode = Periode(it.fom, it.tom ?: LocalDate.MAX)
                periode.overlapperMed(refusjonsperiode) || refusjonsperiode.starterEtter(periode)
            }

        internal companion object {
            private fun List<Refusjonsopplysning>.overlapper() = map { it.periode }.overlapper()
            internal fun List<Refusjonsopplysning>.gjennopprett() = Refusjonsopplysninger(this)
            internal val Refusjonsopplysning.refusjonsopplysninger get() = Refusjonsopplysninger(listOf(this))
        }

        class RefusjonsopplysningerBuilder {
            private val refusjonsopplysninger = mutableListOf<Pair<LocalDateTime, Refusjonsopplysning>>()
            fun leggTil(refusjonsopplysning: Refusjonsopplysning, tidsstempel: LocalDateTime) = apply {
                refusjonsopplysninger.add(tidsstempel to refusjonsopplysning)
            }

            private fun sorterteRefusjonsopplysninger() = refusjonsopplysninger.sortedWith(compareBy({ it.second.fom }, { it.first })).map { it.second }

            fun build() = Refusjonsopplysninger(emptyList<Refusjonsopplysning>().merge(sorterteRefusjonsopplysninger()))
        }
    }

}

typealias ManglerRefusjonsopplysning = (LocalDate, Inntekt) -> Unit