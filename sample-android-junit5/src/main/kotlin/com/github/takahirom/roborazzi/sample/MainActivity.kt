package com.github.takahirom.roborazzi.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.takahirom.roborazzi.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    binding.updateButton.setOnClickListener {
      val input = binding.inputEditText.text ?: ""

      binding.descriptionText.text = getString(R.string.text_description_2, input)
    }
  }
}
