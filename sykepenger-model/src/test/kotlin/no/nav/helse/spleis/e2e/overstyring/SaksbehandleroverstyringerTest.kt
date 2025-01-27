package no.nav.helse.spleis.e2e.overstyring

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.a1
import no.nav.helse.hendelser.Dagtype.Arbeidsdag
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.MinimumSykdomsgradsvurderingMeldingData
import no.nav.helse.hendelser.OverstyrTidslinjeData
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_7
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_17
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_4
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SaksbehandleroverstyringerTest: AbstractDslTest() {

    @Test
    fun `saksbehandler oversyrer tidslinje og vurderer minimum sykdomsgrad i en smell`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 19.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            observatør.utkastTilVedtakEventer.single().tags.let { tags ->
                assertTrue(tags.contains("Avslag"))
                assertTrue(tags.contains("IngenUtbetaling"))
            }

            håndterSaksbehandleroverstyringer(
                OverstyrTidslinjeData(a1, listOf(ManuellOverskrivingDag(1.januar, Arbeidsdag))),
                MinimumSykdomsgradsvurderingMeldingData(perioderMedMinimumSykdomsgradVurdertOK = setOf(januar), emptySet())
            )
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING_REVURDERING)
            assertEquals(2.januar, inspektør.vedtaksperioder(1.vedtaksperiode).skjæringstidspunkt)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)

            observatør.utkastTilVedtakEventer.last().tags.let { tags ->
                assertTrue(tags.contains("Innvilget"))
                assertTrue(tags.contains("Arbeidsgiverutbetaling"))
            }

            assertVarsler(1.vedtaksperiode, RV_VV_4, RV_IV_7, RV_VV_17)
        }
    }
}
