package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsavgjørelse
import no.nav.helse.hendelser.utbetaling.avvist
import no.nav.helse.person.Dokumentsporing.Companion.ider
import no.nav.helse.person.Dokumentsporing.Companion.sisteInntektsmeldingId
import no.nav.helse.person.Dokumentsporing.Companion.søknadIder
import no.nav.helse.person.Dokumentsporing.Companion.tilSubsumsjonsformat
import no.nav.helse.person.Generasjoner.Generasjon.Companion.dokumentsporing
import no.nav.helse.person.Generasjoner.Generasjon.Companion.erUtbetaltPåForskjelligeUtbetalinger
import no.nav.helse.person.Generasjoner.Generasjon.Companion.jurist
import no.nav.helse.person.Generasjoner.Generasjon.Companion.lagreTidsnæreInntekter
import no.nav.helse.person.Generasjoner.Generasjon.Endring.Companion.dokumentsporing
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
import no.nav.helse.person.aktivitetslogg.GodkjenningsbehovBuilder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.harId
import no.nav.helse.utbetalingstidslinje.Maksdatosituasjon
import no.nav.helse.utbetalingstidslinje.Rettighetsvurdering
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

internal class Generasjoner(generasjoner: List<Generasjon>) {
    internal constructor(sykmeldingsperiode: Periode, sykdomstidslinje: Sykdomstidslinje, dokumentsporing: Dokumentsporing, søknad: Søknad) : this(mutableListOf(Generasjon.nyGenerasjon(sykdomstidslinje, dokumentsporing, sykmeldingsperiode, søknad)))

    private val utbetalingene get() = generasjoner.mapNotNull(Generasjon::utbetaling)
    private val generasjoner = generasjoner.toMutableList()
    private val siste get() = generasjoner.lastOrNull()?.utbetaling()

    private val observatører = mutableListOf<GenerasjonObserver>()

    internal fun addObserver(observatør: GenerasjonObserver) {
        observatører.add(observatør)
        generasjoner.forEach { it.addObserver(observatør) }
    }

    internal fun accept(visitor: GenerasjonerVisitor) {
        visitor.preVisitGenerasjoner(generasjoner)
        generasjoner.forEach { generasjon ->
            generasjon.accept(visitor)
        }
        visitor.postVisitGenerasjoner(generasjoner)
    }

    internal fun sykdomstidslinje() = generasjoner.last().sykdomstidslinje()

    internal fun harUtbetaling() = siste != null && siste!!.gyldig()
    internal fun trekkerTilbakePenger() = siste?.trekkerTilbakePenger() == true
    internal fun utbetales() = generasjoner.any { it.erInFlight() }
    internal fun erAvsluttet() = generasjoner.last().erAvsluttet()
    internal fun erAvvist() = siste?.erAvvist() == true
    internal fun harUtbetalinger() = siste?.harUtbetalinger() == true
    internal fun erUbetalt() = siste?.erUbetalt() == true

    internal fun kanForkastes(arbeidsgiverUtbetalinger: List<Utbetaling>) = generasjoner.all { generasjon ->
        generasjon.kanForkastes(arbeidsgiverUtbetalinger)
    }
    internal fun harAvsluttede() = generasjoner.any { generasjon -> generasjon.utbetaling()?.erAvsluttet() == true }
    internal fun harId(utbetalingId: UUID) = utbetalingene.harId(utbetalingId)
    internal fun hørerIkkeSammenMed(other: Utbetaling) = generasjoner.lastOrNull { generasjon  -> generasjon.utbetaling()?.gyldig() == true }?.utbetaling()?.hørerSammen(other) == false
    internal fun håndterUtbetalinghendelse(hendelse: UtbetalingHendelse) = generasjoner.any { it.håndterUtbetalinghendelse(hendelse) }

    internal fun lagreTidsnæreInntekter(
        arbeidsgiver: Arbeidsgiver,
        skjæringstidspunkt: LocalDate,
        hendelse: IAktivitetslogg,
        oppholdsperiodeMellom: Periode?
    ) {
        generasjoner.lagreTidsnæreInntekter(arbeidsgiver, skjæringstidspunkt, hendelse, oppholdsperiodeMellom)
    }

    internal fun gjelderIkkeFor(hendelse: Utbetalingsavgjørelse) = siste?.gjelderFor(hendelse) != true

    internal fun erHistorikkEndretSidenBeregning(infotrygdhistorikk: Infotrygdhistorikk) =
        infotrygdhistorikk.harEndretHistorikk(siste!!)

