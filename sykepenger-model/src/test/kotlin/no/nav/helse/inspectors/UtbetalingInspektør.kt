package no.nav.helse.inspectors

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.hendelser.Periode
import no.nav.helse.utbetalingslinjer.UtbetalingVisitor
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingtype
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Økonomi
import kotlin.properties.Delegates

val Utbetaling.inspektør get() = UtbetalingInspektør(this)

class UtbetalingInspektør(utbetaling: Utbetaling) : UtbetalingVisitor {
    lateinit var utbetalingId: UUID
        private set
    lateinit var korrelasjonsId: UUID
        private set
    lateinit var periode: Periode
        private set
    lateinit var tilstand: Utbetalingstatus
        private set
    lateinit var arbeidsgiverOppdrag: Oppdrag
        private set
    lateinit var personOppdrag: Oppdrag
        private set
    private val dager = mutableListOf<Utbetalingsdag>()
    val utbetalingstidslinje: Utbetalingstidslinje get() = Utbetalingstidslinje(dager.toList())
    var nettobeløp by Delegates.notNull<Int>()
        private set
    private lateinit var status: Utbetalingstatus
    lateinit var type: Utbetalingtype
        private set
    var forbrukteSykedager by Delegates.notNull<Int>()
        private set
    var gjenståendeSykedager by Delegates.notNull<Int>()
        private set
    lateinit var maksdato: LocalDate
        private set
    var avstemmingsnøkkel: Long? = null
    val erUbetalt get() = status == Utbetalingstatus.IKKE_UTBETALT
    val erForkastet get() = status == Utbetalingstatus.FORKASTET
    val erEtterutbetaling get() = type == Utbetalingtype.ETTERUTBETALING
    val erAnnullering get() = type == Utbetalingtype.ANNULLERING
    val erUtbetalt get() = status == Utbetalingstatus.ANNULLERT || status == Utbetalingstatus.UTBETALT

    init {
        utbetaling.accept(this)
    }

    override fun preVisitUtbetaling(
        utbetaling: Utbetaling,
        id: UUID,
        korrelasjonsId: UUID,
        type: Utbetalingtype,
        utbetalingstatus: Utbetalingstatus,
        periode: Periode,
        tidsstempel: LocalDateTime,
        oppdatert: LocalDateTime,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        maksdato: LocalDate,
        forbrukteSykedager: Int?,
        gjenståendeSykedager: Int?,
        stønadsdager: Int,
        beregningId: UUID,
        overføringstidspunkt: LocalDateTime?,
        avsluttet: LocalDateTime?,
        avstemmingsnøkkel: Long?,
        annulleringer: Set<UUID>
    ) {
        utbetalingId = id
        this.periode = periode
        this.korrelasjonsId = korrelasjonsId
        this.tilstand = utbetalingstatus
        this.type = type
        this.status = utbetalingstatus
        this.avstemmingsnøkkel = avstemmingsnøkkel
        this.nettobeløp = arbeidsgiverNettoBeløp + personNettoBeløp
        this.forbrukteSykedager = forbrukteSykedager ?: -1
        this.gjenståendeSykedager = gjenståendeSykedager ?: -1
        this.maksdato = maksdato
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodeDag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.NavDag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.Arbeidsdag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.Fridag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.AvvistDag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.ForeldetDag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun visit(dag: Utbetalingsdag.UkjentDag, dato: LocalDate, økonomi: Økonomi) {
        dager.add(dag)
    }

    override fun preVisitArbeidsgiverOppdrag(oppdrag: Oppdrag) {
        this.arbeidsgiverOppdrag = oppdrag
    }

    override fun preVisitPersonOppdrag(oppdrag: Oppdrag) {
        this.personOppdrag = oppdrag
    }
}
