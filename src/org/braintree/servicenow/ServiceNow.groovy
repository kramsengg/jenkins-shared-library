package org.braintree.servicenow

import org.braintree.servicenow.exceptions.InvalidConfigurationItemException
import org.braintree.servicenow.exceptions.InvalidTemplateException
import org.braintree.servicenow.exceptions.ServiceNowException

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import jenkins.plugins.http_request.ResponseContentSupplier

import net.sf.json.JSONObject

import org.jenkinsci.plugins.webhookstep.WebhookToken

class ServiceNow implements Serializable {
    private def steps
    private def env
    private def cmEnv

    protected Boolean useGsnow
    protected String credentialsId
    protected String endpoint

    public ServiceNow(Script script, String cmEnv = null) {
        this.steps = script
        this.env = script.env

        if (cmEnv) {
            this.cmEnv = cmEnv
        } else {
            this.cmEnv = env.JENKINS_URL == "https://ci.braintree.tools/" ? EnvConfig.GSNOW_PROD : EnvConfig.GSNOW_QA
        }

        switch(this.cmEnv) {
            case EnvConfig.SNOW_DEV:
                this.useGsnow = false
                this.credentialsId = EnvConfig.SNOW_DEV_CREDENTIAL
                this.endpoint = EnvConfig.SNOW_DEV_ENDPOINT
                break
            case EnvConfig.GSNOW_QA:
                this.useGsnow = true
                this.credentialsId = EnvConfig.GSNOW_QA_CREDENTIAL
                this.endpoint = EnvConfig.GSNOW_QA_ENDPOINT
                break
            case EnvConfig.GSNOW_PROD:
                this.useGsnow = true
                this.credentialsId = EnvConfig.GSNOW_PROD_CREDENTIAL
                this.endpoint = EnvConfig.GSNOW_PROD_ENDPOINT
                break
            default:
                throw new ServiceNowException(
                    "Expected one of ['${EnvConfig.SNOW_DEV}', '${EnvConfig.GSNOW_QA}', '${EnvConfig.GSNOW_PROD}'] but got ${cmEnv}"
                )
        }
    }

    public promptToCompleteChecklist(String ticket, String filePath, String appName, String stage) {
        List<String> checklist
        try {
            Map releaseChecklists = parseChecklist(filePath)
            checklist = getAppChecklistByStage(releaseChecklists, appName, stage)
        } catch (FileNotFoundException e) {
            steps.echo "Could not find ${filePath}. Skipping release checklist prompt..."
            return
        } catch (ServiceNowException e) {
            steps.echo e.getMessage() + " " + "Skipping release checklist prompt..."
            return
        }

        HashMap submission = steps.input(
            message: "Please complete the ${stage} checklist",
            parameters: formatChecklist(checklist),
            submitterParameter: "CHECKLIST_SUBMITTER"
        )

        Map parsedSubmission = parseChecklistSubmission(submission)
        if (parsedSubmission.incompleteItems) {
            HashMap reprompt = steps.input(
                message: "The following ${stage} checklist items were incomplete. Please complete these items or add a reason for why these were not completed.",
                parameters: formatChecklist(parsedSubmission.incompleteItems) + [steps.text(name: "reason", trim: true)],
                submitterParameter: "CHECKLIST_SUBMITTER"
            )

            Map repromptParsed = parseChecklistSubmission(reprompt)
            parsedSubmission.completedItems = parsedSubmission.completedItems + repromptParsed.completedItems
            parsedSubmission.incompleteItems = repromptParsed.incompleteItems
            parsedSubmission.reason = repromptParsed.reason
        }

        // Allows this method to be used for completing checklists without needing a SNOW ticket
        if (ticket != null) {
            String workNote = formatChecklistSubmission(parsedSubmission, stage)
            publishWorkNotes(ticket, workNote)
        }
    }

    public String formatChecklistSubmission(Map parsedSubmission, String stage) {
        String workNote = ""

        if (parsedSubmission.completedItems) {
            workNote += "${parsedSubmission.submitter} completed the following ${stage} items:"

            parsedSubmission.completedItems.each { item ->
                workNote += "\n" + "|--" + " " + item
            }
        } else {
            workNote += "No ${stage} checklist items were completed by ${parsedSubmission.submitter}"
        }

        workNote += "\n\n"

        if (parsedSubmission.incompleteItems) {
            workNote += "The following ${stage} checklist items are incomplete:"

            parsedSubmission.incompleteItems.each { item ->
                workNote += "\n" + "|--" + " " + item
            }

            if (parsedSubmission.reason) {
                workNote += "\n\n" + "The following reason was given for the incomplete items:"
                workNote += "\n" + "|--" + " " + parsedSubmission.reason
            } else {
                workNote += "\n\n" + "No reason given for the incomplete items"
            }
        } else {
            workNote += "There are no incomplete items. All items were completed."
        }

        return workNote
    }

