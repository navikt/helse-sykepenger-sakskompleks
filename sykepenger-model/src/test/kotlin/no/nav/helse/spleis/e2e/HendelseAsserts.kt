package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.AktivitetsloggAsserts
import no.nav.helse.dsl.UNG_PERSON_FNR_2018
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.Varslersamler
import no.nav.helse.erHelg
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Periode
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.person.AbstractPersonTest
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.Person
import no.nav.helse.person.TilstandType
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

internal fun assertInntektshistorikkForDato(forventetInntekt: Inntekt?, dato: LocalDate, inspektør: TestArbeidsgiverInspektør) {
    assertEquals(forventetInntekt, inspektør.inntektInspektør.omregnetÅrsinntekt(dato)?.sykepengegrunnlag)
}

internal fun assertInntektForDato(forventetInntekt: Inntekt?, dato: LocalDate, inspektør: TestArbeidsgiverInspektør) {
    val grunnlagsdataInspektør = inspektør.vilkårsgrunnlaghistorikk().grunnlagsdata(dato).inspektør
    val sykepengegrunnlagInspektør = grunnlagsdataInspektør.inntektsgrunnlag.inspektør
    sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver[inspektør.orgnummer]?.inspektør.also {
        assertEquals(forventetInntekt, it?.fastsattÅrsinntekt)
    }
}

internal fun AbstractEndToEndTest.assertEtterspurt(løsning: KClass<out Hendelse>, type: Aktivitet.Behov.Behovtype, vedtaksperiodeIdInnhenter: IdInnhenter, orgnummer: String) {
    val etterspurtBehov = EtterspurtBehov.finnEtterspurtBehov(ikkeBesvarteBehov, type, vedtaksperiodeIdInnhenter, orgnummer)
    assertTrue(ikkeBesvarteBehov.remove(etterspurtBehov)) {
        "Forventer at $type skal være etterspurt før ${løsning.simpleName} håndteres. Perioden er i ${
            observatør.tilstandsendringer[vedtaksperiodeIdInnhenter.id(orgnummer)]?.last()
        }."
    }
}
/*

internal fun <T : PersonHendelse> AbstractEndToEndTest.assertEtterspurt(løsning: KClass<T>, type: Aktivitet.Behov.Behovtype) {
    val etterspurtBehov = EtterspurtBehov.finnEtterspurtBehov(ikkeBesvarteBehov, type, vedtaksperiodeIdInnhenter, orgnummer)
    assertTrue(ikkeBesvarteBehov.remove(etterspurtBehov)) {
        "Forventer at $type skal være etterspurt før ${løsning.simpleName} håndteres. Perioden er i ${
            observatør.tilstandsendringer[vedtaksperiodeIdInnhenter.id(orgnummer)]?.last()
        }.\nAktivitetsloggen:\n${person.personLogg}"
    }
}
*/

internal inline fun <reified R : Utbetalingsdag> assertUtbetalingsdag(dag: Utbetalingsdag, expectedDagtype: KClass<R>, expectedTotalgrad: Int = 100) {
    dag.let {
        assertEquals(it::class, expectedDagtype)
        it.økonomi.brukTotalGrad { totalGrad -> assertEquals(expectedTotalgrad, totalGrad) }
    }
}

internal fun AbstractEndToEndTest.assertUtbetalingsbeløp(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    forventetArbeidsgiverbeløp: Int,
    forventetArbeidsgiverRefusjonsbeløp: Int,
    subset: Periode? = null,
    orgnummer: String = a1
) {
    val utbetalingstidslinje = inspektør(orgnummer).utbetalingstidslinjer(vedtaksperiodeIdInnhenter).let { subset?.let(it::subset) ?: it }

    utbetalingstidslinje.filterNot { it.dato.erHelg() }.forEach { utbetalingsdag ->
        assertEquals(forventetArbeidsgiverbeløp.daglig, utbetalingsdag.økonomi.inspektør.arbeidsgiverbeløp) { "feil arbeidsgiverbeløp for dag ${utbetalingsdag.dato} " }
        assertEquals(forventetArbeidsgiverRefusjonsbeløp.daglig, utbetalingsdag.økonomi.inspektør.arbeidsgiverRefusjonsbeløp.rundTilDaglig())
        assertEquals(INGEN, utbetalingsdag.økonomi.inspektør.personbeløp)
    }
}

internal fun AbstractEndToEndTest.assertHarHendelseIder(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    vararg hendelseIder: UUID,
    orgnummer: String = a1
) {
    assertTrue(
        inspektør(orgnummer).hendelseIder(vedtaksperiodeIdInnhenter).containsAll(hendelseIder.toList())
    ) { "Perioden inneholder ikke alle hendelseidene" }
}

