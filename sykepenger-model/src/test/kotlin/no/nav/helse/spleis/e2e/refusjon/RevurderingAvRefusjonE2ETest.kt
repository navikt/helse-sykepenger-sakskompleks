package no.nav.helse.spleis.e2e.refusjon

import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.etterlevelse.Ledd
import no.nav.helse.etterlevelse.Paragraf
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.SubsumsjonInspektør
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RevurderingAvRefusjonE2ETest : AbstractDslTest() {

    @Test
    fun `endring av refusjonsopplysninger skal ikke vilkårsprøve opptjening`() {
        a1 {
            val antallSubsumsjoner = { subsumsjonInspektør: SubsumsjonInspektør ->
                subsumsjonInspektør.antallSubsumsjoner(
                    paragraf = Paragraf.PARAGRAF_8_2,
                    versjon = 12.juni(2020),
                    ledd = Ledd.LEDD_1,
                    punktum = null,
                    bokstav = null
                )
            }

            nyttVedtak(1.januar til 31.januar)
            assertSubsumsjoner { assertEquals(1, antallSubsumsjoner(this)) }
            håndterOverstyrArbeidsgiveropplysninger(1.januar, listOf(
                OverstyrtArbeidsgiveropplysning(a1, INNTEKT, "ingen endring", null, listOf(
                    Triple(25.januar, null, INGEN)
                ))
            ))
            håndterYtelser(1.vedtaksperiode)
            assertSubsumsjoner { assertEquals(1, antallSubsumsjoner(this)) }
        }
    }
}