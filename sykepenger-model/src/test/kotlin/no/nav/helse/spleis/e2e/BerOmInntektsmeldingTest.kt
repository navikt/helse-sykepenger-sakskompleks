package no.nav.helse.spleis.e2e

import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BerOmInntektsmeldingTest : AbstractEndToEndTest() {

    @Test
    fun `Ber ikke om inntektsmelding på korte perioder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.januar))
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))
        assertIngenFunksjonelleFeil()
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(0, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `Ber ikke om inntektsmelding på korte perioder med uferdige periode hos annen ag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 15.mars), orgnummer = a2)
        val søknadId = håndterSøknad(Sykdom(1.mars, 15.mars, 100.prosent), orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
        assertTrue(observatør.manglendeInntektsmeldingVedtaksperioder.none { event ->
            søknadId in event.søknadIder
        })
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AvventerInntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertIngenFunksjonelleFeil()
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
        )
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
        assertEquals(
            PersonObserver.ManglendeInntektsmeldingEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
                fom = 1.januar,
                tom = 31.januar,
                søknadIder = setOf(søknadId)
            ),
            observatør.manglendeInntektsmeldingVedtaksperioder.single()
        )
    }

    @Test
    fun `Ber ikke om inntektsmelding ved forlengelser`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId1 = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertIngenFunksjonelleFeil()
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
        assertEquals(
            PersonObserver.ManglendeInntektsmeldingEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
                fom = 1.januar,
                tom = 31.januar,
                søknadIder = setOf(søknadId1)
            ),
            observatør.manglendeInntektsmeldingVedtaksperioder.single()
        )
    }

    @Test
    fun `Sender ut event om at vi ikke trenger inntektsmelding når vi forlater AvventerInntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING
        )

        assertEquals(1, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `Send event om at vi ikke trenger inntektsmelding når vi forlater AvventerInntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)), 2.februar)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
        )

        assertEquals(1, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)
    }
}
