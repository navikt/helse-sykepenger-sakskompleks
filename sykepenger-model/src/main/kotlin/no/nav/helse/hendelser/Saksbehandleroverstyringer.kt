package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.*
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Revurderingseventyr.Companion.tidligsteEventyr
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

sealed interface Overstyringsdata

sealed interface Overstyring {
    fun ferdighåndtert(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, person: Person)
    fun overstyring(eventyr: Revurderingseventyr, aktivitetslogg: IAktivitetslogg, person: Person)
    data object Enkeltoverstyring: Overstyring {
        override fun ferdighåndtert(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, person: Person) = person.håndterGjenoppta(hendelse, aktivitetslogg)
        override fun overstyring(eventyr: Revurderingseventyr, aktivitetslogg: IAktivitetslogg, person: Person) = person.igangsettOverstyring(eventyr, aktivitetslogg)
    }
}

data class Saksbehandleroverstyringer(
    private val meldingsreferanseId: UUID,
    private val innsendt: LocalDateTime,
    private val overstyringer: List<Overstyringsdata>
) : Hendelse, Overstyring {

    override val behandlingsporing = Behandlingsporing.IngenArbeidsgiver

    override val metadata = HendelseMetadata(
        meldingsreferanseId = meldingsreferanseId,
        avsender = SAKSBEHANDLER,
        innsendt = innsendt,
        registrert = LocalDateTime.now(),
        automatiskBehandling = false
    )

    override fun ferdighåndtert(hendelse: Hendelse, aktivitetslogg: IAktivitetslogg, person: Person) {
        /** Gjør ingenting her ettersom vi først håndterer gjennoppta etter at alle overstyringene er håndtert **/
        aktivitetslogg.info("Håndterte overstyring ${hendelse::class.simpleName}")
    }


    private val eventyr = mutableListOf<Revurderingseventyr>()
    override fun overstyring(eventyr: Revurderingseventyr, aktivitetslogg: IAktivitetslogg, person: Person) {
        /** Samler opp eventyr slik at vi etter håndtering av alle overstyrigene kan igangsette det tidligste **/
        this.eventyr.add(eventyr)
    }

    internal fun håndter(aktivitetslogg: IAktivitetslogg, person: Person) {
        overstyringer.forEach { overstyringsdata ->
            when (overstyringsdata) {
                is OverstyrTidslinjeData -> person.håndter(OverstyrTidslinje(overstyringsdata, metadata, this), aktivitetslogg)
                is MinimumSykdomsgradsvurderingMeldingData -> person.håndter(MinimumSykdomsgradsvurderingMelding(overstyringsdata, metadata, this), aktivitetslogg)
            }
        }
        val tidligsteEventyr = eventyr.tidligsteEventyr() ?: return aktivitetslogg.info("Det ble tilsynelatende ikke endret noe som følge av saksbehandlingsoverstyringene.")
        person.igangsettOverstyring(tidligsteEventyr, aktivitetslogg)
        person.håndterGjenoppta(this, aktivitetslogg)
    }
}
