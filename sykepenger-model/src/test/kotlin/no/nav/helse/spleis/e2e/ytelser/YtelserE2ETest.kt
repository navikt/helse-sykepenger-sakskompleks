package no.nav.helse.spleis.e2e.ytelser

import java.time.LocalDate
import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.fredag
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_11
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_5
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_6
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_7
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_8
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertActivities
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.assertVarsler
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.søndag
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class YtelserE2ETest : AbstractEndToEndTest() {

    @Test
    fun `Foreldrepenger i halen flytter skjæringstidspunkt`() {
        nyttVedtak(1.januar til søndag(28.januar))
        // Saksbehandler overstyrer i snuten
        håndterOverstyrTidslinje((1.januar til fredag(26.januar)).map { ManuellOverskrivingDag(it, Dagtype.Foreldrepengerdag) })
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        assertEquals(27.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))

        assertForventetFeil(
            forklaring = "Burde ikke forslå andre ytelser i halen om det flytter skjæringstidspunktet",
            nå = {
                assertThrows<IllegalStateException> { håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(1.januar til søndag(28.januar), 100))) }
            },
            ønsket = {
                håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(1.januar til søndag(28.januar), 100)))
                assertTrue(inspektør.sykdomstidslinje[27.januar] is Dag.SykHelgedag)
                assertTrue(inspektør.sykdomstidslinje[28.januar] is Dag.SykHelgedag)
                assertEquals(27.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
            }
        )
    }

    @Test
    fun `perioden får warnings dersom bruker har fått Dagpenger innenfor 4 uker før skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 19.januar))
        håndterSøknad(Sykdom(3.januar, 19.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), 3.januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        assertFalse(hendelselogg.harFunksjonelleFeilEllerVerre())
        assertIngenVarsler()
        håndterYtelser(1.vedtaksperiode, dagpenger = listOf(3.januar.minusDays(14) til 5.januar.minusDays(15)))
        assertVarsler(1.vedtaksperiode.filter())
        assertIngenFunksjonelleFeil()
        assertActivities(person)
    }


    @Test
    fun `perioden får warnings dersom bruker har fått AAP innenfor 6 måneder før skjæringstidspunkt`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 19.januar))
        håndterSøknad(Sykdom(3.januar, 19.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), 3.januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        assertFalse(hendelselogg.harFunksjonelleFeilEllerVerre())
        assertIngenVarsler(1.vedtaksperiode.filter())
        håndterYtelser(1.vedtaksperiode, arbeidsavklaringspenger = listOf(3.januar.minusDays(60) til 5.januar.minusDays(60)))
        assertVarsel(Varselkode.RV_AY_3, 1.vedtaksperiode.filter())
        assertIngenFunksjonelleFeil()
        assertActivities(person)
    }

    @Test
    fun `AAP starter senere enn sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 19.januar))
        håndterSøknad(Sykdom(3.januar, 19.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, arbeidsavklaringspenger = listOf(3.februar til 5.februar))
        assertIngenVarsel(Varselkode.RV_AY_3, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Dagpenger starter senere enn sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 19.januar))
        håndterSøknad(Sykdom(3.januar, 19.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, dagpenger = listOf(3.februar til 5.februar))
        assertIngenVarsel(RV_AY_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Foreldrepenger starter mindre enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(3.februar til 20.februar, 100)))
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter())
        assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
    }

    @Test
    fun `Overlappende svangerskapspenger`() {
        håndterSøknad(Sykdom(1.januar, 19.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, svangerskapspenger = listOf(GradertPeriode(3.januar til 20.januar, 100)))
        assertVarsel(RV_AY_11)
    }

    @Test
    fun `Foreldrepenger starter mer enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(3.januar til 20.januar, 100)))
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `forlengelse trenger ikke sjekke mot 4-ukers vindu`() {
        nyttVedtak(1.januar til 31.januar)
        forlengVedtak(1.februar til 28.februar)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode,
            foreldrepenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            svangerskapspenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            omsorgspenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            opplæringspenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            pleiepenger = listOf(GradertPeriode(20.januar til 31.januar, 100))
        )
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter())
        assertVarsel(RV_AY_6, 1.vedtaksperiode.filter())
        assertVarsel(RV_AY_7, 1.vedtaksperiode.filter())
        assertVarsel(RV_AY_8, 1.vedtaksperiode.filter())
        assertVarsel(RV_AY_11, 1.vedtaksperiode.filter())
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode,
            foreldrepenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            svangerskapspenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            omsorgspenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            opplæringspenger = listOf(GradertPeriode(20.januar til 31.januar, 100)),
            pleiepenger = listOf(GradertPeriode(20.januar til 31.januar, 100))
        )
        assertIngenVarsler(2.vedtaksperiode.filter())
    }
    @Test
    fun `Omsorgspenger starter mer enn 4 uker før sykefraværstilfellet`() {
        håndterSykmelding(Sykmeldingsperiode(3.mars, 19.mars))
        håndterSøknad(Sykdom(3.mars, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(3.mars til 18.mars))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, omsorgspenger = listOf(GradertPeriode(3.januar til 20.januar, 100)))
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `Foreldrepenger før og etter sykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april))
        håndterSøknad(Sykdom(1.april, 30.april, 100.prosent))
        håndterInntektsmelding(listOf(1.april til 16.april))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(
            GradertPeriode(1.februar til 28.februar, 100),
            GradertPeriode(1.mai til 31.mai, 100) ))
        assertIngenFunksjonelleFeil()
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
    }

    @Test
    fun `Svangerskapspenger før og etter sykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.april, 30.april))
        håndterSøknad(Sykdom(1.april, 30.april, 100.prosent))
        håndterInntektsmelding(listOf(1.april til 16.april))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(
            1.vedtaksperiode,
            svangerskapspenger = listOf(
                GradertPeriode(20.februar til 28.februar, 100),
                GradertPeriode(1.mai til 31.mai, 100)
            )
        )
        assertIngenFunksjonelleFeil()
        assertIngenVarsel(RV_AY_11)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
    }

    @Test
    fun `skal ikke ha varsler om andre ytelser ved sammenhengende sykdom etter nådd maksdato`() {
        createKorttidsPerson(UNG_PERSON_FNR_2018, 1.januar(1992), maksSykedager = 11)

        nyttVedtak(1.januar til 31.januar)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(
            2.vedtaksperiode,
            arbeidsavklaringspenger = listOf(1.februar til 28.februar),
            dagpenger = listOf(1.februar til 28.februar),
        )

        assertIngenVarsler()
    }

    @Test
    fun `skal ikke ha funksjonelle feil om andre ytelser ved sammenhengende sykdom etter nådd maksdato`() {
        createKorttidsPerson(UNG_PERSON_FNR_2018, 1.januar(1992), maksSykedager = 11)

        nyttVedtak(1.januar til 31.januar)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(
            2.vedtaksperiode,
            foreldrepenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            svangerskapspenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            pleiepenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            omsorgspenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            opplæringspenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            institusjonsoppholdsperioder = listOf(Institusjonsopphold.Institusjonsoppholdsperiode(1.februar, 28.februar))
        )

        assertIngenFunksjonelleFeil()
        assertTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
    }

    @Test
    fun `skal ikke ha varsler om andre ytelser for revurdering ved sammenhengende sykdom etter nådd maksdato`() {
        createKorttidsPerson(UNG_PERSON_FNR_2018, 1.januar(1992), maksSykedager = 11)

        nyttVedtak(1.januar til 31.januar)
        forlengVedtak(1.februar til 28.februar)

        håndterSøknad(Sykdom(1.februar, 28.februar, 95.prosent))
        håndterYtelser(
            2.vedtaksperiode,
            foreldrepenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            svangerskapspenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            pleiepenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            omsorgspenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            opplæringspenger = listOf(GradertPeriode(1.februar til 28.februar, 100)),
            institusjonsoppholdsperioder = listOf(Institusjonsopphold.Institusjonsoppholdsperiode(1.februar, 28.februar)),
            arbeidsavklaringspenger = listOf(1.februar til 28.februar),
            dagpenger = listOf(1.februar til 28.februar)
        )
        assertIngenVarsler()
    }

    @Test
    fun `Var ikke permisjon i forlengelsen likevel`(){
        nyttVedtak(1.januar til 31.januar)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Søknad.Søknadsperiode.Permisjon(1.februar, 28.februar))
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomstidslinje.toShortString())
    }

    @Test
    fun `Annen ytelse i periode som tidligere var sykdom`() {
        nyPeriode(1.januar til 16.januar, orgnummer = a1)
        nyPeriode(1.januar til 16.januar, orgnummer = a2)
        nyPeriode(17.januar til 25.januar, orgnummer = a1)
        nyPeriode(17.januar til 31.januar, orgnummer = a2)
        nyPeriode(26.januar til 31.januar, orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        håndterVilkårsgrunnlag(2.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntektperioderForSykepengegrunnlag {
                1.oktober(2017) til 1.desember(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT * 0.95
                }
            }, arbeidsforhold = emptyList()),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT)
            ),
            orgnummer = a1
        )
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        håndterOverstyrTidslinje((20..31).map {  ManuellOverskrivingDag(it.januar, Dagtype.Pleiepengerdag) }, orgnummer = a1)
        håndterOverstyrTidslinje((20..31).map {  ManuellOverskrivingDag(it.januar, Dagtype.Pleiepengerdag) }, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
    }

    @Test
    fun `Fullstendig overlapp med foreldrepenger`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(1.januar til 31.januar, 100)))
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter())
        assertEquals("YYYYYYY YYYYYYY YYYYYYY YYYYYYY YYY", inspektør.sykdomstidslinje.toShortString())
    }

    @Test
    fun `graderte foreldrepenger i halen`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(30.januar til 31.januar, 50)))
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter())
        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomstidslinje.toShortString())
    }

    @Test
    fun `Overlapp med foreldrepenger i halen og utenfor perioden begrenses av perioden`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(1.januar til 10.februar, 100)))
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter())
        assertEquals("YYYYYYY YYYYYYY YYYYYYY YYYYYYY YYY", inspektør.sykdomstidslinje.toShortString())
    }

    @Test
    fun `Overlapp med foreldrepenger i halen og før perioden begrenses av perioden`() {
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterInntektsmelding(listOf(1.februar til 16.februar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, foreldrepenger = listOf(GradertPeriode(1.januar til 28.februar, 100)))
        assertVarsel(RV_AY_5, 1.vedtaksperiode.filter())
        assertEquals(1.februar, inspektør.sykdomstidslinje.førsteDag())
        assertEquals(28.februar, inspektør.sykdomstidslinje.sisteDag())
        assertEquals("YYYY YYYYYYY YYYYYYY YYYYYYY YYY", inspektør.sykdomstidslinje.toShortString())
    }
}
