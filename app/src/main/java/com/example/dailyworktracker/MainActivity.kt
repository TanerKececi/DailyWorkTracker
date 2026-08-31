package com.example.dailyworktracker

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity. It exists purely to host the navigation graph; every screen is a
 * Fragment destination, so there is no per-screen Activity plumbing to maintain.
 *
 * Window insets are handled by the destination layouts themselves rather than here, so each screen
 * can decide what draws edge to edge.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}
