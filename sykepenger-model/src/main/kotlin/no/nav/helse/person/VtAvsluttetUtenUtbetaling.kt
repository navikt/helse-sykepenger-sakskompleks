package no.nav.helse.person

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.DagerFraInntektsmelding
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Revurderingseventyr
import no.nav.helse.hendelser.Søknad
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.person.Venteårsak.Companion.fordi
import no.nav.helse.person.Venteårsak.Companion.utenBegrunnelse
import no.nav.helse.person.Venteårsak.Hva.HJELP
import no.nav.helse.person.Venteårsak.Hvorfor.VIL_OMGJØRES
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_38
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RV_1
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverFaktaavklartInntekt
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import org.slf4j.LoggerFactory

internal data object AvsluttetUtenUtbetaling : Vedtaksperiodetilstand {
    override val type = AVSLUTTET_UTEN_UTBETALING
    override val erFerdigBehandlet = true

    private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    override fun entering(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        val arbeidsgiverperiode =
            vedtaksperiode.arbeidsgiver.arbeidsgiverperiodeHensyntattEgenmeldinger(vedtaksperiode.periode)
        if (arbeidsgiverperiode?.forventerInntekt(vedtaksperiode.periode) == true) {
            // Dersom egenmeldingene hinter til at perioden er utenfor AGP, da ønsker vi å sende en ekte forespørsel til arbeidsgiver om opplysninger
            vedtaksperiode.sendTrengerArbeidsgiveropplysninger(arbeidsgiverperiode)
        }
        val utbetalingstidslinje = forsøkÅLageUtbetalingstidslinje(vedtaksperiode)
        vedtaksperiode.behandlinger.avsluttUtenVedtak(
            vedtaksperiode.arbeidsgiver,
            aktivitetslogg, utbetalingstidslinje
        )
        vedtaksperiode.person.gjenopptaBehandling(aktivitetslogg)
    }

    override fun lagUtbetalingstidslinje(
        vedtaksperiode: Vedtaksperiode,
        inntekt: ArbeidsgiverFaktaavklartInntekt?
    ): Utbetalingstidslinje {
        val benyttetInntekt = inntekt ?: vedtaksperiode.defaultinntektForAUU()
        return vedtaksperiode.behandlinger.lagUtbetalingstidslinje(
            benyttetInntekt,
            vedtaksperiode.jurist,
            vedtaksperiode.refusjonstidslinje
        )
    }

    private fun forsøkÅLageUtbetalingstidslinje(vedtaksperiode: Vedtaksperiode): Utbetalingstidslinje {
        val faktaavklarteInntekter = vedtaksperiode.vilkårsgrunnlag?.faktaavklarteInntekter()
            ?.forArbeidsgiver(vedtaksperiode.arbeidsgiver.organisasjonsnummer)
        return try {
            lagUtbetalingstidslinje(vedtaksperiode, faktaavklarteInntekter)
        } catch (err: Exception) {
            sikkerLogg.warn(
                "klarte ikke lage utbetalingstidslinje for auu: ${err.message}, {}",
                kv("vedtaksperiodeId", vedtaksperiode.id),
                err
            )
            Utbetalingstidslinje()
        }
    }

    override fun leaving(vedtaksperiode: Vedtaksperiode, aktivitetslogg: IAktivitetslogg) {
        vedtaksperiode.behandlinger.bekreftÅpenBehandling(vedtaksperiode.arbeidsgiver)
    }

    private fun skalOmgjøres(vedtaksperiode: Vedtaksperiode): Boolean {
        return vedtaksperiode.forventerInntekt()
    }

    override fun venteårsak(vedtaksperiode: Vedtaksperiode): Venteårsak {
        if (!skalOmgjøres(vedtaksperiode)) return HJELP.utenBegrunnelse
        return HJELP fordi VIL_OMGJØRES
    }

