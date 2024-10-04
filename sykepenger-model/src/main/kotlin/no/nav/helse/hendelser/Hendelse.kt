package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.AvsenderDto
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

enum class Avsender {
    SYKMELDT, ARBEIDSGIVER, SAKSBEHANDLER, SYSTEM;

    fun dto() = when (this) {
        SYKMELDT -> AvsenderDto.SYKMELDT
        ARBEIDSGIVER -> AvsenderDto.ARBEIDSGIVER
        SAKSBEHANDLER -> AvsenderDto.SAKSBEHANDLER
        SYSTEM -> AvsenderDto.SYSTEM
    }
    companion object {
        fun gjenopprett(dto: AvsenderDto): Avsender {
            return when (dto) {
                AvsenderDto.ARBEIDSGIVER -> ARBEIDSGIVER
                AvsenderDto.SAKSBEHANDLER -> SAKSBEHANDLER
                AvsenderDto.SYKMELDT -> SYKMELDT
                AvsenderDto.SYSTEM -> SYSTEM
            }
        }
    }
}

sealed interface Hendelse : IAktivitetslogg {
    fun meldingsreferanseId(): UUID
    fun innsendt(): LocalDateTime
    fun registrert(): LocalDateTime
    fun avsender(): Avsender
    fun navn(): String
    fun venter(block: () -> Unit) { block() }

}