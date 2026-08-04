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
        PUBLIC_PRODUCTS_URL = 'https://products.integratedmodelling.org/klab-ide'
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

                    env.PRODUCTS_DESTINATION =
                        productsFolderName(env.BRANCH_NAME)

                    env.CONVEYOR_SITE_BASE_URL =
                        "${env.PUBLIC_PRODUCTS_URL}/" +
                        "${env.PRODUCTS_DESTINATION}"

                    currentBuild.description =
                        "${env.BRANCH_NAME} @ ${env.SHORT_COMMIT}"

                    echo(
                        "${env.BRANCH_NAME} build at " +
                        "${env.CURRENT_COMMIT}; product generation is " +
                        "${env.PRODUCTS_GEN}; Conveyor site is " +
                        "${env.CONVEYOR_SITE_BASE_URL}"
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
                    command -v zip
                '''
            }
        }

        stage('Build platform installers') {
            steps {
                sh '''
                    set -eu
                    rm -rf output

                    if [ ! -f conveyor.conf ]; then
                        echo "conveyor.conf was not found."
                        exit 1
                    fi

                    # Generate installers with URLs rooted directly at:
                    # https://products.integratedmodelling.org/klab-ide/<branch>
                    cp conveyor.conf .conveyor.conf.jenkins-backup
                    trap 'cp .conveyor.conf.jenkins-backup conveyor.conf; rm -f .conveyor.conf.jenkins-backup' EXIT HUP INT TERM
                    printf '\\napp.site.base-url = "%s"\\n' \
                        "${CONVEYOR_SITE_BASE_URL}" \
                        >> conveyor.conf

                    echo "Conveyor site URL: ${CONVEYOR_SITE_BASE_URL}"

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
                    artifacts: 'minio/**/klab-ide-windows-*.zip',
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
 * Stage the complete Conveyor site at the branch root and add one Windows
 * download bundle containing only the files needed for Windows installation.
 */
def prepareProductsUpload(String destination) {
    withEnv([
        "PRODUCTS_DESTINATION=${destination}"
    ]) {
        sh '''
            set -eu

            staging_root="${WORKSPACE}/minio/${PRODUCTS_DESTINATION}"
            windows_bundle="klab-ide-windows-${PRODUCTS_DESTINATION}.zip"
            windows_bundle_tmp="${WORKSPACE}/${windows_bundle}"
            windows_file_list="${WORKSPACE}/windows-bundle-files.txt"

            rm -rf "${staging_root}"
            mkdir -p "${staging_root}"

            # Keep the complete Conveyor site unchanged at the branch root.
            # Installer/update metadata references files relative to this root.
            cp -a output/. "${staging_root}/"

            # Validate the files required by the self-signed Windows installer.
            for required_pattern in \
                'install.ps1' \
                '*.appinstaller' \
                '*.crt' \
                '*.exe' \
                '*.msix'; do
                if [ -z "$(find "${staging_root}" \
                    -maxdepth 1 \
                    -type f \
                    -name "${required_pattern}" \
                    -print -quit)" ]; then
                    echo "Missing required Windows file: ${required_pattern}"
                    exit 1
                fi
            done

            cat > "${staging_root}/README-WINDOWS.txt" <<'EOF'
k.LAB IDE Windows installation
==============================

This package is signed with a development/self-signed certificate.

Installation:

1. Extract every file from this ZIP into the same directory.
2. Open PowerShell as Administrator.
3. Change to the extracted directory.
4. Run:

   Set-ExecutionPolicy -Scope Process Bypass -Force
   .\\install.ps1

Do not install only the MSIX unless the certificate has already been trusted.
EOF

            # Build an explicit file list so the ZIP contains only the Windows
            # installation set, not Linux/macOS packages or site metadata.
            rm -f \
                "${windows_bundle_tmp}" \
                "${windows_file_list}"

            printf '%s\\n' \
                'README-WINDOWS.txt' \
                'install.ps1' \
                > "${windows_file_list}"

            for required_pattern in \
                '*.appinstaller' \
                '*.crt' \
                '*.exe' \
                '*.msix'; do
                find "${staging_root}" \
                    -maxdepth 1 \
                    -type f \
                    -name "${required_pattern}" \
                    -printf '%f\\n' \
                    >> "${windows_file_list}"
                done

            sort -u \
                "${windows_file_list}" \
                -o "${windows_file_list}"

            echo "Windows bundle contents:"
            cat "${windows_file_list}"

            (
                cd "${staging_root}"
                zip -9 "${windows_bundle_tmp}" -@ < "${windows_file_list}"
            )

            mv -v \
                "${windows_bundle_tmp}" \
                "${staging_root}/${windows_bundle}"

            # Confirm that the generated installer metadata uses the public
            # branch-root URL rather than an obsolete host or /site subfolder.
            if ! grep -Fq \
                "${CONVEYOR_SITE_BASE_URL}" \
                "${staging_root}/install.ps1"; then
                echo "install.ps1 does not reference ${CONVEYOR_SITE_BASE_URL}"
                exit 1
            fi

            for appinstaller in "${staging_root}"/*.appinstaller; do
                if ! grep -Fq \
                    "${CONVEYOR_SITE_BASE_URL}" \
                    "${appinstaller}"; then
                    echo "${appinstaller} does not reference ${CONVEYOR_SITE_BASE_URL}"
                exit 1
            fi
            done

            echo "Products prepared at the branch root:"
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
