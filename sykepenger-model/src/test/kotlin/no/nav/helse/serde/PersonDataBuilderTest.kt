package no.nav.helse.serde

import java.time.LocalDate
import java.time.Year
import no.nav.helse.EnableFeriepenger
import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson.Companion.AKTØRID
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.TestPerson.Companion.UNG_PERSON_FDATO_2018
import no.nav.helse.dsl.TestPerson.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.dto.AvsenderDto
import no.nav.helse.dto.BegrunnelseDto
import no.nav.helse.dto.DokumenttypeDto
import no.nav.helse.dto.InntektDto
import no.nav.helse.dto.InntektbeløpDto
import no.nav.helse.dto.ProsentdelDto
import no.nav.helse.dto.SykdomstidslinjeDagDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.serialisering.ArbeidsgiverUtDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto
import no.nav.helse.dto.serialisering.UtbetalingsdagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlaghistorikkUtDto
import no.nav.helse.erHelg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.NullObserver
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.oktober
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.AVV
import no.nav.helse.testhelpers.FOR
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.HELG
import no.nav.helse.testhelpers.NAP
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.UKJ
import no.nav.helse.testhelpers.assertInstanceOf
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetalingFilter
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@EnableFeriepenger
internal class PersonDataBuilderTest : AbstractDslTest() {
    private companion object {
        private val IngenBeløp = InntektDto(
            InntektbeløpDto.Årlig(beløp = 0.0),
            InntektbeløpDto.MånedligDouble(beløp = 0.0),
            InntektbeløpDto.DagligDouble(beløp = 0.0),
            InntektbeløpDto.DagligInt(beløp = 0)
        )
        private val IngenGrad = ProsentdelDto(prosent = 0.0)
    }

