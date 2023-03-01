def call(Map args = [:], Object targets) {
  script {
    String revision = args.revision
    if (revision == null) {
      throw new Exception("required named parameter `revision` was missing")
    }
    String deployEnv = args.deployEnv
    if (deployEnv == null) {
      throw new Exception("required named parameter `deployEnv` was missing")
    }
    String channel = args.channel
    if (channel != null && !(channel =~ /^#/)) {
      throw new Exception("malformed channel `${channel}`")
    }

    def deployNotifyChannel = args.deployNotifyChannel
    if (deployNotifyChannel != null && !(deployNotifyChannel =~ /^#/)) {
      throw new Exception("malformed deployNotifyChannel `${deployNotifyChannel}`")
    }

    String handle = args.handle
    if (handle != null && !(handle =~ /^@/)) {
      throw new Exception("malformed handle `${handle}`")
    }

    Boolean manuallySetRollback = args.get("manuallySetRollback", false)

    if(targets instanceof String) {
      targets = [targets]
    }
    if(!(targets instanceof List<String>)) {
      throw new Exception("`targets` parameter must be either a String or List<String>")
    }

    String repository = args.get("repository", findRepository())
    Boolean skipSandDiffConfirm = args.get("skipSandDiffConfirm", true)
    Boolean enableDiffConfirm = args.get("enableDiffConfirm", true)

    def timeout_in = args.get("timeout_in", 45)
    def timeout_unit = args.get("timeout_unit", "MINUTES")

    String rollbackRevision = args.rollbackRevision

    String templateName = args.get("templateName", "")
    String changeTicketType = args.get("changeTicketType", "normal")
    String risk = args.get("risk", "moderate")

    targets.each { target ->
      shipTarget(target, revision, deployEnv, channel, handle, repository, manuallySetRollback, enableDiffConfirm, skipSandDiffConfirm, timeout_in, timeout_unit, rollbackRevision, deployNotifyChannel, templateName, changeTicketType, risk)
    }
  }
}

def shipTarget(String target, String revision, String deployEnv, String channel, String handle, String repository, Boolean manuallySetRollback, Boolean enableDiffConfirm, Boolean skipSandDiffConfirm, int timeout_in, String timeout_unit, String rollbackRevision, String deployNotifyChannel, String templateName, String changeTicketType, String risk) {
  def deployArgs = [
    revision: revision,
    deployEnv: deployEnv,
    channel: channel,
    timeout_in: timeout_in,
    timeout_unit: timeout_unit,
    deployNotifyChannel: deployNotifyChannel
  ]

  if(isHigherLevelEnv(deployEnv)) {
    deployArgs["handle"] = handle

    if (isSandEnv(deployEnv) && skipSandDiffConfirm) {
      enableDiffConfirm = false
    }

    def statusCode = btDeploy(
      deployArgs + [dryRun: true, exitDiff: true, enableDiffConfirm: enableDiffConfirm],
      target
    )
    if(statusCode == 0) {
      echo "No diff was found in the target environment. Skipping apply because there are no changes."
      return
    }

    if(isProdEnv(deployEnv)) {
      if (env.CM_PLATFORM == "servicenow") {
        deployArgs["withServiceNow"] = true
        requestChangeApprovalThroughServiceNow(
          applicationName: target,
          channel: channel,
          slackHandle: handle,
          manuallySetRollback: manuallySetRollback,
          rollbackRevision: rollbackRevision,
          templateName: templateName,
          changeTicketType: changeTicketType,
          risk: risk,
        )
      } else {
        deployArgs["withTugboat"] = true
        startTugboat(
          applicationName: target,
          revision: revision,
          channel: channel,
          slackHandle: handle,
          manuallySetRollback: manuallySetRollback,
          rollbackRevision: rollbackRevision,
        )
      }
    }

    def credentialsIdMap = requestAuthorization(
      revision: revision,
      targets: [target],
      repository: repository,
      channel: channel,
      handle: handle,
      deployEnv: deployEnv
    )
    deployArgs["credentialsId"] = credentialsIdMap[target]
  }

  echo "Deploying ${target} @ ${revision} to ${deployEnv}"

  if(env.CHANGE_ID) {
    deployArgs["dryRun"] = true
    echo "btShipIt will do a dryRun for pull requests. If you want to test a deploy, use a pipeline for a feature branch which will only be allowed to deploy to dev and qa dimensions."
  }

  btDeploy(
    deployArgs,
    target
  )
}

def isProdEnv(String deployEnv) {
  return ["prod", "production"].any { prodEnv -> deployEnv.endsWith(prodEnv) }
}

def isSandEnv(String deployEnv) {
  return ["sand", "sandbox", "datastag"].any { sandEnv -> deployEnv.endsWith(sandEnv) }
}

def isHigherLevelEnv(String deployEnv) {
  return isSandEnv(deployEnv) || isProdEnv(deployEnv)
}
