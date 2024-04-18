package no.nav.helse.dsl

import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
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
import no.nav.helse.spleis.e2e.assertInntektForDato
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class TestPersonTest : AbstractDslTest() {
    @Test
    fun `oppretter standardperson`() {
        val inspektør = inspiser(personInspektør)
        assertEquals(TestPerson.UNG_PERSON_FNR_2018, inspektør.personidentifikator)
        assertEquals(TestPerson.UNG_PERSON_FDATO_2018, inspektør.fødselsdato)
        assertEquals(TestPerson.AKTØRID, inspektør.aktørId)
        assertNull(inspektør.dødsdato)
    }

    @Test
    fun `kan sende sykmelding til arbeidsgiver`() {
        a1.håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        assertEquals(1, a1.inspektør.sykmeldingsperioder().size)
    }

    @Test
    fun `kan teste utenfor arbeidsgiver-kontekst`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
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
    }

    @Test
    fun `kan sende sykmelding via testblokk`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        }
        assertEquals(1, a1.inspektør.sykmeldingsperioder().size)
    }

    @Test
    fun `kan sende utbetale`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
            håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), INNTEKT)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
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
        }
    }

    @Test
    fun `flere arbeidsgivere`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        }
        a2 {
            håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), INNTEKT)
            assertTilstander(
                1.vedtaksperiode,
                START,
                AVVENTER_INFOTRYGDHISTORIKK,
                AVVENTER_INNTEKTSMELDING_REPLAY,
                AVVENTER_INNTEKTSMELDING,
                AVVENTER_BLOKKERENDE_PERIODE
            )
        }
        a2 {
            håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), INNTEKT)
            assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_REPLAY, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a1.assertTilstander(1.vedtaksperiode(a1), START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING_REPLAY, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `ingen historie med inntektsmelding først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), INNTEKT)
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
            assertEquals(18, it.sykdomstidslinje.inspektør.dagteller[Dag.Sykedag::class])
            assertEquals(6, it.sykdomstidslinje.inspektør.dagteller[Dag.SykHelgedag::class])
        }
        Assertions.assertNotNull(inspektør.sisteMaksdato(1.vedtaksperiode))
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
        Assertions.assertTrue(1.vedtaksperiode in observatør.utbetalteVedtaksperioder)
        inspektør.sykdomstidslinje.inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(3.januar til 26.januar, it.first())
        }
    }
}
