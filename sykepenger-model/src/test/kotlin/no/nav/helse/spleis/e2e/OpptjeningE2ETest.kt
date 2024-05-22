package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.YearMonth
import no.nav.helse.desember
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.aktivitetslogg.UtbetalingInntektskilde.EN_ARBEIDSGIVER
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OV_3
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class OpptjeningE2ETest : AbstractEndToEndTest() {
    @Test
    fun `lagrer arbeidsforhold brukt til opptjening`() {
        personMedArbeidsforhold(Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT))
        assertHarArbeidsforhold(1.januar, a1)
        assertHarIkkeArbeidsforhold(1.januar, a2)
    }

    @Test
    fun `lagrer flere arbeidsforhold brukt til opptjening`() {
        personMedArbeidsforhold(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        assertHarArbeidsforhold(1.januar, a1)
        assertHarArbeidsforhold(1.januar, a2)
    }

    @Test
    fun `lagrer arbeidsforhold brukt til opptjening om tilstøtende`() {
        personMedArbeidsforhold(
            Vilkårsgrunnlag.Arbeidsforhold(a1, 20.desember(2017), null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, 19.desember(2017), Arbeidsforholdtype.ORDINÆRT)
        )

        assertHarArbeidsforhold(1.januar, a1)
        assertHarArbeidsforhold(1.januar, a2)
    }

    @Test
    fun `lagrer arbeidsforhold brukt til opptjening ved overlapp`() {
        personMedArbeidsforhold(
            Vilkårsgrunnlag.Arbeidsforhold(a1, 1.desember(2017), null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, 24.desember(2017), Arbeidsforholdtype.ORDINÆRT)
        )
        assertHarArbeidsforhold(1.januar, a1)
        assertHarArbeidsforhold(1.januar, a2)
    }

    @Test
    fun `opptjening er ikke oppfylt siden det ikke er nok opptjeningsdager`() {
        personMedArbeidsforhold(Vilkårsgrunnlag.Arbeidsforhold(a1, ansattFom = 31.desember(2017), ansattTom = null, Arbeidsforholdtype.ORDINÆRT))
        val grunnlagsdata = person.vilkårsgrunnlagFor(1.januar) as VilkårsgrunnlagHistorikk.Grunnlagsdata
        assertEquals(1, grunnlagsdata.opptjening.opptjeningsdager())
        assertEquals(false, grunnlagsdata.opptjening.harTilstrekkeligAntallOpptjeningsdager())
        assertVarsel(RV_OV_1, 1.vedtaksperiode.filter(orgnummer = a1))
    }

    @Test
    fun `tar ikke med inntekter fra A-Ordningen dersom arbeidsforholdet kun er brukt til opptjening og ikke gjelder under skjæringstidspunktet`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 15.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(1))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017), 31.desember(2017), Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntekter, arbeidsforhold = emptyList()),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør

        assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(EN_ARBEIDSGIVER, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(1, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
    }

    @Test
    fun `Har ikke pensjonsgivende inntekt måneden før skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
            inntekter = listOf(
                ArbeidsgiverInntekt(a1, (0..2).map {
                    val yearMonth = YearMonth.from(inspektør(a1).skjæringstidspunkt(1.vedtaksperiode)).minusMonths(3L - it)
                    ArbeidsgiverInntekt.MånedligInntekt(
                        yearMonth = yearMonth,
                        type = ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
                        inntekt = Inntekt.INGEN,
                        fordel = "fordel",
                        beskrivelse = "beskrivelse"
                    )
                })
            ), arbeidsforhold = emptyList()
        ))

        assertVarsel(RV_OV_3, 1.vedtaksperiode.filter())

        håndterYtelser()

        assertEquals(0, inspektør.utbetalinger.single().inspektør.utbetalingstidslinje.inspektør.avvistDagTeller)
    }

    private fun personMedArbeidsforhold(vararg arbeidsforhold: Vilkårsgrunnlag.Arbeidsforhold, fom: LocalDate = 1.januar, tom: LocalDate = 31.januar, vedtaksperiodeIdInnhenter: IdInnhenter = 1.vedtaksperiode) {
        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a1)
        håndterSøknad(Sykdom(fom, tom, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(fom til fom.plusDays(15)), orgnummer = a1)
        håndterVilkårsgrunnlag(vedtaksperiodeIdInnhenter, arbeidsforhold = arbeidsforhold.toList(), orgnummer = a1)
    }
    companion object {
        fun AbstractEndToEndTest.assertHarArbeidsforhold(skjæringstidspunkt: LocalDate, arbeidsforhold: String) {
            val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(skjæringstidspunkt)
            assertNotNull(vilkårsgrunnlag)
            val opptjening = vilkårsgrunnlag.inspektør.opptjening.inspektør.arbeidsforhold[arbeidsforhold]
            assertNotNull(opptjening)
            assertTrue(opptjening.isNotEmpty())
        }

        fun AbstractEndToEndTest.assertHarIkkeArbeidsforhold(skjæringstidspunkt: LocalDate, arbeidsforhold: String) {
            val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(skjæringstidspunkt)
            assertNotNull(vilkårsgrunnlag)
            assertNull(vilkårsgrunnlag.inspektør.opptjening.inspektør.arbeidsforhold[arbeidsforhold])
        }
    }
}
