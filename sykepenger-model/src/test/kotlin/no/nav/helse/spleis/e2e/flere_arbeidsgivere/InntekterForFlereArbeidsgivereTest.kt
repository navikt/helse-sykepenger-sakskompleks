package no.nav.helse.spleis.e2e.flere_arbeidsgivere

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.oktober
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.Person
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertInntektForDato
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterInntektsmeldingPortal
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.sykmelding
import no.nav.helse.spleis.e2e.søknad
import no.nav.helse.spleis.e2e.utbetalingshistorikkEtterInfotrygdEndring
import no.nav.helse.spleis.e2e.ytelser
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class InntekterForFlereArbeidsgivereTest : AbstractEndToEndTest() {

    private val a1Inspektør get() = TestArbeidsgiverInspektør(person, a1)
    private val a2Inspektør get() = TestArbeidsgiverInspektør(person, a2)
    private val a3Inspektør get() = TestArbeidsgiverInspektør(person, a3)
    private val a4Inspektør get() = TestArbeidsgiverInspektør(person, a4)

    @Test
    fun `Inntekter fra flere arbeidsgivere`() {
        nyPeriode(
            periode = 1.januar til 31.januar,
            orgnummer = a1,
            inntekt = 16000.månedlig,
        )
        assertIngenFunksjonelleFeil()

        vilkårsgrunnlag(
            1.vedtaksperiode,
            skjæringstidspunkt = 1.januar,
            orgnummer = a1,
            inntekterForSykepengegrunnlag = inntektperioderForSykepengegrunnlag {
                1.oktober(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 15000
                    a3 inntekt 4750
                    a4 inntekt 2250
                }
            },
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a3, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a4, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
            )
        ).håndter(Person::håndter)

        assertIngenFunksjonelleFeil()

        assertInntektForDato(16000.månedlig, 1.januar, inspektør = a1Inspektør)
        assertInntektForDato(null, 1.januar, inspektør = a2Inspektør)
        assertInntektForDato(4750.månedlig, 1.januar, inspektør = a3Inspektør)
        assertInntektForDato(2250.månedlig, 1.januar, inspektør = a4Inspektør)
    }

    @Test
    fun `Lagrer ikke inntekter fra infotrygd`() {
        val inntekterForSykepengegrunnlag = inntektperioderForSykepengegrunnlag {
            1.oktober(2017) til 1.desember(2017) inntekter {
                a1 inntekt 23500.månedlig
                a2 inntekt 4900.månedlig
            }
        }

        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        nyPeriode(1.januar til 31.januar, a1, 25000.månedlig)

        vilkårsgrunnlag(
            1.vedtaksperiode,
            skjæringstidspunkt = 1.januar,
            orgnummer = a1,
            inntekterForSykepengegrunnlag = inntekterForSykepengegrunnlag,
            arbeidsforhold = arbeidsforhold
        ).håndter(Person::håndter)

        ytelser(
            1.vedtaksperiode, orgnummer = a1
        ).håndter(Person::håndter)

        val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(1.januar) ?: fail { "forventet vilkårsgrunnlag" }

        assertEquals(25000.månedlig, vilkårsgrunnlag.inspektør.sykepengegrunnlag.inspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.inntektsopplysning.inspektør.beløp)
        assertEquals(4900.månedlig, vilkårsgrunnlag.inspektør.sykepengegrunnlag.inspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.inntektsopplysning.inspektør.beløp)
    }

    @Test
    fun `Skatteinntekter og inntektsmelding for en arbeidsgiver og kun skatt for andre arbeidsgiver - gir korrekt sykepenge- og sammenligningsgrunnlag`() {
        val inntekterForSykepengegrunnlag = inntektperioderForSykepengegrunnlag {
            1.oktober(2017) til 1.desember(2017) inntekter {
                a1 inntekt 15000
                a2 inntekt 21000
            }
        }
        val arbeidsforhold = listOf(
            Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT),
            Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null, Arbeidsforholdtype.ORDINÆRT)
        )
        nyPeriode(1.januar til 31.januar, a1, 25000.månedlig)
        vilkårsgrunnlag(
            1.vedtaksperiode,
            skjæringstidspunkt = 1.januar,
            orgnummer = a1,
            inntekterForSykepengegrunnlag = inntekterForSykepengegrunnlag,
            arbeidsforhold = arbeidsforhold
        ).håndter(Person::håndter)

        val grunnlagsdataInspektør = person.vilkårsgrunnlagFor(1.januar)?.inspektør ?: fail { "fant ikke vilkårsgrunnlag" }
        assertEquals(552000.årlig, grunnlagsdataInspektør.sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `To arbeidsgivere, kun én blir forlenget - dagsats justeres pga lavere total sykdomsgrad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, orgnummer = a2)

        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar), orgnummer = a1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertEquals(1.januar, inspektør(a1).skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(1.januar, inspektør(a1).skjæringstidspunkt(2.vedtaksperiode))
        assertEquals(1.januar, inspektør(a2).skjæringstidspunkt(1.vedtaksperiode))

        val utbetalingslinje1 = inspektør(a1).utbetalingslinjer(0)
        val utbetalingslinje2 = inspektør(a1).utbetalingslinjer(1)

        assertEquals(1081, inspektør(a1).utbetaling(0).inspektør.arbeidsgiverOppdrag[0].beløp)
        assertEquals(1080, inspektør(a2).utbetaling(0).inspektør.arbeidsgiverOppdrag[0].beløp)
        assertEquals(1080, inspektør(a1).utbetaling(1).inspektør.arbeidsgiverOppdrag[1].beløp)

        assertNotEquals(utbetalingslinje1, utbetalingslinje2)
        assertNotEquals(utbetalingslinje1.linjerUtenOpphør().last().beløp, utbetalingslinje2.linjerUtenOpphør().last().beløp)
        assertEquals(utbetalingslinje1.fagsystemId(), utbetalingslinje2.fagsystemId())
    }

    private fun nyPeriode(
        periode: Periode,
        orgnummer: String,
        inntekt: Inntekt
    ) {
        utbetalingshistorikkEtterInfotrygdEndring().håndter(Person::håndter)
        sykmelding(
            UUID.randomUUID(),
            Sykmeldingsperiode(periode.start, periode.endInclusive),
            orgnummer = orgnummer,
            mottatt = periode.endInclusive.atStartOfDay()
        ).håndter(Person::håndter)
        søknad(
            UUID.randomUUID(),
            Sykdom(periode.start, periode.endInclusive, 100.prosent),
            orgnummer = orgnummer
        ).håndter(Person::håndter)
        håndterInntektsmeldingPortal(
            arbeidsgiverperioder = listOf(Periode(periode.start, periode.start.plusDays(15))),
            beregnetInntekt = inntekt,
            inntektsdato = periode.start,
            førsteFraværsdag = periode.start,
            orgnummer = orgnummer
        )
    }

    private fun vilkårsgrunnlag(
        vedtaksperiodeIdInnhenter: IdInnhenter,
        skjæringstidspunkt: LocalDate,
        arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>? = null,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
        orgnummer: String = ORGNUMMER,
        meldingsreferanseId: UUID = UUID.randomUUID(),
        inntekterForSykepengegrunnlag: List<ArbeidsgiverInntekt>

    ): Vilkårsgrunnlag {
        return Vilkårsgrunnlag(
            meldingsreferanseId = meldingsreferanseId,
            vedtaksperiodeId = vedtaksperiodeIdInnhenter.id(orgnummer).toString(),
            skjæringstidspunkt = skjæringstidspunkt,
            aktørId = AKTØRID,
            personidentifikator = UNG_PERSON_FNR_2018,
            orgnummer = orgnummer,
            medlemskapsvurdering = Medlemskapsvurdering(medlemskapstatus),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekterForSykepengegrunnlag, arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold ?: listOf(
                Vilkårsgrunnlag.Arbeidsforhold(orgnummer, 1.januar(2017), type = Arbeidsforholdtype.ORDINÆRT)
            )
        ).apply {
            hendelselogg = this

        }
    }
}
