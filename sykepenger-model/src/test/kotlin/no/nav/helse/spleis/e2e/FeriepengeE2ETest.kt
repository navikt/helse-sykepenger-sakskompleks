package no.nav.helse.spleis.e2e

import java.time.Year
import no.nav.helse.EnableFeriepenger
import no.nav.helse.Toggle
import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Arbeidsgiverutbetalingsperiode
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger.Utbetalingsperiode.Personutbetalingsperiode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.TestArbeidsgiverInspektør.Feriepengeoppdrag.Companion.utbetalingslinjer
import no.nav.helse.inspectors.TestArbeidsgiverInspektør.Feriepengeutbetalingslinje
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.oktober
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.september
import no.nav.helse.serde.reflection.castAsList
import no.nav.helse.sisteBehov
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Klassekode
import no.nav.helse.utbetalingslinjer.Satstype
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import kotlin.math.roundToInt

@EnableFeriepenger
@Isolated
internal class FeriepengeE2ETest : AbstractEndToEndTest() {
    private companion object {
        val DAGSINNTEKT = INNTEKT.rundTilDaglig().dagligInt
    }

    private fun fangLoggmeldinger(vararg filter: String, block: () -> Any): List<String> {
        block()
        val etter = person.personLogg.toString()

        val bareMeldingerSomMatcher = { event: String ->
            filter.isEmpty() || filter.any { filtertekst -> event.contains(filtertekst) }
        }
        return etter.lineSequence().filter(bareMeldingerSomMatcher).toList()
    }

