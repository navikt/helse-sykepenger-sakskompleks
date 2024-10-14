package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.BehandlingSubsumsjonslogg
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.refusjon.Refusjonsservitør

class OverstyrArbeidsgiveropplysninger(
    private val meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    internal val skjæringstidspunkt: LocalDate,
    private val arbeidsgiveropplysninger: List<ArbeidsgiverInntektsopplysning>,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
    private val opprettet: LocalDateTime,
    private val refusjonstidslinjer: Map<String, Pair<Beløpstidslinje, Boolean>>
) : PersonHendelse(meldingsreferanseId, fødselsnummer, aktørId, aktivitetslogg), OverstyrInntektsgrunnlag {
    override fun erRelevant(skjæringstidspunkt: LocalDate) = this.skjæringstidspunkt == skjæringstidspunkt

    override fun vilkårsprøvEtterNyInformasjonFraSaksbehandler(person: Person, jurist: BehandlingSubsumsjonslogg) {
        person.vilkårsprøvEtterNyInformasjonFraSaksbehandler(
            this,
            this.skjæringstidspunkt,
            jurist
        )
    }

    override fun innsendt() = opprettet
    override fun avsender() = Avsender.SAKSBEHANDLER

    internal fun overstyr(builder: Inntektsgrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer) {
        arbeidsgiveropplysninger.forEach { builder.leggTilInntekt(it) }
    }

    internal fun arbeidsgiveropplysningerKorrigert(person: Person, orgnummer: String, hendelseId: UUID) {
        if (arbeidsgiveropplysninger.any { it.gjelder(orgnummer) }) {
            person.arbeidsgiveropplysningerKorrigert(
                PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                    korrigertInntektsmeldingId = hendelseId,
                    korrigerendeInntektektsopplysningstype = SAKSBEHANDLER,
                    korrigerendeInntektsopplysningId = meldingsreferanseId
                )
            )
        }
    }

    internal fun refusjonstidslinje(organisasjonsnummer: String, skjæringstidspunkt: LocalDate, periode: Periode): Beløpstidslinje {
        if (this.skjæringstidspunkt != skjæringstidspunkt) return Beløpstidslinje()
        val (beløpstidslinje, strekkbar) = refusjonstidslinjer[organisasjonsnummer] ?: return Beløpstidslinje()
        return if (strekkbar) beløpstidslinje.strekkFrem(periode.endInclusive).subset(periode)
        else beløpstidslinje.subset(periode)
    }

    internal fun refusjonsservitør(førsteFraværsdager: Collection<LocalDate>, orgnummer: String): Refusjonsservitør? {
        val (beløpstidslinje, strekkbar) = refusjonstidslinjer[orgnummer] ?: return null
        return Refusjonsservitør.fra(
            førsteFraværsdager = førsteFraværsdager,
            refusjonstidslinje = beløpstidslinje,
            strekkbar = strekkbar
        )
    }
}
