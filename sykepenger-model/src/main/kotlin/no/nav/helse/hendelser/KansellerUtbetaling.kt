package no.nav.helse.hendelser

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.ArbeidstakerHendelse
import java.time.LocalDateTime

class KansellerUtbetaling(
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    internal val fagsystemId: String,
    internal val saksbehandler: String,
    internal val saksbehandlerEpost: String,
    internal val opprettet: LocalDateTime,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : ArbeidstakerHendelse(aktivitetslogg) {

    override fun aktørId() = aktørId

    override fun fødselsnummer() = fødselsnummer

    override fun organisasjonsnummer() = organisasjonsnummer

}
