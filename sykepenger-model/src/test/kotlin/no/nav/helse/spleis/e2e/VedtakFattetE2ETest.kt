package no.nav.helse.spleis.e2e

import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.AvsluttetMedVedtakEvent.FastsattIInfotrygd
import no.nav.helse.person.PersonObserver.AvsluttetMedVedtakEvent.FastsattISpeil
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VedtakFattetE2ETest : AbstractEndToEndTest() {

    @Test
    fun `sender ikke vedtak fattet for perioder innenfor arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(0, inspektør.utbetalinger.size)
        assertEquals(0, observatør.utbetalingUtenUtbetalingEventer.size)
        assertEquals(0, observatør.utbetalingMedUtbetalingEventer.size)
        1.vedtaksperiode.assertIngenVedtakFattet()
        assertEquals(1.januar til 10.januar, 1.vedtaksperiode.avsluttetUtenVedtakEventer.single().periode)
    }

    @Test
    fun `sender vedtak fattet for perioder utenfor arbeidsgiverperioden`() {
        nyttVedtak(1.januar til 31.januar, 100.prosent)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertEquals(1, inspektør.utbetalinger.size)
        assertEquals(0, observatør.utbetalingUtenUtbetalingEventer.size)
        assertEquals(1, observatør.utbetalingMedUtbetalingEventer.size)
        assertEquals(1, observatør.avsluttetMedVedtakEvent.size)
        val event = observatør.avsluttetMedVedtakEvent.getValue(1.vedtaksperiode.id(ORGNUMMER))
        assertEquals(inspektør.utbetaling(0).inspektør.utbetalingId, event.utbetalingId)
        assertEquals(Utbetalingstatus.UTBETALT, inspektør.utbetaling(0).inspektør.tilstand)
        val forventetSykepengegrunnlagsfakta = FastsattISpeil(
            omregnetÅrsinntekt = 372000.0,
            `6G` = 561804.0,
            arbeidsgivere = listOf(FastsattISpeil.Arbeidsgiver(a1, 372000.0, null))
        )
        assertEquals(forventetSykepengegrunnlagsfakta, event.sykepengegrunnlagsfakta)
    }

    @Test
    fun `sender vedtak fattet for perioder utenfor arbeidsgiverperioden med bare ferie`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), Ferie(17.januar, 20.januar))
        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertEquals(0, inspektør.utbetalinger.size)
        assertEquals(0, observatør.utbetalingUtenUtbetalingEventer.size)
        assertEquals(0, observatør.utbetalingMedUtbetalingEventer.size)
        1.vedtaksperiode.assertIngenVedtakFattet()
        assertEquals(2, 1.vedtaksperiode.avsluttetUtenVedtakEventer.size)
        assertEquals(setOf(søknadId), 1.vedtaksperiode.avsluttetUtenVedtakEventer.first().hendelseIder)
        assertEquals(setOf(søknadId, inntektsmeldingId),1.vedtaksperiode.avsluttetUtenVedtakEventer.last().hendelseIder)
    }

    @Test
    fun `sender vedtak fattet for forlengelseperioder utenfor arbeidsgiverperioden med bare ferie`() {
        nyttVedtak(1.januar til 20.januar, 100.prosent)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar))
        håndterSøknad(Sykdom(21.januar, 31.januar, 100.prosent), Ferie(21.januar, 31.januar))
        håndterYtelser(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        assertEquals(2, inspektør.utbetalinger.size)
        assertEquals(1, observatør.utbetalingUtenUtbetalingEventer.size)
        assertEquals(1, observatør.utbetalingMedUtbetalingEventer.size)
        assertEquals(2, observatør.avsluttetMedVedtakEvent.size)
        val event = observatør.avsluttetMedVedtakEvent.getValue(2.vedtaksperiode.id(ORGNUMMER))
        assertEquals(inspektør.utbetaling(1).inspektør.utbetalingId, event.utbetalingId)
        assertEquals(Utbetalingstatus.GODKJENT_UTEN_UTBETALING, inspektør.utbetaling(1).inspektør.tilstand)
        val forventetSykepengegrunnlagsfakta = FastsattISpeil(
            omregnetÅrsinntekt = 372000.0,
            `6G` = 561804.0,
            arbeidsgivere = listOf(FastsattISpeil.Arbeidsgiver(a1, 372000.0, null))
        )
        assertEquals(forventetSykepengegrunnlagsfakta, event.sykepengegrunnlagsfakta)
    }

    @Test
    fun `sender vedtak fattet ved fastsettelse etter hovedregel med flere arbeidsgivere`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020)), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020)), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar(2020) til 16.januar(2020)), beregnetInntekt = INNTEKT, orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar(2020) til 16.januar(2020)), beregnetInntekt = INNTEKT, orgnummer = a2)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertEquals(2, observatør.avsluttetMedVedtakEvent.size)
        val a1Sykepengegrunnlagsfakta = observatør.avsluttetMedVedtakEvent.values.first { it.organisasjonsnummer == a1 }.sykepengegrunnlagsfakta
        val a2Sykepengegrunnlagsfakta = observatør.avsluttetMedVedtakEvent.values.first { it.organisasjonsnummer == a2 }.sykepengegrunnlagsfakta
        assertEquals(a1Sykepengegrunnlagsfakta, a2Sykepengegrunnlagsfakta)

        val forventetSykepengegrunnlagsfakta = FastsattISpeil(
            omregnetÅrsinntekt = 744000.0,
            `6G` = 599148.0,
            arbeidsgivere = listOf(
                FastsattISpeil.Arbeidsgiver(a1, 372000.0, null),
                FastsattISpeil.Arbeidsgiver(a2, 372000.0, null),
            )
        )
        assertEquals(forventetSykepengegrunnlagsfakta, a1Sykepengegrunnlagsfakta)
    }

    @Test
    fun `sender vedtak fattet etter skjønnsmessig fastsettelse med flere arbeidsgivere`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020)), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020)), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.januar(2020) til 16.januar(2020)),
            beregnetInntekt = 45000.månedlig,
            orgnummer = a1,
        )
        håndterInntektsmelding(
            listOf(1.januar(2020) til 16.januar(2020)),
            beregnetInntekt = 44000.månedlig,
            orgnummer = a2,
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
        håndterSkjønnsmessigFastsettelse(1.januar(2020), listOf(OverstyrtArbeidsgiveropplysning(a1, 46000.månedlig), OverstyrtArbeidsgiveropplysning(a2, 45000.månedlig)))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertEquals(2, observatør.avsluttetMedVedtakEvent.size)
        val a1Sykepengegrunnlagsfakta = observatør.avsluttetMedVedtakEvent.values.first { it.organisasjonsnummer == a1 }.sykepengegrunnlagsfakta
        val a2Sykepengegrunnlagsfakta = observatør.avsluttetMedVedtakEvent.values.first { it.organisasjonsnummer == a2 }.sykepengegrunnlagsfakta
        assertEquals(a1Sykepengegrunnlagsfakta, a2Sykepengegrunnlagsfakta)

        val forventetSykepengegrunnlagsfakta = FastsattISpeil(
            omregnetÅrsinntekt = 1068000.0,
            `6G` = 599148.0,
            arbeidsgivere = listOf(
                FastsattISpeil.Arbeidsgiver(a1, 540000.0, 552000.0),
                FastsattISpeil.Arbeidsgiver(a2, 528000.0, 540000.0),
            )
        )
        assertEquals(forventetSykepengegrunnlagsfakta, a1Sykepengegrunnlagsfakta)
    }

    @Test
    fun `sender vedtak fattet med sykepengegrunnlag fastsatt i Infotrygd`() {
        createOvergangFraInfotrygdPerson()
        forlengVedtak(1.februar til 28.februar)
        assertEquals(1, observatør.avsluttetMedVedtakEvent.size)
        val event = observatør.avsluttetMedVedtakEvent.values.single()
        val forventetSykepengegrunnlagsfakta = FastsattIInfotrygd(372000.0)
        assertEquals(forventetSykepengegrunnlagsfakta, event.sykepengegrunnlagsfakta)
    }

    @Test
    fun `sender avsluttet uten vedtak når saksbehandler overstyrer perioden inn i AvsluttetUtenUtbetaling`(){
        val søknadId = håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        val inntektsmeldingId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(beløp = INNTEKT, null, emptyList()),
            begrunnelseForReduksjonEllerIkkeUtbetalt = "noe",
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        val liste = (1..16).map{
            ManuellOverskrivingDag(it.januar, Dagtype.Feriedag)
        }
        val overstyringId = UUID.randomUUID()
        håndterOverstyrTidslinje(liste, meldingsreferanseId = overstyringId)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        val utbetaling = inspektør.utbetalinger.single()
        assertEquals(Utbetalingstatus.FORKASTET, utbetaling.inspektør.tilstand)
        1.vedtaksperiode.assertIngenVedtakFattet()
        assertEquals(2, 1.vedtaksperiode.avsluttetUtenVedtakEventer.size)
        assertEquals(setOf(søknadId), 1.vedtaksperiode.avsluttetUtenVedtakEventer.first().hendelseIder)
        assertEquals(setOf(søknadId, inntektsmeldingId, overstyringId),1.vedtaksperiode.avsluttetUtenVedtakEventer.last().hendelseIder)
    }


    @Test
    fun `Sender avsluttet uten vedtak ved kort gap til periode med kun ferie`() {
        nyttVedtak(1.januar til 31.januar)
        håndterSykmelding(Sykmeldingsperiode(10.februar, 28.februar))
        håndterSøknad(Sykdom(10.februar, 28.februar, 100.prosent), Ferie(10.februar, 28.februar))
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        2.vedtaksperiode.assertIngenVedtakFattet()
        assertEquals(10.februar til 28.februar, 2.vedtaksperiode.avsluttetUtenVedtakEventer.single().periode)
    }

    @Test
    fun `Periode med kun ferie etter kort gap etter kort auu tagges ikke med IngenNyArbeidsgiverperiode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(15.januar, 31.januar))
        håndterSøknad(Sykdom(15.januar, 31.januar, 100.prosent), Ferie(15.januar, 31.januar))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        assertEquals(1, 1.vedtaksperiode.avsluttetUtenVedtakEventer.size)
        assertEquals(1, 2.vedtaksperiode.avsluttetUtenVedtakEventer.size)
        1.vedtaksperiode.assertIngenVedtakFattet()
        2.vedtaksperiode.assertIngenVedtakFattet()
    }

    @Test
    fun `sender med tidligere dokumenter etter revurdering`() {
        nyttVedtak(1.januar til 31.januar)
        val overstyringId = UUID.randomUUID()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)), meldingsreferanseId = overstyringId)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        assertEquals(1, observatør.avsluttetMedVedtakEventer.getValue(1.vedtaksperiode.id(ORGNUMMER)).size)
        val tidligereVedtak = observatør.avsluttetMedVedtakEvent.getValue(1.vedtaksperiode.id(ORGNUMMER))
        håndterUtbetalt()
        assertEquals(2, observatør.avsluttetMedVedtakEventer.getValue(1.vedtaksperiode.id(ORGNUMMER)).size)
        val nyttVedtak = observatør.avsluttetMedVedtakEvent.getValue(1.vedtaksperiode.id(ORGNUMMER))
        assertEquals(tidligereVedtak.hendelseIder.plus(overstyringId), nyttVedtak.hendelseIder)
    }

    private val IdInnhenter.vedtakFattetEvent get() = observatør.avsluttetMedVedtakEvent.values.single {
        it.vedtaksperiodeId == id(ORGNUMMER)
    }
    private val IdInnhenter.avsluttetUtenVedtakEventer get() = observatør.avsluttetUtenVedtakEventer.getValue(id(ORGNUMMER))
    private fun IdInnhenter.assertIngenVedtakFattet() = assertEquals(emptyList<PersonObserver.AvsluttetMedVedtakEvent>(), observatør.avsluttetMedVedtakEventer[id(ORGNUMMER)] ?: emptyList<PersonObserver.AvsluttetMedVedtakEvent>())
}
