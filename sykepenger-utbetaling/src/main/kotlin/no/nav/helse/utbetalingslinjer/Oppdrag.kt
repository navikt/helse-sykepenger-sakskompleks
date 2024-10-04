package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.dto.EndringskodeDto
import no.nav.helse.dto.FagområdeDto
import no.nav.helse.dto.OppdragstatusDto
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.dto.deserialisering.OppdragInnDto
import no.nav.helse.dto.serialisering.OppdragUtDto
import no.nav.helse.erHelg
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.SimuleringHendelse
import no.nav.helse.hendelser.UtbetalingHendelseHendelse
import no.nav.helse.hendelser.til
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetskontekst
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_OS_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_UT_23
import no.nav.helse.utbetalingslinjer.Feriepengegrunnlag.UtbetaltDag
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AVVIST
import no.nav.helse.utbetalingslinjer.Oppdragstatus.FEIL
import no.nav.helse.utbetalingslinjer.Utbetalingslinje.Companion.kjedeSammenLinjer
import no.nav.helse.utbetalingslinjer.Utbetalingslinje.Companion.kobleTil
import no.nav.helse.utbetalingslinjer.Utbetalingslinje.Companion.normaliserLinjer

class Oppdrag private constructor(
    val mottaker: String,
    val fagområde: Fagområde,
    val linjer: MutableList<Utbetalingslinje>,
    val fagsystemId: String,
    val endringskode: Endringskode,
    nettoBeløp: Int = linjer.sumOf { it.totalbeløp() },
    overføringstidspunkt: LocalDateTime? = null,
    avstemmingsnøkkel: Long? = null,
    status: Oppdragstatus? = null,
    val tidsstempel: LocalDateTime,
    erSimulert: Boolean = false,
    simuleringsResultat: SimuleringResultatDto? = null
) : List<Utbetalingslinje> by linjer, Aktivitetskontekst {
    var nettoBeløp: Int = nettoBeløp
        private set
    var overføringstidspunkt: LocalDateTime? = overføringstidspunkt
        private set
    var avstemmingsnøkkel: Long? = avstemmingsnøkkel
        private set
    var status: Oppdragstatus? = status
        private set
    var erSimulert: Boolean = erSimulert
        private set
    var simuleringsResultat: SimuleringResultatDto? = simuleringsResultat
        private set

    companion object {
        fun periode(vararg oppdrag: Oppdrag): Periode? {
            return oppdrag
                .mapNotNull { it.linjeperiode }
                .takeIf(List<*>::isNotEmpty)
                ?.reduce(Periode::plus)
        }

        fun List<Oppdrag>.trekkerTilbakePenger() = sumOf { it.nettoBeløp() } < 0

        fun stønadsdager(vararg oppdrag: Oppdrag): Int {
            return Utbetalingslinje.stønadsdager(oppdrag.toList().flatten())
        }

        fun synkronisert(vararg oppdrag: Oppdrag): Boolean {
            val endrede = oppdrag.filter { it.harUtbetalinger() }
            return endrede.all { it.status == endrede.first().status }
        }

        fun ingenFeil(vararg oppdrag: Oppdrag) = oppdrag.none { it.status in listOf(AVVIST, FEIL) }
        fun harFeil(vararg oppdrag: Oppdrag) = oppdrag.any { it.status in listOf(AVVIST, FEIL) }
        fun kanIkkeForsøkesPåNy(vararg oppdrag: Oppdrag) = oppdrag.any { it.status == AVVIST }

        internal fun List<Oppdrag>.valider(aktivitetslogg: IAktivitetslogg) {
            if (all { it.nettoBeløp >= 0 }) return
            aktivitetslogg.varsel(RV_UT_23)
        }

        fun gjenopprett(dto: OppdragInnDto): Oppdrag {
            return Oppdrag(
                mottaker = dto.mottaker,
                fagområde = when (dto.fagområde) {
                    FagområdeDto.SP -> Fagområde.Sykepenger
                    FagområdeDto.SPREF -> Fagområde.SykepengerRefusjon
                },
                linjer = dto.linjer.map { Utbetalingslinje.gjenopprett(it) }.toMutableList(),
                fagsystemId = dto.fagsystemId,
                endringskode = Endringskode.gjenopprett(dto.endringskode),
                nettoBeløp = dto.nettoBeløp,
                overføringstidspunkt = dto.overføringstidspunkt,
                avstemmingsnøkkel = dto.avstemmingsnøkkel,
                status = when (dto.status) {
                    OppdragstatusDto.AKSEPTERT -> Oppdragstatus.AKSEPTERT
                    OppdragstatusDto.AKSEPTERT_MED_FEIL -> Oppdragstatus.AKSEPTERT_MED_FEIL
                    OppdragstatusDto.AVVIST -> Oppdragstatus.AVVIST
                    OppdragstatusDto.FEIL -> Oppdragstatus.FEIL
                    OppdragstatusDto.OVERFØRT -> Oppdragstatus.OVERFØRT
                    null -> null
                },
                tidsstempel = dto.tidsstempel,
                erSimulert = dto.erSimulert,
                simuleringsResultat = dto.simuleringsResultat
            )
        }
    }

    val linjeperiode get() = firstOrNull()?.let { (it.datoStatusFom ?: it.fom) til last().tom }

    constructor(
        mottaker: String,
        fagområde: Fagområde,
        linjer: List<Utbetalingslinje> = listOf(),
        fagsystemId: String = genererUtbetalingsreferanse(UUID.randomUUID())
    ) : this(
        mottaker,
        fagområde,
        normaliserLinjer(fagsystemId, linjer).toMutableList(),
        fagsystemId,
        Endringskode.NY,
        tidsstempel = LocalDateTime.now()
    )

    fun detaljer(): OppdragDetaljer {
        val linjene = map { it.detaljer() }
        return OppdragDetaljer(
            fagsystemId = fagsystemId,
            fagområde = fagområde.verdi,
            mottaker = mottaker,
            nettoBeløp = nettoBeløp,
            stønadsdager = stønadsdager(),
            fom = linjene.firstOrNull()?.fom ?: LocalDate.MIN,
            tom = linjene.lastOrNull()?.tom ?: LocalDate.MIN,
            linjer = linjene
        )
    }

    private operator fun contains(other: Oppdrag) = this.tilhører(other) || this.overlapperMed(other)

    fun tilhører(other: Oppdrag) = this.fagsystemId == other.fagsystemId && this.fagområde == other.fagområde
    private fun overlapperMed(other: Oppdrag) =
        this.linjeperiode?.let { other.linjeperiode?.overlapperMed(it) } ?: false

    fun overfør(
        aktivitetslogg: IAktivitetslogg,
        maksdato: LocalDate?,
        saksbehandler: String
    ) {
        if (status == Oppdragstatus.AKSEPTERT) return aktivitetslogg.info("Overfører ikke oppdrag som allerede er akseptert for fagområde=$fagområde med fagsystemId=$fagsystemId")
        if (!harUtbetalinger()) return aktivitetslogg.info("Overfører ikke oppdrag uten endring for fagområde=$fagområde med fagsystemId=$fagsystemId")
        check(endringskode != Endringskode.UEND)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.behov(Behovtype.Utbetaling, "Trenger å sende utbetaling til Oppdrag", behovdetaljer(saksbehandler, maksdato))
    }

    fun simuler(aktivitetslogg: IAktivitetslogg, maksdato: LocalDate, saksbehandler: String) {
        if (!harUtbetalinger()) return aktivitetslogg.info("Simulerer ikke oppdrag uten endring fagområde=$fagområde med fagsystemId=$fagsystemId")
        check(endringskode != Endringskode.UEND)
        check(status == null)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.behov(Behovtype.Simulering, "Trenger simulering fra Oppdragssystemet", behovdetaljer(saksbehandler, maksdato))
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst("Oppdrag", mapOf("fagsystemId" to fagsystemId))

    private fun behovdetaljer(saksbehandler: String, maksdato: LocalDate?): MutableMap<String, Any> {
        return mutableMapOf(
            "mottaker" to mottaker,
            "fagområde" to "$fagområde",
            "linjer" to kopierKunLinjerMedEndring().map(Utbetalingslinje::behovdetaljer),
            "fagsystemId" to fagsystemId,
            "endringskode" to "$endringskode",
            "saksbehandler" to saksbehandler
        ).apply {
            maksdato?.let {
                put("maksdato", maksdato.toString())
            }
        }
    }

    fun totalbeløp() = linjerUtenOpphør().sumOf { it.totalbeløp() }
    fun stønadsdager() = sumOf { it.stønadsdager() }

    fun nettoBeløp() = nettoBeløp

    private fun nettoBeløp(tidligere: Oppdrag) {
        nettoBeløp = this.totalbeløp() - tidligere.totalbeløp()
    }

    fun harUtbetalinger() = any(Utbetalingslinje::erForskjell)

    fun erRelevant(fagsystemId: String, fagområde: Fagområde) =
        this.fagsystemId == fagsystemId && this.fagområde == fagområde

    private fun kopierKunLinjerMedEndring() = kopierMed(filter(Utbetalingslinje::erForskjell))

    private fun kopierUtenOpphørslinjer() = kopierMed(linjerUtenOpphør())

    fun linjerUtenOpphør() = filter { !it.erOpphør() }

    fun annuller(aktivitetslogg: IAktivitetslogg): Oppdrag {
        return tomtOppdrag().minus(this, aktivitetslogg)
    }

    private fun tomtOppdrag(): Oppdrag =
        Oppdrag(
            mottaker = mottaker,
            fagområde = fagområde,
            fagsystemId = fagsystemId
        )

    fun begrensFra(førsteDag: LocalDate): Oppdrag {
        val (senereLinjer, tidligereLinjer) = this.linjer
            .filterNot { it.erOpphør() }
            .partition { it.fom >= førsteDag }
        val delvisOverlappendeFørsteLinje = tidligereLinjer
            .lastOrNull()
            ?.takeIf { it.tom >= førsteDag }
            ?.begrensFra(førsteDag)
        return kopierMed(listOfNotNull(delvisOverlappendeFørsteLinje) + senereLinjer)
    }

    fun begrensTil(sisteDato: LocalDate, other: Oppdrag? = null): Oppdrag {
        val (tidligereLinjer, senereLinjer) = this.linjer
            .filterNot { it.erOpphør() }
            .partition { it.tom <= sisteDato }
        val delvisOverlappendeSisteLinje = senereLinjer
            .firstOrNull()
            ?.takeIf { it.fom <= sisteDato }
            ?.begrensTil(sisteDato)
        other?.also { kobleTil(it) }
        return kopierMed(tidligereLinjer + listOfNotNull(delvisOverlappendeSisteLinje))
    }

    operator fun plus(other: Oppdrag): Oppdrag {
        check(none { linje -> other.any { it.periode.overlapperMed(linje.periode) } }) {
            "ikke støttet: kan ikke overlappe med annet oppdrag"
        }
        if (this.isNotEmpty() && other.isNotEmpty() && this.fomHarFlyttetSegFremover(other)) return other + this
        return kopierMed((slåSammenOppdrag(other)))
    }

    private fun slåSammenOppdrag(other: Oppdrag): List<Utbetalingslinje> {
        if (this.isEmpty()) return other.linjer
        if (other.isEmpty()) return this.linjer
        val sisteLinje = this.last()
        val første = other.first()
        val mellomlinje = sisteLinje.slåSammenLinje(første) ?: return this.linjer + other.linjer
        return this.linjer.dropLast(1) + listOf(mellomlinje) + other.linjer.drop(1)
    }

    fun minus(eldre: Oppdrag, aktivitetslogg: IAktivitetslogg): Oppdrag {
        // overtar fagsystemId fra tidligere Oppdrag uten utbetaling, gitt at det er samme arbeidsgiverperiode
        if (eldre.erTomt()) return medFagsystemId(eldre)
        return when {
            // om man trekker fra et utbetalt oppdrag med et tomt oppdrag medfører det et oppdrag som opphører (les: annullerer) hele fagsystemIDen
            erTomt() -> annulleringsoppdrag(eldre)
            eldre.ingenUtbetalteDager() -> kjørFrem(eldre)
            // "fom" kan flytte seg fremover i tid dersom man, eksempelvis, revurderer en utbetalt periode til å starte med ikke-utbetalte dager (f.eks. ferie)
            fomHarFlyttetSegFremover(eldre.kopierUtenOpphørslinjer()) -> {
                aktivitetslogg.varsel(RV_OS_2)
                returførOgKjørFrem(eldre.kopierUtenOpphørslinjer())
            }
            // utbetaling kan endres til å starte tidligere, eksempelvis via revurdering der feriedager egentlig er sykedager
            fomHarFlyttetSegBakover(eldre.kopierUtenOpphørslinjer()) -> {
                aktivitetslogg.varsel(RV_OS_2)
                kjørFrem(eldre.kopierUtenOpphørslinjer())
            }
            // fom er lik, men endring kan oppstå overalt ellers
            else -> endre(eldre.kopierUtenOpphørslinjer(), aktivitetslogg)
        }.also { it.nettoBeløp(eldre) }
    }

    private fun ingenUtbetalteDager() = linjerUtenOpphør().isEmpty()

    private fun erTomt() = this.isEmpty()

    // Vi har oppdaget utbetalingsdager tidligere i tidslinjen
    private fun fomHarFlyttetSegBakover(eldre: Oppdrag) = this.first().fom < eldre.first().fom

    // Vi har endret tidligere utbetalte dager til ikke-utbetalte dager i starten av tidslinjen
    private fun fomHarFlyttetSegFremover(eldre: Oppdrag) = this.first().fom > eldre.first().fom

    // man opphører (annullerer) et annet oppdrag ved å lage en opphørslinje som dekker hele perioden som er utbetalt
    // om det forrige oppdraget også var et opphør så kopieres siste linje for å bevare
    // delytelseId-rekkefølgen slik at det nye oppdraget kan bygges videre på
    private fun annulleringsoppdrag(tidligere: Oppdrag) =
        if (tidligere.kopierUtenOpphørslinjer().erTomt()) kopierMed(
            linjer = listOf(tidligere.last().markerUendret(tidligere.last())),
            fagsystemId = tidligere.fagsystemId,
            endringskode = Endringskode.UEND
        )
        else kopierMed(
            linjer = listOf(tidligere.last().opphørslinje(tidligere.kopierUtenOpphørslinjer().first().fom)),
            fagsystemId = tidligere.fagsystemId,
            endringskode = Endringskode.ENDR
        )
    // når man oppretter en NY linje med dato-intervall "(a, b)" vil oppdragsystemet
    // automatisk opphøre alle eventuelle linjer med fom > b.
    //
    // Eksempel:
    // Oppdrag 1: 5. januar til 31. januar (linje 1)
    // Oppdrag 2: 1. januar til 10. januar
    // Fordi linje "1. januar - 10. januar" opprettes som NY, medfører dette at oppdragsystemet opphører 11. januar til 31. januar automatisk
    private fun kjørFrem(tidligere: Oppdrag): Oppdrag {
        val sammenkoblet = this.kobleTil(tidligere)
        val linjer = kjedeSammenLinjer(sammenkoblet, tidligere.last())
        return sammenkoblet.kopierMed(linjer)
    }


    private fun endre(avtroppendeOppdrag: Oppdrag, aktivitetslogg: IAktivitetslogg) =
        DifferanseBuilder(this).kalkulerDifferanse(avtroppendeOppdrag, aktivitetslogg)

    // når man oppretter en NY linje vil Oppdragsystemet IKKE ta stilling til periodene FØR.
    // Man må derfor eksplisitt opphøre evt. perioder tidligere, som i praksis vil medføre at
    // oppdraget kjøres tilbake, så fremover
    private fun returførOgKjørFrem(tidligere: Oppdrag): Oppdrag {
        val deletion = this.opphørOppdrag(tidligere)
        val kjørtFrem = this.kjørFrem(tidligere)
        return kjørtFrem.kopierMed(listOf(deletion) + kjørtFrem.linjer)
    }

    private fun opphørOppdrag(tidligere: Oppdrag) =
        tidligere.last().opphørslinje(tidligere.first().fom)


    private fun medFagsystemId(other: Oppdrag) = kopierMed(this.linjer, fagsystemId = other.fagsystemId)

    private fun kopierMed(linjer: List<Utbetalingslinje>, fagsystemId: String = this.fagsystemId, endringskode: Endringskode = this.endringskode) = Oppdrag(
        mottaker = mottaker,
        fagområde = fagområde,
        linjer = linjer.map { it.kopier() }.toMutableList(),
        fagsystemId = fagsystemId,
        endringskode = endringskode,
        overføringstidspunkt = overføringstidspunkt,
        avstemmingsnøkkel = avstemmingsnøkkel,
        status = status,
        tidsstempel = tidsstempel,
        erSimulert = erSimulert,
        simuleringsResultat = simuleringsResultat
    )

    private fun kobleTil(tidligere: Oppdrag) = kopierMed(
        linjer.kobleTil(tidligere.fagsystemId),
        tidligere.fagsystemId,
        Endringskode.ENDR
    )

    fun lagreOverføringsinformasjon(hendelse: UtbetalingHendelseHendelse) {
        if (hendelse.fagsystemId != this.fagsystemId) return
        if (this.avstemmingsnøkkel == null) this.avstemmingsnøkkel = hendelse.avstemmingsnøkkel
        if (this.overføringstidspunkt == null) this.overføringstidspunkt = hendelse.overføringstidspunkt
        this.status = hendelse.status
    }
    fun håndter(simulering: SimuleringHendelse) {
        if (simulering.fagsystemId != this.fagsystemId || simulering.fagområde != this.fagområde || !simulering.simuleringOK) return
        this.erSimulert = true
        this.simuleringsResultat = simulering.simuleringsResultat
    }
    fun erKlarForGodkjenning() = !harUtbetalinger() || erSimulert

    private class DifferanseBuilder(
        private val påtroppendeOppdrag: Oppdrag
    ) {
        private lateinit var tilstand: Tilstand
        private lateinit var sisteLinjeITidligereOppdrag: Utbetalingslinje

        private lateinit var linkTo: Utbetalingslinje

        // forsøker så langt det lar seg gjøre å endre _siste_ linje, dersom mulig *)
        // ellers lager den NY linjer fra og med linja før endringen oppstod
        // *) en linje kan endres dersom "tom"-dato eller grad er eneste forskjell
        //    ulik dagsats eller fom-dato medfører enten at linjen får status OPPH, eller at man overskriver
        //    ved å sende NY linjer
        fun kalkulerDifferanse(avtroppendeOppdrag: Oppdrag, aktivitetslogg: IAktivitetslogg): Oppdrag {
            this.linkTo = avtroppendeOppdrag.last()
            val kobletTil = påtroppendeOppdrag.kobleTil(avtroppendeOppdrag)
            val medLinkeLinjer = kopierLikeLinjer(kobletTil, avtroppendeOppdrag, aktivitetslogg)
            if (medLinkeLinjer.last().erForskjell()) return medLinkeLinjer
            return medLinkeLinjer.kopierMed(medLinkeLinjer.linjer, endringskode = Endringskode.UEND)
        }

        private fun opphørTidligereLinjeOgOpprettNy(
            nåværende: Utbetalingslinje,
            tidligere: Utbetalingslinje,
            aktivitetslogg: IAktivitetslogg,
            datoStatusFom: LocalDate = tidligere.fom
        ): List<Utbetalingslinje> {
            linkTo = tidligere
            val opphørslinje = tidligere.opphørslinje(datoStatusFom)
            val linketTilForrige = nåværende.kobleTil(linkTo)
            linkTo = linketTilForrige
            tilstand = Ny()
            aktivitetslogg.varsel(RV_OS_3)
            return listOf(opphørslinje, linketTilForrige)
        }

        private fun kopierLikeLinjer(nytt: Oppdrag, tidligere: Oppdrag, aktivitetslogg: IAktivitetslogg): Oppdrag {
            tilstand = if (tidligere.last().tom > nytt.last().tom) Slett(nytt.last()) else Identisk()
            sisteLinjeITidligereOppdrag = tidligere.last()
            val linjer = nytt.zip(tidligere).map { (a, b) -> tilstand.håndterForskjell(a, b, aktivitetslogg) }.flatten()
            val remaining = (nytt.size - minOf(nytt.size, tidligere.size)).coerceAtLeast(0)
            val nyeLinjer = nytt.takeLast(remaining)
            return nytt.kopierMed(linjer + kjedeSammenLinjer(nyeLinjer, linjer.last()))
        }

        private fun håndterUlikhet(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg): List<Utbetalingslinje> {
            return when {
                nåværende.kanEndreEksisterendeLinje(tidligere, sisteLinjeITidligereOppdrag) -> listOf(nåværende.endreEksisterendeLinje(tidligere))
                nåværende.skalOpphøreOgErstatte(tidligere, sisteLinjeITidligereOppdrag) -> opphørTidligereLinjeOgOpprettNy(nåværende, tidligere, aktivitetslogg)
                nåværende.fom > tidligere.fom -> opphørTidligereLinjeOgOpprettNy(nåværende, sisteLinjeITidligereOppdrag, aktivitetslogg, tidligere.fom)
                else -> listOf(opprettNyLinje(nåværende))
            }
        }

        private fun opprettNyLinje(nåværende: Utbetalingslinje): Utbetalingslinje {
            val nyLinje = nåværende.kobleTil(linkTo)
            linkTo = nyLinje
            tilstand = Ny()
            return nyLinje
        }


        private interface Tilstand {
            fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg): List<Utbetalingslinje>
        }

        private inner class Identisk : Tilstand {
            override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg): List<Utbetalingslinje> {
                if (nåværende == tidligere) return listOf(nåværende.markerUendret(tidligere))
                return håndterUlikhet(nåværende, tidligere, aktivitetslogg)
            }
        }

        private inner class Slett(private val sisteLinjeINyttOppdrag: Utbetalingslinje) : Tilstand {
            override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg): List<Utbetalingslinje> {
                if (nåværende == tidligere) {
                    if (nåværende == sisteLinjeINyttOppdrag) return listOf(nåværende.kobleTil(linkTo))
                    return listOf(nåværende.markerUendret(tidligere))
                }
                return håndterUlikhet(nåværende, tidligere, aktivitetslogg)
            }
        }

        private inner class Ny : Tilstand {
            override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg): List<Utbetalingslinje> {
                val nyLinje = nåværende.kobleTil(linkTo)
                linkTo = nyLinje
                return listOf(nyLinje)
            }
        }
    }

    internal fun betalteDager() = linjerUtenOpphør().flatMap { linje ->
        linje
            .takeIf { linje.beløp != null }
            ?.asSequence()
            ?.filterNot { it.erHelg() }
            ?.map { UtbetaltDag(it, linje.beløp!!) }
            ?.toList()
            ?: emptyList<UtbetaltDag>()
    }

    fun dto() = OppdragUtDto(
        mottaker = mottaker,
        fagområde = when (fagområde) {
            Fagområde.SykepengerRefusjon -> FagområdeDto.SPREF
            Fagområde.Sykepenger -> FagområdeDto.SP
        },
        linjer = linjer.map { it.dto() },
        fagsystemId = fagsystemId,
        endringskode = when (endringskode) {
            Endringskode.NY -> EndringskodeDto.NY
            Endringskode.UEND -> EndringskodeDto.UEND
            Endringskode.ENDR -> EndringskodeDto.ENDR
        },
        nettoBeløp = nettoBeløp,
        totalbeløp = this.totalbeløp(),
        stønadsdager = this.stønadsdager(),
        overføringstidspunkt = overføringstidspunkt,
        avstemmingsnøkkel = avstemmingsnøkkel,
        status = when (status) {
            Oppdragstatus.OVERFØRT -> OppdragstatusDto.OVERFØRT
            Oppdragstatus.AKSEPTERT -> OppdragstatusDto.AKSEPTERT
            Oppdragstatus.AKSEPTERT_MED_FEIL -> OppdragstatusDto.AKSEPTERT_MED_FEIL
            AVVIST -> OppdragstatusDto.AVVIST
            FEIL -> OppdragstatusDto.FEIL
            null -> null
        },
        tidsstempel = tidsstempel,
        erSimulert = erSimulert,
        simuleringsResultat = simuleringsResultat
    )
}

enum class Oppdragstatus { OVERFØRT, AKSEPTERT, AKSEPTERT_MED_FEIL, AVVIST, FEIL }