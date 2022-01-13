package no.nav.helse.person.etterlevelse

import no.nav.helse.person.Bokstav
import no.nav.helse.person.Ledd
import no.nav.helse.person.Ledd.Companion.ledd
import no.nav.helse.person.Paragraf
import no.nav.helse.person.Punktum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class BetingetVurderingTest {

    private val observatør get() = JuridiskVurderingObservatør()
    private lateinit var vurderinger: List<JuridiskVurdering>

    @BeforeEach
    fun beforeEach() {
        vurderinger = emptyList()
    }

    @Test
    fun `Legger til betingede vurderinger`() {
        nyVurdering()
        assertEquals(1, vurderinger.size)
        observatør.assertVurdering(vurderinger.first(), true)
    }

    @Test
    fun `En betinget vurdering erstatter en annen dersom de er like`() {
        nyVurdering()
        nyVurdering()
        assertEquals(1, vurderinger.size)
        observatør.assertVurdering(vurderinger.first(), true)
    }

    @Test
    fun `En betinget vurdering blir ikke lagt til dersom betingelsen ikke er oppfylt`() {
        nyVurdering(false)
        assertEquals(0, vurderinger.size)
    }

    private fun nyVurdering(
        funnetRelevant: Boolean = true,
        oppfylt: Boolean = true,
        versjon: LocalDate = LocalDate.MAX,
        paragraf: Paragraf = Paragraf.PARAGRAF_8_2,
        ledd: Ledd = 1.ledd,
        punktum: List<Punktum> = emptyList(),
        bokstaver: List<Bokstav> = emptyList(),
        input: Map<String, Any> = emptyMap(),
        output: Map<String, Any> = emptyMap(),
        kontekster: Map<String, String> = emptyMap()
    ) {
        vurderinger = BetingetVurdering(funnetRelevant, oppfylt, versjon, paragraf, ledd, punktum, bokstaver, input, output, kontekster).sammenstill(vurderinger)
    }

    private class JuridiskVurderingObservatør : JuridiskVurderingVisitor {
        private var funnetRelevant: Boolean? = null

        override fun visitBetingetVurdering(funnetRelevant: Boolean) {
            this.funnetRelevant = funnetRelevant
        }

        fun assertVurdering(juridiskVurdering: JuridiskVurdering, funnetRelevant: Boolean) {
            require(juridiskVurdering is BetingetVurdering)
            juridiskVurdering.accept(this)
            assertEquals(funnetRelevant, this.funnetRelevant)
        }
    }
}
