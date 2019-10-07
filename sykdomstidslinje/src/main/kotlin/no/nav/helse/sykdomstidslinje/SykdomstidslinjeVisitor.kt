package no.nav.helse.sykdomstidslinje

interface SykdomstidslinjeVisitor {
    fun visitArbeidsdag(arbeidsdag: Arbeidsdag) {}
    fun visitFeriedag(feriedag: Feriedag) {}
    fun visitHelgedag(helgedag: Helgedag) {}
    fun visitSykedag(sykedag: Sykedag) {}
    fun visitEgenmeldingsdag(egenmeldingsdag: Egenmeldingsdag) {}
    fun visitSykHelgedag(sykHelgedag: SykHelgedag) {}
    fun visitUtenlandsdag(utenlandsdag: Utenlandsdag) {}
    fun visitUbestemt(ubestemtdag: Ubestemtdag) {}
    fun visitStudiedag(studiedag: Studiedag) {}
    fun preVisitComposite(compositeSykdomstidslinje: CompositeSykdomstidslinje) {}
    fun postVisitComposite(compositeSykdomstidslinje: CompositeSykdomstidslinje) {}
}
