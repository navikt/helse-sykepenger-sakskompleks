package no.nav.helse.person.behandling

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode

sealed interface Behandlingsinntekt {
    val hendelseId: UUID
    val beløp: no.nav.helse.økonomi.Inntekt
    val tidsstempel: LocalDateTime
}

data class TilkommenInntekt(
    override val hendelseId: UUID,
    override val beløp: no.nav.helse.økonomi.Inntekt,
    override val tidsstempel: LocalDateTime,
    private val orgnummer: String,
    private val periode: Periode
) : Behandlingsinntekt
