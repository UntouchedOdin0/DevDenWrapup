package me.untouchedodin0.wrapup.commands

import me.untouchedodin0.wrapup.tables.EmojiUsageTable
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object WrapupCommand : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "wrapup") return

        // 1. Tell Discord we are thinking (prevents "Command Failed" if DB is slow)
        event.deferReply().queue()

        val userId = event.user.idLong

        // 2. Fetch some quick stats for the reply
        transaction {
            // 1. Get the Top 3 Emojis by grouping and counting
            val topEmojis = EmojiUsageTable
                .slice(EmojiUsageTable.emoji, EmojiUsageTable.emoji.count())
                .selectAll().where { EmojiUsageTable.userId eq userId }
                .groupBy(EmojiUsageTable.emoji)
                .orderBy(EmojiUsageTable.emoji.count(), SortOrder.DESC)
                .limit(3)
                .mapIndexed { index, row ->
                    val emojiName = row[EmojiUsageTable.emoji]
                    val count = row[EmojiUsageTable.emoji.count()]
                    "#${index + 1} $emojiName - You used this $count times"
                }

            // 2. Get the total count for the header
            val totalCount = EmojiUsageTable
                .selectAll().where { EmojiUsageTable.userId eq userId }
                .count()

            if (totalCount == 0L) {
                event.hook.sendMessage("You haven't used any emojis yet!").queue()
            } else {
                val favoriteEmojiText = if (topEmojis.isNotEmpty()) {
                    "Your favorite emojis were:\n\n${topEmojis.joinToString("\n")}"
                } else {
                    "You haven't used enough emojis for a top list yet!"
                }

                event.hook.sendMessage(
                    "✨ **${event.user.name}'s 2025 Wrapup Preview** ✨\n" +
                            "You used **$totalCount** emojis this year,\n" +
                            favoriteEmojiText
                ).queue()
            }
        }
    }
}