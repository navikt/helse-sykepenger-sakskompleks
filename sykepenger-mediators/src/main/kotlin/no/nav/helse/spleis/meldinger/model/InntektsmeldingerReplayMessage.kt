package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.asOptionalLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.meldinger.model.InntektsmeldingMessage.Companion.tilAvsendersystem
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

// Understands a JSON message representing an Inntektsmelding replay
internal class InntektsmeldingerReplayMessage(packet: JsonMessage) : HendelseMessage(packet) {
    private val aktørId = packet["aktørId"].asText()
    override val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
    override val skalDuplikatsjekkes = false

    private val aktivitetslogg = Aktivitetslogg()

    private val inntektsmeldinger = mutableListOf<Inntektsmelding>()

    private val inntektsmeldingerReplay = InntektsmeldingerReplay(
        meldingsreferanseId = id,
        aktørId = aktørId,
        fødselsnummer = fødselsnummer,
        organisasjonsnummer = organisasjonsnummer,
        aktivitetslogg = aktivitetslogg,
        vedtaksperiodeId = vedtaksperiodeId,
        inntektsmeldinger = inntektsmeldinger
    )

    init {
        packet["inntektsmeldinger"].forEach { inntektsmelding ->
            inntektsmeldinger.add(inntektsmeldingReplay(
                inntektsmelding.path("internDokumentId").asText().toUUID(),
                inntektsmelding.path("inntektsmelding")
            ))
        }
    }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, inntektsmeldingerReplay, context)
    }

    private fun inntektsmeldingReplay(internDokumentId: UUID, packet: JsonNode): Inntektsmelding {
        val refusjon = Inntektsmelding.Refusjon(
            beløp = packet.path("refusjon").path("beloepPrMnd").takeUnless(JsonNode::isMissingOrNull)?.asDouble()?.månedlig,
            opphørsdato = packet.path("refusjon").path("opphoersdato").asOptionalLocalDate(),
            endringerIRefusjon = packet["endringIRefusjoner"].map {
                Inntektsmelding.Refusjon.EndringIRefusjon(
                    it.path("beloep").asDouble().månedlig,
                    it.path("endringsdato").asLocalDate()
                )
            }
        )
        val arbeidsforholdId = packet.path("arbeidsforholdId").takeIf(JsonNode::isTextual)?.asText()
        val vedtaksperiodeId = packet.path("vedtaksperiodeId").takeIf(JsonNode::isTextual)?.asText()?.let { UUID.fromString(it) }
        val orgnummer = packet.path("virksomhetsnummer").asText()
        val aktørId = packet.path("arbeidstakerAktorId").asText()
        val mottatt = packet.path("mottattDato").asLocalDateTime()
        val førsteFraværsdag = packet.path("foersteFravaersdag").asOptionalLocalDate()
        val beregnetInntekt = packet.path("beregnetInntekt").asDouble()
        val arbeidsgiverperioder = packet.path("arbeidsgiverperioder").map(::asPeriode)
        val begrunnelseForReduksjonEllerIkkeUtbetalt = packet.path("begrunnelseForReduksjonEllerIkkeUtbetalt").takeIf(JsonNode::isTextual)?.asText()
        val harOpphørAvNaturalytelser = packet.path("opphoerAvNaturalytelser").size() > 0
        val harFlereInntektsmeldinger = packet.path("harFlereInntektsmeldinger").asBoolean(false)
        val avsendersystem = packet.path("avsenderSystem").tilAvsendersystem()
        val inntektsdato = packet.path("inntektsdato").asOptionalLocalDate()

        return Inntektsmelding(
            meldingsreferanseId = internDokumentId,
            refusjon = refusjon,
            orgnummer = orgnummer,
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            førsteFraværsdag = førsteFraværsdag,
            inntektsdato = inntektsdato,
            beregnetInntekt = beregnetInntekt.månedlig,
            arbeidsgiverperioder = arbeidsgiverperioder,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger,
            avsendersystem = avsendersystem,
            vedtaksperiodeId = vedtaksperiodeId,
            mottatt = mottatt,
            aktivitetslogg = aktivitetslogg.barn()
        )
    }
}
