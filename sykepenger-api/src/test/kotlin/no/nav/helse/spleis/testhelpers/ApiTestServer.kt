package no.nav.helse.spleis.testhelpers

import com.github.navikt.tbd_libs.test_support.TestDataSource
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.mockk.every
import io.mockk.mockkStatic
import io.prometheus.client.CollectorRegistry
import java.net.Socket
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.person.Person
import no.nav.helse.serde.serialize
import no.nav.helse.spleis.Issuer
import no.nav.helse.spleis.JwtStub
import no.nav.helse.spleis.config.AzureAdAppConfig
import no.nav.helse.spleis.config.KtorConfig
import no.nav.helse.spleis.createApp
import no.nav.helse.spleis.dao.HendelseDao
import no.nav.helse.spleis.databaseContainer
import no.nav.helse.spleis.handleRequest
import no.nav.helse.spleis.nais
import no.nav.helse.spleis.randomPort
import no.nav.helse.spleis.responseBody
import no.nav.helse.økonomi.Inntekt
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions

internal class ApiTestServer(private val port: Int = randomPort()) {

    private val dataSource: TestDataSource = databaseContainer.nyTilkobling()

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private lateinit var jwtStub: JwtStub

    private lateinit var app: ApplicationEngine
    private lateinit var appBaseUrl: String
    private val teller = AtomicInteger()

    internal fun clean() {
        teller.set(0)
        dataSource.cleanUp()
    }

    internal fun tearDown() {
        CollectorRegistry.defaultRegistry.clear()
        app.stop(1000L, 1000L)
        wireMockServer.stop()
    }

    internal fun start() {
        mockkStatic("no.nav.helse.spleis.NaisKt")
        every { any<Application>().nais(any(), any()) } returns Unit

        //Stub ID provider (for authentication of REST endpoints)
        wireMockServer.start()
        Awaitility.await("vent på WireMockServer har startet")
            .atMost(5, TimeUnit.SECONDS)
            .until {
                try {
                    Socket("localhost", wireMockServer.port()).use { it.isConnected }
                } catch (err: Exception) {
                    false
                }
            }
        jwtStub = JwtStub("Microsoft Azure AD", wireMockServer)
        WireMock.stubFor(jwtStub.stubbedJwkProvider())
        WireMock.stubFor(jwtStub.stubbedConfigProvider())

        appBaseUrl = "http://localhost:$port"
        app = createApp(
            KtorConfig(httpPort = port),
            AzureAdAppConfig(
                clientId = "spleis_azure_ad_app_id",
                configurationUrl = "${wireMockServer.baseUrl()}/config"
            ),
            null,
            null,
            { dataSource.ds },
            teller
        )

        app.start(wait = false)
    }

    internal fun createToken(audience: String = Issuer.AUDIENCE) = jwtStub.createTokenFor(audience)

    internal fun httpPost(
        path: String,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        accessToken: String? = createToken(),
        testBlock: String.() -> Unit = {}
    ) {
        val connection = appBaseUrl.handleRequest(HttpMethod.Get, path) {
            doOutput = true
            accessToken?.let { setRequestProperty(HttpHeaders.Authorization, "Bearer $it") }
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
            val input = body.toByteArray(Charsets.UTF_8)
            outputStream.write(input, 0, input.size)
        }
        Assertions.assertEquals(expectedStatus.value, connection.responseCode)
        testBlock(connection.responseBody)
    }

    internal fun lagrePerson(aktørId: String, fødselsnummer: String, person: Person) {
        val serialisertPerson = person.serialize()
        sessionOf(dataSource.ds, returnGeneratedKey = true).use {
            val personId = it.run(queryOf("INSERT INTO person (fnr, aktor_id, skjema_versjon, data) VALUES (?, ?, ?, (to_json(?::json)))",
                fødselsnummer.toLong(), aktørId.toLong(), serialisertPerson.skjemaVersjon, serialisertPerson.json).asUpdateAndReturnGeneratedKey)
            it.run(queryOf("INSERT INTO person_alias (fnr, person_id) VALUES (?, ?)",
                fødselsnummer.toLong(), personId!!).asExecute)

        }
    }

    private fun lagreHendelse(
        fødselsnummer: String,
        meldingsReferanse: UUID,
        meldingstype: HendelseDao.Meldingstype = HendelseDao.Meldingstype.INNTEKTSMELDING,
        data: String = "{}"
    ) {
        sessionOf(dataSource.ds).use {
            it.run(
                queryOf(
                    "INSERT INTO melding (fnr, melding_id, melding_type, data) VALUES (?, ?, ?, (to_json(?::json)))",
                    fødselsnummer.toLong(),
                    meldingsReferanse.toString(),
                    meldingstype.toString(),
                    data
                ).asExecute
            )
        }
    }

    internal fun lagreInntektsmelding(fødselsnummer: String, meldingsReferanse: UUID, beregnetInntekt: Inntekt, førsteFraværsdag: LocalDate) {
        lagreHendelse(
            fødselsnummer = fødselsnummer,
            meldingsReferanse = meldingsReferanse,
            meldingstype = HendelseDao.Meldingstype.INNTEKTSMELDING,
            data = """
                {
                    "beregnetInntekt": "$beregnetInntekt",
                    "mottattDato": "${LocalDateTime.now()}",
                    "@opprettet": "${LocalDateTime.now()}",
                    "foersteFravaersdag": "$førsteFraværsdag",
                    "@id": "$meldingsReferanse"
                }
            """.trimIndent()
        )
    }

    internal fun lagreSykmelding(fødselsnummer: String, meldingsReferanse: UUID, fom: LocalDate, tom: LocalDate) {
        lagreHendelse(
            fødselsnummer = fødselsnummer,
            meldingsReferanse = meldingsReferanse,
            meldingstype = HendelseDao.Meldingstype.NY_SØKNAD,
            data = """
                {
                    "@opprettet": "${LocalDateTime.now()}",
                    "@id": "$meldingsReferanse",
                    "fom": "$fom",
                    "tom": "$tom"
                }
            """.trimIndent()
        )
    }

    internal fun lagreSøknadNav(fødselsnummer: String, meldingsReferanse: UUID, fom: LocalDate, tom: LocalDate, sendtNav: LocalDateTime) {
        lagreHendelse(
            fødselsnummer = fødselsnummer,
            meldingsReferanse = meldingsReferanse,
            meldingstype = HendelseDao.Meldingstype.SENDT_SØKNAD_NAV,
            data = """
                {
                    "@opprettet": "${LocalDateTime.now()}",
                    "@id": "$meldingsReferanse",
                    "fom": "$fom",
                    "tom": "$tom",
                    "sendtNav": "$sendtNav"
                }
            """.trimIndent()
        )
    }

}