    override fun venter(vedtaksperiode: Vedtaksperiode, nestemann: Vedtaksperiode) =
        if (!skalOmgjøres(vedtaksperiode)) null
        else vedtaksperiode.vedtaksperiodeVenter(vedtaksperiode)

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        anmodningOmForkasting: AnmodningOmForkasting,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.etterkomAnmodningOmForkasting(anmodningOmForkasting, aktivitetslogg)
    }

    override fun igangsettOverstyring(
        vedtaksperiode: Vedtaksperiode,
        revurdering: Revurderingseventyr,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.behandlinger.sikreNyBehandling(
            vedtaksperiode.arbeidsgiver,
            revurdering.hendelse,
            vedtaksperiode.person.beregnSkjæringstidspunkt(),
            vedtaksperiode.arbeidsgiver.beregnArbeidsgiverperiode(vedtaksperiode.jurist)
        )
        if (skalOmgjøres(vedtaksperiode)) {
            revurdering.inngåSomEndring(vedtaksperiode, aktivitetslogg, vedtaksperiode.periode)
            revurdering.loggDersomKorrigerendeSøknad(
                aktivitetslogg,
                "Startet omgjøring grunnet korrigerende søknad"
            )
            vedtaksperiode.videreførEksisterendeRefusjonsopplysninger(aktivitetslogg = aktivitetslogg)
            aktivitetslogg.info(RV_RV_1.varseltekst)
            if (vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivitetslogg)) {
                aktivitetslogg.info("mangler nødvendige opplysninger fra arbeidsgiver")
                return endreTilstand(vedtaksperiode, aktivitetslogg, AvventerInntektsmelding)
            }
        }
        endreTilstand(vedtaksperiode, aktivitetslogg, AvventerBlokkerendePeriode)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        søknad: Søknad,
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: List<Arbeidsgiver>,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        aktivitetslogg.info("Prøver å igangsette revurdering grunnet korrigerende søknad")
        vedtaksperiode.håndterOverlappendeSøknadRevurdering(søknad, aktivitetslogg)
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        dager: DagerFraInntektsmelding,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.håndterDager(dager, aktivitetslogg)
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) {
            if (vedtaksperiode.arbeidsgiver.kanForkastes(
                    vedtaksperiode,
                    aktivitetslogg
                )
            ) return vedtaksperiode.forkast(dager.hendelse, aktivitetslogg)
            return vedtaksperiode.behandlinger.avsluttUtenVedtak(
                arbeidsgiver = vedtaksperiode.arbeidsgiver,
                aktivitetslogg = aktivitetslogg,
                utbetalingstidslinje = forsøkÅLageUtbetalingstidslinje(vedtaksperiode)
            )
        }
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: Hendelse,
        aktivitetslogg: IAktivitetslogg,
        infotrygdhistorikk: Infotrygdhistorikk
    ) {
        if (!skalOmgjøres(vedtaksperiode)) return
        vedtaksperiode.behandlinger.sikreNyBehandling(
            vedtaksperiode.arbeidsgiver,
            hendelse,
            vedtaksperiode.person.beregnSkjæringstidspunkt(),
            vedtaksperiode.arbeidsgiver.beregnArbeidsgiverperiode(vedtaksperiode.jurist)
        )

        val aktivAktivitetslogg = håndterFørstegangsbehandling(aktivitetslogg, vedtaksperiode)

        infotrygdhistorikk.valider(
            aktivAktivitetslogg,
            vedtaksperiode.periode,
            vedtaksperiode.skjæringstidspunkt,
            vedtaksperiode.arbeidsgiver.organisasjonsnummer
        )

        if (aktivAktivitetslogg.harFunksjonelleFeilEllerVerre()) {
            aktivAktivitetslogg.info("Forkaster perioden fordi Infotrygdhistorikken ikke validerer")
            return vedtaksperiode.forkast(hendelse, aktivAktivitetslogg)
        }
        if (vedtaksperiode.måInnhenteInntektEllerRefusjon(aktivAktivitetslogg)) {
            aktivAktivitetslogg.info("Forkaster perioden fordi perioden har ikke tilstrekkelig informasjon til utbetaling")
            return vedtaksperiode.forkast(hendelse, aktivAktivitetslogg)
        }
        aktivAktivitetslogg.varsel(RV_IT_38)
        vedtaksperiode.person.igangsettOverstyring(
            Revurderingseventyr.infotrygdendring(
                hendelse,
                vedtaksperiode.skjæringstidspunkt,
                vedtaksperiode.periode
            ),
            aktivAktivitetslogg
        )
    }

    override fun håndter(vedtaksperiode: Vedtaksperiode, påminnelse: Påminnelse, aktivitetslogg: IAktivitetslogg) {
        forsøkÅLageUtbetalingstidslinje(vedtaksperiode)

        if (!skalOmgjøres(vedtaksperiode) && vedtaksperiode.behandlinger.erAvsluttet()) return aktivitetslogg.info("Forventer ikke inntekt. Vil forbli i AvsluttetUtenUtbetaling")
        påminnelse.eventyr(vedtaksperiode.skjæringstidspunkt, vedtaksperiode.periode)?.also {
            aktivitetslogg.info("Reberegner perioden ettersom det er ønsket")
            vedtaksperiode.person.igangsettOverstyring(it, aktivitetslogg)
        }
    }

    override fun håndter(
        vedtaksperiode: Vedtaksperiode,
        hendelse: OverstyrTidslinje,
        aktivitetslogg: IAktivitetslogg
    ) {
        vedtaksperiode.revurderTidslinje(hendelse, aktivitetslogg)
    }
}
