package no.nav.helse.person.builders

import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.AvsluttetMedVedtakEvent.FastsattIInfotrygd
import no.nav.helse.person.PersonObserver.AvsluttetMedVedtakEvent.FastsattISpeil
import no.nav.helse.person.PersonObserver.AvsluttetMedVedtakEvent.FastsattISpeil.Arbeidsgiver
import no.nav.helse.person.PersonObserver.AvsluttetMedVedtakEvent.Sykepengegrunnlagsfakta
import no.nav.helse.person.inntekt.Sykepengegrunnlag.Begrensning
import no.nav.helse.person.inntekt.Sykepengegrunnlag.Begrensning.ER_6G_BEGRENSET
import no.nav.helse.økonomi.Inntekt

internal class VedtakFattetBuilder(
    private val fødselsnummer: String,
    private val aktørId: String,
    private val organisasjonsnummer: String,
    private val vedtaksperiodeId: UUID,
    private val behandlingId: UUID,
    private val periode: Periode,
    private val hendelseIder: Set<UUID>,
    private val skjæringstidspunkt: LocalDate
) {
    private var sykepengegrunnlag =  Inntekt.INGEN
    private var beregningsgrunnlag = Inntekt.INGEN
    private var begrensning: Begrensning? = null
    private var vedtakFattetTidspunkt = LocalDateTime.now()
    private lateinit var utbetalingId: UUID

    internal fun utbetalingId(id: UUID) = apply { this.utbetalingId = id }
    internal fun utbetalingVurdert(tidspunkt: LocalDateTime) = apply { this.vedtakFattetTidspunkt = tidspunkt }
    internal fun sykepengegrunnlag(sykepengegrunnlag: Inntekt) = apply { this.sykepengegrunnlag = sykepengegrunnlag }
    internal fun beregningsgrunnlag(beregningsgrunnlag: Inntekt) = apply { this.beregningsgrunnlag = beregningsgrunnlag }
    internal fun begrensning(begrensning: Begrensning) = apply { this.begrensning = begrensning }

    private lateinit var sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta
    internal fun sykepengegrunnlagsfakta(sykepengegrunnlagsfakta: Sykepengegrunnlagsfakta) = apply { this.sykepengegrunnlagsfakta = sykepengegrunnlagsfakta }

    internal fun result(): PersonObserver.AvsluttetMedVedtakEvent {
        val omregnetÅrsinntektPerArbeidsgiverÅrlig = when (val f = sykepengegrunnlagsfakta) {
            is FastsattISpeil -> f.arbeidsgivere.associate { it.arbeidsgiver to (it.skjønnsfastsatt ?: it.omregnetÅrsinntekt) }
            else -> emptyMap()
        }
        return PersonObserver.AvsluttetMedVedtakEvent(
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            periode = periode,
            hendelseIder = hendelseIder,
            skjæringstidspunkt = skjæringstidspunkt,
            sykepengegrunnlag = sykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
            beregningsgrunnlag = beregningsgrunnlag.reflection { årlig, _, _, _ -> årlig },
            omregnetÅrsinntektPerArbeidsgiver = omregnetÅrsinntektPerArbeidsgiverÅrlig,
            inntekt = beregningsgrunnlag.reflection { _, månedlig, _, _ -> månedlig },
            utbetalingId = utbetalingId,
            sykepengegrunnlagsbegrensning = when (begrensning) {
                ER_6G_BEGRENSET -> "ER_6G_BEGRENSET"
                Begrensning.ER_IKKE_6G_BEGRENSET -> "ER_IKKE_6G_BEGRENSET"
                Begrensning.VURDERT_I_INFOTRYGD -> "VURDERT_I_INFOTRYGD"
                else -> "VET_IKKE"
            },
            vedtakFattetTidspunkt = vedtakFattetTidspunkt,
            sykepengegrunnlagsfakta = sykepengegrunnlagsfakta
        )
    }

    sealed class SykepengegrunnlagsfaktaBuilder {
        protected val Inntekt.årlig get() = reflection { årlig, _, _, _ -> årlig }.toDesimaler
        protected val Double.toDesimaler get() = toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        abstract fun build(): Sykepengegrunnlagsfakta
    }
    internal class FastsattIInfotrygdBuilder(private val omregnetÅrsinntekt: Inntekt) : SykepengegrunnlagsfaktaBuilder() {
        override fun build() = FastsattIInfotrygd(omregnetÅrsinntekt.årlig)
    }
    internal class FastsattISpleisBuilder(
        private val omregnetÅrsinntekt: Inntekt,
        private val `6G`: Inntekt
    ) : SykepengegrunnlagsfaktaBuilder() {
        private lateinit var innrapportertÅrsinntekt: Inntekt
        internal fun innrapportertÅrsinntekt(innrapportertÅrsinntekt: Inntekt) = apply { this.innrapportertÅrsinntekt = innrapportertÅrsinntekt }

        private val arbeidsgivere = mutableListOf<Arbeidsgiver>()
        fun arbeidsgiver(arbeidsgiver: String, omregnetÅrsinntekt: Inntekt, skjønnsfastsatt: Inntekt?) = apply {
            arbeidsgivere.add(Arbeidsgiver(arbeidsgiver, omregnetÅrsinntekt.årlig, skjønnsfastsatt?.årlig))
        }
        override fun build() = FastsattISpeil(
            omregnetÅrsinntekt = omregnetÅrsinntekt.årlig,
            `6G`= `6G`.årlig,
            arbeidsgivere = arbeidsgivere.toList()
        )
    }
}
