package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.deserialisering.RefusjonsopplysningInnDto
import no.nav.helse.dto.deserialisering.RefusjonsopplysningerInnDto
import no.nav.helse.dto.serialisering.RefusjonsopplysningUtDto
import no.nav.helse.dto.serialisering.RefusjonsopplysningerUtDto
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

    private fun trim(periodeSomSkalFjernes: Periode): List<Refusjonsopplysning> {
        return this.periode
            .trim(periodeSomSkalFjernes)
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

    private fun begrensetFra(dato: LocalDate): Refusjonsopplysning? {
        if (periode.endInclusive < dato) return null
        if (periode.start >= dato) return this
        return Refusjonsopplysning(meldingsreferanseId, dato, tom, beløp)
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
        private fun List<Refusjonsopplysning>.mergeInnNyeOpplysninger(nyeOpplysninger: List<Refusjonsopplysning>): List<Refusjonsopplysning> {
            val begrensetFra = minOfOrNull { it.fom } ?: LocalDate.MIN
            return nyeOpplysninger
                .fold(this) { resultat, nyOpplysning -> resultat.mergeInnNyOpplysning(nyOpplysning, begrensetFra) }
                .sortedBy { it.fom }
        }

        private fun List<Refusjonsopplysning>.mergeInnNyOpplysning(nyOpplysning: Refusjonsopplysning, begrensetFra: LocalDate): List<Refusjonsopplysning> {
            // begrenser refusjonsopplysningen slik at den ikke kan strekke tilbake i tid
            val nyOpplysningBegrensetStart = nyOpplysning.begrensetFra(begrensetFra) ?: return this
            // bevarer eksisterende opplysning hvis ny opplysning finnes fra før (dvs. vi bevarer meldingsreferanseId på forrige)
            if (any { it.funksjoneltLik(nyOpplysningBegrensetStart) }) return this
            // Beholder de delene som ikke dekkes av den nye opplysningen og legger til den nye opplysningen
            return flatMap { eksisterendeOpplysning -> eksisterendeOpplysning.trim(nyOpplysningBegrensetStart.periode) }.plus(nyOpplysningBegrensetStart)
        }

        internal fun gjenopprett(dto: RefusjonsopplysningInnDto): Refusjonsopplysning {
            return Refusjonsopplysning(
                meldingsreferanseId = dto.meldingsreferanseId,
                fom = dto.fom,
                tom = dto.tom,
                beløp = Inntekt.gjenopprett(dto.beløp)
            )
        }
    }

    class Refusjonsopplysninger private constructor(
        refusjonsopplysninger: List<Refusjonsopplysning>,
        private val oppholdsdagbegrenser: Oppholdsdagbegrenser = Begrens
    ) {
        private val validerteRefusjonsopplysninger = refusjonsopplysninger.sortedBy { it.fom }

        internal val erTom = validerteRefusjonsopplysninger.isEmpty()
        constructor(): this(emptyList())

        internal fun tidligereBegrenset() = Refusjonsopplysninger(this.validerteRefusjonsopplysninger, TidligereBegrenset)

        init {
            check(!validerteRefusjonsopplysninger.overlapper()) { "Refusjonsopplysninger skal ikke kunne inneholde overlappende informasjon: $refusjonsopplysninger" }
        }

        internal fun lagreTidsnær(førsteFraværsdag: LocalDate, refusjonshistorikk: Refusjonshistorikk) {
            val relevanteRefusjonsopplysninger = validerteRefusjonsopplysninger.filter {
                (it.tom ?: LocalDate.MAX) >= førsteFraværsdag
            }
            if (relevanteRefusjonsopplysninger.isEmpty()) return
            val første = relevanteRefusjonsopplysninger.first()
            val endringerIRefusjon = relevanteRefusjonsopplysninger.drop(1).map { refusjonsopplysning ->
                Refusjonshistorikk.Refusjon.EndringIRefusjon(
                    endringsdato = refusjonsopplysning.fom,
                    beløp = refusjonsopplysning.beløp
                )
            }

            val refusjon = Refusjonshistorikk.Refusjon(
                meldingsreferanseId = første.meldingsreferanseId,
                førsteFraværsdag = førsteFraværsdag,
                arbeidsgiverperioder = emptyList(),
                beløp = første.beløp,
                sisteRefusjonsdag = null,
                endringerIRefusjon = endringerIRefusjon
            )
            refusjonshistorikk.leggTilRefusjon(refusjon)
        }

        internal fun accept(visitor: RefusjonsopplysningerVisitor) {
            visitor.preVisitRefusjonsopplysninger(this)
            validerteRefusjonsopplysninger.forEach { it.accept(visitor) }
            visitor.postVisitRefusjonsopplysninger(this)
        }

        internal fun merge(other: Refusjonsopplysninger): Refusjonsopplysninger {
            return Refusjonsopplysninger(validerteRefusjonsopplysninger.mergeInnNyeOpplysninger(other.validerteRefusjonsopplysninger))
        }

        internal fun gjenoppliv(nyttSkjæringstidspunkt: LocalDate): Refusjonsopplysninger {
            val gammelSnute = validerteRefusjonsopplysninger.firstOrNull { it.dekker(nyttSkjæringstidspunkt) } ?: validerteRefusjonsopplysninger.firstOrNull() ?: return this
            val nySnute = Refusjonsopplysning(gammelSnute.meldingsreferanseId, nyttSkjæringstidspunkt, null, gammelSnute.beløp)
            return nySnute.refusjonsopplysninger.merge(this)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Refusjonsopplysninger) return false
            return validerteRefusjonsopplysninger == other.validerteRefusjonsopplysninger
        }


        override fun hashCode() = validerteRefusjonsopplysninger.hashCode()

        override fun toString() = validerteRefusjonsopplysninger.toString()

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
        ) = oppholdsdagbegrenser.begrens(this, sisteOppholdsdagFørUtbetalingsdager).harNødvendigRefusjonsopplysninger(skjæringstidspunkt,utbetalingsdager, hendelse,organisasjonsnummer)

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

        internal fun overlappendeEllerSenereRefusjonsopplysninger(periode: Periode?): List<Refusjonsopplysning> {
            if (periode == null) return emptyList()
            return validerteRefusjonsopplysninger.filter {
                val refusjonsperiode = Periode(it.fom, it.tom ?: LocalDate.MAX)
                periode.overlapperMed(refusjonsperiode) || refusjonsperiode.starterEtter(periode)
            }
        }

        internal companion object {
            private fun List<Refusjonsopplysning>.overlapper() = map { it.periode }.overlapper()
            internal fun List<Refusjonsopplysning>.gjennopprett() = Refusjonsopplysninger(this)
            internal val Refusjonsopplysning.refusjonsopplysninger get() = Refusjonsopplysninger(listOf(this))

            internal fun gjenopprett(dto: RefusjonsopplysningerInnDto) = Refusjonsopplysninger(
                refusjonsopplysninger = dto.opplysninger.map { gjenopprett(it) }
            )
        }

        class RefusjonsopplysningerBuilder {
            private val refusjonsopplysninger = mutableListOf<Pair<LocalDateTime, Refusjonsopplysning>>()
            fun leggTil(refusjonsopplysning: Refusjonsopplysning, tidsstempel: LocalDateTime) = apply {
                refusjonsopplysninger.add(tidsstempel to refusjonsopplysning)
            }

            private fun sorterteRefusjonsopplysninger() = refusjonsopplysninger
                .sortedWith(compareBy({ (tidsstempel, _) -> tidsstempel }, { (_, refusjonsopplysning) -> refusjonsopplysning.fom }))
                .map { (_, refusjonsopplysning) -> refusjonsopplysning }

            fun build() = Refusjonsopplysninger(emptyList<Refusjonsopplysning>().mergeInnNyeOpplysninger(sorterteRefusjonsopplysninger()))
        }

        internal fun dto() = RefusjonsopplysningerUtDto(
            opplysninger = this.validerteRefusjonsopplysninger.map { it.dto() }
        )

        private sealed interface Oppholdsdagbegrenser {
            fun begrens(refusjonsopplysninger: Refusjonsopplysninger, sisteOppholdsdagFørUtbetalingsdager: LocalDate?): Refusjonsopplysninger
        }

        private data object Begrens: Oppholdsdagbegrenser {
            override fun begrens(refusjonsopplysninger: Refusjonsopplysninger, sisteOppholdsdagFørUtbetalingsdager: LocalDate?): Refusjonsopplysninger {
                if (sisteOppholdsdagFørUtbetalingsdager == null) return refusjonsopplysninger
                val begrensedeRefusjonsopplysninger = refusjonsopplysninger.validerteRefusjonsopplysninger.mapNotNull { it.begrensTil(sisteOppholdsdagFørUtbetalingsdager )}
                return Refusjonsopplysninger(begrensedeRefusjonsopplysninger)
            }
        }

        private data object TidligereBegrenset: Oppholdsdagbegrenser {
            override fun begrens(refusjonsopplysninger: Refusjonsopplysninger, sisteOppholdsdagFørUtbetalingsdager: LocalDate?) = refusjonsopplysninger
        }
    }

    internal fun dto() = RefusjonsopplysningUtDto(
        meldingsreferanseId = this.meldingsreferanseId,
        fom = this.fom,
        tom = this.tom,
        beløp = this.beløp.dto()
    )
}