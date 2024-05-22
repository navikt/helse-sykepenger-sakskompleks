package no.nav.helse.spleis.e2e.behandlinger

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.tilGodkjenning
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.ANNULLERT_PERIODE
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.TIL_INFOTRYGD
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BehandlingForkastetEventTest : AbstractDslTest() {

    @Test
    fun `uberegnet behandling forkastes`() {
        a1 {
            tilGodkjenning(1.januar, 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
            val behandlingForkastetEvent = observatør.behandlingForkastetEventer.single()
            val sisteBehandling = inspektørForkastet(1.vedtaksperiode).behandlinger.single()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId,
                automatiskBehandling = true
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertEquals(TIL_INFOTRYGD, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingForkastetEvent)
        }
    }
    @Test
    fun `uberegnet behandling forkastes manuelt`() {
        a1 {
            tilGodkjenning(1.januar, 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false, automatiskBehandling = false)
            val behandlingForkastetEvent = observatør.behandlingForkastetEventer.single()
            val sisteBehandling = inspektørForkastet(1.vedtaksperiode).behandlinger.single()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId,
                automatiskBehandling = false
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertEquals(TIL_INFOTRYGD, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingForkastetEvent)
        }
    }

    @Test
    fun `behandling uten vedtak forkastes`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
            håndterUtbetalingshistorikkEtterInfotrygdendring(listOf(ArbeidsgiverUtbetalingsperiode(a1, 1.januar, 10.januar, 100.prosent, 500.daglig)))
            val behandlingForkastetEvent = observatør.behandlingForkastetEventer.single()
            val sisteBehandling = inspektørForkastet(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId,
                automatiskBehandling = true
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertEquals(TIL_INFOTRYGD, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingForkastetEvent)
        }
    }

    @Test
    fun `annullering oppretter ny behandling som forkastes`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterAnnullering(inspektør.utbetalinger(1.vedtaksperiode).single().inspektør.utbetalingId)
            val behandlingForkastetEvent = observatør.behandlingForkastetEventer.single()
            val sisteBehandling = inspektørForkastet(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId,
                automatiskBehandling = false
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertEquals(ANNULLERT_PERIODE, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingForkastetEvent)
            val behandlingOpprettetEventer = observatør.behandlingOpprettetEventer
            assertEquals(2, behandlingOpprettetEventer.size)
            val sisteBehandlingOpprettet = behandlingOpprettetEventer.last()
            assertEquals(sisteBehandling.id, sisteBehandlingOpprettet.behandlingId)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering, sisteBehandlingOpprettet.type)
        }
    }

    @Test
    fun `annullering av åpnet revurdering endrer behandling som forkastes`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterAnnullering(inspektør.utbetalinger(1.vedtaksperiode).last().inspektør.utbetalingId)
            val behandlingForkastetEvent = observatør.behandlingForkastetEventer.single()
            val sisteBehandling = inspektørForkastet(1.vedtaksperiode).behandlinger.last()
            val forventetBehandlingId = sisteBehandling.id
            val forventetBehandlingEvent = PersonObserver.BehandlingForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = "42",
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = forventetBehandlingId,
                automatiskBehandling = false
            )
            assertTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertEquals(ANNULLERT_PERIODE, sisteBehandling.tilstand)
            assertEquals(forventetBehandlingEvent, behandlingForkastetEvent)
            val behandlingOpprettetEventer = observatør.behandlingOpprettetEventer
            assertEquals(2, behandlingOpprettetEventer.size)
            val sisteBehandlingOpprettet = behandlingOpprettetEventer.last()
            assertEquals(sisteBehandling.id, sisteBehandlingOpprettet.behandlingId)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering, sisteBehandlingOpprettet.type)
        }
    }
}
