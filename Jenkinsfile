pipeline {
    agent { label 'ci' }

    tools {
        maven 'Maven_3.9.6' 
        jdk 'jdk17'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }

        stage('Deploy Snapshot') {
            when {
                branch 'main'
            }
            steps {
                // This assumes you have configured a 'Managed File' in Jenkins 
                // with the ID 'sonatype-settings' containing your Sonatype credentials.
                configFileProvider([configFile(fileId: 'sonatype-settings', variable: 'MAVEN_SETTINGS')]) {
                    sh 'mvn deploy -s $MAVEN_SETTINGS -DskipTests'
                }
            }
        }
    }

    post {
        failure {
            echo 'Build failed. Check the logs and Sonatype credentials.'
        }
        success {
            echo 'Snapshot successfully deployed to Sonatype S01.'
        }
    }
}
