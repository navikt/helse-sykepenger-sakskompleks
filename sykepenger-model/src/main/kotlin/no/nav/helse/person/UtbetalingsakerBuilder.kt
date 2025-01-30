package no.nav.helse.person

import java.time.LocalDate
import kotlin.collections.component1
import kotlin.collections.component2
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.trim
import no.nav.helse.utbetalingslinjer.Utbetalingsak
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverperiodeForVedtaksperiode

internal class UtbetalingsakerBuilder(
    val vedtaksperiodene: List<ArbeidsgiverperiodeForVedtaksperiode>,
    val infotrygdbetalinger: List<Periode>
) {

    /**
     * utbetalingsaker er den logiske inndelingen som binder
     * sammen flere vedtaksperioder til en sak mot Oppdragsystemet
     *
     * i praksis lager vi en utbetalingssak per arbeidsgiverperiode, men vi bryter de opp om
     * det er infotrygdutbetalinger mellom
     */
    fun lagUtbetalingsaker(): List<Utbetalingsak> {
        /* ignorerer infotrygdutbetalinger som overlapper med vedtaksperiodene våre */
        val infotrygdbetalinger = utbetalteInfotrygdperioderMellomVedtaksperioder(vedtaksperiodene)

        return vedtaksperiodene
            .groupBy { finnStartdatoForUtbetalingsakForVedtaksperiode(it, infotrygdbetalinger, vedtaksperiodene) }
            .map { (utbetalingsakensStartdato, perioder) ->
                Utbetalingsak(
                    startperiode = utbetalingsakensStartdato,
                    vedtaksperioder = perioder.map { it.vedtaksperiode }
                )
            }
    }

    private fun finnStartdatoForUtbetalingsakForVedtaksperiode(vedtaksperiode: ArbeidsgiverperiodeForVedtaksperiode, infotrygdbetalinger: List<Periode>, vedtaksperiodene: List<ArbeidsgiverperiodeForVedtaksperiode>): LocalDate {
        // eventuell infotrygdutbetaling som ligger mellom arbeidsgiverperioden og vedtaksperioden
        val mellomliggendeInfotrygdutbetaling = infotrygdutbetalingEtterArbeidsgiverperiodenOgFørVedtaksperioden(infotrygdbetalinger, vedtaksperiode)
        val infotrygdutbetalingsakStartdato = mellomliggendeInfotrygdutbetaling?.let { infotrygdDag ->
            vedtaksperiodene.first { it.vedtaksperiode.start > infotrygdDag.endInclusive }.vedtaksperiode.start
        }
        /* lar utbetalingsakene starte med enten:
            a) første dag i første vedtaksperiode etter mellomliggende infotrygdutbetaling
            b) første dag i arbeidsgiverperioden
            c) første dag i vedtaksperioden hvis arbeidsgiverperioden er tom og det ikke foreligger noen IT-utbetalinger
         */
        return infotrygdutbetalingsakStartdato
            ?: vedtaksperiode.arbeidsgiverperioder.firstOrNull()?.start
            ?: vedtaksperiode.vedtaksperiode.start
    }

    private fun infotrygdutbetalingEtterArbeidsgiverperiodenOgFørVedtaksperioden(infotrygdbetalinger: List<Periode>, vedtaksperiode: ArbeidsgiverperiodeForVedtaksperiode) =
        infotrygdbetalinger.lastOrNull { infotrygdperiode ->
            (vedtaksperiode.arbeidsgiverperioder.isEmpty() || infotrygdperiode.start > vedtaksperiode.arbeidsgiverperioder.last().endInclusive)
                && infotrygdperiode.endInclusive < vedtaksperiode.vedtaksperiode.start
        }

    private fun utbetalteInfotrygdperioderMellomVedtaksperioder(vedtaksperiodene: List<ArbeidsgiverperiodeForVedtaksperiode>) =
        infotrygdbetalinger.flatMap { periode ->
            vedtaksperiodene.map { it.vedtaksperiode }.trim(periode)
        }
}
