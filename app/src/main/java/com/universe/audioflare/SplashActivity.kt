package com.universe.audioflare

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Delay for 2 seconds
        window.decorView.postDelayed({
            // Start the main activity
            startActivity(Intent(this, MainActivity::class.java))
            // Finish the splash activity so the user can't go back to it
            finish()
        }, 2000) // 2000 milliseconds = 2 seconds
    }
}
