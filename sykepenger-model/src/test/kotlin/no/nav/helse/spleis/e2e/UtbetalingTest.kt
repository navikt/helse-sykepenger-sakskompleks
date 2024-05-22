package no.nav.helse.spleis.e2e

import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
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
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertEquals(ORGNUMMER, observatør.utbetaltEndretEventer.last().organisasjonsnummer)
    }

    @Test
    fun `grad rundes av`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
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
        håndterSøknad(Sykdom(23.januar, 31.januar, 100.prosent))

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
    fun `utbetaling_utbetalt tar med vedtaksperiode-ider for ett enkelt vedtak`() {
        nyttVedtak(1.januar til 31.januar, 100.prosent)

        val utbetalingEvent = observatør.utbetalingMedUtbetalingEventer.first()

        assertEquals(1, utbetalingEvent.vedtaksperiodeIder.size)
        assertEquals(1.vedtaksperiode.id(ORGNUMMER), utbetalingEvent.vedtaksperiodeIder.first())
    }

    @Test
    fun `utbetaling_utbetalt tar med vedtaksperiode-ider for flere vedtak`() {
        nyttVedtak(1.januar til 31.januar, 100.prosent)
        forlengVedtak(1.februar, 28.februar)

        val førsteEvent = observatør.utbetalingMedUtbetalingEventer.first()
        val andreEvent = observatør.utbetalingMedUtbetalingEventer.last()

        assertEquals(1, førsteEvent.vedtaksperiodeIder.size)
        assertEquals(1.vedtaksperiode.id(ORGNUMMER), førsteEvent.vedtaksperiodeIder.first())
        assertEquals(1, andreEvent.vedtaksperiodeIder.size)
        assertEquals(2.vedtaksperiode.id(ORGNUMMER), andreEvent.vedtaksperiodeIder.first())
    }
}
