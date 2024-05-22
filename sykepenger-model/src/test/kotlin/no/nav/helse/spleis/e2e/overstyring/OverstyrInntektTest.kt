package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.inntekt.IkkeRapportert
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertInntektForDato
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
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
        tilGodkjenning(fom, 31.januar(2021), 100.prosent, fom)

        assertInntektForDato(INNTEKT, fom, inspektør = inspektør)

        håndterInntektsmelding(
            listOf(fom til fom.plusDays(15)),
            beregnetInntekt = overstyrtInntekt,
            refusjon = Refusjon(overstyrtInntekt, null, emptyList()),
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING)

        // assert at vi bruker den nye inntekten i beregning av penger til sjuk.
        val vilkårsgrunnlagInspektør = inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør
        val sykepengegrunnlagInspektør = vilkårsgrunnlagInspektør?.sykepengegrunnlag?.inspektør
        sykepengegrunnlagInspektør?.arbeidsgiverInntektsopplysningerPerArbeidsgiver?.get(ORGNUMMER)?.inspektør
            ?.also {
                assertEquals(overstyrtInntekt, it.inntektsopplysning.inspektør.beløp)
                assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
    }

    @Test
    fun `overstyre ghostinntekt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
            inntekter = inntektperioderForSykepengegrunnlag {
                1.oktober(2017) til 1.desember(2017) inntekter {
                    a1 inntekt INNTEKT
                }
            },
            arbeidsforhold = emptyList()
        ), arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017), type = Arbeidsforholdtype.ORDINÆRT)
        ), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()

        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(a2, 500.daglig, "retter opp ikke-rapportert-inntekt", null, emptyList())
        ))
        val vilkårsgrunnlagInspektør = inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør
        val sykepengegrunnlagInspektør = vilkårsgrunnlagInspektør?.sykepengegrunnlag?.inspektør
        val a2Opplysninger = sykepengegrunnlagInspektør?.arbeidsgiverInntektsopplysningerPerArbeidsgiver?.get(a2)?.inspektør ?: fail { "må ha inntekt for a2" }
        assertEquals(500.daglig, a2Opplysninger.inntektsopplysning.inspektør.beløp)
        assertEquals(Saksbehandler::class, a2Opplysninger.inntektsopplysning::class)
        val overstyrtInntekt = a2Opplysninger.inntektsopplysning.inspektør.forrigeInntekt ?: fail { "forventet overstyrt inntekt" }
        assertEquals(IkkeRapportert::class, overstyrtInntekt::class)
    }

    @Test
    fun `skal ikke hente registerdata for vilkårsprøving på nytt ved overstyring av inntekt`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 19000.månedlig, orgnummer = ORGNUMMER, skjæringstidspunkt = 1.januar)
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

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = OverMinstegrense,
        )
        val inntekter = listOf(grunnlag(ORGNUMMER, 1.januar, OverMinstegrense.repeat(3)))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntekter, arbeidsforhold = emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterOverstyrInntekt(UnderMinstegrense, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        val utbetalinger = inspektør.utbetalinger
        assertEquals(2, utbetalinger.size)
        assertEquals(0, utbetalinger.last().inspektør.arbeidsgiverOppdrag.nettoBeløp())
        assertTrue(utbetalinger.last().erAvsluttet())
        assertTrue(utbetalinger.first().inspektør.erForkastet)
    }

}
