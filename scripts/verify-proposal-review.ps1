param(
  [Parameter(Mandatory=$true)][string]$BackendRoot,
  [Parameter(Mandatory=$true)][string]$ResolvedClasspath
)
# Verification only: no install, service startup, or modification of the shared Maven repository.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
  New-Item -ItemType Directory -Force target/proposal-review-api | Out-Null
  [xml]$pom = Get-Content pom.xml
  $ns = $pom.DocumentElement.NamespaceURI
  function Add-VerificationDependency($group, $artifact, $version, $classifier, $jarPath) {
    $existing = @($pom.project.dependencies.dependency | Where-Object {
      $_.groupId -eq $group -and $_.artifactId -eq $artifact -and [string]$_.classifier -eq $classifier
    })
    if ($existing.Count -gt 0) { return }
    $dep = $pom.CreateElement('dependency', $ns)
    foreach ($pair in @(@('groupId',$group),@('artifactId',$artifact),@('version',$version),@('scope','system'),@('systemPath',$jarPath))) {
      $node = $pom.CreateElement($pair[0],$ns); $node.InnerText = $pair[1]; [void]$dep.AppendChild($node)
    }
    if ($classifier) { $node = $pom.CreateElement('classifier',$ns); $node.InnerText=$classifier; [void]$dep.AppendChild($node) }
    [void]$pom.project.dependencies.AppendChild($dep)
  }
  # Use the backend's already-built classes. Building that repository remains a separate step.
  foreach ($artifact in @('klab.core.api','klab.core.common')) {
    $classes = Join-Path $BackendRoot "$artifact/target/classes"
    if (!(Test-Path $classes)) { throw "Build the backend first: missing $classes" }
    $jarPath = Join-Path $repoRoot "target/proposal-review-api/$artifact-1.0.0-SNAPSHOT.jar"
    & jar --create --file $jarPath -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "Could not package $artifact for verification" }
    @($pom.project.dependencies.dependency | Where-Object { $_.artifactId -eq $artifact }) | ForEach-Object { [void]$pom.project.dependencies.RemoveChild($_) }
    Add-VerificationDependency 'org.integratedmodelling' $artifact '1.0.0-SNAPSHOT' '' $jarPath.Replace('\','/')
  }
  # Preserve the normal IDE's resolved dependency graph, because system-scoped verification
  # overrides do not carry transitive dependencies. All paths must be existing Maven jars.
  foreach ($jarPath in (Get-Content $ResolvedClasspath -Raw).Trim().Split(';')) {
    if (!(Test-Path $jarPath)) { throw "Missing resolved dependency: $jarPath" }
    $relative = ($jarPath -split '[\\/]repository[\\/]',2)
    if ($relative.Length -ne 2) { throw 'Pass a classpath resolved from the normal IDE pom.xml' }
    $parts = $relative[1] -split '[\\/]'
    $artifact=$parts[-3]; $version=$parts[-2]; $group=$parts[0..($parts.Length-4)] -join '.'
    $classifier=$parts[-1].Replace("$artifact-$version",'').Replace('.jar','').TrimStart('-')
    Add-VerificationDependency $group $artifact $version $classifier $jarPath.Replace('\','/')
  }
  $verificationPom = Join-Path $repoRoot '.proposal-verification-pom.xml'
  $pom.Save($verificationPom)
  & mvn -o -f $verificationPom '-Dtest=Proposal*Test,WorkflowReviewCallbacksTest,WorkflowProposalTransitionTest' test
  if ($LASTEXITCODE -ne 0) { throw 'Proposal review verification failed; inspect target/surefire-reports' }
} finally { Pop-Location }
