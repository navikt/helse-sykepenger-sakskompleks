package no.nav.helse.spleis.e2e.behandlinger

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.assertForventetFeil
import no.nav.helse.august
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.forlengVedtak
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.dsl.nyPeriode
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.dsl.tilGodkjenning
import no.nav.helse.februar
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeidsgiverdag
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingkilde
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.ANNULLERT_PERIODE
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.AVSLUTTET_UTEN_VEDTAK
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.REVURDERT_VEDTAK_AVVIST
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.UBEREGNET_OMGJØRING
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.UBEREGNET_REVURDERING
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.VEDTAK_FATTET
import no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.VEDTAK_IVERKSATT
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BehandlingerE2ETest : AbstractDslTest() {

    @Test
    fun `ny periode har en behandling`() {
        a1 {
            val søknadId = UUID.randomUUID()
            val registrert = LocalDateTime.now()
            val innsendt = registrert.minusHours(2)
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), søknadId = søknadId, sendtTilNAVEllerArbeidsgiver = innsendt, registrert = registrert)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                assertEquals(1, behandlinger.single().endringer.size)
                assertEquals(Behandlingkilde(meldingsreferanseId = søknadId, innsendt = innsendt, registert = registrert, avsender = Avsender.SYKMELDT), behandlinger.single().kilde)
            }
        }
    }

    @Test
    fun `korrigerende inntektsmelding`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            val mottatt = LocalDateTime.now()
            val inntektsmeldingId = håndterInntektsmelding(
                listOf(1.januar til 16.januar),
                beregnetInntekt = INNTEKT * 1.1,
                mottatt = mottatt
            )
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Behandlingkilde(meldingsreferanseId = inntektsmeldingId, innsendt = mottatt, registert = mottatt, avsender = Avsender.ARBEIDSGIVER), behandlinger.last().kilde)
            }
        }
    }

    @Test
    fun `Flere sykefraværstilfeller på flere arbeidsgivere med korrigerende inntektsmelding i snuten`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            nyttVedtak(1.mars, 31.mars)
            assertEquals(1, inspektør(1.vedtaksperiode).behandlinger.size)
            assertEquals(1, inspektør(2.vedtaksperiode).behandlinger.size)
        }
        a2 {
            nyttVedtak(1.mai, 31.mai)
            håndterSøknad(Sykdom(1.juli, 31.juli, 100.prosent))
            assertEquals(1, inspektør(1.vedtaksperiode).behandlinger.size)
            assertEquals(1, inspektør(2.vedtaksperiode).behandlinger.size)
        }

        val korrigerendeImA1 = UUID.randomUUID()
        val mottatt = LocalDateTime.now()
        val forventetKilde = Behandlingkilde(meldingsreferanseId = korrigerendeImA1, innsendt = mottatt, registert = mottatt, avsender = Avsender.ARBEIDSGIVER)

        a1 {
            håndterInntektsmelding(
                listOf(1.januar til 16.januar),
                beregnetInntekt = INNTEKT * 1.1,
                id = korrigerendeImA1,
                mottatt = mottatt
            )
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(forventetKilde, behandlinger.last().kilde)
            }
            inspektør(2.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(forventetKilde, behandlinger.last().kilde)
            }
        }

        a2 {
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(forventetKilde, behandlinger.last().kilde)
            }
            inspektør(2.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
            }
        }
    }

    @Test
    fun `Saksbehandler må behandle søknad i Infotrygd`() {
        a1 {
            tilGodkjenning(1.januar, 31.januar)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
            inspektørForkastet(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
            }
        }
    }
    @Test
    fun `annullere iverksatt vedtak`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            håndterAnnullering(inspektør.utbetalinger.single().inspektør.utbetalingId)
            inspektørForkastet(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
                assertEquals(Avsender.SAKSBEHANDLER, behandlinger.last().kilde.avsender)
                behandlinger.last().also { sisteBehandling ->
                    assertNotNull(sisteBehandling.avsluttet)
                    assertEquals(ANNULLERT_PERIODE, sisteBehandling.tilstand)
                    assertEquals(1, sisteBehandling.endringer.size)
                    sisteBehandling.endringer.single().also { endring ->
                        assertNotNull(endring.utbetaling)
                        assertNotNull(endring.grunnlagsdata)
                        endring.utbetaling.inspektør.also { annulleringen ->
                            assertEquals(Utbetalingtype.ANNULLERING, annulleringen.type)
                        }
                    }
                }
            }
        }
    }
    @Test
    fun `annullere flere iverksatte vedtak`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            forlengVedtak(1.februar, 28.februar)
            nyttVedtak(10.mars, 31.mars, arbeidsgiverperiode = listOf(10.mars til 25.mars))
            håndterAnnullering(inspektør.utbetalinger.last().inspektør.utbetalingId)
            assertEquals(4, inspektør.utbetalinger.size)
            inspektørForkastet(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
                assertEquals(Avsender.SAKSBEHANDLER, behandlinger.last().kilde.avsender)
                behandlinger.last().also { sisteBehandling ->
                    assertNotNull(sisteBehandling.avsluttet)
                    assertEquals(ANNULLERT_PERIODE, sisteBehandling.tilstand)
                    assertEquals(1, sisteBehandling.endringer.size)
                    sisteBehandling.endringer.single().also { endring ->
                        assertNotNull(endring.utbetaling)
                        assertNotNull(endring.grunnlagsdata)
                        endring.utbetaling.inspektør.also { annulleringen ->
                            assertEquals(Utbetalingtype.ANNULLERING, annulleringen.type)
                        }
                    }
                }
            }
            inspektørForkastet(2.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
                assertEquals(Avsender.SAKSBEHANDLER, behandlinger.last().kilde.avsender)
                behandlinger.last().also { sisteBehandling ->
                    assertNotNull(sisteBehandling.avsluttet)
                    assertEquals(ANNULLERT_PERIODE, sisteBehandling.tilstand)
                    assertEquals(1, sisteBehandling.endringer.size)
                    sisteBehandling.endringer.single().also { endring ->
                        assertNotNull(endring.utbetaling)
                        assertNotNull(endring.grunnlagsdata)
                        endring.utbetaling.inspektør.also { annulleringen ->
                            assertEquals(Utbetalingtype.ANNULLERING, annulleringen.type)
                        }
                    }
                }
            }
            inspektørForkastet(3.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
                assertEquals(Avsender.SAKSBEHANDLER, behandlinger.last().kilde.avsender)
                behandlinger.last().also { sisteBehandling ->
                    assertNotNull(sisteBehandling.avsluttet)
                    assertEquals(ANNULLERT_PERIODE, sisteBehandling.tilstand)
                    assertEquals(1, sisteBehandling.endringer.size)
                    sisteBehandling.endringer.single().also { endring ->
                        assertNotNull(endring.utbetaling)
                        assertNotNull(endring.grunnlagsdata)
                        endring.utbetaling.inspektør.also { annulleringen ->
                            assertEquals(Utbetalingtype.ANNULLERING, annulleringen.type)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `annullere en uberegnet revurdering`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
            håndterAnnullering(inspektør.utbetalinger.single().inspektør.utbetalingId)
            inspektørForkastet(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(ANNULLERT_PERIODE, behandlinger.last().tilstand)
            }
        }
    }

    @Test
    fun `annullere en beregnet revurdering`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterAnnullering(inspektør.utbetalinger.first().inspektør.utbetalingId)
            assertEquals(Utbetalingstatus.FORKASTET, inspektør.utbetaling(1).inspektør.tilstand)
            inspektørForkastet(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(ANNULLERT_PERIODE, behandlinger.last().tilstand)
            }
        }
    }

    @Test
    fun `avvise en beregnet revurdering`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjent = false)
            håndterPåminnelse(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, reberegning = true)
            assertEquals(Utbetalingstatus.IKKE_GODKJENT, inspektør.utbetaling(1).inspektør.tilstand)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(3, behandlinger.size)
                assertEquals(VEDTAK_IVERKSATT, behandlinger[0].tilstand)
                assertEquals(REVURDERT_VEDTAK_AVVIST, behandlinger[1].tilstand)
                assertEquals(UBEREGNET_REVURDERING, behandlinger[2].tilstand)
            }
        }
    }

    @Test
    fun `Reberegner en periode`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            håndterPåminnelse(1.vedtaksperiode, AVSLUTTET, reberegning = true)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
                assertEquals(Avsender.SYSTEM, behandlinger.last().kilde.avsender)
            }
        }
    }

    @Test
    fun `En utbetaling i Infotrygd blander seg`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
            val id = UUID.randomUUID()
            håndterUtbetalingshistorikkEtterInfotrygdendring(utbetalinger = listOf(ArbeidsgiverUtbetalingsperiode(a1, 1.januar, 5.januar, 100.prosent, INNTEKT)), id = id)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(Avsender.SYKMELDT, behandlinger.first().kilde.avsender)
                behandlinger.last().let {
                    assertEquals(Avsender.SYSTEM, it.kilde.avsender)
                    assertEquals(id, it.kilde.meldingsreferanseId)
                }
            }
        }
    }

    @Test
    fun `ny periode som starter med egenmeldinger påvirker ikke sykmeldingsperiode`() {
        a1 {
            håndterSøknad(Sykdom(3.januar, 17.januar, 100.prosent), egenmeldinger = listOf(Arbeidsgiverdag(1.januar, 2.januar)))
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                assertEquals(1, behandlinger.single().endringer.size)
                assertEquals(1.januar til 17.januar, behandlinger.single().periode)
            }
        }
    }

    @Test
    fun `korrigert søknad lager ny endring`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))
            val søknad2 = UUID.randomUUID()
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), Ferie(19.januar, 20.januar), søknadId = søknad2)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                assertEquals(2, behandlinger.single().endringer.size)
                assertEquals(Dokumentsporing.søknad(søknad2), behandlinger.single().endringer.last().dokumentsporing)
            }
        }
    }

    @Test
    fun `korrigert tidslinje mens perioden er til godkjenning`() {
        a1 {
            tilGodkjenning(2.januar, 31.januar)
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(1.januar, Dagtype.Sykedag, 100)))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)

            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                behandlinger[0].also { behandling ->
                    assertEquals(6, behandling.endringer.size)
                    assertEquals(no.nav.helse.inspectors.VedtaksperiodeInspektør.Behandling.Behandlingtilstand.BEREGNET, behandling.tilstand)
                }
            }
        }
    }

    @Test
    fun `korrigert søknad etter fattet vedtak lager ny behandling`() {
        a1 {
            val søknad1 = UUID.randomUUID()
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), søknadId = søknad1)
            val im = håndterInntektsmelding(listOf(1.januar til 16.januar), INNTEKT)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            val vedtakFattetTidspunkt = LocalDateTime.now()
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, godkjenttidspunkt = vedtakFattetTidspunkt)
            val søknad2 = UUID.randomUUID()
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), Ferie(19.januar, 20.januar), søknadId = søknad2)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                behandlinger[0].also { behandling ->
                    assertEquals(4, behandling.endringer.size)
                    assertEquals(Dokumentsporing.søknad(søknad1), behandling.endringer[0].dokumentsporing)
                    assertEquals(Dokumentsporing.inntektsmeldingDager(im), behandling.endringer[1].dokumentsporing)
                    assertEquals(Dokumentsporing.inntektsmeldingInntekt(im), behandling.endringer[2].dokumentsporing)
                    assertEquals(Dokumentsporing.inntektsmeldingInntekt(im), behandling.endringer[3].dokumentsporing)
                    assertEquals(VEDTAK_FATTET, behandling.tilstand)
                    assertEquals(vedtakFattetTidspunkt, behandling.vedtakFattet)
                    assertNull(behandling.avsluttet)
                }
                behandlinger[1].also { behandling ->
                    assertEquals(1, behandling.endringer.size)
                    assertEquals(Dokumentsporing.søknad(søknad2), behandling.endringer.last().dokumentsporing)
                    assertEquals(UBEREGNET_REVURDERING, behandling.tilstand)
                }
            }
        }
    }

    @Test
    fun `korrigert søknad på tidligere periode, med senere periode til utbetaling, lager ny behandling`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            tilGodkjenning(1.mars, 31.mars)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterSøknad(Sykdom(1.januar, 31.januar, 90.prosent))

            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                behandlinger[0].also { behandling ->
                    assertEquals(4, behandling.endringer.size)
                }
                behandlinger[1].also { behandling ->
                    assertEquals(1, behandling.endringer.size)
                    assertEquals(UBEREGNET_REVURDERING, behandling.tilstand)
                }
            }
            inspektør(2.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                behandlinger[0].also { behandling ->
                    assertEquals(4, behandling.endringer.size)
                    assertEquals(VEDTAK_FATTET, behandling.tilstand)
                }
                behandlinger[1].also { behandling ->
                    assertEquals(1, behandling.endringer.size)
                    assertEquals(UBEREGNET_REVURDERING, behandling.tilstand)
                }
            }
        }
    }

    @Test
    fun `korrigert søknad i avsluttet uten utbetaling som ikke medfører omgjøring`() {
        a1 {
            håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent))
            håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent), Ferie(18.januar, 18.januar))
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[0].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[1].tilstand)
            }
        }
    }

    @Test
    fun `overstyr tidslinje i avsluttet uten utbetaling som ikke medfører omgjøring`() {
        a1 {
            håndterSøknad(Sykdom(3.januar, 18.januar, 100.prosent))
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.januar, Dagtype.Feriedag)))
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[0].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[1].tilstand)
            }
        }
    }

    @Test
    fun `inntektsmelding med første fraværsdag utenfor sykdom - to tidligere vedtak - inntektsmelding ikke håndtert fordi inntekt håndteres ikke`() {
        a1 {
            nyttVedtak(1.januar, 31.januar)
            forlengVedtak(1.februar, 28.februar)
            val inntektsmeldingId = håndterInntektsmelding(listOf(1.februar til 16.februar), førsteFraværsdag = 1.mars)
            assertTrue(inntektsmeldingId in observatør.inntektsmeldingIkkeHåndtert)
            assertFalse(inntektsmeldingId in observatør.inntektsmeldingHåndtert.map { it.first })
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
            }
            inspektør(2.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                behandlinger.last().also { sisteBehandling ->
                    assertEquals(inntektsmeldingId, sisteBehandling.kilde.meldingsreferanseId)
                    assertEquals(Dokumentsporing.inntektsmeldingDager(inntektsmeldingId), sisteBehandling.endringer.single().dokumentsporing)
                }
            }
        }
    }

    @Test
    fun `delvis overlappende søknad i uberegnet`() {
        a1 {
            val søknad1 = UUID.randomUUID()
            val søknad2 = UUID.randomUUID()
            håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent), søknadId = søknad1)
            håndterSøknad(Sykdom(3.januar, 27.januar, 100.prosent), søknadId = søknad2)
            inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.behandlinger.single().also { behandling ->
                assertEquals(søknad1, behandling.kilde.meldingsreferanseId)
                assertEquals(Dokumentsporing.søknad(søknad1), behandling.endringer.single().dokumentsporing)
            }
            inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.behandlinger.single().also { behandling ->
                assertEquals(søknad2, behandling.kilde.meldingsreferanseId)
                assertEquals(Dokumentsporing.søknad(søknad2), behandling.endringer.single().dokumentsporing)
            }
        }
    }

    @Test
    fun `delvis overlappende søknad i avsluttet uten utbetaling`() {
        a1 {
            håndterSøknad(Sykdom(8.august, 21.august, 100.prosent))
            håndterSykmelding(Sykmeldingsperiode(10.august, 31.august))
            val overlappende = UUID.randomUUID()
            håndterSøknad(Sykdom(10.august, 31.august, 100.prosent), søknadId = overlappende)
            assertEquals(8.august til 21.august, inspektør.periode(1.vedtaksperiode))
            assertEquals(10.august til 31.august, inspektør.periode(2.vedtaksperiode))
            assertTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            inspektør.vedtaksperioder(2.vedtaksperiode).inspektør.behandlinger.last().also { behandling ->
                assertEquals(overlappende, behandling.kilde.meldingsreferanseId)
                assertEquals(Dokumentsporing.søknad(overlappende), behandling.endringer.single().dokumentsporing)
            }
            assertFunksjonellFeil(Varselkode.`Mottatt søknad som delvis overlapper`, 1.vedtaksperiode.filter())
            assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TilstandType.TIL_INFOTRYGD)
        }
    }

    @Test
    fun `periode hos ag2 blir innenfor agp mens ag1 har laget utbetaling`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(2.januar, 17.januar, 100.prosent))
        }
        a2 {
            håndterInntektsmelding(emptyList(), førsteFraværsdag = 2.januar, begrunnelseForReduksjonEllerIkkeUtbetalt = "IkkeOpptjening")
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
                arbeidsforhold = listOf(Arbeidsforhold(a1, LocalDate.EPOCH, type = ORDINÆRT), Arbeidsforhold(a2, LocalDate.EPOCH, type = ORDINÆRT))
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
        }
        a2 {
            håndterInntektsmelding(listOf(2.januar til 17.januar))
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a1 {
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(1, behandlinger.size)
                assertEquals(VEDTAK_IVERKSATT, behandlinger.single().tilstand)
            }
        }
        a2 {
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[0].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[1].tilstand)
            }
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        }
    }

    @Test
    fun `korrigert søknad på kort periode`() {
        a1 {
            nyPeriode(1.januar til 5.januar)
            nyPeriode(6.januar til 8.januar)
            nyPeriode(9.januar til 15.januar)
            nyttVedtak(16.januar, 25.januar, arbeidsgiverperiode = listOf(1.januar til 16.januar))
            håndterSøknad(Sykdom(6.januar, 8.januar, 100.prosent, 10.prosent))

            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[0].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[1].tilstand)
            }
            inspektør(2.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(3, behandlinger.size)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[0].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[1].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[2].tilstand)
            }
            inspektør(3.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(3, behandlinger.size)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[0].tilstand)
                assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[1].tilstand)
                assertForventetFeil(
                    forklaring = "behandligen skal være lukket",
                    nå = {
                        assertEquals(UBEREGNET_OMGJØRING, behandlinger[2].tilstand)
                    },
                    ønsket = {
                        assertEquals(AVSLUTTET_UTEN_VEDTAK, behandlinger[2].tilstand)
                    }
                )
            }
            inspektør(4.vedtaksperiode).behandlinger.also { behandlinger ->
                assertEquals(2, behandlinger.size)
                assertEquals(VEDTAK_IVERKSATT, behandlinger[0].tilstand)
                assertEquals(UBEREGNET_REVURDERING, behandlinger[1].tilstand)
            }
        }
    }
}