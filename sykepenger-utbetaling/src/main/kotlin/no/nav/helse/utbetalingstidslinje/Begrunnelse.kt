package no.nav.helse.utbetalingstidslinje

sealed class Begrunnelse {

    fun skalAvvises(utbetalingsdag: Utbetalingsdag) = utbetalingsdag is Utbetalingsdag.AvvistDag || utbetalingsdag is Utbetalingsdag.NavDag || utbetalingsdag is Utbetalingsdag.ArbeidsgiverperiodedagNav

    object SykepengedagerOppbrukt : Begrunnelse()
    object SykepengedagerOppbruktOver67 : Begrunnelse()
    object MinimumInntekt : Begrunnelse()
    object MinimumInntektOver67 : Begrunnelse()
    object EgenmeldingUtenforArbeidsgiverperiode : Begrunnelse()
    object AndreYtelserForeldrepenger: Begrunnelse()
    object AndreYtelserAap: Begrunnelse()
    object AndreYtelserOmsorgspenger: Begrunnelse()
    object AndreYtelserPleiepenger: Begrunnelse()
    object AndreYtelserSvangerskapspenger: Begrunnelse()
    object AndreYtelserOpplaringspenger: Begrunnelse()
    object AndreYtelserDagpenger: Begrunnelse()
    object MinimumSykdomsgrad : Begrunnelse()
    object EtterDødsdato : Begrunnelse()
    object Over70 : Begrunnelse()
    object ManglerOpptjening : Begrunnelse()
    object ManglerMedlemskap : Begrunnelse()
    object NyVilkårsprøvingNødvendig : Begrunnelse()

}