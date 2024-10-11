package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.til
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.person.inntekt.Saksbehandler
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.asOptionalLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

internal class OverstyrArbeidsgiveropplysningerMessage(packet: JsonMessage) : HendelseMessage(packet) {

    override val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
    private val arbeidsgiveropplysninger = packet.arbeidsgiveropplysninger(skjæringstidspunkt)
    private val refusjonstidslinjer = packet.refusjonstidslinjer()

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) =
        mediator.behandle(
            this, OverstyrArbeidsgiveropplysninger(
                meldingsreferanseId = id,
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiveropplysninger = arbeidsgiveropplysninger,
                opprettet = opprettet,
                refusjonstidslinjer = refusjonstidslinjer
            ),
            context
        )

    private companion object {

        private fun JsonMessage.arbeidsgiveropplysninger(skjæringstidspunkt: LocalDate): List<ArbeidsgiverInntektsopplysning> {
            val arbeidsgivere = get("arbeidsgivere").takeUnless { it.isMissingOrNull() } ?: return emptyList()
            val id = UUID.fromString(get("@id").asText())
            val opprettet = get("@opprettet").asLocalDateTime()
            return arbeidsgivere.map { arbeidsgiveropplysning ->
                val orgnummer = arbeidsgiveropplysning["organisasjonsnummer"].asText()
                val månedligInntekt = arbeidsgiveropplysning["månedligInntekt"].asDouble().månedlig
                val forklaring = arbeidsgiveropplysning["forklaring"].asText()
                val subsumsjon = arbeidsgiveropplysning.path("subsumsjon").asSubsumsjon()

                val fom = arbeidsgiveropplysning.path("fom").takeIf(JsonNode::isTextual)?.asLocalDate() ?: skjæringstidspunkt
                val tom = arbeidsgiveropplysning.path("tom").takeIf(JsonNode::isTextual)?.asLocalDate() ?: LocalDate.MAX
                val saksbehandlerinntekt = Saksbehandler(skjæringstidspunkt, id, månedligInntekt, forklaring, subsumsjon, opprettet)
                val refusjonsopplysninger = arbeidsgiveropplysning["refusjonsopplysninger"].asRefusjonsopplysninger(id, opprettet)

                ArbeidsgiverInntektsopplysning(orgnummer, fom til tom, saksbehandlerinntekt, refusjonsopplysninger)
            }
        }

        private fun JsonNode.asSubsumsjon() = this.takeUnless(JsonNode::isMissingOrNull)?.let {
            Subsumsjon(
                paragraf = it["paragraf"].asText(),
                ledd = it.path("ledd").takeUnless(JsonNode::isMissingOrNull)?.asInt(),
                bokstav = it.path("bokstav").takeUnless(JsonNode::isMissingOrNull)?.asText()
            )
        }

        private fun JsonNode.asRefusjonsopplysninger(meldingsreferanseId: UUID, opprettet: LocalDateTime) = RefusjonsopplysningerBuilder().also { builder ->
            this.map { refusjonsopplysning ->
                builder.leggTil(
                    Refusjonsopplysning(
                        meldingsreferanseId = meldingsreferanseId,
                        fom = refusjonsopplysning.path("fom").asLocalDate(),
                        tom = refusjonsopplysning.path("tom").asOptionalLocalDate(),
                        beløp = refusjonsopplysning.path("beløp").asDouble().månedlig
                    ), opprettet)
            }
        }.build()

        private fun JsonMessage.refusjonstidslinjer(): Map<String, Beløpstidslinje> {
            val id = UUID.fromString(get("@id").asText())
            val opprettet = get("@opprettet").asLocalDateTime()
            return get("arbeidsgivere").associateBy { it.path("organisasjonsnummer").asText() }.mapValues { (_, arbeidsgiver) ->
                arbeidsgiver.path("refusjonsopplysninger").refusjonstidslinje(id, opprettet)
            }
        }

        private fun JsonNode.refusjonstidslinje(meldingsreferanseId: UUID, opprettet: LocalDateTime) : Beløpstidslinje {
            return this.fold(Beløpstidslinje()) { acc, node ->
                val fom = node.path("fom").asLocalDate()
                val tom = node.path("tom").asOptionalLocalDate() ?: fom
                val beløp = node.path("beløp").asDouble().månedlig
                val refusjonstidslinje = Beløpstidslinje.fra(fom til tom, beløp, Kilde(meldingsreferanseId, SAKSBEHANDLER, opprettet))
                refusjonstidslinje + acc
            }
        }
    }
}


