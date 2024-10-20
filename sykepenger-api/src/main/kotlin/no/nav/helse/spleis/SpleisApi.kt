package no.nav.helse.spleis

import com.fasterxml.jackson.core.JsonParseException
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
import io.ktor.http.ContentType
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.dto.tilSpannerPersonDto
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.person.Person
import no.nav.helse.spleis.dao.HendelseDao
import no.nav.helse.spleis.dao.PersonDao
import no.nav.helse.spleis.sporing.serializePersonForSporing

internal fun Application.spannerApi(hendelseDao: HendelseDao, personDao: PersonDao, spurteDuClient: SpurteDuClient?) {
    routing {
        authenticate {
            get("/api/person-json/{maskertId?}") {
                withContext(Dispatchers.IO) {
                    val ident = call.fnr(personDao, spurteDuClient)
                    val serialisertPerson = personDao.hentPersonFraFnr(ident) ?: throw NotFoundException("Kunne ikke finne person for fødselsnummer")
                    val dto = serialisertPerson.tilPersonDto { hendelseDao.hentAlleHendelser(ident) }
                    val person = Person.gjenopprett(EmptyLog, dto)
                    call.respond(person.dto().tilSpannerPersonDto())
                }
            }

            get("/api/hendelse-json/{hendelse}") {
                withContext(Dispatchers.IO) {
                    val hendelseId = call.parameters["hendelse"] ?: throw IllegalArgumentException("Kall Mangler hendelse referanse")

                    val meldingsReferanse = try {
                        UUID.fromString(hendelseId)
                    } catch (e: IllegalArgumentException) {
                        throw BadRequestException("meldingsreferanse bør/skal være en UUID")
                    }

                    val hendelse =
                        hendelseDao.hentHendelse(meldingsReferanse) ?: throw NotFoundException("Kunne ikke finne hendelse for hendelsereferanse = ${hendelseId}")


                    call.respondText(hendelse, ContentType.Application.Json)
                }
            }
        }
    }
}

internal fun Application.sporingApi(hendelseDao: HendelseDao, personDao: PersonDao) {
    routing {
        authenticate {
            get("/api/vedtaksperioder") {
                withContext(Dispatchers.IO) {
                    val fnr =  call.fnr(personDao)
                    val person = personDao.hentPersonFraFnr(fnr) ?: throw NotFoundException("Kunne ikke finne person for fødselsnummer")
                    val dto = person.tilPersonDto { hendelseDao.hentAlleHendelser(fnr) }
                    call.respond(serializePersonForSporing(Person.gjenopprett(EmptyLog, dto)))
                }
            }
        }
    }
}

private fun RoutingCall.fnr(personDao: PersonDao, spurteDuClient: SpurteDuClient? = null): Long {
    val maskertId = this.parameters["maskertId"]
    val (fnr, aktorid) = this.identFraSpurteDu(spurteDuClient, maskertId) ?: this.identFraRequest()
    return fnr(personDao, fnr, aktorid) ?: throw BadRequestException("Mangler fnr eller aktorId i headers")
}

private fun ApplicationCall.identFraSpurteDu(spurteDuClient: SpurteDuClient?, maskertId: String?): Pair<Long?, Long?>? {
    if (spurteDuClient == null || maskertId == null) return null
    val id = try {
        UUID.fromString(maskertId)
    } catch (err: Exception) {
        return null
    }
    val token = bearerToken ?: return null
    val tekstinnhold = spurteDuClient.vis(id, token).text
    return try {
        val node = objectMapper.readTree(tekstinnhold)
        val ident = node.path("ident").asLong()
        val identype = node.path("identtype").asText()
        when (identype.lowercase()) {
            "fnr" -> Pair(ident, null)
            "aktorid" -> Pair(null, ident)
            else -> null
        }
    } catch (err: JsonParseException) {
        null
    }
}
private fun ApplicationCall.identFraRequest(): Pair<Long?, Long?> {
    return Pair(
        request.header("fnr")?.toLong(),
        request.header("aktorId")?.toLong()
    )
}

private fun fnr(personDao: PersonDao, fnr: Long?, aktørId: Long?): Long? {
    return fnr ?: aktørId?.let { personDao.hentFødselsnummer(aktørId) }
}

private val ApplicationCall.bearerToken: String? get() {
    val httpAuthHeader = request.parseAuthorizationHeader() ?: return null
    if (httpAuthHeader !is HttpAuthHeader.Single) return null
    return httpAuthHeader.blob
}
