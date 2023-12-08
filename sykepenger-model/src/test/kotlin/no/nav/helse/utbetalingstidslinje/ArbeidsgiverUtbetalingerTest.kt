package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Personidentifikator
import no.nav.helse.april
import no.nav.helse.desember
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.UtbetalingstidslinjeInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold
import no.nav.helse.person.Person
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.arbeidsgiver
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.e2e.assertInfo
import no.nav.helse.sykepengegrunnlag
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.UTELATE
import no.nav.helse.testhelpers.Utbetalingsdager
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ArbeidsgiverUtbetalingerTest {

    private var maksdato: LocalDate? = null
    private var gjenståendeSykedager: Int? = null
    private var forbrukteSykedager: Int? = 0
    private lateinit var inspektør: UtbetalingstidslinjeInspektør
    private lateinit var aktivitetslogg: Aktivitetslogg

    private companion object {
        val UNG_PERSON_FNR_2018 = "12029240045".somPersonidentifikator()
        val UNG_PERSON_2018_FØDSELSDATO = 12.februar(1992)
        val PERSON_67_ÅR_5_JANUAR_FNR_2018 = "05015112345".somPersonidentifikator()
        val PERSON_67_ÅR_5_JANUAR_2018_FØDSELSDATO = 5.januar(1951)
        val PERSON_68_ÅR_1_DESEMBER_2018 = "01125112345".somPersonidentifikator()
        val PERSON_68_ÅR_1_DESEMBER_2018_FØDSELDATO = 1.desember(1951)
        val PERSON_70_ÅR_1_FEBRUAR_2018 = "01024812345".somPersonidentifikator()
        val PERSON_70_ÅR_1_FEBRUAR_2018_FØDSELSDATO = 1.februar(1948)
        val ORGNUMMER = "888888888"
        val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = "aktørId",
            personidentifikator = UNG_PERSON_FNR_2018,
            organisasjonsnummer = ORGNUMMER
        )
    }

    @Test
    fun `uavgrenset utbetaling`() {
        undersøke(UNG_PERSON_FNR_2018, 12.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(12, inspektør.size)
        assertEquals(10, inspektør.navDagTeller)
        assertEquals(2, inspektør.navHelgDagTeller)
        assertEquals(12000.0, inspektør.totalUtbetaling())
        assertEquals(12.desember, maksdato)
        assertEquals(238, gjenståendeSykedager)
        assertTrue(aktivitetslogg.harAktiviteter())
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `avgrenset betaling pga minimum inntekt`() {
        val vilkårsgrunnlagElement = VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = 1.januar,
            sykepengegrunnlag = 1000.månedlig.sykepengegrunnlag,
            opptjening = Opptjening.nyOpptjening(emptyList(), 1.januar, SubsumsjonObserver.NullObserver),
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            vurdertOk = false,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        )
        undersøke(UNG_PERSON_FNR_2018, 5.NAV(12), 7.NAV, grunnlagsdata = vilkårsgrunnlagElement, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)

        assertEquals(12, inspektør.size)
        assertEquals(0, inspektør.navDagTeller)
        assertEquals(2, inspektør.navHelgDagTeller)
        assertEquals(10, inspektør.avvistDagTeller)
        assertEquals(0.0, inspektør.totalUtbetaling())
        assertEquals(26.desember, maksdato)
        assertEquals(248, gjenståendeSykedager)
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `avgrenset betaling pga maksimum inntekt`() {
        undersøke(UNG_PERSON_FNR_2018, 5.NAV(3500), 7.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)

        assertEquals(12, inspektør.size)
        assertEquals(10, inspektør.navDagTeller)
        assertEquals(2, inspektør.navHelgDagTeller)
        assertEquals(10805.0 + 6000.0, inspektør.totalUtbetaling())
        assertEquals(12.desember, maksdato)
        assertEquals(238, gjenståendeSykedager)
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre()) { aktivitetslogg.toString() }
    }

    @Test
    fun `avgrenset betaling pga minimun sykdomsgrad`() {
        undersøke(UNG_PERSON_FNR_2018, 5.NAV(1200, 19.0), 2.ARB, 5.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)

        assertEquals(12, inspektør.size)
        assertEquals(5, inspektør.avvistDagTeller)
        assertEquals(2, inspektør.arbeidsdagTeller)
        assertEquals(5, inspektør.navDagTeller)
        assertEquals(6000.0, inspektør.totalUtbetaling())
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `avgrenset betaling pga oppbrukte sykepengedager`() {
        undersøke(PERSON_67_ÅR_5_JANUAR_FNR_2018, 7.UTELATE, 91.NAV, fødselsdato = PERSON_67_ÅR_5_JANUAR_2018_FØDSELSDATO)

        assertEquals(91, inspektør.size)
        assertEquals(60, inspektør.navDagTeller)
        assertEquals(26, inspektør.navHelgDagTeller)
        assertEquals(5, inspektør.avvistDagTeller)
        assertEquals(60 * 1200.0, inspektør.totalUtbetaling())
        assertEquals(30.mars, maksdato)
        assertEquals(0, gjenståendeSykedager)
        aktivitetslogg.assertInfo("Maks antall sykepengedager er nådd i perioden")
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `avgrenset betaling pga oppbrukte sykepengedager i tillegg til beløpsgrenser`() {
        undersøke(PERSON_67_ÅR_5_JANUAR_FNR_2018, 7.UTELATE, 98.NAV, fødselsdato = PERSON_67_ÅR_5_JANUAR_2018_FØDSELSDATO)

        assertEquals(98, inspektør.size)
        assertEquals(60, inspektør.navDagTeller)
        assertEquals(28, inspektør.navHelgDagTeller)
        assertEquals(10, inspektør.avvistDagTeller)
        assertEquals((60 * 1200.0), inspektør.totalUtbetaling())
        assertEquals(30.mars, maksdato)
        assertEquals(0, gjenståendeSykedager)
        aktivitetslogg.assertInfo("Maks antall sykepengedager er nådd i perioden")
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `historiske utbetalingstidslinjer vurdert i 248 grense`() {
        undersøke(
            PERSON_67_ÅR_5_JANUAR_FNR_2018,
            tidslinjeOf(35.UTELATE, 68.NAV),
            tidslinjeOf(7.UTELATE, 26.NAV),
            fødselsdato = PERSON_67_ÅR_5_JANUAR_2018_FØDSELSDATO
        )

        assertEquals(68, inspektør.size)
        assertEquals(40, inspektør.navDagTeller)
        assertEquals(10, inspektør.avvistDagTeller)
        assertEquals(40 * 1200.0, inspektør.totalUtbetaling())
        assertEquals(30.mars, maksdato)
        assertEquals(0, gjenståendeSykedager)
        aktivitetslogg.assertInfo("Maks antall sykepengedager er nådd i perioden")
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `beregn maksdato i et sykdomsforløp som slutter på en fredag`() {
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 4.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(28.desember, maksdato) // 3 dager already paid, 245 left. So should be fredag!
        assertEquals(245, gjenståendeSykedager)
        assertTrue(aktivitetslogg.harAktiviteter())
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `beregn maksdato i et sykdomsforløp med opphold i sykdom`() {
        undersøke(UNG_PERSON_FNR_2018, 2.ARB, 16.AP, 7.ARB, 8.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(8.januar(2019), maksdato)
        assertEquals(242, gjenståendeSykedager)
    }

    @Test
    fun `beregn maksdato (med rest) der den ville falt på en lørdag`() { //(351.S + 1.F + 1.S)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 335.NAV, 1.FRI, 1.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(31.desember, maksdato)
        assertEquals(8, gjenståendeSykedager)
    }

    @Test
    fun `beregn maksdato (med rest) der den ville falt på en søndag`() { //(23.S + 2.F + 1.S)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 7.NAV, 2.FRI, 1.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(1.januar(2019), maksdato)
        assertEquals(242, gjenståendeSykedager)
    }

    @Test
    fun `maksdato forskyves av ferie etterfulgt av sykedager`() { //21.S + 3.F + 1.S)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 5.NAV, 3.FRI, 1.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(2.januar(2019), maksdato)
        assertEquals(244, gjenståendeSykedager)
    }

    @Test
    fun `maksdato forskyves ikke av ferie på tampen av sykdomstidslinjen`() { //(21.S + 3.F)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 5.NAV, 3.FRI, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(2.januar(2019), maksdato)
        assertEquals(245, gjenståendeSykedager)
    }

    @Test
    fun `maksdato forskyves ikke av ferie etterfulgt av arbeidsdag på tampen av sykdomstidslinjen`() { //(21.S + 3.F + 1.A)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 5.NAV, 3.FRI, 1.ARB, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(3.januar(2019), maksdato)
        assertEquals(245, gjenståendeSykedager)
    }

    @Test
    fun `setter maksdato når ingen dager er brukt`() { //(16.S)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(28.desember, maksdato)
        assertEquals(248, gjenståendeSykedager)
        assertEquals(0, forbrukteSykedager)
    }

    @Test
    fun `når sykepengeperioden går over maksdato, så skal utbetaling stoppe ved maksdato`() { //(249.NAV)
        undersøke(UNG_PERSON_FNR_2018, 16.AP, 349.NAV, fødselsdato = UNG_PERSON_2018_FØDSELSDATO)
        assertEquals(28.desember, maksdato)
        assertEquals(0, gjenståendeSykedager)
        assertEquals(248, inspektør.navDagTeller)
        aktivitetslogg.assertInfo("Maks antall sykepengedager er nådd i perioden")
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `når personen fyller 67 blir antall gjenværende dager 60`() {
        undersøke(PERSON_67_ÅR_5_JANUAR_FNR_2018, 16.AP, 94.NAV, fødselsdato = PERSON_67_ÅR_5_JANUAR_2018_FØDSELSDATO)
        assertEquals(10.april, maksdato)
        assertEquals(0, gjenståendeSykedager)
        assertEquals(60, inspektør.navDagTeller)
        aktivitetslogg.assertInfo("Maks antall sykepengedager er nådd i perioden")
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `når personen fyller 67 og 248 dager er brukt opp`() {
        undersøke(PERSON_68_ÅR_1_DESEMBER_2018, 16.AP, 366.NAV, fødselsdato = PERSON_68_ÅR_1_DESEMBER_2018_FØDSELDATO)
        assertEquals(28.desember, maksdato)
        assertEquals(0, gjenståendeSykedager)
        aktivitetslogg.assertInfo("Maks antall sykepengedager er nådd i perioden")
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `når personen fyller 70 skal det ikke utbetales sykepenger`() {
        undersøke(PERSON_70_ÅR_1_FEBRUAR_2018, 1.NAV, startdato = 1.februar, fødselsdato = PERSON_70_ÅR_1_FEBRUAR_2018_FØDSELSDATO)
        assertEquals(31.januar, maksdato)
        assertEquals(0, gjenståendeSykedager)
        assertEquals(0, forbrukteSykedager)
    }

    private fun undersøke(
        fnr: Personidentifikator,
        vararg utbetalingsdager: Utbetalingsdager,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata? = null,
        startdato: LocalDate = 1.januar,
        fødselsdato: LocalDate
    ) {
        val tidslinje = tidslinjeOf(*utbetalingsdager, startDato = startdato)
        undersøke(fnr, tidslinje, tidslinjeOf(), grunnlagsdata, fødselsdato)
    }

    private fun undersøke(
        fnr: Personidentifikator,
        arbeidsgiverTidslinje: Utbetalingstidslinje,
        historiskTidslinje: Utbetalingstidslinje,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata? = null,
        fødselsdato: LocalDate
    ) {
        val person = Person("aktørid", fnr, fødselsdato.alder, MaskinellJurist())
        // seed arbeidsgiver med sykdomshistorikk
        val førsteDag = arbeidsgiverTidslinje.periode().start
        val sisteDag = arbeidsgiverTidslinje.periode().endInclusive
        person.håndter(
            hendelsefabrikk.lagSykmelding(
                sykeperioder = arrayOf(Sykmeldingsperiode(førsteDag, sisteDag))
            )
        )
        person.håndter(
            hendelsefabrikk.lagSøknad(
                perioder = arrayOf(Sykdom(førsteDag, sisteDag, 100.prosent)),
                sykmeldingSkrevet = 1.januar.atStartOfDay(),
                sendtTilNAVEllerArbeidsgiver = 1.januar
            )
        )

        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()

        vilkårsgrunnlagHistorikk.lagre(
            grunnlagsdata ?: VilkårsgrunnlagHistorikk.Grunnlagsdata(
                skjæringstidspunkt = 1.januar,
                sykepengegrunnlag = 30000.månedlig.sykepengegrunnlag(fødselsdato.alder),
                opptjening = Opptjening.nyOpptjening(
                    listOf(
                        Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                            ORGNUMMER, listOf(
                                Arbeidsforhold(1.januar.minusYears(1), null, false)
                            )
                        )
                    ), 1.januar, SubsumsjonObserver.NullObserver
                ),
                medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
                vurdertOk = true,
                meldingsreferanseId = UUID.randomUUID(),
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        )

        person.håndter(
            inntektsmelding = hendelsefabrikk.lagInntektsmelding(
                refusjon = Inntektsmelding.Refusjon(30000.månedlig, null),
                førsteFraværsdag = førsteDag,
                beregnetInntekt = 30000.månedlig,
                arbeidsgiverperioder = listOf(),
                arbeidsforholdId = null,
                begrunnelseForReduksjonEllerIkkeUtbetalt = null,
                harOpphørAvNaturalytelser = false
            )
        )
        aktivitetslogg = Aktivitetslogg()

        val utbetalinger = ArbeidsgiverUtbetalinger(
            NormalArbeidstaker,
            fødselsdato.alder,
            { _, _, _ -> mapOf(person.arbeidsgiver(ORGNUMMER) to arbeidsgiverTidslinje) },
            historiskTidslinje,
            vilkårsgrunnlagHistorikk
        )
        val (maksimumSykepenger, tidslinjerPerArbeidsgiver) = utbetalinger.beregn(
            arbeidsgiverTidslinje.periode(),
            arbeidsgiverTidslinje.periode(),
            aktivitetslogg,
            SubsumsjonObserver.NullObserver
        )
        maksdato = maksimumSykepenger.sisteDag()
        gjenståendeSykedager = maksimumSykepenger.gjenståendeDager()
        forbrukteSykedager = maksimumSykepenger.forbrukteDager()
        inspektør = tidslinjerPerArbeidsgiver.entries
            .single { (arbeidsgiver, _) -> arbeidsgiver.inspektør.organisasjonsnummer == ORGNUMMER }
            .value
            .inspektør
    }
}
