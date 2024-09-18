package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Grunnbeløp
import no.nav.helse.dto.BehandlingkildeDto
import no.nav.helse.dto.BehandlingtilstandDto
import no.nav.helse.dto.deserialisering.BehandlingInnDto
import no.nav.helse.dto.deserialisering.BehandlingendringInnDto
import no.nav.helse.dto.deserialisering.BehandlingerInnDto
import no.nav.helse.dto.serialisering.BehandlingUtDto
import no.nav.helse.dto.serialisering.BehandlingendringUtDto
import no.nav.helse.dto.serialisering.BehandlingerUtDto
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.hendelser.utbetaling.avvist
import no.nav.helse.person.Behandlinger.Behandling.Companion.berik
import no.nav.helse.person.Behandlinger.Behandling.Companion.dokumentsporing
import no.nav.helse.person.Behandlinger.Behandling.Companion.erUtbetaltPåForskjelligeUtbetalinger
import no.nav.helse.person.Behandlinger.Behandling.Companion.harGjenbrukbareOpplysninger
import no.nav.helse.person.Behandlinger.Behandling.Companion.jurist
import no.nav.helse.person.Behandlinger.Behandling.Companion.lagreGjenbrukbareOpplysninger
import no.nav.helse.person.Behandlinger.Behandling.Companion.endretSykdomshistorikkFra
import no.nav.helse.person.Behandlinger.Behandling.Companion.grunnbeløpsregulert
import no.nav.helse.person.Behandlinger.Behandling.Endring.Companion.IKKE_FASTSATT_SKJÆRINGSTIDSPUNKT
import no.nav.helse.person.Behandlinger.Behandling.Endring.Companion.dokumentsporing
import no.nav.helse.person.Dokumentsporing.Companion.ider
import no.nav.helse.person.Dokumentsporing.Companion.sisteInntektsmeldingDagerId
import no.nav.helse.person.Dokumentsporing.Companion.sisteInntektsmeldingInntektId
import no.nav.helse.person.Dokumentsporing.Companion.søknadIder
import no.nav.helse.person.Dokumentsporing.Companion.tilSubsumsjonsformat
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement.Companion.harUlikeGrunnbeløp
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.PeriodeMedSammeSkjæringstidspunkt
import no.nav.helse.person.behandling.Behandlingsinntekt
import no.nav.helse.person.builders.UtkastTilVedtakBuilder
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.sykdomstidslinje.Skjæringstidspunkt
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverFaktaavklartInntekt
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverperiodeForVedtaksperiode
import no.nav.helse.utbetalingstidslinje.Maksdatoresultat
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.UtbetalingstidslinjeBuilderVedtaksperiode
import no.nav.helse.økonomi.Inntekt

internal class Behandlinger private constructor(behandlinger: List<Behandling>) {
    internal constructor() : this(emptyList())
    companion object {
        internal fun Map<UUID, Behandlinger>.berik(builder: UtkastTilVedtakBuilder) = mapValues { (_, behandlinger) -> behandlinger.behandlinger.last() }.berik(builder)
        fun gjenopprett(dto: BehandlingerInnDto, grunnlagsdata: Map<UUID, VilkårsgrunnlagElement>, utbetalinger: Map<UUID, Utbetaling>) = Behandlinger(
            behandlinger = dto.behandlinger.map { Behandling.gjenopprett(it, grunnlagsdata, utbetalinger) }
        )
    }
    private val utbetalingene get() = behandlinger.mapNotNull(Behandling::utbetaling)
    private val behandlinger = behandlinger.toMutableList()
    private val siste get() = behandlinger.lastOrNull()?.utbetaling()

    private val observatører = mutableListOf<BehandlingObserver>()

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

    internal fun arbeidsgiverperiode() = ArbeidsgiverperiodeForVedtaksperiode(periode(), behandlinger.last().arbeidsgiverperiode)
    internal fun lagUtbetalingstidslinje(faktaavklarteInntekter: ArbeidsgiverFaktaavklartInntekt, subsumsjonslogg: Subsumsjonslogg) = behandlinger.last().lagUtbetalingstidslinje(faktaavklarteInntekter, subsumsjonslogg)
    internal fun utbetalingstidslinje() = behandlinger.last().utbetalingstidslinje()
    internal fun skjæringstidspunkt() = behandlinger.last().skjæringstidspunkt

    internal fun sykdomstidslinje() = behandlinger.last().sykdomstidslinje()

    internal fun trekkerTilbakePenger() = siste?.trekkerTilbakePenger() == true
    internal fun utbetales() = behandlinger.any { it.erInFlight() }
    internal fun erAvsluttet() = behandlinger.last().erAvsluttet()
    internal fun erAvvist() = siste?.erAvvist() == true
    internal fun harUtbetalinger() = siste?.harUtbetalinger() == true
    internal fun erUbetalt() = siste?.erUbetalt() == true

    internal fun kanForkastes(hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) =
        behandlinger.last().kanForkastes(hendelse, arbeidsgiverUtbetalinger)
    internal fun håndterUtbetalinghendelse(hendelse: UtbetalingHendelse) = behandlinger.any { it.håndterUtbetalinghendelse(hendelse) }

    internal fun behandlingVenter(builder: VedtaksperiodeVenter.Builder) {
        behandlinger.last().behandlingVenter(builder)
    }

    internal fun validerFerdigBehandlet(hendelse: Hendelse) = behandlinger.last().validerFerdigBehandlet(hendelse)

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

    internal fun valider(ytelser: Ytelser, erForlengelse: Boolean) {
        val sisteUtbetaling = siste
        if (sisteUtbetaling == null) ytelser.valider(periode(), skjæringstidspunkt(), periode().endInclusive, erForlengelse)
        else sisteUtbetaling.valider { maksdato -> ytelser.valider(periode(), skjæringstidspunkt(), maksdato, erForlengelse) }
    }

    fun lagreTilkomneInntekter(søknad: Søknad) {
        behandlinger.last().lagreMedTilkomneInntekter(søknad)
    }

