package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.Toggle
import no.nav.helse.dto.deserialisering.PersonInnDto
import no.nav.helse.dto.serialisering.PersonUtDto
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.ArbeidstakerHendelse
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.IdentOpphørt
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.KanIkkeBehandlesHer
import no.nav.helse.hendelser.MinimumSykdomsgradsvurderingMelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.VedtakFattet
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingpåminnelse
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.person.Arbeidsgiver.Companion.aktiveSkjæringstidspunkter
import no.nav.helse.person.Arbeidsgiver.Companion.avklarSykepengegrunnlag
import no.nav.helse.person.Arbeidsgiver.Companion.avventerSøknad
import no.nav.helse.person.Arbeidsgiver.Companion.beregnFeriepengerForAlleArbeidsgivere
import no.nav.helse.person.Arbeidsgiver.Companion.beregnSkjæringstidspunkt
import no.nav.helse.person.Arbeidsgiver.Companion.beregnSkjæringstidspunkter
import no.nav.helse.person.Arbeidsgiver.Companion.finn
import no.nav.helse.person.Arbeidsgiver.Companion.førsteFraværsdager
import no.nav.helse.person.Arbeidsgiver.Companion.gjenopptaBehandling
import no.nav.helse.person.Arbeidsgiver.Companion.håndter
import no.nav.helse.person.Arbeidsgiver.Companion.håndterHistorikkFraInfotrygd
import no.nav.helse.person.Arbeidsgiver.Companion.igangsettOverstyring
import no.nav.helse.person.Arbeidsgiver.Companion.nestemann
import no.nav.helse.person.Arbeidsgiver.Companion.nåværendeVedtaksperioder
import no.nav.helse.person.Arbeidsgiver.Companion.tidligsteDato
import no.nav.helse.person.Arbeidsgiver.Companion.validerTilstand
import no.nav.helse.person.Arbeidsgiver.Companion.vedtaksperioder
import no.nav.helse.person.Arbeidsgiver.Companion.venter
import no.nav.helse.person.PersonObserver.FørsteFraværsdag
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
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.view.PersonView
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import kotlin.math.roundToInt

