package no.nav.helse.serde

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.serde.PersonData.ArbeidsgiverData.PeriodeData
import no.nav.helse.serde.PersonData.ArbeidsgiverData.VedtaksperiodeData
import no.nav.helse.serde.PersonData.ArbeidsgiverData.VedtaksperiodeData.TilstandTypeData
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

internal class VedtaksperiodeDataTest {

    @Test
    fun `deserialisere og serialisere vedtaksperiode med egenmeldingsdager`() {
        val id = UUID.fromString("a79fd48e-f30e-4aeb-b2a3-a196167810ad")

        @Language("JSON")
        val json = """{
          "id": "$id",
          "tilstand": "AVVENTER_INNTEKTSMELDING",
          "skjæringstidspunkt": "2018-01-01",
          "behandlinger": [],
          "opprettet": "2018-01-01T01:00:01",
          "oppdatert": "2018-01-01T01:00:01",
          "egenmeldingsperioder": [
            { "fom": "2018-01-01", "tom": "2018-01-02" },
            { "fom": "2018-01-04", "tom": "2018-01-07" }
          ] 
        }"""

        val actual = serdeObjectMapper.readValue<VedtaksperiodeData>(json)
        val expected = VedtaksperiodeData(
            id = id,
            tilstand = TilstandTypeData.AVVENTER_INNTEKTSMELDING,
            skjæringstidspunkt = LocalDate.of(2018, 1, 1),
            behandlinger = emptyList(),
            egenmeldingsperioder = listOf(
                PeriodeData(LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 2)),
                PeriodeData(LocalDate.of(2018, 1, 4), LocalDate.of(2018, 1, 7))
            ),
            opprettet = LocalDateTime.of(2018, 1, 1, 1, 0, 1),
            oppdatert = LocalDateTime.of(2018, 1, 1, 1, 0, 1)
        )

        assertEquals(expected, actual)
        JSONAssert.assertEquals(json, serdeObjectMapper.writeValueAsString(actual), JSONCompareMode.NON_EXTENSIBLE)
    }
}