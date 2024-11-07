package no.nav.helse.spleis.graphql

import java.time.LocalDate.EPOCH
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.TilkommenInntekt
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.spleis.speil.dto.AnnullertPeriode
import no.nav.helse.spleis.speil.dto.BeregnetPeriode
import no.nav.helse.spleis.speil.dto.GhostPeriodeDTO
import no.nav.helse.spleis.speil.dto.InfotrygdVilkårsgrunnlag
import no.nav.helse.spleis.speil.dto.Inntektkilde
import no.nav.helse.spleis.speil.dto.PersonDTO
import no.nav.helse.spleis.speil.dto.SammenslåttDag
import no.nav.helse.spleis.speil.dto.SpleisVilkårsgrunnlag
import no.nav.helse.spleis.speil.dto.Sykdomstidslinjedag
import no.nav.helse.spleis.speil.dto.SykdomstidslinjedagKildetype
import no.nav.helse.spleis.speil.dto.SykdomstidslinjedagType
import no.nav.helse.spleis.speil.dto.UberegnetPeriode
import no.nav.helse.spleis.speil.dto.Utbetalingsinfo
import no.nav.helse.spleis.speil.dto.UtbetalingstidslinjedagType
import no.nav.helse.spleis.testhelpers.OverstyrtArbeidsgiveropplysning
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SpeilBuilderTest : AbstractE2ETest() {

    @Test
    fun `totalgrad må jo være lik for avslag og utbetaling`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 19.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1, beregnetInntekt = 836352.årlig)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to 836352.årlig, a2 to 168276.årlig))
        håndterYtelser()

        // Totalgraden er her 15.81748468089681
        // På avslagsdager tar vi toInt() -> 15
        // Dette er nok fordi 19.99 skal gi avslag på minumum sykdomsgrad, og da er det rart å vise 20% i Speil
        speilApi().assertTotalgrad(15, 17.januar til 19.januar, 22.januar til 26.januar, 29.januar til 31.januar)

        håndterMinimumSykdomsgradsvurderingMelding(perioderMedMinimumSykdomsgradVurdertOK = setOf(17.januar til 31.januar))
        håndterYtelser()

        // På utbetalingsdager tar vi toRoundInt() -> 16
        speilApi().assertTotalgrad(16, 17.januar til 19.januar, 22.januar til 26.januar, 29.januar til 31.januar)
    }

    private fun PersonDTO.assertTotalgrad(forventet: Int, vararg perioder: Periode) {
        val totalgrader = (arbeidsgivere[0]
        .generasjoner[0]
        .perioder[0] as BeregnetPeriode)
        .sammenslåttTidslinje
        .filter { sammenslåttDag -> perioder.any { sammenslåttDag.dagen in it } }
        .map { it.utbetalingsinfo?.totalGrad }
        assertTrue(totalgrader.all { it == forventet }) { "Her er det noe som ikke er $forventet: $totalgrader"}
    }

    @Test
    fun `lager NyeInntektsforhold-pølse for tilkommen inntekt`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), tilkomneInntekter = listOf(TilkommenInntekt(fom = 1.februar, tom = 28.februar, orgnummer = "a24", råttBeløp = 10000)))
        håndterYtelser()

        val nyeInntektsforholdPølse = speilApi().arbeidsgivere.find { it.organisasjonsnummer == "a24" }?.nyeInntektsforhold!!.single()
        assertEquals(1.februar til 28.februar, nyeInntektsforholdPølse.fom til nyeInntektsforholdPølse.tom)
        assertEquals(1.januar, nyeInntektsforholdPølse.skjæringstidspunkt)
        assertEquals(10000.0 / 20, nyeInntektsforholdPølse.dagligBeløp)
        assertEquals(10833, nyeInntektsforholdPølse.månedligBeløp.toInt())

        assertEquals(emptyList<GhostPeriodeDTO>(),  speilApi().arbeidsgivere.find { it.organisasjonsnummer == "a24" }?.ghostPerioder)
    }

    @Test
    fun `kan lage NyeInntektsforhold-pølse med peker til riktig vilkårsgrunnlag før noe er utbetalt`() {
        tilGodkjenning(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), tilkomneInntekter = listOf(TilkommenInntekt(fom = 1.februar, tom = 28.februar, orgnummer = "a24", råttBeløp = 10000)))

        val nyeInntektsforholdPølser = speilApi().arbeidsgivere.find { it.organisasjonsnummer == "a24" }?.nyeInntektsforhold!!.single()
        assertEquals(1.februar til 28.februar, nyeInntektsforholdPølser.fom til nyeInntektsforholdPølser.tom)

        val førstegangs = speilApi().arbeidsgivere.find { it.organisasjonsnummer == "a1" }!!.generasjoner.single().perioder.find { it.fom == 1.januar } as BeregnetPeriode
        val vilkårsgrunnlagIdFørstegangssøknad = førstegangs.vilkårsgrunnlagId
        val vilkårsgrunnlag = speilApi().vilkårsgrunnlag.keys
        assertEquals(1, vilkårsgrunnlag.size)
        assertTrue(vilkårsgrunnlagIdFørstegangssøknad in vilkårsgrunnlag)
    }


    @Test
    fun `Dødsdato ligger på person`() {
        val dødsdato = 1.januar
        håndterDødsmelding(dødsdato)
        assertEquals(dødsdato, speilApi().dødsdato)
    }

    @Test
    fun `nav utbetaler agp`() {
        tilGodkjenning(1.januar, 31.januar)
        val id = UUID.randomUUID()
        håndterOverstyrTidslinje((1.januar til 16.januar).map {
            ManuellOverskrivingDag(it, Dagtype.SykedagNav, 100)
        }, meldingsreferanseId = id)
        håndterYtelserTilGodkjenning()
        val speilJson = speilApi()
        val tidslinje = speilJson.arbeidsgivere.single().generasjoner.single().perioder.single().sammenslåttTidslinje
        val forventetFørstedag = SammenslåttDag(
            dagen = 1.januar,
            sykdomstidslinjedagtype = SykdomstidslinjedagType.SYKEDAG_NAV,
            utbetalingstidslinjedagtype = UtbetalingstidslinjedagType.ArbeidsgiverperiodeDag,
            kilde = Sykdomstidslinjedag.SykdomstidslinjedagKilde(SykdomstidslinjedagKildetype.Saksbehandler, id),
            grad = 100,
            utbetalingsinfo = Utbetalingsinfo(
                personbeløp = 0,
                arbeidsgiverbeløp = 2161,
                totalGrad = 100
            )
        )
        assertEquals(forventetFørstedag, tidslinje.first())
    }

    @Test
    fun `nav skal ikke utbetale agp for kort periode likevel - perioden går så til AUU`() {
        håndterSøknad(1.januar til 16.januar)
        håndterInntektsmelding(1.januar, begrunnelseForReduksjonEllerIkkeUtbetalt = "ja",)
        håndterVilkårsgrunnlag()
        håndterYtelserTilGodkjenning()
        val idOverstyring = UUID.randomUUID()
        håndterOverstyrTidslinje((1.januar til 16.januar).map {
            ManuellOverskrivingDag(it, Dagtype.Sykedag, 100)
        }, meldingsreferanseId = idOverstyring)
        val speilJson = speilApi()
        val generasjoner = speilJson.arbeidsgivere.single().generasjoner
        assertEquals(2, generasjoner.size)
        val tidslinje = generasjoner[0].perioder.single().sammenslåttTidslinje
        val forventetFørstedag = SammenslåttDag(
            dagen = 1.januar,
            sykdomstidslinjedagtype = SykdomstidslinjedagType.SYKEDAG,
            utbetalingstidslinjedagtype = UtbetalingstidslinjedagType.ArbeidsgiverperiodeDag, // ingen utbetalingstidslinje
            kilde = Sykdomstidslinjedag.SykdomstidslinjedagKilde(SykdomstidslinjedagKildetype.Saksbehandler, idOverstyring),
            grad = 100,
            utbetalingsinfo = null
        )
        assertEquals(forventetFørstedag, tidslinje.first())
    }

    @Test
    fun `Viser inntektsgrunnlag for arbeidsforhold som startet innen 3 måneder før skjæringstidspunktet, selvom vi ikke har inntekt`() {
        håndterSøknad(Sykdom(1.januar, 15.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1,)
        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to 31000.månedlig),
            arbeidsforhold = listOf(a1 to EPOCH, a2 to 25.november(2017))
        )
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        val personDto = speilApi()

        assertEquals(listOf(a1, a2).map(String::toString), personDto.arbeidsgivere.map { it.organisasjonsnummer })

        val vilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val arbeidsgiverInntektA2 = personDto.vilkårsgrunnlag[vilkårsgrunnlagId]?.inntekter?.first { it.organisasjonsnummer == a2 }

        assertEquals(0.0, arbeidsgiverInntektA2?.omregnetÅrsinntekt?.beløp)
        assertEquals(Inntektkilde.IkkeRapportert, arbeidsgiverInntektA2?.omregnetÅrsinntekt?.kilde)
    }

    @Test
    fun `beregnet periode peker på vilkårsgrunnlagid for infotrygdvilkårsgrunnlag`() {
        createOvergangFraInfotrygdPerson()
        forlengVedtak(1.mars, 31.mars)

        val infotrygdVilkårsgrunnlag = dto().vilkårsgrunnlagHistorikk.historikk.first().vilkårsgrunnlag.first { it.skjæringstidspunkt == 1.januar }
        val infotrygdVilkårsgrunnlagId = infotrygdVilkårsgrunnlag.vilkårsgrunnlagId

        val personDto = speilApi()
        val speilVilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId

        assertEquals(speilVilkårsgrunnlagId, infotrygdVilkårsgrunnlagId)
    }

    @Test
    fun `annullert periode skal ikke ha vilkårsgrunnlagsId`() {
        val utbetaling = nyttVedtak(1.januar, 31.januar)
        håndterAnnullerUtbetaling(utbetaling)
        val personDto = speilApi()
        val generasjoner = personDto.arbeidsgivere.first().generasjoner
        assertEquals(2, generasjoner.size)
        assertInstanceOf(BeregnetPeriode::class.java, generasjoner.last().perioder.single())
        assertInstanceOf(AnnullertPeriode::class.java, generasjoner.first().perioder.single())
    }

    @Test
    fun `beregnet periode peker på et vilkårsgrunnlag`() {
        nyttVedtak(1.januar, 31.januar)
        val personDto = speilApi()
        val speilVilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[speilVilkårsgrunnlagId]
        assertTrue(vilkårsgrunnlag is SpleisVilkårsgrunnlag)
    }

    @Test
    fun `refusjon ligger på vilkårsgrunnlaget`() {
        nyttVedtak(1.januar, 31.januar)
        val personDto = speilApi()
        val speilVilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[speilVilkårsgrunnlagId] as? SpleisVilkårsgrunnlag
        assertTrue(vilkårsgrunnlag!!.arbeidsgiverrefusjoner.isNotEmpty())
        val arbeidsgiverrefusjon = vilkårsgrunnlag.arbeidsgiverrefusjoner.single()
        assertEquals(a1, arbeidsgiverrefusjon.arbeidsgiver)
        val refusjonsopplysning = arbeidsgiverrefusjon.refusjonsopplysninger.single()

        assertEquals(1.januar, refusjonsopplysning.fom)
        assertEquals(null, refusjonsopplysning.tom)
        assertEquals(INNTEKT,refusjonsopplysning.beløp.månedlig)
    }

    @Test
    fun `refusjon ligger på vilkårsgrunnlaget - også for infotrygd`() {
        createOvergangFraInfotrygdPerson()
        forlengVedtak(1.mars, 31.mars)
        val personDto = speilApi()
        val speilVilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[speilVilkårsgrunnlagId] as? InfotrygdVilkårsgrunnlag
        assertTrue(vilkårsgrunnlag!!.arbeidsgiverrefusjoner.isNotEmpty())
        val arbeidsgiverrefusjon = vilkårsgrunnlag.arbeidsgiverrefusjoner.single()
        assertEquals(a1, arbeidsgiverrefusjon.arbeidsgiver)
        val refusjonsopplysning = arbeidsgiverrefusjon.refusjonsopplysninger.single()

        assertEquals(1.januar, refusjonsopplysning.fom)
        assertEquals(null, refusjonsopplysning.tom)
        assertEquals(31000.0, refusjonsopplysning.beløp)
    }

    @Test
    fun `endring i refusjon`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        val inntektsmeldingId = håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, endringerIRefusjon = listOf(
                Inntektsmelding.Refusjon.EndringIRefusjon(INGEN, 1.februar))),
        )
        håndterVilkårsgrunnlag()
        håndterYtelserTilGodkjenning()

        val personDto = speilApi()
        val speilVilkårsgrunnlagId = (personDto.arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = personDto.vilkårsgrunnlag[speilVilkårsgrunnlagId] as? SpleisVilkårsgrunnlag
        assertTrue(vilkårsgrunnlag!!.arbeidsgiverrefusjoner.isNotEmpty())
        val arbeidsgiverrefusjon = vilkårsgrunnlag.arbeidsgiverrefusjoner.single()
        assertEquals(a1, arbeidsgiverrefusjon.arbeidsgiver)
        val refusjonsopplysninger = arbeidsgiverrefusjon.refusjonsopplysninger

        assertEquals(2, refusjonsopplysninger.size)

        assertEquals(1.januar, refusjonsopplysninger.first().fom)
        assertEquals(31.januar, refusjonsopplysninger.first().tom)
        assertEquals(INNTEKT, refusjonsopplysninger.first().beløp.månedlig)
        assertEquals(inntektsmeldingId, refusjonsopplysninger.first().meldingsreferanseId)
        assertEquals(1.februar, refusjonsopplysninger.last().fom)
        assertEquals(null, refusjonsopplysninger.last().tom)
        assertEquals(INGEN, refusjonsopplysninger.last().beløp.månedlig)
        assertEquals(inntektsmeldingId, refusjonsopplysninger.last().meldingsreferanseId)
    }

    @Test
    fun `korrigert inntektsmelding i Avsluttet, velger opprinnelig refusjon for eldste generasjon`() {
        nyttVedtak(1.januar, 31.januar)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(20000.månedlig, null),
        )
        håndterYtelser()

        val speil = speilApi()
        val generasjoner = speil.arbeidsgivere.first().generasjoner
        assertEquals(2, generasjoner.size)

        generasjoner.last().also { eldsteGenerasjon ->
            assertEquals(1, eldsteGenerasjon.perioder.size)
            val vilkårsgrunnlagId = (eldsteGenerasjon.perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
            val vilkårsgrunnlag = speil.vilkårsgrunnlag[vilkårsgrunnlagId] as? SpleisVilkårsgrunnlag
            assertEquals(INNTEKT, vilkårsgrunnlag!!.arbeidsgiverrefusjoner.single().refusjonsopplysninger.single().beløp.månedlig)
        }
        generasjoner.first().also { nyesteGenerasjon ->
            assertEquals(1, nyesteGenerasjon.perioder.size)
            val vilkårsgrunnlagId = (nyesteGenerasjon.perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
            val vilkårsgrunnlag = speil.vilkårsgrunnlag[vilkårsgrunnlagId] as? SpleisVilkårsgrunnlag
            assertEquals(20000.månedlig, vilkårsgrunnlag!!.arbeidsgiverrefusjoner.single().refusjonsopplysninger.single().beløp.månedlig)
        }
    }

    @Test
    fun `Endring til ingen refusjon i forlengelsen`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelserTilGodkjenning()
        håndterInntektsmeldingUtenRefusjon(
            listOf(1.januar til 16.januar),
            vedtaksperiode = 2
        )
        håndterYtelserTilGodkjent()
        håndterYtelserTilGodkjenning()

        val januarVilkårsgrunnlagId = (speilApi().arbeidsgivere.first().generasjoner.last().perioder.last() as BeregnetPeriode).vilkårsgrunnlagId
        val februarVilkårsgrunnlagId = (speilApi().arbeidsgivere.first().generasjoner.first().perioder.first() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = speilApi().vilkårsgrunnlag

        assertEquals(1, vilkårsgrunnlag[januarVilkårsgrunnlagId]!!.arbeidsgiverrefusjoner.single().refusjonsopplysninger.size)
        assertEquals(2, vilkårsgrunnlag[februarVilkårsgrunnlagId]!!.arbeidsgiverrefusjoner.single().refusjonsopplysninger.size)

        val førsteRefusjonsopplysning = vilkårsgrunnlag[februarVilkårsgrunnlagId]!!.arbeidsgiverrefusjoner.single().refusjonsopplysninger.first()
        val sisteRefusjonsopplysning = vilkårsgrunnlag[februarVilkårsgrunnlagId]!!.arbeidsgiverrefusjoner.single().refusjonsopplysninger.last()

        assertEquals(1.januar, førsteRefusjonsopplysning.fom)
        assertEquals(31.januar, førsteRefusjonsopplysning.tom)
        assertEquals(INNTEKT, førsteRefusjonsopplysning.beløp.månedlig)

        assertEquals(1.februar, sisteRefusjonsopplysning.fom)
        assertEquals(null, sisteRefusjonsopplysning.tom)
        assertEquals(INGEN, sisteRefusjonsopplysning.beløp.månedlig)
    }

    @Test
    fun `hendelser på uberegnet periode`() {
        val søknadId = håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        val periode = speilApi().arbeidsgivere.single().generasjoner.single().perioder.single() as UberegnetPeriode
        assertEquals(setOf(søknadId), periode.hendelser)
    }

    @Test
    fun `legger ved skjønnsmessig fastsatt sykepengegrunnlag`() {
        val inntektIm = 31000.0
        val inntektSkatt = 31000.0 * 2
        val inntektSkjønnsfastsatt = 31000 * 1.5
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(1.januar, beregnetInntekt = inntektIm.månedlig,)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to inntektSkatt.månedlig))
        håndterYtelserTilGodkjenning()
        håndterSkjønnsmessigFastsettelse(
            skjæringstidspunkt = 1.januar,
            opplysninger = listOf(OverstyrtArbeidsgiveropplysning(a1, inntektSkjønnsfastsatt.månedlig))
        )
        håndterYtelserTilGodkjenning()

        val personDto = speilApi()
        val vilkårsgrunnlagId = (personDto.arbeidsgivere.single().generasjoner.single().perioder.single() as BeregnetPeriode).vilkårsgrunnlagId
        val vilkårsgrunnlag = (personDto.vilkårsgrunnlag[vilkårsgrunnlagId] as SpleisVilkårsgrunnlag)
        assertEquals(inntektIm * 12, vilkårsgrunnlag.omregnetÅrsinntekt)
        assertEquals(inntektSkjønnsfastsatt * 12, vilkårsgrunnlag.beregningsgrunnlag)
        assertEquals(inntektSkjønnsfastsatt * 12, vilkårsgrunnlag.inntekter.single().skjønnsmessigFastsatt!!.årlig)

        assertEquals(inntektSkjønnsfastsatt, vilkårsgrunnlag.inntekter.single().skjønnsmessigFastsatt!!.månedlig)

        assertEquals(Inntektkilde.Inntektsmelding.name, vilkårsgrunnlag.inntekter.single().omregnetÅrsinntekt.kilde.name)
        assertEquals(inntektIm * 12, vilkårsgrunnlag.inntekter.single().omregnetÅrsinntekt.beløp)
        assertEquals(inntektIm, vilkårsgrunnlag.inntekter.single().omregnetÅrsinntekt.månedsbeløp)
    }

    @Test
    fun `Sykmeldt 20 % fra to forskjellige arbeidsforhold gir faktisk rett på sykepenger`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 20.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 20.prosent), orgnummer = a2)

        håndterInntektsmelding(1.januar, orgnummer = a2, beregnetInntekt = 22966.54.månedlig)
        håndterInntektsmelding(1.januar, orgnummer = a1, beregnetInntekt = 18199.7.månedlig)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to 18199.7.månedlig, a2 to 22966.54.månedlig))
        håndterYtelser()

        val personDto = speilApi()
        assertEquals(20,
            (personDto
                .arbeidsgivere[0]
                .generasjoner[0]
                .perioder[0] as BeregnetPeriode)
                .sammenslåttTidslinje[28]
                .utbetalingsinfo!!
                .totalGrad)
    }

    @Test
    fun `Sykmeldt rett under 20 % fra to forskjellige arbeidsforhold gir ikke rett på sykepenger`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 19.99.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 19.99.prosent), orgnummer = a2)

        håndterInntektsmelding(1.januar, orgnummer = a2, beregnetInntekt = 22966.54.månedlig)
        håndterInntektsmelding(1.januar, orgnummer = a1, beregnetInntekt = 18199.7.månedlig)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to 18199.7.månedlig, a2 to 22966.54.månedlig))
        håndterYtelser()

        val personDto = speilApi()
        assertEquals(19,
            (personDto
                .arbeidsgivere[0]
                .generasjoner[0]
                .perioder[0] as BeregnetPeriode)
                .sammenslåttTidslinje[28]
                .utbetalingsinfo!!
                .totalGrad)
    }

}
