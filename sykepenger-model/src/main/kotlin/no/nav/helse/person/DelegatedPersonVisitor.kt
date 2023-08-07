package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Utbetalingsperiode
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag
import no.nav.helse.person.inntekt.IkkeRapportert
import no.nav.helse.person.inntekt.Infotrygd
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.inntekt.Skatteopplysning
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Fagområde
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling
import no.nav.helse.utbetalingslinjer.Klassekode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Satstype
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinjeberegning
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosent
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi

internal class DelegatedPersonVisitor(private val delegateeFun: () -> PersonVisitor) : PersonVisitor {
    private val delegatee get() = delegateeFun()

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        delegatee.preVisitAktivitetslogg(aktivitetslogg)
    }

    override fun visitInfo(id: UUID, kontekster: List<SpesifikkKontekst>, aktivitet: Aktivitet.Info, melding: String, tidsstempel: String) {
        delegatee.visitInfo(id, kontekster, aktivitet, melding, tidsstempel)
    }

    override fun visitVarsel(id: UUID, kontekster: List<SpesifikkKontekst>, aktivitet: Aktivitet.Varsel, kode: Varselkode?, melding: String, tidsstempel: String) {
        delegatee.visitVarsel(id, kontekster, aktivitet, kode, melding, tidsstempel)
    }

    override fun preVisitUtbetalingstidslinjeberegninger(beregninger: List<Utbetalingstidslinjeberegning>) {
        delegatee.preVisitUtbetalingstidslinjeberegninger(beregninger)
    }

    override fun postVisitUtbetalingstidslinjeberegninger(beregninger: List<Utbetalingstidslinjeberegning>) {
        delegatee.postVisitUtbetalingstidslinjeberegninger(beregninger)
    }

    override fun postVisitGrunnlagsdata(
        skjæringstidspunkt: LocalDate,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
        sykepengegrunnlag: Sykepengegrunnlag,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
        vurdertOk: Boolean,
        meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID
    ) {
        delegatee.postVisitGrunnlagsdata(
            skjæringstidspunkt,
            grunnlagsdata,
            sykepengegrunnlag,
            medlemskapstatus,
            vurdertOk,
            meldingsreferanseId,
            vilkårsgrunnlagId
        )
    }

    override fun postVisitInfotrygdVilkårsgrunnlag(
        infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID
    ) {
        delegatee.postVisitInfotrygdVilkårsgrunnlag(infotrygdVilkårsgrunnlag, skjæringstidspunkt, sykepengegrunnlag, vilkårsgrunnlagId)
    }

    override fun preVisitSykepengegrunnlag(
        sykepengegrunnlag1: Sykepengegrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Inntekt,
        avviksprosent: Prosent,
        totalOmregnetÅrsinntekt: Inntekt,
        beregningsgrunnlag: Inntekt,
        `6G`: Inntekt,
        begrensning: Sykepengegrunnlag.Begrensning,
        vurdertInfotrygd: Boolean,
        minsteinntekt: Inntekt,
        oppfyllerMinsteinntektskrav: Boolean,
        tilstand: Sykepengegrunnlag.Tilstand
    ) {
        delegatee.preVisitSykepengegrunnlag(
            sykepengegrunnlag1,
            skjæringstidspunkt,
            sykepengegrunnlag,
            avviksprosent,
            totalOmregnetÅrsinntekt,
            beregningsgrunnlag,
            `6G`,
            begrensning,
            vurdertInfotrygd,
            minsteinntekt,
            oppfyllerMinsteinntektskrav,
            tilstand,
        )
    }

    override fun postVisitSykepengegrunnlag(
        sykepengegrunnlag1: Sykepengegrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Inntekt,
        avviksprosent: Prosent,
        totalOmregnetÅrsinntekt: Inntekt,
        beregningsgrunnlag: Inntekt,
        `6G`: Inntekt,
        begrensning: Sykepengegrunnlag.Begrensning,
        vurdertInfotrygd: Boolean,
        minsteinntekt: Inntekt,
        oppfyllerMinsteinntektskrav: Boolean,
        tilstand: Sykepengegrunnlag.Tilstand
    ) {
        delegatee.postVisitSykepengegrunnlag(
            sykepengegrunnlag1,
            skjæringstidspunkt,
            sykepengegrunnlag,
            avviksprosent,
            totalOmregnetÅrsinntekt,
            beregningsgrunnlag,
            `6G`,
            begrensning,
            vurdertInfotrygd,
            minsteinntekt,
            oppfyllerMinsteinntektskrav,
            tilstand
        )
    }

    override fun preVisitSammenligningsgrunnlag(sammenligningsgrunnlag1: Sammenligningsgrunnlag, sammenligningsgrunnlag: Inntekt) {
        delegatee.preVisitSammenligningsgrunnlag(sammenligningsgrunnlag1, sammenligningsgrunnlag)
    }

    override fun postVisitSammenligningsgrunnlag(sammenligningsgrunnlag1: Sammenligningsgrunnlag, sammenligningsgrunnlag: Inntekt) {
        delegatee.postVisitSammenligningsgrunnlag(sammenligningsgrunnlag1, sammenligningsgrunnlag)
    }

    override fun preVisitArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
        arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag,
        orgnummer: String,
        rapportertInntekt: Inntekt
    ) {
        delegatee.preVisitArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
            arbeidsgiverInntektsopplysning,
            orgnummer,
            rapportertInntekt
        )
    }
    override fun postVisitArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
        arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag,
        orgnummer: String,
        rapportertInntekt: Inntekt
    ) {
        delegatee.postVisitArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(
            arbeidsgiverInntektsopplysning,
            orgnummer,
            rapportertInntekt
        )
    }

    override fun preVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag>) {
        delegatee.preVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger)
    }

    override fun postVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag>) {
        delegatee.postVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger)
    }

    override fun preVisitOpptjening(
        opptjening: Opptjening,
        arbeidsforhold: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag>,
        opptjeningsperiode: Periode
    ) {
        delegatee.preVisitOpptjening(opptjening, arbeidsforhold, opptjeningsperiode)
    }

    override fun postVisitOpptjening(opptjening: Opptjening, arbeidsforhold: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag>, opptjeningsperiode: Periode) {
        delegatee.postVisitOpptjening(opptjening, arbeidsforhold, opptjeningsperiode)
    }

    override fun preVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer: String, ansattPerioder: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold>) {
        delegatee.preVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer, ansattPerioder)
    }

    override fun visitArbeidsforhold(ansattFom: LocalDate, ansattTom: LocalDate?, deaktivert: Boolean) {
        delegatee.visitArbeidsforhold(ansattFom, ansattTom, deaktivert)
    }

    override fun postVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer: String, ansattPerioder: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold>) {
        delegatee.postVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer, ansattPerioder)
    }

    override fun preVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
        delegatee.preVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger)
    }

    override fun preVisitArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning, orgnummer: String) {
        delegatee.preVisitArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysning, orgnummer)
    }

    override fun postVisitArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning, orgnummer: String) {
        delegatee.postVisitArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysning, orgnummer)
    }

    override fun preVisitRefusjonsopplysninger(refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger) {
        delegatee.preVisitRefusjonsopplysninger(refusjonsopplysninger)
    }

    override fun postVisitRefusjonsopplysninger(refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger) {
        delegatee.postVisitRefusjonsopplysninger(refusjonsopplysninger)
    }

    override fun visitRefusjonsopplysning(meldingsreferanseId: UUID, fom: LocalDate, tom: LocalDate?, beløp: Inntekt) {
        delegatee.visitRefusjonsopplysning(meldingsreferanseId, fom, tom, beløp)
    }

    override fun postVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
        delegatee.postVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger)
    }

    override fun preVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
        delegatee.preVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger)
    }

    override fun postVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {
        delegatee.postVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger)
    }

    override fun preVisitUtbetalingstidslinjeberegning(
        id: UUID,
        tidsstempel: LocalDateTime,
        organisasjonsnummer: String,
        sykdomshistorikkElementId: UUID,
        vilkårsgrunnlagHistorikkInnslagId: UUID
    ) {
        delegatee.preVisitUtbetalingstidslinjeberegning(
            id,
            tidsstempel,
            organisasjonsnummer,
            sykdomshistorikkElementId,
            vilkårsgrunnlagHistorikkInnslagId
        )
    }

    override fun postVisitUtbetalingstidslinjeberegning(
        id: UUID,
        tidsstempel: LocalDateTime,
        organisasjonsnummer: String,
        sykdomshistorikkElementId: UUID,
        vilkårsgrunnlagHistorikkInnslagId: UUID
    ) {
        delegatee.postVisitUtbetalingstidslinjeberegning(
            id,
            tidsstempel,
            organisasjonsnummer,
            sykdomshistorikkElementId,
            vilkårsgrunnlagHistorikkInnslagId
        )
    }

    override fun visitBehov(
        id: UUID,
        kontekster: List<SpesifikkKontekst>,
        aktivitet: Aktivitet.Behov,
        type: Aktivitet.Behov.Behovtype,
        melding: String,
        detaljer: Map<String, Any?>,
        tidsstempel: String
    ) {
        delegatee.visitBehov(id, kontekster, aktivitet, type, melding, detaljer, tidsstempel)
    }

    override fun visitFunksjonellFeil(id: UUID, kontekster: List<SpesifikkKontekst>, aktivitet: Aktivitet.FunksjonellFeil, melding: String, tidsstempel: String) {
        delegatee.visitFunksjonellFeil(id, kontekster, aktivitet, melding, tidsstempel)
    }

    override fun visitLogiskFeil(id: UUID, kontekster: List<SpesifikkKontekst>, aktivitet: Aktivitet.LogiskFeil, melding: String, tidsstempel: String) {
        delegatee.visitLogiskFeil(id, kontekster, aktivitet, melding, tidsstempel)
    }

    override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        delegatee.postVisitAktivitetslogg(aktivitetslogg)
    }

    override fun preVisitPerson(
        person: Person,
        opprettet: LocalDateTime,
        aktørId: String,
        personidentifikator: Personidentifikator,
        vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
    ) {
        delegatee.preVisitPerson(person, opprettet, aktørId, personidentifikator, vilkårsgrunnlagHistorikk)
    }

    override fun visitAlder(alder: Alder, fødselsdato: LocalDate, dødsdato: LocalDate?) {
        delegatee.visitAlder(alder, fødselsdato, dødsdato)
    }

    override fun visitPersonAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        delegatee.visitPersonAktivitetslogg(aktivitetslogg)
    }

    override fun preVisitArbeidsgivere() {
        delegatee.preVisitArbeidsgivere()
    }

    override fun postVisitArbeidsgivere() {
        delegatee.postVisitArbeidsgivere()
    }

    override fun preVisitVilkårsgrunnlagHistorikk() {
        delegatee.preVisitVilkårsgrunnlagHistorikk()
    }

    override fun preVisitInnslag(innslag: VilkårsgrunnlagHistorikk.Innslag, id: UUID, opprettet: LocalDateTime) {
        delegatee.preVisitInnslag(innslag, id, opprettet)
    }

    override fun preVisitGrunnlagsdata(
        skjæringstidspunkt: LocalDate,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
        sykepengegrunnlag: Sykepengegrunnlag,
        opptjening: Opptjening,
        vurdertOk: Boolean,
        meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus
    ) {
        delegatee.preVisitGrunnlagsdata(
            skjæringstidspunkt,
            grunnlagsdata,
            sykepengegrunnlag,
            opptjening,
            vurdertOk,
            meldingsreferanseId,
            vilkårsgrunnlagId,
            medlemskapstatus
        )
    }

    override fun preVisitInfotrygdVilkårsgrunnlag(
        infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID
    ) {
        delegatee.preVisitInfotrygdVilkårsgrunnlag(infotrygdVilkårsgrunnlag, skjæringstidspunkt, sykepengegrunnlag, vilkårsgrunnlagId)
    }

    override fun postVisitInnslag(innslag: VilkårsgrunnlagHistorikk.Innslag, id: UUID, opprettet: LocalDateTime) {
        delegatee.postVisitInnslag(innslag, id, opprettet)
    }

    override fun postVisitVilkårsgrunnlagHistorikk() {
        delegatee.postVisitVilkårsgrunnlagHistorikk()
    }

    override fun preVisitInfotrygdhistorikk() {
        delegatee.preVisitInfotrygdhistorikk()
    }

    override fun preVisitInfotrygdhistorikkElement(
        id: UUID,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        hendelseId: UUID?
    ) {
        delegatee.preVisitInfotrygdhistorikkElement(id, tidsstempel, oppdatert, hendelseId)
    }

    override fun preVisitInfotrygdhistorikkPerioder() {
        delegatee.preVisitInfotrygdhistorikkPerioder()
    }

    override fun preVisitInfotrygdhistorikkInntektsopplysninger() {
        delegatee.preVisitInfotrygdhistorikkInntektsopplysninger()
    }

    override fun visitInfotrygdhistorikkInntektsopplysning(
        orgnr: String,
        sykepengerFom: LocalDate,
        inntekt: Inntekt,
        refusjonTilArbeidsgiver: Boolean,
        refusjonTom: LocalDate?,
        lagret: LocalDateTime?
    ) {
        delegatee.visitInfotrygdhistorikkInntektsopplysning(orgnr, sykepengerFom, inntekt, refusjonTilArbeidsgiver, refusjonTom, lagret)
    }

    override fun postVisitInfotrygdhistorikkInntektsopplysninger() {
        delegatee.postVisitInfotrygdhistorikkInntektsopplysninger()
    }

    override fun visitInfotrygdhistorikkArbeidskategorikoder(arbeidskategorikoder: Map<String, LocalDate>) {
        delegatee.visitInfotrygdhistorikkArbeidskategorikoder(arbeidskategorikoder)
    }

    override fun visitInfotrygdhistorikkFerieperiode(periode: Friperiode, fom: LocalDate, tom: LocalDate) {
        delegatee.visitInfotrygdhistorikkFerieperiode(periode, fom, tom)
    }

    override fun visitInfotrygdhistorikkPersonUtbetalingsperiode(
        periode: Utbetalingsperiode,
        orgnr: String,
        fom: LocalDate,
        tom: LocalDate,
        grad: Prosentdel,
        inntekt: Inntekt
    ) {
        delegatee.visitInfotrygdhistorikkPersonUtbetalingsperiode(periode, orgnr, fom, tom, grad, inntekt)
    }

    override fun visitInfotrygdhistorikkArbeidsgiverUtbetalingsperiode(
        periode: Utbetalingsperiode,
        orgnr: String,
        fom: LocalDate,
        tom: LocalDate,
        grad: Prosentdel,
        inntekt: Inntekt
    ) {
        delegatee.visitInfotrygdhistorikkArbeidsgiverUtbetalingsperiode(periode, orgnr, fom, tom, grad, inntekt)
    }

    override fun postVisitInfotrygdhistorikkPerioder() {
        delegatee.postVisitInfotrygdhistorikkPerioder()
    }

    override fun postVisitInfotrygdhistorikkElement(
        id: UUID,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        hendelseId: UUID?
    ) {
        delegatee.postVisitInfotrygdhistorikkElement(id, tidsstempel, oppdatert, hendelseId)
    }

    override fun postVisitInfotrygdhistorikk() {
        delegatee.postVisitInfotrygdhistorikk()
    }

    override fun postVisitPerson(
        person: Person,
        opprettet: LocalDateTime,
        aktørId: String,
        personidentifikator: Personidentifikator,
        vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
    ) {
        delegatee.postVisitPerson(person, opprettet, aktørId, personidentifikator, vilkårsgrunnlagHistorikk)
    }

    override fun preVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver, id: UUID, organisasjonsnummer: String) {
        delegatee.preVisitArbeidsgiver(arbeidsgiver, id, organisasjonsnummer)
    }

    override fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
        delegatee.preVisitUtbetalinger(utbetalinger)
    }

    override fun postVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
        delegatee.postVisitUtbetalinger(utbetalinger)
    }

    override fun preVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {
        delegatee.preVisitPerioder(vedtaksperioder)
    }

    override fun postVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {
        delegatee.postVisitPerioder(vedtaksperioder)
    }

    override fun preVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {
        delegatee.preVisitForkastedePerioder(vedtaksperioder)
    }

    override fun preVisitForkastetPeriode(vedtaksperiode: Vedtaksperiode) {
        delegatee.preVisitForkastetPeriode(vedtaksperiode)
    }

    override fun postVisitForkastetPeriode(vedtaksperiode: Vedtaksperiode) {
        delegatee.postVisitForkastetPeriode(vedtaksperiode)
    }

    override fun postVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {
        delegatee.postVisitForkastedePerioder(vedtaksperioder)
    }

    override fun preVisitInntektsmeldinginfoHistorikk(inntektsmeldingInfoHistorikk: InntektsmeldingInfoHistorikk) {
        delegatee.preVisitInntektsmeldinginfoHistorikk(inntektsmeldingInfoHistorikk)
    }

    override fun preVisitInntektsmeldinginfoElement(dato: LocalDate, elementer: List<InntektsmeldingInfo>) {
        delegatee.preVisitInntektsmeldinginfoElement(dato, elementer)
    }

    override fun visitInntektsmeldinginfo(id: UUID, arbeidsforholdId: String?) {
        delegatee.visitInntektsmeldinginfo(id, arbeidsforholdId)
    }

    override fun postVisitInntektsmeldinginfoElement(dato: LocalDate, elementer: List<InntektsmeldingInfo>) {
        delegatee.postVisitInntektsmeldinginfoElement(dato, elementer)
    }

    override fun postVisitInntektsmeldinginfoHistorikk(inntektsmeldingInfoHistorikk: InntektsmeldingInfoHistorikk) {
        delegatee.postVisitInntektsmeldinginfoHistorikk(inntektsmeldingInfoHistorikk)
    }

    override fun postVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver, id: UUID, organisasjonsnummer: String) {
        delegatee.postVisitArbeidsgiver(arbeidsgiver, id, organisasjonsnummer)
    }

    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: () -> LocalDate,
        skjæringstidspunktFraInfotrygd: LocalDate?,
        forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
        hendelseIder: Set<Dokumentsporing>,
        inntektsmeldingInfo: InntektsmeldingInfo?
    ) {
        delegatee.preVisitVedtaksperiode(
            vedtaksperiode,
            id,
            tilstand,
            opprettet,
            oppdatert,
            periode,
            opprinneligPeriode,
            skjæringstidspunkt,
            skjæringstidspunktFraInfotrygd,
            forlengelseFraInfotrygd,
            hendelseIder,
            inntektsmeldingInfo
        )
    }

    override fun preVisitFeriepengeutbetalinger(feriepengeutbetalinger: List<Feriepengeutbetaling>) {
        delegatee.preVisitFeriepengeutbetalinger(feriepengeutbetalinger)
    }

    override fun postVisitFeriepengeutbetalinger(feriepengeutbetalinger: List<Feriepengeutbetaling>) {
        delegatee.postVisitFeriepengeutbetalinger(feriepengeutbetalinger)
    }

    override fun preVisitFeriepengeutbetaling(
        feriepengeutbetaling: Feriepengeutbetaling,
        infotrygdFeriepengebeløpPerson: Double,
        infotrygdFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpPerson: Double,
        overføringstidspunkt: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        utbetalingId: UUID,
        sendTilOppdrag: Boolean,
        sendPersonoppdragTilOS: Boolean
    ) {
        delegatee.preVisitFeriepengeutbetaling(
            feriepengeutbetaling,
            infotrygdFeriepengebeløpPerson,
            infotrygdFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpPerson,
            overføringstidspunkt,
            avstemmingsnøkkel,
            utbetalingId,
            sendTilOppdrag,
            sendPersonoppdragTilOS
        )
    }

    override fun preVisitFeriepengerArbeidsgiveroppdrag() {
        delegatee.preVisitFeriepengerArbeidsgiveroppdrag()
    }

    override fun preVisitFeriepengerPersonoppdrag() {
        delegatee.preVisitFeriepengerPersonoppdrag()
    }

    override fun postVisitFeriepengeutbetaling(
        feriepengeutbetaling: Feriepengeutbetaling,
        infotrygdFeriepengebeløpPerson: Double,
        infotrygdFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpPerson: Double,
        overføringstidspunkt: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        utbetalingId: UUID,
        sendTilOppdrag: Boolean,
        sendPersonoppdragTilOS: Boolean
    ) {
        delegatee.postVisitFeriepengeutbetaling(
            feriepengeutbetaling,
            infotrygdFeriepengebeløpPerson,
            infotrygdFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpArbeidsgiver,
            spleisFeriepengebeløpPerson,
            overføringstidspunkt,
            avstemmingsnøkkel,
            utbetalingId,
            sendTilOppdrag,
            sendPersonoppdragTilOS
        )
    }

    override fun preVisitFeriepengeberegner(
        feriepengeberegner: Feriepengeberegner,
        feriepengedager: List<Feriepengeberegner.UtbetaltDag>,
        opptjeningsår: Year,
        utbetalteDager: List<Feriepengeberegner.UtbetaltDag>
    ) {
        delegatee.preVisitFeriepengeberegner(feriepengeberegner, feriepengedager, opptjeningsår, utbetalteDager)
    }

    override fun postVisitFeriepengeberegner(
        feriepengeberegner: Feriepengeberegner,
        feriepengedager: List<Feriepengeberegner.UtbetaltDag>,
        opptjeningsår: Year,
        utbetalteDager: List<Feriepengeberegner.UtbetaltDag>
    ) {
        delegatee.postVisitFeriepengeberegner(feriepengeberegner, feriepengedager, opptjeningsår, utbetalteDager)
    }

    override fun preVisitUtbetaleDager() {
        delegatee.preVisitUtbetaleDager()
    }

    override fun postVisitUtbetaleDager() {
        delegatee.postVisitUtbetaleDager()
    }

    override fun preVisitFeriepengedager() {
        delegatee.preVisitFeriepengedager()
    }

    override fun postVisitFeriepengedager() {
        delegatee.postVisitFeriepengedager()
    }

    override fun visitInfotrygdArbeidsgiverDag(
        infotrygdArbeidsgiver: Feriepengeberegner.UtbetaltDag.InfotrygdArbeidsgiver,
        orgnummer: String,
        dato: LocalDate,
        beløp: Int
    ) {
        delegatee.visitInfotrygdArbeidsgiverDag(infotrygdArbeidsgiver, orgnummer, dato, beløp)
    }

    override fun visitInfotrygdPersonDag(infotrygdPerson: Feriepengeberegner.UtbetaltDag.InfotrygdPerson, orgnummer: String, dato: LocalDate, beløp: Int) {
        delegatee.visitInfotrygdPersonDag(infotrygdPerson, orgnummer, dato, beløp)
    }

    override fun visitSpleisArbeidsgiverDag(
        spleisArbeidsgiver: Feriepengeberegner.UtbetaltDag.SpleisArbeidsgiver,
        orgnummer: String,
        dato: LocalDate,
        beløp: Int
    ) {
        delegatee.visitSpleisArbeidsgiverDag(spleisArbeidsgiver, orgnummer, dato, beløp)
    }

    override fun visitSpleisPersonDag(
        spleisPerson: Feriepengeberegner.UtbetaltDag.SpleisPerson,
        orgnummer: String,
        dato: LocalDate,
        beløp: Int
    ) {
        delegatee.visitSpleisPersonDag(spleisPerson, orgnummer, dato, beløp)
    }

    override fun postVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: () -> LocalDate,
        skjæringstidspunktFraInfotrygd: LocalDate?,
        forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
        hendelseIder: Set<Dokumentsporing>,
        inntektsmeldingInfo: InntektsmeldingInfo?
    ) {
        delegatee.postVisitVedtaksperiode(
            vedtaksperiode,
            id,
            tilstand,
            opprettet,
            oppdatert,
            periode,
            opprinneligPeriode,
            skjæringstidspunkt,
            skjæringstidspunktFraInfotrygd,
            forlengelseFraInfotrygd,
            hendelseIder,
            inntektsmeldingInfo
        )
    }

    override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje, gjeldendePeriode: Periode?) {
        delegatee.preVisitUtbetalingstidslinje(tidslinje, gjeldendePeriode)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.NavDag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.Arbeidsdag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.Fridag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.AvvistDag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.ForeldetDag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun visit(dag: Utbetalingsdag.UkjentDag, dato: LocalDate, økonomi: Økonomi) {
        delegatee.visit(dag, dato, økonomi)
    }

    override fun postVisitUtbetalingstidslinje() {
        delegatee.postVisitUtbetalingstidslinje()
    }

    override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        delegatee.preVisitSykdomshistorikk(sykdomshistorikk)
    }

    override fun preVisitSykmeldingsperioder(sykmeldingsperioder: Sykmeldingsperioder) {
        delegatee.preVisitSykmeldingsperioder(sykmeldingsperioder)
    }

    override fun visitSykmeldingsperiode(periode: Periode) {
        delegatee.visitSykmeldingsperiode(periode)
    }

    override fun postVisitSykmeldingsperioder(sykmeldingsperioder: Sykmeldingsperioder) {
        delegatee.postVisitSykmeldingsperioder(sykmeldingsperioder)
    }

    override fun preVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element, id: UUID, hendelseId: UUID?, tidsstempel: LocalDateTime) {
        delegatee.preVisitSykdomshistorikkElement(element, id, hendelseId, tidsstempel)
    }

    override fun preVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje, hendelseId: UUID?, tidsstempel: LocalDateTime) {
        delegatee.preVisitHendelseSykdomstidslinje(tidslinje, hendelseId, tidsstempel)
    }

    override fun postVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje) {
        delegatee.postVisitHendelseSykdomstidslinje(tidslinje)
    }

    override fun preVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {
        delegatee.preVisitBeregnetSykdomstidslinje(tidslinje)
    }

    override fun postVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {
        delegatee.postVisitBeregnetSykdomstidslinje(tidslinje)
    }

    override fun postVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element, id: UUID, hendelseId: UUID?, tidsstempel: LocalDateTime) {
        delegatee.postVisitSykdomshistorikkElement(element, id, hendelseId, tidsstempel)
    }

    override fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        delegatee.postVisitSykdomshistorikk(sykdomshistorikk)
    }

    override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {
        delegatee.preVisitSykdomstidslinje(tidslinje, låstePerioder)
    }

    override fun visitDag(dag: Dag.UkjentDag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, kilde)
    }

    override fun visitDag(dag: Dag.Arbeidsdag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, kilde)
    }

    override fun visitDag(dag: Dag.Arbeidsgiverdag, dato: LocalDate, økonomi: Økonomi, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.Feriedag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, kilde)
    }

    override fun visitDag(dag: Dag.FerieUtenSykmeldingDag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, kilde)
    }

    override fun visitDag(dag: Dag.FriskHelgedag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, kilde)
    }

    override fun visitDag(dag: Dag.ArbeidsgiverHelgedag, dato: LocalDate, økonomi: Økonomi, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.Sykedag, dato: LocalDate, økonomi: Økonomi, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.ForeldetSykedag, dato: LocalDate, økonomi: Økonomi, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, økonomi, kilde)
    }

    override fun visitDag(
        dag: Dag.SykedagNav,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde
    ) {
        delegatee.visitDag(dag, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.SykHelgedag, dato: LocalDate, økonomi: Økonomi, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, økonomi, kilde)
    }

    override fun visitDag(dag: Dag.Permisjonsdag, dato: LocalDate, kilde: SykdomstidslinjeHendelse.Hendelseskilde) {
        delegatee.visitDag(dag, dato, kilde)
    }

    override fun visitDag(
        dag: Dag.ProblemDag,
        dato: LocalDate,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde,
        other: SykdomstidslinjeHendelse.Hendelseskilde?,
        melding: String
    ) {
        delegatee.visitDag(dag, dato, kilde, other, melding)
    }

    override fun visitDag(
        dag: Dag.AndreYtelser,
        dato: LocalDate,
        kilde: SykdomstidslinjeHendelse.Hendelseskilde,
        ytelse: Dag.AndreYtelser.AnnenYtelse
    ) {
        delegatee.visitDag(dag, dato, kilde, ytelse)
    }

    override fun postVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: MutableList<Periode>) {
        delegatee.postVisitSykdomstidslinje(tidslinje, låstePerioder)
    }

    override fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
        delegatee.preVisitInntekthistorikk(inntektshistorikk)
    }

    override fun postVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
        delegatee.postVisitInntekthistorikk(inntektshistorikk)
    }

    override fun preVisitSaksbehandler(
        saksbehandler: Saksbehandler,
        id: UUID,
        dato: LocalDate,
        hendelseId: UUID,
        beløp: Inntekt,
        forklaring: String?,
        subsumsjon: Subsumsjon?,
        tidsstempel: LocalDateTime
    ) {
        delegatee.preVisitSaksbehandler(saksbehandler, id, dato, hendelseId, beløp, forklaring, subsumsjon, tidsstempel)
    }

    override fun postVisitSaksbehandler(
        saksbehandler: Saksbehandler,
        id: UUID,
        dato: LocalDate,
        hendelseId: UUID,
        beløp: Inntekt,
        forklaring: String?,
        subsumsjon: Subsumsjon?,
        tidsstempel: LocalDateTime
    ) {
        delegatee.postVisitSaksbehandler(saksbehandler, id, dato, hendelseId, beløp, forklaring, subsumsjon, tidsstempel)
    }

    override fun preVisitSkjønnsmessigFastsatt(
        saksbehandler: SkjønnsmessigFastsatt,
        id: UUID,
        dato: LocalDate,
        hendelseId: UUID,
        beløp: Inntekt,
        tidsstempel: LocalDateTime
    ) {
        delegatee.preVisitSkjønnsmessigFastsatt(saksbehandler, id, dato, hendelseId, beløp, tidsstempel)
    }

    override fun postVisitSkjønnsmessigFastsatt(
        saksbehandler: SkjønnsmessigFastsatt,
        id: UUID,
        dato: LocalDate,
        hendelseId: UUID,
        beløp: Inntekt,
        tidsstempel: LocalDateTime
    ) {
        delegatee.postVisitSkjønnsmessigFastsatt(saksbehandler, id, dato, hendelseId, beløp, tidsstempel)
    }

    override fun visitInntektsmelding(
        inntektsmelding: Inntektsmelding,
        id: UUID,
        dato: LocalDate,
        hendelseId: UUID,
        beløp: Inntekt,
        tidsstempel: LocalDateTime
    ) {
        delegatee.visitInntektsmelding(inntektsmelding, id, dato, hendelseId, beløp, tidsstempel)
    }

    override fun visitIkkeRapportert(
        ikkeRapportert: IkkeRapportert,
        id: UUID,
        hendelseId: UUID,
        dato: LocalDate,
        tidsstempel: LocalDateTime
    ) {
        delegatee.visitIkkeRapportert(ikkeRapportert, id, hendelseId, dato, tidsstempel)
    }

    override fun visitInfotrygd(infotrygd: Infotrygd, id: UUID, dato: LocalDate, hendelseId: UUID, beløp: Inntekt, tidsstempel: LocalDateTime) {
        delegatee.visitInfotrygd(infotrygd, id, dato, hendelseId, beløp, tidsstempel)
    }

    override fun preVisitSkattSykepengegrunnlag(
        skattSykepengegrunnlag: SkattSykepengegrunnlag,
        id: UUID,
        hendelseId: UUID,
        dato: LocalDate,
        beløp: Inntekt,
        tidsstempel: LocalDateTime
    ) {
        delegatee.preVisitSkattSykepengegrunnlag(skattSykepengegrunnlag, id, hendelseId, dato, beløp, tidsstempel)
    }

    override fun postVisitSkattSykepengegrunnlag(
        skattSykepengegrunnlag: SkattSykepengegrunnlag,
        id: UUID,
        hendelseId: UUID,
        dato: LocalDate,
        beløp: Inntekt,
        tidsstempel: LocalDateTime
    ) {
        delegatee.postVisitSkattSykepengegrunnlag(skattSykepengegrunnlag, id, hendelseId, dato, beløp, tidsstempel)
    }

    override fun visitSkatteopplysning(
        skatteopplysning: Skatteopplysning,
        hendelseId: UUID,
        beløp: Inntekt,
        måned: YearMonth,
        type: Skatteopplysning.Inntekttype,
        fordel: String,
        beskrivelse: String,
        tidsstempel: LocalDateTime
    ) {
        delegatee.visitSkatteopplysning(skatteopplysning, hendelseId, beløp, måned, type, fordel, beskrivelse, tidsstempel)
    }

    override fun preVisitUtbetaling(
        utbetaling: Utbetaling,
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        utbetalingstatus: Utbetalingstatus,
        periode: Periode,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        stønadsdager: Int,
        beregningId: UUID,
        overføringstidspunkt: LocalDateTime?,
        avsluttet: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        annulleringer: Set<UUID>
    ) {
        delegatee.preVisitUtbetaling(
            utbetaling,
            id,
            korrelasjonsId,
            type,
            utbetalingstatus,
            periode,
            tidsstempel,
            oppdatert,
            arbeidsgiverNettoBeløp,
            personNettoBeløp,
            maksdato,
            forbrukteSykedager,
            gjenståendeSykedager,
            stønadsdager,
            beregningId,
            overføringstidspunkt,
            avsluttet,
            avstemmingsnøkkel,
            annulleringer
        )
    }

    override fun preVisitArbeidsgiverOppdrag(oppdrag: Oppdrag) {
        delegatee.preVisitArbeidsgiverOppdrag(oppdrag)
    }

    override fun postVisitArbeidsgiverOppdrag(oppdrag: Oppdrag) {
        delegatee.postVisitArbeidsgiverOppdrag(oppdrag)
    }

    override fun preVisitPersonOppdrag(oppdrag: Oppdrag) {
        delegatee.preVisitPersonOppdrag(oppdrag)
    }

    override fun postVisitPersonOppdrag(oppdrag: Oppdrag) {
        delegatee.postVisitPersonOppdrag(oppdrag)
    }

    override fun visitVurdering(
        vurdering: Utbetaling.Vurdering,
        ident: String,
        epost: String,
        tidspunkt: LocalDateTime,
        automatiskBehandling: Boolean,
        godkjent: Boolean
    ) {
        delegatee.visitVurdering(vurdering, ident, epost, tidspunkt, automatiskBehandling, godkjent)
    }

    override fun preVisitVedtakserperiodeUtbetalinger(utbetalinger: List<Triple<VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?, Utbetaling, Sykdomstidslinje?>>) {
        delegatee.preVisitVedtakserperiodeUtbetalinger(utbetalinger)
    }

    override fun preVisitVedtaksperiodeUtbetaling(
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
        utbetaling: Utbetaling,
        sykdomstidslinje: Sykdomstidslinje
    ) {
        delegatee.preVisitVedtaksperiodeUtbetaling(grunnlagsdata, utbetaling, sykdomstidslinje)
    }

    override fun postVisitVedtaksperiodeUtbetaling(
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
        utbetaling: Utbetaling,
        sykdomstidslinje: Sykdomstidslinje
    ) {
        delegatee.postVisitVedtaksperiodeUtbetaling(grunnlagsdata, utbetaling, sykdomstidslinje)
    }

    override fun postVisitVedtakserperiodeUtbetalinger(utbetalinger: List<Triple<VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?, Utbetaling, Sykdomstidslinje?>>) {
        delegatee.postVisitVedtakserperiodeUtbetalinger(utbetalinger)
    }

    override fun preVisitUtbetalingsdager(dager: Collection<Utbetalingsdag>) {
        delegatee.preVisitUtbetalingsdager(dager)
    }

    override fun postVisitUtbetalingsdager(dager: Collection<Utbetalingsdag>) {
        delegatee.postVisitUtbetalingsdager(dager)
    }

    override fun postVisitUtbetaling(
        utbetaling: Utbetaling,
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        tilstand: Utbetalingstatus,
        periode: Periode,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        stønadsdager: Int,
        beregningId: UUID,
        overføringstidspunkt: LocalDateTime?,
        avsluttet: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        annulleringer: Set<UUID>
    ) {
        delegatee.postVisitUtbetaling(
            utbetaling,
            id,
            korrelasjonsId,
            type,
            tilstand,
            periode,
            tidsstempel,
            oppdatert,
            arbeidsgiverNettoBeløp,
            personNettoBeløp,
            maksdato,
            forbrukteSykedager,
            gjenståendeSykedager,
            stønadsdager,
            beregningId,
            overføringstidspunkt,
            avsluttet,
            avstemmingsnøkkel,
            annulleringer
        )
    }

    override fun preVisitOppdrag(
        oppdrag: Oppdrag,
        fagområde: Fagområde,
        fagsystemId: String,
        mottaker: String,
        sisteArbeidsgiverdag: LocalDate?,
        stønadsdager: Int,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime,
        endringskode: Endringskode,
        avstemmingsnøkkel: Long?,
        status: Oppdragstatus?,
        overføringstidspunkt: LocalDateTime?,
        erSimulert: Boolean,
        simuleringsResultat: SimuleringResultat?
    ) {
        delegatee.preVisitOppdrag(
            oppdrag,
            fagområde,
            fagsystemId,
            mottaker,
            sisteArbeidsgiverdag,
            stønadsdager,
            totalBeløp,
            nettoBeløp,
            tidsstempel,
            endringskode,
            avstemmingsnøkkel,
            status,
            overføringstidspunkt,
            erSimulert,
            simuleringsResultat
        )
    }

    override fun postVisitOppdrag(
        oppdrag: Oppdrag,
        fagområde: Fagområde,
        fagsystemId: String,
        mottaker: String,
        sisteArbeidsgiverdag: LocalDate?,
        stønadsdager: Int,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime,
        endringskode: Endringskode,
        avstemmingsnøkkel: Long?,
        status: Oppdragstatus?,
        overføringstidspunkt: LocalDateTime?,
        erSimulert: Boolean,
        simuleringsResultat: SimuleringResultat?
    ) {
        delegatee.postVisitOppdrag(
            oppdrag,
            fagområde,
            fagsystemId,
            mottaker,
            sisteArbeidsgiverdag,
            stønadsdager,
            totalBeløp,
            nettoBeløp,
            tidsstempel,
            endringskode,
            avstemmingsnøkkel,
            status,
            overføringstidspunkt,
            erSimulert,
            simuleringsResultat
        )
    }

    override fun visitUtbetalingslinje(
        linje: Utbetalingslinje,
        fom: LocalDate,
        tom: LocalDate,
        stønadsdager: Int,
        totalbeløp: Int,
        satstype: Satstype,
        beløp: Int?,
        aktuellDagsinntekt: Int?,
        grad: Int?,
        delytelseId: Int,
        refDelytelseId: Int?,
        refFagsystemId: String?,
        endringskode: Endringskode,
        datoStatusFom: LocalDate?,
        statuskode: String?,
        klassekode: Klassekode
    ) {
        delegatee.visitUtbetalingslinje(
            linje,
            fom,
            tom,
            stønadsdager,
            totalbeløp,
            satstype,
            beløp,
            aktuellDagsinntekt,
            grad,
            delytelseId,
            refDelytelseId,
            refFagsystemId,
            endringskode,
            datoStatusFom,
            statuskode,
            klassekode
        )
    }

    override fun preVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {
        delegatee.preVisitRefusjonshistorikk(refusjonshistorikk)
    }

    override fun preVisitRefusjon(
        meldingsreferanseId: UUID,
        førsteFraværsdag: LocalDate?,
        arbeidsgiverperioder: List<Periode>,
        beløp: Inntekt?,
        sisteRefusjonsdag: LocalDate?,
        endringerIRefusjon: List<Refusjonshistorikk.Refusjon.EndringIRefusjon>,
        tidsstempel: LocalDateTime
    ) {
        delegatee.preVisitRefusjon(meldingsreferanseId, førsteFraværsdag, arbeidsgiverperioder, beløp, sisteRefusjonsdag, endringerIRefusjon, tidsstempel)
    }

    override fun visitEndringIRefusjon(beløp: Inntekt, endringsdato: LocalDate) {
        delegatee.visitEndringIRefusjon(beløp, endringsdato)
    }

    override fun postVisitRefusjon(
        meldingsreferanseId: UUID,
        førsteFraværsdag: LocalDate?,
        arbeidsgiverperioder: List<Periode>,
        beløp: Inntekt?,
        sisteRefusjonsdag: LocalDate?,
        endringerIRefusjon: List<Refusjonshistorikk.Refusjon.EndringIRefusjon>,
        tidsstempel: LocalDateTime
    ) {
        delegatee.postVisitRefusjon(meldingsreferanseId, førsteFraværsdag, arbeidsgiverperioder, beløp, sisteRefusjonsdag, endringerIRefusjon, tidsstempel)
    }

    override fun postVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {
        delegatee.postVisitRefusjonshistorikk(refusjonshistorikk)
    }
}
