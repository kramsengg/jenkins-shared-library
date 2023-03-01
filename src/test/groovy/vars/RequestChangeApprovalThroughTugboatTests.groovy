import com.lesfurets.jenkins.unit.BasePipelineTest

import org.jenkinsci.plugins.webhookstep.WebhookToken
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*

class RequestChangeApprovalThroughTugboatTests extends BasePipelineTest {
    def requestChangeApprovalThroughTugboat
    Map env

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        env = [
            GIT_COMMIT: "111",
            CHG_TICKET: "CHG1234567"
        ]
        binding.setVariable('env', env)

        // Reduce verbosity
        helper.registerAllowedMethod('echo', [String.class])

        helper.registerAllowedMethod('script', [Closure.class])
        helper.registerAllowedMethod('requestChangeApprovalThroughServiceNow', [Map.class])
        helper.registerAllowedMethod('rollbackRevision', [Map.class])
        helper.registerAllowedMethod('registerWebhook', []) {
            return new WebhookToken("random-token", "http://jenkins_url/webhook-step/random-token", null)
        }
        helper.registerAllowedMethod('startTugboatDeploy', [String.class, String.class, Map.class])
        helper.registerAllowedMethod('waitForWebhook', [WebhookToken.class])
        helper.registerAllowedMethod('readJSON', [Map.class]) { return [status: "approved"] }

        requestChangeApprovalThroughTugboat = loadScript("vars/requestChangeApprovalThroughTugboat.groovy")
    }

    @Test
    void testRequestChangeApprovalThroughTugboat_WhenFeatureFlagIsSet_RequestChangeApprovalThroughServiceNow() {
        binding.setVariable('env', binding.getVariable('env') + [CM_PLATFORM: 'servicenow'])

        def requestChangeApprovalThroughTugboat = loadScript("vars/requestChangeApprovalThroughTugboat.groovy")
        requestChangeApprovalThroughTugboat([:])

        helper.verify('requestChangeApprovalThroughServiceNow', 1)

        // verifies we don't request approval through both SNOW & Tugboat
        helper.verify('script', 0)
    }

    @Test
    void testRequestChangeApprovalThroughTugboat_WhenFeatureFlagIsNotSet_RequestChangeApprovalThroughTugboat() {
        requestChangeApprovalThroughTugboat([:])

        helper.verify('requestChangeApprovalThroughServiceNow', 0)

        // verifies we requested approval through Tugboat
        helper.verify('script', 1)
    }
}
