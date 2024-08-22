package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.util.UUID
import no.nav.helse.AlderVisitor
import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.AktivitetsloggVisitor
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Utbetalingsperiode
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagVisitor
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningVisitor
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.InntektsmeldingVisitor
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeDagVisitor
import no.nav.helse.utbetalingslinjer.Feriepengeutbetaling
import no.nav.helse.utbetalingslinjer.OppdragVisitor
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.UtbetalingVisitor
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import no.nav.helse.utbetalingstidslinje.UtbetalingsdagVisitor
import no.nav.helse.økonomi.Avviksprosent
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosentdel

internal interface PersonVisitor : AlderVisitor, ArbeidsgiverVisitor, AktivitetsloggVisitor, VilkårsgrunnlagHistorikkVisitor, InfotrygdhistorikkVisitor {
    fun preVisitPerson(
        person: Person,
        opprettet: LocalDateTime,
        aktørId: String,
        personidentifikator: Personidentifikator,
        vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
    ) {}
    fun visitPersonAktivitetslogg(aktivitetslogg: Aktivitetslogg) {}
    fun preVisitArbeidsgivere() {}
    fun postVisitArbeidsgivere() {}
    fun postVisitPerson(
        person: Person,
        opprettet: LocalDateTime,
        aktørId: String,
        personidentifikator: Personidentifikator,
        vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk
    ) {}
}

internal interface InfotrygdperiodeVisitor {
    fun visitInfotrygdhistorikkFerieperiode(periode: Friperiode, fom: LocalDate, tom: LocalDate) {}
    fun visitInfotrygdhistorikkPersonUtbetalingsperiode(
        periode: Utbetalingsperiode,
        orgnr: String,
        fom: LocalDate,
        tom: LocalDate,
        grad: Prosentdel,
        inntekt: Inntekt
    ) {}
    fun visitInfotrygdhistorikkArbeidsgiverUtbetalingsperiode(
        periode: Utbetalingsperiode,
        orgnr: String,
        fom: LocalDate,
        tom: LocalDate,
        grad: Prosentdel,
        inntekt: Inntekt
    ) {}
}

internal interface InfotrygdhistorikkVisitor: InfotrygdperiodeVisitor {
    fun preVisitInfotrygdhistorikk() {}
    fun preVisitInfotrygdhistorikkElement(
        id: UUID,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        hendelseId: UUID?
    ) {
    }

    fun preVisitInfotrygdhistorikkPerioder() {}
    fun postVisitInfotrygdhistorikkPerioder() {}
    fun preVisitInfotrygdhistorikkInntektsopplysninger() {}
    fun visitInfotrygdhistorikkInntektsopplysning(
        orgnr: String,
        sykepengerFom: LocalDate,
        inntekt: Inntekt,
        refusjonTilArbeidsgiver: Boolean,
        refusjonTom: LocalDate?,
        lagret: LocalDateTime?
    ) {
    }

    fun postVisitInfotrygdhistorikkInntektsopplysninger() {}
    fun visitInfotrygdhistorikkArbeidskategorikoder(arbeidskategorikoder: Map<String, LocalDate>) {}
    fun postVisitInfotrygdhistorikkElement(
        id: UUID,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        hendelseId: UUID?
    ) {
    }

    fun postVisitInfotrygdhistorikk() {}
}

internal interface FeriepengeutbetalingsperiodeVisitor {
    fun visitPersonutbetalingsperiode(orgnr: String, periode: Periode, beløp: Int, utbetalt: LocalDate) {}
    fun visitArbeidsgiverutbetalingsperiode(orgnr: String, periode: Periode, beløp: Int, utbetalt: LocalDate) {}
}

interface RefusjonsopplysningerVisitor {
    fun preVisitRefusjonsopplysninger(refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger) {}
    fun visitRefusjonsopplysning(meldingsreferanseId: UUID, fom: LocalDate, tom: LocalDate?, beløp: Inntekt) {}
    fun postVisitRefusjonsopplysninger(refusjonsopplysninger: Refusjonsopplysning.Refusjonsopplysninger) {}
}

