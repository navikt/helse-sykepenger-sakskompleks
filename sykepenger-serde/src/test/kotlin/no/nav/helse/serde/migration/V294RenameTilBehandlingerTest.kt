package no.nav.helse.serde.migration

import org.junit.jupiter.api.Test

internal class V294RenameTilBehandlingerTest: MigrationTest(V294RenameTilBehandlinger()) {

    @Test
    fun `migrerer til behandlinger`() {
        assertMigration("/migrations/294/expected.json", "/migrations/294/original.json")
    }
}