package no.nav.helse.serde

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.person.Person
import no.nav.helse.serde.migration.JsonMigration
import no.nav.helse.serde.migration.JsonMigrationException
import no.nav.helse.serde.migration.MeldingerSupplier
import no.nav.helse.serde.migration.V100SlettEnkeltFeriepengeutbetaling
import no.nav.helse.serde.migration.V101SlettEnkeltFeriepengeutbetaling
import no.nav.helse.serde.migration.V102LeggerFeriepengerSendTilOppdragFelt
import no.nav.helse.serde.migration.V103FjernerFeriepengeUtbetalingerMedTalletNullSomBeløp
import no.nav.helse.serde.migration.V104SlettOpphørMedFeilKlassekode
import no.nav.helse.serde.migration.V105VilkårsgrunnlagMedGenerasjoner
import no.nav.helse.serde.migration.V106FjernerTommeInnslagIVilkårsgrunnlagHistorikken
import no.nav.helse.serde.migration.V107None
import no.nav.helse.serde.migration.V108None
import no.nav.helse.serde.migration.V109UtvidetUtbetalingstidslinjeBeregning
import no.nav.helse.serde.migration.V10EndreNavnPåSykdomstidslinjer
import no.nav.helse.serde.migration.V110SletteArbeidsforhold
import no.nav.helse.serde.migration.V111RiktigStatusAnnullerteUtbetalinger
import no.nav.helse.serde.migration.V112FjernTrailingCarriageReturnFraFagsystemId
import no.nav.helse.serde.migration.V113MigrerVekkFraFjernetTilstandTilAnnullering
import no.nav.helse.serde.migration.V114LagreSykepengegrunnlag
import no.nav.helse.serde.migration.V115LagreVilkårsgrunnlagFraInfotrygd
import no.nav.helse.serde.migration.V116LeggTilRefusjonshistorikk
import no.nav.helse.serde.migration.V117LeggTilGradPåUgyldigeInfotrygdHistorikkPerioder
import no.nav.helse.serde.migration.V118LeggTilBegrensningPåSykepengegrunnlag
import no.nav.helse.serde.migration.V119SletteSkatteopplydsningFraITVilkårsgrunnlag
import no.nav.helse.serde.migration.V11LeggeTilForlengelseFraInfotrygd
import no.nav.helse.serde.migration.V120BrukeInntektsmeldingOverSkattIVilkårsgrunnlag
import no.nav.helse.serde.migration.V121SletteVilkårsgrunnlagUtenNødvendigInntekt
import no.nav.helse.serde.migration.V122FikseNullverdierForAvstemmingsnøkkel
import no.nav.helse.serde.migration.V123FlytteDataInnIOppdrag
import no.nav.helse.serde.migration.V124SetteOppdragSomUendret
import no.nav.helse.serde.migration.V125KorrelasjonsIdPåUtbetaling
import no.nav.helse.serde.migration.V126FjernePaddingPåFagsystemId
import no.nav.helse.serde.migration.V127DefaultArbeidsgiverRefusjonsbeløp
import no.nav.helse.serde.migration.V128FjerneUgyldigSimuleringsResultat
import no.nav.helse.serde.migration.V129KopiereSimuleringsResultatTilOppdrag
import no.nav.helse.serde.migration.V12Aktivitetslogg
import no.nav.helse.serde.migration.V130TrimmeUtbetalingstidslinje
import no.nav.helse.serde.migration.V131FjernHelgedagFraSykdomstidslinje
import no.nav.helse.serde.migration.V132None
import no.nav.helse.serde.migration.V133VilkårsgrunnlagIdPåVilkårsgrunnlag
import no.nav.helse.serde.migration.V134OppdragErSimulert
import no.nav.helse.serde.migration.V135UtbetalingslinjeGrad
import no.nav.helse.serde.migration.V136MigrereTilstanderPåForkastede
import no.nav.helse.serde.migration.V137InntektsmeldingInfoHistorikk
import no.nav.helse.serde.migration.V138SammenligningsgrunnlagPerArbeidsgiverIVilkårsgrunnlag
import no.nav.helse.serde.migration.V139EndreTommeInntektsopplysningerTilIkkeRapportert
import no.nav.helse.serde.migration.V13NettoBeløpIOppdrag
import no.nav.helse.serde.migration.V140OppdaterFelterIArbeidsforhold
import no.nav.helse.serde.migration.V141RenameErAktivtTilDeaktivert
import no.nav.helse.serde.migration.V142DeaktiverteArbeidsforholdPåSykepengegrunnlag
import no.nav.helse.serde.migration.V143DummyMigrerHendelseInnPåVedtaksperiode
import no.nav.helse.serde.migration.V144TyperPåHendelserIVedtaksperiode
import no.nav.helse.serde.migration.V145DummyLagreArbeidsforholdForOpptjening
import no.nav.helse.serde.migration.V146DummyLagreArbeidsforholdForOpptjening
import no.nav.helse.serde.migration.V147LagreArbeidsforholdForOpptjening
import no.nav.helse.serde.migration.V148OpprettSykmeldingsperioder
import no.nav.helse.serde.migration.V149ResetLåstePerioder
import no.nav.helse.serde.migration.V14NettoBeløpIVedtaksperiode
import no.nav.helse.serde.migration.V150MigrerVedtaksperioderTilNyTilstandsflyt
import no.nav.helse.serde.migration.V151ResetLåstePerioder
import no.nav.helse.serde.migration.V152SlettGamleSykmeldingsperioder
import no.nav.helse.serde.migration.V153FjerneSykmeldingsdager
import no.nav.helse.serde.migration.V154FjerneProblemdager
import no.nav.helse.serde.migration.V155FjerneProblemdager
import no.nav.helse.serde.migration.V156None
import no.nav.helse.serde.migration.V157ManglerDagerPåSykdomstidslinjenDryRun
import no.nav.helse.serde.migration.V158LeggerTilPersonoppdragForFeriepenger
import no.nav.helse.serde.migration.V159None
import no.nav.helse.serde.migration.V15ØkonomiSykdomstidslinjer
import no.nav.helse.serde.migration.V160FikserDoubleGrad
import no.nav.helse.serde.migration.V161FikserDoubleGrad
import no.nav.helse.serde.migration.V162SletterDuplikaterFraInntektshistorikken
import no.nav.helse.serde.migration.V163ForkasteTommeVedtaksperioder
import no.nav.helse.serde.migration.V164ForkasteForkastedeVedtaksperioder
import no.nav.helse.serde.migration.V165TrimmerVedtaksperiode
import no.nav.helse.serde.migration.V166UtbetalteDagerMedForHøyAvviksprosent
import no.nav.helse.serde.migration.V167ProblemDagKilde
import no.nav.helse.serde.migration.V168Fødselsdato
import no.nav.helse.serde.migration.V169TildelingUtbetaling
import no.nav.helse.serde.migration.V16StatusIUtbetaling
import no.nav.helse.serde.migration.V170TildelingUtbetaling
import no.nav.helse.serde.migration.V171FjerneForkastedePerioderUtenSykdomstidslinje
import no.nav.helse.serde.migration.V172LoggeUbrukteVilkårsgrunnlag
import no.nav.helse.serde.migration.V173FjerneUbrukteVilkårsgrunnlag
import no.nav.helse.serde.migration.V174None
import no.nav.helse.serde.migration.V175IdPåAktiviteter
import no.nav.helse.serde.migration.V176LoggingAvForkastOgFlyttVilkårsgrunnlag
import no.nav.helse.serde.migration.V177LoggingAvForkastOgFlyttVilkårsgrunnlag
import no.nav.helse.serde.migration.V178ForkastOgFlyttVilkårsgrunnlag
import no.nav.helse.serde.migration.V179TildelingUtbetaling
import no.nav.helse.serde.migration.V17ForkastedePerioder
import no.nav.helse.serde.migration.V180OverstyrteVilkårsgrunnlag
import no.nav.helse.serde.migration.V181FjerneUtbetaling
import no.nav.helse.serde.migration.V182OverstyrteVilkårsgrunnlag
import no.nav.helse.serde.migration.V183UtbetalingerOgVilkårsgrunnlag
import no.nav.helse.serde.migration.V184SetteSkjæringstidspunktEldreVilkårsgrunnlag
import no.nav.helse.serde.migration.V185None
import no.nav.helse.serde.migration.V186None
import no.nav.helse.serde.migration.V187None
import no.nav.helse.serde.migration.V188None
import no.nav.helse.serde.migration.V189None
import no.nav.helse.serde.migration.V18UtbetalingstidslinjeØkonomi
import no.nav.helse.serde.migration.V190None
import no.nav.helse.serde.migration.V191None
import no.nav.helse.serde.migration.V192FjerneUtbetalingFraForkastetPeriode
import no.nav.helse.serde.migration.V193UtbetalingerOgVilkårsgrunnlag
import no.nav.helse.serde.migration.V194RefusjonsopplysningerIVilkårsgrunnlagDryRun
import no.nav.helse.serde.migration.V195RefusjonsopplysningerIVilkårsgrunnlagPrepp
import no.nav.helse.serde.migration.V196RefusjonsopplysningerIVilkårsgrunnlag
import no.nav.helse.serde.migration.V197SpissetVilkårsgrunnlagKopi
import no.nav.helse.serde.migration.V198GjenoppliveTidligereForkastet
import no.nav.helse.serde.migration.V199InfotrygdDefaultRefusjon
import no.nav.helse.serde.migration.V19KlippOverlappendeVedtaksperioder
import no.nav.helse.serde.migration.V1EndreKunArbeidsgiverSykedagEnum
import no.nav.helse.serde.migration.V200FikseStuckPeriode
import no.nav.helse.serde.migration.V201FjerneUbruktTilstand
import no.nav.helse.serde.migration.V202NullstilleSisteArbeidsgiverdag
import no.nav.helse.serde.migration.V203SpissetGjenopplivingAvTidligereForkastet
import no.nav.helse.serde.migration.V204GjenoppliveTidligereForkastet
import no.nav.helse.serde.migration.V205SpissetGjenopplivingAvTidligereForkastet
import no.nav.helse.serde.migration.V206GjenoppliveTidligereForkastet
import no.nav.helse.serde.migration.V207SpissetGjenopplivingAvTidligereForkastet
import no.nav.helse.serde.migration.V208GjenoppliveAUU
import no.nav.helse.serde.migration.V209SpissetVilkårsgrunnlagKopiMedAnnetSkjæringstidspunkt
import no.nav.helse.serde.migration.V20AvgrensVedtaksperiode
import no.nav.helse.serde.migration.V210EndreGamlePerioderMedKildeSykmelding
import no.nav.helse.serde.migration.V211SammenligningsgrunnlagBareSkatt
import no.nav.helse.serde.migration.V212FjerneRapporterteInntekterFraInntektshistorikk
import no.nav.helse.serde.migration.V213FlytteDatoTilSkattSykepengegrunnlag
import no.nav.helse.serde.migration.V214FjernerInfotrygdOgSkattSykepengegrunnlagFraInntektshistorikken
import no.nav.helse.serde.migration.V215TarBortInnslagFraInntektshistorikken
import no.nav.helse.serde.migration.V216FlytteHendelseIdOgTidsstempelTilSkattSykepengegrunnlag
import no.nav.helse.serde.migration.V217SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V218SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V219SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V21FjernGruppeId
import no.nav.helse.serde.migration.V220MigrerePeriodeForUtbetaling
import no.nav.helse.serde.migration.V221MigrerePeriodeForUtbetaling
import no.nav.helse.serde.migration.V222SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V223SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V224SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V225None
import no.nav.helse.serde.migration.V226SondereTrøbleteUtbetalinger
import no.nav.helse.serde.migration.V227ForkasteTrøbleteUtbetalinger
import no.nav.helse.serde.migration.V228None
import no.nav.helse.serde.migration.V229None
import no.nav.helse.serde.migration.V22FjernFelterFraSykdomstidslinje
import no.nav.helse.serde.migration.V230MigrereFeilaktigFomForUtbetaling
import no.nav.helse.serde.migration.V231NyttTilstandsnavnAvventerInntektsmelding
import no.nav.helse.serde.migration.V232AvsluttetUtenUtbetalingLåstePerioder
import no.nav.helse.serde.migration.V233RefusjonsopplysningerStarterPåSkjæringstidspunkt
import no.nav.helse.serde.migration.V234RefusjonsopplysningerStarterPåSkjæringstidspunkt
import no.nav.helse.serde.migration.V235OmgjortePerioderSomRevurderinger
import no.nav.helse.serde.migration.V236MigrereUtbetalingTilÅOverlappeMedAUU
import no.nav.helse.serde.migration.V237KopiereRefusjonsopplysningerTilEldreInnslag
import no.nav.helse.serde.migration.V238KobleSaksbehandlerinntekterTilDenOverstyrte
import no.nav.helse.serde.migration.V239KompensereManglendeOriginalInntekt
import no.nav.helse.serde.migration.V23None
import no.nav.helse.serde.migration.V240KopiereSykdomstidslinjeTilVedtaksperiodeutbetalinger
import no.nav.helse.serde.migration.V241DokumenttypeSomListe
import no.nav.helse.serde.migration.V242None
import no.nav.helse.serde.migration.V243None
import no.nav.helse.serde.migration.V244None
import no.nav.helse.serde.migration.V246None
import no.nav.helse.serde.migration.V247ForkasteOverlappende
import no.nav.helse.serde.migration.V248FinneOverlappendePerioder
import no.nav.helse.serde.migration.V249FlytteSammenligningsgrunnlagOgAvviksprosentInnISykepengegrunnlag
import no.nav.helse.serde.migration.V24None
import no.nav.helse.serde.migration.V250FikseAvvisteFeriepengeoppdrag
import no.nav.helse.serde.migration.V251None
import no.nav.helse.serde.migration.V252SykdomstidslinjeForForkastedeUtenSykdomstidslinje
import no.nav.helse.serde.migration.V253Sykepengegrunnlagtilstand
import no.nav.helse.serde.migration.V254FikseNavnPåTilstanderISykepengegrunnlaget
import no.nav.helse.serde.migration.V255SpissetMigreringForÅForkasteUtbetaling
import no.nav.helse.serde.migration.V256SpissetMigreringForÅForkastePeriode
import no.nav.helse.serde.migration.V257UtbetalingUtbetalingstidslinje
import no.nav.helse.serde.migration.V25ManglendeForlengelseFraInfotrygd
import no.nav.helse.serde.migration.V26SykdomshistorikkMerge
import no.nav.helse.serde.migration.V27CachetSykdomstidslinjePåVedtaksperiode
import no.nav.helse.serde.migration.V28HendelsesIderPåVedtaksperiode
import no.nav.helse.serde.migration.V29LeggerTilInntektsKildeType
import no.nav.helse.serde.migration.V2Medlemskapstatus
import no.nav.helse.serde.migration.V30AvviksprosentSomNullable
import no.nav.helse.serde.migration.V31LeggerTilInntektendringTidsstempel
import no.nav.helse.serde.migration.V32SletterForkastedePerioderUtenHistorikk
import no.nav.helse.serde.migration.V33BehovtypeAktivitetslogg
import no.nav.helse.serde.migration.V34OpprinneligPeriodePåVedtaksperiode
import no.nav.helse.serde.migration.V35ÅrsakTilForkasting
import no.nav.helse.serde.migration.V36BonkersNavnPåForkastedePerioder
import no.nav.helse.serde.migration.V37None
import no.nav.helse.serde.migration.V38InntektshistorikkVol2
import no.nav.helse.serde.migration.V39SetterAutomatiskBehandlingPåVedtaksperiode
import no.nav.helse.serde.migration.V3BeregnerGjenståendeSykedagerFraMaksdato
import no.nav.helse.serde.migration.V40RenamerFørsteFraværsdag
import no.nav.helse.serde.migration.V41RenamerBeregningsdato
import no.nav.helse.serde.migration.V42AnnulleringIUtbetaling
import no.nav.helse.serde.migration.V43RenamerSkjæringstidspunkt
import no.nav.helse.serde.migration.V44MaksdatoIkkeNullable
import no.nav.helse.serde.migration.V45InntektsmeldingId
import no.nav.helse.serde.migration.V46GamleAnnulleringsforsøk
import no.nav.helse.serde.migration.V47BeregnetUtbetalingstidslinjer
import no.nav.helse.serde.migration.V48OppdragTidsstempel
import no.nav.helse.serde.migration.V49UtbetalingId
import no.nav.helse.serde.migration.V4LeggTilNySykdomstidslinje
import no.nav.helse.serde.migration.V50None
import no.nav.helse.serde.migration.V51PatcheGamleAnnulleringer
import no.nav.helse.serde.migration.V52None
import no.nav.helse.serde.migration.V53KnytteVedtaksperiodeTilUtbetaling
import no.nav.helse.serde.migration.V54UtvideUtbetaling
import no.nav.helse.serde.migration.V55UtvideUtbetalingMedVurdering
import no.nav.helse.serde.migration.V56UtvideUtbetalingMedAvstemmingsnøkkel
import no.nav.helse.serde.migration.V57UtbetalingAvsluttet
import no.nav.helse.serde.migration.V58Utbetalingtype
import no.nav.helse.serde.migration.V59UtbetalingNesteForrige
import no.nav.helse.serde.migration.V5BegrensGradTilMellom0Og100
import no.nav.helse.serde.migration.V60FiksStatusPåUtbetalinger
import no.nav.helse.serde.migration.V61MigrereUtbetaltePerioderITilInfotrygd
import no.nav.helse.serde.migration.V62VurderingGodkjentBoolean
import no.nav.helse.serde.migration.V63EndreUtbetalingId
import no.nav.helse.serde.migration.V64None
import no.nav.helse.serde.migration.V65None
import no.nav.helse.serde.migration.V66SjekkeEtterutbetalingerForFeil
import no.nav.helse.serde.migration.V67FeilStatusOgTypePåAnnulleringer
import no.nav.helse.serde.migration.V68FikseØdelagteUtbetalinger
import no.nav.helse.serde.migration.V69SetteOpprettetOgOppdatertTidspunkt
import no.nav.helse.serde.migration.V6LeggTilNySykdomstidslinje
import no.nav.helse.serde.migration.V70SetteOppdatertTidspunkt
import no.nav.helse.serde.migration.V71SetteOpprettetTidspunkt
import no.nav.helse.serde.migration.V72RetteUtbetalingPåEnkeltperson
import no.nav.helse.serde.migration.V73MergeAvventerGapOgAvventerInntektsmeldingFerdigGap
import no.nav.helse.serde.migration.V74SykdomshistorikkElementId
import no.nav.helse.serde.migration.V75UtbetalingstidslinjeberegningId
import no.nav.helse.serde.migration.V76UtbetalingstidslinjeberegningSykdomshistorikkElementId
import no.nav.helse.serde.migration.V77UtbetalingBeregningId
import no.nav.helse.serde.migration.V78VedtaksperiodeListeOverUtbetalinger
import no.nav.helse.serde.migration.V79IdIInntektshistorikk
import no.nav.helse.serde.migration.V7DagsatsSomHeltall
import no.nav.helse.serde.migration.V80InntektskildePåVedtaksperiode
import no.nav.helse.serde.migration.V81ForkastetUtbetalinger
import no.nav.helse.serde.migration.V82VilkårsgrunnlagHistorikk
import no.nav.helse.serde.migration.V83VilkårsvurderingerForInfotrygdperioder
import no.nav.helse.serde.migration.V84Infotrygdhistorikk
import no.nav.helse.serde.migration.V85FjernerAvsluttetUtenUtbetalingMedInntektsmelding
import no.nav.helse.serde.migration.V86ForkastetPeriodeIFjernetState
import no.nav.helse.serde.migration.V87InfotrygdhistorikkStatslønn
import no.nav.helse.serde.migration.V88InfotrygdhistorikkInntekterLagret
import no.nav.helse.serde.migration.V89RetteOppFeilTilstand
import no.nav.helse.serde.migration.V8LeggerTilLønnIUtbetalingslinjer
import no.nav.helse.serde.migration.V90HendelsekildeTidsstempel
import no.nav.helse.serde.migration.V91AvvistDagerBegrunnelser
import no.nav.helse.serde.migration.V92VilkårsvurderingMinimumInntekt
import no.nav.helse.serde.migration.V93MeldingsreferanseIdPåGrunnlagsdata
import no.nav.helse.serde.migration.V94AvvistDagerBegrunnelser2
import no.nav.helse.serde.migration.V95ArbeidsforholdId
import no.nav.helse.serde.migration.V96RetteOppFeilUtbetalingPeker
import no.nav.helse.serde.migration.V97RenameDagsatsTilSats
import no.nav.helse.serde.migration.V98SletterITCacheMedUtbetalingsperioder
import no.nav.helse.serde.migration.V99LeggerTilSatstypePåUtbetalingslinjene
import no.nav.helse.serde.migration.V9FjernerGamleSykdomstidslinjer
import no.nav.helse.serde.migration.migrate

