package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.antallEtterspurteBehov
import no.nav.helse.assertForventetFeil
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
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
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_1
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
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengPeriode
import no.nav.helse.spleis.e2e.forlengTilGodkjentVedtak
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterAnnullerUtbetaling
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterInntektsmeldingMedValidering
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class RevurderTidslinjeTest : AbstractEndToEndTest() {

    @Test
    fun `revurdere mens en forlengelse er til utbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        forlengTilGodkjentVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertNotNull(observatør.vedtakFattetEvent[2.vedtaksperiode.id(ORGNUMMER)])
    }
    @Test
    fun `revurdere mens en førstegangsbehandling er til utbetaling`() {
        tilGodkjent(1.januar, 31.januar, 100.prosent, 1.januar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertNotNull(observatør.vedtakFattetEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en førstegangsbehandling er til utbetaling - utbetalingen feiler`() {
        tilGodkjent(1.januar, 31.januar, 100.prosent, 1.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        nullstillTilstandsendringer()
        håndterUtbetalt(status = Oppdragstatus.AVVIST)
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
        assertNull(observatør.vedtakFattetEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en periode har feilet i utbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        forlengTilGodkjentVedtak(1.februar, 28.februar)
        håndterUtbetalt(Oppdragstatus.FEIL)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
    }

    @Test
    fun `annullering etter revurdering med utbetaling feilet`() {
        nyttVedtak(3.januar, 26.januar)
        forlengVedtak(27.januar, 26.februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(26.januar, Dagtype.Feriedag)))

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt(Oppdragstatus.AVVIST)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertTrue(hendelselogg.harFunksjonelleFeilEllerVerre()) { "kan pt. ikke annullere når siste utbetaling har feilet" }
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING
        )
    }

    @Test
    fun `annullering etter revurdering feilet`() {
        nyttVedtak(3.januar, 26.januar)
        forlengVedtak(29.januar, 26.februar)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(26.januar, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(fagsystemId = inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertForkastetPeriodeTilstander(1.vedtaksperiode, REVURDERING_FEILET)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, REVURDERING_FEILET)
    }

    @Test
    fun `to perioder - revurder dager i eldste`() {
        nyttVedtak(3.januar, 26.januar)
        forlengVedtak(27.januar, 14.februar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((20.januar til 22.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertIngenFunksjonelleFeil()

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
    fun `to perioder - revurder dag i nyeste`() {
        nyttVedtak(3.januar, 26.januar)
        forlengVedtak(27.januar, 14.februar)

        håndterOverstyrTidslinje((4.februar til 8.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertIngenFunksjonelleFeil()
        assertTilstander(
            0,
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
            1,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
        )

        assertEquals(3, inspektør.utbetalinger.size)
        assertEquals(6, inspektør.forbrukteSykedager(0))
        assertEquals(19, inspektør.forbrukteSykedager(1))
        assertEquals(15, inspektør.forbrukteSykedager(2))
    }

    @Test
    fun `to perioder - hele den nyeste perioden blir ferie`() {
        nyttVedtak(3.januar, 26.januar)
        forlengVedtak(27.januar, 14.februar)

        håndterOverstyrTidslinje((27.januar til 14.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertIngenFunksjonelleFeil()

        assertTilstander(
            0,
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
            1,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
        )

        assertEquals(3, inspektør.utbetalinger.size)
        assertEquals(6, inspektør.forbrukteSykedager(0))
        assertEquals(19, inspektør.forbrukteSykedager(1))
        assertEquals(6, inspektør.forbrukteSykedager(2))
    }

    @Test
    fun `kan ikke utvide perioden med sykedager`() {
        nyttVedtak(3.januar, 26.januar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((25.januar til 14.februar).map { manuellSykedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVSLUTTET
        )

        assertEquals(Utbetalingstatus.GODKJENT_UTEN_UTBETALING, inspektør.utbetalingtilstand(1))
    }

    @Test
    fun `ferie i arbeidsgiverperiode`() {
        nyttVedtak(3.januar, 26.januar)
        håndterSykmelding(Sykmeldingsperiode(27.januar, 14.februar))
        håndterSøknad(Sykdom(27.januar, 14.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterOverstyrTidslinje((6.januar til 9.januar).map { manuellFeriedag(it) })  // ferie på første navdag
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
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

        assertIngenFunksjonelleFeil()
        assertEquals(3, inspektør.utbetalinger.size)
        assertFalse(inspektør.utbetaling(2).harUtbetalinger())
    }

    @Test
    fun `ledende uferdig periode`() {
        nyttVedtak(3.januar, 26.januar)
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
        assertEquals(2, revurdering.inspektør.arbeidsgiverOppdrag.size)
        assertEquals(19.januar, revurdering.inspektør.arbeidsgiverOppdrag[0].datoStatusFom())
        assertEquals(23.januar til 26.januar, revurdering.inspektør.arbeidsgiverOppdrag[1].periode)
    }

    @Test
    fun `ledende uferdig periode som ikke har en utbetaling`() {
        nyttVedtak(3.januar, 26.januar)
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
        nyttVedtak(3.januar, 26.januar)
        nyttVedtak(1.februar, 20.februar)
        forlengVedtak(21.februar, 10.mars)

        håndterOverstyrTidslinje((5.februar til 15.februar).map { manuellFeriedag(it) })
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `samme fagsystemId og forskjellig skjæringstidspunkt - revurder siste periode i siste skjæringstidspunkt`() {
        nyttVedtak(3.januar, 26.januar)
        nyttVedtak(1.februar, 20.februar)
        forlengVedtak(21.februar, 10.mars)

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
            3.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
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
    fun `forsøk på å revurdere eldre fagsystemId med nyere perioder uten utbetaling, og periode med utbetaling etterpå`() {
        nyttVedtak(3.januar, 26.januar)
        tilGodkjenning(1.mai, 31.mai, 100.prosent, 1.mai)

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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
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
        nyttVedtak(3.januar, 26.januar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((3.januar til 20.januar).map { manuellFeriedag(it) } + (21.januar til 26.januar).map { manuellPermisjonsdag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_VILKÅRSPRØVING_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
        )

        assertIngenFunksjonelleFeil()
        assertEquals(2, inspektør.utbetalinger.size)
        assertEquals(6, inspektør.forbrukteSykedager(0))
        assertEquals(0, inspektør.forbrukteSykedager(1))
    }

    @Test
    fun `revurderer siste utbetalte periode med bare ferie og permisjon - med tidligere utbetaling`() {
        nyttVedtak(3.januar, 26.januar)
        nyttVedtak(3.mars, 26.mars)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((3.mars til 20.mars).map { manuellFeriedag(it) } + (21.mars til 26.mars).map { manuellPermisjonsdag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        assertForventetFeil(
            forklaring = "Har laget en overlappende utbetaling fordi det ikke er noen ArbeidsgiverperiodeDag før januar." +
                    "OppdragBuilder stopper først ved ArbeidsgiverperiodeDag eller UkjentDag",
            nå = {
                assertThrows<IllegalStateException>("Har laget en overlappende utbetaling") {
                    håndterYtelser(2.vedtaksperiode)
                }
            },
            ønsket = {
                assertDoesNotThrow { håndterYtelser(2.vedtaksperiode) }
                val andreUtbetaling = inspektør.utbetaling(1).inspektør
                val tredjeUtbetaling = inspektør.utbetaling(2).inspektør
                assertEquals(andreUtbetaling.korrelasjonsId, tredjeUtbetaling.korrelasjonsId)
                assertEquals(1, tredjeUtbetaling.arbeidsgiverOppdrag.size)
                tredjeUtbetaling.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                    assertEquals(18.mars, linje.fom)
                    assertEquals(26.mars, linje.tom)
                    assertEquals(18.mars, linje.datoStatusFom)
                    assertEquals("OPPH", linje.statuskode)
                }
            }
        )
    }

    @Test
    fun `Avslag fører til feilet revurdering`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterInntektsmelding(listOf(Periode(1.januar, 17.januar)), førsteFraværsdag = 1.januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, besvart = LocalDateTime.now().minusHours(24))
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            REVURDERING_FEILET
        )
        assertVarsel(RV_UT_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Feilet simulering gir warning`() {
        nyttVedtak(1.januar, 31.januar)

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode, simuleringOK = false)
        nullstillTilstandsendringer()
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertIngenVarsel(Varselkode.RV_SI_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `annullering av feilet revurdering`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterInntektsmelding(listOf(Periode(1.januar, 17.januar)), førsteFraværsdag = 1.januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(
            1.vedtaksperiode,
            foreldrepenger = 16.januar til 28.januar
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )
    }

    @Test
    fun `påminnet revurdering timer ikke ut`() {
        nyttVedtak(3.januar, 26.januar)

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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
    }

    @Test
    fun `revurder med kun ferie`() {
        nyttVedtak(3.januar, 26.januar)
        forlengVedtak(27.januar, 13.februar)
        forlengPeriode(14.februar, 15.februar)

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
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
        nyttVedtak(3.januar, 26.januar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((20.januar til 22.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((23.januar til 23.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertIngenFunksjonelleFeil()
        assertEquals(6, inspektør.forbrukteSykedager(0))
        assertEquals(5, inspektør.forbrukteSykedager(1))
        assertEquals(4, inspektør.forbrukteSykedager(2))
    }

    @Test
    fun `tre utbetalte perioder - midterste blir revurdert - to siste perioder blir revurdert, første er urørt`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje((15.februar til 28.februar).map { manuellFeriedag(it) })

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
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING
        )
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `tre utbetalte perioder - midterste blir revurdert og utbetalt - to siste perioder blir revurdert, første er urørt`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje((15.februar til 28.februar).map { manuellFeriedag(it) })
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
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
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
        nyttVedtak(3.januar, 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(27.januar, 5.februar))
        håndterSøknad(Sykdom(27.januar, 5.februar, 100.prosent))

        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        val førsteUtbetaling = inspektør.utbetaling(0).inspektør
        val andreUtbetaling = inspektør.utbetaling(1).inspektør
        val tredjeUtbetaling = inspektør.utbetaling(2).inspektør

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

        val førsteUtbetaling = inspektør.utbetaling(0).inspektør
        val andreUtbetaling = inspektør.utbetaling(1).inspektør
        val tredjeUtbetaling = inspektør.utbetaling(2).inspektør

        assertEquals(førsteUtbetaling.korrelasjonsId, andreUtbetaling.korrelasjonsId)
        assertEquals(18.januar, førsteUtbetaling.arbeidsgiverOppdrag.inspektør.sisteArbeidsgiverdag)
        assertEquals(andreUtbetaling.korrelasjonsId, tredjeUtbetaling.korrelasjonsId)
        assertEquals(15.januar, andreUtbetaling.arbeidsgiverOppdrag.inspektør.sisteArbeidsgiverdag)
        assertEquals(18.januar, tredjeUtbetaling.arbeidsgiverOppdrag.inspektør.sisteArbeidsgiverdag)
    }

    @Test
    fun `forlengelse samtidig som en aktiv revurdering hvor forlengelsen sin IM flytter skjæringstidspunktet til aktive revurderingen`() {
        nyttVedtak(3.januar, 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(30.januar, 5.februar))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 30.januar)

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(30.januar, 5.februar, 100.prosent))

        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)

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
    }

    @Test
    fun `feilet revurdering blokkerer videre behandling`() {
        nyttVedtak(3.januar, 26.januar)

        håndterOverstyrTidslinje((16.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        håndterSykmelding(Sykmeldingsperiode(27.januar, 5.februar))
        håndterSøknad(Sykdom(27.januar, 5.februar, 100.prosent))

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE
        )
    }

    @Test
    fun `etterspør ytelser ved påminnelser i avventer_historikk_revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        assertEtterspurteYtelser(1, 1.vedtaksperiode)

        håndterOverstyrTidslinje((25.januar til 26.januar).map { manuellFeriedag(it) })
        assertEtterspurteYtelser(2, 1.vedtaksperiode)

        håndterPåminnelse(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertEtterspurteYtelser(3, 1.vedtaksperiode)
    }

    @Test
    fun `Håndter påminnelser i alle tilstandene knyttet til en revurdering med en arbeidsgiver`() {
        nyttVedtak(1.januar, 31.januar)
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
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, besvart = LocalDateTime.now().minusYears(1))
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

        val utbetaling1 = inspektør.utbetaling(0).inspektør
        val revurdering = inspektør.utbetaling(1).inspektør

        assertEquals(utbetaling1.korrelasjonsId, revurdering.korrelasjonsId)
        val oppdragInspektør = revurdering.arbeidsgiverOppdrag.inspektør
        assertEquals(Endringskode.ENDR, oppdragInspektør.endringskode)
        assertEquals(1, oppdragInspektør.antallLinjer())
        revurdering.arbeidsgiverOppdrag.single().inspektør.also { linje ->
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(17.januar, linje.datoStatusFom)
            assertEquals("OPPH", linje.statuskode)
        }

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertVarsel(RV_IT_3, AktivitetsloggFilter.person())
    }

    @Test
    fun `warning dersom det er utbetalt en periode i Infotrygd etter perioden som revurderes nå`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, besvart = LocalDateTime.now().minusYears(1))
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
    fun `Om en periode havner i RevurderingFeilet så skal alle sammenhengende perioder havne i RevurderingFeilet`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje((20.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(3.vedtaksperiode, foreldrepenger = 17.januar til 31.januar)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `revurderer siste utbetalte periode`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 18.januar)), førsteFraværsdag = 2.januar)
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
        assertEquals(Utbetalingstatus.AVVENTER_ARBEIDSGIVERKVITTERING, inspektør.utbetalingtilstand(1))
        assertEquals(inspektør.utbetaling(0).inspektør.arbeidsgiverOppdrag.fagsystemId(), inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.fagsystemId())
        inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.also { oppdrag ->
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
        håndterInntektsmelding(listOf(Periode(2.januar, 18.januar)), førsteFraværsdag = 2.januar)
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
        assertEquals(Utbetalingstatus.AVVENTER_ARBEIDSGIVERKVITTERING, inspektør.utbetalingtilstand(2))
        assertEquals(inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.fagsystemId(), inspektør.utbetaling(2).inspektør.arbeidsgiverOppdrag.fagsystemId())
        inspektør.utbetaling(2).inspektør.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(1, oppdrag.size)
            assertEquals(18.januar, oppdrag[0].fom)
            assertEquals(26.januar, oppdrag[0].tom)
            assertTrue(oppdrag[0].erForskjell())
            assertEquals(100, oppdrag[0].grad)
        }
    }

    @Test
    fun `oppdager nye utbetalte dager fra infotrygd i revurderingen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, besvart = 31.januar.atStartOfDay())
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

        assertIngenVarsler(1.vedtaksperiode.filter())
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )
    }

    @Test
    fun `Kun periode berørt av endringene skal ha hendelseIden - forlengelse får hendelseId`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        val hendelseId = UUID.randomUUID()
        håndterOverstyrTidslinje(
            meldingsreferanseId = hendelseId,
            overstyringsdager = (30.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) }
        )
        assertEquals(1.januar til 31.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(1.februar til 28.februar, inspektør.periode(2.vedtaksperiode))
        assertHarHendelseIder(1.vedtaksperiode, hendelseId)
        assertHarIkkeHendelseIder(2.vedtaksperiode, hendelseId)
    }

    @Test
    fun `Kun periode berørt av endringene skal ha hendelseIden`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(2.februar, 28.februar)
        val hendelseId = UUID.randomUUID()
        håndterOverstyrTidslinje(
            meldingsreferanseId = hendelseId,
            overstyringsdager = (30.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) }
        )
        assertEquals(1.januar til 31.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(2.februar til 28.februar, inspektør.periode(2.vedtaksperiode))
        assertHarHendelseIder(1.vedtaksperiode, hendelseId)
        assertHarIkkeHendelseIder(2.vedtaksperiode, hendelseId)
    }

    @Test
    fun `revurder første dag i periode på en sykedag som forlenger tidligere arbeidsgiverperiode med nytt skjæringstidspunkt`() {
        nyttVedtak(1.januar, 20.januar)
        nyttVedtak(25.januar, 25.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(25.januar, Dagtype.Sykedag, 80)))
        håndterYtelser(2.vedtaksperiode)

        assertTilstander(
            2.vedtaksperiode,
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
            AVVENTER_GJENNOMFØRT_REVURDERING,
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
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, besvart = LocalDate.EPOCH.atStartOfDay())
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
        assertEquals(expected, person.personLogg.antallEtterspurteBehov(vedtaksperiodeIdInnhenter, Aktivitet.Behov.Behovtype.Dødsinfo))
    }


}
