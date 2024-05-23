package no.nav.helse.spleis.e2e.behandlinger

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.forlengVedtak
import no.nav.helse.februar
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.AbstractPersonTest.Companion.UNG_PERSON_FNR_2018
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BehandlingOpprettetEventTest : AbstractDslTest() {

    @Test
    fun `event om opprettet behandling`() {
        a1 {
            val søknadId = UUID.randomUUID()
            val registrert = LocalDateTime.now()
            val innsendt = registrert.minusHours(2)
            håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), søknadId = søknadId, sendtTilNAVEllerArbeidsgiver = innsendt, registrert = registrert)
            val behandlingOpprettetEvent = observatør.behandlingOpprettetEventer.last()
            inspektør(1.vedtaksperiode).behandlinger.also { behandlinger ->
                val behandlingId = behandlinger.single().id
                val forventetBehandlingEvent = PersonObserver.BehandlingOpprettetEvent(
                    fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                    aktørId = "42",
                    organisasjonsnummer = a1,
                    vedtaksperiodeId = 1.vedtaksperiode,
                    behandlingId = behandlingId,
                    type = PersonObserver.BehandlingOpprettetEvent.Type.Søknad,
                    fom = 1.januar,
                    tom = 20.januar,
                    kilde = PersonObserver.BehandlingOpprettetEvent.Kilde(
                        meldingsreferanseId = søknadId,
                        innsendt = innsendt,
                        registert = registrert,
                        avsender = Avsender.SYKMELDT
                    )
                )
                assertEquals(forventetBehandlingEvent, behandlingOpprettetEvent)
            }
        }
    }

    @Test
    fun revurdering() {
        a1 {
            nyttVedtak(1.januar til 31.januar, 100.prosent)
            håndterSøknad(Sykdom(1.januar, 31.januar, 80.prosent))
            val behandlingOpprettetEventer = observatør.behandlingOpprettetEventer
            assertEquals(2, behandlingOpprettetEventer.size)
            val førsteEvent = behandlingOpprettetEventer.first()
            val andreEvent = behandlingOpprettetEventer.last()
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Søknad, førsteEvent.type)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering, andreEvent.type)
        }
    }

    @Test
    fun omgjøring() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
            håndterInntektsmelding(listOf(25.desember(2017) til 10.januar), beregnetInntekt = INNTEKT)
            val behandlingOpprettetEventer = observatør.behandlingOpprettetEventer
            assertEquals(2, behandlingOpprettetEventer.size)
            val førsteEvent = behandlingOpprettetEventer.first()
            val andreEvent = behandlingOpprettetEventer.last()
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Søknad, førsteEvent.type)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Omgjøring, andreEvent.type)
        }
    }

    @Test
    fun annullering() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            forlengVedtak(1.februar til 28.februar)
            håndterAnnullering(inspektør.utbetaling(0).inspektør.utbetalingId)
            val behandlingOpprettetEventer = observatør.behandlingOpprettetEventer
            assertEquals(4, behandlingOpprettetEventer.size)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Søknad, behandlingOpprettetEventer[0].type)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Søknad, behandlingOpprettetEventer[1].type)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering, behandlingOpprettetEventer[2].type)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering, behandlingOpprettetEventer[3].type)

            val vedtaksperiodeForkastetEventer = observatør.behandlingForkastetEventer
            assertEquals(2, vedtaksperiodeForkastetEventer.size)
            assertEquals(1.vedtaksperiode, vedtaksperiodeForkastetEventer[0].vedtaksperiodeId)
            assertEquals(2.vedtaksperiode, vedtaksperiodeForkastetEventer[1].vedtaksperiodeId)
        }
    }

    @Test
    fun `søknaden som forkastes på direkten`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), utenlandskSykmelding = true)
            val behandlingOpprettet = observatør.behandlingOpprettetEventer.single()
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Søknad, behandlingOpprettet.type)
        }
    }

    @Test
    fun `anmoder en periode om forkasting`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
            håndterUtbetalingshistorikkEtterInfotrygdendring(listOf(ArbeidsgiverUtbetalingsperiode(a1, 1.januar, 10.januar, 100.prosent, 500.daglig)))
            val behandlingOpprettet = observatør.behandlingOpprettetEventer
            assertEquals(2, behandlingOpprettet.size)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Søknad, behandlingOpprettet[0].type)
            assertEquals(PersonObserver.BehandlingOpprettetEvent.Type.Omgjøring, behandlingOpprettet[1].type)
            val behandlingForkastetEvent = observatør.behandlingForkastetEventer.single()
            assertEquals(1.vedtaksperiode, behandlingForkastetEvent.vedtaksperiodeId)
        }
    }
}
