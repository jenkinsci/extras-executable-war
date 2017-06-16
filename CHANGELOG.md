Changelog
====

### 1.35.1

Release date: June 16, 2017

Fixed issues:

* [JENKINS-44894](https://issues.jenkins-ci.org/browse/JENKINS-44894) -
Fix typo in the system property name
([PR #10](https://github.com/jenkinsci/extras-executable-war/pull/10)).

### 1.35

Release date: June 14, 2017

* [JENKINS-44764](https://issues.jenkins-ci.org/browse/JENKINS-44764) -
Executable WAR now checks for Java 8 instead of Java 7 before starting Jenkins
([PR #8](https://github.com/jenkinsci/extras-executable-war/pull/8)).
* [JENKINS-44894](https://issues.jenkins-ci.org/browse/JENKINS-44894) -
Add system properties for managing the Jetty Session ID cookie name
([PR #9](https://github.com/jenkinsci/extras-executable-war/pull/9)).
  * Now it is possible to disable random cookie name generation or to specify a custom one
  * These options can be used to workaround issues like [JENKINS-25046](https://issues.jenkins-ci.org/browse/JENKINS-25046)
  * The options are documented [here](README.md#jetty-session-ids)

### Previous releases

See the commit history and [Jenkins changelog](http://jenkins-ci.org/changelog).
