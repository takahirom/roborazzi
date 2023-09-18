package com.github.takahirom.roborazzi

object RoborazziReportConst {
  const val resultsSummaryFilePath = "build/test-results/roborazzi/results-summary.json"
  const val resultDirPath = "build/test-results/roborazzi/results/"
  const val reportFilePath = "build/reports/roborazzi/index.html"
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

        .material-icons {
            color: #29b6f6;
        }

        .us {
            color: #ffcc80;
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
</script>
</body>
</html>
  """
}