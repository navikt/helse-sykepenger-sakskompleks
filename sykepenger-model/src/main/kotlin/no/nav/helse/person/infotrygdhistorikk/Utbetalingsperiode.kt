package no.nav.helse.person.infotrygdhistorikk

import java.time.LocalDate
import no.nav.helse.dto.deserialisering.InfotrygdArbeidsgiverutbetalingsperiodeInnDto
import no.nav.helse.dto.deserialisering.InfotrygdPersonutbetalingsperiodeInnDto
import no.nav.helse.dto.serialisering.InfotrygdArbeidsgiverutbetalingsperiodeUtDto
import no.nav.helse.dto.serialisering.InfotrygdPersonutbetalingsperiodeUtDto
import no.nav.helse.erHelg
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi

sealed class Utbetalingsperiode(
    val orgnr: String,
    fom: LocalDate,
    tom: LocalDate,
    val grad: Prosentdel,
    val inntekt: Inntekt
) : Infotrygdperiode(fom, tom) {
    companion object {
        // inntektbeløpet i Infotrygd-utbetalingene er gradert; justerer derfor "opp igjen"
        fun inntekt(inntekt: Inntekt, grad: Prosentdel) = Inntekt.fraGradert(inntekt, grad)
    }
    override fun sykdomstidslinje(kilde: SykdomshistorikkHendelse.Hendelseskilde): Sykdomstidslinje {
        return Sykdomstidslinje.sykedager(periode.start, periode.endInclusive, grad, kilde)
    }

    override fun utbetalingstidslinje() =
        Utbetalingstidslinje.Builder().apply {
            periode.forEach { dag -> nyDag(this, dag) }
        }.build()

    private fun nyDag(builder: Utbetalingstidslinje.Builder, dato: LocalDate) {
        val økonomi = Økonomi.sykdomsgrad(grad)
        if (dato.erHelg()) return builder.addHelg(dato, økonomi.inntekt(INGEN, `6G` = INGEN, refusjonsbeløp = INGEN))
        builder.addNAVdag(dato, økonomi.inntekt(inntekt, `6G` = INGEN, refusjonsbeløp = INGEN))
    }

    override fun gjelder(orgnummer: String) = orgnummer == this.orgnr

    override fun funksjoneltLik(other: Infotrygdperiode): Boolean {
        if (!super.funksjoneltLik(other)) return false
        other as Utbetalingsperiode
        return this.orgnr == other.orgnr && this.periode.start == other.periode.start && this.grad == other.grad && this.inntekt == other.inntekt
    }
}

class ArbeidsgiverUtbetalingsperiode(orgnr: String, fom: LocalDate, tom: LocalDate, grad: Prosentdel, inntekt: Inntekt) :
    Utbetalingsperiode(orgnr, fom, tom, grad, inntekt) {

    internal fun dto() = InfotrygdArbeidsgiverutbetalingsperiodeUtDto(
        orgnr = orgnr,
        periode = periode.dto(),
        grad = grad.dto(),
        inntekt = inntekt.dto()
    )

    internal companion object {
        internal fun gjenopprett(dto: InfotrygdArbeidsgiverutbetalingsperiodeInnDto): ArbeidsgiverUtbetalingsperiode {
            val periode = Periode.gjenopprett(dto.periode)
            return ArbeidsgiverUtbetalingsperiode(
                orgnr = dto.orgnr,
                fom = periode.start,
                tom = periode.endInclusive,
                grad = Prosentdel.gjenopprett(dto.grad),
                inntekt = Inntekt.gjenopprett(dto.inntekt)
            )
        }
    }
}

class PersonUtbetalingsperiode(orgnr: String, fom: LocalDate, tom: LocalDate, grad: Prosentdel, inntekt: Inntekt) :
    Utbetalingsperiode(orgnr, fom, tom, grad, inntekt) {

    internal fun dto() = InfotrygdPersonutbetalingsperiodeUtDto(
        orgnr = orgnr,
        periode = periode.dto(),
        grad = grad.dto(),
        inntekt = inntekt.dto()
    )

    internal companion object {
        internal fun gjenopprett(dto: InfotrygdPersonutbetalingsperiodeInnDto): PersonUtbetalingsperiode {
            val periode = Periode.gjenopprett(dto.periode)
            return PersonUtbetalingsperiode(
                orgnr = dto.orgnr,
                fom = periode.start,
                tom = periode.endInclusive,
                grad = Prosentdel.gjenopprett(dto.grad),
                inntekt = Inntekt.gjenopprett(dto.inntekt)
            )
        }
    }
}
