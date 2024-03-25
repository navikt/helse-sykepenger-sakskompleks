package no.nav.helse.hendelser

import java.util.UUID
import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje

class Foreldrepenger(
    private val foreldrepengeytelse: List<Periode>
) {

    internal fun overlapper(aktivitetslogg: IAktivitetslogg, sykdomsperiode: Periode, erForlengelse: Boolean): Boolean {
        if (foreldrepengeytelse.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen foreldrepenger")
            return false
        }
        val overlappsperiode = if (erForlengelse) sykdomsperiode else sykdomsperiode.familieYtelserPeriode
        return foreldrepengeytelse.any { ytelse -> ytelse.overlapperMed(overlappsperiode) }.also { overlapper ->
            if (!overlapper) aktivitetslogg.info("Bruker har foreldrepenger, men det slår ikke ut på overlappsjekken")
        }
    }

    internal fun sykdomshistorikkElement(
        meldingsreferanseId: UUID,
        kilde: SykdomshistorikkHendelse.Hendelseskilde
    ): Sykdomshistorikk.Element {
        val førsteDato = foreldrepengeytelse.minOf { it.start }
        val sisteDato = foreldrepengeytelse.maxOf { it.endInclusive }
        return Sykdomshistorikk.Element.opprett(meldingsreferanseId, Sykdomstidslinje.andreYtelsedager(førsteDato, sisteDato, kilde, Dag.AndreYtelser.AnnenYtelse.Foreldrepenger))
    }

    internal fun skalOppdatereHistorikk(vedtaksperiode: Periode, periodeRettEtter: Periode? = null): Boolean {
        if (foreldrepengeytelse.isEmpty()) return false
        if (foreldrepengeytelse.size > 1) return false
        if (periodeRettEtter != null) return false
        val foreldrepengeperiode = foreldrepengeytelse.first()
        val fullstendigOverlapp = foreldrepengeperiode == vedtaksperiode
        val foreldrepengerIHalen = vedtaksperiode.contains(foreldrepengeperiode) && foreldrepengeperiode.endInclusive == vedtaksperiode.endInclusive
        return fullstendigOverlapp || foreldrepengerIHalen
    }
}
