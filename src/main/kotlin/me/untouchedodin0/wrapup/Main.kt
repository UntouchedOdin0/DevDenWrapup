package me.untouchedodin0.wrapup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.untouchedodin0.wrapup.commands.WrapupCommand
import me.untouchedodin0.wrapup.listeners.MessageListener
import me.untouchedodin0.wrapup.listeners.VoiceListener
import me.untouchedodin0.wrapup.listeners.message.EmojiListener
import me.untouchedodin0.wrapup.tables.BumpTable
import me.untouchedodin0.wrapup.tables.EmojiUsageTable
import me.untouchedodin0.wrapup.tables.MessagesTable
import me.untouchedodin0.wrapup.tables.VoiceSessionsTable
import me.untouchedodin0.wrapup.utils.EmojiCache
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

fun main() {
    // 1. Setup the Background Scope
    val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val properties = Properties()
    val configFile = File("config.properties")

    if (!configFile.exists()) {
        println("Error: config.properties not found!")
        return
    }

    configFile.inputStream().use { properties.load(it) }
    val token = properties.getProperty("bot.token")
    val testGuildId = properties.getProperty("test.guild.id") // Better to keep in config!

    // Connect to the SQLite file
    Database.connect("jdbc:sqlite:./bot_data.db", driver = "org.sqlite.JDBC")

    transaction {
        SchemaUtils.create(MessagesTable, EmojiUsageTable, BumpTable, VoiceSessionsTable)
    }

    EmojiCache.initialize()

    // 2. Initialize JDA
    val jda = JDABuilder.createDefault(token)
        .enableIntents(
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_VOICE_STATES
        )
        .addEventListeners(
            VoiceListener(botScope),
            MessageListener(botScope),
            EmojiListener(botScope),
            WrapupCommand // IMPORTANT: Make sure the listener is registered!
        )
        .build()
        .awaitReady()

    // --- COMMAND FIX SECTION ---

    // A. Clear ALL Global Commands (Removes the 'ghost' duplicates)
    jda.updateCommands().addCommands().queue {
        println("🗑️ Successfully cleared all global commands.")
    }

    // B. Register to your specific Guild for instant updates
    if (testGuildId != null) {
        val guild = jda.getGuildById(testGuildId)
        if (guild != null) {
            guild.updateCommands().addCommands(
                Commands.slash("scan", "Replicate search results counter")
                    .addOption(OptionType.STRING, "date", "Start date (DD-MM-YYYY)", true),
                Commands.slash("wrapup", "Generate your year-end stats summary!")
            ).queue {
                println("✅ Registered clean commands instantly to guild: ${guild.name}")
            }
        } else {
            println("❌ Could not find guild with ID: $testGuildId. Check your config!")
        }
    }

    // --- END COMMAND FIX SECTION ---

    val scanner = MessageScanner()
    jda.addEventListener(BotListener(scanner, botScope))
}
