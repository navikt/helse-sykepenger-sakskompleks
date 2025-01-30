package no.nav.helse.dto.serialisering

import no.nav.helse.dto.PeriodeDto

data class BeregnetArbeidsgiverperiodeUtDto(
    val status: StatusUtDto,
    val arbeidsgiverperioder: List<VenteperiodeUtDto>
) {
    enum class StatusUtDto {
        TELLING_IKKE_BEGYNT,
        TELLING_STARTET,
        TELLING_FERDIG
    }

    data class VenteperiodeUtDto(
        val periode: PeriodeDto,
        val navOvertarAnsvar: Boolean
    )
}
