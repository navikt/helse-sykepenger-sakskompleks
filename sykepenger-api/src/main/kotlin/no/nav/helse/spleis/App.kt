package no.nav.helse.spleis

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import no.nav.helse.spleis.config.ApplicationConfiguration
import no.nav.helse.spleis.config.AzureAdAppConfig
import no.nav.helse.spleis.config.DataSourceConfiguration
import no.nav.helse.spleis.config.KtorConfig
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
    val teller = AtomicInteger(0)
    val app = createApp(config.ktorConfig, config.azureConfig, config.azureClient, config.spurteDuClient, config.dataSourceConfiguration::getDataSource, teller)
    app.start(wait = true)
}

internal fun createApp(ktorConfig: KtorConfig, azureConfig: AzureAdAppConfig, azureClient: AzureTokenProvider?, spurteDuClient: SpurteDuClient?, dataSourceProvider: () -> DataSource, teller: AtomicInteger, collectorRegistry: CollectorRegistry = CollectorRegistry()) =
    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment {
            ktorConfig.configure(this)
            log = logg
            module {
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
                preStopHook(teller)
                install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
                requestResponseTracing(LoggerFactory.getLogger("no.nav.helse.spleis.api.Tracing"), collectorRegistry)
                nais(teller, collectorRegistry)
                azureAdAppAuthentication(azureConfig)
                val dataSource = dataSourceProvider()
                spannerApi(dataSource, spurteDuClient, azureClient)
                sporingApi(dataSource)
                installGraphQLApi(dataSource)
            }
        },
        configure = {
            this.responseWriteTimeoutSeconds = 30
        }
    )

