package no.nav.helse.spleis.e2e.søknad

import java.time.YearMonth
import no.nav.helse.førsteArbeidsdag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.inspektør
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_REPLAY
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertTilstander
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FremtidigSøknadE2ETest : AbstractEndToEndTest() {
    private companion object {
        private val inneværendeMåned = YearMonth.now()
        private val nesteMåned = inneværendeMåned.plusMonths(1)
        private val fom = inneværendeMåned.atDay(14)
        private val tom = nesteMåned.atDay(14).førsteArbeidsdag()
        private val sisteArbeidsgiverdag = fom.plusDays(15)
    }

    @Test
    fun `kan sende inn søknad før periode er gått ut`() {
        håndterSykmelding(Sykmeldingsperiode(fom, tom))
        håndterSøknad(Sykdom(fom, tom, 100.prosent))
        håndterInntektsmelding(listOf(Periode(fom, sisteArbeidsgiverdag)), førsteFraværsdag = fom,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING_REPLAY,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        val arbeidsgiverOppdrag = inspektør.utbetalinger.first().inspektør.arbeidsgiverOppdrag
        val oppdragInspektør = arbeidsgiverOppdrag.inspektør
        assertEquals(sisteArbeidsgiverdag.plusDays(1), arbeidsgiverOppdrag.first().inspektør.fom)
        assertEquals(tom, arbeidsgiverOppdrag.last().inspektør.tom)
        assertEquals(tom, oppdragInspektør.periode?.endInclusive)
    }

}
