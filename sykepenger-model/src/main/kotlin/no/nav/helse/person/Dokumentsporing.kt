package no.nav.helse.person

import java.util.Objects
import java.util.UUID
import no.nav.helse.dto.DokumentsporingDto
import no.nav.helse.dto.DokumenttypeDto
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Subsumsjonskontekst

class Dokumentsporing private constructor(val id: UUID, val dokumentType: DokumentType) {

    companion object {
        internal fun sykmelding(id: UUID) = Dokumentsporing(id, DokumentType.Sykmelding)
        internal fun søknad(id: UUID) = Dokumentsporing(id, DokumentType.Søknad)
        internal fun inntektsmeldingInntekt(id: UUID) = Dokumentsporing(id, DokumentType.InntektsmeldingInntekt)
        internal fun inntektsmeldingRefusjon(id: UUID) = Dokumentsporing(id, DokumentType.InntektsmeldingRefusjon)
        internal fun inntektsmeldingDager(id: UUID) = Dokumentsporing(id, DokumentType.InntektsmeldingDager)
        internal fun overstyrTidslinje(id: UUID) = Dokumentsporing(id, DokumentType.OverstyrTidslinje)
        internal fun overstyrInntekt(id: UUID) = Dokumentsporing(id, DokumentType.OverstyrInntekt)
        internal fun overstyrRefusjon(id: UUID) = Dokumentsporing(id, DokumentType.OverstyrRefusjon)
        internal fun overstyrArbeidsgiveropplysninger(id: UUID) = Dokumentsporing(id, DokumentType.OverstyrArbeidsgiveropplysninger)
        internal fun overstyrArbeidsforhold(id: UUID) = Dokumentsporing(id, DokumentType.OverstyrArbeidsforhold)
        internal fun skjønnsmessigFastsettelse(id: UUID) = Dokumentsporing(id, DokumentType.SkjønnsmessigFastsettelse)
        internal fun grunnbeløpendring(id: UUID) = Dokumentsporing(id, DokumentType.SkjønnsmessigFastsettelse) // TODO: bytte DokumentType
        internal fun andreYtelser(id: UUID) = Dokumentsporing(id, DokumentType.AndreYtelser)
        internal fun system(meldingsreferanseId: UUID) = Dokumentsporing(meldingsreferanseId, DokumentType.System)

        internal fun Iterable<Dokumentsporing>.ider() = map { it.id }.toSet()
        internal fun Iterable<Dokumentsporing>.søknadIder() = filter { it.dokumentType == DokumentType.Søknad }.map { it.id }.toSet()
        internal fun Iterable<Dokumentsporing>.sisteInntektsmeldingDagerId() = lastOrNull { it.dokumentType == DokumentType.InntektsmeldingDager }?.id
        internal fun Iterable<Dokumentsporing>.sisteInntektsmeldingInntektId() = lastOrNull { it.dokumentType == DokumentType.InntektsmeldingInntekt }?.id

        internal fun Iterable<Dokumentsporing>.tilSubsumsjonsformat() = map {
            Subsumsjonskontekst(
                type = when (it.dokumentType) {
                    DokumentType.Sykmelding -> KontekstType.Sykmelding
                    DokumentType.Søknad -> KontekstType.Søknad
                    DokumentType.InntektsmeldingDager -> KontekstType.Inntektsmelding
                    DokumentType.InntektsmeldingInntekt -> KontekstType.Inntektsmelding
                    DokumentType.InntektsmeldingRefusjon -> KontekstType.Inntektsmelding
                    DokumentType.OverstyrTidslinje -> KontekstType.OverstyrTidslinje
                    DokumentType.OverstyrInntekt -> KontekstType.OverstyrInntekt
                    DokumentType.OverstyrRefusjon -> KontekstType.OverstyrRefusjon
                    DokumentType.OverstyrArbeidsgiveropplysninger -> KontekstType.OverstyrArbeidsgiveropplysninger
                    DokumentType.OverstyrArbeidsforhold -> KontekstType.OverstyrArbeidsforhold
                    DokumentType.SkjønnsmessigFastsettelse -> KontekstType.SkjønnsmessigFastsettelse
                    DokumentType.AndreYtelser -> KontekstType.AndreYtelser
                    DokumentType.System -> KontekstType.System
                },
                verdi = it.id.toString()
            )
        }

        internal fun gjenopprett(dto: DokumentsporingDto): Dokumentsporing {
            return Dokumentsporing(
                id = dto.id,
                dokumentType = when (dto.type) {
                    DokumenttypeDto.InntektsmeldingDager -> DokumentType.InntektsmeldingDager
                    DokumenttypeDto.InntektsmeldingInntekt -> DokumentType.InntektsmeldingInntekt
                    DokumenttypeDto.InntektsmeldingRefusjon -> DokumentType.InntektsmeldingRefusjon
                    DokumenttypeDto.OverstyrArbeidsforhold -> DokumentType.OverstyrArbeidsforhold
                    DokumenttypeDto.OverstyrArbeidsgiveropplysninger -> DokumentType.OverstyrArbeidsgiveropplysninger
                    DokumenttypeDto.OverstyrInntekt -> DokumentType.OverstyrInntekt
                    DokumenttypeDto.OverstyrRefusjon -> DokumentType.OverstyrRefusjon
                    DokumenttypeDto.OverstyrTidslinje -> DokumentType.OverstyrTidslinje
                    DokumenttypeDto.SkjønnsmessigFastsettelse -> DokumentType.SkjønnsmessigFastsettelse
                    DokumenttypeDto.Sykmelding -> DokumentType.Sykmelding
                    DokumenttypeDto.Søknad -> DokumentType.Søknad
                    DokumenttypeDto.AndreYtelser -> DokumentType.AndreYtelser
                    DokumenttypeDto.System -> DokumentType.System
                }
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Dokumentsporing) return false
        if (other === this) return true
        return this.id == other.id && this.dokumentType == other.dokumentType
    }
    override fun hashCode(): Int {
        return Objects.hash(id, dokumentType)
    }
    override fun toString() = "$dokumentType ($id)"

    internal fun dto() = DokumentsporingDto(
        id = this.id,
        type = when (dokumentType) {
            DokumentType.Sykmelding -> DokumenttypeDto.Sykmelding
            DokumentType.Søknad -> DokumenttypeDto.Søknad
            DokumentType.InntektsmeldingInntekt -> DokumenttypeDto.InntektsmeldingInntekt
            DokumentType.InntektsmeldingRefusjon -> DokumenttypeDto.InntektsmeldingRefusjon
            DokumentType.InntektsmeldingDager -> DokumenttypeDto.InntektsmeldingDager
            DokumentType.OverstyrTidslinje -> DokumenttypeDto.OverstyrTidslinje
            DokumentType.OverstyrInntekt -> DokumenttypeDto.OverstyrInntekt
            DokumentType.OverstyrRefusjon -> DokumenttypeDto.OverstyrRefusjon
            DokumentType.OverstyrArbeidsgiveropplysninger -> DokumenttypeDto.OverstyrArbeidsgiveropplysninger
            DokumentType.OverstyrArbeidsforhold -> DokumenttypeDto.OverstyrArbeidsforhold
            DokumentType.SkjønnsmessigFastsettelse -> DokumenttypeDto.SkjønnsmessigFastsettelse
            DokumentType.AndreYtelser -> DokumenttypeDto.AndreYtelser
            DokumentType.System -> DokumenttypeDto.System
        }
    )
}

