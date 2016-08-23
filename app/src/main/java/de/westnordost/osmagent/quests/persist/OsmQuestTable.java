package de.westnordost.osmagent.quests.persist;

public class OsmQuestTable
{
	public static final String NAME = "osm_quests";

	public static final String NAME_MERGED_VIEW = "osm_quests_full";

	public static class Columns
	{
		public static final String
				QUEST_ID = "quest_id",
				QUEST_TYPE = "quest_type",
				ELEMENT_ID = "element_id",
				ELEMENT_TYPE  = "element_type",
				QUEST_STATUS = "quest_status",
				TAG_CHANGES = "tag_changes";
	}
}
