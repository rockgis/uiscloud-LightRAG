pipeline {
    agent any

    parameters {
        string(
            name: 'DEPLOY_HOST',
            defaultValue: '192.168.0.151',
            description: '배포 서버 IP / 호스트명'
        )
        string(
            name: 'DEPLOY_DIR',
            defaultValue: '/svc/app/lightrag',
            description: '배포 서버의 대상 디렉토리'
        )
    }

    environment {
        IMAGE_NAME = 'knowwheresoft/uiscloud-lightrag'
        APP_PORT   = '9621'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.APP_VERSION = sh(
                        script: "grep '__version__' lightrag/_version.py | cut -d'\"' -f2",
                        returnStdout: true
                    ).trim()
                    env.IMAGE_TAG = "${env.APP_VERSION}-${env.GIT_SHORT_SHA}"
                    echo "Build tag: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Setup Docker') {
            steps {
                sh '''
                    docker version
                    docker buildx create --name lightrag-builder --use --bootstrap 2>/dev/null || \
                        docker buildx use lightrag-builder 2>/dev/null || true
                '''
            }
        }

        stage('Build Image') {
            steps {
                sh '''
                    docker build \
                        --cache-from ${IMAGE_NAME}:latest \
                        --build-arg BUILDKIT_INLINE_CACHE=1 \
                        -f Dockerfile \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        -t ${IMAGE_NAME}:latest \
                        .
                '''
            }
        }

        stage('Push to Registry') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'deploy-server-ssh',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    ),
                    file(
                        credentialsId: 'env-file-lightrag',
                        variable: 'ENV_FILE'
                    )
                ]) {
                    sh """
                        # 배포 서버에 디렉토리 생성
                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            \${SSH_USER}@${params.DEPLOY_HOST} \
                            "mkdir -p ${params.DEPLOY_DIR}/data/{rag_storage,inputs,prompts}"

                        # docker-compose.yml 및 .env 파일 전송
                        scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            docker-compose.yml \
                            \${SSH_USER}@${params.DEPLOY_HOST}:${params.DEPLOY_DIR}/docker-compose.yml

                        scp -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            \$ENV_FILE \
                            \${SSH_USER}@${params.DEPLOY_HOST}:${params.DEPLOY_DIR}/.env

                        # 원격 서버에서 배포 실행
                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            \${SSH_USER}@${params.DEPLOY_HOST} << REMOTE
                                set -e
                                cd ${params.DEPLOY_DIR}

                                # 새 이미지 pull
                                docker pull ${env.IMAGE_NAME}:${env.IMAGE_TAG}

                                # docker-compose의 이미지를 빌드 태그로 고정
                                sed -i 's|image: .*lightrag.*|image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}|g' docker-compose.yml

                                # 컨테이너 재시작 (데이터 볼륨 유지)
                                docker compose down --timeout 30 || true
                                docker compose up -d

                                echo "Deploy completed: ${env.IMAGE_TAG}"
REMOTE
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'deploy-server-ssh',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \
                            \${SSH_USER}@${params.DEPLOY_HOST} << REMOTE
                                echo "Health check: http://localhost:${env.APP_PORT}/health"
                                for i in \$(seq 1 18); do
                                    if curl -sf http://localhost:${env.APP_PORT}/health > /dev/null 2>&1; then
                                        echo "Health check PASSED (attempt \$i)"
                                        exit 0
                                    fi
                                    echo "Waiting... (\$i/18)"
                                    sleep 10
                                done
                                echo "Health check FAILED after 3 minutes"
                                docker compose -f ${params.DEPLOY_DIR}/docker-compose.yml logs --tail=50
                                exit 1
REMOTE
                    """
                }
            }
        }
    }

    post {
        success {
            echo "Deployment SUCCESS: ${env.IMAGE_NAME}:${env.IMAGE_TAG} → ${params.DEPLOY_HOST}:${env.APP_PORT}"
        }
        failure {
            withCredentials([sshUserPrivateKey(
                credentialsId: 'deploy-server-ssh',
                keyFileVariable: 'SSH_KEY',
                usernameVariable: 'SSH_USER'
            )]) {
                sh """
                    echo "Deployment FAILED — attempting rollback"
                    ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \
                        \${SSH_USER}@${params.DEPLOY_HOST} << REMOTE
                            cd ${params.DEPLOY_DIR}
                            PREV_TAG=\$(docker images ${env.IMAGE_NAME} --format '{{.Tag}}' \
                                | grep -v latest | grep -v ${env.IMAGE_TAG} \
                                | sort -t'-' -k2 -r | head -1)
                            if [ -n "\$PREV_TAG" ]; then
                                echo "Rolling back to: \$PREV_TAG"
                                sed -i "s|image: .*lightrag.*|image: ${env.IMAGE_NAME}:\$PREV_TAG|g" docker-compose.yml
                                docker compose down --timeout 30 || true
                                docker compose up -d
                            else
                                echo "No previous image found for rollback"
                            fi
REMOTE
                """
            }
        }
        always {
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
