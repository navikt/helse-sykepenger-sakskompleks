package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import no.nav.helse.dto.deserialisering.InntektsopplysningInnDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.INNTEKTSMELDING
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_7
import no.nav.helse.yearMonth
import no.nav.helse.økonomi.Inntekt

class Inntektsmelding internal constructor(
    id: UUID,
    dato: LocalDate,
    hendelseId: UUID,
    beløp: Inntekt,
    tidsstempel: LocalDateTime,
    override val kilde: Inntektskilde
) : Inntektsopplysning(id, hendelseId, dato, beløp, tidsstempel, kilde) {
    internal constructor(dato: LocalDate, hendelseId: UUID, beløp: Inntekt, kilde: Inntektskilde = Inntektskilde.Arbeidsgiver, tidsstempel: LocalDateTime = LocalDateTime.now()) : this(UUID.randomUUID(), dato, hendelseId, beløp, tidsstempel, kilde)

    override fun gjenbrukbarInntekt(beløp: Inntekt?) = beløp?.let { Inntektsmelding(dato, hendelseId, it, kilde, tidsstempel) }?: this

    internal fun view() = InntektsmeldingView(
        id = id,
        dato = dato,
        hendelseId = hendelseId,
        beløp = beløp,
        tidsstempel = tidsstempel
    )

    override fun blirOverstyrtAv(ny: Inntektsopplysning): Inntektsopplysning {
        return ny.overstyrer(this)
    }

    override fun overstyrer(gammel: Saksbehandler) = this

    override fun overstyrer(gammel: SkjønnsmessigFastsatt) =
        if (erOmregnetÅrsinntektEndret(this, gammel)) this
        else gammel.overstyrer(this)

    internal fun avklarSykepengegrunnlag(skatt: SkatteopplysningSykepengegrunnlag): Inntektsopplysning {
        if (skatt.dato.yearMonth < this.dato.yearMonth) return skatt
        return this
    }

    internal fun kanLagres(other: Inntektsmelding) = this.hendelseId != other.hendelseId || this.dato != other.dato

    override fun erSamme(other: Inntektsopplysning): Boolean {
        return other is Inntektsmelding && this.dato == other.dato && other.beløp == this.beløp
    }
    override fun arbeidsgiveropplysningerKorrigert(
        person: Person,
        inntektsmelding: no.nav.helse.hendelser.Inntektsmelding
    ) {
        person.arbeidsgiveropplysningerKorrigert(
            PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                korrigertInntektsmeldingId = hendelseId,
                korrigerendeInntektektsopplysningstype = INNTEKTSMELDING,
                korrigerendeInntektsopplysningId = inntektsmelding.metadata.meldingsreferanseId
            )
        )
    }
    override fun arbeidsgiveropplysningerKorrigert(
        person: Person,
        orgnummer: String,
        saksbehandlerOverstyring: OverstyrArbeidsgiveropplysninger
    ) {
        saksbehandlerOverstyring.arbeidsgiveropplysningerKorrigert(person, orgnummer, hendelseId)
    }

    internal fun kopierTidsnærOpplysning(
        nyDato: LocalDate,
        aktivitetslogg: IAktivitetslogg,
        nyArbeidsgiverperiode: Boolean,
        inntektshistorikk: Inntektshistorikk
    ) {
        if (nyDato == this.dato) return
        val dagerMellom = ChronoUnit.DAYS.between(this.dato, nyDato)
        if (dagerMellom >= 60) {
            aktivitetslogg.info("Det er $dagerMellom dager mellom forrige inntektdato (${this.dato}) og ny inntektdato ($nyDato), dette utløser varsel om gjenbruk.")
            aktivitetslogg.varsel(RV_IV_7)
        } else if (nyArbeidsgiverperiode) {
            aktivitetslogg.info("Det er ny arbeidsgiverperiode, og dette utløser varsel om gjenbruk. Forrige inntektdato var ${this.dato} og ny inntektdato er $nyDato")
            aktivitetslogg.varsel(RV_IV_7)
        }
        inntektshistorikk.leggTil(Inntektsmelding(nyDato, hendelseId, beløp, kilde, tidsstempel))
        aktivitetslogg.info("Kopierte inntekt som lå lagret på ${this.dato} til $nyDato")
    }

    override fun dto() =
        InntektsopplysningUtDto.InntektsmeldingDto(
            id = id,
            hendelseId = hendelseId,
            dato = dato,
            beløp = beløp.dto(),
            tidsstempel = tidsstempel,
            kilde = kilde.dto()
        )

    internal companion object {
        internal fun gjenopprett(dto: InntektsopplysningInnDto.InntektsmeldingDto): Inntektsmelding {
            return Inntektsmelding(
                id = dto.id,
                dato = dto.dato,
                hendelseId = dto.hendelseId,
                beløp = Inntekt.gjenopprett(dto.beløp),
                tidsstempel = dto.tidsstempel,
                kilde = Inntektskilde.gjenopprett(dto.kilde)
            )
        }

        internal fun List<Inntektsmelding>.finnInntektsmeldingForSkjæringstidspunkt(skjæringstidspunkt: LocalDate, førsteFraværsdag: LocalDate?): Inntektsmelding? {
            val inntektsmeldinger = this.filter { it.dato == skjæringstidspunkt || it.dato == førsteFraværsdag }
            return inntektsmeldinger.maxByOrNull { inntektsmelding -> inntektsmelding.tidsstempel }
        }

        internal fun List<Inntektsmelding>.avklarSykepengegrunnlag(
            skjæringstidspunkt: LocalDate,
            førsteFraværsdag: LocalDate?,
            skatteopplysning: SkatteopplysningerForSykepengegrunnlag?
        ): Inntektsopplysning? {
            val inntektsmelding = finnInntektsmeldingForSkjæringstidspunkt(skjæringstidspunkt, førsteFraværsdag)
            val skatt = skatteopplysning?.avklarSomSykepengegrunnlag(skjæringstidspunkt) ?: return inntektsmelding
            return inntektsmelding?.avklarSykepengegrunnlag(skatt) ?: skatt
        }
    }
}

internal data class InntektsmeldingView(
    val id: UUID,
    val dato: LocalDate,
    val hendelseId: UUID,
    val beløp: Inntekt,
    val tidsstempel: LocalDateTime
)
