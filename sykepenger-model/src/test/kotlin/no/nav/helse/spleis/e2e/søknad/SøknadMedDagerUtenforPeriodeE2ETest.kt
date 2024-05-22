package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Permisjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_7
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SøknadMedDagerUtenforPeriodeE2ETest: AbstractEndToEndTest() {

    @Test
    fun `eldgammel ferieperiode før sykdomsperioden klippes bort`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 28.mars))
        håndterSøknad(
            Sykdom(1.mars, 28.mars, 100.prosent),
            Ferie(1.juli(2015), 10.juli(2015)),
        )
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
    }

    @Test
    fun `søknad med arbeidsdager mellom to perioder bridger ikke de to periodene`(){
        nyttVedtak(1.januar til 19.januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(20.januar, 31.januar))
        assertVarsel(RV_SØ_7, AktivitetsloggFilter.person())
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `feriedager som vi allerede vet om fra forrige periode, trimme bort ferie, ingen warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(25.januar, 31.januar))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(25.januar, 31.januar))

        assertEquals(1.januar til 31.januar, inspektør.periode(1.vedtaksperiode) )
        assertEquals(1.februar til 28.februar, inspektør.periode(2.vedtaksperiode) )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertEquals(7, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(2.vedtaksperiode).inspektør.dagteller[Feriedag::class])
    }

    @Test
    fun `klipper bare ferie - ikke ferie i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(
            Sykdom(1.februar, 28.februar, 100.prosent),
            Ferie(1.januar, 16.januar),
            Permisjon(17.januar, 25.januar),
            Ferie(26.januar, 31.januar)
        )
        assertEquals(1.februar til 28.februar, inspektør.periode(1.vedtaksperiode) )
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
    }

    @Test
    fun `klipper bare ferie - ferie litt inn i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(
            Sykdom(1.februar, 28.februar, 100.prosent),
            Ferie(1.januar, 16.januar),
            Permisjon(17.januar, 25.januar),
            Ferie(26.januar, 2.februar)
        )
        assertEquals(1.februar til 28.februar, inspektør.periode(1.vedtaksperiode) )
        assertEquals(2, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
    }

    @Test
    fun `feriedager som vi ikke vet om og ikke treffer forrige periode, trimme bort ferie, ingen warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 22.januar))
        håndterSøknad(Sykdom(1.januar, 22.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(25.januar, 31.januar))

        assertEquals(1.januar til 22.januar, inspektør.periode(1.vedtaksperiode) )
        assertEquals(1.februar til 28.februar, inspektør.periode(2.vedtaksperiode) )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(2.vedtaksperiode).inspektør.dagteller[Feriedag::class])
        assertIngenVarsler()
    }

    @Test
    fun `feriedager som vi ikke vet om og bridger gapet til forrige periode, trimme bort ferie, ingen warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 22.januar))
        håndterSøknad(Sykdom(1.januar, 22.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(23.januar, 31.januar))

        assertEquals(1.januar til 22.januar, inspektør.periode(1.vedtaksperiode) )
        assertEquals(1.februar til 28.februar, inspektør.periode(2.vedtaksperiode) )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(2.vedtaksperiode).inspektør.dagteller[Feriedag::class])
    }

    @Test
    fun `feriedager som vi vet om og treffer forrige periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 22.januar))
        håndterSøknad(Sykdom(1.januar, 22.januar, 100.prosent), Ferie(18.januar, 22.januar))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(18.januar, 31.januar))

        assertEquals(1.januar til 22.januar, inspektør.periode(1.vedtaksperiode) )
        assertEquals(1.februar til 28.februar, inspektør.periode(2.vedtaksperiode) )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
        assertEquals(5, inspektør.vedtaksperiodeSykdomstidslinje(1.vedtaksperiode).inspektør.dagteller[Feriedag::class])
        assertEquals(null, inspektør.vedtaksperiodeSykdomstidslinje(2.vedtaksperiode).inspektør.dagteller[Feriedag::class])
    }
}
