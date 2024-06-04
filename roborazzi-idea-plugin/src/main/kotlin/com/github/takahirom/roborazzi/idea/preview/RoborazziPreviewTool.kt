package com.github.takahirom.roborazzi.idea.preview

import com.github.takahirom.roborazzi.idea.settings.AppSettingsConfigurable
import com.github.takahirom.roborazzi.idea.settings.AppSettingsState
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
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.containers.SLRUMap
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.kotlin.codegen.inline.getOrPut
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
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

class PreviewViewModel {

  var coroutineScope = MainScope()
  val imagesStateFlow = MutableStateFlow<List<Pair<String, Long>>>(listOf())
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

  fun onCaretPositionChanged(project: Project) {
    roborazziLog("onCaretPositionChanged")
    coroutineScope.launch {
      updateListJob?.cancel()
      refreshListProcess(project)
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      val offset = editor?.caretModel?.offset
      if (offset != null) {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile
          ?: return@launch
        val kotlinFile = psiFile as? KtFile ?: return@launch
        val pe: PsiElement = kotlinFile.findElementAt(editor.caretModel.offset) ?: return@launch
        val method: KtFunction = findFunction(pe) ?: return@launch
        imagesStateFlow.value.indexOfFirst {
          it.first.substringAfterLast(File.separator).contains(method.name ?: "")
        }
          .let {
            shouldSeeIndex.value = it
          }
      }
    }
  }

  private fun findFunction(element: PsiElement): KtFunction? {
    var methodCnadidate: KtDeclaration? = null
    while (true) {
      methodCnadidate = if (methodCnadidate == null) {
        PsiTreeUtil.getParentOfType(
          element,
          KtDeclaration::class.java
        )
      } else {
        if ((element is CompositeElement)) methodCnadidate else PsiTreeUtil.getParentOfType(
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
    val start = System.currentTimeMillis()
    roborazziLog("refreshListProcess")
    statusText.value = "Loading..."
    yield()
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return run {
      statusText.value = "No editor found"
    }

    val psiFile: PsiFile =
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return run {
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
    fun hasPreviewOrTestAnnotationOrHasNameOfTestFunction(declaration: KtDeclaration): Boolean {
      return declaration.annotationEntries.any { annotation ->
        annotation.text.contains("Composable") ||
          annotation.text.contains("Preview") || annotation.text.contains("Test")
      } || (declaration is KtFunction && declaration.name?.startsWith("test") == true)
    }

    val functions: List<KtFunction> = allDeclarations.filterIsInstance<KtFunction>()
      .filter { hasPreviewOrTestAnnotationOrHasNameOfTestFunction(it) }
    val classes: List<KtClass> = allDeclarations.filterIsInstance<KtClass>()
      .filter { it.declarations.any { hasPreviewOrTestAnnotationOrHasNameOfTestFunction(it) } }

    val searchPath = project.basePath
    statusText.value = "Searching images in $searchPath ..."

    val files = withContext(Dispatchers.IO) {
      val roborazziFolders = ProjectRootManager.getInstance(project).contentRootsFromAllModules
        .map { File(it.path + AppSettingsState.instance.imagesPathForModule) }
        .filter {
          it.exists()
        }

      roborazziFolders
        .flatMap { folder ->
          folder.walkTopDown().filter { it.isFile }
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
      .map { it.path to it.lastModified() }
    roborazziLog("refreshListProcess result result.size:${result.size} in ${System.currentTimeMillis() - start}ms")
    imagesStateFlow.value = result
  }

  private suspend fun List<File>.sortedByClassesAndFunctions(
    classes: List<KtClass>,
    functions: List<KtFunction>
  ): List<File> {
    val allFunctionNamesOrder = (classes
      .flatMap { it.declarations.filterIsInstance<KtFunction>() } + functions
      ).map { it.name }
    val files = this
    return withContext(Dispatchers.Default) {
      files.sortedBy { file ->
        allFunctionNamesOrder.indexOfFirst { functionName ->
          file.name.contains(functionName ?: "")
        }
      }
    }
  }

  private suspend fun findImages(
    elements: List<KtElement>,
    files: List<File>
  ): List<File> {
    val elementNames = elements.mapNotNull { element ->
      element.name
    }
    return withContext(Dispatchers.Default) {
      elementNames.flatMap { elementName ->
        val pattern = ".*$elementName.*.png"
        files
          .filter {
            val matches = it.name.matches(Regex(pattern))
            matches
          }
      }
    }
  }

  private fun cancel() {
    coroutineScope.cancel()
  }

  fun onHide() {
    cancel()
  }
}

class RoborazziPreviewPanel(project: Project) : JPanel(BorderLayout()) {
  private val listModel = DefaultListModel<Pair<String, Long>>()
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
          viewModel?.onCaretPositionChanged(project)
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
        statusLabel.text = it
      }
    }
    viewModel?.coroutineScope?.launch {
      viewModel?.shouldSeeIndex?.collect {
        roborazziLog("shouldSeeIndex.collect $it")
        imageList.selectedIndex = it
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
