package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.økonomi.Inntekt

sealed class SkatteopplysningSykepengegrunnlag(
    id: UUID,
    hendelseId: UUID,
    dato: LocalDate,
    beløp: Inntekt,
    tidsstempel: LocalDateTime
) : Inntektsopplysning(id, hendelseId, dato, beløp, tidsstempel, Inntektskilde.AOrdningen)