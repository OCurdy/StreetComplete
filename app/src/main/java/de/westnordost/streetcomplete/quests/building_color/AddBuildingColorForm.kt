package de.westnordost.streetcomplete.quests.building_color

import androidx.appcompat.app.AlertDialog
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AImageListQuestAnswerFragment
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.view.image_select.Item

class AddBuildingColorForm : AImageListQuestAnswerFragment<Color, ColorAnswer>(){
    override val otherAnswers = listOf(
        AnswerItem(R.string.quest_buildingColor_answer_multiple_color) { showMultipleColorsHint() },
    )
    override val items: List<Item<Color>>
        get() = FACADE.toItems()

    override val itemsPerRow = 3

    override fun onClickOk(selectedItems: List<Color>) {
        val value = selectedItems.single()

        applyAnswer(ColorAnswer(value))
    }
    private fun showMultipleColorsHint() {
        activity?.let {
            AlertDialog.Builder(it)
                .setMessage(R.string.quest_building_answer_multiple_color_description)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
