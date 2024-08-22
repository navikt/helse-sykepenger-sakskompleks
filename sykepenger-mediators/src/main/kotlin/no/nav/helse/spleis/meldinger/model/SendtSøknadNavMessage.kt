package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import java.time.LocalDate
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Personopplysninger

// Understands a JSON message representing a Søknad that is sent to NAV
internal class SendtSøknadNavMessage(packet: JsonMessage, private val builder: SendtSøknadBuilder = SendtSøknadBuilder()) : SøknadMessage(packet, builder) {

    override fun _behandle(mediator: IHendelseMediator, personopplysninger: Personopplysninger, packet: JsonMessage, context: MessageContext) {
        builder.sendt(packet["sendtNav"].asLocalDateTime())
        byggSendtSøknad(builder, packet)
        mediator.behandle(personopplysninger, this, builder.build(), context, packet["historiskeFolkeregisteridenter"].map(JsonNode::asText).map(String::somPersonidentifikator).toSet())
    }

    internal companion object {
        internal fun byggSendtSøknad(builder: SendtSøknadBuilder, packet: JsonMessage) {
            builder.permittert(packet["permitteringer"].takeIf(JsonNode::isArray)?.takeUnless { it.isEmpty }?.let { true } ?: false)
            builder.egenmeldinger(packet["egenmeldingsdagerFraSykmelding"]
                .takeIf(JsonNode::isArray)
                ?.map { LocalDate.parse(it.asText()) }
                ?.grupperSammenhengendePerioderMedHensynTilHelg()
                ?: emptyList()
            )
            packet["merknaderFraSykmelding"].takeIf(JsonNode::isArray)?.forEach {
                builder.merknader(it.path("type").asText(), it.path("beskrivelse").takeUnless { it.isMissingOrNull() }?.asText())
            }
            packet["papirsykmeldinger"].forEach {
                builder.papirsykmelding(fom = it.path("fom").asLocalDate(), tom = it.path("tom").asLocalDate())
            }
            val ikkeJobbetIDetSisteFraAnnetArbeidsforhold = harSvartNeiOmIkkeJobbetIDetSisteFraAnnetArbeidsforhold(packet["sporsmal"])
            builder.ikkeJobbetIDetSisteFraAnnetArbeidsforhold(ikkeJobbetIDetSisteFraAnnetArbeidsforhold)
            val inntektskilder = andreInntektskilder(packet["andreInntektskilder"], ikkeJobbetIDetSisteFraAnnetArbeidsforhold)
            builder.inntektskilde(inntektskilder.isNotEmpty())

            packet["fravar"].forEach { fravær ->
                val fraværstype = fravær["type"].asText()
                val fom = fravær.path("fom").asLocalDate()
                val tom = fravær.path("tom").takeUnless { it.isMissingOrNull() }?.asLocalDate()
                builder.fravær(fraværstype, fom, tom)
            }
            packet["opprinneligSendt"].takeUnless(JsonNode::isMissingOrNull)?.let {
                builder.opprinneligSendt(it.asLocalDateTime())
            }
            builder.arbeidsgjennopptatt(packet["arbeidGjenopptatt"].asOptionalLocalDate())
            builder.utenlandskSykmelding(packet["utenlandskSykmelding"].asBoolean(false))
            builder.sendTilGosys(packet["sendTilGosys"].asBoolean(false))
        }

        private fun andreInntektskilder(andreInntektskilder: JsonNode, ikkeJobbetIDetSisteFraAnnetArbeidsforhold: Boolean): List<String> {
            if (andreInntektskilder !is ArrayNode) return emptyList()
            // fjerner ANDRE_ARBEIDSFORHOLD dersom <ikkeJobbetIDetSisteFraAnnetArbeidsforhold> er satt til true
            return andreInntektskilder
                .map { it.path("type").asText() }
                .filterNot { kilde -> kilde == "ANDRE_ARBEIDSFORHOLD" && ikkeJobbetIDetSisteFraAnnetArbeidsforhold }
        }

        private fun harSvartNeiOmIkkeJobbetIDetSisteFraAnnetArbeidsforhold(listeAvSpørsmål: JsonNode): Boolean {
            val svarene = spørsmål(listeAvSpørsmål)
            val svarPåOmJobbetIDetSiste = svarene
                .firstOrNull { it.path("tag").asText() == "INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD_JOBBET_I_DET_SISTE" }
                ?.path("svar")
                ?.singleOrNull()
            return svarPåOmJobbetIDetSiste?.path("verdi")?.asText() == "NEI"
        }

        private fun spørsmål(listeAvSpørsmål: JsonNode): List<JsonNode> {
            if (listeAvSpørsmål !is ArrayNode) return emptyList()
            return listeAvSpørsmål.flatMap { spørsmål ->
                listOf(spørsmål) + spørsmål(spørsmål.path("undersporsmal"))
            }
        }
    }
}
