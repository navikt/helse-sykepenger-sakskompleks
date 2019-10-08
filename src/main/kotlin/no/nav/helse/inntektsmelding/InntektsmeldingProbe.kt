package no.nav.helse.inntektsmelding

import io.prometheus.client.Counter
import no.nav.helse.inntektsmelding.domain.Inntektsmelding
import org.slf4j.LoggerFactory

class InntektsmeldingProbe {

    companion object {
        private val log = LoggerFactory.getLogger(InntektsmeldingProbe::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

        val innteksmeldingerMottattCounterName = "inntektsmeldinger_mottatt_totals"

        private val inntektsmeldingMottattCounter = Counter.build(innteksmeldingerMottattCounterName, "Antall inntektsmeldinger mottatt")
                .register()
    }

    fun mottattInntektsmelding(inntektsmelding: Inntektsmelding) {
        log.info("mottok inntektsmelding med id=${inntektsmelding.inntektsmeldingId} " +
                "for arbeidstaker med aktørId = ${inntektsmelding.arbeidstakerAktorId} " +
                "fra arbeidsgiver med virksomhetsnummer ${inntektsmelding.virksomhetsnummer} " +
                "evt. arbeidsgiverAktørId = ${inntektsmelding.arbeidsgiverAktorId}")
        sikkerLogg.info("${inntektsmelding.jsonNode}")
        inntektsmeldingMottattCounter.inc()
    }



}
