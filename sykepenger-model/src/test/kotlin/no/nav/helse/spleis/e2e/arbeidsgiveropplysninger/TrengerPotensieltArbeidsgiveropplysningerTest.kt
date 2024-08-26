package no.nav.helse.spleis.e2e.arbeidsgiveropplysninger

import no.nav.helse.Toggle
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TrengerPotensieltArbeidsgiveropplysningerTest : AbstractEndToEndTest() {

    @Test
    fun `sender ut event TrengerPotensieltArbeidsgiveropplysninger når vi ankommer AvsluttetUtenUtbetaling`() {
        nyPeriode(1.januar til 16.januar)
        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(1, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedPotensiellForespørsel = PersonObserver.TrengerPotensieltArbeidsgiveropplysningerEvent(
            ORGNUMMER,
            inspektør.vedtaksperiodeId(1.vedtaksperiode),
            1.januar,
            sykmeldingsperioder = listOf(1.januar til 16.januar),
            egenmeldingsperioder = emptyList(),
            førsteFraværsdager = listOf(PersonObserver.FørsteFraværsdag(ORGNUMMER, 1.januar))
        )

        assertEquals(
            expectedPotensiellForespørsel,
            observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.single()
        )
    }

    @Test
    fun `sender sykmeldingsperioder som er knyttet til samme AGP, men ikke etter gjeldende forespørsel`() {
        nyPeriode(1.januar til 1.januar)
        nyPeriode(1.februar til 2.februar)
        nyPeriode(3.februar til 3.februar)
        nyPeriode(9.februar til 9.februar)
        nyPeriode(7.februar til 7.februar)

        assertTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstand(3.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstand(4.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstand(5.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(6, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.size)

        val expectedPotensiellForespørsel1 = PersonObserver.TrengerPotensieltArbeidsgiveropplysningerEvent(
            ORGNUMMER,
            inspektør.vedtaksperiodeId(4.vedtaksperiode),
            9.februar,
            sykmeldingsperioder = listOf(
                1.februar til 2.februar,
                3.februar til 3.februar,
                9.februar til 9.februar
            ),
            egenmeldingsperioder = emptyList(),
            førsteFraværsdager = listOf(PersonObserver.FørsteFraværsdag(ORGNUMMER, 9.februar))
        )

        assertEquals(expectedPotensiellForespørsel1, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[3])

        val expectedPotensiellForespørsel2 = PersonObserver.TrengerPotensieltArbeidsgiveropplysningerEvent(
            organisasjonsnummer = ORGNUMMER,
            vedtaksperiodeId = inspektør.vedtaksperiodeId(5.vedtaksperiode),
            skjæringstidspunkt = 7.februar,
            sykmeldingsperioder = listOf(
                1.februar til 2.februar,
                3.februar til 3.februar,
                7.februar til 7.februar
            ),
            egenmeldingsperioder = emptyList(),
            førsteFraværsdager = listOf(PersonObserver.FørsteFraværsdag(ORGNUMMER, 7.februar))
        )

        assertEquals(expectedPotensiellForespørsel2, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[4])

        val expectedPotensiellForespørsel3 = PersonObserver.TrengerPotensieltArbeidsgiveropplysningerEvent(
            ORGNUMMER,
            inspektør.vedtaksperiodeId(4.vedtaksperiode),
            9.februar,
            sykmeldingsperioder = listOf(
                1.februar til 2.februar,
                3.februar til 3.februar,
                7.februar til 7.februar,
                9.februar til 9.februar
            ),
            egenmeldingsperioder = emptyList(),
            førsteFraværsdager = listOf(PersonObserver.FørsteFraværsdag(ORGNUMMER, 9.februar))
        )

        assertEquals(expectedPotensiellForespørsel3, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[5])
    }

    @Test
    fun `sender egenmeldingsperioder fra søknader som er knyttet til samme AGP`() {
        håndterSykmelding(Sykmeldingsperiode(5.januar, 16.januar))
        håndterSøknad(
            Sykdom(5.januar, 16.januar, 100.prosent),
            egenmeldinger = listOf(1.januar til 4.januar)
        )

        // Langt gap fra forrige periode
        håndterSykmelding(Sykmeldingsperiode(5.mars, 8.mars))
        håndterSøknad(
            Sykdom(5.mars, 8.mars, 100.prosent),
            egenmeldinger = listOf(1.mars til 2.mars)
        )

        // Kort gap fra forrige periode
        håndterSykmelding(Sykmeldingsperiode(14.mars, 16.mars))
        håndterSøknad(
            Sykdom(14.mars, 16.mars, 100.prosent),
            egenmeldinger = listOf(13.mars til 13.mars)
        )

        assertEquals(0, observatør.trengerArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(3, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.size)

        assertEquals(listOf(1.januar til 4.januar), observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[0].egenmeldingsperioder)
        assertEquals(listOf(1.mars til 2.mars), observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[1].egenmeldingsperioder)

        val expectedPotensiellForespørsel = PersonObserver.TrengerPotensieltArbeidsgiveropplysningerEvent(
            ORGNUMMER,
            inspektør.vedtaksperiodeId(3.vedtaksperiode),
            13.mars,
            sykmeldingsperioder = listOf(5.mars til 8.mars, 14.mars til 16.mars),
            egenmeldingsperioder = listOf(1.mars til 2.mars, 13.mars til 13.mars),
            førsteFraværsdager = listOf(PersonObserver.FørsteFraværsdag(ORGNUMMER, 13.mars))
        )
        assertEquals(expectedPotensiellForespørsel, observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.last())
    }

    @Test
    fun `Skal ikke sende med egenmeldingsdager etter vedtaksperioden sin potensielle forespørsel`() = Toggle.EgenmeldingStrekkerIkkeSykdomstidslinje.enable {
        håndterSykmelding(Sykmeldingsperiode(10.januar, 16.januar))
        håndterSøknad(Sykdom(10.januar, 16.januar, 100.prosent), egenmeldinger = listOf(9.januar til 9.januar))
        håndterSykmelding(Sykmeldingsperiode(2.januar, 5.januar))
        håndterSøknad(Sykdom(2.januar, 5.januar, 100.prosent), egenmeldinger = listOf(1.januar til 1.januar))


        val trengerPotensieltArbeidsgiveropplysningerVedtaksperioder = observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder
        assertEquals(3, trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.size)
        assertEquals(listOf(9.januar til 9.januar), trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[0].egenmeldingsperioder)
        assertEquals(listOf(1.januar til 1.januar), trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[1].egenmeldingsperioder)
        assertEquals(listOf(1.januar til 1.januar, 9.januar til 9.januar), trengerPotensieltArbeidsgiveropplysningerVedtaksperioder[2].egenmeldingsperioder)
    }

    @Test
    fun `Skal sende med første fraværsdager for alle arbeidsgivere på skjæringstidspunktet`() {
        nyPeriode(januar, orgnummer = a1)
        nyPeriode(2.januar til 2.januar, orgnummer = a2)

        assertEquals(
            listOf(
                PersonObserver.FørsteFraværsdag(a1, 1.januar),
                PersonObserver.FørsteFraværsdag(a2, 2.januar)
            ), observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.last().førsteFraværsdager
        )
    }

    @Test
    fun `Skal sende med sykmeldingsperiode selv om det er søknad uten sykedager`() {
        håndterSøknad(
            Sykdom(1.januar, 16.januar, 100.prosent),
            Søknad.Søknadsperiode.Ferie(1.januar, 16.januar)
        )

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        val expectedPotensiellForespørsel = PersonObserver.TrengerPotensieltArbeidsgiveropplysningerEvent(
            ORGNUMMER,
            inspektør.vedtaksperiodeId(1.vedtaksperiode),
            1.januar,
            sykmeldingsperioder = listOf(1.januar til 16.januar),
            egenmeldingsperioder = listOf(),
            førsteFraværsdager = listOf() // men ingen første fraværsdag
        )

        assertEquals(
            expectedPotensiellForespørsel,
            observatør.trengerPotensieltArbeidsgiveropplysningerVedtaksperioder.last()
        )
    }

}