package org.wordpress.android.ui.reader.services.discover

import android.app.job.JobParameters
import com.wordpress.rest.RestRequest.ErrorListener
import com.wordpress.rest.RestRequest.Listener
import org.json.JSONArray
import org.json.JSONObject
import org.wordpress.android.WordPress
import org.wordpress.android.datasets.ReaderDiscoverCardsTable
import org.wordpress.android.datasets.ReaderPostTable
import org.wordpress.android.models.ReaderPost
import org.wordpress.android.models.ReaderPostList
import org.wordpress.android.models.ReaderTag
import org.wordpress.android.models.ReaderTagList
import org.wordpress.android.models.ReaderTagType.DEFAULT
import org.wordpress.android.models.discover.ReaderDiscoverCard
import org.wordpress.android.models.discover.ReaderDiscoverCard.InterestsYouMayLikeCard
import org.wordpress.android.models.discover.ReaderDiscoverCard.ReaderPostCard
import org.wordpress.android.ui.reader.ReaderConstants
import org.wordpress.android.ui.reader.actions.ReaderActions
import org.wordpress.android.ui.reader.actions.ReaderActions.UpdateResult.FAILED
import org.wordpress.android.ui.reader.actions.ReaderActions.UpdateResult.HAS_NEW
import org.wordpress.android.ui.reader.actions.ReaderActions.UpdateResultListener
import org.wordpress.android.ui.reader.services.ServiceCompletionListener
import org.wordpress.android.ui.reader.services.discover.ReaderDiscoverLogic.DiscoverTasks.REQUEST
import org.wordpress.android.ui.reader.services.discover.ReaderDiscoverLogic.DiscoverTasks.REQUEST_FORCE
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.READER
import org.wordpress.android.util.JSONUtils

/**
 * This class contains logic related to fetching data for the discover tab in the Reader.
 */
class ReaderDiscoverLogic constructor(private val completionListener: ServiceCompletionListener) {
    enum class DiscoverTasks {
        REQUEST, REQUEST_FORCE
    }

    private var listenerCompanion: JobParameters? = null

    fun performTasks(task: DiscoverTasks, companion: JobParameters?) {
        listenerCompanion = companion

        when (task) {
            REQUEST -> {
                requestDataForDiscover(false, UpdateResultListener {
                    // TODO malinjir emit REQUEST finish event
                    completionListener.onCompleted(listenerCompanion)
                })
            }
            REQUEST_FORCE -> {
                requestDataForDiscover(true, UpdateResultListener {
                    // TODO malinjir emit REQUEST_FORCE finish event
                    completionListener.onCompleted(listenerCompanion)
                })
            }
        }
    }

    private fun requestDataForDiscover(forceRefresh: Boolean, resultListener: ReaderActions.UpdateResultListener) {
        val path = "read/tags/cards"

        val sb = StringBuilder(path)

        if (!forceRefresh) {
            sb.append("?page_handle=")
            // TODO malinjir load page handle
        }
        val listener = Listener { jsonObject -> // remember when this tag was updated if newer posts were requested
            if (forceRefresh) {
                // TODO malinjir clear cache
            }
            handleRequestDiscoverDataResponse(jsonObject, resultListener)
        }
        val errorListener = ErrorListener { volleyError ->
            AppLog.e(READER, volleyError)
            resultListener.onUpdateResult(FAILED)
        }

        WordPress.getRestClientUtilsV2()[sb.toString(), null, null, listener, errorListener]
    }

    private fun handleRequestDiscoverDataResponse(json: JSONObject?, resultListener: UpdateResultListener) {
        // TODO malinjir move to bg thread
        if (json == null) {
            resultListener.onUpdateResult(FAILED)
            return
        }
        val fullCardsJson = json.optJSONArray(ReaderConstants.JSON_CARDS)

        // Parse the json into cards model objects
        val cards = parseCards(fullCardsJson)
        insertPostsIntoDb(cards.filterIsInstance<ReaderPostCard>().map { it.post })

        // Simplify the json. The simplified version is used in the upper layers to load the data from the db.
        val simplifiedCardsJson = simplifyJson(fullCardsJson)
        insertCardsJsonIntoDb(simplifiedCardsJson)

        val nextPageHandle = parseNextPageHandle(json)
        // TODO malinjir save next page handle into shared preferences

        resultListener.onUpdateResult(HAS_NEW)
    }

