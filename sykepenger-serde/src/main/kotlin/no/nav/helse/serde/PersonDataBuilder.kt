package no.nav.helse.serde

import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import no.nav.helse.dto.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagDto
import no.nav.helse.dto.AvsenderDto
import no.nav.helse.dto.BegrunnelseDto
import no.nav.helse.dto.BehandlingkildeDto
import no.nav.helse.dto.BehandlingtilstandDto
import no.nav.helse.dto.BeløpstidslinjeDto
import no.nav.helse.dto.DokumentsporingDto
import no.nav.helse.dto.DokumenttypeDto
import no.nav.helse.dto.EndringIRefusjonDto
import no.nav.helse.dto.EndringskodeDto
import no.nav.helse.dto.FagområdeDto
import no.nav.helse.dto.HendelseskildeDto
import no.nav.helse.dto.InfotrygdFerieperiodeDto
import no.nav.helse.dto.InntekttypeDto
import no.nav.helse.dto.KlassekodeDto
import no.nav.helse.dto.MaksdatobestemmelseDto
import no.nav.helse.dto.MedlemskapsvurderingDto
import no.nav.helse.dto.OppdragstatusDto
import no.nav.helse.dto.SatstypeDto
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.dto.SkatteopplysningDto
import no.nav.helse.dto.SykdomshistorikkElementDto
import no.nav.helse.dto.SykdomstidslinjeDagDto
import no.nav.helse.dto.SykdomstidslinjeDto
import no.nav.helse.dto.SykmeldingsperioderDto
import no.nav.helse.dto.UtbetalingTilstandDto
import no.nav.helse.dto.UtbetalingVurderingDto
import no.nav.helse.dto.UtbetalingtypeDto
import no.nav.helse.dto.UtbetaltDagDto
import no.nav.helse.dto.VedtaksperiodetilstandDto
import no.nav.helse.dto.serialisering.ArbeidsgiverInntektsopplysningUtDto
import no.nav.helse.dto.serialisering.ArbeidsgiverUtDto
import no.nav.helse.dto.serialisering.BehandlingUtDto
import no.nav.helse.dto.serialisering.BehandlingendringUtDto
import no.nav.helse.dto.serialisering.FeriepengeUtDto
import no.nav.helse.dto.serialisering.ForkastetVedtaksperiodeUtDto
import no.nav.helse.dto.serialisering.InfotrygdArbeidsgiverutbetalingsperiodeUtDto
import no.nav.helse.dto.serialisering.InfotrygdInntektsopplysningUtDto
import no.nav.helse.dto.serialisering.InfotrygdPersonutbetalingsperiodeUtDto
import no.nav.helse.dto.serialisering.InfotrygdhistorikkelementUtDto
import no.nav.helse.dto.serialisering.InntektsopplysningUtDto
import no.nav.helse.dto.serialisering.MaksdatoresultatUtDto
import no.nav.helse.dto.serialisering.OppdragUtDto
import no.nav.helse.dto.serialisering.OpptjeningUtDto
import no.nav.helse.dto.serialisering.PersonUtDto
import no.nav.helse.dto.serialisering.RefusjonUtDto
import no.nav.helse.dto.serialisering.RefusjonsopplysningUtDto
import no.nav.helse.dto.serialisering.SammenligningsgrunnlagUtDto
import no.nav.helse.dto.serialisering.InntektsgrunnlagUtDto
import no.nav.helse.dto.serialisering.UtbetalingUtDto
import no.nav.helse.dto.serialisering.UtbetalingsdagUtDto
import no.nav.helse.dto.serialisering.UtbetalingslinjeUtDto
import no.nav.helse.dto.serialisering.UtbetalingstidslinjeUtDto
import no.nav.helse.dto.serialisering.VedtaksperiodeUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagInnslagUtDto
import no.nav.helse.dto.serialisering.VilkårsgrunnlagUtDto
import no.nav.helse.serde.PersonData.ArbeidsgiverData.SykdomstidslinjeData.DagData
import no.nav.helse.serde.PersonData.ArbeidsgiverData.VedtaksperiodeData.TilstandTypeData
import no.nav.helse.serde.PersonData.UtbetalingstidslinjeData.UtbetalingsdagData
import no.nav.helse.serde.PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.SkatteopplysningData
import no.nav.helse.serde.mapping.JsonMedlemskapstatus

fun PersonData.tilSerialisertPerson(pretty: Boolean = false): SerialisertPerson {
    val node = serdeObjectMapper.valueToTree<ObjectNode>(this)
    return SerialisertPerson(if (pretty) node.toPrettyString() else node.toString())
}
fun PersonUtDto.tilPersonData() = PersonData(
    aktørId = this.aktørId,
    fødselsdato = this.alder.fødselsdato,
    fødselsnummer = this.fødselsnummer,
    opprettet = this.opprettet,
    arbeidsgivere = this.arbeidsgivere.map { it.tilPersonData() },
    infotrygdhistorikk = this.infotrygdhistorikk.elementer.map { it.tilPersonData() },
    vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk.historikk.map { it.tilPersonData() },
    minimumSykdomsgradVurdering = minimumSykdomsgradVurdering.perioder.map { PersonData.MinimumSykdomsgradVurderingPeriodeData(it.fom, it.tom) },
    dødsdato = this.alder.dødsdato,
    skjemaVersjon = SerialisertPerson.gjeldendeVersjon()
)

private fun ArbeidsgiverUtDto.tilPersonData() = PersonData.ArbeidsgiverData(
    id = this.id,
    organisasjonsnummer = this.organisasjonsnummer,
    inntektshistorikk = this.inntektshistorikk.historikk.map { it.tilPersonData() },
    sykdomshistorikk = this.sykdomshistorikk.elementer.map { it.tilPersonData() },
    sykmeldingsperioder = this.sykmeldingsperioder.tilPersonData(),
    vedtaksperioder = this.vedtaksperioder.map { it.tilPersonData() },
    forkastede = this.forkastede.map { it.tilPersonData() },
    utbetalinger = this.utbetalinger.map { it.tilPersonData() },
    feriepengeutbetalinger = this.feriepengeutbetalinger.map { it.tilPersonData() },
    refusjonshistorikk = this.refusjonshistorikk.refusjoner.map { it.tilPersonData() }
)

