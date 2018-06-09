Changelog
====

### 1.40

Release date: Jun 10, 2018

* [JENKINS-51155](https://issues.jenkins-ci.org/browse/JENKINS-51155) -
Add a `--enable-future-java` flag, which allows running with not-yet-supported Java versions

### 1.39

Release date: Mar 28, 2018

* [JENKINS-50439](https://issues.jenkins-ci.org/browse/JENKINS-50439) -
  Remove spurious "Failed to delete the temporary Winstone file /tmp/winstone/jenkins-2.x.war" warning when starting Jenkins

### 1.38

Release date: Mar 10, 2018

* [JENKINS-49737](https://issues.jenkins-ci.org/browse/JENKINS-49737) -
Print warning if the executable is started with Java 9 or above.
* [PR #15](https://github.com/jenkinsci/extras-executable-war/pull/15) -
Developer: Update to the newest parent POM, cleanup issues reported by static analysis.

### 1.37

Release date: Jan 08, 2018

* [PR #13](https://github.com/jenkinsci/extras-executable-war/pull/13) - 
Add ability to supply WAR command-line arguments via STDIN using the `--paramsFromStdIn` parameter
([Documentation](https://github.com/jenkinsci/extras-executable-war#parameters-from-stdin)).
* [JENKINS-22088](https://issues.jenkins-ci.org/browse/JENKINS-22088) -
Prevent the disk space leak due to multiple copies of `winstone-XXXX.jar` in the TEMP folder.

### 1.36

Release date: July 14, 2017

* [JENKINS-45438](https://issues.jenkins-ci.org/browse/JENKINS-45438) -
Set context classloader to enable the ServiceLoader mechanism required for HTTP/2 support
([PR #11](https://github.com/jenkinsci/extras-executable-war/pull/11)).

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
