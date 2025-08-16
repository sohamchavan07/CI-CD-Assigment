pipeline {
  agent any

  environment {
    STAGING_HOST   = 'your.staging.host'
    STAGING_USER   = 'deploy'
    STAGING_PATH   = '/var/www/myapp'
    SLACK_CHANNEL  = '#deployments'
    APPROVERS      = 'admin,devops'        // 🔑 Only these users can approve
    NOTIFY_EMAIL   = 'team@example.com'    // 🔑 Email to notify
  }

  stages {
    stage('Checkout') {
      when { branch 'staging' }
      steps {
        checkout scm
      }
    }

    stage('Build') {
      when { branch 'staging' }
      steps {
        echo "Building app..."
        sh 'echo "Build complete"'
      }
    }

    stage('Approval Required') {
      when { branch 'staging' }
      steps {
        script {
          // 🔑 Send email requesting approval
          emailext(
            subject: "🚦 Staging Deployment Pending Approval (Build #${env.BUILD_NUMBER})",
            body: """<p>Deployment to *STAGING* is waiting for approval.</p>
                     <p><b>Job:</b> ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                     <p><b>URL:</b> <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
                     <p>Please log into Jenkins and approve/reject.</p>""",
            to: "${env.NOTIFY_EMAIL}"
          )

          // 🔑 Manual approval
          timeout(time: 30, unit: 'MINUTES') {   // prevent indefinite wait
            input(
              message: "Deploy to STAGING?",
              ok: "Proceed",
              submitter: env.APPROVERS         // only authorized users
            )
          }
        }
      }
    }

    stage('Deploy to Staging') {
      when { branch 'staging' }
      steps {
        sshagent(credentials: ['staging-ssh-key']) {
          sh '''
            echo "Deploying to staging..."
            ssh -o StrictHostKeyChecking=no $STAGING_USER@$STAGING_HOST "echo 'Deployed build $BUILD_NUMBER to $STAGING_PATH'"
          '''
        }
      }
    }
  }

  post {
    success {
      // 🔑 Send email after successful deployment
      emailext(
        subject: "✅ Staging Deployment Succeeded (Build #${env.BUILD_NUMBER})",
        body: """<p>Deployment to *STAGING* was successful.</p>
                 <p><b>Job:</b> ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                 <p><b>URL:</b> <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>""",
        to: "${env.NOTIFY_EMAIL}"
      )
    }
    aborted {
      emailext(
        subject: "⚠️ Staging Deployment Aborted (Build #${env.BUILD_NUMBER})",
        body: "<p>The staging deployment was aborted or timed out.</p>",
        to: "${env.NOTIFY_EMAIL}"
      )
    }
    failure {
      emailext(
        subject: "❌ Staging Deployment Failed (Build #${env.BUILD_NUMBER})",
        body: "<p>The staging deployment failed. Check Jenkins logs.</p>",
        to: "${env.NOTIFY_EMAIL}"
      )
    }
  }
}
