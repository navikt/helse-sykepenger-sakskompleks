package no.nav.helse.spleis.e2e

import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.nullstillTilstandsendringer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VedtaksperiodeAnnullertEventTest : AbstractEndToEndTest() {

    @Test
    fun `vi sender vedtaksperiode annullert-hendelse når saksbehandler annullerer en vedtaksperiode`() {
        nyttVedtak(januar)
        håndterAnnullerUtbetaling()

        assertTrue(observatør.vedtaksperiodeAnnullertEventer.isNotEmpty())
    }

    @Test
    fun `vi sender vedtaksperiode annullert-hendelser når saksbehandler annullerer en vedtaksperiode i et lengre sykdomsforløp`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        nyttVedtak(5.mars til 31.mars, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        håndterAnnullerUtbetaling()

        assertEquals(3, observatør.vedtaksperiodeAnnullertEventer.size)
        assertEquals(
            1.januar til 31.januar,
            observatør.vedtaksperiodeAnnullertEventer[0].fom til observatør.vedtaksperiodeAnnullertEventer[0].tom
        )
        assertEquals(
            februar,
            observatør.vedtaksperiodeAnnullertEventer[1].fom til observatør.vedtaksperiodeAnnullertEventer[1].tom
        )
        assertEquals(
            5.mars til 31.mars,
            observatør.vedtaksperiodeAnnullertEventer[2].fom til observatør.vedtaksperiodeAnnullertEventer[2].tom
        )
    }

    @Test
    fun `vi sender ikke ut vedtaksperiode annullert-hendelse for vedtaksperioder som ikke er utbetalt`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        tilGodkjenning(5.mars til 31.mars, ORGNUMMER, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        håndterAnnullerUtbetaling()

        assertEquals(2, observatør.vedtaksperiodeAnnullertEventer.size)
        assertEquals(
            1.januar til 31.januar,
            observatør.vedtaksperiodeAnnullertEventer[0].fom til observatør.vedtaksperiodeAnnullertEventer[0].tom
        )
        assertEquals(
            februar,
            observatør.vedtaksperiodeAnnullertEventer[1].fom til observatør.vedtaksperiodeAnnullertEventer[1].tom
        )
    }

    @Test
    fun `også langt gap`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        nyttVedtak(april, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        håndterAnnullerUtbetaling()

        assertEquals(1, observatør.vedtaksperiodeAnnullertEventer.size)
        assertEquals(
            april,
            observatør.vedtaksperiodeAnnullertEventer[0].fom til observatør.vedtaksperiodeAnnullertEventer[0].tom
        )
    }

    @Test
    fun `arbeid ikke gjenopptatt`() {
        nyttVedtak(januar)

        håndterSøknad(mars)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.mars,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FerieEllerAvspasering",
            vedtaksperiodeIdInnhenter = 2.vedtaksperiode
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((februar).map {
            ManuellOverskrivingDag(
                it,
                Dagtype.ArbeidIkkeGjenopptattDag
            )
        })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(0, observatør.vedtaksperiodeAnnullertEventer.size)
    }

    @Test
    fun `revurdering uten endring som siden annulleres skal sende melding om annullert`() {
        nyttVedtak(januar)

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterUtbetalt()
        håndterAnnullerUtbetaling()

        assertEquals(1, observatør.vedtaksperiodeAnnullertEventer.size)
    }

    @Test
    fun `Pågående revurdering uten endring som siden annulleres skal sende melding om annullert`() {
        nyttVedtak(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterAnnullerUtbetaling()

        assertEquals(1, observatør.vedtaksperiodeAnnullertEventer.size)
    }

}