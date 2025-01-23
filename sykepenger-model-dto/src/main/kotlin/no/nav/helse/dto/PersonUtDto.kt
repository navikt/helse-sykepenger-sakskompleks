package no.nav.helse.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.util.*

data class AlderDto(val fødselsdato: LocalDate, val dødsdato: LocalDate?)

data class SykmeldingsperioderDto(val perioder: List<PeriodeDto>)

data class SykdomshistorikkDto(val elementer: List<SykdomshistorikkElementDto>)
data class SykdomshistorikkElementDto(
    val id: UUID,
    val hendelseId: UUID?,
    val tidsstempel: LocalDateTime,
    val hendelseSykdomstidslinje: SykdomstidslinjeDto,
    val beregnetSykdomstidslinje: SykdomstidslinjeDto,
)

data class SykdomstidslinjeDto(
    val dager: List<SykdomstidslinjeDagDto>,
    val periode: PeriodeDto?,
    val låstePerioder: List<PeriodeDto>
)

sealed class SykdomstidslinjeDagDto {
    abstract val dato: LocalDate
    abstract val kilde: HendelseskildeDto

    data class UkjentDagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto) : SykdomstidslinjeDagDto()
    data class ArbeidsdagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto) : SykdomstidslinjeDagDto()
    data class ArbeidsgiverdagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val grad: ProsentdelDto) : SykdomstidslinjeDagDto()
    data class FeriedagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto) : SykdomstidslinjeDagDto()
    data class ArbeidIkkeGjenopptattDagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto) : SykdomstidslinjeDagDto()
    data class FriskHelgedagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto) : SykdomstidslinjeDagDto()
    data class ArbeidsgiverHelgedagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val grad: ProsentdelDto) : SykdomstidslinjeDagDto()
    data class SykedagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val grad: ProsentdelDto) : SykdomstidslinjeDagDto()
    data class SykHelgedagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val grad: ProsentdelDto) : SykdomstidslinjeDagDto()
    data class SykedagNavDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val grad: ProsentdelDto) : SykdomstidslinjeDagDto()
    data class ForeldetSykedagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val grad: ProsentdelDto) : SykdomstidslinjeDagDto()
    data class PermisjonsdagDto(override val dato: LocalDate, override val kilde: HendelseskildeDto) : SykdomstidslinjeDagDto()
    data class ProblemDagDto(
        override val dato: LocalDate,
        override val kilde: HendelseskildeDto,
        val other: HendelseskildeDto,
        val melding: String
    ) : SykdomstidslinjeDagDto()

    data class AndreYtelserDto(override val dato: LocalDate, override val kilde: HendelseskildeDto, val ytelse: YtelseDto) : SykdomstidslinjeDagDto() {
        enum class YtelseDto {
            Foreldrepenger, AAP, Omsorgspenger, Pleiepenger, Svangerskapspenger, Opplæringspenger, Dagpenger
        }
    }
}

data class HendelseskildeDto(
    val type: String,
    val meldingsreferanseId: UUID,
    val tidsstempel: LocalDateTime
)

sealed class VedtaksperiodetilstandDto {
    data object AVVENTER_HISTORIKK : VedtaksperiodetilstandDto()
    data object AVVENTER_GODKJENNING : VedtaksperiodetilstandDto()
    data object AVVENTER_SIMULERING : VedtaksperiodetilstandDto()
    data object TIL_UTBETALING : VedtaksperiodetilstandDto()
    data object TIL_INFOTRYGD : VedtaksperiodetilstandDto()
    data object AVSLUTTET : VedtaksperiodetilstandDto()
    data object AVSLUTTET_UTEN_UTBETALING : VedtaksperiodetilstandDto()
    data object REVURDERING_FEILET : VedtaksperiodetilstandDto()
    data object START : VedtaksperiodetilstandDto()
    data object AVVENTER_INFOTRYGDHISTORIKK : VedtaksperiodetilstandDto()
    data object AVVENTER_INNTEKTSMELDING : VedtaksperiodetilstandDto()
    data object AVVENTER_BLOKKERENDE_PERIODE : VedtaksperiodetilstandDto()
    data object AVVENTER_VILKÅRSPRØVING : VedtaksperiodetilstandDto()
    data object AVVENTER_REVURDERING : VedtaksperiodetilstandDto()
    data object AVVENTER_HISTORIKK_REVURDERING : VedtaksperiodetilstandDto()
    data object AVVENTER_VILKÅRSPRØVING_REVURDERING : VedtaksperiodetilstandDto()
    data object AVVENTER_SIMULERING_REVURDERING : VedtaksperiodetilstandDto()
    data object AVVENTER_GODKJENNING_REVURDERING : VedtaksperiodetilstandDto()
}

