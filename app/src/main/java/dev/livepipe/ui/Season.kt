package dev.livepipe.ui

import java.time.LocalDate

/**
 * Japan's 72 micro-seasons (kō), in English: the Wa theme names today's under the app title. Start dates are the
 * usual calendar approximations (they drift a day either way by year). Same list as TO-DO by Angra.
 */
data class Ko(val md: Int, val english: String, val sekki: String)

private val KO = listOf(
    Ko(105, "Parsley flourishes", "Lesser cold"),
    Ko(110, "Springs thaw", "Lesser cold"),
    Ko(115, "Pheasants start to call", "Lesser cold"),
    Ko(120, "Butterburs bud", "Greater cold"),
    Ko(125, "Ice thickens on streams", "Greater cold"),
    Ko(130, "Hens start laying", "Greater cold"),
    Ko(204, "East wind melts the ice", "Spring begins"),
    Ko(209, "Bush warblers sing", "Spring begins"),
    Ko(214, "Fish rise from the ice", "Spring begins"),
    Ko(219, "Rain moistens the soil", "Rainwater"),
    Ko(224, "Mist starts to linger", "Rainwater"),
    Ko(301, "Grass sprouts, trees bud", "Rainwater"),
    Ko(306, "Hibernating insects surface", "Insects awaken"),
    Ko(311, "First peach blossoms", "Insects awaken"),
    Ko(316, "Caterpillars become butterflies", "Insects awaken"),
    Ko(321, "Sparrows start to nest", "Spring equinox"),
    Ko(326, "First cherry blossoms", "Spring equinox"),
    Ko(331, "Distant thunder", "Spring equinox"),
    Ko(405, "Swallows return", "Pure and clear"),
    Ko(410, "Wild geese fly north", "Pure and clear"),
    Ko(415, "First rainbows", "Pure and clear"),
    Ko(420, "First reeds sprout", "Grain rain"),
    Ko(425, "Last frost, rice seedlings grow", "Grain rain"),
    Ko(430, "Peonies bloom", "Grain rain"),
    Ko(505, "Frogs start singing", "Summer begins"),
    Ko(510, "Worms surface", "Summer begins"),
    Ko(515, "Bamboo shoots sprout", "Summer begins"),
    Ko(521, "Silkworms feast on mulberry", "Lesser ripening"),
    Ko(526, "Safflowers bloom", "Lesser ripening"),
    Ko(531, "Wheat ripens", "Lesser ripening"),
    Ko(606, "Praying mantises hatch", "Grain in ear"),
    Ko(611, "Fireflies rise from the grass", "Grain in ear"),
    Ko(616, "Plums turn yellow", "Grain in ear"),
    Ko(621, "Self-heal withers", "Summer solstice"),
    Ko(627, "Irises bloom", "Summer solstice"),
    Ko(702, "Crow-dipper sprouts", "Summer solstice"),
    Ko(707, "Warm winds blow", "Lesser heat"),
    Ko(712, "First lotus blossoms", "Lesser heat"),
    Ko(717, "Hawks learn to fly", "Lesser heat"),
    Ko(723, "Paulownia trees seed", "Greater heat"),
    Ko(728, "Earth is damp, air is humid", "Greater heat"),
    Ko(802, "Great rains sometimes fall", "Greater heat"),
    Ko(807, "Cool winds blow", "Autumn begins"),
    Ko(813, "Evening cicadas sing", "Autumn begins"),
    Ko(818, "Thick fog descends", "Autumn begins"),
    Ko(823, "Cotton flowers open", "Heat eases"),
    Ko(828, "Heat starts to die down", "Heat eases"),
    Ko(902, "Rice ripens", "Heat eases"),
    Ko(908, "Dew glistens white on grass", "White dew"),
    Ko(913, "Wagtails sing", "White dew"),
    Ko(918, "Swallows leave", "White dew"),
    Ko(923, "Thunder ceases", "Autumn equinox"),
    Ko(928, "Insects hole up", "Autumn equinox"),
    Ko(1003, "Farmers drain the fields", "Autumn equinox"),
    Ko(1008, "Wild geese return", "Cold dew"),
    Ko(1013, "Chrysanthemums bloom", "Cold dew"),
    Ko(1018, "Crickets chirp by the door", "Cold dew"),
    Ko(1023, "First frost", "Frost falls"),
    Ko(1028, "Light rains sometimes fall", "Frost falls"),
    Ko(1102, "Maple and ivy turn yellow", "Frost falls"),
    Ko(1107, "Camellias bloom", "Winter begins"),
    Ko(1112, "Land starts to freeze", "Winter begins"),
    Ko(1117, "Daffodils bloom", "Winter begins"),
    Ko(1122, "Rainbows hide", "Lesser snow"),
    Ko(1127, "North wind strips the leaves", "Lesser snow"),
    Ko(1202, "Tachibana citrus turns yellow", "Lesser snow"),
    Ko(1207, "Cold sets in, winter begins", "Greater snow"),
    Ko(1212, "Bears start hibernating", "Greater snow"),
    Ko(1216, "Salmon gather upstream", "Greater snow"),
    Ko(1221, "Self-heal sprouts", "Winter solstice"),
    Ko(1226, "Deer shed antlers", "Winter solstice"),
    Ko(1231, "Wheat sprouts under snow", "Winter solstice"),
)

/** The micro-season a date falls in (1 to 4 January still belong to the one that began on 31 December). */
fun microSeason(date: LocalDate): Ko {
    val md = date.monthValue * 100 + date.dayOfMonth
    return KO.lastOrNull { it.md <= md } ?: KO.last()
}

/** 0 spring (Mar–May), 1 summer (Jun–Aug), 2 autumn (Sep–Nov), 3 winter (Dec–Feb). */
fun seasonOf(date: LocalDate) = ((date.monthValue + 9) % 12) / 3
