package no.nav.helse.spleis.e2e.inntektsmelding

import java.time.LocalDate
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterAnnullerUtbetaling
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InntektsmeldingOgFerieE2ETest : AbstractEndToEndTest() {


    @Test
    fun ferieforlengelse() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 20.februar))
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent), Ferie(1.februar, 20.februar))

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `ferie med gap til forrige, men samme skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT, orgnummer = a1,)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                listOf(
                    grunnlag(a1, 1.januar, 31000.månedlig.repeat(3)),
                    grunnlag(a2, 1.januar, 31000.månedlig.repeat(3)),
                ), arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 20.februar), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(5.februar, 20.februar), orgnummer = a1)
        håndterSøknad(Sykdom(5.februar, 20.februar, 100.prosent), Ferie(5.februar, 20.februar), orgnummer = a1)

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2,)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        nullstillTilstandsendringer()
        håndterUtbetalt(orgnummer = a2)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `forkaster kort periode etter annullering`() {
        nyPeriode(1.januar til 5.januar, a1)
        nyPeriode(10.januar til 16.januar, a1)
        nyPeriode(17.januar til 20.januar, a1)
        nyttVedtak(21.januar til 31.januar, arbeidsgiverperiode = listOf(1.januar til 5.januar, 10.januar til 20.januar), orgnummer = a1, vedtaksperiodeIdInnhenter = 4.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(a1)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(4.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD, orgnummer = a1)
    }

    @Test
    fun `bare ferie (forlengelse) - etter tilbakevennende sykdom`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(5.februar, 23.februar))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 5.februar,)
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(5.februar, 23.februar, 100.prosent), Ferie(5.februar, 23.februar))
        håndterSøknad(Sykdom(24.februar, 28.februar, 100.prosent), Ferie(24.februar, 28.februar))

        assertEquals(1.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(1.vedtaksperiode))

        assertEquals(5.februar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(5.februar til 23.februar, inspektør.periode(2.vedtaksperiode))
        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(2.vedtaksperiode))
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)

        assertEquals(24.februar, inspektør.skjæringstidspunkt(3.vedtaksperiode))
        assertEquals(24.februar til 28.februar, inspektør.periode(3.vedtaksperiode))
        assertEquals(emptyList<Any>(), inspektør.arbeidsgiverperiode(3.vedtaksperiode))
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `bare ferie (sykdomsforlengelse) - etter tilbakevennende sykdom`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(5.februar, 23.februar))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 5.februar,)
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(5.februar, 23.februar, 100.prosent), Ferie(5.februar, 23.februar))
        håndterSøknad(Sykdom(24.februar, 28.februar, 100.prosent))

        assertEquals(1.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(1.vedtaksperiode))

        assertEquals(5.februar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(5.februar til 23.februar, inspektør.periode(2.vedtaksperiode))
        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(2.vedtaksperiode))
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)

        assertEquals(24.februar, inspektør.skjæringstidspunkt(3.vedtaksperiode))
        assertEquals(24.februar til 28.februar, inspektør.periode(3.vedtaksperiode))
        assertEquals(listOf(24.februar til 28.februar), inspektør.arbeidsgiverperiode(3.vedtaksperiode))
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }
    @Test
    fun `periode med ferie kant-i-kant med en periode med utbetalingsdag`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(5.februar, 23.februar))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 5.februar,)
        håndterSykmelding(Sykmeldingsperiode(24.februar, 12.mars))
        håndterSøknad(Sykdom(5.februar, 23.februar, 100.prosent), Ferie(5.februar, 23.februar))
        håndterSøknad(Sykdom(24.februar, 12.mars, 100.prosent))

        assertEquals(1.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(1.vedtaksperiode))

        assertEquals(5.februar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(5.februar til 23.februar, inspektør.periode(2.vedtaksperiode))
        assertEquals(listOf(1.januar til 16.januar), inspektør.arbeidsgiverperiode(2.vedtaksperiode))
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)

        assertEquals(24.februar, inspektør.skjæringstidspunkt(3.vedtaksperiode))
        assertEquals(24.februar til 12.mars, inspektør.periode(3.vedtaksperiode))
        assertEquals(listOf(24.februar til 11.mars), inspektør.arbeidsgiverperiode(3.vedtaksperiode))

        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
    }
}
