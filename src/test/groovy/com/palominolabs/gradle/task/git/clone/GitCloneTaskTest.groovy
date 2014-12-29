package com.palominolabs.gradle.task.git.clone

import com.jcraft.jsch.JSchException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
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
    assertFileContents(new File(task.dir, "file1"), contents)
  }

  static void assertFileContents(File f, String contents) {
    assert f.exists()

    assert contents == f.text.trim()
  }
}
