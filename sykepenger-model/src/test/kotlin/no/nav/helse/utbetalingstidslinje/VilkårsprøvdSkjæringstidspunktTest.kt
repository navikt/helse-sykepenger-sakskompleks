package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Grunnbeløp
import no.nav.helse.dsl.BeløpstidslinjeDsl.Arbeidsgiver
import no.nav.helse.dsl.BeløpstidslinjeDsl.hele
import no.nav.helse.dsl.BeløpstidslinjeDsl.oppgir
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.UTELATE
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VilkårsprøvdSkjæringstidspunktTest {

    @Test
    fun ghosttidslinjer() {
        val vilkårsgrunnlag = VilkårsprøvdSkjæringstidspunkt(
            skjæringstidspunkt = 1.januar,
            `6G` = Grunnbeløp.`6G`.beløp(1.januar),
            inntekter = listOf(
                VilkårsprøvdSkjæringstidspunkt.FaktaavklartInntekt("a1", 1000.daglig, 1.januar til 31.januar),
                VilkårsprøvdSkjæringstidspunkt.FaktaavklartInntekt("a2", 500.daglig, 25.januar til LocalDate.MAX),
            ),
            tilkommendeInntekter = emptyList(),
            deaktiverteArbeidsforhold = emptyList()
        )

        vilkårsgrunnlag.medGhostOgNyeInntekterUnderveis(
            mapOf(
                "a1" to listOf(tidslinjeOf(16.AP, 8.NAV))
            )
        ).also { result ->
            assertEquals(1, result.size)
            assertEquals(24, result["a1"]?.size)
        }

        vilkårsgrunnlag.medGhostOgNyeInntekterUnderveis(
            mapOf(
                "a1" to listOf(tidslinjeOf(16.AP, 15.NAV))
            )
        ).also { result ->
            assertEquals(2, result.size)
            assertEquals(31, result["a1"]?.size)
            assertEquals(7, result["a2"]?.size)
            assertEquals(25.januar til 31.januar, result["a2"]?.periode())
        }

        vilkårsgrunnlag.medGhostOgNyeInntekterUnderveis(
            mapOf(
                "a2" to listOf(tidslinjeOf(31.UTELATE, 28.NAV))
            )
        ).also { result ->
            assertEquals(2, result.size)
            assertEquals(28, result["a1"]?.size)
            assertEquals(1.februar til 28.februar, result["a1"]?.periode())
            assertEquals(28, result["a2"]?.size)
            assertEquals(1.februar til 28.februar, result["a2"]?.periode())
        }
    }

    @Test
    fun `nye inntekter underveis`() {
        val vilkårsgrunnlag = VilkårsprøvdSkjæringstidspunkt(
            skjæringstidspunkt = 1.januar,
            `6G` = Grunnbeløp.`6G`.beløp(1.januar),
            inntekter = listOf(
                VilkårsprøvdSkjæringstidspunkt.FaktaavklartInntekt("a1", 1000.daglig, 1.januar til 31.januar)
            ),
            tilkommendeInntekter = listOf(
                VilkårsprøvdSkjæringstidspunkt.NyInntektUnderveis("a2", Arbeidsgiver oppgir 500.daglig hele januar)
            ),
            deaktiverteArbeidsforhold = emptyList()
        )

        vilkårsgrunnlag.medGhostOgNyeInntekterUnderveis(
            mapOf(
                "a1" to listOf(tidslinjeOf(16.AP, 8.NAV))
            )
        ).also { result ->
            assertEquals(2, result.size)
            assertEquals(24, result["a1"]?.size)
            assertEquals(24, result["a2"]?.size)
            assertEquals(1.januar til 24.januar, result["a2"]?.periode())
        }
    }
}
