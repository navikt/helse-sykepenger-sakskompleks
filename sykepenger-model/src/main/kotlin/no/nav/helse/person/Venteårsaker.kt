package no.nav.helse.person

import java.time.LocalDate
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.NullObserver
import no.nav.helse.hendelser.Periode
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode.Companion.finn

internal class Venteårsaker(
    private val organisasjonsnummer: String,
    private val arbeidsgiverperioder: List<Arbeidsgiverperiode>,
) {

    companion object {
        fun Iterable<Venteårsaker>.finnVenteårsak(organisasjonsnummer: String, vedtaksperiode: Periode, skjæringstidspunkt: LocalDate) = this
            .singleOrNull { it.organisasjonsnummer == organisasjonsnummer }
            ?.venteårsak(vedtaksperiode, skjæringstidspunkt)
    }

    private fun ikkeBehovForInntekt(vedtaksperiode: Periode): Boolean {
        val arbeidsgiverperiode = arbeidsgiverperioder.finn(vedtaksperiode)
        return !Arbeidsgiverperiode.forventerInntekt(arbeidsgiverperiode, vedtaksperiode, Sykdomstidslinje(), NullObserver)
    }

    fun venteårsak(vedtaksperiode: Periode, skjæringstidspunkt: LocalDate): Venteårsak? {
        if (ikkeBehovForInntekt(vedtaksperiode)) return Venteårsak.ForventerIkkeInntekt
        return null
    }

    sealed class Venteårsak {
        data object ForventerIkkeInntekt : Venteårsak()
    }
}