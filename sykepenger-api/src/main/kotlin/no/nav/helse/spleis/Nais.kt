package no.nav.helse.spleis

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.io.CharArrayWriter

internal fun Application.nais(collectorRegistry: CollectorRegistry) {
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM)
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
        )
    }

    routing {
        get("/isalive") {
            call.respondText("ALIVE", ContentType.Text.Plain)
        }
        get("/isready") {
            call.respondText("READY", ContentType.Text.Plain)
        }
        get("/metrics") {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()
            val formatted = CharArrayWriter(1024)
                .also { TextFormat.write004(it, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names)) }
                .use { it.toString() }

            call.respondText(
                contentType = ContentType.parse(TextFormat.CONTENT_TYPE_004),
                text = formatted
            )
        }
    }
}
