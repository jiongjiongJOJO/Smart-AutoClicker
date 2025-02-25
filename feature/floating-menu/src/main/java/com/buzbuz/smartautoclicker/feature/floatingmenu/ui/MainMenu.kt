/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.floatingmenu.ui

import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.view.WindowManager

import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat

import com.buzbuz.smartautoclicker.core.display.DisplayMetrics
import com.buzbuz.smartautoclicker.core.ui.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.ui.overlays.menu.OverlayMenu
import com.buzbuz.smartautoclicker.core.ui.overlays.viewModels
import com.buzbuz.smartautoclicker.feature.floatingmenu.R
import com.buzbuz.smartautoclicker.feature.floatingmenu.databinding.OverlayMenuBinding
import com.buzbuz.smartautoclicker.feature.scenario.config.ui.scenario.ScenarioDialog
import com.buzbuz.smartautoclicker.feature.scenario.debugging.ui.overlay.DebugModel
import com.buzbuz.smartautoclicker.feature.scenario.debugging.ui.overlay.DebugOverlayView
import com.buzbuz.smartautoclicker.feature.scenario.debugging.ui.report.DebugReportDialog
import com.buzbuz.smartautoclicker.feature.tutorial.ui.TutorialActivity

import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * [OverlayMenu] implementation for displaying the main menu overlay.
 *
 * This is the menu displayed once the service is started via the [com.buzbuz.smartautoclicker.activity.ScenarioActivity]
 * once the user has selected a scenario to be used. It allows the user to start the detection on the currently loaded
 * scenario, as well as editing the attached list of events.
 *
 * There is no overlay views attached to this overlay menu, meaning that the user will always be able to clicks on the
 * Activities displayed below it.
 */
class MainMenu(private val onStopClicked: () -> Unit) : OverlayMenu() {

    /** The view model for this menu. */
    private val viewModel: MainMenuModel by viewModels()
    /** The view model for the debugging features. */
    private val debuggingViewModel: DebugModel by viewModels()

    /** View binding for the content of the overlay. */
    private lateinit var viewBinding: OverlayMenuBinding
    /** Animation from play to pause. */
    private lateinit var playToPauseDrawable: AnimatedVectorDrawableCompat
    /** Animation from pause to play. */
    private lateinit var pauseToPlayDrawable: AnimatedVectorDrawableCompat

    private var billingFlowTriggeredByDetectionLimitation: Boolean = false
    /** The coroutine job for the observable used in debug mode. Null when not in debug mode. */
    private var debugObservableJob: Job? = null

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        playToPauseDrawable = AnimatedVectorDrawableCompat.create(context, R.drawable.anim_play_pause)!!
        pauseToPlayDrawable = AnimatedVectorDrawableCompat.create(context, R.drawable.anim_pause_play)!!

