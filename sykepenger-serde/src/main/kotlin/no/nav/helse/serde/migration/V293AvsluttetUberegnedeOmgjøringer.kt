package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal class V293AvsluttetUberegnedeOmgjøringer: JsonMigration(version = 293) {
    override val description = "avslutter uberegnede omgjøringer for auu-perioder"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        val aktørId = jsonNode.path("aktørId").asText()
        val fnr = jsonNode.path("fødselsnummer").asText()
        MDC.putCloseable("aktørId", aktørId).use {
            jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
                val orgnr = arbeidsgiver.path("organisasjonsnummer").asText()
                MDC.putCloseable("orgnr", orgnr).use {
                    migrerArbeidsgiver(arbeidsgiver)
                }
            }
        }
    }

    private fun migrerArbeidsgiver(arbeidsgiver: JsonNode) {
        arbeidsgiver.path("vedtaksperioder").forEach { vedtaksperiode ->
            migrerVedtaksperiode(vedtaksperiode)
        }
    }

    private fun migrerVedtaksperiode(vedtaksperiode: JsonNode) {
        if (vedtaksperiode.path("tilstand").asText() != "AVSLUTTET_UTEN_UTBETALING") return

        val generasjoner = vedtaksperiode.path("generasjoner") as ArrayNode
        val siste = generasjoner.last()
        if (siste.path("tilstand").asText() != "UBEREGNET_OMGJØRING") return
        val avsluttet = vedtaksperiode.path("oppdatert").asText()

        sikkerLogg.info("lukker behandling=${siste.path("id").asText()} for {}", kv("vedtaksperiodeId", vedtaksperiode.path("id").asText()))
        siste as ObjectNode
        siste.put("tilstand", "AVSLUTTET_UTEN_VEDTAK")
        siste.put("avsluttet", avsluttet)
    }

    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

    }
}