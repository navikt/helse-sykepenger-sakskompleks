package no.nav.helse.hendelser

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Annullering
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Arbeidsforhold
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Arbeidsgiveropplysninger
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Arbeidsgiverperiode
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Grunnbeløpsregulering
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Infotrygd
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.KorrigertInntektsmeldingInntektsopplysninger
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.KorrigertSøknad
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.MinimumSykdomsgradVurdert
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.NyPeriode
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Reberegning
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.SkjønssmessigFastsettelse
import no.nav.helse.hendelser.Revurderingseventyr.RevurderingÅrsak.Sykdomstidslinje
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.OverstyringIgangsatt.VedtaksperiodeData
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.varsel
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RV_7

class Revurderingseventyr private constructor(
    private val hvorfor: RevurderingÅrsak,
    private val skjæringstidspunkt: LocalDate,
    private val periodeForEndring: Periode,
    val hendelse: Hendelse
) {

    internal companion object {
        fun nyPeriode(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periodeForEndring: Periode) = Revurderingseventyr(NyPeriode, skjæringstidspunkt, periodeForEndring, hendelse)
        fun arbeidsforhold(hendelse: Hendelse, skjæringstidspunkt: LocalDate) = Revurderingseventyr(Arbeidsforhold, skjæringstidspunkt, skjæringstidspunkt.somPeriode(), hendelse)
        fun korrigertSøknad(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periodeForEndring: Periode) = Revurderingseventyr(KorrigertSøknad, skjæringstidspunkt, periodeForEndring, hendelse)
        fun reberegning(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periodeForEndring: Periode) = Revurderingseventyr(Reberegning, skjæringstidspunkt, periodeForEndring, hendelse)
        fun sykdomstidslinje(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periodeForEndring: Periode) = Revurderingseventyr(Sykdomstidslinje, skjæringstidspunkt, periodeForEndring, hendelse)
        fun arbeidsgiveropplysninger(hendelse: Hendelse, skjæringstidspunkt: LocalDate, endringsdato: LocalDate) = Revurderingseventyr(Arbeidsgiveropplysninger, skjæringstidspunkt, endringsdato.somPeriode(), hendelse)
        fun skjønnsmessigFastsettelse(hendelse: Hendelse, skjæringstidspunkt: LocalDate, endringsdato: LocalDate) = Revurderingseventyr(SkjønssmessigFastsettelse, skjæringstidspunkt, endringsdato.somPeriode(), hendelse)
        fun arbeidsgiverperiode(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periodeForEndring: Periode) = Revurderingseventyr(Arbeidsgiverperiode, skjæringstidspunkt, periodeForEndring, hendelse)
        fun infotrygdendring(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periodeForEndring: Periode) = Revurderingseventyr(RevurderingÅrsak.Infotrygdendring, skjæringstidspunkt, periodeForEndring, hendelse)
        fun korrigertInntektsmeldingInntektsopplysninger(hendelse: Hendelse, skjæringstidspunkt: LocalDate, endringsdato: LocalDate) = Revurderingseventyr(KorrigertInntektsmeldingInntektsopplysninger, skjæringstidspunkt, endringsdato.somPeriode(), hendelse)
        fun refusjonsopplysninger(hendelse: Hendelse, skjæringstidspunkt: LocalDate, periode: Periode) = Revurderingseventyr(RevurderingÅrsak.Refusjonsopplysninger, skjæringstidspunkt, periode, hendelse)
        fun inntekt(hendelse: Hendelse, skjæringstidspunkt: LocalDate) = Revurderingseventyr(RevurderingÅrsak.Inntekt, skjæringstidspunkt, skjæringstidspunkt.somPeriode(), hendelse)
        fun grunnbeløpsregulering(hendelse: Hendelse, skjæringstidspunkt: LocalDate) = Revurderingseventyr(Grunnbeløpsregulering, skjæringstidspunkt, skjæringstidspunkt.somPeriode(), hendelse)
        fun annullering(hendelse: Hendelse, periode: Periode) = Revurderingseventyr(Annullering, periode.start, periode, hendelse)
        fun minimumSykdomsgradVurdert(hendelse: Hendelse, periode: Periode) = Revurderingseventyr(MinimumSykdomsgradVurdert, periode.start, periode, hendelse)
        fun infotrygd(hendelse: Hendelse, periode: Periode) = Revurderingseventyr(Infotrygd, periode.start, periode, hendelse)

        fun tidligsteEventyr(a: Revurderingseventyr?, b: Revurderingseventyr?) = when {
            b == null || (a != null && a.periodeForEndring.start <= b.periodeForEndring.start) -> a
            else -> b
        }

        fun List<Revurderingseventyr>.tidligsteEventyr(): Revurderingseventyr? {
            if (this.isEmpty()) return null
            return fold(first()) { champion, challenger ->
                tidligsteEventyr(champion, challenger) ?: champion
            }
        }
    }

    private val vedtaksperioder = mutableListOf<VedtaksperiodeData>()

    internal fun inngåSomRevurdering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg, periode: Periode) =
        inngå(vedtaksperiode, aktivitetslogg, TypeEndring.REVURDERING, periode)

    internal fun inngåSomEndring(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg, periode: Periode) =
        inngå(vedtaksperiode, aktivitetslogg, TypeEndring.ENDRING, periode)

    internal fun inngåVedSaksbehandlerendring(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg, periode: Periode) {
        if (hendelse.metadata.avsender != Avsender.SAKSBEHANDLER) return
        if (!periode.overlapperMed(periodeForEndring)) return
        inngåSomEndring(vedtaksperiode, aktivitetslogg, periode)
    }

    private fun inngå(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg, typeEndring: TypeEndring, periode: Periode) {
        hvorfor.dersomInngått(aktivitetslogg, vedtaksperioder.isEmpty())
        vedtaksperiode.inngåIRevurderingseventyret(vedtaksperioder, typeEndring.name)
    }

    internal fun ikkeRelevant(periode: Periode): Boolean {
        return periodeForEndring.starterEtter(periode)
    }

    internal fun sendOverstyringIgangsattEvent(person: Person) {
        if (vedtaksperioder.isEmpty()) return
        hvorfor.emitOverstyringIgangsattEvent(person, vedtaksperioder.toList(), skjæringstidspunkt, periodeForEndring, hendelse.metadata.meldingsreferanseId)
    }

    internal fun loggDersomKorrigerendeSøknad(aktivitetslogg: IAktivitetslogg, loggMelding: String) {
        if (hvorfor == KorrigertSøknad) {
            aktivitetslogg.info(loggMelding)
        }
    }

    private enum class TypeEndring {
        ENDRING,
        REVURDERING
    }

    private sealed interface RevurderingÅrsak {

        fun dersomInngått(aktivitetslogg: IAktivitetslogg, ingenAndrePåmeldt: Boolean) {}

        fun emitOverstyringIgangsattEvent(person: Person, vedtaksperioder: List<VedtaksperiodeData>, skjæringstidspunkt: LocalDate, periodeForEndring: Periode, meldingsreferanseId: UUID) {
            person.emitOverstyringIgangsattEvent(
                PersonObserver.OverstyringIgangsatt(
                    årsak = navn(),
                    berørtePerioder = vedtaksperioder,
                    skjæringstidspunkt = skjæringstidspunkt,
                    periodeForEndring = periodeForEndring,
                    meldingsreferanseId = meldingsreferanseId
                )
            )
        }

        fun navn(): String

        data object Arbeidsgiverperiode : RevurderingÅrsak {
            override fun navn() = "ARBEIDSGIVERPERIODE"
        }

        data object Infotrygdendring : RevurderingÅrsak {
            override fun navn(): String {
                return "INFOTRYGDENDRING"
            }
        }

        data object Sykdomstidslinje : RevurderingÅrsak {
            override fun navn() = "SYKDOMSTIDSLINJE"
        }

        data object Arbeidsgiveropplysninger : RevurderingÅrsak {
            override fun navn() = "ARBEIDSGIVEROPPLYSNINGER"
        }

        data object SkjønssmessigFastsettelse : RevurderingÅrsak {
            override fun navn() = "SKJØNNSMESSIG_FASTSETTELSE"
        }

        data object Arbeidsforhold : RevurderingÅrsak {
            override fun navn() = "ARBEIDSFORHOLD"
        }

        data object Grunnbeløpsregulering : RevurderingÅrsak {
            override fun navn() = "GRUNNBELØPSREGULERING"
        }

        data object Annullering : RevurderingÅrsak {
            override fun navn() = "ANNULLERING"
            override fun dersomInngått(aktivitetslogg: IAktivitetslogg, ingenAndrePåmeldt: Boolean) {
                aktivitetslogg.varsel(RV_RV_7)
            }
        }

        data object MinimumSykdomsgradVurdert : RevurderingÅrsak {
            override fun navn(): String = "MINIMUM_SYKDOMSGRAD_VURDERT"
        }

        data object Infotrygd : RevurderingÅrsak {
            override fun navn(): String = "INFOTRYGD"
        }

        data object Reberegning : RevurderingÅrsak {
            override fun emitOverstyringIgangsattEvent(
                person: Person,
                vedtaksperioder: List<VedtaksperiodeData>,
                skjæringstidspunkt: LocalDate,
                periodeForEndring: Periode,
                meldingsreferanseId: UUID
            ) { /* trenger ikke fortelle om en reberegning */
            }

            override fun navn() = "REBEREGNING"
        }

        data object KorrigertSøknad : RevurderingÅrsak {
            override fun navn() = "KORRIGERT_SØKNAD"
        }

        data object KorrigertInntektsmeldingInntektsopplysninger : RevurderingÅrsak {

            override fun dersomInngått(aktivitetslogg: IAktivitetslogg, ingenAndrePåmeldt: Boolean) {
                if (ingenAndrePåmeldt) aktivitetslogg.varsel(RV_IM_4, "Inngår i revurdering på grunn av korrigert inntektsmelding")
                aktivitetslogg.info("korrigert inntektsmelding trigget revurdering på grunn av inntektsopplysninger")
            }

            override fun navn() = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER"
        }

        data object NyPeriode : RevurderingÅrsak {
            override fun navn() = "NY_PERIODE"
        }

        data object Refusjonsopplysninger : RevurderingÅrsak {
            override fun navn() = "REFUSJONSOPPLYSNINGER"
        }

        data object Inntekt : RevurderingÅrsak {
            override fun navn() = "INNTEKT"
        }
    }
}
