package no.nav.helse.utbetalingslinjer

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OppdragTest {

    @Test
    fun `begrense oppdrag til siste dato`() {
        val oppdrag1 = Oppdrag("", Fagområde.SykepengerRefusjon, listOf(
            Utbetalingslinje(
                fom = 19.januar,
                tom = 25.januar,
                endringskode = Endringskode.UEND,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 1,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 26.januar,
                tom = 28.januar,
                endringskode = Endringskode.ENDR,
                aktuellDagsinntekt = 1000,
                beløp = 500,
                grad = 50,
                delytelseId = 2,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 29.januar,
                tom = 31.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 3,
                refDelytelseId = null,
                refFagsystemId = null
            )
        ), sisteArbeidsgiverdag = 18.januar)

        assertTrue(oppdrag1.begrensTil(18.januar).isEmpty())
        oppdrag1.begrensTil(20.januar).also { result ->
            assertEquals(1, result.size)
            assertEquals(19.januar, result.single().inspektør.fom)
            assertEquals(19.januar, result.single().inspektør.tom)
        }
        oppdrag1.begrensTil(28.januar).also { result ->
            assertEquals(2, result.size)
            assertEquals(19.januar, result[0].inspektør.fom)
            assertEquals(25.januar, result[0].inspektør.tom)
            assertEquals(26.januar, result[1].inspektør.fom)
            assertEquals(28.januar, result[1].inspektør.tom)
        }
    }

    @Test
    fun `begrense oppdrag fra første dato`() {
        val oppdrag1 = Oppdrag("", Fagområde.SykepengerRefusjon, listOf(
            Utbetalingslinje(
                fom = 19.januar,
                tom = 25.januar,
                endringskode = Endringskode.UEND,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 1,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 26.januar,
                tom = 28.januar,
                endringskode = Endringskode.ENDR,
                aktuellDagsinntekt = 1000,
                beløp = 500,
                grad = 50,
                delytelseId = 2,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 29.januar,
                tom = 31.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 3,
                refDelytelseId = null,
                refFagsystemId = null
            )
        ), sisteArbeidsgiverdag = 18.januar)

        assertTrue(oppdrag1.begrensFra(1.februar).isEmpty())
        oppdrag1.begrensFra(29.januar).also { result ->
            assertEquals(1, result.size)
            assertEquals(29.januar, result.single().inspektør.fom)
            assertEquals(31.januar, result.single().inspektør.tom)
            assertEquals(3, result.single().inspektør.delytelseId)
        }
        oppdrag1.begrensFra(24.januar).also { result ->
            assertEquals(3, result.size)
            assertEquals(24.januar, result[0].inspektør.fom)
            assertEquals(25.januar, result[0].inspektør.tom)
            assertEquals(26.januar, result[1].inspektør.fom)
            assertEquals(28.januar, result[1].inspektør.tom)
            assertEquals(29.januar, result[2].inspektør.fom)
            assertEquals(31.januar, result[2].inspektør.tom)
        }
    }

    @Test
    fun `prepende tomme oppdrag`() {
        val oppdrag1 = Oppdrag("", Fagområde.SykepengerRefusjon)
        val oppdrag2 = Oppdrag("", Fagområde.SykepengerRefusjon)
        val result = oppdrag1 + oppdrag2
        assertTrue(result.isEmpty())
    }
    @Test
    fun `prepend oppdrag`() {
        val oppdrag1 = Oppdrag("", Fagområde.SykepengerRefusjon, listOf(
            Utbetalingslinje(
                fom = 19.januar,
                tom = 25.januar,
                endringskode = Endringskode.UEND,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 1,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 26.januar,
                tom = 28.januar,
                endringskode = Endringskode.ENDR,
                aktuellDagsinntekt = 1000,
                beløp = 500,
                grad = 50,
                delytelseId = 2,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 29.januar,
                tom = 31.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 3,
                refDelytelseId = null,
                refFagsystemId = null
            )
        ), sisteArbeidsgiverdag = 18.januar)
        val oppdrag2 = Oppdrag("", Fagområde.SykepengerRefusjon, listOf(
            Utbetalingslinje(
                fom = 17.januar,
                tom = 18.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100
            )
        ), sisteArbeidsgiverdag = 16.januar)

        val oppdrag3 = Oppdrag("", Fagområde.SykepengerRefusjon, listOf(
            Utbetalingslinje(
                fom = 17.januar,
                tom = 25.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 1,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 26.januar,
                tom = 28.januar,
                endringskode = Endringskode.ENDR,
                aktuellDagsinntekt = 1000,
                beløp = 500,
                grad = 50,
                delytelseId = 2,
                refDelytelseId = null,
                refFagsystemId = null
            ),
            Utbetalingslinje(
                fom = 29.januar,
                tom = 31.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 3,
                refDelytelseId = null,
                refFagsystemId = null
            )
        ), sisteArbeidsgiverdag = 16.januar)
        assertEquals(oppdrag3, oppdrag1 + oppdrag2)
        assertEquals(oppdrag3, oppdrag2 + oppdrag1)

        val oppdragSomKanUtbetales = oppdrag3.minus(oppdrag1, Aktivitetslogg())
        val expectedOppdragSomKanUtbetales = Oppdrag("", Fagområde.SykepengerRefusjon, listOf(
            Utbetalingslinje(
                fom = 17.januar,
                tom = 25.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 4,
                refDelytelseId = 3,
                refFagsystemId = oppdrag1.inspektør.fagsystemId()
            ),
            Utbetalingslinje(
                fom = 26.januar,
                tom = 28.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 500,
                grad = 50,
                delytelseId = 5,
                refDelytelseId = 4,
                refFagsystemId = oppdrag1.inspektør.fagsystemId()
            ),
            Utbetalingslinje(
                fom = 29.januar,
                tom = 31.januar,
                endringskode = Endringskode.NY,
                aktuellDagsinntekt = 1000,
                beløp = 1000,
                grad = 100,
                delytelseId = 6,
                refDelytelseId = 5,
                refFagsystemId = oppdrag1.inspektør.fagsystemId()
            )
        ), sisteArbeidsgiverdag = 16.januar)
        assertEquals(expectedOppdragSomKanUtbetales, oppdragSomKanUtbetales)
    }

    private fun assertEquals(expected: Oppdrag, actual: Oppdrag) {
        assertEquals(expected.size, actual.size) { "antall linjer stemmer ikke" }
        assertEquals(expected.inspektør.mottaker, actual.inspektør.mottaker) { "mottaker stemmer ikke" }
        assertEquals(expected.inspektør.fagområde, actual.inspektør.fagområde) { "mottaker stemmer ikke" }
        expected.forEachIndexed { index, expectedLinje ->
            val actualLinje = actual[index]

            assertEquals(expectedLinje.inspektør.fom, actualLinje.inspektør.fom)
            assertEquals(expectedLinje.inspektør.tom, actualLinje.inspektør.tom)
            assertEquals(expectedLinje.inspektør.beløp, actualLinje.inspektør.beløp)
            assertEquals(expectedLinje.inspektør.grad, actualLinje.inspektør.grad)
        }
    }

    @Test
    fun `tomt oppdrag ber ikke om simulering (brukerutbetaling)`() {
        val oppdrag = Oppdrag("mottaker", Fagområde.Sykepenger)
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.simuler(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    @Test
    fun `nettobeløp for annulleringer`() {
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 1.januar,
                    tom = 16.januar,
                    endringskode = Endringskode.UEND,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )
        val annullering = oppdrag.annuller(Aktivitetslogg())
        assertEquals(-1 * oppdrag.totalbeløp(), annullering.nettoBeløp())
    }

    @Test
    fun `uend linjer i oppdrag ber ikke om simulering (brukerutbetaling)`() {
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 1.januar,
                    tom = 16.januar,
                    endringskode = Endringskode.UEND,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.simuler(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    @Test
    fun `tomt oppdrag ber ikke om overføring (brukerutbetaling)`() {
        val oppdrag = Oppdrag("mottaker", Fagområde.Sykepenger)
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.overfør(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    @Test
    fun `tomt oppdrag ber ikke om simulering (arbeidsgiver)`() {
        val oppdrag = Oppdrag("mottaker", Fagområde.SykepengerRefusjon)
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.simuler(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    @Test
    fun `tomt oppdrag ber ikke om overføring (arbeidsgiver)`() {
        val oppdrag = Oppdrag("mottaker", Fagområde.SykepengerRefusjon)
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.overfør(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    @Test
    fun `er relevant`() {
        val fagsystemId = "a"
        val fagområde = Fagområde.SykepengerRefusjon
        val oppdrag = Oppdrag("mottaker", fagområde, fagsystemId = fagsystemId, sisteArbeidsgiverdag = 1.januar)
        assertTrue(oppdrag.erRelevant(fagsystemId, fagområde))
        assertFalse(oppdrag.erRelevant(fagsystemId, Fagområde.Sykepenger))
        assertFalse(oppdrag.erRelevant("b", fagområde))
    }

    @Test
    fun `simulerer oppdrag med linjer`(){
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 17.januar,
                    tom = 31.januar,
                    endringskode = Endringskode.NY,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.simuler(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isNotEmpty())
    }

    @Test
    fun `overfører oppdrag med linjer`(){
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 17.januar,
                    tom = 31.januar,
                    endringskode = Endringskode.NY,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )
        val aktivitetslogg = Aktivitetslogg()
        oppdrag.overfør(aktivitetslogg, 1.januar, "Sara Saksbehandler")
        assertTrue(aktivitetslogg.behov().isNotEmpty())
    }

    @Test
    fun `lagrer avstemmingsnøkkel, overføringstidspunkt og status på oppdraget ved akseptert ubetaling`() {
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 17.januar,
                    tom = 31.januar,
                    endringskode = Endringskode.NY,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )
        val avstemmingsnøkkel: Long = 1235
        val overføringstidspunkt = LocalDateTime.now()

        oppdrag.lagreOverføringsinformasjon(UtbetalingHendelseAdapter(UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "1234",
            fødselsnummer = "1234",
            orgnummer = "5678",
            fagsystemId = oppdrag.fagsystemId(),
            utbetalingId = "7894",
            avstemmingsnøkkel = avstemmingsnøkkel,
            overføringstidspunkt = overføringstidspunkt,
            melding = "foo",
            status = Oppdragstatus.AKSEPTERT
        )))

        assertEquals(avstemmingsnøkkel, oppdrag.inspektør.avstemmingsnøkkel)
        assertEquals(overføringstidspunkt, oppdrag.inspektør.overføringstidspunkt)
        assertEquals(Oppdragstatus.AKSEPTERT, oppdrag.inspektør.status())
    }

    @Test
    fun `overskriver ikke overføringstidspunkt`() {
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 17.januar,
                    tom = 31.januar,
                    endringskode = Endringskode.NY,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )
        val avstemmingsnøkkel: Long = 1235
        val overføringstidspunkt = LocalDateTime.now()

        oppdrag.lagreOverføringsinformasjon(UtbetalingHendelseAdapter(
            UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "1234",
            fødselsnummer = "1234",
            orgnummer = "5678",
            fagsystemId = oppdrag.fagsystemId(),
            utbetalingId = "7894",
            avstemmingsnøkkel = avstemmingsnøkkel,
            overføringstidspunkt = overføringstidspunkt,
            melding = "foo",
            status = Oppdragstatus.AKSEPTERT
        )))

        val aksepteringstidspunkt = overføringstidspunkt.plusSeconds(3)
        oppdrag.lagreOverføringsinformasjon(UtbetalingHendelseAdapter(UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "1234",
            fødselsnummer = "1234",
            orgnummer = "5678",
            fagsystemId = oppdrag.fagsystemId(),
            utbetalingId = "7894",
            avstemmingsnøkkel = avstemmingsnøkkel,
            overføringstidspunkt = aksepteringstidspunkt,
            melding = "foo",
            status = Oppdragstatus.AKSEPTERT
        )))

        assertEquals(avstemmingsnøkkel, oppdrag.inspektør.avstemmingsnøkkel)
        assertEquals(overføringstidspunkt, oppdrag.inspektør.overføringstidspunkt)
        assertEquals(Oppdragstatus.AKSEPTERT, oppdrag.inspektør.status())
    }

    @Test
    fun `lagrer ikke avstemmingsnøkkel, overførsingstidspunkt og status på annen fagsystemId`() {
        val oppdrag = Oppdrag(
            "mottaker", Fagområde.Sykepenger, listOf(
                Utbetalingslinje(
                    fom = 17.januar,
                    tom = 31.januar,
                    endringskode = Endringskode.NY,
                    aktuellDagsinntekt = 1000,
                    beløp = 1000,
                    grad = 100
                )

            ), sisteArbeidsgiverdag = 16.januar
        )

        oppdrag.lagreOverføringsinformasjon(UtbetalingHendelseAdapter(
            UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "1234",
            fødselsnummer = "1234",
            orgnummer = "5678",
            fagsystemId = "${UUID.randomUUID()}",
            utbetalingId = "7894",
            avstemmingsnøkkel = 6666,
            overføringstidspunkt = LocalDateTime.now(),
            melding = "foo",
            status = Oppdragstatus.AKSEPTERT
        )))

        oppdrag.lagreOverføringsinformasjon(UtbetalingHendelseAdapter(UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "1234",
            fødselsnummer = "1234",
            orgnummer = "5678",
            fagsystemId = "${UUID.randomUUID()}",
            utbetalingId = "7894",
            avstemmingsnøkkel = 6666,
            overføringstidspunkt = LocalDateTime.now(),
            melding = "foo",
            status = Oppdragstatus.AKSEPTERT
        )))

        assertNull(oppdrag.inspektør.avstemmingsnøkkel)
        assertNull(oppdrag.inspektør.overføringstidspunkt)
        assertNull(oppdrag.inspektør.status())
    }
}
