package com.palominolabs.gradle.task.git.clone

import java.nio.file.Paths
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GitCloneTask extends DefaultTask {

  /**
   * Dir to clone into
   */
  File dir

  /**
   * Uri of repo to clone from
   */
  String uri

  /**
   * Treeish to checkout in clone
   */
  String treeish

  /**
   * Known hosts path to use when ssh'ing
   */
  String knownHostsPath = Paths.get(System.getProperty('user.home'), '.ssh', 'known_hosts').toString()

  /**
   * Try loading ssh identities from ssh-agent. Will fall back to ssh identity if ssh-agent not available.
   */
  boolean trySshAgent = true

  /**
   * Path to (unencrypted) private key to try if ssh-agent can't be found
   */
  String sshIdentityPrivKeyPath = Paths.get(System.getProperty('user.home'), '.ssh', 'id_rsa').toString()

  /**
   * Do a reset --hard when checking out
   */
  boolean reset = false

  /**
   * Set to false to allow connecting to SSH hosts that don't have an entry in knownHostsPath
   */
  boolean strictHostKeyChecking = true

  @TaskAction
  def setUpRepo() {

    if (dir == null) {
      throw new NullPointerException("Must specify dir")
    }

    if (uri == null) {
      throw new NullPointerException("Must specify uri")
    }

    if (treeish == null) {
      throw new NullPointerException("Must specify treeish")
    }

    if (!trySshAgent && !(new File(sshIdentityPrivKeyPath).canRead())) {
      throw new IllegalArgumentException("ssh-agent disabled, and ssh priv key $sshIdentityPrivKeyPath not readable")
    }

    if (!dir.exists()) {
      dir.mkdirs()
    }

    File gitDir = new File(dir, ".git")

    TransportConfigCallback configCallback = new SshAgentTransportConfigCallback(knownHostsPath, trySshAgent,
        sshIdentityPrivKeyPath, strictHostKeyChecking)

    if (!gitDir.exists()) {
      // no git dir there yet; clone it
      Git.cloneRepository()
          .setDirectory(dir)
          .setNoCheckout(true)
          .setURI(uri)
          .setCloneAllBranches(true)
          .setTransportConfigCallback(configCallback)
          .call()
    }

    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder()
        .setGitDir(gitDir)
        .readEnvironment()

    Repository repository = repositoryBuilder.build()
    Git git = new Git(repository)

    ObjectId ref = repository.resolve(treeish)

    if (ref == null) {
      // we may just need to fetch
      git.fetch()
          .setTransportConfigCallback(configCallback)
          .call()

      ref = repository.resolve(treeish)

      if (ref == null) {
        throw new RuntimeException("Couldn't resolve <$treeish>")
      }
    }

    RevCommit revCommit = new RevWalk(repository).parseCommit(ref)

    git.checkout()
        .setAllPaths(true)
        .setStartPoint(revCommit)
        .call()

    if (reset) {
      git.reset()
          .setMode(ResetCommand.ResetType.HARD)
          .setRef(treeish)
          .call()
    }
  }
}
