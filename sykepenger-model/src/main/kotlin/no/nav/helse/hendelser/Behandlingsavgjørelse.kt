package no.nav.helse.hendelser

import java.util.UUID

interface Behandlingsavgjørelse : UtbetalingsavgjørelseHendelse, Hendelse {
    fun relevantVedtaksperiode(id: UUID): Boolean
}