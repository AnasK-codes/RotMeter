package com.rotmeter.app.ui

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rotmeter.app.R
import com.rotmeter.app.db.AppUsage
import com.rotmeter.app.db.DailyRot
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.databinding.FragmentHomeBinding
import com.rotmeter.app.databinding.DialogLevelUpBinding
import com.rotmeter.app.notify.NotificationHelper
import com.rotmeter.app.util.DateUtils
import com.rotmeter.app.util.DemoData
import com.rotmeter.app.util.RotLevel
import com.rotmeter.app.util.Gamification
import com.rotmeter.app.usage.UsagePermission
import com.rotmeter.app.usage.UsageSync
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class HomeFragment : Fragment() {
    private var binding: FragmentHomeBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appAdapter = AppUsageAdapter()
    private val badgesAdapter = BadgesAdapter { showBadgeDialog(it) }
    private var usageDialog: AlertDialog? = null
    private var badgeDialog: AlertDialog? = null
    private var levelDialog: AlertDialog? = null
    private var trophyView: TextView? = null
    private val entranceViews = mutableListOf<View>()
    private var viewActive = AtomicBoolean(false)
    private var waitingForUsageAccess = false
    private var usageAccessGranted = false
    private var refreshGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        waitingForUsageAccess = savedInstanceState?.getBoolean(WAITING_FOR_USAGE_ACCESS) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val viewBinding = FragmentHomeBinding.inflate(inflater, container, false)
        viewActive = AtomicBoolean(true)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding ?: return
        viewBinding.topApps.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.topApps.adapter = appAdapter
        viewBinding.badgesList.layoutManager = GridLayoutManager(requireContext(), 2, RecyclerView.HORIZONTAL, false)
        viewBinding.badgesList.adapter = badgesAdapter
        viewBinding.productiveCredits.text = getString(R.string.productive_credits, 0)
        renderGame(viewBinding, Gamification.calculate(emptyList(), DateUtils.todayString(), 0 to 0, null))
        viewBinding.syncNow.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            syncNow()
        }
        viewBinding.loadDemo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            refresh(loadDemo = true)
        }
        animateEntrance(viewBinding)
    }

    override fun onResume() {
        super.onResume()
        usageAccessGranted = UsagePermission.hasAccess(requireContext())
        val syncAfterGrant = waitingForUsageAccess && usageAccessGranted
        waitingForUsageAccess = false
        refresh(syncUsage = syncAfterGrant)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(WAITING_FOR_USAGE_ACCESS, waitingForUsageAccess)
        super.onSaveInstanceState(outState)
    }

    private fun syncNow() {
        usageAccessGranted = UsagePermission.hasAccess(requireContext())
        if (usageAccessGranted) refresh(syncUsage = true) else showUsageAccessDialog()
    }

    private fun showUsageAccessDialog() {
        if (!isAdded || binding == null || usageDialog?.isShowing == true) return
        usageDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.usage_access_title)
            .setMessage(R.string.usage_access_explanation)
            .setNegativeButton(R.string.not_now) { _, _ ->
                binding?.root?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            .setPositiveButton(R.string.open_usage_settings) { _, _ ->
                binding?.root?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val currentContext = context ?: return@setPositiveButton
                waitingForUsageAccess = true
                try {
                    UsagePermission.openSettings(currentContext)
                } catch (error: ActivityNotFoundException) {
                    waitingForUsageAccess = false
                    Log.e("RotHome", "Usage-access settings unavailable", error)
                    Toast.makeText(currentContext, R.string.usage_settings_error, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun refresh(loadDemo: Boolean = false, syncUsage: Boolean = false) {
        val viewBinding = binding ?: return
        val owner = viewLifecycleOwner
        val token = viewActive
        val appContext = requireContext().applicationContext
        val generation = ++refreshGeneration
        viewBinding.loadDemo.isEnabled = false
        viewBinding.syncNow.isEnabled = false
        viewBinding.errorMessage.isVisible = false
        if (loadDemo) viewBinding.loadDemo.setText(R.string.loading_demo_data)
        viewBinding.syncNow.setText(if (syncUsage) R.string.syncing_usage else R.string.sync_now)

        // Serialize refreshes and seeding across Home instances created by navigation/rotation.
        DbExecutor.executor.execute {
            if (!token.get()) return@execute
            val result = runCatching {
                RotDbHelper(appContext).use { helper ->
                    val repo = RotRepository(helper) { token.get() }
                    if (loadDemo) repo.runInTransaction { DemoData.seed(this) }
                    val syncedApps = if (syncUsage) UsageSync.syncToday(appContext, repo) else null
                    if (!token.get()) return@execute
                    if (loadDemo || syncUsage) NotificationHelper.notifyUnnotifiedAlerts(appContext, repo)
                    val today = DateUtils.todayString()
                    val weeks = repo.getWeekOverWeek(today)
                    val days = repo.getGamificationDays()
                    val topLimit = repo.getTodayTopAppLimit(today)
                    if (!token.get()) return@execute
                    val game = Gamification.calculate(days, today, weeks, topLimit)
                    HomeState(
                        daily = repo.getTodayRot(today),
                        credits = repo.getTotalCredits(today),
                        topApps = repo.getTopRotApps(today, 3),
                        weeks = weeks,
                        hasData = repo.getWorstApp("0001-01-01", today) != null,
                        syncedApps = syncedApps,
                        game = game
                    )
                }
            }
            if (!token.get()) return@execute
            mainHandler.post {
                if (!token.get() || !isAdded || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@post
                if (generation != refreshGeneration) return@post
                val currentBinding = binding ?: return@post
                currentBinding.loadDemo.isEnabled = true
                currentBinding.loadDemo.setText(R.string.load_demo_data)
                currentBinding.syncNow.isEnabled = true
                currentBinding.syncNow.setText(R.string.sync_now)
                result.fold(
                    onSuccess = { state ->
                        render(currentBinding, state)
                        state.syncedApps?.let { count ->
                            if (count == -1) {
                                usageAccessGranted = false
                                showUsageAccessDialog()
                            } else {
                                Toast.makeText(
                                    requireContext(), getString(R.string.synced_apps, count), Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onFailure = {
                        Log.e("RotHome", "Could not load Home data", it)
                        currentBinding.errorMessage.isVisible = true
                    }
                )
            }
        }
    }

    private fun render(viewBinding: FragmentHomeBinding, state: HomeState) {
        val score = state.daily.rotScore
        val level = RotLevel.fromScore(score)
        val color = ContextCompat.getColor(requireContext(), level.colorRes)
        viewBinding.rotScore.text = getString(R.string.score_number, score)
        viewBinding.rotScore.setTextColor(color)
        viewBinding.levelLabel.setText(level.labelRes)
        viewBinding.levelLabel.setTextColor(color)
        viewBinding.rotRing.setScore(score, getString(level.labelRes), color)
        viewBinding.roast.setText(level.roastRes)
        viewBinding.rotMeter.setIndicatorColor(color)
        viewBinding.rotMeter.progress = score.coerceIn(0, 300)
        viewBinding.rotMeter.contentDescription = getString(
            R.string.meter_description, score, getString(level.labelRes)
        )
        viewBinding.productiveCredits.text = getString(R.string.productive_credits, state.credits)
        viewBinding.emptyState.isVisible = !state.hasData
        appAdapter.submitList(state.topApps) {
            if (binding === viewBinding) viewBinding.topApps.scheduleLayoutAnimation()
        }
        viewBinding.noRotApps.isVisible = state.hasData && state.topApps.isEmpty()
        val (thisWeek, lastWeek) = state.weeks
        val change = when {
            lastWeek != 0 -> String.format(
                Locale.US, "%+.0f%%", (thisWeek.toDouble() - lastWeek) * 100.0 / lastWeek
            )
            thisWeek == 0 -> getString(R.string.week_change_zero)
            else -> getString(R.string.week_change_no_baseline)
        }
        viewBinding.weekComparison.text = getString(R.string.week_over_week, thisWeek, lastWeek, change)
        renderGame(viewBinding, state.game)
        checkLevelUp(state.game.player)
    }

    private fun renderGame(viewBinding: FragmentHomeBinding, game: Gamification.State) {
        val player = game.player
        viewBinding.avatarLevel.text = getString(R.string.score_number, player.level)
        viewBinding.playerLevel.text = getString(R.string.player_level, player.level)
        viewBinding.playerTitle.setText(player.titleRes)
        viewBinding.xpProgress.max = Gamification.XP_PER_LEVEL
        viewBinding.xpProgress.progress = player.xpInLevel
        viewBinding.xpText.text = getString(R.string.player_xp, player.xpInLevel, Gamification.XP_PER_LEVEL)
        viewBinding.xpProgress.contentDescription = getString(R.string.player_xp_description, player.totalXp, player.xpInLevel)
        viewBinding.streakChip.text = resources.getQuantityString(R.plurals.clean_day_streak, game.currentStreak, game.currentStreak)
        viewBinding.bestStreak.text = getString(R.string.best_streak, game.bestStreak)
        viewBinding.questProductiveDetail.text = getString(R.string.quest_productive_progress,
            String.format(Locale.US, "%.1f", game.productiveMinutes))
        viewBinding.questLimitDetail.text = game.topApp?.let { top ->
            top.dailyLimitMin?.let { getString(R.string.quest_limit_progress, top.app.label, top.app.minutes, it) }
                ?: getString(R.string.quest_set_limit)
        } ?: getString(R.string.quest_no_top_app)
        viewBinding.questRoastsDetail.text = getString(R.string.quest_roasts_count, game.todayAlertCount)
        styleQuest(viewBinding.questProductiveCard, viewBinding.questProductiveIcon, viewBinding.questProductiveProgress, game.productiveQuest)
        styleQuest(viewBinding.questLimitCard, viewBinding.questLimitIcon, viewBinding.questLimitProgress, game.limitQuest)
        styleQuest(viewBinding.questRoastsCard, viewBinding.questRoastsIcon, viewBinding.questRoastsProgress, game.roastQuest)
        badgesAdapter.submitList(game.badges)
        viewBinding.badgesList.scheduleLayoutAnimation()
    }

    private fun styleQuest(card: MaterialCardView, icon: ImageView, progress: LinearProgressIndicator, quest: Gamification.Quest) {
        val context = requireContext()
        val tint = ContextCompat.getColor(context, if (quest.complete) R.color.rot_fresh else R.color.rot_neon_purple)
        card.setCardBackgroundColor(ContextCompat.getColor(context,
            if (quest.complete) R.color.quest_success_surface else R.color.rot_surface))
        card.setStrokeColor(ContextCompat.getColor(context,
            if (quest.complete) R.color.quest_success_stroke else R.color.rot_stroke))
        icon.setImageResource(if (quest.complete) R.drawable.ic_check else R.drawable.ic_quest)
        icon.setColorFilter(tint)
        icon.contentDescription = getString(if (quest.complete) R.string.quest_complete_description else R.string.quest_pending_description)
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        progress.setIndicatorColor(tint)
        progress.progress = quest.progress
    }

    private fun animateEntrance(viewBinding: FragmentHomeBinding) {
        val token = viewActive
        entranceViews.addAll(listOf(viewBinding.playerCard, viewBinding.rotCard, viewBinding.creditsCard,
            viewBinding.questsCard, viewBinding.badgesCard, viewBinding.weekCard))
        entranceViews.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 20f * resources.displayMetrics.density
            mainHandler.postDelayed({
                if (token.get()) card.animate().alpha(1f).translationY(0f)
                    .setDuration(280L).setInterpolator(DecelerateInterpolator()).start()
            }, index * 60L)
        }
    }

    private fun showBadgeDialog(item: Gamification.BadgeState) {
        val viewBinding = binding ?: return
        if (!viewActive.get() || !isAdded || !isResumed) return
        viewBinding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        badgeDialog?.dismiss()
        badgeDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.badge.nameRes)
            .setMessage(getString(R.string.badge_dialog_message, getString(item.badge.descriptionRes),
                getString(if (item.unlocked) R.string.badge_unlocked else R.string.badge_locked)))
            .setPositiveButton(R.string.close_dialog) { _, _ -> binding?.root?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            .show()
    }

    private fun checkLevelUp(player: Gamification.Player) {
        if (!viewActive.get() || !isResumed || binding == null) return
        val preferences = requireContext().getSharedPreferences("rotmeter_gamification", android.content.Context.MODE_PRIVATE)
        val previous = preferences.getLong("last_seen_player_level", 1L)
        preferences.edit().putLong("last_seen_player_level", player.level).apply()
        if (player.level <= previous) return
        levelDialog?.dismiss()
        val dialogBinding = DialogLevelUpBinding.inflate(layoutInflater)
        dialogBinding.newLevel.text = getString(R.string.level_up_message, player.level)
        dialogBinding.newTitle.setText(player.titleRes)
        val trophy = dialogBinding.trophy
        trophyView = trophy
        trophy.scaleX = 0.35f
        trophy.scaleY = 0.35f
        trophy.alpha = 0f
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.level_up_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.close_dialog) { _, _ -> binding?.root?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            .create()
        levelDialog = dialog
        dialog.setOnShowListener {
            if (viewActive.get()) trophy.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(420L).setInterpolator(OvershootInterpolator(1.5f)).start()
        }
        dialog.setOnDismissListener {
            trophy.animate().cancel()
            if (levelDialog === dialog) {
                levelDialog = null
                trophyView = null
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        viewActive.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        entranceViews.forEach { it.animate().cancel() }
        entranceViews.clear()
        binding?.rotRing?.stopAnimation()
        trophyView?.animate()?.cancel()
        trophyView = null
        badgeDialog?.dismiss()
        badgeDialog = null
        levelDialog?.dismiss()
        levelDialog = null
        usageDialog?.dismiss()
        usageDialog = null
        binding?.topApps?.adapter = null
        binding?.badgesList?.adapter = null
        binding = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private data class HomeState(
        val daily: DailyRot,
        val credits: Int,
        val topApps: List<AppUsage>,
        val weeks: Pair<Int, Int>,
        val hasData: Boolean,
        val syncedApps: Int?,
        val game: Gamification.State
    )

    companion object {
        private const val WAITING_FOR_USAGE_ACCESS = "waiting_for_usage_access"
    }
}
