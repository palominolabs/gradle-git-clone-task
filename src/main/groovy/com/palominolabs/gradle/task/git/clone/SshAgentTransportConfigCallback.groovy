package com.palominolabs.gradle.task.git.clone

import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.TransportGitSsh

final class SshAgentTransportConfigCallback implements TransportConfigCallback {

  private final String knownHostsPath

  SshAgentTransportConfigCallback(String knownHostsPath) {
    this.knownHostsPath = knownHostsPath
  }

  @Override
  public void configure(Transport transport) {
    if (transport instanceof TransportGitSsh) {
      ((TransportGitSsh) transport).
          setSshSessionFactory(new SshAgentSshSessionFactory(knownHostsPath))
    }
  }
}
