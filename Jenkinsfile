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
	    when {
                expression { GIT_BRANCH == 'master' }
            }
            steps {

                sh "git submodule foreach git pull origin master"

                sh "${env.ANT_HOME} pack_build"
                
                timeout(60) {
                	sh "${env.ANT_HOME}"
                }
            }
            
        }
        
        stage('Archive build output') {
            when {
                expression { GIT_BRANCH == 'master' }
            }
            steps {                
                archiveArtifacts artifacts: 'aion-v*.tar.bz2'
            }
        }
        
    	stage('Test') {
		steps {
    			timeout(60){
    				sh "${env.ANT_HOME} test_all"
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
	success {
        	cleanWs()
	} 
    }
    
}

environment {
    JAVA_HOME = "${env.JDK_9_HOME}"

    ANT_HOME = "${env.ANT_HOME}"
    
    SYSTEM_TESTS_HOME = "test"

    GIT_BRANCH = "${env.BRANCH_NAME}"

}
