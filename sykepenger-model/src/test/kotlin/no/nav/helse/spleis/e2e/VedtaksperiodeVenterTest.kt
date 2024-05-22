package no.nav.helse.spleis.e2e

import java.util.UUID
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.AKTØRID
import no.nav.helse.dsl.TestPerson.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.dsl.nyPeriode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.Venteårsak.Hva
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class VedtaksperiodeVenterTest: AbstractDslTest() {

    @Test
    fun `Vedtaksperioden vi venter på kan være en annen enn den som er nestemann til behandling`() {
        a1 { håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent)) }
        a2 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        }
        val a2VedtaksperiodeId = a2 { 1.vedtaksperiode }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            val vedtaksperiodeVenter = observatør.vedtaksperiodeVenter.last { it.vedtaksperiodeId == 1.vedtaksperiode }
            assertEquals(a2VedtaksperiodeId, vedtaksperiodeVenter.venterPå.vedtaksperiodeId)
            assertEquals("a2", vedtaksperiodeVenter.venterPå.organisasjonsnummer)
            assertEquals("INNTEKTSMELDING", vedtaksperiodeVenter.venterPå.venteårsak.hva)
            assertNull(vedtaksperiodeVenter.venterPå.venteårsak.hvorfor)
        }
    }

    @Test
    fun `Venter på tidligere periode som ikke har fått inntektsmelding`(){
        a1 {
            val søknadIdJanuar = UUID.randomUUID()
            nyPeriode(1.januar til 31.januar, søknadId = søknadIdJanuar)

            assertVenterPå(listOf(
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING
            ))
            val søknadIdMars = UUID.randomUUID()
            nyPeriode(1.mars til 31.mars, søknadId = søknadIdMars)
            assertVenterPå(listOf(
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                2.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                2.vedtaksperiode to Hva.INNTEKTSMELDING
            ))

            val inntektsmeldingIdMars = håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertVenterPå(listOf(
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                2.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                2.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                2.vedtaksperiode to Hva.INNTEKTSMELDING
            ))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            val venterTil = inspektør(1.vedtaksperiode).oppdatert.plusDays(180)
            val forventetVedtaksperiode1 = PersonObserver.VedtaksperiodeVenterEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = inspektør(1.vedtaksperiode).behandlinger.last().id,
                skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                hendelser = setOf(søknadIdJanuar),
                ventetSiden = inspektør(1.vedtaksperiode).oppdatert,
                venterTil = venterTil,
                venterPå = PersonObserver.VedtaksperiodeVenterEvent.VenterPå(
                    vedtaksperiodeId = 1.vedtaksperiode,
                    skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                    organisasjonsnummer = a1,
                    venteårsak = PersonObserver.VedtaksperiodeVenterEvent.Venteårsak(
                        hva = "INNTEKTSMELDING",
                        hvorfor = null
                    )
                )
            )
            val forventetVedtaksperiode2 = PersonObserver.VedtaksperiodeVenterEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = a1,
                vedtaksperiodeId = 2.vedtaksperiode,
                behandlingId = inspektør(2.vedtaksperiode).behandlinger.last().id,
                skjæringstidspunkt = inspektør(2.vedtaksperiode).skjæringstidspunkt,
                hendelser = setOf(søknadIdMars, inntektsmeldingIdMars),
                ventetSiden = inspektør(2.vedtaksperiode).oppdatert,
                venterTil = venterTil,
                venterPå = PersonObserver.VedtaksperiodeVenterEvent.VenterPå(
                    vedtaksperiodeId = 1.vedtaksperiode,
                    skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                    organisasjonsnummer = a1,
                    venteårsak = PersonObserver.VedtaksperiodeVenterEvent.Venteårsak(
                        hva = "INNTEKTSMELDING",
                        hvorfor = null
                    )
                )
            )
            assertEquals(forventetVedtaksperiode1, observatør.vedtaksperiodeVenter.last{
                it.vedtaksperiodeId == 1.vedtaksperiode
            })
            assertEquals(forventetVedtaksperiode2, observatør.vedtaksperiodeVenter.last {
                it.vedtaksperiodeId == 2.vedtaksperiode
            })
        }
    }

    @Test
    fun HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE(){
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        }
        a2 {
            val søknadId = UUID.randomUUID()
            nyPeriode(1.januar til 31.januar, søknadId = søknadId)
            assertVenterPå(listOf(
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING
            ))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertVenterPå(listOf(
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.INNTEKTSMELDING,
                1.vedtaksperiode to Hva.SØKNAD
            ))

            val forventet = PersonObserver.VedtaksperiodeVenterEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = a2,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = inspektør(1.vedtaksperiode).behandlinger.last().id,
                skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                hendelser = setOf(søknadId, inntektsmeldingId),
                ventetSiden = inspektør(1.vedtaksperiode).oppdatert,
                venterTil = inspektør(1.vedtaksperiode).oppdatert.plusDays(90),
                venterPå = PersonObserver.VedtaksperiodeVenterEvent.VenterPå(
                    vedtaksperiodeId = 1.vedtaksperiode,
                    skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                    organisasjonsnummer = a2,
                    venteårsak = PersonObserver.VedtaksperiodeVenterEvent.Venteårsak(
                        hva = "SØKNAD",
                        hvorfor = "HAR_SYKMELDING_SOM_OVERLAPPER_PÅ_ANDRE_ARBEIDSGIVERE"
                    )
                )
            )
            assertEquals(forventet, observatør.vedtaksperiodeVenter.last())
        }
    }

    @Test
    fun `Periode i avventer innteksmelding`() {
        a1 {
            val søknadId = UUID.randomUUID()
            nyPeriode(1.januar til 31.januar, søknadId = søknadId)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            val forventet = PersonObserver.VedtaksperiodeVenterEvent(
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                aktørId = AKTØRID,
                organisasjonsnummer = a1,
                vedtaksperiodeId = 1.vedtaksperiode,
                behandlingId = inspektør(1.vedtaksperiode).behandlinger.last().id,
                skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                hendelser = setOf(søknadId),
                ventetSiden = inspektør(1.vedtaksperiode).oppdatert,
                venterTil = inspektør(1.vedtaksperiode).oppdatert.plusDays(180),
                venterPå = PersonObserver.VedtaksperiodeVenterEvent.VenterPå(
                    vedtaksperiodeId = 1.vedtaksperiode,
                    skjæringstidspunkt = inspektør(1.vedtaksperiode).skjæringstidspunkt,
                    organisasjonsnummer = a1,
                    venteårsak = PersonObserver.VedtaksperiodeVenterEvent.Venteårsak(
                        hva = "INNTEKTSMELDING",
                        hvorfor = null
                    )
                )
            )
            assertEquals(forventet, observatør.vedtaksperiodeVenter.last())
        }
    }

    @Test
    fun `En periode i Avsluttet Uten Utbetaling som eneste periode som fortsatt ikke skal utbetales skriker ikke om hjelp`() {
        a1 {
            nyPeriode(16.januar til 31.januar)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            val venteHendelseFør = observatør.vedtaksperiodeVenter.toList()
            håndterPåminnelse(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            assertEquals(venteHendelseFør, observatør.vedtaksperiodeVenter)
        }
    }

    @Test
    fun `Om perioden man venter på har en timeout bør den brukes som venter til`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
            håndterInntektsmelding(listOf(1.mars til 16.mars))
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

            val januarVenterTil = inspektør(1.vedtaksperiode).oppdatert.plusDays(180)
            val januarVenter = observatør.vedtaksperiodeVenter.last { it.vedtaksperiodeId == 1.vedtaksperiode }
            assertEquals(1.vedtaksperiode, januarVenter.venterPå.vedtaksperiodeId)
            assertEquals(januarVenterTil, januarVenter.venterTil)
            val marsVenter = observatør.vedtaksperiodeVenter.last { it.vedtaksperiodeId == 2.vedtaksperiode }
            assertEquals(1.vedtaksperiode, marsVenter.venterPå.vedtaksperiodeId)
            assertEquals(januarVenterTil, marsVenter.venterTil)
        }
    }

    private fun assertVenterPå(expected: List<Pair<UUID, Hva>>) {
        val actual = observatør.vedtaksperiodeVenter.map { it.vedtaksperiodeId to it.venterPå.venteårsak.hva }
        assertEquals(expected.map { it.first to it.second.toString() }, actual)
        assertEquals(expected.size, observatør.vedtaksperiodeVenter.size)
    }
}