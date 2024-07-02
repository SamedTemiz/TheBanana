package com.timrashard.banana

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.timrashard.banana.data.AssetManager
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random


class MainActivity : AppCompatActivity() {

    private lateinit var layout: ConstraintLayout
    private lateinit var txtScore: TextView
    private lateinit var imgBanana: ImageView
    private lateinit var imgFun: ImageView
    private lateinit var btnColor: ImageButton
    private lateinit var btnReset: ImageButton
    private lateinit var sharedPreferences: SharedPreferences
    private var score: Long = 0
    private var layoutWidth: Int = 0
    private var layoutHeight: Int = 0
    private val random = Random.Default

    private lateinit var assetManager: AssetManager
    private lateinit var emojiFiles: List<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            init()
            event()
            checkAndDownloadAssets()
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate() hatası: ", e)
        }
    }

    override fun onResume() {
        super.onResume()
        init()
        event()
    }

    private fun init() {
        try {
            sharedPreferences = getSharedPreferences("banana_pref", Context.MODE_PRIVATE)
            assetManager = AssetManager(this)

            layout = findViewById(R.id.constraintLayout)
            layout.post {
                layoutWidth = layout.width
                layoutHeight = layout.height

                loadImagePosition()
            }

            txtScore = findViewById(R.id.txt_score)
            imgBanana = findViewById(R.id.img_banana)
            imgFun = findViewById(R.id.img_fun)
            btnColor = findViewById(R.id.btn_color)
            btnReset = findViewById(R.id.btn_reset)


            val color = sharedPreferences.getInt("color", getColor(R.color.primary))
            layout.setBackgroundColor(color)

            score = sharedPreferences.getLong("score", 0)
            txtScore.text = "$score"

            checkAndDownloadAssets()
        } catch (e: Exception) {
            Log.e("MainActivity", "init() hatası: ", e)
        }
    }

    private fun event() {
        layout.setOnClickListener {
            incrementScore()
        }

        btnReset.setOnClickListener {
            resetScore()
        }

        btnColor.setOnClickListener {
            changeBackgroundColor()
        }

        imgFun.setOnClickListener {
            incrementScore(5)
            setRandomEmoji()
            setRandomImagePosition()
        }
    }

    private fun incrementScore(boost: Long? = null) {
        score += boost ?: 1
        txtScore.text = "$score"
        saveScore(score)
    }

    private fun resetScore() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset Score")
        builder.setMessage("Are you sure you want to reset the score?")
        builder.setPositiveButton("Yes") { _, _ ->
            score = 0
            txtScore.text = "$score"
            saveScore(score)
        }
        builder.setNegativeButton("No", null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun changeBackgroundColor() {
        val color = Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256))
        saveColor(color)
        layout.setBackgroundColor(color)
    }

    private fun checkAndDownloadAssets() {
        try {
            val isDownloaded = sharedPreferences.getBoolean("isDownloaded", false)
            if (!isDownloaded) {
                lifecycleScope.launch {
                    val isSuccess = assetManager.downloadAssets("SamedTemiz", "TheBanana", "emojis")
                    if (isSuccess) {
                        with(sharedPreferences.edit()) {
                            putBoolean("isDownloaded", true)
                            apply()
                        }

                        emojiFiles = loadEmojiFiles()
                        emojiSetup()
                    }
                }
            } else {
                emojiFiles = loadEmojiFiles()
                emojiSetup()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "checkAndDownloadAssets() hatası: ", e)
        }
    }

    private fun loadEmojiFiles(): List<File> {
        return try {
            val emojisDir = File(getExternalFilesDir(null), "emojis")
            if (emojisDir.exists() && emojisDir.isDirectory) {
                emojisDir.listFiles()?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "loadEmojiFiles() hatası: ", e)
            emptyList() // Hata durumunda boş bir liste döndür
        }
    }

    private fun emojiSetup() {
        setRandomEmoji()
        loadImagePosition()
        imgFun.visibility = View.VISIBLE
    }

    private fun setRandomEmoji() {
        try {
            if (emojiFiles.isNotEmpty()) {
                val randomIndex = Random.nextInt(emojiFiles.size)
                val randomEmojiFile = emojiFiles[randomIndex]

                Glide.with(this)
                    .load(randomEmojiFile)
                    .into(imgFun)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "setRandomEmoji() hatası: ", e)
        }
    }

    private fun loadImagePosition() {
        try {
            val startMargin = sharedPreferences.getInt("imgFun_startMargin", -1)
            val bottomMargin = sharedPreferences.getInt("imgFun_bottomMargin", -1)

            if (startMargin != -1 && bottomMargin != -1) {
                val constraintSet = ConstraintSet()
                constraintSet.clone(layout)

                constraintSet.connect(
                    R.id.img_fun,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    startMargin
                )
                constraintSet.connect(
                    R.id.img_fun,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    bottomMargin
                )

                constraintSet.applyTo(layout)
            } else {
                setRandomImagePosition()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "loadImagePosition() hatası: ", e)
        }
    }

    private fun setRandomImagePosition() {
        try {
            val constraintSet = ConstraintSet()
            constraintSet.clone(layout)

            val parentWidth = layout.width
            val parentHeight = layout.height

            val imgWidth = imgFun.width
            val imgHeight = imgFun.height

            val newStartMargin = if (parentWidth > imgWidth) {
                random.nextInt(parentWidth - imgWidth)
            } else {
                0
            }

            val newBottomMargin = if (parentHeight > imgHeight) {
                random.nextInt(parentHeight - imgHeight)
            } else {
                0
            }

            constraintSet.connect(
                R.id.img_fun,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                newStartMargin
            )
            constraintSet.connect(
                R.id.img_fun,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                newBottomMargin
            )

            constraintSet.applyTo(layout)

            saveImagePosition(newStartMargin, newBottomMargin)
        } catch (e: Exception) {
            Log.e("MainActivity", "setRandomImagePosition() hatası: ", e)
        }
    }

    private fun saveScore(score: Long) {
        val editor = sharedPreferences.edit()
        editor.putLong("score", score)
        editor.apply()
    }

    private fun saveColor(color: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt("color", color)
        editor.apply()
    }

    private fun saveImagePosition(startMargin: Int, bottomMargin: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt("imgFun_startMargin", startMargin)
        editor.putInt("imgFun_bottomMargin", bottomMargin)
        editor.apply()
    }
}