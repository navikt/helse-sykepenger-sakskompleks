package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.dsl.a3
import no.nav.helse.dsl.assertInntektsgrunnlag
import no.nav.helse.dsl.nyPeriode
import no.nav.helse.februar
import no.nav.helse.hendelser.OverstyrArbeidsforhold.ArbeidsforholdOverstyrt
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.UtbetalingInntektskilde.EN_ARBEIDSGIVER
import no.nav.helse.person.UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.inntekt.Arbeidsgiverinntekt
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.person
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class RevurderArbeidsforholdTest : AbstractDslTest() {

    @Test
    fun `revurder arbeidsforhold i Avsluttet`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            nullstillTilstandsendringer()
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, true, "test"))
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, 31000.månedlig)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(EN_ARBEIDSGIVER, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            }
        }
    }

    @Test
    fun `overstyrer forlengelse, førstegangsbehandling revurderes`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            // ny periode
            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
            håndterSøknad(februar)
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
            (inspektør.vilkårsgrunnlag(2.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)

                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            nullstillTilstandsendringer()
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, true, "test"))
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, 31000.månedlig)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                TIL_UTBETALING,
                AVSLUTTET
            )
            assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(EN_ARBEIDSGIVER, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            }
        }
    }

    @Test
    fun `deaktiverer arbeidsforhold frem & tilbake, førstegangsbehandling revurderes`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            // ny periode
            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
            håndterSøknad(februar)
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            nullstillTilstandsendringer()
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, true, "test"))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(EN_ARBEIDSGIVER, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            }
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, false, "test"))
            inspektør.utbetalinger(1.vedtaksperiode).also { utbetalinger ->
                assertEquals(2, utbetalinger.size)
                assertEquals(Utbetalingstatus.FORKASTET, utbetalinger.last().inspektør.tilstand)
            }
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVSLUTTET
            )
            assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
        }
    }

    @Test
    fun `revurderer arbeidsforhold i AvventerHistorikkRevurdering`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            nullstillTilstandsendringer()
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, true, "test"))
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(EN_ARBEIDSGIVER, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            }
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, false, "test"))
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVSLUTTET
            )
        }
    }

    @Test
    fun `revurderer arbeidsforhold i AvventerSimuleringRevurdering`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            nullstillTilstandsendringer()
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, true, "test"))
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(EN_ARBEIDSGIVER, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            }
            håndterYtelser(1.vedtaksperiode)
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, false, "test"))
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
                assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            }
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVSLUTTET
            )
        }
    }

    @Test
    fun `deaktiverer en auu`() {
        a1 {
            nyPeriode(januar)
        }
        a2 {
            nyPeriode(1.januar til 16.januar)
        }
        a1 {
            håndterArbeidsgiveropplysninger(listOf(1.januar til 16.januar), vedtaksperiodeId = 1.vedtaksperiode)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, true, "arbeidsforholdet er inaktivt"))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            assertEquals(100, inspektør.utbetalinger(1.vedtaksperiode).last().utbetalingstidslinje[17.januar].økonomi.inspektør.totalGrad)
            assertVarsler(listOf(Varselkode.RV_VV_2), 1.vedtaksperiode.filter())
        }
    }

    @Test
    fun `flere arbeidsgivere med sykdom`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        a2 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        a1 { håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT) }
        a2 { håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT) }

        a1 {
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2, a3)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        inspiser(personInspektør).vilkårsgrunnlagHistorikk.grunnlagsdata(1.januar).inspektør.also { vilkårsgrunnlag ->
            val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

            assertEquals(1116000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
            assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
            assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
            a1 { assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode)) }
            a2 { assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode)) }
            assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT)
            assertInntektsgrunnlag(vilkårsgrunnlag, a3, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
        }
        nullstillTilstandsendringer()
        håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a3, true, "test"))

        a1 {
            håndterSkjønnsmessigFastsettelse(
                1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, 31000.månedlig),
                OverstyrtArbeidsgiveropplysning(a2, 31000.månedlig)
            )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a1 {
            assertTilstander(
                1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                TIL_UTBETALING,
                AVSLUTTET
            )
        }
        a2 {
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
        inspiser(personInspektør).vilkårsgrunnlagHistorikk.grunnlagsdata(1.januar).inspektør.also { vilkårsgrunnlag ->
            val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

            assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
            assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
            assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
            a1 { assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode)) }
            a2 { assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode)) }
            assertInntektsgrunnlag(vilkårsgrunnlag, a1, INNTEKT)
            assertInntektsgrunnlag(vilkårsgrunnlag, a2, INNTEKT)
        }
    }

    @Test
    fun `over 6G -- deaktiverer og aktiverer arbeidsforhold medfører tilbakekreving`() {
        val inntekt = 33000.månedlig
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntekt)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertInntektsgrunnlag(1.januar, a1, inntekt)
            assertInntektsgrunnlag(1.januar, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertPeriode(17.januar til 31.januar, 1114.daglig)
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, deaktivert = true, "deaktiverer a2"))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, inntekt)))
            assertInntektsgrunnlag(1.januar, a1, inntekt)

            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 1523.daglig)
            håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a2, deaktivert = false, "aktiverer a2 igjen"))
            assertInntektsgrunnlag(1.januar, a1, inntekt)
            assertInntektsgrunnlag(1.januar, a2, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 1114.daglig)
            assertInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", person())
        }
    }

    @Test
    fun `over 6G -- deaktiverer og aktiverer arbeidsforhold medfører tilbakekreving flere arbeidsgivere`() {
        val inntekt = 23000.månedlig
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        a2 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
        }
        a1 { håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntekt) }
        a2 { håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntekt) }

        a1 {
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2, a3)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            assertInntektsgrunnlag(1.januar, a1, inntekt)
            assertInntektsgrunnlag(1.januar, a2, inntekt)
            assertInntektsgrunnlag(1.januar, a3, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
        }

        håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a3, true, "deaktiverer a3"))

        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterSkjønnsmessigFastsettelse(
                1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, inntekt),
                OverstyrtArbeidsgiveropplysning(a2, inntekt)
            )
            )
            assertInntektsgrunnlag(1.januar, a1, inntekt)
            assertInntektsgrunnlag(1.januar, a2, inntekt)
        }

        (a1 og a2) {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        }
        håndterOverstyrArbeidsforhold(1.januar, ArbeidsforholdOverstyrt(a3, false, "aktiverer a3 igjen"))

        (a1 og a2) {
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertInntektsgrunnlag(1.januar, a1, inntekt)
            assertInntektsgrunnlag(1.januar, a2, inntekt)
            assertInntektsgrunnlag(1.januar, a3, INNTEKT, forventetkilde = Arbeidsgiverinntekt.Kilde.AOrdningen)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertInfo("En endring hos en arbeidsgiver har medført at det trekkes tilbake penger hos andre arbeidsgivere", person())
        }
    }

    private fun TestPerson.TestArbeidsgiver.assertDag(dato: LocalDate, arbeidsgiverbeløp: Inntekt, personbeløp: Inntekt) {
        inspektør(orgnummer).sisteUtbetalingUtbetalingstidslinje()[dato].let {
            if (it is Utbetalingsdag.NavHelgDag) return
            assertEquals(arbeidsgiverbeløp, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(personbeløp, it.økonomi.inspektør.personbeløp)
        }
    }

    private fun TestPerson.TestArbeidsgiver.assertPeriode(
        periode: Periode,
        arbeidsgiverbeløp: Inntekt,
        personbeløp: Inntekt = Inntekt.INGEN
    ) =
        periode.forEach { assertDag(it, arbeidsgiverbeløp, personbeløp) }
}
