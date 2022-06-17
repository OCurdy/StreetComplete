package de.westnordost.streetcomplete.quests.facade

import androidx.appcompat.app.AlertDialog
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AImageListQuestAnswerFragment
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.view.image_select.Item


class AddFacadeBuildingForm : AImageListQuestAnswerFragment<Facade, FacadeAnswer>() {
    override val otherAnswers = listOf(
        AnswerItem(R.string.quest_buildingFacade_answer_multiple_material) { showMultipleFacadeHint() },
    )
    override val items: List<Item<Facade>>
        get() = FACADE.toItems()

    override val itemsPerRow = 3

    override fun onClickOk(selectedItems: List<Facade>) {
        val value = selectedItems.single()

        applyAnswer(FacadeAnswer(value))
    }
    private fun showMultipleFacadeHint() {
        activity?.let {
            AlertDialog.Builder(it)
                .setMessage(R.string.quest_buildingFacade_answer_multiple_material_description)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
