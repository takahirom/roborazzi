package com.github.takahirom.roborazzi.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.takahirom.roborazzi.sample.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

  private var _binding: FragmentFirstBinding? = null

  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {

    _binding = FragmentFirstBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.compose.setContent {
      SampleComposableFunction()
    }

    binding.buttonFirst.setOnClickListener {
      findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}

@Composable
fun SampleComposableFunction() {
  var count by remember { mutableStateOf(0) }
  Column(
    Modifier
      .size(300.dp)
  ) {
    Box(
      Modifier
        .testTag("MyComposeButton")
        .background(Color.Gray)
        .size(50.dp)
        .clickable {
          count++
        }
    )
    (0 until count).forEach {
      Box(
        Modifier
          .background(Color.Red)
          .testTag("child:$it")
          .size(30.dp)
      )
    }
  }
}