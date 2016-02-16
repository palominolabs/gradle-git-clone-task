This project provides a Gradle task to do one thing: have a directory be the contents of a Git repo at a specified treeish. It will clone it if it doesn't exist already, check out the specified treeish, and fetch if you change the treeish to something that isn't in the current clone.

# Quick start

Suppose you want the contents of `git@github.com:palominolabs/gradle-git-clone-task-demo-repo.git` to be in `build/some-repo`. Put this in your `build.gradle`:

```
buildscript {
  repositories {
    jcenter()
  }

  dependencies {
    classpath 'com.palominolabs.gradle.task:gradle-git-clone-task:0.0.2'
  }
}

// use any task name you like
task cloneSomeRepo(type: com.palominolabs.gradle.task.git.clone.GitCloneTask) {
  dir = file("$buildDir/some-repo")
  uri = 'git@github.com:palominolabs/gradle-git-clone-task-demo-repo.git'
  treeish = 'v2' // a commit hash, or tag name, or branch name (with remote prefix, like 'origin/master')
}
```

Run `gradle cloneSomeRepo` and presto!

# Other configuration

Besides the requried `dir`, `uri`, and `treeish`, there are a few more things you can tweak.

#### Disabling ssh agent usage
By default, the task will try to load ssh keys from ssh-agent. If you want it to not try ssh-agent, set `trySshAgent` to false.

#### SSH identity private key
By default, the task looks in `~/.ssh/id_rsa` for an unencrypted private key if it can't find ssh-agent. This location can be configured via `sshIdentityPrivKeyPath`.

#### SSH known hosts
By default, the task configures Jsch to look for your SSH known hosts file in `~/.ssh/known_hosts`. To make it look somewhere else, set `knownHostsPath` to a `String` path.

#### Resetting the repo each time the task runs
If you want to do the equivalent of a `git reset --hard`, set `reset` to `true`.

#### HTTP Authentication
HTTP authentication isn't supported because so far SSH auth has always been able to do the job. HTTP also lacks an obvious one-stop auth config solution like "just use ssh private keys".