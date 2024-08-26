package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.Toggle
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Permisjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.september
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjent
import no.nav.helse.spleis.e2e.tilSimulering
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.FORKASTET
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KorrigertSøknadTest : AbstractEndToEndTest() {

    @Test
    fun `korrigerer med arbeid gjenopptatt etter utbetalt`() {
        nyttVedtak(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(1.januar, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertEquals("AAAAARR AAAAARR AAAAARR AAAAARR AAA", inspektør.sykdomstidslinje.toShortString())
        assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
    }

    @Test
    fun `korrigert søknad i til utbetaling - utbetaler først, så revurdere`() {
        tilGodkjent(3.januar til 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(3.januar, 26.januar, 80.prosent))
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
    }

    @Test
    fun `korrigert søknad i avventer simulering - forkaster utbetalingen`() {
        tilSimulering(3.januar til 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(3.januar, 26.januar, 80.prosent))
        assertEquals(FORKASTET, inspektør.utbetalinger(1.vedtaksperiode).single().inspektør.tilstand)
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Arbeidsdag i søknad nr 2 kaster ikke ut perioden`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `Overlappende søknad som er lengre tilbake støttes ikke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar))
        håndterSøknad(Sykdom(3.januar, 31.januar, 100.prosent))
        håndterSøknad(januar)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
    }
    @Test
    fun `Overlappende søknad som er lengre frem støttes ikke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar))
        håndterSøknad(Sykdom(3.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 1.februar, 100.prosent))
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `Korrigerer fridager til sykedag`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Permisjon(30.januar, 30.januar), Ferie(31.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)
        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[30.januar] is Sykedag)
            assertTrue(it[31.januar] is Sykedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `Korrigerer sykedag til feriedag`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Permisjon(30.januar, 30.januar), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)
        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[30.januar] is Permisjonsdag)
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `Korrigerer grad`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertEquals(50, inspektør.sykdomstidslinje.inspektør.grader[17.januar])
        assertEquals(50, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.grad(17.januar))
    }

    @Test
    fun `Korrigerer feriedag til sykedag i forlengelse`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        val søknadId = håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.mars)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[28.februar] is Sykedag)
        }
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `korrigerende søknad som lager nytt skjæringstidspunkt på tidligere forlengelse`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(februar)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(mars)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(20.februar, 28.februar))

        assertIngenFunksjonelleFeil(2.vedtaksperiode.filter())

        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)
        assertTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `nytt skjæringstidspunkt på forlengelse etter friskmelding på førstegangsbehandling`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(februar)

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(25.januar, 31.januar))

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_INNTEKTSMELDING)

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_UFERDIG_GAP`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        val søknadId = håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar), korrigerer = søknadId, opprinneligSendt = 1.mars)
        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[28.februar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        val søknadId = håndterSøknad(februar)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar), korrigerer = søknadId, opprinneligSendt = 1.mars)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[28.februar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVENTER_TIDLIGERE_ELLER_OVERLAPPENDE`() {
        håndterSykmelding(Sykmeldingsperiode(1.desember(2017), 11.desember(2017)))
        håndterSøknad(Sykdom(1.desember(2017), 11.desember(2017), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar,)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)


        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
        assertIngenVarsler(3.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_FERDIG_GAP`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `avslutter periode med friskmelding -- avventer vilkårsprøving`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(17.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also { inspektør ->
            (17.januar til 31.januar).forEach {
                assertTrue(inspektør[it] is Dag.Arbeidsdag || inspektør[it] is Dag.FriskHelgedag)
            }
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `avslutter periode med 100 % ferie og ingen utbetaling -- avventer vilkårsprøving`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also { inspektør ->
            (17.januar til 31.januar).forEach {
                assertTrue(inspektør[it] is Feriedag)
            }
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_VILKÅRSPRØVING`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_HISTORIKK`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Går tilbake til AVVENTER_HISTORIKK når søknaden kommer inn i AVVENTER_SIMULERING`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Sykedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Går tilbake til AVVENTER_HISTORIKK når søknaden kommer inn i AVVENTER_GODKJENNING`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Sykedag)
        }
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
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Ikke foreld dager ved sen korrigerende søknad om original søknad var innenfor avskjæringsdag`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 31.januar)
        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), sendtTilNAVEllerArbeidsgiver = 30.september, korrigerer = søknadId, opprinneligSendt = 31.januar)
        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
    }

    @Test
    fun `Foreld dager ved sen søknad, selv om vi mottar korrigerende søknad`() {
        håndterSykmelding(januar)
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        assertEquals("KKKKKHH KKKKKHH KKKKKHH KKKKKHH KKK", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), sendtTilNAVEllerArbeidsgiver = 2.mai, korrigerer = søknadId, opprinneligSendt = 1.mai)
        assertEquals("KKKKKHH KKKKKHH KKKKKHH KKKKKHH KKF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
    }

    @Test
    fun `Korrigerende søknad appender egenmeldingsperioder til vedtaksperioden`() {
        håndterSøknad(Sykdom(3.januar, 31.januar, 100.prosent), egenmeldinger = listOf(1.januar til 2.januar))
        assertEquals(listOf(1.januar til 2.januar), inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.egenmeldingsperioder)

        håndterSøknad(Sykdom(7.januar, 31.januar, 100.prosent), egenmeldinger = listOf(5.januar til 6.januar))
        assertEquals(listOf(1.januar til 2.januar, 5.januar til 6.januar), inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.egenmeldingsperioder)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), egenmeldinger = listOf(2.januar til  5.januar))
        assertEquals(listOf(1.januar til 6.januar), inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.egenmeldingsperioder)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), egenmeldinger = listOf(1.januar til  6.januar))
        assertEquals(listOf(1.januar til 6.januar), inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.egenmeldingsperioder)
    }

    @Test
    fun `Korrigerende søknad som fører til revurdering appender egenmeldingsperioder til vedtaksperioden`() {
        nyttVedtak(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), egenmeldinger = listOf(1.januar til 5.januar, 8.januar til 10.januar))
        assertEquals(listOf(1.januar til 10.januar), inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.egenmeldingsperioder)
    }
}