internal interface SykepengegrunnlagVisitor : ArbeidsgiverInntektsopplysningVisitor, SammenligningsgrunnlagVisitor {
    fun preVisitSykepengegrunnlag(
        sykepengegrunnlag1: Sykepengegrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Inntekt,
        avviksprosent: Avviksprosent,
        totalOmregnetÅrsinntekt: Inntekt,
        beregningsgrunnlag: Inntekt,
        `6G`: Inntekt,
        begrensning: Sykepengegrunnlag.Begrensning,
        vurdertInfotrygd: Boolean,
        minsteinntekt: Inntekt,
        oppfyllerMinsteinntektskrav: Boolean
    ) {}
    fun preVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {}

    fun postVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {}

    fun preVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {}

    fun postVisitDeaktiverteArbeidsgiverInntektsopplysninger(arbeidsgiverInntektopplysninger: List<ArbeidsgiverInntektsopplysning>) {}

    fun postVisitSykepengegrunnlag(
        sykepengegrunnlag1: Sykepengegrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Inntekt,
        avviksprosent: Avviksprosent,
        totalOmregnetÅrsinntekt: Inntekt,
        beregningsgrunnlag: Inntekt,
        `6G`: Inntekt,
        begrensning: Sykepengegrunnlag.Begrensning,
        vurdertInfotrygd: Boolean,
        minsteinntekt: Inntekt,
        oppfyllerMinsteinntektskrav: Boolean
    ) {}
}

internal interface SammenligningsgrunnlagVisitor : ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagVisitor {
    fun preVisitSammenligningsgrunnlag(
        sammenligningsgrunnlag1: Sammenligningsgrunnlag,
        sammenligningsgrunnlag: Inntekt
    ) {}

    fun preVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag>) {}

    fun postVisitArbeidsgiverInntektsopplysningerForSammenligningsgrunnlag(arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag>) {}

    fun postVisitSammenligningsgrunnlag(
        sammenligningsgrunnlag1: Sammenligningsgrunnlag,
        sammenligningsgrunnlag: Inntekt
    ) {}
}

internal interface OpptjeningVisitor {
    fun preVisitOpptjening(opptjening: Opptjening, arbeidsforhold: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag>, opptjeningsperiode: Periode) {}
    fun preVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer: String, ansattPerioder: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold>) {}
    fun visitArbeidsforhold(ansattFom: LocalDate, ansattTom: LocalDate?, deaktivert: Boolean) {}
    fun postVisitArbeidsgiverOpptjeningsgrunnlag(orgnummer: String, ansattPerioder: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold>) {}
    fun postVisitOpptjening(opptjening: Opptjening, arbeidsforhold: List<Opptjening.ArbeidsgiverOpptjeningsgrunnlag>, opptjeningsperiode: Periode) {}
}

internal interface VilkårsgrunnlagHistorikkVisitor : OpptjeningVisitor, SykepengegrunnlagVisitor {
    fun preVisitVilkårsgrunnlagHistorikk() {}
    fun preVisitInnslag(innslag: VilkårsgrunnlagHistorikk.Innslag, id: UUID, opprettet: LocalDateTime) {}
    fun postVisitInnslag(innslag: VilkårsgrunnlagHistorikk.Innslag, id: UUID, opprettet: LocalDateTime) {}
    fun postVisitVilkårsgrunnlagHistorikk() {}
    fun preVisitGrunnlagsdata(
        skjæringstidspunkt: LocalDate,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
        sykepengegrunnlag: Sykepengegrunnlag,
        opptjening: Opptjening,
        vurdertOk: Boolean,
        meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus
    ) {}
    fun postVisitGrunnlagsdata(
        skjæringstidspunkt: LocalDate,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
        sykepengegrunnlag: Sykepengegrunnlag,
        medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
        vurdertOk: Boolean,
        meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID
    ) {}
    fun preVisitInfotrygdVilkårsgrunnlag(
        infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID
    ) {
    }

    fun postVisitInfotrygdVilkårsgrunnlag(
        infotrygdVilkårsgrunnlag: VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID
    ) {}
}

