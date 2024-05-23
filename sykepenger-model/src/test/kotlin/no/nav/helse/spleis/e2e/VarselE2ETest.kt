package no.nav.helse.spleis.e2e

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_9
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_13
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_14
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IT_37
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_MV_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_5
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_2
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class VarselE2ETest: AbstractEndToEndTest() {

    @Test
    fun `varsel - Sykmeldingen er tilbakedatert, vurder fra og med dato for utbetaling`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            merknaderFraSykmelding = listOf(Søknad.Merknad("UGYLDIG_TILBAKEDATERING"))
        )
        assertVarsel(RV_SØ_3, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Søknaden inneholder Permisjonsdager utenfor sykdomsvindu`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            Søknad.Søknadsperiode.Permisjon(1.desember(2017), 31.desember(2017)),
        )
        assertVarsel(RV_SØ_5, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Perioden er avslått på grunn av at den sykmeldte ikke er medlem av Folketrygden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Nei)

        assertVarsel(RV_MV_2, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Endrer tidligere oppdrag, Kontroller simuleringen`(){
        nyttVedtak(3.januar til 26.januar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((20.januar til 22.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((23.januar til 23.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        assertVarsel(RV_OS_3)
    }

    @Test
    fun `varsel - Det er institusjonsopphold i perioden - Vurder retten til sykepenger`() {
        nyttVedtak(1.januar til 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(Institusjonsopphold.Institusjonsoppholdsperiode(1.januar, 31.januar)))

        assertVarsel(RV_AY_9)
    }

    @Test
    fun `varsel - Utbetalingen ble gjennomført, men med advarsel`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(status = Oppdragstatus.AKSEPTERT_MED_FEIL)
        assertVarsel(RV_UT_2)
    }

    @Test
    fun `varsel revurdering - Støtter ikke overgang fra infotrygd for flere arbeidsgivere`() {
        nyttVedtak(1.januar til 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            inntektshistorikk = listOf(
                Inntektsopplysning(a2, 1.januar, inntekt = INNTEKT, true, null),
                Inntektsopplysning(a3, 1.januar, inntekt = INNTEKT, true, null)
            )
        )
        håndterYtelser()
        assertIngenVarsel(RV_IT_13, 1.vedtaksperiode.filter())
        assertIngenFunksjonelleFeil(1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel revurdering - Forlenger en Infotrygdperiode på tvers av arbeidsgivere`() {
        nyttVedtak(1.januar til 31.januar)
        forlengVedtak(1.februar til 28.februar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.februar, Dagtype.Feriedag)))
        håndterUtbetalingshistorikkEtterInfotrygdendring(
            utbetalinger = arrayOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, INNTEKT)),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 17.januar, INNTEKT, true))
        )
        håndterYtelser(2.vedtaksperiode)
        assertVarsel(RV_IT_14, 2.vedtaksperiode.filter())
        assertIngenFunksjonelleFeil(2.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - skjæringstidspunkt endres som følge av historikk fra IT`() {
        nyPeriode(10.januar til 25.januar)
        nyPeriode(26.januar til 31.januar)
        håndterInntektsmelding(listOf(10.januar til 25.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterUtbetalingshistorikkEtterInfotrygdendring(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar, 9.januar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
            Inntektsopplysning(ORGNUMMER, 1.januar, INNTEKT, true)
        ))
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING, TIL_INFOTRYGD, varselkode = RV_IT_14)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, TIL_INFOTRYGD)
    }

    @Test
    fun `varsel - skjæringstidspunkt endres som følge av historikk fra IT - forlengelse`() {
        nyttVedtak(10.februar til 28.februar)
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent))
        håndterUtbetalingshistorikkEtterInfotrygdendring(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 5.februar, 11.februar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
            Inntektsopplysning(ORGNUMMER, 5.februar, INNTEKT, true)
        ))
        assertFunksjonellFeil(RV_IT_37, 2.vedtaksperiode.filter(ORGNUMMER))
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)
    }

}