    private fun parseCards(cardsJsonArray: JSONArray): ArrayList<ReaderDiscoverCard> {
        val cards: ArrayList<ReaderDiscoverCard> = arrayListOf()
        for (i in 0 until cardsJsonArray.length()) {
            val cardJson = cardsJsonArray.getJSONObject(i)
            when (cardJson.getString(ReaderConstants.JSON_CARD_TYPE)) {
                ReaderConstants.JSON_CARD_INTERESTS_YOU_MAY_LIKE -> {
                    val interests = parseInterestTagsList(cardJson)
                    cards.add(InterestsYouMayLikeCard(interests))
                }
                ReaderConstants.JSON_CARD_POST -> {
                    val post = ReaderPost.fromJson(cardJson.getJSONObject(ReaderConstants.JSON_CARD_DATA))
                    cards.add(ReaderPostCard(post))
                }
            }
        }
        return cards
    }

    private fun insertPostsIntoDb(posts: List<ReaderPost>) {
        val postList = ReaderPostList()
        postList.addAll(posts)
        ReaderPostTable.addOrUpdatePosts(null, postList)
    }

    /**
     * This methods replace the gigantic post object with its simplified version. The post data are already stored in
     * the database so we don't need to store them within the json.
     */
    private fun simplifyJson(cardsJsonArray: JSONArray): JSONArray {
        for (i in 0 until cardsJsonArray.length()) {
            val cardJson = cardsJsonArray.getJSONObject(i)
            if (cardJson.getString(ReaderConstants.JSON_CARD_TYPE) == ReaderConstants.JSON_CARD_POST) {
                cardsJsonArray.put(i, convertToSimplifiedPostJson(cardJson))
            }
        }
        return cardsJsonArray
    }

    /**
     * Removes all unnecessary fields from the gigantic post object - keeps only postId, siteId and pseudoId.
     */
    private fun convertToSimplifiedPostJson(cardJson: JSONObject): JSONObject {
        val originalPostData = cardJson.getJSONObject(ReaderConstants.JSON_CARD_DATA)
        val simplifiedPostData = JSONObject()
        // copy only fields which uniquely identify this post
        simplifiedPostData.put(ReaderConstants.POST_ID, originalPostData.get(ReaderConstants.POST_ID))
        simplifiedPostData.put(ReaderConstants.POST_SITE_ID, originalPostData.get(ReaderConstants.POST_SITE_ID))
        simplifiedPostData.put(ReaderConstants.POST_PSEUDO_ID, originalPostData.get(ReaderConstants.POST_PSEUDO_ID))
        cardJson.put(ReaderConstants.JSON_CARD_DATA, simplifiedPostData)
        return cardJson
    }

    private fun parseInterestTagsList(jsonObject: JSONObject?): ReaderTagList {
        val interestTags = ReaderTagList()
        if (jsonObject == null) {
            return interestTags
        }
        val jsonInterests = jsonObject.optJSONArray(ReaderConstants.JSON_CARD_DATA) ?: return interestTags
        for (i in 0 until jsonInterests.length()) {
            interestTags.add(parseInterestTag(jsonInterests.optJSONObject(i)))
        }
        return interestTags
    }

    private fun insertCardsJsonIntoDb(simplifiedCardsJson: JSONArray) {
        ReaderDiscoverCardsTable.addCardsPage(simplifiedCardsJson.toString())
    }

    private fun parseInterestTag(jsonInterest: JSONObject): ReaderTag {
        val tagTitle = JSONUtils.getStringDecoded(jsonInterest, ReaderConstants.JSON_TAG_TITLE)
        val tagSlug = JSONUtils.getStringDecoded(jsonInterest, ReaderConstants.JSON_TAG_SLUG)
        return ReaderTag(tagSlug, tagTitle, tagTitle, "", DEFAULT)
    }

    private fun parseNextPageHandle(jsonObject: JSONObject): String =
            jsonObject.getString(ReaderConstants.JSON_NEXT_PAGE_HANDLE)
}
