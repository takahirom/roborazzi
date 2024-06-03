package com.github.takahirom.roborazzi.idea.preview

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.kotlin.psi.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Image
import java.awt.event.*
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*


class RoborazziPreviewToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()
    val panel = RoborazziPreviewPanel(project)
    val content = contentFactory.createContent(panel, "", false)
    toolWindow.contentManager.addContent(content)
  }
}

class PreviewViewModel {

  var coroutineScope = MainScope()
  val imagesStateFlow = MutableStateFlow<List<Pair<File, Long>>>(listOf())
  private val lastEditingFileName = MutableStateFlow<String?>(null)
  val statusText = MutableStateFlow("No images found")
  val shouldSeeIndex = MutableStateFlow(-1)
  private var updateListJob: Job? = null

  fun onRefreshClicked(project: Project) {
    roborazziLog("onRefreshClicked")
    refreshList(project)
  }

  fun onInit(project: Project) {
    roborazziLog("onInit")
    refreshList(project)
  }

  fun onFileOpened(project: Project) {
    roborazziLog("onFileOpened")
    coroutineScope.launch {
      refreshListProcess(project)
    }
  }

  fun onCaretPositionChanged(project: Project) {
    roborazziLog("onCaretPositionChanged")
    coroutineScope.launch {
      refreshListProcess(project)
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      val offset = editor?.caretModel?.offset
      if (offset != null) {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return@launch
        val kotlinFile = psiFile as? KtFile ?: return@launch
        val pe: PsiElement = kotlinFile.findElementAt(editor.caretModel.offset) ?: return@launch
        val method: KtFunction = findFunction(pe) ?: return@launch
        imagesStateFlow.value.indexOfFirst { it.first.name.contains(method.name ?: "") }.let {
          shouldSeeIndex.value = it
        }
      }
    }
  }

  private fun findFunction(element: PsiElement): KtFunction? {
    var methodCnadidate: KtDeclaration? = null
    while (true) {
      if (methodCnadidate == null) {
        methodCnadidate = PsiTreeUtil.getParentOfType(
          element,
          KtDeclaration::class.java
        )
      } else {
        methodCnadidate = if ((element is CompositeElement)) methodCnadidate else PsiTreeUtil.getParentOfType(
          methodCnadidate,
          KtDeclaration::class.java
        )
      }
      if (methodCnadidate is KtFunction) {
        if (methodCnadidate.isLocal) {
          continue
        }
        break
      }
      if (methodCnadidate == null) {
        break
      }
    }

    return methodCnadidate as? KtFunction
  }

  private fun refreshList(project: Project) {
    updateListJob?.cancel()
    updateListJob = coroutineScope.launch {
      refreshListProcess(project)
    }
  }

  private suspend fun refreshListProcess(project: Project) {
    roborazziLog("refreshListProcess")
    statusText.value = "Loading..."
    yield()
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return run {
      statusText.value = "No editor found"
    }

    val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return run {
      statusText.value = "No psi file found"
    }
    val kotlinFile = psiFile as? KtFile ?: return run {
      statusText.value = "No kotlin file found"
    }
    if (lastEditingFileName.value != kotlinFile.name) {
      imagesStateFlow.value = emptyList()
      delay(10)
      lastEditingFileName.value = kotlinFile.name
    }
    val allDeclarations = kotlinFile.declarations
    val allPreviewImageFiles = mutableListOf<File>()
    val functions: List<KtFunction> = allDeclarations.filterIsInstance<KtFunction>()
    val classes: List<KtClass> = allDeclarations.filterIsInstance<KtClass>()
    val searchPath = project.basePath
    statusText.value = "Searching images in ${searchPath} ..."

    val files = withContext(Dispatchers.IO) {
      val roborazziFolders = ProjectRootManager.getInstance(project).contentRootsFromAllModules
        .map { File(it.path + File.separator + "build" + File.separator + "outputs" + File.separator + "roborazzi") }
        .filter {
          it.exists()
        }

      roborazziFolders
        .flatMap {
          it.walkTopDown().filter { it.isFile }
        }
    }

    allPreviewImageFiles.addAll(findImages(classes, files))
    allPreviewImageFiles.addAll(findImages(functions, files))

    if (allPreviewImageFiles.isEmpty()) {
      statusText.value = "No images found"
    } else {
      statusText.value = "${allPreviewImageFiles.size} images found"
    }
    val result = allPreviewImageFiles.sortedByClassesAndFunctions(classes, functions)
      .map { it to it.lastModified() }
    roborazziLog("result result.size:${result.size}")
    imagesStateFlow.value = result
  }

