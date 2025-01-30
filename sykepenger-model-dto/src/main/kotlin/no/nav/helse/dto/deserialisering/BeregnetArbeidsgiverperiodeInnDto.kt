package no.nav.helse.dto.deserialisering

import no.nav.helse.dto.PeriodeDto

data class BeregnetArbeidsgiverperiodeInnDto(
    val status: StatusInnDto,
    val arbeidsgiverperioder: List<VenteperiodeInnDto>
) {
    enum class StatusInnDto {
        TELLING_IKKE_BEGYNT,
        TELLING_STARTET,
        TELLING_FERDIG
    }

    data class VenteperiodeInnDto(
        val periode: PeriodeDto,
        val navOvertarAnsvar: Boolean
    )
}