private fun InntektsopplysningUtDto.InntektsmeldingDto.tilPersonData() = PersonData.ArbeidsgiverData.InntektsmeldingData(
    id = this.id,
    dato = this.dato,
    hendelseId = this.hendelseId,
    beløp = this.beløp.månedligDouble.beløp,
    kilde = when (this.kilde) {
        InntektsopplysningUtDto.InntektsmeldingDto.KildeDto.Arbeidsgiver -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData.InntektsmeldingKildeDto.Arbeidsgiver
        InntektsopplysningUtDto.InntektsmeldingDto.KildeDto.AOrdningen -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData.InntektsmeldingKildeDto.AOrdningen
    },
    tidsstempel = this.tidsstempel
)

private fun SykdomshistorikkElementDto.tilPersonData() = PersonData.SykdomshistorikkData(
    id = this.id,
    tidsstempel = this.tidsstempel,
    hendelseId = this.hendelseId,
    hendelseSykdomstidslinje = this.hendelseSykdomstidslinje.tilPersonData(),
    beregnetSykdomstidslinje = this.beregnetSykdomstidslinje.tilPersonData(),
)
private fun SykdomstidslinjeDto.tilPersonData() = PersonData.ArbeidsgiverData.SykdomstidslinjeData(
    dager = dager.map { it.tilPersonData() }.forkortSykdomstidslinje(),
    låstePerioder = this.låstePerioder.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    periode = this.periode?.let { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) }
)

private fun List<DagData>.forkortSykdomstidslinje(): List<DagData> {
    return this.fold(emptyList()) { result, neste ->
        val slåttSammen = result.lastOrNull()?.utvideMed(neste) ?: return@fold result + neste
        result.dropLast(1) + slåttSammen
    }
}

private fun DagData.utvideMed(other: DagData): DagData? {
    if (!kanUtvidesMed(other)) return null
    val otherDato = checkNotNull(other.dato) { "dato må være satt" }
    if (this.dato != null) {
        return this.copy(dato = null, fom = dato, tom = otherDato)
    }
    return this.copy(tom = other.dato)
}

private fun DagData.kanUtvidesMed(other: DagData): Boolean {
    // alle verdier må være like (untatt datoene)
    val utenDatoer = { dag: DagData -> dag.copy(fom = null, tom = null, dato = LocalDate.EPOCH) }
    return utenDatoer(this) == utenDatoer(other) && (dato ?: tom!!).plusDays(1) == other.dato
}

