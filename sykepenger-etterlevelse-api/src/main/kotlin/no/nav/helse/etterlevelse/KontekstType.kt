package no.nav.helse.etterlevelse

enum class KontekstType {
    Fødselsnummer,
    Organisasjonsnummer,
    Vedtaksperiode,
    Sykmelding,
    Søknad,
    Inntektsmelding,
    OverstyrTidslinje,
    OverstyrInntekt,
    OverstyrRefusjon,
    OverstyrArbeidsgiveropplysninger,
    OverstyrArbeidsforhold,
    SkjønnsmessigFastsettelse,
    AndreYtelser,
    System
}

data class Subsumsjonskontekst(
    val type: KontekstType,
    val verdi: String
)