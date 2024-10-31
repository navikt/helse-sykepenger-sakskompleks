package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.dsl.SubsumsjonsListLog
import no.nav.helse.dsl.TestPerson
import no.nav.helse.dsl.UgyldigeSituasjonerObservatør
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.februar
import no.nav.helse.gjenopprettFraJSON
import no.nav.helse.gjenopprettFraJSONtekst
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.serde.tilPersonData
import no.nav.helse.serde.tilSerialisertPerson
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.TestObservatør
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

internal abstract class AbstractPersonTest {

    internal companion object {
        val UNG_PERSON_FNR_2018: Personidentifikator = Personidentifikator("12029240045")
        val UNG_PERSON_FØDSELSDATO = 12.februar(1992)
        const val AKTØRID = "42"
        val ORGNUMMER: String = "987654321"

        val a1: String = ORGNUMMER
        val a1Hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = AKTØRID,
            personidentifikator = UNG_PERSON_FNR_2018,
            organisasjonsnummer = ORGNUMMER
        )
        val a2: String = "654321987"
        val a3: String = "321987654"
        val a4: String = "456789123"

        private fun overgangFraInfotrygdPerson(jurist: Subsumsjonslogg) = gjenopprettFraJSON("/personer/infotrygdforlengelse.json", jurist).also { person ->
            person.håndter(
                Utbetalingshistorikk(
                    UUID.randomUUID(), "", "", ORGNUMMER, UUID.randomUUID().toString(),
                    InfotrygdhistorikkElement.opprett(
                        LocalDateTime.now(),
                        UUID.randomUUID(),
                        listOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar, 31.januar, 100.prosent, TestPerson.INNTEKT)),
                        listOf(Inntektsopplysning(ORGNUMMER, 1.januar, TestPerson.INNTEKT, true)),
                        emptyMap()
                    ),
                    besvart = LocalDateTime.now()
                ),
                Aktivitetslogg()
            )
        }
        private fun pingPongPerson(jurist: Subsumsjonslogg) = gjenopprettFraJSON("/personer/pingpong.json", jurist).also { person ->
            person.håndter(
                Utbetalingshistorikk(
                    UUID.randomUUID(), "", "", ORGNUMMER, UUID.randomUUID().toString(),
                    InfotrygdhistorikkElement.opprett(
                        LocalDateTime.now(),
                        UUID.randomUUID(),
                        listOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.februar, 28.februar, 100.prosent, TestPerson.INNTEKT)),
                        listOf(Inntektsopplysning(ORGNUMMER, 1.februar, TestPerson.INNTEKT, true)),
                        emptyMap()
                    ),
                    besvart = LocalDateTime.now()
                ),
                Aktivitetslogg()
            )
        }
    }

    lateinit var person: Person
    lateinit var observatør: TestObservatør
    lateinit var ugyldigeSituasjonerObservatør: UgyldigeSituasjonerObservatør
    lateinit var jurist: SubsumsjonsListLog
    val inspektør get() = inspektør(ORGNUMMER)

    val Int.vedtaksperiode: IdInnhenter get() = IdInnhenter { orgnummer -> this@vedtaksperiode.vedtaksperiodeId(orgnummer) }
    fun Int.vedtaksperiode(orgnummer: String): IdInnhenter = IdInnhenter { this@vedtaksperiode.vedtaksperiodeId(orgnummer) }
    fun IdInnhenter.filter(orgnummer: String = ORGNUMMER) = AktivitetsloggFilter.vedtaksperiode(this, orgnummer)

    @BeforeEach
    internal fun createTestPerson() {
        createTestPerson(UNG_PERSON_FNR_2018, UNG_PERSON_FØDSELSDATO)
    }

    @AfterEach
    fun verify() {
        ugyldigeSituasjonerObservatør.bekreftIngenUgyldigeSituasjoner(person.personlogg)
    }

    private fun regler(maksSykedager: Int): ArbeidsgiverRegler = object: ArbeidsgiverRegler {
        override fun burdeStarteNyArbeidsgiverperiode(oppholdsdagerBrukt: Int) = oppholdsdagerBrukt >= 16
        override fun arbeidsgiverperiodenGjennomført(arbeidsgiverperiodedagerBrukt: Int) = arbeidsgiverperiodedagerBrukt >= 16
        override fun dekningsgrad() = 1.0
        override fun maksSykepengedager() = maksSykedager
        override fun maksSykepengedagerOver67() = maksSykedager
    }
    protected fun createKorttidsPerson(personidentifikator: Personidentifikator, fødseldato: LocalDate, maksSykedager: Int) = createTestPerson { jurist ->
        Person(AKTØRID, personidentifikator, fødseldato.alder, jurist, regler(maksSykedager))
    }
    protected fun createTestPerson(personidentifikator: Personidentifikator, fødseldato: LocalDate, dødsdato: LocalDate? = null) = createTestPerson { jurist ->
        Person(AKTØRID, personidentifikator, Alder(fødseldato, dødsdato), jurist)
    }
    protected fun createPingPongPerson() = createTestPerson { jurist -> pingPongPerson(jurist) }
    protected fun createOvergangFraInfotrygdPerson() = createTestPerson { jurist -> overgangFraInfotrygdPerson(jurist) }

    protected fun createTestPerson(block: (jurist: Subsumsjonslogg) -> Person) : Person {
        jurist = SubsumsjonsListLog()
        person = block(jurist)
        observatør = TestObservatør(person)
        ugyldigeSituasjonerObservatør = UgyldigeSituasjonerObservatør(person)
        return person
    }

    internal fun reserialiser() {
        createTestPerson {
            gjenopprettFraJSONtekst(person.dto().tilPersonData().tilSerialisertPerson().json)
        }
    }

    private fun Int.vedtaksperiodeId(orgnummer: String) = observatør.vedtaksperiode(orgnummer, this - 1)
    fun Int.utbetaling(orgnummer: String) = inspektør(orgnummer).utbetalingId(this - 1)
    fun inspektør(orgnummer: String) = TestArbeidsgiverInspektør(person, orgnummer)
    fun inspektør(orgnummer: String, block: TestArbeidsgiverInspektør.() -> Unit) = inspektør(orgnummer).run(block)
}

internal fun AbstractPersonTest.nullstillTilstandsendringer() = observatør.nullstillTilstandsendringer()

internal fun interface IdInnhenter {
    fun id(orgnummer: String): UUID
}
