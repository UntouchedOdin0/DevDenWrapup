import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.time.LocalDate
import java.time.ZoneOffset

class BotListener(
    private val scanner: MessageScanner,
    private val botScope: CoroutineScope
) : ListenerAdapter() {

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name == "scan") {
            event.deferReply().queue() //

            val dateStr = event.getOption("date")?.asString ?: return
            val since = try {
                LocalDate.parse(dateStr).atStartOfDay().atOffset(ZoneOffset.UTC)
            } catch (e: Exception) {
                event.hook.editOriginal("❌ Invalid date format. Use YYYY-MM-DD").queue()
                return
            }

            botScope.launch {
                var messageCount = 0
                val uniqueUsers = mutableSetOf<Long>() // Stores IDs to save memory
                val channelActivity = mutableMapOf<String, Int>()

                scanner.streamGuildMessages(event.guild!!, since)
                    .collect { message ->
                        messageCount++
                        uniqueUsers.add(message.author.idLong)
                        channelActivity.merge(message.channel.name, 1, Int::plus)
                    }

                // Find the most active channel
                val topChannel = channelActivity.maxByOrNull { it.value }?.key ?: "N/A"

                // Build a "General Useful Info" embed
                val response = """
                📊 **Scan Results since $dateStr**
                
                ✉️ **Total Messages:** ${"%,d".format(messageCount)}
                👥 **Unique Users:** ${"%,d".format(uniqueUsers.size)}
                🏗️ **Channels Scanned:** ${channelActivity.size}
                🔥 **Most Active:** #$topChannel
                
                *Scan completed successfully without blocking gateway.*
            """.trimIndent()

                event.hook.editOriginal(response).queue() //
            }
        }
    }
}