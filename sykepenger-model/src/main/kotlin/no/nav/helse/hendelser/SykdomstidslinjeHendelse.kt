package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.nesteDag
import no.nav.helse.person.Behandlinger
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde

sealed class SykdomstidslinjeHendelse(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    organisasjonsnummer: String,
    opprettet: LocalDateTime,
    melding: Melding? = null,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, aktivitetslogg),
    SykdomshistorikkHendelse {

    private val håndtertAv = mutableSetOf<UUID>()
    private var nesteFraOgMed: LocalDate = LocalDate.MIN
    internal val kilde: Hendelseskilde = Hendelseskilde(melding ?: this::class, meldingsreferanseId(), opprettet)

    override fun oppdaterFom(other: Periode): Periode {
        // strekker vedtaksperioden tilbake til å måte første dag
        val førsteDag = sykdomstidslinje().førsteDag()
        return other.oppdaterFom(førsteDag)
    }

    internal fun noenHarHåndtert() = håndtertAv.isNotEmpty()

    internal abstract fun erRelevant(other: Periode): Boolean

    internal fun vurdertTilOgMed(dato: LocalDate) {
        nesteFraOgMed = dato.nesteDag
        trimSykdomstidslinje(nesteFraOgMed)
    }

    protected open fun trimSykdomstidslinje(fom: LocalDate) {}

    internal fun periode(): Periode {
        return sykdomstidslinje().periode() ?: LocalDate.MIN.somPeriode()
    }

    override fun equals(other: Any?): Boolean = other is SykdomstidslinjeHendelse
        && this.meldingsreferanseId() == other.meldingsreferanseId()

    internal fun leggTil(vedtaksperiodeId: UUID, behandlinger: Behandlinger): Boolean {
        håndtertAv.add(vedtaksperiodeId)
        // return behandlinger.oppdaterDokumentsporing(dokumentsporing())
        return true
    }

    override fun hashCode(): Int {
        return meldingsreferanseId().hashCode()
    }
}

