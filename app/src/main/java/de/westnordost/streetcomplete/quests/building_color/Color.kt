package de.westnordost.streetcomplete.quests.building_color


enum class Color (val osmValue: String){
    WHITE("#FFFFFF"),
    LIGHT_GREY("#D3D3D3"),
    GREY("#808080"),
    MAROON("#A79876"),
    OCHRE("#CC7722"),
    BISQUE("#FFE4C4"),
    BEIGE("#FFD966"),
    BLUE("#6699FF"),
    BLACK("#000000")
}

val FACADE = listOf(
    Color.WHITE, Color.LIGHT_GREY, Color.GREY, Color.MAROON, Color.OCHRE, Color.BISQUE, Color.BEIGE, Color.BLUE, Color.BLACK
)
