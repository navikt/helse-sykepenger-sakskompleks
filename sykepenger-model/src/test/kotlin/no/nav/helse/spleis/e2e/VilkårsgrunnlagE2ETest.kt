package no.nav.helse.spleis.e2e

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.november
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VilkårsgrunnlagE2ETest : AbstractEndToEndTest() {

    @Test
    fun `skjæringstidspunkt måneden før inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(26.januar, 8.februar), orgnummer = a1)
        håndterSøknad(Sykdom(26.januar, 8.februar, 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(6.februar, 28.februar), orgnummer = a2)
        håndterSøknad(Sykdom(6.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(21.januar til 21.januar, 6.februar til 20.februar), orgnummer = a2)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            orgnummer = a2,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    ArbeidsgiverInntekt(a1, listOf(
                        desember(2017).lønnsinntekt(),
                        november(2017).lønnsinntekt(),
                        oktober(2017).lønnsinntekt()
                    )),
                    ArbeidsgiverInntekt(a2, listOf(
                        desember(2017).lønnsinntekt(),
                        november(2017).lønnsinntekt(),
                        oktober(2017).lønnsinntekt(),
                    )),
                ), arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, 1.januar(2017), null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.januar(2017), null, Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)
    }

    @Test
    fun `negativt omregnet årsinntekt for ghost-arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    ArbeidsgiverInntekt(a1, listOf(
                        desember(2017).lønnsinntekt(INNTEKT),
                        november(2017).lønnsinntekt(INNTEKT),
                        oktober(2017).lønnsinntekt(INNTEKT)
                    )),
                    ArbeidsgiverInntekt(a2, listOf(
                        desember(2017).lønnsinntekt(2500.månedlig),
                        november(2017).lønnsinntekt((-3000).månedlig),
                    )),
                ), arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, 1.januar(2017), null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.januar(2017), null, Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
    }

    @Test
    fun `Forkaster ikke etterfølgende perioder dersom vilkårsprøving feiler pga minimum inntekt på første periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 17.januar))
        håndterSøknad(Sykdom(1.januar, 17.januar, 100.prosent))

        val arbeidsgiverperioder = listOf(
            1.januar til 16.januar
        )

        håndterInntektsmelding(
            arbeidsgiverperioder = arbeidsgiverperioder,
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 1000.månedlig,
            refusjon = Inntektsmelding.Refusjon(1000.månedlig, null, emptyList()),
        )

        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)

        håndterSykmelding(Sykmeldingsperiode(18.januar, 20.januar))
        håndterSøknad(Sykdom(18.januar, 20.januar, 100.prosent))

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
        )

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE
        )
    }
    
    @Test
    fun `Forkaster ikke vilkårsgrunnlag om det er en periode i AUU med samme skjæringstidspunkt som den som blir annullert`() {
        nyPeriode(1.januar til 16.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        nyPeriode(17.januar til 31.januar)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        assertVilkårsgrunnlagFraSpleisFor(1.januar)
        håndterAnnullerUtbetaling()
        assertIngenVilkårsgrunnlagFraSpleis()
    }

    @Test
    fun `nytt og eneste arbeidsforhold på skjæringstidspunkt`() {
        nyPeriode(1.januar til 31.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
        håndterVilkårsgrunnlag(1.vedtaksperiode, arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(a1, 1.januar, null, Arbeidsforholdtype.ORDINÆRT)))
        assertVarsel(Varselkode.RV_VV_1)
    }

    @Test
    fun `Forkaster vilkårsgrunnlag når periode annulleres`() {
        nyttVedtak(1.januar til 31.januar)
        assertVilkårsgrunnlagFraSpleisFor(1.januar)
        håndterAnnullerUtbetaling()
        assertIngenVilkårsgrunnlagFraSpleis()
    }

    private fun assertVilkårsgrunnlagFraSpleisFor(vararg skjæringstidspunkt: LocalDate) {
        assertEquals(skjæringstidspunkt.toSet(), person.inspektør.vilkårsgrunnlagHistorikk.inspektør.aktiveSpleisSkjæringstidspunkt)
    }
    private fun assertIngenVilkårsgrunnlagFraSpleis() = assertVilkårsgrunnlagFraSpleisFor()
}
