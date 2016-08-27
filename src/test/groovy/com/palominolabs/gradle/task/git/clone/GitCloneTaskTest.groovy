package com.palominolabs.gradle.task.git.clone

import com.jcraft.jsch.JSchException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Paths
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

final class GitCloneTaskTest {

  public static final String SSH_REPO_URI = 'git@github.com:palominolabs/gradle-git-clone-task-demo-repo.git'
  private static String PRIV_KEY_PATH = new File('.').canonicalFile.toPath().resolve("src/test/resources/id_rsa").
      toString()

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  File dir

  GitCloneTask task

  @Before
  public void setUp() throws IOException {
    dir = tmp.newFolder()

    task = ProjectBuilder.builder().build().task('cloneTask', type: GitCloneTask)
    task.dir = dir
    task.uri = 'https://github.com/palominolabs/gradle-git-clone-task-demo-repo.git'
    task.treeish = 'b81f11dac85d93566036cb5d31d4e8365752e9f6'

    // can't count on CI having github's host key
    task.knownHostsPath = '/dev/null'
    task.strictHostKeyChecking = false

    // don't accidentally use a private key
    task.sshIdentityPrivKeyPath = 'no-such-key'
  }

  @Test
  public void testClonesIntoExistingDir() {
    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testClonesIntoExistingDirWithPortInHttpsUri() {
    task.uri = 'https://github.com:443/palominolabs/gradle-git-clone-task-demo-repo.git'

    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testCreatesDirAndClones() {
    task.dir = new File(dir, "subdir")
    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testCleansUpDirtyFilesIfRequested() {
    task.reset = true
    task.setUpRepo()

    new File(task.dir, "file1").setText "new text!"

    task.setUpRepo()

    assertChangedFileContents("v3")
  }

  @Test
  public void testDoesntCleanUpDirtyFilesIfDisabled() {
    task.reset = false
    task.setUpRepo()

    new File(task.dir, "file1").setText "new text!"

    assertChangedFileContents("new text!")
  }

  @Test
  public void testCanGoToOldCommitInRepo() {
    task.setUpRepo()
    assertChangedFileContents("v3")

    task.treeish = 'e3e07ab45915482d4f7ac061caef3dd684f2c132'
    task.setUpRepo()

    assertChangedFileContents("v1")
  }

  @Test
  public void testChecksOutTag() {
    task.treeish = 'v2'
    task.setUpRepo()

    assertChangedFileContents("v2")
  }

  @Test
  public void testChecksOutBranch() {
    task.treeish = 'origin/branch-at-v2'
    task.setUpRepo()

    assertChangedFileContents("v2")
  }

  @Test
  public void testChecksOutHash() {
    task.treeish = '4822b45c4599786240255898c1c1ad4ecdc50a2c'
    task.setUpRepo()

    assertChangedFileContents("v2")
  }

  @Test
  public void testSshUriWithAgent() {
    if (System.getenv('SSH_AUTH_SOCK') == null || 'true'.equals(System.getenv('CI'))) {
      System.err.println "No ssh agent found; skipping test"
      return
    }

    task.uri = SSH_REPO_URI

    task.setUpRepo()
    assertChangedFileContents("v3")
  }

  @Test
  public void testSshUriWithAgentAndPort() {
    if (System.getenv('SSH_AUTH_SOCK') == null || 'true'.equals(System.getenv('CI'))) {
      System.err.println "No ssh agent found; skipping test"
      return
    }

    task.uri = SSH_REPO_URI

    task.setUpRepo()
    assertChangedFileContents("v3")
  }

  @Test
  public void testUseUnknownTreeishFails() {
    task.treeish = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    try {
      task.setUpRepo()
      fail()
    } catch (RuntimeException e) {
      // this is only thrown after trying to fetch. Unfortunately, there isn't a great way to test
      // that trying again will actually *work*; that would involve making a repo publicly writable
      // so that everyone running the tests could pass it.
      assertEquals("Couldn't resolve <xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx>", e.getMessage())
    }
  }

  @Test
  public void testUsePrivKeyFile() {
    task.uri = SSH_REPO_URI
    task.trySshAgent = false
    task.sshIdentityPrivKeyPath = PRIV_KEY_PATH

    task.setUpRepo()
    assertChangedFileContents("v3")
  }

  @Test
  public void testFallBackToIdentityIfSshAgentNotFound() {
    task.uri = SSH_REPO_URI
    task.sshIdentityPrivKeyPath = PRIV_KEY_PATH

    def origEnv = unsetSshAgentEnvVar()

    try {
      task.setUpRepo()
    } finally {
      if (origEnv != null) {
        setEnv(origEnv)
      }
    }

    assertChangedFileContents("v3")
  }

  @Test
  public void testFailsWhenCantLoadSshAgentAndKeyPathIsBad() {
    task.uri = SSH_REPO_URI
    task.sshIdentityPrivKeyPath = 'foo/bar'

    def origEnv = unsetSshAgentEnvVar()

    try {
      task.setUpRepo()
      fail()
    } catch (JSchException e) {
      def cause = e.getCause()
      assertTrue(cause instanceof FileNotFoundException)
      assertEquals("foo/bar (No such file or directory)", cause.getMessage())
    } finally {
      if (origEnv != null) {
        setEnv(origEnv)
      }
    }
  }

  // This will only pass if you have write access to the demo repo. Sorry.
  @Test
  public void testUpdatesForRemoteBranchTreeishChange() {
    task.setUpRepo()
    assertChangedFileContents("v3")

    // set up another clone
    File otherClone = tmp.newFolder()
    GitCloneTask otherTask = ProjectBuilder.builder().build().task('otherTask', type: GitCloneTask)
    otherTask.dir = otherClone
    otherTask.uri = SSH_REPO_URI
    otherTask.treeish = 'b81f11dac85d93566036cb5d31d4e8365752e9f6'

    // can't count on CI having github's host key
    otherTask.knownHostsPath = Paths.get(System.getProperty('user.home'), '.ssh', 'known_hosts').toString()
    otherTask.setUpRepo()
    assertChangedFileContents(otherTask, "v3")

    // create a remote branch
    String branchName = "testrun-tmp-branch/" + UUID.randomUUID()

    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder()
        .setGitDir(new File(otherTask.dir, ".git"))
        .readEnvironment()
    Repository repository = repositoryBuilder.build()
    Git git = new Git(repository)

    ObjectId id = repository.resolve("refs/tags/v2")
    RevCommit commit = new RevWalk(repository).parseCommit(id)


    // replicate some guts of the task so ssh agent will work
    TransportConfigCallback configCallback = new SshAgentTransportConfigCallback(
        otherTask.knownHostsPath,
        true,
        '/dev/null',
        true)

    git.branchCreate()
        .setName(branchName)
        .setStartPoint(commit)
        .call()
    git.checkout()
        .setName(branchName)
        .call()
    git.push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("$branchName:$branchName"))
        .setTransportConfigCallback(configCallback)
        .call()

    task.treeish = "origin/$branchName"
    task.setUpRepo()
    assertChangedFileContents("v2")

    // move the local branch forward
    git.reset()
        .setMode(ResetCommand.ResetType.HARD)
        .setRef("refs/tags/v3")
        .call()
    // push
    git.push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("$branchName:$branchName"))
        .setTransportConfigCallback(configCallback)
        .call()

    // already has branch, won't see update
    task.setUpRepo()
    assertChangedFileContents("v2")

    // force fetch
    task.forceFetch = true
    task.setUpRepo()
    assertChangedFileContents("v3")

    // delete the remote branch
    git.push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec().setSourceDestination(null, "refs/heads/$branchName"))
        .setTransportConfigCallback(configCallback)
        .call()
  }