sealed class BehandlingtilstandDto {
    data object UBEREGNET : BehandlingtilstandDto()
    data object UBEREGNET_OMGJØRING : BehandlingtilstandDto()
    data object UBEREGNET_REVURDERING : BehandlingtilstandDto()
    data object BEREGNET : BehandlingtilstandDto()
    data object BEREGNET_OMGJØRING : BehandlingtilstandDto()
    data object BEREGNET_REVURDERING : BehandlingtilstandDto()
    data object VEDTAK_FATTET : BehandlingtilstandDto()
    data object REVURDERT_VEDTAK_AVVIST : BehandlingtilstandDto()
    data object VEDTAK_IVERKSATT : BehandlingtilstandDto()
    data object AVSLUTTET_UTEN_VEDTAK : BehandlingtilstandDto()
    data object ANNULLERT_PERIODE : BehandlingtilstandDto()
    data object TIL_INFOTRYGD : BehandlingtilstandDto()
}

data class DokumentsporingDto(
    val id: UUID,
    val type: DokumenttypeDto
)

sealed class DokumenttypeDto {
    data object Sykmelding : DokumenttypeDto()
    data object Søknad : DokumenttypeDto()
    data object InntektsmeldingInntekt : DokumenttypeDto()
    data object InntektsmeldingRefusjon : DokumenttypeDto()
    data object InntektsmeldingDager : DokumenttypeDto()
    data object InntektFraAOrdningen : DokumenttypeDto()
    data object OverstyrTidslinje : DokumenttypeDto()
    data object OverstyrInntekt : DokumenttypeDto()
    data object OverstyrRefusjon : DokumenttypeDto()
    data object OverstyrArbeidsgiveropplysninger : DokumenttypeDto()
    data object OverstyrArbeidsforhold : DokumenttypeDto()
    data object SkjønnsmessigFastsettelse : DokumenttypeDto()
    data object AndreYtelser : DokumenttypeDto()
}

data class BehandlingkildeDto(
    val meldingsreferanseId: UUID,
    val innsendt: LocalDateTime,
    val registert: LocalDateTime,
    val avsender: AvsenderDto
)

sealed class MedlemskapsvurderingDto {
    data object Ja : MedlemskapsvurderingDto()
    data object Nei : MedlemskapsvurderingDto()
    data object VetIkke : MedlemskapsvurderingDto()
    data object UavklartMedBrukerspørsmål : MedlemskapsvurderingDto()
}

data class ArbeidsgiverOpptjeningsgrunnlagDto(
    val orgnummer: String,
    val ansattPerioder: List<ArbeidsforholdDto>
)

data class ArbeidsforholdDto(
    val ansattFom: LocalDate,
    val ansattTom: LocalDate?,
    val deaktivert: Boolean
)

data class FeriepengeberegnerDto(
    val opptjeningsår: Year,
    val utbetalteDager: List<UtbetaltDagDto>,
    val feriepengedager: List<UtbetaltDagDto>
)

sealed class UtbetaltDagDto {
    abstract val orgnummer: String
    abstract val dato: LocalDate
    abstract val beløp: Int

    data class InfotrygdArbeidsgiver(
        override val orgnummer: String,
        override val dato: LocalDate,
        override val beløp: Int
    ) : UtbetaltDagDto()

    data class InfotrygdPerson(
        override val orgnummer: String,
        override val dato: LocalDate,
        override val beløp: Int
    ) : UtbetaltDagDto()

    data class SpleisArbeidsgiver(
        override val orgnummer: String,
        override val dato: LocalDate,
        override val beløp: Int
    ) : UtbetaltDagDto()

    data class SpleisPerson(
        override val orgnummer: String,
        override val dato: LocalDate,
        override val beløp: Int
    ) : UtbetaltDagDto()
}

data class InfotrygdFerieperiodeDto(val periode: PeriodeDto)