private fun SykdomstidslinjeDagDto.tilPersonData() = when (this) {
    is SykdomstidslinjeDagDto.UkjentDagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.UKJENT_DAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.AndreYtelserDto -> DagData(
        type = when (this.ytelse) {
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.AAP -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_AAP
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Foreldrepenger -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_FORELDREPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Omsorgspenger -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_OMSORGSPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Pleiepenger -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_PLEIEPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Svangerskapspenger -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_SVANGERSKAPSPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Opplæringspenger -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_OPPLÆRINGSPENGER
            SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto.Dagpenger -> PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ANDRE_YTELSER_DAGPENGER
        },
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEID_IKKE_GJENOPPTATT_DAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidsdagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEIDSDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEIDSGIVERDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ArbeidsgiverdagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.ARBEIDSGIVERDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.FeriedagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.FERIEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ForeldetSykedagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.FORELDET_SYKEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.FriskHelgedagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.FRISK_HELGEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.PermisjonsdagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.PERMISJONSDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.ProblemDagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.PROBLEMDAG,
        kilde = this.kilde.tilPersonData(),
        grad = 0.0,
        other = this.other.tilPersonData(),
        melding = this.melding,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.SykHelgedagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.SYKEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.SykedagDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.SYKEDAG,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
    is SykdomstidslinjeDagDto.SykedagNavDto -> DagData(
        type = PersonData.ArbeidsgiverData.SykdomstidslinjeData.JsonDagType.SYKEDAG_NAV,
        kilde = this.kilde.tilPersonData(),
        grad = this.grad.prosent,
        other = null,
        melding = null,
        dato = dato,
        fom = null,
        tom = null
    )
}
private fun HendelseskildeDto.tilPersonData() = PersonData.ArbeidsgiverData.SykdomstidslinjeData.KildeData(
    type = this.type,
    id = this.meldingsreferanseId,
    tidsstempel = this.tidsstempel
)
private fun RefusjonUtDto.tilPersonData() = PersonData.ArbeidsgiverData.RefusjonData(
    meldingsreferanseId = this.meldingsreferanseId,
    førsteFraværsdag = this.førsteFraværsdag,
    arbeidsgiverperioder = this.arbeidsgiverperioder.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    beløp = this.beløp?.månedligDouble?.beløp,
    sisteRefusjonsdag = this.sisteRefusjonsdag,
    endringerIRefusjon = this.endringerIRefusjon.map { it.tilPersonData() },
    tidsstempel = this.tidsstempel
)
private fun EndringIRefusjonDto.tilPersonData() = PersonData.ArbeidsgiverData.RefusjonData.EndringIRefusjonData(
    beløp = this.beløp.beløp,
    endringsdato = this.endringsdato
)

private fun SykmeldingsperioderDto.tilPersonData() = perioder.map {
    PersonData.ArbeidsgiverData.SykmeldingsperiodeData(it.fom, it.tom)
}
private fun ForkastetVedtaksperiodeUtDto.tilPersonData() = PersonData.ArbeidsgiverData.ForkastetVedtaksperiodeData(
    vedtaksperiode = this.vedtaksperiode.tilPersonData()
)
private fun VedtaksperiodeUtDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData(
    id = id,
    tilstand = when (tilstand) {
        VedtaksperiodetilstandDto.AVSLUTTET -> TilstandTypeData.AVSLUTTET
        VedtaksperiodetilstandDto.AVSLUTTET_UTEN_UTBETALING -> TilstandTypeData.AVSLUTTET_UTEN_UTBETALING
        VedtaksperiodetilstandDto.AVVENTER_BLOKKERENDE_PERIODE -> TilstandTypeData.AVVENTER_BLOKKERENDE_PERIODE
        VedtaksperiodetilstandDto.AVVENTER_GODKJENNING -> TilstandTypeData.AVVENTER_GODKJENNING
        VedtaksperiodetilstandDto.AVVENTER_GODKJENNING_REVURDERING -> TilstandTypeData.AVVENTER_GODKJENNING_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_HISTORIKK -> TilstandTypeData.AVVENTER_HISTORIKK
        VedtaksperiodetilstandDto.AVVENTER_HISTORIKK_REVURDERING -> TilstandTypeData.AVVENTER_HISTORIKK_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_INFOTRYGDHISTORIKK -> TilstandTypeData.AVVENTER_INFOTRYGDHISTORIKK
        VedtaksperiodetilstandDto.AVVENTER_INNTEKTSMELDING -> TilstandTypeData.AVVENTER_INNTEKTSMELDING
        VedtaksperiodetilstandDto.AVVENTER_REVURDERING -> TilstandTypeData.AVVENTER_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_SIMULERING -> TilstandTypeData.AVVENTER_SIMULERING
        VedtaksperiodetilstandDto.AVVENTER_SIMULERING_REVURDERING -> TilstandTypeData.AVVENTER_SIMULERING_REVURDERING
        VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING -> TilstandTypeData.AVVENTER_VILKÅRSPRØVING
        VedtaksperiodetilstandDto.AVVENTER_VILKÅRSPRØVING_REVURDERING -> TilstandTypeData.AVVENTER_VILKÅRSPRØVING_REVURDERING
        VedtaksperiodetilstandDto.REVURDERING_FEILET -> TilstandTypeData.REVURDERING_FEILET
        VedtaksperiodetilstandDto.START -> TilstandTypeData.START
        VedtaksperiodetilstandDto.TIL_INFOTRYGD -> TilstandTypeData.TIL_INFOTRYGD
        VedtaksperiodetilstandDto.TIL_UTBETALING -> TilstandTypeData.TIL_UTBETALING
    },
    skjæringstidspunkt = skjæringstidspunkt,
    behandlinger = behandlinger.behandlinger.map { it.tilPersonData() },
    egenmeldingsperioder = egenmeldingsperioder.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    opprettet = opprettet,
    oppdatert = oppdatert
)
private fun BehandlingUtDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData(
    id = this.id,
    tilstand = when (this.tilstand) {
        BehandlingtilstandDto.ANNULLERT_PERIODE -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.ANNULLERT_PERIODE
        BehandlingtilstandDto.AVSLUTTET_UTEN_VEDTAK -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.AVSLUTTET_UTEN_VEDTAK
        BehandlingtilstandDto.BEREGNET -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.BEREGNET
        BehandlingtilstandDto.BEREGNET_OMGJØRING -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.BEREGNET_OMGJØRING
        BehandlingtilstandDto.BEREGNET_REVURDERING -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.BEREGNET_REVURDERING
        BehandlingtilstandDto.REVURDERT_VEDTAK_AVVIST -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.REVURDERT_VEDTAK_AVVIST
        BehandlingtilstandDto.TIL_INFOTRYGD -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.TIL_INFOTRYGD
        BehandlingtilstandDto.UBEREGNET -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.UBEREGNET
        BehandlingtilstandDto.UBEREGNET_OMGJØRING -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.UBEREGNET_OMGJØRING
        BehandlingtilstandDto.UBEREGNET_REVURDERING -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.UBEREGNET_REVURDERING
        BehandlingtilstandDto.VEDTAK_FATTET -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.VEDTAK_FATTET
        BehandlingtilstandDto.VEDTAK_IVERKSATT -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.TilstandData.VEDTAK_IVERKSATT
    },
    vedtakFattet = this.vedtakFattet,
    avsluttet = this.avsluttet,
    kilde = this.kilde.tilPersonData(),
    endringer = this.endringer.map { it.tilPersonData() },
)
private fun BehandlingkildeDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.KildeData(
    meldingsreferanseId = this.meldingsreferanseId,
    innsendt = this.innsendt,
    registrert = this.registert,
    avsender = this.avsender.tilPersonData()
)

private fun AvsenderDto.tilPersonData() = when (this) {
    AvsenderDto.ARBEIDSGIVER -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.AvsenderData.ARBEIDSGIVER
    AvsenderDto.SAKSBEHANDLER -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.AvsenderData.SAKSBEHANDLER
    AvsenderDto.SYKMELDT -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.AvsenderData.SYKMELDT
    AvsenderDto.SYSTEM -> PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.AvsenderData.SYSTEM
}
private fun BehandlingendringUtDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData.BehandlingData.EndringData(
    id = id,
    tidsstempel = tidsstempel,
    sykmeldingsperiodeFom = sykmeldingsperiode.fom,
    sykmeldingsperiodeTom = sykmeldingsperiode.tom,
    fom = periode.fom,
    tom = periode.tom,
    skjæringstidspunkt = skjæringstidspunkt,
    utbetalingId = utbetalingId,
    vilkårsgrunnlagId = vilkårsgrunnlagId,
    sykdomstidslinje = sykdomstidslinje.tilPersonData(),
    utbetalingstidslinje = utbetalingstidslinje.tilPersonData(),
    refusjonstidslinje = refusjonstidslinje.tilPersonData(),
    dokumentsporing = dokumentsporing.tilPersonData(),
    arbeidsgiverperioder = arbeidsgiverperioder.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    maksdatoresultat = maksdatoresultat.tilPersonData()
)
private fun MaksdatoresultatUtDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData.MaksdatoresultatData(
    vurdertTilOgMed = vurdertTilOgMed,
    bestemmelse = when (bestemmelse) {
        MaksdatobestemmelseDto.IKKE_VURDERT -> PersonData.ArbeidsgiverData.VedtaksperiodeData.MaksdatobestemmelseData.IKKE_VURDERT
        MaksdatobestemmelseDto.ORDINÆR_RETT -> PersonData.ArbeidsgiverData.VedtaksperiodeData.MaksdatobestemmelseData.ORDINÆR_RETT
        MaksdatobestemmelseDto.BEGRENSET_RETT -> PersonData.ArbeidsgiverData.VedtaksperiodeData.MaksdatobestemmelseData.BEGRENSET_RETT
        MaksdatobestemmelseDto.SYTTI_ÅR -> PersonData.ArbeidsgiverData.VedtaksperiodeData.MaksdatobestemmelseData.SYTTI_ÅR
    },
    startdatoTreårsvindu = startdatoTreårsvindu,
    startdatoSykepengerettighet = startdatoSykepengerettighet,
    forbrukteDager = forbrukteDager.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    oppholdsdager = oppholdsdager.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    avslåtteDager = avslåtteDager.map { PersonData.ArbeidsgiverData.PeriodeData(it.fom, it.tom) },
    maksdato = maksdato,
    gjenståendeDager = gjenståendeDager,
    grunnlag = grunnlag.tilPersonData()
)
private fun DokumentsporingDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentsporingData(
    dokumentId = this.id,
    dokumenttype = when (type) {
        DokumenttypeDto.InntektsmeldingDager -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.InntektsmeldingDager
        DokumenttypeDto.InntektsmeldingInntekt -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.InntektsmeldingInntekt
        DokumenttypeDto.InntektsmeldingRefusjon -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.InntektsmeldingRefusjon
        DokumenttypeDto.OverstyrArbeidsforhold -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrArbeidsforhold
        DokumenttypeDto.OverstyrArbeidsgiveropplysninger -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrArbeidsgiveropplysninger
        DokumenttypeDto.OverstyrInntekt -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrInntekt
        DokumenttypeDto.OverstyrRefusjon -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrRefusjon
        DokumenttypeDto.OverstyrTidslinje -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.OverstyrTidslinje
        DokumenttypeDto.SkjønnsmessigFastsettelse -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.SkjønnsmessigFastsettelse
        DokumenttypeDto.Sykmelding -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.Sykmelding
        DokumenttypeDto.Søknad -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.Søknad
        DokumenttypeDto.AndreYtelser -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.AndreYtelser
        DokumenttypeDto.System -> PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentTypeData.System
    }
)
private fun UtbetalingUtDto.tilPersonData() = PersonData.UtbetalingData(
    id = this.id,
    korrelasjonsId = this.korrelasjonsId,
    fom = this.periode.fom,
    tom = this.periode.tom,
    annulleringer = this.annulleringer,
    utbetalingstidslinje = this.utbetalingstidslinje.tilPersonData(),
    arbeidsgiverOppdrag = this.arbeidsgiverOppdrag.tilPersonData(),
    personOppdrag = this.personOppdrag.tilPersonData(),
    tidsstempel = this.tidsstempel,
    type = when (this.type) {
        UtbetalingtypeDto.ANNULLERING -> PersonData.UtbetalingData.UtbetalingtypeData.ANNULLERING
        UtbetalingtypeDto.ETTERUTBETALING -> PersonData.UtbetalingData.UtbetalingtypeData.ETTERUTBETALING
        UtbetalingtypeDto.FERIEPENGER -> PersonData.UtbetalingData.UtbetalingtypeData.FERIEPENGER
        UtbetalingtypeDto.REVURDERING -> PersonData.UtbetalingData.UtbetalingtypeData.REVURDERING
        UtbetalingtypeDto.UTBETALING -> PersonData.UtbetalingData.UtbetalingtypeData.UTBETALING
    },
    status = when (this.tilstand) {
        UtbetalingTilstandDto.ANNULLERT -> PersonData.UtbetalingData.UtbetalingstatusData.ANNULLERT
        UtbetalingTilstandDto.FORKASTET -> PersonData.UtbetalingData.UtbetalingstatusData.FORKASTET
        UtbetalingTilstandDto.GODKJENT -> PersonData.UtbetalingData.UtbetalingstatusData.GODKJENT
        UtbetalingTilstandDto.GODKJENT_UTEN_UTBETALING -> PersonData.UtbetalingData.UtbetalingstatusData.GODKJENT_UTEN_UTBETALING
        UtbetalingTilstandDto.IKKE_GODKJENT -> PersonData.UtbetalingData.UtbetalingstatusData.IKKE_GODKJENT
        UtbetalingTilstandDto.IKKE_UTBETALT -> PersonData.UtbetalingData.UtbetalingstatusData.IKKE_UTBETALT
        UtbetalingTilstandDto.NY -> PersonData.UtbetalingData.UtbetalingstatusData.NY
        UtbetalingTilstandDto.OVERFØRT -> PersonData.UtbetalingData.UtbetalingstatusData.OVERFØRT
        UtbetalingTilstandDto.UTBETALT -> PersonData.UtbetalingData.UtbetalingstatusData.UTBETALT
    },
    maksdato = this.maksdato,
    forbrukteSykedager = this.forbrukteSykedager,
    gjenståendeSykedager = this.gjenståendeSykedager,
    vurdering = this.vurdering?.tilPersonData(),
    overføringstidspunkt = overføringstidspunkt,
    avstemmingsnøkkel = avstemmingsnøkkel,
    avsluttet = avsluttet,
    oppdatert = oppdatert
)
private fun UtbetalingstidslinjeUtDto.tilPersonData() = PersonData.UtbetalingstidslinjeData(
    dager = this.dager.map { it.tilPersonData() }.forkortUtbetalingstidslinje()
)


private fun List<UtbetalingsdagData>.forkortUtbetalingstidslinje(): List<UtbetalingsdagData> {
    return this.fold(emptyList()) { result, neste ->
        val slåttSammen = result.lastOrNull()?.utvideMed(neste) ?: return@fold result + neste
        result.dropLast(1) + slåttSammen
    }
}

private fun UtbetalingsdagData.utvideMed(other: UtbetalingsdagData): UtbetalingsdagData? {
    if (!kanUtvidesMed(other)) return null
    val otherDato = checkNotNull(other.dato) { "dato må være satt" }
    if (this.dato != null) {
        return this.copy(dato = null, fom = dato, tom = otherDato)
    }
    return this.copy(tom = other.dato)
}

private fun UtbetalingsdagData.kanUtvidesMed(other: UtbetalingsdagData): Boolean {
    // alle verdier må være like (untatt datoene)
    val utenDatoer = { dag: UtbetalingsdagData -> dag.copy(fom = null, tom = null, dato = LocalDate.EPOCH) }
    return utenDatoer(this) == utenDatoer(other) && (dato ?: tom!!).plusDays(1) == other.dato
}

private fun UtbetalingsdagUtDto.tilPersonData() = UtbetalingsdagData(
    type = when (this) {
        is UtbetalingsdagUtDto.ArbeidsdagDto -> PersonData.UtbetalingstidslinjeData.TypeData.Arbeidsdag
        is UtbetalingsdagUtDto.ArbeidsgiverperiodeDagDto -> PersonData.UtbetalingstidslinjeData.TypeData.ArbeidsgiverperiodeDag
        is UtbetalingsdagUtDto.ArbeidsgiverperiodeDagNavDto -> PersonData.UtbetalingstidslinjeData.TypeData.ArbeidsgiverperiodedagNav
        is UtbetalingsdagUtDto.AvvistDagDto -> PersonData.UtbetalingstidslinjeData.TypeData.AvvistDag
        is UtbetalingsdagUtDto.ForeldetDagDto -> PersonData.UtbetalingstidslinjeData.TypeData.ForeldetDag
        is UtbetalingsdagUtDto.FridagDto -> PersonData.UtbetalingstidslinjeData.TypeData.Fridag
        is UtbetalingsdagUtDto.NavDagDto -> PersonData.UtbetalingstidslinjeData.TypeData.NavDag
        is UtbetalingsdagUtDto.NavHelgDagDto -> PersonData.UtbetalingstidslinjeData.TypeData.NavHelgDag
        is UtbetalingsdagUtDto.UkjentDagDto -> PersonData.UtbetalingstidslinjeData.TypeData.UkjentDag
    },
    aktuellDagsinntekt = this.økonomi.aktuellDagsinntekt.dagligDouble.beløp,
    beregningsgrunnlag = this.økonomi.beregningsgrunnlag.dagligDouble.beløp,
    dekningsgrunnlag = this.økonomi.dekningsgrunnlag.dagligDouble.beløp,
    grunnbeløpgrense = this.økonomi.grunnbeløpgrense?.årlig?.beløp,
    begrunnelser = when (this) {
        is UtbetalingsdagUtDto.AvvistDagDto -> this.begrunnelser.map { it.tilPersonData() }
        else -> null
    },
    grad = this.økonomi.grad.prosent,
    totalGrad = this.økonomi.totalGrad.prosent,
    arbeidsgiverRefusjonsbeløp = økonomi.arbeidsgiverRefusjonsbeløp.dagligDouble.beløp,
    arbeidsgiverbeløp = this.økonomi.arbeidsgiverbeløp?.dagligDouble?.beløp,
    personbeløp = this.økonomi.personbeløp?.dagligDouble?.beløp,
    er6GBegrenset = this.økonomi.er6GBegrenset,
    dato = this.dato,
    fom = null,
    tom = null
)

private fun BegrunnelseDto.tilPersonData() = when (this) {
    BegrunnelseDto.AndreYtelserAap -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserAap
    BegrunnelseDto.AndreYtelserDagpenger -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserDagpenger
    BegrunnelseDto.AndreYtelserForeldrepenger -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserForeldrepenger
    BegrunnelseDto.AndreYtelserOmsorgspenger -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserOmsorgspenger
    BegrunnelseDto.AndreYtelserOpplaringspenger -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserOpplaringspenger
    BegrunnelseDto.AndreYtelserPleiepenger -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserPleiepenger
    BegrunnelseDto.AndreYtelserSvangerskapspenger -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.AndreYtelserSvangerskapspenger
    BegrunnelseDto.EgenmeldingUtenforArbeidsgiverperiode -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.EgenmeldingUtenforArbeidsgiverperiode
    BegrunnelseDto.EtterDødsdato -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.EtterDødsdato
    BegrunnelseDto.ManglerMedlemskap -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.ManglerMedlemskap
    BegrunnelseDto.ManglerOpptjening -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.ManglerOpptjening
    BegrunnelseDto.MinimumInntekt -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.MinimumInntekt
    BegrunnelseDto.MinimumInntektOver67 -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.MinimumInntektOver67
    BegrunnelseDto.MinimumSykdomsgrad -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.MinimumSykdomsgrad
    BegrunnelseDto.NyVilkårsprøvingNødvendig -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.NyVilkårsprøvingNødvendig
    BegrunnelseDto.Over70 -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.Over70
    BegrunnelseDto.SykepengedagerOppbrukt -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.SykepengedagerOppbrukt
    BegrunnelseDto.SykepengedagerOppbruktOver67 -> PersonData.UtbetalingstidslinjeData.BegrunnelseData.SykepengedagerOppbruktOver67
}

private fun UtbetalingVurderingDto.tilPersonData() = PersonData.UtbetalingData.VurderingData(
    godkjent = godkjent,
    ident = ident,
    epost = epost,
    tidspunkt = tidspunkt,
    automatiskBehandling = automatiskBehandling
)
private fun OppdragUtDto.tilPersonData() = PersonData.OppdragData(
    mottaker = this.mottaker,
    fagområde = when (this.fagområde) {
        FagområdeDto.SP -> "SP"
        FagområdeDto.SPREF -> "SPREF"
    },
    linjer = this.linjer.map { it.tilPersonData() },
    fagsystemId = this.fagsystemId,
    endringskode = this.endringskode.tilPersonData(),
    tidsstempel = this.tidsstempel,
    nettoBeløp = this.nettoBeløp,
    avstemmingsnøkkel = this.avstemmingsnøkkel,
    status = when (this.status) {
        OppdragstatusDto.AKSEPTERT -> PersonData.OppdragData.OppdragstatusData.AKSEPTERT
        OppdragstatusDto.AKSEPTERT_MED_FEIL -> PersonData.OppdragData.OppdragstatusData.AKSEPTERT_MED_FEIL
        OppdragstatusDto.AVVIST -> PersonData.OppdragData.OppdragstatusData.AVVIST
        OppdragstatusDto.FEIL -> PersonData.OppdragData.OppdragstatusData.FEIL
        OppdragstatusDto.OVERFØRT -> PersonData.OppdragData.OppdragstatusData.OVERFØRT
        null -> null
    },
    overføringstidspunkt = this.overføringstidspunkt,
    erSimulert = this.erSimulert,
    simuleringsResultat = this.simuleringsResultat?.tilPersonData()
)
private fun EndringskodeDto.tilPersonData() = when (this) {
    EndringskodeDto.ENDR -> "ENDR"
    EndringskodeDto.NY -> "NY"
    EndringskodeDto.UEND -> "UEND"
}
private fun UtbetalingslinjeUtDto.tilPersonData() = PersonData.UtbetalingslinjeData(
    fom = this.fom,
    tom = this.tom,
    satstype = when (this.satstype) {
        SatstypeDto.Daglig -> "DAG"
        SatstypeDto.Engang -> "ENG"
    },
    sats = this.beløp,
    grad = this.grad,
    refFagsystemId = this.refFagsystemId,
    delytelseId = this.delytelseId,
    refDelytelseId = this.refDelytelseId,
    endringskode = this.endringskode.tilPersonData(),
    klassekode = this.klassekode.tilPersonData(),
    datoStatusFom = this.datoStatusFom
)
private fun KlassekodeDto.tilPersonData() = when (this) {
    KlassekodeDto.RefusjonFeriepengerIkkeOpplysningspliktig -> "SPREFAGFER-IOP"
    KlassekodeDto.RefusjonIkkeOpplysningspliktig -> "SPREFAG-IOP"
    KlassekodeDto.SykepengerArbeidstakerFeriepenger -> "SPATFER"
    KlassekodeDto.SykepengerArbeidstakerOrdinær -> "SPATORD"
}
private fun SimuleringResultatDto.tilPersonData() = PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData(
    totalbeløp = this.totalbeløp,
    perioder = this.perioder.map {
        PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.SimulertPeriode(
            fom = it.fom,
            tom = it.tom,

            utbetalinger = it.utbetalinger.map {
                PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.SimulertUtbetaling(
                    forfallsdato = it.forfallsdato,
                    utbetalesTil = PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Mottaker(
                        id = it.utbetalesTil.id,
                        navn = it.utbetalesTil.navn
                    ),
                    feilkonto = it.feilkonto,
                    detaljer = it.detaljer.map {
                        PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Detaljer(
                            fom = it.fom,
                            tom = it.tom,
                            konto = it.konto,
                            beløp = it.beløp,
                            klassekode = PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Klassekode(
                                kode = it.klassekode.kode,
                                beskrivelse = it.klassekode.beskrivelse
                            ),
                            uføregrad = it.uføregrad,
                            utbetalingstype = it.utbetalingstype,
                            tilbakeføring = it.tilbakeføring,
                            sats = PersonData.ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData.Sats(
                                sats = it.sats.sats,
                                antall = it.sats.antall,
                                type = it.sats.type
                            ),
                            refunderesOrgnummer = it.refunderesOrgnummer
                        )
                    }
                )
            }
        )
    }
)
private fun FeriepengeUtDto.tilPersonData() = PersonData.ArbeidsgiverData.FeriepengeutbetalingData(
    infotrygdFeriepengebeløpPerson = this.infotrygdFeriepengebeløpPerson,
    infotrygdFeriepengebeløpArbeidsgiver = this.infotrygdFeriepengebeløpArbeidsgiver,
    spleisFeriepengebeløpArbeidsgiver = this.spleisFeriepengebeløpArbeidsgiver,
    spleisFeriepengebeløpPerson = this.spleisFeriepengebeløpPerson,
    oppdrag = this.oppdrag.tilPersonData(),
    personoppdrag = this.personoppdrag.tilPersonData(),
    opptjeningsår = this.feriepengeberegner.opptjeningsår,
    utbetalteDager = this.feriepengeberegner.utbetalteDager.map { it.tilPersonData() },
    feriepengedager = this.feriepengeberegner.feriepengedager.map { it.tilPersonData() },
    utbetalingId = utbetalingId,
    sendTilOppdrag = sendTilOppdrag,
    sendPersonoppdragTilOS = sendPersonoppdragTilOS
)
private fun UtbetaltDagDto.tilPersonData() = PersonData.ArbeidsgiverData.FeriepengeutbetalingData.UtbetaltDagData(
    type = when (this) {
        is UtbetaltDagDto.InfotrygdArbeidsgiver -> "InfotrygdArbeidsgiverDag"
        is UtbetaltDagDto.InfotrygdPerson -> "InfotrygdPersonDag"
        is UtbetaltDagDto.SpleisArbeidsgiver -> "SpleisArbeidsgiverDag"
        is UtbetaltDagDto.SpleisPerson -> "SpleisPersonDag"
    },
    orgnummer = orgnummer,
    dato = dato,
    beløp = beløp
)
private fun InfotrygdhistorikkelementUtDto.tilPersonData() = PersonData.InfotrygdhistorikkElementData(
    id = this.id,
    tidsstempel = this.tidsstempel,
    hendelseId = this.hendelseId,
    ferieperioder = this.ferieperioder.map { it.tilPersonData() },
    arbeidsgiverutbetalingsperioder = this.arbeidsgiverutbetalingsperioder.map { it.tilPersonData() },
    personutbetalingsperioder = this.personutbetalingsperioder.map { it.tilPersonData() },
    inntekter = this.inntekter.map { it.tilPersonData() },
    arbeidskategorikoder = arbeidskategorikoder,
    oppdatert = oppdatert
)
private fun InfotrygdFerieperiodeDto.tilPersonData() = PersonData.InfotrygdhistorikkElementData.FerieperiodeData(
    fom = this.periode.fom,
    tom = this.periode.tom
)
private fun InfotrygdArbeidsgiverutbetalingsperiodeUtDto.tilPersonData() = PersonData.InfotrygdhistorikkElementData.ArbeidsgiverutbetalingsperiodeData(
    orgnr = this.orgnr,
    fom = this.periode.fom,
    tom = this.periode.tom,
    grad = this.grad.prosent,
    inntekt = this.inntekt.dagligInt.beløp
)
private fun InfotrygdPersonutbetalingsperiodeUtDto.tilPersonData() = PersonData.InfotrygdhistorikkElementData.PersonutbetalingsperiodeData(
    orgnr = this.orgnr,
    fom = this.periode.fom,
    tom = this.periode.tom,
    grad = this.grad.prosent,
    inntekt = this.inntekt.dagligInt.beløp
)
private fun InfotrygdInntektsopplysningUtDto.tilPersonData() = PersonData.InfotrygdhistorikkElementData.InntektsopplysningData(
    orgnr = this.orgnummer,
    sykepengerFom = this.sykepengerFom,
    inntekt = this.inntekt.månedligDouble.beløp,
    refusjonTilArbeidsgiver = refusjonTilArbeidsgiver,
    refusjonTom = refusjonTom,
    lagret = lagret
)
private fun VilkårsgrunnlagInnslagUtDto.tilPersonData() = PersonData.VilkårsgrunnlagInnslagData(
    id = this.id,
    opprettet = this.opprettet,
    vilkårsgrunnlag = this.vilkårsgrunnlag.map { it.tilPersonData() }
)
private fun VilkårsgrunnlagUtDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData(
    skjæringstidspunkt = this.skjæringstidspunkt,
    type = when (this) {
        is VilkårsgrunnlagUtDto.Infotrygd -> PersonData.VilkårsgrunnlagElementData.GrunnlagsdataType.Infotrygd
        is VilkårsgrunnlagUtDto.Spleis -> PersonData.VilkårsgrunnlagElementData.GrunnlagsdataType.Vilkårsprøving
    },
    inntektsgrunnlag = this.inntektsgrunnlag.tilPersonData(),
    opptjening = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> this.opptjening.tilPersonData()
        is VilkårsgrunnlagUtDto.Infotrygd -> null
    },
    medlemskapstatus = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> when (this.medlemskapstatus) {
            MedlemskapsvurderingDto.Ja -> JsonMedlemskapstatus.JA
            MedlemskapsvurderingDto.Nei -> JsonMedlemskapstatus.NEI
            MedlemskapsvurderingDto.UavklartMedBrukerspørsmål -> JsonMedlemskapstatus.UAVKLART_MED_BRUKERSPØRSMÅL
            MedlemskapsvurderingDto.VetIkke -> JsonMedlemskapstatus.VET_IKKE
        }
        else -> null
    },
    vurdertOk = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> this.vurdertOk
        else -> null
    },
    meldingsreferanseId = when (this) {
        is VilkårsgrunnlagUtDto.Spleis -> this.meldingsreferanseId
        else -> null
    },
    vilkårsgrunnlagId = this.vilkårsgrunnlagId
)

