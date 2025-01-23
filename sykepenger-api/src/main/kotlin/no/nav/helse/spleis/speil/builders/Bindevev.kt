package no.nav.helse.spleis.speil.builders

import java.time.LocalDate
import no.nav.helse.spleis.speil.dto.Arbeidsgiverinntekt
import no.nav.helse.spleis.speil.dto.Arbeidsgiverrefusjon
import no.nav.helse.spleis.speil.dto.Inntekt
import no.nav.helse.spleis.speil.dto.Inntektkilde
import no.nav.helse.spleis.speil.dto.Refusjonselement
import no.nav.helse.spleis.speil.dto.SkjønnsmessigFastsattDTO

internal data class INyInntektUnderveis(
    val arbeidsgiver: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val dagligbeløp: Double,
    val månedligBeløp: Double
)

internal data class IArbeidsgiverinntekt(
    val arbeidsgiver: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val omregnetÅrsinntekt: IOmregnetÅrsinntekt,
    val skjønnsmessigFastsatt: SkjønnsmessigFastsattDTO?,
    val deaktivert: Boolean
) {
    internal fun toDTO(): Arbeidsgiverinntekt {
        return Arbeidsgiverinntekt(
            organisasjonsnummer = arbeidsgiver,
            omregnetÅrsinntekt = omregnetÅrsinntekt.toDTO(),
            skjønnsmessigFastsatt = skjønnsmessigFastsatt,
            fom = fom,
            tom = tom.takeUnless { it == LocalDate.MAX },
            deaktivert = deaktivert
        )
    }

    internal fun erTilkommenInntekt(skjæringstidspunkt: LocalDate) =
        fom > skjæringstidspunkt
}

internal data class IArbeidsgiverrefusjon(
    val arbeidsgiver: String,
    val refusjonsopplysninger: List<Refusjonselement>
) {
    internal fun toDTO(): Arbeidsgiverrefusjon {
        return Arbeidsgiverrefusjon(
            arbeidsgiver = arbeidsgiver,
            refusjonsopplysninger = refusjonsopplysninger
        )
    }
}

internal data class IOmregnetÅrsinntekt(
    val kilde: IInntektkilde,
    val beløp: Double,
    val månedsbeløp: Double
) {
    internal fun toDTO(): Inntekt {
        return Inntekt(
            kilde = kilde.toDTO(),
            beløp = beløp,
            månedsbeløp = månedsbeløp
        )
    }
}

internal enum class IInntektkilde {
    Saksbehandler, Inntektsmelding, Infotrygd, AOrdningen, IkkeRapportert;

    internal fun toDTO() = when (this) {
        Saksbehandler -> Inntektkilde.Saksbehandler
        Inntektsmelding -> Inntektkilde.Inntektsmelding
        Infotrygd -> Inntektkilde.Infotrygd
        AOrdningen -> Inntektkilde.AOrdningen
        IkkeRapportert -> Inntektkilde.IkkeRapportert
    }
}