internal interface ArbeidsgiverVisitor : InntekthistorikkVisitor, SykdomshistorikkVisitor, VedtaksperiodeVisitor,
    UtbetalingVisitor, FeriepengeutbetalingVisitor, RefusjonshistorikkVisitor, SykmeldingsperioderVisitor {
    fun preVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) {
    }

    fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) {}
    fun postVisitUtbetalinger(utbetalinger: List<Utbetaling>) {}
    fun preVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {}
    fun postVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {}
    fun preVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {}
    fun preVisitForkastetPeriode(vedtaksperiode: Vedtaksperiode) {}
    fun postVisitForkastetPeriode(vedtaksperiode: Vedtaksperiode) {}
    fun postVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {}
    fun postVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) {
    }
}

internal interface FeriepengeutbetalingVisitor : OppdragVisitor {
    fun preVisitFeriepengeutbetalinger(feriepengeutbetalinger: List<Feriepengeutbetaling>) {}
    fun preVisitFeriepengeutbetaling(
        feriepengeutbetaling: Feriepengeutbetaling,
        infotrygdFeriepengebeløpPerson: Double,
        infotrygdFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpPerson: Double,
        overføringstidspunkt: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        utbetalingId: UUID,
        sendTilOppdrag: Boolean,
        sendPersonoppdragTilOS: Boolean,
    ) {
    }

    fun preVisitFeriepengerArbeidsgiveroppdrag() {}
    fun preVisitFeriepengerPersonoppdrag() {}

    fun preVisitFeriepengeberegner(
        feriepengeberegner: Feriepengeberegner,
        feriepengedager: List<Feriepengeberegner.UtbetaltDag>,
        opptjeningsår: Year,
        utbetalteDager: List<Feriepengeberegner.UtbetaltDag>
    ) {
    }

    fun preVisitUtbetaleDager() {}
    fun postVisitUtbetaleDager() {}
    fun preVisitFeriepengedager() {}
    fun postVisitFeriepengedager() {}

    fun visitInfotrygdPersonDag(infotrygdPerson: Feriepengeberegner.UtbetaltDag.InfotrygdPerson, orgnummer: String, dato: LocalDate, beløp: Int) {}
    fun visitInfotrygdArbeidsgiverDag(
        infotrygdArbeidsgiver: Feriepengeberegner.UtbetaltDag.InfotrygdArbeidsgiver,
        orgnummer: String,
        dato: LocalDate,
        beløp: Int
    ) {
    }

    fun visitSpleisArbeidsgiverDag(spleisArbeidsgiver: Feriepengeberegner.UtbetaltDag.SpleisArbeidsgiver, orgnummer: String, dato: LocalDate, beløp: Int) {}
    fun visitSpleisPersonDag(spleisPerson: Feriepengeberegner.UtbetaltDag.SpleisPerson, orgnummer: String, dato: LocalDate, beløp: Int) {}
    fun postVisitFeriepengeberegner(
        feriepengeberegner: Feriepengeberegner,
        feriepengedager: List<Feriepengeberegner.UtbetaltDag>,
        opptjeningsår: Year,
        utbetalteDager: List<Feriepengeberegner.UtbetaltDag>
    ) {}
    fun postVisitFeriepengeutbetaling(
        feriepengeutbetaling: Feriepengeutbetaling,
        infotrygdFeriepengebeløpPerson: Double,
        infotrygdFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpArbeidsgiver: Double,
        spleisFeriepengebeløpPerson: Double,
        overføringstidspunkt: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        utbetalingId: UUID,
        sendTilOppdrag: Boolean,
        sendPersonoppdragTilOS: Boolean,
    ) {
    }

    fun postVisitFeriepengeutbetalinger(feriepengeutbetalinger: List<Feriepengeutbetaling>) {}
}

internal interface BehandlingerVisitor : BehandlingVisitor {

    fun preVisitBehandlinger(behandlinger: List<Behandlinger.Behandling>) {}
    fun postVisitBehandlinger(behandlinger: List<Behandlinger.Behandling>) {}
}

