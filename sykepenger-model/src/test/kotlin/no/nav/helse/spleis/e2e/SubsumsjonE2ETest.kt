package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.YearMonth
import no.nav.helse.Personidentifikator
import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.etterlevelse.Bokstav
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_A
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_B
import no.nav.helse.etterlevelse.FOLKETRYGDLOVENS_OPPRINNELSESDATO
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Ledd.Companion.ledd
import no.nav.helse.etterlevelse.Ledd.LEDD_1
import no.nav.helse.etterlevelse.Ledd.LEDD_2
import no.nav.helse.etterlevelse.Ledd.LEDD_3
import no.nav.helse.etterlevelse.Paragraf
import no.nav.helse.etterlevelse.Paragraf.KJENNELSE_2006_4023
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_22_13
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_10
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_11
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_12
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_13
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_15
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_16
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_17
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_19
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_2
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_28
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_29
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_3
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_48
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_51
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_9
import no.nav.helse.etterlevelse.Punktum.Companion.punktum
import no.nav.helse.etterlevelse.Subsumsjon.Utfall
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_IKKE_OPPFYLT
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_OPPFYLT
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsmelding.Avsendersystem.ALTINN
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Utlandsopphold
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.SubsumsjonInspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType
import no.nav.helse.september
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubsumsjonE2ETest : AbstractEndToEndTest() {

    @Test
    fun `subsummerer ikke inntektsspesfikke subsumsjoner ved overstyring som ikke fører til endrede inntekter i sykpengegrunnlaget`() {
        håndterSøknad(januar, orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            )
        )
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_28, ledd = LEDD_3, bokstav = BOKSTAV_A, versjon = 1.januar(2019)))
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_29, versjon = 1.januar(2019)))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a2, INNTEKT * 1.1, Subsumsjon("8-28", 3, "b"))))

        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_28, ledd = LEDD_3, bokstav = BOKSTAV_A, versjon = 1.januar(2019)))
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_29, versjon = 1.januar(2019)))
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_28, ledd = LEDD_3, bokstav = BOKSTAV_B, versjon = 1.januar(2019)))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSøknad(februar, orgnummer = a2)

        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2, beregnetInntekt = INNTEKT)
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_28, ledd = LEDD_3, bokstav = BOKSTAV_A, versjon = 1.januar(2019)))
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_29, versjon = 1.januar(2019)))
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(paragraf = PARAGRAF_8_28, ledd = LEDD_3, bokstav = BOKSTAV_B, versjon = 1.januar(2019)))
    }

    @Test
    fun `§ 8-2 ledd 1 - opptjeningstid tilfredstilt`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 4.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_2,
            ledd = LEDD_1,
            versjon = 12.juni(2020),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "tilstrekkeligAntallOpptjeningsdager" to 28,
                "arbeidsforhold" to listOf(
                    mapOf(
                        "orgnummer" to ORGNUMMER,
                        "fom" to 4.desember(2017),
                        "tom" to 31.januar
                    )
                )
            ),
            output = mapOf("antallOpptjeningsdager" to 28)
        )
    }

    @Test
    fun `§ 8-2 ledd 1 - opptjeningstid ikke tilfredstilt`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_2,
            ledd = LEDD_1,
            versjon = 12.juni(2020),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "tilstrekkeligAntallOpptjeningsdager" to 28,
                "arbeidsforhold" to listOf(
                    mapOf(
                        "orgnummer" to ORGNUMMER,
                        "fom" to 5.desember(2017),
                        "tom" to 31.januar
                    )
                )
            ),
            output = mapOf("antallOpptjeningsdager" to 27)
        )
    }

    @Test
    fun `§ 8-3 ledd 1 punktum 2 - fyller 70`() {
        val fnr = Personidentifikator("20014835841")
        createTestPerson(fnr, 20.januar(1948))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), fnr = fnr)
        håndterSøknad(januar, fnr = fnr)
        håndterInntektsmelding(listOf(1.januar til 16.januar), fnr = fnr)
        håndterVilkårsgrunnlag(fnr = fnr)
        håndterYtelser(fnr = fnr)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_1,
            punktum = 2.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "syttiårsdagen" to 20.januar,
                "utfallFom" to 1.januar,
                "utfallTom" to 19.januar,
                "tidslinjeFom" to 1.januar,
                "tidslinjeTom" to 31.januar
            ),
            output = mapOf(
                "avvisteDager" to emptyList<Periode>()
            )
        )

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_1,
            punktum = 2.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "syttiårsdagen" to 20.januar,
                "utfallFom" to 20.januar,
                "utfallTom" to 31.januar,
                "tidslinjeFom" to 1.januar,
                "tidslinjeTom" to 31.januar
            ),
            output = mapOf(
                "avvisteDager" to listOf(20.januar til 31.januar)
            )
        )
    }

    @Test
    fun `§ 8-3 ledd 1 punktum 2 - blir aldri 70`() {
        val fnr = Personidentifikator("01024835841")
        createTestPerson(fnr, 1.februar(1948))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), fnr = fnr)
        håndterSøknad(januar, fnr = fnr)
        håndterInntektsmelding(listOf(1.januar til 16.januar), fnr = fnr)
        håndterVilkårsgrunnlag(fnr = fnr)
        håndterYtelser(fnr = fnr)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_1,
            punktum = 2.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "syttiårsdagen" to 1.februar,
                "utfallFom" to 1.januar,
                "utfallTom" to 31.januar,
                "tidslinjeFom" to 1.januar,
                "tidslinjeTom" to 31.januar
            ),
            output = mapOf(
                "avvisteDager" to emptyList<Periode>()
            )
        )
    }

    @Test
    fun `§ 8-3 ledd 1 punktum 2 - er alltid 70`() {
        val fnr = Personidentifikator("01014835841")
        createTestPerson(fnr, 1.januar(1948))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), fnr = fnr)
        håndterSøknad(januar, fnr = fnr)
        håndterInntektsmelding(listOf(1.januar til 16.januar), fnr = fnr)
        håndterVilkårsgrunnlag(fnr = fnr)
        håndterYtelser(fnr = fnr)

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_1,
            punktum = 2.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "syttiårsdagen" to 1.januar,
                "utfallFom" to 1.januar,
                "utfallTom" to 31.januar,
                "tidslinjeFom" to 1.januar,
                "tidslinjeTom" to 31.januar
            ),
            output = mapOf(
                "avvisteDager" to listOf(17.januar til 31.januar)
            )
        )
    }

    @Test
    fun `§ 8-3 ledd 1 punktum 2 - er alltid 70 uten NAVdager`() {
        val fnr = Personidentifikator("01014835841")
        createTestPerson(fnr, 1.januar(1948))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar), fnr = fnr)
        håndterSøknad(1.januar til 16.januar, fnr = fnr)
        håndterInntektsmelding(listOf(1.januar til 16.januar), fnr = fnr)

        assertForventetFeil(
            forklaring = "Perioden avsluttes automatisk -- usikker på hva vi ønsker av etterlevelse da",
            nå = {
                assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET_UTEN_UTBETALING)
                SubsumsjonInspektør(jurist).assertIkkeVurdert(paragraf = PARAGRAF_8_3)
            },
            ønsket = {
                håndterYtelser(fnr = fnr)
                håndterVilkårsgrunnlag(fnr = fnr)
                håndterYtelser(fnr = fnr)

                SubsumsjonInspektør(jurist).assertIkkeOppfylt(
                    paragraf = PARAGRAF_8_3,
                    ledd = LEDD_1,
                    punktum = 2.punktum,
                    versjon = 16.desember(2011),
                    input = mapOf(
                        "syttiårsdagen" to 1.januar,
                        "utfallFom" to 1.januar,
                        "utfallTom" to 16.januar,
                        "tidslinjeFom" to 1.januar,
                        "tidslinjeTom" to 16.januar
                    ),
                    output = mapOf(
                        "avvisteDager" to emptyList<Periode>()
                    )
                )
            }
        )
    }

    @Test
    fun `§ 8-3 ledd 2 punktum 1 - har minimum inntekt halv G`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 46817.årlig)
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "grunnlagForSykepengegrunnlag" to 46817.0,
                "minimumInntekt" to 46817.0
            ),
            output = emptyMap()
        )
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_51, LEDD_2, 1.punktum)
    }

    @Test
    fun `§ 8-3 ledd 2 punktum 1 - har minimum inntekt halv G - også ved overstyring`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 46817.årlig)
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.november(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold, inntekt = 50000.årlig)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "grunnlagForSykepengegrunnlag" to 46817.0,
                "minimumInntekt" to 46817.0
            ),
            output = emptyMap()
        )
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_51, LEDD_2, 1.punktum)

        håndterYtelser()
        håndterSimulering()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(ORGNUMMER, 50000.årlig)))

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 1,
            forventetAntall = 2,
            utfall = VILKAR_OPPFYLT,
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "grunnlagForSykepengegrunnlag" to 50000.0,
                "minimumInntekt" to 46817.0
            ),
            output = emptyMap()
        )
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_51, LEDD_2, 1.punktum)
    }

    @Test
    fun `§ 8-3 ledd 2 punktum 1 - har inntekt mindre enn en halv G`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 46816.årlig)
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 16.desember(2011),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "grunnlagForSykepengegrunnlag" to 46816.0,
                "minimumInntekt" to 46817.0
            ),
            output = emptyMap()
        )
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_51, LEDD_2, 1.punktum)
    }

    @Test
    fun `§ 8-9 ledd 1 - ikke vurdert dersom det ikke er oppgitt utenlandsopphold`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(20.januar, 31.januar))
        SubsumsjonInspektør(jurist).assertIkkeVurdert(
            paragraf = PARAGRAF_8_9,
            versjon = 1.juni(2021),
            ledd = LEDD_1,
        )
    }

    @Test
    fun `§ 8-9 ledd 1 - avslag ved utenlandsopphold`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Utlandsopphold(20.januar, 31.januar))
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_9,
            versjon = 1.juni(2021),
            ledd = LEDD_1,
            input = mapOf(
                "soknadsPerioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                        "type" to "sykdom"
                    ),
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar,
                        "type" to "utlandsopphold"
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar
                    )
                )
            )
        )
    }

    @Test
    fun `§ 8-9 ledd 1 - en subsumsjon for to utenlandsopphold`() {
        håndterSykmelding(januar)
        håndterSøknad(
            Sykdom(1.januar, 31.januar, 100.prosent),
            Utlandsopphold(15.januar, 17.januar),
            Utlandsopphold(20.januar, 31.januar)
        )
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_9,
            versjon = 1.juni(2021),
            ledd = LEDD_1,
            input = mapOf(
                "soknadsPerioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                        "type" to "sykdom"
                    ),
                    mapOf(
                        "fom" to 15.januar,
                        "tom" to 17.januar,
                        "type" to "utlandsopphold"
                    ),
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar,
                        "type" to "utlandsopphold"
                    )
                )

            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 15.januar,
                        "tom" to 17.januar
                    ),
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar
                    )
                )
            )
        )
    }

    @Test
    fun `§ 8-9 ledd 1 - avslag ved utenlandsopphold, selv om utenlandsoppholdet er helt innenfor en ferie`() {
        håndterSykmelding(januar)
        håndterSøknad(
            Sykdom(1.januar, 31.januar, 100.prosent),
            Utlandsopphold(20.januar, 31.januar),
            Ferie(20.januar, 31.januar)
        )
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_9,
            versjon = 1.juni(2021),
            ledd = LEDD_1,
            input = mapOf(
                "soknadsPerioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                        "type" to "sykdom"
                    ),
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar,
                        "type" to "utlandsopphold"
                    ),
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar,
                        "type" to "ferie"
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 20.januar,
                        "tom" to 31.januar
                    )
                )
            )
        )
    }

    @Test
    fun `§ 8-10 ledd 2 punktum 1 - inntekt overstiger ikke maksimum sykepengegrunnlag`() {
        val maksimumSykepengegrunnlag2018 = (93634 * 6).årlig // 6G
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = maksimumSykepengegrunnlag2018)
        håndterVilkårsgrunnlag(inntekt = maksimumSykepengegrunnlag2018)
        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_10,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 1.januar(2020),
            input = mapOf(
                "maksimaltSykepengegrunnlag" to 561804.0,
                "skjæringstidspunkt" to 1.januar,
                "grunnlagForSykepengegrunnlag" to 561804.0
            ),
            output = mapOf(
                "erBegrenset" to false
            )
        )
    }

    @Test
    fun `§ 8-10 ledd 2 punktum 1 - inntekt overstiger maksimum sykepengegrunnlag`() {
        val maksimumSykepengegrunnlag2018 = (93634 * 6).årlig // 6G
        val inntekt = maksimumSykepengegrunnlag2018.plus(1.årlig)
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntekt)
        håndterVilkårsgrunnlag(inntekt = inntekt)
        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_10,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 1.januar(2020),
            input = mapOf(
                "maksimaltSykepengegrunnlag" to 561804.0,
                "skjæringstidspunkt" to 1.januar,
                "grunnlagForSykepengegrunnlag" to 561805.0
            ),
            output = mapOf(
                "erBegrenset" to true
            )
        )
    }

    @Test
    fun `§ 8-10 ledd 3 - årlig inntekt omregnet til daglig`() {
        val inntekt = 260000.årlig
        håndterSykmelding(januar)
        håndterSøknad(januar)
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntekt)

        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_10,
            ledd = LEDD_3,
            versjon = 1.januar(2020),
            sporing = listOf(
                Subsumsjonskontekst(KontekstType.Fødselsnummer, UNG_PERSON_FNR_2018.toString()),
                Subsumsjonskontekst(KontekstType.Organisasjonsnummer, ORGNUMMER),
                Subsumsjonskontekst(KontekstType.Inntektsmelding, im.toString())
            ),
            input = mapOf("årligInntekt" to 260000.0),
            output = mapOf("dagligInntekt" to 1000.0)
        )
    }

    @Test
    fun `§ 8-11 ledd 1 - yter ikke sykepenger i helgedager`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(Periode(1.januar, 18.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_11,
            ledd = LEDD_1,
            versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
            input = mapOf(
                "periode" to mapOf("fom" to 1.januar, "tom" to 31.januar)
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf("fom" to 20.januar, "tom" to 21.januar),
                    mapOf("fom" to 27.januar, "tom" to 28.januar)
                )
            )
        )
    }

    @Test
    fun `§ 8-12 ledd 1 punktum 1 - Brukt færre enn 248 dager`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 50.prosent, 50.prosent))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 21.mai(2021),
            input = mapOf(
                "fom" to 3.januar,
                "tom" to 26.januar,
                "utfallFom" to 19.januar,
                "utfallTom" to 26.januar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 3.januar, "tom" to 18.januar, "dagtype" to "AGPDAG", "grad" to 50),
                        mapOf("fom" to 19.januar, "tom" to 26.januar, "dagtype" to "NAVDAG", "grad" to 50)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 3.januar, "tom" to 18.januar, "dagtype" to "AGPDAG", "grad" to 50),
                    mapOf("fom" to 19.januar, "tom" to 26.januar, "dagtype" to "NAVDAG", "grad" to 50)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 242,
                "forbrukteSykedager" to 6,
                "maksdato" to 1.januar(2019)
            )
        )
    }

    @Test
    fun `§ 8-12 ledd 1 punktum 1 - Brukt flere enn 248 dager`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar(2018), 11.januar(2019)))
        håndterSøknad(Sykdom(3.januar(2018), 11.januar(2019), 50.prosent, 50.prosent), sendtTilNAVEllerArbeidsgiver = 3.januar(2018))
        håndterInntektsmelding(listOf(Periode(3.januar(2018), 18.januar(2018))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 21.mai(2021),
            input = mapOf(
                "fom" to 3.januar,
                "tom" to 11.januar(2019),
                "utfallFom" to 19.januar,
                "utfallTom" to 1.januar(2019),
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 3.januar, "tom" to 18.januar, "dagtype" to "AGPDAG", "grad" to 50),
                        mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG", "grad" to 50)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 3.januar, "tom" to 18.januar, "dagtype" to "AGPDAG", "grad" to 50),
                    mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG", "grad" to 50)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 248,
                "maksdato" to 1.januar(2019)
            )
        )

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 21.mai(2021),
            input = mapOf(
                "fom" to 3.januar,
                "tom" to 11.januar(2019),
                "utfallFom" to 2.januar(2019),
                "utfallTom" to 11.januar(2019),
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 3.januar, "tom" to 18.januar, "dagtype" to "AGPDAG", "grad" to 50),
                        mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG", "grad" to 50)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 3.januar, "tom" to 18.januar, "dagtype" to "AGPDAG", "grad" to 50),
                    mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG", "grad" to 50)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 248,
                "maksdato" to 1.januar(2019)
            )
        )
    }

    @Test
    fun `§8-12 ledd 1 punktum 1 - Blir kun vurdert en gang etter ny periode med ny rett til sykepenger`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar(2019)))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)), avsendersystem = ALTINN)
        håndterSøknad(1.januar til 31.januar(2019), sendtTilNAVEllerArbeidsgiver = 1.januar(2018))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(16.juni(2019), 31.juli(2019)))
        håndterSøknad(Sykdom(16.juni(2019), 31.juli(2019), 50.prosent, 50.prosent))
        håndterInntektsmelding(listOf(Periode(16.juni(2019), 1.juli(2019))), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        SubsumsjonInspektør(jurist).assertVurdert(
            paragraf = PARAGRAF_8_12,
            ledd = 1.ledd,
            punktum = 1.punktum,
            versjon = LocalDate.of(2021, 5, 21),
            vedtaksperiodeId = 2.vedtaksperiode
        )
    }

    @Test
    fun `§ 8-12 ledd 2 - Bruker har vært arbeidsfør i 26 uker`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent, 50.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(17.juli, 31.august))
        håndterSøknad(Sykdom(17.juli, 31.august, 50.prosent, 50.prosent))
        håndterInntektsmelding(listOf(Periode(17.juli, 1.august)), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()


        SubsumsjonInspektør(jurist).assertIkkeVurdert(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_2,
            versjon = 21.mai(2021),
            vedtaksperiodeId = 1.vedtaksperiode
        )
        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_2,
            versjon = 21.mai(2021),
            input = mapOf(
                "dato" to 1.august,
                "tilstrekkeligOppholdISykedager" to 182, //26 uker * 7 dager
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 17.juli, "tom" to 1.august, "dagtype" to "AGPDAG", "grad" to 50),
                        mapOf("fom" to 2.august, "tom" to 31.august, "dagtype" to "NAVDAG", "grad" to 50)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 50),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 50),
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 50),
                    mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 50),
                    mapOf("fom" to 17.juli, "tom" to 1.august, "dagtype" to "AGPDAG", "grad" to 50),
                    mapOf("fom" to 2.august, "tom" to 31.august, "dagtype" to "NAVDAG", "grad" to 50)
                )
            ),
            output = emptyMap(),
            vedtaksperiodeId = 2.vedtaksperiode
        )
    }

    @Test
    fun `§8-12 ledd 2 - Bruker har ikke vært arbeidsfør i 26 uker`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2018), 30.desember(2018)))
        håndterInntektsmelding(listOf(Periode(1.januar(2018), 16.januar(2018))), avsendersystem = ALTINN)
        håndterSøknad(
            Sykdom(1.januar(2018), 30.desember(2018), 100.prosent),
            sendtTilNAVEllerArbeidsgiver = 1.januar(2018)
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.januar(2019), 31.januar(2019)))
        håndterSøknad(
            Sykdom(1.januar(2019), 31.januar(2019), 100.prosent),
            sendtTilNAVEllerArbeidsgiver = 31.januar(2019)
        )
        håndterInntektsmelding(listOf(Periode(1.januar(2018), 16.januar(2018))), førsteFraværsdag = 1.januar(2019), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_2,
            versjon = 21.mai(2021),
            input = mapOf(
                "dato" to 31.desember,
                "tilstrekkeligOppholdISykedager" to 182,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar(2019), "tom" to 31.januar(2019), "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 30.desember, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 30.desember, "dagtype" to "NAVDAG", "grad" to 100),
                    mapOf("fom" to 1.januar(2019), "tom" to 31.januar(2019), "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = emptyMap(),
            vedtaksperiodeId = 2.vedtaksperiode
        )
    }

    @Test
    fun `§ 8-13 ledd 1 - Sykmeldte har 20 prosent uføregrad`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_13,
            ledd = 1.ledd,
            versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
            input = mapOf(
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 20),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 20)
                    )
                ),
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar
                    )
                )

            )
        )
    }

    @Test
    fun `§ 8-13 ledd 1 - Sykmeldte har under 20 prosent uføregrad`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 19.prosent, 81.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_13,
            ledd = 1.ledd,
            versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
            input = mapOf(
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 19),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 19)
                    )
                ),
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 17.januar,
                        "tom" to 31.januar
                    )
                )
            )
        )
    }

    @Test
    fun `§ 8-13 ledd 2 - Sykmeldte har 20 prosent uføregrad`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_13,
            ledd = 2.ledd,
            versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
            input = mapOf(
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 20),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 20)
                    )
                ),
                "grense" to 20.0
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar
                    )
                ),
                "dagerUnderGrensen" to emptyList()
            )
        )
    }

    @Test
    fun `§ 8-13 ledd 2 - Sykmeldte har under 20 prosent uføregrad`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 19.prosent, 81.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_13,
            ledd = 2.ledd,
            versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
            input = mapOf(
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 19),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 19)
                    )
                ),
                "grense" to 20.0
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar
                    )
                ),
                "dagerUnderGrensen" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar
                    )
                )
            )
        )
    }

    @Test
    fun `§ 8-15 - lager subsumsjon ved deaktivering av ghostarbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 1000.månedlig.repeat(2))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterOverstyrArbeidsforhold(
            1.januar,
            listOf(
                OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(
                    a2,
                    true,
                    "Jeg, en saksbehandler, overstyrte pga 8-15"
                )
            )
        )
        SubsumsjonInspektør(jurist).assertOppfylt(
            versjon = 18.desember(1998),
            paragraf = PARAGRAF_8_15,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "inntekterSisteTreMåneder" to
                        listOf(
                            mapOf(
                                "beløp" to 1000.0,
                                "årMåned" to YearMonth.of(2017, 11),
                                "type" to "LØNNSINNTEKT",
                                "fordel" to "Juidy inntekt",
                                "beskrivelse" to "Juidy fordel"
                            ),
                            mapOf(
                                "beløp" to 1000.0,
                                "årMåned" to YearMonth.of(2017, 12),
                                "type" to "LØNNSINNTEKT",
                                "fordel" to "Juidy inntekt",
                                "beskrivelse" to "Juidy fordel"
                            )
                        ),

                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-15"
            ),
            output = mapOf(
                "arbeidsforholdAvbrutt" to a2
            )
        )
    }

    @Test
    fun `§ 8-15 - lager subsumsjon ved deaktivering av ghostarbeidsforhold uten inntekt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterOverstyrArbeidsforhold(
            1.januar,
            listOf(
                OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(
                    a2,
                    true,
                    "Jeg, en saksbehandler, overstyrte pga 8-15"
                )
            )
        )
        SubsumsjonInspektør(jurist).assertOppfylt(
            versjon = 18.desember(1998),
            paragraf = PARAGRAF_8_15,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "inntekterSisteTreMåneder" to emptyList<Map<String, Any>>(),
                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-15"
            ),
            output = mapOf(
                "arbeidsforholdAvbrutt" to a2
            )
        )
    }

    @Test
    fun `§ 8-15 - lager subsumsjon ved reaktivering av ghostarbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 1000.månedlig.repeat(2))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterOverstyrArbeidsforhold(
            1.januar,
            listOf(
                OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(
                    a2,
                    true,
                    "Jeg, en saksbehandler, overstyrte pga 8-15"
                )
            )
        )
        SubsumsjonInspektør(jurist).assertOppfylt(
            versjon = 18.desember(1998),
            paragraf = PARAGRAF_8_15,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "inntekterSisteTreMåneder" to listOf(
                    mapOf(
                        "beløp" to 1000.0,
                        "årMåned" to YearMonth.of(2017, 11),
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    ),
                    mapOf(
                        "beløp" to 1000.0,
                        "årMåned" to YearMonth.of(2017, 12),
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    )
                ),
                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-15"
            ),
            output = mapOf(
                "arbeidsforholdAvbrutt" to a2
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterOverstyrArbeidsforhold(
            1.januar,
            listOf(
                OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(
                    a2,
                    false,
                    "Jeg, en saksbehandler, aktiverte pga 8-15"
                )
            )
        )

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            versjon = 18.desember(1998),
            paragraf = PARAGRAF_8_15,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "inntekterSisteTreMåneder" to
                        listOf(
                            mapOf(
                                "beløp" to 1000.0,
                                "årMåned" to YearMonth.of(2017, 11),
                                "type" to "LØNNSINNTEKT",
                                "fordel" to "Juidy inntekt",
                                "beskrivelse" to "Juidy fordel"
                            ),
                            mapOf(
                                "beløp" to 1000.0,
                                "årMåned" to YearMonth.of(2017, 12),
                                "type" to "LØNNSINNTEKT",
                                "fordel" to "Juidy inntekt",
                                "beskrivelse" to "Juidy fordel"
                            )
                        ),

                "forklaring" to "Jeg, en saksbehandler, aktiverte pga 8-15"
            ),
            output = mapOf(
                "aktivtArbeidsforhold" to a2
            )
        )
    }

    @Test
    fun `§ 8-15 - lager subsumsjon ved deaktivering av ghostarbeidsforhold når inntekt har blitt overstyrt først`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 1000.månedlig.repeat(2))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterOverstyrInntekt(1001.månedlig, a2, 1.januar)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterOverstyrArbeidsforhold(
            1.januar,
            listOf(
                OverstyrArbeidsforhold.ArbeidsforholdOverstyrt(
                    a2,
                    true,
                    "Jeg, en saksbehandler, overstyrte pga 8-15"
                )
            )
        )
        SubsumsjonInspektør(jurist).assertOppfylt(
            versjon = 18.desember(1998),
            paragraf = PARAGRAF_8_15,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "inntekterSisteTreMåneder" to emptyList<Map<String, Any>>(),
                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-15"
            ),
            output = mapOf(
                "arbeidsforholdAvbrutt" to a2
            )
        )
    }

    @Test
    fun `§ 8-16 ledd 1 - dekningsgrad`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_16,
            ledd = 1.ledd,
            versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
            input = mapOf(
                "dekningsgrad" to 1.0,
                "inntekt" to 372000.0,
            ),
            output = mapOf(
                "dekningsgrunnlag" to 372000.0,
                "perioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                    )
                )
            )
        )
    }

    @Test
    fun `§ 8-17 ledd 1 bokstav a - trygden yter sykepenger ved utløp av arbeidsgiverperioden`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            paragraf = PARAGRAF_8_17,
            index = 0,
            forventetAntall = 2,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar,
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                        "dagtype" to "SYKEDAG",
                        "grad" to 100
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(mapOf("fom" to 17.januar, "tom" to 17.januar))
            ),
            utfall = VILKAR_OPPFYLT
        )
        SubsumsjonInspektør(jurist).assertPaaIndeks(
            paragraf = PARAGRAF_8_17,
            index = 1,
            forventetAntall = 2,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar,
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                        "dagtype" to "SYKEDAG",
                        "grad" to 100
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(mapOf("fom" to 17.januar, "tom" to 17.januar))
            ),
            utfall = VILKAR_OPPFYLT
        )
    }

    @Test
    fun `§ 8-17 ledd 1 bokstav a - trygden yter sykepenger dersom arbeidsgiverperioden avslutter på en fredag`() {
        håndterSykmelding(Sykmeldingsperiode(4.januar, 22.januar))
        håndterSøknad(4.januar til 22.januar)
        håndterInntektsmelding(listOf(4.januar til 19.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            paragraf = PARAGRAF_8_17,
            index = 0,
            forventetAntall = 2,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar,
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf(
                        "fom" to 4.januar,
                        "tom" to 22.januar,
                        "dagtype" to "SYKEDAG",
                        "grad" to 100
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(mapOf("fom" to 22.januar, "tom" to 22.januar))
            ),
            utfall = VILKAR_OPPFYLT,
        )
        SubsumsjonInspektør(jurist).assertPaaIndeks(
            paragraf = PARAGRAF_8_17,
            index = 1,
            forventetAntall = 2,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar,
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf(
                        "fom" to 4.januar,
                        "tom" to 22.januar,
                        "dagtype" to "SYKEDAG",
                        "grad" to 100
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(mapOf("fom" to 22.januar, "tom" to 22.januar))
            ),
            utfall = VILKAR_OPPFYLT,
        )
    }

    @Test
    fun `§ 8-17 ledd 1 bokstav a - trygden yter ikke sykepenger dersom arbeidsgiverperioden ikke er fullført`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSøknad(1.januar til 16.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET_UTEN_UTBETALING)
        SubsumsjonInspektør(jurist).assertFlereIkkeOppfylt(
            antall = 2,
            lovverk = "folketrygdloven",
            paragraf = PARAGRAF_8_17,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar(2018),
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 16.januar,
                        "dagtype" to "SYKEDAG",
                        "grad" to 100
                    )
                )
            ),
            output = mapOf("perioder" to listOf(mapOf("fom" to 1.januar, "tom" to 16.januar)))
        )
    }

    @Test
    fun `§ 8-17 ledd 1 bokstav a - trygden yter ikke sykepenger dersom arbeidsgiverperioden ikke er påstartet`() {
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent), Arbeid(1.januar, 10.januar))
        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET_UTEN_UTBETALING)
        SubsumsjonInspektør(jurist).assertFlereIkkeOppfylt(
            antall = 1,
            lovverk = "folketrygdloven",
            paragraf = PARAGRAF_8_17,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar(2018),
            input = mapOf(
                "sykdomstidslinje" to emptyList<Any>()
            ),
            output = mapOf("perioder" to listOf(mapOf("fom" to 1.januar, "tom" to 10.januar)))
        )
    }

    @Test
    fun `§ 8-17 ledd 1 bokstav a - ikke-oppfylt innenfor arbeidsgiverperioden`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_17,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar(2018),
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 31.januar,
                        "dagtype" to "SYKEDAG",
                        "grad" to 100
                    )
                )
            ),
            output = mapOf(
                "perioder" to listOf(mapOf("fom" to 1.januar, "tom" to 16.januar))
            )
        )
    }

    @Test
    fun `§ 8-17 ledd 1 bokstav a - opphold inne i arbeidsgiverperioden`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 10.januar, 12.januar til 17.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 0,
            utfall = VILKAR_IKKE_OPPFYLT,
            forventetAntall = 1,
            lovverk = "folketrygdloven",
            paragraf = PARAGRAF_8_17,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            versjon = 1.januar(2018),
            input = mapOf(
                "sykdomstidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 10.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                    mapOf("fom" to 12.januar, "tom" to 31.januar, "dagtype" to "SYKEDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 10.januar),
                    mapOf("fom" to 12.januar, "tom" to 17.januar)
                )
            )
        )
    }

    @Test
    fun `§ 8-17 ledd 2 - trygden yter ikke sykepenger for feriedager og permisjonsdager`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(30.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            versjon = 1.januar(2018),
            paragraf = PARAGRAF_8_17,
            ledd = 2.ledd,
            input = mapOf(
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 29.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                    mapOf("fom" to 30.januar, "tom" to 31.januar, "dagtype" to "FERIEDAG", "grad" to null)
                ),
            ),
            output = mapOf(
                "perioder" to listOf(mapOf("fom" to 30.januar, "tom" to 31.januar))
            )
        )
    }

    @Test
    fun `§ 8-19 første ledd - arbeidsgiverperioden varer i 16 dager`() {
        håndterSykmelding(Sykmeldingsperiode(4.januar, 22.januar))
        håndterSøknad(4.januar til 22.januar)
        håndterInntektsmelding(listOf(4.januar til 19.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            paragraf = PARAGRAF_8_19,
            index = 0,
            forventetAntall = 2,
            versjon = 1.januar(2001),
            ledd = 1.ledd,
            input = mapOf(
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 4.januar, "tom" to 22.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                ),
            ),
            output = mapOf(
                "sisteDagIArbeidsgiverperioden" to 19.januar,
            ),
            utfall = Utfall.VILKAR_BEREGNET,
        )
        SubsumsjonInspektør(jurist).assertPaaIndeks(
            paragraf = PARAGRAF_8_19,
            index = 1,
            forventetAntall = 2,
            versjon = 1.januar(2001),
            ledd = 1.ledd,
            input = mapOf(
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 4.januar, "tom" to 22.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                ),
            ),
            output = mapOf(
                "sisteDagIArbeidsgiverperioden" to 19.januar,
            ),
            utfall = Utfall.VILKAR_BEREGNET
        )
    }

    @Test
    fun `§ 8-19 andre ledd - arbeidsgiverperioden regnes fra og med første hele fraværsdag`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_19,
            ledd = 2.ledd,
            versjon = 1.januar(2001),
            input = mapOf(
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 31.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                ),
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 1.januar,
                        "tom" to 16.januar
                    )
                ),
            )
        )
    }

    @Test
    fun `§ 8-19 tredje ledd - opphold i AGP`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 3.januar, 5.januar til 10.januar, 12.januar til 17.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = PARAGRAF_8_19,
            ledd = 3.ledd,
            versjon = 1.januar(2001),
            input = mapOf(
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 3.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                    mapOf("fom" to 5.januar, "tom" to 10.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                    mapOf("fom" to 12.januar, "tom" to 31.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                ),
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf("fom" to 5.januar, "tom" to 5.januar),
                    mapOf("fom" to 12.januar, "tom" to 12.januar)
                )
            )
        )
    }

    @Test
    fun `§ 8-19 fjerde ledd - ny agp etter tilstrekkelig opphold`() {
        håndterSøknad(Sykdom(2.januar, 2.februar, 100.prosent), Arbeid(18.januar, 2.februar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        SubsumsjonInspektør(jurist).apply {
            assertPaaIndeks(
                paragraf = PARAGRAF_8_19,
                index = 0,
                forventetAntall = 2,
                ledd = 4.ledd,
                versjon = 1.januar(2001),
                input = mapOf(
                    "beregnetTidslinje" to listOf(
                        mapOf("fom" to 2.januar, "tom" to 17.januar, "dagtype" to "SYKEDAG", "grad" to 100)
                    ),
                ),
                output = mapOf(
                    "perioder" to listOf(
                        mapOf("fom" to 2.februar, "tom" to 2.februar),
                    )
                ),
                vedtaksperiodeId = 1.vedtaksperiode,
                utfall = Utfall.VILKAR_BEREGNET
            )
            assertPaaIndeks(
                paragraf = PARAGRAF_8_19,
                index = 1,
                forventetAntall = 2,
                ledd = 4.ledd,
                versjon = 1.januar(2001),
                input = mapOf(
                    "beregnetTidslinje" to listOf(
                        mapOf("fom" to 2.januar, "tom" to 17.januar, "dagtype" to "SYKEDAG", "grad" to 100)
                    ),
                ),
                output = mapOf(
                    "perioder" to listOf(
                        mapOf("fom" to 2.februar, "tom" to 2.februar),
                    )
                ),
                vedtaksperiodeId = 1.vedtaksperiode,
                utfall = Utfall.VILKAR_BEREGNET
            )
        }
    }

    @Test
    fun `§ 8-28 tredje ledd bokstav a - legger tre siste innraporterte inntekter til grunn for andre arbeidsgivere`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 32000.månedlig.repeat(3))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        SubsumsjonInspektør(jurist).assertBeregnet(
            versjon = 1.januar(2019),
            paragraf = PARAGRAF_8_28,
            ledd = 3.ledd,
            bokstav = BOKSTAV_A,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "inntekterSisteTreMåneder" to listOf(
                    mapOf(
                        "årMåned" to YearMonth.of(2017, 10),
                        "beløp" to 32000.0,
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    ),
                    mapOf(
                        "årMåned" to YearMonth.of(2017, 11),
                        "beløp" to 32000.0,
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    ),
                    mapOf(
                        "årMåned" to YearMonth.of(2017, 12),
                        "beløp" to 32000.0,
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    )
                )
            ),
            output = mapOf(
                "beregnetGrunnlagForSykepengegrunnlagPrÅr" to 384000.0,
                "beregnetGrunnlagForSykepengegrunnlagPrMåned" to 32000.0
            )
        )
    }

    @Test
    fun `§ 8-28 tredje ledd bokstav b - legger tiden arbeidsforholdet har var til grunn om det er nyere enn tre måneder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 1000.månedlig.repeat(2))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.oktober(2017), 31.oktober(2017), Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.april(2016), 3.mai(2016), Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val subsumsjon = Subsumsjon("8-28", 3, "b")
        håndterOverstyrInntekt(
            inntekt = 1500.månedlig,
            orgnummer = a2,
            1.januar,
            subsumsjon = subsumsjon,
            forklaring = "Jeg, en saksbehandler, overstyrte pga 8-28 b"
        )
        SubsumsjonInspektør(jurist).assertBeregnet(
            versjon = 1.januar(2019),
            paragraf = PARAGRAF_8_28,
            ledd = 3.ledd,
            bokstav = BOKSTAV_B,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "startdatoArbeidsforhold" to 1.oktober(2017),
                "overstyrtInntektFraSaksbehandler" to mapOf(
                    "dato" to 1.januar,
                    "beløp" to 1500.0,
                ),
                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-28 b"
            ),
            output = mapOf(
                "beregnetGrunnlagForSykepengegrunnlagPrÅr" to 18000.0,
                "beregnetGrunnlagForSykepengegrunnlagPrMåned" to 1500.0
            ),
        )
    }

    @Test
    fun `§ 8-28 tredje ledd bokstav c - saksbehandler overstyrer inntekt pga varig lønnsendring som ikke ble hensyntatt`(){
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 1000.månedlig.repeat(2))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.april(2016), 3.mai(2016), Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val subsumsjon = Subsumsjon("8-28", 3, "c")
        håndterOverstyrInntekt(
            inntekt = 1500.månedlig,
            orgnummer = a2,
            1.januar,
            subsumsjon = subsumsjon,
            forklaring = "Jeg, en saksbehandler, overstyrte pga 8-28 c"
        )
        SubsumsjonInspektør(jurist).assertBeregnet(
            versjon = 1.januar(2019),
            paragraf = PARAGRAF_8_28,
            ledd = 3.ledd,
            bokstav = Bokstav.BOKSTAV_C,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "overstyrtInntektFraSaksbehandler" to mapOf(
                    "dato" to 1.januar,
                    "beløp" to 1500.0,
                ),
                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-28 c"
            ),
            output = mapOf(
                "beregnetGrunnlagForSykepengegrunnlagPrÅr" to 18000.0,
                "beregnetGrunnlagForSykepengegrunnlagPrMåned" to 1500.0
            )
        )
    }

    @Test
    fun `§ 8-28 femte ledd - saksbehandler overstyrer inntekt pga mangelfull rapportering til A-ordningen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 1000.månedlig.repeat(2))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.november(2017), null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.oktober(2017), 31.oktober(2017), Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.september(2017), 30.september(2017), Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, 1.april(2016), 3.mai(2016), Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val subsumsjon = Subsumsjon("8-28", 5, null)
        håndterOverstyrInntekt(
            inntekt = 1500.månedlig,
            orgnummer = a2,
            1.januar,
            subsumsjon = subsumsjon,
            forklaring = "Jeg, en saksbehandler, overstyrte pga 8-28 (5)"
        )
        SubsumsjonInspektør(jurist).assertBeregnet(
            versjon = 1.januar(2019),
            paragraf = PARAGRAF_8_28,
            ledd = 5.ledd,
            input = mapOf(
                "organisasjonsnummer" to a2,
                "skjæringstidspunkt" to 1.januar,
                "overstyrtInntektFraSaksbehandler" to mapOf(
                    "dato" to 1.januar,
                    "beløp" to 1500.0,
                ),
                "forklaring" to "Jeg, en saksbehandler, overstyrte pga 8-28 (5)"
            ),
            output = mapOf(
                "beregnetGrunnlagForSykepengegrunnlagPrÅr" to 18000.0,
                "beregnetGrunnlagForSykepengegrunnlagPrMåned" to 1500.0
            )
        )
    }

    @Test
    fun `§ 8-29 - filter for inntekter som skal medregnes ved beregning av sykepengegrunnlaget for arbeidsforhold hvor sykdom ikke starter på skjæringstidspunktet`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.mars), orgnummer = a1)
        håndterSøknad(1.januar til 15.mars, orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            orgnummer = a1,
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 31000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 32000.månedlig.repeat(3))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        SubsumsjonInspektør(jurist).assertBeregnet(
            versjon = 1.januar(2019),
            paragraf = PARAGRAF_8_29,
            ledd = null,
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "organisasjonsnummer" to a2,
                "inntektsopplysninger" to listOf(
                    mapOf(
                        "årMåned" to YearMonth.of(2017, 10),
                        "beløp" to 32000.0,
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    ),
                    mapOf(
                        "årMåned" to YearMonth.of(2017, 11),
                        "beløp" to 32000.0,
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    ),
                    mapOf(
                        "årMåned" to YearMonth.of(2017, 12),
                        "beløp" to 32000.0,
                        "type" to "LØNNSINNTEKT",
                        "fordel" to "Juidy inntekt",
                        "beskrivelse" to "Juidy fordel"
                    )
                )
            ),
            output = mapOf(
                "grunnlagForSykepengegrunnlag" to 384000.0
            )
        )
    }

    @Test
    fun `§ 8-51 ledd 2 - er ikke over 67 år`() {
        håndterSykmelding(januar)
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 187268.årlig)
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_51, ledd = LEDD_2)
    }

    @Test
    fun `§ 8-51 ledd 2 - har minimum inntekt 2G - over 67 år`() {
        val personOver67år = Personidentifikator("01014500065")
        createTestPerson(personOver67år, 1.januar(1945))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), fnr = personOver67år)
        håndterSøknad(januar, fnr = personOver67år)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 187268.årlig, fnr = personOver67år)
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold, fnr = personOver67år)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_2,
            versjon = 16.desember(2011),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "alderPåSkjæringstidspunkt" to 73,
                "grunnlagForSykepengegrunnlag" to 187268.0,
                "minimumInntekt" to 187268.0
            ),
            output = emptyMap()
        )
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_3, ledd = LEDD_2, 1.punktum)
    }

    @Test
    fun `§ 8-51 ledd 2 - har inntekt mindre enn 2G - over 67 år`() {
        val personOver67år = Personidentifikator("01014500065")
        createTestPerson(personOver67år, 1.januar(1945))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), fnr = personOver67år)
        håndterSøknad(januar, fnr = personOver67år)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 187267.årlig, fnr = personOver67år)
        val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar, Arbeidsforholdtype.ORDINÆRT))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold, fnr = personOver67år)

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_2,
            versjon = 16.desember(2011),
            input = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "alderPåSkjæringstidspunkt" to 73,
                "grunnlagForSykepengegrunnlag" to 187267.0,
                "minimumInntekt" to 187268.0
            ),
            output = emptyMap()
        )
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_3, ledd = LEDD_2, 1.punktum)
    }

    @Test
    fun `§ 8-51 ledd 2 - avslag subsummeres når person blir 67 år underveis i sykefraværstilfellet og tjener mindre enn 2G`() {
        val personOver67år = Personidentifikator("05025100065")
        createTestPerson(personOver67år, 5.februar(1951))
        val inntekt = 100_000.årlig // mellom 0.5G og 2G - slik at kravet er oppfyllt før personen fyllte 67, men ikke etter

        nyttVedtak(januar, fnr = personOver67år, beregnetInntekt = inntekt)
        SubsumsjonInspektør(jurist).assertIkkeVurdert(PARAGRAF_8_2, ledd = LEDD_2)
        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_2,
            punktum = 1.punktum,
            input = null,
            versjon = 16.desember(2011)
        )

        forlengVedtak(februar)
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_2,
            versjon = 16.desember(2011),
            input = mapOf(
                "sekstisyvårsdag" to 5.februar(2018),
                "utfallFom" to 6.februar,
                "utfallTom" to 28.februar,
                "periodeFom" to 1.februar,
                "periodeTom" to 28.februar,
                "grunnlagForSykepengegrunnlag" to 100000.0,
                "minimumInntekt" to 187268.0
            ),
            output = emptyMap()
        )

        forlengVedtak(mars)
        assertEquals(1, SubsumsjonInspektør(jurist).antallSubsumsjoner(
            paragraf = PARAGRAF_8_3,
            ledd = LEDD_2,
            punktum = 1.punktum,
            versjon = 16.desember(2011),
            bokstav = null
        ))
        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 0,
            forventetAntall = 1,
            paragraf = PARAGRAF_8_51,
            versjon = 16.desember(2011),
            ledd = LEDD_2,
            input = mapOf(
                "sekstisyvårsdag" to 5.februar(2018),
                "utfallFom" to 1.mars,
                "utfallTom" to 31.mars,
                "periodeFom" to 1.mars,
                "periodeTom" to 31.mars,
                "grunnlagForSykepengegrunnlag" to 100000.0,
                "minimumInntekt" to 187268.0
            ),
            output = emptyMap(),
            vedtaksperiodeId = 3.vedtaksperiode,
            utfall = VILKAR_IKKE_OPPFYLT
        )
    }

    @Test
    fun `§ 8-51 ledd 3 - 60 sykedager etter fylte 67 år - frisk på 60-årsdagen så total sykedager blir en dag mindre uten at maksdato endres`() {
        val personOver67år = Personidentifikator("01025100065")
        createTestPerson(personOver67år, 1.februar(1951))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), fnr = personOver67år)
        håndterSøknad(januar, fnr = personOver67år)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, fnr = personOver67år)
        håndterVilkårsgrunnlag(1.vedtaksperiode, fnr = personOver67år)
        håndterYtelser(1.vedtaksperiode, fnr = personOver67år)
        håndterSimulering(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalt(fnr = personOver67år)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar), fnr = personOver67år)
        håndterSøknad(2.februar til 28.februar, fnr = personOver67år)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 2.februar, fnr = personOver67år, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalt(fnr = personOver67år)

        håndterVilkårsgrunnlag(2.vedtaksperiode, fnr = personOver67år)
        håndterYtelser(2.vedtaksperiode, fnr = personOver67år)
        håndterSimulering(2.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalt(fnr = personOver67år)

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 0,
            forventetAntall = 2,
            paragraf = PARAGRAF_8_51,
            versjon = 16.desember(2011),
            ledd = LEDD_3,
            input = mapOf(
                "fom" to 1.januar,
                "tom" to 31.januar,
                "utfallFom" to 17.januar,
                "utfallTom" to 31.januar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 61,
                "forbrukteSykedager" to 11,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 1.vedtaksperiode,
            utfall = VILKAR_OPPFYLT
        )


        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 1,
            forventetAntall = 2,
            paragraf = PARAGRAF_8_51,
            versjon = 16.desember(2011),
            ledd = LEDD_3,
            input = mapOf(
                "fom" to 1.januar,
                "tom" to 31.januar,
                "utfallFom" to 17.januar,
                "utfallTom" to 31.januar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 61,
                "forbrukteSykedager" to 11,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 1.vedtaksperiode,
            utfall = VILKAR_OPPFYLT
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 2.februar,
                "tom" to 28.februar,
                "utfallFom" to 2.februar,
                "utfallTom" to 28.februar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 2.februar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100),
                    mapOf("fom" to 2.februar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 41,
                "forbrukteSykedager" to 30,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 2.vedtaksperiode
        )
    }

    @Test
    fun `§ 8-51 ledd 3 - 60 sykedager etter fylte 67 år - frisk dagen etter 67-årsdagen så maksdato flyttes en dag`() {
        val personOver67år = Personidentifikator("01025100065")
        createTestPerson(personOver67år, 1.februar(1951))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 1.februar), fnr = personOver67år)
        håndterSøknad(1.januar til 1.februar, fnr = personOver67år)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, fnr = personOver67år)
        håndterVilkårsgrunnlag(1.vedtaksperiode, fnr = personOver67år)
        håndterYtelser(1.vedtaksperiode, fnr = personOver67år)
        håndterSimulering(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalt(fnr = personOver67år)

        håndterSykmelding(Sykmeldingsperiode(3.februar, 28.februar), fnr = personOver67år)
        håndterSøknad(3.februar til 28.februar, fnr = personOver67år)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 3.februar, fnr = personOver67år, vedtaksperiodeIdInnhenter = 2.vedtaksperiode)

        håndterYtelser(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalt(fnr = personOver67år)

        håndterVilkårsgrunnlag(2.vedtaksperiode, fnr = personOver67år)
        håndterYtelser(2.vedtaksperiode, fnr = personOver67år)
        håndterSimulering(2.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, fnr = personOver67år)
        håndterUtbetalt(fnr = personOver67år)

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 0,
            forventetAntall = 2,
            paragraf = PARAGRAF_8_51,
            versjon = 16.desember(2011),
            ledd = LEDD_3,
            input = mapOf(
                "fom" to 1.januar,
                "tom" to 1.februar,
                "utfallFom" to 17.januar,
                "utfallTom" to 1.februar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 1.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 1.februar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 60,
                "forbrukteSykedager" to 12,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 1.vedtaksperiode,
            utfall = VILKAR_OPPFYLT
        )

        SubsumsjonInspektør(jurist).assertPaaIndeks(
            index = 1,
            forventetAntall = 2,
            paragraf = PARAGRAF_8_51,
            versjon = 16.desember(2011),
            ledd = LEDD_3,
            input = mapOf(
                "fom" to 1.januar,
                "tom" to 1.februar,
                "utfallFom" to 17.januar,
                "utfallTom" to 1.februar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 1.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 1.februar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 60,
                "forbrukteSykedager" to 12,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 1.vedtaksperiode,
            utfall = VILKAR_OPPFYLT
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 3.februar,
                "tom" to 28.februar,
                "utfallFom" to 3.februar,
                "utfallTom" to 28.februar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 3.februar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 1.februar, "dagtype" to "NAVDAG", "grad" to 100),
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 1.februar, "dagtype" to "NAVDAG", "grad" to 100),
                    mapOf("fom" to 3.februar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 42,
                "forbrukteSykedager" to 30,
                "maksdato" to 27.april
            ),
            vedtaksperiodeId = 2.vedtaksperiode
        )
    }

    @Test
    fun `§ 8-51 ledd 3 - 60 sykedager etter fylte 67 år - syk 60 dager etter fylte 67 år`() {
        val personOver67år = Personidentifikator("01025100065")
        createTestPerson(personOver67år, 1.februar(1951))
        nyttVedtak(januar, fnr = personOver67år)
        forlengVedtak(februar, fnr = personOver67år)
        forlengVedtak(mars, fnr = personOver67år)
        forlengVedtak(1.april til 26.april, fnr = personOver67år)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.januar,
                "tom" to 31.januar,
                "utfallFom" to 17.januar,
                "utfallTom" to 31.januar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 61,
                "forbrukteSykedager" to 11,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 1.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.februar,
                "tom" to 28.februar,
                "utfallFom" to 1.februar,
                "utfallTom" to 28.februar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.februar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100),
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 41,
                "forbrukteSykedager" to 31,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 2.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.mars,
                "tom" to 31.mars,
                "utfallFom" to 1.mars,
                "utfallTom" to 31.mars,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.mars, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 19,
                "forbrukteSykedager" to 53,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 3.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.april,
                "tom" to 26.april,
                "utfallFom" to 1.april,
                "utfallTom" to 26.april,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.april, "tom" to 26.april, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 26.april, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 72,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 4.vedtaksperiode
        )
    }

    @Test
    fun `§ 8-51 ledd 3 - 60 sykedager etter fylte 67 år - syk 61 dager etter fylte 67 år`() {
        val personOver67år = Personidentifikator("01025100065")
        createTestPerson(personOver67år, 1.februar(1951))
        nyttVedtak(januar, fnr = personOver67år)
        forlengVedtak(februar, fnr = personOver67år)
        forlengVedtak(mars, fnr = personOver67år)
        forlengVedtak(1.april til 27.april, fnr = personOver67år)

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.januar,
                "tom" to 31.januar,
                "utfallFom" to 17.januar,
                "utfallTom" to 31.januar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 61,
                "forbrukteSykedager" to 11,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 1.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.februar,
                "tom" to 28.februar,
                "utfallFom" to 1.februar,
                "utfallTom" to 28.februar,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.februar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.januar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 41,
                "forbrukteSykedager" to 31,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 2.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.mars,
                "tom" to 31.mars,
                "utfallFom" to 1.mars,
                "utfallTom" to 31.mars,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.mars, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 28.februar, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 19,
                "forbrukteSykedager" to 53,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 3.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.april,
                "tom" to 27.april,
                "utfallFom" to 1.april,
                "utfallTom" to 26.april,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.april, "tom" to 27.april, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 27.april, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 72,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 4.vedtaksperiode
        )

        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_8_51,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "fom" to 1.april,
                "tom" to 27.april,
                "utfallFom" to 27.april,
                "utfallTom" to 27.april,
                "tidslinjegrunnlag" to listOf(
                    listOf(
                        mapOf("fom" to 1.april, "tom" to 27.april, "dagtype" to "NAVDAG", "grad" to 100)
                    ),
                    listOf(
                        mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                        mapOf("fom" to 17.januar, "tom" to 31.mars, "dagtype" to "NAVDAG", "grad" to 100)
                    )
                ),
                "beregnetTidslinje" to listOf(
                    mapOf("fom" to 1.januar, "tom" to 16.januar, "dagtype" to "AGPDAG", "grad" to 100),
                    mapOf("fom" to 17.januar, "tom" to 27.april, "dagtype" to "NAVDAG", "grad" to 100)
                )
            ),
            output = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 72,
                "maksdato" to 26.april
            ),
            vedtaksperiodeId = 4.vedtaksperiode
        )
    }

    @Test
    fun `§ 22-13 - foreldelse`() {
        håndterSøknad(15.januar til 15.februar, sendtTilNAVEllerArbeidsgiver = 1.mai)
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            paragraf = PARAGRAF_22_13,
            ledd = LEDD_3,
            versjon = 16.desember(2011),
            input = mapOf(
                "avskjæringsdato" to 1.februar
            ),
            output = mapOf(
                "perioder" to listOf(
                    mapOf(
                        "fom" to 15.januar,
                        "tom" to 19.januar
                    ),
                    mapOf(
                        "fom" to 22.januar,
                        "tom" to 26.januar
                    ),
                    mapOf(
                        "fom" to 29.januar,
                        "tom" to 31.januar
                    )
                )
            ),
            vedtaksperiodeId = 1.vedtaksperiode
        )
    }

    @Test
    fun `§ forvaltningsloven 35 - omgjøring av vedtak uten klage`() {
        nyttVedtak(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 90.prosent))
        SubsumsjonInspektør(jurist).assertOppfylt(
            lovverk = "forvaltningsloven",
            paragraf = Paragraf.PARAGRAF_35,
            ledd = LEDD_1,
            versjon = 1.juni(2021),
            input = mapOf(
                "stadfesting" to true
            ),
            output = emptyMap(),
            vedtaksperiodeId = 1.vedtaksperiode
        )
    }

    @Test
    fun `andre ytelser i snuten`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterOverstyrTidslinje(overstyringsdager = listOf(
            ManuellOverskrivingDag(1.februar, Dagtype.Foreldrepengerdag),
            ManuellOverskrivingDag(2.februar, Dagtype.Pleiepengerdag),
            ManuellOverskrivingDag(3.februar, Dagtype.Omsorgspengerdag),
            ManuellOverskrivingDag(4.februar, Dagtype.Svangerskapspengerdag),
            ManuellOverskrivingDag(5.februar, Dagtype.Opplaringspengerdag),
            ManuellOverskrivingDag(6.februar, Dagtype.AAPdag),
            ManuellOverskrivingDag(7.februar, Dagtype.Dagpengerdag),
            ManuellOverskrivingDag(8.februar, Dagtype.AAPdag),
        ))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        val forventetInput = mapOf(
            "sykdomstidslinje" to listOf(
                mapOf("fom" to 1.februar, "tom" to 1.februar, "dagtype" to "FORELDREPENGER", "grad" to null),
                mapOf("fom" to 2.februar, "tom" to 2.februar, "dagtype" to "PLEIEPENGER", "grad" to null),
                mapOf("fom" to 3.februar, "tom" to 3.februar, "dagtype" to "OMSORGSPENGER", "grad" to null),
                mapOf("fom" to 4.februar, "tom" to 4.februar, "dagtype" to "SVANGERSKAPSPENGER", "grad" to null),
                mapOf("fom" to 5.februar, "tom" to 5.februar, "dagtype" to "OPPLÆRINGSPENGER", "grad" to null),
                mapOf("fom" to 6.februar, "tom" to 6.februar, "dagtype" to "ARBEIDSAVKLARINGSPENGER", "grad" to null),
                mapOf("fom" to 7.februar, "tom" to 7.februar, "dagtype" to "DAGPENGER", "grad" to null),
                mapOf("fom" to 8.februar, "tom" to 8.februar, "dagtype" to "ARBEIDSAVKLARINGSPENGER", "grad" to null),
                mapOf("fom" to 9.februar, "tom" to 28.februar, "dagtype" to "SYKEDAG", "grad" to 100)
            ),
        )
        // Alt utenom Arbeidsavklaringspenger
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            lovverk = "trygderetten",
            versjon = 2.mars(2007),
            paragraf = KJENNELSE_2006_4023,
            input = forventetInput,
            output = mapOf("perioder" to listOf(
                mapOf("fom" to 1.februar, "tom" to 5.februar),
                mapOf("fom" to 7.februar, "tom" to 7.februar),
            ))
        )
        // Arbeidsavklaringspenger
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            lovverk = "folketrygdloven",
            versjon = 21.mai(2021),
            paragraf = PARAGRAF_8_48,
            input = forventetInput,
            output = mapOf("perioder" to listOf(
                mapOf("fom" to 6.februar, "tom" to 6.februar),
                mapOf("fom" to 8.februar, "tom" to 8.februar),
            ))
        )
    }

    @Test
    fun `andre ytelser i halen`() {
        nyttVedtak(januar)
        håndterOverstyrTidslinje(overstyringsdager = listOf(
            ManuellOverskrivingDag(24.januar, Dagtype.Foreldrepengerdag),
            ManuellOverskrivingDag(25.januar, Dagtype.Pleiepengerdag),
            ManuellOverskrivingDag(26.januar, Dagtype.Omsorgspengerdag),
            ManuellOverskrivingDag(27.januar, Dagtype.Svangerskapspengerdag),
            ManuellOverskrivingDag(28.januar, Dagtype.Opplaringspengerdag),
            ManuellOverskrivingDag(29.januar, Dagtype.AAPdag),
            ManuellOverskrivingDag(30.januar, Dagtype.Dagpengerdag),
            ManuellOverskrivingDag(31.januar, Dagtype.AAPdag),
        ))
        håndterYtelser(1.vedtaksperiode)
        val forventetInput = mapOf(
            "sykdomstidslinje" to listOf(
                mapOf("fom" to 1.januar, "tom" to 23.januar, "dagtype" to "SYKEDAG", "grad" to 100),
                mapOf("fom" to 24.januar, "tom" to 24.januar, "dagtype" to "FORELDREPENGER", "grad" to null),
                mapOf("fom" to 25.januar, "tom" to 25.januar, "dagtype" to "PLEIEPENGER", "grad" to null),
                mapOf("fom" to 26.januar, "tom" to 26.januar, "dagtype" to "OMSORGSPENGER", "grad" to null),
                mapOf("fom" to 27.januar, "tom" to 27.januar, "dagtype" to "SVANGERSKAPSPENGER", "grad" to null),
                mapOf("fom" to 28.januar, "tom" to 28.januar, "dagtype" to "OPPLÆRINGSPENGER", "grad" to null),
                mapOf("fom" to 29.januar, "tom" to 29.januar, "dagtype" to "ARBEIDSAVKLARINGSPENGER", "grad" to null),
                mapOf("fom" to 30.januar, "tom" to 30.januar, "dagtype" to "DAGPENGER", "grad" to null),
                mapOf("fom" to 31.januar, "tom" to 31.januar, "dagtype" to "ARBEIDSAVKLARINGSPENGER", "grad" to null)
            ),
        )
        // Alt utenom Arbeidsavklaringspenger
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            lovverk = "trygderetten",
            versjon = 2.mars(2007),
            paragraf = KJENNELSE_2006_4023,
            input = forventetInput,
            output = mapOf("perioder" to listOf(
                mapOf("fom" to 24.januar, "tom" to 28.januar),
                mapOf("fom" to 30.januar, "tom" to 30.januar),
            ))
        )
        // Arbeidsavklaringspenger
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
            lovverk = "folketrygdloven",
            versjon = 21.mai(2021),
            paragraf = PARAGRAF_8_48,
            input = forventetInput,
            output = mapOf("perioder" to listOf(
                mapOf("fom" to 29.januar, "tom" to 29.januar),
                mapOf("fom" to 31.januar, "tom" to 31.januar),
            ))
        )
    }
}