        viewBinding = OverlayMenuBinding.inflate(layoutInflater)
        return viewBinding.root
    }

    override fun onCreateOverlayView(): DebugOverlayView = DebugOverlayView(context)

    override fun onCreateOverlayViewLayoutParams(): WindowManager.LayoutParams =
        super.onCreateOverlayViewLayoutParams().apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

    override fun onCreate() {
        super.onCreate()

        // Ensure the debug view state is correct
        viewBinding.layoutDebug.visibility = View.GONE
        setOverlayViewVisibility(View.GONE)

        // When the billing flow is not longer displayed, restore the dialogs states
        lifecycleScope.launch {
            repeatOnLifecycle((Lifecycle.State.CREATED)) {
                viewModel.isBillingFlowInProgress.collect { isDisplayed ->
                    if (!isDisplayed) {
                        if (billingFlowTriggeredByDetectionLimitation) {
                            show()
                            billingFlowTriggeredByDetectionLimitation = false
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.canStartScenario.collect(::updatePlayPauseButtonEnabledState) }
                launch { viewModel.detectionState.collect(::updateDetectionState) }
                launch { debuggingViewModel.isDebugging.collect(::updateDebugOverlayViewVisibility) }
                launch { debuggingViewModel.isDebugReportReady.collect(::showDebugReportDialog) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.monitorPlayPauseButtonView(viewBinding.btnPlay)
        viewModel.monitorConfigButtonView(viewBinding.btnClickList)
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.shouldShowFirstTimeTutorialDialog()) showFirstTimeTutorialDialog()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopViewMonitoring()
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_play -> {
                viewModel.toggleDetection(context) {
                    billingFlowTriggeredByDetectionLimitation = true
                    hide()
                }
            }
            R.id.btn_click_list -> {
                viewModel.startScenarioEdition {
                    showScenarioConfigDialog()
                }
            }
            R.id.btn_stop -> onStopClicked()
        }
    }

    override fun getWindowMaximumSize(backgroundView: ViewGroup): Size {
        val bgSize = super.getWindowMaximumSize(backgroundView)
        return Size(
            bgSize.width + context.resources.getDimensionPixelSize(R.dimen.overlay_debug_panel_width),
            bgSize.height,
        )
    }

    /** Refresh the play menu item according to the scenario state. */
    private fun updatePlayPauseButtonEnabledState(canStartDetection: Boolean) =
        setMenuItemViewEnabled(viewBinding.btnPlay, canStartDetection)

    /** Refresh the menu layout according to the detection state. */
    private fun updateDetectionState(newState: UiState) {
        val currentState = viewBinding.btnPlay.tag
        if (currentState == newState) return

        viewBinding.btnPlay.tag = newState
        when (newState) {
            UiState.Idle -> {
                if (currentState == null) {
                    viewBinding.btnPlay.setImageResource(R.drawable.ic_play_arrow)
                } else {
                    animateLayoutChanges {
                        setMenuItemVisibility(viewBinding.btnStop, true)
                        setMenuItemVisibility(viewBinding.btnClickList, true)
                        viewBinding.btnPlay.setImageDrawable(pauseToPlayDrawable)
                        pauseToPlayDrawable.start()
                    }
                }
            }

            UiState.Detecting -> {
                if (currentState == null) {
                    viewBinding.btnPlay.setImageResource(R.drawable.ic_pause)
                } else {
                    animateLayoutChanges {
                        setMenuItemVisibility(viewBinding.btnStop, false)
                        setMenuItemVisibility(viewBinding.btnClickList, false)
                        viewBinding.btnPlay.setImageDrawable(playToPauseDrawable)
                        playToPauseDrawable.start()
                    }
                }
            }
        }
    }

    private fun showScenarioConfigDialog() =
        OverlayManager.getInstance(context).navigateTo(
            context = context,
            newOverlay = ScenarioDialog(
                onConfigDiscarded = viewModel::cancelScenarioChanges,
                onConfigSaved = { viewModel.saveScenarioChanges { success -> if (!success) showScenarioSaveErrorDialog() } },
            ),
            hideCurrent = true,
        )

    private fun showScenarioSaveErrorDialog() {
        MaterialAlertDialogBuilder(DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(context, R.style.AppTheme)))
            .setTitle(R.string.dialog_overlay_title_warning)
            .setMessage(R.string.message_scenario_saving_error)
            .setPositiveButton(R.string.button_dialog_modify) { _: DialogInterface, _: Int ->
                showScenarioConfigDialog()
            }
            .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int ->
                viewModel.cancelScenarioChanges()
            }
            .create()
            .apply {
                window?.setType(DisplayMetrics.TYPE_COMPAT_OVERLAY)
            }
            .show()
    }

    /**
     * Change the debug state of this UI.
     * @param isVisible true when the debug view should be shown, false to hide it.
     */
    private fun updateDebugOverlayViewVisibility(isVisible: Boolean) {
        if (isVisible && debugObservableJob == null) {
            viewBinding.layoutDebug.visibility = View.VISIBLE
            setOverlayViewVisibility(View.VISIBLE)
            debugObservableJob = observeDebugValues()

        } else if (!isVisible && debugObservableJob != null) {
            debugObservableJob?.cancel()
            debugObservableJob = null

            viewBinding.debugEventName.text = null
            viewBinding.debugConditionName.text = null
            viewBinding.debugConfidenceRate.text = null
            viewBinding.layoutDebug.visibility = View.GONE
            setOverlayViewVisibility(View.GONE)
            (screenOverlayView as DebugOverlayView).clear()
        }
    }

    /**
     * Observe the values for the debug and update the debug views.
     * @return the coroutine job for the observable. Can be cancelled to stop the observation.
     */
    private fun observeDebugValues() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                debuggingViewModel.debugLastPositive.collect { debugInfo ->
                    viewBinding.debugEventName.text = debugInfo.eventName
                    viewBinding.debugConditionName.text = debugInfo.conditionName
                    viewBinding.debugConfidenceRate.text = debugInfo.confidenceRateText
                }
            }

            launch {
                debuggingViewModel.debugLastPositiveCoordinates.collect { coordinates ->
                    (screenOverlayView as DebugOverlayView).setPositiveResult(coordinates)
                }
            }
        }
    }

    private fun showDebugReportDialog(reportReady: Boolean) {
        if (!reportReady) return

        OverlayManager.getInstance(context).navigateTo(
            context = context,
            newOverlay = DebugReportDialog(),
        )
    }

    private fun showFirstTimeTutorialDialog() {
        MaterialAlertDialogBuilder(DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(context, R.style.AppTheme)))
            .setTitle(R.string.dialog_title_tutorial_first_time)
            .setMessage(R.string.message_tutorial_first_time)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                context.startActivity(
                    Intent()
                        .setComponent(ComponentName("com.buzbuz.smartautoclicker", "com.buzbuz.smartautoclicker.feature.tutorial.ui.TutorialActivity"))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .create()
            .apply {
                window?.setType(DisplayMetrics.TYPE_COMPAT_OVERLAY)
            }
            .show()

        viewModel.onFirstTimeTutorialDialogShown()
    }
}