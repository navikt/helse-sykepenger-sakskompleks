package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.BehandlingkildeDto
import no.nav.helse.dto.BehandlingtilstandDto
import no.nav.helse.dto.deserialisering.BehandlingInnDto
import no.nav.helse.dto.deserialisering.BehandlingendringInnDto
import no.nav.helse.dto.deserialisering.BehandlingerInnDto
import no.nav.helse.dto.serialisering.BehandlingUtDto
import no.nav.helse.dto.serialisering.BehandlingendringUtDto
import no.nav.helse.dto.serialisering.BehandlingerUtDto
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.hendelser.utbetaling.avvist
import no.nav.helse.person.Behandlinger.Behandling.Companion.dokumentsporing
import no.nav.helse.person.Behandlinger.Behandling.Companion.erUtbetaltPåForskjelligeUtbetalinger
import no.nav.helse.person.Behandlinger.Behandling.Companion.jurist
import no.nav.helse.person.Behandlinger.Behandling.Companion.lagreTidsnæreInntekter
import no.nav.helse.person.Behandlinger.Behandling.Endring.Companion.dokumentsporing
import no.nav.helse.person.Dokumentsporing.Companion.ider
import no.nav.helse.person.Dokumentsporing.Companion.sisteInntektsmeldingId
import no.nav.helse.person.Dokumentsporing.Companion.søknadIder
import no.nav.helse.person.Dokumentsporing.Companion.tilSubsumsjonsformat
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.PeriodeMedSammeSkjæringstidspunkt
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.harId
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Maksdatosituasjon
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal class Behandlinger private constructor(behandlinger: List<Behandling>) {
    internal constructor() : this(emptyList())
    companion object {
        fun gjenopprett(dto: BehandlingerInnDto, grunnlagsdata: Map<UUID, VilkårsgrunnlagElement>, utbetalinger: Map<UUID, Utbetaling>) = Behandlinger(
            behandlinger = dto.behandlinger.map { Behandling.gjenopprett(it, grunnlagsdata, utbetalinger) }
        )
    }
    private val utbetalingene get() = behandlinger.mapNotNull(Behandling::utbetaling)
    private val behandlinger = behandlinger.toMutableList()
    private val siste get() = behandlinger.lastOrNull()?.utbetaling()

    private val observatører = mutableListOf<BehandlingObserver>()

    internal fun loggOverlappendeUtbetalingerMedInfotrygd(person: Person, vedtaksperiodeId: UUID) {
        person.loggOverlappendeUtbetalingerMedInfotrygd(siste, vedtaksperiodeId)
    }

    internal fun initiellBehandling(sykmeldingsperiode: Periode, sykdomstidslinje: Sykdomstidslinje, dokumentsporing: Dokumentsporing, søknad: Søknad) {
        check(behandlinger.isEmpty())
        val behandling = Behandling.nyBehandling(this.observatører, sykdomstidslinje, dokumentsporing, sykmeldingsperiode, søknad)
        leggTilNyBehandling(behandling)
    }

    internal fun addObserver(observatør: BehandlingObserver) {
        observatører.add(observatør)
        behandlinger.forEach { it.addObserver(observatør) }
    }

    internal fun accept(visitor: BehandlingerVisitor) {
        visitor.preVisitBehandlinger(behandlinger)
        behandlinger.forEach { behandling ->
            behandling.accept(visitor)
        }
        visitor.postVisitBehandlinger(behandlinger)
    }

    internal fun sykdomstidslinje() = behandlinger.last().sykdomstidslinje()

    internal fun trekkerTilbakePenger() = siste?.trekkerTilbakePenger() == true
    internal fun utbetales() = behandlinger.any { it.erInFlight() }
    internal fun erAvsluttet() = behandlinger.last().erAvsluttet()
    internal fun erAvvist() = siste?.erAvvist() == true
    internal fun harUtbetalinger() = siste?.harUtbetalinger() == true
    internal fun erUbetalt() = siste?.erUbetalt() == true

    internal fun kanForkastes(hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) =
        behandlinger.last().kanForkastes(hendelse, arbeidsgiverUtbetalinger)
    internal fun harId(utbetalingId: UUID) = utbetalingene.harId(utbetalingId)
    internal fun håndterUtbetalinghendelse(hendelse: UtbetalingHendelse) = behandlinger.any { it.håndterUtbetalinghendelse(hendelse) }

    internal fun lagreTidsnæreInntekter(
        arbeidsgiver: Arbeidsgiver,
        skjæringstidspunkt: LocalDate,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        oppholdsperiodeMellom: Periode?
    ) {
        behandlinger.lagreTidsnæreInntekter(this, arbeidsgiver, skjæringstidspunkt, hendelse, aktivitetslogg, oppholdsperiodeMellom)
    }

    internal fun gjelderIkkeFor(hendelse: Utbetalingsavgjørelse) = siste?.gjelderFor(hendelse) != true

    internal fun erHistorikkEndretSidenBeregning(infotrygdhistorikk: Infotrygdhistorikk) =
        infotrygdhistorikk.harEndretHistorikk(siste!!)

    internal fun overlapperMed(other: Behandlinger): Boolean {
        if (!this.harUtbetalinger() || !other.harUtbetalinger()) return false
        return this.siste!!.overlapperMed(other.siste!!)
    }

    internal fun valider(simulering: Simulering) {
        siste!!.valider(simulering)
    }

    internal fun erKlarForGodkjenning() = siste?.erKlarForGodkjenning() ?: false

    internal fun simuler(hendelse: IAktivitetslogg) = siste!!.simuler(hendelse)

    internal fun godkjenning(hendelse: IAktivitetslogg, erForlengelse: Boolean, perioderMedSammeSkjæringstidspunkt: List<Pair<UUID, Behandlinger>>, kanForkastes: Boolean) {
        val behandlingerMedSammeSkjæringstidspunkt = perioderMedSammeSkjæringstidspunkt.map { it.first to it.second.behandlinger.last() }
        behandlinger.last().godkjenning(hendelse, erForlengelse, behandlingerMedSammeSkjæringstidspunkt, kanForkastes)
    }

    internal fun håndterAnnullering(arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, andreBehandlinger: List<Behandlinger>): Boolean {
        val annullering = behandlinger.last().annuller(arbeidsgiver, hendelse, this.behandlinger.toList()) ?: return false
        andreBehandlinger.forEach {
            it.kobleAnnulleringTilAndre(arbeidsgiver, hendelse, annullering)
        }
        return true
    }

    private fun kobleAnnulleringTilAndre(arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling) {
        leggTilNyBehandling(behandlinger.last().annuller(arbeidsgiver, hendelse, annullering, behandlinger.toList()))
    }

    internal fun nyUtbetaling(
        vedtaksperiodeSomLagerUtbetaling: UUID,
        fødselsnummer: String,
        arbeidsgiver: Arbeidsgiver,
        grunnlagsdata: VilkårsgrunnlagElement,
        hendelse: IAktivitetslogg,
        maksimumSykepenger: Maksdatosituasjon,
        utbetalingstidslinje: Utbetalingstidslinje
    ): Utbetalingstidslinje {
        return behandlinger.last().utbetaling(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksimumSykepenger, utbetalingstidslinje)
    }

    internal fun forkast(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse) {
        leggTilNyBehandling(behandlinger.last().forkastVedtaksperiode(arbeidsgiver, hendelse))
        behandlinger.last().forkastetBehandling(hendelse)
    }
    internal fun forkastUtbetaling(hendelse: IAktivitetslogg) {
        behandlinger.last().forkastUtbetaling(hendelse)
    }
    internal fun harIkkeUtbetaling() = behandlinger.last().harIkkeUtbetaling()


    fun vedtakFattet(arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
        this.behandlinger.last().vedtakFattet(arbeidsgiver, utbetalingsavgjørelse)
    }
    fun bekreftAvsluttetBehandlingMedVedtak(arbeidsgiver: Arbeidsgiver) {
        bekreftAvsluttetBehandling(arbeidsgiver)
        check(erFattetVedtak()) {
            "forventer at behandlingen skal ha fattet vedtak"
        }
    }
    private fun erFattetVedtak(): Boolean {
        return behandlinger.last().erFattetVedtak()
    }
    private fun bekreftAvsluttetBehandling(arbeidsgiver: Arbeidsgiver) {
        arbeidsgiver.bekreftErLåst(periode())
        check(erAvsluttet()) {
            "forventer at utbetaling skal være avsluttet"
        }
    }
    fun avsluttUtenVedtak(arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg) {
        check(behandlinger.last().utbetaling() == null) { "Forventet ikke at perioden har fått utbetaling: kun perioder innenfor arbeidsgiverperioden skal sendes hit. " }
        this.behandlinger.last().avsluttUtenVedtak(arbeidsgiver, hendelse)
        bekreftAvsluttetBehandling(arbeidsgiver)
    }

    internal fun sykmeldingsperiode() = this.behandlinger.first().sykmeldingsperiode()
    internal fun periode() = this.behandlinger.last().periode()

    // sørger for ny behandling når vedtaksperioden går ut av Avsluttet/AUU,
    // men bare hvis det ikke er laget en ny allerede fra før
    fun sikreNyBehandling(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse) {
        leggTilNyBehandling(behandlinger.last().sikreNyBehandling(arbeidsgiver, hendelse))
    }

    private fun leggTilNyBehandling(behandling: Behandling?) {
        if (behandling == null) return
        if (behandlinger.isNotEmpty())
            check(behandlinger.last().tillaterNyBehandling(behandling)) {
                "siste behandling ${behandlinger.last()} tillater ikke opprettelse av ny behandling $behandling"
            }
        this.behandlinger.add(behandling)
    }

    fun klarForUtbetaling(): Boolean {
        return behandlinger.last().klarForUtbetaling()
    }

    fun bekreftÅpenBehandling(arbeidsgiver: Arbeidsgiver) {
        arbeidsgiver.bekreftErÅpen(periode())
        check(behandlinger.last().harÅpenBehandling()) {
            "forventer at vedtaksperioden er uberegnet når den går ut av Avsluttet/AvsluttetUtenUtbetaling"
        }
    }

    internal fun jurist(jurist: MaskinellJurist, vedtaksperiodeId: UUID) =
        behandlinger.jurist(jurist, vedtaksperiodeId)

    internal fun hendelseIder() = behandlinger.dokumentsporing
    internal fun dokumentsporing() = behandlinger.dokumentsporing.ider()

    internal fun søknadIder() = behandlinger.dokumentsporing.søknadIder()
    internal fun sisteInntektsmeldingId() = behandlinger.dokumentsporing.sisteInntektsmeldingId()

    internal fun oppdaterDokumentsporing(dokument: Dokumentsporing): Boolean {
        return behandlinger.last().oppdaterDokumentsporing(dokument)
    }

    fun dokumentHåndtert(dokumentsporing: Dokumentsporing) =
        behandlinger.any { it.dokumentHåndtert(dokumentsporing) }

    fun håndterEndring(person: Person, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, validering: () -> Unit) {
        val nyBehandling = behandlinger.last().håndterEndring(arbeidsgiver, hendelse)?.also {
            leggTilNyBehandling(it)
        }
        // 🤯 <OBS! NB!> 🤯
        // spesialist er -avhengig- av at sykefraværstilfelle går ut før behandlingen kan lukkes automatisk
        // Meldingen kan dessuten ikke sendes ut før behandlingen er fysisk lagt til i listen (se leggTilNyBehandling(it) over),
        // fordi når vedtaksperioden skal håndtere sykefraværstilfelle-signalet så avhenger den at behandlingen er på plass
        person.sykdomshistorikkEndret(hendelse)
        // 🤯 </OBS! NB!> 🤯

        validering()

        nyBehandling?.vurderLukkeAutomatisk(arbeidsgiver, hendelse)
    }

    fun erUtbetaltPåForskjelligeUtbetalinger(other: Behandlinger): Boolean {
        return this.behandlinger.erUtbetaltPåForskjelligeUtbetalinger(other.behandlinger)
    }

    internal fun trengerArbeidsgiverperiode() = behandlinger.dokumentsporing.sisteInntektsmeldingId() == null

    internal class Behandlingkilde(
        val meldingsreferanseId: UUID,
        val innsendt: LocalDateTime,
        val registert: LocalDateTime,
        val avsender: Avsender
    ) {
        constructor(hendelse: Hendelse): this(hendelse.meldingsreferanseId(), hendelse.innsendt(), hendelse.registrert(), hendelse.avsender())

        internal fun accept(visitor: BehandlingerVisitor) {
            visitor.visitBehandlingkilde(meldingsreferanseId, innsendt, registert, avsender)
        }

        internal fun dto() = BehandlingkildeDto(
            meldingsreferanseId = this.meldingsreferanseId,
            innsendt = this.innsendt,
            registert = this.registert,
            avsender = avsender.dto()
        )

        internal companion object {
            fun gjenopprett(dto: BehandlingkildeDto): Behandlingkilde {
                return Behandlingkilde(
                    meldingsreferanseId = dto.meldingsreferanseId,
                    innsendt = dto.innsendt,
                    registert = dto.registert,
                    avsender = Avsender.gjenopprett(dto.avsender)
                )
            }
        }
    }


    internal class Behandling private constructor(
        private val id: UUID,
        private var tilstand: Tilstand,
        private val endringer: MutableList<Endring>,
        private var vedtakFattet: LocalDateTime?,
        private var avsluttet: LocalDateTime?,
        private val kilde: Behandlingkilde,
        observatører: List<BehandlingObserver>
    ) {
        private val observatører = observatører.toMutableList()
        private val gjeldende get() = endringer.last()
        private val periode: Periode get() = gjeldende.periode
        private val tidsstempel = endringer.first().tidsstempel
        private val dokumentsporing get() = endringer.dokumentsporing

        constructor(observatører: List<BehandlingObserver>, tilstand: Tilstand, endringer: List<Endring>, avsluttet: LocalDateTime?, kilde: Behandlingkilde) : this(UUID.randomUUID(), tilstand, endringer.toMutableList(), null, avsluttet, kilde, observatører) {
            check(observatører.isNotEmpty()) {
                "må ha minst én observatør for å registrere en behandling"
            }
            tilstand.behandlingOpprettet(this)
        }

        init {
            check(endringer.isNotEmpty()) {
                "Må ha endringer for at det skal være vits med en behandling"
            }
        }

        internal fun addObserver(observatør: BehandlingObserver) {
            check(observatører.none { it === observatør }) { "observatør finnes fra før" }
            observatører.add(observatør)
        }

        override fun toString() = "$periode - $tilstand"

        fun accept(visitor: BehandlingerVisitor) {
            visitor.preVisitBehandling(id, tidsstempel, tilstand, periode, vedtakFattet, avsluttet, kilde)
            endringer.forEach { it.accept(visitor) }
            kilde.accept(visitor)
            visitor.postVisitBehandling(id, tidsstempel, tilstand, periode, vedtakFattet, avsluttet, kilde)
        }

        fun sykmeldingsperiode() = endringer.first().sykmeldingsperiode
        fun periode() = periode

        // TODO: se på om det er nødvendig å støtte Dokumentsporing som et sett; eventuelt om Behandling må ha et sett
        class Endring constructor(
            private val id: UUID,
            val tidsstempel: LocalDateTime,
            val sykmeldingsperiode: Periode,
            val periode: Periode,
            val grunnlagsdata: VilkårsgrunnlagElement?,
            val utbetaling: Utbetaling?,
            val dokumentsporing: Dokumentsporing,
            val sykdomstidslinje: Sykdomstidslinje,
        ) {

            internal constructor(
                grunnlagsdata: VilkårsgrunnlagElement?,
                utbetaling: Utbetaling?,
                dokumentsporing: Dokumentsporing,
                sykdomstidslinje: Sykdomstidslinje,
                sykmeldingsperiode: Periode,
                periode: Periode
            ) : this(UUID.randomUUID(), LocalDateTime.now(), sykmeldingsperiode, periode, grunnlagsdata, utbetaling, dokumentsporing, sykdomstidslinje)

            companion object {
                val List<Endring>.dokumentsporing get() = map { it.dokumentsporing }.toSet()
                fun gjenopprett(dto: BehandlingendringInnDto, grunnlagsdata: Map<UUID, VilkårsgrunnlagElement>, utbetalinger: Map<UUID, Utbetaling>): Endring {
                    return Endring(
                        id = dto.id,
                        tidsstempel = dto.tidsstempel,
                        sykmeldingsperiode = Periode.gjenopprett(dto.sykmeldingsperiode),
                        periode = Periode.gjenopprett(dto.periode),
                        grunnlagsdata = dto.vilkårsgrunnlagId?.let { grunnlagsdata.getValue(it) },
                        utbetaling = dto.utbetalingId?.let { utbetalinger.getValue(it) },
                        dokumentsporing = Dokumentsporing.gjenopprett(dto.dokumentsporing),
                        sykdomstidslinje = Sykdomstidslinje.gjenopprett(dto.sykdomstidslinje)
                    )
                }
            }

            override fun toString() = "$periode - $dokumentsporing - ${sykdomstidslinje.toShortString()}${utbetaling?.let { " - $it" } ?: ""}"

            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other !is Endring) return false
                return this.dokumentsporing == other.dokumentsporing
            }

            internal fun accept(visitor: BehandlingerVisitor) {
                visitor.preVisitBehandlingendring(id, tidsstempel, sykmeldingsperiode, periode, grunnlagsdata, utbetaling, dokumentsporing, sykdomstidslinje)
                grunnlagsdata?.accept(visitor)
                utbetaling?.accept(visitor)
                sykdomstidslinje.accept(visitor)
                visitor.postVisitBehandlingendring(id, tidsstempel, sykmeldingsperiode, periode, grunnlagsdata, utbetaling, dokumentsporing, sykdomstidslinje)
            }

            internal fun kopierMedEndring(periode: Periode, dokument: Dokumentsporing, sykdomstidslinje: Sykdomstidslinje) = Endring(
                grunnlagsdata = null,
                utbetaling = null,
                dokumentsporing = dokument,
                sykdomstidslinje = sykdomstidslinje,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = periode
            )
            internal fun kopierUtenUtbetaling() = Endring(
                grunnlagsdata = null,
                utbetaling = null,
                dokumentsporing = this.dokumentsporing,
                sykdomstidslinje = this.sykdomstidslinje,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode
            )
            internal fun kopierMedUtbetaling(utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement) = Endring(
                grunnlagsdata = grunnlagsdata,
                utbetaling = utbetaling,
                dokumentsporing = this.dokumentsporing,
                sykdomstidslinje = this.sykdomstidslinje,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode
            )
            internal fun kopierDokument(dokument: Dokumentsporing) = Endring(
                grunnlagsdata = this.grunnlagsdata,
                utbetaling = this.utbetaling,
                dokumentsporing = dokument,
                sykdomstidslinje = this.sykdomstidslinje,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode
            )

            fun lagreTidsnæreInntekter(
                nyttSkjæringstidspunkt: LocalDate,
                arbeidsgiver: Arbeidsgiver,
                hendelse: IAktivitetslogg,
                oppholdsperiodeMellom: Periode?
            ) {
                grunnlagsdata?.lagreTidsnæreInntekter(nyttSkjæringstidspunkt, arbeidsgiver, hendelse, oppholdsperiodeMellom)
            }

            fun forkastUtbetaling(hendelse: IAktivitetslogg) {
                utbetaling?.forkast(hendelse)
            }

            fun godkjenning(hendelse: IAktivitetslogg, erForlengelse: Boolean, kanForkastes: Boolean, behandlingId: UUID, perioderMedSammeSkjæringstidspunkt: List<Triple<UUID, UUID, Periode>>) {
                checkNotNull(utbetaling) { "Forventet ikke manglende utbetaling ved godkjenningsbehov" }
                checkNotNull(grunnlagsdata) { "Forventet ikke manglende vilkårsgrunnlag ved godkjennignsbehov" }
                val builder = GodkjenningsbehovBuilder(erForlengelse, kanForkastes, periode, behandlingId, perioderMedSammeSkjæringstidspunkt.map { (vedtaksperiodeId, behandlingId, periode) ->
                    PeriodeMedSammeSkjæringstidspunkt(vedtaksperiodeId, behandlingId, periode)
                })
                grunnlagsdata.byggGodkjenningsbehov(builder)
                utbetaling.byggGodkjenningsbehov(hendelse, periode, builder)
                Aktivitet.Behov.godkjenning(
                    aktivitetslogg = hendelse,
                    builder = builder
                )
            }
            internal fun dto(): BehandlingendringUtDto {
                val vilkårsgrunnlagUtDto = this.grunnlagsdata?.dto()
                val utbetalingUtDto = this.utbetaling?.dto()
                return BehandlingendringUtDto(
                    id = this.id,
                    tidsstempel = this.tidsstempel,
                    sykmeldingsperiode = this.sykmeldingsperiode.dto(),
                    periode = this.periode.dto(),
                    vilkårsgrunnlagId = vilkårsgrunnlagUtDto?.vilkårsgrunnlagId,
                    skjæringstidspunkt = vilkårsgrunnlagUtDto?.skjæringstidspunkt,
                    utbetalingId = utbetalingUtDto?.id,
                    utbetalingstatus = utbetalingUtDto?.tilstand,
                    dokumentsporing = this.dokumentsporing.dto(),
                    sykdomstidslinje = this.sykdomstidslinje.dto()
                )
            }
        }

        internal fun sykdomstidslinje() = endringer.last().sykdomstidslinje

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is Behandling) return false
            return this.tilstand == other.tilstand && this.dokumentsporing == other.dokumentsporing
        }

        override fun hashCode(): Int {
            return this.dokumentsporing.hashCode()
        }

        internal fun erFattetVedtak() = vedtakFattet != null
        internal fun erInFlight() = erFattetVedtak() && !erAvsluttet()
        internal fun erAvsluttet() = avsluttet != null

        internal fun klarForUtbetaling() = this.tilstand in setOf(Tilstand.Uberegnet, Tilstand.UberegnetOmgjøring, Tilstand.UberegnetRevurdering)
        internal fun harÅpenBehandling() = this.tilstand in setOf(Tilstand.UberegnetRevurdering, Tilstand.UberegnetOmgjøring, Tilstand.AnnullertPeriode, Tilstand.TilInfotrygd)
        internal fun harIkkeUtbetaling() = this.tilstand in setOf(Tilstand.Uberegnet, Tilstand.UberegnetOmgjøring, Tilstand.TilInfotrygd)

        internal fun vedtakFattet(arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
            if (utbetalingsavgjørelse.avvist) return tilstand.vedtakAvvist(this, arbeidsgiver, utbetalingsavgjørelse)
            tilstand.vedtakFattet(this, arbeidsgiver, utbetalingsavgjørelse)
        }

        internal fun avsluttUtenVedtak(arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg) {
            tilstand.avsluttUtenVedtak(this, arbeidsgiver, hendelse)
        }

        internal fun forkastVedtaksperiode(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
            return tilstand.forkastVedtaksperiode(this, arbeidsgiver, hendelse)
        }

        private fun tilstand(nyTilstand: Tilstand, hendelse: IAktivitetslogg) {
            tilstand.leaving(this)
            tilstand = nyTilstand
            tilstand.entering(this, hendelse)
        }

        fun forkastUtbetaling(hendelse: IAktivitetslogg) {
            tilstand.utenUtbetaling(this, hendelse)
        }

        fun utbetaling() = gjeldende.utbetaling

        fun utbetaling(
            vedtaksperiodeSomLagerUtbetaling: UUID,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            grunnlagsdata: VilkårsgrunnlagElement,
            hendelse: IAktivitetslogg,
            maksimumSykepenger: Maksdatosituasjon,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            return tilstand.utbetaling(this, vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksimumSykepenger, utbetalingstidslinje)
        }

        private fun håndterAnnullering(arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling): Utbetaling? {
            val utbetaling = checkNotNull(gjeldende.utbetaling) { "forventer å ha en tidligere utbetaling" }
            return arbeidsgiver.nyAnnullering(hendelse, utbetaling)
        }

        private fun lagOmgjøring(
            vedtaksperiodeSomLagerUtbetaling: UUID,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            grunnlagsdata: VilkårsgrunnlagElement,
            hendelse: IAktivitetslogg,
            maksimumSykepenger: Maksdatosituasjon,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            val strategi = Arbeidsgiver::lagUtbetaling
            return lagUtbetaling(
                vedtaksperiodeSomLagerUtbetaling,
                fødselsnummer,
                arbeidsgiver,
                grunnlagsdata,
                hendelse,
                maksimumSykepenger,
                utbetalingstidslinje,
                strategi,
                Tilstand.BeregnetOmgjøring
            )
        }
        private fun lagUtbetaling(
            vedtaksperiodeSomLagerUtbetaling: UUID,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            grunnlagsdata: VilkårsgrunnlagElement,
            hendelse: IAktivitetslogg,
            maksimumSykepenger: Maksdatosituasjon,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            val strategi = Arbeidsgiver::lagUtbetaling
            return lagUtbetaling(
                vedtaksperiodeSomLagerUtbetaling,
                fødselsnummer,
                arbeidsgiver,
                grunnlagsdata,
                hendelse,
                maksimumSykepenger,
                utbetalingstidslinje,
                strategi,
                Tilstand.Beregnet
            )
        }
        private fun lagRevurdering(
            vedtaksperiodeSomLagerUtbetaling: UUID,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            grunnlagsdata: VilkårsgrunnlagElement,
            hendelse: IAktivitetslogg,
            maksimumSykepenger: Maksdatosituasjon,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            val strategi = Arbeidsgiver::lagRevurdering
            return lagUtbetaling(
                vedtaksperiodeSomLagerUtbetaling,
                fødselsnummer,
                arbeidsgiver,
                grunnlagsdata,
                hendelse,
                maksimumSykepenger,
                utbetalingstidslinje,
                strategi,
                Tilstand.BeregnetRevurdering
            )
        }
        private fun lagUtbetaling(
            vedtaksperiodeSomLagerUtbetaling: UUID,
            fødselsnummer: String,
            arbeidsgiver: Arbeidsgiver,
            grunnlagsdata: VilkårsgrunnlagElement,
            hendelse: IAktivitetslogg,
            maksimumSykepenger: Maksdatosituasjon,
            utbetalingstidslinje: Utbetalingstidslinje,
            strategi: (Arbeidsgiver, aktivitetslogg: IAktivitetslogg, fødselsnummer: String, utbetalingstidslinje: Utbetalingstidslinje, maksdato: LocalDate, forbrukteSykedager: Int, gjenståendeSykedager: Int, periode: Periode) -> Utbetaling,
            nyTilstand: Tilstand
        ): Utbetalingstidslinje {
            val denNyeUtbetalingen = strategi(arbeidsgiver, hendelse, fødselsnummer, utbetalingstidslinje, maksimumSykepenger.maksdato, maksimumSykepenger.forbrukteDager, maksimumSykepenger.gjenståendeDager, periode)
            denNyeUtbetalingen.nyVedtaksperiodeUtbetaling(vedtaksperiodeSomLagerUtbetaling)
            nyEndring(gjeldende.kopierMedUtbetaling(denNyeUtbetalingen, grunnlagsdata))
            tilstand(nyTilstand, hendelse)
            return utbetalingstidslinje.subset(periode)
        }

        fun dokumentHåndtert(dokumentsporing: Dokumentsporing) =
            dokumentsporing in this.dokumentsporing

        internal fun oppdaterDokumentsporing(dokument: Dokumentsporing): Boolean {
            return tilstand.oppdaterDokumentsporing(this, dokument)
        }

        private fun kopierMedDokument(dokument: Dokumentsporing): Boolean {
            if (gjeldende.dokumentsporing == dokument) return false
            nyEndring(gjeldende.kopierDokument(dokument))
            return true
        }

        private fun utenUtbetaling(hendelse: IAktivitetslogg) {
            gjeldende.utbetaling!!.forkast(hendelse)
            nyEndring(gjeldende.kopierUtenUtbetaling())
        }

        private fun nyEndring(endring: Endring?) {
            if (endring == null) return
            endringer.add(endring)
        }

        fun håndterEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling? {
            return tilstand.håndterEndring(this, arbeidsgiver, hendelse)
        }

        fun vurderLukkeAutomatisk(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) {
            return tilstand.vurderLukkeAutomatisk(this, arbeidsgiver, hendelse)
        }
        private fun håndtereEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Endring {
            val oppdatertPeriode = hendelse.oppdaterFom(endringer.last().periode)
            val sykdomstidslinje = arbeidsgiver.oppdaterSykdom(hendelse).subset(oppdatertPeriode)
            return endringer.last().kopierMedEndring(oppdatertPeriode, hendelse.dokumentsporing(), sykdomstidslinje)
        }

        // oppdaterer seg selv med endringen
        private fun oppdaterMedEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) {
            val endring = håndtereEndring(arbeidsgiver, hendelse)
            if (endring == gjeldende) return
            nyEndring(endring)
        }
        private fun nyBehandlingMedEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, starttilstand: Tilstand = Tilstand.Uberegnet): Behandling {
            arbeidsgiver.låsOpp(periode)
            return Behandling(
                observatører = this.observatører,
                tilstand = starttilstand,
                endringer = listOf(håndtereEndring(arbeidsgiver, hendelse)),
                avsluttet = null,
                kilde = Behandlingkilde(hendelse)
            )
        }

        private fun sikreNyBehandling(arbeidsgiver: Arbeidsgiver, starttilstand: Tilstand, hendelse: Hendelse): Behandling {
            arbeidsgiver.låsOpp(periode)
            return Behandling(
                observatører = this.observatører,
                tilstand = starttilstand,
                endringer = listOf(endringer.last().kopierUtenUtbetaling()),
                avsluttet = null,
                kilde = Behandlingkilde(hendelse)
            )
        }

        private fun nyAnnullertBehandling(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling {
            arbeidsgiver.låsOpp(periode)
            return Behandling(
                observatører = this.observatører,
                tilstand = Tilstand.AnnullertPeriode,
                endringer = listOf(this.gjeldende.kopierMedUtbetaling(annullering, grunnlagsdata)),
                avsluttet = LocalDateTime.now(),
                kilde = Behandlingkilde(hendelse)
            )
        }

        fun sikreNyBehandling(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
            return tilstand.sikreNyBehandling(this, arbeidsgiver, hendelse)
        }

        fun tillaterNyBehandling(other: Behandling): Boolean {
            return tilstand.tillaterNyBehandling(this, other)
        }

        fun håndterUtbetalinghendelse(hendelse: UtbetalingHendelse): Boolean {
            return tilstand.håndterUtbetalinghendelse(this, hendelse)
        }

        fun kanForkastes(hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
            return tilstand.kanForkastes(this, hendelse, arbeidsgiverUtbetalinger)
        }
        private fun behandlingLukket(arbeidsgiver: Arbeidsgiver, ) {
            arbeidsgiver.lås(periode)
            check(observatører.isNotEmpty()) { "behandlingen har ingen registrert observatør" }
            observatører.forEach { it.behandlingLukket(id) }
        }
        private fun vedtakIverksatt(hendelse: IAktivitetslogg) {
            check(observatører.isNotEmpty()) { "behandlingen har ingen registrert observatør" }
            observatører.forEach { it.vedtakIverksatt(hendelse, id, avsluttet!!, periode, dokumentsporing.ider(), utbetaling()!!.id, vedtakFattet!!, gjeldende.grunnlagsdata!!) }
        }

        private fun avsluttetUtenVedtak(hendelse: IAktivitetslogg) {
            check(observatører.isNotEmpty()) { "behandlingen har ingen registrert observatør" }
            observatører.forEach { it.avsluttetUtenVedtak(hendelse, id, avsluttet!!, periode, dokumentsporing.ider()) }
        }

        private fun emitNyBehandlingOpprettet(type: PersonObserver.BehandlingOpprettetEvent.Type) {
            check(observatører.isNotEmpty()) { "behandlingen har ingen registrert observatør" }
            observatører.forEach { it.nyBehandling(id, periode, kilde.meldingsreferanseId, kilde.innsendt, kilde.registert, kilde.avsender, type) }
        }

        internal fun forkastetBehandling(hendelse: Hendelse) {
            check(observatører.isNotEmpty()) { "behandlingen har ingen registrert observatør" }
            check(this.tilstand in setOf(Tilstand.TilInfotrygd, Tilstand.AnnullertPeriode))
            observatører.forEach { it.behandlingForkastet(id, hendelse) }
        }

        internal fun vedtakAnnullert(hendelse: IAktivitetslogg) {
            check(observatører.isNotEmpty()) { "behandlingen har ingen registrert observatør" }
            check(this.tilstand === Tilstand.AnnullertPeriode)
            observatører.forEach { it.vedtakAnnullert(hendelse, id) }
        }

        internal fun godkjenning(hendelse: IAktivitetslogg, erForlengelse: Boolean, behandlingerMedSammeSkjæringstidspunkt: List<Pair<UUID, Behandling>>, kanForkastes: Boolean) {
            val perioderMedSammeSkjæringstidspunkt = behandlingerMedSammeSkjæringstidspunkt.map { Triple(it.first, it.second.id, it.second.periode) }
            gjeldende.godkjenning(hendelse, erForlengelse, kanForkastes, id, perioderMedSammeSkjæringstidspunkt)
        }

        fun annuller(arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, behandlinger: List<Behandling>): Utbetaling? {
            val sisteVedtak = behandlinger.lastOrNull { it.erFattetVedtak() } ?: return null
            return sisteVedtak.håndterAnnullering(arbeidsgiver, hendelse)
        }
        fun annuller(arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, andreBehandlinger: List<Behandling>): Behandling? {
            val sisteVedtak = andreBehandlinger.lastOrNull { behandlingen -> behandlingen.erFattetVedtak() } ?: return null
            if (true != sisteVedtak.utbetaling()?.hørerSammen(annullering)) return null
            return tilstand.annuller(this, arbeidsgiver, hendelse, annullering, checkNotNull(sisteVedtak.gjeldende.grunnlagsdata))
        }

        private fun tillaterOverlappendeUtbetalingerForkasting(hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
            val overlappendeUtbetalinger = arbeidsgiverUtbetalinger.filter { it.overlapperMed(periode) }
            return Utbetaling.kanForkastes(overlappendeUtbetalinger, arbeidsgiverUtbetalinger).also {
                if (!it) hendelse.info("[kanForkastes] Kan i utgangspunktet ikke forkastes ettersom perioden har ${overlappendeUtbetalinger.size} overlappende utbetalinger")
            }
        }
        /* hvorvidt en AUU- (eller har vært-auu)-periode kan forkastes */
        private fun kanForkastingAvKortPeriodeTillates(hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
            return tillaterOverlappendeUtbetalingerForkasting(hendelse, arbeidsgiverUtbetalinger)
        }

        /*
enum class Periodetilstand {
    TilUtbetaling,
    TilAnnullering,
    Utbetalt,
    Annullert,
    AnnulleringFeilet,
    RevurderingFeilet,
    ForberederGodkjenning,
    ManglerInformasjon,
    UtbetaltVenterPåAnnenPeriode,
    VenterPåAnnenPeriode,
    TilGodkjenning,
    IngenUtbetaling,
    TilInfotrygd;
}
         */

        internal companion object {
            val List<Behandling>.sykmeldingsperiode get() = first().periode
            val List<Behandling>.dokumentsporing get() = map { it.dokumentsporing }.reduce(Set<Dokumentsporing>::plus)

            fun nyBehandling(observatører: List<BehandlingObserver>, sykdomstidslinje: Sykdomstidslinje, dokumentsporing: Dokumentsporing, sykmeldingsperiode: Periode, søknad: Søknad) =
                Behandling(
                    observatører = observatører,
                    tilstand = Tilstand.Uberegnet,
                    endringer = listOf(
                        Endring(
                            grunnlagsdata = null,
                            utbetaling = null,
                            dokumentsporing = dokumentsporing,
                            sykdomstidslinje = sykdomstidslinje,
                            sykmeldingsperiode = sykmeldingsperiode,
                            periode = checkNotNull(sykdomstidslinje.periode()) { "kan ikke opprette behandling på tom sykdomstidslinje" }
                        )
                    ),
                    avsluttet = null,
                    kilde = Behandlingkilde(søknad)
                )
            fun List<Behandling>.jurist(jurist: MaskinellJurist, vedtaksperiodeId: UUID) =
                jurist.medVedtaksperiode(vedtaksperiodeId, dokumentsporing.tilSubsumsjonsformat(), sykmeldingsperiode)

            fun List<Behandling>.lagreTidsnæreInntekter(behandlinger: Behandlinger, arbeidsgiver: Arbeidsgiver, skjæringstidspunkt: LocalDate, hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, oppholdsperiodeMellom: Periode?) {
                val sisteEndringMedUtbetaling = asReversed().firstNotNullOfOrNull { it.endringer.lastOrNull { it.utbetaling != null } } ?: return
                behandlinger.sikreNyBehandling(arbeidsgiver, hendelse)
                return sisteEndringMedUtbetaling.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, aktivitetslogg, oppholdsperiodeMellom)
            }

            // hvorvidt man delte samme utbetaling før
            fun List<Behandling>.erUtbetaltPåForskjelligeUtbetalinger(other: List<Behandling>): Boolean {
                val forrigeIverksatteThis = forrigeIverksatte ?: return true
                val forrigeIverksatteOther = other.forrigeIverksatte ?: return true
                // hvis forrige iverksatte på *this* har ulik korrelasjonsId som siste iverksatte på *other* -> return true
                val utbetalingThis = checkNotNull(forrigeIverksatteThis.utbetaling()) {
                    "forventer at det skal være en utbetaling på en behandling som er iverksatt"
                }
                val utbetalingOther = forrigeIverksatteOther.utbetaling() ?: return true // forrige periode kan være AUU
                return !utbetalingOther.hørerSammen(utbetalingThis)
            }

            private val List<Behandling>.forrigeIverksatte get() = lastOrNull { it.vedtakFattet != null }

            internal fun gjenopprett(dto: BehandlingInnDto, grunnlagsdata: Map<UUID, VilkårsgrunnlagElement>, utbetalinger: Map<UUID, Utbetaling>): Behandling {
                return Behandling(
                    id = dto.id,
                    tilstand = when (dto.tilstand) {
                        BehandlingtilstandDto.ANNULLERT_PERIODE -> Tilstand.AnnullertPeriode
                        BehandlingtilstandDto.AVSLUTTET_UTEN_VEDTAK -> Tilstand.AvsluttetUtenVedtak
                        BehandlingtilstandDto.BEREGNET -> Tilstand.Beregnet
                        BehandlingtilstandDto.BEREGNET_OMGJØRING -> Tilstand.BeregnetOmgjøring
                        BehandlingtilstandDto.BEREGNET_REVURDERING -> Tilstand.BeregnetRevurdering
                        BehandlingtilstandDto.REVURDERT_VEDTAK_AVVIST -> Tilstand.RevurdertVedtakAvvist
                        BehandlingtilstandDto.TIL_INFOTRYGD -> Tilstand.TilInfotrygd
                        BehandlingtilstandDto.UBEREGNET -> Tilstand.Uberegnet
                        BehandlingtilstandDto.UBEREGNET_OMGJØRING -> Tilstand.UberegnetOmgjøring
                        BehandlingtilstandDto.UBEREGNET_REVURDERING -> Tilstand.UberegnetRevurdering
                        BehandlingtilstandDto.VEDTAK_FATTET -> Tilstand.VedtakFattet
                        BehandlingtilstandDto.VEDTAK_IVERKSATT -> Tilstand.VedtakIverksatt
                    },
                    endringer = dto.endringer.map { Endring.gjenopprett(it, grunnlagsdata, utbetalinger) }.toMutableList(),
                    vedtakFattet = dto.vedtakFattet,
                    avsluttet = dto.avsluttet,
                    kilde = Behandlingkilde.gjenopprett(dto.kilde),
                    observatører = emptyList()
                )
            }
        }
        internal sealed interface Tilstand {
            fun behandlingOpprettet(behandling: Behandling) {
                error("Forventer ikke å opprette behandling i tilstand ${this.javaClass.simpleName}")
            }
            fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {}
            fun leaving(behandling: Behandling) {}
            fun annuller(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling? {
                return null
            }
            fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
                behandling.tilstand(TilInfotrygd, hendelse)
                return null
            }
            fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling? {
                error("Har ikke implementert håndtering av endring i $this")
            }
            fun vurderLukkeAutomatisk(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) {}
            fun vedtakAvvist(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                error("Kan ikke avvise vedtak for behandling i $this")
            }
            fun vedtakFattet(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                error("Kan ikke fatte vedtak for behandling i $this")
            }
            fun avsluttUtenVedtak(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg) {
                error("Kan ikke avslutte uten vedtak for behandling i $this")
            }
            fun avsluttMedVedtak(behandling: Behandling, hendelse: IAktivitetslogg) {
                error("Kan ikke avslutte behandling i $this")
            }
            fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {
                error("Støtter ikke å forkaste utbetaling utbetaling i $this")
            }
            fun utbetaling(
                behandling: Behandling,
                vedtaksperiodeSomLagerUtbetaling: UUID,
                fødselsnummer: String,
                arbeidsgiver: Arbeidsgiver,
                grunnlagsdata: VilkårsgrunnlagElement,
                hendelse: IAktivitetslogg,
                maksimumSykepenger: Maksdatosituasjon,
                utbetalingstidslinje: Utbetalingstidslinje
            ): Utbetalingstidslinje {
                error("Støtter ikke å opprette utbetaling i $this")
            }

            fun oppdaterDokumentsporing(behandling: Behandling, dokument: Dokumentsporing): Boolean {
                error("Støtter ikke å oppdatere dokumentsporing med $dokument i $this")
            }

            fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean
            fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
                return null
            }
            fun tillaterNyBehandling(behandling: Behandling, other: Behandling): Boolean = false
            fun håndterUtbetalinghendelse(behandling: Behandling, hendelse: UtbetalingHendelse) = false

            data object Uberegnet : Tilstand {
                override fun behandlingOpprettet(behandling: Behandling) = behandling.emitNyBehandlingOpprettet(PersonObserver.BehandlingOpprettetEvent.Type.Søknad)
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    check(behandling.utbetaling() == null) { "skal ikke ha utbetaling og være uberegnet samtidig" }
                }

                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = true

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling? {
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse)
                    return null
                }

                override fun oppdaterDokumentsporing(behandling: Behandling, dokument: Dokumentsporing) =
                    behandling.kopierMedDokument(dokument)

                override fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {}

                override fun utbetaling(
                    behandling: Behandling,
                    vedtaksperiodeSomLagerUtbetaling: UUID,
                    fødselsnummer: String,
                    arbeidsgiver: Arbeidsgiver,
                    grunnlagsdata: VilkårsgrunnlagElement,
                    hendelse: IAktivitetslogg,
                    maksimumSykepenger: Maksdatosituasjon,
                    utbetalingstidslinje: Utbetalingstidslinje
                ): Utbetalingstidslinje {
                    return behandling.lagUtbetaling(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksimumSykepenger, utbetalingstidslinje)
                }

                override fun avsluttUtenVedtak(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg) {
                    behandling.behandlingLukket(arbeidsgiver)
                    behandling.tilstand(AvsluttetUtenVedtak, hendelse)
                }
            }
            data object UberegnetOmgjøring : Tilstand by (Uberegnet) {
                override fun behandlingOpprettet(behandling: Behandling) = behandling.emitNyBehandlingOpprettet(PersonObserver.BehandlingOpprettetEvent.Type.Omgjøring)
                override fun utbetaling(
                    behandling: Behandling,
                    vedtaksperiodeSomLagerUtbetaling: UUID,
                    fødselsnummer: String,
                    arbeidsgiver: Arbeidsgiver,
                    grunnlagsdata: VilkårsgrunnlagElement,
                    hendelse: IAktivitetslogg,
                    maksimumSykepenger: Maksdatosituasjon,
                    utbetalingstidslinje: Utbetalingstidslinje
                ): Utbetalingstidslinje {
                    return behandling.lagOmgjøring(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksimumSykepenger, utbetalingstidslinje)
                }

                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    behandling.kanForkastingAvKortPeriodeTillates(hendelse, arbeidsgiverUtbetalinger)

                override fun vurderLukkeAutomatisk(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) {
                    if (!kanLukkesUtenVedtak(arbeidsgiver, behandling)) return
                    behandling.avsluttUtenVedtak(arbeidsgiver, hendelse)
                }

                private fun kanLukkesUtenVedtak(arbeidsgiver: Arbeidsgiver, behandling: Behandling): Boolean {
                    val arbeidsgiverperiode = arbeidsgiver.arbeidsgiverperiode(behandling.periode) ?: return true
                    val forventerInntekt = Arbeidsgiverperiode.forventerInntekt(arbeidsgiverperiode, behandling.periode, behandling.sykdomstidslinje(), NullObserver)
                    return !forventerInntekt
                }
            }
            data object UberegnetRevurdering : Tilstand by (Uberegnet) {
                override fun behandlingOpprettet(behandling: Behandling) = behandling.emitNyBehandlingOpprettet(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering)
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = false
                override fun annuller(
                    behandling: Behandling,
                    arbeidsgiver: Arbeidsgiver,
                    hendelse: AnnullerUtbetaling,
                    annullering: Utbetaling,
                    grunnlagsdata: VilkårsgrunnlagElement
                ): Behandling? {
                    behandling.nyEndring(behandling.gjeldende.kopierMedUtbetaling(annullering, grunnlagsdata))
                    behandling.tilstand(AnnullertPeriode, hendelse)
                    return null
                }

                override fun utbetaling(
                    behandling: Behandling,
                    vedtaksperiodeSomLagerUtbetaling: UUID,
                    fødselsnummer: String,
                    arbeidsgiver: Arbeidsgiver,
                    grunnlagsdata: VilkårsgrunnlagElement,
                    hendelse: IAktivitetslogg,
                    maksimumSykepenger: Maksdatosituasjon,
                    utbetalingstidslinje: Utbetalingstidslinje
                ): Utbetalingstidslinje {
                    return behandling.lagRevurdering(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksimumSykepenger, utbetalingstidslinje)
                }
            }
            data object Beregnet : Tilstand {
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    checkNotNull(behandling.gjeldende.utbetaling)
                    checkNotNull(behandling.gjeldende.grunnlagsdata)
                }

                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = true

                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    return super.forkastVedtaksperiode(behandling, arbeidsgiver, hendelse)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse)
                    behandling.tilstand(Uberegnet, hendelse)
                    return null
                }

                override fun oppdaterDokumentsporing(behandling: Behandling, dokument: Dokumentsporing) =
                    behandling.kopierMedDokument(dokument)

                override fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.utenUtbetaling(hendelse)
                    behandling.tilstand(Uberegnet, hendelse)
                }

                override fun vedtakAvvist(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                    // perioden kommer til å bli kastet til infotrygd
                }

                override fun vedtakFattet(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                    behandling.vedtakFattet = utbetalingsavgjørelse.avgjørelsestidspunkt
                    behandling.behandlingLukket(arbeidsgiver)
                    behandling.tilstand(if (behandling.gjeldende.utbetaling?.erAvsluttet() == true) VedtakIverksatt else VedtakFattet, utbetalingsavgjørelse)
                }
            }
            data object BeregnetOmgjøring : Tilstand by (Beregnet) {
                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse)
                    behandling.tilstand(UberegnetOmgjøring, hendelse)
                    return null
                }
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    behandling.kanForkastingAvKortPeriodeTillates(hendelse, arbeidsgiverUtbetalinger)
                override fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.utenUtbetaling(hendelse)
                    behandling.tilstand(UberegnetOmgjøring, hendelse)
                }
            }
            data object BeregnetRevurdering : Tilstand by (Beregnet) {
                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    return super.forkastVedtaksperiode(behandling, arbeidsgiver, hendelse)
                }
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = false

                override fun annuller(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling? {
                    behandling.gjeldende.utbetaling!!.forkast(hendelse)
                    behandling.nyEndring(behandling.gjeldende.kopierMedUtbetaling(annullering, grunnlagsdata))
                    behandling.tilstand(AnnullertPeriode, hendelse)
                    return null
                }

                override fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.utenUtbetaling(hendelse)
                    behandling.tilstand(UberegnetRevurdering, hendelse)
                }
                override fun vedtakAvvist(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                    behandling.behandlingLukket(arbeidsgiver)
                    behandling.tilstand(RevurdertVedtakAvvist, utbetalingsavgjørelse)
                }
                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse)
                    behandling.tilstand(UberegnetRevurdering, hendelse)
                    return null
                }
            }
            data object RevurdertVedtakAvvist : Tilstand {
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = false

                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    error("Kan ikke forkaste i tilstand ${this.javaClass.simpleName}")
                }

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetRevurdering, hendelse)
                }

                override fun annuller(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling {
                    return behandling.nyAnnullertBehandling(arbeidsgiver, hendelse, annullering, grunnlagsdata)
                }
                override fun tillaterNyBehandling(behandling: Behandling, other: Behandling): Boolean {
                    return true
                }
            }
            data object VedtakFattet : Tilstand {
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    checkNotNull(behandling.gjeldende.utbetaling)
                    checkNotNull(behandling.gjeldende.grunnlagsdata)
                }
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = false

                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
                    error("Kan ikke forkaste i tilstand ${this.javaClass.simpleName}")
                }

                override fun tillaterNyBehandling(behandling: Behandling, other: Behandling): Boolean {
                    return true
                }

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetRevurdering, hendelse)
                }

                override fun håndterUtbetalinghendelse(behandling: Behandling, hendelse: UtbetalingHendelse): Boolean {
                    val utbetaling = checkNotNull(behandling.gjeldende.utbetaling) { "forventer utbetaling" }
                    if (!utbetaling.gjelderFor(hendelse)) return false
                    if (utbetaling.erAvsluttet()) avsluttMedVedtak(behandling, hendelse)
                    return true
                }

                override fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {}

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) =
                    behandling.nyBehandlingMedEndring(arbeidsgiver, hendelse, UberegnetRevurdering)

                override fun avsluttMedVedtak(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.tilstand(VedtakIverksatt, hendelse)
                }
            }

            data object AvsluttetUtenVedtak : Tilstand {
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.vedtakFattet = null // det fattes ikke vedtak i AUU
                    behandling.avsluttet = LocalDateTime.now()
                    behandling.avsluttetUtenVedtak(hendelse)
                }
                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    arbeidsgiver.låsOpp(behandling.periode)
                    return Behandling(
                        observatører = behandling.observatører,
                        tilstand = TilInfotrygd,
                        endringer = listOf(behandling.gjeldende.kopierUtenUtbetaling()),
                        avsluttet = LocalDateTime.now(),
                        kilde = Behandlingkilde(hendelse)
                    )
                }
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    behandling.kanForkastingAvKortPeriodeTillates(hendelse, arbeidsgiverUtbetalinger)

                override fun tillaterNyBehandling(behandling: Behandling, other: Behandling): Boolean {
                    return true
                }

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetOmgjøring, hendelse)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Behandling {
                    return behandling.nyBehandlingMedEndring(arbeidsgiver, hendelse, UberegnetOmgjøring)
                }
            }
            data object VedtakIverksatt : Tilstand {
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.avsluttet = LocalDateTime.now()
                    behandling.vedtakIverksatt(hendelse)
                }
                override fun annuller(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling {
                    return behandling.nyAnnullertBehandling(arbeidsgiver, hendelse, annullering, grunnlagsdata)
                }
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = false

                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    error("Kan ikke forkaste i tilstand ${this.javaClass.simpleName}")
                }
                override fun tillaterNyBehandling(behandling: Behandling, other: Behandling): Boolean {
                    return true
                }

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetRevurdering, hendelse)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) =
                    behandling.nyBehandlingMedEndring(arbeidsgiver, hendelse, UberegnetRevurdering)
            }
            data object AnnullertPeriode : Tilstand {
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.avsluttet = LocalDateTime.now()
                }

                override fun behandlingOpprettet(behandling: Behandling) = behandling.emitNyBehandlingOpprettet(PersonObserver.BehandlingOpprettetEvent.Type.Revurdering)
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = true

                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling? {
                    behandling.vedtakAnnullert(hendelse)
                    return null
                }

                override fun annuller(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling? {
                    error("forventer ikke å annullere i $this")
                }
            }
            data object TilInfotrygd : Tilstand {
                override fun behandlingOpprettet(behandling: Behandling) = behandling.emitNyBehandlingOpprettet(PersonObserver.BehandlingOpprettetEvent.Type.Omgjøring)
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    behandling.avsluttet = LocalDateTime.now()
                }
                override fun annuller(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling? {
                    error("forventer ikke å annullere i $this")
                }
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
                    error("forventer ikke å forkaste en periode som allerde er i $this")
                }
            }
        }

        internal fun dto() = BehandlingUtDto(
            id = this.id,
            tilstand = when (tilstand) {
                Tilstand.AnnullertPeriode -> BehandlingtilstandDto.ANNULLERT_PERIODE
                Tilstand.AvsluttetUtenVedtak -> BehandlingtilstandDto.AVSLUTTET_UTEN_VEDTAK
                Tilstand.Beregnet -> BehandlingtilstandDto.BEREGNET
                Tilstand.BeregnetOmgjøring -> BehandlingtilstandDto.BEREGNET_OMGJØRING
                Tilstand.BeregnetRevurdering -> BehandlingtilstandDto.BEREGNET_REVURDERING
                Tilstand.RevurdertVedtakAvvist -> BehandlingtilstandDto.REVURDERT_VEDTAK_AVVIST
                Tilstand.TilInfotrygd -> BehandlingtilstandDto.TIL_INFOTRYGD
                Tilstand.Uberegnet -> BehandlingtilstandDto.UBEREGNET
                Tilstand.UberegnetOmgjøring -> BehandlingtilstandDto.UBEREGNET_OMGJØRING
                Tilstand.UberegnetRevurdering -> BehandlingtilstandDto.UBEREGNET_REVURDERING
                Tilstand.VedtakFattet -> BehandlingtilstandDto.VEDTAK_FATTET
                Tilstand.VedtakIverksatt -> BehandlingtilstandDto.VEDTAK_IVERKSATT
            },
            endringer = this.endringer.map { it.dto() },
            vedtakFattet = this.vedtakFattet,
            avsluttet = this.avsluttet,
            kilde = this.kilde.dto()
        )
    }

    internal fun dto() = BehandlingerUtDto(behandlinger = this.behandlinger.map { it.dto() })
}