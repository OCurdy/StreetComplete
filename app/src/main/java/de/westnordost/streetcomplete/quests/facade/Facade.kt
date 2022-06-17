package de.westnordost.streetcomplete.quests.facade

enum class Facade(val osmValue: String) {
    CEMENT_BLOCK("cement_block"),
    BRICK("brick"),
    PLASTER("plaster"),
    CONCRETE("concrete"),
    MUD("mud"),
    STONE("stone"),
    WOOD ("wood"),
    METAL ("metal"),
    GLASS ("glass")

}

val FACADE = listOf(
    Facade.CEMENT_BLOCK,Facade.PLASTER, Facade.CONCRETE, Facade.BRICK,Facade.MUD, Facade.STONE, Facade.WOOD, Facade.METAL, Facade.GLASS
)

