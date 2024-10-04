package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.Ytelser.Companion.familieYtelserPeriode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje

class Foreldrepenger(
    private val foreldrepengeytelse: List<GradertPeriode>
): AnnenYtelseSomKanOppdatereHistorikk() {
    private val perioder get() = foreldrepengeytelse.map { it.periode }

    internal fun overlapper(aktivitetslogg: IAktivitetslogg, sykdomsperiode: Periode, erForlengelse: Boolean): Boolean {
        if (foreldrepengeytelse.isEmpty()) {
            aktivitetslogg.info("Bruker har ingen foreldrepenger")
            return false
        }
        val overlappsperiode = if (erForlengelse) sykdomsperiode else sykdomsperiode.familieYtelserPeriode
        return perioder.any { ytelse -> ytelse.overlapperMed(overlappsperiode) }.also { overlapper ->
            if (!overlapper) aktivitetslogg.info("Bruker har foreldrepenger, men det slår ikke ut på overlappsjekken")
        }
    }

    override fun sykdomstidslinje(meldingsreferanseId: UUID, registrert: LocalDateTime): Sykdomstidslinje {
        if (foreldrepengeytelse.isEmpty()) return Sykdomstidslinje()
        require(foreldrepengeytelse.map { it.periode }.grupperSammenhengendePerioder().size == 1) {"Ikke trygt å kalle sykdomstidslinjen til ${this.javaClass.simpleName} når det er huller i ytelser"}
        val hendelseskilde = SykdomshistorikkHendelse.Hendelseskilde(Ytelser::class, meldingsreferanseId, registrert)
        val førsteDag = foreldrepengeytelse.map { it.periode }.minOf { it.start }
        val sisteDag = foreldrepengeytelse.map { it.periode }.maxOf { it.endInclusive }
        val sykdomstidslinje = Sykdomstidslinje.andreYtelsedager(førsteDag, sisteDag, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Foreldrepenger)
        return sykdomstidslinje
    }

    override fun skalOppdatereHistorikk(vedtaksperiode: Periode, skjæringstidspunkt: LocalDate, vedtaksperiodeRettEtter: Periode?): Pair<Boolean, Companion.HvorforIkkeOppdatereHistorikk?> {
        return foreldrepengeytelse.skalOppdatereHistorikkIHalen(vedtaksperiode, skjæringstidspunkt, vedtaksperiodeRettEtter)
    }
}