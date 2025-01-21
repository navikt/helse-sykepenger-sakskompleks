package no.nav.helse.inspectors

import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning

internal val ArbeidsgiverInntektsopplysning.inspektør get() = ArbeidsgiverInntektsopplysningInspektør(this)

internal class ArbeidsgiverInntektsopplysningInspektør(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning) {
    val orgnummer = arbeidsgiverInntektsopplysning.orgnummer
    val inntektsopplysning = arbeidsgiverInntektsopplysning.inntektsopplysning
    val korrigertInntekt = arbeidsgiverInntektsopplysning.korrigertInntekt

    val omregnetÅrsinntekt = arbeidsgiverInntektsopplysning.omregnetÅrsinntekt.beløp
    val fastsattÅrsinntekt = arbeidsgiverInntektsopplysning.fastsattÅrsinntekt

    val skjønnsmessigFastsatt = arbeidsgiverInntektsopplysning.skjønnsmessigFastsatt
    val gjelder = arbeidsgiverInntektsopplysning.gjelder
}
