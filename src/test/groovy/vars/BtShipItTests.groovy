import com.lesfurets.jenkins.unit.BasePipelineTest

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*

class BtShipItTests extends BasePipelineTest {
    def btShipIt
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
        helper.registerAllowedMethod('findRepository', []) { return "braintree/jenkinsfile_shared_libraries" }
        helper.registerAllowedMethod('btDeploy', [Map.class, String.class])
        helper.registerAllowedMethod('requestChangeApprovalThroughServiceNow', [Map.class])
        helper.registerAllowedMethod('startTugboat', [Map.class])
        helper.registerAllowedMethod('requestAuthorization', [Map.class]) { return [targetName: []]}

        btShipIt = loadScript("vars/btShipIt.groovy")
    }

    @Test
    void testBtShipIt_WhenFeatureFlagNotSet_UseStartTugboat() {
        btShipIt(
            "targetName",
            revision: env.GIT_COMMIT,
            deployEnv: "prod",
            channel: "#dummy-slack-channel-1234"
        )

        helper.verify('startTugboat', 1)
        helper.verify('requestChangeApprovalThroughServiceNow', 0)
    }

    @Test
    void testBtShipIt_WhenFeatureFlatIsSet_RequestChangeApprovalThroughServiceNow() {
        binding.setVariable('env', binding.getVariable('env') + [CM_PLATFORM: 'servicenow'])

        def btShipIt = loadScript("vars/btShipIt.groovy")
        btShipIt(
            "targetName",
            revision: env.GIT_COMMIT,
            deployEnv: "prod",
            channel: "#dummy-slack-channel-1234"
        )

        helper.verify('startTugboat', 0)
        helper.verify('requestChangeApprovalThroughServiceNow', 1)
    }

    @Test
    void testBtShipIt_WhenLowerEnvDeployment_DoNotRequestApprovals() {
        binding.setVariable('env', binding.getVariable('env') + [CM_PLATFORM: 'servicenow'])

        def btShipIt = loadScript("vars/btShipIt.groovy")
        btShipIt(
            "targetName",
            revision: env.GIT_COMMIT,
            deployEnv: "qa",
            channel: "#dummy-slack-channel-1234"
        )

        helper.verify('startTugboat', 0)
        helper.verify('requestChangeApprovalThroughServiceNow', 0)
    }
}
