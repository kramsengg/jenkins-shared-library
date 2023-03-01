import com.lesfurets.jenkins.unit.BasePipelineTest

import org.braintree.sonarqube.SonarQube

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.junit.Assert.*
import static org.mockito.Mockito.*

class ExecuteSonarQubeScanTests extends BasePipelineTest {
    def executeSonarQubeScan
    Map env

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        binding.setVariable('env', env)
        // Reduce verbosity
        helper.registerAllowedMethod('echo', [String.class])

        executeSonarQubeScan = loadScript("vars/executeSonarQubeScan.groovy")
    }

    @Test
    void testExecuteSonarQubeScan_whenValidProjectPropertiesFile_scanExecutes() {
        SonarQube sq = mock(SonarQube.class)
        doReturn(null).when(sq).validateProjectPropertiesFile(anyString())
        executeSonarQubeScan(SonarQube: sq)
        verify(sq, times(1)).startScan()
    }
}