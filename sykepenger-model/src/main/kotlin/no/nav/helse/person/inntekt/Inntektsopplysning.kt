package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.økonomi.Inntekt

abstract class Inntektsopplysning protected constructor(
    protected val id: UUID,
    protected val hendelseId: UUID,
    protected val dato: LocalDate,
    protected val tidsstempel: LocalDateTime
) {
    internal abstract fun accept(visitor: InntektsopplysningVisitor)
    internal abstract fun omregnetÅrsinntekt(): Inntekt

    internal open fun overstyres(ny: Inntektsopplysning): Inntektsopplysning {
        if (ny.omregnetÅrsinntekt() == this.omregnetÅrsinntekt()) return this
        return ny.overstyrer(this)
    }

    protected open fun overstyrer(overstyrInntekt: Inntektsopplysning?) = this

    final override fun equals(other: Any?) = other is Inntektsopplysning && erSamme(other)

    final override fun hashCode(): Int {
        var result = dato.hashCode()
        result = 31 * result + tidsstempel.hashCode() * 31
        return result
    }

    protected abstract fun erSamme(other: Inntektsopplysning): Boolean

    internal open fun subsumerSykepengegrunnlag(subsumsjonObserver: SubsumsjonObserver, organisasjonsnummer: String, startdatoArbeidsforhold: LocalDate?) { }

    internal open fun subsumerArbeidsforhold(
        subsumsjonObserver: SubsumsjonObserver,
        organisasjonsnummer: String,
        forklaring: String,
        oppfylt: Boolean
    ): Inntektsopplysning? = null

    internal companion object {

        internal fun List<Inntektsopplysning>.markerFlereArbeidsgivere(aktivitetslogg: IAktivitetslogg) {
            if (distinctBy { it.dato }.size <= 1 && none { it is SkattSykepengegrunnlag || it is IkkeRapportert }) return
            aktivitetslogg.varsel(Varselkode.RV_VV_2)
        }
    }
}