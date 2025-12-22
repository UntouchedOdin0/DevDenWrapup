import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.takeWhile
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import java.time.OffsetDateTime

class MessageScanner {

    fun streamChannelMessages(channel: MessageChannel, since: OffsetDateTime): Flow<Message> {
        return channel.iterableHistory
            .asFlow()
            .takeWhile { it.timeCreated.isAfter(since) }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun streamGuildMessages(guild: Guild, since: OffsetDateTime): Flow<Message> {
        return guild.textChannels.asFlow()
            .filter { it.canTalk() }
            // Fetch from 6 channels at once to speed it up
            .flatMapMerge(concurrency = 6) { channel ->
                streamChannelMessages(channel, since)
            }
    }
}