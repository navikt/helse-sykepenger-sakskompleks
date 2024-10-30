package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.Toggle
import no.nav.helse.dto.deserialisering.ArbeidsgiverInnDto
import no.nav.helse.dto.serialisering.ArbeidsgiverUtDto
import no.nav.helse.etterlevelse.BehandlingSubsumsjonslogg
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.Behandlingsavgjørelse
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrInntektsgrunnlag
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.hendelser.SykdomstidslinjeHendelse
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingpåminnelse
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.slåSammenSykdomstidslinjer
import no.nav.helse.person.PersonObserver.UtbetalingEndretEvent.OppdragEventDetaljer
import no.nav.helse.person.Vedtaksperiode.Companion.AUU_SOM_VIL_UTBETALES
import no.nav.helse.person.Vedtaksperiode.Companion.HAR_PÅGÅENDE_UTBETALINGER
import no.nav.helse.person.Vedtaksperiode.Companion.MED_SKJÆRINGSTIDSPUNKT
import no.nav.helse.person.Vedtaksperiode.Companion.SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG
import no.nav.helse.person.Vedtaksperiode.Companion.aktiveSkjæringstidspunkter
import no.nav.helse.person.Vedtaksperiode.Companion.arbeidsgiverperioder
import no.nav.helse.person.Vedtaksperiode.Companion.beregnSkjæringstidspunkter
import no.nav.helse.person.Vedtaksperiode.Companion.checkBareEnPeriodeTilGodkjenningSamtidig
import no.nav.helse.person.Vedtaksperiode.Companion.egenmeldingsperioder
import no.nav.helse.person.Vedtaksperiode.Companion.harIngenSporingTilInntektsmeldingISykefraværet
import no.nav.helse.person.Vedtaksperiode.Companion.nestePeriodeSomSkalGjenopptas
import no.nav.helse.person.Vedtaksperiode.Companion.nåværendeVedtaksperiode
import no.nav.helse.person.Vedtaksperiode.Companion.periode
import no.nav.helse.person.Vedtaksperiode.Companion.refusjonseventyr
import no.nav.helse.person.Vedtaksperiode.Companion.refusjonstidslinje
import no.nav.helse.person.Vedtaksperiode.Companion.sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode
import no.nav.helse.person.Vedtaksperiode.Companion.startdatoerPåSammenhengendeVedtaksperioder
import no.nav.helse.person.Vedtaksperiode.Companion.validerTilstand
import no.nav.helse.person.Vedtaksperiode.Companion.venter
import no.nav.helse.person.Yrkesaktivitet.Companion.tilYrkesaktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.builders.UtbetalingsdagerBuilder
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.beløpstidslinje
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.refusjonsopplysninger
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.refusjon.Refusjonsservitør
import no.nav.helse.person.refusjon.Suppekjøkken
import no.nav.helse.person.view.ArbeidsgiverView
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Skjæringstidspunkt
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.utbetalingslinjer.Arbeidsgiverferiepengegrunnlag
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling.Companion.gjelderFeriepengeutbetaling
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.grunnlagForFeriepenger
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.tillaterOpprettelseAvUtbetaling
import no.nav.helse.utbetalingslinjer.UtbetalingObserver
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiodeberegner
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperioderesultat
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperioderesultat.Companion.finn
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiodeteller
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Prosentdel.Companion.prosent

