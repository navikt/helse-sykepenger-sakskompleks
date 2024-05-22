package no.nav.helse.spleis.e2e.behandlinger

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.tilGodkjenning
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.AVSLUTTET_UTEN_VEDTAK
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.REVURDERT_VEDTAK_AVVIST
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.TIL_INFOTRYGD
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.VEDTAK_FATTET
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.VEDTAK_IVERKSATT
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.onsdag
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType
import no.nav.helse.søndag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BehandlingLukketEventTest : AbstractDslTest() {

    @Test
    fun `behandling lukkes når vedtak fattes`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)
            val behandlingLukketEvent = observatør.behandlingLukketEventer.single()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.single()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_UTBETALING)
            assertEquals(VEDTAK_FATTET, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes ikke når vedtak avvises`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
            assertEquals(0, observatør.behandlingLukketEventer.size)
            val sisteBehandling = inspektørForkastet(1.vedtaksperiode).behandlinger.single()
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertEquals(TIL_INFOTRYGD, sisteBehandling.tilstand)
        }
    }

    @Test
    fun `behandling lukkes når revurdert vedtak avvises`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
            val behandlinger = inspektør(1.vedtaksperiode).behandlinger
            assertEquals(2, observatør.behandlingLukketEventer.size)
            assertEquals(2, behandlinger.size)
            val sisteBehandling = behandlinger.last()
            assertTilstand(1.vedtaksperiode, TilstandType.AVVENTER_GODKJENNING_REVURDERING)
            assertEquals(REVURDERT_VEDTAK_AVVIST, sisteBehandling.tilstand)
        }
    }

    @Test
    fun `behandling lukkes når vedtak uten utbetaling fattes`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)
            val behandlingLukketEvent = observatør.behandlingLukketEventer.single()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.single()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET)
            assertEquals(VEDTAK_IVERKSATT, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes når periode går til auu`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
            val behandlingLukketEvent = observatør.behandlingLukketEventer.single()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.single()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET_UTEN_UTBETALING)
            assertEquals(AVSLUTTET_UTEN_VEDTAK, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes når revurdering fattes`() {
        a1 {
            nyttVedtak(1.januar til onsdag(31.januar))
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(onsdag den 31.januar, Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)

            val behandlingLukketEvent = observatør.behandlingLukketEventer.last()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_UTBETALING)
            assertEquals(VEDTAK_FATTET, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes når revurdering avvises`() {
        a1 {
            nyttVedtak(1.januar til onsdag(31.januar))
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(onsdag(31.januar), Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)

            val behandlingLukketEvent = observatør.behandlingLukketEventer.last()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.AVVENTER_GODKJENNING_REVURDERING)
            assertEquals(REVURDERT_VEDTAK_AVVIST, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes når revurdering uten utbetaling fattes`() {
        a1 {
            nyttVedtak(1.januar til søndag(28.januar))
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(søndag(28.januar), Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)

            val behandlingLukketEvent = observatør.behandlingLukketEventer.last()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET)
            assertEquals(VEDTAK_IVERKSATT, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes når revurdering gjør om til auu - med tidligere utbetaling`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterOverstyrTidslinje((17.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)
            håndterUtbetalt()

            val behandlingLukketEvent = observatør.behandlingLukketEventer.last()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET)
            assertEquals(VEDTAK_IVERKSATT, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }

    @Test
    fun `behandling lukkes når vedtak uten utbetaling fattes - uten tidligere utbetaling`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)

            håndterOverstyrTidslinje((17.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = true)

            val behandlingLukketEvent = observatør.behandlingLukketEventer.last()
            val sisteBehandling = inspektør(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingLukketEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId
            )
            assertTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET)
            assertEquals(VEDTAK_IVERKSATT, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingLukketEvent)
        }
    }
}
