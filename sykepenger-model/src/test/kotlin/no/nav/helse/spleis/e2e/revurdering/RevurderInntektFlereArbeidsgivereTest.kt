package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.TestPerson
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.dsl.a3
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RevurderInntektFlereArbeidsgivereTest : AbstractDslTest() {

    @Test
    fun `over 6G -- revurder inntekt ned på a1 når begge er i Avsluttet`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 32000.månedlig)
        nullstillTilstandsendringer()
        a1 { assertDag(17.januar, 1081.0.daglig, aktuellDagsinntekt = 32000.månedlig) }
        a2 { assertDag(17.januar, 1080.0.daglig, aktuellDagsinntekt = 32000.månedlig) }

        a1 {
            håndterOverstyrInntekt(1.januar, 31000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
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
            assertDag(17.januar, 1063.0.daglig, aktuellDagsinntekt = 31000.månedlig)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
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
            assertDag(17.januar, 1098.0.daglig, aktuellDagsinntekt = 32000.månedlig)
        }
    }

    @Test
    fun `over 6G -- revurder inntekt opp på a1 påvirker ikke utbetaling når refusjon er uendret`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 32000.månedlig)
        a1 { assertDag(17.januar, 1081.0.daglig, aktuellDagsinntekt = 32000.månedlig, personbeløp = INGEN) }
        a2 { assertDag(17.januar, 1080.0.daglig, aktuellDagsinntekt = 32000.månedlig, personbeløp = INGEN) }

        a1 {
            håndterOverstyrInntekt(1.januar, 33000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertDag(17.januar, 1081.0.daglig, aktuellDagsinntekt = 33000.månedlig, personbeløp = INGEN)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertDag(17.januar, 1080.0.daglig, aktuellDagsinntekt = 32000.månedlig, personbeløp = INGEN)
            assertIngenInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", AktivitetsloggFilter.person())
        }
    }

    @Test
    fun `over 6G -- revurder inntekt opp på a1 påvirker utbetaling når refusjon er endret`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 32000.månedlig)
        a1 { assertDag(17.januar, 1081.0.daglig, aktuellDagsinntekt = 32000.månedlig, personbeløp = INGEN) }
        a2 { assertDag(17.januar, 1080.0.daglig, aktuellDagsinntekt = 32000.månedlig, personbeløp = INGEN) }

        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 33000.månedlig)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
            håndterOverstyrInntekt(1.januar, 33000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertDag(17.januar, 1097.0.daglig, aktuellDagsinntekt = 33000.månedlig, personbeløp = INGEN)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertDag(17.januar, 1064.0.daglig, aktuellDagsinntekt = 32000.månedlig, personbeløp = INGEN)
            assertInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", AktivitetsloggFilter.person())
        }
    }

    @Test
    fun `Å flytte penger fra arbeidsgiveroppdrag til personoppdrag skal ikke logge at arbeidsgiveren har fått trukket penger`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 15000.månedlig)
        a2 {
            håndterInntektsmelding(
                listOf(1.januar til 16.januar),
                beregnetInntekt = 15000.månedlig,
                refusjon = Refusjon(7500.månedlig, opphørsdato = null)
            )
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertIngenInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", AktivitetsloggFilter.person())
        }
    }

    @Test
    fun `under 6G -- revurder inntekt opp på a1 gir brukerutbetaling når refusjon er uendret`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 15000.månedlig)
        (a1 og a2) { assertDag(17.januar, 692.0.daglig, aktuellDagsinntekt = 15000.månedlig, personbeløp = INGEN) }

        a1 {
            håndterOverstyrInntekt(1.januar, 16500.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertDag(17.januar, 692.0.daglig, aktuellDagsinntekt = 16500.månedlig, personbeløp = 70.daglig)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertDag(17.januar, 692.0.daglig, aktuellDagsinntekt = 15000.månedlig, personbeløp = INGEN)
            assertIngenInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", AktivitetsloggFilter.person())
        }
    }

    @Test
    fun `under 6G -- revurder inntekt opp på a1 gir økt arbeidsgiverutbetaling når refusjon er endret`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 15000.månedlig)
        (a1 og a2) { assertDag(17.januar, 692.0.daglig, aktuellDagsinntekt = 15000.månedlig, personbeløp = INGEN) }

        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 16500.månedlig)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
            håndterOverstyrInntekt(1.januar, 16500.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertDag(17.januar, 762.0.daglig, aktuellDagsinntekt = 16500.månedlig, personbeløp = INGEN)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertDag(17.januar, 692.0.daglig, aktuellDagsinntekt = 15000.månedlig, personbeløp = INGEN)
            assertIngenInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", AktivitetsloggFilter.person())
        }
    }

    @Test
    fun `3 arbeidsgivere -- justerer inntekten ned på a1`() {
        (a1 og a2 og a3).nyeVedtak(januar, inntekt = 32000.månedlig)
        nullstillTilstandsendringer()
        a1 { assertDag(17.januar, 721.0.daglig, aktuellDagsinntekt = 32000.månedlig) }
        (a2 og a3) { assertDag(17.januar, 720.0.daglig, aktuellDagsinntekt = 32000.månedlig) }

        a1 {
            håndterOverstyrInntekt(1.januar, 31000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
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
            assertDag(17.januar, 705.0.daglig, aktuellDagsinntekt = 31000.månedlig)
        }
        (a2 og a3) {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
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
            assertDag(17.januar, 728.0.daglig, aktuellDagsinntekt = 32000.månedlig)
        }
    }

    @Test
    fun `a1 er avsluttet og a2 er i AvventerGodkjenning -- revurderer a1`() {
        (a1 og a2) {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        (a1 og a2) { håndterInntektsmelding(listOf(1.januar til 16.januar)) }
        a1 {
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
        }
        nullstillTilstandsendringer()
        a1 {
            håndterOverstyrInntekt(1.januar, 32000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            val utbetalinger = inspektør.utbetalinger(1.vedtaksperiode)
            assertEquals(2, utbetalinger.size)
            val arbeidsgiverOppdrag = utbetalinger.last().inspektør.arbeidsgiverOppdrag
            assertEquals(1, arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.UEND, arbeidsgiverOppdrag[0].inspektør.endringskode)
            assertDag(17.januar, 1081.daglig, aktuellDagsinntekt = 32000.månedlig)
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVSLUTTET
            )
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertDag(17.januar, 1080.daglig, aktuellDagsinntekt = 31000.månedlig)
            assertTilstander(
                1.vedtaksperiode,
                AVVENTER_GODKJENNING,
                AVVENTER_BLOKKERENDE_PERIODE,
                AVVENTER_HISTORIKK,
                AVVENTER_SIMULERING,
                AVVENTER_GODKJENNING,
                TIL_UTBETALING,
                AVSLUTTET
            )
        }
    }

    @Test
    fun `a1 er avsluttet og a2 er til AvventerGodkjenningRevurdering -- revurderer a1`() {
        (a1 og a2).nyeVedtak(januar, inntekt = 32000.månedlig)
        nullstillTilstandsendringer()
        a1 {
            håndterOverstyrInntekt(1.januar, 31000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
        }
        nullstillTilstandsendringer()
        a1 {
            håndterOverstyrInntekt(1.januar, 31500.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
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
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstander(
                1.vedtaksperiode,
                AVVENTER_GODKJENNING_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                TIL_UTBETALING,
                AVSLUTTET
            )
        }
    }

    @Test
    fun `a1 er avsluttet og a2 er til godkjenning -- overstyrer a2`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        a2 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        a1 { håndterInntektsmelding(listOf(1.januar til 16.januar)) }
        a2 { håndterInntektsmelding(listOf(1.januar til 16.januar)) }
        a1 {
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterOverstyrInntekt(1.januar, 32000.månedlig)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }
    }

    @Test
    fun `revurder inntekt når a1 står i AvventerHistorikkRevurdering`() {
        (a1 og a2).nyeVedtak(januar)
        nullstillTilstandsendringer()
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 23000.månedlig)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
            håndterOverstyrInntekt(1.januar, 22000.månedlig)
            håndterOverstyrInntekt(1.januar, 23000.månedlig)
            håndterYtelser(1.vedtaksperiode)

            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING
            )
            assertDag(17.januar, 1062.daglig, aktuellDagsinntekt = 23000.månedlig)
        }
        a2 {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `revurder inntekt når a1 står i AvventerSimuleringRevurdering`() {
        (a1 og a2).nyeVedtak(januar)
        nullstillTilstandsendringer()
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 23000.månedlig)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
            håndterOverstyrInntekt(1.januar, 22000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterOverstyrInntekt(1.januar, 23000.månedlig)
            håndterYtelser(1.vedtaksperiode)

            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING
            )
            assertDag(17.januar, 1062.daglig, aktuellDagsinntekt = 23000.månedlig)
        }
        a2 {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `revurder inntekt når a1 står i AvventerGodkjenningRevurdering`() {
        (a1 og a2).nyeVedtak(januar)
        nullstillTilstandsendringer()
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 23000.månedlig)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
            håndterOverstyrInntekt(1.januar, 22000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterOverstyrInntekt(1.januar, 23000.månedlig)
            håndterYtelser(1.vedtaksperiode)

            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING
            )
            assertDag(17.januar, 1062.daglig, aktuellDagsinntekt = 23000.månedlig)
        }
        a2 {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `revurderer tidligere skjæringstidspunkt`() {
        (a1 og a2).nyeVedtak(januar)
        (a1 og a2).nyeVedtak(mars)
        nullstillTilstandsendringer()
        a1 {
            håndterOverstyrInntekt(1.januar, 19000.månedlig)
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertDag(17.januar, 877.daglig, aktuellDagsinntekt = 19000.månedlig)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertDag(17.januar, 923.daglig, aktuellDagsinntekt = 20000.månedlig)
        }
        (a1 og a2) {
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `kun den arbeidsgiveren som har fått overstyrt inntekt som faktisk lagrer inntekten`() {
        a2 {
            nyttVedtak(1.januar(2017) til 31.januar(2017), 100.prosent) // gammelt vedtak
        }
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                skatteinntekter = listOf(a1 to INNTEKT),
                arbeidsforhold = listOf(
                    Triple(a1, LocalDate.EPOCH, null),
                    Triple(a2, LocalDate.EPOCH, null)
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            håndterOverstyrInntekt(skjæringstidspunkt = 1.januar, inntekt = 25000.månedlig)
        }

        (inspiser(personInspektør).vilkårsgrunnlagHistorikk.grunnlagsdata(1.januar).inspektør).also { vilkårsgrunnlag ->
            val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør
            assertEquals(300000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
            assertEquals(300000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
            assertEquals(1, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
            sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                assertEquals(25000.månedlig, it.fastsattÅrsinntekt)
            }
        }
    }

    private fun TestPerson.TestArbeidsgiver.assertDag(dato: LocalDate, arbeidsgiverbeløp: Inntekt, personbeløp: Inntekt = INGEN, aktuellDagsinntekt: Inntekt = INGEN) {
        inspektør.sisteUtbetalingUtbetalingstidslinje()[dato].let {
            assertEquals(arbeidsgiverbeløp, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(personbeløp, it.økonomi.inspektør.personbeløp)
            assertEquals(aktuellDagsinntekt, it.økonomi.inspektør.aktuellDagsinntekt)
        }
    }
}
