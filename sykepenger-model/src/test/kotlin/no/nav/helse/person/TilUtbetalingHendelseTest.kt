package no.nav.helse.person

import no.nav.helse.hendelser.*
import no.nav.helse.testhelpers.desember
import no.nav.helse.testhelpers.inntektperioder
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class TilUtbetalingHendelseTest : AbstractPersonTest() {
    private companion object {
        private const val SAKSBEHANDLER_IDENT = "O123456"
        private val INNTEKT = 31000.månedlig
        private lateinit var førsteSykedag: LocalDate
        private lateinit var sisteSykedag: LocalDate
        private lateinit var sykmeldingHendelseId: UUID
        private lateinit var søknadHendelseId: UUID
        private lateinit var inntektsmeldingHendelseId: UUID
    }

    private lateinit var hendelse: ArbeidstakerHendelse

    @BeforeEach
    internal fun setupTests() {
        førsteSykedag = 1.januar
        sisteSykedag = 31.januar
        sykmeldingHendelseId = UUID.randomUUID()
        søknadHendelseId = UUID.randomUUID()
        inntektsmeldingHendelseId = UUID.randomUUID()
    }

    @Test
    fun `utbetaling er godkjent av saksbehandler`() {
        håndterGodkjenning(SAKSBEHANDLER_IDENT, false)
        person.håndter(utbetaling(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT))
        assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))

        assertEquals(2, observatør.utbetaltEventer.first().oppdrag.size)

        PersonObserver.UtbetaltEvent(
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            organisasjonsnummer = ORGNUMMER,
            hendelser = setOf(sykmeldingHendelseId, søknadHendelseId, inntektsmeldingHendelseId),
            oppdrag = listOf(
                PersonObserver.UtbetaltEvent.Utbetalt(
                    mottaker = ORGNUMMER,
                    fagområde = "SPREF",
                    fagsystemId = observatør.utbetaltEventer.first().oppdrag[0].fagsystemId,
                    totalbeløp = 11 * 1431,
                    utbetalingslinjer = listOf(
                        PersonObserver.UtbetaltEvent.Utbetalt.Utbetalingslinje(
                            fom = 17.januar,
                            tom = 31.januar,
                            dagsats = 1431,
                            beløp = 1431,
                            grad = 100.0,
                            sykedager = 11
                        )
                    )
                ),
                PersonObserver.UtbetaltEvent.Utbetalt(
                    mottaker = UNG_PERSON_FNR_2018,
                    fagområde = "SP",
                    fagsystemId = observatør.utbetaltEventer.first().oppdrag[1].fagsystemId,
                    totalbeløp = 0,
                    utbetalingslinjer = emptyList()
                )
            ),
            ikkeUtbetalteDager = emptyList(),
            fom = førsteSykedag,
            tom = sisteSykedag,
            forbrukteSykedager = 11,
            gjenståendeSykedager = 237,
            godkjentAv = SAKSBEHANDLER_IDENT,
            automatiskBehandling = false,
            opprettet = observatør.utbetaltEventer.first().opprettet,
            sykepengegrunnlag = INNTEKT.reflection { årlig, _, _, _ -> årlig },
            månedsinntekt = INNTEKT.reflection { _, månedlig, _, _ -> månedlig },
            maksdato = 28.desember
        ).also {
            assertEquals(it, observatør.utbetaltEventer.first())
        }
    }

    @Test
    fun `utbetaling er godkjent automatisk`() {
        håndterGodkjenning("SYSTEM", true)
        person.håndter(utbetaling(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT))
        assertEquals(TilstandType.AVSLUTTET, inspektør.sisteTilstand(1.vedtaksperiode))

        assertEquals(2, observatør.utbetaltEventer.first().oppdrag.size)

        PersonObserver.UtbetaltEvent(
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            organisasjonsnummer = ORGNUMMER,
            hendelser = setOf(sykmeldingHendelseId, søknadHendelseId, inntektsmeldingHendelseId),
            oppdrag = listOf(
                PersonObserver.UtbetaltEvent.Utbetalt(
                    mottaker = ORGNUMMER,
                    fagområde = "SPREF",
                    fagsystemId = observatør.utbetaltEventer.first().oppdrag[0].fagsystemId,
                    totalbeløp = 11 * 1431,
                    utbetalingslinjer = listOf(
                        PersonObserver.UtbetaltEvent.Utbetalt.Utbetalingslinje(
                            fom = 17.januar,
                            tom = 31.januar,
                            dagsats = 1431,
                            beløp = 1431,
                            grad = 100.0,
                            sykedager = 11
                        )
                    )
                ),
                PersonObserver.UtbetaltEvent.Utbetalt(
                    mottaker = UNG_PERSON_FNR_2018,
                    fagområde = "SP",
                    fagsystemId = observatør.utbetaltEventer.first().oppdrag[1].fagsystemId,
                    totalbeløp = 0,
                    utbetalingslinjer = emptyList()
                )
            ),
            ikkeUtbetalteDager = emptyList(),
            fom = førsteSykedag,
            tom = sisteSykedag,
            forbrukteSykedager = 11,
            gjenståendeSykedager = 237,
            godkjentAv = "SYSTEM",
            automatiskBehandling = true,
            opprettet = observatør.utbetaltEventer.first().opprettet,
            sykepengegrunnlag = INNTEKT.reflection { årlig, _, _, _ -> årlig },
            månedsinntekt = INNTEKT.reflection { _, månedlig, _, _ -> månedlig },
            maksdato = 28.desember
        ).also {
            assertEquals(it, observatør.utbetaltEventer.first())
        }
    }

    @Test
    fun `utbetaling ikke godkjent`() {
        håndterGodkjenning()
        person.håndter(utbetaling(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AVVIST))
        assertEquals(TilstandType.UTBETALING_FEILET, inspektør.sisteTilstand(1.vedtaksperiode))
        assertTrue(observatør.utbetaltEventer.isEmpty())
    }

    private fun håndterGodkjenning(
        godkjentAv: String = SAKSBEHANDLER_IDENT,
        automatiskBehandling: Boolean = false
    ) {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag(1.vedtaksperiode))
        person.håndter(ytelser(1.vedtaksperiode))
        person.håndter(simulering(1.vedtaksperiode))
        person.håndter(utbetalingsgodkjenning(1.vedtaksperiode, true, godkjentAv, automatiskBehandling))
    }

    private fun utbetaling(vedtaksperiodeId: UUID, status: UtbetalingHendelse.Oppdragstatus) =
        UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "$vedtaksperiodeId",
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = ORGNUMMER,
            utbetalingsreferanse = "ref",
            status = status,
            melding = "hei",
            saksbehandler = "Z999999",
            saksbehandlerEpost = "mille.mellomleder@nav.no",
            godkjenttidspunkt = LocalDateTime.now(),
            annullert = false
        ).apply {
            hendelse = this
        }

    private fun utbetalingsgodkjenning(
        vedtaksperiodeId: UUID,
        godkjent: Boolean,
        godkjentAv: String,
        automatiskBehandling: Boolean
    ) = Utbetalingsgodkjenning(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = AKTØRID,
        fødselsnummer = UNG_PERSON_FNR_2018,
        organisasjonsnummer = ORGNUMMER,
        vedtaksperiodeId = "$vedtaksperiodeId",
        saksbehandler = godkjentAv,
        utbetalingGodkjent = godkjent,
        godkjenttidspunkt = LocalDateTime.now(),
        automatiskBehandling = automatiskBehandling,
        saksbehandlerEpost = "mille.mellomleder@nav.no"
    ).apply {
        hendelse = this
    }

    private fun ytelser(
        vedtaksperiodeId: UUID,
        utbetalinger: List<Utbetalingshistorikk.Periode> = emptyList(),
        foreldrepengeYtelse: Periode? = null,
        svangerskapYtelse: Periode? = null
    ) = Aktivitetslogg().let {
        val meldingsreferanseId = UUID.randomUUID()
        Ytelser(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = "$vedtaksperiodeId",
            utbetalingshistorikk = Utbetalingshistorikk(
                meldingsreferanseId = meldingsreferanseId,
                aktørId = AKTØRID,
                fødselsnummer = UNG_PERSON_FNR_2018,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = "$vedtaksperiodeId",
                utbetalinger = utbetalinger,
                inntektshistorikk = emptyList(),
                aktivitetslogg = it
            ),
            foreldrepermisjon = Foreldrepermisjon(
                foreldrepengeytelse = foreldrepengeYtelse,
                svangerskapsytelse = svangerskapYtelse,
                aktivitetslogg = it
            ),
            pleiepenger = Pleiepenger(
                perioder = emptyList(),
                aktivitetslogg = it
            ),
            omsorgspenger = Omsorgspenger(
                perioder = emptyList(),
                aktivitetslogg = it
            ),
            opplæringspenger = Opplæringspenger(
                perioder = emptyList(),
                aktivitetslogg = it
            ),
            institusjonsopphold = Institusjonsopphold(
                perioder = emptyList(),
                aktivitetslogg = it
            ),
            aktivitetslogg = it
        ).apply {
            hendelse = this
        }
    }

    private fun sykmelding() =
        Sykmelding(
            meldingsreferanseId = sykmeldingHendelseId,
            fnr = UNG_PERSON_FNR_2018,
            aktørId = AKTØRID,
            orgnummer = ORGNUMMER,
            sykeperioder = listOf(Sykmeldingsperiode(førsteSykedag, sisteSykedag, 100)),
            mottatt = førsteSykedag.plusMonths(3).atStartOfDay()
        ).apply {
            hendelse = this
        }

    private fun søknad() =
        Søknad(
            meldingsreferanseId = søknadHendelseId,
            fnr = UNG_PERSON_FNR_2018,
            aktørId = AKTØRID,
            orgnummer = ORGNUMMER,
            perioder = listOf(Søknad.Søknadsperiode.Sykdom(førsteSykedag, sisteSykedag, 100)),
            harAndreInntektskilder = false,
            sendtTilNAV = sisteSykedag.atStartOfDay(),
            permittert = false
        ).apply {
            hendelse = this
        }

    private fun inntektsmelding() =
        Inntektsmelding(
            meldingsreferanseId = inntektsmeldingHendelseId,
            refusjon = Inntektsmelding.Refusjon(null, INNTEKT, emptyList()),
            orgnummer = ORGNUMMER,
            fødselsnummer = UNG_PERSON_FNR_2018,
            aktørId = AKTØRID,
            førsteFraværsdag = førsteSykedag,
            beregnetInntekt = INNTEKT,
            arbeidsgiverperioder = listOf(Periode(førsteSykedag, førsteSykedag.plusDays(16))),
            ferieperioder = emptyList(),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        ).apply {
            hendelse = this
        }

    private fun vilkårsgrunnlag(vedtaksperiodeId: UUID) =
        Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "$vedtaksperiodeId",
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = ORGNUMMER,
            inntektsvurdering = Inntektsvurdering(inntektperioder {
                1.januar(2018) til 1.desember(2018) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }),
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            opptjeningvurdering = Opptjeningvurdering(
                listOf(
                    Opptjeningvurdering.Arbeidsforhold(
                        ORGNUMMER,
                        1.januar(2017)
                    )
                )
            ),
            dagpenger = Dagpenger(emptyList()),
            arbeidsavklaringspenger = Arbeidsavklaringspenger(emptyList())
        ).apply {
            hendelse = this
        }

    private fun simulering(vedtaksperiodeId: UUID) =
        Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "$vedtaksperiodeId",
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = ORGNUMMER,
            simuleringOK = true,
            melding = "",
            simuleringResultat = null
        ).apply {
            hendelse = this
        }
}
