package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Companion.godkjenning
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_11
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_12
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_13
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_15
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_21
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_6
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_7
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_9
import no.nav.helse.utbetalingslinjer.Fagområde.Sykepenger
import no.nav.helse.utbetalingslinjer.Fagområde.SykepengerRefusjon
import no.nav.helse.utbetalingslinjer.Oppdrag.Companion.trekkerTilbakePenger
import no.nav.helse.utbetalingslinjer.Utbetalingkladd.Companion.finnKladd
import no.nav.helse.utbetalingslinjer.Utbetalingtype.ANNULLERING
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Utbetaling private constructor(
    private val id: UUID,
    private val korrelasjonsId: UUID,
    private val beregningId: UUID,
    private val periode: Periode,
    private val utbetalingstidslinje: Utbetalingstidslinje,
    private val arbeidsgiverOppdrag: Oppdrag,
    private val personOppdrag: Oppdrag,
    private val tidsstempel: LocalDateTime,
    private var tilstand: Tilstand,
    private val type: Utbetalingtype,
    private val maksdato: LocalDate,
    private val forbrukteSykedager: Int?,
    private val gjenståendeSykedager: Int?,
    private val annulleringer: List<Utbetaling>,
    private var vurdering: Vurdering?,
    private var overføringstidspunkt: LocalDateTime?,
    private var avstemmingsnøkkel: Long?,
    private var avsluttet: LocalDateTime?,
    private var oppdatert: LocalDateTime = tidsstempel
) : Aktivitetskontekst {
    constructor(
        beregningId: UUID,
        korrelerendeUtbetaling: Utbetaling?,
        periode: Periode,
        utbetalingstidslinje: Utbetalingstidslinje,
        arbeidsgiverOppdrag: Oppdrag,
        personOppdrag: Oppdrag,
        type: Utbetalingtype,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        annulleringer: List<Utbetaling> = emptyList()
    ) : this(
        UUID.randomUUID(),
        korrelerendeUtbetaling?.korrelasjonsId ?: UUID.randomUUID(),
        beregningId,
        periode,
        utbetalingstidslinje,
        arbeidsgiverOppdrag,
        personOppdrag,
        LocalDateTime.now(),
        Ny,
        type,
        maksdato,
        forbrukteSykedager,
        gjenståendeSykedager,
        annulleringer,
        null,
        null,
        null,
        null
    ) {
        check(annulleringer.all { it.type == ANNULLERING }) { "skal bare ha annulleringer" }
    }

    private val stønadsdager get() = Oppdrag.stønadsdager(arbeidsgiverOppdrag, personOppdrag)
    private val observers = mutableSetOf<UtbetalingObserver>()
    private var forrigeHendelse: IAktivitetslogg? = null

    private fun harHåndtert(hendelse: IAktivitetslogg) =
        (hendelse == forrigeHendelse).also { forrigeHendelse = hendelse }

    fun registrer(observer: UtbetalingObserver) {
        observers.add(observer)
    }

    fun gyldig() = tilstand !in setOf(Ny, Forkastet)
    fun erUbetalt() = tilstand == IkkeUtbetalt
    fun erUtbetalt() = tilstand == Utbetalt || tilstand == Annullert
    private fun erAktiv() = erAvsluttet() || erInFlight()
    private fun erAktivEllerUbetalt() = erAktiv() || erUbetalt()
    fun erInFlight() = tilstand in listOf(AvventerKvitteringer, AvventerArbeidsgiverkvittering, AvventerPersonkvittering)
    fun erAvsluttet() = erUtbetalt() || tilstand == GodkjentUtenUtbetaling
    fun erAvvist() = tilstand == IkkeGodkjent
    private fun erAnnullering() = type == ANNULLERING

    fun harNærliggendeUtbetaling(other: Periode): Boolean {
        if (arbeidsgiverOppdrag.isEmpty() && personOppdrag.isEmpty()) return false
        return periode.overlapperMed(other.oppdaterFom(other.start.minusDays(16)))
    }
    fun trekkerTilbakePenger() = listOf(arbeidsgiverOppdrag, personOppdrag).trekkerTilbakePenger()

    // this kan revurdere other gitt at fagsystemId == other.fagsystemId,
    // og at this er lik den siste aktive utbetalingen for fagsystemIden
    fun hørerSammen(other: Utbetaling) =
        this.korrelasjonsId == other.korrelasjonsId

    fun harUtbetalinger() =
        harOppdragMedUtbetalinger() || annulleringer.any { it.harOppdragMedUtbetalinger() }

    private fun harOppdragMedUtbetalinger() =
        arbeidsgiverOppdrag.harUtbetalinger() || personOppdrag.harUtbetalinger()

    fun harDelvisRefusjon() = arbeidsgiverOppdrag.harUtbetalinger () && personOppdrag.harUtbetalinger()

    fun erKlarForGodkjenning() = personOppdrag.erKlarForGodkjenning() && arbeidsgiverOppdrag.erKlarForGodkjenning()

    fun opprett(hendelse: IAktivitetslogg) {
        tilstand.opprett(this, hendelse)
    }

    fun håndter(hendelse: UtbetalingsgodkjenningPort) {
        if (!hendelse.erRelevant(id)) return
        hendelse.valider()
        godkjenn(hendelse, hendelse.vurdering())
    }

    fun håndter(hendelse: GrunnbeløpsreguleringPort) {
        godkjenn(hendelse, Vurdering.automatiskGodkjent)
    }

    fun håndter(utbetaling: UtbetalingHendelsePort) {
        if (!utbetaling.erRelevant(arbeidsgiverOppdrag.fagsystemId(), personOppdrag.fagsystemId(), id)) return håndterKvitteringForAnnullering(utbetaling)
        if (harHåndtert(utbetaling)) return
        utbetaling.kontekst(this)
        tilstand.kvittér(this, utbetaling)
    }

    private fun håndterKvitteringForAnnullering(hendelse: UtbetalingHendelsePort) {
        if (annulleringer.none { hendelse.erRelevant(it.arbeidsgiverOppdrag.fagsystemId(), it.personOppdrag.fagsystemId(), it.id) }) return
        hendelse.kontekst(this)
        tilstand.kvittérAnnullering(this, hendelse)
    }

    fun håndter(simulering: SimuleringPort) {
        if (!simulering.erRelevantForUtbetaling(id)) return
        personOppdrag.håndter(simulering)
        arbeidsgiverOppdrag.håndter(simulering)
    }

    fun simuler(hendelse: IAktivitetslogg) {
        hendelse.kontekst(this)
        tilstand.simuler(this, hendelse)
    }

    fun godkjenning(
        hendelse: IAktivitetslogg,
        periode: Periode,
        skjæringstidspunkt: LocalDate,
        periodetype: UtbetalingPeriodetype,
        førstegangsbehandling: Boolean,
        inntektskilde: UtbetalingInntektskilde,
        orgnummereMedRelevanteArbeidsforhold: List<String>
    ) {
        hendelse.kontekst(this)
        tilstand.godkjenning(
            this,
            periode,
            skjæringstidspunkt,
            periodetype,
            førstegangsbehandling,
            inntektskilde,
            orgnummereMedRelevanteArbeidsforhold,
            hendelse
        )
    }

    fun håndter(påminnelse: UtbetalingpåminnelsePort) {
        if (!påminnelse.erRelevant(id)) return
        påminnelse.kontekst(this)
        if (!påminnelse.gjelderStatus(tilstand.status)) return
        tilstand.håndter(this, påminnelse)
    }

    fun gjelderFor(hendelse: UtbetalingHendelsePort) =
        hendelse.erRelevant(arbeidsgiverOppdrag.fagsystemId(), personOppdrag.fagsystemId(), id)

    fun gjelderFor(hendelse: UtbetalingsgodkjenningPort) =
        hendelse.erRelevant(id)

    fun valider(simulering: SimuleringPort) {
        arbeidsgiverOppdrag.valider(simulering)
        personOppdrag.valider(simulering)
    }

    fun build(builder: UtbetalingVedtakFattetBuilder) {
        builder.utbetalingId(id)
        vurdering?.build(builder)
    }

    fun håndter(hendelse: AnnullerUtbetalingPort) {
        godkjenn(hendelse, hendelse.vurdering())
    }

    fun annuller(hendelse: AnnullerUtbetalingPort): Utbetaling? {
        if (!hendelse.erRelevant(arbeidsgiverOppdrag.fagsystemId())) {
            hendelse.info("Kan ikke annullere: hendelsen er ikke relevant for ${arbeidsgiverOppdrag.fagsystemId()}.")
            hendelse.funksjonellFeil(RV_UT_15)
            return null
        }
        return opphør(hendelse)
    }

    private fun opphør(hendelse: IAktivitetslogg) =
        tilstand.annuller(this, hendelse)

    fun forkast(hendelse: IAktivitetslogg) {
        hendelse.kontekst(this)
        tilstand.forkast(this, hendelse)
    }

    fun arbeidsgiverOppdrag() = arbeidsgiverOppdrag

    fun personOppdrag() = personOppdrag

    override fun toSpesifikkKontekst() =
        SpesifikkKontekst("Utbetaling", mapOf("utbetalingId" to "$id"))

    private fun godkjenn(hendelse: IAktivitetslogg, vurdering: Vurdering) {
        hendelse.kontekst(this)
        tilstand.godkjenn(this, hendelse, vurdering)
    }

    private fun tilstand(neste: Tilstand, hendelse: IAktivitetslogg) {
        oppdatert = LocalDateTime.now()
        val forrigeTilstand = tilstand
        tilstand = neste
        observers.forEach {
            it.utbetalingEndret(
                id,
                type,
                arbeidsgiverOppdrag,
                personOppdrag,
                forrigeTilstand.status,
                neste.status,
                korrelasjonsId
            )
        }
        tilstand.entering(this, hendelse)
    }


    companion object {

        val log: Logger = LoggerFactory.getLogger("Utbetaling")
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        private const val systemident = "SPLEIS"

        fun lagUtbetaling(
            utbetalinger: List<Utbetaling>,
            fødselsnummer: String,
            beregningId: UUID,
            organisasjonsnummer: String,
            utbetalingstidslinje: Utbetalingstidslinje,
            periode: Periode,
            aktivitetslogg: IAktivitetslogg,
            maksdato: LocalDate,
            forbrukteSykedager: Int,
            gjenståendeSykedager: Int,
            type: Utbetalingtype = Utbetalingtype.UTBETALING
        ): Pair<Utbetaling, List<Utbetaling>> {
            val bb = UtbetalingkladderBuilder(utbetalingstidslinje, organisasjonsnummer, fødselsnummer)
            val oppdragene = bb.build()

            val kladdene = oppdragene.finnKladd(periode)
            val kladden = kladdene.firstOrNull() ?: Utbetalingkladd(
                periode = periode,
                arbeidsgiveroppdrag = Oppdrag(organisasjonsnummer, SykepengerRefusjon),
                personoppdrag = Oppdrag(fødselsnummer, Sykepenger)
            )
            if (kladdene.size > 1) {
                sikkerlogg.error("Vedtaksperioden $periode brytes opp i flere utbetalinger, mest sannsynlig en overlappende Infotrygd-utbetaling",
                    keyValue("fødselsnummer", fødselsnummer),
                    keyValue("organisasjonsnummer", organisasjonsnummer)
                )
            }

            val forrigeUtbetalte = kladden.forrigeUtbetalte(utbetalinger)
            val korrelerendeUtbetaling = forrigeUtbetalte.firstOrNull { it.harOppdragMedUtbetalinger() } ?: forrigeUtbetalte.firstOrNull()

            val nyUtbetaling = if (korrelerendeUtbetaling == null) {
                kladden.begrensTil(periode)
            } else {
                if (kladden.opphørerHale(korrelerendeUtbetaling.periode)) {
                    kladden.begrensTil(aktivitetslogg, periode.oppdaterFom(korrelerendeUtbetaling.periode), korrelerendeUtbetaling.arbeidsgiverOppdrag, korrelerendeUtbetaling.personOppdrag)
                } else {
                    kladden.begrensTilOgKopier(aktivitetslogg, periode.oppdaterFom(korrelerendeUtbetaling.periode), korrelerendeUtbetaling.arbeidsgiverOppdrag, korrelerendeUtbetaling.personOppdrag)
                }
            }

            val annulleringer = forrigeUtbetalte.filterNot { it === korrelerendeUtbetaling }.mapNotNull { it.opphør(aktivitetslogg) }
            if (annulleringer.isNotEmpty()) aktivitetslogg.varsel(RV_UT_21)
            val utbetalingen = nyUtbetaling.lagUtbetaling(type, korrelerendeUtbetaling, beregningId, utbetalingstidslinje, maksdato, forbrukteSykedager, gjenståendeSykedager, annulleringer)
            return utbetalingen to annulleringer
        }

        fun finnUtbetalingForAnnullering(utbetalinger: List<Utbetaling>, hendelse: AnnullerUtbetalingPort): Utbetaling? {
            return utbetalinger.aktive().lastOrNull() ?: run {
                hendelse.funksjonellFeil(RV_UT_4)
                return null
            }
        }
        fun List<Utbetaling>.aktive() = grupperUtbetalinger(Utbetaling::erAktiv)
        private fun List<Utbetaling>.aktiveMedUbetalte() = grupperUtbetalinger(Utbetaling::erAktivEllerUbetalt)
        fun List<Utbetaling>.aktive(periode: Periode) = this
            .aktive()
            .filter { utbetaling ->
                utbetaling.periode.overlapperMed(periode) || utbetaling.periode.erRettFør(periode.start)
            }
        private fun List<Utbetaling>.grupperUtbetalinger(filter: (Utbetaling) -> Boolean) =
            this
                .asSequence()
                .filter { it.gyldig() }
                .groupBy { it.korrelasjonsId }
                .filter { (_, utbetalinger) -> utbetalinger.any(filter) }
                .map { (_, utbetalinger) -> utbetalinger.sortedBy { it.tidsstempel } }
                .map { it.last(filter) }
                .sortedBy { it.tidsstempel }
                .filterNot(Utbetaling::erAnnullering)
                .toList()

        fun List<Utbetaling>.tillaterOpprettelseAvUtbetaling(other: Utbetaling): Boolean {
            if (other.erAnnullering()) return true // må godta annulleringer ettersom de vil rydde opp i nettopp overlappende utbetalinger
            val overlappendeUtbetalingsperioder = overlappendeUtbetalingsperioder(other)
            if (overlappendeUtbetalingsperioder.isNotEmpty()) {
                sikkerlogg.warn("Vi har opprettet en utbetaling med periode ${other.periode} & korrelasjonsId ${other.korrelasjonsId} som overlapper med eksisterende utbetalinger $overlappendeUtbetalingsperioder")
            }
            return overlappendeUtbetalingsperioder.isEmpty()
        }

        private fun List<Utbetaling>.overlappendeUtbetalingsperioder(other: Utbetaling): List<Periode> {
            return aktiveMedUbetalte()
                .filterNot { it.hørerSammen(other) }
                .filter { it.periode.overlapperMed(other.periode) }
                .map { it.periode }
        }

        fun List<Utbetaling>.harNærliggendeUtbetaling(periode: Periode) =
            aktiveMedUbetalte().any { it.harNærliggendeUtbetaling(periode) }

        fun List<Utbetaling>.harId(id: UUID) = any { it.id == id }
        fun ferdigUtbetaling(
            id: UUID,
            korrelasjonsId: UUID,
            beregningId: UUID,
            annulleringer: List<Utbetaling>,
            opprinneligPeriode: Periode,
            utbetalingstidslinje: Utbetalingstidslinje,
            arbeidsgiverOppdrag: Oppdrag,
            personOppdrag: Oppdrag,
            tidsstempel: LocalDateTime,
            utbetalingstatus: Utbetalingstatus,
            utbetalingtype: Utbetalingtype,
            maksdato: LocalDate,
            forbrukteSykedager: Int?,
            gjenståendeSykedager: Int?,
            vurdering: Vurdering?,
            overføringstidspunkt: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            avsluttet: LocalDateTime?,
            oppdatert: LocalDateTime
        ): Utbetaling = Utbetaling(
            id = id,
            korrelasjonsId = korrelasjonsId,
            beregningId = beregningId,
            periode = opprinneligPeriode,
            utbetalingstidslinje = utbetalingstidslinje,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            personOppdrag = personOppdrag,
            tidsstempel = tidsstempel,
            tilstand = utbetalingstatus.tilTilstand(),
            type = utbetalingtype,
            maksdato = maksdato,
            forbrukteSykedager = forbrukteSykedager,
            gjenståendeSykedager = gjenståendeSykedager,
            annulleringer = annulleringer,
            vurdering = vurdering,
            overføringstidspunkt = overføringstidspunkt,
            avstemmingsnøkkel = avstemmingsnøkkel,
            avsluttet = avsluttet,
            oppdatert = oppdatert
        )

        fun ferdigVurdering(
            godkjent: Boolean,
            ident: String,
            epost: String,
            tidspunkt: LocalDateTime,
            automatiskBehandling: Boolean
        ): Vurdering = Vurdering(godkjent, ident, epost, tidspunkt, automatiskBehandling)

        // kan forkaste dersom ingen utbetalinger er utbetalt/in flight, eller de er annullert
        fun kanForkastes(vedtaksperiodeUtbetalinger: List<Utbetaling>, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
            val annulleringer = arbeidsgiverUtbetalinger.filter { it.erAnnullering() }
            return vedtaksperiodeUtbetalinger.filter { it.erAktiv() }.all { utbetaling ->
                annulleringer.any { annullering -> annullering.hørerSammen(utbetaling) }
            }
        }
    }

    fun accept(visitor: UtbetalingVisitor) {
        visitor.preVisitUtbetaling(
            this,
            id,
            korrelasjonsId,
            type,
            tilstand.status,
            periode,
            tidsstempel,
            oppdatert,
            arbeidsgiverOppdrag.nettoBeløp(),
            personOppdrag.nettoBeløp(),
            maksdato,
            forbrukteSykedager,
            gjenståendeSykedager,
            stønadsdager,
            beregningId,
            overføringstidspunkt,
            avsluttet,
            avstemmingsnøkkel,
            annulleringer.map { it.id }.toSet()
        )
        utbetalingstidslinje.accept(visitor)
        visitor.preVisitArbeidsgiverOppdrag(arbeidsgiverOppdrag)
        arbeidsgiverOppdrag.accept(visitor)
        visitor.postVisitArbeidsgiverOppdrag(arbeidsgiverOppdrag)
        visitor.preVisitPersonOppdrag(personOppdrag)
        personOppdrag.accept(visitor)
        visitor.postVisitPersonOppdrag(personOppdrag)
        vurdering?.accept(visitor)
        visitor.postVisitUtbetaling(
            this,
            id,
            korrelasjonsId,
            type,
            tilstand.status,
            periode,
            tidsstempel,
            oppdatert,
            arbeidsgiverOppdrag.nettoBeløp(),
            personOppdrag.nettoBeløp(),
            maksdato,
            forbrukteSykedager,
            gjenståendeSykedager,
            stønadsdager,
            beregningId,
            overføringstidspunkt,
            avsluttet,
            avstemmingsnøkkel,
            annulleringer.map { it.id }.toSet()
        )
    }

    fun utbetalingstidslinje() = utbetalingstidslinje
    fun utbetalingstidslinje(periode: Periode) = utbetalingstidslinje.subset(periode)

    private fun overførArbeidsgiveroppdrag(hendelse: IAktivitetslogg) {
        overfør(hendelse, arbeidsgiverOppdrag)
    }

    private fun overførPersonoppdrag(hendelse: IAktivitetslogg) {
        overfør(hendelse, personOppdrag)
    }

    private fun overførBegge(hendelse: IAktivitetslogg) {
        overførArbeidsgiveroppdrag(hendelse)
        overførPersonoppdrag(hendelse)
    }

    private fun overfør(hendelse: IAktivitetslogg, oppdrag: Oppdrag) {
        vurdering?.overfør(hendelse, oppdrag, maksdato.takeUnless { type == ANNULLERING })
    }

    private fun håndterKvittering(hendelse: UtbetalingHendelsePort) {
        håndterKvittering(hendelse) { if (type == ANNULLERING) Annullert else Utbetalt }
    }

    private fun håndterKvittering(hendelse: UtbetalingHendelsePort, nesteTilstand: () -> Tilstand) {
        hendelse.valider()
        if (hendelse.skalForsøkesIgjen()) return // utbetaling gjør retry ved neste påminnelse
        lagreOverføringsinformasjon(hendelse, hendelse.avstemmingsnøkkel, hendelse.overføringstidspunkt)
        tilstand(nesteTilstand(), hendelse)
    }

    fun nyVedtaksperiodeUtbetaling(vedtaksperiodeId: UUID) {
        observers.forEach { it.nyVedtaksperiodeUtbetaling(this.id, vedtaksperiodeId) }
    }

    fun overlapperMed(other: Utbetaling): Boolean {
        return this.periode.overlapperMed(other.periode)
    }
    fun erNyereEnn(other: LocalDateTime): Boolean {
        return other <= tidsstempel
    }
    private fun lagreOverføringsinformasjon(hendelse: IAktivitetslogg, avstemmingsnøkkel: Long, tidspunkt: LocalDateTime) {
        hendelse.info("Utbetalingen ble overført til Oppdrag/UR $tidspunkt, og har fått avstemmingsnøkkel $avstemmingsnøkkel.\n")
        if (this.avstemmingsnøkkel != null && this.avstemmingsnøkkel != avstemmingsnøkkel)
            hendelse.info("Avstemmingsnøkkel har endret seg.\nTidligere verdi: ${this.avstemmingsnøkkel}")
        if (this.overføringstidspunkt == null) this.overføringstidspunkt = tidspunkt
        if (this.avstemmingsnøkkel == null) this.avstemmingsnøkkel = avstemmingsnøkkel
    }
    override fun toString() = "$type(${tilstand.status}) - $periode"

    internal interface Tilstand {
        val status: Utbetalingstatus
        fun forkast(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {}

        fun opprett(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            hendelse.info("Forventet ikke å opprette utbetaling i tilstand=${this::class.simpleName}")
            hendelse.funksjonellFeil(RV_UT_6)
        }

        fun godkjenn(
            utbetaling: Utbetaling,
            hendelse: IAktivitetslogg,
            vurdering: Vurdering
        ) {
            hendelse.info("Forventet ikke godkjenning på utbetaling=${utbetaling.id} i tilstand=${this::class.simpleName}")
            hendelse.funksjonellFeil(RV_UT_7)
        }

        fun annuller(utbetaling: Utbetaling, hendelse: IAktivitetslogg): Utbetaling? {
            hendelse.info("Forventet ikke å annullere på utbetaling=${utbetaling.id} i tilstand=${this::class.simpleName}")
            hendelse.funksjonellFeil(RV_UT_9)
            return null
        }

        fun kvittér(utbetaling: Utbetaling, hendelse: UtbetalingHendelsePort) {
            hendelse.info("Forventet ikke kvittering på utbetaling=${utbetaling.id} i tilstand=${this::class.simpleName}")
            hendelse.funksjonellFeil(RV_UT_11)
        }
        fun kvittérAnnullering(utbetaling: Utbetaling, hendelse: UtbetalingHendelsePort) {
            hendelse.info("Forventet ikke kvittering for annullering på utbetaling=${utbetaling.id} i tilstand=${this::class.simpleName}")
            hendelse.funksjonellFeil(RV_UT_11)
        }

        fun håndter(utbetaling: Utbetaling, påminnelse: UtbetalingpåminnelsePort) {
            påminnelse.info("Utbetaling ble påminnet, men gjør ingenting")
        }

        fun simuler(utbetaling: Utbetaling, aktivitetslogg: IAktivitetslogg) {
            aktivitetslogg.info("Forventet ikke simulering på utbetaling=${utbetaling.id} i tilstand=${this::class.simpleName}")
            aktivitetslogg.funksjonellFeil(RV_UT_12)
        }

        fun godkjenning(
            utbetaling: Utbetaling,
            periode: Periode,
            skjæringstidspunkt: LocalDate,
            periodetype: UtbetalingPeriodetype,
            førstegangsbehandling: Boolean,
            inntektskilde: UtbetalingInntektskilde,
            orgnummereMedRelevanteArbeidsforhold: List<String>,
            hendelse: IAktivitetslogg
        ) {
            hendelse.info("Forventet ikke å lage godkjenning på utbetaling=${utbetaling.id} i tilstand=${this::class.simpleName}")
            hendelse.funksjonellFeil(RV_UT_13)
        }

        fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {}
    }

    internal object Ny : Tilstand {
        override val status = Utbetalingstatus.NY
        override fun opprett(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            utbetaling.tilstand(IkkeUtbetalt, hendelse)
        }
    }

    internal object IkkeUtbetalt : Tilstand {
        override val status = Utbetalingstatus.IKKE_UTBETALT
        override fun forkast(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            utbetaling.annulleringer.forEach { it.forkast(hendelse) }
            hendelse.kontekst(utbetaling)
            hendelse.info("Forkaster utbetaling")
            utbetaling.tilstand(Forkastet, hendelse)
        }

        override fun godkjenn(utbetaling: Utbetaling, hendelse: IAktivitetslogg, vurdering: Vurdering) {
            utbetaling.vurdering = vurdering
            utbetaling.annulleringer.forEach { it.godkjenn(hendelse, vurdering) }
            hendelse.kontekst(utbetaling)
            utbetaling.tilstand(vurdering.avgjør(utbetaling), hendelse)
        }

        override fun simuler(utbetaling: Utbetaling, aktivitetslogg: IAktivitetslogg) {
            utbetaling.annulleringer.forEach { it.simuler(aktivitetslogg) }

            aktivitetslogg.kontekst(utbetaling)
            utbetaling.arbeidsgiverOppdrag.simuler(aktivitetslogg, utbetaling.maksdato, systemident)
            utbetaling.personOppdrag.simuler(aktivitetslogg, utbetaling.maksdato, systemident)
        }

        override fun godkjenning(
            utbetaling: Utbetaling,
            periode: Periode,
            skjæringstidspunkt: LocalDate,
            periodetype: UtbetalingPeriodetype,
            førstegangsbehandling: Boolean,
            inntektskilde: UtbetalingInntektskilde,
            orgnummereMedRelevanteArbeidsforhold: List<String>,
            hendelse: IAktivitetslogg
        ) {
            godkjenning(
                aktivitetslogg = hendelse,
                periodeFom = periode.start,
                periodeTom = periode.endInclusive,
                skjæringstidspunkt = skjæringstidspunkt,
                periodetype = periodetype.name,
                førstegangsbehandling = førstegangsbehandling,
                utbetalingtype = utbetaling.type.name,
                inntektskilde = inntektskilde.name,
                orgnummereMedRelevanteArbeidsforhold = orgnummereMedRelevanteArbeidsforhold
            )
        }
    }

    internal object Godkjent : Tilstand {
        override val status = Utbetalingstatus.GODKJENT

        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            check(utbetaling.annulleringer.isNotEmpty())
        }

        override fun håndter(utbetaling: Utbetaling, påminnelse: UtbetalingpåminnelsePort) {
            vurderNesteTilstand(utbetaling, påminnelse)
        }

        override fun kvittérAnnullering(utbetaling: Utbetaling, hendelse: UtbetalingHendelsePort) {
            vurderNesteTilstand(utbetaling, hendelse)
        }

        private fun vurderNesteTilstand(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            if (utbetaling.annulleringer.any { !it.erAvsluttet() }) return
            utbetaling.tilstand(when {
                utbetaling.arbeidsgiverOppdrag.harUtbetalinger() && utbetaling.personOppdrag.harUtbetalinger() -> AvventerKvitteringer
                utbetaling.arbeidsgiverOppdrag.harUtbetalinger() -> AvventerArbeidsgiverkvittering
                utbetaling.personOppdrag.harUtbetalinger() -> AvventerPersonkvittering
                else -> GodkjentUtenUtbetaling
            }, hendelse)
        }
    }

    internal object GodkjentUtenUtbetaling : Tilstand {
        override val status = Utbetalingstatus.GODKJENT_UTEN_UTBETALING
        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            check(!utbetaling.harOppdragMedUtbetalinger())
            utbetaling.vurdering?.avsluttetUtenUtbetaling(utbetaling)
            utbetaling.avsluttet = LocalDateTime.now()
        }

        override fun annuller(utbetaling: Utbetaling, hendelse: IAktivitetslogg) = Utbetaling(
            utbetaling.beregningId,
            utbetaling,
            utbetaling.periode,
            utbetaling.utbetalingstidslinje,
            utbetaling.arbeidsgiverOppdrag.annuller(hendelse),
            utbetaling.personOppdrag.annuller(hendelse),
            ANNULLERING,
            LocalDate.MAX,
            null,
            null
        ).also { hendelse.info("Oppretter annullering med id ${it.id}") }
    }

    internal object AvventerKvitteringer : Tilstand {
        override val status = Utbetalingstatus.AVVENTER_KVITTERINGER

        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            check(utbetaling.arbeidsgiverOppdrag.harUtbetalinger() && utbetaling.personOppdrag.harUtbetalinger())
            utbetaling.overførBegge(hendelse)
        }

        override fun håndter(utbetaling: Utbetaling, påminnelse: UtbetalingpåminnelsePort) {
            utbetaling.overførBegge(påminnelse)
        }

        override fun kvittér(utbetaling: Utbetaling, hendelse: UtbetalingHendelsePort) {
            utbetaling.håndterKvittering(hendelse) {
                when {
                    utbetaling.arbeidsgiverOppdrag.lagreOverføringsinformasjon(hendelse) -> AvventerPersonkvittering
                    utbetaling.personOppdrag.lagreOverføringsinformasjon(hendelse) -> AvventerArbeidsgiverkvittering
                    else -> error("fikk kvittering som verken var for arbeidsgiver- eller personoppdrag")
                }
            }
        }
    }

    internal object AvventerArbeidsgiverkvittering : Tilstand {
        override val status = Utbetalingstatus.AVVENTER_ARBEIDSGIVERKVITTERING

        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            check(utbetaling.arbeidsgiverOppdrag.harUtbetalinger())
            utbetaling.overførArbeidsgiveroppdrag(hendelse)
        }

        override fun håndter(utbetaling: Utbetaling, påminnelse: UtbetalingpåminnelsePort) {
            utbetaling.overførArbeidsgiveroppdrag(påminnelse)
        }

        override fun kvittér(utbetaling: Utbetaling, hendelse: UtbetalingHendelsePort) {
            if (!utbetaling.arbeidsgiverOppdrag.lagreOverføringsinformasjon(hendelse)) return
            utbetaling.håndterKvittering(hendelse)
        }
    }

    internal object AvventerPersonkvittering : Tilstand {
        override val status = Utbetalingstatus.AVVENTER_PERSONKVITTERING

        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            check(utbetaling.personOppdrag.harUtbetalinger())
            utbetaling.overførPersonoppdrag(hendelse)
        }

        override fun håndter(utbetaling: Utbetaling, påminnelse: UtbetalingpåminnelsePort) {
            utbetaling.overførPersonoppdrag(påminnelse)
        }

        override fun kvittér(utbetaling: Utbetaling, hendelse: UtbetalingHendelsePort) {
            if (!utbetaling.personOppdrag.lagreOverføringsinformasjon(hendelse)) return
            utbetaling.håndterKvittering(hendelse)
        }
    }

    internal object Annullert : Tilstand {
        override val status = Utbetalingstatus.ANNULLERT
        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            utbetaling.vurdering?.annullert(utbetaling)
            utbetaling.avsluttet = LocalDateTime.now()
        }
    }

    internal object Utbetalt : Tilstand {
        override val status = Utbetalingstatus.UTBETALT
        override fun entering(utbetaling: Utbetaling, hendelse: IAktivitetslogg) {
            utbetaling.vurdering?.utbetalt(utbetaling)
            utbetaling.avsluttet = LocalDateTime.now()
        }

        override fun annuller(utbetaling: Utbetaling, hendelse: IAktivitetslogg) =
            Utbetaling(
                utbetaling.beregningId,
                utbetaling,
                utbetaling.periode,
                utbetaling.utbetalingstidslinje,
                utbetaling.arbeidsgiverOppdrag.annuller(hendelse),
                utbetaling.personOppdrag.annuller(hendelse),
                ANNULLERING,
                LocalDate.MAX,
                null,
                null
            ).also { hendelse.info("Oppretter annullering med id ${it.id}") }
    }

    internal object IkkeGodkjent : Tilstand {
        override val status = Utbetalingstatus.IKKE_GODKJENT
    }
    internal object Forkastet : Tilstand {
        override val status = Utbetalingstatus.FORKASTET
    }

    class Vurdering(
        private val godkjent: Boolean,
        private val ident: String,
        private val epost: String,
        private val tidspunkt: LocalDateTime,
        private val automatiskBehandling: Boolean
    ) {
        companion object {
            val automatiskGodkjent get() = Vurdering(true, systemident, "tbd@nav.no", LocalDateTime.now(), true)
        }

        fun accept(visitor: UtbetalingVurderingVisitor) {
            visitor.visitVurdering(this, ident, epost, tidspunkt, automatiskBehandling, godkjent)
        }

        fun annullert(utbetaling: Utbetaling) {
            utbetaling.observers.forEach {
                it.utbetalingAnnullert(
                    id = utbetaling.id,
                    korrelasjonsId = utbetaling.korrelasjonsId,
                    periode = utbetaling.periode,
                    personFagsystemId = utbetaling.personOppdrag.fagsystemId(),
                    godkjenttidspunkt = tidspunkt,
                    saksbehandlerEpost = epost,
                    saksbehandlerIdent = ident,
                    arbeidsgiverFagsystemId = utbetaling.arbeidsgiverOppdrag.fagsystemId()
                )
            }
        }

        fun utbetalt(utbetaling: Utbetaling) {
            utbetaling.observers.forEach {
                it.utbetalingUtbetalt(
                    utbetaling.id,
                    utbetaling.korrelasjonsId,
                    utbetaling.type,
                    utbetaling.periode,
                    utbetaling.maksdato,
                    utbetaling.forbrukteSykedager!!,
                    utbetaling.gjenståendeSykedager!!,
                    utbetaling.stønadsdager,
                    utbetaling.arbeidsgiverOppdrag,
                    utbetaling.personOppdrag,
                    epost,
                    tidspunkt,
                    automatiskBehandling,
                    utbetaling.utbetalingstidslinje,
                    ident
                )
            }
        }

        fun avsluttetUtenUtbetaling(utbetaling: Utbetaling) {
            utbetaling.observers.forEach {
                it.utbetalingUtenUtbetaling(
                    utbetaling.id,
                    utbetaling.korrelasjonsId,
                    utbetaling.type,
                    utbetaling.periode,
                    utbetaling.maksdato,
                    utbetaling.forbrukteSykedager!!,
                    utbetaling.gjenståendeSykedager!!,
                    utbetaling.stønadsdager,
                    utbetaling.personOppdrag,
                    ident,
                    utbetaling.arbeidsgiverOppdrag,
                    tidspunkt,
                    automatiskBehandling,
                    utbetaling.utbetalingstidslinje,
                    epost
                )
            }
        }

        fun overfør(hendelse: IAktivitetslogg, oppdrag: Oppdrag, maksdato: LocalDate?) {
            oppdrag.overfør(hendelse, maksdato, ident)
        }

        internal fun avgjør(utbetaling: Utbetaling) =
            when {
                !godkjent -> IkkeGodkjent
                utbetaling.annulleringer.any { it.harOppdragMedUtbetalinger() } -> Godkjent
                utbetaling.arbeidsgiverOppdrag.harUtbetalinger() && utbetaling.personOppdrag.harUtbetalinger() -> AvventerKvitteringer
                utbetaling.arbeidsgiverOppdrag.harUtbetalinger() -> AvventerArbeidsgiverkvittering
                utbetaling.personOppdrag.harUtbetalinger() -> AvventerPersonkvittering
                utbetaling.type == ANNULLERING -> Annullert
                else -> GodkjentUtenUtbetaling
            }

        fun build(builder: UtbetalingVedtakFattetBuilder) {
            builder.utbetalingVurdert(tidspunkt)
        }
    }

}