    internal fun overlapperMed(other: Generasjoner): Boolean {
        if (!this.harUtbetalinger() || !other.harUtbetalinger()) return false
        return this.siste!!.overlapperMed(other.siste!!)
    }

    internal fun valider(simulering: Simulering) {
        siste!!.valider(simulering)
    }

    internal fun erKlarForGodkjenning() = siste?.erKlarForGodkjenning() ?: false

    internal fun simuler(hendelse: IAktivitetslogg) = siste!!.simuler(hendelse)

    internal fun godkjenning(hendelse: IAktivitetslogg, erForlengelse: Boolean, kanForkastes: Boolean) {
        generasjoner.last().godkjenning(hendelse, erForlengelse, kanForkastes)
    }

    internal fun nyUtbetaling(
        vedtaksperiodeSomLagerUtbetaling: UUID,
        fødselsnummer: String,
        arbeidsgiver: Arbeidsgiver,
        grunnlagsdata: VilkårsgrunnlagElement,
        periode: Periode,
        hendelse: IAktivitetslogg,
        maksimumSykepenger: Rettighetsvurdering,
        utbetalingstidslinje: Utbetalingstidslinje
    ): Utbetalingstidslinje {
        val strategi = if (this.harAvsluttede()) Arbeidsgiver::lagRevurdering else Arbeidsgiver::lagUtbetaling
        val denNyeUtbetalingen = strategi(arbeidsgiver, hendelse, fødselsnummer, utbetalingstidslinje, maksimumSykepenger.maksdato, maksimumSykepenger.forbrukteDager, maksimumSykepenger.gjenståendeDager, periode)
        denNyeUtbetalingen.nyVedtaksperiodeUtbetaling(vedtaksperiodeSomLagerUtbetaling)
        generasjoner.last().utbetaling(denNyeUtbetalingen, grunnlagsdata, hendelse)
        return utbetalingstidslinje.subset(periode)
    }

    internal fun forkast(hendelse: Hendelse) {
        leggTilNyGenerasjon(generasjoner.last().forkastVedtaksperiode(hendelse))
        generasjoner.last().forkastetGenerasjon()
    }
    internal fun forkastUtbetaling(hendelse: IAktivitetslogg) {
        generasjoner.last().forkastUtbetaling(hendelse)
    }
    internal fun harIkkeUtbetaling() = generasjoner.last().harIkkeUtbetaling()


    fun vedtakFattet(utbetalingsavgjørelse: Utbetalingsavgjørelse) {
        this.generasjoner.last().vedtakFattet(utbetalingsavgjørelse)
    }
    fun avslutt(hendelse: IAktivitetslogg) {
        this.generasjoner.last().avslutt(hendelse)
    }

    internal fun sykmeldingsperiode() = this.generasjoner.first().sykmeldingsperiode()
    internal fun periode() = this.generasjoner.last().periode()

    // sørger for ny generasjon når vedtaksperioden går ut av Avsluttet/AUU,
    // men bare hvis det ikke er laget en ny allerede fra før
    fun sikreNyGenerasjon(hendelse: Hendelse) {
        leggTilNyGenerasjon(generasjoner.last().sikreNyGenerasjon(hendelse))
    }

    private fun leggTilNyGenerasjon(generasjon: Generasjon?) {
        if (generasjon == null) return
        check(generasjoner.last().tillaterNyGenerasjon(generasjon)) {
            "siste generasjon ${generasjoner.last()} tillater ikke opprettelse av ny generasjon $generasjon"
        }
        observatører.forEach {
            generasjon.addObserver(it)
        }
        this.generasjoner.add(generasjon)
        generasjon.nyGenerasjon()
    }

    fun klarForUtbetaling(): Boolean {
        return generasjoner.last().klarForUtbetaling()
    }

    fun harÅpenGenerasjon(): Boolean {
        return generasjoner.last().harÅpenGenerasjon()
    }

    internal fun jurist(jurist: MaskinellJurist, vedtaksperiodeId: UUID) =
        generasjoner.jurist(jurist, vedtaksperiodeId)

    internal fun hendelseIder() = generasjoner.dokumentsporing
    internal fun dokumentsporing() = generasjoner.dokumentsporing.ider()

    internal fun søknadIder() = generasjoner.dokumentsporing.søknadIder()
    internal fun sisteInntektsmeldingId() = generasjoner.dokumentsporing.sisteInntektsmeldingId()

    internal fun oppdaterDokumentsporing(dokument: Dokumentsporing): Boolean {
        return generasjoner.last().oppdaterDokumentsporing(dokument)
    }

