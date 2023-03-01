package org.braintree.sonarqube

class SonarQube implements Serializable {
    private def steps
    private def env

    public SonarQube(Script script) {
        this.steps = script
        this.env = script.env
    }

    public startScan() {
        env.scannerHome = steps.tool 'SonarQube Scanner 4.7'
        steps.withSonarQubeEnv('PayPal-SonarQube') {
            steps.sh "${env.scannerHome}/bin/sonar-scanner"
        }
    }

    public validateProjectPropertiesFile(String filePath) throws FileNotFoundException {
        Map properties
        if (steps.fileExists(filePath)) {
            properties = steps.readProperties(file: filePath)
            if(properties['sonar.projectKey'] == null) {
                throw new Exception("Required property 'sonar.projectKey' not set in sonar-project.properties file.")
            }
        } else {
            throw new FileNotFoundException("Could not find ${filePath} file.")
        }
    }
}
