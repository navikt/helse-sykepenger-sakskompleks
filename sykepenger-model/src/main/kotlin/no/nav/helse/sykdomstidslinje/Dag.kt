package no.nav.helse.sykdomstidslinje

import java.time.LocalDate
import java.util.SortedMap
import no.nav.helse.erHelg
import no.nav.helse.dto.SykdomstidslinjeDagDto
import no.nav.helse.dto.SykdomstidslinjeDagDto.AndreYtelserDto.YtelseDto
import no.nav.helse.dto.HendelseskildeDto
import no.nav.helse.hendelser.Melding
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserAap
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserDagpenger
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserForeldrepenger
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserOmsorgspenger
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserOpplaringspenger
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserPleiepenger
import no.nav.helse.person.PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO.AndreYtelserSvangerskapspenger
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.AAP
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.Dagpenger
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.Foreldrepenger
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.Omsorgspenger
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.Opplæringspenger
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.Pleiepenger
import no.nav.helse.sykdomstidslinje.Dag.AndreYtelser.AnnenYtelse.Svangerskapspenger
import no.nav.helse.hendelser.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi

internal typealias BesteStrategy = (Dag, Dag) -> Dag

sealed class Dag(
    val dato: LocalDate,
    val kilde: Hendelseskilde
) {
    private fun name() = javaClass.canonicalName.split('.').last()

    internal companion object {
        internal val default: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            if (venstre == høyre) venstre else høyre.problem(venstre)
        }

        internal val override: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            høyre
        }

        internal val sammenhengendeSykdom: BesteStrategy = { venstre: Dag, høyre: Dag ->
            require(venstre.dato == høyre.dato) { "Støtter kun sammenlikning av dager med samme dato" }
            when (venstre) {
                is Sykedag,
                is SykedagNav,
                is SykHelgedag,
                is Arbeidsgiverdag,
                is ArbeidsgiverHelgedag -> venstre
                is Feriedag,
                is Permisjonsdag -> when (høyre) {
                    is Sykedag,
                    is SykedagNav,
                    is SykHelgedag,
                    is Arbeidsgiverdag,
                    is ArbeidsgiverHelgedag -> høyre
                    else -> venstre
                }
                else -> høyre
            }
        }

        internal val replace: BesteStrategy = { venstre: Dag, høyre: Dag ->
            if (høyre is UkjentDag) venstre
            else høyre
        }

        internal fun SortedMap<LocalDate, Dag>.funksjoneltLik(other: Map<LocalDate, Dag>) =
            all { (dato, dag) -> other[dato]?.funksjoneltLik(dag) == true }

        fun gjenopprett(dag: SykdomstidslinjeDagDto): Dag {
            return when (dag) {
                is SykdomstidslinjeDagDto.AndreYtelserDto -> AndreYtelser.gjenopprett(dag)
                is SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto -> ArbeidIkkeGjenopptattDag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.ArbeidsdagDto -> Arbeidsdag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto -> ArbeidsgiverHelgedag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.ArbeidsgiverdagDto -> Arbeidsgiverdag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.FeriedagDto -> Feriedag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.ForeldetSykedagDto -> ForeldetSykedag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.FriskHelgedagDto -> FriskHelgedag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.PermisjonsdagDto -> Permisjonsdag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.ProblemDagDto -> ProblemDag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.SykHelgedagDto -> SykHelgedag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.SykedagDto -> Sykedag.gjenopprett(dag)
                is SykdomstidslinjeDagDto.SykedagNavDto -> SykedagNav.gjenopprett(dag)
                is SykdomstidslinjeDagDto.UkjentDagDto -> UkjentDag.gjenopprett(dag)
            }
        }
    }

    internal fun kommerFra(hendelse: Melding) = kilde.erAvType(hendelse)
    internal fun kommerFra(hendelse: String) = kilde.erAvType(hendelse)

    internal fun erHelg() = dato.erHelg()

    internal fun problem(other: Dag, melding: String = "Kan ikke velge mellom ${name()} fra $kilde og ${other.name()} fra ${other.kilde}."): Dag =
        ProblemDag(dato, kilde, other.kilde, melding)

    override fun equals(other: Any?) =
        other != null && this::class == other::class && this.equals(other as Dag)

    protected open fun equals(other: Dag) = this.dato == other.dato && this.kilde == other.kilde

    protected open fun funksjoneltLik(other: Dag) = this.dato == other.dato && this::class == other::class

    protected fun sammeGrad(økonomi: Økonomi, other: Økonomi) = økonomi.brukAvrundetGrad { it } == other.brukAvrundetGrad { it }

    override fun hashCode() = dato.hashCode() * 37 + kilde.hashCode() * 41 + this::class.hashCode()

    override fun toString() = "${this::class.java.simpleName} ($dato) $kilde"

    internal class UkjentDag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.UkjentDagDto(dato, kilde)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.UkjentDagDto): UkjentDag {
                return UkjentDag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class Arbeidsdag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidsdagDto(dato, kilde)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.ArbeidsdagDto): Arbeidsdag {
                return Arbeidsdag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class Arbeidsgiverdag(
        dato: LocalDate,
        val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is Arbeidsgiverdag && sammeGrad(økonomi, other.økonomi)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidsgiverdagDto(dato, kilde, økonomi.dto().grad)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.ArbeidsgiverdagDto): Arbeidsgiverdag {
                return Arbeidsgiverdag(
                    dato = dto.dato,
                    økonomi = Økonomi.sykdomsgrad(Prosentdel.gjenopprett(dto.grad)),
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class Feriedag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.FeriedagDto(dato, kilde)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.FeriedagDto): Feriedag {
                return Feriedag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class ArbeidIkkeGjenopptattDag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto(dato, kilde)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.ArbeidIkkeGjenopptattDagDto): ArbeidIkkeGjenopptattDag {
                return ArbeidIkkeGjenopptattDag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class FriskHelgedag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.FriskHelgedagDto(dato, kilde)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.FriskHelgedagDto): FriskHelgedag {
                return FriskHelgedag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class ArbeidsgiverHelgedag(
        dato: LocalDate,
        val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is ArbeidsgiverHelgedag && sammeGrad(økonomi, other.økonomi)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto(dato, kilde, økonomi.dto().grad)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.ArbeidsgiverHelgedagDto): ArbeidsgiverHelgedag {
                return ArbeidsgiverHelgedag(
                    dato = dto.dato,
                    økonomi = Økonomi.sykdomsgrad(Prosentdel.gjenopprett(dto.grad)),
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class Sykedag(
        dato: LocalDate,
        val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is Sykedag && sammeGrad(økonomi, other.økonomi)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.SykedagDto(dato, kilde, økonomi.dto().grad)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.SykedagDto): Sykedag {
                return Sykedag(
                    dato = dto.dato,
                    økonomi = Økonomi.sykdomsgrad(Prosentdel.gjenopprett(dto.grad)),
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class ForeldetSykedag(
        dato: LocalDate,
        val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is ForeldetSykedag && sammeGrad(økonomi, other.økonomi)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ForeldetSykedagDto(dato, kilde, økonomi.dto().grad)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.ForeldetSykedagDto): ForeldetSykedag {
                return ForeldetSykedag(
                    dato = dto.dato,
                    økonomi = Økonomi.sykdomsgrad(Prosentdel.gjenopprett(dto.grad)),
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class SykHelgedag(
        dato: LocalDate,
        val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is SykHelgedag && sammeGrad(økonomi, other.økonomi)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.SykHelgedagDto(dato, kilde, økonomi.dto().grad)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.SykHelgedagDto): SykHelgedag {
                return SykHelgedag(
                    dato = dto.dato,
                    økonomi = Økonomi.sykdomsgrad(Prosentdel.gjenopprett(dto.grad)),
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class SykedagNav(
        dato: LocalDate,
        val økonomi: Økonomi,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is SykedagNav && sammeGrad(økonomi, other.økonomi)
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.SykedagNavDto(dato, kilde, økonomi.dto().grad)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.SykedagNavDto): SykedagNav {
                return SykedagNav(
                    dato = dto.dato,
                    økonomi = Økonomi.sykdomsgrad(Prosentdel.gjenopprett(dto.grad)),
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class Permisjonsdag(
        dato: LocalDate,
        kilde: Hendelseskilde
    ) : Dag(dato, kilde) {
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.PermisjonsdagDto(dato, kilde)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.PermisjonsdagDto): Permisjonsdag {
                return Permisjonsdag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde)
                )
            }
        }
    }

    internal class ProblemDag(
        dato: LocalDate,
        kilde: Hendelseskilde,
        val other: Hendelseskilde,
        val melding: String
    ) : Dag(dato, kilde) {
        internal constructor(dato: LocalDate, kilde: Hendelseskilde, melding: String) : this(dato, kilde, kilde, melding)
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is ProblemDag && melding == other.melding
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.ProblemDagDto(dato, kilde, other.dto(), melding)
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.ProblemDagDto): ProblemDag {
                return ProblemDag(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde),
                    other = Hendelseskilde.gjenopprett(dto.other),
                    melding = dto.melding
                )
            }
        }
    }

    internal class AndreYtelser(
        dato: LocalDate,
        kilde: Hendelseskilde,
        val ytelse: AnnenYtelse,
    ) : Dag(dato, kilde) {
        enum class AnnenYtelse {
            Foreldrepenger, AAP, Omsorgspenger, Pleiepenger, Svangerskapspenger, Opplæringspenger, Dagpenger;

            fun dto() = when (this) {
                Foreldrepenger -> YtelseDto.Foreldrepenger
                AAP -> YtelseDto.AAP
                Omsorgspenger -> YtelseDto.Omsorgspenger
                Pleiepenger -> YtelseDto.Pleiepenger
                Svangerskapspenger -> YtelseDto.Svangerskapspenger
                Opplæringspenger -> YtelseDto.Opplæringspenger
                Dagpenger -> YtelseDto.Dagpenger
            }
        }
        internal fun tilEksternBegrunnelse(): PersonObserver.Utbetalingsdag.EksternBegrunnelseDTO {
            return when (ytelse) {
                Foreldrepenger -> AndreYtelserForeldrepenger
                AAP -> AndreYtelserAap
                Omsorgspenger -> AndreYtelserOmsorgspenger
                Pleiepenger -> AndreYtelserPleiepenger
                Svangerskapspenger -> AndreYtelserSvangerskapspenger
                Opplæringspenger -> AndreYtelserOpplaringspenger
                Dagpenger -> AndreYtelserDagpenger
            }
        }
        override fun funksjoneltLik(other: Dag) = dato == other.dato && other is AndreYtelser && ytelse == other.ytelse
        override fun dto(dato: LocalDate, kilde: HendelseskildeDto) = SykdomstidslinjeDagDto.AndreYtelserDto(dato, kilde, ytelse.dto())
        internal companion object {
            fun gjenopprett(dto: SykdomstidslinjeDagDto.AndreYtelserDto): AndreYtelser {
                return AndreYtelser(
                    dato = dto.dato,
                    kilde = Hendelseskilde.gjenopprett(dto.kilde),
                    ytelse = when (dto.ytelse) {
                        YtelseDto.Foreldrepenger -> Foreldrepenger
                        YtelseDto.AAP -> AAP
                        YtelseDto.Omsorgspenger -> Omsorgspenger
                        YtelseDto.Pleiepenger -> Pleiepenger
                        YtelseDto.Svangerskapspenger -> Svangerskapspenger
                        YtelseDto.Opplæringspenger -> Opplæringspenger
                        YtelseDto.Dagpenger -> Dagpenger
                    }
                )
            }
        }
    }

    internal fun dto() = dto(dato, kilde.dto())
    protected abstract fun dto(dato: LocalDate, kilde: HendelseskildeDto): SykdomstidslinjeDagDto
}
