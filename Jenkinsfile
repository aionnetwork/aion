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
	
        P2P_PORT = sh(returnStdout: true, script: 'shuf -i 30304-65000 -n 1').trim()

        GIT_TAG = sh(returnStdout: true, script:
        '''\
        #!/bin/bash -e
        if git rev-parse --verify -q refs/tags/${GIT_BRANCH}^{} | grep -q ${GIT_COMMIT}
        then echo tag
            exit 0
        else echo unknown
            exit 0
        fi
        '''.stripIndent()).trim()
    }

    triggers { cron('0 3 * * 6') }

    stages {
        stage('Build') {
            steps {
                echo "Building branch: ${env.BRANCH_NAME}"
                echo "git branch: ${GIT_BRANCH}"
		// comment it out due to temporary ignore the consensus tests.
                // sh "git lfs install"
                sh "./gradlew pack" 
            }
        }
        
        stage('Archive build output') {
            when {
                expression { 
                    GIT_BRANCH == 'master' || GIT_TAG == 'tag'
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
                expression {GIT_BRANCH == 'master' || GIT_TAG == 'tag'}
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
                    expression {GIT_BRANCH == 'master' || GIT_TAG == 'tag'}
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
                expression { env.CHANGE_ID || GIT_BRANCH == 'master' || GIT_TAG == 'tag'}
            }
            steps {
                timeout(20) {
                    dir('FunctionalTests') {
                        checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'https://github.com/aionnetwork/node_test_harness.git']], branches: [[name: '764b12d']]], poll: false
                    }

                    sh('cp pack/oan.tar.bz2 FunctionalTests/Tests')

                    dir('FunctionalTests') { 
                        sh('tar -C Tests -xjf Tests/oan.tar.bz2')
                        sh('./gradlew :Tests:test -i -PtestNodes=java')
                    }
                }
            }
        }

        stage('Testnet Sync test') {
	    when {
		triggeredBy 'TimerTrigger'
                expression{GIT_BRANCH == 'master'|| GIT_TAG == 'tag'}
	    }

	    steps {
                timeout(time: 4, unit: 'HOURS') {
                    dir('pack') {
                        sh('tar xvf oan.tar.bz2')
                        echo "Start amity sync test..."
                        sh('./oan/aion.sh -n amity e port=${P2P_PORT} log GEN=ERROR SYNC=ERROR CONS=ERROR DB=ERROR API=ERROR dev fs')
                        echo "finished amity sync test..."
                        sh('rm -rf ./oan/amity/*')
		    }
                }
            }
	}

        stage('Mainnet Sync test') {
            when {
                triggeredBy 'TimerTrigger'
                expression{GIT_BRANCH == 'master'|| GIT_TAG == 'tag'}
            }

            steps {
                timeout(time: 12, unit: 'HOURS') {
                    dir('pack') {
                        echo "Start mainnet sync test..."
                        sh('./oan/aion.sh e port=${P2P_PORT} log GEN=ERROR SYNC=ERROR CONS=ERROR DB=ERROR API=ERROR dev fs')
                        echo "finished mainnet sync test..."
                        sh('rm -rf ./oan')
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