    /**
     * tester serialisering av person med ulike scenario
     * [x] utbetaling med delvis refusjon
     * [x] auu periode blir omgjort
     * [x] ghost i vilkårsgrunnlag
     * [x] sykmeldingsperiode
     * [x] forkastet vedtaksperiode
     * [x] foreldet periode
     * [x] arbeidsgiverperiodenav-dager
     * [x] feriepenger
     * [x] annullering
     *
     */
    @Test
    fun `serialisering av person`() {
        a1 {
            håndterSøknad(Sykdom(5.januar, 17.januar, 100.prosent))
            håndterInntektsmeldingPortal(listOf(1.januar til 16.januar), inntektsdato = 1.januar, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(
                beløp = INNTEKT/2,
                opphørsdato = 31.januar
            ))
            håndterVilkårsgrunnlag(1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(
                    a1 to INNTEKT
                ), 1.januar),
                arbeidsforhold = listOf(
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017), type = Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT)
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2018))

            håndterAnnullering(inspektør.utbetalinger(1.vedtaksperiode).last().inspektør.utbetalingId)
            håndterUtbetalt()
            håndterSykmelding(Sykmeldingsperiode(1.august, 5.august))
        }
        a2 {
            håndterSøknad(
                Sykdom(3.februar, 28.februar, 100.prosent), Arbeid(21.februar, 28.februar),
                egenmeldinger = listOf(1.februar til 2.februar),
                sendtTilNAVEllerArbeidsgiver = 1.juni
            )
            håndterInntektsmeldingPortal(listOf(1.februar til 16.februar), inntektsdato = 1.februar, førsteFraværsdag = 1.februar)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
        }
        a3 {
            håndterSøknad(Sykdom(1.juni, 16.juni, 100.prosent))
            håndterInntektsmeldingPortal(listOf(1.juni til 16.juni),
                inntektsdato = 1.juni,
                førsteFraværsdag = 1.juni,
                beregnetInntekt = INNTEKT,
                begrunnelseForReduksjonEllerIkkeUtbetalt = "IngenOpptjening",
                refusjon = Inntektsmelding.Refusjon(INGEN, null)
            )
            håndterVilkårsgrunnlag(1.vedtaksperiode, inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(
                a3 to INNTEKT
            ), 1.juni))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterOverstyrArbeidsgiveropplysninger(1.juni, listOf(
                OverstyrtArbeidsgiveropplysning(a3, INNTEKT + 1.daglig, "for lite inntekt")
            ))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        håndterDødsmelding(1.oktober)

        val dto = dto()

        assertEquals(UNG_PERSON_FNR_2018.toString(), dto.fødselsnummer)
        assertEquals(AKTØRID, dto.aktørId)
        assertEquals(UNG_PERSON_FDATO_2018, dto.alder.fødselsdato)
        assertEquals(1.oktober, dto.alder.dødsdato)

        assertArbeidsgivere(dto.arbeidsgivere)
        assertVilkårsgrunnlaghistorikk(dto.vilkårsgrunnlagHistorikk)
        dto.arbeidsgivere[0].feriepengeutbetalinger.also { feriepenger ->
            assertEquals(1, feriepenger.size)
            feriepenger[0].also { feriepenge ->
                assertEquals(2, feriepenge.feriepengeberegner.utbetalteDager.size)
            }
        }
        assertGjenoppbygget(dto)
    }

    @Test
    fun `dto av utbetalingstidslinje`() {
        val input = tidslinjeOf(
            1.AP, 1.NAP, 1.NAV(dekningsgrunnlag = 1200, refusjonsbeløp = 600.0), 1.HELG, 1.ARB, 1.FRI, 1.FOR,
            1.AVV(dekningsgrunnlag = 1000, begrunnelse = Begrunnelse.SykepengedagerOppbrukt),
            1.AVV(dekningsgrunnlag = 500, begrunnelse = Begrunnelse.MinimumInntekt),
            1.UKJ
        )
        val tidslinje = MaksimumUtbetalingFilter().filter(listOf(input), input.periode(), Aktivitetslogg(), NullObserver).single()
        val dto = tidslinje.dto()
        assertEquals(10, dto.dager.size)
        dto.dager[0].also { dag ->
            assertEquals(1.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.ArbeidsgiverperiodeDagDto>(dag)
        }
        dto.dager[1].also { dag ->
            assertEquals(2.januar, dag.dato)
            assertEquals(100.0, dag.økonomi.grad.prosent)
            assertEquals(100.0, dag.økonomi.totalGrad.prosent)
            assertEquals(InntektDto(
                InntektbeløpDto.Årlig(beløp = 312000.0),
                InntektbeløpDto.MånedligDouble(beløp = 26000.0),
                InntektbeløpDto.DagligDouble(beløp = 1200.0),
                InntektbeløpDto.DagligInt(beløp = 1200)
            ), dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.ArbeidsgiverperiodeDagNavDto>(dag)
        }
        dto.dager[2].also { dag ->
            assertEquals(3.januar, dag.dato)
            assertEquals(100.0, dag.økonomi.grad.prosent)
            assertEquals(100.0, dag.økonomi.totalGrad.prosent)
            assertEquals(InntektDto(
                InntektbeløpDto.Årlig(beløp = 156000.0),
                InntektbeløpDto.MånedligDouble(beløp = 13000.0),
                InntektbeløpDto.DagligDouble(beløp = 600.0),
                InntektbeløpDto.DagligInt(beløp = 600)
            ), dag.økonomi.arbeidsgiverbeløp)
            assertEquals(InntektDto(
                InntektbeløpDto.Årlig(beløp = 156000.0),
                InntektbeløpDto.MånedligDouble(beløp = 13000.0),
                InntektbeløpDto.DagligDouble(beløp = 600.0),
                InntektbeløpDto.DagligInt(beløp = 600)
            ), dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.NavDagDto>(dag)
        }
        dto.dager[3].also { dag ->
            assertEquals(4.januar, dag.dato)
            assertEquals(100.0, dag.økonomi.grad.prosent)
            assertEquals(100.0, dag.økonomi.totalGrad.prosent)
            assertEquals(InntektDto(
                InntektbeløpDto.Årlig(beløp = 312000.0),
                InntektbeløpDto.MånedligDouble(beløp = 26000.0),
                InntektbeløpDto.DagligDouble(beløp = 1200.0),
                InntektbeløpDto.DagligInt(beløp = 1200)
            ), dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto. NavHelgDagDto>(dag)
        }
        dto.dager[4].also { dag ->
            assertEquals(5.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.ArbeidsdagDto>(dag)
        }
        dto.dager[5].also { dag ->
            assertEquals(6.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.FridagDto>(dag)
        }
        dto.dager[6].also { dag ->
            assertEquals(7.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.ForeldetDagDto>(dag)
        }
        dto.dager[7].also { dag ->
            assertEquals(8.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.AvvistDagDto>(dag)
            assertEquals(1, dag.begrunnelser.size)
            assertInstanceOf<BegrunnelseDto.SykepengedagerOppbrukt>(dag.begrunnelser.single())
        }
        dto.dager[8].also { dag ->
            assertEquals(9.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.AvvistDagDto>(dag)
            assertEquals(1, dag.begrunnelser.size)
            assertInstanceOf<BegrunnelseDto.MinimumInntekt>(dag.begrunnelser.single())
        }
        dto.dager[9].also { dag ->
            assertEquals(10.januar, dag.dato)
            assertEquals(IngenGrad, dag.økonomi.grad)
            assertEquals(IngenGrad, dag.økonomi.totalGrad)
            assertEquals(IngenBeløp, dag.økonomi.arbeidsgiverbeløp)
            assertEquals(IngenBeløp, dag.økonomi.personbeløp)
            assertInstanceOf<UtbetalingsdagUtDto.UkjentDagDto>(dag)
        }
    }

    private fun assertArbeidsgivere(arbeidsgivere: List<ArbeidsgiverUtDto>) {
        assertEquals(3, arbeidsgivere.size)

        arbeidsgivere[0].also { arbeidsgiver ->
            assertEquals(a1, arbeidsgiver.organisasjonsnummer)
            assertEquals(1, arbeidsgiver.inntektshistorikk.historikk.size)
            arbeidsgiver.inntektshistorikk.historikk[0].also { inntektsmelding ->
                assertEquals(1.januar, inntektsmelding.dato)
                val forventetInntekt = INNTEKT
                assertEquals(forventetInntekt.reflection { årlig, _, _, _ -> årlig }, inntektsmelding.beløp.årlig.beløp)
                assertEquals(forventetInntekt.reflection { _, månedligDouble, _, _ -> månedligDouble }, inntektsmelding.beløp.månedligDouble.beløp)
                assertEquals(forventetInntekt.reflection { _, _, dagligDouble, _ -> dagligDouble }, inntektsmelding.beløp.dagligDouble.beløp)
                assertEquals(forventetInntekt.reflection { _, _, _, dagligInt -> dagligInt }, inntektsmelding.beløp.dagligInt.beløp)
            }
            assertEquals(3, arbeidsgiver.sykdomshistorikk.elementer.size)
            arbeidsgiver.sykdomshistorikk.elementer[2].also { sykdomshistorikkElement ->
                assertEquals(13, sykdomshistorikkElement.hendelseSykdomstidslinje.dager.size)
                sykdomshistorikkElement.hendelseSykdomstidslinje.dager.also { dager ->
                    val forventetPeriode = 5.januar til 17.januar
                    forventetPeriode.forEach { dato ->
                        val dagen = dager.single { it.dato == dato }
                        assertEquals("Søknad", dagen.kilde.type)
                        if (dato.erHelg()) assertInstanceOf<SykdomstidslinjeDagDto.SykHelgedagDto>(dagen)
                        else assertInstanceOf<SykdomstidslinjeDagDto.SykedagDto>(dagen)
                    }
                }
                assertEquals(13, sykdomshistorikkElement.beregnetSykdomstidslinje.dager.size)
            }
            assertEquals(1, arbeidsgiver.sykmeldingsperioder.perioder.size)
            arbeidsgiver.sykmeldingsperioder.perioder[0].also { periode ->
                assertEquals(1.august, periode.fom)
                assertEquals(5.august, periode.tom)
            }

            assertEquals(2, arbeidsgiver.utbetalinger.size)
            assertEquals(0, arbeidsgiver.vedtaksperioder.size)
            assertEquals(1, arbeidsgiver.forkastede.size)
            arbeidsgiver.forkastede[0].vedtaksperiode.also { vedtaksperiode ->
                assertEquals(VedtaksperiodetilstandDto.TIL_INFOTRYGD, vedtaksperiode.tilstand)
                assertEquals(3, vedtaksperiode.behandlinger.behandlinger.size)
                vedtaksperiode.behandlinger.behandlinger[0].also { behandling ->
                    assertEquals(AvsenderDto.SYKMELDT, behandling.kilde.avsender)
                    assertNull(behandling.vedtakFattet)
                    assertNotNull(behandling.avsluttet)
                    assertEquals(2, behandling.endringer.size)
                    behandling.endringer[0].also { endring ->
                        assertEquals(5.januar, endring.periode.fom)
                        assertEquals(17.januar, endring.periode.tom)
                        assertEquals(5.januar, endring.sykmeldingsperiode.fom)
                        assertEquals(17.januar, endring.sykmeldingsperiode.tom)
                        assertEquals(DokumenttypeDto.Søknad, endring.dokumentsporing.type)
                        assertEquals(13, endring.sykdomstidslinje.dager.size)
                        assertEquals(5.januar, endring.sykdomstidslinje.periode?.fom)
                        assertEquals(17.januar, endring.sykdomstidslinje.periode?.tom)
                        assertEquals(LocalDate.MIN, endring.skjæringstidspunkt)
                    }
                    behandling.endringer[1].also { endring ->
                        assertEquals(5.januar, endring.periode.fom)
                        assertEquals(17.januar, endring.periode.tom)
                        assertEquals(5.januar, endring.sykmeldingsperiode.fom)
                        assertEquals(17.januar, endring.sykmeldingsperiode.tom)
                        assertEquals(DokumenttypeDto.Søknad, endring.dokumentsporing.type)
                        assertEquals(13, endring.sykdomstidslinje.dager.size)
                        assertEquals(5.januar, endring.sykdomstidslinje.periode?.fom)
                        assertEquals(17.januar, endring.sykdomstidslinje.periode?.tom)
                        assertEquals(5.januar, endring.skjæringstidspunkt)
                    }
                }
            }
        }
    }
    private fun assertVilkårsgrunnlaghistorikk(historikk: VilkårsgrunnlaghistorikkUtDto) {
        assertEquals(6, historikk.historikk.size)
        historikk.historikk[5].also { innslag ->
            assertEquals(1, innslag.vilkårsgrunnlag.size)
            innslag.vilkårsgrunnlag[0].also { vilkårsgrunnlagDto ->
                assertEquals(2, vilkårsgrunnlagDto.sykepengegrunnlag.arbeidsgiverInntektsopplysninger.size)
                vilkårsgrunnlagDto.sykepengegrunnlag.arbeidsgiverInntektsopplysninger[0].also { arbeidsgiverInntektsopplysningDto ->
                    assertInstanceOf<InntektsopplysningUtDto.InntektsmeldingDto>(arbeidsgiverInntektsopplysningDto.inntektsopplysning)
                    assertEquals(InntektDto(
                        InntektbeløpDto.Årlig(beløp = 372000.0),
                        InntektbeløpDto.MånedligDouble(beløp = 31000.0),
                        InntektbeløpDto.DagligDouble(beløp = 1430.7692307692307),
                        InntektbeløpDto.DagligInt(beløp = 1430)
                    ), arbeidsgiverInntektsopplysningDto.inntektsopplysning.beløp)
                }
                vilkårsgrunnlagDto.sykepengegrunnlag.arbeidsgiverInntektsopplysninger[1].also { arbeidsgiverInntektsopplysningDto ->
                    assertInstanceOf<InntektsopplysningUtDto.IkkeRapportertDto>(arbeidsgiverInntektsopplysningDto.inntektsopplysning)
                    assertNull(arbeidsgiverInntektsopplysningDto.inntektsopplysning.beløp)
                }
            }
        }
    }
}