import com.lesfurets.jenkins.unit.BasePipelineTest

import org.braintree.servicenow.ServiceNow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import net.sf.json.JSONObject

import static org.junit.Assert.*
import static org.mockito.Mockito.*

class NotifyServiceNowTests extends BasePipelineTest {
    def notifyServiceNow
    Map env

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        // Reduce verbosity
        helper.registerAllowedMethod('echo', [String.class])

        helper.registerAllowedMethod('findApplicationName', []) { return "braintree/jenkinsfile_shared_libraries" }

        notifyServiceNow = loadScript("vars/notifyServiceNow.groovy")
    }

    @Test
    void testNotifyServiceNow_WhenTicketEnvIsNotSet_DoNotNotifyServiceNow() {
        ServiceNow sn = mock(ServiceNow.class)
        notifyServiceNow(serviceNow: sn)
        verify(sn, never()).moveChangeRequestToReview(anyString())
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Successful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("post-deployment"))
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Unsuccessful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("rollback"))
        verify(sn, never()).cancelChangeRequest(anyString())
    }

    @Test
    void testNotifyServiceNow_WhenTicketIsNotInImplementState_DoNotUpdateChangeRequest() {
        env = [
            CHG_TICKET: "111"
        ]
        binding.setVariable('env', env)
        ServiceNow sn = mock(ServiceNow.class)
        doReturn(false).when(sn).isTicketInState(anyString(), eq("Implement"))

        notifyServiceNow(serviceNow: sn)
        verify(sn, never()).moveChangeRequestToReview(anyString())
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Successful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("post-deployment"))
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Unsuccessful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("rollback"))
    }

    @Test
    void testNotifyServiceNow_WhenDeploymentIsSuccessful_CloseWithSuccessCodeAndPromptChecklist() {
        env = [
            CHG_TICKET: "111",
            SUCCESSFUL_DEPLOYS: "ruby_example"
        ]
        binding.setVariable('env', env)
        ServiceNow sn = mock(ServiceNow.class)
        doReturn(true).when(sn).isTicketInState(anyString(), eq("Implement"))
        binding.getVariable('currentBuild').currentResult = 'SUCCESS'

        notifyServiceNow(serviceNow: sn)
        verify(sn, times(1)).moveChangeRequestToReview(anyString())
        verify(sn, times(1)).signOffChangeRequest(anyString(),eq("Successful"),anyString())
        verify(sn, times(1)).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("post-deployment"))
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Unsuccessful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("rollback"))
        verify(sn, never()).cancelChangeRequest(anyString())
    }

    @Test
    void testNotifyServiceNow_WhenDeploymentIsUnsuccessful_CloseWithUnsuccessfulCodeAndPromptChecklist() {
        env = [
            CHG_TICKET: "111",
            FAILED_DEPLOYS: "ruby_example"
        ]
        binding.setVariable('env', env)
        ServiceNow sn = mock(ServiceNow.class)
        doReturn(true).when(sn).isTicketInState(anyString(), eq("Implement"))
        binding.getVariable('currentBuild').currentResult = 'FAILURE'

        notifyServiceNow(serviceNow: sn)
        verify(sn, times(1)).moveChangeRequestToReview(anyString())
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Successful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("post-deployment"))
        verify(sn, times(1)).signOffChangeRequest(anyString(),eq("Unsuccessful"),anyString())
        verify(sn, times(1)).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("rollback"))
        verify(sn, never()).cancelChangeRequest(anyString())
    }

    @Test
    void testNotifyServiceNow_WhenPipelineAbortedInImplementState_DoNotUpdateChangeRequest() {
        env = [
            CHG_TICKET: "111"
        ]
        binding.setVariable('env', env)
        ServiceNow sn = mock(ServiceNow.class)
        doReturn(true).when(sn).isTicketInState(anyString(), eq("Implement"))
        binding.getVariable('currentBuild').currentResult = 'ABORTED'

        notifyServiceNow(serviceNow: sn)
        verify(sn, never()).moveChangeRequestToReview(anyString())
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Successful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("post-deployment"))
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Unsuccessful"),anyString())
        verify(sn, never()).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("rollback"))
    }

    @Test
    void testNotifyServiceNow_WhenMultipleDeployments_PromptChecklistMultipleTimes() {
        env = [
            CHG_TICKET: "111",
            FAILED_DEPLOYS: "ruby_example_1,ruby_example_2,ruby_exampl_3",
            SUCCESSFUL_DEPLOYS: "ruby_example"
        ]
        binding.setVariable('env', env)
        ServiceNow sn = mock(ServiceNow.class)
        doReturn(true).when(sn).isTicketInState(anyString(), eq("Implement"))
        binding.getVariable('currentBuild').currentResult = 'FAILURE'

        notifyServiceNow(serviceNow: sn)
        verify(sn, times(1)).moveChangeRequestToReview(anyString())
        verify(sn, never()).signOffChangeRequest(anyString(),eq("Successful"),anyString())
        verify(sn, times(1)).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("post-deployment"))
        verify(sn, times(1)).signOffChangeRequest(anyString(),eq("Unsuccessful"),anyString())
        verify(sn, times(3)).promptToCompleteChecklist(anyString(), anyString(), anyString(), eq("rollback"))
        verify(sn, never()).cancelChangeRequest(anyString())
    }
}
