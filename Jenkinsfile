properties([[$class: 'jenkins.model.BuildDiscarderProperty', strategy:
            [$class: 'LogRotator', numToKeepStr: '100', artifactNumToKeepStr: '20']
            ]])
            
pipeline {
    agent any

    environment {
        LD_LIBRARY_PATH = '/usr/lib/jvm/java-11-openjdk-amd64/lib/server:/usr/local/lib'
        JAVA_HOME = '/usr/lib/jvm/java-11-openjdk-amd64'
        PATH = '/home/aion/.cargo/bin:/home/aion/bin:/home/aion/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/snap/bin:/usr/lib/jvm/java-11-openjdk-amd64/bin'
        LIBRARY_PATH = '/usr/lib/jvm/java-11-openjdk-amd64/lib/server'
    }

    stages {
        stage('Build') {
            steps {
                echo "Building branch: ${env.BRANCH_NAME}"
		// comment it out due to temporary ignore the consensus tests.
                // sh "git lfs install"
                sh "./gradlew pack" 
            }
        }
        
        stage('Archive build output') {
            when {
                expression { 
                    GIT_BRANCH == 'master' || buildingTag() 
                }
            }

            steps {                
                archiveArtifacts artifacts: 'pack/oan-v*.tar.bz2'
            }
        }
       
        stage('Full test') {
            when {
                // only run if:
                // - this branch is master
                expression {GIT_BRANCH == 'master' || buildingTag()}
            }

            steps {
                timeout(60) {
                    sh "./gradlew ciBuild"
                }
            }
        }

        stage('Unit test') {
            when {
                // only run if:
                // - this branch is in a PR (env.CHANGE_ID not null), or
                // - this branch is not master
                not {
                    expression {GIT_BRANCH == 'master'}
                }
            }

            steps {
                timeout(60) {
                    sh "./gradlew unitTest"
                }
            }
        }

        stage('Functional tests') {
            when { 
                // only run if:
                // - this branch is in a PR (env.CHANGE_ID not null), or
                // - this branch is master
                expression { env.CHANGE_ID || GIT_BRANCH == 'master' || buildingTag()}
            }
            steps {
                timeout(20) {
                    dir('FunctionalTests') {
                        checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'https://github.com/aionnetwork/node_test_harness.git']], branches: [[name: 'a3248ba']]], poll: false
                    }

                    sh('cp pack/oan.tar.bz2 FunctionalTests/Tests')

                    dir('FunctionalTests') { 
                        sh('tar -C Tests -xjf Tests/oan.tar.bz2')
                        sh('./gradlew :Tests:test -i -PtestNodes=java')
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts:'FunctionalTests/Tests/oan/custom/log/**', allowEmptyArchive: true
            sh('([ -d FunctionalTests ] && cp -r FunctionalTests/report/FunctionalTests report/) || true')
            junit "report/**/*.xml"
            sh 'bash script/jenkins-dump-heapfiles.sh'
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
