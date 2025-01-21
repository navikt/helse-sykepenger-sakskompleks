package no.nav.helse.spleis.e2e.overstyring

import java.util.*
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.dsl.a3
import no.nav.helse.dsl.tilGodkjenning
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.arbeidsgiver
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.assertBeløpstidslinje
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.saksbehandler
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

internal class SkjønnsmessigFastsettelseTest : AbstractDslTest() {

    @Test
    fun `Når inntekt skjønnsfastsettes til 0 og det finnes andre arbeidsgivere i økonomi-lista`() {
        "a1" {}
        "a2" {}
        "a3" {}
        "a4" {}
        "a5" {}
        "a6" {
            tilGodkjenning(mars)
            håndterSkjønnsmessigFastsettelse(1.mars, listOf(OverstyrtArbeidsgiveropplysning("a6", INGEN)))
            assertVarsel(Varselkode.RV_SV_1, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            assertEquals(100, inspektør(1.vedtaksperiode).utbetalingstidslinje[17.mars].økonomi.inspektør.totalGrad)
        }
    }

    @Test
    fun `blir syk fra ghost etter skjønnsfastsettelse - skal ikke medføre ny skjønnsfastsettelse`() {
        a1 {
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            håndterSkjønnsmessigFastsettelse(
                1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT + 500.månedlig),
                OverstyrtArbeidsgiveropplysning(a2, INNTEKT - 500.månedlig)
            )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
        }
        a1 {
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())

            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a1 {
            val sykepengegrunnlag = inspektør.vilkårsgrunnlag(1.januar)?.inspektør?.inntektsgrunnlag?.inspektør ?: fail { "forventer vilkårsgrunnlag" }

            sykepengegrunnlag.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also { arbeidsgiverInntektsopplysning ->
                assertEquals(INNTEKT, arbeidsgiverInntektsopplysning.omregnetÅrsinntekt)
                assertNotNull(arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt)
            }
            sykepengegrunnlag.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also { arbeidsgiverInntektsopplysning ->
                assertEquals(INNTEKT, arbeidsgiverInntektsopplysning.omregnetÅrsinntekt)
                assertNotNull(arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt)
            }
        }
    }

