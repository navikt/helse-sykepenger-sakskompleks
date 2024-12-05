package no.nav.helse.person.builders

import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.UtkastTilVedtakEvent.FastsattEtterHovedregel
import no.nav.helse.person.PersonObserver.UtkastTilVedtakEvent.FastsattEtterSkjønn
import no.nav.helse.person.PersonObserver.UtkastTilVedtakEvent.FastsattIInfotrygd
import no.nav.helse.person.PersonObserver.UtkastTilVedtakEvent.Sykepengegrunnlagsfakta
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingtype.REVURDERING
import no.nav.helse.utbetalingslinjer.Utbetalingtype.UTBETALING
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import kotlin.properties.Delegates

internal class UtkastTilVedtakBuilder(
    private val vedtaksperiodeId: UUID,
    private val arbeidsgiver: String,
    private val kanForkastes: Boolean,
    erForlengelse: Boolean,
    private val harPeriodeRettFør: Boolean
) {
    private val tags = mutableSetOf<Tag>()
    init {
        if (erForlengelse) tags.add(Tag.Forlengelse)
        else tags.add(Tag.Førstegangsbehandling)
    }

    internal fun grunnbeløpsregulert() = apply { tags.add(Tag.Grunnbeløpsregulering) }

    private data class RelevantPeriode(val vedtaksperiodeId: UUID, val behandlingId: UUID, val skjæringstidspunkt: LocalDate, val periode: Periode)
    private val relevantePerioder = mutableSetOf<RelevantPeriode>()
    internal fun relevantPeriode(vedtaksperiodeId: UUID, behandlingId: UUID, skjæringstidspunkt: LocalDate, periode: Periode) = apply {
        relevantePerioder.add(RelevantPeriode(vedtaksperiodeId, behandlingId, skjæringstidspunkt, periode))
    }

    private lateinit var behandlingId: UUID
    internal fun behandlingId(behandlingId: UUID) = apply { this.behandlingId = behandlingId }

    private lateinit var periode: Periode
    internal fun periode(arbeidsgiverperiode: List<Periode>, periode: Periode) = apply {
        this.periode = periode

        val gjennomført = NormalArbeidstaker.arbeidsgiverperiodenGjennomført(arbeidsgiverperiode.periode()?.count() ?: 0)
        val arbeidsgiverperiodePåstartetITidligerePeriode = arbeidsgiverperiode.isNotEmpty() && periode.start >= arbeidsgiverperiode.last().endInclusive
        if (!harPeriodeRettFør && gjennomført && arbeidsgiverperiodePåstartetITidligerePeriode) {
            tags.add(Tag.IngenNyArbeidsgiverperiode)
        }
    }

    private val hendelseIder = mutableSetOf<UUID>()
    internal fun hendelseIder(hendelseIder: Set<UUID>) = apply { this.hendelseIder.addAll(hendelseIder) }

    private lateinit var skjæringstidspunkt: LocalDate
    internal fun skjæringstidspunkt(skjæringstidspunkt: LocalDate) = apply {
        this.skjæringstidspunkt = skjæringstidspunkt
    }

    private lateinit var vilkårsgrunnlagId: UUID
    internal fun vilkårsgrunnlagId(vilkårsgrunnlagId: UUID) = apply { this.vilkårsgrunnlagId = vilkårsgrunnlagId }

    private lateinit var utbetalingId: UUID
    private var revurdering by Delegates.notNull<Boolean>()
    internal fun utbetaling(utbetaling: Utbetaling) = apply {
        this.utbetalingId = utbetaling.id

        check(utbetaling.type == UTBETALING || utbetaling.type == REVURDERING) {
            "Utkast til vedtak på ${utbetaling.type.name}? Det kan jo ikke være rett."
        }

        this.revurdering = utbetaling.type == REVURDERING

        val antallTagsFør = tags.size
        val arbeidsgiverNettoBeløp = utbetaling.arbeidsgiverOppdrag().nettoBeløp()
        val personNettoBeløp = utbetaling.personOppdrag().nettoBeløp()

        if (arbeidsgiverNettoBeløp > 0) tags.add(Tag.Arbeidsgiverutbetaling)
        else if (arbeidsgiverNettoBeløp < 0) tags.add(Tag.NegativArbeidsgiverutbetaling)

        if (personNettoBeløp > 0) tags.add(Tag.Personutbetaling)
        else if (personNettoBeløp < 0) tags.add(Tag.NegativPersonutbetaling)

        if (arbeidsgiverNettoBeløp == 0 && personNettoBeløp == 0) tags.add(Tag.IngenUtbetaling)

        check(tags.size > antallTagsFør) {
            "arbeidsgiverNettoBeløp=$arbeidsgiverNettoBeløp, personNettoBeløp=$personNettoBeløp burde bli minst én ny tag."
        }
    }

    internal fun utbetalingstidslinje(utbetalingstidslinje: Utbetalingstidslinje) = apply {
        tags.add(utbetalingstidslinje.behandlingsresultat)
    }

    private val Utbetalingstidslinje.behandlingsresultat get(): Tag {
        val avvistDag = any { it is Utbetalingsdag.AvvistDag || it is Utbetalingsdag.ForeldetDag }
        val navDag = any { it is Utbetalingsdag.NavDag }

        return when {
            !navDag -> Tag.Avslag
            navDag && avvistDag -> Tag.DelvisInnvilget
            else -> Tag.Innvilget
        }
    }

    internal fun sykdomstidslinje(sykdomstidslinje: Sykdomstidslinje) = apply {
        if (sykdomstidslinje.any { it is Dag.Feriedag }) tags.add(Tag.Ferie)
    }

    private var sykepengegrunnlag by Delegates.notNull<Double>()
    private lateinit var beregningsgrunnlag: Inntekt
    private var totalOmregnetÅrsinntekt by Delegates.notNull<Double>()
    private var seksG by Delegates.notNull<Double>()
    internal fun sykepengegrunnlag(sykepengegrunnlag: Inntekt, beregningsgrunnlag: Inntekt, totalOmregnetÅrsinntekt: Inntekt, seksG: Inntekt, inngangsvilkårFraInfotrygd: Boolean) = apply {
        this.sykepengegrunnlag = sykepengegrunnlag.årlig
        this.beregningsgrunnlag = beregningsgrunnlag
        this.totalOmregnetÅrsinntekt = totalOmregnetÅrsinntekt.årlig
        this.seksG = seksG.årlig

        val toG = seksG/3
        if (!inngangsvilkårFraInfotrygd && beregningsgrunnlag > seksG) tags.add(Tag.`6GBegrenset`)
        if (sykepengegrunnlag < toG) tags.add(Tag.SykepengegrunnlagUnder2G)
        if (inngangsvilkårFraInfotrygd) tags.add(Tag.InngangsvilkårFraInfotrygd)
    }

    private data class Arbeidsgiverinntekt(val arbeidsgiver: String, val omregnedeÅrsinntekt: Double, val skjønnsfastsatt: Double?, val gjelder: Periode, val inntektskilde: Inntektsopplysning.Inntektskilde?)
    private val arbeidsgiverinntekter = mutableSetOf<Arbeidsgiverinntekt>()
    internal fun arbeidsgiverinntekt(arbeidsgiver: String, omregnedeÅrsinntekt: Inntekt, skjønnsfastsatt: Inntekt?, gjelder: Periode, inntektskilde: Inntektsopplysning.Inntektskilde?) = apply {
        arbeidsgiverinntekter.add(Arbeidsgiverinntekt(arbeidsgiver, omregnedeÅrsinntekt.årlig, skjønnsfastsatt?.årlig, gjelder, inntektskilde))
    }

    private val tilkomneArbeidsgivere = mutableSetOf<String>()
    internal fun tilkommetInntekt(arbeidsgiver: String) {
        tilkomneArbeidsgivere.add(arbeidsgiver)
    }

    private val build by lazy { Build() }

    internal fun buildGodkjenningsbehov() = build.godkjenningsbehov
    internal fun buildUtkastTilVedtak() = build.utkastTilVedtak
    internal fun buildAvsluttedMedVedtak(vedtakFattet: LocalDateTime, historiskeHendelseIder: Set<UUID>) = build.avsluttetMedVedtak(vedtakFattet, historiskeHendelseIder)

    private inner class Build {
        private val skjønnsfastsatt = arbeidsgiverinntekter.any { it.skjønnsfastsatt != null }.also {
            if (it) check(arbeidsgiverinntekter.all { arbeidsgiver -> arbeidsgiver.skjønnsfastsatt != null }) { "Enten må ingen eller alle arbeidsgivere i sykepengegrunnlaget være skjønnsmessig fastsatt." }
        }
        private val perioderMedSammeSkjæringstidspunkt = relevantePerioder.filter { it.skjæringstidspunkt == skjæringstidspunkt }

        init {
            check(arbeidsgiverinntekter.isNotEmpty()) { "Forventet ikke at det ikke er noen arbeidsgivere i sykepengegrunnlaget." }
            if (tilkomneArbeidsgivere.isNotEmpty()) tags.add(Tag.TilkommenInntekt)
            if (arbeidsgiverinntekter.size + tilkomneArbeidsgivere.size == 1) tags.add(Tag.EnArbeidsgiver) else tags.add(Tag.FlereArbeidsgivere)
            if (arbeidsgiverinntekter.single { it.arbeidsgiver == arbeidsgiver }.inntektskilde == Inntektsopplysning.Inntektskilde.AOrdningen) tags.add(Tag.InntektFraAOrdningenLagtTilGrunn)
        }

        private val sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta = when {
            tags.contains(Tag.InngangsvilkårFraInfotrygd) -> FastsattIInfotrygd(totalOmregnetÅrsinntekt).also {
                check(Tag.FlereArbeidsgivere !in tags) { "Skal ikke være mulig med vilkårsgrunnlag fra Infotrygd og flere arbeidsgivere!" }
            }
            skjønnsfastsatt ->  FastsattEtterSkjønn(
                omregnetÅrsinntekt = totalOmregnetÅrsinntekt,
                sykepengegrunnlag = sykepengegrunnlag,
                `6G`= seksG,
                arbeidsgivere = arbeidsgiverinntekter.map { FastsattEtterSkjønn.Arbeidsgiver(
                    arbeidsgiver = it.arbeidsgiver,
                    omregnetÅrsinntekt = it.omregnedeÅrsinntekt,
                    skjønnsfastsatt = it.skjønnsfastsatt!!,
                    inntektskilde = it.inntektskilde
                )}
            )
            else -> FastsattEtterHovedregel(
                omregnetÅrsinntekt = totalOmregnetÅrsinntekt,
                sykepengegrunnlag = sykepengegrunnlag,
                `6G`= seksG,
                arbeidsgivere = arbeidsgiverinntekter.map { FastsattEtterHovedregel.Arbeidsgiver(
                    arbeidsgiver = it.arbeidsgiver,
                    omregnetÅrsinntekt = it.omregnedeÅrsinntekt,
                    inntektskilde = it.inntektskilde
                )}
            )
        }

        private val beregningsgrunnlagForAvsluttetMedVedtak: Double = sykepengegrunnlagsfakta.beregningsgrunnlagForAvsluttetMedVedtak().also {
            check(it == beregningsgrunnlag.årlig) { "Beregningsgrunnlag ${beregningsgrunnlag.årlig} er noe annet enn beregningsgrunnlag beregnet fra sykepengegrunnlagsfakta $it" }
        }

        private val inntektForAvsluttetMedVedtak: Double = sykepengegrunnlagsfakta.inntektForAvsluttetMedVedtak().also {
            check(it == beregningsgrunnlag.månedlig) { "Inntekt ${beregningsgrunnlag.månedlig} er noe annet enn inntekt beregnet fra sykepengegrunnlagsfakta $it" }
        }

        private val omregnetÅrsinntektPerArbeidsgiverForAvsluttedMedVedtak = sykepengegrunnlagsfakta.omregnetÅrsinntektPerArbeidsgiverForAvsluttedMedVedtak()

        private val sykepengegrunnlagsbegrensningForAvsluttetMedVedtak = sykepengegrunnlagsfakta.sykepengegrunnlagsbegrensningForAvsluttedMedVedtak(tags)

        private val periodetypeForGodkjenningsbehov = sykepengegrunnlagsfakta.periodetypeForGodkjenningsbehov(tags)

        private val omregnedeÅrsinntekterForGodkjenningsbehov = sykepengegrunnlagsfakta.omregnedeÅrsinntekterForGodkjenningsbehov(arbeidsgiver)

        val godkjenningsbehov = mapOf(
            "periodeFom" to "${periode.start}",
            "periodeTom" to "${periode.endInclusive}",
            "skjæringstidspunkt" to "$skjæringstidspunkt",
            "vilkårsgrunnlagId" to "$vilkårsgrunnlagId",
            "periodetype" to periodetypeForGodkjenningsbehov,
            "førstegangsbehandling" to tags.contains(Tag.Førstegangsbehandling),
            "utbetalingtype" to if (revurdering) "REVURDERING" else "UTBETALING",
            "inntektskilde" to if (tags.contains(Tag.EnArbeidsgiver)) "EN_ARBEIDSGIVER" else "FLERE_ARBEIDSGIVERE",
            // Til ettertanke: Her kan det være orgnummer på tilkomnde arbeidsgivere i tillegg til de som er i "sykepengegrunnlagsfakta". Kanskje finne på noe smartere der?
            "orgnummereMedRelevanteArbeidsforhold" to (arbeidsgiverinntekter.map { it.arbeidsgiver }).toSet(),
            "tags" to tags.utgående,
            "kanAvvises" to kanForkastes,
            "omregnedeÅrsinntekter" to omregnedeÅrsinntekterForGodkjenningsbehov,
            "behandlingId" to "$behandlingId",
            "hendelser" to hendelseIder,
            "perioderMedSammeSkjæringstidspunkt" to perioderMedSammeSkjæringstidspunkt.map {
                mapOf(
                    "vedtaksperiodeId" to "${it.vedtaksperiodeId}",
                    "behandlingId" to "${it.behandlingId}",
                    "fom" to "${it.periode.start}",
                    "tom" to "${it.periode.endInclusive}"
                )
            },
            "sykepengegrunnlagsfakta" to when (sykepengegrunnlagsfakta) {
                is FastsattIInfotrygd -> mapOf(
                    "omregnetÅrsinntektTotalt" to sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                    "fastsatt" to sykepengegrunnlagsfakta.fastsatt
                )
                is FastsattEtterHovedregel -> mapOf(
                    "omregnetÅrsinntektTotalt" to sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                    "sykepengegrunnlag" to sykepengegrunnlag,
                    "6G" to seksG,
                    "fastsatt" to sykepengegrunnlagsfakta.fastsatt,
                    "arbeidsgivere" to sykepengegrunnlagsfakta.arbeidsgivere.map {
                        mapOf(
                            "arbeidsgiver" to it.arbeidsgiver,
                            "omregnetÅrsinntekt" to it.omregnetÅrsinntekt,
                            "inntektskilde" to it.inntektskilde
                        )
                    }
                )
                is FastsattEtterSkjønn -> mapOf(
                    "omregnetÅrsinntektTotalt" to sykepengegrunnlagsfakta.omregnetÅrsinntekt,
                    "6G" to seksG,
                    "fastsatt" to sykepengegrunnlagsfakta.fastsatt,
                    "arbeidsgivere" to sykepengegrunnlagsfakta.arbeidsgivere.map {
                        mapOf(
                            "arbeidsgiver" to it.arbeidsgiver,
                            "omregnetÅrsinntekt" to it.omregnetÅrsinntekt,
                            "skjønnsfastsatt" to it.skjønnsfastsatt,
                            "inntektskilde" to it.inntektskilde
                        )
                    },
                    "skjønnsfastsatt" to sykepengegrunnlagsfakta.skjønnsfastsatt
                )
            }
        )

        val utkastTilVedtak = PersonObserver.UtkastTilVedtakEvent(
            vedtaksperiodeId = vedtaksperiodeId,
            skjæringstidspunkt = skjæringstidspunkt,
            behandlingId = behandlingId,
            tags = tags.utgående,
            `6G`= when (val fakta = sykepengegrunnlagsfakta) {
                is FastsattIInfotrygd -> null
                is FastsattEtterHovedregel -> fakta.`6G`
                is FastsattEtterSkjønn -> fakta.`6G`
            }
        )

        fun avsluttetMedVedtak(vedtakFattet: LocalDateTime, historiskeHendelseIder: Set<UUID>) = PersonObserver.AvsluttetMedVedtakEvent(
            organisasjonsnummer = arbeidsgiver,
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            periode = periode,
            // Til ettertanke: AvsluttetMedVedtak har alle hendelseId'er ever på vedtaksperioden, mens godkjenningsbehov/utkast_til_vedtak har kun behandlingens
            hendelseIder = hendelseIder + historiskeHendelseIder,
            skjæringstidspunkt = skjæringstidspunkt,
            sykepengegrunnlag = sykepengegrunnlag,
            // Til ettertanke: Denne mappes ut i JSON som "grunnlagForSykepengegrunnlag"
            beregningsgrunnlag = beregningsgrunnlagForAvsluttetMedVedtak,
            // Til ettertanke: Den var jo uventet, men er jo slik det har vært 🤷‍
            // Til ettertanke: Denne hentet data fra sykepengegrunnlagsfakta som har to desimaler
            // Til ettertanke: Denne mappes ut i JSON som "grunnlagForSykepengegrunnlagPerArbeidsgiver"
            omregnetÅrsinntektPerArbeidsgiver = omregnetÅrsinntektPerArbeidsgiverForAvsluttedMedVedtak,
            inntekt = inntektForAvsluttetMedVedtak, // TODO: Til ettertanke: What? 👀 Denne håper jeg ingen bruker
            utbetalingId = utbetalingId,
            sykepengegrunnlagsbegrensning = sykepengegrunnlagsbegrensningForAvsluttetMedVedtak,
            vedtakFattetTidspunkt = vedtakFattet,
            // Til ettertanke: Akkurat i avsluttet i vedtak blir beløp i sykepengegrunnlagsfakta avrundet til to desimaler.
            sykepengegrunnlagsfakta = when (val fakta = sykepengegrunnlagsfakta) {
                is FastsattIInfotrygd -> fakta.copy(omregnetÅrsinntekt = fakta.omregnetÅrsinntekt.toDesimaler)
                is FastsattEtterHovedregel -> fakta.copy(
                    omregnetÅrsinntekt = fakta.omregnetÅrsinntekt.toDesimaler,
                    `6G` = fakta.`6G`.toDesimaler,
                    arbeidsgivere = fakta.arbeidsgivere.map { it.copy(omregnetÅrsinntekt = it.omregnetÅrsinntekt.toDesimaler) }
                )
                is FastsattEtterSkjønn -> fakta.copy(
                    omregnetÅrsinntekt = fakta.omregnetÅrsinntekt.toDesimaler,
                    `6G` = fakta.`6G`.toDesimaler,
                    arbeidsgivere = fakta.arbeidsgivere.map { it.copy(omregnetÅrsinntekt = it.omregnetÅrsinntekt.toDesimaler, skjønnsfastsatt = it.skjønnsfastsatt.toDesimaler) }
                )
            }
        )
    }

    private enum class Tag {
        Forlengelse,
        Førstegangsbehandling,
        IngenNyArbeidsgiverperiode,
        Grunnbeløpsregulering,
        EnArbeidsgiver,
        FlereArbeidsgivere,
        `6GBegrenset`,
        Arbeidsgiverutbetaling,
        NegativArbeidsgiverutbetaling,
        Personutbetaling,
        NegativPersonutbetaling,
        IngenUtbetaling,
        SykepengegrunnlagUnder2G,
        InngangsvilkårFraInfotrygd,
        Avslag,
        DelvisInnvilget,
        Innvilget,
        TilkommenInntekt,
        Ferie,
        InntektFraAOrdningenLagtTilGrunn,
    }

    private companion object {
        private val Double.toDesimaler get() = toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        private val Set<Tag>.utgående get() = map { it.name }.toSet()

        private fun Sykepengegrunnlagsfakta.beregningsgrunnlagForAvsluttetMedVedtak() = when (val fakta = this) {
            is FastsattEtterSkjønn -> fakta.skjønnsfastsatt
            else -> fakta.omregnetÅrsinntekt
        }

        private fun Sykepengegrunnlagsfakta.inntektForAvsluttetMedVedtak() = beregningsgrunnlagForAvsluttetMedVedtak() / 12

        private fun Sykepengegrunnlagsfakta.omregnetÅrsinntektPerArbeidsgiverForAvsluttedMedVedtak(): Map<String, Double> = when (val fakta = this) {
            is FastsattIInfotrygd -> emptyMap()
            is FastsattEtterHovedregel -> fakta.arbeidsgivere.associate { it.arbeidsgiver to it.omregnetÅrsinntekt.toDesimaler }
            is FastsattEtterSkjønn -> fakta.arbeidsgivere.associate { it.arbeidsgiver to it.skjønnsfastsatt.toDesimaler }
        }

        private fun Sykepengegrunnlagsfakta.sykepengegrunnlagsbegrensningForAvsluttedMedVedtak(tags: Set<Tag>) = when {
            this is FastsattIInfotrygd -> "VURDERT_I_INFOTRYGD"
            tags.contains(Tag.`6GBegrenset`) -> "ER_6G_BEGRENSET"
            else -> "ER_IKKE_6G_BEGRENSET"
        }

        private fun Sykepengegrunnlagsfakta.periodetypeForGodkjenningsbehov(tags: Set<Tag>): String {
            val erForlengelse = tags.contains(Tag.Forlengelse)
            return when {
                this is FastsattIInfotrygd -> if (erForlengelse) "INFOTRYGDFORLENGELSE" else "OVERGANG_FRA_IT"
                else -> if (erForlengelse) "FORLENGELSE" else "FØRSTEGANGSBEHANDLING"
            }
        }

        private fun Sykepengegrunnlagsfakta.omregnedeÅrsinntekterForGodkjenningsbehov(arbeidsgiver: String): List<Map<String, Any>> = when (val fakta = this) {
            is FastsattIInfotrygd -> listOf(mapOf("organisasjonsnummer" to arbeidsgiver, "beløp" to fakta.omregnetÅrsinntekt))
            is FastsattEtterHovedregel -> fakta.arbeidsgivere.map { mapOf("organisasjonsnummer" to it.arbeidsgiver, "beløp" to it.omregnetÅrsinntekt ) }
            is FastsattEtterSkjønn -> fakta.arbeidsgivere.map { mapOf("organisasjonsnummer" to it.arbeidsgiver, "beløp" to it.omregnetÅrsinntekt ) } // Nei, ikke bug at det er omregnetÅrsinntekt
        }
    }
}
