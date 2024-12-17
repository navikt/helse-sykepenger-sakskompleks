package no.nav.helse.spleis.e2e.inntektsmelding

import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.UgyldigeSituasjonerObservatør.Companion.assertUgyldigSituasjon
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.februar
import no.nav.helse.fredag
import no.nav.helse.til
import no.nav.helse.den
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mandag
import no.nav.helse.mars
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.september
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.torsdag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class FlereSkjæringstidspunktTest : AbstractDslTest() {

    @Test
    fun `korrigert søknad som dekker deler av perioden`() {
        a1 {
            nyttVedtak(januar)
            assertUgyldigSituasjon("FLERE_SKJÆRINGSTIDSPUNKT") {
                håndterSøknad(
                    Sykdom(20.januar, 20.januar, 100.prosent),
                    Arbeid(20.januar, 20.januar)
                )
            }

            assertEquals(listOf(21.januar, 1.januar), inspektør.skjæringstidspunkter(1.vedtaksperiode))
            assertSisteTilstand(1.vedtaksperiode, TilstandType.AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `inntektsmelding strekkes tilbake til å dekke arbeidsgiverperiode om det er helg mellom`() {
        a1 {
            håndterSøknad(mandag den 22.januar til 15.februar)
            håndterInntektsmelding(
                førsteFraværsdag = 22.januar,
                arbeidsgiverperioder = listOf(torsdag den 4.januar til fredag den 19.januar),
                beregnetInntekt = INNTEKT
            )
            assertEquals(22.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
            assertEquals(listOf(22.januar, 4.januar), inspektør.skjæringstidspunkter(1.vedtaksperiode))
            assertEquals(4.januar til 15.februar, inspektør.vedtaksperioder(1.vedtaksperiode).periode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        }
    }

    @Test
    fun `Først sier sykmeldte at det var egenmeldingsdag, så ombestemmer de seg`() {
        a1 {
            nyttVedtak(januar)

            håndterSøknad(Sykdom(15.februar, 28.februar, 100.prosent), egenmeldinger = listOf(5.februar til 5.februar))
            observatør.vedtaksperiodeVenter.last().let {
                assertEquals("INNTEKTSMELDING", it.venterPå.venteårsak.hva)
                assertEquals("SSHH SSSSSHH SSS", inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.sykdomstidslinje.toShortString())
            }

            håndterSøknad(5.februar til 5.februar)

            assertEquals("S", inspektør.vedtaksperioder(3.vedtaksperiode).inspektør.sykdomstidslinje.toShortString())
            assertTilstand(3.vedtaksperiode, TilstandType.AVVENTER_INNTEKTSMELDING)
        }
    }

    @Test
    fun `Egenmeldingsdager fra sykmelding møter begrunnelseForReduksjonEllerIkkeUtbetalt`() {
        a1 {
            håndterSøknad(Sykdom(9.mars, 14.mars, 100.prosent), egenmeldinger = listOf(2.mars til 3.mars))
            håndterInntektsmelding(listOf(2.januar til 17.januar), førsteFraværsdag = 2.mars, begrunnelseForReduksjonEllerIkkeUtbetalt = "IkkeFullStillingsandel")

            observatør.vedtaksperiodeVenter.last().let {
                assertEquals("SHH SSS", inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.sykdomstidslinje.toShortString())
                assertEquals("INNTEKTSMELDING", it.venterPå.venteårsak.hva)
                assertNull(it.venterPå.venteårsak.hvorfor)
            }
        }
    }

    @Test
    fun `Får flere skjæringstidspunkt før perioden er utbetalt`() {
        a1 {
            håndterSøknad(5.august til 20.august)
            håndterSøknad(21.august til 20.september)
            håndterInntektsmelding(listOf(5.august til 20.august))
            håndterVilkårsgrunnlag(2.vedtaksperiode)
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)

            inspektør.vedtaksperioder(2.vedtaksperiode).let {
                assertEquals("SSSSHH SSSSSHH SSSSSHH SSSSSHH SSSS", it.sykdomstidslinje.toShortString())
                assertEquals(5.august, it.inspektør.skjæringstidspunkt)
            }
            nullstillTilstandsendringer()

            håndterInntektsmelding(listOf(27.august til 27.august, 4.september til 18.september))
            assertFunksjonellFeil(Varselkode.RV_IV_11, 2.vedtaksperiode.filter())
            assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        }
    }

    @Test
    fun `Inntektsmeldingen strekker perioden tilbake, så kommer det en out of order søknad i starten av perioden inntektsmeldingen har strukket seg over`() {
        a1 {
            håndterSøknad(20.august til 3.september)
            håndterSøknad(17.september til 19.september)

            assertEquals(17.september til 19.september, inspektør.vedtaksperioder(2.vedtaksperiode).periode)
            assertEquals("SSS", inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.sykdomstidslinje.toShortString())

            håndterInntektsmelding(listOf(20.august til 4.september), førsteFraværsdag = 17.september)

            assertEquals(4.september til 19.september, inspektør.vedtaksperioder(2.vedtaksperiode).periode)
            assertEquals("U????? ??????? SSS", inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.sykdomstidslinje.toShortString())
            assertEquals(17.september, inspektør.vedtaksperioder(2.vedtaksperiode).skjæringstidspunkt)

            håndterVilkårsgrunnlag(2.vedtaksperiode)
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
            nullstillTilstandsendringer()

            håndterSøknad(4.september til 9.september)
            assertFunksjonellFeil(Varselkode.RV_IV_11, 2.vedtaksperiode.filter())
            assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
        }
    }
}
