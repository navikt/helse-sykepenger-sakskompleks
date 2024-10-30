package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.antallEtterspurteBehov
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.Inntektsmelding.Avsendersystem.ALTINN
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
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
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_23
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertHarHendelseIder
import no.nav.helse.spleis.e2e.assertHarIkkeHendelseIder
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengPeriode
import no.nav.helse.spleis.e2e.forlengTilGodkjentVedtak
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterAnnullerUtbetaling
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterPåminnelse
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.spleis.e2e.manuellPermisjonsdag
import no.nav.helse.spleis.e2e.manuellSykedag
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.spleis.e2e.tilGodkjent
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevurderTidslinjeTest : AbstractEndToEndTest() {

    @Test
    fun `revurdere mens en forlengelse er til utbetaling`() {
        nyttVedtak(januar)
        forlengTilGodkjentVedtak(februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        assertNotNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en førstegangsbehandling er til utbetaling`() {
        tilGodkjent(januar, 100.prosent, 1.januar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertNotNull(observatør.avsluttetMedVedtakEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en førstegangsbehandling er til utbetaling - utbetalingen feiler`() {
        tilGodkjent(januar, 100.prosent, 1.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        nullstillTilstandsendringer()
        håndterUtbetalt(status = Oppdragstatus.AVVIST)
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
        assertNull(observatør.avsluttetMedVedtakEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en periode har feilet i utbetaling`() {
        nyttVedtak(januar)
        forlengTilGodkjentVedtak(februar)
        håndterUtbetalt(Oppdragstatus.FEIL)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
    }

    @Test
    fun `annullering etter revurdering med utbetaling feilet`() {
        nyttVedtak(3.januar til 26.januar)
        forlengVedtak(27.januar til 26.februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(26.januar, Dagtype.Feriedag)))

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(Oppdragstatus.AVVIST)

        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetaling(1).utbetalingId)
        assertTrue(hendelselogg.harFunksjonelleFeilEllerVerre()) { "kan pt. ikke annullere når siste utbetaling har feilet" }
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `annullering etter revurdering feilet`() {
        nyttVedtak(3.januar til 26.januar)
        forlengVedtak(29.januar til 26.februar)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(26.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetaling(1).utbetalingId)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, REVURDERING_FEILET, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, TIL_INFOTRYGD)
    }

    @Test
    fun `to perioder - revurder dager i eldste`() {
        nyttVedtak(3.januar til 26.januar)
        forlengVedtak(27.januar til 14.februar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((20.januar til 22.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertIngenFunksjonelleFeil()

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `to perioder - revurder dag i nyeste`() {
        nyttVedtak(3.januar til 26.januar)
        forlengVedtak(27.januar til 14.februar)

        assertEquals(6, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)
        assertEquals(19, inspektør.sisteMaksdato(2.vedtaksperiode).antallForbrukteDager)

        håndterOverstyrTidslinje((4.februar til 8.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(15, inspektør.sisteMaksdato(2.vedtaksperiode).antallForbrukteDager)

        assertIngenFunksjonelleFeil()
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
        )

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
        )
    }

    @Test
    fun `to perioder - hele den nyeste perioden blir ferie`() {
        nyttVedtak(3.januar til 26.januar)
        forlengVedtak(27.januar til 14.februar)

        assertEquals(6, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)
        assertEquals(19, inspektør.sisteMaksdato(2.vedtaksperiode).antallForbrukteDager)

        håndterOverstyrTidslinje((27.januar til 14.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(6, inspektør.sisteMaksdato(2.vedtaksperiode).antallForbrukteDager)
        assertIngenFunksjonelleFeil()

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
        )

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
        )
    }

    @Test
    fun `kan ikke utvide perioden med sykedager`() {
        nyttVedtak(3.januar til 26.januar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((25.januar til 14.februar).map { manuellSykedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVSLUTTET
        )

        assertEquals(Utbetalingstatus.GODKJENT_UTEN_UTBETALING, inspektør.utbetalingtilstand(1))
    }

    @Test
    fun `ledende uferdig periode`() {
        nyttVedtak(3.januar til 26.januar)
        håndterSykmelding(Sykmeldingsperiode(27.januar, 14.februar))
        håndterSøknad(Sykdom(27.januar, 14.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterOverstyrTidslinje((19.januar til 22.januar).map { manuellFeriedag(it) })  // ferie på første navdag
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        val revurdering = inspektør.utbetaling(2)
        assertIngenFunksjonelleFeil()
        assertEquals(2, revurdering.arbeidsgiverOppdrag.size)
        assertEquals(19.januar, revurdering.arbeidsgiverOppdrag[0].datoStatusFom)
        assertEquals(23.januar til 26.januar, revurdering.arbeidsgiverOppdrag[1].periode)
    }

    @Test
    fun `ledende uferdig periode som ikke har en utbetaling`() {
        nyttVedtak(3.januar til 26.januar)
        håndterSykmelding(Sykmeldingsperiode(27.januar, 14.februar))
        håndterSøknad(Sykdom(27.januar, 14.februar, 100.prosent))
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((6.januar til 10.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVSLUTTET
        )
        assertTilstander(
            2.vedtaksperiode,
            AVVENTER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `samme fagsystemId og forskjellig skjæringstidspunkt - revurder første periode i siste skjæringstidspunkt`() {
        nyttVedtak(3.januar til 26.januar)
        nyttVedtak(1.februar til 20.februar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        forlengVedtak(21.februar til 10.mars)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje((5.februar til 15.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
    }

    @Test
    fun `samme fagsystemId og forskjellig skjæringstidspunkt - revurder siste periode i siste skjæringstidspunkt`() {
        nyttVedtak(3.januar til 26.januar)
        nyttVedtak(1.februar til 20.februar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        forlengVedtak(21.februar til 10.mars)

        håndterOverstyrTidslinje((22.februar til 25.februar).map { manuellFeriedag(it) })
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            3.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `forsøk på å revurdere eldre fagsystemId med nyere perioder uten utbetaling, og periode med utbetaling etterpå`() {
        nyttVedtak(3.januar til 26.januar)
        tilGodkjenning(mai, 100.prosent, 1.mai, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        håndterOverstyrTidslinje((4.januar til 20.januar).map { manuellFeriedag(it) })
        inspektør.sykdomstidslinje.inspektør.also { sykdomstidslinjeInspektør ->
            assertTrue((4.januar til 20.januar).none { sykdomstidslinjeInspektør.dager[it] == Dag.Feriedag::class })
        }

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE
        )
    }

    @Test
    fun `revurderer siste utbetalte periode med bare ferie og permisjon`() {
        nyttVedtak(3.januar til 26.januar)
        nullstillTilstandsendringer()

        assertEquals(6, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)

        håndterOverstyrTidslinje((3.januar til 20.januar).map { manuellFeriedag(it) } + (21.januar til 26.januar).map { manuellPermisjonsdag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(0, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
        )

        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `revurderer siste utbetalte periode med bare ferie og permisjon - med tidligere utbetaling`() {
        nyttVedtak(3.januar til 26.januar)
        nyttVedtak(3.mars til 26.mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((3.mars til 20.mars).map { manuellFeriedag(it) } + (21.mars til 26.mars).map { manuellPermisjonsdag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrTidslinje((3.mars til 20.mars).map { manuellSykedag(it) })

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertEquals(4, inspektør.antallUtbetalinger)
        val førsteUtbetaling = inspektør.utbetaling(0)
        val andreUtbetaling = inspektør.utbetaling(1)
        val annulleringenAvMars = inspektør.utbetaling(2)
        val revurderingenAvMars = inspektør.utbetaling(3)

        assertEquals(andreUtbetaling.korrelasjonsId, annulleringenAvMars.korrelasjonsId)
        assertNotEquals(førsteUtbetaling.korrelasjonsId, andreUtbetaling.korrelasjonsId)
        assertEquals(andreUtbetaling.korrelasjonsId, revurderingenAvMars.korrelasjonsId)

        assertEquals(1, annulleringenAvMars.arbeidsgiverOppdrag.size)
        annulleringenAvMars.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(19.mars, linje.fom)
            assertEquals(26.mars, linje.tom)
            assertEquals(19.mars, linje.datoStatusFom)
            assertEquals("OPPH", linje.statuskode)
        }

        assertEquals(1, revurderingenAvMars.arbeidsgiverOppdrag.size)
        assertEquals(Endringskode.ENDR, revurderingenAvMars.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(3.mars til 26.mars, revurderingenAvMars.periode)
        revurderingenAvMars.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(19.mars til 20.mars, linje.fom til linje.tom)
        }
    }

    @Test
    fun `Avslag fører til feilet revurdering`() {
        håndterSykmelding(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 17.januar)), førsteFraværsdag = 1.januar, avsendersystem = ALTINN)
        håndterSøknad(januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            REVURDERING_FEILET
        )
    }

    @Test
    fun `Feilet simulering gir warning`() {
        nyttVedtak(januar)

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode, simuleringOK = false)
        nullstillTilstandsendringer()
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertIngenVarsel(Varselkode.RV_SI_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `annullering av feilet revurdering`() {
        håndterSykmelding(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 17.januar)), førsteFraværsdag = 1.januar, avsendersystem = ALTINN)
        håndterSøknad(januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(
            1.vedtaksperiode,
            foreldrepenger = listOf(GradertPeriode(16.januar til 28.januar, 100))
        )

        håndterAnnullerUtbetaling()

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `påminnet revurdering timer ikke ut`() {
        nyttVedtak(3.januar til 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, LocalDateTime.now().minusDays(14))

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
    }

    @Test
    fun `revurder med kun ferie`() {
        nyttVedtak(3.januar til 26.januar)
        forlengVedtak(27.januar til 13.februar)
        forlengPeriode(14.februar til 15.februar)

        håndterOverstyrTidslinje((27.januar til 13.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertTilstander(
            3.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `revurder en revurdering`() {
        nyttVedtak(3.januar til 26.januar)
        nullstillTilstandsendringer()

        assertEquals(6, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)

        håndterOverstyrTidslinje((20.januar til 22.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(5, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)

        håndterOverstyrTidslinje((23.januar til 23.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(4, inspektør.sisteMaksdato(1.vedtaksperiode).antallForbrukteDager)
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `tre utbetalte perioder - midterste blir revurdert - to siste perioder blir revurdert, første er urørt`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        forlengVedtak(mars)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje((15.februar til 28.februar).map { manuellFeriedag(it) })

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `tre utbetalte perioder - midterste blir revurdert og utbetalt - to siste perioder blir revurdert, første er urørt`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        forlengVedtak(mars)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje((15.februar til 28.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        assertIngenFunksjonelleFeil()
        assertEquals(0, inspektør.ikkeUtbetalteUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
        assertEquals(0, inspektør.ikkeUtbetalteUtbetalingerForVedtaksperiode(2.vedtaksperiode).size)
        assertEquals(0, inspektør.ikkeUtbetalteUtbetalingerForVedtaksperiode(3.vedtaksperiode).size)
        assertEquals(1, inspektør.avsluttedeUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
        assertEquals(2, inspektør.avsluttedeUtbetalingerForVedtaksperiode(2.vedtaksperiode).size)
        assertEquals(2, inspektør.avsluttedeUtbetalingerForVedtaksperiode(3.vedtaksperiode).size)
    }

    @Test
    fun `forleng uferdig revurdering`() {
        nyttVedtak(3.januar til 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(27.januar, 5.februar))
        håndterSøknad(Sykdom(27.januar, 5.februar, 100.prosent))

        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        val førsteUtbetaling = inspektør.utbetaling(0)
        val andreUtbetaling = inspektør.utbetaling(1)
        val tredjeUtbetaling = inspektør.utbetaling(2)

        assertEquals(førsteUtbetaling.korrelasjonsId, andreUtbetaling.korrelasjonsId)
        assertEquals(andreUtbetaling.korrelasjonsId, tredjeUtbetaling.korrelasjonsId)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `revurdering endrer arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent), Arbeid(25.januar, 26.januar))
        håndterInntektsmelding(listOf(3.januar til 18.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        // for at ferie skal regnes som del av arbeidsgiverperioden, må det være sykdom
        // på begge sider av feriedagene. i utgangspunktet er arbeidsgiverperioden 3. - 18. januar,
        // men for at feriedagene 16. - 18. januar skal telle med, må det være sykdom 27. januar.
        // Siden 27. januar hittil er ukjent for oss, regnes det som antatt arbeidsdag.
        // Altså blir arbeidsgiverperioden 1. januar - 15.januar med denne overstyringen, med 16. januar-26.januar
        // tellende som oppholdsdager
        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        // registrerer sykdomsdag 27. januar som gjør at arbeidsgiverperioden blir 3.januar - 18.januar,
        // siden det nå er sykdom på begge sider av feriedagene
        håndterSykmelding(Sykmeldingsperiode(27.januar, 5.februar))
        håndterSøknad(Sykdom(27.januar, 5.februar, 100.prosent))

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        val førsteUtbetaling = inspektør.utbetaling(0)
        val andreUtbetaling = inspektør.utbetaling(1)
        val tredjeUtbetaling = inspektør.utbetaling(2)

        assertEquals(førsteUtbetaling.korrelasjonsId, andreUtbetaling.korrelasjonsId)
        assertEquals(andreUtbetaling.korrelasjonsId, tredjeUtbetaling.korrelasjonsId)
    }

    @Test
    fun `forlengelse samtidig som en aktiv revurdering hvor forlengelsen sin IM flytter skjæringstidspunktet til aktive revurderingen`() {
        nyttVedtak(3.januar til 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(30.januar, 5.februar))
        assertEquals("SSSHH SSSSSHH SFFFFFF FFFFF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 30.januar, avsendersystem = ALTINN)
        assertEquals("UUSSSHH SSSSSHH SFFFFFF FFFFF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(30.januar, 5.februar, 100.prosent))

        assertTilstander(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING_REVURDERING)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `feilet revurdering blokkerer videre behandling`() {
        nyttVedtak(3.januar til 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        håndterSykmelding(Sykmeldingsperiode(27.januar, 5.februar))
        håndterSøknad(Sykdom(27.januar, 5.februar, 100.prosent))

        assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `etterspør ytelser ved påminnelser i avventer_historikk_revurdering`() {
        nyttVedtak(januar)
        assertEtterspurteYtelser(1, 1.vedtaksperiode)

        håndterOverstyrTidslinje((25.januar til 26.januar).map { manuellFeriedag(it) })
        assertEtterspurteYtelser(2, 1.vedtaksperiode)

        håndterPåminnelse(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertEtterspurteYtelser(3, 1.vedtaksperiode)
    }

    @Test
    fun `Håndter påminnelser i alle tilstandene knyttet til en revurdering med en arbeidsgiver`() {
        nyttVedtak(januar)
        håndterOverstyrTidslinje((25.januar til 26.januar).map { manuellFeriedag(it) })
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertEtterspurteYtelser(3, 1.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertEquals(3, person.personLogg.antallEtterspurteBehov(1.vedtaksperiode, Aktivitet.Behov.Behovtype.Simulering))

        håndterSimulering(1.vedtaksperiode)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertEquals(3, person.personLogg.antallEtterspurteBehov(1.vedtaksperiode, Aktivitet.Behov.Behovtype.Godkjenning))
    }

    @Test
    fun `validering av infotrygdhistorikk i revurdering skal føre til en warning i stedet for en feilet revurdering`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, 1000.daglig),
            inntektshistorikk = listOf(
                Inntektsopplysning(
                    ORGNUMMER,
                    17.januar, INNTEKT, true
                )
            )
        )
        håndterOverstyrTidslinje((20.januar til 26.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(1.vedtaksperiode))

        val utbetaling1 = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(1)

        assertEquals(utbetaling1.korrelasjonsId, revurdering.korrelasjonsId)
        val oppdragInspektør = revurdering.arbeidsgiverOppdrag.inspektør
        assertEquals(Endringskode.ENDR, oppdragInspektør.endringskode)
        // infotrygd-dagene påvirker ikke resultatet. oppdraget kortes ned mht feriedagene
        assertEquals(2, oppdragInspektør.antallLinjer())
        revurdering.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(Endringskode.ENDR, linje.endringskode)
            assertEquals(17.januar til 19.januar, linje.fom til linje.tom)
            assertEquals(null, linje.datoStatusFom)
            assertEquals(null, linje.statuskode)
        }
        revurdering.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(Endringskode.NY, linje.endringskode)
            assertEquals(27.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(null, linje.datoStatusFom)
            assertEquals(null, linje.statuskode)
        }

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertVarsel(RV_IT_3, AktivitetsloggFilter.person())
    }

    @Test
    fun `warning dersom det er utbetalt en periode i Infotrygd etter perioden som revurderes nå`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.februar, 28.februar, 100.prosent, 1000.daglig),
            inntektshistorikk = listOf(
                Inntektsopplysning(
                    ORGNUMMER,
                    1.februar, INNTEKT, true
                )
            )
        )
        håndterOverstyrTidslinje((20.januar til 26.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertVarsel(RV_IT_1, AktivitetsloggFilter.person())
    }

    @Test
    fun `revurderer siste utbetalte periode`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 18.januar)), førsteFraværsdag = 2.januar, avsendersystem = ALTINN)
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()
        håndterOverstyrTidslinje(listOf(manuellSykedag(26.januar, 80)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        assertEquals(Utbetalingstatus.UTBETALT, inspektør.utbetalingtilstand(0))
        assertEquals(Utbetalingstatus.OVERFØRT, inspektør.utbetalingtilstand(1))
        assertEquals(inspektør.utbetaling(0).arbeidsgiverOppdrag.fagsystemId, inspektør.utbetaling(1).arbeidsgiverOppdrag.fagsystemId)
        inspektør.utbetaling(1).arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(2, oppdrag.size)
            assertEquals(18.januar, oppdrag[0].fom)
            assertEquals(25.januar, oppdrag[0].tom)
            assertEquals(100, oppdrag[0].grad)

            assertEquals(26.januar, oppdrag[1].fom)
            assertEquals(26.januar, oppdrag[1].tom)
            assertEquals(80, oppdrag[1].grad)
        }
    }

    @Test
    fun `revurderer siste utbetalte periode i en forlengelse med bare ferie og permisjon`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 18.januar)), førsteFraværsdag = 2.januar, avsendersystem = ALTINN)
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar))
        håndterSøknad(Sykdom(29.januar, 23.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((29.januar til 15.februar).map { manuellFeriedag(it) } + (16.februar til 23.februar).map { manuellPermisjonsdag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        assertEquals(Utbetalingstatus.UTBETALT, inspektør.utbetalingtilstand(0))
        assertEquals(Utbetalingstatus.UTBETALT, inspektør.utbetalingtilstand(1))
        assertEquals(Utbetalingstatus.OVERFØRT, inspektør.utbetalingtilstand(2))
        assertEquals(inspektør.utbetaling(1).arbeidsgiverOppdrag.fagsystemId, inspektør.utbetaling(2).arbeidsgiverOppdrag.fagsystemId)
        inspektør.utbetaling(2).arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(1, oppdrag.size)
            assertEquals(18.januar, oppdrag[0].fom)
            assertEquals(26.januar, oppdrag[0].tom)
            assertTrue(oppdrag[0].erForskjell())
            assertEquals(100, oppdrag[0].grad)
        }
    }

    @Test
    fun `oppdager nye utbetalte dager fra infotrygd i revurderingen`() {
        håndterSykmelding(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)), avsendersystem = ALTINN)
        håndterSøknad(januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.november(2017), 30.november(2017), 100.prosent, 32000.månedlig),
            inntektshistorikk = listOf(
                Inntektsopplysning(
                    orgnummer = ORGNUMMER, sykepengerFom = 1.november, 32000.månedlig, refusjonTilArbeidsgiver = true
                )
            )
        )
        håndterOverstyrTidslinje(overstyringsdager = listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)

        assertVarsel(RV_UT_23, 1.vedtaksperiode.filter())
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )
    }

    @Test
    fun `Kun periode berørt av endringene skal ha hendelseIden - forlengelse får hendelseId`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        val hendelseId = UUID.randomUUID()
        håndterOverstyrTidslinje(
            meldingsreferanseId = hendelseId,
            overstyringsdager = (30.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) }
        )
        assertEquals(januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(februar, inspektør.periode(2.vedtaksperiode))
        assertHarHendelseIder(1.vedtaksperiode, hendelseId)
        assertHarIkkeHendelseIder(2.vedtaksperiode, hendelseId)
    }

    @Test
    fun `Kun periode berørt av endringene skal ha hendelseIden`() {
        nyttVedtak(januar)
        nyttVedtak(2.februar til 28.februar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        val hendelseId = UUID.randomUUID()
        håndterOverstyrTidslinje(
            meldingsreferanseId = hendelseId,
            overstyringsdager = (30.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) }
        )
        assertEquals(januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(2.februar til 28.februar, inspektør.periode(2.vedtaksperiode))
        assertHarHendelseIder(1.vedtaksperiode, hendelseId)
        assertHarIkkeHendelseIder(2.vedtaksperiode, hendelseId)
    }

    @Test
    fun `revurder første dag i periode på en sykedag som forlenger tidligere arbeidsgiverperiode med nytt skjæringstidspunkt`() {
        nyttVedtak(1.januar til 20.januar)
        håndterSøknad(Sykdom(25.januar, 25.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 25.januar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(25.januar, Dagtype.Sykedag, 80)))
        håndterYtelser(2.vedtaksperiode)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
        )
    }

    @Test
    fun `Revurdering med nyere IT historikk (frem i tid)`() {
        /*
        Når periode ble revurdert dukker det opp en utbetaling etter vedtaksperioden vi ikke kjente til før. Siden vi har utbetalingsdager i infotrygd prøver
        vi å beregne utbetaling uten å ha lagret en inntekt. Det fører til en error i aktivitetsloggen som igjen gjør at perioden ender opp i RevurderingFeilet
        og speil er i en tilstand hvor ting ikke er oppdatert i saksbildet
         */
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.mars, 31.mars, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.mars, INNTEKT, true))
        )
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.januar, Dagtype.Sykedag, 80)))
        håndterYtelser(1.vedtaksperiode)
        assertVarsel(RV_IT_1, 1.vedtaksperiode.filter())
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
    }

    private fun assertEtterspurteYtelser(expected: Int, vedtaksperiodeIdInnhenter: IdInnhenter) {
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Foreldrepenger))
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Pleiepenger))
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Omsorgspenger))
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Opplæringspenger))
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Arbeidsavklaringspenger))
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Dagpenger))
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Institusjonsopphold))
    }


}
