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
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.databinding.FragmentAlertsBinding

class AlertsFragment : Fragment() {
    private var binding: FragmentAlertsBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val alertsAdapter = AlertsAdapter()
    private var refreshGeneration = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val viewBinding = FragmentAlertsBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding ?: return
        viewBinding.alertsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.alertsList.adapter = alertsAdapter
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
        viewBinding.emptyState.isVisible = false
        viewBinding.emptyRoastsContent.isVisible = false
        DbExecutor.executor.execute {
            val result = runCatching {
                RotDbHelper(appContext).use { helper -> RotRepository(helper).getAlerts() }
            }
            mainHandler.post {
                if (!isAdded || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@post
                if (generation != refreshGeneration) return@post
                val currentBinding = binding ?: return@post
                currentBinding.loading.isVisible = false
                result.fold(
                    onSuccess = { alerts ->
                        alertsAdapter.submitList(alerts) {
                            if (binding === currentBinding) currentBinding.alertsList.scheduleLayoutAnimation()
                        }
                        currentBinding.alertsList.isVisible = alerts.isNotEmpty()
                        currentBinding.emptyState.isVisible = alerts.isEmpty()
                        currentBinding.emptyRoastsContent.isVisible = alerts.isEmpty()
                    },
                    onFailure = {
                        Log.e("RotAlerts", "Could not load alerts", it)
                        currentBinding.errorMessage.isVisible = true
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        binding?.alertsList?.adapter = null
        binding = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}
