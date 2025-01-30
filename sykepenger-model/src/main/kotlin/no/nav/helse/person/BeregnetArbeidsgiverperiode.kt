package no.nav.helse.person

import no.nav.helse.dto.deserialisering.BeregnetArbeidsgiverperiodeInnDto
import no.nav.helse.dto.deserialisering.BeregnetArbeidsgiverperiodeInnDto.StatusInnDto
import no.nav.helse.dto.serialisering.BeregnetArbeidsgiverperiodeUtDto
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.periode

internal data class BeregnetArbeidsgiverperiode(
    val status: Status,
    val arbeidsgiverperioder: List<Venteperiode>
) {
    val omsluttendePeriode = arbeidsgiverperioder.map { it.periode }.periode()

    enum class Status {
        TELLING_IKKE_BEGYNT,
        TELLING_STARTET,
        TELLING_FERDIG
    }

    // todo: ikke kall dette venteperiode
    // todo: ha perioder nav overtar ansvar som EGEN liste ved siden av
    data class Venteperiode(
        val periode: Periode,
        val navOvertarAnsvar: Boolean
    )

    fun dto() = BeregnetArbeidsgiverperiodeUtDto(
        status = when (status) {
            Status.TELLING_IKKE_BEGYNT -> BeregnetArbeidsgiverperiodeUtDto.StatusUtDto.TELLING_IKKE_BEGYNT
            Status.TELLING_STARTET -> BeregnetArbeidsgiverperiodeUtDto.StatusUtDto.TELLING_STARTET
            Status.TELLING_FERDIG -> BeregnetArbeidsgiverperiodeUtDto.StatusUtDto.TELLING_FERDIG
        },
        arbeidsgiverperioder = arbeidsgiverperioder.map {
            BeregnetArbeidsgiverperiodeUtDto.VenteperiodeUtDto(
                periode = it.periode.dto(),
                navOvertarAnsvar = it.navOvertarAnsvar
            )
        }
    )

    companion object {
        fun gjenopprett(dto: BeregnetArbeidsgiverperiodeInnDto ) = BeregnetArbeidsgiverperiode(
            status = when (dto.status) {
                StatusInnDto.TELLING_IKKE_BEGYNT -> Status.TELLING_IKKE_BEGYNT
                StatusInnDto.TELLING_STARTET -> Status.TELLING_STARTET
                StatusInnDto.TELLING_FERDIG -> Status.TELLING_FERDIG
            },
            arbeidsgiverperioder = dto.arbeidsgiverperioder.map {
                Venteperiode(
                    periode = Periode.gjenopprett(it.periode),
                    navOvertarAnsvar = it.navOvertarAnsvar
                )
            }
        )
    }
}
