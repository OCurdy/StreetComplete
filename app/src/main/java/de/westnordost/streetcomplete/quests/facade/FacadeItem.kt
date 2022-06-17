package de.westnordost.streetcomplete.quests.facade

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.view.image_select.Item

fun List<Facade>.toItems() = this.map { it.asItem() }

fun Facade.asItem(): Item<Facade> = when (this) {
    Facade.CEMENT_BLOCK -> Item(this, R.drawable.facade_cement_block, R.string.quest_facade_value_cement_block)
    Facade.BRICK -> Item(this, R.drawable.facade_brick, R.string.quest_facade_value_brick)
    Facade.PLASTER -> Item(this, R.drawable.facade_plaster, R.string.quest_facade_value_plaster)
    Facade.CONCRETE -> Item(this, R.drawable.facade_concrete, R.string.quest_facade_value_concrete)
    Facade.MUD -> Item(this, R.drawable.facade_mud, R.string.quest_facade_value_mud)
    Facade.STONE -> Item(this, R.drawable.facade_stone, R.string.quest_facade_value_stone)
    Facade.WOOD -> Item(this, R.drawable.facade_wood,R.string.quest_facade_value_wood)
    Facade.METAL -> Item(this, R.drawable.facade_metal, R.string.quest_facade_value_metal)
    Facade.GLASS -> Item(this, R.drawable.facade_glass, R.string.quest_facade_value_glass)
}
