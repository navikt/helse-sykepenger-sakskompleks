package no.nav.helse.spleis

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.UUID
import javax.sql.DataSource
import no.nav.helse.spleis.config.ApplicationConfiguration
import no.nav.helse.spleis.config.AzureAdAppConfig
import no.nav.helse.spleis.config.KtorConfig
import no.nav.helse.spleis.dao.HendelseDao
import no.nav.helse.spleis.dao.PersonDao
import no.nav.helse.spleis.graphql.Api.installGraphQLApi
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

internal val nyObjectmapper get() = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())
    .setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
        indentObjectsWith(DefaultIndenter("  ", "\n"))
    })

internal val objectMapper = nyObjectmapper
internal val logg = LoggerFactory.getLogger("no.nav.helse.spleis.api.Application")

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, err ->
        logg.error("Uncaught exception in thread ${thread.name}: {}", err.message, err)
    }

    val config = ApplicationConfiguration()
    val dataSource by lazy {
        // viktig å cache resultatet fra getDataSource() fordi den gir en -ny- tilkobling hver gang.
        // gjentatte kall til getDataSource() vil til slutt tømme databasen for tilkoblinger
        config.dataSourceConfiguration.getDataSource()
    }
    val app = createApp(config.ktorConfig, config.azureConfig, config.spekematClient, config.spurteDuClient, { dataSource })
    app.start(wait = true)
}

internal fun createApp(
    ktorConfig: KtorConfig,
    azureConfig: AzureAdAppConfig,
    spekematClient: SpekematClient,
    spurteDuClient: SpurteDuClient?,
    dataSourceProvider: () -> DataSource,
    collectorRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
) =
    embeddedServer(
        factory = Netty,
        environment = applicationEnvironment {
            log = logg
        },
        configure = {
            this.responseWriteTimeoutSeconds = 30
            connector {
                ktorConfig.configure(this)
            }
        },
        {
            azureAdAppAuthentication(azureConfig)
            lagApplikasjonsmodul(spekematClient, spurteDuClient, dataSourceProvider, collectorRegistry)
        }
    )

internal fun Application.lagApplikasjonsmodul(
    spekematClient: SpekematClient,
    spurteDuClient: SpurteDuClient?,
    dataSourceProvider: () -> DataSource,
    collectorRegistry: PrometheusMeterRegistry
) {
    install(CallId) {
        header("callId")
        verify { it.isNotEmpty() }
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        logger = LoggerFactory.getLogger("no.nav.helse.spleis.api.CallLogging")
        level = Level.INFO
        callIdMdc("callId")
        disableDefaultColors()
        filter { call -> call.request.path().startsWith("/api/") }
    }
    install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
    requestResponseTracing(LoggerFactory.getLogger("no.nav.helse.spleis.api.Tracing"), collectorRegistry)
    nais(collectorRegistry)

    val hendelseDao = HendelseDao(dataSourceProvider)
    val personDao = PersonDao(dataSourceProvider)

    spannerApi(hendelseDao, personDao, spurteDuClient)
    sporingApi(hendelseDao, personDao)
    installGraphQLApi(spekematClient, hendelseDao, personDao)
}