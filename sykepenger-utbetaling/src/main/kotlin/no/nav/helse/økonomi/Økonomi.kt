package no.nav.helse.økonomi

import no.nav.helse.dto.deserialisering.ØkonomiInnDto
import no.nav.helse.dto.serialisering.ØkonomiUtDto
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.summer
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.slf4j.LoggerFactory
import kotlin.properties.Delegates

class Økonomi private constructor(
    private val grad: Prosentdel,
    private val totalGrad: Prosentdel = grad,
    private val arbeidsgiverRefusjonsbeløp: Inntekt = INGEN,
    private val aktuellDagsinntekt: Inntekt = INGEN,
    private val beregningsgrunnlag: Inntekt = INGEN,
    private val dekningsgrunnlag: Inntekt = INGEN,
    private val grunnbeløpgrense: Inntekt? = null,
    private val arbeidsgiverbeløp: Inntekt? = null,
    private val personbeløp: Inntekt? = null,
    private val er6GBegrenset: Boolean? = null,
    private val tilstand: Tilstand = Tilstand.KunGrad,
) {
    companion object {
        private val arbeidsgiverBeløp = { økonomi: Økonomi -> økonomi.arbeidsgiverbeløp!! }
        private val personBeløp = { økonomi: Økonomi -> økonomi.personbeløp!! }
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        fun sykdomsgrad(grad: Prosentdel) =
            Økonomi(grad)

        fun ikkeBetalt() = Økonomi(
            grad = 0.prosent,
            tilstand = Tilstand.IkkeBetalt
        )

        fun List<Økonomi>.totalSykdomsgrad(): Prosentdel {
            val økonomiList = totalSykdomsgrad(this)
            return økonomiList.first().totalGrad
        }

        private fun List<Økonomi>.beregningsgrunnlag() = map { it.beregningsgrunnlag }.summer()

        fun totalSykdomsgrad(økonomiList: List<Økonomi>): List<Økonomi> {
            val beregningsgrunnlag = økonomiList.beregningsgrunnlag()
            if (beregningsgrunnlag == INGEN) {
                val økonomiListMedSykdomsgrad = økonomiList.filter { it.sykdomsgrad() > 0.prosent }
                if (økonomiListMedSykdomsgrad.isEmpty()) {
                    return økonomiList.map { økonomi: Økonomi ->
                        økonomi.kopierMed(totalgrad = 0.prosent)
                    }
                }
                else {
                    val totalgrad = Inntekt.vektlagtGjennomsnitt(økonomiListMedSykdomsgrad.map { it.sykdomsgrad() to it.aktuellDagsinntekt }, beregningsgrunnlag)
                    return økonomiList.map { økonomi: Økonomi ->
                        økonomi.kopierMed(totalgrad = totalgrad)
                    }
                }
            }
            val totalgrad = Inntekt.vektlagtGjennomsnitt(økonomiList.map { it.sykdomsgrad() to it.aktuellDagsinntekt }, beregningsgrunnlag)
            return økonomiList.map { økonomi: Økonomi ->
                økonomi.kopierMed(totalgrad = totalgrad)
            }
        }

        fun List<Økonomi>.erUnderGrensen() = none { !it.totalGrad.erUnderGrensen() }

        private fun totalUtbetalingsgrad(økonomiList: List<Økonomi>) =
            Inntekt.vektlagtGjennomsnitt(økonomiList.map { it.utbetalingsgrad() to it.aktuellDagsinntekt }, økonomiList.beregningsgrunnlag())

        fun betal(økonomiList: List<Økonomi>): List<Økonomi> {
            val utbetalingsgrad = totalUtbetalingsgrad(økonomiList)
            val foreløpig = delteUtbetalinger(økonomiList)
            return fordelBeløp(foreløpig, utbetalingsgrad)
        }

        private fun delteUtbetalinger(økonomiList: List<Økonomi>) = økonomiList.map { it.betal() }

        private fun fordelBeløp(økonomiList: List<Økonomi>, utbetalingsgrad: Prosentdel): List<Økonomi> {
            val totalArbeidsgiver = totalArbeidsgiver(økonomiList)
            val totalPerson = totalPerson(økonomiList)
            val total = totalArbeidsgiver + totalPerson
            if (total == INGEN) return økonomiList.map { økonomi -> økonomi.kopierMed(er6GBegrenset = false) }

            // todo: trenger ikke vite 6G hvis inntektene er redusert ihht. 6G når de settes på Økonomi
            val grunnbeløp = økonomiList.firstNotNullOf { it.grunnbeløpgrense }
            val beregningsgrunnlag = økonomiList.beregningsgrunnlag()
            val sykepengegrunnlagBegrenset6G = minOf(beregningsgrunnlag, grunnbeløp)
            val er6GBegrenset = beregningsgrunnlag > grunnbeløp

            val sykepengegrunnlag = (sykepengegrunnlagBegrenset6G * utbetalingsgrad).rundTilDaglig()
            val fordelingRefusjon = fordel(økonomiList, totalArbeidsgiver, sykepengegrunnlag, { økonomi, inntekt -> økonomi.kopierMed(arbeidsgiverbeløp = inntekt) }, arbeidsgiverBeløp)
            val totalArbeidsgiverrefusjon = totalArbeidsgiver(fordelingRefusjon)
            val fordelingPerson = fordel(fordelingRefusjon, total - totalArbeidsgiverrefusjon, sykepengegrunnlag - totalArbeidsgiverrefusjon, { økonomi, inntekt -> økonomi.kopierMed(personbeløp = inntekt) }, personBeløp)
            val totalPersonbeløp = totalPerson(fordelingPerson)
            val restbeløp = sykepengegrunnlag - totalArbeidsgiverrefusjon - totalPersonbeløp
            val restfordeling = restfordeling(fordelingPerson, restbeløp)
            return restfordeling.map { økonomi -> økonomi.kopierMed(er6GBegrenset = er6GBegrenset) }
        }

        private fun restfordeling(økonomiList: List<Økonomi>, grense: Inntekt): List<Økonomi> {
            // På grunn av ulike avrundinger mellom arbeidsgiverrefusjon og sykepengegrunnlag kan det oppstå
            // differanse på 1 krone som da ville vært dumt å fordele på personbeløp
            // TODO: Finn en måte å fordele denne ene kronen på arbeidsgivere
            if (grense == 1.daglig) return økonomiList.also {
                sikkerlogg.info("Restbeløp på 1 krone")
            }
            var budsjett = grense
            var list = økonomiList
            // Fordeler 1 krone per arbeidsforhold som skal ha en utbetaling uansett frem til hele potten er fordelt
            while (budsjett > INGEN) {
                list = list.map {
                    val personbeløp = personBeløp(it)
                    if (budsjett > INGEN && (arbeidsgiverBeløp(it) > INGEN || personbeløp > INGEN)) {
                        budsjett -= 1.daglig
                        it.kopierMed(personbeløp = personbeløp + 1.daglig)
                    } else {
                        it
                    }
                }
            }
            return list

        }

        private fun fordel(økonomiList: List<Økonomi>, total: Inntekt, grense: Inntekt, setter: (Økonomi, Inntekt) -> Økonomi, getter: (Økonomi) -> Inntekt): List<Økonomi> {
            return økonomiList
                .reduserOver6G(grense, total, getter)
                .fordel1Kr(grense, total, setter)
        }

        private fun List<Økonomi>.reduserOver6G(grense: Inntekt, total: Inntekt, getter: (Økonomi) -> Inntekt): List<Triple<Økonomi, Inntekt, Double>> {
            val ratio = reduksjon(grense, total)
            return map {
                val redusertBeløp = getter(it).times(ratio)
                val rundetNed = redusertBeløp.rundNedTilDaglig()
                val differanse = (redusertBeløp - rundetNed).reflection { _, _, daglig, _ -> daglig }
                Triple(it, rundetNed, differanse)
            }
        }

        // fordeler 1 kr til hver av arbeidsgiverne som har mest i differanse i beløp
        private fun List<Triple<Økonomi, Inntekt, Double>>.fordel1Kr(grense: Inntekt, total: Inntekt, setter: (Økonomi, Inntekt) -> Økonomi): List<Økonomi> {
            val maksimalt = total.coerceAtMost(grense)
            val rest = (maksimalt - map { it.second }.summer()).reflection { _, _, _, dagligInt -> dagligInt }
            val sortertEtterTap = sortedByDescending { (_, _, differanse) -> differanse }.take(rest)
            return map { (økonomi, beløp) ->
                val ekstra = if (sortertEtterTap.any { (other) -> other === økonomi }) 1.daglig else INGEN
                setter(økonomi, beløp + ekstra)
            }
        }

        private fun reduksjon(grense: Inntekt, total: Inntekt): Prosentdel {
            if (total == INGEN) return 0.prosent
            return grense ratio total
        }

        private fun total(økonomiList: List<Økonomi>, strategi: (Økonomi) -> Inntekt): Inntekt =
            økonomiList.map { strategi(it) }.summer()

        private fun totalArbeidsgiver(økonomiList: List<Økonomi>) = total(økonomiList, arbeidsgiverBeløp)

        private fun totalPerson(økonomiList: List<Økonomi>) = total(økonomiList, personBeløp)

        internal fun er6GBegrenset(økonomiList: List<Økonomi>) =
            økonomiList.any { it.er6GBegrenset() }

        fun gjenopprett(dto: ØkonomiInnDto, erAvvistDag: Boolean): Økonomi {
            return Økonomi(
                grad = Prosentdel.gjenopprett(dto.grad),
                totalGrad = Prosentdel.gjenopprett(dto.totalGrad),
                arbeidsgiverRefusjonsbeløp = Inntekt.gjenopprett(dto.arbeidsgiverRefusjonsbeløp),
                aktuellDagsinntekt = Inntekt.gjenopprett(dto.aktuellDagsinntekt),
                beregningsgrunnlag = Inntekt.gjenopprett(dto.beregningsgrunnlag),
                dekningsgrunnlag = Inntekt.gjenopprett(dto.dekningsgrunnlag),
                grunnbeløpgrense = dto.grunnbeløpgrense?.let { Inntekt.gjenopprett(it) },
                arbeidsgiverbeløp = dto.arbeidsgiverbeløp?.let { Inntekt.gjenopprett(it) },
                personbeløp = dto.personbeløp?.let { Inntekt.gjenopprett(it) },
                er6GBegrenset = dto.er6GBegrenset,
                tilstand = when {
                    dto.arbeidsgiverbeløp == null && erAvvistDag -> Økonomi.Tilstand.Låst
                    dto.arbeidsgiverbeløp == null -> Økonomi.Tilstand.HarInntekt
                    erAvvistDag -> Økonomi.Tilstand.LåstMedBeløp
                    else -> Økonomi.Tilstand.HarBeløp
                }
            )
        }
    }

    init {
        require(dekningsgrunnlag >= INGEN) { "dekningsgrunnlag kan ikke være negativ." }
    }

    fun inntekt(aktuellDagsinntekt: Inntekt, dekningsgrunnlag: Inntekt = aktuellDagsinntekt, beregningsgrunnlag: Inntekt = aktuellDagsinntekt, `6G`: Inntekt, refusjonsbeløp: Inntekt): Økonomi =
        tilstand.inntekt(
            økonomi = this,
            aktuellDagsinntekt = aktuellDagsinntekt,
            beregningsgrunnlag = beregningsgrunnlag,
            refusjonsbeløp = refusjonsbeløp,
            dekningsgrunnlag = dekningsgrunnlag,
            `6G` = `6G`
        )

    fun lås() = tilstand.lås(this)

    fun builder(builder: ØkonomiBuilder) {
        tilstand.builder(this, builder)
    }

    private fun _buildKunGrad(builder: ØkonomiBuilder) {
        /* ikke legg på flere felter - alle er enten null eller har defaultverdi */
        builder.grad(grad.toDouble())
    }

    private fun _build(builder: ØkonomiBuilder) {
        builder.grad(grad.toDouble())
            .arbeidsgiverRefusjonsbeløp(arbeidsgiverRefusjonsbeløp.reflection { _, _, daglig, _ -> daglig })
            .dekningsgrunnlag(dekningsgrunnlag.reflection { _, _, daglig, _ -> daglig })
            .totalGrad(totalGrad.toDouble())
            .aktuellDagsinntekt(aktuellDagsinntekt.reflection { _, _, daglig, _ -> daglig })
            .beregningsgrunnlag(beregningsgrunnlag.reflection { _, _, daglig, _ -> daglig })
            .arbeidsgiverbeløp(arbeidsgiverbeløp?.reflection { _, _, daglig, _ -> daglig })
            .personbeløp(personbeløp?.reflection { _, _, daglig, _ -> daglig })
            .er6GBegrenset(er6GBegrenset)
            .grunnbeløpsgrense(grunnbeløpgrense?.reflection { årlig, _, _, _ -> årlig })
            .tilstand(tilstand)
    }

    // sykdomsgrader opprettes som int, og det gir ikke mening å runde opp og på den måten "gjøre personen mer syk"
    fun <R> brukAvrundetGrad(block: (grad: Int) -> R) = block(grad.toDouble().toInt())
    // speil viser grad som nedrundet int (det rundes -ikke- oppover siden det ville gjort 19.5 % (for liten sykdomsgrad) til 20 % (ok sykdomsgrad)
    fun <R> brukTotalGrad(block: (totalGrad: Int) -> R) = block(totalGrad.toDouble().toInt())

    fun accept(visitor: ØkonomiVisitor) {
        visitor.visitAvrundetØkonomi(
            arbeidsgiverbeløp?.reflection { _, _, daglig, _ -> daglig.toInt() },
            personbeløp?.reflection { _, _, daglig, _ -> daglig.toInt() }
        )
    }

    private fun utbetalingsgrad() = tilstand.utbetalingsgrad(this)
    private fun sykdomsgrad() = tilstand.sykdomsgrad(this)

    private fun betal() = tilstand.betal(this)

    fun er6GBegrenset() = tilstand.er6GBegrenset(this)

    private fun _betal(): Økonomi {
        val total = (dekningsgrunnlag * utbetalingsgrad()).rundTilDaglig()
        val gradertArbeidsgiverRefusjonsbeløp = (arbeidsgiverRefusjonsbeløp * utbetalingsgrad()).rundTilDaglig()
        val arbeidsgiverbeløp = gradertArbeidsgiverRefusjonsbeløp.coerceAtMost(total)
        return kopierMed(
            arbeidsgiverbeløp = arbeidsgiverbeløp,
            personbeløp = (total - arbeidsgiverbeløp).coerceAtLeast(INGEN),
            tilstand = Tilstand.HarBeløp
        )
    }

    fun ikkeBetalt() = kopierMed(tilstand = Tilstand.IkkeBetalt)

    private fun kopierMed(
        grad: Prosentdel = this.grad,
        totalgrad: Prosentdel = this.totalGrad,
        arbeidsgiverRefusjonsbeløp: Inntekt = this.arbeidsgiverRefusjonsbeløp,
        aktuellDagsinntekt: Inntekt = this.aktuellDagsinntekt,
        beregningsgrunnlag: Inntekt = this.beregningsgrunnlag,
        dekningsgrunnlag: Inntekt = this.dekningsgrunnlag,
        grunnbeløpgrense: Inntekt? = this.grunnbeløpgrense,
        arbeidsgiverbeløp: Inntekt? = this.arbeidsgiverbeløp,
        personbeløp: Inntekt? = this.personbeløp,
        er6GBegrenset: Boolean? = this.er6GBegrenset,
        tilstand: Tilstand = this.tilstand,
    ) = Økonomi(
        grad = grad,
        totalGrad = totalgrad,
        arbeidsgiverRefusjonsbeløp = arbeidsgiverRefusjonsbeløp,
        aktuellDagsinntekt = aktuellDagsinntekt,
        beregningsgrunnlag = beregningsgrunnlag,
        dekningsgrunnlag = dekningsgrunnlag,
        grunnbeløpgrense = grunnbeløpgrense,
        arbeidsgiverbeløp = arbeidsgiverbeløp,
        personbeløp = personbeløp,
        er6GBegrenset = er6GBegrenset,
        tilstand = tilstand
    )

    sealed class Tilstand {

        internal open fun utbetalingsgrad(økonomi: Økonomi) = sykdomsgrad(økonomi)
        internal open fun sykdomsgrad(økonomi: Økonomi) = økonomi.grad

        internal open fun builder(økonomi: Økonomi, builder: ØkonomiBuilder) {
            økonomi._build(builder)
        }

        internal open fun inntekt(
            økonomi: Økonomi,
            aktuellDagsinntekt: Inntekt,
            beregningsgrunnlag: Inntekt,
            refusjonsbeløp: Inntekt,
            dekningsgrunnlag: Inntekt,
            `6G`: Inntekt
        ): Økonomi {
            throw IllegalStateException("Kan ikke sette inntekt i tilstand ${this::class.simpleName}")
        }

        internal open fun betal(økonomi: Økonomi): Økonomi {
            throw IllegalStateException("Kan ikke beregne utbetaling i tilstand ${this::class.simpleName}")
        }

        internal open fun er6GBegrenset(økonomi: Økonomi): Boolean {
            throw IllegalStateException("Beløp er ikke beregnet ennå")
        }

        internal open fun lås(økonomi: Økonomi): Økonomi {
            throw IllegalStateException("Kan ikke låse Økonomi i tilstand ${this::class.simpleName}")
        }

        internal object KunGrad : Tilstand() {

            override fun builder(økonomi: Økonomi, builder: ØkonomiBuilder) {
                økonomi._buildKunGrad(builder)
            }

            override fun utbetalingsgrad(økonomi: Økonomi) = throw IllegalStateException("ingen utbetalingsgrad i KunGrad")

            override fun inntekt(
                økonomi: Økonomi,
                aktuellDagsinntekt: Inntekt,
                beregningsgrunnlag: Inntekt,
                refusjonsbeløp: Inntekt,
                dekningsgrunnlag: Inntekt,
                `6G`: Inntekt
            ) = økonomi.kopierMed(
                grad = økonomi.grad,
                totalgrad = økonomi.totalGrad,
                arbeidsgiverRefusjonsbeløp = refusjonsbeløp,
                aktuellDagsinntekt = aktuellDagsinntekt,
                beregningsgrunnlag = beregningsgrunnlag,
                dekningsgrunnlag = dekningsgrunnlag,
                grunnbeløpgrense = `6G`,
                tilstand = HarInntekt
            )
        }

        internal object IkkeBetalt : Tilstand() {

            override fun lås(økonomi: Økonomi) = økonomi

            override fun utbetalingsgrad(økonomi: Økonomi) = 0.prosent

            override fun inntekt(
                økonomi: Økonomi,
                aktuellDagsinntekt: Inntekt,
                beregningsgrunnlag: Inntekt,
                refusjonsbeløp: Inntekt,
                dekningsgrunnlag: Inntekt,
                `6G`: Inntekt
            ) = økonomi.kopierMed(
                grad = økonomi.grad,
                totalgrad = økonomi.totalGrad,
                arbeidsgiverRefusjonsbeløp = refusjonsbeløp,
                aktuellDagsinntekt = aktuellDagsinntekt,
                beregningsgrunnlag = beregningsgrunnlag,
                dekningsgrunnlag = dekningsgrunnlag,
                grunnbeløpgrense = `6G`,
                tilstand = HarInntektIkkeBetalt
            )

            override fun betal(økonomi: Økonomi) = økonomi.kopierMed(
                arbeidsgiverbeløp = INGEN,
                personbeløp = INGEN,
                tilstand = HarBeløp
            )
        }

        object HarInntektIkkeBetalt : Tilstand() {
            override fun utbetalingsgrad(økonomi: Økonomi) = 0.prosent
            override fun lås(økonomi: Økonomi) = økonomi.kopierMed(tilstand = Låst)
            override fun betal(økonomi: Økonomi) = økonomi._betal()
        }

        object HarInntekt : Tilstand() {
            override fun lås(økonomi: Økonomi) = økonomi.kopierMed(tilstand = Låst)
            override fun betal(økonomi: Økonomi) = økonomi._betal()
        }

        object HarBeløp : Tilstand() {
            override fun er6GBegrenset(økonomi: Økonomi) = økonomi.er6GBegrenset!!

        }

        object Låst : Tilstand() {
            override fun utbetalingsgrad(økonomi: Økonomi) = 0.prosent
            override fun lås(økonomi: Økonomi) = økonomi
            override fun betal(økonomi: Økonomi) = økonomi.kopierMed(
                arbeidsgiverbeløp = INGEN,
                personbeløp = INGEN,
                tilstand = LåstMedBeløp
            )
        }

        object LåstMedBeløp : Tilstand() {
            override fun utbetalingsgrad(økonomi: Økonomi) = 0.prosent
            override fun lås(økonomi: Økonomi) = økonomi
            override fun er6GBegrenset(økonomi: Økonomi) = false
        }
    }

    class Builder : ØkonomiBuilder() {
        fun build() = when (tilstand) {
            is Tilstand.KunGrad -> Økonomi(grad.prosent)
            else -> Økonomi(
                grad.prosent,
                totalGrad?.prosent!!,
                arbeidsgiverRefusjonsbeløp?.daglig!!,
                aktuellDagsinntekt?.daglig!!,
                beregningsgrunnlag?.daglig!!,
                dekningsgrunnlag?.daglig!!,
                grunnbeløpgrense?.årlig,
                arbeidsgiverbeløp?.daglig,
                personbeløp?.daglig,
                er6GBegrenset,
                tilstand!!
            )
        }
    }

    fun dto() = ØkonomiUtDto(
        grad = grad.dto(),
        totalGrad = totalGrad.dto(),
        arbeidsgiverRefusjonsbeløp = arbeidsgiverRefusjonsbeløp.dto(),
        aktuellDagsinntekt = aktuellDagsinntekt.dto(),
        beregningsgrunnlag = beregningsgrunnlag.dto(),
        dekningsgrunnlag = dekningsgrunnlag.dto(),
        grunnbeløpgrense = grunnbeløpgrense?.dto(),
        arbeidsgiverbeløp = arbeidsgiverbeløp?.dto(),
        personbeløp = personbeløp?.dto(),
        er6GBegrenset = er6GBegrenset
    )
}

