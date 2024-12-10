package no.nav.helse.person

import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.UTBETALING
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerSimulering : Vedtaksperiodetilstand {
    override val type: TilstandType = AVVENTER_SIMULERING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        trengerSimulering(vedtaksperiode, aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = UTBETALING.utenBegrunnelse
    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknad(søknad, aktivitetslogg, AvventerBlokkerendePeriode)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        } ?: trengerSimulering(vedtaksperiode, aktivitetslogg)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, simulering: Simulering, aktivitetslogg: IAktivitetslogg) {
        val aktivAktivitetslogg = håndterFørstegangsbehandling(aktivitetslogg, vedtaksperiode)
        vedtaksperiode.behandlinger.valider(simulering, aktivAktivitetslogg)
        if (!vedtaksperiode.behandlinger.erKlarForGodkjenning()) return aktivAktivitetslogg.info("Kan ikke gå videre da begge oppdragene ikke er simulert.")
        endreTilstand(vedtaksperiode, aktivAktivitetslogg, AvventerGodkjenning)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }

    private fun trengerSimulering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.behandlinger.simuler(aktivitetslogg)
    }
}
