package no.nav.helse.spleis.e2e

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.november
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class UtbetalingTest : AbstractEndToEndTest() {
    val ANNET_ORGNUMMER = "foo"

    @Test
    fun `Utbetaling endret får rett organisasjonsnummer ved overlappende sykemelding`() {
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ANNET_ORGNUMMER, 1.januar(2016), 31.januar(2016), 100.prosent, 1000.daglig),
            inntektshistorikk = listOf(
                Inntektsopplysning(ANNET_ORGNUMMER, 1.januar(2016), 1000.daglig, true)
            )
        )
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(2.februar til 28.februar)
        håndterSøknad(februar)

        assertEquals(ORGNUMMER, observatør.utbetaltEndretEventer.last().organisasjonsnummer)
    }

    @Test
    fun `grad rundes av`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 80.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertEquals(20, inspektør.utbetaling(0).inspektør.arbeidsgiverOppdrag[0].grad)
    }

    @Test
    fun `første periode er kun arbeidsgiverperiode og ferie`() {
        håndterSykmelding(Sykmeldingsperiode(4.januar, 22.januar))
        håndterSøknad(Sykdom(4.januar, 22.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(20.januar, 22.januar))
        håndterInntektsmelding(listOf(4.januar til 19.januar))
        håndterSykmelding(Sykmeldingsperiode(23.januar, 31.januar))
        håndterSøknad(23.januar til 31.januar)

        assertEquals(4.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(4.januar til 22.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(4.januar til 19.januar, inspektør.arbeidsgiverperiode(1.vedtaksperiode))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        assertEquals(4.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(23.januar til 31.januar, inspektør.periode(2.vedtaksperiode))
        assertEquals(4.januar til 19.januar, inspektør.arbeidsgiverperiode(2.vedtaksperiode))
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        assertEquals(1, inspektør.utbetalinger.size)
        assertEquals(0, inspektør.utbetalinger(1.vedtaksperiode).size)
        assertEquals(1, inspektør.utbetalinger(2.vedtaksperiode).size)
    }

    @Test
    fun `Utbetaling med stort gap kobles ikke sammen med forrige utbetaling -- når snutete egenmeldingsdager og denne utbetalingen ikke har penger`() {
        nyttVedtak(januar)

        håndterSøknad(
            Sykdom(1.desember, 31.desember, 10.prosent),
            egenmeldinger = listOf(13.november til 14.november)
        )
        håndterInntektsmelding(listOf(1.desember til 16.desember))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        // Arbeidsgiverperioden blir beregnet riktig
        assertEquals(1.januar til 16.januar, inspektør(ORGNUMMER).arbeidsgiverperiode(1.vedtaksperiode))
        assertEquals(1.desember til 16.desember, inspektør(ORGNUMMER).arbeidsgiverperiode(2.vedtaksperiode))

        assertEquals(2, inspektør(ORGNUMMER).utbetalinger.size)
        assertEquals(1.januar til 31.januar, inspektør(ORGNUMMER).utbetalinger.first().inspektør.periode)
        assertEquals(13.november til 31.desember, inspektør(ORGNUMMER).utbetalinger.last().inspektør.periode)
        assertNotEquals(inspektør(ORGNUMMER).utbetalinger[0].inspektør.korrelasjonsId, inspektør(ORGNUMMER).utbetalinger[1].inspektør.korrelasjonsId)
    }
}