private fun OpptjeningUtDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData.OpptjeningData(
    opptjeningFom = this.opptjeningsperiode.fom,
    opptjeningTom = this.opptjeningsperiode.tom,
    arbeidsforhold = this.arbeidsforhold.map {
        PersonData.VilkårsgrunnlagElementData.OpptjeningData.ArbeidsgiverOpptjeningsgrunnlagData(
            orgnummer = it.orgnummer,
            ansattPerioder = it.ansattPerioder.map {
                PersonData.VilkårsgrunnlagElementData.OpptjeningData.ArbeidsgiverOpptjeningsgrunnlagData.ArbeidsforholdData(
                    ansattFom = it.ansattFom,
                    ansattTom = it.ansattTom,
                    deaktivert = it.deaktivert
                )
            }
        )
    }
)
private fun InntektsgrunnlagUtDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData.InntektsgrunnlagData(
    grunnbeløp = this.`6G`.årlig.beløp,
    arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilPersonData() },
    sammenligningsgrunnlag = this.sammenligningsgrunnlag.tilPersonData(),
    deaktiverteArbeidsforhold = this.deaktiverteArbeidsforhold.map { it.tilPersonData() },
    vurdertInfotrygd = this.vurdertInfotrygd
)

private fun ArbeidsgiverInntektsopplysningUtDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData(
    orgnummer = this.orgnummer,
    fom = this.gjelder.fom,
    tom = this.gjelder.tom,
    inntektsopplysning = this.inntektsopplysning.tilPersonData(),
    refusjonsopplysninger = this.refusjonsopplysninger.opplysninger.map {
        it.tilPersonData()
    }
)

