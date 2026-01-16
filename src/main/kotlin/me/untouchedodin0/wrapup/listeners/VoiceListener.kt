package me.untouchedodin0.wrapup.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.untouchedodin0.wrapup.tables.VoiceSessionsTable
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

class VoiceListener(private val scope: CoroutineScope) : ListenerAdapter() {

    // Temporary storage: UserID -> JoinTime
    private val joinCache = ConcurrentHashMap<Long, Long>()

    // 1. THE SYNC: Runs once when the bot starts/joins a server
    override fun onGuildReady(event: GuildReadyEvent) {
        event.guild.voiceChannels.forEach { channel ->
            channel.members.forEach { member ->
                // Don't track bots
                if (!member.user.isBot) {
                    joinCache[member.idLong] = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        if (event.member.user.isBot) return

        val userID = event.member.idLong
        val now = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            // User leaves or moves
            if (event.channelLeft != null) {
                val startTime = joinCache.remove(userID)
                if (startTime != null) {
                    saveVoiceSession(
                        userID,
                        event.channelLeft!!.idLong,
                        start = startTime,
                        end = now
                    )
//                    val durationMillis = now - startTime

                    // Format the duration into a readable string (e.g., 01h 05m 10s)
//                    val duration = durationMillis.milliseconds


                    // Keep your database save here too!
//                    saveVoiceSession(userID, startTime, now)
                }
            }

            if (event.channelJoined != null) {
                joinCache[userID] = now
            }
        }
    }

    private fun saveVoiceSession(userId: Long, channelId: Long, start: Long, end: Long) {
        val sessionDuration = end - start
        if (sessionDuration < 5000) return

        transaction {
            VoiceSessionsTable.insert {
                it[this.userId] = userId
                it[this.channelId] = channelId
                it[this.startTime] = start
                it[this.endTime] = end
                it[this.duration] = sessionDuration // Store the pre-calculated time

                println("Saved session: UserID=$userId, Channel=$channelId, Duration=$sessionDuration")
            }
        }
    }
}