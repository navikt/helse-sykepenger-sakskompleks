package no.nav.helse.person

import java.util.UUID
import no.nav.helse.dto.deserialisering.ForkastetVedtaksperiodeInnDto
import no.nav.helse.dto.serialisering.ForkastetVedtaksperiodeUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.person.Vedtaksperiode.Companion.inneholder
import no.nav.helse.person.Vedtaksperiode.Companion.slåSammenForkastedeSykdomstidslinjer
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode

internal class ForkastetVedtaksperiode(
    private val vedtaksperiode: Vedtaksperiode
) {

    fun view() = vedtaksperiode.view()

    internal companion object {
        private fun Iterable<ForkastetVedtaksperiode>.perioder() = map { it.vedtaksperiode }

        internal fun List<ForkastetVedtaksperiode>.overlapperMed(dagerFraInntektsmelding: DagerFraInntektsmelding) =
            perioder().any { dagerFraInntektsmelding.overlapperMed(it.periode()) }

        internal fun harNyereForkastetPeriode(forkastede: Iterable<ForkastetVedtaksperiode>, vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) =
            Vedtaksperiode.harNyereForkastetPeriode(forkastede.perioder(), vedtaksperiode, hendelse)

        internal fun harOverlappendeForkastetPeriode(forkastede: Iterable<ForkastetVedtaksperiode>, vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) =
            Vedtaksperiode.harOverlappendeForkastetPeriode(forkastede.perioder(), vedtaksperiode, hendelse)

        internal fun forlengerForkastet(forkastede: Iterable<ForkastetVedtaksperiode>, hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode) =
            Vedtaksperiode.forlengerForkastet(forkastede.perioder(), hendelse, vedtaksperiode)

        internal fun Iterable<ForkastetVedtaksperiode>.slåSammenSykdomstidslinjer(sykdomstidslinje: Sykdomstidslinje) = perioder().slåSammenForkastedeSykdomstidslinjer(sykdomstidslinje)

        internal fun harKortGapTilForkastet(forkastede: Iterable<ForkastetVedtaksperiode>, hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode) =
            Vedtaksperiode.harKortGapTilForkastet(forkastede.perioder(), hendelse, vedtaksperiode)

        internal fun Iterable<ForkastetVedtaksperiode>.erForkastet(vedtaksperiodeId: UUID) = perioder().inneholder(vedtaksperiodeId)

        internal fun hørerTilArbeidsgiverperiode(
            forkastede: List<ForkastetVedtaksperiode>,
            vedtaksperioder: List<Vedtaksperiode>,
            arbeidsgiverperiode: Arbeidsgiverperiode
        ): List<Vedtaksperiode> =
            (forkastede.map { it.vedtaksperiode } + vedtaksperioder)
                .sorted()
                .filter { arbeidsgiverperiode.hørerTil(it.periode()) }

        internal fun gjenopprett(
            person: Person,
            aktørId: String,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            organisasjonsnummer: String,
            dto: ForkastetVedtaksperiodeInnDto,
            subsumsjonslogg: Subsumsjonslogg,
            grunnlagsdata: Map<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>,
            utbetalinger: Map<UUID, Utbetaling>
        ): ForkastetVedtaksperiode {
            return ForkastetVedtaksperiode(
                vedtaksperiode = Vedtaksperiode.gjenopprett(
                    person = person,
                    aktørId = aktørId,
                    fødselsnummer = fødselsnummer,
                    arbeidsgiver = arbeidsgiver,
                    organisasjonsnummer = organisasjonsnummer,
                    dto = dto.vedtaksperiode,
                    subsumsjonslogg = subsumsjonslogg,
                    grunnlagsdata = grunnlagsdata,
                    utbetalinger = utbetalinger
                )
            )
        }

    }

    internal fun dto() = ForkastetVedtaksperiodeUtDto(vedtaksperiode.dto(null))
}