    public Boolean isTicketInState(String ticket, String state) {
        JSONObject response

        if (useGsnow) {
            response = makeRequest("GET", "/change_request?ticket=${ticket}")
        } else {
            response = makeRequest("GET", "/change_request/number/${ticket}")
        }

        return response.result[0].state == state.capitalize()
    }

    public Map parseChecklistSubmission(HashMap submission) {
        List<String> completedItems = []
        List<String> incompleteItems = []

        String submitter = submission.remove("CHECKLIST_SUBMITTER")
        String reason
        if (submission.containsKey("reason")) {
            reason = submission.remove("reason")
        }

        submission.each { item,complete ->
            if (complete) {
                completedItems.add(item)
            } else {
                incompleteItems.add(item)
            }
        }

        return [completedItems: completedItems, incompleteItems: incompleteItems, submitter: submitter, reason: reason]
    }

    public List<String> getAppChecklistByStage(Map releaseChecklists, String appName, String stage) throws ServiceNowException {
        List<String> checklist = []
        if (releaseChecklists.containsKey(appName)) {
            Map checklists = releaseChecklists[appName]

            if (checklists.containsKey(stage)) {
                checklist = checklists[stage]
            } else {
                throw new ServiceNowException("No checklist found for stage: ${stage}.")
            }
        } else {
            throw new ServiceNowException("No checklists found for app name: ${appName}.")
        }

        return checklist
    }

    public LinkedHashMap parseChecklist(String filePath = "release_checklist.yaml") throws FileNotFoundException {
        LinkedHashMap checklist

        if (steps.fileExists(filePath)) {
            checklist = steps.readYaml(file: filePath)
        } else {
            throw new FileNotFoundException("Could not find ${filePath}.")
        }

        return checklist
    }

    public List formatChecklist(List<String> checklist) {
        List parameters = []

        checklist.each { item ->
            parameters.add(steps.booleanParam(name: item))
        }

        return parameters
    }

    public String formatStageResults(List<Map> results) {
        String output = ""
        results.each{ stage ->
            output += stage["name"] + "\n"
            output += "  |-- Status: " + stage["status"] + "\n"
            output += "  |-- Duration: " + stage["durationMillis"] + " ms\n"
        }

        return output
    }

    public List<Map> retrievePipelineStages(String build_url) {
        String url = build_url + "wfapi/describe"
        def response = steps.httpRequest(url: url, authentication: 'JENKINS_USER_AND_TOKEN')
        def responseJSON = steps.readJSON(text: response.content)
        return responseJSON["stages"]
    }

    public String generateDiffLink() {
        // Matches the org/repo in either an https or ssh git url
        // Examples:
        //   git@github.braintreeps.com:braintree/ruby_example.git => braintree/ruby_example
        //   https://github.braintreeps.com:braintree/ruby_example.git => braintree/ruby_example
        String repoName = (env.GIT_URL =~ /(?:https:\/\/|http:\/\/|git@)?github\.braintreeps\.com(?:\/|:)(\w+\/[A-Za-z0-9_.-]+).git/)[0][1]

        String previousCommit
        if (env.BT_ROLLBACK_REVISION) {
            previousCommit = env.BT_ROLLBACK_REVISION
        } else if (env.GIT_PREVIOUS_COMMIT != env.GIT_COMMIT) {
            previousCommit = env.GIT_PREVIOUS_COMMIT
        } else {
            previousCommit = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
        }

        return "https://github.braintreeps.com/" + repoName + "/compare/" + previousCommit + "..." + env.GIT_COMMIT
    }

