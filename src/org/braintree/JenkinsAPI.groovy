package org.braintree

import hudson.slaves.OfflineCause;
import org.jenkinsci.plugins.workflow.cps.CpsScript
import hudson.model.User;
import hudson.model.Computer;
import com.cloudbees.groovy.cps.NonCPS

@NonCPS
static List<String> markAgentsOffline(List<String> instanceIds, String offliningInstanceName, CpsScript script) {
  def offlineNames = []

  def validComputers = jenkins.model.Jenkins.instance.computers.findAll { it.node != null }
  validComputers.each { c ->
    def nodeName = c.node.selfLabel.name
    def isRolloutCandidate = instanceIds.any { i ->
      nodeName.contains(i)
    } && (nodeName != offliningInstanceName)

    if (isRolloutCandidate) {
      offlineNames += nodeName
      def offlineCauseText = "To be terminated for JPW Rollout - ${nodeName}"
      script.print(offlineCauseText)

      def offlineCause = new OfflineCause.UserCause(User.getById("jenkins", false), offlineCauseText)
      c.setTemporarilyOffline(true, offlineCause)
    }
  }
  return offlineNames
}

@NonCPS
static void cleanupOfflineAndIdleAgents(String label, CpsScript script) {
  findWorkersOnLabel(label).each { w ->
    if (w.isIdle() && !w.isOnline() && w.node != null) {
      script.print("Deleting `${w.node.selfLabel.name}`...")
      w.doDoDelete()
    }
  }
}

@NonCPS
static List<Computer> findWorkersOnLabel(String label) {
  return jenkins.model.Jenkins.instance.computers.findAll { c ->
    if (c.node == null) {
      return false
    }
    def nodeLabels = c.node.assignedLabels.collect{ it.name }
    return nodeLabels.contains(label)
  }
}

// always run rollout with a worker on the latest revision, if any are available
// e.g. we've run a rollout iteration and a worker created with latest revision is available
// if none are available, then pick the first worker for the label
// this is to avoid the race condition where only one worker is left
// to replace, but the rollout pipeline keeps picking that worker to run the job on
@NonCPS
static Computer findFirstAvailableLatestRevisionAgent(String label, List<String> instancesOnLatest, CpsScript script) {
  def workersMatchingLabel = findWorkersOnLabel(label)

  if (workersMatchingLabel.isEmpty()) {
    throw new Exception("Could not find any JPW workers with label: ${label}.")
  }

  def workersOnLatestRevision = workersMatchingLabel.findAll { c ->
    return instancesOnLatest.any { i ->
      c.node.selfLabel.name.contains(i)
    }
  }

  if (!workersOnLatestRevision.isEmpty()) {
    def latestWorker =  workersOnLatestRevision.find { w -> w.isOnline() && w.isIdle() && w.node != null }
    if (latestWorker != null) {
      return latestWorker
    }
  }

  def availableAgent = workersMatchingLabel.find { w -> w.isOnline() && w.isIdle() && w.node != null }
  if (availableAgent != null) {
    return availableAgent
  }

  throw new Exception("Could not find any available JPW workers with label: ${label}. All workers are either busy or offline.")
}
