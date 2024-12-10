package no.nav.helse.person

import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.Venteårsak.Hva.INNTEKTSMELDING
import no.nav.helse.person.Venteårsak.Hvorfor.FLERE_SKJÆRINGSTIDSPUNKT
import no.nav.helse.person.Venteårsak.Hvorfor.SKJÆRINGSTIDSPUNKT_FLYTTET_REVURDERING
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal data object AvventerRevurdering : Vedtaksperiodetilstand {
    override val type = AVVENTER_REVURDERING
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.behandlinger.forkastUtbetaling(aktivitetslogg)
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak? {
        return tilstand(vedtaksperiode, Aktivitetslogg()).venteårsak()
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterOverstyringIgangsattRevurdering(revurdering, aktivitetslogg)
        vedtaksperiode.behandlinger.forkastUtbetaling(aktivitetslogg)
    }

    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode): VedtaksperiodeVenter? {
        val venterPå = tilstand(vedtaksperiode, Aktivitetslogg()).venterPå() ?: nestemann
        return vedtaksperiode.vedtaksperiodeVenter(venterPå)
    }

    override fun gjenopptaBehandling(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        tilstand(vedtaksperiode, aktivitetslogg).gjenopptaBehandling(vedtaksperiode, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: UtbetalingHendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterUtbetalingHendelse(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad, aktivitetslogg)
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        if (påminnelse.skalReberegnes()) {
            vedtaksperiode.behandlinger.lagreGjenbrukbareOpplysninger(
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.arbeidsgiver.organisasjonsnummer,
                vedtaksperiode.arbeidsgiver,
                aktivitetslogg
            )
        }
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun skalHåndtereDager(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) =
        vedtaksperiode.skalHåndtereDagerRevurdering(dager, aktivitetslogg)

    override fun håndtertInntektPåSkjæringstidspunktet(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Inntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.inntektsmeldingHåndtert(hendelse)
    }

    private fun tilstand(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg): Tilstand {
        if (vedtaksperiode.harFlereSkjæringstidspunkt()) return HarFlereSkjæringstidspunkt(vedtaksperiode)
        if (vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) return TrengerInntektsmelding(
            vedtaksperiode
        )
        val førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver =
            vedtaksperiode.førstePeriodeAnnenArbeidsgiverSomTrengerInntektsmelding()
        if (førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver != null) return TrengerInntektsmeldingAnnenArbeidsgiver(
            førstePeriodeSomTrengerInntektsmeldingAnnenArbeidsgiver
        )
        if (vedtaksperiode.vilkårsgrunnlag == null) return KlarForVilkårsprøving
        return KlarForBeregning
    }

    private sealed interface Tilstand {
        fun venteårsak(): Venteårsak?
        fun venterPå(): Vedtaksperiode? = null
        fun gjenopptaBehandling(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg)
    }

    private data class TrengerInntektsmelding(private val vedtaksperiode: Vedtaksperiode) : Tilstand {
        override fun venterPå() = vedtaksperiode
        override fun venteårsak() = INNTEKTSMELDING fordi SKJÆRINGSTIDSPUNKT_FLYTTET_REVURDERING
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Trenger inntektsmelding for perioden etter igangsatt revurdering")
        }
    }

    private data class HarFlereSkjæringstidspunkt(private val vedtaksperiode: Vedtaksperiode) : Tilstand {
        override fun venterPå() = vedtaksperiode
        override fun venteårsak() = HJELP fordi FLERE_SKJÆRINGSTIDSPUNKT
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Denne perioden har flere skjæringstidspunkt slik den står nå. Saksbehandler må inn å vurdere om det kan overstyres dager på en slik måte at det kun er ett skjæringstidspunkt. Om ikke må den kastes ut av Speil.")
        }
    }

    private data class TrengerInntektsmeldingAnnenArbeidsgiver(private val trengerInntektsmelding: Vedtaksperiode) :
        Tilstand {
        override fun venteårsak() = trengerInntektsmelding.venteårsak()
        override fun venterPå() = trengerInntektsmelding
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) {
            aktivitetslogg.info("Trenger inntektsmelding på annen arbeidsgiver etter igangsatt revurdering")
        }
    }

    private data object KlarForVilkårsprøving : Tilstand {
        override fun venteårsak() = null
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) {
            endreTilstand(vedtaksperiode, aktivitetslogg, AvventerVilkårsprøvingRevurdering) {
                aktivitetslogg.info("Trenger å utføre vilkårsprøving før vi kan beregne utbetaling for revurderingen.")
            }
        }
    }

    private data object KlarForBeregning : Tilstand {
        override fun venteårsak() = null
        override fun gjenopptaBehandling(
            vedtaksperiode: Vedtaksperiode,
            aktivitetslogg: IAktivitetslogg
        ) {
            endreTilstand(vedtaksperiode, aktivitetslogg, AvventerHistorikkRevurdering)
        }
    }
}