class SerialisertPerson(val json: String) {
    internal companion object {
        private val migrations = listOf(
            V1EndreKunArbeidsgiverSykedagEnum(),
            V2Medlemskapstatus(),
            V3BeregnerGjenståendeSykedagerFraMaksdato(),
            V4LeggTilNySykdomstidslinje(),
            V5BegrensGradTilMellom0Og100(),
            V6LeggTilNySykdomstidslinje(),
            V7DagsatsSomHeltall(),
            V8LeggerTilLønnIUtbetalingslinjer(),
            V9FjernerGamleSykdomstidslinjer(),
            V10EndreNavnPåSykdomstidslinjer(),
            V11LeggeTilForlengelseFraInfotrygd(),
            V12Aktivitetslogg(),
            V13NettoBeløpIOppdrag(),
            V14NettoBeløpIVedtaksperiode(),
            V15ØkonomiSykdomstidslinjer(),
            V16StatusIUtbetaling(),
            V17ForkastedePerioder(),
            V18UtbetalingstidslinjeØkonomi(),
            V19KlippOverlappendeVedtaksperioder(),
            V20AvgrensVedtaksperiode(),
            V21FjernGruppeId(),
            V22FjernFelterFraSykdomstidslinje(),
            V23None(),
            V24None(),
            V25ManglendeForlengelseFraInfotrygd(),
            V26SykdomshistorikkMerge(),
            V27CachetSykdomstidslinjePåVedtaksperiode(),
            V28HendelsesIderPåVedtaksperiode(),
            V29LeggerTilInntektsKildeType(),
            V30AvviksprosentSomNullable(),
            V31LeggerTilInntektendringTidsstempel(),
            V32SletterForkastedePerioderUtenHistorikk(),
            V33BehovtypeAktivitetslogg(),
            V34OpprinneligPeriodePåVedtaksperiode(),
            V35ÅrsakTilForkasting(),
            V36BonkersNavnPåForkastedePerioder(),
            V37None(),
            V38InntektshistorikkVol2(),
            V39SetterAutomatiskBehandlingPåVedtaksperiode(),
            V40RenamerFørsteFraværsdag(),
            V41RenamerBeregningsdato(),
            V42AnnulleringIUtbetaling(),
            V43RenamerSkjæringstidspunkt(),
            V44MaksdatoIkkeNullable(),
            V45InntektsmeldingId(),
            V46GamleAnnulleringsforsøk(),
            V47BeregnetUtbetalingstidslinjer(),
            V48OppdragTidsstempel(),
            V49UtbetalingId(),
            V50None(),
            V51PatcheGamleAnnulleringer(),
            V52None(),
            V53KnytteVedtaksperiodeTilUtbetaling(),
            V54UtvideUtbetaling(),
            V55UtvideUtbetalingMedVurdering(),
            V56UtvideUtbetalingMedAvstemmingsnøkkel(),
            V57UtbetalingAvsluttet(),
            V58Utbetalingtype(),
            V59UtbetalingNesteForrige(),
            V60FiksStatusPåUtbetalinger(),
            V61MigrereUtbetaltePerioderITilInfotrygd(),
            V62VurderingGodkjentBoolean(),
            V63EndreUtbetalingId(),
            V64None(),
            V65None(),
            V66SjekkeEtterutbetalingerForFeil(),
            V67FeilStatusOgTypePåAnnulleringer(),
            V68FikseØdelagteUtbetalinger(),
            V69SetteOpprettetOgOppdatertTidspunkt(),
            V70SetteOppdatertTidspunkt(),
            V71SetteOpprettetTidspunkt(),
            V72RetteUtbetalingPåEnkeltperson(),
            V73MergeAvventerGapOgAvventerInntektsmeldingFerdigGap(),
            V74SykdomshistorikkElementId(),
            V75UtbetalingstidslinjeberegningId(),
            V76UtbetalingstidslinjeberegningSykdomshistorikkElementId(),
            V77UtbetalingBeregningId(),
            V78VedtaksperiodeListeOverUtbetalinger(),
            V79IdIInntektshistorikk(),
            V80InntektskildePåVedtaksperiode(),
            V81ForkastetUtbetalinger(),
            V82VilkårsgrunnlagHistorikk(),
            V83VilkårsvurderingerForInfotrygdperioder(),
            V84Infotrygdhistorikk(),
            V85FjernerAvsluttetUtenUtbetalingMedInntektsmelding(),
            V86ForkastetPeriodeIFjernetState(),
            V87InfotrygdhistorikkStatslønn(),
            V88InfotrygdhistorikkInntekterLagret(),
            V89RetteOppFeilTilstand(),
            V90HendelsekildeTidsstempel(),
            V91AvvistDagerBegrunnelser(),
            V92VilkårsvurderingMinimumInntekt(),
            V93MeldingsreferanseIdPåGrunnlagsdata(),
            V94AvvistDagerBegrunnelser2(),
            V95ArbeidsforholdId(),
            V96RetteOppFeilUtbetalingPeker(),
            V97RenameDagsatsTilSats(),
            V98SletterITCacheMedUtbetalingsperioder(),
            V99LeggerTilSatstypePåUtbetalingslinjene(),
            V100SlettEnkeltFeriepengeutbetaling(),
            V101SlettEnkeltFeriepengeutbetaling(),
            V102LeggerFeriepengerSendTilOppdragFelt(),
            V103FjernerFeriepengeUtbetalingerMedTalletNullSomBeløp(),
            V104SlettOpphørMedFeilKlassekode(),
            V105VilkårsgrunnlagMedGenerasjoner(),
            V106FjernerTommeInnslagIVilkårsgrunnlagHistorikken(),
            V107None(),
            V108None(),
            V109UtvidetUtbetalingstidslinjeBeregning(),
            V110SletteArbeidsforhold(),
            V111RiktigStatusAnnullerteUtbetalinger(),
            V112FjernTrailingCarriageReturnFraFagsystemId(),
            V113MigrerVekkFraFjernetTilstandTilAnnullering(),
            V114LagreSykepengegrunnlag(),
            V115LagreVilkårsgrunnlagFraInfotrygd(),
            V116LeggTilRefusjonshistorikk(),
            V117LeggTilGradPåUgyldigeInfotrygdHistorikkPerioder(),
            V118LeggTilBegrensningPåSykepengegrunnlag(),
            V119SletteSkatteopplydsningFraITVilkårsgrunnlag(),
            V120BrukeInntektsmeldingOverSkattIVilkårsgrunnlag(),
            V121SletteVilkårsgrunnlagUtenNødvendigInntekt(),
            V122FikseNullverdierForAvstemmingsnøkkel(),
            V123FlytteDataInnIOppdrag(),
            V124SetteOppdragSomUendret(),
            V125KorrelasjonsIdPåUtbetaling(),
            V126FjernePaddingPåFagsystemId(),
            V127DefaultArbeidsgiverRefusjonsbeløp(),
            V128FjerneUgyldigSimuleringsResultat(),
            V129KopiereSimuleringsResultatTilOppdrag(),
            V130TrimmeUtbetalingstidslinje(),
            V131FjernHelgedagFraSykdomstidslinje(),
            V132None(),
            V133VilkårsgrunnlagIdPåVilkårsgrunnlag(),
            V134OppdragErSimulert(),
            V135UtbetalingslinjeGrad(),
            V136MigrereTilstanderPåForkastede(),
            V137InntektsmeldingInfoHistorikk(),
            V138SammenligningsgrunnlagPerArbeidsgiverIVilkårsgrunnlag(),
            V139EndreTommeInntektsopplysningerTilIkkeRapportert(),
            V140OppdaterFelterIArbeidsforhold(),
            V141RenameErAktivtTilDeaktivert(),
            V142DeaktiverteArbeidsforholdPåSykepengegrunnlag(),
            V143DummyMigrerHendelseInnPåVedtaksperiode(),
            V144TyperPåHendelserIVedtaksperiode(),
            V145DummyLagreArbeidsforholdForOpptjening(),
            V146DummyLagreArbeidsforholdForOpptjening(),
            V147LagreArbeidsforholdForOpptjening(),
            V148OpprettSykmeldingsperioder(),
            V149ResetLåstePerioder(),
            V150MigrerVedtaksperioderTilNyTilstandsflyt(),
            V151ResetLåstePerioder(),
            V152SlettGamleSykmeldingsperioder(),
            V153FjerneSykmeldingsdager(),
            V154FjerneProblemdager(),
            V155FjerneProblemdager(),
            V156None(),
            V157ManglerDagerPåSykdomstidslinjenDryRun(),
            V158LeggerTilPersonoppdragForFeriepenger(),
            V159None(),
            V160FikserDoubleGrad(),
            V161FikserDoubleGrad(),
            V162SletterDuplikaterFraInntektshistorikken(),
            V163ForkasteTommeVedtaksperioder(),
            V164ForkasteForkastedeVedtaksperioder(),
            V165TrimmerVedtaksperiode(),
            V166UtbetalteDagerMedForHøyAvviksprosent(),
            V167ProblemDagKilde(),
            V168Fødselsdato(),
            V169TildelingUtbetaling(),
            V170TildelingUtbetaling(),
            V171FjerneForkastedePerioderUtenSykdomstidslinje(),
            V172LoggeUbrukteVilkårsgrunnlag(),
            V173FjerneUbrukteVilkårsgrunnlag(),
            V174None(),
            V175IdPåAktiviteter(),
            V176LoggingAvForkastOgFlyttVilkårsgrunnlag(),
            V177LoggingAvForkastOgFlyttVilkårsgrunnlag(),
            V178ForkastOgFlyttVilkårsgrunnlag(),
            V179TildelingUtbetaling(),
            V180OverstyrteVilkårsgrunnlag(),
            V181FjerneUtbetaling(),
            V182OverstyrteVilkårsgrunnlag(),
            V183UtbetalingerOgVilkårsgrunnlag(),
            V184SetteSkjæringstidspunktEldreVilkårsgrunnlag(),
            V185None(),
            V186None(),
            V187None(),
            V188None(),
            V189None(),
            V190None(),
            V191None(),
            V192FjerneUtbetalingFraForkastetPeriode(),
            V193UtbetalingerOgVilkårsgrunnlag(),
            V194RefusjonsopplysningerIVilkårsgrunnlagDryRun(),
            V195RefusjonsopplysningerIVilkårsgrunnlagPrepp(),
            V196RefusjonsopplysningerIVilkårsgrunnlag(),
            V197SpissetVilkårsgrunnlagKopi(),
            V198GjenoppliveTidligereForkastet(),
            V199InfotrygdDefaultRefusjon(),
            V200FikseStuckPeriode(),
            V201FjerneUbruktTilstand(),
            V202NullstilleSisteArbeidsgiverdag(),
            V203SpissetGjenopplivingAvTidligereForkastet(),
            V204GjenoppliveTidligereForkastet(),
            V205SpissetGjenopplivingAvTidligereForkastet(),
            V206GjenoppliveTidligereForkastet(),
            V207SpissetGjenopplivingAvTidligereForkastet(),
            V208GjenoppliveAUU(),
            V209SpissetVilkårsgrunnlagKopiMedAnnetSkjæringstidspunkt(),
            V210EndreGamlePerioderMedKildeSykmelding(),
            V211SammenligningsgrunnlagBareSkatt(),
            V212FjerneRapporterteInntekterFraInntektshistorikk(),
            V213FlytteDatoTilSkattSykepengegrunnlag(),
            V214FjernerInfotrygdOgSkattSykepengegrunnlagFraInntektshistorikken(),
            V215TarBortInnslagFraInntektshistorikken(),
            V216FlytteHendelseIdOgTidsstempelTilSkattSykepengegrunnlag(),
            V217SpissetMigreringForÅForkasteUtbetaling(),
            V218SpissetMigreringForÅForkasteUtbetaling(),
            V219SpissetMigreringForÅForkasteUtbetaling(),
            V220MigrerePeriodeForUtbetaling(),
            V221MigrerePeriodeForUtbetaling(),
            V222SpissetMigreringForÅForkasteUtbetaling(),
            V223SpissetMigreringForÅForkasteUtbetaling(),
            V224SpissetMigreringForÅForkasteUtbetaling(),
            V225None(),
            V226SondereTrøbleteUtbetalinger(),
            V227ForkasteTrøbleteUtbetalinger(),
            V228None(),
            V229None(),
            V230MigrereFeilaktigFomForUtbetaling(),
            V231NyttTilstandsnavnAvventerInntektsmelding(),
            V232AvsluttetUtenUtbetalingLåstePerioder(),
            V233RefusjonsopplysningerStarterPåSkjæringstidspunkt(),
            V234RefusjonsopplysningerStarterPåSkjæringstidspunkt(),
            V235OmgjortePerioderSomRevurderinger(),
            V236MigrereUtbetalingTilÅOverlappeMedAUU(),
            V237KopiereRefusjonsopplysningerTilEldreInnslag(),
            V238KobleSaksbehandlerinntekterTilDenOverstyrte(),
            V239KompensereManglendeOriginalInntekt(),
            V240KopiereSykdomstidslinjeTilVedtaksperiodeutbetalinger(),
            V241DokumenttypeSomListe(),
            V242None(),
            V243None(),
            V244None(),
            V246None(),
            V247ForkasteOverlappende(),
            V248FinneOverlappendePerioder(),
            V249FlytteSammenligningsgrunnlagOgAvviksprosentInnISykepengegrunnlag(),
            V250FikseAvvisteFeriepengeoppdrag(),
            V251None(),
            V252SykdomstidslinjeForForkastedeUtenSykdomstidslinje(),
            V253Sykepengegrunnlagtilstand(),
            V254FikseNavnPåTilstanderISykepengegrunnlaget(),
            V255SpissetMigreringForÅForkasteUtbetaling(),
            V256SpissetMigreringForÅForkastePeriode(),
            V257UtbetalingUtbetalingstidslinje()
        )

        fun gjeldendeVersjon() = JsonMigration.gjeldendeVersjon(migrations)
        fun medSkjemaversjon(jsonNode: JsonNode) = JsonMigration.medSkjemaversjon(migrations, jsonNode)
    }

    val skjemaVersjon = gjeldendeVersjon()

    private fun migrate(jsonNode: JsonNode, meldingerSupplier: MeldingerSupplier) {
        try {
            migrations.migrate(jsonNode, meldingerSupplier)
        } catch (err: Exception) {
            throw JsonMigrationException("Feil under migrering: ${err.message}", err)
        }
    }

    fun deserialize(
        jurist: MaskinellJurist,
        tidligereBehandlinger: List<Person> = emptyList(),
        meldingerSupplier: MeldingerSupplier = MeldingerSupplier.empty
    ): Person {
        val jsonNode = serdeObjectMapper.readTree(json)
        migrate(jsonNode, meldingerSupplier)

        try {
            val personData: PersonData = requireNotNull(serdeObjectMapper.treeToValue(jsonNode))
            return personData.createPerson(jurist, tidligereBehandlinger)
        } catch (err: Exception) {
            val aktørId = jsonNode.path("aktørId").asText()
            throw DeserializationException("Feil under oversetting til modellobjekter for aktør=$aktørId: ${err.message}", err)
        }
    }
}
