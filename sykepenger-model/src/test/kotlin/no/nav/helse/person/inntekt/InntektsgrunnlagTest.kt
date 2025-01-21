package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.properties.Delegates
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Grunnbeløp
import no.nav.helse.april
import no.nav.helse.dsl.UNG_PERSON_FØDSELSDATO
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.erHelg
import no.nav.helse.etterlevelse.BehandlingSubsumsjonslogg
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Paragraf
import no.nav.helse.etterlevelse.Subsumsjon
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_OPPFYLT
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.etterlevelse.annetLedd
import no.nav.helse.etterlevelse.folketrygdloven
import no.nav.helse.etterlevelse.førstePunktum
import no.nav.helse.etterlevelse.paragraf
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.inntektsgrunnlag
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.nesteDag
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_8
import no.nav.helse.person.inntekt.Skatteopplysning.Inntekttype.LØNNSINNTEKT
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.assertVarsler
import no.nav.helse.sykepengegrunnlag
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.NAVDAGER
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class InntektsgrunnlagTest {
    private companion object {
        private val fødseldato67år = 1.februar(1954)
    }

    private val jurist = BehandlingSubsumsjonslogg(
        EmptyLog, listOf(
        Subsumsjonskontekst(KontekstType.Fødselsnummer, "fnr"),
        Subsumsjonskontekst(KontekstType.Organisasjonsnummer, "orgnr"),
        Subsumsjonskontekst(KontekstType.Vedtaksperiode, "${UUID.randomUUID()}"),
    )
    )

    @Test
    fun `minimum inntekt tom 67 år - må være 0,5 G`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val halvG = Grunnbeløp.halvG.beløp(skjæringstidspunkt)

        var observer = MinsteinntektSubsumsjonObservatør()
        val sykepengegrunnlag = halvG.inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var aktivitetslogg = Aktivitetslogg()
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(halvG, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(observer.`§ 8-3 ledd 2 punktum 1`)

        observer = MinsteinntektSubsumsjonObservatør()
        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (halvG - 1.daglig).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(halvG, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
        assertFalse(observer.`§ 8-3 ledd 2 punktum 1`)
    }

    @Test
    fun `minimum inntekt etter 67 år - må være 2 G`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 2.februar(2021)
        val `2G` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)

        var observer = MinsteinntektSubsumsjonObservatør()
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G`).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)

        observer = MinsteinntektSubsumsjonObservatør()
        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G` - 1.daglig).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
        assertFalse(observer.`§ 8-51 ledd 2`)
    }

    @Test
    fun `kan ikke avvise dager på tidslinje som er før`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val forLitenInntekt = Grunnbeløp.halvG.beløp(skjæringstidspunkt) - 1.daglig
        val sykepengegrunnlag = (forLitenInntekt).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, EmptyLog)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = 1.januar)
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist)
        assertEquals(0, resultat.single().inspektør.avvistDagTeller)
    }

    @Test
    fun `minimum inntekt før fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val forLitenInntekt = Grunnbeløp.halvG.beløp(skjæringstidspunkt) - 1.daglig

        val sykepengegrunnlag = (forLitenInntekt).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, EmptyLog)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(41, resultat.inspektør.avvistDagTeller)
        assertTrue(
            (1.januar(2021) til 1.februar(2021))
                .filterNot { it.erHelg() }
                .all { dato ->
                    resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntekt
                }
        )
        assertTrue(
            (2.februar(2021) til 28.februar(2021))
                .filterNot { it.erHelg() }
                .all { dato ->
                    resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
                }
        )
    }

    @Test
    fun `minimum inntekt etter fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.mars(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, EmptyLog)

        val tidslinje = tidslinjeOf(20.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(20, resultat.inspektør.avvistDagTeller)
        assertTrue(resultat.inspektør.avvistedatoer.all { dato ->
            resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
        })
    }

    @Test
    fun `minimum inntekt ved overgang til 67 år - var innenfor før fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, EmptyLog)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(27, resultat.inspektør.avvistDagTeller)
        assertTrue(resultat.inspektør.avvistedatoer.all { dato ->
            resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
        })
    }

    @Test
    fun `avviser ikke dager utenfor skjæringstidspunktperioden`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, EmptyLog)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til 31.januar(2021), skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(0, resultat.inspektør.avvistDagTeller)
    }

    @Test
    fun `mindre enn 2G, men skjæringstidspunkt er før virkningen av minsteinntekt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 23.mai(2021)
        val `2G_2021` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        val `2G_2020` = Grunnbeløp.`2G`.beløp(30.april(2021))

        val observer = MinsteinntektSubsumsjonObservatør()
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G_2021`).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertEquals(`2G_2020`, Grunnbeløp.`2G`.minsteinntekt(skjæringstidspunkt))
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2020`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)

        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G_2021` - 1.daglig).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2020`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)
    }

    @Test
    fun `mindre enn 2G, og skjæringstidspunkt er etter virkningen av minsteinntekt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 24.mai(2021)
        val `2G_2021` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G_2021`).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertEquals(`2G_2021`, Grunnbeløp.`2G`.minsteinntekt(skjæringstidspunkt))
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2021`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())

        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G_2021` - 1.daglig).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2021`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `begrunnelse tom 67 år - minsteinntekt oppfylt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val inntekt = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        val sykepengegrunnlag = (inntekt).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(0, resultat.inspektør.avvistDagTeller)
    }

    @Test
    fun `begrunnelse tom 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val halvG = Grunnbeløp.halvG.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (halvG - 1.daglig).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(20, resultat.inspektør.avvistDagTeller)
        assertTrue(
            (1.februar(2021) til 1.februar(2021))
                .filterNot { it.erHelg() }
                .all { dato ->
                    resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntekt
                }
        )
        assertTrue(
            (2.februar(2021) til 28.februar(2021))
                .filterNot { it.erHelg() }
                .all { dato ->
                    resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
                }
        )
    }

    @Test
    fun `begrunnelse etter 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 2.februar(2021)
        val `2G` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`2G` - 1.daglig).inntektsgrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(27.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        val resultat = sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX, skjæringstidspunkt til skjæringstidspunkt, jurist).single()
        assertEquals(19, resultat.inspektør.avvistDagTeller)
        assertTrue(
            (2.februar(2021) til 28.februar(2021))
                .filterNot { it.erHelg() }
                .all { dato ->
                    resultat.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
                }
        )
    }

    @Test
    fun `justerer grunnbeløpet`() {
        val sykepengegrunnlag = 60000.månedlig.inntektsgrunnlag("orgnr", 1.mai(2020), 1.mai(2020))
        val justert = sykepengegrunnlag.grunnbeløpsregulering()
        assertNotNull(justert)
        assertNotSame(sykepengegrunnlag, justert)
        assertNotEquals(sykepengegrunnlag.inspektør.sykepengegrunnlag, justert.inspektør.sykepengegrunnlag)
        assertNotEquals(sykepengegrunnlag.inspektør.`6G`, justert.inspektør.`6G`)
        assertTrue(sykepengegrunnlag.inspektør.`6G` < justert.inspektør.`6G`)
        assertTrue(sykepengegrunnlag.inspektør.sykepengegrunnlag < justert.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `sykepengegrunnlaget skal ikke rundes av - 6g-begrenset`() {
        val `6G` = Grunnbeløp.`6G`.beløp(1.januar)
        val sykepengegrunnlag = `6G`.sykepengegrunnlag
        assertEquals(`6G`, sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `sykepengegrunnlaget skal ikke rundes av - under 6`() {
        val daglig = 255.5.daglig
        val sykepengegrunnlag = daglig.sykepengegrunnlag
        assertEquals(daglig, sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `tilkommen inntekt`() {
        val skjæringstidspunkt = 1.januar
        val a1Fom = skjæringstidspunkt
        val a2Fom = skjæringstidspunkt.plusDays(1)
        val a1Inntekt = 25000.månedlig
        val a2Inntekt = 5000.månedlig
        val inntekter = listOf(
            ArbeidsgiverInntektsopplysning("a1", a1Fom til LocalDate.MAX, arbeidsgiverinntekt(a1Fom, a1Inntekt), null, null),
            ArbeidsgiverInntektsopplysning("a2", a2Fom til LocalDate.MAX, arbeidsgiverinntekt(a2Fom, a2Inntekt), null, null),
        )

        val inntektsgrunnlag = Inntektsgrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            arbeidsgiverInntektsopplysninger = inntekter,
            skjæringstidspunkt = skjæringstidspunkt,
            subsumsjonslogg = EmptyLog
        )
        assertEquals(a1Inntekt, inntektsgrunnlag.inspektør.sykepengegrunnlag)
        assertEquals(a1Inntekt, inntektsgrunnlag.inspektør.beregningsgrunnlag)
    }

    @Test
    fun `lager varsel dersom arbeidsforhold har opphørt`() {
        val skjæringstidspunkt = 1.mars
        val sluttdatoA1 = skjæringstidspunkt.minusMonths(1).withDayOfMonth(1)
        val startdatoA2 = skjæringstidspunkt.minusMonths(1).withDayOfMonth(2)

        val inntektsgrunnlag = Inntektsgrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = skjæringstidspunkt,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = skattSykepengegrunnlag(
                        hendelseId = UUID.randomUUID(),
                        dato = skjæringstidspunkt,
                        inntektsopplysninger = listOf(
                            Skatteopplysning(
                                hendelseId = UUID.randomUUID(),
                                beløp = 25000.månedlig,
                                måned = 1.januar.yearMonth,
                                type = LØNNSINNTEKT,
                                fordel = "",
                                beskrivelse = "",
                                tidsstempel = LocalDateTime.now()
                            )
                        )
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false
        )

        val opptjening = Opptjening.nyOpptjening(
            listOf(
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                    a1, listOf(
                    Arbeidsforhold(
                        ansattFom = LocalDate.EPOCH,
                        ansattTom = sluttdatoA1,
                        deaktivert = false
                    )
                )
                ),
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                    a2, listOf(
                    Arbeidsforhold(
                        ansattFom = startdatoA2,
                        ansattTom = null,
                        deaktivert = false
                    )
                )
                )
            ), skjæringstidspunkt
        )

        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, a1)
            aktivitetslogg.assertVarsel(RV_VV_8)
        }

        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.sjekkForNyArbeidsgiver(aktivitetslogg, opptjening, a2)
            aktivitetslogg.assertVarsler(emptyList())
        }
    }

    @Test
    fun `lager varsel ved flere arbeidsgivere med ghost`() {
        val skjæringstidspunkt = 1.mars
        val førsteFraværsdagAG1 = skjæringstidspunkt
        val inntektsgrunnlag = Inntektsgrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = førsteFraværsdagAG1,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = skattSykepengegrunnlag(
                        hendelseId = UUID.randomUUID(),
                        dato = skjæringstidspunkt,
                        inntektsopplysninger = listOf(
                            Skatteopplysning(
                                hendelseId = UUID.randomUUID(),
                                beløp = 25000.månedlig,
                                måned = 1.januar.yearMonth,
                                type = LØNNSINNTEKT,
                                fordel = "",
                                beskrivelse = "",
                                tidsstempel = LocalDateTime.now()
                            )
                        )
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false
        )


        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
            aktivitetslogg.assertVarsel(RV_VV_2)
        }
    }

    @Test
    fun `lager varsel ved flere arbeidsgivere med ulik startdato`() {
        val skjæringstidspunkt = 1.mars
        val førsteFraværsdagAG1 = skjæringstidspunkt
        val førsteFraværsdagAG2 = skjæringstidspunkt.nesteDag
        val inntektsgrunnlag = Inntektsgrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = førsteFraværsdagAG1,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = førsteFraværsdagAG2,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false
        )


        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
            aktivitetslogg.assertVarsel(RV_VV_2)
        }
    }

    @Test
    fun `lager varsel dersom en arbeidsgiver i sykepengegrunnlaget ikke har registrert opptjening`() {
        val skjæringstidspunkt = 1.mars
        val inntektsgrunnlag = Inntektsgrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = skjæringstidspunkt,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = skjæringstidspunkt,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false
        )

        val opptjeningUtenA2 = Opptjening.nyOpptjening(
            listOf(
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                    a1, listOf(
                    Arbeidsforhold(
                        ansattFom = LocalDate.EPOCH,
                        ansattTom = null,
                        deaktivert = false
                    )
                )
                )
            ), skjæringstidspunkt
        )
        val opptjeningMedA2 = Opptjening.nyOpptjening(
            listOf(
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                    a1, listOf(
                    Arbeidsforhold(
                        ansattFom = LocalDate.EPOCH,
                        ansattTom = null,
                        deaktivert = false
                    )
                )
                ),
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                    a2, listOf(
                    Arbeidsforhold(
                        ansattFom = LocalDate.EPOCH,
                        ansattTom = null,
                        deaktivert = false
                    )
                )
                )
            ), skjæringstidspunkt
        )

        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjeningUtenA2)
            aktivitetslogg.assertVarsel(RV_VV_1)
        }

        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.måHaRegistrertOpptjeningForArbeidsgivere(aktivitetslogg, opptjeningMedA2)
            aktivitetslogg.assertVarsler(emptyList())
        }
    }

    @Test
    fun `ikke varsel ved flere arbeidsgivere med samme startdato`() {
        val skjæringstidspunkt = 1.mars
        val førsteFraværsdagAG1 = skjæringstidspunkt
        val førsteFraværsdagAG2 = skjæringstidspunkt
        val inntektsgrunnlag = Inntektsgrunnlag.ferdigSykepengegrunnlag(
            alder = UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a1,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = førsteFraværsdagAG1,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                ),
                ArbeidsgiverInntektsopplysning(
                    orgnummer = a2,
                    gjelder = skjæringstidspunkt til LocalDate.MAX,
                    inntektsopplysning = arbeidsgiverinntekt(
                        dato = førsteFraværsdagAG2,
                        beløp = 25000.månedlig
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false
        )


        Aktivitetslogg().also { aktivitetslogg ->
            inntektsgrunnlag.markerFlereArbeidsgivere(aktivitetslogg)
            aktivitetslogg.assertVarsler(emptyList())
        }
    }

    private class MinsteinntektSubsumsjonObservatør : Subsumsjonslogg {
        var `§ 8-3 ledd 2 punktum 1` by Delegates.notNull<Boolean>()
        var `§ 8-51 ledd 2` by Delegates.notNull<Boolean>()

        override fun logg(subsumsjon: Subsumsjon) {
            when {
                subsumsjon.er(folketrygdloven.paragraf(Paragraf.PARAGRAF_8_3).annetLedd.førstePunktum) ->
                    this.`§ 8-3 ledd 2 punktum 1` = subsumsjon.utfall == VILKAR_OPPFYLT

                subsumsjon.er(folketrygdloven.paragraf(Paragraf.PARAGRAF_8_51).annetLedd) -> {
                    this.`§ 8-51 ledd 2` = subsumsjon.utfall == VILKAR_OPPFYLT
                }
            }
        }
    }
}
