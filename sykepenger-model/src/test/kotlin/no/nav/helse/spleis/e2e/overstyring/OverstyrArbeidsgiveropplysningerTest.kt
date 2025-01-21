package no.nav.helse.spleis.e2e.overstyring

import java.util.*
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.dsl.a3
import no.nav.helse.februar
import no.nav.helse.hendelser.Avsender.ARBEIDSGIVER
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.arbeidsgiver
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.assertBeløpstidslinje
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.beløpstidslinje
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.saksbehandler
import no.nav.helse.person.inntekt.Arbeidsgiverinntekt
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.assertVarsler
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.testhelpers.assertInstanceOf
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Endringskode.UEND
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OverstyrArbeidsgiveropplysningerTest : AbstractEndToEndTest() {

    @Test
    fun `Overstyring av refusjon skal gjelde også på forlengelser`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterOverstyrArbeidsgiveropplysninger(
            1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(
                a1, INNTEKT, listOf(
                Triple(1.januar, null, INNTEKT / 2),
            )
            )
        )
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertEquals(INNTEKT / 2, inspektør.vedtaksperioder(2.vedtaksperiode).refusjonstidslinje[1.februar].beløp)
        håndterYtelser(2.vedtaksperiode)
        assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
        assertVarsel(Varselkode.RV_UT_23, 2.vedtaksperiode.filter())
    }

    @Test
    fun `Kun periodene med endring i refusjon revurderes`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterOverstyrArbeidsgiveropplysninger(
            1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(
                a1, INNTEKT, listOf(
                Triple(1.januar, 31.januar, INNTEKT),
                Triple(1.februar, null, INNTEKT / 2)
            )
            )
        )
        )
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `overstyrer inntekt og refusjon`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()
        val nyInntekt = INNTEKT * 2
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(
                a1, nyInntekt, listOf(
                Triple(1.januar, null, nyInntekt)
            )
            )
        ), meldingsreferanseId = overstyringId
        )
        håndterYtelser(1.vedtaksperiode)
        val førsteUtbetaling = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(1)
        assertEquals(førsteUtbetaling.korrelasjonsId, revurdering.korrelasjonsId)
        assertEquals(0, revurdering.personOppdrag.size)
        revurdering.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(17.januar, oppdrag[0].inspektør.fom)
            assertEquals(31.januar, oppdrag[0].inspektør.tom)
            assertEquals(2161, oppdrag[0].inspektør.beløp)
        }

        assertEquals(nyInntekt, inspektør.fastsattInntekt(1.vedtaksperiode))
        assertEquals(1, inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.inntektsgrunnlag.arbeidsgiverInntektsopplysninger.size)
        assertBeløpstidslinje(Beløpstidslinje.fra(januar, nyInntekt, overstyringId.saksbehandler), inspektør.refusjon(1.vedtaksperiode))

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )
    }

    @Test
    fun `ny inntektsmelding etter saksbehandleroverstyrt inntekt`() {
        nyttVedtak(januar)
        val nySaksbehandlerInntekt = INNTEKT * 2
        val nyIMInntekt = INNTEKT * 3
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(
                a1, nySaksbehandlerInntekt, listOf(
                Triple(1.januar, null, nySaksbehandlerInntekt)
            )
            )
        ), meldingsreferanseId = overstyringId
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertEquals(nySaksbehandlerInntekt, inspektør.fastsattInntekt(1.januar))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = nyIMInntekt
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter())
        assertEquals(nyIMInntekt, inspektør.fastsattInntekt(1.januar))
    }

    @Test
    fun `overstyrer inntekt og refusjon til samme som før`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()
        val nyInntekt = INNTEKT * 2
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(a1, nyInntekt, listOf(Triple(1.januar, null, nyInntekt)))
            ),
            meldingsreferanseId = overstyringId
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()
        val overstyring2Id = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(a1, nyInntekt, listOf(Triple(1.januar, null, nyInntekt)))
            ),
            meldingsreferanseId = overstyring2Id
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        val førsteUtbetaling = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(1)
        assertEquals(førsteUtbetaling.korrelasjonsId, revurdering.korrelasjonsId)
        assertEquals(0, revurdering.personOppdrag.size)
        revurdering.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(17.januar, oppdrag[0].inspektør.fom)
            assertEquals(31.januar, oppdrag[0].inspektør.tom)
            assertEquals(2161, oppdrag[0].inspektør.beløp)
        }

        assertEquals(nyInntekt, inspektør.fastsattInntekt(1.vedtaksperiode))
        assertEquals(1, inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.inntektsgrunnlag.arbeidsgiverInntektsopplysninger.size)
        assertBeløpstidslinje(Beløpstidslinje.fra(januar, nyInntekt, overstyringId.saksbehandler), inspektør.refusjon(1.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
    }

    @Test
    fun `overstyring av refusjon skal starte revurdering fom første dato med endring`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        forlengVedtak(mars)
        nullstillTilstandsendringer()
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT, listOf(Triple(1.mars, null, INNTEKT / 2)))),
            meldingsreferanseId = overstyringId
        )
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

        assertVarsel(Varselkode.RV_UT_23, 3.vedtaksperiode.filter())
        val førsteUtbetaling = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(3)
        assertEquals(førsteUtbetaling.korrelasjonsId, revurdering.korrelasjonsId)
        assertEquals(1, revurdering.personOppdrag.size)
        revurdering.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(17.januar, oppdrag[0].inspektør.fom)
            assertEquals(28.februar, oppdrag[0].inspektør.tom)
            assertEquals(1431, oppdrag[0].inspektør.beløp)
            assertEquals(ENDR, oppdrag[0].inspektør.endringskode)

            assertEquals(1.mars, oppdrag[1].inspektør.fom)
            assertEquals(30.mars, oppdrag[1].inspektør.tom)
            assertEquals(715, oppdrag[1].inspektør.beløp)
            assertEquals(NY, oppdrag[1].inspektør.endringskode)
        }
        revurdering.personOppdrag.also { oppdrag ->
            assertEquals(1.mars, oppdrag[0].inspektør.fom)
            assertEquals(30.mars, oppdrag[0].inspektør.tom)
            assertEquals(716, oppdrag[0].inspektør.beløp)
            assertEquals(NY, oppdrag[0].inspektør.endringskode)
        }
        assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        assertEquals(1, inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.inntektsgrunnlag.arbeidsgiverInntektsopplysninger.size)
        assertEquals(INNTEKT, inspektør.fastsattInntekt(1.vedtaksperiode))
        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(januar, INNTEKT), inspektør.refusjon(1.vedtaksperiode), ignoreMeldingsreferanseId = true)
        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(februar, INNTEKT), inspektør.refusjon(2.vedtaksperiode), ignoreMeldingsreferanseId = true)
        assertBeløpstidslinje(Beløpstidslinje.fra(mars, INNTEKT / 2, overstyringId.saksbehandler), inspektør.refusjon(3.vedtaksperiode))

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
    }

    @Test
    fun `overstyrer inntekt på Infotrygdvilkårsgrunnlag`() {
        createOvergangFraInfotrygdPerson()
        assertTrue(inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.infotrygd)
        val antallHistorikkInnslagFør = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val gammelInntekt = inspektør.inntekt(1.januar)
        val nyInntekt = INNTEKT * 2
        nullstillTilstandsendringer()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(OverstyrtArbeidsgiveropplysning(a1, nyInntekt, emptyList()))
        )
        assertEquals(antallHistorikkInnslagFør, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertSame(gammelInntekt, inspektør.inntekt(1.januar))
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `skal være idempotente greier`() {
        nyttVedtak(januar)

        val overstyr: () -> Unit = {
            håndterOverstyrArbeidsgiveropplysninger(
                skjæringstidspunkt = 1.januar,
                meldingsreferanseId = UUID.randomUUID(),
                arbeidsgiveropplysninger = listOf(
                    OverstyrtArbeidsgiveropplysning(a1, INNTEKT / 2, refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT / 2)))
                )
            )
        }

        assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        overstyr()
        håndterYtelser(1.vedtaksperiode)
        assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter())
        assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        nullstillTilstandsendringer()

        repeat(10) { overstyr() }
        assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `overstyrer arbeidsgiveropplysninger på flere arbeidsgivere`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, a3, inntekt = inntektPerArbeidsgiver)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a3)

        val vilkårsgrunnlagHistorikkInnslagFørOverstyring = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val overstyringId = UUID.randomUUID()
        val a3RefusjonsopplysningerFørOverstyring = inspektør(a3).refusjon(1.vedtaksperiode)
        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(januar, inntektPerArbeidsgiver), a3RefusjonsopplysningerFørOverstyring, ignoreMeldingsreferanseId = true)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 20.januar, inntektPerArbeidsgiver),
                        Triple(21.januar, null, INGEN),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                orgnummer = a2,
                inntekt = inntektPerArbeidsgiver * 1.25,
                refusjonsopplysninger = listOf(
                    Triple(1.januar, null, inntektPerArbeidsgiver * 1.25)
                )
            ), OverstyrtArbeidsgiveropplysning(
                orgnummer = a3,
                inntekt = inntektPerArbeidsgiver * 1.5,
                refusjonsopplysninger = listOf(
                    Triple(1.januar, null, inntektPerArbeidsgiver)
                )
            )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter(orgnummer = a1))
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        // a1
        assertEquals(inntektPerArbeidsgiver, inspektør(a1).fastsattInntekt(1.januar))

        assertBeløpstidslinje(
            ARBEIDSGIVER.beløpstidslinje(1.januar til 20.januar, inntektPerArbeidsgiver) + SAKSBEHANDLER.beløpstidslinje(21.januar til 31.januar, INGEN),
            inspektør.refusjon(1.vedtaksperiode),
            ignoreMeldingsreferanseId = true
        )

        // a2
        assertEquals(inntektPerArbeidsgiver * 1.25, inspektør(a2).fastsattInntekt(1.januar))
        assertBeløpstidslinje(Beløpstidslinje.fra(januar, inntektPerArbeidsgiver * 1.25, overstyringId.saksbehandler), inspektør(a2).refusjon(1.vedtaksperiode))

        // a3
        assertEquals(inntektPerArbeidsgiver * 1.5, inspektør(a3).fastsattInntekt(1.januar))
        assertBeløpstidslinje(a3RefusjonsopplysningerFørOverstyring, inspektør(a3).refusjon(1.vedtaksperiode))
    }

    @Test
    fun `to arbeidsgivere uten endring`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntektPerArbeidsgiver)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)

        val vilkårsgrunnlagHistorikkInnslagFørOverstyring = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val overstyringId = UUID.randomUUID()
        val a1InntektFørOverstyring = inspektør.inntekt(1.januar)
        val a2InntektFørOverstyring = inspektør(a2).inntekt(1.januar)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver,
                refusjonsopplysninger = listOf(Triple(1.januar, null, inntektPerArbeidsgiver))
                )
            )
        )

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        assertSame(a1InntektFørOverstyring, inspektør.inntekt(1.januar))
        assertSame(a2InntektFørOverstyring, inspektør(a2).inntekt(1.januar))
    }

    @Test
    fun `to arbeidsgivere kun endring på den ene`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntektPerArbeidsgiver)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)

        val vilkårsgrunnlagHistorikkInnslagFørOverstyring = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val overstyringId = UUID.randomUUID()
        val a1InntektsopplysningFørOverstyring = inspektør.inntekt(1.vedtaksperiode)
        val a1RefusjonFørOverstyring = inspektør.refusjon(1.vedtaksperiode)
        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(januar, inntektPerArbeidsgiver), a1RefusjonFørOverstyring, ignoreMeldingsreferanseId = true)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                orgnummer = a2,
                inntekt = inntektPerArbeidsgiver * 1.5,
                refusjonsopplysninger = listOf(
                    Triple(1.januar, 20.januar, inntektPerArbeidsgiver),
                    Triple(21.januar, null, inntektPerArbeidsgiver * 1.5)
                )
            )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter(orgnummer = a1))
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        assertSame(a1InntektsopplysningFørOverstyring, inspektør.inntekt(1.vedtaksperiode))
        assertBeløpstidslinje(a1RefusjonFørOverstyring, inspektør.refusjon(1.vedtaksperiode))

        assertEquals(inntektPerArbeidsgiver * 1.5, inspektør(a2).fastsattInntekt(1.januar))
        assertBeløpstidslinje(
            ARBEIDSGIVER.beløpstidslinje(1.januar til 20.januar, inntektPerArbeidsgiver) + SAKSBEHANDLER.beløpstidslinje(21.januar til 31.januar, inntektPerArbeidsgiver * 1.5),
            inspektør(a2).refusjon(1.vedtaksperiode),
            ignoreMeldingsreferanseId = true
        )

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING, a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, a2)
    }

    @Test
    fun `to arbeidsgivere kun refusjonsendring på den ene og endring av inntekt på andre`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntektPerArbeidsgiver)
        forlengVedtak(februar, a1, a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, a2)

        val refusjonsopplysningerFørOverstyringA1 = inspektør.refusjon(1.vedtaksperiode) + inspektør.refusjon(2.vedtaksperiode)
        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(1.januar til 28.februar, inntektPerArbeidsgiver), refusjonsopplysningerFørOverstyringA1, ignoreMeldingsreferanseId = true)

        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver * 1.5,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                orgnummer = a2,
                inntekt = inntektPerArbeidsgiver,
                refusjonsopplysninger = listOf(
                    Triple(1.januar, 31.januar, inntektPerArbeidsgiver),
                    Triple(1.februar, null, inntektPerArbeidsgiver / 2)
                )
            )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertEquals(inntektPerArbeidsgiver * 1.5, inspektør.fastsattInntekt(1.januar))
        assertEquals(inntektPerArbeidsgiver, inspektør(a2).fastsattInntekt(1.januar))
        assertBeløpstidslinje(refusjonsopplysningerFørOverstyringA1, inspektør.refusjon(1.vedtaksperiode) + inspektør.refusjon(2.vedtaksperiode))

        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(januar, inntektPerArbeidsgiver), inspektør(a2).refusjon(1.vedtaksperiode), ignoreMeldingsreferanseId = true)
        assertBeløpstidslinje(Beløpstidslinje.fra(februar, inntektPerArbeidsgiver / 2, overstyringId.saksbehandler), inspektør(a2).refusjon(2.vedtaksperiode))

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING, a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING, a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING, a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING, a2)
    }

    @Test
    fun `flere arbeidsgivere får rett utbetaling etter nye opplysninger på begge arbeidsgivere`() {
        val inntekt = 10000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntekt)
        assertEquals(1, inspektør(a1).antallUtbetalinger)
        assertEquals(1, inspektør(a2).antallUtbetalinger)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntekt * 1.5,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntekt),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                orgnummer = a2,
                inntekt = inntekt,
                refusjonsopplysninger = listOf(
                    Triple(1.januar, 20.januar, inntekt),
                    Triple(21.januar, null, INGEN)
                )
            )
            )
        )

        håndterYtelser(1.vedtaksperiode)

        assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter(orgnummer = a2))

        assertEquals(2, inspektør(a1).antallUtbetalinger)
        assertEquals(2, inspektør(a2).antallUtbetalinger)

        assertEquals(inspektør(a1).utbetaling(0).korrelasjonsId, inspektør(a1).sisteUtbetaling().korrelasjonsId)
        assertEquals(inspektør(a2).utbetaling(0).korrelasjonsId, inspektør(a2).sisteUtbetaling().korrelasjonsId)

        inspektør(a1).utbetaling(0).let { opprinneligUtbetaling ->
            assertEquals(0, opprinneligUtbetaling.personOppdrag.size)
            assertEquals(1, opprinneligUtbetaling.arbeidsgiverOppdrag.size)
            opprinneligUtbetaling.arbeidsgiverOppdrag[0].let { utbetalingslinje ->
                assertEquals(17.januar, utbetalingslinje.inspektør.fom)
                assertEquals(31.januar, utbetalingslinje.inspektør.tom)
                assertEquals(462, utbetalingslinje.inspektør.beløp)
                assertEquals(NY, utbetalingslinje.inspektør.endringskode)
            }
        }

        inspektør(a1).sisteUtbetaling().let { revurdering ->
            assertEquals(1, revurdering.personOppdrag.size)
            assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
            revurdering.arbeidsgiverOppdrag[0].let { utbetalingslinje ->
                assertEquals(17.januar, utbetalingslinje.inspektør.fom)
                assertEquals(31.januar, utbetalingslinje.inspektør.tom)
                assertEquals(462, utbetalingslinje.inspektør.beløp)
                assertEquals(UEND, utbetalingslinje.inspektør.endringskode)
            }
            revurdering.personOppdrag[0].let { utbetalingslinje ->
                assertEquals(17.januar, utbetalingslinje.inspektør.fom)
                assertEquals(31.januar, utbetalingslinje.inspektør.tom)
                assertEquals(230, utbetalingslinje.inspektør.beløp)
                assertEquals(NY, utbetalingslinje.inspektør.endringskode)
            }
        }

        inspektør(a2).utbetaling(0).let { opprinneligUtbetaling ->
            assertEquals(0, opprinneligUtbetaling.personOppdrag.size)
            assertEquals(1, opprinneligUtbetaling.arbeidsgiverOppdrag.size)
            opprinneligUtbetaling.arbeidsgiverOppdrag[0].let { utbetalingslinje ->
                assertEquals(17.januar, utbetalingslinje.inspektør.fom)
                assertEquals(31.januar, utbetalingslinje.inspektør.tom)
                assertEquals(461, utbetalingslinje.inspektør.beløp)
                assertEquals(NY, utbetalingslinje.inspektør.endringskode)
            }
        }
        inspektør(a2).sisteUtbetaling().let { revurdering ->
            assertEquals(1, revurdering.personOppdrag.size)
            assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
            revurdering.arbeidsgiverOppdrag[0].let { utbetalingslinje ->
                assertEquals(17.januar, utbetalingslinje.inspektør.fom)
                assertEquals(19.januar, utbetalingslinje.inspektør.tom)
                assertEquals(462, utbetalingslinje.inspektør.beløp)
                assertEquals(NY, utbetalingslinje.inspektør.endringskode)
            }
            revurdering.personOppdrag[0].let { utbetalingslinje ->
                assertEquals(22.januar, utbetalingslinje.inspektør.fom)
                assertEquals(31.januar, utbetalingslinje.inspektør.tom)
                assertEquals(462, utbetalingslinje.inspektør.beløp)
                assertEquals(NY, utbetalingslinje.inspektør.endringskode)
            }
        }
    }

    @Test
    fun `overstyrer arbeidsgiver som ikke er i sykepengegrunnlaget`() {
        nyttVedtak(januar, orgnummer = a1)
        val nyInntekt = INNTEKT * 1.25

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = nyInntekt,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, nyInntekt)
                    )
                ), OverstyrtArbeidsgiveropplysning(
                orgnummer = a2,
                inntekt = nyInntekt,
                refusjonsopplysninger = listOf(
                    Triple(1.januar, null, nyInntekt)
                )
            )
            )
        )

        val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(1.vedtaksperiode)
        assertNotNull(vilkårsgrunnlag)
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inspektør.inntektsgrunnlag.inspektør
        assertEquals(1, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)

        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(INNTEKT, it.inntektsopplysning.inspektør.beløp)
            assertInstanceOf<Arbeidsgiverinntekt>(it.inntektsopplysning)

            assertEquals(nyInntekt, it.fastsattÅrsinntekt)
            assertNotNull(it.korrigertInntekt)
        }
    }

    @Test
    fun `overstyrer kun enkelte arbeidsgivere i sykepengegrunnlaget`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntektPerArbeidsgiver)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)

        val vilkårsgrunnlagHistorikkInnslagFørOverstyring = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val overstyringId = UUID.randomUUID()
        val a1InntektFørOverstyring = inspektør.inntekt(1.vedtaksperiode)
        val a1RefusjonFørOverstyring = inspektør.refusjon(1.vedtaksperiode)
        assertBeløpstidslinje(ARBEIDSGIVER.beløpstidslinje(januar, inntektPerArbeidsgiver), a1RefusjonFørOverstyring, ignoreMeldingsreferanseId = true)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver * 1.5,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 20.januar, inntektPerArbeidsgiver),
                        Triple(21.januar, null, inntektPerArbeidsgiver * 1.5)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(Varselkode.RV_UT_23, 1.vedtaksperiode.filter(orgnummer = a1))
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertSame(a1InntektFørOverstyring, inspektør.inntekt(1.vedtaksperiode))
        assertSame(a1RefusjonFørOverstyring, inspektør.refusjon(1.vedtaksperiode))

        assertEquals(inntektPerArbeidsgiver * 1.5, inspektør(a2).fastsattInntekt(1.vedtaksperiode))
        assertBeløpstidslinje(
            ARBEIDSGIVER.beløpstidslinje(1.januar til 20.januar, inntektPerArbeidsgiver) + SAKSBEHANDLER.beløpstidslinje(21.januar til 31.januar, inntektPerArbeidsgiver * 1.5) ,
            inspektør(a2).refusjon(1.vedtaksperiode),
            ignoreMeldingsreferanseId = true
        )
    }

    @Test
    fun `Innteksmelding overstyrer saksbehandlerinntekt`() {
        nyeVedtak(januar, a1, a2)
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = UUID.randomUUID(),
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = INNTEKT * 1.5,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT * 1.5))
                ),
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = INNTEKT * 1.5,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT * 1.5))
                )
            )
        )
        assertNotNull(inspektør.korrigertInntekt(1.januar))
        assertEquals(INNTEKT * 1.5, inspektør.fastsattInntekt(1.januar))
        assertNotNull(inspektør(a2).korrigertInntekt(1.januar))
        assertEquals(INNTEKT * 1.5, inspektør(a2).fastsattInntekt(1.januar))

        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT,
            orgnummer = a1
        )

        assertVarsel(Varselkode.RV_IM_4, 1.vedtaksperiode.filter(a1))

        assertNull(inspektør.korrigertInntekt(1.januar))
        assertEquals(INNTEKT, inspektør.fastsattInntekt(1.januar))
        assertNotNull(inspektør(a2).korrigertInntekt(1.januar))
        assertEquals(INNTEKT * 1.5, inspektør(a2).fastsattInntekt(1.januar))
    }

    @Test
    fun `Legge til refusjonsopplysninger tilbake i tid`() {
        val im1 = UUID.randomUUID()
        nyttVedtak(januar, inntektsmeldingId = im1)

        nyPeriode(5.februar til 28.februar)
        val im2 = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 7.februar
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertBeløpstidslinje(Beløpstidslinje.fra(januar, INNTEKT, im1.arbeidsgiver), inspektør.refusjon(1.vedtaksperiode))
        assertBeløpstidslinje(Beløpstidslinje.fra(5.februar til 28.februar, INNTEKT, im2.arbeidsgiver), inspektør.refusjon(2.vedtaksperiode))

        nullstillTilstandsendringer()

        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 5.februar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT, listOf(Triple(5.februar, null, INNTEKT)))
            )
        )

        assertBeløpstidslinje(Beløpstidslinje.fra(5.februar til 28.februar, INNTEKT, im2.arbeidsgiver), inspektør.refusjon(2.vedtaksperiode))

        assertVarsler(emptyList(), 2.vedtaksperiode.filter())
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING)
    }
}
