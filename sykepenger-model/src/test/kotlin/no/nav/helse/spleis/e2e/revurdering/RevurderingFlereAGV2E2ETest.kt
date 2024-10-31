package no.nav.helse.spleis.e2e.revurdering

import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype.Feriedag
import no.nav.helse.hendelser.Dagtype.Sykedag
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_5
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_4
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.forlengelseTilGodkjenning
import no.nav.helse.spleis.e2e.førstegangTilGodkjenning
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class RevurderingFlereAGV2E2ETest: AbstractEndToEndTest() {

    @Test
    fun `revurdere første periode - flere ag - ag 1`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a1, a2)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `revurdere andre periode - flere ag - ag 1`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a1, a2)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Feriedag)), orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `revurdere tredje periode - flere ag - ag1`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a1, a2)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.mars, Feriedag)), orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `revurdere første periode - flere ag - ag 2`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a1, a2)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.januar, Feriedag)), orgnummer = a2)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `revurdere andre periode - flere ag - ag 2`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a1, a2)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Feriedag)), orgnummer = a2)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `revurdere tredje periode - flere ag - ag2`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a1, a2)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.mars, Feriedag)), orgnummer = a2)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `starte revurdering av ag1 igjen etter at ag2 har startet revurdering`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Sykedag, 100)), a1)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `revurdering påvirkes ikke av gjenoppta behandling ved avsluttet uten utbetaling`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()
        håndterSykmelding(Sykmeldingsperiode(1.juni, 10.juni), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.juni, 10.juni, 100.prosent), orgnummer = a2)

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
    }

    @Test
    fun `revurdering av ag 2 mens ag 1 er til utbetaling`() {
        nyeVedtak(januar, a1, a2)
        forlengelseTilGodkjenning(februar, a1, a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a2)
        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        nullstillTilstandsendringer()
        håndterUtbetalt()

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        }

        nullstillTilstandsendringer()

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        }
    }

    @Test
    fun `revurdering av tidligere frittstående periode hos ag3 mens overlappende hos ag1 og ag2 utbetales`() {
        nyttVedtak(januar, orgnummer = a3)

        nyeVedtak(mai, a1, a2)
        forlengelseTilGodkjenning(juni, a1, a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a3)
        håndterUtbetalt(orgnummer = a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
            assertNotNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
        }
        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        inspektør(a3) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        håndterSimulering(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(orgnummer = a3)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }
        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        inspektør(a3) {
            assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        }
    }

    @Test
    fun `revurdering av ag 1 kicker i gang revurdering av ag 2 - holder igjen senere perioder hos ag1`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)

        nyttVedtak(april, orgnummer = a1, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt for ag 1 mens senere periode for ag 1 er til utbetaling`() {
        /* Rekkefølge ting burde skje i:
        * 1. a1 v2 utbetales, a1 v1 avventer revurdering, a2 v1 avventer andre arbeidsgivere
        * 2. a1 v2 utbetalt, a1 v1 revurderes, a2 v1 avventer andre arbeidsgivere
        * 3. a1 v1 revurdert, a1 v2 revurderes, a2 v1 avventer andre arbeidsgivere
        * */
        nyttVedtak(januar, orgnummer = a1)
        førstegangTilGodkjenning(mars, a1 to 2.vedtaksperiode, a2 to null )
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        håndterUtbetalt(orgnummer = a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
            assertNotNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
    }

    @Test
    fun `tre ag der a1 og a3 har to førstegangsbehandlinger - første førstegang på a1 blir revurdert mens andre førstegang på a1 er til utbetaling`() {
        nyeVedtak(januar, a1, a3)
        førstegangTilGodkjenning(mars, a1 to 2.vedtaksperiode, a2 to null, a3 to 2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        inspektør(a3) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        håndterUtbetalt(orgnummer = a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
            assertNotNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }

        inspektør(a3) {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
    }

    @Test
    fun `revurdering av ag 2 mens ag 1 revurderes og er til utbetaling`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a2)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        nullstillTilstandsendringer()
        håndterUtbetalt(orgnummer = a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)

        inspektør(a1) {
            assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        inspektør(a2) {
            assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `validerer ytelser på alle arbeidsgivere ved revurdering av flere arbeidgivere`() {
        nyeVedtak(januar, a1, a2)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)

        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(20.januar til 31.januar, 100)), orgnummer = a1)
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter(a1))
        assertIngenVarsel(RV_AY_5, 1.vedtaksperiode.filter(a2))
    }

    @Test
    fun `validerer ytelser for periode som strekker seg fra skjæringstidsunkt til siste tom`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)
        forlengVedtak(mars, a2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), a1)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(20.mars til 31.mars, 100)), orgnummer = a1)
        assertIngenVarsel(RV_AY_5)
    }

    @Test
    fun `forkaster gamle utbetalinger for flere AG når der skjer endringer siden forrige`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, a1, a2)

        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(17.januar, Feriedag),
            ManuellOverskrivingDag(18.januar, Feriedag)
        ), a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(18.januar, Sykedag, 100)
        ), a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)

        inspektør(a1).utbetalinger(1.vedtaksperiode).also { utbetalinger ->
            assertEquals(3, utbetalinger.size)
        }
        inspektør(a1).utbetalinger(2.vedtaksperiode).also { utbetalinger ->
            assertEquals(1, utbetalinger.size)
        }
        inspektør(a2).utbetalinger(1.vedtaksperiode).also { utbetalinger ->
            assertEquals(3, utbetalinger.size)
            assertEquals(100, utbetalinger.last().inspektør.utbetalingstidslinje[18.januar].økonomi.inspektør.totalGrad)
        }
        inspektør(a2).utbetalinger(2.vedtaksperiode).also { utbetalinger ->
            assertEquals(1, utbetalinger.size)
        }
    }

    @Test
    fun `Varsel på perioder hos begge AG dersom grad er under 20 prosent`() {
        nyeVedtak(januar, a1, a2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Sykedag, 19)), orgnummer = a1)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Sykedag, 19)), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertEquals(19, inspektør(a1).sykdomstidslinje.inspektør.grader[17.januar])
        assertEquals(19, inspektør(a2).sykdomstidslinje.inspektør.grader[17.januar])
        assertVarsel(RV_VV_4, 1.vedtaksperiode.filter(a1))
        assertIngenVarsel(RV_VV_4, 1.vedtaksperiode.filter(a2))
    }

    @Test
    fun `revurdere på en eldre arbeidsgiver - infotrygd har utbetalt `() {
        nyeVedtak(januar, a1)
        nyPeriode(1.februar til 18.februar, a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2,)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)), orgnummer = a1)

        nullstillTilstandsendringer()
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(a2, 17.januar, 18.februar, 100.prosent, INNTEKT)
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        val utbetaling1 = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(1)

        assertEquals(utbetaling1.korrelasjonsId, revurdering.korrelasjonsId)
        val oppdragInspektør = revurdering.arbeidsgiverOppdrag.inspektør
        assertEquals(Endringskode.ENDR, oppdragInspektør.endringskode)
        assertEquals(2, oppdragInspektør.antallLinjer())
        revurdering.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(Endringskode.ENDR, linje.endringskode)
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(17.januar, linje.datoStatusFom)
            assertEquals("OPPH", linje.statuskode)
        }
        revurdering.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(Endringskode.NY, linje.endringskode)
            assertEquals(18.januar til 31.januar, linje.fom til linje.tom)
            assertNull(linje.datoStatusFom)
            assertNull(linje.statuskode)
        }

        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, orgnummer = a1)
    }
}