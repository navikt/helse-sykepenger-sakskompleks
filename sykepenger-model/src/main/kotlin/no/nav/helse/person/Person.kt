package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.math.roundToInt
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.Toggle
import no.nav.helse.dto.deserialisering.PersonInnDto
import no.nav.helse.dto.serialisering.PersonUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.Arbeidsgiveropplysninger
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.Behandlingsporing
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.IdentOpphørt
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.KanIkkeBehandlesHer
import no.nav.helse.hendelser.KorrigerteArbeidsgiveropplysninger
import no.nav.helse.hendelser.MinimumSykdomsgradsvurderingMelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingpåminnelse
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.VedtakFattet
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.Arbeidsgiver.Companion.aktiveSkjæringstidspunkter
import no.nav.helse.person.Arbeidsgiver.Companion.avventerSøknad
import no.nav.helse.person.Arbeidsgiver.Companion.beregnFeriepengerForAlleArbeidsgivere
import no.nav.helse.person.Arbeidsgiver.Companion.beregnSkjæringstidspunkt
import no.nav.helse.person.Arbeidsgiver.Companion.beregnSkjæringstidspunkter
import no.nav.helse.person.Arbeidsgiver.Companion.finn
import no.nav.helse.person.Arbeidsgiver.Companion.gjenopptaBehandling
import no.nav.helse.person.Arbeidsgiver.Companion.håndter
import no.nav.helse.person.Arbeidsgiver.Companion.håndterHistorikkFraInfotrygd
import no.nav.helse.person.Arbeidsgiver.Companion.håndterOverstyringAvInntekt
import no.nav.helse.person.Arbeidsgiver.Companion.håndterOverstyringAvRefusjon
import no.nav.helse.person.Arbeidsgiver.Companion.igangsettOverstyring
import no.nav.helse.person.Arbeidsgiver.Companion.mursteinsperioder
import no.nav.helse.person.Arbeidsgiver.Companion.nestemann
import no.nav.helse.person.Arbeidsgiver.Companion.nåværendeVedtaksperioder
import no.nav.helse.person.Arbeidsgiver.Companion.tidligsteDato
import no.nav.helse.person.Arbeidsgiver.Companion.validerTilstand
import no.nav.helse.person.Arbeidsgiver.Companion.vedtaksperioder
import no.nav.helse.person.Arbeidsgiver.Companion.venter
import no.nav.helse.person.PersonObserver.FørsteFraværsdag
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
import no.nav.helse.person.Yrkesaktivitet.Companion.tilYrkesaktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AG_1
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_10
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.inntekt.Infotrygd
import no.nav.helse.person.inntekt.Arbeidsgiverinntekt
import no.nav.helse.person.inntekt.NyInntektUnderveis
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.view.PersonView
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner

