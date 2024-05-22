package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.tilGodkjenning
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MakstidIAvventerBlokkerendePeriodeTest: AbstractDslTest() {

    @Test
    fun `periode i avventer blokkerende som venter på inntektsmelding fra annen arbeidsgiver bør ha samme timeout som avventer inntektsmelding`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a2 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            assertEquals(LocalDate.now().plusDays(180), venterTil(1.vedtaksperiode))
        }
        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertEquals(LocalDate.now().plusDays(180), venterTil(1.vedtaksperiode))
        }
    }

    @Test
    fun `periode i avventer blokkerende som venter på søknad fra annen arbeidsgiver venter i 3 måneder`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        }
        a2 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertEquals(LocalDate.now().plusDays(90), venterTil(1.vedtaksperiode))
        }
    }

    @Test
    fun `periode i avventer blokkerende venter på annen periode til godkjenning har evig timeout`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertEquals(LocalDate.MAX, venterTil(2.vedtaksperiode))
        }
    }

    @Test
    fun `periode i avventer blokkerende som venter på inntektsmelding fra annen arbeidsgiver tross tidligere periode til godkjenning har samme timeout som avventer inntektsmelding`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a2 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            assertEquals(LocalDate.now().plusDays(180), venterTil(1.vedtaksperiode))
        }
        a1 {
            assertEquals(LocalDate.MAX, venterTil(1.vedtaksperiode))
            assertEquals(LocalDate.now().plusDays(180), venterTil(2.vedtaksperiode))
        }
    }

    @Test
    fun `perioder i avventer blokkerende som kun venter på godkjenning har evig timeout`() {
        a1 {
            tilGodkjenning(1.januar til 31.januar)
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a2 {
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertEquals(LocalDate.MAX, venterTil(1.vedtaksperiode))
        }
        a1 {
            assertEquals(LocalDate.MAX, venterTil(1.vedtaksperiode))
            assertEquals(LocalDate.MAX, venterTil(2.vedtaksperiode))
        }
    }

    private fun venterTil(vedtaksperiodeId: UUID) =
        observatør.vedtaksperiodeVenter.last { it.vedtaksperiodeId == vedtaksperiodeId }.venterTil.toLocalDate()
}