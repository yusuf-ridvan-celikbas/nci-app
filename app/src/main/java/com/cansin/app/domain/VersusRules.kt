package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side

data class VersusSetup(
    val redName: String? = null,
    val blueName: String? = null,
    val redFriendId: Long? = null,
    val blueFriendId: Long? = null,
    val meSide: Side? = null,
)

data class TabTotals(val red: Long = 0, val blue: Long = 0, val shared: Long = 0) {
    val total: Long get() = red + blue + shared
}

object VersusRules {
    fun cleanName(raw: String?): String? = raw?.trim()?.take(FriendProfile.MAX_NAME)?.ifEmpty { null }

    fun apply(session: Session, setup: VersusSetup): Session = session.copy(
        redName = cleanName(setup.redName),
        blueName = cleanName(setup.blueName),
        redFriendId = setup.redFriendId,
        blueFriendId = setup.blueFriendId,
        meSide = setup.meSide?.takeIf { it != Side.SOLO },
    )

    fun fits(side: Side, mode: SessionMode?): Boolean =
        if (side == Side.SOLO) mode != SessionMode.VERSUS else mode == SessionMode.VERSUS

    fun meSide(session: Session?): Side? =
        if (session?.mode == SessionMode.VERSUS) session.meSide else Side.SOLO

    fun isMine(event: BeerEvent, session: Session): Boolean = event.side == meSide(session)

    fun noteSide(side: Side?, mode: SessionMode): Side? =
        side?.takeIf { mode == SessionMode.VERSUS && it != Side.SOLO }

    fun totals(notes: List<Note>): TabTotals = notes.fold(TabTotals()) { acc, note ->
        val amount = note.amountMinor ?: 0
        when (note.side) {
            Side.RED -> acc.copy(red = acc.red + amount)
            Side.BLUE -> acc.copy(blue = acc.blue + amount)
            else -> acc.copy(shared = acc.shared + amount)
        }
    }
}
