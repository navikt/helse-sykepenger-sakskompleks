package no.nav.helse

import java.time.LocalDate
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.dsl.UNG_PERSON_FØDSELSDATO
import no.nav.helse.dsl.a1
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.til
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.inntekt.arbeidsgiverinntekt
import no.nav.helse.økonomi.Inntekt

internal val Inntekt.sykepengegrunnlag get() = inntektsgrunnlag(a1)

internal fun Inntekt.inntektsgrunnlag(orgnr: String) = inntektsgrunnlag(UNG_PERSON_FØDSELSDATO.alder, orgnr, 1.januar)
internal fun Inntekt.inntektsgrunnlag(alder: Alder) = inntektsgrunnlag(alder, a1, 1.januar)
internal fun Inntekt.inntektsgrunnlag(skjæringstidspunkt: LocalDate) =
    inntektsgrunnlag(UNG_PERSON_FØDSELSDATO.alder, a1, skjæringstidspunkt)

internal fun Inntekt.inntektsgrunnlag(alder: Alder, orgnr: String, skjæringstidspunkt: LocalDate, subsumsjonslogg: Subsumsjonslogg = Subsumsjonslogg.EmptyLog) =
    Inntektsgrunnlag(
        alder = alder,
        arbeidsgiverInntektsopplysninger = listOf(
            ArbeidsgiverInntektsopplysning(
                orgnummer = orgnr,
                gjelder = skjæringstidspunkt til LocalDate.MAX,
                inntektsopplysning = arbeidsgiverinntekt(skjæringstidspunkt, this),
                korrigertInntekt = null,
                skjønnsmessigFastsatt = null
            )
        ),
        skjæringstidspunkt = skjæringstidspunkt,
        subsumsjonslogg = subsumsjonslogg
    )

internal fun Inntekt.inntektsgrunnlag(orgnr: String, skjæringstidspunkt: LocalDate, virkningstidspunkt: LocalDate) =
    Inntektsgrunnlag.ferdigSykepengegrunnlag(
        alder = UNG_PERSON_FØDSELSDATO.alder,
        skjæringstidspunkt = skjæringstidspunkt,
        arbeidsgiverInntektsopplysninger = listOf(
            ArbeidsgiverInntektsopplysning(
                orgnummer = orgnr,
                gjelder = skjæringstidspunkt til LocalDate.MAX,
                inntektsopplysning = arbeidsgiverinntekt(skjæringstidspunkt, this),
                korrigertInntekt = null,
                skjønnsmessigFastsatt = null
            )
        ),
        deaktiverteArbeidsforhold = emptyList(),
        vurdertInfotrygd = false,
        `6G` = Grunnbeløp.`6G`.beløp(skjæringstidspunkt, virkningstidspunkt)
    )
