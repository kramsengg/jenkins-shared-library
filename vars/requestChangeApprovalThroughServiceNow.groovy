import org.braintree.servicenow.ServiceNow

def call(Map options = [:]) {
    // This is done to allow injecting a mock class into the script during testing
    ServiceNow sn = options.serviceNow ?: new ServiceNow(this)

    options["applicationName"] = options.get("applicationName", findApplicationName())
    options["templateName"] = options.get("templateName", "")
    options["changeTicketType"] = options.get("changeTicketType", "normal")
    options["risk"] = options.get("risk", "moderate")
    options["manuallySetRollback"] = options.get("manuallySetRollback", false)

    env.BT_ROLLBACK_REVISION = getRollbackRevision(options)

    env.CHG_TICKET = sn.getOrCreateChangeRequest(
        options["applicationName"],
        options["templateName"],
        options["changeTicketType"],
        options["risk"]
    )

    List<Map> pipeline_stages = sn.retrievePipelineStages(env.BUILD_URL)
    String stages = sn.formatStageResults(pipeline_stages)
    sn.publishWorkNotes(env.CHG_TICKET, stages)

    if (options["changeTicketType"] == "normal" && !sn.isTicketInState(env.CHG_TICKET, "scheduled")) {
        sn.requestApproval(env.CHG_TICKET)
    } else if(options["changeTicketType"] == "standard") {
        sn.checkinChangeRequest(env.CHG_TICKET, "scheduled")
    }
}

def getRollbackRevision (Map options) {
    if (options.rollbackRevision != null) {
        return options.rollbackRevision
    }

    if (!options.manuallySetRollback) {
        return ""
    }

    if (!options.containsKey('channel')) {
        String exception = """
        required named parameter `channel`
        If the manuallySetRollback is set to true, channel must be provided for notification.
        """

        echo exception
        throw new Exception(exception)
    }

    if ((env.JENKINS_URL == "https://ci.braintree.tools/") || (env.JENKINS_URL == "https://jenkins.jenkins-prod.braintree-api.com/")) {
        String channel = options.get('channel')
        String slackHandle = options.get("slackHandle", "")
        String applicationName = options.applicationName ?: findApplicationName()
        String deployerUsername = findUserFromBuildCause()
        String message = """
        Please input rollback revision. ${slackHandle}
        (<${env.BUILD_URL}/input|Classic UI> | <${env.RUN_DISPLAY_URL}|Blue Ocean>)
        Repository: ${applicationName}
        Build Name: ${env.JOB_NAME}
        Deployer: ${deployerUsername}
        """

        slackSend(channel: channel, message: message)
    }

    return inputWithTimeout(
        message: "Rollback Revision",
        id: "RollbackRevision",
        parameters: [
            [
                $class: 'TextParameterDefinition',
                name: "rollback_revision",
                defaultValue: "",
                description: "Please provide rollback revision.",
            ],
        ]
    )
}
