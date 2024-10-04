package no.nav.helse.utbetalingslinjer

import java.time.LocalDateTime
import java.time.Month
import java.time.Year
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.dto.deserialisering.FeriepengeInnDto
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.dto.serialisering.FeriepengeUtDto
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.UtbetalingEndretEvent.OppdragEventDetaljer
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_2
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import kotlin.math.roundToInt

internal class Feriepengeutbetaling private constructor(
    private val feriepengeberegner: Feriepengeberegner,
    private val infotrygdFeriepengebeløpPerson: Double,
    private val infotrygdFeriepengebeløpArbeidsgiver: Double,
    private val spleisFeriepengebeløpArbeidsgiver: Double,
    private val spleisFeriepengebeløpPerson: Double,
    private val oppdrag: Oppdrag,
    private val personoppdrag: Oppdrag,
    private val utbetalingId: UUID,
    private val sendTilOppdrag: Boolean,
    private val sendPersonoppdragTilOS: Boolean,
) : Aktivitetskontekst {
    var overføringstidspunkt: LocalDateTime? = null
    var avstemmingsnøkkel: Long? = null

    companion object {
        fun List<Feriepengeutbetaling>.gjelderFeriepengeutbetaling(hendelse: UtbetalingHendelse) = any { hendelse.fagsystemId == it.oppdrag.fagsystemId || hendelse.fagsystemId == it.personoppdrag.fagsystemId }

        internal fun gjenopprett(alder: Alder, dto: FeriepengeInnDto): Feriepengeutbetaling {
            return Feriepengeutbetaling(
                feriepengeberegner = Feriepengeberegner.gjenopprett(alder, dto.feriepengeberegner),
                infotrygdFeriepengebeløpPerson = dto.infotrygdFeriepengebeløpPerson,
                infotrygdFeriepengebeløpArbeidsgiver = dto.infotrygdFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpArbeidsgiver = dto.spleisFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpPerson = dto.spleisFeriepengebeløpPerson,
                oppdrag = Oppdrag.gjenopprett(dto.oppdrag),
                personoppdrag = Oppdrag.gjenopprett(dto.personoppdrag),
                utbetalingId = dto.utbetalingId,
                sendTilOppdrag = dto.sendTilOppdrag,
                sendPersonoppdragTilOS = dto.sendPersonoppdragTilOS
            )
        }
    }

    internal fun view() = FeriepengeutbetalingView(
        infotrygdFeriepengebeløpPerson = infotrygdFeriepengebeløpPerson,
        infotrygdFeriepengebeløpArbeidsgiver = infotrygdFeriepengebeløpArbeidsgiver,
        spleisFeriepengebeløpArbeidsgiver = spleisFeriepengebeløpArbeidsgiver,
        spleisFeriepengebeløpPerson = spleisFeriepengebeløpPerson,
        oppdrag = oppdrag,
        personoppdrag = personoppdrag
    )

    fun håndter(utbetalingHendelse: UtbetalingHendelse, organisasjonsnummer: String, person: Person) {
        if (utbetalingHendelse.utbetalingId != this.utbetalingId || utbetalingHendelse.fagsystemId !in setOf(oppdrag.fagsystemId, personoppdrag.fagsystemId)) return

        utbetalingHendelse.info("Behandler svar fra Oppdrag/UR/spenn for feriepenger")
        when (utbetalingHendelse.status) {
            Oppdragstatus.OVERFØRT,
            Oppdragstatus.AKSEPTERT -> { } // all is good
            Oppdragstatus.AKSEPTERT_MED_FEIL -> utbetalingHendelse.varsel(RV_UT_2)
            Oppdragstatus.AVVIST,
            Oppdragstatus.FEIL -> utbetalingHendelse.info("Utbetaling feilet med status ${utbetalingHendelse.status}. Feilmelding fra Oppdragsystemet: ${utbetalingHendelse.melding}")
        }
        val utbetaltOk = !utbetalingHendelse.harFunksjonelleFeilEllerVerre()
        lagreInformasjon(utbetalingHendelse, utbetaltOk)

        if (!utbetaltOk) {
            utbetalingHendelse.info("Utbetaling av feriepenger med utbetalingId $utbetalingId og fagsystemIder ${oppdrag.fagsystemId} og ${personoppdrag.fagsystemId} feilet.")
            return
        }

        person.feriepengerUtbetalt(
            PersonObserver.FeriepengerUtbetaltEvent(
                organisasjonsnummer = organisasjonsnummer,
                arbeidsgiverOppdrag = PersonObserver.FeriepengerUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(oppdrag),
                personOppdrag = PersonObserver.FeriepengerUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(personoppdrag)
            )
        )

        person.utbetalingEndret(
            PersonObserver.UtbetalingEndretEvent(
                organisasjonsnummer = organisasjonsnummer,
                utbetalingId = utbetalingId,
                type = Utbetalingtype.FERIEPENGER.name,
                forrigeStatus = Utbetalingstatus.IKKE_UTBETALT.name,
                gjeldendeStatus = Utbetalingstatus.UTBETALT.name,
                arbeidsgiverOppdrag = OppdragEventDetaljer.mapOppdrag(oppdrag),
                personOppdrag = OppdragEventDetaljer.mapOppdrag(personoppdrag),
                korrelasjonsId = UUID.randomUUID()
            )
        )
    }

    private fun lagreInformasjon(hendelse: UtbetalingHendelse, gikkBra: Boolean) {
        overføringstidspunkt = hendelse.overføringstidspunkt
        avstemmingsnøkkel = hendelse.avstemmingsnøkkel
        hendelse.info("Data for feriepenger fra Oppdrag/UR: tidspunkt: $overføringstidspunkt, avstemmingsnøkkel $avstemmingsnøkkel og utbetalt ok: ${if (gikkBra) "ja" else "nei"}")
    }

    override fun toSpesifikkKontekst() =
        SpesifikkKontekst("Feriepengeutbetaling", mapOf("utbetalingId" to "$utbetalingId"))

    internal fun overfør(hendelse: PersonHendelse) {
        hendelse.kontekst(this)
        if (sendTilOppdrag) oppdrag.overfør(hendelse, null, "SPLEIS")
        if (sendPersonoppdragTilOS) personoppdrag.overfør(hendelse, null, "SPLEIS")
    }

    internal fun gjelderForÅr(år: Year) = feriepengeberegner.gjelderForÅr(år)

    internal class Builder(
        private val aktørId: String,
        private val personidentifikator: Personidentifikator,
        private val orgnummer: String,
        private val feriepengeberegner: Feriepengeberegner,
        private val utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger,
        private val tidligereFeriepengeutbetalinger: List<Feriepengeutbetaling>
    ) {
        private fun oppdrag(fagområde: Fagområde, forrigeOppdrag: Oppdrag?, beløp: Int): Oppdrag {
            val (klassekode, mottaker) = when (fagområde) {
                Fagområde.SykepengerRefusjon -> Klassekode.RefusjonFeriepengerIkkeOpplysningspliktig to orgnummer
                Fagområde.Sykepenger -> Klassekode.SykepengerArbeidstakerFeriepenger to personidentifikator.toString()
            }
            val fagsystemId = forrigeOppdrag?.fagsystemId ?: genererUtbetalingsreferanse(UUID.randomUUID())

            val nyttOppdrag = Oppdrag(
                mottaker = mottaker,
                fagområde = fagområde,
                linjer = listOf(
                    Utbetalingslinje(
                        fom = utbetalingshistorikkForFeriepenger.opptjeningsår.plusYears(1).atMonth(Month.MAY).atDay(1),
                        tom = utbetalingshistorikkForFeriepenger.opptjeningsår.plusYears(1).atMonth(Month.MAY).atEndOfMonth(),
                        satstype = Satstype.Engang,
                        beløp = beløp,
                        grad = null,
                        klassekode = klassekode,
                    )
                ),
                fagsystemId = fagsystemId,
            )

            if (forrigeOppdrag == null) return nyttOppdrag
            if (beløp == 0) forrigeOppdrag.annuller(utbetalingshistorikkForFeriepenger)
            return nyttOppdrag.minus(forrigeOppdrag, utbetalingshistorikkForFeriepenger)
        }

        private fun skalSendeOppdrag(forrigeOppdrag: Oppdrag?, beløp: Int): Boolean {
            if (forrigeOppdrag == null) return beløp != 0
            return beløp != forrigeOppdrag.totalbeløp() || beløp == 0
        }

        private val Double.finere get() = "$this".padStart(10, ' ')
        private val List<Periode>.finere get() = joinToString(separator = "\n\t\t\t\t\t", prefix = "\n\t\t\t\t\t") {"$it (${it.count()} dager)" }

        internal fun build(hendelse: IAktivitetslogg): Feriepengeutbetaling {
            // Arbeidsgiver
            val infotrygdHarUtbetaltTilArbeidsgiver = utbetalingshistorikkForFeriepenger.utbetalteFeriepengerTilArbeidsgiver(orgnummer)
            val hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver = feriepengeberegner.beregnUtbetalteFeriepengerForInfotrygdArbeidsgiver(orgnummer)

            if (hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver.roundToInt() != 0 &&
                hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver.roundToInt() !in infotrygdHarUtbetaltTilArbeidsgiver
            ) {
                hendelse.info(
                    """
                    Beregnet feriepengebeløp til arbeidsgiver i IT samsvarer ikke med faktisk utbetalt beløp
                    AktørId: $aktørId
                    Arbeidsgiver: $orgnummer
                    Infotrygd har utbetalt $infotrygdHarUtbetaltTilArbeidsgiver
                    Vi har beregnet at infotrygd har utbetalt ${hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver.roundToInt()}
                    """.trimIndent()
                )
            }

            val infotrygdFeriepengebeløpArbeidsgiver = feriepengeberegner.beregnFeriepengerForInfotrygdArbeidsgiver(orgnummer)
            val spleisFeriepengebeløpArbeidsgiver = feriepengeberegner.beregnFeriepengerForSpleisArbeidsgiver(orgnummer)
            val totaltFeriepengebeløpArbeidsgiver: Double = feriepengeberegner.beregnFeriepengerForArbeidsgiver(orgnummer)
            val differanseMellomTotalOgAlleredeUtbetaltAvInfotrygd: Double = feriepengeberegner.beregnFeriepengedifferansenForArbeidsgiver(orgnummer)
            val arbeidsgiverbeløp = differanseMellomTotalOgAlleredeUtbetaltAvInfotrygd.roundToInt()

            val forrigeSendteArbeidsgiverOppdrag =
                tidligereFeriepengeutbetalinger
                    .lastOrNull { it.gjelderForÅr(utbetalingshistorikkForFeriepenger.opptjeningsår) && it.sendTilOppdrag }
                    ?.oppdrag
                    ?.takeIf { it.linjerUtenOpphør().isNotEmpty() }

            val arbeidsgiveroppdrag = oppdrag(Fagområde.SykepengerRefusjon, forrigeSendteArbeidsgiverOppdrag, arbeidsgiverbeløp)

            if (arbeidsgiverbeløp != 0 && orgnummer == "0") hendelse.info("Forventer ikke arbeidsgiveroppdrag til orgnummer \"0\", aktørId=$aktørId.")

            val sendArbeidsgiveroppdrag = skalSendeOppdrag(forrigeSendteArbeidsgiverOppdrag, arbeidsgiverbeløp)

            // Person
            val infotrygdHarUtbetaltTilPerson = utbetalingshistorikkForFeriepenger.utbetalteFeriepengerTilPerson()
            val hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPerson = feriepengeberegner.beregnUtbetalteFeriepengerForInfotrygdPerson(orgnummer)
            if (hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPerson.roundToInt() != 0 &&
                hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPerson.roundToInt() !in infotrygdHarUtbetaltTilPerson
            ) {
                hendelse.info(
                    """
                    Beregnet feriepengebeløp til person i IT samsvarer ikke med faktisk utbetalt beløp
                    AktørId: $aktørId
                    Arbeidsgiver: $orgnummer
                    Infotrygd har utbetalt $infotrygdHarUtbetaltTilPerson
                    Vi har beregnet at infotrygd har utbetalt ${hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPerson.roundToInt()}
                    """.trimIndent()
                )
            }

            val differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson = feriepengeberegner.beregnFeriepengedifferansenForPerson(orgnummer)
            val infotrygdFeriepengebeløpPerson = feriepengeberegner.beregnFeriepengerForInfotrygdPerson(orgnummer)
            val spleisFeriepengebeløpPerson = feriepengeberegner.beregnFeriepengerForSpleisPerson(orgnummer)
            val totaltFeriepengebeløpPerson = feriepengeberegner.beregnFeriepengerForPerson(orgnummer)
            val hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver = feriepengeberegner.beregnUtbetalteFeriepengerForInfotrygdPersonForEnArbeidsgiver(orgnummer)
            val personbeløp = differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson.roundToInt()

            val forrigeSendtePersonOppdrag =
                tidligereFeriepengeutbetalinger
                    .lastOrNull { it.gjelderForÅr(utbetalingshistorikkForFeriepenger.opptjeningsår) && it.sendPersonoppdragTilOS }
                    ?.personoppdrag
                    ?.takeIf { it.linjerUtenOpphør().isNotEmpty() }

            val personoppdrag = oppdrag(Fagområde.Sykepenger, forrigeSendtePersonOppdrag, personbeløp)

            val sendPersonoppdrag = skalSendeOppdrag(forrigeSendtePersonOppdrag, personbeløp)

            if (differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson < -499 || differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson > 100) hendelse.info(
                """
                ${if (differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson < 0) "Differanse mellom det IT har utbetalt og det spleis har beregnet at IT skulle betale" else "Utbetalt for lite i Infotrygd"} for person & orgnr-kombo:
                AktørId: $aktørId
                Arbeidsgiver: $orgnummer
                Diff: $differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson
                Hva vi har beregnet at IT har utbetalt til person for denne AG: $hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver
                IT sin personandel: $infotrygdFeriepengebeløpPerson
                """.trimIndent()
            )

            val arbeidsgiveroppdragdetaljer = PersonObserver.FeriepengerUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(arbeidsgiveroppdrag).toString()
            val personoppdragdetaljer = PersonObserver.FeriepengerUtbetaltEvent.OppdragEventDetaljer.mapOppdrag(personoppdrag).toString()
            // Logging
            hendelse.info(
                """
                Nøkkelverdier om feriepengeberegning
                AktørId: $aktørId
                Arbeidsgiver: $orgnummer
                
                - ARBEIDSGIVER:
                Alle feriepengeutbetalinger fra Infotrygd (alle ytelser): $infotrygdHarUtbetaltTilArbeidsgiver (NB! Dette bruks ikke i beregning, bare til logging)
                Vår beregning av hva Infotrygd ville utbetalt i en verden uten Spleis: $hvaViHarBeregnetAtInfotrygdHarUtbetaltTilArbeidsgiver
                Men siden Spleis finnes:
                    Infotrygd skal betale:                      ${infotrygdFeriepengebeløpArbeidsgiver.finere}
                    Spleis skal betale:                         ${spleisFeriepengebeløpArbeidsgiver.finere}
                    Totalt feriepengebeløp:                     ${totaltFeriepengebeløpArbeidsgiver.finere}
                    Infotrygd-utbetalingen må korrigeres med:   ${differanseMellomTotalOgAlleredeUtbetaltAvInfotrygd.finere}
                
                - PERSON:
                Alle feriepengeutbetalinger fra Infotrygd (alle ytelser): $infotrygdHarUtbetaltTilPerson (NB! Dette bruks ikke i beregning, bare til logging)
                Vår beregning av hva Infotrygd ville utbetalt i en verden uten Spleis: $hvaViHarBeregnetAtInfotrygdHarUtbetaltTilPersonForDenneAktuelleArbeidsgiver
                Men siden Spleis finnes:
                    Infotrygd skal betale:                      ${infotrygdFeriepengebeløpPerson.finere}
                    Spleis skal betale:                         ${spleisFeriepengebeløpPerson.finere}
                    Totalt feriepengebeløp:                     ${totaltFeriepengebeløpPerson.finere}
                    Infotrygd-utbetalingen må korrigeres med:   ${differanseMellomTotalOgAlleredeUtbetaltAvInfotrygdTilPerson.finere}

                - GENERELT:         
                ${feriepengeberegner.feriepengedatoer().let { datoer -> "Datoer vi skal utbetale feriepenger for (${datoer.size}): ${datoer.grupperSammenhengendePerioder().finere}"}}
                
                - OPPDRAG:
                Skal sende arbeidsgiveroppdrag til OS: $sendArbeidsgiveroppdrag
                Differanse fra forrige sendte arbeidsgoiveroppdrag: ${forrigeSendteArbeidsgiverOppdrag?.totalbeløp()?.minus(arbeidsgiveroppdrag.totalbeløp())}
                Arbeidsgiveroppdrag: $arbeidsgiveroppdragdetaljer
                
                Skal sende personoppdrag til OS: $sendPersonoppdrag
                Differanse fra forrige sendte personoppdrag: ${forrigeSendtePersonOppdrag?.totalbeløp()?.minus(personoppdrag.totalbeløp())}
                Personoppdrag: $personoppdragdetaljer
                """
            )

            return Feriepengeutbetaling(
                feriepengeberegner = feriepengeberegner,
                infotrygdFeriepengebeløpPerson = infotrygdFeriepengebeløpPerson,
                infotrygdFeriepengebeløpArbeidsgiver = infotrygdFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpArbeidsgiver = spleisFeriepengebeløpArbeidsgiver,
                spleisFeriepengebeløpPerson = spleisFeriepengebeløpPerson,
                oppdrag = arbeidsgiveroppdrag,
                personoppdrag = personoppdrag,
                utbetalingId = UUID.randomUUID(),
                sendTilOppdrag = sendArbeidsgiveroppdrag,
                sendPersonoppdragTilOS = sendPersonoppdrag,
            )
        }
    }

    internal fun dto() = FeriepengeUtDto(
        feriepengeberegner = feriepengeberegner.dto(),
        infotrygdFeriepengebeløpPerson = infotrygdFeriepengebeløpPerson,
        infotrygdFeriepengebeløpArbeidsgiver = infotrygdFeriepengebeløpArbeidsgiver,
        spleisFeriepengebeløpPerson = spleisFeriepengebeløpPerson,
        spleisFeriepengebeløpArbeidsgiver = spleisFeriepengebeløpArbeidsgiver,
        oppdrag = this.oppdrag.dto(),
        personoppdrag = this.personoppdrag.dto(),
        utbetalingId = this.utbetalingId,
        sendTilOppdrag = this.sendTilOppdrag,
        sendPersonoppdragTilOS = this.sendPersonoppdragTilOS
    )
}

internal data class FeriepengeutbetalingView(
    val infotrygdFeriepengebeløpPerson: Double,
    val infotrygdFeriepengebeløpArbeidsgiver: Double,
    val spleisFeriepengebeløpArbeidsgiver: Double,
    val spleisFeriepengebeløpPerson: Double,
    val oppdrag: Oppdrag,
    val personoppdrag: Oppdrag
)