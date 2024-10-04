package no.nav.helse.spleis.mediator

import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.IdentOpphørt
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.Migrate
import no.nav.helse.hendelser.MinimumSykdomsgradsvurderingMelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingpåminnelse
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Personopplysninger
import no.nav.helse.spleis.meldinger.model.AnmodningOmForkastingMessage
import no.nav.helse.spleis.meldinger.model.AnnulleringMessage
import no.nav.helse.spleis.meldinger.model.AvbruttArbeidsledigSøknadMessage
import no.nav.helse.spleis.meldinger.model.AvbruttSøknadMessage
import no.nav.helse.spleis.meldinger.model.AvstemmingMessage
import no.nav.helse.spleis.meldinger.model.DødsmeldingMessage
import no.nav.helse.spleis.meldinger.model.GrunnbeløpsreguleringMessage
import no.nav.helse.spleis.meldinger.model.ForkastSykmeldingsperioderMessage
import no.nav.helse.spleis.meldinger.model.GjenopplivVilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import no.nav.helse.spleis.meldinger.model.IdentOpphørtMessage
import no.nav.helse.spleis.meldinger.model.InfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingerReplayMessage
import no.nav.helse.spleis.meldinger.model.MigrateMessage
import no.nav.helse.spleis.meldinger.model.MinimumSykdomsgradVurdertMessage
import no.nav.helse.spleis.meldinger.model.NyArbeidsledigSøknadMessage
import no.nav.helse.spleis.meldinger.model.NyFrilansSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySelvstendigSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySøknadMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsforholdMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsgiveropplysningerMessage
import no.nav.helse.spleis.meldinger.model.OverstyrTidslinjeMessage
import no.nav.helse.spleis.meldinger.model.PersonPåminnelseMessage
import no.nav.helse.spleis.meldinger.model.PåminnelseMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsgiverMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsledigMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadFrilansMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadNavMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadSelvstendigMessage
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.helse.spleis.meldinger.model.SkjønnsmessigFastsettelseMessage
import no.nav.helse.spleis.meldinger.model.SykepengegrunnlagForArbeidsgiverMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingpåminnelseMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingsgodkjenningMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkEtterInfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkForFeriepengerMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkMessage
import no.nav.helse.spleis.meldinger.model.VilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.YtelserMessage

internal class TestHendelseMediator : IHendelseMediator {
    val lestNySøknad get() = lestNySøknadVerdi.get()
    val lestNySøknadFrilans get() = lestNySøknadFrilansVerdi.get()
    val lestNySøknadSelvstendig get() = lestNySøknadSelvstendigVerdi.get()
    val lestNySøknadArbeidsledig get() = lestNySøknadArbeidsledigVerdi.get()
    val lestSendtSøknadArbeidsgiver get() = lestSendtSøknadArbeidsgiverVerdi.get()
    val lestSendtSøknad get() = lestSendtSøknadVerdi.get()
    val lestSendtSøknadFrilans get() = lestSendtSøknadFrilansVerdi.get()
    val lestSendtSøknadSelvstendig get() = lestSendtSøknadSelvstendigVerdi.get()
    val lestSendtSøknadArbeidsledig get() = lestSendtSøknadArbeidsledigVerdi.get()
    val lestInntektsmelding get() = lestInntektsmeldingVerdi.get()
    val lestDødsmelding get() = lestDødsmeldingVerdi.get()
    val lestPåminnelse get() = lestPåminnelseVerdi.get()
    val lestPersonpåminnelse get() = lestPersonpåminnelseVerdi.get()
    val lestAnmodningOmForkasting get() = lestAnmodningOmForkastingVerdi.get()
    val lestutbetalingpåminnelse get() = lestutbetalingpåminnelseVerdi.get()
    val lestUtbetalingshistorikk get() = lestUtbetalingshistorikkVerdi.get()
    val lestUtbetalingshistorikkForFeriepenger get() = lestUtbetalingshistorikkForFeriepengerVerdi.get()
    val lestYtelser get() = lestYtelserVerdi.get()
    val lestVilkårsgrunnlag get() = lestVilkårsgrunnlagVerdi.get()
    val lestUtbetalingsgrunnlag get() = lestUtbetalingsgrunnlagVerdi.get()
    val lestSimulering get() = lestSimuleringVerdi.get()
    val lestUtbetalingsgodkjenning get() = lestUtbetalingsgodkjenningVerdi.get()
    val lestUtbetaling get() = lestUtbetalingVerdi.get()
    val lestAnnullerUtbetaling get() = lestAnnullerUtbetalingVerdi.get()
    val lestAvstemming get() = lestAvstemmingVerdi.get()
    val lestMigrate get() = lestMigrateVerdi.get()
    val lestOverstyrTidslinje get() = lestOverstyrTidslinjeVerdi.get()
    val lestOverstyrInntekt get() = lestOverstyrInntektVerdi.get()
    val lestOverstyrArbeidsgiveropplysningser get() = lestOverstyrArbeidsgiveropplysningserVerdi.get()
    val lestOverstyrArbeidsforhold get() = lestOverstyrArbeidsforholdVerdi.get()
    val lestReplayHendelser get() = lestReplayHendelserVerdi.get()
    val lestGrunnbeløpsregulering get() = lestGrunnbeløpsreguleringVerdi.get()
    val lestInfotrygdendring get() = lestInfotrygdendringVerdi.get()
    val utbetalingshistorikkEtterInfotrygdendringMessage get() = utbetalingshistorikkEtterInfotrygdendringMessageVerdi.get()
    val lestForkastSykmeldingsperioderMessage get() = lestForkastSykmeldingsperioderMessageVerdi.get()

