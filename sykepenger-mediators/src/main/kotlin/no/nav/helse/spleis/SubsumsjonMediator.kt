package no.nav.helse.spleis

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Subsumsjon
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.spleis.SubsumsjonMediator.SubsumsjonEvent.Companion.paragrafVersjonFormaterer
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import org.slf4j.LoggerFactory

internal class SubsumsjonMediator(
    private val fødselsnummer: String,
    private val message: HendelseMessage,
    private val versjonAvKode: String
) : Subsumsjonslogg {

    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(PersonMediator::class.java)
    }

    private val subsumsjoner = mutableListOf<SubsumsjonEvent>()

    override fun logg(subsumsjon: Subsumsjon) {
        bekreftAtSubsumsjonerHarKnytningTilBehandling(subsumsjon)
        subsumsjoner.add(SubsumsjonEvent(
            sporing = subsumsjon.kontekster
                .filterNot { it.type == KontekstType.Fødselsnummer }
                .groupBy({ it.type }) { it.verdi },
            lovverk = subsumsjon.lovverk,
            ikrafttredelse = paragrafVersjonFormaterer.format(subsumsjon.versjon),
            paragraf = subsumsjon.paragraf.ref,
            ledd = subsumsjon.ledd?.nummer,
            punktum = subsumsjon.punktum?.nummer,
            bokstav = subsumsjon.bokstav?.ref,
            input = subsumsjon.input,
            output = subsumsjon.output,
            utfall = subsumsjon.utfall.name
        ))
    }

    private fun bekreftAtSubsumsjonerHarKnytningTilBehandling(subsumsjon: Subsumsjon) {
        val kritiskeTyper = setOf(KontekstType.Fødselsnummer, KontekstType.Organisasjonsnummer)
        check(kritiskeTyper.all { kritiskType ->
            subsumsjon.kontekster.count { it.type == kritiskType } == 1
        }) {
            "en av $kritiskeTyper mangler/har duplikat:\n${subsumsjon.kontekster.joinToString(separator = "\n")}"
        }
        // todo: sjekker for mindre enn 1 også ettersom noen subsumsjoner skjer på arbeidsgivernivå. det burde vi forsøke å flytte/fikse slik at
        // alt kan subsummeres i kontekst av en behandling.
        check(subsumsjon.kontekster.count { it.type == KontekstType.Vedtaksperiode } <= 1) {
            "det er flere kontekster av ${KontekstType.Vedtaksperiode}:\n${subsumsjon.kontekster.joinToString(separator = "\n")}"
        }
    }

    fun ferdigstill(context: MessageContext) {
        if (subsumsjoner.isEmpty()) return
        logg.info("som følge av hendelse id=${message.id} sendes ${subsumsjoner.size} subsumsjonsmeldinger på rapid")
        subsumsjoner
            .map { subsumsjonMelding(fødselsnummer = fødselsnummer, event = it) }
            .forEach {
                context.publish(fødselsnummer, it.toJson().also { message ->
                    sikkerLogg.info("som følge av hendelse id=${this.message.id} sender subsumsjon: $message")
                })
            }
    }

    private fun subsumsjonMelding(fødselsnummer: String, event: SubsumsjonEvent): JsonMessage {
        return JsonMessage.newMessage("subsumsjon", mapOf(
            "@id" to event.id,
            "subsumsjon" to mutableMapOf(
                "id" to event.id,
                "eventName" to "subsumsjon",
                "tidsstempel" to ZonedDateTime.now(),
                "versjon" to "1.0.0",
                "kilde" to "spleis",
                "versjonAvKode" to versjonAvKode,
                "fodselsnummer" to fødselsnummer,
                "sporing" to event.sporing.map { it.key.tilEkstern() to it.value }.toMap(),
                "lovverk" to event.lovverk,
                "lovverksversjon" to event.ikrafttredelse,
                "paragraf" to event.paragraf,
                "input" to event.input,
                "output" to event.output,
                "utfall" to event.utfall
            ).apply {
                compute("ledd") { _, _ -> event.ledd }
                compute("punktum") { _, _ -> event.punktum }
                compute("bokstav") { _, _ -> event.bokstav }
            }
        ))
    }

    private fun KontekstType.tilEkstern() = when(this) {
        KontekstType.Fødselsnummer -> "fodselsnummer"
        KontekstType.Organisasjonsnummer -> "organisasjonsnummer"
        KontekstType.Vedtaksperiode -> "vedtaksperiode"
        KontekstType.Sykmelding -> "sykmelding"
        KontekstType.Søknad -> "soknad"
        KontekstType.Inntektsmelding -> "inntektsmelding"
        KontekstType.OverstyrTidslinje -> "overstyrtidslinje"
        KontekstType.OverstyrArbeidsgiveropplysninger -> "overstyrarbeidsgiveropplysninger"
        KontekstType.OverstyrInntekt -> "overstyrinntekt"
        KontekstType.OverstyrRefusjon -> "overstyrrefusjon"
        KontekstType.OverstyrArbeidsforhold -> "overstyrarbeidsforhold"
        KontekstType.SkjønnsmessigFastsettelse -> "skjønnsmessigfastsettelse"
        KontekstType.AndreYtelser -> "andreytelser"
        KontekstType.System -> "system"
    }

    data class SubsumsjonEvent(
        val id: UUID = UUID.randomUUID(),
        val sporing: Map<KontekstType, List<String>>,
        val lovverk: String,
        val ikrafttredelse: String,
        val paragraf: String,
        val ledd: Int?,
        val punktum: Int?,
        val bokstav: Char?,
        val input: Map<String, Any>,
        val output: Map<String, Any>,
        val utfall: String,
    ) {
        companion object {
            val paragrafVersjonFormaterer = DateTimeFormatter.ISO_DATE
        }
    }
}
