package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender.SYSTEM
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.AktivitetsloggObserver
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode

sealed class PersonHendelse(
    private val meldingsreferanseId: UUID,
    protected val fødselsnummer: String,
    protected val aktørId: String,
    private var aktivitetslogg: IAktivitetslogg
) : Hendelse, Aktivitetskontekst {

    init {
        aktivitetslogg.kontekst(this)
    }

    override fun behov() = aktivitetslogg.behov()

    fun wrap(other: (IAktivitetslogg) -> IAktivitetslogg, block: () -> Unit): IAktivitetslogg {
        val kopi = aktivitetslogg
        aktivitetslogg = other(aktivitetslogg)
        block()
        aktivitetslogg = kopi
        return this
    }

    fun aktørId() = aktørId
    fun fødselsnummer() = fødselsnummer

    override fun meldingsreferanseId() = meldingsreferanseId
    override fun innsendt(): LocalDateTime = LocalDateTime.now()
    override fun registrert(): LocalDateTime = innsendt()
    override fun avsender() = SYSTEM
    override fun navn(): String = javaClass.simpleName

    final override fun toSpesifikkKontekst() = this.javaClass.canonicalName.split('.').last().let {
        SpesifikkKontekst(it, mapOf(
            "meldingsreferanseId" to meldingsreferanseId().toString(),
            "aktørId" to aktørId(),
            "fødselsnummer" to fødselsnummer()
        ) + kontekst())
    }

    protected open fun kontekst(): Map<String, String> = emptyMap()

    fun toLogString() = aktivitetslogg.toString()

    override fun info(melding: String, vararg params: Any?) = aktivitetslogg.info(melding, *params)
    override fun varsel(kode: Varselkode) = aktivitetslogg.varsel(kode)
    override fun behov(type: Aktivitet.Behov.Behovtype, melding: String, detaljer: Map<String, Any?>) =
        aktivitetslogg.behov(type, melding, detaljer)
    override fun funksjonellFeil(kode: Varselkode) = aktivitetslogg.funksjonellFeil(kode)
    override fun logiskFeil(melding: String, vararg params: Any?) = aktivitetslogg.logiskFeil(melding, *params)
    override fun harAktiviteter() = aktivitetslogg.harAktiviteter()
    override fun harVarslerEllerVerre() = aktivitetslogg.harVarslerEllerVerre()
    override fun harFunksjonelleFeilEllerVerre() = aktivitetslogg.harFunksjonelleFeilEllerVerre()
    override fun aktivitetsteller() = aktivitetslogg.aktivitetsteller()
    override fun barn() = aktivitetslogg.barn()
    override fun kontekst(kontekst: Aktivitetskontekst) = aktivitetslogg.kontekst(kontekst)
    override fun kontekst(parent: Aktivitetslogg, kontekst: Aktivitetskontekst) = aktivitetslogg.kontekst(parent, kontekst)
    override fun kontekster() = aktivitetslogg.kontekster()

    override fun register(observer: AktivitetsloggObserver) {
        aktivitetslogg.register(observer)
    }

    companion object {
        fun wrap(hendelse: PersonHendelse, block: () -> Unit) =
            hendelse.wrap(::FunksjonelleFeilTilVarsler, block)
    }
}