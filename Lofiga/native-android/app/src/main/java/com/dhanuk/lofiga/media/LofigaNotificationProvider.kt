package com.dhanuk.lofiga.media

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList

/**
 * Custom notification provider that renders exactly three buttons:
 * [Previous] [Play/Pause] [Next] — no duplicates.
 *
 * The default provider appends the player's built-in seek-to-previous/next
 * buttons (whenever `player.getAvailableCommands()` contains the seek
 * commands) AND every custom CommandButton in the media button preferences,
 * which caused the notification to show the prev/next icons twice. We override
 * [getMediaButtons] so the built-in row is never added; prev/next are always
 * the custom session commands routed to the app's queue logic via
 * onCustomCommand.
 */
@OptIn(UnstableApi::class)
class LofigaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    companion object {
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREV = "ACTION_PREV"
        private const val COMMAND_KEY_COMPACT_VIEW_INDEX =
            "androidx.media3.session.command.COMPACT_VIEW_INDEX"
    }

    private val prevCommand = SessionCommand(ACTION_PREV, Bundle.EMPTY)
    private val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        val builder = ImmutableList.builder<CommandButton>()

        builder.add(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName("Previous")
                .setSessionCommand(prevCommand)
                .setEnabled(true)
                .setExtras(compactViewExtras(0))
                .build()
        )

        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            builder.add(
                CommandButton.Builder(
                    if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
                )
                    .setDisplayName(if (showPauseButton) "Pause" else "Play")
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setExtras(compactViewExtras(1))
                    .build()
            )
        }

        builder.add(
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("Next")
                .setSessionCommand(nextCommand)
                .setEnabled(true)
                .setExtras(compactViewExtras(2))
                .build()
        )

        return builder.build()
    }

    private fun compactViewExtras(index: Int): Bundle =
        Bundle().apply {
            putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index)
        }
}