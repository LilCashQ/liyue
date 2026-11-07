package com.liyue.mctoolbox.map

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsed form of the JSON document returned by `structureDetails` in the
 * native bridge (see `cubiomes_details.c` upstream).
 *
 * The bridge reports what it can actually determine: [variant] is always
 * present when [supported] is set, while [pieces] only appears for structure
 * types whose layout cubiomes can reconstruct. [limits] names the parts that
 * are approximate or unavailable so the UI can say so instead of pretending.
 */
data class StructureDetails(
    val supported: Boolean,
    val truncated: Boolean,
    val variant: StructureVariant?,
    val pieces: List<StructurePiece>,
    val containers: List<StructureContainer>,
    val limits: List<String>,
    /** Filled in only when a single container was requested. */
    val items: List<StructureItem> = emptyList(),
) {
    companion object {
        fun parse(json: String): StructureDetails? = runCatching {
            val root = JSONObject(json)
            StructureDetails(
                supported = root.optBoolean("supported", false),
                truncated = root.optBoolean("truncated", false),
                variant = root.optJSONObject("variant")?.let(::parseVariant),
                pieces = root.optJSONArray("pieces").mapObjects(::parsePiece),
                containers = root.optJSONArray("containers").mapObjects(::parseContainer),
                limits = root.optJSONArray("limits").mapStrings(),
                items = root.optJSONArray("items").mapObjects(::parseItem),
            )
        }.getOrNull()

        private fun parseVariant(json: JSONObject): StructureVariant = StructureVariant(
            rotation = json.optInt("rotation", 0),
            mirror = json.optInt("mirror", 0),
            biomeId = json.optInt("biome", -1),
            abandoned = json.optBoolean("abandoned", false),
            giant = json.optBoolean("giant", false),
            underground = json.optBoolean("underground", false),
            airPocket = json.optBoolean("airPocket", false),
            basement = json.optBoolean("basement", false),
            cracked = json.optBoolean("cracked", false),
            size = json.optInt("size", 0),
            offsetX = json.optJSONObject("offset")?.optInt("x", 0) ?: 0,
            offsetY = json.optJSONObject("offset")?.optInt("y", 0) ?: 0,
            offsetZ = json.optJSONObject("offset")?.optInt("z", 0) ?: 0,
            sizeX = json.optJSONObject("size3d")?.optInt("x", 0) ?: 0,
            sizeY = json.optJSONObject("size3d")?.optInt("y", 0) ?: 0,
            sizeZ = json.optJSONObject("size3d")?.optInt("z", 0) ?: 0,
        )

        private fun parsePiece(json: JSONObject): StructurePiece = StructurePiece(
            id = json.optInt("id", 0),
            name = json.optString("name", "unknown"),
            rotation = json.optInt("rotation", 0),
            endPortalRoom = json.optBoolean("endPortalRoom", false),
            portalEyes = json.optInt("portalEyes", 0),
            positionKnown = json.optBoolean("positionKnown", true),
            x = json.optJSONObject("position")?.optInt("x", 0) ?: 0,
            y = json.optJSONObject("position")?.optInt("y", 0) ?: 0,
            z = json.optJSONObject("position")?.optInt("z", 0) ?: 0,
            hasBounds = json.has("min") && json.has("max"),
            minX = json.optJSONObject("min")?.optInt("x", 0) ?: 0,
            minY = json.optJSONObject("min")?.optInt("y", 0) ?: 0,
            minZ = json.optJSONObject("min")?.optInt("z", 0) ?: 0,
            maxX = json.optJSONObject("max")?.optInt("x", 0) ?: 0,
            maxY = json.optJSONObject("max")?.optInt("y", 0) ?: 0,
            maxZ = json.optJSONObject("max")?.optInt("z", 0) ?: 0,
        )

        private fun parseItem(json: JSONObject): StructureItem = StructureItem(
            id = json.optString("id", ""),
            count = json.optInt("count", 0),
        )

        private fun parseContainer(json: JSONObject): StructureContainer = StructureContainer(
            id = json.optInt("id", 0),
            piece = json.optInt("piece", 0),
            x = json.optInt("x", 0),
            z = json.optInt("z", 0),
            table = json.optString("table", ""),
        )

        private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
        }

        private fun JSONArray?.mapStrings(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it, "").takeIf(String::isNotEmpty) }
        }
    }
}

/** Rotation, biome and bounding box of one structure start. */
data class StructureVariant(
    val rotation: Int,
    val mirror: Int,
    val biomeId: Int,
    val abandoned: Boolean,
    val giant: Boolean,
    val underground: Boolean,
    val airPocket: Boolean,
    val basement: Boolean,
    val cracked: Boolean,
    val size: Int,
    val offsetX: Int,
    val offsetY: Int,
    val offsetZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
)

/** One generated piece of a structure layout. */
data class StructurePiece(
    val id: Int,
    val name: String,
    val rotation: Int,
    val endPortalRoom: Boolean,
    val portalEyes: Int,
    val positionKnown: Boolean,
    val x: Int,
    val y: Int,
    val z: Int,
    val hasBounds: Boolean,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
)

/** One loot chest inside a piece. */
/** One rolled item stack inside a chest; [id] looks like `minecraft:golden_apple`. */
data class StructureItem(val id: String, val count: Int)

data class StructureContainer(
    val id: Int,
    val piece: Int,
    val x: Int,
    val z: Int,
    val table: String,
)
