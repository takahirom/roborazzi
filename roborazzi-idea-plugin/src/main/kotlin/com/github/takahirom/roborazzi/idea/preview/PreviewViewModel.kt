package com.github.takahirom.roborazzi.idea.preview

import com.github.takahirom.roborazzi.idea.settings.AppSettingsState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import java.io.File

class PreviewViewModel {

  var coroutineScope = MainScope()
  val imagesStateFlow = MutableStateFlow<List<Pair<String, Long>>>(listOf())
  private val lastEditingFileName = MutableStateFlow<String?>(null)
  val statusText = MutableStateFlow("No images found")
  private val _dropDownUiState = MutableStateFlow(emptyList<String>())
  val dropDownUiState = _dropDownUiState.asStateFlow()
  val shouldSeeIndex = MutableStateFlow(-1)
  private var updateListJob: Job? = null
  private val gradleTask = RoborazziGradleTask()

  fun onRefreshClicked(project: Project) {
    roborazziLog("onRefreshClicked")
    refreshList(project)
  }

  fun onInit(project: Project) {
    roborazziLog("onInit")
    refreshList(project)
  }

  fun onSelectedFileChanged(project: Project) {
    roborazziLog("onSelectedFileChanged")
    coroutineScope.launch {
      updateListJob?.cancel()
      refreshListProcess(project)
      selectListIndexByCaret(project)
      fetchTasks(project)
    }
  }

  fun onCaretPositionChanged(project: Project) {
    roborazziLog("onCaretPositionChanged")
    coroutineScope.launch {
      updateListJob?.cancel()
      refreshListProcess(project)
      selectListIndexByCaret(project)
      fetchTasks(project)
    }
  }

  fun executeTaskByName(project: Project, taskName: String) {
    roborazziLog("executeTaskByName")
    gradleTask.executeTaskByName(project, taskName)
  }

  private fun selectListIndexByCaret(project: Project) {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    val offset = editor?.caretModel?.offset
    if (offset != null) {
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile
        ?: return
      val kotlinFile = psiFile as? KtFile ?: return
      val pe: PsiElement = kotlinFile.findElementAt(editor.caretModel.offset) ?: return
      val method: KtFunction = findFunction(pe) ?: return
      roborazziLog("imagesStateFlow.value = ${imagesStateFlow.value}")
      imagesStateFlow.value.indexOfFirst {
        it.first.substringAfterLast(File.separator).contains(method.name ?: "")
      }
        .let {
          roborazziLog("shouldSeeIndex.value = $it")
          shouldSeeIndex.value = it
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

  private fun fetchTasks(project: Project) {
    roborazziLog("fetchTasks...")
    _dropDownUiState.value = gradleTask.fetchTasks(project)
  }

  private suspend fun refreshListProcess(project: Project) {
    val start = System.currentTimeMillis()
    roborazziLog("refreshListProcess")
    statusText.value = "Loading..."
    yield()
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return run {
      statusText.value = "No editor found"
      imagesStateFlow.value = emptyList()
    }

    val psiFile: PsiFile =
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return run {
        statusText.value = "No psi file found"
        imagesStateFlow.value = emptyList()
      }
    val kotlinFile = psiFile as? KtFile ?: return run {
      statusText.value = "No kotlin file found"
      imagesStateFlow.value = emptyList()
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
      .filter {
        it.name?.contains("Test") == true || it.declarations.any {
          hasPreviewOrTestAnnotationOrHasNameOfTestFunction(it)
        }
      }

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

  fun onShouldSeeIndexHandled() {
    shouldSeeIndex.value = -1
  }
}