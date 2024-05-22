package no.nav.helse.spleis.e2e

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.PersonObserver.ArbeidsgiveropplysningerKorrigertEvent
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class ArbeidsopplysningerKorrigertTest : AbstractEndToEndTest() {

    @Test
    fun `Sender ut spisset event ved korrigerende inntektsmelding som endrer inntekt og refusjon`() {
        nyPeriode(1.januar til 31.januar)
        val korrigertInntektsmeldingId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = 31000.månedlig,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        val korrigerendeInntektsmeldingId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(29000.månedlig, null),
        )

        val expected = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingId,
            korrigerendeInntektsopplysningId = korrigerendeInntektsmeldingId,
            korrigerendeInntektektsopplysningstype = INNTEKTSMELDING
        )
        val actual = observatør.arbeidsgiveropplysningerKorrigert.single()
        assertEquals(expected, actual)
    }

    @Test
    fun `Sender ut spisset event ved korrigerende inntektsmelding som endrer agp`() {
        nyPeriode(1.januar til 31.januar)
        val korrigertInntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        val korrigerendeInntektsmeldingId = håndterInntektsmelding(listOf(2.januar til 17.januar))

        val expected = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingId,
            korrigerendeInntektsopplysningId = korrigerendeInntektsmeldingId,
            korrigerendeInntektektsopplysningstype = INNTEKTSMELDING
        )
        val actual = observatør.arbeidsgiveropplysningerKorrigert.single()
        assertEquals(expected, actual)
    }

    @Test
    fun `Sender ut spisset event ved saksbehandleroverstyring som endrer inntekt og refusjon`() {
        nyPeriode(1.januar til 31.januar)
        val korrigertInntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        val korrigerendeInntektsopplysningId = håndterOverstyrArbeidsgiveropplysninger(
            1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    ORGNUMMER,
                    25000.månedlig,
                    "forklaring",
                    null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT))
                ))
        )

        val expected = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingId,
            korrigerendeInntektsopplysningId = korrigerendeInntektsopplysningId,
            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER
        )
        val actual = observatør.arbeidsgiveropplysningerKorrigert.single()
        assertEquals(expected, actual)
    }

    @Test
    fun `Sender ut to spissede eventer ved saksbehandleroverstyring som endrer inntekt hos to arbeidsgivere`() {
        val fom = 1.januar
        val tom = 31.januar
        nyPeriode(fom til tom, orgnummer = a1)
        nyPeriode(fom til tom, orgnummer = a2)
        val korrigertInntektsmeldingIdA1 = håndterInntektsmelding(listOf(fom til fom.plusDays(15)), orgnummer = a1)
        val korrigertInntektsmeldingIdA2 = håndterInntektsmelding(listOf(fom til fom.plusDays(15)), orgnummer = a2)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag(INNTEKT, 1.januar, a1, a2),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt()

        val korrigerendeInntektsopplysningId = håndterOverstyrArbeidsgiveropplysninger(
            1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    a1,
                    25000.månedlig,
                    "forklaring",
                    null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT))
                ),
                OverstyrtArbeidsgiveropplysning(
                    a2,
                    25000.månedlig,
                    "forklaring",
                    null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT))
                )
            )
        )
        val expectedA1 = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingIdA1,
            korrigerendeInntektsopplysningId = korrigerendeInntektsopplysningId,
            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER
        )
        val expectedA2 = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingIdA2,
            korrigerendeInntektsopplysningId = korrigerendeInntektsopplysningId,
            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER
        )
        assertEquals(expectedA1, observatør.arbeidsgiveropplysningerKorrigert[0])
        assertEquals(expectedA2, observatør.arbeidsgiveropplysningerKorrigert[1])
        assertEquals(2, observatør.arbeidsgiveropplysningerKorrigert.size)
    }

    @Test
    fun `Sender ut ett spisset event ved saksbehandleroverstyring som endrer inntekt hos en av to arbeidsgivere`() {
        val fom = 1.januar
        val tom = 31.januar
        nyPeriode(fom til tom, orgnummer = a1)
        nyPeriode(fom til tom, orgnummer = a2)
        val korrigertInntektsmeldingIdA1 = håndterInntektsmelding(listOf(fom til fom.plusDays(15)), orgnummer = a1)
        håndterInntektsmelding(listOf(fom til fom.plusDays(15)), orgnummer = a2)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = inntektsvurderingForSykepengegrunnlag(INNTEKT, 1.januar, a1, a2),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt()

        val korrigerendeInntektsopplysningId = håndterOverstyrArbeidsgiveropplysninger(
            1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    a1,
                    25000.månedlig,
                    "forklaring",
                    null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT))
                )
            )
        )
        val expected = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingIdA1,
            korrigerendeInntektsopplysningId = korrigerendeInntektsopplysningId,
            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER
        )
        assertEquals(expected, observatør.arbeidsgiveropplysningerKorrigert.first())
        assertEquals(1, observatør.arbeidsgiveropplysningerKorrigert.size)
    }

    @Test
    fun `Sender ut spisset event ved saksbehandleroverstyring som endrer agp`() {
        nyPeriode(1.januar til 31.januar)
        val korrigertInntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        val korrigerendeInntektsopplysningId = håndterOverstyrTidslinje(
            listOf(
                ManuellOverskrivingDag(
                    31.desember(2017),
                    Dagtype.Egenmeldingsdag
                )
            )
        ).meldingsreferanseId()
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        val expected = ArbeidsgiveropplysningerKorrigertEvent(
            korrigertInntektsmeldingId = korrigertInntektsmeldingId,
            korrigerendeInntektsopplysningId = korrigerendeInntektsopplysningId,
            korrigerendeInntektektsopplysningstype = SAKSBEHANDLER
        )
        val actual = observatør.arbeidsgiveropplysningerKorrigert.single()
        assertEquals(expected, actual)
    }

    @Test
    fun `Sender ikke ut spisset event ved tidslinjeoverstyring som ikke fører til en endring av agp`() {
        nyttVedtak(1.januar til 31.januar)
        håndterOverstyrTidslinje(
            listOf(
                ManuellOverskrivingDag(
                    10.januar,
                    Dagtype.Feriedag
                )
            )
        ).meldingsreferanseId()
        håndterYtelser(1.vedtaksperiode)
        val actual = observatør.arbeidsgiveropplysningerKorrigert
        assertEquals(0, actual.size)
    }

    @Test
    fun `Sender ut spisset event ved korrigerende inntektsmelding som ikke fører til endring`() {
        nyPeriode(1.januar til 31.januar)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT,
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT,
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null),
        )
        håndterOverstyrArbeidsgiveropplysninger(
            1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    a1,
                    INNTEKT,
                    "forklaring",
                    null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT))
                )
            )
        )
        assertFalse(observatør.arbeidsgiveropplysningerKorrigert.isEmpty())
    }

    @Test
    fun `Sender ikke ut spisset event ved korrigerende inntektsmelding som korrigerer noe annet enn en inntektsmelding`() {
        nyttVedtak(1.januar til 31.januar)
        håndterOverstyrArbeidsgiveropplysninger(
            1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(
                    ORGNUMMER,
                    40000.månedlig,
                    "forklaring",
                    null,
                    refusjonsopplysninger = listOf(Triple(1.januar, null, INNTEKT))
                )
            )
        )
        assertEquals(1, observatør.arbeidsgiveropplysningerKorrigert.size)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 41000.månedlig)
        assertEquals(1, observatør.arbeidsgiveropplysningerKorrigert.size)
    }
}