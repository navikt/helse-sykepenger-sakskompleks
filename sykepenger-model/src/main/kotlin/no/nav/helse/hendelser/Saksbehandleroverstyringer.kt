package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.*
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

sealed interface Overstyringsdata

data class Saksbehandleroverstyringer(
    private val meldingsreferanseId: UUID,
    private val innsendt: LocalDateTime,
    private val overstyringer: List<Overstyringsdata>
) : Hendelse {

    override val behandlingsporing = Behandlingsporing.IngenArbeidsgiver

    override val metadata = HendelseMetadata(
        meldingsreferanseId = meldingsreferanseId,
        avsender = SAKSBEHANDLER,
        innsendt = innsendt,
        registrert = LocalDateTime.now(),
        automatiskBehandling = false
    )

    internal fun håndter(aktivitetslogg: IAktivitetslogg, person: Person) {
        overstyringer.forEach { hendelse ->
            when (hendelse) {
                is OverstyrTidslinjeData -> person.håndter(OverstyrTidslinje(hendelse, metadata), aktivitetslogg) { /* ikke gjenoppta */ }
                is MinimumSykdomsgradsvurderingMeldingData -> person.håndter(MinimumSykdomsgradsvurderingMelding(hendelse, metadata), aktivitetslogg) { /* ikke gjenoppta */ }
            }
        }
    }
}
