import ch.qos.logback.core.subst.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent

fun main() {
    // 1. Setup the Background Scope
    val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 2. Build JDA
    val jda = JDABuilder.createDefault("BOT_TOKEN")
        .enableIntents(
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MEMBERS // Added for member-related stats
        )
        .build()
        .awaitReady()

    // 3. Register the Slash Command
    jda.updateCommands().addCommands(
        Commands.slash("scan", "Replicate search results counter")
            .addOption(OptionType.STRING, "date", "Start date (YYYY-MM-DD)", true)
    ).queue()

    // 4. Attach the Listener and pass the scanner/scope
    val scanner = MessageScanner()
    jda.addEventListener(BotListener(scanner, botScope))
}