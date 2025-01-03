package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.etterlevelse.BehandlingSubsumsjonslogg
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.februar
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VilkårsgrunnlagHistorikkInnslagTest {
    private lateinit var innslag: VilkårsgrunnlagHistorikk.Innslag
    private val jurist = BehandlingSubsumsjonslogg(
        EmptyLog, listOf(
        Subsumsjonskontekst(KontekstType.Fødselsnummer, "fnr"),
        Subsumsjonskontekst(KontekstType.Organisasjonsnummer, "orgnr"),
        Subsumsjonskontekst(KontekstType.Vedtaksperiode, "${UUID.randomUUID()}"),
    )
    )

    private companion object {
        private val ALDER = 12.februar(1992).alder
    }

    @BeforeEach
    fun beforeEach() {
        innslag = VilkårsgrunnlagHistorikk.Innslag(null, emptyList())
    }

    @Test
    fun `avviser dager uten opptjening`() {
        val tidslinjer = listOf(tidslinjeOf(1.NAV))
        innslag.add(1.januar, grunnlagsdata(1.januar, harOpptjening = false))
        val resultat = innslag.avvis(tidslinjer, 1.januar til 1.januar, jurist)
        val avvisteDager = avvisteDager(resultat)
        assertEquals(1, avvisteDager.size)
        assertNotNull(avvisteDager.first().erAvvistMed(Begrunnelse.ManglerOpptjening))
    }

    @Test
    fun `avviser ikke dager dersom vurdert ok`() {
        val tidslinjer = listOf(tidslinjeOf(1.NAV))
        innslag.add(1.januar, grunnlagsdata(1.januar, harOpptjening = true, harMinimumInntekt = true, erMedlem = true))
        val resultat = innslag.avvis(tidslinjer, 1.januar til 1.januar, jurist)
        assertEquals(0, avvisteDager(resultat).size)
    }

    @Test
    fun `avviser med flere begrunnelser`() {
        val tidslinjer = listOf(tidslinjeOf(1.NAV))
        innslag.add(1.januar, grunnlagsdata(1.januar, harOpptjening = false, harMinimumInntekt = false))
        val resultat = innslag.avvis(tidslinjer, 1.januar til 1.januar, jurist)
        val avvisteDager = avvisteDager(resultat)
        assertEquals(1, avvisteDager.size)
        avvisteDager.first().also {
            assertNotNull(it.erAvvistMed(Begrunnelse.MinimumInntekt))
            assertNotNull(it.erAvvistMed(Begrunnelse.ManglerOpptjening))
        }
    }

    @Test
    fun `avviser på tvers av vilkårsgrunnlagelementer`() {
        val tidslinjer = listOf(tidslinjeOf(2.NAV, skjæringstidspunkter = listOf(1.januar, 2.januar)))
        innslag.add(1.januar, grunnlagsdata(1.januar, harOpptjening = false))
        innslag.add(2.januar, grunnlagsdata(2.januar, harMinimumInntekt = false))
        val resultat = innslag.avvis(tidslinjer, 2.januar til 2.januar, jurist)
        val avvisteDager = avvisteDager(resultat)
        assertEquals(2, avvisteDager.size)
        assertNotNull(avvisteDager.first().erAvvistMed(Begrunnelse.ManglerOpptjening))
        assertNotNull(avvisteDager.last().erAvvistMed(Begrunnelse.MinimumInntekt))
    }

    @Test
    fun `oppfyller ingen inngangsvilkår deler av perioden`() {
        val tidslinjer = listOf(tidslinjeOf(2.NAV, skjæringstidspunkter = listOf(1.januar, 2.januar)))
        innslag.add(1.januar, grunnlagsdata(1.januar))
        innslag.add(2.januar, grunnlagsdata(2.januar, harOpptjening = false, harMinimumInntekt = false, erMedlem = false))
        val resultat = innslag.avvis(tidslinjer, 2.januar til 2.januar, jurist)
        val avvisteDager = avvisteDager(resultat)
        assertEquals(1, avvisteDager.size)
        avvisteDager.first().also {
            assertNotNull(it.erAvvistMed(Begrunnelse.ManglerOpptjening))
            assertNotNull(it.erAvvistMed(Begrunnelse.MinimumInntekt))
            assertNotNull(it.erAvvistMed(Begrunnelse.ManglerMedlemskap))
        }
    }

    private fun grunnlagsdata(skjæringstidspunkt: LocalDate, harOpptjening: Boolean = true, harMinimumInntekt: Boolean = true, erMedlem: Boolean = true): VilkårsgrunnlagHistorikk.Grunnlagsdata {
        val opptjening = if (!harOpptjening) emptyList() else listOf(
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(
                "orgnr", listOf(
                Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold(skjæringstidspunkt.minusYears(1), null, false)
            )
            )
        )
        val inntekt = if (!harMinimumInntekt) 2000.månedlig else 25000.månedlig
        return VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = skjæringstidspunkt,
            inntektsgrunnlag = Inntektsgrunnlag.opprett(
                ALDER, listOf(
                ArbeidsgiverInntektsopplysning(
                    "orgnr",
                    skjæringstidspunkt til LocalDate.MAX,
                    Saksbehandler(
                        skjæringstidspunkt,
                        UUID.randomUUID(),
                        inntekt,
                        "",
                        null,
                        LocalDateTime.now()
                    )
                )
            ), skjæringstidspunkt, jurist
            ),
            opptjening = Opptjening.nyOpptjening(opptjening, 1.januar),
            medlemskapstatus = when (erMedlem) {
                true -> Medlemskapsvurdering.Medlemskapstatus.Ja
                false -> Medlemskapsvurdering.Medlemskapstatus.Nei
            },
            vurdertOk = harOpptjening && harMinimumInntekt && erMedlem,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        )
    }

    private fun avvisteDager(tidslinjer: List<Utbetalingstidslinje>): List<Utbetalingsdag.AvvistDag> {
        return tidslinjer.flatMap { it.inspektør.avvistedager }
    }
}
