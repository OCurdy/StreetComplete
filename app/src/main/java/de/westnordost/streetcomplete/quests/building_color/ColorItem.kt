package de.westnordost.streetcomplete.quests.building_color

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.view.image_select.Item

fun List<Color>.toItems() = this.map { it.asItem() }

fun Color.asItem(): Item<Color> = when (this) {
    Color.WHITE -> Item(this, R.drawable.white)
    Color.LIGHT_GREY -> Item(this, R.drawable.light_grey)
    Color.GREY -> Item(this, R.drawable.grey)
    Color.MAROON -> Item(this, R.drawable.light_maroon)
    Color.OCHRE -> Item(this, R.drawable.ochre)
    Color.BISQUE -> Item(this, R.drawable.bisque)
    Color.BLUE -> Item(this, R.drawable.blue)
    Color.BEIGE -> Item(this, R.drawable.beige)
    Color.BLACK -> Item(this, R.drawable.black)
}
