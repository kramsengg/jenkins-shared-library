import com.lesfurets.jenkins.unit.BasePipelineTest

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*

class StartTugboatTests extends BasePipelineTest {
    def startTugboat
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

        helper.registerAllowedMethod('requestChangeApprovalThroughServiceNow', [Map.class])
        helper.registerAllowedMethod('requestChangeApprovalThroughTugboat', [Map.class])

        startTugboat = loadScript("vars/startTugboat.groovy")
    }

    @Test
    void testStartTugboat_WhenFeatureFlagIsSet_RequestApprovalThroughServiceNow() {
        binding.setVariable('env', binding.getVariable('env') + [CM_PLATFORM: 'servicenow'])
        def startTugboat = loadScript("vars/startTugboat.groovy")

        startTugboat()

        helper.verify('requestChangeApprovalThroughServiceNow', 1)
        helper.verify('requestChangeApprovalThroughTugboat', 0)
    }

    @Test
    void testStartTugboat_WhenFeatureFlagIsNotSet_RequestApprovalThroughTugboat() {
        startTugboat()

        helper.verify('requestChangeApprovalThroughServiceNow', 0)
        helper.verify('requestChangeApprovalThroughTugboat', 1)
    }
}
