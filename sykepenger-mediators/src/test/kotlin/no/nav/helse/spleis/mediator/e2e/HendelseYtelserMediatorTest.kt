package no.nav.helse.spleis.mediator.e2e

import no.nav.helse.desember
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.januar
import no.nav.helse.november
import no.nav.helse.spleis.mediator.TestMessageFactory.InstitusjonsoppholdTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.OmsorgspengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.OpplæringspengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.PleiepengerTestdata
import no.nav.helse.spleis.mediator.TestMessageFactory.UtbetalingshistorikkTestdata
import no.nav.inntektsmeldingkontrakt.Periode
import org.junit.jupiter.api.Test

internal class HendelseYtelserMediatorTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `Periode med overlappende pleiepenger får varsel`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(vedtaksperiodeIndeks = 0, pleiepenger = listOf(PleiepengerTestdata(3.januar, 26.januar, 100)))

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }

    @Test
    fun `Periode med overlappende omsorgspenger får varsel`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(
            vedtaksperiodeIndeks = 0,
            omsorgspenger = listOf(OmsorgspengerTestdata(3.januar, 26.januar, 100))
        )

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }

    @Test
    fun `Periode med overlappende opplæringspenger får varsel`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(
            vedtaksperiodeIndeks = 0,
            opplæringspenger = listOf(OpplæringspengerTestdata(3.januar, 26.januar, 100))
        )

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }

    @Test
    fun `Periode med overlappende institusjonsopphold blir sendt til Infotrygd`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(
            vedtaksperiodeIndeks = 0,
            institusjonsoppholdsperioder = listOf(
                InstitusjonsoppholdTestdata(
                    startdato = 3.januar,
                    faktiskSluttdato = null,
                    institusjonstype = "FO",
                    kategori = "S"
                )
            )
        )

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "TIL_INFOTRYGD"
        )
    }

    @Test
    fun `Arbeidskategorikode ulik 01 kaster perioden til Infotrygd`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendUtbetalingshistorikkEtterInfotrygdendring(listOf(UtbetalingshistorikkTestdata(
            fom = 1.januar,
            tom = 2.januar,
            arbeidskategorikode = "07",
            utbetalteSykeperioder = listOf(
                UtbetalingshistorikkTestdata.UtbetaltSykeperiode(
                    fom = 1.januar,
                    tom = 2.januar,
                    dagsats = 1400.0,
                    typekode = "0",
                    utbetalingsgrad = "100",
                    organisasjonsnummer = ORGNUMMER
                )
            ),
            inntektsopplysninger = listOf(
                UtbetalingshistorikkTestdata.Inntektsopplysninger(
                    sykepengerFom = 1.november(2017),
                    inntekt = 36000.0,
                    organisasjonsnummer = ORGNUMMER,
                    refusjonTilArbeidsgiver = true
                )
            )
        )))

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "TIL_INFOTRYGD"
        )
    }

    @Test
    fun `Passerer validering når utbetalte sykeperioder er tom`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )
        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        val historikk = listOf(UtbetalingshistorikkTestdata(
            fom = 1.januar,
            tom = 26.januar,
            arbeidskategorikode = "07",
            utbetalteSykeperioder = emptyList(),
            inntektsopplysninger = emptyList()
        ))
        sendVilkårsgrunnlag(0)
        sendYtelser(vedtaksperiodeIndeks = 0)

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }

    @Test
    fun `Utbetalingsgrad 000 fører til ugyldig utbetalingsperiode i sykepengehistorikken`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        )

        val historikk = listOf(UtbetalingshistorikkTestdata(
            fom = 1.januar,
            tom = 2.januar,
            arbeidskategorikode = "01",
            utbetalteSykeperioder = listOf(
                UtbetalingshistorikkTestdata.UtbetaltSykeperiode(
                    fom = 1.januar,
                    tom = 2.januar,
                    dagsats = 1400.0,
                    typekode = "5",
                    utbetalingsgrad = "000",
                    organisasjonsnummer = ORGNUMMER
                )
            ),
            inntektsopplysninger = listOf(
                UtbetalingshistorikkTestdata.Inntektsopplysninger(
                    sykepengerFom = 1.januar,
                    inntekt = 36000.0,
                    organisasjonsnummer = ORGNUMMER,
                    refusjonTilArbeidsgiver = true
                )
            )
        ))

        sendInntektsmelding(listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendVilkårsgrunnlag(0)
        sendUtbetalingshistorikkEtterInfotrygdendring(historikk)
        sendYtelser(0)

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }

    @Test
    fun `Marker ikke historiske ferieperioder som ugyldig basert på utbetalingsgrad`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 9.januar, tom = 31.januar, sykmeldingsgrad = 100))
        sendSøknad(
            perioder = listOf(SoknadsperiodeDTO(fom = 9.januar, tom = 31.januar, sykmeldingsgrad = 100))
        )

        val historikk = listOf(UtbetalingshistorikkTestdata(
            fom = 1.januar,
            tom = 8.januar,
            arbeidskategorikode = "01",
            utbetalteSykeperioder = listOf(
                UtbetalingshistorikkTestdata.UtbetaltSykeperiode(
                    fom = 1.desember(2017),
                    tom = 5.desember(2017),
                    dagsats = 1400.0,
                    typekode = "5",
                    utbetalingsgrad = "100",
                    organisasjonsnummer = ORGNUMMER
                ),
                UtbetalingshistorikkTestdata.UtbetaltSykeperiode(
                    fom = 6.desember(2017),
                    tom = 10.desember(2017),
                    dagsats = 1400.0,
                    typekode = "9",
                    utbetalingsgrad = "",
                    organisasjonsnummer = ORGNUMMER
                ),
                UtbetalingshistorikkTestdata.UtbetaltSykeperiode(
                    fom = 11.desember(2017),
                    tom = 24.desember(2017),
                    dagsats = 1400.0,
                    typekode = "5",
                    utbetalingsgrad = "100",
                    organisasjonsnummer = ORGNUMMER
                ),
            ),
            inntektsopplysninger = listOf(
                UtbetalingshistorikkTestdata.Inntektsopplysninger(
                    sykepengerFom = 1.desember(2017),
                    inntekt = 36000.0,
                    organisasjonsnummer = ORGNUMMER,
                    refusjonTilArbeidsgiver = true
                )
            )
        ))

        sendInntektsmelding(listOf(Periode(fom = 9.januar, tom = 25.januar)), førsteFraværsdag = 9.januar)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)

        assertTilstander(
            0,
            "AVVENTER_INFOTRYGDHISTORIKK",
            "AVVENTER_INNTEKTSMELDING_REPLAY",
            "AVVENTER_INNTEKTSMELDING",
            "AVVENTER_BLOKKERENDE_PERIODE",
            "AVVENTER_VILKÅRSPRØVING",
            "AVVENTER_HISTORIKK",
            "AVVENTER_SIMULERING"
        )
    }
}


