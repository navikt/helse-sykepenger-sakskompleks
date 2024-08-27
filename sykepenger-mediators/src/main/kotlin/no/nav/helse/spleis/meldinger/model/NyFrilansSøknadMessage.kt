package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.person.Frilans
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Personopplysninger

internal class NyFrilansSøknadMessage(packet: JsonMessage, private val builder: NySøknadBuilder = NySøknadBuilder()) : SøknadMessage(packet, builder, Frilans) {
    override fun _behandle(mediator: IHendelseMediator, personopplysninger: Personopplysninger, packet: JsonMessage, context: MessageContext) {
        builder.fremtidigSøknad(packet["fremtidig_søknad"].asBoolean())
        mediator.behandle(personopplysninger, this, builder.build(), context, packet["historiskeFolkeregisteridenter"].map(JsonNode::asText).map(String::somPersonidentifikator).toSet())
    }
}
