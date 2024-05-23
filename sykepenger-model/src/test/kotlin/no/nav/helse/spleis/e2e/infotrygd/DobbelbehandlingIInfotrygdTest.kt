package no.nav.helse.spleis.e2e.infotrygd

import java.time.LocalDate
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_3
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterPåminnelse
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DobbelbehandlingIInfotrygdTest : AbstractEndToEndTest() {

    @Test
    fun `avdekker overlapp dobbelbehandlinger i Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        val historie1 = arrayOf(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 3.januar, 26.januar, 100.prosent, 1000.daglig)
        )
        val inntektshistorikk1 = listOf(Inntektsopplysning(ORGNUMMER, 3.januar, INNTEKT, true))
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            *historie1,
            inntektshistorikk = inntektshistorikk1,
            besvart = LocalDate.EPOCH.atStartOfDay()
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertVarsel(RV_IT_3)
    }

    @Test
    fun `utbetaling i infotrygd etterpå`() {
        nyttVedtak(1.januar til 31.januar)
        forlengVedtak(1.februar til 28.februar)

        val historie1 = arrayOf(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 26.januar, 100.prosent, 1000.daglig)
        )
        val inntektshistorikk1 = listOf(Inntektsopplysning(ORGNUMMER, 3.januar, INNTEKT, true))
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            *historie1,
            inntektshistorikk = inntektshistorikk1,
            besvart = LocalDate.EPOCH.atStartOfDay()
        )

        håndterPåminnelse(1.vedtaksperiode, AVSLUTTET, skalReberegnes = true)
        håndterYtelser(1.vedtaksperiode)

        inspektør.utbetalinger(1.vedtaksperiode).last().inspektør.also { utbetalingInspektør ->
            assertEquals(Endringskode.UEND, utbetalingInspektør.arbeidsgiverOppdrag.inspektør.endringskode)
            assertEquals(0, utbetalingInspektør.personOppdrag.inspektør.antallLinjer())
        }
    }
}