    fun dokumentHåndtert(dokumentsporing: Dokumentsporing) =
        generasjoner.any { it.dokumentHåndtert(dokumentsporing) }

    fun håndterEndring(person: Person, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) {
        generasjoner.last().håndterEndring(arbeidsgiver, hendelse)?.also {
            leggTilNyGenerasjon(it)
        }
        person.sykdomshistorikkEndret(hendelse)
    }

    fun erFattetVedtak(): Boolean {
        return generasjoner.last().erFattetVedtak()
    }

    fun erUtbetaltPåForskjelligeUtbetalinger(other: Generasjoner): Boolean {
        return this.generasjoner.erUtbetaltPåForskjelligeUtbetalinger(other.generasjoner)
    }

    internal fun trengerArbeidsgiverperiode() = generasjoner.dokumentsporing.sisteInntektsmeldingId() == null
    internal fun førsteGenerasjonOpprettet() {
        generasjoner.single().nyGenerasjon()
    }

    internal class Generasjonkilde(
        val meldingsreferanseId: UUID,
        val innsendt: LocalDateTime,
        val registert: LocalDateTime,
        val avsender: Avsender
    ) {
        constructor(hendelse: Hendelse): this(hendelse.meldingsreferanseId(), hendelse.innsendt(), hendelse.registrert(), hendelse.avsender())

        internal fun accept(visitor: GenerasjonerVisitor) {
            visitor.preVisitGenerasjonkilde(meldingsreferanseId, innsendt, registert, avsender)
        }
    }


    internal class Generasjon(
        private val id: UUID,
        private var tilstand: Tilstand,
        private val endringer: MutableList<Endring>,
        private var vedtakFattet: LocalDateTime?,
        private var avsluttet: LocalDateTime?,
        private val kilde: Generasjonkilde
    ) {
        private val gjeldende get() = endringer.last()
        private val periode: Periode get() = gjeldende.periode
        private val tidsstempel = endringer.first().tidsstempel
        private val dokumentsporing get() = endringer.dokumentsporing
        private val observatører = mutableListOf<GenerasjonObserver>()

        constructor(tilstand: Tilstand, endringer: List<Endring>, avsluttet: LocalDateTime?, kilde: Generasjonkilde) : this(UUID.randomUUID(), tilstand, endringer.toMutableList(), null, avsluttet, kilde)

        init {
            check(endringer.isNotEmpty()) {
                "Må ha endringer for at det skal være vits med en generasjon"
            }
        }


        internal fun addObserver(observatør: GenerasjonObserver) {
            observatører.add(observatør)
        }

        override fun toString() = "$periode - $tilstand"

        fun accept(visitor: GenerasjonerVisitor) {
            visitor.preVisitGenerasjon(id, tidsstempel, tilstand, periode, vedtakFattet, avsluttet, kilde)
            endringer.forEach { it.accept(visitor) }
            kilde.accept(visitor)
            visitor.postVisitGenerasjon(id, tidsstempel, tilstand, periode, vedtakFattet, avsluttet, kilde)
        }

        fun sykmeldingsperiode() = endringer.first().sykmeldingsperiode
        fun periode() = periode

        // TODO: se på om det er nødvendig å støtte Dokumentsporing som et sett; eventuelt om Generasjon må ha et sett
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
            }

