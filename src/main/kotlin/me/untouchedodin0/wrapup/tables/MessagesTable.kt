package me.untouchedodin0.wrapup.tables

import org.jetbrains.exposed.sql.Table

object MessagesTable : Table("messages") {
    val id = long("message_id")
    val authorId = long("author_id").index()
    val channelId = long("channel_id")
    val timestamp = long("timestamp").index()

    // Stats for "The Yap"
    val wordCount = integer("word_count")
    val charCount = integer("char_count")

    // Stats for "The Vibe"
    val hasAttachment = bool("has_attachment")
    val isReply = bool("is_reply").default(false)

    // Optional: Pre-calculate the hour for "Time of Day" stats
    val hourOfDay = integer("hour_of_day")

    override val primaryKey = PrimaryKey(id)
}

object EmojiUsageTable : Table("emoji_usage") {
    val id = integer("id").autoIncrement()
    val messageId = long("message_id").index() // <--- ADD THIS
    val emoji = varchar("emoji_id", 128)
    val userId = long("user_id").index()
    val timestamp = long("timestamp").index() // Crucial for date range queries

    override val primaryKey = PrimaryKey(id)
}

object BumpTable : Table("bumps") {
    val id = integer("id").autoIncrement()
    val userId = long("user_id").index()
    val bumpTime = double("bump_time") // Stores the speed (e.g., 16.29)
    val timestamp = long("timestamp")  // Stores when it happened

    override val primaryKey = PrimaryKey(id)
}

object VoiceSessionsTable : Table("voice_sessions") {
    val id = integer("id").autoIncrement()
    val userId = long("user_id").index()
    val channelId = long("channel_id")
    val startTime = long("start_time")
    val endTime = long("end_time")
    val duration = long("duration") // Pre-calculated (endTime - startTime)

    override val primaryKey = PrimaryKey(id)
}