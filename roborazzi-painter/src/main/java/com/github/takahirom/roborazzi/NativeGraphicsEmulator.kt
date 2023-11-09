package com.github.takahirom.roborazzi

import java.awt.BorderLayout
import java.awt.Button
import java.awt.Canvas
import java.awt.Dimension
import java.awt.Frame
import java.awt.Graphics
import java.awt.Panel
import java.awt.ScrollPane
import java.awt.ScrollPane.SCROLLBARS_ALWAYS
import java.awt.TextField
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.UIManager

@InternalRoborazziApi
class NativeGraphicsEmulator {
  private lateinit var nativeGraphicsEmulatorFrame: NativeGraphicsEmulatorFrame
  fun start(
    initialCanvas: AwtRoboCanvas,
    onClick: (x: Int, y: Int) -> Unit,
    onClose: () -> Unit,
    onRotateButtonClicked: () -> Unit,
    onDarkLightButtonClicked: () -> Unit,
    onSelectedDeviceChange: (String) -> Unit,
    onDump: () -> Unit,
  ) {
    nativeGraphicsEmulatorFrame = NativeGraphicsEmulatorFrame(
      initialCanvas,
      onClick = onClick,
      onClose = onClose,
      onRotateButtonClicked = onRotateButtonClicked,
      onDarkLightButtonClicked = onDarkLightButtonClicked,
      onSelectedDeviceChange = onSelectedDeviceChange,
      onDump = onDump,
    )
  }

  fun onFrame(uiState: NativeGraphicsEmulatorFrame.UiState) {
    nativeGraphicsEmulatorFrame.onFrame(uiState)
  }
}

@InternalRoborazziApi
class NativeGraphicsEmulatorFrame(
  var roboCanvas: AwtRoboCanvas,
  onClick: (x: Int, y: Int) -> Unit,
  onClose: () -> Unit,
  onRotateButtonClicked: () -> Unit,
  onDarkLightButtonClicked: () -> Unit,
  onSelectedDeviceChange: (String) -> Unit,
  onDump: () -> Unit,
) :
  Frame("Roborazzi Native Graphics Emulator") {
  data class UiState(
    val canvas: AwtRoboCanvas,
    val qualifier: String,
    val devices: List<String>,
  )

  private val frameCanvas: Canvas
  private val scrollPane: ScrollPane
  private val controlPanel: ControlPanel


  init {
    UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel")
    setLayout(BorderLayout())
//    setSize(canvas.width, canvas.height)

    addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent) {
        dispose()
        onClose()
      }
    })

    frameCanvas = object : Canvas() {
      override fun getPreferredSize(): Dimension {
        return Dimension(roboCanvas.width, roboCanvas.height)
      }

      override fun paint(g: Graphics) {
        super.paint(g)
        g.drawImage(roboCanvas.bufferedImage, 0, 0, this)
      }
    }
    scrollPane = ScrollPane(SCROLLBARS_ALWAYS).apply {
      size = Dimension(roboCanvas.width, roboCanvas.height)
      hAdjustable.unitIncrement = 32
      vAdjustable.unitIncrement = 32
      add(frameCanvas)
    }
    add(scrollPane, BorderLayout.CENTER)
    controlPanel = ControlPanel(
      onRotateButtonClicked = onRotateButtonClicked,
      onDarkLightButtonClicked = onDarkLightButtonClicked,
      onSelectedDeviceChange = onSelectedDeviceChange,
      onDump = onDump,
    )
    add(
      controlPanel,
      BorderLayout.NORTH
    )
    pack()
    isVisible = true

    frameCanvas.addMouseListener(
      object : MouseListener {
        override fun mouseClicked(event: MouseEvent) {
          val x = event.x
          val y = event.y
//              val pixel = canvas.getPixel(x, y)
          onClick(x, y)
        }

        override fun mousePressed(e: MouseEvent?) {
        }

        override fun mouseReleased(e: MouseEvent?) {
        }

        override fun mouseEntered(e: MouseEvent?) {
        }

        override fun mouseExited(e: MouseEvent?) {
        }
      }
    )
  }

  class ControlPanel(
    onRotateButtonClicked: () -> Unit,
    onDarkLightButtonClicked: () -> Unit,
    onSelectedDeviceChange: (String) -> Unit,
    onDump: () -> Unit,
  ) : Panel() {

    val qualifierText: TextField
    val deviceComboBox: JComboBox<String>

    init {
      layout = BoxLayout(this, BoxLayout.X_AXIS)

      qualifierText = TextField("Qualifier")
      add(qualifierText)

      val rotateButton = Button("Rotate")
      rotateButton.addActionListener {
        onRotateButtonClicked()
      }
      add(rotateButton)

      val darkLightButton = Button("DarkLight")
      darkLightButton.addActionListener {
        onDarkLightButtonClicked()
      }
      add(darkLightButton)

      deviceComboBox = JComboBox<String>()
      deviceComboBox.addActionListener {
        val selectedDevice = deviceComboBox.selectedItem as String? ?: return@addActionListener
        onSelectedDeviceChange(selectedDevice)
      }
      add(deviceComboBox)

      val dumpButton = Button("Dump")
      dumpButton.addActionListener {
        onDump()
      }
      add(dumpButton)
    }
  }

  private val doOnFrame = mutableListOf<() -> Unit>()

  fun onFrame(uiState: UiState) {
    doOnFrame.forEach { it() }
    doOnFrame.clear()

    controlPanel.qualifierText.text = uiState.qualifier
    if (controlPanel.deviceComboBox.itemCount != uiState.devices.size + 1) {
      controlPanel.deviceComboBox.removeAllItems()
      controlPanel.deviceComboBox.addItem("Select device")
      for (device in uiState.devices) {
        controlPanel.deviceComboBox.addItem(device)
      }
    }
    uiState.canvas.drawPendingDraw()
    this.roboCanvas = uiState.canvas
    frameCanvas.repaint()
    if (frameCanvas.minimumSize.width != roboCanvas.width || frameCanvas.minimumSize.height != roboCanvas.height) {
      frameCanvas.minimumSize = Dimension(roboCanvas.width, roboCanvas.height)
      pack()
      doOnFrame.add {
        scrollPane.addNotify()
        scrollPane.doLayout()
        scrollPane.hAdjustable.unitIncrement = 32
        scrollPane.vAdjustable.unitIncrement = 32
      }
    }
  }
}