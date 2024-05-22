package no.nav.helse.spleis.e2e.revurdering

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class AnnullereTidligereUtbetalingE2ETest : AbstractDslTest() {

    @Test
    fun `annullere tidligere utbetaling på samme arbeidsgiver`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            val utbetalingId = inspektør.utbetalinger.single().inspektør.utbetalingId
            nyttVedtak(1.mars til 31.mars)
            håndterAnnullering(utbetalingId)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, TilstandType.TIL_INFOTRYGD)
            assertSisteTilstand(2.vedtaksperiode, TilstandType.AVVENTER_HISTORIKK_REVURDERING)
        }
    }

    @Test
    fun `annullere tidligere utbetaling på annen arbeidsgiver`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
        }
        val utbetalingId = inspektør.utbetalinger.single().inspektør.utbetalingId
        a2 {
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent))
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a1 {
            håndterAnnullering(utbetalingId)
            assertIngenFunksjonelleFeil()
            Assertions.assertEquals(Utbetalingtype.ANNULLERING, inspektør.utbetalinger.last().inspektør.type)
        }
    }
}