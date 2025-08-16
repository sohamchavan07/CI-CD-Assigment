pipeline {
  agent any
  options {
    timestamps()
    ansiColor('xterm')
  }

  environment {
    // ---- Edit these ----
    STAGING_HOST   = 'your.staging.host'          // hostname or IP
    STAGING_USER   = 'deploy'                     // remote SSH user
    STAGING_PATH   = '/var/www/myapp'             // app root on server
    SLACK_CHANNEL  = '#deployments'               // Slack channel
    // --------------------
  }

  stages {
    stage('Gate: staging branch only') {
      when { not { branch 'staging' } }
      steps {
        echo "Non-staging branch (${env.BRANCH_NAME}), skipping deploy stages."
      }
    }

    stage('Checkout') {
      when { branch 'staging' }
      steps {
        checkout scm
        sh 'git --version'
      }
    }

    stage('Build') {
      when { branch 'staging' }
      steps {
        echo '🔧 Building the app...'
        // --- Pick ONE build block and remove the others ---
        // Node.js
        sh '''
          if [ -f package.json ]; then
            command -v node >/dev/null || true
            npm ci
            npm run build
          fi
        '''
        // Java (Maven)
        sh '''
          if [ -f pom.xml ]; then
            mvn -v || true
            mvn -B -DskipTests package
          fi
        '''
        // Ruby on Rails
        sh '''
          if [ -f Gemfile ]; then
            bundle -v || true
            bundle install --path vendor/bundle
            RAILS_ENV=production bundle exec rake assets:precompile
          fi
        '''
      }
    }

    stage('Package artifact') {
      when { branch 'staging' }
      steps {
        echo '📦 Packaging artifact...'
        sh '''
          set -eux
          rm -f build.tgz || true
          if [ -d dist ]; then
            tar -czf build.tgz dist
          elif ls target/*.jar >/dev/null 2>&1; then
            tar -czf build.tgz target/*.jar
          elif [ -d public ]; then
            tar -czf build.tgz public
          else
            tar -czf build.tgz .
          fi
          ls -lh build.tgz
        '''
        archiveArtifacts artifacts: 'build.tgz', fingerprint: true
      }
    }

    stage('Deploy to Staging (SSH)') {
      when { branch 'staging' }
      steps {
        echo '🚀 Deploying to staging...'
        sshagent(credentials: ['staging-ssh-key']) {
          sh '''
            set -eux
            REMOTE="${STAGING_USER}@${STAGING_HOST}"

            # Prepare remote dirs
            ssh -o StrictHostKeyChecking=no "$REMOTE" "mkdir -p ${STAGING_PATH}/releases ${STAGING_PATH}/current"

            # Upload artifact with a release marker
            REL="build-$BUILD_NUMBER.tgz"
            scp -o StrictHostKeyChecking=no build.tgz "$REMOTE:${STAGING_PATH}/releases/$REL"

            # Unpack and (optionally) restart service
            ssh -o StrictHostKeyChecking=no "$REMOTE" bash -lc '
              set -eux
              cd ${STAGING_PATH}
              rm -rf current/*
              tar -xzf releases/'"$REL"' -C current --strip-components=0 || tar -xzf releases/'"$REL"' -C current
              # App-specific steps go here, for example:
              if command -v systemctl >/dev/null 2>&1; then
                sudo systemctl restart myapp || true
              fi
            '
          '''
        }
      }
    }
  }

  post {
    always {
      script {
        // Collect commit details
        def commit = sh(script: "git log -1 --pretty=format:'%h — %s (by %an)'", returnStdout: true).trim()
        def status = currentBuild.currentResult
        def emoji  = (status == 'SUCCESS') ? '✅' : (status == 'FAILURE' ? '❌' : '⚠️')
        def msg = """${emoji} *${env.JOB_NAME}* #${env.BUILD_NUMBER} — staging
*Status:* ${status}
*Commit:* ${commit}
*Duration:* ${currentBuild.durationString}
*URL:* ${env.BUILD_URL}""".stripIndent()

        slackSend(channel: env.SLACK_CHANNEL, message: msg)
      }
    }
  }
}
