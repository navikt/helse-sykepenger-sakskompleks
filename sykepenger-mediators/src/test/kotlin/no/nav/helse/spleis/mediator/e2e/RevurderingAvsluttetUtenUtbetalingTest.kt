package no.nav.helse.spleis.mediator.e2e

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

internal class RevurderingAvsluttetUtenUtbetalingTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `revurdering ved inntektsmelding for korte perioder`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 5.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 5.januar, sykmeldingsgrad = 100))
        )
        sendNySøknad(SoknadsperiodeDTO(fom = 6.januar, tom = 10.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 6.januar, tom = 10.januar, sykmeldingsgrad = 100))
        )
        sendNySøknad(SoknadsperiodeDTO(fom = 11.januar, tom = 17.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 11.januar, tom = 17.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 1.januar, tom = 16.januar)), førsteFraværsdag = 1.januar)
        sendVilkårsgrunnlag(2)
        sendYtelser(2)
        sendSimulering(2, SimuleringMessage.Simuleringstatus.OK)

        assertTilstander(0, "AVVENTER_INFOTRYGDHISTORIKK", "AVVENTER_INNTEKTSMELDING_REPLAY", "AVSLUTTET_UTEN_UTBETALING", "AVVENTER_BLOKKERENDE_PERIODE", "AVSLUTTET_UTEN_UTBETALING")
        assertTilstander(1, "AVVENTER_INNTEKTSMELDING_REPLAY", "AVSLUTTET_UTEN_UTBETALING", "AVVENTER_BLOKKERENDE_PERIODE", "AVSLUTTET_UTEN_UTBETALING")
        assertTilstander(2, "AVVENTER_INNTEKTSMELDING_REPLAY", "AVSLUTTET_UTEN_UTBETALING", "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE", "AVVENTER_VILKÅRSPRØVING", "AVVENTER_HISTORIKK", "AVVENTER_SIMULERING", "AVVENTER_GODKJENNING")
    }

    @Test
    fun `revurdering ved inntektsmelding for korte perioder - endring av skjæringstidspunkt`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 8.januar, tom = 10.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 8.januar, tom = 10.januar, sykmeldingsgrad = 100))
        )
        sendNySøknad(SoknadsperiodeDTO(fom = 11.januar, tom = 22.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 11.januar, tom = 22.januar, sykmeldingsgrad = 100))
        )
        sendNySøknad(SoknadsperiodeDTO(fom = 23.januar, tom = 23.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 23.januar, tom = 23.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(
            listOf(
                Periode(fom = 1.januar, tom = 6.januar),
                Periode(fom = 11.januar, tom = 21.januar),
            ), førsteFraværsdag = 11.januar
        )
        sendVilkårsgrunnlag(1)
        sendYtelser(1)
        sendSimulering(1, SimuleringMessage.Simuleringstatus.OK)

        assertTilstander(0, "AVVENTER_INFOTRYGDHISTORIKK", "AVVENTER_INNTEKTSMELDING_REPLAY", "AVSLUTTET_UTEN_UTBETALING", "AVVENTER_BLOKKERENDE_PERIODE", "AVSLUTTET_UTEN_UTBETALING")
        assertTilstander(1, "AVVENTER_INNTEKTSMELDING_REPLAY", "AVSLUTTET_UTEN_UTBETALING", "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE", "AVVENTER_VILKÅRSPRØVING", "AVVENTER_HISTORIKK", "AVVENTER_SIMULERING", "AVVENTER_GODKJENNING")
        assertTilstander(2, "AVVENTER_INNTEKTSMELDING_REPLAY", "AVSLUTTET_UTEN_UTBETALING", "AVVENTER_INNTEKTSMELDING", "AVVENTER_BLOKKERENDE_PERIODE")
    }

    private val logCollector = LogCollector()

    private fun catchErrors(vararg filter: String, block: () -> Any): List<ILoggingEvent> {
        val logger = (LoggerFactory.getLogger("tjenestekall") as Logger)
        logger.addAppender(logCollector)
        logCollector.start()
        block()
        logger.detachAppender(logCollector)
        logCollector.stop()
        return logCollector.iterator().asSequence().filter { event ->
            filter.toList().any { filterText -> event.formattedMessage.contains(filterText) }
        }.toList()
    }

    @AfterEach
    fun after() {
        val logger = (LoggerFactory.getLogger("tjenestekall") as Logger)
        logger.detachAndStopAllAppenders()
    }

    private class LogCollector private constructor(private val messages: MutableList<ILoggingEvent>): AppenderBase<ILoggingEvent>(), Iterable<ILoggingEvent> by (messages) {
        constructor() : this(mutableListOf())

        override fun append(eventObject: ILoggingEvent) {
            messages.add(eventObject)
        }
    }
}
