pipeline {
    agent any

    tools {
        // EXACT name you gave JDK 25 in "Global Tool Configuration"
        jdk 'JDK 25' 
        // EXACT name you gave Maven in "Global Tool Configuration"
        maven 'Maven 3.9.8' 
    }

    environment {
        // GPG Passphrase logic is still needed for signing
        MAVEN_GPG_PASSPHRASE = credentials('gpg-passphrase')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Verify') {
            steps {
                // Compile and run tests with JDK 25
                sh 'mvn clean verify'
            }
        }

        stage('Deploy to Central') {
            when {
                // Only run this stage if it is NOT a snapshot
                expression {
                    def pom = readMavenPom file: 'pom.xml'
                    return !pom.version.endsWith("-SNAPSHOT")
                }
            }
            steps {
                script {
                    echo "Deploying Release to Central Portal..."
                    // The new plugin reads auth from settings.xml (server: central)
                    // We pass GPG passphrase for the maven-gpg-plugin
                    sh 'mvn deploy -P release -DskipTests'
                }
            }
        }
    }
}