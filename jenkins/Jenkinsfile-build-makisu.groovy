/*
    To learn how to use this sample pipeline, follow the guide below and enter the
    corresponding values for your environment and for this repository:
    - https://github.com/ibm-cloud-architecture/refarch-cloudnative-devops-kubernetes
*/

// Pod Template
def podLabel = "orders"
def cloud = env.CLOUD ?: "kubernetes"
def registryCredsID = env.REGISTRY_CREDENTIALS ?: "registry-credentials-id"
def serviceAccount = env.SERVICE_ACCOUNT ?: "jenkins"

// Pod Environment Variables
def registry = env.REGISTRY ?: "docker.io"
def imageName = env.IMAGE_NAME ?: "ibmcase/bluecompute-orders"

podTemplate(
    label: podLabel, 
    cloud: cloud, 
    serviceAccount: serviceAccount, 
    yaml: """
kind: Pod
metadata:
  name: image-build
spec:
  containers:
  - name: jdk
    image: ibmcase/openjdk-bash:alpine
    tty: true
    command:
    - /bin/cat
    volumeMounts:
    - name: gradle
      mountPath: /home/gradle/.gradle
  - name: makisu
    image: jkwong/makisu-alpine:v0.1.11
    imagePullPolicy: Always
    tty: true
    command:
    - /bin/cat
    volumeMounts:
    - name: makisu-storage
      mountPath: /makisu-storage
  - name: skopeo
    image: jkwong/skopeo-jenkins:latest
    tty: true
    command:
    - /bin/cat
  volumes:
  - name: gradle
    emptyDir: {}
  - name: makisu-storage
    emptyDir: {}
"""
) {
    node(podLabel) {
        checkout scm

        stage('Local - Build and Unit Test') {
            container(name:'jdk', shell:'/bin/bash') {
                sh """
                #!/bin/bash
                ./gradlew build
                """
            }
        }

        stage('Docker - Build Image') {
            container(name:'makisu', shell:'/bin/sh') {
                sh """
                #!/bin/sh
                /makisu-internal/makisu build \
                  --modifyfs \
                  -t ${IMAGE_NAME}:${env.BUILD_NUMBER} \
                  --dest `pwd`/image.tar \
                  `pwd`
                """
            }
        }
        stage ('Docker - push image') {
            container(name:'skopeo', shell:'/bin/sh') {
                withCredentials([usernamePassword(credentialsId: registryCredsID,
                                usernameVariable: 'USERNAME',
                                passwordVariable: 'PASSWORD')]) {
                    sh """
                    #!/bin/sh
                    /usr/bin/skopeo copy \
                      --dest-creds ${USERNAME}:${PASSWORD} \
                      --dest-tls-verify=false \
                      docker-archive:`pwd`/image.tar \
                      docker://${REGISTRY}/${IMAGE_NAME}:${env.BUILD_NUMBER}
                    """
                }
            }
        }
    }
}