package no.nav.helse.spleis.e2e

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ManglerVilkårsgrunnlagE2ETest : AbstractEndToEndTest() {

    @Test
    fun `Inntektsmelding opplyser om endret arbeidsgiverperiode - AUU periode inneholder utbetalingsdag`() {
        nyPeriode(2.januar til 17.januar)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(2.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertNull(inspektør.vilkårsgrunnlag(1.vedtaksperiode))

        nyPeriode(22.januar til 31.januar)
        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), førsteFraværsdag = 22.januar)

        assertEquals(1.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertNull(inspektør.vilkårsgrunnlag(1.vedtaksperiode))

        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `Infotrygd utbetaler periode i forkant - Skjæringstidspunktet flytter seg`() {
        nyttVedtak(1.februar til 28.februar)
        assertEquals(1.februar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertNotNull(inspektør.vilkårsgrunnlag(1.vedtaksperiode))

        håndterUtbetalingshistorikkEtterInfotrygdendring(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar, 31.januar, 100.prosent, INNTEKT))
        nyPeriode(10.mars til 31.mars)
        håndterInntektsmelding(listOf(10.mars til 26.mars))
        håndterVilkårsgrunnlag(2.vedtaksperiode)

        assertEquals(1.februar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(setOf(1.februar, 10.mars), person.inspektør.vilkårsgrunnlagHistorikk.inspektør.aktiveSpleisSkjæringstidspunkt)

        håndterYtelser(2.vedtaksperiode)
        assertVarsel(Varselkode.RV_OS_2)
    }

    @Test
    fun `søknad omgjør paddet arbeidsdager til syk`() {
        håndterSøknad(Sykdom(1.januar, 3.januar, 100.prosent))
        håndterSøknad(Sykdom(31.januar, 5.februar, 100.prosent))
        // perioden 4. til 9.januar er paddet arbeidsdager; perioden 23.januar til 30.januar er "implisitte arbeidsdager" (ukjentdager på sykdomtsidslinjen)
        håndterInntektsmelding(listOf(1.januar til 3.januar, 10.januar til 22.januar), førsteFraværsdag = 31.januar)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        inspektør.sykdomstidslinje.inspektør.also { sykdomstidslinjeInspektør ->
            assertInstanceOf(Dag.Arbeidsdag::class.java, sykdomstidslinjeInspektør[4.januar])
            assertInstanceOf(Dag.Arbeidsdag::class.java, sykdomstidslinjeInspektør[9.januar])
            assertInstanceOf(Dag.UkjentDag::class.java, sykdomstidslinjeInspektør[23.januar])
            assertInstanceOf(Dag.UkjentDag::class.java, sykdomstidslinjeInspektør[30.januar])
        }

        håndterSøknad(Sykdom(10.januar, 26.januar, 100.prosent))

        inspektør.sykdomstidslinje.inspektør.also { sykdomstidslinjeInspektør ->
            assertInstanceOf(Dag.Arbeidsdag::class.java, sykdomstidslinjeInspektør[4.januar])
            assertInstanceOf(Dag.Arbeidsdag::class.java, sykdomstidslinjeInspektør[9.januar])
            assertInstanceOf(Dag.Sykedag::class.java, sykdomstidslinjeInspektør[23.januar])
            assertInstanceOf(Dag.Sykedag::class.java, sykdomstidslinjeInspektør[26.januar])
        }

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertEquals(31.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
    }

    @Test
    fun `Inntektsmelding sletter vilkårsgrunnlag og trekker tilbake penger`() {
        createOvergangFraInfotrygdPerson()
        assertEquals(1.januar til 31.januar, person.inspektør.utbetaltIInfotrygd.single())
        assertEquals(1.februar til 28.februar, inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.periode)

        val assertTilstandFørInnteksmeldingHensyntas: () -> Unit = {
            val førsteUtbetalingsdagIInfotrygd = 1.januar
            assertEquals(førsteUtbetalingsdagIInfotrygd, inspektør.skjæringstidspunkt(1.vedtaksperiode))
            assertNotNull(inspektør.vilkårsgrunnlag(1.vedtaksperiode))
            assertTrue(inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.infotrygd)
        }

        assertTilstandFørInnteksmeldingHensyntas()

        håndterSykmelding(Sykmeldingsperiode(5.mars, 31.mars))
        // Arbeidsgiver sender inntektsmelding for forlengelse i Mars _før_ vi møttar søknad.
        // Så lenge det ikke treffer noen vedtaksperiode i Spleis skjer det ingenting.
        // Personen vært frisk 1. & 2.Mars, så er nytt skjæringstidspunkt, men samme arbeidsgiverperiode
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(16.desember(2017) til 31.desember(2017)),
            førsteFraværsdag = 5.mars,
        )
        assertInfo("Inntektsmelding ikke håndtert")
        assertTilstandFørInnteksmeldingHensyntas()

        // Når søknaden kommer replayes Inntektsmelding og nå puttes plutselig info fra Inntektsmlding på
        // arbeidsgiver, også lengre tilbake i tid enn vedtaksperioden som blir truffet.
        håndterSøknad(Sykdom(5.mars, 31.mars, 100.prosent))
        assertTilstandFørInnteksmeldingHensyntas()
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_SIMULERING)
    }


    @Test
    fun `korrigert arbeidsgiverperiode under pågående revurdering`() {
        nyttVedtak(1.januar til 31.januar, 100.prosent)
        forlengVedtak(1.februar, 28.februar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 80.prosent))

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.februar til 16.februar), førsteFraværsdag = 1.februar)
        assertEquals(listOf(1.januar), person.skjæringstidspunkter())

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
    }

    @Test
    fun `korrigert arbeidsgiverperiode under pågående revurdering - korrigert søknad for februar`() {
        nyttVedtak(1.januar til 31.januar, 100.prosent)
        forlengVedtak(1.februar, 28.februar, 100.prosent)
        håndterSøknad(Sykdom(1.februar, 28.februar, 80.prosent))

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.februar til 16.februar), førsteFraværsdag = 1.februar)
        assertEquals(listOf(1.januar), person.skjæringstidspunkter())

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
    }


    private companion object {
        private fun assertIllegalStateException(melding: String, block: () -> Unit) {
            assertEquals(melding, assertThrows<IllegalStateException>(melding) { block() }.message)
        }
    }
}