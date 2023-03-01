import com.lesfurets.jenkins.unit.BasePipelineTest

import org.braintree.servicenow.ServiceNow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*
import static org.mockito.Mockito.*

class BtDeployTests extends BasePipelineTest {
    def btDeploy
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
        binding.getVariable('currentBuild').getStartTimeInMillis = { return 10000 }

        // Reduce verbosity
        helper.registerAllowedMethod('echo', [String.class])

        helper.registerAllowedMethod('script', [Closure.class])
        helper.registerAllowedMethod('nodeWithWorkspace', [String.class, Closure.class])
        helper.registerAllowedMethod('timeoutWithRetryOnRemovedAgent', [Map.class, Closure.class])
        helper.registerAllowedMethod('updateDeploymentCard', [String.class, String.class, String.class])

        btDeploy = loadScript("vars/btDeploy.groovy")
    }

    @Test
    void testBtDeploy_WhenFeatureFlagNotSet_NoServiceNowCallsMade() {
        ServiceNow sn = mock(ServiceNow.class)

        def result = btDeploy(
            [revision: env.GIT_COMMIT, withTugboat: true, serviceNow: sn],
            "target"
        )

        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("pre-deployment"))
        verify(sn, never()).checkinChangeRequest(anyString(), anyString())

        // Verify tugboat is called
        helper.verify('updateDeploymentCard', 1)
    }

    @Test
    void testBtDeploy_WhenWithTugboatIsFalse_SnowNotUsed() {
        ServiceNow sn = mock(ServiceNow.class)

        binding.setVariable('env', binding.getVariable('env') + [CM_PLATFORM: 'servicenow'])
        btDeploy = loadScript("vars/btDeploy.groovy")

        def result = btDeploy(
            [revision: env.GIT_COMMIT, withTugboat: false, serviceNow: sn],
            "target"
        )

        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("pre-deployment"))
        verify(sn, never()).checkinChangeRequest(anyString(), anyString())
    }

    @Test
    void testBtDeploy_WhenFeatureFlagIsSet_SnowIsUsed() {
        ServiceNow sn = mock(ServiceNow.class)

        binding.setVariable('env', binding.getVariable('env') + [CM_PLATFORM: 'servicenow'])
        btDeploy = loadScript("vars/btDeploy.groovy")

        def result = btDeploy(
            [revision: env.GIT_COMMIT, withTugboat: true, serviceNow: sn],
            "target"
        )

        verify(sn, times(1)).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("pre-deployment"))
        verify(sn, times(1)).checkinChangeRequest(anyString(), eq("implement"))
    }

    @Test
    void testBtDeploy_WhenFeatureFlagAndWithServiceNowIsSet_SnowIsUsed() {
        ServiceNow sn = mock(ServiceNow.class)

        binding.getVariable("env").CM_PLATFORM = "servicenow"
        btDeploy = loadScript("vars/btDeploy.groovy")

        def result = btDeploy(
            [revision: env.GIT_COMMIT, withServiceNow: true, serviceNow: sn],
            "target"
        )

        verify(sn, times(1)).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("pre-deployment"))
        verify(sn, times(1)).checkinChangeRequest(anyString(), eq("implement"))
    }
}
