package com.ssafy.culture.data.repository

import com.ssafy.culture.data.local.CultureDao
import com.ssafy.culture.data.local.CultureEntity
import com.ssafy.culture.data.remote.CultureApi
import com.ssafy.culture.domain.model.CultureItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface CultureRepository {
    fun observeItems(): Flow<List<CultureItem>>
    fun observeItem(id: Int): Flow<CultureItem?>
    suspend fun refreshItems()
}

@Singleton
class DefaultCultureRepository @Inject constructor(
    private val cultureApi: CultureApi,
    private val cultureDao: CultureDao,
) : CultureRepository {

    override fun observeItems(): Flow<List<CultureItem>> =
        cultureDao.observeItems().map { entities ->
            entities.map(CultureEntity::toDomain)
        }

    override fun observeItem(id: Int): Flow<CultureItem?> =
        cultureDao.observeItem(id).map { entity ->
            entity?.toDomain()
        }

    override suspend fun refreshItems() = withContext(Dispatchers.IO) {
        val remoteItems = cultureApi.getCultureItems().map { dto ->
            CultureEntity(
                id = dto.id,
                title = dto.title.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                },
                imageUrl = dto.url,
                thumbnailUrl = dto.thumbnailUrl,
            )
        }
        cultureDao.upsertAll(remoteItems)
    }
}

private fun CultureEntity.toDomain(): CultureItem =
    CultureItem(
        id = id,
        title = title,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
    )
