package no.nav.helse.spleis.e2e

import no.nav.helse.februar
import no.nav.helse.fredag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.søndag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AvsluttetUtenUtbetalingE2ETest: AbstractEndToEndTest() {
    /*
        Hvis vi har en kort periode som har endt opp i AVSLUTTET_UTEN_UTBETALING vil alle etterkommende perioder
        bli stuck med å vente på den korte perioden. Da vil de aldri komme seg videre og til slutt time ut
    */

    @Test
    fun `en auu som strekker seg utover arbeidsgiverperioden`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(17.januar, 25.januar), Søknad.Søknadsperiode.Permisjon(26.januar, 31.januar))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        val utoverAgp = inspektør.arbeidsgiverperioden(1.vedtaksperiode)!!.sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(inspektør.sykdomstidslinje)
        assertEquals("FFFFF FFFFPPP PPP", utoverAgp.toShortString())
    }

    @Test
    fun `flere auuer hvor siste strekker seg utover arbeidsgiverperioden`() {
        val periode1 = 1.januar til 5.januar
        val periode2 = 10.januar til 14.januar
        val periode3 = 20.januar til 31.januar
        håndterSøknad(Sykdom(periode1.start, periode1.endInclusive, 100.prosent))
        håndterSøknad(Sykdom(periode2.start, periode2.endInclusive, 100.prosent))
        håndterSøknad(Sykdom(periode3.start, periode3.endInclusive, 100.prosent), Søknad.Søknadsperiode.Ferie(26.januar, 31.januar))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(Sykdomstidslinje(), inspektør.arbeidsgiverperioden(1.vedtaksperiode)!!.sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(inspektør.sykdomstidslinje.subset(periode1)))
        assertEquals(Sykdomstidslinje(), inspektør.arbeidsgiverperioden(2.vedtaksperiode)!!.sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(inspektør.sykdomstidslinje.subset(periode2)))
        assertEquals("FFF FFF", inspektør.arbeidsgiverperioden(3.vedtaksperiode)!!.sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(inspektør.sykdomstidslinje.subset(periode3)).toShortString())
    }

    @Test
    fun `en auu som strekker seg utover arbeidsgiverperioden kun med helg`() {
        håndterSøknad(Sykdom(4.januar, søndag(21.januar), 100.prosent))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(4.januar til fredag(19.januar), inspektør.arbeidsgiverperiode(1.vedtaksperiode))
        val utoverAgp = inspektør.arbeidsgiverperioden(1.vedtaksperiode)!!.sykdomstidslinjeSomStrekkerSegUtoverArbeidsgiverperioden(inspektør.sykdomstidslinje)
        assertEquals(Sykdomstidslinje(), utoverAgp)
    }

    @Test
    fun `kort periode blokkerer neste periode i ny arbeidsgiverperiode`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 10.januar))
        håndterSøknad(Sykdom(3.januar, 10.januar, 100.prosent))
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
        )

        håndterSykmelding(Sykmeldingsperiode(3.mars, 26.mars))
        håndterInntektsmelding(listOf(Periode(3.mars, 18.mars)))
        håndterSøknad(Sykdom(3.mars, 26.mars, 100.prosent))
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    /*
        Denne testen er en slags følgefeil av testen over. Det at periode #2 er kort og får inntektsmeldingen lurer oss ut av UFERDIG-løpet og lar oss
        fortsette behandling. Dessverre setter vi oss fast i AVVENTER_HISTORIKK fordi periode #1 blokkerer utførelsen i Vedtaksperiode.forsøkUtbetaling(..)
     */
    @Test
    fun `kort periode setter senere periode fast i AVVENTER_HISTORIKK`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 10.januar))
        håndterSøknad(Sykdom(3.januar, 10.januar, 100.prosent))
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVSLUTTET_UTEN_UTBETALING,
        )

        håndterSykmelding(Sykmeldingsperiode(3.mars, 7.mars))
        håndterSøknad(Sykdom(3.mars, 7.mars, 100.prosent))
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)

        håndterSykmelding(Sykmeldingsperiode(8.mars, 26.mars))
        håndterInntektsmelding(listOf(Periode(3.mars, 18.mars)))

        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)

        håndterSøknad(Sykdom(8.mars, 26.mars, 100.prosent))
        håndterVilkårsgrunnlag(3.vedtaksperiode, INNTEKT)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertTilstander(
            3.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `arbeidsgiverperiode med brudd i helg`() {
        håndterSøknad(Sykdom(4.januar, 5.januar, 100.prosent))
        håndterSøknad(Sykdom(8.januar, 12.januar, 100.prosent))
        håndterSøknad(Sykdom(13.januar, 19.januar, 100.prosent))
        håndterSøknad(Sykdom(20.januar, 1.februar, 100.prosent))

        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(4.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)

        nullstillTilstandsendringer()
        // 6. og 7. januar blir FriskHelg og medfører brudd i arbeidsgiverperioden
        // og dermed ble også skjæringstidspunktet forskjøvet til 8. januar
        håndterInntektsmelding(
            listOf(
                1.januar til 3.januar, //3
                4.januar til 5.januar, // 2
                // 6. og 7. januar er helg
                8.januar til 12.januar,// 5
                13.januar til 18.januar // 6
            ),
            8.januar,
        )
        assertTilstander(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(4.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)
    }
}
