import static org.braintree.JenkinsAPI.markAgentsOffline
import static org.braintree.JenkinsAPI.findFirstAvailableLatestRevisionAgent
import static org.braintree.JenkinsAPI.findWorkersOnLabel
import static org.braintree.JenkinsAPI.cleanupOfflineAndIdleAgents
import hudson.model.User

def call(Map options = [:]) {
  if (options.revision == null) {
    String message = "required named parameter `revision`"
    echo message
    throw new Exception(message)
  }


  workersToOfflineCount = orchestrator("production-sandbox", options.revision)

  if (workersToOfflineCount > 0) {
    // exit early, because there are more workers under this commit sha/label to rollout
    // don't proceed offlining next label until all workers have been rolled out
    // the next cron build should take care of the next iteration of rolling out this commit sha/label
    return
  }
  orchestrator("production", options.revision)
}

def workspaceIsReadyForRollout(label, revision) {
  def asgRevision = null
  node(label) {
    def response = sh(
      script: "sudo -nu rollout jpw-rollout show-asg-revision",
      label: "Fetch the latest commit sha that the ASG is tagged with",
      returnStdout: true
    )
    def splitStdOut = response.split("\n")
    asgRevision = splitStdOut[splitStdOut.size() - 1]
  }

  if (revision != asgRevision) {
    echo "Exiting early since the ${label} workspace's ASG has not been applied to reflect ${revision}. Current deployed commit sha = ${asgRevision}."
  }
  return revision == asgRevision
}

def orchestrator(label, revision) {
  def offliningWorker = null
  def response = null

  if (!workspaceIsReadyForRollout(label, revision)) {
    return 0
  }


  node(label) {
    response = sh(
      script: "sudo -nu rollout jpw-rollout list --label=${label} --tagged-with=${revision}",
      label: "List workers on the latest deploy revision",
      returnStdout: true
    )
  }

  def jpwRolloutListResponse = parseJpwRolloutStdOut(response)
  def workersOnLatestRevision = jpwRolloutListResponse["instance_ids"]
  offliningWorker = findFirstAvailableLatestRevisionAgent(label, workersOnLatestRevision, this)

  node(offliningWorker.node.selfLabel.name) {
    checkout scm
    def listResponse = sh(
      script: "sudo -nu rollout jpw-rollout list --label=${label} --not-tagged-with=${revision}",
      label: "List AWS instances to offline",
      returnStdout: true
    )
    jpwRolloutListResponse = parseJpwRolloutStdOut(listResponse)

    // the instances that need to be offlined
    def workersToOfflineCount = jpwRolloutListResponse["instance_ids"].size()
    workersOnLatestRevisionCount = workersOnLatestRevision.size()
    if (workersToOfflineCount != 0) {
      // the instances that were actually offlined (e.g. don't offline the orchestrating agent)
      def offlinedWorkerCount = markAgentsOffline(
        jpwRolloutListResponse["instance_ids"],
        offliningWorker.node.selfLabel.name,
        this
      ).size()
      def terminateResponse = sh(
        script: "sudo -nu rollout jpw-rollout terminate --label=${label}",
        label: "Terminate AWS instances underneath offline and idle workers",
        returnStdout: true
      )
      def terminatedInstances = parseJpwRolloutStdOut(terminateResponse)
      echo "Issued EC2 termination for the following instances: ${terminatedInstances}."
      cleanupOfflineAndIdleAgents(label, this)
      def isFinishedOfflining = workersToOfflineCount == terminatedInstances.size()
      concludeRolloutIteration(offlinedWorkerCount, workersOnLatestRevisionCount, isFinishedOfflining, label, revision)
    } else {
      echo "Did not offline any ${label} workers. ${workersOnLatestRevisionCount} workers are on ${revision}."
    }
    return workersToOfflineCount
  }
}

def parseJpwRolloutStdOut(stdOutResponse) {
  def data = stdOutResponse.split("\n")
  def clean_json_string = data[data.size() - 1].trim().replace("[0m", "")
  return readJSON(text: clean_json_string)
}

def concludeRolloutIteration(offlineWorkerCount, workersOnLatestCount, isFinishedOfflining, label, revision) {
  String jenkins_env = "QA"
  if (env.JENKINS_URL == "https://ci.braintree.tools/") {
    jenkins_env = "CI"
  }

  String message

  if (isFinishedOfflining) {
    message = "[${jenkins_env}] :party-jenkins: Finished offlining `${offlineWorkerCount}` workers. `${workersOnLatestCount}` *${label}* workers are on the latest revision: `${revision}` (Remember, we still have to wait for the remaining `${offlineWorkerCount}` worker(s) to be replaced). :party-jenkins:"
  } else {
    message = "[${jenkins_env}] Offlined `${offlineWorkerCount}` workers. `${workersOnLatestCount}` *${label}* workers are now on the latest revision: `${revision}`."
  }

  message = message + " (<${env.BUILD_URL}|Classic UI> | <${env.RUN_DISPLAY_URL}|Blue Ocean>)"
  slackSend(channel: getChannel(), message: message)
}
