package no.nav.helse.serde

import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.Alder
import no.nav.helse.erHelg
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SimuleringResultat
import no.nav.helse.hendelser.Subsumsjon
import no.nav.helse.hendelser.til
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.ForlengelseFraInfotrygd
import no.nav.helse.person.InntektsmeldingInfo
import no.nav.helse.person.InntektsmeldingInfoHistorikk
import no.nav.helse.person.Opptjening
import no.nav.helse.person.Person
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.TilstandType
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VedtaksperiodeUtbetalinger
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.infotrygdhistorikk.PersonUtbetalingsperiode
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag
import no.nav.helse.person.inntekt.IkkeRapportert
import no.nav.helse.person.inntekt.Infotrygd
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.Companion.gjennopprett
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag
import no.nav.helse.person.inntekt.Skatteopplysning
import no.nav.helse.person.inntekt.SkjønnsmessigFastsatt
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.serde.PersonData.ArbeidsgiverData.InntektsmeldingInfoHistorikkElementData.Companion.finn
import no.nav.helse.serde.PersonData.ArbeidsgiverData.InntektsmeldingInfoHistorikkElementData.Companion.tilInntektsmeldingInfoHistorikk
import no.nav.helse.serde.PersonData.ArbeidsgiverData.RefusjonData.Companion.parseRefusjon
import no.nav.helse.serde.PersonData.ArbeidsgiverData.RefusjonData.EndringIRefusjonData.Companion.parseEndringerIRefusjon
import no.nav.helse.serde.PersonData.ArbeidsgiverData.VedtaksperiodeData.DokumentsporingData.Companion.tilSporing
import no.nav.helse.serde.PersonData.ArbeidsgiverData.VedtaksperiodeData.VedtaksperiodeUtbetalingData.Companion.tilModellobjekt
import no.nav.helse.serde.PersonData.InfotrygdhistorikkElementData.Companion.tilModellObjekt
import no.nav.helse.serde.PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningData.Companion.parseArbeidsgiverInntektsopplysninger
import no.nav.helse.serde.PersonData.VilkårsgrunnlagElementData.ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData.Companion.parseArbeidsgiverInntektsopplysninger
import no.nav.helse.serde.PersonData.VilkårsgrunnlagElementData.OpptjeningData.ArbeidsgiverOpptjeningsgrunnlagData.Companion.tilArbeidsgiverOpptjeningsgrunnlag
import no.nav.helse.serde.PersonData.VilkårsgrunnlagInnslagData.Companion.tilModellObjekt
import no.nav.helse.serde.mapping.JsonMedlemskapstatus
import no.nav.helse.somPersonidentifikator
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
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner.UtbetaltDag.InfotrygdArbeidsgiver
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner.UtbetaltDag.InfotrygdPerson
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner.UtbetaltDag.SpleisArbeidsgiver
import no.nav.helse.utbetalingstidslinje.Feriepengeberegner.UtbetaltDag.SpleisPerson
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinjeberegning
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi

