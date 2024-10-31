package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.OverstyringIgangsatt.VedtaksperiodeData
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.OverstyrtArbeidsgiveropplysning
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterPåminnelse
import no.nav.helse.spleis.e2e.håndterSkjønnsmessigFastsettelse
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RevurderingseventyrEventTest : AbstractEndToEndTest() {

    @Test
    fun `happy case revurdering`() {
        nyttVedtak(januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))

        revurderingIgangsattEvent {
            this bleForårsaketAv "SYKDOMSTIDSLINJE"
            this medSkjæringstidspunkt 1.januar
            this avTypeEndring "REVURDERING"
        }
    }
    @Test
    fun `happy case overstyring`() {
        tilGodkjenning(januar, a1)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))

        revurderingIgangsattEvent {
            this bleForårsaketAv "SYKDOMSTIDSLINJE"
            this medSkjæringstidspunkt 1.januar
            this avTypeEndring "OVERSTYRING"
        }
    }

    @Test
    fun `skjønnsfastsetting`() {
        håndterSøknad(januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 60000.månedlig,)
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntekt = 30000.månedlig)
        assertSisteTilstand(1.vedtaksperiode, TilstandType.AVVENTER_HISTORIKK)
        håndterSkjønnsmessigFastsettelse(
            1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(
                    orgnummer = ORGNUMMER,
                    inntekt = 60000.månedlig,
                    forklaring = "",
                    subsumsjon = null,
                    refusjonsopplysninger = listOf(
                        Triple(1.januar, null, 31000.månedlig)
                    )
                )
            )
        )
        revurderingIgangsattEvent {
            this bleForårsaketAv "SKJØNNSMESSIG_FASTSETTELSE"
            this medSkjæringstidspunkt 1.januar
            this avTypeEndring "OVERSTYRING"
        }
    }

    @Test
    fun `auu skal utbetales`() {
        håndterSøknad(1.januar til 16.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(16.januar, Dagtype.SykedagNav, 100)))
        revurderingIgangsattEvent {
            this bleForårsaketAv "SYKDOMSTIDSLINJE"
            this medSkjæringstidspunkt 1.januar
            this avTypeEndring "OVERSTYRING"
            assertEquals(
                listOf(
                    VedtaksperiodeData(
                        orgnummer = a1,
                        vedtaksperiodeId = 1.vedtaksperiode.id(ORGNUMMER),
                        periode = 1.januar til 16.januar,
                        skjæringstidspunkt = 1.januar,
                        typeEndring = "ENDRING"
                    ),
                ), this.berørtePerioder
            )
        }
    }

    @Test
    fun `Overstyring av inntekt`() {
        tilGodkjenning(januar, a1)
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar)

        revurderingIgangsattEvent {
            this bleForårsaketAv "ARBEIDSGIVEROPPLYSNINGER"
            this medSkjæringstidspunkt 1.januar
            this avTypeEndring "OVERSTYRING"
        }
    }

    @Test
    fun `to vedtaksperioder berørt av en revurdering`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        val januar = observatør.utbetalteVedtaksperioder.first()
        val februar = observatør.utbetalteVedtaksperioder.last()

        revurderingIgangsattEvent {
            this bleForårsaketAv "SYKDOMSTIDSLINJE"
            this medSkjæringstidspunkt 1.januar
            this medførteRevurderingAv (januar og februar)
        }
    }

    @Test
    fun `reberegning av revurdering`() {
        nyttVedtak(januar)
        forlengVedtak(februar)
        forlengVedtak(mars)
        håndterSøknad(27.februar til 28.februar)
        nullstillTilstandsendringer()
        håndterPåminnelse(3.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, skalReberegnes = true)
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)
        assertEquals(1, observatør.overstyringIgangsatt.size)
        revurderingIgangsattEvent(0) {
            this bleForårsaketAv "KORRIGERT_SØKNAD"
        }
    }

    @Test
    fun `flere revurderinger`() {
        nyttVedtak(januar)
        håndterSøknad(29.januar til 30.januar)
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar)

        revurderingIgangsattEvent(0) {
            this bleForårsaketAv "KORRIGERT_SØKNAD"
        }
        revurderingIgangsattEvent(1) {
            this bleForårsaketAv "ARBEIDSGIVEROPPLYSNINGER"
        }
    }

    @Test
    fun `flere arbeidsgivere`() {
        nyeVedtak(januar, a1, a2)
        val overstyringId = UUID.randomUUID()
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar, meldingsreferanseId = overstyringId)

        val periodeAG1 = observatør.utbetalteVedtaksperioder.first()
        val periodeAG2 = observatør.utbetalteVedtaksperioder.last()

        revurderingIgangsattEvent {
            this bleForårsaketAv "ARBEIDSGIVEROPPLYSNINGER"
            this medSkjæringstidspunkt 1.januar
            this medførteRevurderingAv (periodeAG1 og periodeAG2)
            assertEquals(
                listOf(
                    VedtaksperiodeData(
                        orgnummer = a1,
                        vedtaksperiodeId = periodeAG1,
                        periode = januar,
                        skjæringstidspunkt = 1.januar,
                        typeEndring = "REVURDERING"
                    ),
                    VedtaksperiodeData(
                        orgnummer = a2,
                        vedtaksperiodeId = periodeAG2,
                        periode = januar,
                        skjæringstidspunkt = 1.januar,
                        typeEndring = "REVURDERING"
                    )
                ), this.berørtePerioder
            )
        }
    }

    @Test
    fun `tidligere skjæringstidspunkt -- revurderer inntekt`() {
        nyttVedtak(januar)
        forlengVedtak(1.februar til 15.februar)
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        forlengVedtak(april)

        val januar = observatør.utbetalteVedtaksperioder[0]
        val februar = observatør.utbetalteVedtaksperioder[1]
        val mars = observatør.utbetalteVedtaksperioder[2]
        val april = observatør.utbetalteVedtaksperioder[3]

        val overstyringId = UUID.randomUUID()
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar, meldingsreferanseId = overstyringId)

        revurderingIgangsattEvent {
            this bleForårsaketAv "ARBEIDSGIVEROPPLYSNINGER"
            this medSkjæringstidspunkt 1.januar
            this medførteRevurderingAv (januar og februar og mars og april)
            assertEquals(
                listOf(
                        VedtaksperiodeData(orgnummer = a1, vedtaksperiodeId = januar, periode = 1.januar til 31.januar, skjæringstidspunkt = 1.januar, typeEndring = "REVURDERING"),
                        VedtaksperiodeData(orgnummer = a1, vedtaksperiodeId = februar, periode = 1.februar til 15.februar, skjæringstidspunkt = 1.januar, typeEndring = "REVURDERING"),
                        VedtaksperiodeData(orgnummer = a1, vedtaksperiodeId = mars, periode = 1.mars til 31.mars, skjæringstidspunkt = 1.mars, typeEndring = "REVURDERING"),
                        VedtaksperiodeData(orgnummer = a1, vedtaksperiodeId = april, periode = 1.april til 30.april, skjæringstidspunkt = 1.mars, typeEndring = "REVURDERING")
                    )
                , this.berørtePerioder
            )
        }
    }

    @Test
    fun `tidligere skjæringstidspunkt -- revurderer tidslinje`() {
        nyttVedtak(januar)
        forlengVedtak(1.februar til 15.februar)
        nyttVedtak(mars, vedtaksperiodeIdInnhenter = 3.vedtaksperiode)
        forlengVedtak(april)

        val februar = observatør.utbetalteVedtaksperioder[1]
        val mars = observatør.utbetalteVedtaksperioder[2]
        val april = observatør.utbetalteVedtaksperioder[3]

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Dagtype.Feriedag)))

        revurderingIgangsattEvent {
            this bleForårsaketAv "SYKDOMSTIDSLINJE"
            this medSkjæringstidspunkt 1.januar
            this medførteRevurderingAv (februar og mars og april)
        }
    }

    private fun revurderingIgangsattEvent(nr: Int = 0, assertBlock: PersonObserver.OverstyringIgangsatt.() -> Unit) {
       val revurderingIgangsattEvent = observatør.overstyringIgangsatt[nr]
        revurderingIgangsattEvent.run(assertBlock)
    }

    private infix fun PersonObserver.OverstyringIgangsatt.bleForårsaketAv(årsak: String): PersonObserver.OverstyringIgangsatt {
        assertEquals(årsak, this.årsak)
        return this
    }

    private infix fun PersonObserver.OverstyringIgangsatt.medSkjæringstidspunkt(dato: LocalDate): PersonObserver.OverstyringIgangsatt {
        assertEquals(dato, this.skjæringstidspunkt)
        return this
    }

    private infix fun PersonObserver.OverstyringIgangsatt.avTypeEndring(typeEndring: String): PersonObserver.OverstyringIgangsatt {
        assertEquals(typeEndring, this.typeEndring)
        return this
    }

    private infix fun PersonObserver.OverstyringIgangsatt.medførteRevurderingAv(vedtaksperiodeIder: List<UUID>): PersonObserver.OverstyringIgangsatt {
        assertEquals(vedtaksperiodeIder, this.berørtePerioder.map { it.vedtaksperiodeId })
        return this
    }

    private infix fun UUID.og(other: UUID) = listOf(this, other)
    private infix fun List<UUID>.og(other: UUID) = this + other

}
