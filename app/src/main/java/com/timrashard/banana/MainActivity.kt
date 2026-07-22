package com.timrashard.banana

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.timrashard.banana.data.AssetManager
import com.timrashard.banana.game.EmojiGameController
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
    private lateinit var btnPrivacy: ImageButton
    private lateinit var btnRewardedAd: MaterialCardView
    private lateinit var txtRewardTitle: TextView
    private lateinit var txtRewardSubtitle: TextView
    private lateinit var boostProgress: LinearProgressIndicator
    private lateinit var sharedPreferences: SharedPreferences
    private var score: Long = 0
    private val random = Random.Default

    private lateinit var assetManager: AssetManager
    private lateinit var emojiFiles: List<File>
    private lateinit var emojiGameController: EmojiGameController
    private lateinit var consentInformation: ConsentInformation
    private var rewardedAd: RewardedAd? = null
    private var isRewardedAdLoading = false
    private var adsInitializationStarted = false
    private var boostEndsAtElapsedRealtime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val boostTicker = object : Runnable {
        override fun run() {
            updateRewardButton()
            if (isBoostActive()) {
                mainHandler.postDelayed(this, BOOST_TICK_MILLIS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            init()
            event()
            checkAndDownloadAssets()
            requestConsentAndInitializeAds()
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate() hatası: ", e)
        }
    }

    private fun init() {
        try {
            sharedPreferences = getSharedPreferences("banana_pref", Context.MODE_PRIVATE)
            assetManager = AssetManager(this)

            layout = findViewById(R.id.constraintLayout)
            txtScore = findViewById(R.id.txt_score)
            imgBanana = findViewById(R.id.img_banana)
            imgFun = findViewById(R.id.img_fun)
            btnColor = findViewById(R.id.btn_color)
            btnReset = findViewById(R.id.btn_reset)
            btnPrivacy = findViewById(R.id.btn_privacy)
            btnRewardedAd = findViewById(R.id.btn_rewarded_ad)
            txtRewardTitle = findViewById(R.id.txt_reward_title)
            txtRewardSubtitle = findViewById(R.id.txt_reward_subtitle)
            boostProgress = findViewById(R.id.boost_progress)
            val emojiStatus: TextView = findViewById(R.id.txt_emoji_status)
            val comboStatus: TextView = findViewById(R.id.txt_combo)
            emojiGameController = EmojiGameController(
                activity = this,
                root = layout,
                target = imgFun,
                rarityStatus = emojiStatus,
                comboStatus = comboStatus,
                protectedViews = listOf(
                    txtScore,
                    emojiStatus,
                    comboStatus,
                    imgBanana,
                    btnReset,
                    btnColor,
                    btnPrivacy,
                    btnRewardedAd
                ),
                onReward = ::incrementScore
            )

            ViewCompat.setOnApplyWindowInsetsListener(layout) { view, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
                windowInsets
            }


            val color = sharedPreferences.getInt("color", getColor(R.color.primary))
            layout.setBackgroundColor(color)

            score = sharedPreferences.getLong("score", 0)
            txtScore.text = "$score"

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

        btnRewardedAd.setOnClickListener {
            showRewardedAd()
        }

        btnPrivacy.setOnClickListener {
            UserMessagingPlatform.showPrivacyOptionsForm(this) { formError ->
                if (formError != null) {
                    Log.w(TAG, "Privacy options form error: ${formError.message}")
                }
                updatePrivacyButton()
                initializeAdsIfAllowed()
            }
        }
    }

    private fun incrementScore(boost: Long? = null) {
        val baseScore = boost ?: 1
        val multiplier = if (isBoostActive()) BOOST_MULTIPLIER else 1L
        score += baseScore * multiplier
        txtScore.text = "$score"
        saveScore(score)
    }

    private fun requestConsentAndInitializeAds() {
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        val requestParameters = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            this,
            requestParameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    updatePrivacyButton()
                    initializeAdsIfAllowed()
                }
                updatePrivacyButton()
                initializeAdsIfAllowed()
            },
            { requestError ->
                Log.w(TAG, "Consent update error: ${requestError.message}")
                updatePrivacyButton()
                initializeAdsIfAllowed()
            }
        )
    }

    private fun initializeAdsIfAllowed() {
        if (!consentInformation.canRequestAds() || adsInitializationStarted) return

        adsInitializationStarted = true
        MobileAds.initialize(this) {
            runOnUiThread { loadRewardedAd() }
        }
    }

    private fun loadRewardedAd() {
        if (!::consentInformation.isInitialized ||
            !consentInformation.canRequestAds() ||
            !adsInitializationStarted
        ) return
        if (isRewardedAdLoading || rewardedAd != null) return

        isRewardedAdLoading = true
        updateRewardButton()
        RewardedAd.load(
            this,
            BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isRewardedAdLoading = false
                    rewardedAd = ad
                    updateRewardButton()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isRewardedAdLoading = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                    updateRewardButton()
                }
            }
        )
    }

    private fun showRewardedAd() {
        if (isBoostActive()) return
        val ad = rewardedAd
        if (ad == null) {
            Toast.makeText(this, R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
            loadRewardedAd()
            return
        }

        rewardedAd = null
        updateRewardButton()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                loadRewardedAd()
            }
        }
        ad.show(this) {
            boostEndsAtElapsedRealtime = SystemClock.elapsedRealtime() + BOOST_DURATION_MILLIS
            Toast.makeText(this, R.string.boost_earned, Toast.LENGTH_SHORT).show()
            mainHandler.removeCallbacks(boostTicker)
            mainHandler.post(boostTicker)
        }
    }

    private fun isBoostActive(): Boolean =
        SystemClock.elapsedRealtime() < boostEndsAtElapsedRealtime

    private fun updateRewardButton() {
        if (isBoostActive()) {
            val remainingMillis =
                boostEndsAtElapsedRealtime - SystemClock.elapsedRealtime()
            val secondsRemaining = (remainingMillis + 999L) / 1_000L
            val progress = ((remainingMillis * 100L) / BOOST_DURATION_MILLIS)
                .toInt()
                .coerceIn(0, 100)
            btnRewardedAd.isClickable = false
            btnRewardedAd.alpha = 1f
            txtRewardTitle.setText(R.string.boost_active_title)
            txtRewardSubtitle.text = getString(R.string.boost_remaining, secondsRemaining)
            boostProgress.visibility = View.VISIBLE
            boostProgress.setProgressCompat(progress, true)
            return
        }

        boostProgress.visibility = View.GONE
        txtRewardTitle.setText(R.string.boost_title)
        btnRewardedAd.alpha = 1f
        if (rewardedAd != null) {
            btnRewardedAd.isClickable = true
            txtRewardSubtitle.setText(R.string.watch_ad_details)
        } else {
            btnRewardedAd.isClickable = adsInitializationStarted && !isRewardedAdLoading
            txtRewardSubtitle.setText(
                if (adsInitializationStarted && !isRewardedAdLoading) {
                    R.string.ad_unavailable_retry
                } else {
                    R.string.ad_loading
                }
            )
        }
    }

    private fun updatePrivacyButton() {
        btnPrivacy.visibility =
            if (consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            ) View.VISIBLE else View.GONE
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
        emojiGameController.start(emojiFiles)
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

    override fun onStart() {
        super.onStart()
        if (::emojiGameController.isInitialized) {
            emojiGameController.resume()
        }
    }

    override fun onStop() {
        if (::emojiGameController.isInitialized) {
            emojiGameController.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(boostTicker)
        if (::emojiGameController.isInitialized) {
            emojiGameController.pause()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val BOOST_DURATION_MILLIS = 60_000L
        private const val BOOST_TICK_MILLIS = 1_000L
        private const val BOOST_MULTIPLIER = 2L
    }
}
