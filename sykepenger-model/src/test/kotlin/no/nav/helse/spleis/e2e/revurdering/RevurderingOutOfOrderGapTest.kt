package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import no.nav.helse.april
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype.Feriedag
import no.nav.helse.hendelser.Dagtype.Sykedag
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.IdInnhenter
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
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Overlapper med foreldrepenger`
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OO_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_10
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_13
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertIngenInfo
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertUtbetalingsbeløp
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.finnSkjæringstidspunkt
import no.nav.helse.spleis.e2e.forlengTilGodkjenning
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.forlengelseTilGodkjenning
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterInntektsmeldingPortal
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.spleis.e2e.tilGodkjent
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Endringskode.UEND
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class RevurderingOutOfOrderGapTest : AbstractEndToEndTest() {

    @Test
    fun `Arbeidsgiver med kort gap mellom sykefravær blir forsøkt sklitaklet av annen arbeidsgiver som tetter gapet og flytter skjæringstidspunktet`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmeldingPortal(listOf(1.januar til 16.januar), inntektsdato = 1.januar, orgnummer = a1)
        håndterVilkårsgrunnlagMedGhost(1.vedtaksperiode, skjæringstidspunkt = 1.januar, arbeidsgiver = a1, ghost = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        håndterSøknad(Sykdom(25.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmeldingPortal(emptyList(), førsteFraværsdag = 25.januar, inntektsdato = 25.januar, orgnummer = a1, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        håndterVilkårsgrunnlagMedGhost(2.vedtaksperiode, skjæringstidspunkt = 25.januar, arbeidsgiver = a1, ghost = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmeldingPortal(listOf(1.januar til 16.januar), inntektsdato = 1.januar, orgnummer = a2, vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
    }

    @Test
    fun `out of order med utbetaling i arbeidsgiverperioden og overlapp med andre ytelser`() {
        nyttVedtak(1.januar til 25.januar)
        nyttVedtak(februar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterSøknad(Sykdom(26.januar, 31.januar, 100.prosent))
        håndterYtelser(3.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(26.januar til 31.januar, 100)))
        assertVarsel(`Overlapper med foreldrepenger`, 3.vedtaksperiode.filter())
        assertTilstander(3.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `out of order med nyere periode til godkjenning revurdering`() {
        nyttVedtak(mars)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(30.mars, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        nullstillTilstandsendringer()
        nyPeriode(januar)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
    }

    @Test
    fun `out of order periode i helg mellom to andre perioder`() {
        nyttVedtak(1.januar til 26.januar)
        forlengVedtak(29.januar til 11.februar)
        nullstillTilstandsendringer()
        nyPeriode(27.januar til 28.januar)
        håndterYtelser(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        val andreUtbetaling = inspektør.utbetaling(1)
        val outOfOrderUtbetaling = inspektør.utbetaling(2)
        val revurderingutbetaling = inspektør.utbetaling(3)

        assertEquals(andreUtbetaling.korrelasjonsId, outOfOrderUtbetaling.korrelasjonsId)
        assertEquals(revurderingutbetaling.korrelasjonsId, outOfOrderUtbetaling.korrelasjonsId)
        val arbeidsgiverOppdrag = outOfOrderUtbetaling.arbeidsgiverOppdrag
        assertEquals(1, arbeidsgiverOppdrag.size)
        arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(UEND, linje.endringskode)
            assertEquals(17.januar, linje.fom)
            assertEquals(9.februar, linje.tom)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
            assertNull(linje.datoStatusFom)
        }
        assertEquals(1, revurderingutbetaling.arbeidsgiverOppdrag.size)
        assertEquals(0, revurderingutbetaling.personOppdrag.size)
        revurderingutbetaling.arbeidsgiverOppdrag.single().inspektør.also { linje ->
            assertEquals(UEND, linje.endringskode)
            assertEquals(17.januar, linje.fom)
            assertEquals(9.februar, linje.tom)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
            assertNull(linje.datoStatusFom)
        }

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET)
    }

    @Test
    fun `hører til samme arbeidsgiverperiode som forrige - har en fremtidig utbetaling`() {
        nyttVedtak(januar)
        nyttVedtak(april, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        nyPeriode(10.februar til 28.februar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), 10.februar, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_GODKJENNING)

        val utbetaling1 = inspektør.utbetaling(0)

        inspektør.utbetaling(2).also { inspektør ->
            assertEquals(inspektør.korrelasjonsId, utbetaling1.korrelasjonsId)
            assertEquals(inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), utbetaling1.arbeidsgiverOppdrag.inspektør.fagsystemId())
            assertEquals(2, inspektør.arbeidsgiverOppdrag.size)
            assertEquals(UEND, inspektør.arbeidsgiverOppdrag[0].inspektør.endringskode)
            assertEquals(17.januar, inspektør.arbeidsgiverOppdrag[0].inspektør.fom)
            assertEquals(31.januar, inspektør.arbeidsgiverOppdrag[0].inspektør.tom)
            assertEquals(NY, inspektør.arbeidsgiverOppdrag[1].inspektør.endringskode)
            assertEquals(10.februar, inspektør.arbeidsgiverOppdrag[1].inspektør.fom)
            assertEquals(28.februar, inspektør.arbeidsgiverOppdrag[1].inspektør.tom)
        }
    }

    @Test
    fun `out-of-order søknad medfører revurdering -- AvventerVilkårsprøving`() {
        nyPeriode(1.mars til 10.mars)
        håndterInntektsmelding(arbeidsgiverperioder = listOf(20.februar til 7.mars), førsteFraværsdag = 20.februar)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)

        nyPeriode(1.januar til 18.januar)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertUtbetalingsbeløp(
            1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1431,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 5.februar til 10.februar
        )
    }

    @Test
    fun `out of order periode med kort gap - utbetalingen på revurderingen får korrekt beløp`() {
        nyttVedtak(februar)
        nyttVedtak(1.januar til 25.januar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        assertEquals(3, inspektør.antallUtbetalinger)
        val nettoBeløpForFebruarMedStandardInntektOgAgp = inspektør.utbetaling(0).nettobeløp
        val antallSykedagerIFebruar2018 = 20
        val nettoBeløpForFebruarMedStandardInntektUtenAgp = 1431 * antallSykedagerIFebruar2018
        val revurdering = inspektør.sisteUtbetaling()
        assertEquals(nettoBeløpForFebruarMedStandardInntektUtenAgp -nettoBeløpForFebruarMedStandardInntektOgAgp, revurdering.nettobeløp)
    }

    @Test
    fun `out of order periode med langt gap - utbetalingen på revurderingen får korrekt beløp`() {
        nyttVedtak(mars)
        nyttVedtak(1.januar til 25.januar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        assertEquals(3, inspektør.antallUtbetalinger)
        val revurdering = inspektør.sisteUtbetaling()
        assertEquals(0, revurdering.nettobeløp)
    }

    @Test
    fun `out of order periode med 18 dagers gap - revurderingen er uten endringer`() {
        nyttVedtak(19.februar til 15.mars)
        nyttVedtak(januar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        assertEquals(3, inspektør.antallUtbetalinger)
        val førsteUtbetaling = inspektør.utbetaling(0)
        val andreUtbetaling = inspektør.utbetaling(1)
        val revurdering = inspektør.utbetaling(2)

        assertNotEquals(førsteUtbetaling.korrelasjonsId, andreUtbetaling.korrelasjonsId)
        assertEquals(førsteUtbetaling.korrelasjonsId, revurdering.korrelasjonsId)
        assertEquals(0, revurdering.nettobeløp)
        assertEquals(UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
    }

    @Test
    fun `out of order periode med 15 dagers gap - mellom to perioder`() {
        nyPeriode(1.januar til 15.januar)
        nyttVedtak(29.januar til 15.februar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nyPeriode(17.januar til 25.januar)

        håndterInntektsmelding(listOf(1.januar til 15.januar, 17.januar til 17.januar), vedtaksperiodeIdInnhenter = 3.vedtaksperiode)

        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        inspektør.utbetaling(0).also { førsteVedtak ->
            førsteVedtak.arbeidsgiverOppdrag.also { oppdraget ->
                assertEquals(1, oppdraget.size)
                assertEquals(NY, oppdraget.inspektør.endringskode)
                oppdraget.single().inspektør.also { linje1 ->
                    assertEquals(30.januar, linje1.fom)
                    assertEquals(15.februar, linje1.tom)
                    assertEquals(1431, linje1.beløp)
                    assertEquals(NY, linje1.endringskode)
                    assertEquals(1, linje1.delytelseId)
                }
            }
        }
        inspektør.utbetaling(1).also { outOfOrderUtbetalingen ->
            assertEquals(Utbetalingtype.UTBETALING, outOfOrderUtbetalingen.type)
            outOfOrderUtbetalingen.arbeidsgiverOppdrag.also { oppdraget ->
                assertEquals(ENDR, oppdraget.inspektør.endringskode)
                assertEquals(2, oppdraget.size)
                oppdraget[0].inspektør.also { linje1 ->
                    assertEquals(18.januar, linje1.fom)
                    assertEquals(25.januar, linje1.tom)
                    assertEquals(1431, linje1.beløp)
                    assertEquals(2, linje1.delytelseId)
                    assertEquals(1, linje1.refDelytelseId)
                    assertEquals(NY, linje1.endringskode)
                }
                oppdraget[1].inspektør.also { linje2 ->
                    assertEquals(30.januar, linje2.fom)
                    assertEquals(15.februar, linje2.tom)
                    assertEquals(1431, linje2.beløp)
                    assertEquals(3, linje2.delytelseId)
                    assertEquals(2, linje2.refDelytelseId)
                    assertEquals(NY, linje2.endringskode)
                }
            }
        }
        inspektør.utbetaling(2).also { revurderingen ->
            assertEquals(Utbetalingtype.REVURDERING, revurderingen.type)
            revurderingen.arbeidsgiverOppdrag.also { oppdraget ->
                assertEquals(ENDR, oppdraget.inspektør.endringskode)
                assertEquals(2, oppdraget.size)
                oppdraget[0].inspektør.also { linje1 ->
                    assertEquals(18.januar, linje1.fom)
                    assertEquals(25.januar, linje1.tom)
                    assertEquals(1431, linje1.beløp)
                    assertEquals(2, linje1.delytelseId)
                    assertEquals(1, linje1.refDelytelseId)
                    assertEquals(UEND, linje1.endringskode)
                }
                oppdraget[1].inspektør.also { linje2 ->
                    assertEquals(29.januar, linje2.fom)
                    assertEquals(15.februar, linje2.tom)
                    assertEquals(1431, linje2.beløp)
                    assertEquals(4, linje2.delytelseId)
                    assertEquals(3, linje2.refDelytelseId)
                    assertEquals(NY, linje2.endringskode)
                }
            }
        }

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `out of order periode rett før - mellom to perioder - arbeidsgiverperioden slutter tidligere`() {
        nyPeriode(1.januar til 15.januar)
        nyPeriode(29.januar til 15.februar)
        håndterInntektsmelding(listOf(1.januar til 15.januar, 29.januar til 29.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        assertEquals(1.januar til 15.januar, inspektør.periode(1.vedtaksperiode))
        assertEquals(16.januar til 15.februar, inspektør.periode(2.vedtaksperiode))

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        nyPeriode(17.januar til 28.januar)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `out of order periode rett før - mellom to perioder - arbeidsgiverperioden var ferdig`() {
        nyPeriode(1.januar til 16.januar)

        nyPeriode(29.januar til 15.februar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 29.januar, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        nullstillTilstandsendringer()
        nyPeriode(17.januar til 28.januar)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(
            3.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertEquals(3, inspektør.antallUtbetalinger)
        val februarUtbetaling = inspektør.utbetaling(0)
        val januarUtbetaling = inspektør.utbetaling(1)
        val februarRevurderingUtbetaling = inspektør.utbetaling(2)
        assertEquals(februarUtbetaling.korrelasjonsId, januarUtbetaling.korrelasjonsId)
        assertEquals(1, januarUtbetaling.arbeidsgiverOppdrag.size)
        januarUtbetaling.arbeidsgiverOppdrag[0].also { linje ->
            assertEquals(17.januar til 15.februar, linje.fom til linje.tom)
            assertEquals(1431, linje.beløp)
        }
        assertEquals(1, februarRevurderingUtbetaling.arbeidsgiverOppdrag.size)
        februarRevurderingUtbetaling.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 15.februar, linje.fom til linje.tom)
            assertEquals(1431, linje.beløp)
            assertEquals(UEND, linje.endringskode)
        }
    }

    @Test
    fun `out of order periode trigger revurdering`() {
        nyttVedtak(mai)
        forlengVedtak(juni)
        nullstillTilstandsendringer()
        nyttVedtak(januar, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `kort out of order-periode medfører at senere kort periode skal omgjøres`() {
        nyPeriode(16.januar til 30.januar)
        nullstillTilstandsendringer()
        nyPeriode(1.januar til 15.januar)
        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_INNTEKTSMELDING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `out of order periode uten utbetaling trigger revurdering`() {
        nyttVedtak(mai)
        forlengVedtak(juni)
        nullstillTilstandsendringer()
        nyPeriode(1.januar til 15.januar)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `out of order periode uten utbetaling trigger revurdering -- flere ag`() {
        nyeVedtak(mai, a1, a2)
        nullstillTilstandsendringer()
        nyPeriode(1.januar til 15.januar, a1)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `Burde revurdere utbetalt periode dersom det kommer en eldre periode fra en annen AG`() {
        nyttVedtak(mars, orgnummer = a2)
        nyPeriode(januar, a1)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a1)
    }

    @Test
    fun `out of order som overlapper med eksisterende -- flere ag`() {
        nyttVedtak(mars, orgnummer = a2)
        nyPeriode(20.februar til 15.mars, a1)
        håndterInntektsmelding(listOf(20.februar til 7.mars), orgnummer = a1,)
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntektperioderForSykepengegrunnlag {
                1.november(2017) til 1.januar(2018) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }, arbeidsforhold = listOf()),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT),
                Arbeidsforhold(a2, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertEquals(1, inspektør(a1).antallUtbetalinger)
        assertEquals(2, inspektør(a2).antallUtbetalinger)
        val a1Utbetaling = inspektør(a1).utbetaling(0)
        val a2FørsteUtbetaling = inspektør(a2).utbetaling(0)
        val a2AndreUtbetaling = inspektør(a2).utbetaling(1)

        assertEquals(a1Utbetaling.tilstand, Utbetalingstatus.UTBETALT)
        assertEquals(a2FørsteUtbetaling.tilstand, Utbetalingstatus.UTBETALT)
        assertEquals(a2AndreUtbetaling.tilstand, Utbetalingstatus.UTBETALT)
        assertEquals(a2FørsteUtbetaling.korrelasjonsId, a2AndreUtbetaling.korrelasjonsId)
        a2FørsteUtbetaling.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(1, oppdrag.size)
            val linje = oppdrag.single().inspektør
            assertEquals(17.mars, linje.fom)
            assertEquals(30.mars, linje.tom)
            assertEquals(1431, linje.beløp)
        }
        a2AndreUtbetaling.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(1, oppdrag.size)
            val linje = oppdrag.single().inspektør
            assertEquals(17.mars, linje.fom)
            assertEquals(30.mars, linje.tom)
            assertEquals(1080, linje.beløp)
        }
        a1Utbetaling.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(1, oppdrag.size)
            val linje = oppdrag.single().inspektør
            assertEquals(8.mars, linje.fom)
            assertEquals(15.mars, linje.tom)
            assertEquals(1080, linje.beløp)
        }

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
    }

    @Test
    fun `out of order som overlapper med eksisterende`() {
        nyttVedtak(mars, orgnummer = a1)
        nyPeriode(20.februar til 15.mars, a1)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `revurdering av senere frittstående periode hos ag3 mens overlappende out of order hos ag1 og ag2 utbetales`() {
        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april), orgnummer = a1)
        håndterSøknad(Sykdom(1.april, 30.april, 100.prosent), orgnummer = a1)
        val inntekt = 20000.månedlig
        håndterInntektsmelding(listOf(1.april til 16.april), beregnetInntekt = inntekt, orgnummer = a1,)
        val sykepengegrunnlag = InntektForSykepengegrunnlag(
            listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a3, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3))
            ), emptyList()
        )
        val arbeidsforhold = listOf(
            Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a3, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = sykepengegrunnlag,
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()


        nyPeriode(februar, a2)
        nyPeriode(februar, a3)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a3)

        håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = inntekt, orgnummer = a2,)
        håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = inntekt, orgnummer = a3,)

        val sykepengegrunnlag2 = InntektForSykepengegrunnlag(
            listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a2, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a3, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), inntekt.repeat(3))
            ), emptyList()
        )
        val arbeidsforhold2 = listOf(
            Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a3, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = sykepengegrunnlag2,
            arbeidsforhold = arbeidsforhold2,
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a3)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a3)
    }

    @Test
    fun `revurdering av senere frittstående periode hos ag3 mens overlappende out of order hos ag1 og ag2 utbetales -- forlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april), orgnummer = a1)
        håndterSøknad(Sykdom(1.april, 30.april, 100.prosent), orgnummer = a1)
        val inntekt = 20000.månedlig
        håndterInntektsmelding(listOf(1.april til 16.april), beregnetInntekt = inntekt, orgnummer = a1,)

        val sykepengegrunnlag = InntektForSykepengegrunnlag(
            listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a3, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3))
            ), emptyList()
        )
        val arbeidsforhold = listOf(
            Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a3, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = sykepengegrunnlag,
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        nyPeriode(februar, a2)
        nyPeriode(februar, a3)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a3)

        håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = inntekt, orgnummer = a2,)
        håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = inntekt, orgnummer = a3,)

        val sykepengegrunnlag2 = InntektForSykepengegrunnlag(
            listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a2, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), inntekt.repeat(3)),
                grunnlag(a3, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), inntekt.repeat(3))
            ), emptyList()
        )
        val arbeidsforhold2 = listOf(
            Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Arbeidsforhold(a3, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = sykepengegrunnlag2,
            arbeidsforhold = arbeidsforhold2,
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        håndterSimulering(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(orgnummer = a3)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a3)

        forlengelseTilGodkjenning(1.mars til 15.mars, a2, a3)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a3)
        assertSisteTilstand(2.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a3)
    }

    @Test
    fun `out of order periode mens senere periode revurderes til utbetaling`() {
        nyttVedtak(mai)
        forlengTilGodkjenning(juni)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)

        nullstillTilstandsendringer()
        nyPeriode(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 3.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE)

        nullstillTilstandsendringer()
        håndterUtbetalt()

        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET)
        assertNotNull(observatør.avsluttetMedVedtakEvent[2.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `første periode i til utbetaling når det dukker opp en out of order-periode`() {
        tilGodkjent(mars, 100.prosent, 1.mars)
        nullstillTilstandsendringer()
        nyPeriode(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
        assertNotNull(observatør.avsluttetMedVedtakEvent[1.vedtaksperiode.id(ORGNUMMER)])

        nullstillTilstandsendringer()

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        assertTilstander(
            2.vedtaksperiode,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
    }

    @Test
    fun `første periode i til utbetaling når det dukker opp en out of order-periode - utbetalingen feiler`() {
        tilGodkjent(mars, 100.prosent, 1.mars)
        nyPeriode(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalt(status = Oppdragstatus.AVVIST)

        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertNull(observatør.avsluttetMedVedtakEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `kort periode, lang periode kommer out of order - kort periode trenger ikke å sendes til saksbehandler`() {
        nyPeriode(1.mars til 16.mars)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        nyPeriode(januar)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

        assertIngenInfo("Revurdering førte til at sykefraværstilfellet trenger inntektsmelding", 1.vedtaksperiode.filter())

        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `kort periode, lang periode kommer out of order og fører til utbetaling på kort periode som nå trenger IM`() {
        nyPeriode(1.mars til 16.mars)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        nyPeriode(1.februar til 25.februar)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        håndterInntektsmelding(listOf(1.februar til 16.februar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterInntektsmelding(listOf(1.februar til 16.februar), førsteFraværsdag = 1.mars, vedtaksperiodeIdInnhenter = 1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

        håndterYtelser(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `out-of-order med error skal ikke medføre revurdering`() {
        nyttVedtak(mars)
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), andreInntektskilder = true)
        assertFunksjonellFeil(RV_SØ_10.varseltekst, 2.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `out-of-order som fører til nådd maksdato skal avslå riktige dager`() {
        createKorttidsPerson(UNG_PERSON_FNR_2018, 1.januar(1992), maksSykedager = 16)

        nyttVedtak(1.januar til 30.januar)
        assertEquals(6, inspektør.sisteMaksdato(1.vedtaksperiode).gjenståendeDager)

        nyttVedtak(1.mai til 24.mai, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        assertEquals(0, inspektør.sisteMaksdato(2.vedtaksperiode).gjenståendeDager)

        nyttVedtak(1.mars til 26.mars, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        //Når out-of-order perioden for mars kommer inn, så er det dager i mai som skal bli avvist pga maksdato
        assertEquals(0, inspektør.sisteMaksdato(3.vedtaksperiode).gjenståendeDager)
        assertEquals(0, inspektør.utbetalingstidslinjer(3.vedtaksperiode).inspektør.avvistDagTeller)
        assertEquals(6, inspektør.utbetalingstidslinjer(2.vedtaksperiode).inspektør.avvistDagTeller)
    }

    @Test
    fun `Warning ved out-of-order - én warning for perioden som trigger out-of-order, én warning for de som blir påvirket av out-of-order`() {
        nyttVedtak(mars)
        forlengVedtak(april)
        forlengVedtak(mai)

        nyttVedtak(januar, vedtaksperiodeIdInnhenter = 4.vedtaksperiode)

        assertVarsel(RV_OO_1, 4.vedtaksperiode.filter())
    }

    @Test
    fun `Warning ved out-of-order - dukker ikke opp i revurderinger som ikke er out-of-order`() {
        nyttVedtak(mars)
        forlengVedtak(april)
        forlengVedtak(mai)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mars, Sykedag, 50)))

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_REVURDERING)

        assertIngenVarsel(RV_OO_1, 1.vedtaksperiode.filter())
        assertIngenVarsel(RV_OO_1, 2.vedtaksperiode.filter())
        assertIngenVarsel(RV_OO_1, 3.vedtaksperiode.filter())
    }

    @Test
    fun `Out of order kastes ut når det finnes en forkastet periode senere i tid`() {
        tilGodkjenning(1.februar til 25.februar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, false)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)

        nyPeriode(1.januar til 25.januar)
        assertSisteTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `Korte perioder skal ikke revurderes dersom de forblir innenfor AGP`() {
        nyPeriode(1.mars til 10.mars)
        nyPeriode(11.mars til 16.mars)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        nyttVedtak(januar, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        assertVarsel(RV_OO_1, 3.vedtaksperiode.filter())
        assertIngenVarsel(RV_OO_1, 2.vedtaksperiode.filter())
        assertIngenVarsel(RV_OO_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Out of order gjør at AUU revurderes fordi de ikke lenger er innen AGP - ber om inntektsmelding`() {
        nyPeriode(1.mars til 10.mars)
        nyPeriode(11.mars til 16.mars)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        nyttVedtak(1.februar til 25.februar, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterInntektsmelding(listOf(1.februar til 16.februar), førsteFraværsdag = 1.mars,)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

        håndterYtelser(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `Out of order som overlapper med annen AG og flytter skjæringstidspunktet - nærmere enn 18 dager fra neste - da gjenbruker vi tidsnære opplysninger`() {
        nyPeriode(mars, a1)
        nyPeriode(20.mars til 20.april, a2)

        håndterInntektsmelding(listOf(1.mars til 16.mars), orgnummer = a1,)
        håndterInntektsmelding(listOf(20.mars til 4.april), orgnummer = a2,)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                listOf(
                    grunnlag(a1, 1.mars, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.mars, INNTEKT.repeat(3))
                ), arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        // siden perioden slutter på en fredag starter ikke oppholdstelling i arbeidsgiverperioden før mandagen.
        // 10.februar-2.mars hører derfor til samme arbeidsgiverperioden som 20.mars-4.april, ettersom avstanden mellom
        // 5.mars (påfølgende mandag)-20.mars er akkurat 16 dager
        nyPeriode(10.februar til 2.mars, a2)

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING, a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, a1)
    }

    @Test
    fun `to perioder på rad kommer out of order - skal revuderes i riktig rekkefølge`() {
        val marsId = 1.vedtaksperiode
        nyttVedtak(mars)
        assertSisteTilstand(marsId, AVSLUTTET)

        nyPeriode(februar)
        håndterInntektsmelding(listOf(1.februar til 16.februar),)
        val februarId = 2.vedtaksperiode
        håndterVilkårsgrunnlag(februarId)
        håndterYtelser(februarId)
        håndterSimulering(februarId)
        håndterUtbetalingsgodkjenning(februarId)
        håndterUtbetalt()

        håndterYtelser(marsId)
        håndterSimulering(marsId)
        assertSisteTilstand(marsId, AVVENTER_GODKJENNING_REVURDERING)
        assertSisteTilstand(februarId, AVSLUTTET)


        nyPeriode(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        val januarId = 3.vedtaksperiode
        håndterVilkårsgrunnlag(januarId)
        håndterYtelser(januarId)
        håndterSimulering(januarId)
        håndterUtbetalingsgodkjenning(januarId)
        håndterUtbetalt()

        assertSisteTilstand(januarId, AVSLUTTET)
        assertSisteTilstand(februarId, AVVENTER_HISTORIKK_REVURDERING)
        assertSisteTilstand(marsId, AVVENTER_REVURDERING)
    }

    @Test
    fun `out-of-order søknad medfører revurdering`() {
        nyttVedtak(februar)
        nyPeriode(januar)

        val februarId = 1.vedtaksperiode
        val januarId = 2.vedtaksperiode

        assertSisteTilstand(februarId, AVVENTER_REVURDERING)
        assertSisteTilstand(januarId, AVVENTER_VILKÅRSPRØVING)

        håndterVilkårsgrunnlag(januarId)
        håndterYtelser(januarId)
        håndterSimulering(januarId)
        håndterUtbetalingsgodkjenning(januarId)
        håndterUtbetalt()

        assertSisteTilstand(februarId, AVVENTER_HISTORIKK_REVURDERING)
        assertSisteTilstand(januarId, AVSLUTTET)
        håndterYtelser(februarId)
        håndterSimulering(februarId)
        håndterUtbetalingsgodkjenning(februarId)
        håndterUtbetalt()
        assertEquals(1.januar, inspektør.skjæringstidspunkt(februarId))
        assertUtbetalingsbeløp(
            januarId,
            forventetArbeidsgiverbeløp = 1431,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 17.januar til 31.januar
        )
        assertUtbetalingsbeløp(
            februarId,
            forventetArbeidsgiverbeløp = 1431,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = februar
        )
    }

    @Test
    fun `en kort out-of-order søknad som flytter skjæringstidspunkt skal trigge revurdering`() {
        nyttVedtak(februar)
        nullstillTilstandsendringer()
        nyPeriode(20.januar til 31.januar)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_VILKÅRSPRØVING_REVURDERING)
    }

    @Test
    fun `out-of-order søknad medfører revurdering -- AvsluttetUtenUtbetaling`() {
        nyPeriode(1.februar til 10.februar)
        nyPeriode(januar)

        val februarId = 1.vedtaksperiode
        val januarId = 2.vedtaksperiode

        assertSisteTilstand(februarId, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(januarId, AVVENTER_INNTEKTSMELDING)

        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(januarId)
        håndterYtelser(januarId)
        håndterSimulering(januarId)
        håndterUtbetalingsgodkjenning(januarId)
        håndterUtbetalt()

        assertSisteTilstand(februarId, AVVENTER_HISTORIKK)
        assertSisteTilstand(januarId, AVSLUTTET)

        håndterYtelser(februarId)
        håndterSimulering(februarId)
        håndterUtbetalingsgodkjenning(februarId)
        håndterUtbetalt()

        assertSisteTilstand(januarId, AVSLUTTET)
        assertSisteTilstand(februarId, AVSLUTTET)
        assertUtbetalingsbeløp(
            januarId,
            forventetArbeidsgiverbeløp = 1431,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 17.januar til 31.januar
        )
        assertUtbetalingsbeløp(
            februarId,
            forventetArbeidsgiverbeløp = 1431,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = februar
        )
    }

    private fun håndterVilkårsgrunnlagMedGhost(vedtaksperiode: IdInnhenter, skjæringstidspunkt: LocalDate, arbeidsgiver: String, ghost: String) {
        håndterVilkårsgrunnlag(vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(arbeidsgiver to INNTEKT, ghost to INNTEKT),skjæringstidspunkt),
            arbeidsforhold = listOf(
                Arbeidsforhold(arbeidsgiver, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Arbeidsforhold(ghost, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = arbeidsgiver
        )
    }
}