package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import no.nav.helse.assertForventetFeil
import no.nav.helse.august
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding.Avsendersystem.ALTINN
import no.nav.helse.hendelser.Inntektsmelding.Avsendersystem.NAV_NO_SELVBESTEMT
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.BehandlingView.TilstandView.AVSLUTTET_UTEN_VEDTAK
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_24
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_8
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_13
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.infotrygdhistorikk.PersonUtbetalingsperiode
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.sisteBehov
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertInfo
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertInntektshistorikkForDato
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.finnSkjæringstidspunkt
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.grunnlag
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
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ReberegningAvAvsluttetUtenUtbetalingNyE2ETest : AbstractEndToEndTest() {

    @Test
    fun `arbeidsgiver opplyser om egenmeldinger og bruker opplyser om ferie`() {
        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent))
        håndterInntektsmelding(listOf(10.januar til 25.januar), avsendersystem = ALTINN)
        håndterSøknad(Sykdom(10.januar, 28.januar, 100.prosent), Ferie(10.januar, 28.januar))
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `omgjøre kort periode etter mottatt im - med eldre utbetalt periode`() {
        nyttVedtak(januar)

        nyPeriode(10.august til 20.august)
        håndterInntektsmelding(listOf(1.august til 16.august), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        val førsteUtbetaling = inspektør.utbetaling(0)
        inspektør.utbetaling(1).also { utbetalingInspektør ->
            assertNotEquals(førsteUtbetaling.korrelasjonsId, utbetalingInspektør.korrelasjonsId)
            assertNotEquals(førsteUtbetaling.arbeidsgiverOppdrag.inspektør.fagsystemId(), utbetalingInspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
            assertNotEquals(førsteUtbetaling.personOppdrag.inspektør.fagsystemId(), utbetalingInspektør.personOppdrag.inspektør.fagsystemId())
            assertEquals(1.august til 20.august, utbetalingInspektør.periode)
        }
    }

    @Test
    fun `inntektsmelding på kort periode gjør at en nyere kort periode skal utbetales`() {
        håndterSykmelding(Sykmeldingsperiode(10.januar, 20.januar))
        håndterSøknad(Sykdom(10.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(9.februar, 20.februar))
        håndterSøknad(Sykdom(9.februar, 20.februar, 100.prosent))
        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(10.januar til 25.januar),)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTrue((21.januar til 25.januar).all {
            inspektør.sykdomstidslinje[it] is Dag.UkjentDag
        })
    }

    @Test
    fun `revurderer eldre skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `revurderer eldre skjæringstidspunkt selv ved flere mindre perioder`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 26.januar))
        håndterSøknad(Sykdom(21.januar, 26.januar, 100.prosent))

        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(10.januar til 25.januar), vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `gjenopptar ikke behandling dersom det er nyere periode som er utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.mai, 15.mai))
        håndterSykmelding(Sykmeldingsperiode(16.mai, 28.mai))
        håndterSøknad(Sykdom(1.mai, 15.mai, 100.prosent))
        håndterSøknad(Sykdom(16.mai, 28.mai, 100.prosent))

        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(4.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `revurderer ikke avsluttet periode dersom perioden fortsatt er innenfor agp etter IM`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(5.januar til 20.januar),)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `avvist revurdering uten tidligere utbetaling kan forkastes`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterInntektsmelding(listOf(10.januar til 25.januar), beregnetInntekt = INNTEKT,)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
    }

    @Test
    fun `infotrygd har utbetalt perioden - vi har kun arbeidsgiverperiode`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        nullstillTilstandsendringer()
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 27.januar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 17.januar, INNTEKT, true)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertVarsel(RV_IT_3)
    }

    @Test
    fun `infotrygd har utbetalt perioden - vi har ingenting`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        nullstillTilstandsendringer()
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            PersonUtbetalingsperiode(ORGNUMMER, 1.januar, 27.januar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 1.januar, INNTEKT, false)
            )
        )

        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertVarsel(RV_IT_3)
    }

    @Test
    fun `infotrygd har utbetalt perioden - vi har ingenting - flere ag`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar), orgnummer = a1)
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar), orgnummer = a2)
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar), orgnummer = a2)
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT, orgnummer = a2,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, orgnummer = a2)
        nullstillTilstandsendringer()
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            PersonUtbetalingsperiode(a2, 1.januar, 27.januar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
                Inntektsopplysning(a2, 1.januar, INNTEKT, false)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `tildele utbetaling etter reberegning`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        val vedtaksperiode1Utbetalinger = inspektør.utbetalinger(1.vedtaksperiode)
        val vedtaksperiode2Utbetalinger = inspektør.utbetalinger(2.vedtaksperiode)
        assertEquals(2, vedtaksperiode1Utbetalinger.size)
        assertEquals(0, vedtaksperiode2Utbetalinger.size)

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `avvist omgjøring uten tidligere utbetaling forkaster nyere forlengelser`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(28.januar, 27.februar))
        håndterSøknad(Sykdom(28.januar, 27.februar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
    }

    @Test
    fun `avvist omgjøring uten tidligere utbetaling forkaster ikke nyere perioder`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(31.januar, 27.februar))
        håndterSøknad(Sykdom(31.januar, 27.februar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
    }

    @Test
    fun `avvist omgjøring uten tidligere utbetaling gjenopptar nyere perioder som har inntekt`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(31.januar, 27.februar))
        håndterSøknad(Sykdom(31.januar, 27.februar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)

        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 31.januar, beregnetInntekt = INNTEKT, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
    }

    @Test
    fun `inntektsmelding gjør om kort periode til arbeidsdager`() {
        håndterSykmelding(Sykmeldingsperiode(19.januar, 20.januar))
        håndterSøknad(Sykdom(18.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 3.februar))
        håndterSøknad(Sykdom(21.januar, 3.februar, 100.prosent))

        nullstillTilstandsendringer()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        håndterInntektsmelding(listOf(10.januar til 20.januar, 28.januar til 1.februar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)

        assertTrue(inspektør.sykdomstidslinje[21.januar] is Dag.FriskHelgedag)
        assertTrue(inspektør.sykdomstidslinje[27.januar] is Dag.FriskHelgedag)

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        //assertVarsel(RV_IM_4, 2.vedtaksperiode.filter(a1)) // huh? Ser bare 1 IM
        assertInfo(RV_RV_1.varseltekst, 2.vedtaksperiode.filter(a1))
        nullstillTilstandsendringer()

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVVENTER_SIMULERING)
    }

    @Test
    fun `støtter omgjøring om det er utbetalt en senere periode på samme skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(19.januar, 20.januar))
        håndterSøknad(Sykdom(18.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 3.februar))
        håndterSøknad(Sykdom(21.januar, 3.februar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(4.februar, 28.februar))
        håndterSøknad(Sykdom(4.februar, 28.februar, 100.prosent))
        håndterInntektsmelding(listOf(19.januar til 3.februar),)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        nullstillTilstandsendringer()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)

        håndterInntektsmelding(listOf(10.januar til 20.januar, 28.januar til 1.februar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `støtter omgjøring om det er utbetalt en senere periode på nyere skjæringstidspunkt`()  {
        håndterSykmelding(Sykmeldingsperiode(19.januar, 20.januar))
        håndterSøknad(Sykdom(18.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 3.februar))
        håndterSøknad(Sykdom(21.januar, 3.februar, 100.prosent))

        nyttVedtak(mai, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        nullstillTilstandsendringer()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)

        håndterInntektsmelding(listOf(10.januar til 20.januar, 28.januar til 1.februar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `inntektsmelding gjør at kort periode faller utenfor agp - før vilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(28.januar, 31.januar))
        håndterSøknad(Sykdom(28.januar, 31.januar, 100.prosent))

        nullstillTilstandsendringer()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterInntektsmelding(listOf(10.januar til 25.januar),)
        assertIngenVarsler(1.vedtaksperiode.filter(a1))
        assertInfo(RV_RV_1.varseltekst, 2.vedtaksperiode.filter(a1))
        // assertNoWarnings(2.vedtaksperiode.filter(a1))
        assertIngenVarsler(3.vedtaksperiode.filter(a1))

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `inntektsmelding gjør at kort periode faller utenfor agp - etter vilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(28.januar, 31.januar))
        håndterSøknad(Sykdom(28.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(12.januar til 27.januar),)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        nullstillTilstandsendringer()

        håndterInntektsmelding(listOf(10.januar til 25.januar),)

        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
        assertVarsel(RV_IM_4, 3.vedtaksperiode.filter())

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `revurderer ikke avsluttet periode dersom perioden fortsatt er innenfor agp etter IM selv ved flere mindre`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(21.januar, 25.januar))

        håndterSøknad(Sykdom(21.januar, 25.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(10.januar til 25.januar),)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `avsluttet periode trenger egen inntektsmelding etter at inntektsmelding treffer forrige`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(23.januar, 25.januar))
        håndterSøknad(Sykdom(23.januar, 25.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(29.januar, 29.januar))
        håndterSøknad(Sykdom(29.januar, 29.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(5.januar til 20.januar),)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `avsluttet periode trenger egen inntektsmelding etter at inntektsmelding treffer forrige 2`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 25.januar))
        håndterSøknad(Sykdom(21.januar, 25.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(29.januar, 29.januar))
        håndterSøknad(Sykdom(29.januar, 29.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(5.januar til 20.januar),)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `gjenopptar behandling på neste periode dersom inntektsmelding treffer avsluttet periode`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar))
        håndterSøknad(Sykdom(21.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(5.januar til 20.januar),)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `revurderer ved mottatt inntektsmelding - påfølgende periode med im går i vanlig løype`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 26.januar))
        håndterSøknad(Sykdom(21.januar, 26.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(30.januar, 31.januar))
        håndterSøknad(Sykdom(30.januar, 31.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(10.januar til 25.januar),)
        håndterInntektsmelding(listOf(10.januar til 25.januar), 30.januar, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `omgjører ved mottatt inntektsmelding - påfølgende periode med im går i vanlig løype - omvendt`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 26.januar))
        håndterSøknad(Sykdom(21.januar, 26.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(30.januar, 31.januar))
        håndterSøknad(Sykdom(30.januar, 31.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(10.januar til 25.januar), 30.januar, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        håndterInntektsmelding(listOf(10.januar til 25.januar), vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `inntektsmelding ag1 - ag1 må vente på inntekt for ag2`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a2)

        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a2)

        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)
    }

    @Test
    fun `inntektsmelding ag2 - ag2 må vente på inntekt for ag1`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a2)

        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a2)

        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2,)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `inntektsmelding for begge arbeidsgivere - bare én av de korte periodene skal utbetales`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a2)

        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a2)

        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(3.januar til 19.januar), orgnummer = a2,)

        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        assertEquals("FLERE_ARBEIDSGIVERE", person.personLogg.sisteBehov(Godkjenning).detaljer()["inntektskilde"] as? String)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `inntektsmelding for begge arbeidsgivere - begge de korte periodene skal utbetales`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 18.januar), orgnummer = a2)

        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar), orgnummer = a2)

        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(19.januar, 31.januar, 100.prosent), orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2,)
        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING,  orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)

        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)

        assertEquals("FLERE_ARBEIDSGIVERE", person.personLogg.sisteBehov(Godkjenning).detaljer()["inntektskilde"] as? String)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        nullstillTilstandsendringer()
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        nullstillTilstandsendringer()
        håndterUtbetalt(orgnummer = a2)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `arbeidsgiver 1 er utenfor arbeidsgiverperioden, men ikke arbeidsgiver 2`() {
        håndterSykmelding(Sykmeldingsperiode(17.juni(2022), 21.juni(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(17.juni(2022), 21.juni(2022), 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(17.juni(2022), 21.juni(2022)), orgnummer = a2)
        håndterSøknad(Sykdom(17.juni(2022), 21.juni(2022), 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2022), 3.juli(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(22.juni(2022), 3.juli(2022), 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2022), 3.juli(2022)), orgnummer = a2)
        håndterSøknad(Sykdom(22.juni(2022), 3.juli(2022), 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(20.juli(2022), 28.juli(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(20.juli(2022), 28.juli(2022), 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(29.juli(2022), 3.august(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(29.juli(2022), 3.august(2022), 100.prosent), orgnummer = a1)

        nullstillTilstandsendringer()

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
        assertTilstander(4.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)

        nullstillTilstandsendringer()

        håndterInntektsmelding(
            listOf(
                7.juni(2022) til 7.juni(2022),
                9.juni(2022) til 10.juni(2022),
                17.juni(2022) til 29.juni(2022)
            ),
            orgnummer = a1,
        )

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertTilstander(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertTilstander(4.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
    }

    @Test
    fun `arbeidsgiver 1 er utenfor arbeidsgiverperioden, men ikke arbeidsgiver 2 - feil ved revurdering forkaster periodene`() {
        håndterSykmelding(Sykmeldingsperiode(17.juni(2022), 21.juni(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(17.juni(2022), 21.juni(2022), 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(17.juni(2022), 21.juni(2022)), orgnummer = a2)
        håndterSøknad(Sykdom(17.juni(2022), 21.juni(2022), 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2022), 3.juli(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(22.juni(2022), 3.juli(2022), 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2022), 3.juli(2022)), orgnummer = a2)
        håndterSøknad(Sykdom(22.juni(2022), 3.juli(2022), 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(20.juli(2022), 28.juli(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(20.juli(2022), 28.juli(2022), 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(29.juli(2022), 3.august(2022)), orgnummer = a1)
        håndterSøknad(Sykdom(29.juli(2022), 3.august(2022), 100.prosent), orgnummer = a1)

        håndterInntektsmelding(
            listOf(
                7.juni(2022) til 7.juni(2022),
                9.juni(2022) til 10.juni(2022),
                17.juni(2022) til 29.juni(2022)
            ),
            orgnummer = a1,
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false, orgnummer = a1)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, orgnummer = a2)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD, orgnummer = a2)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(4.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD, orgnummer = a1)
    }

    @Test
    fun `infotrygd har plutselig utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(10.januar, 20.januar))
        håndterSøknad(Sykdom(10.januar, 20.januar, 100.prosent))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)

        nullstillTilstandsendringer()
        val utbetalinger = listOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 20.januar, 100.prosent, INNTEKT))
        val inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.januar, INNTEKT, true))
        håndterUtbetalingshistorikkEtterInfotrygdendring(*utbetalinger.toTypedArray(), inntektshistorikk = inntektshistorikk)
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        assertVarsel(RV_IT_3, 1.vedtaksperiode.filter())
        assertIngenVarsel(RV_IT_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `endrer arbeidsgiverperiode etter igangsatt revurdering`() {
        val forMyeInntekt = INNTEKT * 1.2
        val riktigInntekt = INNTEKT

        håndterSøknad(Sykdom(5.februar, 11.februar, 100.prosent))
        håndterSøknad(Sykdom(12.februar, 20.februar, 100.prosent))
        nullstillTilstandsendringer()
        håndterInntektsmelding(
            listOf(
                24.januar til 8.februar
            ),
            beregnetInntekt = forMyeInntekt,
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        val im = håndterInntektsmelding(
            listOf(
                22.januar til 6.februar
            ),
            beregnetInntekt = riktigInntekt,
        )
        assertTrue(im in observatør.inntektsmeldingHåndtert.map { it.first })
        assertVarsel(RV_IM_4, 1.vedtaksperiode.filter())
        assertVarsel(RV_IM_24, 1.vedtaksperiode.filter())
        assertEquals(22.januar til 11.februar, inspektør.periode(1.vedtaksperiode))
        assertEquals("UUUUUGG UUUUUGG SSSSSHH SSSSSHH SS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(1.vedtaksperiode)

        assertInfo(RV_RV_1.varseltekst, 1.vedtaksperiode.filter(ORGNUMMER))
        assertInfo(RV_RV_1.varseltekst, 2.vedtaksperiode.filter(ORGNUMMER))

        assertNotNull(vilkårsgrunnlag)

        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inspektør.inntektsgrunnlag.inspektør

        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(riktigInntekt, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }

        assertEquals(riktigInntekt, vilkårsgrunnlag.inspektør.inntektsgrunnlag.inspektør.sykepengegrunnlag)

        val utbetaling = inspektør.gjeldendeUtbetalingForVedtaksperiode(1.vedtaksperiode)
        val utbetalingInspektør = utbetaling.inspektør

        val førsteUtbetalingsdag = utbetalingInspektør.utbetalingstidslinje[7.februar]
        assertEquals(riktigInntekt, førsteUtbetalingsdag.økonomi.inspektør.aktuellDagsinntekt)
        assertEquals(riktigInntekt, førsteUtbetalingsdag.økonomi.inspektør.arbeidsgiverRefusjonsbeløp)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING
        )
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `arbeidsgiver angrer på innsendt arbeidsgiverperiode - endrer ikke på sykdomstidslinjen fra im2`() {
        håndterSykmelding(Sykmeldingsperiode(5.februar, 20.februar))
        håndterSøknad(Sykdom(5.februar, 20.februar, 100.prosent), Ferie(10.februar, 20.februar))
        nullstillTilstandsendringer()
        håndterInntektsmelding(
            listOf(
                29.januar til 13.februar
            ),
        )
        håndterInntektsmelding(
            listOf(
                22.januar til 6.februar
            ),
        )

        assertTrue(inspektør.sykdomstidslinje[10.februar] is Dag.ArbeidsgiverHelgedag)
        assertTrue(inspektør.sykdomstidslinje[11.februar] is Dag.ArbeidsgiverHelgedag)
        assertTrue(inspektør.sykdomstidslinje[13.februar] is Dag.Arbeidsgiverdag)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `omgjøring med ghost`() {
        val beregnetInntektA1 = 31000.månedlig

        håndterSykmelding(Sykmeldingsperiode(10.januar, 25.januar), orgnummer = a1)
        håndterSøknad(Sykdom(10.januar, 25.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = beregnetInntektA1, orgnummer = a1,)

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), beregnetInntektA1.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 32000.månedlig.repeat(3))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntekter, arbeidsforhold = emptyList()),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].let {
            assertEquals(1063.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(beregnetInntektA1, it.økonomi.inspektør.aktuellDagsinntekt)
        }

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `revurdere etter at én arbeidsgiver har blitt til to`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT, orgnummer = a1,)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                listOf(
                    grunnlag(a1, 1.januar, 31000.månedlig.repeat(3)),
                    grunnlag(a2, 1.januar, 31000.månedlig.repeat(3)),
                ), arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        forlengVedtak(februar, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(10.mars, 22.mars), orgnummer = a2)
        håndterSøknad(Sykdom(10.mars, 22.mars, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), orgnummer = a1)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)

        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(27.februar, Dagtype.Feriedag)
        ), orgnummer = a1)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        nullstillTilstandsendringer()

        assertTilstander(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `forkaster vedtaksperioder i revurdering som kun består av AUU`() {
        håndterSykmelding(Sykmeldingsperiode(10.januar, 20.januar))
        håndterSøknad(Sykdom(10.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 25.januar))
        håndterSøknad(Sykdom(21.januar, 25.januar, 100.prosent))

        håndterInntektsmelding(listOf(2.januar til 17.januar),)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))

        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
    }

    @Test
    fun `Skal ikke forkaste vedtaksperioder med overlappende utbetaling som treffes av søknad som forkastes på direkten`() {
        håndterSøknad(Sykdom(10.januar, 20.januar, 100.prosent))
        håndterSøknad(Sykdom(21.januar, 31.januar, 100.prosent))

        håndterInntektsmelding(listOf(5.januar til 20.januar),)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(5.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(5.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))

        håndterInntektsmelding(listOf(2.januar til 17.januar), beregnetInntekt = INNTEKT*1.2,)
        assertEquals(2.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(2.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))

        assertTilstander(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)

        assertEquals(2.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(2.januar, inspektør.skjæringstidspunkt(2.vedtaksperiode))
    }

    @Test
    fun `allerede utbetalt i Infotrygd uten utbetaling etterpå`() {
        nyPeriode(2.januar til 17.januar)
        håndterUtbetalingshistorikkEtterInfotrygdendring(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 17.januar, 100.prosent, INNTEKT))
        assertVarsel(RV_IT_3, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD)
    }

    @Test
    fun `omgjøring med funksjonell feil i inntektsmelding fra Altinn eller LPS`() {
        håndterSøknad(Sykdom(2.januar, 17.januar, 100.prosent))
        nyttVedtak(18.januar til 31.januar, arbeidsgiverperiode = listOf(2.januar til 17.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()
        val im = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FiskerMedHyre",
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            avsendersystem = ALTINN
        )

        assertEquals(2.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertInntektshistorikkForDato(INNTEKT, 1.januar, inspektør)
        assertTrue(im in observatør.inntektsmeldingIkkeHåndtert)
        assertFunksjonellFeil(RV_IM_8, 1.vedtaksperiode.filter())
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.behandlinger.let {
            assertEquals(4, it.size)
            assertTrue(it.all { behalding -> behalding.tilstand == AVSLUTTET_UTEN_VEDTAK })
            assertEquals(im, it[3].kilde.meldingsreferanseId)
        }
    }

    @Test
    fun `omgjøring med funksjonell feil i inntektsmelding fra portalen`() {
        håndterSøknad(Sykdom(2.januar, 17.januar, 100.prosent))
        nyttVedtak(18.januar til 31.januar, arbeidsgiverperiode = listOf(2.januar til 17.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()
        val im = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FiskerMedHyre",
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            avsendersystem = NAV_NO_SELVBESTEMT
        )
        assertEquals(2.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertInntektshistorikkForDato(INNTEKT, 2.januar, inspektør)

        assertForventetFeil(
            forklaring = "Støtter ikke denne inntektsmeldingen",
            nå = {
                assertTrue(im in observatør.inntektsmeldingHåndtert.map { it.first })
            },
            ønsket = {
                assertTrue(im in observatør.inntektsmeldingIkkeHåndtert)
            }
        )

        assertFunksjonellFeil(RV_IM_8, 1.vedtaksperiode.filter())
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.behandlinger.let {
            assertEquals(4, it.size)
            assertTrue(it.all { behalding -> behalding.tilstand == AVSLUTTET_UTEN_VEDTAK })
            assertEquals(im, it[3].kilde.meldingsreferanseId)
        }
    }
}
