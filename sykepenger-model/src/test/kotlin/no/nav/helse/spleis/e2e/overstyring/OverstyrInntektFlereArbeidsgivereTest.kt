package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_2
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertInntektForDato
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.håndterArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlagFlereArbeidsgivere
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class OverstyrInntektFlereArbeidsgivereTest : AbstractEndToEndTest() {

    @Test
    fun `overstyr inntekt med flere AG -- happy case`() {
        tilGodkjenning(januar, a1, a2)
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        assertInntektForDato(INNTEKT, 1.januar, inspektør = inspektør(a1))
        assertInntektForDato(INNTEKT, 1.januar, inspektør = inspektør(a2))

        (inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
            val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

            assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
            assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
            assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
            assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
            assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
            sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                assertEquals(INNTEKT, it.fastsattÅrsinntekt)
            }
            sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                assertEquals(INNTEKT, it.fastsattÅrsinntekt)
            }
        }

        håndterOverstyrInntekt(19000.månedlig, a1, 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        (inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
            val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

            assertEquals(600000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
            assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
            assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
            assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
            assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
            sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                assertEquals(19000.månedlig, it.fastsattÅrsinntekt)
            }
            sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                assertEquals(INNTEKT, it.fastsattÅrsinntekt)
            }

        }
    }

    @Test
    fun `overstyr inntekt med flere AG -- kan overstyre perioden i AvventerBlokkerende`() {
        tilGodkjenning(januar, a1, a2)
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        håndterOverstyrInntekt(19000.månedlig, a2, 1.januar)
        assertIngenFunksjonelleFeil()
        assertInntektForDato(INNTEKT, 1.januar, inspektør = inspektør(a1))
        assertInntektForDato(19000.månedlig, 1.januar, inspektør = inspektør(a2))
        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

        assertEquals(600000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(INNTEKT, it.fastsattÅrsinntekt)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(19000.månedlig, it.fastsattÅrsinntekt)
        }
    }

    @Test
    fun `skal ikke kunne overstyre en arbeidsgiver hvis en annen er utbetalt`() {
        tilGodkjenning(januar, a1, a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterOverstyrInntekt(19000.månedlig, a2, 1.januar)
        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertInntektForDato(19000.månedlig, 1.januar, inspektør = inspektør(a2))
        assertInntektForDato(INNTEKT, 1.januar, inspektør = inspektør(a1))
        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

        assertEquals(600000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(INNTEKT, it.fastsattÅrsinntekt)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(19000.månedlig, it.fastsattÅrsinntekt)
        }
    }

    @Test
    fun `flere arbeidsgivere med ghost - overstyrer inntekt til arbeidsgiver med sykdom -- happy case`() {
        tilOverstyring()
        håndterOverstyrInntekt(29000.månedlig, a1, 1.januar)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

        assertEquals(720000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(29000.månedlig, it.fastsattÅrsinntekt)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(INNTEKT, it.fastsattÅrsinntekt)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertTilstander(
            1.vedtaksperiode,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            orgnummer = a1
        )
    }

    @Test
    fun `overstyrer inntekt til under krav til minste inntekt`() {
        tilGodkjenning(januar, a1, a2, beregnetInntekt = 1959.månedlig)
        håndterOverstyrInntekt(1500.månedlig, a1, 1.januar)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(RV_SV_1, 1.vedtaksperiode.filter(a1))
        assertIngenFunksjonelleFeil()
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `overstyring av inntekt kan føre til brukerutbetaling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterArbeidsgiveropplysninger(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT / 4,
            orgnummer = a1,
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode
        )
        håndterArbeidsgiveropplysninger(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT / 4,
            orgnummer = a2,
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode
        )

        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterOverstyrInntekt(8000.månedlig, skjæringstidspunkt = 1.januar, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        inspektør(a1).utbetaling(1).also { utbetaling ->
            assertEquals(1, utbetaling.arbeidsgiverOppdrag.size)
            utbetaling.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(358, linje.beløp)
                assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            }
            assertEquals(1, utbetaling.personOppdrag.size)
            utbetaling.personOppdrag[0].inspektør.also { linje ->
                assertEquals(11, linje.beløp)
                assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            }
        }
        inspektør(a2).utbetaling(1).also { utbetaling ->
            assertEquals(1, utbetaling.arbeidsgiverOppdrag.size)
            utbetaling.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(358, linje.beløp)
                assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            }
            assertTrue(utbetaling.personOppdrag.isEmpty())
        }
    }

    private fun tilOverstyring(fom: LocalDate = 1.januar, tom: LocalDate = 31.januar) {
        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(fom, tom, 100.prosent), orgnummer = a1)
        håndterArbeidsgiveropplysninger(
            listOf(fom til fom.plusDays(15)),
            orgnummer = a1,
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode
        )
        håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2, orgnummer = a1)
        assertVarsel(RV_VV_2, 1.vedtaksperiode.filter(orgnummer = a1))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
    }
}