    internal fun erKlarForGodkjenning() = siste?.erKlarForGodkjenning() ?: false

    internal fun simuler(hendelse: IAktivitetslogg) = siste!!.simuler(hendelse)

    internal fun godkjenning(
        hendelse: IAktivitetslogg,
        erForlengelse: Boolean,
        perioderMedSammeSkjæringstidspunkt: List<Pair<UUID, Behandlinger>>,
        kanForkastes: Boolean,
        arbeidsgiverperiode: Arbeidsgiverperiode?,
        harPeriodeRettFør: Boolean
    ) {
        val behandlingerMedSammeSkjæringstidspunkt = perioderMedSammeSkjæringstidspunkt.map { it.first to it.second.behandlinger.last() }
        behandlinger.last().godkjenning(hendelse, erForlengelse, behandlingerMedSammeSkjæringstidspunkt, kanForkastes, arbeidsgiverperiode, harPeriodeRettFør, behandlinger.grunnbeløpsregulert())
    }

    internal fun utkastTilVedtak(hendelse: IAktivitetslogg, builder: UtkastTilVedtakBuilder) {
        if (behandlinger.grunnbeløpsregulert()) builder.grunnbeløpsregulert()
        behandlinger.last().utkastTilVedtak(hendelse, builder)
    }

    internal fun håndterAnnullering(arbeidsgiver: Arbeidsgiver, hendelse: AnnullerUtbetaling, andreBehandlinger: List<Behandlinger>): Utbetaling? {
        val annullering = behandlinger.last().annuller(arbeidsgiver, hendelse, this.behandlinger.toList()) ?: return null
        andreBehandlinger.forEach {
            it.kobleAnnulleringTilAndre(arbeidsgiver, hendelse, annullering)
        }
        return annullering
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
        maksdatoresultat: Maksdatoresultat,
        utbetalingstidslinje: Utbetalingstidslinje
    ): Utbetalingstidslinje {
        return behandlinger.last().utbetaling(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksdatoresultat, utbetalingstidslinje)
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
    fun avsluttUtenVedtak(arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg, utbetalingstidslinje: Utbetalingstidslinje) {
        check(behandlinger.last().utbetaling() == null) { "Forventet ikke at perioden har fått utbetaling: kun perioder innenfor arbeidsgiverperioden skal sendes hit. " }
        this.behandlinger.last().avsluttUtenVedtak(arbeidsgiver, hendelse, utbetalingstidslinje)
        bekreftAvsluttetBehandling(arbeidsgiver)
    }

    internal fun sykmeldingsperiode() = this.behandlinger.first().sykmeldingsperiode()
    internal fun periode() = this.behandlinger.last().periode()

    // sørger for ny behandling når vedtaksperioden går ut av Avsluttet/AUU,
    // men bare hvis det ikke er laget en ny allerede fra før
    fun sikreNyBehandling(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) {
        leggTilNyBehandling(behandlinger.last().sikreNyBehandling(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode))
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
    internal fun sisteInntektsmeldingDagerId() = behandlinger.dokumentsporing.sisteInntektsmeldingDagerId()
    internal fun harHåndtertDagerTidligere() = behandlinger.dokumentsporing.sisteInntektsmeldingDagerId() != null
    internal fun harHåndtertInntektTidligere() = behandlinger.dokumentsporing.sisteInntektsmeldingInntektId() != null

    internal fun oppdaterDokumentsporing(dokument: Dokumentsporing): Boolean {
        return behandlinger.last().oppdaterDokumentsporing(dokument)
    }

    fun dokumentHåndtert(dokumentsporing: Dokumentsporing) =
        behandlinger.any { it.dokumentHåndtert(dokumentsporing) }

    internal fun harGjenbrukbareOpplysninger(organisasjonsnummer: String) =
        behandlinger.harGjenbrukbareOpplysninger(organisasjonsnummer)

    internal fun lagreGjenbrukbareOpplysninger(skjæringstidspunkt: LocalDate, organisasjonsnummer: String, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg) =
        behandlinger.lagreGjenbrukbareOpplysninger(skjæringstidspunkt, organisasjonsnummer, arbeidsgiver, hendelse)

    fun håndterEndring(person: Person, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>, validering: () -> Unit) {
        behandlinger.last().håndterEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)?.also {
            leggTilNyBehandling(it)
        }
        person.sykdomshistorikkEndret()
        validering()
        hendelse.igangsettOverstyring(person)
    }

    private fun SykdomshistorikkHendelse.igangsettOverstyring(person: Person) {
        revurderingseventyr(skjæringstidspunkt(), periode())
            ?.takeIf { behandlinger.endretSykdomshistorikkFra(this) }
            ?.let { revurderingseventyr -> person.igangsettOverstyring(revurderingseventyr) }
    }

    fun beregnSkjæringstidspunkt(beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) {
        behandlinger.last().beregnSkjæringstidspunkt(beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
    }

    fun erUtbetaltPåForskjelligeUtbetalinger(other: Behandlinger): Boolean {
        return this.behandlinger.erUtbetaltPåForskjelligeUtbetalinger(other.behandlinger)
    }

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
        val arbeidsgiverperiode get() = gjeldende.arbeidsgiverperiode
        val skjæringstidspunkt get() = gjeldende.skjæringstidspunkt

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
        fun behandlingVenter(builder: VedtaksperiodeVenter.Builder) {
            builder.behandlingVenter(id)
        }
        fun utbetalingstidslinje() = gjeldende.utbetalingstidslinje
        fun lagUtbetalingstidslinje(faktaavklarteInntekter: ArbeidsgiverFaktaavklartInntekt, subsumsjonslogg: Subsumsjonslogg): Utbetalingstidslinje {
            val builder = UtbetalingstidslinjeBuilderVedtaksperiode(
                faktaavklarteInntekter = faktaavklarteInntekter,
                regler = ArbeidsgiverRegler.Companion.NormalArbeidstaker,
                subsumsjonslogg = subsumsjonslogg,
                arbeidsgiverperiode = arbeidsgiverperiode
            )
            sykdomstidslinje().accept(builder)
            return builder.result()
        }

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
            val utbetalingstidslinje: Utbetalingstidslinje,
            val skjæringstidspunkt: LocalDate,
            val arbeidsgiverperiode: List<Periode>,
            val maksdatoresultat: Maksdatoresultat,
            val inntekter: List<Behandlingsinntekt>
        ) {

            internal constructor(
                grunnlagsdata: VilkårsgrunnlagElement?,
                utbetaling: Utbetaling?,
                dokumentsporing: Dokumentsporing,
                sykdomstidslinje: Sykdomstidslinje,
                utbetalingstidslinje: Utbetalingstidslinje,
                sykmeldingsperiode: Periode,
                periode: Periode,
                skjæringstidspunkt: LocalDate,
                arbeidsgiverperiode: List<Periode>,
                maksdatoresultat: Maksdatoresultat,
                inntekter: List<Behandlingsinntekt>
            ) : this(
                id = UUID.randomUUID(),
                tidsstempel = LocalDateTime.now(),
                sykmeldingsperiode = sykmeldingsperiode,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                utbetaling = utbetaling,
                dokumentsporing = dokumentsporing,
                sykdomstidslinje = sykdomstidslinje,
                utbetalingstidslinje = utbetalingstidslinje,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiverperiode = arbeidsgiverperiode,
                maksdatoresultat = maksdatoresultat,
                inntekter = inntekter
            )

            private fun skjæringstidspunkt(beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, sykdomstidslinje: Sykdomstidslinje = this.sykdomstidslinje, periode: Periode = this.periode) =
                beregnSkjæringstidspunkt().beregnSkjæringstidspunkt(periode, sykdomstidslinje.sykdomsperiode())

            companion object {
                val IKKE_FASTSATT_SKJÆRINGSTIDSPUNKT = LocalDate.MIN
                val List<Endring>.dokumentsporing get() = map { it.dokumentsporing }.toSet()
                fun gjenopprett(dto: BehandlingendringInnDto, grunnlagsdata: Map<UUID, VilkårsgrunnlagElement>, utbetalinger: Map<UUID, Utbetaling>, erAvsluttetUtenVedtak: Boolean): Endring {
                    val periode = Periode.gjenopprett(dto.periode)
                    val utbetaling = dto.utbetalingId?.let { utbetalinger.getValue(it) }
                    return Endring(
                        id = dto.id,
                        tidsstempel = dto.tidsstempel,
                        sykmeldingsperiode = Periode.gjenopprett(dto.sykmeldingsperiode),
                        periode = periode,
                        grunnlagsdata = dto.vilkårsgrunnlagId?.let { grunnlagsdata.getValue(it) },
                        utbetaling = utbetaling,
                        dokumentsporing = Dokumentsporing.gjenopprett(dto.dokumentsporing),
                        sykdomstidslinje = Sykdomstidslinje.gjenopprett(dto.sykdomstidslinje),
                        utbetalingstidslinje = migrerUtbetalingstidslinje(dto, utbetaling, erAvsluttetUtenVedtak),
                        skjæringstidspunkt = dto.skjæringstidspunkt,
                        arbeidsgiverperiode = dto.arbeidsgiverperiode.map { Periode.gjenopprett(it) },
                        maksdatoresultat = dto.maksdatoresultat?.let { Maksdatoresultat.gjenopprett(it) } ?: Maksdatoresultat.IkkeVurdert,
                        inntekter = emptyList() // TODO kanskje legge til noe her
                    )
                }

                private fun migrerUtbetalingstidslinje(dto: BehandlingendringInnDto, utbetaling: Utbetaling?, erAvsluttetUtenVedtak: Boolean): Utbetalingstidslinje {
                    if (dto.utbetalingstidslinje != null) return Utbetalingstidslinje.gjenopprett(dto.utbetalingstidslinje!!)
                    if (utbetaling != null) return utbetaling.utbetalingstidslinje.subset(Periode.gjenopprett(dto.periode))
                    if (erAvsluttetUtenVedtak) {
                        try {
                            val builder = UtbetalingstidslinjeBuilderVedtaksperiode(
                                faktaavklarteInntekter = ArbeidsgiverFaktaavklartInntekt(
                                    skjæringstidspunkt = dto.skjæringstidspunkt,
                                    `6G` = Grunnbeløp.`6G`.beløp(dto.skjæringstidspunkt),
                                    fastsattÅrsinntekt = Inntekt.INGEN,
                                    gjelder = dto.skjæringstidspunkt til LocalDate.MAX,
                                    refusjonsopplysninger = Refusjonsopplysninger()
                                ),
                                regler = ArbeidsgiverRegler.Companion.NormalArbeidstaker,
                                subsumsjonslogg = Subsumsjonslogg.NullObserver,
                                arbeidsgiverperiode = dto.arbeidsgiverperiode.map { Periode.gjenopprett(it) }
                            )
                            Sykdomstidslinje.gjenopprett(dto.sykdomstidslinje).accept(builder)
                            return builder.result()
                        } catch (err: Exception) {
                            // svelger denne
                        }
                    }
                    return Utbetalingstidslinje()
                }
            }

            internal fun arbeidsgiverperiodeEndret(other: Endring) =
                this.arbeidsgiverperiode != other.arbeidsgiverperiode

            internal fun erRettFør(neste: Endring): Boolean {
                return this.sykdomstidslinje.erRettFør(neste.sykdomstidslinje)
            }

            override fun toString() = "$periode - $dokumentsporing - ${sykdomstidslinje.toShortString()}${utbetaling?.let { " - $it" } ?: ""}"

            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other !is Endring) return false
                return this.dokumentsporing == other.dokumentsporing
            }

