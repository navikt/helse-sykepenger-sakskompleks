package no.nav.helse.spleis.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.meldinger.model.HendelseMessage

internal class TestHendelseMessage(
    fnr: String,
    packet: JsonMessage = testPacket(fnr),
    private val tracinginfo: Map<String, Any> = emptyMap()
) : HendelseMessage(packet) {
    override val fødselsnummer = fnr

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        throw NotImplementedError()
    }

    override fun additionalTracinginfo(packet: JsonMessage) =
        tracinginfo

    internal companion object {
        fun testPacket(fødselsnummer: String, extra: Map<String, Any> = emptyMap()) =
            JsonMessage.newMessage("test_event",extra + mapOf(
                "fødselsnummer" to fødselsnummer
            )).apply {
                requireKey("@opprettet", "@id", "@event_name", "fødselsnummer")
                requireKey(*extra.keys.toTypedArray())
            }
    }
}
