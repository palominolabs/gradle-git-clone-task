package com.palominolabs.gradle.plugin.git.clone

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.SshSessionFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Paths

class GitCloneTask extends DefaultTask {

  File dir

  String uri

  String treeish

  String knownHostsPath = Paths.get(System.getProperty('user.home'), '.ssh', 'known_hosts').toString()

  boolean cloneAllBranches = true

  boolean reset = false

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

    if (!dir.exists()) {
      dir.mkdirs()
    }

    SshSessionFactory.setInstance(new SshAgentSshSessionFactory(knownHostsPath))

    File gitDir = new File(dir, ".git")

    if (!gitDir.exists()) {
      // no git dir there yet; clone it
      Git.cloneRepository()
          .setDirectory(dir)
          .setNoCheckout(true)
          .setURI(uri)
          .setCloneAllBranches(cloneAllBranches)
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
