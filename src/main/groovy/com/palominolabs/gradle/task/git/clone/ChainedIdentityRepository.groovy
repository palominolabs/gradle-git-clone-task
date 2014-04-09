package com.palominolabs.gradle.task.git.clone

import com.jcraft.jsch.IdentityRepository

class ChainedIdentityRepository implements IdentityRepository {

  private final List<IdentityRepository> repos;

  ChainedIdentityRepository(List<IdentityRepository> repos) {
    this.repos = repos
  }

  @Override
  Vector getIdentities() {
    Vector v = new Vector()

    repos.each { v.addAll it.getIdentities() }

    return v;
  }

  @Override
  boolean add(byte[] identity) {
    for (IdentityRepository r : repos) {
      if (r.add(identity)) {
        return true
      }
    }

    return false
  }

  @Override
  boolean remove(byte[] blob) {
    boolean ok = false

    for (IdentityRepository r : repos) {
      if (r.remove(blob)) {
        ok = true
      }
    }

    return ok
  }

  @Override
  void removeAll() {
    repos.each {it.removeAll() }
  }
}
