package com.smartisan.music.data.playlist

data class UserPlaylistSummary(
    val id: String,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class UserPlaylistDetail(
    val id: String,
    val name: String,
    val mediaIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

sealed interface PlaylistCreateResult {
    data class Success(
        val playlistId: String,
        val addedCount: Int,
    ) : PlaylistCreateResult

    data object EmptyName : PlaylistCreateResult

    data object DuplicateName : PlaylistCreateResult
}

sealed interface PlaylistRenameResult {
    data object Success : PlaylistRenameResult

    data object EmptyName : PlaylistRenameResult

    data object DuplicateName : PlaylistRenameResult

    data object MissingPlaylist : PlaylistRenameResult
}

data class PlaylistAddResult(
    val addedCount: Int,
    val duplicateCount: Int,
)

internal fun normalizePlaylistName(name: String): String {
    return name.trim()
}

internal fun nextUntitledPlaylistName(
    existingNames: Collection<String>,
    baseName: String,
): String {
    val occupiedNumbers = existingNames.asSequence()
        .map(::normalizePlaylistName)
        .mapNotNull { name ->
            when {
                name == baseName -> 1
                name.startsWith("$baseName ") -> {
                    name.removePrefix("$baseName ").toIntOrNull()?.takeIf { it > 0 }
                }
                else -> null
            }
        }
        .toSet()

    var index = 1
    while (index in occupiedNumbers) {
        index += 1
    }
    return "$baseName $index"
}
