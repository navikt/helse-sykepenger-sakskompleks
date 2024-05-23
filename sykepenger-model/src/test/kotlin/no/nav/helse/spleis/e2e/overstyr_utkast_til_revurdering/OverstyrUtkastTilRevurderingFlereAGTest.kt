package no.nav.helse.spleis.e2e.overstyr_utkast_til_revurdering

import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertUtbetalingsdag
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import org.junit.jupiter.api.Test

internal class OverstyrUtkastTilRevurderingFlereAGTest : AbstractEndToEndTest() {

    private companion object {
        private val AG1 = "123456789"
        private val AG2 = "987612345"
    }

    @Test
    fun `overstyr utkast til revurdering flere ag - første ag mottar overstyring og går helt til utbetalt`() {
        nyeVedtak(1.januar, 31.januar, AG1, AG2)
        forlengVedtak(1.februar til 28.februar, AG1, AG2)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((29.januar til 29.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)

        håndterOverstyrTidslinje((30.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalt(orgnummer = AG1)

        håndterYtelser(1.vedtaksperiode, orgnummer = AG2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = AG2)

        håndterYtelser(2.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = AG1)

        håndterYtelser(2.vedtaksperiode, orgnummer = AG2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = AG2)

        inspektør(AG1) {
            val utbetalingstidslinje = sisteUtbetalingUtbetalingstidslinje()
            assertUtbetalingsdag(utbetalingstidslinje[29.januar], expectedDagtype = Fridag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[30.januar], expectedDagtype = Fridag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[31.januar], expectedDagtype = Fridag::class, 50)
            assertIngenFunksjonelleFeil()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        }

        inspektør(AG2) {
            val utbetalingstidslinje = sisteUtbetalingUtbetalingstidslinje()
            assertIngenFunksjonelleFeil()
            assertUtbetalingsdag(utbetalingstidslinje[29.januar], expectedDagtype = NavDag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[30.januar], expectedDagtype = NavDag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[31.januar], expectedDagtype = NavDag::class, 50)

            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        }
    }

    @Test
    fun `overstyr utkast til revurdering flere ag - første ag mottar overstyring`() {
        nyeVedtak(1.januar, 31.januar, AG1, AG2)
        forlengVedtak(1.februar til 28.februar, AG1, AG2)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)

        håndterOverstyrTidslinje((30.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG1)

        inspektør(AG1) {
            assertIngenFunksjonelleFeil()
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING
            )
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }

        inspektør(AG2) {
            assertIngenFunksjonelleFeil()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `overstyr utkast til revurdering flere ag - andre ag mottar overstyring`() {
        nyeVedtak(1.januar, 31.januar, AG1, AG2)
        forlengVedtak(1.februar til 28.februar, AG1, AG2)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((29.januar til 29.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)

        håndterOverstyrTidslinje((30.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG2)

        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)

        inspektør(AG1) {
            val utbetalingstidslinje = sisteUtbetalingUtbetalingstidslinje()
            assertUtbetalingsdag(utbetalingstidslinje[29.januar], expectedDagtype = Fridag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[30.januar], expectedDagtype = NavDag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[31.januar], expectedDagtype = NavDag::class, 50)
            assertIngenFunksjonelleFeil()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }

        inspektør(AG2) {
            val utbetalingstidslinje = sisteUtbetalingUtbetalingstidslinje()
            assertUtbetalingsdag(utbetalingstidslinje[29.januar], expectedDagtype = NavDag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[30.januar], expectedDagtype = Fridag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[31.januar], expectedDagtype = Fridag::class, 50)
            assertIngenFunksjonelleFeil()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `overstyr utkast til revurdering flere ag - andre ag mottar overstyring i siste periode`() {
        nyeVedtak(1.januar, 31.januar, AG1, AG2)
        forlengVedtak(1.februar til 28.februar, AG1, AG2)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)

        håndterOverstyrTidslinje((1.februar til 2.februar).map { manuellFeriedag(it) }, orgnummer = AG2)

        inspektør(AG1) {
            val utbetalingstidslinje = sisteUtbetalingUtbetalingstidslinje()
            assertUtbetalingsdag(utbetalingstidslinje[29.januar], expectedDagtype = Fridag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[30.januar], expectedDagtype = Fridag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[31.januar], expectedDagtype = Fridag::class, 50)

            assertIngenFunksjonelleFeil()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }

        inspektør(AG2) {
            val utbetalingstidslinje = sisteUtbetalingUtbetalingstidslinje()
            assertUtbetalingsdag(utbetalingstidslinje[29.januar], expectedDagtype = NavDag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[30.januar], expectedDagtype = NavDag::class, 50)
            assertUtbetalingsdag(utbetalingstidslinje[31.januar], expectedDagtype = NavDag::class, 50)

            assertIngenFunksjonelleFeil()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `overstyr utkast til revurdering flere ag - kan overstyre periode i én ag dersom annen ag er revurdert ferdig`() {
        nyeVedtak(1.januar, 31.januar, AG1, AG2)
        forlengVedtak(1.februar til 28.februar, AG1, AG2)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalt(orgnummer = AG1)

        håndterOverstyrTidslinje((1.februar til 2.februar).map { manuellFeriedag(it) }, orgnummer = AG2)
        inspektør(AG1) {
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                TIL_UTBETALING,
                AVSLUTTET
            )
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
        inspektør(AG2) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `revurder revurdering flere ag - kan ikke revurdere én ag hvis en annen ag ikke er ferdig revurdert`() {
        nyeVedtak(1.januar, 31.januar, AG1, AG2)
        forlengVedtak(1.februar til 28.februar, AG1, AG2)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) }, orgnummer = AG1)
        håndterYtelser(1.vedtaksperiode, orgnummer = AG1)
        håndterSimulering(1.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = AG1)
        håndterUtbetalt(orgnummer = AG1)

        håndterOverstyrTidslinje((1.februar til 2.februar).map { manuellFeriedag(it) }, orgnummer = AG1)
        inspektør(AG1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
        inspektør(AG2) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
        håndterYtelser(1.vedtaksperiode, orgnummer = AG2)
        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = AG2)
        inspektør(AG1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        }
        inspektør(AG2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }
    }
}
