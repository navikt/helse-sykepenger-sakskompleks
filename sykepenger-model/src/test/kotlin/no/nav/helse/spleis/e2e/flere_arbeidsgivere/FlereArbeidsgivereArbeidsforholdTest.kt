package no.nav.helse.spleis.e2e.flere_arbeidsgivere

import java.time.LocalDate
import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType
import no.nav.helse.person.UtbetalingInntektskilde
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_1
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.finnSkjæringstidspunkt
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FlereArbeidsgivereArbeidsforholdTest : AbstractEndToEndTest() {

    @Test
    fun `Filtrerer ut irrelevante arbeidsforhold per arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.mars til 16.mars),
            førsteFraværsdag = 1.mars,
            beregnetInntekt = 10000.månedlig,
            orgnummer = a1,
        )
        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 10000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 20000.månedlig.repeat(3))
        )
        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a1, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a2, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.mars)
        assertNotNull(vilkårsgrunnlag)

        assertEquals(1, vilkårsgrunnlag.inspektør.opptjening!!.arbeidsforhold.single { it.orgnummer == a1 }.ansattPerioder.size)
        assertEquals(1, vilkårsgrunnlag.inspektør.opptjening!!.arbeidsforhold.single { it.orgnummer == a2 }.ansattPerioder.size)
    }

    @Test
    fun `Vanlige forlengelser av arbeidsgiver som ikke finnes i aareg, kan utbetales uten warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.mars til 16.mars),
            førsteFraværsdag = 1.mars,
            beregnetInntekt = 10000.månedlig,
            orgnummer = a1,
        )
        val inntekter1 = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 10000.månedlig.repeat(3))
        )
        val arbeidsforhold1 = listOf(Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter1,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.april, 30.april, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertSisteTilstand(2.vedtaksperiode, TilstandType.AVSLUTTET, orgnummer = a1)
        assertIngenVarsler(2.vedtaksperiode.filter(a1))
    }

    @Test
    fun `Flere aktive arbeidsforhold, men kun 1 med inntekt, skal ikke få warning for flere arbeidsgivere`() {
        håndterSykmelding(Sykmeldingsperiode(1.juli(2021), 31.juli(2021)), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.juli(2021), 31.juli(2021), 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.juli(2021) til 16.juli(2021)),
            førsteFraværsdag = 1.juli(2021),
            beregnetInntekt = 30000.månedlig,
            orgnummer = a1,
        )
        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 30000.månedlig.repeat(3))
        )
        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a1, ansattFom = 2.januar(2020), ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a2, ansattFom = 1.mai(2019), ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        assertIngenVarsler()
    }

    @Test
    fun `arbeidsgivere med sammenligningsgrunnlag, men uten inntekt, skal ikke anses som ekstra arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, orgnummer = a1,)

        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT))
        val inntekter = listOf(grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)))

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertEquals(UtbetalingInntektskilde.EN_ARBEIDSGIVER, inspektør(a1).inntektskilde(1.vedtaksperiode))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)

        assertIngenVarsler(1.vedtaksperiode.filter(orgnummer = a1))
        assertEquals(UtbetalingInntektskilde.EN_ARBEIDSGIVER, inspektør(a1).inntektskilde(2.vedtaksperiode))
    }

    @Test
    fun `Syk for a1, slutter i a1, syk for a2, a1 finnes ikke i Aa-reg lenger - ingen warning for manglende arbeidsforhold`() {
        /*
        * Siden vi ikke vet om arbeidsforhold for tidligere utbetalte perioder må vi passe på at ikke lar de periodene føre til advarsel på nye helt uavhengie vedtaksperioder
        * Sjekker kun arbeidsforhold for gjelende skjæringstidspunkt, derfor vil ikke mangel av arbeidsforhold for a1 skape problemer
        * */
        håndterSykmelding(Sykmeldingsperiode(1.mars(2017), 31.mars(2017)), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars(2017), 31.mars(2017), 50.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.mars(2017) til 16.mars(2017)),
            førsteFraværsdag = 1.mars(2017),
            beregnetInntekt = 30000.månedlig,
            orgnummer = a1,
        )
        val inntekterA1 = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 35000.månedlig.repeat(3))
        )
        val arbeidsforholdA1 = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a1, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT)
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekterA1,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforholdA1,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 50.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.mars til 16.mars),
            førsteFraværsdag = 1.mars,
            beregnetInntekt = 30000.månedlig,
            orgnummer = a2,
        )
        val inntekterA2 = listOf(
            grunnlag(a2, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), 35000.månedlig.repeat(3))
        )

        val arbeidsforholdA2 = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a2, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekterA2,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforholdA2,
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertEquals(
            692.0,
            inspektør(a2).vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje[19.mars].økonomi.inspektør.arbeidsgiverbeløp?.daglig)
        val a2Linje = inspektør(a2).utbetaling(0).arbeidsgiverOppdrag.last()
        assertEquals(17.mars, a2Linje.fom)
        assertEquals(30.mars, a2Linje.tom)
        assertEquals(692, a2Linje.beløp)

        assertIngenVarsler()
    }

    @Test
    fun `lagrer kun arbeidsforhold som gjelder under skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.mars til 16.mars),
            førsteFraværsdag = 1.mars,
            beregnetInntekt = 10000.månedlig,
            orgnummer = a1,
        )
        val inntekter1 = listOf(grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 10000.månedlig.repeat(3)))
        val arbeidsforhold1 = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, 1.januar, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a1, 1.januar, null, Arbeidsforholdtype.ORDINÆRT), // Skal gjelde
            Vilkårsgrunnlag.Arbeidsforhold(a1, 28.februar, 1.mars, Arbeidsforholdtype.ORDINÆRT), // Skal gjelde
            Vilkårsgrunnlag.Arbeidsforhold(a1, 1.mars, 31.mars, Arbeidsforholdtype.ORDINÆRT), // Skal ikke gjelde fordi ansettelsetidspunktet er på skjæringstidspunktet
            Vilkårsgrunnlag.Arbeidsforhold(a1, 1.februar, 28.februar, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a1, 2.mars, 31.mars, Arbeidsforholdtype.ORDINÆRT) // Gjelder ikke etter endring
        )
        val vilkårsgrunnlag = håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter1,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold1
        )
        val grunnlagsdata = vilkårsgrunnlag.grunnlagsdata()
        assertEquals(4, grunnlagsdata.inspektør.opptjening!!.arbeidsforhold.single { it.orgnummer == a1 }.ansattPerioder.size)
    }

    @Test
    fun `opphold i arbeidsforhold skal ikke behandles som ghost`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 11400.månedlig,
            orgnummer = a1
        )

        val arbeidsforhold1 = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
        )
        val inntekter1 = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 11400.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 45000.månedlig.repeat(3))
        )
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter1,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 20.februar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(2.februar, 20.februar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(2.februar til 17.februar),
            førsteFraværsdag = 2.februar,
            beregnetInntekt = 45000.månedlig,
            orgnummer = a2
        )

        val inntekter3 = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), 11400.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a2, 1.vedtaksperiode), 45000.månedlig.repeat(3))
        )
        val arbeidsforhold2 = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, 31.januar, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a1, 1.mars, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a2,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter3,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.mars til 16.mars),
            førsteFraværsdag = 1.mars,
            beregnetInntekt = 11400.månedlig,
            orgnummer = a1,
            vedtaksperiodeIdInnhenter = 2.vedtaksperiode
        )
        val inntekter2 = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 2.vedtaksperiode), 11400.månedlig.repeat(2)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 2.vedtaksperiode), 45000.månedlig.repeat(3))
        )
        håndterVilkårsgrunnlag(
            2.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter2,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold2
        )
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        val utbetaling = inspektør(a2).utbetaling(0)
        val linje = utbetaling.arbeidsgiverOppdrag.linjerUtenOpphør().single()
        assertEquals(100, inspektør(a2).vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje[20.februar].økonomi.brukTotalGrad { totalGrad -> totalGrad })
        assertEquals(
            2077,
            linje.beløp
        ) // Ikke cappet på 6G, siden personen ikke jobber hos a1 ved dette skjæringstidspunktet
        assertEquals(18.februar, linje.fom)
        assertEquals(20.februar, linje.tom)
    }

    @Test
    fun `Vedtaksperioder med flere arbeidsforhold fra Aa-reg skal ha inntektskilde FLERE_ARBEIDSGIVERE`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            orgnummer = a1,
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
        )
        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertEquals(UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
    }

    @Test
    fun `ignorerer arbeidsforhold med blanke orgnumre`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            orgnummer = a1,
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
        )
        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        assertEquals(listOf(a1, a2).map(String::toString), person.inspektør.arbeidsgivere())
    }

    @Test
    fun `Forlengelser skal ikke få warning på at arbeidsgiver ikke finnes i Aa-reg`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = INNTEKT,
            orgnummer = a1,
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = emptyList(),
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(a2, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT))
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)

        assertVarsel(RV_VV_1, 1.vedtaksperiode.filter(a1))
    }

}