package fr.cartes.multiplications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ear = Ear(this)
        game = Game(Speaker(this), ear, Progress(this))
        setContent {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                ear.micAllowed = granted
            }
            LaunchedEffect(Unit) {
                val has = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (has) ear.micAllowed = true else launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
            App(game)
        }
    }

    override fun onStart() {
        super.onStart()
        game.resume()
    }

    override fun onStop() {
        super.onStop()
        game.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        game.release()
    }
}
