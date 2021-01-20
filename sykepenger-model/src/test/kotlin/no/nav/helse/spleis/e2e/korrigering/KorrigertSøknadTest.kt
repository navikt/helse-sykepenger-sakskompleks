package no.nav.helse.spleis.e2e.korrigering

import no.nav.helse.Toggles
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.*
import no.nav.helse.hendelser.til
import no.nav.helse.person.TilstandType.*
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.testhelpers.SykdomstidslinjeInspektør
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KorrigertSøknadTest : AbstractEndToEndTest() {

    @Test
    fun `Arbeidsdag i søknad nr 2 kaster ut perioden`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 30.januar, 100.prosent), Arbeid(31.januar, 31.januar))
            assertForkastetPeriodeTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, TIL_INFOTRYGD)
        }
    }

    @Test
    fun `Støtter ikke korrigerende søknad på utbetalt vedtaksperiode`() {
        Toggles.KorrigertSøknadToggle.enable {
            nyttVedtak(1.januar, 31.januar)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
            assertErrors(inspektør)
        }
    }

    @Test
    fun `Korrigerer feriedag til sykedag`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Sykedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Korrigerer sykedag til feriedag`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Korrigerer feriedag til sykedag i forlengelse`() {
        Toggles.KorrigertSøknadToggle.enable {
            nyttVedtak(1.januar, 31.januar)
            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[28.februar] is Sykedag)
            }
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, AVVENTER_HISTORIKK)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_UFERDIG_GAP`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

            håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar))
            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[28.februar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
            assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_UFERDIG_GAP`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

            håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 2.februar)
            håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar))
            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[28.februar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
            assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP, AVVENTER_UFERDIG_GAP)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[28.februar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
            assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_UFERDIG_FORLENGELSE`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))

            håndterSykmelding(Sykmeldingsperiode(11.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
            håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
            assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, AVVENTER_UFERDIG_FORLENGELSE)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_FERDIG_GAP`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterUtbetalingshistorikk(1.vedtaksperiode)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_VILKÅRSPRØVING_GAP`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_HISTORIKK`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Feriedag)
            }
            assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK)
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Går tilbake til AVVENTER_HISTORIKK når søknaden kommer inn i AVVENTER_SIMULERING`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Sykedag)
            }
            assertTilstander(
                1.vedtaksperiode,
                START,
                MOTTATT_SYKMELDING_FERDIG_GAP,
                AVVENTER_GAP,
                AVVENTER_VILKÅRSPRØVING_GAP,
                AVVENTER_HISTORIKK,
                AVVENTER_SIMULERING,
                AVVENTER_HISTORIKK
            )
            assertEquals(1, inspektør.personLogg.warn().size)
        }
    }

    @Test
    fun `Går tilbake til AVVENTER_HISTORIKK når søknaden kommer inn i AVVENTER_GODKJENNING`() {
        Toggles.KorrigertSøknadToggle.enable {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

            SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also {
                assertTrue(it[31.januar] is Sykedag)
            }
            assertTilstander(
                1.vedtaksperiode,
                START,
                MOTTATT_SYKMELDING_FERDIG_GAP,
                AVVENTER_GAP,
                AVVENTER_VILKÅRSPRØVING_GAP,
                AVVENTER_HISTORIKK,
                AVVENTER_SIMULERING,
                AVVENTER_GODKJENNING,
                AVVENTER_HISTORIKK
            )
            assertEquals(1, inspektør.personLogg.warn().size)
            assertTrue(observatør.reberegnedeVedtaksperioder.contains(1.vedtaksperiode))
        }
    }
}
