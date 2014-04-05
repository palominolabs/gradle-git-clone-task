package com.palominolabs.gradle.plugin.git.clone

import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.agentproxy.Connector
import com.jcraft.jsch.agentproxy.ConnectorFactory
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.JschSession
import org.eclipse.jgit.transport.RemoteSession
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.FS

class SshAgentSshSessionFactory extends SshSessionFactory {

  private final String knownHostsPath


  SshAgentSshSessionFactory(String knownHostsPath) {
    this.knownHostsPath = knownHostsPath
  }

  @Override
  RemoteSession getSession(URIish uri, CredentialsProvider credentialsProvider, FS fs, int tms) throws
      TransportException {

    ConnectorFactory cf = ConnectorFactory.getDefault();
    Connector connector = cf.createConnector()

    IdentityRepository repo = new RemoteIdentityRepository(connector)

    JSch jsch = new JSch();
    jsch.setConfig("PreferredAuthentications", "publickey");
    jsch.setIdentityRepository(repo)
    jsch.setKnownHosts(knownHostsPath)

    Session session = jsch.getSession(uri.getUser(), uri.getHost())
    session.connect()

    return new JschSession(session, uri)
  }
}