abstract class ØkonomiBuilder {
    protected var grad by Delegates.notNull<Double>()
    protected var arbeidsgiverRefusjonsbeløp: Double? = null
    protected var dekningsgrunnlag: Double? = null
    protected var totalGrad: Double? = null
    protected var aktuellDagsinntekt: Double? = null
    protected var beregningsgrunnlag: Double? = null
    protected var arbeidsgiverbeløp: Double? = null
    protected var personbeløp: Double? = null
    protected var er6GBegrenset: Boolean? = null
    protected var grunnbeløpgrense: Double? = null
    protected var tilstand: Økonomi.Tilstand? = null


    fun grad(grad: Double): ØkonomiBuilder = apply {
        this.grad = grad
    }

    fun tilstand(tilstand: Økonomi.Tilstand): ØkonomiBuilder = apply {
        this.tilstand = tilstand
    }

    fun grunnbeløpsgrense(grunnbeløpgrense: Double?) = apply {
        this.grunnbeløpgrense = grunnbeløpgrense
    }

    fun arbeidsgiverRefusjonsbeløp(arbeidsgiverRefusjonsbeløp: Double?) = apply {
        this.arbeidsgiverRefusjonsbeløp = arbeidsgiverRefusjonsbeløp
    }

    fun dekningsgrunnlag(dekningsgrunnlag: Double?) = apply {
        this.dekningsgrunnlag = dekningsgrunnlag
    }

