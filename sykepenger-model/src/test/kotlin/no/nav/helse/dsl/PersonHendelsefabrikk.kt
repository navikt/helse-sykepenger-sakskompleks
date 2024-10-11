package no.nav.helse.dsl

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning.Companion.medSaksbehandlerinntekt
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning.Companion.medSkjønnsmessigFastsattInntekt
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning.Companion.refusjonstidslinjer
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.til
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.økonomi.Inntekt

internal class PersonHendelsefabrikk(
    private val aktørId: String,
    private val personidentifikator: Personidentifikator
) {
    internal fun lagDødsmelding(dødsdato: LocalDate) =
        Dødsmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            dødsdato = dødsdato
        )
    internal fun lagOverstyrArbeidsforhold(skjæringstidspunkt: LocalDate, vararg overstyrteArbeidsforhold: OverstyrArbeidsforhold.ArbeidsforholdOverstyrt) =
        OverstyrArbeidsforhold(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            overstyrteArbeidsforhold = overstyrteArbeidsforhold.toList(),
            opprettet = LocalDateTime.now()
        )
    internal fun lagPåminnelse() =
        PersonPåminnelse(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId
        )

    internal fun lagSkjønnsmessigFastsettelse(
        skjæringstidspunkt: LocalDate,
        arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>,
        meldingsreferanseId: UUID,
        tidsstempel: LocalDateTime
    ) =
        SkjønnsmessigFastsettelse(
            meldingsreferanseId = meldingsreferanseId,
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiveropplysninger = arbeidsgiveropplysninger.medSkjønnsmessigFastsattInntekt(meldingsreferanseId, skjæringstidspunkt),
            opprettet = tidsstempel
        )

    internal fun lagOverstyrArbeidsgiveropplysninger(skjæringstidspunkt: LocalDate, arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>, meldingsreferanseId: UUID, tidsstempel: LocalDateTime) =
        OverstyrArbeidsgiveropplysninger(
            meldingsreferanseId = meldingsreferanseId,
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiveropplysninger = arbeidsgiveropplysninger.medSaksbehandlerinntekt(meldingsreferanseId, skjæringstidspunkt),
            refusjonstidslinjer = arbeidsgiveropplysninger.refusjonstidslinjer(skjæringstidspunkt, meldingsreferanseId, tidsstempel),
            opprettet = tidsstempel
        )

    internal fun lagUtbetalingshistorikkForFeriepenger(opptjeningsår: Year) =
        UtbetalingshistorikkForFeriepenger(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            utbetalinger = emptyList(),
            feriepengehistorikk = emptyList(),
            arbeidskategorikoder = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(emptyList()),
            opptjeningsår = opptjeningsår,
            skalBeregnesManuelt = false
        )
}

internal class OverstyrtArbeidsgiveropplysning(
    private val orgnummer: String,
    private val inntekt: Inntekt,
    private val forklaring: String? = null,
    private val subsumsjon: Subsumsjon? = null,
    private val refusjonsopplysninger: List<Triple<LocalDate, LocalDate?, Inntekt>>? = null,
    private val gjelder: Periode? = null
) {
    private fun refusjonsopplysninger(førsteDag: LocalDate) =  refusjonsopplysninger ?: listOf(Triple(førsteDag, null, inntekt))
    internal companion object {
        private fun List<OverstyrtArbeidsgiveropplysning>.tilArbeidsgiverInntektsopplysning(meldingsreferanseId: UUID, skjæringstidspunkt: LocalDate, inntektsopplysning: (overstyrtArbeidsgiveropplysning: OverstyrtArbeidsgiveropplysning) -> Inntektsopplysning) =
            map {
                val gjelder = it.gjelder ?: (skjæringstidspunkt til LocalDate.MAX)
                ArbeidsgiverInntektsopplysning(
                    orgnummer = it.orgnummer,
                    gjelder = gjelder,
                    inntektsopplysning = inntektsopplysning(it),
                    refusjonsopplysninger = RefusjonsopplysningerBuilder().apply { it.refusjonsopplysninger(gjelder.start).forEach { (fom, tom, refusjonsbeløp) -> leggTil(Refusjonsopplysning(meldingsreferanseId, fom, tom, refusjonsbeløp), LocalDateTime.now()) } }.build()
                )
            }
        internal fun List<OverstyrtArbeidsgiveropplysning>.medSaksbehandlerinntekt(meldingsreferanseId: UUID, skjæringstidspunkt: LocalDate) = tilArbeidsgiverInntektsopplysning(meldingsreferanseId, skjæringstidspunkt) {
            checkNotNull(it.forklaring) { "Forklaring må settes på Saksbehandlerinntekt"}
            Saksbehandler(skjæringstidspunkt, meldingsreferanseId, it.inntekt, it.forklaring, it.subsumsjon, LocalDateTime.now())
        }
        internal fun List<OverstyrtArbeidsgiveropplysning>.medSkjønnsmessigFastsattInntekt(meldingsreferanseId: UUID, skjæringstidspunkt: LocalDate) = tilArbeidsgiverInntektsopplysning(meldingsreferanseId, skjæringstidspunkt) {
            check(it.forklaring == null) { "Skal ikke sette forklaring på Skjønnsmessig fastsatt inntekt" }
            check(it.subsumsjon == null) { "Skal ikke sette subsumsjon på Skjønssmessig fastsatt inntekt" }
            SkjønnsmessigFastsatt(skjæringstidspunkt, meldingsreferanseId, it.inntekt, LocalDateTime.now())
        }

        internal fun List<OverstyrtArbeidsgiveropplysning>.refusjonstidslinjer(skjæringstidspunkt: LocalDate, meldingsreferanseId: UUID, opprettet: LocalDateTime) = this.associateBy { it.orgnummer }.mapValues { (_, opplysning) ->
            val defaultRefusjonFom = opplysning.gjelder?.start ?: skjæringstidspunkt
            opplysning.refusjonsopplysninger(defaultRefusjonFom).fold(Beløpstidslinje()) { acc, (fom, tom, beløp) ->
                acc + Beløpstidslinje.fra(fom til (tom ?: fom), beløp, Kilde(meldingsreferanseId, SAKSBEHANDLER, opprettet))
            }
        }
    }
}