#!/usr/bin/env groovy

pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21-conveyor'
    }

    options {
        /*
         * The repository is checked out explicitly in the Checkout stage.
         */
        skipDefaultCheckout(true)

        /*
         * Prevent concurrent builds of the same branch from updating the
         * same MinIO destination.
         */
        disableConcurrentBuilds()

        buildDiscarder(
            logRotator(
                numToKeepStr: '20'
            )
        )

        timestamps()
    }

    environment {
        CONVEYOR_AGREE_TO_LICENSE = '1'
        MAVEN_OPTS = '-Xmx2g'

        /*
         * Jenkins "Username with password" credential:
         *
         * Username = MinIO access key
         * Password = MinIO secret key
         */
        MINIO_HOST = 'http://192.168.250.224:9000'
        MINIO_CREDENTIALS = 'bc42afcf-7037-4d23-a7cb-6c66b8a0aa45'

        /*
         * "minio" is the mc alias.
         * "products" is the bucket.
         * "klab-ide" is the prefix inside the bucket.
         */
        MINIO_PRODUCTS_PATH = 'minio/products/klab-ide'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm

                script {
                    env.CURRENT_COMMIT = sh(
                        script: 'git rev-parse --verify HEAD',
                        returnStdout: true
                    ).trim()

                    env.SHORT_COMMIT = sh(
                        script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()

                    /*
                     * Products are published only from master and develop.
                     */
                    env.PRODUCTS_GEN = shouldPushProducts(env.BRANCH_NAME)
                    def destination = productsFolderName(env.BRANCH_NAME)

                    env.PRODUCTS_DESTINATION = destination
                    env.CONVEYOR_BASE_URL = "https://products.integratedmodelling.org/klab-ide/${destination}"


                    currentBuild.description =
                        "${env.BRANCH_NAME} @ ${env.SHORT_COMMIT}"

                    echo(
                        "Branch: ${env.BRANCH_NAME}\n" +
                        "Commit: ${env.CURRENT_COMMIT}\n" +
                        "Conveyor URL: ${env.CONVEYOR_BASE_URL}\n" +
                        "Product generation: ${env.PRODUCTS_GEN}"
                    )
                }

                sh '''
                    set -eu

                    echo "Branch: ${BRANCH_NAME}"
                    echo "Commit: ${CURRENT_COMMIT}"

                    echo "Repository status:"
                    git status --short --branch
                '''
            }
        }

        stage('Verify agent') {
            steps {
                sh '''
                    set -eu

                    echo "Java version:"
                    java -version

                    echo "Maven version:"
                    mvn -version

                    echo "Conveyor installation:"
                    command -v conveyor
                    conveyor --help >/dev/null

                    echo "MinIO client installation:"
                    command -v mc
                    mc --version
                '''
            }
        }

        stage('Build Conveyor site') {
            environment {
                SIGNING_KEY = credentials('conveyor-signing-key')
            }
            steps {
                sh '''
                    set -eu
                    rm -rf output

                    # The Maven Conveyor profile forwards conveyor.target to:
                    # conveyor make <target>. The site target builds packages for
                    # every machine configured in conveyor.conf.
                    ./mvnw \
                        -B \
                        -ntp \
                        -DskipTests \
                        -Pconveyor \
                        -Dconveyor.target=site \
                        clean package

                    if [ ! -d output ]; then
                        echo "ERROR: Conveyor did not create the output directory."
                        exit 1
                    fi
                    if [ -z "$(find output -type f -print -quit)" ]; then
                        echo "ERROR: Conveyor generated no site files."
                        exit 1
                    fi

                    if [ ! -f output/download.html ]; then
                        echo "ERROR: Conveyor site did not generate output/download.html."
                        exit 1
                    fi

                    echo "Generated Conveyor site:"
                    find output -type f -print | sort
                '''
            }
        }

        stage('Push Conveyor site') {
            when {
                expression {
                    env.PRODUCTS_GEN == 'yes'
                }
            }

            steps {
                script {
                    def destination = env.PRODUCTS_DESTINATION

                    echo(
                        "Uploading the unchanged Conveyor site to " +
                        "${env.MINIO_PRODUCTS_PATH}/${destination}"
                    )

                    withCredentials([
                        usernamePassword(
                            credentialsId: env.MINIO_CREDENTIALS,
                            passwordVariable: 'SECRETKEY',
                            usernameVariable: 'ACCESSKEY'
                        )
                    ]) {
                        sh '''
                            set -eu
                            set +x
                            mc alias set \
                                minio \
                                "${MINIO_HOST}" \
                                "${ACCESSKEY}" \
                                "${SECRETKEY}"
                            echo "MinIO connection configured."
                            mc ls minio/products >/dev/null
                        '''

                        uploadProducts(destination)
                    }
                }
            }
        }
    }

    post {
        success {
            echo(
                "Conveyor site build and upload completed successfully for " +
                "${env.BRANCH_NAME}."
            )
        }

        unsuccessful {
            echo(
                "The Conveyor site build or product upload failed for " +
                "${env.BRANCH_NAME}."
            )
        }

        cleanup {
            deleteDir()
        }
    }
}

/**
 * Determines whether products should be uploaded.
 *
 *   master  -> upload
 *   develop -> upload
 *   others  -> build only
 */
def shouldPushProducts(String branchName) {
    return branchName == 'master' || branchName == 'develop'
        ? 'yes'
        : 'no'
}

/**
 * Determines the directory name used in MinIO.
 *
 * The master branch is published as "latest", matching the existing
 * product publication convention.
 */
def productsFolderName(String branchName) {
    return branchName == 'master'
        ? 'latest'
        : branchName
}

/**
 * Uploads the complete Conveyor-generated site without moving, sorting,
 * renaming, repackaging, or compressing any files.
 */
def uploadProducts(String destination) {
    withEnv([
        "PRODUCTS_DESTINATION=${destination}"
    ]) {
        sh '''
            set -eu

            source_directory="${WORKSPACE}/output"
            remote_directory="${MINIO_PRODUCTS_PATH}/${PRODUCTS_DESTINATION}"

            if [ ! -f "${source_directory}/download.html" ]; then
                echo "ERROR: ${source_directory}/download.html does not exist."
                    exit 1
                fi

            echo "Replacing the existing site at:"
            echo "${remote_directory}"

            mc rm \
                --recursive \
                --force \
                "${remote_directory}" \
                || echo "${remote_directory} does not exist"

            echo "Uploading Conveyor output directly from:"
            echo "${source_directory}"

            echo "Uploading Conveyor output directly to:"
            echo "${remote_directory}"

            # mc mirror copies the contents of output/ directly into the
            # branch directory. No local file movement or compression occurs.
            mc mirror \
                --overwrite \
                "${source_directory}/" \
                "${remote_directory}/"

            echo "Conveyor site uploaded successfully."

            echo "Uploaded files:"
            mc ls \
                --recursive \
                "${remote_directory}"
        '''
    }
}