    fun totalGrad(totalGrad: Double?) = apply {
        this.totalGrad = totalGrad
    }

    fun aktuellDagsinntekt(aktuellDagsinntekt: Double?) = apply {
        this.aktuellDagsinntekt = aktuellDagsinntekt
    }

    fun beregningsgrunnlag(beregningsgrunnlag: Double?) = apply {
        this.beregningsgrunnlag = beregningsgrunnlag
    }

    fun arbeidsgiverbeløp(arbeidsgiverbeløp: Double?) = apply {
        this.arbeidsgiverbeløp = arbeidsgiverbeløp
    }

    fun personbeløp(personbeløp: Double?) = apply {
        this.personbeløp = personbeløp
    }

    fun er6GBegrenset(er6GBegrenset: Boolean?) = apply {
        this.er6GBegrenset = er6GBegrenset
    }
}

fun List<Økonomi>.betal() = Økonomi.betal(this)

fun List<Økonomi>.er6GBegrenset() = Økonomi.er6GBegrenset(this)

/*
Fordi vi må vekk fra `medData` og gjøre like ting sånn nogenlunde likt.
 */
interface ØkonomiVisitor {
    fun visitAvrundetØkonomi(
        arbeidsgiverbeløp: Int?,
        personbeløp: Int?
    ) {}
}