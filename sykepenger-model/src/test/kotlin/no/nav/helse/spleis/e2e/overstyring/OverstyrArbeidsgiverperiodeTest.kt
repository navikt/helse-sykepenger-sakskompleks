package no.nav.helse.spleis.e2e.overstyring

import no.nav.helse.april
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OverstyrArbeidsgiverperiodeTest : AbstractDslTest() {

    @Test
    fun `endre arbeidsgiverperiode til å starte tidligere`() {
        a1 {
            håndterSøknad(Sykdom(20.januar, 15.februar, 100.prosent))
            håndterInntektsmelding(
                listOf(
                    17.januar til 31.januar,
                    2.februar til 2.februar
                )
            )
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            nullstillTilstandsendringer()
            assertEquals(17.januar til 15.februar, inspektør.periode(1.vedtaksperiode))
            // drar agp tilbake to dager, men glemmer å omgjøre 1. februar til sykdom
            observatør.vedtaksperiodeVenter.clear()
            håndterOverstyrTidslinje(
                listOf(
                    ManuellOverskrivingDag(15.januar, Dagtype.Sykedag, 100),
                    ManuellOverskrivingDag(16.januar, Dagtype.Sykedag, 100)
                )
            )
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)

            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(1.februar, Dagtype.Sykedag, 100)))

            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            assertEquals(2, inspektør.antallUtbetalinger)
            inspektør.sisteUtbetaling().also { revurderingen ->
                assertEquals(0, revurderingen.personOppdrag.size)
                assertEquals(1, revurderingen.arbeidsgiverOppdrag.size)
                revurderingen.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                    assertEquals(31.januar til 15.februar, linje.fom til linje.tom)
                    assertEquals(1431, linje.beløp)
                }
            }
        }
    }

    @Test
    fun `endre arbeidsgiverperiode til å starte tidligere med tidligere urelatert utbetaling`() {
        a1 {
            nyttVedtak(januar)

            håndterSøknad(Sykdom(1.april, 14.april, 100.prosent))
            håndterSøknad(Sykdom(15.april, 30.april, 100.prosent))
            håndterInntektsmelding(listOf(1.april til 16.april))
            håndterVilkårsgrunnlag(3.vedtaksperiode)
            håndterYtelser(3.vedtaksperiode)
            håndterSimulering(3.vedtaksperiode)

            nullstillTilstandsendringer()
            håndterOverstyrTidslinje(
                listOf(
                    ManuellOverskrivingDag(1.april, Dagtype.Arbeidsdag),
                    ManuellOverskrivingDag(2.april, Dagtype.Arbeidsdag),
                )
            )

            håndterVilkårsgrunnlag(3.vedtaksperiode)
            håndterYtelser(3.vedtaksperiode)

            assertTilstander(1.vedtaksperiode, AVSLUTTET)
            assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
            assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)

            assertEquals(3, inspektør.antallUtbetalinger)
            inspektør.sisteUtbetaling().also { utbetalingen ->
                assertEquals(0, utbetalingen.personOppdrag.size)
                assertEquals(1, utbetalingen.arbeidsgiverOppdrag.size)
                utbetalingen.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                    assertEquals(19.april til 30.april, linje.fom til linje.tom)
                    assertEquals(1431, linje.beløp)
                }
            }
        }
    }

}
