package no.nav.helse.serde

import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Dokumentsporing.Companion.toJsonList
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.ForlengelseFraInfotrygd
import no.nav.helse.person.InntektsmeldingInfo
import no.nav.helse.person.InntektsmeldingInfoHistorikk
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Person
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Utbetalingsperiode
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag
import no.nav.helse.person.inntekt.IkkeRapportert
import no.nav.helse.person.inntekt.Infotrygd
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.inntekt.Skatteopplysning
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.serde.PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType
import no.nav.helse.serde.PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.*
import no.nav.helse.serde.PersonData.UtbetalingstidslinjeData.TypeData
import no.nav.helse.serde.api.BuilderState
import no.nav.helse.serde.mapping.JsonMedlemskapstatus
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.*
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsgiverdag
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling
import no.nav.helse.utbetalingslinjer.Klassekode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Satstype
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Arbeidsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.AvvistDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ForeldetDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinjeberegning
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosent
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi
import no.nav.helse.økonomi.ØkonomiBuilder
import kotlin.collections.set

fun Person.serialize(): SerialisertPerson {
    val jsonBuilder = JsonBuilder()
    this.accept(jsonBuilder)
    return SerialisertPerson(jsonBuilder.toJson())
}

internal class JsonBuilder : AbstractBuilder() {

    private lateinit var personBuilder: PersonState

    private var jsonNode: ObjectNode? = null

    private fun build(): ObjectNode {
        return jsonNode ?: personBuilder.build().also {
            jsonNode = it
        }
    }
    
    internal fun toJson() = build().toString()
    internal fun toPretty() = build().toPrettyString()
    override fun toString() = toJson()

    override fun preVisitPerson(
        person: Person,
        opprettet: LocalDateTime,
        aktørId: String,
        personidentifikator: Personidentifikator,
        vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
    ) {
        personBuilder = PersonState(personidentifikator, aktørId, opprettet)
        pushState(personBuilder)
    }

    private class PersonState(personidentifikator: Personidentifikator, aktørId: String, opprettet: LocalDateTime) : BuilderState() {
        private val personMap = mutableMapOf<String, Any?>(
            "aktørId" to aktørId,
            "fødselsnummer" to personidentifikator.toString(),
            "opprettet" to opprettet
        )
        private val vilkårsgrunnlagHistorikk = mutableListOf<Map<String, Any?>>()

        private val arbeidsgivere = mutableListOf<MutableMap<String, Any?>>()

        fun build() = SerialisertPerson.medSkjemaversjon(serdeObjectMapper.valueToTree(personMap))

        override fun visitAlder(alder: Alder, fødselsdato: LocalDate, dødsdato: LocalDate?) {
            personMap["fødselsdato"] = fødselsdato
            personMap["dødsdato"] = dødsdato
        }

        override fun preVisitArbeidsgivere() {
            personMap["arbeidsgivere"] = arbeidsgivere
        }

        override fun preVisitArbeidsgiver(
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            organisasjonsnummer: String
        ) {
            val arbeidsgiverMap = mutableMapOf<String, Any?>()
            arbeidsgivere.add(arbeidsgiverMap)
            pushState(ArbeidsgiverState(arbeidsgiverMap))
        }

        override fun preVisitInfotrygdhistorikk() {
            val historikk = mutableListOf<Map<String, Any?>>()
            personMap["infotrygdhistorikk"] = historikk
            pushState(InfotrygdhistorikkState(historikk))
        }

        override fun preVisitVilkårsgrunnlagHistorikk() {
            personMap["vilkårsgrunnlagHistorikk"] = vilkårsgrunnlagHistorikk
            pushState(VilkårsgrunnlagHistorikkState(vilkårsgrunnlagHistorikk))
        }

        override fun postVisitPerson(
            person: Person,
            opprettet: LocalDateTime,
            aktørId: String,
            personidentifikator: Personidentifikator,
            vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
        ) {
            popState()
        }
    }

    private class ArbeidsgiverState(private val arbeidsgiverMap: MutableMap<String, Any?>) : BuilderState() {
        private val beregningerList = mutableListOf<Map<String, Any?>>()
        private val refusjonOpphører = mutableListOf<LocalDate?>()
        private val feriepengeutbetalingListe = mutableListOf<Map<String, Any>>()
        private val vedtaksperiodeListe = mutableListOf<MutableMap<String, Any?>>()
        private val forkastedeVedtaksperiodeListe = mutableListOf<MutableMap<String, Any?>>()
        private val utbetalingstidslinjer = mutableListOf<MutableMap<String, Any?>>()

        override fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
            val inntektshistorikkListe = mutableListOf<Map<String, Any?>>()
            arbeidsgiverMap["inntektshistorikk"] = inntektshistorikkListe
            pushState(InntektshistorikkState(inntektshistorikkListe))
        }

