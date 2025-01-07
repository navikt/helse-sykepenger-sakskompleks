package no.nav.helse.testhelpers

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje

internal sealed class TestEvent(opprettet: LocalDateTime) : SykdomshistorikkHendelse {
    companion object {
        val søknad = Søknad(LocalDateTime.now()).kilde
        val inntektsmelding = Inntektsmelding(LocalDateTime.now()).kilde
        val sykmelding = Sykmelding(LocalDateTime.now()).kilde
        val saksbehandler = OverstyrTidslinje(LocalDateTime.now()).kilde
        val testkilde = TestHendelse(LocalDateTime.now()).kilde
    }

    val kilde = SykdomshistorikkHendelse.Hendelseskilde(this::class.simpleName ?: "Ukjent", UUID.randomUUID(), opprettet)

    // Objects impersonating real-life sources of sickness timeline days
    class Inntektsmelding(opprettet: LocalDateTime) : TestEvent(opprettet)
    class Sykmelding(opprettet: LocalDateTime) : TestEvent(opprettet)
    class OverstyrTidslinje(opprettet: LocalDateTime) : TestEvent(opprettet)
    class Søknad(opprettet: LocalDateTime) : TestEvent(opprettet)
    class TestHendelse(opprettet: LocalDateTime) : TestEvent(opprettet)

    override fun sykdomstidslinje(): Sykdomstidslinje {
        error("ikke i bruk")
    }
}
