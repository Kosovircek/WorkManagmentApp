package si.sluzbataski.data.entity

/**
 * Nujnost taska.
 *
 * Uporabnik izbere natanko eno od petih možnosti. Možnost [DATUM] pomeni, da ima task
 * določen rok (datum, opcijsko tudi uro) – tak task se vpiše v Google koledar in je v
 * seznamih vedno NA VRHU.
 *
 * @property rank      vrstni red pri sortiranju (manjša številka = višje na seznamu)
 * @property label     slovensko ime, prikazano uporabniku
 * @property colorArgb barva, s katero je task označen v seznamih in widgetih
 */
enum class Urgency(val rank: Int, val label: String, val colorArgb: Long) {
    DATUM(0, "Datum", 0xFF1E88E5),
    URGENTNO(1, "Urgentno", 0xFFD32F2F),
    NUJNO(2, "Nujno", 0xFFF57C00),
    NORMALNO(3, "Normalno", 0xFF43A047),
    KO_BO_CAS(4, "Ko bo čas", 0xFF9E9E9E);

    companion object {
        /** Nujnosti brez datuma, urejene od najmanj do najbolj nujne (za premikanje gor/dol). */
        val LADDER = listOf(KO_BO_CAS, NORMALNO, NUJNO, URGENTNO)

        /** Varno pretvori ime iz baze/CSV nazaj v enum (neznano ime -> NORMALNO). */
        fun fromName(name: String?): Urgency = entries.firstOrNull { it.name == name } ?: NORMALNO
    }
}
