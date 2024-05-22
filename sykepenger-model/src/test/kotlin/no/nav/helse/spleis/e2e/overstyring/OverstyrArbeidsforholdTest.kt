package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.etterspurtBehov
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.november
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.UtbetalingInntektskilde.EN_ARBEIDSGIVER
import no.nav.helse.person.aktivitetslogg.UtbetalingInntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_1
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsforhold
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

internal class OverstyrArbeidsforholdTest : AbstractEndToEndTest() {
    @Test
    fun `fjerner arbeidsforhold fra arbeidsforholdhistorikken ved overstyring`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017), null, Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        val skjæringstidspunkt = inspektør.skjæringstidspunkt(1.vedtaksperiode)
        val relevanteOrgnumre1: Iterable<String> = hendelselogg.etterspurtBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Godkjenning, "orgnummereMedRelevanteArbeidsforhold") ?: fail { "forventet orgnummereMedRelevanteArbeidsforhold" }
        assertEquals(listOf(a1, a2).toList(), relevanteOrgnumre1.toList())
        håndterOverstyrArbeidsforhold(skjæringstidspunkt, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        val relevanteOrgnumre2: Iterable<String> = hendelselogg.etterspurtBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Godkjenning, "orgnummereMedRelevanteArbeidsforhold") ?: fail { "forventet orgnummereMedRelevanteArbeidsforhold" }
        assertEquals(listOf(a1), relevanteOrgnumre2.toList())
    }

    @Test
    fun `Overstyring av arbeidsforhold fører til et nytt vilkårsgrunnlag med nye inntektsopplysninger`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017), null, Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        val skjæringstidspunkt = inspektør.skjæringstidspunkt(1.vedtaksperiode)
        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1431.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }
        håndterOverstyrArbeidsforhold(skjæringstidspunkt, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1431.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }

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
    fun `godtar overstyring uavhengig av rekkefølgen på arbeidsgivere`() {
        nyttVedtak(1.januar(2017) til 31.januar(2017), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar), orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a3, 1.desember(2017), null, Arbeidsforholdtype.ORDINÆRT)
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.januar, INNTEKT.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        inspektør(a2).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1080.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }
        håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a3, true, "forklaring")))
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        inspektør(a2).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1080.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }

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
    fun `kan ikke overstyre arbeidsforhold for arbeidsgiver vi ikke kjenner til`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.januar, INNTEKT.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertThrows<IllegalStateException>("Kan ikke overstyre arbeidsforhold for en arbeidsgiver vi ikke kjenner til") {
            håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a3, true, "forklaring")))
        }
    }

    @Test
    fun `deaktivering av arbeidsforhold uten sykdom fører til nytt sykepengegrunnlag uten arbeidsforholdet, selv med inntekt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a3, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a3, 1.januar, 1000.månedlig.repeat(1)) // Liten inntekt som saksbehandler ikke ser på som relevant
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1075.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }
        håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a3, true, "forklaring")))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1080.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør

        assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.inspektør.beløp)
            assertEquals(SkattSykepengegrunnlag::class, it.inntektsopplysning::class)
        }

    }

    @Test
    fun `tar med inntekt fra inntektsmelding selv om vi ikke finner et aktivt arbeidsforhold i arbeidsforholdhistorikken`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, 30.november(2017), Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a3, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a3, 1.januar, INNTEKT.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        val vilkårsgrunnlag = inspektør(a2).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør

        assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a2).inntektskilde(1.vedtaksperiode))
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a3).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.inspektør.beløp)
            assertEquals(SkattSykepengegrunnlag::class, it.inntektsopplysning::class)
        }

    }

    @Test
    fun `kan ikke overstyre arbeidsforhold dersom ingen vedtaksperioder kan håndtere hendelsen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.januar, 1000.månedlig.repeat(1))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        assertThrows<IllegalStateException> {
            håndterOverstyrArbeidsforhold(2.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        }
    }

    @Test
    fun `vi vilkårsprøver krav om minimum inntekt ved overstyring av arbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 3800.månedlig, orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, 3800.månedlig.repeat(3)),
                    grunnlag(a2, 1.januar, 300.månedlig.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør

        assertEquals(45600.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(45600.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(EN_ARBEIDSGIVER, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(EN_ARBEIDSGIVER, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(1, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(3800.månedlig, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        assertVarsel(RV_SV_1, 1.vedtaksperiode.filter(a1))
    }

    @Test
    fun `vi vilkårsprøver krav om opptjening ved overstyring av arbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT, orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, 31.desember(2017), null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, 5.januar, Arbeidsforholdtype.ORDINÆRT),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a2, 1.januar, 1000.månedlig.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(1431.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }
        håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        inspektør(a1).sisteUtbetalingUtbetalingstidslinje()[17.januar].also {
            assertEquals(0.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(0.daglig, it.økonomi.inspektør.personbeløp)
            assertEquals(31000.månedlig, it.økonomi.inspektør.aktuellDagsinntekt)
        }

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
        assertVarsel(RV_OV_1, 1.vedtaksperiode.filter(a1))
        assertInstanceOf(Utbetalingsdag.AvvistDag::class.java, inspektør.sisteUtbetalingUtbetalingstidslinje()[31.januar])
    }
}
