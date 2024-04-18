package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.YearMonth
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.InntektForSykepengegrunnlag.Arbeidsforhold
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_REPLAY
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.spleis.e2e.AktivitetsloggFilter.Companion.filter
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class FrilanserTest : AbstractDslTest() {

    @Test
    fun `frilanssøknad gir error`() {
        frilans {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            assertFunksjonelleFeil()
            assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD)
        }
    }

    @Test
    fun `frilansarbeidsforhold med feriepenger siste tre måneder før skjæringstidspunktet`() {
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT), 1.januar, listOf(
                    Arbeidsforhold(a2, listOf(InntektForSykepengegrunnlag.MånedligArbeidsforhold(november(2017), true)))
                ))
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertIngenFunksjonelleFeil()
            assertIngenVarsler()
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        }
    }

    @Test
    fun `Person med frilanserinntekt i løpet av de siste 3 månedene sendes til infotrygd`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT), 1.januar, listOf(
                    Arbeidsforhold(a1, listOf(InntektForSykepengegrunnlag.MånedligArbeidsforhold(yearMonth = YearMonth.of(2017, 10), erFrilanser = true)))
                ))
            )
            assertFunksjonellFeil("Fant frilanserinntekt på en arbeidsgiver de siste 3 månedene", 1.vedtaksperiode.filter())
            assertForkastetPeriodeTilstander(
                1.vedtaksperiode,
                START,
                AVVENTER_INFOTRYGDHISTORIKK,
                AVVENTER_INNTEKTSMELDING_REPLAY,
                AVVENTER_INNTEKTSMELDING,
                AVVENTER_BLOKKERENDE_PERIODE,
                AVVENTER_VILKÅRSPRØVING,
                TIL_INFOTRYGD
            )
        }
    }

    @Test
    fun `Person med frilanserarbeidsforhold uten inntekt i løpet av de siste 3 månedene skal `() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT), 1.januar, listOf(
                    Arbeidsforhold(a3, listOf(InntektForSykepengegrunnlag.MånedligArbeidsforhold(yearMonth = YearMonth.of(2017, 1), erFrilanser = true)))
                ))
            )
            assertIngenFunksjonelleFeil(1.vedtaksperiode.filter())
            assertTilstander(
                1.vedtaksperiode,
                START,
                AVVENTER_INFOTRYGDHISTORIKK,
                AVVENTER_INNTEKTSMELDING_REPLAY,
                AVVENTER_INNTEKTSMELDING,
                AVVENTER_BLOKKERENDE_PERIODE,
                AVVENTER_VILKÅRSPRØVING,
                AVVENTER_HISTORIKK
            )
        }
    }

    @Test
    fun `Frilansere oppdages ikke i Aa-reg, men case med frilanser-ghost skal gi warning`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, beregnetInntekt = INNTEKT)
            val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT))
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                orgnummer = a1,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to 1000.månedlig), 1.januar),
                arbeidsforhold = arbeidsforhold
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt(orgnummer = a1)

            assertIngenVarsler()
            assertSisteTilstand(1.vedtaksperiode, TilstandType.AVSLUTTET)
        }
    }

    @Test
    fun `Førstegangsbehandling med ekstra arbeidsforhold som ikke er aktivt - skal ikke få warning hvis det er flere arbeidsforhold`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
            håndterInntektsmelding(
                listOf(1.mars til 16.mars),
                førsteFraværsdag = 1.mars,
                beregnetInntekt = 10000.månedlig
            )
            val arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a1, ansattFom = LocalDate.EPOCH, ansattTom = null, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(orgnummer = a2, ansattFom = LocalDate.EPOCH, ansattTom = 1.februar, type = Arbeidsforholdtype.ORDINÆRT)
            )
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                orgnummer = a1,
                inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to 10000.månedlig, a2 to 100.månedlig), 1.januar),
                arbeidsforhold = arbeidsforhold
            )
            håndterYtelser(1.vedtaksperiode)
            assertIngenVarsler()
        }
    }

    @Test
    fun `En arbeidsgiver med inntekter fra flere arbeidsgivere de siste tre månedene - får ikke warning om at det er flere arbeidsgivere, får warning om at det er flere inntektskilder enn arbeidsforhold`() {
        a1 {
            val periode = 1.januar til 31.januar
            håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive))
            håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
            håndterUtbetalt()

            assertIngenVarsler()
        }
    }

}
