package no.nav.helse.person

import java.time.LocalDate
import no.nav.helse.dto.SykmeldingsperioderDto
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmelding

internal class Sykmeldingsperioder(
    private var perioder: List<Periode> = listOf()
) {

    fun view() = SykmeldingsperioderView(perioder)

    internal fun lagre(sykmelding: Sykmelding) {
        perioder = sykmelding.oppdaterSykmeldingsperioder(perioder)
    }

    internal fun avventerSøknad(vedtaksperiode: Periode): Boolean {
        return perioder.any { other -> vedtaksperiode.overlapperMed(other) }
    }

    internal fun fjern(søknad: Periode) {
        perioder = perioder.flatMap { it.trim(søknad.oppdaterFom(LocalDate.MIN)) }
    }

    internal fun overlappendePerioder(dager: DagerFraInntektsmelding) =
        dager.overlappendeSykmeldingsperioder(perioder)

    internal fun perioderInnenfor16Dager(dager: DagerFraInntektsmelding) =
        dager.perioderInnenfor16Dager(perioder)

    internal fun dto() = SykmeldingsperioderDto(perioder = this.perioder.map { it.dto() })

    internal companion object {
        fun gjenopprett(dto: SykmeldingsperioderDto): Sykmeldingsperioder {
            return Sykmeldingsperioder(
                perioder = dto.perioder.map { Periode.gjenopprett(it) }
            )
        }
    }
}

internal data class SykmeldingsperioderView(val perioder: List<Periode>)
