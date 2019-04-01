//configuration
properties([[$class: 'jenkins.model.BuildDiscarderProperty', strategy:
      [$class: 'LogRotator', numToKeepStr: '100', artifactNumToKeepStr: '20']
      ]])
      
pipeline {
  agent any

  stages {
    stage('Build') {
      steps {
        echo "Building branch: ${env.BRANCH_NAME}"
        sh "./gradlew pack"
      }
    }
    
    stage('Archive build output') {
      when {
        expression { 
          GIT_BRANCH == 'master' ||
            GIT_BRANCH == 'dev' ||
            GIT_BRANCH == 'ci' ||
            GIT_BRANCH == 'dev-audit' 
        }
      }

      steps {        
        archiveArtifacts artifacts: 'pack/aion-v*.tar.bz2'
      }
    }
    
    stage('Unit test') {
      steps {
        timeout(60) {
          sh "./gradlew ciBuild"
        }
      }
      post {
          always {
              junit "report/**/*.xml"
          }
        }
    }

    stage('Checkout functional tests') { 
      dir('FunctionalTests') {
        git url: 'https://github.com/aionnetwork/node_test_harness.git'
      }

    }

    stage('Functional tests') { 
      steps { 
          echo 'Coming soon'
      }
    }
  }

  post {
    always {
      cleanWs()
  }

  success {
    slackSend channel: '#ci',
      color: 'good',
      message: "The pipeline ${currentBuild.fullDisplayName} completed successfully. Grab the generated builds at ${env.BUILD_URL}"
  } 

    failure {
      slackSend channel: '#ci',
          color: 'danger', 
          message: "The pipeline ${currentBuild.fullDisplayName} failed at ${env.BUILD_URL}"
    }

  }
}

environment {
  JAVA_HOME = "${env.JDK_9_HOME}"
  ANT_HOME = "${env.ANT_HOME}"
  SYSTEM_TESTS_HOME = "test"
  GIT_BRANCH = "${env.BRANCH_NAME}"
  DOCKER_HOST = "unix:///var/run/docker.sock"
}