    public String getOrCreateChangeRequest(String application, String templateName = null, String changeType = "normal", String risk = "moderate") {
        String changeId

        // sysparm_query operators:
        // https://docs.servicenow.com/bundle/sandiego-platform-user-interface/page/use/common-ui-elements/reference/r_OpAvailableFiltersQueries.html
        String query = \
            "type=${changeType}" +
            "^u_brand.name=Braintree" +
            "^correlation_id=${env.GIT_COMMIT}" +
            "^u_site_components=${application}" +
            "^stateIN-5,-4,-3,-2"

        // query will throw a ServiceNowException if no results are returned
        // if a query succeeds, we can assume at least 1 result is included in the response
        try {
            steps.echo "Querying for existing CHG ticket..."
            JSONObject response = queryForChangeRequest(query)

            changeId = response.result[0].number
            steps.echo "Found CHG ticket, ${changeId}, using this instead of creating a new ticket."
        } catch (ServiceNowException e) {
            changeId = cycleThroughTemplates(application, templateName, changeType, risk)
        }

        return changeId
    }

    public JSONObject queryForChangeRequest(String sysparmQuery) throws ServiceNowException {
        String basePath = useGsnow ? "/change_request/query" : "/change_request"

        return makeRequest(
            "GET",
            basePath + "?" + "sysparm_query=${java.net.URLEncoder.encode(sysparmQuery, 'UTF-8')}"
        )
    }

    public String cycleThroughTemplates(String application, String templateName = null, String changeType = "normal", String risk = "moderate") throws InvalidTemplateException {
        // Implicit app-template + Jenkins-specific release template for fallback
        List<String> templates = [
            "BT-${application.capitalize()}-${risk.capitalize()}-${changeType.capitalize()}",
            "BT Jenkins - Jenkins Normal Release Template"
        ]

        if (templateName) {
            templates.add(0, templateName)
        }

        Boolean promptToContinue = true
        String chgTicket
        Exception lastException
        for (String template in templates) {
            try {
                chgTicket = createChangeRequest(application, template)
                break
            } catch (InvalidTemplateException e) {
                lastException = e

                // If an explicit template is passed in prompt the deployer to confirm if they want to continue with a default template or not
                if (templateName && promptToContinue) {
                    steps.input(
                        message: """
                            Unable to apply template '${templateName}' due to the following exception:

                            ${e.toString()}

                            Select 'Proceed' to continue to deployment using a default template or 'Abort' to cancel the pipeline.
                        """.stripIndent().trim()
                    )

                    // Only prompt to continue once
                    promptToContinue = false
                }
            }
        }

        if (chgTicket == null) {
            throw lastException
        }

        return chgTicket
    }

    public String createChangeRequest(
        String application,
        String templateName = "BT Jenkins - Jenkins Normal Release Template")
    throws InvalidTemplateException, ServiceNowException {


        Map payload = [
            cmdb_ci: application,
            correlation_id: env.GIT_COMMIT,
            start_date: getStartTime(),
            u_atb_cust_impact: "0", // TODO: Determine if we always want to set this to zero or let it be user defined?
            u_brand: "Braintree",
            u_category_subtype: "Deployment",
            u_category_type: "Automation",
            u_deployment_vehicle: "Braintree Jenkins",
            u_duration: "30", // Based on lifetime of cicd oidc token for k8s
            u_service_category: "Jenkins Automation",
            u_site_components: application,
            u_site_impact: "No impact", // TODO: Determine what this should be set to
            u_template: templateName
        ]

        String deployer = determineUserFromBuildCause()
        payload["requested_by"] = deployer

        if (deployer != "api_bo9pace") {
            payload["assigned_to"] = deployer
        }

        // TODO: Update/remove fields as needed per environement
        // TODO: Update to using 'useGsnow' flag once SNOW_DEV(paypaldevproject) is no longer in use.
        switch(cmEnv) {
            case EnvConfig.SNOW_DEV:
                payload["correlation_display"] = "BT-Jenkins"
                break
            default:
                payload["stack_info"] = "BT Jenkins"
                break
        }

        steps.echo "Creating CHG ticket for this deployment..."
        JSONObject response
        try {
            response = makeRequest(
                "POST",
                "/change_request",
                payload
            )
        } catch (InvalidConfigurationItemException ignored) {
            // Removing the CI from the payload will cause technical approval to be delegated to BT release-team in SNOW
            payload.remove("cmdb_ci")
            response = makeRequest(
                "POST",
                "/change_request",
                payload
            )
        }

        String ticket = response.result.record_id

        publishDeploymentInfoToTicket(ticket, application)
        steps.echo "Created CHG ticket: ${ticket} for this deployment."
        return ticket
    }

    public cancelChangeRequest(String ticket) {
        Map payload = [
            state: "canceled"
        ]

        steps.echo "Cancelling CHG ticket: ${ticket}."

        if (useGsnow) {
            payload["ticket"] = ticket
            makeRequest("POST", "/change_request/update", payload)
        } else {
            makeRequest("PATCH", "/change_request/number/${ticket}", payload)
        }

    }

