package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V22FjernGruppeId : JsonMigration(version = 22) {
    override val description: String = "Fjerner gruppeId fra vedtaksperiode"

    override fun doMigration(jsonNode: ObjectNode) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("vedtaksperioder").forEach { periode -> (periode as ObjectNode).remove("gruppeId") }
            arbeidsgiver.path("forkastede").forEach { periode -> (periode as ObjectNode).remove("gruppeId") }
        }
    }
}