  private fun List<File>.sortedByClassesAndFunctions(classes: List<KtClass>, functions: List<KtFunction>): List<File> {
    val allFunctionsOrder = classes.flatMap { it.declarations.filterIsInstance<KtFunction>() } + functions
    return this.sortedBy { file ->
      allFunctionsOrder.indexOfFirst { function ->
        file.name.contains(function.name ?: "")
      }
    }
  }

  private fun findImages(
    elements: List<KtElement>,
    files: List<File>
  ): List<File> {
    return elements.flatMap { element ->
      val elementNameName = element.name ?: return@flatMap emptyList<File>()
      val pattern = ".*$elementNameName.*.png"
      files
        .filter {
          val matches = it.name.matches(Regex(pattern))
          matches
        }
    }
  }

  fun cancel() {
    coroutineScope.cancel()
  }

  fun onHide() {
    cancel()
  }
}

class RoborazziPreviewPanel(project: Project) : JPanel(BorderLayout()) {
  private val listModel = DefaultListModel<Pair<File, Long>>()
  private val statusLabel = JBLabel("No images found")
  private val statusBar = JBBox.createHorizontalBox().apply {
    add(JLabel("Refresh: ").apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          viewModel?.onRefreshClicked(project)
        }
      })
    })
    add(statusLabel)
  }
  private val imageList = object : JBList<Pair<File, Long>>(listModel) {
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
    addComponentListener(l);
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
    messageBus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        viewModel?.onInit(project)
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        viewModel?.onFileOpened(project)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        editor?.caretModel?.addCaretListener(object : CaretListener {
          override fun caretPositionChanged(event: CaretEvent) {
            super.caretPositionChanged(event)
            viewModel?.onCaretPositionChanged(project)
          }
        })
      }
    })

    viewModel?.onInit(project)
  }

  private fun restartViewModel() {
    viewModel = PreviewViewModel()
    viewModel?.coroutineScope?.launch {
      viewModel?.imagesStateFlow?.collect {
        listModel.clear()
        listModel.addAll(it)
      }
    }
    viewModel?.coroutineScope?.launch {
      viewModel?.statusText?.collect {
        statusLabel.text = it
      }
    }
    viewModel?.coroutineScope?.launch {
      viewModel?.shouldSeeIndex?.collect {
        imageList.selectedIndex = it
      }
    }
  }

}


class ImageListCellRenderer : ListCellRenderer<Pair<File, Long>> {
  override fun getListCellRendererComponent(
    list: JList<out Pair<File, Long>>,
    value: Pair<File, Long>,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val file = value.first
    val icon = ImageIcon(ImageIO.read(File(file.path)))
    val scale = if (list.width < icon.iconWidth) list.width.toDouble() / icon.iconWidth else 1.0
    val newIcon = if (scale == 1.0) icon else ImageIcon(
      icon.image.getScaledInstance(
        (icon.iconWidth * scale).toInt(),
        (icon.iconHeight * scale).toInt(),
        Image.SCALE_SMOOTH
      )
    )

    val imageLabel = JBLabel()
    imageLabel.setIcon(newIcon)
    imageLabel.isAllowAutoWrapping = true

    val box = JBBox.createVerticalBox().apply {
      add(imageLabel)
      add(JBLabel(file.name))
      // Add space between items
      add(Box.createVerticalStrut(30))
      alignmentX = Component.LEFT_ALIGNMENT
    }

    // 要素の選択状態を適用
    if (isSelected) {
      box.background = list.selectionBackground
      box.foreground = list.selectionForeground
    } else {
      box.background = list.background
      box.foreground = list.foreground
    }
    box.isOpaque = true

    return box
  }
}

fun roborazziLog(message: String) {
  println("Roborazzi idea plugin: $message")
}
