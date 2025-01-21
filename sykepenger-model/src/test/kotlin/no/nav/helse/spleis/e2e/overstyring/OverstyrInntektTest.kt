package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_2
import no.nav.helse.person.inntekt.Arbeidsgiverinntekt
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertInntektForDato
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.håndterArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class OverstyrInntektTest : AbstractEndToEndTest() {

    @Test
    fun `skal kunne overstyre en inntekt i et enkelt case`() {
        // Hva gjør vi egentlig med overstyring? Skal man kunne sette opp inntekten uten å ha mottatt en ny inntektsmelding med nye refusjonsopplysninger?
        val fom = 1.januar(2021)
        val overstyrtInntekt = 32000.månedlig
        tilGodkjenning(fom til 31.januar(2021), 100.prosent)

        assertInntektForDato(INNTEKT, fom, inspektør = inspektør)

        håndterInntektsmelding(
            listOf(fom til fom.plusDays(15)),
            beregnetInntekt = overstyrtInntekt,
            refusjon = Refusjon(overstyrtInntekt, null, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()

        assertVarsel(RV_IM_4, 1.vedtaksperiode.filter())
        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING)

        // assert at vi bruker den nye inntekten i beregning av penger til sjuk.
        val vilkårsgrunnlagInspektør = inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør
        val sykepengegrunnlagInspektør = vilkårsgrunnlagInspektør?.inntektsgrunnlag?.inspektør
        sykepengegrunnlagInspektør?.arbeidsgiverInntektsopplysningerPerArbeidsgiver?.get(a1)?.inspektør
            ?.also {
                assertEquals(overstyrtInntekt, it.inntektsopplysning.inspektør.beløp)
                assertEquals(Arbeidsgiverinntekt::class, it.inntektsopplysning::class)
            }
    }

    @Test
    fun `overstyre ghostinntekt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterArbeidsgiveropplysninger(
            listOf(1.januar til 16.januar),
            orgnummer = a1,
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            skatteinntekter = listOf(a1 to INNTEKT),
            arbeidsforhold = listOf(
                Triple(a1, LocalDate.EPOCH, null),
                Triple(a2, 1.desember(2017), null)
            ),
            orgnummer = a1
        )
        assertVarsel(RV_VV_2, 1.vedtaksperiode.filter(orgnummer = a1))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()

        håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(OverstyrtArbeidsgiveropplysning(a2, 500.daglig, emptyList()))
        )
        val vilkårsgrunnlagInspektør = inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør
        val sykepengegrunnlagInspektør = vilkårsgrunnlagInspektør?.inntektsgrunnlag?.inspektør
        val a2Opplysninger = sykepengegrunnlagInspektør?.arbeidsgiverInntektsopplysningerPerArbeidsgiver?.get(a2)?.inspektør ?: fail { "må ha inntekt for a2" }
        assertEquals(500.daglig, a2Opplysninger.fastsattÅrsinntekt)
    }

    @Test
    fun `skal ikke hente registerdata for vilkårsprøving på nytt ved overstyring av inntekt`() {
        tilGodkjenning(januar, a1)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 19000.månedlig, orgnummer = a1, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `Ved overstyring av inntekt til under krav til minste sykepengegrunnlag skal vi lage en utbetaling uten utbetaling`() {
        val OverMinstegrense = 50000.årlig
        val UnderMinstegrense = 46000.årlig

        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterArbeidsgiveropplysninger(
            listOf(1.januar til 16.januar),
            beregnetInntekt = OverMinstegrense,
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterOverstyrInntekt(UnderMinstegrense, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        assertVarsel(Varselkode.RV_SV_1, 1.vedtaksperiode.filter())

        assertEquals(2, inspektør.antallUtbetalinger)
        assertEquals(0, inspektør.utbetaling(1).arbeidsgiverOppdrag.nettoBeløp())
        assertTrue(inspektør.utbetaling(1).erAvsluttet)
        assertTrue(inspektør.utbetaling(0).erForkastet)
    }
}
