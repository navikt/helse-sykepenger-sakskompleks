package no.nav.helse.spleis

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.FørsteFraværsdag
import no.nav.helse.person.TilstandType
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import org.slf4j.LoggerFactory

internal class PersonMediator(
    private val message: HendelseMessage
) : PersonObserver {
    private val meldinger = mutableListOf<Pakke>()

    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun ferdigstill(context: MessageContext) {
        sendUtgåendeMeldinger(context)
    }

    private fun sendUtgåendeMeldinger(context: MessageContext) {
        if (meldinger.isEmpty()) return
        message.logOutgoingMessages(sikkerLogg, meldinger.size)
        meldinger.forEach { pakke -> pakke.publish(context) }
    }

    private fun queueMessage(fødselsnummer: String, message: String) {
        val eventName = objectMapper.readTree(message).path("@event_name").asText()
        meldinger.add(Pakke(fødselsnummer, eventName, message))
    }

    override fun inntektsmeldingReplay(
        personidentifikator: Personidentifikator,
        organisasjonsnummer: String,
        vedtaksperiodeId: UUID,
        skjæringstidspunkt: LocalDate,
        sykmeldingsperioder: List<Periode>,
        egenmeldingsperioder: List<Periode>,
        førsteFraværsdager: List<FørsteFraværsdag>,
        trengerArbeidsgiverperiode: Boolean,
        erPotensiellForespørsel: Boolean
    ) {
        queueMessage(
            JsonMessage.newMessage(
                "trenger_inntektsmelding_replay", mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "skjæringstidspunkt" to skjæringstidspunkt,
                "sykmeldingsperioder" to sykmeldingsperioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                },
                "egenmeldingsperioder" to egenmeldingsperioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                },
                "førsteFraværsdager" to førsteFraværsdager.map {
                    mapOf(
                        "organisasjonsnummer" to it.organisasjonsnummer,
                        "førsteFraværsdag" to it.førsteFraværsdag
                    )
                },
                "trengerArbeidsgiverperiode" to trengerArbeidsgiverperiode,
                "potensiellForespørsel" to erPotensiellForespørsel
            )
            )
        )
    }

    override fun inntektsmeldingFørSøknad(event: PersonObserver.InntektsmeldingFørSøknadEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "inntektsmelding_før_søknad", mapOf(
                "inntektsmeldingId" to event.inntektsmeldingId,
                "relevante_sykmeldingsperioder" to event.relevanteSykmeldingsperioder.map { periode ->
                    mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive
                    )
                },
                "organisasjonsnummer" to event.organisasjonsnummer
            )
            )
        )
    }

    override fun inntektsmeldingIkkeHåndtert(inntektsmeldingId: UUID, organisasjonsnummer: String, harPeriodeInnenfor16Dager: Boolean) {
        queueMessage(
            JsonMessage.newMessage(
                "inntektsmelding_ikke_håndtert", mapOf(
                "inntektsmeldingId" to inntektsmeldingId,
                "organisasjonsnummer" to organisasjonsnummer,
                "harPeriodeInnenfor16Dager" to harPeriodeInnenfor16Dager
            )
            )
        )
    }

    override fun inntektsmeldingHåndtert(inntektsmeldingId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {
        queueMessage(
            JsonMessage.newMessage(
                "inntektsmelding_håndtert", mapOf(
                "inntektsmeldingId" to inntektsmeldingId,
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId
            )
            )
        )
    }

    override fun søknadHåndtert(søknadId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {
        queueMessage(
            JsonMessage.newMessage(
                "søknad_håndtert", mapOf(
                "søknadId" to søknadId,
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId
            )
            )
        )
    }

    override fun vedtaksperiodeAnnullert(vedtaksperiodeAnnullertEvent: PersonObserver.VedtaksperiodeAnnullertEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "vedtaksperiode_annullert", mapOf(
                "fom" to vedtaksperiodeAnnullertEvent.fom,
                "tom" to vedtaksperiodeAnnullertEvent.tom,
                "vedtaksperiodeId" to vedtaksperiodeAnnullertEvent.vedtaksperiodeId,
                "behandlingId" to vedtaksperiodeAnnullertEvent.behandlingId,
                "organisasjonsnummer" to vedtaksperiodeAnnullertEvent.organisasjonsnummer
            )
            )
        )
    }

    override fun overlappendeInfotrygdperioder(event: PersonObserver.OverlappendeInfotrygdperioder) {
        queueMessage(
            JsonMessage.newMessage(
                "overlappende_infotrygdperioder", mutableMapOf(
                "infotrygdhistorikkHendelseId" to event.infotrygdhistorikkHendelseId,
                "vedtaksperioder" to event.overlappendeInfotrygdperioder.map {
                    mapOf(
                        "organisasjonsnummer" to it.organisasjonsnummer,
                        "vedtaksperiodeId" to it.vedtaksperiodeId,
                        "vedtaksperiodeFom" to it.vedtaksperiodeFom,
                        "vedtaksperiodeTom" to it.vedtaksperiodeTom,
                        "vedtaksperiodetilstand" to it.vedtaksperiodetilstand,
                        "kanForkastes" to it.kanForkastes,
                        "infotrygdperioder" to it.infotrygdperioder.map { infotrygdperiode ->
                            mapOf(
                                "fom" to infotrygdperiode.fom,
                                "tom" to infotrygdperiode.tom,
                                "type" to infotrygdperiode.type,
                                "organisasjonsnummer" to infotrygdperiode.orgnummer
                            )
                        }
                    )
                },
            )
            )
        )
    }

    override fun vedtaksperiodePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, påminnelse: Påminnelse) {
        queueMessage(JsonMessage.newMessage("vedtaksperiode_påminnet", påminnelse.toOutgoingMessage()))
    }

    override fun vedtaksperiodeIkkePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, nåværendeTilstand: TilstandType) {
        queueMessage(
            JsonMessage.newMessage(
                "vedtaksperiode_ikke_påminnet", mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "tilstand" to nåværendeTilstand
            )
            )
        )
    }

    override fun annullering(event: PersonObserver.UtbetalingAnnullertEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "utbetaling_annullert", mutableMapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "utbetalingId" to event.utbetalingId,
                "korrelasjonsId" to event.korrelasjonsId,
                "fom" to event.fom,
                "tom" to event.tom,
                "tidspunkt" to event.annullertAvSaksbehandler,
                "epost" to event.saksbehandlerEpost,
                "ident" to event.saksbehandlerIdent,
                "arbeidsgiverFagsystemId" to event.arbeidsgiverFagsystemId,
                "personFagsystemId" to event.personFagsystemId
            )
            )
        )
    }

    override fun utbetalingEndret(event: PersonObserver.UtbetalingEndretEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "utbetaling_endret", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "utbetalingId" to event.utbetalingId,
                "type" to event.type,
                "forrigeStatus" to event.forrigeStatus,
                "gjeldendeStatus" to event.gjeldendeStatus,
                "arbeidsgiverOppdrag" to event.arbeidsgiverOppdrag,
                "personOppdrag" to event.personOppdrag,
                "korrelasjonsId" to event.korrelasjonsId
            )
            )
        )
    }

    override fun nyVedtaksperiodeUtbetaling(
        organisasjonsnummer: String,
        utbetalingId: UUID,
        vedtaksperiodeId: UUID
    ) {
        queueMessage(
            JsonMessage.newMessage(
                "vedtaksperiode_ny_utbetaling", mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "utbetalingId" to utbetalingId
            )
            )
        )
    }

    override fun overstyringIgangsatt(event: PersonObserver.OverstyringIgangsatt) {
        queueMessage(JsonMessage.newMessage("overstyring_igangsatt", mapOf(
            "revurderingId" to UUID.randomUUID(),
            "kilde" to message.meldingsporing.id,
            "skjæringstidspunkt" to event.skjæringstidspunkt,
            "periodeForEndringFom" to event.periodeForEndring.start,
            "periodeForEndringTom" to event.periodeForEndring.endInclusive,
            "årsak" to event.årsak,
            "typeEndring" to event.typeEndring,
            "berørtePerioder" to event.berørtePerioder.map {
                mapOf(
                    "vedtaksperiodeId" to it.vedtaksperiodeId,
                    "skjæringstidspunkt" to it.skjæringstidspunkt,
                    "periodeFom" to it.periode.start,
                    "periodeTom" to it.periode.endInclusive,
                    "orgnummer" to it.orgnummer,
                    "typeEndring" to it.typeEndring,
                )
            }
        )))
    }

    override fun utbetalingUtbetalt(event: PersonObserver.UtbetalingUtbetaltEvent) {
        queueMessage(utbetalingAvsluttet("utbetaling_utbetalt", event))
    }

    override fun utbetalingUtenUtbetaling(event: PersonObserver.UtbetalingUtbetaltEvent) {
        queueMessage(utbetalingAvsluttet("utbetaling_uten_utbetaling", event))
    }

    private fun utbetalingAvsluttet(eventName: String, event: PersonObserver.UtbetalingUtbetaltEvent) =
        JsonMessage.newMessage(
            eventName, mapOf(
            "organisasjonsnummer" to event.organisasjonsnummer,
            "utbetalingId" to event.utbetalingId,
            "korrelasjonsId" to event.korrelasjonsId,
            "type" to event.type,
            "fom" to event.fom,
            "tom" to event.tom,
            "maksdato" to event.maksdato,
            "forbrukteSykedager" to event.forbrukteSykedager,
            "gjenståendeSykedager" to event.gjenståendeSykedager,
            "stønadsdager" to event.stønadsdager,
            "ident" to event.ident,
            "epost" to event.epost,
            "tidspunkt" to event.tidspunkt,
            "automatiskBehandling" to event.automatiskBehandling,
            "arbeidsgiverOppdrag" to event.arbeidsgiverOppdrag,
            "personOppdrag" to event.personOppdrag,
            "utbetalingsdager" to event.utbetalingsdager
        )
        )

    override fun feriepengerUtbetalt(event: PersonObserver.FeriepengerUtbetaltEvent) =
        queueMessage(
            JsonMessage.newMessage(
                "feriepenger_utbetalt", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "arbeidsgiverOppdrag" to event.arbeidsgiverOppdrag,
                "personOppdrag" to event.personOppdrag,
            )
            )
        )

    override fun vedtaksperiodeEndret(event: PersonObserver.VedtaksperiodeEndretEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "vedtaksperiode_endret", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId,
                "gjeldendeTilstand" to event.gjeldendeTilstand,
                "forrigeTilstand" to event.forrigeTilstand,
                "hendelser" to event.hendelser,
                "makstid" to event.makstid,
                "fom" to event.fom,
                "tom" to event.tom,
                "skjæringstidspunkt" to event.skjæringstidspunkt
            )
            )
        )
    }

    override fun vedtaksperioderVenter(eventer: List<PersonObserver.VedtaksperiodeVenterEvent>) {
        if (eventer.isEmpty()) return
        queueMessage(JsonMessage.newMessage("vedtaksperioder_venter", mapOf(
            "vedtaksperioder" to eventer.map { event ->
                mapOf(
                    "organisasjonsnummer" to event.organisasjonsnummer,
                    "vedtaksperiodeId" to event.vedtaksperiodeId,
                    "behandlingId" to event.behandlingId,
                    "skjæringstidspunkt" to event.skjæringstidspunkt,
                    "hendelser" to event.hendelser,
                    "ventetSiden" to event.ventetSiden,
                    "venterTil" to event.venterTil,
                    "venterPå" to mapOf(
                        "vedtaksperiodeId" to event.venterPå.vedtaksperiodeId,
                        "skjæringstidspunkt" to event.venterPå.skjæringstidspunkt,
                        "organisasjonsnummer" to event.venterPå.organisasjonsnummer,
                        "venteårsak" to mapOf(
                            "hva" to event.venterPå.venteårsak.hva,
                            "hvorfor" to event.venterPå.venteårsak.hvorfor
                        )
                    )
                )
            }
        )))
    }

    override fun vedtaksperiodeOpprettet(event: PersonObserver.VedtaksperiodeOpprettet) {
        queueMessage(
            JsonMessage.newMessage(
                "vedtaksperiode_opprettet", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId,
                "skjæringstidspunkt" to event.skjæringstidspunkt,
                "fom" to event.periode.start,
                "tom" to event.periode.endInclusive
            )
            )
        )
    }

    override fun vedtaksperiodeForkastet(event: PersonObserver.VedtaksperiodeForkastetEvent) {
        queueMessage(JsonMessage.newMessage("vedtaksperiode_forkastet", event.toJsonMap()))
    }

    override fun nyBehandling(event: PersonObserver.BehandlingOpprettetEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "behandling_opprettet", mutableMapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId,
                "behandlingId" to event.behandlingId,
                "søknadIder" to event.søknadIder,
                "type" to event.type,
                "fom" to event.fom,
                "tom" to event.tom,
                "kilde" to mapOf(
                    "meldingsreferanseId" to event.kilde.meldingsreferanseId,
                    "innsendt" to event.kilde.innsendt,
                    "registrert" to event.kilde.registert,
                    "avsender" to event.kilde.avsender
                )
            )
            )
        )
    }

    override fun behandlingForkastet(event: PersonObserver.BehandlingForkastetEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "behandling_forkastet", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId,
                "behandlingId" to event.behandlingId,
                "automatiskBehandling" to event.automatiskBehandling
            )
            )
        )
    }

    override fun behandlingLukket(event: PersonObserver.BehandlingLukketEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "behandling_lukket", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId,
                "behandlingId" to event.behandlingId
            )
            )
        )
    }

    override fun avsluttetUtenVedtak(event: PersonObserver.AvsluttetUtenVedtakEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "avsluttet_uten_vedtak", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId,
                "behandlingId" to event.behandlingId,
                "fom" to event.periode.start,
                "tom" to event.periode.endInclusive,
                "skjæringstidspunkt" to event.skjæringstidspunkt,
                "hendelser" to event.hendelseIder,
                "avsluttetTidspunkt" to event.avsluttetTidspunkt
            )
            )
        )
    }

    override fun avsluttetMedVedtak(event: PersonObserver.AvsluttetMedVedtakEvent) {
        queueMessage(JsonMessage.newMessage("avsluttet_med_vedtak", mapOf(
            "organisasjonsnummer" to event.organisasjonsnummer,
            "vedtaksperiodeId" to event.vedtaksperiodeId,
            "behandlingId" to event.behandlingId,
            "fom" to event.periode.start,
            "tom" to event.periode.endInclusive,
            "hendelser" to event.hendelseIder,
            "skjæringstidspunkt" to event.skjæringstidspunkt,
            "sykepengegrunnlag" to event.sykepengegrunnlag,
            "grunnlagForSykepengegrunnlag" to event.beregningsgrunnlag,
            "grunnlagForSykepengegrunnlagPerArbeidsgiver" to event.omregnetÅrsinntektPerArbeidsgiver,
            "begrensning" to event.sykepengegrunnlagsbegrensning,
            "inntekt" to event.inntekt,
            "vedtakFattetTidspunkt" to event.vedtakFattetTidspunkt,
            "utbetalingId" to event.utbetalingId,
            "sykepengegrunnlagsfakta" to when (val fakta = event.sykepengegrunnlagsfakta) {
                is PersonObserver.UtkastTilVedtakEvent.FastsattEtterHovedregel -> mapOf(
                    "fastsatt" to fakta.fastsatt,
                    "omregnetÅrsinntekt" to fakta.omregnetÅrsinntekt,
                    "6G" to fakta.`6G`,
                    "arbeidsgivere" to fakta.arbeidsgivere.map { arbeidsgiver ->
                        mapOf(
                            "arbeidsgiver" to arbeidsgiver.arbeidsgiver,
                            "omregnetÅrsinntekt" to arbeidsgiver.omregnetÅrsinntekt,
                            "inntektskilde" to arbeidsgiver.inntektskilde
                        )
                    }
                )

                is PersonObserver.UtkastTilVedtakEvent.FastsattEtterSkjønn -> mutableMapOf(
                    "fastsatt" to fakta.fastsatt,
                    "omregnetÅrsinntekt" to fakta.omregnetÅrsinntekt,
                    "6G" to fakta.`6G`,
                    "arbeidsgivere" to fakta.arbeidsgivere.map { arbeidsgiver ->
                        mapOf(
                            "arbeidsgiver" to arbeidsgiver.arbeidsgiver,
                            "omregnetÅrsinntekt" to arbeidsgiver.omregnetÅrsinntekt,
                            "skjønnsfastsatt" to arbeidsgiver.skjønnsfastsatt,
                            "inntektskilde" to arbeidsgiver.inntektskilde
                        )
                    },
                    "skjønnsfastsatt" to fakta.skjønnsfastsatt
                )

                is PersonObserver.UtkastTilVedtakEvent.FastsattIInfotrygd -> mapOf(
                    "fastsatt" to fakta.fastsatt,
                    "omregnetÅrsinntekt" to fakta.omregnetÅrsinntekt
                )
            }
        )
        )
        )
    }

    override fun vedtaksperiodeIkkeFunnet(event: PersonObserver.VedtaksperiodeIkkeFunnetEvent) {
        queueMessage(
            JsonMessage.newMessage(
                "vedtaksperiode_ikke_funnet", mapOf(
                "organisasjonsnummer" to event.organisasjonsnummer,
                "vedtaksperiodeId" to event.vedtaksperiodeId
            )
            )
        )
    }

    override fun sykefraværstilfelleIkkeFunnet(event: PersonObserver.SykefraværstilfelleIkkeFunnet) {
        queueMessage(
            JsonMessage.newMessage(
                "sykefraværstilfelle_ikke_funnet", mapOf(
                "skjæringstidspunkt" to event.skjæringstidspunkt,
            )
            )
        )
    }

    override fun skatteinntekterLagtTilGrunn(event: PersonObserver.SkatteinntekterLagtTilGrunnEvent) {
        queueMessage(JsonMessage.newMessage("skatteinntekter_lagt_til_grunn", event.toJsonMap()))
    }

    override fun trengerArbeidsgiveropplysninger(event: PersonObserver.TrengerArbeidsgiveropplysningerEvent) {
        queueMessage(JsonMessage.newMessage("trenger_opplysninger_fra_arbeidsgiver", event.toJsonMap()))
    }

    override fun trengerIkkeArbeidsgiveropplysninger(event: PersonObserver.TrengerIkkeArbeidsgiveropplysningerEvent) {
        queueMessage(JsonMessage.newMessage("trenger_ikke_opplysninger_fra_arbeidsgiver", event.toJsonMap()))
    }

    override fun arbeidsgiveropplysningerKorrigert(event: PersonObserver.ArbeidsgiveropplysningerKorrigertEvent) {
        queueMessage(JsonMessage.newMessage("arbeidsgiveropplysninger_korrigert", event.toJsonMap()))
    }

    override fun utkastTilVedtak(event: PersonObserver.UtkastTilVedtakEvent) {
        val utkastTilVedtak = mutableMapOf(
            "vedtaksperiodeId" to event.vedtaksperiodeId,
            "behandlingId" to event.behandlingId,
            "skjæringstidspunkt" to event.skjæringstidspunkt,
            "tags" to event.tags
        )
        if (event.`6G` != null) utkastTilVedtak["sykepengegrunnlagsfakta"] = mapOf("6G" to event.`6G`)
        queueMessage(JsonMessage.newMessage("utkast_til_vedtak", utkastTilVedtak.toMap()))
    }

    private fun leggPåStandardfelter(outgoingMessage: JsonMessage) = outgoingMessage.apply {
        this["fødselsnummer"] = message.meldingsporing.fødselsnummer
    }

    private fun queueMessage(outgoingMessage: JsonMessage) {
        queueMessage(message.meldingsporing.fødselsnummer, leggPåStandardfelter(outgoingMessage).toJson())
    }

    private data class Pakke(
        private val fødselsnummer: String,
        private val eventName: String,
        private val blob: String,
    ) {
        fun publish(context: MessageContext) {
            context.publish(fødselsnummer, blob.also { sikkerLogg.info("sender $eventName: $it") })
        }
    }

}
