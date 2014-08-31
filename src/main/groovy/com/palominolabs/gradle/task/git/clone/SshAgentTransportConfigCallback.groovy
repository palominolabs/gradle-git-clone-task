package com.palominolabs.gradle.task.git.clone

import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.TransportGitSsh

final class SshAgentTransportConfigCallback implements TransportConfigCallback {

  private final String knownHostsPath
  private final boolean trySshAgent
  private final String identityPrivKeyPath
  private final boolean strictHostKeyChecking

  SshAgentTransportConfigCallback(String knownHostsPath, boolean trySshAgent, String identityPrivKeyPath,
                                  boolean strictHostKeyChecking) {
    this.knownHostsPath = knownHostsPath
    this.trySshAgent = trySshAgent
    this.identityPrivKeyPath = identityPrivKeyPath
    this.strictHostKeyChecking = strictHostKeyChecking
  }

  @Override
  public void configure(Transport transport) {
    if (transport instanceof TransportGitSsh) {
      ((TransportGitSsh) transport).
          setSshSessionFactory(new SshAgentSshSessionFactory(knownHostsPath, trySshAgent, identityPrivKeyPath,
              strictHostKeyChecking))
    }
  }
}
