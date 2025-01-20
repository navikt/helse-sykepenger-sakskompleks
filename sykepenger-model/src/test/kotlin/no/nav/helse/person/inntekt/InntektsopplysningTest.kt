package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.person.inntekt.Inntektsmeldinginntekt.Kilde
import no.nav.helse.testhelpers.assertInstanceOf
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class InntektsopplysningTest {
    private companion object {
        private val INNTEKT = 25000.månedlig
    }

    @Test
    fun `inntektsmelding-likhet`() {
        val hendelsesId = UUID.randomUUID()
        val im1 = inntektsmeldinginntekt(1.januar, hendelsesId)
        val im2 = inntektsmeldinginntekt(1.januar, hendelsesId)

        assertFalse(im1.kanLagres(im2))
        assertFalse(im2.kanLagres(im1))
    }

    @Test
    fun `inntektsmelding-ulikhet`() {
        val im1 = inntektsmeldinginntekt(1.januar, UUID.randomUUID())
        val im2 = inntektsmeldinginntekt(2.januar, UUID.randomUUID())

        assertTrue(im1.kanLagres(im2))
        assertTrue(im2.kanLagres(im1))
    }

    @Test
    fun `saksbehandler-likhet`() {
        val ikkeRapportert = SkattSykepengegrunnlag.ikkeRapportert(1.januar, UUID.randomUUID())
        val saksbehandler1 = Saksbehandler(UUID.randomUUID(), Inntektsdata(UUID.randomUUID(), 20.januar, 20000.månedlig, LocalDateTime.now()), ikkeRapportert)
        val saksbehandler2 = Saksbehandler(UUID.randomUUID(), Inntektsdata(UUID.randomUUID(), 20.januar, 25000.månedlig, LocalDateTime.now()), ikkeRapportert)

        assertFalse(saksbehandler1.funksjoneltLik(saksbehandler2))
    }

    @Test
    fun `turnering - skatt vs inntektsmelding`() {
        val im = inntektsmeldinginntekt(10.februar, UUID.randomUUID())
        assertInstanceOf<Arbeidsgiverinntekt>(im.avklarSykepengegrunnlag(SkattSykepengegrunnlag.ikkeRapportert(1.februar, UUID.randomUUID())))
        assertInstanceOf<SkattSykepengegrunnlag>(im.avklarSykepengegrunnlag(SkattSykepengegrunnlag.ikkeRapportert(31.januar, UUID.randomUUID())))
    }

    private fun inntektsmeldinginntekt(dato: LocalDate, hendelseId: UUID) =
        Inntektsmeldinginntekt(UUID.randomUUID(), Inntektsdata(hendelseId, dato, INNTEKT, LocalDateTime.now()), Kilde.Arbeidsgiver)

}