private fun InntektsopplysningUtDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData(
    id = this.id,
    dato = this.dato,
    hendelseId = this.hendelseId,
    beløp = when (this) {
        is InntektsopplysningUtDto.SkattSykepengegrunnlagDto -> null
        is InntektsopplysningUtDto.IkkeRapportertDto -> null
        is InntektsopplysningUtDto.InfotrygdDto -> this.beløp.månedligDouble.beløp
        is InntektsopplysningUtDto.InntektsmeldingDto -> this.beløp.månedligDouble.beløp
        is InntektsopplysningUtDto.SaksbehandlerDto -> this.beløp.månedligDouble.beløp
        is InntektsopplysningUtDto.SkjønnsmessigFastsattDto -> this.beløp.månedligDouble.beløp
        is InntektsopplysningUtDto.InntektFraSøknadDto -> this.beløp.månedligDouble.beløp
    },
    kilde = when (this) {
        is InntektsopplysningUtDto.IkkeRapportertDto -> "IKKE_RAPPORTERT"
        is InntektsopplysningUtDto.InfotrygdDto -> "INFOTRYGD"
        is InntektsopplysningUtDto.InntektsmeldingDto -> "INNTEKTSMELDING"
        is InntektsopplysningUtDto.SaksbehandlerDto -> "SAKSBEHANDLER"
        is InntektsopplysningUtDto.SkattSykepengegrunnlagDto -> "SKATT_SYKEPENGEGRUNNLAG"
        is InntektsopplysningUtDto.SkjønnsmessigFastsattDto -> "SKJØNNSMESSIG_FASTSATT"
        is InntektsopplysningUtDto.InntektFraSøknadDto -> "INNTEKT_FRA_SØKNAD"
    },
    forklaring = when (this) {
        is InntektsopplysningUtDto.SaksbehandlerDto -> this.forklaring
        else -> null
    },
    subsumsjon = when (this) {
        is InntektsopplysningUtDto.SaksbehandlerDto -> this.subsumsjon?.let {
            PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData.SubsumsjonData(
                paragraf = it.paragraf,
                bokstav = it.bokstav,
                ledd = it.ledd
            )
        }
        else -> null
    },
    tidsstempel = this.tidsstempel,
    overstyrtInntektId = when (this) {
        is InntektsopplysningUtDto.SaksbehandlerDto -> this.overstyrtInntekt
        is InntektsopplysningUtDto.SkjønnsmessigFastsattDto -> this.overstyrtInntekt
        else -> null
    },
    skatteopplysninger = when (this) {
        is InntektsopplysningUtDto.SkattSykepengegrunnlagDto -> this.inntektsopplysninger.map { it.tilPersonDataSkattopplysning() }
        else -> null
    },
    inntektsmeldingkilde = when (this) {
        is InntektsopplysningUtDto.InntektsmeldingDto -> when (this.kilde) {
            InntektsopplysningUtDto.InntektsmeldingDto.KildeDto.Arbeidsgiver -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData.InntektsmeldingKildeDto.Arbeidsgiver
            InntektsopplysningUtDto.InntektsmeldingDto.KildeDto.AOrdningen -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.InntektsopplysningData.InntektsmeldingKildeDto.AOrdningen
        }
        else -> null
    }
)

