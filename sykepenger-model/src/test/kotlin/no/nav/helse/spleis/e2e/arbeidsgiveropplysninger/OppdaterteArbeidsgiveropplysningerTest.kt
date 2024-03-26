package no.nav.helse.spleis.e2e.arbeidsgiveropplysninger

import java.time.LocalDate
import no.nav.helse.Toggle
import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSkjønnsmessigFastsettelse
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OppdaterteArbeidsgiveropplysningerTest: AbstractEndToEndTest() {
    @Test
    fun `Sender ny forespørsel når korrigerende søknad kommer før vi har fått svar på forrige forespørsel -- flytter skjæringstidspunktet`() {
        nyPeriode(2.januar til 31.januar)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(2.januar, 31.januar, 100.prosent), egenmeldinger = listOf(Søknad.Søknadsperiode.Arbeidsgiverdag(1.januar, 1.januar)))

        val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
            skjæringstidspunkt = 1.januar,
            sykmeldingsperioder = listOf(2.januar til 31.januar),
            egenmeldingsperioder = listOf(1.januar til 1.januar),
            førsteFraværsdager = listOf(
                mapOf("organisasjonsnummer" to a1, "førsteFraværsdag" to 1.januar)
            ),
            forespurteOpplysninger = listOf(
                PersonObserver.Inntekt(forslag = null),
                PersonObserver.Refusjon(forslag = emptyList()),
                PersonObserver.Arbeidsgiverperiode
            )
        )

        assertTilstander(1.vedtaksperiode,
            TilstandType.START,
            TilstandType.AVVENTER_INFOTRYGDHISTORIKK,
            TilstandType.AVVENTER_INNTEKTSMELDING
        )
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(expectedForespørsel, actualForespørsel)
    }

    @Test
    fun `Sender ny forespørsel når korrigerende søknad kommer før vi har fått svar på forrige forespørsel -- flytter ikke skjæringstidspunktet`() {
        håndterSykmelding(Sykmeldingsperiode(5.januar, 31.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(5.januar, 31.januar, 100.prosent), egenmeldinger = listOf(Søknad.Søknadsperiode.Arbeidsgiverdag(1.januar, 1.januar)))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(5.januar, 31.januar, 100.prosent), egenmeldinger = listOf(Søknad.Søknadsperiode.Arbeidsgiverdag(1.januar, 2.januar)))

        val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
            skjæringstidspunkt = 5.januar,
            sykmeldingsperioder = listOf(5.januar til 31.januar),
            egenmeldingsperioder = listOf(1.januar til 2.januar),
            førsteFraværsdager = listOf(
                mapOf("organisasjonsnummer" to a1, "førsteFraværsdag" to 5.januar)
            ),
            forespurteOpplysninger = listOf(
                PersonObserver.Inntekt(forslag = null),
                PersonObserver.Refusjon(forslag = emptyList()),
                PersonObserver.Arbeidsgiverperiode
            )
        )

        assertTilstander(1.vedtaksperiode,
            TilstandType.START,
            TilstandType.AVVENTER_INFOTRYGDHISTORIKK,
            TilstandType.AVVENTER_INNTEKTSMELDING
        )
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(expectedForespørsel, actualForespørsel)
    }

    @Test
    fun `Korrigerende søknad fjerner ikke egenmeldingsdager, er det tiltenkt`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 31.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(2.januar, 31.januar, 100.prosent), egenmeldinger = listOf(Søknad.Søknadsperiode.Arbeidsgiverdag(1.januar, 1.januar)))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(2.januar, 31.januar, 100.prosent))

        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertForventetFeil(
            forklaring = "Vi fjerner ikke egenmeldingsdager dersom korrigerende søknad ikke har egenmeldinger, er dette tiltenkt?",
            nå = {
                assertEquals(listOf(1.januar til 1.januar), actualForespørsel.egenmeldingsperioder)
            },
            ønsket = {
                Assertions.fail("""\_(ツ)_/¯""")
            }
        )
    }

    @Test
    fun `Søknad fra annen arbeidsgiver flytter skjæringstidspunktet i AVVENTER_INNTEKTSMELDING`() {
        nyPeriode(2.januar til 31.januar, orgnummer = a1)
        nyPeriode(1.januar til 31.januar, orgnummer = a2)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) }.size)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) }.size)

        val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
            organisasjonsnummer = a1,
            vedtaksperiodeId = 1.vedtaksperiode.id(a1),
            skjæringstidspunkt = 1.januar,
            sykmeldingsperioder = listOf(2.januar til 31.januar),
            egenmeldingsperioder = emptyList(),
            førsteFraværsdager = listOf(
                mapOf("organisasjonsnummer" to a1, "førsteFraværsdag" to 2.januar),
                mapOf("organisasjonsnummer" to a2, "førsteFraværsdag" to 1.januar)
            ),
            forespurteOpplysninger = listOf(
                PersonObserver.Inntekt(forslag = null),
                PersonObserver.Refusjon(forslag = emptyList()),
                PersonObserver.Arbeidsgiverperiode
            )
        )
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last {
            it.vedtaksperiodeId == 1.vedtaksperiode.id(a1)
        }
        assertEquals(expectedForespørsel, actualForespørsel)
    }

    @Test
    fun `Søknad fra annen arbeidsgiver flytter skjæringstidspunktet, skal ikke be om nye opplysninger i annen tilstand enn AVVENTER_INNTEKTSMELDING`() {
        nyttVedtak(2.januar, 31.januar, orgnummer = a1)
        nyPeriode(1.januar til 31.januar, orgnummer = a2)

        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) }.size)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) }.size)
    }

    @Test
    fun `oppdaterte opplysninger for mars når ag2 tetter gapet`() {
        nyPeriode(1.januar til 31.januar, a1)
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(
                a1 to INNTEKT,
                a2 to INNTEKT
            ), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT)
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        nyPeriode(1.mars til 31.mars, orgnummer = a1)
        nyPeriode(1.februar til 28.februar, orgnummer = a2)
        assertEquals(4, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) }.size)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) }.size)
        val arbeidsgiveropplysningerEventer = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter {
            it.vedtaksperiodeId == 2.vedtaksperiode.id(a1)
        }
        assertEquals(2, arbeidsgiveropplysningerEventer.size)
        arbeidsgiveropplysningerEventer.last().also { trengerArbeidsgiveropplysningerEvent ->
            val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
                organisasjonsnummer = a1,
                vedtaksperiodeId = 2.vedtaksperiode.id(a1),
                skjæringstidspunkt = 1.januar,
                sykmeldingsperioder = listOf(1.mars til 31.mars),
                egenmeldingsperioder = emptyList(),
                førsteFraværsdager = listOf(
                    mapOf("organisasjonsnummer" to a1, "førsteFraværsdag" to 1.januar),
                    mapOf("organisasjonsnummer" to a2, "førsteFraværsdag" to 1.februar)
                ),
                forespurteOpplysninger = listOf(
                    PersonObserver.FastsattInntekt(INNTEKT),
                    PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(im, 1.januar, null, INNTEKT))),
                    PersonObserver.Arbeidsgiverperiode
                )
            )
            assertEquals(expectedForespørsel, trengerArbeidsgiveropplysningerEvent)
        }
    }

    @Test
    fun `Overlappende søknad fører til oppdatert forespørsel`() {
        nyPeriode(20.januar til 20.februar, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Arbeid(18.januar, 31.januar), orgnummer = a2)

        assertEquals(20.januar, inspektør(a1).skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(1.januar, inspektør(a2).skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) }.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.filter { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) }.size)
    }

    @Test
    fun `Sender oppdatert forespørsel når vi vi får inn ny inntekt på en forrige periode`() {
        nyPeriode(1.januar til 31.januar)
        nyPeriode(10.februar til 10.mars)

        val im = håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertForventetFeil(
            forklaring = "ønsker å sende ut en oppdatert forespørsel for andre vedtaksperiode med oppdatert forrige inntekt",
            nå = {
                assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
                val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
                    organisasjonsnummer = ORGNUMMER,
                    vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                    skjæringstidspunkt = 10.februar,
                    sykmeldingsperioder = listOf(10.februar til 10.mars),
                    egenmeldingsperioder = emptyList(),
                    førsteFraværsdager = listOf(mapOf("organisasjonsnummer" to ORGNUMMER, "førsteFraværsdag" to 10.februar)),
                    forespurteOpplysninger = listOf(
                        PersonObserver.Inntekt(forslag = null),
                        PersonObserver.Refusjon(forslag = emptyList())
                    )
                )

                assertEquals(expectedForespørsel, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last())
            },
            ønsket = {
                assertEquals(3, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

                val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
                    organisasjonsnummer = ORGNUMMER,
                    vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                    skjæringstidspunkt = 10.februar,
                    sykmeldingsperioder = listOf(10.februar til 10.mars),
                    egenmeldingsperioder = emptyList(),
                    førsteFraværsdager = listOf(
                        mapOf("organisasjonsnummer" to ORGNUMMER, "førsteFraværsdag" to 10.februar),
                    ),
                    forespurteOpplysninger = listOf(
                        PersonObserver.Inntekt(forslag = PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
                        PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(im, 1.januar, null, INNTEKT))),
                        PersonObserver.Arbeidsgiverperiode
                    )
                )
                assertEquals(expectedForespørsel, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last())
            }
        )
    }

    @Test
    fun `sender oppdatert forespørsel om arbeidsgiveropplysninger når forrige periode som ikke er auu får et nytt vilkårsgrunnlag`()  = Toggle.OppdaterteForespørsler.enable {
        nyttVedtak(1.november(2017), 30.november(2017))  // skal ikke oppdatere tidligere perioder
        nyPeriode(1.januar til 31.januar)                   // periode som får et vilkårsgrunnlag som skal være med i oppdatert forespørsel
        nyPeriode(18.februar til 22.februar)                // en kort periode vi ikke skal bry oss om
        nyPeriode(1.mars til 31.mars)                       // perioden som skal sende ut oppdatert forespørsel
        nyPeriode(1.april til 5.april)                      // forlengelse i AvventerInntektsmelding som ikke skal sende ny forespørsel
        nyPeriode(1.mai til 31.mai)                         // skal ikke sende oppdatert forespørsel for senere skjæringstidspunkt enn førstkommende
        nyPeriode(1.juni til 5.juni)                        // skal ikke sende forespørsel for forlengelser

        assertEquals(4, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        val im = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
        håndterVilkårsgrunnlag(2.vedtaksperiode)

        assertEquals(5, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val oppdatertForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()

        assertEquals(4.vedtaksperiode.id(ORGNUMMER), oppdatertForespørsel.vedtaksperiodeId)
        assertEquals(
            PersonObserver.Inntekt(forslag = PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            oppdatertForespørsel.forespurteOpplysninger.first { it is PersonObserver.Inntekt }
        )

        assertEquals(
            PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(im, 1.januar, null, INNTEKT))),
            oppdatertForespørsel.forespurteOpplysninger.first { it is PersonObserver.Refusjon }
        )
    }

    @Test
    fun `Sender ikke med skjønnsmessig inntekt ved oppdatert forespørsel`() {
        nyttVedtak(1.januar, 31.januar)
        nyPeriode(1.mars til 31.mars)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(ORGNUMMER, INNTEKT/2)))
        assertEquals(3, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val forespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(
            PersonObserver.Inntekt(forslag = PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            forespørsel.forespurteOpplysninger.first { it is PersonObserver.Inntekt }
        )
    }

    @Test
    fun `Sender oppdatert forespørsel ved nytt vilkårsgrunnlag pga saksbehandleroverstyrt inntekt`() {
        nyttVedtak(1.januar, 31.januar)
        nyPeriode(1.mars til 31.mars)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(ORGNUMMER, 32000.månedlig)))

        assertEquals(3, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val forespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(
            PersonObserver.Inntekt(forslag = PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER, 32000.0)),
            forespørsel.forespurteOpplysninger.first { it is PersonObserver.Inntekt }
        )
    }

    @Test
    fun `Sender oppdatert forespørsel ved nytt vilkårsgrunnlag pga korrigerende inntektsmelding -- revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nyPeriode(1.mars til 31.mars)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 32000.månedlig)

        val forespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(
            PersonObserver.Inntekt(forslag = PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 32000.0)),
            forespørsel.forespurteOpplysninger.first { it is PersonObserver.Inntekt }
        )
    }

    @Test
    fun `Sender oppdatert forespørsel ved nytt vilkårsgrunnlag pga korrigerende inntektsmelding  -- overstyring`() {
        nyPeriode(1.januar til 31.januar)
        nyPeriode(1.mars til 31.mars)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 32000.månedlig)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 33000.månedlig)

        val forespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(
            PersonObserver.Inntekt(forslag = PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 33000.0)),
            forespørsel.forespurteOpplysninger.first { it is PersonObserver.Inntekt }
        )
    }

    @Test
    fun `sender ikke oppdatert forespørsel for en periode som har mottatt inntektsmelding`() {
        nyPeriode(1.januar til 31.januar)
        nyPeriode(1.mars til 31.mars)
        håndterInntektsmelding(listOf(1.mars til 31.mars))

        assertTilstand(2.vedtaksperiode, TilstandType.AVVENTER_BLOKKERENDE_PERIODE)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        håndterInntektsmelding(listOf(1.januar til 31.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)

        assertTilstand(2.vedtaksperiode, TilstandType.AVVENTER_BLOKKERENDE_PERIODE)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }
}