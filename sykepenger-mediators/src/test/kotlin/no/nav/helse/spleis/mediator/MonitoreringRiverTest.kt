package no.nav.helse.spleis.mediator

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spleis.monitorering.MonitoreringRiver
import no.nav.helse.spleis.monitorering.RegelmessigAvstemming
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MonitoreringRiverTest {

    @Test
    fun `Både avsender og Spleis skal være system participating services`() {
        val sprute = TestRapid().apply {
            MonitoreringRiver(this, RegelmessigAvstemming { 2 })
        }

        @Language("JSON")
        val spruteMelding = """
        {
          "@event_name": "minutt",
          "@opprettet": "2023-05-12T08:45:03.327320577"
        }
        """

        sprute.sendTestMessage(spruteMelding)
        val slackMelding = sprute.inspektør.message(0)
        assertEquals("slackmelding", slackMelding.path("@event_name").asText())
        assertEquals("ERROR", slackMelding.path("level").asText())
        assertEquals("Det er 2 personer som ikke er avstemt på over en måned!", slackMelding.path("melding").asText())
        val systemParticipatingServices = slackMelding.path("system_participating_services").size()
        assertEquals(2, systemParticipatingServices)
    }
}