    @Test
    fun `person som har fått utbetalt direkte`() {
        nyttVedtak(1.januar(2022) til 31.januar(2022), refusjon = Inntektsmelding.Refusjon(INGEN, null))
        inspektør.utbetaling(0).let { utbetalingInspektør ->
            assertEquals(0, utbetalingInspektør.arbeidsgiverOppdrag.size)
            assertEquals(1, utbetalingInspektør.personOppdrag.size)
        }
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022)
        )

        assertEquals(1605.5819999999999, inspektør.spleisFeriepengebeløpPerson.first())
        assertEquals(0.0, inspektør.spleisFeriepengebeløpArbeidsgiver.first())
    }
    @Test
    fun `person som har både refusjon og direkte utbetaling`() {
        nyttVedtak(1.januar(2022) til 31.januar(2022), refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null))
        inspektør.utbetaling(0).let { utbetalingInspektør ->
            assertEquals(1, utbetalingInspektør.arbeidsgiverOppdrag.size)
            assertEquals(1, utbetalingInspektør.personOppdrag.size)
        }
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022)
        )

        assertEquals(803.352, inspektør.spleisFeriepengebeløpPerson.first())
        assertEquals(802.2299999999999, inspektør.spleisFeriepengebeløpArbeidsgiver.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpPerson.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
    }
    @Test
    fun `person som har både litt fra infotrygd og litt fra spleis`() {
        nyttVedtak(1.januar(2022) til 31.januar(2022), refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null))
        inspektør.utbetaling(0).let { utbetalingInspektør ->
            assertEquals(1, utbetalingInspektør.arbeidsgiverOppdrag.size)
            assertEquals(1, utbetalingInspektør.personOppdrag.size)
        }
        val dagsatsIT = (INNTEKT/2).dagligInt
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(ORGNUMMER, 17.mars(2022), 31.mars(2022), dagsatsIT, 31.mars(2022)),
                Personutbetalingsperiode(ORGNUMMER, 17.mars(2022), 31.mars(2022), dagsatsIT, 31.mars(2022))
            ),
            feriepengehistorikk = listOf(
                UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 802, 1.mai(2023), 31.mai(2023)),
                UtbetalingshistorikkForFeriepenger.Feriepenger("0", 802, 1.mai(2023), 31.mai(2023))
            )
        )

        assertEquals(803.352, inspektør.spleisFeriepengebeløpPerson.first())
        assertEquals(802.2299999999999, inspektør.spleisFeriepengebeløpArbeidsgiver.first())
        assertEquals(802.2299999999999, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(802.2299999999999, inspektør.infotrygdFeriepengebeløpPerson.first())
    }
    @Test
    fun `person som har både litt fra infotrygd og litt fra spleis med forskjellig refusjon`() {
        nyttVedtak(1.januar(2022) til 31.januar(2022), refusjon = Inntektsmelding.Refusjon(INNTEKT / 3, null))
        inspektør.utbetaling(0).let { utbetalingInspektør ->
            assertEquals(1, utbetalingInspektør.arbeidsgiverOppdrag.size)
            assertEquals(1, utbetalingInspektør.personOppdrag.size)
        }
        val dagsatsIT = (INNTEKT/2).dagligInt
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(ORGNUMMER, 17.mars(2022), 31.desember(2022), dagsatsIT, 31.mars(2022)),
                Personutbetalingsperiode(ORGNUMMER, 17.mars(2022), 31.desember(2022), dagsatsIT, 31.mars(2022))
            )
        )

        assertEquals(1070.388, inspektør.spleisFeriepengebeløpPerson.first())
        assertEquals(535.194, inspektør.spleisFeriepengebeløpArbeidsgiver.first())
        assertEquals(2698.41, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(2698.41, inspektør.infotrygdFeriepengebeløpPerson.first())

        val utbetalingslinjer = listOf(
            Feriepengeutbetalingslinje(
                fom = 1.mai(2023),
                tom = 31.mai(2023),
                satstype = Satstype.Engang,
                beløp = -267,
                grad = null,
                klassekode = Klassekode.RefusjonFeriepengerIkkeOpplysningspliktig,
                endringskode = Endringskode.NY
            ), Feriepengeutbetalingslinje(
                fom = 1.mai(2023),
                tom = 31.mai(2023),
                satstype = Satstype.Engang,
                beløp = 268,
                grad = null,
                klassekode = Klassekode.SykepengerArbeidstakerFeriepenger,
                endringskode = Endringskode.NY
            )
        )
        assertEquals(utbetalingslinjer, inspektør.feriepengeoppdrag.utbetalingslinjer)
    }

    @Test
    fun `Infotrygd har betalt ut 48 dager til person - Spleis har utbetalt 48 i forkant`() {
        nyttVedtak(1.januar(2022) til 31.mars(2022), refusjon = Inntektsmelding.Refusjon(INGEN, null))
        val dagsatsIT = 1574

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022),
            utbetalinger = listOf(
                Personutbetalingsperiode(ORGNUMMER, 1.august(2022), 31.oktober(2022), dagsatsIT, 31.mars(2022))
            )
        )
        assertEquals(0.0, inspektør.spleisFeriepengebeløpArbeidsgiver.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(7006.1759999999995, inspektør.spleisFeriepengebeløpPerson.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpPerson.first())

        val utbetalingslinje = listOf(Feriepengeutbetalingslinje(
            fom = 1.mai(2023),
            tom = 31.mai(2023),
            satstype = Satstype.Engang,
            beløp = -700,
            grad = null,
            klassekode = Klassekode.SykepengerArbeidstakerFeriepenger,
            endringskode = Endringskode.NY
        ))
        assertEquals(utbetalingslinje, inspektør.feriepengeoppdrag.utbetalingslinjer)

    }

    @Test
    fun `Spleis utbetaler feriepenger til person, blir annullert i Spleis mellom første og andre kjøring`() {
        nyttVedtak(1.januar(2022) til 31.mars(2022), refusjon = Inntektsmelding.Refusjon(INGEN, null))
        val dagsatsIT = 1574

        // Første kjøring
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022),
            utbetalinger = listOf(
                Personutbetalingsperiode(ORGNUMMER, 1.august(2022), 31.oktober(2022), dagsatsIT, 31.mars(2022))
            )
        )
        assertEquals(0.0, inspektør.spleisFeriepengebeløpArbeidsgiver.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(7006.1759999999995, inspektør.spleisFeriepengebeløpPerson.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpPerson.first())

        val utbetalingslinje = listOf(Feriepengeutbetalingslinje(
            fom = 1.mai(2023),
            tom = 31.mai(2023),
            satstype = Satstype.Engang,
            beløp = -700,
            grad = null,
            klassekode = Klassekode.SykepengerArbeidstakerFeriepenger,
            endringskode = Endringskode.NY
        ))
        assertEquals(utbetalingslinje, inspektør.feriepengeoppdrag.utbetalingslinjer)

        håndterAnnullerUtbetaling(
            utbetalingId = inspektør.sisteUtbetalingId(1.vedtaksperiode) // Er personoppdraget som annuleres men gjøres basert på fagsystemid på arbeidsgiveroppdraget 🤷‍
        )
        håndterUtbetalt()
        // Andre kjøring ❤️
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022),
            utbetalinger = listOf(
                Personutbetalingsperiode(ORGNUMMER, 17.januar(2022), 31.mars(2022), dagsatsIT, 31.mars(2022)),
                Personutbetalingsperiode(ORGNUMMER, 1.august(2022), 31.oktober(2022), dagsatsIT, 31.oktober(2022))
            )
        )

        val utbetalingslinjerAndreKjøring = listOf(Feriepengeutbetalingslinje(
            fom = 1.mai(2023),
            tom = 31.mai(2023),
            satstype = Satstype.Engang,
            beløp = -700,
            grad = null,
            klassekode = Klassekode.SykepengerArbeidstakerFeriepenger,
            endringskode = Endringskode.ENDR,
            statuskode = "OPPH"
        ))
        assertEquals(utbetalingslinjerAndreKjøring, inspektør.feriepengeoppdrag.utbetalingslinjer)
    }

    @Test
    fun `serialiserer og deserialiserer Spleis feriepengebeløp for person`() {
        nyttVedtak(1.januar(2022) til 31.januar(2022), refusjon = Inntektsmelding.Refusjon(INGEN, null))
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2022)
        )
        assertEquals(1605.5819999999999, inspektør.spleisFeriepengebeløpPerson.first())
        reserialiser()
        assertEquals(1605.5819999999999, inspektør.spleisFeriepengebeløpPerson.first())
    }

    @Test
    fun `Beregner feriepenger korrekt for enkel spleisperiode uten infotrygdhistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )

        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpPerson.first())
        assertEquals(1431 * 10 * 0.102, inspektør.spleisFeriepengebeløpArbeidsgiver.first())

        val utbetalingslinje = Feriepengeutbetalingslinje(
            1.mai(2021),
            31.mai(2021),
            Satstype.Engang,
            1460,
            null,
            Klassekode.RefusjonFeriepengerIkkeOpplysningspliktig,
            Endringskode.NY
        )
        assertEquals(utbetalingslinje, inspektør.feriepengeoppdrag.first().feriepengeutbetalingslinjer.first())
    }

    @Test
    fun `Beregner feriepenger korrekt for enkel spleisperiode med en utbetaling i infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.mars(2020),
                    31.mars(2020),
                    1431,
                    31.mars(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 3211, 1.mai(2021), 31.mai(2021)))
        )

        assertEquals(1431 * 22 * 0.102, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpPerson.first())
        assertEquals(1431 * 10 * 0.102, inspektør.spleisFeriepengebeløpArbeidsgiver.first())

        val utbetalingslinje = Feriepengeutbetalingslinje(
            1.mai(2021),
            31.mai(2021),
            Satstype.Engang,
            1460,
            null,
            Klassekode.RefusjonFeriepengerIkkeOpplysningspliktig,
            Endringskode.NY
        )
        assertEquals(utbetalingslinje, inspektør.feriepengeoppdrag.first().feriepengeutbetalingslinjer.first())
    }

    @Test
    fun `Legger ikke infotrygdcache til grunn for feriepenger`() {
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar(2020), 31.januar(2020), 100.prosent, INNTEKT),
            inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 1.januar(2020), INNTEKT, true)
            )
        )
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.januar(2020),
                    31.januar(2020),
                    1431,
                    31.januar(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 3357, 1.mai(2021), 31.mai(2021)))
        )

        assertEquals(1431 * 23 * 0.102, inspektør.infotrygdFeriepengebeløpArbeidsgiver.first())
        assertEquals(0.0, inspektør.infotrygdFeriepengebeløpPerson.first())
        assertEquals(1431 * 10 * 0.102, inspektør.spleisFeriepengebeløpArbeidsgiver.first())

        val utbetalingslinje = Feriepengeutbetalingslinje(
            1.mai(2021),
            31.mai(2021),
            Satstype.Engang,
            1460,
            null,
            Klassekode.RefusjonFeriepengerIkkeOpplysningspliktig,
            Endringskode.NY
        )
        assertEquals(utbetalingslinje, inspektør.feriepengeoppdrag.first().feriepengeutbetalingslinjer.first())
    }

    @Test
    fun `Beregner ikke feriepenger for personer markert for manuell beregning av feriepenger`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            skalBeregnesManuelt = true
        )

        assertEquals(0, inspektør.feriepengeoppdrag.size)
        assertTrue(person.personLogg.toString().contains("Person er markert for manuell beregning av feriepenger"))
    }

    @Test
    fun `Sender ikke to utbetalingsbehov om feriepengereberegningen er lik den forrige`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.november(2020),
                    30.november(2020),
                    1000,
                    1.desember(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 2142, 1.mai(2021), 31.mai(2021)))
        )
        assertEquals(2, inspektør.feriepengeoppdrag.size)
        assertEquals(1, engangsutbetalinger().size)

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.november(2020),
                    30.november(2020),
                    1000,
                    1.desember(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 2142, 1.mai(2021), 31.mai(2021)))
        )

        assertEquals(4, inspektør.feriepengeoppdrag.size)
        assertEquals(1, engangsutbetalinger().size)
    }

    @Test
    fun `Korrigerer en ukjent arbeidsgiver hvis feriepengene er brukt opp i spleis`() {
        val ORGNUMMER2 = "978654321"
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.august(2020)))
        håndterSøknad(1.juni(2020) til 30.august(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER2,
                    1.november(2020),
                    30.november(2020),
                    1000,
                    1.desember(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER2, 2142, 1.mai(2021), 31.mai(2021)))
        )

        assertEquals(2, inspektør.feriepengeoppdrag.size)
        assertEquals(2, inspektør(ORGNUMMER2).feriepengeoppdrag.size)
        assertEquals(7006, inspektør.feriepengeoppdrag.first().feriepengeutbetalingslinjer.first().beløp)
        assertEquals(-2142, inspektør(ORGNUMMER2).feriepengeoppdrag.first().feriepengeutbetalingslinjer.first().beløp)
    }

    @Test
    fun `Ghost arbeidsgiver fra feriepengeberegner påvirker ikke senere sykepengeberegning`() {
        val ORGNUMMER2 = "978654321"
        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020),
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER2,
                    1.november(2020),
                    30.november(2020),
                    1000,
                    1.desember(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER2, 2142, 1.mai(2021), 31.mai(2021)))
        )
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.august(2020)))
        håndterSøknad(1.juni(2020) til 30.august(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()


        assertEquals(0, inspektør.feriepengeoppdrag.size)
        assertEquals(2, inspektør(ORGNUMMER2).feriepengeoppdrag.size)
        assertTrue(inspektør(ORGNUMMER2).feriepengeoppdrag.first().feriepengeutbetalingslinjer.isEmpty())
    }

    @Test
    fun `Validerer at beregnet feriepengebeløp for IT finnes i lista over utbetalte feriepenger`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Beregnet feriepengebeløp til arbeidsgiver i IT samsvarer ikke med faktisk utbetalt beløp") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                utbetalinger = listOf(
                    Arbeidsgiverutbetalingsperiode(
                        ORGNUMMER,
                        1.januar(2020),
                        31.januar(2020),
                        1431,
                        31.januar(2020)
                    )
                ),
                feriepengehistorikk = listOf(
                    UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 3357, 1.mai(2021), 31.mai(2021)),
                    UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 4000, 1.mai(2021), 31.mai(2021))
                )
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isEmpty())
        }
        assertEquals(2, inspektør.feriepengeoppdrag.size)
    }

    @Test
    fun `Validering feiler hvis beregnet feriepengebeløp for IT ikke finnes i lista over utbetalte feriepenger`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Beregnet feriepengebeløp til arbeidsgiver i IT samsvarer ikke med faktisk utbetalt beløp") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                utbetalinger = listOf(
                    Arbeidsgiverutbetalingsperiode(
                        ORGNUMMER,
                        1.januar(2020),
                        31.januar(2020),
                        1431,
                        31.januar(2020)
                    )
                ),
                feriepengehistorikk = listOf(
                    UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 3356, 1.mai(2021), 31.mai(2021)),
                    UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 4000, 1.mai(2021), 31.mai(2021))
                )
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isNotEmpty())
        }
        assertEquals(2, inspektør.feriepengeoppdrag.size)
    }

    @Test
    fun `Validerer ikke utbetalte feriepenger hvis beregnet feriepengebeløp for IT er 0`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Beregnet feriepengebeløp til arbeidsgiver i IT samsvarer ikke med faktisk utbetalt beløp") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                feriepengehistorikk = listOf(
                    UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 4000, 1.mai(2021), 31.mai(2021))
                )
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isEmpty())
        }

        assertEquals(2, inspektør.feriepengeoppdrag.size)
    }

    @Test
    fun `Utbetaling av feriepenger sender behov til oppdrag`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )

        assertTrue(person.personLogg.toString().contains("Trenger å sende utbetaling til Oppdrag"))
        assertEquals(person.personLogg.behov().last().detaljer()["saksbehandler"], "SPLEIS")

        @Suppress("unchecked_cast")
        val linje = (person.personLogg.behov().last().detaljer()["linjer"] as ArrayList<LinkedHashMap<String, String>>).first()
        assertEquals(linje["satstype"], "ENG")
        assertEquals(linje["klassekode"], "SPREFAGFER-IOP")
        assertEquals(linje["grad"], null)
    }

    @Test
    fun `Sender ut events etter mottak av kvittering fra oppdrag`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )

        val fagsystemIdFeriepenger = person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Utbetaling).detaljer()["fagsystemId"] as String
        håndterFeriepengerUtbetalt(fagsystemId = fagsystemIdFeriepenger)

        assertTrue(person.personLogg.toString().contains("Data for feriepenger fra Oppdrag/UR"))
        assertTrue(person.personLogg.toString().contains("utbetalt ok: ja"))
        observatør.feriepengerUtbetaltEventer.first().let { event ->
            assertEquals(fagsystemIdFeriepenger, event.arbeidsgiverOppdrag.fagsystemId)
            val linje = event.arbeidsgiverOppdrag.linjer.first()
            assertEquals("2021-05-01", linje.fom.toString())
            assertEquals("2021-05-31", linje.tom.toString())
            assertEquals("1460", linje.totalbeløp.toString())
        }
    }

    @Test
    fun `Sender ut events kun for oppdrag med relevant utbetalingId etter mottak av kvittering`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )

        val fagsystemIdFeriepenger = person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Utbetaling).detaljer()["fagsystemId"] as String
        håndterFeriepengerUtbetalt(fagsystemId = fagsystemIdFeriepenger)

        håndterSykmelding(Sykmeldingsperiode(1.juli(2020), 10.juli(2020)))
        håndterSøknad(1.juli(2020) til 10.juli(2020))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )

        håndterFeriepengerUtbetalt(fagsystemId = fagsystemIdFeriepenger)

        assertTrue(person.personLogg.toString().contains("Data for feriepenger fra Oppdrag/UR"))
        assertTrue(person.personLogg.toString().contains("utbetalt ok: ja"))

        assertEquals(2, observatør.feriepengerUtbetaltEventer.size)

        observatør.feriepengerUtbetaltEventer.first().let { event ->
            assertEquals(fagsystemIdFeriepenger, event.arbeidsgiverOppdrag.fagsystemId)
            val linje = (event.arbeidsgiverOppdrag.linjer).first()
            assertEquals("2021-05-01", linje.fom.toString())
            assertEquals("2021-05-31", linje.tom.toString())
            assertEquals("1460", linje.totalbeløp.toString())
        }
        observatør.feriepengerUtbetaltEventer.last().let { event ->
            assertEquals(fagsystemIdFeriepenger, event.arbeidsgiverOppdrag.fagsystemId)
            val linje = (event.arbeidsgiverOppdrag.linjer).first()
            assertEquals("2021-05-01", linje.fom.toString())
            assertEquals("2021-05-31", linje.tom.toString())
            assertEquals("2627",  linje.totalbeløp.toString())
        }

        val feriepengerUtbetaltEndretEventer = observatør.utbetaltEndretEventer.filter { it.type == Utbetalingtype.FERIEPENGER.name }
        assertEquals(2, feriepengerUtbetaltEndretEventer.size)
    }

    @Test
    fun `Sender ikke behov når det ikke er noen diff i IT og spleis sine beregninger av feriepenger`() {
        Toggle.SendFeriepengeOppdrag.enable {
            håndterSykmelding(Sykmeldingsperiode(6.juni(2020), 7.juni(2020)))
            håndterSøknad(6.juni(2020) til 7.juni(2020))
            håndterInntektsmelding(listOf(6.juni(2020) til 7.juni(2020)),)

            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020)
            )
            assertFalse(person.personLogg.toString().contains("Trenger å sende utbetaling til Oppdrag"))
        }
    }

    @Test
    fun `Totalbeløp settes til sats for utbetaling av feriepenger`() {
        Toggle.SendFeriepengeOppdrag.enable {
            håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
            håndterSøknad(1.juni(2020) til 30.juni(2020))
            håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020)
            )

            @Suppress("unchecked_cast")
            val linje = (person.personLogg.behov().last().detaljer()["linjer"] as ArrayList<LinkedHashMap<String, String>>).first()

            assertEquals("1460", "${linje["sats"]}")
            assertEquals("1460", "${linje["totalbeløp"]}")
        }
    }

    @Test
    fun `reberegning av feriepenger med endringer`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020)))
        håndterSøknad(1.juni(2020) til 30.juni(2020))
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )
        håndterUtbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    20.januar(2020),
                    31.januar(2020),
                    690,
                    30.juni(2020)
                )
            ),
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, (10 * 690 * 0.102).roundToInt(), 1.mai, 31.mai)),
            opptjeningsår = Year.of(2020)
        )

        assertEquals(1, engangsutbetalinger().size)
    }

    @Test
    fun `reberegning av feriepenger med endringer hvor totalt utbetalte dager går over 48`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )
        val førsteUtbetaling = engangsutbetalinger().last()
        val fagsystemId = førsteUtbetaling.detaljer()["fagsystemId"]
        håndterUtbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    20.januar(2020),
                    31.januar(2020),
                    690,
                    30.juni(2020)
                )
            ), // 10 dager
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, (10 * 690 * 0.102).roundToInt(), 1.mai, 31.mai)),
            opptjeningsår = Year.of(2020)
        )

        assertEquals(2, engangsutbetalinger().size)
        val utbetaling = engangsutbetalinger().last()
        assertEquals((38 * DAGSINNTEKT * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertEquals(fagsystemId, utbetaling.detaljer()["fagsystemId"])
        assertEquals("ENDR", utbetaling.detaljer()["endringskode"])
        assertEquals("NY", utbetaling.linje()["endringskode"])
        assertEquals(førsteUtbetaling.linje()["delytelseId"], utbetaling.linje()["refDelytelseId"])
        assertEquals(førsteUtbetaling.linje()["klassekode"], utbetaling.linje()["klassekode"])
    }

    @Test
    fun `reberegning av feriepenger med endringer hvor første ikke blir sendt`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    1.januar(2020),
                    8.mars(2020),
                    690,
                    30.juni(2020)
                )
            ), // 10 dager
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, (10 * 690 * 0.102).roundToInt(), 1.mai, 31.mai)),
            opptjeningsår = Year.of(2020)
        )
        håndterUtbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    20.januar(2020),
                    31.januar(2020),
                    690,
                    30.juni(2020)
                )
            ), // 10 dager
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, (10 * 690 * 0.102).roundToInt(), 1.mai, 31.mai)),
            opptjeningsår = Year.of(2020)
        )

        assertEquals(1, engangsutbetalinger().size)
        val utbetaling = engangsutbetalinger().last()
        assertEquals((38 * DAGSINNTEKT * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertEquals("NY", utbetaling.detaljer()["endringskode"])
        assertEquals("NY", utbetaling.linje()["endringskode"])
    }

    @Test
    fun `Kobler ny utbetaling til det forrige sendte oppdraget`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )
        val førsteUtbetaling = engangsutbetalinger().last()
        val fagsystemId = førsteUtbetaling.detaljer()["fagsystemId"]

        håndterUtbetalingshistorikkForFeriepenger(
            opptjeningsår = Year.of(2020)
        )
        assertEquals(4, inspektør.feriepengeoppdrag.size)
        assertEquals(1, engangsutbetalinger().size)

        håndterUtbetalingshistorikkForFeriepenger(
            utbetalinger = listOf(
                Arbeidsgiverutbetalingsperiode(
                    ORGNUMMER,
                    20.januar(2020),
                    31.januar(2020),
                    690,
                    30.juni(2020)
                )
            ), // 10 dager
            feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, (10 * 690 * 0.102).roundToInt(), 1.mai, 31.mai)),
            opptjeningsår = Year.of(2020)
        )

        assertEquals(6, inspektør.feriepengeoppdrag.size)
        assertEquals(2, engangsutbetalinger().size)
        val utbetaling = engangsutbetalinger().last()
        assertEquals((38 * DAGSINNTEKT * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertEquals(fagsystemId, utbetaling.detaljer()["fagsystemId"])
        assertEquals("ENDR", utbetaling.detaljer()["endringskode"])
        assertEquals("NY", utbetaling.linje()["endringskode"])
        assertEquals(førsteUtbetaling.linje()["delytelseId"], utbetaling.linje()["refDelytelseId"])
    }

    @Test
    fun `toggle av føkekr ikke shit`() {
        Toggle.SendFeriepengeOppdrag.enable {
            håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
            håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
            håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020)
            )
            val førsteUtbetaling = engangsutbetalinger()

            Toggle.SendFeriepengeOppdrag.disable {
                håndterUtbetalingshistorikkForFeriepenger(
                    utbetalinger = listOf(
                        Arbeidsgiverutbetalingsperiode(
                            ORGNUMMER,
                            20.januar(2020),
                            31.januar(2020),
                            690,
                            30.juni(2020)
                        )
                    ), // 10 dager
                    feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, (10 * 690 * 0.102).roundToInt(), 1.mai, 31.mai)),
                    opptjeningsår = Year.of(2020)
                )
            }

            assertEquals(1, engangsutbetalinger().size)
            val utbetaling = engangsutbetalinger()
            assertEquals(førsteUtbetaling, utbetaling)
        }
    }

    @Test
    fun `Reberegning som ender med 0 i totalsum sender opphør`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        val førsteUtbetaling = engangsutbetalinger().last()
        val fagsystemId = førsteUtbetaling.detaljer()["fagsystemId"]

        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        assertEquals(4, inspektør.feriepengeoppdrag.size)
        assertEquals(2, engangsutbetalinger().size)
        val utbetaling = engangsutbetalinger().last()
        assertEquals((43 * DAGSINNTEKT * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertEquals(1.mai(2021).toString(), utbetaling.linje()["datoStatusFom"])
        assertEquals(fagsystemId, utbetaling.detaljer()["fagsystemId"])
        assertEquals(førsteUtbetaling.linje()["klassekode"], utbetaling.linje()["klassekode"])
    }

    @Test
    fun `Rekjøring etter annullert oppdrag skal ikke sende feriepenger ved beløp 0`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        val førsteUtbetaling = engangsutbetalinger().last()
        val fagsystemId1FraBehov = førsteUtbetaling.detaljer()["fagsystemId"]

        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        assertEquals(6, inspektør.feriepengeoppdrag.size)
        assertEquals(2, engangsutbetalinger().size)

        val fagsystemId1 = inspektør.feriepengeoppdrag[0].fagsystemId
        val fagsystemId2 = inspektør.feriepengeoppdrag[2].fagsystemId
        val fagsystemId3 = inspektør.feriepengeoppdrag[4].fagsystemId

        assertEquals(fagsystemId1, fagsystemId2)
        assertNotEquals(fagsystemId1, fagsystemId3)

        val fagsystemId2FraBehov = engangsutbetalinger().last().detaljer()["fagsystemId"]
        assertEquals(fagsystemId1, fagsystemId1FraBehov)
        assertEquals(fagsystemId1FraBehov, fagsystemId2FraBehov)
    }

    @Test
    fun `Rekjøring etter annullert oppdrag skal sende feriepenger med ny fagsystemId`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        val førsteUtbetaling = engangsutbetalinger().last()
        val fagsystemId1FraBehov = førsteUtbetaling.detaljer()["fagsystemId"]

        håndterAnnullerUtbetaling()
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        håndterSykmelding(Sykmeldingsperiode(1.oktober(2020), 14.desember(2020)))
        håndterSøknad(1.oktober(2020) til 14.desember(2020)) // 41 dager
        håndterInntektsmelding(listOf(1.oktober(2020) til 16.oktober(2020)), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterUtbetalingshistorikkForFeriepenger(opptjeningsår = Year.of(2020))

        assertEquals(6, inspektør.feriepengeoppdrag.size)
        assertEquals(3, engangsutbetalinger().size)

        val fagsystemId1 = inspektør.feriepengeoppdrag[0].fagsystemId
        val fagsystemId2 = inspektør.feriepengeoppdrag[2].fagsystemId
        val fagsystemId3 = inspektør.feriepengeoppdrag[4].fagsystemId

        assertEquals(fagsystemId1, fagsystemId2)
        assertNotEquals(fagsystemId1, fagsystemId3)

        val fagsystemId2FraBehov = engangsutbetalinger()[1].detaljer()["fagsystemId"]
        assertEquals(fagsystemId1, fagsystemId1FraBehov)
        assertEquals(fagsystemId1FraBehov, fagsystemId2FraBehov)

        val utbetaling = engangsutbetalinger().last()
        assertEquals((41 * DAGSINNTEKT * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertNull(utbetaling.linje()["datoStatusFom"])
        assertEquals(fagsystemId3, utbetaling.detaljer()["fagsystemId"])
        assertEquals(førsteUtbetaling.linje()["klassekode"], utbetaling.linje()["klassekode"])
    }

    @Test
    fun `Feriepengeutbetaling til person`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Differanse mellom det IT har utbetalt og det spleis har beregnet at IT skulle betale") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                utbetalinger = listOf(
                    Personutbetalingsperiode(ORGNUMMER, 1.september(2020), 15.september(2020), 1172, 20.september(2020)),
                ),
                feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 3211, 1.mai(2021), 31.mai(2021)))
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isNotEmpty())
        }
        assertEquals(2, engangsutbetalinger().size)
        val utbetaling = engangsutbetalinger().last()
        assertEquals(0 - (6 * 1172 * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertNull(utbetaling.linje()["datoStatusFom"])
        assertEquals("SP", utbetaling.detaljer()["fagområde"])
        assertEquals("SPATFER", utbetaling.linje()["klassekode"])
    }

    @Test
    fun `Personutbetaling 48 dager i IT, spleis skal ikke betale noe`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Differanse mellom det IT har utbetalt og det spleis har beregnet at IT skulle betale") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                utbetalinger = listOf(
                    Personutbetalingsperiode(ORGNUMMER, 1.januar(2020), 6.mars(2020), 1172, 20.mars(2020)),
                ),
                feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger(ORGNUMMER, 5738, 1.mai(2021), 31.mai(2021)))
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isEmpty())
        }
        assertEquals(0, engangsutbetalinger().size)
    }

    @Test
    fun `Feriepengeutbetaling til orgnummer 0`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Differanse mellom det IT har utbetalt og det spleis har beregnet at IT skulle betale") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                utbetalinger = listOf(
                    Personutbetalingsperiode("0", 1.september(2020), 15.september(2020), 1172, 20.september(2020)),
                ),
                feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger("0", 3211, 1.mai(2021), 31.mai(2021)))
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isNotEmpty())
        }
        assertEquals(2, engangsutbetalinger().size)
        val utbetaling = engangsutbetalinger().last()
        assertEquals(0 - (6 * 1172 * 0.102).roundToInt(), utbetaling.linje()["sats"])
        assertNull(utbetaling.linje()["datoStatusFom"])
        assertEquals("SP", utbetaling.detaljer()["fagområde"])
        assertEquals("SPATFER", utbetaling.linje()["klassekode"])
    }

    @Test
    fun `Test av sanity-logging`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 14.august(2020)))
        håndterSøknad(1.juni(2020) til 14.august(2020)) // 43 dager
        håndterInntektsmelding(listOf(1.juni(2020) til 16.juni(2020)),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        fangLoggmeldinger("Forventer ikke arbeidsgiveroppdrag til orgnummer \"0\"") {
            håndterUtbetalingshistorikkForFeriepenger(
                opptjeningsår = Year.of(2020),
                utbetalinger = listOf(
                    // Ikke funksjonelt gyldig med refusjon til orgnr 0
                    Arbeidsgiverutbetalingsperiode("0", 1.september(2020), 15.september(2020), 1172, 20.september(2020)),
                ),
                feriepengehistorikk = listOf(UtbetalingshistorikkForFeriepenger.Feriepenger("0", 3211, 1.mai(2021), 31.mai(2021)))
            )
        }.also { loggmeldinger ->
            assertTrue(loggmeldinger.isNotEmpty())
        }
        assertEquals(2, engangsutbetalinger().size)
        val ugyldigOppdrag = engangsutbetalinger().last()
        assertEquals(0 - (6 * 1172 * 0.102).roundToInt(), ugyldigOppdrag.linje()["sats"])
        assertNull(ugyldigOppdrag.linje()["datoStatusFom"])
        assertEquals("SPREF", ugyldigOppdrag.detaljer()["fagområde"])
    }

    private fun engangsutbetalinger() = person.personLogg.behov()
        .filter { it.type == Aktivitet.Behov.Behovtype.Utbetaling }
        .filter { utbetaling -> utbetaling.detaljer()["linjer"].castAsList<Map<String, Any>>().any { linje -> linje["satstype"] == "ENG" } }

    private fun Aktivitet.Behov.linje() = this
        .detaljer()["linjer"].castAsList<Map<String, Any?>>()
        .single()
}