class Person private constructor(
    aktørId: String,
    personidentifikator: Personidentifikator,
    internal var alder: Alder,
    private val _arbeidsgivere: MutableList<Arbeidsgiver>,
    internal val aktivitetslogg: Aktivitetslogg,
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
                aktørId = dto.aktørId,
                personidentifikator = Personidentifikator(dto.fødselsnummer),
                alder = alder,
                _arbeidsgivere = arbeidsgivere,
                aktivitetslogg = Aktivitetslogg(),
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
                Arbeidsgiver.gjenopprett(
                    person,
                    alder,
                    dto.aktørId,
                    dto.fødselsnummer,
                    it,
                    subsumsjonslogg,
                    grunnlagsdataMap
                )
            })
            return person
        }
    }

    internal constructor(
        aktørId: String,
        personidentifikator: Personidentifikator,
        alder: Alder,
        subsumsjonslogg: Subsumsjonslogg,
        regler: ArbeidsgiverRegler
    ) : this(
        aktørId,
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
        aktørId: String,
        personidentifikator: Personidentifikator,
        alder: Alder,
        jurist: Subsumsjonslogg
    ) : this(aktørId, personidentifikator, alder, jurist, NormalArbeidstaker)

    internal val arbeidsgivere: List<Arbeidsgiver> get() = _arbeidsgivere.toList()

    var aktørId: String = aktørId
        private set

    var personidentifikator: Personidentifikator = personidentifikator
        private set

    private val observers = mutableListOf<PersonObserver>()

    internal fun view() = PersonView(
        arbeidsgivere = arbeidsgivere.map{it.view()},
        vilkårsgrunnlaghistorikk = vilkårsgrunnlagHistorikk.view()
    )

    fun håndter(sykmelding: Sykmelding) {
        registrer(sykmelding, "Behandler sykmelding")
        tidligereBehandlinger(sykmelding, sykmelding.periode())
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(sykmelding)
        arbeidsgiver.håndter(sykmelding)
        håndterGjenoppta(sykmelding)
    }

    fun håndter(avbruttSøknad: AvbruttSøknad) {
        registrer(avbruttSøknad, "Behandler avbrutt søknad")
        val arbeidsgiver = finnArbeidsgiver(avbruttSøknad)
        arbeidsgiver.håndter(avbruttSøknad)
        gjenopptaBehandling(avbruttSøknad)
        håndterGjenoppta(avbruttSøknad)
    }

    fun håndter(forkastSykmeldingsperioder: ForkastSykmeldingsperioder) {
        registrer(forkastSykmeldingsperioder, "Behandler forkasting av sykmeldingsperioder")
        finnArbeidsgiver(forkastSykmeldingsperioder).håndter(forkastSykmeldingsperioder)
        gjenopptaBehandling(forkastSykmeldingsperioder)
        håndterGjenoppta(forkastSykmeldingsperioder)
    }

    fun håndter(anmodningOmForkasting: AnmodningOmForkasting) {
        registrer(anmodningOmForkasting, "Behandler anmodning om forkasting")
        finnArbeidsgiver(anmodningOmForkasting).håndter(anmodningOmForkasting)
        håndterGjenoppta(anmodningOmForkasting)
    }

    fun håndter(søknad: Søknad) {
        registrer(søknad, "Behandler søknad")
        tidligereBehandlinger(søknad, søknad.periode())
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(søknad)
        søknad.forUng(alder)
        arbeidsgiver.håndter(søknad, arbeidsgivere.toList(), infotrygdhistorikk)
        håndterGjenoppta(søknad)
    }

    fun håndter(inntektsmelding: Inntektsmelding) {
        registrer(inntektsmelding, "Behandler inntektsmelding")
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(inntektsmelding)
        arbeidsgiver.håndter(inntektsmelding)
        arbeidsgiver.inntektsmeldingFerdigbehandlet(inntektsmelding)
        håndterGjenoppta(inntektsmelding)
    }

    fun håndter(replays: InntektsmeldingerReplay) {
        registrer(replays, "Behandler replay av inntektsmeldinger")
        finnArbeidsgiver(replays).håndter(replays)
        håndterGjenoppta(replays)
    }

    fun håndter(gjenopplivVilkårsgrunnlag: GjenopplivVilkårsgrunnlag) {
        gjenopplivVilkårsgrunnlag.valider(arbeidsgivere.map { it.organisasjonsnummer() })
        gjenopplivVilkårsgrunnlag.gjenoppliv(vilkårsgrunnlagHistorikk)
        gjenopptaBehandling(gjenopplivVilkårsgrunnlag)
        håndterGjenoppta(gjenopplivVilkårsgrunnlag)
    }

    fun håndter(melding: MinimumSykdomsgradsvurderingMelding) {
        registrer(melding, "Behandler minimum sykdomsgradvurdering")
        melding.oppdater(this.minimumSykdomsgradsvurdering)
        this.igangsettOverstyring(Revurderingseventyr.minimumSykdomsgradVurdert(melding, melding.periodeForEndring()))
        håndterGjenoppta(melding)
    }

    private fun tidligereBehandlinger(hendelse: ArbeidstakerHendelse, periode: Periode) {
        val cutoff = periode.start.minusMonths(6)
        val andreBehandledeVedtaksperioder = tidligereBehandlinger.flatMap { it.vedtaksperioderEtter(cutoff) }
        if (andreBehandledeVedtaksperioder.isNotEmpty()) {
            hendelse.funksjonellFeil(Varselkode.RV_AN_5)
            val msg = andreBehandledeVedtaksperioder.map {
                "vedtaksperiode(${it.periode()})"
            }
            hendelse.info("""hendelse: ${hendelse::class.java.simpleName} ($periode) kaster ut personen aktørid: $aktørId fnr: $personidentifikator 
                | tidligere behandlede identer: ${tidligereBehandlinger.map { it.personidentifikator }}
                | tidligere behandlede perioder: ${msg.joinToString { it }}
                | cutoff: $cutoff""".trimMargin())
        }
    }

    private fun vedtaksperioderEtter(dato: LocalDate) = arbeidsgivere.flatMap { it.vedtaksperioderEtter(dato) }

    fun håndter(dødsmelding: Dødsmelding) {
        dødsmelding.kontekst(aktivitetslogg, this)
        dødsmelding.info("Registrerer dødsdato")
        alder = dødsmelding.dødsdato(alder)
        håndterGjenoppta(dødsmelding)
    }

    fun håndter(identOpphørt: IdentOpphørt, nyPersonidentifikator: Personidentifikator, nyAktørId: String) {
        identOpphørt.kontekst(aktivitetslogg, this)
        identOpphørt.info("Person har byttet ident til $nyPersonidentifikator")
        this.personidentifikator = nyPersonidentifikator
        this.aktørId = nyAktørId
        håndterGjenoppta(identOpphørt)
    }

    fun håndter(identOpphørt: IdentOpphørt, nyPersonidentifikator: Personidentifikator) {
        identOpphørt.kontekst(aktivitetslogg, this)
        identOpphørt.info("Person har byttet ident til $nyPersonidentifikator, men gjør ingenting med det foreløpig")
        håndterGjenoppta(identOpphørt)
    }

    fun håndter(infotrygdendring: Infotrygdendring) {
        infotrygdendring.kontekst(aktivitetslogg, this)
        val tidligsteDato = arbeidsgivere.tidligsteDato()
        infotrygdhistorikk.oppfrisk(infotrygdendring, tidligsteDato)
        håndterGjenoppta(infotrygdendring)
    }

    fun håndter(utbetalingshistorikkEtterInfotrygdendring: UtbetalingshistorikkEtterInfotrygdendring) = håndterHistorikkFraInfotrygd(utbetalingshistorikkEtterInfotrygdendring) {
        utbetalingshistorikkEtterInfotrygdendring.oppdaterHistorikk(it)
    }

    fun håndter(utbetalingshistorikk: Utbetalingshistorikk) =
        håndterHistorikkFraInfotrygd(utbetalingshistorikk) {
        utbetalingshistorikk.oppdaterHistorikk(it)
    }

    private fun håndterHistorikkFraInfotrygd(hendelse: Hendelse, oppdatertHistorikk: (infotrygdhistorikk: Infotrygdhistorikk) -> Boolean) {
        hendelse.kontekst(aktivitetslogg, this)
        oppdatertHistorikk(infotrygdhistorikk)
        sykdomshistorikkEndret()
        arbeidsgivere.håndterHistorikkFraInfotrygd(hendelse, infotrygdhistorikk)
        val alleVedtaksperioder = arbeidsgivere.vedtaksperioder { true }
        emitOverlappendeInfotrygdperioder(alleVedtaksperioder)
        håndterGjenoppta(hendelse)
    }

    private fun emitOverlappendeInfotrygdperioder(alleVedtaksperioder: List<Vedtaksperiode>) {
        if (!infotrygdhistorikk.harHistorikk()) return
        val hendelseId = infotrygdhistorikk.siste.hendelseId!!
        val perioder = infotrygdhistorikk.siste.perioder
        val event = alleVedtaksperioder.fold(PersonObserver.OverlappendeInfotrygdperioder(emptyList(), hendelseId.toString())) { result, vedtaksperiode ->
            vedtaksperiode.overlappendeInfotrygdperioder(result, perioder)
        }
        emitOverlappendeInfotrygdperioder(event)
    }

    fun håndter(utbetalingshistorikk: UtbetalingshistorikkForFeriepenger) {
        utbetalingshistorikk.kontekst(aktivitetslogg, this)

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            utbetalingshistorikk.info("Starter beregning av feriepenger")
        }

        if (utbetalingshistorikk.skalBeregnesManuelt) {
            utbetalingshistorikk.info("Person er markert for manuell beregning av feriepenger - aktørId: $aktørId")
            return
        }

        // Hardkodet dato skal være datoen Infotrygd sist kjørte feriepenger
        val DATO_FOR_SISTE_FERIEPENGEKJØRING_I_INFOTRYGD = LocalDate.of(2024, 8, 24)

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
            utbetalingshistorikk.info(
                """
                Beregnet feriepengebeløp til person i IT samsvarer ikke med faktisk utbetalt beløp
                AktørId: $aktørId
                Faktisk utbetalt beløp: $feriepengepengebeløpPersonUtbetaltAvInfotrygd
                Beregnet beløp: $beregnetFeriepengebeløpPersonInfotrygd
                """.trimIndent()
            )
        }

        utbetalingshistorikk.sikreAtArbeidsgivereEksisterer {
            _arbeidsgivere.finnEllerOpprett(Yrkesaktivitet.Arbeidstaker(it), utbetalingshistorikk)
        }
        arbeidsgivere.beregnFeriepengerForAlleArbeidsgivere(
            aktørId,
            personidentifikator,
            feriepengeberegner,
            utbetalingshistorikk
        )

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            aktivitetslogg.info("Feriepenger er utbetalt")
        }
    }

    fun håndter(ytelser: Ytelser) {
        registrer(ytelser, "Behandler historiske utbetalinger og inntekter")
        finnArbeidsgiver(ytelser).håndter(ytelser, infotrygdhistorikk)
        håndterGjenoppta(ytelser)
    }

    fun håndter(utbetalingsgodkjenning: Utbetalingsgodkjenning) {
        registrer(utbetalingsgodkjenning, "Behandler utbetalingsgodkjenning")
        finnArbeidsgiver(utbetalingsgodkjenning).håndter(utbetalingsgodkjenning)
        håndterGjenoppta(utbetalingsgodkjenning)
    }

    fun håndter(vedtakFattet: VedtakFattet) {
        registrer(vedtakFattet, "Behandler vedtak fattet")
        finnArbeidsgiver(vedtakFattet).håndter(vedtakFattet)
        håndterGjenoppta(vedtakFattet)
    }

    fun håndter(kanIkkeBehandlesHer: KanIkkeBehandlesHer) {
        registrer(kanIkkeBehandlesHer, "Behandler kan ikke behandles her")
        finnArbeidsgiver(kanIkkeBehandlesHer).håndter(kanIkkeBehandlesHer)
        håndterGjenoppta(kanIkkeBehandlesHer)
    }

    fun håndter(sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver) {
        registrer(sykepengegrunnlagForArbeidsgiver, "Behandler sykepengegrunnlag for arbeidsgiver")
        finnArbeidsgiver(sykepengegrunnlagForArbeidsgiver).håndter(sykepengegrunnlagForArbeidsgiver)
        håndterGjenoppta(sykepengegrunnlagForArbeidsgiver)
    }

    fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        registrer(vilkårsgrunnlag, "Behandler vilkårsgrunnlag")
        finnArbeidsgiver(vilkårsgrunnlag).håndter(vilkårsgrunnlag)
        håndterGjenoppta(vilkårsgrunnlag)
    }

    fun håndter(simulering: Simulering) {
        registrer(simulering, "Behandler simulering")
        finnArbeidsgiver(simulering).håndter(simulering)
        håndterGjenoppta(simulering)
    }

    fun håndter(utbetaling: UtbetalingHendelse) {
        registrer(utbetaling, "Behandler utbetaling")
        finnArbeidsgiver(utbetaling).håndter(utbetaling)
        håndterGjenoppta(utbetaling)
    }

    fun håndter(påminnelse: Utbetalingpåminnelse) {
        påminnelse.kontekst(aktivitetslogg, this)
        finnArbeidsgiver(påminnelse).håndter(påminnelse)
        håndterGjenoppta(påminnelse)
    }

    fun håndter(påminnelse: PersonPåminnelse) {
        påminnelse.kontekst(aktivitetslogg, this)
        påminnelse.info("Håndterer påminnelse for person")
        håndterGjenoppta(påminnelse)
    }

    fun håndter(påminnelse: Påminnelse) {
        try {
            påminnelse.kontekst(aktivitetslogg, this)
            if (finnArbeidsgiver(påminnelse).håndter(påminnelse)) return håndterGjenoppta(påminnelse)
        } catch (err: Aktivitetslogg.AktivitetException) {
            påminnelse.funksjonellFeil(RV_AG_1)
        }
        observers.forEach { påminnelse.vedtaksperiodeIkkeFunnet(it) }
        håndterGjenoppta(påminnelse)
    }

    fun håndter(hendelse: OverstyrTidslinje) {
        hendelse.kontekst(aktivitetslogg, this)
        finnArbeidsgiver(hendelse).håndter(hendelse)
        håndterGjenoppta(hendelse)
    }

    fun håndter(hendelse: OverstyrArbeidsgiveropplysninger) {
        hendelse.kontekst(aktivitetslogg, this)
        check(arbeidsgivere.håndter(hendelse)) {
            "Ingen vedtaksperioder håndterte overstyringen av arbeidsgiveropplysninger"
        }
        håndterGjenoppta(hendelse)
    }

    fun håndter(hendelse: SkjønnsmessigFastsettelse) {
        hendelse.kontekst(aktivitetslogg, this)
        check(arbeidsgivere.håndter(hendelse)) {
            "Ingen vedtaksperioder håndterte skjønnsmessig fastsettelse"
        }
        håndterGjenoppta(hendelse)
    }

    fun håndter(overstyrArbeidsforhold: OverstyrArbeidsforhold) {
        overstyrArbeidsforhold.kontekst(aktivitetslogg, this)
        check(arbeidsgivere.håndter(overstyrArbeidsforhold)) {
            "Kan ikke overstyre arbeidsforhold fordi ingen vedtaksperioder håndterte hendelsen"
        }
        håndterGjenoppta(overstyrArbeidsforhold)
    }

    fun håndter(hendelse: AnnullerUtbetaling) {
        hendelse.kontekst(aktivitetslogg, this)
        arbeidsgivere.finn(hendelse.organisasjonsnummer().tilYrkesaktivitet())?.håndter(hendelse)
            ?: hendelse.funksjonellFeil(RV_AG_1)
        håndterGjenoppta(hendelse)
    }

    fun håndter(hendelse: Grunnbeløpsregulering) {
        hendelse.kontekst(aktivitetslogg, this)
        if (arbeidsgivere.håndter(hendelse)) return håndterGjenoppta(hendelse)
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

    internal fun vedtaksperiodeVenter(event: PersonObserver.VedtaksperiodeVenterEvent) {
        observers.forEach { it.vedtaksperiodeVenter(event) }
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
                aktørId = aktørId,
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

    internal fun emitOverlappendeInfotrygdperioder(event: PersonObserver.OverlappendeInfotrygdperioder) {
        observers.forEach { it.overlappendeInfotrygdperioder(event) }
    }

    internal fun feriepengerUtbetalt(feriepengerUtbetaltEvent: PersonObserver.FeriepengerUtbetaltEvent) {
        observers.forEach { it.feriepengerUtbetalt(feriepengerUtbetaltEvent) }
    }

    internal fun grunnlagForFeriepenger() = arbeidsgivere.map { it.grunnlagForFeriepenger() }

    internal fun trengerHistorikkFraInfotrygd(hendelse: IAktivitetslogg) {
        infotrygdhistorikk.oppfriskNødvendig(hendelse, arbeidsgivere.tidligsteDato())
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Person", mapOf("fødselsnummer" to personidentifikator.toString(), "aktørId" to aktørId))
    }

    private fun registrer(hendelse: PersonHendelse, melding: String) {
        hendelse.kontekst(aktivitetslogg, this)
        hendelse.info(melding)
    }

    private fun finnEllerOpprettArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        finnEllerOpprettArbeidsgiver(hendelse.organisasjonsnummer().tilYrkesaktivitet(), hendelse)

    private fun finnEllerOpprettArbeidsgiver(yrkesaktivitet: Yrkesaktivitet, aktivitetslogg: IAktivitetslogg) =
        _arbeidsgivere.finnEllerOpprett(yrkesaktivitet, aktivitetslogg)

    private fun finnArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        hendelse.organisasjonsnummer().tilYrkesaktivitet().let { yrkesaktivitet ->
            arbeidsgivere.finn(yrkesaktivitet) ?: hendelse.logiskFeil("Finner ikke arbeidsgiver")
        }

    private fun MutableList<Arbeidsgiver>.finnEllerOpprett(yrkesaktivitet: Yrkesaktivitet, aktivitetslogg: IAktivitetslogg) =
        finn(yrkesaktivitet) ?: Arbeidsgiver(this@Person, yrkesaktivitet, jurist).also { arbeidsgiver ->
            aktivitetslogg.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", yrkesaktivitet)
            add(arbeidsgiver)
        }

    internal fun nåværendeVedtaksperioder(filter: VedtaksperiodeFilter) =
        arbeidsgivere.nåværendeVedtaksperioder(filter).sorted()

    internal fun avventerSøknad(periode: Periode) = arbeidsgivere.avventerSøknad(periode)
    internal fun vedtaksperioder(filter: VedtaksperiodeFilter) = arbeidsgivere.vedtaksperioder(filter).sorted()
    internal fun førsteFraværsdager(skjæringstidspunkt: LocalDate) = arbeidsgivere.førsteFraværsdager(skjæringstidspunkt)

    internal fun forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer: String, skjæringstidspunkt: LocalDate, periode: Periode) =
        vilkårsgrunnlagHistorikk.forespurtInntektOgRefusjonsopplysninger(organisasjonsnummer, skjæringstidspunkt, periode)

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
        vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun blitt6GBegrensetSidenSist(skjæringstidspunkt: LocalDate) =
        vilkårsgrunnlagHistorikk.blitt6GBegrensetSidenSist(skjæringstidspunkt)

    internal fun lagreVilkårsgrunnlag(vilkårsgrunnlag: VilkårsgrunnlagHistorikk.Grunnlagsdata) {
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag)
    }

    internal fun avklarSykepengegrunnlag(
        hendelse: IAktivitetslogg,
        skjæringstidspunkt: LocalDate,
        sammenligningsgrunnlag: Sammenligningsgrunnlag,
        skatteopplysninger: Map<String, SkattSykepengegrunnlag>,
        subsumsjonslogg: Subsumsjonslogg
    ): Inntektsgrunnlag {
        skatteopplysninger.keys.forEach { orgnr -> finnEllerOpprettArbeidsgiver(orgnr.tilYrkesaktivitet(), hendelse) } // oppretter evt. nye arbeidsgivere
        return Inntektsgrunnlag.opprett(
            alder,
            arbeidsgivere.avklarSykepengegrunnlag(hendelse, skjæringstidspunkt, skatteopplysninger),
            skjæringstidspunkt,
            sammenligningsgrunnlag,
            subsumsjonslogg
        )
    }

    internal fun beregnSkjæringstidspunkt() = arbeidsgivere.beregnSkjæringstidspunkt(infotrygdhistorikk)

    internal fun sykdomshistorikkEndret() {
        arbeidsgivere.beregnSkjæringstidspunkter(infotrygdhistorikk)
    }

    internal fun søppelbøtte(hendelse: Hendelse, filter: VedtaksperiodeFilter) {
        infotrygdhistorikk.tøm()
        Arbeidsgiver.søppelbøtte(arbeidsgivere, hendelse, filter)
        sykdomshistorikkEndret()
        ryddOppVilkårsgrunnlag()
        gjenopptaBehandling(hendelse)
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

    internal fun emitInntektsmeldingIkkeHåndtert(hendelse: Inntektsmelding, organisasjonsnummer: String, harPeriodeInnenfor16Dager: Boolean) {
        observers.forEach {
            it.inntektsmeldingIkkeHåndtert(hendelse.meldingsreferanseId(), organisasjonsnummer, harPeriodeInnenfor16Dager)
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
        søknad: Søknad,
        orgnummereMedTilkomneInntekter: List<String>,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)
        if (grunnlag == null) {
            søknad.info("Fant ikke vilkårsgrunnlag på skjæringstidspunkt $skjæringstidspunkt")
            return
        }
        orgnummereMedTilkomneInntekter.forEach { orgnr ->
            finnEllerOpprettArbeidsgiver(orgnr.tilYrkesaktivitet(), søknad)
        }
        val nyttGrunnlag = grunnlag.tilkomneInntekterFraSøknaden(søknad, subsumsjonslogg)
        nyttVilkårsgrunnlag(søknad, nyttGrunnlag)
    }

    internal fun nyeArbeidsgiverInntektsopplysninger(
        skjæringstidspunkt: LocalDate,
        inntektsmelding: Inntektsmelding,
        subsumsjonslogg: Subsumsjonslogg
    ): Revurderingseventyr? {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)
        if (grunnlag == null) {
            inntektsmelding.info("Fant ikke vilkårsgrunnlag på skjæringstidspunkt $skjæringstidspunkt")
            return null
        }
        val (nyttGrunnlag, eventyr) = grunnlag.nyeArbeidsgiverInntektsopplysninger(this, inntektsmelding, subsumsjonslogg)
        nyttVilkårsgrunnlag(inntektsmelding, nyttGrunnlag)
        return eventyr
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: OverstyrArbeidsgiveropplysninger,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return hendelse.funksjonellFeil(RV_VV_10)
        val (nyttGrunnlag, eventyr) = grunnlag.overstyrArbeidsgiveropplysninger(this, hendelse, subsumsjonslogg)
        nyttVilkårsgrunnlag(hendelse, nyttGrunnlag)
        igangsettOverstyring(eventyr)
    }


    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: SkjønnsmessigFastsettelse,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return hendelse.funksjonellFeil(RV_VV_10)
        val (nyttGrunnlag, eventyr) = grunnlag.skjønnsmessigFastsettelse(hendelse, subsumsjonslogg)
        nyttVilkårsgrunnlag(hendelse, nyttGrunnlag)
        igangsettOverstyring(eventyr)
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: OverstyrArbeidsforhold,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return hendelse.funksjonellFeil(RV_VV_10)
        nyttVilkårsgrunnlag(hendelse, grunnlag.overstyrArbeidsforhold(hendelse, subsumsjonslogg))
        igangsettOverstyring(Revurderingseventyr.arbeidsforhold(hendelse, skjæringstidspunkt))
    }

    internal fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(
        hendelse: Grunnbeløpsregulering,
        skjæringstidspunkt: LocalDate,
        subsumsjonslogg: Subsumsjonslogg
    ) {
        val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) ?: return hendelse.funksjonellFeil(RV_VV_10)
        grunnlag.grunnbeløpsregulering(hendelse, subsumsjonslogg)?.let { grunnbeløpsregulert ->
            nyttVilkårsgrunnlag(hendelse, grunnbeløpsregulert)
            igangsettOverstyring(Revurderingseventyr.grunnbeløpsregulering(hendelse, skjæringstidspunkt))
        }
    }

    private fun nyttVilkårsgrunnlag(hendelse: IAktivitetslogg, vilkårsgrunnlag: VilkårsgrunnlagElement) {
        hendelse.kontekst(vilkårsgrunnlag)
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag)
    }
    private var gjenopptaBehandlingNy = false

    internal fun gjenopptaBehandling(hendelse: IAktivitetslogg) {
        hendelse.info("Forbereder gjenoppta behandling")
        gjenopptaBehandlingNy = true
    }

    private fun håndterGjenoppta(hendelse: Hendelse) {
        while (gjenopptaBehandlingNy) {
            gjenopptaBehandlingNy = false
            arbeidsgivere.gjenopptaBehandling(hendelse)
        }
        arbeidsgivere.validerTilstand(hendelse)
        håndterVedtaksperiodeVenter(hendelse)
        behandlingUtført()
    }

    private fun håndterVedtaksperiodeVenter(hendelse: Hendelse) {
        hendelse.venter {
            val nestemann = arbeidsgivere.nestemann() ?: return@venter
            arbeidsgivere.venter(nestemann)
        }
    }

    private fun behandlingUtført() {
        observers.forEach { it.behandlingUtført() }
    }

    internal fun igangsettOverstyring(revurdering: Revurderingseventyr) {
        arbeidsgivere.igangsettOverstyring(revurdering)
        revurdering.sendOverstyringIgangsattEvent(this)
        ryddOppVilkårsgrunnlag()
    }

    private fun ryddOppVilkårsgrunnlag() {
        val skjæringstidspunkter = arbeidsgivere.aktiveSkjæringstidspunkter()
        vilkårsgrunnlagHistorikk.oppdaterHistorikk(aktivitetslogg, skjæringstidspunkter)
    }

    internal fun nyVedtaksperiodeUtbetaling(organisasjonsnummer: String, utbetalingId: UUID, vedtaksperiodeId: UUID) {
        observers.forEach { it.nyVedtaksperiodeUtbetaling(personidentifikator, aktørId, organisasjonsnummer, utbetalingId, vedtaksperiodeId) }
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
        aktørId = aktørId,
        fødselsnummer = personidentifikator.toString(),
        alder = alder.dto(),
        arbeidsgivere = arbeidsgivere.map { it.dto(arbeidsgivere.nestemann()) },
        opprettet = opprettet,
        infotrygdhistorikk = infotrygdhistorikk.dto(),
        vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk.dto(),
        minimumSykdomsgradVurdering = minimumSykdomsgradsvurdering.dto()
    )

}

