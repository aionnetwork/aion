//configuration
properties([[$class: 'jenkins.model.BuildDiscarderProperty', strategy:
			[$class: 'LogRotator', numToKeepStr: '100', artifactNumToKeepStr: '20']
			]])
			

node {
    echo "current branch: ${env.BRANCH_NAME}"
}

pipeline {
    agent any
    stages {
        stage('Setup') {
            steps {
                
                sh "git submodule init" 

                sh "git submodule update --recursive --remote --merge"

                sh "${env.ANT_HOME} pack_build"
                
                timeout(60) {
                	sh "${env.ANT_HOME}"
                }
            }
            
        }
        
        stage('Archive build output') {
            when {
                expression { GIT_BRANCH == 'master' || GIT_BRANCH == 'dev' || GIT_BRANCH == 'ci' }
            }
            steps {                
                archiveArtifacts artifacts: 'pack/aion-v*.tar.bz2'
            }
        }
        
    	stage('Test') {
		steps {
    			timeout(60){
    				sh "${env.ANT_HOME} ci_build"
    			}
    		}
    		post {
                	always {
                    		junit "report/*"
                	}
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

}
