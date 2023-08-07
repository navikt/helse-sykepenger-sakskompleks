package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal class V257UtbetalingUtbetalingstidslinje : JsonMigration(version = 257) {
    override val description = "flytter dager på Utbetaling ett nivå opp"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("utbetalinger").forEach { utbetaling ->
                val dager = utbetaling.path("utbetalingstidslinje").path("dager")
                (utbetaling as ObjectNode).set<JsonNode>("dager", dager)
            }
        }
    }
}