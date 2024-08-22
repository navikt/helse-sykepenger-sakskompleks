package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal class V298EgenmeldingsdagerPåVedtaksperiode: JsonMigration(version = 298) {
    override val description = "lagrer egenmeldingsdager på vedtaksperiode"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("vedtaksperioder").forEach { periode ->
                migrerVedtaksperiode(periode)
            }
            arbeidsgiver.path("forkastede").forEach { forkastet ->
                migrerVedtaksperiode(forkastet.path("vedtaksperiode"))
            }
        }
    }

    private fun migrerVedtaksperiode(vedtaksperiode: JsonNode) {
        vedtaksperiode as ObjectNode
        vedtaksperiode.putArray("egenmeldingsperioder")
    }
}