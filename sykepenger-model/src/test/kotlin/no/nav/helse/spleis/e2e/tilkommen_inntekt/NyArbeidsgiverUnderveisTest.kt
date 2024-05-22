package no.nav.helse.spleis.e2e.tilkommen_inntekt

import java.time.LocalDate
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class NyArbeidsgiverUnderveisTest : AbstractDslTest() {

    @Test
    fun `Omgjøring av overlappende periode med nytt skjæringstidspunkt`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
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
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, 1.januar, type = Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT)
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