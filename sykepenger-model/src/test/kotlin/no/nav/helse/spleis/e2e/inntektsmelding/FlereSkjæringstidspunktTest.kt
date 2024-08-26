package no.nav.helse.spleis.e2e.inntektsmelding

import java.util.UUID
import no.nav.helse.Toggle
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.februar
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class FlereSkjæringstidspunktTest: AbstractDslTest() {

    @Test
    fun `Først sier sykmeldte at det var egenmeldingsdag, så ombestemmer de seg`() = Toggle.EgenmeldingStrekkerIkkeSykdomstidslinje.enable {
        a1 {
            nyttVedtak(januar)

            håndterSøknad(Sykdom(15.februar, 28.februar, 100.prosent), egenmeldinger = listOf(5.februar til 5.februar))
            observatør.vedtaksperiodeVenter.last().let {
                assertEquals("INNTEKTSMELDING", it.venterPå.venteårsak.hva)
                assertEquals("SSHH SSSSSHH SSS", inspektør.sykdomstidslinje(2.vedtaksperiode).toShortString())
            }

            håndterSøknad(5.februar til 5.februar)

            assertEquals("S", inspektør.sykdomstidslinje(3.vedtaksperiode).toShortString())
            assertTilstand(3.vedtaksperiode, TilstandType.AVVENTER_INNTEKTSMELDING)
        }
    }

    @Test
    fun `Egenmeldingsdager fra sykmelding møter begrunnelseForReduksjonEllerIkkeUtbetalt`() = Toggle.EgenmeldingStrekkerIkkeSykdomstidslinje.enable {
        a1 {
            håndterSøknad(Sykdom(9.mars, 14.mars, 100.prosent), egenmeldinger = listOf(2.mars til 3.mars))
            håndterInntektsmelding(listOf(2.januar til 17.januar), førsteFraværsdag = 2.mars, begrunnelseForReduksjonEllerIkkeUtbetalt = "IkkeFullStillingsandel")

            observatør.vedtaksperiodeVenter.last().let {
                assertEquals("SHH SSS", inspektør.sykdomstidslinje(1.vedtaksperiode).toShortString())
                assertEquals("INNTEKTSMELDING", it.venterPå.venteårsak.hva)
                assertNull(it.venterPå.venteårsak.hvorfor)
            }
        }
    }

    private companion object {
        private fun TestArbeidsgiverInspektør.sykdomstidslinje(vedtaksperiodeId: UUID) = vedtaksperioder(vedtaksperiodeId).inspektør.behandlinger.last().endringer.last().sykdomstidslinje
    }
}