package no.nav.helse.spleis.e2e.refusjon

import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.forlengVedtak
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RefusjonsopplysningerE2ETest : AbstractDslTest() {

    @Test
    fun `første fraværsdag oppgitt til dagen etter arbeidsgiverperioden`(){
        a1 {
            nyttVedtak(førsteFraværsdag = 17.januar, arbeidsgiverperiode = listOf(1.januar til 16.januar), periode = januar)
            assertIngenInfoSomInneholder("Mangler refusjonsopplysninger på orgnummer")
        }
    }

    @Test
    fun `første fraværsdag oppgitt til dagen etter arbeidsgiverperioden over helg`(){
        a1 {
            nyttVedtak(førsteFraværsdag = 22.januar, arbeidsgiverperiode = listOf(4.januar til 19.januar), periode = 4.januar til 31.januar)
            assertIngenInfoSomInneholder("Mangler refusjonsopplysninger på orgnummer")
        }
    }

    @Test
    fun `lager nytt innslag i vilkårsgrunnlaghistorikken med oppdaterte refusjonsopplysninger ved ny inntektsmelding`() {
        a1 {
            val arbeidsgiverperiode = listOf(1.januar til 16.januar)
            nyttVedtak(januar, arbeidsgiverperiode = arbeidsgiverperiode, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, opphørsdato = null))
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(januar, INNTEKT)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            håndterInntektsmelding(arbeidsgiverperioder = arbeidsgiverperiode, førsteFraværsdag = 22.januar, refusjon = Inntektsmelding.Refusjon(beløp = INGEN, opphørsdato = null))
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().let { refusjonsopplysninger ->
                refusjonsopplysninger.assertRefusjonsbeløp(1.januar til 21.januar, INNTEKT)
                refusjonsopplysninger.assertRefusjonsbeløp(22.januar til 31.januar, INGEN)
            }
        }
    }

    @Test
    fun `Duplikat innhold i ny inntektsmelding`() {
        a1 {
            val arbeidsgiverperiode = listOf(1.januar til 16.januar)
            nyttVedtak(januar, arbeidsgiverperiode = arbeidsgiverperiode, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, opphørsdato = null))
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(januar, INNTEKT)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            håndterInntektsmelding(arbeidsgiverperioder = arbeidsgiverperiode, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(beløp = INNTEKT, opphørsdato = null))
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(januar, INNTEKT)
        }
    }
    @Test
    fun `Duplikat inntektsmelding`() {
        a1 {
            val arbeidsgiverperiode = listOf(1.januar til 16.januar)
            håndterSykmelding(januar)
            håndterSøknad(januar)
            val inntektsmeldingId = håndterInntektsmelding(arbeidsgiverperiode)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(januar, INNTEKT)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
            håndterSøknad(februar)
            håndterInntektsmelding(
                id = inntektsmeldingId,
                arbeidsgiverperioder = arbeidsgiverperiode,
            )
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(januar, INNTEKT)
        }
    }


    @Test
    fun `Inntektsmelding uten refusjonsopplysninger tolkes som ingen refusjon`() {
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(januar)
            assertEquals(0, inspektør.arbeidsgiver.inspektør.refusjonshistorikk.inspektør.antall)
            assertEquals(emptyList<Refusjonsopplysning>(), inspektør.refusjonsopplysningerFraRefusjonshistorikk(1.januar).inspektør.refusjonsopplysninger)
            val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), refusjon = Inntektsmelding.Refusjon(null, null, emptyList()), beregnetInntekt = INNTEKT)
            assertEquals(1, inspektør.arbeidsgiver.inspektør.refusjonshistorikk.inspektør.antall)
            assertEquals(listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INGEN)), inspektør.refusjonsopplysningerFraRefusjonshistorikk(1.januar).inspektør.refusjonsopplysninger)
        }
    }

    @Test
    fun `godtar refusjonsopplysninger selv med oppholdsdager i snuten`() {
        listOf(a1, a2).nyeVedtak(1.desember(2017) til 28.desember(2017))

        a2 {
            forlengVedtak(29.desember(2017) til 10.januar)
        }
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(1.januar, 1.januar))
            håndterInntektsmelding(listOf(), førsteFraværsdag = 1.januar)
        }
        a2 {
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        }
        a1 {
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
        }
    }


    @Test
    fun `godtar refusjonsopplysninger selv med oppholdsdager i snuten take two`() {
        listOf(a1, a2).nyeVedtak(1.desember(2017) til 28.desember(2017))

        a2 {
            forlengVedtak(29.desember(2017) til 10.januar)
        }
        a1 {
            håndterSykmelding(januar)
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(1.januar, 1.januar))
            håndterInntektsmelding(listOf(1.desember(2017) til 16.desember(2017)), førsteFraværsdag = 2.januar)

            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)

            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        }
        a1 {
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
        }
    }


    @Test
    fun `godtar refusjonsopplysninger selv med oppholdsdager i snuten men hva med egenmeldinger da`() {
        listOf(a1, a2).nyeVedtak(1.desember(2017) til 28.desember(2017), inntekt = INNTEKT)

        a2 {
            forlengVedtak(29.desember(2017) til 10.januar)
        }
        a1 {
            håndterSykmelding(Sykmeldingsperiode(2.januar, 31.januar))
            håndterSøknad(Sykdom(2.januar, 31.januar, 100.prosent), egenmeldinger = listOf(29.desember(2017) til 29.desember(2017)))
            håndterInntektsmeldingPortal(førsteFraværsdag = 2.januar, arbeidsgiverperioder = emptyList(), inntektsdato = 1.desember(2017))

            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        }
        a1 {
            assertForventetFeil("vet ikke",
                nå = {
                    assertIngenVarsler(2.vedtaksperiode.filter())
                    assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
                },
                ønsket = {
                    assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
                })
        }
    }

    private fun Refusjonsopplysninger.assertRefusjonsbeløp(periode: Periode, beløp: Inntekt) {
        periode.forEach { dag ->
            assertEquals(beløp, refusjonsbeløpOrNull(dag))
        }
    }
}