import org.braintree.servicenow.ServiceNow

def call(Map options = [:]) {
    // This is done to allow injecting a mock class into the script during testing
    ServiceNow sn = options.serviceNow ?: new ServiceNow(this)
    if (!env.CHG_TICKET) {
        echo "CHG_TICKET not set, likely because deploy did not create a ChangeRequest on ServiceNow. This is not an error. ServiceNow will not be notified of the build result."
        return
    } else {
        closeCR(sn)
        promptClosureChecklists(sn)
    }
}

def closeCR(ServiceNow sn) {
    String successCloseNote = "Jenkins build status Successful, closing CR"
    String failureCloseNote = "Jenkins build status Unsuccessful, closing CR"

    echo "Ticket in Implement state: " + sn.isTicketInState(env.CHG_TICKET, "Implement")
    if (sn.isTicketInState(env.CHG_TICKET, "Implement")) {
        if (currentBuild.currentResult == "SUCCESS") {
            sn.moveChangeRequestToReview(env.CHG_TICKET)
            sn.signOffChangeRequest(env.CHG_TICKET, "Successful", successCloseNote)
        } else if (currentBuild.currentResult == "FAILURE") {
            sn.moveChangeRequestToReview(env.CHG_TICKET)
            sn.signOffChangeRequest(env.CHG_TICKET, "Unsuccessful", failureCloseNote)
        }
    }
}

def promptClosureChecklists(ServiceNow sn) {
    if (env.FAILED_DEPLOYS) {
        echo "FAILED_DEPLOYS are: " + env.FAILED_DEPLOYS
        env.FAILED_DEPLOYS.split(",").each { appName->
            echo "Request rollback checklist for: " + appName
            sn.promptToCompleteChecklist(env.CHG_TICKET, "release_checklist.yaml", appName, "rollback")
        }
    }
    if (env.SUCCESSFUL_DEPLOYS) {
        echo "SUCCESSFUL_DEPLOYS are: " + env.SUCCESSFUL_DEPLOYS
        env.SUCCESSFUL_DEPLOYS.split(",").each {appName->
            echo "Request post-deployment checklist for: " + appName
            sn.promptToCompleteChecklist(env.CHG_TICKET, "release_checklist.yaml", appName, "post-deployment")
        }
    }
}