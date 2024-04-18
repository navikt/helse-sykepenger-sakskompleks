package no.nav.helse.person

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.februar
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Foreldrepenger
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingReplayUtført
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Svangerskapspenger
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class SimuleringHendelseTest : AbstractPersonTest() {
    private companion object {
        private val førsteSykedag = 1.januar
        private val sisteSykedag = 28.februar
    }

    private lateinit var hendelse: ArbeidstakerHendelse

    @Test
    fun `simulering er OK`() {
        håndterYtelser()
        håndterSimuleringer()
        assertEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertFalse(person.personLogg.harVarslerEllerVerre())
    }

    @Test
    fun `simulering er ikke OK`() {
        håndterYtelser()
        håndterSimuleringer(mapOf(Fagområde.SykepengerRefusjon to Pair(false, 1431)))
        assertFalse(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertEquals(AVVENTER_SIMULERING, inspektør.sisteTilstand(1.vedtaksperiode))
    }

    @Test
    fun `simulering ved delvis refusjon`() {
        håndterYtelser(Inntektsmelding.Refusjon(31000.månedlig, sisteSykedag.minusDays(7), emptyList()))
        håndterSimuleringer(mapOf(
            Fagområde.SykepengerRefusjon to Pair(true, 1431),
            Fagområde.Sykepenger to Pair(true, 1431)
        ))
        assertEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertFalse(person.personLogg.harVarslerEllerVerre())
    }

    @Test
    fun `simulering ved delvis refusjon hvor vi avventer en simulering`() {
        håndterYtelser(Inntektsmelding.Refusjon(31000.månedlig, sisteSykedag.minusDays(7), emptyList()))
        håndterSimuleringer(mapOf(
            Fagområde.SykepengerRefusjon to Pair(true, 1431)
        ))
        assertEquals(AVVENTER_SIMULERING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertFalse(person.personLogg.harVarslerEllerVerre())
    }

    @Test
    fun `simulering ved ingen refusjon`() {
        håndterYtelser(Inntektsmelding.Refusjon(INGEN, null, emptyList()))
        håndterSimuleringer(mapOf(
            Fagområde.Sykepenger to Pair(true, 1431)
        ))
        assertEquals(AVVENTER_GODKJENNING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertFalse(person.personLogg.harVarslerEllerVerre())
    }

    private fun håndterYtelser(
        refusjon: Inntektsmelding.Refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList())
    ) {
        person.håndter(
            UtbetalingshistorikkEtterInfotrygdendring(UUID.randomUUID(), "", "", InfotrygdhistorikkElement.opprett(
                oppdatert = LocalDateTime.now(),
                hendelseId = UUID.randomUUID(),
                perioder = emptyList(),
                inntekter = emptyList(),
                arbeidskategorikoder = emptyMap()
            ), besvart = LocalDateTime.now()))
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(InntektsmeldingReplayUtført(UUID.randomUUID(), UNG_PERSON_FNR_2018.toString(), AKTØRID, ORGNUMMER, 1.vedtaksperiode.id(ORGNUMMER)))
        person.håndter(inntektsmelding(refusjon))
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
    }

    private fun ytelser(
        foreldrepengeYtelse: List<GradertPeriode> = emptyList(),
        svangerskapYtelse: List<GradertPeriode> = emptyList()
    ) = Aktivitetslogg().let {
        val meldingsreferanseId = UUID.randomUUID()
        Ytelser(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018.toString(),
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
            foreldrepenger = Foreldrepenger(
                foreldrepengeytelse = foreldrepengeYtelse
            ),
            svangerskapspenger = Svangerskapspenger(
                svangerskapsytelse = svangerskapYtelse
            ),
            pleiepenger = Pleiepenger(
                perioder = emptyList(),
            ),
            omsorgspenger = Omsorgspenger(
                perioder = emptyList(),
            ),
            opplæringspenger = Opplæringspenger(
                perioder = emptyList(),
            ),
            institusjonsopphold = Institusjonsopphold(
                perioder = emptyList(),
            ),
            arbeidsavklaringspenger = Arbeidsavklaringspenger(emptyList()),
            dagpenger = Dagpenger(emptyList()),
            aktivitetslogg = it
        ).apply {
            hendelse = this
        }
    }

    private fun sykmelding() = a1Hendelsefabrikk.lagSykmelding(
        sykeperioder = arrayOf(Sykmeldingsperiode(førsteSykedag, sisteSykedag))
    ).apply {
        hendelse = this
    }

    private fun søknad() =
        a1Hendelsefabrikk.lagSøknad(
            perioder = arrayOf(Sykdom(førsteSykedag, sisteSykedag, 100.prosent)),
            sendtTilNAVEllerArbeidsgiver = sisteSykedag
        ).apply {
            hendelse = this
        }

    private fun inntektsmelding(
        refusjon: Inntektsmelding.Refusjon
    ) = a1Hendelsefabrikk.lagInntektsmelding(
            refusjon = refusjon,
            førsteFraværsdag = førsteSykedag,
            beregnetInntekt = 31000.månedlig,
            arbeidsgiverperioder = listOf(Periode(førsteSykedag, førsteSykedag.plusDays(15))),
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
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntektperioderForSykepengegrunnlag {
                    1.oktober(2017) til 1.desember(2017) inntekter {
                        ORGNUMMER inntekt 31000.månedlig
                    }
                }, arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT)
            )
        ).apply {
            hendelse = this
        }

    private fun håndterSimuleringer(simuleringsdetaljer: Map<Fagområde, Pair<Boolean, Int>> = mapOf(Fagområde.SykepengerRefusjon to Pair(true, 1431))) {
        hendelse.behov().filter { it.type == Aktivitet.Behov.Behovtype.Simulering }.forEach { simuleringsBehov ->
            val fagsystemId = simuleringsBehov.detaljer().getValue("fagsystemId") as String
            val fagområde = Fagområde.from(simuleringsBehov.detaljer().getValue("fagområde") as String)
            val utbetalingId = UUID.fromString(simuleringsBehov.kontekst().getValue("utbetalingId"))
            if (!simuleringsdetaljer.containsKey(fagområde)) return@forEach
            val (simuleringOk, dagsats) = simuleringsdetaljer.getValue(fagområde)
            person.håndter(simulering(simuleringOk, dagsats, fagområde, fagsystemId, utbetalingId))
        }
    }

    private fun simulering(
        simuleringOK: Boolean,
        dagsats: Int,
        fagområde: Fagområde,
        fagsystemId: String,
        utbetalingId: UUID) =
        Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018.toString(),
            orgnummer = ORGNUMMER,
            fagsystemId = fagsystemId,
            fagområde = fagområde.verdi,
            simuleringOK = simuleringOK,
            melding = "",
            utbetalingId = utbetalingId,
            simuleringResultat = if (!simuleringOK) null else SimuleringResultatDto(
                totalbeløp = 44361,
                perioder = listOf(
                    SimuleringResultatDto.SimulertPeriode(
                        fom = 17.januar,
                        tom = 31.januar,
                        utbetalinger = listOf(
                            SimuleringResultatDto.SimulertUtbetaling(
                                forfallsdato = 1.februar,
                                utbetalesTil = SimuleringResultatDto.Mottaker(
                                    UNG_PERSON_FNR_2018.toString(),
                                    "Ung Person"
                                ),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimuleringResultatDto.Detaljer(
                                        fom = 17.januar,
                                        tom = 31.januar,
                                        konto = "11111111111",
                                        beløp = dagsats * 11,
                                        klassekode = SimuleringResultatDto.Klassekode(
                                            "SPREFAG-IOP",
                                            "Sykepenger, Refusjon arbeidsgiver"
                                        ),
                                        uføregrad = 100,
                                        utbetalingstype = "YTELSE",
                                        tilbakeføring = false,
                                        sats = SimuleringResultatDto.Sats(dagsats.toDouble(), 11, "DAGLIG"),
                                        refunderesOrgnummer = ORGNUMMER
                                    )
                                )
                            )
                        )
                    ),
                    SimuleringResultatDto.SimulertPeriode(
                        fom = 1.februar,
                        tom = 28.februar,
                        utbetalinger = listOf(
                            SimuleringResultatDto.SimulertUtbetaling(
                                forfallsdato = 1.mars,
                                utbetalesTil = SimuleringResultatDto.Mottaker(
                                    UNG_PERSON_FNR_2018.toString(),
                                    "Ung Person"
                                ),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimuleringResultatDto.Detaljer(
                                        fom = 1.februar,
                                        tom = 28.februar,
                                        konto = "11111111111",
                                        beløp = dagsats * 20,
                                        klassekode = SimuleringResultatDto.Klassekode(
                                            "SPREFAG-IOP",
                                            "Sykepenger, Refusjon arbeidsgiver"
                                        ),
                                        uføregrad = 100,
                                        utbetalingstype = "YTELSE",
                                        tilbakeføring = false,
                                        sats = SimuleringResultatDto.Sats(dagsats.toDouble(), 20, "DAGLIG"),
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
}
