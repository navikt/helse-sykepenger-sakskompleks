package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingReplayUtført
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InntektsmeldingHendelseTest : AbstractPersonTest() {

    private companion object {
        private val INNTEKT_PR_MÅNED = 12340.månedlig
    }

    @Test
    fun `legger inn beregnet inntekt i inntekthistorikk`() {
        val inntekthistorikk = Inntektshistorikk()
        inntektsmelding(beregnetInntekt = INNTEKT_PR_MÅNED, førsteFraværsdag = 1.januar).let { it.addInntekt(inntekthistorikk, NullObserver) }
        assertEquals(INNTEKT_PR_MÅNED, inntekthistorikk.avklarSykepengegrunnlag(1.januar, 1.januar, null)?.inspektør?.beløp)
    }

    @Test
    fun `skjæringstidspunkt oppdateres i vedtaksperiode når inntektsmelding håndteres`() {
        person.håndter(UtbetalingshistorikkEtterInfotrygdendring(UUID.randomUUID(), "", "", InfotrygdhistorikkElement.opprett(
            oppdatert = LocalDateTime.now(),
            hendelseId = UUID.randomUUID(),
            perioder = emptyList(),
            inntekter = emptyList(),
            arbeidskategorikoder = emptyMap(),
        ), besvart = LocalDateTime.now()))
        person.håndter(sykmelding(Sykmeldingsperiode(6.januar, 20.januar)))
        person.håndter(søknad(Sykdom(6.januar, 20.januar, 100.prosent)))
        assertEquals(6.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
        person.håndter(inntektsmelding(førsteFraværsdag = 1.januar))
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(1.januar, inspektør.skjæringstidspunkt(1.vedtaksperiode))
    }

    @Test
    fun `ferie i inntektsmelding vinner over sykedager i sykmelding`() {
        val inntektsmelding = a1Hendelsefabrikk.lagInntektsmelding(
            refusjon = Inntektsmelding.Refusjon(INNTEKT_PR_MÅNED, null, emptyList()),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = INNTEKT_PR_MÅNED,
            arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )
        person.håndter(UtbetalingshistorikkEtterInfotrygdendring(UUID.randomUUID(), "", "", InfotrygdhistorikkElement.opprett(
            oppdatert = LocalDateTime.now(),
            hendelseId = UUID.randomUUID(),
            perioder = emptyList(),
            inntekter = emptyList(),
            arbeidskategorikoder = emptyMap()
        ), besvart = LocalDateTime.now()))
        person.håndter(sykmelding(Sykmeldingsperiode(6.januar, 20.januar)))
        person.håndter(søknad(Sykdom(6.januar, 20.januar, 100.prosent)))
        person.håndter(InntektsmeldingReplayUtført(UUID.randomUUID(), UNG_PERSON_FNR_2018.toString(), AKTØRID, ORGNUMMER, 1.vedtaksperiode.id(ORGNUMMER)))
        person.håndter(inntektsmelding)
        assertEquals(TilstandType.AVVENTER_VILKÅRSPRØVING, inspektør.sisteTilstand(1.vedtaksperiode))
    }

    private fun inntektsmelding(
        beregnetInntekt: Inntekt = 1000.månedlig,
        førsteFraværsdag: LocalDate = 1.januar
    ) = a1Hendelsefabrikk.lagInntektsmelding(
            refusjon = Inntektsmelding.Refusjon(beregnetInntekt, null, emptyList()),
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = beregnetInntekt,
            arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

    private fun sykmelding(vararg sykeperioder: Sykmeldingsperiode) = a1Hendelsefabrikk.lagSykmelding(
        sykeperioder = sykeperioder
    )

    private fun søknad(vararg perioder: Søknadsperiode) = a1Hendelsefabrikk.lagSøknad(
        perioder = arrayOf(*perioder),
        andreInntektskilder = false,
        sendtTilNAVEllerArbeidsgiver = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive,
        permittert = false,
        merknaderFraSykmelding = emptyList(),
        sykmeldingSkrevet = LocalDateTime.now()
    )
}
