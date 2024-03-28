package no.nav.helse.serde.migration

import org.junit.jupiter.api.Test

internal class V293AvsluttetUberegnedeOmgjøringerTest: MigrationTest(V293AvsluttetUberegnedeOmgjøringer()) {

    @Test
    fun `migrerer uberegnede omgjøringer`() {
        assertMigration("/migrations/293/expected.json", "/migrations/293/original.json")
    }
}