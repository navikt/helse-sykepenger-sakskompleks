package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.inntekt.Inntektsgrunnlag

class OverstyrArbeidsforhold(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    private val skjæringstidspunkt: LocalDate,
    private val overstyrteArbeidsforhold: List<ArbeidsforholdOverstyrt>,
    private val opprettet: LocalDateTime
) : PersonHendelse(meldingsreferanseId, fødselsnummer, aktørId, Aktivitetslogg()), OverstyrInntektsgrunnlag {

    override fun erRelevant(skjæringstidspunkt: LocalDate) = this.skjæringstidspunkt == skjæringstidspunkt

    override fun dokumentsporing() = Dokumentsporing.overstyrArbeidsforhold(meldingsreferanseId())

    override fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(person: Person, jurist: MaskinellJurist) {
        person.vilkårsprøvEtterNyInformasjonFraSaksbehandler(this, this.skjæringstidspunkt, jurist)
    }

    override fun innsendt() = opprettet

    override fun avsender() = SAKSBEHANDLER

    internal fun overstyr(inntektsgrunnlag: Inntektsgrunnlag, subsumsjonslogg: Subsumsjonslogg): Inntektsgrunnlag {
        return overstyrteArbeidsforhold.fold(inntektsgrunnlag) { acc, overstyring ->
            overstyring.overstyr(acc, subsumsjonslogg)
        }
    }

    internal fun overstyr(opptjening: Opptjening, subsumsjonslogg: Subsumsjonslogg): Opptjening {
        return overstyrteArbeidsforhold.fold(opptjening) { acc, overstyring ->
            overstyring.overstyr(acc, subsumsjonslogg)
        }
    }

    class ArbeidsforholdOverstyrt(
        internal val orgnummer: String,
        private val deaktivert: Boolean,
        private val forklaring: String
    ) {
        internal fun overstyr(inntektsgrunnlag: Inntektsgrunnlag, subsumsjonslogg: Subsumsjonslogg) = when (deaktivert) {
            true -> inntektsgrunnlag.deaktiver(orgnummer, forklaring, subsumsjonslogg)
            else -> inntektsgrunnlag.aktiver(orgnummer, forklaring, subsumsjonslogg)
        }

        internal fun overstyr(opptjening: Opptjening, subsumsjonslogg: Subsumsjonslogg) = when (deaktivert) {
            true -> opptjening.deaktiver(orgnummer, subsumsjonslogg)
            else -> opptjening.aktiver(orgnummer, subsumsjonslogg)
        }
    }
}
