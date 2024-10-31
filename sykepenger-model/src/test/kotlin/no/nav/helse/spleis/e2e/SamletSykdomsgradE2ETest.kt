package no.nav.helse.spleis.e2e

import no.nav.helse.assertForventetFeil
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
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
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_4
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SamletSykdomsgradE2ETest: AbstractEndToEndTest() {

    @Test
    fun `hele perioden avvises`() {
        nyeVedtak(1.januar til 4.februar, a1, a2)
        nyPeriode(5.februar til 9.februar, a1, 10.prosent)
        nyPeriode(5.februar til 9.februar, a2, 10.prosent)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        inspektør(a1).utbetaling(1).also {
            assertEquals(listOf(5.februar, 6.februar, 7.februar, 8.februar, 9.februar), it.utbetalingstidslinje.inspektør.avvistedatoer)
        }
    }

    @Test
    fun `avviser dager under 20 prosent`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        håndterSøknad(Sykdom(1.januar, 20.januar, 19.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        assertEquals(Utbetalingstatus.GODKJENT_UTEN_UTBETALING, inspektør.utbetalingtilstand(0))
        val utbetalingstidslinje = inspektør.utbetalingUtbetalingstidslinje(0)
        assertTrue(utbetalingstidslinje[17.januar] is Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[18.januar] is Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[19.januar] is Utbetalingsdag.AvvistDag)
        assertEquals(3, utbetalingstidslinje.inspektør.avvistDagTeller)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING,
            AVSLUTTET
        )
    }

    @Test
    fun `avviser dager under 20 prosent på forlengelser`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        håndterSøknad(Sykdom(1.januar, 20.januar, 19.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar))
        håndterSøknad(21.januar til 31.januar)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        val utbetalingstidslinje = inspektør.utbetalingUtbetalingstidslinje(1)
        assertTrue(utbetalingstidslinje[17.januar] is Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[18.januar] is Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[19.januar] is Utbetalingsdag.AvvistDag)
        assertEquals(3, utbetalingstidslinje.inspektør.avvistDagTeller)
        assertEquals(Utbetalingstatus.OVERFØRT, inspektør.utbetalingtilstand(1))
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING
        )
    }

    @Test
    fun `opprinnelig søknad med 100 prosent arbeidshelse blir korrigert slik at sykdomsgraden blir 100 prosent `() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent, 100.prosent)) // 100 prosent arbeidshelse => 0 prosent syk
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSøknad(1.januar til 20.januar) // korrigert søknad med 0 prosent arbeidshelse => 100 prosent syk
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertForventetFeil(
            forklaring = "når vi mottar korrigert søknad ligger det igjen warnings fra før som ikke lengre gjelder",
            nå = { assertVarsel(RV_VV_4) },
            ønsket = { assertIngenVarsler() }
        )
    }

    @Test
    fun `ny periode med egen arbeidsgiverperiode skal ikke ha warning pga sykdomsgrad som gjelder forrige periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        håndterSøknad(Sykdom(1.januar, 20.januar, 19.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 20.mars))
        håndterSøknad(1.mars til 20.mars)
        håndterInntektsmelding(listOf(Periode(1.mars, 16.mars)), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        assertVarsel(RV_VV_4, 1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }
}
