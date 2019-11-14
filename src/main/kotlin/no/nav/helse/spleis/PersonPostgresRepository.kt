package no.nav.helse.spleis

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.person.Person
import javax.sql.DataSource

class PersonPostgresRepository(private val dataSource: DataSource,
                               private val probe: PostgresProbe = PostgresProbe) : PersonRepository {

    override fun hentPerson(aktørId: String): Person? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT data FROM person WHERE aktor_id = ? ORDER BY id DESC LIMIT 1", aktørId).map {
                it.string("data")
            }.asSingle)
        }?.let {
            Person.fromJson(it)
        }?.also {
            probe.personLestFraDb()
        }
    }

}