  /**
   * @return null if env var wasn't found (and nothing was modified), or returns the original env var map for later
   * resetting
   */
  private Map<String, String> unsetSshAgentEnvVar() {
    Map<String, String> origEnv = System.getenv()
    Map<String, String> modifiedEnv = new HashMap<>(origEnv)

    String origValue = modifiedEnv.remove('SSH_AUTH_SOCK')

    // env map never has null values, so null implies not set
    if (origValue == null) {
      return null;
    }

    modifiedEnv = Collections.unmodifiableMap(modifiedEnv)
    setEnv(modifiedEnv)

    return origEnv;
  }

  private void setEnv(Map<String, String> env) {
    // getenv() is cached so we can't use posix setenv() to change it; we have to get dirty.
    // I hate being part of the problem.

    Class<?> klass = Class.forName("java.lang.ProcessEnvironment")
    Field field = klass.getDeclaredField("theUnmodifiableEnvironment")

    field.setAccessible(true)

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    field.set(null, env)
  }

  void assertChangedFileContents(String contents) {
    assertChangedFileContents(task, contents)
  }

  void assertChangedFileContents(GitCloneTask task, String contents) {
    assertFileContents(new File(task.dir, "file1"), contents)
  }

  static void assertFileContents(File f, String contents) {
    assert f.exists()

    assert contents == f.text.trim()
  }
}
