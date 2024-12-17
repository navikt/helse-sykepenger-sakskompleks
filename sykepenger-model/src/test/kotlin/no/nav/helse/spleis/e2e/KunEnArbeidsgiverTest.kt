package no.nav.helse.spleis.e2e

import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Utlandsopphold
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_8
import no.nav.helse.september
import no.nav.helse.sisteBehov
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KunEnArbeidsgiverTest : AbstractDslTest() {

    @Test
    fun `ingen historie med inntektsmelding først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        inspektør.sykdomstidslinje.inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(3.januar til 26.januar, it.first())
        }
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)
        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertInntektForDato(INNTEKT, 3.januar, inspektør = it)
            assertEquals(2, it.sykdomshistorikk.size)
            assertEquals(18, it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
            assertEquals(6, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
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
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(1.vedtaksperiode in observatør.utbetalteVedtaksperioder)
        inspektør.sykdomstidslinje.inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(3.januar til 26.januar, it.first())
        }
    }

    @Test
    fun `ingen historie med to søknader til arbeidsgiver før inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar))
        håndterSøknad(Sykdom(3.januar, 5.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(8.januar, 10.januar))
        håndterSøknad(Sykdom(8.januar, 10.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 22.januar))
        håndterSøknad(Sykdom(11.januar, 22.januar, 100.prosent))

        håndterInntektsmelding(arbeidsgiverperioder = listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(3.vedtaksperiode, INNTEKT)

        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertInntektForDato(INNTEKT, 3.januar, inspektør = it)
            assertEquals(6, it.sykdomshistorikk.size)
            assertEquals(4, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
            assertEquals(14, it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(
            3.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med to søknader (med gap mellom) til arbeidsgiver først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 4.januar))
        håndterSøknad(Sykdom(3.januar, 4.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(8.januar, 10.januar))
        håndterSøknad(Sykdom(8.januar, 10.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 22.januar))
        håndterSøknad(Sykdom(11.januar, 22.januar, 100.prosent))

        håndterInntektsmelding(listOf(3.januar til 4.januar, 8.januar til 21.januar), INNTEKT)

        håndterVilkårsgrunnlag(3.vedtaksperiode)

        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertInntektForDato(INNTEKT, 8.januar, inspektør = it)
            assertEquals(6, it.sykdomshistorikk.size)
            assertEquals(4, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
            assertEquals(13, it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(
            3.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med Søknad først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)
        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertInntektForDato(INNTEKT, 3.januar, inspektør = it)
            assertEquals(2, it.sykdomshistorikk.size)
            assertEquals(18, it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
            assertEquals(6, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
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
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(1.vedtaksperiode in observatør.utbetalteVedtaksperioder)
    }

    @Test
    fun `Søknad med utenlandsopphold gir warning`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent), Utlandsopphold(11.januar, 15.januar))
        assertVarsler()
        assertVarsel(RV_SØ_8, 1.vedtaksperiode.filter())
        inspektør.also {
            assertEquals(1, it.sykdomshistorikk.size)
            assertEquals(18, it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
            assertEquals(6, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
        }
    }

    @Test
    fun `søknad sendt etter 3 mnd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertInntektForDato(INNTEKT, 3.januar, inspektør = it)
            assertEquals(2, it.sykdomshistorikk.size)
            assertNull(it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
            assertEquals(6, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
            assertEquals(0, it.utbetalinger(1.vedtaksperiode).size)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `vedtaksperioder som avventer inntektsmelding strekkes tilbake til å dekke arbeidsgiverperiode`() {
        nyPeriode(januar, a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertEquals(januar, inspektør.vedtaksperioder(1.vedtaksperiode).periode)
        håndterInntektsmelding(
            førsteFraværsdag = 1.januar,
            arbeidsgiverperioder = listOf(16.desember(2017) til 31.desember(2017)),
            beregnetInntekt = INNTEKT
        )
        assertEquals("GG UUUUUGG UUUUUGG SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        assertEquals(16.desember(2017) til 31.januar, inspektør.vedtaksperioder(1.vedtaksperiode).periode)
    }

    @Test
    fun `inntektsmeldingen padder ikke senere vedtaksperioder med arbeidsdager`() {
        håndterSykmelding(Sykmeldingsperiode(4.januar, 22.januar))
        håndterSøknad(Sykdom(4.januar, 22.januar, 100.prosent))
        håndterInntektsmelding(listOf(4.januar til 19.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(24.januar, 31.januar))
        håndterSøknad(Sykdom(24.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(4.januar til 19.januar),
            beregnetInntekt = INNTEKT,
            førsteFraværsdag = 24.januar
        )
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertInntektForDato(INNTEKT, 4.januar, inspektør = it)
            assertInntektForDato(INNTEKT, 24.januar, inspektør = it)
            assertEquals(4.januar, it.skjæringstidspunkt(1.vedtaksperiode))
            assertEquals(24.januar, it.skjæringstidspunkt(2.vedtaksperiode))
            assertEquals(19, it.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
            assertEquals(8, it.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
            assertEquals(1, it.sykdomstidslinje.inspektør.dagteller[Dag.UkjentDag::class])
        }
    }

    @Test
    fun `forlengelseperioden avsluttes ikke automatisk hvis warnings`() {
        nyttVedtak(1.januar til 20.januar, 100.prosent)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar))
        håndterSøknad(Sykdom(21.januar, 31.januar, 100.prosent), Ferie(21.januar, 31.januar))
        håndterYtelser(2.vedtaksperiode, arbeidsavklaringspenger = listOf(3.januar.minusDays(60) til 5.januar.minusDays(60)))
        assertVarsler(2.vedtaksperiode.filter())
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `To perioder med opphold`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertActivities()
        håndterSøknad(Sykdom(1.februar, 23.februar, 100.prosent))
        håndterInntektsmelding(listOf(1.februar til 16.februar), INNTEKT)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertIngenFunksjonelleFeil()
        assertActivities()
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
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Venter på å bli kilt etter inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 7.januar))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 23.februar))
        håndterSøknad(Sykdom(3.januar, 7.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterSøknad(Sykdom(8.januar, 23.februar, 100.prosent))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertIngenFunksjonelleFeil()
        assertActivities()
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Fortsetter før andre søknad`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        håndterSøknad(Sykdom(29.januar, 23.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertIngenFunksjonelleFeil()
        assertActivities()
        inspektør.also {
            assertEquals(3.januar, it.skjæringstidspunkt(1.vedtaksperiode))
            assertEquals(3.januar, it.skjæringstidspunkt(2.vedtaksperiode))
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
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder der den første er i utbetaling feilet`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AVVIST)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar))
        håndterSøknad(Sykdom(29.januar, 23.februar, 100.prosent))

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
            TIL_UTBETALING
        )
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE
        )
    }

    @Test
    fun `Sykmelding med gradering`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 50.prosent, 50.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        assertIngenFunksjonelleFeil()
        assertActivities()
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
            TIL_UTBETALING
        )
    }

    @Test
    fun `Person uten skjæringstidspunkt feiler ikke i validering av Utbetalingshistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(23.oktober(2020), 18.november(2020)))
        håndterSøknad(Sykdom(23.oktober(2020), 18.november(2020), 100.prosent), Ferie(23.oktober(2020), 18.november(2020)))
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `oppretter ikke ny vedtaksperiode ved søknad som overlapper med forkastet periode`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendTilGosys = true)

        håndterSykmelding(januar)
        håndterSøknad(januar)

        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
    }

    @Test
    fun `Inntektskilde i godkjenningsbehov for en arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.desember(2020), 31.desember(2020)))
        håndterSøknad(Sykdom(1.desember(2020), 31.desember(2020), 100.prosent))
        håndterInntektsmelding(listOf(1.desember(2020) til 16.desember(2020)), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        assertEquals(UtbetalingInntektskilde.EN_ARBEIDSGIVER, inspektør.inntektskilde(1.vedtaksperiode))
        assertEquals("EN_ARBEIDSGIVER", inspiser(personInspektør).aktivitetslogg.sisteBehov(1.vedtaksperiode).detaljer()["inntektskilde"])
    }

    @Test
    fun `sender med skjæringstidspunkt på godkjenningsbehov`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        val godkjenningsbehov = inspiser(personInspektør).aktivitetslogg.sisteBehov(Aktivitet.Behov.Behovtype.Godkjenning)
        assertEquals(1.januar.toString(), godkjenningsbehov.detaljer()["skjæringstidspunkt"])
    }

    @Test
    fun `Starter ikke ny arbeidsgiverperiode dersom flere opphold til sammen utgjør over 16 dager når hvert opphold er under 16 dager - opphold starter på helg`() {
        håndterSykmelding(Sykmeldingsperiode(27.januar(2021), 2.februar(2021)))
        håndterSøknad(Sykdom(27.januar(2021), 2.februar(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(3.februar(2021), 7.februar(2021)))
        håndterSøknad(Sykdom(3.februar(2021), 7.februar(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(8.februar(2021), 12.februar(2021)))
        håndterSøknad(Sykdom(8.februar(2021), 12.februar(2021), 100.prosent))

        håndterInntektsmelding(
            listOf(
                28.desember(2020) til 28.desember(2020),
                13.januar(2021) til 15.januar(2021),
                27.januar(2021) til 7.februar(2021)
            ), INNTEKT
        )

        håndterVilkårsgrunnlag(3.vedtaksperiode, INNTEKT)

        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        inspektør.utbetalingUtbetalingstidslinje(0).inspektør.also {
            assertEquals(5, it.navDagTeller)
            assertEquals(16, it.arbeidsgiverperiodeDagTeller)
        }
    }

    @Test
    fun `Starter ikke ny arbeidsgiverperiode dersom flere opphold til sammen utgjør over 16 dager når hvert opphold er under 16 dager`() {
        håndterSykmelding(Sykmeldingsperiode(27.januar(2021), 2.februar(2021)))
        håndterSøknad(Sykdom(27.januar(2021), 2.februar(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(3.februar(2021), 7.februar(2021)))
        håndterSøknad(Sykdom(3.februar(2021), 7.februar(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(8.februar(2021), 12.februar(2021)))
        håndterSøknad(Sykdom(8.februar(2021), 12.februar(2021), 100.prosent))
        håndterInntektsmelding(
            listOf(
                27.desember(2020) til 27.desember(2020),
                12.januar(2021) til 14.januar(2021),
                27.januar(2021) til 7.februar(2021)
            ), INNTEKT
        )

        håndterVilkårsgrunnlag(3.vedtaksperiode, INNTEKT)

        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        inspektør.utbetalingUtbetalingstidslinje(0).inspektør.also {
            assertEquals(5, it.navDagTeller)
            assertEquals(16, it.arbeidsgiverperiodeDagTeller)
        }
    }

    @Test
    fun `Starter ikke ny arbeidsgiverperiode dersom flere opphold til sammen utgjør over 16 dager når hvert opphold er under 16 dager - opphold etter arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2021), 10.januar(2021)))
        håndterSøknad(Sykdom(1.januar(2021), 10.januar(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(20.januar(2021), 25.januar(2021)))
        håndterSøknad(Sykdom(20.januar(2021), 25.januar(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(5.februar(2021), 12.februar(2021)))
        håndterSøknad(Sykdom(5.februar(2021), 12.februar(2021), 100.prosent))

        håndterInntektsmelding(
            listOf(
                1.januar(2021) til 10.januar(2021),
                20.januar(2021) til 25.januar(2021)
            ), INNTEKT, førsteFraværsdag = 5.februar(2021)
        )
        håndterVilkårsgrunnlag(3.vedtaksperiode, INNTEKT)

        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        inspektør.utbetalingUtbetalingstidslinje(0).inspektør.also {
            assertEquals(6, it.navDagTeller)
            assertEquals(16, it.arbeidsgiverperiodeDagTeller)
        }
    }

    @Test
    fun `foreldet sykdomsdag etter opphold skal ikke bli til navdag`() {
        nyttVedtak(15.januar til 7.februar)

        håndterSykmelding(
            Sykmeldingsperiode(22.februar, 14.mars),
            mottatt = 6.august.atStartOfDay(),
            sykmeldingSkrevet = 6.august.atStartOfDay()
        )
        håndterSøknad(Sykdom(22.februar, 14.mars, 50.prosent), sendtTilNAVEllerArbeidsgiver = 8.august)

        håndterInntektsmelding(listOf(22.februar til 14.mars), INNTEKT)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        inspektør.utbetalingstidslinjer(2.vedtaksperiode).inspektør.let {
            assertEquals(6, it.navHelgDagTeller)
            assertEquals(15, it.foreldetDagTeller)
        }
    }

    @Test
    fun `Periode som kommer inn som SøknadArbeidsgiver selv om det er mindre enn 16 dager gap til forrige periode`() {
        nyttVedtak(1.september(2021) til 24.september(2021))

        håndterSykmelding(Sykmeldingsperiode(12.oktober(2021), 22.oktober(2021)))
        håndterSøknad(Sykdom(12.oktober(2021), 22.oktober(2021), 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(23.oktober(2021), 29.oktober(2021)))
        håndterSøknad(Sykdom(23.oktober(2021), 29.oktober(2021), 100.prosent))

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterInntektsmelding(listOf(12.oktober(2021) til 27.oktober(2021)), INNTEKT)

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTrue(inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.utbetalingstidslinje.inspektør.erNavdag(18.oktober(2021)))
    }
}
