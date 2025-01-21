package no.nav.helse.dto.serialisering

import no.nav.helse.dto.PeriodeDto

data class ArbeidsgiverInntektsopplysningUtDto(
    val orgnummer: String,
    val gjelder: PeriodeDto,
    val inntektsopplysning: InntektsopplysningUtDto,
    val korrigertInntekt: InntektsopplysningUtDto.SaksbehandlerDto?,
    val skjønnsmessigFastsatt: SkjønnsmessigFastsattUtDto?
)
