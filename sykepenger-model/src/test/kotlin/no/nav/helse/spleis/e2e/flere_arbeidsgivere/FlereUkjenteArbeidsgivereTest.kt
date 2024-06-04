package no.nav.helse.spleis.e2e.flere_arbeidsgivere

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.OverstyringIgangsatt.VedtaksperiodeData
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class FlereUkjenteArbeidsgivereTest : AbstractEndToEndTest() {

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandlingen hos ag2`() {
        val inntektA1 = INNTEKT + 500.daglig
        val inntektA2 = INNTEKT

        nyeVedtak(1.januar, 31.januar, a1, a2, inntekt = INNTEKT)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        val im1 = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektA1, orgnummer = a1,)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val im2 = håndterInntektsmelding(listOf(1.mars til 16.mars), beregnetInntekt = inntektA2, orgnummer = a2,)
        nullstillTilstandsendringer()
        val søknad = UUID.randomUUID()
        håndterSøknad(Sykdom(1.mars, 20.mars, 100.prosent), id = søknad, orgnummer = a2)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(3.vedtaksperiode)?.inspektør ?: fail { "må ha vilkårsgrunnlag" }
        val inntektsopplysninger = vilkårsgrunnlag.sykepengegrunnlag.inspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver

        assertEquals(2, inntektsopplysninger.size)
        val a1Inspektør = inntektsopplysninger.getValue(a1).inspektør
        assertEquals(Inntektsmelding::class, a1Inspektør.inntektsopplysning::class)
        assertEquals(inntektA1, a1Inspektør.inntektsopplysning.inspektør.beløp)
        val a2Inspektør = inntektsopplysninger.getValue(a2).inspektør
        assertEquals(Inntektsmelding::class, a2Inspektør.inntektsopplysning::class)
        assertEquals(inntektA2, a2Inspektør.inntektsopplysning.inspektør.beløp)

        val overstyringerIgangsatt = observatør.overstyringIgangsatt
        assertEquals(5, overstyringerIgangsatt.size)

        overstyringerIgangsatt[0].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_ARBEIDSGIVERPERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 31.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a2, 1.vedtaksperiode.id(a2), 1.januar til 31.januar, 1.januar, "REVURDERING")
                ),
                meldingsreferanseId = im1
            ), event)
        }

        overstyringerIgangsatt[1].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 1.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a2, 1.vedtaksperiode.id(a2), 1.januar til 31.januar, 1.januar, "REVURDERING")
                ),
                meldingsreferanseId = im1
            ), event)
        }

        overstyringerIgangsatt[2].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "NY_PERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.mars til 20.mars,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                ),
                meldingsreferanseId = søknad
            ), event)
        }

        overstyringerIgangsatt[3].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "ARBEIDSGIVERPERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.mars til 20.mars,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                ),
                meldingsreferanseId = im2
            ), event)
        }

        overstyringerIgangsatt[4].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.mars.somPeriode(),
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                ),
                meldingsreferanseId = im2
            ), event)
        }

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `én arbeidsgiver blir to - førstegangsbehandlingen hos ag2 forkastes ikke`() {
        val inntektA1 = INNTEKT + 500.daglig

        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektA1, orgnummer = a1,)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()
        nyPeriode(1.mars til 20.mars, a2)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandling hos ag2 som først var ansett som ghost`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()

        // a2 sent til festen
        val id = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            orgnummer = a2,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "ja",
        )
        assertEquals(id, observatør.inntektsmeldingIkkeHåndtert.single())
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertVarsel(Varselkode.RV_IM_8, 1.vedtaksperiode.filter(a2))
        assertTilstander(
            1.vedtaksperiode,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING, orgnummer = a1
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandling hos ag2 som først var ansett som ghost - a1 revurderes`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        // a2 sent til festen
        val imId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            orgnummer = a2,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "ja",
        )
        assertEquals(imId, observatør.inntektsmeldingIkkeHåndtert.single())
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        assertEquals(listOf(
            Dokumentsporing.søknad(søknadId),
            Dokumentsporing.inntektsmeldingDager(imId),
            Dokumentsporing.inntektsmeldingInntekt(imId)
        ), inspektør(a2).hendelser(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        assertVarsel(Varselkode.RV_IM_8, 1.vedtaksperiode.filter(a2))
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
            orgnummer = a1
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a2)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandling hos ag2 som først var antatt å være frisk - men tidlig inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        val imId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            orgnummer = a2,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "ja",
        )
        assertEquals(imId, observatør.inntektsmeldingIkkeHåndtert.single())

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2) // a2 sent til festen, men med ting liggende i vilkårsgrunnlaget
        val sykepengegrunnlagInspektør = inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(INNTEKT, it.inntektsopplysning.fastsattÅrsinntekt())
            assertEquals(Refusjonsopplysning(imId, 1.januar, null, beløp = INNTEKT), it.refusjonsopplysninger.inspektør.refusjonsopplysninger.single())
        }

        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        assertEquals(
            listOf(
                Dokumentsporing.søknad(søknadId),
                Dokumentsporing.inntektsmeldingDager(imId),
                Dokumentsporing.inntektsmeldingInntekt(imId)
            ), inspektør(a2).hendelser(1.vedtaksperiode)
        )
        assertVarsel(Varselkode.RV_IM_8, 1.vedtaksperiode.filter(a2))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
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
            AVSLUTTET,
            orgnummer = a1
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a2)
    }
}