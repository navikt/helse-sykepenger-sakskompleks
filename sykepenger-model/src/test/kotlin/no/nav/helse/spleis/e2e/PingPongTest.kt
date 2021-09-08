package no.nav.helse.spleis.e2e

import no.nav.helse.Toggles
import no.nav.helse.hendelser.Arbeidsforhold
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class PingPongTest : AbstractEndToEndTest() {

    @Test
    fun `Forlengelser av infotrygd overgang har samme maksdato som forrige`() {
        val historikk1 = ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 20.november(2019),  29.mai(2020), 100.prosent, 1145.daglig)
        val inntekter = listOf(Inntektsopplysning(ORGNUMMER, 20.november(2019), 1000.daglig, true))
        håndterSykmelding(Sykmeldingsperiode(30.mai(2020), 19.juni(2020), 100.prosent))
        håndterSøknad(Sykdom(30.mai(2020), 19.juni(2020), 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode, historikk1, inntektshistorikk = inntekter)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2020), 9.juli(2020), 100.prosent))
        håndterSøknad(
            Sykdom(22.juni(2020), 9.juli(2020), 100.prosent)
        )
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterPåminnelse(2.vedtaksperiode, AVVENTER_GODKJENNING, LocalDateTime.now().minusDays(110))

        val historikk2 = ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 22.juni(2020),  17.august(2020), 100.prosent, 1145.daglig)
        val inntekter2 = listOf(
            Inntektsopplysning(ORGNUMMER, 20.november(2019), 1000.daglig, true),
            Inntektsopplysning(ORGNUMMER, 22.juni(2020), 1000.daglig, true)
        )
        håndterSykmelding(Sykmeldingsperiode(18.august(2020), 2.september(2020), 100.prosent))
        håndterSøknad(Sykdom(18.august(2020), 2.september(2020), 100.prosent))
        håndterUtbetalingshistorikk(3.vedtaksperiode, historikk1, historikk2, inntektshistorikk = inntekter2)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, true)
        håndterUtbetalt(3.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertEquals(30.oktober(2020), inspektør.sisteMaksdato(3.vedtaksperiode))
    }

    @Test
    fun `riktig skjæringstidspunkt ved spleis - infotrygd - spleis`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        val historie = ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.februar,  28.februar, 100.prosent, 1000.daglig)
        val inntekter = listOf(Inntektsopplysning(ORGNUMMER, 1.februar, INNTEKT, true))
        håndterUtbetalingshistorikk(2.vedtaksperiode, historie, inntektshistorikk = inntekter)
        håndterYtelser(2.vedtaksperiode)
        assertEquals(1.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
    }

    @Test
    fun `periode utebetales på nytt orgnummer i IT`() = Toggles.FlereArbeidsgivereUlikFom.disable {
        val a2 = "yep"
        nyttVedtak(1.januar, 31.januar, 25.prosent)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 25.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.mars, 31.mars, 25.prosent), orgnummer = a2)

        val historikk = ArbeidsgiverUtbetalingsperiode(a2, 1.februar, 28.februar, 25.prosent, INNTEKT)
        val inntekter = listOf(Inntektsopplysning(a2, 1.februar, 25000.månedlig, true))
        håndterUtbetalingshistorikk(1.vedtaksperiode(a2), historikk, inntektshistorikk = inntekter, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode(a2), orgnummer = a2)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode(a2),
            orgnummer = a2,
            arbeidsforhold = listOf(Arbeidsforhold(ORGNUMMER, LocalDate.EPOCH, 1.februar))
        )

        assertEquals(31, inspektør(ORGNUMMER).utbetalingstidslinjeBeregninger.last().utbetalingstidslinje().size)
        assertTrue(inspektør(a2).utbetalingstidslinjer(1.vedtaksperiode(a2)).none { it is Utbetalingstidslinje.Utbetalingsdag.AvvistDag })
    }
}
