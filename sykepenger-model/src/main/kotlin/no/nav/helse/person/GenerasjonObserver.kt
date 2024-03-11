package no.nav.helse.person

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Hendelse
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg

internal interface GenerasjonObserver {

    fun avsluttetUtenVedtak(
        hendelse: IAktivitetslogg,
        generasjonId: UUID,
        tidsstempel: LocalDateTime,
        periode: Periode,
        dokumentsporing: Set<UUID>
    )
    fun vedtakIverksatt(
        hendelse: IAktivitetslogg,
        generasjonId: UUID,
        tidsstempel: LocalDateTime,
        periode: Periode,
        dokumentsporing: Set<UUID>,
        utbetalingId: UUID,
        vedtakFattetTidspunkt: LocalDateTime,
        vilkårsgrunnlag: VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
    )
    fun vedtakAnnullert(hendelse: IAktivitetslogg, generasjonId: UUID)
    fun generasjonLukket(generasjonId: UUID)
    fun generasjonForkastet(generasjonId: UUID, hendelse: Hendelse)

    fun nyGenerasjon(
        id: UUID,
        periode: Periode,
        meldingsreferanseId: UUID,
        innsendt: LocalDateTime,
        registert: LocalDateTime,
        avsender: Avsender,
        type: PersonObserver.GenerasjonOpprettetEvent.Type
    )
}