package com.github.takahirom.roborazzi

@InternalRoborazziApi
object RoborazziReportConst {
  @Deprecated(
    message = "Use resultsSummaryFilePathFromBuildDir instead",
    replaceWith = ReplaceWith("resultsSummaryFilePathFromBuildDir"),
    level = DeprecationLevel.ERROR
  )
  const val resultsSummaryFilePath = "build/test-results/roborazzi/results-summary.json"
  @Deprecated(
    message = "Use resultDirPathFromBuildDir instead",
    replaceWith = ReplaceWith("resultDirPathFromBuildDir"),
    level = DeprecationLevel.ERROR
  )
  const val resultDirPath = "build/test-results/roborazzi/results/"
  @Deprecated(
    message = "Use reportFilePathFromBuildDir instead",
    replaceWith = ReplaceWith("reportFilePathFromBuildDir"),
    level = DeprecationLevel.ERROR
  )
  const val reportFilePath = "build/reports/roborazzi/index.html"

  const val resultsSummaryFilePathFromBuildDir = "test-results/roborazzi/results-summary.json"
  const val resultDirPathFromBuildDir = "test-results/roborazzi/results/"
  const val reportFilePathFromBuildDir = "reports/roborazzi/index.html"

  sealed interface DefaultContextData {
    val key: String
    val title: String

    object DescriptionClass : DefaultContextData {
      override val key = "roborazzi_description_class"
      override val title = "Class"
    }

    companion object {
      fun keyToTitle(key: String): String {
        return when (key) {
          DescriptionClass.key -> DescriptionClass.title
          else -> key
        }
      }
    }
  }

  const val reportHtml = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Roborazzi report</title>
    <!-- Compiled and minified CSS -->
    <link
            rel="stylesheet"
            href="https://cdn.jsdelivr.net/npm/@materializecss/materialize/dist/css/materialize.min.css"
    />
    <link
            href="https://fonts.googleapis.com/icon?family=Material+Icons"
            rel="stylesheet"
    />
    <style>
        .container {
            width: 90%;
        }

        h3 {
            color: orange;
        }

        a, .menu {
            color: white;
        }
        
        th a, td a {
            display: block;
            color: black;
        }

        .material-icons {
            color: #29b6f6;
        }

        .us {
            color: #ffcc80;
        }
        
        #imageBottomSheet {
            max-height: 100%;
            top: 15%;
        }

        #modalImage {
          max-width: 100%;
        }
    </style>
</head>
<body>
<nav role="navigation" class="light-blue lighten-1">
    <div class="nav-wrapper container">
        <a href="#" class="brand-logo">Roborazzi report</a>
        <a href="#" data-target="nav-mobile" class="sidenav-trigger"
        ><i class="material-icons menu">menu</i></a
        >
    </div>
</nav>
<div class="section">
    <div class="container">
        <br><br>
REPORT_TEMPLATE_BODY
        <br><br>
    </div>
</div>

<div id="imageBottomSheet" class="modal bottom-sheet max-height">
    <div class="modal-content center-align">
        <img id="modalImage" src="" alt="">
    </div>
</div>

<footer class="page-footer orange">
    <div class="container">
        <a class="us" href="https://github.com/takahirom/roborazzi" target="_blank"
           rel="noopener noreferrer">Roborazzi</a>
        <br>
        <br>
    </div>
</footer>
<!-- Compiled and minified JavaScript -->
<script src="https://cdn.jsdelivr.net/npm/@materializecss/materialize/dist/js/materialize.min.js"></script>
<script>
    M.AutoInit();
    document.addEventListener('DOMContentLoaded', function() {
        M.Tabs.getInstance(document.getElementsByClassName("tabs")[0]).select('results');
        var modalInstance = M.Modal.init(document.getElementById('imageBottomSheet'), {});
        var modal = document.getElementById('imageBottomSheet');
        var modalImage = document.getElementById('modalImage');
        var modalTriggers = document.querySelectorAll('.modal-trigger');
        modalTriggers.forEach(function(trigger) {
            trigger.addEventListener('click', function() {
                var src = this.getAttribute('src');
                var alt = this.getAttribute('data-alt');
                modalImage.setAttribute('src', src);
                modalImage.setAttribute('alt', alt);
                var instance = M.Modal.getInstance(modal);
                instance.open();
            });
        });
    });
</script>
</body>
</html>
  """
}