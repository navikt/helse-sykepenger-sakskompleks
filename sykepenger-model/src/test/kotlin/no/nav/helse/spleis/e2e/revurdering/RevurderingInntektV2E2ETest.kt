package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Dokumentsporing.Companion.ider
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_1
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengTilGodkjentVedtak
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSkjønnsmessigFastsettelse
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RevurderingInntektV2E2ETest : AbstractEndToEndTest() {

    @Test
    fun `revurdere enslig periode`() {
        val forventetEndring = 200.daglig
        val overstyrtInntekt = INNTEKT + forventetEndring
        nyttVedtak(januar)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.januar, 1431.daglig, INGEN, INNTEKT)
        håndterOverstyrInntekt(overstyrtInntekt, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertDag<Sykedag, NavDag>(
            dato = 17.januar,
            arbeidsgiverbeløp = 1431.daglig,
            personbeløp = forventetEndring,
            aktuellDagsinntekt = overstyrtInntekt
        )
        assertDiff(2200)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }


    @Test
    fun `skal kunne overstyre inntekt i utkast til revurdering`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertDiff(-5588)

        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        inspektør.utbetalinger(1.vedtaksperiode).also { utbetalinger ->
            assertEquals(2, utbetalinger.size)
            assertEquals(Utbetalingstatus.FORKASTET, utbetalinger.last().inspektør.tilstand)
        }
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertDiff(-3047)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i utkast til revurdering, også når det er snakk om flere perioder`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertDiff(-5588)

        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertDiff(-3047)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i AvventerHistorikkRevurdering`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
        assertDiff(-3047)

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i AvventerSimuleringRevurdering`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
        assertDiff(-3047)

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i utkast til revurdering ved revurdering av tidslinje`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(
            dato = 22.januar,
            arbeidsgiverbeløp = 1431.daglig,
            personbeløp = INGEN,
            aktuellDagsinntekt = INNTEKT
        )

        håndterOverstyrTidslinje((20.januar til 29.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(
            dato = 22.januar,
            arbeidsgiverbeløp = INGEN,
            personbeløp = INGEN,
            aktuellDagsinntekt = INNTEKT
        )

        val overstyrtInntekt = 20000.månedlig
        håndterOverstyrInntekt(inntekt = overstyrtInntekt, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertDag<Sykedag, NavDag>(
            dato = 17.januar,
            arbeidsgiverbeløp = overstyrtInntekt.rundTilDaglig(),
            personbeløp = INGEN,
            aktuellDagsinntekt = overstyrtInntekt
        )
        assertDiff(-11126)

        assertEquals(33235, inspektør.sisteUtbetaling().arbeidsgiverOppdrag.totalbeløp())
        assertEquals("SSSSSHH SSSSSHH SSSSSFF FFFFFFF FSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNFF FFFFFFF FNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `Ved overstyring av revurdering av inntekt til under krav til minste sykepengegrunnlag skal vi opphøre den opprinnelige utbetalingen`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 50000.årlig,
        )
        val inntekter = listOf(grunnlag(ORGNUMMER, 1.januar, 50000.årlig.repeat(3)))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntekter, arbeidsforhold = emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrInntekt(46000.årlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTrue(inspektør.utbetaling(0).erUtbetalt)
        assertTrue(inspektør.utbetaling(1).erUbetalt)
        assertEquals(inspektør.utbetaling(0).arbeidsgiverOppdrag.fagsystemId, inspektør.utbetaling(1).arbeidsgiverOppdrag.fagsystemId)
        assertDiff(-2112)
    }

    @Test
    fun `revurder inntekt ukjent skjæringstidspunkt`() {
        nyttVedtak(januar, 100.prosent)
        nullstillTilstandsendringer()
        assertThrows<IllegalStateException> { håndterOverstyrInntekt(inntekt = 32000.månedlig, skjæringstidspunkt = 2.januar) }
        assertIngenFunksjonelleFeil(AktivitetsloggFilter.person())
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertEquals(1, inspektør.antallUtbetalinger)
    }

    @Test
    fun `revurder inntekt tidligere skjæringstidspunkt - med AGP imellom`() {
        nyttVedtak(januar, 100.prosent)
        nyttVedtak(mars, 100.prosent, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()

        val korrelasjonsIdPåUtbetaling1 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId

        håndterOverstyrInntekt(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )

        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING
        )

        assertEquals(3, inspektør.antallUtbetalinger)
        val korrelasjonsIdPåUtbetaling2 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId
        val korrelasjonsIdPåUtbetaling3 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.korrelasjonsId

        assertEquals(korrelasjonsIdPåUtbetaling1, korrelasjonsIdPåUtbetaling2)
        assertNotEquals(korrelasjonsIdPåUtbetaling2, korrelasjonsIdPåUtbetaling3)
        assertDiff(506)

        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        håndterYtelser(2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)

        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)

        assertEquals(4, inspektør.antallUtbetalinger)
        val korrelasjonsIdPåUtbetaling4 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId
        val korrelasjonsIdPåUtbetaling5 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.korrelasjonsId

        assertEquals(korrelasjonsIdPåUtbetaling3, korrelasjonsIdPåUtbetaling5)
        assertNotEquals(korrelasjonsIdPåUtbetaling4, korrelasjonsIdPåUtbetaling5)
        assertDiff(0)
    }

    @Test
    fun `revurder inntekt tidligere skjæringstidspunkt - med samme AGP`() {
        nyttVedtak(januar, 100.prosent)
        nyttVedtak(2.februar til 28.februar, 100.prosent, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )

        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING
        )

        assertEquals(3, inspektør.antallUtbetalinger)

        val korrelasjonsIdPåUtbetaling1 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId
        val korrelasjonsIdPåUtbetaling2 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.korrelasjonsId

        assertEquals(korrelasjonsIdPåUtbetaling1, korrelasjonsIdPåUtbetaling2)
        assertDiff(506)
        inspektør.utbetaling(2).also { revurdering ->
            val januarutbetaling = inspektør.utbetaling(0)
            val februarutbetaling = inspektør.utbetaling(1)
            assertEquals(januarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
            assertEquals(februarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)

            assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", revurdering.utbetalingstidslinje.toString().trim())
            assertEquals(januar, januarutbetaling.periode)
            assertEquals(1.januar til 28.februar, februarutbetaling.periode)
            assertEquals(januar, revurdering.periode)
            assertEquals(1, revurdering.personOppdrag.size)
            assertEquals(2, revurdering.arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            assertEquals(0, revurdering.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                assertEquals(17.januar, arbeidsgiveroppdrag.fom(0))
                assertEquals(31.januar, arbeidsgiveroppdrag.tom(0))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(0))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(0))

                assertEquals(2.februar, arbeidsgiveroppdrag.fom(1))
                assertEquals(28.februar, arbeidsgiveroppdrag.tom(1))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(1))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(1))
            }
            assertEquals(506, revurdering.personOppdrag.inspektør.nettoBeløp)
            revurdering.personOppdrag.inspektør.also { personoppdrag ->
                assertEquals(17.januar, personoppdrag.fom(0))
                assertEquals(31.januar, personoppdrag.tom(0))
                assertEquals(46, personoppdrag.beløp(0))
                assertEquals(Endringskode.NY, personoppdrag.endringskode(0))
            }
        }
    }

    @Test
    fun `revurder inntekt til under krav til minste sykepengegrunnlag`() {
        nyttVedtak(januar, 100.prosent)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 3000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        assertEquals(2, inspektør.antallUtbetalinger)
        assertDiff(-15741)

        assertVarsel(RV_SV_1, AktivitetsloggFilter.person())
        assertFalse(inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje.harUtbetalingsdager())
    }

    @Test
    fun `revurder inntekt til under krav til minste sykepengegrunnlag slik at utbetaling opphører, og så revurder igjen til over krav til minste sykepengegrunnlag`() {
        nyttVedtak(januar, 100.prosent, beregnetInntekt = 5000.månedlig)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 3000.månedlig, skjæringstidspunkt = 1.januar)
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, 3000.månedlig)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        assertEquals(2, inspektør.antallUtbetalinger)
        assertDiff(-2541)

        assertVarsel(RV_SV_1, AktivitetsloggFilter.person())
        assertFalse(inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje.harUtbetalingsdager())
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrInntekt(5000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        var opprinneligFagsystemId: String?
        inspektør.utbetaling(0).arbeidsgiverOppdrag.apply {
            assertEquals(Endringskode.NY, inspektør.endringskode)
            opprinneligFagsystemId = fagsystemId
            assertEquals(1, size)
            first().inspektør.apply {
                assertEquals(Endringskode.NY, endringskode)
                assertEquals(1, delytelseId)
                assertEquals(null, refDelytelseId)
                assertEquals(null, refFagsystemId)
            }
        }
        inspektør.utbetaling(1).arbeidsgiverOppdrag.apply {
            assertEquals(Endringskode.ENDR, inspektør.endringskode)
            assertEquals(opprinneligFagsystemId, fagsystemId)
            assertEquals(1, size)
            first().inspektør.apply {
                assertEquals(Endringskode.ENDR, endringskode)
                assertEquals(1, delytelseId)
                assertEquals(null, refDelytelseId)
                assertEquals(null, refFagsystemId)
                assertEquals(17.januar, datoStatusFom)
            }
        }
        inspektør.utbetaling(2).arbeidsgiverOppdrag.apply {
            assertEquals(Endringskode.ENDR, inspektør.endringskode)
            assertEquals(opprinneligFagsystemId, fagsystemId)
            assertEquals(1, size)
            first().inspektør.apply {
                assertEquals(Endringskode.NY, endringskode)
                assertEquals(2, delytelseId)
                assertEquals(1, refDelytelseId)
                assertEquals(fagsystemId, refFagsystemId)
            }
        }
        assertDiff(2541)
    }

    @Test
    fun `revurdering av inntekt delegeres til den første perioden som har en utbetalingstidslinje - arbeidsgiversøknad først`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.januar))
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(16.januar, 15.februar))
        håndterSøknad(Sykdom(16.januar, 15.februar, 100.prosent))
        håndterInntektsmelding(
            listOf(Periode(1.januar, 16.januar)),
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        nullstillTilstandsendringer()
        håndterOverstyrInntekt(skjæringstidspunkt = 1.januar, inntekt = 30000.månedlig)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertDiff(-1012)
    }

    @Test
    fun `revurdering av inntekt delegeres til den første perioden som har en utbetalingstidslinje - periode uten utbetaling først`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(1.januar, 31.januar))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)),)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(februar)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        nullstillTilstandsendringer()

        håndterOverstyrInntekt(skjæringstidspunkt = 1.januar, inntekt = 30000.månedlig)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(2, inspektør.antallUtbetalinger)
        assertEquals(0, inspektør.utbetalinger(1.vedtaksperiode).size)
        assertEquals(2, inspektør.utbetalinger(2.vedtaksperiode).size)
        assertEquals(Utbetalingstatus.UTBETALT, inspektør.utbetalingtilstand(0))
        assertEquals(Utbetalingstatus.UTBETALT, inspektør.utbetalingtilstand(1))
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertDiff(-920)
    }

    @Test
    fun `Perioder med aktuelt skjæringstidspunkt skal være stemplet med hendelseId`() {
        nyttVedtak(januar)
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        forlengVedtak(april)
        val overstyrInntektHendelseId = UUID.randomUUID()
        håndterOverstyrInntekt(skjæringstidspunkt = 1.mars, meldingsreferanseId = overstyrInntektHendelseId)

        inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.behandlinger.last().also { behandling ->
            assertNotEquals(overstyrInntektHendelseId, behandling.kilde.meldingsreferanseId)
            assertTrue(overstyrInntektHendelseId !in behandling.endringer.map { it.dokumentsporing }.ider())
        }
        inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.behandlinger.last().also { behandling ->
            assertEquals(overstyrInntektHendelseId, behandling.kilde.meldingsreferanseId)
            assertTrue(overstyrInntektHendelseId !in behandling.endringer.map { it.dokumentsporing }.ider())
        }
        inspektør.vedtaksperioder(3.vedtaksperiode).inspektør.behandlinger.last().also { behandling ->
            assertEquals(overstyrInntektHendelseId, behandling.kilde.meldingsreferanseId)
            assertTrue(overstyrInntektHendelseId !in behandling.endringer.map { it.dokumentsporing }.ider())
        }
    }

    @Test
    fun `revurdere inntekt slik at det blir brukerutbetaling`() {
        nyttVedtak(januar)
        håndterInntektsmelding(
            listOf(Periode(1.januar, 16.januar)),
            refusjon = Refusjon(
                INGEN,
                null,
                emptyList()
            ),
        )
        håndterOverstyrInntekt(inntekt = INNTEKT, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertDiff(0)
        assertTrue(inspektør.utbetaling(1).personOppdrag.harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).arbeidsgiverOppdrag.harUtbetalinger()) // opphører arbeidsgiveroppdraget
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).personOppdrag))
        assertEquals(17.januar, inspektør.utbetaling(1).arbeidsgiverOppdrag.first().inspektør.datoStatusFom)

        assertEquals(15741, inspektør.utbetaling(1).personOppdrag.nettoBeløp())
        assertEquals(-15741, inspektør.utbetaling(1).arbeidsgiverOppdrag.nettoBeløp())
    }

    @Test
    fun `revurdere inntekt slik at det blir delvis refusjon`() {
        nyttVedtak(januar)
        håndterInntektsmelding(
            listOf(Periode(1.januar, 16.januar)),
            refusjon = Refusjon(
                25000.månedlig,
                null,
                emptyList()
            ),
        )
        håndterOverstyrInntekt(inntekt = INNTEKT, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertDiff(0)
        assertTrue(inspektør.utbetaling(1).personOppdrag.harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).arbeidsgiverOppdrag.harUtbetalinger()) // opphører arbeidsgiveroppdraget
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).personOppdrag))
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).arbeidsgiverOppdrag))

        assertEquals(3047, inspektør.utbetaling(1).personOppdrag.nettoBeløp())
        assertEquals(-3047, inspektør.utbetaling(1).arbeidsgiverOppdrag.nettoBeløp())
    }

    @Test
    fun `revurdere mens en periode er til utbetaling`() {
        nyttVedtak(januar)
        forlengTilGodkjentVedtak(februar)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        assertEquals(1, observatør.avsluttetMedVedtakEvent.size)
        assertNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        assertEquals(2, observatør.avsluttetMedVedtakEvent.size)
        assertNotNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en periode har feilet i utbetaling`() {
        nyttVedtak(januar)
        forlengTilGodkjentVedtak(februar)
        nullstillTilstandsendringer()
        håndterUtbetalt(status = Oppdragstatus.FEIL)
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
    }


    @Test
    fun `refusjonsopplysninger er uendret etter revurdert inntekt`() {
        nyttVedtak(januar)
        val refusjonsopplysningerFørRevurdering = inspektør.refusjonsopplysningerFraVilkårsgrunnlag(1.januar)
        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        val refusjonsopplysningerEtterRevurdering = inspektør.refusjonsopplysningerFraVilkårsgrunnlag(1.januar)
        assertEquals(refusjonsopplysningerFørRevurdering, refusjonsopplysningerEtterRevurdering)
    }


    private inline fun <reified D: Dag, reified UD: Utbetalingsdag>assertDag(dato: LocalDate, arbeidsgiverbeløp: Inntekt, personbeløp: Inntekt = INGEN, aktuellDagsinntekt: Inntekt = INGEN) {
        inspektør.sykdomshistorikk.tidslinje(0)[dato].let {
            assertTrue(it is D) { "Forventet ${D::class.simpleName} men var ${it::class.simpleName}"}
        }
        inspektør.sisteUtbetalingUtbetalingstidslinje()[dato].let {
            assertTrue(it is UD) { "Forventet ${UD::class.simpleName} men var ${it::class.simpleName}"}
            assertEquals(arbeidsgiverbeløp, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(personbeløp, it.økonomi.inspektør.personbeløp)
            assertEquals(aktuellDagsinntekt, it.økonomi.inspektør.aktuellDagsinntekt)
        }
    }

    private fun assertDiff(diff: Int) {
        assertEquals(diff, inspektør.sisteUtbetaling().nettobeløp)
    }
}