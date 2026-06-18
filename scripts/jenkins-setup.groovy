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
// 파일: /svc/app/lightrag/.env 기준 (2026-06-18, EXAONE-4.5-33B via LiteLLM)
// 재인코딩: ssh server "cat /svc/app/lightrag/.env" | base64
def envBase64 = "IyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBTZXJ2ZXIgQ29uZmlndXJhdGlvbgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKSE9TVD0wLjAuMC4wClBPUlQ9OTYyMQpXRUJVSV9USVRMRT0nTXkgR3JhcGggS0InCldFQlVJX0RFU0NSSVBUSU9OPSdTaW1wbGUgYW5kIEZhc3QgR3JhcGggQmFzZWQgUkFHIFN5c3RlbScKCiMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwojIyMgTExNIENvbmZpZ3VyYXRpb24KIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCkxMTV9CSU5ESU5HPW9wZW5haQpMTE1fQklORElOR19IT1NUPWh0dHA6Ly8xOTIuMTY4LjAuMjEwOjQwMDAvdjEKTExNX0JJTkRJTkdfQVBJX0tFWT1zay1kZ3gtcHJveHkKTExNX01PREVMPUVYQU9ORS00LjUtMzNCCkxMTV9USU1FT1VUPTYwMApNQVhfQVNZTkM9MQoKIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBFbWJlZGRpbmcgQ29uZmlndXJhdGlvbgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKRU1CRURESU5HX0JJTkRJTkc9b3BlbmFpCkVNQkVERElOR19CSU5ESU5HX0hPU1Q9aHR0cDovLzE5Mi4xNjguMC4yMTA6NDAwMC92MQpFTUJFRERJTkdfQklORElOR19BUElfS0VZPXNrLWRneC1wcm94eQpFTUJFRERJTkdfTU9ERUw9YmdlLW0zCkVNQkVERElOR19ESU09MTAyNApFTUJFRERJTkdfTUFYX1RPS0VOX1NJWkU9ODE5MgoKIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBTdG9yYWdlIENvbmZpZ3VyYXRpb24gKGxpZ2h0d2VpZ2h0LCBmaWxlLWJhc2VkKQojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKTElHSFRSQUdfS1ZfU1RPUkFHRT1Kc29uS1ZTdG9yYWdlCkxJR0hUUkFHX1ZFQ1RPUl9TVE9SQUdFPU5hbm9WZWN0b3JEQlN0b3JhZ2UKTElHSFRSQUdfR1JBUEhfU1RPUkFHRT1OZXR3b3JrWFN0b3JhZ2UKTElHSFRSQUdfRE9DX1NUQVRVU19TVE9SQUdFPUpzb25Eb2NTdGF0dXNTdG9yYWdlCgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKIyMjIFBpcGVsaW5lIENvbmZpZ3VyYXRpb24KIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCk1BWF9QQVJBTExFTF9JTlNFUlQ9MgpNQVhfR0xFQU5JTkc9MApTVU1NQVJZX0xBTkdVQUdFPUtvcmVhbgpFTlRJVFlfRVhUUkFDVElPTl9VU0VfSlNPTj1mYWxzZQoKIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBQYXJzZXIgQ29uZmlndXJhdGlvbgojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKTElHSFRSQUdfUEFSU0VSPSo6bmF0aXZlLXRlUCwqOmxlZ2FjeS1SClZMTV9QUk9DRVNTX0VOQUJMRT1mYWxzZQoKIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBSZXJhbmsgKGRpc2FibGVkKQojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKUkVSQU5LX0JJTkRJTkc9bnVsbAoKIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjCiMjIyBRd2VuMy1BV1Eg7Lac66ClIOygnOyWtAojIyMgLSBlbmFibGVfdGhpbmtpbmc6IGZhbHNlICjsp4HsoJEgdkxMTeyXkOyEnOunjCDsoIHsmqksIExpdGVMTE0gZHJvcF9wYXJhbXProZwg7KCc6rGw65CoKQojIyMgLSAvbm9fdGhpbmsgcHJlZml4OiBwcm9tcHQucHnsl5Drj4QgZGVmZW5zZS1pbi1kZXB0aOuhnCDstpTqsIAKIyMjIC0gdGhpbmtpbmdfYnVkZ2V0OjAg7J2AIC0xIOuwmOuztSDrsoTqt7gg67Cc7IOd7ZWY66+A66GcIOyCrOyaqSDslYgg7ZWoCiMjIyAtIE1BWF9UT1RBTF9UT0tFTlM9NDAwMDogRVhBT05FIG5vbi1BV1EsIOuNlCDrhJPsnYAg7Luo7YWN7Iqk7Yq4IO2XiOyaqQojIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMKT1BFTkFJX0xMTV9NQVhfVE9LRU5TPTgwMApPUEVOQUlfTExNX0VYVFJBX0JPRFk9eyJjaGF0X3RlbXBsYXRlX2t3YXJncyI6IHsiZW5hYmxlX3RoaW5raW5nIjogZmFsc2V9LCAicmVwZXRpdGlvbl9wZW5hbHR5IjogMS4xfQpNQVhfVE9UQUxfVE9LRU5TPTQwMDAKTUFYX0VOVElUWV9UT0tFTlM9MTAwMApNQVhfUkVMQVRJT05fVE9LRU5TPTEwMDAK"

def credId    = "env-file-lightrag"
def credDesc  = "LightRAG production .env (EXAONE-4.5-33B via LiteLLM + bge-m3, file-based storage)"

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
