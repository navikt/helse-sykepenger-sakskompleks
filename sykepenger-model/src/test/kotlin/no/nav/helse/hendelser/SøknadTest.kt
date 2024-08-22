package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.NullObserver
import no.nav.helse.februar
import no.nav.helse.hendelser.Søknad.Merknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Papirsykmelding
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Permisjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Utlandsopphold
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.november
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg.AktivitetException
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_44
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.ProblemDag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class SøknadTest {

    private companion object {
        private const val UNG_PERSON_FNR_2018 = "12029240045"
        private val ungPersonFnr2018Hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = "12345",
            personidentifikator = UNG_PERSON_FNR_2018.somPersonidentifikator(),
            organisasjonsnummer = "987654321"
        )
        private const val FYLLER_18_ÅR_2_NOVEMBER = "02110075045"
        private val november2 = 2.november(2000)
        private val fyller18År2NovemberHendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = "12345",
            personidentifikator = FYLLER_18_ÅR_2_NOVEMBER.somPersonidentifikator(),
            organisasjonsnummer = "987654321"
        )
    }

    private lateinit var aktivitetslogg: Aktivitetslogg
    private lateinit var søknad: Søknad

    @Test
    fun `søknad med bare sykdom`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent))
        assertFalse(søknad.valider(null, MaskinellJurist()).harFunksjonelleFeilEllerVerre())
        assertEquals(10, søknad.sykdomstidslinje().count())
    }

    @Test
    fun `søknad med ferie`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), Ferie(2.januar, 4.januar))
        assertFalse(søknad.valider(null, MaskinellJurist()).harVarslerEllerVerre())
        assertEquals(10, søknad.sykdomstidslinje().count())
    }

    @Test
    fun `søknad med utlandsopphold`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), Utlandsopphold(2.januar, 4.januar))
        assertTrue(søknad.valider(null, MaskinellJurist()).harVarslerEllerVerre())
        assertEquals(10, søknad.sykdomstidslinje().count())
    }

    @Test
    fun `søknad med overlappende ferie og permisjon`() {
        søknad(Sykdom(1.januar, 1.januar, 100.prosent), Ferie(1.januar, 1.januar), Permisjon(1.januar, 1.januar))
        assertEquals(Dag.Feriedag::class, søknad.sykdomstidslinje()[1.januar]::class)
    }

    @Test
    fun `søknad med ferie som inneholder utlandsopphold`() {
        `utlandsopphold og ferie`(Ferie(2.januar, 4.januar), Utlandsopphold(2.januar, 4.januar), false)
        `utlandsopphold og ferie`(Ferie(2.januar, 6.januar), Utlandsopphold(2.januar, 4.januar), false)
        `utlandsopphold og ferie`(Ferie(1.januar, 4.januar), Utlandsopphold(2.januar, 4.januar), false)
        `utlandsopphold og ferie`(Ferie(1.januar, 6.januar), Utlandsopphold(2.januar, 4.januar), false)
        `utlandsopphold og ferie`(Ferie(1.januar, 3.januar), Utlandsopphold(2.januar, 4.januar), true)
        `utlandsopphold og ferie`(Ferie(3.januar, 7.januar), Utlandsopphold(2.januar, 4.januar), true)
        `utlandsopphold og ferie`(Ferie(1.januar, 1.januar), Utlandsopphold(2.januar, 4.januar), true)
        `utlandsopphold og ferie`(Ferie(5.januar, 9.januar), Utlandsopphold(2.januar, 4.januar), true)
    }

    private fun `utlandsopphold og ferie`(ferie: Ferie, utlandsopphold: Utlandsopphold, skalHaWarning: Boolean) {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), ferie, utlandsopphold)
        assertEquals(skalHaWarning, søknad.valider(null, MaskinellJurist()).harVarslerEllerVerre())
        assertEquals(10, søknad.sykdomstidslinje().count())
    }

    @Test
    fun `17 år på søknadstidspunkt gir error`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), hendelsefabrikk = fyller18År2NovemberHendelsefabrikk, sendtTilNAVEllerArbeidsgiver = 1.november)
        assertTrue(søknad.forUng(november2.alder))
        assertTrue(søknad.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `18 år på søknadstidspunkt gir ikke error`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), hendelsefabrikk = fyller18År2NovemberHendelsefabrikk, sendtTilNAVEllerArbeidsgiver = 2.november)
        assertFalse(søknad.forUng(november2.alder))
        assertFalse(søknad.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `søknad med permisjon`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), Permisjon(5.januar, 10.januar))
        assertFalse(søknad.valider(null, MaskinellJurist()).harFunksjonelleFeilEllerVerre())
        assertEquals(10, søknad.sykdomstidslinje().count())
    }

    @Test
    fun `søknad med permisjon før perioden`() {
        søknad(Sykdom(5.januar, 10.januar, 100.prosent), Permisjon(1.januar, 10.januar))
        assertTrue(søknad.valider(null, MaskinellJurist()).harVarslerEllerVerre())
        assertEquals(5.januar til 10.januar, søknad.periode())
        assertEquals(6, søknad.sykdomstidslinje().count())
    }

    @Test
    fun `søknad med papirsykmelding utenfor søknadsperioden`() {
        assertThrows<AktivitetException> { søknad(Sykdom(1.januar, 10.januar, 100.prosent), Papirsykmelding(11.januar, 16.januar)) }
    }

    @Test
    fun `søknad med papirsykmelding`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent), Papirsykmelding(1.januar, 10.januar))
        assertTrue(søknad.valider(null, MaskinellJurist()).harFunksjonelleFeilEllerVerre())
        assertEquals(10, søknad.sykdomstidslinje().count())
        assertEquals(10, søknad.sykdomstidslinje().filterIsInstance<ProblemDag>().size)
    }

    @Test
    fun `sykdomsgrad under 100 støttes`() {
        søknad(Sykdom(1.januar, 10.januar, 50.prosent))
        assertFalse(søknad.valider(null, MaskinellJurist()).harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `sykdom faktiskgrad under 100 støttes`() {
        søknad(Sykdom(1.januar, 10.januar, 100.prosent, 50.prosent))
        assertFalse(søknad.valider(null, MaskinellJurist()).harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `ferie foran sykdomsvindu`() {
        søknad(Sykdom(1.februar, 10.februar, 100.prosent), Ferie(20.januar, 31.januar))
        assertFalse(søknad.valider(null, MaskinellJurist()).harVarslerEllerVerre())
        assertEquals(1.februar, søknad.sykdomstidslinje().førsteDag())
    }

    @Test
    fun `ferie etter sykdomsvindu - ikke et realistisk scenario`() {
        assertThrows<AktivitetException> { søknad(Sykdom(1.januar, 10.januar, 100.prosent), Ferie(2.januar, 16.januar)) }
    }

    @Test
    fun `permisjon ligger utenfor sykdomsvindu`() {
        assertThrows<AktivitetException> { søknad(Sykdom(1.januar, 10.januar, 100.prosent), Permisjon(2.januar, 16.januar)) }
    }

    @Test
    fun `arbeidag ligger utenfor sykdomsvindu`() {
        assertThrows<AktivitetException> { søknad(Sykdom(1.januar, 10.januar, 100.prosent), Arbeid(2.januar, 16.januar)) }
    }

    @Test
    fun `søknad uten andre inntektskilder`() {
        søknad(Sykdom(5.januar, 12.januar, 100.prosent), andreInntektskilder = false)
        assertFalse(søknad.valider(null, MaskinellJurist()).harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `må ha perioder`() {
        assertThrows<AktivitetException> { søknad() }
    }

    @Test
    fun `må ha sykdomsperioder`() {
        assertThrows<AktivitetException> { søknad(Ferie(2.januar, 16.januar)) }
    }

    @Test
    fun `angitt arbeidsgrad kan ikke føre til sykegrad høyere enn graden fra sykmelding`() {
        assertThrows<IllegalStateException> { Sykdom(1.januar, 31.januar, 20.prosent, 79.prosent) }
    }

    @Test
    fun `angitt arbeidsgrad kan føre til lavere sykegrad enn graden fra sykmelding`() {
        søknad(Sykdom(1.januar, 31.januar, 20.prosent, 81.prosent))
        søknad.valider(null, MaskinellJurist())
        assertFalse(søknad.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `angitt arbeidsgrad kan føre til lik sykegrad som graden fra sykmelding`() {
        søknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent))
        søknad.valider(null, MaskinellJurist())
        assertFalse(søknad.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `søknad uten permittering får ikke warning`() {
        søknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent))
        søknad.valider(null, MaskinellJurist())
        assertFalse(søknad.harVarslerEllerVerre())
    }

    @Test
    fun `søknad med permittering får warning`() {
        søknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent), permittert = true)
        søknad.valider(null, MaskinellJurist())
        assertTrue(søknad.harVarslerEllerVerre())
    }

    @Test
    fun `søknad uten tilbakedateringmerknad får ikke warning`() {
        søknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent))
        søknad.valider(null, MaskinellJurist())
        assertFalse(søknad.harVarslerEllerVerre())
    }

    @ParameterizedTest
    @ValueSource(strings = ["UGYLDIG_TILBAKEDATERING", "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER", "UNDER_BEHANDLING", "DELVIS_GODKJENT"])
    fun `søknad med tilbakedateringmerknad får warning`(merknad: String) {
        søknad(Sykdom(1.januar, 31.januar, 20.prosent, 80.prosent), merknaderFraSykmelding = listOf(Merknad(merknad)))
        søknad.valider(null, MaskinellJurist())
        assertTrue(søknad.harVarslerEllerVerre())
        aktivitetslogg.assertVarsel(RV_SØ_3)
    }

    @Test
    fun `søknadsturnering for nye dagtyper`() {
        søknad(Arbeid(15.januar, 31.januar), Sykdom(1.januar, 31.januar, 100.prosent))

        assertEquals(10, søknad.sykdomstidslinje().filterIsInstance<Sykedag>().size)
        assertEquals(4, søknad.sykdomstidslinje().filterIsInstance<SykHelgedag>().size)
        assertEquals(13, søknad.sykdomstidslinje().filterIsInstance<Arbeidsdag>().size)
        assertEquals(4, søknad.sykdomstidslinje().filterIsInstance<FriskHelgedag>().size)
    }

    @Test
    fun `legger på warning om søknad inneholder foreldete dager`() {
        søknad(Sykdom(1.januar, 1.mai, 100.prosent))
        søknad.valider(null, MaskinellJurist())
        assertEquals(1, søknad.kontekster().size)
        assertTrue(søknad.harVarslerEllerVerre())
        assertFalse(søknad.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `ikke jobbet siste 14 dager i annet arbeidsforhold`() {
        søknad(Sykdom(1.januar, 20.januar, 100.prosent), ikkeJobbetIDetSisteFraAnnetArbeidsforhold = true)
        søknad.valider(null, NullObserver)
        aktivitetslogg.assertVarsel(RV_SØ_44)
    }

    @Test
    fun `jobbet siste 14 dager i annet arbeidsforhold`() {
        søknad(Sykdom(1.januar, 20.januar, 100.prosent), ikkeJobbetIDetSisteFraAnnetArbeidsforhold = false)
        søknad.valider(null, NullObserver)
        aktivitetslogg.assertIngenVarsler()
    }

    private fun søknad(
        vararg perioder: Søknadsperiode,
        andreInntektskilder: Boolean = false,
        permittert: Boolean = false,
        merknaderFraSykmelding: List<Merknad> = emptyList(),
        hendelsefabrikk: ArbeidsgiverHendelsefabrikk = ungPersonFnr2018Hendelsefabrikk,
        sendtTilNAVEllerArbeidsgiver: LocalDate? = null,
        egenmeldinger: List<Periode> = emptyList(),
        ikkeJobbetIDetSisteFraAnnetArbeidsforhold: Boolean = false
    ) {
        aktivitetslogg = Aktivitetslogg()
        søknad = hendelsefabrikk.lagSøknad(
            perioder = perioder,
            andreInntektskilder = andreInntektskilder,
            ikkeJobbetIDetSisteFraAnnetArbeidsforhold = ikkeJobbetIDetSisteFraAnnetArbeidsforhold,
            sendtTilNAVEllerArbeidsgiver = sendtTilNAVEllerArbeidsgiver ?: Søknadsperiode.søknadsperiode(perioder.toList())?.endInclusive ?: LocalDate.now(),
            permittert = permittert,
            merknaderFraSykmelding = merknaderFraSykmelding,
            sykmeldingSkrevet = LocalDateTime.now(),
            aktivitetslogg = aktivitetslogg,
            egenmeldinger = egenmeldinger
        )
    }
}
