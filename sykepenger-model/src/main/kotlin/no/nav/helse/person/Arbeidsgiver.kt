package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.Toggle
import no.nav.helse.dto.deserialisering.ArbeidsgiverInnDto
import no.nav.helse.dto.serialisering.ArbeidsgiverUtDto
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingReplay
import no.nav.helse.hendelser.InntektsmeldingReplayUtført
import no.nav.helse.hendelser.OverstyrSykepengegrunnlag
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingpåminnelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.slåSammenSykdomstidslinjer
import no.nav.helse.person.PersonObserver.UtbetalingEndretEvent.OppdragEventDetaljer
import no.nav.helse.person.Vedtaksperiode.Companion.AUU_SOM_VIL_UTBETALES
import no.nav.helse.person.Vedtaksperiode.Companion.AuuGruppering.Companion.auuGruppering
import no.nav.helse.person.Vedtaksperiode.Companion.HAR_PÅGÅENDE_UTBETALINGER
import no.nav.helse.person.Vedtaksperiode.Companion.MED_SKJÆRINGSTIDSPUNKT
import no.nav.helse.person.Vedtaksperiode.Companion.SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG
import no.nav.helse.person.Vedtaksperiode.Companion.TRENGER_REFUSJONSOPPLYSNINGER
import no.nav.helse.person.Vedtaksperiode.Companion.aktiveSkjæringstidspunkter
import no.nav.helse.person.Vedtaksperiode.Companion.checkBareEnPeriodeTilGodkjenningSamtidig
import no.nav.helse.person.Vedtaksperiode.Companion.finnNesteVedtaksperiodeSomTrengerInntektsmelding
import no.nav.helse.person.Vedtaksperiode.Companion.finnVedtaksperioderKnyttetTilSkjæringstidspunkt
import no.nav.helse.person.Vedtaksperiode.Companion.iderMedUtbetaling
import no.nav.helse.person.Vedtaksperiode.Companion.nestePeriodeSomSkalGjenopptas
import no.nav.helse.person.Vedtaksperiode.Companion.nåværendeVedtaksperiode
import no.nav.helse.person.Vedtaksperiode.Companion.trengerInntektsmelding
import no.nav.helse.person.Vedtaksperiode.Companion.venter
import no.nav.helse.person.Yrkesaktivitet.Companion.tilYrkesaktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.builders.UtbetalingsdagerBuilder
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.refusjonsopplysninger
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling.Companion.gjelderFeriepengeutbetaling
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.tillaterOpprettelseAvUtbetaling
import no.nav.helse.utbetalingslinjer.UtbetalingObserver
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverUtbetalinger
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode.Companion.finn
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import no.nav.helse.utbetalingstidslinje.Inntekter
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjeBuilder

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
    private val jurist: MaskinellJurist
) : Aktivitetskontekst, UtbetalingObserver {
    internal constructor(person: Person, yrkesaktivitet: Yrkesaktivitet, jurist: MaskinellJurist) : this(
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
        jurist = yrkesaktivitet.jurist(jurist)
    )

    init {
        utbetalinger.forEach { it.registrer(this) }
    }

    internal companion object {
        internal fun List<Arbeidsgiver>.finn(yrkesaktivitet: Yrkesaktivitet) = find { it.erSammeYrkesaktivitet(yrkesaktivitet) }

        internal fun List<Arbeidsgiver>.tidligsteDato(): LocalDate {
            return mapNotNull { it.sykdomstidslinje().periode()?.start }.minOrNull() ?: LocalDate.now()
        }

        internal fun List<Arbeidsgiver>.forkastAuu(hendelse: Hendelse, auu: Vedtaksperiode, infotrygdhistorikk: Infotrygdhistorikk) {
            val alleVedtaksperioder = flatMap { it.vedtaksperioder }
            alleVedtaksperioder.auuGruppering(auu, infotrygdhistorikk)?.forkast(hendelse, alleVedtaksperioder)
        }

        internal fun List<Arbeidsgiver>.igangsettOverstyring(revurdering: Revurderingseventyr) {
            forEach { arbeidsgiver ->
                arbeidsgiver.håndter(revurdering) { igangsettOverstyring(revurdering) }
            }
        }

        internal fun List<Arbeidsgiver>.venter(nestemann: Vedtaksperiode) {
            forEach { arbeidsgiver ->
                arbeidsgiver.vedtaksperioder.venter(nestemann)
            }
        }

        internal fun List<Arbeidsgiver>.aktiveSkjæringstidspunkter(): Set<LocalDate> {
            return flatMap { it.vedtaksperioder }.aktiveSkjæringstidspunkter()
        }

        internal fun List<Arbeidsgiver>.håndterHistorikkFraInfotrygd(
            hendelse: Hendelse,
            infotrygdhistorikk: Infotrygdhistorikk
        ) {
            forEach { arbeidsgiver ->
                arbeidsgiver.håndterHistorikkFraInfotrygd(hendelse, infotrygdhistorikk)
            }
        }

        internal fun List<Arbeidsgiver>.håndter(overstyrSykepengegrunnlag: OverstyrSykepengegrunnlag) =
            any { it.håndter(overstyrSykepengegrunnlag) }

        internal fun Iterable<Arbeidsgiver>.nåværendeVedtaksperioder(filter: VedtaksperiodeFilter) =
            mapNotNull { it.vedtaksperioder.nåværendeVedtaksperiode(filter) }

        internal fun Iterable<Arbeidsgiver>.vedtaksperioder(filter: VedtaksperiodeFilter) =
            map { it.vedtaksperioder.filter(filter) }.flatten()

        internal fun Iterable<Arbeidsgiver>.førsteFraværsdager(skjæringstidspunkt: LocalDate) =
            mapNotNull { arbeidsgiver ->
                val førsteFraværsdag = arbeidsgiver.finnFørsteFraværsdag(skjæringstidspunkt)
                if (førsteFraværsdag != null) return@mapNotNull mapOf(
                    "organisasjonsnummer" to arbeidsgiver.organisasjonsnummer,
                    "førsteFraværsdag" to førsteFraværsdag
                )
                null
            }

        internal fun List<Arbeidsgiver>.avklarSykepengegrunnlag(aktivitetslogg: IAktivitetslogg, skjæringstidspunkt: LocalDate, skatteopplysninger: Map<String, SkattSykepengegrunnlag>) =
            mapNotNull { arbeidsgiver -> arbeidsgiver.avklarSykepengegrunnlag(skjæringstidspunkt, skatteopplysninger[arbeidsgiver.organisasjonsnummer], aktivitetslogg) }

        internal fun skjæringstidspunkt(arbeidsgivere: List<Arbeidsgiver>, periode: Periode, infotrygdhistorikk: Infotrygdhistorikk) =
            infotrygdhistorikk.skjæringstidspunkt(periode, arbeidsgivere.map(Arbeidsgiver::sykdomstidslinje))

        internal fun skjæringstidspunkter(arbeidsgivere: List<Arbeidsgiver>, infotrygdhistorikk: Infotrygdhistorikk) =
            infotrygdhistorikk.skjæringstidspunkter(arbeidsgivere.map(Arbeidsgiver::sykdomstidslinje))

        internal fun skjæringstidspunkt(arbeidsgivere: List<Arbeidsgiver>, arbeidsgiver: Arbeidsgiver, sykdomstidslinje: Sykdomstidslinje, periode: Periode, infotrygdhistorikk: Infotrygdhistorikk): LocalDate {
            // Bruker sykdomstidslinjen fra alle arbeidsgivere, med unntak av den ene som det sendes inn en sykdomstidslinje for 🫠
            val sykdomstidslinjer = arbeidsgivere
                .filterNot { it.organisasjonsnummer == arbeidsgiver.organisasjonsnummer }
                .map { it.sykdomstidslinje() }
                .toMutableList()
                .also { it.add(sykdomstidslinje) }
                .toList()
            return infotrygdhistorikk.skjæringstidspunkt(periode, sykdomstidslinjer)
        }

        internal fun Iterable<Arbeidsgiver>.validerVilkårsgrunnlag(
            aktivitetslogg: IAktivitetslogg,
            vilkårsgrunnlag: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
            organisasjonsnummer: String,
            skjæringstidspunkt: LocalDate
        ) {
            val relevanteArbeidsgivere = medSkjæringstidspunkt(skjæringstidspunkt).map { it.organisasjonsnummer }
            vilkårsgrunnlag.valider(aktivitetslogg, organisasjonsnummer, relevanteArbeidsgivere)
        }

        internal fun Iterable<Arbeidsgiver>.beregnFeriepengerForAlleArbeidsgivere(
            aktørId: String,
            personidentifikator: Personidentifikator,
            feriepengeberegner: Feriepengeberegner,
            utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger
        ) {
            forEach { it.utbetalFeriepenger(
                aktørId,
                personidentifikator,
                feriepengeberegner,
                utbetalingshistorikkForFeriepenger
            ) }
        }

        private fun Iterable<Arbeidsgiver>.medSkjæringstidspunkt(skjæringstidspunkt: LocalDate) = this
            .filter { arbeidsgiver -> arbeidsgiver.skalInngåISykepengegrunnlaget(skjæringstidspunkt) }

        internal fun Iterable<Arbeidsgiver>.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag(skjæringstidspunkt: LocalDate) = this
            .any { arbeidsgiver -> arbeidsgiver.manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag(skjæringstidspunkt) }

        /* krever inntekt for alle vedtaksperioder som deler skjæringstidspunkt,
            men tillater at det ikke er inntekt for perioder innenfor arbeidsgiverperioden/uten utbetaling
         */
        internal fun Iterable<Arbeidsgiver>.harNødvendigInntektForVilkårsprøving(skjæringstidspunkt: LocalDate) = this
            .all { arbeidsgiver -> arbeidsgiver.harNødvendigInntektForVilkårsprøving(skjæringstidspunkt) }

        internal fun Iterable<Arbeidsgiver>.trengerInntektsmelding(periode: Periode) = this
            .flatMap { it.vedtaksperioder }
            .filter { it.periode().overlapperMed(periode) }
            .trengerInntektsmelding()
            .isNotEmpty()

        internal fun Iterable<Arbeidsgiver>.avventerSøknad(periode: Periode) = this
            .any { it.sykmeldingsperioder.avventerSøknad(periode) }

        private fun Iterable<Arbeidsgiver>.sistePeriodeSomHarPågåendeUtbetaling() = vedtaksperioder(HAR_PÅGÅENDE_UTBETALINGER).maxOrNull()
        private fun Iterable<Arbeidsgiver>.førsteAuuSomVilUtbetales() = nåværendeVedtaksperioder(AUU_SOM_VIL_UTBETALES).minOrNull()
        internal fun Iterable<Arbeidsgiver>.gjenopptaBehandling(hendelse: Hendelse) {
            if (sistePeriodeSomHarPågåendeUtbetaling() != null) return hendelse.info("Stopper gjenoppta behandling pga. pågående utbetaling")
            val periodeSomSkalGjenopptas = periodeSomSkalGjenopptas() ?: return
            checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas)
            periodeSomSkalGjenopptas.gjenopptaBehandling(hendelse, this)
        }

        internal fun Iterable<Arbeidsgiver>.nestemann() = sistePeriodeSomHarPågåendeUtbetaling() ?: periodeSomSkalGjenopptas() ?: førsteAuuSomVilUtbetales()

        private fun Iterable<Arbeidsgiver>.periodeSomSkalGjenopptas() = flatMap { it.vedtaksperioder }.nestePeriodeSomSkalGjenopptas()
        private fun Iterable<Arbeidsgiver>.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas: Vedtaksperiode) = flatMap { it.vedtaksperioder }.checkBareEnPeriodeTilGodkjenningSamtidig(periodeSomSkalGjenopptas)

        internal fun søppelbøtte(
            arbeidsgivere: List<Arbeidsgiver>,
            hendelse: Hendelse,
            filter: VedtaksperiodeFilter
        ) {
            arbeidsgivere.flatMap { it.søppelbøtte(hendelse, filter) }.forEach { it.buildAndEmit() }
        }

        internal fun gjenopprett(
            person: Person,
            alder: Alder,
            aktørId: String,
            fødselsnummer: String,
            dto: ArbeidsgiverInnDto,
            personJurist: MaskinellJurist,
            grunnlagsdata: Map<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>
        ): Arbeidsgiver {
            val arbeidsgiverJurist = personJurist.medOrganisasjonsnummer(dto.organisasjonsnummer)
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
                jurist = arbeidsgiverJurist
            )
            val utbetalingerMap = utbetalinger.associateBy(Utbetaling::id)
            vedtaksperioder.addAll(dto.vedtaksperioder.map { Vedtaksperiode.gjenopprett(person, aktørId, fødselsnummer, arbeidsgiver, dto.organisasjonsnummer, it, arbeidsgiverJurist, grunnlagsdata, utbetalingerMap) })
            forkastede.addAll(dto.forkastede.map { ForkastetVedtaksperiode.gjenopprett(person, aktørId, fødselsnummer, arbeidsgiver, dto.organisasjonsnummer, it, arbeidsgiverJurist, grunnlagsdata, utbetalingerMap) })
            return arbeidsgiver
        }
    }

    private fun erSammeYrkesaktivitet(yrkesaktivitet: Yrkesaktivitet) = this.yrkesaktivitet == yrkesaktivitet

    /* hvorvidt arbeidsgiver ikke inngår i sykepengegrunnlaget som er på et vilkårsgrunnlag,
        for eksempel i saker hvor man var syk på én arbeidsgiver på skjæringstidspunktet, også blir man
        etterhvert syk fra ny arbeidsgiver (f.eks. jobb-bytte)
     */
    internal fun manglerNødvendigInntektVedTidligereBeregnetSykepengegrunnlag(skjæringstidspunkt: LocalDate) =
        harNødvendigInntektITidligereBeregnetSykepengegrunnlag(skjæringstidspunkt) == false && skalInngåISykepengegrunnlaget(skjæringstidspunkt)

    internal fun harNødvendigInntektForVilkårsprøving(skjæringstidspunkt: LocalDate) : Boolean {
        if (!skalInngåISykepengegrunnlaget(skjæringstidspunkt)) return true
        return harNødvendigInntektITidligereBeregnetSykepengegrunnlag(skjæringstidspunkt) ?: kanBeregneSykepengegrunnlag(skjæringstidspunkt)
    }

    private fun skalInngåISykepengegrunnlaget(skjæringstidspunkt: LocalDate) =
        vedtaksperioder.any(SKAL_INNGÅ_I_SYKEPENGEGRUNNLAG(skjæringstidspunkt))

    private fun harNødvendigRefusjonsopplysninger(skjæringstidspunkt: LocalDate, periode: Periode, hendelse: IAktivitetslogg) : Boolean {
        if (!trengerRefusjonsopplysninger(skjæringstidspunkt, periode)) return true
        val arbeidsgiverperiode = arbeidsgiverperiode(periode) ?: return false
        val refusjonsopplysninger = when (val vilkårsgrunnlag = person.vilkårsgrunnlagFor(skjæringstidspunkt)) {
            null -> refusjonshistorikk.refusjonsopplysninger(skjæringstidspunkt)
            else -> vilkårsgrunnlag.refusjonsopplysninger(organisasjonsnummer)
        }
        return Arbeidsgiverperiode.harNødvendigeRefusjonsopplysninger(skjæringstidspunkt, periode, refusjonsopplysninger, arbeidsgiverperiode, hendelse, organisasjonsnummer)
    }

    private fun trengerRefusjonsopplysninger(skjæringstidspunkt: LocalDate, periode: Periode) =
        vedtaksperioder.any(TRENGER_REFUSJONSOPPLYSNINGER(skjæringstidspunkt, periode))

    internal fun harTilstrekkeligInformasjonTilUtbetaling(skjæringstidspunkt: LocalDate, periode: Periode, hendelse: IAktivitetslogg): Boolean {
        val harNødvendigInntektForVilkårsprøving = harNødvendigInntektForVilkårsprøving(skjæringstidspunkt)
        if (!harNødvendigInntektForVilkårsprøving) hendelse.info("Mangler inntekt for vilkårsprøving på $skjæringstidspunkt for $organisasjonsnummer")
        return harNødvendigInntektForVilkårsprøving && harNødvendigRefusjonsopplysninger(skjæringstidspunkt, periode, hendelse)
    }

    private fun harNødvendigInntektITidligereBeregnetSykepengegrunnlag(skjæringstidspunkt: LocalDate) =
        person.vilkårsgrunnlagFor(skjæringstidspunkt)?.harNødvendigInntektForVilkårsprøving(organisasjonsnummer)

    private fun kanBeregneSykepengegrunnlag(skjæringstidspunkt: LocalDate) = avklarSykepengegrunnlag(skjæringstidspunkt) != null

    private fun avklarSykepengegrunnlag(skjæringstidspunkt: LocalDate, skattSykepengegrunnlag: SkattSykepengegrunnlag? = null, aktivitetslogg: IAktivitetslogg? = null) : ArbeidsgiverInntektsopplysning? {
        val førsteFraværsdag = finnFørsteFraværsdag(skjæringstidspunkt)
        return yrkesaktivitet.avklarSykepengegrunnlag(skjæringstidspunkt, førsteFraværsdag, inntektshistorikk, skattSykepengegrunnlag, refusjonshistorikk, aktivitetslogg)
    }

    internal fun accept(visitor: ArbeidsgiverVisitor) {
        visitor.preVisitArbeidsgiver(this, id, organisasjonsnummer)
        inntektshistorikk.accept(visitor)
        sykdomshistorikk.accept(visitor)
        sykmeldingsperioder.accept(visitor)
        visitor.preVisitUtbetalinger(utbetalinger)
        utbetalinger.forEach { it.accept(visitor) }
        visitor.postVisitUtbetalinger(utbetalinger)
        visitor.preVisitPerioder(vedtaksperioder)
        vedtaksperioder.forEach { it.accept(visitor) }
        visitor.postVisitPerioder(vedtaksperioder)
        visitor.preVisitForkastedePerioder(forkastede)
        forkastede.forEach { it.accept(visitor) }
        visitor.postVisitForkastedePerioder(forkastede)
        visitor.preVisitFeriepengeutbetalinger(feriepengeutbetalinger)
        feriepengeutbetalinger.forEach { it.accept(visitor) }
        visitor.postVisitFeriepengeutbetalinger(feriepengeutbetalinger)
        refusjonshistorikk.accept(visitor)
        visitor.postVisitArbeidsgiver(this, id, organisasjonsnummer)
    }

    internal fun organisasjonsnummer() = organisasjonsnummer

    internal fun utbetaling() = utbetalinger.lastOrNull()

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
        val (utbetalingen, annulleringer) = Utbetaling.lagUtbetaling(
            utbetalinger = utbetalinger,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            utbetalingstidslinje = utbetalingstidslinje,
            periode = periode,
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
        utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger
    ) {
        utbetalingshistorikkForFeriepenger.kontekst(this)

        val feriepengeutbetaling = Feriepengeutbetaling.Builder(
            aktørId,
            personidentifikator,
            organisasjonsnummer,
            feriepengeberegner,
            utbetalingshistorikkForFeriepenger,
            feriepengeutbetalinger
        ).build(utbetalingshistorikkForFeriepenger)

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            feriepengeutbetalinger.add(feriepengeutbetaling)
            feriepengeutbetaling.overfør(utbetalingshistorikkForFeriepenger)
        }
    }

    internal fun håndter(sykmelding: Sykmelding) {
        håndter(sykmelding, Vedtaksperiode::håndter)
        sykmeldingsperioder.lagre(sykmelding)
    }

    internal fun håndter(forkastSykmeldingsperioder: ForkastSykmeldingsperioder) {
        forkastSykmeldingsperioder.kontekst(this)
        forkastSykmeldingsperioder.forkast(sykmeldingsperioder)
    }

    internal fun håndter(anmodningOmForkasting: AnmodningOmForkasting) {
        anmodningOmForkasting.kontekst(this)
        håndter(anmodningOmForkasting, Vedtaksperiode::håndter)
    }

    internal fun vurderOmSøknadIkkeKanHåndteres(hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode, arbeidsgivere: List<Arbeidsgiver>): Boolean {
        // sjekker først egen arbeidsgiver først
        return yrkesaktivitet.erYrkesaktivitetenIkkeStøttet(hendelse) || this.harForkastetVedtaksperiodeSomBlokkererBehandling(hendelse, vedtaksperiode)
                || arbeidsgivere.any { it !== this && it.harForkastetVedtaksperiodeSomBlokkererBehandling(hendelse, vedtaksperiode) }
                || ForkastetVedtaksperiode.harKortGapTilForkastet(forkastede, hendelse, vedtaksperiode)
    }

    private fun harForkastetVedtaksperiodeSomBlokkererBehandling(hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode): Boolean {
        return ForkastetVedtaksperiode.forlengerForkastet(forkastede, hendelse, vedtaksperiode)
                || ForkastetVedtaksperiode.harOverlappendeForkastetPeriode(forkastede, vedtaksperiode, hendelse)
                || ForkastetVedtaksperiode.harNyereForkastetPeriode(forkastede, vedtaksperiode, hendelse)
    }

    internal fun håndter(søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
        søknad.kontekst(this)
        søknad.slettSykmeldingsperioderSomDekkes(sykmeldingsperioder)
        opprettVedtaksperiodeOgHåndter(søknad, arbeidsgivere)
    }

    private fun opprettVedtaksperiodeOgHåndter(søknad: Søknad, arbeidsgivere: List<Arbeidsgiver>) {
        håndter(søknad) { håndter(søknad, arbeidsgivere) }
        if (søknad.noenHarHåndtert() && !søknad.harFunksjonelleFeilEllerVerre()) return
        val vedtaksperiode = søknad.lagVedtaksperiode(person, this, jurist)
        if (søknad.harFunksjonelleFeilEllerVerre()) {
            registrerForkastetVedtaksperiode(vedtaksperiode, søknad)
            return
        }
        registrerNyVedtaksperiode(vedtaksperiode)
        vedtaksperiode.håndter(søknad, arbeidsgivere)
    }

    internal fun håndter(inntektsmelding: Inntektsmelding, vedtaksperiodeId: UUID? = null) {
        inntektsmelding.kontekst(this)
        if (vedtaksperiodeId != null) inntektsmelding.info("Replayer inntektsmelding.")
        val dager = inntektsmelding.dager()
        håndter(inntektsmelding) { håndter(dager) }

        addInntektsmelding(inntektsmelding)

        inntektsmelding.ikkeHåndert(person, vedtaksperioder, sykmeldingsperioder, dager)
    }

    internal fun håndter(inntektsmelding: InntektsmeldingReplay) {
        inntektsmelding.fortsettÅBehandle(this)
    }

    internal fun håndter(inntektsmeldingReplayUtført: InntektsmeldingReplayUtført) {
        inntektsmeldingReplayUtført.kontekst(this)
        håndter(inntektsmeldingReplayUtført) { håndter(inntektsmeldingReplayUtført) }
    }

    internal fun håndterHistorikkFraInfotrygd(
        hendelse: Hendelse,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        hendelse.kontekst(this)
        håndter(hendelse) { håndterHistorikkFraInfotrygd(hendelse, infotrygdhistorikk) }
    }

    internal fun håndter(
        ytelser: Ytelser,
        infotrygdhistorikk: Infotrygdhistorikk,
        arbeidsgiverUtbetalinger: ArbeidsgiverUtbetalinger
    ) {
        ytelser.kontekst(this)
        håndter(ytelser) { håndter(ytelser, infotrygdhistorikk, arbeidsgiverUtbetalinger) }
    }

    internal fun håndter(utbetalingsavgjørelse: Utbetalingsavgjørelse) {
        utbetalingsavgjørelse.kontekst(this)
        utbetalinger.forEach { it.håndter(utbetalingsavgjørelse) }
        håndter(utbetalingsavgjørelse, Vedtaksperiode::håndter)
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        vilkårsgrunnlag.kontekst(this)
        håndter(vilkårsgrunnlag, Vedtaksperiode::håndter)
    }

    internal fun håndter(simulering: Simulering) {
        simulering.kontekst(this)
        utbetalinger.forEach { it.håndter(simulering) }
        håndter(simulering, Vedtaksperiode::håndter)
    }

    internal fun håndter(utbetalingHendelse: UtbetalingHendelse) {
        utbetalingHendelse.kontekst(this)
        if (feriepengeutbetalinger.gjelderFeriepengeutbetaling(utbetalingHendelse)) return håndterFeriepengeUtbetaling(utbetalingHendelse)
        håndterUtbetaling(utbetalingHendelse)
    }

    private fun håndterFeriepengeUtbetaling(utbetalingHendelse: UtbetalingHendelse) {
        feriepengeutbetalinger.forEach { it.håndter(utbetalingHendelse, organisasjonsnummer, person) }
    }

    private fun håndterUtbetaling(utbetaling: UtbetalingHendelse) {
        utbetalinger.forEach { it.håndter(utbetaling) }
        håndter(utbetaling, Vedtaksperiode::håndter)
    }

    internal fun nyAnnullering(hendelse: AnnullerUtbetaling, utbetalingSomSkalAnnulleres: Utbetaling): Utbetaling? {
        val annullering = utbetalingSomSkalAnnulleres.annuller(hendelse, utbetalinger.toList()) ?: return null
        nyUtbetaling(hendelse, annullering)
        annullering.håndter(hendelse)
        return annullering
    }

    internal fun håndter(hendelse: AnnullerUtbetaling) {
        hendelse.kontekst(this)
        hendelse.info("Håndterer annullering")
        håndter(hendelse) { håndter(it, vedtaksperioder.toList()) }
    }

    internal fun håndter(påminnelse: Utbetalingpåminnelse) {
        påminnelse.kontekst(this)
        utbetalinger.forEach { it.håndter(påminnelse) }
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        påminnelse.kontekst(this)
        return énHarHåndtert(påminnelse, Vedtaksperiode::håndter)
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
        val builder = UtbetalingsdagerBuilder(sykdomshistorikk.sykdomstidslinje())
        utbetalingstidslinje.accept(builder)
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
                vedtaksperiodeIder = vedtaksperioder.iderMedUtbetaling(id), // TODO: denne kan slettes når spesialsaker ikke trengs automatiseres mer (eneste avhengighet er sporbar).
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
        val builder = UtbetalingsdagerBuilder(sykdomshistorikk.sykdomstidslinje())
        utbetalingstidslinje.accept(builder)
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
                vedtaksperiodeIder = vedtaksperioder.iderMedUtbetaling(id), // TODO: denne kan slettes når spesialsaker ikke trengs automatiseres mer (eneste avhengighet er sporbar).
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

    internal fun håndter(hendelse: OverstyrTidslinje) {
        hendelse.kontekst(this)
        håndter(hendelse, Vedtaksperiode::håndter)
    }

    private fun håndter(overstyrSykepengegrunnlag: OverstyrSykepengegrunnlag): Boolean {
        overstyrSykepengegrunnlag.kontekst(this)
        return énHarHåndtert(overstyrSykepengegrunnlag) { håndter(it) }
    }

    internal fun oppdaterSykdom(hendelse: SykdomshistorikkHendelse): Sykdomstidslinje {
        return sykdomshistorikk.håndter(hendelse)
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

    internal fun sykdomstidslinjeUten(sykdomstidslinjer: List<Sykdomstidslinje>) = sykdomstidslinjer.fold(sykdomstidslinje()) { acc, sykdomstidslinje ->
        acc - sykdomstidslinje
    }

    internal fun arbeidsgiverperiode(periode: Periode): Arbeidsgiverperiode? {
        val arbeidsgiverperioder = person.arbeidsgiverperiodeFor(organisasjonsnummer, sykdomstidslinje(), null)
        return arbeidsgiverperioder.finn(periode)
    }
    internal fun arbeidsgiverperiodeInkludertForkastet(periode: Periode, sykdomstidslinje: Sykdomstidslinje): Arbeidsgiverperiode? {
        val arbeidsgiverperioder = person.arbeidsgiverperiodeFor(organisasjonsnummer, sykdomstidslinjeInkludertForkastet(sykdomstidslinje), null)
        return arbeidsgiverperioder.finn(periode)
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

    private fun addInntektsmelding(inntektsmelding: Inntektsmelding) {
        val inntektsdato = inntektsmelding.addInntekt(inntektshistorikk, inntektsmelding.jurist(jurist))
        inntektsmelding.leggTilRefusjon(refusjonshistorikk)
        val sykdomstidslinjeperiode = sykdomstidslinje().periode()
        val skjæringstidspunkt = person.skjæringstidspunkt(inntektsdato.somPeriode())
        if (!inntektsmelding.skalOppdatereVilkårsgrunnlag(sykdomstidslinjeperiode)) {
            return inntektsmelding.info("Inntektsmelding oppdaterer ikke vilkårsgrunnlag")
        }
        finnAlternativInntektsdato(inntektsdato, skjæringstidspunkt)?.let {
            inntektsmelding.addInntekt(inntektshistorikk, it)
        }
        person.nyeArbeidsgiverInntektsopplysninger(skjæringstidspunkt, inntektsmelding, jurist)
        håndter(inntektsmelding) { håndtertInntektPåSkjæringstidspunktet(skjæringstidspunkt, inntektsmelding) }
    }

    internal fun lagreTidsnærInntektsmelding(
        skjæringstidspunkt: LocalDate,
        orgnummer: String,
        inntektsmelding: no.nav.helse.person.inntekt.Inntektsmelding,
        refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger,
        hendelse: IAktivitetslogg,
        oppholdsperiodeMellom: Periode?
    ) {
        if (this.organisasjonsnummer != orgnummer) return
        val nyFørsteFraværsdag = finnFørsteFraværsdag(skjæringstidspunkt)
        if (nyFørsteFraværsdag == null) return
        inntektsmelding.kopierTidsnærOpplysning(nyFørsteFraværsdag, hendelse, oppholdsperiodeMellom, inntektshistorikk)
        // TODO: lagre refusjonsopplysninger inni inntektsmelding-opplysningen?
        refusjonsopplysninger.lagreTidsnær(nyFørsteFraværsdag, refusjonshistorikk)
    }

    private fun søppelbøtte(hendelse: Hendelse, filter: VedtaksperiodeFilter): List<Vedtaksperiode.VedtaksperiodeForkastetEventBuilder> {
        hendelse.kontekst(this)
        val perioder: List<Pair<Vedtaksperiode, Vedtaksperiode.VedtaksperiodeForkastetEventBuilder>> = vedtaksperioder
            .filter(filter)
            .mapNotNull { vedtaksperiode -> vedtaksperiode.forkast(hendelse, utbetalinger)?.let { vedtaksperiode to it } }

        vedtaksperioder.removeAll(perioder.map { it.first })
        forkastede.addAll(perioder.map { ForkastetVedtaksperiode(it.first) })
        sykdomshistorikk.fjernDager(perioder.map { it.first.periode() })
        return perioder.map { it.second }
    }

    private fun registrerNyVedtaksperiode(vedtaksperiode: Vedtaksperiode) {
        vedtaksperioder.add(vedtaksperiode)
        vedtaksperioder.sort()
    }

    private fun registrerForkastetVedtaksperiode(vedtaksperiode: Vedtaksperiode, hendelse: SykdomstidslinjeHendelse) {
        hendelse.info("Oppretter forkastet vedtaksperiode ettersom Søknad inneholder errors")
        val vedtaksperiodeForkastetEventBuilder = vedtaksperiode.forkast(hendelse, utbetalinger)
        vedtaksperiodeForkastetEventBuilder!!.buildAndEmit()
        forkastede.add(ForkastetVedtaksperiode(vedtaksperiode))
    }

    internal fun finnVedtaksperiodeRettFør(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other ->
            other.erVedtaksperiodeRettFør(vedtaksperiode)
        }

    internal fun finnVedtaksperiodeFør(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.indexOf(vedtaksperiode)
            .takeIf { index -> index > 0 }
            ?.let { vedtaksperioder[it - 1] }

    internal fun finnVedtaksperiodeRettEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other ->
            vedtaksperiode.erVedtaksperiodeRettFør(other)
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
        return sykdomstidslinje().subset(førstePeriodeMedUtbetaling.periode().oppdaterFom(skjæringstidspunkt)).sisteSkjæringstidspunkt()
    }

    private fun finnAlternativInntektsdato(inntektsdato: LocalDate, skjæringstidspunkt: LocalDate): LocalDate? {
        if (inntektsdato <= skjæringstidspunkt) return null
        return sykdomstidslinje().sisteSkjæringstidspunktTidligereEnn(inntektsdato)?.takeUnless { it == inntektsdato }
    }

    internal fun beregnUtbetalingstidslinje(
        hendelse: IAktivitetslogg,
        periode: Periode,
        regler: ArbeidsgiverRegler,
        vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk,
        infotrygdhistorikk: Infotrygdhistorikk,
        subsumsjonObserver: SubsumsjonObserver
    ): Utbetalingstidslinje {
        val inntekter = Inntekter(
            hendelse = hendelse,
            vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk,
            regler = regler,
            subsumsjonObserver = subsumsjonObserver,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperioder = vedtaksperioder
        )
        val ghosttidslinje = vilkårsgrunnlagHistorikk.ghosttidslinje(organisasjonsnummer, periode.endInclusive)
        val sykdomstidslinje = ghosttidslinje.merge(sykdomstidslinje(), replace)
        if (sykdomstidslinje.count() == 0) return Utbetalingstidslinje()
        val builder = UtbetalingstidslinjeBuilder(inntekter, periode)
        infotrygdhistorikk.buildUtbetalingstidslinje(organisasjonsnummer, sykdomstidslinje, builder, subsumsjonObserver)
        return builder.result()
    }

    private fun <Hendelse : IAktivitetslogg> håndter(hendelse: Hendelse, håndterer: Vedtaksperiode.(Hendelse) -> Unit) {
        looper { håndterer(it, hendelse) }
    }

    private fun <Hendelse : IAktivitetslogg> énHarHåndtert(hendelse: Hendelse, håndterer: Vedtaksperiode.(Hendelse) -> Boolean): Boolean {
        var håndtert = false
        looper { håndtert = håndtert || håndterer(it, hendelse) }
        return håndtert
    }

    private fun <Hendelse : IAktivitetslogg> noenHarHåndtert(hendelse: Hendelse, håndterer: Vedtaksperiode.(Hendelse) -> Boolean): Boolean {
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

    internal fun kanForkastes(vedtaksperiode: Vedtaksperiode, hendelse: IAktivitetslogg) =
        vedtaksperiode.kanForkastes(utbetalinger, hendelse)

    fun vedtaksperioderKnyttetTilArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode?): List<Vedtaksperiode> {
        if (arbeidsgiverperiode == null) return emptyList()
        return vedtaksperioder.filter {
            arbeidsgiverperiode.hørerTil(it.periode())
        }
    }

    internal fun finnNesteVedtaksperiodeSomTrengerInntektsmelding(vedtaksperiode: Vedtaksperiode): Vedtaksperiode? = vedtaksperioder.finnNesteVedtaksperiodeSomTrengerInntektsmelding(vedtaksperiode)

    internal fun vedtaksperioderKnyttetTilSkjæringstidspunkt(skjæringstidspunkt: LocalDate) =
        vedtaksperioder.finnVedtaksperioderKnyttetTilSkjæringstidspunkt(skjæringstidspunkt)

    fun vedtaksperioderKnyttetTilArbeidsgiverperiodeInkludertForkastede(arbeidsgiverperiode: Arbeidsgiverperiode?): List<Vedtaksperiode> {
        if (arbeidsgiverperiode == null) return emptyList()
        return ForkastetVedtaksperiode.hørerTilArbeidsgiverperiode(forkastede, vedtaksperioder, arbeidsgiverperiode)
    }

    internal fun vedtaksperioderEtter(dato: LocalDate) = vedtaksperioder.filter { it.slutterEtter(dato) }

    internal class JsonRestorer private constructor() {
        internal companion object {
            internal fun restore(
                person: Person,
                organisasjonsnummer: String,
                id: UUID,
                inntektshistorikk: Inntektshistorikk,
                sykdomshistorikk: Sykdomshistorikk,
                sykmeldingsperioder: Sykmeldingsperioder,
                vedtaksperioder: MutableList<Vedtaksperiode>,
                forkastede: MutableList<ForkastetVedtaksperiode>,
                utbetalinger: List<Utbetaling>,
                feriepengeutbetalinger: List<Feriepengeutbetaling>,
                refusjonshistorikk: Refusjonshistorikk,
                yrkesaktivitet: Yrkesaktivitet,
                jurist: MaskinellJurist
            ) = Arbeidsgiver(
                person,
                organisasjonsnummer,
                id,
                inntektshistorikk,
                sykdomshistorikk,
                sykmeldingsperioder,
                vedtaksperioder,
                forkastede,
                utbetalinger.toMutableList(),
                feriepengeutbetalinger.toMutableList(),
                refusjonshistorikk,
                yrkesaktivitet,
                jurist
            )
        }
    }

    internal fun dto() = ArbeidsgiverUtDto(
        id = id,
        organisasjonsnummer = organisasjonsnummer,
        inntektshistorikk = inntektshistorikk.dto(),
        sykdomshistorikk = sykdomshistorikk.dto(),
        sykmeldingsperioder = sykmeldingsperioder.dto(),
        vedtaksperioder = vedtaksperioder.map { it.dto() },
        forkastede = forkastede.map { it.dto() },
        utbetalinger = utbetalinger.map { it.dto() },
        feriepengeutbetalinger = feriepengeutbetalinger.map { it.dto() },
        refusjonshistorikk = refusjonshistorikk.dto()
    )
}