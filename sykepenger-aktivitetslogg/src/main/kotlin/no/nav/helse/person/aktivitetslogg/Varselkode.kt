package no.nav.helse.person.aktivitetslogg

// Alle Varselkoder må følge formatet
const val varselkodeformat = "RV_\\D{2}_\\d{1,3}"
private val regex = "^$varselkodeformat$".toRegex()

enum class Varselkode(
    val varseltekst: String,
    val funksjonellFeilTekst: String = varseltekst,
    private val avviklet: Boolean = false
) {

    // SØ: Søknad
    RV_SØ_1("Søknaden inneholder permittering. Vurder om permittering har konsekvens for rett til sykepenger"),
    RV_SØ_2("Minst én dag er avslått på grunn av foreldelse. Vurder å sende vedtaksbrev fra Infotrygd"),
    RV_SØ_3("Sykmeldingen er tilbakedatert, vurder fra og med dato for utbetaling."),
    RV_SØ_5("Søknaden inneholder Permisjonsdager utenfor sykdomsvindu"),
    RV_SØ_7("Søknaden inneholder Arbeidsdager utenfor sykdomsvindu"),
    RV_SØ_8("Utenlandsopphold oppgitt i perioden i søknaden."),
    RV_SØ_10("Den sykmeldte har fått et nytt inntektsforhold."),
    RV_SØ_13("Overlappende søknad starter før, eller slutter etter, opprinnelig periode"),
    RV_SØ_17("Søker er ikke gammel nok på søknadstidspunktet til å søke sykepenger uten fullmakt fra verge"),
    RV_SØ_22("Søknaden inneholder en Papirsykmeldingsperiode"),
    RV_SØ_27("Mottatt søknad out of order innenfor 18 dager til neste"),
    RV_SØ_28("Søknad har mindre enn 20 dagers gap til en forkastet periode"),
    RV_SØ_29("Søknaden er opprettet fra en utenlandsk sykmelding"),
    RV_SØ_30("Søknaden er markert med flagget sendTilGosys"),
    RV_SØ_31("Søknad er før en forkastet vedtaksperiode hos samme arbeidsgiver"),
    RV_SØ_32("Søknad er før en forkastet vedtaksperiode hos annen arbeidsgiver"),
    RV_SØ_33("Søknad overlapper med en forkastet periode hos samme arbeidsgiver"),
    RV_SØ_34("Søknad overlapper med en forkastet periode hos annen arbeidsgiver"),
    RV_SØ_35("Søknad overlapper delvis med en forkastet periode hos samme arbeidsgiver"),
    RV_SØ_36("Søknad overlapper delvis med en forkastet periode hos annen arbeidsgiver"),
    RV_SØ_37("Søknad forlenger en forkastet periode hos samme arbeidsgiver"),
    RV_SØ_38("Søknad forlenger en forkastet periode hos annen arbeidsgiver"),

    RV_SØ_39("Støtter ikke søknadstypen"),
    RV_SØ_40("Støtter ikke søknadstypen for forlengelser vilkårsprøvd i Infotrygd"),

    // Arbeidsledig søknader
    RV_SØ_42("Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader"),
    RV_SØ_43("Arbeidsledigsøknad er lagt til grunn, undersøk refusjonsopplysningene før du utbetaler"),

    RV_SØ_44("I søknaden er det oppgitt at den sykmeldte har et arbeidsforhold som ikke er registrert i AA-registeret."),

    // OO: Out-of-order
    RV_OO_1("Det er behandlet en søknad i Speil for en senere periode enn denne."),
    RV_OO_2("Saken må revurderes fordi det har blitt behandlet en tidligere periode som kan ha betydning."),

    // IM: Inntektsmelding
    RV_IM_2("Første fraværsdag i inntektsmeldingen er ulik skjæringstidspunktet. Kontrollér at inntektsmeldingen er knyttet til riktig periode."),
    RV_IM_3("Inntektsmeldingen og vedtaksløsningen er uenige om beregningen av arbeidsgiverperioden. Undersøk hva som er riktig arbeidsgiverperiode."),
    RV_IM_4("Det er mottatt flere inntektsmeldinger på samme skjæringstidspunkt. Undersøk at arbeidsgiverperioden, sykepengegrunnlaget og refusjonsopplysningene er riktige"),
    RV_IM_5("Sykmeldte har oppgitt ferie første dag i arbeidsgiverperioden."),
    RV_IM_6("Inntektsmelding inneholder ikke beregnet inntekt"),
    RV_IM_7("Brukeren har opphold i naturalytelser"),
    RV_IM_8("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden"),
    RV_IM_22("Det er mottatt flere inntektsmeldinger på kort tid for samme arbeidsgiver"),
    RV_IM_23("Arbeidsgiver har oppgitt hullete arbeidsgiverperiode og begrunnelse for redusert utbetaling i arbeidsgiverperiode"),
    RV_IM_24("Det har kommet ny inntektsmelding, vurder om arbeidsgiverperiode er riktig"),
    RV_IM_25("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden grunnet ferie eller avspasering"),

    // ST: Sykdomstidslinje
    RV_ST_1("Sykdomstidslinjen inneholder ustøttet dag."),

    // RE: Refusjon
    RV_RE_1("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", avviklet = true),

    // IT: Infotrygd
    RV_IT_1("Det er utbetalt en periode i Infotrygd etter perioden du skal behandle nå. Undersøk at antall forbrukte dager og grunnlag i Infotrygd er riktig", funksjonellFeilTekst = "Det er utbetalt en nyere periode i Infotrygd"),
    RV_IT_3("Utbetaling i Infotrygd overlapper med vedtaksperioden"), // funksjonellFeil
    RV_IT_13("Støtter ikke overgang fra infotrygd for flere arbeidsgivere", avviklet = true),
    RV_IT_14("Forlenger en Infotrygdperiode på tvers av arbeidsgivere"),
    RV_IT_33("Skjæringstidspunktet har endret seg som følge av historikk fra Infotrygd"),
    RV_IT_36("Periode som før var uten utbetalingsdager har nå utbetalingsdager etter endringer fra Infotrygd"),
    RV_IT_37("Det er en utbetaling i Infotrygd nærmere enn 18 dager"),
    RV_IT_38("En utbetaling i Infotrygd har medført at perioden nå vil utbetales"),

    // VV: Vilkårsvurdering
    RV_VV_1("Bruker har ikke registrert opptjening hos arbeidsgiver"),
    RV_VV_2("Flere arbeidsgivere, ulikt starttidspunkt for sykefraværet eller ikke fravær fra alle arbeidsforhold"),
    RV_VV_4("Minst én dag uten utbetaling på grunn av sykdomsgrad under 20 %. Vurder å sende vedtaksbrev fra Infotrygd"),
    RV_VV_5("Bruker mangler nødvendig inntekt ved validering av Vilkårsgrunnlag", avviklet = true),
    RV_VV_8("Den sykmeldte har skiftet arbeidsgiver, og det er beregnet at den nye arbeidsgiveren mottar refusjon lik forrige. Kontroller at dagsatsen blir riktig."),
    RV_VV_9("Bruker er fortsatt syk 26 uker etter maksdato"),
    RV_VV_10("Fant ikke vilkårsgrunnlag. Kan ikke vilkårsprøve på nytt etter ny informasjon fra saksbehandler."),
    RV_VV_12("Kan ikke overstyre inntekt uten at det foreligger et vilkårsgrunnlag"),

    // VV: Opptjeningsvurdering
    RV_OV_1("Perioden er avslått på grunn av manglende opptjening"),
    RV_OV_3("Det er ikke registrert inntekt i måneden før skjæringstidspunktet"),

    // MV: Medlemskapsvurdering
    RV_MV_1("Vurder lovvalg og medlemskap", avviklet = true),
    RV_MV_2("Perioden er avslått på grunn av at den sykmeldte ikke er medlem av Folketrygden"),
    RV_MV_3("Arbeid utenfor Norge oppgitt i søknaden"),

    // IV: Inntektsvurdering
    @Deprecated("Denne skal ikke være i bruk i Spleis mer, brukes av spinnvill")
    RV_IV_2("Har mer enn 25 % avvik. Dette støttes foreløpig ikke i Speil. Du må derfor annullere periodene.", funksjonellFeilTekst = "Har mer enn 25 % avvik"),
    RV_IV_3("Fant frilanserinntekt på en arbeidsgiver de siste 3 månedene"),
    RV_IV_7("Det er gjenbrukt inntektsopplysninger "),
    RV_IV_8("Perioden har flere skjæringstidspunkter og det er dager hvor inntekten derfor er satt til 0 kr"),

    // SV: Sykepengegrunnlagsvurdering
    RV_SV_1("Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag"),
    RV_SV_2("Minst en arbeidsgiver inngår ikke i sykepengegrunnlaget"),
    RV_SV_3("Mangler inntekt for sykepengegrunnlag som følge av at skjæringstidspunktet har endret seg", avviklet = true),
    RV_SV_5("Det har tilkommet nye inntekter"),

    // AY: Andre ytelser
    RV_AY_3("Bruker har mottatt AAP innenfor 6 måneder før skjæringstidspunktet. Kontroller at brukeren har rett til sykepenger"),
    RV_AY_4("Bruker har mottatt dagpenger innenfor 4 uker før skjæringstidspunktet. Kontroller om bruker er dagpengemottaker. Kombinerte ytelser støttes foreløpig ikke av systemet"),
    RV_AY_5("Det er utbetalt foreldrepenger i samme periode."),
    RV_AY_6("Det er utbetalt pleiepenger i samme periode."),
    RV_AY_7("Det er utbetalt omsorgspenger i samme periode."),
    RV_AY_8("Det er utbetalt opplæringspenger i samme periode."),
    RV_AY_9("Det er institusjonsopphold i perioden. Vurder retten til sykepenger."),
    RV_AY_11("Det er utbetalt svangerskapspenger i samme periode."),

    // SI: Simulering
    RV_SI_1("Feil under simulering", avviklet = true),
    RV_SI_3("Det er simulert et negativt beløp."),

    // UT: Utbetaling
    RV_UT_1("Utbetaling av revurdert periode ble avvist av saksbehandler. Utbetalingen må annulleres", avviklet = true),
    RV_UT_2("Utbetalingen ble gjennomført, men med advarsel"),
    RV_UT_3("Feil ved utbetalingstidslinjebygging"),
    RV_UT_5("Utbetaling ble ikke gjennomført"),
    RV_UT_6("Forventet ikke å opprette utbetaling"),
    RV_UT_7("Forventet ikke godkjenning på utbetaling"),
    RV_UT_9("Forventet ikke å annullere på utbetaling"),
    RV_UT_11("Forventet ikke kvittering på utbetaling"),
    RV_UT_12("Forventet ikke simulering på utbetaling"),
    RV_UT_13("Forventet ikke å lage godkjenning på utbetaling"),
    RV_UT_18("Utbetaling markert som ikke godkjent automatisk"),
    RV_UT_19("Utbetaling markert som ikke godkjent av saksbehandler"),
    RV_UT_21("Utbetaling opphører tidligere utbetaling. Kontroller simuleringen"),
    RV_UT_23("Den nye beregningen vil trekke penger i sykefraværstilfellet"),
    RV_UT_24("Utbetalingen er avvist, men perioden kan ikke forkastes. Overstyr perioden, eller annuller sykefraværstilfellet om nødvendig"),

    // OS: Oppdragsystemet
    RV_OS_2("Utbetalingens fra og med-dato er endret. Kontroller simuleringen"),
    RV_OS_3("Endrer tidligere oppdrag. Kontroller simuleringen."),

    // RV: Revurdering
    RV_RV_1("Denne perioden var tidligere regnet som innenfor arbeidsgiverperioden"),
    RV_RV_2("Forkaster avvist revurdering ettersom vedtaksperioden ikke har tidligere utbetalte utbetalinger."),
    RV_RV_3("Forespurt revurdering av inntekt hvor personen har flere arbeidsgivere (inkl. ghosts)", avviklet= true),
    RV_RV_7("En tidligere periode er annullert. Den må være utbetalt i Infotrygd før denne perioden kan behandles."),

    // VT: Vedtaksperiodetilstand
    RV_VT_1("Gir opp fordi tilstanden er nådd makstid"),

    RV_AG_1("Finner ikke arbeidsgiver"),

    //AN: Annet
    RV_AN_5("Personen har blitt behandlet på en tidligere ident"),

    // YS: Yrkesskade
    RV_YS_1("Yrkesskade oppgitt i søknaden")
    ;

    init {
        require(this.name.matches(regex)) {"Ugyldig varselkode-format: ${this.name}"}
    }

    internal fun varsel(kontekster: List<SpesifikkKontekst>): Aktivitet.Varsel =
        Aktivitet.Varsel.opprett(kontekster, this, varseltekst)
    internal fun funksjonellFeil(kontekster: List<SpesifikkKontekst>): Aktivitet.FunksjonellFeil =
        Aktivitet.FunksjonellFeil.opprett(kontekster, this, funksjonellFeilTekst)

    override fun toString() = "${this.name}: $varseltekst"

    companion object {
        val `Mottatt søknad som delvis overlapper` = RV_SØ_13

        val `Overlapper med foreldrepenger` = RV_AY_5
        val `Overlapper med pleiepenger` = RV_AY_6
        val `Overlapper med omsorgspenger` = RV_AY_7
        val `Overlapper med opplæringspenger` = RV_AY_8
        val `Overlapper med institusjonsopphold` = RV_AY_9
        val `Overlapper med svangerskapspenger` = RV_AY_11


        val `Støtter ikke søknadstypen` = RV_SØ_39
        val `Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader` = RV_SØ_42
        val `Arbeidsledigsøknad er lagt til grunn` =  RV_SØ_43

        fun IAktivitetslogg.varsel(varselkode: Varselkode, detaljer: String) {
            varsel(varselkode)
            info("${varselkode.name} detaljer: $detaljer")
        }
    }
}
