package no.nav.helse.utbetalingstidslinje

import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import no.nav.helse.Alder
import kotlin.math.max

internal class UtbetalingTeller(
    private val alder: Alder,
    private val arbeidsgiverRegler: ArbeidsgiverRegler
) {
    private companion object {
        private const val HISTORISK_PERIODE_I_ÅR: Long = 3
    }
    private var forbrukteDager: Int = 0
    private var gammelPersonDager: Int = 0
    private var startdatoSykepengerettighet: LocalDate = LocalDate.MIN
    private var startdatoTreårsvindu: LocalDate = LocalDate.MIN
    private val betalteDager = mutableSetOf<LocalDate>()

    internal fun startdatoSykepengerettighet() = startdatoSykepengerettighet.takeUnless { it == LocalDate.MIN }

    internal fun inkrementer(dato: LocalDate) {
        if (forbrukteDager == 0) {
            startdatoSykepengerettighet = dato
            startdatoTreårsvindu = dato.minusYears(HISTORISK_PERIODE_I_ÅR)
        }
        betalteDager.add(dato)
        forbrukteDager += 1
        if (alder.innenfor67årsgrense(dato)) return
        gammelPersonDager += 1
    }

    internal fun dekrementer(dato: LocalDate) {
        val nyStartdatoTreårsvindu = dato.minusYears(HISTORISK_PERIODE_I_ÅR)
        if (nyStartdatoTreårsvindu >= startdatoSykepengerettighet) {
            startdatoTreårsvindu.datesUntil(nyStartdatoTreårsvindu).filter { it in betalteDager }.forEach { dekrementer ->
                dekrementerDag(dekrementer)
            }
        }
        startdatoTreårsvindu = nyStartdatoTreårsvindu
    }

    private fun dekrementerDag(dato: LocalDate) {
        if (dato < startdatoSykepengerettighet) return
        forbrukteDager = max(0, forbrukteDager - 1)
    }

    internal fun resett() {
        betalteDager.clear()
        startdatoSykepengerettighet = LocalDate.MIN
        startdatoTreårsvindu = LocalDate.MIN
        forbrukteDager = 0
        gammelPersonDager = 0
    }

    internal fun erDagerOver67ÅrForbrukte(): Boolean {
        val gjenståendeSykepengedagerOver67 = arbeidsgiverRegler.maksSykepengedagerOver67() - gammelPersonDager
        return gjenståendeSykepengedagerOver67 == 0
    }
    internal fun erAlleDagerForbrukte(): Boolean {
        val gjenståendeSykepengedager = arbeidsgiverRegler.maksSykepengedager() - forbrukteDager
        return gjenståendeSykepengedager == 0 || erDagerOver67ÅrForbrukte()
    }

    internal fun gjenståendeDager(dato: LocalDate): Int {
        val gjenståendeSykepengedager = arbeidsgiverRegler.maksSykepengedager() - forbrukteDager
        val gjenståendeSykepengedagerOver67 = arbeidsgiverRegler.maksSykepengedagerOver67() - gammelPersonDager
        return alder.maksimumSykepenger(dato, forbrukteDager, gjenståendeSykepengedager, gjenståendeSykepengedagerOver67).gjenståendeDager()
    }

    internal fun maksimumSykepenger(sisteKjenteDag: LocalDate? = null): Alder.MaksimumSykepenger {
        val gjenståendeSykepengedager = arbeidsgiverRegler.maksSykepengedager() - forbrukteDager
        val gjenståendeSykepengedagerOver67 = arbeidsgiverRegler.maksSykepengedagerOver67() - gammelPersonDager
        // dersom personen har gjenstående sykedager brukes sisteKjenteDag fordi personen kan ha ferie/annen kjent informasjon på slutten av tidslinjen, og ikke
        // nødvendigvis en utbetalingsdag.
        val perspektiv = if (gjenståendeSykepengedager == 0 || gjenståendeSykepengedagerOver67 == 0) betalteDager.last() else sisteKjenteDag?.minusDays(when (sisteKjenteDag.dayOfWeek) {
            SATURDAY -> 1
            SUNDAY -> 2
            else -> 0
        }) ?: betalteDager.last()

        return alder.maksimumSykepenger(perspektiv, forbrukteDager, gjenståendeSykepengedager, gjenståendeSykepengedagerOver67)
    }

    fun clone(): UtbetalingTeller {
        return UtbetalingTeller(alder, arbeidsgiverRegler).apply {
            this.forbrukteDager = this@UtbetalingTeller.forbrukteDager
            this.gammelPersonDager = this@UtbetalingTeller.gammelPersonDager
            this.startdatoSykepengerettighet = this@UtbetalingTeller.startdatoSykepengerettighet
            this.startdatoTreårsvindu = this@UtbetalingTeller.startdatoTreårsvindu
            this.betalteDager.addAll(this@UtbetalingTeller.betalteDager)
        }
    }
}
