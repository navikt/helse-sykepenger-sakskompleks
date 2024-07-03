package no.nav.helse.spleis.e2e

import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.sisteBehov
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnnullerUtbetalingTest : AbstractEndToEndTest() {

    @Test
    fun `avvis hvis arbeidsgiver er ukjent`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterAnnullerUtbetaling(orgnummer = a2)
        assertTrue(person.personLogg.harFunksjonelleFeilEllerVerre(), person.personLogg.toString())
    }

    @Test
    fun `avvis hvis vi ikke finner fagsystemId`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterAnnullerUtbetaling(utbetalingId = UUID.randomUUID())
        inspektør.also {
            assertEquals(AVSLUTTET, it.sisteTilstand(1.vedtaksperiode))
        }
    }

    @Test
    fun `forkaster ikke tidligere perioder ved annullering`() {
        nyttVedtak(januar)
        nyttVedtak(mai)
        håndterAnnullerUtbetaling()
        assertEquals(1, observatør.forkastedePerioder())
        assertEquals(AVSLUTTET, observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
    }

    @Test
    fun `forkaster senere perioder ved annullering`() {
        nyttVedtak(januar)
        forlengVedtak(1.februar, 28.februar) // forlengelse
        nyttVedtak(10.mars til 31.mars) // førstegangsbehandling, men med samme agp
        håndterSykmelding(Sykmeldingsperiode(1.mai, 20.mai)) // førstegangsbehandling, ny agp
        håndterSøknad(Sykdom(1.mai, 20.mai, 100.prosent))
        håndterAnnullerUtbetaling()
        assertEquals(4, observatør.forkastedePerioder())
        assertEquals(AVSLUTTET, observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
        assertEquals(AVSLUTTET, observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
        assertEquals(AVSLUTTET, observatør.forkastet(3.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
        assertEquals(AVVENTER_INNTEKTSMELDING, observatør.forkastet(4.vedtaksperiode.id(ORGNUMMER)).gjeldendeTilstand)
    }

    @Test
    fun `annuller siste utbetaling`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        val behovTeller = person.personLogg.behov().size
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        assertIngenFunksjonelleFeil()
        val behov = person.personLogg.sisteBehov(Behovtype.Utbetaling)
        @Suppress("UNCHECKED_CAST")
        val statusForUtbetaling = (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["statuskode"]
        assertEquals("OPPH", statusForUtbetaling)
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        assertFalse(person.personLogg.harFunksjonelleFeilEllerVerre())
        assertEquals(2, inspektør.arbeidsgiverOppdrag.size)
        assertEquals(1, person.personLogg.behov().size - behovTeller)
        inspektør.arbeidsgiverOppdrag[1].inspektør.also {
            assertEquals(19.januar, it.fom(0))
            assertEquals(26.januar, it.tom(0))
            assertEquals(19.januar, it.datoStatusFom(0))
        }
        person.personLogg.behov().last().also {
            assertEquals(Behovtype.Utbetaling, it.type)
            assertNull(it.detaljer()["maksdato"])
            assertEquals("SPREF", it.detaljer()["fagområde"])
        }
    }

    @Test
    fun `Annuller flere fagsystemid for samme arbeidsgiver`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        nyttVedtak(mars, 100.prosent, 1.mars)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(2.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        sisteBehovErAnnullering(2.vedtaksperiode)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        sisteBehovErAnnullering(1.vedtaksperiode)
    }

    private fun sisteBehovErAnnullering(vedtaksperiodeIdInnhenter: IdInnhenter) {
        person.personLogg.behov().last().also {
            assertEquals(Behovtype.Utbetaling, it.type)
            assertEquals(inspektør.fagsystemId(vedtaksperiodeIdInnhenter), it.detaljer()["fagsystemId"])
            assertEquals("OPPH", it.hentLinjer()[0]["statuskode"])
        }
    }

    private fun assertIngenAnnulleringsbehov() {
        assertFalse(
            person.personLogg.behov()
                .filter { it.type == Behovtype.Utbetaling }
                .any {
                    it.hentLinjer().any { linje ->
                        linje["statuskode"] == "OPPH"
                    }
                }
        )
    }

    @Test
    fun `Annuller oppdrag som er under utbetaling feiler`() {
        tilGodkjent(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        assertTrue(hendelselogg.harFunksjonelleFeilEllerVerre())
        assertIngenAnnulleringsbehov()
    }

    @Test
    fun `Annuller av oppdrag med feilet utbetaling feiler`() {
        tilGodkjent(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterUtbetalt(status = Oppdragstatus.FEIL)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        assertTrue(hendelselogg.harFunksjonelleFeilEllerVerre())
        assertIngenAnnulleringsbehov()
    }

    @Test
    fun `Kan annullere hvis noen vedtaksperioder er til utbetaling`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        tilGodkjent(mars, 100.prosent, 1.mars)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        sisteBehovErAnnullering(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
    }

    private fun Aktivitet.Behov.hentLinjer() =
        @Suppress("UNCHECKED_CAST")
        (detaljer()["linjer"] as List<Map<String, Any>>)


    @Test
    fun `Ved feilet annulleringsutbetaling settes utbetaling til annullering feilet`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.FEIL)
        assertFalse(hendelselogg.harFunksjonelleFeilEllerVerre())
        assertEquals(Utbetalingstatus.OVERFØRT, inspektør.utbetaling(1).inspektør.tilstand)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD)
    }

    @Test
    fun `Periode som håndterer avvist annullering i TilAnnullering blir værende i TilAnnullering`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.AVVIST)
        assertFalse(person.personLogg.harFunksjonelleFeilEllerVerre())
        assertEquals(Utbetalingstatus.OVERFØRT, inspektør.utbetaling(1).inspektør.tilstand)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD)
    }

    @Test
    fun `En enkel periode som er avsluttet som blir annullert blir også satt i tilstand TilAnnullering`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        inspektør.also {
            assertEquals(AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))
        }
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        assertFalse(person.personLogg.harFunksjonelleFeilEllerVerre(), person.personLogg.toString())
        inspektør.also {
            assertEquals(TIL_INFOTRYGD, inspektør.sisteTilstand(1.vedtaksperiode))
            assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        }
    }

    @Test
    fun `Annullering av én periode fører til at alle avsluttede sammenhengende perioder blir satt i tilstand TilAnnullering`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        forlengVedtak(27.januar, 31.januar, 100.prosent)
        forlengPeriode(1.februar, 20.februar, 100.prosent)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        assertFalse(person.personLogg.harFunksjonelleFeilEllerVerre(), person.personLogg.toString())
        assertEquals(3, inspektør.utbetalinger.size)
        assertEquals(Utbetalingstatus.OVERFØRT, inspektør.utbetaling(2).inspektør.tilstand)
        assertEquals(Utbetalingtype.ANNULLERING, inspektør.utbetaling(2).inspektør.type)
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVVENTER_HISTORIKK, TIL_INFOTRYGD)
        assertEquals(Utbetalingstatus.ANNULLERT, inspektør.utbetaling(2).inspektør.tilstand)
    }

    @Test
    fun `Periode som håndterer godkjent annullering i TilAnnullering blir forkastet`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling()
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        assertFalse(person.personLogg.harFunksjonelleFeilEllerVerre(), person.personLogg.toString())
        assertForkastetPeriodeTilstander(1.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD)
    }

    @Test
    fun `Annullering av én periode fører kun til at sammehengende utbetalte perioder blir forkastet og værende i Avsluttet`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        forlengVedtak(27.januar, 30.januar, 100.prosent)
        nyttVedtak(1.mars til 20.mars, 100.prosent, 1.mars)
        val behovTeller = person.personLogg.behov().size
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalinger.last().inspektør.utbetalingId)
        assertFalse(person.personLogg.harFunksjonelleFeilEllerVerre(), person.personLogg.toString())
        assertEquals(1, person.personLogg.behov().size - behovTeller, person.personLogg.toString())
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, AVSLUTTET, TIL_INFOTRYGD)
    }

    @Test
    fun `publiserer et event ved annullering av full refusjon`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT
        )

        val annullering = observatør.annulleringer.lastOrNull()
        assertNotNull(annullering)

        val utbetalingInspektør = inspektør.utbetaling(0).inspektør
        assertEquals(utbetalingInspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), annullering.arbeidsgiverFagsystemId)
        assertEquals(utbetalingInspektør.personOppdrag.inspektør.fagsystemId(), annullering.personFagsystemId)

        assertEquals("tbd@nav.no", annullering.saksbehandlerEpost)
        assertEquals(3.januar, annullering.fom)
        assertEquals(26.januar, annullering.tom)
    }

    @Test
    fun `publiserer kun ett event ved annullering av utbetaling som strekker seg over flere vedtaksperioder med full refusjon`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        forlengVedtak(27.januar, 20.februar, 100.prosent)
        assertEquals(2, inspektør.vedtaksperiodeTeller)

        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(2.vedtaksperiode))
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT
        )

        assertEquals(TIL_INFOTRYGD, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(TIL_INFOTRYGD, inspektør.sisteTilstand(2.vedtaksperiode))

        val annulleringer = observatør.annulleringer
        assertEquals(1, annulleringer.size)
        val annullering = annulleringer.lastOrNull()
        assertNotNull(annullering)

        val utbetalingInspektør = inspektør.utbetaling(0).inspektør
        assertEquals(utbetalingInspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), annullering.arbeidsgiverFagsystemId)
        assertEquals(utbetalingInspektør.personOppdrag.inspektør.fagsystemId(), annullering.personFagsystemId)
        assertEquals(3.januar, annullering.fom)
        assertEquals(20.februar, annullering.tom)

        assertEquals("tbd@nav.no", annullering.saksbehandlerEpost)
    }

    @Test
    fun `setter datoStatusFom som fom dato i annullering hvor graden endres`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        forlengVedtak(27.januar, 20.februar, 30.prosent)
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        val utbetalingId = inspektør.utbetalingId(2.vedtaksperiode)

        håndterAnnullerUtbetaling(utbetalingId = utbetalingId)
        håndterUtbetalt(
            status = Oppdragstatus.AKSEPTERT
        )

        val annulleringer = observatør.annulleringer
        assertEquals(1, annulleringer.size)
        val annullering = annulleringer.last()
        assertEquals(3.januar, annullering.fom)
    }

    @Test
    fun `kan ikke annullere utbetalingsreferanser som ikke er siste`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        nyttVedtak(3.mars til 26.mars, 100.prosent, 3.mars)

        val utbetalingId = inspektør.utbetalingId(1.vedtaksperiode)
        håndterAnnullerUtbetaling(utbetalingId = utbetalingId)
        sisteBehovErAnnullering(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertVarsel(Varselkode.RV_RV_7, 2.vedtaksperiode.filter())
        håndterUtbetalt()
        assertEquals(1, observatør.annulleringer.size)
    }

    @Test
    fun `annulerer flere fagsystemider baklengs`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        nyttVedtak(mars, 100.prosent, 1.mars)

        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(2.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        sisteBehovErAnnullering(2.vedtaksperiode)
        assertFalse(inspektør.utbetalinger.last { it.inspektør.arbeidsgiverOppdrag.fagsystemId() == inspektør.fagsystemId(1.vedtaksperiode) }.inspektør.erAnnullering)
        assertTrue(inspektør.utbetalinger.last { it.inspektør.arbeidsgiverOppdrag.fagsystemId() == inspektør.fagsystemId(2.vedtaksperiode) }.inspektør.erAnnullering)

        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)
        sisteBehovErAnnullering(1.vedtaksperiode)
        assertTrue(inspektør.utbetalinger.last { it.inspektør.arbeidsgiverOppdrag.fagsystemId() == inspektør.fagsystemId(1.vedtaksperiode) }.inspektør.erAnnullering)
        assertTrue(inspektør.utbetalinger.last { it.inspektør.arbeidsgiverOppdrag.fagsystemId() == inspektør.fagsystemId(2.vedtaksperiode) }.inspektør.erAnnullering)
    }

    @Test
    fun `annuller over ikke utbetalt forlengelse`() {
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterSykmelding(Sykmeldingsperiode(27.januar, 31.januar))
        håndterSøknad(Sykdom(27.januar, 31.januar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, false)

        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        val annullering = inspektør.utbetaling(2)
        sisteBehovErAnnullering(1.vedtaksperiode)
        assertTrue(annullering.inspektør.erAnnullering)
        assertEquals(26.januar, annullering.inspektør.arbeidsgiverOppdrag.inspektør.periode?.endInclusive)
        assertEquals(19.januar, annullering.inspektør.arbeidsgiverOppdrag.first().inspektør.fom)
        assertEquals(26.januar, annullering.inspektør.arbeidsgiverOppdrag.last().inspektør.tom)
    }

    @Test
    fun `UtbetalingAnnullertEvent inneholder saksbehandlerident`(){
        nyttVedtak(3.januar til 26.januar, 100.prosent, 3.januar)
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT)

        assertEquals("Ola Nordmann", observatør.annulleringer.first().saksbehandlerIdent)
    }

    @Test
    fun `skal ikke forkaste utbetalte perioder, med mindre de blir annullert`() {
        // lag en periode
        nyttVedtak(januar)
        // prøv å forkast, ikke klar det
        håndterSykmelding(Sykmeldingsperiode(1.februar, 19.februar))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 20.februar))
        håndterSøknad(Sykdom(1.februar, 19.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent))

        assertTrue(inspektør.periodeErIkkeForkastet(1.vedtaksperiode))
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
        // annullér
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        håndterUtbetalt()
        // sjekk at _nå_ er den forkasta
        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
    }

    @Test
    fun `skal kunne annullere tidligere utbetaling dersom siste utbetaling er uten utbetaling`() {
        nyttVedtak(januar)
        håndterSykmelding(Sykmeldingsperiode(1.mars, 20.mars))
        håndterSøknad(Sykdom(1.mars, 20.mars, 100.prosent), Søknad.Søknadsperiode.Ferie(17.mars, 20.mars))
        håndterInntektsmelding(listOf(1.mars til 16.mars))
        håndterAnnullerUtbetaling(utbetalingId = inspektør.utbetalingId(1.vedtaksperiode))
        assertFalse(hendelselogg.harFunksjonelleFeilEllerVerre())
        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
    }

    @Test
    fun `annullering av periode medfører at låser på sykdomstidslinje blir forkastet`() {
        nyttVedtak(januar)
        håndterAnnullerUtbetaling()
        inspektør.sykdomstidslinje.inspektør.låstePerioder.also {
            assertEquals(0, it.size)
        }
    }
}
