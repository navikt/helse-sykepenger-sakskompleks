package no.nav.helse.spleis.e2e

import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.søppelbøtte
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_28
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_31
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_33
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_37
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ForkastForlengelseAvForkastetPeriodeTest : AbstractEndToEndTest() {

    @Test
    fun `forlenger med forkastet periode hos annen arbeidsgiver`() {
        (1.januar til 17.januar).forkast()
        håndterSykmelding(Sykmeldingsperiode(18.januar, 16.februar), orgnummer = a2)
        håndterSøknad(18.januar til 16.februar, orgnummer = a2)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `forlenger tidligere overlappende sykmelding`() {
        (1.januar til 10.januar).forkast()
        håndterSykmelding(Sykmeldingsperiode(9.januar, 15.januar))
        håndterSøknad(9.januar til 15.januar)
        håndterSykmelding(Sykmeldingsperiode(16.januar, 31.januar))
        håndterSøknad(16.januar til 31.januar)
        assertTrue(observatør.hendelseider(1.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertTrue(observatør.hendelseider(2.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertTrue(observatør.hendelseider(3.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 3.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `søknader som overlapper`() {
        håndterSykmelding(Sykmeldingsperiode(15.januar, 30.januar))
        håndterSøknad(15.januar til 30.januar)
        håndterSykmelding(Sykmeldingsperiode(31.januar, 15.februar))
        håndterSøknad(31.januar til 15.februar)
        håndterSykmelding(Sykmeldingsperiode(16.januar, 31.januar)) // overlapper med vedtaksperiode 1 og vedtaksperiode 2
        håndterSøknad(16.januar til 31.januar) // overlapper med vedtaksperiode 1 og vedtaksperiode 2
        håndterSykmelding(Sykmeldingsperiode(1.februar, 16.februar)) // overlapper med vedtaksperiode 2
        håndterSøknad(1.februar til 16.februar) // overlapper med vedtaksperiode 2
        håndterSykmelding(Sykmeldingsperiode(19.februar, 8.mars)) // forlenger overlappende
        håndterSøknad(19.februar til 8.mars) // forlenger overlappende
        assertEquals(5, inspektør.vedtaksperiodeTeller)
        assertTrue(observatør.hendelseider(1.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertTrue(observatør.hendelseider(2.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertTrue(observatør.hendelseider(3.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertTrue(observatør.hendelseider(4.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertTrue(observatør.hendelseider(5.vedtaksperiode.id(ORGNUMMER)).isNotEmpty())
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(4.vedtaksperiode, START, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(5.vedtaksperiode, START, TIL_INFOTRYGD)
        assertFunksjonellFeil("Overlappende søknad starter før, eller slutter etter, opprinnelig periode", 1.vedtaksperiode.filter())
    }

    @Test
    fun `ny periode etter forkastede`() {
        (1.januar til 10.januar).forkast()
        håndterSykmelding(Sykmeldingsperiode(9.januar, 15.januar))
        håndterSøknad(9.januar til 15.januar)
        håndterSykmelding(Sykmeldingsperiode(16.januar, 19.januar))
        håndterSøknad(16.januar til 19.januar)
        håndterSykmelding(Sykmeldingsperiode(10.februar, 28.februar))
        håndterSøknad(10.februar til 28.februar)
        assertTilstander(4.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `forkaster periode som er forlengelse av forkastet periode`() {
        (1.januar til 17.januar).forkast()
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)
        håndterSykmelding(Sykmeldingsperiode(18.januar, 31.januar))
        håndterSøknad(18.januar til 31.januar)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_37, 2.vedtaksperiode.filter())
    }

    @Test
    fun `forkaster periode som er forlengelse av forkastet periode over helg`() {
        (1.januar til 19.januar).forkast()
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)
        håndterSykmelding(Sykmeldingsperiode(22.januar, 31.januar))
        håndterSøknad(22.januar til 31.januar)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_37, 2.vedtaksperiode.filter())
    }

    @Test
    fun `forkaster ikke periode som har gap til forkastet periode`() {
        (1.januar til 19.januar).forkast()
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)
        håndterSykmelding(Sykmeldingsperiode(10.februar, 28.februar))
        håndterSøknad(10.februar til 28.februar)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertIngenFunksjonelleFeil(2.vedtaksperiode.filter())
    }

    @Test
    fun `fortsetter å forkaste gjentatte forlengelser av kastede perioder`() {
        (1.januar til 18.januar).forkast()
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 1.vedtaksperiode, TIL_INFOTRYGD)
        håndterSykmelding(Sykmeldingsperiode(19.januar, 31.januar))
        håndterSøknad(19.januar til 31.januar)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_37, 2.vedtaksperiode.filter())
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(februar)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 3.vedtaksperiode, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_37, 3.vedtaksperiode.filter())
        håndterSykmelding(Sykmeldingsperiode(21.mars, 21.april))
        håndterSøknad(21.mars til 21.april)
        assertSisteTilstand(4.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertIngenFunksjonelleFeil(4.vedtaksperiode.filter())
    }

    @Test
    fun `forkaster også overlappende perioder som er uferdig`(){
        nyPeriode(januar)
        forkastAlle()

        nyPeriode(mars)
        nyPeriode(1.februar til 31.mars)

        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster etterfølgende perioder som er uferdig`(){
        nyPeriode(januar)
        forkastAlle()

        nyPeriode(mars)
        nyPeriode(april)
        nyPeriode(juni)
        nyPeriode(februar)

        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(4.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(5.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster ikke etterfølgende perioder som er avsluttet`(){
        nyPeriode(januar)
        forkastAlle()

        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        forlengVedtak(april)
        nyttVedtak(juni, vedtaksperiodeIdInnhenter = 4.vedtaksperiode)

        nyPeriode(februar)

        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstand(3.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstand(4.vedtaksperiode, AVVENTER_REVURDERING)
        assertForkastetPeriodeTilstander(5.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster alle forlengelser`() {
        nyPeriode(januar)
        forkastAlle()

        nyPeriode(juni)
        nyPeriode(april)
        nyPeriode(mars)
        nyPeriode(mai)

        nyPeriode(februar)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(4.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(5.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(6.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster forlengede vedtak på tvers av arbeidsgivere - a2 overlapper med alle perioder til a1`() {
        nyPeriode(januar, a1)
        forkastAlle()

        nyPeriode(15.januar til 15.mars, a2)
        nyPeriode(mars, a1)
        nyPeriode(februar, a1)

        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(a1, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(a1, 3.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }


    @Test
    fun `forkaster forlengede og overlappende vedtak på tvers av arbeidsgivere - a2 overlapper med en periode til a1`() {
        nyPeriode(januar, a1)
        forkastAlle()

        nyPeriode(mars, a1)
        nyPeriode(10.februar til 20.februar, a2)
        nyPeriode(februar, a1)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD, orgnummer = a1)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, orgnummer = a2)
    }

    @Test
    fun `Hensyntar ikke periode på overlappende søknad som forkastes når vi vurderer forlengelse fra Infotrygd`() {
        nyPeriode(7.juni til 28.juni)
        håndterSøknad(27.mai til 15.juni)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertFunksjonellFeil("Overlappende søknad starter før, eller slutter etter, opprinnelig periode", 1.vedtaksperiode.filter())
        nyPeriode(16.juni til 16.juli)
        assertEquals(7.juni til 28.juni, inspektør.periode(1.vedtaksperiode))
        assertEquals(27.mai til 15.juni, inspektør.periode(2.vedtaksperiode))
        assertEquals(16.juni til 16.juli, inspektør.periode(3.vedtaksperiode))
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster periode dersom forlenger forkastet periode med friskmeldin`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(25.januar, 31.januar))
        person.søppelbøtte(forrigeHendelse, januar)

        nyPeriode(1.februar til 20.mars)
        assertTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `forkaster periode dersom person har vært friskmeldt mindre enn 18 dager`() {
        nyPeriode(januar)
        person.søppelbøtte(forrigeHendelse, januar)

        nyPeriode(18.februar til 20.mars)
        assertTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `person som har vært friskmeldt i 18 dager kan fortsatt behandles`() {
        nyPeriode(januar)
        person.søppelbøtte(forrigeHendelse, januar)

        nyPeriode(19.februar til 20.mars)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `person som kun har helg mellom to sykefraværstilfeller skal ikke få to funksjonelle feil når den kastes ut pga for lite gap` () {
        nyPeriode(1.januar til 19.januar)
        person.søppelbøtte(forrigeHendelse, 1.januar til 19.januar)

        nyPeriode(22.januar til 31.januar)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_37)
        assertIngenFunksjonellFeil(RV_SØ_28)
    }

    @Test
    fun `søknad som har mindre enn 20 dagers gap til en forkastet periode og overlapper med en annen forkastet periode skal kun få én funksjonell feil` () {
        nyPeriode(1.januar til 17.januar)
        nyPeriode(18.januar til 31.januar)
        person.søppelbøtte(forrigeHendelse, januar)

        nyPeriode(22.januar til 31.januar)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_33)
        assertIngenFunksjonellFeil(RV_SØ_28)
    }

    @Test
    fun `kun én error dersom søknad forlenger forkastet periode og har en forkastet periode som er senere i tid`() {
        nyPeriode(januar)
        nyPeriode(mars)
        person.søppelbøtte(forrigeHendelse, 1.januar til 31.mars)

        nyPeriode(1.februar til 17.februar)
        assertFunksjonellFeil(RV_SØ_37)
        assertIngenFunksjonellFeil(RV_SØ_31)
    }

    private fun Periode.forkast() {
        håndterSykmelding(Sykmeldingsperiode(start, endInclusive))
        håndterSøknad(this)
        person.søppelbøtte(forrigeHendelse, this)
    }
}
