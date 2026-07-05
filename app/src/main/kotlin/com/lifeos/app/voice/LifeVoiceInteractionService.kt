package com.lifeos.app.voice

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.lifeos.app.MainActivity

/**
 * Registers LifeOS as a digital-assistant app (§Module 10). Long-press
 * home/power-assist opens the quick-capture overlay with voice ready —
 * the "just get it out of my head" path from anywhere on the device.
 */
class LifeVoiceInteractionService : VoiceInteractionService()

class LifeVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LifeVoiceSession(this)
}

class LifeVoiceSession(service: VoiceInteractionSessionService) : VoiceInteractionSession(service) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Alpha assist surface: hand off to the in-app capture sheet (voice mic
        // one tap away); a floating session window replaces this later.
        startAssistantActivity(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_QUICK_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        hide()
    }
}
