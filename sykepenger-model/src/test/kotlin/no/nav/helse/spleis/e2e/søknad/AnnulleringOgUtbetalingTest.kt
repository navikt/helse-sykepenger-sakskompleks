package no.nav.helse.spleis.e2e.søknad

import java.util.UUID
import no.nav.helse.EnableToggle
import no.nav.helse.Toggle
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.*
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@EnableToggle(Toggle.AnnullereOgUtbetale::class)
internal class AnnulleringOgUtbetalingTest : AbstractEndToEndTest() {

    @Test
    fun `tidligere periode med ferie får samme arbeidsgiverperiode som nyere periode`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(1.februar, 28.februar))

        nyttVedtak(1.mars, 31.mars)

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
        assertUtbetalingtilstander(marsutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(annulleringAvJanuar.utbetalingId, NY, IKKE_UTBETALT, ANNULLERT)
        assertUtbetalingtilstander(februarutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(revurderingAvMars.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)

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

        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVSLUTTET)
        assertSisteTilstand(3.vedtaksperiode, TilstandType.AVSLUTTET)

    }

    @Test
    fun `reberegne og forkaste utbetaling som inneholder annulleringer`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(1.februar, 28.februar))

        nyttVedtak(1.mars, 31.mars)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingshistorikkEtterInfotrygdendring(ArbeidsgiverUtbetalingsperiode("orgnr", 1.mai(2017), 5.mai(2017), 100.prosent, 1000.daglig))
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
    fun `korrigerer periode med bare ferie`()  {
        nyttVedtak(3.januar, 26.januar)
        nyttVedtak(3.mars, 26.mars)
        nullstillTilstandsendringer()

        håndterSøknad(Sykdom(3.mars, 26.mars, 100.prosent), Ferie(3.mars, 26.mars))
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
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
        assertUtbetalingtilstander(januarutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(marsutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(annulleringAvMars.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, ANNULLERT)
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
    fun `to uavhengige arbeidsgiverperioder blir til en som følge av overstyring`()  {
        nyttVedtak(3.januar, 26.januar)
        håndterSøknad(Sykdom(27.januar, 28.februar, 100.prosent), Arbeid(13.februar, 28.februar))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        nyttVedtak(3.mars, 31.mars)
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
        assertUtbetalingtilstander(januarutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(februarutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(marsutbetaling.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(annulleringAvMars.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, ANNULLERT)
        assertUtbetalingtilstander(revurderingAvFebruar.utbetalingId, NY, IKKE_UTBETALT, GODKJENT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)
        assertUtbetalingtilstander(revurderingAvMars.utbetalingId, NY, IKKE_UTBETALT, AVVENTER_ARBEIDSGIVERKVITTERING, UTBETALT)

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

    private fun assertUtbetalingtilstander(utbetalingId: UUID, vararg status: Utbetalingstatus) {
        observatør.utbetaltEndretEventer
            .filter { it.utbetalingId == utbetalingId }
            .also { events ->
                assertEquals(status.map(Utbetalingstatus::name).toList(), events.map { it.forrigeStatus }.plus(events.last().gjeldendeStatus))
            }
    }
}
