package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.februar
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
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
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonellFeil
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertUtbetalingsbeløp
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.spleis.e2e.tilSimulering
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OutOfOrderSøknadTest : AbstractEndToEndTest() {

    @Test
    fun `ny tidligere periode - senere i avventer simulering - forkaster utbetalingen`() {
        tilSimulering(3.mars, 26.mars, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(5.januar, 19.januar, 80.prosent))
        assertEquals(Utbetalingstatus.FORKASTET, inspektør.utbetalinger(1.vedtaksperiode).single().inspektør.tilstand)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `out-of-order perioder endrer skjæringstidspunkter`() {
        tilGodkjenning(3.januar, 31.januar, ORGNUMMER)
        nullstillTilstandsendringer()
        nyPeriode(1.januar til 2.januar, ORGNUMMER)
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntekt = 20000.månedlig)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        assertIngenFunksjonellFeil(Varselkode.RV_SV_3)
    }

    @Test
    fun `Revurderer vegg-i-vegg-AUU når det ikke foreligger tidligere utbetaling`() {
        nyPeriode(1.mars til 16.mars)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertIngenVarsel(Varselkode.RV_OO_2, 1.vedtaksperiode.filter())
        assertVarsel(Varselkode.RV_OO_1, 2.vedtaksperiode.filter())

        håndterInntektsmelding(listOf(1.februar til 16.februar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertUtbetalingsbeløp(
            1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1431,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 17.februar til 16.mars
        )
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `out-of-order med senere periode i Avventer inntektsmelding eller historikk`() {
        nyttVedtak(1.januar til 31.januar, 100.prosent)
        nyPeriode(1.mars til 20.mars, ORGNUMMER)
        nullstillTilstandsendringer()
        forlengVedtak(1.februar, 28.februar)

        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)
    }
}
