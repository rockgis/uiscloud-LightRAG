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
                sh """
                    docker build \
                        --cache-from ${env.IMAGE_NAME}:latest \
                        --build-arg BUILDKIT_INLINE_CACHE=1 \
                        -f Dockerfile \
                        -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} \
                        -t ${env.IMAGE_NAME}:latest \
                        .
                """
            }
        }

        stage('Push to Registry') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                        docker push ${env.IMAGE_NAME}:${env.IMAGE_TAG}
                        docker push ${env.IMAGE_NAME}:latest
                        docker logout
                    """
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: 'env-file-lightrag', variable: 'ENV_FILE')]) {
                    sh """
                        mkdir -p ${params.DEPLOY_DIR}/data/{rag_storage,inputs,prompts}

                        cp docker-compose.yml ${params.DEPLOY_DIR}/docker-compose.yml

                        # .env: 서버에 이미 존재하면 유지, 없을 때만 credentials에서 복사
                        # (운영 중 수동 변경된 설정을 배포가 덮어쓰는 것을 방지)
                        if [ ! -f "${params.DEPLOY_DIR}/.env" ]; then
                            sudo rm -f ${params.DEPLOY_DIR}/.env 2>/dev/null || true
                            cp \$ENV_FILE ${params.DEPLOY_DIR}/.env
                            echo ".env: credentials에서 신규 생성"
                        else
                            echo ".env: 서버 기존 파일 유지 (변경하려면 서버에서 직접 수정 후 docker compose up -d --force-recreate)"
                        fi

                        cd ${params.DEPLOY_DIR}
                        docker pull ${env.IMAGE_NAME}:${env.IMAGE_TAG}
                        sed -i 's|image: .*lightrag.*|image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}|g' docker-compose.yml
                        docker compose down --timeout 30 || true
                        docker compose up -d

                        echo "Deploy completed: ${env.IMAGE_TAG}"
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def maxAttempts = 18
                    def url = "http://${params.DEPLOY_HOST}:${env.APP_PORT}/health"
                    echo "Health check URL: ${url}"

                    for (int i = 1; i <= maxAttempts; i++) {
                        def status = sh(
                            script: "curl -sf '${url}'",
                            returnStatus: true
                        )
                        if (status == 0) {
                            echo "Health check PASSED (attempt ${i}/${maxAttempts})"
                            return
                        }
                        echo "Waiting... (${i}/${maxAttempts})"
                        if (i < maxAttempts) {
                            sleep(10)
                        }
                    }

                    sh "docker compose -f ${params.DEPLOY_DIR}/docker-compose.yml logs --tail=50 || true"
                    error "Health check FAILED after ${maxAttempts * 10} seconds"
                }
            }
        }
    }

    post {
        success {
            echo "Deployment SUCCESS: ${env.IMAGE_NAME}:${env.IMAGE_TAG} -> ${params.DEPLOY_HOST}:${env.APP_PORT}"
        }
        failure {
            script {
                echo "Deployment FAILED -- attempting rollback"
                def prevTag = sh(
                    script: """
                        docker images ${env.IMAGE_NAME} --format '{{.Tag}}' \
                            | grep -v latest \
                            | grep -v '${env.IMAGE_TAG}' \
                            | sort -r | head -1
                    """,
                    returnStdout: true
                ).trim()

                if (prevTag) {
                    echo "Rolling back to: ${prevTag}"
                    sh """
                        sed -i 's|image: .*lightrag.*|image: ${env.IMAGE_NAME}:${prevTag}|g' \
                            ${params.DEPLOY_DIR}/docker-compose.yml
                        cd ${params.DEPLOY_DIR}
                        docker compose down --timeout 30 || true
                        docker compose up -d
                    """
                    echo "Rollback to ${prevTag} completed"
                } else {
                    echo "No previous image found for rollback"
                }
            }
        }
        always {
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
