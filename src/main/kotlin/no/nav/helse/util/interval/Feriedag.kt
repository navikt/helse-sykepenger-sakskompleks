package no.nav.helse.util.interval

import java.time.LocalDate
import java.time.LocalDateTime

internal class Feriedag internal constructor(gjelder: LocalDate, rapportert: LocalDateTime) : Dag(gjelder, rapportert) {
    override fun antallSykedager() = 0
}
