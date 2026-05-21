pipeline {
    agent any

    options {
        gitLabConnection('gitlab')
    }

    stages {
        stage('CI - Build') {
            steps {
                gitlabCommitStatus(name: 'CI - Build') {
                    sh '''
                    docker compose -f docker-compose.yml -f docker-compose.dev.yml build
                    '''
                }
            }
        }

        stage('CD - Deploy') {
            when {
                branch 'master'  
            }
            steps {
                gitlabCommitStatus(name: 'CD - Deploy') {
                    sshagent(credentials: ['ec2-ssh-key']) {
                        sh '''
                        ssh -o StrictHostKeyChecking=no ubuntu@k14d109.p.ssafy.io "
                        cd /home/ubuntu/S14P31D109 &&
                        git pull origin master &&
                        docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
                        "
                        '''
                    }
                }
            }
        }
    }
}