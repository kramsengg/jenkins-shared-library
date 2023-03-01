import static org.braintree.DatadogMetricsUtil.*
import org.braintree.HistogramTimer
import java.util.concurrent.TimeUnit

import org.braintree.servicenow.ServiceNow

def call(Map options = [:], String target) {
  options.time = options.timeout_in ?: 45
  options.unit = options.timeout_unit ?: "MINUTES"
  options.applicationName = options.applicationName ?: target

  script {
    if (options.revision == null) {
      message = "required named parameter `revision`"
      echo message
      throw new Exception(message)
    }
    if (target == null) {
      message = "required parameter `target`"
      echo message
      throw new Exception(message)
    }

    dockerPull()
    def label = revisionAllowed(options, target) ? "production" : "production-sandbox"

    def withServiceNow = options.get("withServiceNow", false) || options.get("withTugboat", false)
    def defaultDiffInputValue = options.get("defaultDiffInputValue", false)
    def applicationName = options.applicationName ?: findApplicationName()
    def revision = options.revision

    // This is done to allow injecting a mock class into the script during testing
    ServiceNow sn = options.serviceNow ?: new ServiceNow(this)

    if (withServiceNow && !options.dryRun) {
      if (env.CM_PLATFORM == "servicenow") {
        sn.promptToCompleteChecklist(env.CHG_TICKET, "release_checklist.yaml", applicationName, "pre-deployment")
        sn.checkinChangeRequest(env.CHG_TICKET, "implement")
      } else {
        updateDeploymentCard(applicationName, "IN PROGRESS", revision)
      }
    }

    HistogramTimer timer = new HistogramTimer()
    timer.start()

    def statusCode = null
    def action = options.dryRun ? "attempting the dry run" : "deploying"
    def deployEnv = options.deployEnv
    String deployEnvPrefix = deployEnv != null ? "*[${deployEnv.toUpperCase()}]*" : ""

    if (!options.dryRun && options.deployNotifyChannel) {
      slackSend(
        channel: options.deployNotifyChannel,
        message: "${deployEnvPrefix} ${applicationName} `${revision}` is ${action}. (<${env.BUILD_URL}|Classic UI> | <${env.RUN_DISPLAY_URL}|Blue Ocean>)",
      )
    }

    nodeWithWorkspace(options.label ?: label) {
      timeoutWithRetryOnRemovedAgent(options) {
        checkout scm
        statusCode = sh(
          script: "sudo -EHnu deploy ${deployCommand(options, target)}",
          label: options.dryRun ? "Generate diff" : "Deploy",
          returnStatus: true
        )
      }
    }

    def successfulDryRunWithDiff = options.dryRun && options.exitDiff && statusCode == 2
    if(statusCode != 0 && !successfulDryRunWithDiff) {
      def exception = "Received a non-successful exit code ${statusCode} when ${action}."
      echo exception

      if (env.CM_PLATFORM == "servicenow" && withServiceNow && !options.dryRun) {
        if(env.FAILED_DEPLOYS) {
          env.FAILED_DEPLOYS = env.FAILED_DEPLOYS + "," + applicationName
        } else {
          env.FAILED_DEPLOYS = applicationName
        }
      }

      throw new Exception(exception)
    }

    String[] tags = tags(env.JOB_NAME, env.BUILD_ID, target: target, revision: revision)

    timer.end()

    def skipForEmptyDiff = options.dryRun && options.exitDiff && statusCode == 0
    // do not prompt for diff confirmation on PRs unless specified
    def shouldConfirmDiff = !skipForEmptyDiff && options.get("enableDiffConfirm", env.CHANGE_ID == null)
    if (options.dryRun && shouldConfirmDiff) {
      def channel = getChannel(options)
      slackHandle = options.get("slackHandle", options.get("handle",findSlackHandle()))
      promptDiffReviewConfirmation(channel, slackHandle, target, deployEnvPrefix, options.defaultDiffInputValue)
    }

    def dataDogClient = statsDClient(tags: tags)

    if (!options.dryRun) {
      dataDogClient.incrementCounter("deploy.invocation")
      dataDogClient.histogram("ci.bt_deploy.duration.seconds", timer.duration())

      Long totalDeployDuration = timer.end() - currentBuild.getStartTimeInMillis()
      dataDogClient.histogram("ci.total_deploy_pipeline.duration.millis", totalDeployDuration)
      dataDogClient.histogram("ci.total_deploy_pipeline.duration.seconds", TimeUnit.MILLISECONDS.toSeconds(totalDeployDuration))
    } else {
      dataDogClient.histogram("ci.bt_deploy.dryrun.duration.seconds", timer.duration())
    }

    if (withServiceNow && !options.dryRun && env.CM_PLATFORM == "servicenow") {
      if(env.SUCCESSFUL_DEPLOYS) {
        env.SUCCESSFUL_DEPLOYS = env.SUCCESSFUL_DEPLOYS + "," + applicationName
      } else {
        env.SUCCESSFUL_DEPLOYS = applicationName
      }
    }

    return statusCode
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

def revisionAllowed(options, target) {
  return sh(
    script: dockerCommand(deployCommand(options + [check_revision_allowed: true], target)),
    returnStatus: true,
    label: "Check if revision is on an allowed branch",
  ) == 0
}

def deployCommand(Map options, String target) {
  def command = "bt-deploy"
  if (options.revision) {
    command += " --revision=\"${options.revision}\""
  }
  if (options.dryRun) {
    command += " --dry-run"

    if (options.exitDiff) {
      command += " --exit-diff"
    }
  }
  if (options.credentialsId) {
    command += " --credentials-id=\"${options.credentialsId}\""
  }
  if (options.check_revision_allowed) {
    command += " --check-revision-allowed"
  }
  if (options.deployEnv) {
    command += " --deploy-env=\"${options.deployEnv}\""
  }
  if (options.whichDeployer) {
    command += " --which-deployer"
  }
  command += " \"${target}\""

  return command
}

def promptDiffReviewConfirmation(String channel, String slackHandle, String target, String deployEnvPrefix, Boolean defaultDiffInputValue) {
  String message = ""
  def deployer = whichDeployer(target)

  if (deployer  == "bt-tmux-deploy-k8s") {
    deployer = "Kubernetes"
  } else {
    deployer = deployer.capitalize()
  }

  message += "Please review the ${deployer} diff. Confirm in Jenkins Pipeline for ${env.JOB_NAME} #${env.BUILD_NUMBER} [${target}]\n(<${env.BUILD_URL}/input|Classic UI> | <${env.RUN_DISPLAY_URL}|Blue Ocean>) ${slackHandle}\n"

  slackSend(
    channel: channel,
    message: message ,
  )

  def inputSubmission = inputWithTimeout(
    submitterParameter: 'submitter',
    message: "Please review the ${deployer} diff in the 'Generate Diff' step.",
    // the input step plugin capitalizes the first character of the provided input...for some reason...
    id: UUID.randomUUID().toString().capitalize(),
    parameters: [
      booleanParam(
        name: "I have reviewed the ${deployer} diff in the 'Generate Diff' step and want to apply this diff.",
        defaultValue: defaultDiffInputValue,
        description: "After confirming, reload the browser to show the authorization step.",
      )
    ]
  )

  Boolean confirmation = inputSubmission."I have reviewed the ${deployer} diff in the 'Generate Diff' step and want to apply this diff."

  if (confirmation == false) {
    diffNotConfirmedMessage = "${deployEnvPrefix} The ${deployer} diff was not reviewed. Please re-run the pipeline and check the box that you've reviewed the diff."
    echo diffNotConfirmedMessage
    throw new Exception(diffNotConfirmedMessage)
  }
  else {
    slackSend(channel: channel, color: "good", message: "${deployEnvPrefix} The ${deployer} diff for ${env.JOB_NAME} #${env.BUILD_NUMBER} [${target}] was reviewed by ${inputSubmission.submitter}")
  }
}

def whichDeployer(String target) {
  return sh(
    script: "bash -o pipefail -c \"${dockerCommand(deployCommand([whichDeployer: true], target))} | tail -1 | awk '{print \$NF}'\"",
    returnStdout: true,
    label: "Check which deployer is for the specified target",
  ).trim()
}
