package com.rotmeter.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.rotmeter.app.R
import com.rotmeter.app.db.AppUsage
import com.rotmeter.app.db.DayScore
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.databinding.FragmentStatsBinding
import com.rotmeter.app.util.DateUtils
import java.time.LocalDate

class StatsFragment : Fragment() {
    private var binding: FragmentStatsBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appAdapter = AppUsageAdapter(showWeeklyShare = true)
    private var refreshGeneration = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val viewBinding = FragmentStatsBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding ?: return
        viewBinding.appTotals.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.appTotals.adapter = appAdapter
        viewBinding.beatAverage.text = getString(R.string.beat_average_days, 0)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val viewBinding = binding ?: return
        val owner = viewLifecycleOwner
        val appContext = requireContext().applicationContext
        val generation = ++refreshGeneration
        viewBinding.loading.isVisible = true
        viewBinding.errorMessage.isVisible = false
        DbExecutor.executor.execute {
            val result = runCatching {
                RotDbHelper(appContext).use { helper ->
                    val repo = RotRepository(helper)
                    val end = DateUtils.todayString()
                    val start = LocalDate.parse(end).minusDays(6).format(DateUtils.formatter)
                    StatsState(
                        scores = repo.getWeekScores(end),
                        worstApp = repo.getWorstApp(start, end),
                        beatAverageDays = repo.getBeatAverageDays(),
                        totals = repo.getWeekTotalsPerApp(start, end)
                    )
                }
            }
            mainHandler.post {
                if (!isAdded || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@post
                if (generation != refreshGeneration) return@post
                val currentBinding = binding ?: return@post
                currentBinding.loading.isVisible = false
                result.fold(
                    onSuccess = { state ->
                        currentBinding.weekChart.setScores(state.scores)
                        currentBinding.worstAppInitial.isVisible = state.worstApp != null
                        state.worstApp?.let { AppVisuals.showInitial(currentBinding.worstAppInitial, it.label, it.rotWeight) }
                        currentBinding.worstApp.text = state.worstApp?.let {
                            getString(R.string.worst_app_details, it.label, it.minutes)
                        } ?: getString(R.string.no_week_usage)
                        currentBinding.beatAverage.text = getString(
                            R.string.beat_average_days, state.beatAverageDays
                        )
                        appAdapter.submitList(state.totals) {
                            if (binding === currentBinding) currentBinding.appTotals.scheduleLayoutAnimation()
                        }
                        currentBinding.emptyTotals.isVisible = state.totals.isEmpty()
                    },
                    onFailure = {
                        Log.e("RotStats", "Could not load stats", it)
                        currentBinding.errorMessage.isVisible = true
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        binding?.weekChart?.stopAnimation()
        binding?.appTotals?.adapter = null
        binding = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private data class StatsState(
        val scores: List<DayScore>,
        val worstApp: AppUsage?,
        val beatAverageDays: Int,
        val totals: List<AppUsage>
    )
}
