package no.nav.helse.person

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.dsl.lagStandardInntekterForOpptjeningsvurdering
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Foreldrepenger
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Simulering
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.hendelser.GradertPeriode
import no.nav.helse.hendelser.Svangerskapspenger
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.sisteBehov
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GodkjenningHendelseTest : AbstractPersonTest() {
    private companion object {
        private val førsteSykedag = 1.januar
        private val sisteSykedag = 31.januar
    }

    private lateinit var hendelse: ArbeidstakerHendelse

    @Test
    fun `utbetaling er godkjent`() {
        håndterYtelser()
        person.håndter(simulering())
        person.håndter(utbetalingsgodkjenning(true))
        assertEquals(TIL_UTBETALING, inspektør.sisteTilstand(1.vedtaksperiode))
    }

    @Test
    fun `utbetaling ikke godkjent`() {
        håndterYtelser()
        person.håndter(simulering())
        person.håndter(utbetalingsgodkjenning(false))
        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertEquals(TIL_INFOTRYGD, inspektør.sisteTilstand(1.vedtaksperiode))
    }

    @Test
    fun `hendelse etter til utbetaling`() {
        håndterYtelser()
        person.håndter(simulering())
        person.håndter(utbetalingsgodkjenning(true))
        assertEquals(TIL_UTBETALING, inspektør.sisteTilstand(1.vedtaksperiode))
        person.håndter(ytelser())
        assertEquals(TIL_UTBETALING, inspektør.sisteTilstand(1.vedtaksperiode))
        assertEquals(1, inspektør.vedtaksperiodeTeller)
    }

    @Test
    fun `dobbelt svar fra saksbehandler`() {
        håndterYtelser()
        person.håndter(simulering())
        person.håndter(utbetalingsgodkjenning(true))
        person.håndter(utbetalingsgodkjenning(true))
        assertEquals(TIL_UTBETALING, inspektør.sisteTilstand(1.vedtaksperiode))
    }

    private fun håndterYtelser() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(utbetalingshistorikk())
        person.håndter(inntektsmelding())
        person.håndter(ytelser())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
    }

    private fun utbetalingsgodkjenning(godkjent: Boolean) = Utbetalingsgodkjenning(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018.toString(),
        organisasjonsnummer = ORGNUMMER,
        utbetalingId = UUID.fromString(
            person.personLogg.sisteBehov(Behovtype.Godkjenning).kontekst()["utbetalingId"] ?: throw IllegalStateException(
                "Finner ikke utbetalingId i: ${
                    person.personLogg.sisteBehov(
                        Behovtype.Godkjenning
                    ).kontekst()
                }"
            )
        ),
        vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
        saksbehandler = "Ola Nordmann",
        saksbehandlerEpost = "ola@nordmann.ss",
        utbetalingGodkjent = godkjent,
        godkjenttidspunkt = LocalDateTime.now(),
        automatiskBehandling = false,
    ).apply {
        hendelse = this
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
                foreldrepengeytelse = foreldrepengeYtelse,
            ),
            svangerskapspenger = Svangerskapspenger(
                svangerskapsytelse = svangerskapYtelse
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
            arbeidsavklaringspenger = Arbeidsavklaringspenger(emptyList()),
            dagpenger = Dagpenger(emptyList()),
            aktivitetslogg = it
        ).apply {
            hendelse = this
        }
    }

    private fun sykmelding() =
        a1Hendelsefabrikk.lagSykmelding(
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

    private fun utbetalingshistorikk() =
        a1Hendelsefabrikk.lagUtbetalingshistorikk(
            vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER)
        ).apply {
            hendelse = this
        }

    private fun inntektsmelding() =
        a1Hendelsefabrikk.lagInntektsmelding(
            refusjon = Inntektsmelding.Refusjon(31000.månedlig, null, emptyList()),
            førsteFraværsdag = førsteSykedag,
            beregnetInntekt = 31000.månedlig,
            arbeidsgiverperioder = listOf(Periode(førsteSykedag, førsteSykedag.plusDays(16))),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null,
        ).apply {
            hendelse = this
        }

    private fun vilkårsgrunnlag() =
        Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "${1.vedtaksperiode.id(ORGNUMMER)}",
            skjæringstidspunkt = førsteSykedag,
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
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering(ORGNUMMER, 31000.månedlig, førsteSykedag),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(ORGNUMMER, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT)
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
            simuleringsResultat = SimuleringResultatDto(
                totalbeløp = 1000,
                perioder = emptyList()
            ),
            utbetalingId = UUID.fromString(person.personLogg.sisteBehov(Behovtype.Simulering).kontekst().getValue("utbetalingId"))
        ).apply {
            hendelse = this
        }
}
