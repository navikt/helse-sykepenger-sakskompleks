package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal class V294RenameTilBehandlinger: JsonMigration(version = 294) {
    override val description = "renamer generasjoner til behandlinger i json"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("vedtaksperioder").forEach { migrerVedtaksperiode(it) }
            arbeidsgiver.path("forkastede").forEach { migrerVedtaksperiode(it.path("vedtaksperiode")) }
        }
    }

    private fun migrerVedtaksperiode(node: JsonNode) {
        val generasjoner = node.path("generasjoner").deepCopy<ArrayNode>()
        node as ObjectNode
        node.set<ArrayNode>("behandlinger", generasjoner)
        node.remove("generasjoner")
    }
}