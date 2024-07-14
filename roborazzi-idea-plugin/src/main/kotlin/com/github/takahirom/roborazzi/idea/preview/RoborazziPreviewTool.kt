package com.github.takahirom.roborazzi.idea.preview

import com.github.takahirom.roborazzi.idea.settings.AppSettingsConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.containers.SLRUMap
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.codegen.inline.getOrPut
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Image
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Box
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel


class RoborazziPreviewToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()
    val panel = RoborazziPreviewPanel(project)
    val content = contentFactory.createContent(panel, "", false)
    toolWindow.contentManager.addContent(content)

    if (toolWindow is ToolWindowEx) {
      toolWindow.setAdditionalGearActions(DefaultActionGroup(object :
        AnAction("Roborazzi setting") {
        override fun actionPerformed(e: AnActionEvent) {
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(e.project, AppSettingsConfigurable::class.java)
        }
      }))
    }
  }
}

class RoborazziPreviewPanel(project: Project) : JPanel(BorderLayout()) {
  private val listModel = DefaultListModel<Pair<String, Long>>()
  private val statusGradleTaskPanel = StatusToolbarPanel(project) { taskName ->
    viewModel?.executeTaskByName(project, taskName)
  }
  private val statusBar = JBBox.createHorizontalBox().apply {
    statusGradleTaskPanel.statusLabel = "No images found"
    add(statusGradleTaskPanel)
  }

  private val imageList = object : JBList<Pair<String, Long>>(listModel) {
    override fun getScrollableTracksViewportWidth(): Boolean {
      return true
    }
  }.apply {
    cellRenderer = ImageListCellRenderer()

    val l: ComponentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        // next line possible if list is of type JXList
        // list.invalidateCellSizeCache();
        // for core: force cache invalidation by temporarily setting fixed height
        setFixedCellHeight(10)
        setFixedCellHeight(-1)
      }
    }
    addComponentListener(l)
    selectionMode = ListSelectionModel.SINGLE_SELECTION
  }

  private var viewModel: PreviewViewModel? = null

  init {
    restartViewModel()
    addComponentListener(
      object : ComponentListener {
        override fun componentResized(e: ComponentEvent?) {
        }

        override fun componentMoved(e: ComponentEvent?) {
        }

        override fun componentShown(e: ComponentEvent?) {
          restartViewModel()
          viewModel?.onInit(project)
        }


        override fun componentHidden(e: ComponentEvent?) {
          viewModel?.onHide()
          viewModel = null
        }
      }
    )
    val scrollPane = JBScrollPane(imageList)
    scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    add(statusBar, BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)
    viewModel?.onInit(project)
    imageList.addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        imageList.ensureIndexIsVisible(imageList.selectedIndex)
      }
    }
    val messageBus: MessageBusConnection = project.messageBus.connect()
    messageBus.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          viewModel?.onInit(project)
        }

        val caretListener = object : CaretListener {
          override fun caretPositionChanged(event: CaretEvent) {
            super.caretPositionChanged(event)
            viewModel?.onCaretPositionChanged(project)
          }
        }

        override fun selectionChanged(event: FileEditorManagerEvent) {
          viewModel?.onSelectedFileChanged(project)
          val editor = FileEditorManager.getInstance(project).selectedTextEditor

          editor?.caretModel?.removeCaretListener(caretListener)
          editor?.caretModel?.addCaretListener(caretListener)
        }
      })

    viewModel?.onInit(project)
  }

  private fun restartViewModel() {
    viewModel = PreviewViewModel()
    viewModel?.coroutineScope?.launch {
      viewModel?.imagesStateFlow?.collect {
        roborazziLog("imagesStateFlow.collect ${it.size}")
        listModel.clear()
        listModel.addAll(it)
      }
    }
    viewModel?.coroutineScope?.launch {
      viewModel?.statusText?.collect {
        statusGradleTaskPanel.statusLabel = it
      }
    }

    viewModel?.coroutineScope?.launch {
      viewModel?.dropDownUiState?.collect {
        statusGradleTaskPanel.isExecuteGradleTaskActionEnabled = it.flag == PreviewViewModel.ActionToolbarUiState.Flag.IDLE
        statusGradleTaskPanel.setActions(
          it.tasks.map { taskName -> StatusToolbarPanel.ToolbarAction(taskName, taskName)}
        )
      }
    }
    viewModel?.coroutineScope?.launch {
      viewModel?.shouldSeeIndex?.collect {
        if (it == -1) {
          return@collect
        }
        roborazziLog("shouldSeeIndex.collect $it")
        imageList.selectedIndex = it
        viewModel?.onShouldSeeIndexHandled()
      }
    }
  }

}


class ImageListCellRenderer : ListCellRenderer<Pair<String, Long>> {
  data class CacheKey(
    val width: Int,
    val filePath: String,
    val lastModified: Long,
    val isSelected: Boolean
  )

  private val imageCache = SLRUMap<Pair<String, Long>, Image>(300, 50)
  private val lruCache = SLRUMap<CacheKey, Box>(300, 50)
  override fun getListCellRendererComponent(
    list: JList<out Pair<String, Long>>,
    value: Pair<String, Long>,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    return lruCache.getOrPut(
      CacheKey(
        width = list.width,
        filePath = value.first,
        lastModified = value.second,
        isSelected = isSelected
      )
    ) {
      val start = System.currentTimeMillis()
      val filePath = value.first
      val lastModified = value.second
      val file = File(filePath)
      val image = imageCache.getOrPut(file.path to lastModified) { ImageIO.read(file) }
      val icon = ImageIcon(image)
      val maxImageWidth = list.width * 0.9
      val scale = if (maxImageWidth < icon.iconWidth) maxImageWidth / icon.iconWidth else 1.0
      val newIcon = if (scale == 1.0) icon else ImageIcon(
        icon.image.getScaledInstance(
          (icon.iconWidth * scale).toInt(),
          (icon.iconHeight * scale).toInt(),
          Image.SCALE_SMOOTH
        )
      )

      val imageLabel = JBLabel()
      imageLabel.setIcon(newIcon)
      imageLabel.background = JBColor.RED

      val box = JBBox.createVerticalBox().apply {
        add(Box.createVerticalStrut(16))
        add(imageLabel)
        add(JBLabel(file.name))
        // Add space between items
        add(Box.createVerticalStrut(16))
      }

      if (isSelected) {
        box.background = list.selectionBackground
        box.foreground = list.selectionForeground
      } else {
        box.background = list.background
        box.foreground = list.foreground
      }
      box.isOpaque = true
      roborazziLog("getListCellRendererComponent in ${System.currentTimeMillis() - start}ms")
      box
    }
  }
}

fun roborazziLog(message: String) {
  println("Roborazzi idea plugin: $message")
}
