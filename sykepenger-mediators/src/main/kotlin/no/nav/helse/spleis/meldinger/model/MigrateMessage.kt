package no.nav.helse.spleis.meldinger.model

import no.nav.helse.hendelser.Migrate
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.spleis.IHendelseMediator

internal class MigrateMessage(packet: JsonMessage) : HendelseMessage(packet) {

    private val aktørId = packet["aktørId"].asText()
    override val fødselsnummer: String = packet["fødselsnummer"].asText()

    private val migrate get() = Migrate(
        meldingsreferanseId = id,
        aktørId = aktørId,
        fødselsnummer = fødselsnummer
    )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, migrate, context)
    }
}