    @Test
    fun `overskriver ikke skjønnsmessig fastsettelse om inntekt fra im utgjør mindre enn 1kr forskjell på årlig`() {
        val inntektVedNyIM = 372000.5.årlig
        a1 {
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)
            assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter())
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            håndterSkjønnsmessigFastsettelse(
                1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT + 500.månedlig),
                OverstyrtArbeidsgiveropplysning(a2, INNTEKT - 500.månedlig)
            )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektVedNyIM)
        }
        a1 {
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())

            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a1 {
            val sykepengegrunnlag = inspektør.vilkårsgrunnlag(1.januar)?.inspektør?.inntektsgrunnlag?.inspektør ?: fail { "forventer vilkårsgrunnlag" }

            sykepengegrunnlag.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also { arbeidsgiverInntektsopplysning ->
                assertEquals(INNTEKT, arbeidsgiverInntektsopplysning.omregnetÅrsinntekt)
                assertEquals(INNTEKT, arbeidsgiverInntektsopplysning.fastsattÅrsinntekt)
                assertNull(arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt)
            }
            sykepengegrunnlag.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also { arbeidsgiverInntektsopplysning ->
                assertEquals(inntektVedNyIM, arbeidsgiverInntektsopplysning.omregnetÅrsinntekt)
                assertEquals(inntektVedNyIM, arbeidsgiverInntektsopplysning.fastsattÅrsinntekt)
                assertNull(arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt)
            }
        }
    }

    @Test
    fun `endring i refusjon skal ikke endre omregnet årsinntekt`() {
        (a1 og a2).nyeVedtak(januar)

        håndterSkjønnsmessigFastsettelse(
            1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(a1, 19000.0.månedlig),
            OverstyrtArbeidsgiveropplysning(a2, 21000.0.månedlig)
        )
        )

        a1 {
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        (a1 og a2).forlengVedtak(februar)

        a1 {
            val im = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 20000.månedlig, refusjon = Refusjon(20000.månedlig, opphørsdato = 31.januar))

            val sykepengegrunnlag = inspektør.vilkårsgrunnlag(1.januar)?.inspektør?.inntektsgrunnlag?.inspektør ?: fail { "forventer vilkårsgrunnlag" }

            sykepengegrunnlag.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also { arbeidsgiverInntektsopplysning ->
                assertEquals(20000.månedlig, arbeidsgiverInntektsopplysning.omregnetÅrsinntekt)
                assertNotNull(arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt)
                assertNotEquals(im, arbeidsgiverInntektsopplysning.inntektsopplysning.inspektør.hendelseId)
            }
            sykepengegrunnlag.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also { arbeidsgiverInntektsopplysning ->
                assertEquals(20000.månedlig, arbeidsgiverInntektsopplysning.omregnetÅrsinntekt)
                assertNotNull(arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt)
            }
        }
    }

    @Test
    fun `korrigere inntekten på noe som allerede har blitt skjønnsmessig fastsatt`() {
        nyttVedtak(januar)
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertEquals(INNTEKT, inspektør.omregnetÅrsinntekt(1.januar))
        assertEquals(INNTEKT * 2, inspektør.fastsattInntekt(1.januar))
        assertEquals(INNTEKT * 2, inspektør.skjønnsfastsatt(1.vedtaksperiode)!!.inspektør.beløp)
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 3)))
        assertEquals(INNTEKT * 3, inspektør.fastsattInntekt(1.januar))
        assertEquals(INNTEKT * 3, inspektør.skjønnsfastsatt(1.vedtaksperiode)!!.inspektør.beløp)
    }

    @Test
    fun `skjønnsmessig fastsatt inntekt skal ikke ha avviksvurdering`() {
        nyttVedtak(januar)
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
        assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        val sykepengegrunnlag = inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.inntektsgrunnlag.inspektør
        assertEquals(INNTEKT, inspektør.omregnetÅrsinntekt(1.vedtaksperiode))
        assertEquals(INNTEKT * 2, inspektør.skjønnsfastsatt(1.vedtaksperiode)!!.inspektør.beløp)
        assertEquals(INNTEKT * 2, sykepengegrunnlag.beregningsgrunnlag)
        assertEquals(INNTEKT, sykepengegrunnlag.omregnetÅrsinntekt)
    }

    @Test
    fun `alle inntektene må skjønnsfastsettes ved overstyring`() {
        (a1 og a2).nyeVedtak(januar)
        a1 {
            assertThrows<IllegalStateException> {
                håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
            }
        }
    }

    @Test
    fun `saksbehandler-inntekt overstyres av en skjønnsmessig med samme beløp`() {
        nyttVedtak(januar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
        assertEquals(INNTEKT * 2, inspektør.fastsattInntekt(1.januar))
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
        assertEquals(3, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertNotNull(inspektør.skjønnsfastsatt(1.januar))
    }

    @Test
    fun `skjønnsmessig fastsettelse overstyres av en skjønnsmessig med samme beløp`() {
        nyttVedtak(januar)
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))
        assertEquals(3, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
    }

    @Test
    fun `skjønnsmessig fastsettelse overstyres av en inntektmelding med samme beløp`() {
        a1 {
            tilGodkjenning(januar)
            val inntekt = INNTEKT
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = inntekt)))
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            nullstillTilstandsendringer()
            val im = håndterInntektsmelding(listOf(1.januar til 16.januar), inntekt)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            val inntektsopplysning = inspektør.inntekt(1.januar)
            assertNotNull(inspektør.skjønnsfastsatt(1.januar))
            assertNotEquals(im, inntektsopplysning.inspektør.hendelseId)
            assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        }
    }

    @Test
    fun `korrigert IM etter skjønnsfastsettelse på flere AG`() {
        (a1 og a2 og a3).nyeVedtak(januar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a3, inntekt = INNTEKT * 3)))
        a3 { assertEquals(INNTEKT * 3, inspektør.fastsattInntekt(1.januar)) }
        håndterSkjønnsmessigFastsettelse(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2),
                OverstyrtArbeidsgiveropplysning(orgnummer = a2, inntekt = INNTEKT * 2),
                OverstyrtArbeidsgiveropplysning(orgnummer = a3, inntekt = INNTEKT * 2)
            )
        )
        a1 { assertNotNull(inspektør.skjønnsfastsatt(1.januar)) }
        a2 { assertNotNull(inspektør.skjønnsfastsatt(1.januar)) }
        a3 { assertNotNull(inspektør.skjønnsfastsatt(1.januar)) }

        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
        }

        a1 { assertEquals(INNTEKT, inspektør.fastsattInntekt(1.januar)) }
        a2 { assertEquals(20000.månedlig, inspektør.fastsattInntekt(1.januar)) }
        a3 { assertEquals(INNTEKT * 3, inspektør.fastsattInntekt(1.januar)) }
    }

    @Test
    fun `skjønnsmessig fastsettelse overstyres av en inntektmelding med ulikt beløp`() {
        nyttVedtak(januar)
        håndterSkjønnsmessigFastsettelse(
            1.januar,
            listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2))
        )
        assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        håndterInntektsmelding(listOf(1.januar til 16.januar), INNTEKT * 3)
        assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
        assertEquals(3, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertEquals(INNTEKT * 3, inspektør.fastsattInntekt(1.januar))
    }

    @Test
    fun `skjønnsmessig fastsatt - men så skulle det være etter hovedregel`() {
        a1 {
            håndterSøknad(januar)
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT * 2)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)

            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT * 2)))

            assertEquals(INNTEKT * 2, inspektør.fastsattInntekt(1.januar))
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = INNTEKT)))

            assertEquals(INNTEKT, inspektør.fastsattInntekt(1.januar))
            håndterYtelser(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
        }
    }

    @Test
    fun `Tidligere perioder revurderes mens nyere skjønnsmessig fastsettes`() {
        a1 {
            nyttVedtak(januar)
            nyPeriode(mars, a1)
            håndterInntektsmelding(listOf(1.mars til 16.mars), beregnetInntekt = INNTEKT * 2)
            håndterVilkårsgrunnlag(2.vedtaksperiode)
            nullstillTilstandsendringer()
            håndterOverstyrTidslinje(
                listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag, 100))
            )
            håndterYtelser(1.vedtaksperiode)
            assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        }
    }

    @Test
    fun `Overstyre refusjon etter skjønnsmessig fastsatt -- etter utbetalt`() {
        val inntektsmeldingInntekt = INNTEKT
        val skjønnsfastsattInntekt = INNTEKT * 2

        a1 {
            // Normal behandling med Inntektsmelding
            håndterSøknad(januar)
            val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektsmeldingInntekt, refusjon = Refusjon(inntektsmeldingInntekt, null, emptyList()))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertEquals(INNTEKT, inspektør.fastsattInntekt(1.januar))
            assertBeløpstidslinje(Beløpstidslinje.fra(januar, inntektsmeldingInntekt, inntektsmeldingId.arbeidsgiver), inspektør.refusjon(1.vedtaksperiode))

            // Saksbehandler skjønnsmessig fastsetter
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = skjønnsfastsattInntekt)))
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertNotNull(inspektør.skjønnsfastsatt(1.januar))
            assertBeløpstidslinje(Beløpstidslinje.fra(januar, inntektsmeldingInntekt, inntektsmeldingId.arbeidsgiver), inspektør.refusjon(1.vedtaksperiode))

            // Saksbehandler endrer kun refusjon, men beholder inntekt
            val overstyrInntektOgRefusjonId = UUID.randomUUID()
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = inntektsmeldingInntekt, refusjonsopplysninger = listOf(Triple(1.januar, null, skjønnsfastsattInntekt)))), hendelseId = overstyrInntektOgRefusjonId)
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertNotNull(inspektør.skjønnsfastsatt(1.januar))
            assertBeløpstidslinje(Beløpstidslinje.fra(januar, skjønnsfastsattInntekt, overstyrInntektOgRefusjonId.saksbehandler), inspektør.refusjon(1.vedtaksperiode))
        }
    }

    @Test
    fun `Overstyre refusjon og inntekt etter skjønnsmessig fastsatt -- inntekten er det samme som skjønnsfastsatt`() {
        val inntektsmeldingInntekt = INNTEKT
        val skjønnsfastsattInntekt = INNTEKT * 2

        a1 {
            // Normal behandling med Inntektsmelding
            håndterSøknad(januar)
            val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektsmeldingInntekt, refusjon = Refusjon(inntektsmeldingInntekt, null, emptyList()))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertEquals(INNTEKT, inspektør.fastsattInntekt(1.januar))
            assertBeløpstidslinje(Beløpstidslinje.fra(januar, inntektsmeldingInntekt, inntektsmeldingId.arbeidsgiver), inspektør.refusjon(1.vedtaksperiode))

            // Saksbehandler skjønnsmessig fastsetter
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = skjønnsfastsattInntekt)))
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertNotNull(inspektør.skjønnsfastsatt(1.januar))
            assertBeløpstidslinje(Beløpstidslinje.fra(januar, inntektsmeldingInntekt, inntektsmeldingId.arbeidsgiver), inspektør.refusjon(1.vedtaksperiode))

            // Saksbehandler endrer refusjon og inntekt til INNTEKT * 2
            val overstyrInntektOgRefusjonId = UUID.randomUUID()
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(orgnummer = a1, inntekt = skjønnsfastsattInntekt, refusjonsopplysninger = listOf(Triple(1.januar, null, skjønnsfastsattInntekt)))), hendelseId = overstyrInntektOgRefusjonId)
            assertEquals(3, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            assertEquals(INNTEKT * 2, inspektør.fastsattInntekt(1.januar))
            assertBeløpstidslinje(Beløpstidslinje.fra(januar, skjønnsfastsattInntekt, overstyrInntektOgRefusjonId.saksbehandler), inspektør.refusjon(1.vedtaksperiode))
        }
    }
}