        override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
            val sykdomshistorikkList = mutableListOf<MutableMap<String, Any?>>()
            arbeidsgiverMap["sykdomshistorikk"] = sykdomshistorikkList
            pushState(SykdomshistorikkState(sykdomshistorikkList))
        }

        override fun preVisitSykmeldingsperioder(sykmeldingsperioder: Sykmeldingsperioder) {
            val sykmeldingsperioderListe = mutableListOf<Map<String, Any>>()
            arbeidsgiverMap["sykmeldingsperioder"] = sykmeldingsperioderListe
            pushState(SykmeldingsperioderState(sykmeldingsperioderListe))
        }

        override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {
            val utbetalingstidslinjeMap = mutableMapOf<String, Any?>()
            utbetalingstidslinjer.add(utbetalingstidslinjeMap)
        }

        override fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
            arbeidsgiverMap["utbetalinger"] = mutableListOf<MutableMap<String, Any?>>().also {
                pushState(UtbetalingerState(it))
            }
        }

        override fun postVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {
            arbeidsgiverMap["vedtaksperioder"] = vedtaksperiodeListe
        }

        override fun preVisitForkastetPeriode(vedtaksperiode: Vedtaksperiode) {
            val vedtaksperiodeMap = mutableMapOf<String, Any?>()
            forkastedeVedtaksperiodeListe.add(
                mutableMapOf(
                    "vedtaksperiode" to vedtaksperiodeMap
                )
            )
            pushState(ForkastetVedtaksperiodeState(vedtaksperiodeMap))
        }

        override fun postVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {
            arbeidsgiverMap["forkastede"] = forkastedeVedtaksperiodeListe
        }

        override fun preVisitVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            id: UUID,
            tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
            opprettet: LocalDateTime,
            oppdatert: LocalDateTime,
            periode: Periode,
            opprinneligPeriode: Periode,
            skjæringstidspunkt: () -> LocalDate,
            skjæringstidspunktFraInfotrygd: LocalDate?,
            forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
            hendelseIder: Set<Dokumentsporing>,
            inntektsmeldingInfo: InntektsmeldingInfo?
        ) {
            val vedtaksperiodeMap = mutableMapOf<String, Any?>()
            vedtaksperiodeListe.add(vedtaksperiodeMap)
            pushState(VedtaksperiodeState(
                vedtaksperiodeMap = vedtaksperiodeMap
            ))
        }

        override fun preVisitUtbetalingstidslinjeberegninger(beregninger: List<Utbetalingstidslinjeberegning>) {
            pushState(UtbetalingstidslinjeberegningerState(beregningerList))
        }

        override fun preVisitFeriepengeutbetalinger(feriepengeutbetalinger: List<Feriepengeutbetaling>) {
            pushState(FeriepengeutbetalingerState(feriepengeutbetalingListe))
        }

        override fun preVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {
            val historikk = mutableListOf<Map<String, Any?>>()
            arbeidsgiverMap["refusjonshistorikk"] = historikk
            pushState(RefusjonshistorikkState(historikk))
        }

        override fun preVisitInntektsmeldinginfoHistorikk(inntektsmeldingInfoHistorikk: InntektsmeldingInfoHistorikk) {
            val historikk = mutableListOf<Map<String, Any>>()
            arbeidsgiverMap["inntektsmeldingInfo"] = historikk
            pushState(InntektsmeldingInfoHistorikkState(historikk))
        }

        override fun postVisitArbeidsgiver(
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            organisasjonsnummer: String
        ) {
            arbeidsgiverMap["id"] = id
            arbeidsgiverMap["organisasjonsnummer"] = organisasjonsnummer
            arbeidsgiverMap["beregnetUtbetalingstidslinjer"] = beregningerList
            arbeidsgiverMap["refusjonOpphører"] = refusjonOpphører
            arbeidsgiverMap["feriepengeutbetalinger"] = feriepengeutbetalingListe.toList()
            popState()
        }
    }

    private class UtbetalingstidslinjeberegningerState(private val beregninger: MutableList<Map<String, Any?>>) : BuilderState() {
        private val utbetalingstidslinjeMap = mutableMapOf<String, Any?>()

        override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {
            pushState(UtbetalingstidslinjeState(utbetalingstidslinjeMap))
        }

        override fun postVisitUtbetalingstidslinjeberegning(
            id: UUID,
            tidsstempel: LocalDateTime,
            organisasjonsnummer: String,
            sykdomshistorikkElementId: UUID,
            vilkårsgrunnlagHistorikkInnslagId: UUID
        ) {
            beregninger.add(mapOf(
                "id" to id,
                "sykdomshistorikkElementId" to sykdomshistorikkElementId,
                "vilkårsgrunnlagHistorikkInnslagId" to vilkårsgrunnlagHistorikkInnslagId,
                "tidsstempel" to tidsstempel,
                "organisasjonsnummer" to organisasjonsnummer,
                "utbetalingstidslinje" to utbetalingstidslinjeMap
            ))
        }

        override fun postVisitUtbetalingstidslinjeberegninger(beregninger: List<Utbetalingstidslinjeberegning>) {
            popState()
        }
    }

    private class FeriepengeutbetalingerState(private val feriepengeutbetalinger: MutableList<Map<String, Any>>) : BuilderState() {

        override fun preVisitFeriepengeutbetaling(
            feriepengeutbetaling: Feriepengeutbetaling,
            infotrygdFeriepengebeløpPerson: Double,
            infotrygdFeriepengebeløpArbeidsgiver: Double,
            spleisFeriepengebeløpArbeidsgiver: Double,
            spleisFeriepengebeløpPerson: Double,
            overføringstidspunkt: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            utbetalingId: UUID,
            sendTilOppdrag: Boolean,
            sendPersonoppdragTilOS: Boolean
        ) {
            val feriepengeutbetalingMap = mutableMapOf<String, Any>()
            feriepengeutbetalinger.add(feriepengeutbetalingMap)
            pushState(FeriepengeutbetalingState(feriepengeutbetalingMap))
        }

        override fun postVisitFeriepengeutbetalinger(feriepengeutbetalinger: List<Feriepengeutbetaling>) {
            popState()
        }
    }

    private class FeriepengeutbetalingState(private val feriepengeutbetalingMap: MutableMap<String, Any>) : BuilderState() {
        private val arbeidsgiveroppdragMap = mutableMapOf<String, Any?>()
        private val personoppdragMap = mutableMapOf<String, Any?>()

        override fun preVisitFeriepengeberegner(
            feriepengeberegner: Feriepengeberegner,
            feriepengedager: List<Feriepengeberegner.UtbetaltDag>,
            opptjeningsår: Year,
            utbetalteDager: List<Feriepengeberegner.UtbetaltDag>
        ) {
            pushState(FeriepengeberegnerState(feriepengeutbetalingMap))
        }

        override fun preVisitFeriepengerArbeidsgiveroppdrag() {
            pushState(OppdragState(arbeidsgiveroppdragMap))
        }

        override fun preVisitFeriepengerPersonoppdrag() {
            pushState(OppdragState(personoppdragMap))
        }

        override fun postVisitFeriepengeutbetaling(
            feriepengeutbetaling: Feriepengeutbetaling,
            infotrygdFeriepengebeløpPerson: Double,
            infotrygdFeriepengebeløpArbeidsgiver: Double,
            spleisFeriepengebeløpArbeidsgiver: Double,
            spleisFeriepengebeløpPerson: Double,
            overføringstidspunkt: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            utbetalingId: UUID,
            sendTilOppdrag: Boolean,
            sendPersonoppdragTilOS: Boolean
        ) {
            feriepengeutbetalingMap["infotrygdFeriepengebeløpPerson"] = infotrygdFeriepengebeløpPerson
            feriepengeutbetalingMap["infotrygdFeriepengebeløpArbeidsgiver"] = infotrygdFeriepengebeløpArbeidsgiver
            feriepengeutbetalingMap["spleisFeriepengebeløpArbeidsgiver"] = spleisFeriepengebeløpArbeidsgiver
            feriepengeutbetalingMap["spleisFeriepengebeløpPerson"] = spleisFeriepengebeløpPerson
            feriepengeutbetalingMap["utbetalingId"] = utbetalingId
            feriepengeutbetalingMap["sendTilOppdrag"] = sendTilOppdrag
            feriepengeutbetalingMap["sendPersonoppdragTilOS"] = sendPersonoppdragTilOS
            feriepengeutbetalingMap["oppdrag"] = arbeidsgiveroppdragMap
            feriepengeutbetalingMap["personoppdrag"] = personoppdragMap
            popState()
        }
    }

    private class RefusjonshistorikkState(private val historikk: MutableList<Map<String, Any?>>) : BuilderState() {
        private var endringerIRefusjon: MutableList<Map<String, Any?>> = mutableListOf()

        override fun preVisitRefusjon(
            meldingsreferanseId: UUID,
            førsteFraværsdag: LocalDate?,
            arbeidsgiverperioder: List<Periode>,
            beløp: Inntekt?,
            sisteRefusjonsdag: LocalDate?,
            endringerIRefusjon: List<Refusjonshistorikk.Refusjon.EndringIRefusjon>,
            tidsstempel: LocalDateTime
        ) {
            this.endringerIRefusjon = mutableListOf()
            historikk.add(
                mapOf(
                    "meldingsreferanseId" to meldingsreferanseId,
                    "førsteFraværsdag" to førsteFraværsdag,
                    "arbeidsgiverperioder" to arbeidsgiverperioder.map { mapOf("fom" to it.start, "tom" to it.endInclusive) },
                    "beløp" to beløp?.reflection { _, månedlig, _, _ -> månedlig },
                    "sisteRefusjonsdag" to sisteRefusjonsdag,
                    "endringerIRefusjon" to this.endringerIRefusjon,
                    "tidsstempel" to tidsstempel
                )
            )
        }

        override fun visitEndringIRefusjon(beløp: Inntekt, endringsdato: LocalDate) {
            endringerIRefusjon.add(
                mapOf(
                    "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                    "endringsdato" to endringsdato
                )
            )
        }

        override fun postVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {
            popState()
        }
    }

    private class InntektsmeldingInfoHistorikkState(private val historikk: MutableList<Map<String, Any>>) : BuilderState() {

        override fun preVisitInntektsmeldinginfoElement(dato: LocalDate, elementer: List<InntektsmeldingInfo>) {
            val element = mutableMapOf<String, Any>()
            historikk.add(element)
            pushState(InntektsmeldingInfoHistorikkElementState(dato, element))
        }

        override fun postVisitInntektsmeldinginfoHistorikk(inntektsmeldingInfoHistorikk: InntektsmeldingInfoHistorikk) {
            popState()
        }

        private class InntektsmeldingInfoHistorikkElementState(dato: LocalDate, element: MutableMap<String, Any>) : BuilderState() {
            private val elementer = mutableListOf<Map<String, Any>>()

            init {
                element["dato"] = dato
                element["inntektsmeldinger"] = elementer
            }

            override fun visitInntektsmeldinginfo(id: UUID, arbeidsforholdId: String?) {
                elementer.add(mutableMapOf<String, Any>("id" to id).apply {
                    compute("arbeidsforholdId") { _, _ -> arbeidsforholdId }
                })
            }

            override fun postVisitInntektsmeldinginfoElement(dato: LocalDate, elementer: List<InntektsmeldingInfo>) {
                popState()
            }
        }
    }

    private class OppdragState(private val oppdragMap: MutableMap<String, Any?>) : BuilderState() {
        private val linjer = mutableListOf<Map<String, Any?>>()

        override fun visitUtbetalingslinje(
            linje: Utbetalingslinje,
            fom: LocalDate,
            tom: LocalDate,
            stønadsdager: Int,
            totalbeløp: Int,
            satstype: Satstype,
            beløp: Int?,
            aktuellDagsinntekt: Int?,
            grad: Int?,
            delytelseId: Int,
            refDelytelseId: Int?,
            refFagsystemId: String?,
            endringskode: Endringskode,
            datoStatusFom: LocalDate?,
            statuskode: String?,
            klassekode: Klassekode
        ) {
            linjer.add(mapOf(
                "fom" to fom,
                "tom" to tom,
                "satstype" to "$satstype",
                "sats" to beløp,
                "lønn" to aktuellDagsinntekt,
                "grad" to grad,
                "stønadsdager" to stønadsdager,
                "totalbeløp" to totalbeløp,
                "endringskode" to endringskode,
                "delytelseId" to delytelseId,
                "refDelytelseId" to refDelytelseId,
                "refFagsystemId" to refFagsystemId,
                "statuskode" to statuskode,
                "datoStatusFom" to datoStatusFom,
                "klassekode" to klassekode.verdi
            ))
        }

        override fun postVisitOppdrag(
            oppdrag: Oppdrag,
            fagområde: Fagområde,
            fagsystemId: String,
            mottaker: String,
            sisteArbeidsgiverdag: LocalDate?,
            stønadsdager: Int,
            totalBeløp: Int,
            nettoBeløp: Int,
            tidsstempel: LocalDateTime,
            endringskode: Endringskode,
            avstemmingsnøkkel: Long?,
            status: Oppdragstatus?,
            overføringstidspunkt: LocalDateTime?,
            erSimulert: Boolean,
            simuleringsResultat: SimuleringResultat?
        ) {
            oppdragMap["mottaker"] = mottaker
            oppdragMap["fagområde"] = "$fagområde"
            oppdragMap["linjer"] = linjer
            oppdragMap["fagsystemId"] = fagsystemId
            oppdragMap["endringskode"] = "$endringskode"
            oppdragMap["sisteArbeidsgiverdag"] = sisteArbeidsgiverdag
            oppdragMap["tidsstempel"] = tidsstempel
            oppdragMap["totalbeløp"] = totalBeløp
            oppdragMap["nettoBeløp"] = nettoBeløp
            oppdragMap["stønadsdager"] = stønadsdager
            oppdragMap["avstemmingsnøkkel"] = avstemmingsnøkkel?.let { "$it" }
            oppdragMap["status"] = status?.let { "$it" }
            oppdragMap["overføringstidspunkt"] = overføringstidspunkt
            oppdragMap["erSimulert"] = erSimulert
            oppdragMap["simuleringsResultat"] = simuleringsResultat?.toMap()

            popState()
        }
    }

    private class FeriepengeberegnerState(private val feriepengeutbetalingMap: MutableMap<String, Any>) : BuilderState() {
        private val utbetalteDager = mutableListOf<Map<String, Any>>()
        private val feriepengedager = mutableListOf<Map<String, Any>>()

        override fun preVisitUtbetaleDager() {
            pushState(FeriepengerUtbetalteDagerState(utbetalteDager))
        }

        override fun preVisitFeriepengedager() {
            pushState(FeriepengerUtbetalteDagerState(feriepengedager))
        }

        override fun postVisitFeriepengeberegner(
            feriepengeberegner: Feriepengeberegner,
            feriepengedager: List<Feriepengeberegner.UtbetaltDag>,
            opptjeningsår: Year,
            utbetalteDager: List<Feriepengeberegner.UtbetaltDag>
        ) {
            feriepengeutbetalingMap["opptjeningsår"] = opptjeningsår
            feriepengeutbetalingMap["utbetalteDager"] = this.utbetalteDager
            feriepengeutbetalingMap["feriepengedager"] = this.feriepengedager
            popState()
        }
    }

    private class FeriepengerUtbetalteDagerState(private val dager: MutableList<Map<String, Any>>) : BuilderState() {
        override fun postVisitUtbetaleDager() {
            popState()
        }

        override fun postVisitFeriepengedager() {
            popState()
        }

        override fun visitInfotrygdArbeidsgiverDag(
            infotrygdArbeidsgiver: Feriepengeberegner.UtbetaltDag.InfotrygdArbeidsgiver,
            orgnummer: String,
            dato: LocalDate,
            beløp: Int
        ) {
            leggTilDag("InfotrygdArbeidsgiverDag", orgnummer, dato, beløp)
        }

        override fun visitInfotrygdPersonDag(infotrygdPerson: Feriepengeberegner.UtbetaltDag.InfotrygdPerson, orgnummer: String, dato: LocalDate, beløp: Int) {
            leggTilDag("InfotrygdPersonDag", orgnummer, dato, beløp)
        }

        override fun visitSpleisArbeidsgiverDag(
            spleisArbeidsgiver: Feriepengeberegner.UtbetaltDag.SpleisArbeidsgiver,
            orgnummer: String,
            dato: LocalDate,
            beløp: Int
        ) {
            leggTilDag("SpleisArbeidsgiverDag", orgnummer, dato, beløp)
        }

        override fun visitSpleisPersonDag(
            spleisPerson: Feriepengeberegner.UtbetaltDag.SpleisPerson,
            orgnummer: String,
            dato: LocalDate,
            beløp: Int
        ) {
            leggTilDag("SpleisPersonDag", orgnummer, dato, beløp)
        }

        private fun leggTilDag(
            type: String,
            orgnummer: String,
            dato: LocalDate,
            beløp: Int
        ) {
            dager.add(
                mapOf(
                    "dato" to dato,
                    "type" to type,
                    "orgnummer" to orgnummer,
                    "beløp" to beløp
                )
            )
        }
    }

    private class InfotrygdhistorikkState(private val historikk: MutableList<Map<String, Any?>>) : BuilderState() {
        override fun preVisitInfotrygdhistorikkElement(
            id: UUID,
            tidsstempel: LocalDateTime,
            oppdatert: LocalDateTime,
            hendelseId: UUID?
        ) {
            val element = mutableMapOf<String, Any?>()
            historikk.add(element)
            pushState(InfotrygdhistorikkElementState(
                element,
                id,
                tidsstempel,
                oppdatert,
                hendelseId
            ))
        }

        override fun postVisitInfotrygdhistorikk() {
            popState()
        }
    }

    private class InfotrygdhistorikkElementState(
        element: MutableMap<String, Any?>,
        id: UUID,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        hendelseId: UUID?
    ) : BuilderState() {
        private val ferieperioder = mutableListOf<Map<String, LocalDate>>()
        private val arbeidsgiverutbetalingsperioder = mutableListOf<Map<String, Any>>()
        private val personutbetalingsperioder = mutableListOf<Map<String, Any>>()
        private val inntekter = mutableListOf<Map<String, Any?>>()
        private val arbeidskategorikoder = mutableMapOf<String, LocalDate>()

        init {
            element["id"] = id
            element["tidsstempel"] = tidsstempel
            element["hendelseId"] = hendelseId
            element["ferieperioder"] = ferieperioder
            element["arbeidsgiverutbetalingsperioder"] = arbeidsgiverutbetalingsperioder
            element["personutbetalingsperioder"] = personutbetalingsperioder
            element["inntekter"] = inntekter
            element["arbeidskategorikoder"] = arbeidskategorikoder
            element["oppdatert"] = oppdatert
        }

        override fun visitInfotrygdhistorikkFerieperiode(periode: Friperiode, fom: LocalDate, tom: LocalDate) {
            ferieperioder.add(
                mapOf(
                    "fom" to fom,
                    "tom" to tom
                )
            )
        }

        override fun visitInfotrygdhistorikkArbeidsgiverUtbetalingsperiode(
            periode: Utbetalingsperiode,
            orgnr: String,
            fom: LocalDate,
            tom: LocalDate,
            grad: Prosentdel,
            inntekt: Inntekt
        ) {
            arbeidsgiverutbetalingsperioder.add(mapOf(
                "orgnr" to orgnr,
                "fom" to fom,
                "tom" to tom,
                "grad" to grad.roundToInt(),
                "inntekt" to inntekt.reflection { _, månedlig, _, _ -> månedlig }
            ))
        }

        override fun visitInfotrygdhistorikkPersonUtbetalingsperiode(
            periode: Utbetalingsperiode,
            orgnr: String,
            fom: LocalDate,
            tom: LocalDate,
            grad: Prosentdel,
            inntekt: Inntekt
        ) {
            personutbetalingsperioder.add(mapOf(
                "orgnr" to orgnr,
                "fom" to fom,
                "tom" to tom,
                "grad" to grad.roundToInt(),
                "inntekt" to inntekt.reflection { _, månedlig, _, _ -> månedlig }
            ))
        }

        override fun visitInfotrygdhistorikkInntektsopplysning(
            orgnr: String,
            sykepengerFom: LocalDate,
            inntekt: Inntekt,
            refusjonTilArbeidsgiver: Boolean,
            refusjonTom: LocalDate?,
            lagret: LocalDateTime?
        ) {
            inntekter.add(
                mapOf(
                    "orgnr" to orgnr,
                    "sykepengerFom" to sykepengerFom,
                    "inntekt" to inntekt.reflection { _, månedlig, _, _ -> månedlig },
                    "refusjonTilArbeidsgiver" to refusjonTilArbeidsgiver,
                    "refusjonTom" to refusjonTom,
                    "lagret" to lagret
                )
            )
        }

        override fun visitInfotrygdhistorikkArbeidskategorikoder(arbeidskategorikoder: Map<String, LocalDate>) {
            this.arbeidskategorikoder.putAll(arbeidskategorikoder)
        }

        override fun postVisitInfotrygdhistorikkElement(
            id: UUID,
            tidsstempel: LocalDateTime,
            oppdatert: LocalDateTime,
            hendelseId: UUID?
        ) {
            popState()
        }
    }


    private class VilkårsgrunnlagHistorikkState(private val historikk: MutableList<Map<String, Any?>>) : BuilderState() {
        override fun preVisitInnslag(
            innslag: VilkårsgrunnlagHistorikk.Innslag,
            id: UUID,
            opprettet: LocalDateTime
        ) {
            pushState(VilkårsgrunnlagInnslagState(this.historikk))
        }

        override fun postVisitVilkårsgrunnlagHistorikk() {
            popState()
        }
    }

    private class VilkårsgrunnlagInnslagState(private val innslag: MutableList<Map<String, Any?>>) : BuilderState() {
        private val vilkårsgrunnlagelementer = mutableListOf<Map<String, Any?>>()

        override fun preVisitGrunnlagsdata(
            skjæringstidspunkt: LocalDate,
            grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening,
            vurdertOk: Boolean,
            meldingsreferanseId: UUID?,
            vilkårsgrunnlagId: UUID,
            medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus
        ) {
            pushState(GrunnlagsdataState(vilkårsgrunnlagelementer))
        }

        override fun preVisitInfotrygdVilkårsgrunnlag(
            infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: Sykepengegrunnlag,
            vilkårsgrunnlagId: UUID
        ) {
            pushState(InfotrygdvilkårsgrunnlagState(vilkårsgrunnlagelementer))
        }


        override fun postVisitInnslag(innslag: VilkårsgrunnlagHistorikk.Innslag, id: UUID, opprettet: LocalDateTime) {
            this.innslag.add(mutableMapOf(
                "id" to id,
                "opprettet" to opprettet,
                "vilkårsgrunnlag" to this.vilkårsgrunnlagelementer
            ))
            popState()
        }
    }

    internal class ArbeidsgiverOpptjeningsgrunnlagState(private val arbeidsforholdOpptjeningsgrunnlagMap: MutableList<Map<String, Any>>) : BuilderState() {
        private var arbeidsforholdMap = mutableListOf<Map<String, Any?>>()

        override fun visitArbeidsforhold(ansattFom: LocalDate, ansattTom: LocalDate?, deaktivert: Boolean) {
            arbeidsforholdMap.add(mapOf("ansattFom" to ansattFom, "ansattTom" to ansattTom, "deaktivert" to deaktivert))
        }

        override fun postVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer: String, ansattPerioder: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold>) {
            arbeidsforholdOpptjeningsgrunnlagMap.add(mapOf("orgnummer" to orgnummer, "ansattPerioder" to arbeidsforholdMap))
            popState()
        }
    }

    class OpptjeningState(private val opptjeningsMap: MutableMap<String, Any>): BuilderState() {
        private val arbeidsforholdOpptjeningsgrunnlagMap = mutableListOf<Map<String, Any>>()

        override fun preVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer: String, ansattPerioder: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold>) {
            pushState(ArbeidsgiverOpptjeningsgrunnlagState(arbeidsforholdOpptjeningsgrunnlagMap))
        }

        override fun postVisitOpptjening(opptjening: Opptjening, arbeidsforhold: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag>, opptjeningsperiode: Periode) {
            opptjeningsMap.putAll(mapOf(
                "arbeidsforhold" to arbeidsforholdOpptjeningsgrunnlagMap,
                "opptjeningFom" to opptjeningsperiode.start,
                "opptjeningTom" to opptjeningsperiode.endInclusive
            ))
            popState()
        }
    }

    class InfotrygdvilkårsgrunnlagState(private val vilkårsgrunnlagelementer: MutableList<Map<String, Any?>>) : BuilderState() {
        private val sykepengegrunnlagMap = mutableMapOf<String, Any>()

        override fun preVisitSykepengegrunnlag(
            sykepengegrunnlag1: Sykepengegrunnlag,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: Inntekt,
            avviksprosent: Prosent,
            totalOmregnetÅrsinntekt: Inntekt,
            beregningsgrunnlag: Inntekt,
            `6G`: Inntekt,
            begrensning: Sykepengegrunnlag.Begrensning,
            vurdertInfotrygd: Boolean,
            minsteinntekt: Inntekt,
            oppfyllerMinsteinntektskrav: Boolean,
            tilstand: Sykepengegrunnlag.Tilstand
        ) {
            pushState(SykepengegrunnlagState(sykepengegrunnlagMap))
        }

        override fun postVisitInfotrygdVilkårsgrunnlag(
            infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: Sykepengegrunnlag,
            vilkårsgrunnlagId: UUID
        ) {
            vilkårsgrunnlagelementer.add(
                mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "type" to "Infotrygd",
                    "sykepengegrunnlag" to sykepengegrunnlagMap,
                    "vilkårsgrunnlagId" to vilkårsgrunnlagId
                )
            )
            popState()
        }
    }

    class GrunnlagsdataState(private val vilkårsgrunnlagElement: MutableList<Map<String, Any?>>) : BuilderState() {
        private val sykepengegrunnlagMap = mutableMapOf<String, Any>()
        private val opptjeningMap = mutableMapOf<String, Any>()

        override fun preVisitSykepengegrunnlag(
            sykepengegrunnlag1: Sykepengegrunnlag,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: Inntekt,
            avviksprosent: Prosent,
            totalOmregnetÅrsinntekt: Inntekt,
            beregningsgrunnlag: Inntekt,
            `6G`: Inntekt,
            begrensning: Sykepengegrunnlag.Begrensning,
            vurdertInfotrygd: Boolean,
            minsteinntekt: Inntekt,
            oppfyllerMinsteinntektskrav: Boolean,
            tilstand: Sykepengegrunnlag.Tilstand
        ) {
            pushState(SykepengegrunnlagState(sykepengegrunnlagMap))
        }

        override fun preVisitOpptjening(opptjening: Opptjening, arbeidsforhold: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag>, opptjeningsperiode: Periode) {
            pushState(OpptjeningState(opptjeningMap))
        }

        override fun postVisitGrunnlagsdata(
            skjæringstidspunkt: LocalDate,
            grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
            sykepengegrunnlag: Sykepengegrunnlag,
            medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
            vurdertOk: Boolean,
            meldingsreferanseId: UUID?,
            vilkårsgrunnlagId: UUID
        ) {
            vilkårsgrunnlagElement.add(
                mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "type" to "Vilkårsprøving",
                    "sykepengegrunnlag" to sykepengegrunnlagMap,
                    "opptjening" to opptjeningMap,
                    "medlemskapstatus" to when (medlemskapstatus) {
                        Medlemskapsvurdering.Medlemskapstatus.Ja -> JsonMedlemskapstatus.JA
                        Medlemskapsvurdering.Medlemskapstatus.Nei -> JsonMedlemskapstatus.NEI
                        Medlemskapsvurdering.Medlemskapstatus.VetIkke -> JsonMedlemskapstatus.VET_IKKE
                    },
                    "vurdertOk" to vurdertOk,
                    "meldingsreferanseId" to meldingsreferanseId,
                    "vilkårsgrunnlagId" to vilkårsgrunnlagId
                )
            )
            popState()
        }
    }


    class SykepengegrunnlagState(private val sykepengegrunnlag: MutableMap<String, Any>) : BuilderState() {
        private val arbeidsgiverInntektsopplysninger = mutableListOf<Map<String, Any>>()
        private val deaktiverteArbeidsgiverInntektsopplysninger = mutableListOf<Map<String, Any>>()
        private val sammenligningsgrunnlagMap = mutableMapOf<String, Any>()

        override fun preVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
            pushState(ArbeidsgiverInntektsopplysningerState(arbeidsgiverInntektsopplysninger))
        }

        override fun preVisitSammenligningsgrunnlag(sammenligningsgrunnlag1: Sammenligningsgrunnlag, sammenligningsgrunnlag: Inntekt) {
            pushState(SammenlingningsgrunnlagState(sammenligningsgrunnlagMap))
        }

        override fun preVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
            pushState(ArbeidsgiverInntektsopplysningerState(deaktiverteArbeidsgiverInntektsopplysninger))
        }

        override fun postVisitSykepengegrunnlag(
            sykepengegrunnlag1: Sykepengegrunnlag,
            skjæringstidspunkt: LocalDate,
            sykepengegrunnlag: Inntekt,
            avviksprosent: Prosent,
            totalOmregnetÅrsinntekt: Inntekt,
            beregningsgrunnlag: Inntekt,
            `6G`: Inntekt,
            begrensning: Sykepengegrunnlag.Begrensning,
            vurdertInfotrygd: Boolean,
            minsteinntekt: Inntekt,
            oppfyllerMinsteinntektskrav: Boolean,
            tilstand: Sykepengegrunnlag.Tilstand
        ) {
            this.sykepengegrunnlag.putAll(
                mapOf(
                    "sykepengegrunnlag" to sykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "avviksprosent" to avviksprosent.ratio(),
                    "beregningsgrunnlag" to beregningsgrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "totalOmregnetÅrsinntekt" to totalOmregnetÅrsinntekt.reflection { årlig, _, _, _ -> årlig },
                    "sammenligningsgrunnlag" to sammenligningsgrunnlagMap,
                    "grunnbeløp" to `6G`.reflection { årlig, _, _, _ -> årlig },
                    "arbeidsgiverInntektsopplysninger" to arbeidsgiverInntektsopplysninger,
                    "begrensning" to begrensning,
                    "deaktiverteArbeidsforhold" to deaktiverteArbeidsgiverInntektsopplysninger,
                    "vurdertInfotrygd" to vurdertInfotrygd,
                    "minsteinntekt" to minsteinntekt.reflection { årlig, _, _, _ -> årlig },
                    "oppfyllerMinsteinntektskrav" to oppfyllerMinsteinntektskrav,
                    "tilstand" to when (tilstand) {
                        is Sykepengegrunnlag.FastsattEtterHovedregel -> PersonData.VilkårsgrunnlagElementData.SykepengegrunnlagData.TilstandData.FASTSATT_ETTER_HOVEDREGEL
                        is Sykepengegrunnlag.AvventerFastsettelseEtterSkjønn -> PersonData.VilkårsgrunnlagElementData.SykepengegrunnlagData.TilstandData.AVVENTER_FASTSETTELSE_ETTER_SKJØNN
                        is Sykepengegrunnlag.FastsattEtterSkjønn -> PersonData.VilkårsgrunnlagElementData.SykepengegrunnlagData.TilstandData.FASTSATT_ETTER_SKJØNN
                        is Sykepengegrunnlag.Start -> throw IllegalStateException("Forventet ikke tilstand ${tilstand.javaClass.simpleName}")
                    }
                )
            )
            popState()
        }
    }

    class SammenlingningsgrunnlagState(private val sammenligningsgrunnlag: MutableMap<String, Any>) : BuilderState() {
        private val arbeidsgiverInntektsopplysninger = mutableListOf<Map<String, Any>>()

        override fun postVisitSammenligningsgrunnlag(sammenligningsgrunnlag1: Sammenligningsgrunnlag, sammenligningsgrunnlag: Inntekt) {
            this.sammenligningsgrunnlag.putAll(
                mapOf(
                    "sammenligningsgrunnlag" to sammenligningsgrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "arbeidsgiverInntektsopplysninger" to arbeidsgiverInntektsopplysninger,
                )
            )
            popState()
        }

        override fun preVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag>) {
            pushState(ArbeidsgiverInntektsopplysningerForSammenligningsgrunnlagState(this.arbeidsgiverInntektsopplysninger))
        }
    }

    class ArbeidsgiverInntektsopplysningerState(private val arbeidsgiverInntektsopplysninger: MutableList<Map<String, Any>>) : BuilderState() {
        private val opplysninger = mutableListOf<Map<String, Any>>()

        override fun preVisitArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning, orgnummer: String) {
            pushState(ArbeidsgiverInntektsopplysningState(opplysninger))
        }

        override fun postVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
            this.arbeidsgiverInntektsopplysninger.addAll(opplysninger)
            popState()
        }

        override fun postVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
            this.arbeidsgiverInntektsopplysninger.addAll(opplysninger)
            popState()
        }

    }
    class ArbeidsgiverInntektsopplysningState(private val arbeidsgiverInntektsopplysninger: MutableList<Map<String, Any>>) : BuilderState() {
        private lateinit var inntektsopplysning: Map<String, Any?>
        private var refusjonsopplysninger = mutableListOf<Map<String, Any?>>()
        private var tilstand: Tilstand = Tilstand.FangeInntekt

        override fun visitRefusjonsopplysning(
            meldingsreferanseId: UUID,
            fom: LocalDate,
            tom: LocalDate?,
            beløp: Inntekt
        ) {
            this.refusjonsopplysninger.add(
                mapOf(
                    "meldingsreferanseId" to meldingsreferanseId,
                    "fom" to fom,
                    "tom" to tom,
                    "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                )
            )
        }

        override fun preVisitSaksbehandler(
            saksbehandler: Saksbehandler,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            forklaring: String?,
            subsumsjon: Subsumsjon?,
            tidsstempel: LocalDateTime
        ) {
            pushState(SaksbehandlerInntektState(id) { inntektsopplysning -> tilstand.lagreInntekt(this, inntektsopplysning) })
        }

        override fun preVisitSkjønnsmessigFastsatt(
            saksbehandler: SkjønnsmessigFastsatt,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            pushState(SkjønnsmessigFastsattInntektState(id) { inntektsopplysning -> tilstand.lagreInntekt(this, inntektsopplysning) })
        }

        override fun visitInntektsmelding(
            inntektsmelding: Inntektsmelding,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            tilstand.lagreInntekt(this, mapOf(
                "id" to id,
                "dato" to dato,
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "kilde" to Inntektsopplysningskilde.INNTEKTSMELDING,
                "tidsstempel" to tidsstempel
            ))
        }

        override fun visitIkkeRapportert(
            ikkeRapportert: IkkeRapportert,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            tilstand.lagreInntekt(this, mapOf(
                "id" to id,
                "hendelseId" to hendelseId,
                "dato" to dato,
                "kilde" to Inntektsopplysningskilde.IKKE_RAPPORTERT,
                "tidsstempel" to tidsstempel
            ))
        }

        override fun visitInfotrygd(
            infotrygd: Infotrygd,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            tilstand.lagreInntekt(this, mapOf(
                "id" to id,
                "dato" to dato,
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "kilde" to Inntektsopplysningskilde.INFOTRYGD,
                "tidsstempel" to tidsstempel
            ))
        }

        override fun preVisitSkattSykepengegrunnlag(
            skattSykepengegrunnlag: SkattSykepengegrunnlag,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            pushState(SkattSykepengegrunnlagState { inntektsopplysning -> tilstand.lagreInntekt(this, inntektsopplysning) })
        }

        override fun postVisitArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning, orgnummer: String) {
            this.arbeidsgiverInntektsopplysninger.add(
                mapOf(
                    "orgnummer" to orgnummer,
                    "inntektsopplysning" to inntektsopplysning,
                    "refusjonsopplysninger" to refusjonsopplysninger.toList()
                )
            )
            popState()
        }

        private sealed interface Tilstand {
            fun lagreInntekt(builder: ArbeidsgiverInntektsopplysningState, inntektsopplysning: Map<String, Any?>)
            object FangeInntekt : Tilstand {
                override fun lagreInntekt(builder: ArbeidsgiverInntektsopplysningState, inntektsopplysning: Map<String, Any?>) {
                    builder.inntektsopplysning = inntektsopplysning
                    builder.tilstand = HarFangetInntekt
                }
            }
            object HarFangetInntekt : Tilstand {
                override fun lagreInntekt(builder: ArbeidsgiverInntektsopplysningState, inntektsopplysning: Map<String, Any?>) {}
            }
        }
    }

    class SaksbehandlerInntektState(private val saksbehandlerinntektId: UUID, private val lagreInntekt: (Map<String, Any?>) -> Unit): BuilderState() {
        private var overstyrtInntektId: UUID? = null

        private fun lagreId(id: UUID) {
            if (overstyrtInntektId != null) return // har allerede registrert id
            overstyrtInntektId = id
        }

        override fun visitIkkeRapportert(
            ikkeRapportert: IkkeRapportert,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun visitInntektsmelding(
            inntektsmelding: Inntektsmelding,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun visitInfotrygd(
            infotrygd: Infotrygd,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun preVisitSkattSykepengegrunnlag(
            skattSykepengegrunnlag: SkattSykepengegrunnlag,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun preVisitSkjønnsmessigFastsatt(
            saksbehandler: SkjønnsmessigFastsatt,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun preVisitSaksbehandler(
            saksbehandler: Saksbehandler,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            forklaring: String?,
            subsumsjon: Subsumsjon?,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun postVisitSaksbehandler(
            saksbehandler: Saksbehandler,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            forklaring: String?,
            subsumsjon: Subsumsjon?,
            tidsstempel: LocalDateTime
        ) {
            if (id !== saksbehandlerinntektId) return

            val inntektDetaljer = mutableMapOf(
                "id" to id,
                "dato" to dato,
                "overstyrtInntektId" to checkNotNull(overstyrtInntektId) { "En saksbehandlerinntekt skal peke til en overstyrt inntekt!" },
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "kilde" to Inntektsopplysningskilde.SAKSBEHANDLER,
                "forklaring" to forklaring,
                "subsumsjon" to subsumsjon?.let {
                    mapOf(
                        "paragraf" to subsumsjon.paragraf,
                        "ledd" to subsumsjon.ledd,
                        "bokstav" to subsumsjon.bokstav
                    )
                },
                "tidsstempel" to tidsstempel
            )
            lagreInntekt(inntektDetaljer)
            popState()
        }
    }
    class SkjønnsmessigFastsattInntektState(private val skjønnsmessigFastsattId: UUID, private val lagreInntekt: (Map<String, Any?>) -> Unit): BuilderState() {
        private var overstyrtInntektId: UUID? = null

        private fun lagreId(id: UUID) {
            if (overstyrtInntektId != null) return // har allerede registrert id
            overstyrtInntektId = id
        }

        override fun visitIkkeRapportert(
            ikkeRapportert: IkkeRapportert,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun visitInntektsmelding(
            inntektsmelding: Inntektsmelding,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun visitInfotrygd(
            infotrygd: Infotrygd,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun preVisitSkattSykepengegrunnlag(
            skattSykepengegrunnlag: SkattSykepengegrunnlag,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun preVisitSkjønnsmessigFastsatt(
            saksbehandler: SkjønnsmessigFastsatt,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun preVisitSaksbehandler(
            saksbehandler: Saksbehandler,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            forklaring: String?,
            subsumsjon: Subsumsjon?,
            tidsstempel: LocalDateTime
        ) {
            lagreId(id)
        }

        override fun postVisitSkjønnsmessigFastsatt(
            saksbehandler: SkjønnsmessigFastsatt,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            if (id != skjønnsmessigFastsattId) return
            val inntektDetaljer = mutableMapOf(
                "id" to id,
                "dato" to dato,
                "overstyrtInntektId" to checkNotNull(overstyrtInntektId) { "En skjønnsmessig inntekt skal peke til en overstyrt inntekt!" },
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "kilde" to Inntektsopplysningskilde.SKJØNNSMESSIG_FASTSATT,
                "tidsstempel" to tidsstempel
            )
            lagreInntekt(inntektDetaljer)
            popState()
        }
    }

    class SkattSykepengegrunnlagState(private val lagreInntekt: (Map<String, Any?>) -> Unit) : BuilderState() {
        private val skatteopplysninger = mutableListOf<Map<String, Any>>()

        override fun visitSkatteopplysning(
            skatteopplysning: Skatteopplysning,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Skatteopplysning.Inntekttype,
            fordel: String,
            beskrivelse: String,
            tidsstempel: LocalDateTime
        ) {
            skatteopplysninger.add(mapOf(
                "kilde" to "SKATT_SYKEPENGEGRUNNLAG",
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "tidsstempel" to tidsstempel,
                "måned" to måned,
                "type" to type,
                "fordel" to fordel,
                "beskrivelse" to beskrivelse
            ))
        }

        override fun postVisitSkattSykepengegrunnlag(
            skattSykepengegrunnlag: SkattSykepengegrunnlag,
            id: UUID,
            hendelseId: UUID,
            dato: LocalDate,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            this.lagreInntekt(mapOf(
                "id" to id,
                "hendelseId" to hendelseId,
                "dato" to dato,
                "kilde" to "SKATT_SYKEPENGEGRUNNLAG",
                "skatteopplysninger" to skatteopplysninger,
                "tidsstempel" to tidsstempel
            ))
            popState()
        }
    }

    class ArbeidsgiverInntektsopplysningerForSammenligningsgrunnlagState(private val arbeidsgiverInntektsopplysninger: MutableList<Map<String, Any>>) : BuilderState() {

        override fun preVisitArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
            arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag,
            orgnummer: String,
            rapportertInntekt: Inntekt
        ) {
            pushState(ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagState(arbeidsgiverInntektsopplysninger))
        }

        override fun postVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag>) {
            popState()
        }
    }

    class ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagState(private val arbeidsgiverInntektsopplysninger: MutableList<Map<String, Any>>) : BuilderState() {
        private val inntektsopplysninger = mutableListOf<Map<String, Any>>()

        override fun visitSkatteopplysning(
            skatteopplysning: Skatteopplysning,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Skatteopplysning.Inntekttype,
            fordel: String,
            beskrivelse: String,
            tidsstempel: LocalDateTime
        ) {
            inntektsopplysninger.add(mapOf(
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "tidsstempel" to tidsstempel,
                "måned" to måned,
                "type" to type,
                "fordel" to fordel,
                "beskrivelse" to beskrivelse
            ))
        }

        override fun postVisitArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
            arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag,
            orgnummer: String,
            rapportertInntekt: Inntekt
        ) {
            this.arbeidsgiverInntektsopplysninger.add(
                mapOf(
                    "orgnummer" to orgnummer,
                    "skatteopplysninger" to inntektsopplysninger
                )
            )
            popState()
        }
    }

    private class ForkastetVedtaksperiodeState(private val vedtaksperiodeMap: MutableMap<String, Any?>) : BuilderState() {

        override fun preVisitVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            id: UUID,
            tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
            opprettet: LocalDateTime,
            oppdatert: LocalDateTime,
            periode: Periode,
            opprinneligPeriode: Periode,
            skjæringstidspunkt: () -> LocalDate,
            skjæringstidspunktFraInfotrygd: LocalDate?,
            forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
            hendelseIder: Set<Dokumentsporing>,
            inntektsmeldingInfo: InntektsmeldingInfo?
        ) {
            pushState(VedtaksperiodeState(
                vedtaksperiodeMap = vedtaksperiodeMap
            ))
        }

        override fun postVisitForkastetPeriode(vedtaksperiode: Vedtaksperiode) {
            popState()
        }
    }

    internal class SykmeldingsperioderState(private val sykmeldingsperioder: MutableList<Map<String, Any>>) : BuilderState() {
        override fun visitSykmeldingsperiode(periode: Periode) {
            sykmeldingsperioder.add(mapOf("fom" to periode.start, "tom" to periode.endInclusive))
        }

        override fun postVisitSykmeldingsperioder(sykmeldingsperioder: Sykmeldingsperioder) {
            popState()
        }
    }

    private class UtbetalingerState(private val utbetalinger: MutableList<MutableMap<String, Any?>>) : BuilderState() {

        override fun preVisitUtbetaling(
            utbetaling: Utbetaling,
            id: UUID,
            korrelasjonsId: UUID,
            type: Utbetalingtype,
            utbetalingstatus: Utbetalingstatus,
            periode: Periode,
            tidsstempel: LocalDateTime,
            oppdatert: LocalDateTime,
            arbeidsgiverNettoBeløp: Int,
            personNettoBeløp: Int,
            maksdato: LocalDate,
            forbrukteSykedager: Int?,
            gjenståendeSykedager: Int?,
            stønadsdager: Int,
            beregningId: UUID,
            overføringstidspunkt: LocalDateTime?,
            avsluttet: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            annulleringer: Set<UUID>
        ) {
            val utbetalingMap = mutableMapOf<String, Any?>()
            utbetalinger.add(utbetalingMap)
            pushState(UtbetalingState(utbetalingMap))
        }

        override fun postVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
            popState()
        }
    }

    private class UtbetalingState(private val utbetalingMap: MutableMap<String, Any?>) : BuilderState() {
        private val utbetalingstidslinjeMap = mutableMapOf<String, Any>()
        private val arbeidsgiverOppdragMap = mutableMapOf<String, Any?>()
        private val personOppdragMap = mutableMapOf<String, Any?>()
        private var vurderingMap: Map<String, Any?>? = null

        override fun preVisitUtbetalingsdager(dager: Collection<Utbetalingsdag>) {
            pushState(UtbetalingstidslinjeState(utbetalingMap))
        }

        override fun preVisitArbeidsgiverOppdrag(oppdrag: Oppdrag) {
            pushState(OppdragState(arbeidsgiverOppdragMap))
        }

        override fun preVisitPersonOppdrag(oppdrag: Oppdrag) {
            pushState(OppdragState(personOppdragMap))
        }

        override fun visitVurdering(
            vurdering: Utbetaling.Vurdering,
            ident: String,
            epost: String,
            tidspunkt: LocalDateTime,
            automatiskBehandling: Boolean,
            godkjent: Boolean
        ) {
            vurderingMap = mapOf<String, Any?>(
                "godkjent" to godkjent,
                "ident" to ident,
                "epost" to epost,
                "tidspunkt" to tidspunkt,
                "automatiskBehandling" to automatiskBehandling
            )
        }

        override fun postVisitUtbetaling(
            utbetaling: Utbetaling,
            id: UUID,
            korrelasjonsId: UUID,
            type: Utbetalingtype,
            tilstand: Utbetalingstatus,
            periode: Periode,
            tidsstempel: LocalDateTime,
            oppdatert: LocalDateTime,
            arbeidsgiverNettoBeløp: Int,
            personNettoBeløp: Int,
            maksdato: LocalDate,
            forbrukteSykedager: Int?,
            gjenståendeSykedager: Int?,
            stønadsdager: Int,
            beregningId: UUID,
            overføringstidspunkt: LocalDateTime?,
            avsluttet: LocalDateTime?,
            avstemmingsnøkkel: Long?,
            annulleringer: Set<UUID>
        ) {
            utbetalingMap["id"] = id
            utbetalingMap["korrelasjonsId"] = korrelasjonsId
            utbetalingMap["beregningId"] = beregningId
            utbetalingMap["annulleringer"] = annulleringer
            utbetalingMap["utbetalingstidslinje"] = utbetalingstidslinjeMap
            utbetalingMap["arbeidsgiverOppdrag"] = arbeidsgiverOppdragMap
            utbetalingMap["personOppdrag"] = personOppdragMap
            utbetalingMap["fom"] = periode.start
            utbetalingMap["tom"] = periode.endInclusive
            utbetalingMap["stønadsdager"] = stønadsdager
            utbetalingMap["tidsstempel"] = tidsstempel
            utbetalingMap["status"] = tilstand
            utbetalingMap["type"] = type
            utbetalingMap["maksdato"] = maksdato
            utbetalingMap["forbrukteSykedager"] = forbrukteSykedager
            utbetalingMap["gjenståendeSykedager"] = gjenståendeSykedager
            utbetalingMap["vurdering"] = vurderingMap
            utbetalingMap["overføringstidspunkt"] = overføringstidspunkt
            utbetalingMap["avstemmingsnøkkel"] = avstemmingsnøkkel?.let { "$it" }
            utbetalingMap["avsluttet"] = avsluttet
            utbetalingMap["oppdatert"] = oppdatert
            popState()
        }
    }

    private class InntektshistorikkState(private val inntekter: MutableList<Map<String, Any?>>) :
        BuilderState() {

        override fun visitInntektsmelding(
            inntektsmelding: Inntektsmelding,
            id: UUID,
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime
        ) {
            inntekter.add(mapOf(
                "id" to id,
                "dato" to dato,
                "hendelseId" to hendelseId,
                "beløp" to beløp.reflection { _, månedlig, _, _ -> månedlig },
                "tidsstempel" to tidsstempel
            ))
        }

        override fun postVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
            popState()
        }
    }

    private class VedtaksperiodeState(private val vedtaksperiodeMap: MutableMap<String, Any?>) : BuilderState() {
        private val utbetalinger = mutableListOf<Map<String, Any?>>()

        override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {
            val sykdomstidslinje = mutableMapOf<String, Any?>()

            vedtaksperiodeMap["sykdomstidslinje"] = sykdomstidslinje
            pushState(SykdomstidslinjeState(sykdomstidslinje))
        }

        override fun preVisitVedtakserperiodeUtbetalinger(utbetalinger: List<Triple<VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?, Utbetaling, Sykdomstidslinje?>>) {
            pushState(VedtaksperiodeUtbetalingerState(this.utbetalinger))
        }

        override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {
            val utbetalingstidslinjeMap = mutableMapOf<String, Any?>()
            vedtaksperiodeMap["utbetalingstidslinje"] = utbetalingstidslinjeMap
            pushState(UtbetalingstidslinjeState(utbetalingstidslinjeMap))
        }

        override fun visitInntektsmeldinginfo(id: UUID, arbeidsforholdId: String?) {
            vedtaksperiodeMap["inntektsmeldingInfo"] = mapOf("id" to id, "arbeidsforholdId" to arbeidsforholdId)
        }

        override fun postVisitVedtaksperiode(
            vedtaksperiode: Vedtaksperiode,
            id: UUID,
            tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
            opprettet: LocalDateTime,
            oppdatert: LocalDateTime,
            periode: Periode,
            opprinneligPeriode: Periode,
            skjæringstidspunkt: () -> LocalDate,
            skjæringstidspunktFraInfotrygd: LocalDate?,
            forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
            hendelseIder: Set<Dokumentsporing>,
            inntektsmeldingInfo: InntektsmeldingInfo?
        ) {
            vedtaksperiodeMap.putAll(mutableMapOf(
                "id" to id,
                "fom" to periode.start,
                "tom" to periode.endInclusive,
                "sykmeldingFom" to opprinneligPeriode.start,
                "sykmeldingTom" to opprinneligPeriode.endInclusive,
                "hendelseIder" to hendelseIder.toJsonList().map { (id, type) ->
                    mapOf(
                        "dokumentId" to id,
                        "dokumenttype" to type.name
                    )
                },
                "tilstand" to tilstand.type.name,
                "utbetalinger" to this.utbetalinger,
                "skjæringstidspunktFraInfotrygd" to skjæringstidspunktFraInfotrygd,
                "skjæringstidspunkt" to skjæringstidspunkt(),
                "forlengelseFraInfotrygd" to forlengelseFraInfotrygd,
                "opprettet" to opprettet,
                "oppdatert" to oppdatert
            ))

            popState()
        }
    }

    private class VedtaksperiodeUtbetalingerState(private val utbetalinger: MutableList<Map<String, Any?>>) : BuilderState() {
        override fun preVisitVedtaksperiodeUtbetaling(
            grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
            utbetaling: Utbetaling,
            sykdomstidslinje: Sykdomstidslinje
        ) {
            pushState(VedtaksperiodeUtbetalingState(utbetalinger))
        }

        override fun postVisitVedtakserperiodeUtbetalinger(utbetalinger: List<Triple<VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?, Utbetaling, Sykdomstidslinje?>>) {
            popState()
        }

        private class VedtaksperiodeUtbetalingState(private val utbetalinger: MutableList<Map<String, Any?>>) : BuilderState() {
            private lateinit var grunnlagId: UUID
            private lateinit var utbetalingId: UUID
            private val sykdomstidslinjedetaljer = mutableMapOf<String, Any?>()

            override fun preVisitGrunnlagsdata(
                skjæringstidspunkt: LocalDate,
                grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
                sykepengegrunnlag: Sykepengegrunnlag,
                opptjening: Opptjening,
                vurdertOk: Boolean,
                meldingsreferanseId: UUID?,
                vilkårsgrunnlagId: UUID,
                medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus
            ) {
                this.grunnlagId = vilkårsgrunnlagId
            }

            override fun preVisitInfotrygdVilkårsgrunnlag(
                infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
                skjæringstidspunkt: LocalDate,
                sykepengegrunnlag: Sykepengegrunnlag,
                vilkårsgrunnlagId: UUID
            ) {
                this.grunnlagId = vilkårsgrunnlagId
            }

            override fun preVisitUtbetaling(
                utbetaling: Utbetaling,
                id: UUID,
                korrelasjonsId: UUID,
                type: Utbetalingtype,
                utbetalingstatus: Utbetalingstatus,
                periode: Periode,
                tidsstempel: LocalDateTime,
                oppdatert: LocalDateTime,
                arbeidsgiverNettoBeløp: Int,
                personNettoBeløp: Int,
                maksdato: LocalDate,
                forbrukteSykedager: Int?,
                gjenståendeSykedager: Int?,
                stønadsdager: Int,
                beregningId: UUID,
                overføringstidspunkt: LocalDateTime?,
                avsluttet: LocalDateTime?,
                avstemmingsnøkkel: Long?,
                annulleringer: Set<UUID>
            ) {
                utbetalingId = id
            }

            override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {
                pushState(SykdomstidslinjeState(sykdomstidslinjedetaljer))
            }

            override fun postVisitVedtaksperiodeUtbetaling(
                grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
                utbetaling: Utbetaling,
                sykdomstidslinje: Sykdomstidslinje
            ) {
                this.utbetalinger.add(mapOf(
                    "utbetalingId" to this.utbetalingId.toString(),
                    "vilkårsgrunnlagId" to this.grunnlagId.toString(),
                    "sykdomstidslinje" to sykdomstidslinjedetaljer
                ))
                popState()
            }
        }
    }

    private class UtbetalingstidslinjeState(private val utbetalingstidslinjeMap: MutableMap<String, Any?>) : BuilderState() {
        private val dager = DateRanges()

        private fun leggTilDag(dato: LocalDate, builder: UtbetalingsdagJsonBuilder) {
            dager.plus(dato, builder.build())
        }

        override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.ArbeidsgiverperiodedagNav).økonomi(økonomi))
        }

        override fun visit(dag: ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.ArbeidsgiverperiodeDag).økonomi(økonomi))
        }

        override fun visit(dag: NavDag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.NavDag).økonomi(økonomi))
        }

        override fun visit(dag: NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.NavHelgDag).økonomi(økonomi))
        }

        override fun visit(dag: Arbeidsdag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.Arbeidsdag).økonomi(økonomi))
        }

        override fun visit(dag: Fridag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.Fridag).økonomi(økonomi))
        }

        override fun visit(dag: AvvistDag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.AvvistDag).økonomi(økonomi).begrunnelser(dag.begrunnelser))
        }

        override fun visit(dag: ForeldetDag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.ForeldetDag).økonomi(økonomi))
        }

        override fun visit(dag: UkjentDag, dato: LocalDate, økonomi: Økonomi) {
            leggTilDag(dato, UtbetalingsdagJsonBuilder(TypeData.UkjentDag).økonomi(økonomi))
        }

        override fun postVisitUtbetalingstidslinje() {
            utbetalingstidslinjeMap["dager"] = dager.toList()
            popState()
        }

        override fun postVisitUtbetalingsdager(dager: Collection<Utbetalingsdag>) {
            utbetalingstidslinjeMap["dager"] = this.dager.toList()
            popState()
        }
    }

    private class SykdomshistorikkState(
        private val sykdomshistorikkElementer: MutableList<MutableMap<String, Any?>>
    ) : BuilderState() {
        override fun preVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            hendelseId: UUID?,
            tidsstempel: LocalDateTime
        ) {
            val elementMap = mutableMapOf<String, Any?>()
            sykdomshistorikkElementer.add(elementMap)

            pushState(SykdomshistorikkElementState(id, hendelseId, tidsstempel, elementMap))
        }

        override fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
            popState()
        }
    }

    private class SykdomshistorikkElementState(
        id: UUID,
        hendelseId: UUID?,
        tidsstempel: LocalDateTime,
        private val elementMap: MutableMap<String, Any?>
    ) : BuilderState() {
        init {
            elementMap["id"] = id
            elementMap["hendelseId"] = hendelseId
            elementMap["tidsstempel"] = tidsstempel
        }

        override fun preVisitHendelseSykdomstidslinje(
            tidslinje: Sykdomstidslinje,
            hendelseId: UUID?,
            tidsstempel: LocalDateTime
        ) {
            val sykdomstidslinje = mutableMapOf<String, Any?>()
            elementMap["hendelseSykdomstidslinje"] = sykdomstidslinje
            pushState(SykdomstidslinjeState(sykdomstidslinje))
        }

        override fun preVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {
            val sykdomstidslinje = mutableMapOf<String, Any?>()
            elementMap["beregnetSykdomstidslinje"] = sykdomstidslinje
            pushState(SykdomstidslinjeState(sykdomstidslinje))
        }

        override fun postVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            hendelseId: UUID?,
            tidsstempel: LocalDateTime
        ) {
            popState()
        }
    }

    private class SykdomstidslinjeState(private val sykdomstidslinje: MutableMap<String, Any?>) : BuilderState() {
        private val dateRanges = DateRanges()

        override fun postVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: MutableList<Periode>) {
            sykdomstidslinje["låstePerioder"] = låstePerioder.map {
                mapOf("fom" to it.start, "tom" to it.endInclusive)
            }
            sykdomstidslinje["periode"] = tidslinje.periode()?.let { mapOf("fom" to it.start, "tom" to it.endInclusive) }

            sykdomstidslinje["dager"] = dateRanges.toList()
            popState()
        }

        override fun visitDag(dag: Dag.Arbeidsdag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dato, DagJsonBuilder(ARBEIDSDAG, kilde))

        override fun visitDag(
            dag: Arbeidsgiverdag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = leggTilDag(dato, DagJsonBuilder(ARBEIDSGIVERDAG, kilde).økonomi(økonomi))

        override fun visitDag(dag: Dag.Feriedag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dato, DagJsonBuilder(FERIEDAG, kilde))

        override fun visitDag(dag: Dag.FerieUtenSykmeldingDag, dato: LocalDate, kilde: Hendelseskilde)  =
            leggTilDag(dato, DagJsonBuilder(FERIE_UTEN_SYKMELDINGDAG, kilde))

        override fun visitDag(dag: Dag.Permisjonsdag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dato, DagJsonBuilder(PERMISJONSDAG, kilde))

        override fun visitDag(dag: Dag.FriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) =
            leggTilDag(dato, DagJsonBuilder(FRISK_HELGEDAG, kilde))

        override fun visitDag(
            dag: Dag.ArbeidsgiverHelgedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = leggTilDag(dato, DagJsonBuilder(ARBEIDSGIVERDAG, kilde).økonomi(økonomi))

        override fun visitDag(
            dag: Dag.Sykedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = leggTilDag(dato, DagJsonBuilder(SYKEDAG, kilde).økonomi(økonomi))

        override fun visitDag(dag: Dag.SykedagNav, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) =
            leggTilDag(dato, DagJsonBuilder(SYKEDAG_NAV, kilde).økonomi(økonomi))

        override fun visitDag(
            dag: Dag.ForeldetSykedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = leggTilDag(dato, DagJsonBuilder(FORELDET_SYKEDAG, kilde).økonomi(økonomi))

        override fun visitDag(
            dag: Dag.SykHelgedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = leggTilDag(dato, DagJsonBuilder(SYKEDAG, kilde).økonomi(økonomi))

        override fun visitDag(
            dag: Dag.ProblemDag,
            dato: LocalDate,
            kilde: Hendelseskilde,
            other: Hendelseskilde?,
            melding: String
        ) = leggTilDag(dato, DagJsonBuilder(PROBLEMDAG, kilde).annenKilde(other).melding(melding))

        override fun visitDag(
            dag: Dag.AndreYtelser,
            dato: LocalDate,
            kilde: Hendelseskilde,
            ytelse: Dag.AndreYtelser.AnnenYtelse
        ) {
            leggTilDag(dato, DagJsonBuilder(
                when (ytelse) {
                    Foreldrepenger -> ANDRE_YTELSER_FORELDREPENGER
                    AAP -> ANDRE_YTELSER_AAP
                    Omsorgspenger -> ANDRE_YTELSER_OMSORGSPENGER
                    Pleiepenger -> ANDRE_YTELSER_PLEIEPENGER
                    Svangerskapspenger -> ANDRE_YTELSER_SVANGERSKAPSPENGER
                    Opplæringspenger -> ANDRE_YTELSER_OPPLÆRINGSPENGER
                    Dagpenger -> ANDRE_YTELSER_DAGPENGER
                }, kilde
            ))
        }

        private fun leggTilDag(dato: LocalDate, builder: DagJsonBuilder) {
            dateRanges.plus(dato, builder.build())
        }
    }

    private class DagJsonBuilder(
        private val type: JsonDagType,
        private val kilde: Hendelseskilde
    ) {
        private var otherKilde: Hendelseskilde? = null
        private var melding: String? = null
        private var økonomiBuilder: ØkonomiJsonBuilder? = null

        fun annenKilde(other: Hendelseskilde?) = apply {
            this.otherKilde = other
        }

        fun melding(melding: String?) = apply {
            this.melding = melding
        }

        fun økonomi(økonomi: Økonomi) = apply {
            this.økonomiBuilder = ØkonomiJsonBuilder().also { økonomi.builder(builder = it) }
        }

        fun build() = mutableMapOf<String, Any>().apply {
            this["type"] = type
            this["kilde"] = kilde.toJson()
            this.compute("other") { _, _ -> otherKilde?.toJson() }
            this.compute("melding") { _, _ -> melding }
            økonomiBuilder?.build()?.also { putAll(it) }
        }
    }

    private class UtbetalingsdagJsonBuilder(
        private val type: TypeData
    ) {
        private var økonomiBuilder: ØkonomiJsonBuilder? = null
        private var begrunnelser: List<PersonData.UtbetalingstidslinjeData.BegrunnelseData>? = null

        fun økonomi(økonomi: Økonomi) = apply {
            this.økonomiBuilder = ØkonomiJsonBuilder().also { økonomi.builder(it) }
        }

        fun begrunnelser(begrunnelser: List<Begrunnelse>) = apply {
            this.begrunnelser = begrunnelser.map { PersonData.UtbetalingstidslinjeData.BegrunnelseData.fraBegrunnelse(it) }
        }

        fun build() = mutableMapOf<String, Any>().apply {
            this["type"] = type
            this.compute("begrunnelser") { _, _ -> begrunnelser }
            økonomiBuilder?.build()?.also { putAll(it) }
        }
    }

    private class ØkonomiJsonBuilder : ØkonomiBuilder() {
        fun build() = mutableMapOf<String, Any>().apply {
            this["grad"] = grad
            this.compute("totalGrad") { _, _ -> totalGrad }
            this.compute("arbeidsgiverRefusjonsbeløp") { _, _ -> arbeidsgiverRefusjonsbeløp }
            this.compute("grunnbeløpgrense") { _, _ -> grunnbeløpgrense }
            this.compute("dekningsgrunnlag") { _, _ -> dekningsgrunnlag }
            this.compute("aktuellDagsinntekt") { _, _ -> aktuellDagsinntekt }
            this.compute("arbeidsgiverbeløp") { _, _ -> arbeidsgiverbeløp }
            this.compute("personbeløp") { _, _ -> personbeløp }
            this.compute("er6GBegrenset") { _, _ -> er6GBegrenset }
        }
    }
}

