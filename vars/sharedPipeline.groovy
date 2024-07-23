def call() {
    pipeline {
        agent any

        environment {
            DOCKERHUB_CREDENTIALS = credentials('dockerhubpwd')
            SLACK_CREDENTIALS = credentials('slackpwd')
        }

        parameters {
            string(name: 'JAVA_REPO', defaultValue: 'https://github.com/pramilasawant/testhello.git', description: 'Java Application Repository')
            string(name: 'PYTHON_REPO', defaultValue: 'https://github.com/pramilasawant/phython-application.git', description: 'Python Application Repository')
            string(name: 'DOCKERHUB_USERNAME', defaultValue: 'pramila188', description: 'DockerHub Username')
            string(name: 'JAVA_IMAGE_NAME', defaultValue: 'testhello', description: 'Java Docker Image Name')
            string(name: 'PYTHON_IMAGE_NAME', defaultValue: 'python-app', description: 'Python Docker Image Name')
            string(name: 'JAVA_NAMESPACE', defaultValue: 'test', description: 'Kubernetes Namespace for Java Application')
            string(name: 'PYTHON_NAMESPACE', defaultValue: 'python', description: 'Kubernetes Namespace for Python Application')
        }

        stages {
            stage('Clone Repositories') {
                parallel {
                    stage('Clone Java Repo') {
                        steps {
                            git url: params.JAVA_REPO, branch: 'main'
                        }
                    }
                    stage('Clone Python Repo') {
                        steps {
                            dir('python-app') {
                                git url: params.PYTHON_REPO, branch: 'main'
                            }
                        }
                    }
                }
            }

            stage('Build and Push Docker Images') {
                parallel {
                    stage('Build and Push Java Image') {
                        steps {
                            dir('testhello') { // Ensure this directory contains the pom.xml
                                sh 'mvn clean install'
                                script {
                                    def image = docker.build("${params.DOCKERHUB_USERNAME}/${params.JAVA_IMAGE_NAME}")
                                    docker.withRegistry('', 'dockerhubpwd') {
                                        image.push()
                                    }
                                }
                            }
                        }
                    }
                    stage('Build and Push Python Image') {
                        steps {
                            dir('python-app') {
                                script {
                                    def image = docker.build("${params.DOCKERHUB_USERNAME}/${params.PYTHON_IMAGE_NAME}")
                                    docker.withRegistry('', 'dockerhubpwd') {
                                        image.push()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            stage('Deploy to Kubernetes') {
                parallel {
                    stage('Deploy Java Application') {
                        steps {
                            script {
                                try {
                                    sh 'kubectl apply -f /var/lib/jenkins/workspace/j-p-project/deploymentservice.yaml'
                                } catch (Exception e) {
                                    echo "Deployment failed: ${e.getMessage()}"
                                    error "Kubernetes deployment ended with HasError"
                                }
                            }
                        }
                    }
                    stage('Deploy Python Application') {
                        steps {
                            script {
                                kubernetesDeploy(
                                    configs: 'deploymentservice.yaml',
                                    kubeconfigId: 'k8sconfigpwd'
                                )
                            }
                        }
                    }
                }
            }
        }

        post {
            success {
                slackSend(channel: '#builds', color: 'good', message: "Build and Deployment of Java and Python applications succeeded.", tokenCredentialId: 'slackpwd')
            }
            failure {
                slackSend(channel: '#builds', color: 'danger', message: "Build and Deployment of Java and Python applications failed.", tokenCredentialId: 'slackpwd')
            }
        }
    }
}
