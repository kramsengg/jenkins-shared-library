import static org.braintree.DatadogMetricsUtil.*
import org.braintree.HistogramTimer
def call(Map options = [:], List<String> targets) {
  options.time = options.timeout_in ?: 30
  options.unit = options.timeout_unit ?: "MINUTES"
  script {
    dockerPull()
    def label = revisionAllowed(options) ? "production" : "production-sandbox"
    HistogramTimer timer = new HistogramTimer()
    timer.start()
    nodeWithWorkspace(options.label ?: label) {
      timeoutWithRetryOnRemovedAgent(options) {
        checkout scm
        sh(script: "sudo -EHnu build ${buildCommand(options, targets)}", label: "Build Image Securely")
      }
    }
    timer.end()
    String[] tags = tags(env.JOB_NAME, env.BUILD_ID)

    def dataDogClient = statsDClient(tags: tags)
    dataDogClient.histogram("ci.bt_build.duration.seconds", timer.duration())
  }
}

def dockerImage() {
  return "dockerhub.braintree.tools/bt/jenkins-production-worker-ci:release"
}

def dockerPull() {
  sh(script: "docker pull ${dockerImage()}", label: "Ensuring latest release of Jenkins Production Worker")
}

def dockerCommand(String command) {
  return "docker run " +
    "-v /var/run/docker.sock:/var/run/docker.sock " +
    "-v ${env.HOME}/.docker/config.json:/root/.docker/config.json " +
    "-v ${env.WORKSPACE}:/root/workspace " +
    "${dockerImage()} " +
    "${command}"
}

def revisionAllowed(options) {
  return sh(script: dockerCommand(buildCommand(options + [check_revision_allowed: true], [])), returnStatus: true, label: "Check if revision is on an allowed branch") == 0
}

def buildCommand(Map options, List<String> targets) {
  def command = "bt-build"
  if (options.revision) {
    command += " --revision=\"${options.revision}\""
  }
  if (options.imageTag) {
    command += " --image-tag=\"${options.imageTag}\""
  }
  if (options.get("push", true)) {
    command += " --push"
  }
  if (options.check_revision_allowed) {
    command += " --check-revision-allowed"
  }
  for (target in targets) {
    command += " \"${target}\""
  }
  return command
}

