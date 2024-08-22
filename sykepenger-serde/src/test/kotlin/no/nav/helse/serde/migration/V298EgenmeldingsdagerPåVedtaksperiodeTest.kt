package no.nav.helse.serde.migration

import org.junit.jupiter.api.Test

internal class V298EgenmeldingsdagerPåVedtaksperiodeTest: MigrationTest(V298EgenmeldingsdagerPåVedtaksperiode()) {

    @Test
    fun `migrerer skjæringstidspunkt på behandlinger`() {
        assertMigration("/migrations/298/expected.json", "/migrations/298/original.json")
    }
}