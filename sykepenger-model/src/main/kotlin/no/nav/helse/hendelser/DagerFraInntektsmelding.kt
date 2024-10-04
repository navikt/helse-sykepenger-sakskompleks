package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import no.nav.helse.erRettFør
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Avsender.ARBEIDSGIVER
import no.nav.helse.hendelser.Periode.Companion.omsluttendePeriode
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.Periode.Companion.periodeRettFør
import no.nav.helse.nesteDag
import no.nav.helse.person.Behandlinger
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Revurderingseventyr
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.Vedtaksperiode.Companion.MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD
import no.nav.helse.person.Vedtaksperiode.Companion.påvirkerArbeidsgiverperiode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.varsel
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_23
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_8
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.økonomi.Prosentdel.Companion.prosent

internal class DagerFraInntektsmelding(
    private val arbeidsgiverperioder: List<Periode>,
    private val førsteFraværsdag: LocalDate?,
    private val mottatt: LocalDateTime,
    begrunnelseForReduksjonEllerIkkeUtbetalt: String?,
    private val avsendersystem: Inntektsmelding.Avsendersystem?,
    private val harFlereInntektsmeldinger: Boolean,
    private val harOpphørAvNaturalytelser: Boolean,
    hendelse: Hendelse
): Hendelse by hendelse {
    private companion object {
        private val ikkeStøttedeBegrunnelserForReduksjon = setOf(
            "BetvilerArbeidsufoerhet",
            "FiskerMedHyre",
            "StreikEllerLockout",
            "FravaerUtenGyldigGrunn",
            "BeskjedGittForSent",
            "IkkeLoenn"
        )

        private const val MAKS_ANTALL_DAGER_MELLOM_TIDLIGERE_OG_NY_AGP_FOR_HÅNDTERING_AV_DAGER = 10
        private const val MAKS_ANTALL_DAGER_MELLOM_FØRSTE_FRAVÆRSDAG_OG_AGP_FOR_HÅNDTERING_AV_DAGER = 20
    }

    // TODO: kilden må være av en type som arver SykdomshistorikkHendelse; altså BitAvInntektsmelding
    // krever nok at vi json-migrerer alle "Inntektsmelding" til "BitAvInntektsmelding" først
    internal val kilde = Hendelseskilde("Inntektsmelding", meldingsreferanseId(), mottatt)
    private val dokumentsporing = Dokumentsporing.inntektsmeldingDager(meldingsreferanseId())
    private val begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt.takeUnless { it.isNullOrBlank() }
    private val arbeidsgiverperiode = arbeidsgiverperioder.periode()
    private val overlappsperiode = when {
        // første fraværsdag er oppgitt etter arbeidsgiverperioden
        arbeidsgiverperiode == null || førsteFraværsdag != null && førsteFraværsdag > arbeidsgiverperiode.endInclusive.nesteDag -> førsteFraværsdag?.somPeriode()
        // kant-i-kant
        førsteFraværsdag?.forrigeDag == arbeidsgiverperiode.endInclusive -> arbeidsgiverperiode.oppdaterTom(arbeidsgiverperiode.endInclusive.nesteDag)
        else -> arbeidsgiverperiode
    }
    private val sykdomstidslinje = lagSykdomstidslinje()
    private val opprinneligPeriode = sykdomstidslinje.periode()

    private val arbeidsdager = mutableSetOf<LocalDate>()
    private val _gjenståendeDager = opprinneligPeriode?.toMutableSet() ?: mutableSetOf()
    val gjenståendeDager get() = _gjenståendeDager.toSet()

    private val håndterteDager = mutableSetOf<LocalDate>()
    private val ignorerDager: Boolean get() {
        if (begrunnelseForReduksjonEllerIkkeUtbetalt == null) return false
        if (begrunnelseForReduksjonEllerIkkeUtbetalt in ikkeStøttedeBegrunnelserForReduksjon) return true
        if (hulleteArbeidsgiverperiode()) return true
        return false
    }
    private var harValidert: Periode? = null

    private fun lagSykdomstidslinje(): Sykdomstidslinje {
        return tidslinjeForArbeidsgiverperioden() ?: tidslinjeForFørsteFraværsdag()
    }

    private fun tidslinjeForFørsteFraværsdag(): Sykdomstidslinje {
        if (begrunnelseForReduksjonEllerIkkeUtbetalt == null || førsteFraværsdag == null) return Sykdomstidslinje()
        return Sykdomstidslinje.sykedagerNav(førsteFraværsdag, førsteFraværsdag, 100.prosent, kilde)
    }

    private fun tidslinjeForArbeidsgiverperioden(): Sykdomstidslinje? {
        if (ignorerDager) return Sykdomstidslinje()
        if (arbeidsgiverperiodenKanIkkeTolkes()) return null

        val arbeidsdager = arbeidsgiverperiode?.let { Sykdomstidslinje.arbeidsdager(arbeidsgiverperiode, kilde) } ?: Sykdomstidslinje()
        val friskHelg = friskHelgMellomFørsteFraværsdagOgArbeidsgiverperioden()
        return arbeidsdager.merge(lagArbeidsgivertidslinje(), Dag.replace).merge(friskHelg)
    }

    private fun arbeidsgiverperiodenKanIkkeTolkes(): Boolean {
        if (førsteFraværsdag == null) return false
        val periodeMellom = arbeidsgiverperiode?.periodeMellom(førsteFraværsdag)
        return periodeMellom != null && periodeMellom.count() >= MAKS_ANTALL_DAGER_MELLOM_FØRSTE_FRAVÆRSDAG_OG_AGP_FOR_HÅNDTERING_AV_DAGER
    }

    private fun friskHelgMellomFørsteFraværsdagOgArbeidsgiverperioden(): Sykdomstidslinje {
        if (førsteFraværsdag == null) return Sykdomstidslinje()
        if (arbeidsgiverperiode?.erRettFør(førsteFraværsdag) != true) return Sykdomstidslinje()
        val helgen = arbeidsgiverperiode.periodeMellom(førsteFraværsdag) ?: return Sykdomstidslinje()
        return Sykdomstidslinje.arbeidsdager(helgen, kilde)
    }

    private fun lagArbeidsgivertidslinje(): Sykdomstidslinje {
        val agp = arbeidsgiverperioder.map(::arbeidsgiverdager).merge()
        if (begrunnelseForReduksjonEllerIkkeUtbetalt == null) return agp
        return agp.merge(arbeidsgiverperiodeNavSkalUtbetale().map(::sykedagerNav).merge(), Dag.replace)
    }

    private fun arbeidsgiverperiodeNavSkalUtbetale(): List<Periode> {
        if (arbeidsgiverperiode != null && (førsteFraværsdag == null || førsteFraværsdag in arbeidsgiverperiode)) return arbeidsgiverperioder
        return listOfNotNull(førsteFraværsdag?.somPeriode())
    }

    private fun arbeidsgiverdager(periode: Periode) = Sykdomstidslinje.arbeidsgiverdager(periode.start, periode.endInclusive, 100.prosent, kilde)
    private fun sykedagerNav(periode: Periode) = Sykdomstidslinje.sykedagerNav(periode.start, periode.endInclusive, 100.prosent, kilde)

    override fun innsendt() = mottatt
    override fun avsender() = ARBEIDSGIVER

    internal fun alleredeHåndtert(behandlinger: Behandlinger) = behandlinger.dokumentHåndtert(dokumentsporing)

    internal fun vurdertTilOgMed(dato: LocalDate) {
        _gjenståendeDager.removeAll { gjenstående -> gjenstående <= dato}
    }

    internal fun leggTilArbeidsdagerFør(dato: LocalDate) {
        if (arbeidsgiverperioder.isEmpty()) return
        if (opprinneligPeriode == null) return
        val oppdatertPeriode = opprinneligPeriode.oppdaterFom(dato)
        val arbeidsdagerFør = oppdatertPeriode.trim(opprinneligPeriode).flatten()
        if (!arbeidsdager.addAll(arbeidsdagerFør)) return
        _gjenståendeDager.addAll(arbeidsdagerFør)
    }

    private fun overlappendeDager(periode: Periode) =  periode.intersect(_gjenståendeDager)

    private fun periodeRettFør(periode: Periode) = _gjenståendeDager.periodeRettFør(periode.start)

    private fun skalHåndtere(periode: Periode): Boolean {
        val overlapperMedVedtaksperiode = overlappendeDager(periode).isNotEmpty()
        val periodeRettFør = periodeRettFør(periode) != null
        return overlapperMedVedtaksperiode || periodeRettFør
    }

    internal fun skalHåndteresAv(periode: Periode): Boolean {
        val vedtaksperiodeRettFør = _gjenståendeDager.isNotEmpty() && periode.endInclusive.erRettFør(_gjenståendeDager.first())
        return skalHåndtere(periode) || vedtaksperiodeRettFør || tomSykdomstidslinjeMenSkalValidere(periode) || egenmeldingerIForkantAvPerioden(periode)
    }

    private fun tomSykdomstidslinjeMenSkalValidere(periode: Periode) =
        (opprinneligPeriode == null && skalValideresAv(periode)).also { if (it) {
            harValidert = periode
        }}

    // om vedtaksperioden ikke direkte overlapper med gjenståendeDager, men gjenståendeDager er ikke tom, og vedtaksperioden overlapper
    // med første fraværsdag, betyr det at inntektsmeldingen informerer om egenmeldinger vi ikke har søknad for.
    // Om vi hadde en vedtaksperiode, ville dagene bli 'spist opp' via vurdertTilOgMed()
    private fun egenmeldingerIForkantAvPerioden(periode: Periode) =
        (overlappsperiode != null && overlappsperiode.overlapperMed(periode) && _gjenståendeDager.isNotEmpty())

    internal fun skalHåndteresAvRevurdering(periode: Periode, sammenhengende: Periode, arbeidsgiverperiode: Arbeidsgiverperiode?): Boolean {
        if (skalHåndtere(periode)) return true
        // vedtaksperiodene før dagene skal bare håndtere dagene om de nye opplyste dagene er nærmere enn 10 dager fra forrige AGP-beregning
        if (opprinneligPeriode == null || arbeidsgiverperiode == null) return false
        val periodeMellomForrigeAgpOgNyAgp = arbeidsgiverperiode.omsluttendePeriode?.periodeMellom(opprinneligPeriode.start) ?: return false
        return periodeMellomForrigeAgpOgNyAgp.count() <= MAKS_ANTALL_DAGER_MELLOM_TIDLIGERE_OG_NY_AGP_FOR_HÅNDTERING_AV_DAGER && sammenhengende.contains(periodeMellomForrigeAgpOgNyAgp)
    }

    internal fun harBlittHåndtertAv(periode: Periode) = håndterteDager.any { it in periode }

    internal fun bitAvInntektsmelding(vedtaksperiode: Periode): BitAvInntektsmelding? {
        val sykdomstidslinje = håndterDager(vedtaksperiode) ?: return null
        return BitAvInntektsmelding(meldingsreferanseId(), sykdomstidslinje, this, innsendt(), registrert(), navn())
    }

    internal fun tomBitAvInntektsmelding(vedtaksperiode: Periode): BitAvInntektsmelding {
        håndterDager(vedtaksperiode)
        return BitAvInntektsmelding(meldingsreferanseId(), Sykdomstidslinje(), this, innsendt(), registrert(), navn())
    }

    private fun håndterDager(vedtaksperiode: Periode): Sykdomstidslinje? {
        val periode = håndterDagerFør(vedtaksperiode) ?: return null
        if (periode.start != vedtaksperiode.start) info("Perioden ble strukket tilbake fra ${vedtaksperiode.start} til ${periode.start} (${ChronoUnit.DAYS.between(periode.start, vedtaksperiode.start)} dager)")
        val sykdomstidslinje = samletSykdomstidslinje(periode)

        håndterteDager.addAll(periode.toList())
        _gjenståendeDager.removeAll(periode)
        return sykdomstidslinje
    }

    private fun samletSykdomstidslinje(periode: Periode) =
        (arbeidsdagertidslinje() + sykdomstidslinje).subset(periode)
    private fun arbeidsdagertidslinje() =
        arbeidsdager.map { Sykdomstidslinje.arbeidsdager(it, it, kilde) }.merge()

    private fun håndterDagerFør(vedtaksperiode: Periode): Periode? {
        leggTilArbeidsdagerFør(vedtaksperiode.start)
        val gjenståendePeriode = _gjenståendeDager.omsluttendePeriode ?: return null
        val periode = vedtaksperiode.oppdaterFom(gjenståendePeriode)
        return periode
    }

    private fun skalValideresAv(periode: Periode) = overlappsperiode?.overlapperMed(periode) == true || ikkeUtbetaltAGPOgAGPOverlapper(periode)
    private fun ikkeUtbetaltAGPOgAGPOverlapper(periode: Periode): Boolean {
        if (begrunnelseForReduksjonEllerIkkeUtbetalt == null) return false
        if (arbeidsgiverperiode == null) return false
        if (førsteFraværsdag != null && førsteFraværsdag > arbeidsgiverperiode.endInclusive.nesteDag) return false
        return arbeidsgiverperiode.overlapperMed(periode)
    }

    internal fun valider(periode: Periode, gammelAgp: Arbeidsgiverperiode? = null) {
        if (!skalValideresAv(periode)) return
        if (harOpphørAvNaturalytelser) funksjonellFeil(Varselkode.RV_IM_7)
        if (harFlereInntektsmeldinger) varsel(Varselkode.RV_IM_22)
        validerBegrunnelseForReduksjonEllerIkkeUtbetalt()
        validerOverstigerMaksimaltTillatAvstandMellomTidligereAGP(gammelAgp)
    }

    private fun validerBegrunnelseForReduksjonEllerIkkeUtbetalt() {
        if (begrunnelseForReduksjonEllerIkkeUtbetalt == null) return
        info("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden på grunn av: %s".format(begrunnelseForReduksjonEllerIkkeUtbetalt))
        if (hulleteArbeidsgiverperiode()) funksjonellFeil(RV_IM_23)
        when (begrunnelseForReduksjonEllerIkkeUtbetalt) {
            in ikkeStøttedeBegrunnelserForReduksjon -> funksjonellFeil(RV_IM_8)
            "FerieEllerAvspasering" -> varsel(Varselkode.RV_IM_25)
            else -> varsel(RV_IM_8)
        }
    }

    private fun hulleteArbeidsgiverperiode(): Boolean {
        return arbeidsgiverperioder.size > 1 && (førsteFraværsdag == null || førsteFraværsdag in arbeidsgiverperiode!!)
    }

    internal fun validerArbeidsgiverperiode(periode: Periode, beregnetArbeidsgiverperiode: Arbeidsgiverperiode?) {
        if (!skalValideresAv(periode)) return
        if (_gjenståendeDager.isNotEmpty()) return validerFeilaktigNyArbeidsgiverperiode(periode, beregnetArbeidsgiverperiode)
        if (beregnetArbeidsgiverperiode != null) validerArbeidsgiverperiode(beregnetArbeidsgiverperiode)
        if (arbeidsgiverperioder.isEmpty()) info("Inntektsmeldingen mangler arbeidsgiverperiode. Vurder om vilkårene for sykepenger er oppfylt, og om det skal være arbeidsgiverperiode")
    }

    private fun validerArbeidsgiverperiode(arbeidsgiverperiode: Arbeidsgiverperiode) {
        if (arbeidsgiverperiode.sammenlign(arbeidsgiverperioder)) return
        if (avsendersystem == Inntektsmelding.Avsendersystem.NAV_NO && arbeidsgiverperioder.isEmpty()) return
        varsel(Varselkode.RV_IM_3)
    }

    private fun validerFeilaktigNyArbeidsgiverperiode(vedtaksperiode: Periode, beregnetArbeidsgiverperiode: Arbeidsgiverperiode?) {
        if (avsendersystem == Inntektsmelding.Avsendersystem.NAV_NO && arbeidsgiverperioder.isEmpty()) return
        beregnetArbeidsgiverperiode?.validerFeilaktigNyArbeidsgiverperiode(vedtaksperiode, this)
    }

    internal fun overlappendeSykmeldingsperioder(sykmeldingsperioder: List<Periode>): List<Periode> {
        if (overlappsperiode == null) return emptyList()
        return sykmeldingsperioder.mapNotNull { it.overlappendePeriode(overlappsperiode) }
    }

    internal fun perioderInnenfor16Dager(sykmeldingsperioder: List<Periode>): List<Periode> {
        if (overlappsperiode == null) return emptyList()
        return sykmeldingsperioder.mapNotNull { sykmeldingsperiode ->
            val erRettFør = overlappsperiode.erRettFør(sykmeldingsperiode)
            if (erRettFør) return@mapNotNull sykmeldingsperiode
            val dagerMellom =
                overlappsperiode.periodeMellom(sykmeldingsperiode.start)?.count() ?: return@mapNotNull null
            if (dagerMellom < MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD) sykmeldingsperiode else null
        }
    }

    internal fun overlapperMed(periode: Periode): Boolean {
        if (overlappsperiode == null) return false
        return overlappsperiode.overlapperMed(periode)
    }

    private fun validerOverstigerMaksimaltTillatAvstandMellomTidligereAGP(gammelAgp: Arbeidsgiverperiode?) {
        if (!overstigerMaksimaltTillatAvstandMellomTidligereAGP(gammelAgp)) return
        varsel(Varselkode.RV_IM_24, "Ignorerer dager fra inntektsmelding fordi perioden mellom gammel agp og opplyst agp er mer enn 10 dager")
    }
    private fun overstigerMaksimaltTillatAvstandMellomTidligereAGP(gammelAgp: Arbeidsgiverperiode?): Boolean {
        if (opprinneligPeriode == null) return false
        val periodeMellom = gammelAgp?.omsluttendePeriode?.periodeMellom(opprinneligPeriode.start) ?: return false
        return periodeMellom.count() > MAKS_ANTALL_DAGER_MELLOM_TIDLIGERE_OG_NY_AGP_FOR_HÅNDTERING_AV_DAGER
    }

    internal fun erKorrigeringForGammel(gammelAgp: Arbeidsgiverperiode?): Boolean {
        if (opprinneligPeriode == null) return true
        if (gammelAgp == null) return false
        if (overstigerMaksimaltTillatAvstandMellomTidligereAGP(gammelAgp)) return true
        info("Håndterer dager fordi perioden mellom gammel agp og opplyst agp er mindre enn 10 dager")
        return false
    }

    internal fun harPeriodeInnenfor16Dager(vedtaksperioder: List<Vedtaksperiode>): Boolean {
        val periode = sykdomstidslinje.periode() ?: return false
        return vedtaksperioder.påvirkerArbeidsgiverperiode(periode)
    }

    fun revurderingseventyr(): Revurderingseventyr? {
        val dagene = håndterteDager.omsluttendePeriode ?: harValidert ?: return null
        return Revurderingseventyr.arbeidsgiverperiode(this, dagene.start, dagene)
    }

    internal class BitAvInntektsmelding(
        private val meldingsreferanseId: UUID,
        private val sykdomstidslinje: Sykdomstidslinje,
        aktivitetslogg: IAktivitetslogg,
        private val innsendt: LocalDateTime,
        private val registert: LocalDateTime,
        private val navn : String
    ): SykdomshistorikkHendelse, Hendelse, IAktivitetslogg by (aktivitetslogg) {
        override fun oppdaterFom(other: Periode) =
            other.oppdaterFom(sykdomstidslinje().periode() ?: other)
        override fun sykdomstidslinje() = sykdomstidslinje

        override fun innsendt() = innsendt
        override fun registrert() = registert
        override fun navn() = navn
        override fun meldingsreferanseId() = meldingsreferanseId
        override fun avsender() = ARBEIDSGIVER
    }
}