// =============================================================================
// Jenkins Script Console 실행 스크립트
// 접속: https://jenkins.uiscloud.net/script
// 아래 1번, 2번을 순서대로 각각 실행하세요.
// =============================================================================

// =============================================================================
// [1단계] env-file-lightrag Credential 생성
// =============================================================================
import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import org.jenkinsci.plugins.plaincredentials.impl.*

// 현재 .env 파일 내용 (base64 인코딩)
// 파일: uiscloud-LightRAG/.env 기준
def envBase64 = "IyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBTZXJ2ZXIgQ29uZmlndXJhdGlvbgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKSE9TVD0wLjAuMC4wClBPUlQ9OTYyMQpXRUJVSV9USVRMRT0nTXkgR3JhcGggS0InCldFQlVJX0RFU0NSSVBUSU9OPSdTaW1wbGUgYW5kIEZhc3QgR3JhcGggQmFzZWQgUkFHIFN5c3RlbScKCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwojIyMgTExNIENvbmZpZ3VyYXRpb24KIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCkxMTV9CSU5ESU5HPW9wZW5haQpMTE1fQklORElOR19IT1NUPWh0dHA6Ly8xOTIuMTY4LjAuMjEwOjQwMDAvdjEKTExNX0JJTkRJTkdfQVBJX0tFWT1zay1kZ3gtcHJveHkKTExNX01PREVMPVF3ZW4zLTMwQi1BM0IKTExNX1RJTUVPVVQ9MzAwCk1BWF9BU1lOQz0xCgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKIyMjIEVtYmVkZGluZyBDb25maWd1cmF0aW9uCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwpFTUJFRERJTkdfQklORElORz1vcGVuYWkKRU1CRURESU5HX0JJTkRJTkdfSE9TVD1odHRwOi8vMTkyLjE2OC4wLjIxMDo0MDAwL3YxCkVNQkVERElOR19CSU5ESU5HX0FQSV9LRVk9c2stZGd4LXByb3h5CkVNQkVERElOR19NT0RFTD1iZ2UtbTMKRU1CRURESU5HX0RJTT0xMDI0CkVNQkVERElOR19NQVhfVE9LRU5fU0laRT04MTkyCgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKIyMjIFN0b3JhZ2UgQ29uZmlndXJhdGlvbiAobGlnaHR3ZWlnaHQsIGZpbGUtYmFzZWQpCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwpMSUdIVFJBR19LVl9TVE9SQUdFPUpzb25LVlN0b3JhZ2UKTElHSFRSQUdfVkVDVE9SX1NUT1JBR0U9TmFub1ZlY3RvckRCU3RvcmFnZQpMSUdIVFJBR19HUkFQSF9TVE9SQUdFPU5ldHdvcmtYU3RvcmFnZQpMSUdIVFJBR19ET0NfU1RBVFVTX1NUT1JBR0U9SnNvbkRvY1N0YXR1c1N0b3JhZ2UKCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwojIyMgUGlwZWxpbmUgQ29uZmlndXJhdGlvbgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKTUFYX1BBUkFMTEVMX0lOU0VSVD0yCk1BWF9HTEVBTklORz0wClNVTU1BUllfTEFOR1VBR0U9S29yZWFuCkVOVElUWV9FWFRSQUNUSU9OX1VTRV9KU09OPWZhbHNlCgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKIyMjIFBhcnNlciBDb25maWd1cmF0aW9uCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwpMSUdIVFJBR19QQVJTRVI9KjpuYXRpdmUtdGVQLCo6bGVnYWN5LVIKVkxNX1BST0NFU1NfRU5BQkxFPWZhbHNlCgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKIyMjIFJlcmFuayAoZGlzYWJsZWQpCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwpSRVJBTktfQklORElORz1udWxsCgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKIyMjIFF3ZW4zIFRoaW5raW5nIEJ1ZGdldAojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKT1BFTkFJX0xMTV9FWFRSQV9CT0RZPXsiY2hhdF90ZW1wbGF0ZV9rd2FyZ3MiOiB7InRoaW5raW5nX2J1ZGdldCI6IDgwMDB9fQo="

def credId    = "env-file-lightrag"
def credDesc  = "LightRAG production .env (Qwen3-30B-A3B + bge-m3, file-based storage)"

// 기존 credential 존재 여부 확인
def store  = SystemCredentialsProvider.instance.store
def domain = Domain.global()
def existing = com.cloudbees.plugins.credentials.CredentialsProvider
    .lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials,
        Jenkins.instance,
        null,
        []
    ).find { it.id == credId }

if (existing) {
    store.removeCredentials(domain, existing)
    println "기존 '${credId}' credential 삭제 후 재생성합니다."
}

def credential = new FileCredentialsImpl(
    CredentialsScope.GLOBAL,
    credId,
    credDesc,
    ".env",
    SecretBytes.fromBytes(Base64.decoder.decode(envBase64))
)

store.addCredentials(domain, credential)
println "✓ Credential '${credId}' 생성 완료"
println "  설명: ${credDesc}"


// =============================================================================
// [2단계] lightrag-pipeline Jenkins 파이프라인 잡 생성
// (1단계 실행 후 아래 내용만 따로 실행하세요)
// =============================================================================
/*
import jenkins.model.*

def jobName   = "lightrag-pipeline"
def repoUrl   = "https://github.com/rockgis/uiscloud-LightRAG.git"
def credId    = "github-pat"
def branch    = "*/main"

def configXml = """
<flow-definition plugin="workflow-job">
  <description>LightRAG CI/CD Pipeline — Qwen3-30B-A3B + bge-m3, file-based storage</description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <jenkins.model.BuildDiscarderProperty>
      <strategy class="hudson.tasks.LogRotator">
        <numToKeep>10</numToKeep>
      </strategy>
    </jenkins.model.BuildDiscarderProperty>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.StringParameterDefinition>
          <name>DEPLOY_HOST</name>
          <defaultValue>192.168.0.151</defaultValue>
          <description>배포 서버 IP / 호스트명</description>
          <trim>false</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>DEPLOY_DIR</name>
          <defaultValue>/svc/app/lightrag</defaultValue>
          <description>배포 서버의 대상 디렉토리</description>
          <trim>false</trim>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs>
        <hudson.plugins.git.UserRemoteConfig>
          <url>${repoUrl}</url>
          <credentialsId>${credId}</credentialsId>
        </hudson.plugins.git.UserRemoteConfig>
      </userRemoteConfigs>
      <branches>
        <hudson.plugins.git.BranchSpec>
          <name>${branch}</name>
        </hudson.plugins.git.BranchSpec>
      </branches>
      <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
    </scm>
    <scriptPath>Jenkinsfile</scriptPath>
    <lightweight>true</lightweight>
  </definition>
</flow-definition>
"""

if (Jenkins.instance.getItem(jobName)) {
    println "기존 '${jobName}' 잡이 있습니다. 삭제 후 재생성합니다."
    Jenkins.instance.getItem(jobName).delete()
}

Jenkins.instance.createProjectFromXML(jobName, new ByteArrayInputStream(configXml.bytes))
println "✓ Pipeline '${jobName}' 생성 완료"
println "  Repository : ${repoUrl}"
println "  Branch     : ${branch}"
println "  Script     : Jenkinsfile"
println "  바로 실행: https://jenkins.uiscloud.net/job/${jobName}/build"
*/