    private val lestNySøknadVerdi = ThreadLocal.withInitial { false }
    private val lestNySøknadFrilansVerdi = ThreadLocal.withInitial { false }
    private val lestNySøknadSelvstendigVerdi = ThreadLocal.withInitial { false }
    private val lestNySøknadArbeidsledigVerdi = ThreadLocal.withInitial { false }
    private val lestSendtSøknadArbeidsgiverVerdi = ThreadLocal.withInitial { false }
    private val lestSendtSøknadVerdi = ThreadLocal.withInitial { false }
    private val lestSendtSøknadFrilansVerdi = ThreadLocal.withInitial { false }
    private val lestSendtSøknadSelvstendigVerdi = ThreadLocal.withInitial { false }
    private val lestSendtSøknadArbeidsledigVerdi = ThreadLocal.withInitial { false }
    private val lestInntektsmeldingVerdi = ThreadLocal.withInitial { false }
    private val lestInntektsmeldingReplayVerdi = ThreadLocal.withInitial { false }
    private val lestDødsmeldingVerdi = ThreadLocal.withInitial { false }
    private val lestPåminnelseVerdi = ThreadLocal.withInitial { false }
    private val lestPersonpåminnelseVerdi = ThreadLocal.withInitial { false }
    private val lestAnmodningOmForkastingVerdi = ThreadLocal.withInitial { false }
    private val lestutbetalingpåminnelseVerdi = ThreadLocal.withInitial { false }
    private val lestUtbetalingshistorikkVerdi = ThreadLocal.withInitial { false }
    private val lestUtbetalingshistorikkForFeriepengerVerdi = ThreadLocal.withInitial { false }
    private val lestYtelserVerdi = ThreadLocal.withInitial { false }
    private val lestVilkårsgrunnlagVerdi = ThreadLocal.withInitial { false }
    private val lestUtbetalingsgrunnlagVerdi = ThreadLocal.withInitial { false }
    private val lestSimuleringVerdi = ThreadLocal.withInitial { false }
    private val lestUtbetalingsgodkjenningVerdi = ThreadLocal.withInitial { false }
    private val lestUtbetalingVerdi = ThreadLocal.withInitial { false }
    private val lestAnnullerUtbetalingVerdi = ThreadLocal.withInitial { false }
    private val lestAvstemmingVerdi = ThreadLocal.withInitial { false }
    private val lestMigrateVerdi = ThreadLocal.withInitial { false }
    private val lestOverstyrTidslinjeVerdi = ThreadLocal.withInitial { false }
    private val lestOverstyrInntektVerdi = ThreadLocal.withInitial { false }
    private val lestOverstyrArbeidsgiveropplysningserVerdi = ThreadLocal.withInitial { false }
    private val lestOverstyrArbeidsforholdVerdi = ThreadLocal.withInitial { false }
    private val lestReplayHendelserVerdi = ThreadLocal.withInitial { false }
    private val lestGrunnbeløpsreguleringVerdi = ThreadLocal.withInitial { false }
    private val lestInfotrygdendringVerdi = ThreadLocal.withInitial { false }
    private val utbetalingshistorikkEtterInfotrygdendringMessageVerdi = ThreadLocal.withInitial { false }
    private val lestForkastSykmeldingsperioderMessageVerdi = ThreadLocal.withInitial { false }

