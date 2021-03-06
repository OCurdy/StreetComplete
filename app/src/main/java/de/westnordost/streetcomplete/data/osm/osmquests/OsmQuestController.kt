package de.westnordost.streetcomplete.data.osm.osmquests

import android.util.Log
import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.countryboundaries.intersects
import de.westnordost.countryboundaries.isInAny
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.Element
import de.westnordost.osmapi.map.data.LatLon
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.notes.Note
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.ktx.format
import de.westnordost.streetcomplete.util.contains
import de.westnordost.streetcomplete.util.enclosingBoundingBox
import de.westnordost.streetcomplete.util.enlargedBy
import de.westnordost.streetcomplete.util.measuredLength
import kotlinx.coroutines.*
import java.lang.System.currentTimeMillis
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.FutureTask
import javax.inject.Inject
import javax.inject.Singleton

/** Controller for managing OsmQuests. Takes care of persisting OsmQuest objects and notifying
 *  listeners about changes */
@Singleton class OsmQuestController @Inject internal constructor(
    private val db: OsmQuestDao,
    private val hiddenDB: OsmQuestsHiddenDao,
    private val mapDataSource: MapDataWithEditsSource,
    private val notesSource: NotesWithEditsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val countryBoundariesFuture: FutureTask<CountryBoundaries>
): OsmQuestSource {

    /* Must be a singleton because there is a listener that should respond to a change in the
     *  database table */

    private val listeners: MutableList<OsmQuestSource.Listener> = CopyOnWriteArrayList()

    private val allQuestTypes get() = questTypeRegistry.all.filterIsInstance<OsmElementQuestType<*>>()

    private val mapDataSourceListener = object : MapDataWithEditsSource.Listener {

        /** For the given elements, replace the current quests with the given ones. Called when
         *  OSM elements are updated, so the quests that reference that element need to be updated
         *  as well. */
        override fun onUpdated(updated: MapDataWithGeometry, deleted: Collection<ElementKey>) {
            val quests = mutableListOf<OsmQuest>()
            val previousQuests = mutableListOf<OsmQuest>()
            // TODO this could actually also be parallelised
            for (element in updated) {
                previousQuests.addAll(db.getAllForElement(element.type, element.id))
                val geometry = updated.getGeometry(element.type, element.id) ?: continue
                quests.addAll(createQuestsForElement(element, geometry, allQuestTypes))
            }

            val deleteQuestIds = mutableListOf<Long>()
            for (key in deleted) {
                // quests that refer to elements that have been deleted shall be deleted
                deleteQuestIds.addAll(db.getAllForElement(key.elementType, key.elementId).mapNotNull { it.id })
            }

            updateQuests(quests, previousQuests, deleteQuestIds)
        }

        /** Replace all quests of the given types in the given bounding box with the given quests.
         *  Called on download of a quest type for a bounding box. */
        override fun onReplacedForBBox(bbox: BoundingBox, mapDataWithGeometry: MapDataWithGeometry) {
            val quests = createQuestsForBBox(bbox, mapDataWithGeometry, allQuestTypes)
            val previousQuests = db.getAllInBBox(bbox)
            updateQuests(quests, previousQuests, emptyList())
        }
    }

    private val notesSourceListener = object : NotesWithEditsSource.Listener {
        override fun onUpdated(added: Collection<Note>, updated: Collection<Note>, deleted: Collection<Long>) {
            /* NOTE deleted notes are not taken into account because we just get the ID here which
               is useless for us, we'd need the note position. So if the quests should be re-created
               without a new download after closing a note, the listener would need to pass
               the actual deleted note. Currently it is not possible to close notes anyway, so who
               cares
             */

            val addedNotePositions = added.map { it.position }
            val questIdsAtNotes = mutableSetOf<Long>()
            for (pos in addedNotePositions) {
                questIdsAtNotes.addAll(db.getAllIdsInBBox(pos.enclosingBoundingBox(1.0)))
            }
            db.deleteAll(questIdsAtNotes)

            onUpdated(deletedIds = questIdsAtNotes)
        }
    }

    init {
        mapDataSource.addListener(mapDataSourceListener)
        notesSource.addListener(notesSourceListener)
    }

    private fun createQuestsForBBox(
        bbox: BoundingBox,
        mapDataWithGeometry: MapDataWithGeometry,
        questTypes: Collection<OsmElementQuestType<*>>
    ): Collection<OsmQuest> {
        val time = currentTimeMillis()

        val quests = ConcurrentLinkedQueue<OsmQuest>()
        val truncatedBlacklistedPositions = notesSource
            .getAllPositions(bbox)
            .map { it.truncateTo5Decimals() }
            .toSet()
        val countryBoundaries = countryBoundariesFuture.get()
        val hiddenQuests = hiddenDB.getAll().toSet()

        runBlocking {
            for (questType in questTypes) {
                launch(Dispatchers.Default) {
                    val questTypeName = questType::class.simpleName!!
                    if (!countryBoundaries.intersects(bbox, questType.enabledInCountries)) {
                        Log.d(TAG, "$questTypeName: Skipped because it is disabled for this country")
                    } else {
                        val questTime = currentTimeMillis()
                        for (element in questType.getApplicableElements(mapDataWithGeometry)) {
                            val geometry = mapDataWithGeometry.getGeometry(element.type, element.id) ?: continue
                            if (!mayCreateQuest(questType, element, geometry, truncatedBlacklistedPositions, hiddenQuests, bbox)) continue
                            quests.add(OsmQuest(null, questType, element.type, element.id, geometry))
                        }

                        val questSeconds = currentTimeMillis() - questTime
                        Log.d(TAG, "$questTypeName: Found ${quests.size} quests in ${questSeconds}ms")
                    }
                }
            }
        }
        val secondsSpentAnalyzing = (currentTimeMillis() - time) / 1000
        Log.i(TAG,"Created ${quests.size} quests for bbox in ${secondsSpentAnalyzing}s")

        return quests
    }

    private fun createQuestsForElement(
        element: Element,
        geometry: ElementGeometry,
        questTypes: Collection<OsmElementQuestType<*>>
    ): Collection<OsmQuest> {
        val time = currentTimeMillis()

        val paddedBounds = geometry.getBounds().enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING)
        val lazyMapData by lazy { mapDataSource.getMapDataWithGeometry(paddedBounds) }

        val quests = ConcurrentLinkedQueue<OsmQuest>()
        val truncatedBlacklistedPositions = notesSource
            .getAllPositions(paddedBounds)
            .map { it.truncateTo5Decimals() }
            .toSet()
        val hiddenQuests = hiddenDB.getAll().toSet()

        runBlocking {
            for (questType in questTypes) {
                launch(Dispatchers.Default) {
                    val appliesToElement = questType.isApplicableTo(element)
                        ?: questType.getApplicableElements(lazyMapData).any { it.id == element.id && it.type == element.type }

                    if (appliesToElement) {
                        if (mayCreateQuest(questType, element, geometry, truncatedBlacklistedPositions, hiddenQuests, null)) {
                            quests.add(OsmQuest(null, questType, element.type, element.id, geometry))
                        }
                    }
                }
            }
        }
        val secondsSpentAnalyzing = (currentTimeMillis() - time) / 1000
        Log.i(TAG,"Created ${quests.size} quests for ${element.type.name}#${element.id} in ${secondsSpentAnalyzing}s")

        return quests
    }

    private fun createQuestsForQuestKeys(keys: Collection<OsmQuestKey>): List<OsmQuest> {
        val questTypesByElement = mutableMapOf<Element, MutableList<OsmElementQuestType<*>>>()
        for (key in keys) {
            val element = mapDataSource.get(key.elementType, key.elementId) ?: continue
            val questType = questTypeRegistry.getByName(key.questTypeName) as? OsmElementQuestType<*> ?: continue
            questTypesByElement.getOrPut(element) { mutableListOf() }.add(questType)
        }

        // TODO this could actually also be parallelised
        val addedQuests = mutableListOf<OsmQuest>()
        for ((element, questTypes) in questTypesByElement) {
            val geometry = mapDataSource.getGeometry(element.type, element.id) ?: continue
            addedQuests.addAll(createQuestsForElement(element, geometry, questTypes))
        }
        return addedQuests
    }

    private fun updateQuests(
        questsNow: Collection<OsmQuest>,
        questsPreviously: Collection<OsmQuest>,
        deletedQuestIds: Collection<Long>) {

        val time = currentTimeMillis()

        val previousQuestsByKey = mutableMapOf<OsmQuestKey, OsmQuest>()
        questsPreviously.associateByTo(previousQuestsByKey) { it.key }

        val addedQuests = mutableListOf<OsmQuest>()
        for (quest in questsNow) {
            val questKey = quest.key
            if (previousQuestsByKey.containsKey(questKey)) {
                previousQuestsByKey.remove(questKey)
            } else {
                addedQuests.add(quest)
            }
        }
        // quests that were created previously for an element but now not anymore shall be deleted
        val obsoleteQuestIds = previousQuestsByKey.values.mapNotNull { it.id } + deletedQuestIds

        val deletedCount = db.deleteAll(obsoleteQuestIds)
        val addedCount = db.addAll(addedQuests)

        val secondsSpentPersisting = (currentTimeMillis() - time) / 1000.0
        Log.i(TAG,"Added $addedCount new and removed $deletedCount already resolved quests in ${secondsSpentPersisting.format(1)}s")

        val reallyAddedQuests = addedQuests.filter { it.id != null }
        onUpdated(added = reallyAddedQuests, deletedIds = obsoleteQuestIds)
    }

    private fun mayCreateQuest(
        questType: OsmElementQuestType<*>,
        element: Element,
        geometry: ElementGeometry,
        blacklistedPositions: Set<LatLon>,
        blacklistedQuests: Set<OsmQuestKey>,
        downloadedBoundingBox: BoundingBox?
    ): Boolean {
        val pos = geometry.center

        // outside downloaded area: skip
        if (downloadedBoundingBox != null && !downloadedBoundingBox.contains(pos)) return false

        val questKey = OsmQuestKey(element.type, element.id, questType::class.simpleName!!)
        if (blacklistedQuests.contains(questKey)) return false

        // do not create quests whose marker is at/near a blacklisted position
        if (blacklistedPositions.contains(pos.truncateTo5Decimals())) return false

        // do not create quests in countries where the quest is not activated
        val countries = questType.enabledInCountries
        if (!countryBoundariesFuture.get().isInAny(pos, countries))  return false

        // do not create quests that refer to geometry that is too long for a surveyor to be expected to survey
        if (geometry is ElementPolylinesGeometry) {
            val totalLength = geometry.polylines.sumByDouble { it.measuredLength() }
            if (totalLength > MAX_GEOMETRY_LENGTH_IN_METERS) {
                return false
            }
        }
        return true
    }

    /** Mark the quest as hidden by user interaction */
    fun hide(quest: OsmQuest) {
        val questId = quest.id ?: return
        hiddenDB.add(quest.key)
        db.delete(questId)
        onUpdated(deletedIds = listOf(questId))
    }

    /** Un-hides all previously hidden quests by user interaction */
    fun unhideAll(): Int {
        val previouslyHiddenQuestKeys = hiddenDB.getAll()
        /* must delete the hidden quests BEFORE recreating the quests, otherwise they would count
           as hidden again! */
        val result = hiddenDB.deleteAll()

        val createdQuests = createQuestsForQuestKeys(previouslyHiddenQuestKeys)

        db.addAll(createdQuests)

        // some quests may already be in the database
        val reallyAddedQuests = createdQuests.filter { it.id != null }
        onUpdated(added = reallyAddedQuests)

        return result
    }

    override fun get(questId: Long): OsmQuest? =
        db.get(questId)

    override fun getAllInBBoxCount(bbox: BoundingBox): Int =
        db.getAllInBBoxCount(bbox)

    override fun getAllVisibleInBBox(bbox: BoundingBox, questTypes: Collection<String>?): List<OsmQuest> =
        db.getAllInBBox(bbox, questTypes)

    override fun addListener(listener: OsmQuestSource.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: OsmQuestSource.Listener) {
        listeners.remove(listener)
    }

    private fun onUpdated(
        added: Collection<OsmQuest> = emptyList(),
        deletedIds: Collection<Long> = emptyList()
    ) {
        listeners.forEach { it.onUpdated(added, deletedIds) }
    }

    companion object {
        private const val TAG = "OsmQuestController"
    }
}

// the resulting precision is about ~1 meter (see #1089)
private fun LatLon.truncateTo5Decimals() = OsmLatLon(latitude.truncateTo5Decimals(), longitude.truncateTo5Decimals())

private fun Double.truncateTo5Decimals() = (this * 1e5).toInt().toDouble() / 1e5

const val MAX_GEOMETRY_LENGTH_IN_METERS = 600
