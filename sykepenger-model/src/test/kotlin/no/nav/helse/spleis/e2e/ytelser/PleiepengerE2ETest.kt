package no.nav.helse.spleis.e2e.ytelser

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class PleiepengerE2ETest : AbstractEndToEndTest() {

    @Test
    fun `Periode for person der det ikke foreligger pleiepengerytelse blir behandlet og sendt til godkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020)))
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar(2020), 16.januar(2020))),)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, pleiepenger = emptyList())
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Periode som overlapper med pleiepengerytelse får varsel`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, pleiepenger = listOf(GradertPeriode(januar, 100)))
        assertVarsel(Varselkode.RV_AY_6)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING)
    }

    @Test
    fun `Periode som overlapper med pleiepengerytelse i starten av perioden får varsel`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, pleiepenger = listOf(GradertPeriode(1.desember(2017) til 1.januar, 100)))
        assertVarsel(Varselkode.RV_AY_6, 1.vedtaksperiode.filter())
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `Periode som overlapper med pleiepengerytelse i slutten av perioden får varsel`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, pleiepenger = listOf(GradertPeriode(31.januar til 14.februar, 100)))
        assertVarsel(Varselkode.RV_AY_6, 1.vedtaksperiode.filter())
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `Pleiepenger starter mindre enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, pleiepenger = listOf(GradertPeriode(3.februar til 20.februar, 100)))
        assertVarsel(Varselkode.RV_AY_6)
        assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
    }


    @Test
    fun `Pleiepenger starter mer enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, pleiepenger = listOf(GradertPeriode(3.januar til 20.januar, 100)))
        assertIngenFunksjonelleFeil()
    }
}
