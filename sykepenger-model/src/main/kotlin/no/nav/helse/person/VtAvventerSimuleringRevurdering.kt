package no.nav.helse.person

import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Hva.UTBETALING
import no.nav.helse.person.Venteårsak.Hvorfor.OVERSTYRING_IGANGSATT
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerSimuleringRevurdering : Vedtaksperiodetilstand {
    override val type: TilstandType = AVVENTER_SIMULERING_REVURDERING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.behandlinger.simuler(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = UTBETALING fordi OVERSTYRING_IGANGSATT
    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering, aktivitetslogg)
    }

    override fun venter(
        vedtaksperiode: Vedtaksperiode,
        nestemann: Vedtaksperiode
    ) = vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        } ?: vedtaksperiode.behandlinger.simuler(aktivitetslogg)
    }

    override fun skalHåndtereDager(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) =
        vedtaksperiode.skalHåndtereDagerRevurdering(dager, aktivitetslogg)

    override fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering, aktivitetslogg: IAktivitetslogg) {
        val wrapper = aktivitetsloggForRevurdering(aktivitetslogg)
        vedtaksperiode.behandlinger.valider(simulering, wrapper)
        if (!vedtaksperiode.behandlinger.erKlarForGodkjenning()) return wrapper.info("Kan ikke gå videre da begge oppdragene ikke er simulert.")
        endreTilstand(vedtaksperiode, wrapper, AvventerGodkjenningRevurdering)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad, aktivitetslogg)
    }
}
