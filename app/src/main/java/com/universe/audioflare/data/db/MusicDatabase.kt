package com.universe.audioflare.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.universe.audioflare.data.db.entities.AlbumEntity
import com.universe.audioflare.data.db.entities.ArtistEntity
import com.universe.audioflare.data.db.entities.FollowedArtistSingleAndAlbum
import com.universe.audioflare.data.db.entities.GoogleAccountEntity
import com.universe.audioflare.data.db.entities.LocalPlaylistEntity
import com.universe.audioflare.data.db.entities.LyricsEntity
import com.universe.audioflare.data.db.entities.NewFormatEntity
import com.universe.audioflare.data.db.entities.NotificationEntity
import com.universe.audioflare.data.db.entities.PairSongLocalPlaylist
import com.universe.audioflare.data.db.entities.PlaylistEntity
import com.universe.audioflare.data.db.entities.QueueEntity
import com.universe.audioflare.data.db.entities.SearchHistory
import com.universe.audioflare.data.db.entities.SetVideoIdEntity
import com.universe.audioflare.data.db.entities.SongEntity
import com.universe.audioflare.data.db.entities.SongInfoEntity

@Database(
    entities = [NewFormatEntity::class, SongInfoEntity::class, SearchHistory::class, SongEntity::class, ArtistEntity::class, AlbumEntity::class, PlaylistEntity::class, LocalPlaylistEntity::class, LyricsEntity::class, QueueEntity::class, SetVideoIdEntity::class, PairSongLocalPlaylist::class, GoogleAccountEntity::class, FollowedArtistSingleAndAlbum::class, NotificationEntity::class],
    version = 9,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(
        from = 1,
        to = 3
    ), AutoMigration(from = 3, to = 4), AutoMigration(from = 2, to = 4), AutoMigration(
        from = 3,
        to = 5
    ), AutoMigration(4, 5), AutoMigration(6, 7), AutoMigration(
        7,
        8,
        spec = AutoMigration7_8::class
    ), AutoMigration(8, 9)]
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun getDatabaseDao(): DatabaseDao
}