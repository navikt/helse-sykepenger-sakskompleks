package no.nav.helse.spleis.e2e

import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.*
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UtbetalingFeiletE2ETest : AbstractEndToEndTest() {

    @Test
    fun `revurdering feilet med ett oppdrag status avvist som bygger på tidligere`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt(Oppdragstatus.AVVIST)

        håndterUtbetalingpåminnelse(2, AVVENTER_ARBEIDSGIVERKVITTERING)
        håndterUtbetalt()

        val første = inspektør.utbetaling(0)
        inspektør.utbetaling(2).inspektør.also { utbetalingInspektør ->
            assertEquals(utbetalingInspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
            assertEquals(Endringskode.ENDR, utbetalingInspektør.arbeidsgiverOppdrag.inspektør.endringskode)
            assertEquals(2, utbetalingInspektør.arbeidsgiverOppdrag.size)
            assertTrue(utbetalingInspektør.arbeidsgiverOppdrag.harUtbetalinger())
            assertEquals(17.januar, utbetalingInspektør.arbeidsgiverOppdrag[0].fom)
            assertEquals(30.januar, utbetalingInspektør.arbeidsgiverOppdrag[0].tom)
            assertEquals(1.februar, utbetalingInspektør.arbeidsgiverOppdrag[1].fom)
            assertEquals(28.februar, utbetalingInspektør.arbeidsgiverOppdrag[1].tom)
            assertTrue(utbetalingInspektør.arbeidsgiverOppdrag[0].erForskjell())
            assertTrue(utbetalingInspektør.arbeidsgiverOppdrag[1].erForskjell())
        }
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVSLUTTET
        )
        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `utbetaling feilet med ett oppdrag status avvist`() {
        tilGodkjent(1.januar, 31.januar, 100.prosent, 1.januar)
        håndterUtbetalt(status = Oppdragstatus.AVVIST)
        nullstillTilstandsendringer()

        håndterUtbetalingpåminnelse(0, AVVENTER_ARBEIDSGIVERKVITTERING)
        håndterUtbetalt()

        assertEquals(1, inspektør.utbetalinger.size)
        assertEquals(UTBETALT, inspektør.utbetaling(0).inspektør.tilstand)

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `utbetaling feilet med ett oppdrag status avvist som bygger på tidligere`() {
        nyttVedtak(1.januar, 31.januar)
        forlengTilGodkjentVedtak(1.februar, 28.februar)
        håndterUtbetalt(status = Oppdragstatus.AVVIST)
        nullstillTilstandsendringer()
        håndterUtbetalingpåminnelse(1, AVVENTER_ARBEIDSGIVERKVITTERING)
        håndterUtbetalt()

        assertEquals(2, inspektør.utbetalinger.size)
        assertEquals(UTBETALT, inspektør.utbetaling(1).inspektør.tilstand)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `utbetaling feilet med ett oppdrag status avvist og ett som er ok`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(1.januar til 16.januar), refusjon = Inntektsmelding.Refusjon(INNTEKT/2, null, emptyList()))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT,
            fagsystemId = inspektør.utbetaling(0).inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId()
        )
        håndterUtbetalt(
            status = Oppdragstatus.AVVIST,
            fagsystemId = inspektør.utbetaling(0).inspektør.personOppdrag.inspektør.fagsystemId()
        )
        nullstillTilstandsendringer()

        håndterUtbetalingpåminnelse(0, AVVENTER_PERSONKVITTERING)
        assertEquals(1, hendelselogg.behov().size)
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT,
            fagsystemId = inspektør.utbetaling(0).inspektør.personOppdrag.inspektør.fagsystemId()
        )

        assertEquals(1, inspektør.utbetalinger.size)
        assertEquals(UTBETALT, inspektør.utbetaling(0).inspektør.tilstand)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `utbetaling feilet med ett oppdrag status ok og ett som er avvist`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(1.januar til 16.januar), refusjon = Inntektsmelding.Refusjon(INNTEKT/2, null, emptyList()))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(
            status = Oppdragstatus.AVVIST,
            fagsystemId = inspektør.utbetaling(0).inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId()

        )
        nullstillTilstandsendringer()

        assertEquals(AVVENTER_KVITTERINGER, inspektør.utbetaling(0).inspektør.tilstand)
        håndterUtbetalingpåminnelse(0, AVVENTER_KVITTERINGER)
        assertEquals(2, hendelselogg.behov().size)
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT,
            fagsystemId = inspektør.utbetaling(0).inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId()
        )
        assertEquals(AVVENTER_PERSONKVITTERING, inspektør.utbetaling(0).inspektør.tilstand)
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT,
            fagsystemId = inspektør.utbetaling(0).inspektør.personOppdrag.inspektør.fagsystemId()
        )

        assertEquals(1, inspektør.utbetalinger.size)
        assertEquals(UTBETALT, inspektør.utbetaling(0).inspektør.tilstand)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `nyere perioder må vente til periode med feilet utbetaling er ok`() {
        nyttVedtak(1.januar, 31.januar, status = Oppdragstatus.AVVIST)
        nyPeriode(1.mars til 31.mars)
        håndterInntektsmelding(listOf(1.mars til 16.mars))

        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)
    }
}
