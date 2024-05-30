package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Grunnbeløp.Companion.`6G`
import no.nav.helse.april
import no.nav.helse.desember
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.inspectors.SubsumsjonInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.Opptjening
import no.nav.helse.etterlevelse.Bokstav
import no.nav.helse.etterlevelse.Ledd
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.Paragraf
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.NullObserver
import no.nav.helse.hendelser.til
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.aktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.deaktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.fastsattÅrsinntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.medInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.overstyrInntekter
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.inntekt.Skatteopplysning.Inntekttype.LØNNSINNTEKT
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje.Companion.ghostdager
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ArbeidsgiverInntektsopplysningTest {

    @Test
    fun ghosttidslinje() {
        val skjæringstidspunkt = 1.januar
        val a1Fom = skjæringstidspunkt
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", a1Fom til 10.januar, Inntektsmelding(a1Fom, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        assertNull(a1Opplysning.ghosttidslinje("a2", skjæringstidspunkt))
        assertEquals(Sykdomstidslinje(), a1Opplysning.ghosttidslinje("a1", 31.desember(2017)))
        assertEquals(ghostdager(skjæringstidspunkt til 31.januar), a1Opplysning.ghosttidslinje("a1", 31.januar))
        assertEquals(ghostdager(skjæringstidspunkt til 5.januar), a1Opplysning.ghosttidslinje("a1", 5.januar))
    }

    @Test
    fun `fastsatt årsinntekt - tilkommen inntekt`() {
        val skjæringstidspunkt = 1.januar
        val a1Fom = skjæringstidspunkt
        val a2Fom = skjæringstidspunkt.plusDays(1)
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", a1Fom til 10.januar, Inntektsmelding(a1Fom, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", a2Fom til LocalDate.MAX, Inntektsmelding(a2Fom, UUID.randomUUID(), 2000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        assertEquals(1000.månedlig, listOf(a1Opplysning, a2Opplysning).fastsattÅrsinntekt(skjæringstidspunkt))
        assertEquals(ghostdager(skjæringstidspunkt til 31.januar), a1Opplysning.ghosttidslinje("a1", 31.januar))
        assertEquals(ghostdager(a2Fom til 31.januar), a2Opplysning.ghosttidslinje("a2", 31.januar))
    }

    @Test
    fun `overstyr inntekter`() {
        val skjæringstidspunkt = 1.januar
        val opptjening = Opptjening.nyOpptjening(emptyList(), skjæringstidspunkt, true, NullObserver)
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 2000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a1Overstyrt = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Saksbehandler(skjæringstidspunkt, UUID.randomUUID(), 3000.månedlig, "", null, LocalDateTime.now()), Refusjonsopplysninger())
        val a3Overstyrt = ArbeidsgiverInntektsopplysning("a3", skjæringstidspunkt til LocalDate.MAX, Saksbehandler(skjæringstidspunkt, UUID.randomUUID(), 4000.månedlig, "", null, LocalDateTime.now()), Refusjonsopplysninger())

        val original = listOf(a1Opplysning, a2Opplysning)
        val new = listOf(a1Overstyrt)

        assertEquals(original, original.overstyrInntekter(skjæringstidspunkt, opptjening, listOf(a1Overstyrt, a1Overstyrt), NullObserver)) { "kan ikke velge mellom inntekter for samme orgnr" }

        assertEquals(emptyList<ArbeidsgiverInntektsopplysning>(), emptyList<ArbeidsgiverInntektsopplysning>().overstyrInntekter(skjæringstidspunkt, opptjening, new, NullObserver))
        assertEquals(original, original.overstyrInntekter(skjæringstidspunkt, opptjening, emptyList(), NullObserver))
        assertEquals(listOf(a1Overstyrt, a2Opplysning), original.overstyrInntekter(skjæringstidspunkt, opptjening, new, NullObserver))
        val forMange = listOf(a1Overstyrt, a3Overstyrt)
        assertEquals(listOf(a1Overstyrt, a2Opplysning), original.overstyrInntekter(skjæringstidspunkt, opptjening, forMange, NullObserver)) { "skal ikke kunne legge til inntekter som ikke finnes fra før" }
    }

    @Test
    fun `ny inntektsmelding uten endring i beløp endrer kun omregnet årsinntekt for skjønnsmessig fastsatt`() {
        val skjæringstidspunkt = 1.januar
        val opptjening = Opptjening.nyOpptjening(emptyList(), skjæringstidspunkt, true, NullObserver)
        val inntektsmeldingA1 = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now())
        val inntektsmeldingA2 = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 2000.månedlig, LocalDateTime.now())
        val inntektsmeldingA3 = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 3000.månedlig, LocalDateTime.now())

        val inntektsmeldingA1Ny = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now())
        val overstyrtA1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val forventetA1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 900.månedlig, inntektsmeldingA1Ny, LocalDateTime.now()), Refusjonsopplysninger())

        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 900.månedlig, inntektsmeldingA1, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 950.månedlig, inntektsmeldingA2, LocalDateTime.now()), Refusjonsopplysninger())
        val a3Opplysning = ArbeidsgiverInntektsopplysning("a3", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 975.månedlig, inntektsmeldingA3, LocalDateTime.now()), Refusjonsopplysninger())

        val original = listOf(a1Opplysning, a2Opplysning, a3Opplysning)
        val expected = listOf(forventetA1Opplysning, a2Opplysning, a3Opplysning)
        val new = listOf(overstyrtA1Opplysning)

        val actual = original.overstyrInntekter(skjæringstidspunkt, opptjening, new, NullObserver)
        assertEquals(expected, actual) { "kan ikke velge mellom inntekter for samme orgnr" }
    }

    @Test
    fun `ny inntektsmelding uten endring i beløp i forhold Skatt endrer kun omregnet årsinntekt for skjønnsmessig fastsatt`() {
        val skjæringstidspunkt = 1.januar
        val opptjening = Opptjening.nyOpptjening(emptyList(), skjæringstidspunkt, true, NullObserver)
        val skattA1 = SkattSykepengegrunnlag(UUID.randomUUID(), skjæringstidspunkt, listOf(
            Skatteopplysning(UUID.randomUUID(), 1000.månedlig, skjæringstidspunkt.minusMonths(1).yearMonth, LØNNSINNTEKT, "", ""),
            Skatteopplysning(UUID.randomUUID(), 1000.månedlig, skjæringstidspunkt.minusMonths(2).yearMonth, LØNNSINNTEKT, "", ""),
            Skatteopplysning(UUID.randomUUID(), 1000.månedlig, skjæringstidspunkt.minusMonths(3).yearMonth, LØNNSINNTEKT, "", "")
        ), emptyList(), LocalDateTime.now())
        val inntektsmeldingA2 = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 2000.månedlig, LocalDateTime.now())
        val inntektsmeldingA3 = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 3000.månedlig, LocalDateTime.now())

        val inntektsmeldingA1Ny = Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now())
        val overstyrtA1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val forventetA1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 900.månedlig, inntektsmeldingA1Ny, LocalDateTime.now()), Refusjonsopplysninger())

        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 900.månedlig, skattA1, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 950.månedlig, inntektsmeldingA2, LocalDateTime.now()), Refusjonsopplysninger())
        val a3Opplysning = ArbeidsgiverInntektsopplysning("a3", skjæringstidspunkt til LocalDate.MAX, SkjønnsmessigFastsatt(UUID.randomUUID(), skjæringstidspunkt, UUID.randomUUID(), 975.månedlig, inntektsmeldingA3, LocalDateTime.now()), Refusjonsopplysninger())

        val original = listOf(a1Opplysning, a2Opplysning, a3Opplysning)
        val expected = listOf(forventetA1Opplysning, a2Opplysning, a3Opplysning)
        val new = listOf(overstyrtA1Opplysning)

        val actual = original.overstyrInntekter(skjæringstidspunkt, opptjening, new, NullObserver)
        assertEquals(expected, actual) { "kan ikke velge mellom inntekter for samme orgnr" }
    }

    @Test
    fun `subsummerer etter overstyring`() {
        val skjæringstidspunkt = 1.april
        val ansattFom = 1.januar
        val orgnummer = "a1"

        val opptjening = Opptjening.nyOpptjening(listOf(
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(orgnummer, listOf(
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold(
                    ansattFom,
                    null,
                    false
                )
            ))
        ), skjæringstidspunkt, true, NullObserver)


        val paragraf = Paragraf.PARAGRAF_8_28
        val ledd = Ledd.LEDD_3
        val bokstav = Bokstav.BOKSTAV_B
        val overstyrtBeløp = 3000.månedlig

        val subsumsjon = Subsumsjon(paragraf.ref, ledd.nummer, bokstav.ref.toString())
        val a1Opplysning = ArbeidsgiverInntektsopplysning(orgnummer, skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a1Overstyrt = ArbeidsgiverInntektsopplysning(orgnummer, skjæringstidspunkt til LocalDate.MAX, Saksbehandler(skjæringstidspunkt, UUID.randomUUID(), overstyrtBeløp, "Jeg bare måtte gjøre det", subsumsjon, LocalDateTime.now()), Refusjonsopplysninger())

        val jurist = MaskinellJurist()
        listOf(a1Opplysning).overstyrInntekter(skjæringstidspunkt, opptjening, listOf(a1Overstyrt), jurist)
        SubsumsjonInspektør(jurist).assertBeregnet(
            paragraf = paragraf,
            versjon = LocalDate.of(2019, 1, 1),
            ledd = ledd,
            punktum = null,
            bokstav = bokstav,
            input = mapOf(
                "organisasjonsnummer" to orgnummer,
                "skjæringstidspunkt" to skjæringstidspunkt,
                "startdatoArbeidsforhold" to ansattFom,
                "overstyrtInntektFraSaksbehandler" to mapOf("dato" to skjæringstidspunkt, "beløp" to overstyrtBeløp.reflection { _, månedlig, _, _ -> månedlig }),
                "forklaring" to "Jeg bare måtte gjøre det"
            ),
            output = mapOf(
                "beregnetGrunnlagForSykepengegrunnlagPrÅr" to overstyrtBeløp.reflection { årlig, _, _, _ -> årlig},
                "beregnetGrunnlagForSykepengegrunnlagPrMåned" to overstyrtBeløp.reflection { _, månedlig, _, _ -> månedlig }
            )
        )
    }

    @Test
    fun `deaktiverer en inntekt`() {
        val skjæringstidspunkt = 1.januar
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, IkkeRapportert(skjæringstidspunkt, UUID.randomUUID(), LocalDateTime.now()), Refusjonsopplysninger())

        val opprinnelig = listOf(a1Opplysning, a2Opplysning)
        val (aktive, deaktiverte) = opprinnelig.deaktiver(emptyList(), "a2", "Denne må bort", NullObserver)
        assertEquals(a1Opplysning, aktive.single())
        assertEquals(a2Opplysning, deaktiverte.single())

        val (nyDeaktivert, nyAktivert) = deaktiverte.aktiver(aktive, "a2", "Jeg gjorde en feil, jeg angrer!", NullObserver)
        assertEquals(0, nyDeaktivert.size)
        assertEquals(opprinnelig, nyAktivert)

        assertThrows<RuntimeException> { opprinnelig.deaktiver(emptyList(), "a3", "jeg vil deaktivere noe som ikke finnes", NullObserver) }
        assertThrows<RuntimeException> { emptyList<ArbeidsgiverInntektsopplysning>().aktiver(opprinnelig, "a3", "jeg vil aktivere noe som ikke finnes", NullObserver) }
    }

    @Test
    fun `subsummerer deaktivering`() {
        val skjæringstidspunkt = 1.januar
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, IkkeRapportert(skjæringstidspunkt, UUID.randomUUID(), LocalDateTime.now()), Refusjonsopplysninger())

        val jurist = MaskinellJurist()
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)
        val (aktive, deaktiverte) = opprinnelig.deaktiver(emptyList(), "a2", "Denne må bort", jurist)
        assertEquals(a1Opplysning, aktive.single())
        assertEquals(a2Opplysning, deaktiverte.single())
        SubsumsjonInspektør(jurist).assertOppfylt(
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
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", skjæringstidspunkt til LocalDate.MAX, Inntektsmelding(skjæringstidspunkt, UUID.randomUUID(), 1000.månedlig, LocalDateTime.now()), Refusjonsopplysninger())
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", skjæringstidspunkt til LocalDate.MAX, IkkeRapportert(skjæringstidspunkt, UUID.randomUUID(), LocalDateTime.now()), Refusjonsopplysninger())

        val jurist = MaskinellJurist()
        val opprinneligAktive = listOf(a1Opplysning)
        val opprinneligDeaktiverte = listOf(a2Opplysning)

        val (deaktiverte, aktive) = opprinneligDeaktiverte.aktiver(opprinneligAktive, "a2", "Denne må tilbake", jurist)
        assertEquals(listOf(a1Opplysning, a2Opplysning), aktive)
        assertEquals(0, deaktiverte.size)
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(
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
            inntektsopplysning = Infotrygd(
                id = inntektID,
                dato = 1.januar,
                hendelseId = hendelseId,
                beløp = 25000.månedlig,
                tidsstempel = tidsstempel
            ),
            refusjonsopplysninger = Refusjonsopplysninger()
        )
        assertEquals(
            inntektsopplysning1,
            ArbeidsgiverInntektsopplysning(
                orgnummer = "orgnummer",
                gjelder = 1.januar til LocalDate.MAX,
                inntektsopplysning = Infotrygd(
                    id = inntektID,
                    dato = 1.januar,
                    hendelseId = hendelseId,
                    beløp = 25000.månedlig,
                    tidsstempel = tidsstempel
                ),
                refusjonsopplysninger = Refusjonsopplysninger()
            )
        )
        assertNotEquals(
            inntektsopplysning1,
            ArbeidsgiverInntektsopplysning(
                orgnummer = "orgnummer2",
                gjelder = 1.januar til LocalDate.MAX,
                inntektsopplysning = Infotrygd(
                    id = inntektID,
                    dato = 1.januar,
                    hendelseId = hendelseId,
                    beløp = 25000.månedlig,
                    tidsstempel = tidsstempel
                ),
                refusjonsopplysninger = Refusjonsopplysninger()
            )
        )
        assertNotEquals(
            inntektsopplysning1,
            ArbeidsgiverInntektsopplysning(
                orgnummer = "orgnummer",
                gjelder = 1.januar til LocalDate.MAX,
                inntektsopplysning = Infotrygd(
                    id = inntektID,
                    dato = 5.januar,
                    hendelseId = hendelseId,
                    beløp = 25000.månedlig,
                    tidsstempel = tidsstempel
                ),
                refusjonsopplysninger = Refusjonsopplysninger()
            )
        )
    }

    @Test
    fun `setter negativt omregnet årsinntekt til 0`() {
        val arbeidsgiverInntektsopplysning = ArbeidsgiverInntektsopplysning(
            "orgnummer",
            1.januar til LocalDate.MAX,
            SkattSykepengegrunnlag(UUID.randomUUID(), 1.januar, inntektsopplysninger = listOf(
                    Skatteopplysning(
                        hendelseId = UUID.randomUUID(),
                        beløp = (-2500).daglig,
                        måned = desember(2017),
                        type = LØNNSINNTEKT,
                        fordel = "fordel",
                        beskrivelse = "beskrivelse"
                    )
                ),
                emptyList()
            ),
            Refusjonsopplysninger()
        )

        val økonomi = listOf(arbeidsgiverInntektsopplysning).medInntekt(
            organisasjonsnummer = "orgnummer",
            `6G` = `6G`.beløp(1.januar),
            dato = 1.januar,
            skjæringstidspunkt = 1.januar,
            økonomi = Økonomi.sykdomsgrad(100.prosent),
            regler = NormalArbeidstaker,
            subsumsjonslogg = NullObserver,
        )
        assertNotNull(økonomi)
        assertEquals(Inntekt.INGEN, økonomi.inspektør.dekningsgrunnlag)
        assertEquals(Inntekt.INGEN, økonomi.inspektør.aktuellDagsinntekt)
    }
}