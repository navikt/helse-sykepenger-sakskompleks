package no.nav.helse.person

import no.nav.helse.august
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.utbetalingslinjer.Utbetalingsak
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverperiodeForVedtaksperiode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtbetalingsakerBuilderTest {

    @Test
    fun `grupperer vedtaksperioder etter arbeidsgiverperioden`() {
        val builder = UtbetalingsakerBuilder(
            vedtaksperiodene = listOf(
                ArbeidsgiverperiodeForVedtaksperiode(1.januar til 10.januar, listOf(1.januar til 10.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(11.januar til 21.januar, listOf(1.januar til 16.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(1.februar til 5.februar, listOf(1.januar til 16.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(1.mai til 31.mai, listOf(1.mai til 16.mai)),
                ArbeidsgiverperiodeForVedtaksperiode(1.august til 5.august, emptyList())
            ),
            infotrygdbetalinger = emptyList()
        )

        val forventet = listOf(
            Utbetalingsak(1.januar, listOf(1.januar til 10.januar, 11.januar til 21.januar, 1.februar til 5.februar)),
            Utbetalingsak(1.mai, listOf(1.mai til 31.mai)),
            Utbetalingsak(1.august, listOf(1.august til 5.august)),
        )
        assertEquals(forventet, builder.lagUtbetalingsaker())
    }

    @Test
    fun `bryter opp utbetalingsaker etter infotrygdutbetaling`() {
        val builder = UtbetalingsakerBuilder(
            vedtaksperiodene = listOf(
                ArbeidsgiverperiodeForVedtaksperiode(1.januar til 10.januar, listOf(1.januar til 10.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(11.januar til 21.januar, listOf(1.januar til 16.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(1.februar til 5.februar, listOf(1.januar til 16.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(6.februar til 15.februar, listOf(1.januar til 16.januar))
            ),
            infotrygdbetalinger = listOf(22.januar til 28.januar)
        )

        val forventet = listOf(
            Utbetalingsak(1.januar, listOf(1.januar til 10.januar, 11.januar til 21.januar)),
            Utbetalingsak(1.februar, listOf(1.februar til 5.februar, 6.februar til 15.februar))
        )
        assertEquals(forventet, builder.lagUtbetalingsaker())
    }

    @Test
    fun `bryter ikke opp utbetalingsaker ved overlappende infotrygdutbetaling`() {
        val builder = UtbetalingsakerBuilder(
            vedtaksperiodene = listOf(
                ArbeidsgiverperiodeForVedtaksperiode(1.januar til 10.januar, listOf(1.januar til 10.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(11.januar til 21.januar, listOf(1.januar til 16.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(1.februar til 5.februar, listOf(1.januar til 16.januar)),
                ArbeidsgiverperiodeForVedtaksperiode(6.februar til 15.februar, listOf(1.januar til 16.januar))
            ),
            infotrygdbetalinger = listOf(17.januar til 21.januar)
        )

        val forventet = listOf(
            Utbetalingsak(1.januar, listOf(1.januar til 10.januar, 11.januar til 21.januar, 1.februar til 5.februar, 6.februar til 15.februar)),
        )
        assertEquals(forventet, builder.lagUtbetalingsaker())
    }

    private fun ArbeidsgiverperiodeForVedtaksperiode(vedtaksperiode: Periode, agp: List<Periode>) =
        ArbeidsgiverperiodeForVedtaksperiode(
            vedtaksperiode = vedtaksperiode,
            arbeidsgiverperiode = BeregnetArbeidsgiverperiode(
                status = BeregnetArbeidsgiverperiode.Status.TELLING_FERDIG,
                arbeidsgiverperioder = agp.map {
                    BeregnetArbeidsgiverperiode.Venteperiode(it, false)
                }
            )
        )
}