    public checkinChangeRequest(String ticket, String state) {
        if (!["scheduled", "implement"].contains(state)) {
            throw new ServiceNowException("Received a state of '${state}', expected one of 'scheduled' or 'implement'.")
        }

        Map payload = [
            state: state
        ]

        steps.echo "Moving CHG ticket, ${ticket}, to ${state.capitalize()} state."
        if (useGsnow) {
            payload["ticket"] = ticket
            makeRequest("POST", "/change_request/checkin", payload)
        } else {
            makeRequest("PATCH", "/change_request/number/${ticket}", payload)
        }
    }

    public moveChangeRequestToReview(String ticket) {
        Map payload = [
            state: "review"
        ]

        steps.echo "Moving CHG ticket, ${ticket}, to the Review state."
        if (useGsnow) {
            payload["ticket"] = ticket
            makeRequest("POST", "/change_request/update", payload)
        } else {
            makeRequest("PATCH", "/change_request/number/${ticket}", payload)
        }
    }

    public requestApproval(String ticket) {
        steps.echo "Requesting approval for CHG ticket: ${ticket}."
        JSONObject response = waitForChangeApproval(ticket) {
            moveChangeRequestToAssess(ticket)
        }

        if (response.state == "Canceled") {
            String cancelMessage = \
                "The change request for this deployment, ${ticket}, was cancelled. " +
                "If this change still needs to be deployed, please re-build this pipeline to generate " +
                "a new change request and re-attempt the deploy."
            steps.echo cancelMessage
            throw new ServiceNowException(cancelMessage)
        }

        if (response.approval != "Approved") {
            String rejectMsg = \
                "The change request for this deployment (${ticket}) was rejected by ${response.rejected_by}."

            if (!response.rejection_comments.isEmpty()) {
                rejectMsg += "\nThe reason given for this rejection was: ${response.rejection_comments}"
            }

            publishWorkNotes(ticket, rejectMsg)
            cancelChangeRequest(ticket)
            steps.echo rejectMsg
            throw new ServiceNowException(rejectMsg)
        }

        if (response.TbStop != "off") {
            String tbStopMessage = \
                "Unable to proceed with deployment as TBSTOP is currently ON for Braintree. " +
                "Please retry this deployment at a later time when TBSTOP is OFF for Braintree. " +
                "If this ticket (${ticket}) is already approved and in the Scheduled state, " +
                "you will not need to go through the approval process again."
            steps.echo tbStopMessage
            throw new ServiceNowException(tbStopMessage)
        }

        steps.echo "CHG ticket, ${ticket}, has been approved in ServiceNow. Continuing to deployment..."
    }

    public signOffChangeRequest(String ticket, String closeCode, String closeNotes) {
        Map payload = [
            close_notes: closeNotes,
            close_code: closeCode,
            state: "closed"
        ]

        steps.echo "Closing CHG ticket, ${ticket}, as ${closeCode.capitalize()}."
        if (useGsnow) {
            payload["ticket"] = ticket
            makeRequest("POST", "/change_request/signoff", payload)
        } else {
            makeRequest("PATCH", "/change_request/number/${ticket}", payload)
        }
    }

    public JSONObject getChangeRequest(String ticket) {
        JSONObject response
        if (useGsnow) {
            response = makeRequest("GET", "/change_request?ticket=${ticket}")
        } else {
            response = makeRequest("GET", "/change_request/number/${ticket}")
        }

        return response.result[0]
    }

    public publishWorkNotes(String ticket, String workNotes) {
        Map payload = [
            work_notes: workNotes
        ]

        if (useGsnow) {
            payload["ticket"] = ticket
            makeRequest("POST", "/change_request/update", payload)
        } else {
            makeRequest("PATCH", "/change_request/number/${ticket}", payload)
        }
    }

    public publishDeploymentInfoToTicket(String ticket, String application) {
        String gitBuildInfo = """
            Build URL: ${env.RUN_DISPLAY_URL}
            Diff link: ${generateDiffLink()}
            Git Commit SHA1: ${env.GIT_COMMIT}
        """.stripIndent().trim()

        if (!env.TAG_NAME) {
            publishWorkNotes(ticket, "Releasing commit ${env.GIT_COMMIT} for ${application}\n\n${gitBuildInfo}")
        } else {
            String repoName = (env.GIT_URL =~ /(?:https:\/\/|http:\/\/|git@)?github\.braintreeps\.com(?:\/|:)(\w+\/[A-Za-z0-9_.-]+).git/)[0][1]
            String tagURL = "https://github.braintreeps.com/" + repoName + "/releases/tag/" + env.TAG_NAME
            publishWorkNotes(ticket, "Releasing tag ${env.TAG_NAME} for ${application}\nTag URL: ${tagURL}\n\n${gitBuildInfo}")
        }
    }