private fun RefusjonsopplysningUtDto.tilPersonData() = PersonData.ArbeidsgiverData.RefusjonsopplysningData(
    meldingsreferanseId = this.meldingsreferanseId,
    fom = this.fom,
    tom = this.tom,
    beløp = this.beløp.månedligDouble.beløp
)

private fun SammenligningsgrunnlagUtDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData.SammenligningsgrunnlagData(
    sammenligningsgrunnlag = this.sammenligningsgrunnlag.årlig.beløp,
    arbeidsgiverInntektsopplysninger = this.arbeidsgiverInntektsopplysninger.map { it.tilPersonData() }
)

private fun ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagDto.tilPersonData() = PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData(
    orgnummer = this.orgnummer,
    skatteopplysninger = this.inntektsopplysninger.map { it.tilPersonData() }
)

private fun SkatteopplysningDto.tilPersonData() =
    PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData(
        hendelseId = this.hendelseId,
        beløp = this.beløp.beløp,
        måned = this.måned,
        type = when (this.type) {
            InntekttypeDto.LØNNSINNTEKT -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.LØNNSINNTEKT
            InntekttypeDto.NÆRINGSINNTEKT -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.NÆRINGSINNTEKT
            InntekttypeDto.PENSJON_ELLER_TRYGD -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.PENSJON_ELLER_TRYGD
            InntekttypeDto.YTELSE_FRA_OFFENTLIGE -> PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.SammenligningsgrunnlagInntektsopplysningData.InntekttypeData.YTELSE_FRA_OFFENTLIGE
        },
        fordel = fordel,
        beskrivelse = beskrivelse,
        tidsstempel = tidsstempel
    )
