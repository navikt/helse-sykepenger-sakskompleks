val mainClass = "no.nav.helse.AppKt"

val innteksmeldingKontraktVersion = "2020.04.06-ab8f786"
val syfokafkaVersion = "2022.08.23-10.31-21b5aa2b"
val mockkVersion = "1.12.4"
val jsonSchemaValidatorVersion = "1.0.70"

dependencies {
    implementation(project(":sykepenger-model"))
    implementation(libs.rapids.and.rivers)
    implementation(libs.bundles.database)
    implementation(libs.flyway)

    testImplementation(libs.testcontainers) {
        exclude("com.fasterxml.jackson.core")
    }
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("com.github.navikt:inntektsmelding-kontrakt:$innteksmeldingKontraktVersion")
    testImplementation("com.github.navikt:sykepengesoknad-kafka:$syfokafkaVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClass
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
}
