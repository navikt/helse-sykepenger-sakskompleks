package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.etterspurteBehov
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Dødsinfo
import no.nav.helse.hendelser.Foreldrepermisjon
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.Utbetalingpåminnelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.sisteBehov
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PåminnelserOgTimeoutTest : AbstractPersonTest() {
    private lateinit var hendelse: ArbeidstakerHendelse

    @Test
    fun `påminnelse i vilkårsprøving`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        val antallBehovFør = hendelse.behov().size
        person.håndter(påminnelse(AVVENTER_VILKÅRSPRØVING, 1.vedtaksperiode))
        assertEquals(AVVENTER_VILKÅRSPRØVING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(antallBehovFør, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.InntekterForSammenligningsgrunnlag))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.InntekterForSykepengegrunnlag))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.ArbeidsforholdV2))
    }

    @Test
    fun `påminnelse i ytelser`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        assertEquals(8, hendelse.behov().size)
        person.håndter(påminnelse(AVVENTER_HISTORIKK, 1.vedtaksperiode))
        assertEquals(AVVENTER_HISTORIKK, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(8, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Foreldrepenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Pleiepenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Omsorgspenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Opplæringspenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Arbeidsavklaringspenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Dagpenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Institusjonsopphold))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Dødsinfo))
    }

    @Test
    fun `påminnelse i ytelser - gammel historikk`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        assertEquals(8, hendelse.behov().size)
        person.håndter(påminnelse(AVVENTER_HISTORIKK, 1.vedtaksperiode))
        assertEquals(AVVENTER_HISTORIKK, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(8, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Foreldrepenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Pleiepenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Omsorgspenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Opplæringspenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Arbeidsavklaringspenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Dagpenger))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Institusjonsopphold))
        assertTrue(hendelse.etterspurteBehov(1.vedtaksperiode.id(ORGNUMMER), Behovtype.Dødsinfo))
    }

    @Test
    fun `påminnelse i avventer simulering`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
        assertEquals(AVVENTER_SIMULERING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(1, hendelse.behov().size)
        assertTrue(hendelse.behov().any { it.type == Behovtype.Simulering })
        person.håndter(påminnelse(AVVENTER_SIMULERING, 1.vedtaksperiode))
        assertEquals(AVVENTER_SIMULERING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(1, hendelse.behov().size)
    }

    @Test
    fun `påminnelse i til godkjenning`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
        person.håndter(simulering())
        assertEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(1, hendelse.behov().size)
        assertTrue(hendelse.behov().any { it.type == Behovtype.Godkjenning })
        person.håndter(påminnelse(AVVENTER_GODKJENNING, 1.vedtaksperiode))

        assertEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(1, hendelse.behov().size)
    }

    @Test
    fun `påminnelse i til utbetaling`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
        person.håndter(simulering())
        person.håndter(utbetalingsgodkjenning())
        assertEquals(1, hendelse.behov().size)
        person.håndter(utbetalingpåminnelse(inspektør.utbetalingId(0)))
        assertEquals(TIL_UTBETALING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(1, hendelse.behov().size)
    }

    @Test
    fun `ignorerer påminnelser på tidligere tilstander`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(påminnelse(AVVENTER_VILKÅRSPRØVING, 1.vedtaksperiode))
        assertEquals(AVVENTER_HISTORIKK, inspektør.sisteTilstand(1.vedtaksperiode))
    }

    private fun søknad(
        vararg perioder: Søknadsperiode = arrayOf(
            Sykdom(
                1.januar,
                20.januar,
                100.prosent
            )
        )
    ) =
        a1Hendelsefabrikk.lagSøknad(
            perioder = perioder,
            sendtTilNAVEllerArbeidsgiver = 20.januar
        ).apply {
            hendelse = this
        }

    private fun utbetalingshistorikk() =
        a1Hendelsefabrikk.lagUtbetalingshistorikk(
            vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER)
        ).apply {
            hendelse = this
        }

    private fun sykmelding(
        vararg perioder: Sykmeldingsperiode = arrayOf(
            Sykmeldingsperiode(
                1.januar,
                20.januar
            )
        )
    ) = a1Hendelsefabrikk.lagSykmelding(
        sykeperioder = perioder
    ).apply {
            hendelse = this
        }

    private fun inntektsmelding(
        vararg arbeidsgiverperiode: Periode = arrayOf(Periode(1.januar, 1.januar.plusDays(15))),
        førsteFraværsdag: LocalDate = 1.januar
    ) = a1Hendelsefabrikk.lagInntektsmelding(
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = 31000.månedlig,
            arbeidsgiverperioder = arbeidsgiverperiode.toList(),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        ).apply {
            hendelse = this
        }

    private fun vilkårsgrunnlag() =
        Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
            skjæringstidspunkt = 1.januar,
            aktørId = "aktørId",
            personidentifikator = UNG_PERSON_FNR_2018,
            orgnummer = ORGNUMMER,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt 31000.månedlig
                }
            }),
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntektperioderForSykepengegrunnlag {
                    1.oktober(2017) til 1.desember(2017) inntekter {
                        ORGNUMMER inntekt 31000.månedlig
                    }
                }, arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(
                    ORGNUMMER,
                    1.januar(2017)
                )
            )
        ).apply {
            hendelse = this
        }

    private fun simulering() =
        Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018.toString(),
            orgnummer = ORGNUMMER,
            fagsystemId = person.personLogg.sisteBehov(Behovtype.Simulering).detaljer().getValue("fagsystemId") as String,
            fagområde = person.personLogg.sisteBehov(Behovtype.Simulering).detaljer().getValue("fagområde") as String,
            simuleringOK = true,
            melding = "",
            utbetalingId = UUID.fromString(person.personLogg.sisteBehov(Behovtype.Simulering).kontekst().getValue("utbetalingId")),
            simuleringResultat = SimuleringResultat(
                totalbeløp = 2000,
                perioder = listOf(
                    SimuleringResultat.SimulertPeriode(
                        periode = Periode(17.januar, 20.januar),
                        utbetalinger = listOf(
                            SimuleringResultat.SimulertUtbetaling(
                                forfallsdato = 21.januar,
                                utbetalesTil = SimuleringResultat.Mottaker(
                                    id = ORGNUMMER,
                                    navn = "Org Orgesen AS"
                                ),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimuleringResultat.Detaljer(
                                        periode = Periode(17.januar, 20.januar),
                                        konto = "81549300",
                                        beløp = 2000,
                                        klassekode = SimuleringResultat.Klassekode(
                                            kode = "SPREFAG-IOP",
                                            beskrivelse = "Sykepenger, Refusjon arbeidsgiver"
                                        ),
                                        uføregrad = 100,
                                        utbetalingstype = "YTELSE",
                                        tilbakeføring = false,
                                        sats = SimuleringResultat.Sats(
                                            sats = 1000.0,
                                            antall = 2,
                                            type = "DAGLIG"
                                        ),
                                        refunderesOrgnummer = ORGNUMMER
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).apply {
            hendelse = this
        }

    private fun ytelser(): Ytelser {
        val meldingsreferanseId = UUID.randomUUID()
        return Ytelser(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018.toString(),
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
            foreldrepermisjon = Foreldrepermisjon(
                foreldrepengeytelse = null,
                svangerskapsytelse = null
            ),
            pleiepenger = Pleiepenger(
                perioder = emptyList()
            ),
            omsorgspenger = Omsorgspenger(
                perioder = emptyList()
            ),
            opplæringspenger = Opplæringspenger(
                perioder = emptyList()
            ),
            institusjonsopphold = Institusjonsopphold(
                perioder = emptyList()
            ),
            dødsinfo = Dødsinfo(null),
            arbeidsavklaringspenger = Arbeidsavklaringspenger(emptyList()),
            dagpenger = Dagpenger(emptyList()),
            aktivitetslogg = Aktivitetslogg()
        ).apply {
            hendelse = this
        }
    }

    private fun utbetalingsgodkjenning() = Utbetalingsgodkjenning(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        organisasjonsnummer = ORGNUMMER,
        utbetalingId = UUID.fromString(person.personLogg.sisteBehov(Behovtype.Godkjenning).kontekst()["utbetalingId"] ?: throw IllegalStateException("Finner ikke utbetalingId i: ${person.personLogg.sisteBehov(
            Behovtype.Godkjenning
        ).kontekst()}")),
        vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
        saksbehandler = "Ola Nordmann",
        saksbehandlerEpost = "ola@normann.ss",
        utbetalingGodkjent = true,
        godkjenttidspunkt = LocalDateTime.now(),
        automatiskBehandling = false,
    ).apply {
        hendelse = this
    }

    private fun påminnelse(tilstandType: TilstandType, vedtaksperiodeIdInnhenter: IdInnhenter) = Påminnelse(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        organisasjonsnummer = ORGNUMMER,
        vedtaksperiodeId = "${vedtaksperiodeIdInnhenter.id(ORGNUMMER)}",
        tilstand = tilstandType,
        antallGangerPåminnet = 1,
        tilstandsendringstidspunkt = LocalDateTime.now(),
        påminnelsestidspunkt = LocalDateTime.now(),
        nestePåminnelsestidspunkt = LocalDateTime.now()
    ).apply {
        hendelse = this
    }

    private fun utbetalingpåminnelse(utbetalingId: UUID) = Utbetalingpåminnelse(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        organisasjonsnummer = ORGNUMMER,
        utbetalingId = utbetalingId,
        status = Utbetalingstatus.AVVENTER_ARBEIDSGIVERKVITTERING,
        antallGangerPåminnet = 1,
        endringstidspunkt = LocalDateTime.now(),
        påminnelsestidspunkt = LocalDateTime.now()
    ).apply {
        hendelse = this
    }
}
