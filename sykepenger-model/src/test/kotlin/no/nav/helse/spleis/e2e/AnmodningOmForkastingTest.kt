package no.nav.helse.spleis.e2e

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.nyPeriode
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype.Feriedag
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class AnmodningOmForkastingTest: AbstractDslTest() {

    @Test
    fun `anmodning avslås av en avsluttet vedtaksperiode`(){
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterAnmodningOmForkasting(1.vedtaksperiode)
            assertInfo("Avslår anmodning om forkasting i AVSLUTTET (kan ikke forkastes)", 1.vedtaksperiode.filter())
        }
    }

    @Test
    fun `når anmodning innfris forkastes alt på skjæringstidspunktet`(){
        (a1 og a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            nyPeriode(1.mars til 31.mars)
        }
        a2 {
            nyPeriode(1.mars til 31.mars)
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a1 {
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        }
        a2 {
            nullstillTilstandsendringer()
            håndterAnmodningOmForkasting(2.vedtaksperiode)
            assertInfo("Etterkommer anmodning om forkasting", 2.vedtaksperiode.filter())
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        }
        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        }
    }

    @Test
    fun `når anmodning innfris forkastes alt senere`() {
        a1 {
            nyPeriode(1.januar til 31.januar)
            nyPeriode(1.mars til 31.mars)
            nyPeriode(1.mai til 31.mai)
            nyPeriode(1.juli til 31.juli)
            nullstillTilstandsendringer()
            håndterAnmodningOmForkasting(2.vedtaksperiode)
            assertTilstander(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
            assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
            assertForkastetPeriodeTilstander(4.vedtaksperiode, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        }
    }

    @Test
    fun `anmodning innfris av en vedtaksperiode som avventer inntektsmelding`(){
        a1 {
            nyPeriode(1.januar til 31.januar)
            nullstillTilstandsendringer()
            håndterAnmodningOmForkasting(1.vedtaksperiode)
            assertForkastetPeriodeTilstander(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        }
    }

    @Test
    fun `Forkaster senere periode påvirker ikke pågående revurdering på tidligere periode med samme skjæringstidspunkt`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()

            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.januar, Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

            nullstillTilstandsendringer()

            assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

            håndterAnmodningOmForkasting(3.vedtaksperiode)

            assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
            assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        }
    }
}