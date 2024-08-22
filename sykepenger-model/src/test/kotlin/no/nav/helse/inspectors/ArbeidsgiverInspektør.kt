package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.ArbeidsgiverVisitor
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.TilstandType
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk

internal val Arbeidsgiver.inspektør get() = ArbeidsgiverInspektør(this)

internal class ArbeidsgiverInspektør(arbeidsgiver: Arbeidsgiver): ArbeidsgiverVisitor {
    private val vedtaksperioder: MutableMap<UUID, Vedtaksperiode> = mutableMapOf()
    private var aktiveVedtaksperioder: List<Vedtaksperiode> = emptyList()
    private val sisteVedtaksperiodeTilstander: MutableMap<UUID, TilstandType> = mutableMapOf()
    private var sisteInntektshistorikk: Inntektshistorikk? = null

    internal lateinit var organisasjonsnummer: String
        private set

    internal lateinit var refusjonshistorikk: Refusjonshistorikk
        private set

    internal lateinit var sykdomshistorikk: Sykdomshistorikk
        private set

    init {
        arbeidsgiver.accept(this)
    }

    internal fun aktiveVedtaksperioder() = aktiveVedtaksperioder
    internal fun forkastedeVedtaksperioder() = vedtaksperioder.values - aktiveVedtaksperioder
    internal fun sisteVedtaksperiodeTilstander() = sisteVedtaksperiodeTilstander
    internal val inntektshistorikk get() = sisteInntektshistorikk!!

    override fun preVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver, id: UUID, organisasjonsnummer: String) {
        this.organisasjonsnummer = organisasjonsnummer
    }

    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        tilstand: Vedtaksperiode.Vedtaksperiodetilstand,
        opprettet: LocalDateTime,
        oppdatert: LocalDateTime,
        periode: Periode,
        opprinneligPeriode: Periode,
        skjæringstidspunkt: LocalDate,
        hendelseIder: Set<Dokumentsporing>,
        egenmeldingsperioder: List<Periode>
    ) {
        vedtaksperioder[id] = vedtaksperiode
        sisteVedtaksperiodeTilstander[id] = tilstand.type
    }

    override fun preVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {
        aktiveVedtaksperioder = vedtaksperioder
    }

    override fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
        this.sisteInntektshistorikk = inntektshistorikk
    }

    override fun preVisitRefusjonshistorikk(refusjonshistorikk: Refusjonshistorikk) {
        this.refusjonshistorikk = refusjonshistorikk
    }

    override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        this.sykdomshistorikk = sykdomshistorikk
    }
}