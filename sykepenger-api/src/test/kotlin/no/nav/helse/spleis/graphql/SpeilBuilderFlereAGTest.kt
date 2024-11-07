package no.nav.helse.spleis.graphql

import java.time.LocalDate.EPOCH
import java.time.Month
import java.time.YearMonth
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.oktober
import no.nav.helse.spleis.speil.dto.Arbeidsgiverinntekt
import no.nav.helse.spleis.speil.dto.BeregnetPeriode
import no.nav.helse.spleis.speil.dto.GhostPeriodeDTO
import no.nav.helse.spleis.speil.dto.Inntekt
import no.nav.helse.spleis.speil.dto.InntekterFraAOrdningen
import no.nav.helse.spleis.speil.dto.Inntektkilde
import no.nav.helse.spleis.speil.dto.SpleisVilkårsgrunnlag
import no.nav.helse.spleis.testhelpers.OverstyrtArbeidsgiveropplysning
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SpeilBuilderFlereAGTest : AbstractE2ETest() {

    @Test
    fun `lager ikke hvit pølse i helg`() {
        håndterSøknad(1.januar til 19.januar)
        håndterSøknad(22.januar til 31.januar)
        håndterInntektsmelding(1.januar)
        håndterVilkårsgrunnlagTilGodkjenning()

        val speilJson = speilApi()
        assertEquals(emptyList<GhostPeriodeDTO>(), speilJson.arbeidsgivere.single().ghostPerioder)
    }

    @Test
    fun `sender med ghost tidslinjer til speil`() {
        håndterSøknad(1.januar til 20.januar, orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()

        val speilJson = speilApi()
        assertEquals(
            emptyList<GhostPeriodeDTO>(),
            speilJson.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder
        )


        val perioder = speilJson.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId
        assertEquals(1, perioder.size)
        val actual = perioder.first()
        val expected = GhostPeriodeDTO(
            id = actual.id,
            fom = 1.januar,
            tom = 20.januar,
            skjæringstidspunkt = 1.januar,
            vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
            deaktivert = false
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `sender med ghost tidslinjer til speil med flere arbeidsgivere ulik fom`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(4.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterInntektsmelding(listOf(4.januar til 19.januar), orgnummer = a2, beregnetInntekt = 5000.månedlig)

        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to INNTEKT, a2 to 5000.månedlig, a3 to 10000.månedlig),
            arbeidsforhold = listOf(a1 to EPOCH, a3 to EPOCH)
        )
        håndterYtelserTilGodkjenning()

        val speilJson1 = speilApi()
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId

        speilJson1.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder.also { ghostPerioder ->
            assertEquals(1, ghostPerioder.size)
            ghostPerioder[0].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 21.januar,
                    tom = 31.januar,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
        }

        speilJson1.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder.also { ghostPerioder ->
            assertEquals(1, ghostPerioder.size)
            ghostPerioder[0].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 1.januar,
                    tom = 3.januar,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
        }

        speilJson1.arbeidsgivere.single { it.organisasjonsnummer == a3 }.ghostPerioder.also { perioder ->
            assertEquals(1, perioder.size)
            val actual = perioder.first()
            val expected = GhostPeriodeDTO(
                id = actual.id,
                fom = 1.januar,
                tom = 31.januar,
                skjæringstidspunkt = 1.januar,
                vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                deaktivert = false
            )
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `sender med ghost tidslinjer til speil med flere arbeidsgivere ulik skjæringstidspunkt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(25.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 24.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(29.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(1.januar, orgnummer = a1, vedtaksperiode = 1)
        håndterInntektsmelding(1.januar, beregnetInntekt = 5000.månedlig, orgnummer = a2, vedtaksperiode = 1)
        håndterInntektsmelding(29.januar, beregnetInntekt = 5000.månedlig, orgnummer = a2, vedtaksperiode = 2)

        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to INNTEKT, a2 to 5000.månedlig, a3 to 10000.månedlig),
            arbeidsforhold = listOf(a1 to EPOCH, a3 to EPOCH)
        )
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to INNTEKT, a2 to 5000.månedlig, a3 to 10000.månedlig),
            arbeidsforhold = listOf(a1 to EPOCH, a3 to EPOCH)
        )
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        val speilJson1 = speilApi()
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId

        speilJson1.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder.also { ghostPerioder ->
            assertEquals(0, ghostPerioder.size)
        }

        speilJson1.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder.also { ghostPerioder ->
            assertEquals(1, ghostPerioder.size)
            ghostPerioder[0].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 25.januar,
                    tom = 28.januar,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun `lager ikke ghosts for forkastede perioder med vilkårsgrunnlag fra spleis`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()

        val speilJson = speilApi()
        assertEquals(emptyList<GhostPeriodeDTO>(), speilJson.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder)
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.februar }.vilkårsgrunnlagId
        val perioder = speilJson.arbeidsgivere.find { it.organisasjonsnummer == a2 }?.ghostPerioder
        assertEquals(1, perioder?.size)
        val actual = perioder!!.first()
        val expected = GhostPeriodeDTO(
            id = actual.id,
            fom = 1.februar,
            tom = 20.februar,
            skjæringstidspunkt = 1.februar,
            vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
            deaktivert = false
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `skal ikke lage ghosts for gamle arbeidsgivere`() {
        håndterSøknad(Sykdom(1.januar(2017), 20.januar(2017), 100.prosent), sendtTilNAV = 20.januar(2017).atStartOfDay(), orgnummer = a3)
        håndterInntektsmelding(1.januar(2017), orgnummer = a3)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a3 to INNTEKT))
        håndterYtelserTilUtbetalt()

        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()

        val speilJson = speilApi()
        assertEquals(emptyList<GhostPeriodeDTO>(), speilJson.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder)
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId
        val perioder = speilJson.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder
        assertEquals(1, perioder.size)
        val actual = perioder.first()
        val expected = GhostPeriodeDTO(
            id = actual.id,
            fom = 1.januar,
            tom = 20.januar,
            skjæringstidspunkt = 1.januar,
            vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
            deaktivert = false
        )

        assertEquals(expected, actual)
        assertEquals(emptyList<GhostPeriodeDTO>(), speilJson.arbeidsgivere.single { it.organisasjonsnummer == a3 }.ghostPerioder)
    }

    @Test
    fun `ghost periode kuttes ved skjæringstidspunkt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(3.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()
        val speilJson = speilApi()
        assertEquals(
            emptyList<GhostPeriodeDTO>(),
            speilJson.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder
        )

        val perioder = speilJson.arbeidsgivere.find { it.organisasjonsnummer == a2 }?.ghostPerioder
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 3.januar }.vilkårsgrunnlagId
        assertEquals(1, perioder?.size)

        val actual = perioder!!.first()
        val expected = GhostPeriodeDTO(
            id = actual.id,
            fom = 3.januar,
            tom = 31.januar,
            skjæringstidspunkt = 3.januar,
            vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
            deaktivert = false
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `arbeidsforhold uten sykepengegrunnlag de tre siste månedene før skjæringstidspunktet skal ikke ha ghostperioder`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to INNTEKT),
            arbeidsforhold = listOf(a1 to EPOCH, a2 to EPOCH)
        )
        håndterYtelserTilGodkjenning()
        val personDto = speilApi()
        val ghostpølser = personDto.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder
        assertEquals(0, ghostpølser.size)
    }

    @Test
    fun `legger ved sammenlignignsgrunnlag og sykepengegrunnlag for deaktiverte arbeidsforhold`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 1000.månedlig))
        håndterYtelserTilGodkjenning()

        håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelserTilGodkjenning()

        val personDto = speilApi()
        val vilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[vilkårsgrunnlagId]

        assertEquals(listOf(a1, a2), vilkårsgrunnlag?.inntekter?.map { it.organisasjonsnummer })
        assertEquals(
            Arbeidsgiverinntekt(
                organisasjonsnummer = a2,
                omregnetÅrsinntekt = Inntekt(
                    kilde = Inntektkilde.AOrdningen,
                    beløp = 12000.0,
                    månedsbeløp = 1000.0,
                    inntekterFraAOrdningen = listOf(
                        InntekterFraAOrdningen(YearMonth.of(2017, Month.OCTOBER), 1000.0),
                        InntekterFraAOrdningen(YearMonth.of(2017, Month.NOVEMBER), 1000.0),
                        InntekterFraAOrdningen(YearMonth.of(2017, Month.DECEMBER), 1000.0)
                    )
                ),
                fom = 1.januar,
                tom = null,
                deaktivert = true
            ),
            vilkårsgrunnlag?.inntekter?.find { it.organisasjonsnummer == a2 }
        )
    }

    @Test
    fun `deaktiverte arbeidsforhold vises i speil selvom sammenligninggrunnlag og sykepengegrunnlag ikke er rapportert til A-ordningen enda`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to INNTEKT),
            arbeidsforhold = listOf(a1 to EPOCH, a2 to 1.desember(2017))
        )
        håndterYtelserTilGodkjenning()

        håndterOverstyrArbeidsforhold(1.januar, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelserTilGodkjenning()

        val personDto = speilApi()
        val vilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[vilkårsgrunnlagId]

        assertEquals(listOf(a1, a2), vilkårsgrunnlag?.inntekter?.map { it.organisasjonsnummer })
        assertTrue(personDto.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder.isNotEmpty())
    }

    @Test
    fun `deaktivert arbeidsforhold blir med i vilkårsgrunnlag`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(
            arbeidsforhold = listOf(a1 to EPOCH, a2 to 1.desember(2017))
        )
        håndterYtelserTilGodkjenning()
        val skjæringstidspunkt = 1.januar
        håndterOverstyrArbeidsforhold(skjæringstidspunkt, listOf(OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        håndterYtelserTilGodkjenning()

        val personDto = speilApi()
        val vilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[vilkårsgrunnlagId]

        val forventet = listOf(
            Arbeidsgiverinntekt(
                organisasjonsnummer = a1,
                omregnetÅrsinntekt = Inntekt(
                    kilde = Inntektkilde.Inntektsmelding,
                    beløp = 576000.0,
                    månedsbeløp = 48000.0,
                    inntekterFraAOrdningen = null
                ),
                fom = 1.januar,
                tom = null,
                deaktivert = false
            ),
            Arbeidsgiverinntekt(
                organisasjonsnummer = a2,
                omregnetÅrsinntekt = Inntekt(
                    kilde = Inntektkilde.IkkeRapportert,
                    beløp = 0.0,
                    månedsbeløp = 0.0,
                    inntekterFraAOrdningen = null
                ),
                fom = 1.januar,
                tom = null,
                deaktivert = true
            )
        )
        assertEquals(forventet, vilkårsgrunnlag?.inntekter)
    }

    @Test
    fun `Skal ikke ta med inntekt på vilkårsgrunnlaget som mangler både sykepengegrunnlag og sammenligningsgrunnlag på skjæringstidspunktet`() {
        nyttVedtak(1.januar(2017), 31.januar(2017), orgnummer = a2)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsforhold = listOf(a1 to 1.oktober(2017)))
        håndterYtelserTilGodkjenning()

        val personDto = speilApi()
        val vilkårsgrunnlagId =
            (personDto.arbeidsgivere.find { it.organisasjonsnummer == a1 }!!.generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[vilkårsgrunnlagId]
        assertEquals(listOf(a1), vilkårsgrunnlag?.inntekter?.map { it.organisasjonsnummer })
        assertEquals(listOf(a2, a1), personDto.arbeidsgivere.map { it.organisasjonsnummer })
    }

    @Test
    fun `Ghostpølse forsvinner ikke etter overstyring av ghost-inntekt`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a2, 9000.månedlig, "")))
        håndterYtelserTilGodkjenning()

        val speilJson = speilApi()
        val perioder = speilJson.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId
        assertEquals(1, perioder.size)
        val actual = perioder.first()
        val expected = GhostPeriodeDTO(
            id = actual.id,
            fom = 1.januar,
            tom = 20.januar,
            skjæringstidspunkt = 1.januar,
            vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
            deaktivert = false
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Ghosten finnes ikke i vilkårsgrunnlaget`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelser()

        val speilJson = speilApi()
        val vilkårsgrunnlag = speilJson.vilkårsgrunnlag
        assertEquals(1, vilkårsgrunnlag.size)

        val arbeidsgiverA1 = speilJson.arbeidsgivere.singleOrNull { it.organisasjonsnummer == a1 }
        assertEquals(1, arbeidsgiverA1?.generasjoner?.size)
        assertEquals(1, arbeidsgiverA1?.generasjoner?.single()?.perioder?.size)
        val beregnetPeriode = arbeidsgiverA1?.generasjoner?.single()?.perioder?.single()
        assertInstanceOf(BeregnetPeriode::class.java, beregnetPeriode)
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId
        val arbeidsgiverA2 = speilJson.arbeidsgivere.singleOrNull { it.organisasjonsnummer == a2 }
        assertEquals(0, arbeidsgiverA2?.generasjoner?.size)
        val perioder = arbeidsgiverA2?.ghostPerioder
        val actual = perioder?.single()!!
        assertEquals(
            GhostPeriodeDTO(
                id = actual.id,
                fom = 1.januar,
                tom = 20.januar,
                skjæringstidspunkt = 1.januar,
                vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                deaktivert = false
            ),
            actual
        )
    }

    @Test
    fun `lager ikke Ghostperiode på et vilkårsgrunnlag som ingen beregnede perioder peker på`() {
        håndterSøknad(1.januar til 16.januar)
        håndterSøknad(17.januar til 31.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        val personDto = speilApi()
        val ghostPerioder = personDto.arbeidsgivere.singleOrNull { it.organisasjonsnummer == a2 }?.ghostPerioder
        assertNull(ghostPerioder)
    }

    @Test
    fun `Finner riktig ghostpølse etter overstyring av ghost-inntekt selvom begge arbeidsgiverne har saksbehandlerinntekt`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to 10000.månedlig))
        håndterYtelserTilGodkjenning()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, 30000.månedlig, "")))
        håndterYtelserTilGodkjenning()

        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a2, 9000.månedlig, "")))
        håndterYtelserTilGodkjenning()

        val speilJson = speilApi()
        val perioder = speilJson.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId
        assertEquals(1, perioder.size)
        val actual = perioder.first()
        val expected = GhostPeriodeDTO(
            id = actual.id,
            fom = 1.januar,
            tom = 20.januar,
            skjæringstidspunkt = 1.januar,
            vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
            deaktivert = false
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `ghost-perioder før og etter søknad`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)

        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to INNTEKT))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)

        val speilJson = speilApi()
        val perioder = speilJson.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder

        assertEquals(2, perioder.size)
        val skjæringstidspunkt = 1.januar
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId
        perioder[0].also { actual ->
            val expected = GhostPeriodeDTO(
                id = actual.id,
                fom = 1.januar,
                tom = 31.januar,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                deaktivert = false
            )
            assertEquals(expected, actual)
        }
        perioder[1].also { actual ->
            val expected = GhostPeriodeDTO(
                id = actual.id,
                fom = 1.mars,
                tom = 31.mars,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                deaktivert = false
            )
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `refusjon for flere arbeidsgivere`() {
        nyeVedtak(1.januar, 31.januar, a1 to 1, a2 to 1)

        val personDto = speilApi()
        val speilVilkårsgrunnlagIdForAG1 = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val speilVilkårsgrunnlagIdForAG2 = (personDto.arbeidsgivere.last().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[speilVilkårsgrunnlagIdForAG1] as? SpleisVilkårsgrunnlag
        val vilkårsgrunnlag2 = personDto.vilkårsgrunnlag[speilVilkårsgrunnlagIdForAG2] as? SpleisVilkårsgrunnlag
        assertEquals(vilkårsgrunnlag, vilkårsgrunnlag2)

        assertTrue(vilkårsgrunnlag!!.arbeidsgiverrefusjoner.isNotEmpty())
        val arbeidsgiverrefusjonForAG1 = vilkårsgrunnlag.arbeidsgiverrefusjoner.find { it.arbeidsgiver == a1 }!!
        val arbeidsgiverrefusjonForAG2 = vilkårsgrunnlag.arbeidsgiverrefusjoner.find { it.arbeidsgiver == a2 }!!

        val refusjonsopplysningerForAG1 = arbeidsgiverrefusjonForAG1.refusjonsopplysninger.single()
        val refusjonsopplysningerForAG2 = arbeidsgiverrefusjonForAG2.refusjonsopplysninger.single()

        assertEquals(1.januar, refusjonsopplysningerForAG1.fom)
        assertEquals(null, refusjonsopplysningerForAG1.tom)
        assertEquals(48000.månedlig,refusjonsopplysningerForAG1.beløp.månedlig)
        assertEquals(1.januar, refusjonsopplysningerForAG2.fom)
        assertEquals(null, refusjonsopplysningerForAG2.tom)
        assertEquals(48000.månedlig,refusjonsopplysningerForAG2.beløp.månedlig)
    }

    @Test
    fun `flere førstegangsaker for begge arbeidsgivere med samme skjæringstidspunkt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(12.februar, 12.mars, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(17.januar, 26.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(13.mars, 31.mars, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(17.januar til 26.januar, 1.februar til 6.februar), orgnummer = a2)
        håndterInntektsmelding(listOf(17.januar til 26.januar, 1.februar til 6.februar), orgnummer = a2)

        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to INNTEKT))
        håndterYtelserTilGodkjenning()

        val speilJson = speilApi()
        val spleisVilkårsgrunnlagId = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.single { it.skjæringstidspunkt == 1.januar }.vilkårsgrunnlagId

        speilJson.arbeidsgivere.single { it.organisasjonsnummer == a1 }.ghostPerioder.also { perioder ->
            assertEquals(2, perioder.size)

            perioder[0].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 1.februar,
                    tom = 11.februar,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
            perioder[1].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 13.mars,
                    tom = 31.mars,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
        }
        speilJson.arbeidsgivere.single { it.organisasjonsnummer == a2 }.ghostPerioder.also { perioder ->
            assertEquals(2, perioder.size)

            perioder[0].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 1.januar,
                    tom = 16.januar,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
            perioder[1].also { actual ->
                val expected = GhostPeriodeDTO(
                    id = actual.id,
                    fom = 1.mars,
                    tom = 12.mars,
                    skjæringstidspunkt = 1.januar,
                    vilkårsgrunnlagId = spleisVilkårsgrunnlagId,
                    deaktivert = false
                )
                assertEquals(expected, actual)
            }
        }
    }
}

