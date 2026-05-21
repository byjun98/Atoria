package com.ssafy.culture.data.route

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ssafy.culture.domain.model.RoutePoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun appendPoint(storyId: Long, point: RoutePoint) {
        val current: MutableList<RoutePoint> = getRouteForStory(storyId).toMutableList()
        current.add(point)
        preferences.edit {
            putString(routeKey(storyId), gson.toJson(current))
        }
    }

    fun getRouteForStory(storyId: Long): List<RoutePoint> {
        val json: String = preferences.getString(routeKey(storyId), null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<RoutePoint>>() {}.type
            gson.fromJson<List<RoutePoint>>(json, type) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    fun setActiveStoryId(storyId: Long) {
        preferences.edit {
            putLong(KeyActiveStoryId, storyId)
        }
    }

    fun getActiveStoryId(): Long? {
        if (!preferences.contains(KeyActiveStoryId)) return null
        return preferences.getLong(KeyActiveStoryId, -1L).takeIf { it > 0 }
    }

    fun clearRouteForStory(storyId: Long) {
        preferences.edit {
            remove(routeKey(storyId))
        }
    }

    private fun routeKey(storyId: Long): String = "route_$storyId"

    private companion object {
        const val PreferencesName: String = "route_history"
        const val KeyActiveStoryId: String = "active_story_id"
    }
}