internal interface BehandlingVisitor : UtbetalingVisitor, VilkårsgrunnlagHistorikkVisitor, SykdomstidslinjeVisitor {
    fun preVisitBehandling(
        id: UUID,
        tidsstempel: LocalDateTime,
        tilstand: Behandlinger.Behandling.Tilstand,
        periode: Periode,
        vedtakFattet: LocalDateTime?,
        avsluttet: LocalDateTime?,
        kilde: Behandlinger.Behandlingkilde
    ) {}
    fun preVisitBehandlingendring(
        id: UUID,
        tidsstempel: LocalDateTime,
        sykmeldingsperiode: Periode,
        periode: Periode,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
        utbetaling: Utbetaling?,
        dokumentsporing: Dokumentsporing,
        sykdomstidslinje: Sykdomstidslinje,
        skjæringstidspunkt: LocalDate,
        arbeidsgiverperiode: List<Periode>
    ) {}
    fun postVisitBehandlingendring(
        id: UUID,
        tidsstempel: LocalDateTime,
        sykmeldingsperiode: Periode,
        periode: Periode,
        grunnlagsdata: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement?,
        utbetaling: Utbetaling?,
        dokumentsporing: Dokumentsporing,
        sykdomstidslinje: Sykdomstidslinje,
        skjæringstidspunkt: LocalDate,
        arbeidsgiverperiode: List<Periode>
    ) {}
    fun visitBehandlingkilde(
        meldingsreferanseId: UUID,
        innsendt: LocalDateTime,
        registrert: LocalDateTime,
        avsender: Avsender
    ) {}
    fun postVisitBehandling(
        id: UUID,
        tidsstempel: LocalDateTime,
        tilstand: Behandlinger.Behandling.Tilstand,
        periode: Periode,
        vedtakFattet: LocalDateTime?,
        avsluttet: LocalDateTime?,
        kilde: Behandlinger.Behandlingkilde
    ) {}
}

internal interface VedtaksperiodeVisitor : BehandlingerVisitor, UtbetalingsdagVisitor {
    fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: LocalDate,
        hendelseIder: Set<Dokumentsporing>,
        egenmeldingsperioder: List<Periode>
    ) {}

    fun postVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: LocalDate,
        hendelseIder: Set<Dokumentsporing>
    ) {
    }
}

internal interface SykdomshistorikkVisitor : SykdomstidslinjeVisitor {
    fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {}
    fun preVisitSykdomshistorikkElement(
        element: Sykdomshistorikk.Element,
        id: UUID,
        hendelseId: UUID?,
        tidsstempel: LocalDateTime
    ) {
    }

    fun preVisitHendelseSykdomstidslinje(
        tidslinje: Sykdomstidslinje,
        hendelseId: UUID?,
        tidsstempel: LocalDateTime
    ) {
    }

    fun postVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje) {}
    fun preVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {}
    fun postVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {}
    fun postVisitSykdomshistorikkElement(
        element: Sykdomshistorikk.Element,
        id: UUID,
        hendelseId: UUID?,
        tidsstempel: LocalDateTime
    ) {
    }

    fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {}
}

internal interface SykdomstidslinjeVisitor: SykdomstidslinjeDagVisitor {
    fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {}
    fun postVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: MutableList<Periode>) {}
}

internal interface RefusjonshistorikkVisitor {
    fun preVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {}
    fun preVisitRefusjon(
        meldingsreferanseId: UUID,
        førsteFraværsdag: LocalDate?,
        arbeidsgiverperioder: List<Periode>,
        beløp: Inntekt?,
        sisteRefusjonsdag: LocalDate?,
        endringerIRefusjon: List<Refusjonshistorikk.Refusjon.EndringIRefusjon>,
        tidsstempel: LocalDateTime
    ) {
    }

    fun visitEndringIRefusjon(beløp: Inntekt, endringsdato: LocalDate) {}
    fun postVisitRefusjon(
        meldingsreferanseId: UUID,
        førsteFraværsdag: LocalDate?,
        arbeidsgiverperioder: List<Periode>,
        beløp: Inntekt?,
        sisteRefusjonsdag: LocalDate?,
        endringerIRefusjon: List<Refusjonshistorikk.Refusjon.EndringIRefusjon>,
        tidsstempel: LocalDateTime
    ) {
    }

    fun postVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {}
}

internal interface InntekthistorikkVisitor : InntektsmeldingVisitor {
    fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {}
    fun postVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {}
}

