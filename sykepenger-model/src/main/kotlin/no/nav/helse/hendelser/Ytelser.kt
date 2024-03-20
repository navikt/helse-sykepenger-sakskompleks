package no.nav.helse.hendelser


import java.time.LocalDate
import java.util.UUID
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse

class Ytelser(
    meldingsreferanseId: UUID,
    aktørId: String,
    fødselsnummer: String,
    organisasjonsnummer: String,
    private val vedtaksperiodeId: String,
    private val foreldrepenger: Foreldrepenger,
    private val svangerskapspenger: Svangerskapspenger,
    private val pleiepenger: Pleiepenger,
    private val omsorgspenger: Omsorgspenger,
    private val opplæringspenger: Opplæringspenger,
    private val institusjonsopphold: Institusjonsopphold,
    private val arbeidsavklaringspenger: Arbeidsavklaringspenger,
    private val dagpenger: Dagpenger,
    aktivitetslogg: Aktivitetslogg
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, aktivitetslogg), SykdomshistorikkHendelse {

    companion object {
        internal val Periode.familieYtelserPeriode get() = oppdaterFom(start.minusWeeks(4))
    }

    internal fun erRelevant(other: UUID) = other.toString() == vedtaksperiodeId

    internal fun valider(periode: Periode, skjæringstidspunkt: LocalDate, maksdato: LocalDate, erForlengelse: Boolean ): Boolean {
        if (periode.start > maksdato) return true

        val periodeForOverlappsjekk = periode.start til minOf(periode.endInclusive, maksdato)
        arbeidsavklaringspenger.valider(this, skjæringstidspunkt, periodeForOverlappsjekk)
        dagpenger.valider(this, skjæringstidspunkt, periodeForOverlappsjekk)
        if (foreldrepenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med foreldrepenger`)
        if (svangerskapspenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med svangerskapspenger`)
        if (pleiepenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med pleiepenger`)
        if (omsorgspenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med omsorgspenger`)
        if (opplæringspenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med opplæringspenger`)
        if (institusjonsopphold.overlapper(this, periodeForOverlappsjekk)) funksjonellFeil(Varselkode.`Overlapper med institusjonsopphold`)

        return !harFunksjonelleFeilEllerVerre()
    }

    override fun dokumentsporing(): Dokumentsporing {
        return Dokumentsporing.overstyrTidslinje(meldingsreferanseId()) // TODO
    }

    override fun oppdaterFom(other: Periode): Periode {
        return other
    }

    override fun element(): Sykdomshistorikk.Element {
        val kilde = SykdomshistorikkHendelse.Hendelseskilde("Ytelser", meldingsreferanseId(), registrert())
        return foreldrepenger.sykdomshistorikkElement(meldingsreferanseId(), kilde)
    }

    internal fun oppdaterHistorikk(periode: Periode, periodeRettEtter: Periode?, oppdaterHistorikk: () -> Unit) {
        if (!skalOppdatereHistorikk(periode, periodeRettEtter)) return
        oppdaterHistorikk()
    }

    private fun skalOppdatereHistorikk(periode: Periode, periodeRettEtter: Periode?): Boolean {
        return foreldrepenger.skalOppdatereHistorikk(periode, periodeRettEtter)
    }
}