internal class Arbeidsgiver private constructor(
    private val person: Person,
    private val organisasjonsnummer: String,
    private val id: UUID,
    private val inntektshistorikk: Inntektshistorikk,
    private val sykdomshistorikk: Sykdomshistorikk,
    private val sykmeldingsperioder: Sykmeldingsperioder,
    private val vedtaksperioder: MutableList<Vedtaksperiode>,
    private val forkastede: MutableList<ForkastetVedtaksperiode>,
    private val utbetalinger: MutableList<Utbetaling>,
    private val feriepengeutbetalinger: MutableList<Feriepengeutbetaling>,
    private val refusjonshistorikk: Refusjonshistorikk,
    private val yrkesaktivitet: Yrkesaktivitet,
    private val subsumsjonslogg: Subsumsjonslogg
) : Aktivitetskontekst, UtbetalingObserver {
    internal constructor(person: Person, yrkesaktivitet: Yrkesaktivitet, subsumsjonslogg: Subsumsjonslogg) : this(
        person = person,
        organisasjonsnummer = yrkesaktivitet.identifikator(),
        id = UUID.randomUUID(),
        inntektshistorikk = Inntektshistorikk(),
        sykdomshistorikk = Sykdomshistorikk(),
        sykmeldingsperioder = Sykmeldingsperioder(),
        vedtaksperioder = mutableListOf(),
        forkastede = mutableListOf(),
        utbetalinger = mutableListOf(),
        feriepengeutbetalinger = mutableListOf(),
        refusjonshistorikk = Refusjonshistorikk(),
        yrkesaktivitet = yrkesaktivitet,
        subsumsjonslogg = subsumsjonslogg
    )

    init {
        utbetalinger.forEach { it.registrer(this) }
    }

    fun view(): ArbeidsgiverView = ArbeidsgiverView(
        organisasjonsnummer = organisasjonsnummer,
        sykdomshistorikk = sykdomshistorikk.view(),
        utbetalinger = utbetalinger.map { it.view },
        inntektshistorikk = inntektshistorikk.view(),
        sykmeldingsperioder = sykmeldingsperioder.view(),
        refusjonshistorikk = refusjonshistorikk.view(),
        feriepengeutbetalinger = feriepengeutbetalinger.map { it.view() },
        aktiveVedtaksperioder = vedtaksperioder.map { it.view() },
        forkastetVedtaksperioder = forkastede.map { it.view() }
    )

    internal companion object {
        internal fun List<Arbeidsgiver>.finn(yrkesaktivitet: Yrkesaktivitet) = find { it.erSammeYrkesaktivitet(yrkesaktivitet) }

        internal fun List<Arbeidsgiver>.tidligsteDato(): LocalDate {
            return mapNotNull { it.sykdomstidslinje().periode()?.start }.minOrNull() ?: LocalDate.now()
        }

        internal fun List<Arbeidsgiver>.igangsettOverstyring(revurdering: Revurderingseventyr, aktivitetslogg: IAktivitetslogg) {
            forEach { arbeidsgiver ->
                arbeidsgiver.looper {
                    it.igangsettOverstyring(revurdering, aktivitetslogg)
                }
            }
        }

        internal fun List<Arbeidsgiver>.venter(nestemann: Vedtaksperiode) {
            forEach { arbeidsgiver ->
                arbeidsgiver.vedtaksperioder.venter(nestemann)
            }
        }

        internal fun List<Arbeidsgiver>.beregnSkjæringstidspunkt(infotrygdhistorikk: Infotrygdhistorikk):() -> Skjæringstidspunkt = {
            infotrygdhistorikk.skjæringstidspunkt(map(Arbeidsgiver::sykdomstidslinje))
        }

        internal fun List<Arbeidsgiver>.beregnSkjæringstidspunkter(infotrygdhistorikk: Infotrygdhistorikk) {
            forEach { it.vedtaksperioder.beregnSkjæringstidspunkter(beregnSkjæringstidspunkt(infotrygdhistorikk), it.beregnArbeidsgiverperiode(Subsumsjonslogg.EmptyLog)) }
        }

        internal fun List<Arbeidsgiver>.aktiveSkjæringstidspunkter(): Set<LocalDate> {
            return flatMap { it.vedtaksperioder }.aktiveSkjæringstidspunkter()
        }

        internal fun List<Arbeidsgiver>.håndterHistorikkFraInfotrygd(
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            forEach { arbeidsgiver ->
                arbeidsgiver.håndterHistorikkFraInfotrygd(hendelse, aktivitetslogg, infotrygdhistorikk)
            }
        }

        internal fun List<Arbeidsgiver>.håndter(overstyrInntektsgrunnlag: OverstyrInntektsgrunnlag, aktivitetslogg: IAktivitetslogg) =
            any { it.håndter(overstyrInntektsgrunnlag, aktivitetslogg) }

        internal fun List<Arbeidsgiver>.håndterOverstyringAvRefusjon(hendelse: OverstyrArbeidsgiveropplysninger, aktivitetslogg: IAktivitetslogg) {
            forEach { arbeidsgiver ->
                val vedtaksperioderPåSkjæringstidspunkt = arbeidsgiver.vedtaksperioder.filter(MED_SKJÆRINGSTIDSPUNKT(hendelse.skjæringstidspunkt))
                val refusjonstidslinje = vedtaksperioderPåSkjæringstidspunkt.refusjonstidslinje()
                val startdatoer = vedtaksperioderPåSkjæringstidspunkt.startdatoerPåSammenhengendeVedtaksperioder()
                val servitør = hendelse.refusjonsservitør(startdatoer, arbeidsgiver.organisasjonsnummer, refusjonstidslinje) ?: return@forEach
                arbeidsgiver.håndter(hendelse, aktivitetslogg, servitør)
            }
        }

        internal fun Iterable<Arbeidsgiver>.nåværendeVedtaksperioder(filter: VedtaksperiodeFilter) =
            mapNotNull { it.vedtaksperioder.nåværendeVedtaksperiode(filter) }

        internal fun Iterable<Arbeidsgiver>.vedtaksperioder(filter: VedtaksperiodeFilter) =
            map { it.vedtaksperioder.filter(filter) }.flatten()

        internal fun Iterable<Arbeidsgiver>.førsteFraværsdager(skjæringstidspunkt: LocalDate) =
            mapNotNull { arbeidsgiver ->
                val førsteFraværsdag = arbeidsgiver.finnFørsteFraværsdag(skjæringstidspunkt) ?: return@mapNotNull null
                PersonObserver.FørsteFraværsdag(arbeidsgiver.organisasjonsnummer, førsteFraværsdag)
            }

        internal fun List<Arbeidsgiver>.avklarSykepengegrunnlag(aktivitetslogg: IAktivitetslogg, skjæringstidspunkt: LocalDate, skatteopplysninger: Map<String, SkattSykepengegrunnlag>) =
            mapNotNull { arbeidsgiver -> arbeidsgiver.avklarSykepengegrunnlag(skjæringstidspunkt, skatteopplysninger[arbeidsgiver.organisasjonsnummer], aktivitetslogg) }

        internal fun List<Arbeidsgiver>.validerTilstand(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) = forEach { it.vedtaksperioder.validerTilstand(hendelse, aktivitetslogg) }

        internal fun Iterable<Arbeidsgiver>.beregnFeriepengerForAlleArbeidsgivere(
            aktørId: String,
            personidentifikator: Personidentifikator,
            feriepengeberegner: Feriepengeberegner,
            utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger,
            aktivitetslogg: IAktivitetslogg
        ) {
            forEach { it.utbetalFeriepenger(
                aktørId,
                personidentifikator,
                feriepengeberegner,
                utbetalingshistorikkForFeriepenger,
                aktivitetslogg
            ) }
        }


        internal fun Iterable<Arbeidsgiver>.avventerSøknad(periode: Periode) = this
            .any { it.sykmeldingsperioder.avventerSøknad(periode) }

        private fun Iterable<Arbeidsgiver>.sistePeriodeSomHarPågåendeUtbetaling() = vedtaksperioder(HAR_PÅGÅENDE_UTBETALINGER).maxOrNull()
        private fun Iterable<Arbeidsgiver>.harPågåeneUtbetaling() = any { it.utbetalinger.any { utbetaling -> utbetaling.erInFlight() } }
        private fun Iterable<Arbeidsgiver>.førsteAuuSomVilUtbetales() = nåværendeVedtaksperioder(AUU_SOM_VIL_UTBETALES).minOrNull()
        internal fun Iterable<Arbeidsgiver>.gjenopptaBehandling(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
            if (harPågåeneUtbetaling()) return aktivitetslogg.info("Stopper gjenoppta behandling pga. pågående utbetaling")
            val periodeSomSkalGjenopptas = periodeSomSkalGjenopptas() ?: return
            checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas)
            periodeSomSkalGjenopptas.gjenopptaBehandling(hendelse, aktivitetslogg)
        }

        internal fun Iterable<Arbeidsgiver>.nestemann() = sistePeriodeSomHarPågåendeUtbetaling() ?: periodeSomSkalGjenopptas() ?: førsteAuuSomVilUtbetales()

        private fun Iterable<Arbeidsgiver>.periodeSomSkalGjenopptas() = flatMap { it.vedtaksperioder }.nestePeriodeSomSkalGjenopptas()
        private fun Iterable<Arbeidsgiver>.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas: Vedtaksperiode) = flatMap { it.vedtaksperioder }.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas)

        internal fun søppelbøtte(
            arbeidsgivere: List<Arbeidsgiver>,
            hendelse: Hendelse,
            aktivitetslogg: IAktivitetslogg,
            filter: VedtaksperiodeFilter
        ) {
            arbeidsgivere.flatMap { it.søppelbøtte(hendelse, aktivitetslogg, filter) }.forEach { it.buildAndEmit() }
        }

        internal fun gjenopprett(
            person: Person,
            alder: Alder,
            aktørId: String,
            fødselsnummer: String,
            dto: ArbeidsgiverInnDto,
            subsumsjonslogg: Subsumsjonslogg,
            grunnlagsdata: Map<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>
        ): Arbeidsgiver {
            val vedtaksperioder = mutableListOf<Vedtaksperiode>()
            val forkastede = mutableListOf<ForkastetVedtaksperiode>()
            val utbetalinger = dto.utbetalinger.fold(emptyList<Utbetaling>()) { result, utbetaling ->
                result.plusElement(Utbetaling.gjenopprett(utbetaling, result))
            }
            val arbeidsgiver = Arbeidsgiver(
                person = person,
                id = dto.id,
                organisasjonsnummer = dto.organisasjonsnummer,
                inntektshistorikk = Inntektshistorikk.gjenopprett(dto.inntektshistorikk),
                sykdomshistorikk = Sykdomshistorikk.gjenopprett(dto.sykdomshistorikk),
                sykmeldingsperioder = Sykmeldingsperioder.gjenopprett(dto.sykmeldingsperioder),
                vedtaksperioder = vedtaksperioder,
                forkastede = forkastede,
                utbetalinger = utbetalinger.toMutableList(),
                feriepengeutbetalinger = dto.feriepengeutbetalinger.map { Feriepengeutbetaling.gjenopprett(alder, it) }.toMutableList(),
                refusjonshistorikk = Refusjonshistorikk.gjenopprett(dto.refusjonshistorikk),
                yrkesaktivitet = dto.organisasjonsnummer.tilYrkesaktivitet(),
                subsumsjonslogg = subsumsjonslogg
            )
            val utbetalingerMap = utbetalinger.associateBy(Utbetaling::id)
            vedtaksperioder.addAll(dto.vedtaksperioder.map { Vedtaksperiode.gjenopprett(person, aktørId, fødselsnummer, arbeidsgiver, dto.organisasjonsnummer, it, subsumsjonslogg, grunnlagsdata, utbetalingerMap) })
            forkastede.addAll(dto.forkastede.map { ForkastetVedtaksperiode.gjenopprett(person, aktørId, fødselsnummer, arbeidsgiver, dto.organisasjonsnummer, it, subsumsjonslogg, grunnlagsdata, utbetalingerMap) })
            return arbeidsgiver
        }
    }

    private fun erSammeYrkesaktivitet(yrkesaktivitet: Yrkesaktivitet) = this.yrkesaktivitet == yrkesaktivitet

    internal fun refusjonsopplysninger(skjæringstidspunkt: LocalDate) = refusjonshistorikk.refusjonsopplysninger(skjæringstidspunkt)

    internal fun kanBeregneSykepengegrunnlag(skjæringstidspunkt: LocalDate) = avklarSykepengegrunnlag(skjæringstidspunkt) != null

    internal fun avklarSykepengegrunnlag(skjæringstidspunkt: LocalDate, skattSykepengegrunnlag: SkattSykepengegrunnlag? = null, aktivitetslogg: IAktivitetslogg? = null) : ArbeidsgiverInntektsopplysning? {
        val førsteFraværsdag = finnFørsteFraværsdag(skjæringstidspunkt)
        return yrkesaktivitet.avklarSykepengegrunnlag(skjæringstidspunkt, førsteFraværsdag, inntektshistorikk, skattSykepengegrunnlag, refusjonshistorikk, aktivitetslogg)
    }

    internal fun organisasjonsnummer() = organisasjonsnummer

    internal fun utbetaling() = utbetalinger.lastOrNull()

    internal fun grunnlagForFeriepenger() = Arbeidsgiverferiepengegrunnlag(
        orgnummer = organisasjonsnummer,
        utbetalinger = utbetalinger.grunnlagForFeriepenger()
    )

    internal fun lagUtbetaling(
        aktivitetslogg: IAktivitetslogg,
        fødselsnummer: String,
        utbetalingstidslinje: Utbetalingstidslinje,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        periode: Periode
    ) = lagNyUtbetaling(aktivitetslogg, fødselsnummer, utbetalingstidslinje, maksdato, forbrukteSykedager, gjenståendeSykedager, periode, Utbetalingtype.UTBETALING)

    internal fun lagRevurdering(
        aktivitetslogg: IAktivitetslogg,
        fødselsnummer: String,
        utbetalingstidslinje: Utbetalingstidslinje,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        periode: Periode
    ): Utbetaling {
        return lagNyUtbetaling(aktivitetslogg, fødselsnummer, utbetalingstidslinje, maksdato, forbrukteSykedager, gjenståendeSykedager, periode, Utbetalingtype.REVURDERING)
    }

    private fun lagNyUtbetaling(
        aktivitetslogg: IAktivitetslogg,
        fødselsnummer: String,
        utbetalingstidslinje: Utbetalingstidslinje,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        periode: Periode,
        type: Utbetalingtype
    ): Utbetaling {
        val utbetalingsaker = UtbetalingsakerBuilder(
            vedtaksperioder.arbeidsgiverperioder(),
            person.infotrygdhistorikk.betaltePerioder()
        ).lagUtbetalingsaker()

        val (utbetalingen, annulleringer) = Utbetaling.lagUtbetaling(
            utbetalinger = utbetalinger,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            utbetalingstidslinje = utbetalingstidslinje,
            periode = periode,
            utbetalingsaker = utbetalingsaker,
            aktivitetslogg = aktivitetslogg,
            maksdato = maksdato,
            forbrukteSykedager = forbrukteSykedager,
            gjenståendeSykedager = gjenståendeSykedager,
            type = type
        )
        nyUtbetaling(aktivitetslogg, utbetalingen, annulleringer)
        return utbetalingen
    }

    private fun nyUtbetaling(aktivitetslogg: IAktivitetslogg, utbetalingen: Utbetaling, annulleringer: List<Utbetaling> = emptyList()) {
        utbetalinger.lastOrNull()?.forkast(aktivitetslogg)
        annulleringer.plus(utbetalingen).forEach { utbetaling ->
            check(utbetalinger.tillaterOpprettelseAvUtbetaling(utbetaling)) { "Har laget en overlappende utbetaling" }
            utbetalinger.add(utbetaling)
            utbetaling.registrer(this)
            utbetaling.opprett(aktivitetslogg)
        }
    }

    internal fun utbetalFeriepenger(
        aktørId: String,
        personidentifikator: Personidentifikator,
        feriepengeberegner: Feriepengeberegner,
        utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger,
        aktivitetslogg: IAktivitetslogg
    ) {
        aktivitetslogg.kontekst(this)

        val feriepengeutbetaling = Feriepengeutbetaling.Builder(
            aktørId,
            personidentifikator,
            organisasjonsnummer,
            feriepengeberegner,
            utbetalingshistorikkForFeriepenger,
            feriepengeutbetalinger
        ).build(aktivitetslogg)

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            feriepengeutbetalinger.add(feriepengeutbetaling)
            feriepengeutbetaling.overfør(aktivitetslogg)
        }
    }

    internal fun håndter(sykmelding: Sykmelding, aktivitetslogg: IAktivitetslogg) {
        håndter(sykmelding, Vedtaksperiode::håndter)
        yrkesaktivitet.håndter(sykmelding, aktivitetslogg, sykmeldingsperioder)
    }

    internal fun håndter(avbruttSøknad: AvbruttSøknad, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        avbruttSøknad.avbryt(sykmeldingsperioder)
    }

    internal fun håndter(forkastSykmeldingsperioder: ForkastSykmeldingsperioder, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        forkastSykmeldingsperioder.forkast(sykmeldingsperioder)
    }

    internal fun håndter(anmodningOmForkasting: AnmodningOmForkasting, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        håndter(anmodningOmForkasting) {
            håndter(anmodningOmForkasting, aktivitetslogg)
        }
    }

    internal fun vurderOmSøknadIkkeKanHåndteres(aktivitetslogg: IAktivitetslogg, vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Boolean {
        // sjekker først egen arbeidsgiver først
        return yrkesaktivitet.erYrkesaktivitetenIkkeStøttet(aktivitetslogg) || this.harForkastetVedtaksperiodeSomBlokkererBehandling(aktivitetslogg, vedtaksperiode)
                || arbeidsgivere.any { it !== this && it.harForkastetVedtaksperiodeSomBlokkererBehandling(aktivitetslogg, vedtaksperiode) }
                || ForkastetVedtaksperiode.harKortGapTilForkastet(forkastede, aktivitetslogg, vedtaksperiode)
    }

    private fun harForkastetVedtaksperiodeSomBlokkererBehandling(aktivitetslogg: IAktivitetslogg, vedtaksperiode: Vedtaksperiode): Boolean {
        return ForkastetVedtaksperiode.forlengerForkastet(forkastede, aktivitetslogg, vedtaksperiode)
                || ForkastetVedtaksperiode.harOverlappendeForkastetPeriode(forkastede, vedtaksperiode, aktivitetslogg)
                || ForkastetVedtaksperiode.harNyereForkastetPeriode(forkastede, vedtaksperiode, aktivitetslogg)
    }

    internal fun håndter(søknad: Søknad, aktivitetslogg: IAktivitetslogg, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
        aktivitetslogg.kontekst(this)
        søknad.slettSykmeldingsperioderSomDekkes(sykmeldingsperioder)
        opprettVedtaksperiodeOgHåndter(søknad, aktivitetslogg, arbeidsgivere, infotrygdhistorikk)
    }

    private fun opprettVedtaksperiodeOgHåndter(søknad: Søknad, aktivitetslogg: IAktivitetslogg, arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) {
        håndter(søknad) { håndter(søknad, aktivitetslogg, arbeidsgivere, infotrygdhistorikk) }
        if (søknad.noenHarHåndtert() && !aktivitetslogg.harFunksjonelleFeilEllerVerre()) return
        val vedtaksperiode = søknad.lagVedtaksperiode(aktivitetslogg, person, this, subsumsjonslogg)
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) {
            registrerForkastetVedtaksperiode(vedtaksperiode, søknad, aktivitetslogg)
            return
        }
        registrerNyVedtaksperiode(vedtaksperiode)
        vedtaksperiode.håndter(søknad, aktivitetslogg, arbeidsgivere, infotrygdhistorikk)
    }

    internal fun håndter(replays: InntektsmeldingerReplay, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        replays.fortsettÅBehandle(this, aktivitetslogg)
        håndter(replays) { håndter(replays, aktivitetslogg) }
    }

    internal fun håndter(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, vedtaksperiodeId: UUID? = null) {
        aktivitetslogg.kontekst(this)
        if (vedtaksperiodeId != null) aktivitetslogg.info("Replayer inntektsmelding.")
        inntektsmelding.loggOmVedtaksperiodeIdFinnes(vedtaksperioder)
        val dager = inntektsmelding.dager()
        håndter(inntektsmelding) { håndter(dager, aktivitetslogg) }
        håndter(inntektsmelding, aktivitetslogg, inntektsmelding.refusjonsservitør)

        val dagoverstyring = dager.revurderingseventyr()
        val refusjonsoverstyring = vedtaksperioder.refusjonseventyr(inntektsmelding)
        addInntektsmelding(inntektsmelding, aktivitetslogg, Revurderingseventyr.Companion.tidligsteEventyr(dagoverstyring, refusjonsoverstyring))

        inntektsmelding.ikkeHåndert(aktivitetslogg, person, vedtaksperioder, forkastede, sykmeldingsperioder, dager)
    }

    internal fun refusjonstidslinje(vedtaksperiode: Vedtaksperiode): Beløpstidslinje {
        val førsteFraværsdag = finnSammenhengendeVedtaksperioder(vedtaksperiode).periode().start
        val søkevindu = førsteFraværsdag til vedtaksperiode.periode().endInclusive
        return refusjonshistorikk.beløpstidslinje(søkevindu).subset(vedtaksperiode.periode())
    }

    internal fun inntektsmeldingFerdigbehandlet(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        aktivitetslogg.info("Inntektsmelding ferdigbehandlet")
        håndter(hendelse) { inntektsmeldingFerdigbehandlet(hendelse, aktivitetslogg) }
    }

    internal fun håndterHistorikkFraInfotrygd(
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        aktivitetslogg.kontekst(this)
        håndter(hendelse) { håndterHistorikkFraInfotrygd(hendelse, aktivitetslogg, infotrygdhistorikk) }
    }

    internal fun håndter(
        ytelser: Ytelser,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        aktivitetslogg.kontekst(this)
        håndter(ytelser) { håndter(ytelser, aktivitetslogg, infotrygdhistorikk) }
    }

    internal fun håndter(utbetalingsavgjørelse: Behandlingsavgjørelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        utbetalinger.forEach { it.håndter(utbetalingsavgjørelse, aktivitetslogg) }
        håndter(utbetalingsavgjørelse) {
            håndter(utbetalingsavgjørelse, aktivitetslogg)
        }
    }

    internal fun lagreInntekt(sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver) {
        sykepengegrunnlagForArbeidsgiver.lagreInntekt(inntektshistorikk, refusjonshistorikk)
    }

    internal fun håndter(sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        håndter(sykepengegrunnlagForArbeidsgiver) {
            håndter(sykepengegrunnlagForArbeidsgiver, aktivitetslogg)
        }
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        håndter(vilkårsgrunnlag) {
            håndter(vilkårsgrunnlag, aktivitetslogg)
        }
    }

    internal fun håndter(simulering: Simulering, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        utbetalinger.forEach { it.håndter(simulering) }
        håndter(simulering) {
            håndter(simulering, aktivitetslogg)
        }
    }

    internal fun håndter(utbetalingHendelse: UtbetalingHendelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        if (feriepengeutbetalinger.gjelderFeriepengeutbetaling(utbetalingHendelse)) return håndterFeriepengeUtbetaling(utbetalingHendelse, aktivitetslogg)
        håndterUtbetaling(utbetalingHendelse, aktivitetslogg)
    }

    private fun håndterFeriepengeUtbetaling(utbetalingHendelse: UtbetalingHendelse, aktivitetslogg: IAktivitetslogg) {
        feriepengeutbetalinger.forEach { it.håndter(utbetalingHendelse, aktivitetslogg, organisasjonsnummer, person) }
    }

    private fun håndterUtbetaling(utbetaling: UtbetalingHendelse, aktivitetslogg: IAktivitetslogg) {
        utbetalinger.forEach { it.håndter(utbetaling, aktivitetslogg) }
        håndter(utbetaling) {
            håndter(utbetaling, aktivitetslogg)
        }
        person.gjenopptaBehandling(aktivitetslogg)
    }

    internal fun nyAnnullering(hendelse: AnnullerUtbetaling, aktivitetslogg: IAktivitetslogg, utbetalingSomSkalAnnulleres: Utbetaling): Utbetaling? {
        val annullering = utbetalingSomSkalAnnulleres.annuller(hendelse, aktivitetslogg, utbetalinger.toList()) ?: return null
        nyUtbetaling(aktivitetslogg, annullering)
        annullering.håndter(hendelse, aktivitetslogg)
        looper { vedtaksperiode -> vedtaksperiode.nyAnnullering(aktivitetslogg, annullering) }
        return annullering
    }

    internal fun håndter(hendelse: AnnullerUtbetaling, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        aktivitetslogg.info("Håndterer annullering")
        håndter(hendelse) { håndter(it, aktivitetslogg, vedtaksperioder.toList()) }
    }

    internal fun håndter(påminnelse: Utbetalingpåminnelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        utbetalinger.forEach { it.håndter(påminnelse, aktivitetslogg) }
    }

    internal fun håndter(påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg): Boolean {
        aktivitetslogg.kontekst(this)
        return énHarHåndtert(påminnelse) { håndter(it, aktivitetslogg) }
    }

    override fun utbetalingUtbetalt(
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        periode: Periode,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        stønadsdager: Int,
        arbeidsgiverOppdrag: Oppdrag,
        personOppdrag: Oppdrag,
        epost: String,
        tidspunkt: LocalDateTime,
        automatiskBehandling: Boolean,
        utbetalingstidslinje: Utbetalingstidslinje,
        ident: String,
    ) {
        val builder = UtbetalingsdagerBuilder(sykdomshistorikk.sykdomstidslinje(), utbetalingstidslinje)
        person.utbetalingUtbetalt(
            PersonObserver.UtbetalingUtbetaltEvent(
                organisasjonsnummer = organisasjonsnummer,
                utbetalingId = id,
                type = type.name,
                korrelasjonsId = korrelasjonsId,
                fom = periode.start,
                tom = periode.endInclusive,
                maksdato = maksdato,
                forbrukteSykedager = forbrukteSykedager,
                gjenståendeSykedager = gjenståendeSykedager,
                stønadsdager = stønadsdager,
                epost = epost,
                tidspunkt = tidspunkt,
                automatiskBehandling = automatiskBehandling,
                arbeidsgiverOppdrag = PersonObserver.UtbetalingUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(arbeidsgiverOppdrag),
                personOppdrag = PersonObserver.UtbetalingUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(personOppdrag),
                utbetalingsdager = builder.result(),
                ident = ident
            )
        )
    }

    override fun utbetalingUtenUtbetaling(
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        periode: Periode,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        stønadsdager: Int,
        personOppdrag: Oppdrag,
        ident: String,
        arbeidsgiverOppdrag: Oppdrag,
        tidspunkt: LocalDateTime,
        automatiskBehandling: Boolean,
        utbetalingstidslinje: Utbetalingstidslinje,
        epost: String,
    ) {
        val builder = UtbetalingsdagerBuilder(sykdomshistorikk.sykdomstidslinje(), utbetalingstidslinje)
        person.utbetalingUtenUtbetaling(
            PersonObserver.UtbetalingUtbetaltEvent(
                organisasjonsnummer = organisasjonsnummer,
                utbetalingId = id,
                type = type.name,
                fom = periode.start,
                tom = periode.endInclusive,
                maksdato = maksdato,
                forbrukteSykedager = forbrukteSykedager,
                gjenståendeSykedager = gjenståendeSykedager,
                stønadsdager = stønadsdager,
                epost = epost,
                tidspunkt = tidspunkt,
                automatiskBehandling = automatiskBehandling,
                arbeidsgiverOppdrag = PersonObserver.UtbetalingUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(arbeidsgiverOppdrag),
                personOppdrag = PersonObserver.UtbetalingUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(personOppdrag),
                utbetalingsdager = builder.result(),
                ident = ident,
                korrelasjonsId = korrelasjonsId
            )
        )
    }

    override fun utbetalingEndret(
        id: UUID,
        type: Utbetalingtype,
        arbeidsgiverOppdrag: Oppdrag,
        personOppdrag: Oppdrag,
        forrigeTilstand: Utbetalingstatus,
        nesteTilstand: Utbetalingstatus,
        korrelasjonsId: UUID
    ) {
        person.utbetalingEndret(
            PersonObserver.UtbetalingEndretEvent(
                organisasjonsnummer = organisasjonsnummer,
                utbetalingId = id,
                type = type.name,
                forrigeStatus = forrigeTilstand.name,
                gjeldendeStatus = nesteTilstand.name,
                arbeidsgiverOppdrag = OppdragEventDetaljer.mapOppdrag(arbeidsgiverOppdrag),
                personOppdrag = OppdragEventDetaljer.mapOppdrag(personOppdrag),
                korrelasjonsId = korrelasjonsId
            )
        )
    }

    override fun nyVedtaksperiodeUtbetaling(utbetalingId: UUID, vedtaksperiodeId: UUID) {
        person.nyVedtaksperiodeUtbetaling(organisasjonsnummer, utbetalingId, vedtaksperiodeId)
    }

    override fun utbetalingAnnullert(
        id: UUID,
        korrelasjonsId: UUID,
        periode: Periode,
        personFagsystemId: String,
        godkjenttidspunkt: LocalDateTime,
        saksbehandlerEpost: String,
        saksbehandlerIdent: String,
        arbeidsgiverFagsystemId: String
    ) {
        person.annullert(
            PersonObserver.UtbetalingAnnullertEvent(
                organisasjonsnummer = organisasjonsnummer,
                korrelasjonsId = korrelasjonsId,
                arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
                personFagsystemId = personFagsystemId,
                utbetalingId = id,
                fom = periode.start,
                tom = periode.endInclusive,
                annullertAvSaksbehandler = godkjenttidspunkt,
                saksbehandlerEpost = saksbehandlerEpost,
                saksbehandlerIdent = saksbehandlerIdent
            )
        )
    }

    internal fun håndter(hendelse: OverstyrTidslinje, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekst(this)
        håndter(hendelse) {
            håndter(hendelse, aktivitetslogg)
        }
    }

    private fun håndter(overstyrInntektsgrunnlag: OverstyrInntektsgrunnlag, aktivitetslogg: IAktivitetslogg): Boolean {
        aktivitetslogg.kontekst(this)
        return énHarHåndtert(overstyrInntektsgrunnlag) {
            håndter(it, aktivitetslogg)
        }
    }

    internal fun håndter(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, servitør: Refusjonsservitør) {
        håndter(hendelse) {
            håndter(hendelse, aktivitetslogg, servitør)
        }
        servitør.donérRester(Suppekjøkken.LoggendeSuppekjøkken(aktivitetslogg))
    }

    internal fun oppdaterSykdom(hendelse: SykdomshistorikkHendelse, aktivitetslogg: IAktivitetslogg): Sykdomstidslinje {
        return sykdomshistorikk.håndter(hendelse, aktivitetslogg)
    }

    private fun sykdomstidslinje(): Sykdomstidslinje {
        if (!sykdomshistorikk.harSykdom()) return Sykdomstidslinje()
        return sykdomshistorikk.sykdomstidslinje()
    }

    private fun sykdomstidslinjeInkludertForkastet(sykdomstidslinje: Sykdomstidslinje): Sykdomstidslinje {
        return  forkastede
            .slåSammenSykdomstidslinjer(sykdomstidslinje)
            .merge(sykdomstidslinje(), replace)
    }

    private fun arbeidsgiverperiodeFor(sykdomstidslinje: Sykdomstidslinje): List<Arbeidsgiverperioderesultat> {
        val teller = Arbeidsgiverperiodeteller.NormalArbeidstaker
        val arbeidsgiverperiodeberegner = Arbeidsgiverperiodeberegner(teller)
        // hensyntar historikk fra infotrygd, men spleis-tidslinjen overskriver eventuell overlappende info
        val samletTidslinje = person.infotrygdhistorikk
            .sykdomstidslinje(organisasjonsnummer)
            .merge(sykdomstidslinje, replace)
        return arbeidsgiverperiodeberegner.resultat(samletTidslinje, person.infotrygdhistorikk.betaltePerioder(organisasjonsnummer))
    }

    internal fun beregnArbeidsgiverperiode(jurist: Subsumsjonslogg) = { vedtaksperiode: Periode ->
        arbeidsgiverperiodeFor(sykdomstidslinje())
            .finn(vedtaksperiode)
            ?.also { it.subsummering(jurist, sykdomstidslinje().subset(vedtaksperiode)) }
            ?.arbeidsgiverperiode
            ?.grupperSammenhengendePerioder()
            ?: emptyList()
    }

    private fun arbeidsgiverperiode(periode: Periode, sykdomstidslinje: Sykdomstidslinje): Arbeidsgiverperiode? {
        val arbeidsgiverperioder = arbeidsgiverperiodeFor(sykdomstidslinje)
        return arbeidsgiverperioder.finn(periode)?.somArbeidsgiverperiode()
    }
    internal fun arbeidsgiverperiode(periode: Periode) =
        arbeidsgiverperiode(periode, sykdomstidslinje())
    internal fun arbeidsgiverperiodeInkludertForkastet(periode: Periode, sykdomstidslinje: Sykdomstidslinje) =
        arbeidsgiverperiode(periode, sykdomstidslinjeInkludertForkastet(sykdomstidslinje))

    internal fun arbeidsgiverperiodeHensyntattEgenmeldinger(periode: Periode): Arbeidsgiverperiode? {
        val egenmeldingsperioder = vedtaksperioder.egenmeldingsperioder()
        if (egenmeldingsperioder.isEmpty()) return arbeidsgiverperiode(periode)

        val tøyseteKilde = SykdomshistorikkHendelse.Hendelseskilde(Søknad::class, UUID.randomUUID(), LocalDateTime.now())
        val egenmeldingstidslinje = egenmeldingsperioder
            .map { Sykdomstidslinje.arbeidsgiverdager(it.start, it.endInclusive, 100.prosent, tøyseteKilde) }
            .merge()
            .fremTilOgMed(periode.endInclusive)

        val sykdomstidslinjeMedEgenmeldinger = egenmeldingstidslinje.merge(sykdomstidslinje(), replace)
        return arbeidsgiverperiode(periode, sykdomstidslinjeMedEgenmeldinger)
    }

    /**
     * Finner alle vedtaksperioder som tilstøter vedtaksperioden
     * @param vedtaksperiode Perioden vi skal finne alle sammenhengende perioder for. Vi henter alle perioder som
     * tilstøter både foran og bak.
     */
    internal fun finnSammenhengendeVedtaksperioder(vedtaksperiode: Vedtaksperiode): List<Vedtaksperiode> {
        val (perioderFør, perioderEtter) = vedtaksperioder.sorted().partition { it før vedtaksperiode }
        val sammenhengendePerioder = mutableListOf(vedtaksperiode)
        perioderFør.reversed().forEach {
            if (it.erVedtaksperiodeRettFør(sammenhengendePerioder.first()))
                sammenhengendePerioder.add(0, it)
        }
        perioderEtter.forEach {
            if (sammenhengendePerioder.last().erVedtaksperiodeRettFør(it))
                sammenhengendePerioder.add(it)
        }
        return sammenhengendePerioder
    }

    private fun addInntektsmelding(inntektsmelding: Inntektsmelding, aktivitetslogg: IAktivitetslogg, overstyring: Revurderingseventyr?) {
        inntektsmelding.leggTilRefusjon(refusjonshistorikk)

        val subsumsjonsloggMedInntektsmeldingkontekst = subsumsjonsloggMedInntektsmeldingkontekst(inntektsmelding)
        val inntektsdato = inntektsmelding.addInntekt(inntektshistorikk, subsumsjonsloggMedInntektsmeldingkontekst, vedtaksperioder)
        val sykdomstidslinjeperiode = sykdomstidslinje().periode()
        val skjæringstidspunkt = person.beregnSkjæringstidspunkt()().beregnSkjæringstidspunkt(inntektsdato.somPeriode())

        if (!inntektsmelding.skalOppdatereVilkårsgrunnlag(aktivitetslogg, sykdomstidslinjeperiode, forkastede)) {
            aktivitetslogg.info("Inntektsmelding oppdaterer ikke vilkårsgrunnlag")
            if (overstyring == null) return
            return person.igangsettOverstyring(overstyring, aktivitetslogg)
        }

        finnAlternativInntektsdato(inntektsdato, skjæringstidspunkt)?.let {
            inntektsmelding.addInntekt(inntektshistorikk, aktivitetslogg, it)
        }

        igangsettOverstyringOgHåndterInntektPåSkjæringstidspunkt(
            skjæringstidspunkt,
            inntektsmelding,
            aktivitetslogg,
            subsumsjonsloggMedInntektsmeldingkontekst,
            overstyring
        )
    }

    private fun igangsettOverstyringOgHåndterInntektPåSkjæringstidspunkt(
        skjæringstidspunkt: LocalDate,
        inntektsmelding: Inntektsmelding,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonsloggMedInntektsmeldingkontekst: BehandlingSubsumsjonslogg,
        overstyring: Revurderingseventyr?
    ) {
        val inntektoverstyring = person.nyeArbeidsgiverInntektsopplysninger(
            skjæringstidspunkt,
            inntektsmelding,
            aktivitetslogg,
            subsumsjonsloggMedInntektsmeldingkontekst
        )
        val overstyringFraInntektsmelding = Revurderingseventyr.tidligsteEventyr(inntektoverstyring, overstyring)
        if (overstyringFraInntektsmelding != null) person.igangsettOverstyring(overstyringFraInntektsmelding, aktivitetslogg)
        håndter(inntektsmelding) {
            håndtertInntektPåSkjæringstidspunktet(skjæringstidspunkt, inntektsmelding, aktivitetslogg)
        }
    }

    private fun subsumsjonsloggMedInntektsmeldingkontekst(inntektsmelding: Inntektsmelding) =
        BehandlingSubsumsjonslogg(
            subsumsjonslogg, listOf(
                Subsumsjonskontekst(KontekstType.Fødselsnummer, person.personidentifikator.toString()),
                Subsumsjonskontekst(KontekstType.Organisasjonsnummer, organisasjonsnummer)
            ) + inntektsmelding.subsumsjonskontekst()
        )

    internal fun lagreTidsnærInntektsmelding(
        skjæringstidspunkt: LocalDate,
        orgnummer: String,
        inntektsmelding: no.nav.helse.person.inntekt.Inntektsmelding,
        refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger,
        aktivitetslogg: IAktivitetslogg,
        nyArbeidsgiverperiode: Boolean
    ) {
        if (this.organisasjonsnummer != orgnummer) return
        setOfNotNull(finnFørsteFraværsdag(skjæringstidspunkt), skjæringstidspunkt).forEach { dato ->
            inntektsmelding.kopierTidsnærOpplysning(dato, aktivitetslogg, nyArbeidsgiverperiode, inntektshistorikk)
            // TODO: lagre refusjonsopplysninger inni inntektsmelding-opplysningen?
            refusjonsopplysninger.lagreTidsnær(dato, refusjonshistorikk)
        }
    }

    private fun søppelbøtte(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, filter: VedtaksperiodeFilter): List<Vedtaksperiode.VedtaksperiodeForkastetEventBuilder> {
        aktivitetslogg.kontekst(this)
        val perioder: List<Pair<Vedtaksperiode, Vedtaksperiode.VedtaksperiodeForkastetEventBuilder>> = vedtaksperioder
            .filter(filter)
            .mapNotNull { vedtaksperiode -> vedtaksperiode.forkast(hendelse, aktivitetslogg, utbetalinger)?.let { vedtaksperiode to it } }

        vedtaksperioder.removeAll(perioder.map { it.first })
        forkastede.addAll(perioder.map { ForkastetVedtaksperiode(it.first) })
        sykdomshistorikk.fjernDager(perioder.map { it.first.periode() })
        return perioder.map { it.second }
    }

    private fun registrerNyVedtaksperiode(vedtaksperiode: Vedtaksperiode) {
        vedtaksperioder.add(vedtaksperiode)
        vedtaksperioder.sort()
    }

    private fun registrerForkastetVedtaksperiode(vedtaksperiode: Vedtaksperiode, hendelse: SykdomstidslinjeHendelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.info("Oppretter forkastet vedtaksperiode ettersom Søknad inneholder errors")
        val vedtaksperiodeForkastetEventBuilder = vedtaksperiode.forkast(hendelse, aktivitetslogg, utbetalinger)
        vedtaksperiodeForkastetEventBuilder!!.buildAndEmit()
        forkastede.add(ForkastetVedtaksperiode(vedtaksperiode))
    }

    internal fun finnVedtaksperiodeRettFør(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other ->
            other.erVedtaksperiodeRettFør(vedtaksperiode)
        }

    internal fun finnVedtaksperiodeFør(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.indexOf(vedtaksperiode)
            .takeUnless { index -> index == 0 }
            ?.let { vedtaksperioder[it - 1] }

    internal fun finnVedtaksperiodeEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.indexOf(vedtaksperiode)
            .takeUnless { index -> index == vedtaksperioder.lastIndex }
            ?.let { vedtaksperioder[it + 1] }

    internal fun finnVedtaksperiodeRettEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other ->
            vedtaksperiode.erVedtaksperiodeRettFør(other)
        }

    internal fun harIngenSporingTilInntektsmeldingISykefraværet(): Boolean {
        return vedtaksperioder.harIngenSporingTilInntektsmeldingISykefraværet()
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Arbeidsgiver", mapOf("organisasjonsnummer" to organisasjonsnummer))
    }

    internal fun lås(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().lås(periode)
    }
    internal fun låsOpp(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().låsOpp(periode)
    }
    internal fun bekreftErLåst(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().bekreftErLåst(periode)
    }
    internal fun bekreftErÅpen(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().bekreftErÅpen(periode)
    }

    private fun finnFørsteFraværsdag(skjæringstidspunkt: LocalDate): LocalDate? {
        val førstePeriodeMedUtbetaling = vedtaksperioder.firstOrNull(SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG(skjæringstidspunkt))
            ?: vedtaksperioder.firstOrNull(MED_SKJÆRINGSTIDSPUNKT(skjæringstidspunkt))
            ?: return null
        return sykdomstidslinje().sisteSkjæringstidspunkt(førstePeriodeMedUtbetaling.periode())
    }

    private fun finnAlternativInntektsdato(inntektsdato: LocalDate, skjæringstidspunkt: LocalDate): LocalDate? {
        if (inntektsdato <= skjæringstidspunkt) return null
        return sykdomstidslinje().sisteSkjæringstidspunkt(inntektsdato.somPeriode())?.takeUnless { it == inntektsdato }
    }

    private fun <Hendelsetype : Hendelse> håndter(hendelse: Hendelsetype, håndterer: Vedtaksperiode.(Hendelsetype) -> Unit) {
        looper { håndterer(it, hendelse) }
    }

    private fun <Hendelsetype : Hendelse> énHarHåndtert(hendelse: Hendelsetype, håndterer: Vedtaksperiode.(Hendelsetype) -> Boolean): Boolean {
        var håndtert = false
        looper { håndtert = håndtert || håndterer(it, hendelse) }
        return håndtert
    }

    private fun <Hendelsetype : Hendelse> noenHarHåndtert(hendelse: Hendelsetype, håndterer: Vedtaksperiode.(Hendelsetype) -> Boolean): Boolean {
        var håndtert = false
        looper { håndtert = håndterer(it, hendelse) || håndtert }
        return håndtert
    }

    // støtter å loope over vedtaksperioder som modifiseres pga. forkasting.
    // dvs. vi stopper å iterere så snart listen har endret seg
    private fun looper(handler: (Vedtaksperiode) -> Unit) {
        val size = vedtaksperioder.size
        var neste = 0
        while (size == vedtaksperioder.size && neste < size) {
            handler(vedtaksperioder[neste])
            neste += 1
        }
    }

    internal fun kanForkastes(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) =
        vedtaksperiode.kanForkastes(utbetalinger, aktivitetslogg)

    fun vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode): List<Vedtaksperiode> {
        return vedtaksperioder.filter { it.periode() in arbeidsgiverperiode }
    }

    internal fun sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperioder.sendOppdatertForespørselOmArbeidsgiveropplysningerForNestePeriode(vedtaksperiode, aktivitetslogg)
    }

    fun vedtaksperioderKnyttetTilArbeidsgiverperiodeInkludertForkastede(arbeidsgiverperiode: Arbeidsgiverperiode?): List<Vedtaksperiode> {
        if (arbeidsgiverperiode == null) return emptyList()
        return ForkastetVedtaksperiode.hørerTilArbeidsgiverperiode(forkastede, vedtaksperioder, arbeidsgiverperiode)
    }

    internal fun vedtaksperioderEtter(dato: LocalDate) = vedtaksperioder.filter { it.slutterEtter(dato) }

    internal fun dto(nestemann: Vedtaksperiode?) = ArbeidsgiverUtDto(
        id = id,
        organisasjonsnummer = organisasjonsnummer,
        inntektshistorikk = inntektshistorikk.dto(),
        sykdomshistorikk = sykdomshistorikk.dto(),
        sykmeldingsperioder = sykmeldingsperioder.dto(),
        vedtaksperioder = vedtaksperioder.map { it.dto(nestemann) },
        forkastede = forkastede.map { it.dto() },
        utbetalinger = utbetalinger.map { it.dto() },
        feriepengeutbetalinger = feriepengeutbetalinger.map { it.dto() },
        refusjonshistorikk = refusjonshistorikk.dto()
    )
}