private fun SkatteopplysningDto.tilPersonDataSkattopplysning() = SkatteopplysningData(
    hendelseId = this.hendelseId,
    beløp = this.beløp.beløp,
    måned = this.måned,
    type = when (this.type) {
        InntekttypeDto.LØNNSINNTEKT -> SkatteopplysningData.InntekttypeData.LØNNSINNTEKT
        InntekttypeDto.NÆRINGSINNTEKT -> SkatteopplysningData.InntekttypeData.NÆRINGSINNTEKT
        InntekttypeDto.PENSJON_ELLER_TRYGD -> SkatteopplysningData.InntekttypeData.PENSJON_ELLER_TRYGD
        InntekttypeDto.YTELSE_FRA_OFFENTLIGE -> SkatteopplysningData.InntekttypeData.YTELSE_FRA_OFFENTLIGE
    },
    fordel = fordel,
    beskrivelse = beskrivelse,
    tidsstempel = tidsstempel
)

private fun BeløpstidslinjeDto.tilPersonData() = PersonData.BeløpstidslinjeData(
    perioder = this.perioder.map {
        PersonData.BeløpstidslinjeperiodeData(
            fom = it.fom,
            tom = it.tom,
            dagligBeløp = it.dagligBeløp,
            meldingsreferanseId = it.kilde.meldingsreferanseId,
            avsender = it.kilde.avsender.tilPersonData(),
            tidsstempel = it.kilde.tidsstempel
        )
    }
)