enum class Utbetalingstatus {
    NY,
    IKKE_UTBETALT,
    IKKE_GODKJENT,
    AVVENTER_KVITTERINGER,
    AVVENTER_ARBEIDSGIVERKVITTERING,
    AVVENTER_PERSONKVITTERING,
    UTBETALT,
    GODKJENT,
    GODKJENT_UTEN_UTBETALING,
    ANNULLERT,
    FORKASTET;
    internal fun tilTilstand() = when(this) {
        NY -> Utbetaling.Ny
        IKKE_UTBETALT -> Utbetaling.IkkeUtbetalt
        IKKE_GODKJENT -> Utbetaling.IkkeGodkjent
        GODKJENT -> Utbetaling.Godkjent
        AVVENTER_KVITTERINGER -> Utbetaling.AvventerKvitteringer
        AVVENTER_ARBEIDSGIVERKVITTERING -> Utbetaling.AvventerArbeidsgiverkvittering
        AVVENTER_PERSONKVITTERING -> Utbetaling.AvventerPersonkvittering
        UTBETALT -> Utbetaling.Utbetalt
        GODKJENT_UTEN_UTBETALING -> Utbetaling.GodkjentUtenUtbetaling
        ANNULLERT -> Utbetaling.Annullert
        FORKASTET -> Utbetaling.Forkastet
    }
}

enum class Utbetalingtype { UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING, FERIEPENGER }
enum class Endringskode { NY, UEND, ENDR }
/* en enum-port/adapter-greie. Alternativet er en modul som inneholder ... kodeverk */
enum class UtbetalingInntektskilde { EN_ARBEIDSGIVER, FLERE_ARBEIDSGIVERE }
enum class Klassekode(val verdi: String) {
    RefusjonIkkeOpplysningspliktig(verdi = "SPREFAG-IOP"),
    RefusjonFeriepengerIkkeOpplysningspliktig(verdi = "SPREFAGFER-IOP"),
    SykepengerArbeidstakerOrdinær(verdi = "SPATORD"),
    SykepengerArbeidstakerFeriepenger(verdi = "SPATFER");

    companion object {
        private val map = values().associateBy(Klassekode::verdi)
        fun from(verdi: String) = requireNotNull(map[verdi]) { "Støtter ikke klassekode: $verdi" }
    }
}
