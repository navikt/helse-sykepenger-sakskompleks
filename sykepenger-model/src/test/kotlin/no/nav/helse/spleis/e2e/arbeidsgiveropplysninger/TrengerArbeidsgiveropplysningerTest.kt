package no.nav.helse.spleis.e2e.arbeidsgiveropplysninger

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.oktober
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.finnSkjæringstidspunkt
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterInntektsmeldingPortal
import no.nav.helse.spleis.e2e.håndterOverstyrArbeidsgiveropplysninger
import no.nav.helse.spleis.e2e.håndterPåminnelse
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSkjønnsmessigFastsettelse
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TrengerArbeidsgiveropplysningerTest : AbstractEndToEndTest() {

    private val INNTEKT_FLERE_AG = 20000.månedlig

    @Test
    fun `flere korte perioder - sender ikke ut ny oppdatert forespørsel ved mottak av im`() {
        håndterSøknad(Sykdom(1.januar, 5.januar, 100.prosent))
        håndterSøknad(Sykdom(6.januar, 7.januar, 100.prosent))
        håndterSøknad(Sykdom(8.januar, 13.januar, 100.prosent))
        håndterSøknad(Sykdom(14.januar, 21.januar, 100.prosent))
        håndterSøknad(Sykdom(22.januar, 26.januar, 100.prosent))
        håndterSøknad(Sykdom(27.januar, 28.januar, 100.prosent))
        håndterSøknad(Sykdom(29.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingPortal(listOf(1.januar til 16.januar), inntektsdato = 1.januar)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(4.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        assertSisteTilstand(5.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertSisteTilstand(6.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertSisteTilstand(7.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `første fraværsdag vi mottar i IM blir feil når det er ferie første dag i sykmeldingsperioden etter kort gap`() {
        nyttVedtak(januar)

        håndterSøknad(Sykdom(12.februar, 28.februar, 100.prosent), Ferie(12.februar, 12.februar))
        observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().let { event ->
            assertEquals(listOf(12.februar til 28.februar), event.sykmeldingsperioder)
            assertEquals(13.februar, event.skjæringstidspunkt)
            assertEquals(listOf(PersonObserver.FørsteFraværsdag(ORGNUMMER, 13.februar)), event.førsteFraværsdager)
            assertFalse(event.forespurteOpplysninger.any { it is PersonObserver.Arbeidsgiverperiode })
        }
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        håndterInntektsmeldingPortal(emptyList(), førsteFraværsdag = 12.februar, inntektsdato = 13.februar)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `replayer ikke portalinnsendt inntektsmelding`() {
        håndterInntektsmeldingPortal(listOf(1.januar til 5.januar, 10.januar til 20.januar), inntektsdato = 10.januar, vedtaksperiodeId = UUID.randomUUID())
        håndterSøknad(Sykdom(10.januar, 31.januar, 100.prosent))

        assertForventetFeil(
            forklaring = "inntektsmeldingen blir ikke replayet, men fordi inntekt og refusjon håndteres på arbeidsgivernivå så vil" +
                    "vedtaksperioden tilsynelatende ha det den trenger. " +
                    "TODO: Portalinnsendte inntektsmeldinger burde ikke blitt håndtert utenfor vedtaksperioden som ba om opplysningene",
            nå = {
                assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
                assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
                assertEquals(10.januar til 31.januar, inspektør.periode(1.vedtaksperiode))
            },
            ønsket = {
                assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
                assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
                assertEquals(januar, inspektør.periode(1.vedtaksperiode))
            }
        )
    }

    @Test
    fun `sender ut event TrengerArbeidsgiveropplysninger når vi ankommer AvventerInntektsmelding`() {
        nyPeriode(januar)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `ber ikke om arbeidsgiveropplysninger på ghost når riktig inntektsmelding kommer`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(
                    a1 to INNTEKT,
                    a2 to INNTEKT
            ), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(11.februar, 28.februar, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)

        assertEquals(1, observatør.inntektsmeldingHåndtert.size)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.organisasjonsnummer == a1 })
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.organisasjonsnummer == a2 })

        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2)

        assertEquals(2, observatør.inntektsmeldingHåndtert.size)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.organisasjonsnummer == a1 })
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.organisasjonsnummer == a2 })
    }

    @Test
    fun `ber ikke om arbeidsgiveropplysninger på forlengelse med forskjøvet agp`() {
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent), Ferie(1.januar, 11.januar))
        håndterSøknad(Sykdom(16.januar, 29.januar, 100.prosent), Ferie(16.januar, 16.januar))
        håndterSøknad(Sykdom(30.januar, 8.februar, 100.prosent))
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2.vedtaksperiode.id(ORGNUMMER), observatør.trengerArbeidsgiveropplysningerVedtaksperioder.single().vedtaksperiodeId)
    }

    @Test
    fun `auu håndterer dager før forlengelsen håndterer inntekt`() {
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent), Ferie(1.januar, 11.januar))
        håndterSøknad(Sykdom(16.januar, 29.januar, 100.prosent), Ferie(16.januar, 16.januar))
        håndterInntektsmelding(listOf(12.januar til 27.januar))
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2.vedtaksperiode.id(ORGNUMMER), observatør.trengerArbeidsgiveropplysningerVedtaksperioder.single().vedtaksperiodeId)
    }

    @Test
    fun `skal ikke be om arbeidsgiverperiode når det er mindre en 16 dagers gap`() {
        val inntektsmeldingId = UUID.randomUUID()
        nyttVedtak(januar, inntektsmeldingId = inntektsmeldingId)
        nyPeriode(16.februar til 15.mars)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT)))
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `skal ikke be om arbeidsgiveropplysninger ved forlengelse`() {
        nyttVedtak(januar)
        forlengVedtak(februar)

        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `skal ikke be om arbeidsgiveropplysninger ved forlengelse selv når inntektsmeldingen ikke har kommet enda`() {
        nyPeriode(januar)
        nyPeriode(februar)

        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `skal be om arbeidsgiverperiode ved 16 dagers gap`() {
        val inntektsmeldingId = UUID.randomUUID()
        nyttVedtak(januar, inntektsmeldingId = inntektsmeldingId)
        nyPeriode(17.februar til 17.mars)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT))),
            PersonObserver.Arbeidsgiverperiode
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `sender med skjæringstidspunkt i eventet`() {
        nyPeriode(januar)
        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()

        assertEquals(1.januar, trengerArbeidsgiveropplysningerEvent.skjæringstidspunkt)
    }

    @Test
    fun `sender med begge sykmeldingsperiodene når vi har en kort periode som forlenges av en lang`() {
        nyPeriode(1.januar til 16.januar)
        nyPeriode(17.januar til 31.januar)

        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        val expectedSykmeldingsperioder = listOf(
            1.januar til 16.januar,
            17.januar til 31.januar
        )
        assertEquals(expectedSykmeldingsperioder, trengerArbeidsgiveropplysningerEvent.sykmeldingsperioder)

        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `sender ikke med begge sykmeldingsperiodene når vi har et gap større enn 16 dager mellom dem`() {
        nyPeriode(januar)
        nyPeriode(17.februar til 17.mars)

        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        val expectedSykmeldingsperioder = listOf(17.februar til 17.mars)
        assertEquals(expectedSykmeldingsperioder, trengerArbeidsgiveropplysningerEvent.sykmeldingsperioder)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `sender med riktig sykmeldingsperioder når arbeidsgiverperioden er stykket opp i flere korte perioder`() {
        nyPeriode(1.januar til 7.januar)
        nyPeriode(9.januar til 14.januar)
        nyPeriode(16.januar til 21.januar)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList()),
            PersonObserver.Arbeidsgiverperiode
        )

        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(expectedForespurteOpplysninger, trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger)

        val expectedSykmeldingsperioder = listOf(
            1.januar til 7.januar,
            9.januar til 14.januar,
            16.januar til 21.januar
        )
        assertEquals(expectedSykmeldingsperioder, trengerArbeidsgiveropplysningerEvent.sykmeldingsperioder)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `ber ikke om inntekt når vi allerede har inntekt på skjæringstidspunktet -- med arbeidsgiverperiode`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, orgnummer = a2)
        nyPeriode(mars, a1)

        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a1)

        assertEquals(5, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) })
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) })
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a1) })

        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        val inntektsmeldingId = inspektør(a1).hendelseIder(1.vedtaksperiode.id(a1)).last()
        val expectedForespurteOpplysninger = listOf(
            PersonObserver.FastsattInntekt(INNTEKT_FLERE_AG),
            PersonObserver.Refusjon(listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT_FLERE_AG))),
            PersonObserver.Arbeidsgiverperiode
        )
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `ber ikke om inntekt og AGP når vi har inntekt på skjæringstidspunkt og det er mindre enn 16 dagers gap`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(1.februar til 10.februar, orgnummer = a2)
        nyPeriode(11.februar til 28.februar, a1)

        assertEquals(5, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) })
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) })
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a1) })
        val inntektsmeldingId = inspektør(a1).hendelseIder(1.vedtaksperiode.id(a1)).last()

        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        val expectedForespurteOpplysninger = listOf(
            PersonObserver.FastsattInntekt(INNTEKT_FLERE_AG),
            PersonObserver.Refusjon(listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT_FLERE_AG)))
        )
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `sender med FastsattInntekt når vi allerede har en inntektsmelding lagt til grunn på skjæringstidspunktet`() {
        nyeVedtak(januar, a1, a2)
        forlengVedtak(februar, orgnummer = a2)
        nyPeriode(mars, a1)

        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a1)

        assertEquals(5, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) })
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) })
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a1) })
        val inntektsmeldingId = inspektør(a1).hendelseIder(1.vedtaksperiode.id(a1)).last()
        val actualForespurtOpplysning = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.FastsattInntekt(INNTEKT_FLERE_AG),
            PersonObserver.Refusjon(listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT_FLERE_AG))),
            PersonObserver.Arbeidsgiverperiode
        )

        assertEquals(expectedForespurteOpplysninger, actualForespurtOpplysning)
    }

    @Test
    fun `sender med første fraværsdag på alle arbeidsgivere for skjæringstidspunktet`() {
        nyeVedtakMedUlikFom(mapOf(
            a1 to (januar),
            a2 to (2.januar til 31.januar)
        ))
        assertEquals(4, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
            organisasjonsnummer = a2,
            vedtaksperiodeId = 1.vedtaksperiode.id(a2),
            skjæringstidspunkt = 1.januar,
            sykmeldingsperioder = listOf(2.januar til 31.januar),
            egenmeldingsperioder = emptyList(),
            førsteFraværsdager = listOf(
                PersonObserver.FørsteFraværsdag(a1, 1.januar),
                PersonObserver.FørsteFraværsdag(a2, 2.januar)
            ),
            forespurteOpplysninger = listOf(
                PersonObserver.Inntekt(null),
                PersonObserver.Refusjon(forslag = emptyList()),
                PersonObserver.Arbeidsgiverperiode
            )
        )
        assertEquals(expectedForespørsel, actualForespørsel)
    }

    @Test
    fun `sender med FastsattInntekt når vi allerede har inntekt fra skatt lagt til grunn på skjæringstidspunktet`() {
        nyeVedtakMedUlikFom(
            mapOf(
                a1 to (31.desember(2017) til 31.januar),
                a2 to (januar)
            )
        )
        forlengVedtak(februar, orgnummer = a1)
        nyPeriode(mars, a2)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

        assertEquals(5, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) })
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a2) })
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a1) })
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a2) })
        val inntektsmeldingId = inspektør(a2).hendelseIder(1.vedtaksperiode.id(a2)).last()

        val actualForespurtOpplysning =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        val expectedForespurteOpplysninger = listOf(
        PersonObserver.FastsattInntekt(INNTEKT_FLERE_AG),
            PersonObserver.Refusjon(listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT_FLERE_AG))),
            PersonObserver.Arbeidsgiverperiode
        )
        assertEquals(expectedForespurteOpplysninger, actualForespurtOpplysning)
    }

    @Test
    fun `be om arbeidsgiverperiode ved forlengelse av en kort periode`() {
        nyPeriode(1.januar til 16.januar)
        nyPeriode(17.januar til 31.januar)

        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList()),
            PersonObserver.Arbeidsgiverperiode
        )

        assertEquals(expectedForespurteOpplysninger, trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `be om arbeidsgiverperiode når en kort periode har et lite gap til ny periode`() {
        nyPeriode(1.januar til 16.januar)
        nyPeriode(20.januar til 31.januar)

        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList()),
            PersonObserver.Arbeidsgiverperiode
        )

        assertEquals(expectedForespurteOpplysninger, trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Skal ikke be om arbeidsgiverperiode når vi allerede har motatt inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(20.januar, 25.januar))
        håndterSøknad(Sykdom(20.januar, 25.januar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar))

        val trengerArbeidsgiveropplysningerEvent = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertFalse(trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger.contains(PersonObserver.Arbeidsgiverperiode))
    }

    @Test
    fun `blir syk fra ghost`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntektperioderForSykepengegrunnlag {
                1.oktober(2017) til 1.desember(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }, emptyList()),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

        val arbeidsgiveropplysningerEvents = observatør.trengerArbeidsgiveropplysningerVedtaksperioder
        assertEquals(2, arbeidsgiveropplysningerEvents.size)
        val trengerArbeidsgiveropplysningerEvent = arbeidsgiveropplysningerEvents.last()

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.FastsattInntekt(INNTEKT),
            PersonObserver.Refusjon(emptyList()),
            PersonObserver.Arbeidsgiverperiode
        )
        assertEquals(expectedForespurteOpplysninger, trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger)

        nullstillTilstandsendringer()
        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2)
        assertVarsel(RV_IM_4, AktivitetsloggFilter.arbeidsgiver(a1))
        assertVarsel(RV_IM_4, AktivitetsloggFilter.arbeidsgiver(a2))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `skal kun sende med refusjonsopplysninger som overlapper med, eller er etter, vedtaksperioden som ikke trenger inntektsopplysninger fra arbeidsgiver`() {
        gapHosÉnArbeidsgiver(Inntektsmelding.Refusjon(
            beløp = INNTEKT_FLERE_AG,
            opphørsdato = null,
            endringerIRefusjon = listOf(
                Inntektsmelding.Refusjon.EndringIRefusjon(18000.månedlig, 10.februar),
                Inntektsmelding.Refusjon.EndringIRefusjon(17000.månedlig, 1.mars),
                Inntektsmelding.Refusjon.EndringIRefusjon(16000.månedlig, 10.mars),
                Inntektsmelding.Refusjon.EndringIRefusjon(15000.månedlig, 1.april)
            )
        ))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)

        assertForventetFeil(
            forklaring = "perioden i mars (2.vedtaksperiode for a2) har en ny arbeidsgiverperiode og skal vente på opplysninger fra AG",
            nå = {
                assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a2)
            },
            ønsket = {
                assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

                val arbeidsgiveropplysningerEvents = observatør.trengerArbeidsgiveropplysningerVedtaksperioder
                assertEquals(3, arbeidsgiveropplysningerEvents.size)
                val trengerArbeidsgiveropplysningerEvent = arbeidsgiveropplysningerEvents.last()
                val inntektsmeldingId = inspektør(a2).hendelseIder(1.vedtaksperiode.id(a2)).last()

                val expectedForespurteOpplysninger = listOf(
                    PersonObserver.FastsattInntekt(INNTEKT_FLERE_AG),
                    PersonObserver.Refusjon(
                        listOf(
                            Refusjonsopplysning(inntektsmeldingId, 1.mars, 9.mars, 17000.månedlig),
                            Refusjonsopplysning(inntektsmeldingId, 10.mars, 31.mars, 16000.månedlig),
                            Refusjonsopplysning(inntektsmeldingId, 1.april, null, 15000.månedlig)
                        )
                    ),
                    PersonObserver.Arbeidsgiverperiode
                )

                assertEquals(expectedForespurteOpplysninger, trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger)
            }
        )
    }

    @Test
    fun `skal sende med riktig refusjonsopplysninger ved ingen refusjon`() {
        gapHosÉnArbeidsgiver(
            Inntektsmelding.Refusjon(
                beløp = INNTEKT_FLERE_AG,
                opphørsdato = 15.mars
        ))

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)

        assertForventetFeil(
            forklaring = "perioden i mars (2.vedtaksperiode for a2) har en ny arbeidsgiverperiode og skal vente på opplysninger fra AG",
            nå = {
                assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a2)
            },
            ønsket = {
                assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

                val trengerArbeidsgiveropplysningerEvents = observatør.trengerArbeidsgiveropplysningerVedtaksperioder
                assertEquals(3, trengerArbeidsgiveropplysningerEvents.size)

                val trengerArbeidsgiveropplysningerEvent = trengerArbeidsgiveropplysningerEvents.last()
                val inntektsmeldingId = inspektør(a2).hendelseIder(1.vedtaksperiode.id(a2)).last()

                val expectedForespurteOpplysninger = listOf(
                    PersonObserver.FastsattInntekt(INNTEKT_FLERE_AG),
                    PersonObserver.Refusjon(
                        listOf(
                            Refusjonsopplysning(inntektsmeldingId, 1.januar, 15.mars, INNTEKT_FLERE_AG),
                            Refusjonsopplysning(inntektsmeldingId, 16.mars, null, INGEN)
                        )
                    ),
                    PersonObserver.Arbeidsgiverperiode
                )

                assertEquals(expectedForespurteOpplysninger, trengerArbeidsgiveropplysningerEvent.forespurteOpplysninger)
            }
        )
    }

    @Test
    fun `Skal ikke be om arbeidsgiveropplysninger for perioder innenfor arbeidsgiverperioden`() {
        nyPeriode(1.januar til 10.januar)
        assertTrue(observatør.trengerArbeidsgiveropplysningerVedtaksperioder.isEmpty())
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `Skal ikke sende ut forespørsel for en periode som allerede har mottatt inntektsmelding`() {
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        nyPeriode(januar)
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Periode etter kort gap skal ikke sende forespørsel dersom inntektsmeldingen allerede er mottatt`() {
        nyttVedtak(januar)

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 10.februar)
        håndterSykmelding(Sykmeldingsperiode(10.februar, 5.mars))
        håndterSøknad(Sykdom(10.februar, 5.mars, sykmeldingsgrad = 100.prosent))

        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Skal ikke sende ut forespørsel for en periode som allerede har mottatt inntektsmelding -- selv om håndteringen feiler`() {
        håndterInntektsmelding(listOf(1.januar til 16.januar), harOpphørAvNaturalytelser = true)
        nyPeriode(januar)
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Skal sende ut forespørsel for en periode dersom inntektsmeldingReplay ikke bærer noen frukter`() {
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        nyPeriode(februar)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Skal sende egenmeldingsdager fra søknad i forespørsel`() {
        håndterSykmelding(Sykmeldingsperiode(5.januar, 31.januar))
        håndterSøknad(Sykdom(5.januar, 31.januar, 100.prosent), egenmeldinger = listOf(1.januar til 4.januar))

        val expectedEgenmeldinger = listOf(1.januar til 4.januar)
        assertEquals(expectedEgenmeldinger, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().egenmeldingsperioder)
    }

    @Test
    fun `Sender med egenmeldingsdager fra kort søknad`() {
        håndterSykmelding(Sykmeldingsperiode(5.januar, 10.januar))
        håndterSøknad(Sykdom(5.januar, 10.januar, 100.prosent), egenmeldinger = listOf(4.januar til 4.januar))
        nyPeriode(11.januar til 31.januar)

        val expectedEgenmeldinger = listOf(4.januar til 4.januar)
        assertEquals(expectedEgenmeldinger, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().egenmeldingsperioder)
    }

    @Test
    fun `Skal ikke sende med skjønnsfastsatt sykpengegrunnlag som inntektForrigeSkjæringstidspunkt` () {
        val inntektsmeldingId = UUID.randomUUID()
        nyttVedtak(januar, inntektsmeldingId = inntektsmeldingId)
        håndterSkjønnsmessigFastsettelse(1.januar, listOf(OverstyrtArbeidsgiveropplysning(ORGNUMMER, INNTEKT *2)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        nyPeriode(16.februar til 15.mars)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INNTEKT))),

        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `Skal sende med saksbehandlerinntekt som inntektForrigeSkjæringstidspunkt` () {
        nyttVedtak(januar)
        val id = håndterOverstyrArbeidsgiveropplysninger(
            skjæringstidspunkt = 1.januar,
            arbeidsgiveropplysninger = listOf(
                OverstyrtArbeidsgiveropplysning(ORGNUMMER, 32000.månedlig, "", null, listOf(Triple(1.januar, null, 32000.månedlig)))
            )
        )
        håndterYtelser(1.vedtaksperiode)

        nyPeriode(16.februar til 15.mars)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER, 32000.0)),
            PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(id, 1.januar, null, 32000.månedlig))),
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `Skal ikke sende med inntektForrigeSkjæringstidspunkt fra annen arbeidsgiver`() {
        nyttVedtak(januar, orgnummer = a1)
        nyPeriode(mars, orgnummer = a2)

        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList()),
            PersonObserver.Arbeidsgiverperiode
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `Sender kun med refusjonsopplysninger som overlapper med eller er nyere enn forespørselsperioden`() {
        nyPeriode(januar)
        val id = håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(
                INNTEKT, 1.april, endringerIRefusjon = listOf(
                Inntektsmelding.Refusjon.EndringIRefusjon(29000.månedlig, 20.januar),
                Inntektsmelding.Refusjon.EndringIRefusjon(28000.månedlig, 30.januar),
                Inntektsmelding.Refusjon.EndringIRefusjon(27000.månedlig, 20.februar)
            )),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)

        nyPeriode(15.februar til 28.februar)
        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            PersonObserver.Refusjon(forslag = listOf(
                Refusjonsopplysning(id, 30.januar, 19.februar, 28000.månedlig),
                Refusjonsopplysning(id, 20.februar, 1.april, 27000.månedlig),
                Refusjonsopplysning(id, 2.april, null, INGEN)
            )),
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `Sender med riktige refusjonsopplysninger ved opphør av refusjon før perioden`() {
        nyPeriode(januar)
        val id = håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 10.februar, endringerIRefusjon = emptyList()),
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)

        nyPeriode(15.februar til 28.februar)
        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(PersonObserver.Inntektsdata(1.januar, PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING, 31000.0)),
            PersonObserver.Refusjon(forslag = listOf(
                Refusjonsopplysning(id, 11.februar, null, INGEN)
            ))
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `Sender med tom liste med refusjonsopplysninger når vi mangler vilkårsgrunnlag på forrige skjæringstidspunkt`() {
        nyPeriode(januar)
        nyPeriode(15.februar til 28.februar)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList())
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }

    @Test
    fun `Sender trenger_ikke_opplysninger_fra_arbeidsgiver-event for out-of-order som er kant i kant`() {
        nyPeriode(februar)
        nyPeriode(januar)

       assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
       assertEquals(1, observatør.trengerIkkeArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Sender ikke trenger_ikke_opplysninger_fra_arbeidsgiver-event for out-of-order med gap`() {
        nyPeriode(februar)
        nyPeriode(1.januar til 30.januar)

        assertEquals(3, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) })
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a1) })
        assertEquals(0, observatør.trengerIkkeArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `Sender ikke trenger_ikke_opplysninger_fra_arbeidsgiver-event for out-of-order som ikke fører til en ny forespørsel`() {
        nyPeriode(februar)
        nyPeriode(25.januar til 31.januar)

        assertEquals(0, observatør.trengerIkkeArbeidsgiveropplysningerVedtaksperioder.size)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(2, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 1.vedtaksperiode.id(a1) })
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.count { it.vedtaksperiodeId == 2.vedtaksperiode.id(a1) })
    }

    @Test
    fun `Skal ikke sende ut forespørsler dersom vi er innenfor arbeidsgiverperioden`() {
        nyPeriode(1.januar til 2.januar)
        nyPeriode(3.januar til 6.januar)

        håndterInntektsmelding(emptyList(), begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(0, observatør.trengerIkkeArbeidsgiveropplysningerVedtaksperioder.size)
    }

    @Test
    fun `forlengelse av auu som slutter på en lørdag skal be om agp`() {
        nyPeriode(4.januar til 20.januar)
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        nyPeriode(21.januar til 31.januar)
        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedForespurteOpplysninger = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList()),
            PersonObserver.Arbeidsgiverperiode
        )
        val actualForespurteOpplysninger =
            observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last().forespurteOpplysninger
        assertEquals(expectedForespurteOpplysninger, actualForespurteOpplysninger)
    }


    @Test
    fun `Kort periode som blir lang pga korrigerende søknad med egenmeldingsdager skal sende ut forespørsel`() {
        nyPeriode(2.januar til 17.januar)
        håndterSøknad(Sykdom(2.januar, 17.januar, 100.prosent), egenmeldinger = listOf(1.januar til 1.januar))

        val expectedForespørsel = PersonObserver.TrengerArbeidsgiveropplysningerEvent(
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
            skjæringstidspunkt = 1.januar,
            sykmeldingsperioder = listOf(2.januar til 17.januar),
            egenmeldingsperioder = listOf(1.januar til 1.januar),
            førsteFraværsdager = listOf(PersonObserver.FørsteFraværsdag(a1, 1.januar)),
            forespurteOpplysninger = listOf(
                PersonObserver.Inntekt(null),
                PersonObserver.Refusjon(forslag = emptyList()),
                PersonObserver.Arbeidsgiverperiode
            )
        )

        assertEquals(1, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(expectedForespørsel, actualForespørsel)
    }

    @Test
    fun `Sender ikke med sykmeldingsperioder som er etter skjæringstidspunktet`() {
        nyPeriode(januar)
        nyPeriode(februar)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        val actualJanuarForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder[1]
        assertEquals(listOf(januar), actualJanuarForespørsel.sykmeldingsperioder)
    }

    @Test
    fun `Sender ikke med tidligere sykmeldingsperioder knyttet til vedtaksperiodens AGP når vi ikke spør om AGP i forespørsel`() {
        nyPeriode(1.januar til 17.januar)
        nyPeriode(20.januar til 31.januar)

        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder[1]
        assertEquals(listOf(20.januar til 31.januar), actualForespørsel.sykmeldingsperioder)
    }

    @Test
    fun `Skal ikke sende med egenmeldingsperioder når vi ikke ber om AGP`() {
        nyttVedtak(1.januar til 17.januar)
        håndterSykmelding(Sykmeldingsperiode(20.januar, 31.januar))
        håndterSøknad(
            Sykdom(20.januar, 31.januar, 100.prosent),
            egenmeldinger = listOf(19.januar til 19.januar)
        )
        håndterInntektsmeldingPortal(emptyList(), inntektsdato = 20.januar, førsteFraværsdag = 20.januar)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder[1]
        assertForventetFeil(
            forklaring = "Trenger ikke sende med egenmeldingsperioder når vi ikke ber om AGP, burde være sikker på at egenmeldinger ikke kommer fra søknad på perioden uten ny AGP",
            nå = {
                assertEquals(listOf(19.januar til 19.januar), actualForespørsel.egenmeldingsperioder)
            },
            ønsket = {
                assertEquals(emptyList<Periode>(), actualForespørsel.egenmeldingsperioder)
            }
        )
    }

    @Test
    fun `Skal ikke be om arbeidsgiverperiode når det er kort gap pga av arbeid gjenopptatt i slutten av perioden før en forlengelse`() {
        nyPeriode(januar, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 20.januar), orgnummer = a2)
        håndterSøknad(Sykdom(3.januar, 20.januar, 100.prosent), Søknad.Søknadsperiode.Arbeid(13.januar, 20.januar), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        nyPeriode(21.januar til 31.januar, orgnummer = a2)

        val expectedForespurteOpplysninger  = listOf(
            PersonObserver.FastsattInntekt(INNTEKT),
            PersonObserver.Refusjon(forslag = listOf(Refusjonsopplysning(im, 1.januar, null, INNTEKT)))
        )
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(expectedForespurteOpplysninger, actualForespørsel.forespurteOpplysninger)
    }

    @Test
    fun `dersom kort periode allerede har fått inntektsmelding trenger ikke neste periode å be om AGP`() {
        nyPeriode(1.januar til 16.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        nyPeriode(18.januar til 31.januar)

        val expectedForespurteOpplysninger  = listOf(
            PersonObserver.Inntekt(null),
            PersonObserver.Refusjon(forslag = emptyList())
        )
        val actualForespørsel = observatør.trengerArbeidsgiveropplysningerVedtaksperioder.last()
        assertEquals(expectedForespurteOpplysninger, actualForespørsel.forespurteOpplysninger)
    }


    private fun gapHosÉnArbeidsgiver(refusjon: Inntektsmelding.Refusjon) {
        nyPeriode(januar, a1)
        nyPeriode(januar, a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT_FLERE_AG,
            orgnummer = a1,
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT_FLERE_AG,
            refusjon = refusjon,
            orgnummer = a2,
        )

        fraVilkårsprøvingTilGodkjent(INNTEKT_FLERE_AG)

        forlengVedtak(februar, orgnummer = a1)
        nyPeriode(mars, a2)
    }

    private fun nyeVedtakMedUlikFom(
        sykefraværHosArbeidsgiver: Map<String, Periode>,
        inntekt: Inntekt = INNTEKT_FLERE_AG
    ) {
        val ag1Periode = sykefraværHosArbeidsgiver[a1]!!
        val ag2Periode = sykefraværHosArbeidsgiver[a2]!!
        nyPeriode(ag1Periode.start til ag1Periode.endInclusive, a1)
        nyPeriode(ag2Periode.start til ag2Periode.endInclusive, a2)

        val inntektsdato = sykefraværHosArbeidsgiver.values.minOf { it.start }
        håndterInntektsmeldingPortal(
            listOf(ag1Periode.start til ag1Periode.start.plusDays(15)),
            beregnetInntekt = inntekt,
            inntektsdato = inntektsdato,
            orgnummer = a1,
        )
        håndterInntektsmeldingPortal(
            listOf(ag2Periode.start til ag2Periode.start.plusDays(15)),
            beregnetInntekt = inntekt,
            inntektsdato = inntektsdato,
            orgnummer = a2,
        )

        fraVilkårsprøvingTilGodkjent(inntekt)
    }

    private fun fraVilkårsprøvingTilGodkjent(inntekt: Inntekt) {
        val sykepengegrunnlag = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), inntekt.repeat(3))
        )

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(sykepengegrunnlag, emptyList()),
            arbeidsforhold = arbeidsforhold
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)
    }
}