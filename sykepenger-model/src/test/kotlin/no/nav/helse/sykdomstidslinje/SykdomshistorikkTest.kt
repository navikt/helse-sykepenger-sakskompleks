package no.nav.helse.sykdomstidslinje

import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.NySøknad
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SendtSøknad
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogger
import no.nav.helse.person.SykdomshistorikkVisitor
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class SykdomshistorikkTest {

    companion object {
        internal const val UNG_PERSON_FNR_2018 = "12020052345"
    }

    private lateinit var inspektør: HistorikkInspektør

    private lateinit var historikk: Sykdomshistorikk
    @BeforeEach
    internal fun initialiser() {
        historikk = Sykdomshistorikk()
    }

    @Test
    internal fun `NySøknad mottatt`() {
        historikk.håndter(nySøknad(Triple(1.januar, 5.januar, 100)))
        assertEquals(1, historikk.size)
        assertEquals(5, historikk.sykdomstidslinje().length())
    }

    @Test
    internal fun `SendtSøknad mottatt`() {
        historikk.håndter(nySøknad(Triple(8.januar, 12.januar, 100)))
        historikk.håndter(
            sendtSøknad(
                SendtSøknad.Periode.Sykdom(8.januar, 10.januar, 100),
                SendtSøknad.Periode.Egenmelding(2.januar, 3.januar)
            )
        )
        val inspektør = HistorikkInspektør(historikk)
        assertEquals(2, historikk.size)
        assertEquals(11, historikk.sykdomstidslinje().length())
        assertEquals(5, inspektør.hendelseSykdomstidslinje[1].length())
        assertEquals(5, inspektør.beregnetSykdomstidslinjer[1].length())
        assertEquals(9, inspektør.hendelseSykdomstidslinje[0].length())
        assertEquals(11, inspektør.beregnetSykdomstidslinjer[0].length())
    }

    @Test
    internal fun `Håndterer Ubestemt dag`() {
        historikk.håndter(nySøknad(Triple(8.januar, 12.januar, 100)))
        sendtSøknad(
            SendtSøknad.Periode.Utdanning(9.januar, 12.januar),
            SendtSøknad.Periode.Sykdom(10.januar, 12.januar, 100)
        ).also {
            historikk.håndter(it)
            assertTrue(it.hasErrorsOld())
        }
        assertEquals(2, historikk.size)
        assertEquals(5, historikk.sykdomstidslinje().length())
    }

    @Test
    internal fun `Inntektsmelding mottatt`() {
        historikk.håndter(nySøknad(Triple(8.januar, 12.januar, 100)))
        historikk.håndter(
            sendtSøknad(
                SendtSøknad.Periode.Sykdom(8.januar, 10.januar, 100),
                SendtSøknad.Periode.Egenmelding(2.januar, 3.januar)
            )
        )
        historikk.håndter(
            inntektsmelding(
                arbeidsgiverperioder = listOf(Periode(1.januar, 3.januar), Periode(9.januar, 12.januar)),
                ferieperioder = listOf(Periode(4.januar, 8.januar))
            )
        )
        val inspektør = HistorikkInspektør(historikk)
        assertEquals(3, historikk.size)
        assertEquals(12, historikk.sykdomstidslinje().length())
        assertEquals(5, inspektør.hendelseSykdomstidslinje[2].length())
        assertEquals(5, inspektør.beregnetSykdomstidslinjer[2].length())
        assertEquals(9, inspektør.hendelseSykdomstidslinje[1].length())
        assertEquals(11, inspektør.beregnetSykdomstidslinjer[1].length())
        assertEquals(12, inspektør.hendelseSykdomstidslinje[0].length())
        assertEquals(12, inspektør.beregnetSykdomstidslinjer[0].length())
    }

    @Test
    internal fun `JSON`() {
        val sendtSøknadId = UUID.randomUUID()
        val nySøknadId = UUID.randomUUID()
        historikk.håndter(nySøknad(Triple(8.januar, 12.januar, 100), hendelseId = nySøknadId))
        historikk.håndter(
            sendtSøknad(
                SendtSøknad.Periode.Sykdom(8.januar, 10.januar, 100),
                SendtSøknad.Periode.Egenmelding(2.januar, 3.januar),
                hendelseId = sendtSøknadId
            )
        )
        val inspektør = HistorikkInspektør(historikk)
        assertEquals(2, historikk.size)
        assertEquals(11, historikk.sykdomstidslinje().length())
        assertEquals(5, inspektør.hendelseSykdomstidslinje[1].length())
        assertEquals(5, inspektør.beregnetSykdomstidslinjer[1].length())
        assertEquals(9, inspektør.hendelseSykdomstidslinje[0].length())
        assertEquals(11, inspektør.beregnetSykdomstidslinjer[0].length())
        assertEquals(inspektør.hendelser[0], sendtSøknadId)
        assertEquals(inspektør.hendelser[1], nySøknadId)
    }

    private fun nySøknad(
        vararg sykeperioder: Triple<LocalDate, LocalDate, Int>,
        hendelseId: UUID = UUID.randomUUID()
    ) = NySøknad(
        meldingsreferanseId = hendelseId,
        fnr = UNG_PERSON_FNR_2018,
        aktørId = "12345",
        orgnummer = "987654321",
        sykeperioder = listOf(*sykeperioder),
        aktivitetslogger = Aktivitetslogger(),
        aktivitetslogg = Aktivitetslogg()
    )

    private fun sendtSøknad(
        vararg perioder: SendtSøknad.Periode,
        hendelseId: UUID = UUID.randomUUID()
    ) = SendtSøknad(
        meldingsreferanseId = hendelseId,
        fnr = UNG_PERSON_FNR_2018,
        aktørId = "12345",
        orgnummer = "987654321",
        perioder = listOf(*perioder),
        aktivitetslogger = Aktivitetslogger(),
        aktivitetslogg = Aktivitetslogg(),
        harAndreInntektskilder = false
    )

    private fun inntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        ferieperioder: List<Periode>,
        refusjonBeløp: Double = 1000.00,
        beregnetInntekt: Double = 1000.00,
        førsteFraværsdag: LocalDate = 1.januar,
        refusjonOpphørsdato: LocalDate = 1.januar,
        endringerIRefusjon: List<LocalDate> = emptyList()
    ) = Inntektsmelding(
        meldingsreferanseId = UUID.randomUUID(),
        refusjon = Inntektsmelding.Refusjon(refusjonOpphørsdato, refusjonBeløp, endringerIRefusjon),
        orgnummer = "88888888",
        fødselsnummer = "12020052345",
        aktørId = "100010101010",
        førsteFraværsdag = førsteFraværsdag,
        beregnetInntekt = beregnetInntekt,
        arbeidsgiverperioder = arbeidsgiverperioder,
        ferieperioder = ferieperioder,
        aktivitetslogger = Aktivitetslogger(),
        aktivitetslogg = Aktivitetslogg()
    )

    private class HistorikkInspektør(sykdomshistorikk: Sykdomshistorikk) : SykdomshistorikkVisitor {
        internal val hendelseSykdomstidslinje = mutableListOf<ConcreteSykdomstidslinje>()
        internal val beregnetSykdomstidslinjer = mutableListOf<ConcreteSykdomstidslinje>()
        internal val hendelser = mutableListOf<UUID>()

        init {
            sykdomshistorikk.accept(this)
        }

        override fun preVisitComposite(tidslinje: CompositeSykdomstidslinje) {
            if (hendelseSykdomstidslinje.size == beregnetSykdomstidslinjer.size) {
                hendelseSykdomstidslinje.add(tidslinje)
            } else {
                beregnetSykdomstidslinjer.add(tidslinje)
            }
        }

        override fun preVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element) {
            hendelser.add(element.hendelseId)
        }
    }
}
