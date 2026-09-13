package eu.kanade.tachiyomi.data.database

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.util.Date

val dateAdapter =
    object : ColumnAdapter<Date, Long> {
        override fun decode(databaseValue: Long): Date = Date(databaseValue)

        override fun encode(value: Date): Long = value.time
    }

private const val listOfStringsSeparator = ", "
val listOfStringsAdapter =
    object : ColumnAdapter<List<String>, String> {
        override fun decode(databaseValue: String) =
            if (databaseValue.isEmpty()) {
                emptyList()
            } else {
                databaseValue.split(listOfStringsSeparator)
            }

        override fun encode(value: List<String>) = value.joinToString(separator = listOfStringsSeparator)
    }

val updateStrategyAdapter =
    object : ColumnAdapter<UpdateStrategy, Int> {
        private val enumValues by lazy { UpdateStrategy.entries }

        override fun decode(databaseValue: Int): UpdateStrategy = enumValues.getOrElse(databaseValue) { UpdateStrategy.ALWAYS_UPDATE }

        override fun encode(value: UpdateStrategy): Int = value.ordinal
    }

val memoAdapter =
    object : ColumnAdapter<JsonObject, String> {
        override fun decode(databaseValue: String): JsonObject =
            if (databaseValue.isEmpty()) {
                JsonObject(emptyMap())
            } else {
                try {
                    Json.decodeFromString(JsonObject.serializer(), databaseValue)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode manga/chapter memo")
                    JsonObject(emptyMap())
                }
            }

        override fun encode(value: JsonObject): String = value.toString()
    }

interface ColumnAdapter<T : Any, S> {
    /**
     * @return [databaseValue] decoded as type [T].
     */
    fun decode(databaseValue: S): T

    /**
     * @return [value] encoded as database type [S].
     */
    fun encode(value: T): S
}
