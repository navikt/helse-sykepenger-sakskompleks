package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender.SAKSBEHANDLER
import no.nav.helse.person.MinimumSykdomsgradsvurdering

/**
 * Melding om perioder saksbehandler har vurdert dithet at bruker har tapt nok arbeidstid til å ha rett på sykepenger,
 * tross < 20% tapt inntekt
 */
class MinimumSykdomsgradsvurderingMeldingData(
    internal val perioderMedMinimumSykdomsgradVurdertOK: Set<Periode>,
    internal val perioderMedMinimumSykdomsgradVurdertIkkeOK: Set<Periode>
): Overstyringsdata

class MinimumSykdomsgradsvurderingMelding(
    data: MinimumSykdomsgradsvurderingMeldingData,
    override val metadata: HendelseMetadata,
    overstyring: Overstyring
) : Hendelse, Overstyring by overstyring {
    private val perioderMedMinimumSykdomsgradVurdertOK = data.perioderMedMinimumSykdomsgradVurdertOK
    private val perioderMedMinimumSykdomsgradVurdertIkkeOK = data.perioderMedMinimumSykdomsgradVurdertIkkeOK

    constructor(perioderMedMinimumSykdomsgradVurdertOK: Set<Periode>, perioderMedMinimumSykdomsgradVurdertIkkeOK: Set<Periode>, meldingsreferanseId: UUID): this(
        data = MinimumSykdomsgradsvurderingMeldingData(perioderMedMinimumSykdomsgradVurdertOK, perioderMedMinimumSykdomsgradVurdertIkkeOK),
        metadata = LocalDateTime.now().let { HendelseMetadata(meldingsreferanseId, SAKSBEHANDLER, it, it, false) },
        overstyring = Overstyring.Enkeltoverstyring
    )

    init {
        sjekkForOverlapp()
    }

    override val behandlingsporing = Behandlingsporing.IngenArbeidsgiver

    internal fun oppdater(vurdering: MinimumSykdomsgradsvurdering) {
        vurdering.leggTil(perioderMedMinimumSykdomsgradVurdertOK)
        vurdering.trekkFra(perioderMedMinimumSykdomsgradVurdertIkkeOK)
        sjekkForOverlapp()
    }

    private fun sjekkForOverlapp() {
        perioderMedMinimumSykdomsgradVurdertOK.forEach {
            if (perioderMedMinimumSykdomsgradVurdertIkkeOK.contains(it)) {
                error("overlappende perioder i MinimumSykdomsgradsvurdering! $it er vurdert OK, men også vurdert til IKKE å være OK")
            }
        }
    }


    internal fun periodeForEndring(): Periode {
        val alle = perioderMedMinimumSykdomsgradVurdertOK + perioderMedMinimumSykdomsgradVurdertIkkeOK
        return Periode(alle.minOf { it.start }, alle.maxOf { it.endInclusive })
    }

    fun valider(): Boolean {
        if (perioderMedMinimumSykdomsgradVurdertOK.isEmpty() && perioderMedMinimumSykdomsgradVurdertIkkeOK.isEmpty()) return false
        if (perioderMedMinimumSykdomsgradVurdertOK.containsAll(perioderMedMinimumSykdomsgradVurdertIkkeOK) && perioderMedMinimumSykdomsgradVurdertIkkeOK.containsAll(
                perioderMedMinimumSykdomsgradVurdertOK
            )
        ) return false
        return true
    }

}
