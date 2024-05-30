package no.nav.helse.spleis.e2e.tilkommen_inntekt

import java.time.LocalDate
import no.nav.helse.Toggle
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class NyArbeidsgiverUnderveisTest : AbstractDslTest() {

    @Test
    fun `Ny arbeidsgiver underveis happy case`() = Toggle.TilkommenInntekt.enable {
        a1 {
            nyttVedtak(1.januar, 31.januar)
        }
        a2 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = 10000.månedlig, begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
        }
        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        }
        a2 {
            inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør.also { sykepengegrunnlagInspektør ->
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                val inntektA2 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.find { it.gjelder(a2) }
                assertEquals(1.februar, inntektA2!!.inspektør.gjelder.start)
                assertEquals(10000.månedlig, inntektA2.inspektør.inntektsopplysning.inspektør.beløp)
                val inntektA1 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.find { it.gjelder(a1) }
                assertEquals(1.januar, inntektA1!!.inspektør.gjelder.start)
                assertEquals(INNTEKT, inntektA1.inspektør.inntektsopplysning.inspektør.beløp)
                assertEquals(INNTEKT, sykepengegrunnlagInspektør.sykepengegrunnlag)
            }
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            assertEquals(0, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.nettoBeløp())
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
            assertEquals(1082.daglig, inspektør.utbetalinger.last().inspektør.utbetalingstidslinje[1.februar].økonomi.inspektør.arbeidsgiverbeløp)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            assertEquals(349.daglig, inspektør.utbetalinger.last().inspektør.utbetalingstidslinje[1.februar].økonomi.inspektør.arbeidsgiverbeløp)
        }
    }

    @Test
    fun `Bytter arbeidsgiver underveis i sykefravær`() = Toggle.TilkommenInntekt.enable {
        a1 {
            nyttVedtak(1.januar, 31.januar)
        }
        a2 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = 10000.månedlig, begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
        }
        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT, gjelder = 1.januar til 31.januar, forklaring = "Noe")))
        }
        a2 {
            inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør.also { sykepengegrunnlagInspektør ->
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                val inntektA2 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.find { it.gjelder(a2) }
                assertEquals(1.februar, inntektA2!!.inspektør.gjelder.start)
                assertEquals(10000.månedlig, inntektA2.inspektør.inntektsopplysning.inspektør.beløp)
                val inntektA1 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.find { it.gjelder(a1) }
                assertEquals(1.januar, inntektA1!!.inspektør.gjelder.start)
                assertEquals(31.januar, inntektA1.inspektør.gjelder.endInclusive)
                assertEquals(INNTEKT, inntektA1.inspektør.inntektsopplysning.inspektør.beløp)
                assertEquals(INNTEKT, sykepengegrunnlagInspektør.sykepengegrunnlag)
            }
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            assertEquals(0, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.nettoBeløp())
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            assertEquals(462.daglig, inspektør.utbetalinger.last().inspektør.utbetalingstidslinje[1.februar].økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(969.daglig, inspektør.utbetalinger.last().inspektør.utbetalingstidslinje[1.februar].økonomi.inspektør.personbeløp)
        }
    }

    @Test
    fun `ghost blir syk`() = Toggle.TilkommenInntekt.enable {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to 10000.månedlig), 1.januar),
                arbeidsforhold = listOf(
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = ORDINÆRT),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = ORDINÆRT),
                ), orgnummer = a1
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a1 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = 10000.månedlig, begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
            inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør.also { sykepengegrunnlagInspektør ->
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                val inntektA2 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(a2) }
                assertInstanceOf(SkattSykepengegrunnlag::class.java, inntektA2.inspektør.inntektsopplysning)
                assertEquals(1.januar til LocalDate.MAX, inntektA2.inspektør.gjelder )
            }
        }
    }
    @Test
    fun `tilkommen inntekt etter skjønnsmessig fastsettelse`() = Toggle.TilkommenInntekt.enable {
        a1 {
            nyttVedtak(1.januar, 31.januar, 100.prosent)
            håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT - 1000.månedlig)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            håndterInntektsmelding(listOf(1.februar til 16.februar), beregnetInntekt = 10000.månedlig, begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør.also { sykepengegrunnlagInspektør ->
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                val inntektA1 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(a1) }
                val inntektA2 = sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.single { it.gjelder(a2) }
                assertInstanceOf(SkjønnsmessigFastsatt::class.java, inntektA1.inspektør.inntektsopplysning)
                assertEquals(1.januar til LocalDate.MAX, inntektA1.inspektør.gjelder )
                assertInstanceOf(Inntektsmelding::class.java, inntektA2.inspektør.inntektsopplysning)
                assertEquals(1.februar til LocalDate.MAX, inntektA2.inspektør.gjelder )
            }
        }
    }


    @Test
    fun `Omgjøring av overlappende periode med nytt skjæringstidspunkt`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(30.januar, Dagtype.Arbeidsdag), ManuellOverskrivingDag(31.januar, Dagtype.Arbeidsdag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterSøknad(Sykdom(31.januar, 15.februar, 100.prosent))
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            assertEquals(31.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
            håndterInntektsmelding(listOf(31.januar til 15.februar), begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        }
        a1 {
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            assertEquals(
                """Arbeidsgiver a2 mangler i sykepengegrunnlaget ved utbetaling av 2018-01-31. 
                Arbeidsgiveren må være i sykepengegrunnlaget for å legge til utbetalingsopplysninger. 
                Arbeidsgiverne i sykepengegrunlaget er [a1]""",
                assertThrows<IllegalStateException> { håndterYtelser(1.vedtaksperiode) }.message
            )
        }
    }

    @Test
    fun `saksbehandler flytter arbeidsgiver på skjæringstidspunktet som tilkommen`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(10.januar, 31.januar, 50.prosent))
            håndterInntektsmelding(listOf(10.januar til 25.januar), 5000.månedlig)
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT), 1.januar),
                arbeidsforhold = listOf(
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = ORDINÆRT),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, 1.januar, type = ORDINÆRT)
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a2, 5000.månedlig, gjelder = 10.januar til LocalDate.MAX, forklaring = "arbeidsgiveren er tilkommen etter skjæringstidspunktet")
            ))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)

            inspektør.utbetaling(1).inspektør.also { utbetalingInspektør ->
                utbetalingInspektør.utbetalingstidslinje[1.januar].also { dag ->
                    assertEquals(100, dag.økonomi.inspektør.totalGrad)
                }
                utbetalingInspektør.utbetalingstidslinje[10.januar].also { dag ->
                    assertEquals(91, dag.økonomi.inspektør.totalGrad)
                }
            }
            inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.also { vilkårsgrunnlagInspektør ->
                assertEquals(INNTEKT, vilkårsgrunnlagInspektør.sykepengegrunnlag.inspektør.sykepengegrunnlag)
            }
        }
    }
}