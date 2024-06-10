package com.timrashard.banana

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.Random

class MainActivity : AppCompatActivity() {
    private lateinit var scoreTextView: TextView
    private lateinit var bananaImageView: ImageView
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var sharedPreferences: SharedPreferences
    private var score: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("banana_pref", Context.MODE_PRIVATE)

        scoreTextView = findViewById(R.id.scoreTextView)
        bananaImageView = findViewById(R.id.bananaImageView)
        constraintLayout = findViewById(R.id.constraintLayout)

        score = sharedPreferences.getInt("score", 0)
        scoreTextView.text = "$score"

        constraintLayout.setOnClickListener {
            incrementScore()
        }


    }

    private fun incrementScore() {
        score++
        scoreTextView.text = "Score: $score"
        saveScore(score)
    }

    private fun saveScore(score: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt("score", score)
        editor.apply()
    }

    fun resetScore(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset Score")
        builder.setMessage("Are you sure you want to reset the score?")
        builder.setPositiveButton("Yes") { dialog, which ->
            score = 0
            scoreTextView.text = "Score: $score"
            saveScore(score)
        }
        builder.setNegativeButton("No", null)
        val dialog = builder.create()
        dialog.show()
    }

    fun changeBackgroundColor(view: View) {
        val rnd = Random()
        val color = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        constraintLayout.setBackgroundColor(color)
    }
}