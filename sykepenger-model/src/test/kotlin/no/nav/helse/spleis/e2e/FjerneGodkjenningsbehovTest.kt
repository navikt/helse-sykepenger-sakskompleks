package no.nav.helse.spleis.e2e

import no.nav.helse.assertForventetFeil
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.tilGodkjenning
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.IKKE_GODKJENT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.UTBETALT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FjerneGodkjenningsbehovTest: AbstractDslTest() {

    @Test
    fun `mottak av vedtak fattet fungerer på samme måte som godkjenningsbehov med tommel opp`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            håndterVedtakFattet(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertEquals(UTBETALT, inspektør.utbetalingtilstand(0))
        }
    }

    @Test
    fun `mottak av kan ikke behandles her fungerer på samme måte som godkjenningsbehov med tommel ned`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            nullstillTilstandsendringer()
            assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING)
            håndterKanIkkeBehandlesHer(1.vedtaksperiode)
            assertForkastetPeriodeTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
            assertEquals(IKKE_GODKJENT, inspektør.utbetalingtilstand(0))
        }
    }

    @Test
    fun `mottak av vedtak fattet fungerer på samme måte som godkjenningsbehov med tommel opp ved revurdering`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterVedtakFattet(1.vedtaksperiode)
            håndterUtbetalt()
            assertEquals(UTBETALT, inspektør.utbetalingtilstand(0))
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT * 1.1, forklaring = "forklaring")))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            håndterVedtakFattet(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertEquals(UTBETALT, inspektør.utbetalingtilstand(1))
        }
    }

    @Test
    fun `mottak av kan ikke behandles her fungerer på samme måte som godkjenningsbehov med tommel ned ved automatisk behandlet revurdering`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterVedtakFattet(1.vedtaksperiode)
            håndterUtbetalt()
            assertEquals(UTBETALT, inspektør.utbetalingtilstand(0))
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT * 1.1, forklaring = "forklaring")))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            håndterKanIkkeBehandlesHer(1.vedtaksperiode, automatisert = true)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            assertEquals(IKKE_GODKJENT, inspektør.utbetalingtilstand(1))
            assertInfo("Revurderingen ble avvist automatisk - hindrer tilstandsendring for å unngå saker som blir stuck", 1.vedtaksperiode.filter())
        }
    }

    @Test
    fun `mottak av kan ikke behandles her fungerer på samme måte som godkjenningsbehov med tommel ned ved manuelt behandlet revurdering`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterVedtakFattet(1.vedtaksperiode)
            håndterUtbetalt()
            assertEquals(UTBETALT, inspektør.utbetalingtilstand(0))
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT * 1.1, forklaring = "forklaring")))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            håndterKanIkkeBehandlesHer(1.vedtaksperiode, automatisert = false)
            assertSisteTilstand(1.vedtaksperiode, REVURDERING_FEILET)
            assertEquals(IKKE_GODKJENT, inspektør.utbetalingtilstand(1))
        }
    }

    @Test
    fun `mottak av vedtak fattet i 'Avsluttet' etter godkjent godkjennigsbehov`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)

            val nyeFunksjonelleFeil = nyeFunksjonelleFeil {
                håndterKanIkkeBehandlesHer(1.vedtaksperiode)
            }

            assertForventetFeil(
                forklaring = "Får funksjonelle feil på at vi ikke forventer godkjenning",
                nå = { assertTrue(nyeFunksjonelleFeil) },
                ønsket = { assertFalse(nyeFunksjonelleFeil) }
            )
        }
    }

    @Test
    fun `mottak av vedtak fattet i 'TilUtbetaling' etter godkjent godkjennigsbehov`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)
            assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING)

            val nyeFunksjonelleFeil = nyeFunksjonelleFeil {
                håndterKanIkkeBehandlesHer(1.vedtaksperiode)
            }

            assertForventetFeil(
                forklaring = "Får funksjonelle feil på at vi ikke forventer godkjenning",
                nå = { assertTrue(nyeFunksjonelleFeil) },
                ønsket = { assertFalse(nyeFunksjonelleFeil) }
            )
        }
    }

    @Test
    fun `mottak av kan ikke behandles her etter avvist godkjennigsbehov`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
            assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
            val utbetalingId = inspektør.utbetalingId { 1.vedtaksperiode }
            assertEquals(IKKE_GODKJENT, inspektør.utbetalinger.single { it.inspektør.utbetalingId == utbetalingId }.inspektør.tilstand)

            val nyeFunksjonelleFeil = nyeFunksjonelleFeil {
                håndterKanIkkeBehandlesHer(1.vedtaksperiode, utbetalingId)
            }

            assertForventetFeil(
                forklaring = "Får funksjonelle feil på at vi ikke forventer godkjenning",
                nå = { assertTrue(nyeFunksjonelleFeil) },
                ønsket = { assertFalse(nyeFunksjonelleFeil) }
            )
        }
    }
}