            internal fun accept(visitor: BehandlingerVisitor) {
                visitor.preVisitBehandlingendring(
                    id,
                    tidsstempel,
                    sykmeldingsperiode,
                    periode,
                    grunnlagsdata,
                    utbetaling,
                    dokumentsporing,
                    sykdomstidslinje,
                    skjæringstidspunkt,
                    arbeidsgiverperiode
                )
                grunnlagsdata?.accept(visitor)
                utbetaling?.accept(visitor)
                sykdomstidslinje.accept(visitor)
                visitor.postVisitBehandlingendring(
                    id,
                    tidsstempel,
                    sykmeldingsperiode,
                    periode,
                    grunnlagsdata,
                    utbetaling,
                    dokumentsporing,
                    sykdomstidslinje,
                    skjæringstidspunkt,
                    arbeidsgiverperiode
                )
            }

            internal fun kopierMedNyttSkjæringstidspunkt(beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Endring? {
                val nyttSkjæringstidspunkt = skjæringstidspunkt(beregnSkjæringstidspunkt)
                val arbeidsgiverperiode = beregnArbeidsgiverperiode(this.periode)
                if (nyttSkjæringstidspunkt == this.skjæringstidspunkt && arbeidsgiverperiode == this.arbeidsgiverperiode) return null
                return Endring(
                    grunnlagsdata = this.grunnlagsdata,
                    utbetaling = this.utbetaling,
                    dokumentsporing = this.dokumentsporing,
                    sykdomstidslinje = this.sykdomstidslinje,
                    utbetalingstidslinje = this.utbetalingstidslinje,
                    sykmeldingsperiode = this.sykmeldingsperiode,
                    periode = this.periode,
                    skjæringstidspunkt = nyttSkjæringstidspunkt,
                    arbeidsgiverperiode = arbeidsgiverperiode,
                    maksdatoresultat = this.maksdatoresultat,
                    inntekter = this.inntekter
                )
            }

            internal fun kopierMedEndring(
                periode: Periode,
                dokument: Dokumentsporing,
                sykdomstidslinje: Sykdomstidslinje,
                beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
                beregnArbeidsgiverperiode: (Periode) -> List<Periode>
            ) = Endring(
                grunnlagsdata = null,
                utbetaling = null,
                dokumentsporing = dokument,
                sykdomstidslinje = sykdomstidslinje,
                utbetalingstidslinje = Utbetalingstidslinje(),
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = periode,
                skjæringstidspunkt = skjæringstidspunkt(beregnSkjæringstidspunkt, sykdomstidslinje, periode),
                arbeidsgiverperiode = beregnArbeidsgiverperiode(this.periode),
                maksdatoresultat = Maksdatoresultat.IkkeVurdert,
                inntekter = this.inntekter
            )
            internal fun kopierUtenUtbetaling(
                beregnSkjæringstidspunkt: (() -> Skjæringstidspunkt)? = null,
                beregnArbeidsgiverperiode: (Periode) -> List<Periode> = { this.arbeidsgiverperiode }
            ) = Endring(
                grunnlagsdata = null,
                utbetaling = null,
                dokumentsporing = this.dokumentsporing,
                sykdomstidslinje = this.sykdomstidslinje,
                utbetalingstidslinje = Utbetalingstidslinje(),
                maksdatoresultat = Maksdatoresultat.IkkeVurdert,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode,
                skjæringstidspunkt = beregnSkjæringstidspunkt?.let { skjæringstidspunkt(beregnSkjæringstidspunkt) } ?: this.skjæringstidspunkt,
                arbeidsgiverperiode = beregnArbeidsgiverperiode(this.periode),
                inntekter = this.inntekter
            )
            internal fun kopierMedUtbetaling(maksdatoresultat: Maksdatoresultat, utbetalingstidslinje: Utbetalingstidslinje, utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement) = Endring(
                grunnlagsdata = grunnlagsdata,
                utbetaling = utbetaling,
                utbetalingstidslinje = utbetalingstidslinje.subset(this.periode),
                maksdatoresultat = maksdatoresultat,
                dokumentsporing = this.dokumentsporing,
                sykdomstidslinje = this.sykdomstidslinje,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode,
                skjæringstidspunkt = this.skjæringstidspunkt,
                arbeidsgiverperiode = this.arbeidsgiverperiode,
                inntekter = this.inntekter
            )
            internal fun kopierDokument(dokument: Dokumentsporing) = Endring(
                grunnlagsdata = this.grunnlagsdata,
                utbetaling = this.utbetaling,
                dokumentsporing = dokument,
                sykdomstidslinje = this.sykdomstidslinje,
                utbetalingstidslinje = this.utbetalingstidslinje,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode,
                skjæringstidspunkt = this.skjæringstidspunkt,
                arbeidsgiverperiode = this.arbeidsgiverperiode,
                maksdatoresultat = this.maksdatoresultat,
                inntekter = this.inntekter
            )
            internal fun kopierMedUtbetalingstidslinje(utbetalingstidslinje: Utbetalingstidslinje) = Endring(
                grunnlagsdata = this.grunnlagsdata,
                utbetaling = this.utbetaling,
                dokumentsporing = this.dokumentsporing,
                sykdomstidslinje = this.sykdomstidslinje,
                utbetalingstidslinje = utbetalingstidslinje.subset(this.periode),
                maksdatoresultat = this.maksdatoresultat,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode,
                skjæringstidspunkt = this.skjæringstidspunkt,
                arbeidsgiverperiode = this.arbeidsgiverperiode,
                inntekter = this.inntekter
            )
            internal fun kopierMedTilkomneInntekter(søknad: Søknad) = Endring(
                grunnlagsdata = this.grunnlagsdata,
                utbetaling = this.utbetaling,
                dokumentsporing = this.dokumentsporing,
                sykdomstidslinje = this.sykdomstidslinje,
                utbetalingstidslinje = this.utbetalingstidslinje,
                maksdatoresultat = this.maksdatoresultat,
                sykmeldingsperiode = this.sykmeldingsperiode,
                periode = this.periode,
                skjæringstidspunkt = this.skjæringstidspunkt,
                arbeidsgiverperiode = this.arbeidsgiverperiode,
                inntekter = søknad.inntekter()
            )

            fun forkastUtbetaling(hendelse: IAktivitetslogg) {
                utbetaling?.forkast(hendelse)
            }

            fun godkjenning(
                hendelse: IAktivitetslogg,
                erForlengelse: Boolean,
                kanForkastes: Boolean,
                behandling: Behandling,
                perioderMedSammeSkjæringstidspunkt: List<Triple<UUID, UUID, Periode>>,
                arbeidsgiverperiode: Arbeidsgiverperiode?,
                harPeriodeRettFør: Boolean,
                gregulering: Boolean
            ) {
                checkNotNull(utbetaling) { "Forventet ikke manglende utbetaling ved godkjenningsbehov" }
                checkNotNull(grunnlagsdata) { "Forventet ikke manglende vilkårsgrunnlag ved godkjennignsbehov" }
                val hendelser = behandling.dokumentsporing.ider()
                val builder = GodkjenningsbehovBuilder(
                    erForlengelse,
                    kanForkastes,
                    periode,
                    behandling.id,
                    perioderMedSammeSkjæringstidspunkt.map { (vedtaksperiodeId, behandlingId, periode) ->
                        PeriodeMedSammeSkjæringstidspunkt(vedtaksperiodeId, behandlingId, periode)
                    },
                    hendelser,
                    gregulering
                )
                grunnlagsdata.byggGodkjenningsbehov(builder)
                utbetaling.byggGodkjenningsbehov(hendelse, periode, builder)
                arbeidsgiverperiode?.tags(this.periode, builder, harPeriodeRettFør)
                behandling.observatører.forEach { it.utkastTilVedtak(behandling.id, builder.tags(), builder.`6G`()) }
                Aktivitet.Behov.godkjenning(
                    aktivitetslogg = hendelse,
                    builder = builder
                )
            }

            fun utkastTilVedtak(hendelse: IAktivitetslogg, builder: UtkastTilVedtakBuilder) {
                checkNotNull(utbetaling) { "Forventet ikke manglende utbetaling ved utkast til vedtak" }
                checkNotNull(grunnlagsdata) { "Forventet ikke manglende vilkårsgrunnlag ved utkast til vedtak" }
                builder.utbetalingstidslinje(utbetalingstidslinje).utbetaling(utbetaling)
                // TODO: grunnlagsdata.berik(builder)
                // TODO: Sende godkjenningsbehov & utkastTilVedtak
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
                    skjæringstidspunkt = this.skjæringstidspunkt,
                    utbetalingId = utbetalingUtDto?.id,
                    utbetalingstatus = utbetalingUtDto?.tilstand,
                    dokumentsporing = this.dokumentsporing.dto(),
                    sykdomstidslinje = this.sykdomstidslinje.dto(),
                    utbetalingstidslinje = this.utbetalingstidslinje.dto(),
                    arbeidsgiverperioder = this.arbeidsgiverperiode.map { it.dto() },
                    maksdatoresultat = this.maksdatoresultat.dto()
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

        internal fun avsluttUtenVedtak(arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg, utbetalingstidslinje: Utbetalingstidslinje) {
            tilstand.avsluttUtenVedtak(this, arbeidsgiver, hendelse, utbetalingstidslinje)
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
            maksdatoresultat: Maksdatoresultat,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            return tilstand.utbetaling(this, vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksdatoresultat, utbetalingstidslinje)
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
            maksdatoresultat: Maksdatoresultat,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            val strategi = Arbeidsgiver::lagUtbetaling
            return lagUtbetaling(
                vedtaksperiodeSomLagerUtbetaling,
                fødselsnummer,
                arbeidsgiver,
                grunnlagsdata,
                hendelse,
                maksdatoresultat,
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
            maksdatoresultat: Maksdatoresultat,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            val strategi = Arbeidsgiver::lagUtbetaling
            // TODO: bør sende med beregnet AGP slik at utbetalingskoden vet hvilket oppdrag som skal bygges videre på
            return lagUtbetaling(
                vedtaksperiodeSomLagerUtbetaling,
                fødselsnummer,
                arbeidsgiver,
                grunnlagsdata,
                hendelse,
                maksdatoresultat,
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
            maksdatoresultat: Maksdatoresultat,
            utbetalingstidslinje: Utbetalingstidslinje
        ): Utbetalingstidslinje {
            val strategi = Arbeidsgiver::lagRevurdering
            return lagUtbetaling(
                vedtaksperiodeSomLagerUtbetaling,
                fødselsnummer,
                arbeidsgiver,
                grunnlagsdata,
                hendelse,
                maksdatoresultat,
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
            maksdatoresultat: Maksdatoresultat,
            utbetalingstidslinje: Utbetalingstidslinje,
            strategi: (Arbeidsgiver, aktivitetslogg: IAktivitetslogg, fødselsnummer: String, utbetalingstidslinje: Utbetalingstidslinje, maksdato: LocalDate, forbrukteSykedager: Int, gjenståendeSykedager: Int, periode: Periode) -> Utbetaling,
            nyTilstand: Tilstand
        ): Utbetalingstidslinje {
            val denNyeUtbetalingen = strategi(arbeidsgiver, hendelse, fødselsnummer, utbetalingstidslinje, maksdatoresultat.maksdato, maksdatoresultat.forbrukteDager.size, maksdatoresultat.gjenståendeDager, periode)
            denNyeUtbetalingen.nyVedtaksperiodeUtbetaling(vedtaksperiodeSomLagerUtbetaling)
            nyEndring(gjeldende.kopierMedUtbetaling(maksdatoresultat, utbetalingstidslinje, denNyeUtbetalingen, grunnlagsdata))
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

        private fun kopierMedUtbetalingstidslinje(utbetalingstidslinje: Utbetalingstidslinje): Boolean {
            nyEndring(gjeldende.kopierMedUtbetalingstidslinje(utbetalingstidslinje))
            return true
        }

        internal fun lagreMedTilkomneInntekter(søknad: Søknad): Boolean {
            nyEndring(gjeldende.kopierMedTilkomneInntekter(søknad))
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

        fun beregnSkjæringstidspunkt(beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) {
            tilstand.beregnSkjæringstidspunkt(this, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
        }

        fun håndterEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
            return tilstand.håndterEndring(this, arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
        }

        private fun håndtereEndring(
            arbeidsgiver: Arbeidsgiver,
            hendelse: SykdomshistorikkHendelse,
            beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
            beregnArbeidsgiverperiode: (Periode) -> List<Periode>
        ): Endring {
            val oppdatertPeriode = hendelse.oppdaterFom(endringer.last().periode)
            val sykdomstidslinje = arbeidsgiver.oppdaterSykdom(hendelse).subset(oppdatertPeriode)
            return endringer.last().kopierMedEndring(oppdatertPeriode, hendelse.dokumentsporing(), sykdomstidslinje, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
        }

        private fun oppdaterMedNyttSkjæringstidspunkt(beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) {
            val endring = endringer.last().kopierMedNyttSkjæringstidspunkt(beregnSkjæringstidspunkt, beregnArbeidsgiverperiode) ?: return
            nyEndring(endring)
        }

        // oppdaterer seg selv med endringen
        private fun oppdaterMedEndring(
            arbeidsgiver: Arbeidsgiver,
            hendelse: SykdomshistorikkHendelse,
            beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
            arbeidsgiverperioder: (Periode) -> List<Periode>
        ) {
            val endring = håndtereEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, arbeidsgiverperioder)
            if (endring == gjeldende) return
            nyEndring(endring)
        }
        private fun nyBehandlingMedEndring(
            arbeidsgiver: Arbeidsgiver,
            hendelse: SykdomshistorikkHendelse,
            beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
            beregnArbeidsgiverperiode: (Periode) -> List<Periode>,
            starttilstand: Tilstand = Tilstand.Uberegnet
        ): Behandling {
            arbeidsgiver.låsOpp(periode)
            return Behandling(
                observatører = this.observatører,
                tilstand = starttilstand,
                endringer = listOf(håndtereEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)),
                avsluttet = null,
                kilde = Behandlingkilde(hendelse)
            )
        }

        private fun sikreNyBehandling(
            arbeidsgiver: Arbeidsgiver,
            starttilstand: Tilstand,
            hendelse: Hendelse,
            beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
            beregnArbeidsgiverperiode: (Periode) -> List<Periode>
        ): Behandling {
            arbeidsgiver.låsOpp(periode)
            return Behandling(
                observatører = this.observatører,
                tilstand = starttilstand,
                endringer = listOf(endringer.last().kopierUtenUtbetaling(beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)),
                avsluttet = null,
                kilde = Behandlingkilde(hendelse)
            )
        }

        private fun nyAnnullertBehandling(arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, annullering: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement): Behandling {
            arbeidsgiver.låsOpp(periode)
            return Behandling(
                observatører = this.observatører,
                tilstand = Tilstand.AnnullertPeriode,
                endringer = listOf(this.gjeldende.kopierMedUtbetaling(Maksdatoresultat.IkkeVurdert, Utbetalingstidslinje(), annullering, grunnlagsdata)),
                avsluttet = LocalDateTime.now(),
                kilde = Behandlingkilde(hendelse)
            )
        }

        fun sikreNyBehandling(
            arbeidsgiver: Arbeidsgiver,
            hendelse: Hendelse,
            beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
            beregnArbeidsgiverperiode: (Periode) -> List<Periode>
        ): Behandling? {
            return tilstand.sikreNyBehandling(this, arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
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
            observatører.forEach { it.nyBehandling(id, periode, kilde.meldingsreferanseId, kilde.innsendt, kilde.registert, kilde.avsender, type, endringer.dokumentsporing.søknadIder()) }
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

        internal fun godkjenning(
            hendelse: IAktivitetslogg,
            erForlengelse: Boolean,
            behandlingerMedSammeSkjæringstidspunkt: List<Pair<UUID, Behandling>>,
            kanForkastes: Boolean,
            arbeidsgiverperiode: Arbeidsgiverperiode?,
            harPeriodeRettFør: Boolean,
            gregulering: Boolean
        ) {
            val perioderMedSammeSkjæringstidspunkt = behandlingerMedSammeSkjæringstidspunkt.map { Triple(it.first, it.second.id, it.second.periode) }
            gjeldende.godkjenning(hendelse, erForlengelse, kanForkastes, this, perioderMedSammeSkjæringstidspunkt, arbeidsgiverperiode, harPeriodeRettFør, gregulering)
        }

        internal fun utkastTilVedtak(hendelse: IAktivitetslogg, builder: UtkastTilVedtakBuilder) {
            builder.behandlingId(id).periode(periode).hendelseIder(dokumentsporing.ider()).skjæringstidspunkt(skjæringstidspunkt)
            gjeldende.utkastTilVedtak(hendelse, builder)
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

        internal fun validerFerdigBehandlet(hendelse: Hendelse) = tilstand.validerFerdigBehandlet(this, hendelse)

        private fun valideringFeilet(hendelse: Hendelse, feil: String) {
            // Om de er hendelsen vi håndterer nå som har skapt situasjonen feiler vi fremfor å gå videre.
            if (kilde.meldingsreferanseId == hendelse.meldingsreferanseId()) error(feil)
            // Om det er krøll fra tidligere logger vi bare
            else hendelse.info("Eksisterende ugyldig behandling på en ferdig behandlet vedtaksperiode: $feil")
        }

        internal companion object {
            val List<Behandling>.sykmeldingsperiode get() = first().periode
            val List<Behandling>.dokumentsporing get() = map { it.dokumentsporing }.takeUnless { it.isEmpty() }?.reduce(Set<Dokumentsporing>::plus) ?: emptySet()

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
                            utbetalingstidslinje = Utbetalingstidslinje(),
                            periode = checkNotNull(sykdomstidslinje.periode()) { "kan ikke opprette behandling på tom sykdomstidslinje" },
                            skjæringstidspunkt = IKKE_FASTSATT_SKJÆRINGSTIDSPUNKT,
                            arbeidsgiverperiode = emptyList(),
                            maksdatoresultat = Maksdatoresultat.IkkeVurdert,
                            inntekter = søknad.inntekter()
                        )
                    ),
                    avsluttet = null,
                    kilde = Behandlingkilde(søknad)
                )
            fun List<Behandling>.jurist(jurist: MaskinellJurist, vedtaksperiodeId: UUID) =
                jurist.medVedtaksperiode(vedtaksperiodeId, dokumentsporing.tilSubsumsjonsformat(), sykmeldingsperiode)

            internal fun List<Behandling>.harGjenbrukbareOpplysninger(organisasjonsnummer: String) = forrigeEndringMedGjenbrukbareOpplysninger(organisasjonsnummer) != null
            internal fun List<Behandling>.lagreGjenbrukbareOpplysninger(skjæringstidspunkt: LocalDate, organisasjonsnummer: String, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg) {
                val (forrigeEndring, vilkårsgrunnlag) = forrigeEndringMedGjenbrukbareOpplysninger(organisasjonsnummer) ?: return
                val nyArbeidsgiverperiode = forrigeEndring.arbeidsgiverperiodeEndret(gjeldendeEndring())
                // Herfra bruker vi "gammel" løype - kanskje noe kan skrus på fra det punktet her om en skulle skru på dette
                vilkårsgrunnlag.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, hendelse, nyArbeidsgiverperiode)
            }
            private fun List<Behandling>.forrigeEndringMedGjenbrukbareOpplysninger(organisasjonsnummer: String): Pair<Endring, VilkårsgrunnlagElement>? =
                forrigeEndringMed { it.grunnlagsdata?.harGjenbrukbareOpplysninger(organisasjonsnummer) == true }?.let { it to it.grunnlagsdata!! }

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

            private fun List<Behandling>.gjeldendeEndring() = this.last().gjeldende
            private fun List<Behandling>.forrigeEndringMed(predikat: (endring: Endring) -> Boolean) =
                this.asReversed().firstNotNullOfOrNull { behandling ->
                    behandling.endringer.asReversed().firstNotNullOfOrNull { endring ->
                        endring.takeIf { predikat(it) }
                    }
                }
            private fun List<Behandling>.forrigeOgGjeldendeEndring(): Pair<Endring?, Endring> {
                val gjeldendeEndring = gjeldendeEndring()
                return forrigeEndringMed { it.tidsstempel < gjeldendeEndring.tidsstempel } to gjeldendeEndring
            }

            internal fun List<Behandling>.endretSykdomshistorikkFra(hendelse: SykdomshistorikkHendelse): Boolean {
                val (forrigeEndring, gjeldendeEndring) = forrigeOgGjeldendeEndring()
                if (gjeldendeEndring.dokumentsporing != hendelse.dokumentsporing()) return false
                if (forrigeEndring == null) return true
                return !gjeldendeEndring.sykdomstidslinje.funksjoneltLik(forrigeEndring.sykdomstidslinje)
            }

            internal fun List<Behandling>.grunnbeløpsregulert(): Boolean {
                val gjeldende = gjeldendeEndring().takeIf { it.grunnlagsdata != null } ?: return false
                val forrige = forrigeEndringMed { it.tidsstempel < gjeldende.tidsstempel && it.grunnlagsdata != null } ?: return false
                if (forrige.skjæringstidspunkt != gjeldende.skjæringstidspunkt) return false
                return listOf(forrige.grunnlagsdata!!, gjeldende.grunnlagsdata!!).harUlikeGrunnbeløp()
            }

            internal fun Map<UUID, Behandling>.berik(builder: UtkastTilVedtakBuilder) = forEach { (vedtaksperiodeId, behandling) ->
                builder.relevantPeriode(vedtaksperiodeId, behandling.id, behandling.skjæringstidspunkt, behandling.periode)
            }

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
                    endringer = dto.endringer.map { Endring.gjenopprett(it, grunnlagsdata, utbetalinger, dto.tilstand == BehandlingtilstandDto.AVSLUTTET_UTEN_VEDTAK) }.toMutableList(),
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
            fun beregnSkjæringstidspunkt(behandling: Behandling, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) {}
            fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
                error("Har ikke implementert håndtering av endring i $this")
            }
            fun vedtakAvvist(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                error("Kan ikke avvise vedtak for behandling i $this")
            }
            fun vedtakFattet(behandling: Behandling, arbeidsgiver: Arbeidsgiver, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                error("Kan ikke fatte vedtak for behandling i $this")
            }
            fun avsluttUtenVedtak(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg, utbetalingstidslinje: Utbetalingstidslinje) {
                error("Kan ikke avslutte uten vedtak for behandling i $this")
            }
            fun avsluttMedVedtak(behandling: Behandling, hendelse: IAktivitetslogg) {
                error("Kan ikke avslutte behandling i $this")
            }
            fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {
                error("Støtter ikke å forkaste utbetaling utbetaling i $this")
            }
            fun utbetaling(behandling: Behandling, vedtaksperiodeSomLagerUtbetaling: UUID, fødselsnummer: String, arbeidsgiver: Arbeidsgiver, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg, maksdatoresultat: Maksdatoresultat, utbetalingstidslinje: Utbetalingstidslinje): Utbetalingstidslinje {
                error("Støtter ikke å opprette utbetaling i $this")
            }
            fun oppdaterDokumentsporing(behandling: Behandling, dokument: Dokumentsporing): Boolean {
                error("Støtter ikke å oppdatere dokumentsporing med $dokument i $this")
            }
            fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean
            fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
                return null
            }
            fun tillaterNyBehandling(behandling: Behandling, other: Behandling): Boolean = false
            fun håndterUtbetalinghendelse(behandling: Behandling, hendelse: UtbetalingHendelse) = false
            fun validerFerdigBehandlet(behandling: Behandling, hendelse: Hendelse) {
                behandling.valideringFeilet(hendelse, "Behandling ${behandling.id} burde vært ferdig behandlet, men står i tilstand ${behandling.tilstand::class.simpleName}")
            }

            data object Uberegnet : Tilstand {
                override fun behandlingOpprettet(behandling: Behandling) = behandling.emitNyBehandlingOpprettet(PersonObserver.BehandlingOpprettetEvent.Type.Søknad)
                override fun entering(behandling: Behandling, hendelse: IAktivitetslogg) {
                    check(behandling.utbetaling() == null) { "skal ikke ha utbetaling og være uberegnet samtidig" }
                }

                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = true

                override fun beregnSkjæringstidspunkt(
                    behandling: Behandling,
                    beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
                    beregnArbeidsgiverperiode: (Periode) -> List<Periode>
                ) {
                    behandling.oppdaterMedNyttSkjæringstidspunkt(beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
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
                    maksdatoresultat: Maksdatoresultat,
                    utbetalingstidslinje: Utbetalingstidslinje
                ): Utbetalingstidslinje {
                    return behandling.lagUtbetaling(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksdatoresultat, utbetalingstidslinje)
                }

                override fun avsluttUtenVedtak(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: IAktivitetslogg, utbetalingstidslinje: Utbetalingstidslinje) {
                    behandling.behandlingLukket(arbeidsgiver)
                    behandling.kopierMedUtbetalingstidslinje(utbetalingstidslinje)
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
                    maksdatoresultat: Maksdatoresultat,
                    utbetalingstidslinje: Utbetalingstidslinje
                ): Utbetalingstidslinje {
                    return behandling.lagOmgjøring(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksdatoresultat, utbetalingstidslinje)
                }

                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    behandling.kanForkastingAvKortPeriodeTillates(hendelse, arbeidsgiverUtbetalinger)
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
                    behandling.nyEndring(behandling.gjeldende.kopierMedUtbetaling(Maksdatoresultat.IkkeVurdert, Utbetalingstidslinje(), annullering, grunnlagsdata))
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
                    maksdatoresultat: Maksdatoresultat,
                    utbetalingstidslinje: Utbetalingstidslinje
                ): Utbetalingstidslinje {
                    return behandling.lagRevurdering(vedtaksperiodeSomLagerUtbetaling, fødselsnummer, arbeidsgiver, grunnlagsdata, hendelse, maksdatoresultat, utbetalingstidslinje)
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

                override fun beregnSkjæringstidspunkt(
                    behandling: Behandling,
                    beregnSkjæringstidspunkt: () -> Skjæringstidspunkt,
                    beregnArbeidsgiverperiode: (Periode) -> List<Periode>
                ) {
                    behandling.oppdaterMedNyttSkjæringstidspunkt(beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
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
                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
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
                    behandling.nyEndring(behandling.gjeldende.kopierMedUtbetaling(Maksdatoresultat.IkkeVurdert, Utbetalingstidslinje(), annullering, grunnlagsdata))
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
                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling? {
                    behandling.gjeldende.forkastUtbetaling(hendelse)
                    behandling.oppdaterMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
                    behandling.tilstand(UberegnetRevurdering, hendelse)
                    return null
                }
            }
            data object RevurdertVedtakAvvist : Tilstand {
                override fun kanForkastes(behandling: Behandling, hendelse: IAktivitetslogg, arbeidsgiverUtbetalinger: List<Utbetaling>) = false

                override fun forkastVedtaksperiode(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse): Behandling {
                    error("Kan ikke forkaste i tilstand ${this.javaClass.simpleName}")
                }

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetRevurdering, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
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

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetRevurdering, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
                }

                override fun håndterUtbetalinghendelse(behandling: Behandling, hendelse: UtbetalingHendelse): Boolean {
                    val utbetaling = checkNotNull(behandling.gjeldende.utbetaling) { "forventer utbetaling" }
                    if (!utbetaling.gjelderFor(hendelse)) return false
                    if (utbetaling.erAvsluttet()) avsluttMedVedtak(behandling, hendelse)
                    return true
                }

                override fun utenUtbetaling(behandling: Behandling, hendelse: IAktivitetslogg) {}

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) =
                    behandling.nyBehandlingMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode, UberegnetRevurdering)

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

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetOmgjøring, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling {
                    return behandling.nyBehandlingMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode, UberegnetOmgjøring)
                }

                override fun validerFerdigBehandlet(behandling: Behandling, hendelse: Hendelse) {
                    if (behandling.avsluttet != null && behandling.vedtakFattet == null) return
                    behandling.valideringFeilet(hendelse, "Behandling ${behandling.id} er ferdig behandlet i tilstand AvsluttetUtenVedtak, men med uventede tidsstempler.")
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

                override fun sikreNyBehandling(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: Hendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>): Behandling {
                    return behandling.sikreNyBehandling(arbeidsgiver, UberegnetRevurdering, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode)
                }

                override fun håndterEndring(behandling: Behandling, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, beregnSkjæringstidspunkt: () -> Skjæringstidspunkt, beregnArbeidsgiverperiode: (Periode) -> List<Periode>) =
                    behandling.nyBehandlingMedEndring(arbeidsgiver, hendelse, beregnSkjæringstidspunkt, beregnArbeidsgiverperiode, UberegnetRevurdering)

                override fun validerFerdigBehandlet(behandling: Behandling, hendelse: Hendelse) {
                    if (behandling.avsluttet != null && behandling.vedtakFattet != null) return
                    behandling.valideringFeilet(hendelse, "Behandling ${behandling.id} er ferdig behandlet i tilstand VedtakIverksatt, men med uventede tidsstempler.")
                }
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

                override fun validerFerdigBehandlet(behandling: Behandling, hendelse: Hendelse) {
                    if (behandling.avsluttet != null && behandling.vedtakFattet == null) return
                    behandling.valideringFeilet(hendelse, "Behandling ${behandling.id} er ferdig behandlet i tiltand TilInfotrygd, men med uventede tidsstempler.")
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