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
                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \${SSH_USER}@${params.DEPLOY_HOST} \\
                            "mkdir -p ${params.DEPLOY_DIR}/data/{rag_storage,inputs,prompts}"

                        scp -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            docker-compose.yml \\
                            \${SSH_USER}@${params.DEPLOY_HOST}:${params.DEPLOY_DIR}/docker-compose.yml

                        scp -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \$ENV_FILE \\
                            \${SSH_USER}@${params.DEPLOY_HOST}:/tmp/.env_lightrag

                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \${SSH_USER}@${params.DEPLOY_HOST} \\
                            "cp /tmp/.env_lightrag ${params.DEPLOY_DIR}/.env && rm /tmp/.env_lightrag"

                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \${SSH_USER}@${params.DEPLOY_HOST} \\
                            "cd ${params.DEPLOY_DIR} && docker pull ${env.IMAGE_NAME}:${env.IMAGE_TAG}"

                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \${SSH_USER}@${params.DEPLOY_HOST} \\
                            "sed -i 's|image: .*lightrag.*|image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}|g' ${params.DEPLOY_DIR}/docker-compose.yml"

                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \${SSH_USER}@${params.DEPLOY_HOST} \\
                            "cd ${params.DEPLOY_DIR} && docker compose down --timeout 30 || true"

                        ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                            \${SSH_USER}@${params.DEPLOY_HOST} \\
                            "cd ${params.DEPLOY_DIR} && docker compose up -d"

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
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'deploy-server-ssh',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    echo "Deployment FAILED -- attempting rollback"
                    def prevTag = sh(
                        script: """
                            ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                                \${SSH_USER}@${params.DEPLOY_HOST} \\
                                "docker images ${env.IMAGE_NAME} --format '{{.Tag}}' | grep -v latest | grep -v '${env.IMAGE_TAG}' | sort -r | head -1"
                        """,
                        returnStdout: true
                    ).trim()

                    if (prevTag) {
                        echo "Rolling back to: ${prevTag}"
                        sh """
                            ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                                \${SSH_USER}@${params.DEPLOY_HOST} \\
                                "sed -i 's|image: .*lightrag.*|image: ${env.IMAGE_NAME}:${prevTag}|g' ${params.DEPLOY_DIR}/docker-compose.yml"

                            ssh -i \$SSH_KEY -o StrictHostKeyChecking=no \\
                                \${SSH_USER}@${params.DEPLOY_HOST} \\
                                "cd ${params.DEPLOY_DIR} && docker compose down --timeout 30 || true && docker compose up -d"
                        """
                        echo "Rollback to ${prevTag} completed"
                    } else {
                        echo "No previous image found for rollback"
                    }
                }
            }
        }
        always {
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
