package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.august
import no.nav.helse.erHelg
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mars
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.ArbeidsgiverperiodeDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.AvvistDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.Fridag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.NavDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.NavHelgDag
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterOverstyringSykedag
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellArbeidsgiverdag
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.spleis.e2e.manuellForeldrepengedag
import no.nav.helse.spleis.e2e.manuellPermisjonsdag
import no.nav.helse.spleis.e2e.manuellSykedag
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Melding
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.reflect.KClass

internal class OverstyrTidslinjeTest : AbstractEndToEndTest() {

    @Test
    fun `overstyring av tidslinje i avventer inntektsmelding`() {
        håndterSøknad(Sykdom(3.februar, 26.februar, 100.prosent))
        håndterSøknad(Sykdom(27.februar, 12.mars, 100.prosent))
        håndterSøknad(Sykdom(13.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(6.mars til 21.mars),)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(2.februar til 17.februar),)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        nullstillTilstandsendringer()

        assertEquals("UGG UUUUUGG UUUUUGR AAAAARR AAAAARR ASSSSHH SSSSSHH SSSSSHH SSSSSH", inspektør.sykdomstidslinje.toShortString())
        håndterOverstyrTidslinje((20.februar til 24.februar).map { ManuellOverskrivingDag(it, Dagtype.Sykedag, 100) })
        assertEquals("UGG UUUUUGG UUUUUGR ASSSSHR AAAAARR ASSSSHH SSSSSHH SSSSSHH SSSSSH", inspektør.sykdomstidslinje.toShortString())
        håndterOverstyrTidslinje((18.februar til 19.februar).map { ManuellOverskrivingDag(it, Dagtype.Sykedag, 100) })
        assertEquals("UGG UUUUUGG UUUUUGH SSSSSHR AAAAARR ASSSSHH SSSSSHH SSSSSHH SSSSSH", inspektør.sykdomstidslinje.toShortString())

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `overstyring av tidslinje i avventer inntektsmelding2`() {
        håndterSøknad(Sykdom(3.februar, 26.februar, 100.prosent))
        håndterSøknad(Sykdom(27.februar, 12.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.februar til 21.mars),)

    }

    @Test
    fun `arbeidsgiver endrer arbeidsgiverperioden tilbake - må overstyre tidslinje for å fikse`() {
        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent))
        nyttVedtak(11.februar, 28.februar, arbeidsgiverperiode = listOf(1.februar til 4.februar, 7.februar til 18.februar))
        assertEquals(Dag.Arbeidsdag::class, inspektør.sykdomstidslinje[5.februar]::class)
        assertEquals(Dag.Arbeidsdag::class, inspektør.sykdomstidslinje[6.februar]::class)
        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(16.januar til 31.januar),)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING)

        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(5.februar, Dagtype.Sykedag, 100),
            ManuellOverskrivingDag(6.februar, Dagtype.Sykedag, 100),
        ))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertEquals(Dag.Sykedag::class, inspektør.sykdomstidslinje[5.februar]::class)
        assertEquals(Dag.Sykedag::class, inspektør.sykdomstidslinje[6.februar]::class)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
    }

    @Test
    fun `overstyre ferie til sykdom`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterOverstyrTidslinje(
            (17.januar til 31.januar).map { dagen -> ManuellOverskrivingDag(dagen, Dagtype.Sykedag, 100) }
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        val vilkårsgrunnlaget = inspektør.vilkårsgrunnlag(1.vedtaksperiode) ?: fail { "fant ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlaget.inspektør.sykepengegrunnlag.inspektør
        val arbeidsgiverInntektsopplysning = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(ORGNUMMER).inspektør
        assertEquals(INNTEKT, arbeidsgiverInntektsopplysning.inntektsopplysning.inspektør.beløp)
        assertEquals(Inntektsmelding::class, arbeidsgiverInntektsopplysning.inntektsopplysning::class)
    }

    @Test
    fun `ferie uten sykmelding mellom to perioder`() {
        nyttVedtak(1.juni, 30.juni)

        håndterSøknad(Sykdom(1.august, 31.august, 100.prosent))
        håndterInntektsmelding(
            listOf(1.juni til 16.juni),
            førsteFraværsdag = 1.august,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FerieEllerAvspasering",
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((1.juli til 31.juli).map { ManuellOverskrivingDag(it, Dagtype.ArbeidIkkeGjenopptattDag) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertEquals("SHH SSSSSHH SSSSSHH SSSSSHH SSSSSHJ JJJJJJJ JJJJJJJ JJJJJJJ JJJJJJJ JJNSSHH SSSSSHH SSSSSHH SSSSSHH SSSSS", inspektør.sykdomstidslinje.toShortString())

        assertEquals(1.august, inspektør.skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(1.juli til 31.august, inspektør.periode(2.vedtaksperiode))

        val juniutbetaling = inspektør.utbetaling(0).inspektør
        val augustutbetalingFør = inspektør.utbetaling(1).inspektør
        val augustutbetalingEtter = inspektør.utbetaling(2).inspektør

        assertNotEquals(juniutbetaling.korrelasjonsId, augustutbetalingFør.korrelasjonsId)
        assertEquals(juniutbetaling.korrelasjonsId, augustutbetalingEtter.korrelasjonsId)

        augustutbetalingEtter.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(2, oppdrag.size)
            oppdrag[0].also { linje ->
                assertEquals(17.juni til 30.juni, linje.fom til linje.tom)
            }
            oppdrag[1].also { linje ->
                assertEquals(1.august til 31.august, linje.fom til linje.tom)
            }
        }
    }

    @Test
    fun `vedtaksperiode strekker seg tilbake og endrer ikke skjæringstidspunktet`() {
        tilGodkjenning(10.januar, 31.januar, a1)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(9.januar, Dagtype.Arbeidsdag)
        ), orgnummer = a1)

        val dagen = inspektør.sykdomstidslinje[9.januar]
        assertEquals(Dag.Arbeidsdag::class, dagen::class)
        assertTrue(dagen.kommerFra(OverstyrTidslinje::class))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertEquals(9.januar til 31.januar, inspektør.periode(1.vedtaksperiode))
        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `strekker ikke inn i forrige periode`() {
        nyPeriode(1.januar til 9.januar, a1)
        tilGodkjenning(10.januar, 31.januar, a1) // 1. jan - 9. jan blir omgjort til arbeidsdager ved innsending av IM her
        nullstillTilstandsendringer()
        // Saksbehandler korrigerer; 9.januar var vedkommende syk likevel
        assertEquals(4, inspektør.sykdomshistorikk.inspektør.elementer())
        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(9.januar, Dagtype.Arbeidsdag)
        ), orgnummer = a1)
        assertEquals(5, inspektør.sykdomshistorikk.inspektør.elementer())
        val dagen = inspektør.sykdomstidslinje[9.januar]
        assertEquals(Dag.Arbeidsdag::class, dagen::class)
        assertTrue(dagen.kommerFra(OverstyrTidslinje::class))

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)

        assertEquals(1.januar til 9.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(10.januar til 31.januar, inspektør.periode(2.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `vedtaksperiode strekker seg ikke tilbake hvis det er en periode foran`() {
        nyPeriode(1.januar til 9.januar, a1)
        nyPeriode(10.januar til 31.januar, a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(9.januar, Dagtype.Sykedag, 100)
        ), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)

        val dagen = inspektør.sykdomstidslinje[9.januar]
        assertEquals(Dag.Sykedag::class, dagen::class)
        assertTrue(dagen.kommerFra(OverstyrTidslinje::class))

        assertEquals(1.januar til 9.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(10.januar til 31.januar, inspektør.periode(2.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `overstyr tidslinje endrer to perioder samtidig`() {
        nyPeriode(1.januar til 9.januar, a1)
        nyPeriode(10.januar til 31.januar, a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        nullstillTilstandsendringer()
        assertEquals(4, inspektør.sykdomshistorikk.inspektør.elementer())
        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(9.januar, Dagtype.Sykedag, 100),
            ManuellOverskrivingDag(10.januar, Dagtype.Feriedag)
        ), orgnummer = a1)
        assertEquals(5, inspektør.sykdomshistorikk.inspektør.elementer())
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)

        assertSykdomstidslinjedag(9.januar, Dag.Sykedag::class, OverstyrTidslinje::class)
        assertSykdomstidslinjedag(10.januar, Dag.Feriedag::class, OverstyrTidslinje::class)

        assertEquals(1.januar til 9.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(10.januar til 31.januar, inspektør.periode(2.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `kan ikke utbetale overstyrt utbetaling`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 18.januar)), førsteFraværsdag = 2.januar,)
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(manuellSykedag(2.januar), manuellArbeidsgiverdag(24.januar), manuellFeriedag(25.januar)))
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        assertEquals(Utbetalingstatus.FORKASTET, inspektør.utbetalingtilstand(0))
    }

    @Test
    fun `grad over grensen overstyres på enkeltdag`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar,)
        håndterSøknad(Sykdom(2.januar, 25.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(manuellSykedag(22.januar, 30)))

        assertNotEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertEquals(3, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.size)
        assertEquals(21.januar, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[0].tom)
        assertEquals(30, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[1].grad)
        assertEquals(23.januar, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[2].fom)
    }

    @Test
    fun `grad under grensen blir ikke utbetalt etter overstyring av grad`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar,)
        håndterSøknad(Sykdom(2.januar, 25.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(manuellSykedag(22.januar, 0)))

        assertNotEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertEquals(2, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.size)
        assertEquals(21.januar, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[0].tom)
        assertEquals(23.januar, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[1].fom)
    }

    @Test
    fun `overstyrt til fridager i midten av en periode blir ikke utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 25.januar))
        håndterInntektsmelding(listOf(Periode(2.januar, 17.januar)), førsteFraværsdag = 2.januar,)
        håndterSøknad(Sykdom(2.januar, 25.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(manuellFeriedag(22.januar), manuellPermisjonsdag(23.januar)))

        assertNotEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertEquals(2, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.size)
        assertEquals(21.januar, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[0].tom)
        assertEquals(24.januar, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag[1].fom)
    }

    @Test
    fun `Overstyring oppdaterer sykdomstidlinjene`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar,)
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(manuellFeriedag(26.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        assertEquals("SSSHH SSSSSHH SSSSSHH SSSSF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        assertEquals("SSSHH SSSSSHH SSSSSHH SSSSF", inspektør.sykdomstidslinje.toShortString())
        assertEquals("PPPPP PPPPPPP PPPPNHH NNNNF", inspektør.utbetalingstidslinjer(1.vedtaksperiode).toString())
    }

    @Test
    fun `Overstyring av sykHelgDag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(20.januar, 21.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterOverstyringSykedag(20.januar til 21.januar)
        håndterYtelser(1.vedtaksperiode)

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.utbetalingstidslinjer(1.vedtaksperiode).toString())
    }

    @Test
    fun `Overstyring av utkast til revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje((20.januar til 29.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        // Denne overstyringen kommer før den forrige er ferdig prossessert
        håndterOverstyrTidslinje((30.januar til 31.januar).map { manuellFeriedag(it) })

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `skal kunne overstyre dagtype i utkast til revurdering ved revurdering av inntekt`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterOverstyrTidslinje((20.januar til 29.januar).map { manuellFeriedag(it) })
        inspektør.utbetalinger(1.vedtaksperiode).also { utbetalinger ->
            assertEquals(2, utbetalinger.size)
            assertEquals(Utbetalingstatus.FORKASTET, utbetalinger.last().inspektør.tilstand)
        }
        inspektør.utbetalinger(2.vedtaksperiode).also { utbetalinger ->
            assertEquals(1, utbetalinger.size)
            assertEquals(Utbetalingstatus.UTBETALT, utbetalinger.last().inspektør.tilstand)
        }
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertEquals(33235, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.totalbeløp())
        assertEquals("SSSSSHH SSSSSHH SSSSSFF FFFFFFF FSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNFF FFFFFFF FNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `overstyrer fra SykedagNav til Sykedag`(){
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), begrunnelseForReduksjonEllerIkkeUtbetalt = "Saerregler",)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        val agp = (1.januar til 16.januar).filterNot { it.erHelg() }
        agp.forEach {
            assertSykdomstidslinjedag(it, Dag.SykedagNav::class, "Inntektsmelding")
        }
        val førsteUtbetalingsdag = inspektør.utbetalinger[0].inspektør.arbeidsgiverOppdrag[0].fom
        assertEquals(1.januar, førsteUtbetalingsdag)
        håndterOverstyrTidslinje(agp.map { ManuellOverskrivingDag(it, Dagtype.Sykedag, 100) })
        håndterYtelser(1.vedtaksperiode)
        agp.forEach {
            assertSykdomstidslinjedag(it, Dag.Sykedag::class, OverstyrTidslinje::class)
        }
        val førsteUtbetalingsdagEtterOverstyring = inspektør.utbetalinger[1].inspektør.arbeidsgiverOppdrag[0].fom
        assertEquals(17.januar, førsteUtbetalingsdagEtterOverstyring)
    }

    @Test
    fun `overstyring av andre ytelser i halen`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje((20.januar til 31.januar).map { manuellForeldrepengedag(it) })
        assertEquals("SSSSSHH SSSSSHH SSSSSYY YYYYYYY YYY", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        håndterYtelser(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        val utbetalingstidslinje = inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør
        assertEquals(12, utbetalingstidslinje.avvistDagTeller)
    }

    @Test
    fun `overstyring av egenmeldingsdager til arbeidsdager`(){
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(10.februar, 28.februar, 100.prosent), Arbeid(20.februar, 28.februar))
        håndterInntektsmelding(listOf(10.februar til 26.februar),)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertEquals("HH SSSSSHH SUUUUGG UAA", inspektør.vedtaksperiodeSykdomstidslinje(2.vedtaksperiode).toShortString())
        håndterOverstyrTidslinje((20..26).map { ManuellOverskrivingDag(it.februar, Dagtype.Arbeidsdag) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertEquals("HH SSSSSHH SAAAARR AAA", inspektør.vedtaksperiodeSykdomstidslinje(2.vedtaksperiode).toShortString())
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
    }

    @Test
    fun `overstyring av andre ytelser i snuten`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje((1.januar til 10.januar).map { manuellForeldrepengedag(it) })
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        val forventetUtbetaling =
            (1.januar til 16.januar).associateWith { ArbeidsgiverperiodeDag } +
            (17.januar til 19.januar).associateWith { NavDag } +
            (20.januar til 21.januar).associateWith { NavHelgDag } +
            (22.januar til 26.januar).associateWith { NavDag } +
            (27.januar til 28.januar).associateWith { NavHelgDag } +
            (29.januar til 31.januar).associateWith { NavDag }

        assertEquals(forventetUtbetaling, observatør.utbetalingMedUtbetalingEventer.first().dager)

        val forventetRevurdering =
            (1.januar til 10.januar).associateWith { AvvistDag } +
            (11.januar til 26.januar).associateWith { ArbeidsgiverperiodeDag } +
            (27.januar til 28.januar).associateWith { NavHelgDag } +
            (29.januar til 31.januar).associateWith { NavDag }

        assertEquals(forventetRevurdering, observatør.utbetalingMedUtbetalingEventer.last().dager)
    }

    private fun assertSykdomstidslinjedag(dato: LocalDate, dagtype: KClass<out Dag>, kommerFra: Melding) {
        assertSykdomstidslinjedag(dato, dagtype, kommerFra.simpleName!!)
    }

    private fun assertSykdomstidslinjedag(dato: LocalDate, dagtype: KClass<out Dag>, kommerFra: String) {
        val dagen = inspektør.sykdomstidslinje[dato]
        assertEquals(dagtype, dagen::class)
        assertTrue(dagen.kommerFra(kommerFra))
    }

    private val PersonObserver.UtbetalingUtbetaltEvent.dager get() = utbetalingsdager.associateBy { it.dato }.mapValues { it.value.type }
}
