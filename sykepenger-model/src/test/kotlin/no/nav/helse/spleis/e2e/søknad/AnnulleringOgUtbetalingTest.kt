package no.nav.helse.spleis.e2e.søknad

import java.util.UUID
import no.nav.helse.april
import no.nav.helse.august
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Institusjonsopphold.Institusjonsoppholdsperiode
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.lørdag
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.ANNULLERT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.FORKASTET
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.GODKJENT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.GODKJENT_UTEN_UTBETALING
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.IKKE_UTBETALT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.NY
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.OVERFØRT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.UTBETALT
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnnulleringOgUtbetalingTest : AbstractDslTest() {

    @Test
    fun `annullere revurdering til godkjenning etter annen revurdering`() = a1 {
        nyttVedtak(1.januar til 31.januar)
        nyttVedtak(1.mars til 31.mars)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(2.vedtaksperiode)

        assertEquals(4, inspektør.utbetalinger.size)
        val marsutbetalingen = inspektør.utbetaling(1).inspektør
        håndterAnnullering(marsutbetalingen.utbetalingId)

        assertEquals(5, inspektør.utbetalinger.size)
        val annulleringen = inspektør.utbetaling(4).inspektør
        assertTrue(annulleringen.erAnnullering)
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
    }

    @Test
    fun `tidligere periode med ferie får samme arbeidsgiverperiode som nyere periode`() = a1 {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(1.februar, 28.februar))

        nyttVedtak(1.mars til 31.mars)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertVarsel(Varselkode.RV_UT_21, 2.vedtaksperiode.filter())

        assertEquals(5, inspektør.utbetalinger.size)
        val januarutbetaling = inspektør.utbetaling(0).inspektør
        val marsutbetaling = inspektør.utbetaling(1).inspektør
        val annulleringAvJanuar = inspektør.utbetaling(2).inspektør
        val februarutbetaling = inspektør.utbetaling(3).inspektør
        val revurderingAvMars = inspektør.utbetaling(4).inspektør

        assertEquals(13, observatør.utbetaltEndretEventer.size)
        assertUtbetalingtilstander(januarutbetaling.utbetalingId, NY, IKKE_UTBETALT, GODKJENT_UTEN_UTBETALING)
        assertUtbetalingtilstander(marsutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(annulleringAvJanuar.utbetalingId, NY, IKKE_UTBETALT, ANNULLERT)
        assertUtbetalingtilstander(februarutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(revurderingAvMars.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)

        assertNotEquals(januarutbetaling.korrelasjonsId, marsutbetaling.korrelasjonsId)
        assertEquals(januarutbetaling.korrelasjonsId, annulleringAvJanuar.korrelasjonsId)
        assertEquals(marsutbetaling.korrelasjonsId, februarutbetaling.korrelasjonsId)
        assertEquals(revurderingAvMars.korrelasjonsId, februarutbetaling.korrelasjonsId)

        assertEquals(0, annulleringAvJanuar.arbeidsgiverOppdrag.size)
        assertEquals(0, annulleringAvJanuar.personOppdrag.size)

        assertEquals(2, februarutbetaling.arbeidsgiverOppdrag.size)
        assertEquals(0, februarutbetaling.personOppdrag.size)
        assertEquals(Endringskode.ENDR, februarutbetaling.arbeidsgiverOppdrag.inspektør.endringskode)
        februarutbetaling.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(1.februar, linje.fom)
            assertEquals(28.februar, linje.tom)
            assertEquals(Endringskode.NY, linje.endringskode)
        }
        februarutbetaling.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(17.mars, linje.fom)
            assertEquals(30.mars, linje.tom)
            assertEquals(Endringskode.NY, linje.endringskode)
        }

        assertEquals(1, revurderingAvMars.arbeidsgiverOppdrag.size)
        assertEquals(0, revurderingAvMars.personOppdrag.size)
        assertEquals(Endringskode.ENDR, revurderingAvMars.arbeidsgiverOppdrag.inspektør.endringskode)
        revurderingAvMars.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(1.februar, linje.fom)
            assertEquals(30.mars, linje.tom)
            assertEquals(Endringskode.NY, linje.endringskode)
        }

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)
    }
    @Test
    fun `Forkaster feilaktig avsluttet periode når to utbetalinger blir til én`() = a1 {
        nyttVedtak(1.januar til 31.januar)
        nyttVedtak(1.mars til 31.mars)

        nullstillTilstandsendringer()
        nyPeriode(5.februar til 15.februar, a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 5.februar)

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode, institusjonsoppholdsperioder = listOf(Institusjonsoppholdsperiode(5.februar, 15.februar)))


        inspektør.utbetaling(0).inspektør.let {
            assertEquals(1.januar til 31.januar, it.periode)
            assertEquals(UTBETALT, it.tilstand)
        }
        inspektør.utbetaling(1).inspektør.let {
            assertEquals(1.mars til 31.mars, it.periode)
            assertEquals(UTBETALT, it.tilstand)
        }
        inspektør.utbetaling(2).inspektør.let {
            assertEquals(1.mars til 31.mars, it.periode)
            assertEquals(ANNULLERT, it.tilstand)
        }

        inspektør.utbetaling(3).inspektør.let {
            assertEquals(1.januar til 31.januar, it.periode)
            assertEquals(GODKJENT_UTEN_UTBETALING, it.tilstand)
        }

        inspektør.utbetaling(4).inspektør.let {
            assertEquals(1.januar til 15.februar, it.periode)
            assertEquals(FORKASTET, it.tilstand)
        }

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `reberegne og forkaste utbetaling som inneholder annulleringer`() = a1 {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(1.februar, 28.februar))

        nyttVedtak(1.mars til 31.mars)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingshistorikkEtterInfotrygdendring(listOf(ArbeidsgiverUtbetalingsperiode("orgnr", 1.mai(2017), 5.mai(2017), 100.prosent, 1000.daglig)))
        håndterYtelser(2.vedtaksperiode)

        assertEquals(6, inspektør.utbetalinger.size)
        val januarutbetaling = inspektør.utbetaling(0).inspektør
        val marsutbetaling = inspektør.utbetaling(1).inspektør
        val annulleringAvJanuarForkastet = inspektør.utbetaling(2).inspektør
        val februarutbetalingForkastet = inspektør.utbetaling(3).inspektør

        val annulleringAvJanuarReberegnet = inspektør.utbetaling(4).inspektør
        val februarutbetalingReberegnet = inspektør.utbetaling(5).inspektør

        assertEquals(GODKJENT_UTEN_UTBETALING, januarutbetaling.tilstand)
        assertEquals(UTBETALT, marsutbetaling.tilstand)

        assertEquals(FORKASTET, annulleringAvJanuarForkastet.tilstand)
        assertEquals(FORKASTET, februarutbetalingForkastet.tilstand)

        assertEquals(IKKE_UTBETALT, annulleringAvJanuarReberegnet.tilstand)
        assertEquals(IKKE_UTBETALT, februarutbetalingReberegnet.tilstand)
    }

    @Test
    fun `korrigerer periode med bare ferie`() = a1 {
        nyttVedtak(3.januar til 26.januar)
        nyttVedtak(3.mars til 26.mars)
        nullstillTilstandsendringer()

        håndterSøknad(Sykdom(3.mars, 26.mars, 100.prosent), Ferie(3.mars, 26.mars))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(4, inspektør.utbetalinger.size)
        val januarutbetaling = inspektør.utbetaling(0).inspektør
        val marsutbetaling = inspektør.utbetaling(1).inspektør
        val annulleringAvMars = inspektør.utbetaling(2).inspektør
        val revurderingAvMars = inspektør.utbetaling(3).inspektør

        assertEquals(12, observatør.utbetaltEndretEventer.size)
        assertUtbetalingtilstander(januarutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(marsutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(annulleringAvMars.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, ANNULLERT)
        assertUtbetalingtilstander(revurderingAvMars.utbetalingId, NY, IKKE_UTBETALT, GODKJENT, GODKJENT_UTEN_UTBETALING)

        assertEquals(marsutbetaling.korrelasjonsId, annulleringAvMars.korrelasjonsId)
        assertEquals(januarutbetaling.korrelasjonsId, revurderingAvMars.korrelasjonsId)

        assertEquals(ANNULLERT, annulleringAvMars.tilstand)
        assertEquals(GODKJENT_UTEN_UTBETALING, revurderingAvMars.tilstand)

        assertEquals(1, annulleringAvMars.arbeidsgiverOppdrag.size)
        assertEquals(0, annulleringAvMars.personOppdrag.size)
        annulleringAvMars.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(19.mars, linje.fom)
            assertEquals(26.mars, linje.tom)
            assertEquals(19.mars, linje.datoStatusFom)
            assertEquals("OPPH", linje.statuskode)
        }

        assertEquals(Endringskode.UEND, revurderingAvMars.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(1, revurderingAvMars.arbeidsgiverOppdrag.size)
        assertEquals(0, revurderingAvMars.personOppdrag.size)
    }

    @Test
    fun `to uavhengige arbeidsgiverperioder blir til en som følge av overstyring`() = a1 {
        nyttVedtak(3.januar til 26.januar)
        håndterSøknad(Sykdom(27.januar, 28.februar, 100.prosent), Arbeid(13.februar, 28.februar))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        nyttVedtak(3.mars til 31.mars)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(
            (13.februar til 28.februar).map { ManuellOverskrivingDag(it, Dagtype.Sykedag, 100) }
        )
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(6, inspektør.utbetalinger.size)
        val januarutbetaling = inspektør.utbetaling(0).inspektør
        val februarutbetaling = inspektør.utbetaling(1).inspektør
        val marsutbetaling = inspektør.utbetaling(2).inspektør
        val annulleringAvMars = inspektør.utbetaling(3).inspektør
        val revurderingAvFebruar = inspektør.utbetaling(4).inspektør
        val revurderingAvMars = inspektør.utbetaling(5).inspektør

        assertEquals(19, observatør.utbetaltEndretEventer.size)
        assertUtbetalingtilstander(januarutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(februarutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(marsutbetaling.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(annulleringAvMars.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, ANNULLERT)
        assertUtbetalingtilstander(revurderingAvFebruar.utbetalingId, NY, IKKE_UTBETALT, GODKJENT, OVERFØRT, UTBETALT)
        assertUtbetalingtilstander(revurderingAvMars.utbetalingId, NY, IKKE_UTBETALT, OVERFØRT, UTBETALT)

        assertEquals(januarutbetaling.korrelasjonsId, februarutbetaling.korrelasjonsId)
        assertNotEquals(februarutbetaling.korrelasjonsId, marsutbetaling.korrelasjonsId)
        assertEquals(marsutbetaling.korrelasjonsId, annulleringAvMars.korrelasjonsId)
        assertEquals(januarutbetaling.korrelasjonsId, revurderingAvFebruar.korrelasjonsId)
        assertEquals(januarutbetaling.korrelasjonsId, revurderingAvMars.korrelasjonsId)

        assertEquals(1, annulleringAvMars.arbeidsgiverOppdrag.size)
        assertEquals(0, annulleringAvMars.personOppdrag.size)
        assertEquals(Endringskode.ENDR, annulleringAvMars.arbeidsgiverOppdrag.inspektør.endringskode)
        annulleringAvMars.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(19.mars, linje.fom)
            assertEquals(30.mars, linje.tom)
            assertEquals(19.mars, linje.datoStatusFom)
            assertEquals("OPPH", linje.statuskode)
        }

        assertEquals(1, revurderingAvFebruar.arbeidsgiverOppdrag.size)
        assertEquals(0, revurderingAvFebruar.personOppdrag.size)
        assertEquals(Endringskode.ENDR, revurderingAvFebruar.arbeidsgiverOppdrag.inspektør.endringskode)
        revurderingAvFebruar.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(19.januar, linje.fom)
            assertEquals(28.februar, linje.tom)
            assertEquals(Endringskode.ENDR, linje.endringskode)
        }

        assertEquals(2, revurderingAvMars.arbeidsgiverOppdrag.size)
        assertEquals(0, revurderingAvMars.personOppdrag.size)
        assertEquals(Endringskode.ENDR, revurderingAvMars.arbeidsgiverOppdrag.inspektør.endringskode)
        revurderingAvMars.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(19.januar, linje.fom)
            assertEquals(28.februar, linje.tom)
            assertEquals(Endringskode.UEND, linje.endringskode)
        }
        revurderingAvMars.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(3.mars, linje.fom)
            assertEquals(30.mars, linje.tom)
            assertEquals(Endringskode.NY, linje.endringskode)
        }
    }

    @Test
    fun `arbeidsgiverperiode slås sammen pga avviklet ferie`() {
        a1 {
            nyttVedtak(1.juni til 30.juni)
            nyttVedtak(1.august til 31.august)
            nullstillTilstandsendringer()
            håndterOverstyrTidslinje(1.juli.til(30.juli).map {
                ManuellOverskrivingDag(it, Dagtype.Feriedag)
            }.plusElement(ManuellOverskrivingDag(31.juli, Dagtype.Arbeidsdag)))
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
            håndterUtbetalt()

            assertEquals(1.juni til 30.juni, inspektør.periode(1.vedtaksperiode))
            assertEquals(1.juli til 31.august, inspektør.periode(2.vedtaksperiode))
            assertEquals("SHH SSSSSHH SSSSSHH SSSSSHH SSSSSHF FFFFFFF FFFFFFF FFFFFFF FFFFFFF FASSSHH SSSSSHH SSSSSHH SSSSSHH SSSSS", inspektør.sykdomstidslinje.toShortString())

            val juniutbetaling = inspektør.utbetaling(0).inspektør
            val augustutbetaling = inspektør.utbetaling(1).inspektør
            val annulleringaugust = inspektør.utbetaling(2).inspektør
            val revurderingaugust = inspektør.utbetaling(3).inspektør

            assertNotEquals(juniutbetaling.korrelasjonsId, augustutbetaling.korrelasjonsId)
            assertEquals(annulleringaugust.korrelasjonsId, augustutbetaling.korrelasjonsId)
            assertEquals(juniutbetaling.korrelasjonsId, revurderingaugust.korrelasjonsId)

            assertEquals(2, revurderingaugust.arbeidsgiverOppdrag.size)
            assertEquals(0, revurderingaugust.personOppdrag.size)
            assertEquals(Endringskode.ENDR, revurderingaugust.arbeidsgiverOppdrag.inspektør.endringskode)
            revurderingaugust.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(17.juni, linje.fom)
                assertEquals(30.juni, linje.tom)
                assertEquals(Endringskode.ENDR, linje.endringskode)
            }
            revurderingaugust.arbeidsgiverOppdrag[1].inspektør.also { linje ->
                assertEquals(1.august, linje.fom)
                assertEquals(31.august, linje.tom)
                assertEquals(Endringskode.NY, linje.endringskode)
            }

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `arbeidsgiverperiode slås sammen pga avviklet ferie - med endring uten utbetalingsendring i forrige periode`() {
        a1 {
            nyttVedtak(1.juni til lørdag.den(30.juni))
            nyttVedtak(1.august til 31.august)
            nullstillTilstandsendringer()
            håndterOverstyrTidslinje(lørdag.den(30.juni).til(30.juli).map {
                ManuellOverskrivingDag(it, Dagtype.Feriedag)
            }.plusElement(ManuellOverskrivingDag(31.juli, Dagtype.Arbeidsdag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()

            assertEquals(1.juni til 30.juni, inspektør.periode(1.vedtaksperiode))
            assertEquals(1.juli til 31.august, inspektør.periode(2.vedtaksperiode))
            assertEquals("SHH SSSSSHH SSSSSHH SSSSSHH SSSSSFF FFFFFFF FFFFFFF FFFFFFF FFFFFFF FASSSHH SSSSSHH SSSSSHH SSSSSHH SSSSS", inspektør.sykdomstidslinje.toShortString())

            val juniutbetaling = inspektør.utbetaling(0).inspektør
            val augustutbetaling = inspektør.utbetaling(1).inspektør
            val annulleringaugust = inspektør.utbetaling(2).inspektør
            val revurderingjuni = inspektør.utbetaling(3).inspektør
            val revurderingaugust = inspektør.utbetaling(4).inspektør

            assertNotEquals(juniutbetaling.korrelasjonsId, augustutbetaling.korrelasjonsId)
            assertEquals(annulleringaugust.korrelasjonsId, augustutbetaling.korrelasjonsId)
            assertEquals(juniutbetaling.korrelasjonsId, revurderingjuni.korrelasjonsId)
            assertEquals(juniutbetaling.korrelasjonsId, revurderingaugust.korrelasjonsId)

            assertEquals(1, annulleringaugust.arbeidsgiverOppdrag.size)
            assertEquals(0, annulleringaugust.personOppdrag.size)
            assertEquals(Endringskode.ENDR, annulleringaugust.arbeidsgiverOppdrag.inspektør.endringskode)
            annulleringaugust.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(17.august, linje.fom)
                assertEquals(31.august, linje.tom)
                assertEquals(Endringskode.ENDR, linje.endringskode)
                assertEquals("OPPH", linje.statuskode)
                assertEquals(17.august, linje.datoStatusFom)
            }

            assertEquals(1, revurderingjuni.arbeidsgiverOppdrag.size)
            assertEquals(0, revurderingjuni.personOppdrag.size)
            assertEquals(Endringskode.UEND, revurderingjuni.arbeidsgiverOppdrag.inspektør.endringskode)
            revurderingjuni.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(17.juni, linje.fom)
                assertEquals(29.juni, linje.tom)
                assertEquals(Endringskode.UEND, linje.endringskode)
            }

            assertEquals(2, revurderingaugust.arbeidsgiverOppdrag.size)
            assertEquals(0, revurderingaugust.personOppdrag.size)
            assertEquals(Endringskode.ENDR, revurderingaugust.arbeidsgiverOppdrag.inspektør.endringskode)
            revurderingaugust.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(17.juni, linje.fom)
                assertEquals(29.juni, linje.tom)
                assertEquals(Endringskode.UEND, linje.endringskode)
            }
            revurderingaugust.arbeidsgiverOppdrag[1].inspektør.also { linje ->
                assertEquals(1.august, linje.fom)
                assertEquals(31.august, linje.tom)
                assertEquals(Endringskode.NY, linje.endringskode)
            }

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `arbeidsgiverperiode slås sammen pga avviklet ferie - med endring uten utbetalingsendring i forrige periode - ny eldre periode mens til utbetaling`() {
        a1 {
            nyttVedtak(1.juni til lørdag.den(30.juni))
            nyttVedtak(1.august til 31.august)
            håndterOverstyrTidslinje(lørdag.den(30.juni).til(30.juli).map {
                ManuellOverskrivingDag(it, Dagtype.Feriedag)
            }.plusElement(ManuellOverskrivingDag(31.juli, Dagtype.Arbeidsdag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)

            assertEquals(1, observatør.avsluttetMedVedtakEventer.getValue(1.vedtaksperiode).size)
            assertEquals(1, observatør.avsluttetMedVedtakEventer.getValue(2.vedtaksperiode).size)

            håndterSøknad(Sykdom(1.april, 16.april, 100.prosent))

            assertEquals(1, observatør.avsluttetMedVedtakEventer.getValue(1.vedtaksperiode).size)
            assertEquals(1, observatør.avsluttetMedVedtakEventer.getValue(2.vedtaksperiode).size)

            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstand(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

            håndterUtbetalt()

            assertEquals(2, observatør.avsluttetMedVedtakEventer.getValue(1.vedtaksperiode).size)
            assertEquals(1, observatør.avsluttetMedVedtakEventer.getValue(2.vedtaksperiode).size)

            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)

            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()

            assertEquals(3, observatør.avsluttetMedVedtakEventer.getValue(1.vedtaksperiode).size)
            assertEquals(2, observatør.avsluttetMedVedtakEventer.getValue(2.vedtaksperiode).size)

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }
    }

    private fun assertUtbetalingtilstander(utbetalingId: UUID, vararg status: Utbetalingstatus) {
        observatør.utbetaltEndretEventer
            .filter { it.utbetalingId == utbetalingId }
            .also { events ->
                assertEquals(status.map(Utbetalingstatus::name).toList(), events.map { it.forrigeStatus }.plus(events.last().gjeldendeStatus))
            }
    }
}