    fun reset() {
        lestNySøknadVerdi.remove()
        lestNySøknadFrilansVerdi.remove()
        lestNySøknadSelvstendigVerdi.remove()
        lestNySøknadArbeidsledigVerdi.remove()
        lestSendtSøknadArbeidsgiverVerdi.remove()
        lestSendtSøknadVerdi.remove()
        lestSendtSøknadFrilansVerdi.remove()
        lestSendtSøknadSelvstendigVerdi.remove()
        lestSendtSøknadArbeidsledigVerdi.remove()
        lestInntektsmeldingVerdi.remove()
        lestPåminnelseVerdi.remove()
        lestPersonpåminnelseVerdi.remove()
        lestutbetalingpåminnelseVerdi.remove()
        lestUtbetalingshistorikkVerdi.remove()
        lestYtelserVerdi.remove()
        lestVilkårsgrunnlagVerdi.remove()
        lestUtbetalingsgrunnlagVerdi.remove()
        lestSimuleringVerdi.remove()
        lestUtbetalingsgodkjenningVerdi.remove()
        lestUtbetalingVerdi.remove()
        lestAnnullerUtbetalingVerdi.remove()
        lestAvstemmingVerdi.remove()
        lestOverstyrTidslinjeVerdi.remove()
        lestGrunnbeløpsreguleringVerdi.remove()
        lestInfotrygdendringVerdi.remove()
        utbetalingshistorikkEtterInfotrygdendringMessageVerdi.remove()
        lestAnmodningOmForkastingVerdi.remove()
    }

    override fun behandle(message: HendelseMessage, context: MessageContext) {
        message.behandle(this, context)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NySøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestNySøknadVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NyFrilansSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestNySøknadFrilansVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NySelvstendigSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestNySøknadSelvstendigVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NyArbeidsledigSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestNySøknadArbeidsledigVerdi.set(true)
    }

    override fun behandle(
        message: SykepengegrunnlagForArbeidsgiverMessage,
        sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver,
        context: MessageContext
    ) {

    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadFrilansMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadFrilansVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadSelvstendigMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadSelvstendigVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsledigMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadArbeidsledigVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsgiverMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadArbeidsgiverVerdi.set(true)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadNavMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadVerdi.set(true)
    }

    override fun behandle(personopplysninger: Personopplysninger, message: InntektsmeldingMessage, inntektsmelding: Inntektsmelding, context: MessageContext) {
        lestInntektsmeldingVerdi.set(true)
    }

    override fun behandle(
        message: InntektsmeldingerReplayMessage,
        replays: InntektsmeldingerReplay,
        context: MessageContext
    ) {}

    override fun behandle(message: DødsmeldingMessage, dødsmelding: Dødsmelding, context: MessageContext) {
        lestDødsmeldingVerdi.set(true)
    }

    override fun behandle(
        nyPersonidentifikator: Personidentifikator,
        message: IdentOpphørtMessage,
        identOpphørt: IdentOpphørt,
        nyAktørId: String,
        gamleIdenter: Set<Personidentifikator>,
        context: MessageContext
    ) {}

    override fun behandle(message: UtbetalingpåminnelseMessage, påminnelse: Utbetalingpåminnelse, context: MessageContext) {
        lestutbetalingpåminnelseVerdi.set(true)
    }

    override fun behandle(message: PåminnelseMessage, påminnelse: Påminnelse, context: MessageContext) {
        lestPåminnelseVerdi.set(true)
    }

    override fun behandle(message: PersonPåminnelseMessage, påminnelse: PersonPåminnelse, context: MessageContext) {
        lestPersonpåminnelseVerdi.set(true)
    }

    override fun behandle(
        message: AnmodningOmForkastingMessage,
        anmodning: AnmodningOmForkasting,
        context: MessageContext
    ) {
        lestAnmodningOmForkastingVerdi.set(true)
    }

    override fun behandle(message: UtbetalingshistorikkMessage, utbetalingshistorikk: Utbetalingshistorikk, context: MessageContext) {
        lestUtbetalingshistorikkVerdi.set(true)
    }

