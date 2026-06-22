package org.agentnativeos.flagsecuretest

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * A deliberately controlled Tier-2 target for the T0 spike.
 *
 * Its window sets [WindowManager.LayoutParams.FLAG_SECURE]. FLAG_SECURE reliably
 * blocks screenshots; the open question T0 settles is whether it *also* removes
 * this screen's text from the accessibility tree. The screen shows a known marker
 * string and an editable field so the spike can test perceive + type under
 * FLAG_SECURE deterministically, without needing a real banking app.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContentView(R.layout.activity_main)
    }
}
