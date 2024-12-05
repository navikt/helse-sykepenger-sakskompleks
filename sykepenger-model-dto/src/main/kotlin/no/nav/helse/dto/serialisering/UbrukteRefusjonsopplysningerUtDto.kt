package no.nav.helse.dto.serialisering

import java.util.UUID
import no.nav.helse.dto.BeløpstidslinjeDto
import no.nav.helse.dto.RefusjonsservitørDto

data class UbrukteRefusjonsopplysningerUtDto(
    val ubrukteRefusjonsopplysninger: RefusjonsservitørDto,
    private val sisteRefusjonstidslinje: BeløpstidslinjeDto?,
    private val sisteBehandlingId: UUID?
) {
    fun refusjonstidslinje(behandlingId: UUID): BeløpstidslinjeDto? {
        if (behandlingId != sisteBehandlingId) return null
        return sisteRefusjonstidslinje
    }
}