class Person private constructor(
    personidentifikator: Personidentifikator,
    internal var alder: Alder,
    private val _arbeidsgivere: MutableList<Arbeidsgiver>,
    internal val personlogg: Aktivitetslogg,
    private val opprettet: LocalDateTime,
    internal val infotrygdhistorikk: Infotrygdhistorikk,
    internal val vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk,
    private val jurist: Subsumsjonslogg,
    private val tidligereBehandlinger: List<Person> = emptyList(),
    internal val regler: ArbeidsgiverRegler = NormalArbeidstaker,
    internal val minimumSykdomsgradsvurdering: MinimumSykdomsgradsvurdering = MinimumSykdomsgradsvurdering()
) : Aktivitetskontekst {
    companion object {
        fun gjenopprett(
            subsumsjonslogg: Subsumsjonslogg,
            dto: PersonInnDto,
            tidligereBehandlinger: List<Person> = emptyList()
        ): Person {
            val arbeidsgivere = mutableListOf<Arbeidsgiver>()
            val grunnlagsdataMap = mutableMapOf<UUID, VilkårsgrunnlagElement>()
            val alder = Alder.gjenopprett(dto.alder)
            val person = Person(
                personidentifikator = Personidentifikator(dto.fødselsnummer),
                alder = alder,
                _arbeidsgivere = arbeidsgivere,
                personlogg = Aktivitetslogg(),
                opprettet = dto.opprettet,
                infotrygdhistorikk = Infotrygdhistorikk.gjenopprett(dto.infotrygdhistorikk),
                vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk.gjenopprett(
                    alder,
                    dto.vilkårsgrunnlagHistorikk,
                    grunnlagsdataMap
                ),
                minimumSykdomsgradsvurdering = MinimumSykdomsgradsvurdering.gjenopprett(dto.minimumSykdomsgradVurdering),
                jurist = subsumsjonslogg,
                tidligereBehandlinger = tidligereBehandlinger
            )
            arbeidsgivere.addAll(dto.arbeidsgivere.map {
                Arbeidsgiver.gjenopprett(person, alder, it, subsumsjonslogg, grunnlagsdataMap)
            })
            return person
        }
    }

    internal constructor(
        personidentifikator: Personidentifikator,
        alder: Alder,
        subsumsjonslogg: Subsumsjonslogg,
        regler: ArbeidsgiverRegler
    ) : this(
        personidentifikator,
        alder,
        mutableListOf(),
        Aktivitetslogg(),
        LocalDateTime.now(),
        Infotrygdhistorikk(),
        VilkårsgrunnlagHistorikk(),
        subsumsjonslogg,
        emptyList<Person>(),
        regler = regler
    )

    constructor(
        personidentifikator: Personidentifikator,
        alder: Alder,
        jurist: Subsumsjonslogg
    ) : this(personidentifikator, alder, jurist, NormalArbeidstaker)

    internal val arbeidsgivere: List<Arbeidsgiver> get() = _arbeidsgivere.toList()

    var personidentifikator: Personidentifikator = personidentifikator
        private set

    val fødselsnummer get() = personidentifikator.toString()

    private val observers = mutableListOf<PersonObserver>()
    internal fun view() = PersonView(
        arbeidsgivere = arbeidsgivere.map { it.view() },
        vilkårsgrunnlaghistorikk = vilkårsgrunnlagHistorikk.view()
    )

    fun håndter(sykmelding: Sykmelding, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler sykmelding")
        tidligereBehandlinger(sykmelding.behandlingsporing, aktivitetslogg, sykmelding.periode())
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(sykmelding.behandlingsporing, aktivitetslogg)
        arbeidsgiver.håndter(sykmelding, aktivitetslogg)
        håndterGjenoppta(sykmelding, aktivitetslogg)
    }

    fun håndter(avbruttSøknad: AvbruttSøknad, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler avbrutt søknad")
        val arbeidsgiver = finnArbeidsgiver(avbruttSøknad.behandlingsporing, aktivitetslogg)
        arbeidsgiver.håndter(avbruttSøknad, aktivitetslogg)
        gjenopptaBehandling(aktivitetslogg)
        håndterGjenoppta(avbruttSøknad, aktivitetslogg)
    }

    fun håndter(forkastSykmeldingsperioder: ForkastSykmeldingsperioder, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler forkasting av sykmeldingsperioder")
        finnArbeidsgiver(forkastSykmeldingsperioder.behandlingsporing, aktivitetslogg).håndter(forkastSykmeldingsperioder, aktivitetslogg)
        gjenopptaBehandling(aktivitetslogg)
        håndterGjenoppta(forkastSykmeldingsperioder, aktivitetslogg)
    }

    fun håndter(anmodningOmForkasting: AnmodningOmForkasting, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler anmodning om forkasting")
        finnArbeidsgiver(anmodningOmForkasting.behandlingsporing, aktivitetslogg).håndter(anmodningOmForkasting, aktivitetslogg)
        håndterGjenoppta(anmodningOmForkasting, aktivitetslogg)
    }

    fun håndter(søknad: Søknad, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler søknad")
        tidligereBehandlinger(søknad.behandlingsporing, aktivitetslogg, søknad.sykdomstidslinje.periode()!!)
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(søknad.behandlingsporing, aktivitetslogg)
        søknad.forUng(aktivitetslogg, alder)
        arbeidsgiver.håndter(søknad, aktivitetslogg, arbeidsgivere.toList(), infotrygdhistorikk)
        håndterGjenoppta(søknad, aktivitetslogg)
    }

    fun håndter(arbeidsgiveropplysninger: Arbeidsgiveropplysninger, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler arbeidsgiveropplysningene ${arbeidsgiveropplysninger.joinToString { "${it::class.simpleName}" }}")
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(arbeidsgiveropplysninger.behandlingsporing, aktivitetslogg)
        arbeidsgiver.håndter(arbeidsgiveropplysninger, aktivitetslogg)
        håndterGjenoppta(arbeidsgiveropplysninger, aktivitetslogg)
    }

    fun håndter(korrigerteArbeidsgiveropplysninger: KorrigerteArbeidsgiveropplysninger, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler de korrigerte arbeidsgiveropplysningene ${korrigerteArbeidsgiveropplysninger.joinToString { "${it::class.simpleName}" }}")
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(korrigerteArbeidsgiveropplysninger.behandlingsporing, aktivitetslogg)
        arbeidsgiver.håndter(korrigerteArbeidsgiveropplysninger, aktivitetslogg)
        håndterGjenoppta(korrigerteArbeidsgiveropplysninger, aktivitetslogg)
    }

    fun håndter(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler inntektsmelding")
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(inntektsmelding.behandlingsporing, aktivitetslogg)
        arbeidsgiver.håndter(inntektsmelding, aktivitetslogg)
        arbeidsgiver.inntektsmeldingFerdigbehandlet(inntektsmelding, aktivitetslogg)
        håndterGjenoppta(inntektsmelding, aktivitetslogg)
    }

    fun håndter(replays: InntektsmeldingerReplay, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler replay av inntektsmeldinger")
        finnArbeidsgiver(replays.behandlingsporing, aktivitetslogg).håndter(replays, aktivitetslogg)
        håndterGjenoppta(replays, aktivitetslogg)
    }

    fun håndter(melding: MinimumSykdomsgradsvurderingMelding, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler minimum sykdomsgradvurdering")
        melding.oppdater(this.minimumSykdomsgradsvurdering)
        this.igangsettOverstyring(Revurderingseventyr.minimumSykdomsgradVurdert(melding, melding.periodeForEndring()), aktivitetslogg)
        håndterGjenoppta(melding, aktivitetslogg)
    }

    private fun tidligereBehandlinger(behandlingsporing: Behandlingsporing.Arbeidsgiver, aktivitetslogg: IAktivitetslogg, periode: Periode) {
        val cutoff = periode.start.minusMonths(6)
        val andreBehandledeVedtaksperioder = tidligereBehandlinger.flatMap { it.vedtaksperioderEtter(cutoff) }
        if (andreBehandledeVedtaksperioder.isNotEmpty()) {
            aktivitetslogg.funksjonellFeil(Varselkode.RV_AN_5)
            val msg = andreBehandledeVedtaksperioder.map {
                "vedtaksperiode(${it.periode})"
            }
            aktivitetslogg.info(
                """hendelse: ${behandlingsporing::class.java.simpleName} ($periode) kaster ut personen 
                | tidligere behandlede identer: ${tidligereBehandlinger.map { it.personidentifikator }}
                | tidligere behandlede perioder: ${msg.joinToString { it }}
                | cutoff: $cutoff""".trimMargin()
            )
        }
    }

    private fun vedtaksperioderEtter(dato: LocalDate) = arbeidsgivere.flatMap { it.vedtaksperioderEtter(dato) }
    fun håndter(dødsmelding: Dødsmelding, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler dødsmelding")
        aktivitetslogg.info("Registrerer dødsdato")
        alder = dødsmelding.dødsdato(alder)
        håndterGjenoppta(dødsmelding, aktivitetslogg)
    }

    fun håndter(identOpphørt: IdentOpphørt, aktivitetslogg: IAktivitetslogg, nyPersonidentifikator: Personidentifikator) {
        registrer(aktivitetslogg, "Behandler ident opphørt")
        aktivitetslogg.info("Person har byttet ident til $nyPersonidentifikator")
        this.personidentifikator = nyPersonidentifikator
        håndterGjenoppta(identOpphørt, aktivitetslogg)
    }

    fun håndter(infotrygdendring: Infotrygdendring, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler infotrygdendring")
        val tidligsteDato = arbeidsgivere.tidligsteDato()
        infotrygdhistorikk.oppfrisk(aktivitetslogg, tidligsteDato)
        håndterGjenoppta(infotrygdendring, aktivitetslogg)
    }

    fun håndter(utbetalingshistorikkEtterInfotrygdendring: UtbetalingshistorikkEtterInfotrygdendring, aktivitetslogg: IAktivitetslogg) = håndterHistorikkFraInfotrygd(utbetalingshistorikkEtterInfotrygdendring, aktivitetslogg) {
        utbetalingshistorikkEtterInfotrygdendring.oppdaterHistorikk(aktivitetslogg, it)
    }

    fun håndter(utbetalingshistorikk: Utbetalingshistorikk, aktivitetslogg: IAktivitetslogg) =
        håndterHistorikkFraInfotrygd(utbetalingshistorikk, aktivitetslogg) {
            utbetalingshistorikk.oppdaterHistorikk(aktivitetslogg, it)
        }

    private fun håndterHistorikkFraInfotrygd(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, oppdatertHistorikk: (infotrygdhistorikk: Infotrygdhistorikk) -> Boolean) {
        registrer(aktivitetslogg, "Behandler historikk fra infotrygd")
        oppdatertHistorikk(infotrygdhistorikk)
        sykdomshistorikkEndret()
        arbeidsgivere.håndterHistorikkFraInfotrygd(hendelse, aktivitetslogg, infotrygdhistorikk)
        val alleVedtaksperioder = arbeidsgivere.vedtaksperioder { true }
        emitOverlappendeInfotrygdperioder(alleVedtaksperioder)
        håndterGjenoppta(hendelse, aktivitetslogg)
    }

    private fun emitOverlappendeInfotrygdperioder(alleVedtaksperioder: List<Vedtaksperiode>) {
        if (!infotrygdhistorikk.harHistorikk()) return
        val hendelseId = infotrygdhistorikk.siste.hendelseId
        val perioder = infotrygdhistorikk.siste.perioder
        val event = alleVedtaksperioder.fold(PersonObserver.OverlappendeInfotrygdperioder(emptyList(), hendelseId.toString())) { result, vedtaksperiode ->
            vedtaksperiode.overlappendeInfotrygdperioder(result, perioder)
        }
        observers.forEach { it.overlappendeInfotrygdperioder(event) }
    }

    fun håndter(utbetalingshistorikk: UtbetalingshistorikkForFeriepenger, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler utbetalingshistorikk for feriepenger")

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            aktivitetslogg.info("Starter beregning av feriepenger")
        }

        if (utbetalingshistorikk.skalBeregnesManuelt) {
            aktivitetslogg.info("Person er markert for manuell beregning av feriepenger")
            return
        }

        // Hardkodet dato skal være datoen Infotrygd sist kjørte feriepenger
        val DATO_FOR_SISTE_FERIEPENGEKJØRING_I_INFOTRYGD = LocalDate.of(2025, 1, 18)

        val feriepengeberegner = Feriepengeberegner(
            alder = alder,
            opptjeningsår = utbetalingshistorikk.opptjeningsår,
            grunnlagFraInfotrygd = utbetalingshistorikk.grunnlagForFeriepenger(DATO_FOR_SISTE_FERIEPENGEKJØRING_I_INFOTRYGD),
            grunnlagFraSpleis = grunnlagForFeriepenger()
        )

        val feriepengepengebeløpPersonUtbetaltAvInfotrygd = utbetalingshistorikk.utbetalteFeriepengerTilPerson()
        val beregnetFeriepengebeløpPersonInfotrygd =
            feriepengeberegner.beregnFeriepengerForInfotrygdPerson().roundToInt()

        if (beregnetFeriepengebeløpPersonInfotrygd != 0 && beregnetFeriepengebeløpPersonInfotrygd !in feriepengepengebeløpPersonUtbetaltAvInfotrygd) {
            aktivitetslogg.info(
                """
                Beregnet feriepengebeløp til person i IT samsvarer ikke med faktisk utbetalt beløp
                Faktisk utbetalt beløp: $feriepengepengebeløpPersonUtbetaltAvInfotrygd
                Beregnet beløp: $beregnetFeriepengebeløpPersonInfotrygd
                """.trimIndent()
            )
        }

        utbetalingshistorikk.sikreAtArbeidsgivereEksisterer {
            _arbeidsgivere.finnEllerOpprett(Yrkesaktivitet.Arbeidstaker(it), aktivitetslogg)
        }
        arbeidsgivere.beregnFeriepengerForAlleArbeidsgivere(
            personidentifikator,
            feriepengeberegner,
            utbetalingshistorikk,
            aktivitetslogg
        )

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            aktivitetslogg.info("Feriepenger er utbetalt")
        }
    }

    fun håndter(ytelser: Ytelser, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler historiske utbetalinger og inntekter")
        finnArbeidsgiver(ytelser.behandlingsporing, aktivitetslogg).håndter(ytelser, aktivitetslogg, infotrygdhistorikk)
        håndterGjenoppta(ytelser, aktivitetslogg)
    }

    fun håndter(utbetalingsgodkjenning: Utbetalingsgodkjenning, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler utbetalingsgodkjenning")
        finnArbeidsgiver(utbetalingsgodkjenning.behandlingsporing, aktivitetslogg).håndter(utbetalingsgodkjenning, aktivitetslogg)
        håndterGjenoppta(utbetalingsgodkjenning, aktivitetslogg)
    }

    fun håndter(vedtakFattet: VedtakFattet, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler vedtak fattet")
        finnArbeidsgiver(vedtakFattet.behandlingsporing, aktivitetslogg).håndter(vedtakFattet, aktivitetslogg)
        håndterGjenoppta(vedtakFattet, aktivitetslogg)
    }

    fun håndter(kanIkkeBehandlesHer: KanIkkeBehandlesHer, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler kan ikke behandles her")
        finnArbeidsgiver(kanIkkeBehandlesHer.behandlingsporing, aktivitetslogg).håndter(kanIkkeBehandlesHer, aktivitetslogg)
        håndterGjenoppta(kanIkkeBehandlesHer, aktivitetslogg)
    }

    fun håndter(sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler sykepengegrunnlag for arbeidsgiver")
        finnArbeidsgiver(sykepengegrunnlagForArbeidsgiver.behandlingsporing, aktivitetslogg).håndter(sykepengegrunnlagForArbeidsgiver, aktivitetslogg)
        håndterGjenoppta(sykepengegrunnlagForArbeidsgiver, aktivitetslogg)
    }

    fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler vilkårsgrunnlag")
        finnArbeidsgiver(vilkårsgrunnlag.behandlingsporing, aktivitetslogg).håndter(vilkårsgrunnlag, aktivitetslogg)
        håndterGjenoppta(vilkårsgrunnlag, aktivitetslogg)
    }

    fun håndter(simulering: Simulering, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler simulering")
        finnArbeidsgiver(simulering.behandlingsporing, aktivitetslogg).håndter(simulering, aktivitetslogg)
        håndterGjenoppta(simulering, aktivitetslogg)
    }

    fun håndter(utbetaling: UtbetalingHendelse, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler utbetaling")
        finnArbeidsgiver(utbetaling.behandlingsporing, aktivitetslogg).håndter(utbetaling, aktivitetslogg)
        håndterGjenoppta(utbetaling, aktivitetslogg)
    }

    fun håndter(påminnelse: Utbetalingpåminnelse, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler utbetalingpåminnelse")
        finnArbeidsgiver(påminnelse.behandlingsporing, aktivitetslogg).håndter(påminnelse, aktivitetslogg)
        håndterGjenoppta(påminnelse, aktivitetslogg)
    }

    fun håndter(påminnelse: PersonPåminnelse, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler personpåminnelse")
        aktivitetslogg.info("Håndterer påminnelse for person")
        håndterGjenoppta(påminnelse, aktivitetslogg)
    }

    fun håndter(påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        try {
            registrer(aktivitetslogg, "Behandler påminnelse")
            if (finnArbeidsgiver(påminnelse.behandlingsporing, aktivitetslogg).håndter(påminnelse, aktivitetslogg)) return håndterGjenoppta(påminnelse, aktivitetslogg)
        } catch (err: Aktivitetslogg.AktivitetException) {
            aktivitetslogg.funksjonellFeil(RV_AG_1)
        }
        observers.forEach { påminnelse.vedtaksperiodeIkkeFunnet(it) }
        håndterGjenoppta(påminnelse, aktivitetslogg)
    }

    fun håndter(hendelse: OverstyrTidslinje, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler Overstyr tidslinje")
        finnArbeidsgiver(hendelse.behandlingsporing, aktivitetslogg).håndter(hendelse, aktivitetslogg)
        håndterGjenoppta(hendelse, aktivitetslogg)
    }

    fun håndter(hendelse: OverstyrArbeidsgiveropplysninger, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler Overstyring av arbeidsgiveropplysninger")
        val inntektseventyr = arbeidsgivere.håndterOverstyringAvInntekt(hendelse, aktivitetslogg)
        val refusjonseventyr = arbeidsgivere.håndterOverstyringAvRefusjon(hendelse, aktivitetslogg)
        val tidligsteEventyr = Revurderingseventyr.tidligsteEventyr(inntektseventyr, refusjonseventyr)
        if (tidligsteEventyr == null) return aktivitetslogg.info("Ingen vedtaksperioder håndterte overstyringen av arbeidsgiveropplysninger fordi overstyringen ikke har endret noe.")
        igangsettOverstyring(tidligsteEventyr, aktivitetslogg)
        håndterGjenoppta(hendelse, aktivitetslogg)
    }

    fun håndter(hendelse: SkjønnsmessigFastsettelse, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler skjønnsmessig fastsettelse")
        check(arbeidsgivere.håndter(hendelse, aktivitetslogg)) {
            "Ingen vedtaksperioder håndterte skjønnsmessig fastsettelse"
        }
        håndterGjenoppta(hendelse, aktivitetslogg)
    }

    fun håndter(overstyrArbeidsforhold: OverstyrArbeidsforhold, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler overstyring av arbeidsforhold")
        check(arbeidsgivere.håndter(overstyrArbeidsforhold, aktivitetslogg)) {
            "Kan ikke overstyre arbeidsforhold fordi ingen vedtaksperioder håndterte hendelsen"
        }
        håndterGjenoppta(overstyrArbeidsforhold, aktivitetslogg)
    }

    fun håndter(hendelse: AnnullerUtbetaling, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler annulleringforespørsel")
        finnArbeidsgiver(hendelse.behandlingsporing, aktivitetslogg).håndter(hendelse, aktivitetslogg)
        håndterGjenoppta(hendelse, aktivitetslogg)
    }

    fun håndter(hendelse: Grunnbeløpsregulering, aktivitetslogg: IAktivitetslogg) {
        registrer(aktivitetslogg, "Behandler grunnbeløpsendring")
        if (arbeidsgivere.håndter(hendelse, aktivitetslogg)) return håndterGjenoppta(hendelse, aktivitetslogg)
        observers.forEach { hendelse.sykefraværstilfelleIkkeFunnet(it) }
    }

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    internal fun annullert(event: PersonObserver.UtbetalingAnnullertEvent) {
        observers.forEach { it.annullering(event) }
    }

    internal fun vedtaksperiodePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, påminnelse: Påminnelse) {
        observers.forEach { it.vedtaksperiodePåminnet(vedtaksperiodeId, organisasjonsnummer, påminnelse) }
    }

    internal fun vedtaksperiodeIkkePåminnet(vedtaksperiodeId: UUID, organisasjonsnummer: String, tilstandType: TilstandType) {
        observers.forEach { it.vedtaksperiodeIkkePåminnet(vedtaksperiodeId, organisasjonsnummer, tilstandType) }
    }

    internal fun vedtaksperiodeForkastet(event: PersonObserver.VedtaksperiodeForkastetEvent) {
        observers.forEach { it.vedtaksperiodeForkastet(event) }
    }

    internal fun vedtaksperiodeEndret(event: PersonObserver.VedtaksperiodeEndretEvent) {
        observers.forEach { it.vedtaksperiodeEndret(event) }
    }

    internal fun inntektsmeldingReplay(
        vedtaksperiodeId: UUID,
        skjæringstidspunkt: LocalDate,
        organisasjonsnummer: String,
        sykmeldingsperioder: List<Periode>,
        egenmeldingsperioder: List<Periode>,
        førsteFraværsdager: List<FørsteFraværsdag>,
        trengerArbeidsgiverperiode: Boolean,
        erPotensiellForespørsel: Boolean
    ) {
        observers.forEach {
            it.inntektsmeldingReplay(
                personidentifikator = personidentifikator,
                organisasjonsnummer = organisasjonsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                skjæringstidspunkt = skjæringstidspunkt,
                sykmeldingsperioder = sykmeldingsperioder,
                egenmeldingsperioder = egenmeldingsperioder,
                førsteFraværsdager = førsteFraværsdager,
                trengerArbeidsgiverperiode = trengerArbeidsgiverperiode,
                erPotensiellForespørsel = erPotensiellForespørsel
            )
        }
    }

    internal fun trengerArbeidsgiveropplysninger(event: PersonObserver.TrengerArbeidsgiveropplysningerEvent) {
        observers.forEach { it.trengerArbeidsgiveropplysninger(event) }
    }

    internal fun trengerIkkeArbeidsgiveropplysninger(event: PersonObserver.TrengerIkkeArbeidsgiveropplysningerEvent) {
        observers.forEach { it.trengerIkkeArbeidsgiveropplysninger(event) }
    }

    internal fun arbeidsgiveropplysningerKorrigert(event: PersonObserver.ArbeidsgiveropplysningerKorrigertEvent) {
        observers.forEach { it.arbeidsgiveropplysningerKorrigert(event) }
    }

    internal fun utbetalingUtbetalt(event: PersonObserver.UtbetalingUtbetaltEvent) {
        observers.forEach { it.utbetalingUtbetalt(event) }
    }

    internal fun utbetalingUtenUtbetaling(event: PersonObserver.UtbetalingUtbetaltEvent) {
        observers.forEach { it.utbetalingUtenUtbetaling(event) }
    }

    internal fun utbetalingEndret(event: PersonObserver.UtbetalingEndretEvent) {
        observers.forEach { it.utbetalingEndret(event) }
    }

    internal fun avsluttetUtenVedtak(event: PersonObserver.AvsluttetUtenVedtakEvent) {
        observers.forEach { it.avsluttetUtenVedtak(event) }
    }

    internal fun avsluttetMedVedtak(avsluttetMedVedtakEvent: PersonObserver.AvsluttetMedVedtakEvent) {
        observers.forEach { it.avsluttetMedVedtak(avsluttetMedVedtakEvent) }
    }

    internal fun behandlingLukket(behandlingLukketEvent: PersonObserver.BehandlingLukketEvent) {
        observers.forEach { it.behandlingLukket(behandlingLukketEvent) }
    }

    internal fun behandlingForkastet(behandlingForkastetEvent: PersonObserver.BehandlingForkastetEvent) {
        observers.forEach { it.behandlingForkastet(behandlingForkastetEvent) }
    }

    internal fun nyBehandling(event: PersonObserver.BehandlingOpprettetEvent) {
        observers.forEach { it.nyBehandling(event) }
    }

    internal fun utkastTilVedtak(event: PersonObserver.UtkastTilVedtakEvent) {
        observers.forEach { it.utkastTilVedtak(event) }
    }

    internal fun emitOverstyringIgangsattEvent(event: PersonObserver.OverstyringIgangsatt) {
        observers.forEach { it.overstyringIgangsatt(event) }
    }

    internal fun feriepengerUtbetalt(feriepengerUtbetaltEvent: PersonObserver.FeriepengerUtbetaltEvent) {
        observers.forEach { it.feriepengerUtbetalt(feriepengerUtbetaltEvent) }
    }

    internal fun grunnlagForFeriepenger() = arbeidsgivere.map { it.grunnlagForFeriepenger() }
    internal fun trengerHistorikkFraInfotrygd(aktivitetslogg: IAktivitetslogg) {
        infotrygdhistorikk.oppfriskNødvendig(aktivitetslogg, arbeidsgivere.tidligsteDato())
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Person", mapOf("fødselsnummer" to personidentifikator.toString()))
    }

    private fun registrer(aktivitetslogg: IAktivitetslogg, melding: String) {
        aktivitetslogg.kontekst(this.personlogg, this)
        aktivitetslogg.info(melding)
    }

    private fun finnEllerOpprettArbeidsgiver(behandlingsporing: Behandlingsporing.Arbeidsgiver, aktivitetslogg: IAktivitetslogg) =
        finnEllerOpprettArbeidsgiver(behandlingsporing.organisasjonsnummer.tilYrkesaktivitet(), aktivitetslogg)

    private fun finnEllerOpprettArbeidsgiver(yrkesaktivitet: Yrkesaktivitet, aktivitetslogg: IAktivitetslogg) =
        _arbeidsgivere.finnEllerOpprett(yrkesaktivitet, aktivitetslogg)

    private fun finnArbeidsgiver(behandlingsporing: Behandlingsporing.Arbeidsgiver, aktivitetslogg: IAktivitetslogg) =
        behandlingsporing.organisasjonsnummer.tilYrkesaktivitet().let { yrkesaktivitet ->
            arbeidsgivere.finn(yrkesaktivitet) ?: aktivitetslogg.logiskFeil("Finner ikke arbeidsgiver")
        }

    private fun MutableList<Arbeidsgiver>.finnEllerOpprett(yrkesaktivitet: Yrkesaktivitet, aktivitetslogg: IAktivitetslogg) =
        finn(yrkesaktivitet) ?: Arbeidsgiver(this@Person, yrkesaktivitet, jurist).also { arbeidsgiver ->
            aktivitetslogg.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", yrkesaktivitet)
            add(arbeidsgiver)
        }

    internal fun nåværendeVedtaksperioder(filter: VedtaksperiodeFilter) =
        arbeidsgivere.nåværendeVedtaksperioder(filter)

    internal fun avventerSøknad(periode: Periode) = arbeidsgivere.avventerSøknad(periode)
    internal fun vedtaksperioder(filter: VedtaksperiodeFilter) = arbeidsgivere.vedtaksperioder(filter)
    internal fun mursteinsperioder(utgangspunkt: Vedtaksperiode) = arbeidsgivere.mursteinsperioder(utgangspunkt)

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
        vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun blitt6GBegrensetSidenSist(skjæringstidspunkt: LocalDate) =
        vilkårsgrunnlagHistorikk.blitt6GBegrensetSidenSist(skjæringstidspunkt)

    internal fun lagreVilkårsgrunnlag(vilkårsgrunnlag: VilkårsgrunnlagHistorikk.Grunnlagsdata) {
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag)
    }

    internal fun beregnSkjæringstidspunkt() = arbeidsgivere.beregnSkjæringstidspunkt(infotrygdhistorikk)
    internal fun sykdomshistorikkEndret() {
        arbeidsgivere.beregnSkjæringstidspunkter(infotrygdhistorikk)
    }

    internal fun søppelbøtte(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, filter: VedtaksperiodeFilter) {
        infotrygdhistorikk.tøm()
        Arbeidsgiver.søppelbøtte(arbeidsgivere, hendelse, aktivitetslogg, filter)
        sykdomshistorikkEndret()
        ryddOppVilkårsgrunnlag(aktivitetslogg)
        gjenopptaBehandling(aktivitetslogg)
    }

    internal fun emitInntektsmeldingFørSøknadEvent(
        meldingsreferanseId: UUID,
        relevanteSykmeldingsperioder: List<Periode>,
        organisasjonsnummer: String
    ) {
        observers.forEach {
            it.inntektsmeldingFørSøknad(PersonObserver.InntektsmeldingFørSøknadEvent(meldingsreferanseId, relevanteSykmeldingsperioder, organisasjonsnummer))
        }
    }

    internal fun emitInntektsmeldingIkkeHåndtert(hendelse: Hendelse, organisasjonsnummer: String, harPeriodeInnenfor16Dager: Boolean) {
        observers.forEach {
            it.inntektsmeldingIkkeHåndtert(hendelse.metadata.meldingsreferanseId, organisasjonsnummer, harPeriodeInnenfor16Dager)
        }
    }

    internal fun emitInntektsmeldingHåndtert(meldingsreferanseId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {
        observers.forEach {
            it.inntektsmeldingHåndtert(meldingsreferanseId, vedtaksperiodeId, organisasjonsnummer)
        }
    }

    internal fun sendSkatteinntekterLagtTilGrunn(skatteinntekterLagtTilGrunnEvent: PersonObserver.SkatteinntekterLagtTilGrunnEvent) {
        observers.forEach {
            it.skatteinntekterLagtTilGrunn(skatteinntekterLagtTilGrunnEvent)
        }
    }

    internal fun emitSøknadHåndtert(meldingsreferanseId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) {
        observers.forEach {
            it.søknadHåndtert(meldingsreferanseId, vedtaksperiodeId, organisasjonsnummer)
        }
    }

    internal fun oppdaterVilkårsgrunnlagMedInntektene(
        skjæringstidspunkt: LocalDate,
        aktivitetslogg: IAktivitetslogg,
        periode: Periode,
        nyeInntekter: List<NyInntektUnderveis>,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)
        if (grunnlag == null) {
            aktivitetslogg.info("Fant ikke vilkårsgrunnlag på skjæringstidspunkt $skjæringstidspunkt")
            return
        }
        nyeInntekter.forEach { inntekt ->
            finnEllerOpprettArbeidsgiver(inntekt.orgnummer.tilYrkesaktivitet(), aktivitetslogg)
        }
        val nyttGrunnlag = grunnlag.tilkomneInntekterFraSøknaden(aktivitetslogg, periode, nyeInntekter, subsumsjonslogg) ?: return
        nyttVilkårsgrunnlag(aktivitetslogg, nyttGrunnlag)
    }

    internal fun nyeArbeidsgiverInntektsopplysninger(
        hendelse: Hendelse,
        skjæringstidspunkt: LocalDate,
        organisasjonsnummer: String,
        inntekt: Arbeidsgiverinntekt,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonslogg: Subsumsjonslogg
    ): Revurderingseventyr? {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)
        if (grunnlag == null) {
            aktivitetslogg.info("Fant ikke vilkårsgrunnlag på skjæringstidspunkt $skjæringstidspunkt")
            return null
        }
        val (nyttGrunnlag, endretInntektsgrunnlag) = grunnlag.nyeArbeidsgiverInntektsopplysninger(organisasjonsnummer, inntekt, aktivitetslogg, subsumsjonslogg) ?: return null

        val endretInntektForArbeidsgiver = endretInntektsgrunnlag.inntekter.first { før -> før.inntektFør.orgnummer == organisasjonsnummer }

        if (endretInntektForArbeidsgiver.inntektFør.korrigertInntekt == null) {
            when (val inntektFraFør = endretInntektForArbeidsgiver.inntektFør.inntektsopplysning) {
                is Arbeidsgiverinntekt -> {
                    arbeidsgiveropplysningerKorrigert(
                        PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                            korrigertInntektsmeldingId = inntektFraFør.inntektsdata.hendelseId,
                            korrigerendeInntektektsopplysningstype = INNTEKTSMELDING,
                            korrigerendeInntektsopplysningId = inntekt.inntektsdata.hendelseId
                        )
                    )
                }

                else -> { /* gjør ingenting */ }
            }
        }

        val eventyr = Revurderingseventyr.korrigertInntektsmeldingInntektsopplysninger(hendelse, skjæringstidspunkt, endretInntektsgrunnlag.endringFom)
        nyttVilkårsgrunnlag(aktivitetslogg, nyttGrunnlag)
        return eventyr
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: OverstyrArbeidsgiveropplysninger,
        aktivitetslogg: IAktivitetslogg,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ): Revurderingseventyr? {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return null
        val (nyttGrunnlag, endretInntektsgrunnlag) = grunnlag.overstyrArbeidsgiveropplysninger(hendelse, aktivitetslogg, subsumsjonslogg) ?: return null
        nyttVilkårsgrunnlag(aktivitetslogg, nyttGrunnlag)

        endretInntektsgrunnlag.inntekter
            .forEach {
                val opptjeningFom = nyttGrunnlag.opptjening!!.startdatoFor(it.inntektEtter.orgnummer)
                hendelse.subsummer(subsumsjonslogg, opptjeningFom, it.inntektEtter.orgnummer)
                when (it.inntektFør.inntektsopplysning) {
                    is Arbeidsgiverinntekt -> {
                        arbeidsgiveropplysningerKorrigert(
                            PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                                korrigertInntektsmeldingId = it.inntektFør.inntektsopplysning.inntektsdata.hendelseId,
                                korrigerendeInntektektsopplysningstype = SAKSBEHANDLER,
                                korrigerendeInntektsopplysningId = hendelse.metadata.meldingsreferanseId
                            )
                        )
                    }
                    is Infotrygd,
                    is Saksbehandler,
                    is SkattSykepengegrunnlag -> { /* gjør ingenting */ }
                }
            }

        val eventyr = Revurderingseventyr.arbeidsgiveropplysninger(hendelse, skjæringstidspunkt, endretInntektsgrunnlag.endringFom)
        return eventyr
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: SkjønnsmessigFastsettelse,
        aktivitetslogg: IAktivitetslogg,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return aktivitetslogg.funksjonellFeil(RV_VV_10)
        val (nyttGrunnlag, endretInntektsgrunnlag) = grunnlag.skjønnsmessigFastsettelse(hendelse, aktivitetslogg, subsumsjonslogg) ?: return
        nyttVilkårsgrunnlag(aktivitetslogg, nyttGrunnlag)

        val eventyr = Revurderingseventyr.skjønnsmessigFastsettelse(hendelse, skjæringstidspunkt, endretInntektsgrunnlag.endringFom)
        igangsettOverstyring(eventyr, aktivitetslogg)
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: OverstyrArbeidsforhold,
        aktivitetslogg: IAktivitetslogg,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return aktivitetslogg.funksjonellFeil(RV_VV_10)
        nyttVilkårsgrunnlag(aktivitetslogg, grunnlag.overstyrArbeidsforhold(hendelse, aktivitetslogg, subsumsjonslogg))
        igangsettOverstyring(Revurderingseventyr.arbeidsforhold(hendelse, skjæringstidspunkt), aktivitetslogg)
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: Grunnbeløpsregulering,
        aktivitetslogg: IAktivitetslogg,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return aktivitetslogg.funksjonellFeil(RV_VV_10)
        grunnlag.grunnbeløpsregulering(hendelse, aktivitetslogg, subsumsjonslogg)?.let { grunnbeløpsregulert ->
            nyttVilkårsgrunnlag(aktivitetslogg, grunnbeløpsregulert)
            igangsettOverstyring(Revurderingseventyr.grunnbeløpsregulering(hendelse, skjæringstidspunkt), aktivitetslogg)
        }
    }

    private fun nyttVilkårsgrunnlag(aktivitetslogg: IAktivitetslogg, vilkårsgrunnlag: VilkårsgrunnlagElement) {
        aktivitetslogg.kontekst(vilkårsgrunnlag)
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag)
    }

    private var gjenopptaBehandlingNy = false
    internal fun gjenopptaBehandling(aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.info("Forbereder gjenoppta behandling")
        gjenopptaBehandlingNy = true
    }

    private fun håndterGjenoppta(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        while (gjenopptaBehandlingNy) {
            gjenopptaBehandlingNy = false
            arbeidsgivere.gjenopptaBehandling(hendelse, aktivitetslogg)
        }
        arbeidsgivere.validerTilstand(hendelse, aktivitetslogg)
        håndterVedtaksperiodeVenter(hendelse)
        behandlingUtført()
    }

    private fun håndterVedtaksperiodeVenter(hendelse: Hendelse) {
        when (hendelse) {
            is Sykmelding -> { /* Sykmelding fører ikke til endringer i tiltander, så sender ikke signal etter håndtering av den */
            }

            else -> {
                val nestemann = arbeidsgivere.nestemann() ?: return
                val eventer = arbeidsgivere.venter(nestemann)
                    .map { it.event() }
                observers.forEach { it.vedtaksperioderVenter(eventer) }
            }
        }
    }

    private fun behandlingUtført() {
        observers.forEach { it.behandlingUtført() }
    }

    internal fun igangsettOverstyring(revurdering: Revurderingseventyr, aktivitetslogg: IAktivitetslogg) {
        arbeidsgivere.igangsettOverstyring(revurdering, aktivitetslogg)
        revurdering.sendOverstyringIgangsattEvent(this)
        ryddOppVilkårsgrunnlag(aktivitetslogg)
    }

    private fun ryddOppVilkårsgrunnlag(aktivitetslogg: IAktivitetslogg) {
        val skjæringstidspunkter = arbeidsgivere.aktiveSkjæringstidspunkter()
        vilkårsgrunnlagHistorikk.oppdaterHistorikk(aktivitetslogg, skjæringstidspunkter)
    }

    internal fun nyVedtaksperiodeUtbetaling(organisasjonsnummer: String, utbetalingId: UUID, vedtaksperiodeId: UUID) {
        observers.forEach { it.nyVedtaksperiodeUtbetaling(organisasjonsnummer, utbetalingId, vedtaksperiodeId) }
    }

    internal fun vedtaksperiodeOpprettet(vedtaksperiodeId: UUID, organisasjonsnummer: String, periode: Periode, skjæringstidspunkt: LocalDate, opprettet: LocalDateTime) {
        val event = PersonObserver.VedtaksperiodeOpprettet(vedtaksperiodeId, organisasjonsnummer, periode, skjæringstidspunkt, opprettet)
        observers.forEach { it.vedtaksperiodeOpprettet(event) }
    }

    internal fun erBehandletIInfotrygd(vedtaksperiode: Periode): Boolean {
        return infotrygdhistorikk.harUtbetaltI(vedtaksperiode) || infotrygdhistorikk.harFerieI(vedtaksperiode)
    }

    internal fun vedtaksperiodeAnnullert(vedtaksperiodeAnnullertEvent: PersonObserver.VedtaksperiodeAnnullertEvent) {
        observers.forEach { it.vedtaksperiodeAnnullert(vedtaksperiodeAnnullertEvent) }
    }

    fun dto() = PersonUtDto(
        fødselsnummer = personidentifikator.toString(),
        alder = alder.dto(),
        arbeidsgivere = arbeidsgivere.map { it.dto(arbeidsgivere.nestemann()) },
        opprettet = opprettet,
        infotrygdhistorikk = infotrygdhistorikk.dto(),
        vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk.dto(),
        minimumSykdomsgradVurdering = minimumSykdomsgradsvurdering.dto()
    )
}
