package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.februar
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadstype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Støtter ikke søknadstypen`
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// https://github.com/navikt/sykepengesoknad-kafka/blob/master/src/main/kotlin/no/nav/helse/flex/sykepengesoknad/kafka/SoknadstypeDTO.kt
internal class AndreSøknadstyperTest: AbstractDslTest() {

    @ParameterizedTest
    @ValueSource(strings = ["SELVSTENDIGE_OG_FRILANSERE", "OPPHOLD_UTLAND", "ANNET_ARBEIDSFORHOLD", "BEHANDLINGSDAGER", "REISETILSKUDD", "GRADERT_REISETILSKUDD"])
    fun `støtter ikke førstegangsbehandlinger`(søknadstype: String) {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), søknadstype = Søknadstype(søknadstype))
            assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD)
            assertFunksjonellFeil(`Støtter ikke søknadstypen`, 1.vedtaksperiode.filter())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["SELVSTENDIGE_OG_FRILANSERE", "OPPHOLD_UTLAND", "ANNET_ARBEIDSFORHOLD", "BEHANDLINGSDAGER", "REISETILSKUDD", "GRADERT_REISETILSKUDD"])
    fun `støtter ikke forlengelser`(søknadstype: String) {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), søknadstype = Søknadstype(søknadstype))
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
            assertFunksjonellFeil(`Støtter ikke søknadstypen`, 2.vedtaksperiode.filter())
        }
    }
}