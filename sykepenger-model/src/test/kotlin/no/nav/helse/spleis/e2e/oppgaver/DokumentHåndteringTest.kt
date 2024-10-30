package no.nav.helse.spleis.e2e.oppgaver

import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_8
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.forkastAlle
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DokumentHåndteringTest : AbstractEndToEndTest() {

    @Test
    fun `sender ut inntektsmelding håndtert også når inntektsmelding kommer før søknad og dagene håndteres av en tidligere periode`() {
        nyttVedtak(januar)
        observatør.inntektsmeldingHåndtert.clear()
        val inntektsmelding = håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar til 16.januar),
            førsteFraværsdag = 10.februar,
            avsendersystem = Inntektsmelding.Avsendersystem.ALTINN
        )
        val søknad = håndterSøknad(Sykdom(10.februar, 28.februar, 100.prosent))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        assertEquals(inntektsmelding to 2.vedtaksperiode.id(ORGNUMMER), observatør.inntektsmeldingHåndtert.single())
        assertEquals(setOf(søknad, inntektsmelding), inspektør.hendelseIder(2.vedtaksperiode))
    }

    @Test
    fun `Inntektsmelding kommer mellom AUU og søknad for førstegangsbehandling`() {
        val søknad1 = håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        val inntektsmelding = håndterInntektsmelding(listOf(1.januar til 16.januar))
        val søknad2 = håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent))

        håndterVilkårsgrunnlag(2.vedtaksperiode)

        assertEquals(setOf(søknad1, inntektsmelding), inspektør.hendelseIder(1.vedtaksperiode))
        assertEquals(setOf(søknad2, inntektsmelding), inspektør.hendelseIder(2.vedtaksperiode))
        assertEquals(2.vedtaksperiode.id(ORGNUMMER), observatør.inntektsmeldingHåndtert.single().second)
    }

    @Test
    fun `Inntektsmelding kommer mellom AUU og søknad for førstegangsbehandling flere arbeidsgivere`() {
        val søknad1A1 = håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent), orgnummer = a1)
        val søknad1A2 = håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent), orgnummer = a2)

        val inntektsmeldingA1 = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1,)
        val inntektsmeldingA2 = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2,)

        val søknad2A1 = håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent), orgnummer = a1)
        val søknad2A2 = håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterVilkårsgrunnlag(2.vedtaksperiode, orgnummer = a1)

        assertEquals(setOf(søknad1A1, inntektsmeldingA1), inspektør(a1).hendelseIder(1.vedtaksperiode))
        assertEquals(setOf(søknad1A2, inntektsmeldingA2), inspektør(a2).hendelseIder(1.vedtaksperiode))

        assertEquals(setOf(søknad2A1, inntektsmeldingA1), inspektør(a1).hendelseIder(2.vedtaksperiode))
        assertEquals(setOf(søknad2A2, inntektsmeldingA2), inspektør(a2).hendelseIder(2.vedtaksperiode))
    }

    @Test
    fun `to helt like korrigerende inntektsmeldinger`() {
        nyttVedtak(januar, 100.prosent, beregnetInntekt = INNTEKT)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT * 1.1,)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        nullstillTilstandsendringer()
        observatør.inntektsmeldingIkkeHåndtert.clear()
        observatør.inntektsmeldingHåndtert.clear()
        val korrigertInntektsmelding2 = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT * 1.1,
        )
        assertEquals(listOf(korrigertInntektsmelding2), observatør.inntektsmeldingHåndtert.map { it.first })
        assertEquals(emptyList<UUID>(), observatør.inntektsmeldingIkkeHåndtert)
    }

    @Test
    fun `sender ikke ut signal om at inntektsmelding ikke er håndtert om annen vedtaksperiode har håndtert inntektsmelding før`() {
        nyttVedtak(januar)
        håndterSøknad(Sykdom(20.februar, 20.mars, 100.prosent))
        assertEquals(emptyList<UUID>(),  observatør.inntektsmeldingIkkeHåndtert)
    }

    @Test
    fun `Inntektsmelding før søknad`() {
        håndterSykmelding(januar)
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar),)
        val inntektsmeldingFørSøknadEvent = observatør.inntektsmeldingFørSøknad.single()
        inntektsmeldingFørSøknadEvent.let {
            assertEquals(id, it.inntektsmeldingId)
            assertEquals(listOf(1.januar til 16.januar), it.relevanteSykmeldingsperioder)
        }
    }

    @Test
    fun `Inntektsmelding før søknad med kort gap`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(20.januar, 31.januar))
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 20.januar)
        val inntektsmeldingFørSøknadEvent = observatør.inntektsmeldingFørSøknad.single()
        inntektsmeldingFørSøknadEvent.let {
            assertEquals(id, it.inntektsmeldingId)
            assertEquals(listOf(20.januar til 20.januar), it.relevanteSykmeldingsperioder)
        }
    }

    @Test
    fun `Inntektsmelding før søknad, men vedtaksperioden er forkastet`() {
        håndterSykmelding(januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), utenlandskSykmelding = true)
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar))
        val inntektsmelding = observatør.inntektsmeldingIkkeHåndtert.single()
        assertEquals(id, inntektsmelding)
    }

    @Test
    fun `Inntektsmelding før forlengelse-søknad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(17.januar, 31.januar))
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        val inntektsmeldingFørSøknadEvent = observatør.inntektsmeldingFørSøknad.single()
        inntektsmeldingFørSøknadEvent.let {
            assertEquals(id, it.inntektsmeldingId)
            assertEquals(listOf(17.januar til 31.januar), it.relevanteSykmeldingsperioder)
        }
    }

    @Test
    fun `Inntektsmelding før forlengelse-søknad - auu er litt lang`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(17.januar, 31.januar))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        val inntektsmeldingFørSøknadEvent = observatør.inntektsmeldingFørSøknad.single()
        inntektsmeldingFørSøknadEvent.let {
            assertEquals(id, it.inntektsmeldingId)
            assertEquals(listOf(februar), it.relevanteSykmeldingsperioder)
        }
    }

    @Test
    fun `Inntektsmelding ikke håndtert`() {
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar),)
        val inntektsmelding = observatør.inntektsmeldingIkkeHåndtert.single()
        assertEquals(id, inntektsmelding)
    }

    @Test
    fun `Inntektsmelding ikke håndtert - lang periode mellom auu og sykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        val inntektsmelding = observatør.inntektsmeldingIkkeHåndtert.single()
        assertEquals(id, inntektsmelding)
    }

    @Test
    fun `Inntektsmelding bare håndtert inntekt`() {
        håndterSøknad(januar)
        val im1 = håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        val hendelserHåndtertFør = inspektør.hendelser(1.vedtaksperiode)
        assertEquals(listOf(
            im1 to 1.vedtaksperiode.id(ORGNUMMER), // pga inntekt
        ), observatør.inntektsmeldingHåndtert)
        assertEquals(emptyList<UUID>(), observatør.inntektsmeldingIkkeHåndtert)
        val søknad = håndterSøknad(Sykdom(10.februar, 28.februar, 100.prosent))
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 10.februar,)
        assertEquals(emptyList<UUID>(), observatør.inntektsmeldingIkkeHåndtert)
        assertEquals(hendelserHåndtertFør, inspektør.hendelser(1.vedtaksperiode))
        assertEquals(setOf(
            Dokumentsporing.søknad(søknad),
            Dokumentsporing.inntektsmeldingDager(im),
            Dokumentsporing.inntektsmeldingRefusjon(im),
            Dokumentsporing.inntektsmeldingInntekt(im)
        ), inspektør.hendelser(2.vedtaksperiode))
        assertEquals(2, observatør.inntektsmeldingHåndtert.size)
        assertEquals(im to 2.vedtaksperiode.id(ORGNUMMER), observatør.inntektsmeldingHåndtert.last())
    }

    @Test
    fun `Inntektsmelding noen dager håndtert`() {
        val søknad = håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar),)
        assertEquals(listOf(im), observatør.inntektsmeldingIkkeHåndtert)
        assertEquals(listOf(søknad to 1.vedtaksperiode.id(ORGNUMMER)), observatør.søknadHåndtert)
        assertEquals(emptyList<Any>(), observatør.inntektsmeldingHåndtert)
    }
    @Test
    fun `Inntektsmelding noen dager håndtert - IM før søknad`() {
        val søknad = håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(11.januar, 20.januar))
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar),)
        assertEquals(emptyList<UUID>(), observatør.inntektsmeldingIkkeHåndtert)
        assertEquals(listOf(im), observatør.inntektsmeldingFørSøknad.map { it.inntektsmeldingId })
        assertEquals(listOf(søknad to 1.vedtaksperiode.id(ORGNUMMER)), observatør.søknadHåndtert)
        assertEquals(emptyList<Any>(), observatør.inntektsmeldingHåndtert)
    }

    @Test
    fun `Inntektsmelding håndteres av flere`() {
        val søknad1 = håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
        val søknad2 = håndterSøknad(Sykdom(11.januar, 16.januar, 100.prosent))
        val søknad3 = håndterSøknad(Sykdom(17.januar, 20.januar, 100.prosent))
        val søknad4 = håndterSøknad(Sykdom(21.januar, 26.januar, 100.prosent))
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar),)

        assertEquals(setOf(
            Dokumentsporing.søknad(søknad1),
            Dokumentsporing.inntektsmeldingDager(im),
            Dokumentsporing.inntektsmeldingRefusjon(im)
        ), inspektør.hendelser(1.vedtaksperiode))
        assertEquals(setOf(
            Dokumentsporing.søknad(søknad2),
            Dokumentsporing.inntektsmeldingDager(im),
            Dokumentsporing.inntektsmeldingRefusjon(im)
        ), inspektør.hendelser(2.vedtaksperiode))
        assertEquals(setOf(
            Dokumentsporing.søknad(søknad3),
            Dokumentsporing.inntektsmeldingRefusjon(im),
            Dokumentsporing.inntektsmeldingInntekt(im)
        ), inspektør.hendelser(3.vedtaksperiode))
        assertEquals(setOf(
            Dokumentsporing.søknad(søknad4),
            Dokumentsporing.inntektsmeldingRefusjon(im),
            Dokumentsporing.inntektsmeldingInntekt(im)
        ), inspektør.hendelser(4.vedtaksperiode))

        assertEquals(emptyList<UUID>(), observatør.inntektsmeldingIkkeHåndtert)
        assertEquals(listOf(
            søknad1 to 1.vedtaksperiode.id(ORGNUMMER),
            søknad2 to 2.vedtaksperiode.id(ORGNUMMER),
            søknad3 to 3.vedtaksperiode.id(ORGNUMMER),
            søknad4 to 4.vedtaksperiode.id(ORGNUMMER)
        ), observatør.søknadHåndtert)
        assertEquals(listOf(
            im to 3.vedtaksperiode.id(ORGNUMMER), // inntekt
            im to 4.vedtaksperiode.id(ORGNUMMER) // inntekt
        ), observatør.inntektsmeldingHåndtert)
    }

    @Test
    fun `har overlappende avslutta vedtaksperiode på annen arbeidsgiver`() {
        nyttVedtak(januar, orgnummer = a2)
        val søknad2 = håndterSøknad(Sykdom(28.januar, 28.februar, 100.prosent), utenlandskSykmelding = true)
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = START,
                hendelser = setOf(søknad2),
                fom = 28.januar,
                tom = 28.februar,
                behandletIInfotrygd = false,
                forlengerPeriode = true,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = true,
                sykmeldingsperioder = listOf(28.januar til 28.februar)
            ), observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER))
        )
    }

    @Test
    fun `har periode rett før men det er en AUU`() {
        nyPeriode(1.januar til 16.januar)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        val søknad2 = håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent))
        val im = håndterInntektsmelding(
            listOf(10.januar til 26.januar),
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FiskerMedHyre",
        )
        assertFunksjonellFeil(RV_IM_8)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = AVVENTER_INNTEKTSMELDING,
                hendelser = setOf(søknad2),
                fom = 17.januar,
                tom = 31.januar,
                behandletIInfotrygd = false,
                forlengerPeriode = false,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = true,
                sykmeldingsperioder = listOf(1.januar til 16.januar, 17.januar til 31.januar)
            ), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER))
        )
        assertTrue(im in observatør.inntektsmeldingIkkeHåndtert)
    }

    @Test
    fun `har en periode rett før på annen arbeidsgiver`() {
        nyttVedtak(januar, orgnummer = a2)
        val søknad2 = håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), utenlandskSykmelding = true)
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = START,
                hendelser = setOf(søknad2),
                fom = 1.februar,
                tom = 28.februar,
                behandletIInfotrygd = false,
                forlengerPeriode = true,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = true,
                sykmeldingsperioder = listOf(februar)
            ), observatør.forkastet(1.vedtaksperiode.id(ORGNUMMER))
        )
    }

    @Test
    fun `har ikke overlappende vedtaksperioder`() {
        tilGodkjenning(januar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        val søknad2 = håndterSøknad(Sykdom(28.januar, 28.februar, 100.prosent))
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = START,
                hendelser = setOf(søknad2),
                fom = 28.januar,
                tom = 28.februar,
                behandletIInfotrygd = false,
                forlengerPeriode = false,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = false,
                sykmeldingsperioder = listOf(januar, 28.januar til 28.februar)
            ), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)))
    }
    @Test
    fun `har vedtaksperiode som påvirker arbeidsgiverperioden`() {
        tilGodkjenning(januar, ORGNUMMER)

        val søknad2 = håndterSøknad(Sykdom(10.februar, 28.februar, 100.prosent))
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = AVVENTER_INNTEKTSMELDING,
                hendelser = setOf(søknad2),
                fom = 10.februar,
                tom = 28.februar,
                behandletIInfotrygd = false,
                forlengerPeriode = false,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = true,
                sykmeldingsperioder = listOf(januar, 10.februar til 28.februar)
            ), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)))
    }

    @Test
    fun `har ikke overlappende vedtaksperiode`() {
        tilGodkjenning(januar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        val søknad2 = håndterSøknad(Sykdom(15.februar, 28.februar, 100.prosent))
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = START,
                hendelser = setOf(søknad2),
                fom = 15.februar,
                tom = 28.februar,
                behandletIInfotrygd = false,
                forlengerPeriode = false,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = true,
                sykmeldingsperioder = listOf(januar, 15.februar til 28.februar)
            ), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)))
    }

    @Test
    fun `delvis overlappende søknad`() {
        val søknad1 = håndterSøknad(Sykdom(11.januar, 16.januar, 100.prosent))
        val søknad2 = håndterSøknad(Sykdom(10.januar, 15.januar, 100.prosent))
        assertEquals(emptyList<UUID>(), observatør.inntektsmeldingIkkeHåndtert)
        assertEquals(listOf(
            søknad1 to 1.vedtaksperiode.id(ORGNUMMER),
            søknad2 to 1.vedtaksperiode.id(ORGNUMMER)
        ), observatør.søknadHåndtert)
        assertEquals(
            PersonObserver.VedtaksperiodeForkastetEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = ORGNUMMER,
                vedtaksperiodeId = 2.vedtaksperiode.id(ORGNUMMER),
                gjeldendeTilstand = START,
                hendelser = setOf(søknad2),
                fom = 10.januar,
                tom = 15.januar,
                behandletIInfotrygd = false,
                forlengerPeriode = true,
                harPeriodeInnenfor16Dager = false,
                trengerArbeidsgiveropplysninger = false,
                sykmeldingsperioder = listOf(11.januar til 16.januar, 10.januar til 15.januar)
            ), observatør.forkastet(2.vedtaksperiode.id(ORGNUMMER)))
    }

    @Test
    fun `sender ut søknad håndtert for forlengelse av forkastet periode`(){
        håndterSykmelding(januar)
        val søknadId1 = håndterSøknad(januar)
        forkastAlle()

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        val søknadId2 = håndterSøknad(februar)
        assertEquals(listOf(
            søknadId1 to 1.vedtaksperiode.id(ORGNUMMER),
            søknadId2 to 2.vedtaksperiode.id(ORGNUMMER)
        ), observatør.søknadHåndtert)
    }

    @Test
    fun `sender ut inntektsmelding ikke håndtert på im med funksjonelle feil ved revurdering av dager`() {
        nyttVedtak(januar)
        val inntektsmeldingId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            harOpphørAvNaturalytelser = true,
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertFalse(inntektsmeldingId in observatør.inntektsmeldingIkkeHåndtert)
        assertTrue(inntektsmeldingId in observatør.inntektsmeldingHåndtert.map { it.first })
    }

    @Test
    fun `inntektsmelding med første fraværsdag utenfor sykdom - ett tidligere vedtak - inntektsmelding ikke håndtert fordi inntekt håndteres ikke`() {
        val im1 = UUID.randomUUID()
        nyttVedtak(januar, inntektsmeldingId = im1)
        val im2 = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.februar,
            refusjon = Inntektsmelding.Refusjon(Inntekt.INGEN, null),
            avsendersystem = Inntektsmelding.Avsendersystem.ALTINN
        )
        håndterSøknad(februar)
        assertFalse(im2 in observatør.inntektsmeldingHåndtert.map(Pair<UUID,*>::first))
        assertTrue(im2 in observatør.inntektsmeldingIkkeHåndtert)
    }

    @Test
    fun `inntektsmelding med første fraværsdag utenfor sykdom - ingen tidligere vedtak - inntektsmelding ikke håndtert fordi inntekt håndteres ikke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        val im = håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), 27.januar, avsendersystem = Inntektsmelding.Avsendersystem.ALTINN)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
        assertFalse(im in observatør.inntektsmeldingHåndtert.map(Pair<UUID,*>::first))
        assertTrue(im in observatør.inntektsmeldingIkkeHåndtert)
    }

    @Test
    fun `inntektsmelding med første fraværsdag utenfor sykdom - ingen tidligere vedtak - IM før søknad - inntektsmelding ikke håndtert fordi inntekt håndteres ikke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)), 27.januar)
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `Skal legge til hendelsesid for korrigerende inntektsmelding på alle vedtaksperioder den treffer`() {
        nyPeriode(1.januar til 10.januar)
        val im1 = håndterInntektsmelding(listOf(1.januar til 16.januar),)
        nyPeriode(11.januar til 31.januar)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        val im2 = håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT*1.1,)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        assertFalse(inspektør.hendelser(1.vedtaksperiode).contains(Dokumentsporing.inntektsmeldingInntekt(im1)))
        assertFalse(inspektør.hendelser(1.vedtaksperiode).contains(Dokumentsporing.inntektsmeldingInntekt(im2)))
    }
}
