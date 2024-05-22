package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadstype.Companion.Arbeidsledig
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Arbeidsledigsøknad er lagt til grunn`
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader`
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class ArbeidsledigSøknadTest: AbstractDslTest() {

    @Test
    fun `støtter arbeidsledigsøknad som forlengelse av tidligere vilkårsprøvd skjæringstidspunkt`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), søknadstype = Arbeidsledig)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
            assertVarsel(`Arbeidsledigsøknad er lagt til grunn`, 2.vedtaksperiode.filter())
        }
    }
    @Test
    fun `trenger ikke varsel ved forlengelse hvis det ikke er refusjon`() {
        a1 {
            nyttVedtak(1.januar til 31.januar, refusjon = Inntektsmelding.Refusjon(Inntekt.INGEN, null, emptyList()))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), søknadstype = Arbeidsledig)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
            assertIngenVarsel(`Arbeidsledigsøknad er lagt til grunn`, 2.vedtaksperiode.filter())
        }
    }

    @Test
    fun `støtter ikke arbeidsledigsøknad som førstegangssøknad`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), søknadstype = Arbeidsledig)
            assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
            assertFunksjonellFeil(`Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader`, 1.vedtaksperiode.filter())
        }
    }

    @Test
    fun `støtter arbeidsledigsøknad som forlengelse av tidligere vilkårsprøvd skjæringstidspunkt med fler arbeidsgivere i sykepengegrunnlaget`() {
        (a1 og a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), søknadstype = Arbeidsledig)
            assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        }
    }

    @Test
    fun `støtter ikke arbeidsledigsøknad som forlengelse av tidligere vilkårsprøvd skjæringstidspunkt hvor arbeidsgiver ikke inngår i sykepengegrunnlaget`() {
        (a1 og a2).nyeVedtak(1.januar til 31.januar)
        a3 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), søknadstype = Arbeidsledig)
            assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD)
            assertFunksjonellFeil(Varselkode.RV_SV_2, 1.vedtaksperiode.filter())
        }
    }
}