package de.westnordost.streetcomplete.quests.building_color

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.Tags
import de.westnordost.streetcomplete.data.user.achievements.QuestTypeAchievement


class AddBuildingColor : OsmFilterQuestType<ColorAnswer> () {
    override val elementFilter = """
        ways, relations with
         building ~ ${BUILDINGS_WITH_LEVELS.joinToString("|")}
         and !building:colour
         and !man_made
         and location != underground
         and ruins != yes
    """
    override val changesetComment = "Add outer colour of the building facade"
    override val wikiLink = "Key:building:colour"
    override val icon = R.drawable.ic_quest_building_color
    override val questTypeAchievements = listOf(QuestTypeAchievement.BUILDING)

    override fun getTitle(tags: Map<String, String>) = R.string.quest_color_title

    override fun createForm() = AddBuildingColorForm ()

    override fun applyAnswerTo(answer: ColorAnswer, tags: Tags, timestampEdited: Long) {
        answer.applyTo(tags, "building:colour")
    }
}

private val BUILDINGS_WITH_LEVELS = arrayOf(
    "yes","house", "residential", "apartments", "detached", "terrace", "dormitory", "semi",
    "semidetached_house", "bungalow", "school", "civic", "college", "university", "public",
    "hospital", "kindergarten", "transportation", "train_station", "hotel", "retail",
    "commercial", "office", "manufacture", "parking", "farm", "farm_auxiliary",
    "cabin"
)
