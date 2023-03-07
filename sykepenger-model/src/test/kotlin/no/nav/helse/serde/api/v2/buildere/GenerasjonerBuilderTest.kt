package no.nav.helse.serde.api.v2.buildere

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Toggle
import no.nav.helse.april
import no.nav.helse.desember
import no.nav.helse.erHelg
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Utdanning
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Utlandsopphold
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.arbeidsgiver
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.serde.api.dto.BeregnetPeriode
import no.nav.helse.serde.api.dto.Generasjon
import no.nav.helse.serde.api.dto.Inntektkilde
import no.nav.helse.serde.api.dto.OmregnetÅrsinntekt
import no.nav.helse.serde.api.dto.Periodetilstand
import no.nav.helse.serde.api.dto.Periodetilstand.Annullert
import no.nav.helse.serde.api.dto.Periodetilstand.ForberederGodkjenning
import no.nav.helse.serde.api.dto.Periodetilstand.IngenUtbetaling
import no.nav.helse.serde.api.dto.Periodetilstand.ManglerInformasjon
import no.nav.helse.serde.api.dto.Periodetilstand.RevurderingFeilet
import no.nav.helse.serde.api.dto.Periodetilstand.TilAnnullering
import no.nav.helse.serde.api.dto.Periodetilstand.TilGodkjenning
import no.nav.helse.serde.api.dto.Periodetilstand.TilUtbetaling
import no.nav.helse.serde.api.dto.Periodetilstand.Utbetalt
import no.nav.helse.serde.api.dto.Periodetilstand.UtbetaltVenterPåAnnenPeriode
import no.nav.helse.serde.api.dto.Periodetilstand.VenterPåAnnenPeriode
import no.nav.helse.serde.api.dto.SykdomstidslinjedagType
import no.nav.helse.serde.api.dto.SykdomstidslinjedagType.FORELDET_SYKEDAG
import no.nav.helse.serde.api.dto.Tidslinjeperiode
import no.nav.helse.serde.api.dto.UberegnetPeriode
import no.nav.helse.serde.api.dto.Utbetalingstatus
import no.nav.helse.serde.api.dto.Utbetalingtype
import no.nav.helse.serde.api.dto.Utbetalingtype.ANNULLERING
import no.nav.helse.serde.api.dto.Utbetalingtype.REVURDERING
import no.nav.helse.serde.api.dto.Utbetalingtype.UTBETALING
import no.nav.helse.serde.api.dto.Vilkårsgrunnlag
import no.nav.helse.serde.api.speil.builders.GenerasjonerBuilder
import no.nav.helse.serde.api.speil.builders.VilkårsgrunnlagBuilder
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.forkastAlle
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterAnnullerUtbetaling
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.spleis.e2e.manuellSykedag
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.søknadDTOer
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class GenerasjonerBuilderTest : AbstractEndToEndTest() {

    @Test
    fun `happy case`() {
        nyttVedtak(1.januar, 31.januar)

        assertEquals(1, generasjoner.size)
        assertEquals(1, generasjoner[0].perioder.size)
        0.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `person med foreldet dager`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.juni)
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        0.generasjon {
            assertEquals(1, perioder.size)
            uberegnetPeriode(0) harTidslinje (1.januar til 31.januar to FORELDET_SYKEDAG) medTilstand ForberederGodkjenning
        }
    }

    @Test
    fun `Sender unike advarsler per periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.april)
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, arbeidsavklaringspenger = listOf(1.januar.minusDays(60) til 31.januar.minusDays(60)))
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        0.generasjon {
            val dedupedAktivitetslogg = beregnetPeriode(0).aktivitetslogg.distinctBy { it.melding }
            assertEquals(dedupedAktivitetslogg, beregnetPeriode(0).aktivitetslogg)
        }
    }

    @Test
    fun `annullerer feilet revurdering`() {
        nyttVedtak(1.januar, 31.januar)

        håndterOverstyrTidslinje()
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        håndterAnnullerUtbetaling()

        0.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Overført avType ANNULLERING medTilstand TilAnnullering
        }

        1.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
        }

        2.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
        }
    }

    @Test
    fun `Sender med varsler for tidligere periode som er avsluttet uten utbetaling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 9.januar))
        håndterSøknad(Sykdom(1.januar, 9.januar, 100.prosent), Utdanning(3.januar, 4.januar))

        håndterSykmelding(Sykmeldingsperiode(10.januar, 25.januar))
        håndterSøknad(Sykdom(10.januar, 25.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        0.generasjon {
            val periode = beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            assertNotEquals(periode.vedtaksperiodeId, periode.aktivitetslogg[0].vedtaksperiodeId)
        }
    }

    @Test
    fun `Sender med varsler for alle tidligere tilstøtende perioder som er avsluttet uten utbetaling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 9.januar))
        håndterSøknad(Sykdom(1.januar, 9.januar, 100.prosent), Utdanning(3.januar, 4.januar)) // Warning

        håndterSykmelding(Sykmeldingsperiode(10.januar, 14.januar))
        håndterSøknad(Sykdom(10.januar, 14.januar, 100.prosent), Utlandsopphold(11.januar, 12.januar)) // Warning
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        håndterSykmelding(Sykmeldingsperiode(15.januar, 25.januar))
        håndterSøknad(Sykdom(15.januar, 25.januar, 100.prosent))

        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        0.generasjon {
            val periode = beregnetPeriode(0) avType UTBETALING er Utbetalingstatus.Utbetalt medTilstand Utbetalt
            assertNotEquals(periode.vedtaksperiodeId, periode.aktivitetslogg[0].vedtaksperiodeId)
            assertNotEquals(periode.vedtaksperiodeId, periode.aktivitetslogg[1].vedtaksperiodeId)
        }
    }

    @Test
    fun `arbeidsgivere uten vedtaksperioder filtreres bort`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        forkastAlle(hendelselogg)
        assertEquals(0, generasjoner.size)
    }

    @Test
    fun `Vedtaksperioder fra flere arbeidsgivere får ikke samme vilkårsgrunnlag-warnings`() {
        val fom = 1.januar
        val tom = 31.januar

        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a2)
        håndterSøknad(Sykdom(fom, tom, 100.prosent), sendtTilNAVEllerArbeidsgiver = fom.plusDays(1), orgnummer = a1)
        håndterSøknad(Sykdom(fom, tom, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            refusjon = Inntektsmelding.Refusjon(beløp = 1000.månedlig, opphørsdato = null, endringerIRefusjon = emptyList()),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            refusjon = Inntektsmelding.Refusjon(beløp = 1000.månedlig, opphørsdato = null, endringerIRefusjon = emptyList()),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a2
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 1000.månedlig
                    a2 inntekt 1000.månedlig
                }
            }),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, automatiskBehandling = false, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, automatiskBehandling = false, orgnummer = a2)

        0.generasjon(a1) {
            assertTrue(beregnetPeriode(0).aktivitetslogg.any { it.melding == "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag" })
        }

        0.generasjon(a2) {
            assertFalse(beregnetPeriode(0).aktivitetslogg.any { it.melding == "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag" })
        }
    }

    @Test
    fun `Akkumulerer inntekter fra a-orningen pr måned`() {
        val fom = 1.januar
        val tom = 31.januar

        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a1)
        håndterSøknad(Sykdom(fom, tom, 100.prosent), sendtTilNAVEllerArbeidsgiver = fom.plusDays(1), orgnummer = a1)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            refusjon = Inntektsmelding.Refusjon(
                beløp = 1000.månedlig,
                opphørsdato = null,
                endringerIRefusjon = emptyList()
            ),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a1
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 1000.månedlig
                    a2 inntekt 600.månedlig
                    a2 inntekt 400.månedlig
                }
            }),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntektperioderForSykepengegrunnlag {
                1.oktober(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 1000.månedlig
                    a2 inntekt 600.månedlig
                    a2 inntekt 400.månedlig
                }
            }, arbeidsforhold = emptyList()),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH),
                Arbeidsforhold(a2, LocalDate.EPOCH)
            ),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, automatiskBehandling = false)

        0.generasjon(a1) {
            val periode = beregnetPeriode(0)
            val vilkårsgrunnlag = periode.vilkårsgrunnlag()
            val omregnetÅrsinntekt = vilkårsgrunnlag.inntekter.first { it.organisasjonsnummer == a2 }.omregnetÅrsinntekt
            assertEquals(3, omregnetÅrsinntekt?.inntekterFraAOrdningen?.size)
            assertTrue(omregnetÅrsinntekt?.inntekterFraAOrdningen?.all { it.sum == 1000.0 } ?: false)
        }
    }

    @Test
    fun `tar med vilkårsgrunnlag med ikke-rapportert inntekt`() {
        // A2 må være først i listen for at buggen skal intreffe
        nyttVedtak(1.januar(2017), 31.januar(2017), 100.prosent, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt INNTEKT
                }
            }),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntektperioderForSykepengegrunnlag {
                    1.oktober(2017) til 1.desember(2017) inntekter {
                        a1 inntekt INNTEKT
                    }
                },
                emptyList()
            ),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Arbeidsforhold(a2, 1.desember(2017), null)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        0.generasjon(a1) {
            val vilkårsgrunnlag = beregnetPeriode(0).vilkårsgrunnlag()
            val inntektsgrunnlag = vilkårsgrunnlag.inntekter.firstOrNull { it.organisasjonsnummer == a2 }
            assertEquals(
                OmregnetÅrsinntekt(
                    Inntektkilde.IkkeRapportert,
                    0.0,
                    0.0,
                    null
                ),
                inntektsgrunnlag?.omregnetÅrsinntekt
            )
        }
    }


    @Test
    fun `happy case med periode til godkjenning`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)

        assertEquals(1, generasjoner.size)
        assertEquals(1, generasjoner[0].perioder.size)
        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
        }
    }

    @Test
    fun `happy case med to perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        assertEquals(1, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `happy case med to perioder med gap`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(2.februar, 28.februar)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (2.februar til 28.februar) medAntallDager 27 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `periode blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(1, generasjoner[0].perioder.size)
        assertEquals(1, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand ForberederGodkjenning
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `periode blir revurdert og utbetalt`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(2, generasjoner.size)
        assertEquals(1, generasjoner[0].perioder.size)
        assertEquals(1, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `forlengelse blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((27.februar til 28.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand ForberederGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder - første blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand ForberederGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand VenterPåAnnenPeriode
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder - revurdering til godkjenning`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand TilGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder - første blir revurdert to ganger`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellSykedag(it) })
        håndterYtelser(2.vedtaksperiode)

        assertEquals(3, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)
        assertEquals(2, generasjoner[2].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand ForberederGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand VenterPåAnnenPeriode
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        2.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder - første blir revurdert to ganger, deretter blir andre revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellSykedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrTidslinje((27.februar til 28.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        assertEquals(4, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)
        assertEquals(2, generasjoner[2].perioder.size)
        assertEquals(2, generasjoner[3].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand ForberederGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        2.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        3.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder med gap - siste blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(2.februar, 28.februar)

        håndterOverstyrTidslinje((27.februar til 28.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (2.februar til 28.februar) medAntallDager 27 forkastet false medTilstand ForberederGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (2.februar til 28.februar) medAntallDager 27 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `én periode som blir annullert`() {
        nyttVedtak(1.januar, 31.januar)
        håndterAnnullerUtbetaling()

        assertEquals(2, generasjoner.size)
        assertEquals(1, generasjoner[0].perioder.size)
        assertEquals(1, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Overført avType ANNULLERING fra (1.januar til 31.januar) medAntallDager 31 forkastet true medTilstand TilAnnullering
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet true medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder som blir annullert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterAnnullerUtbetaling()

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Overført avType ANNULLERING fra (1.februar til 28.februar) medAntallDager 28 forkastet true medTilstand TilAnnullering
            beregnetPeriode(1) er Utbetalingstatus.Overført avType ANNULLERING fra (1.januar til 31.januar) medAntallDager 31 forkastet true medTilstand TilAnnullering
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet true medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet true medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder som blir annullert - deretter nye perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        nyttVedtak(1.april, 30.april)

        assertEquals(2, generasjoner.size)
        assertEquals(3, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.april til 30.april) medAntallDager 30 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Annullert avType ANNULLERING fra (1.februar til 28.februar) medAntallDager 28 forkastet true medTilstand Annullert
            beregnetPeriode(2) er Utbetalingstatus.Annullert avType ANNULLERING fra (1.januar til 31.januar) medAntallDager 31 forkastet true medTilstand Annullert
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet true medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet true medTilstand Utbetalt
        }
    }

    @Test
    fun `to arbeidsgiverperioder - siste blir annullert`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        håndterAnnullerUtbetaling()

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Overført avType ANNULLERING fra (1.mars til 31.mars) medAntallDager 31 forkastet true medTilstand TilAnnullering
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medAntallDager 31 forkastet true medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder som blir revurdert - deretter forlengelse`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        forlengVedtak(1.mars, 31.mars)

        assertEquals(2, generasjoner.size)
        assertEquals(3, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(2) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false  medTilstand Utbetalt
        }
    }

    @Test
    fun `to perioder som blir revurdert - deretter forlengelse som så blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        forlengVedtak(1.mars, 31.mars)
        håndterOverstyrTidslinje((1.mars til 31.mars).map { manuellFeriedag(it) })
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(3, generasjoner.size)
        assertEquals(3, generasjoner[0].perioder.size)
        assertEquals(3, generasjoner[1].perioder.size)
        assertEquals(2, generasjoner[2].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand IngenUtbetaling
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(2) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(2) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }

        2.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `korte perioder - arbeidsgiversøknader`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.januar))
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))

        0.generasjon {
            uberegnetPeriode(0) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `kort periode med forlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.januar))
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(16.januar, 15.februar))
        håndterSøknad(Sykdom(16.januar, 15.februar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        assertEquals(1, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType UTBETALING fra (16.januar til 15.februar) medAntallDager 31 forkastet false medTilstand ForberederGodkjenning
            uberegnetPeriode(1) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `kort periode med forlengelse og revurdering av siste periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.januar))
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(16.januar, 15.februar))
        håndterSøknad(Sykdom(16.januar, 15.februar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrTidslinje((13.februar til 14.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(2, generasjoner[1].perioder.size)

        0.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (16.januar til 15.februar) medAntallDager 31 forkastet false medTilstand ForberederGodkjenning
            uberegnetPeriode(1) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (16.januar til 15.februar) medAntallDager 31 forkastet false medTilstand Utbetalt
            uberegnetPeriode(1) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `to perioder etter hverandre, nyeste er i venter-tilstand`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 21.februar))
        håndterSøknad(Sykdom(1.februar, 21.februar, 100.prosent))

        assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
        }
    }

    @Test
    fun `to førstegangsbehandlinger, nyeste er i venter-tilstand`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand ManglerInformasjon
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
        }
    }

    @Test
    fun `tidligere generasjoner skal ikke inneholde perioder som venter eller venter på informasjon`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        0.generasjon {
            assertEquals(2, this.perioder.size)
            uberegnetPeriode(0) medTilstand ManglerInformasjon
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
        1.generasjon {
            assertEquals(1, this.perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `periode som har tilstand TIL_INFOTRYGD sendes ikke med til Speil`() {
        val fyller18November2018 = "02110075045".somPersonidentifikator()
        createTestPerson(fyller18November2018, 2.november(2000))
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars), fnr = fyller18November2018)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), fnr = fyller18November2018)

        assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertEquals(emptyList<Generasjon>(), generasjoner)
    }

    @Test
    fun `ventende periode`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertEquals(1, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)

        0.generasjon {
            uberegnetPeriode(0) fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand ForberederGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `ventende perioder med revurdert tidligere periode`() {
        nyttVedtak(1.januar, 31.januar)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        håndterOverstyrTidslinje((29.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        assertEquals(2, generasjoner[0].perioder.size)
        assertEquals(1, generasjoner[1].perioder.size)

        0.generasjon {
            uberegnetPeriode(0) fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand ForberederGodkjenning
        }

        1.generasjon {
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
        }
    }

    @Test
    fun `periode uten utbetaling - kun ferie`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertEquals(1, generasjoner.size)
        assertEquals(1, generasjoner[0].perioder.size)
        0.generasjon {
            uberegnetPeriode(0) fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `får riktig aldersvilkår per periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0).assertAldersvilkår(true, 26)
            beregnetPeriode(1).assertAldersvilkår(true, 25)
        }
    }

    @Test
    fun `får riktig sykepengedager-vilkår per periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0).assertSykepengedagerVilkår(31, 217, 28.desember, 1.januar, true)
            beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
        }
    }

    @Test
    fun `får riktig søknadsfrist-vilkår per periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
        }
    }

    @Test
    fun `får riktig vilkår per periode ved revurdering av siste periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje((27.februar til 28.februar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0).assertAldersvilkår(true, 26)
            beregnetPeriode(1).assertAldersvilkår(true, 25)
            beregnetPeriode(0).assertSykepengedagerVilkår(29, 219, 1.januar(2019), 1.januar, true)
            beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
        }
        1.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
            beregnetPeriode(0).assertSykepengedagerVilkår(31, 217, 28.desember, 1.januar, true)
            beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
        }
    }

    @Test
    fun `får riktig vilkår per periode ved revurdering av første periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje((30.januar til 31.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)

        0.generasjon {
            beregnetPeriode(0).assertAldersvilkår(true, 26)
            beregnetPeriode(1).assertAldersvilkår(true, 25)
            // Revurdering av tidligere periode medfører at alle perioder berørt av revurderingen deler den samme utbetalingen, og derfor ender opp med samme
            // gjenstående dager, forbrukte dager og maksdato. Kan muligens skrives om i modellen slik at disse tallene kan fiskes ut fra utbetalingen gitt en
            // periode
            beregnetPeriode(0).assertSykepengedagerVilkår(29, 219, 1.januar(2019), 1.januar, true)
            beregnetPeriode(1).assertSykepengedagerVilkår(29, 219, 1.januar(2019), 1.januar, true)
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
        }
        1.generasjon {
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
            beregnetPeriode(0).assertSykepengedagerVilkår(31, 217, 28.desember, 1.januar, true)
            beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            beregnetPeriode(0).assertSøknadsfristVilkår(1.februar, 28.februar, 28.februar.atStartOfDay(), true)
            beregnetPeriode(1).assertSøknadsfristVilkår(1.januar, 31.januar, 31.januar.atStartOfDay(), true)
        }
    }

    @Test
    fun `ta med personoppdrag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            refusjon = Inntektsmelding.Refusjon(0.månedlig, null),
            førsteFraværsdag = 1.januar,
            arbeidsgiverperioder = listOf(1.januar til 16.januar)
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        0.generasjon {
            assertEquals(1, perioder.size)
            assertEquals(0, this.perioder.first().sammenslåttTidslinje[16].utbetalingsinfo!!.utbetaling)
            assertEquals(0, this.perioder.first().sammenslåttTidslinje[16].utbetalingsinfo!!.arbeidsgiverbeløp)
            assertEquals(0, this.perioder.first().sammenslåttTidslinje[16].utbetalingsinfo!!.refusjonsbeløp)
            assertEquals(1431, this.perioder.first().sammenslåttTidslinje[16].utbetalingsinfo!!.personbeløp)
            assertEquals(0, beregnetPeriode(0).utbetaling.arbeidsgiverNettoBeløp)
            assertEquals(15741, beregnetPeriode(0).utbetaling.personNettoBeløp)
        }
    }

    @Test
    fun `kun førstegangsbehandling har warnings fra vilkårsprøving`() {
        val fom = 1.januar
        val tom = 31.januar
        val forlengelseFom = 1.februar
        val forlengelseTom = 28.februar

        håndterSykmelding(Sykmeldingsperiode(fom, tom))
        håndterSøknad(Sykdom(fom, tom, 100.prosent), sendtTilNAVEllerArbeidsgiver = fom.plusDays(1))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            refusjon = Inntektsmelding.Refusjon(
                beløp = 1000.månedlig,
                opphørsdato = null,
                endringerIRefusjon = emptyList()
            ),
            beregnetInntekt = 1000.månedlig
        )
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt 1000.månedlig
                }
            })
        )
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, automatiskBehandling = false)

        håndterSykmelding(Sykmeldingsperiode(forlengelseFom, forlengelseTom))
        håndterSøknad(
            Sykdom(forlengelseFom, forlengelseTom, 100.prosent),
            sendtTilNAVEllerArbeidsgiver = forlengelseTom
        )
        håndterYtelser(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, automatiskBehandling = false)

        assertEquals(1, generasjoner.size)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.GodkjentUtenUtbetaling avType UTBETALING fra (1.februar til 28.februar) utenWarning "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag" medTilstand IngenUtbetaling
            beregnetPeriode(1) er Utbetalingstatus.GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) medWarning "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag" medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `kun første arbeidsgiver har warnings fra vilkårsprøving`() {
        val fom = 1.januar
        val tom = 31.januar

        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(fom, tom), orgnummer = a2)
        håndterSøknad(Sykdom(fom, tom, 100.prosent), sendtTilNAVEllerArbeidsgiver = fom.plusDays(1), orgnummer = a1)
        håndterSøknad(Sykdom(fom, tom, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            refusjon = Inntektsmelding.Refusjon(
                beløp = 1000.månedlig,
                opphørsdato = null,
                endringerIRefusjon = emptyList()
            ),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(fom til fom.plusDays(15)),
            refusjon = Inntektsmelding.Refusjon(
                beløp = 1000.månedlig,
                opphørsdato = null,
                endringerIRefusjon = emptyList()
            ),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a2
        )


        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 1000.månedlig
                    a2 inntekt 1000.månedlig
                }
            }),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, automatiskBehandling = false, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, automatiskBehandling = false, orgnummer = a2)

        0.generasjon(a1) {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) medWarning "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag" medTilstand IngenUtbetaling
        }
        0.generasjon(a2) {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) utenWarning "Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag" medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `ag2 venter på ag1 mens ag1 er til godkjenning`() {
        tilGodkjenning(1.januar, 31.januar, a1, a2)

        0.generasjon(a1) {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) medTilstand TilGodkjenning
        }
        0.generasjon(a2) {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) medTilstand VenterPåAnnenPeriode
        }
    }

    @Test
    fun `periode med bare ferie`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 20.februar))
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent), Ferie(1.februar, 20.februar))
        håndterYtelser(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.GodkjentUtenUtbetaling medTilstand IngenUtbetaling
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt medTilstand Utbetalt
        }
    }

    @Test
    fun `behandlingstyper i normal forlengelsesflyt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand ManglerInformasjon
            uberegnetPeriode(1) medTilstand ManglerInformasjon
        }

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            uberegnetPeriode(1) medTilstand ForberederGodkjenning
        }

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            uberegnetPeriode(1) medTilstand ForberederGodkjenning
        }

        håndterYtelser(1.vedtaksperiode)
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand ForberederGodkjenning
        }

        håndterSimulering(1.vedtaksperiode)
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand TilGodkjenning
        }

        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand TilUtbetaling
        }

        håndterUtbetalt()
        0.generasjon {
            assertEquals(2, perioder.size)
            uberegnetPeriode(0) medTilstand ForberederGodkjenning
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `Annullering av revurdering feilet`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        nullstillTilstandsendringer()
        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, REVURDERING_FEILET)

        assertEquals(3, generasjoner.size)
        0.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Annullert avType ANNULLERING medTilstand Annullert
        }

        1.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
        }

        2.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
        }
    }

    @Test
    fun `revurdering feilet med flere perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
            beregnetPeriode(1) er Utbetalingstatus.IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
        }

        1.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
        }
    }

    @Test
    fun `utbetaling feilet`() {
        tilGodkjenning(1.januar, 31.januar, grad = 100.prosent, førsteFraværsdag = 1.januar)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(status = Oppdragstatus.FEIL)

        assertTilstand(1.vedtaksperiode, TIL_UTBETALING)
        0.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Overført avType UTBETALING medTilstand TilUtbetaling
        }
    }

    @Test
    fun `revurdering av flere arbeidsgivere`() {
        nyeVedtak(1.januar, 31.januar, a1, a2)
        forlengVedtak(1.februar, 28.februar, a1, a2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)

        0.generasjon(a1) {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand ForberederGodkjenning
            beregnetPeriode(1) medTilstand VenterPåAnnenPeriode
        }
        0.generasjon(a2) {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand VenterPåAnnenPeriode
        }

        håndterSimulering(2.vedtaksperiode, orgnummer = a1)

        0.generasjon(a1) {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand TilGodkjenning
            beregnetPeriode(1) medTilstand TilGodkjenning
        }
        0.generasjon(a2) {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand VenterPåAnnenPeriode
        }

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)

        0.generasjon(a1) {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }
        0.generasjon(a2) {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand TilGodkjenning
            beregnetPeriode(1) medTilstand TilGodkjenning
        }
    }

    @Test
    fun `flere revurderinger, deretter revurdering feilet`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser()
        håndterSimulering()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.januar, Dagtype.Feriedag)))
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        assertEquals(2, generasjoner.size)

        0.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) medTilstand RevurderingFeilet
        }
        1.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
        }
    }

    @Test
    fun `revurdering til kun ferie`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(List(16) { ManuellOverskrivingDag(16.januar.plusDays(it.toLong()), Dagtype.Feriedag) })
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        assertEquals(2, generasjoner.size)
        0.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) medTilstand IngenUtbetaling
        }

        1.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
        }
    }

    @Test
    fun `annullering av revurdert periode i til godkjenning`() {
        nyttVedtak(1.mars, 31.mars)

        nyttVedtak(1.mai, 31.mai)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mai, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.mai, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        assertEquals(2, generasjoner.size)
        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Utbetalt
        }
        1.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `annullering av flere perioder`() {
        nyttVedtak(1.mars, 31.mars)

        nyttVedtak(1.mai, 31.mai)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mai, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.mai, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        assertEquals(2, generasjoner.size)
        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
        }
        1.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `annullering av enda flere perioder`() {
        nyttVedtak(1.mars, 31.mars)

        nyttVedtak(1.mai, 31.mai)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mai, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.mai, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        nyttVedtak(1.juli, 31.juli)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(3.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        assertEquals(3, generasjoner.size)
        0.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Annullert
        }
        1.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Annullert
        }
        2.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `flere perioder der første blir annullert, deretter ny periode, deretter annullering igjen`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        nyttVedtak(1.mai, 31.mai)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(3.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        assertEquals(3, generasjoner.size)

        0.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt
        }
        1.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt
        }
        2.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()
        assertEquals(3, generasjoner.size)

        0.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Annullert
        }
        1.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt
        }
        2.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `flere perioder der første blir annullert, deretter ny periode, deretter annullering igjen 2`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        nyttVedtak(1.mai, 31.mai)

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(3.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(4, generasjoner.size)

        0.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt avType REVURDERING
        }
        1.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt avType UTBETALING
        }
        2.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt avType UTBETALING
        }
        3.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }

        håndterAnnullerUtbetaling(fagsystemId = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.arbeidsgiverOppdrag.fagsystemId())
        håndterUtbetalt()
        assertEquals(5, generasjoner.size)

        0.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Annullert
        }
        1.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt avType REVURDERING
        }
        2.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Annullert
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt avType UTBETALING
        }
        3.generasjon {
            assertEquals(3, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Annullert
            beregnetPeriode(2) medTilstand Utbetalt avType UTBETALING
        }
        4.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand Utbetalt
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `revurdering av tidligere skjæringstidspunkt`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))

        assertEquals(1, generasjoner.size)
        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand Utbetalt
        }

        håndterYtelser(1.vedtaksperiode)

        assertEquals(2, generasjoner.size)
        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand ForberederGodkjenning
        }
        1.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand Utbetalt
        }
    }

    @Test
    fun `inntektsmelding gjør at kort periode faller utenfor agp - før vilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(28.januar, 31.januar))
        håndterSøknad(Sykdom(28.januar, 31.januar, 100.prosent))

        nullstillTilstandsendringer()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterInntektsmelding(listOf(10.januar til 25.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertEquals(1, generasjoner.size)
        0.generasjon {
            assertEquals(3, perioder.size)
            uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
            beregnetPeriode(1) avType UTBETALING medTilstand TilGodkjenning
            uberegnetPeriode(2) medTilstand IngenUtbetaling
        }

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
        assertTilstander(3.vedtaksperiode,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE
        )
    }

    @Test
    @Disabled("går igjennom denne med Maxi og Simen")
    fun `inntektsmelding for to korte perioder`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(24.januar, 30.januar))
        håndterSøknad(Sykdom(24.januar, 30.januar, 100.prosent))

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(2.januar til 17.januar), førsteFraværsdag = 24.januar)
        assertEquals("UUUUGG UUUUSHH SSSSSH? ??SSSHH SS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString())
        assertEquals(2.januar til 17.januar, inspektør.arbeidsgiverperiode(1.vedtaksperiode))
        assertEquals(2.januar til 17.januar, inspektør.arbeidsgiverperiode(2.vedtaksperiode))
        val arbeidsgiverperioden = inspektør.arbeidsgiverperioden(2.vedtaksperiode)!!
        assertFalse(arbeidsgiverperioden.erFørsteUtbetalingsdagFørEllerLik(17.januar.somPeriode()))
        assertTrue(arbeidsgiverperioden.erFørsteUtbetalingsdagFørEllerLik(18.januar.somPeriode()))

        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertEquals(1, generasjoner.size)
        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) avType UTBETALING medTilstand TilGodkjenning
            uberegnetPeriode(1) medTilstand IngenUtbetaling
        }

        håndterInntektsmelding(listOf(2.januar til 17.januar), førsteFraværsdag = 2.januar)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalt()

        håndterYtelser(2.vedtaksperiode)

        assertEquals(1, generasjoner.size)
        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) avType UTBETALING medTilstand ForberederGodkjenning
            beregnetPeriode(1) avType UTBETALING medTilstand Utbetalt
        }

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
    }

    @Test
    fun `avvist revurdering uten tidligere utbetaling kan forkastes`() {
        håndterSykmelding(Sykmeldingsperiode(12.januar, 20.januar))
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(21.januar, 27.januar))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))

        håndterInntektsmelding(listOf(10.januar til 25.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, TIL_INFOTRYGD)

        assertEquals(1, generasjoner.size)
        0.generasjon {
            assertEquals(1, perioder.size)
            uberegnetPeriode(0) medTilstand IngenUtbetaling
        }
    }

    @Test
    fun `avvist utbetaling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)

        assertEquals(0, generasjoner.size)
    }

    @Test
    fun `Utbetalt periode i AvventerRevurdering skal mappes til UtbetaltVenterPåAnnenPeriode`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
            beregnetPeriode(1) medTilstand ForberederGodkjenning
        }
    }

    @Test
    fun `out of order med gap`() {
        nyttVedtak(1.mars, 31.mars, orgnummer = a1)
        tilGodkjenning(1.januar, 31.januar, a1)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand UtbetaltVenterPåAnnenPeriode
            beregnetPeriode(1) er Utbetalingstatus.IkkeUtbetalt avType UTBETALING fra (1.januar til 31.januar) medTilstand TilGodkjenning
        }

        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        0.generasjon {
            assertEquals(2, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.IkkeUtbetalt avType REVURDERING fra (1.mars til 31.mars) medTilstand TilGodkjenning
            beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medTilstand Utbetalt
        }
        1.generasjon {
            assertEquals(1, perioder.size)
            beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
        }
    }

    @Test
    fun `tidligere periode med ferie får samme arbeidsgiverperiode som nyere periode`() = Toggle.AnnullereOgUtbetale.enable {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Søknad.Søknadsperiode.Arbeid(1.februar, 28.februar))

        nyttVedtak(1.mars, 31.mars)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        generasjoner(ORGNUMMER).also { generasjoner ->
            assertEquals(2, generasjoner.size)
            0.generasjon {
                assertEquals(3, perioder.size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.mars til 31.mars) medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medTilstand Utbetalt
                beregnetPeriode(2) er Utbetalingstatus.GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(2, perioder.size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) medTilstand IngenUtbetaling
            }
        }
    }

    private fun BeregnetPeriode.assertAldersvilkår(expectedOppfylt: Boolean, expectedAlderSisteSykedag: Int) {
        assertEquals(expectedOppfylt, periodevilkår.alder.oppfylt)
        assertEquals(expectedAlderSisteSykedag, periodevilkår.alder.alderSisteSykedag)
    }

    private fun BeregnetPeriode.assertSykepengedagerVilkår(
        expectedForbrukteSykedager: Int,
        expectedGjenståendeSykedager: Int,
        expectedMaksdato: LocalDate,
        expectedSkjæringstidspunkt: LocalDate,
        expectedOppfylt: Boolean
    ) {
        assertEquals(expectedForbrukteSykedager, periodevilkår.sykepengedager.forbrukteSykedager)
        assertEquals(expectedGjenståendeSykedager, periodevilkår.sykepengedager.gjenståendeDager)
        assertEquals(expectedMaksdato, periodevilkår.sykepengedager.maksdato)
        assertEquals(expectedSkjæringstidspunkt, periodevilkår.sykepengedager.skjæringstidspunkt)
        assertEquals(expectedOppfylt, periodevilkår.sykepengedager.oppfylt)
    }

    private fun BeregnetPeriode.assertSøknadsfristVilkår(
        expectedSøknadFom: LocalDate,
        expectedSøknadTom: LocalDate,
        expectedSendtNav: LocalDateTime,
        expectedOppfylt: Boolean
    ) {
        assertEquals(expectedSøknadFom, periodevilkår.søknadsfrist?.søknadFom)
        assertEquals(expectedSøknadTom, periodevilkår.søknadsfrist?.søknadTom)
        assertEquals(expectedSendtNav, periodevilkår.søknadsfrist?.sendtNav)
        assertEquals(expectedOppfylt, periodevilkår.søknadsfrist?.oppfylt)
    }

    private val generasjoner get() = generasjoner(ORGNUMMER)
    private lateinit var vilkårsgrunnlagHistorikk: Map<UUID, Map<LocalDate, Vilkårsgrunnlag>>

    // dette er vilkårsgrunnlag som pekes på av beregnede perioder
    private lateinit var vilkårsgrunnlag: Map<UUID, Vilkårsgrunnlag>

    private fun generasjoner(organisasjonsnummer: String): List<Generasjon> {
        val vilkårsgrunnlagHistorikkBuilderResult = VilkårsgrunnlagBuilder(person.inspektør.vilkårsgrunnlagHistorikk).build()
        vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikkBuilderResult.toDTO()

        val generasjonerBuilder = GenerasjonerBuilder(
            søknadDTOer,
            UNG_PERSON_FØDSELSDATO.alder,
            person.arbeidsgiver(organisasjonsnummer),
            vilkårsgrunnlagHistorikkBuilderResult
        )
        val generasjoner = generasjonerBuilder.build()
        vilkårsgrunnlag = vilkårsgrunnlagHistorikkBuilderResult.vilkårsgrunnlagSomBlirPektPå()
        return generasjoner
    }

    private fun Int.generasjon(organisasjonsnummer: String = ORGNUMMER, assertBlock: Generasjon.() -> Unit) {
        require(this >= 0) { "Kan ikke være et negativt tall!" }
        generasjoner(organisasjonsnummer)[this].run(assertBlock)
    }

    private infix fun <T : Tidslinjeperiode> T.medAntallDager(antall: Int): T {
        assertEquals(antall, sammenslåttTidslinje.size)
        return this
    }
    private infix fun <T : Tidslinjeperiode> T.harTidslinje(dager: Pair<Periode, SykdomstidslinjedagType>): T {
        val (periode, dagtype) = dager
        val periodeUtenHelg = periode.filterNot { it.erHelg() }
        val tidslinjedager = this.sammenslåttTidslinje.filter { it.dagen in periodeUtenHelg }
        assertEquals(periodeUtenHelg.toList().size, tidslinjedager.size)
        assertTrue(tidslinjedager.all { it.sykdomstidslinjedagtype == dagtype })
        return this
    }

    private fun BeregnetPeriode.vilkårsgrunnlag(): Vilkårsgrunnlag {
        return requireNotNull(vilkårsgrunnlag[this.vilkårsgrunnlagId]) { "Forventet å finne vilkårsgrunnlag for periode" }
    }

    private infix fun <T : Tidslinjeperiode> T.forkastet(forkastet: Boolean): T {
        assertEquals(forkastet, this.erForkastet)
        return this
    }

    private infix fun BeregnetPeriode.er(utbetalingstilstand: Utbetalingstatus): BeregnetPeriode {
        assertEquals(utbetalingstilstand, this.utbetaling.status)
        return this
    }

    private infix fun BeregnetPeriode.avType(type: Utbetalingtype): BeregnetPeriode {
        assertEquals(type, this.utbetaling.type)
        return this
    }

    private infix fun <T : Tidslinjeperiode> T.medTilstand(tilstand: Periodetilstand): T {
        assertEquals(tilstand, this.periodetilstand)
        return this
    }

    private infix fun <T : Tidslinjeperiode> T.fra(periode: Periode): T {
        assertEquals(periode.start, this.fom)
        assertEquals(periode.endInclusive, this.tom)
        return this
    }

    private infix fun BeregnetPeriode.medWarning(warning: String): BeregnetPeriode {
        assertTrue(this.aktivitetslogg.filter { it.alvorlighetsgrad == "W" }.any { it.melding == warning })
        return this
    }

    private infix fun BeregnetPeriode.utenWarning(warning: String): BeregnetPeriode {
        assertFalse(this.aktivitetslogg.filter { it.alvorlighetsgrad == "W" }.any { it.melding == warning })
        return this
    }

    private fun Generasjon.beregnetPeriode(index: Int): BeregnetPeriode {
        val periode = this.perioder[index]
        require(periode is BeregnetPeriode) { "Perioden er ikke en tidslinjeperiode!" }
        return periode
    }

    private fun Generasjon.uberegnetPeriode(index: Int): UberegnetPeriode {
        val periode = this.perioder[index]
        require(periode is UberegnetPeriode) { "Perioden er ikke en kort periode!" }
        return periode
    }
}
