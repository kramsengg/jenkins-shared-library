package org.braintree.servicenow

import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.model.Result
import jenkins.model.CauseOfInterruption
import org.braintree.servicenow.exceptions.InvalidConfigurationItemException
import org.braintree.servicenow.exceptions.InvalidTemplateException
import org.braintree.servicenow.exceptions.ServiceNowException
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.mockito.InOrder
import jenkins.plugins.http_request.ResponseContentSupplier

import net.sf.json.JSONObject

import org.jenkinsci.plugins.webhookstep.WebhookToken
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*
import static org.mockito.Mockito.*

class ServiceNowTests extends BasePipelineTest {
    Script mockScript
    final static String chgTicket = "CHG1234567"

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none()

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        Map env = [
            GIT_URL: "https://github.braintreeps.com/braintree/jenkinsfile_shared_libraries.git",
            GIT_PREVIOUS_COMMIT: "0000",
            GIT_COMMIT: "1111",
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: "2222",
            RUN_DISPLAY_URL: "https://jenkins/job/braintree/job/repo/job/master/1/display/redirect"
        ]

        // Reduces the verbosity of tests
        // Comment out to see `echo` statements from ServiceNow.class
        helper.registerAllowedMethod("echo", [String])

        binding.setVariable('env', env)
        mockScript = loadInlineScript("")
    }

    @Test
    void testGenerateDiffLink_WhenValidSshOrHttpsRepoUrl_DiffLinkCorrectlyPopulatesWithOrgAndRepoName() {
        ServiceNow sn = new ServiceNow(mockScript)

        // Chosen based on https://github.com/moby/moby/issues/679#issuecomment-18307522
        List<String> testCases = [
            "cosmos/infrastructure",
            "personal/repository",
            "braintree/jenkins-production-worker",
            "braintree/jenkinsfile_shared_libraries",
            "braintree/something.with.dots"
        ]

        testCases.each {repo->
            String httpsUrl = "https://github.braintreeps.com/" + repo + ".git"
            String sshUrl = "git@github.braintreeps.com:" + repo + ".git"

            // Test HTTPS URLs
            binding.getVariable("env").GIT_URL = httpsUrl
            assertEquals("https://github.braintreeps.com/" + repo + "/compare/0000...1111", sn.generateDiffLink())

            // Test SSH URLs
            binding.getVariable("env").GIT_URL = sshUrl
            assertEquals("https://github.braintreeps.com/" + repo + "/compare/0000...1111", sn.generateDiffLink())
        }

    }

    @Test
    void testGenerateDiffLink_WhenPreviousCommitSameAsCurrentCommit_UseLastSuccessfulPreviousCommit() {
        ServiceNow sn = new ServiceNow(mockScript)

        binding.getVariable("env").GIT_PREVIOUS_COMMIT = binding.getVariable("env").GIT_COMMIT
        assertEquals("https://github.braintreeps.com/braintree/jenkinsfile_shared_libraries/compare/2222...1111", sn.generateDiffLink())
    }

    @Test
    void testGenerateDiffLink_WhenRollbackRevisionSet_UseRollbackRevision() {
        ServiceNow sn = new ServiceNow(mockScript)

        binding.getVariable("env").BT_ROLLBACK_REVISION = "abcd1234"
        assertEquals("https://github.braintreeps.com/braintree/jenkinsfile_shared_libraries/compare/abcd1234...1111", sn.generateDiffLink())
    }

    @Test
    void testGenerateDiffLink_WhenRollbackRevisionNotSet_UsePreviousCommit() {
        ServiceNow sn = new ServiceNow(mockScript)

        binding.getVariable("env").BT_ROLLBACK_REVISION = ""
        assertEquals("https://github.braintreeps.com/braintree/jenkinsfile_shared_libraries/compare/0000...1111", sn.generateDiffLink())
    }

    @Test
    void testEnvConfigIsSetProperlyOrThrowsException() {
        ServiceNow snGsnowQa = new ServiceNow(mockScript, "gsnow-qa")
        ServiceNow snGsnowProd = new ServiceNow(mockScript, "gsnow")
        ServiceNow snSnowDev = new ServiceNow(mockScript, "snow-dev")

        assertTrue(snGsnowQa.useGsnow)
        assertEquals(snGsnowQa.credentialsId, EnvConfig.GSNOW_QA_CREDENTIAL)
        assertEquals(snGsnowQa.endpoint, EnvConfig.GSNOW_QA_ENDPOINT)

        assertTrue(snGsnowProd.useGsnow)
        assertEquals(snGsnowProd.credentialsId, EnvConfig.GSNOW_PROD_CREDENTIAL)
        assertEquals(snGsnowProd.endpoint, EnvConfig.GSNOW_PROD_ENDPOINT)

        assertFalse(snSnowDev.useGsnow)
        assertEquals(snSnowDev.credentialsId, EnvConfig.SNOW_DEV_CREDENTIAL)
        assertEquals(snSnowDev.endpoint, EnvConfig.SNOW_DEV_ENDPOINT)

        exceptionRule.expect(ServiceNowException.class)
        exceptionRule.expectMessage("Expected one of ['snow-dev', 'gsnow-qa', 'gsnow'] but got badEnv")
        ServiceNow snBadEnv = new ServiceNow(mockScript, "badEnv")
    }

    @Test
    void testCmEnvIsSetProperlyBasedOnJenkinsUrl() {
        binding.setVariable("env", [JENKINS_URL: "https://ci.braintree.tools/"])
        ServiceNow sn = new ServiceNow(mockScript)

        assertEquals(sn.endpoint, EnvConfig.GSNOW_PROD_ENDPOINT)

        binding.setVariable("env", [JENKINS_URL: "https://jenkinsqa.braintree.tools/"])
        sn = new ServiceNow(mockScript)

        assertEquals(sn.endpoint, EnvConfig.GSNOW_QA_ENDPOINT)
    }

    @Test
    void testCreateChangeRequest_WhenPayloadContainsRequiredFields_CreateSuccessful() {
        String startDateTime = "2022-01-01 00:00:01"
        JSONObject mockResponse = new JSONObject()
        mockResponse.putAll([result: [record_id: chgTicket]])

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(startDateTime).when(sn).getStartTime()
        doReturn("jenkins").when(sn).determineUserFromBuildCause()
        doReturn(mockResponse).when(sn).makeRequest(anyString(), anyString(), anyMap())
        doReturn(null).when(sn).publishDeploymentInfoToTicket(eq(chgTicket), anyString())

        Map expectedPayload = [
            cmdb_ci: "appName",
            correlation_id: binding.getVariable("env").GIT_COMMIT,
            start_date: startDateTime,
            u_atb_cust_impact: "0",
            u_brand: "Braintree",
            u_category_subtype: "Deployment",
            u_category_type: "Automation",
            u_deployment_vehicle: "Braintree Jenkins",
            u_duration: "30",
            u_service_category: "Jenkins Automation",
            u_site_components: "appName",
            u_site_impact: "No impact",
            u_template: "BT Jenkins - Jenkins Normal Release Template",
            requested_by: "jenkins",
            assigned_to: "jenkins",
            stack_info: "BT Jenkins"
        ]

        String result = sn.createChangeRequest("appName")
        verify(sn, times(1)).makeRequest(eq("POST"), eq("/change_request"), eq(expectedPayload))
        assertEquals(chgTicket, result)
    }

    @Test
    void testGetOrCreateChangeRequest_WhenTicketAlreadyExists_GetsTicketInsteadOfCreating() {
        ServiceNow sn = spy(new ServiceNow(mockScript, "gsnow-qa"))

        JSONObject response = new JSONObject()
        response.element("result", [[number: chgTicket],[number: chgTicket + "8"]])

        doReturn(response).when(sn).queryForChangeRequest(anyString())

        String result = sn.getOrCreateChangeRequest("appName", "normal")
        assertEquals(chgTicket, result)
    }

    @Test
    void testGetOrCreateChangeRequest_WhenTicketDoesNotExist_CreatesTicketInsteadOfGetting() {
        ServiceNow sn = spy(new ServiceNow(mockScript, "gsnow-qa"))

        doThrow(ServiceNowException.class).when(sn).queryForChangeRequest(anyString())
        doReturn(chgTicket).when(sn).createChangeRequest(anyString(), anyString())

        String result = sn.getOrCreateChangeRequest("appName")
        assertEquals(chgTicket, result)
    }

    @Test
    void testQueryForChangeRequest() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString())

        sn.queryForChangeRequest("type=normal^u_brand.name=Braintree^correlation_id=111^u_site_components=services-end-to-end-testing")
        verify(sn, times(1)).makeRequest(eq("GET"), eq("/change_request?sysparm_query=type%3Dnormal%5Eu_brand.name%3DBraintree%5Ecorrelation_id%3D111%5Eu_site_components%3Dservices-end-to-end-testing"))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString())

        snWithGsnow.queryForChangeRequest("type=normal^u_brand.name=Braintree^correlation_id=111^u_site_components=services-end-to-end-testing")
        verify(snWithGsnow, times(1)).makeRequest(eq("GET"), eq("/change_request/query?sysparm_query=type%3Dnormal%5Eu_brand.name%3DBraintree%5Ecorrelation_id%3D111%5Eu_site_components%3Dservices-end-to-end-testing"))
    }

    @Test
    void testCycleThroughTemplates_WhenDeployerProceedsAfterPrompt_PipelineContinuesSuccessfully() {
        helper.registerAllowedMethod("input", [Map.class])

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doThrow(InvalidTemplateException.class).when(sn).createChangeRequest(anyString(), eq("BT-dummy-template"))
        doThrow(InvalidTemplateException.class).when(sn).createChangeRequest(anyString(), eq("BT-AppName-Moderate-Normal"))
        doReturn(chgTicket).when(sn).createChangeRequest(anyString(), eq("BT Jenkins - Jenkins Normal Release Template"))

        String result = sn.cycleThroughTemplates("appName", "BT-dummy-template")

        // Verify order of execution
        // Should try to create a CR from an explicitly passed template -> implicitly determined template -> BT Jenkins - Jenkins Normal Release Template
        InOrder inOrder = inOrder(sn)
        inOrder.verify(sn).createChangeRequest(anyString(), eq("BT-dummy-template"))
        inOrder.verify(sn).createChangeRequest(anyString(), eq("BT-AppName-Moderate-Normal"))
        inOrder.verify(sn).createChangeRequest(anyString(), eq("BT Jenkins - Jenkins Normal Release Template"))

        helper.verify("input", 1) // should only prompt for input once on explicit template
        assertEquals(chgTicket, result)
        assertJobStatusSuccess()
    }

    @Test(expected = FlowInterruptedException.class)
    void testCycleThroughTemplates_WhenDeployerAbortsAfterPrompt_PipelineAbortsSuccessfully() {
        helper.registerAllowedMethod("input", [Map.class]) {
            throw new FlowInterruptedException(
                Result.ABORTED,
                true,
                new CauseOfInterruption.UserInterruption("jenkins")
            )
        }

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doThrow(InvalidTemplateException.class).when(sn).createChangeRequest(anyString(), eq("BT-dummy-template"))

        sn.cycleThroughTemplates("appName", "BT-dummy-template")

        // Verify order of execution
        // Should try to create a CR from an explicitly passed template but not others
        InOrder inOrder = inOrder(sn)
        inOrder.verify(sn).createChangeRequest(anyString(), eq("BT-dummy-template"))
        inOrder.verify(sn, never()).createChangeRequest(anyString(), eq("BT-AppName-Moderate-Normal"))
        inOrder.verify(sn, never()).createChangeRequest(anyString(), eq("BT Jenkins - Jenkins Normal Release Template"))

        helper.verify("input", 1)
    }

    @Test
    void testCycleThroughTemplates_WhenUnableToApplyAnyTemplate_RaiseLastExceptionEncountered() {
        helper.registerAllowedMethod("input", [Map.class])

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doThrow(new InvalidTemplateException("Exception for explicit template")).when(sn).createChangeRequest(anyString(), eq("BT-dummy-template"))
        doThrow(new InvalidTemplateException("Exception for implicit Template")).when(sn).createChangeRequest(anyString(), eq("BT-AppName-Moderate-Normal"))
        doThrow(new InvalidTemplateException("Exception for jenkins Template")).when(sn).createChangeRequest(anyString(), eq("BT Jenkins - Jenkins Normal Release Template"))

        exceptionRule.expect(InvalidTemplateException.class)
        exceptionRule.expectMessage("Exception for jenkins Template")

        sn.cycleThroughTemplates("appName", "BT-dummy-template")
    }

    @Test
    void testCycleThroughTemplates_WhenMultipleTemplateAvailable_ReturnAfterFirstChangeRequestCreated() {
        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(chgTicket).when(sn).createChangeRequest(anyString(), eq("BT-dummy-template"))
        doReturn("CHG111111").when(sn).createChangeRequest(anyString(), eq("BT-AppName-Moderate-Normal"))
        doReturn("CHG222222").when(sn).createChangeRequest(anyString(), eq("BT Jenkins - Jenkins Normal Release Template"))

        String result = sn.cycleThroughTemplates("appName", "BT-dummy-template")

        verify(sn, times(1)).createChangeRequest(anyString(), eq("BT-dummy-template"))
        verify(sn, never()).createChangeRequest(anyString(), eq("BT-AppName-Moderate-Normal"))
        verify(sn, never()).createChangeRequest(anyString(), eq("BT Jenkins - Jenkins Normal Release Template"))
        assertEquals(chgTicket, result)
    }

    @Test
    void testCancelChangeRequest() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.cancelChangeRequest(chgTicket)
        verify(sn, times(1)).makeRequest(eq("PATCH"), eq("/change_request/number/${chgTicket}".toString()), eq([state: "canceled"]))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        snWithGsnow.cancelChangeRequest(chgTicket)
        verify(snWithGsnow, times(1)).makeRequest(eq("POST"), eq("/change_request/update"), eq([state: "canceled", ticket: chgTicket]))
    }

    @Test
    void testCheckinChangeRequest_WhenSnowOrGsnow_UseProperPathPayloadAndHttpMethod() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.checkinChangeRequest(chgTicket, "scheduled")
        verify(sn, times(1)).makeRequest(eq("PATCH"), eq("/change_request/number/${chgTicket}".toString()), eq([state: "scheduled"]))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        snWithGsnow.checkinChangeRequest(chgTicket, "implement")
        verify(snWithGsnow, times(1)).makeRequest(eq("POST"), eq("/change_request/checkin"), eq([state: "implement", ticket: chgTicket]))
    }

    @Test(expected=ServiceNowException.class)
    void testCheckinChangeRequest_OnlyAcceptsScheduledOrImplement() {
        ServiceNow sn = spy(new ServiceNow(mockScript))
        sn.checkinChangeRequest(chgTicket, "notScheduledOrImplement")
    }

    @Test
    void testMoveChangeRequestToReview() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.moveChangeRequestToReview(chgTicket)
        verify(sn, times(1)).makeRequest(eq("PATCH"), eq("/change_request/number/${chgTicket}".toString()), eq([state: "review"]))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        snWithGsnow.moveChangeRequestToReview(chgTicket)
        verify(snWithGsnow, times(1)).makeRequest(eq("POST"), eq("/change_request/update"), eq([state: "review", ticket: chgTicket]))
    }

    @Test
    void testSignOffChangeRequest() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.signOffChangeRequest(chgTicket, "successful", "deploy successful")
        verify(sn, times(1)).makeRequest(eq("PATCH"), eq("/change_request/number/${chgTicket}".toString()), eq([state: "closed", close_code: "successful", close_notes: "deploy successful"]))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        snWithGsnow.signOffChangeRequest(chgTicket, "successful", "deploy successful")
        verify(snWithGsnow, times(1)).makeRequest(eq("POST"), eq("/change_request/signoff"), eq([state: "closed", close_code: "successful", close_notes: "deploy successful", ticket: chgTicket]))
    }

    @Test
    void testGetChangeRequest() {
        Map mockTicketInfo = [
            number: chgTicket,
            type: "Normal",
            state: "New",
            assignment_group: "BT Jenkins"
        ]

        JSONObject mockResponse = new JSONObject()
        mockResponse.putAll([
            result: [mockTicketInfo]
        ])

        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(mockResponse).when(sn).makeRequest(anyString(), anyString(), anyMap())

        JSONObject snowResponse = sn.getChangeRequest(chgTicket)
        verify(sn, times(1)).makeRequest(eq("GET"), eq("/change_request/number/${chgTicket}".toString()))
        assertEquals(mockTicketInfo, snowResponse)

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(mockResponse).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        JSONObject gsnowResponse = snWithGsnow.getChangeRequest(chgTicket)
        verify(snWithGsnow, times(1)).makeRequest(eq("GET"), eq("/change_request?ticket=${chgTicket}".toString()))
        assertEquals(mockTicketInfo, gsnowResponse)
    }

    @Test
    void testPublishWorkNotes() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.publishWorkNotes(chgTicket, "deploy successful")
        verify(sn, times(1)).makeRequest(eq("PATCH"), eq("/change_request/number/${chgTicket}".toString()), eq([work_notes: "deploy successful"]))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        snWithGsnow.publishWorkNotes(chgTicket, "deploy successful")
        verify(snWithGsnow, times(1)).makeRequest(eq("POST"), eq("/change_request/update"), eq([work_notes: "deploy successful", ticket: chgTicket]))
    }

    @Test
    void testPublishDeploymentInfoToTicket_WhenUsingTagName_PublishWorkNotesUsingTag() {
        binding.getVariable("env").TAG_NAME = "v1.0.0"
        binding.getVariable("env").GIT_URL = "https://github.braintreeps.com/braintree/jenkinsfile_shared_libraries.git"

        String deployInfo = """Releasing tag v1.0.0 for appName
Tag URL: https://github.braintreeps.com/braintree/jenkinsfile_shared_libraries/releases/tag/v1.0.0

Build URL: https://jenkins/job/braintree/job/repo/job/master/1/display/redirect
Diff link: https://some-diff-link.braintree.com
Git Commit SHA1: 1111"""

        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn("https://some-diff-link.braintree.com").when(sn).generateDiffLink()
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.publishDeploymentInfoToTicket(chgTicket, "appName")

        verify(sn, times(1)).publishWorkNotes(eq(chgTicket), eq(deployInfo))
    }

    @Test
    void testPublishDeploymentInfoToTicket_WhenUsingCommitSha_PublishWorkNotesUsingTag() {
        String deployInfo = """Releasing commit 1111 for appName

Build URL: https://jenkins/job/braintree/job/repo/job/master/1/display/redirect
Diff link: https://some-diff-link.braintree.com
Git Commit SHA1: 1111"""

        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn("https://some-diff-link.braintree.com").when(sn).generateDiffLink()
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.publishDeploymentInfoToTicket(chgTicket, "appName")

        verify(sn, times(1)).publishWorkNotes(eq(chgTicket), eq(deployInfo))
    }

    @Test
    void testMoveChangeRequestToAssess() {
        // Test request against ServiceNow endpoints
        ServiceNow sn = spy(new ServiceNow(mockScript, "snow-dev"))
        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.moveChangeRequestToAssess(chgTicket)
        verify(sn, times(1)).makeRequest(eq("PATCH"), eq("/change_request/number/${chgTicket}".toString()), eq([state: "assess"]))

        // Test request against GSNOW endpoints
        ServiceNow snWithGsnow = spy(new ServiceNow(mockScript, "gsnow-qa"))
        doReturn(new JSONObject()).when(snWithGsnow).makeRequest(anyString(), anyString(), anyMap())

        snWithGsnow.moveChangeRequestToAssess(chgTicket)
        verify(snWithGsnow, times(1)).makeRequest(eq("POST"), eq("/change_request/update"), eq([ticket: chgTicket, state: "assess"]))
    }

    @Test
    void testRequestApproval_WhenRequestingApproval_MovesTicketToAssessState() {
        JSONObject response = new JSONObject()
        response.putAll([ticket: chgTicket, approval: "Approved", TbStop: "off"])

        helper.registerAllowedMethod('lock', [String, Closure], null)
        helper.registerAllowedMethod('registerWebhook', [Map]) { args-> return new WebhookToken(args.token, "http://jenkins_url/webhook-step/${args.token}", null) }
        helper.registerAllowedMethod('waitForWebhook', [WebhookToken])
        helper.registerAllowedMethod('readJSON', [Map]) { args->
            return response
        }

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())

        sn.requestApproval(chgTicket)
        verify(sn, times(1)).moveChangeRequestToAssess(eq(chgTicket))
    }

    @Test(expected=ServiceNowException.class)
    void testRequestApproval_WhenCancelled_ThrowsServiceNotException() {
        JSONObject response = new JSONObject()
        response.putAll([ticket: chgTicket, state: "Canceled", approval: "Rejected", TbStop: "off"])

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())
        doReturn(response).when(sn).waitForChangeApproval(anyString(), any(Closure.class))
        sn.requestApproval(chgTicket)
    }

    @Test()
    void testRequestApproval_WhenNotApprovedWithComments_ThrowsServiceNotException() {
        JSONObject response = new JSONObject()
        response.putAll([ticket: chgTicket, state: "New", approval: "Rejected", rejected_by: "John Smith", rejection_comments: "deploy freeze in place", TbStop: "off"])

        String rejectMsg = \
            "The change request for this deployment (${chgTicket}) was rejected by ${response.rejected_by}.\n" +
            "The reason given for this rejection was: ${response.rejection_comments}"

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())
        doReturn(response).when(sn).waitForChangeApproval(anyString(), any(Closure.class))

        try {
            sn.requestApproval(chgTicket)
            fail("Expected a ServiceNowException but no exception thrown")
        } catch (ServiceNowException e) {
            assertEquals(e.getClass(), ServiceNowException.class)
            assertEquals(e.getMessage(), rejectMsg)
        }

        verify(sn, times(1)).publishWorkNotes(eq(chgTicket), eq(rejectMsg))
        verify(sn, times(1)).cancelChangeRequest(eq(chgTicket))
    }

    @Test()
    void testRequestApproval_WhenNotApprovedWithoutComments_ThrowsServiceNotException() {
        JSONObject response = new JSONObject()
        response.putAll([ticket: chgTicket, state: "New", approval: "Rejected", rejected_by: "John Smith", rejection_comments: "", TbStop: "off"])

        String rejectMsg = "The change request for this deployment (${chgTicket}) was rejected by ${response.rejected_by}."

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())
        doReturn(response).when(sn).waitForChangeApproval(anyString(), any(Closure.class))

        try {
            sn.requestApproval(chgTicket)
            fail("Expected a ServiceNowException but no exception thrown")
        } catch (ServiceNowException e) {
            assertEquals(e.getClass(), ServiceNowException.class)
            assertEquals(e.getMessage(), rejectMsg)
        }

        verify(sn, times(1)).publishWorkNotes(eq(chgTicket), eq(rejectMsg))
        verify(sn, times(1)).cancelChangeRequest(eq(chgTicket))
    }

    @Test(expected=ServiceNowException.class)
    void testRequestApproval_WhenTbStopIsOn_ThrowsServiceNowException() {
        JSONObject response = new JSONObject()
        response.putAll([ticket: chgTicket, state: "Scheduled", approval: "Approved", TbStop: "on"])

        ServiceNow sn = spy(new ServiceNow(mockScript))

        doReturn(new JSONObject()).when(sn).makeRequest(anyString(), anyString(), anyMap())
        doReturn(response).when(sn).waitForChangeApproval(anyString(), any(Closure.class))
        sn.requestApproval(chgTicket)
    }

    @Test
    void testWaitForChangeApproval_WhenResponseTicketDoesNotMatch_RegenerateWebhookUrl() {
        JSONObject response = new JSONObject()
        response.putAll([ticket: "doesNotMatch", approval: "Approved", TbStop: "off"])

        helper.registerAllowedMethod('lock', [String, Closure], null)
        helper.registerAllowedMethod('registerWebhook', [Map]) { args-> return new WebhookToken(args.token, "http://jenkins_url/webhook-step/${args.token}", null) }
        helper.registerAllowedMethod('waitForWebhook', [WebhookToken])

        Integer readJSONInvocations = 0
        helper.registerAllowedMethod('readJSON', [Map]) { args->
            if (readJSONInvocations < 1) {
                readJSONInvocations += 1
                return response
            }

            response.ticket = chgTicket
            return response
        }

        ServiceNow sn = spy(new ServiceNow(mockScript))

        sn.waitForChangeApproval(chgTicket) {}
        helper.verify("registerWebhook", 2)
        helper.verify("waitForWebhook", 2)
        helper.verify("readJSON", 2)
    }

    @Test
    void testFormatStageResults() {
        ServiceNow sn = new ServiceNow(mockScript)
        List<Map> stages = [
            [name: "Build", status: "SUCCESS", durationMillis: "50000"],
            [name: "Unit Test", status: "SUCCESS", durationMillis: "10000"],
            [name: "Integration Test", status: "SUCCESS", durationMillis: "30000"],
            [name: "Push Images", status: "SUCCESS", durationMillis: "50000"],
            [name: "Deploy to Prod", status: "IN PROGRESS", durationMillis: "1000"],
        ]

        String result = sn.formatStageResults(stages)
        String expectedResult = """
            Build
              |-- Status: SUCCESS
              |-- Duration: 50000 ms
            Unit Test
              |-- Status: SUCCESS
              |-- Duration: 10000 ms
            Integration Test
              |-- Status: SUCCESS
              |-- Duration: 30000 ms
            Push Images
              |-- Status: SUCCESS
              |-- Duration: 50000 ms
            Deploy to Prod
              |-- Status: IN PROGRESS
              |-- Duration: 1000 ms
        """.stripIndent().trim()
        expectedResult += "\n"

        assertEquals(result, expectedResult)
    }

    @Test
    void testMakeRequest_WhenSendingRequest_EnsurePayloadIsFormattedCorrectly() {
        Map expectedHttpRequestParams = [
            url: "${EnvConfig.SNOW_DEV_ENDPOINT}/change_request".toString(),
            acceptType: "APPLICATION_JSON",
            httpMode: "POST",
            requestBody: "[json:[state:scheduled, u_modified_by:api_bo9pace], returnText:true]",
            quiet: true,
            validResponseCodes: "100:599", // Prevent httpRequest from failing the pipeline
            authentication: EnvConfig.SNOW_DEV_CREDENTIAL
        ]

        JSONObject response = new JSONObject()
        response.putAll([status: "success"])

        helper.registerAllowedMethod("writeJSON", [Map]) {args-> return args.toString()}
        helper.registerAllowedMethod("readJSON", [Map]) { return response }
        helper.registerAllowedMethod("httpRequest", [Map]) {args->
            assertEquals(expectedHttpRequestParams, args)

            return new ResponseContentSupplier("", 200)
        }

        ServiceNow sn = new ServiceNow(mockScript, "snow-dev")
        JSONObject result = sn.makeRequest(expectedHttpRequestParams["httpMode"], "/change_request", [state: "scheduled"])

        assertEquals(response.status, result.status)
    }

    @Test
    void testMakeRequest_WhenDebugFlagIsSet_QuietEqualsFalse() {
        Map expectedHttpRequestParams = [
            url: "${EnvConfig.SNOW_DEV_ENDPOINT}/change_request".toString(),
            acceptType: "APPLICATION_JSON",
            httpMode: "POST",
            requestBody: "[json:[state:scheduled, u_modified_by:api_bo9pace], returnText:true]",
            quiet: false,
            validResponseCodes: "100:599", // Prevent httpRequest from failing the pipeline
            authentication: EnvConfig.SNOW_DEV_CREDENTIAL
        ]

        JSONObject response = new JSONObject()
        response.putAll([status: "success"])

        helper.registerAllowedMethod("writeJSON", [Map]) {args-> return args.toString()}
        helper.registerAllowedMethod("readJSON", [Map]) { return response }
        helper.registerAllowedMethod("httpRequest", [Map]) {args->
            assertEquals(expectedHttpRequestParams, args)

            return new ResponseContentSupplier("", 200)
        }

        // Set debug flag as environment variable passed to the test
        binding.setVariable("env", binding.getVariable("env") + [DEBUG_SNOW_REQUESTS: "true"])

        ServiceNow sn = new ServiceNow(mockScript, "snow-dev")
        JSONObject result = sn.makeRequest(expectedHttpRequestParams["httpMode"], "/change_request", [state: "scheduled"])

        assertEquals(response.status, result.status)
        helper.verify("echo", 1)
    }

    @Test
    void testMakeRequest_WhenUsingGsnow_EnsureAuthenticationHeaderIsSet() {
        String token = "injectedToken"
        Map expectedHttpRequestParams = [
            url: "${EnvConfig.GSNOW_QA_ENDPOINT}/change_request".toString(),
            acceptType: "APPLICATION_JSON",
            httpMode: "POST",
            requestBody: "[json:[state:scheduled, u_modified_by:api_bo9pace], returnText:true]",
            quiet: true,
            validResponseCodes: "100:599", // Prevent httpRequest from failing the pipeline
            customHeaders: [[maskValue: true, name: "Authorization", value: "Token ${token}"]]
        ]

        JSONObject response = new JSONObject()
        response.putAll([status: "success"])

        helper.registerAllowedMethod("writeJSON", [Map]) {args-> return args.toString()}
        helper.registerAllowedMethod("readJSON", [Map]) { return response }
        helper.registerAllowedMethod("httpRequest", [Map]) {args->
            assertEquals(expectedHttpRequestParams, args)

            return new ResponseContentSupplier("", 200)
        }
        helper.registerAllowedMethod("withCredentials", [List, Closure]) { List l, Closure c->
            c.call()
        }

        // TODO: Find a way to set this as part of the withCredentials block
        binding.setVariable("env", binding.getVariable("env") + [GSNOW_TOKEN: token])

        ServiceNow sn = new ServiceNow(mockScript, "gsnow-qa")
        JSONObject result = sn.makeRequest(expectedHttpRequestParams["httpMode"], "/change_request", [state: "scheduled"])

        assertEquals(response.status, result.status)
    }

    @Test(expected=ServiceNowException.class)
    void testMakeRequest_WhenSnowReturnsFailedStatus_ThrowServiceNowException() {
        Map expectedHttpRequestParams = [
            url: "${EnvConfig.SNOW_DEV_ENDPOINT}/change_request".toString(),
            acceptType: "APPLICATION_JSON",
            httpMode: "POST",
            requestBody: "[json:[state:scheduled, u_modified_by:api_bo9pace], returnText:true]",
            quiet: true,
            validResponseCodes: "100:599", // Prevent httpRequest from failing the pipeline
            authentication: EnvConfig.SNOW_DEV_CREDENTIAL
        ]

        JSONObject response = new JSONObject()
        response.putAll([status: "failure"])
        response.element("error", [message: "Problem in creating record", detail: "Some issue occurred"])

        helper.registerAllowedMethod("writeJSON", [Map]) {args-> return args.toString()}
        helper.registerAllowedMethod("readJSON", [Map]) { return response }
        helper.registerAllowedMethod("httpRequest", [Map]) {args->
            assertEquals(expectedHttpRequestParams, args)

            return new ResponseContentSupplier("", 200)
        }

        ServiceNow sn = new ServiceNow(mockScript, "snow-dev")
        sn.makeRequest(expectedHttpRequestParams["httpMode"], "/change_request", [state: "scheduled"])
    }

    @Test
    void testMakeRequest_WhenCannotCreateChangeRequestFromTemplate_ThrowInvalidTemplateException() {
        JSONObject mockResponse = new JSONObject()
        mockResponse.putAll([
            status: "failure",
            error: [
                message: "Problem in creating record",
                detail: "Action Plan, Verification Plan / Success Criteria, Back Out Plan field(s) required to create change ticket",
            ]
        ])

        helper.registerAllowedMethod("writeJSON", [Map])
        helper.registerAllowedMethod("httpRequest", [Map]) { return new ResponseContentSupplier("", 200) }
        helper.registerAllowedMethod("readJSON", [Map]) { return mockResponse }

        ServiceNow sn = new ServiceNow(mockScript)

        exceptionRule.expect(InvalidTemplateException)
        exceptionRule.expectMessage(ServiceNowErrors.INVALID_TEMPLATE.toString() + " for template: RandomTemplate")

        sn.makeRequest("POST", "/change_request", ["u_template": "RandomTemplate"])
    }

    @Test
    void testMakeRequest_WhenCannotUseDesiredCI_ThrowInvalidCIException() {
        JSONObject mockResponse = new JSONObject()
        mockResponse.putAll([
            status: "failure",
            error: [
                message: "invalid_ci is not a valid value for reference field cmdb_ci",
                detail: "Error in creating record",
            ]
        ])

        helper.registerAllowedMethod("writeJSON", [Map])
        helper.registerAllowedMethod("httpRequest", [Map]) { return new ResponseContentSupplier("", 200) }
        helper.registerAllowedMethod("readJSON", [Map]) { return mockResponse }

        ServiceNow sn = new ServiceNow(mockScript)

        exceptionRule.expect(InvalidConfigurationItemException)
        exceptionRule.expectMessage("The provided CI is not available in ServiceNow.")

        sn.makeRequest("POST", "/change_request", ["u_template": "RandomTemplate"])
    }

    @Test
    void testIsTicketInState_UsingGnowApi_UseTicketAsQueryParam() {
        JSONObject response = new JSONObject()
        response.putAll([result: [[state: "Closed"]]])

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))
        doReturn(response).when(sn).makeRequest(anyString(), anyString(), anyMap())

        Boolean result = sn.isTicketInState(chgTicket, "closed")

        verify(sn, times(1)).makeRequest(eq("GET"), eq("/change_request?ticket=${chgTicket}".toString()))
        assertTrue(result)
    }

    @Test
    void testIsTicketInState_UsingSnowApi_UseTicketAsPathParam() {
        JSONObject response = new JSONObject()
        response.putAll([result: [[state: "Review"]]])

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.SNOW_DEV))
        doReturn(response).when(sn).makeRequest(anyString(), anyString(), anyMap())

        Boolean result = sn.isTicketInState(chgTicket, "closed")

        verify(sn, times(1)).makeRequest(eq("GET"), eq("/change_request/number/${chgTicket}".toString()))
        assertFalse(result)
    }

    @Test(expected=FileNotFoundException.class)
    void testParseChecklist_WhenFileDoesNotExist_ThrowsFileNotFoundException() {
        helper.registerAllowedMethod("fileExists", [String]) { return false }

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))

        sn.parseChecklist("fakefile.yaml")
    }

    @Test
    void testParseChecklist_WhenFileExists_ParseFileSuccessful() {
        helper.registerAllowedMethod("fileExists", [String]) { return true }
        helper.registerAllowedMethod("readYaml", [Map]) { return [foo: "bar"] }

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))
        HashMap response = sn.parseChecklist("checklist.yaml")

        assertEquals([foo: "bar"], response)
    }

    @Test
    void testPromptToCompleteChecklist_WhenAllChecklistItemsCompleted_NoRepromptAndCallsPublishWorkNotesSuccessfully() {
        helper.registerAllowedMethod("input", [Map]) { return [:] }

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))

        doReturn([:]).when(sn).parseChecklist(anyString())
        doReturn([]).when(sn).getAppChecklistByStage(anyMap(), anyString(), anyString())
        doReturn([:]).when(sn).parseChecklistSubmission(anyMap())
        doReturn("Worknote").when(sn).formatChecklistSubmission(anyMap(), anyString())
        doReturn(null).when(sn).publishWorkNotes(anyString(), anyString())

        sn.promptToCompleteChecklist(chgTicket, "/checklist.yaml", "appName", "Review")

        verify(sn, times(1)).publishWorkNotes(eq(chgTicket), eq("Worknote"))
    }

    @Test
    void testPromptToCompleteChecklist_WhenIncompleteChecklistItemsExist_RepromptsForInputAndPublishesChecklistSuccessfully() {
        helper.registerAllowedMethod("input", [Map]) { return [:] }
        helper.registerAllowedMethod('text', [Map])

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))

        doReturn([:]).when(sn).parseChecklist(anyString())
        doReturn([]).when(sn).getAppChecklistByStage(anyMap(), anyString(), anyString())
        doReturn([completedItems: ["item2"], incompleteItems: ["item1"]]).when(sn).parseChecklistSubmission(anyMap())
        doReturn("Worknote").when(sn).formatChecklistSubmission(anyMap(), anyString())
        doReturn(null).when(sn).publishWorkNotes(anyString(), anyString())

        sn.promptToCompleteChecklist(chgTicket, "/checklist.yaml", "appName", "Review")

        verify(sn, times(1)).publishWorkNotes(eq(chgTicket), eq("Worknote"))
        verify(sn, times(2)).parseChecklistSubmission(anyMap())
        helper.verify('input', 2)
    }

    @Test
    void testPromptToCompleteChecklist_WWhenReleaseChecklistDoesNotExist_ReturnEarly() {
        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))

        doThrow(FileNotFoundException.class).when(sn).parseChecklist(anyString())

        sn.promptToCompleteChecklist(chgTicket, "/checklist.yaml", "appName", "Review")

        helper.verify('echo', 1)
        helper.verify('input', 0)
    }

    @Test
    void testPromptToCompleteChecklist_WWhenReleaseChecklistStageDoesNotExist_ReturnEarly() {
        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))

        doReturn([:]).when(sn).parseChecklist(anyString())
        doThrow(ServiceNowException.class).when(sn).getAppChecklistByStage(anyMap(), anyString(), anyString())

        sn.promptToCompleteChecklist(chgTicket, "/checklist.yaml", "appName", "Review")

        helper.verify('echo', 1)
        helper.verify('input', 0)

    }

    @Test
    void testPromptToCompleteChecklist_WhenNullTicket_DoNotPublishWorkNote() {
        helper.registerAllowedMethod("input", [Map]) { return [:] }
        helper.registerAllowedMethod('text', [Map])

        ServiceNow sn = spy(new ServiceNow(mockScript, EnvConfig.GSNOW_QA))

        doReturn([:]).when(sn).parseChecklist(anyString())
        doReturn([]).when(sn).getAppChecklistByStage(anyMap(), anyString(), anyString())
        doReturn([completedItems: ["item2"], incompleteItems: ["item1"]]).when(sn).parseChecklistSubmission(anyMap())

        sn.promptToCompleteChecklist(null, "/checklist.yaml", "appName", "Review")

        verify(sn, never()).formatChecklistSubmission(anyMap(), anyString())
        verify(sn, never()).publishWorkNotes(anyString(), anyString())
    }

    @Test
    void testParseChecklistSubmission_WhenNoReason_SubmissionParsedCorrectly() {
        Map testSubmission = [
            "CHECKLIST_SUBMITTER": "jenkins",
            "CompleteItem1": true,
            "CompleteItem2": true,
            "CompleteItem3": true,
            "IncompleteItem1": false
        ]

        ServiceNow sn = new ServiceNow(mockScript)
        Map result = sn.parseChecklistSubmission(testSubmission)

        assertEquals("jenkins", result.submitter)
        assertEquals(["CompleteItem1", "CompleteItem2", "CompleteItem3"], result.completedItems)
        assertEquals(["IncompleteItem1"], result.incompleteItems)
        assertNull(result.reason)
    }

    @Test
    void testParseChecklistSubmission_WhenReasonIsPresent_SubmissionParsedCorrectly() {
        Map testSubmission = [
            CHECKLIST_SUBMITTER: "jenkins",
            reason: "test reason"
        ]

        ServiceNow sn = new ServiceNow(mockScript)
        Map result = sn.parseChecklistSubmission(testSubmission)

        assertEquals("jenkins", result.submitter)
        assertTrue(result.completedItems.isEmpty())
        assertTrue(result.incompleteItems.isEmpty())
        assertEquals("test reason", result.reason)
    }

    @Test
    void testFormatChecklistSubmission_WhenNoCompletedItems_ReturnListOfIncompleteItems() {
        Map testSubmission = [
            submitter: "jenkins",
            completedItems: [],
            incompleteItems: [
                "Item1",
                "Item2"
            ],
            reason: "test reason"
        ]

        String expectedResult = """
            No pre-deployment checklist items were completed by jenkins

            The following pre-deployment checklist items are incomplete:
            |-- Item1
            |-- Item2

            The following reason was given for the incomplete items:
            |-- test reason
        """.stripIndent().trim()

        ServiceNow sn = new ServiceNow(mockScript)
        String result = sn.formatChecklistSubmission(testSubmission, "pre-deployment")

        assertEquals(expectedResult, result)
    }

    @Test
    void testFormatChecklistSubmission_WhenNoCompletedItemsAndNoReason_ReturnListOfIncompleteItems() {
        Map testSubmission = [
            submitter: "jenkins",
            completedItems: [],
            incompleteItems: [
                "Item1",
                "Item2"
            ]
        ]

        String expectedResult = """
            No pre-deployment checklist items were completed by jenkins

            The following pre-deployment checklist items are incomplete:
            |-- Item1
            |-- Item2

            No reason given for the incomplete items
        """.stripIndent().trim()

        ServiceNow sn = new ServiceNow(mockScript)
        String result = sn.formatChecklistSubmission(testSubmission, "pre-deployment")

        assertEquals(expectedResult, result)
    }

    @Test
    void testFormatChecklistSubmission_WhenNoIncompleteItems_ReturnListOfCompletedItems() {
        Map testSubmission = [
            submitter: "jenkins",
            incompleteItems: [],
            completedItems: [
                "Item1",
                "Item2"
            ]
        ]

        String expectedResult = """
            jenkins completed the following pre-deployment items:
            |-- Item1
            |-- Item2

            There are no incomplete items. All items were completed.
        """.stripIndent().trim()

        ServiceNow sn = new ServiceNow(mockScript)
        String result = sn.formatChecklistSubmission(testSubmission, "pre-deployment")

        assertEquals(expectedResult, result)
    }

    @Test
    void testGetAppChecklistByStage_WhenAppChecklistNotDefined_ThrowServiceNowException() {
        ServiceNow sn = new ServiceNow(mockScript)

        exceptionRule.expect(ServiceNowException)
        exceptionRule.expectMessage("No checklists found for app name: appName")

        sn.getAppChecklistByStage([:], "appName", "pre-deployment")
    }

    @Test
    void testGetAppChecklistByStage_WhenStageChecklistNotDefined_ThrowServiceNowException() {
        ServiceNow sn = new ServiceNow(mockScript)

        exceptionRule.expect(ServiceNowException)
        exceptionRule.expectMessage("No checklist found for stage: pre-deployment")

        sn.getAppChecklistByStage(
            [appName: ["post-deployment": ["item1", "item2"]]],
            "appName",
            "pre-deployment"
        )
    }

    @Test
    void testGetAppChecklistByStage_WhenChecklistExists_ReturnItems() {
        Map releaseChecklists = [
            appName: [
                "pre-deployment": [
                    "item1",
                    "item2"
                ]
            ]
        ]

        List<String> expected = ["item1", "item2"]

        ServiceNow sn = new ServiceNow(mockScript)
        List<String> result = sn.getAppChecklistByStage(releaseChecklists, "appName", "pre-deployment")

        assertEquals(expected, result)
    }
}
