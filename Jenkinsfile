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
                numToKeepStr: '20',
                artifactNumToKeepStr: '10'
            )
        )

        timestamps()
    }

    environment {
        CONVEYOR_AGREE_TO_LICENSE = '1'
        MAVEN_OPTS = '-Xmx2g'

        /*
         * The credentials entry must be a Jenkins
         * "Username with password" credential:
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
                    env.PRODUCTS_GEN =
                        shouldPushProducts(env.BRANCH_NAME)

                    currentBuild.description =
                        "${env.BRANCH_NAME} @ ${env.SHORT_COMMIT}"

                    echo(
                        "${env.BRANCH_NAME} build at " +
                        "${env.CURRENT_COMMIT}; product generation is " +
                        "${env.PRODUCTS_GEN}"
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

                    echo "Archive tools:"
                    command -v unzip
                '''
            }
        }

        stage('Build platform installers') {
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
                        echo "The Conveyor output directory was not created."
                        exit 1
                    fi
                    if [ -z "$(find output -type f -print -quit)" ]; then
                        echo "No Conveyor artifacts were generated."
                        exit 1
                    fi
                    echo "Generated Conveyor artifacts:"
                    find output -type f -print | sort
                '''
            }
        }

        stage('Prepare platform folders') {
            steps {
                script {
                    def destination = productsFolderName(env.BRANCH_NAME)
                    prepareProductsUpload(destination)
                }
            }
        }

        stage('Archive artifacts') {
            steps {
                archiveArtifacts(
                    artifacts: 'output/**/*',
                    fingerprint: true,
                    allowEmptyArchive: false
                )
            }
        }

        stage('Push products') {
            when {
                expression {
                    env.PRODUCTS_GEN == 'yes'
                }
            }

            steps {
                script {
                    def destination = productsFolderName(env.BRANCH_NAME)

                    echo(
                        "Uploading platform products to " +
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
                            # Verify access to the products bucket before
                            # preparing and deleting any existing products.
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
                "Conveyor installer build completed successfully for " +
                "${env.BRANCH_NAME}."
            )
        }

        unsuccessful {
            echo(
                "The Conveyor installer build or product upload failed for " +
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
 *   others  -> build and archive only
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
 * Sort the Conveyor site output into win, linux and mac directories.
 */
def prepareProductsUpload(String destination) {
    withEnv([
        "PRODUCTS_DESTINATION=${destination}"
    ]) {
        sh '''
            set -eu

            staging_root="${WORKSPACE}/minio/${PRODUCTS_DESTINATION}"
            win_directory="${staging_root}/win"
            linux_directory="${staging_root}/linux"
            mac_directory="${staging_root}/mac"

            rm -rf "${staging_root}"
            mkdir -p \
                "${win_directory}" \
                "${linux_directory}" \
                "${mac_directory}"

            # Windows installer products generated by the Conveyor site task.
            find output -type f \
                \( \
                    -iname '*.exe' \
                    -o -iname '*.msix' \
                    -o -iname '*.appinstaller' \
                    -o -iname 'install.ps1' \
                    -o -iname '*.crt' \
                \) \
                -exec cp -v '{}' "${win_directory}/" \;

            # Linux Debian/Ubuntu installer packages.
            find output -type f \
                -iname '*.deb' \
                -exec cp -v '{}' "${linux_directory}/" \;

            # A Conveyor macOS package is a ZIP containing an .app bundle.
            find output -type f -iname '*.zip' -print |
                while IFS= read -r archive; do
                    if unzip -Z1 "${archive}" | grep -qE '\\.app(/|$)'; then
                        cp -v "${archive}" "${mac_directory}/"
                    fi
                done

            # Keep macOS update/install support files alongside the app ZIPs.
            find output -type f \
                \( \
                    -iname 'appcast*.rss' \
                    -o -iname '*.delta' \
                    -o -iname 'launch.mac' \
                \) \
                -exec cp -v '{}' "${mac_directory}/" \;

            if [ -z "$(find "${win_directory}" -type f -print -quit)" ]; then
                echo "No Windows installer was found in the Conveyor output."
                exit 1
            fi

            if [ -z "$(find "${linux_directory}" -type f -print -quit)" ]; then
                echo "No Linux .deb package was found in the Conveyor output."
                exit 1
            fi

            if [ -z "$(find "${mac_directory}" -type f -print -quit)" ]; then
                echo "No macOS app ZIP was found in the Conveyor output."
                exit 1
            fi

            echo "Products prepared for upload:"
            find "${staging_root}" -type f -print | sort
        '''
    }
}

/**
 * Replace the branch directory in MinIO, then upload win/linux/mac.
 */
def uploadProducts(String destination) {
    withEnv([
        "PRODUCTS_DESTINATION=${destination}"
    ]) {
        sh '''
            set -eu

            staging_directory="${WORKSPACE}/minio/${PRODUCTS_DESTINATION}"
            remote_directory="${MINIO_PRODUCTS_PATH}/${PRODUCTS_DESTINATION}"

            echo "Removing previous products from ${remote_directory}"
            mc rm \
                --recursive \
                --force \
                "${remote_directory}" \
                || echo "${remote_directory} does not exist"

            echo "Uploading products from:"
            echo "${staging_directory}"

            echo "Uploading products to:"
            echo "${MINIO_PRODUCTS_PATH}/"

            # Copy the directory, rather than only its contents. This creates:
            # minio/products/klab-ide/latest
            # minio/products/klab-ide/develop

            mc cp \
                --recursive \
                "${staging_directory}" \
                "${MINIO_PRODUCTS_PATH}/"

            echo "Products uploaded successfully."

            echo "Uploaded files:"
            mc ls \
                --recursive \
                "${remote_directory}"
        '''
    }
}
