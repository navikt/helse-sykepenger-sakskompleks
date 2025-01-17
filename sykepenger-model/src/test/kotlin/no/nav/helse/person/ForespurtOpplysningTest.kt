package no.nav.helse.person

import no.nav.helse.person.PersonObserver.ForespurtOpplysning.Companion.toJsonMap
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForespurtOpplysningTest {

    @Test
    fun `serialiserer ForespurtOpplysning med Inntekt riktig`() {

        val forespurteOpplysninger = listOf(
            PersonObserver.Inntekt,
            PersonObserver.Arbeidsgiverperiode,
            PersonObserver.Refusjon
        )

        val expectedJson = forespurteOpplysningerMap()
        assertEquals(expectedJson, forespurteOpplysninger.toJsonMap())
    }

    @Test
    fun `serialiserer ForespurtOpplysning med FastsattInntekt riktig`() {

        val forespurteOpplysninger = listOf(
            PersonObserver.FastsattInntekt(30000.månedlig),
            PersonObserver.Arbeidsgiverperiode,
            PersonObserver.Refusjon
        )

        val expectedJson = forespurteOpplysningerMedFastsattInntektMap()
        assertEquals(expectedJson, forespurteOpplysninger.toJsonMap())
    }

    private fun forespurteOpplysningerMedFastsattInntektMap() = listOf(
        mapOf(
            "opplysningstype" to "FastsattInntekt",
            "fastsattInntekt" to 30000.0,
        ),
        mapOf(
            "opplysningstype" to "Arbeidsgiverperiode"
        ),
        mapOf(
            "opplysningstype" to "Refusjon",
            "forslag" to emptyList<Nothing>()
        )
    )

    private fun forespurteOpplysningerMap() = listOf(
        mapOf(
            "opplysningstype" to "Inntekt",
            "forslag" to mapOf(
                "forrigeInntekt" to null
            ),
        ),
        mapOf(
            "opplysningstype" to "Arbeidsgiverperiode"
        ),
        mapOf(
            "opplysningstype" to "Refusjon",
            "forslag" to emptyList<Any>(),
        )
    )
}
