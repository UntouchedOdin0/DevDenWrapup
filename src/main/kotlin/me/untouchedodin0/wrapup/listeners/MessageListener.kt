package me.untouchedodin0.wrapup.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.untouchedodin0.wrapup.tables.BumpTable
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class MessageListener(private val scope: CoroutineScope) : ListenerAdapter() {

    //onMessageReceived ?
    private val mediaRegex = Regex("(?i)https?://\\S+\\.(jpg|jpeg|png|gif|webp|mp4|mov|webm)")
    private val bumpRegex = Regex("(.+)\\s+bumped in just\\s+([0-9.]+)\\s*s")

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val content = event.message.contentRaw

        // 1. Create a "Clean" version of the message (removes emojis/symbols)
        // This regex keeps letters, numbers, and spaces only
        val cleanContent = content.replace(Regex("[^a-zA-Z0-9.\\s]"), "").trim()

        // 2. Check for the bump in the CLEANED string
        if (cleanContent.contains("bumped in just")) {
            // Now the regex is super simple because the emojis are gone!
            val match = bumpRegex.find(cleanContent)

            if (match != null) {
                val name = match.groupValues[1].trim()
                val speed = match.groupValues[2].toDoubleOrNull() ?: 0.0

                println("🎯 Clean Match! Name: $name, Speed: $speed")
                logBump(event, match)
                return
            }
        }

        // 3. Normal Bot Filter
        if (event.author.isBot) return

        val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val chars = content.length
        val hasMedia = event.message.attachments.isNotEmpty() || mediaRegex.containsMatchIn(content)
        val isReply = event.message.messageReference != null

        println("content $content words $words chars $chars hasMedia $hasMedia isReply $isReply")
    }

    private fun logBump(event: MessageReceivedEvent, match: MatchResult) {
        val rawName = match.groupValues[1].trim()
        // Convert the "16.29" string into a Double
        val speed = match.groupValues[2].toDoubleOrNull() ?: 0.0

        // Find the user by name
        val member = event.guild.getMembersByEffectiveName(rawName, true).firstOrNull()

        if (member != null) {
            scope.launch(Dispatchers.IO) {
                transaction {
                    BumpTable.insert {
                        it[this.userId] = member.idLong
                        it[this.bumpTime] = speed
                        it[this.timestamp] = System.currentTimeMillis()
                    }
                }
                println("Logged: ${member.effectiveName} bumped in ${speed}s")
            }
        }
    }
}