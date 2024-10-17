package no.nav.helse.person

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.Personidentifikator
import no.nav.helse.desember
import no.nav.helse.dsl.SubsumsjonsListLog
import no.nav.helse.dsl.lagStandardInntekterForOpptjeningsvurdering
import no.nav.helse.etterlevelse.BehandlingSubsumsjonslogg
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Ledd.Companion.ledd
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_2
import no.nav.helse.etterlevelse.Subsumsjonskontekst
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.hendelser.til
import no.nav.helse.inntektsgrunnlag
import no.nav.helse.inspectors.GrunnlagsdataInspektør
import no.nav.helse.inspectors.SubsumsjonInspektør
import no.nav.helse.inspectors.Vilkårgrunnlagsinspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.person.Opptjening.ArbeidsgiverOpptjeningsgrunnlag.Arbeidsforhold
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement.Companion.skjæringstidspunktperioder
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.sykepengegrunnlag
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VilkårsgrunnlagHistorikkTest {
    private lateinit var historikk: VilkårsgrunnlagHistorikk
    private val inspektør get() = Vilkårgrunnlagsinspektør(historikk.view())
    private val subsumsjonslogg = SubsumsjonsListLog()
    private val jurist = BehandlingSubsumsjonslogg(subsumsjonslogg, listOf(
        Subsumsjonskontekst(KontekstType.Fødselsnummer, "fnr"),
        Subsumsjonskontekst(KontekstType.Organisasjonsnummer, "orgnr"),
        Subsumsjonskontekst(KontekstType.Vedtaksperiode, "${UUID.randomUUID()}"),
    ))

    companion object {
        private const val ORGNR = "123456789"
        private val arbeidsforhold = listOf(Vilkårsgrunnlag.Arbeidsforhold(ORGNR, 1.desember(2017), type = Arbeidsforholdtype.ORDINÆRT))
        private val arbeidsforholdFraHistorikk = listOf(
            Opptjening.ArbeidsgiverOpptjeningsgrunnlag(ORGNR, listOf(Arbeidsforhold(1.desember(2017), null, false)))
        )
    }

    @BeforeEach
    fun setup() {
        historikk = VilkårsgrunnlagHistorikk()
    }

    @Test
    fun `lager perioder for vilkårsgrunnlagene`() {
        val grunnlagMedSkjæringstidspunkt = { skjæringstidspunkt: LocalDate ->
            val inntekt = 31000.månedlig
            VilkårsgrunnlagHistorikk.Grunnlagsdata(
                skjæringstidspunkt = skjæringstidspunkt,
                inntektsgrunnlag = inntekt.inntektsgrunnlag(ORGNR),
                opptjening = Opptjening.nyOpptjening(arbeidsforholdFraHistorikk, skjæringstidspunkt, true),
                medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
                vurdertOk = true,
                meldingsreferanseId = UUID.randomUUID(),
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }
        val grunnlag1Januar = grunnlagMedSkjæringstidspunkt(1.januar)
        val grunnlag1Februar = grunnlagMedSkjæringstidspunkt(1.februar)

        assertEquals(1.januar til LocalDate.MAX, skjæringstidspunktperioder(listOf(grunnlag1Januar)).single())
        skjæringstidspunktperioder(listOf(grunnlag1Januar, grunnlag1Februar)).also { resultat ->
            assertEquals(listOf(januar, 1.februar til LocalDate.MAX), resultat)
        }
        skjæringstidspunktperioder(listOf(grunnlag1Februar, grunnlag1Januar)).also { resultat ->
            assertEquals(listOf(januar, 1.februar til LocalDate.MAX), resultat)
        }
    }

    @Test
    fun `fjerner vilkårsgrunnlag som ikke gjelder lengre`() {
        val inntekt = 21000.månedlig
        val gammeltSkjæringstidspunkt = 10.januar
        val nyttSkjæringstidspunkt = 1.januar

        historikk.lagre(VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = gammeltSkjæringstidspunkt,
            inntektsgrunnlag = inntekt.inntektsgrunnlag(ORGNR),
            opptjening = Opptjening.nyOpptjening(arbeidsforholdFraHistorikk, gammeltSkjæringstidspunkt, true),
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            vurdertOk = true,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        ))

        assertEquals(1, historikk.inspektør.vilkårsgrunnlagTeller.size)
        historikk.oppdaterHistorikk(Aktivitetslogg(), setOf(nyttSkjæringstidspunkt))

        assertEquals(2, historikk.inspektør.vilkårsgrunnlagTeller.size)
        assertEquals(0, historikk.inspektør.vilkårsgrunnlagTeller[0]) { "det siste innslaget skal være tomt" }
        assertEquals(1, historikk.inspektør.vilkårsgrunnlagTeller[1])
        assertNull(historikk.vilkårsgrunnlagFor(gammeltSkjæringstidspunkt)) { "skal ikke beholde vilkårsgrunnlag for skjæringstidspunkter som ikke finnes" }
        assertNull(historikk.vilkårsgrunnlagFor(nyttSkjæringstidspunkt)) { "skal ikke ha vilkårsgrunnlag for skjæringstidspunkt som ikke er vilkårsprøvd" }
    }

    @Test
    fun `setter inntekt på økonomi`() {
        val inntekt = 21000.månedlig
        val grunnlag = VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = 1.januar,
            inntektsgrunnlag = inntekt.inntektsgrunnlag(ORGNR),
            opptjening = Opptjening.nyOpptjening(arbeidsforholdFraHistorikk, 1.januar, true),
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            vurdertOk = true,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        )
        val økonomi: Økonomi = grunnlag.faktaavklarteInntekter().inntekterForArbeidsgiver(ORGNR)!!.medInntektHvisFinnes(1.januar, Økonomi.ikkeBetalt(), NormalArbeidstaker, Beløpstidslinje())
        assertEquals(inntekt, økonomi.inspektør.aktuellDagsinntekt)
    }

    @Test
    fun `setter inntekt på økonomi om vurdering ikke er ok`() {
        val inntekt = 21000.månedlig
        val grunnlagsdata = VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = 1.januar,
            inntektsgrunnlag = inntekt.inntektsgrunnlag(ORGNR),
            opptjening = Opptjening.nyOpptjening(arbeidsforholdFraHistorikk, 1.januar, true),
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            vurdertOk = false,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        )
        val økonomi: Økonomi = grunnlagsdata.faktaavklarteInntekter().inntekterForArbeidsgiver(ORGNR)!!.medInntektHvisFinnes(1.januar, Økonomi.ikkeBetalt(), NormalArbeidstaker, Beløpstidslinje())
        assertEquals(inntekt, økonomi.inspektør.aktuellDagsinntekt)
    }

    @Test
    fun `feiler dersom inntekt ikke finnes for orgnr`() {
        val inntekt = 21000.månedlig
        val grunnlag = VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = 1.januar,
            inntektsgrunnlag = inntekt.inntektsgrunnlag("et annet orgnr"),
            opptjening = Opptjening.nyOpptjening(arbeidsforholdFraHistorikk, 1.januar, true),
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            vurdertOk = true,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        )
        val resultat = grunnlag.faktaavklarteInntekter().inntekterForArbeidsgiver(ORGNR)?.medInntektHvisFinnes(31.desember(2017), Økonomi.ikkeBetalt(), NormalArbeidstaker, Beløpstidslinje())
        assertNull(resultat)
    }

    @Test
    fun `korrekt antall innslag i vilkårsgrunnlagshistorikken ved én vilkårsprøving`() {
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )
        historikk.lagre(vilkårsgrunnlag.grunnlagsdata())
        assertNotNull(historikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør = GrunnlagsdataInspektør(historikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertTrue(grunnlagsdataInspektør.vurdertOk)
        assertEquals(1, inspektør.vilkårsgrunnlagTeller[0])
    }

    @Test
    fun `Registrerer subsumsjoner ved validering av vilkårsgrunnlag`() {
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )

        vilkårsgrunnlag.valider(10000.månedlig.sykepengegrunnlag, jurist)
        SubsumsjonInspektør(subsumsjonslogg).assertVurdert(paragraf = PARAGRAF_8_2, ledd = 1.ledd, versjon = 12.juni(2020))
    }

    @Test
    fun `ny vilkårsprøving på samme skjæringstidspunkt overskriver gammel vilkårsprøving - medfører nytt innslag`() {
        val arbeidsforhold = arbeidsforhold
        val vilkårsgrunnlag1 = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        val vilkårsgrunnlag2 = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Nei),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag1.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )
        vilkårsgrunnlag2.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )

        historikk.lagre(vilkårsgrunnlag1.grunnlagsdata())
        assertNotNull(historikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør1 = GrunnlagsdataInspektør(historikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertTrue(grunnlagsdataInspektør1.vurdertOk)

        historikk.lagre(vilkårsgrunnlag2.grunnlagsdata())
        assertNotNull(historikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør2 = GrunnlagsdataInspektør(historikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertFalse(grunnlagsdataInspektør2.vurdertOk)

        assertEquals(1, inspektør.vilkårsgrunnlagTeller[0])
        assertEquals(1, inspektør.vilkårsgrunnlagTeller[1])
    }

    @Test
    fun `vilkårsprøving på to ulike skjæringstidspunkt medfører to innslag der siste innslag har vilkårsprøving for begge skjæringstidspunktene`() {
        val vilkårsgrunnlag1 = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        val vilkårsgrunnlag2 = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 2.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )

        vilkårsgrunnlag1.valider(10000.månedlig.sykepengegrunnlag, jurist)
        vilkårsgrunnlag2.valider(10000.månedlig.sykepengegrunnlag, jurist)
        historikk.lagre(vilkårsgrunnlag1.grunnlagsdata())
        historikk.lagre(vilkårsgrunnlag2.grunnlagsdata())
        assertEquals(1, inspektør.vilkårsgrunnlagTeller[1])
        assertEquals(2, inspektør.vilkårsgrunnlagTeller[0])
    }

    @Test
    fun `Finner vilkårsgrunnlag for skjæringstidspunkt - ok`() {
        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag.grunnlagsdata())
        assertNotNull(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør = GrunnlagsdataInspektør(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertTrue(grunnlagsdataInspektør.vurdertOk)
    }

    @Test
    fun `Finner vilkårsgrunnlag for skjæringstidspunkt - ikke ok`() {
        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Nei),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag.grunnlagsdata())
        assertNotNull(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør = GrunnlagsdataInspektør(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertFalse(grunnlagsdataInspektør.vurdertOk)
    }


    @Test
    fun `Avviser kun utbetalingsdager som har likt skjæringstidspunkt som et vilkårsgrunnlag som ikke er ok`() {
        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()
        val vilkårsgrunnlag1 = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Nei),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag1.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )
        val vilkårsgrunnlag2 = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag2.valider(
            10000.månedlig.sykepengegrunnlag,
            jurist
        )
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag1.grunnlagsdata())
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag2.grunnlagsdata())
        val utbetalingstidslinjeMedNavDager = tidslinjeOf(16.AP, 10.NAV)
        val resultat = vilkårsgrunnlagHistorikk.avvisInngangsvilkår(listOf(utbetalingstidslinjeMedNavDager), 1.januar til 1.januar, jurist).single()
        assertEquals(8, resultat.filterIsInstance<Utbetalingsdag.NavDag>().size)
    }

    @Test
    fun `Avslår vilkår for minimum inntekt med riktig begrunnelse for personer under 67 år`() {
        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = Personidentifikator("20043769969"),
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag.valider(
            10.månedlig.sykepengegrunnlag,
            jurist
        )
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag.grunnlagsdata())
        assertNotNull(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør = GrunnlagsdataInspektør(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertFalse(grunnlagsdataInspektør.vurdertOk)
        val utbetalingstidslinjeMedNavDager = tidslinjeOf(16.AP, 10.NAV)
        val resultat = vilkårsgrunnlagHistorikk.avvisInngangsvilkår(listOf(utbetalingstidslinjeMedNavDager), 1.januar til 1.januar, jurist).single()
        resultat.filterIsInstance<Utbetalingsdag.AvvistDag>().let { avvisteDager ->
            assertEquals(8, avvisteDager.size)
            avvisteDager.forEach {
                assertEquals(1, it.begrunnelser.size)
                assertEquals(Begrunnelse.MinimumInntekt, it.begrunnelser.first())
            }
        }
    }

    @Test
    fun `Avslår vilkår for minimum inntekt med riktig begrunnelse for dem mellom 67 og 70`() {
        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()
        val fødselsnummer = Personidentifikator("01015036963")
        val fødselsdato = 1.januar(1950)
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID().toString(),
            skjæringstidspunkt = 1.januar,
            aktørId = "AKTØR_ID",
            personidentifikator = fødselsnummer,
            orgnummer = "ORGNUMMER",
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList()),
            inntekterForOpptjeningsvurdering = lagStandardInntekterForOpptjeningsvurdering("ORGNUMMER", INGEN, 1.januar),
            arbeidsforhold = arbeidsforhold
        )
        vilkårsgrunnlag.valider(
            10.månedlig.inntektsgrunnlag(fødselsdato.alder),
            jurist
        )
        vilkårsgrunnlagHistorikk.lagre(vilkårsgrunnlag.grunnlagsdata())
        assertNotNull(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar))
        val grunnlagsdataInspektør = GrunnlagsdataInspektør(vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(1.januar)!!.view())
        assertFalse(grunnlagsdataInspektør.vurdertOk)
        val utbetalingstidslinjeMedNavDager = tidslinjeOf(16.AP, 10.NAV)
        val resultat = vilkårsgrunnlagHistorikk.avvisInngangsvilkår(listOf(utbetalingstidslinjeMedNavDager), 1.januar til 1.januar, jurist).single()

        resultat.filterIsInstance<Utbetalingsdag.AvvistDag>().let { avvisteDager ->
            assertEquals(8, avvisteDager.size)
            avvisteDager.forEach {
                assertEquals(1, it.begrunnelser.size)
                assertEquals(Begrunnelse.MinimumInntektOver67, it.begrunnelser.first())
            }
        }
    }

    @Test
    fun `equals av InfotrygdVilkårsgrunnlag`() {
        val sykepengegrunnlag = 25000.månedlig.sykepengegrunnlag
        val element1 = VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag(
            skjæringstidspunkt = 1.januar,
            inntektsgrunnlag = sykepengegrunnlag
        )
        assertEquals(element1, element1)
        assertEquals(
            element1, VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = 1.januar,
                inntektsgrunnlag = sykepengegrunnlag
            )
        )
        assertNotEquals(
            element1, VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = 2.januar,
                inntektsgrunnlag = sykepengegrunnlag
            )
        )
        assertNotEquals(
            element1, VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = 5.februar,
                inntektsgrunnlag = 25000.månedlig.sykepengegrunnlag
            )
        )
        assertNotEquals(
            element1, VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = 1.januar,
                inntektsgrunnlag = 30900.månedlig.sykepengegrunnlag
            )
        )
    }
}
