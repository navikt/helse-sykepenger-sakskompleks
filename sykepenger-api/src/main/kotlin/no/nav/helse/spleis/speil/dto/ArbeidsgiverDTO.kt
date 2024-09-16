package no.nav.helse.spleis.speil.dto

import java.util.UUID
import no.nav.helse.erHelg
import no.nav.helse.hendelser.til
import no.nav.helse.spleis.speil.builders.IVilkårsgrunnlagHistorikk

data class ArbeidsgiverDTO(
    val organisasjonsnummer: String,
    val id: UUID,
    val generasjoner: List<SpeilGenerasjonDTO>,
    val ghostPerioder: List<GhostPeriodeDTO> = emptyList(),
    val nyeInntektsforhold: List<NyttInntektsforholdPeriodeDTO> = emptyList()
) {
    private companion object {
        fun List<ArbeidsgiverDTO>.sykefraværstilfeller() = this
            .flatMap { arbeidsgiver ->
                arbeidsgiver.generasjoner.firstOrNull()?.perioder?.map { periode ->
                    periode.skjæringstidspunkt to (periode.fom .. periode.tom)
                } ?: emptyList()
            }.groupBy({ it.first }) { it.second }
    }
    internal fun erTom(vilkårsgrunnlagHistorikk: IVilkårsgrunnlagHistorikk) = ghostPerioder.isEmpty()
            && nyeInntektsforhold.isEmpty()
            && generasjoner.isEmpty()
            && vilkårsgrunnlagHistorikk.inngårIkkeISammenligningsgrunnlag(organisasjonsnummer)

    internal fun medGhostperioderOgNyeInntektsforholdperioder(vilkårsgrunnlagHistorikk: IVilkårsgrunnlagHistorikk, arbeidsgivere: List<ArbeidsgiverDTO>): ArbeidsgiverDTO {
        val sykefraværstilfeller = arbeidsgivere.sykefraværstilfeller()
        val potensiellePerioder = vilkårsgrunnlagHistorikk.potensielleGhostsperioder(organisasjonsnummer, sykefraværstilfeller)
        val potensielleGhostperioder = potensiellePerioder.mapNotNull { it.first }
        val potensielleMyttInntektsforholdperioder = potensiellePerioder.mapNotNull { it.second }

        val ghostsperioder = potensielleGhostperioder.flatMap { ghostperiode -> fjernDagerMedSykdom(ghostperiode) }

        return copy(
            ghostPerioder = ghostsperioder,
            nyeInntektsforhold = potensielleMyttInntektsforholdperioder
        )
    }

    private fun fjernDagerMedSykdom(ghostperiode: GhostPeriodeDTO): List<GhostPeriodeDTO> {
        if (generasjoner.isEmpty()) return listOf(ghostperiode)
        val tidslinjeperioderFraNyesteGenerasjon = generasjoner
            .first()
            .perioder
            .sortedBy { it.fom }
        val oppslittetPølser = tidslinjeperioderFraNyesteGenerasjon.fold(listOf(ghostperiode)) { resultat, vedtaksperiode ->
            val tidligereGhostperioder = resultat.dropLast(1)
            val sisteGhostperiode = resultat.lastOrNull()
            val tidslinjeperiode = vedtaksperiode.fom..vedtaksperiode.tom
            tidligereGhostperioder + (sisteGhostperiode?.brytOpp(tidslinjeperiode) ?: emptyList())
        }
        return fjernHelgepølser(oppslittetPølser)
    }

    private fun fjernHelgepølser(ghostPerioder: List<GhostPeriodeDTO>) = ghostPerioder
        .filterNot { ghostperiode ->
            val periode = ghostperiode.fom til ghostperiode.tom
            periode.count() <= 2 && periode.start.erHelg() && periode.endInclusive.erHelg()
        }
}