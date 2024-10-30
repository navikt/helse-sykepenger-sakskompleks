package no.nav.helse.hendelser

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.desember
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding.Refusjon.EndringIRefusjon
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_3
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertInfo
import no.nav.helse.spleis.e2e.assertIngenFunksjonellFeil
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.ArbeidsgiverHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsgiverdag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

internal class InntektsmeldingTest {

    private val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
        aktørId = "100010101010",
        personidentifikator = Personidentifikator("12029240045"),
        organisasjonsnummer = "88888888"
    )
    private lateinit var aktivitetslogg: Aktivitetslogg
    private lateinit var inntektsmelding: Inntektsmelding
    private lateinit var dager: DagerFraInntektsmelding

    @Test
    fun `inntektsmelding uten arbeidsgiverperiode og førsteFraværsdag er null`() {
        assertThrows<IllegalStateException> { inntektsmelding(emptyList(), førsteFraværsdag = null) }
    }

    @Test
    fun `periode dersom første fraværsdag er kant i kant med arbeidsgiverperioden`() {
        inntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 17.januar)
        assertEquals(1.januar til 16.januar, dager.inspektør.periode)
    }

    @Test
    fun `periode dersom første fraværsdag er etter arbeidsgiverperioden`() {
        inntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 18.januar)
        assertEquals(1.januar til 16.januar, dager.inspektør.periode)
    }

    @Test
    fun `periode dersom første fraværsdag er i arbeidsgiverperioden`() {
        inntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 16.januar)
        assertEquals(1.januar til 16.januar, dager.inspektør.periode)
    }

    @Test
    fun `periode dersom første fraværsdag er null`() {
        inntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = null)
        assertEquals(1.januar til 16.januar, dager.inspektør.periode)
    }

    @Test
    fun `trimme inntektsmelding forbi tom`() {
        inntektsmelding(listOf(1.januar til 16.januar))
        dager.vurdertTilOgMed(17.januar)
        assertNull(dager.inspektør.periode)
        assertEquals(emptySet<LocalDate>(), dager.inspektør.gjenståendeDager)
    }

    @Test
    fun `inntektsmelding hvor førsteFraværsdag er null`() {
        inntektsmelding(listOf(Periode(1.januar, 2.januar)), førsteFraværsdag = null)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje()
        assertEquals(1.januar, nyTidslinje?.periode()?.start)
        assertEquals(2.januar, nyTidslinje?.periode()?.endInclusive)
    }

    @Test
    fun `padder med arbeidsdager i forkant av arbeidsgiverperiode`() {
        inntektsmelding(listOf(3.januar til 18.januar), førsteFraværsdag = 3.januar)
        val tidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, tidslinje.førsteDag())
        assertTrue(tidslinje[1.januar] is Arbeidsdag)
        assertTrue(tidslinje[2.januar] is Arbeidsdag)
        assertTrue(tidslinje[3.januar] is Arbeidsgiverdag)
    }

    @Test
    fun `padder ikke med arbeidsdager i forkant av første fraværsdag uten arbeidsgiverperiode`() {
        inntektsmelding(emptyList(), førsteFraværsdag = 3.januar)
        val tidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje()
        assertNull(tidslinje)
    }

    @Test
    fun `padder ikke med arbeidsdager mellom siste arbeidsgiverperiode og første fraværsdag`() {
        inntektsmelding(
            listOf(
                1.januar til 7.januar,
                10.januar til 18.januar
            ), førsteFraværsdag = 25.januar
        )
        val tidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, tidslinje.førsteDag())
        assertEquals(18.januar, tidslinje.sisteDag())
        assertTrue(tidslinje[1.januar] is Arbeidsgiverdag)
        assertFalse(tidslinje[19.januar] is Arbeidsdag)
        assertFalse(tidslinje[24.januar] is Arbeidsdag)
        assertTrue(tidslinje[25.januar] is UkjentDag)
    }

    @Test
    fun `padder ikke med arbeidsdager mellom dato og første fraværsdag`() {
        inntektsmelding(
            listOf(
                1.januar til 7.januar,
                10.januar til 18.januar
            ), førsteFraværsdag = 25.januar
        )
        val tidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 20.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, tidslinje.førsteDag())
        assertEquals(18.januar, tidslinje.sisteDag())
        assertTrue(tidslinje[1.januar] is Arbeidsgiverdag)
        assertFalse(tidslinje[19.januar] is Arbeidsdag)
        assertFalse(tidslinje[20.januar] is FriskHelgedag)
        assertFalse(tidslinje[24.januar] is Arbeidsdag)
        assertTrue(tidslinje[25.januar] is UkjentDag)
    }

    @Test
    fun `padder med arbeidsdager før arbeidsgiverperiode, men ikke mellom dato og første fraværsdag`() {
        inntektsmelding(
            listOf(
                1.januar til 7.januar,
                10.januar til 18.januar
            ), førsteFraværsdag = 25.januar
        )
        val tidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 31.desember(2017) til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(31.desember(2017), tidslinje.førsteDag())
        assertEquals(18.januar, tidslinje.sisteDag())
        assertTrue(tidslinje[31.desember(2017)] is FriskHelgedag)
        assertFalse(tidslinje[19.januar] is Arbeidsdag)
        assertFalse(tidslinje[20.januar] is FriskHelgedag)
        assertFalse(tidslinje[24.januar] is Arbeidsdag)
        assertTrue(tidslinje[25.januar] is UkjentDag)
    }

    @Test
    fun `skjæringstidspunkt skal ikke bli tidligere enn første dag i siste arbeidsgiverperiode`() {
        inntektsmelding(
            listOf(
                Periode(1.januar, 2.januar),
                Periode(10.januar, 12.januar),
                Periode(15.januar, 24.januar)
            ), førsteFraværsdag = 1.januar
        )
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 10.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, nyTidslinje.periode()?.start)
        assertEquals(24.januar, nyTidslinje.periode()?.endInclusive)
        assertEquals(15.januar, nyTidslinje.sisteSkjæringstidspunkt())
    }

    @Test
    fun `ferie sammenhengende før siste arbeidsgiverperiode påvirker ikke skjæringstidspunkt`() {
        inntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar, 2.januar), Periode(15.januar, 17.januar)),
            førsteFraværsdag = 1.januar
        )
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 10.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, nyTidslinje.periode()?.start)
        assertEquals(17.januar, nyTidslinje.periode()?.endInclusive)
        assertEquals(15.januar, nyTidslinje.sisteSkjæringstidspunkt())
    }

    @Test
    fun `ferie og helg sammenhengende med to arbeidsgiverperioder slår sammen periodene`() {
        inntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar, 5.januar), Periode(15.januar, 17.januar)),
            førsteFraværsdag = 1.januar
        )
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, nyTidslinje.periode()?.start)
        assertEquals(17.januar, nyTidslinje.periode()?.endInclusive)
    }

    @Test
    fun `to ferieperioder med gap, som er sammenhengende med hver sin arbeidsgiverperiode, påvirker ikke skjæringstidspunkt`() {
        inntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar, 5.januar), Periode(15.januar, 17.januar)),
            førsteFraværsdag = 1.januar
        )
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(1.januar, nyTidslinje.periode()?.start)
        assertEquals(17.januar, nyTidslinje.periode()?.endInclusive)
        assertEquals(15.januar, nyTidslinje.sisteSkjæringstidspunkt())
    }

    @Test
    fun `inntektsmelding uten arbeidsgiverperiode med førsteFraværsdag satt`() {
        inntektsmelding(emptyList(), førsteFraværsdag = 1.januar)
        assertNull(dager.inspektør.periode)
        assertEquals(emptySet<LocalDate>(), dager.inspektør.gjenståendeDager)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje()
        dager.validerArbeidsgiverperiode(aktivitetslogg, januar, null)
        aktivitetslogg.assertIngenVarsler()
        aktivitetslogg.assertIngenFunksjonellFeil()
        assertNull(nyTidslinje)
    }

    @Test
    fun `inntektsmelding uten arbeidsgiverperiode med førsteFraværsdag & begrunnelseForReduksjonEllerIkkeUtbetalt satt`() {
        inntektsmelding(
            emptyList(),
            førsteFraværsdag = 1.januar,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FiskerMedHyre"
        )
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje()
        dager.valider(aktivitetslogg, januar)
        aktivitetslogg.assertInfo("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden på grunn av: FiskerMedHyre")
        aktivitetslogg.assertFunksjonellFeil("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden")
        assertNull(nyTidslinje)
    }

    @Test
    fun `sykdom med en antatt arbeidsdag`() {
        inntektsmelding(listOf(Periode(1.januar, 2.januar), Periode(4.januar, 5.januar)))
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(Arbeidsdag::class, nyTidslinje[3.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[1.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[2.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[4.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[5.januar]::class)
    }

    @Test
    fun `arbeidsgiverperiode med gap`() {
        inntektsmelding(listOf(Periode(1.januar, 2.januar), Periode(4.januar, 5.januar)), førsteFraværsdag = 4.januar)

        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[1.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[2.januar]::class)
        assertEquals(Arbeidsdag::class, nyTidslinje[3.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[4.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[5.januar]::class)
    }

    @Test
    fun `første fraværsdag etter arbeidsgiverperiode blir ikke arbeidsgiverdag`() {
        inntektsmelding(listOf(Periode(1.januar, 1.januar)), førsteFraværsdag = 3.januar)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[1.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[2.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[3.januar]::class)
    }

    @Test
    fun `arbeidsgiverperioden i inntektsmelding kan være tom`() {
        inntektsmelding(emptyList())
        val sykdomstidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje()
        assertNull(sykdomstidslinje)
    }

    @Test
    fun `arbeidgiverperioden kan ha overlappende perioder`() {
        inntektsmelding(listOf(Periode(1.januar, 2.januar), Periode(4.januar, 5.januar), Periode(3.januar, 4.januar)))
        dager.validerArbeidsgiverperiode(aktivitetslogg, 1.januar til 6.januar, null)
        aktivitetslogg.assertIngenFunksjonellFeil()
    }

    @Test
    fun `helg i opphold i arbeidsgiverperioden skal være helgedager`() {
        inntektsmelding(listOf(Periode(1.januar, 4.januar), Periode(9.januar, 10.januar)))

        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[1.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[2.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[3.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[4.januar]::class)
        assertEquals(Arbeidsdag::class, nyTidslinje[5.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[6.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[7.januar]::class)
        assertEquals(Arbeidsdag::class, nyTidslinje[8.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[9.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[10.januar]::class)
    }

    @Test
    fun `bruker første fraværsdag som TOM hvis både ferieperioder og arbeidsgiverperioder i inntektsmeldingen er tomme`() {
        inntektsmelding(emptyList(), førsteFraværsdag = 2.januar)
        assertNull(dager.inspektør.periode)
        assertEquals(emptySet<LocalDate>(), dager.inspektør.gjenståendeDager)
        val sykdomstidslinje = dager.bitAvInntektsmelding(aktivitetslogg, 2.januar til 31.januar)?.sykdomstidslinje()
        assertNull(sykdomstidslinje)
    }

    @Test
    fun `FiskerMedHyre satt som begrunnelseForReduksjonEllerIkkeUtbetalt i inntektsmelding kastes ut`() {
        inntektsmelding(
            listOf(Periode(1.januar, 10.januar)),
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FiskerMedHyre"
        )
        dager.valider(aktivitetslogg, 1.januar til 12.januar)
        aktivitetslogg.assertFunksjonellFeil("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden")
        aktivitetslogg.assertInfo("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden på grunn av: FiskerMedHyre")
    }

    @Test
    fun `begrunnelseForReduksjonEllerIkkeUtbetalt som tom String i inntektsmelding gir ikke warning`() {
        inntektsmelding(
            listOf(Periode(1.januar, 10.januar)),
            begrunnelseForReduksjonEllerIkkeUtbetalt = ""
        )
       dager.validerArbeidsgiverperiode(aktivitetslogg, 1.januar til 10.januar, null)
       assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `opphold mellom arbeidsgiverperiode og første fraværsdag i helg på søndag gir friskhelgedag`() {
        inntektsmelding(
            listOf(Periode(5.januar, 20.januar)),
            førsteFraværsdag = 22.januar
        )
        assertEquals(5.januar til 21.januar, dager.inspektør.periode)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 5.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(5.januar, nyTidslinje.sisteSkjæringstidspunkt())
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[5.januar]::class)
        assertEquals(ArbeidsgiverHelgedag::class, nyTidslinje[20.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[21.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[22.januar]::class)
    }

    @Test
    fun `helg mellom arbeidsgiverperiode`() {
        inntektsmelding(
            listOf(
                1.januar til 3.januar,
                4.januar til 5.januar,
                // 6. og 7. januar er helg
                8.januar til 12.januar,
                13.januar til 18.januar
            ), førsteFraværsdag = 1.januar
        )
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }

        assertEquals(8.januar, nyTidslinje.sisteSkjæringstidspunkt())
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[5.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[6.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[7.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[8.januar]::class)
    }

    @Test
    fun `opphold mellom arbeidsgiverperiode og første fraværsdag i helg på lørdag gir friskhelgedag`() {
        inntektsmelding(
            listOf(Periode(4.januar, 19.januar)),
            førsteFraværsdag = 21.januar
        )
        assertEquals(4.januar til 20.januar, dager.inspektør.periode)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 4.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(4.januar, nyTidslinje.sisteSkjæringstidspunkt())
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[4.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[19.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[20.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[21.januar]::class)
    }

    @Test
    fun `opphold mellom arbeidsgiverperiode og første fraværsdag i helg på lørdag og søndag gir friskhelgedager`() {
        inntektsmelding(
            listOf(Periode(4.januar, 19.januar)),
            førsteFraværsdag = 22.januar
        )
        assertEquals(4.januar til 21.januar, dager.inspektør.periode)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 4.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(4.januar, nyTidslinje.sisteSkjæringstidspunkt())
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[4.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[19.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[20.januar]::class)
        assertEquals(FriskHelgedag::class, nyTidslinje[21.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[22.januar]::class)
    }


    @Test
    fun `opphold mellom arbeidsgiverperiode og første fraværsdag, arbeidsgiverperiode slutter på torsdag`() {
        inntektsmelding(
            listOf(Periode(3.januar, 18.januar)),
            førsteFraværsdag = 22.januar
        )
        assertEquals(3.januar til 18.januar, dager.inspektør.periode)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 3.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(3.januar, nyTidslinje.sisteSkjæringstidspunkt())
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[3.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[18.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[19.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[20.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[21.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[22.januar]::class)
    }

    @Test
    fun `opphold mellom arbeidsgiverperiode og første fraværsdag, første fraværsdag er tirsdag`() {
        inntektsmelding(
            listOf(Periode(4.januar, 19.januar)),
            førsteFraværsdag = 23.januar
        )
        assertEquals(4.januar til 19.januar, dager.inspektør.periode)
        val nyTidslinje = dager.bitAvInntektsmelding(Aktivitetslogg(), 4.januar til 31.januar)?.sykdomstidslinje() ?: fail { "forventet sykdomstidslinje" }
        assertEquals(4.januar, nyTidslinje.sisteSkjæringstidspunkt())
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[4.januar]::class)
        assertEquals(Arbeidsgiverdag::class, nyTidslinje[19.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[20.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[21.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[22.januar]::class)
        assertEquals(UkjentDag::class, nyTidslinje[23.januar]::class)
    }

    @Test
    fun `førsteFraværsdag kan være null ved lagring av inntekt`() {
        inntektsmelding(listOf(Periode(1.januar, 16.januar)), førsteFraværsdag = null)
        assertDoesNotThrow { inntektsmelding.addInntekt(Inntektshistorikk(), EmptyLog, emptyList()) }
    }

    @Test
    fun `lagrer inntekt for periodens skjæringstidspunkt dersom det er annerledes enn inntektmeldingens skjæringstidspunkt`() {
        inntektsmelding(
            listOf(Periode(1.januar, 16.januar)),
            refusjonBeløp = 2000.månedlig,
            beregnetInntekt = 2000.månedlig,
            førsteFraværsdag = 3.februar
        )
        val inntektshistorikk = Inntektshistorikk()
        inntektsmelding.addInntekt(inntektshistorikk, Aktivitetslogg(), 1.februar)
        assertEquals(2000.månedlig, inntektshistorikk.avklarSykepengegrunnlag(1.februar, 1.februar, null)?.inspektør?.beløp)
        assertNull(inntektshistorikk.avklarSykepengegrunnlag(3.februar, 3.februar, null))
    }

    @Test
    fun `lagrer inntekt på første dag i arbeidsgiverpeiroden`() {
        inntektsmelding(
            listOf(1.januar til 10.januar, 11.januar til 16.januar),
            refusjonBeløp = 2000.månedlig,
            beregnetInntekt = 2000.månedlig,
            førsteFraværsdag = 1.januar
        )
        val inntektshistorikk = Inntektshistorikk()
        inntektsmelding.addInntekt(inntektshistorikk, EmptyLog, emptyList())
        assertEquals(2000.månedlig, inntektshistorikk.avklarSykepengegrunnlag(1.januar, 1.januar, null)?.inspektør?.beløp)
    }

    @Test
    fun `uenige om arbeidsgiverperiode`() {
        inntektsmelding(listOf(2.januar til 17.januar))
        dager.vurdertTilOgMed(17.januar)
        dager.validerArbeidsgiverperiode(aktivitetslogg, 1.januar til 17.januar, Arbeidsgiverperiode(listOf(1.januar til 16.januar)).apply { kjentDag(17.januar) })
        aktivitetslogg.assertVarsel(RV_IM_3)
    }

    @Test
    fun `uenige om arbeidsgiverperiode med NAV_NO som avsendersystem gir varsel`() {
        inntektsmeldingPortal(listOf(2.januar til 17.januar), inntektsdato = 2.januar)
        dager.vurdertTilOgMed(17.januar)
        dager.validerArbeidsgiverperiode(aktivitetslogg, 1.januar til 17.januar, Arbeidsgiverperiode(listOf(1.januar til 16.januar)).apply { kjentDag(17.januar) })
        aktivitetslogg.assertVarsel(RV_IM_3)
    }

    @Test
    fun `tom arbeidsgiverperiode med NAV_NO som avsendersystem gir ikke varsel`() {
        inntektsmeldingPortal(emptyList(), inntektsdato = 1.januar)
        dager.vurdertTilOgMed(17.januar)
        dager.validerArbeidsgiverperiode(aktivitetslogg, 1.januar til 17.januar, Arbeidsgiverperiode(listOf(1.januar til 16.januar)).apply { kjentDag(17.januar) })
        aktivitetslogg.assertIngenVarsel(RV_IM_3)
    }

    private fun inntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        refusjonBeløp: Inntekt = 1000.månedlig,
        beregnetInntekt: Inntekt = 1000.månedlig,
        førsteFraværsdag: LocalDate? = arbeidsgiverperioder.maxOfOrNull { it.start } ?: 1.januar,
        refusjonOpphørsdato: LocalDate? = null,
        endringerIRefusjon: List<EndringIRefusjon> = emptyList(),
        arbeidsforholdId: String? = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null
    ) {
        aktivitetslogg = Aktivitetslogg()
        inntektsmelding = hendelsefabrikk.lagInntektsmelding(
            refusjon = Inntektsmelding.Refusjon(refusjonBeløp, refusjonOpphørsdato, endringerIRefusjon),
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = beregnetInntekt,
            arbeidsgiverperioder = arbeidsgiverperioder,
            arbeidsforholdId = arbeidsforholdId,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt
        )
        dager = inntektsmelding.dager()
    }

    private fun inntektsmeldingPortal(
        arbeidsgiverperioder: List<Periode>,
        refusjonBeløp: Inntekt = 1000.månedlig,
        beregnetInntekt: Inntekt = 1000.månedlig,
        inntektsdato: LocalDate,
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        førsteFraværsdag: LocalDate? = arbeidsgiverperioder.maxOfOrNull { it.start } ?: 1.januar,
        refusjonOpphørsdato: LocalDate? = null,
        endringerIRefusjon: List<EndringIRefusjon> = emptyList(),
        arbeidsforholdId: String? = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null
    ) {
        aktivitetslogg = Aktivitetslogg()
        inntektsmelding = hendelsefabrikk.lagPortalinntektsmelding(
            refusjon = Inntektsmelding.Refusjon(refusjonBeløp, refusjonOpphørsdato, endringerIRefusjon),
            førsteFraværsdag = førsteFraværsdag,
            inntektsdato = inntektsdato,
            vedtaksperiodeId = vedtaksperiodeId,
            beregnetInntekt = beregnetInntekt,
            arbeidsgiverperioder = arbeidsgiverperioder,
            arbeidsforholdId = arbeidsforholdId,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt
        )
        dager = inntektsmelding.dager()
    }
}
