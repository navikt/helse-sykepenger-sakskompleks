package no.nav.helse.person

import no.nav.helse.hendelser.Behandlingsavgjørelse
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Hva.GODKJENNING
import no.nav.helse.person.Venteårsak.Hvorfor.OVERSTYRING_IGANGSATT
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerGodkjenningRevurdering : Vedtaksperiodetilstand {
    override val type = AVVENTER_GODKJENNING_REVURDERING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.trengerGodkjenning(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode) = GODKJENNING fordi OVERSTYRING_IGANGSATT
    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering, aktivitetslogg)
    }

    override fun nyAnnullering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        if (vedtaksperiode.behandlinger.erAvvist()) return
        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerRevurdering)
    }

    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun skalHåndtereDager(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) =
        vedtaksperiode.skalHåndtereDagerRevurdering(dager, aktivitetslogg)

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        } ?: vedtaksperiode.trengerGodkjenning(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
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
            if (utbetalingsavgjørelse.automatisert) {
                return aktivitetslogg.info("Revurderingen ble avvist automatisk - hindrer tilstandsendring for å unngå saker som blir stuck")
            }
        }
        endreTilstand(
            vedtaksperiode = vedtaksperiode,
            event = aktivitetslogg,
            nyTilstand = when {
                vedtaksperiode.behandlinger.erAvvist() -> RevurderingFeilet
                vedtaksperiode.behandlinger.harUtbetalinger() -> TilUtbetaling
                else -> Avsluttet
            }
        )
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        if (vedtaksperiode.behandlinger.erHistorikkEndretSidenBeregning(infotrygdhistorikk)) return endreTilstand(
            vedtaksperiode,
            aktivitetslogg,
            AvventerRevurdering
        ) {
            aktivitetslogg.info("Infotrygdhistorikken har endret seg, reberegner periode")
        }
        else aktivitetslogg.info("Infotrygdhistorikken er uendret, reberegner ikke periode")
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

