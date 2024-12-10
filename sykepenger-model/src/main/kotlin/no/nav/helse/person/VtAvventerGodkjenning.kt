package no.nav.helse.person

import no.nav.helse.hendelser.Behandlingsavgjørelse
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.GODKJENNING
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_24
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerGodkjenning : Vedtaksperiodetilstand {
    override val type = AVVENTER_GODKJENNING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.trengerGodkjenning(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = GODKJENNING.utenBegrunnelse
    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknad(søknad, aktivitetslogg, AvventerBlokkerendePeriode)
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) return
    }

    override fun håndter(
        person: Person,
        arbeidsgiver: Arbeidsgiver,
        vedtaksperiode: Vedtaksperiode,
        utbetalingsavgjørelse: Behandlingsavgjørelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.behandlinger.vedtakFattet(arbeidsgiver, utbetalingsavgjørelse, aktivitetslogg)
        if (vedtaksperiode.behandlinger.erAvvist()) {
            return if (arbeidsgiver.kanForkastes(vedtaksperiode, aktivitetslogg)) vedtaksperiode.forkast(
                utbetalingsavgjørelse,
                aktivitetslogg
            )
            else aktivitetslogg.varsel(RV_UT_24)
        }
        endreTilstand(
            vedtaksperiode = vedtaksperiode,
            event = aktivitetslogg,
            nyTilstand = when {
                vedtaksperiode.behandlinger.harUtbetalinger() -> TilUtbetaling
                else -> Avsluttet
            }
        )
    }

    override fun nyAnnullering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerBlokkerendePeriode)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        } ?: vedtaksperiode.trengerGodkjenning(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        if (!vedtaksperiode.behandlinger.erHistorikkEndretSidenBeregning(infotrygdhistorikk)) {
            aktivitetslogg.info("Infotrygdhistorikken er uendret, reberegner ikke periode")
            return
        }
        vedtaksperiode.behandlinger.forkastUtbetaling(aktivitetslogg)
        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerBlokkerendePeriode) {
            aktivitetslogg.info("Infotrygdhistorikken har endret seg, reberegner periode")
        }
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattFørstegangsvurdering(revurdering, aktivitetslogg)
    }
}
