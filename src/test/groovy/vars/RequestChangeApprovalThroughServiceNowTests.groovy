import com.lesfurets.jenkins.unit.BasePipelineTest

import org.braintree.servicenow.ServiceNow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import net.sf.json.JSONObject

import static org.junit.Assert.*
import static org.mockito.Mockito.*

class RequestChangeApprovalThroughServiceNowTests extends BasePipelineTest {
    def requestChangeApprovalThroughServiceNow
    Map env

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        env = [
            GIT_COMMIT: "111"
        ]
        binding.setVariable('env', env)

        // Reduce verbosity
        helper.registerAllowedMethod('echo', [String.class])

        helper.registerAllowedMethod('findApplicationName', []) { return "braintree/jenkinsfile_shared_libraries" }

        requestChangeApprovalThroughServiceNow = loadScript("vars/requestChangeApprovalThroughServiceNow.groovy")
    }

    @Test
    void testRequestChangeApprovalThroughServiceNow_WhenTicketAlreadyInScheduledState_DoNotRequestApproval() {
        ServiceNow sn = mock(ServiceNow.class)

        doReturn("CHG1234567").when(sn).getOrCreateChangeRequest(anyString(), anyString(), anyString(), anyString())
        doReturn("https://diff-link").when(sn).generateDiffLink()
        doReturn(null).when(sn).publishWorkNotes(anyString(), anyString())
        doReturn([[:]]).when(sn).retrievePipelineStages(anyString())
        doReturn("").when(sn).formatStageResults(anyList())
        doReturn(true).when(sn).isTicketInState(anyString(), eq("scheduled"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        requestChangeApprovalThroughServiceNow(serviceNow: sn)

        verify(sn, never()).requestApproval(anyString())
        verify(sn, never()).checkinChangeRequest(anyString(), eq("scheduled"))
    }

    @Test
    void testRequestChangeApprovalThroughServiceNow_WhenStandardChange_DoNotRequestApprovalAndMoveToScheduledState() {
        ServiceNow sn = mock(ServiceNow.class)

        doReturn("CHG1234567").when(sn).getOrCreateChangeRequest(anyString(), anyString(), anyString(), anyString())
        doReturn("https://diff-link").when(sn).generateDiffLink()
        doReturn(null).when(sn).publishWorkNotes(anyString(), anyString())
        doReturn([[:]]).when(sn).retrievePipelineStages(anyString())
        doReturn("").when(sn).formatStageResults(anyList())
        doReturn(true).when(sn).isTicketInState(anyString(), eq("scheduled"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        requestChangeApprovalThroughServiceNow(changeTicketType: "standard", serviceNow: sn)

        verify(sn, never()).requestApproval(anyString())
        verify(sn, times(1)).checkinChangeRequest(anyString(), eq("scheduled"))
    }

    @Test
    void testRequestChangeApprovalThroughServiceNow_WhenNormalChangeNotInScheduledState_RequestApproval() {
        ServiceNow sn = mock(ServiceNow.class)

        doReturn("CHG1234567").when(sn).getOrCreateChangeRequest(anyString(), anyString(), anyString(), anyString())
        doReturn("https://diff-link").when(sn).generateDiffLink()
        doReturn(null).when(sn).publishWorkNotes(anyString(), anyString())
        doReturn([[:]]).when(sn).retrievePipelineStages(anyString())
        doReturn("").when(sn).formatStageResults(anyList())
        doReturn(false).when(sn).isTicketInState(anyString(), eq("scheduled"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())
        doReturn(null).when(sn).requestApproval(anyString())

        requestChangeApprovalThroughServiceNow(changeTicketType: "normal", serviceNow: sn)

        verify(sn, times(1)).requestApproval(anyString())
        verify(sn, never()).checkinChangeRequest(anyString(), eq("scheduled"))
    }

    @Test
    void testRequestChangeApprovalThroughServiceNow_WhenRollbackRevisionSet_SetEnvironmentVariable() {
        ServiceNow sn = mock(ServiceNow.class)

        requestChangeApprovalThroughServiceNow(rollbackRevision: "abcd1234", serviceNow: sn)

        assertEquals(binding.getVariable("env").BT_ROLLBACK_REVISION, "abcd1234")
    }

    @Test
    void testRequestChangeApprovalThroughServiceNow_WhenRollbackRevisionNotSet_EmptyEnvironmentVariable() {
        ServiceNow sn = mock(ServiceNow.class)

        requestChangeApprovalThroughServiceNow(serviceNow: sn)

        assertEquals(binding.getVariable("env").BT_ROLLBACK_REVISION, "")
    }

    @Test
    void testRequestChangeApprovalThroughServiceNow_WhenManuallySetRollbackIsSet_InputIsSetInEnvVar() {
        ServiceNow sn = mock(ServiceNow.class)

        helper.registerAllowedMethod("inputWithTimeout", [Map.class]) { return "abcd1234" }

        requestChangeApprovalThroughServiceNow(manuallySetRollback: true, channel: "#def_not_a_real_channel_", serviceNow: sn)

        helper.verify("inputWithTimeout", 1)

        assertEquals(binding.getVariable("env").BT_ROLLBACK_REVISION, "abcd1234")
    }
}
