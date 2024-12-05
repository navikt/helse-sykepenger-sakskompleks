package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.person.inntekt.Inntektsmelding.Companion.avklarSykepengegrunnlag
import no.nav.helse.person.inntekt.Inntektsmelding.Companion.finnInntektsmeldingForSkjæringstidspunkt
import no.nav.helse.person.inntekt.Inntektsopplysning.Inntektskilde
import no.nav.helse.person.inntekt.Skatteopplysning.Inntekttype.LØNNSINNTEKT
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class InntektsopplysningTest {
    private companion object {
        private val INNTEKT = 25000.månedlig
    }

    @Test
    fun overstyres() {
        val im1 = Inntektsmelding(1.januar, UUID.randomUUID(), INNTEKT)
        val im2 = Inntektsmelding(1.januar, UUID.randomUUID(), INNTEKT)
        val saksbehandler1 = im1.overstyresAv(Saksbehandler(20.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now()))
        val saksbehandler2 = Saksbehandler(20.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val saksbehandler3 = Saksbehandler(20.januar, UUID.randomUUID(), 30000.månedlig, "", null, LocalDateTime.now())
        val saksbehandler4 = Saksbehandler(20.januar, UUID.randomUUID(), INGEN, "", null, LocalDateTime.now())
        val ikkeRapportert = IkkeRapportert(1.januar, UUID.randomUUID(), LocalDateTime.now())

        assertEquals(saksbehandler1, im1.overstyresAv(saksbehandler1))
        assertEquals(saksbehandler1, saksbehandler1.overstyresAv(saksbehandler2))
        assertEquals(saksbehandler3, saksbehandler1.overstyresAv(saksbehandler3))
        assertEquals(im1, im1.overstyresAv(im2))
        assertEquals(saksbehandler4, ikkeRapportert.overstyresAv(saksbehandler4))
        assertEquals(im1, ikkeRapportert.overstyresAv(im1))
    }

    @Test
    fun `opphøre, gjøre om, avvikle, tilbakestille eller kansellere`() {
        val im = Inntektsmelding(1.januar, UUID.randomUUID(), INNTEKT)
        val saksbehandler = im.overstyresAv(Saksbehandler(20.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now()))
        val skjønnsmessigFastsatt1 = im.overstyresAv(SkjønnsmessigFastsatt(1.januar, UUID.randomUUID(), 20000.månedlig, LocalDateTime.now()))
        val skjønnsmessigFastsatt2 = saksbehandler.overstyresAv(SkjønnsmessigFastsatt(1.januar, UUID.randomUUID(), 20000.månedlig, LocalDateTime.now()))
        val skjønnsmessigFastsatt3 = skjønnsmessigFastsatt2.overstyresAv(SkjønnsmessigFastsatt(1.januar, UUID.randomUUID(), 20000.månedlig, LocalDateTime.now()))
        val ikkeRapportert = IkkeRapportert(1.januar, UUID.randomUUID(), LocalDateTime.now())
        val skatt = SkattSykepengegrunnlag(UUID.randomUUID(), 1.februar, emptyList(), emptyList())

        assertEquals(im, im.omregnetÅrsinntekt())
        assertEquals(saksbehandler, saksbehandler.omregnetÅrsinntekt())
        assertEquals(im, skjønnsmessigFastsatt1.omregnetÅrsinntekt())
        assertEquals(saksbehandler, skjønnsmessigFastsatt2.omregnetÅrsinntekt())
        assertEquals(saksbehandler, skjønnsmessigFastsatt3.omregnetÅrsinntekt())
        assertEquals(ikkeRapportert, ikkeRapportert.omregnetÅrsinntekt())
        assertEquals(skatt, skatt.omregnetÅrsinntekt())
    }

    @Test
    fun `inntektsmelding-likhet`() {
        val hendelsesId = UUID.randomUUID()
        val im1 = Inntektsmelding(1.januar, hendelsesId, INNTEKT)
        val im2 = Inntektsmelding(1.januar, hendelsesId, INNTEKT)

        assertEquals(im1, im2)
        assertFalse(im1.kanLagres(im2))
        assertFalse(im2.kanLagres(im1))
    }


    @Test
    fun `inntektsmelding-ulikhet`() {
        val im1 = Inntektsmelding(1.januar, UUID.randomUUID(), INNTEKT)
        val im2 = Inntektsmelding(2.januar, UUID.randomUUID(), INNTEKT)

        assertNotEquals(im1, im2)
        assertTrue(im1.kanLagres(im2))
        assertTrue(im2.kanLagres(im1))
    }

    @Test
    fun `saksbehandler-likhet`() {
        val saksbehandler1 = Saksbehandler(20.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val saksbehandler2 = Saksbehandler(20.januar, UUID.randomUUID(), 25000.månedlig, "", null, LocalDateTime.now())

        assertNotEquals(saksbehandler1, saksbehandler2)
    }

    @Test
    fun `turnering - inntektsmelding vs inntektsmelding`() {
        val im1 = Inntektsmelding(1.januar, UUID.randomUUID(), INNTEKT, Inntektskilde.Arbeidsgiver, LocalDateTime.now())
        val im2 = Inntektsmelding(1.januar, UUID.randomUUID(), INNTEKT, Inntektskilde.Arbeidsgiver, LocalDateTime.now().plusSeconds(1))

        assertEquals(im2, listOf(im1, im2).finnInntektsmeldingForSkjæringstidspunkt(1.januar, null))
    }

    @Test
    fun `turnering - skatt vs inntektsmelding`() {
        val im = Inntektsmelding(10.februar, UUID.randomUUID(), INNTEKT)
        val skatt1 = SkatteopplysningerForSykepengegrunnlag(
            arbeidsgiver = "orgnr",
            hendelseId = UUID.randomUUID(),
            skjæringstidspunkt = 1.februar,
            inntektsopplysninger = listOf(
                Skatteopplysning(
                    hendelseId = UUID.randomUUID(),
                    beløp = 25000.månedlig,
                    måned = 1.januar.yearMonth,
                    type = LØNNSINNTEKT,
                    fordel = "",
                    beskrivelse = "",
                    tidsstempel = LocalDateTime.now()
                )
            ),
            ansattPerioder = listOf(
                SkatteopplysningerForSykepengegrunnlag.AnsattPeriode(LocalDate.EPOCH, null),
            ),
            tidsstempel = LocalDateTime.now()
        )
        val skatt2 = SkatteopplysningerForSykepengegrunnlag(
            arbeidsgiver = "orgnr",
            hendelseId = UUID.randomUUID(),
            skjæringstidspunkt = 31.januar,
            inntektsopplysninger = listOf(
                Skatteopplysning(
                    hendelseId = UUID.randomUUID(),
                    beløp = 25000.månedlig,
                    måned = 1.desember(2017).yearMonth,
                    type = LØNNSINNTEKT,
                    fordel = "",
                    beskrivelse = "",
                    tidsstempel = LocalDateTime.now()
                )
            ),
            ansattPerioder = listOf(
                SkatteopplysningerForSykepengegrunnlag.AnsattPeriode(LocalDate.EPOCH, null),
            ),
            tidsstempel = LocalDateTime.now()
        )

        assertSame(im, listOf(im).avklarSykepengegrunnlag(1.februar, 10.februar, skatt1))
        assertInstanceOf(SkattSykepengegrunnlag::class.java, listOf(im).avklarSykepengegrunnlag(31.januar, 10.februar, skatt2))
        assertSame(im, im.avklarSykepengegrunnlag(skatt1.avklarSomSykepengegrunnlag(1.februar)!!))
        assertInstanceOf(SkattSykepengegrunnlag::class.java, im.avklarSykepengegrunnlag(skatt2.avklarSomSykepengegrunnlag(31.januar)!!))
    }
}
