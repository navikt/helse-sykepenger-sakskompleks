package no.nav.helse.sykdomstidslinje

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.erRettFør
import no.nav.helse.februar
import no.nav.helse.hendelser.BitAvArbeidsgiverperiode
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.inntektsmelding.validert
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.mars
import no.nav.helse.perioder
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.testhelpers.A
import no.nav.helse.testhelpers.AIG
import no.nav.helse.testhelpers.F
import no.nav.helse.testhelpers.FORELDET
import no.nav.helse.testhelpers.H
import no.nav.helse.testhelpers.N
import no.nav.helse.testhelpers.P
import no.nav.helse.testhelpers.S
import no.nav.helse.testhelpers.U
import no.nav.helse.testhelpers.UK
import no.nav.helse.testhelpers.YF
import no.nav.helse.testhelpers.opphold
import no.nav.helse.testhelpers.resetSeed
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SkjæringstidspunktTest {

    @BeforeEach
    fun setup() {
        resetSeed()
    }

    @Test
    fun `skjæringstidspunkter happy cases`() {
        // snodige egenmeldingsdager fra sykmelding
        resetSeed(11.juni(2024))
        assertEquals(15.juni(2024), beregnSkjæringstidspunkt(2.U + 2.A + 1.U + 1.UK + 8.S, 11.juni(2024) til 23.juni(2024)))

        // foreldede dager i forkant av forlengelse
        resetSeed()
        assertEquals(1.januar, beregnSkjæringstidspunkt(31.FORELDET + 28.S, februar))

        // forlengelse med bare sykdom
        resetSeed()
        assertEquals(1.januar, beregnSkjæringstidspunkt(31.S, 20.januar til 31.januar))

        // ferie mellom sykdomsperioder
        resetSeed()
        assertEquals(1.januar, beregnSkjæringstidspunkt(10.S + 10.F + 10.S, 25.januar til 31.januar))

        // ferie mellom arbeid og sykdomsperiode
        resetSeed()
        assertEquals(21.januar, beregnSkjæringstidspunkt(10.A + 10.F + 10.S, 20.januar til 31.januar))

        // skjæringstidspunkt for ukjent dag-hale-periode
        resetSeed()
        assertEquals(1.januar, beregnSkjæringstidspunkt(16.S + 10.opphold, 20.januar til 31.januar))

        // ukjent dager mellom sykdomsperioder
        resetSeed()
        assertEquals(25.januar, beregnSkjæringstidspunkt(20.S + 4.opphold + 7.S, 25.januar til 31.januar))

        // periode med 100 % friskmelding
        resetSeed()
        assertNull(beregnSkjæringstidspunkt(16.S + 15.A, 17.januar til 31.januar))

        // periode friskmelding i halen og snuten
        resetSeed()
        assertEquals(11.januar, beregnSkjæringstidspunkt(10.A + 6.S + 15.A, 1.januar til 31.januar))

        // andre ytelser mellom sykdomsperioder
        resetSeed()
        assertEquals(27.januar, beregnSkjæringstidspunkt(16.S + 10.YF + 5.S, 27.januar til 31.januar))

        // ferie etter opphold
        resetSeed()
        assertNull(beregnSkjæringstidspunkt(16.S + 10.opphold + 5.F, 27.januar til 31.januar))

        // periode med litt tøysete hale
        resetSeed()
        assertEquals(7.januar, beregnSkjæringstidspunkt(2.S + 2.YF + 2.F + 2.S, 1.januar til 8.januar))

        // sykdomsperiode med tøysete hale #2
        resetSeed()
        assertEquals(1.januar, beregnSkjæringstidspunkt(2.S + 1.A + 14.F, 1.januar til 17.januar))

        // sykdomsperiode med tøysete hale #3
        resetSeed()
        assertEquals(1.januar, beregnSkjæringstidspunkt(2.S + 1.AIG + 14.F, 1.januar til 17.januar))

        // sykdomsperiode med andre ytelser og ferie forut
        resetSeed()
        assertEquals(7.januar, beregnSkjæringstidspunkt(2.S + 2.YF + 2.F + 2.S, 7.januar til 8.januar))
    }

    @Test
    fun `skjæringstidspunkt er sykedag, arbeidsgiverdag eller syk helgedag`() {
        assertFørsteDagErSkjæringstidspunkt(1.S)
        assertFørsteDagErSkjæringstidspunkt(1.U)
        assertFørsteDagErSkjæringstidspunkt(1.H)
    }

    @Test
    fun `syk-ferie uten sykmelding-helg-syk regnes som gap`() {
        assertDagenErSkjæringstidspunkt(22.januar, 15.S + 4.AIG + 2.opphold + 1.S)
    }

    @Test
    fun `syk-helg-ferie uten sykmelding-syk regnes ikke som gap`() {
        assertDagenErSkjæringstidspunkt(17.januar, 12.S + 2.opphold + 2.AIG + 1.S)
    }

    @Test
    fun `syk-ferie-helg-syk regnes ikke som gap`() {
        assertFørsteDagErSkjæringstidspunkt(15.S + 4.F + 2.opphold + 1.S)
    }

    @Test
    fun `syk-helg-ferie-syk regnes ikke som gap`() {
        assertFørsteDagErSkjæringstidspunkt(12.S + 2.opphold + 2.F + 1.S)
    }

    @Test
    fun `skjæringstidspunkt er første arbeidsgiverdag, sykedag eller syk helgedag i en sammenhengende periode`() {
        assertFørsteDagErSkjæringstidspunkt(2.S)
        assertFørsteDagErSkjæringstidspunkt(2.U)

        resetSeed(4.januar)
        perioder(2.S, 2.opphold, 2.S) { periode1, _, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.A, 2.F, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.A, 2.P, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.F, 2.S) { periode1, _, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.P, 2.S) { periode1, _, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.opphold, 1.H) { _, _, sisteSykedager ->
            assertFørsteDagErSkjæringstidspunkt(sisteSykedager, this)
        }
        perioder(2.S, 2.opphold, 1.U) { _, _, sisteSykedager ->
            assertFørsteDagErSkjæringstidspunkt(sisteSykedager, this)
        }
        perioder(2.S, 2.A, 2.S, 2.A) { _, _, sisteSykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sisteSykedager, this)
        }
        perioder(2.S, 2.AIG, 2.F, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.F, 2.AIG, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.YF, 2.F, 2.S) { a, b, c, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.F, 2.YF, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.YF, 2.S) { _, _, periode3 ->
            assertFørsteDagErSkjæringstidspunkt(periode3, this)
        }
    }

    @Test
    fun `tidslinjer som slutter med ignorerte dager`() {
        perioder(2.S, 2.F) { periode1, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.P) { periode1, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.YF) { periode1, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
    }

    @Test
    fun `periode starter søndag - har sykdom til fredag før - lørdag er ukjent`() {
        val skjæringstidspunkt = beregnSkjæringstidspunkt(5.S + 1.UK + 1.S, januar)
        assertEquals(1.januar, skjæringstidspunkt)
    }

    @Test
    fun `sykedager etterfulgt av arbeidsdager`() {
        perioder(2.S, 2.A) { sykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sykedager, this)
        }
    }

    @Test
    fun `sykedager etterfulgt av implisittdager`() {
        perioder(2.S, 2.opphold) { sykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sykedager, this)
        }
    }

    @Test
    fun `søknad med arbeidgjenopptatt gir ikke feil`() {
        perioder(2.S, 2.opphold) { sykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sykedager, this)
        }
    }

    @Test
    fun `sykeperiode starter på skjæringstidspunkt`() {
        val søknad = søknad(Søknadsperiode.Sykdom(4.februar(2020), 21.februar(2020), 100.prosent))
        val skjæringstidspunkt = 4.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(20.januar(2020), 20.januar(2020)),
                Periode(4.februar(2020), 18.februar(2020))
            ), førsteFraværsdag = skjæringstidspunkt
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `skjæringstidspunkt er riktig selv om første fraværsdag er satt for tidlig`() {
        val søknad = søknad(Søknadsperiode.Sykdom(4.februar(2020), 21.februar(2020), 100.prosent))
        val skjæringstidspunkt = 4.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(20.januar(2020), 20.januar(2020)),
                Periode(4.februar(2020), 18.februar(2020))
            ), førsteFraværsdag = 20.januar(2020)
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `skjæringstidspunkt er riktig selv om første fraværsdag er satt for sent`() {
        val søknad = søknad(Søknadsperiode.Sykdom(4.februar(2020), 21.februar(2020), 100.prosent))
        val skjæringstidspunkt = 4.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(20.januar(2020), 20.januar(2020)),
                Periode(4.februar(2020), 18.februar(2020))
            ), førsteFraværsdag = 18.februar(2020)
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `sykeperioden starter etter skjæringstidspunkt`() {
        val søknad = søknad(Søknadsperiode.Sykdom(29.januar(2020), 16.februar(2020), 100.prosent))
        val skjæringstidspunkt = 20.januar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(13.januar(2020), 17.januar(2020)),
                Periode(20.januar(2020), 30.januar(2020))
            ), førsteFraværsdag = skjæringstidspunkt
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `arbeidsgiverperiode med enkeltdager før skjæringstidspunkt`() {
        val søknad = søknad(Søknadsperiode.Sykdom(12.februar(2020), 19.februar(2020), 100.prosent))
        val skjæringstidspunkt = 3.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(14.januar(2020), 14.januar(2020)),
                Periode(28.januar(2020), 28.januar(2020)),
                Periode(3.februar(2020), 16.februar(2020))
            ), førsteFraværsdag = skjæringstidspunkt
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `tilstøtende arbeidsgivertidslinjer`() {
        val arbeidsgiver1tidslinje = 14.S
        val arbeidsgiver2tidslinje = 14.S
        assertTrue(arbeidsgiver1tidslinje.sisteDag().erRettFør(arbeidsgiver2tidslinje.førsteDag()))
        assertSkjæringstidspunkt(1.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med helgegap`() {
        val arbeidsgiver1tidslinje = 12.S + 2.opphold
        val arbeidsgiver2tidslinje = 14.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med arbeidsdager i helg`() {
        val arbeidsgiver1tidslinje = 12.S + 1.A
        val arbeidsgiver2tidslinje = 1.A + 14.S
        assertSkjæringstidspunkt(15.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med arbeidsdager og sykedager i helg`() {
        val arbeidsgiver1tidslinje = 14.S
        resetSeed(10.januar)
        val arbeidsgiver2tidslinje = 2.S + 2.A + 7.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 20.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med helgegap og arbeidsdag på fredag`() {
        val arbeidsgiver1tidslinje = 11.S + 1.A + 2.opphold
        val arbeidsgiver2tidslinje = 14.S
        assertSkjæringstidspunkt(15.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med helgegap og arbeidsdag på mandag`() {
        val arbeidsgiver1tidslinje = 12.S + 2.opphold
        val arbeidsgiver2tidslinje = 1.A + 13.S
        assertSkjæringstidspunkt(16.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer overlappende arbeidsdager og sykedager`() {
        val arbeidsgiver1tidslinje = 6.S + 6.A
        resetSeed()
        val arbeidsgiver2tidslinje = 14.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 14.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med gap blir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 7.A
        resetSeed()
        val arbeidsgiver2tidslinje = 14.A + 14.S
        resetSeed()
        val arbeidsgiver3tidslinje = 7.A + 7.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje, arbeidsgiver3tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie som gap blir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 1.F
        val arbeidsgiver2tidslinje = 1.F + 14.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 23.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og arbeidsdag på samme dager, forblir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 2.F + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.A + 7.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(10.januar, 1.januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og arbeidsdag på samme dager, forblir sammenhengende - motsatt rekkefølge på tidslinjer`() {
        val arbeidsgiver1tidslinje = 7.S + 2.A + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.F + 7.S
        assertSkjæringstidspunkt(10.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og ukjentdag på samme dager, forblir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 2.F + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.UK + 7.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(10.januar, 1.januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og ukjentdag på samme dager, forblir sammenhengende - motsatt rekkefølge på tidslinjer`() {
        val arbeidsgiver1tidslinje = 7.S + 2.UK + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.F + 7.S
        assertSkjæringstidspunkt(10.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1.januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie i begynnelsen, med arbeidsdag på samme dager, forblir usammenhengende`() {
        val arbeidsgiver1tidslinje = 7.F + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.A + 7.S
        assertSkjæringstidspunkt(8.januar, 1.januar til 14.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(8.januar, 1.januar til 14.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(8.januar, 1.januar til 14.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `gir kun skjæringstidspunkt som er relevant for perioden`() {
        val tidslinje = 7.S + 2.opphold + 3.S + 5.opphold + 3.F
        assertSkjæringstidspunkt(1.januar, 1.januar til 7.januar, tidslinje)
        assertSkjæringstidspunkt(10.januar, 10.januar til 12.januar, tidslinje)
        assertSkjæringstidspunkt(null, 18.januar til 20.januar, tidslinje)
        assertSkjæringstidspunkt(10.januar, 1.januar til 20.januar, tidslinje)
    }

    @Test
    fun `Sykedager Nav`() {
        val tidslinje = 5.N
        assertFørsteDagErSkjæringstidspunkt(tidslinje)
    }

    @Test
    fun `andre ytelser etter andre ytelser etter sykedag skal bruke skjæringstidspunktet for de første sykedagene`() {
        val tidslinje = 31.S + 28.YF + 31.YF
        assertSkjæringstidspunkt(1.januar, mars, tidslinje)
    }

    @Test
    fun `sykedag etter andre ytelser etter sykedag skal få nytt skjæringstidspunkt`() {
        val tidslinje = 31.S + 28.YF + 31.S
        assertSkjæringstidspunkt(1.mars, 1.januar til 31.mars, tidslinje)
    }

    @Test
    fun `andre ytelser etter sykdom skal bruke sykdommens skjæringstidspunkt`() {
        val tidslinje = 31.S + 28.YF
        assertSkjæringstidspunkt(1.januar, februar, tidslinje)
    }

    private fun assertSkjæringstidspunkt(forventetSkjæringstidspunkt: LocalDate?, periode: Periode, vararg tidslinje: Sykdomstidslinje) {
        assertEquals(forventetSkjæringstidspunkt, Sykdomstidslinje.beregnSkjæringstidspunkt(tidslinje.toList()).sisteOrNull(periode))
    }

    private fun assertSkjæringstidspunkt(
        forventetSkjæringstidspunkt: LocalDate,
        vararg hendelse: SykdomshistorikkHendelse
    ) {
        val a = Sykdomshistorikk()
        hendelse.forEach { a.håndter(UUID.randomUUID(), it.sykdomstidslinje()) }

        val tidslinje = a.sykdomstidslinje()

        val skjæringstidspunkt = beregnSkjæringstidspunkt(tidslinje, tidslinje.periode()!!)
        assertEquals(forventetSkjæringstidspunkt, skjæringstidspunkt) {
            "Forventet skjæringstidspunkt $forventetSkjæringstidspunkt. " +
                "Fikk $skjæringstidspunkt\n" +
                "Tidslinje:\n$tidslinje"
        }
    }

    private fun søknad(vararg perioder: Søknadsperiode): Søknad {
        return hendelsefabrikk.lagSøknad(
            perioder = perioder,
            sendtTilNAVEllerArbeidsgiver = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive
        )
    }

    private fun inntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        refusjonBeløp: Inntekt = INNTEKT_PR_MÅNED,
        beregnetInntekt: Inntekt = INNTEKT_PR_MÅNED,
        førsteFraværsdag: LocalDate = 1.januar,
        refusjonOpphørsdato: LocalDate = 31.desember,
        endringerIRefusjon: List<Inntektsmelding.Refusjon.EndringIRefusjon> = emptyList()
    ): BitAvArbeidsgiverperiode {
        val inntektsmelding = hendelsefabrikk.lagKlassiskInntektsmelding(
            arbeidsgiverperioder = arbeidsgiverperioder,
            beregnetInntekt = beregnetInntekt,
            førsteFraværsdag = førsteFraværsdag,
            refusjon = Inntektsmelding.Refusjon(refusjonBeløp, refusjonOpphørsdato, endringerIRefusjon),
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )
        val aktivitetslogg = Aktivitetslogg()
        inntektsmelding.validert()

        return inntektsmelding.dager().bitAvInntektsmelding(
            aktivitetslogg,
            arbeidsgiverperioder.plusElement(førsteFraværsdag.somPeriode()).periode()!!
        )!!
    }

    private companion object {
        private const val ORGNUMMER = "987654321"
        private const val INNTEKT = 31000.00
        private val INNTEKT_PR_MÅNED = INNTEKT.månedlig
        private val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            organisasjonsnummer = ORGNUMMER
        )

        private fun beregnSkjæringstidspunkt(tidslinje: Sykdomstidslinje, søkeperiode: Periode) =
            Skjæringstidspunkt(tidslinje).sisteOrNull(søkeperiode)

        private fun assertDagenErSkjæringstidspunkt(
            dagen: LocalDate,
            sykdomstidslinje: Sykdomstidslinje
        ) {
            val skjæringstidspunkt = beregnSkjæringstidspunkt(sykdomstidslinje, sykdomstidslinje.periode()!!)
            assertEquals(
                dagen,
                skjæringstidspunkt
            ) { "Forventet $dagen, men fikk $skjæringstidspunkt.\nPeriode: ${sykdomstidslinje.periode()}\nTidslinjen:\n$sykdomstidslinje" }
        }

        private fun assertFørsteDagErSkjæringstidspunkt(sykdomstidslinje: Sykdomstidslinje) {
            val førsteDag = sykdomstidslinje.periode()?.start
            assertDagenErSkjæringstidspunkt(førsteDag!!, sykdomstidslinje)
        }

        private fun assertFørsteDagErSkjæringstidspunkt(
            perioden: Sykdomstidslinje,
            sykdomstidslinje: Sykdomstidslinje
        ) {
            val førsteDag = perioden.periode()?.start ?: fail { "Tom periode" }
            assertNotNull(førsteDag)
            assertDagenErSkjæringstidspunkt(førsteDag, sykdomstidslinje)
        }
    }
}
