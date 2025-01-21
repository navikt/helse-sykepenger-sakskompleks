package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.dsl.SubsumsjonsListLog
import no.nav.helse.etterlevelse.BehandlingSubsumsjonslogg
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Paragraf
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.SubsumsjonInspektør
import no.nav.helse.januar
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.aktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.deaktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.overstyrMedInntektsmelding
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.overstyrMedSaksbehandler
import no.nav.helse.person.inntekt.Skatteopplysning.Inntekttype.LØNNSINNTEKT
import no.nav.helse.testhelpers.assertInstanceOf
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ArbeidsgiverInntektsopplysningTest {

    private val subsumsjonslogg = SubsumsjonsListLog()
    private val jurist = BehandlingSubsumsjonslogg(
        subsumsjonslogg, listOf(
        Subsumsjonskontekst(KontekstType.Fødselsnummer, "fnr"),
        Subsumsjonskontekst(KontekstType.Organisasjonsnummer, "orgnr"),
        Subsumsjonskontekst(KontekstType.Vedtaksperiode, "${UUID.randomUUID()}"),
    )
    )

    @Test
    fun `overstyr inntekter`() {
        val skjæringstidspunkt = 1.januar
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig), null, null)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntekt(skjæringstidspunkt, 2000.månedlig), null, null)
        val a1Overstyrt = OverstyrArbeidsgiveropplysninger.KorrigertArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsdata(UUID.randomUUID(), skjæringstidspunkt, 3000.månedlig, LocalDateTime.now()), OverstyrArbeidsgiveropplysninger.Overstyringbegrunnelse("", null))
        val a3Overstyrt = OverstyrArbeidsgiveropplysninger.KorrigertArbeidsgiverInntektsopplysning("a3", skjæringstidspunkt til LocalDate.MAX, Inntektsdata(UUID.randomUUID(), skjæringstidspunkt, 4000.månedlig, LocalDateTime.now()), OverstyrArbeidsgiveropplysninger.Overstyringbegrunnelse("", null))

        val original = listOf(a1Opplysning, a2Opplysning)
        val expected = listOf(a1Opplysning, a2Opplysning)
        val new = listOf(a1Overstyrt)

        assertEquals(expected, original.overstyrMedSaksbehandler(listOf(a1Overstyrt, a1Overstyrt)))

        original.overstyrMedSaksbehandler(new).also {
            assertEquals(2, it.size)
            assertEquals(3000.månedlig, it[0].fastsattÅrsinntekt)
            assertEquals(2000.månedlig, it[1].fastsattÅrsinntekt)
            assertNotNull(it[0].korrigertInntekt)
            assertInstanceOf<Arbeidsgiverinntekt>(it[1].inntektsopplysning)
        }
        val forMange = listOf(a1Overstyrt, a3Overstyrt)
        original.overstyrMedSaksbehandler(forMange).also {
            assertEquals(2, it.size)
            assertEquals(3000.månedlig, it[0].fastsattÅrsinntekt)
            assertEquals(2000.månedlig, it[1].fastsattÅrsinntekt)
            assertNotNull(it[0].korrigertInntekt)
            assertInstanceOf<Arbeidsgiverinntekt>(it[1].inntektsopplysning)
        }
    }

    @Test
    fun `ny inntektsmelding uten endring i beløp endrer kun omregnet årsinntekt for skjønnsmessig fastsatt`() {
        val skjæringstidspunkt = 1.januar
        val arbeidsgiverinntektA1 = arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig)
        val arbeidsgiverinntektA2 = arbeidsgiverinntekt(skjæringstidspunkt, 2000.månedlig)
        val arbeidsgiverinntektA3 = arbeidsgiverinntekt(skjæringstidspunkt, 3000.månedlig)

        val arbeidsgiverinntektA1Ny = arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig)
        val overstyrtA1Opplysning = arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig)
        val forventetA1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA1Ny, null, skjønnsmessigFastsatt(skjæringstidspunkt, 900.månedlig))

        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA1, null, skjønnsmessigFastsatt(skjæringstidspunkt, 900.månedlig))
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA2, null, skjønnsmessigFastsatt(skjæringstidspunkt, 950.månedlig))
        val a3Opplysning = ArbeidsgiverInntektsopplysning("a3", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA3, null, skjønnsmessigFastsatt(skjæringstidspunkt, 975.månedlig))

        val original = listOf(a1Opplysning, a2Opplysning, a3Opplysning)
        val expected = listOf(forventetA1Opplysning, a2Opplysning, a3Opplysning)

        val actual = original.overstyrMedInntektsmelding("a1", overstyrtA1Opplysning)
        assertTrue(expected.funksjoneltLik(actual)) { "kan ikke velge mellom inntekter for samme orgnr" }
    }

    @Test
    fun `ny inntektsmelding uten endring i beløp i forhold Skatt endrer kun omregnet årsinntekt for skjønnsmessig fastsatt`() {
        val skjæringstidspunkt = 1.januar
        val skattA1 = skattSykepengegrunnlag(
            UUID.randomUUID(), skjæringstidspunkt, listOf(
            Skatteopplysning(UUID.randomUUID(), 1000.månedlig, skjæringstidspunkt.minusMonths(1).yearMonth, LØNNSINNTEKT, "", ""),
            Skatteopplysning(UUID.randomUUID(), 1000.månedlig, skjæringstidspunkt.minusMonths(2).yearMonth, LØNNSINNTEKT, "", ""),
            Skatteopplysning(UUID.randomUUID(), 1000.månedlig, skjæringstidspunkt.minusMonths(3).yearMonth, LØNNSINNTEKT, "", "")
        )
        )
        val arbeidsgiverinntektA2 = arbeidsgiverinntekt(skjæringstidspunkt, 2000.månedlig)
        val arbeidsgiverinntektA3 = arbeidsgiverinntekt(skjæringstidspunkt, 3000.månedlig)

        val arbeidsgiverinntektA1Ny = arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig)
        val overstyrtA1Opplysning = arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig)
        val forventetA1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA1Ny, null, skjønnsmessigFastsatt(skjæringstidspunkt, 900.månedlig))

        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, skattA1, null, skjønnsmessigFastsatt(skjæringstidspunkt, 900.månedlig))
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA2, null, skjønnsmessigFastsatt(skjæringstidspunkt, 950.månedlig))
        val a3Opplysning = ArbeidsgiverInntektsopplysning("a3", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntektA3, null, skjønnsmessigFastsatt(skjæringstidspunkt, 975.månedlig))

        val original = listOf(a1Opplysning, a2Opplysning, a3Opplysning)
        val expected = listOf(forventetA1Opplysning, a2Opplysning, a3Opplysning)

        val actual = original.overstyrMedInntektsmelding("a1", overstyrtA1Opplysning)
        assertTrue(expected.funksjoneltLik(actual))
    }

    @Test
    fun `deaktiverer en inntekt`() {
        val skjæringstidspunkt = 1.januar
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig), null, null)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, SkattSykepengegrunnlag.ikkeRapportert(skjæringstidspunkt, UUID.randomUUID()), null, null)

        val opprinnelig = listOf(a1Opplysning, a2Opplysning)
        val (aktive, deaktiverte) = opprinnelig.deaktiver(emptyList(), "a2", "Denne må bort", EmptyLog)
        assertEquals(a1Opplysning, aktive.single())
        assertEquals(a2Opplysning, deaktiverte.single())

        val (nyDeaktivert, nyAktivert) = deaktiverte.aktiver(aktive, "a2", "Jeg gjorde en feil, jeg angrer!", EmptyLog)
        assertEquals(0, nyDeaktivert.size)
        assertTrue(opprinnelig.funksjoneltLik(nyAktivert))

        assertThrows<RuntimeException> { opprinnelig.deaktiver(emptyList(), "a3", "jeg vil deaktivere noe som ikke finnes", EmptyLog) }
        assertThrows<RuntimeException> { emptyList<ArbeidsgiverInntektsopplysning>().aktiver(opprinnelig, "a3", "jeg vil aktivere noe som ikke finnes", EmptyLog) }
    }

    @Test
    fun `subsummerer deaktivering`() {
        val skjæringstidspunkt = 1.januar
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig), null, null)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, SkattSykepengegrunnlag.ikkeRapportert(skjæringstidspunkt, UUID.randomUUID()), null, null)

        val opprinnelig = listOf(a1Opplysning, a2Opplysning)
        val (aktive, deaktiverte) = opprinnelig.deaktiver(emptyList(), "a2", "Denne må bort", jurist)
        assertEquals(a1Opplysning, aktive.single())
        assertEquals(a2Opplysning, deaktiverte.single())
        SubsumsjonInspektør(subsumsjonslogg).assertOppfylt(
            paragraf = Paragraf.PARAGRAF_8_15,
            versjon = LocalDate.of(1998, 12, 18),
            ledd = null,
            punktum = null,
            bokstav = null,
            input = mapOf(
                "organisasjonsnummer" to "a2",
                "skjæringstidspunkt" to skjæringstidspunkt,
                "inntekterSisteTreMåneder" to emptyList<Any>(),
                "forklaring" to "Denne må bort"
            ),
            output = mapOf("arbeidsforholdAvbrutt" to "a2")
        )
    }

    @Test
    fun `subsummerer aktivering`() {
        val skjæringstidspunkt = 1.januar
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, arbeidsgiverinntekt(skjæringstidspunkt, 1000.månedlig), null, null)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, SkattSykepengegrunnlag.ikkeRapportert(skjæringstidspunkt, UUID.randomUUID()), null, null)

        val opprinneligAktive = listOf(a1Opplysning)
        val opprinneligDeaktiverte = listOf(a2Opplysning)

        val (deaktiverte, aktive) = opprinneligDeaktiverte.aktiver(opprinneligAktive, "a2", "Denne må tilbake", jurist)
        assertTrue(listOf(a1Opplysning, a2Opplysning).funksjoneltLik(aktive))
        assertEquals(0, deaktiverte.size)
        SubsumsjonInspektør(subsumsjonslogg).assertIkkeOppfylt(
            paragraf = Paragraf.PARAGRAF_8_15,
            versjon = LocalDate.of(1998, 12, 18),
            ledd = null,
            punktum = null,
            bokstav = null,
            input = mapOf(
                "organisasjonsnummer" to "a2",
                "skjæringstidspunkt" to skjæringstidspunkt,
                "inntekterSisteTreMåneder" to emptyList<Any>(),
                "forklaring" to "Denne må tilbake"
            ),
            output = mapOf("aktivtArbeidsforhold" to "a2")
        )
    }

    @Test
    fun equals() {
        val inntektID = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()
        val tidsstempel = LocalDateTime.now()
        val inntektsopplysning1 = ArbeidsgiverInntektsopplysning(
            orgnummer = "orgnummer",
            gjelder = 1.januar til LocalDate.MAX,
            inntektsopplysning = infotrygd(
                id = inntektID,
                dato = 1.januar,
                hendelseId = hendelseId,
                beløp = 25000.månedlig,
                tidsstempel = tidsstempel
            ),
            korrigertInntekt = null,
            skjønnsmessigFastsatt = null
        )
        assertTrue(
            inntektsopplysning1.funksjoneltLik(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = "orgnummer",
                    gjelder = 1.januar til LocalDate.MAX,
                    inntektsopplysning = infotrygd(
                        id = inntektID,
                        dato = 1.januar,
                        hendelseId = hendelseId,
                        beløp = 25000.månedlig,
                        tidsstempel = tidsstempel
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            )
        )
        assertFalse(
            inntektsopplysning1.funksjoneltLik(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = "orgnummer2",
                    gjelder = 1.januar til LocalDate.MAX,
                    inntektsopplysning = infotrygd(
                        id = inntektID,
                        dato = 1.januar,
                        hendelseId = hendelseId,
                        beløp = 25000.månedlig,
                        tidsstempel = tidsstempel
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            )
        )
        assertFalse(
            inntektsopplysning1.funksjoneltLik(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = "orgnummer",
                    gjelder = 1.januar til LocalDate.MAX,
                    inntektsopplysning = infotrygd(
                        id = inntektID,
                        dato = 5.januar,
                        hendelseId = hendelseId,
                        beløp = 25000.månedlig,
                        tidsstempel = tidsstempel
                    ),
                    korrigertInntekt = null,
                    skjønnsmessigFastsatt = null
                )
            )
        )
    }
}

internal fun List<ArbeidsgiverInntektsopplysning>.funksjoneltLik(other: List<ArbeidsgiverInntektsopplysning>): Boolean {
    if (this.size != other.size) return false
    return this
        .zip(other) { a, b -> a.funksjoneltLik(b) }
        .none { it == false }
}

internal fun ArbeidsgiverInntektsopplysning.funksjoneltLik(other: ArbeidsgiverInntektsopplysning): Boolean {
    return this.gjelder == other.gjelder && this.orgnummer == other.orgnummer && this.inntektsopplysning.funksjoneltLik(other.inntektsopplysning)
}
