package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.Objects
import java.util.UUID
import kotlin.reflect.KClass
import no.nav.helse.dto.HendelseskildeDto
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje

internal typealias Melding = KClass<out SykdomshistorikkHendelse>

interface SykdomshistorikkHendelse {
    fun sykdomstidslinje(): Sykdomstidslinje

    class Hendelseskilde(
        private val type: String,
        private val meldingsreferanseId: UUID,
        private val tidsstempel: LocalDateTime
    ) {
        internal constructor(
            hendelse: Melding,
            meldingsreferanseId: UUID,
            tidsstempel: LocalDateTime
        ) : this(kildenavn(hendelse), meldingsreferanseId, tidsstempel)

        companion object {
            internal val INGEN = Hendelseskilde(SykdomshistorikkHendelse::class, UUID.randomUUID(), LocalDateTime.now())

            private fun kildenavn(hendelse: Melding): String =
                hendelse.simpleName ?: "Ukjent"

            internal fun tidligsteTidspunktFor(kilder: List<Hendelseskilde>, type: Melding): LocalDateTime {
                check(kilder.all { it.erAvType(type) })
                return kilder.first().tidsstempel
            }

            internal fun gjenopprett(dto: HendelseskildeDto): Hendelseskilde {
                return Hendelseskilde(
                    type = dto.type,
                    meldingsreferanseId = dto.meldingsreferanseId,
                    tidsstempel = dto.tidsstempel
                )
            }
        }

        override fun toString() = type
        override fun equals(other: Any?) = other is Hendelseskilde && type == other.type && tidsstempel == other.tidsstempel && meldingsreferanseId == other.meldingsreferanseId
        override fun hashCode() = Objects.hash(type, tidsstempel, meldingsreferanseId)
        internal fun meldingsreferanseId() = meldingsreferanseId
        internal fun erAvType(meldingstype: Melding) = this.type == kildenavn(meldingstype)

        // todo: midlertidig fordi "Inntektsmelding" ikke er en SykdomshistorikkHendelse. Alle dager med kilde "Inntektsmelding" må migreres til "BitFraInntektsmelding"
        internal fun erAvType(meldingstype: String) = this.type == meldingstype
        internal fun toJson() = mapOf("type" to type, "id" to meldingsreferanseId, "tidsstempel" to tidsstempel)
        internal fun dto() = HendelseskildeDto(type, meldingsreferanseId, tidsstempel)
    }
}
