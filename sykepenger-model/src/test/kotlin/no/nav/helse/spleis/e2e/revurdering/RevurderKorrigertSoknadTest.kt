package no.nav.helse.spleis.e2e.revurdering

import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_10
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_13
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Endringskode.UEND
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevurderKorrigertSoknadTest : AbstractEndToEndTest() {

    @Test
    fun `Avsluttet periode får en korrigert søknad med perfekt overlapp - skal sette i gang en revurdering`() {
        nyttVedtak(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        håndterYtelser(1.vedtaksperiode)
        assertTrue(inspektør.sykdomstidslinje[17.januar] is Feriedag)
        assertTrue(inspektør.sykdomstidslinje[18.januar] is Feriedag)
        assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[17.januar] is Fridag)
        assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[18.januar] is Fridag)
    }

    @Test
    fun `Avsluttet periode får en overlappende søknad som strekker seg etter vedtaksperiode - skal ikke sette i gang revurdering`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar))
        håndterSøknad(Sykdom(15.januar, 15.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `Utbetaling i Infortrygd opphører tidligere utbetalinger innenfor samme arbeidsgiverperiode`() {
        nyttVedtak(1.januar til 28.januar)
        nyttVedtak(3.februar til 28.februar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        forlengVedtak(mars)
        val marsutbetaling = inspektør.utbetaling(2)
        håndterSykmelding(Sykmeldingsperiode(29.januar, 25.februar))
        håndterSøknad(Sykdom(29.januar, 25.februar, 100.prosent))

        assertFunksjonellFeil(RV_SØ_13)
        assertForkastetPeriodeTilstander(4.vedtaksperiode, START, TIL_INFOTRYGD)

        håndterUtbetalingshistorikkEtterInfotrygdendring(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 29.januar, 2.februar, 100.prosent, INNTEKT))
        forlengVedtak(april)
        assertEquals(4, inspektør.antallUtbetalinger)

        inspektør.sisteUtbetaling().also { utbetalinginspektør ->
            assertEquals(utbetalinginspektør.korrelasjonsId, marsutbetaling.korrelasjonsId)
            assertEquals(2, utbetalinginspektør.arbeidsgiverOppdrag.size)

            utbetalinginspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(UEND, linje.endringskode)
                assertEquals(17.januar til 26.januar, linje.periode)
                assertEquals(100, linje.grad)
                assertEquals(1431, linje.beløp)
            }
            utbetalinginspektør.arbeidsgiverOppdrag[1].inspektør.also { linje ->
                assertEquals(ENDR, linje.endringskode)
                assertEquals(3.februar til 30.april, linje.periode)
                assertEquals(100, linje.grad)
                assertEquals(1431, linje.beløp)
            }
        }
    }

    @Test
    fun `Avsluttet periode får en overlappende søknad som strekker seg før vedtaksperiode - skal ikke sette i gang revurdering`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(15.desember(2017), 15.januar))
        håndterSøknad(Sykdom(15.desember(2017), 15.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `Avsluttet periode får en overlappende søknad som starter etter og slutter før vedtaksperiode - skal sette i gang revurdering`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(17.januar, 25.januar))
        håndterSøknad(Sykdom(17.januar, 25.januar, 50.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertEquals(januar, inspektør.sykdomstidslinje.periode())
        håndterYtelser(1.vedtaksperiode)
        (1..16).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (17..25).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (26..31).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Korrigerende søknad med ferie`() {
        nyttVedtak(januar)
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        assertEquals(Sykedag::class, inspektør.sykdomstidslinje[17.januar]::class)
        assertEquals(Sykedag::class, inspektør.sykdomstidslinje[18.januar]::class)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(Feriedag::class, inspektør.sykdomstidslinje[17.januar]::class)
        assertEquals(Feriedag::class, inspektør.sykdomstidslinje[18.januar]::class)

        assertVarsel(RV_OS_2)

        val arbeidsgiverOppdrag = inspektør.sisteUtbetaling().arbeidsgiverOppdrag
        assertEquals(2, arbeidsgiverOppdrag.size)
        arbeidsgiverOppdrag[0].inspektør.let { utbetalingslinjeInspektør ->
            assertEquals(17.januar, utbetalingslinjeInspektør.fom)
            assertEquals(31.januar, utbetalingslinjeInspektør.tom)
            assertEquals(ENDR, utbetalingslinjeInspektør.endringskode)
            assertEquals(17.januar, utbetalingslinjeInspektør.datoStatusFom)
        }
        arbeidsgiverOppdrag[1].inspektør.let { utbetalingslinjeInspektør ->
            assertEquals(19.januar, utbetalingslinjeInspektør.fom)
            assertEquals(31.januar, utbetalingslinjeInspektør.tom)
            assertEquals(NY, utbetalingslinjeInspektør.endringskode)
            assertNull(utbetalingslinjeInspektør.datoStatusFom)
        }
    }

    @Test
    fun `Korrigerende søknad setter i gang en revurdering på siste skjæringstidspunkt`() {
        nyttVedtak(januar)
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), Ferie(15.mars, 16.mars))
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

        assertEquals(20, inspektør.sykdomstidslinje.subset(mars).inspektør.dagteller[Sykedag::class])
        assertEquals(2, inspektør.sykdomstidslinje.subset(mars).inspektør.dagteller[Feriedag::class])
        assertEquals(9, inspektør.sykdomstidslinje.subset(mars).inspektør.dagteller[SykHelgedag::class])
    }

    @Test
    fun `Avsluttet periode med en forlengelse får en korrigerende søknad - skal sette i gang revurdering`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertEquals(21, inspektør.sykdomstidslinje.subset(januar).inspektør.dagteller[Sykedag::class])
        assertEquals(2, inspektør.sykdomstidslinje.subset(januar).inspektør.dagteller[Feriedag::class])
        assertEquals(8, inspektør.sykdomstidslinje.subset(januar).inspektør.dagteller[SykHelgedag::class])
    }

    @Test
    fun `Avsluttet forlengelse får en overlappende søknad - skal sette i gang revurdering`() {
        nyttVedtak(januar, 50.prosent)
        forlengVedtak(februar, 50.prosent)
        håndterSykmelding(Sykmeldingsperiode(5.februar, 20.februar))
        håndterSøknad(Sykdom(5.februar, 20.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        håndterYtelser(2.vedtaksperiode)
        (1..4).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
        (5..20).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
        (21..28).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Overlappende søknad treffer førstegangsbehandling og forlengelse`() {
        nyttVedtak(januar, 50.prosent)
        forlengVedtak(februar, 50.prosent)
        håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar))
        håndterSøknad(Sykdom(15.januar, 15.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)
        assertFunksjonellFeil(RV_SØ_13, 2.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)

    }

    @Test
    fun `Avsluttet forlengelse får en overlappende søknad som slutter etter`() {
        nyttVedtak(januar, 50.prosent)
        forlengVedtak(februar, 50.prosent)

        håndterSykmelding(Sykmeldingsperiode(15.februar, 15.mars))
        håndterSøknad(Sykdom(15.februar, 15.mars, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_13, 2.vedtaksperiode.filter())

    }

    @Test
    fun `Avsluttet periode med forlengelse får en overlappende søknad som starter før`() {
        nyttVedtak(januar, 50.prosent)
        forlengVedtak(februar, 50.prosent)
        håndterSykmelding(Sykmeldingsperiode(15.desember(2017), 15.januar))
        håndterSøknad(Sykdom(15.desember(2017), 15.januar, 100.prosent))

        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertTilstand(3.vedtaksperiode, TIL_INFOTRYGD)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerGodkjenningRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstand(1.vedtaksperiode, AVSLUTTET)

        (17..21).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (27..31).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (22..26).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }
    @Test
    fun `Korrigerende søknad for periode i AvventerSimuleringRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstand(1.vedtaksperiode, AVSLUTTET)

        (17..21).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (27..31).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (22..26).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }
    @Test
    fun `Korrigerende søknad for periode i AvventerHistorikkRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstand(1.vedtaksperiode, AVSLUTTET)

        (17..21).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (27..31).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (22..26).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }
    @Test
    fun `Korrigerende søknad for periode i AvventerRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(januar, 100.prosent)
        nyttVedtak(15.februar til 15.mars, 100.prosent, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.januar, Dagtype.Feriedag)))

        assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)

        håndterSøknad(Sykdom(15.februar, 15.mars, 100.prosent, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        (15..28).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
        (1..15).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.mars].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Korrigerende søknad i AvventerVilkårsprøving - setter i gang en overstyring av behandlingen`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 16.februar))
        håndterSøknad(Sykdom(1.februar, 16.februar, 100.prosent))

        håndterInntektsmelding(listOf(31.januar til 15.februar),)

        assertTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)

        håndterSøknad(Sykdom(31.januar, 16.februar, 100.prosent))

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        (16..16).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Korrigerende søknad for førstegangsbehandling med forlengelse i avventerGodkjenning - setter i gang en revurdering`() {
        nyttVedtak(januar, 100.prosent)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(februar)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
    }


    @Test
    fun `Korrigert søknad med friskmelding for avsluttet periode`() {
        nyttVedtak(januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(20.januar, 31.januar))

        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)

        assertEquals(8, inspektør.sykdomstidslinje.subset(januar).inspektør.dagteller[Arbeidsdag::class])
        val utbetalingInspektør = inspektør.utbetaling(0).arbeidsgiverOppdrag.inspektør
        val utbetalingInspektørRevurdering = inspektør.utbetaling(1).arbeidsgiverOppdrag.inspektør
        assertEquals(utbetalingInspektør.fagsystemId(), utbetalingInspektørRevurdering.fagsystemId())
        assertEquals(1, utbetalingInspektørRevurdering.antallLinjer())
        assertEquals(17.januar til 19.januar, utbetalingInspektørRevurdering.fom(0) til utbetalingInspektørRevurdering.tom(0))
        assertEquals(ENDR, utbetalingInspektørRevurdering.endringskode(0))
    }

    @Test
    fun `Korrigert søknad med friskmelding for periode i AvventerGodkjenningRevurdering`() {
        nyttVedtak(januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 20.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        nullstillTilstandsendringer()

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(20.januar, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        assertEquals(8, inspektør.sykdomstidslinje.subset(januar).inspektør.dagteller[Arbeidsdag::class])

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `andre inntektskilder i avsluttet på førstegangsbehandling - skal gi error`() {
        nyttVedtak(januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 20.prosent), andreInntektskilder = true)

        assertForventetFeil(
            forklaring = "Produkteier ønsker warning, legal ønsker error. for øyeblikket warning",
            nå = {
                assertVarsel(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            },
            ønsket = {
                assertFunksjonellFeil(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
            }
        )
    }

    @Test
    fun `andre inntektskilder til godkjenning på førstegangsbehandling - skal gi error`() {
        tilGodkjenning(januar, ORGNUMMER)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 20.prosent), andreInntektskilder = true)
        assertForventetFeil(
            forklaring = "Produkteier ønsker warning, legal ønsker error. for øyeblikket warning",
            nå = {
                assertVarsel(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
            },
            ønsket = {
                assertFunksjonellFeil(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
            }
        )
    }

    private fun assertRevurderingUtenEndring(vedtakperiodeId: IdInnhenter, orgnummer: String = ORGNUMMER, block:() -> Unit) {
        val sykdomsHistorikkElementerFør = inspektør(orgnummer).sykdomshistorikk.elementer()
        val utbetalingerFør = inspektør(orgnummer).utbetalinger(vedtakperiodeId)
        block()
        val utbetalingerEtter = inspektør(orgnummer).utbetalinger(vedtakperiodeId)
        val sykdomsHistorikkElementerEtter = inspektør(orgnummer).sykdomshistorikk.elementer()
        assertEquals(1, utbetalingerEtter.size - utbetalingerFør.size) { "Forventet at det skal være opprettet en utbetaling" }
        assertEquals(UEND, utbetalingerEtter.last().inspektør.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(0, utbetalingerEtter.last().inspektør.personOppdrag.size)
        assertEquals(sykdomsHistorikkElementerFør, sykdomsHistorikkElementerEtter) { "Forventet at sykdomshistorikken skal være uendret" }
    }
}