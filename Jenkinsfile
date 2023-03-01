pipeline {
    agent { label "ec2" }

    stages {
        stage("Unit Tests") {
            steps {
                sh(
                    script: "./gradlew test",
                    label: "Execute JUnit tests..."
                )

                junit(testResults: "**/build/test-results/test/TEST-*.xml")
                jacoco()
            }
        }
    }
}
