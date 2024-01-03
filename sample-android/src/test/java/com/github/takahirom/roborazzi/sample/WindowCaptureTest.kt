package com.github.takahirom.roborazzi.sample

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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


  @OptIn(ExperimentalMaterialApi::class)
  @Test
  fun composeBottomSheet() {
    composeTestRule.setContent {
      Column(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxSize()
      ) {
        Text("Under the dialog")
        // FROM: https://foso.github.io/Jetpack-Compose-Playground/material/modalbottomsheetlayout/
        val state = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val scope = rememberCoroutineScope()
        ModalBottomSheetLayout(
          {
            LazyColumn {
              items(50) {
                ListItem(
                  text = { Text("Item $it") },
                  icon = {
                    Icon(
                      Icons.Default.Favorite,
                      contentDescription = "Localized description"
                    )
                  }
                )
              }
            }
          }, sheetState = state
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text("Rest of the UI")
            Spacer(Modifier.height(20.dp))
            Button(onClick = { scope.launch { state.show() } }) {
              Text("Click to show sheet")
            }
          }
        }
      }
    }

    composeTestRule
      .onNode(hasText("Click to show sheet"))
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