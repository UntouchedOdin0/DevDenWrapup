package me.untouchedodin0.wrapup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.untouchedodin0.wrapup.tables.MessagesTable
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


class BotListener(
    private val scanner: MessageScanner,
    private val botScope: CoroutineScope,
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name == "scan") {
            event.deferReply().queue() //

            val dateStr = event.getOption("date")?.asString ?: return
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val customEmojiRegex = Regex("<a?:[a-zA-Z0-9_]+:[0-9]+>") // Matches custom Discord emojis (static and animated)
            val unicodeEmojiRegex = Regex("[\\uD83C\\uDF00-\\uD83D\\uDE4F\\uD83D\\uDE80-\\uD83D\\uDEFF]")

            val since = try {
                LocalDate.parse(dateStr, formatter)
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
            } catch (_: Exception) {
                event.hook.editOriginal("❌ Invalid date format. Use DD-MM-YYYY (e.g., 25-12-2024)").queue()
                return
            }

            botScope.launch {
                var messageCount = 0
                val uniqueUsers = mutableSetOf<Long>()
                val channelActivity = mutableMapOf<String, Int>()
                val emojiCounts = mutableMapOf<String, Long>() // Local tally
                val dbBuffer = mutableListOf<Message>()

                scanner.streamGuildMessages(event.guild!!, since)
                    .collect { message ->
                        val content = message.contentRaw

                        // 1. Extract Custom Emojis

                        customEmojiRegex.findAll(content).forEach {
                            emojiCounts.merge(it.value, 1L, Long::plus)
                        }

                        // 2. Extract Unicode Emojis
                        unicodeEmojiRegex.findAll(content).forEach {
                            emojiCounts.merge(it.value, 1L, Long::plus)
                        }

                        messageCount++
                        uniqueUsers.add(message.author.idLong)
                        channelActivity.merge(message.channel.name, 1, Int::plus)

                        dbBuffer.add(message)

                        // Batch insert every 1,000 messages
                        if (dbBuffer.size >= 1000) {
                            val toSave = dbBuffer.toList()
                            dbBuffer.clear()
                            saveBatch(toSave)

                            // After saving 1,000 msgs, pause for 100ms
                            // This prevents "Thread Starvation" and JDA rate-limit spikes
                            delay(100)
                        }
                    }

                // --- CRITICAL FIX: Save the remaining messages ---
                if (dbBuffer.isNotEmpty()) {
                    saveBatch(dbBuffer.toList())
                    dbBuffer.clear()
                }

                saveEmojiStats(emojiCounts)

                // Verify the database count directly from the file
                val totalInDb: Long = transaction {
                    MessagesTable.selectAll().count()
                }
                val topChannel = channelActivity.maxByOrNull { it.value }?.key ?: "N/A"

                val response = """
                📊 **Scan Results since $dateStr**
                
                ✉️ **Total Messages:** ${"%,d".format(messageCount)}
                👥 **Unique Users:** ${"%,d".format(uniqueUsers.size)}
                🏗️ **Channels Scanned:** ${channelActivity.size}
                🔥 **Most Active:** #$topChannel
                
                *Scan completed successfully without blocking gateway.*
                """.trimIndent()

                // Find the top emoji from the scan
                val topEmojiEntry = emojiCounts.maxByOrNull { it.value }
                val topEmojiDisplay = topEmojiEntry?.let { (emoji, count) ->
                    "$emoji **%,d**".format(count)
                } ?: "None"
                val embed = EmbedBuilder().apply {
                    setTitle("📊 Scan Results")
                    setDescription("Metadata collection since **$dateStr**")
                    setColor(Color.GREEN)

                    // Using the stats you provided
                    addField("✉️ Total Messages", "%,d".format(messageCount), true)
                    addField("👥 Unique Users", "%,d".format(uniqueUsers.size), true)
                    addField("🏗️ Channels Scanned", channelActivity.size.toString(), true)

                    addField("🔥 Most Active", "#$topChannel", true)
                    addField("✨ Top Emoji", topEmojiDisplay, true)
                    addField("🗄️ Database Total", "%,d".format(totalInDb), true)

                    setFooter("Privacy Note: Only metadata (who/when/where) was saved.")
                    setTimestamp(Instant.now())
                }.build()

//                event.hook.editOriginal(response).queue() //
                event.hook.editOriginalEmbeds(embed).queue()

/*                val response =
                    """
        📊 **Scan Results since $dateStr**
        
        ✉️ **Total Messages Found:** ${"%,d".format(messageCount)}
        👥 **Unique Users:** ${"%,d".format(uniqueUsers.size)}
        🏗️ **Channels Scanned:** ${channelActivity.size}
        🔥 **Most Active:** #$topChannel
        🗄️ **Database Total:** ${"%,d".format(totalInDb)} messages archived.
        
        *Scan completed successfully.*
        """.trimIndent()*/

            }
        }
    }

    private fun saveBatch(toSave: List<Message>) {
        botScope.launch(Dispatchers.IO) {
            transaction {
                // Log SQL to console to confirm it is actually running
                addLogger(StdOutSqlLogger)
                MessagesTable.batchInsert(toSave, ignore = true) { msg ->
                    this[MessagesTable.id] = msg.idLong
                    this[MessagesTable.authorId] = msg.author.idLong
                    this[MessagesTable.channelId] = msg.channel.idLong
                    this[MessagesTable.timestamp] = msg.timeCreated.toInstant().toEpochMilli()
                }
            }
        }
    }

    private fun saveEmojiStats(counts: Map<String, Long>) {
        transaction {
            counts.forEach { (emojiId, amount) ->
                // Use raw SQL for a "Clean Upsert" in SQLite
                exec("""
                INSERT INTO emoji_stats (emoji_id, use_count) 
                VALUES ('$emojiId', $amount)
                ON CONFLICT(emoji_id) DO UPDATE SET use_count = use_count + excluded.use_count
            """)
            }
        }
    }
}