package no.nav.helse.spleis.e2e

import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SkjæringstidspunktE2ETest: AbstractEndToEndTest() {

    @Test
    fun `skjæringstidspunkt skal ikke hensynta sykedager i et senere sykefraværstilefelle`() {
        nyttVedtak(januar)
        håndterSøknad(februar)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterSøknad(mars)
        håndterSøknad(mai)

        håndterOverstyrTidslinje((1.februar til 31.mars).map { manuellForeldrepengedag(it) })
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
        assertEquals(1.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(1.januar, inspektør.skjæringstidspunkt(3.vedtaksperiode))
    }

    @Test
    fun `periode med bare ferie - tidligere sykdom`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar,  10.januar, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.januar, INNTEKT, true))
        )
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), Ferie(1.mars, 31.mars))
        assertEquals(1.mars, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `Finner skjæringstidspunkt for periode med arbeidsdager på slutten som overlapper med sykdom hos annen arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(24.februar, 24.mars), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(25.januar, 25.februar), orgnummer = a2)

        håndterSøknad(januar, orgnummer = a1)
        håndterSøknad(24.februar til 24.mars, orgnummer = a1)
        håndterSøknad(Sykdom(25.januar, 25.februar, 100.prosent), Arbeid(20.februar, 25.februar), orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 15000.månedlig, orgnummer = a1, vedtaksperiodeIdInnhenter = 1.vedtaksperiode)
        håndterInntektsmelding(listOf(25.januar til 10.februar), beregnetInntekt = 16000.månedlig, orgnummer = a2, vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        val inntekter = listOf(
            grunnlag(a1, 1.januar, 15000.månedlig.repeat(3)),
            grunnlag(a2, 1.januar, 16000.månedlig.repeat(3))
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertEquals(1.januar, inspektør(a1).skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(24.februar, inspektør(a1).skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(1.januar, inspektør(a2).skjæringstidspunkt(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)

        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a1)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(24.februar til 11.mars), beregnetInntekt = 17000.månedlig, orgnummer = a1, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterVilkårsgrunnlag(
            2.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 24.februar, 17000.månedlig.repeat(3)),
                    grunnlag(a2, 24.februar, 16000.månedlig.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET, orgnummer = a1)
    }

    @Test
    fun `Finner skjæringstidspunkt for periode med arbeidsdager på slutten som overlapper med sykdom hos annen arbeidsgiver - siste skjæringstidspunkt mangler inntekt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(23.februar, 24.mars), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(25.januar, 25.februar), orgnummer = a2)

        håndterSøknad(januar, orgnummer = a1)
        håndterSøknad(23.februar til 24.mars, orgnummer = a1)
        håndterSøknad(Sykdom(25.januar, 25.februar, 100.prosent), Arbeid(20.februar, 25.februar), orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 15000.månedlig, orgnummer = a1, vedtaksperiodeIdInnhenter = 1.vedtaksperiode)
        håndterInntektsmelding(listOf(25.januar til 10.februar), beregnetInntekt = 16000.månedlig, orgnummer = a2, vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        val inntekter = listOf(
            grunnlag(a1, 1.januar, 15000.månedlig.repeat(3)),
            grunnlag(a2, 1.januar, 16000.månedlig.repeat(3))
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()
        håndterUtbetalt(orgnummer = a1)

        assertEquals(1.januar, inspektør(a1).skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(23.februar, inspektør(a1).skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(1.januar, inspektør(a2).skjæringstidspunkt(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(23.februar til 10.mars), beregnetInntekt = 15000.månedlig, orgnummer = a1, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET, orgnummer = a2)
    }
}
