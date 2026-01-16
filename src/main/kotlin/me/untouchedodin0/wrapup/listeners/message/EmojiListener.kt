package me.untouchedodin0.wrapup.listeners.message

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.untouchedodin0.wrapup.tables.EmojiUsageTable
import me.untouchedodin0.wrapup.utils.EmojiCache
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class EmojiListener(private val scope: CoroutineScope) : ListenerAdapter() {

    private val customEmojiRegex = Regex("<a?(:[a-zA-Z0-9_]+:)([0-9]+)>")
    private val unicodeEmojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+")

    // use this https://github.com/vdurmont/emoji-java (already imported in gradle)
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage) return

        val content = event.message.contentRaw
        val userId = event.author.idLong
        val deletedMessageId = event.messageIdLong

        // 1. Find Custom Emojis and store them
        val customEmojiMatches = customEmojiRegex.findAll(content).toList()
        val customEmojis = customEmojiMatches.map { it.groupValues[1] }

        // 2. Remove Custom Emojis from the string so the Unicode scanner doesn't get confused
        var cleanContent = content
        customEmojiMatches.forEach { match ->
            cleanContent = cleanContent.replace(match.value, "")
        }

        // 3. Get Unicode Emojis from the "cleaned" content
        val unicodeEmojis = EmojiCache.extractEmojis(cleanContent)

        // 4. Combine them
        val allEmojis = unicodeEmojis + customEmojis

        println("Total Emojis (Cleaned): $allEmojis")

        // Save to database logic...

        if (allEmojis.isNotEmpty()) {
            saveEmojiStats(userId, deletedMessageId, allEmojis)
        }
    }

    override fun onMessageDelete(event: MessageDeleteEvent) {
        val deletedMessageId = event.messageIdLong
        println("Message deleted with id $deletedMessageId")

        scope.launch(Dispatchers.IO) {
            transaction {
                // Delete all emojis associated with that message ID
                val deletedCount = EmojiUsageTable.deleteWhere {
                    EmojiUsageTable.messageId eq deletedMessageId
                }
                if (deletedCount > 0) {
                    println("Removed $deletedCount emojis from DB because message was deleted.")
                }
            }
        }
    }

    private fun saveEmojiStats(userId: Long, messageId: Long, emojis: List<String>) {
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            transaction {
                emojis.forEach { emojiName ->
                    EmojiUsageTable.insert {
                        it[this.messageId] = messageId
                        it[this.emoji] = emojiName
                        it[this.userId] = userId
                        it[this.timestamp] = now
                    }
                }
            }
        }
    }
}