package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.person.inntekt.Arbeidsgiverinntekt.Kilde
import no.nav.helse.økonomi.Inntekt

internal fun arbeidsgiverinntekt(
    dato: LocalDate,
    beløp: Inntekt
) =
    Arbeidsgiverinntekt(
        id = UUID.randomUUID(),
        inntektsdata = Inntektsdata(UUID.randomUUID(), dato, beløp, LocalDateTime.now()),
        kilde = Kilde.Arbeidsgiver
)

internal fun infotrygd(id: UUID, dato: LocalDate, hendelseId: UUID, beløp: Inntekt, tidsstempel: LocalDateTime) =
    Infotrygd(id, Inntektsdata(hendelseId, dato, beløp, tidsstempel))

internal fun skattSykepengegrunnlag(
    hendelseId: UUID,
    dato: LocalDate,
    beløp: Inntekt,
) =
    SkattSykepengegrunnlag(UUID.randomUUID(), Inntektsdata(hendelseId, dato, beløp, LocalDateTime.now()))

internal fun skjønnsmessigFastsatt(
    dato: LocalDate,
    beløp: Inntekt
) = SkjønnsmessigFastsatt(
    id = UUID.randomUUID(),
    inntektsdata = Inntektsdata(
        hendelseId = UUID.randomUUID(),
        dato = dato,
        beløp = beløp,
        tidsstempel = LocalDateTime.now()
    )
)
