package no.nav.helse.spleis.e2e

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_23
import no.nav.helse.økonomi.Inntekt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VarselVedNegativtBeløpE2ETest: AbstractDslTest() {

    @Test
    fun `skal få varsel når utbetaling flyttes fra arbeidsgiver til person`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            assertIngenVarsel(RV_UT_23)
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT, ".", null, listOf(Triple(1.januar, null, Inntekt.INGEN)))
            ))
            håndterYtelser(1.vedtaksperiode)
            assertEquals(-15741, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            assertEquals(15741, inspektør.utbetalinger.last().inspektør.personOppdrag.inspektør.nettoBeløp)
            assertVarsel(RV_UT_23)
        }
    }
    @Test
    fun `skal få varsel når utbetaling flyttes fra person til arbeidsgiver`() {
        a1 {
            nyttVedtak(1.januar til 31.januar, refusjon = Inntektsmelding.Refusjon(Inntekt.INGEN, null, emptyList()))
            assertIngenVarsel(RV_UT_23)
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT, ".", null, listOf(Triple(1.januar, null, INNTEKT)))
            ))
            håndterYtelser(1.vedtaksperiode)
            assertEquals(15741, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            assertEquals(-15741, inspektør.utbetalinger.last().inspektør.personOppdrag.inspektør.nettoBeløp)
            assertVarsel(RV_UT_23)
        }
    }

    @Test
    fun `skal få varsel når utbetaling reduseres pga lavere inntekt`() {
        a1 {
            nyttVedtak(1.januar til 31.januar)
            assertIngenVarsel(RV_UT_23)

            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT*0.8, ".", null, emptyList())
            ))
            håndterYtelser(1.vedtaksperiode)
            assertEquals(-3146, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            assertEquals(0, inspektør.utbetalinger.last().inspektør.personOppdrag.inspektør.nettoBeløp)
            assertVarsel(RV_UT_23)
        }
    }

}