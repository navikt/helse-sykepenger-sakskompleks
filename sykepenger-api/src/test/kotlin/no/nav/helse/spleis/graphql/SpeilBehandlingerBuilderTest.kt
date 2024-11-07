package no.nav.helse.spleis.graphql

import java.time.LocalDate
import java.time.LocalDate.EPOCH
import java.util.UUID
import no.nav.helse.Grunnbeløp.Companion.halvG
import no.nav.helse.april
import no.nav.helse.august
import no.nav.helse.den
import no.nav.helse.desember
import no.nav.helse.erHelg
import no.nav.helse.februar
import no.nav.helse.fredag
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.september
import no.nav.helse.spleis.speil.dto.AnnullertPeriode
import no.nav.helse.spleis.speil.dto.BeregnetPeriode
import no.nav.helse.spleis.speil.dto.Inntekt
import no.nav.helse.spleis.speil.dto.Inntektkilde
import no.nav.helse.spleis.speil.dto.Periodetilstand
import no.nav.helse.spleis.speil.dto.Periodetilstand.Annullert
import no.nav.helse.spleis.speil.dto.Periodetilstand.AvventerInntektsopplysninger
import no.nav.helse.spleis.speil.dto.Periodetilstand.ForberederGodkjenning
import no.nav.helse.spleis.speil.dto.Periodetilstand.IngenUtbetaling
import no.nav.helse.spleis.speil.dto.Periodetilstand.RevurderingFeilet
import no.nav.helse.spleis.speil.dto.Periodetilstand.TilAnnullering
import no.nav.helse.spleis.speil.dto.Periodetilstand.TilGodkjenning
import no.nav.helse.spleis.speil.dto.Periodetilstand.TilUtbetaling
import no.nav.helse.spleis.speil.dto.Periodetilstand.Utbetalt
import no.nav.helse.spleis.speil.dto.Periodetilstand.UtbetaltVenterPåAnnenPeriode
import no.nav.helse.spleis.speil.dto.Periodetilstand.VenterPåAnnenPeriode
import no.nav.helse.spleis.speil.dto.SpeilGenerasjonDTO
import no.nav.helse.spleis.speil.dto.SpeilTidslinjeperiode
import no.nav.helse.spleis.speil.dto.SykdomstidslinjedagType
import no.nav.helse.spleis.speil.dto.Tidslinjeperiodetype
import no.nav.helse.spleis.speil.dto.Tidslinjeperiodetype.FORLENGELSE
import no.nav.helse.spleis.speil.dto.Tidslinjeperiodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.spleis.speil.dto.UberegnetPeriode
import no.nav.helse.spleis.speil.dto.Utbetalingstatus
import no.nav.helse.spleis.speil.dto.Utbetalingstatus.GodkjentUtenUtbetaling
import no.nav.helse.spleis.speil.dto.Utbetalingstatus.IkkeGodkjent
import no.nav.helse.spleis.speil.dto.Utbetalingstatus.Overført
import no.nav.helse.spleis.speil.dto.Utbetalingstatus.Ubetalt
import no.nav.helse.spleis.speil.dto.Utbetalingtype
import no.nav.helse.spleis.speil.dto.Utbetalingtype.ANNULLERING
import no.nav.helse.spleis.speil.dto.Utbetalingtype.REVURDERING
import no.nav.helse.spleis.speil.dto.Utbetalingtype.UTBETALING
import no.nav.helse.spleis.testhelpers.OverstyrtArbeidsgiveropplysning
import no.nav.helse.søndag
import no.nav.helse.til
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SpeilBehandlingerBuilderTest : AbstractE2ETest() {

    @Test
    fun `En periode i AvventerInntektsmelding venter på inntektsopplysninger`() {
        val søknadA1 = håndterSøknad(1.januar til 25.januar, orgnummer = a1)

        generasjoner {
            0.generasjon {
                uberegnetPeriode(0) medTilstand AvventerInntektsopplysninger
            }
        }
    }

    @Test
    fun `periodene viser til overstyring av sykepengegrunnlag i hendelser`() {
        val søknadA1 = håndterSøknad(1.januar til 25.januar, orgnummer = a1)
        val søknadA2 = håndterSøknad(1.januar til 25.januar, orgnummer = a2)
        val imA1 = håndterInntektsmelding(1.januar, orgnummer = a1)
        val imA2 = håndterInntektsmelding(1.januar, orgnummer = a2)

        val søknadA1Forlengelse = håndterSøknad(Sykdom(26.januar, 31.januar, 100.prosent), orgnummer = a1)
        val søknadA2Forlengelse = håndterSøknad(Sykdom(26.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterVilkårsgrunnlagTilUtbetalt()
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()

        val skjønnsfastsettelse = UUID.randomUUID()
        håndterSkjønnsmessigFastsettelse(
            1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT + 500.daglig),
                OverstyrtArbeidsgiveropplysning(a2, INNTEKT - 500.daglig),
            ), skjønnsfastsettelse
        )
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()

        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()

        generasjoner(a1) {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medHendelser setOf(søknadA1Forlengelse, skjønnsfastsettelse)
                beregnetPeriode(1) medHendelser setOf(søknadA1, imA1, skjønnsfastsettelse)
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medHendelser setOf(søknadA1Forlengelse)
                beregnetPeriode(1) medHendelser setOf(søknadA1, imA1)
            }
        }
        generasjoner(a2) {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medHendelser setOf(søknadA2Forlengelse, skjønnsfastsettelse)
                beregnetPeriode(1) medHendelser setOf(søknadA2, imA2, skjønnsfastsettelse)
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medHendelser setOf(søknadA2Forlengelse)
                beregnetPeriode(1) medHendelser setOf(søknadA2, imA2)
            }
        }
    }

    @Test
    fun `forkastet auu`() {
        val søknad = håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))
        håndterSøknad(Sykdom(11.januar, 20.januar, 100.prosent))
        val im = håndterInntektsmelding(1.januar)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 1.januar til 10.januar medTilstand Annullert
            }
            1.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 1.januar til 10.januar medTilstand IngenUtbetaling medHendelser setOf(søknad, im)
            }
            2.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 1.januar til 10.januar medTilstand IngenUtbetaling medHendelser setOf(søknad)
            }
        }
    }

    @Test
    fun `omgjøre kort periode får referanse til inntektsmeldingen som inneholder inntekten som er lagt til grunn`() {
        val søknad1 = håndterSøknad(Sykdom(1.januar, 24.januar, 100.prosent))
        val inntektsmeldingbeløp1 = INNTEKT
        val søknad2 = håndterSøknad(Sykdom(25.januar, søndag den 11.februar, 100.prosent))
        val inntektsmelding1 = håndterInntektsmelding(
            listOf(25.januar til fredag den 9.februar),
            vedtaksperiode = 2,
            beregnetInntekt = inntektsmeldingbeløp1
        )
        val inntektsmeldingbeløp2 = INNTEKT*1.1
        val inntektsmelding2 = håndterInntektsmelding(1.januar, beregnetInntekt = inntektsmeldingbeløp2, vedtaksperiode = 1)
        håndterVilkårsgrunnlag()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand ForberederGodkjenning medHendelser setOf(søknad2, inntektsmelding1)
                uberegnetPeriode(1) medTilstand IngenUtbetaling medHendelser setOf(søknad1, inntektsmelding1, inntektsmelding2)
            }
            1.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand IngenUtbetaling medHendelser setOf(søknad2, inntektsmelding1)
                uberegnetPeriode(1) medTilstand IngenUtbetaling medHendelser setOf(søknad1, inntektsmelding1)
            }
        }
    }

    @Test
    fun `revurdere før forlengelse utbetales`() {
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        håndterSøknad(Sykdom(17.januar, 22.januar, 100.prosent))
        håndterSøknad(Sykdom(23.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(1.januar)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()
        håndterYtelserTilGodkjenning()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(22.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()
        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                uberegnetPeriode(0) fra 23.januar til 31.januar medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) fra 17.januar til 22.januar avType REVURDERING medTilstand TilGodkjenning
                uberegnetPeriode(2) fra 1.januar til 16.januar medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) fra 17.januar til 22.januar avType UTBETALING medTilstand Utbetalt
                uberegnetPeriode(1) fra 1.januar til 16.januar medTilstand IngenUtbetaling
            }
            2.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 1.januar til 16.januar medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `syk nav-dager i to korte perioder`() {
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent), Ferie(1.januar, 15.januar))
        håndterSøknad(Sykdom(16.januar, 20.januar, 100.prosent), Ferie(16.januar, 20.januar))
        håndterInntektsmelding(1.januar, begrunnelseForReduksjonEllerIkkeUtbetalt = "ManglerOpptjening")
        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra 16.januar til 20.januar medTilstand VenterPåAnnenPeriode
                uberegnetPeriode(1) fra 1.januar til 15.januar medTilstand ForberederGodkjenning
            }
            1.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra 16.januar til 20.januar medTilstand IngenUtbetaling
                uberegnetPeriode(1) fra 1.januar til 15.januar medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `Manglende generasjon når det kommer IM som endrer AGP ved å endre dager i forkant av perioden`() {
        håndterSøknad(Sykdom(7.august, 20.august, 100.prosent))
        håndterSøknad(Sykdom(21.august, 1.september, 100.prosent))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(24.juli til 25.juli, 7.august til 20.august),)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()
        // 21 & 22.August utbetalingsdager

        håndterInntektsmelding(7.august)
        håndterYtelserTilUtbetalt()
        // 21 & 22.August agp -- denne blir ikke en generasjon

        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(24.juli, Dagtype.Egenmeldingsdag),
            ManuellOverskrivingDag(25.juli, Dagtype.Egenmeldingsdag)
        ))

        håndterYtelserTilGodkjenning()
        // 21 & 22.August utbetalingsdager

        generasjoner {
            assertEquals(4, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) avType REVURDERING fra 21.august til 1.september medTilstand TilGodkjenning
                uberegnetPeriode(1) fra 24.juli til 20.august medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) avType REVURDERING fra 21.august til 1.september medTilstand Utbetalt
                uberegnetPeriode(1) fra 24.juli til 20.august medTilstand IngenUtbetaling
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) avType UTBETALING medTilstand Utbetalt
                uberegnetPeriode(1) medTilstand IngenUtbetaling
            }
            3.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `avvik i inntekt slik at dager avslås pga minsteinntekt`() {
        val beregnetInntekt = halvG.beløp(1.januar)
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar,  28.februar)
        håndterInntektsmelding(1.januar, beregnetInntekt = beregnetInntekt - 1.daglig,)
        håndterYtelserTilUtbetalt()
        håndterYtelserTilGodkjenning()
        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) avType REVURDERING fra 1.februar til 28.februar medTilstand TilGodkjenning
                beregnetPeriode(1) avType REVURDERING fra 1.januar til 31.januar medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) avType UTBETALING medTilstand Utbetalt
                beregnetPeriode(1) avType UTBETALING medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `revurdere skjæringstidspunktet flere ganger før forlengelsene`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT - 500.daglig, "")))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()
        håndterYtelserTilGodkjenning()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT - 550.daglig, "")))
        håndterYtelserTilGodkjenning()
        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                uberegnetPeriode(0) fra 1.mars til 31.mars medTilstand UtbetaltVenterPåAnnenPeriode
                uberegnetPeriode(1) fra 1.februar til 28.februar medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(2) avType REVURDERING fra 1.januar til 31.januar medTilstand TilGodkjenning
            }
            1.generasjon {
                // fordi de to andre ikke ble utbetalt før det startet ny revurdering
                assertEquals(1, size)
                beregnetPeriode(0) avType REVURDERING fra 1.januar til 31.januar medTilstand Utbetalt
            }
            2.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) avType UTBETALING medTilstand Utbetalt
                beregnetPeriode(1) avType UTBETALING medTilstand Utbetalt
                beregnetPeriode(2) avType UTBETALING medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `revurdere skjæringstidspunktet flere ganger`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT - 500.daglig, "")))
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT - 2.daglig, "")))
        håndterYtelserTilGodkjenning()
        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                uberegnetPeriode(1) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(2) medTilstand TilGodkjenning
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) avType REVURDERING fra 1.mars til 31.mars medTilstand Utbetalt
                beregnetPeriode(1) avType REVURDERING fra 1.februar til 28.februar medTilstand Utbetalt
                beregnetPeriode(2) avType REVURDERING fra 1.januar til 31.januar  medTilstand Utbetalt
            }
            2.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) avType UTBETALING medTilstand Utbetalt
                beregnetPeriode(1) avType UTBETALING medTilstand Utbetalt
                beregnetPeriode(2) avType UTBETALING medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `periodetype ved enkel revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT - 500.0.daglig, "")))
        håndterYtelserTilGodkjenning()
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra 1.februar til 28.februar medTilstand UtbetaltVenterPåAnnenPeriode medPeriodetype FORLENGELSE
                beregnetPeriode(1) fra 1.januar til 31.januar medTilstand TilGodkjenning medPeriodetype FØRSTEGANGSBEHANDLING
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) fra 1.februar til 28.februar medTilstand Utbetalt medPeriodetype FORLENGELSE
                beregnetPeriode(1) fra 1.januar til 31.januar medTilstand Utbetalt medPeriodetype FØRSTEGANGSBEHANDLING
            }
        }
    }

    @Test
    fun `person med foreldet dager`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAV = 1.juni.atStartOfDay())
        håndterInntektsmelding(1.januar,)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(1, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) fra 1.februar til 28.februar medTilstand TilGodkjenning medPeriodetype FORLENGELSE
                beregnetPeriode(1) harTidslinje (1.januar til 31.januar to SykdomstidslinjedagType.FORELDET_SYKEDAG) medTilstand IngenUtbetaling medPeriodetype FØRSTEGANGSBEHANDLING
            }
        }
    }

    @Test
    fun `annullerer feilet revurdering`() {
        val utbetaling = nyttVedtak(1.januar, 31.januar)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        håndterAnnullerUtbetaling(utbetaling)

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(1, size)
                annullertPeriode(0) er Overført avType ANNULLERING medTilstand TilAnnullering
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
            }
            2.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `tar med vilkårsgrunnlag med ikke-rapportert inntekt`() {
        // A2 må være først i listen for at buggen skal intreffe
        nyttVedtak(1.januar(2017), 31.januar(2017), orgnummer = a2)

        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1,)
        håndterVilkårsgrunnlag(
            inntekter = listOf(a1 to INNTEKT),
            arbeidsforhold = listOf(a1 to EPOCH, a2 to 1.desember(2017))
        )
        håndterYtelserTilGodkjenning()

        generasjoner(a1) {
            0.generasjon {
                val vilkårsgrunnlag = beregnetPeriode(0).vilkårsgrunnlag()
                val inntektsgrunnlag = vilkårsgrunnlag.inntekter.firstOrNull { it.organisasjonsnummer == a2 }
                assertEquals(Inntekt(Inntektkilde.IkkeRapportert, 0.0, 0.0, null), inntektsgrunnlag?.omregnetÅrsinntekt)
            }
        }
    }

    @Test
    fun `to perioder - første blir revurdert to ganger, deretter blir andre revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilUtbetalt()
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterOverstyrTidslinje((29.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Sykedag, 100) })
        håndterYtelserTilUtbetalt()
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        håndterOverstyrTidslinje((27.februar til 28.februar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(4, size)

            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand TilGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }

            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er GodkjentUtenUtbetaling avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }

            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er GodkjentUtenUtbetaling avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }

            3.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `to perioder med gap - siste blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(2.februar, 28.februar, vedtaksperiode = 2)

        håndterOverstyrTidslinje((27.februar til 28.februar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (2.februar til 28.februar) medAntallDager 27 forkastet false medTilstand TilGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (2.februar til 28.februar) medAntallDager 27 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `én periode som blir annullert`() {
        val utbetaling = nyttVedtak(1.januar, 31.januar)
        håndterAnnullerUtbetaling(utbetaling)

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(1, size)
                annullertPeriode(0) er Overført avType ANNULLERING fra (1.januar til 31.januar) medAntallDager 0 forkastet true medTilstand TilAnnullering
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `to perioder som blir annullert`() {
        nyttVedtak(1.januar, 31.januar)
        val utbetaling = forlengVedtak(1.februar, 28.februar)
        håndterAnnullerUtbetaling(utbetaling)

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                annullertPeriode(0) er Overført avType ANNULLERING fra (1.februar til 28.februar) medAntallDager 0 forkastet true medTilstand TilAnnullering
                annullertPeriode(1) er Overført avType ANNULLERING fra (1.januar til 31.januar) medAntallDager 0 forkastet true medTilstand TilAnnullering
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `to perioder som blir annullert - deretter nye perioder`() {
        nyttVedtak(1.januar, 31.januar)
        val utbetaling = forlengVedtak(1.februar, 28.februar)
        håndterAnnullerUtbetaling(utbetaling)
        håndterUtbetalt()

        nyttVedtak(1.april, 30.april, vedtaksperiode = 3)

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.april til 30.april) medAntallDager 30 forkastet false medTilstand Utbetalt
                annullertPeriode(1) er Utbetalingstatus.Annullert avType ANNULLERING fra (1.februar til 28.februar) medAntallDager 0 forkastet true medTilstand Annullert
                annullertPeriode(2) er Utbetalingstatus.Annullert avType ANNULLERING fra (1.januar til 31.januar) medAntallDager 0 forkastet true medTilstand Annullert
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `to arbeidsgiverperioder - siste blir annullert`() {
        nyttVedtak(1.januar, 31.januar)
        val utbetaling = nyttVedtak(1.mars, 31.mars, vedtaksperiode = 2)
        håndterAnnullerUtbetaling(utbetaling)

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                annullertPeriode(0) er Overført avType ANNULLERING fra (1.mars til 31.mars) medAntallDager 0 forkastet true medTilstand TilAnnullering
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `to perioder som blir revurdert - deretter forlengelse`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilUtbetalt()
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        forlengVedtak(1.mars, 31.mars)

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er GodkjentUtenUtbetaling avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(2) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false  medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `to perioder som blir revurdert - deretter forlengelse som så blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje((29.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilUtbetalt()
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        forlengVedtak(1.mars, 31.mars)
        håndterOverstyrTidslinje((1.mars til 31.mars).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilUtbetalt()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand IngenUtbetaling
                beregnetPeriode(1) er GodkjentUtenUtbetaling avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(2) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medAntallDager 31 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er GodkjentUtenUtbetaling avType REVURDERING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(2) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `korte perioder - arbeidsgiversøknader`() {
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))

        generasjoner {
            0.generasjon {
                uberegnetPeriode(0) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `kort periode med forlengelse`() {
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))
        håndterSøknad(Sykdom(16.januar, 15.februar, 100.prosent))
        håndterInntektsmelding(1.januar,)
        håndterVilkårsgrunnlagTilGodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Ubetalt avType UTBETALING fra (16.januar til 15.februar) medTilstand TilGodkjenning
                uberegnetPeriode(1) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `kort periode med forlengelse og revurdering av siste periode`() {
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))

        håndterSøknad(Sykdom(16.januar, 15.februar, 100.prosent))
        håndterInntektsmelding(1.januar,)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterOverstyrTidslinje((13.februar til 14.februar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (16.januar til 15.februar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
                uberegnetPeriode(1) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (16.januar til 15.februar) medAntallDager 31 forkastet false medTilstand Utbetalt
                uberegnetPeriode(1) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
            }
            2.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra (1.januar til 15.januar) medAntallDager 15 forkastet false medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `to perioder etter hverandre, nyeste er i venter-tilstand`() {
        tilGodkjenning(1.januar, 31.januar, a1)
        håndterSøknad(Sykdom(1.februar, 21.februar, 100.prosent))

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) er Ubetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
            }
        }
    }

    @Test
    fun `to førstegangsbehandlinger, nyeste er i venter-tilstand`() {
        tilGodkjenning(1.januar, 31.januar, a1)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand AvventerInntektsopplysninger
                beregnetPeriode(1) er Ubetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
            }
        }
    }

    @Test
    fun `tidligere generasjoner skal ikke inneholde perioder som venter eller venter på informasjon`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelserTilUtbetalt()

        generasjoner {
            0.generasjon {
                assertEquals(2, this.perioder.size)
                uberegnetPeriode(0) medTilstand AvventerInntektsopplysninger
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(1, this.perioder.size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `ventende periode etter førstegangsbehandling`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        generasjoner {
            assertEquals(1, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand ForberederGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `ventende periode etter revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelserTilUtbetalt()
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra (1.februar til 28.februar) medTilstand ForberederGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.januar til 31.januar) medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `ventende perioder med revurdert tidligere periode`() {
        nyttVedtak(1.januar, 31.januar)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelserTilGodkjenning()

        håndterOverstyrTidslinje((29.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra (1.februar til 28.februar) medAntallDager 28 forkastet false medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) er Ubetalt avType REVURDERING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand TilGodkjenning
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `periode uten utbetaling - kun ferie`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 31.januar))
        håndterInntektsmelding(1.januar,)
        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra (1.januar til 31.januar) medAntallDager 31 forkastet false medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra (1.januar til 31.januar) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `får riktig aldersvilkår per periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0).assertAldersvilkår(true, 26)
                beregnetPeriode(1).assertAldersvilkår(true, 25)
            }
        }
    }

    @Test
    fun `får riktig sykepengedager-vilkår per periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0).assertSykepengedagerVilkår(31, 217, 28.desember, 1.januar, true)
                beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            }
        }
    }

    @Test
    fun `får riktig vilkår per periode ved revurdering av siste periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje((27.februar til 28.februar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilGodkjenning()

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0).assertAldersvilkår(true, 26)
                beregnetPeriode(1).assertAldersvilkår(true, 25)
                beregnetPeriode(0).assertSykepengedagerVilkår(29, 219, 1.januar(2019), 1.januar, true)
                beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0).assertSykepengedagerVilkår(31, 217, 28.desember, 1.januar, true)
                beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            }
        }
    }

    @Test
    fun `får riktig vilkår per periode ved revurdering av første periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje((30.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilGodkjenning()

        generasjoner {
            0.generasjon {
                uberegnetPeriode(0) medTilstand  UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1).assertAldersvilkår(true, 25)
                // Revurdering av tidligere periode medfører at alle perioder berørt av revurderingen deler den samme utbetalingen, og derfor ender opp med samme
                // gjenstående dager, forbrukte dager og maksdato. Kan muligens skrives om i modellen slik at disse tallene kan fiskes ut fra utbetalingen gitt en
                // periode
                beregnetPeriode(1).assertSykepengedagerVilkår(9, 239, 1.januar(2019), 1.januar, true)
            }
            1.generasjon {
                beregnetPeriode(0).assertSykepengedagerVilkår(31, 217, 28.desember, 1.januar, true)
                beregnetPeriode(1).assertSykepengedagerVilkår(11, 237, 28.desember, 1.januar, true)
            }
        }
    }

    @Test
    fun `ta med personoppdrag`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingUtenRefusjon(1.januar, vedtaksperiode = 1)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        generasjoner {
            0.generasjon {
                assertEquals(1, size)
                assertEquals(0, this.perioder.first().sammenslåttTidslinje[16].utbetalingsinfo!!.arbeidsgiverbeløp)
                assertEquals(2161, this.perioder.first().sammenslåttTidslinje[16].utbetalingsinfo!!.personbeløp)
                assertEquals(0, beregnetPeriode(0).utbetaling.arbeidsgiverNettoBeløp)
                assertEquals(23771, beregnetPeriode(0).utbetaling.personNettoBeløp)
            }
        }
    }

    @Test
    fun `ag2 venter på ag1 mens ag1 er til godkjenning`() {
        tilGodkjenning(1.januar, 31.januar, a1 to 1, a2 to 1)

        generasjoner(a1) {
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand TilGodkjenning
            }
        }
        generasjoner(a2) {
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand VenterPåAnnenPeriode
            }
        }
    }

    @Test
    fun `periode med bare ferie`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent), Ferie(1.februar, 20.februar))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er GodkjentUtenUtbetaling medTilstand IngenUtbetaling
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `behandlingstyper i normal forlengelsesflyt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand AvventerInntektsopplysninger
                uberegnetPeriode(1) medTilstand AvventerInntektsopplysninger
            }
        }

        håndterInntektsmelding(1.januar,)
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                uberegnetPeriode(1) medTilstand ForberederGodkjenning
            }
        }

        håndterVilkårsgrunnlag()
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                uberegnetPeriode(1) medTilstand ForberederGodkjenning
            }
        }

        håndterYtelser()
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand ForberederGodkjenning
            }
        }

        håndterSimulering()
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilGodkjenning
            }
        }

        håndterUtbetalingsgodkjenning()
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilUtbetaling
            }
        }

        håndterUtbetalt()
        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand ForberederGodkjenning
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `Annullering av revurdering feilet`() {
        val utbetaling = nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        håndterAnnullerUtbetaling(utbetaling)
        håndterUtbetalt()


        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(1, size)
                annullertPeriode(0) er Utbetalingstatus.Annullert avType ANNULLERING medTilstand Annullert
            }

            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
            }

            2.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `revurdering av tidligere skjæringstidspunkt - opphører refusjon som treffer flere perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(a1, INNTEKT, "", null, listOf(Triple(1.januar, null, INGEN)))))
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING medTilstand Utbetalt fra (1.februar til 28.februar)
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING medTilstand Utbetalt fra (1.januar til 31.januar)
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt fra (1.februar til 28.februar)
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt fra (1.januar til 31.januar)
            }
        }
    }

    @Test
    fun `revurdering av tidligere skjæringstidspunkt - nyere revurdering med ingen endringer`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mai, 31.mai, vedtaksperiode = 2)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelserTilUtbetalt()

        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er GodkjentUtenUtbetaling avType REVURDERING medTilstand Utbetalt fra (1.mai til 31.mai)
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType REVURDERING medTilstand Utbetalt fra (1.januar til 31.januar)
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt fra (1.mai til 31.mai)
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt fra (1.januar til 31.januar)
            }
        }
    }

    @Test
    fun `revurdering feilet med flere perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        generasjoner {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) er IkkeGodkjent avType REVURDERING medTilstand RevurderingFeilet
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `utbetaling feilet`() {
        tilGodkjenning(1.januar, 31.januar)
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt(status = Oppdragstatus.FEIL)

        generasjoner {
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Overført avType UTBETALING medTilstand TilUtbetaling
            }
        }
    }

    @Test
    fun `En arbeidsgiver korrigerer inn litt mer ferie i ferien sin`() {
        nyeVedtak(1.januar, 31.januar, a1 to 1, a2 to 1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(1.februar, 28.februar), orgnummer = a1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(1.februar, 27.februar), orgnummer = a2)

        håndterYtelser()
        håndterUtbetalingsgodkjenning()

        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(1.februar, 28.februar), orgnummer = a2)
        håndterYtelser()
        håndterUtbetalingsgodkjenning()

        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        generasjoner(a1) {
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er GodkjentUtenUtbetaling avType REVURDERING medTilstand IngenUtbetaling
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            }
        }
        generasjoner(a2) {
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING medTilstand IngenUtbetaling
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING medTilstand Utbetalt
            }
        }

    }

    @Test
    fun `overlappende periode flere arbeidsgivere`() {
        nyeVedtak(1.januar, 31.januar, a1 to 1, a2 to 1)

        håndterSøknad(1.februar til 28.februar, a1)
        håndterSøknad(1.februar til 28.februar, a2)

        håndterYtelserTilGodkjenning()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)), orgnummer = a1)
        håndterYtelserTilGodkjenning()

        generasjoner(a1) {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilGodkjenning
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand Utbetalt
            }
        }
        generasjoner(a2) {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand UtbetaltVenterPåAnnenPeriode
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `lage generasjoner når a2 er i Avventer historikk revurdering og har blitt tildelt utbetaling`() {
        nyeVedtak(1.januar, 31.januar, a1 to 1, a2 to 1)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)), orgnummer = a1)
        håndterYtelserTilUtbetalt()
        generasjoner(a1) {
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand Utbetalt
            }
        }
        generasjoner(a2) {
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand ForberederGodkjenning
            }
        }
    }

    @Test
    fun `revurdering av flere arbeidsgivere`() {
        nyeVedtak(1.januar, 31.januar, a1 to 1, a2 to 1)
        forlengVedtak(1.februar, 28.februar, a1, a2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)), orgnummer = a1)
        håndterYtelser()

        generasjoner(a1) {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand ForberederGodkjenning
            }
        }
        generasjoner(a2) {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand UtbetaltVenterPåAnnenPeriode
            }
        }

        håndterSimulering()

        generasjoner(a1) {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilGodkjenning
            }
        }
        generasjoner(a2) {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand UtbetaltVenterPåAnnenPeriode
            }
        }

        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterYtelser()

        generasjoner(a1) {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
        generasjoner(a2) {
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilGodkjenning
            }
        }
    }

    @Test
    fun `flere revurderinger, deretter revurdering feilet`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand RevurderingFeilet
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `revurdering til kun ferie`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje((17.januar til 31.januar).map { ManuellOverskrivingDag(it, Dagtype.Feriedag) })
        håndterYtelserTilUtbetalt()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand IngenUtbetaling
            }

            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `annullering av revurdert periode i til godkjenning`() {
        nyttVedtak(1.mars, 31.mars)
        val utbetaling = nyttVedtak(1.mai, 31.mai, vedtaksperiode = 2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mai, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.mai, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterAnnullerUtbetaling(utbetaling)
        håndterUtbetalt()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                annullertPeriode(0) medTilstand Annullert
                beregnetPeriode(1) medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `annullering etter utbetaling underkjent`() {
        nyttVedtak(1.mars, 31.mars)
        val utbetalig = forlengVedtak(1.april, 30.april)
        forlengTilGodkjenning(1.mai, 31.mai)
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)
        håndterAnnullerUtbetaling(utbetalig)
        håndterUtbetalt()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `annullering av flere perioder`() {
        val førsteUtbetaling = nyttVedtak(1.mars, 31.mars)
        val sisteUtbetaling = nyttVedtak(1.mai, 31.mai, vedtaksperiode = 2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mai, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.mai, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterAnnullerUtbetaling(sisteUtbetaling)
        håndterUtbetalt()

        håndterAnnullerUtbetaling(førsteUtbetaling)
        håndterUtbetalt()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `annullering av enda flere perioder`() {
        val førsteUtbetaling = nyttVedtak(1.mars, 31.mars)
        val sisteUtbetaling = nyttVedtak(1.mai, 31.mai, vedtaksperiode = 2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mai, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.mai, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        håndterAnnullerUtbetaling(sisteUtbetaling)
        håndterUtbetalt()

        håndterAnnullerUtbetaling(førsteUtbetaling)
        håndterUtbetalt()

        val utbetaling = nyttVedtak(1.juli, 31.juli, vedtaksperiode = 3)

        håndterAnnullerUtbetaling(utbetaling)
        håndterUtbetalt()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
                annullertPeriode(2) medTilstand Annullert
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) medTilstand Utbetalt
                annullertPeriode(1) medTilstand Annullert
                annullertPeriode(2) medTilstand Annullert
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `flere perioder der første blir annullert, deretter ny periode, deretter annullering igjen`() {
        val januarutbetaling = nyttVedtak(1.januar, 31.januar)
        val marsutbetaling = nyttVedtak(1.mars, 31.mars, vedtaksperiode = 2)

        håndterAnnullerUtbetaling(marsutbetaling)
        håndterUtbetalt()

        val maiutbetaling = nyttVedtak(1.mai, 31.mai, vedtaksperiode = 3)

        håndterAnnullerUtbetaling(maiutbetaling)
        håndterUtbetalt()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) medTilstand Utbetalt
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }

        håndterAnnullerUtbetaling(januarutbetaling)
        håndterUtbetalt()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
                annullertPeriode(2) medTilstand Annullert
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) medTilstand Utbetalt
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `flere perioder der første blir annullert, deretter ny periode, deretter annullering igjen 2`() {
        val januarutbetaling = nyttVedtak(1.januar, 31.januar)
        val marsutbetaling = nyttVedtak(1.mars, 31.mars, vedtaksperiode = 2)

        håndterAnnullerUtbetaling(marsutbetaling)
        håndterUtbetalt()

        val maiutbetaling = nyttVedtak(1.mai, 31.mai, vedtaksperiode = 3)

        håndterAnnullerUtbetaling(maiutbetaling)
        håndterUtbetalt()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        val sisteutbetaling = håndterYtelserTilUtbetalt()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt avType REVURDERING
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) medTilstand Utbetalt
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt avType UTBETALING
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }

        håndterAnnullerUtbetaling(sisteutbetaling)
        håndterUtbetalt()

        generasjoner {
            assertEquals(4, size)
            0.generasjon {
                assertEquals(3, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
                annullertPeriode(2) medTilstand Annullert
            }
            1.generasjon {
                assertEquals(3, size)
                annullertPeriode(0) medTilstand Annullert
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt avType REVURDERING
            }
            2.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) medTilstand Utbetalt
                annullertPeriode(1) medTilstand Annullert
                beregnetPeriode(2) medTilstand Utbetalt avType UTBETALING
            }
            3.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `revurdering av tidligere skjæringstidspunkt`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars, vedtaksperiode = 2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                uberegnetPeriode(1) medTilstand ForberederGodkjenning
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }

        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilGodkjenning
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `inntektsmelding gjør at kort periode faller utenfor agp - før vilkårsprøving`() {
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))
        håndterSøknad(Sykdom(28.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(10.januar)
        håndterVilkårsgrunnlagTilGodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(3, size)
                uberegnetPeriode(0) medTilstand VenterPåAnnenPeriode
                beregnetPeriode(1) avType UTBETALING medTilstand TilGodkjenning
                uberegnetPeriode(2) medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand IngenUtbetaling
                uberegnetPeriode(1) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `avvist revurdering uten tidligere utbetaling kan forkastes`() {
        håndterSøknad(Sykdom(12.januar, 20.januar, 100.prosent))
        håndterSøknad(Sykdom(21.januar, 27.januar, 100.prosent))
        håndterInntektsmelding(10.januar)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        generasjoner {
            assertEquals(3, size)

            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra 21.januar til 27.januar medTilstand Annullert
                uberegnetPeriode(1) fra 10.januar til 20.januar medTilstand Annullert
            }
            1.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 10.januar til 20.januar medTilstand IngenUtbetaling
            }
            2.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra 21.januar til 27.januar medTilstand IngenUtbetaling
                uberegnetPeriode(1) fra 12.januar til 20.januar medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `avvist utbetaling`() {
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))
        håndterInntektsmelding(1.januar)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = false)

        generasjoner {
            assertEquals(0, size)
        }
    }

    @Test
    fun `Utbetalt periode i AvventerRevurdering skal mappes til UtbetaltVenterPåAnnenPeriode`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars, vedtaksperiode = 2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))
        håndterYtelserTilGodkjenning()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) medTilstand TilGodkjenning
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) medTilstand Utbetalt
                beregnetPeriode(1) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `out of order med gap`() {
        nyttVedtak(1.mars, 31.mars, orgnummer = a1)
        tilGodkjenning(1.januar, 31.januar, a1, vedtaksperiode = 2)


        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra (1.mars til 31.mars) medTilstand UtbetaltVenterPåAnnenPeriode
                beregnetPeriode(1) er Ubetalt avType UTBETALING fra (1.januar til 31.januar) medTilstand TilGodkjenning
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
            }
        }

        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()
        håndterYtelserTilGodkjenning()


        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (1.mars til 31.mars) medTilstand TilGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 31.januar) medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `omgjøre periode etter en revurdering`() {
        håndterSøknad(4.januar til 20.januar)
        nyttVedtak(1.mars, 31.mars, vedtaksperiode = 2)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), Ferie(30.mars, 31.mars))
        håndterYtelserTilUtbetalt()

        håndterInntektsmelding(1.januar)
        håndterVilkårsgrunnlagTilUtbetalt()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra (1.mars til 31.mars) medTilstand ForberederGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 20.januar) medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(1) fra (4.januar til 20.januar) medTilstand IngenUtbetaling
            }
            2.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(1) fra (4.januar til 20.januar) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `omgjøre kort periode til at nav utbetaler`() {
        val søknad = håndterSøknad(Sykdom(4.januar, 20.januar, 100.prosent))
        val im = håndterInntektsmelding(listOf(4.januar til 19.januar))
        val overstyring = UUID.randomUUID()
        håndterOverstyrTidslinje(4.januar.til(19.januar).map { ManuellOverskrivingDag(it, Dagtype.SykedagNav, 100) }, meldingsreferanseId = overstyring)

        håndterVilkårsgrunnlagTilUtbetalt()
        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (4.januar til 20.januar) medTilstand Utbetalt medHendelser setOf(søknad, im, overstyring)
            }
            1.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 4.januar til 20.januar medTilstand IngenUtbetaling medHendelser setOf(søknad, im)
            }
            2.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 4.januar til 20.januar medTilstand IngenUtbetaling medHendelser setOf(søknad)
            }
        }
    }

    @Test
    fun `omgjøring av eldre kort periode`() {
        håndterSøknad(5.januar til 19.januar, orgnummer = a1)
        nyttVedtak(1.mars, 31.mars, orgnummer = a1, vedtaksperiode = 2)


        generasjoner {
            assertEquals(1, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(1) fra (5.januar til 19.januar) medTilstand IngenUtbetaling
            }
        }

        håndterOverstyrTidslinje((1.januar til 4.januar).map {
            ManuellOverskrivingDag(it, Dagtype.Sykedag, 100)
        }, orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1, vedtaksperiode = 1)
        håndterVilkårsgrunnlagTilUtbetalt()

        håndterYtelser()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (1.mars til 31.mars) medTilstand TilGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.januar til 19.januar) medTilstand Utbetalt
            }
            1.generasjon {
                assertEquals(2, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(1) fra (5.januar til 19.januar) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `out of order som er innenfor agp så utbetales`() {
        nyttVedtak(1.mars, 31.mars, orgnummer = a1)
        håndterSøknad(1.januar til 15.januar, orgnummer = a1)


        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(2, size)
                uberegnetPeriode(0) fra (1.mars til 31.mars) medTilstand ForberederGodkjenning
                uberegnetPeriode(1) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
            }
        }

        håndterSøknad(16.januar til 31.januar, orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a1, vedtaksperiode = 2)
        håndterVilkårsgrunnlag()
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterYtelser() // her vi må bruke beregnet-tidspunktet og ikke generasjon opprettet

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (1.mars til 31.mars) medTilstand TilGodkjenning
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (16.januar til 31.januar) medTilstand Utbetalt
                uberegnetPeriode(2) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
            2.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.mars til 31.mars) medTilstand Utbetalt
            }
        }
    }

    @Test
    fun `out of order som er innenfor agp - så nyere perioder`() {
        nyttVedtak(1.mars, 31.mars, orgnummer = a1)
        håndterSøknad(1.januar til 15.januar, orgnummer = a1)
        håndterYtelser()
        håndterUtbetalingsgodkjenning()
        forlengVedtak(1.april, 10.april, orgnummer = a1)


        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.april til 10.april) medTilstand Utbetalt
                beregnetPeriode(1) er GodkjentUtenUtbetaling avType REVURDERING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(2) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
        }

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(10.april, Dagtype.Feriedag)))
        håndterYtelser()
        håndterSimulering()

        generasjoner {
            assertEquals(3, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Ubetalt avType REVURDERING fra (1.april til 10.april) medTilstand TilGodkjenning
                beregnetPeriode(1) er GodkjentUtenUtbetaling avType REVURDERING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(2) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.april til 10.april) medTilstand Utbetalt
                beregnetPeriode(1) er GodkjentUtenUtbetaling avType REVURDERING fra (1.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(2) fra (1.januar til 15.januar) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `tidligere periode med arbeid får samme arbeidsgiverperiode som nyere periode`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAV = 1.mai.atStartOfDay())
        håndterInntektsmelding(1.januar)
        håndterVilkårsgrunnlagTilGodkjenning()
        håndterUtbetalingsgodkjenning()

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Søknad.Søknadsperiode.Arbeid(1.februar, 28.februar))

        nyttVedtak(2.mars, 31.mars, vedtaksperiode = 3)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelserTilUtbetalt()
        håndterYtelserTilUtbetalt()

        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType REVURDERING fra (2.mars til 31.mars) medTilstand Utbetalt
                beregnetPeriode(1) er Utbetalingstatus.Utbetalt avType UTBETALING fra (1.februar til 28.februar) medTilstand Utbetalt
                beregnetPeriode(2) er GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) medTilstand IngenUtbetaling
            }
            1.generasjon {
                assertEquals(3, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt avType UTBETALING fra (2.mars til 31.mars) medTilstand Utbetalt
                uberegnetPeriode(1) fra (1.februar til 28.februar) medTilstand IngenUtbetaling
                beregnetPeriode(2) er GodkjentUtenUtbetaling avType UTBETALING fra (1.januar til 31.januar) medTilstand IngenUtbetaling
            }
        }
    }

    @Test
    fun `bygge generasjon mens periode er i Avventer historikk og forrige arbeidsgiver er utbetalt`() {
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent) , orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent) , orgnummer = a2)
        håndterInntektsmelding(1.januar, orgnummer = a1)
        håndterInntektsmelding(1.januar, orgnummer = a2)
        håndterVilkårsgrunnlag(arbeidsgivere = listOf(a1 to INNTEKT, a2 to INNTEKT))
        håndterYtelserTilUtbetalt()
        generasjoner(a1) {
            assertEquals(1, size)
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Utbetalingstatus.Utbetalt medTilstand Utbetalt
            }
        }
        generasjoner(a2) {
            assertEquals(1, size)
            0.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) er Ubetalt medTilstand ForberederGodkjenning
            }
        }
    }

    @Test
    fun `uberegnet periode i avventer vilkårsprøving revurdering`() {
        nyttVedtak(2.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(1.januar, Dagtype.Sykedag, 100)))
        generasjoner {
            assertEquals(2, size)
            0.generasjon {
                assertEquals(1, size)
                uberegnetPeriode(0) fra 1.januar til 31.januar medTilstand ForberederGodkjenning
            }
            1.generasjon {
                assertEquals(1, size)
                beregnetPeriode(0) fra 2.januar til 31.januar er Utbetalingstatus.Utbetalt medTilstand Utbetalt
            }
        }
    }

    private fun BeregnetPeriode.assertAldersvilkår(expectedOppfylt: Boolean, expectedAlderSisteSykedag: Int) {
        assertEquals(expectedOppfylt, periodevilkår.alder.oppfylt)
        assertEquals(expectedAlderSisteSykedag, periodevilkår.alder.alderSisteSykedag)
    }

    private fun BeregnetPeriode.assertSykepengedagerVilkår(
        expectedForbrukteSykedager: Int,
        expectedGjenståendeSykedager: Int,
        expectedMaksdato: LocalDate,
        expectedSkjæringstidspunkt: LocalDate,
        expectedOppfylt: Boolean
    ) {
        assertEquals(expectedForbrukteSykedager, periodevilkår.sykepengedager.forbrukteSykedager)
        assertEquals(expectedGjenståendeSykedager, periodevilkår.sykepengedager.gjenståendeDager)
        assertEquals(expectedMaksdato, periodevilkår.sykepengedager.maksdato)
        assertEquals(expectedSkjæringstidspunkt, periodevilkår.sykepengedager.skjæringstidspunkt)
        assertEquals(expectedOppfylt, periodevilkår.sykepengedager.oppfylt)
    }

    private class Arbeidsgivergenerasjoner(
        private val orgnummer: String,
        private val vilkårsgrunnlag: Map<UUID, no.nav.helse.spleis.speil.dto.Vilkårsgrunnlag>,
        private val generasjoner: List<SpeilGenerasjonDTO>
    ) {
        val size = generasjoner.size

        fun Int.generasjon(assertBlock: SpeilGenerasjonDTO.() -> Unit) {
            require(this >= 0) { "Kan ikke være et negativt tall!" }
            generasjoner[this].run(assertBlock)
        }

        fun SpeilGenerasjonDTO.beregnetPeriode(index: Int): BeregnetPeriode {
            val periode = this.perioder[index]
            require(periode is BeregnetPeriode) { "Perioden ${periode::class.simpleName} er ikke en beregnet periode!" }
            return periode
        }

        fun SpeilGenerasjonDTO.annullertPeriode(index: Int): AnnullertPeriode {
            val periode = this.perioder[index]
            require(periode is AnnullertPeriode) { "Perioden ${periode::class.simpleName} er ikke en annullert periode!" }
            return periode
        }

        fun SpeilGenerasjonDTO.uberegnetPeriode(index: Int): UberegnetPeriode {
            val periode = this.perioder[index]
            require(periode is UberegnetPeriode) { "Perioden ${periode::class.simpleName} er ikke en uberegnet periode!" }
            return periode
        }


        infix fun <T : SpeilTidslinjeperiode> T.medAntallDager(antall: Int): T {
            assertEquals(antall, sammenslåttTidslinje.size)
            return this
        }
        infix fun <T : SpeilTidslinjeperiode> T.harTidslinje(dager: Pair<Periode, SykdomstidslinjedagType>): T {
            val (periode, dagtype) = dager
            val periodeUtenHelg = periode.filterNot { it.erHelg() }
            val tidslinjedager = this.sammenslåttTidslinje.filter { it.dagen in periodeUtenHelg }
            assertEquals(periodeUtenHelg.toList().size, tidslinjedager.size)
            assertTrue(tidslinjedager.all { it.sykdomstidslinjedagtype == dagtype })
            return this
        }

        fun BeregnetPeriode.vilkårsgrunnlag(): no.nav.helse.spleis.speil.dto.Vilkårsgrunnlag {
            return requireNotNull(vilkårsgrunnlag[this.vilkårsgrunnlagId]) { "Forventet å finne vilkårsgrunnlag for periode" }
        }

        infix fun <T : SpeilTidslinjeperiode> T.forkastet(forkastet: Boolean): T {
            assertEquals(forkastet, this.erForkastet)
            return this
        }

        infix fun BeregnetPeriode.er(utbetalingstilstand: Utbetalingstatus): BeregnetPeriode {
            assertEquals(utbetalingstilstand, this.utbetaling.status)
            return this
        }

        infix fun BeregnetPeriode.avType(type: Utbetalingtype): BeregnetPeriode {
            assertEquals(type, this.utbetaling.type)
            return this
        }

        infix fun AnnullertPeriode.er(utbetalingstilstand: Utbetalingstatus): AnnullertPeriode {
            assertEquals(utbetalingstilstand, this.utbetaling.status)
            return this
        }

        infix fun AnnullertPeriode.avType(type: Utbetalingtype): AnnullertPeriode {
            assertEquals(type, this.utbetaling.type)
            return this
        }

        infix fun <T : SpeilTidslinjeperiode> T.medTilstand(tilstand: Periodetilstand): T {
            assertEquals(tilstand, this.periodetilstand)
            return this
        }

        infix fun <T : SpeilTidslinjeperiode> T.medHendelser(hendelser: Set<UUID>): T {
            assertEquals(hendelser, this.hendelser)
            return this
        }

        infix fun <T : SpeilTidslinjeperiode> T.medPeriodetype(tidslinjeperiodetype: Tidslinjeperiodetype): T {
            assertEquals(tidslinjeperiodetype, this.periodetype)
            return this
        }

        infix fun <T : SpeilTidslinjeperiode> T.fra(periode: Periode): T {
            assertEquals(periode.start, this.fom)
            assertEquals(periode.endInclusive, this.tom)
            return this
        }
        infix fun <T : SpeilTidslinjeperiode> T.fra(fom: LocalDate): T {
            assertEquals(fom, this.fom)
            return this
        }
        infix fun <T : SpeilTidslinjeperiode> T.til(tom: LocalDate): T {
            assertEquals(tom, this.tom)
            return this
        }
    }

    private fun generasjoner(organisasjonsnummer: String = a1, block: Arbeidsgivergenerasjoner.() -> Unit = {}) {
        val d = speilApi()
        Arbeidsgivergenerasjoner(organisasjonsnummer, d.vilkårsgrunnlag, d.arbeidsgivere.singleOrNull { it.organisasjonsnummer == organisasjonsnummer }?.generasjoner ?: emptyList()).apply(block)
    }
}