            override fun toString() = "$periode - $dokumentsporing - ${sykdomstidslinje.toShortString()}${utbetaling?.let { " - $it" } ?: ""}"

            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other !is Endring) return false
                return this.dokumentsporing == other.dokumentsporing
            }

            internal fun accept(visitor: GenerasjonerVisitor) {
                visitor.preVisitGenerasjonendring(id, tidsstempel, sykmeldingsperiode, periode, grunnlagsdata, utbetaling, dokumentsporing, sykdomstidslinje)
                grunnlagsdata?.accept(visitor)
                utbetaling?.accept(visitor)
                sykdomstidslinje.accept(visitor)
                visitor.postVisitGenerasjonendring(id, tidsstempel, sykmeldingsperiode, periode, grunnlagsdata, utbetaling, dokumentsporing, sykdomstidslinje)
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

            fun godkjenning(hendelse: IAktivitetslogg, erForlengelse: Boolean, kanForkastes: Boolean) {
                checkNotNull(utbetaling) { "Forventet ikke manglende utbetaling ved godkjenningsbehov" }
                checkNotNull(grunnlagsdata) { "Forventet ikke manglende vilkårsgrunnlag ved godkjennignsbehov" }
                val builder = GodkjenningsbehovBuilder(erForlengelse, kanForkastes, periode)
                grunnlagsdata.byggGodkjenningsbehov(builder)
                utbetaling.godkjenning(hendelse, builder)
            }
        }

        internal fun sykdomstidslinje() = endringer.last().sykdomstidslinje

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is Generasjon) return false
            return this.tilstand == other.tilstand && this.dokumentsporing == other.dokumentsporing
        }

        override fun hashCode(): Int {
            return this.dokumentsporing.hashCode()
        }

        internal fun erFattetVedtak() = vedtakFattet != null
        internal fun erInFlight() = erFattetVedtak() && !erAvsluttet()
        internal fun erAvsluttet() = avsluttet != null

        internal fun klarForUtbetaling() = this.tilstand in setOf(Tilstand.Uberegnet, Tilstand.UberegnetOmgjøring, Tilstand.UberegnetRevurdering)
        internal fun harÅpenGenerasjon() = this.tilstand in setOf(Tilstand.UberegnetRevurdering, Tilstand.UberegnetOmgjøring, Tilstand.TilInfotrygd)
        internal fun harIkkeUtbetaling() = this.tilstand in setOf(Tilstand.Uberegnet, Tilstand.UberegnetOmgjøring, Tilstand.TilInfotrygd)

        internal fun vedtakFattet(utbetalingsavgjørelse: Utbetalingsavgjørelse) {
            if (utbetalingsavgjørelse.avvist) return tilstand.vedtakAvvist(this, utbetalingsavgjørelse)
            tilstand.vedtakFattet(this, utbetalingsavgjørelse)
        }

        internal fun avslutt(hendelse: IAktivitetslogg) {
            tilstand.avslutt(this, hendelse)
        }

        internal fun forkastVedtaksperiode(hendelse: Hendelse): Generasjon? {
            return tilstand.forkastVedtaksperiode(this, hendelse)
        }

        private fun tilstand(nyTilstand: Tilstand, hendelse: IAktivitetslogg) {
            tilstand.leaving(this)
            tilstand = nyTilstand
            tilstand.entering(this, hendelse)
        }

        fun lagreTidsnæreInntekter(
            nyttSkjæringstidspunkt: LocalDate,
            arbeidsgiver: Arbeidsgiver,
            hendelse: IAktivitetslogg,
            oppholdsperiodeMellom: Periode?
        ) {
            endringer.lastOrNull { it.utbetaling != null }?.lagreTidsnæreInntekter(nyttSkjæringstidspunkt, arbeidsgiver, hendelse, oppholdsperiodeMellom)
        }

        fun forkastUtbetaling(hendelse: IAktivitetslogg) {
            tilstand.utenUtbetaling(this, hendelse)
        }

        fun utbetaling() = gjeldende.utbetaling

        fun utbetaling(utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg) {
            tilstand.utbetaling(this, utbetaling, grunnlagsdata, hendelse)
        }

        private fun medUtbetaling(utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement) {
            nyEndring(gjeldende.kopierMedUtbetaling(utbetaling, grunnlagsdata))
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

        fun håndterEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Generasjon? {
            return tilstand.håndterEndring(this, arbeidsgiver, hendelse)
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

        private fun nyGenerasjonMedEndring(arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse, starttilstand: Tilstand = Tilstand.Uberegnet): Generasjon {
            return Generasjon(
                tilstand = starttilstand,
                endringer = listOf(håndtereEndring(arbeidsgiver, hendelse)),
                avsluttet = null,
                kilde = Generasjonkilde(hendelse)
            )
        }
        private fun sikreNyGenerasjon(starttilstand: Tilstand, hendelse: Hendelse): Generasjon {
            return Generasjon(
                tilstand = starttilstand,
                endringer = listOf(endringer.last().kopierUtenUtbetaling()),
                avsluttet = null,
                kilde = Generasjonkilde(hendelse)
            )
        }

        private fun nyGenerasjonTilInfotrygd(hendelse: Hendelse) = Generasjon(
            tilstand = Tilstand.TilInfotrygd,
            endringer = listOf(this.gjeldende.kopierUtenUtbetaling()),
            avsluttet = LocalDateTime.now(),
            kilde = Generasjonkilde(hendelse)
        )

        fun sikreNyGenerasjon(hendelse: Hendelse): Generasjon? {
            return tilstand.sikreNyGenerasjon(this, hendelse)
        }

        fun tillaterNyGenerasjon(other: Generasjon): Boolean {
            return tilstand.tillaterNyGenerasjon(this, other)
        }

        fun håndterUtbetalinghendelse(hendelse: UtbetalingHendelse): Boolean {
            return tilstand.håndterUtbetalinghendelse(this, hendelse)
        }

        fun kanForkastes(arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
            return tilstand.kanForkastes(this, arbeidsgiverUtbetalinger)
        }

        private fun erUtbetalingAnnullert(arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
            val utbetalingen = checkNotNull(gjeldende.utbetaling) {
                "forventer at det er en utbetaling på denne generasjonen"
            }
            return Utbetaling.kanForkastes(utbetalingen, arbeidsgiverUtbetalinger)
        }

        private fun generasjonLukket() {
            observatører.forEach { it.generasjonLukket(id) }
        }
        private fun vedtakIverksatt(hendelse: IAktivitetslogg) {
            observatører.forEach { it.vedtakIverksatt(hendelse, id, avsluttet!!, periode, dokumentsporing.ider(), utbetaling()!!.id, vedtakFattet!!) }
        }
        private fun avsluttetUtenVedtak(hendelse: IAktivitetslogg) {
            observatører.forEach { it.generasjonLukket(id) }
            observatører.forEach { it.avsluttetUtenVedtak(hendelse, id, avsluttet!!, periode, dokumentsporing.ider()) }
        }

        internal fun nyGenerasjon() {
            val type = when(tilstand) {
                Tilstand.UberegnetRevurdering -> PersonObserver.GenerasjonOpprettetEvent.Type.Revurdering
                Tilstand.UberegnetOmgjøring -> PersonObserver.GenerasjonOpprettetEvent.Type.Omgjøring
                Tilstand.TilInfotrygd -> PersonObserver.GenerasjonOpprettetEvent.Type.TilInfotrygd
                else -> PersonObserver.GenerasjonOpprettetEvent.Type.Førstegangsbehandling
            }
            observatører.forEach { it.nyGenerasjon(id, kilde.meldingsreferanseId, kilde.innsendt, kilde.registert, kilde.avsender, type) }
        }

        internal fun forkastetGenerasjon() {
            observatører.forEach { it.generasjonForkastet(id) }
        }

        internal fun godkjenning(hendelse: IAktivitetslogg, erForlengelse: Boolean, kanForkastes: Boolean) {
            gjeldende.godkjenning(hendelse, erForlengelse, kanForkastes)
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
            val List<Generasjon>.sykmeldingsperiode get() = first().periode
            val List<Generasjon>.dokumentsporing get() = map { it.dokumentsporing }.reduce(Set<Dokumentsporing>::plus)

            fun nyGenerasjon(sykdomstidslinje: Sykdomstidslinje, dokumentsporing: Dokumentsporing, sykmeldingsperiode: Periode, søknad: Søknad) =
                Generasjon(
                    tilstand = Tilstand.Uberegnet,
                    endringer = listOf(
                        Endring(
                            grunnlagsdata = null,
                            utbetaling = null,
                            dokumentsporing = dokumentsporing,
                            sykdomstidslinje = sykdomstidslinje,
                            sykmeldingsperiode = sykmeldingsperiode,
                            periode = checkNotNull(sykdomstidslinje.periode()) { "kan ikke opprette generasjon på tom sykdomstidslinje" }
                        )
                    ),
                    avsluttet = null,
                    kilde = Generasjonkilde(søknad)
                )

            fun List<Generasjon>.jurist(jurist: MaskinellJurist, vedtaksperiodeId: UUID) =
                jurist.medVedtaksperiode(vedtaksperiodeId, dokumentsporing.tilSubsumsjonsformat(), sykmeldingsperiode)

            fun List<Generasjon>.lagreTidsnæreInntekter(arbeidsgiver: Arbeidsgiver, skjæringstidspunkt: LocalDate, hendelse: IAktivitetslogg, oppholdsperiodeMellom: Periode?) {
                lastOrNull { it.endringer.any { it.utbetaling != null } }?.lagreTidsnæreInntekter(skjæringstidspunkt, arbeidsgiver, hendelse, oppholdsperiodeMellom)
            }

            // hvorvidt man delte samme utbetaling før
            fun List<Generasjon>.erUtbetaltPåForskjelligeUtbetalinger(other: List<Generasjon>): Boolean {
                val forrigeIverksatteThis = forrigeIverksatte ?: return true
                val forrigeIverksatteOther = other.forrigeIverksatte ?: return true
                // hvis forrige iverksatte på *this* har ulik korrelasjonsId som siste iverksatte på *other* -> return true
                val utbetalingThis = checkNotNull(forrigeIverksatteThis.utbetaling()) {
                    "forventer at det skal være en utbetaling på en generasjon som er iverksatt"
                }
                val utbetalingOther = forrigeIverksatteOther.utbetaling() ?: return true // forrige periode kan være AUU
                return !utbetalingOther.hørerSammen(utbetalingThis)
            }

            private val List<Generasjon>.forrigeIverksatte get() = lastOrNull { it.vedtakFattet != null }
        }
        internal sealed interface Tilstand {
            fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {}
            fun leaving(generasjon: Generasjon) {}
            fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon? {
                generasjon.tilstand(TilInfotrygd, hendelse)
                return null
            }
            fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Generasjon? {
                error("Har ikke implementert håndtering av endring i $this")
            }
            fun vedtakAvvist(generasjon: Generasjon, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                error("Kan ikke avvise vedtak for generasjon i $this")
            }
            fun vedtakFattet(generasjon: Generasjon, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                error("Kan ikke fatte vedtak for generasjon i $this")
            }
            fun avslutt(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                error("Kan ikke avslutte generasjon i $this")
            }
            fun utenUtbetaling(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                error("Støtter ikke å forkaste utbetaling utbetaling i $this")
            }
            fun utbetaling(generasjon: Generasjon, utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg) {
                error("Støtter ikke å motta utbetaling i $this")
            }

            fun oppdaterDokumentsporing(generasjon: Generasjon, dokument: Dokumentsporing): Boolean {
                error("Støtter ikke å oppdatere dokumentsporing med $dokument i $this")
            }

            fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean

            fun sikreNyGenerasjon(generasjon: Generasjon, hendelse: Hendelse): Generasjon? {
                return null
            }
            fun tillaterNyGenerasjon(generasjon: Generasjon, other: Generasjon): Boolean = false
            fun håndterUtbetalinghendelse(generasjon: Generasjon, hendelse: UtbetalingHendelse) = false

            data object Uberegnet : Tilstand {
                override fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    check(generasjon.utbetaling() == null) { "skal ikke ha utbetaling og være uberegnet samtidig" }
                }

                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
                    return true
                }

                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Generasjon? {
                    generasjon.oppdaterMedEndring(arbeidsgiver, hendelse)
                    return null
                }

                override fun oppdaterDokumentsporing(generasjon: Generasjon, dokument: Dokumentsporing) =
                    generasjon.kopierMedDokument(dokument)

                override fun utenUtbetaling(generasjon: Generasjon, hendelse: IAktivitetslogg) {}

                override fun utbetaling(generasjon: Generasjon, utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg) {
                    generasjon.medUtbetaling(utbetaling, grunnlagsdata)
                    generasjon.tilstand(Beregnet, hendelse)
                }

                override fun avslutt(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.tilstand(AvsluttetUtenVedtak, hendelse)
                }
            }
            data object UberegnetOmgjøring : Tilstand by (Uberegnet) {
                override fun utbetaling(generasjon: Generasjon, utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg) {
                    generasjon.medUtbetaling(utbetaling, grunnlagsdata)
                    generasjon.tilstand(BeregnetOmgjøring, hendelse)
                }

                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
                    // TODO: her kunne vi sjekket om omgjøringen kommer til å skape problemer for andre;
                    // i så tilfelle kan vi ikke forkaste
                    return true
                }
            }
            data object UberegnetRevurdering : Tilstand by (Uberegnet) {
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) = true

                override fun utbetaling(generasjon: Generasjon, utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg) {
                    generasjon.medUtbetaling(utbetaling, grunnlagsdata)
                    generasjon.tilstand(BeregnetRevurdering, hendelse)
                }
                override fun avslutt(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    // TODO: ugyldig operasjon? kaste exception? ingen modelltester trigger denne
                    generasjon.tilstand(AvsluttetUtenVedtakRevurdering, hendelse)
                }
            }
            data object Beregnet : Tilstand {
                override fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    checkNotNull(generasjon.gjeldende.utbetaling)
                    checkNotNull(generasjon.gjeldende.grunnlagsdata)
                }

                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) = true


                override fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon? {
                    generasjon.gjeldende.forkastUtbetaling(hendelse)
                    return super.forkastVedtaksperiode(generasjon, hendelse)
                }

                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Generasjon? {
                    generasjon.gjeldende.forkastUtbetaling(hendelse)
                    generasjon.oppdaterMedEndring(arbeidsgiver, hendelse)
                    generasjon.tilstand(Uberegnet, hendelse)
                    return null
                }

                // TODO: denne overriden kan fjernes dersom utbetalingsperioder() i Vedtaksperiode hensyntar
                // bergnede perioder
                override fun utbetaling(generasjon: Generasjon, utbetaling: Utbetaling, grunnlagsdata: VilkårsgrunnlagElement, hendelse: IAktivitetslogg) {
                    generasjon.gjeldende.forkastUtbetaling(hendelse)
                    generasjon.medUtbetaling(utbetaling, grunnlagsdata)
                }

                override fun oppdaterDokumentsporing(generasjon: Generasjon, dokument: Dokumentsporing) =
                    generasjon.kopierMedDokument(dokument)

                override fun utenUtbetaling(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.utenUtbetaling(hendelse)
                    generasjon.tilstand(Uberegnet, hendelse)
                }

                override fun vedtakAvvist(generasjon: Generasjon, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                    // perioden kommer til å bli kastet til infotrygd
                }

                override fun vedtakFattet(generasjon: Generasjon, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                    generasjon.vedtakFattet = utbetalingsavgjørelse.avgjørelsestidspunkt
                    generasjon.generasjonLukket()
                    generasjon.tilstand(if (generasjon.gjeldende.utbetaling?.erAvsluttet() == true) VedtakIverksatt else VedtakFattet, utbetalingsavgjørelse)
                }
            }
            data object BeregnetOmgjøring : Tilstand by (Beregnet) {
                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Generasjon? {
                    generasjon.gjeldende.forkastUtbetaling(hendelse)
                    generasjon.oppdaterMedEndring(arbeidsgiver, hendelse)
                    generasjon.tilstand(UberegnetOmgjøring, hendelse)
                    return null
                }
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) = true
                override fun utenUtbetaling(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.utenUtbetaling(hendelse)
                    generasjon.tilstand(UberegnetOmgjøring, hendelse)
                }
            }
            data object BeregnetRevurdering : Tilstand by (Beregnet) {
                override fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon? {
                    generasjon.gjeldende.forkastUtbetaling(hendelse)
                    return super.forkastVedtaksperiode(generasjon, hendelse)
                }
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) = true

                override fun utenUtbetaling(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.utenUtbetaling(hendelse)
                    generasjon.tilstand(UberegnetRevurdering, hendelse)
                }
                override fun vedtakAvvist(generasjon: Generasjon, utbetalingsavgjørelse: Utbetalingsavgjørelse) {
                    generasjon.generasjonLukket()
                    generasjon.tilstand(RevurdertVedtakAvvist, utbetalingsavgjørelse)
                }
                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse): Generasjon? {
                    generasjon.gjeldende.forkastUtbetaling(hendelse)
                    generasjon.oppdaterMedEndring(arbeidsgiver, hendelse)
                    generasjon.tilstand(UberegnetRevurdering, hendelse)
                    return null
                }
            }
            data object RevurdertVedtakAvvist : Tilstand {
                override fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.nyGenerasjonTilInfotrygd(hendelse)
                }
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    generasjon.erUtbetalingAnnullert(arbeidsgiverUtbetalinger)
                override fun tillaterNyGenerasjon(generasjon: Generasjon, other: Generasjon): Boolean {
                    return true
                }
            }
            data object VedtakFattet : Tilstand {
                override fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    checkNotNull(generasjon.gjeldende.utbetaling)
                    checkNotNull(generasjon.gjeldende.grunnlagsdata)
                }
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    generasjon.erUtbetalingAnnullert(arbeidsgiverUtbetalinger)
                override fun tillaterNyGenerasjon(generasjon: Generasjon, other: Generasjon): Boolean {
                    return true
                }

                override fun sikreNyGenerasjon(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.sikreNyGenerasjon(UberegnetRevurdering, hendelse)
                }

                override fun håndterUtbetalinghendelse(generasjon: Generasjon, hendelse: UtbetalingHendelse): Boolean {
                    val utbetaling = checkNotNull(generasjon.gjeldende.utbetaling) { "forventer utbetaling" }
                    if (!utbetaling.gjelderFor(hendelse)) return false
                    if (utbetaling.erAvsluttet()) avslutt(generasjon, hendelse)
                    return true
                }

                override fun utenUtbetaling(generasjon: Generasjon, hendelse: IAktivitetslogg) {}

                override fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon? {
                    error("usikker på hvordan vi kan forkaste vedtaksperioden som har fått utbetaling godkjent, men ikke avsluttet i $this")
                }

                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) =
                    generasjon.nyGenerasjonMedEndring(arbeidsgiver, hendelse, UberegnetRevurdering)


                override fun oppdaterDokumentsporing(generasjon: Generasjon, dokument: Dokumentsporing): Boolean {
                    // oppdaterer ikke dokumentsporing på fattet vedtak;
                    // det må komme en endring via håndterEndring istedenfor
                    return false
                }

                override fun avslutt(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.tilstand(VedtakIverksatt, hendelse)
                }
            }

            data object AvsluttetUtenVedtak : Tilstand {
                override fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.vedtakFattet = null // det fattes ikke vedtak i AUU
                    generasjon.avsluttet = LocalDateTime.now()
                    generasjon.avsluttetUtenVedtak(hendelse)
                }
                override fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.nyGenerasjonTilInfotrygd(hendelse)
                }
                // TODO: her kunne vi sjekket om omgjøringen kommer til å skape problemer for andre;
                // i så tilfelle kan vi ikke forkaste
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    true
                override fun tillaterNyGenerasjon(generasjon: Generasjon, other: Generasjon): Boolean {
                    return true
                }

                override fun avslutt(generasjon: Generasjon, hendelse: IAktivitetslogg) {}

                override fun sikreNyGenerasjon(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.sikreNyGenerasjon(UberegnetOmgjøring, hendelse)
                }

                // det deles ut dokumentsporinger til alle vedtaksperioder så snart én har håndtert hendelsen;
                // det betyr også at det kommer til å bli sendt ut et overstyring igangsatt-event som vil
                // opprette en ny Uberegnet periode for Vedtaksperioden.
                // derfor legger vi den bare til i listen uten å gjøre noe mer spesielt
                override fun oppdaterDokumentsporing(generasjon: Generasjon, dokument: Dokumentsporing) =
                    generasjon.kopierMedDokument(dokument)

                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) =
                    generasjon.nyGenerasjonMedEndring(arbeidsgiver, hendelse, UberegnetOmgjøring)
            }
            data object AvsluttetUtenVedtakRevurdering : Tilstand by (AvsluttetUtenVedtak) {
                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) =
                    generasjon.nyGenerasjonMedEndring(arbeidsgiver, hendelse, UberegnetRevurdering)

                override fun sikreNyGenerasjon(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.sikreNyGenerasjon(UberegnetRevurdering, hendelse)
                }
            }
            data object VedtakIverksatt : Tilstand {
                override fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.avsluttet = LocalDateTime.now()
                    generasjon.vedtakIverksatt(hendelse)
                }
                override fun forkastVedtaksperiode(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.nyGenerasjonTilInfotrygd(hendelse)
                }
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>) =
                    generasjon.erUtbetalingAnnullert(arbeidsgiverUtbetalinger)
                override fun tillaterNyGenerasjon(generasjon: Generasjon, other: Generasjon): Boolean {
                    return true
                }

                override fun sikreNyGenerasjon(generasjon: Generasjon, hendelse: Hendelse): Generasjon {
                    return generasjon.sikreNyGenerasjon(UberegnetRevurdering, hendelse)
                }

                override fun håndterEndring(generasjon: Generasjon, arbeidsgiver: Arbeidsgiver, hendelse: SykdomshistorikkHendelse) =
                    generasjon.nyGenerasjonMedEndring(arbeidsgiver, hendelse, UberegnetRevurdering)

                // det deles ut dokumentsporinger til alle vedtaksperioder så snart én har håndtert hendelsen;
                // det betyr også at det kommer til å bli sendt ut et overstyring igangsatt-event som vil
                // opprette en ny Uberegnet periode for Vedtaksperioden.
                // derfor legger vi den bare til i listen uten å gjøre noe mer spesielt
                override fun oppdaterDokumentsporing(generasjon: Generasjon, dokument: Dokumentsporing) =
                    generasjon.kopierMedDokument(dokument)
            }
            data object TilInfotrygd : Tilstand {
                override fun entering(generasjon: Generasjon, hendelse: IAktivitetslogg) {
                    generasjon.avsluttet = LocalDateTime.now()
                }
                override fun kanForkastes(generasjon: Generasjon, arbeidsgiverUtbetalinger: List<Utbetaling>): Boolean {
                    error("forventer ikke å forkaste en periode som allerde er i $this")
                }
            }
        }
    }
}