package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Grunnbeløp
import no.nav.helse.Toggle
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.etterspurtBehov
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.inntektsmelding.ALTINN
import no.nav.helse.hendelser.inntektsmelding.LPS
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IV_10
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_24
import no.nav.helse.person.inntekt.Inntektsopplysning.Inntektskilde
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.sisteBehov
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.IKKE_GODKJENT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.IKKE_UTBETALT
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GodkjenningsbehovTest : AbstractEndToEndTest() {

    @Test
    fun `sender med inntektskilder i sykepengegrunnlaget i godkjenningsbehovet`() {
        nyPeriode(januar, orgnummer = a1)
        nyPeriode(januar, orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, tilstandsendringstidspunkt = LocalDateTime.now().minusMonths(3), orgnummer = a2)
        håndterSykepengegrunnlagForArbeidsgiver(1.vedtaksperiode, orgnummer = a2)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        val inntektskilder = inntektskilder(1.vedtaksperiode, orgnummer = a1)
        assertEquals(listOf(Inntektskilde.Arbeidsgiver, Inntektskilde.AOrdningen), inntektskilder)
    }

    @Test
    fun `sender med inntektskilde a-ordningen i sykepengegrunnlaget i godkjenningsbehovet ved skjønnsmessig fastsettelse`() {
        nyPeriode(januar, a1)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, tilstandsendringstidspunkt = LocalDateTime.now().minusMonths(3), orgnummer = a1)
        håndterSykepengegrunnlagForArbeidsgiver(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSkjønnsmessigFastsettelse(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(
                orgnummer = a1,
                inntekt = INNTEKT * 2
            )
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        val inntektskilder = inntektskilder(1.vedtaksperiode, orgnummer = a1)
        assertEquals(listOf(Inntektskilde.AOrdningen), inntektskilder)
    }

    @Test
    fun `sender med inntektskilde arbeidsgiver i sykepengegrunnlaget i godkjenningsbehovet ved skjønnsmessig fastsettelse`() {
        tilGodkjenning(januar, a1)

        håndterSkjønnsmessigFastsettelse(1.januar, listOf(
            OverstyrtArbeidsgiveropplysning(
                orgnummer = a1,
                inntekt = INNTEKT * 2
            )
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        val inntektskilder = inntektskilder(1.vedtaksperiode, orgnummer = a1)
        assertEquals(listOf(Inntektskilde.Arbeidsgiver), inntektskilder)
    }

    @Test
    fun `sender med sykepengegrunnlag i godkjenningsbehovet`() {
        tilGodkjenning(januar, beregnetInntekt = INNTEKT*6, organisasjonsnummere = arrayOf(a1))
        assertEquals(Grunnbeløp.`6G`.beløp(1.januar).årlig, sykepengegrunnlag(1.vedtaksperiode))
    }

    @Test
    fun `sender med feil vilkårsgrunnlagId i påminnet godkjenningsbehov om det har kommet nytt vilkårsgrunnlag med endring _senere_ enn perioden`() {
        tilGodkjenning(januar, a1)
        val vilkårsgrunnlagId1 = vilkårsgrunnlagIdFraVilkårsgrunnlaghistorikken(1.januar)
        assertEquals(vilkårsgrunnlagId1, vilkårsgrunnlagIdFraSisteGodkjenningsbehov(1.vedtaksperiode))
        håndterSøknad(februar)
        håndterSøknad(mars)
        nullstillTilstandsendringer()

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.mars, refusjon = Inntektsmelding.Refusjon(Inntekt.INGEN, null), avsendersystem = LPS)
        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING) // Reberegnes ikke ettersom endringen gjelder fra og med 1.mars
        val vilkårsgrunnlagId2 = vilkårsgrunnlagIdFraVilkårsgrunnlaghistorikken(1.januar)
        assertNotEquals(vilkårsgrunnlagId1, vilkårsgrunnlagId2) // IM lager nytt innslag i vilkårsgrunnlaghistorikken pga nye refusjonsopplysninger

        håndterPåminnelse(1.vedtaksperiode, AVVENTER_GODKJENNING)

        assertEquals(vilkårsgrunnlagId1, vilkårsgrunnlagIdFraSisteGodkjenningsbehov(1.vedtaksperiode))
    }

    @Test
    fun `sender med feil vilkårsgrunnlagId i første godkjenningsbehov om det har kommet nytt vilkårsgrunnlag med endring _senere_ enn perioden mens den står i avventer simulering`() {
        håndterSøknad(januar)
        håndterSøknad(februar)
        håndterSøknad(mars)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        val vilkårsgrunnlagId1 = vilkårsgrunnlagIdFraVilkårsgrunnlaghistorikken(1.januar)
        håndterYtelser(1.vedtaksperiode)
        nullstillTilstandsendringer()

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.mars, refusjon = Inntektsmelding.Refusjon(Inntekt.INGEN, null), avsendersystem = LPS)

        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING) // Reberegnes ikke ettersom endringen gjelder fra og med 1.mars
        val vilkårsgrunnlagId2 = vilkårsgrunnlagIdFraVilkårsgrunnlaghistorikken(1.januar)
        assertNotEquals(vilkårsgrunnlagId1, vilkårsgrunnlagId2) // IM lager nytt innslag i vilkårsgrunnlaghistorikken pga nye refusjonsopplysninger

        håndterSimulering(1.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING, AVVENTER_GODKJENNING)

        assertEquals(vilkårsgrunnlagId1, vilkårsgrunnlagIdFraSisteGodkjenningsbehov(1.vedtaksperiode))
    }

    @Test
    fun `godkjenningsbehov som ikke kan avvises blir forsøkt avvist`() {
        håndterSøknad(1.januar til 16.januar)
        håndterSøknad(17.januar til 31.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterSøknad(mars)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            begrunnelseForReduksjonEllerIkkeUtbetalt = "Agp skal utbetales av NAV!!",
        )
        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVVENTER_HISTORIKK)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertFalse(kanAvvises(1.vedtaksperiode))

        val utbetalingId = UUID.fromString(person.personLogg.sisteBehov(Aktivitet.Behov.Behovtype.Godkjenning).kontekst()["utbetalingId"])
        val utbetaling = inspektør.utbetalinger(1.vedtaksperiode).last()
        assertEquals(utbetalingId, utbetaling.inspektør.utbetalingId)

        assertEquals(IKKE_UTBETALT, utbetaling.inspektør.tilstand)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingId = utbetalingId, utbetalingGodkjent = false)
        assertEquals(IKKE_GODKJENT, utbetaling.inspektør.tilstand)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        assertVarsel(RV_UT_24, 1.vedtaksperiode.filter())
    }

    @Test
    fun `førstegangsbehandling som kan avvises`() {
        tilGodkjenning(januar, a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTrue(kanAvvises(1.vedtaksperiode))
    }

    @Test
    fun `omgjøring som kan avvises`() {
        håndterSøknad(2.januar til 17.januar)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTrue(kanAvvises(1.vedtaksperiode))
    }

    @Test
    fun `omgjøring som _ikke_ kan avvises`() {
        håndterSøknad(2.januar til 17.januar)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        håndterSøknad(18.januar til 31.januar)
        håndterInntektsmelding(listOf(2.januar til 17.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTrue(kanAvvises(2.vedtaksperiode))
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertFalse(kanAvvises(1.vedtaksperiode))
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertFalse(kanAvvises(2.vedtaksperiode))
    }

    @Test
    fun `revurdering kan ikke avvises`() {
        nyttVedtak(januar)
        håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(OverstyrtArbeidsgiveropplysning(
            a1,
            INNTEKT * 1.05,
            "forklaring",
            null,
            emptyList()
        )))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertFalse(kanAvvises(1.vedtaksperiode))
    }

    @Test
    fun `kan avvise en out of order rett i forkant av en utbetalt periode`() {
        nyttVedtak(februar)
        håndterSøknad(januar)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTrue(kanAvvises(2.vedtaksperiode))
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false)
        assertSisteForkastetPeriodeTilstand(a1, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING_REVURDERING)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertFalse(kanAvvises(1.vedtaksperiode))
    }

    @Test
    fun `kan avvise en out of order selv om noe er utbetalt senere på annen agp`() {
        nyttVedtak(mars)
        håndterSøknad(januar)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTrue(kanAvvises(2.vedtaksperiode))
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, utbetalingGodkjent = false)
        assertSisteForkastetPeriodeTilstand(a1, 2.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        assertFalse(kanAvvises(1.vedtaksperiode))
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `markerer godkjenningsbehov som har brukt skatteinntekter istedenfor inntektsmelding med riktig tag`() =
        Toggle.InntektsmeldingSomIkkeKommer.enable {
            nyPeriode(januar)
            håndterPåminnelse(
                1.vedtaksperiode,
                AVVENTER_INNTEKTSMELDING,
                tilstandsendringstidspunkt = LocalDateTime.now().minusMonths(3)
            )
            håndterSykepengegrunnlagForArbeidsgiver(1.vedtaksperiode)
            assertVarsel(RV_IV_10)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            hendelselogg.assertHarTag(
                vedtaksperiode = 1.vedtaksperiode,
                forventetTag = "InntektFraAOrdningenLagtTilGrunn"
            )
        }

    @Test
    fun `markerer godkjenningsbehov som har brukt skatteinntekter istedenfor inntektsmelding med riktig tag for flere arbeidsgivere med ulik start`() = Toggle.InntektsmeldingSomIkkeKommer.enable {
        nyPeriode(januar, a1)
        nyPeriode(februar, a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a1)
        håndterInntektsmelding(listOf(1.februar til 16.februar), førsteFraværsdag = 1.februar, vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a2, avsendersystem = ALTINN)

        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, type = Arbeidsforholdtype.ORDINÆRT),
            ), orgnummer = a1
        )
        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a2)

        hendelselogg.assertHarTag(
            vedtaksperiode = 1.vedtaksperiode,
            forventetTag = "InntektFraAOrdningenLagtTilGrunn",
            orgnummer = a2
        )
    }


    private fun kanAvvises(vedtaksperiode: IdInnhenter, orgnummer: String = a1) = hendelselogg.etterspurtBehov<Boolean>(
        vedtaksperiodeId = vedtaksperiode.id(orgnummer),
        behov = Aktivitet.Behov.Behovtype.Godkjenning,
        felt = "kanAvvises"
    )!!

    private fun omregnedeÅrsinntekter(vedtaksperiode: IdInnhenter, orgnummer: String = a1) = hendelselogg.etterspurtBehov<List<Map<String, Any>>>(
        vedtaksperiodeId = vedtaksperiode.id(orgnummer),
        behov = Aktivitet.Behov.Behovtype.Godkjenning,
        felt = "omregnedeÅrsinntekter"
    )!!.map {
        OmregnetÅrsinntektFraGodkjenningsbehov(
            orgnummer = it.getValue("organisasjonsnummer") as String,
            beløp = (it.getValue("beløp") as Double).årlig
        )
    }

    private data class OmregnetÅrsinntektFraGodkjenningsbehov(val orgnummer: String, val beløp: Inntekt)

    private fun vilkårsgrunnlagIdFraSisteGodkjenningsbehov(vedtaksperiode: IdInnhenter, orgnummer: String = a1) = hendelselogg.etterspurtBehov<String>(
        vedtaksperiodeId = vedtaksperiode.id(orgnummer),
        behov = Aktivitet.Behov.Behovtype.Godkjenning,
        felt = "vilkårsgrunnlagId"
    )!!.let { UUID.fromString(it) }

    private fun vilkårsgrunnlagIdFraVilkårsgrunnlaghistorikken(skjæringstidspunkt: LocalDate, orgnummer: String = a1)
        = inspektør(orgnummer).vilkårsgrunnlag(skjæringstidspunkt)!!.view().inspektør.vilkårsgrunnlagId

    private fun sykepengegrunnlag(vedtaksperiode: IdInnhenter, orgnummer: String = a1) = hendelselogg.etterspurtBehov<Map<String, Any>>(
        vedtaksperiodeId = vedtaksperiode.id(orgnummer),
        behov = Aktivitet.Behov.Behovtype.Godkjenning,
        felt = "sykepengegrunnlagsfakta"
    )!!["sykepengegrunnlag"]


    private fun inntektskilder(vedtaksperiode: IdInnhenter, orgnummer: String = a1) = hendelselogg.etterspurtBehov<Map<String, List<Map<String, Any>>>>(
        vedtaksperiodeId = vedtaksperiode.id(orgnummer),
        behov = Aktivitet.Behov.Behovtype.Godkjenning,
        felt = "sykepengegrunnlagsfakta"
    )!!["arbeidsgivere"]!!.map { it["inntektskilde"] }}
