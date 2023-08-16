package no.nav.helse.hendelser.inntektsmelding

import java.time.LocalDate
import no.nav.helse.erRettFør
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.omsluttendePeriode
import no.nav.helse.hendelser.Periode.Companion.periodeRettFør
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.varsel
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode

internal class DagerFraInntektsmelding(
    private val inntektsmelding: Inntektsmelding
): IAktivitetslogg by inntektsmelding {
    private val opprinneligPeriode = inntektsmelding.sykdomstidslinje().periode()
    private val arbeidsdager = mutableSetOf<LocalDate>()
    private val gjenståendeDager = opprinneligPeriode?.toMutableSet() ?: mutableSetOf()
    private var dagerHåndtert = false
    private val alleDager get() = (opprinneligPeriode?: emptySet()) + arbeidsdager
    private val håndterteDager get() = alleDager - gjenståendeDager
    private val dokumentsporing = Dokumentsporing.inntektsmeldingDager(meldingsreferanseId())

    private companion object {
        private const val MAKS_ANTALL_DAGER_MELLOM_TIDLIGERE_OG_NY_AGP_FOR_HÅNDTERING_AV_DAGER = 10
    }

    internal fun accept(visitor: DagerFraInntektsmeldingVisitor) {
        visitor.visitGjenståendeDager(gjenståendeDager)
    }

    internal fun meldingsreferanseId() = inntektsmelding.meldingsreferanseId()
    internal fun leggTil(hendelseIder: MutableSet<Dokumentsporing>) : Boolean {
        dagerHåndtert = true
        return hendelseIder.add(dokumentsporing)
    }
    internal fun alleredeHåndtert(hendelseIder: Set<Dokumentsporing>) = dokumentsporing in hendelseIder

    internal fun vurdertTilOgMed(dato: LocalDate) {
        inntektsmelding.trimLeft(dato)
        gjenståendeDager.removeAll {gjenstående -> gjenstående <= dato}
    }
    internal fun oppdatertFom(periode: Periode) = inntektsmelding.oppdaterFom(periode)
    internal fun leggTilArbeidsdagerFør(dato: LocalDate) {
        checkNotNull(opprinneligPeriode) { "Forventer ikke å utvide en tom sykdomstidslinje" }
        inntektsmelding.padLeft(dato)
        val oppdatertPeriode = inntektsmelding.sykdomstidslinje().periode() ?: return
        if (opprinneligPeriode == oppdatertPeriode) return
        val arbeidsdagerFør = oppdatertPeriode - opprinneligPeriode
        if (!arbeidsdager.addAll(arbeidsdagerFør)) return
        gjenståendeDager.addAll(arbeidsdagerFør)
    }

    internal fun håndterPeriodeRettFør(periode: Periode, oppdaterSykdom: (sykdomstidslinje: SykdomstidslinjeHendelse) -> Sykdomstidslinje): Sykdomstidslinje {
        val periodeRettFør = periodeRettFør(periode) ?: return Sykdomstidslinje()
        return håndter(periodeRettFør, oppdaterSykdom)
    }

    private fun overlappendeDager(periode: Periode) =  periode.intersect(gjenståendeDager)

    private fun periodeRettFør(periode: Periode) = gjenståendeDager.periodeRettFør(periode.start)

    internal fun skalHåndteresAv(periode: Periode): Boolean {
        val overlapperMedVedtaksperiode = overlappendeDager(periode).isNotEmpty()
        val periodeRettFør = periodeRettFør(periode) != null
        val vedtaksperiodeRettFør = gjenståendeDager.isNotEmpty() && periode.endInclusive.erRettFør(gjenståendeDager.first())
        return overlapperMedVedtaksperiode || periodeRettFør || vedtaksperiodeRettFør
    }

    internal fun skalHåndteresAvRevurdering(periode: Periode, sammenhengende: Periode, arbeidsgiverperiode: Arbeidsgiverperiode?): Boolean {
        val overlapperMedVedtaksperiode = overlappendeDager(periode).isNotEmpty()
        val periodeRettFør = periodeRettFør(periode) != null
        if (overlapperMedVedtaksperiode || periodeRettFør) return true
        // vedtaksperiodene før dagene skal bare håndtere dagene om de nye opplyste dagene er nærmere enn 10 dager fra forrige AGP-beregning
        if (opprinneligPeriode == null || arbeidsgiverperiode == null) return false
        val periodeMellomForrigeAgpOgNyAgp = arbeidsgiverperiode.omsluttendePeriode?.periodeMellom(opprinneligPeriode.start) ?: return false
        return periodeMellomForrigeAgpOgNyAgp.count() <= MAKS_ANTALL_DAGER_MELLOM_TIDLIGERE_OG_NY_AGP_FOR_HÅNDTERING_AV_DAGER && sammenhengende.contains(periodeMellomForrigeAgpOgNyAgp)
    }

    internal fun harBlittHåndtertAv(periode: Periode) = håndterteDager.any { it in periode }

    private fun håndter(periode: Periode, oppdaterSykdom: (sykdomstidslinje: SykdomstidslinjeHendelse) -> Sykdomstidslinje): Sykdomstidslinje {
        val arbeidsgiverSykdomstidslinje = oppdaterSykdom(BitAvInntektsmelding(inntektsmelding, periode))
        gjenståendeDager.removeAll(periode)
        dagerHåndtert = true
        return arbeidsgiverSykdomstidslinje
    }

    internal fun håndter(periode: Periode, arbeidsgiverperiode: () -> Arbeidsgiverperiode?, oppdaterSykdom: (sykdomstidslinje: SykdomstidslinjeHendelse) -> Sykdomstidslinje): Sykdomstidslinje? {
        val overlappendeDager = overlappendeDager(periode).takeUnless { it.isEmpty() } ?: return null
        val arbeidsgiverSykdomstidslinje = håndter(overlappendeDager.omsluttendePeriode!!, oppdaterSykdom)
        if (gjenståendeDager.isEmpty()) inntektsmelding.validerArbeidsgiverperiode(periode, arbeidsgiverperiode())
        return arbeidsgiverSykdomstidslinje.subset(periode)
    }

    internal fun skalValideresAv(periode: Periode) = inntektsmelding.skalValideresAv(periode)

    internal fun valider(periode: Periode) {
        inntektsmelding.valider(periode, NullObserver)
    }

    internal fun valider(periode: Periode, arbeidsgiverperiode: Arbeidsgiverperiode?) {
        valider(periode)
        inntektsmelding.validerArbeidsgiverperiode(periode, arbeidsgiverperiode)
    }

    internal fun noenDagerHåndtert() = dagerHåndtert

    internal fun påvirker(sykdomstidslinje: Sykdomstidslinje): Boolean {
        val periode = sykdomstidslinje.periode() ?: return false
        return sykdomstidslinje.påvirkesAv(BitAvInntektsmelding(inntektsmelding, periode).sykdomstidslinje())
    }

    internal fun håndterKorrigering(gammelAgp: Arbeidsgiverperiode?, håndterDager: () -> Unit) {
        if (opprinneligPeriode == null) return
        if (gammelAgp == null) return håndterDager()
        val periodeMellom = gammelAgp.omsluttendePeriode?.periodeMellom(opprinneligPeriode.start)
        if (periodeMellom != null && periodeMellom.count() > MAKS_ANTALL_DAGER_MELLOM_TIDLIGERE_OG_NY_AGP_FOR_HÅNDTERING_AV_DAGER) {
            varsel(Varselkode.RV_IM_24, "Ignorerer dager fra inntektsmelding fordi perioden mellom gammel agp og opplyst agp er mer enn 10 dager")
            return
        }
        info("Håndterer dager fordi perioden mellom gammel agp og opplyst agp er mindre enn 10 dager")
        håndterDager()
    }

    private class BitAvInntektsmelding(
        private val inntektsmelding: Inntektsmelding,
        private val periode: Periode
    ): SykdomstidslinjeHendelse(inntektsmelding.meldingsreferanseId(), inntektsmelding) {
        override fun sykdomstidslinje() = inntektsmelding.sykdomstidslinje().subset(periode)
        override fun valider(periode: Periode, subsumsjonObserver: SubsumsjonObserver) = throw IllegalStateException("Ikke i bruk")
        override fun leggTil(hendelseIder: MutableSet<Dokumentsporing>) = throw IllegalStateException("Ikke i bruk")
    }
}

internal interface DagerFraInntektsmeldingVisitor {
    fun visitGjenståendeDager(dager: Set<LocalDate>)
}