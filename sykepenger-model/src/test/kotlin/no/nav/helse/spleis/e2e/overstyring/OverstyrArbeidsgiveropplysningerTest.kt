package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
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
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Endringskode.UEND
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class OverstyrArbeidsgiveropplysningerTest : AbstractEndToEndTest() {

    @Test
    fun `Overstyring av refusjon skal gjelde også på forlengelser`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, INNTEKT, "Noo", null, listOf(
                Triple(1.januar,null, INNTEKT / 2),
            ))
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertEquals(INNTEKT/2, inspektør.vedtaksperioder(2.vedtaksperiode).refusjonstidslinje[1.februar].beløp)
        håndterYtelser(2.vedtaksperiode)
    }

    @Test
    fun `Kun periodene med endring i refusjon revurderes`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, INNTEKT, "Noo", null, listOf(
                Triple(1.januar, 31.januar, INNTEKT),
                Triple(1.februar, null, INNTEKT / 2)
            ))
        ))
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `overstyrer inntekt og refusjon`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()
        val nyInntekt = INNTEKT * 2
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, nyInntekt, "Det var jo alt for lite!", null, listOf(
                Triple(1.januar, null, nyInntekt)
            ))
        ), meldingsreferanseId = overstyringId)
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
        inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør?.also { vilkårsgrunnlag ->
            vilkårsgrunnlag.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.let { arbeidsgiverInntektsopplysninger ->
                assertEquals(1, arbeidsgiverInntektsopplysninger.size)
                arbeidsgiverInntektsopplysninger.single().inspektør.also { overstyring ->
                    assertEquals(nyInntekt, overstyring.inntektsopplysning.inspektør.beløp)
                    assertEquals(listOf(Refusjonsopplysning(overstyringId, 1.januar, null, nyInntekt)), overstyring.refusjonsopplysninger)
                }
            }
        } ?: fail { "Forventet vilkårsgrunnlag" }
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
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, nySaksbehandlerInntekt, "Det var jo alt for lite!", null, listOf(
                Triple(1.januar, null, nySaksbehandlerInntekt)
            ))
        ), meldingsreferanseId = overstyringId)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTrue(inspektør.inntektsopplysningISykepengegrunnlaget(1.januar) is Saksbehandler)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = nyIMInntekt,)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTrue(inspektør.inntektsopplysningISykepengegrunnlaget(1.januar) is Inntektsmelding)
    }

    @Test
    fun `overstyrer inntekt og refusjon til samme som før`() {
        nyttVedtak(januar)
        nullstillTilstandsendringer()
        val nyInntekt = INNTEKT * 2
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, nyInntekt, "Det var jo alt for lite!", null, listOf(
                Triple(1.januar, null, nyInntekt)
            ))
        ), meldingsreferanseId = overstyringId)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()
        val overstyring2Id = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, nyInntekt, "Det var jo alt for lite!", null, listOf(
                Triple(1.januar, null, nyInntekt)
            ))
        ), meldingsreferanseId = overstyring2Id)
        håndterYtelser(1.vedtaksperiode)

        val førsteUtbetaling = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(2)
        assertEquals(førsteUtbetaling.korrelasjonsId, revurdering.korrelasjonsId)
        assertEquals(0, revurdering.personOppdrag.size)
        revurdering.arbeidsgiverOppdrag.also { oppdrag ->
            assertEquals(17.januar, oppdrag[0].inspektør.fom)
            assertEquals(31.januar, oppdrag[0].inspektør.tom)
            assertEquals(2161, oppdrag[0].inspektør.beløp)
        }
        assertEquals(3, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør?.also { vilkårsgrunnlag ->
            vilkårsgrunnlag.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.let { arbeidsgiverInntektsopplysninger ->
                assertEquals(1, arbeidsgiverInntektsopplysninger.size)
                arbeidsgiverInntektsopplysninger.single().inspektør.also { overstyring ->
                    assertEquals(nyInntekt, overstyring.inntektsopplysning.inspektør.beløp)
                    assertEquals(listOf(Refusjonsopplysning(overstyringId, 1.januar, null, nyInntekt)), overstyring.refusjonsopplysninger)
                }
            }
        } ?: fail { "Forventet vilkårsgrunnlag" }
        assertTilstander(
            1.vedtaksperiode,
            AVVENTER_GODKJENNING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )
    }

    @Test
    fun `overstyring av refusjon skal starte revurdering fom første dato med endring`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        forlengVedtak(mars)
        nullstillTilstandsendringer()
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, INNTEKT, "Vi bruker det samme som før", null, listOf(
                Triple(1.mars, null, INNTEKT/2)
            ))
        ), meldingsreferanseId = overstyringId)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)

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
        assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør?.also { vilkårsgrunnlag ->
            vilkårsgrunnlag.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.let { arbeidsgiverInntektsopplysninger ->
                assertEquals(1, arbeidsgiverInntektsopplysninger.size)
                arbeidsgiverInntektsopplysninger.single().inspektør.also { overstyring ->
                    assertEquals(INNTEKT, overstyring.inntektsopplysning.inspektør.beløp)
                    assertEquals(listOf(
                        Refusjonsopplysning(overstyring.refusjonsopplysninger.first().inspektør.meldingsreferanseId, 1.januar, 28.februar, INNTEKT),
                        Refusjonsopplysning(overstyringId, 1.mars, null, INNTEKT/2)
                    ), overstyring.refusjonsopplysninger)
                }
            }
        } ?: fail { "Forventet vilkårsgrunnlag" }
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
    }

    @Test
    fun `overstyrer inntekt på Infotrygdvilkårsgrunnlag`() {
        createOvergangFraInfotrygdPerson()
        assertTrue(inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.infotrygd)
        val antallHistorikkInnslagFør = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val gammelInntekt = inspektør.inntektISykepengegrunnlaget(1.januar)
        val nyInntekt = INNTEKT*2
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, nyInntekt, "Prøver å overstyre Infotrygd-inntekt", null, emptyList())
        ))
        håndterYtelser(1.vedtaksperiode)
        assertEquals(antallHistorikkInnslagFør, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertEquals(UEND, inspektør.utbetaling(1).arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(gammelInntekt, inspektør.inntektISykepengegrunnlaget(1.januar))
    }

    @Test
    fun `overstyrer refusjon på Infotrygdvilkårsgrunnlag`() {
        createOvergangFraInfotrygdPerson()
        assertTrue(inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.infotrygd)
        val antallHistorikkInnslag = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val gammelInntekt = inspektør.inntektISykepengegrunnlaget(1.januar)
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(ORGNUMMER, gammelInntekt, "Bare nye refusjonsopplysninger her", null, listOf(
                Triple(1.januar, null, gammelInntekt/2)
            ))
        ), meldingsreferanseId = overstyringId)
        håndterYtelser(1.vedtaksperiode)
        assertEquals(antallHistorikkInnslag + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        assertEquals(gammelInntekt, inspektør.inntektISykepengegrunnlaget(1.januar))
        assertEquals(listOf(Refusjonsopplysning(overstyringId, 1.januar, null, gammelInntekt/2)), inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar))

        val førsteUtbetaling = inspektør.utbetaling(0)
        val revurdering = inspektør.utbetaling(1)
        assertEquals(førsteUtbetaling.korrelasjonsId, revurdering.korrelasjonsId)

        assertEquals(1431, førsteUtbetaling.arbeidsgiverOppdrag.single().inspektør.beløp)
        assertEquals(0, førsteUtbetaling.personOppdrag.size)

        assertEquals(715, revurdering.arbeidsgiverOppdrag.single().inspektør.beløp)
        assertEquals(716, revurdering.personOppdrag.single().inspektør.beløp)
    }

    @Test
    fun `skal være idempotente greier`() {
        nyttVedtak(januar)

        val overstyr: () -> Unit = {
            håndterOverstyrArbeidsgiveropplysninger(
                skjæringstidspunkt = 1.januar,
                meldingsreferanseId = UUID.randomUUID(),
                arbeidsgiveropplysninger = listOf(OverstyrtArbeidsgiveropplysning(ORGNUMMER, INNTEKT/2, "noe", null, refusjonsopplysninger = listOf(
                    Triple(1.januar, null, INNTEKT/2)
                )))
            )
            håndterYtelser(1.vedtaksperiode)
        }

        assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
        overstyr()
        assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        repeat(10) { overstyr() }
        assertEquals(12, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
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
        val a3RefusjonsopplysningerFørOverstyring = inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a3)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver,
                    forklaring = "beholder inntekt, men nye refusjonsopplysninger",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 20.januar, inntektPerArbeidsgiver),
                        Triple(21.januar, null, INGEN),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver*1.25,
                    forklaring = "justerer opp inntekt og refusjon",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver*1.25)
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a3,
                    inntekt = inntektPerArbeidsgiver*1.5,
                    forklaring = "justerer opp inntekt, uendret refusjon",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        // a1
        assertEquals(inntektPerArbeidsgiver, inspektør.inntektISykepengegrunnlaget(1.januar, a1))
        assertEquals(listOf(
            Refusjonsopplysning(overstyringId, 1.januar, 20.januar, inntektPerArbeidsgiver),
            Refusjonsopplysning(overstyringId, 21.januar, null, INGEN)
        ), inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a1))

        // a2
        assertEquals(inntektPerArbeidsgiver*1.25, inspektør.inntektISykepengegrunnlaget(1.januar, a2))
        assertEquals(listOf(
            Refusjonsopplysning(overstyringId, 1.januar, null, inntektPerArbeidsgiver*1.25),
        ), inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a2))

        // a3
        assertEquals(inntektPerArbeidsgiver*1.5, inspektør.inntektISykepengegrunnlaget(1.januar, a3))
        assertEquals(a3RefusjonsopplysningerFørOverstyring, inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a3))
    }

    @Test
    fun `to arbeidsgivere uten endring`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntektPerArbeidsgiver)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)

        val vilkårsgrunnlagHistorikkInnslagFørOverstyring = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val overstyringId = UUID.randomUUID()
        val a1ArbeidsgiverinntektsopplysningerFørOverstyring = inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a1)
        val a2ArbeidsgiverinntektsopplysningerFørOverstyring = inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a2)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver,
                    forklaring = "ingen endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver,
                    forklaring = "ingen endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        assertEquals(a1ArbeidsgiverinntektsopplysningerFørOverstyring, inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a1))
        assertEquals(a2ArbeidsgiverinntektsopplysningerFørOverstyring, inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a2))
    }

    @Test
    fun `to arbeidsgivere kun endring på den ene`() {
        val inntektPerArbeidsgiver = 19000.månedlig
        nyeVedtak(januar, a1, a2, inntekt = inntektPerArbeidsgiver)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a2)

        val vilkårsgrunnlagHistorikkInnslagFørOverstyring = inspektør.vilkårsgrunnlagHistorikkInnslag().size
        val overstyringId = UUID.randomUUID()
        val a1ArbeidsgiverinntektsopplysningerFørOverstyring = inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a1)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver,
                    forklaring = "ingen endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver*1.5,
                    forklaring = "endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 20.januar, inntektPerArbeidsgiver),
                        Triple(21.januar, null, inntektPerArbeidsgiver*1.5)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring +1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        assertEquals(a1ArbeidsgiverinntektsopplysningerFørOverstyring, inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a1))

        assertEquals(inntektPerArbeidsgiver*1.5, inspektør.inntektISykepengegrunnlaget(1.januar, a2))
        assertEquals(listOf(
            Refusjonsopplysning(overstyringId, 1.januar, 20.januar, inntektPerArbeidsgiver),
            Refusjonsopplysning(overstyringId, 21.januar, null, inntektPerArbeidsgiver*1.5)
        ), inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a2))

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

        val refusjonsopplysningerFørOverstyringA1 = inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a1)
        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = inntektPerArbeidsgiver*1.5,
                    forklaring = "endring på inntekt",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntektPerArbeidsgiver),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver,
                    forklaring = "endring på refusjonen",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 31.januar, inntektPerArbeidsgiver),
                        Triple(1.februar, null, inntektPerArbeidsgiver/2)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertEquals(inntektPerArbeidsgiver*1.5, inspektør.inntektISykepengegrunnlaget(1.januar, a1))
        assertEquals(inntektPerArbeidsgiver, inspektør.inntektISykepengegrunnlaget(1.januar, a2))
        assertEquals(refusjonsopplysningerFørOverstyringA1, inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a1))
        assertEquals(listOf(
            Refusjonsopplysning(overstyringId, 1.januar, 31.januar, inntektPerArbeidsgiver),
            Refusjonsopplysning(overstyringId, 1.februar, null, inntektPerArbeidsgiver/2)
        ), inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a2))

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
                    forklaring = "oppjustert inntekt, uendret refusjon",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, inntekt),
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntekt,
                    forklaring = "samme inntekt, overgang til brukerutbetaling",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 20.januar, inntekt),
                        Triple(21.januar, null, INGEN)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode)

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
                    forklaring = "er i sykepengegrunnlaget",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, nyInntekt)
                    )
                ), OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = nyInntekt,
                    forklaring = "er ikke i sykepengegrunnlaget",
                    subsumsjon = null,
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
            assertEquals(nyInntekt, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
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
        val a1ArbeidsgiverinntektsopplysningerFørOverstyring = inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a1)

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = overstyringId,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = inntektPerArbeidsgiver*1.5,
                    forklaring = "endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, 20.januar, inntektPerArbeidsgiver),
                        Triple(21.januar, null, inntektPerArbeidsgiver*1.5)
                    )
                )
            )
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertEquals(vilkårsgrunnlagHistorikkInnslagFørOverstyring + 1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

        assertEquals(a1ArbeidsgiverinntektsopplysningerFørOverstyring, inspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(1.januar, a1))

        assertEquals(inntektPerArbeidsgiver*1.5, inspektør.inntektISykepengegrunnlaget(1.januar, a2))
        assertEquals(listOf(
            Refusjonsopplysning(overstyringId, 1.januar, 20.januar, inntektPerArbeidsgiver),
            Refusjonsopplysning(overstyringId, 21.januar, null, inntektPerArbeidsgiver*1.5)
        ), inspektør.refusjonsopplysningerISykepengegrunnlaget(1.januar, a2))
    }

    @Test
    fun `g`() {
        nyeVedtak(januar, a1, a2)
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            meldingsreferanseId = UUID.randomUUID(),
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a1,
                    inntekt = INNTEKT*1.5,
                    forklaring = "endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT*1.5))
                ),
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = a2,
                    inntekt = INNTEKT*1.5,
                    forklaring = "endring",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT*1.5))
                )
            )
        )
        assertTrue(inspektør.inntektsopplysningISykepengegrunnlaget(1.januar, a1) is Saksbehandler)
        assertTrue(inspektør.inntektsopplysningISykepengegrunnlaget(1.januar, a2) is Saksbehandler)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT, orgnummer = a1,)
        assertTrue(inspektør.inntektsopplysningISykepengegrunnlaget(1.januar, a1) is Inntektsmelding)
        assertTrue(inspektør.inntektsopplysningISykepengegrunnlaget(1.januar, a2) is Saksbehandler)
    }

    @Test
    fun `Legge til refusjonsopplysninger tilbake i tid`() {
        nyttVedtak(januar)

        nyPeriode(5.februar til 28.februar)
        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 7.februar,)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        assertIngenVarsler()

        assertEquals(listOf(
            Refusjonsopplysning(inntektsmeldingId, 5.februar, 6.februar, INNTEKT),
            Refusjonsopplysning(inntektsmeldingId, 7.februar, null, INNTEKT),
        ), inspektør.refusjonsopplysningerISykepengegrunnlaget(5.februar, a1))

        val overstyringId = UUID.randomUUID()
        håndterOverstyrArbeidsgiveropplysninger(5.februar, meldingsreferanseId = overstyringId, arbeidsgiveropplysninger = listOf(
            OverstyrtArbeidsgiveropplysning(a1, INNTEKT, "endre refusjon", null, listOf(
                Triple(5.februar, null, INNTEKT)
            ))
        ))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
    }

    private fun TestArbeidsgiverInspektør.inntektISykepengegrunnlaget(skjæringstidspunkt: LocalDate, orgnr: String = ORGNUMMER) =
        vilkårsgrunnlag(skjæringstidspunkt)!!.inspektør.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(orgnr) }.inspektør.inntektsopplysning.inspektør.beløp

    private fun TestArbeidsgiverInspektør.inntektsopplysningISykepengegrunnlaget(skjæringstidspunkt: LocalDate, orgnr: String = ORGNUMMER) =
        vilkårsgrunnlag(skjæringstidspunkt)!!.inspektør.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(orgnr) }.inspektør.inntektsopplysning

    private fun TestArbeidsgiverInspektør.refusjonsopplysningerISykepengegrunnlaget(skjæringstidspunkt: LocalDate, orgnr: String = ORGNUMMER) =
        vilkårsgrunnlag(skjæringstidspunkt)!!.inspektør.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(orgnr) }.inspektør.refusjonsopplysninger

    private fun TestArbeidsgiverInspektør.arbeidsgiverInntektsopplysningISykepengegrunnlaget(skjæringstidspunkt: LocalDate, orgnr: String = ORGNUMMER) =
        vilkårsgrunnlag(skjæringstidspunkt)!!.inspektør.inntektsgrunnlag.inspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(orgnr) }
}
