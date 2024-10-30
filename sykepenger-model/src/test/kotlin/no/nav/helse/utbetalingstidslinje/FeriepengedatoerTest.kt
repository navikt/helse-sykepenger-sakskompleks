package no.nav.helse.utbetalingstidslinje


import java.time.LocalDate
import java.time.Year
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.april
import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.erHelg
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.håndterAnnullerUtbetaling
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FeriepengedatoerTest : AbstractEndToEndTest() {
    private companion object {
        private val alder = UNG_PERSON_FØDSELSDATO.alder
        private val a1 = "456789123"
        private val a2 = "789456213"
    }

    private fun feriepengerFor(opptjeningsår: Year, historikk: UtbetalingshistorikkForFeriepenger, sisteInfotrygdkjøring: LocalDate = LocalDate.MAX) =
        Feriepengeberegner(alder, opptjeningsår, grunnlagFraInfotrygd = historikk.grunnlagForFeriepenger(sisteInfotrygdkjøring), grunnlagFraSpleis = person.grunnlagForFeriepenger())


    @Test
    fun `Finner datoer for feriepengeberegning med 48 sammenhengende utbetalingsdager i IT fra første januar`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(ORGNUMMER, 1.januar, 7.mars, 1000, 7.mars))
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 49 sammenhengende utbetalingsdager i IT fra første januar`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(ORGNUMMER, 1.januar, 8.mars, 1000, 8.mars))
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 47 sammenhengende utbetalingsdager i IT fra første januar`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(ORGNUMMER, 1.januar, 6.mars, 1000, 6.mars))
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals(47, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning kun for dager i aktuelt opptjeningsår`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.desember(2017),
                    31.januar(2018),
                    1000,
                    31.januar(2018)
                )
            )
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals(23, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Teller ikke med utbetalinger gjort etter feriepengekjøring i IT - oppdatert for første kjøring i 23 for regnskapåret i 21`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.desember(2017),
                    31.januar(2018),
                    1000,
                    24.august(2024)
                )
            )
        )

        val beregner = feriepengerFor(Year.of(2018), historikk, sisteInfotrygdkjøring = 24.august(2024))
        assertEquals(0, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Teller ikke med utbetalinger med inaktiv arbeidskategorikode i IT`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.desember(2017),
                    31.januar(2018),
                    1000,
                    31.januar(2018)
                )
            ),
            arbeidskategorikoder = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(
                listOf(
                    UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.KodePeriode(
                        periode = 1.desember(2017) til 31.januar(2018),
                        arbeidskategorikode = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.Arbeidskategorikode.Inaktiv
                    )
                )
            )
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals(0, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Teller ikke med utbetalinger med kombinert arbeidskategorikode og orgnummer lik 0 i IT`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Personutbetalingsperiode(
                    "0",
                    1.desember(2017),
                    31.januar(2018),
                    1000,
                    31.januar(2018)
                ),
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.desember(2017),
                    31.januar(2018),
                    1000,
                    31.januar(2018)
                )
            ),
            arbeidskategorikoder = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(
                listOf(
                    UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.KodePeriode(
                        periode = 1.desember(2017) til 31.januar(2018),
                        arbeidskategorikode = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.Arbeidskategorikode.ArbeidstakerSelvstendig
                    )
                )
            )
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals(0.0, beregner.beregnFeriepengerForInfotrygdPerson())
    }

    @Test
    fun `Prioriterer ikke personutbetalinger med kombinert arbeidskategorikode og orgnummer lik 0 i IT`() {
        byggPerson(
            arbeidsgiverperiode = 1.januar(2018) til 16.januar(2018),
            syktil = 28.mars(2018)
        )

        val historikk = utbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Personutbetalingsperiode(
                    "0",
                    1.desember(2018),
                    31.desember(2018),
                    1000,
                    31.desember(2018)
                ),
                UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.desember(2018),
                    31.desember(2018),
                    1000,
                    31.desember(2018)
                )
            ),
            arbeidskategorikoder = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(
                listOf(
                    UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.KodePeriode(
                        periode = 1.desember(2018) til 31.desember(2018),
                        arbeidskategorikode = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.Arbeidskategorikode.ArbeidstakerSelvstendig
                    )
                )
            )
        )

        val beregner = feriepengerFor(Year.of(2018), historikk)
        assertEquals((17.januar(2018) til 23.mars(2018)).filterNot { it.erHelg() }, beregner.feriepengedatoer())
        assertEquals(0.0, beregner.beregnFeriepengerForInfotrygdPerson())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 sammenhengende utbetalingsdager i Oppdrag fra første januar`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 7.mars(2018)
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 49 sammenhengende utbetalingsdager i Oppdrag fra første januar`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 8.mars(2018)
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 47 sammenhengende utbetalingsdager i Oppdrag fra første januar`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 6.mars(2018)
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(47, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 sammenhengende utbetalingsdager i Oppdrag fra niende mai`() {
        byggPerson(
            arbeidsgiverperiode = 23.april(2018) til 8.mai(2018),
            syktil = 13.juli(2018)
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 47 ikke-sammenhengende utbetalingsdager i Oppdrag`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 22.januar(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.mars(2018) til 16.mars(2018),
            syktil = 28.mars(2018),
            vedtaksperiodeIdInnhenter = 2.vedtaksperiode
        )
        byggPerson(
            arbeidsgiverperiode = 1.mai(2018) til 16.mai(2018),
            syktil = 12.juni(2018),
            vedtaksperiodeIdInnhenter = 3.vedtaksperiode
        )
        byggPerson(
            arbeidsgiverperiode = 1.juli(2018) til 16.juli(2018),
            syktil = 21.juli(2018),
            vedtaksperiodeIdInnhenter = 4.vedtaksperiode
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(47, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 ikke-sammenhengende utbetalingsdager i Oppdrag`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 22.januar(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.mars(2018) til 16.mars(2018),
            syktil = 28.mars(2018),
            vedtaksperiodeIdInnhenter = 2.vedtaksperiode
        )
        byggPerson(
            arbeidsgiverperiode = 1.mai(2018) til 16.mai(2018),
            syktil = 12.juni(2018),
            vedtaksperiodeIdInnhenter = 3.vedtaksperiode
        )
        byggPerson(
            arbeidsgiverperiode = 1.juli(2018) til 16.juli(2018),
            syktil = 23.juli(2018),
            vedtaksperiodeIdInnhenter = 4.vedtaksperiode
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med to helt overlappende Oppdrag`() {
        byggPersonToParallelle(
            arbeidsgiverperiode = 23.april(2018) til 8.mai(2018),
            syktil = 13.juli(2018)
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(48, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Finner datoer for feriepengeberegning med to ikke-overlappende utbetalingstidslinjer`() {
        byggPerson(
            arbeidsgiverperiode = 1.januar(2018) til 16.januar(2018),
            syktil = 15.februar(2018),
            orgnummer = a1
        )
        byggPerson(
            arbeidsgiverperiode = 1.juli(2018) til 16.juli(2018),
            syktil = 15.august(2018),
            orgnummer = a2
        )

        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())

        assertEquals(44, beregner.feriepengedatoer().size)
    }

    @Test
    fun `Teller ikke med dager fra annullerte utbetalinger i feriepengeberegneren`() {
        byggPersonMedAnnullering()
        val beregner = feriepengerFor(Year.of(2018), utbetalingshistorikkForFeriepenger())
        assertEquals(0, beregner.feriepengedatoer().size)
    }

    private fun utbetalingshistorikkForFeriepenger(
        utbetalinger: List<UtbetalingshistorikkForFeriepenger.Utbetalingsperiode> = emptyList(),
        arbeidskategorikoder: UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(
            listOf(
                UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.KodePeriode(
                    periode = LocalDate.MIN til LocalDate.MAX,
                    arbeidskategorikode = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder.Arbeidskategorikode.Arbeidstaker
                )
            )
        ),
        skalBeregnesManuelt: Boolean = false
    ) =
        UtbetalingshistorikkForFeriepenger(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = AKTØRID,
            fødselsnummer = UNG_PERSON_FNR_2018.toString(),
            utbetalinger = utbetalinger,
            feriepengehistorikk = emptyList(),
            arbeidskategorikoder = arbeidskategorikoder,
            opptjeningsår = Year.of(2020),
            skalBeregnesManuelt = skalBeregnesManuelt,
        )

    private fun byggPerson(
        arbeidsgiverperiode: Periode = 1.januar til 16.januar,
        syktil: LocalDate = 31.januar,
        orgnummer: String = ORGNUMMER,
        vedtaksperiodeIdInnhenter: IdInnhenter? = 1.vedtaksperiode
    ) {
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil), orgnummer = orgnummer)
        håndterSøknad(
            Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent),
            orgnummer = orgnummer
        )
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = orgnummer, vedtaksperiodeIdInnhenter = vedtaksperiodeIdInnhenter)
        håndterVilkårsgrunnlag(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterUtbetalt(orgnummer = orgnummer)
    }

    private fun byggPersonMedAnnullering(
        arbeidsgiverperiode: Periode = 1.januar til 16.januar,
        syktil: LocalDate = 31.januar,
        orgnummer: String = ORGNUMMER
    ) {
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil), orgnummer = orgnummer)
        håndterSøknad(
            Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent),
            orgnummer = orgnummer
        )
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = orgnummer,)
        håndterVilkårsgrunnlag(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterUtbetalt(orgnummer = orgnummer)
        håndterAnnullerUtbetaling(orgnummer = orgnummer)
        håndterUtbetalt()
    }

    private fun byggPersonToParallelle(
        arbeidsgiverperiode: Periode = 1.januar til 16.januar,
        syktil: LocalDate = 31.januar
    ) {
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil), orgnummer = a2)
        håndterSøknad(Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = a1,)
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = a2,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
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
