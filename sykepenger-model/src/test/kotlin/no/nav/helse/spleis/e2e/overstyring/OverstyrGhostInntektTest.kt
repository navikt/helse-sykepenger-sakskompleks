package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.assertVarsler
import no.nav.helse.spleis.e2e.håndterArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlagFlereArbeidsgivere
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class OverstyrGhostInntektTest : AbstractEndToEndTest() {
    @Test
    fun `Overstyrer ghost-inntekt -- happy case`() {
        tilOverstyring(
        )
        håndterOverstyrInntekt(500.månedlig, a2, 1.januar)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

        assertEquals(378000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(378000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.fastsattÅrsinntekt)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(500.månedlig, it.fastsattÅrsinntekt)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter(orgnummer = a1))
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a1)
    }

    @Test
    fun `Ved overstyring av inntekt til under krav til minste sykepengegrunnlag skal vi lage en utbetaling uten utbetaling`() {
        tilOverstyring()
        håndterOverstyrInntekt(INGEN, a1, 1.januar)
        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(a1, 3750.månedlig),
                OverstyrtArbeidsgiveropplysning(a2, INGEN)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        assertVarsler(listOf(Varselkode.RV_VV_2, Varselkode.RV_SV_1), 1.vedtaksperiode.filter())
        assertEquals(2, inspektør.antallUtbetalinger)
        assertEquals(0, inspektør.utbetaling(1).arbeidsgiverOppdrag.nettoBeløp())
        Assertions.assertTrue(inspektør.utbetaling(1).erAvsluttet)
        Assertions.assertTrue(inspektør.utbetaling(0).erForkastet)
    }

    @Test
    fun `Overstyr ghost-inntekt -- ghost har ingen inntekt fra før av`() {
        tilOverstyring(
        )
        håndterOverstyrInntekt(500.månedlig, a2, 1.januar)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inntektsgrunnlag.inspektør

        assertEquals(378000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(378000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.fastsattÅrsinntekt)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(500.månedlig, it.fastsattÅrsinntekt)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a1)
        assertVarsel(Varselkode.RV_VV_2, 1.vedtaksperiode.filter(orgnummer = a1))
    }

    private fun tilOverstyring(fom: LocalDate = 1.januar, tom: LocalDate = 31.januar) {
        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(fom, tom, 100.prosent), orgnummer = a1)
        håndterArbeidsgiveropplysninger(
            listOf(fom til fom.plusDays(15)),
            orgnummer = a1,
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode
        )
        håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
    }
}
