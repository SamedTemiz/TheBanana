package com.timrashard.banana.game

import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.timrashard.banana.R
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class EmojiGameController(
    private val activity: AppCompatActivity,
    private val root: ConstraintLayout,
    private val target: ImageView,
    private val rarityStatus: TextView,
    private val comboStatus: TextView,
    private val protectedViews: List<View>,
    private val onReward: (Long) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random.Default
    private var files: List<File> = emptyList()
    private var activeRarity = Rarity.COMMON
    private var targetIsActive = false
    private var paused = true
    private var combo = 0

    private val spawnRunnable = Runnable { spawnTarget() }
    private val missRunnable = Runnable { missTarget() }

    init {
        target.setOnClickListener {
            catchTarget()
        }
    }

    fun start(emojiFiles: List<File>) {
        pause()
        files = emojiFiles.filter { it.isFile }
        combo = 0
        updateComboStatus()
        preloadSomeTargets()
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            paused = false
            scheduleNextSpawn(FIRST_SPAWN_DELAY_MILLIS)
        }
    }

    fun resume() {
        if (!paused || files.isEmpty()) return
        paused = false
        scheduleNextSpawn(FIRST_SPAWN_DELAY_MILLIS)
    }

    fun pause() {
        paused = true
        handler.removeCallbacks(spawnRunnable)
        handler.removeCallbacks(missRunnable)
        target.animate().cancel()
        targetIsActive = false
        target.visibility = View.GONE
        rarityStatus.visibility = View.GONE
    }

    private fun preloadSomeTargets() {
        files.shuffled().take(PRELOAD_TARGET_COUNT).forEach { file ->
            Glide.with(activity)
                .load(file)
                .preload(dp(TARGET_SIZE_DP), dp(TARGET_SIZE_DP))
        }
    }

    private fun spawnTarget() {
        if (paused || files.isEmpty()) return
        if (root.width == 0 || root.height == 0) {
            scheduleNextSpawn(LAYOUT_RETRY_DELAY_MILLIS)
            return
        }

        handler.removeCallbacks(missRunnable)
        target.animate().cancel()
        activeRarity = chooseRarity()
        val file = files[random.nextInt(files.size)]
        val start = findSafePosition()

        Glide.with(activity).load(file).into(target)
        target.contentDescription = activity.getString(
            R.string.emoji_target_description,
            activity.getString(activeRarity.labelRes),
            activeRarity.reward
        )
        target.x = start.x
        target.y = start.y
        target.alpha = 0f
        target.scaleX = ENTRY_SCALE
        target.scaleY = ENTRY_SCALE
        target.rotation = 0f
        target.visibility = View.VISIBLE
        target.bringToFront()
        targetIsActive = true
        showRarityStatus(activeRarity)

        target.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ENTRY_ANIMATION_MILLIS)
            .withEndAction {
                if (targetIsActive && shouldMove(file)) {
                    moveTarget(activeRarity.visibleMillis - ENTRY_ANIMATION_MILLIS)
                }
            }
            .start()

        handler.postDelayed(missRunnable, activeRarity.visibleMillis)
    }

    private fun moveTarget(durationMillis: Long) {
        val destination = findSafePosition()
        target.animate()
            .x(destination.x)
            .y(destination.y)
            .setInterpolator(LinearInterpolator())
            .setDuration(max(MIN_MOVEMENT_DURATION_MILLIS, durationMillis))
            .start()
    }

    private fun catchTarget() {
        if (!targetIsActive || paused) return

        targetIsActive = false
        handler.removeCallbacks(missRunnable)
        target.animate().cancel()
        target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        combo += 1
        val comboMultiplier = min(MAX_COMBO_MULTIPLIER, 1 + (combo - 1) / CATCHES_PER_MULTIPLIER)
        val reward = activeRarity.reward * comboMultiplier
        onReward(reward)
        updateComboStatus(comboMultiplier)

        rarityStatus.text = activity.getString(R.string.emoji_reward_earned, reward)
        target.animate()
            .alpha(0f)
            .scaleX(CATCH_SCALE)
            .scaleY(CATCH_SCALE)
            .rotationBy(CATCH_ROTATION_DEGREES)
            .setDuration(CATCH_ANIMATION_MILLIS)
            .withEndAction {
                target.visibility = View.GONE
                rarityStatus.visibility = View.GONE
                scheduleNextSpawn(AFTER_CATCH_DELAY_MILLIS)
            }
            .start()
    }

    private fun missTarget() {
        if (!targetIsActive) return

        targetIsActive = false
        target.animate().cancel()
        combo = 0
        updateComboStatus()
        target.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setDuration(EXIT_ANIMATION_MILLIS)
            .withEndAction {
                target.visibility = View.GONE
                rarityStatus.visibility = View.GONE
                scheduleNextSpawn(randomSpawnDelay())
            }
            .start()
    }

    private fun showRarityStatus(rarity: Rarity) {
        rarityStatus.setTextColor(ContextCompat.getColor(activity, rarity.colorRes))
        rarityStatus.text = activity.getString(
            R.string.emoji_rarity_reward,
            activity.getString(rarity.labelRes),
            rarity.reward
        )
        rarityStatus.visibility = View.VISIBLE
    }

    private fun updateComboStatus(multiplier: Int = 1) {
        if (combo < 2) {
            comboStatus.visibility = View.GONE
            return
        }
        comboStatus.text = activity.getString(R.string.emoji_combo, combo, multiplier)
        comboStatus.visibility = View.VISIBLE
    }

    private fun chooseRarity(): Rarity {
        val roll = random.nextInt(100)
        return when {
            roll < 65 -> Rarity.COMMON
            roll < 90 -> Rarity.RARE
            roll < 98 -> Rarity.EPIC
            else -> Rarity.LEGENDARY
        }
    }

    private fun shouldMove(file: File): Boolean =
        file.extension.equals("gif", ignoreCase = true) &&
            random.nextInt(100) < MOVING_GIF_PERCENT

    private fun findSafePosition(): PointF {
        val targetWidth = max(target.width, dp(TARGET_SIZE_DP))
        val targetHeight = max(target.height, dp(TARGET_SIZE_DP))
        val minX = root.paddingLeft + dp(EDGE_PADDING_DP)
        val maxX = max(minX, root.width - root.paddingRight - targetWidth - dp(EDGE_PADDING_DP))
        val minY = root.paddingTop + dp(TOP_SAFE_AREA_DP)
        val maxY = max(
            minY,
            root.height - root.paddingBottom - targetHeight - dp(BOTTOM_SAFE_AREA_DP)
        )
        var fallback = PointF(minX.toFloat(), minY.toFloat())

        repeat(POSITION_ATTEMPTS) {
            val x = randomBetween(minX, maxX)
            val y = randomBetween(minY, maxY)
            fallback = PointF(x.toFloat(), y.toFloat())
            val candidate = Rect(x, y, x + targetWidth, y + targetHeight)
            if (protectedViews.none { candidate.intersects(expandedBounds(it)) }) {
                return fallback
            }
        }
        return fallback
    }

    private fun expandedBounds(view: View): Rect {
        if (view.visibility != View.VISIBLE) return Rect()
        val gap = dp(PROTECTED_VIEW_GAP_DP)
        return Rect(
            view.x.toInt() - gap,
            view.y.toInt() - gap,
            (view.x + view.width).toInt() + gap,
            (view.y + view.height).toInt() + gap
        )
    }

    private fun Rect.intersects(other: Rect): Boolean =
        !other.isEmpty && Rect.intersects(this, other)

    private fun randomBetween(minimum: Int, maximum: Int): Int =
        if (maximum <= minimum) minimum else random.nextInt(minimum, maximum + 1)

    private fun randomSpawnDelay(): Long =
        random.nextLong(MIN_SPAWN_DELAY_MILLIS, MAX_SPAWN_DELAY_MILLIS + 1)

    private fun scheduleNextSpawn(delayMillis: Long) {
        if (paused || files.isEmpty()) return
        handler.removeCallbacks(spawnRunnable)
        handler.postDelayed(spawnRunnable, delayMillis)
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private enum class Rarity(
        @StringRes val labelRes: Int,
        @ColorRes val colorRes: Int,
        val reward: Long,
        val visibleMillis: Long
    ) {
        COMMON(R.string.rarity_common, R.color.rarity_common, 5L, 4_500L),
        RARE(R.string.rarity_rare, R.color.rarity_rare, 25L, 4_000L),
        EPIC(R.string.rarity_epic, R.color.rarity_epic, 100L, 3_500L),
        LEGENDARY(R.string.rarity_legendary, R.color.rarity_legendary, 500L, 3_000L)
    }

    private companion object {
        const val TARGET_SIZE_DP = 48
        const val EDGE_PADDING_DP = 12
        const val TOP_SAFE_AREA_DP = 72
        const val BOTTOM_SAFE_AREA_DP = 76
        const val PROTECTED_VIEW_GAP_DP = 12
        const val POSITION_ATTEMPTS = 40
        const val PRELOAD_TARGET_COUNT = 8
        const val MOVING_GIF_PERCENT = 60
        const val CATCHES_PER_MULTIPLIER = 3
        const val MAX_COMBO_MULTIPLIER = 5
        const val FIRST_SPAWN_DELAY_MILLIS = 700L
        const val LAYOUT_RETRY_DELAY_MILLIS = 250L
        const val MIN_SPAWN_DELAY_MILLIS = 1_500L
        const val MAX_SPAWN_DELAY_MILLIS = 3_500L
        const val AFTER_CATCH_DELAY_MILLIS = 650L
        const val ENTRY_ANIMATION_MILLIS = 180L
        const val EXIT_ANIMATION_MILLIS = 160L
        const val CATCH_ANIMATION_MILLIS = 220L
        const val MIN_MOVEMENT_DURATION_MILLIS = 1_000L
        const val ENTRY_SCALE = 0.65f
        const val EXIT_SCALE = 0.7f
        const val CATCH_SCALE = 1.6f
        const val CATCH_ROTATION_DEGREES = 25f
    }
}