    protected JSONObject waitForChangeApproval(String ticket, Closure preApprovalSteps) {
        JSONObject response

        // Lock used to ensure no concurrency issues when multiple jobs attempt to create an approval webhook
        steps.lock("cr-webhook-${env.GIT_COMMIT}") {
            preApprovalSteps()

            steps.timeout(time: 2, unit: "HOURS") {
                // Maintain lock and re-generate webhook URL if payload is received
                // for a ticket that doesn't match the expected ticket
               while (response == null || response["ticket"] != ticket) {
                   WebhookToken wh = steps.registerWebhook(token: env.GIT_COMMIT)
                   steps.echo "Waiting for ServiceNow to notify of approvals at ${wh.getUrl()}..."
                   String raw = steps.waitForWebhook(wh)
                   response = steps.readJSON(text: raw)
               }
            }
        }

        return response
    }

    protected moveChangeRequestToAssess(String ticket) {
        Map payload = [
            state: "assess"
        ]

        steps.echo "Moving CHG ticket, ${ticket}, to the Assess state."
        if (useGsnow) {
            payload["ticket"] = ticket
            makeRequest("POST", "/change_request/update", payload)
        } else {
            makeRequest("PATCH", "/change_request/number/${ticket}", payload)
        }
    }

    protected JSONObject makeRequest(String httpMethod, String path = "", Map payload = [:])
        throws ServiceNowException, InvalidTemplateException, InvalidConfigurationItemException {
        Boolean quiet = true

        if (env.DEBUG_SNOW_REQUESTS) {
            quiet = false
        }

        // Always include this as part of the payload
        payload["u_modified_by"] = "api_bo9pace"

        Map opts = [
            url: endpoint + path,
            acceptType: "APPLICATION_JSON",
            httpMode: httpMethod,
            requestBody: steps.writeJSON(json: payload, returnText: true),
            quiet: quiet,
            validResponseCodes: "100:599" // Prevent httpRequest from failing the pipeline
        ]

        ResponseContentSupplier raw
        if (useGsnow) {
            steps.withCredentials([steps.string(credentialsId: credentialsId, variable: "GSNOW_TOKEN")]) {
                opts["customHeaders"] = [[maskValue: true, name: "Authorization", value: "Token ${env.GSNOW_TOKEN}"]]
                raw = steps.httpRequest(opts)
            }
        } else {
            opts["authentication"] = credentialsId
            raw = steps.httpRequest(opts)
        }

        if (!quiet) {
            steps.echo "${raw.getStatus()}" + ":\n" + raw.getContent().toString()
        }

        JSONObject response = steps.readJSON(text: raw.getContent())

        if (response.status == "failure") {
            if (response.error.message == ServiceNowErrors.INVALID_TEMPLATE.getMessage() &&
                response.error.detail == ServiceNowErrors.INVALID_TEMPLATE.getDetail()
            ) {
                String exceptionMsg = ServiceNowErrors.INVALID_TEMPLATE.toString() + " for template: " + payload["u_template"]
                throw new InvalidTemplateException(exceptionMsg)
            } else if (response.error.message.endsWith(ServiceNowErrors.INVALID_CONFIGURATION_ITEM.getMessage())) {
                throw new InvalidConfigurationItemException("The provided CI is not available in ServiceNow.")
            } else {
                throw new ServiceNowException(response)
            }
        }

        return response
    }

    protected String determineUserFromBuildCause() {
        // api_bo9pace is the GSNOW User
        String user = "api_bo9pace"

        if (!steps.currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause').isEmpty()) {
            user = steps.currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')[0].userId
        } else if (!steps.currentBuild.getBuildCauses('org.jenkinsci.plugins.github_branch_source.GitHubSenderCause').isEmpty()) {
            user = steps.currentBuild.getBuildCauses('org.jenkinsci.plugins.github_branch_source.GitHubSenderCause')[0].login
        }

        return user
    }

    protected String getStartTime() {
        // ServiceNow expects Datetime fields to be sent using the Pacific timezone
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"))

        return now.plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
}