    override fun behandle(message: UtbetalingshistorikkForFeriepengerMessage, utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger, context: MessageContext) {
        lestUtbetalingshistorikkForFeriepengerVerdi.set(true)
    }

    override fun behandle(message: YtelserMessage, ytelser: Ytelser, context: MessageContext) {
        lestYtelserVerdi.set(true)
    }

    override fun behandle(message: VilkårsgrunnlagMessage, vilkårsgrunnlag: Vilkårsgrunnlag, context: MessageContext) {
        lestVilkårsgrunnlagVerdi.set(true)
    }

    override fun behandle(message: UtbetalingsgodkjenningMessage, utbetalingsgodkjenning: Utbetalingsgodkjenning, context: MessageContext) {
        lestUtbetalingsgodkjenningVerdi.set(true)
    }

    override fun behandle(message: UtbetalingMessage, utbetaling: UtbetalingHendelse, context: MessageContext) {
        lestUtbetalingVerdi.set(true)
    }

    override fun behandle(message: SimuleringMessage, simulering: Simulering, context: MessageContext) {
        lestSimuleringVerdi.set(true)
    }

    override fun behandle(message: AnnulleringMessage, annullerUtbetaling: AnnullerUtbetaling, context: MessageContext) {
        lestAnnullerUtbetalingVerdi.set(true)
    }

    override fun behandle(message: AvstemmingMessage, personidentifikator: Personidentifikator, aktørId: String, context: MessageContext) {
        lestAvstemmingVerdi.set(true)
    }

    override fun behandle(message: MigrateMessage, migrate: Migrate, context: MessageContext) {
        lestMigrateVerdi.set(true)
    }

    override fun behandle(message: OverstyrTidslinjeMessage, overstyrTidslinje: OverstyrTidslinje, context: MessageContext) {
        lestOverstyrTidslinjeVerdi.set(true)
    }

    override fun behandle(message: OverstyrArbeidsgiveropplysningerMessage, overstyrArbeidsgiveropplysninger: OverstyrArbeidsgiveropplysninger, context: MessageContext) {
        lestOverstyrArbeidsgiveropplysningserVerdi.set(true)
    }

    override fun behandle(message: OverstyrArbeidsforholdMessage, overstyrArbeidsforhold: OverstyrArbeidsforhold, context: MessageContext) {
        lestOverstyrArbeidsforholdVerdi.set(true)
    }

    override fun behandle(message: GrunnbeløpsreguleringMessage, grunnbeløpsregulering: Grunnbeløpsregulering, context: MessageContext) {
        lestGrunnbeløpsreguleringVerdi.set(true)
    }

    override fun behandle(
        message: InfotrygdendringMessage,
        infotrygdEndring: Infotrygdendring,
        context: MessageContext
    ) {
        lestInfotrygdendringVerdi.set(true)
    }

    override fun behandle(
        message: UtbetalingshistorikkEtterInfotrygdendringMessage,
        utbetalingshistorikkEtterInfotrygdendring: UtbetalingshistorikkEtterInfotrygdendring,
        context: MessageContext
    ) {
        utbetalingshistorikkEtterInfotrygdendringMessageVerdi.set(true)
    }

    override fun behandle(
        message: ForkastSykmeldingsperioderMessage,
        forkastSykmeldingsperioder: ForkastSykmeldingsperioder,
        context: MessageContext
    ) {
        lestForkastSykmeldingsperioderMessageVerdi.set(true)
    }

    override fun behandle(
        avbruttSøknadMessage: AvbruttSøknadMessage,
        avbruttSøknad: AvbruttSøknad,
        context: MessageContext
    ) {}

    override fun behandle(
        avbruttArbeidsledigSøknadMessage: AvbruttArbeidsledigSøknadMessage,
        avbruttSøknad: AvbruttSøknad,
        context: MessageContext
    ) {}

    override fun behandle(
        message: GjenopplivVilkårsgrunnlagMessage,
        gjenopplivVilkårsgrunnlag: GjenopplivVilkårsgrunnlag,
        context: MessageContext
    ) {}

    override fun behandle(
        message: SkjønnsmessigFastsettelseMessage,
        skjønnsmessigFastsettelse: SkjønnsmessigFastsettelse,
        context: MessageContext
    ) {}

    override fun behandle(
        message: MinimumSykdomsgradVurdertMessage,
        minimumSykdomsgradsvurdering: MinimumSykdomsgradsvurderingMelding,
        context: MessageContext
    ) {}
}
