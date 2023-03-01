import org.braintree.servicenow.ServiceNow

def call() {
    sn = new ServiceNow(this)
    def pipeline_stages = sn.retrievePipelineStages(env.BUILD_URL)
    echo sn.formatStageResults(pipeline_stages)
    echo sn.generateDiffLink()
    // def raw = sh(script: "curl https://jenkinsqa.braintree.tools/job/braintree/job/jenkins/job/master/513/wfapi/describe", returnStdout: true)
    // def response = readJSON(text: raw)
    // echo sn.formatStageResults(response["stages"]).
}
