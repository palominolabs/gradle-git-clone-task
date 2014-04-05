This project provides a Gradle task to do one thing: have a directory be the contents of a Git repo at a specified treeish. It will clone it if it doesn't exist, check out the specified treeish, and fetch if you change the treeish to something that isn't in the current clone.

# Quick start

Suppose you want the contents of `git@github.com:palominolabs/gradle-git-clone-task-demo-repo.git` to be in `build/some-repo`. Put this in your `build.gradle` and now you have a task that does that:

```
buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    classpath 'com.palominolabs.gradle.task:gradle-git-clone-task:0.0.1'
  }
}

task cloneSomeRepo(type: com.palominolabs.gradle.task.git.clone.GitCloneTask) {
  dir = file("$buildDir/some-repo")
  uri = 'git@github.com:palominolabs/gradle-git-clone-task-demo-repo.git'
  treeish = 'v2' // a git hash, or tag name, or branch name'
}
```

Run `gradle cloneSomeRepo` and presto!

# Other configuration

Besides the requried `dir`, `uri`, and `treeish`, there are a few more things you can tweak.

### SSH known hosts
By default, the task configures Jsch to look for your SSH known hosts file in `~/.ssh/known_hosts`. To make it look somewhere else, set `knownHostsPath` to a `String` path.

### Resetting the repo each time the task runs
If you want to do the equivalent of a `git reset --hard`, set `reset` to `true`.
