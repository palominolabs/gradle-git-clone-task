package com.palominolabs.gradle.task.git.clone

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.agentproxy.AgentProxyException
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SshAgentSshSessionFactory extends SshSessionFactory {
  private static final Logger logger = LoggerFactory.getLogger(SshAgentSshSessionFactory.class);

  private final String knownHostsPath

  private final boolean trySshAgent

  private final String identityPrivKeyPath

  SshAgentSshSessionFactory(String knownHostsPath, boolean trySshAgent, String identityPrivKeyPath) {
    this.knownHostsPath = knownHostsPath
    this.trySshAgent = trySshAgent
    this.identityPrivKeyPath = identityPrivKeyPath
  }

  @Override
  RemoteSession getSession(URIish uri, CredentialsProvider credentialsProvider, FS fs, int tms) throws
      TransportException {

    ConnectorFactory cf = ConnectorFactory.getDefault()
    Connector connector

    if (trySshAgent) {
      try {
        connector = cf.createConnector()
      } catch (AgentProxyException e) {
        logger.debug("Couldn't load agent connector", e)
      }
    }

    JSch jsch = new JSch();

    // unfortunately, jsch's api doesn't really let us try one then the other
    if (connector != null) {
      jsch.setIdentityRepository(new RemoteIdentityRepository(connector))
    } else {
      jsch.addIdentity(identityPrivKeyPath)
    }

    jsch.setConfig("PreferredAuthentications", "publickey");
    jsch.setKnownHosts(knownHostsPath)

    Session session = jsch.getSession(uri.getUser(), uri.getHost())
    session.connect()

    return new JschSession(session, uri)
  }
}