internal data class PersonData(
    private val aktørId: String,
    private val fødselsnummer: String,
    private val fødselsdato: LocalDate,
    private val arbeidsgivere: List<ArbeidsgiverData>,
    private val opprettet: LocalDateTime,
    private val infotrygdhistorikk: List<InfotrygdhistorikkElementData>,
    private val vilkårsgrunnlagHistorikk: List<VilkårsgrunnlagInnslagData>,
    private val dødsdato: LocalDate?
) {
    private val arbeidsgivereliste = mutableListOf<Arbeidsgiver>()
    private val fnr by lazy { fødselsnummer.somPersonidentifikator() }
    private val alder by lazy { Alder(fødselsdato, dødsdato) }
    private val vilkårsgrunnlaghistorikkBuilder = VilkårsgrunnlaghistorikkBuilder(vilkårsgrunnlagHistorikk)

    private fun person(jurist: MaskinellJurist, tidligereBehandlinger: List<Person> = emptyList()) = Person.ferdigPerson(
        aktørId = aktørId,
        personidentifikator = fnr,
        alder = alder,
        arbeidsgivere = arbeidsgivereliste,
        aktivitetslogg = Aktivitetslogg(),
        opprettet = opprettet,
        infotrygdhistorikk = infotrygdhistorikk.tilModellObjekt(),
        vilkårsgrunnlaghistorikk = vilkårsgrunnlaghistorikkBuilder.build(alder),
        tidligereBehandlinger = tidligereBehandlinger,
        jurist = jurist
    )

    internal fun createPerson(jurist: MaskinellJurist, tidligereBehandlinger: List<Person> = emptyList()): Person {
        val personJurist = jurist.medFødselsnummer(fødselsnummer.somPersonidentifikator().toString())
        val person = person(personJurist, tidligereBehandlinger)
        arbeidsgivereliste.addAll(this.arbeidsgivere.map {
            it.konverterTilArbeidsgiver(
                person,
                this.aktørId,
                this.fødselsnummer,
                this.alder,
                vilkårsgrunnlaghistorikkBuilder::hentVilkårsgrunnlagelement,
                personJurist
            )
        })
        return person
    }

    data class InfotrygdhistorikkElementData(
        private val id: UUID,
        private val tidsstempel: LocalDateTime,
        private val hendelseId: UUID?,
        private val ferieperioder: List<FerieperiodeData>,
        private val arbeidsgiverutbetalingsperioder: List<ArbeidsgiverutbetalingsperiodeData>,
        private val personutbetalingsperioder: List<PersonutbetalingsperiodeData>,
        private val inntekter: List<InntektsopplysningData>,
        private val arbeidskategorikoder: Map<String, LocalDate>,
        private val oppdatert: LocalDateTime
    ) {
        internal companion object {
            fun List<InfotrygdhistorikkElementData>.tilModellObjekt() =
                Infotrygdhistorikk.ferdigInfotrygdhistorikk(map { it.parseInfotrygdhistorikkElement() })
        }

        internal fun parseInfotrygdhistorikkElement() = InfotrygdhistorikkElement.ferdigElement(
            id = id,
            tidsstempel = tidsstempel,
            hendelseId = hendelseId,
            infotrygdperioder = arbeidsgiverutbetalingsperioder.map { it.parsePeriode() }
                    + personutbetalingsperioder.map { it.parsePeriode() }
                    + ferieperioder.map { it.parsePeriode() },
            inntekter = inntekter.map { it.parseInntektsopplysning() },
            arbeidskategorikoder = arbeidskategorikoder,
            oppdatert = oppdatert
        )

        data class FerieperiodeData(
            private val fom: LocalDate,
            private val tom: LocalDate
        ) {
            internal fun parsePeriode() = Friperiode(fom, tom)
        }

        data class PersonutbetalingsperiodeData(
            private val orgnr: String,
            private val fom: LocalDate,
            private val tom: LocalDate,
            private val grad: Int,
            private val inntekt: Double
        ) {
            internal fun parsePeriode() = PersonUtbetalingsperiode(
                orgnr = orgnr,
                fom = fom,
                tom = tom,
                grad = grad.prosent,
                inntekt = inntekt.månedlig
            )
        }

        data class ArbeidsgiverutbetalingsperiodeData(
            private val orgnr: String,
            private val fom: LocalDate,
            private val tom: LocalDate,
            private val grad: Int,
            private val inntekt: Double
        ) {
            internal fun parsePeriode() = ArbeidsgiverUtbetalingsperiode(
                orgnr = orgnr,
                fom = fom,
                tom = tom,
                grad = grad.prosent,
                inntekt = inntekt.månedlig
            )
        }

        data class InntektsopplysningData(
            private val orgnr: String,
            private val sykepengerFom: LocalDate,
            private val inntekt: Double,
            private val refusjonTilArbeidsgiver: Boolean,
            private val refusjonTom: LocalDate?,
            private val lagret: LocalDateTime?
        ) {
            internal fun parseInntektsopplysning() = Inntektsopplysning.ferdigInntektsopplysning(
                orgnummer = orgnr,
                sykepengerFom = sykepengerFom,
                inntekt = inntekt.månedlig,
                refusjonTilArbeidsgiver = refusjonTilArbeidsgiver,
                refusjonTom = refusjonTom,
                lagret = lagret
            )
        }
    }

    internal class VilkårsgrunnlaghistorikkBuilder(private val historikk: List<VilkårsgrunnlagInnslagData>) {
        private val inntekter = mutableMapOf<UUID, no.nav.helse.person.inntekt.Inntektsopplysning>()
        private val elementer = mutableMapOf<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>()

        internal fun registrerVilkårsgrunnlagElement(id: UUID, element: () -> VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement) =
            elementer.getOrPut(id, element)
        internal fun registrerInntekt(id: UUID, inntektsopplysning: () -> no.nav.helse.person.inntekt.Inntektsopplysning) =
            inntekter.getOrPut(id, inntektsopplysning)

        fun build(alder: Alder) =
            historikk.tilModellObjekt(this, alder)

        internal fun hentVilkårsgrunnlagelement(id: UUID) = elementer.getValue(id)
        internal fun hentInntekt(id: UUID) = inntekter.getValue(id)
    }

    data class VilkårsgrunnlagInnslagData(
        private val id: UUID,
        private val opprettet: LocalDateTime,
        private val vilkårsgrunnlag: List<VilkårsgrunnlagElementData>
    ) {
        private fun tilModellobjekt(builder: VilkårsgrunnlaghistorikkBuilder, alder: Alder) = id to VilkårsgrunnlagHistorikk.Innslag.gjenopprett(
            id = id,
            opprettet = opprettet,
            elementer = vilkårsgrunnlag.associate { it.parseDataForVilkårsvurdering(builder, alder) }
        )

        internal companion object {
            private fun List<VilkårsgrunnlagInnslagData>.parseVilkårsgrunnlag(builder: VilkårsgrunnlaghistorikkBuilder, alder: Alder): List<VilkårsgrunnlagHistorikk.Innslag> {
                // parser vilkårsgrunnlag fra eldste innslag (bakerst i listen) til nyeste (først i listen) fordi nyere inntekter kan peke på eldre inntekter (overstyringer)
                val mellombelsResultat = this
                    .asReversed()
                    .map { innslagData -> innslagData.tilModellobjekt(builder, alder) }
                    // snu rekkefølgen tilbake igjen
                    .reversed()
                this.bekreftRekkefølgeErIvaretatt(mellombelsResultat.map { it.first })
                return mellombelsResultat.map { it.second }
            }

            private fun List<VilkårsgrunnlagInnslagData>.bekreftRekkefølgeErIvaretatt(rekkefølge: List<UUID>) {
                check(this.size == rekkefølge.size) { "antall innslag har blitt endret" }
                check(this.zip(rekkefølge) { a, b -> a.id == b }.all { it }) { "rekkefølgen har endret seg, minst ett innslag ligger feil!" }
            }

            internal fun List<VilkårsgrunnlagInnslagData>.tilModellObjekt(builder: VilkårsgrunnlaghistorikkBuilder, alder: Alder) = VilkårsgrunnlagHistorikk.ferdigVilkårsgrunnlagHistorikk(parseVilkårsgrunnlag(builder, alder))
        }
    }

    data class VilkårsgrunnlagElementData(
        private val skjæringstidspunkt: LocalDate,
        private val type: GrunnlagsdataType,
        private val sykepengegrunnlag: SykepengegrunnlagData,
        private val sammenligningsgrunnlag: SammenligningsgrunnlagData?,
        private val avviksprosent: Double?,
        private val opptjening: OpptjeningData?,
        private val medlemskapstatus: JsonMedlemskapstatus?,
        private val vurdertOk: Boolean?,
        private val meldingsreferanseId: UUID?,
        private val vilkårsgrunnlagId: UUID
    ) {
        internal fun parseDataForVilkårsvurdering(
            builder: VilkårsgrunnlaghistorikkBuilder,
            alder: Alder,
        ) = skjæringstidspunkt to builder.registrerVilkårsgrunnlagElement(vilkårsgrunnlagId) {
            when (type) {
                GrunnlagsdataType.Vilkårsprøving -> VilkårsgrunnlagHistorikk.Grunnlagsdata(
                    skjæringstidspunkt = skjæringstidspunkt,
                    sykepengegrunnlag = sykepengegrunnlag.parseSykepengegrunnlag(builder, alder, skjæringstidspunkt),
                    opptjening = opptjening!!.tilOpptjening(skjæringstidspunkt),
                    medlemskapstatus = when (medlemskapstatus!!) {
                        JsonMedlemskapstatus.JA -> Medlemskapsvurdering.Medlemskapstatus.Ja
                        JsonMedlemskapstatus.NEI -> Medlemskapsvurdering.Medlemskapstatus.Nei
                        JsonMedlemskapstatus.VET_IKKE -> Medlemskapsvurdering.Medlemskapstatus.VetIkke
                    },
                    vurdertOk = vurdertOk!!,
                    meldingsreferanseId = meldingsreferanseId,
                    vilkårsgrunnlagId = vilkårsgrunnlagId
                )
                GrunnlagsdataType.Infotrygd -> VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag(
                    skjæringstidspunkt = skjæringstidspunkt,
                    sykepengegrunnlag = sykepengegrunnlag.parseSykepengegrunnlagInfotrygd(builder, alder, skjæringstidspunkt),
                    vilkårsgrunnlagId = vilkårsgrunnlagId
                )
            }
        }

        enum class GrunnlagsdataType {
            Infotrygd,
            Vilkårsprøving
        }

        data class SykepengegrunnlagData(
            private val grunnbeløp: Double?,
            private val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningData>,
            private val sammenligningsgrunnlag: SammenligningsgrunnlagData?,
            private val deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysningData>,
            private val vurdertInfotrygd: Boolean,
            private val tilstand: TilstandData
        ) {
            enum class TilstandData {
                FASTSATT_ETTER_HOVEDREGEL,
                AVVENTER_FASTSETTELSE_ETTER_SKJØNN,
                FASTSATT_ETTER_SKJØNN
            }

            internal fun parseSykepengegrunnlag(
                builder: VilkårsgrunnlaghistorikkBuilder,
                alder: Alder,
                skjæringstidspunkt: LocalDate
            ) = Sykepengegrunnlag.ferdigSykepengegrunnlag(
                alder = alder,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger.parseArbeidsgiverInntektsopplysninger(builder),
                sammenligningsgrunnlag = sammenligningsgrunnlag!!.parseSammenligningsgrunnlag(),
                deaktiverteArbeidsforhold = deaktiverteArbeidsforhold.parseArbeidsgiverInntektsopplysninger(builder),
                vurdertInfotrygd = vurdertInfotrygd,
                `6G` = grunnbeløp?.årlig,
                tilstand = when (tilstand) {
                    TilstandData.FASTSATT_ETTER_HOVEDREGEL -> Sykepengegrunnlag.FastsattEtterHovedregel
                    TilstandData.AVVENTER_FASTSETTELSE_ETTER_SKJØNN -> Sykepengegrunnlag.AvventerFastsettelseEtterSkjønn
                    TilstandData.FASTSATT_ETTER_SKJØNN -> Sykepengegrunnlag.FastsattEtterSkjønn
                }
            )

            internal fun parseSykepengegrunnlagInfotrygd(
                builder: VilkårsgrunnlaghistorikkBuilder,
                alder: Alder,
                skjæringstidspunkt: LocalDate
            ) = Sykepengegrunnlag.ferdigSykepengegrunnlag(
                alder = alder,
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger.parseArbeidsgiverInntektsopplysninger(builder),
                sammenligningsgrunnlag = Sammenligningsgrunnlag(emptyList()),
                deaktiverteArbeidsforhold = deaktiverteArbeidsforhold.parseArbeidsgiverInntektsopplysninger(builder),
                vurdertInfotrygd = vurdertInfotrygd,
                `6G` = grunnbeløp?.årlig,
                tilstand = Sykepengegrunnlag.FastsattEtterHovedregel
            )
        }

        data class SammenligningsgrunnlagData(
            private val sammenligningsgrunnlag: Double,
            private val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData>,
        ) {

            internal fun parseSammenligningsgrunnlag(): Sammenligningsgrunnlag = Sammenligningsgrunnlag(
                sammenligningsgrunnlag.årlig,
                arbeidsgiverInntektsopplysninger.parseArbeidsgiverInntektsopplysninger()
            )
        }

        data class ArbeidsgiverInntektsopplysningData(
            private val orgnummer: String,
            private val inntektsopplysning: InntektsopplysningData,
            private val refusjonsopplysninger: List<ArbeidsgiverData.RefusjonsopplysningData>
        ) {
            companion object {
                private fun List<ArbeidsgiverData.RefusjonsopplysningData>.tilModellobjekt() =
                    map { it.tilModellobjekt() }

                internal fun List<ArbeidsgiverInntektsopplysningData>.parseArbeidsgiverInntektsopplysninger(builder: VilkårsgrunnlaghistorikkBuilder) =
                    map {
                        ArbeidsgiverInntektsopplysning(it.orgnummer, it.inntektsopplysning.tilModellobjekt(builder), it.refusjonsopplysninger.tilModellobjekt().gjennopprett())
                    }
            }

            data class SkatteopplysningData(
                private val hendelseId: UUID,
                private val beløp: Double,
                private val måned: YearMonth,
                private val type: String,
                private val fordel: String,
                private val beskrivelse: String,
                private val tidsstempel: LocalDateTime
            ) {
                internal fun tilModellobjekt() = Skatteopplysning(hendelseId, beløp.månedlig, måned, enumValueOf(type), fordel, beskrivelse, tidsstempel)
            }

            data class InntektsopplysningData(
                private val id: UUID,
                private val dato: LocalDate,
                private val hendelseId: UUID?,
                private val beløp: Double?,
                private val kilde: String,
                private val forklaring: String?,
                private val subsumsjon: SubsumsjonData?,
                private val tidsstempel: LocalDateTime,
                private val overstyrtInntektId: UUID?,
                private val skatteopplysninger: List<SkatteopplysningData>?
            ) {
                private companion object {
                    private val IKKE_MIGRERT_HENDELSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000")
                }

                private val ekteHendelseId = hendelseId ?: IKKE_MIGRERT_HENDELSE_ID // TODO: migrere json slik at alle inntektsopplysninger har hendelseId (det er bare IKKE_RAPPORTERT)

                data class SubsumsjonData(
                    private val paragraf: String,
                    private val ledd: Int?,
                    private val bokstav: String?
                ) {
                    internal fun tilModellobjekt() = Subsumsjon(paragraf, ledd, bokstav)
                }
                internal fun tilModellobjekt(builder: VilkårsgrunnlaghistorikkBuilder): no.nav.helse.person.inntekt.Inntektsopplysning {
                    val opplysning = builder.registrerInntekt(id) {
                        when (kilde.let(Inntektsopplysningskilde::valueOf)) {
                            Inntektsopplysningskilde.INFOTRYGD -> somInfotrygd()
                            Inntektsopplysningskilde.INNTEKTSMELDING -> somInntektsmelding()
                            Inntektsopplysningskilde.IKKE_RAPPORTERT -> somIkkeRapportert()
                            Inntektsopplysningskilde.SAKSBEHANDLER -> somSaksbehandler(builder)
                            Inntektsopplysningskilde.SKJØNNSMESSIG_FASTSATT -> somSkjønnsmessigFastsatt(builder)
                            Inntektsopplysningskilde.SKATT_SYKEPENGEGRUNNLAG -> somSkattSykepengegrunnlag()
                            else -> error("Fant ${kilde}. Det er ugyldig for sykepengegrunnlag")
                        }
                    }
                    return opplysning
                }

                private fun somSaksbehandler(builder: VilkårsgrunnlaghistorikkBuilder) =
                    Saksbehandler(
                        id = id,
                        dato = dato,
                        hendelseId = ekteHendelseId,
                        beløp = requireNotNull(beløp).månedlig,
                        forklaring = forklaring,
                        subsumsjon = subsumsjon?.tilModellobjekt(),
                        overstyrtInntekt = builder.hentInntekt(requireNotNull(overstyrtInntektId)),
                        tidsstempel = tidsstempel
                    )
                private fun somSkjønnsmessigFastsatt(builder: VilkårsgrunnlaghistorikkBuilder) =
                    SkjønnsmessigFastsatt(
                        id = id,
                        dato = dato,
                        hendelseId = ekteHendelseId,
                        beløp = requireNotNull(beløp).månedlig,
                        overstyrtInntekt = builder.hentInntekt(requireNotNull(overstyrtInntektId)),
                        tidsstempel = tidsstempel
                    )
                private fun somIkkeRapportert() = IkkeRapportert(
                    id = id,
                    hendelseId = ekteHendelseId,
                    dato = dato,
                    tidsstempel = tidsstempel
                )
                private fun somInntektsmelding() = Inntektsmelding(
                    id = id,
                    dato = dato,
                    hendelseId = ekteHendelseId,
                    beløp = requireNotNull(beløp).månedlig,
                    tidsstempel = tidsstempel
                )
                private fun somInfotrygd() = Infotrygd(
                    id = id,
                    dato = dato,
                    hendelseId = ekteHendelseId,
                    beløp = requireNotNull(beløp).månedlig,
                    tidsstempel = tidsstempel
                )
                private fun somSkattSykepengegrunnlag() = SkattSykepengegrunnlag.ferdigSkattSykepengegrunnlag(
                    id = id,
                    dato = dato,
                    inntektsopplysninger = requireNotNull(skatteopplysninger).map { skatteData ->
                        skatteData.tilModellobjekt()
                    },
                    ansattPerioder = emptyList(),
                    tidsstempel = tidsstempel,
                    hendelseId = ekteHendelseId
                )
            }
        }

        data class ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData(
            private val orgnummer: String,
            private val skatteopplysninger: List<SammenligningsgrunnlagInntektsopplysningData>
        ) {
            companion object {
                internal fun List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlagData>.parseArbeidsgiverInntektsopplysninger(): List<ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag> =
                    map {
                        ArbeidsgiverInntektsopplysningForSammenligningsgrunnlag(it.orgnummer, it.skatteopplysninger.map { it.tilModellobjekt() })
                    }
            }
            data class SammenligningsgrunnlagInntektsopplysningData(
                private val hendelseId: UUID,
                private val beløp: Double,
                private val måned: YearMonth,
                private val type: InntekttypeData,
                private val fordel: String,
                private val beskrivelse: String,
                private val tidsstempel: LocalDateTime,
            ) {
                internal enum class InntekttypeData {
                    LØNNSINNTEKT,
                    NÆRINGSINNTEKT,
                    PENSJON_ELLER_TRYGD,
                    YTELSE_FRA_OFFENTLIGE;

                    fun tilModellenum() = when(this) {
                        LØNNSINNTEKT -> Skatteopplysning.Inntekttype.LØNNSINNTEKT
                        NÆRINGSINNTEKT -> Skatteopplysning.Inntekttype.NÆRINGSINNTEKT
                        PENSJON_ELLER_TRYGD -> Skatteopplysning.Inntekttype.PENSJON_ELLER_TRYGD
                        YTELSE_FRA_OFFENTLIGE -> Skatteopplysning.Inntekttype.YTELSE_FRA_OFFENTLIGE
                    }
                }
                fun tilModellobjekt(): Skatteopplysning =
                    Skatteopplysning(
                        hendelseId = hendelseId,
                        beløp = beløp.månedlig,
                        måned = måned,
                        type = type.tilModellenum(),
                        fordel = fordel,
                        beskrivelse = beskrivelse,
                        tidsstempel = tidsstempel
                    )
            }
        }

        data class OpptjeningData(
            private val opptjeningFom: LocalDate,
            private val opptjeningTom: LocalDate,
            private val arbeidsforhold: List<ArbeidsgiverOpptjeningsgrunnlagData>
        ) {
            fun tilOpptjening(skjæringstidspunkt: LocalDate) = Opptjening.gjenopprett(
                skjæringstidspunkt = skjæringstidspunkt,
                arbeidsforhold = arbeidsforhold.tilArbeidsgiverOpptjeningsgrunnlag(),
                opptjeningsperiode = opptjeningFom til opptjeningTom
            )

            data class ArbeidsgiverOpptjeningsgrunnlagData(
                private val orgnummer: String,
                private val ansattPerioder: List<ArbeidsforholdData>
            ) {
                data class ArbeidsforholdData(
                    val ansattFom: LocalDate,
                    val ansattTom: LocalDate?,
                    val deaktivert: Boolean
                ) {
                    internal fun tilArbeidsforhold() =
                        Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold(ansattFom, ansattTom, deaktivert)
                }

                companion object {
                    fun List<ArbeidsgiverOpptjeningsgrunnlagData>.tilArbeidsgiverOpptjeningsgrunnlag() =
                        map { arbeidsgiverOpptjeningsgrunnlag ->
                            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                                arbeidsgiverOpptjeningsgrunnlag.orgnummer,
                                arbeidsgiverOpptjeningsgrunnlag.ansattPerioder.map { it.tilArbeidsforhold() }
                            )
                        }
                }
            }
        }
    }

    data class ArbeidsgiverData(
        private val organisasjonsnummer: String,
        private val id: UUID,
        private val inntektshistorikk: List<InntektsmeldingData> = listOf(),
        private val sykdomshistorikk: List<SykdomshistorikkData>,
        private val sykmeldingsperioder: List<SykmeldingsperiodeData>,
        private val vedtaksperioder: List<VedtaksperiodeData>,
        private val forkastede: List<ForkastetVedtaksperiodeData>,
        private val utbetalinger: List<UtbetalingData>,
        private val beregnetUtbetalingstidslinjer: List<BeregnetUtbetalingstidslinjeData>,
        private val feriepengeutbetalinger: List<FeriepengeutbetalingData> = emptyList(),
        private val refusjonshistorikk: List<RefusjonData>,
        private val inntektsmeldingInfo: List<InntektsmeldingInfoHistorikkElementData>
    ) {
        private val modelSykdomshistorikk = SykdomshistorikkData.parseSykdomshistorikk(sykdomshistorikk)
        private val vedtaksperiodeliste = mutableListOf<Vedtaksperiode>()
        private val forkastedeliste = mutableListOf<ForkastetVedtaksperiode>()
        private val modelUtbetalinger = utbetalinger.fold(emptyList<Pair<UUID, Utbetaling>>()) { andreUtbetalinger, utbetalingen ->
            andreUtbetalinger.plus(utbetalingen.id to utbetalingen.konverterTilUtbetaling(andreUtbetalinger))
        }.map(Pair<*, Utbetaling>::second)
        private val utbetalingMap = utbetalinger.zip(modelUtbetalinger) { data, utbetaling -> data.id to utbetaling }.toMap()

        internal fun konverterTilArbeidsgiver(
            person: Person,
            aktørId: String,
            fødselsnummer: String,
            alder: Alder,
            grunnlagoppslag: (UUID) -> VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
            jurist: MaskinellJurist
        ): Arbeidsgiver {
            val arbeidsgiverJurist = jurist.medOrganisasjonsnummer(organisasjonsnummer)
            val arbeidsgiver = Arbeidsgiver.JsonRestorer.restore(
                person,
                organisasjonsnummer,
                id,
                Inntektshistorikk.gjenopprett(inntektshistorikk.map { it.tilModellobjekt() }),
                modelSykdomshistorikk,
                Sykmeldingsperioder(sykmeldingsperioder.map { it.tilPeriode() }),
                vedtaksperiodeliste,
                forkastedeliste,
                modelUtbetalinger,
                beregnetUtbetalingstidslinjer.map { it.tilBeregnetUtbetalingstidslinje() },
                feriepengeutbetalinger.map { it.createFeriepengeutbetaling(alder) },
                refusjonshistorikk.parseRefusjon(),
                inntektsmeldingInfo.tilInntektsmeldingInfoHistorikk(),
                arbeidsgiverJurist
            )

            vedtaksperiodeliste.addAll(this.vedtaksperioder.map {
                it.createVedtaksperiode(
                    person,
                    arbeidsgiver,
                    aktørId,
                    fødselsnummer,
                    this.organisasjonsnummer,
                    grunnlagoppslag,
                    utbetalingMap,
                    inntektsmeldingInfo,
                    arbeidsgiverJurist
                )
            })

            forkastedeliste.addAll(this.forkastede.map { periode ->
                ForkastetVedtaksperiode(
                    periode.vedtaksperiode.createVedtaksperiode(
                        person,
                        arbeidsgiver,
                        aktørId,
                        fødselsnummer,
                        this.organisasjonsnummer,
                        grunnlagoppslag,
                        utbetalingMap,
                        inntektsmeldingInfo,
                        arbeidsgiverJurist
                    )
                )
            })
            vedtaksperiodeliste.sort()
            return arbeidsgiver
        }

        data class InntektsmeldingData(
            private val id: UUID,
            private val dato: LocalDate,
            private val hendelseId: UUID,
            private val beløp: Double,
            private val tidsstempel: LocalDateTime
        ) {

            internal fun tilModellobjekt() = Inntektsmelding(
                id = id,
                dato = dato,
                hendelseId = hendelseId,
                beløp = beløp.månedlig,
                tidsstempel = tidsstempel
            )
        }

        data class InntektsmeldingInfoHistorikkElementData(
            private val dato: LocalDate,
            private val inntektsmeldinger: List<InntektsmeldingInfoData>
        ) {
            internal fun finn(dato: LocalDate, element: InntektsmeldingInfoData): InntektsmeldingInfo? {
                if (this.dato != dato) return null
                return inntektsmeldinger.firstNotNullOfOrNull { it.tilInntektsmeldingInfo(element) }
            }
            internal fun toMapPair() = dato to inntektsmeldinger.map { it.tilInntektsmeldingInfo() }.toMutableList()

            data class InntektsmeldingInfoData(
                private val id: UUID,
                private val arbeidsforholdId: String?
            ) {
                private val modellobjekt by lazy { InntektsmeldingInfo(id, arbeidsforholdId) }
                internal fun tilInntektsmeldingInfo(other: InntektsmeldingInfoData): InntektsmeldingInfo? {
                    if (this.id != other.id || this.arbeidsforholdId != other.arbeidsforholdId) return null
                    return modellobjekt
                }

                internal fun tilInntektsmeldingInfo() = modellobjekt

            }

            internal companion object {
                internal fun List<InntektsmeldingInfoHistorikkElementData>.finn(dato: LocalDate?, element: InntektsmeldingInfoData) =
                    (dato?.let { firstNotNullOfOrNull { it.finn(dato, element) } }) ?: element.tilInntektsmeldingInfo()

                internal fun List<InntektsmeldingInfoHistorikkElementData>.tilInntektsmeldingInfoHistorikk() =
                    InntektsmeldingInfoHistorikk(associate { it.toMapPair() }.toMutableMap())
            }
        }

        data class BeregnetUtbetalingstidslinjeData(
            private val id: UUID,
            private val tidsstempel: LocalDateTime,
            private val sykdomshistorikkElementId: UUID,
            private val vilkårsgrunnlagHistorikkInnslagId: UUID,
            private val organisasjonsnummer: String,
            private val utbetalingstidslinje: UtbetalingstidslinjeData
        ) {
            internal fun tilBeregnetUtbetalingstidslinje() =
                Utbetalingstidslinjeberegning.restore(
                    id = id,
                    tidsstempel = tidsstempel,
                    sykdomshistorikkElementId = sykdomshistorikkElementId,
                    vilkårsgrunnlagHistorikkInnslagId = vilkårsgrunnlagHistorikkInnslagId,
                    organisasjonsnummer = organisasjonsnummer,
                    utbetalingstidslinje = utbetalingstidslinje.konverterTilUtbetalingstidslinje()
                )
        }

        data class RefusjonsopplysningData(
            private val meldingsreferanseId: UUID,
            private val fom: LocalDate,
            private val tom: LocalDate?,
            private val beløp: Double
        ) {
            internal fun tilModellobjekt() = Refusjonsopplysning(meldingsreferanseId, fom, tom, beløp.månedlig)
        }

        data class SykdomstidslinjeData(
            private val dager: List<DagData>,
            private val periode: Periode?,
            private val låstePerioder: MutableList<Periode>? = mutableListOf()
        ) {
            private val dagerMap: Map<LocalDate, Dag> = DagData.parseDager(dager)

            internal fun createSykdomstidslinje(): Sykdomstidslinje =
                Sykdomstidslinje.ferdigSykdomstidslinje(
                    dager = dagerMap,
                    periode = periode,
                    perioder = låstePerioder ?: mutableListOf()
                )

            data class DagData(
                private val type: JsonDagType,
                private val kilde: KildeData,
                private val grad: Double,
                private val other: KildeData?,
                private val melding: String?
            ) {
                // Gjør så vi kan ha dato/fom og tom på samme nivå som resten av verdiene i dag
                @JsonUnwrapped
                private lateinit var datoer: DateRange

                internal companion object {
                    internal fun parseDager(dager: List<DagData>): Map<LocalDate, Dag> =
                        dager.filterNot { it.type == JsonDagType.UKJENT_DAG }
                            .flatMap { it.datoer.dates().map { dato -> dato to it.parseDag(dato) } }
                            .toMap()
                            .toSortedMap()
                }

                private val økonomi get() = Økonomi.sykdomsgrad(grad.prosent)

                private val hendelseskilde get() = kilde.parseKilde()

                internal fun parseDag(dato: LocalDate): Dag = when (type) {
                    JsonDagType.ARBEIDSDAG -> Dag.Arbeidsdag(dato, hendelseskilde)
                    JsonDagType.ARBEIDSGIVERDAG -> if (dato.erHelg()) {
                        Dag.ArbeidsgiverHelgedag(dato, økonomi, hendelseskilde)
                    } else {
                        Dag.Arbeidsgiverdag(dato, økonomi, hendelseskilde)
                    }
                    JsonDagType.FERIEDAG -> Dag.Feriedag(dato, hendelseskilde)
                    JsonDagType.FERIE_UTEN_SYKMELDINGDAG -> Dag.FerieUtenSykmeldingDag(dato, hendelseskilde)
                    JsonDagType.FRISK_HELGEDAG -> Dag.FriskHelgedag(dato, hendelseskilde)
                    JsonDagType.FORELDET_SYKEDAG -> Dag.ForeldetSykedag(dato, økonomi, hendelseskilde)
                    JsonDagType.PERMISJONSDAG -> Dag.Permisjonsdag(dato, hendelseskilde)
                    JsonDagType.PROBLEMDAG -> Dag.ProblemDag(dato, hendelseskilde, other!!.parseKilde(), melding!!)
                    JsonDagType.SYKEDAG -> if (dato.erHelg()) {
                        Dag.SykHelgedag(dato, økonomi, hendelseskilde)
                    } else {
                        Dag.Sykedag(dato, økonomi, hendelseskilde)
                    }
                    JsonDagType.SYKEDAG_NAV -> Dag.SykedagNav(dato, økonomi, hendelseskilde)
                    JsonDagType.ANDRE_YTELSER_FORELDREPENGER -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Foreldrepenger)
                    JsonDagType.ANDRE_YTELSER_AAP -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.AAP)
                    JsonDagType.ANDRE_YTELSER_OMSORGSPENGER -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Omsorgspenger)
                    JsonDagType.ANDRE_YTELSER_PLEIEPENGER -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Pleiepenger)
                    JsonDagType.ANDRE_YTELSER_SVANGERSKAPSPENGER -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Svangerskapspenger)
                    JsonDagType.ANDRE_YTELSER_OPPLÆRINGSPENGER -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Opplæringspenger)
                    JsonDagType.ANDRE_YTELSER_DAGPENGER -> Dag.AndreYtelser(dato, hendelseskilde, Dag.AndreYtelser.AnnenYtelse.Dagpenger)
                    JsonDagType.UKJENT_DAG -> throw IllegalStateException("Deserialisering av $type er ikke støttet")
                }
            }

            enum class JsonDagType {
                ARBEIDSDAG,
                ARBEIDSGIVERDAG,

                FERIEDAG,
                FERIE_UTEN_SYKMELDINGDAG,
                FRISK_HELGEDAG,
                FORELDET_SYKEDAG,
                PERMISJONSDAG,
                PROBLEMDAG,
                SYKEDAG,
                SYKEDAG_NAV,
                ANDRE_YTELSER_FORELDREPENGER,
                ANDRE_YTELSER_AAP,
                ANDRE_YTELSER_OMSORGSPENGER,
                ANDRE_YTELSER_PLEIEPENGER,
                ANDRE_YTELSER_SVANGERSKAPSPENGER,
                ANDRE_YTELSER_OPPLÆRINGSPENGER,
                ANDRE_YTELSER_DAGPENGER,

                UKJENT_DAG
            }

            data class KildeData(
                private val type: String,
                private val id: UUID,
                private val tidsstempel: LocalDateTime
            ) {
                internal fun parseKilde() =
                    SykdomstidslinjeHendelse.Hendelseskilde(type, id, tidsstempel)
            }
        }

        data class ForkastetVedtaksperiodeData(
            val vedtaksperiode: VedtaksperiodeData
        )

        data class FeriepengeutbetalingData(
            private val infotrygdFeriepengebeløpPerson: Double,
            private val infotrygdFeriepengebeløpArbeidsgiver: Double,
            private val spleisFeriepengebeløpArbeidsgiver: Double,
            private val spleisFeriepengebeløpPerson: Double,
            private val oppdrag: OppdragData,
            private val personoppdrag: OppdragData,
            private val opptjeningsår: Year,
            private val utbetalteDager: List<UtbetaltDagData>,
            private val feriepengedager: List<UtbetaltDagData>,
            private val utbetalingId: UUID,
            private val sendTilOppdrag: Boolean,
            private val sendPersonoppdragTilOS: Boolean,
        ) {
            internal fun createFeriepengeutbetaling(alder: Alder): Feriepengeutbetaling {
                val feriepengeberegner = createFeriepengeberegner(alder)
                return Feriepengeutbetaling.ferdigFeriepengeutbetaling(
                    feriepengeberegner = feriepengeberegner,
                    infotrygdFeriepengebeløpPerson = infotrygdFeriepengebeløpPerson,
                    infotrygdFeriepengebeløpArbeidsgiver = infotrygdFeriepengebeløpArbeidsgiver,
                    spleisFeriepengebeløpArbeidsgiver = spleisFeriepengebeløpArbeidsgiver,
                    spleisFeriepengebeløpPerson = spleisFeriepengebeløpPerson,
                    oppdrag = oppdrag.konverterTilOppdrag(),
                    personoppdrag = personoppdrag.konverterTilOppdrag(),
                    utbetalingId = utbetalingId,
                    sendTilOppdrag = sendTilOppdrag,
                    sendPersonoppdragTilOS = sendPersonoppdragTilOS,
                )
            }

            private fun createFeriepengeberegner(alder: Alder): Feriepengeberegner {
                return Feriepengeberegner.ferdigFeriepengeberegner(
                    alder = alder,
                    opptjeningsår = opptjeningsår,
                    utbetalteDager = utbetalteDager.sortedBy { it.type }.map { it.createUtbetaltDag() }
                )
            }

            data class UtbetaltDagData(
                internal val type: String,
                private val orgnummer: String,
                private val dato: LocalDate,
                private val beløp: Int,
            ) {
                internal fun createUtbetaltDag() =
                    when (type) {
                        "InfotrygdPersonDag" -> InfotrygdPerson(orgnummer, dato, beløp)
                        "InfotrygdArbeidsgiverDag" -> InfotrygdArbeidsgiver(orgnummer, dato, beløp)
                        "SpleisArbeidsgiverDag" -> SpleisArbeidsgiver(orgnummer, dato, beløp)
                        "SpleisPersonDag" -> SpleisPerson(orgnummer, dato, beløp)
                        else -> throw IllegalArgumentException("Støtter ikke denne dagtypen: $type")
                    }

            }
        }

        data class SykmeldingsperiodeData(
            private val fom: LocalDate,
            private val tom: LocalDate
        ) {
            internal fun tilPeriode() = fom til tom
        }

        data class VedtaksperiodeData(
            private val id: UUID,
            private val skjæringstidspunktFraInfotrygd: LocalDate?,
            private val skjæringstidspunkt: LocalDate?,
            private val sykdomstidslinje: SykdomstidslinjeData,
            private val hendelseIder: List<DokumentsporingData>,
            private val inntektsmeldingInfo: InntektsmeldingInfoHistorikkElementData.InntektsmeldingInfoData?,
            private val fom: LocalDate,
            private val tom: LocalDate,
            private val sykmeldingFom: LocalDate,
            private val sykmeldingTom: LocalDate,
            private val tilstand: TilstandType,
            private val utbetalinger: List<VedtaksperiodeUtbetalingData>,
            private val utbetalingstidslinje: UtbetalingstidslinjeData,
            private val forlengelseFraInfotrygd: ForlengelseFraInfotrygd,
            private val opprettet: LocalDateTime,
            private val oppdatert: LocalDateTime
        ) {
            data class DokumentsporingData(
                private val dokumentId: UUID,
                private val dokumenttype: DokumentTypeData
            ) {
                companion object {
                    internal fun List<DokumentsporingData>.tilSporing() = map { it.dokumenttype.tilModelltype(it.dokumentId) }.toSet()
                }
            }
            enum class DokumentTypeData(private val modelltype: (UUID) -> Dokumentsporing) {
                Sykmelding(Dokumentsporing::sykmelding),
                Søknad(Dokumentsporing::søknad),
                InntektsmeldingInntekt(Dokumentsporing::inntektsmeldingInntekt),
                InntektsmeldingDager(Dokumentsporing::inntektsmeldingDager),
                OverstyrTidslinje(Dokumentsporing::overstyrTidslinje),
                OverstyrInntekt(Dokumentsporing::overstyrInntekt),
                OverstyrRefusjon(Dokumentsporing::overstyrRefusjon),
                OverstyrArbeidsgiveropplysninger(Dokumentsporing::overstyrArbeidsgiveropplysninger),
                OverstyrArbeidsforhold(Dokumentsporing::overstyrArbeidsforhold),
                SkjønnsmessigFastsettelse(Dokumentsporing::skjønnsmessigFastsettelse);

                fun tilModelltype(dokumentId: UUID) = modelltype(dokumentId)
            }

            data class VedtaksperiodeUtbetalingData(
                private val vilkårsgrunnlagId: UUID,
                private val utbetalingId: UUID,
                private val sykdomstidslinje: SykdomstidslinjeData
            ) {
                companion object {
                    fun List<VedtaksperiodeUtbetalingData>.tilModellobjekt(grunnlagoppslag: (UUID) -> VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement, utbetalinger: Map<UUID, Utbetaling>) =
                        this.map { (grunnlagId, utbetalingId, sykdomstidslinjedata) ->
                            Triple(grunnlagoppslag(grunnlagId), utbetalinger.getValue(utbetalingId), sykdomstidslinjedata.createSykdomstidslinje())
                        }
                }
            }
            internal fun createVedtaksperiode(
                person: Person,
                arbeidsgiver: Arbeidsgiver,
                aktørId: String,
                fødselsnummer: String,
                organisasjonsnummer: String,
                grunnlagoppslag: (UUID) -> VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement,
                utbetalinger: Map<UUID, Utbetaling>,
                inntektsmeldingInfoHistorikk: List<InntektsmeldingInfoHistorikkElementData>,
                jurist: MaskinellJurist
            ): Vedtaksperiode {
                val sporingIder = hendelseIder.tilSporing()
                val sykmeldingsperiode = Periode(sykmeldingFom, sykmeldingTom)
                return Vedtaksperiode.ferdigVedtaksperiode(
                    person = person,
                    arbeidsgiver = arbeidsgiver,
                    id = id,
                    aktørId = aktørId,
                    fødselsnummer = fødselsnummer,
                    organisasjonsnummer = organisasjonsnummer,
                    tilstand = parseTilstand(this.tilstand),
                    skjæringstidspunktFraInfotrygd = skjæringstidspunktFraInfotrygd,
                    sykdomstidslinje = sykdomstidslinje.createSykdomstidslinje(),
                    dokumentsporing = sporingIder.toMutableSet(),
                    inntektsmeldingInfo = inntektsmeldingInfo?.let {
                        inntektsmeldingInfoHistorikk.finn(
                            skjæringstidspunktFraInfotrygd ?: skjæringstidspunkt,
                            it
                        )
                    },
                    periode = Periode(fom, tom),
                    sykmeldingsperiode = sykmeldingsperiode,
                    utbetalinger = VedtaksperiodeUtbetalinger(
                        utbetalinger = this.utbetalinger.tilModellobjekt(grunnlagoppslag, utbetalinger)
                    ),
                    utbetalingstidslinje = this.utbetalingstidslinje.konverterTilUtbetalingstidslinje(),
                    forlengelseFraInfotrygd = forlengelseFraInfotrygd,
                    opprettet = opprettet,
                    oppdatert = oppdatert,
                    medVedtaksperiode = jurist
                )
            }

            private fun parseTilstand(tilstand: TilstandType) = when (tilstand) {
                TilstandType.AVVENTER_HISTORIKK -> Vedtaksperiode.AvventerHistorikk
                TilstandType.AVVENTER_SKJØNNSMESSIG_FASTSETTELSE -> Vedtaksperiode.AvventerSkjønnsmessigFastsettelse
                TilstandType.AVVENTER_SKJØNNSMESSIG_FASTSETTELSE_REVURDERING -> Vedtaksperiode.AvventerSkjønnsmessigFastsettelseRevurdering
                TilstandType.AVVENTER_GODKJENNING -> Vedtaksperiode.AvventerGodkjenning
                TilstandType.AVVENTER_SIMULERING -> Vedtaksperiode.AvventerSimulering
                TilstandType.TIL_UTBETALING -> Vedtaksperiode.TilUtbetaling
                TilstandType.AVSLUTTET -> Vedtaksperiode.Avsluttet
                TilstandType.AVSLUTTET_UTEN_UTBETALING -> Vedtaksperiode.AvsluttetUtenUtbetaling
                TilstandType.REVURDERING_FEILET -> Vedtaksperiode.RevurderingFeilet
                TilstandType.TIL_INFOTRYGD -> Vedtaksperiode.TilInfotrygd
                TilstandType.START -> Vedtaksperiode.Start
                TilstandType.AVVENTER_INFOTRYGDHISTORIKK -> Vedtaksperiode.AvventerInfotrygdHistorikk
                TilstandType.AVVENTER_VILKÅRSPRØVING -> Vedtaksperiode.AvventerVilkårsprøving
                TilstandType.AVVENTER_INNTEKTSMELDING -> Vedtaksperiode.AvventerInntektsmelding
                TilstandType.AVVENTER_BLOKKERENDE_PERIODE -> Vedtaksperiode.AvventerBlokkerendePeriode
                TilstandType.AVVENTER_REVURDERING -> Vedtaksperiode.AvventerRevurdering
                TilstandType.AVVENTER_HISTORIKK_REVURDERING -> Vedtaksperiode.AvventerHistorikkRevurdering
                TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING -> Vedtaksperiode.AvventerVilkårsprøvingRevurdering
                TilstandType.AVVENTER_SIMULERING_REVURDERING -> Vedtaksperiode.AvventerSimuleringRevurdering
                TilstandType.AVVENTER_GODKJENNING_REVURDERING -> Vedtaksperiode.AvventerGodkjenningRevurdering
                TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING -> Vedtaksperiode.AvventerGjennomførtRevurdering
            }

            data class DataForSimuleringData(
                private val totalbeløp: Int,
                private val perioder: List<SimulertPeriode>
            ) {
                internal fun parseDataForSimulering() = SimuleringResultat(
                    totalbeløp = totalbeløp,
                    perioder = perioder.map { it.parsePeriode() }
                )

                data class SimulertPeriode(
                    private val fom: LocalDate,
                    private val tom: LocalDate,
                    private val utbetalinger: List<SimulertUtbetaling>
                ) {

                    internal fun parsePeriode(): SimuleringResultat.SimulertPeriode {
                        return SimuleringResultat.SimulertPeriode(
                            periode = Periode(fom, tom),
                            utbetalinger = utbetalinger.map { it.parseUtbetaling() }
                        )
                    }
                }

                data class SimulertUtbetaling(
                    private val forfallsdato: LocalDate,
                    private val utbetalesTil: Mottaker,
                    private val feilkonto: Boolean,
                    private val detaljer: List<Detaljer>
                ) {
                    internal fun parseUtbetaling(): SimuleringResultat.SimulertUtbetaling {
                        return SimuleringResultat.SimulertUtbetaling(
                            forfallsdato = forfallsdato,
                            utbetalesTil = SimuleringResultat.Mottaker(
                                id = utbetalesTil.id,
                                navn = utbetalesTil.navn
                            ),
                            feilkonto = feilkonto,
                            detaljer = detaljer.map { it.parseDetaljer() }
                        )
                    }
                }

                data class Detaljer(
                    private val fom: LocalDate,
                    private val tom: LocalDate,
                    private val konto: String,
                    private val beløp: Int,
                    private val klassekode: Klassekode,
                    private val uføregrad: Int,
                    private val utbetalingstype: String,
                    private val tilbakeføring: Boolean,
                    private val sats: Sats,
                    private val refunderesOrgnummer: String
                ) {
                    internal fun parseDetaljer(): SimuleringResultat.Detaljer {
                        return SimuleringResultat.Detaljer(
                            periode = Periode(fom, tom),
                            konto = konto,
                            beløp = beløp,
                            klassekode = SimuleringResultat.Klassekode(
                                kode = klassekode.kode,
                                beskrivelse = klassekode.beskrivelse
                            ),
                            uføregrad = uføregrad,
                            utbetalingstype = utbetalingstype,
                            tilbakeføring = tilbakeføring,
                            sats = SimuleringResultat.Sats(
                                sats = sats.sats,
                                antall = sats.antall,
                                type = sats.type
                            ),
                            refunderesOrgnummer = refunderesOrgnummer
                        )
                    }
                }

                data class Sats(
                    val sats: Double,
                    val antall: Int,
                    val type: String
                )

                data class Klassekode(
                    val kode: String,
                    val beskrivelse: String
                )

                data class Mottaker(
                    val id: String,
                    val navn: String
                )
            }
        }

        data class RefusjonData(
            private val meldingsreferanseId: UUID,
            private val førsteFraværsdag: LocalDate?,
            private val arbeidsgiverperioder: List<Periode>,
            private val beløp: Double?,
            private val sisteRefusjonsdag: LocalDate?,
            private val endringerIRefusjon: List<EndringIRefusjonData>,
            private val tidsstempel: LocalDateTime
        ) {
            internal companion object {
                internal fun List<RefusjonData>.parseRefusjon() = Refusjonshistorikk().apply {
                    forEach {
                        leggTilRefusjon(
                            Refusjonshistorikk.Refusjon(
                                meldingsreferanseId = it.meldingsreferanseId,
                                førsteFraværsdag = it.førsteFraværsdag,
                                arbeidsgiverperioder = it.arbeidsgiverperioder,
                                beløp = it.beløp?.månedlig,
                                sisteRefusjonsdag = it.sisteRefusjonsdag,
                                endringerIRefusjon = it.endringerIRefusjon.parseEndringerIRefusjon(),
                                tidsstempel = it.tidsstempel
                            )
                        )
                    }
                }
            }

            data class EndringIRefusjonData(
                private val beløp: Double,
                private val endringsdato: LocalDate
            ) {
                internal companion object {
                    internal fun List<EndringIRefusjonData>.parseEndringerIRefusjon() = map {
                        Refusjonshistorikk.Refusjon.EndringIRefusjon(
                            beløp = it.beløp.månedlig,
                            endringsdato = it.endringsdato
                        )
                    }
                }
            }
        }
    }

    data class SykdomshistorikkData(
        private val tidsstempel: LocalDateTime,
        private val id: UUID,
        private val hendelseId: UUID?,
        private val hendelseSykdomstidslinje: ArbeidsgiverData.SykdomstidslinjeData,
        private val beregnetSykdomstidslinje: ArbeidsgiverData.SykdomstidslinjeData
    ) {

        internal companion object {
            internal fun parseSykdomshistorikk(data: List<SykdomshistorikkData>) =
                Sykdomshistorikk.ferdigSykdomshistorikk(data.map { it.parseSykdomshistorikk() })
        }

        internal fun parseSykdomshistorikk(): Sykdomshistorikk.Element {
            return Sykdomshistorikk.Element.ferdigSykdomshistorikkElement(
                id = id,
                hendelseId = hendelseId,
                tidsstempel = tidsstempel,
                hendelseSykdomstidslinje = hendelseSykdomstidslinje.createSykdomstidslinje(),
                beregnetSykdomstidslinje = beregnetSykdomstidslinje.createSykdomstidslinje()
            )
        }
    }

    data class UtbetalingData(
        val id: UUID,
        private val korrelasjonsId: UUID,
        private val beregningId: UUID,
        private val fom: LocalDate,
        private val tom: LocalDate,
        private val annulleringer: List<UUID>?,
        private val dager: List<UtbetalingstidslinjeData.UtbetalingsdagData>,
        private val arbeidsgiverOppdrag: OppdragData,
        private val personOppdrag: OppdragData,
        private val tidsstempel: LocalDateTime,
        private val type: String,
        private val status: String,
        private val maksdato: LocalDate,
        private val forbrukteSykedager: Int?,
        private val gjenståendeSykedager: Int?,
        private val vurdering: VurderingData?,
        private val overføringstidspunkt: LocalDateTime?,
        private val avstemmingsnøkkel: Long?,
        private val avsluttet: LocalDateTime?,
        private val oppdatert: LocalDateTime
    ) {

        internal fun konverterTilUtbetaling(andreUtbetalinger: List<Pair<UUID, Utbetaling>>) = Utbetaling.ferdigUtbetaling(
            id = id,
            korrelasjonsId = korrelasjonsId,
            beregningId = beregningId,
            annulleringer = annulleringer?.map { annulleringen -> andreUtbetalinger.single { it.first == annulleringen }.second } ?: emptyList(),
            opprinneligPeriode = fom til tom,
            utbetalingstidslinje = dager.flatMap { it.parseDager() },
            arbeidsgiverOppdrag = arbeidsgiverOppdrag.konverterTilOppdrag(),
            personOppdrag = personOppdrag.konverterTilOppdrag(),
            tidsstempel = tidsstempel,
            utbetalingstatus = enumValueOf(status),
            utbetalingtype = enumValueOf(type),
            maksdato = maksdato,
            forbrukteSykedager = forbrukteSykedager,
            gjenståendeSykedager = gjenståendeSykedager,
            vurdering = vurdering?.konverterTilVurdering(),
            overføringstidspunkt = overføringstidspunkt,
            avstemmingsnøkkel = avstemmingsnøkkel,
            avsluttet = avsluttet,
            oppdatert = oppdatert
        )

        data class VurderingData(
            private val godkjent: Boolean,
            private val ident: String,
            private val epost: String,
            private val tidspunkt: LocalDateTime,
            private val automatiskBehandling: Boolean
        ) {
            internal fun konverterTilVurdering() = Utbetaling.ferdigVurdering(
                godkjent = godkjent,
                ident = ident,
                epost = epost,
                tidspunkt = tidspunkt,
                automatiskBehandling = automatiskBehandling
            )
        }
    }

    data class OppdragData(
        private val mottaker: String,
        private val fagområde: String,
        private val linjer: List<UtbetalingslinjeData>,
        private val fagsystemId: String,
        private val endringskode: String,
        private val sisteArbeidsgiverdag: LocalDate?,
        private val tidsstempel: LocalDateTime,
        private val nettoBeløp: Int,
        private val avstemmingsnøkkel: Long?,
        private val status: Oppdragstatus?,
        private val overføringstidspunkt: LocalDateTime?,
        private val erSimulert: Boolean,
        private val simuleringsResultat: ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData?
    ) {
        internal fun konverterTilOppdrag(): Oppdrag = Oppdrag.ferdigOppdrag(
            mottaker = mottaker,
            from = Fagområde.from(fagområde),
            utbetalingslinjer = linjer.map { it.konverterTilUtbetalingslinje() },
            fagsystemId = fagsystemId,
            endringskode = Endringskode.valueOf(endringskode),
            sisteArbeidsgiverdag = sisteArbeidsgiverdag,
            nettoBeløp = nettoBeløp,
            overføringstidspunkt = overføringstidspunkt,
            avstemmingsnøkkel = avstemmingsnøkkel,
            status = status,
            tidsstempel = tidsstempel,
            erSimulert = erSimulert,
            simuleringResultat = simuleringsResultat?.parseDataForSimulering()
        )
    }

    data class UtbetalingslinjeData(
        private val fom: LocalDate,
        private val tom: LocalDate,
        private val satstype: String,
        private val sats: Int,
        private val lønn: Int?,
        private val grad: Int?,
        private val refFagsystemId: String?,
        private val delytelseId: Int,
        private val refDelytelseId: Int?,
        private val endringskode: String,
        private val klassekode: String,
        private val datoStatusFom: LocalDate?
    ) {

        internal fun konverterTilUtbetalingslinje(): Utbetalingslinje = Utbetalingslinje.ferdigUtbetalingslinje(
            fom = fom,
            tom = tom,
            satstype = Satstype.fromString(satstype),
            sats = sats,
            lønn = lønn,
            grad = grad,
            refFagsystemId = refFagsystemId,
            delytelseId = delytelseId,
            refDelytelseId = refDelytelseId,
            endringskode = Endringskode.valueOf(endringskode),
            klassekode = Klassekode.from(klassekode),
            datoStatusFom = datoStatusFom
        )
    }

    data class UtbetalingstidslinjeData(
        val dager: List<UtbetalingsdagData>
    ) {
        internal fun konverterTilUtbetalingstidslinje(): Utbetalingstidslinje =
            Utbetalingstidslinje.ferdigUtbetalingstidslinje(dager.flatMap { it.parseDager() })

        enum class BegrunnelseData {
            SykepengedagerOppbrukt,
            SykepengedagerOppbruktOver67,
            MinimumInntekt,
            MinimumInntektOver67,
            EgenmeldingUtenforArbeidsgiverperiode,
            MinimumSykdomsgrad,
            AndreYtelserAap,
            AndreYtelserDagpenger,
            AndreYtelserForeldrepenger,
            AndreYtelserOmsorgspenger,
            AndreYtelserOpplaringspenger,
            AndreYtelserPleiepenger,
            AndreYtelserSvangerskapspenger,
            EtterDødsdato,
            ManglerMedlemskap,
            ManglerOpptjening,
            Over70,
            NyVilkårsprøvingNødvendig;

            fun tilBegrunnelse() = when (this) {
                SykepengedagerOppbrukt -> Begrunnelse.SykepengedagerOppbrukt
                SykepengedagerOppbruktOver67 -> Begrunnelse.SykepengedagerOppbruktOver67
                MinimumSykdomsgrad -> Begrunnelse.MinimumSykdomsgrad
                EgenmeldingUtenforArbeidsgiverperiode -> Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode
                AndreYtelserAap -> Begrunnelse.AndreYtelserAap
                AndreYtelserDagpenger -> Begrunnelse.AndreYtelserDagpenger
                AndreYtelserForeldrepenger -> Begrunnelse.AndreYtelserForeldrepenger
                AndreYtelserOmsorgspenger -> Begrunnelse.AndreYtelserOmsorgspenger
                AndreYtelserOpplaringspenger -> Begrunnelse.AndreYtelserOpplaringspenger
                AndreYtelserPleiepenger -> Begrunnelse.AndreYtelserPleiepenger
                AndreYtelserSvangerskapspenger -> Begrunnelse.AndreYtelserSvangerskapspenger
                MinimumInntekt -> Begrunnelse.MinimumInntekt
                MinimumInntektOver67 -> Begrunnelse.MinimumInntektOver67
                EtterDødsdato -> Begrunnelse.EtterDødsdato
                ManglerMedlemskap -> Begrunnelse.ManglerMedlemskap
                ManglerOpptjening -> Begrunnelse.ManglerOpptjening
                Over70 -> Begrunnelse.Over70
                NyVilkårsprøvingNødvendig -> Begrunnelse.NyVilkårsprøvingNødvendig
            }

            internal companion object {
                fun fraBegrunnelse(begrunnelse: Begrunnelse) = when (begrunnelse) {
                    is Begrunnelse.SykepengedagerOppbrukt -> SykepengedagerOppbrukt
                    is Begrunnelse.SykepengedagerOppbruktOver67 -> SykepengedagerOppbruktOver67
                    is Begrunnelse.MinimumSykdomsgrad -> MinimumSykdomsgrad
                    is Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode -> EgenmeldingUtenforArbeidsgiverperiode
                    is Begrunnelse.AndreYtelserAap -> AndreYtelserAap
                    is Begrunnelse.AndreYtelserDagpenger -> AndreYtelserDagpenger
                    is Begrunnelse.AndreYtelserForeldrepenger -> AndreYtelserForeldrepenger
                    is Begrunnelse.AndreYtelserOmsorgspenger -> AndreYtelserOmsorgspenger
                    is Begrunnelse.AndreYtelserOpplaringspenger -> AndreYtelserOpplaringspenger
                    is Begrunnelse.AndreYtelserPleiepenger -> AndreYtelserPleiepenger
                    is Begrunnelse.AndreYtelserSvangerskapspenger -> AndreYtelserSvangerskapspenger
                    is Begrunnelse.MinimumInntekt -> MinimumInntekt
                    is Begrunnelse.MinimumInntektOver67 -> MinimumInntektOver67
                    is Begrunnelse.EtterDødsdato -> EtterDødsdato
                    is Begrunnelse.ManglerMedlemskap -> ManglerMedlemskap
                    is Begrunnelse.ManglerOpptjening -> ManglerOpptjening
                    is Begrunnelse.Over70 -> Over70
                    is Begrunnelse.NyVilkårsprøvingNødvendig -> NyVilkårsprøvingNødvendig
                }
            }
        }

        enum class TypeData {
            ArbeidsgiverperiodeDag,
            NavDag,
            NavHelgDag,
            Arbeidsdag,
            Fridag,
            AvvistDag,
            UkjentDag,
            ForeldetDag,
            ArbeidsgiverperiodedagNav
        }

        data class UtbetalingsdagData(
            private val type: TypeData,
            private val aktuellDagsinntekt: Double,
            private val dekningsgrunnlag: Double,
            private val grunnbeløpgrense: Double?,
            private val begrunnelse: BegrunnelseData?,
            private val begrunnelser: List<BegrunnelseData>?,
            private val grad: Double,
            private val totalGrad: Double,
            private val arbeidsgiverRefusjonsbeløp: Double,
            private val arbeidsgiverbeløp: Double?,
            private val personbeløp: Double?,
            private val er6GBegrenset: Boolean?
        ) {
            private val builder: Økonomi.Builder = Økonomi.Builder()

            init {
                builder.grad(grad)
                    .totalGrad(totalGrad)
                    .aktuellDagsinntekt(aktuellDagsinntekt)
                    .dekningsgrunnlag(dekningsgrunnlag)
                    .grunnbeløpsgrense(grunnbeløpgrense)
                    .arbeidsgiverRefusjonsbeløp(arbeidsgiverRefusjonsbeløp)
                    .arbeidsgiverbeløp(arbeidsgiverbeløp)
                    .personbeløp(personbeløp)
                    .er6GBegrenset(er6GBegrenset)
                    .tilstand(
                        when {
                            arbeidsgiverbeløp == null && type == TypeData.AvvistDag -> Økonomi.Tilstand.Låst
                            arbeidsgiverbeløp == null -> Økonomi.Tilstand.HarInntekt
                            type == TypeData.AvvistDag -> Økonomi.Tilstand.LåstMedBeløp
                            else -> Økonomi.Tilstand.HarBeløp
                        }
                    )
            }

            // Gjør så vi kan ha dato/fom og tom på samme nivå som resten av verdiene i utbetalingsdata
            @JsonUnwrapped
            private lateinit var datoer: DateRange

            private val økonomi get() = builder.build()

            internal fun parseDager() = datoer.dates().map(::parseDag)

            internal fun parseDag(dato: LocalDate) =
                when (type) {
                    TypeData.ArbeidsgiverperiodeDag -> {
                        Utbetalingsdag.ArbeidsgiverperiodeDag(dato = dato, økonomi = økonomi)
                    }
                    TypeData.NavDag -> {
                        Utbetalingsdag.NavDag(dato, økonomi)
                    }
                    TypeData.NavHelgDag -> {
                        Utbetalingsdag.NavHelgDag(dato = dato, økonomi = økonomi)
                    }
                    TypeData.Arbeidsdag -> {
                        Utbetalingsdag.Arbeidsdag(dato = dato, økonomi = økonomi)
                    }
                    TypeData.Fridag -> {
                        Utbetalingsdag.Fridag(dato = dato, økonomi = økonomi)
                    }
                    TypeData.AvvistDag -> {
                        Utbetalingsdag.AvvistDag(
                            dato = dato,
                            økonomi = økonomi,
                            begrunnelser = begrunnelser?.map { it.tilBegrunnelse() } ?: error("Prøver å deserialisere avvist dag uten begrunnelse")
                        )
                    }
                    TypeData.UkjentDag -> {
                        Utbetalingsdag.UkjentDag(dato = dato, økonomi = økonomi)
                    }
                    TypeData.ForeldetDag -> {
                        Utbetalingsdag.ForeldetDag(dato = dato, økonomi = økonomi)
                    }
                    TypeData.ArbeidsgiverperiodedagNav -> {
                        Utbetalingsdag.ArbeidsgiverperiodedagNav(dato = dato, økonomi = økonomi)
                    }
                }
        }
    }
}
