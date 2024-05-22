package no.nav.helse.spleis.e2e.flere_arbeidsgivere

import java.time.LocalDate
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.finnSkjæringstidspunkt
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class FlereArbeidsgivereForlengelserTest : AbstractEndToEndTest() {

    @Test
    fun `Tillater forlengelse av flere arbeidsgivere`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1,
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a2,
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        //Forlengelsen starter her
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive), orgnummer = a2)
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(
                forlengelseperiode.start,
                forlengelseperiode.endInclusive,
                100.prosent
            ), orgnummer = a1)
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(
                forlengelseperiode.start,
                forlengelseperiode.endInclusive,
                100.prosent
            ), orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_GODKJENNING, orgnummer = a1)

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.TIL_UTBETALING, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_SIMULERING, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_GODKJENNING, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.TIL_UTBETALING, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Ghost forlenger annen arbeidsgiver - skal gå fint`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(30000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 30000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 30000.månedlig.repeat(3))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a1, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a2, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.februar til 16.februar),
            førsteFraværsdag = 1.februar,
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(30000.månedlig, null, emptyList()),
            orgnummer = a2,
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET, orgnummer = a2)
        Assertions.assertEquals(1.januar, inspektør(a1).skjæringstidspunkt(1.vedtaksperiode))
        Assertions.assertEquals(1.januar, inspektør(a2).skjæringstidspunkt(1.vedtaksperiode))
        Assertions.assertEquals(
            inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode),
            inspektør(a2).vilkårsgrunnlag(1.vedtaksperiode)
        )
    }

    @Test
    fun `forlengelse av AvsluttetUtenUtbetaling for flere arbeidsgivere skal ikke gå til AvventerHistorikk uten IM for begge arbeidsgivere`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 12.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 12.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 12.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 12.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(13.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(13.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(13.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(13.januar, 31.januar, 100.prosent), orgnummer = a2)

        assertTilstand(2.vedtaksperiode, TilstandType.AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }
}