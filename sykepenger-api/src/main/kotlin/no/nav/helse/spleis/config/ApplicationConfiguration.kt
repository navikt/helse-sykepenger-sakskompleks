package no.nav.helse.spleis.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.spurtedu.SpurteDuClient
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.engine.EngineConnectorBuilder
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import no.nav.helse.spleis.SpekematClient
import no.nav.helse.spleis.objectMapper

internal class ApplicationConfiguration(env: Map<String, String> = System.getenv()) {
    internal val ktorConfig = KtorConfig(
        httpPort = env["HTTP_PORT"]?.toInt() ?: 8080
    )

    internal val azureConfig = AzureAdAppConfig(
        clientId = env.getValue("AZURE_APP_CLIENT_ID"),
        issuer = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
        jwkProvider = JwkProviderBuilder(URI(env.getValue("AZURE_OPENID_CONFIG_JWKS_URI")).toURL()).build(),
    )

    internal val azureClient = createAzureTokenClientFromEnvironment(env)
    internal val spurteDuClient = SpurteDuClient(
        objectMapper = objectMapper,
        tokenProvider = azureClient
    )
    internal val spekematClient = SpekematClient(
        tokenProvider = azureClient,
        objectMapper = objectMapper,
        scope = env.getValue("SPEKEMAT_SCOPE")
    )

    // Håndter on-prem og gcp database tilkobling forskjellig
    internal val dataSourceConfiguration = DataSourceConfiguration(
        jdbcUrl = env["DATABASE_JDBC_URL"],
        gcpProjectId = env["GCP_TEAM_PROJECT_ID"],
        databaseRegion = env["DATABASE_REGION"],
        databaseInstance = env["DATABASE_INSTANCE"],
        databaseUsername = env["DATABASE_SPLEIS_API_USERNAME"],
        databasePassword = env["DATABASE_SPLEIS_API_PASSWORD"],
        databaseName = env["DATABASE_SPLEIS_API_DATABASE"]
    )
}

internal class KtorConfig(private val httpPort: Int = 8080) {
    fun configure(builder: EngineConnectorBuilder) {
        builder.port = httpPort
    }
}

internal class AzureAdAppConfig(
    private val clientId: String,
    private val issuer: String,
    private val jwkProvider: JwkProvider
) {
    fun configureVerification(configuration: JWTAuthenticationProvider.Config) {
        configuration.verifier(jwkProvider, issuer) {
            withAudience(clientId)
        }
        configuration.validate { credentials -> JWTPrincipal(credentials.payload) }
    }

    private fun String.getJson(): JsonNode {
        val (responseCode, responseBody) = this.fetchUrl()
        if (responseCode >= 300 || responseBody == null) throw RuntimeException("got status $responseCode from ${this}.")
        return jacksonObjectMapper().readTree(responseBody)
    }

    private fun String.fetchUrl() = with(URI(this).toURL().openConnection() as HttpURLConnection) {
        requestMethod = "GET"
        connectTimeout = Duration.ofSeconds(5).toMillis().toInt()
        readTimeout = Duration.ofSeconds(5).toMillis().toInt()
        val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
        responseCode to stream?.bufferedReader()?.readText()
    }
}
