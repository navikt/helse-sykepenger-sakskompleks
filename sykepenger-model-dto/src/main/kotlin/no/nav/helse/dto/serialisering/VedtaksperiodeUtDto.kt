package no.nav.helse.dto.serialisering

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.LazyVedtaksperiodeVenterDto
import no.nav.helse.dto.PeriodeDto
import no.nav.helse.dto.VedtaksperiodetilstandDto

data class VedtaksperiodeUtDto(
    val id: UUID,
    var tilstand: VedtaksperiodetilstandDto,
    val skjæringstidspunkt: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
    val sykmeldingFom: LocalDate,
    val sykmeldingTom: LocalDate,
    val behandlinger: BehandlingerUtDto,
    val venteårsak: LazyVedtaksperiodeVenterDto,
    val egenmeldingsdager: List<PeriodeDto>,
    val opprettet: LocalDateTime,
    var oppdatert: LocalDateTime
)