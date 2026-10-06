package com.rotmeter.app.ui

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rotmeter.app.R
import com.rotmeter.app.db.AppInfo
import com.rotmeter.app.blocking.BlockingPreferences
import com.rotmeter.app.blocking.BlockingAccess
import com.rotmeter.app.usage.UsagePermission
import com.rotmeter.app.util.DateUtils
import com.rotmeter.app.db.DbExecutor
import com.rotmeter.app.db.RotDbHelper
import com.rotmeter.app.db.RotRepository
import com.rotmeter.app.databinding.DialogLimitBinding
import com.rotmeter.app.databinding.FragmentLimitsBinding

class LimitsFragment : Fragment() {
    private var binding: FragmentLimitsBinding? = null
    private var limitDialog: AlertDialog? = null
    private var blockingDialog: AlertDialog? = null
    private var updatingBlockingControls = false
    private var isBusy = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val limitsAdapter = LimitsAdapter(onSelectionChanged = { app, selected ->
        context?.let { BlockingPreferences.setSelected(it, app.packageName, selected) }
    }) { app -> if (!isBusy) showLimitDialog(app) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val viewBinding = FragmentLimitsBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding ?: return
        viewBinding.appsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.appsList.adapter = limitsAdapter
        refreshBlockingControls()
        viewBinding.softBlockingEnabled.setOnCheckedChangeListener { _, enabled ->
            if (updatingBlockingControls) return@setOnCheckedChangeListener
            BlockingPreferences.setEnabled(requireContext(), enabled)
            refreshBlockingControls()
            if (enabled && (!UsagePermission.hasAccess(requireContext()) ||
                    !BlockingAccess.isServiceEnabled(requireContext()))) showBlockingSetup()
        }
        viewBinding.blockingSetup.setOnClickListener { showBlockingSetup() }
    }

    override fun onResume() {
        super.onResume()
        refreshBlockingControls()
        refresh()
    }

    private fun refreshBlockingControls() {
        val viewBinding = binding ?: return
        val currentContext = context ?: return
        updatingBlockingControls = true
        viewBinding.softBlockingEnabled.isChecked = BlockingPreferences.isEnabled(currentContext)
        updatingBlockingControls = false
        viewBinding.blockingStatus.setText(when {
            !BlockingPreferences.isEnabled(currentContext) -> R.string.blocking_disabled
            !UsagePermission.hasAccess(currentContext) -> R.string.blocking_needs_usage
            !BlockingAccess.isServiceEnabled(currentContext) -> R.string.blocking_needs_accessibility
            else -> R.string.blocking_ready
        })
    }

    private fun showBlockingSetup() {
        if (!isAdded || binding == null || blockingDialog?.isShowing == true) return
        val currentContext = requireContext()
        val needsUsage = !UsagePermission.hasAccess(currentContext)
        blockingDialog = MaterialAlertDialogBuilder(currentContext)
            .setTitle(if (needsUsage) R.string.usage_access_title else R.string.soft_block_service_name)
            .setMessage(if (needsUsage) R.string.blocking_usage_explanation else R.string.blocking_disclosure)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (needsUsage) R.string.open_usage_settings else R.string.open_accessibility_settings) { _, _ ->
                try {
                    if (needsUsage) UsagePermission.openSettings(currentContext)
                    else BlockingAccess.openSettings(currentContext)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(currentContext, R.string.blocking_settings_error, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showLimitDialog(app: AppInfo) {
        if (!isAdded || binding == null || limitDialog?.isShowing == true) return
        val inputBinding = DialogLimitBinding.inflate(layoutInflater)
        app.dailyLimitMin?.let {
            inputBinding.limitInput.setText(getString(R.string.score_number, it))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_limit_title, app.label))
            .setView(inputBinding.root)
            .setPositiveButton(R.string.set_limit, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.remove_limit) { _, _ ->
                refresh { it.removeLimit(app.packageName) }
            }
            .create()
        limitDialog = dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = inputBinding.limitInput.text?.toString()?.trim().orEmpty()
            val minutes = text.toIntOrNull()
            if (text.isEmpty()) {
                inputBinding.limitInputLayout.error = getString(R.string.limit_empty_error)
            } else if (minutes == null || minutes !in 5..600) {
                inputBinding.limitInputLayout.error = getString(R.string.limit_range_error)
            } else {
                inputBinding.limitInputLayout.error = null
                refresh { it.setLimit(app.packageName, minutes) }
                dialog.dismiss()
            }
        }
        inputBinding.limitInput.setSelection(inputBinding.limitInput.text?.length ?: 0)
    }

    private fun refresh(change: ((RotRepository) -> Unit)? = null) {
        val viewBinding = binding ?: return
        val owner = viewLifecycleOwner
        val appContext = requireContext().applicationContext
        isBusy = true
        viewBinding.loading.isVisible = true
        viewBinding.errorMessage.isVisible = false
        DbExecutor.executor.execute {
            val result = runCatching {
                RotDbHelper(appContext).use { helper ->
                    val repo = RotRepository(helper)
                    change?.invoke(repo)
                    repo.getAppsWithTodayUsage(DateUtils.todayString())
                }
            }
            mainHandler.post {
                if (!isAdded || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@post
                val currentBinding = binding ?: return@post
                isBusy = false
                currentBinding.loading.isVisible = false
                result.fold(
                    onSuccess = { apps ->
                        limitsAdapter.submitList(apps) {
                            if (binding === currentBinding) currentBinding.appsList.scheduleLayoutAnimation()
                        }
                    },
                    onFailure = {
                        Log.e("RotLimits", "Could not update or load limits", it)
                        currentBinding.errorMessage.isVisible = true
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        binding?.softBlockingEnabled?.setOnCheckedChangeListener(null)
        binding?.blockingSetup?.setOnClickListener(null)
        blockingDialog?.dismiss()
        blockingDialog = null
        limitDialog?.dismiss()
        limitDialog = null
        binding?.appsList?.adapter = null
        binding = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}
