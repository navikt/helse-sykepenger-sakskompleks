package no.nav.helse.spleis.e2e

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GrunnbeløpsreguleringTest: AbstractDslTest() {

    @Test
    fun `Grunnbeløpsregulering med allerede riktig G-beløp`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertEquals(561804.årlig, inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør.`6G`)
            nullstillTilstandsendringer()
            inspektør.vilkårsgrunnlagHistorikkInnslag()
            håndterGrunnbeløpsregulering(1.januar)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertInfo("Grunnbeløpet i sykepengegrunnlaget 2018-01-01 er allerede korrekt.", 1.vedtaksperiode.filter())
            assertEquals(561804.årlig, inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør.`6G`)
            assertTilstander(1.vedtaksperiode, AVSLUTTET)
        }
    }
}