internal fun AbstractEndToEndTest.assertHarIkkeHendelseIder(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    vararg hendelseIder: UUID,
    orgnummer: String = a1
) {
    assertTrue(
        inspektør(orgnummer).hendelseIder(vedtaksperiodeIdInnhenter).none { it in hendelseIder.toList() }) { "Perioden inneholder ikke alle hendelseidene" }
}

internal fun AbstractEndToEndTest.assertTilstand(
    vedtaksperiodeIdInnhenter: IdInnhenter,
    tilstand: TilstandType,
    orgnummer: String = a1,
) {
    val sisteTilstand = inspektør(orgnummer).sisteTilstand(vedtaksperiodeIdInnhenter)
    assertEquals(tilstand, sisteTilstand) {
        "Forventet at perioden skal stå i tilstand $tilstand, mens den står faktisk i $sisteTilstand\n${person.personLogg}"
    }
}

internal fun AbstractEndToEndTest.assertSisteTilstand(vedtaksperiodeIdInnhenter: IdInnhenter, tilstand: TilstandType, orgnummer: String = a1, errortekst: (() -> String)? = null) {
    assertEquals(tilstand, observatør.tilstandsendringer[vedtaksperiodeIdInnhenter.id(orgnummer)]?.last(), errortekst)
}

internal fun AbstractEndToEndTest.assertTilstander(indeks: Int, vararg tilstander: TilstandType, orgnummer: String = a1) {
    assertTilstander(vedtaksperiodeIdInnhenter = (indeks + 1).vedtaksperiode, tilstander = tilstander, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.assertTilstander(vedtaksperiodeIdInnhenter: IdInnhenter, vararg tilstander: TilstandType, orgnummer: String = a1, inspektør: TestArbeidsgiverInspektør = inspektør(orgnummer), message: String? = null) {
    val id = vedtaksperiodeIdInnhenter.id(orgnummer)
    assertFalse(inspektør.periodeErForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er forkastet med tilstander: ${observatør.tilstandsendringer[id]}:\n${person.personLogg}" }
    assertTrue(inspektør.periodeErIkkeForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er forkastet med tilstander: ${observatør.tilstandsendringer[id]}\n${person.personLogg}" }
    assertEquals(tilstander.asList(), observatør.tilstandsendringer[id], message)
}

internal fun AbstractEndToEndTest.assertForkastetPeriodeTilstander(indeks: Int, vararg tilstander: TilstandType, orgnummer: String = a1) {
    assertForkastetPeriodeTilstander(vedtaksperiodeIdInnhenter = indeks.vedtaksperiode, tilstander = tilstander, orgnummer = orgnummer)
}

internal fun AbstractEndToEndTest.assertForkastetPeriodeTilstander(vedtaksperiodeIdInnhenter: IdInnhenter, vararg tilstander: TilstandType, orgnummer: String = a1, inspektør: TestArbeidsgiverInspektør = inspektør(orgnummer), varselkode: Varselkode? = null) {
    assertTrue(inspektør.periodeErForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er ikke forkastet" }
    assertFalse(inspektør.periodeErIkkeForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er ikke forkastet" }
    assertEquals(tilstander.asList(), observatør.tilstandsendringer[vedtaksperiodeIdInnhenter.id(orgnummer)])
    varselkode?.let { assertFunksjonellFeil(it, vedtaksperiodeIdInnhenter.filter()) }
}

internal fun AbstractEndToEndTest.assertForkastetPeriodeTilstander(orgnummer: String, vedtaksperiodeIdInnhenter: IdInnhenter, vararg tilstander: TilstandType) {
    assertTrue(inspektør(orgnummer).periodeErForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er ikke forkastet" }
    assertFalse(inspektør(orgnummer).periodeErIkkeForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er ikke forkastet" }
    assertEquals(tilstander.asList(), observatør.tilstandsendringer[vedtaksperiodeIdInnhenter.id(orgnummer)])
}

internal fun AbstractEndToEndTest.assertSisteForkastetPeriodeTilstand(orgnummer: String, vedtaksperiodeIdInnhenter: IdInnhenter, tilstand: TilstandType) {
    assertEquals(tilstand, observatør.tilstandsendringer[vedtaksperiodeIdInnhenter.id(orgnummer)]?.last())
    assertTrue(inspektør(orgnummer).periodeErForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er ikke forkastet" }
    assertFalse(inspektør(orgnummer).periodeErIkkeForkastet(vedtaksperiodeIdInnhenter)) { "Perioden er ikke forkastet" }
}

internal fun AbstractPersonTest.assertInfo(forventet: String, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle) = person.personLogg.assertInfo(forventet, filter, assertetVarsler)
internal fun AbstractPersonTest.assertIngenInfo(forventet: String, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle) = person.personLogg.assertIngenInfo(forventet, filter, assertetVarsler)
internal fun AbstractPersonTest.assertVarsler(varsel: List<Varselkode>, filter: AktivitetsloggFilter) = person.personLogg.assertVarsler(varsel, filter, assertetVarsler)
internal fun AbstractPersonTest.assertVarsel(varsel: Varselkode, filter: AktivitetsloggFilter) = person.personLogg.assertVarsel(varsel, filter, assertetVarsler)
internal fun AbstractPersonTest.assertFunksjonellFeil(varselkode: Varselkode, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle) = person.personLogg.assertFunksjonellFeil(varselkode, filter, assertetVarsler)
internal fun AbstractPersonTest.assertFunksjonelleFeil(filter: AktivitetsloggFilter) = person.personLogg.assertFunksjonelleFeil(filter, assertetVarsler)
internal fun AbstractPersonTest.assertIngenFunksjonellFeil(varselkode: Varselkode, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle) = person.personLogg.assertIngenFunksjonellFeil(varselkode, filter, assertetVarsler)
internal fun AbstractPersonTest.assertIngenFunksjonelleFeil(filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle) = person.personLogg.assertIngenFunksjonelleFeil(filter, assertetVarsler)
internal fun AbstractPersonTest.assertLogiskFeil(severe: String, filter: AktivitetsloggFilter) = person.personLogg.assertLogiskFeil(severe, filter, assertetVarsler)

internal fun Aktivitetslogg.assertInfo(forventet: String, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertInfo(forventet, filter)

internal fun Aktivitetslogg.assertIngenInfo(forventet: String, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertIngenInfo(forventet, filter)

internal fun Aktivitetslogg.assertVarsler(varsel: List<Varselkode>, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertVarsler(varsel, filter)

internal fun Aktivitetslogg.assertVarsel(varsel: Varselkode, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertVarsel(varsel, filter)

internal fun Aktivitetslogg.assertFunksjonellFeil(varselkode: Varselkode, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertFunksjonellFeil(varselkode, filter)

internal fun Aktivitetslogg.assertFunksjonelleFeil(filter: AktivitetsloggFilter, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertFunksjonelleFeil(filter)

internal fun Aktivitetslogg.assertIngenFunksjonellFeil(varselkode: Varselkode, filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertIngenFunksjonellFeil(varselkode, filter)

internal fun Aktivitetslogg.assertIngenFunksjonelleFeil(filter: AktivitetsloggFilter = AktivitetsloggFilter.Alle, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertIngenFunksjonelleFeil(filter)

internal fun Aktivitetslogg.assertLogiskFeil(severe: String, filter: AktivitetsloggFilter, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertLogiskFeil(severe, filter)

internal fun Aktivitetslogg.assertHarTag(vedtaksperiode: IdInnhenter, orgnummer: String = a1, forventetTag: String, assertetVarsler: Varslersamler.AssertetVarsler = Varslersamler.AssertetVarsler()) =
    AktivitetsloggAsserts(this, assertetVarsler).assertHarTag(vedtaksperiode.id(orgnummer), forventetTag)

internal fun interface AktivitetsloggFilter {
    companion object {
        internal val Alle = AktivitetsloggFilter { true }
        internal fun UUID.filter() = vedtaksperiode(this)

        internal fun vedtaksperiode(vedtaksperiodeId: UUID): AktivitetsloggFilter = AktivitetsloggFilter { kontekst ->
            kontekst.kontekstType == "Vedtaksperiode" && kontekst.kontekstMap["vedtaksperiodeId"] == vedtaksperiodeId.toString()
        }

        internal fun vedtaksperiode(idInnhenter: IdInnhenter, orgnummer: String): AktivitetsloggFilter = AktivitetsloggFilter { kontekst ->
            kontekst.kontekstType == "Vedtaksperiode" && kontekst.kontekstMap["vedtaksperiodeId"] == idInnhenter.id(orgnummer).toString()
        }

        internal fun person(personidentifikator: Personidentifikator = UNG_PERSON_FNR_2018): AktivitetsloggFilter = AktivitetsloggFilter { kontekst ->
            kontekst.kontekstMap["fødselsnummer"] == personidentifikator.toString()
        }

        internal fun arbeidsgiver(orgnummer: String): AktivitetsloggFilter = AktivitetsloggFilter { kontekst ->
            kontekst.kontekstType == "Arbeidsgiver" && kontekst.kontekstMap["organisasjonsnummer"] == orgnummer
        }
    }

    fun filtrer(kontekst: SpesifikkKontekst): Boolean
}

internal fun assertActivities(person: Person) {
    assertTrue(person.personLogg.aktiviteter.isNotEmpty(), person.personLogg.toString())
}
