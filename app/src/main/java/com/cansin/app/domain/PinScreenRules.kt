package com.cansin.app.domain

/** P24: screen pinning is for passing the phone around during Versus/Together. */
object PinScreenRules {
    /** Leave pinning once no shared-phone session is open, so nobody is stuck on the counter. */
    fun shouldAutoUnpin(pinned: Boolean, sharedSessionOpen: Boolean): Boolean = pinned && !sharedSessionOpen
}
