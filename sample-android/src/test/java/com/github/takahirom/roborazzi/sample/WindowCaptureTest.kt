package com.github.takahirom.roborazzi.sample

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.Dump
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class WindowCaptureTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun composeDialog() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Under the dialog")
        AlertDialog(
          onDismissRequest = { },
          title = { Text("ComposeAlertDialogTitle") },
          text = { Text("Text") },
          confirmButton = {
            Text("OK")
          },
          dismissButton = {
            Text("Cancel")
          }
        )
      }
    }

    captureScreenRoboImage()
  }


  @OptIn(ExperimentalMaterial3Api::class)
  @Test
  fun composeBottomSheet() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        var showBottomSheet by remember { mutableStateOf(false) }
        Scaffold(
          floatingActionButton = {
            ExtendedFloatingActionButton(
              text = { Text("Show bottom sheet") },
              icon = { Icon(Icons.Filled.Add, contentDescription = "") },
              onClick = {
                showBottomSheet = true
              }
            )
          }
        ) { contentPadding ->
          // Screen content
          Text(text = "Under the dialog")

          if (showBottomSheet) {
            ModalBottomSheet(
              onDismissRequest = {
                showBottomSheet = false
              },
              sheetState = sheetState
            ) {
              // Sheet content
              Button(onClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                  if (!sheetState.isVisible) {
                    showBottomSheet = false
                  }
                }
              }) {
                Text(modifier = Modifier.padding(contentPadding), text = "Hide bottom sheet")
              }
            }
          }
        }
      }
    }

    composeTestRule
      .onNode(hasText("Show bottom sheet"), true)
      .performClick()
    composeTestRule.waitForIdle()

    captureScreenRoboImage()
  }

  @Test
  fun androidDialog() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Under the dialog")
        val context = LocalContext.current
        LaunchedEffect(Unit) {
          AlertDialog.Builder(context)
            .setTitle("ViewAlertDialogTitle")
            .setMessage("Text")
            .setPositiveButton("OK") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
        }
      }
    }

    captureScreenRoboImage()
  }

  @Test
  fun noDialog() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Content")
      }
    }

    captureScreenRoboImage()
  }

  @Test
  fun dump() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Under the dialog")
        val context = LocalContext.current
        LaunchedEffect(Unit) {
          AlertDialog.Builder(context)
            .setTitle("ViewAlertDialogTitle")
            .setMessage("Text")
            .setPositiveButton("OK") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
        }
        AlertDialog(
          onDismissRequest = { },
          title = { Text("ComposeAlertDialogTitle") },
          text = { Text("Text") },
          confirmButton = {
            Text("OK")
          },
          dismissButton = {
            Text("Cancel")
          }
        )
      }
    }

    captureScreenRoboImage(
      roborazziOptions = RoborazziOptions(
        captureType = RoborazziOptions.CaptureType.Dump(),
      )
    )
  }
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class FragmentActivityWindowCaptureTest {
  class MyBottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun setupDialog(dialog: Dialog, style: Int) {
      super.setupDialog(dialog, style)
      dialog.setContentView(FrameLayout(requireContext()).apply {
        addView(TextView(requireContext()).apply {
          text = "BottomSheetDialogFragment"
          background = ColorDrawable(android.graphics.Color.RED)
        })
      })
    }
  }

  @Test
  fun bottomSheetDialog() {
    ROBORAZZI_DEBUG = true
    val activityScenario = ActivityScenario.launch(MainActivity::class.java)
    activityScenario.onActivity { activity ->
      val fragmentManager = activity.supportFragmentManager
      MyBottomSheetDialogFragment()
        .show(fragmentManager, "BottomSheetDialogFragment")
      fragmentManager.executePendingTransactions()
    }
    captureScreenRoboImage()
  }
}